# MXboxS

基于 [FongMi/TV](https://github.com/FongMi/TV) 的二次开发项目，覆盖 **Android TV（leanback）** 与 **手机端（mobile）** 的影视应用。

[![Build MXboxS Release](https://github.com/ssmhdssmhd/MXboxS/actions/workflows/build.yml/badge.svg)](https://github.com/ssmhdssmhd/MXboxS/actions/workflows/build.yml)
[![Sync Upstream](https://github.com/ssmhdssmhd/MXboxS/actions/workflows/sync.yml/badge.svg)](https://github.com/ssmhdssmhd/MXboxS/actions/workflows/sync.yml)

---

## 最新更新

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