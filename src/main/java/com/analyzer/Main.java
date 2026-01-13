package com.analyzer;

import javax.swing.*;
import java.awt.*;

/**
 * HadoopSparkImageAnalyzer - 主程序入口
 * 用于处理海量黑白图像的分布式分析系统
 */
public class Main {
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
        JPanel contentPanel = new JPanel(new GridLayout(5, 1, 10, 10));
        contentPanel.add(new JLabel("✓ 项目启动成功！", SwingConstants.CENTER));
        contentPanel.add(new JLabel("✓ Java版本：" + System.getProperty("java.version"), SwingConstants.CENTER));
        contentPanel.add(new JLabel("✓ 操作系统：" + System.getProperty("os.name"), SwingConstants.CENTER));
        contentPanel.add(new JLabel("", SwingConstants.CENTER)); // 空行
        JLabel instructionLabel = new JLabel("请在此扩展功能（如直方图计算、图像搜索等）", SwingConstants.CENTER);
        instructionLabel.setForeground(Color.BLUE);
        contentPanel.add(instructionLabel);

        mainPanel.add(contentPanel, BorderLayout.CENTER);

        // 底部按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        
        JButton testButton = new JButton("测试按钮");
        testButton.addActionListener(e -> {
            JOptionPane.showMessageDialog(frame, 
                "这是一个示例按钮！\n后续可在此添加业务逻辑。", 
                "提示", 
                JOptionPane.INFORMATION_MESSAGE);
        });
        buttonPanel.add(testButton);

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
        System.out.println("========================================");
    }
}
