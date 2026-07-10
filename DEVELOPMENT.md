# JMComic PDF - Android App 开发文档

## 项目概述

基于 [JMComic-Crawler-Python](https://github.com/hect0x7/JMComic-Crawler-Python) 开发的 Android 应用，用户输入禁漫车号即可自动下载漫画图片并合并为 PDF。

## 技术栈

| 层级 | 技术 |
|------|------|
| UI | Kotlin + Jetpack Compose + Material 3 |
| 状态管理 | ViewModel + StateFlow |
| Python 集成 | Chaquopy 15.0.1 |
| 爬虫库 | jmcomic 2.6.14 |
| PDF 生成 | Pillow（纯 Python，无原生扩展依赖） |
| 最低 SDK | Android 8.0 (API 26) |
| 目标 SDK | Android 14 (API 34) |
| AGP | 8.2.2 |
| Kotlin | 1.9.22 |
| Gradle | 8.9 |

## 项目结构

`
android/
├── build.gradle.kts                 # 根构建配置（AGP 8.2.2 + Kotlin 1.9.22 + Chaquopy 15.0.1）
├── settings.gradle.kts              # 模块设置 + Chaquopy Maven 仓库
├── gradle.properties                # Gradle JVM 参数
├── gradlew.bat                      # Gradle Wrapper 脚本
├── DEVELOPMENT.md                   # 本文档
├── gradle/wrapper/
│   ├── gradle-wrapper.jar
│   └── gradle-wrapper.properties    # Gradle 8.9
└── app/
    ├── build.gradle                 # 应用构建配置（Groovy DSL）
    ├── proguard-rules.pro           # 混淆规则
    └── src/main/
        ├── AndroidManifest.xml      # 权限、Activity、FileProvider
        ├── python/
        │   └── jm_bridge.py         # Python 桥接脚本（下载 + Pillow PDF 合成）
        ├── java/com/jmcomic/pdfapp/
        │   ├── MainActivity.kt      # 入口 Activity + PDF 打开
        │   ├── ui/
        │   │   ├── theme/
        │   │   │   ├── Color.kt     # 深色主题颜色
        │   │   │   ├── Type.kt      # 字体样式
        │   │   │   └── Theme.kt     # Material 3 主题
        │   │   └── screen/
        │   │       └── MainScreen.kt # 主界面（Idle/Downloading/Success/Error 状态）
        │   └── viewmodel/
        │       └── MainViewModel.kt  # 状态管理 + Python 调用
        └── res/
            ├── xml/file_paths.xml    # FileProvider 路径配置
            └── values/themes.xml     # Android 原生主题
`

## 核心流程

`
用户输入车号 → MainViewModel.startDownload()
    ↓ IO 线程
Python.getInstance().getModule("jm_bridge").callAttr("get_pdf_path", id, outputDir)
    ↓
jm_bridge.py:
    1. jmcomic.download_album(id, option) → 下载所有图片
    2. Pillow Image.open + save(pdf) → 合并为 PDF
    3. 返回 PDF 文件路径
    ↓
ViewModel 更新为 Success + pdfPath
    ↓
MainScreen → 打开 PDF 按钮
    ↓
MainActivity.openPdf() → FileProvider → 系统 PDF 阅读器
`

## 构建说明

### 前置条件

- JDK 17+
- Android SDK 34（platform-tools、platforms;android-34、uild-tools;34.0.0）
- Python 3.11（Chaquopy 15.x 要求 Python 3.8-3.11）
- Android Studio（可选，用于打开项目）或仅使用命令行

### 命令行构建

`powershell
 = "D:\Tools\android-sdk"
D:\Tools\Java\jdk-21 = "D:\Tools\Java\jdk-21"
C:\Users\53473\.codex\tmp\arg0\codex-arg0iXmBhu;D:\OpenSSH-Win64\OpenSSH-Win64;D:\Tools\Python;C:\Program Files (x86)\Common Files\Intel\Shared Libraries\redist\intel64\compiler;D:\Tools\;D:\Tools\Java\jdk-21\bin;C:\WINDOWS\system32;C:\WINDOWS;C:\WINDOWS\System32\Wbem;C:\WINDOWS\System32\WindowsPowerShell\v1.0\;C:\WINDOWS\System32\OpenSSH\;C:\Program Files\dotnet\;C:\Program Files\Microsoft SQL Server\130\Tools\Binn\;D:\Tools\Python\Scripts;D:\mysql-8.4.7-winx64\mysql-8.4.7-winx64\bin;D:\mysql-8.4.7-winx64\mysql-8.4.7-winx64\bin;C;\Program Files (x86)\Incredibuild;C:\Program Files (x86)\Windows Kits\10\Windows Performance Toolkit\;C:\Program Files\Calibre2\;C:\Program Files\Microsoft SQL Server\150\Tools\Binn\;D:\Bandizip;D:\Tools\Git\cmd;C:\Program Files\Go\bin;D:\Tools\Git LFS;D:\Tools\nvm;C:\nvm4w\nodejs;C:\Program Files\Go\bin;C:\Users\53473\go\bin;D:\Docker\App\resources\bin;D:\Erlang OTP\bin;D:\RabbitMQ\rabbitmq_server-4.3.1\sbin;D:\Tools\uv;d:\Cursor\cursor\resources\app\codeBin;D:\Tools\Python3.11\Scripts\;D:\Tools\Python3.11\;C:\Program Files\MySQL\MySQL Shell 8.0\bin\;C:\Windows\System64;C:\Users\53473\AppData\Local\Microsoft\WindowsApps;C:\Program Files\JetBrains\PyCharm Community Edition 2024.3\bin;C:\Tool\CLion\CLion 2024.1.2\bin;D:\Tools\Java\Intellij_idea\IntelliJ IDEA Community Edition 2024.3\bin;C:\Users\53473\AppData\Local\Programs\Ollama;%MindStudio%;C:\Users\53473\AppData\Local\Programs\Microsoft VS Code\bin;D:\Tools\SysGCC\bin;D:\work\Year3CSFM\Experiment\win32-buildtools-1.2\win32-buildtools-1.2\bin;C:\SysGCC\risc-v\bin;D:\Tools\Java\jdk-13\bin;D:\Tools\Java\jdk-23\bin;D:\Tools\Java\jdk-25\bin;D:\work\ApMaven\Bin\apache-maven-3.9.11\bin;D:\JetBrains\DataGrip 2025.3.1\bin;D:\JetBrains\IntelliJ IDEA 2025.1.1.1\bin;C:\Users\53473\AppData\Roaming\npm;C:\Program Files\JetBrains\PyCharm 2025.3.1.1\bin;D:\work\mysy\mingw64\bin;D:\Tools\nvm\npm_global;D:\Cursor\cursor\resources\app\bin;D:\Tools\protoc-34.1-win64\bin;C:\Users\53473\AppData\Local\Microsoft\WinGet\Packages\BurntSushi.ripgrep.MSVC_Microsoft.Winget.Source_8wekyb3d8bbwe\ripgrep-15.1.0-x86_64-pc-windows-msvc;C:\Users\53473\AppData\Local\Microsoft\WinGet\Packages\OpenAI.Codex_Microsoft.Winget.Source_8wekyb3d8bbwe;C:\Users\53473\AppData\Local\OpenAI\Codex\bin\ada252862d154cdd;C:\Program Files\WindowsApps\OpenAI.Codex_26.608.1337.0_x64__2p2nqsd0c76g0\app\resources = "D:\Tools\Java\jdk-21\bin;\platform-tools;C:\Users\53473\.codex\tmp\arg0\codex-arg0iXmBhu;D:\OpenSSH-Win64\OpenSSH-Win64;D:\Tools\Python;C:\Program Files (x86)\Common Files\Intel\Shared Libraries\redist\intel64\compiler;D:\Tools\;D:\Tools\Java\jdk-21\bin;C:\WINDOWS\system32;C:\WINDOWS;C:\WINDOWS\System32\Wbem;C:\WINDOWS\System32\WindowsPowerShell\v1.0\;C:\WINDOWS\System32\OpenSSH\;C:\Program Files\dotnet\;C:\Program Files\Microsoft SQL Server\130\Tools\Binn\;D:\Tools\Python\Scripts;D:\mysql-8.4.7-winx64\mysql-8.4.7-winx64\bin;D:\mysql-8.4.7-winx64\mysql-8.4.7-winx64\bin;C;\Program Files (x86)\Incredibuild;C:\Program Files (x86)\Windows Kits\10\Windows Performance Toolkit\;C:\Program Files\Calibre2\;C:\Program Files\Microsoft SQL Server\150\Tools\Binn\;D:\Bandizip;D:\Tools\Git\cmd;C:\Program Files\Go\bin;D:\Tools\Git LFS;D:\Tools\nvm;C:\nvm4w\nodejs;C:\Program Files\Go\bin;C:\Users\53473\go\bin;D:\Docker\App\resources\bin;D:\Erlang OTP\bin;D:\RabbitMQ\rabbitmq_server-4.3.1\sbin;D:\Tools\uv;d:\Cursor\cursor\resources\app\codeBin;D:\Tools\Python3.11\Scripts\;D:\Tools\Python3.11\;C:\Program Files\MySQL\MySQL Shell 8.0\bin\;C:\Windows\System64;C:\Users\53473\AppData\Local\Microsoft\WindowsApps;C:\Program Files\JetBrains\PyCharm Community Edition 2024.3\bin;C:\Tool\CLion\CLion 2024.1.2\bin;D:\Tools\Java\Intellij_idea\IntelliJ IDEA Community Edition 2024.3\bin;C:\Users\53473\AppData\Local\Programs\Ollama;%MindStudio%;C:\Users\53473\AppData\Local\Programs\Microsoft VS Code\bin;D:\Tools\SysGCC\bin;D:\work\Year3CSFM\Experiment\win32-buildtools-1.2\win32-buildtools-1.2\bin;C:\SysGCC\risc-v\bin;D:\Tools\Java\jdk-13\bin;D:\Tools\Java\jdk-23\bin;D:\Tools\Java\jdk-25\bin;D:\work\ApMaven\Bin\apache-maven-3.9.11\bin;D:\JetBrains\DataGrip 2025.3.1\bin;D:\JetBrains\IntelliJ IDEA 2025.1.1.1\bin;C:\Users\53473\AppData\Roaming\npm;C:\Program Files\JetBrains\PyCharm 2025.3.1.1\bin;D:\work\mysy\mingw64\bin;D:\Tools\nvm\npm_global;D:\Cursor\cursor\resources\app\bin;D:\Tools\protoc-34.1-win64\bin;C:\Users\53473\AppData\Local\Microsoft\WinGet\Packages\BurntSushi.ripgrep.MSVC_Microsoft.Winget.Source_8wekyb3d8bbwe\ripgrep-15.1.0-x86_64-pc-windows-msvc;C:\Users\53473\AppData\Local\Microsoft\WinGet\Packages\OpenAI.Codex_Microsoft.Winget.Source_8wekyb3d8bbwe;C:\Users\53473\AppData\Local\OpenAI\Codex\bin\ada252862d154cdd;C:\Program Files\WindowsApps\OpenAI.Codex_26.608.1337.0_x64__2p2nqsd0c76g0\app\resources"
cd android
gradle assembleDebug --no-daemon
`

### APK 位置

ndroid/app/build/outputs/apk/debug/app-debug.apk

- Debug APK 大小：约 43 MB

### 关键依赖版本

`
jmcomic==2.6.14
lxml==4.6.3（Chaquopy Android 预编译 wheel）
Pillow（Chaquopy Android 预编译 wheel）
pycryptodome（Chaquopy Android 预编译 wheel）
curl-cffi（Chaquopy 提供的 wheel）
pyyaml（纯 Python 构建为 wheel）
commonx==0.6.40
`

## 已知问题与解决

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| lxml 编译失败 | 新版本无 Android wheel | 锁定 lxml==4.6.3，Chaquopy pypi 有 cp38 arm64 预编译版本 |
| pikepdf 编译失败 | 需要 libqpdf C 库 | 用 Pillow 替代 img2pdf 进行 PDF 合成 |
| cgi 模块缺失 | Python 3.13 移除了 cgi | 使用 Python 3.11 |
| Chaquopy DSL 不解析 | Kotlin DSL + Chaquopy 兼容性 | 切换到 Groovy DSL (uild.gradle) |
| SSL 证书错误 | 代理 MITM 拦截 | 关闭 Gradle JVM 代理，直连 |
| Gradle Wrapper 下载失败 | 网络问题 | 手动下载 gradle-wrapper.jar 和 gradle-8.9-bin.zip |

## 后续迭代方向

- [ ] 搜索功能
- [ ] 下载历史记录
- [ ] 批量下载支持
- [ ] 内嵌 PDF 阅读器
- [ ] 登录支持（需要账号密码的下载场景）
- [ ] 暗色/亮色主题切换
- [ ] 下载进度显示优化