package com.analyzer.core;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 全图搜索匹配器
 * 使用直方图比对进行图像匹配
 */
public class ImageMatcher {
    
    /**
     * 搜索结果
     */
    public static class MatchResult {
        private String filename;
        private double similarity;
        
        public MatchResult(String filename, double similarity) {
            this.filename = filename;
            this.similarity = similarity;
        }
        
        public String getFilename() {
            return filename;
        }
        
        public double getSimilarity() {
            return similarity;
        }
        
        @Override
        public String toString() {
            return String.format("%s (相似度: %.2f%%)", filename, similarity * 100);
        }
    }
    
    /**
     * 在图像库中搜索匹配的图像
     * 
     * @param queryImage 查询图像文件
     * @param imageLibrary 图像库目录
     * @param topN 返回前N个最匹配的结果
     * @return 匹配结果列表（按相似度降序排列）
     * @throws IOException 处理失败时抛出
     */
    public static List<MatchResult> searchImage(File queryImage, File imageLibrary, int topN) throws IOException {
        // 生成查询图像的直方图
        ImageHistogram queryHistogram = new ImageHistogram(queryImage);
        
        // 获取图像库中的所有图像
        List<File> libraryImages = ImageResourceDownloader.getExistingImages(imageLibrary);
        
        if (libraryImages.isEmpty()) {
            throw new IOException("图像库为空");
        }
        
        // 计算每张图像与查询图像的相似度
        List<MatchResult> results = new ArrayList<>();
        
        for (File libImage : libraryImages) {
            try {
                ImageHistogram libHistogram = new ImageHistogram(libImage);
                double similarity = queryHistogram.calculateSimilarity(libHistogram);
                results.add(new MatchResult(libImage.getName(), similarity));
            } catch (IOException e) {
                System.err.println("处理图像失败: " + libImage.getName() + " - " + e.getMessage());
            }
        }
        
        // 按相似度降序排序
        Collections.sort(results, new Comparator<MatchResult>() {
            @Override
            public int compare(MatchResult r1, MatchResult r2) {
                return Double.compare(r2.getSimilarity(), r1.getSimilarity());
            }
        });
        
        // 返回前N个结果
        int resultSize = Math.min(topN, results.size());
        return results.subList(0, resultSize);
    }
    
    /**
     * 批量生成图像直方图
     * 
     * @param imageDir 图像目录
     * @param callback 进度回调
     * @return 直方图列表
     */
    public static List<ImageHistogram> generateHistograms(File imageDir, ProgressCallback callback) {
        List<File> images = ImageResourceDownloader.getExistingImages(imageDir);
        List<ImageHistogram> histograms = new ArrayList<>();
        
        int total = images.size();
        int processed = 0;
        
        for (File imageFile : images) {
            try {
                ImageHistogram histogram = new ImageHistogram(imageFile);
                histograms.add(histogram);
                processed++;
                
                if (callback != null) {
                    callback.onProgress(processed, total, "已处理: " + imageFile.getName());
                }
            } catch (IOException e) {
                System.err.println("生成直方图失败: " + imageFile.getName() + " - " + e.getMessage());
                if (callback != null) {
                    callback.onError(processed, "处理失败: " + e.getMessage());
                }
            }
        }
        
        return histograms;
    }
    
    /**
     * 进度回调接口
     */
    public interface ProgressCallback {
        void onProgress(int current, int total, String message);
        void onError(int index, String errorMessage);
    }
}
