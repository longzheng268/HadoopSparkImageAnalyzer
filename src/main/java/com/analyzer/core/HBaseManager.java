package com.analyzer.core;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * HBase管理器
 * 负责图像数据和直方图的存储与检索
 * 
 * 表结构:
 * - 表名: image_histograms
 * - RowKey: 图像文件名
 * - 列族 info: 存储图像元数据
 *   - info:filename: 文件名
 *   - info:width: 图像宽度
 *   - info:height: 图像高度
 * - 列族 histogram: 存储直方图数据
 *   - histogram:data: 256个整数，逗号分隔
 * - 列族 image: 存储图像数据
 *   - image:data: 图像二进制数据
 */
public class HBaseManager {
    
    private static final String TABLE_NAME = "image_histograms";
    private static final String CF_INFO = "info";
    private static final String CF_HISTOGRAM = "histogram";
    private static final String CF_IMAGE = "image";
    
    private static Configuration config = null;
    private static Connection connection = null;
    
    /**
     * 初始化HBase连接
     */
    public static synchronized void initialize() throws IOException {
        if (config == null) {
            config = HBaseConfiguration.create();
            
            // 从环境变量或默认配置读取HBase配置
            String zookeeperQuorum = System.getenv("HBASE_ZOOKEEPER_QUORUM");
            String zookeeperPort = System.getenv("HBASE_ZOOKEEPER_PORT");
            
            if (zookeeperQuorum == null) {
                zookeeperQuorum = "localhost";
            }
            if (zookeeperPort == null) {
                zookeeperPort = "2181";
            }
            
            config.set("hbase.zookeeper.quorum", zookeeperQuorum);
            config.set("hbase.zookeeper.property.clientPort", zookeeperPort);
            
            System.out.println("HBase配置: zookeeper=" + zookeeperQuorum + ":" + zookeeperPort);
        }
        
        if (connection == null || connection.isClosed()) {
            connection = ConnectionFactory.createConnection(config);
            System.out.println("HBase连接已建立");
            
            // 确保表存在
            ensureTableExists();
        }
    }
    
    /**
     * 确保表存在，不存在则创建
     */
    private static void ensureTableExists() throws IOException {
        try (Admin admin = connection.getAdmin()) {
            TableName tableName = TableName.valueOf(TABLE_NAME);
            
            if (!admin.tableExists(tableName)) {
                System.out.println("创建HBase表: " + TABLE_NAME);
                TableDescriptorBuilder builder = TableDescriptorBuilder.newBuilder(tableName);
                
                // 添加列族
                builder.setColumnFamily(ColumnFamilyDescriptorBuilder.of(CF_INFO));
                builder.setColumnFamily(ColumnFamilyDescriptorBuilder.of(CF_HISTOGRAM));
                builder.setColumnFamily(ColumnFamilyDescriptorBuilder.of(CF_IMAGE));
                
                admin.createTable(builder.build());
                System.out.println("表创建成功");
            } else {
                System.out.println("HBase表已存在: " + TABLE_NAME);
            }
        }
    }
    
    /**
     * 存储图像及其直方图到HBase
     */
    public static void storeImage(File imageFile, ImageHistogram histogram) throws IOException {
        initialize();
        
        Table table = connection.getTable(TableName.valueOf(TABLE_NAME));
        
        try {
            String filename = imageFile.getName();
            Put put = new Put(Bytes.toBytes(filename));
            
            // 存储元数据
            put.addColumn(Bytes.toBytes(CF_INFO), Bytes.toBytes("filename"), Bytes.toBytes(filename));
            put.addColumn(Bytes.toBytes(CF_INFO), Bytes.toBytes("width"), Bytes.toBytes(histogram.getWidth()));
            put.addColumn(Bytes.toBytes(CF_INFO), Bytes.toBytes("height"), Bytes.toBytes(histogram.getHeight()));
            
            // 存储直方图数据（逗号分隔）
            StringBuilder histData = new StringBuilder();
            int[] histArray = histogram.getHistogram();
            for (int i = 0; i < histArray.length; i++) {
                if (i > 0) histData.append(",");
                histData.append(histArray[i]);
            }
            put.addColumn(Bytes.toBytes(CF_HISTOGRAM), Bytes.toBytes("data"), Bytes.toBytes(histData.toString()));
            
            // 存储图像二进制数据
            BufferedImage image = ImageIO.read(imageFile);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            put.addColumn(Bytes.toBytes(CF_IMAGE), Bytes.toBytes("data"), baos.toByteArray());
            
            table.put(put);
            
        } finally {
            table.close();
        }
    }
    
    /**
     * 从HBase读取所有图像的直方图数据
     */
    public static List<ImageHistogram> getAllHistograms() throws IOException {
        initialize();
        
        List<ImageHistogram> histograms = new ArrayList<>();
        Table table = connection.getTable(TableName.valueOf(TABLE_NAME));
        
        try {
            Scan scan = new Scan();
            scan.addFamily(Bytes.toBytes(CF_INFO));
            scan.addFamily(Bytes.toBytes(CF_HISTOGRAM));
            
            ResultScanner scanner = table.getScanner(scan);
            
            for (Result result : scanner) {
                try {
                    String filename = Bytes.toString(result.getValue(Bytes.toBytes(CF_INFO), Bytes.toBytes("filename")));
                    int width = Bytes.toInt(result.getValue(Bytes.toBytes(CF_INFO), Bytes.toBytes("width")));
                    int height = Bytes.toInt(result.getValue(Bytes.toBytes(CF_INFO), Bytes.toBytes("height")));
                    String histData = Bytes.toString(result.getValue(Bytes.toBytes(CF_HISTOGRAM), Bytes.toBytes("data")));
                    
                    // 解析直方图数据
                    String[] parts = histData.split(",");
                    int[] histogram = new int[256];
                    for (int i = 0; i < parts.length && i < 256; i++) {
                        histogram[i] = Integer.parseInt(parts[i]);
                    }
                    
                    histograms.add(new ImageHistogram(histogram, width, height, filename));
                } catch (Exception e) {
                    System.err.println("解析直方图失败: " + e.getMessage());
                }
            }
            
            scanner.close();
        } finally {
            table.close();
        }
        
        return histograms;
    }
    
    /**
     * 从HBase读取图像数据
     */
    public static BufferedImage getImage(String filename) throws IOException {
        initialize();
        
        Table table = connection.getTable(TableName.valueOf(TABLE_NAME));
        
        try {
            Get get = new Get(Bytes.toBytes(filename));
            get.addColumn(Bytes.toBytes(CF_IMAGE), Bytes.toBytes("data"));
            
            Result result = table.get(get);
            if (result.isEmpty()) {
                return null;
            }
            
            byte[] imageData = result.getValue(Bytes.toBytes(CF_IMAGE), Bytes.toBytes("data"));
            ByteArrayInputStream bais = new ByteArrayInputStream(imageData);
            return ImageIO.read(bais);
            
        } finally {
            table.close();
        }
    }
    
    /**
     * 检查HBase中是否有数据
     */
    public static boolean hasData() throws IOException {
        initialize();
        
        Table table = connection.getTable(TableName.valueOf(TABLE_NAME));
        
        try {
            Scan scan = new Scan();
            scan.setLimit(1);
            ResultScanner scanner = table.getScanner(scan);
            boolean hasData = scanner.iterator().hasNext();
            scanner.close();
            return hasData;
        } finally {
            table.close();
        }
    }
    
    /**
     * 获取HBase中的图像数量
     */
    public static int getImageCount() throws IOException {
        initialize();
        
        Table table = connection.getTable(TableName.valueOf(TABLE_NAME));
        
        try {
            Scan scan = new Scan();
            scan.addFamily(Bytes.toBytes(CF_INFO));
            ResultScanner scanner = table.getScanner(scan);
            
            int count = 0;
            for (Result result : scanner) {
                count++;
            }
            
            scanner.close();
            return count;
        } finally {
            table.close();
        }
    }
    
    /**
     * 删除HBase中的图像
     */
    public static void deleteImage(String filename) throws IOException {
        initialize();
        
        Table table = connection.getTable(TableName.valueOf(TABLE_NAME));
        
        try {
            Delete delete = new Delete(Bytes.toBytes(filename));
            table.delete(delete);
            System.out.println("已从HBase删除: " + filename);
        } finally {
            table.close();
        }
    }
    
    /**
     * 关闭HBase连接
     */
    public static synchronized void close() throws IOException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
            connection = null;
            System.out.println("HBase连接已关闭");
        }
    }
}
