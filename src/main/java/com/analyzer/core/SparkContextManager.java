package com.analyzer.core;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;

/**
 * Spark上下文管理器
 * 管理Spark会话的创建和配置
 */
public class SparkContextManager {
    
    private static JavaSparkContext sparkContext = null;
    private static boolean useYarn = false;
    
    /**
     * 获取或创建Spark上下文
     * 
     * @return JavaSparkContext实例
     */
    public static synchronized JavaSparkContext getOrCreateContext() {
        if (sparkContext == null) {
            SparkConf conf = new SparkConf()
                .setAppName("HadoopSparkImageAnalyzer")
                .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
                .set("spark.ui.showConsoleProgress", "false");
            
            // 尝试连接到YARN，如果失败则使用local模式
            boolean yarnConfigured = false;
            try {
                // 检查是否有YARN环境变量
                String hadoopHome = System.getenv("HADOOP_HOME");
                String yarnConf = System.getenv("YARN_CONF_DIR");
                
                if (hadoopHome != null || yarnConf != null) {
                    conf.setMaster("yarn")
                        .set("spark.submit.deployMode", "client")
                        .set("spark.yarn.am.memory", "512m")
                        .set("spark.executor.memory", "512m")
                        .set("spark.executor.instances", "2");
                    yarnConfigured = true;
                    System.out.println("检测到YARN环境变量，尝试使用YARN模式连接到Hadoop集群");
                } else {
                    // 使用local模式进行本地分布式处理
                    conf.setMaster("local[*]");
                    System.out.println("使用Local模式（本地分布式）");
                }
            } catch (Exception e) {
                conf.setMaster("local[*]");
                System.out.println("配置Spark时出错，使用Local模式（本地分布式）: " + e.getMessage());
            }
            
            // 尝试创建Spark上下文，如果YARN模式失败则降级到local模式
            try {
                sparkContext = new JavaSparkContext(conf);
                if (yarnConfigured) {
                    useYarn = true;
                    System.out.println("成功连接到YARN集群");
                }
            } catch (Exception e) {
                if (yarnConfigured) {
                    System.err.println("YARN模式初始化失败，降级到Local模式: " + e.getMessage());
                    // 重新配置为local模式
                    conf.setMaster("local[*]");
                    // 移除YARN相关配置
                    conf.remove("spark.submit.deployMode");
                    conf.remove("spark.yarn.am.memory");
                    conf.remove("spark.executor.memory");
                    conf.remove("spark.executor.instances");
                    useYarn = false;
                    sparkContext = new JavaSparkContext(conf);
                    System.out.println("成功切换到Local模式（本地分布式）");
                } else {
                    // 如果已经是local模式还失败，则抛出异常
                    throw e;
                }
            }
            
            // 设置日志级别
            sparkContext.setLogLevel("WARN");
        }
        
        return sparkContext;
    }
    
    /**
     * 检查是否使用YARN模式
     */
    public static boolean isUsingYarn() {
        return useYarn;
    }
    
    /**
     * 关闭Spark上下文
     */
    public static synchronized void closeContext() {
        if (sparkContext != null) {
            sparkContext.close();
            sparkContext = null;
        }
    }
    
    /**
     * 获取Spark Web UI地址
     */
    public static String getSparkUIUrl() {
        if (sparkContext != null) {
            return sparkContext.sc().uiWebUrl().getOrElse(() -> "未启动");
        }
        return "未初始化";
    }
}
