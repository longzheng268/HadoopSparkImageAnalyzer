package com.analyzer.core;

import java.util.*;

/**
 * 缓存管理器
 * 管理搜索结果的缓存（模拟Redis功能）
 * 保存最近20条搜索结果
 */
public class CacheManager {
    
    // 使用LinkedHashMap实现LRU缓存
    private static final int MAX_CACHE_SIZE = 20;
    private static LinkedHashMap<String, CacheEntry> cache = new LinkedHashMap<String, CacheEntry>(MAX_CACHE_SIZE + 1, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    };
    
    /**
     * 缓存条目
     */
    public static class CacheEntry {
        private String key;
        private String result;
        private String taskType;
        private long timestamp;
        private int accessCount;
        
        public CacheEntry(String key, String result, String taskType) {
            this.key = key;
            this.result = result;
            this.taskType = taskType;
            this.timestamp = System.currentTimeMillis();
            this.accessCount = 0;
        }
        
        public void incrementAccess() {
            this.accessCount++;
            this.timestamp = System.currentTimeMillis(); // 更新访问时间
        }
        
        public String getKey() {
            return key;
        }
        
        public String getResult() {
            return result;
        }
        
        public String getTaskType() {
            return taskType;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
        
        public int getAccessCount() {
            return accessCount;
        }
        
        @Override
        public String toString() {
            Date date = new Date(timestamp);
            return String.format("[%s] %s - 访问次数:%d\n关键字:%s\n结果预览:%s", 
                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date),
                taskType,
                accessCount,
                key,
                result.length() > 100 ? result.substring(0, 100) + "..." : result
            );
        }
    }
    
    /**
     * 添加缓存
     */
    public static synchronized void put(String key, String result, String taskType) {
        CacheEntry entry = new CacheEntry(key, result, taskType);
        cache.put(key, entry);
        System.out.println("缓存已添加: " + key + " (当前缓存数: " + cache.size() + ")");
    }
    
    /**
     * 获取缓存
     */
    public static synchronized String get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry != null) {
            entry.incrementAccess();
            System.out.println("缓存命中: " + key);
            return entry.getResult();
        }
        System.out.println("缓存未命中: " + key);
        return null;
    }
    
    /**
     * 检查缓存是否存在
     */
    public static synchronized boolean containsKey(String key) {
        return cache.containsKey(key);
    }
    
    /**
     * 获取所有缓存条目
     */
    public static synchronized List<CacheEntry> getAllEntries() {
        return new ArrayList<>(cache.values());
    }
    
    /**
     * 获取缓存统计信息
     */
    public static synchronized String getCacheStats() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 缓存统计信息 ===\n");
        sb.append("当前缓存数: ").append(cache.size()).append("/").append(MAX_CACHE_SIZE).append("\n\n");
        
        if (cache.isEmpty()) {
            sb.append("缓存为空\n");
        } else {
            sb.append("缓存列表（按访问时间排序）:\n\n");
            List<CacheEntry> entries = getAllEntries();
            int index = 1;
            for (CacheEntry entry : entries) {
                sb.append(index++).append(". ").append(entry.toString()).append("\n\n");
            }
        }
        
        return sb.toString();
    }
    
    /**
     * 清除所有缓存
     */
    public static synchronized void clearCache() {
        cache.clear();
        System.out.println("缓存已清除");
    }
    
    /**
     * 生成搜索键（用于全图搜索）
     */
    public static String generateSearchKey(String queryImageName) {
        return "SEARCH_" + queryImageName;
    }
    
    /**
     * 生成局部特征搜索键
     */
    public static String generateLocalFeatureKey(String featureImageName, double threshold) {
        return "LOCAL_" + featureImageName + "_" + threshold;
    }
    
    /**
     * 生成篡改检测键
     */
    public static String generateTamperKey(String suspectImageName, int threshold) {
        return "TAMPER_" + suspectImageName + "_" + threshold;
    }
}
