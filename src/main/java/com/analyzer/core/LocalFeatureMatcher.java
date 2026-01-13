package com.analyzer.core;

import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 局部特征搜索匹配器
 * 使用Spark RDD实现图像分割并行比对
 * 在大图中搜索包含指定小图的位置
 */
public class LocalFeatureMatcher {
    
    // 像素匹配阈值：灰度差小于此值认为匹配
    private static final int PIXEL_MATCH_THRESHOLD = 5;
    
    /**
     * 匹配结果
     */
    public static class LocalMatchResult implements Serializable {
        private static final long serialVersionUID = 1L;
        private String filename;
        private int matchCount;
        private List<MatchLocation> locations;
        
        public LocalMatchResult(String filename) {
            this.filename = filename;
            this.matchCount = 0;
            this.locations = new ArrayList<>();
        }
        
        public void addLocation(int x, int y, double similarity) {
            locations.add(new MatchLocation(x, y, similarity));
            matchCount++;
        }
        
        public String getFilename() {
            return filename;
        }
        
        public int getMatchCount() {
            return matchCount;
        }
        
        public List<MatchLocation> getLocations() {
            return locations;
        }
        
        @Override
        public String toString() {
            if (matchCount == 0) {
                return filename + " - 未找到匹配";
            }
            StringBuilder sb = new StringBuilder();
            sb.append(filename).append(" - 找到 ").append(matchCount).append(" 个匹配位置:\n");
            for (MatchLocation loc : locations) {
                sb.append("  位置(").append(loc.x).append(",").append(loc.y)
                  .append(") 相似度:").append(String.format("%.2f%%", loc.similarity * 100))
                  .append("\n");
            }
            return sb.toString();
        }
    }
    
    /**
     * 匹配位置
     */
    public static class MatchLocation implements Serializable {
        private static final long serialVersionUID = 1L;
        private int x;
        private int y;
        private double similarity;
        
        public MatchLocation(int x, int y, double similarity) {
            this.x = x;
            this.y = y;
            this.similarity = similarity;
        }
        
        public int getX() {
            return x;
        }
        
        public int getY() {
            return y;
        }
        
        public double getSimilarity() {
            return similarity;
        }
    }
    
    /**
     * 在图像库中搜索包含局部特征的图像（使用Spark RDD进行图像分割并行比对）
     * 
     * @param featureImage 局部特征图像
     * @param imageLibrary 图像库目录
     * @param threshold 相似度阈值（0-1，建议0.95以上）
     * @return 匹配结果列表
     * @throws IOException 处理失败时抛出
     */
    public static List<LocalMatchResult> searchLocalFeature(File featureImage, File imageLibrary, double threshold) throws IOException {
        System.out.println("=== 使用Spark RDD进行分布式局部特征搜索 ===");
        
        BufferedImage feature = ImageIO.read(featureImage);
        if (feature == null) {
            throw new IOException("无法读取特征图像");
        }
        
        int featureWidth = feature.getWidth();
        int featureHeight = feature.getHeight();
        
        System.out.println("特征图像尺寸: " + featureWidth + "x" + featureHeight);
        
        // 获取特征图像的灰度矩阵
        final int[][] featureMatrix = getGrayscaleMatrix(feature);
        
        // 获取图像库中的所有图像
        List<File> libraryImages = ImageResourceDownloader.getExistingImages(imageLibrary);
        System.out.println("图像库大小: " + libraryImages.size() + " 张图像");
        
        // 获取Spark上下文
        JavaSparkContext sc = SparkContextManager.getOrCreateContext();
        System.out.println("Spark UI: " + SparkContextManager.getSparkUIUrl());
        
        // 使用Spark RDD并行处理每张图像
        JavaRDD<File> imagesRDD = sc.parallelize(libraryImages);
        
        List<LocalMatchResult> results = imagesRDD
            .map(libImage -> {
                try {
                    BufferedImage target = ImageIO.read(libImage);
                    if (target == null) {
                        return new LocalMatchResult(libImage.getName());
                    }
                    
                    LocalMatchResult result = new LocalMatchResult(libImage.getName());
                    
                    int targetWidth = target.getWidth();
                    int targetHeight = target.getHeight();
                    
                    // 如果特征图像大于目标图像，跳过
                    if (featureWidth > targetWidth || featureHeight > targetHeight) {
                        return result;
                    }
                    
                    // 获取目标图像的灰度矩阵
                    int[][] targetMatrix = getGrayscaleMatrix(target);
                    
                    // 将搜索区域分割成块进行并行处理（模拟图像分割）
                    // 这里在一个map操作内对不同区域进行扫描
                    for (int y = 0; y <= targetHeight - featureHeight; y++) {
                        for (int x = 0; x <= targetWidth - featureWidth; x++) {
                            double similarity = calculateRegionSimilarity(
                                featureMatrix, targetMatrix, x, y, featureWidth, featureHeight);
                            
                            if (similarity >= threshold) {
                                result.addLocation(x, y, similarity);
                            }
                        }
                    }
                    
                    return result;
                } catch (IOException e) {
                    System.err.println("处理图像失败: " + libImage.getName() + " - " + e.getMessage());
                    return new LocalMatchResult(libImage.getName());
                }
            })
            .collect();
        
        System.out.println("Spark任务完成，处理了 " + results.size() + " 张图像");
        
        return results;
    }
    
    /**
     * 计算区域相似度
     */
    private static double calculateRegionSimilarity(int[][] feature, int[][] target, 
                                                     int startX, int startY, int width, int height) {
        int totalPixels = width * height;
        int matchingPixels = 0;
        int totalDiff = 0;
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int featureValue = feature[y][x];
                int targetValue = target[startY + y][startX + x];
                int diff = Math.abs(featureValue - targetValue);
                totalDiff += diff;
                
                // 如果差异小于阈值，认为匹配
                if (diff < PIXEL_MATCH_THRESHOLD) {
                    matchingPixels++;
                }
            }
        }
        
        // 使用综合评分：匹配像素比例 * 平均差异的倒数
        double matchRatio = (double) matchingPixels / totalPixels;
        double avgDiff = (double) totalDiff / totalPixels;
        double diffScore = 1.0 - (avgDiff / 255.0);
        
        return (matchRatio + diffScore) / 2.0;
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
