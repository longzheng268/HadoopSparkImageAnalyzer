package com.analyzer.core;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.Set;

/**
 * Redis缓存管理器
 * 使用真实的Redis服务器进行缓存管理
 * 实现LRU策略，保存最近20条搜索结果
 */
public class RedisManager {
    
    private static final String REDIS_HOST = "localhost";
    private static final int REDIS_PORT = 6379;
    private static final int MAX_CACHE_SIZE = 20;
    
    // 用于记录缓存访问顺序的有序集合
    private static final String CACHE_ORDER_KEY = "cache:order";
    // 缓存键前缀
    private static final String CACHE_PREFIX = "cache:";
    
    private static JedisPool jedisPool = null;
    
    /**
     * 初始化Redis连接池
     */
    public static synchronized void initialize() {
        if (jedisPool == null) {
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(10);
            poolConfig.setMaxIdle(5);
            poolConfig.setMinIdle(1);
            poolConfig.setTestOnBorrow(true);
            
            String host = System.getenv("REDIS_HOST");
            if (host == null) {
                host = REDIS_HOST;
            }
            
            String portStr = System.getenv("REDIS_PORT");
            int port = REDIS_PORT;
            if (portStr != null) {
                try {
                    port = Integer.parseInt(portStr);
                } catch (NumberFormatException e) {
                    // 使用默认端口
                }
            }
            
            try {
                jedisPool = new JedisPool(poolConfig, host, port);
                System.out.println("Redis连接池已创建: " + host + ":" + port);
                
                // 测试连接
                try (Jedis jedis = jedisPool.getResource()) {
                    jedis.ping();
                    System.out.println("Redis连接测试成功");
                }
            } catch (Exception e) {
                System.err.println("Redis连接失败: " + e.getMessage());
                System.err.println("将使用内存模拟缓存");
                jedisPool = null;
            }
        }
    }
    
    /**
     * 检查Redis是否可用
     */
    public static boolean isAvailable() {
        return jedisPool != null;
    }
    
    /**
     * 获取缓存值
     */
    public static String get(String key) {
        initialize();
        
        if (jedisPool == null) {
            // 降级到内存缓存
            return CacheManager.get(key);
        }
        
        try (Jedis jedis = jedisPool.getResource()) {
            String cacheKey = CACHE_PREFIX + key;
            String value = jedis.get(cacheKey);
            
            if (value != null) {
                // 更新访问时间（使用当前时间戳作为分数）
                long now = System.currentTimeMillis();
                jedis.zadd(CACHE_ORDER_KEY, now, cacheKey);
                System.out.println("缓存命中: " + key);
            } else {
                System.out.println("缓存未命中: " + key);
            }
            
            return value;
        } catch (Exception e) {
            System.err.println("Redis读取失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 设置缓存值
     */
    public static void put(String key, String value, String type) {
        initialize();
        
        if (jedisPool == null) {
            // 降级到内存缓存
            CacheManager.put(key, value, type);
            return;
        }
        
        try (Jedis jedis = jedisPool.getResource()) {
            String cacheKey = CACHE_PREFIX + key;
            long now = System.currentTimeMillis();
            
            // 存储值
            jedis.set(cacheKey, value);
            
            // 设置过期时间（1小时）
            jedis.expire(cacheKey, 3600);
            
            // 记录访问顺序
            jedis.zadd(CACHE_ORDER_KEY, now, cacheKey);
            
            // 检查缓存大小，移除最旧的记录
            long cacheSize = jedis.zcard(CACHE_ORDER_KEY);
            if (cacheSize > MAX_CACHE_SIZE) {
                // 获取最旧的记录
                Set<String> oldKeys = jedis.zrange(CACHE_ORDER_KEY, 0, cacheSize - MAX_CACHE_SIZE - 1);
                for (String oldKey : oldKeys) {
                    jedis.del(oldKey);
                    jedis.zrem(CACHE_ORDER_KEY, oldKey);
                }
                System.out.println("移除了 " + oldKeys.size() + " 个旧缓存");
            }
            
            System.out.println("缓存已添加: " + key + " (当前缓存数: " + Math.min(cacheSize, MAX_CACHE_SIZE) + ")");
            
        } catch (Exception e) {
            System.err.println("Redis写入失败: " + e.getMessage());
        }
    }
    
    /**
     * 清除所有缓存
     */
    public static void clearCache() {
        initialize();
        
        if (jedisPool == null) {
            CacheManager.clearCache();
            return;
        }
        
        try (Jedis jedis = jedisPool.getResource()) {
            // 获取所有缓存键
            Set<String> cacheKeys = jedis.zrange(CACHE_ORDER_KEY, 0, -1);
            
            // 删除所有缓存数据
            for (String key : cacheKeys) {
                jedis.del(key);
            }
            
            // 清空顺序记录
            jedis.del(CACHE_ORDER_KEY);
            
            System.out.println("已清除所有Redis缓存");
            
        } catch (Exception e) {
            System.err.println("清除Redis缓存失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取缓存统计信息
     */
    public static String getCacheStats() {
        initialize();
        
        if (jedisPool == null) {
            return "Redis不可用\n\n" + CacheManager.getCacheStats();
        }
        
        try (Jedis jedis = jedisPool.getResource()) {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Redis缓存统计 ===\n\n");
            
            long cacheSize = jedis.zcard(CACHE_ORDER_KEY);
            sb.append("当前缓存数量: ").append(cacheSize).append("/").append(MAX_CACHE_SIZE).append("\n\n");
            
            if (cacheSize > 0) {
                sb.append("缓存列表（按访问时间倒序）:\n\n");
                
                // 获取所有缓存键（按时间倒序）
                Set<String> cacheKeys = jedis.zrevrange(CACHE_ORDER_KEY, 0, -1);
                
                int index = 1;
                for (String cacheKey : cacheKeys) {
                    String key = cacheKey.substring(CACHE_PREFIX.length());
                    String value = jedis.get(cacheKey);
                    Long score = jedis.zscore(CACHE_ORDER_KEY, cacheKey).longValue();
                    
                    sb.append(index++).append(". ").append(key).append("\n");
                    sb.append("   访问时间: ").append(new java.util.Date(score)).append("\n");
                    
                    if (value != null && value.length() > 100) {
                        sb.append("   结果: ").append(value.substring(0, 100)).append("...\n");
                    } else {
                        sb.append("   结果: ").append(value).append("\n");
                    }
                    sb.append("\n");
                }
            }
            
            return sb.toString();
            
        } catch (Exception e) {
            return "获取Redis缓存统计失败: " + e.getMessage();
        }
    }
    
    /**
     * 关闭Redis连接池
     */
    public static synchronized void close() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
            jedisPool = null;
            System.out.println("Redis连接池已关闭");
        }
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
