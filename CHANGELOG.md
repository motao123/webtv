# Changelog

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
