# Changelog

## v1.2 (2026-07-29)

### 新功能

- **底部导航栏**：首页 + 设置两个 Tab，设置页包含下载管理
- **多章节选择下载**：多章节漫画弹出章节列表，用户可自由勾选要下载的章节，每章生成独立 PDF
- **下载历史管理**：设置页展示所有已下载的 PDF，支持模糊搜索、打开、删除
- **真正的按需下载**：只下载用户选中的章节图片，不再下载整本漫画的全部图片

### 修复

- 多章节漫画不再合并为单个巨大 PDF，每章独立一个 PDF 文件
- 下载进度条现在正确反映选中章节的图片数量（不再显示全书总页数）
- 进度消息从误导性的"获取漫画信息…"改为准确的"下载图片中…"和逐章进度
- jmcomic `get_album_detail` API 调用修复（`hasattr` 被 jmcomic 自定义 `__getattr__` 误判）
- 选择性下载使用 `option.download_photo(photo_id)` 接口

### 架构变更

- `MainScreen.kt` / `MainViewModel.kt` → 拆分为 `HomeScreen` + `SettingsScreen` + 对应 ViewModel
- 新增 `model/` 包：`ChapterInfo`, `DownloadRecord`, UI state 数据类
- 新增 `data/DownloadHistoryManager`：JSON 文件持久化下载记录
- Python 侧新增 `get_album_info()`, `download_selected_chapters()`, 重构公共配置 helper

## v1.1 (2026-07-24)

### 新功能

- **多章节漫画支持**：自动识别漫画内的独立章节（photo），各章节图片分目录存储避免同名覆盖，按章节顺序合成为一个 PDF
- **代理自动探测**：启动时自动扫描本机代理端口（Clash/v2ray 常见端口），无需手动填写代理地址。同时支持模拟器（10.0.2.2）和真机（127.0.0.1）

### 界面优化

- 移除「备用域名」输入框 —— 域名由 App 自动探测，零用户知识
- 移除「代理地址」输入框 —— 代理自动探测，用户只需开启代理软件即可
- 界面精简为一个输入框：「漫画 ID」

### 依赖升级

- jmcomic: 2.6.14 → 2.7.2
- 修复 curl_cffi 与 Android 不兼容问题（MetaPathFinder mock）
- Pillow 版本固定 9.2.0

### 修复

- 多章节漫画图片因同名覆盖导致 PDF 内容错乱
- 中文错误提示（网络连接失败 / 连接超时 / 漫画不存在等）
- gradlew 脚本参数重复传递导致构建失败
- release 构建 Python 3.11 → 3.8 字节码兼容警告

### 已知限制

- 需要在本机开启代理软件（Clash / v2ray 等）才能访问禁漫站点
- 代理端口需为常见端口（7890 / 7897 / 10808 / 10809）
