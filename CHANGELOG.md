# Changelog

## 5.17.3 — 投屏兼容模式：为不支持 HLS 的电视转封装为 MPEG-TS

- **新增投屏「兼容模式」**（投屏设备选择对话框里可勾选，默认关闭）：面向**本身不支持 HLS 的电视/盒子**。5.17.2 的手机中转只解决了防盗链（电视不会带 `Referer`/`UA`/`Cookie`），对这类设备无效（Issue #13 的实测反馈）。
- **采用纯 Java 拼接，不引入 FFmpeg**：MPEG-TS 是 188 字节自包含包、无全局文件头，HLS 的 `.ts` 分片按播放列表顺序**字节级拼接就是合法的连续 TS 流**——这正是 `ffmpeg -c copy -f mpegts` 的本质。手机按真实请求头依次取分片、顺序写进同一个 HTTP 流，零重编码、零新增依赖、不增加安装包体积。
  - 之所以不走「起 ffmpeg 子进程」：Android 10+ 的 W^X 限制禁止从应用私有目录执行二进制；项目里的 `libijkffmpeg.so` / `libmpv.so` 是播放器内核，没有封装器接口；且现有 4 个安装包已超 100 MB。
- **明确降级而非静默黑屏**：检测到 fMP4（`#EXT-X-MAP`）、AES-128 加密分片、`#EXT-X-DISCONTINUITY`、直播滚动列表、非 TS 分片时，返回具体原因而不是让电视黑屏。master 播放列表在服务端内部跟随首个 variant（不依赖渲染器处理 302）。
- **DIDL 声明改为真实 MIME**：兼容模式声明 `http-get:*:video/MP2T:*`，不再用泛化的 `video/*`——部分严格设备会据此直接拒绝投屏。
- 投屏地址在**点击设备的那一刻**才计算（此前在 Activity 构造时就算好了），这样对话框里的开关能生效。
- 新增 `TsPlaylistTest`（11 条断言）覆盖播放列表解析、相对路径、CRLF、查询串、UTF-8 BOM 与各类降级分支；`HeaderStoreTest` 同步更新以区分「有效但无请求头的上下文」与「无效上下文」。播放列表解析刻意只用纯 Java：单测跑在 JVM 上，`android.jar` 是「方法未实现即抛异常」的桩，用 `TextUtils` 会让测试与实现无关地全红。

> 已知取舍：兼容模式下连续流**不支持拖动进度条**，且中转期间手机需保持在网。支持 HLS 的设备保持默认关闭，行为不变。

## 5.17.2 — m3u8 投屏改为经手机中转

- **手机端投屏（Issue #13）**：此前投屏是把原始链接交给电视，而电视请求 m3u8 分片时**不带 `Referer` / `User-Agent` / `Cookie`**，多数片源在这些头做防盗链校验，于是「App 里能播、投屏黑屏」。现在当播放中的视频需要请求头时，投屏改为**经手机中转**：手机按真实头去取分片，改写播放列表后交给电视。链接本身不需要头时仍然直连，不为能播的源平白多绕一跳。
- **顺带修复两个投屏即坏的问题**：① Python 直播源发出的 `127.0.0.1/m3u8` 链接重写成局域网地址后**没带访问令牌**，电视侧会被本机服务直接拒绝（401）；② 投屏**本地视频**时 `/file` 地址同样缺令牌。两者现在都会补上。
- **上游请求头不落进电视可见的 URL**：把真实请求头写进代理地址虽然可行，但那会让 `Cookie` 出现在电视日志与同网段抓包里。改为只在手机内存里保存、按短 id 取回（有效期 12 小时、上限 16 条，自动淘汰）。
- 新增 `HeaderStoreTest`（7 条断言）覆盖寄存行为：透传、逐跳头剔除、大小写、空值、容量淘汰与 id 不可预测性。

## 5.17.1 — 修复电视端设置页遥控器无法继续下移

- **电视端设置页**：「图像大小」往下的项目用遥控器下键选不到（`siteBlock`、`recordSync`、`公网远程`，以及语言 / 关于 / 编码能力等）。两个原因叠加：`图像大小` 被硬编码了 `nextFocusDown="@id/backup"`，会跳过中间 6 项；而 `公网远程` 在新增「安全远程播放」功能时被误插进了 `备份 / 恢复` 所在的**水平容器**里，其 `match_parent` 宽度把按权重分配的 `备份` `恢复` 挤成零宽，导致跳转目标落在一个不可见的项上。
- 修法：移除该 `nextFocusDown`，恢复自然顺序（图像大小 → 语言 → 关于 → 编码能力 → 站点屏蔽 → 播放进度同步 → 公网远程 → 备份/恢复）；把 `公网远程` 提升为顶层全宽项，未动 `备份` `恢复` 的并排布局。

## 5.17.0 — 更新流程改进

- **可自选默认更新方式**：在「关于 → 更新设置」里新增**默认更新方式**，可选**浏览器下载**（默认）或**应用内下载**，决定更新弹窗把哪一个放在主按钮位置。电视端不显示该项——电视没有浏览器可以接管，一直走应用内下载。
- **手机端更新弹窗**：主按钮会**把下载链接复制到剪贴板并打开浏览器**，由系统浏览器接管下载，不必再把手机占着；另一个动作保留为次按钮，下载进度随之显示在该按钮上。下载途中点浏览器按钮会先停掉应用内下载再交给浏览器，避免后台白跑。
- **弹窗里补上可长按选择的下载链接**：此前的提示语写着「复制以下链接到浏览器打开」，界面上却根本没有链接可复制。
- **修复 GitHub 更新直链 404**：此前拼的是 `/releases/latest/<文件名>`，而 GitHub 只在 `/releases/latest/download/<文件名>` 提供资产，这条线路一直是死的（被自建镜像与 CNB 兜住，所以没暴露）。同时去掉会拼出**无版本号旧文件名**的兜底——v5.16.0 起资产名已带版本号。

## 5.16.0 — 安全修复、多语言补全与产物命名改进

这一版把此前积累在 `main` 上、一直未随版本发布的改动一并带出：

- **安全**：修复 m3u8 代理与 MPV 代理的**重定向绕过**（URL 校验通过后，302 仍可把请求引向内网与云元数据），并收紧 DASH manifest 的 XML 解析（禁用 DOCTYPE、外部实体与外部 DTD，避免 XXE 与实体膨胀）。
- **多语言**：补齐「公网远程」功能的**简繁中文翻译**（此前只有英文，Android 会静默回退，界面上中英混排），并加入跨语言一致性检查。
- **产物**：APK 文件名改为**带版本号**（如 `mobile-arm64_v8a-5.16.0.apk`），消除发版后 CDN 缓存 300 秒造成的旧包窗口；App 端零改动。
- **其它**：EPG 拆分到独立分支、镜像拉取脚本加入完整性闸门、CI runner 固化到 `ubuntu-24.04`。

### 变更

- 补齐「公网远程」功能的简繁中文翻译，并加入多语言一致性检查

### 修复

- 17 条 `remote_public_*` 字符串（公网远程开关、一次性配对码、吊销设备身份、中继地址提示等）此前只有英文，简体与繁体中文均缺失。Android 对缺失的翻译会**静默回退显示英文**——构建和运行都不报错，所以界面上一直是这样中英混着的。已按英文顺序补齐两版翻译。
- 新增 `scripts/check_i18n.py` 并接入 CI，跨 main / mobile / leanback 三个 source set 比对默认（英文）与各语言，覆盖 `<string>` 与 `<string-array>` 两类资源，报告四类问题：**缺失的键**（会回退英文）、**多余的键**、**占位符或 `xliff:g` 数量不一致**（运行时会抛 `IllegalFormatException`）、以及**与默认完全相同的值**（可能是漏翻，也可能本就是专名符号）。前三类会让 CI 失败，第四类只提示。脚本会跳过 `translatable="false"` 的条目（内嵌图片、常量），它们与语言无关，纳入比对只会产生噪音。

### 说明

- 比对后三语言完全一致：main 各 1164 条（含 19 个选项数组，另有 1 条 `translatable="false"`），mobile 各 36 条（含 1 个），leanback 各 11 条。
- 「与默认相同」的 45 条经逐条确认均无需翻译：符号（`→` `←` `-` `+`）、技术专名（`Token` `Proxy` `UA` `EPG` `LUT` `DoH` `GitHub`）、播放内核与渲染名（`EXO`/`IJK`/`MPV`、`Surface`/`Texture`）、URL/目录示例，以及纯占位符格式串（`%1$s · %2$s`）。

### 三处安全修复（重定向绕过 ×2、DASH XML 解析）

#### 修复

- `server/process/M3u8`：此前只对查询参数里的 URL 做 `UrlSafety` 校验，随后交给**默认跟随重定向**的 OkHttp 客户端。上游只要返回 `302` 指向内网地址即可绕过检查，把设备变成 SSRF 中继。改为**手动逐跳跟随、并对每一跳重新校验**——重定向功能不受影响（不少直播源依赖 302 跳转），playlist 改写所用的 base 仍是最后一跳的 URL，行为不变。
- `androidx/media3/mpvplayer/MpvHlsProxy`（请求路径）：同一类问题——`fetch()` 校验了 `isSafeMediaUrl`，但它使用的客户端同样会跟随重定向，`302` 指向回环或 `169.254` 元数据地址即可绕过。已改为逐跳重新校验，并保留放宽的媒体策略（局域网流媒体照常播放）。
- `androidx/media3/mpvplayer/MpvHlsProxy`（XML 解析）：解析来自播放源的 DASH manifest 时，`DocumentBuilderFactory` 默认会解析 DTD 与外部实体，构造的 manifest 可借此访问内网地址或触发实体膨胀耗尽内存。已禁用 DOCTYPE 声明、外部实体与外部 DTD，并关闭 XInclude 与实体展开；该改写逻辑在解析失败时本就回退原文，因此功能无损。

#### 说明

- 两处均出自一次自动化的代码安全审计，并经人工逐条复核确认（同批 5 条报告中另有 2 条属已注释说明的设计取舍、1 条尚待专门复核）。

### APK 产物改用带版本号的文件名，发版不再受 CDN 缓存影响

#### 变更

- 发版产物改名为 `<flavor>-<version>.apk`（如 `mobile-arm64_v8a-5.16.0.apk`）；更新清单的 `apk` 字段随之指向新名字，并新增 `flavor` 字段标识变体。
- 起因是 EdgeOne 的**分档缓存**：清单与首页是 `no-cache`（每次回源、实时），而 APK 二进制是 `public, max-age=300`。文件名跨版本复用，就意味着发版后最多 5 分钟内下载到的仍可能是上一版——`Updater.validate()` 的 sha256 校验会因此失败，而同一窗口内的三次重试同样拿不到新包（详见 `debug/cnb-双仓库架构评审-20261008.md` §六 的实测）。文件名带上版本号后，每次发版都是一个 CDN 从未见过的 URL，天然 MISS。
- **App 侧零改动**：`Updater.readManifest()` 一直是先从清单取 `apk` 字段，再据此拼出三条线路的 URL（自建镜像 / GitHub Release / CNB raw），所以清单换了名字就自动生效，**已安装的旧版本同样如此**，不必发新版过渡。
- 新增 `scripts/bump_version.sh`：一条命令同步 README 与 Pages 站点的版本标记和 10 个下载链接（`--dry-run` 可先预览），替代手改十来个位置。
- 新增 `scripts/test_apk_index.py`（14 条断言）并接入 CI，覆盖下载页对「带版本 / 不带版本」两种清单的处理，以及缺包时必须报错——这个脚本历史上就因包数写死让一次发版失败过。

#### 修复

- 发版前置检查新增一项：`docs/index.html` 的下载链接必须与将要发布的资产名一致，否则在编译前失败并给出修复命令。资产改名后 Pages 上的旧链接会 404，而这种坏法在有人点击之前是完全静默的。
- 同一条检查里的版本标记正则此前只认 `最新版本：**vX.Y.Z**`（带全角冒号）。README 重做成项目主页风格时去掉了冒号，正则从此失配、读到空值，结果是**下一次发版必然在 preflight 失败**。改为同时接受两种写法，并在读不到标记时给出明确诊断，而不是「shows version ""」这种会把人带偏的提示。
- Release notes 里「8 个 APK 产物」与实际 10 个不符，一并改正。

#### 说明

- 清单文件名仍是 `<flavor>.json`（App 端正是按这个名字请求的），只有 APK 二进制带版本号。
- `docs/index.html` 本次**未改**：当前 latest 仍是 v5.15.0，其资产是旧的固定名，现有链接有效。下次发版前跑一次 `bash scripts/bump_version.sh <新版本>` 即可完成切换（不跑则在 preflight 被拦下）。

### EPG 挪到独立分支，与 APK 镜像彻底解耦

#### 变更

- EPG（`pl.xml.gz`）从 CNB 镜像仓的 `main:apk/epg/` 挪到**专用 `epg` 分支**。`main` 是发版时用 orphan 分支整体重建的 APK 镜像，每次发布会把 `apk/` 整个换掉；EPG 与 APK 的生命周期完全不同，挤在同一条分支上就只能靠「发版前备份、抓不到再沿用」的补丁兜着。挪到独立分支后，发版流程根本不会碰到它。
- `epg-sync.yml` 改为推送到 `epg` 分支（分支尚不存在时以 orphan 起一条，兼容首次运行；并修正了首次运行无 `HEAD` 时不能拿 `git diff --cached` 判断的边界）。
- `scripts/pull_apk.sh` 的 `EPG_URL` 改指 `.../raw/epg/pl.xml.gz`；于是**发版 workflow 里那套 EPG 备份/沿用补丁被删除**（`2f1f04e` 引入的），不再需要。
- 电视端下载地址不变（`https://pan.imotao.com/file/apk/epg/pl.xml.gz`），**App 侧零改动**。

#### 说明

- 切换顺序是「先建分支并验证 → 再改服务器」：已确认 `epg` 分支的 raw 可取（HTTP 200，内容与旧位置逐字节一致）后才动脚本。
- 服务器上用临时目录做了全流程演练：退出码 0、89 秒、22 个文件逐字节一致，且演练产物 / 线上服务 / `epg` 分支三处的 EPG sha256 完全相同。
- 部署留了备份 `/root/pull_apk.sh.bak.20261008193512`；`pull_apk.sh` 回归测试 14 条断言仍全绿。

### 拉取脚本改为「先闸门后同步」，并保护 EPG

#### 修复

- `scripts/pull_apk.sh`（部署于宝塔 `/root/pull_apk.sh`）此前是**先 `rsync --delete` 再校验**。若 CNB 镜像仓的某次重建不完整（缺 APK 或体积不对），`--delete` 已经把线上目录清空了，事后的校验只能打条日志，损失已经造成。
- 改为**同步前先过完整性闸门**：逐条清单核对「清单声明的 APK 存在、且体积与 `size` 完全一致」，任一项不符即整轮中止并保留线上文件；`rsync` 之后再复核一次作为兜底。
- 顺带保护 EPG：`rsync` 排除 `/epg`。节目单本就由脚本开头的 raw 直取负责刷新，生命周期与 APK 无关，镜像重建漏掉它时不该删掉线上正在服务的那份。
- 外部路径（`DEST` / `RAW` / `CLONE_URL` / `LOG` / `LOCK` / …）改为可由环境变量覆盖，默认值即生产值——脚本因此可以在临时目录里完整演练。

#### 说明

- 默认行为与旧版一致，只新增前置校验；已用桩件跑通五个场景（完整镜像 / 缺 APK / 体积不符 / 镜像无 EPG / 版本未变）。

### 发版重建镜像时不再可能丢 EPG

#### 修复

- `android-release.yml` 重建 CNB 镜像（orphan 分支）时会连工作区里的 `apk/` 一起删掉，其中包含 `epg-sync` 定时推上来的 `pl.xml.gz`；而后面补抓的 `curl ... || true` 一旦失败就静默放过，于是这次发布会把**没有 EPG 的 `apk/`** 强推上去。宝塔 `pull_apk.sh` 的 `rsync --delete` 会立刻删掉线上节目单，直到下一次 `epg-sync`（最长 4 小时）才恢复。
- 改为**先备份再重建**：重建前置出镜像里现有的 `apk/epg/pl.xml.gz`，抓取失败时沿用它；连兜底都没有时才输出 `::warning`。另外给抓取结果补了 `gzip -t` 校验，避免把错误页当成节目单提交进去（原写法既没校验、也静默失败）。
- 三条分支（上游可用 / 上游失败有兜底 / 上游失败无兜底）用桩件逐一跑通验证。

#### 说明

- 只动发版 workflow 里的这一个 step，不涉及服务器侧脚本，**无需与 `pull_apk.sh` 协同部署**即可生效。

### 确立「GitHub 唯一可写源」，CNB 镜像改为只快进

#### 修复

- **`cnb-source-mirror.yml` 的 `git push --mirror` 会破坏 CNB 侧的提交。** `--mirror` 是「强一致 + 删多余引用」语义，实测 dry-run 会把 CNB 的 `main` 从 `a0e9c8c` 强制回滚到 GitHub 的 `4d340ed`，并删除 `refs/notes/npc-push`、`refs/pull/{4,5,6}/*`、`refs/heads/auto/*`。也就是说：**任何在 CNB 侧产生的提交或 PR，都会被下一次同步静默抹掉**（当时已有 `.cnb.yml`、`ci/android.Dockerfile`、third_party 清理等 7 个提交只存在于 CNB 侧）。
- 改为「只快进、不强制」：分支用不带 `+` 的显式 refspec 推送，标签单独允许强制；并在推送前加**分叉守卫** —— 若某个分支在 CNB 侧领先于 GitHub，直接以 `::error` 失败告警并中止，而不是覆盖。顺带受益的是 `refs/pull/*`、`refs/notes/*` 等平台引用不再被删除。
- 去掉该步骤的 `continue-on-error: true`：镜像失败必须看得见，此前会被静默吞掉。
- 令牌改为走 `credential.helper` 而非写进 remote URL，避免日志或报错把 `CNB_TOKEN` 打出来。

#### 变更

- **确立单一真源**：GitHub `motao123/webtv` 是唯一可写源，CNB `code_free/webtv-coding` 为只读镜像。此前 CNB 侧已存在的 7 个提交（`.cnb.yml`、`ci/android.Dockerfile`、third_party 冗余清理、发版门禁修复）已全部回流到本仓库，两侧不再分叉。
- **`.cnb.yml` 去掉 `main: push` 门禁**：镜像推送同样会触发 push 事件，导致同一份提交在 GitHub CI 与 CNB 上各跑一遍完全相同的编译 + 单测（约 9 分钟/次）。保留 `main: pull_request` 门禁与 `npc:go` 评审——它们只在有人真的在 CNB 侧开 PR 时生效，那才是 GitHub 看不见的场景。
- README 的双仓库架构说明补上「只读镜像、改代码请到 GitHub」的明确约束。

#### 说明

- 未改任何业务代码，仅调整同步语义、CI 触发范围与文档。

### CNB 编译门禁改用自建 Android 镜像

#### 修复

- CNB 侧编译门禁此前声明 `cnbcool/android:latest`，但 CNB 的 `cnbcool` 命名空间下**并不存在该镜像**，流水线在 Prepare 阶段就以 `pull access denied` 失败（构建日志实测）。
- 改为用 `ci/android.Dockerfile` 动态构建：Temurin JDK 21（jammy）+ 发行版 python3.10 + Android cmdline-tools / platform-37 / build-tools 37.0.0，并复制 Go 工具链以支撑 `serverless/webtv-remote-go` 的 `go test`。Dockerfile 有哈希缓存，仅在自身变更时重建。
- `ci/android.Dockerfile` 里 SDK 平台包名原先写成 `platforms;android-37`，但 Google 仓库中**不存在该包**（只有 `android-37.0` / `37.1` / `37.2`），镜像构建在 `sdkmanager --install` 处以 `Warning: Failed to find package 'platforms;android-37'` 退出。现改为 `platforms;android-37.0`，与 `.github/workflows/*.yml` 中的声明保持一致。
- 镜像换好后门禁又卡在「中转服务测试」：`go test -race` 需要 cgo，容器里默认 `CGO_ENABLED=0`（`go: -race requires cgo; enable cgo by setting CGO_ENABLED=1`）。Dockerfile 补装 `gcc` / `libc6-dev`，stage 里显式 `export CGO_ENABLED=1`——`.github/workflows/remote-relay.yml` 能在 `ubuntu-latest` 上跑通同样是因为那边默认有 gcc。
- `main` 的 `push` 门禁漏掉了「中转服务测试」这一关（只 `pull_request` 有），代码合入 main 后 Go 侧再无校验。现补上，两条门禁 stage 一致。

#### 说明

- 未改任何业务代码，仅替换编译门禁的构建环境来源。

### 清理 third_party 冗余产物

#### 变更

- 删除从未被引用的 `nextlib-media3ext 1.10.0-0.12.1`（非 `-fongmi-softload`）AAR 与 POM：`gradle/libs.versions.toml` 自 5.5.60 起只指向 `-fongmi-softload`，且两者的 `FfmpegVideoDecoder`/`FfmpegVideoRenderer` 并不通用（softload 版多出 `lowres`/`skipFrame`/`skipLoopFilter` 参数与 ABI 入参），保留只会误导后续升级。
- 删除 `third_party/nextlib-media3ext-compat/` 孤儿目录：其中的 `NextRenderersFactory.java` 是 `softload` AAR 的反编译参考副本，未被任何构建脚本、settings 或源码引用，且与线上 AAR 已有实现重复。
- 删除本地 Maven 中 19 个 `-sources.jar` / `-javadoc.jar`（4.3 MB）：仅供阅读，构建不消费；同步清理 18 个 `.module` 中对应的 `SourcePublication` / `JavaDocPublication` 变体，避免元数据指向已删文件。

#### 说明

- 仅动 `third_party/`，未改任何 Java/Kotlin/Gradle 源码；`.module` 交叉校验通过，引用文件 0 缺失。
- `-fongmi-softload` 的 AAR、POM、`.module` 原样保留。

## 5.15.0 — 修复全新安装后首页空白，并补齐 TV 端精简版 (2026-10-07)

#### 修复

- **修复全新安装后首页推荐不渲染**（issue #11 反馈：lite 版在 Android 14 上首页空白，且完整版同样复现）：`DependencyTrust` 在冷启动时若拿不到已 resumed 的 Activity 就直接判定"不信任"，而 `App.activity()` 在应用尚未 resume、或首次启动的权限引导把用户带进系统设置（Activity 被 pause）时为空；远程 jar 于是被静默拒绝。更致命的是 `JarLoader` 用 `computeIfAbsent` 把这次失败**当成结果永久缓存成 `SpiderNull`**——所有 `csp_` 站点（含首页推荐）在整个进程生命周期内都再也加载不出来，且不会再弹确认框，只有杀进程重来才可能恢复。现做三处修复：
  - 信任检查改为**等待 Activity 就绪**（最多 30 秒）再弹框，而不是立即静默拒绝；
  - `JarLoader` / `JsLoader` **不再缓存 `SpiderNull`**，加载失败可重试，不再把一次竞态失败变成永久故障；
  - 用户确认信任后**主动刷新首页**，不必手动重载配置即可看到内容。
- "拒绝"只在本次进程内记住：既不会因为首页自动重试而变成每隔几秒弹一次的骚扰，重启后也仍可重新确认。

### 新增

- **TV 端精简版**：新增 `leanback-lite-universal.apk` 与 `leanback-lite-arm64_v8a.apk`，与手机端精简版一致——去掉 MPV 播放内核与 Python 运行时，保留 EXO/IJK 双内核、QuickJS/JS 源与全部协议能力。至此精简版共 4 个包（手机 2 + TV 2），完整版 6 个包照常发布，一个版本共 10 个安装包。

#### 说明

- 精简版不含 Python 运行时，**Python 类型直播源（如央视频/央视官网/广东广电官方解析源）不可用**，需要这类源请装完整版。
- 更新清单与下载页已同步覆盖 10 个包。

## 5.14.1 — 应用内更新断点续传与重试加固 (2026-10-05)

#### 修复

- **修复弱网下应用内更新反复失败**：更新器此前没有断点续传——下载一中断就删除已下载部分，换源后也从第 0 字节重来；大体积安装包（完整版 113~120MB）在网络抖动叠加 GitHub 不可达时几乎没有生还空间。现改为：失败保留部分文件，重试与换源均通过 HTTP Range 从断点继续；同一源最多重试 3 次，耗尽后换下一源并保留进度。
- 续传全程有护栏：服务器返回 206 时校验 `Content-Range` 起点；返回 200（忽略 Range，或 CDN 挑战页）先做体积校验再决定是否整体重下；416 或 Range 不匹配自动丢弃部分文件从头下载；清单无体积信息时不续传；下载完成后仍走既有的体积/SHA-256/签名/包名校验，损坏文件被识别后清档重来。
- 上次会话中断遗留的半个 `update.apk` 会被复用：再次触发更新时从断点继续，不再重新下载整个文件。

## 5.14.0 — 新增手机端精简版安装包 (2026-10-05)

### 新增

- **精简版 APK**（issue #11）：新增 `mobile-lite-arm64_v8a.apk`（约 56MB）与 `mobile-lite-universal.apk`（约 88MB），对比完整版的 114/120MB 减少约一半。精简版去掉 MPV 播放内核（约 37MB）与 Python 运行时（约 21MB），保留 EXO/IJK 双内核、QuickJS/JS 源、迅雷/秘恰等全部协议能力；播放内核选择器在精简版中隐藏 MPV 选项，存量偏好自动回落 EXO。完整版 6 个安装包照常发布，功能不变。
- 构建系统新增 `edition` 维度（full/lite），Python 依赖改为 full 专属，lite 以编译桩保证同代码库双形态；MPV 原生库按 ABI 经 `addStaticSourceDirectory` 注入 full 变体。

#### 说明

- 精简版与完整版包名相同，互相覆盖安装；应用内更新按 `mobile-lite-*` 独立清单分发，不会跨版本"升级"。
- 精简版两个包体积均低于 100MB，CNB 镜像可直连下载，应用内更新成功率更高。

## 5.13.1 — 修复打开视频必崩的空指针 (2026-09-30)

#### 修复

- **修复打开所有视频时崩溃**（issue #10）：release 构建中 R8 把多个 ViewBinding 类横向合并成一个持-holder 类，并且把 `bind()` 里"缺少视图即抛异常"的路径改写为存入 null 后继续，使 `VideoActivity` 初始化读取 OSD 子绑定字段时抛出 `NullPointerException`。现做两层修复：混淆规则禁止 ViewBinding 类参与合并，`bind()` 的错误信息得以保留；播放页对 OSD/音频台子绑定判空，缺失时降级为不显示悬浮信息而不是崩溃。
- **CI 附带 R8 mapping**：发布构建现在上传各变体的 `mapping.txt` 工件（保留 90 天），线上崩溃堆栈可以精确反混淆，不再依赖本地环境复现。

## 5.13.0 — 自托管公网遥控与局域网播放控制 (2026-09-30)

### 新增

- **自托管公网遥控**：手机与电视增加可选的 HTTPS 中转连接，默认关闭；支持限时单次配对、设备授权撤销、WebSocket 与 HTTP 轮询回退。
- **受限远程命令**：支持搜索、推送 HTTP/HTTPS 播放地址、播放/暂停/停止、上下集、循环、重播和读取播放状态；命令有有效期、授权校验和重复执行保护。
- **Go 中转服务与网页控制台**：随源码提供独立服务、持久化授权、测试与部署示例，不提供公共遥控服务器。部署时需自行配置 HTTPS，不能直接暴露 App 的局域网管理端口。
- **局域网管理页遥控**：新增本机/已配对远端播放状态和控制面板，支持手动配对；隐藏页面或切换目标时停止旧状态请求，离线时退避。

### 修复与文档

- 局域网设备凭据不再随管理设备列表返回，远端请求限定目标与路径，避免通过重定向或地址前缀误匹配泄漏凭据；兼容 `loop` / `repeat` 控制名称。
- 更新 README、GitHub Pages 与完整开发文档，纠正旧文档中“管理接口无鉴权”的描述，区分局域网控制与自托管公网遥控。
- 更新清单使用 JSON 序列化生成简要版本说明，并检查六种 APK 是否齐全，避免特殊字符破坏清单。

### 使用边界

- 本版不包含完整云同步、媒体离线下载、远程安装、任意文件访问、登录态提取或网络代理。
- 遥控需要设备在线；Android 强制停止、后台限制或没有活动播放器时不能保证执行。命令受理不代表已经播放，应以命令结果和状态为准。

## 5.12.3 — 补齐单元测试基线，并修掉它反查出的两个历史缺陷 (2026-09-21)

#### 修复

- **修复点击异常历史记录崩溃**：`History.getVodId()` 直接取 `getKey().split(SYMBOL)[1]`，key 中缺少分隔符时数组只有一项，抛 `ArrayIndexOutOfBoundsException`。调用点覆盖主页续播、收藏页、历史进入播放页与片单浏览，也就是说一条 key 异常的历史记录被点到就崩。现按长度取值，缺字段返回空串。
- **修复历史列表观看进度不刷新**：`History.isSameContent()` 只比较片名、封面与创建时间，而它驱动 DiffUtil 决定行是否需要重绑。因此仅播放进度、时长或选集发生变化而创建时间未变时，记录被判定为内容相同、界面不更新。现补齐 `position`/`duration`/`vodRemarks` 比较（本仓库 `History` 无 `wallPic` 字段，故不引入该项）。
- **修复解析规则静默丢失**：站源里 `"type"` 写成字符串（如 `"1"`）时，Gson 无法反序列化为 `Integer`，整条解析规则会静默消失。移植上游 `ParseTypeAdapter` 做宽容解析，非数字与空值回落为 0。

### 测试

- 从上游搬入 116 个纯 JUnit 单测，`app/src/test` 由 14 个文件增至 130 个，CI 单次运行约 930+ 个断言。筛选条件是不引用 android/robolectric 且被测生产类在本仓库存在；再按能否编译剔除 18 个（针对上游已分叉的类签名）。同名文件不覆盖本仓库已有测试。
- 上述历史相关缺陷正是这批测试第一次运行即反查出来的（`HistoryTest` 4 例失败），且以修正代码而非放宽断言的方式收敛。
- 另剔除 2 个通过不了的搬入测试（`MpvPreloadControllerTest`、`AutoRebufferPolicyTest`）：它们针对上游已增强的预加载门控与降速恢复档位，本仓库对应类是较早实现，让断言通过需要连带改动缓冲行为，而这类改动必须真机用真实流验证，故列为待评估项而非静默改代码。

### 其他

- 清理 `third_party/maven` 中零引用的 media3 `1.10.1-fongmi` 版本目录（17 个模块、340 个文件、约 10MB）。当前依赖锁定在 `1.11.0-alpha01-fongmi`（`gradle/libs.versions.toml:41`），全仓无任何引用。

## 5.12.2 — 修复应用内更新不可用，并把 CI 的宿主 Python 依赖从 Launchpad 摘掉 (2026-09-21)

本版取代 v5.12.0（其更新清单损坏，见下），功能改动与 5.12.0 完全相同。v5.12.1 因构建基础设施故障两次未产出产物，从未发布，仅留下一个空 tag。

- **修复应用内更新被判无效**：v5.12.0 的六份更新清单里 `versionName` 是注释文本而非版本号。清单生成用 `grep -m1 'versionName' app/build.gradle` 取第一个匹配行，而 5.12.0 在 `app/build.gradle` 第 17 行新增的解释注释里带有 `versionName` 字样且位置更靠前，于是抢先命中。`Updater` 会把清单 `versionName` 与下载到的 APK 实际版本比对（`Updater.java:320`），不一致即判定更新无效，六个变体的更新因此全部卡死。现改为锚定到声明行提取（`versionCode` 同步处理），并把触发问题的注释去掉裸字段名。发版 preflight 里的提取本来就是锚定的，所以 tag 校验通过、只有清单坏掉。
- **CI 宿主 Python 改由 `actions/setup-python` 提供**：Chaquopy v17 只需要 PATH 上有一个宿主 `python3.10`，此前靠 deadsnakes PPA 安装，而它的 Launchpad 签名密钥会整段无法解析（`GPGKeyTemporarilyNotFoundError`），使发版与主干同时变红——v5.12.1 的两次构建即因此失败。改为 GitHub 侧 Python 发行版，并在其未暴露 `python3.10` 这个名字时按同一 bin 目录补软链；CI 与发版两条流水线同改。

## 5.12.0 — EPG 节目提醒上线 + 播放链路崩溃与远程接口加固 (2026-09-21)

### 新增

- **EPG 节目提醒**：直播节目单里点击**未开播**的节目即可预约提醒（提前 5 分钟通知），再次点击取消；预约状态持久化在数据库，重启后 `rebuildFromStorage()` 自动重挂闹钟。此前表结构、DAO、`BroadcastReceiver`、`AlarmManager` 逻辑与开机自启全部已存在，但没有任何 UI 入口调用，属于用户按不到的死代码，本版接通。
  - 补充 `SCHEDULE_EXACT_ALARM` 权限与独立通知渠道 `epg_reminder`（此前 `EpgReminder.createChannel()` 从未被调用，通知无渠道可用）。
  - 无精确闹钟权限时不再静默失败：直接跳系统「闹钟与提醒」授权页并给出提示；三语文案（en / zh-rCN / zh-rTW）。
  - 改用 `AlarmManagerCompat.canScheduleExactAlarms()`：原代码直接调用 API 31 才有的方法，而 `minSdk = 24`，在接通 UI 后会让 Android 7–11 设备在预约与开机重建时抛 `NoSuchMethodError`（外层只 `catch (Exception)`，拦不住 `Error`）。

#### 修复

- 修复电视端切换线路可能崩溃：`mBinding.flag` 的选中回调在 Leanback 布局过程中直接执行整条换源链路（`onItemClick` → `seamless`），且未校验页面是否已销毁。现改为上游同款 `FlagSelectionListener`——`post` 到布局完成后执行、校验视图仍附着且选中项未被更新列表顶替，`onItemClick` 增加 `isFinishing()/isDestroyed()` 守卫；顺带修掉选中位置为 `-1` 时 `get(position)` 越界的隐患。
- 修复缺少 `AndroidKeyStore` provider 的设备上云备份 token 无法保存：`key()` 直接抛异常导致整条 GitCloud 备份不可用。现回退到 `noBackupFilesDir` 下的本机 AES 密钥（`local-v1:` 前缀标识），旧密文仍可正常解密，且密钥落盘后会回读校验，校验不过宁可不保存。
- 修复三处播放页生命周期空指针：`PlaybackActivity.seekTo()` 只判了 `mController` 却经 `player()` 解引用可能已解绑的 `mService`；手机端与电视端导入 LUT 预设时在工作线程读文件后 `App.post` 回来使用 `player()`/`mBinding`，期间退出页面即崩（与 5.10.6 同型）；`onActivityResult` 用 `service()::dispatchNext` 形式传入方法引用，接收者在调用点求值，外部播放器返回时服务未绑定或控制器构建失败（5.11.1 起为受支持状态）即 NPE。
- 修复远程控制与网页桥接在播放器释放后调用：`/action` 控制指令与 WebHome `control` 都在工作线程判空后把 service 捕获进 `App.post`，而 `PlaybackService.onDestroy()` 会先 `player.release()` 再清空引用，落到主线程时可能对已释放的播放器下指令。现统一在主线程重新获取 service 并判空。
- 修复 `/media` 端点可能永久占用工作线程：等待主线程快照的 `future.get()` 没有超时，主线程繁忙时连接与线程都不回收，改为 2 秒超时返回空对象。
- 修复自更新镜像自适应失效：`Updater.copyAndOpen()` 的 CNB 可达性探测是同步网络请求，而三个调用点全在主线程回调里，`NetworkOnMainThreadException` 被吞成「探测失败」后恒定丢弃 CNB 直链、退回 GitHub 下载地址——对 GitHub 不通的网络正好拿到不可用链接。现把探测移到工作线程，结果回主线程再拉起安装器。

### 工程

- 新增 `CI` 工作流：main 分支 push 与 PR 编译电视端/手机端 Debug 并跑单元测试（此前仓库全部 12 个 variant 的构建与测试只在打 tag 时执行，主干腐化要等到发版才暴露）。
- 发版工作流增加 `preflight` 前置校验：tag 与 `versionName` 不一致直接失败（应用内更新按「所有源取最高版本」选择，tag 超前会向全部用户推送一个不存在的版本）、Release 构建缺少签名 secrets 立即失败而不是等完整编译后在 Gradle 抛错。
- APK 现在自带溯源信息：`BuildConfig` 新增 `GIT_REVISION`/`GIT_STATE`/`BUILD_TIME`，并显示在「关于」弹窗，用于定位用户反馈对应哪个提交打出的包。
- 删除 `webhome-devkit/skills/upstream-integration-governor/SKILL.md`：该文件与上游 `.codex/` 下的副本逐字节相同，但引用的 `task_guard.sh`、`verify_upstream_checkpoint.sh`、依赖合并评估文档四个路径在本仓库全部不存在，会误导加载它的 agent。

## 5.11.2 — 修复横屏手势分区与锁屏方向 (2026-09-09)

- 修复横屏下「左侧亮度 / 右侧音量」手势分区仍按竖屏尺寸划分：手势分区改用播放窗口自身尺寸计算（与触摸坐标同一空间，旋转即时生效），不再依赖系统方向配置；亮度/音量的滑动行程同步统一为当前窗口高度，横竖屏手感一致。
- 修复横屏点击「锁定屏幕」后被切回竖屏并锁死：锁向判定改为完全信任 `Display.getRotation()`（窗口系统当前真实旋转），不再读滞后的方向配置——旋转后立即锁定也会锁定在当前方向。直播播放页同步修复。
- 附带修复 `getScreenWidth/Height` 在旋转过渡期返回错误边长的问题（窗口尺寸直接取当前窗口边界，不再按方向取长/短边）。

## 5.11.1 — 修复点播放报错弹窗与退出崩溃 (2026-09-09)

- 修复部分设备（如 OPPO PKC110 / Android 16）点击播放即弹出「播放器初始化失败，请重试」并中断播放：MediaController 只是播放服务的 UI 状态镜像（进度条/播放按钮用），其偶发构建失败在 5.11.0 被误当作播放错误处理，触发了「停止播放 + 换源链路」。现改为静默降级并自动重试一次，实际播放不再受任何影响（此为 5.10.7 的既有行为）。
- 修复伴随的退出崩溃 `NullPointerException in VideoActivity.onStop()`（用户上报：服务解绑后退出页面必崩，`Unable to stop activity`）：手机端与电视端 `onStop` 补齐播放服务空保护。
- 从 5.11.0 升级强烈建议；5.10.7 及更早版本不受此问题影响。

## 5.11.0 — 全面代码审计整改：安全加固 + 崩溃修复 + 体验打磨 (2026-09-09)

本版本基于五维代码审计（交互 / 业务逻辑 / 安全 / 代码质量 / 样式，共确认 85 项问题），完成全部高危与中危修复及大部分低危优化。发布前六个 APK 全量构建验证通过。

### 安全加固

- 修复 `/m3u8` 未认证 SSRF：端点纳入 IP 保护清单，代理目标强制公网校验（拦截内网/回环/云元数据探测）；顺带修复改写链接硬编码 9978 端口的隐患。
- `/webResource` 网关加固：响应加 `X-Frame-Options: SAMEORIGIN` + `CSP frame-ancestors 'self'`，外部恶意页面无法再借 iframe 获得回环 origin；CORS 回环白名单收紧到精确端口（不再接受缺省端口）；客户端接入「连接时」私网 DNS 过滤，封堵 DNS 重绑定 TOCTOU；私网判定补齐 CGNAT（100.64/10）、fake-ip（198.18/15）与 NAT64 网段。
- WebHome 桥信任收敛：`app.history` / `site.info` / `config.info` / `device.info` 及带凭据的网络请求现在要求主帧 bridgeToken——跨源 iframe 无法读取父窗口变量，无法再冒用受信页面身份读取观影历史、站点凭据或以设备 Cookie 发起请求；结果分块 id 加随机成分防抢读。
- 登录态同步链路加密：登录态档案（网盘 Cookie、GitCloud token）传输改为 AES-GCM，密钥取自对端配对 token，被动嗅探不再能直接接管网盘账号；还原侧补 zip 条目与总量上限；本地管理页发起的同步同样加密。旧版明文档案保持兼容。
- 本地服务器 token 比较改为常量时间；`169.254.*` 链路本地地址不再视为可信 LAN。

### 功能修复

- **一键同步修复**：同步请求此前不带服务端 token、必被 401 拒绝。新增设备「配对码」机制（两端同步弹窗互见配对码，输入一次即记住），App 内与网页端一键同步全链路恢复可用；反向推送自动携带本机 token。
- 修复集数列表为空时（详情失败/纯 URL 推送）手机上/下滑切集与电视端「上一集/下一集」按钮必崩。
- 修复设备扫描每次泄漏一个 64 线程固定池（现空闲 30 秒自动回收）。
- 修复 MediaController 构建失败被静默吞掉后的空指针（现在上报错误并提示）。
- 备份恢复防数据丢失：恢复全程单事务（中途失败回滚）；`force` + 空/损坏备份不再清空配置库（拒绝并报错）；损坏的备份文件不再崩溃（回调报错）。
- 数据库：迁移失败现在触发「保留现场 + 自动重建」而非崩溃循环；「继续观看」主键改为（配置 + 影片）复合主键并迁移，多配置共用同站点时进度不再互相覆盖；同步合并按「时间优先、同秒比进度」判定并保留旧记录中的进度，时钟偏差不再丢数据。
- 修复换源/换集/切内核/选字幕/解码切换后倍速被静默重置为默认的问题（用户倍速全程保持）。
- 配置导入失败不再留下空白首页：自动回退到上一个成功加载的配置；配置仓库（depot）自引用不再无限递归、清理时不再跨类型误删同 URL 的直播/壁纸配置。
- Git 云盘同步 push 前自动 `pull --rebase`，双端同时上传不再必然冲突；冲突时给出「先手动下拉同步」的可操作提示。
- 修复直播收藏分组在「无收藏组」直播源上的 ConcurrentModificationException 崩溃。
- 修复手动「检查更新」后点取消会永久关闭自动更新的问题（取消仅取消本次）。
- 搜索相关性阈值边界语义修正（6=不过滤），rank 计算去重。

### 交互与无障碍

- 配置中心切换配置、收藏页跨配置点播补齐进度提示与失败反馈（此前完全静默，可重复触发）。
- 电视端播放控制栏倍速/字幕/片头/片尾微调改为「短按移动焦点、长按连续调节」，修复上下键被吞导致的焦点死路。
- 播放控制栏 13 个图标按钮补齐 contentDescription（TalkBack 可用）。
- 中/繁语言包补齐 21 个缺失 key：画质调节弹窗、更新弹窗、音频预设、渲染模式等界面不再出现中英混排。
- 共享设置弹窗的开关控件补 TV 焦点态（选中可见）。
- 站点切换空列表、恢复损坏备份等场景补明确报错。

### 样式

- 管理页修复 `#app` 与 `.manage-app` 宽度特异性冲突，PC 端 1120px 宽布局恢复生效。
- 触控目标增大：文件树/选项复选框 24px、文件操作按钮 44px；移除 viewport 的 `user-scalable=0`（恢复缩放自救）。
- 播放 OSD 四角文本补 `maxLines/ellipsize`，长媒体信息不再叠压画面；全量清理 19 个布局中废弃的 `singleLine` 用法。
- 删除 7 个未引用 drawable。

### 已知保留项

- APK 推送链 `sha256` 参数仍为可选（保持与旧版接收端兼容）。
- 双端 VideoActivity 约 2200 行重复、三内核引擎桥接方法重复等大型重构，以及 Android 端颜色 token 化，列入后续版本。

## 5.10.7 — 修复点击播放即崩溃（无限递归） (2026-09-08)

- 修复手机/电视端进入详情页后点击播放即崩溃（StackOverflowError）：音频舞台控制器的 `Host.service()` 实现误写成 `return service();`——匿名内部类里这会解析到**自己**，形成无限递归。改为 `return VideoActivity.this.service();`。全仓扫描确认无其他同类写法。
- 该缺陷在 v5.10.5 之前被 `setLut` 的 onCreate 崩溃（5.10.6 已修）掩盖，播放链路修复后暴露。

## 5.10.6 — 修复进入详情页即崩溃 (2026-09-08)

- 修复 v5.10.3 起手机/电视端进入视频详情页必崩的问题：LUT 按钮文案刷新（`setLut`）在 onCreate 阶段经 `player()` 读取播放服务字段，而播放服务此时尚未绑定 → NullPointerException。改为直接使用静态 `LutSetting.getButtonText()`，不再依赖播放器实例（R8 反混淆定位：崩溃点 `i2 = setLut`，调用链 `onCreate → initView → setVideoView → setLut`）。

## 5.10.5 — 修复下载完成后不跳转安装页 (2026-09-08)

- 补声明 `REQUEST_INSTALL_PACKAGES` 权限：缺少它时，Android 8+ 触发系统安装器会被静默拦截（部分 ROM 表现为"什么都没发生"）。
- 修复安装包签名校验：Android 9+ 的 `getPackageArchiveInfo(GET_SIGNING_CERTIFICATES)` 对**未安装的 APK 文件**经常返回空签名，导致校验误判"签名不匹配"并静默拒绝；现在自动回退到 `GET_SIGNATURES` 解析安装包签名（对文件可靠）。
- 安装环节失败不再静默：解析不到安装器时明确提示，并回退复制下载链接。

## 5.10.4 — 更新检查免疫单一源过期 (2026-09-07)

- 修复更新检查的多源判定缺陷：此前"第一个请求成功的源"直接生效（哪怕是 CDN 边缘缓存的过期清单）。现在并行对比所有源（所选源/服务器/GitHub/CNB），**取版本号最高者**，任何单一源返回过期内容都不再影响检测。
- 配套服务器侧修复：源站 nginx 对更新清单返回 `Cache-Control: no-cache`、APK 返回 `max-age=300`，CDN（EdgeOne）不再长期缓存清单。

## 5.10.3 — 修复手机首页顶栏图标重叠 (2026-09-04)

- 修复手机端首页顶栏：v5.10.0 引入的「APK 推送」「推送播放」两个菜单项以常驻图标显示，把工具栏标题（换源入口）挤压得与搜索按钮重叠。两项改为收进溢出菜单（⋮），顶栏恢复原 5 图标布局。

## 5.10.2 — 自建服务器镜像（更新下载全面提速） (2026-09-04)

- 新增自建服务器镜像（pan.imotao.com，HTTPS + CDN）：发版时 GitHub Actions 自动把 6 个 APK + 清单 rsync 到服务器；服务器无单文件 100MB 限制，arm64/universal 也能走国内直连。
- 应用内更新默认路由变为：**自建服务器 → GitHub（可套代理）→ CNB**，逐路自动回退；「检查更新」同样多源依次尝试（当前所选源 → 服务器 → GitHub → CNB），任一可用即成功。
- 「更新下载设置」新增「下载源」选择：自动（推荐，国内走服务器、海外走 GitHub）/ 服务器镜像 / GitHub / CNB。
- 无播放功能变化。

## 5.10.1 — CNB 镜像自适应（修复 raw 超限） (2026-09-04)

- 修复镜像同步对 CNB 限额的误判：CNB raw 上限为十进制 100 MB（100,000,000 字节），旧检查按二进制 MiB 计算，导致 arm64/universal 包自 v5.9.x 起就实际越线（raw 下载报 `errcode 2000033`）。
- 超限 APK（v5.10.1 中为 arm64 与 universal 共 4 个，含 tensorflow-lite/jgit 带来的体积增长）不再同步到 CNB，CNB 镜像仅保留放得下的包与全部清单。
- 更新清单新增 `cnb` 字段标记该包是否有 CNB 镜像；应用内更新据此跳过必败的 CNB 路由，直接走 GitHub（可在「更新下载设置」里配 GitHub 代理加速）。
- 无功能变化，仅发布链路与更新路由修复。

## 5.10.0 — 批量对齐上游与 WebHTV（8 个功能批次） (2026-09-03)

本版本将上游 FongMi/TV v5.6.3 与 fish2018/webhtv v5.6.0 的可用增量整体并入，共 8 个提交、约 300 个文件。全程保持既有定制（家庭过滤、ServerAuth、强制签名、targetSdk 37、经典绿色界面）不变。

### 来自上游 FongMi（播放正确性与体验）

- data:URI 图片本地代理：base64 海报经 `/image/<md5>` 16MB LRU 缓存提供，修复部分配置海报/封面与 MediaSession 封面不显示。
- 退出首页自动轮换备份：`.tv` 格式、保留 7 份、旧 `tv-*.bk.gz` 自动迁移；恢复对话框列出新格式。
- 字幕/音轨语言智能匹配：zh-Hans/Hant 脚本级评分选轨。
- Exo 跳过静音（倍速设置面板开关）；补齐 mpv TLS CA 资产 `cacert.pem`（vendored MpvPlayer 期望但缺失）。
- YouTube 提取器升级：HLS 优先、音视频分段重建 DASH MPD、字幕轨；Dolby Vision 输出选项接入 mpv（SPDIF 直通已有）。
- 外挂字幕字体导入（SAF 导入 ttf/otf，喂给 mpv fonts.conf / sub-font）。
- 长按倍速面板、音效面板补档位（响度/10 段 EQ/稳定/语音增强/增益/平衡/声道模式）、画质面板 14 个预设芯片、leanback 分享入口。

### 应用内更新（原复制链接升级 → 真下载）

- 应用内下载 APK + 进度 + 速度，SHA-256/大小/包名/签名四重校验，GitHub 代理 → GitHub → CNB 多路回退；失败回退复制链接。
- 「更新下载设置」（GitHub 代理预设/自定义）入口在关于对话框；CI 清单升级为含 versionName/size/sha256。

### Git 云备份（webhtv）

- gitcloud 全家桶：GitHub/CNB provider、JGit 引擎、GitCloudDialog（账号/仓库/文件树/上传下载/恢复）、AppBackup 全量备份；入口在增强功能。

### 管理页（webhtv）

- 新端点：文件树、接口配置管理（切/删）、登录态 6 个、代理建议 2 个；管理页新增登录态、接口配置、目录树、代理建议 UI；webtv 安全面板与 token 鉴权保留。

### 站点注入与 WebHome 扩展（webhtv）

- 站点注入：「识别」粘贴多段 JSON 自动归类（含远程 live 脚本探测）、本地文件导入、拖拽/置顶排序、WebHome 站点级扩展文件选择器。
- WebHome 扩展：按站点多选 / CSP key 正则匹配作用域；调试台可选脚本并保留匹配范围；旧数据零迁移兼容。

### 性能面板与 LUT 调色（webhtv）

- 性能面板 UI（四档配置 + 逐内核参数，接 webtv 自有目录 API）。
- LUT 调色全链路打通：LUT 引擎首次接入 PlayerManager（MPV 原生 shader + EXO 视频效果，换内核自动恢复）、LutDialog/LutPanelDialog/LutQuickPanel、13 个内置 .cube 预设、自定义目录。

### UI 杂项（webhtv）

- APK 推送安装 + 推送播放（手机 → 设备，含服务端 /action 分支与进度恢复）。
- 360kan 三列热搜（电视剧/电影/综艺）；TV 悬浮 OSD 面板设置项；快速搜索浮窗（含搜索进度）；毛玻璃退出确认；leanback 控制面板；leanback 睡眠定时；播放器按钮自定义排序/显隐。
- 网盘测速诊断 + 上次异常退出日志。

### 大块功能（webhtv）

- 歌词/桌面歌词/沉浸音频音乐模式：7 家歌词源、逐字歌词、音频舞台（唱片机背景、队列加歌、歌词搜索）、桌面歌词悬浮窗。
- KTV 伴唱：TFLite BasicPitch 音高提取（含 DSP/YIN 降级）、麦克风录音评分三档难度、UltraStar/USDB 等曲库源、结算弹窗。
- 播放器调优策略类成套并入（110 个文件）：补齐 mpv seek/track 刷新策略与 Exo HttpEofRecovery/缓存观测接线；其余策略按「webtv 自有策略优先」原则入库待用。

### 其他

- webhome-devkit 文档/模板/技能并入；开发文档合并至 webhtv 当前版（保留 webtv 专属章节）。
- mobile 直播线路九宫格、多日节目单弹窗。
- 依赖保守升级：appcompat 1.8.0、lifecycle-service 2.11.0、glide 5.0.9、guava 33.7.1、NewPipeExtractor v0.26.5、okhttp 5.5.0（AGP 维持 9.2.1）；新增 jgit、tensorflow-lite 2.17.0。
- 修复：`shape_accent` 引用的 `selector_button/selector_stroke` 颜色从未定义（CI 全新构建必炸的暗雷）、`Backup.redact` 对 gson 2.14 宽松解析的防线失效、`BackupManager` 异常捕获兼容。
- 未并入：Exo DoVi 原生 GPU 渲染器（需 C++/NDK 构建链，本机与 CI 均未配置，且 mpv 层已有 DV 输出路径）。

## 5.9.6 — 恢复经典绿色界面 (2026-08-18)

- 恢复旧版 `wallpaper_1` 绿色壁纸作为默认背景，并安全迁移此前默认的梦幻紫霞壁纸。
- 固定完整的 Material 3 绿色语义色板，清除底部导航、FAB、列表选中态和标签的默认紫色容器。
- 保留半透明黑卡片、白色文字、TV 白色焦点框和自定义壁纸能力，不恢复动态主题功能。

## 5.9.5 — 移除主题色动态染色 (2026-08-18)

- 移除手机端主题色选择、壁纸取色和 DynamicColors 自定义种子，恢复固定高对比配色。
- 保留壁纸显示与 `wall` / `wall_type` 设置，电视端不引入动态主题。

## 5.9.2 — 弹幕状态二次修复 (2026-08-18)

- 分离弹幕加载与显示偏好，设置页总开关同步两者，实际启用统一为 `load && show`。
- 播放器 controller 替换时清理旧实例，并原子恢复配置、启用状态与当前选中弹幕源。
- 统一电视、手机、直播与投屏的连接和播放器重建行为；无弹幕源时仍可关闭显示。
- 云搜结果绑定播放 generation 与当前集上下文，拒绝已切集或已取消后投递的旧回调。
- 外部弹幕与文件弹幕只选择来源，不再自动开启显示。
- 新增纯 Java 弹幕状态与 generation 回归测试。

## 5.5.97 — 配置导入 NPE 修复（真机验证发现） (2026-08-12)

真机验证暴露：清数据后重新添加接口配置时，`ConfigImport.preview*` 对可能为 null 的 `config.getName()` 调用 `.isEmpty()` → NullPointerException → "导入校验失败"。

### 修改

- `ConfigImport.previewVod/previewLive/previewWall`：将 `config.getName().isEmpty()` 全部改为 `TextUtils.isEmpty(config.getName())`（对 null 安全）

#### 说明

- `Config.create(type, url)` 不设置 name，重新添加接口时 name 为 null 触发此 bug
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.96 — WebHome 视口注入修复（真机验证发现） (2026-08-12)

真机验证暴露问题：WebHome 页面加载后 `window.__fmViewport` 为 undefined、`--fm-*` CSS 变量为空。根因：页面加载前的布局变化先用 EMPTY 视口锁定了 `lastViewportKey`，页面加载后 `injectViewport` 因 key 相同被去重跳过，注入从未落到已加载页面。

### 修改

- `HomeWebController.onPageFinished`：页面加载完成后重置 `lastViewportKey = null`，强制对就绪页面重新注入视口

#### 说明

- 修复后 WebHome 页面可获得 `window.__fmViewport` 与 `--fm-safe-*`/`--fm-chrome-mode` 等视口信息
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.95 — WebHome 集成（Step6：Raw 适配 + 内嵌点播） (2026-08-12)

WebHome 集成专项**第六步**：移植并接入 `WebHomeRawAdapter`（raw 内容代理）与 `WebHomeInlineVodStore`（内嵌点播）。

### 新增

- `web/WebHomeRawAdapter`：WebView raw 内容代理（git 仓库内容经 OkHttp 拉取 + 缓存 + 类型适配），接入 `HomeWebController.shouldInterceptRequest`（加载页面时创建、销毁时清除）
- `web/WebHomeInlineVodStore`：内嵌点播存储（Vod 内容按 id 存储，供播放器取用）

### 修改

- `HomeWebBridge`：新增 `player.playVodInline` 分发 + `playVodInline`（存内嵌点播并打开播放器）+ 简单 `resolveInlineEpisode`（直接 mediaUrl 的集数可解析）
- `HomeWebController`：JS 桥暴露 `player.playVodInline`

#### 说明

- `WebHomeRawAdapter` 仅对 git 仓库作用域内的 raw 请求生效，不影响其他请求
- 内嵌点播支持**直接 mediaUrl** 的集数；`pageUrl` 需 JS 集数解析的场景暂优雅降级（后续可补 JS 往返解析）
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.94 — WebHome 集成（Step5：openVod 接线） (2026-08-12)

WebHome 集成专项**第五步**：接通 WebHome 页面 → 原生内容的 `openVod` 通道。

### 修改

- `HomeWebController.openVod()` → `Listener.openVod()`；JS 桥 `window.fongmi.app.openVod` 暴露
- `HomeWebBridge`：新增 `app.openVod` 分发 → `App.post(controller::openVod)`
- mobile `VodFragment.openVod()`：退出 WebHome 全屏并切回原生首页内容（`homeContent`）

#### 说明

- WebHome 页面可调用 `app.openVod()` 切回原生点播内容；安全非破坏（新增方法/hook，不影响既有调用）
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.93 — WebHome 集成（Step4：宿主真实视口） (2026-08-12)

WebHome 集成专项**第四步**：宿主从窗口 insets 构建**真实安全区视口**并注入 WebHome 页面（此前视口为 EMPTY）。

### 修改

- mobile `VodFragment`：`ViewCompat.setOnApplyWindowInsetsListener` 监听窗口 insets，构建 `WebHomeViewport.from(insets, mode, 0)`（全屏时 chrome 模式为 `immersive`，否则 `normal`）并经 `applyWebHomeViewport` 注入

#### 说明

- 本步让 WebHome 页面获得**真实的安全区/状态栏信息**（`--fm-safe-*`/`--fm-chrome-mode` 等 CSS 变量），配合 Step2/3 的视口管道
- 仅读取 insets，不做系统栏隐藏操作，安全非破坏
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.92 — WebHome 集成（Step3：视口管道） (2026-08-12)

WebHome 集成专项**第三步**：补全视口管道，让宿主可向 WebHome 控制器喂入视口数据（实际安全区/chrome 信息）。

### 修改

- `HomeWebController` 新增 `setViewport(WebHomeViewport)` / `getViewport()`（外部可注入视口并触发重新注入）
- mobile `VodFragment`：`getViewport()` 返回当前视口；新增 `applyWebHomeViewport(WebHomeViewport)` 接入 `mWeb.setViewport`

#### 说明

- 本步为**视口数据管道**（host → 控制器 → 页面 `fmviewport`），不涉及系统栏操作，安全非破坏
- 真实安全区数据的计算（基于窗口 insets）与 chrome 模式（edge/immersive 隐藏系统栏）属更深 UI 子系统，后续单独评估
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.91 — WebHome 集成（Step2：视口注入 + chrome 管道） (2026-08-12)

WebHome 集成专项**第二步**：把简化版 `fmviewport` 注入升级为 `WebHomeViewport` 驱动的丰富协议，并加入 `normalChrome`/`setChrome`/`restoreChrome` 管道。

### 修改

- `HomeWebController` 新增 `viewport`/`lastViewportKey` 字段
- `injectViewport()`：改用 `viewport.script(...)`（安全区/手势区/chrome 模式 CSS 变量 + `fmviewport` 事件），并加 key 去重
- 新增 `setChrome(JsonObject)` / `restoreChrome()` / `normalChrome()`（经 `Listener` 钩子，当前为 no-op）

#### 说明

- 视口协议更丰富（`--fm-chrome-mode`/`--fm-system-bars-hidden`/手势区等），即使 `viewport` 暂为 EMPTY 也输出完整结构
- chrome 模式尚未由宿主 Activity 实际应用（Step3）；`Listener` 钩子默认 no-op，现有行为不变
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.90 — WebHome 集成（Step1：chrome/视口基础） (2026-08-12)

WebHome 完整集成专项的**第一步**：移植 chrome 模式与视口（viewport）基础类，并扩展 `HomeWebController.Listener` 接口（非破坏性）。

### 新增

- `web/WebHomeChrome`：chrome 模式（normal/edge/immersive）常量与判定
- `web/WebHomeViewport`：安全区/手势区/chrome 视口信息构建（JSON/脚本/CSS 注入）

### 修改

- `HomeWebController.Listener` 新增 5 个 default no-op 方法：`applyDefaultChrome`/`setChrome`/`restoreChrome`/`getViewport`/`openVod`（不破坏现有实现者）

#### 说明

- 本步为**非破坏性基础**：接口方法均为 default no-op，现有 WebHome 行为不变
- 后续步骤将接入 chrome()/视口注入，并在宿主 Activity 应用 chrome 模式（edge/immersive）
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.89 — WebHome 扩展支持 git 原始地址解析 (2026-08-12)

从 webhtv 移植 `GitRawUrlResolver`（纯 Java URL 解析工具），并接入 WebHome 扩展的远程加载：远程扩展配置里填 `github.com/...`、`cnb.cool/...`、`gitee.com/...` 等 git 仓库地址时，自动解析为 raw 上游地址，扩展清单可正常加载。

### 新增

- `web/GitRawUrlResolver`：解析 GitHub / Gist / Gitee / GitLab / CNB / Gitea 等 git 托管地址 → raw 上游地址（含 scope 标识）

### 修改

- `WebHomeExtensionRegistry.loadRemote`：远程扩展 URL 先经 `GitRawUrlResolver` 转成 raw 地址再加载

#### 说明

- 纯 Java 工具，无 WebView 依赖；不影响已配置的 raw 地址或直接网址
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.88 — MPV 音频 DSP 完整链路（响度+稳定器+限幅器） (2026-08-12)

在 v5.5.87 响度归一化基础上，补全 MPV 音频 DSP：均衡器 + `loudnorm`（响度）+ `acompressor`（动态稳定/压缩）+ `alimiter`（输出限幅），全部为 ffmpeg/MPV 标准 `af` 滤镜。

### 修改

- `MpvPlayer.setAudioEqualizer` → 重构为 `setAudioDsp(freq, gainDb, loudness, compressorRatio, limiter)`，按需在 `af` 链追加 `loudnorm`/`acompressor`/`alimiter`；旧方法保留并委托
- `MpvPlayerEngine.applyAudioSetting`：`getStabilityAmount()` 映射为压缩比（1 + 稳定度×2.5），`shouldLimitOutput()` 启用限幅

#### 说明

- **默认关闭** → 默认用户 `af` 链与均衡器时代完全一致，零行为变化；仅开启相应音效时才追加对应滤镜
- `safeSetPropertyString` 静默吞异常：即使打包的 MPV 不支持某滤镜也只不生效，不崩溃
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.87 — MPV 音频响度归一化（loudnorm） (2026-08-12)

为 MPV 内核补充音频 DSP 的第一步：开启响度归一化时，在 `af` 滤镜链末尾追加标准的 `loudnorm` 滤镜（默认关闭，不影响默认用户）。

### 修改

- `MpvPlayer.setAudioEqualizer(freq, gain, loudness)`：`loudness=true` 时在均衡器链后追加 `,loudnorm`；旧的 2 参方法保留并委托
- `MpvPlayerEngine.applyAudioSetting`：传入 `config.isLoudnessEnabled()`；`clearAudioEffect` 清空

#### 说明

- **默认关闭** → 默认用户 `af` 链与 v5.5.84 完全一致（零行为变化）；仅在用户开启"响度"时才追加 `loudnorm`
- `safeSetPropertyString` 静默吞异常：即使打包的 MPV 不支持 `loudnorm` 也不会崩溃（仅该滤镜不生效）
- 响度/稳定/限幅的完整 DSP 仍需 mpvplayer audio 扩展，后续评估
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.86 — 播放记录删除事件 Webhook 补全 (2026-08-11)

补全播放记录同步的删除事件上报：此前删除记录**不触发** `playback.deleted` Webhook（Step2 时因 DeleteInput 未就绪而跳过）。现在删除/清空历史记录时向已配置的 Webhook 推送删除事件，配合删除墓碑防止远端复活。

### 新增

- `PlaybackRecord.deleted(input, cid)`：构建删除记录（scope/historyKey/siteKey/vodId + dedupeKey）
- `PlaybackEventCollector.onHistoryDeleted(input, cid)`：发送删除事件
- `PlaybackProgressWriter.notifyDeleted(History)` / `notifyCleared(int cid)`：工具方法

### 修改

- `History.delete()`（单条）/ `History.delete(cid)`（批量清空）：删除后触发删除事件 Webhook
- `PlaybackProgressWriter.deleteInternal`：`notify=true` 时触发 `onHistoryDeleted`

#### 说明

- Webhook 仅在已配置且同步开启时发送；删除事件受墓碑保护，远端不会复活已删记录
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.85 — 直播源加载失败提示 + startFlow 判空 (2026-08-11)

排查"直播电视无法加载"：直播源（尤其经 GitHub 代理的源）间歇性失效时，App 此前**静默显示空白**、无任何提示。本次改进：

### 修改

- `LiveViewModel.parse`：直播源加载失败时（非 ExtractException 的网络/解析错误）也通过 url 通道 post 出 `Result.error`，让 `LiveActivity.onError` 显示清晰错误提示（新增 `live_load_failed` 字符串，三语言）
- `LiveActivity.startFlow()`（mobile + leanback）：增加 `mChannel == null` 判空，避免直播源加载失败（尚无选中频道）时 NPE

#### 说明

- 直播源本身受网络/代理稳定性影响（如 gh.927223.xyz 等 GitHub 代理间歇 403/超时）；App 侧现在能明确提示"加载失败"，用户可切换到其他可用直播源
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.84 — MPV 内核音频均衡器支持 (2026-08-11)

为 MPV 播放内核补齐音频均衡器（此前仅 EXO 可用）：通过 MPV 原生 `af` 音频滤镜链（MPlayer 经典 `equalizer=f=<Hz>:g=<dB>` 语法）实现与 EXO 同款 10 频段均衡。

### 新增

- `MpvPlayer.setAudioEqualizer(frequenciesHz, gainsDb)`：把频段增益映射为 MPV `af=equalizer=...` 链；全零/空时清空 `af`
- `MpvPlayerEngine` 覆盖 `supportsAudioSetting()`/`applyAudioSetting()`/`clearAudioEffect()`，复用既有 `AudioEffectConfig`（频段 32Hz–16kHz，增益 dB = level/100）

#### 说明

- 仅均衡器频段部分（纯 MPV `equalizer` af，无 LUT 冲突）；响度/稳定/限幅等 DSP 仍走 EXO 路线
- `af` 为 MPV 标准滤镜语法，设置失败静默（不影响播放）
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.83 — 镜像历史重建：CNB 仓库不再膨胀 (2026-08-11)

根治 CNB APK 镜像仓库此前累积到 ~10 GiB 的问题：每次发布改为**孤儿分支重建历史**（只含最新 APK/JSON）并 **force-push** 到 main，历史从 ~600 MB 重新开始，不再随发布单调增长。

### 修改

- CI "Sync to mirror"：`git checkout --orphan` 重建 + `git push --force`，仅保留最新 6 个 APK + JSON
- APK 仍以 git blob 存储（CNB raw 才能服务真实文件）；LFS 已被证明会返回指针、破坏下载，不采用
- 防呆：dist 无 APK 时跳过重建，避免清空镜像

#### 说明

- force-push 失败时自动保持旧历史（`continue-on-error`，不影响发布）
- 首次生效后，镜像 main 分支历史即重置为最新发布的内容；CNB 侧旧对象由其 GC 回收
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.82 — 播放记录远端同步（Step4b，最后一块） (2026-08-11)

移植 webhtv 播放记录同步的最后一块：**远端同步引擎**，从自建中转服务拉取/合并播放记录与删除事件，支持增量游标与站点过滤。

### 新增

- `playback/`：`RemoteSyncConfig`（远端源配置：URL/token/站点过滤/间隔/游标）、`PlaybackRemoteSyncStore`（配置持久化）、`PlaybackRemoteSyncPayload`（响应解析：upserts/deletions/nextSince 多格式兼容）、`PlaybackRemoteSyncResult`、`PlaybackRemoteSyncer`（定时拉取 + 对账）
- `PlaybackProgressWriter` 新增远端变体：`applyFromRemoteSync`/`deleteFromRemoteSync`（带墓碑与远端时间戳校验，防旧写入复活/过期覆盖）

### 修改

- `App.startBackgroundServices` 启动 `PlaybackRemoteSyncer`（首次 3s、周期 5min，受同步总开关与隐身模式保护）

#### 说明

- 需自建中转服务（Cloudflare/Deno/Vercel/Go/Rust，见 webhome-devkit 文档）并配置远端源后才生效；无配置时静默不动作
- 至此播放记录同步四步全部完成：只读上报 / Webhook / 进度写入 / 删除墓碑 / 远端同步
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.81 — 更新下载回退：universal APK 超 CNB 限制时走 GitHub (2026-08-11)

universal APK（含 arm64+armv7 双 ABI 原生库）无法安全压到 CNB raw 的 100 MiB 限制内（lxml 为蜘蛛体系必需，不可裁剪）。改为**更新下载时自动探测**：CNB 对 APK 返回 4xx（413 超限）时回退到 GitHub Releases 下载。

### 修改

- `Updater.onConfirm`：对 CNB 的 APK URL 做 HEAD 探测，不可达（413/网络失败）时改用 GitHub Releases URL
- arm64/armv7（<100 MiB，CNB 可服务）仍走 CNB 快源；universal（超限）自动回退 GitHub

#### 说明

- 探测在后台线程执行，5s 超时，不影响主线程
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.80 — 镜像同步回滚 LFS（修复 CNB 下载） (2026-08-11)

v5.5.79 引入的 **git-lfs** 镜像同步，导致 CNB raw 对 `*.apk` 返回 **134 字节的 LFS 指针**而非 APK 本体，反而破坏了 App 内更新下载。回滚为 **git blob** 存储（CNB raw 才能服务真实文件），并保留"只留最新 APK"以控制镜像体积。

### 修改

- CI 镜像同步：移除 `git lfs track`，并清理残留 `.gitattributes` 的 LFS 行；APK 作为 git blob 推送
- 仍保留 `resConfigs`（arm64/armv7 APK 已 <100 MiB）与"只保留最新版本 APK"

#### 说明

- arm64/armv7 APK **99/86 MiB**（<100 MiB），CNB raw 可正常下载；universal（双 ABI）仍 102-103 MiB 略超限
- 镜像历史此前已累积 ~10 GiB，需在 CNB 仓库手动清理（本版起不再因 LFS 指针占用额外空间）
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.79 — 更新修复：APK 压缩至 CNB 100 MiB 限制内 (2026-08-11)

CNB 镜像的 raw 文件端点有 **100 MiB 硬限制**，此前 APK（100-105 MiB）通过 CNB 更新时报 `raw 文件大小 100 MiB 超过限制`。

### 修改

- `app/build.gradle`：`resConfigs "en", "zh-rCN", "zh-rTW"` 裁剪依赖的未用语言资源
  - arm64 APK：105 → **99 MiB** ✓（<100 MiB）
  - armv7 APK：87 → **86 MiB** ✓
  - universal（双 ABI）：103 → **102-103 MiB**，仍略超限（见说明）
- CI：新增 "Check APK sizes" 步骤，超限输出 warning
- CI 镜像同步：改用 **git-lfs** 存储 APK 并**只保留最新版本**（删除旧 APK），避免镜像仓库继续膨胀（此前累计到 ~10 GiB）

#### 说明

- universal APK 因同时含 arm64+armv7 双 ABI 原生库仍略超 100 MiB；arm64/armv7 设备（绝大多数，含 Pixel 7 Pro）已可正常通过 CNB 更新
- 已有 10 GiB 镜像历史需在 CNB 仓库手动清理（重写历史或重建镜像），本版起不再继续增长
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.78 — 播放记录删除 API（构建修复） (2026-08-11)

修复 v5.5.77 编译错误：`HistoryDao.delete(int, String)` 由 `void` 改为返回 `int`（删除处理需统计受影响行数；对忽略返回值的既有调用方源码兼容）。

## 5.5.77 — 播放记录删除 API + 删除墓碑 (2026-08-11)

从 webhtv-main 移植播放记录同步的删除能力：`DELETE` 语义的 `POST /api/playback/progress/delete`，配合删除墓碑防止旧写入复活已删记录。

### 新增

- `bean/PlaybackDeleteTombstone`：删除墓碑模型（config/scope/site/vod/时间戳）
- `playback/PlaybackDeleteTombstoneStore`：墓碑持久化（**Prefers 实现**，避免 Room 迁移）+ 90 天保留 + 单调最新判定
- `playback/PlaybackProgressDeleteInput`：删除请求（全量/站点/单条/多别名解析/confirm 校验）
- `PlaybackProgressWriter.deleteFromLocalApi`：本地删除（先记墓碑再删 History+Track）
- `HistoryDao` 补充 `findAll(cid)`

### 修改

- `PlaybackProgressApi` 新增 `/api/playback/progress/delete` 端点（批量）
- 写入路径增加墓碑检查：已删记录不复活
- `PlaybackProgressApplyResult` 补回删除相关重载

#### 说明

- 全量清理需 `confirm=true`，按站点清理需 `siteKey`；受同步总开关 + 本机写入开关 + 隐身三重保护
- 墓碑用 Prefers 而非 Room（webhtv 用 Room 实体需 DB 迁移，此处避免）；远端同步（Step4b）后续
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.76 — 播放进度写入 API（构建修复） (2026-08-11)

修复 v5.5.75 编译错误：`PlaybackProgressApi.doResponse` 补回 `catch (Throwable)` 以捕获 `readBody` 抛出的 checked `Exception`。

## 5.5.75 — 播放进度写入 API（/api/playback/progress） (2026-08-11)

从 webhtv-main 移植播放记录同步的**第三步**：新增本机播放进度写入 API，外部工具/爬虫可将播放进度写入本地历史。

### 新增

- `playback/`：`PlaybackProgressInput`（进度写入请求，含多别名解析/校验）、`PlaybackProgressApplyResult`、`PlaybackProgressBatchResult`、`PlaybackProgressWriter`（本地写入核心：匹配本地历史/按需新建/写盘）
- `server/process/PlaybackProgressApi`：`POST /api/playback/progress`（单条）与 `/api/playback/progress/batch`（批量），含 CORS
- `HistoryDao` 补充 `findByKeyPrefix(cid, prefix)` 查询

### 修改

- `server/Nano` 注册写入端点

#### 说明

- 受 `ViewingRecordSyncStore` 总开关 + 本机 API 修改开关 + 隐身模式三重保护
- 仅本地写入；删除/远端同步/墓碑因依赖额外存储与更复杂对账，暂未移植
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.74 — 播放记录 Webhook 上报 (2026-08-11)

从 webhtv-main 移植播放记录同步的**第二步**：新增 Webhook 上报能力，播放开始/进度/暂停/恢复/结束/停止时向配置的 Webhook URL 推送播放记录。

### 新增

- `playback/`：`WebhookConfig`（Webhook 配置，含字段预设 basic/standard/full/anonymous/custom）、`PlaybackWebhookStore`（配置持久化 + 失败熔断）、`PlaybackWebhookSender`（异步上报 + 重试 + 幂等头）、`PlaybackHttpHeaders`、`ViewingRecordSyncStore`（总开关）
- `PlaybackEventCollector`：采集播放事件（start/progress/pause/resume/stopped/ended）并触发 Webhook
- `PlaybackRecord` 补回 `withEvent()`/`copy()`；`PlaybackFieldPolicy` 恢复 `webhook()`/`anonymous()`/`custom()`

### 修改

- mobile/leanback `VideoActivity`：`getPlayer`/`onStateChanged`/`onPlayingChanged`/`onTimeChanged`/`onStop`/`onDestroy` 接线到 `PlaybackEventCollector`

#### 说明

- 本步仅 Webhook 上报；进度写入 API 与远端同步因依赖 DB/删除墓碑/远端配置，留待后续
- 默认关闭（`ViewingRecordSyncStore.isEnabled` 默认 true，但无 Webhook 配置时不发）；删除事件处理暂未移植
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.73 — 当前播放记录只读 API（构建修复 2） (2026-08-11)

修复编译错误：恢复 `PlaybackRuntime.playerFor` 方法（此前编辑误删），`PlaybackRuntime` 保持 public 供 `VideoActivity` 接线。

## 5.5.72 — 当前播放记录只读 API（构建修复） (2026-08-11)

修复 v5.5.71 的编译错误：`PlaybackRuntime` 由包私有改为 `public`，并公开 `setPlayer`/`updateHistory`，供 `VideoActivity`（其他包）接线。

## 5.5.71 — 当前播放记录只读 API（/api/playback/current） (2026-08-11)

从 webhtv-main 移植播放记录同步能力的**第一步**：新增只读的"当前播放"上报端点，供爬虫/外部工具查询本机正在播放的片源。

### 新增

- `playback/` 包：`PlaybackRuntime`（当前播放运行时单例）、`PlaybackRecord`（播放记录模型）、`PlaybackFieldPolicy`（字段策略）、`PlaybackConfigIdentity`（配置身份）、`PlaybackApi.current()`（只读获取当前播放记录）
- `server/process/PlaybackRecordApi`：暴露 `GET/POST /api/playback/current?siteKey=xxx`（含 CORS），返回当前播放记录（schema/state/position/duration/progress/speed 等）
- `server/Nano` 注册该端点

### 修改

- mobile/leanback `VideoActivity`：播放启动时 `PlaybackRuntime.setPlayer/updateHistory` 填充运行时，`onDestroy` 清理

#### 说明

- 仅只读上报，不含写入/远端同步/Webhook（后续 Step2/Step3 逐步加入）
- 隐身模式或无可读记录时返回 404；字段策略与 webhtv 一致（`apiSafe` 不含 episodeUrl 等敏感字段）
- `client` 字段适配为 webtv 的 `BuildConfig.FLAVOR`
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.70 — 站点弹窗手动排序记忆 (2026-08-11)

从 webhtv-main 移植 `SiteOrderStore`：手机端点播源弹窗支持**长按拖拽排序**，顺序按站点配置持久化（每份配置独立记忆），下次打开弹窗自动恢复。

### 新增

- `setting/SiteOrderStore`：站点顺序持久化（`site_dialog_order_<cid>`，按配置分离），`sortSites` 恢复 / `save` 保存
- mobile `SiteAdapter`：`sortSites` 恢复顺序；新增 `drag(from,to)` 完成拖拽移动并保存
- mobile `SiteDialog`：挂载 `ItemTouchHelper`（上/下拖拽），长按拖动站点即可调整顺序

#### 说明

- 仅手机端（leanback 为遥控器 UI，无触屏拖拽，与 webhtv 范围一致）
- 手动顺序优先于站点健康排序（`SiteOrderStore.sortSites` 最后执行，仅在存在已保存顺序时生效）
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.69 — 软件 DSP 音频效果通路（EXO，API 24+） (2026-08-11)

从 TV-fongmi 移植软件 DSP 音频管线，补齐此前「仅硬件均衡器」缺口：不仅 API 28+ 的硬件 `DynamicsProcessing` 均衡器，现还支持 API 24–27 的软件均衡器，以及响度归一、动态稳定、前级/Boost 增益与软限幅等 DSP 处理。

### 新增

- **软件 DSP 管线**（纯 Media3 `BaseAudioProcessor`，无外部依赖）：`AudioEffectProcessor` / `AudioSoftwareEqualizer`（10 频段 IIR 均衡）/ `AudioLimiter` / `AudioLoudnessNormalizer` / `AudioStabilizer`
- `ExoAudioEffectController`：统一调度硬件（API 28+ 均衡器）与软件（API<28 均衡器 + DSP）两条通路
- `AudioEffectProcessor` 内实现声道混合（立体声/单声道/反相，ITU-R BS.775 下混），替代 TV-fongmi 依赖的 mpvplayer 扩展 `AudioChannelMix`

### 修改

- `ExoUtil.buildPlayer` / `buildRenderersFactory` 支持注入 `AudioProcessor`：通过覆写 `NextRenderersFactory.buildAudioSink`，把 `AudioEffectProcessor` 挂进 `DefaultAudioSink` 音频链
- `ExoPlayerEngine` 改用 `ExoAudioEffectController`，把软件 processor 注入音频渲染器；`applyAudioSetting()` / `clearAudioEffect()` / `rebuild()` 同步更新

#### 说明

- 音频效果默认关闭，仅用户启用时生效；软件通路主要服务于 API<28 或需要响度/稳定等 DSP 的低版本设备
- 声道混合为内部实现（与 TV-fongmi 的 mpvplayer 扩展算法等效），未引入额外依赖
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.68 — 直播 Python 源 m3u8 兼容代理 (2026-08-11)

从 webhtv-main 移植 `server/process/M3u8`：为 Python 直播源发出的 `127.0.0.1:9978/m3u8?url=` 链接提供本地代理转发，补全 UA/Referer/Origin 头，并重写播放列表内的分片地址回到本代理，保证直播可正常播放。

### 新增

- `server/process/M3u8`：独立 `Process` 实现，命中 `/m3u8` 时转发上游，区分播放列表与分片流
  - 播放列表：重写 `.m3u8` 内分片/`#EXT-X-KEY`URI 为本代理地址，禁缓存
  - 分片流：透传 Range/Content-Range，支持 HEAD 与断点续传
- `server/Nano` 注册 `process.add(new M3u8())`

### 安全/兼容

- 仅接受 `http(s)` 目标，转发异常捕获返回 500，不向外暴露内部请求
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.67 — 播放健壮性加固（Issue #7 闪退修复） (2026-08-11)

针对 Android 17（Pixel 7 Pro，手机版）闪退反馈，加固播放/换源路径的异常防护，避免 Spider/解析或引擎重建抛出的异常直接导致应用崩溃（借鉴 webhtv 的 try/catch(Throwable) 防崩思路）。

### 修改

- `VideoActivity`（mobile 与 leanback）`getPlayer()`：`mViewModel.playerContent(...)` 包裹 `try/catch(Throwable)`，Spider/解析异常不再崩溃，转为走既有错误提示流程（`onError`）
- `VideoActivity`（mobile 与 leanback）`onPlayerKernel()` / `onDecode()`：切换内核/编码时包裹 `try/catch(Throwable)`，引擎重建失败不闪退
- `PlayerManager.switchPlayer()`：重建后 `setMediaItem()` / `seekTo()` 包裹 `try/catch(Throwable)`，媒介装载异常不闪退

#### 说明

- 崩溃仅凭 issue 描述（无 logcat 堆栈）无法唯一归因，本版先做整体健壮性加固；若仍复现，需补充崩溃日志定位视频 GL 特效或音频均衡器路径
- 软件 DSP 音频管线（TV-fongmi 完整版）因依赖 `AudioChannelMix` 且 webtv 的 mpvplayer 为精简 shim，暂缓移植
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.66 — 音频效果（EXO 均衡器，API 28+） (2026-08-11)

从 TV-fongmi 移植音频均衡器（首期，EXO 优先）：轨道弹窗新增「设置」入口（音频轨显示），可切换音效预设与开/关。

### 新增

- **音频均衡器**: 基于 Android `DynamicsProcessing`（API 28+）硬件均衡器，挂到 EXO 音频会话，支持 12 个预设（自然/人声/影院/低音/高音/流行/摇滚/舞曲/电子/嘻哈/爵士/古典）+ 关闭
- 移植自包含引擎：`AudioEffectConfig` / `AudioEffectBands` / `AudioEffectPreset` / `AudioPresetLevels` / `AudioChannelMode` / `AudioEqualizerController`；`AudioSetting` 偏好
- 新增 `AudioSettingDialog`（启用开关 + 预设 chips），轨道弹窗音频轨进入

### 修改

- `ExoPlayerEngine` 实现 `applyAudioSetting()` / `clearAudioEffect()` / `supportsAudioSetting()`（读取 EXO 音频声道数构建 EQ 配置）
- `PlayerEngine` 新增 `applyAudioSetting()` / `clearAudioEffect()` / `supportsAudioSetting()` 默认实现；`PlayerManager` 新增对应委托与 `canSetAudioSetting()`

#### 说明

- 首期仅含均衡器预设 + 限幅（`DynamicsProcessing`），**不含**软件 DSP 通路（需自定义渲染器注入 `AudioChannelMix`，留待后续）与 MPV 滤镜；API 28 以下优雅降级为「不支持」
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.65 — 视频色彩调节支持 MPV (2026-08-11)

补全视频色彩调节的 MPV 支持：此前仅 EXO 可用，现通过 MPV 原生 VO 属性（亮度/对比度/饱和度/伽马/色相）实现同款调节。

### 新增

- **MPV 视频色彩调节**: `MpvPlayer` 新增 `setVideoEqualizer()`（设置 `brightness`/`contrast`/`saturation`/`gamma`/`hue` VO 属性）
- `MpvPlayerEngine` 重写 `supportsVideoEffects()` / `applyVideoProfile()` / `clearVideoProfile()`，将 `VideoEffectProfile` 换算为 MPV 属性范围
- 引擎统一：`PlayerEngine` 新增 `applyVideoProfile()` / `clearVideoProfile()` 默认实现；`ExoPlayerEngine` 将色彩效果控制器收纳到引擎内部并实现 profile 应用

### 修改

- `PlayerManager` 改用引擎级 `applyVideoProfile()`/`clearVideoProfile()`，`canSetVideoSetting()` 现对 EXO 与 MPV 均可用
- 视频设置对话框在 EXO 与 MPV 下均可开启调节

#### 说明

- MPV 采用 VO 属性实现，未覆盖色温/锐度/阴影（MPV 无对应原生属性，仅亮度/对比度/饱和度/伽马/色相生效）；与 LUT 互不冲突
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.64 — 视频色彩调节 (2026-08-11)

从 TV-fongmi 移植视频色彩调节能力（EXO 内核）：轨道弹窗新增「设置」入口，可调整饱和度/对比度/亮度/伽马/色相/色温/锐度/阴影。

### 新增

- **视频色彩调节**: 轨道弹窗新增设置按钮（视频轨显示），基于 Media3 `GlEffect`/`BaseGlShaderProgram` 实现
- 移植自包含效果引擎：`VideoEffectProfile` / `VideoEffectPreset` / `ColorToneAdjustEffect` / `DetailAdjustEffect` 及各自 shader program / `ExoVideoEffectController`
- 新增 `VideoSetting` 偏好与 `VideoSettingDialog`（启用开关 + 8 项滑杆调节 + 重置）；`view_setting_slider` 滑杆行
- 仅 EXO 内核启用（`ExoPlayer.getVideoEffectsSupport()` 校验），MPV 优雅降级为「不支持」提示

### 修改

- `ExoPlayerEngine` 重写 `supportsVideoEffects()` / `setVideoEffects()`
- `PlayerManager` 新增 `canSetVideoSetting()` / `refreshVideoSetting()` / `clearVideoEffect()`
- `BaseBottomSheetDialog` 新增 `getMaxHeight()` 高度上限支持
- 顺带移植 `SliderUtil`（滑杆吸附工具）

#### 说明

- 本次为 EXO-first 精简版：未含 TV-fongmi 的完整预设系统（自然/鲜艳等 12 档 chips）与对比预览、未实现 MPV 端均衡器/shader（webtv 内嵌 MpvPlayer 缺 `MpvVideoEqualizer` API，待后续）
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.63 — 在线字幕搜索 (2026-08-11)

从 TV-fongmi 移植 Assrt 在线字幕搜索能力：轨道面板新增「搜索」入口，可在线搜索、下载并套用外部字幕。

### 新增

- **在线字幕搜索**: 轨道弹窗新增搜索按钮（仅点播字幕轨显示），基于 Assrt API 搜索外部字幕
- 支持关键词搜索 / 分页 / 详情解析 / ZIP 字幕包下载解压后选择套用
- 新增 `SubtitleApi` / `SubtitleSearchDialog` / `SubtitleApiDialog`（Token 配置）/ `SubtitleAdapter` 等；`SubtitleSearchItem.toSub()` 复用既有 `Sub` / `PlayerManager.setSub` 套用流程
- 新增 `SubtitleSetting`（Assrt Token 持久化），经搜索弹窗的设置齿轮进入 Token 配置
- 安全底线：下载与解压均带字节/条目上限（32MB 下载、256 条目/128MB 解压）与路径穿越防护

### 修改

- `Sub` 新增 4 参构造 `from(name,url,lang,format)`
- `Download` 新增 `maxBytes` 上限；`FileUtil` 新增带限制与穿越防护的 4 参 `zipDecompress`
- `BaseBottomSheetDialog` 新增预测性返回（back dispatcher）支持

#### 说明

- 仅移植在线搜索，不含 TV-fongmi 的字幕外观/样式调整（webtv 已有 `SubtitleDialog`）
- Assrt Token 需用户自行填写（`VodConfig.getAssrt` 配置字段未引入）
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.62 — EXO 自适应降速 + WebView 闪退修复 (2026-08-11)

修复安卓 17 / Pixel 7 Pro 手机版闪退（issue #7），并从上游 WebHomeTV 移植 EXO 自适应降速网络保护（最小可验证集）。

#### 修复

- **WebView 渲染进程崩溃闪退**: `CustomWebView` 新增 `onRenderProcessGone` 兜底（镜像 HomeWebController 模式，记日志后正常结束解析并返回 true），避免解析页渲染进程崩溃拖垮整个 App —— 对应 issue #7 安卓 17 手机版闪退

### 新增

- **EXO 自适应降速网络保护**: 从 WebHomeTV 移植 `ExoNetworkGuardController`（含 `ExoNetworkProtectionPolicy` / `ForwardBufferTrend` / `ExoNetworkGuardEligibility` / `ExoNetworkGuardBufferPolicy`）
- 依据缓冲水位快慢趋势，VOD 播放时在网络吃紧自动在 0.85～1.00x 之间动态降速、网络恢复后平滑回升；仅作用于 EXO 内核 + 点播 + 用户 1.0x 场景
- 增强设置新增「网络保护」开关（跟随性能自动档默认开启），经 `ExoPerformanceSetting` 偏好管理，`PlayerManager.setSpeed` 挂钩用户手动调速

#### 说明

- 采用纯 buffer 模式（不含遥测/A-B 实验链），最小可验证移植，不影响 IJK/MPV 内核
- **已知后续项**: `librtmp-jni.so`（由 media3-datasource-rtmp 引入）ELF 仅 4KB 对齐，在 16KB 页大小设备（如 Pixel 7 Pro 安卓 17）上播放 RTMP 流时存在 dlopen 崩溃风险；当前应用已开启 `pageSizeCompat="enabled"` 兼容模式，后续需将 RTMP 制品重编至 16KB 对齐或评估移除
- 家庭过滤、ServerAuth、强制签名、targetSdk 37 等安全底线均保留

## 5.5.61 — Phase 3A login-state learning (2026-07-22)

从上游 WebHomeTV 移植登录态学习与一键同步，保留 webtv 安全底线；GitCloud 完整能力仍属后续阶段。

### 新增

- **登录态学习**: 增强设置入口，学习 Cookie / 登录态文件路径（开始学习 → 登录网盘 → 完成学习）
- **路径管理**: 目录树勾选、待确认候选、文本预览/编辑
- **一键同步**: 新增「登录态」勾选项，推送/拉取时打包与恢复登录态 ZIP
- **本地管理页同步**: Manage/Action 推送端与接收端支持 `loginStateFiles`
- **加密 Token 存储**: 最小 `GitCloudTokenStore`（Android Keystore AES/GCM），供登录态 token 导入导出

#### 说明

- 仅移植登录态学习，不引入完整 GitCloud / Remote Trust / 观影同步
- 登录态默认勾选同步；路径与 token 不落明文日志
- 家庭过滤、ServerAuth、强制签名、targetSdk 37、Room ≥37 均保留

## 5.5.60 — Multi-kernel player MVP (2026-07-21)

从上游 WebHomeTV 移植 EXO / IJK / MPV 多内核播放能力，默认仍为 EXO；保留 webtv 续播/倍速与安全底线。

### 新增

- **播放器内核**: 设置页可切换 EXO / IJK / MPV；播放页长按“播放器”按钮可快速切换内核
- **IJK 内核**: 内置 jniLibs，兼容更多容器/协议场景
- **MPV 内核**: 内置 libmpv assets，提升软解与特殊片源兼容
- **Media3**: 对齐上游 `1.11.0-alpha01-fongmi`（MediaTitle → MediaEdition）

#### 说明

- APK 体积会明显增大（IJK + MPV native）
- 默认内核仍为 EXO，不影响既有播放连续性
- 未整包合入上游完整性能面板 / LUT / karaoke 等周边

## 5.5.59 — Phase 1 enhance features from WebHomeTV (2026-07-21)

从上游 WebHomeTV 移植第一批低风险增强能力，保留 webtv 安全底线（ServerAuth、强制签名、targetSdk 37、FamilyFilter、ConfigImport、Room ≥37）。

### 新增

- **网盘检测 (DriveCheck)**: 增强设置开关；`POST /pan/check`（受 ServerAuth 保护）；WebHome SDK `pan.check` / `pan.play`
- **CSP 预热**: 配置加载后预初始化 Jar CSP 站点，支持默认/自定义站点选择
- **WebHome 主页全屏**: 可关闭网页强制全屏，保留原生工具栏布局
- **播放界面剧照**: 设置开关（默认开），为后续剧照背景能力预留偏好

### 保留

- FamilyFilter、配置导入预检、ServerAuth 鉴权、Release 强制签名、Universal APK

## 5.5.58 — Security audit hardening (2026-07-17)

代码审计发现的高/中/低风险项系统性修复，覆盖远程代码加载、本地服务、WebHome 桥、DLNA、播放器和 CI 构建链。

### 安全加固

- **远程 JAR 加载**: 拒绝明文 `http://` JAR；`file://` JAR 必须带 hash 且经过依赖信任确认
- **依赖信任键**: 信任键从 `hashCode` 改为 SHA-256，避免碰撞；新增非信任页面播放确认
- **路径穿越**: `Path.local()` 校验 canonical 路径必须落在 cache/files/root 安全目录内
- **DNS 重绑定**: WebCall 新增 `FilteringDns`，DNS 解析阶段拒绝私有/回环/链路本地地址
- **DLNA**: `SocketHttpStreamServer` 收紧已知 peer 判定，缺失 `soapaction` 的请求直接拒绝
- **本地服务**: `ServerAuth` 扩展受保护路径（`/media`、`/tvbus`、`/device`），不再信任 `http-client-ip` 头
- **Manage 跳转**: `isValidTarget` 校验所有解析地址均为本机/局域网，避免 DNS 重绑定绕过
- **代理头转发**: `/proxy` 不再向 spider 转发 `authorization`、`cookie`、`x-fongmi-token` 等敏感头
- **日志脱敏**: SiteApi 播放日志和 OkHttp DebugEventListener 的 URL/头信息脱敏
- **WebView**: `CustomWebView` 显式关闭 `file://` 访问和通用文件访问

### 优化

- **搜索相关性**: 预编译噪声正则，扩展 CJK 标点覆盖；相关性阈值可通过 `search_relevance_threshold` 配置
- **搜索取消**: 多源搜索回调在主线程二次校验 epoch，缩小 TOCTOU 窗口
- **DexOpt 隔离**: 每个 spider key 使用独立 `dexopt/<key>` 目录，`clear()` 清理所有子目录
- **CORS 收敛**: `/webResource` 仅允许必要的请求头和暴露头
- **组件导出**: `LiveActivity` 改为 `exported=false`（无 intent-filter，仅内部启动）
- **CI 构建链**: JDK 升级到 21 对齐源码目标，build-tools 升级到 37.0.0，CNB_TOKEN 通过 env 注入避免脚本注入

## 5.5.57 — Search relevance polish (2026-07-18)

优化多源搜索结果相关性，减少与关键词不匹配的模糊结果。

### 优化

- **搜索相关性**: 按关键词匹配度过滤和排序搜索结果，片名精确/包含匹配优先，明显无关结果不再展示
- **文档说明**: README 补充搜索相关性能力说明

## 5.5.56 — Source entries and protocol compatibility (2026-07-14)

补齐指定 TV 源入口，并确认播放器壳子已接入官方 FongMi 额外协议扩展能力。

### 新增 / 优化

- **指定源入口**: TV 源补充本地视频、手机推送、网络直播、哔哩直播、好看短剧和河马短剧
- **源配置校验**: 检查 `api.json` 格式、站点 key 重复、直播配置、`spider.jar` md5 和本地 `js/py` 引用
- **协议兼容**: README 标注 Force、JianPian、Thunder、TVBus 等 FongMi 协议扩展已接入

## 5.5.55 — FongMi api37 compatibility (2026-07-14)

跟进官方 FongMi api37 适配，提升目标 SDK 并收敛新版 Android 权限请求路径。

### 优化 / 兼容

- **Target SDK 37**: 将应用目标 SDK 提升到 37，保持与官方 FongMi api37 适配一致
- **权限请求适配**: 文件访问按 Android 版本与系统能力请求所有文件访问或存储权限，Android 13+ 才请求通知权限
- **构建工具链**: Gradle Wrapper 对齐官方参考版本，README 补齐六个 APK 构建与 Actions 发布说明

## 5.5.54 — WebHome and release safety audit fixes (2026-07-04)

修复代码审计中发现的 WebHome 信任边界、Native 请求、Release 签名和生命周期残余风险。

### 修复 / 加固

- **WebHome 文件信任**: `file://` 首页使用 canonical path 判断，避免路径前缀误判为受信任页面
- **Native SDK 权限**: 未受信任 WebView 页面仅保留最小可用 API，播放控制、页面导航、缓存、设备和配置等能力需要受信任来源
- **Native 请求防护**: `net.request` 拒绝本机、链路本地和局域网私有地址，避免受信任页面变成内网请求代理
- **Release 签名**: Release 构建缺少签名配置时直接失败，不再回退到 debug 签名
- **数据库迁移**: 移除 v1-v29 破坏性迁移声明，迁移失败时保留失败库副本后再重建
- **设备发现边界**: `/device` 仅允许本机和局域网访问，保留一键同步发现能力并减少公网枚举
- **生命周期观察者**: Leanback 和 Mobile 的播放/直播页改用 lifecycle-aware observer，减少手动解绑遗漏风险
- **Mobile 换源续播**: 手机端切换搜索来源前先保存当前历史，避免重置播放器后丢失续播进度
- **镜像同步可见性**: Release workflow 将 CNB 镜像和 TV 源同步结果写入 Actions summary

## 5.5.53 — Stability and release hardening (2026-07-04)

继续收敛播放状态一致性与发布安全细节，降低升级和发版过程中的数据与凭据风险。

### 修复 / 优化

- **Mobile 倍速保持**: 手机端每次播放器准备新媒体源后也会重新应用当前历史倍速，避免自动下一集后实际倍速和界面显示不一致
- **数据库恢复加固**: 数据库打开失败时先保留失败库文件副本，再重建数据库，减少排查和恢复成本
- **迁移范围收敛**: 破坏性迁移只保留给缺少旧 schema 的 v1-v29，v30 之后继续走显式迁移链
- **Release 日志脱敏**: GitHub Actions 不再打印包含签名配置的 `local.properties`
- **Release 说明**: 发布页自动带上当前版本的 changelog 摘要
- **仓库清洁**: 忽略本地 `*-apkapp.txt` 临时产物，避免误提交

## 5.5.52 — Playback speed continuity (2026-07-04)

修复 TV 端自动播放下一集后实际倍速恢复为 x1，但界面仍显示历史倍速的问题。

#### 修复

- **倍速保持**: 每次播放器准备新媒体源后重新应用当前剧集历史倍速，保证自动下一集、手动换集和刷新播放时实际倍速与界面显示一致

## 5.5.51 — Playback continuity polish (2026-07-04)

修复 TV 端播放电视剧时，切换搜索到的其他来源后容易从头播放的问题，并放大播放页右上角时间显示。

### 修复 / 优化

- **换源续播**: 切换到搜索结果来源前先保存当前播放历史，避免新源记录丢失播放进度
- **播放页时钟**: 放大右上角时间字号，提升电视端观看距离下的可读性

## 5.5.46 — Hide selected source-side config entries (2026-06-13)

在保留用户自带来源整体内容的前提下，精确隐藏配置中心里指定的来源项，例如夸克网盘配置、UC网盘配置、百度网盘配置、天翼云盘配置、123云盘配置、阿里云盘配置、移动云盘配置、哔哩配置，以及综合配置里的“云盘排序”。

### 新增 / 优化

- **配置中心来源项精确屏蔽**: 只隐藏用户指定的配置中心来源项，不扩大到其他来源内容
- **家庭过滤兼容**: 继续保留可选家庭过滤，对首页/分类/搜索元数据做关键词屏蔽

## 5.5.44 — README cleanup and release polish (2026-06-13)

重构 README 结构，突出播放器壳子定位、导入/管理/同步路径与当前亮点，让用户更容易快速理解项目用途和边界。

### 新增 / 优化

- **README 重写**: 收紧为用户导向结构，拆分“这是什么 / 亮点 / 使用 / 文档 / 构建 / 更新说明”
- **版本文案统一**: README、CHANGELOG 与 release 语义进一步对齐

## 5.5.43 — Family filter for TV-style homepage content (2026-06-12)

新增壳子级家庭过滤能力，基于站点返回的元数据关键词屏蔽不适合电视客厅展示的分类、推荐卡片和搜索结果，并向 WebHome 页面暴露过滤策略供其协作隐藏内容。

### 新增 / 优化

- **家庭过滤开关**: 在增强功能页新增“家庭过滤”入口，可启用/禁用过滤并编辑关键词
- **关键词屏蔽**: 对首页、分类、搜索等原生内容流按关键词过滤分类、卡片和筛选项
- **WebHome 协作**: `fm.config()` 现在会暴露家庭过滤状态和关键词，方便页面侧自定义隐藏逻辑
- **同步 / 备份一致性**: 家庭过滤设置会跟随 settings 一起备份和同步

## 5.5.42 — Config center & sync continuity (2026-06-12)

继续强化播放器壳子的配置管理与同步连续性，补齐最近设备记忆和配置管理入口的独立页面能力。

### 新增 / 优化

- **独立配置管理入口**: 手机端与 TV 端开始引入独立的配置管理页面入口
- **配置中心深化**: 设置页的配置历史入口升级为更明确的配置管理入口
- **最近同步设备记忆**: 一键同步会记住上次设备，并在下次发现后优先选中
- **发版一致性**: 统一版本号、README 与 CHANGELOG 说明

## 5.5.41 — Shell UX Improvements (2026-06-12)

强化播放器壳子的配置导入、配置管理、同步文案与继续观看/收藏语义，确保用户自带 JSON 的导入、管理和迁移体验更清晰。

### 新增 / 优化

- **配置导入预检**: 导入 URL / 文件前先做基础校验，并显示配置摘要
- **失败不覆盖当前配置**: 导入失败时保留当前正在使用的配置
- **配置管理增强**: 历史配置列表显示当前项、来源类型与最后使用时间
- **删除影响提示**: 删除配置前提示会同步移除关联的历史与收藏
- **同步文案升级**: 同步项改为更贴近壳子用户理解的名称，成功提示带设备名
- **继续观看 / 收藏语义增强**: 历史页和收藏页标题更清晰，配置缺失时给出明确提示

## 5.5.40 — Shell UX Improvements (2026-06-12)

移除一批不再维护的外部集成能力，并恢复正式仓库构建配置。

### 移除

- **外部检测服务**: 删除不再维护的分享检测服务与相关本地 HTTP 端点
- **WebHome 扩展桥接**: 删除一批已下线的 Bridge 能力
- **附加同步/UI 入口**: 删除对应的路径管理、附加入口和相关界面逻辑

### 构建恢复

- **Chaquopy**: 恢复正式仓库 Python 3.10 配置
- **requirements**: 恢复 `ujson` 依赖，撤销本地临时打包修改

## 5.5.38 — Reverted experiment (2026-06-12)

回滚一次未继续保留的实验性功能。

#### 变更

- 引入了一组后续未继续保留的实验性界面与同步逻辑
- 后续版本已整体移除

## 5.5.37 — Manual Update Flow (2026-06-11)

简化为手动下载更新：不再尝试 App 内下载安装，直接复制链接到浏览器下载。

### 改动

- **更新流程**: 检测到新版本后，确认按钮 → 复制下载链接到剪贴板 → 打开浏览器下载安装
- **移除**: 移除 App 内下载、FileProvider 安装、导出到 Downloads 等复杂逻辑

## 5.5.36 — Post-cleanup (2026-06-11)

清理镜像残留，更新 README 版本号。

## 5.5.35 — Remove Mirror (2026-06-11)

移除镜像同步。

## 5.5.33 — Fix DB Migration Crash (2026-06-11)

修复 5.5.32 数据库迁移导致闪退的问题。

#### 修复

- **DB 初始化加固**: `AppDatabase.get()` 首次创建失败时自动删除旧数据库重新创建，避免迁移异常导致闪退

## 5.5.32 — EPG Reminder Persistence & CI Sync Warning (2026-06-11)

EPG 节目提醒持久化到数据库，设备重启后自动重建闹钟。

### 新增

- **EPG 持久化**: 节目提醒保存到 Room 数据库，重启和 BOOT_COMPLETED 时自动重建所有未过期的提醒闹钟

#### 修复

- **清理死代码**: 删除未使用的 `CharsetDetectDataSource.java`

## 5.5.31 — Code Cleanup & CI Sync Robustness (2026-06-11)

代码整洁和 CI 稳定性改进。

#### 修复

- **死代码清理**: 删除未使用的 `CharsetDetectDataSource.java`（v5.5.20 已从 `MediaSourceFactory` 移除引用）

## 5.5.30 — Fix Update Mirror Selection & Install (2026-06-11)

修复版本更新下载走 GitHub 慢和下载后不弹出安装的问题。

#### 修复

- **镜像选择**: `isChina()` 新增系统语言/地区本地检测，不再仅依赖 `ip-api.com`（国内可能被墙导致默认走 GitHub）
- **安装确认**: 下载完成后改用 `ACTION_INSTALL_PACKAGE` 触发安装，并先弹出 Toast 提示
- **安装鲁棒**: 精简重复的 `FLAG_GRANT_READ_URI_PERMISSION`，Android N+ 走 FileProvider

## 5.5.29 — Fix Live TV URL Refresh on Error (2026-06-11)

修复电视直播源 token 过期后无法自动刷新的问题。

#### 修复

- **直播错误重试**: `LiveActivity.onError` 播放失败时先调用 `mViewModel.getUrl(mChannel)` 从 Spider/DIYP 源重新获取新 URL（带新鲜 token），再进入自动换台流程
- **BAD_HTTP_STATUS 重试**: `ExoPlayerEngine` 把 HTTP 错误码加入可重试列表

## 5.5.28 — Fix Live TV BAD HTTP STATUS (2026-06-11)

修复电视直播显示 `Bad HTTP Status` 无法播放的问题。

#### 修复

- **HTTP 错误重试**: `ExoPlayerEngine.handleError` 将 `ERROR_CODE_IO_BAD_HTTP_STATUS`（HTTP 605 等）加入可重试列表，不再直接判定为 FATAL，给直播源 token 刷新留一次机会

## 5.5.27 — Fix Update JSON Asset Path (2026-06-11)

修复点击版本更新后只显示“正在检测更新”但没有后续结果的问题。

#### 修复

- **JSON 文件名**: 更新检查改为读取当前 ABI 对应的 `mobile-arm64_v8a.json` / `leanback-arm64_v8a.json`，不再请求不存在的 `mobile.json`
- **GitHub 路径**: GitHub Release assets 直接走 `latest/download/*.json`
- **失败提示**: 更新检查异常时显示 `Update check failed`，不再重复显示 `Checking for updates…`

## 5.5.26 — Fix WebHome Bridge Diagnostics Crash (2026-06-11)

修复同意更新源后 WebHome 调用 Bridge 时闪退的问题。

#### 修复

- **WebView 线程安全**: `HomeWebBridge` 诊断不再从后台线程读取 `WebView.getUrl()`，改用 `HomeWebController` 在主线程维护的 origin 缓存

## 5.5.25 — Fix Update Source Version Detection (2026-06-11)

修复点击版本更新时误提示 `Already up to date` 的问题。

#### 修复

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

新增 IP 地理位置检测，自动选择最优更新源。

### 新增

- **GeoIP 检测**: 通过 `ip-api.com` 检测用户所在国家，自动选择最快的更新源
- **手动切换**: `Setting.putMirror()` 支持 `auto`/`github` 模式
- **CI 同步**: GitHub Actions 发布后自动推送 APK + JSON 到镜像仓库

## 5.5.20 — Fix Live Stream Loading Stuck (2026-06-11)

修复电视直播一直加载不显示画面的问题。

### 原因

P2-7 字幕编码检测 (`CharsetDetectDataSource`) 被错误地应用到所有媒体数据源，其缓冲读取逻辑在遇到小文件（<5MB）时会在流末尾重复返回已读数据，导致 ExoPlayer 的 HLS 解析器收到损坏的播放列表数据（`Input does not start with the #EXTM3U header`）。

#### 修复

- 移除全局 `CharsetDetectDataSource.Factory` 包装器，恢复原始 `DefaultDataSource.Factory`

## 5.5.19 — Fix Live Stream HTTP Blocked (2026-06-11)

修复直播流 `Network Connection Failed`：`base-config cleartextTrafficPermitted` 恢复为 `true`，允许 HTTP 直播流。

### 原因

P0 安全提交将 `cleartextTrafficPermitted` 改为 `false`，意图是只允许 HTTPS。但绝大多数直播源使用 HTTP 协议且域名不固定，无法预先配置白名单。

## 5.5.18 — Build Fix (2026-06-11)

修复 CI 编译错误：`CharsetDetectDataSource.close()` 添加 `throws IOException` 声明。

## 5.5.17 — Backup & CustomCsp Storage Fix (2026-06-11)

修复备份/恢复和自定义 CSP 功能在 Android 11+ 上静默失败的问题。

#### 修复

- **Path.tv()**: 备份文件从外部存储迁移到内部存储，无需 `MANAGE_EXTERNAL_STORAGE` 权限
- **CustomCspSetting**: CSP 配置目录从 `Path.root("TV/CustomCsp")` 迁移到 `Path.files()`

### 原因

v5.5.7 安全审计移除了 `MANAGE_EXTERNAL_STORAGE` 权限，v5.5.10 将 `hasFileAccess()` 在 Android 11+ 改为始终返回 `true`。但由于该权限实际未被授予，写入外部存储的操作静默失败，用户点击备份/恢复按钮后无任何效果。

## 5.5.16 — EventBus Annotation Processor Cleanup (2026-06-10)

移除 EventBus 注解处理器依赖，彻底解决 `No option eventBusIndex passed to annotation processor` 编译错误。

## 5.5.15 — CI Build Fix (2026-06-10)

修复 EventBus 注解处理器导致的编译错误。

#### 修复

- **EpgParser**: 合并重复的 `getEpg()` 方法，XXE 安全校验统一走一个入口
- **EventBus**: `Startup.java` 改用 `EventBus.getDefault()`，移除 `eventBusIndex` 注解处理器参数

## 5.5.14 — P1/P2 Security & Stability Fixes (2026-06-10)

修复 14 个安全和稳定性问题。

## 5.5.13 — P0 Security Fixes (2026-06-10)

基于 v5.5.12 的 P0 级安全修复版本，修复 6 个严重安全漏洞。

#### 修复

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

#### 修复

- **Setting.hasFileManager()**: 始终返回 `false`（v5.5.7 已移除 `MANAGE_EXTERNAL_STORAGE`，PermissionX 无法处理未声明的权限）
- **Setting.hasFileAccess()**: Android 11+ 直接返回 `true`（应用内部存储无需权限，文件选择器走 SAF）
- 仅在 Android 10 及以下请求旧版 `READ/WRITE_EXTERNAL_STORAGE` 权限

## 5.5.9 — Build Fix (2026-06-10)

修复 v5.5.8 编译错误。

#### 修复

- **CacheManager**: 移除 `Path.mkdir()` 私有方法调用（`Path.exo()` 已自动创建目录）
- **EpgReminder**: `buildNotification()` 改为 `public static`，修复跨包访问权限

## 5.5.8 — Enhancements & Optimizations (2026-06-10)

基于 v5.5.7 安全审计修复后的功能增强和性能优化版本。

### 修复 (P0-P2)

- **OkHttp 懒加载竞态**: `dns()`/`responseInterceptor()` 等 6 个方法加 `synchronized` 保护
- **Server 端口泄漏**: `start()` 端口绑定失败时调用 `nano.stop()` 清理资源
- **ImgUtil.failed 有界集合**: `HashSet` 改为 `LinkedHashSet` 有界 LRU（最大 200），防止 OOM
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
- **M6**: `HomeWebController` 和 `CustomWebView` 的 `shouldOverrideUrlLoading` 阻止 `intent://` 协议。

### 低危 (LOW)

- **L5**: `Local.unzip()` 新增 ZIP 炸弹防护：单条目 ≤100MB，总计 ≤500MB，最多 1000 个条目。

## 5.5.3 — Security Hardening

基于 5.5.2 的安全加固版本。修复了 13 个高/中危问题，但**未新增功能**。

### 网络与 TLS

- 移除 OkHttp 全局 `trustAllCertificates()` 和 `hostnameVerifier(() -> true)` 绕过，恢复系统证书校验。
- 新增 `app/src/main/res/xml/network_security_config.xml`：影视源保留 HTTP 兼容，配置源、更新源、远端 JAR 等关键端点（`github.com`、`*.githubusercontent.com`）强制 HTTPS。
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
