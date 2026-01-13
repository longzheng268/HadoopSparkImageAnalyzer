# 改进说明 - HBase图像管理与搜索优化

## 问题描述

原系统存在以下问题：

1. **图像库为空错误**：即使手动选择了本地图像，系统仍然报告"图像库为空"
2. **无法上传到HBase**：选择本地图像后没有自动上传到HBase的功能
3. **缺少HBase管理功能**：没有独立的功能来管理HBase中的图像
4. **搜索功能单一**：搜索功能只能从本地文件选择图像，无法使用HBase中的图像
5. **逻辑混乱**：本地文件和HBase数据源的逻辑不清晰

## 解决方案

### 1. 新增HBase图像管理选项卡

在主界面添加了专门的"HBase图像管理"选项卡，提供以下功能：

#### 功能列表：
- **查看HBase图像**：显示HBase中存储的所有图像列表，包括文件名和尺寸信息
- **上传本地图像到HBase**：支持选择多张图像同时上传
- **批量上传到HBase**：一键将本地图像目录中的所有图像批量上传
- **删除HBase图像**：从HBase中删除指定图像

#### 技术实现：
```java
// 新增方法
- viewHBaseImages(): 查看HBase中的图像列表
- uploadLocalImageToHBase(): 上传单张或多张图像
- batchUploadToHBase(): 批量上传本地所有图像
- deleteHBaseImage(): 删除指定图像
- updateImageCountLabel(): 更新图像数量显示

// HBaseManager新增方法
- deleteImage(String filename): 删除指定图像
```

### 2. 改进图像搜索功能

#### 改进前：
```java
// 只能从本地文件选择
JFileChooser fileChooser = new JFileChooser(imageDir);
int result = fileChooser.showOpenDialog(parentFrame);
File queryImage = fileChooser.getSelectedFile();
```

#### 改进后：
```java
// 先询问用户选择图像来源
String[] options = {"从本地文件选择", "从HBase选择"};
int choice = JOptionPane.showOptionDialog(...);

if (choice == 0) {
    // 从本地文件选择，并提供上传到HBase的选项
} else {
    // 从HBase选择，直接读取HBase中的图像
}
```

#### 新增功能：
1. **双重图像源支持**：用户可以选择从本地文件或HBase中选择查询图像
2. **自动上传提示**：选择本地图像后，询问是否上传到HBase
3. **智能错误检查**：检查本地和HBase图像库状态，提供明确的错误提示

### 3. 改进局部特征搜索

应用与全图搜索相同的改进：
- 支持从本地或HBase选择特征图像
- 自动检查图像库状态
- 提供清晰的错误提示

### 4. 改进篡改检测

应用与全图搜索相同的改进：
- 支持从本地或HBase选择疑似篡改图像
- 自动检查图像库状态
- 提供清晰的错误提示

### 5. 改进错误提示

#### 改进前：
```
暂无图像资源！
请先下载样本图像。
```

#### 改进后：
```
图像库为空！
本地文件: 0 张
HBase: 0 张

请先下载样本图像或上传图像到HBase。
```

## 使用场景

### 场景1：第一次使用系统

1. 打开程序，进入"数据清洗"选项卡
2. 点击"下载样本图像"，下载10-20张测试图像
3. 进入"HBase图像管理"选项卡
4. 点击"批量上传到HBase"，将所有图像上传到HBase
5. 现在可以在搜索功能中使用HBase中的图像

### 场景2：使用本地图像进行搜索

1. 进入"图像搜索"选项卡
2. 点击"开始全图搜索"
3. 选择"从本地文件选择"
4. 选择要查询的图像
5. 系统询问是否上传到HBase（建议选择"是"）
6. 查看搜索结果

### 场景3：使用HBase图像进行搜索

1. 进入"图像搜索"选项卡
2. 点击"开始全图搜索"
3. 选择"从HBase选择"
4. 从下拉列表中选择查询图像
5. 查看搜索结果

### 场景4：管理HBase中的图像

1. 进入"HBase图像管理"选项卡
2. 点击"查看HBase图像"查看当前存储的图像
3. 如需删除某张图像，点击"删除HBase图像"
4. 从列表中选择要删除的图像
5. 确认删除

## 技术细节

### 图像源切换逻辑

```java
// 检查图像库状态
File imageDir = new File(IMAGE_RESOURCE_DIR);
List<File> localImages = ImageResourceDownloader.getExistingImages(imageDir);
int hbaseCount = 0;
try {
    hbaseCount = HBaseManager.getImageCount();
} catch (Exception e) {
    // HBase不可用
}

// 如果两个源都为空，显示错误
if (localImages.isEmpty() && hbaseCount == 0) {
    JOptionPane.showMessageDialog(parentFrame,
        "图像库为空！\n本地文件: 0 张\nHBase: 0 张\n\n请先下载样本图像或上传图像到HBase。",
        "提示",
        JOptionPane.INFORMATION_MESSAGE);
    return;
}
```

### HBase图像读取

```java
// 从HBase读取图像
BufferedImage img = HBaseManager.getImage(imageName);
if (img != null) {
    // 创建临时文件
    File tempFile = File.createTempFile("hbase_query_", ".png");
    tempFile.deleteOnExit();
    javax.imageio.ImageIO.write(img, "png", tempFile);
    // 使用临时文件进行后续处理
}
```

### 批量上传优化

使用SwingWorker在后台线程执行批量上传，避免阻塞GUI：

```java
SwingWorker<Integer, Void> worker = new SwingWorker<Integer, Void>() {
    @Override
    protected Integer doInBackground() throws Exception {
        int successCount = 0;
        for (File imageFile : images) {
            try {
                ImageHistogram histogram = new ImageHistogram(imageFile);
                HBaseManager.storeImage(imageFile, histogram);
                successCount++;
            } catch (Exception e) {
                // 记录错误，继续处理下一张
            }
        }
        return successCount;
    }
};
```

## 代码变更统计

### 修改的文件：
1. **Main.java**：+705行，-72行
   - 新增HBase管理面板
   - 改进搜索功能
   - 改进错误提示

2. **HBaseManager.java**：+18行，-0行
   - 新增deleteImage方法

3. **README.md**：更新文档
   - 添加HBase管理模块说明
   - 更新搜索功能说明
   - 更新使用方法

### 新增的功能方法：
- `createHBaseManagementPanel()`
- `viewHBaseImages()`
- `uploadLocalImageToHBase()`
- `batchUploadToHBase()`
- `deleteHBaseImage()`
- `updateImageCountLabel()`

## 测试建议

### 1. HBase管理功能测试
- [ ] 测试查看HBase图像列表
- [ ] 测试上传单张图像到HBase
- [ ] 测试批量上传到HBase
- [ ] 测试从HBase删除图像

### 2. 搜索功能测试
- [ ] 测试从本地选择查询图像
- [ ] 测试从HBase选择查询图像
- [ ] 测试自动上传提示功能
- [ ] 测试空图像库错误提示

### 3. 局部特征搜索测试
- [ ] 测试从本地选择特征图像
- [ ] 测试从HBase选择特征图像

### 4. 篡改检测测试
- [ ] 测试从本地选择疑似篡改图像
- [ ] 测试从HBase选择疑似篡改图像

## 注意事项

1. **HBase连接**：确保HBase服务已启动，否则HBase相关功能将不可用
2. **临时文件**：从HBase读取的图像会创建临时文件，程序退出时自动清理
3. **重复上传**：系统不会自动检查重复，建议手动避免重复上传相同图像
4. **性能考虑**：批量上传大量图像时会占用较多时间，建议分批上传

## 总结

通过这次改进，系统现在支持：
- ✅ 独立的HBase图像管理功能
- ✅ 本地和HBase双重图像源
- ✅ 自动上传提示
- ✅ 清晰的错误提示
- ✅ 统一的图像选择逻辑

用户不再需要担心"图像库为空"的问题，可以灵活地在本地文件和HBase之间切换使用图像资源。
