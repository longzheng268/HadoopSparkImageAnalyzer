# HadoopSparkImageAnalyzer

## 项目简介

本项目是一个面向海量黑白图像数据的分布式处理与分析系统，利用 Hadoop 和 Spark 大数据技术，实现图像的高效处理、特征搜索、篡改检测及结果缓存。系统以 BOSSBase 图像数据库（1000 张 512×512 灰度图像）为数据源，通过分布式计算框架优化处理速度，支持全图搜索、局部特征匹配和篡改检查等核心功能。

## 技术栈

- **编程语言**：Java 1.8+
- **构建工具**：Gradle
- **GUI框架**：Java Swing
- **大数据框架**：Hadoop、Spark（需手动启用依赖）
- **开发工具**：兼容 Eclipse IDE

## 项目结构

```
HadoopSparkImageAnalyzer/
├── build.gradle                # Gradle构建配置
├── settings.gradle             # Gradle项目设置
├── .gitignore                  # Git忽略文件配置
├── README.md                   # 项目说明文档
└── src/
    └── main/
        ├── java/
        │   └── com/analyzer/
        │       ├── Main.java           # 主程序入口（Swing GUI）
        │       └── core/               # 核心功能包（预留）
        │           └── CorePackageInfo.java
        └── resources/                   # 资源文件目录（预留）
```

## 快速开始

### 前置要求

- **Java JDK 1.8** 或更高版本
- **Gradle 6.0+**（或使用项目自带的Gradle Wrapper）

### 构建项目

在项目根目录下执行：

```bash
# Linux/Mac
./gradlew build

# Windows
gradlew.bat build
```

### 运行程序

```bash
# Linux/Mac
./gradlew run

# Windows
gradlew.bat run
```

运行成功后会弹出一个 Swing GUI 窗口，显示项目基本信息。

### 生成Eclipse项目配置

如需将项目导入 Eclipse IDE，执行以下命令生成Eclipse配置文件：

```bash
# Linux/Mac
./gradlew eclipse

# Windows
gradlew.bat eclipse
```

然后在 Eclipse 中选择 **File → Import → Existing Projects into Workspace**，选择项目目录即可导入。

## 依赖管理

### 当前依赖

项目目前仅依赖 Java 内置的 Swing 库，无需额外下载。

### 启用Hadoop/Spark依赖

为避免自动下载大文件，Hadoop 和 Spark 依赖默认以注释形式保留在 `build.gradle` 中。如需启用，请手动取消注释以下部分：

```gradle
dependencies {
    // 取消以下注释以启用Hadoop/Spark依赖
    // implementation 'org.apache.spark:spark-core_2.12:3.3.0'
    // implementation 'org.apache.hadoop:hadoop-client:3.3.4'
    // implementation 'org.apache.hadoop:hadoop-common:3.3.4'
}
```

启用后，执行 `./gradlew build` 会自动下载相关依赖。

## 开发指南

### 添加业务逻辑

1. **核心功能**：在 `src/main/java/com/analyzer/core/` 目录下添加业务类，如：
   - 图像直方图计算
   - 图像特征搜索
   - 图像篡改检测
   - 结果缓存管理

2. **GUI扩展**：修改 `Main.java` 中的 Swing 界面，添加按钮、输入框等组件。

3. **资源文件**：将配置文件、图像数据等资源放置在 `src/main/resources/` 目录下。

### 常用Gradle命令

```bash
# 清理构建文件
./gradlew clean

# 编译代码
./gradlew compileJava

# 运行程序
./gradlew run

# 生成Eclipse配置
./gradlew eclipse

# 清理Eclipse配置
./gradlew cleanEclipse

# 查看所有任务
./gradlew tasks
```

## 后续开发计划

- [ ] 实现图像直方图计算功能
- [ ] 集成 Hadoop MapReduce 进行分布式处理
- [ ] 集成 Spark RDD 优化处理性能
- [ ] 实现图像特征搜索算法
- [ ] 实现图像篡改检测算法
- [ ] 添加结果缓存机制
- [ ] 完善 GUI 界面功能

## 许可证

本项目仅供学习和研究使用。

## 联系方式

如有问题或建议，请通过 GitHub Issues 反馈。
