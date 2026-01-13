package com.analyzer.core;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 图像篡改检测器
 * 检测图像局部修改并定位篡改位置
 */
public class TamperDetector {
    
    /**
     * 篡改检测结果
     */
    public static class TamperResult {
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
    public static class TamperedRegion {
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
     * 检测图像篡改
     * 
     * @param suspectImage 疑似被篡改的图像
     * @param imageLibrary 图像库目录
     * @param threshold 差异阈值（灰度差大于此值视为篡改，建议10-30）
     * @return 检测结果（按匹配像素数降序排列）
     * @throws IOException 处理失败时抛出
     */
    public static List<TamperResult> detectTampering(File suspectImage, File imageLibrary, int threshold) throws IOException {
        BufferedImage suspect = ImageIO.read(suspectImage);
        if (suspect == null) {
            throw new IOException("无法读取疑似篡改图像");
        }
        
        int suspectWidth = suspect.getWidth();
        int suspectHeight = suspect.getHeight();
        int[][] suspectMatrix = getGrayscaleMatrix(suspect);
        
        // 获取图像库中的所有图像
        List<File> libraryImages = ImageResourceDownloader.getExistingImages(imageLibrary);
        List<TamperResult> results = new ArrayList<>();
        
        for (File libImage : libraryImages) {
            // 跳过自己
            if (libImage.getName().equals(suspectImage.getName())) {
                continue;
            }
            
            try {
                BufferedImage original = ImageIO.read(libImage);
                if (original == null) continue;
                
                // 只比较尺寸相同的图像
                if (original.getWidth() != suspectWidth || original.getHeight() != suspectHeight) {
                    continue;
                }
                
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
                
                TamperResult result = new TamperResult(libImage.getName(), matchingPixels, totalPixels);
                
                // 识别篡改区域（连通域检测）
                List<TamperedRegion> regions = findTamperedRegions(differenceMap, suspectWidth, suspectHeight);
                for (TamperedRegion region : regions) {
                    result.addTamperedRegion(region);
                }
                
                results.add(result);
                
            } catch (IOException e) {
                System.err.println("处理图像失败: " + libImage.getName() + " - " + e.getMessage());
            }
        }
        
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
        int blockSize = 16; // 16x16 像素块
        
        for (int by = 0; by < height; by += blockSize) {
            for (int bx = 0; bx < width; bx += blockSize) {
                int endX = Math.min(bx + blockSize, width);
                int endY = Math.min(by + blockSize, height);
                
                // 统计块内差异像素数
                int diffCount = 0;
                for (int y = by; y < endY; y++) {
                    for (int x = bx; x < endX; x++) {
                        if (differenceMap[y][x]) {
                            diffCount++;
                        }
                    }
                }
                
                // 如果差异像素超过块的30%，认为是篡改区域
                int blockPixels = (endX - bx) * (endY - by);
                if (diffCount > blockPixels * 0.3) {
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
}
