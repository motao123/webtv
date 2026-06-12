<div align="center">

# WebHomeTV

**面向 Android TV / 手机的影视播放器壳子**

用户自己找 JSON / 配置源，再在 App 内导入、管理、同步和使用。

[![Release](https://img.shields.io/github/v/release/motao123/webtv?label=release)](https://github.com/motao123/webtv/releases)
[![Android](https://img.shields.io/badge/Android-5.0%2B-3DDC84)](#下载安装)
[![TV](https://img.shields.io/badge/Android%20TV-Leanback-4285F4)](#下载安装)

</div>

---

## 这是什么

`WebHomeTV` 是基于 **FongMi / CatVod** 生态增强维护的 Android 影音播放器壳子。

它**不内置内容，不分发 JSON**，而是提供一个更好用的壳子，帮助用户：

- 导入自己的点播 / 直播 / 壁纸配置
- 使用 WebHome 自定义首页
- 管理多个配置
- 在手机和电视之间同步配置与状态
- 继续观看、收藏、历史跨设备迁移

一句话：

> 这是一个“让用户自带配置更容易导入、管理和迁移”的播放器壳子。

---

## 当前亮点

| 能力 | 说明 |
| --- | --- |
| 配置导入增强 | 导入前预检，失败不覆盖当前配置，支持 URL / 文件 / assets |
| 配置管理 | 支持查看当前配置、历史配置、来源类型、最后使用时间 |
| 一键同步 | 局域网同步配置、历史、收藏、WebHome、设置等壳子状态 |
| 最近设备记忆 | 一键同步会记住上次设备，下次优先选中 |
| 继续观看 / 收藏 | 强化继续观看、收藏恢复与配置缺失时的回退路径 |
| 家庭过滤 | 按标签/关键词屏蔽不适合电视客厅展示的内容 |
| WebHome 首页 | 每个 CSP 站点都可以配置独立网页首页 |
| Native SDK | 网页可通过 `window.fm` 调用 App 原生播放、搜索、请求、缓存等能力 |
| 本地管理页 | 局域网访问 App 管理页面，支持同步、文件、调试等能力 |
| 自动更新 | 多源更新，适配不同网络环境 |

---

## 下载安装

最新版本：**v5.5.44**

下载地址：

- [GitHub Releases](https://github.com/motao123/webtv/releases)

推荐 APK：

| 设备类型 | 推荐 APK |
| --- | --- |
| Android TV / 新电视盒子 | `leanback-arm64_v8a.apk` |
| Android TV / 老盒子 | `leanback-armeabi_v7a.apk` |
| Android 手机 / 新设备 | `mobile-arm64_v8a.apk` |
| Android 手机 / 老设备 | `mobile-armeabi_v7a.apk` |

> 不确定 CPU 架构时，优先下载 `arm64_v8a`。

---

## 首次使用

安装后先导入你自己的配置源。

常见方式：

- 粘贴配置 URL
- 选择本地配置文件
- 通过局域网同步从另一台设备导入

可导入的配置类型：

- 点播配置
- 直播配置
- 壁纸配置

项目本身不附带内容源，用户需要自行准备合法可用的 JSON / 配置地址。

---

## 适合谁

适合这些用户：

- 已经有自己的 TVBox / FongMi / CatVod 配置源
- 想在电视端使用 WebHome 自定义首页
- 想把手机和电视上的配置、历史、收藏同步起来
- 想把播放器壳子和内容源彻底分离

不适合这些用户：

- 希望安装后自带影视内容
- 希望仓库直接提供 JSON / 站源

---

## 主要功能

### 1. 配置导入与管理

- 导入前预检
- 导入失败不覆盖当前配置
- 查看当前配置 / 历史配置
- 显示来源类型和最后使用时间
- 删除配置前提示关联影响

### 2. 一键同步

可同步的壳子状态包括：

- 配置与站源
- 本地脚本 / Jar 数据
- WebHome 数据
- 搜索记录
- 继续观看
- 收藏
- 应用设置

### 3. 继续观看 / 收藏

- 记录最近观看进度
- 收藏内容可跨配置恢复
- 配置缺失时提供回退路径

### 4. 家庭过滤

- 在增强功能中开启
- 使用关键词屏蔽不适合电视端首页展示的内容
- 适用于原生首页 / 分类 / 搜索等内容流
- WebHome 页面也可以通过 `fm.config()` 读取过滤策略并自行配合隐藏

### 5. WebHome

- 站点可配置独立网页首页
- 支持透明背景
- 支持网页与原生能力联动
- 可通过 `window.fm` 调用搜索、播放、请求、缓存等能力

### 6. 本地管理页

- 局域网打开管理页
- 文件管理
- 同步控制
- 调试日志
- 配置相关操作

---

## WebHome SDK（简要）

常用能力：

| 能力 | 说明 |
| --- | --- |
| `fm.req(url, options)` | 原生请求，绕过普通浏览器 CORS 限制 |
| `fm.res(url, options)` | 生成本地资源网关地址 |
| `fm.play(url, title, options)` | 播放直链或 `push://` 地址 |
| `fm.vod(siteKey, vodId, title, pic)` | 打开原生详情 / 播放链路 |
| `fm.search(keyword, { direct })` | 调用原生搜索 |
| `fm.openLive()` / `fm.openKeep()` / `fm.openSetting()` | 打开原生页面 |
| `fm.history()` | 读取最近观看记录 |
| `fm.config()` | 获取当前配置与家庭过滤状态 |
| `fm.site()` | 获取当前站点信息 |
| `fm.cache` | 本地缓存能力 |
| `fm.back()` / `fm.reload()` | 处理返回与刷新 |

更完整说明见文档。

---

## 文档

- [应用完整开发文档](docs/应用完整开发文档.md)
- [WebHome 扩展脚本开发指南](docs/webhome-extension/README.md)

如果你是开发者，建议先看：

1. WebHome 首页配置
2. Native SDK
3. 配置导入与管理逻辑
4. 本地管理页与同步逻辑

---

## 构建

环境要求：

- JDK 17
- Android SDK
- 仓库内置 `gradlew`

常用构建命令：

```bash
bash gradlew :app:assembleMobileArm64_v8aRelease
bash gradlew :app:assembleMobileArmeabi_v7aRelease
bash gradlew :app:assembleLeanbackArm64_v8aRelease
bash gradlew :app:assembleLeanbackArmeabi_v7aRelease
```

---

## 更新说明

App 内更新入口：

```text
设置 → 版本检查
```

更新策略：

- 检查到新版本后下载 APK
- 下载完成后交给系统安装器
- 如果自动安装失败，APK 会导出到 Downloads 目录，用户可手动安装

---

## 开源说明

本仓库只提供技术实现和播放器壳子能力：

- 不内置影视内容
- 不维护站源
- 不分发 JSON
- 不提供内容接口

所有内容来源都应由用户自行配置，并确保合法合规。
