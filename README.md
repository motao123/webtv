# WebHomeTV

WebHomeTV 是基于 FongMi / CatVod 生态二次开发的 Android 影音应用，保留原有点播、直播、Spider、解析、投屏、本地 HTTP 服务等能力，并重点增强了 **WebHome 自定义首页**、**App Native SDK**、**网盘链接检测**、**站点健康排序** 和 **Nostr/TMDB 推荐首页**。

这个项目的核心目标不是替换 CSP/Spider 体系，而是让 CSP 站点首页可以变成一个真正可开发的网页应用：开发者可以用 HTML/CSS/JavaScript 定制首页，再通过 App 暴露的 Native 能力完成搜索、播放、跨域请求、资源代理、最近观看、网盘检测和状态同步。

## 用户使用指南

本应用是 **Android 客户端**，装到电视盒子或手机上使用，不在服务器上跑。

### 一、下载 APK

到 [Releases](https://github.com/motao123/webtv/releases) 下载最新版本（当前 v5.5.7 - 安全加固版），按你的设备类型选：

| 你的设备 | 下载哪个 |
|---|---|
| 电视盒子 / Android TV（绝大多数） | `leanback-arm64_v8a.apk` |
| 老款电视盒子（2018 年前） | `leanback-armeabi_v7a.apk` |
| 手机（推荐） | `mobile-arm64_v8a.apk` |
| 老手机 | `mobile-armeabi_v7a.apk` |

> arm64 是目前主流，**不确定就下 arm64**。

### 二、安装到设备

#### 方法 1：U 盘（电视盒子最常用）

1. 把下载好的 APK 放到 U 盘根目录
2. U 盘插到电视盒子 USB 口
3. 用电视盒子自带文件管理器（或装个 ES 文件浏览器 / X-plore）打开 U 盘
4. 点 APK → 「安装」
5. 第一次会提示授权「未知来源」，按提示开启

#### 方法 2：手机直接装

手机浏览器下载 APK 后点开安装，提示「未知来源」时按提示授权即可。

#### 方法 3：电脑 ADB 推送

```bash
adb install leanback-arm64_v8a.apk        # 电视盒子
adb install mobile-arm64_v8a.apk         # 手机
```

#### 方法 4：设备自带浏览器

在电视盒子/手机的浏览器里直接打开 release 链接下载，省去拷贝步骤。

### 三、首次打开

启动后 App 会让你**配置 CSP 站点源**（也就是你想看的影视内容来自哪），可以填：

- 公开的 TVBox CSP 配置地址（自行搜索）
- 自建的 `api.json` 链接

详细字段说明见 [应用完整开发文档.md](docs/应用完整开发文档.md)。

### 四、检查更新

- 进入 App → 设置 → 「版本检查」手动触发
- v5.5.3 起**移除了启动时自动弹窗**，需要主动点

### 五、常见问题

**装不上，提示「禁止安装」**
→ 设置 → 安全 → 允许「未知来源」/「安装未知应用」

**装到电视上遥控器不好用 / 不能进详情**
→ 装错 APK 了。电视上要装 `leanback-...`，手机 APK 装到电视上会出现这种问题，卸载重装 leanback 版。

**打开闪退**
→ 检查 Android 版本（要求 5.0+）；或换 `armeabi_v7a` 版本试试（少数 arm64 设备兼容有问题）。

**不知道选 arm64 还是 armv7**
→ 2018 年后出的设备基本都是 arm64，先下 arm64。

**想卸载干净**
→ 设置 → 应用 → WebHomeTV → 卸载。

### 增强功能

- **网盘检测**：对网盘相关能力进行可用性检测，帮助确认当前环境是否支持网盘播放或解析。
- **站点健康排序**：自动学习站点搜索、详情和播放成功率，搜索与换源优先使用更可用的站点；站点弹窗默认保留用户配置顺序，可在弹窗内单独开启健康排序。
- **一键同步**：支持在同一局域网设备间同步配置、站源数据、WebHome 数据、搜索记录、观看历史、收藏和应用设置。
- **站点注入**：支持添加自定义 WebHome 或通用 CSP 站点，并可配置启用状态、插入位置、首页、搜索和换源等行为。
- **APP代理**：支持配置代理地址和域名匹配规则，可按当前站点自动建议代理域名，并用于改善特定站点、接口或播放链路的网络访问。
- **调试日志**：提供本机和局域网日志查看入口，便于排查播放、代理、站源和 WebHome 相关问题。
- **安全加固**：恢复 TLS 证书校验、远程 JAR 强制哈希、本地服务 token 鉴权、文件接口路径校验、WebHome Bridge 权限分级，详见 [安全加固](#安全加固) 章节。

## 效果演示

https://github.com/user-attachments/assets/7249b787-a720-406c-8365-acaa0995cb6a

```
{
  "key": "Nostr",
  "name": "Nostr推荐",
  "type": 3,
  "api": "csp_Nostr",
  "homePage": "https://www.252035.xyz/xs/tvbox/nostr.html"
}
```

## 文档

完整开发说明见：

[**应用完整开发文档.md**](docs/应用完整开发文档.md)


这份文档包含：

- App 配置字段
- Spider 开发
- JS/Python Spider 运行时
- 本地 HTTP 服务
- WebHome SDK 参数和返回值
- 透明背景实现建议
- 网盘检测 API
- 站点健康排序
- PanSou 集成建议
- Nostr 首页实现要点
- 隐藏功能和使用技巧
- Android Intent、DLNA、MediaSession
- CORS、Cookie 和网络策略

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
| `fm.pan.check(items)` | 调用内置网盘链接有效性检测，`fm.check(items)` 是短别名 |
| `fm.pan.play({ type, url, password, title })` | 播放网盘分享、磁力、电驴、thunder 等需要进入 push 链路的地址 |
| `fm.config()` | 获取当前配置和网盘检测开关状态 |
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
| `fm.config()` | 拒绝 | 避免暴露网盘检测开关、当前配置 |
| `fm.history()` | 拒绝 | 避免观看记录外泄 |
| `fm.openSetting()` | 拒绝 | 避免第三方页面劫持设置入口 |
| `fm.pan.check(items)` | 拒绝 | 网盘检测改为只能由可信页面触发 |
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

电视端 WebHome 要按遥控器模型单独设计焦点：默认焦点不要放在文本框；搜索建议、状态面板、网盘结果列表等打开后要限制方向键在当前区域内；文本框默认 `readonly`，只有 OK/点击后进入编辑态；动态列表刷新要恢复原焦点和滚动位置。完整经验见 [应用完整开发文档.md](docs/应用完整开发文档.md) 的“电视端遥控器 UX 最佳实践”。

### 6. 内置网盘链接检测和播放

设置页新增“增强功能”入口，网盘检测开关放在增强功能页中，默认开启。开启后，WebHome 或自定义工具可以调用 App 内置检测能力。

WebHome SDK：

```js
const config = await fm.config();
if (config.driveCheck) {
  const result = await fm.pan.check([
    { type: "aliyun", url: "https://www.aliyundrive.com/s/xxx" },
    { type: "quark", url: "https://pan.quark.cn/s/xxx" }
  ]);
}
```

本地 HTTP API：

```http
POST http://127.0.0.1:{port}/pan/check
Content-Type: application/json

{
  "items": [
    { "type": "quark", "url": "https://pan.quark.cn/s/xxx" }
  ]
}
```

> `127.0.0.1` 请求默认放行；如果要跨设备调用，需要附加 token：
>
> ```http
> POST http://<lan-ip>:{port}/pan/check?token=<token>
> Header: X-Fongmi-Token: <token>
> ```
>
> token 在 `Server.getAddress(int)` 或 `ManageService.getLanUrl()` 返回的管理页 URL 里会附带，或者从 App 内的「管理页面」二维码里读取。

检测接口支持批量提交，内部每批最多 10 条并发检测，超过 10 条会自动分批处理。WebHome 开发时建议只检测用户当前可见范围内的资源，并且只检测 App 支持的网盘类型，避免无意义请求和界面跳动。

`fm.pan.play({ type, url, password, title })` 是 WebHome 的网盘播放语义入口，当前内部复用 App 已有的 `push_agent/pvideo` 播放链路。因为底层进入 `SiteApi.PUSH`，磁力、电驴、thunder、jianpian 等地址也可以走这个入口。它的性能和直接推送 `push://` 基本一致，但对 WebHome 开发者更清晰，也方便后续 App 内部调整播放策略。`password` 参数会保留在 API 形态中，当前播放链路主要依赖 App/JAR/pvideo 自身处理。

### 6.1 调试日志

调试日志入口也在“增强功能”页中，默认关闭。开启后，App 会记录 WebHome SDK 调用、`fm.req`/资源网关、`pan.check`、`pan.play`、本地 HTTP 服务、爬虫请求、push/pvideo 和播放状态等链路日志。

行为说明：

- 关闭调试日志时不弹 toast，并自动清空当前进程内日志。
- 日志保存在当前 App 进程内，不限制 2000 条；关闭或进程结束后不保留。
- 开启后会打开 `/debug/logs` 页面，可刷新、下载、清空，也可以通过同局域网地址查看。

### 7. PanSou 网盘搜索集成示例

`demo/nostr-合并.html` 的详情页集成了 PanSou 类搜索能力，支持：

- 自定义盘搜服务地址。
- 账号密码认证。
- 自定义 TG 频道。
- 按网盘类型分 Tab 展示。
- 对支持的网盘类型调用 App 内置检测。
- 只检测可见范围内的结果。
- 检测结果用状态圆点表达。
- 点击资源后调用 `fm.pan.play({ type, url, password, title })` 交给 App 播放。

PanSou 搜索结果可能是异步补充的，示例页会轮询合并新增结果。

### 8. Nostr + TMDB 推荐首页示例

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
- 设置页新增“增强功能”入口，手机端和电视端都是独立设置页，集中放置网盘检测、站点健康排序、管理页面、站点注入、Proxy、一键同步和调试日志。
- 手机端和电视端都保留原有 FongMi/CatVod 能力。
- WebHome 能力优先面向手机端体验，同时兼顾电视遥控器焦点和返回操作。
- 自更新行为已调整：检测到新版本后，APK 通过 `MediaStore.Downloads` 导出到系统「下载」目录，并通过 toast 提示用户「用文件管理器打开安装」。这样 App 不再请求 `REQUEST_INSTALL_PACKAGES` 权限，避免 Play 政策与用户信任风险。
- 文件管理 UI 在 Android 11+ 不再申请「所有文件访问」权限（`MANAGE_EXTERNAL_STORAGE` 已移除），默认只能在 App 专属目录或用户通过 SAF 选定的位置内操作。低于 Android 11 的设备维持原行为。

## Demo

仓库主要维护两个 WebHome 相关示例：

| 文件 | 说明 |
| --- | --- |
| `demo/nostr-合并.html` | 正式推荐首页示例，集成 TMDB、Nostr、PanSou、网盘检测、透明背景 |
| `demo/check.html` | 网盘检测能力测试页 |

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
- 新增 `network_security_config.xml`：影视源继续允许 `http`，但配置源、更新源、远端 JAR 等关键端点（`cnb.cool`、`github.com`、`*.githubusercontent.com`、`gitee.com`）强制 HTTPS。
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

受保护路径：`/manage/*`、`/file`、`/upload`、`/newFolder`、`/delFolder`、`/delFile`、`/debug/*`、`/cache`、`/action`、`/proxy`、`/webResource`、`/pan/check`。

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
| `pan.check()` | 拒绝 |
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

本仓库只提供技术实现和开发示例，不内置、不维护、不分发任何影视内容、播放源、资源站接口或网盘资源。项目中的搜索、播放、网盘检测、TMDB、Nostr、PanSou 等能力都需要用户自行配置合法服务和数据来源。
