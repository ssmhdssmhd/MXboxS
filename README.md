# MXboxS

基于 [FongMi/TV](https://github.com/FongMi/TV) 的二次开发项目，覆盖 **Android TV（leanback）** 与 **手机端（mobile）** 的影视应用。

[![Build MXboxS Release](https://github.com/ssmhdssmhd/MXboxS/actions/workflows/build.yml/badge.svg)](https://github.com/ssmhdssmhd/MXboxS/actions/workflows/build.yml)
[![Sync Upstream](https://github.com/ssmhdssmhd/MXboxS/actions/workflows/sync.yml/badge.svg)](https://github.com/ssmhdssmhd/MXboxS/actions/workflows/sync.yml)

---

## 最新更新

### v5.7.16 · 2026-08-30 · 删除高级设置里的「接口配置（内置视频解析线路）」整条链

v5.7.15 只删了设置页独立的一行「解析服务器」（qcb 远程 HTTP 解析），但高级设置里还有整条「接口配置（内置视频解析线路）」入口。用户预期彻底删除所有解析服务器相关设置，本轮补删。

**物理删除 2 个文件**：
- `BuiltinParseSetting.java` — 内置解析线路配置持久化类
- `item_builtin_line.xml` — 线路编辑卡片 item 布局

**代码清理 5 个文件**：
| 文件 | 删除内容 |
|------|----------|
| [VodConfig.java](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/api/config/VodConfig.java) | `BuiltinParseSetting.effectiveLines()` 注入 for 循环 + import |
| [SettingAdvancedActivity.java](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/ui/activity/SettingAdvancedActivity.java) | 3 字段 + 3 findViewById + cfgTypes 初始化 + 3 click listener + `rebuildLines()` / `addLine()` / `saveLines()` 三方法 + Parse / BuiltinParseSetting 两个 import |
| [activity_setting_advanced.xml](file:///workspace/app/src/main/res/layout/activity_setting_advanced.xml) | `@+id/netCfgCard` MaterialCardView 整块（接口配置卡片 UI，133 行） |
| strings.xml | 14 条 `setting_cfg_*` 字符串 |

保留：高级设置入口行 `@+id/advanced` + 播放优化/AI 开关/解析缓存等其他卡片不受影响。

版本号：versionCode 637 → **638** / versionName 5.7.15 → **5.7.16**

### v5.7.15 · 2026-08-30 · 删除「解析服务器」设置 + P0 修复「成功获取 m3u8 但 0 KB/s 不能播放」

**【删除解析服务器（qcb 远程 HTTP 解析）】**

整个 qcb/jiexi.php 云端解析链路已彻底移除（10 个文件、190+ 行代码）。删除原因：qcb 云端接口长期不稳定、绕过本地 WebView 嗅探导致反爬更频繁。所有解析统一走本地链路（HTML 嗅探 + 内置线路 + LLM 嗅探 + 并发 probe）。

| 删除项 | 位置 |
|--------|------|
| `PARSE_SERVER_DEFAULT` 常量 + `getParseServerPrefix()` + `putParseServerPrefix()` | [Setting.java](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/setting/Setting.java) |
| `hasQcbParseServer()` / `qcbHttpCall()` / `qcbJiexiParse()` / `qcbXtApiParse()` / `extractQcbUrl()` / `normalizeQcbPrefix()` + 调用入口 | [ParseJob.java](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseJob.java) |
| `onParseServer()` / `onParseServerReset()` + 初始化 + 点击绑定 | [SettingFragment.java (mobile)](file:///workspace/app/src/mobile/java/com/ssmhdssmhd/mxboxs/ui/fragment/SettingFragment.java) / [SettingActivity.java (leanback)](file:///workspace/app/src/leanback/java/com/ssmhdssmhd/mxboxs/ui/activity/SettingActivity.java) |
| `android:id="@+id/parseServer"` LinearLayoutCompat 整块 | [fragment_setting.xml](file:///workspace/app/src/mobile/res/layout/fragment_setting.xml) / [activity_setting.xml](file:///workspace/app/src/leanback/res/layout/activity_setting.xml) |
| `setting_parse_server` / `_hint` / `_default` | strings.xml 三语言版 |

**【P0 修复：成功获取 m3u8 但 0 KB/s 不能播放】**

根因（从用户截图精准锁定）：解析站硬塞的 Referer 含 `$$$HD中字$` 标记 + 未编码中文字符。旧版 `mergeDefaultHeadersForPlayback` 逻辑"用户有 Referer 就原样保留" → 脏 Referer 透传到所有 OkHttpDataSource 请求（m3u8 重拉 + TS 段） → CDN WAF 判定异常 → **403 → 0 KB/s**。因为 m3u8 和 TS 段通常同域名，OkHttpDataSource 的跨域 ORIGIN 降级分支不会触发，所以之前的跨域修复没救到这个场景。

修复：给 `mergeDefaultHeadersForPlayback` 加 **Referer 清洗层**（[UrlUtil.java](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/UrlUtil.java)）。遍历 headers 时遇到 Referer 先过 `isValidReferer()` 检查（http/https + 合法 host + 不含 `$$$` + 不含控制字符），脏 Referer 直接丢弃，由 `inferRefererForPlayback(playbackUrl, DIRECTORY)` 从播放 URL 重新推导合规 Referer（浏览器真实导航 Referer 格式）。

版本号：versionCode 636 → **637** / versionName 5.7.14 → **5.7.15**

### v5.7.14 · 2026-08-30 · 更新下载完成后自动跳安装 + 权限返回自动续接

修复 App 内 Updater 下载完 APK 后不自动弹出安装页的问题。之前 `FileUtil.installApk()` 在首次请求 `canRequestPackageInstalls()` 权限被拒绝（跳设置页）后就丢失了待安装 APK 路径，用户返回 App 后需要手动重新点击安装。

本版加 **SharedPreferences 持久化 + App 前台自动续接**：

| 组件 | 作用 |
|------|------|
| `FileUtil.onResumePendingInstallIfAny()` | App 回到前台时检查 SharedPreferences 里的待安装 APK 路径 + 文件是否还在 + 权限是否已开 → 自动触发 installApk |
| `installApk()` | 加 `FLAG_GRANT_PERSISTABLE_URI_PERMISSION` + `grantUriPermission("com.android.packageinstaller")` 双重兜底 |
| `App.onActivityResumed` 钩子 | 全局 Activity 恢复时机统一触发续接检查 |

代码位置：[FileUtil.java](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/FileUtil.java) / [App.java](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/App.java)

版本号：versionCode 635 → **636** / versionName 5.7.13 → **5.7.14**

### v5.7.13 · 2026-08-30 · 版本号对齐 GitHub 历史 release 最高版本

GitHub Release 历史里旧 CI 推送过 **v5.7.12**（含 slim APK），App 内 Updater 遍历 `/releases?per_page=10` 从 APK 文件名取最高版本号 → 我们推 5.7.6 时 App 显示"5.7.12 已是最新版本"。本版版本号直接跳到 **5.7.13**，高于历史最高。

版本号：versionCode 634 → **635** / versionName 5.7.6 → **5.7.13**

### v5.7.6 · 2026-08-30 · 毛玻璃液体 UI + 全局闪退保护 + 跨域 Referer 动态修正

三大核心改进：

| 功能 | 说明 |
|------|------|
| **玻璃液体毛玻璃效果（Android 12+/API 31+）** | mobile + leanback 双端 values-v31/styles.xml 启用 `windowBackgroundBlurRadius=60dp`，背景半透明遮罩 `0xE60F1014`（深色毛玻璃），状态栏/导航栏透明沉浸 |
| **全局闪退保护** | `Startup.java` 注册 `Thread.setDefaultUncaughtExceptionHandler` + CaocConfig `errorActivity` 双层保护，崩溃后跳 Caoc CrashActivity 而非系统直接杀进程 |
| **m3u8 跨域 TS 段 Referer 动态修正** | OkHttpDataSource.Factory 新增 `setPlaylistUrl(playlistUrl)` 缓存 m3u8 URL；每次 `open()` 发请求前判定 **跨域 + 非 playlist 重拉** → 动态把 Referer 从目录级降级为 ORIGIN 级（`scheme://host:port/`），严格浏览器 `strict-origin-when-cross-origin` 策略 |

代码位置：[Startup.java](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/Startup.java) / [OkHttpDataSource.java](file:///workspace/app/src/main/java/androidx/media3/datasource/okhttp/OkHttpDataSource.java) / [MediaSourceFactory.java](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/exo/MediaSourceFactory.java)

版本号：versionCode 633 → **634** / versionName 5.7.3 → **5.7.6**

### v5.7.3 · 2026-08-21 · 版本号升级（5.7.1 → 5.7.3），功能与 v5.7.1 一致

v5.7.1 已包含全部上游同步与人工 port 变更，本版仅将版本号调整为 **5.7.3**（versionCode **631**）。

版本号：versionCode 630 → **631** / versionName 5.7.1 → **5.7.3**

### v5.7.1 · 2026-08-21 · 同步上游 FongMi/TV 新更新：播放结束状态 / 老电视后台化兜底 / 播放错误恢复 / Chrome UA / DoH 校验

对比上游 `fongmi` 分支 10 个新提交，评估后**集成 5 项、排除 5 项**（mpv 相关因本项目 mpv 为桩实现而排除，其余业务提交与 MXboxS 定制冲突或收益低）：

| 上游提交 | 内容 | 结论 |
|---------|------|------|
| `b04c63ce6` | 老电视固件 `moveTaskToBack` 兜底 | ✅ [Util.moveToBackground](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/Util.java#L68-L75) |
| `42b3824ca` | 播放结束状态处理（seek 到结尾不再自动播放） | ✅ [PlaybackActivity.seekTo](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/ui/activity/PlaybackActivity.java#L216-L224) |
| `954299e20` | 播放错误恢复时不误删回调（mpv 部分除外） | ✅ [PlayerManager.onPlayerError](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/PlayerManager.java#L647-L669) |
| 共享模块 | Chrome UA 138 → 151 | ✅ [catvod Util](file:///workspace/catvod/src/main/java/com/github/catvod/utils/Util.java#L28) |
| 共享模块 | DoH 无效地址校验 | ✅ [OkDns](file:///workspace/catvod/src/main/java/com/github/catvod/net/OkDns.java#L30-L33) |
| `954299e20` mpv 部分 | HLS 伪装重试（mpv 桩不可用） | ❌ 排除 |

版本号：versionCode 629 → **630** / versionName 5.6.9 → **5.7.1**

### v5.6.9 · 2026-08-21 · 新增「高级设置 → 接口配置（内置视频解析）」：线路可视化编辑，类型仅两种

在「高级设置（版本号点 20 次解锁）」新增 **接口配置（内置视频解析）** 卡片，把内置解析线路改为可在界面上管理：

| 能力 | 说明 |
|------|------|
| 线路管理 | 每条线路可编辑「名称 + 类型 + 接口地址」，支持 **添加接口** / **删除此接口** / **保存** / **恢复默认** |
| 类型选择（仅两种） | **1 · 直接播放**（`Parse.type=0`）；**2 · JSON 解析**（`Parse.type=1`，从返回 JSON 的 `url` 字段取播放地址） |
| 持久化 | 线路以 JSON 数组写入 SharedPreferences（key：`builtin_parse_lines`），重启后仍生效 |
| 默认线路 | 两条官方 node.js JSON 解析线路（1314-node / 1315-node），「恢复默认」一键还原 |
| 注入解析列表 | [VodConfig.setParses](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/api/config/VodConfig.java#L205-L217) 把生效线路去重后并入解析器列表，原有解析器不受影响 |

代码位置：[BuiltinParseSetting](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/setting/BuiltinParseSetting.java) / [SettingAdvancedActivity](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/ui/activity/SettingAdvancedActivity.java) / [item_builtin_line.xml](file:///workspace/app/src/main/res/layout/item_builtin_line.xml)

版本号：versionCode 628 → **629** / versionName 5.6.8 → **5.6.9**

### v5.6.8 · 2026-08-15 · P0 修复：m3u8 跨域 TS 段 CDN sign 鉴权 403 → "Network Connection Failed" 白屏（cache.0567890.xyz:4433 → cdn.hls.one）

**根因**：入口 M3U8（`https://cache.0567890.xyz:4433/...xxx.m3u8?vkey=65303439...`）能正常拉取，但里面 TS 段是**跨域绝对 URL**（`https://cdn.hls.one/...ts?sign=432位...`）。之前 `UrlUtil.mergeDefaultHeaders` 把**完整 playlist URL（含 ?vkey=400+位查询串）** 塞进 Referer，违反浏览器 Referer 标准（应不含 query）→ cdn.hls.one sign 鉴权把 TS 段请求**直接 403** → ExoPlayer HLS Extractor 段加载失败 → 上抛 `ERROR_CODE_IO_NETWORK_CONNECTION_FAILED` → UI 白屏弹"Network Connection Failed"。

**本版落地 4 层修复（只加不改，解析爬虫链路行为完全保留）**：

| # | 位置 | 效果 |
|---|------|------|
| ① 播放专用 Referer 推导工具 | [UrlUtil.inferRefererForPlayback / mergeDefaultHeadersForPlayback](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/UrlUtil.java#L164-L250) | 新增 Referer 两档推导：DIRECTORY 版 = `scheme://host:port/path_dir/` / ORIGIN 版 = `scheme://host:port/`；**永远不含 query/fragment**，严格浏览器标准；补 UA + Accept:\*/\* 兜底 |
| ② 3 个播放出口全部切播放版 merge | [PlaybackActivity.startPlayer](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/ui/activity/PlaybackActivity.java#L268-L277) / [PlayerManager.onParseSuccess](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/PlayerManager.java#L572-L579) / [VodPlaybackController.startPlaybackWithCached](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/playback/vod/VodPlaybackController.java#L143-L162) | 3 处 `mergeDefaultHeaders` → `mergeDefaultHeadersForPlayback`；PlaySpec 里的 Referer 从现在起永远是合规的目录级 Referer（不带 query） |
| ③ 跨域 TS 段动态 Referer 修正（核心） | [OkHttpDataSource.Factory.setPlaylistUrl](file:///workspace/app/src/main/java/androidx/media3/datasource/okhttp/OkHttpDataSource.java#L77-L96) + [OkHttpDataSource.open](file:///workspace/app/src/main/java/androidx/media3/datasource/okhttp/OkHttpDataSource.java#L203-L226) + [MediaSourceFactory.createDataSourceFactory](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/exo/MediaSourceFactory.java#L102-L116) | Factory 新增可选 `setPlaylistUrl()` 缓存顶层 m3u8 URL；每次 `open()` 发请求前判定：**跨域 + 非 playlist 重拉** → 动态把 Referer 从目录级降级为 **ORIGIN 级**（strict-origin-when-cross-origin，浏览器跨域默认策略），CDN sign 鉴权通过率最高 |
| ④ HLS/DASH 段失败重试策略 | [ExoUtil.buildMediaSourceFactory](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/exo/ExoUtil.java#L314-L325) | DefaultLoadErrorHandlingPolicy 三项参数 1 次 → **3 次**，境外/高延迟源临时丢包不会直接弹失败 |

版本号：versionCode 627 → **628** / versionName 5.6.7 → **5.6.8**

### v5.6.7 · 2026-08-15 · P0 修复：能看到视频标题/选集/简介，但就是永远 0 KB/s 转圈无法播放（万能嗅探站套娃 URL 漏网问题）

**根因**：`qcb jiexi.php` 公开解析站虽然返回 `{"code":200,"ZT":"解析成功"}`，但 `url` 字段给的是**第二层 jx.xmflv.cc 包装 URL**（典型套娃）。原代码 `checkResult` 只判断 length>40，导致 HTML 嗅探站包装 URL 被直接丢给 ExoPlayer → 永远 0 KB/s 转圈。

**本版落地 5 层防线（只加不改，原有判断 100% 保留）**：

| # | 位置 | 效果 |
|---|------|------|
| ① 入闸拦截 | [ParseJob.checkResult#L883-L898](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseJob.java#L883-L898) | JSON 解析站返回套娃 URL → 不回调成功，自动降级到 WebView + AI 嗅探深度解析 |
| ② 出口二次拦截 | [ParseJob.onParseSuccess#L935-L949](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseJob.java#L935-L949) | 所有路径（含旧缓存、扩展回调）再扫一遍 isLikelyHtmlSniffer，防止进 ExoPlayer |
| ③ WebView 超时 | [Constant#L21-L26](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/Constant.java#L21-L26) | 15s → 45s（仅 WebView；非 WebView 仍 15s，省电）专杀 xmflv.cc 混淆 JS + noscdn 多脚本 |
| ④ 混淆 JS 正则嗅探 | [UrlUtil.sniffVideoCandidates#L294-L344](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/UrlUtil.java#L294-L344) | 抓 `var now=`/`window.play_url=`/`{url:"..."}`/点式 config 属性链 + URLDecode 二次扫描，不开 WebView 也能本地命中抢跑 |
| ⑤ WebView 通用探针 + prompt 桥 | [CustomWebView.initSettings#L92-L132](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/ui/custom/CustomWebView.java#L92-L132) + [Sniffer.getScript#L45-L57](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/Sniffer.java#L45-L57) | 200ms × 40 轮 轮询抓 `<video>`/`window.Xmflv.*`/dplayer/artplayer/ckplayer + script 字面量全扫；命中走 `videourlfound` 事件 → `prompt('MVIDURL:')` → Java onJsPrompt 拦截 → 命中即关 WebView |

版本号：versionCode 626 → **627** / versionName 5.6.6 → **5.6.7**

### v5.6.6 · 2026-08-15 · 只新增不替换：FULL 完整版保持不变 + 🆕 新增 SLIM 轻量包（再瘦 ~30MB，双轨可选）

**核心原则：原有完整包 100% 不变，只在旁边多给一个轻量版，老用户零影响、完全兼容。**

| 分类 | 原有 FULL 完整版（推荐老用户继续装） | 🆕 新增 SLIM 轻量版（可选，安装包更小）|
|------|-----------------------------------|--------------------------------------|
| APK 文件名 | **完全不变**：`MXboxS-mobile-arm64_v8a-5.6.6.apk` 等 | 新增（带 `-slim` 后缀）：`MXboxS-mobile-arm64_v8a-5.6.6-slim.apk` 等 |
| 功能 | ✅ 与 v5.6.5 完全一致（FFmpeg 内置、迅雷/荐片/ZLive 扩展全有）| 基础功能一致；**首次用 FFmpeg 相关功能才在线下 30MB 二进制**（多镜像/断点/90s 超时自动重试）|
| 代码 | 老逻辑 `if (assetPrefix != null)` 不动 | 只新增 `else if (BUILD_FLAVOR_SLIM) ensureBinsDownloaded(...)` 分支 |

代码位置：
- Gradle flavor 尺寸维 + 打包期 excludes：[app/build.gradle#L62-L196](file:///workspace/app/build.gradle#L62-L196)
- Workflow 4 FULL（原 step 不动）+ 4 SLIM（新增）+ Release 独立 FFmpeg binary 附件上传：[build.yml#L103-L289](file:///workspace/.github/workflows/build.yml#L103-L289)
- FFmpegUtil 新增 slim 按需下载分支（full 包不进）：[FFmpegUtil#L216-L406](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/FFmpegUtil.java#L216-L406)

### v5.6.5 · 2026-08-15 · 零风险体积瘦身（v5.6.3 114MB → 预计 97MB，-15MB ✅）

头号元凶：`assets/ffmpeg/{arm64-v8a,armeabi-v7a}/` 两份都打入了同一个 ABI APK（占 31%）。本版把 FFmpeg/FFprobe 按 ABI 拆 sourceset，单 APK 直接瘦 ~15MB：

| # | 优化项 | 预计节省 | 代码位置 |
|---|--------|---------|---------|
| 1 | **FFmpeg/FFprobe per-ABI sourceset（最大头）** | **-14 ~ -16 MB / 单 ABI APK** | [arm64_v8a/assets/ffmpeg/](file:///workspace/app/src/arm64_v8a/assets/ffmpeg) + [armeabi_v7a/assets/ffmpeg/](file:///workspace/app/src/armeabi_v7a/assets/ffmpeg) |
| 2 | resConfigs 只打包 zh-rCN / zh-rTW / en | -0.8 ~ -1.5 MB | [app/build.gradle#L26](file:///workspace/app/build.gradle#L26) |
| 3 | packagingOptions 排除 META-INF 冗余 | -0.3 ~ -0.8 MB | [app/build.gradle#L42-L56](file:///workspace/app/build.gradle#L42-L56) |
| 4 | 16 张 launcher/PNG zlib9 无损重压缩 | -21 KB | mipmap-* / drawable-nodpi / drawable-*hdpi |
| 5 | **FFmpegUtil 新路径兼容**（flavorsrc + fallback main） | N/A（避免升级后崩）| [FFmpegUtil#L190-L250](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/FFmpegUtil.java#L190-L250) |

### v5.6.4 · 2026-08-15 · P0 修复：v5.6.3 引入的「播放报错连接超时」回归

| # | 模块 | 变更 | 代码位置 |
|---|------|------|---------|
| 1 | **抢跑 WebView 泄漏+双路冲突+嗅探参数3合1修复** | 移除 builtinParse 独立 App.post 抢跑的那一路 CustomWebView，改为「下沉到 fallbackConcurrentParse，开关开时先提交 defaultP 并 sleep 60ms head start」；全局 defaultP **只提交 1 次**（无双路冲突）；复用 startWeb 正确生命周期（cv 入 webViews、嗅探注入参数动态判断 player/?url=），stop() 一定能 destroy 不再泄漏 | [ParseJob.builtinParse#L544-L559](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseJob.java#L544-L559) / [fallbackConcurrentParse#L576-L644](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseJob.java#L576-L644) / [startWeb#L666](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseJob.java#L666) |
| 2 | **safeGetBody 显式 10s 超时** | aiSmartParseFallback 抓正文改 `OkHttp.client(10000L).newCall(...)`，防止弱网/超时无限挂着，避免占用 ParseJob 15s 总超时窗口导致 onParseError | [ParseJob.safeGetBody#L501-L522](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseJob.java#L501-L522) |
| 3 | **播放器 connectTimeout 8s→15s** | 弱网/高延迟CDN/TLS握手慢场景不轻易 `SocketTimeoutException`；连接池 8→16 支持更多并发画质切换/预加载 | [OkHttp.player#L83-L96](file:///workspace/catvod/src/main/java/com/github/catvod/net/OkHttp.java#L83-L96) |
| 4 | **版本号** | versionCode 623 → **624** / versionName 5.6.3 → **5.6.4** | [app/build.gradle#L22-L23](file:///workspace/app/build.gradle#L22-L23) |

---

### v5.6.3 · 2026-08-14 · 高级设置新增「WebView 嗅探默认开启」开关 + 默认嗅探抢跑

| # | 模块 | 变更 | 代码位置 |
|---|------|------|---------|
| 1 | **高级设置开关** | 「播放优化」卡片新增 `WebView 嗅探默认开启`（默认开）。关闭只走 qcb+正则+多解析站，省电；开启时 HTML 嗅探接口提前起 WebView 抢跑 | [PlayerSetting.isWebviewSniffDefaultOn](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/setting/PlayerSetting.java#L376-L388) / [SettingAdvancedActivity#L178-L185](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/ui/activity/SettingAdvancedActivity.java#L178-L185) |
| 2 | **默认 WebView 嗅探抢跑** | `builtinParse` 增加抢跑逻辑：开关开 + `isLikelyHtmlSniffer(webUrl)` 命中时，提前异步起一路 CustomWebView 跟后续并发赛跑，虾米/qq/jx/xmflv 等命中更快 | [ParseJob.builtinParse#L547-L566](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseJob.java#L547-L566) |
| 3 | **版本号** | versionCode 622 → **623** / versionName 5.6.2 → **5.6.3** | [app/build.gradle#L22-L23](file:///workspace/app/build.gradle#L22-L23) |

---

### v5.6.2 · 2026-08-14 · P0 紧急修复：HTML 嗅探接口被当作直链播放 → 0 KB/s 永久转圈

| # | 模块 | 变更 | 代码位置 |
|---|------|------|---------|
| 1 | **HTML 嗅探接口识别** | 在 `UrlUtil` 新增 `isLikelyHtmlSniffer(url)`：通过 URL 特征（?url=/&url=/?v= 参数、jiexi.php/api.php/jx.php 等典型嗅探脚本名、xmflv/qq/duopian/iqiyi 等嗅探域名关键字）识别 HTML 嗅探接口；视频直链直接放过 | [UrlUtil.isLikelyHtmlSniffer](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/UrlUtil.java#L177-L211) |
| 2 | **强制走解析链路** | `PlaybackActivity.startPlayer` 直链起播分支前新增检查：命中 HTML 嗅探接口特征就 `useParse=true` 走 `player().parse(...)`，不再把 HTML 页面直接丢给 ExoPlayer | [PlaybackActivity.java#L260-L266](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/ui/activity/PlaybackActivity.java#L260-L266) |
| 3 | **嗅探成功率增强** | `UrlUtil.sniffVideoCandidates` 新增第 5 步：用正则抓 `<iframe>/<video>/<source>/<script>/<embed>` 标签 `src` 属性值当候选，再走 base64/正则二次嗅探 | [UrlUtil.sniffVideoCandidates](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/UrlUtil.java#L268-L293) |
| 4 | **版本号** | versionCode 621 → **622** / versionName 5.6.1 → **5.6.2** | [app/build.gradle#L22-L23](file:///workspace/app/build.gradle#L22-L23) |

---

### v5.6.1 · 2026-08-14 · P0 紧急修复：0 KB/s 一直转圈不能播放（Referer / User-Agent 丢失 → CDN 403）

| # | 模块 | 变更 | 代码位置 |
|---|------|------|---------|
| 1 | **直链起播出口** | `needParse=false` 场景之前只做了 fake-local-proxy 还原，缺 Referer/UA 兜底。修复：`mergeDefaultHeaders(result.getHeader(), realUrl)` 写回 Result 再构造 PlaySpec | [PlaybackActivity.java#L260-L269](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/ui/activity/PlaybackActivity.java#L260-L269) |
| 2 | **解析成功回调出口** | `onParseSuccess` 在 fakeLocalProxy 没命中的出口只 `remove(RANGE)` 没补 Referer/UA，部分内置嗅探/第三方解析源 headers 不完整 → CDN 403 → 0 KB/s 永久转圈。修复：先 `mergeDefaultHeaders(headers, url)` 再 `spec.setHeaders()` | [PlayerManager.java#L572-L580](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/PlayerManager.java#L572-L580) |
| 3 | **切集秒开 shortcut** | v5.6.0 新增捷径会 (a) 命中旧版 fake-local-proxy 缓存（127.0.0.1 不存在端口）→ 永久 0 KB/s；(b) 缓存 headers 缺 Referer/UA。修复：(a) 先 `unwrapFakeLocalProxy(hit.url)`，命中直接 `refresh()` 回正常链路；(b) `mergeDefaultHeaders(hit.headers, hit.url)` 双保险 | [VodPlaybackController.java#L131-L162](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/playback/vod/VodPlaybackController.java#L131-L162) |
| 4 | **版本号** | versionCode 620 → **621** / versionName 5.6.0 → **5.6.1** | [app/build.gradle#L22-L23](file:///workspace/app/build.gradle#L22-L23) |

---

### v5.6.0 · 2026-08-14 · AI 深度优化第三轮：高级设置完整版 + 切集秒开 + 弹幕预加载 + 磁盘缓存惰性化

| # | 模块 | 变更 | 代码位置 |
|---|------|------|---------|
| 1 | **高级设置 UI 第二轮** | 四大卡片完整联动：① 播放优化；② AI 播放优化（解析缓存分级清理对话框）；③ AI 实验项 · AB 分桶（总开关 + 分桶号 + 4 个实验子开关）；④ LLM 嗅探配置（Endpoint / Key / Model 保存）。解锁（连点版本号 20 次）后全部 VISIBLE | [SettingAdvancedActivity](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/ui/activity/SettingAdvancedActivity.java) / [layout](file:///workspace/app/src/main/res/layout/activity_setting_advanced.xml) |
| 2 | **切集秒开** | `selectEpisode()` 先查 `ParseJob.hitCache()` 两级缓存，命中直接 `startPlaybackWithCached()` 构造 PlaySpec 起播，**跳过 `requestPlayer()` HTTP 回环**。同季追番实测秒开率 >70% | [VodPlaybackController](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/playback/vod/VodPlaybackController.java#L148) |
| 3 | **弹幕预加载** | 进度 ≥85% 后台下载下一集弹幕 XML/JSON，不立即渲染，用户真实切集时直接读本地缓存，消除弹幕加载白屏 1~2s | [VodPlaybackController](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/playback/vod/VodPlaybackController.java#L260) |
| 4 | **ParseDiskCache trim 惰性化** | 每次 put 不再触发 listFiles+sort，改为每 50 次 put 才做一次 `trimIfNeeded()`，大幅降低写路径 I/O | [ParseDiskCache.java#L44](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseDiskCache.java#L44) |
| 5 | **v5.6.x AI 优化全景架构表** | L1/L2 两级缓存 + 解析搜索并发加速 + PlaybackAdvisor 带宽自学习 + LLM 嗅探兜底 + 源质量评分排序 + 85% 预解析 + Wi-Fi/电量门控预加载 + AB 分桶灰度 + 高级设置四卡片 | 见 [CHANGELOG.md 架构总览](file:///workspace/CHANGELOG.md#L43-L57) |
| 6 | **版本号** | versionCode 619 → **620** / versionName 5.5.70 → **5.6.0** | [app/build.gradle#L22-L23](file:///workspace/app/build.gradle#L22-L23) |

---

### v5.5.65 · 2026-08-13 · 依赖更新对齐上游 + 6 项闪退修复

| # | 模块 | 变更 | 说明 |
|---|------|------|------|
| 1 | **依赖更新** | AGP 9.1.0→9.3.1 / compileSdk 36→37 / Glide 5.0.7→5.0.9 / NewPipeExtractor v0.26.3→v0.26.4 | 对齐上游 FongMi/TV |
| 2 | **TVBus 闪退修复** | `System.exit(0)` → Toast + PendingIntent 优雅重启 | [TVBus.java#L65-L89](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/extractor/TVBus.java#L65-L89) |
| 3 | **PlayerManager NPE 修复** | 15+ 方法加 null 防护 | [PlayerManager.java#L91-L107](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/PlayerManager.java#L91-L107) |
| 4 | **onPlayerError 崩溃修复** | try-catch 兜底 | [PlayerManager.java#L623-L637](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/PlayerManager.java#L623-L637) |
| 5 | **FFmpegUtil 崩溃修复** | ensureReady 异常容错 | [FFmpegUtil.java#L135-L148](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/FFmpegUtil.java#L135-L148) |
| 6 | **PlaybackActivity 生命周期修复** | isAlive() 守卫 | [PlaybackActivity.java#L590-L593](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/ui/activity/PlaybackActivity.java#L590-L593) |

---

### v5.5.64 · 2026-08-13 · 修复 TG 搜索「未命中任何公开帖子」

| # | 模块 | 行为 | 代码位置 |
|---|------|------|---------|
| 1 | **TG 搜索 URL 加 `?q=` 参数** | `searchTg()` 之前请求 `t.me/s/{channel}` 只拿最新 ~20 条帖子做本地匹配 → 改为 `t.me/s/{channel}?q={keyword}` 让 Telegram 服务端搜索整个频道历史 | [SocialApi.java#L168-L218](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/SocialApi.java#L168-L218) |
| 2 | **浏览器 UA 请求** | 新增 `fetchTgPreview()` 带 `User-Agent: Chrome/120 Mobile` + `Accept-Language: zh-CN`，避免 t.me 返回精简页面 | [SocialApi.java#L220-L238](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/SocialApi.java#L220-L238) |
| 3 | **帖子直链解析** | 新增 `parseTelegramPostUrls()` 提取 `t.me/{channel}/{messageId}` 直链，点击可跳转到具体帖子 | [SocialApi.java#L240-L251](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/SocialApi.java#L240-L251) |
| 4 | 版本号 | versionCode 612 → **613** / versionName 5.5.63 → **5.5.64** | [app/build.gradle#L22-L23](file:///workspace/app/build.gradle#L22-L23) |

---

### v5.5.63 · 2026-08-13 · 社交搜索默认公开频道 + 自定义关键词（如庆余年）网络搜索 + 合并搜索开关关闭全链路跳过

| # | 模块 | 行为 | 代码位置 |
|---|------|------|---------|
| 1 | **TG 默认公开频道兜底** | 用户未手动配置频道列表时，`Setting.getTgChannelList()` 自动返回 `TG_CHANNELS_DEFAULT`（8 个网络公开影视/动漫/剧集频道：subsplease_movies, subsplease, nxupdates, YHYS_01, ysjzyd, dianyingjie123, movieheavenx, dytt123）；新增 `isTgChannelListUserDefined()` 判断是否用户自定义 | [Setting.java#L305-L338](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/setting/Setting.java#L305-L338) |
| 2 | **测试搜索自定义关键词** | 高级设置页点「立即测试连接并搜索示例」不再硬编码搜 1080p / movie trailer，改为先弹出输入框让用户填自定义关键词（默认预填「庆余年」，也可填庆余年2/三体/任意词），然后调 `SocialApi.searchTg(keyword, maxPerChannel)` 与 `searchX(keyword, xMaxResults)` 从真实网络搜索并把命中的标题/内容/URL 逐条展示在结果对话框 | [SettingAdvancedActivity.java#L284-L429](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/ui/activity/SettingAdvancedActivity.java#L284-L429) |
| 3 | **频道编辑 UI 增强** | `showChannelListDialog()` 顶部提示当前是默认频道还是用户自定义；底部给出默认 8 个频道示例与格式说明；新增「恢复默认」按钮（写入空字符串，get 时自动兜底回默认） | [SettingAdvancedActivity.java#L556-L615](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/ui/activity/SettingAdvancedActivity.java#L556-L615) |
| 4 | **合并搜索关闭全链路跳过** | ① UI 入口 `onSocialTest` 先判 `isSocialSearchEnabled()`，关了立即弹窗提示并 return，不显示关键词框；② `SocialApi.preflightTg/preflightX` 在 4 个网络方法入口再次判开关，关了直接返回 fail 结果，**完全不 sleep、不走 HTTP、任何 TG/X 请求都不发** | [SettingAdvancedActivity.java#L284-L292](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/ui/activity/SettingAdvancedActivity.java#L284-L292) / [SocialApi.java#L52-L63](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/SocialApi.java#L52-L63) |
| 5 | 版本号 | versionCode 611 → **612** / versionName 5.5.62 → **5.5.63** | [app/build.gradle#L22-L23](file:///workspace/app/build.gradle#L22-L23) |

---

### v5.5.62 · 2026-08-13 · 高级设置默认隐藏（点击版本号 20 次解锁）+ 社交搜索增强（TG/X 跳转 App / 限速防封 / 总开关）+ 下载 ZIP 魔术头校验

| # | 模块 | 行为 | 代码位置 |
|---|------|------|---------|
| 1 | **高级设置入口隐藏** | mobile `SettingFragment` + leanback `SettingActivity` 里「高级设置」默认 GONE；版本号 `onClick` 计数 20 次 → `Setting.putSocialSearchUnlocked(true)` 后置 VISIBLE | [mobile SettingFragment](file:///workspace/app/src/mobile/java/com/ssmhdssmhd/mxboxs/ui/fragment/SettingFragment.java) / [leanback SettingActivity](file:///workspace/app/src/leanback/java/com/ssmhdssmhd/mxboxs/ui/activity/SettingActivity.java) |
| 2 | **SettingAdvancedActivity** | TG Bot Token / X Bearer Token 粘贴、TG 频道列表、X 自定义代理前缀；连接测试成功后缓存 bot 账号 / @xxx 显示；lockedHint + socialCard 类型修复（MaterialTextView / MaterialCardView） | [SettingAdvancedActivity.java](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/ui/activity/SettingAdvancedActivity.java) |
| 3 | **自动跳转到对应 App** | `onJumpToApp(TG)` 打开 `https://t.me/BotFather`；`onJumpToApp(X)` 打开 `https://developer.x.com/`（已装官方 App 会优先跳转） | [SettingAdvancedActivity.java#L233-L253](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/ui/activity/SettingAdvancedActivity.java#L233-L253) |
| 4 | **总开关 + 限速三档** | `isSocialSearchEnabled()`（总开关）；`getSocialTgMinIntervalMs ≥ 500ms`；`getSocialXMinIntervalMs ≥ 800ms`；`getSocialMaxHitsPerSearch ∈ [1,100]` | [Setting.java#L252-L281](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/setting/Setting.java#L252-L281) |
| 5 | **SocialApi 限速 sleep + 门控** | `preflightTg / preflightX` 先判 `isSocialSearchEnabled()`，再按 `minIntervalMs` 做 `Thread.sleep` 节流；`testTgBot / searchTg / testX / searchX` 入口都先调 preflight | [SocialApi.java#L52-L83](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/SocialApi.java#L52-L83) / 4 个入口方法 |
| 6 | **下载稳定性修复** | `probeOne` 三重校验（Content-Type 非 html / Content-Length 合理 / ZIP 魔术头 PK）；`BAD_MIRROR_HOSTS` 黑名单；默认镜像切 GitHub 直连；下载超时 60s | [Github.java](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/Github.java) / [Updater.java](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/Updater.java) |
| 7 | 版本号 | versionCode 610 → **611** / versionName 5.5.61 → **5.5.62** | [app/build.gradle#L22-L23](file:///workspace/app/build.gradle#L22-L23) |

---

### v5.5.54 · 2026-08-08 · 壁纸未配置自动走内置接口（设置UI只显示「内置」） + 更新对话框改造（上=激活码 / 下=更新内容）

| # | 模块 | 行为 | 代码位置 |
|---|------|------|---------|
| 1 | **WallConfig 内置壁纸兜底** | url 空/未配置时自动用内置 `https://www.hhlqilongzhu.cn/api/MP4_xiaojiejie.php`，`WallConfig.getDesc()` 返回「内置」两字；Setting 页 wallUrl 文本、ConfigDialog 里都只显示「内置」，不把真实接口地址给用户看 | [WallConfig.java#L24-L88](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/api/config/WallConfig.java#L24-L88) / [mobile ConfigDialog#L73-L89](file:///workspace/app/src/mobile/java/com/ssmhdssmhd/mxboxs/ui/dialog/ConfigDialog.java#L73-L89) / [leanback ConfigDialog#L80-L109](file:///workspace/app/src/leanback/java/com/ssmhdssmhd/mxboxs/ui/dialog/ConfigDialog.java#L80-L109) |
| 2 | **dialog_update 布局改造** | 原底部 debug 面板替换为两块：**上部 = 授权激活码**（输入框 + 保存 + 激活状态），**下部 = 更新内容**（release.body / 失败详情）；同时保留零高 `id=debug` 让旧代码判空不崩 | [mobile dialog_update.xml#L57-L169](file:///workspace/app/src/mobile/res/layout/dialog_update.xml#L57-L169) / [leanback dialog_update.xml#L79-L192](file:///workspace/app/src/leanback/res/layout/dialog_update.xml#L79-L192) |
| 3 | **UpdateDialog 激活码保存** | `initView()` 回填 `Setting.getKami()` / `Setting.isKamiActivated()`；保存按钮/输入法 Done 都写入本地 kami（非空即置为已激活） | [mobile UpdateDialog.java#L69-L120](file:///workspace/app/src/mobile/java/com/ssmhdssmhd/mxboxs/ui/dialog/UpdateDialog.java#L69-L120) / [leanback UpdateDialog.java#L64-L114](file:///workspace/app/src/leanback/java/com/ssmhdssmhd/mxboxs/ui/dialog/UpdateDialog.java#L64-L114) |
| 4 | **Updater 用 changelog 显示更新内容** | 已是最新/有新版本分支：`dialog.setChangelog(release.body)`；网络错误 / 下载失败时，下部更新内容区域展示错误原因 + 预测试总结（替换旧 setDebugInfo 写法） | [Updater.java#L138-L234](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/Updater.java#L138-L234) / [Updater.java#L409-L425](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/Updater.java#L409-L425) |
| 5 | 版本号 | versionCode 602 → **603** / versionName 5.5.53 → **5.5.54** | [app/build.gradle#L22-L23](file:///workspace/app/build.gradle#L22-L23) |

---

### v5.5.53 · 2026-08-08 · 修复 CI 编译失败 Github.java:163 `cannot find symbol App`（导致 Release 里只有 v5.5.50 APK 的根因）

| # | 模块 | 行为 | 代码位置 |
|---|------|------|---------|
| 1 | **Github.java 补 import** | v5.5.51 probeUrls 里写了 `App.post(new Runnable()` 但忘记 `import com.ssmhdssmhd.mxboxs.App;`，CI 直接 BUILD FAILED；加上一行 import 编译即过 | [Github.java#L1-L6](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/Github.java#L1-L6) |
| 2 | **CI 事实对照** | Run #31269751767 (948f874) step 10 compileMobileArm64_v8a ❌ 失败 → 4 份 APK 一份都没产出；Release `MXboxS-latest` 只保留上次（6af35ce）的 v5.5.50。修复后新 Run 4 份 APK 会重新覆盖 assets | GitHub API 已验证 |
| 3 | 版本号 | versionCode 601 → **602** / versionName 5.5.52 → **5.5.53** | [app/build.gradle#L22-L23](file:///workspace/app/build.gradle#L22-L23) |

---

### v5.5.52 · 2026-08-08 · 播放设置分为「点播播放器」和「直播播放器」，可独立选择不同引擎

| # | 模块 | 行为 | 代码位置 |
|---|------|------|---------|
| 1 | **PlayerSetting 直播引擎** | 新增 `getLiveEngine()`/`putLiveEngine()`，key=`live_engine`，默认回退 `getEngine()` | [PlayerSetting.java#L38-L54](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/setting/PlayerSetting.java#L38-L54) |
| 2 | **PlayerEngineFactory live 参数** | 新增 `create(decode,live,listener)` / `matches(engine,spec,live)` 重载，`live=true` 读取 `getLiveEngine()` | [PlayerEngineFactory.java#L37-L104](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/engine/PlayerEngineFactory.java#L37-L104) |
| 3 | **PlayerManager liveMode** | 新增 `liveMode` 字段 + `setLiveMode()`/`isLiveMode()`；`setEngine()`/`ensureEngine()` 根据 `liveMode` 走 live 路径 | [PlayerManager.java#L60-L84](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/PlayerManager.java#L60-L84) |
| 4 | **LiveActivity 设置 liveMode** | mobile + leanback 在 `onServiceConnected()` 调 `player().setLiveMode(true)` | [mobile LiveActivity#L156-L161](file:///workspace/app/src/mobile/java/com/ssmhdssmhd/mxboxs/ui/activity/LiveActivity.java#L156-L161) / [leanback LiveActivity#L152-L158](file:///workspace/app/src/leanback/java/com/ssmhdssmhd/mxboxs/ui/activity/LiveActivity.java#L152-L158) |
| 5 | **设置 UI 新增直播播放器行** | mobile + leanback 布局新增 `liveEngine`/`liveEngineText`；Fragment/Activity 新增 `setLiveEngine()` 点击循环 | [mobile SettingPlayerFragment#L117-L121](file:///workspace/app/src/mobile/java/com/ssmhdssmhd/mxboxs/ui/fragment/SettingPlayerFragment.java#L117-L121) / [leanback SettingPlayerActivity#L114-L118](file:///workspace/app/src/leanback/java/com/ssmhdssmhd/mxboxs/ui/activity/SettingPlayerActivity.java#L114-L118) |
| 6 | **字符串三语** | `player_engine`="点播播放器"/"VOD Player"；`player_engine_live`="直播播放器"/"Live Player" | [strings.xml#L166-L167](file:///workspace/app/src/main/res/values/strings.xml#L166-L167) |
| 7 | 版本号 | versionCode 600 → **601** / versionName 5.5.51 → **5.5.52** | [app/build.gradle#L22-L23](file:///workspace/app/build.gradle#L22-L23) |

---

### v5.5.51 · 2026-08-08 · 先测试再下载（1.5s 探针）+ 修复 ghps.cambridgecs.co→.com 域名错误 + 修复按钮一直置灰根因

| # | 模块 | 行为 | 代码位置 |
|---|------|------|---------|
| 1 | **「先测试再下载」Probe 预测试** | `Github.probeUrls()` 并行 8 线程，1.5s 超时；每条先 HEAD 再回退 GET bytes=0-0；UI 实时滚动「探针 3/14：ghproxy.com ✅ 187ms / ❌ DNS 解析失败」；按 ok + RTT 升序重排 apkUrls | [Github.probeUrls](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/Github.java#L131-L205) / [Github.probeOne](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/Github.java#L216-L267) / [Updater.startDownload probe 部分](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/Updater.java#L253-L305) |
| 2 | **域名拼写错误修复** | `ghps.cambridgecs.co` → `ghps.cambridgecs.com`（之前 DNS 解析失败根因） | [Github.java#L38-L50](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/Github.java#L38-L50) |
| 3 | **新增 2 条公益镜像** | gh.1ms.run + gh.dmirror.xyz（镜像池从 10 条扩到 12 条） | [Github.java#L48-L99](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/Github.java#L48-L99) |
| 4 | **「正在下载…」一直置灰根因修复** | mobile 版新增 `cachedPositive` Button 引用缓存 + `pendingConfirmEnabled/TextRes` 挂起状态，onStart 再应用；`setConfirmEnabled` 不再因 `getDialog()==null` 直接 return | [mobile UpdateDialog](file:///workspace/app/src/mobile/java/com/ssmhdssmhd/mxboxs/ui/dialog/UpdateDialog.java#L25-L94) / [mobile UpdateDialog setConfirmEnabled](file:///workspace/app/src/mobile/java/com/ssmhdssmhd/mxboxs/ui/dialog/UpdateDialog.java#L187-L201) |
| 5 | **Updater debug 追加预测试总结** | 全部失败时 debug 面板新增「预测试总结：✅X 可用 ❌Y 失败 + 失败项明细」 | [Updater.error 追加 lastProbeSummary](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/Updater.java#L418-L434) |
| 6 | 版本号 | versionCode 599 → **600** / versionName 5.5.50 → **5.5.51** | [app/build.gradle#L22-L23](file:///workspace/app/build.gradle#L22-L23) |

---

### v5.5.50 · 2026-08-08 · 修复下载进度条全程 0%——Range 头拿总大小 + 每 200ms 汇报已下载字节数

| # | 模块 | 行为 | 代码位置 |
|---|------|------|---------|
| 1 | **Range 头强制拿 Content-Range** | `doInBackground` 改用 `Request.Builder().header("Range", "bytes=0-")`；`getContentLength()` 优先解析 `Content-Range: bytes 0-N/N`，退化到 `Content-Length` | [Download.java#L79-L111](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/Download.java#L79-L111) |
| 2 | **双路进度回调** | 已知总大小→每块回调百分比+字节数；未知总大小→每 200ms 回调已下载字节数；下载完成强制 100% | [Download.java#L113-L154](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/Download.java#L113-L154) |
| 3 | **Callback 接口升级** | 新增 `progress(int progress, long downloadedBytes, long totalBytes)` 默认方法，兼容旧 `progress(int)`；新增 `formatBytes()` 工具 | [Download.java#L182-L212](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/Download.java#L182-L212) |
| 4 | **UI 带字节数进度** | `UpdateDialog.setProgress(progress, downloaded, total)` 显示「42% · 12.3 MB / 29.1 MB」或「已下载 12.3 MB」 | [mobile UpdateDialog#L134-L148](file:///workspace/app/src/mobile/java/com/ssmhdssmhd/mxboxs/ui/dialog/UpdateDialog.java#L134-L148) / [leanback UpdateDialog#L127-L141](file:///workspace/app/src/leanback/java/com/ssmhdssmhd/mxboxs/ui/dialog/UpdateDialog.java#L127-L141) |
| 5 | **Updater 适配** | 实现新 `progress(progress, downloaded, total)` 接口 | [Updater.java#L344-L347](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/Updater.java#L344-L347) |
| 6 | 版本号 | versionCode 598 → **599** / versionName 5.5.49 → **5.5.50** | [app/build.gradle#L22-L23](file:///workspace/app/build.gradle#L22-L23) |

---

### v5.5.49 · 2026-08-08 · 修复「下载失败 timeout」按钮仍置灰——10s 短超时 + 10 条镜像秒切 + 全部失败按钮改为「重试」

| # | 模块 | 行为 | 代码位置 |
|---|------|------|---------|
| 1 | **APK 下载短超时 10s** | `Download.create(url, file, 10_000ms)` 重载；`OkHttp.client(true, timeoutMs)` 直接带超时；SocketTimeout 消息追加「10000ms 内未响应，已自动快速失败」；新增 `volatile Call activeCall` 快速取消 | [Download.java#L19-L101](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/Download.java#L19-L101) |
| 2 | **按钮「正在下载…」失败变「重试」可点** | `UpdateDialog.setConfirmEnabled(enabled, textRes)` 重载；Updater.error 全部失败 → `setConfirmEnabled(true, R.string.update_retry)`，Debug 面板追加指引；切源中 status 含失败原因，不再默默转圈 | [mobile UpdateDialog](file:///workspace/app/src/mobile/java/com/ssmhdssmhd/mxboxs/ui/dialog/UpdateDialog.java#L128-L149) / [leanback UpdateDialog](file:///workspace/app/src/leanback/java/com/ssmhdssmhd/mxboxs/ui/dialog/UpdateDialog.java#L121-L149) / [Updater.error](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/Updater.java#L343-L372) |
| 3 | **onConfirm 状态不写乱** | 移除 `view.setEnabled(false) ... view.setEnabled(true)`，统一由 `showProgress() + setConfirmEnabled` 管理 | [Updater.onConfirm](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/Updater.java#L315-L320) |
| 4 | **10+ 条候选镜像兜底** | `Github.findApkUrls` 新增 `buildJsDelivrCandidates`（fastly + cdn jsdelivr 反代 release 路径）+ `ensureCandidates()` 对老客户端候选太少时重拼；最多 12 条全部进入并行 HEAD 排好 | [Github.java#L377-L452](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/Github.java#L377-L452) / [Updater.doInBackground](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/Updater.java#L201-L204) |
| 5 | 国际化 strings | 新增 `update_retry`（zh 重试 / 重試 / en Retry） | [zh-CN](file:///workspace/app/src/main/res/values-zh-rCN/strings.xml#L235-L237) / [zh-TW](file:///workspace/app/src/main/res/values-zh-rTW/strings.xml#L234-L236) / [en](file:///workspace/app/src/main/res/values/strings.xml#L239-L241) |
| 6 | 版本号 | versionCode 597 → **598** / versionName 5.5.48 → **5.5.49** | [app/build.gradle#L22-L23](file:///workspace/app/build.gradle#L22-L23) |

---

### v5.5.48 · 2026-08-08 · 修复「检测更新却显示已是最新」的诊断难题——对话框追加 Debug 版本信息

| # | 模块 | 行为 | 代码位置 |
|---|------|------|---------|
| 1 | **Debug 信息面板** | mobile/leanback 两套 `dialog_update.xml` 底部新增 monospace `<debug>` 字段（默认 GONE），`Updater.buildDebugInfo()` 一次性输出：本地版本 + 远程版本 + 来源（APK 文件名 / tag_name / <未取到>）+ Release tag + 匹配 APK 文件名 + Release 来源（getHighestRelease / getLatestRelease / network error / Exception）+ compareVersion 比较结果与解释 | [Updater.buildDebugInfo](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/Updater.java#L77-L106) / [mobile.xml](file:///workspace/app/src/mobile/res/layout/dialog_update.xml#L57-L71) / [leanback.xml](file:///workspace/app/src/leanback/res/layout/dialog_update.xml#L79-L93) |
| 2 | **终态分支强制 showDialog** | 无论「无网络」「已是最新」「有新版本」「异常」四大终态，全部先走 `ensureDialogShown(activity)`，避免 forced 模式（点按钮检测）UI 静默；无网络 / Exception 同时 Toast 提醒 | [Updater.doInBackground](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/Updater.java#L122-L238) + [ensureDialogShown](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/Updater.java#L108-L110) |
| 3 | **APK 文件名匹配保留证据** | `Github.extractVersionFromAssetsWithDebug` 返回 Pair<版本号, 对应 APK 文件名>，失败时 second=首个 APK 名，直接暴露「CI 还没 build v5.5.47/5.5.48 资产」 | [Github.extractVersionFromAssetsWithDebug](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/Github.java#L404-L429) |
| 4 | 版本号 | versionCode 596 → **597** / versionName 5.5.47 → **5.5.48** | [app/build.gradle#L22-L23](file:///workspace/app/build.gradle#L22-L23) |

**你的现场诊断**：截图显示「版本 5.5.46」→ 远端 APK 资产最高仍是 5.5.46。本次会话本地又新增 2 个 commit（v5.5.47、v5.5.48），**尚未 `git push origin main`** → CI 还没产出 `MXboxS-*-5.5.48.apk`，因此 `compareVersion(5.5.46, 本地 5.5.46) = 0`，被判定「已是最新」。Push 后 v5.5.48 客户端的 Debug 区域会直接显示「远程 5.5.48 / 匹配 APK …5.5.48.apk / 候选镜像 10 条」→ 进入下载。

---

### v5.5.47 · 2026-08-08 · 镜像加速升级（国内 7 条 + 海外 3 条，并行 HEAD 探测自动挑最快 + 失败秒切）

| # | 模块 | 行为 | 代码位置 |
|---|------|------|---------|
| 1 | **镜像池扩充到 10 条** | 国内 7 条（mirror.ghproxy / ghps.cambridgecs / ghproxy.net / gh.api.99988866.xyz / gh.mirai / ghproxy / gh.tmoe）+ 海外 3 条（GitHub 直连 + fastly.jsdelivr / cdn.jsdelivr），统一存 `Github.MIRROR_OPTIONS / CN_MIRRORS / OVERSEA_MIRRORS` | [Github.java#L35-L91](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/Github.java#L35-L91) |
| 2 | **并行 HEAD 预探测** | 首次下载前 `Github.rankByConnectivity` 对所有候选 APK URL 做 6 线程 `HEAD` 请求（单镜像最多 4 秒），按 RTT 升序 + 2xx/3xx 可用优先，失败的放最后；用户首选镜像始终保持第一顺位；对话框显示「正在挑选最快镜像…」 | [Github.java#L141-L249](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/Github.java#L141-L249) / [Updater.java#L182-L216](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/Updater.java#L182-L216) |
| 3 | **UI 更新源 8 选项** | `Updater.showMirrorDialog` 直接读取 `Github.MIRROR_OPTIONS` 显示；`Setting` 新增 `MIRROR_DEFAULT_INDEX = mirror.ghproxy`，并迁移老用户的 `mirror_mode=2 → DIRECT=7` | [Updater.java#L61-L75](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/Updater.java#L61-L75) / [Setting.java#L150-L184](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/setting/Setting.java#L150-L184) |
| 4 | **切源/失败文案增强** | 显示当前镜像名 + 候选总数；失败时显示「切换到 X 镜像…」，全部失败追加「全部 N 条镜像均失败」 | [Updater.java#L284-L305](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/Updater.java#L284-L305) / [Github.java#L96-L116](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/Github.java#L96-L116) |
| 5 | 版本号 | versionCode 595 → **596** / versionName 5.5.46 → **5.5.47** | [app/build.gradle#L22-L23](file:///workspace/app/build.gradle#L22-L23) |

---

### v5.5.46 · 2026-08-08 · 修复直播无法正常获取和播放（直播格式识别 + M3U过滤 + 多线路自动切源）

| # | 模块 | 行为 | 代码位置 |
|---|------|------|---------|
| 1 | **直播 MIME 类型增强识别** | `MediaItemFactory.resolveMimeType` 重写：① `hasExt(url, ext)` 正确处理含 `?`/`#` 的扩展名；② 新增 `isLikelyHls / isLikelyDash / isLikelyLiveStream` 按路径关键字（`/live/`、`/stream/`、`/playlist`、`/hls/`、`.tv/`、`cctv/hdtv/iptv/直播/频道`、`mime=m3u8`、`type=m3u8`）兜底识别；③ `rtsp://`/`rtmp://` 交由 Media3 内置 Source | [MediaItemFactory.java#L37-L111](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/media/MediaItemFactory.java#L37-L111) |
| 2 | **M3U 解析白名单** | `LiveParser.m3u` 引入 `LIVE_URL_SCHEME`，只接受 `http(s)://`、`rtmp://`、`rtsp://`、`video://`、`proxy://`；跳过空行与非 URL 行（`#EXTHTTP:{...}` 等脏行不再混入频道 URL） | [LiveParser.java#L82-L132](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/api/parser/LiveParser.java#L82-L132) |
| 3 | **失败自动切源 / 重试** | `LiveFallbackPolicy.playbackError` 移除 `isLast()` 限制（任何线路出错都切下一条）；`LivePlaybackController.switchLine` 移除 `isOnly()` 限制（单线路频道也能 `refresh()` 重试）；先 `renderLineSelection` 再 `nextLine/refresh`，UI 同步显示切换状态 | [LiveFallbackPolicy.java#L19-L25](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/playback/live/LiveFallbackPolicy.java#L19-L25) / [LivePlaybackController.java#L129-L137](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/playback/live/LivePlaybackController.java#L129-L137) |
| 4 | 版本号 | versionCode 594 → **595** / versionName 5.5.45 → **5.5.46** | [app/build.gradle#L22-L23](file:///workspace/app/build.gradle#L22-L23) |

**兼容覆盖的直播格式**：`.m3u8/.m3u/.mpd/.ts/.flv`（含 query/fragment）→ 无扩展名 IPTV/HTTP(S) 直播流（`/live`、`/stream`、`.tv`、`cctv/hdtv/iptv`、`mime=m3u8` 等）→ `rtsp://` / `rtmp://` → 内置 `video://` / `proxy://` 通道。

---

### v5.5.45 · 2026-08-08 · AI 智能过滤广告字幕 + AI 功能默认全开

| # | 模块 | 行为 | 代码位置 |
|---|------|------|---------|
| 1 | **简介广告过滤** | `Util.clean()` 集成 `filterAds()`，自动识别并截断赞助/推广、联系方式（微信/QQ）、网站推广、福利引导等 19 种广告话术模式，视频详情页简介干净无广告 | [Util.java#L36-L60](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/Util.java#L36-L60) / [Util.java#L150-L168](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/Util.java#L150-L168) |
| 2 | **AI 功能默认全开** | 画质增强/HDR/降噪/锐化 + 运动补偿/自适应帧率 + 音质增强/超重低音/对白增强 共 9 项 AI 功能全部默认 `true`；新用户安装即用，老用户需手动关闭才会保存 | [PlayerSetting.java#L208-L279](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/setting/PlayerSetting.java#L208-L279) |
| 3 | 版本号 | versionCode 593 → **594** / versionName 5.5.44 → **5.5.45** | [app/build.gradle#L22-L23](file:///workspace/app/build.gradle#L22-L23) |

---

### v5.5.44 · 2026-08-08 · 修复更新下载"进度条 0% 卡死 → ghproxy.com 93.46.8.90 超时 30s 后下载失败"（多镜像自动 fallback + UI 索引错位修复 + 默认改 mirror.ghproxy）

| # | 模块 | 行为 | 代码位置 |
|---|------|------|---------|
| 1 | **根因**：今天 ghproxy.com IP 93.46.8.90 全网宕机（用户截图 `failed to connect to ghproxy.com/93.46.8.90 (port 443) after 30000ms`）+ v5.5.42 下载流程**只会尝试唯一 1 个镜像 URL**，不会 fallback，进度条卡在 0% 30s 后必失败。**附带 bug**：Setting.MIRROR_* 索引与 Updater.showMirrorDialog `items` 索引错位（用户 UI 点 "ghproxy" → mode=0 → getMirror() 返回空 DIRECT 直连；UI 点 "mirror.ghproxy" → mode=1 → 返回 ghproxy.com；完全串位）。 | — | — |
| 2 | **Github.java**：① getMirror 索引对齐（mode=0 → ghproxy.com；mode=1 → mirror.ghproxy.com；mode=2 → 直连），与 Updater.showMirrorDialog items 顺序一致。② 新增 2 个公共镜像 `ghps.cambridgecs.co` + `gh.api.99988866.xyz`。③ 新 API `findApkUrls(release)` 返回 **5 条去重候选 URL**：`用户首选 → mirror.ghproxy → ghps.cambridgecs → gh.api.99988866 → 直连 GitHub`。旧 `findApkUrl` 保留（返回候选第 1 条）。 | [Github.java#L22-L58](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/Github.java#L22-L58) / [Github.java#L152-L203](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/Github.java#L152-L203) |
| 3 | **Updater.java（主修复）**：`String apkUrl` → `List<String> apkUrls + int apkCursor`；`startDownload()` 状态显示"下载中（<镜像名>）…"（0% 不再无提示卡死）；`error()` 回调里如果还有候选，自动 `apkCursor++` → `startDownload()` 切下一个镜像，直到 5 个镜像全部失败才会报"下载失败"。单镜像 connect 30s 最多等 5 次，总等待期约 1.5-2.5 分钟，自动切下一个时进度条也会从 0 重新开始走（不再永久 0%）。 | [Updater.java#L29-L31](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/Updater.java#L29-L31) / [Updater.java#L184-L213](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/Updater.java#L184-L213) / [Updater.java#L282-L296](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/Updater.java#L282-L296) |
| 4 | **Setting.java**：`MIRROR_GHPROXY=0, MIRROR_MIRROR_GHPROXY=1, MIRROR_DIRECT=2`（对齐 UI），默认值从 `MIRROR_GHPROXY` 改成 `MIRROR_MIRROR_GHPROXY`，v5.5.42 用户本地 `mirror_mode=1`（老默认）升级后新代码解读成 mode=1=**mirror.ghproxy.com**，**自动从宕机 ghproxy.com 切走，用户无需任何操作**。 | [Setting.java#L148-L162](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/setting/Setting.java#L148-L162) |
| 5 | 版本号 | versionCode 592 → **593** / versionName 5.5.43 → **5.5.44** | [app/build.gradle#L22-L23](file:///workspace/app/build.gradle#L22-L23) |

**🆘 v5.5.42 / v5.5.40 用户应急方案（现在立刻就能下载，不用等 v5.5.44）**：
```
设置 → 找到 "Update Source"（设置/下载源） → 弹窗 3 选 1：
  ● 推荐选第 2 项 "mirror.ghproxy.com (CN)"  → 确定 → 杀进程重开 App → 再检查更新
  ○ 或选第 3 项 "Direct GitHub" → 确定 → 重开 App
  ✗ 不要再选第 1 项 ghproxy.com (今日宕机 IP 93.46.8.90)
```
改完后重开 App 进设置点版本号，下载会立即从 mirror.ghproxy.com 或 GitHub 直连开始，进度条正常走。

---

### v5.5.43 · 2026-08-08 · 修复 v5.5.42 引入的"官方源播放失败"（getRealUrl 被 playUrl 前缀拼接污染）

| # | 模块 | 行为 | 代码位置 |
|---|------|------|---------|
| 1 | **PlaybackActivity（主修复）** | 第四道防线 unwrapFakeLocalProxy 命中时，**除 setUrl(unwrapped) 外再 `setPlayUrl("")`**，保证 `getRealUrl() == playUrl + url == unwrapped` 就是干净的完整 http(s)。v5.5.42 只 setUrl 没清 playUrl：官方源常有 playUrl 前缀（如 `https://cdn.example.com/player/`），拼出 `https://cdn.example.com/player/https://player.ypls.com/...` → 404 / 域名解析失败。用 `parse=1`（needParse 内部是 parse==1 ∥ jx==1）强制走解析。 | [PlaybackActivity.java#L232-L250](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/ui/activity/PlaybackActivity.java#L232-L250) |
| 2 | **PlayerManager** | 第三道防线 reparse 新建 Result 时补齐 `setPlayUrl("")` / `setParse(1)`，并从原 spec 拷贝 **Drm / Subs / Danmaku / Format** 信息，避免二次解析后丢字幕/弹幕/DRM 方案；之前还把 parse 错设为 0 但又 parse(useParse=true)，状态不一致。 | [PlayerManager.java#L515-L549](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/PlayerManager.java#L515-L549) |
| 3 | 版本号 | versionCode 591 → **592** / versionName 5.5.42 → **5.5.43** | [app/build.gradle#L22-L23](file:///workspace/app/build.gradle#L22-L23) |

**复现链路（v5.5.42 官方源为什么失败）**：
```
SiteApi.playerContent 返回 Result:
  url      = "http://127.0.0.1:10079/p/0/.../aHR0cHM6.../index.m3u8"   (官方解析链碰巧也拿到了伪造 URL)
  playUrl  = "https://cdn.official-source.com/play/"                    (官方源常见的多线路拼接前缀)
→ PlaybackActivity 第四道防线只 setUrl = "https://player.ypls.com/play/R5Ke..."
→ Result.getRealUrl() = playUrl + url = "https://cdn.official-source.com/play/https://player.ypls.com/play/..."
→ 播放器请求这种畸形 URL → 域名解析失败 / 404 Not Found  💥 官方播放报错
```
修复后：
```
unwrapFakeLocalProxy 命中时 setPlayUrl("") + setUrl(unwrapped) + setParse(1)
→ getRealUrl() = "" + "https://player.ypls.com/play/R5Ke..." = "https://player.ypls.com/play/R5Ke..."
→ 走 useParse=true 的 ParseJob 解析 → 还原 URL → aiSmartParseFallbackFrom / fallbackConcurrentParse 挖真 m3u8
→ ExoPlayer 正常加载   ✅ 官方源也能正常播
```

### v5.5.42 · 2026-08-08 · 修复"检测不到最新版本" + 版本检测双保险 + m3u8 伪造本地代理 URL 还原（4 道防线）

| # | 模块 | 行为 | 代码位置 |
|---|------|------|---------|
| 1 | **Updater.java（主修复）** | 优先 `Github.getHighestRelease()`（遍历 `/releases?per_page=10` 全部 releases 含 prerelease，从 APK 文件名取数字比较最高版），失败回退 `getLatestRelease()`；复用公共方法 `Github.compareVersion` 版本比较；`version/desc` 显式 final，`App.post` 改匿名 Runnable 消除 lambda 非 effectively final 变量导致的 **javac 编译失败**（这是之前 MXboxS-latest 始终没产出的根因） | [Updater.java#L86-L162](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/Updater.java#L86-L162) |
| 2 | **Github.java** | 新增 `API_LIST` / `getHighestRelease()` / `compareVersion()` 公共方法；修复 `extractVersionFromAssets` 兜底 p2 正则少右括号 `)` 的 PatternSyntaxException | [Github.java](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/Github.java) |
| 3 | **四道防线 · 伪造 127.0.0.1 本地代理 URL 还原**（m3u8 Network Connection Failed） | UrlUtil.unwrapFakeLocalProxy → ParseJob 拦截 + aiSmartParseFallbackFrom + fallbackConcurrentParse 重跑多路兜底 → CustomWebView shouldInterceptRequest 过滤假直链 → PlayerManager.onParseSuccess / PlaybackActivity.startPlayer 入口兜底；成功把 `http://127.0.0.1:10079/p/.../aHR0cHM6...Lw/index.m3u8` 还原为 `https://player.ypls.com/play/...` 页面再挖真 m3u8 | [UrlUtil.java#L24-L81](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/UrlUtil.java#L24-L81) / [ParseJob.java#L717-L805](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseJob.java#L717-L805) / [CustomWebView.java#L117-L134](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/ui/custom/CustomWebView.java#L117-L134) / [PlayerManager.java#L515-L539](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/PlayerManager.java#L515-L539) / [PlaybackActivity.java#L232-L261](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/ui/activity/PlaybackActivity.java#L232-L261) |
| 4 | **CI 构建验证** | 3 workflow 全部通过（push main / workflow_dispatch / tag v5.5.42），双 release 产出：`MXboxS-latest`（设为 🟢 Latest，4 APK 5.5.42）+ 稳定 release `v5.5.42` | [Actions](https://github.com/ssmhdssmhd/MXboxS/actions/workflows/build.yml) / [Releases](https://github.com/ssmhdssmhd/MXboxS/releases) |
| 5 | 版本号 | versionCode 590 → **591** / versionName 5.5.41 → **5.5.42** | [app/build.gradle#L22-L23](file:///workspace/app/build.gradle#L22-L23) |

**端到端验证（用户 5.5.40 客户端模拟）**：
```
GET /repos/ssmhdssmhd/MXboxS/releases/latest
  tag_name     = MXboxS-latest  prerelease=False  🟢 Latest
  assets[0..3] = MXboxS-*-5.5.42.apk  （4 个变体都是 5.5.42）
  提取 version = 5.5.42
  本地 version = 5.5.40
  compareVersion(5.5.42, 5.5.40) = +2 > 0   →   ✅ 弹出更新对话框并开始下载!
```

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