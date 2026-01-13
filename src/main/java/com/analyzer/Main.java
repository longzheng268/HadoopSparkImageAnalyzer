package com.analyzer;

import com.analyzer.core.ImageResourceDownloader;

import javax.swing.*;
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
        frame.setSize(600, 400);
        frame.setLocationRelativeTo(null); // 窗口居中显示

        // 创建主面板
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // 标题标签
        JLabel titleLabel = new JLabel("HadoopSparkImageAnalyzer", SwingConstants.CENTER);
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        // 中间内容面板
        JPanel contentPanel = new JPanel(new GridLayout(6, 1, 10, 10));
        contentPanel.add(new JLabel("✓ 项目启动成功！", SwingConstants.CENTER));
        contentPanel.add(new JLabel("✓ Java版本：" + System.getProperty("java.version"), SwingConstants.CENTER));
        contentPanel.add(new JLabel("✓ 操作系统：" + System.getProperty("os.name"), SwingConstants.CENTER));
        
        // 显示现有图像数量
        File imageDir = new File(IMAGE_RESOURCE_DIR);
        List<File> existingImages = ImageResourceDownloader.getExistingImages(imageDir);
        JLabel imageCountLabel = new JLabel("✓ 图像资源：" + existingImages.size() + " 张", SwingConstants.CENTER);
        contentPanel.add(imageCountLabel);
        
        contentPanel.add(new JLabel("", SwingConstants.CENTER)); // 空行
        JLabel instructionLabel = new JLabel("点击下方按钮下载样本图像或开始分析", SwingConstants.CENTER);
        instructionLabel.setForeground(Color.BLUE);
        contentPanel.add(instructionLabel);

        mainPanel.add(contentPanel, BorderLayout.CENTER);

        // 底部按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        
        // 下载图像按钮
        JButton downloadButton = new JButton("下载样本图像");
        downloadButton.addActionListener(e -> {
            downloadSampleImages(frame, imageCountLabel);
        });
        buttonPanel.add(downloadButton);
        
        // 查看图像按钮
        JButton viewButton = new JButton("查看已有图像");
        viewButton.addActionListener(e -> {
            viewExistingImages(frame);
        });
        buttonPanel.add(viewButton);

        JButton exitButton = new JButton("退出程序");
        exitButton.addActionListener(e -> {
            frame.dispose();
            System.exit(0);
        });
        buttonPanel.add(exitButton);

        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

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
        new Thread(() -> {
            try {
                File targetDir = new File(IMAGE_RESOURCE_DIR);
                List<File> downloaded = ImageResourceDownloader.downloadGrayscaleImages(
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
                
                // 下载完成
                SwingUtilities.invokeLater(() -> {
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
                });
                
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    progressDialog.dispose();
                    JOptionPane.showMessageDialog(parentFrame, 
                        "下载失败: " + ex.getMessage(), 
                        "错误", 
                        JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
        
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
}
