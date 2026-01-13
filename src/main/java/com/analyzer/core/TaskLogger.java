package com.analyzer.core;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 任务日志系统
 * 记录用户任务的启动、结束和状态
 */
public class TaskLogger {
    
    private static final String LOG_DIR = "logs";
    private static final String LOG_FILE = "task_log.txt";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    /**
     * 任务状态
     */
    public enum TaskStatus {
        STARTED("开始"),
        COMPLETED("完成"),
        FAILED("失败");
        
        private String description;
        
        TaskStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 任务记录
     */
    public static class TaskRecord {
        private String taskId;
        private String taskType;
        private String timestamp;
        private TaskStatus status;
        private String details;
        
        public TaskRecord(String taskId, String taskType, String timestamp, TaskStatus status, String details) {
            this.taskId = taskId;
            this.taskType = taskType;
            this.timestamp = timestamp;
            this.status = status;
            this.details = details;
        }
        
        public String getTaskId() {
            return taskId;
        }
        
        public String getTaskType() {
            return taskType;
        }
        
        public String getTimestamp() {
            return timestamp;
        }
        
        public TaskStatus getStatus() {
            return status;
        }
        
        public String getDetails() {
            return details;
        }
        
        @Override
        public String toString() {
            return String.format("[%s] %s - %s - %s - %s", 
                timestamp, taskId, taskType, status.getDescription(), details);
        }
        
        public String toLogLine() {
            return String.format("%s|%s|%s|%s|%s", 
                timestamp, taskId, taskType, status.name(), details);
        }
        
        public static TaskRecord fromLogLine(String line) {
            String[] parts = line.split("\\|", 5);
            if (parts.length < 5) {
                return null;
            }
            return new TaskRecord(
                parts[1],
                parts[2],
                parts[0],
                TaskStatus.valueOf(parts[3]),
                parts[4]
            );
        }
    }
    
    /**
     * 记录任务日志
     */
    public static synchronized void logTask(String taskType, TaskStatus status, String details) {
        try {
            File logDir = new File(LOG_DIR);
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            
            File logFile = new File(logDir, LOG_FILE);
            
            String taskId = generateTaskId();
            String timestamp = DATE_FORMAT.format(new Date());
            TaskRecord record = new TaskRecord(taskId, taskType, timestamp, status, details);
            
            try (FileWriter fw = new FileWriter(logFile, true);
                 PrintWriter pw = new PrintWriter(fw)) {
                pw.println(record.toLogLine());
            }
            
            System.out.println("日志已记录: " + record.toString());
            
        } catch (IOException e) {
            System.err.println("写入日志失败: " + e.getMessage());
        }
    }
    
    /**
     * 读取所有日志记录
     */
    public static List<TaskRecord> readLogs() {
        List<TaskRecord> records = new ArrayList<>();
        
        File logFile = new File(LOG_DIR, LOG_FILE);
        if (!logFile.exists()) {
            return records;
        }
        
        try (BufferedReader br = new BufferedReader(new FileReader(logFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                TaskRecord record = TaskRecord.fromLogLine(line);
                if (record != null) {
                    records.add(record);
                }
            }
        } catch (IOException e) {
            System.err.println("读取日志失败: " + e.getMessage());
        }
        
        return records;
    }
    
    /**
     * 获取未完成的任务
     */
    public static List<TaskRecord> getIncompleteTasks() {
        List<TaskRecord> allRecords = readLogs();
        Map<String, TaskRecord> taskMap = new HashMap<>();
        
        // 记录每个任务的最新状态
        for (TaskRecord record : allRecords) {
            taskMap.put(record.getTaskId(), record);
        }
        
        // 找出状态为STARTED的任务（未完成）
        List<TaskRecord> incompleteTasks = new ArrayList<>();
        for (TaskRecord record : taskMap.values()) {
            if (record.getStatus() == TaskStatus.STARTED) {
                incompleteTasks.add(record);
            }
        }
        
        return incompleteTasks;
    }
    
    /**
     * 清除日志文件
     */
    public static synchronized void clearLogs() {
        File logFile = new File(LOG_DIR, LOG_FILE);
        if (logFile.exists()) {
            logFile.delete();
        }
    }
    
    /**
     * 生成任务ID
     */
    private static String generateTaskId() {
        // 使用时间戳和计数器确保唯一性
        return "TASK_" + System.currentTimeMillis() + "_" + Thread.currentThread().getId();
    }
    
    /**
     * 格式化日志摘要
     */
    public static String getLogSummary() {
        List<TaskRecord> records = readLogs();
        
        if (records.isEmpty()) {
            return "暂无任务日志";
        }
        
        int startedCount = 0;
        int completedCount = 0;
        int failedCount = 0;
        
        for (TaskRecord record : records) {
            switch (record.getStatus()) {
                case STARTED:
                    startedCount++;
                    break;
                case COMPLETED:
                    completedCount++;
                    break;
                case FAILED:
                    failedCount++;
                    break;
            }
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("任务日志统计:\n");
        sb.append("总任务数: ").append(records.size()).append("\n");
        sb.append("已开始: ").append(startedCount).append("\n");
        sb.append("已完成: ").append(completedCount).append("\n");
        sb.append("失败: ").append(failedCount).append("\n");
        
        List<TaskRecord> incompleteTasks = getIncompleteTasks();
        if (!incompleteTasks.isEmpty()) {
            sb.append("\n未完成任务: ").append(incompleteTasks.size()).append("\n");
            for (TaskRecord task : incompleteTasks) {
                sb.append("  - ").append(task.toString()).append("\n");
            }
        }
        
        return sb.toString();
    }
}
