package com.analyzer.mapreduce;

import com.analyzer.core.ImageHistogram;
import com.analyzer.core.ImageResourceDownloader;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.util.ToolRunner;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Hadoop MapReduce任务提交管理器
 * 负责准备输入数据、提交任务到YARN、解析输出结果
 */
public class HadoopJobSubmitter {
    
    private static final String HDFS_TEMP_DIR = "/tmp/image-analyzer";
    
    /**
     * 提交直方图生成任务
     * 
     * @param imageDir 图像目录
     * @return 任务是否成功
     */
    public static boolean submitHistogramJob(File imageDir) throws Exception {
        Configuration conf = new Configuration();
        FileSystem fs = FileSystem.get(conf);
        
        // 准备输入数据：将图像写入SequenceFile
        String inputPath = HDFS_TEMP_DIR + "/histogram-input-" + System.currentTimeMillis();
        String outputPath = HDFS_TEMP_DIR + "/histogram-output-" + System.currentTimeMillis();
        
        Path inputPathObj = new Path(inputPath);
        Path outputPathObj = new Path(outputPath);
        
        // 删除已存在的路径
        if (fs.exists(inputPathObj)) {
            fs.delete(inputPathObj, true);
        }
        if (fs.exists(outputPathObj)) {
            fs.delete(outputPathObj, true);
        }
        
        // 创建SequenceFile写入图像数据
        List<File> images = ImageResourceDownloader.getExistingImages(imageDir);
        Path seqFilePath = new Path(inputPath + "/images.seq");
        
        try (SequenceFile.Writer writer = SequenceFile.createWriter(
                conf,
                SequenceFile.Writer.file(seqFilePath),
                SequenceFile.Writer.keyClass(Text.class),
                SequenceFile.Writer.valueClass(BytesWritable.class))) {
            
            for (File imageFile : images) {
                try {
                    BufferedImage img = ImageIO.read(imageFile);
                    if (img != null) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ImageIO.write(img, "jpg", baos);
                        byte[] imageBytes = baos.toByteArray();
                        
                        writer.append(new Text(imageFile.getName()), new BytesWritable(imageBytes));
                    }
                } catch (Exception e) {
                    System.err.println("跳过无法读取的图像: " + imageFile.getName());
                }
            }
        }
        
        System.out.println("输入数据已准备: " + inputPath);
        
        // 提交MapReduce任务到YARN
        String[] args = {inputPath, outputPath};
        int exitCode = ToolRunner.run(conf, new HistogramGenerationJob(), args);
        
        // 清理临时文件
        try {
            fs.delete(inputPathObj, true);
            fs.delete(outputPathObj, true);
        } catch (Exception e) {
            System.err.println("清理临时文件失败: " + e.getMessage());
        }
        
        return exitCode == 0;
    }
    
    /**
     * 提交全图搜索任务
     * 
     * @param queryHistogram 查询图像直方图
     * @param libraryHistograms 图像库直方图列表
     * @param topN 返回前N个结果
     * @return 任务是否成功
     */
    public static boolean submitSearchJob(ImageHistogram queryHistogram, 
                                         List<ImageHistogram> libraryHistograms, 
                                         int topN) throws Exception {
        Configuration conf = new Configuration();
        FileSystem fs = FileSystem.get(conf);
        
        // 设置查询直方图
        String histStr = histogramToString(queryHistogram.getHistogram());
        conf.set("query.histogram", histStr);
        conf.setInt("search.topn", topN);
        
        // 准备输入数据：将直方图写入文本文件
        String inputPath = HDFS_TEMP_DIR + "/search-input-" + System.currentTimeMillis();
        String outputPath = HDFS_TEMP_DIR + "/search-output-" + System.currentTimeMillis();
        
        Path inputPathObj = new Path(inputPath);
        Path outputPathObj = new Path(outputPath);
        
        if (fs.exists(inputPathObj)) {
            fs.delete(inputPathObj, true);
        }
        if (fs.exists(outputPathObj)) {
            fs.delete(outputPathObj, true);
        }
        
        // 写入直方图数据
        Path dataPath = new Path(inputPath + "/histograms.txt");
        try (FSDataOutputStream out = fs.create(dataPath)) {
            for (ImageHistogram hist : libraryHistograms) {
                String line = hist.getFilename() + "\t" + histogramToString(hist.getHistogram()) + "\n";
                out.write(line.getBytes("UTF-8"));
            }
        }
        
        System.out.println("输入数据已准备: " + inputPath);
        
        // 提交MapReduce任务到YARN
        String[] args = {inputPath, outputPath};
        int exitCode = ToolRunner.run(conf, new ImageSearchJob(), args);
        
        // 清理临时文件
        try {
            fs.delete(inputPathObj, true);
            fs.delete(outputPathObj, true);
        } catch (Exception e) {
            System.err.println("清理临时文件失败: " + e.getMessage());
        }
        
        return exitCode == 0;
    }
    
    /**
     * 提交局部特征搜索任务
     * 
     * @param featureImage 特征图像
     * @param imageDir 图像目录
     * @param threshold 相似度阈值
     * @return 任务是否成功
     */
    public static boolean submitLocalFeatureJob(File featureImage, File imageDir, double threshold) throws Exception {
        Configuration conf = new Configuration();
        FileSystem fs = FileSystem.get(conf);
        
        // 读取特征图像数据
        BufferedImage feature = ImageIO.read(featureImage);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(feature, "jpg", baos);
        byte[] featureData = baos.toByteArray();
        
        // 设置特征图像数据和阈值
        conf.set("feature.image.data", new String(featureData, "ISO-8859-1"));
        conf.setDouble("feature.threshold", threshold);
        
        // 准备输入数据
        String inputPath = HDFS_TEMP_DIR + "/feature-input-" + System.currentTimeMillis();
        String outputPath = HDFS_TEMP_DIR + "/feature-output-" + System.currentTimeMillis();
        
        Path inputPathObj = new Path(inputPath);
        Path outputPathObj = new Path(outputPath);
        
        if (fs.exists(inputPathObj)) {
            fs.delete(inputPathObj, true);
        }
        if (fs.exists(outputPathObj)) {
            fs.delete(outputPathObj, true);
        }
        
        // 创建SequenceFile写入图像数据
        List<File> images = ImageResourceDownloader.getExistingImages(imageDir);
        Path seqFilePath = new Path(inputPath + "/images.seq");
        
        try (SequenceFile.Writer writer = SequenceFile.createWriter(
                conf,
                SequenceFile.Writer.file(seqFilePath),
                SequenceFile.Writer.keyClass(Text.class),
                SequenceFile.Writer.valueClass(BytesWritable.class))) {
            
            for (File imageFile : images) {
                try {
                    BufferedImage img = ImageIO.read(imageFile);
                    if (img != null) {
                        ByteArrayOutputStream imgBaos = new ByteArrayOutputStream();
                        ImageIO.write(img, "jpg", imgBaos);
                        byte[] imageBytes = imgBaos.toByteArray();
                        
                        writer.append(new Text(imageFile.getName()), new BytesWritable(imageBytes));
                    }
                } catch (Exception e) {
                    System.err.println("跳过无法读取的图像: " + imageFile.getName());
                }
            }
        }
        
        System.out.println("输入数据已准备: " + inputPath);
        
        // 提交MapReduce任务到YARN
        String[] args = {inputPath, outputPath};
        int exitCode = ToolRunner.run(conf, new LocalFeatureSearchJob(), args);
        
        // 清理临时文件
        try {
            fs.delete(inputPathObj, true);
            fs.delete(outputPathObj, true);
        } catch (Exception e) {
            System.err.println("清理临时文件失败: " + e.getMessage());
        }
        
        return exitCode == 0;
    }
    
    /**
     * 提交篡改检测任务
     * 
     * @param suspectImage 疑似篡改图像
     * @param imageDir 图像目录
     * @param threshold 差异阈值
     * @return 任务是否成功
     */
    public static boolean submitTamperDetectionJob(File suspectImage, File imageDir, int threshold) throws Exception {
        Configuration conf = new Configuration();
        FileSystem fs = FileSystem.get(conf);
        
        // 读取疑似篡改图像数据
        BufferedImage suspect = ImageIO.read(suspectImage);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(suspect, "jpg", baos);
        byte[] suspectData = baos.toByteArray();
        
        // 设置疑似篡改图像数据和阈值
        conf.set("suspect.image.data", new String(suspectData, "ISO-8859-1"));
        conf.setInt("tamper.threshold", threshold);
        
        // 准备输入数据
        String inputPath = HDFS_TEMP_DIR + "/tamper-input-" + System.currentTimeMillis();
        String outputPath = HDFS_TEMP_DIR + "/tamper-output-" + System.currentTimeMillis();
        
        Path inputPathObj = new Path(inputPath);
        Path outputPathObj = new Path(outputPath);
        
        if (fs.exists(inputPathObj)) {
            fs.delete(inputPathObj, true);
        }
        if (fs.exists(outputPathObj)) {
            fs.delete(outputPathObj, true);
        }
        
        // 创建SequenceFile写入图像数据
        List<File> images = ImageResourceDownloader.getExistingImages(imageDir);
        Path seqFilePath = new Path(inputPath + "/images.seq");
        
        try (SequenceFile.Writer writer = SequenceFile.createWriter(
                conf,
                SequenceFile.Writer.file(seqFilePath),
                SequenceFile.Writer.keyClass(Text.class),
                SequenceFile.Writer.valueClass(BytesWritable.class))) {
            
            for (File imageFile : images) {
                try {
                    BufferedImage img = ImageIO.read(imageFile);
                    if (img != null) {
                        ByteArrayOutputStream imgBaos = new ByteArrayOutputStream();
                        ImageIO.write(img, "jpg", imgBaos);
                        byte[] imageBytes = imgBaos.toByteArray();
                        
                        writer.append(new Text(imageFile.getName()), new BytesWritable(imageBytes));
                    }
                } catch (Exception e) {
                    System.err.println("跳过无法读取的图像: " + imageFile.getName());
                }
            }
        }
        
        System.out.println("输入数据已准备: " + inputPath);
        
        // 提交MapReduce任务到YARN
        String[] args = {inputPath, outputPath};
        int exitCode = ToolRunner.run(conf, new TamperDetectionJob(), args);
        
        // 清理临时文件
        try {
            fs.delete(inputPathObj, true);
            fs.delete(outputPathObj, true);
        } catch (Exception e) {
            System.err.println("清理临时文件失败: " + e.getMessage());
        }
        
        return exitCode == 0;
    }
    
    /**
     * 将直方图数组转换为逗号分隔的字符串
     */
    private static String histogramToString(int[] histogram) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < histogram.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(histogram[i]);
        }
        return sb.toString();
    }
}
