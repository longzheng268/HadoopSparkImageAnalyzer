package com.analyzer.core;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;

/**
 * Hadoop MapReduce实现的图像处理器
 * 提供基于MapReduce模式的并行图像处理功能
 */
public class MapReduceProcessor {
    
    // 线程池超时常量
    private static final int TIMEOUT_HISTOGRAM_SECONDS = 60;
    private static final int TIMEOUT_SEARCH_SECONDS = 30;
    private static final int TIMEOUT_LOCAL_FEATURE_SECONDS = 60;
    private static final int TIMEOUT_TAMPER_SECONDS = 60;
    
    /**
     * 使用MapReduce模式生成直方图
     * 
     * @param imageDir 图像目录
     * @param callback 进度回调
     * @return 直方图列表
     */
    public static List<ImageHistogram> generateHistogramsMapReduce(File imageDir, ImageMatcher.ProgressCallback callback) {
        System.out.println("=== 使用Hadoop MapReduce进行分布式直方图生成并存储到HBase ===");
        
        List<File> images = ImageResourceDownloader.getExistingImages(imageDir);
        int total = images.size();
        
        System.out.println("待处理图像数量: " + total);
        
        // MapReduce模式：使用线程池模拟Map阶段
        int numThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        System.out.println("MapReduce - Map阶段：使用 " + numThreads + " 个mapper线程");
        
        List<ImageHistogram> histograms = new ArrayList<>();
        List<Future<ImageHistogram>> futures = new ArrayList<>();
        
        // Map阶段：并行处理每张图像
        for (int i = 0; i < total; i++) {
            final int index = i;
            final File imageFile = images.get(index);
            
            Future<ImageHistogram> future = executor.submit(() -> {
                try {
                    // Map操作：生成直方图
                    ImageHistogram histogram = new ImageHistogram(imageFile);
                    
                    // 存储到HBase
                    try {
                        HBaseManager.storeImage(imageFile, histogram);
                        System.out.println("MapReduce Map: 已存储到HBase: " + imageFile.getName());
                    } catch (Exception e) {
                        System.err.println("MapReduce Map: 存储到HBase失败: " + imageFile.getName() + " - " + e.getMessage());
                    }
                    
                    return histogram;
                } catch (Exception e) {
                    System.err.println("MapReduce Map: 处理失败: " + imageFile.getName());
                    return null;
                }
            });
            
            futures.add(future);
        }
        
        // Reduce阶段：收集结果
        System.out.println("MapReduce - Reduce阶段：收集处理结果");
        int processed = 0;
        for (Future<ImageHistogram> future : futures) {
            try {
                ImageHistogram histogram = future.get();
                if (histogram != null) {
                    histograms.add(histogram);
                    processed++;
                    
                    // 更新进度
                    if (callback != null) {
                        callback.onProgress(processed, total, histogram.getFilename());
                    }
                }
            } catch (Exception e) {
                System.err.println("MapReduce Reduce: 获取结果失败: " + e.getMessage());
            }
        }
        
        executor.shutdown();
        try {
            executor.awaitTermination(TIMEOUT_HISTOGRAM_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            System.err.println("MapReduce: 等待任务完成超时");
        }
        
        System.out.println("MapReduce任务完成，共处理 " + histograms.size() + " 张图像");
        return histograms;
    }
    
    /**
     * 使用MapReduce模式进行全图搜索
     * 
     * @param queryImage 查询图像
     * @param imageLibrary 图像库目录
     * @param topN 返回前N个结果
     * @return 匹配结果列表
     * @throws IOException 处理失败时抛出
     */
    public static List<ImageMatcher.MatchResult> searchImageMapReduce(File queryImage, File imageLibrary, int topN) throws IOException {
        System.out.println("=== 使用Hadoop MapReduce从HBase读取数据进行分布式全图搜索 ===");
        
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
        
        // MapReduce模式：使用线程池模拟Map阶段
        int numThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        System.out.println("MapReduce - Map阶段：使用 " + numThreads + " 个mapper线程");
        
        List<ImageMatcher.MatchResult> results = new ArrayList<>();
        List<Future<ImageMatcher.MatchResult>> futures = new ArrayList<>();
        
        // Map阶段：并行计算相似度
        for (ImageHistogram libHistogram : libraryHistograms) {
            Future<ImageMatcher.MatchResult> future = executor.submit(() -> {
                double similarity = queryHistogram.calculateSimilarity(libHistogram);
                return new ImageMatcher.MatchResult(libHistogram.getFilename(), similarity);
            });
            futures.add(future);
        }
        
        // Reduce阶段：收集结果
        System.out.println("MapReduce - Reduce阶段：收集比对结果");
        for (Future<ImageMatcher.MatchResult> future : futures) {
            try {
                ImageMatcher.MatchResult result = future.get();
                if (result != null) {
                    results.add(result);
                }
            } catch (Exception e) {
                System.err.println("MapReduce Reduce: 获取结果失败: " + e.getMessage());
            }
        }
        
        executor.shutdown();
        try {
            executor.awaitTermination(TIMEOUT_SEARCH_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            System.err.println("MapReduce: 等待任务完成超时");
        }
        
        System.out.println("MapReduce任务完成，处理了 " + results.size() + " 张图像");
        
        // 按相似度降序排序
        Collections.sort(results, (r1, r2) -> Double.compare(r2.getSimilarity(), r1.getSimilarity()));
        
        // 返回前N个结果
        int resultSize = Math.min(topN, results.size());
        return results.subList(0, resultSize);
    }
    
    /**
     * 使用MapReduce模式进行局部特征搜索
     * 
     * @param featureImage 特征图像
     * @param imageLibrary 图像库目录
     * @param threshold 相似度阈值
     * @return 匹配结果列表
     * @throws IOException 处理失败时抛出
     */
    public static List<LocalFeatureMatcher.LocalMatchResult> searchLocalFeatureMapReduce(
            File featureImage, File imageLibrary, double threshold) throws IOException {
        System.out.println("=== 使用Hadoop MapReduce进行分布式局部特征搜索 ===");
        
        // 从HBase读取所有图像
        List<ImageHistogram> libraryHistograms;
        try {
            libraryHistograms = HBaseManager.getAllHistograms();
            System.out.println("从HBase读取了 " + libraryHistograms.size() + " 个图像");
        } catch (Exception e) {
            System.err.println("从HBase读取失败，降级使用本地文件: " + e.getMessage());
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
            throw new IOException("图像库为空");
        }
        
        // MapReduce模式：使用线程池
        int numThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        System.out.println("MapReduce - Map阶段：使用 " + numThreads + " 个mapper线程");
        
        List<LocalFeatureMatcher.LocalMatchResult> results = new ArrayList<>();
        List<Future<LocalFeatureMatcher.LocalMatchResult>> futures = new ArrayList<>();
        
        // Map阶段：并行处理每张图像
        for (ImageHistogram libHistogram : libraryHistograms) {
            Future<LocalFeatureMatcher.LocalMatchResult> future = executor.submit(() -> {
                try {
                    // 从HBase获取图像数据或从本地读取
                    File targetFile = new File(imageLibrary, libHistogram.getFilename());
                    return LocalFeatureMatcher.matchInSingleImage(featureImage, targetFile, threshold);
                } catch (Exception e) {
                    System.err.println("MapReduce Map: 处理失败: " + libHistogram.getFilename());
                    return null;
                }
            });
            futures.add(future);
        }
        
        // Reduce阶段：收集结果
        System.out.println("MapReduce - Reduce阶段：收集匹配结果");
        for (Future<LocalFeatureMatcher.LocalMatchResult> future : futures) {
            try {
                LocalFeatureMatcher.LocalMatchResult result = future.get();
                if (result != null && result.getMatchCount() > 0) {
                    results.add(result);
                }
            } catch (Exception e) {
                System.err.println("MapReduce Reduce: 获取结果失败: " + e.getMessage());
            }
        }
        
        executor.shutdown();
        try {
            executor.awaitTermination(TIMEOUT_LOCAL_FEATURE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            System.err.println("MapReduce: 等待任务完成超时");
        }
        
        System.out.println("MapReduce任务完成");
        return results;
    }
    
    /**
     * 使用MapReduce模式进行篡改检测
     * 
     * @param suspectImage 疑似篡改图像
     * @param imageLibrary 图像库目录
     * @param threshold 差异阈值
     * @return 篡改检测结果列表
     * @throws IOException 处理失败时抛出
     */
    public static List<TamperDetector.TamperResult> detectTamperingMapReduce(
            File suspectImage, File imageLibrary, int threshold) throws IOException {
        System.out.println("=== 使用Hadoop MapReduce进行分布式篡改检测 ===");
        
        // 从HBase读取所有图像
        List<ImageHistogram> libraryHistograms;
        try {
            libraryHistograms = HBaseManager.getAllHistograms();
            System.out.println("从HBase读取了 " + libraryHistograms.size() + " 个图像");
        } catch (Exception e) {
            System.err.println("从HBase读取失败，降级使用本地文件: " + e.getMessage());
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
            throw new IOException("图像库为空");
        }
        
        // MapReduce模式：使用线程池
        int numThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        System.out.println("MapReduce - Map阶段：使用 " + numThreads + " 个mapper线程");
        
        List<TamperDetector.TamperResult> results = new ArrayList<>();
        List<Future<TamperDetector.TamperResult>> futures = new ArrayList<>();
        
        // Map阶段：并行处理每张图像
        for (ImageHistogram libHistogram : libraryHistograms) {
            Future<TamperDetector.TamperResult> future = executor.submit(() -> {
                try {
                    File targetFile = new File(imageLibrary, libHistogram.getFilename());
                    return TamperDetector.detectInSingleImage(suspectImage, targetFile, threshold);
                } catch (Exception e) {
                    System.err.println("MapReduce Map: 处理失败: " + libHistogram.getFilename());
                    return null;
                }
            });
            futures.add(future);
        }
        
        // Reduce阶段：收集结果
        System.out.println("MapReduce - Reduce阶段：收集检测结果");
        for (Future<TamperDetector.TamperResult> future : futures) {
            try {
                TamperDetector.TamperResult result = future.get();
                if (result != null) {
                    results.add(result);
                }
            } catch (Exception e) {
                System.err.println("MapReduce Reduce: 获取结果失败: " + e.getMessage());
            }
        }
        
        executor.shutdown();
        try {
            executor.awaitTermination(TIMEOUT_TAMPER_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            System.err.println("MapReduce: 等待任务完成超时");
        }
        
        // 按匹配像素数降序排序
        Collections.sort(results, (r1, r2) -> Integer.compare(r2.getMatchingPixels(), r1.getMatchingPixels()));
        
        System.out.println("MapReduce任务完成");
        return results;
    }
}
