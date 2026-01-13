package com.analyzer.mapreduce;

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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Hadoop MapReduce任务：篡改检测
 * 检测图像是否被篡改并定位篡改区域
 */
public class TamperDetectionJob extends Configured implements Tool {
    
    private static final int BLOCK_SIZE = 16;
    
    /**
     * Mapper: 检测每张图像与疑似篡改图像的差异
     */
    public static class TamperMapper extends Mapper<Text, BytesWritable, Text, IntWritable> {
        private byte[] suspectImageData;
        private int threshold;
        
        @Override
        protected void setup(Context context) throws IOException, InterruptedException {
            super.setup(context);
            
            Configuration conf = context.getConfiguration();
            
            // 读取疑似篡改图像数据
            String suspectDataStr = conf.get("suspect.image.data");
            if (suspectDataStr != null) {
                suspectImageData = suspectDataStr.getBytes("ISO-8859-1");
            } else {
                throw new IOException("疑似篡改图像数据未设置");
            }
            
            // 读取差异阈值
            threshold = conf.getInt("tamper.threshold", 20);
        }
        
        @Override
        protected void map(Text key, BytesWritable value, Context context) 
                throws IOException, InterruptedException {
            String filename = key.toString();
            
            try {
                // 读取疑似篡改图像
                ByteArrayInputStream suspectBais = new ByteArrayInputStream(suspectImageData);
                BufferedImage suspectImage = ImageIO.read(suspectBais);
                
                if (suspectImage == null) {
                    System.err.println("MapReduce Map: 无法读取疑似篡改图像");
                    return;
                }
                
                // 读取目标图像
                ByteArrayInputStream targetBais = new ByteArrayInputStream(value.getBytes());
                BufferedImage targetImage = ImageIO.read(targetBais);
                
                if (targetImage == null) {
                    System.err.println("MapReduce Map: 处理失败: " + filename);
                    context.getCounter("Tamper Detection", "Failed to Read").increment(1);
                    return;
                }
                
                // 检查尺寸是否匹配
                if (suspectImage.getWidth() != targetImage.getWidth() || 
                    suspectImage.getHeight() != targetImage.getHeight()) {
                    return;
                }
                
                // 计算匹配像素数
                int matchingPixels = calculateMatchingPixels(suspectImage, targetImage, threshold);
                
                // 输出匹配像素数
                context.write(key, new IntWritable(matchingPixels));
                context.getCounter("Tamper Detection", "Processed").increment(1);
                
            } catch (Exception e) {
                System.err.println("MapReduce Map: 处理失败: " + filename);
                context.getCounter("Tamper Detection", "Processing Failed").increment(1);
            }
        }
        
        private int calculateMatchingPixels(BufferedImage suspect, BufferedImage target, int threshold) {
            int width = suspect.getWidth();
            int height = suspect.getHeight();
            int matchingPixels = 0;
            
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int suspectRgb = suspect.getRGB(x, y);
                    int targetRgb = target.getRGB(x, y);
                    
                    int suspectGray = rgbToGray(suspectRgb);
                    int targetGray = rgbToGray(targetRgb);
                    
                    if (Math.abs(suspectGray - targetGray) <= threshold) {
                        matchingPixels++;
                    }
                }
            }
            
            return matchingPixels;
        }
        
        private int rgbToGray(int rgb) {
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            return (int) (0.299 * r + 0.587 * g + 0.114 * b);
        }
    }
    
    /**
     * Reducer: 收集检测结果并按匹配度排序
     */
    public static class TamperReducer extends Reducer<Text, IntWritable, Text, IntWritable> {
        private List<TamperResult> results = new ArrayList<>();
        
        private static class TamperResult implements Comparable<TamperResult> {
            String filename;
            int matchingPixels;
            
            TamperResult(String filename, int matchingPixels) {
                this.filename = filename;
                this.matchingPixels = matchingPixels;
            }
            
            @Override
            public int compareTo(TamperResult other) {
                return Integer.compare(other.matchingPixels, this.matchingPixels);
            }
        }
        
        @Override
        protected void reduce(Text key, Iterable<IntWritable> values, Context context) 
                throws IOException, InterruptedException {
            int maxMatching = 0;
            for (IntWritable val : values) {
                maxMatching = Math.max(maxMatching, val.get());
            }
            results.add(new TamperResult(key.toString(), maxMatching));
        }
        
        @Override
        protected void cleanup(Context context) throws IOException, InterruptedException {
            super.cleanup(context);
            
            // 按匹配像素数降序排序
            Collections.sort(results);
            
            // 输出所有结果
            for (TamperResult result : results) {
                context.write(new Text(result.filename), new IntWritable(result.matchingPixels));
            }
            
            System.out.println("MapReduce任务完成");
        }
    }
    
    @Override
    public int run(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: TamperDetectionJob <input> <output>");
            return -1;
        }
        
        Configuration conf = getConf();
        Job job = Job.getInstance(conf, "Tamper Detection");
        
        job.setJarByClass(TamperDetectionJob.class);
        job.setMapperClass(TamperMapper.class);
        job.setReducerClass(TamperReducer.class);
        
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(IntWritable.class);
        
        job.setInputFormatClass(SequenceFileInputFormat.class);
        job.setOutputFormatClass(TextOutputFormat.class);
        
        // 使用一个Reducer以便全局排序
        job.setNumReduceTasks(1);
        
        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));
        
        return job.waitForCompletion(true) ? 0 : 1;
    }
    
    public static void main(String[] args) throws Exception {
        int exitCode = ToolRunner.run(new TamperDetectionJob(), args);
        System.exit(exitCode);
    }
}
