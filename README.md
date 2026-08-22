# JMComic PDF

Android 漫画下载器 — 输入车号，自动下载漫画图片并合成 PDF。

基于 [JMComic-Crawler-Python](https://github.com/hect0x7/JMComic-Crawler-Python) (`jmcomic`)。

## 功能

- 输入 JM 漫画车号（album ID），自动识别单章节/多章节漫画
- **单章节漫画**：一键下载，直接生成 PDF
- **多章节漫画**：弹出章节列表，自由勾选要下载的章节，每章生成独立 PDF——不再合并为单个巨大文件
- **多漫画管理**：PDF 按漫画 ID 分目录存储，支持同时存放多部漫画
- **已下载跳过**：再次搜索已下载的漫画时，已下载章节显示标记并默认跳过
- **下载管理**：设置页漫画卡片式分组，可展开查看章节、模糊搜索、删除单章或整部
- 自动处理图片扰码（scrambling），还原正确画面
- 支持 WebP 格式图片（Android 原生解码）
- **Material 3 主题**：深色模式（跟随系统 / 浅色 / 深色三态切换）+ Android 12+ 动态取色
- **漫画封面缩略图**：取第一章第一张图（自动解扰），展示于下载管理与章节选择弹窗
- 自适应应用图标（保留原图标图案）+ Android 12+ 启动屏
- 删除整部漫画二次确认，防止误删
- 代理自动探测（Clash / v2ray 常见端口），零配置

## 技术栈

| 层级 | 技术 |
|------|------|
| UI | Kotlin + Jetpack Compose + Material 3 |
| 导航 | Scaffold + NavigationBar（首页 / 设置） |
| 状态管理 | ViewModel + StateFlow |
| 数据持久化 | JSON 文件（下载历史记录） |
| Python 运行时 | [Chaquopy](https://chaquo.com/chaquopy/) 15.0.1 |
| 爬虫库 | `jmcomic` 2.7.2 |
| 图片解码 | Android BitmapFactory + Pillow 9.2.0 |
| PDF 合成 | Pillow |
| 最低 SDK | Android 8.0 (API 26) |
| 目标 SDK | Android 14 (API 34) |

## 项目结构

```
├── build.gradle.kts              # 根构建配置
├── settings.gradle.kts           # 模块设置 + 仓库
├── gradle.properties             # Gradle 参数
├── gradlew / gradlew.bat         # Gradle Wrapper 脚本
│
└── app/
    ├── build.gradle              # 应用构建配置 (Groovy DSL)
    ├── proguard-rules.pro        # 混淆规则
    └── src/main/
        ├── python/
        │   └── jm_bridge.py      # Python 桥接：下载 + 解扰 + PDF
        ├── java/com/jmcomic/pdfapp/
        │   ├── JMComicApp.kt     # Application（崩溃日志 + Python 初始化）
        │   ├── MainActivity.kt   # 入口 Activity + 底部导航 + PDF 打开
        │   ├── model/
        │   │   └── Models.kt     # 共享数据类（ChapterInfo / DownloadRecord / UI State）
        │   ├── data/
        │   │   ├── DownloadHistoryManager.kt  # 下载历史 JSON 持久化
        │   │   └── ThemePrefs.kt              # 主题模式持久化（跟随系统/浅色/深色）
        │   ├── ui/
        │   │   ├── components/      # GradientButton / CoverImage 公共组件
        │   │   ├── theme/           # 颜色 / 字体 / Material 3 主题（明暗双主题 + 动态取色）
        │   │   └── screen/
        │   │       ├── HomeScreen.kt            # 首页（输入 + 下载）
        │   │       ├── ChapterSelectDialog.kt   # 多章节选择弹窗
        │   │       └── SettingsScreen.kt        # 设置页（下载管理 + 搜索 + 主题切换）
        │   └── viewmodel/
        │       ├── HomeViewModel.kt     # 首页逻辑（获取信息 → 下载）
        │       └── SettingsViewModel.kt # 设置页逻辑（历史、搜索、删除）
        ├── AndroidManifest.xml
        └── res/
            ├── drawable-nodpi/       # 自适应图标前景位图
            ├── mipmap-anydpi-v26/    # 自适应图标（保留原图标图案）
            ├── values-v31/           # Android 12+ 启动屏主题
            ├── xml/file_paths.xml    # FileProvider 路径
            └── values/themes.xml     # 原生主题
```

## 构建

### 前置条件

- **JDK 17+**
- **Android SDK 34**（platforms;android-34、build-tools;34.0.0）
- **Python 3.11**（Chaquopy 15.0.1 构建用）
- Android Studio（可选）或仅用命令行

### 步骤

```bash
# 1. 克隆仓库
git clone https://github.com/Duyell/jmcomicA.git
cd jmcomicA

# 2. 创建 local.properties，指向你的 Android SDK
#    内容：sdk.dir=你的SDK路径
#    例如 macOS: sdk.dir=/Users/xxx/Library/Android/sdk
#    例如 Windows: sdk.dir=D\:\\Tools\\android-sdk

# 3. 修改 app/build.gradle 中的 Python 路径
#    找到 buildPython 并将其改为你的 Python 3.11 路径

# 4. 构建 Debug APK
./gradlew assembleDebug     # macOS / Linux
gradlew.bat assembleDebug   # Windows

# 5. 构建 Release APK（需要 keystore.properties）
./gradlew assembleRelease
```

APK 位于：
- Debug: `app/build/outputs/apk/debug/JMComicPdf-v1.3.1.apk`
- Release: `app/build/outputs/apk/release/JMComicPdf-v1.3.1.apk`

> 注意：debug 版与 release 版签名不同，从 debug 切换到 release 需先卸载旧版（会清空应用数据），release 之间的升级可直接覆盖安装。

## 核心流程

### 单章节漫画

```
用户输入车号 → HomeViewModel.onDownloadTapped()
    ↓
Python get_album_info() → 章节数 ≤ 1
    ↓
Python get_pdf_path() → download_album_as_pdf()
    ↓
1. jmcomic API 下载漫画元数据 + 图片（WebP 格式，已扰码）
2. Android BitmapFactory 解码 WebP
3. Canvas API 解扰（strip reorder）
4. Pillow 合成单个 PDF
    ↓
HomeScreen → 打开 PDF → FileProvider → 系统阅读器
```

### 多章节漫画

```
用户输入车号 → HomeViewModel.onDownloadTapped()
    ↓
Python get_album_info() → 章节数 > 1
    ↓
ChapterSelectDialog（勾选要下载的章节）
    ↓ 用户确认
Python download_selected_chapters()
    ↓
1. jmcomic API client 获取专辑详情（78 章）
2. option.download_photo(photo_id) 只下载选中章节的图片
3. 解扰 → 逐章合成 PDF
    ↓
HomeScreen → 逐章结果卡片（可独立打开每个 PDF）
    ↓
下载记录存入 download_history.json → 设置页可查看/搜索/删除
```

> 封面缓存：`get_album_info()` 会同时下载第一章第一张图并解扰，缓存到 `filesDir/covers/` 作为漫画缩略图；失败时 UI 显示渐变占位图。

## 已知问题 / 注意事项

- **Python 版本**：构建用 Python 3.11，设备上运行 Python 3.8（Chaquopy 内置），直接运行 `.py` 源文件避过字节码兼容问题
- **图片格式**：JM CDN 返回 WebP 图片。Android Pillow 不含 `_webp` 扩展，已使用 Android 原生 `BitmapFactory` 替代
- **图片扰码**：JM 对图片做水平条带扰码，解扰算法已移植到 Android Canvas API
- **curl_cffi**：此库包含不兼容 Android 的原生代码，已在 Python 层通过 MetaPathFinder 屏蔽，自动回退到 `requests`
- **网络**：需要在本机开启代理软件（Clash / v2ray 等），代理端口需为常见端口（7890 / 7897 / 10808 / 10809）

## License

MIT

## Credits

- [JMComic-Crawler-Python](https://github.com/hect0x7/JMComic-Crawler-Python) — Python 漫画爬虫库
- [Chaquopy](https://chaquo.com/chaquopy/) — Android 上的 Python 运行时
- [Pillow](https://python-pillow.org/) — Python 图像处理
