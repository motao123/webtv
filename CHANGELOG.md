# Changelog

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
