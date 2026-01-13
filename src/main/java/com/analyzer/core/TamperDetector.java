package com.analyzer.core;

import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 图像篡改检测器
 * 使用Spark RDD实现图像分割并行比对
 * 检测图像局部修改并定位篡改位置
 */
public class TamperDetector {
    
    // 连通域检测阈值：块内差异像素比例超过此值认为是篡改区域
    private static final double TAMPER_BLOCK_THRESHOLD = 0.3;
    
    // 篡改检测的像素块大小
    private static final int BLOCK_SIZE = 16;
    
    /**
     * 篡改检测结果
     */
    public static class TamperResult implements Serializable {
        private static final long serialVersionUID = 1L;
        private String filename;
        private int matchingPixels;
        private int totalPixels;
        private double similarity;
        private List<TamperedRegion> tamperedRegions;
        
        public TamperResult(String filename, int matchingPixels, int totalPixels) {
            this.filename = filename;
            this.matchingPixels = matchingPixels;
            this.totalPixels = totalPixels;
            this.similarity = (double) matchingPixels / totalPixels;
            this.tamperedRegions = new ArrayList<>();
        }
        
        public void addTamperedRegion(TamperedRegion region) {
            tamperedRegions.add(region);
        }
        
        public String getFilename() {
            return filename;
        }
        
        public double getSimilarity() {
            return similarity;
        }
        
        public int getMatchingPixels() {
            return matchingPixels;
        }
        
        public int getTotalPixels() {
            return totalPixels;
        }
        
        public List<TamperedRegion> getTamperedRegions() {
            return tamperedRegions;
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(filename).append("\n");
            sb.append("相似度: ").append(String.format("%.2f%%", similarity * 100)).append("\n");
            sb.append("匹配像素: ").append(matchingPixels).append("/").append(totalPixels).append("\n");
            
            if (!tamperedRegions.isEmpty()) {
                sb.append("检测到 ").append(tamperedRegions.size()).append(" 个篡改区域:\n");
                for (int i = 0; i < Math.min(5, tamperedRegions.size()); i++) {
                    TamperedRegion region = tamperedRegions.get(i);
                    sb.append("  区域").append(i + 1).append(": (")
                      .append(region.startX).append(",").append(region.startY)
                      .append(") - (").append(region.endX).append(",").append(region.endY)
                      .append(") 差异像素:").append(region.pixelCount).append("\n");
                }
                if (tamperedRegions.size() > 5) {
                    sb.append("  ... 以及其他 ").append(tamperedRegions.size() - 5).append(" 个区域\n");
                }
            } else {
                sb.append("未检测到明显篡改区域\n");
            }
            
            return sb.toString();
        }
    }
    
    /**
     * 篡改区域
     */
    public static class TamperedRegion implements Serializable {
        private static final long serialVersionUID = 1L;
        private int startX;
        private int startY;
        private int endX;
        private int endY;
        private int pixelCount;
        
        public TamperedRegion(int startX, int startY, int endX, int endY, int pixelCount) {
            this.startX = startX;
            this.startY = startY;
            this.endX = endX;
            this.endY = endY;
            this.pixelCount = pixelCount;
        }
        
        public int getStartX() {
            return startX;
        }
        
        public int getStartY() {
            return startY;
        }
        
        public int getEndX() {
            return endX;
        }
        
        public int getEndY() {
            return endY;
        }
        
        public int getPixelCount() {
            return pixelCount;
        }
    }
    
    /**
     * 检测图像篡改（根据当前引擎使用Spark RDD或MapReduce进行图像分割并行比对）
     * 
     * @param suspectImage 疑似被篡改的图像
     * @param imageLibrary 图像库目录
     * @param threshold 差异阈值（灰度差大于此值视为篡改，建议10-30）
     * @return 检测结果（按匹配像素数降序排列）
     * @throws IOException 处理失败时抛出
     */
    public static List<TamperResult> detectTampering(File suspectImage, File imageLibrary, int threshold) throws IOException {
        // 根据当前计算引擎选择处理方式
        if (ComputeEngineManager.isUsingMapReduce()) {
            return MapReduceProcessor.detectTamperingMapReduce(suspectImage, imageLibrary, threshold);
        }
        
        System.out.println("=== 使用Spark RDD进行分布式篡改检测 ===");
        
        BufferedImage suspect = ImageIO.read(suspectImage);
        if (suspect == null) {
            throw new IOException("无法读取疑似篡改图像");
        }
        
        int suspectWidth = suspect.getWidth();
        int suspectHeight = suspect.getHeight();
        final int[][] suspectMatrix = getGrayscaleMatrix(suspect);
        
        System.out.println("疑似篡改图像尺寸: " + suspectWidth + "x" + suspectHeight);
        
        // 获取图像库中的所有图像
        List<File> libraryImages = ImageResourceDownloader.getExistingImages(imageLibrary);
        
        // 过滤掉疑似图像本身
        final String suspectName = suspectImage.getName();
        libraryImages.removeIf(f -> f.getName().equals(suspectName));
        
        System.out.println("图像库大小: " + libraryImages.size() + " 张图像");
        
        // 获取Spark上下文
        JavaSparkContext sc = SparkContextManager.getOrCreateContext();
        System.out.println("Spark UI: " + SparkContextManager.getSparkUIUrl());
        
        // 使用Spark RDD并行处理每张图像
        JavaRDD<File> imagesRDD = sc.parallelize(libraryImages);
        
        List<TamperResult> results = imagesRDD
            .map(libImage -> {
                try {
                    BufferedImage original = ImageIO.read(libImage);
                    if (original == null) {
                        return null;
                    }
                    
                    // 只比较尺寸相同的图像
                    if (original.getWidth() != suspectWidth || original.getHeight() != suspectHeight) {
                        return null;
                    }
                    
                    int[][] originalMatrix = getGrayscaleMatrix(original);
                    
                    // 逐像素比对（这里可以进一步分割成块并行处理）
                    int matchingPixels = 0;
                    int totalPixels = suspectWidth * suspectHeight;
                    boolean[][] differenceMap = new boolean[suspectHeight][suspectWidth];
                    
                    for (int y = 0; y < suspectHeight; y++) {
                        for (int x = 0; x < suspectWidth; x++) {
                            int diff = Math.abs(suspectMatrix[y][x] - originalMatrix[y][x]);
                            if (diff <= threshold) {
                                matchingPixels++;
                                differenceMap[y][x] = false;
                            } else {
                                differenceMap[y][x] = true;
                            }
                        }
                    }
                    
                    TamperResult result = new TamperResult(libImage.getName(), matchingPixels, totalPixels);
                    
                    // 识别篡改区域（连通域检测）
                    List<TamperedRegion> regions = findTamperedRegions(differenceMap, suspectWidth, suspectHeight);
                    for (TamperedRegion region : regions) {
                        result.addTamperedRegion(region);
                    }
                    
                    return result;
                    
                } catch (IOException e) {
                    System.err.println("处理图像失败: " + libImage.getName() + " - " + e.getMessage());
                    return null;
                }
            })
            .filter(r -> r != null)
            .collect();
        
        System.out.println("Spark任务完成，处理了 " + results.size() + " 张图像");
        
        // 按匹配像素数降序排序
        Collections.sort(results, new Comparator<TamperResult>() {
            @Override
            public int compare(TamperResult r1, TamperResult r2) {
                return Integer.compare(r2.getMatchingPixels(), r1.getMatchingPixels());
            }
        });
        
        return results;
    }
    
    /**
     * 识别篡改区域（简化版连通域检测）
     */
    private static List<TamperedRegion> findTamperedRegions(boolean[][] differenceMap, int width, int height) {
        List<TamperedRegion> regions = new ArrayList<>();
        boolean[][] visited = new boolean[height][width];
        
        // 使用网格分割方法识别区域
        
        for (int by = 0; by < height; by += BLOCK_SIZE) {
            for (int bx = 0; bx < width; bx += BLOCK_SIZE) {
                int endX = Math.min(bx + BLOCK_SIZE, width);
                int endY = Math.min(by + BLOCK_SIZE, height);
                
                // 统计块内差异像素数
                int diffCount = 0;
                for (int y = by; y < endY; y++) {
                    for (int x = bx; x < endX; x++) {
                        if (differenceMap[y][x]) {
                            diffCount++;
                        }
                    }
                }
                
                // 如果差异像素超过块的指定阈值比例，认为是篡改区域
                int blockPixels = (endX - bx) * (endY - by);
                if (diffCount > blockPixels * TAMPER_BLOCK_THRESHOLD) {
                    regions.add(new TamperedRegion(bx, by, endX - 1, endY - 1, diffCount));
                }
            }
        }
        
        // 按差异像素数降序排序
        Collections.sort(regions, new Comparator<TamperedRegion>() {
            @Override
            public int compare(TamperedRegion r1, TamperedRegion r2) {
                return Integer.compare(r2.getPixelCount(), r1.getPixelCount());
            }
        });
        
        return regions;
    }
    
    /**
     * 获取图像的灰度矩阵
     */
    private static int[][] getGrayscaleMatrix(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[][] matrix = new int[height][width];
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                matrix[y][x] = (int) (0.299 * r + 0.587 * g + 0.114 * b);
            }
        }
        
        return matrix;
    }
    
    /**
     * 在单张图像中检测篡改（用于MapReduce）
     * 
     * @param suspectImage 疑似篡改图像
     * @param originalImage 原始图像
     * @param threshold 差异阈值
     * @return 检测结果
     * @throws IOException 处理失败时抛出
     */
    public static TamperResult detectInSingleImage(File suspectImage, File originalImage, int threshold) throws IOException {
        BufferedImage suspect = ImageIO.read(suspectImage);
        BufferedImage original = ImageIO.read(originalImage);
        
        if (suspect == null || original == null) {
            return null;
        }
        
        int suspectWidth = suspect.getWidth();
        int suspectHeight = suspect.getHeight();
        
        // 只比较尺寸相同的图像
        if (original.getWidth() != suspectWidth || original.getHeight() != suspectHeight) {
            return null;
        }
        
        int[][] suspectMatrix = getGrayscaleMatrix(suspect);
        int[][] originalMatrix = getGrayscaleMatrix(original);
        
        // 逐像素比对
        int matchingPixels = 0;
        int totalPixels = suspectWidth * suspectHeight;
        boolean[][] differenceMap = new boolean[suspectHeight][suspectWidth];
        
        for (int y = 0; y < suspectHeight; y++) {
            for (int x = 0; x < suspectWidth; x++) {
                int diff = Math.abs(suspectMatrix[y][x] - originalMatrix[y][x]);
                if (diff <= threshold) {
                    matchingPixels++;
                    differenceMap[y][x] = false;
                } else {
                    differenceMap[y][x] = true;
                }
            }
        }
        
        TamperResult result = new TamperResult(originalImage.getName(), matchingPixels, totalPixels);
        
        // 识别篡改区域
        List<TamperedRegion> regions = findTamperedRegions(differenceMap, suspectWidth, suspectHeight);
        for (TamperedRegion region : regions) {
            result.addTamperedRegion(region);
        }
        
        return result;
    }
}
