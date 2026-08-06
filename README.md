# MXboxS

基于 [FongMi/TV](https://github.com/FongMi/TV) 的二次开发项目，覆盖 **Android TV（leanback）** 与 **手机端（mobile）** 的影视应用。

[![Build MXboxS Release](https://github.com/ssmhdssmhd/MXboxS/actions/workflows/build.yml/badge.svg)](https://github.com/ssmhdssmhd/MXboxS/actions/workflows/build.yml)
[![Sync Upstream](https://github.com/ssmhdssmhd/MXboxS/actions/workflows/sync.yml/badge.svg)](https://github.com/ssmhdssmhd/MXboxS/actions/workflows/sync.yml)

---

## 最新更新

### v5.5.42 · 2026-08-06 · 修复 m3u8 播放报错 "Network Connection Failed"（第三方解析站伪造 127.0.0.1 本地代理 URL）

| # | 防线位置 | 行为 | 代码位置 |
|---|---------|------|---------|
| 1 | **UrlUtil** | 新增 `unwrapFakeLocalProxy(url)` 还原算法：识别 `http://127.0.0.1:非9978~9999/p/0/.../base64/index.m3u8` 结构，从 base64 段解码出真实页面 URL（如 https://player.ypls.com/play/...） | [UrlUtil.java#L24-L81](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/UrlUtil.java#L24-L81) |
| 2 | **ParseJob** | 解析成功出口拦截：先嗅探还原 URL（直链 probe + 正文正则候选逐个 probe），未命中则 `fallbackConcurrentParse` 重跑「JSON解析站 + WebView sniff + jsonExtend」多路兜底挖真 m3u8 | [ParseJob.java#L717-L805](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseJob.java#L717-L805) |
| 3 | **CustomWebView** | shouldInterceptRequest 过滤伪造本地代理 URL，不把它当直链触发 onParseSuccess | [CustomWebView.java#L117-L134](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/ui/custom/CustomWebView.java#L117-L134) |
| 4 | **PlayerManager** | onParseSuccess 入口第三道防线：仍检测到伪造则用还原真实 URL 重走 `parse(useParse=true)`（+reparse 尾标防递归） | [PlayerManager.java#L515-L539](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/PlayerManager.java#L515-L539) |
| 5 | **PlaybackActivity** | startPlayer 入口第四道防线：SiteApi 直接返回的伪造 URL 替换为真实 URL，并强制 `parse=1 / useParse=true` 走解析 | [PlaybackActivity.java#L232-L261](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/ui/activity/PlaybackActivity.java#L232-L261) |
| 6 | 版本号 | versionCode 590 → **591** / versionName 5.5.41 → **5.5.42** | [app/build.gradle#L22-L23](file:///workspace/app/build.gradle#L22-L23) |

### v5.5.41 · 2026-08-06 · 修复自动更新：push main 自动更新 Releases Latest，App 自动感知最新版

| # | 模块 | 行为 | 代码位置 |
|---|------|------|---------|
| 1 | **构建工作流** | push main 构建成功后自动创建/覆盖 `MXboxS-latest` Release（`--latest` 标记为 Latest），上传 4 APK，带 release notes | [build.yml#L181-L237](file:///workspace/.github/workflows/build.yml#L181-L237) |
| 2 | **版本提取** | Updater.java 优先从 APK asset 文件名提取版本号（兼容 `MXboxS-latest` tag），失败回退 tag_name（兼容 `v*` 稳定 Release） | [Updater.java#L106-L113](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/Updater.java#L106-L113) |
| 3 | **Github 工具** | 新增 `extractVersionFromAssets(release)`：正则从 `MXboxS-mobile-arm64_v8a-X.Y.Z.apk` 提取版本号 | [Github.java#L88-L115](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/Github.java#L88-L115) |
| 4 | 版本号 | versionCode 589 → **590** / versionName 5.5.40 → **5.5.41** | [app/build.gradle#L22-L23](file:///workspace/app/build.gradle#L22-L23) |

### v5.5.40 · 2026-08-05 · 修复云播 m3u8 直链无法播放的问题

| # | 模块 | 行为 | 代码位置 |
|---|------|------|---------|
| 1 | **OkHttpDataSource 重写** | 原 stub 把 `OkHttpClient` 丢弃，退化为 `DefaultHttpDataSource`，丢失 SSL 信任 / 自定义 DNS / 拦截器。重写为真正基于 OkHttp 的实现，继承 `BaseDataSource`，复用 `OkHttp.player()` 的 `trustAllCertificates` + `OkDns` + `AuthInterceptor` 等 | [OkHttpDataSource.java](file:///workspace/app/src/main/java/androidx/media3/datasource/okhttp/OkHttpDataSource.java) |
| 2 | **双模式播放** | 方式 1（官方）：夸克网盘 `proxy?do=quark` 代理保持不变；方式 2（云播直链）：m3u8/mp4 直链由 ExoPlayer + OkHttpDataSource 直接播放，支持 AES-128 相对路径 enc.key、跨域 TS、自签名证书 | [MediaSourceFactory.java#L51](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/exo/MediaSourceFactory.java#L51) |
| 3 | 版本号 | versionCode 588 → **589** / versionName 5.5.39 → **5.5.40** | [app/build.gradle#L22-L23](file:///workspace/app/build.gradle#L22-L23) |

### v5.5.39 · 2026-08-05 · 修复应用内更新两大关键 Bug

| # | 模块 | 行为 | 代码位置 |
|---|------|------|---------|
| 1 | **版本比较** | `Updater.parseVersionCode()` 用 `5.5.36→5536` 与 `VERSION_CODE=587` 比较导致误报更新；改为 `compareVersionNames()` 按点分段**整数比较** versionName，仅远端 > 本地才提示更新 | [Updater.java#L110](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/Updater.java#L110)、[compareVersionNames()](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/Updater.java#L175-L192) |
| 2 | **ghproxy 镜像** | `Github.java` 中 `mirror + url` 缺 `/`，拼接成 `ghproxy.comhttps://...` 导致域名解析失败；补 `/` 分隔符 | [Github.java#L39](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/Github.java#L39)、[Github.java#L65](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/Github.java#L65)、[Github.java#L77](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/Github.java#L77) |
| 3 | 版本号 | versionCode 587 → **588** / versionName 5.5.38 → **5.5.39** | [app/build.gradle#L22-L23](file:///workspace/app/build.gradle#L22-L23) |

### v5.5.38 · 2026-08-05 · 新增会员卡密激活功能

| # | 模块 | 行为 | 代码位置 |
|---|------|------|---------|
| 1 | **卡密文件** | 仓库根目录新增 [`kami.txt`](file:///workspace/kami.txt)，存储 64 位有效卡密（首张 `bcda1fe5e260218399c2222d299d2a39555bd38461c81975247b8587c3ba62ac`），`#` 开头为注释。 | [kami.txt](file:///workspace/kami.txt) |
| 2 | **卡密验证工具** | 新增 `KamiUtil`：从 GitHub `kami.txt` 拉取卡密列表校验，支持 ghproxy → raw → jsDelivr 多源回退 + 12 小时本地缓存。 | [KamiUtil.java](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/KamiUtil.java) |
| 3 | **激活界面** | 新增 `KamiActivity`：卡密输入 / 验证 / 购买入口 / 已激活面板（掩码显示）/ 注销。未激活按返回或点退出 = 退出 App。 | [KamiActivity.java](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/ui/activity/KamiActivity.java) |
| 4 | **启动校验** | mobile + leanback 两端 `HomeActivity.initView` 首行增加激活校验：未激活 → 跳转 `KamiActivity` → 自身 finish。 | [mobile HomeActivity](file:///workspace/app/src/mobile/java/com/ssmhdssmhd/mxboxs/ui/activity/HomeActivity.java#L79-L92)、[leanback HomeActivity](file:///workspace/app/src/leanback/java/com/ssmhdssmhd/mxboxs/ui/activity/HomeActivity.java#L118-L137) |
| 5 | 版本号 | versionCode 586 → **587** / versionName 5.5.37 → **5.5.38** | [app/build.gradle#L22-L23](file:///workspace/app/build.gradle#L22-L23) |

### v5.5.34 · 2026-08-04 · 修复部分视频源解析成功但播放 0 KB/s 的问题

| # | 模块 | 行为 | 代码位置 |
|---|------|------|---------|
| 1 | **解析验证** | 重写 `probeVideoUrl` 方法，从"盲目信任视频后缀"改为"基于 HTTP 状态码的精细化判定"。对于返回 404/500 的无效 URL（即使后缀是 `.m3u8`/`.mp4`），正确识别为失败并触发 fallback。 | [ParseJob.java#L241-L304](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseJob.java#L241-L304) |
| 2 | **AI 嗅探** | `aiSmartParseFallback` 直链分支：探测失败时不再直接放弃，而是继续走嗅探流程，增加一次获取有效视频 URL 的机会。 | [ParseJob.java#L187-L234](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseJob.java#L187-L234) |

### v5.5.33 · 2026-08-04 · 新增上游 FongMi/TV 实时同步工作流

| # | 模块 | 行为 | 代码位置 |
|---|------|------|---------|
| 1 | **同步工作流** | 新增 `sync.yml`，每 6 小时自动从 [FongMi/TV](https://github.com/FongMi/TV) `fongmi` 分支拉取最新变更，`git merge -X ours` 合并到 `upstream-sync` 分支（冲突保留 MXboxS 定制），清理上游 `com/fongmi/` app/ Java 文件（避免编译失败），创建/更新 PR 供人工 review。 | [sync.yml](file:///workspace/.github/workflows/sync.yml) |
| 2 | **构建工作流** | `build.yml` 新增 `upstream-sync` 分支 push 触发 + PR 触发，同步 PR 自动构建验证；PR 构建跳过 APK 上传。 | [build.yml#L3-L16](file:///workspace/.github/workflows/build.yml#L3-L16) |
| 3 | **基线追踪** | `.upstream-sync-baseline` 文件记录上次同步的上游 SHA，避免重复同步。 | [.upstream-sync-baseline](file:///workspace/.upstream-sync-baseline) |
| 4 | **文档** | README 新增「实时同步上游 FongMi/TV」章节与「模块化开发原则」。 | [README.md#L49-L148](file:///workspace/README.md#L49-L148) |

### v5.5.32 · 2026-08-02 · 修复「内置解析失败」官解线路 qcb 回环后不报错

| # | 模块 | 行为 | 代码位置 |
|---|------|------|---------|
| 1 | **内置解析（三级链路）** | 从 qcb + AI sniff 升级为 **qcb → AI sniff → 传统多解析站/WebView 并发兜底**，官解线路（爱奇艺/腾讯/优酷/B 站）qcb 无官解回环时不再直接 onParseError：`fallbackConcurrentParse` 并发跑 ① 全部 type=1 JSON 解析站 `jsonParse`；② 默认解析站 WebView sniff（按 type 分发）；③ `jsonExtend` 扩展并发；15s 超时，命中即 CAS done=true 取消其余 Future。 | [builtinParse()](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseJob.java#L357-L363)、[fallbackConcurrentParse()](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseJob.java#L373-L417) |
| 2 | qcbHttpCall 兼容性 | url/msg 两字段都解一层嵌套 JSON（有时 qcb 把 `{code,url}` 再塞成字符串），并通过 preferCandidateUrl **优先挑 ≠ 原 URL + 带视频后缀** 的候选，回环结果再被 isSameAsInput 拦截。 | [ParseJob.java#L500-L503](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseJob.java#L500-L503)、[extractQcbUrl/preferCandidateUrl](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseJob.java#L543-L597) |
| 3 | 版本号 | versionCode 580 → **581** / versionName 5.5.31 → **5.5.32** | [app/build.gradle#L22-L23](file:///workspace/app/build.gradle#L22-L23) |

### v5.5.31 · 2026-08-02 · 动态壁纸（视频/GIF） + 壁纸声音默认关闭

| # | 模块 | 行为 | 代码位置 |
|---|------|------|---------|
| 1 | **壁纸渲染** | 复用现有 `CustomWallView` 三分支，**视频壁纸 (TYPE_VIDEO=ExoPlayer 无限循环) / GIF 壁纸 (TYPE_GIF=GifDrawable 帧循环) 均像视频一样动**。静态 (TYPE_RES) 不变化。 | [CustomWallView.java#L41-L45](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/ui/custom/CustomWallView.java#L41-L45)、[loadVideo()/loadGif()](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/ui/custom/CustomWallView.java#L118-L132) |
| 2 | **壁纸声音** | 新增 `Setting.getWallSound() / putWallSound()`，`Prefers.getBoolean` 默认值 `false` → **AI / 设置中壁纸声音默认为关闭**。首次播放动态视频壁纸自动 `mute()`。切换开关 → `ConfigEvent.common()` → `applyWallSound()` 实时 `mute/unmute`。 | [Setting.java#L70-L76](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/setting/Setting.java#L70-L76)、[CustomWallView.java#L70-L81](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/ui/custom/CustomWallView.java#L70-L81) |
| 3 | **设置页 UI** | 手机 fragment_setting.xml + TV activity_setting.xml，在「壁纸」行下方均新增**「壁纸声音」开关一行**（Off / On 显示当前状态，点击切换）。英文 + 简繁中文 strings 文案齐全。 | [SettingFragment.java#L299-L303](file:///workspace/app/src/mobile/java/com/ssmhdssmhd/mxboxs/ui/fragment/SettingFragment.java#L299-L303)、[SettingActivity.java#L273-L277](file:///workspace/app/src/leanback/java/com/ssmhdssmhd/mxboxs/ui/activity/SettingActivity.java#L273-L277)、[values/strings.xml#L132-L133](file:///workspace/app/src/main/res/values/strings.xml#L132-L133) |
| 4 | 版本号 | versionCode 579 → **580** / versionName 5.5.30 → **5.5.31** | [app/build.gradle#L22-L23](file:///workspace/app/build.gradle#L22-L23) |

### v5.5.30 · 2026-08-02 · 解析链路按要求重定义

| # | 模块 | 行为 | 代码位置 |
|---|------|------|---------|
| 1 | **内置解析** | **固定调用** `http://114.134.184.91:9002/jiexi.php?url=<编码地址>`。Setting 默认解析服务器前缀已设为 `http://114.134.184.91:9002`，用户无自定义时自动走该域名。qcb 返回失败时再走 AI 嗅探兜底。 | [Setting.java#L140-L148](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/setting/Setting.java#L140-L148)、[ParseJob.java#L439-L444](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseJob.java#L439-L444) |
| 2 | **超级解析** | **完全改为 AI 自动识别然后解析**。移除原有的第三方 JSON 解析站、WebView 嗅探、qcb xt/api.php 并发链路，直接走纯启发式 AI sniff：① 直链快速 probe → ② 抓正文正则扫候选 URL + probe → ③ 最终宽容 probe 兜底。 | [ParseJob.java#L165-L177](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseJob.java#L165-L177) |
| 3 | 版本号 | versionCode 578 → **579** / versionName 5.5.29 → **5.5.30** | [app/build.gradle#L22-L23](file:///workspace/app/build.gradle#L22-L23) |

---

## 分支说明

| 分支 | 用途 | 说明 |
|------|------|------|
| `main` | 二次开发主分支 | 活跃开发，所有 MXboxS 定制改动在此提交 |
| `upstream-sync` | 上游同步分支 | 由 `sync.yml` 工作流自动维护，含上游 FongMi/TV 最新变更，通过 PR 合入 `main` |

> 历史的 `TV` / `mobile` / `KF` 分支已弃用，所有变体（leanback + mobile）统一在 `main` 分支通过 productFlavors 构建。

---

## 实时同步上游 FongMi/TV

本项目基于 [FongMi/TV](https://github.com/FongMi/TV) 二次开发，通过 GitHub Actions 工作流 [.github/workflows/sync.yml](.github/workflows/sync.yml) **每 6 小时自动同步上游变更**。

### 同步机制

| 项目 | 值 |
|------|-----|
| 上游仓库 | [FongMi/TV](https://github.com/FongMi/TV) |
| 上游分支 | `fongmi` |
| 同步频率 | 每 6 小时（UTC 00:00 / 06:00 / 12:00 / 18:00）+ 手动触发 |
| 同步策略 | `git merge -X ours`（冲突时**保留 MXboxS 定制**为准） |
| 同步目标 | `upstream-sync` 分支 → PR → 人工 review → 合入 `main` |
| 基线追踪 | `.upstream-sync-baseline` 文件记录上次同步的上游 SHA |

### 同步范围

| 模块 | 路径 | 同步方式 | 说明 |
|------|------|----------|------|
| **共享模块** | `catvod/` `chaquo/` `forcetech/` `docs/` `gradle/` | ✅ 自动合并 | 路径与上游一致，上游变更自动合入（冲突保留 MXboxS 版本） |
| **根构建文件** | `build.gradle` `settings.gradle` `gradle.properties` | ✅ 自动合并 | 同上 |
| **app/ 业务代码** | `app/src/*/java/com/fongmi/...` | ⚠️ 仅报告 | 包名不同（`com.fongmi.android.tv` vs `com.ssmhdssmhd.mxboxs`），上游 Java 文件**不引入**（避免编译失败），PR 中生成变更清单供人工 port |
| **app/ 资源** | `app/src/main/res/` | ✅ 自动合并 | 新增资源自动引入，同名冲突保留 MXboxS 版本 |

### 同步流程

```
每 6 小时触发
    │
    ▼
fetch upstream/fongmi
    │
    ▼
对比 .upstream-sync-baseline ──── 无变化 ──→ 跳过
    │
    有变化
    ▼
checkout upstream-sync 分支
    │
    ▼
merge origin/main（同步 MXboxS 最新改动）
    │
    ▼
merge upstream/fongmi -X ours（冲突保留 MXboxS）
    │
    ▼
清理上游 com/fongmi/ app/ Java 文件（避免编译失败）
    │
    ▼
更新 .upstream-sync-baseline
    │
    ▼
push upstream-sync + 创建/更新 PR
    │
    ▼
人工 review → 合入 main → 触发构建
```

### 手动触发同步

在 GitHub Actions 页面选择 `Sync Upstream` 工作流，点击 `Run workflow`：

- **默认**：基于已有 `upstream-sync` 分支继续合并
- **force_recreate=true**：丢弃 `upstream-sync` 历史，从 `main` 重新创建（用于修复同步异常）

### PR 说明

同步 PR 标题格式：`chore(sync): 上游 FongMi/TV 同步 @<上游SHA>`

PR 正文包含：
- 上游最近 30 条提交
- 共享模块变更统计（自动合并部分）
- app/ 业务代码变更清单（需人工 port）
- 全部变更统计

> **合并 PR 前**：请确认 CI 构建通过。合并后 `main` 即包含上游最新共享模块变更。

---

## 模块化开发原则

为降低上游同步冲突，后续 MXboxS 定制开发请遵循以下原则：

1. **新增功能优先放独立文件/类**，避免修改上游同名文件
   - ✅ 新建 `MxboxsXxxParser.java` 而非修改上游的 `Parser.java`
   - ❌ 直接在上游文件中加 MXboxS 专属逻辑

2. **定制逻辑通过继承/组合扩展**，而非直接修改上游代码
   - ✅ `class MxboxsPlayer extends UpstreamPlayer`
   - ❌ 在 `UpstreamPlayer.java` 中加 `if (mxboxs) ...`

3. **资源文件使用 `mxboxs_` 前缀**，避免与上游资源冲突
   - ✅ `mxboxs_ic_feature.xml`、`mxboxs_string_feature`
   - ❌ 修改上游 `ic_feature.xml`、`string_feature`

4. **build.gradle 改动隔离**到 MXboxS 专属配置块
   - ✅ 新增 `// MXboxS custom` 注释块
   - ❌ 分散修改上游已有配置行

5. **包名引用统一使用 `com.ssmhdssmhd.mxboxs`**，不混用上游包名

---

## 项目架构

| 项目 | 值 |
|------|-----|
| 应用名称 | MXboxS |
| 包名 | `com.ssmhdssmhd.mxboxs` |
| 版本 | v5.5.42 (591) |
| 最低 SDK | 24（Android 7.0） |
| 架构 | `arm64-v8a`、`armeabi-v7a` |
| 构建变体 | `leanback`（电视版）、`mobile`（手机版） |

### 云端编译

GitHub Actions 自动编译 **TV (leanback)** + **手机 (mobile)** 两个变体，各含 `arm64-v8a` 与 `armeabi-v7a` 两种架构，提交到 `main` 分支即可触发；推送 `v*` tag 会额外创建 GitHub Release。同步 PR 也会触发构建验证（不上传 APK）。

构建产物可在 [Actions](https://github.com/ssmhdssmhd/MXboxS/actions) 页面下载，统一打包为 `MXboxS-Release-APKs` Artifact，包含：
- `MXboxS-mobile-arm64_v8a-5.5.42.apk`（手机版 arm64，推荐主流机型）
- `MXboxS-mobile-armeabi_v7a-5.5.42.apk`（手机版 32 位，老旧机型）
- `MXboxS-leanback-arm64_v8a-5.5.42.apk`（电视版 arm64，推荐盒子/电视）
- `MXboxS-leanback-armeabi_v7a-5.5.42.apk`（电视版 32 位）

### v5.5.24 本地构建产物校验

| APK 文件 | 大小 | SHA-256 |
|----------|------|---------|
| `MXboxS-mobile-arm64_v8a-5.5.24.apk` | 47 MB | `9c3ff40818b9eacdc902e623ff00dac822eb5cb486ae4d4a97e2deac99ecb90c` |
| `MXboxS-mobile-armeabi_v7a-5.5.24.apk` | 40 MB | `5969d95f121c48452a604fe7d094b276f361da8477ad1964957f5b0d2192e7b3` |
| `MXboxS-leanback-arm64_v8a-5.5.24.apk` | 47 MB | `56ca2ef16a4a3314fb06f5fdee2ad5c337625f350d2d70ed80c905a2daa01a38` |
| `MXboxS-leanback-armeabi_v7a-5.5.24.apk` | 40 MB | `1677fd77a4be5d593d99ae8e791b7b57e7e1ee9fba8cb1862b2b79d491c2fbb1` |

### 本地编译

```bash
# 手机版（Mobile）
./gradlew assembleMobileArm64_v8aRelease       # arm64-v8a
./gradlew assembleMobileArmeabi_v7aRelease      # armeabi-v7a

# 电视版（Leanback / Android TV）
./gradlew assembleLeanbackArm64_v8aRelease      # arm64-v8a
./gradlew assembleLeanbackArmeabi_v7aRelease    # armeabi-v7a
```

```
├── app/
│   ├── src/main/      共享业务逻辑
│   ├── src/mobile/    手机端 UI
│   └── src/leanback/  电视端 UI（mobile 分支已移除）
├── catvod/            爬虫抽象层
├── quickjs/           QuickJS JavaScript 引擎
├── chaquo/            Chaquopy Python 引擎
├── thunder/           迅雷下载引擎
├── tvbus/             TVBus 直播引擎
├── forcetech/         ForceTech 直播引擎
├── zlive/             ZLive 引擎
├── jianpian/          简片引擎
├── hook/              Chromium WebView Hook
└── docs/              配置文档
```

---

## 文档

| 文件 | 说明 |
|------|------|
| [CHANGELOG.md](CHANGELOG.md) | 版本更新日志 |
| [MOBILE_APP_ANALYSIS.md](MOBILE_APP_ANALYSIS.md) | Mobile App 代码分析（图标、页面、调用方式） |
| [docs/CONFIG.md](docs/CONFIG.md) | Vod / Live 配置说明 |
| [docs/SPIDER.md](docs/SPIDER.md) | Spider 爬虫接口规范 |
| [docs/LOCAL.md](docs/LOCAL.md) | 本地 HTTP API 说明 |
| [docs/LIVE.md](docs/LIVE.md) | 直播源格式说明 |

---

## 功能特性

### 播放器
- 多引擎可切换（设置中选择，默认可选）：
  - **ExoPlayer (Media3 1.11.0-rc01)**：默认引擎，支持 HLS/DASH/SmoothStreaming/RTSP、DRM (Widevine/PlayReady/ClearKey)、AI 画质优化（强制最高码率 + 视频缩放裁剪）、OkHttp + 磁盘缓存
  - **MPV (MpvPlayer)**：硬解 mediacodec-copy、画质 profile=high-quality、去环路滤波、soxr 重采样、字幕样式可配、Vulkan/GpuNext 开关、磁盘预载
  - **System Player**：系统原生播放器
  - **Ali / Nova / IJK**：兼容扩展引擎（引擎选择器中提供，缺失底层库时自动回退到 ExoPlayer）
- 渲染：SurfaceView（默认） / TextureView，支持隧道模式（Tunneling）
- 字幕：SRT / SSA / ASS 外挂字幕，支持动态添加、位置/大小调节
- 画中画（PiP）、倍速播放、背景音频（含 PiP 模式）
- 详细引擎参数：音频直通、音频/视频软解偏好、AAC 优先、DV7 HEVC 回退、隧道模式、AdBlock、UA、长按倍速等
- 自动降级：DRM 或 SMB 源自动切换到 EXO；MPV 不可用时回退 EXO；Ali/Nova/IJK 不可用时回退 EXO
- 进度条旁 **全屏按钮**：进入/退出全屏不再依赖旋转方向，新手用户可直接点击图标
- **内置 m3u8/mp4 解析器（Built-in）**：直出解析或启发式正则嗅探页面内视频直链，不依赖第三方解析站

### 点播
- 多站点分类浏览，Filter 筛选
- 多站点并行搜索
- 播放失败自动换源
- **超级解析（AI 自动识别然后解析）**：纯启发式本地嗅探，不依赖任何第三方解析站/云端接口/WebView：
  1. 若 webUrl 本身就是 m3u8/mp4/flv/m4v/ts/mkv/webm 直链 → 可达性 probe 通过即直接播放；
  2. 否则抓取页面正文，正则扫常见视频 URL（含相对路径拼接）→ 命中后逐个 probe 可达性 → 播放；
  3. 全部失败时兜底：拿原 URL 做一次 Content-Type/Content-Length 宽容 probe。
- 观看记录、收藏、无痕模式
- 手势控制（亮度/音量/进度）

### 直播
- 支持 M3U、TXT、JSON 格式直播源
- EPG 节目单（XMLTV，支持 .gz）
- 频道收藏、密码保护

### 爬虫引擎
- Java JAR（DexClassLoader）
- JavaScript（QuickJS）
- Python（Chaquopy）

### 网络
- DoH（DNS over HTTPS）
- HTTP / HTTPS / SOCKS 代理
- 广告拦截
- WebView 嗅探

---

## 原始项目

- [FongMi/TV](https://github.com/FongMi/TV) — 原始开源项目
- [讨论群组](https://t.me/fongmi_official)
- [发布频道](https://t.me/fongmi_release)