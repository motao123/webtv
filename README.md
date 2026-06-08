# WebHomeTV

WebHomeTV 是基于 FongMi / CatVod 生态二次开发的 Android 影音应用，保留原有点播、直播、Spider、解析、投屏、本地 HTTP 服务等能力，并重点增强了 **WebHome 自定义首页**、**App Native SDK**、**网盘链接检测**、**站点健康排序** 和 **Nostr/TMDB 推荐首页**。

这个项目的核心目标不是替换 CSP/Spider 体系，而是让 CSP 站点首页可以变成一个真正可开发的网页应用：开发者可以用 HTML/CSS/JavaScript 定制首页，再通过 App 暴露的 Native 能力完成搜索、播放、跨域请求、资源代理、最近观看、网盘检测和状态同步。

### 增强功能

- **网盘检测**：对网盘相关能力进行可用性检测，帮助确认当前环境是否支持网盘播放或解析。
- **站点健康排序**：自动学习站点搜索、详情和播放成功率，搜索与换源优先使用更可用的站点；站点弹窗默认保留用户配置顺序，可在弹窗内单独开启健康排序。
- **一键同步**：支持在同一局域网设备间同步配置、站源数据、WebHome 数据、搜索记录、观看历史、收藏和应用设置。
- **站点注入**：支持添加自定义 WebHome 或通用 CSP 站点，并可配置启用状态、插入位置、首页、搜索和换源等行为。
- **APP代理**：支持配置代理地址和域名匹配规则，可按当前站点自动建议代理域名，并用于改善特定站点、接口或播放链路的网络访问。
- **调试日志**：提供本机和局域网日志查看入口，便于排查播放、代理、站源和 WebHome 相关问题。

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

## 构建

环境要求：

- JDK 17
- Android SDK 和 Android Gradle Plugin 所需 build tools
- Gradle Wrapper 使用仓库内置 `gradlew`

仓库已经包含完整打包所需的 `app/libs/*.aar` 和 Media3 本地 Maven 产物，clone 后可以直接执行 Gradle 构建命令。

### 直接 clone 并打包

`webhtv-latest-target28` 保持较低 targetSdk，`webhtv-latest-target37` 跟随新版 Android targetSdk。任选一个分支复制执行即可。

target28 手机 arm64：

```bash
git clone -b webhtv-latest-target28 https://github.com/fish2018/webhtv.git
cd webhtv
bash gradlew :app:assembleMobileArm64_v8aRelease
```

target37 手机 arm64：

```bash
git clone -b webhtv-latest-target37 https://github.com/fish2018/webhtv.git
cd webhtv
bash gradlew :app:assembleMobileArm64_v8aRelease
```

已 clone 的仓库切换到 target28：

```bash
git fetch origin
git switch webhtv-latest-target28
```

已 clone 的仓库切换到 target37：

```bash
git fetch origin
git switch webhtv-latest-target37
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

当前播放层依赖：

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

### 友情链接
[![Linux.do](https://img.shields.io/badge/-Linux.do-1c1c1e?style=flat-square&logo=data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZlcnNpb249IjEuMiIgYmFzZVByb2ZpbGU9InRpbnktcHMiIHdpZHRoPSIxMjgiIGhlaWdodD0iMTI4IiB2aWV3Qm94PSIwIDAgMTIwIDEyMCI+CiAgPGNsaXBQYXRoIGlkPSJhIj4KICAgIDxjaXJjbGUgY3g9IjYwIiBjeT0iNjAiIHI9IjQ3Ii8+CiAgPC9jbGlwUGF0aD4KICA8Y2lyY2xlIGZpbGw9IiNmMGYwZjAiIGN4PSI2MCIgY3k9IjYwIiByPSI1MCIvPgogIDxyZWN0IGZpbGw9IiMxYzFjMWUiIGNsaXAtcGF0aD0idXJsKCNhKSIgeD0iMTAiIHk9IjEwIiB3aWR0aD0iMTAwIiBoZWlnaHQ9IjMwIi8+CiAgPHJlY3QgZmlsbD0iI2YwZjBmMCIgY2xpcC1wYXRoPSJ1cmwoI2EpIiB4PSIxMCIgeT0iNDAiIHdpZHRoPSIxMDAiIGhlaWdodD0iNDAiLz4KICA8cmVjdCBmaWxsPSIjZmZiMDAzIiBjbGlwLXBhdGg9InVybCgjYSkiIHg9IjEwIiB5PSI4MCIgd2lkdGg9IjEwMCIgaGVpZ2h0PSIzMCIvPgo8L3N2Zz4K)](https://linux.do/)
