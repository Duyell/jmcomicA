# JMComic PDF

Android 漫画下载器 — 输入车号，自动下载漫画图片并合成 PDF。

基于 [JMComic-Crawler-Python](https://github.com/hect0x7/JMComic-Crawler-Python) (`jmcomic`)。

## 功能

- 输入 JM 漫画车号（album ID），一键下载所有章节
- 自动处理图片扰码（scrambling），还原正确画面
- 所有图片合并为单个 PDF 文件
- 支持 WebP 格式图片（Android 原生解码）
- 暗色主题 UI（Jetpack Compose + Material 3）

## 技术栈

| 层级 | 技术 |
|------|------|
| UI | Kotlin + Jetpack Compose + Material 3 |
| 状态管理 | ViewModel + StateFlow |
| Python 运行时 | [Chaquopy](https://chaquo.com/chaquopy/) 15.0.1 |
| 爬虫库 | `jmcomic` 2.6.14 |
| 图片解码 | Android BitmapFactory + Pillow |
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
        │   ├── MainActivity.kt   # 入口 Activity + PDF 打开
        │   ├── ui/
        │   │   ├── theme/        # 颜色 / 字体 / Material 3 主题
        │   │   └── screen/
        │   │       └── MainScreen.kt    # 主界面
        │   └── viewmodel/
        │       └── MainViewModel.kt     # 状态管理 + Python 调用
        ├── AndroidManifest.xml
        └── res/
            ├── xml/file_paths.xml       # FileProvider 路径
            └── values/themes.xml        # 原生主题
```

## 构建

### 前置条件

- **JDK 17+**
- **Android SDK 34**（platforms;android-34、build-tools;34.0.0）
- **Python 3.8**（Chaquopy 15.x 要求；3.9-3.11 也可尝试但可能有兼容性问题）
- Android Studio（可选）或仅用命令行

### 步骤

```bash
# 1. 克隆仓库
git clone https://github.com/Duyell/jmcomicA.git
cd jmcomicA

# 2. 创建 local.properties，指向你的 Android SDK
#    内容：sdk.dir=你的SDK路径
#    例如 macOS: sdk.dir=/Users/xxx/Library/Android/sdk
#    例如 Windows: sdk.dir=C\:\\Users\\xxx\\AppData\\Local\\Android\\Sdk

# 3. 修改 app/build.gradle 中的 Python 路径
#    找到 buildPython 并将其改为你的 Python 3.8 路径
#    例如 macOS: buildPython '/usr/local/bin/python3.8'
#    例如 Windows: buildPython 'C:/Python38/python.exe'

# 4. 构建 Debug APK（macOS / Linux）
./gradlew assembleDebug

# Windows
gradlew.bat assembleDebug
```

APK 位于：`app/build/outputs/apk/debug/app-debug.apk`

## 核心流程

```
用户输入车号 → MainViewModel.startDownload()
    ↓ IO 线程
Python → jm_bridge.download_album_as_pdf(album_id, output_dir)
    ↓
1. jmcomic API 下载漫画元数据 + 图片（WebP 格式，已扰码）
2. Android BitmapFactory 解码 WebP
3. Canvas API 解扰（strip reorder）
4. 保存为 JPEG
5. Pillow 合成 PDF
    ↓
ViewModel → Success(pdfPath)
    ↓
MainScreen → 打开 PDF 按钮
    ↓
MainActivity.openPdf() → FileProvider → 系统 PDF 阅读器
```

## 已知问题 / 注意事项

- **Python 版本**：Chaquopy 15.0.1 在设备上使用 Python 3.8，构建时 `buildPython` 最好也指向 3.8，避免字节码不兼容
- **图片格式**：JM CDN 返回 WebP 图片。Android Pillow 不含 `_webp` 扩展，已使用 Android 原生 `BitmapFactory` 替代
- **图片扰码**：JM 对图片做水平条带扰码，解扰算法已移植到 Android Canvas API
- **curl_cffi**：此库包含不兼容 Android 的原生代码，已在 Python 层通过 import hook 屏蔽，自动回退到 `requests`

## License

MIT

## Credits

- [JMComic-Crawler-Python](https://github.com/hect0x7/JMComic-Crawler-Python) — Python 漫画爬虫库
- [Chaquopy](https://chaquo.com/chaquopy/) — Android 上的 Python 运行时
- [Pillow](https://python-pillow.org/) — Python 图像处理
