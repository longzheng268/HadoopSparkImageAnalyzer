package com.analyzer.core;

/**
 * 计算引擎管理器
 * 管理Spark和Hadoop MapReduce之间的切换
 */
public class ComputeEngineManager {
    
    /**
     * 计算引擎类型枚举
     */
    public enum EngineType {
        HADOOP_MAPREDUCE("Hadoop MapReduce"),
        SPARK("Apache Spark");
        
        private final String displayName;
        
        EngineType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    // 默认使用Hadoop MapReduce（根据题目要求）
    private static EngineType currentEngine = EngineType.HADOOP_MAPREDUCE;
    
    /**
     * 获取当前计算引擎
     */
    public static EngineType getCurrentEngine() {
        return currentEngine;
    }
    
    /**
     * 设置计算引擎
     */
    public static void setEngine(EngineType engine) {
        if (engine != currentEngine) {
            EngineType oldEngine = currentEngine;
            currentEngine = engine;
            System.out.println("计算引擎已切换: " + oldEngine.getDisplayName() + " -> " + currentEngine.getDisplayName());
            
            // 如果从Spark切换到MapReduce，关闭Spark上下文
            if (oldEngine == EngineType.SPARK && engine == EngineType.HADOOP_MAPREDUCE) {
                try {
                    SparkContextManager.closeContext();
                    System.out.println("Spark上下文已关闭");
                } catch (Exception e) {
                    System.err.println("关闭Spark上下文失败: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * 检查当前是否使用Spark引擎
     */
    public static boolean isUsingSpark() {
        return currentEngine == EngineType.SPARK;
    }
    
    /**
     * 检查当前是否使用MapReduce引擎
     */
    public static boolean isUsingMapReduce() {
        return currentEngine == EngineType.HADOOP_MAPREDUCE;
    }
    
    /**
     * 获取引擎状态描述
     */
    public static String getEngineStatus() {
        StringBuilder status = new StringBuilder();
        status.append("当前计算引擎: ").append(currentEngine.getDisplayName()).append("\n");
        
        if (currentEngine == EngineType.SPARK) {
            try {
                String sparkUIUrl = SparkContextManager.getSparkUIUrl();
                status.append("Spark状态: ").append(SparkContextManager.isUsingYarn() ? "YARN模式" : "Local模式").append("\n");
                status.append("Spark UI: ").append(sparkUIUrl).append("\n");
            } catch (Exception e) {
                status.append("Spark状态: 未初始化\n");
            }
        } else {
            status.append("MapReduce状态: 可用\n");
            String hadoopHome = System.getenv("HADOOP_HOME");
            if (hadoopHome != null) {
                status.append("HADOOP_HOME: ").append(hadoopHome).append("\n");
            } else {
                status.append("注意: 未检测到HADOOP_HOME环境变量\n");
            }
        }
        
        return status.toString();
    }
}
