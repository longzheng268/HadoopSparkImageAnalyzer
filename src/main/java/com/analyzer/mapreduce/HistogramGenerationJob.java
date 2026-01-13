package com.analyzer.mapreduce;

import com.analyzer.core.ImageHistogram;
import com.analyzer.core.HBaseManager;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.SequenceFileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.NullOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

/**
 * Hadoop MapReduce任务：图像直方图生成
 * 使用真正的Hadoop MapReduce框架进行分布式处理
 */
public class HistogramGenerationJob extends Configured implements Tool {
    
    /**
     * Mapper: 处理每张图像，生成直方图并存储到HBase
     */
    public static class HistogramMapper extends Mapper<Text, BytesWritable, Text, NullWritable> {
        
        @Override
        protected void setup(Context context) throws IOException, InterruptedException {
            super.setup(context);
            // 初始化HBase连接
            try {
                HBaseManager.initialize();
            } catch (IOException e) {
                System.err.println("HBase初始化失败: " + e.getMessage());
            }
        }
        
        @Override
        protected void map(Text key, BytesWritable value, Context context) 
                throws IOException, InterruptedException {
            String filename = key.toString();
            byte[] imageData = value.getBytes();
            
            try {
                // 从字节数组读取图像
                ByteArrayInputStream bais = new ByteArrayInputStream(imageData);
                BufferedImage image = ImageIO.read(bais);
                
                if (image == null) {
                    System.err.println("MapReduce Map: 无法读取图像: " + filename);
                    context.getCounter("Image Processing", "Failed to Read").increment(1);
                    return;
                }
                
                // 生成直方图
                int[] histogram = new int[256];
                int width = image.getWidth();
                int height = image.getHeight();
                
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int rgb = image.getRGB(x, y);
                        int r = (rgb >> 16) & 0xFF;
                        int g = (rgb >> 8) & 0xFF;
                        int b = rgb & 0xFF;
                        int gray = (int) (0.299 * r + 0.587 * g + 0.114 * b);
                        histogram[gray]++;
                    }
                }
                
                // 创建ImageHistogram对象
                ImageHistogram imgHist = new ImageHistogram(histogram, width, height, filename);
                
                // 存储到HBase
                try {
                    // 从临时文件创建File对象用于存储
                    File tempFile = File.createTempFile("img_", "_" + filename);
                    ImageIO.write(image, "jpg", tempFile);
                    HBaseManager.storeImage(tempFile, imgHist);
                    tempFile.delete();
                    
                    System.out.println("MapReduce Map: 已存储到HBase: " + filename);
                    context.getCounter("Image Processing", "Success").increment(1);
                    context.write(key, NullWritable.get());
                } catch (Exception e) {
                    System.err.println("MapReduce Map: 存储到HBase失败: " + filename + " - " + e.getMessage());
                    context.getCounter("Image Processing", "HBase Store Failed").increment(1);
                }
                
            } catch (Exception e) {
                System.err.println("MapReduce Map: 处理失败: " + filename + " - " + e.getMessage());
                context.getCounter("Image Processing", "Processing Failed").increment(1);
            }
        }
    }
    
    /**
     * Reducer: 汇总处理结果
     */
    public static class HistogramReducer extends Reducer<Text, NullWritable, Text, NullWritable> {
        private int totalProcessed = 0;
        
        @Override
        protected void reduce(Text key, Iterable<NullWritable> values, Context context) 
                throws IOException, InterruptedException {
            totalProcessed++;
            context.write(key, NullWritable.get());
        }
        
        @Override
        protected void cleanup(Context context) throws IOException, InterruptedException {
            super.cleanup(context);
            System.out.println("MapReduce任务完成，共处理 " + totalProcessed + " 张图像");
        }
    }
    
    @Override
    public int run(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: HistogramGenerationJob <input> <output>");
            return -1;
        }
        
        Configuration conf = getConf();
        Job job = Job.getInstance(conf, "Image Histogram Generation");
        
        job.setJarByClass(HistogramGenerationJob.class);
        job.setMapperClass(HistogramMapper.class);
        job.setReducerClass(HistogramReducer.class);
        
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(NullWritable.class);
        
        job.setInputFormatClass(SequenceFileInputFormat.class);
        job.setOutputFormatClass(NullOutputFormat.class);
        
        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));
        
        return job.waitForCompletion(true) ? 0 : 1;
    }
    
    public static void main(String[] args) throws Exception {
        int exitCode = ToolRunner.run(new HistogramGenerationJob(), args);
        System.exit(exitCode);
    }
}
