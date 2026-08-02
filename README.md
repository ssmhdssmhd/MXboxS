# MXboxS

基于 [FongMi/TV](https://github.com/FongMi/TV) 的二次开发项目，覆盖 **Android TV（leanback）** 与 **手机端（mobile）** 的影视应用。

[![Build MXboxS Release](https://github.com/ssmhdssmhd/MXboxS/actions/workflows/build.yml/badge.svg)](https://github.com/ssmhdssmhd/MXboxS/actions/workflows/build.yml)

---

## 最新更新

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
| `main` | 二次开发主分支 | 活跃开发，所有改动在此提交 |
| `TV` | 原始源码（不动） | FongMi/TV 纯净源码，作为开发环境参考 |
| `mobile` | 手机端专用 | 仅保留 mobile 代码，已移除 leanback 电视端代码 |
| `KF` | KF 分支 | 完整代码副本 |

---

## 项目架构

| 项目 | 值 |
|------|-----|
| 应用名称 | MXboxS |
| 包名 | `com.ssmhdssmhd.mxboxs` |
| 版本 | v5.5.32 (581) |
| 最低 SDK | 24（Android 7.0） |
| 架构 | `arm64-v8a`、`armeabi-v7a` |
| 构建变体 | `leanback`（电视版）、`mobile`（手机版） |

### 云端编译

GitHub Actions 自动编译 **TV (leanback)** + **手机 (mobile)** 两个变体，各含 `arm64-v8a` 与 `armeabi-v7a` 两种架构，提交到 `main` 或 `mobile` 分支即可触发；推送 `v*` tag 会额外创建 GitHub Release。

构建产物可在 [Actions](https://github.com/ssmhdssmhd/MXboxS/actions) 页面下载，统一打包为 `MXboxS-Release-APKs` Artifact，包含：
- `MXboxS-mobile-arm64_v8a-5.5.32.apk`（手机版 arm64，推荐主流机型）
- `MXboxS-mobile-armeabi_v7a-5.5.32.apk`（手机版 32 位，老旧机型）
- `MXboxS-leanback-arm64_v8a-5.5.32.apk`（电视版 arm64，推荐盒子/电视）
- `MXboxS-leanback-armeabi_v7a-5.5.32.apk`（电视版 32 位）

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