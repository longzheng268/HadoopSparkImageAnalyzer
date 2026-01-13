package com.analyzer.core;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * 图像直方图生成器
 * 统计图像中每个灰度值（0-255）出现的像素数量
 */
public class ImageHistogram {
    
    // 直方图数据：索引0-255对应灰度值0-255，值为该灰度出现的像素数
    private int[] histogram;
    
    // 图像尺寸
    private int width;
    private int height;
    
    // 图像文件名
    private String filename;
    
    /**
     * 从图像文件生成直方图
     * 
     * @param imageFile 图像文件
     * @throws IOException 读取图像失败时抛出
     */
    public ImageHistogram(File imageFile) throws IOException {
        this.filename = imageFile.getName();
        this.histogram = new int[256];
        
        BufferedImage image = ImageIO.read(imageFile);
        if (image == null) {
            throw new IOException("无法读取图像: " + imageFile.getName());
        }
        
        this.width = image.getWidth();
        this.height = image.getHeight();
        
        // 统计每个灰度值的像素数
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                // 转换为灰度值（使用标准加权平均法）
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int gray = (int) (0.299 * r + 0.587 * g + 0.114 * b);
                histogram[gray]++;
            }
        }
    }
    
    /**
     * 使用直方图数据构造
     * 
     * @param histogram 直方图数组
     * @param width 图像宽度
     * @param height 图像高度
     * @param filename 文件名
     */
    public ImageHistogram(int[] histogram, int width, int height, String filename) {
        this.histogram = histogram;
        this.width = width;
        this.height = height;
        this.filename = filename;
    }
    
    /**
     * 计算与另一个直方图的相似度（使用直方图交集法）
     * 
     * @param other 另一个直方图
     * @return 相似度分数（0-1之间，1表示完全相同）
     */
    public double calculateSimilarity(ImageHistogram other) {
        double intersection = 0;
        double total = 0;
        
        for (int i = 0; i < 256; i++) {
            intersection += Math.min(this.histogram[i], other.histogram[i]);
            total += Math.max(this.histogram[i], other.histogram[i]);
        }
        
        return total > 0 ? intersection / total : 0;
    }
    
    /**
     * 获取直方图数组
     */
    public int[] getHistogram() {
        return histogram;
    }
    
    /**
     * 获取图像宽度
     */
    public int getWidth() {
        return width;
    }
    
    /**
     * 获取图像高度
     */
    public int getHeight() {
        return height;
    }
    
    /**
     * 获取文件名
     */
    public String getFilename() {
        return filename;
    }
    
    /**
     * 获取直方图摘要信息
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("文件名: ").append(filename).append("\n");
        sb.append("尺寸: ").append(width).append("x").append(height).append("\n");
        sb.append("总像素数: ").append(width * height).append("\n");
        
        // 统计非零灰度值数量
        int nonZeroCount = 0;
        for (int count : histogram) {
            if (count > 0) nonZeroCount++;
        }
        sb.append("使用的灰度值种类: ").append(nonZeroCount).append("/256\n");
        
        return sb.toString();
    }
    
    /**
     * 转换为详细字符串（用于存储）
     */
    public String toDetailString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Filename:").append(filename).append("\n");
        sb.append("Size:").append(width).append("x").append(height).append("\n");
        sb.append("Histogram:");
        for (int i = 0; i < histogram.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(histogram[i]);
        }
        return sb.toString();
    }
}
