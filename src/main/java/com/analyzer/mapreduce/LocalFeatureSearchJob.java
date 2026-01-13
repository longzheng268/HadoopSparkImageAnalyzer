package com.analyzer.mapreduce;

import com.analyzer.core.LocalFeatureMatcher;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.SequenceFileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

/**
 * Hadoop MapReduce任务：局部特征搜索
 * 在大图中搜索包含指定小图的位置
 */
public class LocalFeatureSearchJob extends Configured implements Tool {
    
    private static final int PIXEL_MATCH_THRESHOLD = 5;
    
    /**
     * Mapper: 在每张图像中搜索局部特征
     */
    public static class FeatureSearchMapper extends Mapper<Text, BytesWritable, Text, IntWritable> {
        private byte[] featureImageData;
        private double threshold;
        
        @Override
        protected void setup(Context context) throws IOException, InterruptedException {
            super.setup(context);
            
            Configuration conf = context.getConfiguration();
            
            // 读取特征图像数据
            String featureDataStr = conf.get("feature.image.data");
            if (featureDataStr != null) {
                featureImageData = featureDataStr.getBytes("ISO-8859-1");
            } else {
                throw new IOException("特征图像数据未设置");
            }
            
            // 读取阈值
            threshold = conf.getDouble("feature.threshold", 0.95);
        }
        
        @Override
        protected void map(Text key, BytesWritable value, Context context) 
                throws IOException, InterruptedException {
            String filename = key.toString();
            
            try {
                // 读取特征图像
                ByteArrayInputStream featureBais = new ByteArrayInputStream(featureImageData);
                BufferedImage featureImage = ImageIO.read(featureBais);
                
                if (featureImage == null) {
                    System.err.println("MapReduce Map: 无法读取特征图像");
                    return;
                }
                
                // 读取目标图像
                ByteArrayInputStream targetBais = new ByteArrayInputStream(value.getBytes());
                BufferedImage targetImage = ImageIO.read(targetBais);
                
                if (targetImage == null) {
                    System.err.println("MapReduce Map: 处理失败: " + filename);
                    context.getCounter("Feature Search", "Failed to Read").increment(1);
                    return;
                }
                
                // 执行局部特征匹配
                int matchCount = matchFeatureInImage(featureImage, targetImage, threshold);
                
                if (matchCount > 0) {
                    context.write(key, new IntWritable(matchCount));
                    context.getCounter("Feature Search", "Match Found").increment(1);
                }
                
            } catch (Exception e) {
                System.err.println("MapReduce Map: 处理失败: " + filename);
                context.getCounter("Feature Search", "Processing Failed").increment(1);
            }
        }
        
        private int matchFeatureInImage(BufferedImage feature, BufferedImage target, double threshold) {
            int fWidth = feature.getWidth();
            int fHeight = feature.getHeight();
            int tWidth = target.getWidth();
            int tHeight = target.getHeight();
            
            if (fWidth > tWidth || fHeight > tHeight) {
                return 0;
            }
            
            int matchCount = 0;
            int totalFeaturePixels = fWidth * fHeight;
            int requiredMatches = (int) (totalFeaturePixels * threshold);
            
            // 滑动窗口搜索
            for (int y = 0; y <= tHeight - fHeight; y++) {
                for (int x = 0; x <= tWidth - fWidth; x++) {
                    int matches = 0;
                    
                    // 比较当前窗口
                    for (int fy = 0; fy < fHeight; fy++) {
                        for (int fx = 0; fx < fWidth; fx++) {
                            int fRgb = feature.getRGB(fx, fy);
                            int tRgb = target.getRGB(x + fx, y + fy);
                            
                            int fGray = rgbToGray(fRgb);
                            int tGray = rgbToGray(tRgb);
                            
                            if (Math.abs(fGray - tGray) <= PIXEL_MATCH_THRESHOLD) {
                                matches++;
                            }
                        }
                    }
                    
                    if (matches >= requiredMatches) {
                        matchCount++;
                    }
                }
            }
            
            return matchCount;
        }
        
        private int rgbToGray(int rgb) {
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            return (int) (0.299 * r + 0.587 * g + 0.114 * b);
        }
    }
    
    /**
     * Reducer: 收集匹配结果
     */
    public static class FeatureSearchReducer extends Reducer<Text, IntWritable, Text, IntWritable> {
        
        @Override
        protected void reduce(Text key, Iterable<IntWritable> values, Context context) 
                throws IOException, InterruptedException {
            int totalMatches = 0;
            for (IntWritable val : values) {
                totalMatches += val.get();
            }
            context.write(key, new IntWritable(totalMatches));
        }
        
        @Override
        protected void cleanup(Context context) throws IOException, InterruptedException {
            super.cleanup(context);
            System.out.println("MapReduce任务完成");
        }
    }
    
    @Override
    public int run(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: LocalFeatureSearchJob <input> <output>");
            return -1;
        }
        
        Configuration conf = getConf();
        Job job = Job.getInstance(conf, "Local Feature Search");
        
        job.setJarByClass(LocalFeatureSearchJob.class);
        job.setMapperClass(FeatureSearchMapper.class);
        job.setReducerClass(FeatureSearchReducer.class);
        
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(IntWritable.class);
        
        job.setInputFormatClass(SequenceFileInputFormat.class);
        job.setOutputFormatClass(TextOutputFormat.class);
        
        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));
        
        return job.waitForCompletion(true) ? 0 : 1;
    }
    
    public static void main(String[] args) throws Exception {
        int exitCode = ToolRunner.run(new LocalFeatureSearchJob(), args);
        System.exit(exitCode);
    }
}
