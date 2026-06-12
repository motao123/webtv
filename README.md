<div align="center">

# WebHomeTV

**为 Android TV / 手机打造的 WebHome 影音客户端**

把传统 TVBox / FongMi / CatVod 生态升级成可以用网页首页驱动的现代影音 App。

[![Release](https://img.shields.io/github/v/release/motao123/webtv?label=release)](https://github.com/motao123/webtv/releases)
[![Android](https://img.shields.io/badge/Android-5.0%2B-3DDC84)](#用户使用指南)
[![TV](https://img.shields.io/badge/Android%20TV-Leanback-4285F4)](#下载-apk)
[![License](https://img.shields.io/badge/Project-WebHomeTV-8A2BE2)](#webhometv)

</div>

---

## 项目简介

`WebHomeTV`（当前仓库名 `webtv`）是围绕 **FongMi / CatVod** 生态能力增强维护的 Android 影音应用。

它保留原有的点播、直播、Spider、解析、投屏、本地 HTTP 服务等能力，同时重点增强：

- **WebHome 自定义首页**：用 HTML / CSS / JavaScript 做自己的影视首页
- **App Native SDK**：网页可调用 App 的搜索、播放、跨域请求、历史、缓存等能力
- **WebHome Bridge 安全分级**：可信 origin 才能使用完整能力
- **站点健康排序**：根据站点可用性自动优化搜索、详情、播放优先级
- **一键同步**：局域网设备间同步配置、站点、历史、收藏和设置
- **家庭过滤**：按标签/关键词屏蔽不适合电视客厅展示的内容
- **安全加固**：Token 鉴权、远程依赖 hash 校验、JAR 首次加载确认、IP 访问控制
- **更新加速**：根据用户网络自动选择最优更新源

简单说：

> 这不是一个普通影视壳子，而是一个可以把 CSP 首页变成“小型网页应用”的 Android 影音客户端。

---

## 核心亮点

| 能力 | 说明 |
| --- | --- |
| WebHome 首页 | 每个 CSP 站点都可以配置独立网页首页 |
| Native SDK | 网页可通过 `window.fm` 调用 App 原生播放、搜索、请求、缓存等能力 |
| 直播 / 点播 | 继续兼容原 FongMi / CatVod 播放体系 |
| Spider 扩展 | 支持 Java / JS / Python Spider 能力 |
| 投屏 / DLNA | 保留投屏和远程播放控制能力 |
| 本地管理页 | 局域网访问 App 管理页面，支持同步、文件、调试等能力 |
| 家庭过滤 | 按标签/关键词屏蔽不适合电视端首页展示的内容 |
| 自动更新 | 多源更新，适配不同网络环境 |
| 安全策略 | 本地服务 token、IP allowlist、远程 JAR 确认、Bridge 权限分级 |

---

## 下载 APK

到 [Releases](https://github.com/motao123/webtv/releases) 下载最新版本：**v5.5.43**。

| 设备类型 | 推荐 APK |
| --- | --- |
| Android TV / 电视盒子，主流新设备 | `leanback-arm64_v8a.apk` |
| Android TV / 老电视盒子 | `leanback-armeabi_v7a.apk` |
| Android 手机，主流新设备 | `mobile-arm64_v8a.apk` |
| Android 老手机 | `mobile-armeabi_v7a.apk` |

> 不确定 CPU 架构时，优先下载 `arm64_v8a`。2018 年后的设备大多都是 arm64。

---

## 安装方式

### 电视盒子 / Android TV

1. 下载 `leanback-*.apk`
2. 放到 U 盘，插入电视盒子
3. 用文件管理器打开 APK
4. 按提示允许“安装未知应用”
5. 完成安装

### 手机

1. 下载 `mobile-*.apk`
2. 直接点击安装
3. 按提示允许浏览器安装未知应用

### ADB 安装

```bash
adb install leanback-arm64_v8a.apk
adb install mobile-arm64_v8a.apk
```

---

## 首次使用

首次启动后需要配置 CSP 站点源，也就是影视内容来源。

可以填写：

- 公开 TVBox / FongMi / CatVod 配置地址
- 自建 `api.json` 地址
- 带 WebHome 首页的自定义配置

配置字段说明见：[应用完整开发文档.md](docs/应用完整开发文档.md)

---

## 更新说明

进入 App：

```text
设置 → 版本检查
```

更新逻辑：

- 没有新版本：提示已是最新版本
- 有新版本：显示更新内容，确认后下载 APK
- 下载完成：调用系统安装器安装
- 安装失败：导出到 Downloads，用户可手动安装

---

## 增强功能

| 功能 | 说明 |
| --- | --- |
| 继续观看 / 收藏 | 强化最近观看、收藏恢复与多设备迁移体验 |
| 继续观看 / 收藏 | 强化继续观看、收藏恢复与配置缺失时的回退路径 |
| 站点健康排序 | 根据站点成功率自动优化搜索、详情、播放排序 |
| 一键同步 | 局域网同步配置、站源、WebHome、搜索记录、历史、收藏和设置 |
| 站点注入 | 添加自定义 WebHome 或通用 CSP 站点，控制启用状态和插入位置 |
| APP 代理 | 配置代理地址和域名规则，改善特定站点网络访问 |
| 调试日志 | 本机和局域网日志入口，方便排查播放、代理、站源、WebHome 问题 |
| WebHome 调试台 | 查看 Console、Network、DOM、Bridge origin、调用计数和拒绝记录 |
| 安全加固 | TLS 校验、远程 JAR 确认、本地服务 token、IP allowlist、Bridge 权限分级 |
| 观看统计 | 基于历史记录统计观看次数和观看时长 |
| 投屏设备历史 | 记录最近投屏设备，方便快速重连 |
| EPG 节目提醒 | 通过系统闹钟推送节目开播提醒 |
| 离线缓存管理 | 查看和清理 ExoPlayer 播放缓存 |

---

## 效果演示

https://github.com/user-attachments/assets/7249b787-a720-406c-8365-acaa0995cb6a

```json
{
  "key": "Nostr",
  "name": "Nostr推荐",
  "type": 3,
  "api": "csp_Nostr",
  "homePage": "https://www.252035.xyz/xs/tvbox/nostr.html"
}
```

---

## 文档入口

完整开发说明见：

[**应用完整开发文档.md**](docs/应用完整开发文档.md)

文档包含：

- App 配置字段
- Spider 开发
- JS / Python Spider 运行时
- 本地 HTTP 服务
- WebHome SDK 参数和返回值
- 透明背景实现建议
- 站点健康排序
- PanSou 集成建议
- Nostr 首页实现要点
- 隐藏功能和使用技巧
- Android Intent、DLNA、MediaSession
- CORS、Cookie 和网络策略

---
## 二开重点

### 1. CSP 站点支持自定义 WebHome 首页

站点配置新增首页字段，切换到该 CSP 站点时可以直接显示自定义网页：

```json
{
  "key": "webhome",
  "name": "WebHome",
  "type": 3,
  "api": "csp_Xxx",
  "homePage": "./nostr.html"
}
```

兼容字段：

- `homePage`
- `home_page`
- `webHome`
- `web_home`

如果配置文件来自在线地址，`./nostr.html` 会按配置文件 URL 做相对路径解析，方便把配置和首页 HTML 放在同一目录。

### 2. WebHome Native SDK

WebHome 页面会注入 `window.fongmi` 和简写 `window.fm`，网页可以直接调用 App 能力。

常用能力包括：

| 能力 | 说明 |
| --- | --- |
| `fm.req(url, options)` | 使用 App 内置 OkHttp 请求接口，绕过浏览器 CORS 限制 |
| `fm.res(url, options)` | 生成本地资源网关地址，给图片、视频、字幕等 DOM 资源使用 |
| `fm.play(url, title, options)` | 播放直链或 `push://` 地址 |
| `fm.vod(siteKey, vodId, title, pic)` | 打开 App 原生 CSP 详情/播放链路 |
| `fm.search(keyword, { direct })` | 调用 App 搜索，支持直接进入搜索结果 |
| `fm.openLive()` / `fm.openKeep()` / `fm.openSetting()` | 打开 App 原生直播、收藏和设置入口 |
| `fm.history()` | 读取最近观看记录 |
| `fm.stat()` | 获取当前播放状态、进度、时长等信息 |
| `fm.ctrl(action)` | 控制播放、暂停、停止、上一集、下一集等 |
| `fm.config()` | 获取当前配置 |
| `fm.site()` | 获取当前站点信息 |
| `fm.device()` | 获取设备信息 |
| `fm.cache` | 提供 WebHome 可用的本地缓存能力 |
| `fm.back()` / `fm.reload()` | 处理网页返回和刷新 |

这套 SDK 的设计目标是让 WebHome 开发者少依赖浏览器私有行为，尽量通过 App 的 Native 能力完成网络、播放和状态管理。

持久化数据建议优先使用 `fm.cache`，不要把账号、页面配置、同步身份等关键数据只放在 `localStorage`。`localStorage` 仍由 Android WebView 提供，并会按页面 origin 保存；但 App 注入 `window.fm` 的时机在页面加载完成后，页面早期脚本应等待 `fmsdk` 事件后再读写 `fm.cache`，或在检测到 `window.fongmiBridge` 但 `window.fm` 尚未就绪时短暂等待，避免误写到浏览器预览 fallback。

#### 不可信页面限制

`HomeWebBridge` 会对非可信 origin 的页面（既不是本地 `file://`/`content://`、也不是 `127.0.0.1`/`localhost`/`::1`、也不是与当前配置源同源）施加如下限制，目的是把 Bridge 暴露给「用户主动配置的 CSP 站首页」，而不是「任意第三方网页」：

| 调用 | 不可信页面 | 备注 |
| --- | --- | --- |
| `fm.device()` | 拒绝 | 避免设备指纹泄露给第三方 |
| `fm.config()` | 拒绝 | 避免暴露当前配置 |
| `fm.history()` | 拒绝 | 避免观看记录外泄 |
| `fm.openSetting()` | 拒绝 | 避免第三方页面劫持设置入口 |
| `fm.cache.get` / `fm.cache.set` / `fm.cache.del` | 拒绝 | 本地缓存仅供可信页面使用 |
| `fm.req` 带 `headers` / `cookies` / `credentials: 'include'` | 拒绝 | 防止携带用户 Cookie 跨域代理 |
| `fm.play(url)` URL 非 `http(s)://` | 拒绝 | 禁止 `file://` / 自定义 scheme 进入播放 |

如果你的 WebHome 来自自有服务器，请把 `api.json` 中站点的 `homePage` URL 与配置源 URL 部署在同一 origin 下，就能自动成为「可信页面」并使用完整 SDK。

`document-start` 注入脚本也从 `*` 收紧到 `originOf(homePage)`，跨域主框架跳转不会继承到原页面的 Bridge 权限。

### 3. CORS 和资源加载增强

普通网页 `fetch()` 会受浏览器 CORS 限制。WebHomeTV 提供两种内置能力：

- `fm.req()`：用于接口请求，返回 JSON、文本、二进制等数据。
- `fm.res()` / `/webResource`：用于图片、视频、字幕、CSS 背景等资源加载。

这可以处理常见跨域、Header、Cookie、资源防盗链等问题。WebHome 页面不需要要求用户安装浏览器插件或关闭系统 WebView 的安全策略。

### 4. 透明背景 WebHome

App WebView 已支持透明背景，WebHome 页面可以让 App 壁纸透出，适合做沉浸式影视首页。

开发时建议：

- `html`、`body` 和主容器保持透明。
- 卡片、按钮、输入框、Tab、弹层使用半透明中性背景。
- 详情页、剧情页等全屏浮层打开时隐藏底层页面，避免多层内容叠在一起。
- 电脑浏览器调试可以保留兜底背景，App 内使用透明背景。

### 5. WebHome 路由、返回、刷新和恢复

WebHome 支持多层网页状态：

- 使用 History API 管理详情页、搜索页、弹层等路由。
- App 返回键会优先让网页内部回退，再退出 WebHome。
- `fm.reload()` 可以刷新当前 WebHome，而不要求用户重启 App。
- App 从后台或锁屏恢复时会派发 `fmresume` 事件，网页可以保留当前页面状态并补偿刷新数据。
- 正常冷启动应默认回到 WebHome 主页；详情页、弹层等 UI 快照只建议用于后台恢复或 App 明确带 `_fm_restore=1` 的 WebView 进程恢复场景。

电视端 WebHome 要按遥控器模型单独设计焦点：默认焦点不要放在文本框；搜索建议、状态面板等打开后要限制方向键在当前区域内；文本框默认 `readonly`，只有 OK/点击后进入编辑态；动态列表刷新要恢复原焦点和滚动位置。完整经验见 [应用完整开发文档.md](docs/应用完整开发文档.md) 的“电视端遥控器 UX 最佳实践”。

### 6. 调试日志

调试日志入口也在“增强功能”页中，默认关闭。开启后，App 会记录 WebHome SDK 调用、`fm.req`/资源网关、本地 HTTP 服务、爬虫请求、push/pvideo 和播放状态等链路日志。

行为说明：

- 关闭调试日志时不弹 toast，并自动清空当前进程内日志。
- 日志保存在当前 App 进程内，不限制 2000 条；关闭或进程结束后不保留。
- 开启后会打开 `/debug/logs` 页面，可刷新、下载、清空，也可以通过同局域网地址查看。

### 6. PanSou 集成示例

`demo/nostr-合并.html` 的详情页集成了 PanSou 类搜索能力，支持：

- 自定义盘搜服务地址。
- 账号密码认证。
- 自定义 TG 频道。
- 按资源类型分 Tab 展示。
- 结果异步补充和合并。

PanSou 搜索结果可能是异步补充的，示例页会轮询合并新增结果。

### 7. Nostr + TMDB 推荐首页示例

`demo/nostr-合并.html` 是一个完整的 WebHome 首页示例，不只是 SDK demo。它包含：

- TMDB 今日趋势、电影、剧集、动画等榜单。
- 中国大陆内容优先的推荐分区。
- 瀑布流卡片布局，移动端一行 3 个，宽屏自动显示更多列。
- Nostr 去中心化偏好同步。
- 用户搜索、点击、播放时长等行为可参与推荐计算。
- 每个用户对同一影视条目的热度去重，避免重复点击无限累加。
- 状态面板展示 SDK、TMDB、Nostr、PanSou、发布状态和身份信息。
- 支持清理本机测试数据和发布 Nostr 删除事件。

示例页使用 TMDB API，请自行替换或管理 API Key，并遵守对应服务条款。

### 9. App 行为调整

- 启动 App 不再自动弹出版本更新窗口。
- 用户仍可在设置页手动点击版本检查。
- 设置页新增“增强功能”入口，手机端和电视端都是独立设置页，集中放置站点健康排序、管理页面、站点注入、Proxy、一键同步和调试日志。
- 手机端和电视端都保留原有 FongMi/CatVod 能力。
- WebHome 能力优先面向手机端体验，同时兼顾电视遥控器焦点和返回操作。
- 自更新行为已调整：检测到新版本后，APK 通过 `MediaStore.Downloads` 导出到系统「下载」目录，并通过 toast 提示用户「用文件管理器打开安装」。这样 App 不再请求 `REQUEST_INSTALL_PACKAGES` 权限，避免 Play 政策与用户信任风险。
- 文件管理 UI 在 Android 11+ 不再申请「所有文件访问」权限（`MANAGE_EXTERNAL_STORAGE` 已移除），默认只能在 App 专属目录或用户通过 SAF 选定的位置内操作。低于 Android 11 的设备维持原行为。

## Demo

仓库主要维护两个 WebHome 相关示例：

| 文件 | 说明 |
| --- | --- |
| `demo/nostr-合并.html` | 正式推荐首页示例，集成 TMDB、Nostr、PanSou、透明背景 |

配置示例：

```json
{
  "sites": [
    {
      "key": "webhome_demo",
      "name": "WebHome 推荐",
      "type": 3,
      "api": "csp_Demo",
      "homePage": "./nostr-合并.html"
    }
  ]
}
```

如果你的配置文件和示例 HTML 放在同一个服务器目录，`homePage` 可以直接写相对路径。

## 安全加固

本仓库相对上游做了一轮系统安全加固，覆盖网络层、动态加载、本地服务、WebHome Bridge 和 Manifest 权限。改动按「修复前风险 → 修复后行为」整理如下：

### 网络与 TLS

- 全局恢复系统证书校验，移除 `trustAllCertificates()` 和 `hostnameVerifier(() -> true)` 绕过。
- 新增 `network_security_config.xml`：影视源（直播流、点播流）继续允许 `http` 明文通信，但配置源、更新源、远端 JAR 等关键端点（`github.com`、`*.githubusercontent.com`）强制 HTTPS。
- Manifest 移除 `usesCleartextTraffic="true"` 与 `requestLegacyExternalStorage="true"`，改为引用 `networkSecurityConfig`。

### 远程 JAR 加载

`api.json` 中 `csp_*` 的 JAR 字段必须携带完整性校验 hash，否则远程 JAR 会被拒绝加载并删除缓存文件：

```text
assets://xxx.jar;sha256;<64位小写hex>
https://example.com/spider.jar;sha256;<64位小写hex>
https://example.com/spider.jar;md5;<32位hex>           # 兼容旧源
```

变更点：
- 不再允许 `md5=<http URL>` 这种「从远程 URL 取 hash」的反模式。
- 远程 JAR 下载后必须先校验，失败即拒绝加载。
- `assets://` 和 `file://` 本地路径可继续加载，但同样走 hash 校验（hash 留空仅用于本地可信包）。

### 本地 HTTP 服务鉴权

本地服务（默认 127.0.0.1，外部可见时附带 LAN 地址）启动时生成进程内 `token`，通过 query 参数、`X-Fongmi-Token` header 或 `Authorization: Bearer ...` 校验。

受保护路径：`/manage/*`、`/file`、`/upload`、`/newFolder`、`/delFolder`、`/delFile`、`/debug/*`、`/cache`、`/action`、`/proxy`、`/webResource`。

- 来自 `127.0.0.1` 的请求默认放行（App 内部播放/代理必需）。
- 来自 LAN 的请求必须带 token，否则返回 `401 Unauthorized`。
- `Server.getAddress(int)`、`ManageService.getLocalUrl()`、`ManageService.getLanUrl()` 自动给管理页 URL 附加 `?token=...`。
- 管理页面（`assets/js/manage.js`、`assets/js/script.js`）的 fetch/AJAX 已统一注入 token。
- 远程管理转发 (`/manage/remote/*`) 会自动从目标设备 URL 中提取并追加 token，避免加固后跨设备管理失效。

### 文件接口与代理网关

- `Local.java` 新增 `safePath/safeFile/safeChild/safeName/unzip`，所有路径走 canonical path 校验，禁绝 zip-slip，禁止删除根目录。
- `/webResource` 拒绝 `loopback` / `link-local` / `site-local` / `any-local` 目标，不再充当内网代理。
- `/webResource` 的 CORS 不再无条件 `*` + `Allow-Credentials: true`。
- `/device` 收敛为只返回 `uuid`、`name`、`ip`、`type` 四个字段，去掉序列号、MAC 等敏感指纹。

### WebHome 权限分级

`HomeWebController.isTrustedHomePage()` 区分：
- 可信：本地 `file://`、`content://`、`127.0.0.1`/`localhost`/`::1`、与当前配置源同源。
- 不可信：其它第三方 `http(s)://` 页面。

`HomeWebBridge.handle()` 在不可信页面下会拒绝以下调用：

| 方法 | 不可信页面 |
| --- | --- |
| `app.history()` | 拒绝 |
| `device.info` / `device()` | 拒绝 |
| `config.info` / `config()` | 拒绝 |
| `app.openSetting()` | 拒绝 |
| `cache.get` / `cache.set` / `cache.del` | 拒绝 |
| `net.request` 带 `headers` / `cookies` / `credentials=include` | 拒绝 |
| `player.playUrl` 非 `http(s)://` 协议 | 拒绝 |

`document-start` 注入脚本从 `Collections.singleton("*")` 收紧为 `Collections.singleton(originOf(homePage))`，未解析到 origin 时不注册。

### 日志脱敏

`WebCall`、`OkHttp.DebugEventListener`、`PlayerManager`、`ExoPlayerEngine`、`MediaSourceFactory`、`CustomWebView`、`Action`、`Proxy`、`Nano` 的日志输出不再打印完整 header 值，统一改为：
- 仅打印 header 名称（`headerKeys=...`）
- 或经 `redact()` 把 `Authorization` / `Cookie` / `Set-Cookie` / `X-Token` / `token` / `password` / `passwd` / `secret` 替换为 `***`
- 参数日志改为只打印 key 集合，避免 URL query 里的 token 泄露

### Manifest 权限收敛

| 权限 | 状态 | 替代行为 |
| --- | --- | --- |
| `MANAGE_EXTERNAL_STORAGE` | 已移除 | 文件管理 UI 在 Android 11+ 受限到 App 专属目录 + SAF 选定的位置 |
| `REQUEST_INSTALL_PACKAGES` | 已移除 | `Updater` 下载 APK 后通过 `MediaStore.Downloads` 写入公共 Downloads 目录，通知用户用文件管理器手动安装 |
| `usesCleartextTraffic` | 已移除 | 由 `network_security_config.xml` 精细化控制 |

### `PlaybackService`

仍然 `exported="true"`，因为它实现 `MediaLibraryService` 协议（蓝牙、Auto、媒体控制按钮等需要外部绑定）。如果你的应用场景不依赖外部媒体控制，建议改为 `exported="false"` 并在 `MediaSession` 中限制可用的 `SessionCommand`。

### 安全审计修复（2024-06）

以下问题在安全审计中发现并已修复：

| 严重级别 | 问题 | 修复 |
| --- | --- | --- |
| **严重** | `ServerAuth.remoteIp()` 从 `session.getHeaders()` 读取 `remote-addr`，但 NanoHTTPD 实际将客户端 IP 存放在 `session.getParms()` 的 `NanoHttpd.RemoteAddress` 键中，导致所有请求被误判为本地请求，token 认证被完全绕过 | 改为从 `session.getParms().get("NanoHttpd.RemoteAddress")` 获取 IP，fallback 到 `x-forwarded-for`；`isLocal()` 空值时改为 fail-secure（拒绝） |
| **警告** | `WebResourceGateway` 和 `DriveCheck` 的 CORS 策略允许 `"null"` Origin，沙箱化 iframe 可通过此漏洞跨域读取代理资源 | 移除对 `"null"` origin 的允许，返回 `false` |
| **警告** | `SyncFiles.restoreArchive()` 处理同步归档时缺少 ZIP 炸弹防护（条目数、单条目大小、总大小限制），而 `Local.unzip()` 已有三重防护 | 添加相同限制：最大 1000 条目、单条目 100MB、总计 500MB |



环境要求：

- JDK 17
- Android SDK 和 Android Gradle Plugin 所需 build tools
- Gradle Wrapper 使用仓库内置 `gradlew`

仓库已经包含完整打包所需的 `app/libs/*.aar` 和 Media3 本地 Maven 产物，clone 后可以直接执行 Gradle 构建命令。

### 直接 clone 并打包

```bash
git clone https://github.com/motao123/webtv.git
cd webtv
git switch main
```

手机端和电视端常用构建命令：

```bash
bash gradlew :app:assembleMobileArm64_v8aRelease
bash gradlew :app:assembleMobileArmeabi_v7aRelease
bash gradlew :app:assembleLeanbackArm64_v8aRelease
bash gradlew :app:assembleLeanbackArmeabi_v7aRelease
```

APK 输出路径以 Gradle 实际输出为准，常见路径：

```text
Release/apk/mobile-arm64_v8a.apk
Release/apk/mobile-armeabi_v7a.apk
Release/apk/leanback-arm64_v8a.apk
Release/apk/leanback-armeabi_v7a.apk
app/build/outputs/apk/mobileArm64_v8a/release/mobile-arm64_v8a.apk
app/build/outputs/apk/leanbackArm64_v8a/release/leanback-arm64_v8a.apk
```

默认不需要配置签名文件。没有 `local.properties` 时，release 包会使用 debug signing 兜底，方便 clone 后直接打包测试。

如果需要使用自己的正式签名，在根目录创建 `local.properties`：

```properties
storeFile=/path/to/keystore.jks
keyAlias=your_alias
storePassword=your_password
```

## 配置源维护：补 JAR 哈希

5.5.3 起，远程 `jar` 字段必须带 `;sha256;<64位小写hex>` 才能加载（详见上文 [安全加固 → 远程 JAR 加载](#远程-jar-加载)）。很多老 TVBox 配置源还在用裸 `./spider.jar`，装进 5.5.3 之后会出现「首页空、搜索报错」。

仓库自带 `tools/patch_jar_hashes.py` 自动给配置源补 SHA-256：

```bash
# 1. 看看会处理哪些 jar
python tools/patch_jar_hashes.py <config_url> --dry-run

# 2. 真的下载并算哈希，输出修补版到 patched.json
python tools/patch_jar_hashes.py <config_url> -o patched.json

# 3. 把 patched.json 上传到自己的服务器（或 GitHub Gist / Pages），
#    然后在 App 设置里把配置地址改成那份新的 URL
```

工具会：

- 按 RFC 3986 把 `./spider.jar`、`../lib/x.jar` 解析成绝对 URL
- 跳过 `assets://` / `file://` 本地 jar
- 跳过作者已经带 `;sha256;` / `;md5;` 的 jar
- 把 SHA-256 拼到 jar 字段末尾，其他字段不动
- `report` 写到 stderr，明确列出每个 jar 的状态 / 哈希

仅 stdlib（`urllib` / `hashlib` / `json`），Python 3.7+ 直接跑。

## 当前播放层依赖

- `app/libs/*.aar`：内置 Hook、TVBus、Thunder、ForceTech、JianPian 等播放能力依赖。
- `third_party/maven`：已生成的 `androidx.media3:*:1.10.1-fongmi` 本地 Maven 产物。
- `third_party/media-lock.json`：记录 Media3 锁定版本，后续升级 Media3 时使用。
- `nextlib-media3ext` 使用 `io.github.anilbeesetti:nextlib-media3ext:1.10.0-0.12.1`，提供 FFmpeg renderer。

## 目录结构

```text
app/       Android 主应用
catvod/    CatVod 抽象层、Spider 接口、网络和代理工具
quickjs/   JavaScript Spider 运行时
chaquo/    Python Spider 运行时
demo/      WebHome 示例页面
docs/      完整开发文档
other/     其它构建或依赖模块
```

## 开源说明

本仓库只提供技术实现和开发示例，不内置、不维护、不分发任何影视内容、播放源或资源站接口。项目中的搜索、播放、TMDB、Nostr、PanSou 等能力都需要用户自行配置合法服务和数据来源。
