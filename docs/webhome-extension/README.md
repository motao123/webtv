# WebHome 扩展脚本开发指南

本文档面向两类读者：

- 人工开发者：拿到一个真实网站 URL 后，开发 WebHome 注入脚本，让网页调用 App 原生播放、网盘、嗅探、网络和缓存能力。
- AI 开发助手：读取本文档和目标 URL 后，按固定流程分析页面、生成脚本、给出配置方式和测试步骤。

WebHome 扩展脚本的主流场景不是把网站完全改写成 CSP 爬虫，而是在 App WebView 里加载真实网站，然后通过注入脚本增强这个网站。例如：拦截资源按钮、把网盘/磁力/直链交给 App 播放、给页面增加“App 播放”按钮、清理干扰 UI、嗅探页面运行时出现的媒体地址。

## 1. 能力模型

WebHome = 一个真实网页 + App 注入的 `fm` SDK + 用户/配置注入的扩展脚本。

脚本可以做的事：

- 读取和改写当前网页 DOM。
- 捕获用户点击，阻止网页默认跳转。
- 从 `href`、`data-url`、`onclick`、文本、剪贴板逻辑、网络请求里提取资源 URL。
- 调用 `fm.play()` 播放直链。
- 调用 `fm.pan.play()` 把网盘、磁力、电驴、迅雷、荐片等推送到 App 既有解析链路。
- 调用 `fm.pan.check()` 检测支持的网盘分享是否有效。
- 调用 `fm.req()` 用 Native OkHttp 请求接口，绕开普通 WebView `fetch` 的 CORS 限制。
- 调用 `fm.res()` 把图片、视频、字幕等 DOM 资源转成本地资源网关地址。
- 调用 `fm.cache` 保存脚本配置和轻量状态。
- 通过 `console.log()`、`GM_log()`、`fm.ext.log()` 和调试工作台排查行为。

脚本不适合做的事：

- 不应在没有用户动作的情况下批量打开播放页。
- 不应把所有普通站内链接都拦截成播放；必须先判断是不是资源链接。
- 不应依赖过度脆弱的第 N 个子元素选择器，例如 `body > div:nth-child(4) > a:nth-child(2)`。
- 不应为了“万能”而破坏网站自己的搜索、筛选、登录、翻页和详情跳转。

## 2. 当前实现支持的扩展来源

App 内“WebHome 扩展”是普通用户和开发者的主要入口，位置在“设置 -> 增强功能 -> 站点注入”下面。配置 JSON 里的 `extensions` / `webHomeExtensions` 是批量下发或辅助导入方式，不是唯一入口。

支持的来源方式：

| 方式 | 适合场景 | 说明 |
| --- | --- | --- |
| 文件 | 本地维护 `.js`、`.json` 文件 | App 读取文件内容后作为用户扩展保存 |
| 链接 | 扩展托管在 HTTP 服务、GitHub raw、CDN | 链接可以是 `.js`，也可以是 manifest JSON |
| 代码 | 直接在 App 内写本地 JS/CSS/辅助代码 | 适合开发调试和小脚本 |
| 文本 JSON | 高级用户直接维护完整扩展源 JSON | 适合批量导入、复制、AI 输出 |

## 3. 配置格式

### 3.1 单个扩展对象

```json
{
  "id": "pomo-native-router",
  "name": "Pomo native router",
  "version": "1.0.0",
  "runAt": "document-end",
  "cspKeyRegex": ["^pomo$"],
  "excludeCspKeyRegex": [],
  "js": ["https://example.com/webhome/pomo.mom.js"],
  "depends": []
}
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | string | 否 | 扩展唯一 ID。为空时 App 会自动生成，但正式分发建议固定 |
| `name` | string | 否 | 展示名称 |
| `version` | string | 否 | 版本号，可被 `depends` 约束 |
| `runAt` | string | 否 | `document-start`、`document-end`、`document-idle`，默认 `document-end` |
| `cspKeyRegex` | string[] / string | 否 | 匹配站点 `key` 的正则。全局扩展建议填写 |
| `excludeCspKeyRegex` | string[] / string | 否 | 排除站点 `key` 的正则 |
| `js` | string[] / string | 否 | 外部 JS 地址，支持相对配置 URL 解析 |
| `code` | string | 否 | 内联 JS 代码 |
| `depends` | string[] / string | 否 | 依赖其它扩展 ID，可写 `id@>=1.0.0` |
| `manifestUrl` / `manifest` / `sourceUrl` / `url` | string | 否 | 指向远程 manifest 或 JS |

`js` 和 `code` 至少要有一个。远程 `.js` 会被下载并缓存；下载失败时会尝试使用缓存。

### 3.2 站点内联扩展

站点自己的 `extensions` 与当前站点一一对应，通常不需要再写 `cspKeyRegex`。完整站点对象仍然要保留普通 CSP 外壳，推荐写 `type: 3`、`api: "csp_Builtin"`、`homePage` 和 `extensions`。

在站点注入的 WebHome 表单里，Key 下方有“扩展”开关。默认关闭；打开后填写的是 `extensions` 字段的数组值，也就是下面示例中的 `[...]`。表单也兼容单个 URL 或单个扩展对象，保存时会包装成数组；正式配置建议直接写数组。

```json
{
  "sites": [
    {
      "key": "pomo",
      "name": "Pomo",
      "type": 3,
      "api": "csp_Builtin",
      "homePage": "https://pomo.mom/",
      "extensions": [
        {
          "id": "pomo-native-router",
          "name": "Pomo native router",
          "runAt": "document-end",
          "js": ["https://example.com/webhome/pomo.mom.js"]
        }
      ]
    }
  ]
}
```

### 3.3 全局扩展

根级 `webHomeExtensions` 适合配置作者批量下发。全局扩展不天然绑定某个站点，所以应使用 `cspKeyRegex` 限定范围。

```json
{
  "webHomeExtensions": [
    {
      "manifestUrl": "https://example.com/webhome/extensions.json",
      "cspKeyRegex": ["^pomo$", "^another_site$"]
    }
  ]
}
```

远程 manifest 可以是数组，也可以是带 `extensions` 字段的对象：

```json
{
  "extensions": [
    {
      "id": "generic-pan-router",
      "name": "Generic pan router",
      "version": "1.0.0",
      "runAt": "document-end",
      "cspKeyRegex": ["^pomo$"],
      "js": ["./templates/pan-link-router.js"]
    }
  ]
}
```

## 4. 注入时机

| `runAt` | 适用 | 注意 |
| --- | --- | --- |
| `document-start` | 提前 Hook `window.open`、`fetch`、`XMLHttpRequest`、历史路由 | 需要 Android WebView 支持 `DOCUMENT_START_SCRIPT`；不支持时会降级到 `document-end` |
| `document-end` | 大多数 DOM 增强、点击拦截、按钮注入 | 默认选择，页面主体通常已经可读 |
| `document-idle` | 非关键增强、批量扫描、网盘检测、性能开销较大的任务 | 避免影响首屏和用户首次点击 |

经验规则：

- 要拦截网站很早注册的全局行为，优先 `document-start`，同时写好降级逻辑。
- 要找按钮、资源区、标题、卡片，优先 `document-end`。
- 要做大范围扫描、可见区网盘检测、媒体性能条目扫描，优先 `document-idle` 或延迟执行。

## 5. 脚本运行环境

扩展脚本由 App 包装执行，默认只在顶层 frame 运行。

包装层提供：

```js
GM_addStyle(css);
GM_log(...args);
GM_getValue(key, defaultValue);
GM_setValue(key, value);
GM_deleteValue(key);
GM_xmlhttpRequest(details);
```

`GM_log()` 会同时输出到页面 console 和 App 扩展日志，适合调试扩展脚本。`GM_xmlhttpRequest()` 底层走 `fm.req()`。

SDK 准备完成后会触发：

```js
window.addEventListener("fmsdk", () => {});
```

SPA 路由变化时 App SDK 会触发：

```js
window.addEventListener("fmurlchange", (event) => {
  console.log(event.detail.url);
});
```

建议封装一个等待 SDK 的函数：

```js
function whenFm() {
  if (window.fm) return Promise.resolve(window.fm);
  return new Promise((resolve) => {
    window.addEventListener("fmsdk", () => resolve(window.fm), { once: true });
  });
}
```

## 6. 常用原生能力

### 6.1 播放直链

```js
await fm.play("https://example.com/video.m3u8", "影片名", {
  headers: { Referer: location.href },
  credentials: "include"
});
```

适用：

- `.m3u8`、`.mp4`、`.mkv`、`.flv`、`.mov` 等直链。
- 需要 Referer/Cookie 的直链，传 `headers` 或 `credentials`。

### 6.2 推送网盘/磁力/电驴/迅雷

```js
await fm.pan.play({
  type: "quark",
  url: "https://pan.quark.cn/s/xxxx",
  title: "影片名"
});
```

推荐类型：

| 类型 | 例子 |
| --- | --- |
| `quark` | `https://pan.quark.cn/s/...` |
| `aliyun` | `https://www.aliyundrive.com/s/...`、`https://www.alipan.com/s/...` |
| `baidu` | `https://pan.baidu.com/s/...` |
| `uc` | `https://drive.uc.cn/s/...` |
| `xunlei` | `https://pan.xunlei.com/s/...` |
| `tianyi` | `https://cloud.189.cn/t/...` |
| `123` | `https://www.123pan.com/s/...` |
| `115` | 115 分享链接 |
| `mobile` | 移动云盘/和彩云 |
| `magnet` | `magnet:?xt=...` |
| `ed2k` | `ed2k://...` |
| `thunder` | `thunder://...` |
| `http` | 无法归类但希望走推送解析的普通 URL |

### 6.3 网盘有效性检测

```js
const config = await fm.config();
if (config.driveCheck) {
  const result = await fm.pan.check([
    { type: "quark", url: "https://pan.quark.cn/s/xxxx", password: "" }
  ]);
  console.log(result.results);
}
```

检测必须异步做，不能阻塞首屏。只检测 App 支持的网盘，不要把磁力、电驴、普通网页链接提交给 `pan.check`。

### 6.4 Native 请求

```js
const res = await fm.req("https://api.example.com/list", {
  responseType: "json",
  credentials: "include",
  headers: { Referer: location.href }
});
```

`fm.req()` 用 Native OkHttp，不受普通 WebView CORS 限制。DOM 资源使用 `fm.res()`：

```js
video.src = fm.res(videoUrl, { headers: { Referer: location.href } });
```

## 7. 网站分析流程

AI 或人工拿到网站 URL 后，按下面顺序分析。

### 7.1 判断页面类型

先回答：

- 首页、列表页、详情页分别是什么 URL 形态？
- 资源按钮在列表页还是详情页？
- 页面是服务端直出 HTML，还是 SPA 运行后才渲染？
- 资源 URL 是直接写在 DOM，还是点击后通过 API 获取？
- 默认行为是 `window.open`、`location.href`、复制剪贴板、下载文件，还是打开站内播放页？

### 7.2 找稳定选择器

优先选择语义稳定的选择器：

```js
".download-item"
".download-link"
"[data-url]"
"a[href*='magnet:']"
"a[href*='pan.quark.cn']"
```

避免选择：

```js
"body > div:nth-child(3) > div:nth-child(2) > a"
".text-gray-900.mt-4.flex.items-center"
```

选择器需要能跨多部影片、多页结果、多种资源分组工作。

### 7.3 找资源 URL

按优先级提取：

1. `data-url`、`data-href`、`data-link`、`data-clipboard-text`。
2. `href`，排除 `javascript:;`、`#`、空值和站内导航。
3. `onclick` 字符串里的 URL。
4. 当前卡片或按钮附近文本里的 URL。
5. 点击后出现的弹窗、复制文本、网络请求。

### 7.4 识别资源类型

```js
function classify(url) {
  if (/^magnet:/i.test(url)) return "magnet";
  if (/^ed2k:/i.test(url)) return "ed2k";
  if (/^thunder:/i.test(url)) return "thunder";
  if (/pan\.quark\.cn/i.test(url)) return "quark";
  if (/aliyundrive\.com|alipan\.com/i.test(url)) return "aliyun";
  if (/pan\.baidu\.com/i.test(url)) return "baidu";
  if (/drive\.uc\.cn/i.test(url)) return "uc";
  if (/pan\.xunlei\.com/i.test(url)) return "xunlei";
  if (/cloud\.189\.cn/i.test(url)) return "tianyi";
  if (/123pan\.|123684\.|123685\.|123912\.|123592\.|123865\./i.test(url)) return "123";
  if (/115\.com|115cdn\.com/i.test(url)) return "115";
  if (/yun\.139\.com|caiyun\.139\.com/i.test(url)) return "mobile";
  if (/\.(m3u8|mp4|mkv|flv|mov|avi|webm)(\?|#|$)/i.test(url)) return "media";
  return "http";
}
```

### 7.5 选择注入策略

| 策略 | 使用场景 | 优点 | 风险 |
| --- | --- | --- | --- |
| 捕获点击并改路由 | 页面已有“下载/播放”按钮 | 改动小，用户习惯不变 | 需要准确判断资源按钮，避免拦截站内导航 |
| 注入“App 播放”按钮 | 不想改变原网站按钮行为 | 可控、明确 | UI 需要适配移动/TV |
| 改写链接 `href` | 链接列表很规整 | 实现简单 | 容易破坏站点原跳转和复制 |
| 网络/媒体嗅探 | 资源点击后动态加载直链 | 能发现 DOM 没暴露的媒体 | JS 只能看到页面层 fetch/XHR/performance，不等同完整 Chrome DevTools |
| 页面清理 | 弹窗、遮罩、新窗口影响使用 | 改善体验 | 不能过度隐藏正文和关键操作 |

最稳妥的默认组合：

1. 注入“App 播放”按钮。
2. 对明确资源按钮做捕获点击。
3. 保留复制按钮、详情跳转、搜索筛选的原行为。
4. 对 SPA 使用 `MutationObserver` 和 `fmurlchange` 重扫。

## 8. 通用脚本结构

推荐骨架：

```js
(function () {
  const CONFIG = {
    actionSelector: "a[href],button,[data-url]",
    buttonText: "App播放"
  };

  function log() {
    if (typeof GM_log === "function") GM_log.apply(null, arguments);
    else console.log.apply(console, ["[fm-ext]"].concat([].slice.call(arguments)));
  }

  function whenFm() {
    if (window.fm) return Promise.resolve(window.fm);
    return new Promise((resolve) => {
      window.addEventListener("fmsdk", () => resolve(window.fm), { once: true });
    });
  }

  function ready(fn) {
    if (document.readyState === "loading") {
      document.addEventListener("DOMContentLoaded", fn, { once: true });
    } else {
      fn();
    }
  }

  function schedule(fn) {
    clearTimeout(schedule.timer);
    schedule.timer = setTimeout(fn, 120);
  }

  ready(() => {
    document.addEventListener("click", onClick, true);
    installObserver();
    scan();
  });

  window.addEventListener("fmurlchange", () => schedule(scan));

  function installObserver() {
    const mo = new MutationObserver(() => schedule(scan));
    mo.observe(document.documentElement, { childList: true, subtree: true });
  }

  function scan() {
    // 注入按钮、刷新状态、绑定标记。
  }

  async function onClick(event) {
    // 捕获资源点击。
  }
})();
```

## 9. 调试流程

推荐 App 内开发流程：

1. 设置 -> 增强功能 -> 站点注入下方的 WebHome 扩展。
2. 使用“表单 -> 代码”新增扩展，绑定当前 WebHome 站点。
3. 打开调试工作台，进入目标页面。
4. 在代码页修改脚本，保存并预览。
5. 在 Web 页点击目标按钮。
6. 在 Console 看 `console.log` / `GM_log` 输出。
7. 在 Network 看页面层 fetch/XHR、资源加载和 App 记录到的请求信息。
8. 在 Elements 查看当前 DOM 片段和候选选择器。

调试注意：

- `console.log()` 必须在脚本实际注入后执行才会出现在工作台；建议同时使用 `GM_log()`。
- 如果页面是 SPA，第一次加载后 DOM 可能变化，必须监听 `fmurlchange` 和 `MutationObserver`。
- 如果按钮点击后网页立刻跳转，需要用捕获阶段监听：`document.addEventListener("click", handler, true)`。
- 如果网站按钮只复制剪贴板，先分析复制逻辑里的数据源，不要直接拦截“复制”按钮为播放。
- 如果 Network 没看到某些请求，可能是浏览器底层资源、iframe、Service Worker 或原生 WebView 行为，JS 调试面板不等同完整 Chrome DevTools。

## 10. AI 自动开发流程

给 AI 一个 URL 时，AI 应输出：

1. 页面角色判断：主页/列表页/详情页/播放页。
2. 关键 DOM 选择器：标题、资源区、资源项、按钮、链接。
3. 资源 URL 来源：`href` / `data-url` / `onclick` / API / 点击后网络。
4. 资源分类规则：网盘、磁力、电驴、迅雷、直链、普通页面。
5. 注入策略：拦截点击、注入按钮、嗅探、清理。
6. 可直接使用的 JS 脚本。
7. 推荐 manifest / 站点 `extensions` 配置。
8. 测试步骤和风险。

AI 分析页面时应执行：

```js
(() => {
  const candidates = [];
  document.querySelectorAll("a,button,[data-url],[data-href],[data-clipboard-text]").forEach((el, index) => {
    const text = (el.innerText || el.textContent || "").trim().replace(/\s+/g, " ").slice(0, 120);
    const attrs = {};
    for (const attr of ["href", "data-url", "data-href", "data-link", "data-clipboard-text", "onclick", "class", "id", "title"]) {
      const value = el.getAttribute(attr);
      if (value) attrs[attr] = value.slice(0, 500);
    }
    const url = attrs["data-url"] || attrs.href || attrs["data-href"] || attrs["data-link"] || attrs["data-clipboard-text"] || "";
    if (url || /下载|播放|网盘|磁力|复制|资源|download|play/i.test(text)) {
      candidates.push({ index, tag: el.tagName.toLowerCase(), text, attrs });
    }
  });
  return {
    url: location.href,
    title: document.title,
    h1: Array.from(document.querySelectorAll("h1,h2")).map(el => el.textContent.trim()).filter(Boolean).slice(0, 10),
    candidates: candidates.slice(0, 200)
  };
})();
```

AI 生成脚本时必须遵守：

- 不拦截所有 `a[href]`，只拦截已分类为资源的链接。
- 点击处理必须 `try/catch`，失败后 toast 或 log。
- 使用 `closest()` 和事件委托，不给每个按钮重复绑定大量监听。
- 用 `data-fm-*` 标记已处理节点，避免重复注入。
- 扫描函数 debounce，MutationObserver 不能每次 DOM 变化都全量重扫。
- 对用户原来的复制、搜索、筛选、翻页尽量不改变。
- 标题提取应有兜底：详情标题 -> `h1/h2` -> `document.title` -> URL。
- 远程分发脚本建议固定 `id` 和 `version`。

## 11. 一键生成通用注入脚本能力设计

这个能力可行，但不应做成“完全无确认自动改网站”。更合理的是“自动分析 + 候选确认 + 模板生成 + 立即预览”。

推荐产品流程：

1. 在调试工作台增加“分析当前页”。
2. App 执行页面采集脚本，生成结构化报告：
   - 当前 URL、标题、站点 key。
   - 候选按钮/链接，含文本、选择器、属性、附近 DOM。
   - 已识别资源 URL，含类型、来源属性、所在卡片。
   - 点击下一次目标元素的录制结果。
   - fetch/XHR/资源网络样本。
   - video/audio/source 当前地址。
3. UI 展示候选列表，用户选择“哪个按钮是资源按钮”、“哪个标题是影片名”、“动作是 App 播放还是网盘推送”。
4. 生成脚本：
   - 资源点击路由模板。
   - 注入 App 播放按钮模板。
   - 网盘检测模板。
   - 媒体嗅探模板。
   - 页面清理模板。
5. 用户点击“保存并预览”，App 写入本地扩展并 reload 当前 WebHome。
6. 调试工作台展示脚本日志、点击路由、网络样本和错误。

技术上建议拆成三层：

| 层 | 职责 | 复杂度 |
| --- | --- | --- |
| 页面采集器 | 在 WebView 当前页面内运行 JS，返回候选 DOM/URL/网络样本 | 中 |
| 规则生成器 | 根据候选和用户选择生成 manifest + JS | 中 |
| 交互调试 UI | 候选确认、代码编辑、保存预览、回滚 | 中高 |

必须有用户确认的原因：

- 很多网站的“下载”可能是广告、站内跳转、复制、登录提示或真实资源，单靠文本无法 100% 判断。
- 同一页面可能同时有多个资源版本，自动挑一个会误导用户。
- 泛拦截会破坏网站正常浏览。
- 外部 AI 分析可能涉及页面 URL、DOM 和 Cookie 上下文，必须由用户确认是否发送。

可先实现半自动 MVP：

- “分析当前页”只在本地生成候选 JSON。
- “生成通用脚本”基于本地模板，不调用外部 AI。
- 用户手动选择 action selector、URL 属性、标题 selector、动作类型。
- 生成脚本后进入代码页，用户可继续编辑。

## 12. 通用模板清单

本目录提供以下模板：

| 文件 | 用途 |
| --- | --- |
| `examples/pomo.mom.js` | Pomo 实战示例：美化移动端/电视端页面，生成“在线播放/网盘/磁力”三组播放列表，点击直接调用原生播放 |
| `examples/pomo.manifest.json` | Pomo 示例 manifest，可作为外部扩展清单格式参考 |
| `templates/page-analyzer.js` | 当前页面候选 DOM/资源分析器，适合半自动生成脚本前采集数据 |
| `templates/auto-resource-router.js` | 通用资源路由：识别 DOM 里的网盘、磁力和媒体直链 |
| `templates/pan-link-router.js` | 通用网盘链接增强：扫描网盘链接、注入播放按钮、可选检测 |
| `templates/media-sniffer.js` | 页面层媒体嗅探：hook fetch/XHR、扫描 video/source/performance |
| `templates/inject-play-buttons.js` | 给指定资源项注入 App 播放按钮 |
| `templates/site-cleanup.js` | 页面清理：新窗口、遮罩、广告区域、滚动锁定等 |

模板不是最终脚本。使用时先改 `CONFIG`，再按目标网站微调选择器和标题提取逻辑。

## 13. 质量检查清单

发布脚本前逐项确认：

- 首页、列表页、详情页都不会报错。
- 没有资源的页面不注入多余按钮。
- 搜索、筛选、翻页、详情跳转仍可用。
- 资源按钮点击后进入 App 播放或推送链路。
- 复制按钮仍保持复制行为，除非用户明确希望改成播放。
- SPA 路由切换后按钮不会重复出现。
- `console.log("ready")` 或 `GM_log("ready")` 能在调试工作台看到。
- 失败时有 toast 或日志，不会静默无响应。
- 网盘检测只在用户开启检测时运行。
- 大页面扫描有 debounce，不会卡顿。
- 没有把账号、Cookie、隐私数据输出到远程日志。

## 14. Pomo 示例结论

以 `https://pomo.mom/` 为例，首页是影片列表，详情页存在“资源下载”区。详情页资源项使用 `.download-item` 容器，资源链接和按钮使用 `.x-dbjs-download-link`、`.x-dbjs-download-btn`，真实 URL 存在 `data-url`。标题可从 `.x-dbjs-title` 读取。

适合策略：

- 隐藏原详情页里分散的在线播放、下载和资源区域，减少手机端误点。
- 首页和搜索页改成搜索优先的简洁影视列表，保留筛选、翻页、加载更多。
- 影片卡片改为手机端双列、平板/电视端多列，加入明确焦点高亮，兼容遥控器 D-pad 操作。
- 详情页隐藏顶部大剧照和底部“探索更多”，将大剧照弱化为简介背景；收藏按钮隐藏，IMDB 评分移动到海报右上角。
- 详情页重新整理海报、元数据、简介、剧照和播放面板的层级，优先展示可直接播放的内容。
- 从详情页资源区提取网盘、磁力、电驴、迅雷等资源。
- 后台读取 Pomo 的在线播放页，解析 `rawData` 里的 m3u8 选集。
- 在详情卡片下生成一个统一播放面板，按“在线播放 / 网盘 / 磁力”三组展示。
- 点击在线播放条目调用 `fm.play()`；点击网盘、磁力、电驴、迅雷条目调用 `fm.pan.play()`。

示例脚本见 [examples/pomo.mom.js](examples/pomo.mom.js)。

站点内联挂载示例：

```json
{
  "key": "pomo",
  "name": "Pomo",
  "type": 3,
  "api": "csp_Builtin",
  "homePage": "https://pomo.mom/",
  "extensions": [
    {
      "id": "pomo-native-router",
      "name": "Pomo native router",
      "version": "1.2.2",
      "runAt": "document-end",
      "js": ["https://example.com/webhome/pomo.mom.js"]
    }
  ]
}
```
