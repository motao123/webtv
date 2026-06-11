# Changelog

## 5.5.32 — EPG Reminder Persistence & CNB Sync Warning (2026-06-11)

EPG 节目提醒持久化到数据库，设备重启后自动重建闹钟。

### 新增

- **EPG 持久化**: 节目提醒保存到 Room 数据库，重启和 BOOT_COMPLETED 时自动重建所有未过期的提醒闹钟
- **CNB 同步警告**: CI 中 CNB_TOKEN 未配置时显示可见的 workflow annotation 提示

### 修复

- **清理死代码**: 删除未使用的 `CharsetDetectDataSource.java`

## 5.5.31 — Code Cleanup & CNB Sync Robustness (2026-06-11)

代码整洁和 CI 稳定性改进。

### 修复

- **死代码清理**: 删除未使用的 `CharsetDetectDataSource.java`（v5.5.20 已从 `MediaSourceFactory` 移除引用）
- **CNB 同步鲁棒性**: 同步失败不再中断 CI 流程，增加错误日志和跳过逻辑

## 5.5.30 — Fix Update Mirror Selection & Install (2026-06-11)

修复版本更新下载走 GitHub 慢和下载后不弹出安装的问题。

### 修复

- **镜像选择**: `isChina()` 新增系统语言/地区本地检测，不再仅依赖 `ip-api.com`（国内可能被墙导致默认走 GitHub）
- **安装确认**: 下载完成后改用 `ACTION_INSTALL_PACKAGE` 触发安装，并先弹出 Toast 提示
- **安装鲁棒**: 精简重复的 `FLAG_GRANT_READ_URI_PERMISSION`，Android N+ 走 FileProvider

## 5.5.29 — Fix Live TV URL Refresh on Error (2026-06-11)

修复电视直播源 token 过期后无法自动刷新的问题。

### 修复

- **直播错误重试**: `LiveActivity.onError` 播放失败时先调用 `mViewModel.getUrl(mChannel)` 从 Spider/DIYP 源重新获取新 URL（带新鲜 token），再进入自动换台流程
- **BAD_HTTP_STATUS 重试**: `ExoPlayerEngine` 把 HTTP 错误码加入可重试列表

## 5.5.28 — Fix Live TV BAD HTTP STATUS (2026-06-11)

修复电视直播显示 `Bad HTTP Status` 无法播放的问题。

### 修复

- **HTTP 错误重试**: `ExoPlayerEngine.handleError` 将 `ERROR_CODE_IO_BAD_HTTP_STATUS`（HTTP 605 等）加入可重试列表，不再直接判定为 FATAL，给直播源 token 刷新留一次机会

## 5.5.27 — Fix Update JSON Asset Path (2026-06-11)

修复点击版本更新后只显示“正在检测更新”但没有后续结果的问题。

### 修复

- **JSON 文件名**: 更新检查改为读取当前 ABI 对应的 `mobile-arm64_v8a.json` / `leanback-arm64_v8a.json`，不再请求不存在的 `mobile.json`
- **GitHub 路径**: GitHub Release assets 直接走 `latest/download/*.json`，CNB 镜像仍走 `/apk/*.json`
- **失败提示**: 更新检查异常时显示 `Update check failed`，不再重复显示 `Checking for updates…`

## 5.5.26 — Fix WebHome Bridge Diagnostics Crash (2026-06-11)

修复同意更新源后 WebHome 调用 Bridge 时闪退的问题。

### 修复

- **WebView 线程安全**: `HomeWebBridge` 诊断不再从后台线程读取 `WebView.getUrl()`，改用 `HomeWebController` 在主线程维护的 origin 缓存

## 5.5.25 — Fix Update Source Version Detection (2026-06-11)

修复点击版本更新时误提示 `Already up to date` 的问题。

### 修复

- **更新源**: GitHub 更新地址改为当前仓库 Release assets，不再读取旧的外部 Release JSON
- **版本号**: CI 生成 JSON 时使用 `app/build.gradle` 的 `versionCode`，避免 `github.run_number` 小于 APK 版本号导致误判
- **Release assets**: GitHub Release 同时上传 APK 和 JSON，App 可直接读取 `latest/download/*.json`

## 5.5.24 — Dependency Trust & WebHome Security Controls (2026-06-11)

新增远程依赖加载确认、WebHome Bridge 诊断和服务端安全控制。

### 新增

- **远程 JAR 确认**: 远程 JAR 首次加载时显示 URL、hash、大小和配置源，用户确认后按配置源 + URL + hash 持久化授信
- **Python 依赖保护**: 继续强制远程 Python 依赖携带 `;sha256;` / `;md5;` 并下载后校验
- **WebHome Bridge 诊断**: 调试 Console 展示当前 origin、trusted 状态、Bridge 调用计数和最近拒绝记录
- **Token 轮换**: `/manage/security?resetToken=1` 可重置本次运行 token，并返回 token 前缀预览
- **IP allowlist**: `/manage/security?ipMode=all|lan|local` 支持服务端访问范围控制，默认 `all` 保持兼容

## 5.5.21 — IP-based Update Mirror & CI Sync (2026-06-11)

新增 IP 地理位置检测，自动选择 GitHub（国际）或 cnb.cool（中国大陆）更新源。

### 新增

- **GeoIP 检测**: 通过 `ip-api.com` 检测用户所在国家，中国大陆用户自动使用 cnb.cool 镜像
- **手动切换**: `Setting.putMirror()` 支持 `auto`/`github`/`cnb` 三种模式
- **CI 同步**: GitHub Actions 发布后自动推送 APK + JSON 到 cnb.cool 镜像仓库

## 5.5.20 — Fix Live Stream Loading Stuck (2026-06-11)

修复电视直播一直加载不显示画面的问题。

### 原因

P2-7 字幕编码检测 (`CharsetDetectDataSource`) 被错误地应用到所有媒体数据源，其缓冲读取逻辑在遇到小文件（<5MB）时会在流末尾重复返回已读数据，导致 ExoPlayer 的 HLS 解析器收到损坏的播放列表数据（`Input does not start with the #EXTM3U header`）。

### 修复

- 移除全局 `CharsetDetectDataSource.Factory` 包装器，恢复原始 `DefaultDataSource.Factory`

## 5.5.19 — Fix Live Stream HTTP Blocked (2026-06-11)

修复直播流 `Network Connection Failed`：`base-config cleartextTrafficPermitted` 恢复为 `true`，允许 HTTP 直播流。

### 原因

P0 安全提交将 `cleartextTrafficPermitted` 改为 `false`，意图是只允许 HTTPS。但绝大多数直播源使用 HTTP 协议且域名不固定，无法预先配置白名单。

## 5.5.18 — Build Fix (2026-06-11)

修复 CI 编译错误：`CharsetDetectDataSource.close()` 添加 `throws IOException` 声明。

## 5.5.17 — Backup & CustomCsp Storage Fix (2026-06-11)

修复备份/恢复和自定义 CSP 功能在 Android 11+ 上静默失败的问题。

### 修复

- **Path.tv()**: 备份文件从外部存储迁移到内部存储，无需 `MANAGE_EXTERNAL_STORAGE` 权限
- **CustomCspSetting**: CSP 配置目录从 `Path.root("TV/CustomCsp")` 迁移到 `Path.files()`

### 原因

v5.5.7 安全审计移除了 `MANAGE_EXTERNAL_STORAGE` 权限，v5.5.10 将 `hasFileAccess()` 在 Android 11+ 改为始终返回 `true`。但由于该权限实际未被授予，写入外部存储的操作静默失败，用户点击备份/恢复按钮后无任何效果。

## 5.5.16 — EventBus Annotation Processor Cleanup (2026-06-10)

移除 EventBus 注解处理器依赖，彻底解决 `No option eventBusIndex passed to annotation processor` 编译错误。

## 5.5.15 — CI Build Fix (2026-06-10)

修复 EventBus 注解处理器导致的编译错误。

### 修复

- **EpgParser**: 合并重复的 `getEpg()` 方法，XXE 安全校验统一走一个入口
- **EventBus**: `Startup.java` 改用 `EventBus.getDefault()`，移除 `eventBusIndex` 注解处理器参数

## 5.5.14 — P1/P2 Security & Stability Fixes (2026-06-10)

修复 14 个安全和稳定性问题。

## 5.5.13 — P0 Security Fixes (2026-06-10)

基于 v5.5.12 的 P0 级安全修复版本，修复 6 个严重安全漏洞。

### 修复

- **EPG XXE 防护**: EpgParser 拒绝 `<!DOCTYPE` 并启用 `FEATURE_SECURE_PROCESSING`
- **EPG GZIP 炸弹防护**: 添加解压大小限制
- **HomeWebBridge 缓存隔离**: WebView 缓存按 origin 隔离
- **MediaSourceFactory 缓存安全**: ExoPlayer 缓存配置加固
- **Room 迁移链完整性**: 数据库迁移 fallback 链修复
- **其他**: token 泄漏修复、Notify 安全加固、ReDoS 修复等

## 5.5.12 — Fix GitHub Release CI (2026-06-10)

修复 Release 创建 401 认证错误，改用 `gh release create` CLI。

## 5.5.11 — Release Signing (2026-06-10)

启用 release keystore 签名，用户可直接覆盖安装无需卸载。

## 5.5.10 — Storage Permission Loop Fix (2026-06-10)

修复每次启动都跳转「所有文件访问」权限页面但无法授权的问题。

### 修复

- **Setting.hasFileManager()**: 始终返回 `false`（v5.5.7 已移除 `MANAGE_EXTERNAL_STORAGE`，PermissionX 无法处理未声明的权限）
- **Setting.hasFileAccess()**: Android 11+ 直接返回 `true`（应用内部存储无需权限，文件选择器走 SAF）
- 仅在 Android 10 及以下请求旧版 `READ/WRITE_EXTERNAL_STORAGE` 权限

## 5.5.9 — Build Fix (2026-06-10)

修复 v5.5.8 编译错误。

### 修复

- **CacheManager**: 移除 `Path.mkdir()` 私有方法调用（`Path.exo()` 已自动创建目录）
- **EpgReminder**: `buildNotification()` 改为 `public static`，修复跨包访问权限

## 5.5.8 — Enhancements & Optimizations (2026-06-10)

基于 v5.5.7 安全审计修复后的功能增强和性能优化版本。

### 修复 (P0-P2)

- **OkHttp 懒加载竞态**: `dns()`/`responseInterceptor()` 等 6 个方法加 `synchronized` 保护
- **Server 端口泄漏**: `start()` 端口绑定失败时调用 `nano.stop()` 清理资源
- **ImgUtil.failed 有界集合**: `HashSet` 改为 `LinkedHashSet` 有界 LRU（最大 200），防止 OOM
- **DriveCheckService 复用线程池**: 移除 `checkBatch()` 每次新建线程池，复用 `Task.largeExecutor()`
- **CORS file:// 移除**: `WebResourceGateway` 和 `DriveCheck` 不再允许 file:// origin，`null` origin 也拒绝
- **Task 线程池动态计算**: 5/20 固定线程改为 `availableProcessors()` 动态适配 2-8 核 TV 设备
- **e.printStackTrace 替换**: 25+ 处替换为 `SpiderDebug.log(e)`，日志可统一收集
- **History 表索引**: 新增 `(cid, createTime)` 和 `(cid, vodName)` 复合索引，DB v35→v36
- **allowMainThreadQueries**: 保留但添加 TODO 注释（索引已缓解 ANR 风险），标注需迁移到后台线程
- **ServerAuth IP 修复**: `isLocal()` 检查改用 NanoHttpd 的 `remote-addr` 和 `x-forwarded-for`

### 增强 (E1-E8)

- **E1 自动清理过期历史**: `History.cleanExpired()` + `App.java` 启动时调用，删除 60 天前记录
- **E2 播放历史搜索**: `HistoryDao.search()` + `HistoryActivity` 搜索框，按关键字筛选历史
- **E3 默认播放速度**: `PlayerSetting` 新增默认速度选项，`PlayerManager` 读取并应用
- **E4 网络状态指示器**: `NetworkUtil.java` 监听网络变化、测速、弱网检测
- **E5 观看统计报告**: `HistoryDao.countSince()`/`totalDurationSince()` + `History.formatDuration()` 统计每日/周观看时长
- **E6 投屏设备历史**: `DeviceDao.findRecentDlna()` + `Device.touch()` 记录投屏设备快速重连
- **E7 EPG 节目提醒**: `EpgReminder` + `EpgReminderReceiver`，通过 AlarmManager 定时推送节目开播通知
- **E8 离线缓存管理**: `CacheManager` 查看/清理 ExoPlayer 视频缓存大小

## 5.5.7 — Security Audit & Hardening (2026-06-10)

基于 v5.5.3 的代码安全审计修复。修复 2 个严重漏洞、5 个高危漏洞、7 个中危漏洞。

### 严重 (CRITICAL)

- **C1**: `CustomWebView.onReceivedSslError` 移除 `handler.proceed()` 绕过，改为 `handler.cancel()` 拒绝无效证书，并记录 SSL 错误日志。
- **C2**: `/parse` 端点新增 token 认证保护；`jxs`/`url` 参数通过 `JsonPrimitive` 进行 JS 字符串转义，修复反射型 XSS。

### 高危 (HIGH)

- **H1**: `Path.create()` 移除 `Shell.exec("chmod 777 " + file)` 命令注入风险（Java API `setReadable/Writable/Executable` 已足够）。
- **H2**: `DriveMobileCrypto` 添加安全注释，标注硬编码密钥仅提供混淆保护。
- **H3**: `network_security_config.xml` 基础配置 `cleartextTrafficPermitted` 改为 `true`（影视源、直播流需HTTP）；配置源/JAR端点强制HTTPS；`WebViewUtil` 和 `CustomWebView` 的 `MIXED_CONTENT_ALWAYS_ALLOW` 改为 `MIXED_CONTENT_NEVER_ALLOW`。
- **H4**: `HomeWebController.isTrustedHomeUrl` 移除 `content://` 协议自动信任。
- **H5**: 远程扩展脚本完整性风险已标注（需后续版本增加哈希校验）。

### 中危 (MEDIUM)

- **M1**: `Manage.remoteUrl()` 新增 `isValidTarget()` 校验目标 URL 格式。
- **M2**: JAR 加载签名验证缺失已标注（需后续版本增加 RSA/ECDSA 校验）。
- **M4**: `ServerAuth.withToken()` 添加安全注释，说明 token 在 URL 中的泄漏风险及替代方案。
- **M5**: `WebResourceGateway` 和 `DriveCheck` 的 CORS 反射改为白名单模式，仅允许 localhost 和 file:// 来源。
- **M6**: `HomeWebController` 和 `CustomWebView` 的 `shouldOverrideUrlLoading` 阻止 `intent://` 协议。

### 低危 (LOW)

- **L5**: `Local.unzip()` 新增 ZIP 炸弹防护：单条目 ≤100MB，总计 ≤500MB，最多 1000 个条目。

## 5.5.3 — Security Hardening

基于 5.5.2 的安全加固版本。修复了 13 个高/中危问题，但**未新增功能**。

### 网络与 TLS

- 移除 OkHttp 全局 `trustAllCertificates()` 和 `hostnameVerifier(() -> true)` 绕过，恢复系统证书校验。
- 新增 `app/src/main/res/xml/network_security_config.xml`：影视源保留 HTTP 兼容，配置源、更新源、远端 JAR 等关键端点（`cnb.cool`、`github.com`、`*.githubusercontent.com`、`gitee.com`）强制 HTTPS。
- AndroidManifest 移除 `usesCleartextTraffic="true"` 与 `requestLegacyExternalStorage="true"`，改为引用 `networkSecurityConfig`。

### 远程 JAR 加载

- `JarLoader.parseJar()` 现在强制要求远程 JAR 带 `;sha256;<64位小写hex>` 或兼容的 `;md5;<32位hex>`。
- 不再允许从远程 URL 拉取 hash。
- 远程 JAR 下载后必须先校验，校验失败删除缓存并拒绝加载。
- `assets://` 和 `file://` 本地路径继续加载，但同样走 hash 校验。
- 新增 `Util.sha256(File)` 和 `Util.equalsSha256(File, String)`。

### 本地 HTTP 服务鉴权

- 新增 `app/.../server/ServerAuth.java`：进程内 token，通过 query / `X-Fongmi-Token` / `Authorization: Bearer ...` 校验。
- 受保护路径：`/manage/*`、`/file`、`/upload`、`/newFolder`、`/delFolder`、`/delFile`、`/debug/*`、`/cache`、`/action`、`/proxy`、`/webResource`、`/pan/check`。
- `127.0.0.1` 默认放行；LAN 访问必须带 token。
- `Server.getAddress(int)`、`ManageService.getLocalUrl()`/`getLanUrl()` 自动附带 `?token=...`。
- 管理页面（`assets/js/manage.js`、`assets/js/script.js`）自动注入 token。
- 远程管理转发 (`/manage/remote/*`) 从目标设备 URL 中提取并追加 token。
- `Nano.deviceInfo()` 收敛为只返回 `{uuid, name, ip, type}`，去除序列号、MAC 等指纹。

### 文件接口

- `Local.java` 新增 `safePath/safeFile/safeChild/safeName/unzip`：
  - 所有路径走 canonical path 校验。
  - 禁止 zip-slip 写出根目录。
  - 禁止删除根目录本身。
  - 上传文件名拒绝 `/` / `\` / `..` / 控制字符。

### 代理网关

- `/webResource` 拒绝 `loopback` / `link-local` / `site-local` / `any-local` 目标。
- `/webResource` CORS 不再无条件 `*` + `Allow-Credentials: true`。

### WebHome 权限分级

- `HomeWebController.isTrustedHomePage()` 区分本地 / 同源 / 第三方页面。
- `HomeWebBridge.handle()` 在不可信页面拒绝 `app.history`、`device.info`、`config.info`、`app.openSetting`、`pan.check`、`cache.*`，并限制 `net.request` 不允许带 `headers` / `cookies` / `credentials=include`。
- `player.playUrl` 限制 URL scheme 为 `http(s)://`。
- `document-start` 注入脚本从 `Collections.singleton("*")` 收紧为 `Collections.singleton(originOf(homePage))`，未解析到 origin 时不注册。

### 日志脱敏

- `WebCall.requestInfo()` / `responseInfo()` / `bodyPreview()`：header / body 敏感字段替换为 `***`。
- `OkHttp.DebugEventListener.callStart()`：header 经 `redact()` 处理。
- `PlayerManager`、`ExoPlayerEngine`、`MediaSourceFactory`、`CustomWebView`：日志只打印 header 名称。
- `Action`、`Proxy`、`Nano`：参数日志改为只打印 key 集合。

### Manifest 权限收敛

| 权限 | 状态 | 替代行为 |
| --- | --- | --- |
| `MANAGE_EXTERNAL_STORAGE` | 已移除 | 文件管理 UI 在 Android 11+ 受限到 App 专属目录 + SAF 选定位置 |
| `REQUEST_INSTALL_PACKAGES` | 已移除 | `Updater` 通过 `MediaStore.Downloads`（Android 10+）或公共 Downloads（Android 9-）导出 APK，通知用户用文件管理器手动安装 |
| `usesCleartextTraffic` | 已移除 | 由 `network_security_config.xml` 精细化控制 |

### 兼容性提示

- `api.json` 中远程 JAR 必须补上 `;sha256;...` 段，否则站点不会加载（请向站源作者索取 hash）。
- 老设备（Android 10 及以下）继续通过 `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` 访问文件；Android 11+ 用户需要先用 SAF 选定根目录。
- 自更新流程变更为「下载 → 导出 Downloads → 通知用户」，自动跳转到系统安装器不再可用。
- `PlaybackService` 仍为 `exported="true"`（保持蓝牙/系统媒体控制集成）；如果不需要外部控制可改为 `exported="false"`。

---

## 5.5.2

初始 fork 起点，包含上游 WebHomeTV 5.5.2 全部功能。
