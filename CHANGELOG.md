# Changelog

## v1.3 (2026-07-29)

### 新功能

- **多漫画管理**：PDF 按漫画 ID 分目录存储（`pdf_output/{album_id}/`），支持同时存放多部漫画的下载内容
- **已下载跳过**：再次输入同一漫画 ID 时，章节选择弹窗中已下载的章节显示"已下载"标签并默认取消勾选，避免重复下载
- **漫画分组视图**：设置页改为漫画卡片式分组，每部漫画可展开/折叠查看章节列表
- **删除整部漫画**：支持一键删除某部漫画的全部章节

### 修复

- `_collect_images_from_photo`：`photo.title` 为空时按 `photo_index` 序号匹配目录，不再 fallback 到遍历全部目录导致重复合并
- `cleanupPdfDir`：不再删除所有 PDF，只清理临时下载目录
- Release 构建禁用 R8（`minifyEnabled false`），避免 Chaquopy 代理类被字节码优化破坏

### 架构变更

- `DownloadHistoryManager`：支持嵌套目录扫描 + 漫画分组 + 检查已下载章节
- `ChapterInfo` 新增 `downloaded` 字段
- 新增 `ComicGroup`、`ComicInfo` 数据类
- `SettingsUiState` 新增 `groups`/`filteredGroups`

## v1.2 (2026-07-29)

### 新功能

- **多章节选择下载**：多章节漫画不再自动合并，而是弹出章节列表对话框。用户可自由勾选要下载的章节，每章生成一个独立 PDF。单章节漫画保持原有"一键下载"体验。
- **底部导航栏**：首页（下载）+ 设置（下载管理）两个 Tab，Material 3 风格。
- **下载管理**：设置页展示所有已下载的 PDF 历史，每条显示漫画标题、章节名、下载时间和文件大小。
- **模糊搜索**：设置页顶部搜索框，支持按漫画 ID、标题、章节名模糊匹配。
- **真正的按需下载**：只下载用户选中章节的图片，不再拉取整本漫画的全部图片。进度条数字准确反映选中章节的图片数量。

### 界面变更

- 首页精简为单个漫画 ID 输入框 + 下载按钮。
- 多章节下载完成后展示逐章结果卡片，每章可独立打开 PDF。
- 设置页为空时显示引导文案。

### 技术修复

- 修复 `get_album_info()` 中 `hasattr` 被 jmcomic 自定义 `__getattr__` 误判导致 API 调用失败。
- 选择性下载改用 `option.download_photo(photo_id)` 接口，绕过 downloader factory 不兼容问题。
- `_collect_images_from_photo()` 增加目录匹配 fallback，解决 `get_album_detail` 返回的 photo 对象 `page_arr` 为 `None` 的崩溃。
- 进度消息从误导性的"获取漫画信息…"改为准确的"下载图片中…"和"下载 2/5: 第X話"等逐章进度。

### 架构变更

- `MainScreen.kt` / `MainViewModel.kt` → 拆分为 `HomeScreen` + `SettingsScreen` + 对应 ViewModel。
- 新增 `model/` 包：`ChapterInfo`, `DownloadRecord`, `HomeUiState`, `SettingsUiState`。
- 新增 `data/DownloadHistoryManager`：JSON 文件持久化下载记录。
- Python 侧新增 `get_album_info()`, `download_selected_chapters()`, `_download_selected_photos()`，重构 `_build_proxy_config()`, `_build_option_text()` 等公共 helper。
- 版本号：versionCode 2→3, versionName 1.1→1.2。

### 已知限制

- 需要在本机开启代理软件（Clash / v2ray 等）才能访问禁漫站点。
- 代理端口需为常见端口（7890 / 7897 / 10808 / 10809）。

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
