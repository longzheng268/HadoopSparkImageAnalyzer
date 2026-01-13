package com.analyzer.core;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * 图像资源下载器
 * 从可靠的图像源下载样本图像用于分析
 * 使用 Lorem Picsum (picsum.photos) API 提供免费、稳定的灰度图像
 */
public class ImageResourceDownloader {
    
    // 图像服务API基础URL（Lorem Picsum提供稳定的免费图像服务）
    private static final String IMAGE_API_BASE = "https://picsum.photos";
    
    // 默认图像尺寸（512x512，符合BOSSBase数据库规格）
    private static final int DEFAULT_IMAGE_SIZE = 512;
    
    // 下载超时时间（毫秒）
    private static final int TIMEOUT_MS = 30000;
    
    /**
     * 下载指定数量的灰度图像到目标目录
     * 
     * @param targetDir 目标目录
     * @param count 下载数量
     * @param progressCallback 进度回调接口（可选）
     * @return 下载成功的图像文件列表
     * @throws IOException 下载失败时抛出
     */
    public static List<File> downloadGrayscaleImages(File targetDir, int count, ProgressCallback progressCallback) throws IOException {
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }
        
        List<File> downloadedFiles = new ArrayList<>();
        
        for (int i = 1; i <= count; i++) {
            try {
                // 使用不同的seed值确保每次获取不同的图像
                // grayscale参数将图像转换为灰度
                String imageUrl = String.format("%s/%d/%d?grayscale&random=%d", 
                    IMAGE_API_BASE, DEFAULT_IMAGE_SIZE, DEFAULT_IMAGE_SIZE, System.currentTimeMillis() + i);
                
                File outputFile = new File(targetDir, String.format("sample_%03d.jpg", i));
                
                // 下载图像
                downloadImage(imageUrl, outputFile);
                downloadedFiles.add(outputFile);
                
                // 调用进度回调
                if (progressCallback != null) {
                    progressCallback.onProgress(i, count, "已下载: " + outputFile.getName());
                }
                
                // 短暂延迟避免API请求过快（300ms是合理的速率限制）
                Thread.sleep(300);
                
            } catch (Exception e) {
                System.err.println("下载第 " + i + " 张图像失败: " + e.getMessage());
                if (progressCallback != null) {
                    progressCallback.onError(i, e.getMessage());
                }
            }
        }
        
        return downloadedFiles;
    }
    
    /**
     * 下载单张图像
     * 
     * @param imageUrl 图像URL
     * @param outputFile 输出文件
     * @throws IOException 下载失败时抛出
     */
    private static void downloadImage(String imageUrl, File outputFile) throws IOException {
        URL url = new URL(imageUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", "HadoopSparkImageAnalyzer/1.0");
            
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP错误码: " + responseCode);
            }
            
            try (InputStream inputStream = connection.getInputStream()) {
                BufferedImage image = ImageIO.read(inputStream);
                
                if (image == null) {
                    throw new IOException("无法解析图像数据");
                }
                
                // 保存图像
                ImageIO.write(image, "jpg", outputFile);
            }
        } finally {
            connection.disconnect();
        }
    }
    
    /**
     * 获取资源目录中已有的图像文件
     * 
     * @param resourceDir 资源目录
     * @return 图像文件列表
     */
    public static List<File> getExistingImages(File resourceDir) {
        List<File> imageFiles = new ArrayList<>();
        
        if (resourceDir.exists() && resourceDir.isDirectory()) {
            File[] files = resourceDir.listFiles((dir, name) -> {
                String lowerName = name.toLowerCase();
                return lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || 
                       lowerName.endsWith(".png") || lowerName.endsWith(".bmp");
            });
            
            if (files != null) {
                for (File file : files) {
                    imageFiles.add(file);
                }
            }
        }
        
        return imageFiles;
    }
    
    /**
     * 进度回调接口
     */
    public interface ProgressCallback {
        /**
         * 进度更新
         * 
         * @param current 当前进度
         * @param total 总数
         * @param message 消息
         */
        void onProgress(int current, int total, String message);
        
        /**
         * 错误回调
         * 
         * @param index 索引
         * @param errorMessage 错误消息
         */
        void onError(int index, String errorMessage);
    }
}
