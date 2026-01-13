package com.analyzer.mapreduce;

import com.analyzer.core.ImageHistogram;
import com.analyzer.core.ImageMatcher;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.KeyValueTextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Hadoop MapReduce任务：全图搜索
 * 基于直方图的图像相似度匹配
 */
public class ImageSearchJob extends Configured implements Tool {
    
    /**
     * Mapper: 计算每张图像与查询图像的相似度
     */
    public static class SearchMapper extends Mapper<Text, Text, Text, DoubleWritable> {
        private int[] queryHistogram;
        
        @Override
        protected void setup(Context context) throws IOException, InterruptedException {
            super.setup(context);
            
            // 从配置中读取查询图像的直方图
            Configuration conf = context.getConfiguration();
            String histStr = conf.get("query.histogram");
            
            if (histStr != null && !histStr.isEmpty()) {
                String[] parts = histStr.split(",");
                queryHistogram = new int[256];
                for (int i = 0; i < 256 && i < parts.length; i++) {
                    queryHistogram[i] = Integer.parseInt(parts[i].trim());
                }
            } else {
                throw new IOException("查询图像直方图未设置");
            }
        }
        
        @Override
        protected void map(Text key, Text value, Context context) 
                throws IOException, InterruptedException {
            try {
                String filename = key.toString();
                String histStr = value.toString();
                
                // 解析直方图数据
                String[] parts = histStr.split(",");
                int[] histogram = new int[256];
                for (int i = 0; i < 256 && i < parts.length; i++) {
                    histogram[i] = Integer.parseInt(parts[i].trim());
                }
                
                // 计算相似度
                double similarity = calculateSimilarity(queryHistogram, histogram);
                
                // 输出文件名和相似度
                context.write(new Text(filename), new DoubleWritable(similarity));
                
            } catch (Exception e) {
                System.err.println("MapReduce Map: 处理失败: " + key.toString());
                context.getCounter("Search", "Failed").increment(1);
            }
        }
        
        private double calculateSimilarity(int[] hist1, int[] hist2) {
            double intersection = 0;
            double total = 0;
            
            for (int i = 0; i < 256; i++) {
                intersection += Math.min(hist1[i], hist2[i]);
                total += Math.max(hist1[i], hist2[i]);
            }
            
            return total > 0 ? intersection / total : 0;
        }
    }
    
    /**
     * Reducer: 收集所有结果并按相似度排序
     */
    public static class SearchReducer extends Reducer<Text, DoubleWritable, Text, DoubleWritable> {
        private List<SearchResult> results = new ArrayList<>();
        
        private static class SearchResult implements Comparable<SearchResult> {
            String filename;
            double similarity;
            
            SearchResult(String filename, double similarity) {
                this.filename = filename;
                this.similarity = similarity;
            }
            
            @Override
            public int compareTo(SearchResult other) {
                return Double.compare(other.similarity, this.similarity);
            }
        }
        
        @Override
        protected void reduce(Text key, Iterable<DoubleWritable> values, Context context) 
                throws IOException, InterruptedException {
            // 收集所有结果
            for (DoubleWritable val : values) {
                results.add(new SearchResult(key.toString(), val.get()));
            }
        }
        
        @Override
        protected void cleanup(Context context) throws IOException, InterruptedException {
            super.cleanup(context);
            
            // 按相似度降序排序
            Collections.sort(results);
            
            // 获取topN配置
            int topN = context.getConfiguration().getInt("search.topn", 5);
            int count = Math.min(topN, results.size());
            
            // 输出前N个结果
            for (int i = 0; i < count; i++) {
                SearchResult result = results.get(i);
                context.write(new Text(result.filename), new DoubleWritable(result.similarity));
            }
            
            System.out.println("MapReduce任务完成，处理了 " + results.size() + " 张图像");
        }
    }
    
    @Override
    public int run(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: ImageSearchJob <input> <output>");
            return -1;
        }
        
        Configuration conf = getConf();
        Job job = Job.getInstance(conf, "Image Search");
        
        job.setJarByClass(ImageSearchJob.class);
        job.setMapperClass(SearchMapper.class);
        job.setReducerClass(SearchReducer.class);
        
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(DoubleWritable.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(DoubleWritable.class);
        
        job.setInputFormatClass(KeyValueTextInputFormat.class);
        job.setOutputFormatClass(TextOutputFormat.class);
        
        // 设置只使用一个Reducer以便进行全局排序
        job.setNumReduceTasks(1);
        
        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));
        
        return job.waitForCompletion(true) ? 0 : 1;
    }
    
    public static void main(String[] args) throws Exception {
        int exitCode = ToolRunner.run(new ImageSearchJob(), args);
        System.exit(exitCode);
    }
}
