package com.analyzer.core;

import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import scala.Tuple2;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 全图搜索匹配器
 * 使用Spark RDD进行分布式直方图比对
 */
public class ImageMatcher {
    
    /**
     * 搜索结果
     */
    public static class MatchResult implements Serializable {
        private static final long serialVersionUID = 1L;
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
     * 在图像库中搜索匹配的图像（从HBase读取数据，使用Spark RDD进行分布式比对）
     * 
     * @param queryImage 查询图像文件
     * @param imageLibrary 图像库目录（用于本地文件读取）
     * @param topN 返回前N个最匹配的结果
     * @return 匹配结果列表（按相似度降序排列）
     * @throws IOException 处理失败时抛出
     */
    public static List<MatchResult> searchImage(File queryImage, File imageLibrary, int topN) throws IOException {
        System.out.println("=== 使用Spark RDD从HBase读取数据进行分布式全图搜索 ===");
        
        // 生成查询图像的直方图
        ImageHistogram queryHistogram = new ImageHistogram(queryImage);
        
        // 从HBase读取所有图像的直方图数据
        List<ImageHistogram> libraryHistograms;
        try {
            libraryHistograms = HBaseManager.getAllHistograms();
            System.out.println("从HBase读取了 " + libraryHistograms.size() + " 个直方图");
        } catch (Exception e) {
            System.err.println("从HBase读取失败，降级使用本地文件: " + e.getMessage());
            // 降级：使用本地文件
            List<File> libraryImages = ImageResourceDownloader.getExistingImages(imageLibrary);
            libraryHistograms = new ArrayList<>();
            for (File f : libraryImages) {
                try {
                    libraryHistograms.add(new ImageHistogram(f));
                } catch (IOException ex) {
                    System.err.println("读取本地文件失败: " + f.getName());
                }
            }
        }
        
        if (libraryHistograms.isEmpty()) {
            throw new IOException("图像库为空（HBase和本地文件都没有数据）");
        }
        
        System.out.println("图像库大小: " + libraryHistograms.size() + " 张图像");
        
        // 获取Spark上下文
        JavaSparkContext sc = SparkContextManager.getOrCreateContext();
        System.out.println("Spark UI: " + SparkContextManager.getSparkUIUrl());
        
        // 创建RDD并行处理直方图比对
        JavaRDD<ImageHistogram> histogramsRDD = sc.parallelize(libraryHistograms);
        
        // 并行计算相似度
        List<MatchResult> results = histogramsRDD
            .map(libHistogram -> {
                double similarity = queryHistogram.calculateSimilarity(libHistogram);
                return new MatchResult(libHistogram.getFilename(), similarity);
            })
            .collect();
        
        System.out.println("Spark任务完成，处理了 " + results.size() + " 张图像");
        
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
     * 批量生成图像直方图并存储到HBase（使用Spark RDD进行分布式处理）
     * 
     * @param imageDir 图像目录
     * @param callback 进度回调
     * @return 直方图列表
     */
    public static List<ImageHistogram> generateHistograms(File imageDir, ProgressCallback callback) {
        System.out.println("=== 使用Spark RDD进行分布式直方图生成并存储到HBase ===");
        
        List<File> images = ImageResourceDownloader.getExistingImages(imageDir);
        int total = images.size();
        
        System.out.println("待处理图像数量: " + total);
        
        // 获取Spark上下文
        JavaSparkContext sc = SparkContextManager.getOrCreateContext();
        System.out.println("Spark UI: " + SparkContextManager.getSparkUIUrl());
        
        // 创建RDD并行处理
        JavaRDD<File> imagesRDD = sc.parallelize(images);
        
        // 并行生成直方图并存储到HBase
        List<ImageHistogram> histograms = imagesRDD
            .map(imageFile -> {
                try {
                    ImageHistogram histogram = new ImageHistogram(imageFile);
                    // 存储到HBase
                    try {
                        HBaseManager.storeImage(imageFile, histogram);
                        System.out.println("已存储到HBase: " + imageFile.getName());
                    } catch (Exception e) {
                        System.err.println("存储到HBase失败: " + imageFile.getName() + " - " + e.getMessage());
                    }
                    return histogram;
                } catch (IOException e) {
                    System.err.println("生成直方图失败: " + imageFile.getName() + " - " + e.getMessage());
                    return null;
                }
            })
            .filter(h -> h != null)
            .collect();
        
        System.out.println("Spark任务完成，成功生成并存储 " + histograms.size() + " 个直方图到HBase");
        
        // 更新进度
        if (callback != null) {
            callback.onProgress(histograms.size(), total, "Spark并行处理完成，已存储到HBase");
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
