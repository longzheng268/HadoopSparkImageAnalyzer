package com.analyzer;

import com.analyzer.core.*;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.util.List;

/**
 * HadoopSparkImageAnalyzer - 主程序入口
 * 用于处理海量黑白图像的分布式分析系统
 */
public class Main {
    
    // 图像资源目录
    private static final String IMAGE_RESOURCE_DIR = "src/main/resources/images";
    public static void main(String[] args) {
        // 使用Swing EDT线程启动GUI
        SwingUtilities.invokeLater(() -> {
            createAndShowGUI();
        });
    }

    /**
     * 创建并显示主窗口
     */
    private static void createAndShowGUI() {
        // 创建主窗口
        JFrame frame = new JFrame("HadoopSparkImageAnalyzer - 图像分析系统");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setLocationRelativeTo(null); // 窗口居中显示

        // 创建主面板
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // 标题标签
        JLabel titleLabel = new JLabel("HadoopSparkImageAnalyzer - 图像分析系统", SwingConstants.CENTER);
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        // 显示现有图像数量
        File imageDir = new File(IMAGE_RESOURCE_DIR);
        List<File> existingImages = ImageResourceDownloader.getExistingImages(imageDir);
        JLabel imageCountLabel = new JLabel("图像资源：" + existingImages.size() + " 张", SwingConstants.CENTER);
        imageCountLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        
        // 信息面板
        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.add(imageCountLabel, BorderLayout.CENTER);
        mainPanel.add(infoPanel, BorderLayout.SOUTH);

        // 中间功能面板 - 使用选项卡
        JTabbedPane tabbedPane = new JTabbedPane();
        
        // 1. 数据清洗面板
        JPanel dataCleaningPanel = createDataCleaningPanel(frame, imageCountLabel);
        tabbedPane.addTab("数据清洗", dataCleaningPanel);
        
        // 2. 图像搜索面板
        JPanel searchPanel = createSearchPanel(frame);
        tabbedPane.addTab("图像搜索", searchPanel);
        
        // 3. 局部特征搜索面板
        JPanel localSearchPanel = createLocalSearchPanel(frame);
        tabbedPane.addTab("局部特征搜索", localSearchPanel);
        
        // 4. 篡改检测面板
        JPanel tamperPanel = createTamperDetectionPanel(frame);
        tabbedPane.addTab("篡改检测", tamperPanel);
        
        // 5. 缓存管理面板
        JPanel cachePanel = createCachePanel(frame);
        tabbedPane.addTab("缓存管理", cachePanel);
        
        // 6. 日志系统面板
        JPanel logPanel = createLogPanel(frame);
        tabbedPane.addTab("日志系统", logPanel);

        mainPanel.add(tabbedPane, BorderLayout.CENTER);

        // 设置面板并显示窗口
        frame.setContentPane(mainPanel);
        frame.setVisible(true);

        // 在控制台打印启动信息
        System.out.println("========================================");
        System.out.println("HadoopSparkImageAnalyzer 已启动");
        System.out.println("Java版本: " + System.getProperty("java.version"));
        System.out.println("工作目录: " + System.getProperty("user.dir"));
        System.out.println("图像资源目录: " + IMAGE_RESOURCE_DIR);
        System.out.println("========================================");
        
        // 记录启动日志
        TaskLogger.logTask("系统启动", TaskLogger.TaskStatus.COMPLETED, "系统成功启动，图像资源: " + existingImages.size() + " 张");
    }
    
    /**
     * 创建数据清洗面板
     */
    private static JPanel createDataCleaningPanel(JFrame parentFrame, JLabel imageCountLabel) {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // 说明文本
        JTextArea infoArea = new JTextArea();
        infoArea.setText("数据清洗模块\n\n" +
            "功能说明：\n" +
            "1. 下载样本图像：从Lorem Picsum下载512×512灰度图像\n" +
            "2. 生成图像直方图：统计每张图像的灰度值分布（0-255）\n" +
            "3. 查看已有图像：显示当前图像库中的所有图像\n\n" +
            "注：生成的直方图数据可存储到HBase（需配置Hadoop/Spark环境）");
        infoArea.setEditable(false);
        infoArea.setLineWrap(true);
        infoArea.setWrapStyleWord(true);
        infoArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(infoArea);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        
        JButton downloadButton = new JButton("下载样本图像");
        downloadButton.addActionListener(e -> downloadSampleImages(parentFrame, imageCountLabel));
        buttonPanel.add(downloadButton);
        
        JButton generateButton = new JButton("生成图像直方图");
        generateButton.addActionListener(e -> generateHistograms(parentFrame));
        buttonPanel.add(generateButton);
        
        JButton viewButton = new JButton("查看已有图像");
        viewButton.addActionListener(e -> viewExistingImages(parentFrame));
        buttonPanel.add(viewButton);
        
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    /**
     * 创建图像搜索面板
     */
    private static JPanel createSearchPanel(JFrame parentFrame) {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JTextArea infoArea = new JTextArea();
        infoArea.setText("全图搜索模块\n\n" +
            "功能说明：\n" +
            "提供一张图片，计算其直方图，与图像库中所有图片的直方图比对，\n" +
            "找到最匹配的图片并返回文件名。\n\n" +
            "使用方法：\n" +
            "1. 点击\"选择查询图像\"按钮\n" +
            "2. 选择要搜索的图像文件\n" +
            "3. 点击\"开始搜索\"进行匹配\n" +
            "4. 系统会显示相似度最高的前5张图像\n\n" +
            "注：搜索结果会自动缓存到Redis（模拟）");
        infoArea.setEditable(false);
        infoArea.setLineWrap(true);
        infoArea.setWrapStyleWord(true);
        infoArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(infoArea);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        
        JButton searchButton = new JButton("开始全图搜索");
        searchButton.addActionListener(e -> performImageSearch(parentFrame));
        buttonPanel.add(searchButton);
        
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    /**
     * 创建局部特征搜索面板
     */
    private static JPanel createLocalSearchPanel(JFrame parentFrame) {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JTextArea infoArea = new JTextArea();
        infoArea.setText("局部特征搜索模块\n\n" +
            "功能说明：\n" +
            "提供一小块正方形的局部特征图片，在所有图像中比对\n" +
            "（直接在图像二维矩阵中比对），找出所有含有该图片的图像。\n\n" +
            "使用方法：\n" +
            "1. 点击\"选择特征图像\"按钮\n" +
            "2. 选择局部特征图像（建议尺寸小于512×512）\n" +
            "3. 设置相似度阈值（建议95%以上）\n" +
            "4. 点击\"开始局部搜索\"进行匹配\n\n" +
            "注：使用Spark RDD实现图像分割并行比对");
        infoArea.setEditable(false);
        infoArea.setLineWrap(true);
        infoArea.setWrapStyleWord(true);
        infoArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(infoArea);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        
        JButton searchButton = new JButton("开始局部特征搜索");
        searchButton.addActionListener(e -> performLocalFeatureSearch(parentFrame));
        buttonPanel.add(searchButton);
        
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    /**
     * 创建篡改检测面板
     */
    private static JPanel createTamperDetectionPanel(JFrame parentFrame) {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JTextArea infoArea = new JTextArea();
        infoArea.setText("图像篡改检测模块\n\n" +
            "功能说明：\n" +
            "提供一张局部发生修改的图片，在所有图像中比对，\n" +
            "找出匹配度最高（即相同像素最多）的图片并返回，\n" +
            "同时输出篡改部分的像素点坐标。\n\n" +
            "使用方法：\n" +
            "1. 点击\"选择疑似篡改图像\"按钮\n" +
            "2. 选择要检测的图像文件\n" +
            "3. 设置差异阈值（灰度差，建议10-30）\n" +
            "4. 点击\"开始篡改检测\"进行分析\n\n" +
            "注：使用MapReduce实现图像分割并行比对");
        infoArea.setEditable(false);
        infoArea.setLineWrap(true);
        infoArea.setWrapStyleWord(true);
        infoArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(infoArea);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        
        JButton detectButton = new JButton("开始篡改检测");
        detectButton.addActionListener(e -> performTamperDetection(parentFrame));
        buttonPanel.add(detectButton);
        
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    /**
     * 创建缓存管理面板
     */
    private static JPanel createCachePanel(JFrame parentFrame) {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JTextArea infoArea = new JTextArea();
        infoArea.setText("搜索结果缓存模块\n\n" +
            "功能说明：\n" +
            "将搜索条件与搜索结果作为键值对缓存在Redis中（当前为内存模拟）。\n" +
            "缓存最近20条搜索结果（包括全图搜索、局部特征搜索、篡改检测）。\n\n" +
            "缓存策略：\n" +
            "- 每次搜索前，先查找缓存\n" +
            "- 若命中，直接返回并更新为最近访问\n" +
            "- 若未命中，执行搜索并缓存结果\n" +
            "- 超过20条时，自动移除最旧记录\n\n" +
            "注：可配置Redis实现分布式缓存");
        infoArea.setEditable(false);
        infoArea.setLineWrap(true);
        infoArea.setWrapStyleWord(true);
        infoArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(infoArea);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        
        JButton viewButton = new JButton("查看缓存统计");
        viewButton.addActionListener(e -> viewCacheStats(parentFrame));
        buttonPanel.add(viewButton);
        
        JButton clearButton = new JButton("清除缓存");
        clearButton.addActionListener(e -> {
            CacheManager.clearCache();
            JOptionPane.showMessageDialog(parentFrame, "缓存已清除", "提示", JOptionPane.INFORMATION_MESSAGE);
        });
        buttonPanel.add(clearButton);
        
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    /**
     * 创建日志系统面板
     */
    private static JPanel createLogPanel(JFrame parentFrame) {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JTextArea infoArea = new JTextArea();
        infoArea.setText("任务日志系统\n\n" +
            "功能说明：\n" +
            "记录用户输入的任务起始与结束信息，包括：\n" +
            "- 任务ID和类型\n" +
            "- 任务开始时间\n" +
            "- 任务状态（开始/完成/失败）\n" +
            "- 任务详细信息\n\n" +
            "用途：\n" +
            "- 查看任务执行历史\n" +
            "- 识别未完成的任务\n" +
            "- 支持任务重做功能\n\n" +
            "日志存储位置：logs/task_log.txt");
        infoArea.setEditable(false);
        infoArea.setLineWrap(true);
        infoArea.setWrapStyleWord(true);
        infoArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(infoArea);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        
        JButton viewButton = new JButton("查看日志");
        viewButton.addActionListener(e -> viewLogs(parentFrame));
        buttonPanel.add(viewButton);
        
        JButton summaryButton = new JButton("日志统计");
        summaryButton.addActionListener(e -> {
            String summary = TaskLogger.getLogSummary();
            JOptionPane.showMessageDialog(parentFrame, summary, "日志统计", JOptionPane.INFORMATION_MESSAGE);
        });
        buttonPanel.add(summaryButton);
        
        JButton clearButton = new JButton("清除日志");
        clearButton.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(parentFrame, 
                "确定要清除所有日志吗？", "确认", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                TaskLogger.clearLogs();
                JOptionPane.showMessageDialog(parentFrame, "日志已清除", "提示", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        buttonPanel.add(clearButton);
        
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    /**
     * 下载样本图像
     */
    private static void downloadSampleImages(JFrame parentFrame, JLabel imageCountLabel) {
        // 弹出对话框询问下载数量
        String input = JOptionPane.showInputDialog(parentFrame, 
            "请输入要下载的图像数量（1-50）：\n" +
            "图像来源：Lorem Picsum (picsum.photos)\n" +
            "图像规格：512×512 灰度图像", 
            "10");
        
        if (input == null) {
            return; // 用户取消
        }
        
        int count;
        try {
            count = Integer.parseInt(input);
            if (count < 1 || count > 50) {
                JOptionPane.showMessageDialog(parentFrame, 
                    "请输入1到50之间的数字！", 
                    "输入错误", 
                    JOptionPane.ERROR_MESSAGE);
                return;
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(parentFrame, 
                "请输入有效的数字！", 
                "输入错误", 
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // 创建进度对话框
        JDialog progressDialog = new JDialog(parentFrame, "下载进度", true);
        progressDialog.setSize(400, 150);
        progressDialog.setLocationRelativeTo(parentFrame);
        progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        
        JPanel progressPanel = new JPanel(new BorderLayout(10, 10));
        progressPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        JLabel statusLabel = new JLabel("准备下载...", SwingConstants.CENTER);
        progressPanel.add(statusLabel, BorderLayout.NORTH);
        
        JProgressBar progressBar = new JProgressBar(0, count);
        progressBar.setStringPainted(true);
        progressPanel.add(progressBar, BorderLayout.CENTER);
        
        JLabel detailLabel = new JLabel("", SwingConstants.CENTER);
        progressPanel.add(detailLabel, BorderLayout.SOUTH);
        
        progressDialog.setContentPane(progressPanel);
        
        // 在后台线程中下载
        final int finalCount = count;
        SwingWorker<List<File>, Void> downloadWorker = new SwingWorker<List<File>, Void>() {
            @Override
            protected List<File> doInBackground() throws Exception {
                File targetDir = new File(IMAGE_RESOURCE_DIR);
                return ImageResourceDownloader.downloadGrayscaleImages(
                    targetDir, 
                    finalCount, 
                    new ImageResourceDownloader.ProgressCallback() {
                        @Override
                        public void onProgress(int current, int total, String message) {
                            SwingUtilities.invokeLater(() -> {
                                progressBar.setValue(current);
                                statusLabel.setText(String.format("正在下载 %d/%d", current, total));
                                detailLabel.setText(message);
                            });
                        }
                        
                        @Override
                        public void onError(int index, String errorMessage) {
                            SwingUtilities.invokeLater(() -> {
                                detailLabel.setText("错误: " + errorMessage);
                            });
                        }
                    }
                );
            }
            
            @Override
            protected void done() {
                try {
                    List<File> downloaded = get();
                    progressDialog.dispose();
                    JOptionPane.showMessageDialog(parentFrame, 
                        String.format("成功下载 %d 张图像！\n保存位置: %s", 
                            downloaded.size(), 
                            new File(IMAGE_RESOURCE_DIR).getAbsolutePath()), 
                        "下载完成", 
                        JOptionPane.INFORMATION_MESSAGE);
                    
                    // 更新图像数量显示
                    File imageDir = new File(IMAGE_RESOURCE_DIR);
                    List<File> existingImages = ImageResourceDownloader.getExistingImages(imageDir);
                    imageCountLabel.setText("✓ 图像资源：" + existingImages.size() + " 张");
                } catch (Exception ex) {
                    progressDialog.dispose();
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(parentFrame, 
                        "下载失败: " + cause.getMessage(), 
                        "错误", 
                        JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        
        downloadWorker.execute();
        
        progressDialog.setVisible(true);
    }
    
    /**
     * 查看已有图像
     */
    private static void viewExistingImages(JFrame parentFrame) {
        File imageDir = new File(IMAGE_RESOURCE_DIR);
        List<File> images = ImageResourceDownloader.getExistingImages(imageDir);
        
        if (images.isEmpty()) {
            JOptionPane.showMessageDialog(parentFrame, 
                "暂无图像资源！\n请先点击 \"下载样本图像\" 按钮下载图像。", 
                "提示", 
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        // 显示图像列表
        StringBuilder message = new StringBuilder();
        message.append("找到 ").append(images.size()).append(" 张图像:\n\n");
        message.append("位置: ").append(imageDir.getAbsolutePath()).append("\n\n");
        message.append("文件列表:\n");
        
        int displayCount = Math.min(images.size(), 20);
        for (int i = 0; i < displayCount; i++) {
            message.append("  • ").append(images.get(i).getName()).append("\n");
        }
        
        if (images.size() > 20) {
            message.append("  ... 以及其他 ").append(images.size() - 20).append(" 个文件\n");
        }
        
        JOptionPane.showMessageDialog(parentFrame, 
            message.toString(), 
            "图像资源列表", 
            JOptionPane.INFORMATION_MESSAGE);
    }
    
    /**
     * 生成图像直方图
     */
    private static void generateHistograms(JFrame parentFrame) {
        File imageDir = new File(IMAGE_RESOURCE_DIR);
        List<File> images = ImageResourceDownloader.getExistingImages(imageDir);
        
        if (images.isEmpty()) {
            JOptionPane.showMessageDialog(parentFrame, 
                "暂无图像资源！\n请先下载样本图像。", 
                "提示", 
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        // 记录任务开始
        TaskLogger.logTask("生成直方图", TaskLogger.TaskStatus.STARTED, "开始处理 " + images.size() + " 张图像");
        
        // 创建进度对话框
        JDialog progressDialog = new JDialog(parentFrame, "生成直方图", true);
        progressDialog.setSize(400, 150);
        progressDialog.setLocationRelativeTo(parentFrame);
        progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        
        JPanel progressPanel = new JPanel(new BorderLayout(10, 10));
        progressPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        JLabel statusLabel = new JLabel("准备处理...", SwingConstants.CENTER);
        progressPanel.add(statusLabel, BorderLayout.NORTH);
        
        JProgressBar progressBar = new JProgressBar(0, images.size());
        progressBar.setStringPainted(true);
        progressPanel.add(progressBar, BorderLayout.CENTER);
        
        JLabel detailLabel = new JLabel("", SwingConstants.CENTER);
        progressPanel.add(detailLabel, BorderLayout.SOUTH);
        
        progressDialog.setContentPane(progressPanel);
        
        SwingWorker<List<ImageHistogram>, Void> worker = new SwingWorker<List<ImageHistogram>, Void>() {
            @Override
            protected List<ImageHistogram> doInBackground() throws Exception {
                return ImageMatcher.generateHistograms(imageDir, new ImageMatcher.ProgressCallback() {
                    @Override
                    public void onProgress(int current, int total, String message) {
                        SwingUtilities.invokeLater(() -> {
                            progressBar.setValue(current);
                            statusLabel.setText(String.format("正在处理 %d/%d", current, total));
                            detailLabel.setText(message);
                        });
                    }
                    
                    @Override
                    public void onError(int index, String errorMessage) {
                        SwingUtilities.invokeLater(() -> {
                            detailLabel.setText("错误: " + errorMessage);
                        });
                    }
                });
            }
            
            @Override
            protected void done() {
                try {
                    List<ImageHistogram> histograms = get();
                    progressDialog.dispose();
                    
                    // 显示结果
                    StringBuilder result = new StringBuilder();
                    result.append("成功生成 ").append(histograms.size()).append(" 个直方图\n\n");
                    result.append("前5个图像的直方图摘要:\n\n");
                    
                    int displayCount = Math.min(5, histograms.size());
                    for (int i = 0; i < displayCount; i++) {
                        result.append(i + 1).append(". ").append(histograms.get(i).getSummary()).append("\n");
                    }
                    
                    JOptionPane.showMessageDialog(parentFrame, 
                        result.toString(), 
                        "直方图生成完成", 
                        JOptionPane.INFORMATION_MESSAGE);
                    
                    // 记录任务完成
                    TaskLogger.logTask("生成直方图", TaskLogger.TaskStatus.COMPLETED, 
                        "成功处理 " + histograms.size() + " 张图像");
                    
                } catch (Exception ex) {
                    progressDialog.dispose();
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(parentFrame, 
                        "生成直方图失败: " + cause.getMessage(), 
                        "错误", 
                        JOptionPane.ERROR_MESSAGE);
                    
                    // 记录任务失败
                    TaskLogger.logTask("生成直方图", TaskLogger.TaskStatus.FAILED, 
                        "错误: " + cause.getMessage());
                }
            }
        };
        
        worker.execute();
        progressDialog.setVisible(true);
    }
    
    /**
     * 执行全图搜索
     */
    private static void performImageSearch(JFrame parentFrame) {
        File imageDir = new File(IMAGE_RESOURCE_DIR);
        List<File> images = ImageResourceDownloader.getExistingImages(imageDir);
        
        if (images.isEmpty()) {
            JOptionPane.showMessageDialog(parentFrame, 
                "暂无图像资源！\n请先下载样本图像。", 
                "提示", 
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        // 选择查询图像
        JFileChooser fileChooser = new JFileChooser(imageDir);
        fileChooser.setDialogTitle("选择查询图像");
        fileChooser.setFileFilter(new FileNameExtensionFilter("图像文件 (*.jpg, *.jpeg, *.png, *.bmp)", 
            "jpg", "jpeg", "png", "bmp"));
        
        int result = fileChooser.showOpenDialog(parentFrame);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        
        File queryImage = fileChooser.getSelectedFile();
        String cacheKey = CacheManager.generateSearchKey(queryImage.getName());
        
        // 检查缓存
        String cachedResult = CacheManager.get(cacheKey);
        if (cachedResult != null) {
            JOptionPane.showMessageDialog(parentFrame, 
                "从缓存中获取结果:\n\n" + cachedResult, 
                "搜索结果（缓存）", 
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        // 记录任务开始
        TaskLogger.logTask("全图搜索", TaskLogger.TaskStatus.STARTED, "查询图像: " + queryImage.getName());
        
        // 创建进度对话框
        JDialog progressDialog = new JDialog(parentFrame, "搜索中", true);
        progressDialog.setSize(400, 100);
        progressDialog.setLocationRelativeTo(parentFrame);
        progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        
        JLabel statusLabel = new JLabel("正在搜索匹配图像...", SwingConstants.CENTER);
        progressDialog.add(statusLabel);
        
        SwingWorker<List<ImageMatcher.MatchResult>, Void> worker = new SwingWorker<List<ImageMatcher.MatchResult>, Void>() {
            @Override
            protected List<ImageMatcher.MatchResult> doInBackground() throws Exception {
                return ImageMatcher.searchImage(queryImage, imageDir, 5);
            }
            
            @Override
            protected void done() {
                try {
                    List<ImageMatcher.MatchResult> results = get();
                    progressDialog.dispose();
                    
                    StringBuilder message = new StringBuilder();
                    message.append("查询图像: ").append(queryImage.getName()).append("\n\n");
                    message.append("找到 ").append(results.size()).append(" 个最匹配的结果:\n\n");
                    
                    for (int i = 0; i < results.size(); i++) {
                        message.append(i + 1).append(". ").append(results.get(i).toString()).append("\n");
                    }
                    
                    String resultStr = message.toString();
                    
                    // 缓存结果
                    CacheManager.put(cacheKey, resultStr, "全图搜索");
                    
                    JOptionPane.showMessageDialog(parentFrame, 
                        resultStr, 
                        "搜索结果", 
                        JOptionPane.INFORMATION_MESSAGE);
                    
                    // 记录任务完成
                    TaskLogger.logTask("全图搜索", TaskLogger.TaskStatus.COMPLETED, 
                        "查询图像: " + queryImage.getName() + ", 找到 " + results.size() + " 个结果");
                    
                } catch (Exception ex) {
                    progressDialog.dispose();
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(parentFrame, 
                        "搜索失败: " + cause.getMessage(), 
                        "错误", 
                        JOptionPane.ERROR_MESSAGE);
                    
                    // 记录任务失败
                    TaskLogger.logTask("全图搜索", TaskLogger.TaskStatus.FAILED, 
                        "错误: " + cause.getMessage());
                }
            }
        };
        
        worker.execute();
        progressDialog.setVisible(true);
    }
    
    /**
     * 执行局部特征搜索
     */
    private static void performLocalFeatureSearch(JFrame parentFrame) {
        File imageDir = new File(IMAGE_RESOURCE_DIR);
        List<File> images = ImageResourceDownloader.getExistingImages(imageDir);
        
        if (images.isEmpty()) {
            JOptionPane.showMessageDialog(parentFrame, 
                "暂无图像资源！\n请先下载样本图像。", 
                "提示", 
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        // 选择特征图像
        JFileChooser fileChooser = new JFileChooser(imageDir);
        fileChooser.setDialogTitle("选择局部特征图像");
        fileChooser.setFileFilter(new FileNameExtensionFilter("图像文件 (*.jpg, *.jpeg, *.png, *.bmp)", 
            "jpg", "jpeg", "png", "bmp"));
        
        int result = fileChooser.showOpenDialog(parentFrame);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        
        File featureImage = fileChooser.getSelectedFile();
        
        // 输入相似度阈值
        String thresholdStr = JOptionPane.showInputDialog(parentFrame, 
            "请输入相似度阈值（0.00-1.00）：\n建议值：0.95", 
            "0.95");
        
        if (thresholdStr == null) {
            return;
        }
        
        double threshold;
        try {
            threshold = Double.parseDouble(thresholdStr);
            if (threshold < 0 || threshold > 1) {
                JOptionPane.showMessageDialog(parentFrame, 
                    "阈值必须在0到1之间！", 
                    "输入错误", 
                    JOptionPane.ERROR_MESSAGE);
                return;
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(parentFrame, 
                "请输入有效的数字！", 
                "输入错误", 
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        String cacheKey = CacheManager.generateLocalFeatureKey(featureImage.getName(), threshold);
        
        // 检查缓存
        String cachedResult = CacheManager.get(cacheKey);
        if (cachedResult != null) {
            JOptionPane.showMessageDialog(parentFrame, 
                "从缓存中获取结果:\n\n" + cachedResult, 
                "搜索结果（缓存）", 
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        // 记录任务开始
        TaskLogger.logTask("局部特征搜索", TaskLogger.TaskStatus.STARTED, 
            "特征图像: " + featureImage.getName() + ", 阈值: " + threshold);
        
        // 创建进度对话框
        JDialog progressDialog = new JDialog(parentFrame, "搜索中", true);
        progressDialog.setSize(400, 100);
        progressDialog.setLocationRelativeTo(parentFrame);
        progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        
        JLabel statusLabel = new JLabel("正在搜索局部特征...", SwingConstants.CENTER);
        progressDialog.add(statusLabel);
        
        final double finalThreshold = threshold;
        SwingWorker<List<LocalFeatureMatcher.LocalMatchResult>, Void> worker = 
            new SwingWorker<List<LocalFeatureMatcher.LocalMatchResult>, Void>() {
            @Override
            protected List<LocalFeatureMatcher.LocalMatchResult> doInBackground() throws Exception {
                return LocalFeatureMatcher.searchLocalFeature(featureImage, imageDir, finalThreshold);
            }
            
            @Override
            protected void done() {
                try {
                    List<LocalFeatureMatcher.LocalMatchResult> results = get();
                    progressDialog.dispose();
                    
                    StringBuilder message = new StringBuilder();
                    message.append("特征图像: ").append(featureImage.getName()).append("\n");
                    message.append("相似度阈值: ").append(String.format("%.2f%%", finalThreshold * 100)).append("\n\n");
                    
                    int totalMatches = 0;
                    for (LocalFeatureMatcher.LocalMatchResult r : results) {
                        if (r.getMatchCount() > 0) {
                            totalMatches++;
                        }
                    }
                    
                    message.append("找到 ").append(totalMatches).append(" 张包含该特征的图像\n\n");
                    
                    int displayCount = 0;
                    for (LocalFeatureMatcher.LocalMatchResult r : results) {
                        if (r.getMatchCount() > 0 && displayCount < 5) {
                            message.append(r.toString()).append("\n");
                            displayCount++;
                        }
                    }
                    
                    if (totalMatches > 5) {
                        message.append("... 以及其他 ").append(totalMatches - 5).append(" 个匹配\n");
                    }
                    
                    String resultStr = message.toString();
                    
                    // 缓存结果
                    CacheManager.put(cacheKey, resultStr, "局部特征搜索");
                    
                    JOptionPane.showMessageDialog(parentFrame, 
                        resultStr, 
                        "搜索结果", 
                        JOptionPane.INFORMATION_MESSAGE);
                    
                    // 记录任务完成
                    TaskLogger.logTask("局部特征搜索", TaskLogger.TaskStatus.COMPLETED, 
                        "特征图像: " + featureImage.getName() + ", 找到 " + totalMatches + " 个匹配");
                    
                } catch (Exception ex) {
                    progressDialog.dispose();
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(parentFrame, 
                        "搜索失败: " + cause.getMessage(), 
                        "错误", 
                        JOptionPane.ERROR_MESSAGE);
                    
                    // 记录任务失败
                    TaskLogger.logTask("局部特征搜索", TaskLogger.TaskStatus.FAILED, 
                        "错误: " + cause.getMessage());
                }
            }
        };
        
        worker.execute();
        progressDialog.setVisible(true);
    }
    
    /**
     * 执行篡改检测
     */
    private static void performTamperDetection(JFrame parentFrame) {
        File imageDir = new File(IMAGE_RESOURCE_DIR);
        List<File> images = ImageResourceDownloader.getExistingImages(imageDir);
        
        if (images.isEmpty()) {
            JOptionPane.showMessageDialog(parentFrame, 
                "暂无图像资源！\n请先下载样本图像。", 
                "提示", 
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        // 选择疑似篡改图像
        JFileChooser fileChooser = new JFileChooser(imageDir);
        fileChooser.setDialogTitle("选择疑似篡改图像");
        fileChooser.setFileFilter(new FileNameExtensionFilter("图像文件 (*.jpg, *.jpeg, *.png, *.bmp)", 
            "jpg", "jpeg", "png", "bmp"));
        
        int result = fileChooser.showOpenDialog(parentFrame);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        
        File suspectImage = fileChooser.getSelectedFile();
        
        // 输入差异阈值
        String thresholdStr = JOptionPane.showInputDialog(parentFrame, 
            "请输入灰度差异阈值（0-255）：\n建议值：20", 
            "20");
        
        if (thresholdStr == null) {
            return;
        }
        
        int threshold;
        try {
            threshold = Integer.parseInt(thresholdStr);
            if (threshold < 0 || threshold > 255) {
                JOptionPane.showMessageDialog(parentFrame, 
                    "阈值必须在0到255之间！", 
                    "输入错误", 
                    JOptionPane.ERROR_MESSAGE);
                return;
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(parentFrame, 
                "请输入有效的数字！", 
                "输入错误", 
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        String cacheKey = CacheManager.generateTamperKey(suspectImage.getName(), threshold);
        
        // 检查缓存
        String cachedResult = CacheManager.get(cacheKey);
        if (cachedResult != null) {
            JOptionPane.showMessageDialog(parentFrame, 
                "从缓存中获取结果:\n\n" + cachedResult, 
                "检测结果（缓存）", 
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        // 记录任务开始
        TaskLogger.logTask("篡改检测", TaskLogger.TaskStatus.STARTED, 
            "疑似篡改图像: " + suspectImage.getName() + ", 阈值: " + threshold);
        
        // 创建进度对话框
        JDialog progressDialog = new JDialog(parentFrame, "检测中", true);
        progressDialog.setSize(400, 100);
        progressDialog.setLocationRelativeTo(parentFrame);
        progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        
        JLabel statusLabel = new JLabel("正在检测图像篡改...", SwingConstants.CENTER);
        progressDialog.add(statusLabel);
        
        final int finalThreshold = threshold;
        SwingWorker<List<TamperDetector.TamperResult>, Void> worker = 
            new SwingWorker<List<TamperDetector.TamperResult>, Void>() {
            @Override
            protected List<TamperDetector.TamperResult> doInBackground() throws Exception {
                return TamperDetector.detectTampering(suspectImage, imageDir, finalThreshold);
            }
            
            @Override
            protected void done() {
                try {
                    List<TamperDetector.TamperResult> results = get();
                    progressDialog.dispose();
                    
                    StringBuilder message = new StringBuilder();
                    message.append("疑似篡改图像: ").append(suspectImage.getName()).append("\n");
                    message.append("差异阈值: ").append(finalThreshold).append("\n\n");
                    
                    if (results.isEmpty()) {
                        message.append("未找到匹配的原始图像\n");
                    } else {
                        message.append("最匹配的原始图像（前3个）:\n\n");
                        int displayCount = Math.min(3, results.size());
                        for (int i = 0; i < displayCount; i++) {
                            message.append(i + 1).append(". ").append(results.get(i).toString()).append("\n");
                        }
                    }
                    
                    String resultStr = message.toString();
                    
                    // 缓存结果
                    CacheManager.put(cacheKey, resultStr, "篡改检测");
                    
                    JOptionPane.showMessageDialog(parentFrame, 
                        resultStr, 
                        "检测结果", 
                        JOptionPane.INFORMATION_MESSAGE);
                    
                    // 记录任务完成
                    TaskLogger.logTask("篡改检测", TaskLogger.TaskStatus.COMPLETED, 
                        "疑似篡改图像: " + suspectImage.getName() + ", 检测完成");
                    
                } catch (Exception ex) {
                    progressDialog.dispose();
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(parentFrame, 
                        "检测失败: " + cause.getMessage(), 
                        "错误", 
                        JOptionPane.ERROR_MESSAGE);
                    
                    // 记录任务失败
                    TaskLogger.logTask("篡改检测", TaskLogger.TaskStatus.FAILED, 
                        "错误: " + cause.getMessage());
                }
            }
        };
        
        worker.execute();
        progressDialog.setVisible(true);
    }
    
    /**
     * 查看缓存统计
     */
    private static void viewCacheStats(JFrame parentFrame) {
        String stats = CacheManager.getCacheStats();
        
        JTextArea textArea = new JTextArea(stats);
        textArea.setEditable(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(600, 400));
        
        JOptionPane.showMessageDialog(parentFrame, 
            scrollPane, 
            "缓存统计", 
            JOptionPane.INFORMATION_MESSAGE);
    }
    
    /**
     * 查看日志
     */
    private static void viewLogs(JFrame parentFrame) {
        List<TaskLogger.TaskRecord> records = TaskLogger.readLogs();
        
        if (records.isEmpty()) {
            JOptionPane.showMessageDialog(parentFrame, 
                "暂无任务日志", 
                "日志查看", 
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        StringBuilder message = new StringBuilder();
        message.append("任务日志（共 ").append(records.size()).append(" 条）\n\n");
        
        // 显示最近20条
        int displayCount = Math.min(20, records.size());
        for (int i = records.size() - displayCount; i < records.size(); i++) {
            message.append(records.get(i).toString()).append("\n\n");
        }
        
        if (records.size() > 20) {
            message.append("... 以及更早的 ").append(records.size() - 20).append(" 条记录\n");
        }
        
        JTextArea textArea = new JTextArea(message.toString());
        textArea.setEditable(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(700, 500));
        
        JOptionPane.showMessageDialog(parentFrame, 
            scrollPane, 
            "任务日志", 
            JOptionPane.INFORMATION_MESSAGE);
    }
}
