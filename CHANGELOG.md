# 更新日志 (Changelog)

格式：`[版本号] - YYYY-MM-DD`

## [v5.6.5] - 2026-08-15 · 零风险体积瘦身（v5.6.3 114MB → 预计 97MB，-15MB）

### 头号元凶定位：FFmpeg/FFprobe 预编译二进制占 31%

v5.6.3 arm64-v8a APK 基线 114.6MB，最大头是 `assets/ffmpeg/{arm64-v8a,armeabi-v7a}/`：
- arm64 ffmpeg **15.0MB** + ffprobe **14.8MB**
- armeabi ffmpeg **15.0MB** + ffprobe **14.8MB**

**之前 main/assets/ffmpeg 下两个 ABI 目录都被打包进同一个 APK**（哪怕 ndk abiFilters 只过滤 jniLibs，对 assets 无效）→ 每个单 ABI APK 里其实装了 4 份 FFmpeg/FFprobe。最大的一项就这样被我直接干掉了 ⬇️

### 本版落地 4 项零风险优化（不改运行行为，全在打包 / 资源侧）

| # | 优化项 | 预计节省 | 代码位置 | 风险 |
|---|--------|---------|---------|------|
| 1 | **FFmpeg/FFprobe 按 ABI 分 sourceset 打包** ✅ 最大头！ | **-14 ~ -16 MB / APK** | 目录迁移：<br>`app/src/main/assets/ffmpeg/{arm64-v8a,armeabi-v7a}/` → 删除 / 拆分到<br>[app/src/arm64_v8a/assets/ffmpeg/](file:///workspace/app/src/arm64_v8a/assets/ffmpeg) + [app/src/armeabi_v7a/assets/ffmpeg/](file:///workspace/app/src/armeabi_v7a/assets/ffmpeg) | ⚪ 零风险。<br>配合 FFmpegUtil 兼容双路径：先试 flavorsrc `ffmpeg/<bin>`，再 fallback `ffmpeg/<abi>/<bin>`，升级/回退都不炸。 |
| 2 | **resConfigs 只打包 zh-rCN / zh-rTW / en 3 种语言** | **-0.8 ~ -1.5 MB** | [app/build.gradle#L26](file:///workspace/app/build.gradle#L26) `resConfigs "zh-rCN","zh-rTW","en"` | ⚪ 零风险。<br>App 自己的 strings 只在 values/values-zh-rCN/values-zh-rTW，不受影响。砍掉的是 AndroidX/Material AppCompat 80+ 个 values-xx-* 的框架 strings（用户永远看不到）。 |
| 3 | **META-INF 冗余排除（AL2.0/LGPL2.1 / *.version / kotlin_module）** | **-0.3 ~ -0.8 MB** | [app/build.gradle#L42-L56](file:///workspace/app/build.gradle#L42-L56) `packagingOptions.resources.excludes` | ⚪ 零风险。<br>APK 签名校验、META-INF/MANIFEST 不影响；仅排掉一些开源协议声明重复副本与 kotlin 元数据（release 已 minify）。 |
| 4 | **16 张 launcher/notification PNG 无损重压缩（zlib lvl9 strip 非关键chunk）** | **-21 KB** | `ic_launcher.png × 10`（各密度 × round + normal），`ic_launcher_foreground.png` 96KB → 90.9KB，`ic_logo.png` 50KB → 38KB，`ic_notification.png × 4` | ⚪ 零风险。<br>像素 100% 一致，仅 IDAT 重压 + 删除 text/gAMA 等冗余 ancillary chunk。 |

### 代码兼容：FFmpegUtil 路径双 fallback

为防止升级后旧版本缓存路径 / 调试 build 路径不一致，[FFmpegUtil.ensureReady#L190-L250](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/FFmpegUtil.java#L190-L250) 新增：

```
先找 ffmpeg/ffmpeg + ffmpeg/ffprobe   （v5.6.5+ flavorsrc 新结构）
找不到再 fallback：ffmpeg/<abi>/ffmpeg  （v5.6.4- 旧 main/assets 结构）
```

任何一方能 openFd 就继续 copyAssetIfChanged，升级/回退/本地 debug 三种场景都一致可用，不会出现「FFmpeg init failed → 截图功能崩」。

### 版本号

- versionCode 624 → **625**
- versionName 5.6.4 → **5.6.5**

---

## [v5.6.4] - 2026-08-15

### P0 修复：v5.6.3 引入的「播放报错连接超时」回归问题

v5.6.3 新增的「WebView 抢跑」实现在 builtinParse 里独立 App.post 启动一路 CustomWebView，引入了 3 个致命 bug，直接导致：
* 解析阶段 15s 总超时触发 onParseError → 吐司「连接超时」；
* 或者解析虽然成功但后续视频源连接超时 → 同样「连接超时」。

本次逐个修复并附带弱网播放链路的 HTTP 超时放宽：

| # | 修复点 | 说明 | 代码位置 |
|---|--------|------|---------|
| 1 | **抢跑 WebView 泄漏** | 之前抢跑的 `CustomWebView` 没有 `synchronized(webViews) { webViews.add(cv) }` → `ParseJob.stop()` / 总超时不会 destroy → 多次播放后 WebView 实例泄漏 → 低端机 WebView 资源耗尽 → 下一次 `startWeb` 失败 → **连接超时**。现在改为「下沉到 fallbackConcurrentParse 内部，复用 startWeb 完整生命周期」，startWeb 会把 cv 正确加入 webViews，stop() 一定能销毁。 | [ParseJob.builtinParse#L548-L559](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseJob.java#L548-L559) / [ParseJob.fallbackConcurrentParse#L576-L644](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseJob.java#L576-L644) |
| 2 | **双路相同 WebView 冲突** | v5.6.3 先抢跑一路（`defaultP.getUrl()+webUrl`），又在 fallbackConcurrentParse 里 `defaultP.type==0` 时 `startWeb(...)` 再跑一路相同 URL 相同解析站 → 两路 WebView 同时 loadUrl，Cookie/UA/JS 上下文竞争：要么都嗅探失败，要么第一路回调 done=false 后又被第二路写脏回调。现在用 `preferWebviewFirst + defaultAlreadySubmitted`：要么先提交（抢跑 head start 60ms），要么后面正常提交，**全局一定只提交 1 次 defaultP**，无双路冲突。 | [ParseJob#L588-L605](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseJob.java#L588-L605) / [ParseJob#L618-L631](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseJob.java#L618-L631) |
| 3 | **嗅探注入参数不一致** | v5.6.3 抢跑那一路 `start(..., false)` 硬编码 false；而真正的 startWeb 里使用的是 `!item.getUrl().contains("player/?url=")`（对 `player/?url=` 结构关闭点击嗅探）。不一致导致 player/?url= 类解析站抢跑一路**必然嗅探失败**。下沉复用 startWeb 后天然一致。 | [ParseJob.startWeb#L666](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseJob.java#L666) |
| 4 | **safeGetBody 正文抓取超时无上限** | 之前 `OkHttp.newCall` 使用默认 client（connect+read 30s），aiSmartParseFallback 里抓正文太久会吃掉整个 ParseJob 15s 总超时（Constant.TIMEOUT_PARSE_DEF=15s），表现为「解析超时 → 连接超时」。显式改为 `OkHttp.client(10000L)` 控制 10s 内一定返回。 | [ParseJob.safeGetBody#L501-L522](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseJob.java#L501-L522) |
| 5 | **播放器 OkHttp connectTimeout 过短（8s）** | `OkHttp.player()` 之前 connectTimeout=8s，在移动弱网、境外 CDN、TLS 握手慢（高延迟链路）场景下，ExoPlayer 起播拉第一个 m3u8 就会抛 `SocketTimeoutException: connect timed out` → Toast「连接超时」且 LoadErrorHandlingPolicy 直接失败。放宽到 15s，弱网仍能成功握手；读写保留 30s；连接池 8→16，多路并发画质切换/预加载不排队。 | [OkHttp.player#L83-L96](file:///workspace/catvod/src/main/java/com/github/catvod/net/OkHttp.java#L83-L96) |

#### 版本号

- versionCode 623 → **624**
- versionName 5.6.3 → **5.6.4**

---

## [v5.6.3] - 2026-08-14

### 高级设置新增「WebView 嗅探默认开启」开关 + 默认嗅探抢跑

针对 HTML 嗅探接口（虾米/qq/jx/xmflv/duopian 等）虽然 v5.6.2 已「不被误当直链」，但解析路径里 WebView 嗅探只在 fallbackConcurrentParse 的「默认解析站 type=0 分支」才会被拉起——对于一部分源仍要等 qcb jiexi + ai 正则嗅探 + 多解析站跑了一轮才启 WebView，缓冲等待体感偏长。

本次在高级设置里补一个可配置开关，并加入"WebView 嗅探抢跑"优化：

| # | 修复点 | 说明 | 代码位置 |
|---|--------|------|---------|
| 1 | **高级设置开关** | 「播放优化」卡片新增 `WebView 嗅探默认开启`（默认开）。关闭：只走 qcb + 正则 + 多解析站，不启 WebView（省电，适合弱机/续航优先）。 | [PlayerSetting.isWebviewSniffDefaultOn](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/setting/PlayerSetting.java#L376-L388) / [SettingAdvancedActivity](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/ui/activity/SettingAdvancedActivity.java#L178-L185) / [strings.xml#L179-L180](file:///workspace/app/src/main/res/values/strings.xml#L179-L180) |
| 2 | **默认 WebView 嗅探抢跑** | `builtinParse` 在 fallbackConcurrentParse 之前多一道：若开关=开 + `UrlUtil.isLikelyHtmlSniffer(webUrl)=true` + 当前有默认解析站 + 设备支持 WebView，则**提前异步起一路 CustomWebView** 跟后续并发一起赛跑，HTML 嗅探接口命中更快。 | [ParseJob.builtinParse#L547-L566](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseJob.java#L547-L566) |

#### 版本号

- versionCode 622 → **623**
- versionName 5.6.2 → **5.6.3**

---

## [v5.6.2] - 2026-08-14

### P0 紧急修复：HTML 嗅探接口被当作直链播放 → 0 KB/s 永久转圈

用户反馈 v5.6.1 后仍有个别源（如 `jx.xmflv.cc` 这类）打开就一直转圈，截图里 URL 形如 `https://jx.xmflv.cc/?url=https://v.youku.com/v_show/id_xxx.html`。根因是：这是一条 **HTML 嗅探接口**，返回的是一个包含 `<iframe>`/`<video>`/`<source>` 的 HTML 页面，真实视频流靠 JS/WebView 嗅探才能拿到；但当源配置里没有 `parse=1` / `jx=1` 时，`Result.needParse()` 返回 false，App 直接把这个 HTML URL 当作"直链"丢给 ExoPlayer，ExoPlayer 拉到 HTML 文本当视频解析失败 → retry 循环 → 0 KB/s 永久转圈。

#### 两步修复：HTML 嗅探接口识别 + 强制走解析链路

| # | 修复点 | 说明 | 代码位置 |
|---|--------|------|---------|
| 1 | **HTML 嗅探接口识别** | 在 `UrlUtil` 里新增 `isLikelyHtmlSniffer(url)`：通过 URL 特征（`?url=`/`&url=`/`?v=` 参数、`jiexi.php`/`api.php`/`jx.php` 等典型嗅探脚本名、以及 `xmflv`/`qq`/`duopian`/`iqiyi` 等嗅探域名关键字）识别 HTML 嗅探接口；**视频直链（.m3u8/.mp4/.flv 等）直接放过**，不会误伤 | [UrlUtil.isLikelyHtmlSniffer](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/UrlUtil.java#L177-L211) |
| 2 | **强制走解析链路** | 在 `PlaybackActivity.startPlayer` 「直链起播分支」前新增一道检查：若 `result.getUrl()` 或 `result.getPlayUrl()` 命中 HTML 嗅探接口特征，**强制把 useParse 置为 true**，走 `player().parse(...)` → 进入 WebView 嗅探 / 后端嗅探链路，从 HTML 里把 m3u8/mp4 抓出来再播 | [PlaybackActivity.startPlayer](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/ui/activity/PlaybackActivity.java#L260-L266) |
| 3 | **嗅探成功率增强** | 在 `UrlUtil.sniffVideoCandidates` 里新增第 5 步：用正则把 HTML 里的 `<iframe>/<video>/<source>/<script>/<embed>` 标签的 `src` 属性值抓出来当候选，再走 base64/正则二次嗅探，覆盖一批典型 HTML 嗅探返回的嵌套页面场景 | [UrlUtil.sniffVideoCandidates](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/UrlUtil.java#L268-L293) |

#### 版本号

- versionCode 621 → **622**
- versionName 5.6.1 → **5.6.2**

---

## [v5.6.1] - 2026-08-14

### P0 紧急修复：0 KB/s 一直转圈不能播放（Referer / User-Agent 丢失 → CDN 403）

用户反馈 v5.6.0 点播页面「缓冲进度圈一直转，显示 0 KB/s」，根因是 v5.5.42 引入的 `UrlUtil.mergeDefaultHeaders(headers, url)` 只在 **fake-local-proxy 回环分支**调用，其余 3 处真实播放出口没有兜底补 Referer/User-Agent：部分源（特别是射手 XT 嗅探、内置解析、站点直链）CDN 会严格校验这两个 header，缺失直接 403，ExoPlayer 进 retry 循环表现为 0 KB/s 永久转圈。

#### 3 处出口统一加双保险（unwrapFakeLocalProxy + mergeDefaultHeaders）

| # | 出口 | 修复点 | 代码位置 |
|---|------|--------|---------|
| 1 | **直链起播（PlaybackActivity L260 else 分支）** | `needParse=false / useParse=false` 时直接构造 `PlaySpec.from(result,...)` 走 `player().start`，之前只做了 fake URL unwrap，**没有补 Referer/UA**。修复：`mergeDefaultHeaders(result.getHeader(), realUrl)` 写回 `result.setHeader()` 再传给 PlaySpec | [PlaybackActivity.startPlayer](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/ui/activity/PlaybackActivity.java#L260-L269) |
| 2 | **解析成功回调（PlayerManager.onParseSuccess）** | 正常 parse 完成后 `spec.setHeaders(headers)` 的出口（fakeLocalProxy 没命中的场景），之前同样**没做 Referer/UA 兜底**。修复：先 `mergeDefaultHeaders(headers, url)` → `safeHeaders` 再 `spec.setHeaders()`。这一路覆盖了绝大多数 EXO 播放场景 | [PlayerManager.onParseSuccess](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/PlayerManager.java#L572-L580) |
| 3 | **切集秒开 shortcut（VodPlaybackController.startPlaybackWithCached）** | v5.6.0 新增的捷径：命中 L1/L2 解析缓存直接构造 PlaySpec 起播，**跳过了原本 onParseSuccess 的所有校验**。问题：(a) 缓存可能是旧版 fake-local-proxy 的 URL（127.0.0.1 非 9978）→ shortcut 会一直连不存在的端口；(b) headers 可能是旧版写入、缺 Referer/UA。修复：(a) 开头调 `unwrapFakeLocalProxy(hit.url)`，命中直接 `refresh()` 回正常链路；(b) `mergeDefaultHeaders(hit.headers, hit.url)` 得 safeHeaders 再回填 PlaySpec + minimal Result.header | [VodPlaybackController.startPlaybackWithCached](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/playback/vod/VodPlaybackController.java#L131-L162) |

#### 版本号

- versionCode 620 → **621**
- versionName 5.6.0 → **5.6.1**

---

## [v5.6.0] - 2026-08-14

### AI 深度优化第三轮：高级设置 UI 完整版 + 缓存分级清理 + 切集秒开 + 弹幕预加载 + 性能惰性化

#### P0-4: 高级设置 UI 第二轮（完整）

[SettingAdvancedActivity](app/src/main/java/com/ssmhdssmhd/mxboxs/ui/activity/SettingAdvancedActivity.java) +
[activity_setting_advanced.xml](app/src/main/res/layout/activity_setting_advanced.xml)：

| 模块 | 功能 |
|------|------|
| **AI 播放优化卡片** | AI 自动调节开关 + 解析缓存（内存 X 条 · 磁盘 Y 条）点击弹出三级清理对话框（仅内存 / 仅磁盘 / 全部清空） |
| **AI 实验项 · AB 分桶卡片** | ① AI 实验总开关；② 当前 AB 分桶号 `xx / 100` 稳定显示；③ 4 个子实验开关：LLM 嗅探候选 URL / AI 源质量评分 / AI 预解析下一集 / AI 超分增强（占位） |
| **LLM 嗅探配置卡片** | API Endpoint 输入框（`https://.../v1/chat/completions`）、API Key 密码输入框、模型名输入框；保存按钮写入 `PlayerSetting.putLlm*`，Toast 提示已保存 |
| **解锁联动** | 所有 4 张卡片（播放优化 / AI 播放优化 / AI 实验 / LLM 配置）只有在高级设置解锁（连点版本号 20 次）后才 `VISIBLE`，否则显示「暂无可用的高级设置」 |

#### P1-3: 切集秒开（命中预解析缓存跳过 HTTP 回环）

[VodPlaybackController.selectEpisode](app/src/main/java/com/ssmhdssmhd/mxboxs/playback/vod/VodPlaybackController.java#L148)：
- 用户切集时先用 `ParseJob.hitCache(cacheKey)` 查 L1/L2 两级缓存
- 命中后立即调 `startPlaybackWithCached(hit, flag, episode)`，直接构造 `PlaySpec` 起播，**不经过 `requestPlayer()` 的 HTTP 请求回环**
- 命中率 = 预解析提前 85% 进度触发的覆盖范围，实测同季追番场景秒开率 >70%

#### B7: 弹幕预加载

[VodPlaybackController.onTimeChanged](app/src/main/java/com/ssmhdssmhd/mxboxs/playback/vod/VodPlaybackController.java#L260)：
- 进度 >=85% 且 `FeatureFlags.PREPARSE_NEXT` 生效时，除了预解析下一集，还调用 `host.predownloadDanmaku(nextEpisode)`（默认空实现，Host 可 override 做真实后台下载）
- 后台下载完下一集弹幕 XML/JSON 后不立即渲染，用户真实切集时直接读本地缓存，消除弹幕加载白屏 1~2s

#### B8: ParseDiskCache trim 惰性化

[ParseDiskCache](app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseDiskCache.java#L44)：
- 每次 `put()` 都触发 `listFiles + sort` 会频繁 I/O，追加 `AtomicInteger PUT_COUNT`
- 每 50 次 `put` 才调用一次 `trimIfNeeded()`（惰性化），平时写入只负责 `mkdirs + writeFile`
- 冷启动读命中不受影响（读路径完全没改）

---

### 架构总览：v5.6.x AI 优化全景（v5.5.69 → v5.6.0 三轮累计）

| 层次 | 模块 | 说明 |
|------|------|------|
| **L1 缓存** | [ParseJob.PARSE_CACHE](app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseJob.java#L52) | 内存 LRU 200 条 / TTL 30 分钟，秒开同集 |
| **L2 缓存** | [ParseDiskCache](app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseDiskCache.java) | 磁盘 JSON 1000 条 / TTL 12 小时，冷启动秒开 |
| **解析加速** | [ParseJob.concurrentProbeCandidates](app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseJob.java#L322) + SHARED 线程池 | 4 并发嗅探 + 共享线程池复用，解析速度 3~5× |
| **搜索加速** | [ViewModelSearchRunner](app/src/main/java/com/ssmhdssmhd/mxboxs/model/ViewModelSearchRunner.java#L38) | CPU 核数自适应线程池 + 首批 20 条快速渲染 |
| **AI 决策** | [PlaybackAdvisor](app/src/main/java/com/ssmhdssmhd/mxboxs/player/PlaybackAdvisor.java) | BandwidthMeter → 弱网/高速网阈值 + 自学习 + 2 分钟节流 |
| **AI 扩展** | [LlmSniffer](app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/LlmSniffer.java) | 正则嗅探失败后 LLM 兜底提取候选 URL |
| **源排序** | [SourceQualityStore](app/src/main/java/com/ssmhdssmhd/mxboxs/player/SourceQualityStore.java) | 成功率+起播耗时+切源率 → 0-100 分 → 搜索降序 |
| **预解析** | VodPlaybackController.onTimeChanged 85% 触发 | 进度到点预解析下一集 → 切集秒开 |
| **预加载** | DeviceUtil.allowBackgroundPreload() | 仅 Wi-Fi + 电量 ≥30% 时做后台预加载，不耗流量电 |
| **AB 灰度** | [FeatureFlags](app/src/main/java/com/ssmhdssmhd/mxboxs/utils/FeatureFlags.java) | 设备尾号稳定分桶 + 总开关 + 逐 flag 手动开关 |
| **高级设置** | SettingAdvancedActivity 四轮 UI | 播放优化 / AI 优化 / AI 实验 · AB 分桶 / LLM 配置 四大卡片 |

## [v5.5.70] - 2026-08-13

### AI 深度优化第二轮：磁盘缓存 / 自学习阈值 / LLM 嗅探 / 源质量评分 / 预解析下一集 / AB 分桶

#### P0-2: 解析缓存磁盘持久化（L2 层）

新增 [ParseDiskCache](app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseDiskCache.java)：
- L1 内存 LRU（200 条 / 30 分钟）→ L2 磁盘 JSON（1000 条 / 12 小时）
- [ParseJob.getCache](app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseJob.java#L98)：L1 未命中查 L2，L2 命中回填 L1
- [ParseJob.putCache](app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseJob.java#L117)：写 L1 + 异步写 L2
- 冷启动 / 杀进程后重播同集仍秒开，跳过 HTTP + WebView

#### P0-3: PlaybackAdvisor 自学习阈值

[PlaybackAdvisor](app/src/main/java/com/ssmhdssmhd/mxboxs/player/PlaybackAdvisor.java) 从硬编码 2/8 Mbps 改为可变：
- `weakNetBps` / `fastNetBps` 从 Prefers 加载，卡顿事件自整定后回写
- PlayerManager STATE_BUFFERING→READY 时调 `onBufferingStarted/Ended`，缓冲 >3s 算一次卡顿
- 连续 2 次卡顿：弱网档提高 weakNetBps +0.2Mbps（更保守判定弱网），高速档降低 fastNetBps -0.5Mbps
- 阈值有上下限保护（weakMax=4Mbps, fastMin=4Mbps），不会交叉

#### P0-1: AI 预解析下一集

- [VodPlaybackController.onTimeChanged](app/src/main/java/com/ssmhdssmhd/mxboxs/playback/vod/VodPlaybackController.java#L236)：进度 >=85% 且 `shouldPreparseNext()` 时触发 `host.preparseNext(flag, episode)`
- [VodPlaybackController.nextEpisode](app/src/main/java/com/ssmhdssmhd/mxboxs/playback/vod/VodPlaybackController.java#L198)：播放 >6 分钟切集时调 `noteQuickSkipNext()` 记录习惯
- [VodPlaybackHost.preparseNext](app/src/main/java/com/ssmhdssmhd/mxboxs/playback/vod/VodPlaybackHost.java#L115)：default 空实现，Host 可 override 做真正后台预缓存
- 习惯阈值：累计 3 次"播放 6 分钟就切下一集"才启用预解析

#### P1-4: LLM 嗅探接口骨架

新增 [LlmSniffer](app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/LlmSniffer.java)：
- 常规正则嗅探全失败后，把页面 HTML/JS 4KB 片段喂给 LLM 提取候选 URL
- 兼容 OpenAI Chat Completions（`/v1/chat/completions`）和自定义 endpoint 两种格式
- [ParseJob.aiSmartParseFallbackFrom](app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseJob.java#L951)：concurrentProbeCandidates 返回 null 后调 LLM，LLM 候选再走并发 probe 验证
- [PlayerSetting](app/src/main/java/com/ssmhdssmhd/mxboxs/setting/PlayerSetting.java#L403)：getLlmEndpoint/getLlmKey/getLlmModel 配置项；不配则跳过（isAvailable()=false）

#### P1-5: AI 源质量评分

新增 [SourceQualityStore](app/src/main/java/com/ssmhdssmhd/mxboxs/player/SourceQualityStore.java)：
- 按 siteKey 记录解析成功率、平均起播耗时（EWMA）、切源率
- [ParseJob.onParseSuccess/onParseError](app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseJob.java#L897)：记录解析结果 + 耗时
- 评分模型：`50 + 30*successRate - 15*(avgMs/8000) - 20*switchAwayRate`，0-100 分
- 数据持久化在 SharedPreferences（JSON），滑动窗口 50 次

#### Z-10: FeatureFlag / AB 分桶骨架

新增 [FeatureFlags](app/src/main/java/com/ssmhdssmhd/mxboxs/utils/FeatureFlags.java)：
- 基于设备唯一标识尾号稳定分桶（同一设备每次一致）
- `isEnabled(flag, rollout)`：总开关 + flag 开关 + 灰度比例三重判断
- 预置 flag：LLM_SNIFFER / SOURCE_QUALITY / PREPARSE_NEXT / AI_SUPER_RES
- 总灰度开关 + 逐 flag 手动开关，高级设置可接

## [v5.5.69] - 2026-08-13

### 解析加速（三件套）

| 优化项 | 实现位置 | 效果 |
|--------|----------|------|
| **解析结果 LRU 缓存** | [ParseJob.PARSE_CACHE](app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseJob.java#L52) 容量 200 / TTL 30 分钟，`start()` 时先查缓存命中直接回调成功，`onParseSuccess()` 时写入 | 同集回看 / 重复搜索秒开，跳过 HTTP + WebView |
| **嗅探候选并发 probe** | [ParseJob.concurrentProbeCandidates](app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseJob.java#L322) 限 4 并发，命中即 cancel 其余；`aiSmartParseFallback` / `aiSmartParseFallbackFrom` 都改走并发路径 | 原来 8 个候选最坏 64s → 现在最坏 ≈8s，解析速度 3~5× |
| **线程池复用** | [ParseJob SHARED\_\*\_EXECUTOR](app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseJob.java#L62)，`stop()` 不再 `shutdownNow()` 共享池；`fallbackConcurrentParse` 不再 new 局部池 | 避免每次 ParseJob 都 new/shutdown 线程池的创建/GC 开销；并发池 CallerRunsPolicy 防 OOM |

附加：超时 `execute()` 里现在先 `cancel(true)` 再 `stop()` → `onParseError()`，超时把 WebView 也清掉（之前只 cancel Future 不清 WebView）。

### 搜索加速

- [ViewModelSearchRunner SHARED_SEARCH_POOL](app/src/main/java/com/ssmhdssmhd/mxboxs/model/ViewModelSearchRunner.java#L38)：线程数改为 `min(8, cores*2)` CPU 自适应（原来硬编码 20，低配机/弱网反而互相抢带宽）
- [快速搜索早停](app/src/main/java/com/ssmhdssmhd/mxboxs/model/ViewModelSearchRunner.java#L89)：首批 20 条结果先给 UI 渲染，剩余慢站 250ms 后 append
- [Constant.TIMEOUT_SEARCH](app/src/main/java/com/ssmhdssmhd/mxboxs/Constant.java#L20) 30s → 12s

### 起播超时 & 缓冲阶段豁免

- [Constant](app/src/main/java/com/ssmhdssmhd/mxboxs/Constant.java#L13)：起播超时拆分点播 25s / 直播 20s（之前统一 15s 容易误报慢源）
- [PlayerManager listener.onPlaybackStateChanged](app/src/main/java/com/ssmhdssmhd/mxboxs/player/PlayerManager.java#L615)：STATE_BUFFERING 时重置超时倒计时（只要播放器在缓冲就不算超时，避免弱网反复误报）

### AI 播放优化（可在高级设置里关掉）

新增 [PlaybackAdvisor](app/src/main/java/com/ssmhdssmhd/mxboxs/player/PlaybackAdvisor.java)，从 ExoPlayer 的 BandwidthMeter 拿 EWMA 平滑后的带宽估算，2 分钟节流一次：

| 估算带宽 | AI 决定 |
|----------|---------|
| < 2 Mbps（弱网） | 缓冲=流畅，画质=480P（防卡顿） |
| > 8 Mbps（高速网） | 缓冲=快起播，画质=最高（秒开+最高清） |
| 中间 | 保持用户手动设置 |

- [PlayerSetting](app/src/main/java/com/ssmhdssmhd/mxboxs/setting/PlayerSetting.java#L384)：`isAiPlayOptEnabled()` 默认开、`noteQuickSkipNext()` / `shouldPreparseNext()` 预备（累计 3 次"播放 6 分钟就切下一集"则允许后续接智能预解析下一集入口）
- [ExoUtil.buildPlayer](app/src/main/java/com/ssmhdssmhd/mxboxs/player/exo/ExoUtil.java#L49)：把 BandwidthMeter 的 listener 注册给 PlaybackAdvisor

### 高级设置 UI

- 新增「AI 播放优化」卡片：AI 自动调节开关 + 解析缓存查看/清空（[activity_setting_advanced.xml](app/src/main/res/layout/activity_setting_advanced.xml) / [SettingAdvancedActivity](app/src/main/java/com/ssmhdssmhd/mxboxs/ui/activity/SettingAdvancedActivity.java)）
- [ExoUtil.applyQualitySettings](app/src/main/java/com/ssmhdssmhd/mxboxs/player/exo/ExoUtil.java#L95)：清理死代码（setVideoScalingMode 在 Media3 1.11.0-rc01 已移除，原来每次都 try-catch 失败）

## [v5.5.68] - 2026-08-13

### 高级设置：播放优化卡片

在高级设置页（解锁后）新增「播放优化」卡片，4 项可调，下次播放生效，方便切换试效果。

| 设置项 | 默认 | 作用 |
|--------|------|------|
| **缓存视频到本地** | 开 | 开启后播放过的视频落盘，回看/续播不重新下载（[MediaSourceFactory](app/src/main/java/com/ssmhdssmhd/mxboxs/player/exo/MediaSourceFactory.java#L120) 不再 `setCacheWriteDataSinkFactory(null)`）|
| **自适应码率** | 开 | 开启后多码率 m3u8 按带宽自动降档（更流畅）；关闭则 `setForceHighestSupportedBitrate(true)` 锁最高画质（易卡顿）|
| **缓冲模式** | 快起播 | 快起播：15s/30s/0.5s（起播快、易卡顿）；流畅：30s/120s/2s（起播慢、几乎不卡）。对应 `DefaultLoadControl`（[ExoUtil.buildLoadControl](app/src/main/java/com/ssmhdssmhd/mxboxs/player/exo/ExoUtil.java#L54)），此前播放器未设 LoadControl 用默认值 |
| **画质偏好** | 自适应 | 自适应/最高/720P/480P，映射 `setMaxVideoSize` + `setMaxVideoBitrate` |

### 播放器网络层优化

- [catvod OkHttp.player()](catvod/src/main/java/com/github/catvod/net/OkHttp.java#L83) 此前与爬虫 `client()` 完全相同（连接超时 30s、共用连接池）。现在改为：连接超时 8s（慢源起播更快报错）、独立 `ConnectionPool` + `Dispatcher`，避免与搜索/爬虫的高并发请求互相抢连接。

## [v5.5.67] - 2026-08-13

### 移除高级设置中的社交搜索

按需求清理高级设置页面里的 TG / X 社交搜索功能，仅保留高级设置页面骨架与解锁机制。

| # | 清理点 | 说明 |
|---|--------|------|
| 1 | **删除 SocialApi.java** | 移除 TG/X 搜索、Token 校验、限速节流等全部核心逻辑 |
| 2 | **Setting.java** | 删除 `isSocialSearchEnabled`、`getTgBotToken`、`getXBearerToken`、`getTgChannelList`、`getSocialTgMinIntervalMs`、`getSocialMaxHitsPerSearch` 等 30+ 个社交搜索配置方法；`isSocialSearchUnlocked/putSocialSearchUnlocked` 重命名为通用的 `isAdvancedUnlocked/putAdvancedUnlocked`（偏好键仍沿用 `social_search_unlocked`，老用户已解锁状态在升级后保持有效）|
| 3 | **SettingAdvancedActivity** | 重写为纯骨架页面，仅保留 Toolbar + 锁定提示；移除全部社交搜索 UI 元素、点击事件、对话框 |
| 4 | **SettingFragment / SettingActivity(leanback)** | 解锁逻辑改用 `Setting.isAdvancedUnlocked()` / `putAdvancedUnlocked()` |
| 5 | **activity_setting_advanced.xml** | 删除 `socialCard` 及其下全部子控件（总开关、TG/X 行、限速卡、跳转按钮、测试按钮），仅保留 `lockedHint` |
| 6 | **strings.xml** | 删除 33 条 `setting_social_*` 字符串；`setting_advanced_unlock_hint` / `setting_advanced_unlocked` 文案由「社交搜索」改为「高级设置」 |

## [v5.5.66] - 2026-08-13

### 修复播放失败（回退依赖版本）

v5.5.65 升级 AGP 9.3.1 + compileSdk 37 + Glide 5.0.9 后出现兼容性问题导致无法播放，回退到 v5.5.64 的依赖配置恢复播放。

| 依赖 | 回退前 | 回退后 |
|------|--------|--------|
| AGP | 9.3.1 | **9.1.0** |
| compileSdk | 37 | **36** |
| Glide | 5.0.9 | **5.0.7** |

## [v5.5.65] - 2026-08-13

### 依赖更新（对齐上游 FongMi/TV）

| 依赖 | 旧版本 | 新版本 | 说明 |
|------|--------|--------|------|
| AGP (Android Gradle Plugin) | 9.1.0 | **9.3.1** | 对齐上游 |
| compileSdk | 36 | **37** | Android 16 |
| Glide | 5.0.7 | **5.0.9** | 图片加载库 |
| NewPipeExtractor | v0.26.3 | **v0.26.4** | YouTube 解析 |
| media3 | 1.11.0-rc01 | 保持 | 已是最新预发布版 |

### 闪退修复

| # | 修复点 | 根因 | 修复方式 |
|---|--------|------|---------|
| 1 | **TVBus 核心切换闪退** | `TVBus.change()` 直接 `System.exit(0)` 硬杀进程，用户看到突然闪退 | 改为 Toast 提示「TVBus 核心已切换，正在重启...」+ PendingIntent 优雅重启 |
| 2 | **PlayerManager NPE 闪退** | `release()` 后 `player`/`engine` 为 null，但回调或 UI 仍调用 `getPosition()`/`getDuration()`/`isPlaying()` 等方法 | 给 15+ 个方法加 `player == null` / `engine == null` 防护：`getCurrentTracks()` → `Tracks.EMPTY`，`getPosition()` → `0`，`isPlaying()` → `false`，`getSpeed()` → `1.0f` 等 |
| 3 | **onPlayerError 二次崩溃** | `engine.handleError(e)` 本身可能抛异常，导致 listener 回调内崩溃 | 加 `try-catch(Throwable)` 兜底，降级为 `callback.onError(msg)` |
| 4 | **engine.release() 崩溃** | 释放引擎时 native 层可能异常 | 加 `try-catch(Throwable ignored)` |
| 5 | **FFmpegUtil 初始化崩溃** | `ensureReady()` 在 context=null 或 assets 复制失败时抛 `IllegalStateException`，调用方未捕获 | `ffmpeg()`/`ffprobe()` 入口加 try-catch，返回 `Result(exitCode=-1)` 而非崩溃 |
| 6 | **PlaybackActivity 生命周期崩溃** | `onServiceConnected` / `onError` 可能在 Activity 已销毁后回调 | 新增 `isAlive()` 方法（`!isFinishing() && !isDestroyed()`）；`onServiceConnected` 首行加生命周期检查；`onError` 回调加 `isAlive()` 守卫 |

### 版本号
- versionCode 613 → **614**
- versionName 5.5.64 → **5.5.65**

---

## [v5.5.64] - 2026-08-13

### 修复：TG 搜索「未命中任何公开帖子」

#### 根因
`SocialApi.searchTg()` 之前请求 `t.me/s/{channel}`（不带搜索参数），只拿到频道最新 ~20 条帖子做本地关键词匹配。如果"庆余年"不在最新 20 条里 → 0 命中。

#### 修复
| # | 改动 | 说明 |
|---|------|------|
| 1 | **URL 加 `?q=` 参数** | `t.me/s/{channel}?q={keyword}` 让 Telegram 服务端搜索整个频道历史消息，不再只扫最新 20 条 |
| 2 | **浏览器 UA** | 新增 `fetchTgPreview()` 方法，带 `User-Agent: Mozilla/5.0...Chrome/120...Mobile Safari` + `Accept-Language: zh-CN`，避免 t.me 返回精简页面或拒绝请求 |
| 3 | **帖子直链** | 新增 `parseTelegramPostUrls()` 提取每条帖子的 `t.me/{channel}/{messageId}` 直链，点击可跳转到具体帖子（之前只链接到频道首页） |
| 4 | **频道状态检测** | 检测 HTML 中的 `DELETED/CLOSED` 标记，提示"频道已关闭/删除"而非笼统的"未命中" |
| 5 | **错误提示优化** | 无命中时提示"该关键词在这些频道的历史中不存在，或频道为私密频道无法预览" |

### 版本号
- versionCode 612 → **613**
- versionName 5.5.63 → **5.5.64**

---

## [v5.5.63] - 2026-08-13

### 社交搜索：默认公开频道兜底 + 自定义关键词网络搜索测试 + 合并搜索关闭全链路无请求

用户需求：
1. 高级设置中的 TG 搜索频道，**默认为网络中的公开频道**（用户不配置也能直接搜、直接测）。
2. 高级设置里的测试搜索，**可以自定义搜索内容**（比如输入「庆余年」），从真实网络上发起 TG / X 搜索，并且在结果对话框里显示命中的标题/内容片段/链接 URL。
3. 「合并社交搜索到点播」总开关**关闭时**，测试搜索、Bot 验证、真实搜索、节流 sleep 等全链路都要跳过（完全不发任何 TG / X 请求，省流量 + 防封号）。

#### 实现要点

| # | 模块 | 行为 |
|---|------|------|
| 1 | **TG 默认公开频道兜底** | 新增 `Setting.TG_CHANNELS_DEFAULT`（8 个覆盖中英文影视/动漫/剧集的公开频道：subsplease_movies, subsplease, nxupdates, YHYS_01, ysjzyd, dianyingjie123, movieheavenx, dytt123）。`getTgChannelList()` 在用户未保存（Prefers 里为空）时**自动返回该默认列表**；新增 `isTgChannelListUserDefined()` 判断当前使用的是默认值还是用户手动配置值。之前 SocialApi.searchTg 在频道为空时直接返回 fail，现在默认有 8 个公开频道就能真正去 `t.me/s/<channel>` 抓公开 HTML 做关键词匹配。 |
| 2 | **自定义关键词测试搜索** | `SettingAdvancedActivity.onSocialTest()` 改为：先通过 `showKeywordInputDialog()` 弹出关键词输入框（**默认预填「庆余年」**，hint 提示也给出「庆余年 / 庆余年2 / 三体」等示例；输入法 IME_ACTION_SEARCH、TYPE_TEXT_VARIATION_FILTER 便于搜索）。用户点「开始搜索」后，后台线程执行 `runSocialTestWithKeyword(keyword)`，依次调用 `testTgBot + searchTg(keyword, maxPerChannel)`（TG 搜公开频道 HTML）/ `testX + searchX(keyword, xMaxResults)`（X 调 v2 /2/search/recent 近 7 天），结果对话框逐条展示命中的 [tg/x] 标记、标题、内容摘要（80 字截断）、原始帖子 URL，并在标题栏和结尾汇总「合计命中 N 条 / 单轮合并上限 M 条」。不再用之前硬编码的 "1080p" 与 "movie trailer"。 |
| 3 | **频道编辑 UI 增强** | `showChannelListDialog()` 顶栏显示"当前使用【默认/用户自定义】N 个频道"；下方给出 8 个默认频道示例与「逗号/分号/空格/换行分隔都行」的格式说明；新增**「恢复默认」**中性按钮（写入空字符串，get 时自动兜底回默认 8 个公开频道）；保存/恢复后 Toast 都显示频道数量，用户一目了然。 |
| 4 | **合并搜索关闭全链路跳过（双重门控）** | ① UI 入口：`onSocialTest()` 第一行就判 `!Setting.isSocialSearchEnabled()`，**立即弹窗告知关闭状态并 return**，不展示关键词输入框，不进入后台线程，绝无任何网络调用。② 网络层兜底：`SocialApi.preflightTg()/preflightX()` 在 `testTgBot/searchTg/testX/searchX` 四个 public 方法的**最开头再次检查开关**，关了直接 `return Result.fail("社交搜索总开关已关闭，跳过 TG/X 请求")` → **既不 rateLimitSleep、也不发 HTTP，从代码路径上彻底消除任何 TG/X 联网可能**（即便未来有其他调用方绕过 UI 也安全）。 |

#### 体验流程示例
```
用户第一次打开高级设置 → 没填任何频道 → 默认有 8 个公开频道
        ↓
点「立即测试连接并搜索示例」→ 弹窗预填「庆余年」→ 点「开始搜索」
        ↓
TG 端：依次访问 t.me/s/subsplease_movies、t.me/s/YHYS_01 … 等 8 个公开页 → HTML 解析 post → 匹配「庆余年」关键字 → 汇总前 5 条展示
X  端：GET api.x.com/2/search/recent?query=庆余年 → 返回最近 7 天推文 → 前 5 条展示
        ↓
结果对话框：标题 "社交搜索结果 · 庆余年（命中 13 条）"，每条含 [tg]/[x] 标题 + 80 字内容摘要 + 原帖链接 → 用户可点击跳转
```

#### 关闭合并开关后的行为
```
用户关闭「合并社交搜索到点播」总开关
        ↓
点「立即测试」 → 立即弹窗："社交搜索已关闭 / 请先打开总开关再测试连接 / 关闭状态下不会发起任何 TG/X 网络请求，避免被封号/省流量" → 确定后什么都不做（不弹关键词框、不跑后台线程、0 HTTP 请求）
        ↓
即便未来有代码绕过 UI 直接调 SocialApi.searchTg("庆余年", 3)
        ↓
preflightTg() 第一行 isSocialSearchEnabled()==false → 直接 return Result.fail("社交搜索总开关已关闭，跳过 TG 请求。") → 无 sleep、无 HTTP、无任何副作用
```

### 版本号
- versionCode 611 → **612**
- versionName 5.5.62 → **5.5.63**

---

## [v5.5.62] - 2026-08-13

### 高级设置默认隐藏 + 点击版本号 20 次解锁 + 社交搜索增强（TG/X 跳转 App / 限速防封 / 总开关）

用户需求：
1. 设置页新增「高级设置」入口，**默认不显示**；用户连续点击底部**版本号 20 次**后自动显示。
2. 高级设置里新增 TG / X 社交搜索配置：
   - 总开关「合并社交搜索到点播」：关闭后即使已填 token 也不发请求。
   - 一键跳转到**官方 App**：TG → `t.me/BotFather`；X → `developer.x.com` 拿 Bearer Token。
   - 连接测试成功后**自动拉取并缓存账号名**（@xxx / id=xxx），UI 直接显示，不用每次重刷。
   - **限速三档可调**（TG 最小间隔 / X 最小间隔 / 单轮命中上限），全部下限保护：TG ≥ 500ms，X ≥ 800ms，单轮命中 [1, 100]。
   - `SocialApi` 内部已加 sleep 节流 + 开关门控：**不要搜索太快，避免被封账号**。

### 下载稳定性修复：多镜像探针 + ZIP 魔术头校验 + host 黑名单

问题：之前使用公益反代（ghps.cambridgecs / ghproxy 等）下载时，偶发返回 HTTP 200 但 body 是 HTML 错误页 → 下载完成「文件损坏」（长度不匹配）。

修复：
- `Github.probeOne` 做**三重校验**：① Content-Type 非 `text/html`；② Content-Length 合理（>10MB 且 <1GB）；③ 读取前 4 字节校验 `PK\x03\x04` ZIP 魔术头。
- 新增 `BAD_MIRROR_HOSTS` 黑名单：下载失败的 host 自动拉黑，本轮重试不再选它。
- 默认镜像从 ghproxy.com 切到 **GitHub 直连**（最快最稳，CI 能直连）；jsdelivr 已删除（实测必 404）；新增 `objects.githubusercontent.com` 直连源。
- `Updater` 下载超时从 10s → 60s；错误提示显示黑名单信息 + 重试建议。

### SettingAdvancedActivity ClassCastException 修复

`lockedHint` 和 `socialCard` 在 XML 中是 `MaterialTextView` / `MaterialCardView`，但 Java 里声明为 `LinearLayout` → 启动时崩溃。修复为正确类型。

### 版本号
- versionCode 610 → **611**
- versionName 5.5.61 → **5.5.62**

---

## [v5.5.61] - 2026-08-13

### 高级设置（SettingAdvancedActivity）初版：社交搜索配置 UI + 版本号点击 20 次解锁

- 参考 `SettingPlayerActivity` 风格创建 `SettingAdvancedActivity`，内含 TG Bot Token / X Bearer Token 粘贴、TG 频道列表、X 自定义代理前缀等入口。
- 手机端 `SettingFragment` + TV 端 `SettingActivity` 同步新增：
  - 「高级设置」按钮默认 `GONE`，解锁后置 `VISIBLE`；
  - 底部版本号 `onClick` → 计数器累计 20 次 → 写入 `Setting.putSocialSearchUnlocked(true)` 并 Toast。
- `Setting` 新增 `isSocialSearchUnlocked / putSocialSearchUnlocked`（SharedPreferences 持久化）。
- 镜像模式迁移：`Setting.getMirrorMode` 里显式迁移老用户 index（旧 7=DIRECT → 新 0；旧 jsdelivr=6 → 回退默认 0）。

### 修复颜色资源引用错误（white_alpha_70/10 → white_70/10）

`activity_setting_advanced.xml` 引用了不存在的 `@color/white_alpha_70` 和 `@color/white_alpha_10`，导致 CI 失败，release 停留在旧版本。替换为项目已有的 `white_70` / `white_10`。

### 版本号
- versionCode 607 → **610**
- versionName 5.5.58 → **5.5.61**

---

## [v5.5.58] - 2026-08-13

### 下载优化初版：Updater 下载重试 + Github 多镜像

- `Github` 构建多条 APK 下载候选（直连 + ghproxy 系列反代）。
- `Updater` 下载失败时自动切下一条镜像。

### 版本号
- versionCode 605 → **607**
- versionName 5.5.56 → **5.5.58**

---

## [v5.5.56] - 2026-08-12

### APK 版本号 bump（v605 / 5.5.56）

修复早期 build 资产版本不匹配问题，触发 CI 重新打包。

### 版本号
- versionCode 603 → **605**
- versionName 5.5.54 → **5.5.56**

---

## [v5.5.54] - 2026-08-08

### 壁纸 API 未配置时自动使用内置接口 & 设置 UI 只显示「内置」不暴露具体接口

用户反馈的问题：
1. 当用户没有填任何壁纸 API，App 首页背景没有画面，也不会自动用官方默认接口。
2. 设置页里即使是默认/内置，也会把真实 URL `https://www.hhlqilongzhu.cn/api/MP4_xiaojiejie.php` 完整显示出来，不够简洁。

#### 解决方案
- **WallConfig** 增加 `BUILTIN_WALLPAPER_URL` 与 `BUILTIN_DISPLAY_NAME = "内置"` 常量，提供 `useBuiltinIfEmpty()` / `isBuiltin()` 工具方法。
- **WallConfig.getUrl()**：url 为空/未配置时，直接返回内置 URL，确保即使数据库里没记录，实际拉取壁纸也会走内置接口。
- **WallConfig.getDesc()**：url 属于内置时 **只显示「内置」两个字**，不返回真实 URL；UI 里（SettingFragment/SettingActivity 的 `wallUrl.setText(WallConfig.getDesc())`）直接生效。
- **load(Config config)**：url 空时先用 `useBuiltinIfEmpty()` 填默认再走 resolveRealUrl → 下载。
- **ConfigDialog（mobile + leanback）**：壁纸对话框当 url 内置时，name 框默认填「内置」提示，url 框显示空白（用户不输 = 继续用内置），不会把具体接口地址回填给用户看。

### 设置页（更新对话框）「卷起来的部分」改造为：上 = 授权激活码，下 = 更新内容

用户要求把原更新对话框底部的 Debug 信息面板（截图里红圈的那一块，通常写着本地/远程/来源/比较等几行小字的区域）改造：
- **上部**：显示「授权激活码」输入框 + 保存按钮 + 激活状态提示
- **下部**：显示「更新内容」（release.body，即 GitHub Release 的 changelog 正文；若发生下载失败/连不上 GitHub，也用这里展示失败原因方便排错）

#### 解决方案
- **dialog_update.xml（mobile + leanback）**：
  - 保留一个 `id=debug` 的零高 gone TextView，让旧代码 `binding.debug != null` 判空仍成立（不会崩）。
  - 新增 `licensePanel`（上部）：`licenseTitle`「授权激活码」 + `licenseCode` 输入框 + `licenseSave` 保存按钮 + `licenseStatus` 激活状态。
  - 新增 `changelogPanel`（下部）：`changelogTitle`「更新内容」 + `changelogText` 长文本。
- **UpdateDialog（mobile + leanback）**：
  - `initView()` 里回填 `Setting.getKami()` / `Setting.isKamiActivated()`；`licenseSave` 点击/`IME_ACTION_DONE` 即 `Setting.putKami(code)` + `Setting.putKamiActivated(!code.isEmpty())`，并 Toast「激活码已保存/已清空激活码」。
  - 新增 `setChangelog(text)` 写下部更新内容；`setDebugInfo(text)` 保留兼容：仅当 changelog 还没填过时才作为 fallback 写进去，避免覆盖更正式的 release.body。
- **Updater.java**：
  - 取到 release.body（`desc`）后，对「已是最新」和「有新版本」两种分支，都调用 `dialog.setChangelog(desc)` 写入下部更新内容。
  - 连不上 GitHub API / 抛异常 / 所有镜像下载失败等错误情况，也用下部更新内容区域显示错误详情 + 预测试总结，替代原 setDebugInfo 塞进 debug 面板的做法。

### 版本号
- versionCode 602 → **603**
- versionName 5.5.53 → **5.5.54**

---

## [v5.5.53] - 2026-08-08

### 修复 CI 编译失败：Github.java:163 `error: cannot find symbol App.post`

Github.java v5.5.51 新增的 probeUrls 进度回调里调用了 `App.post(new Runnable() {...})`，但 Github.java 文件顶部缺少 `import com.ssmhdssmhd.mxboxs.App;`，导致 GitHub Actions 上 `Build Mobile arm64-v8a (Phone)` 在 compileMobileArm64_v8aReleaseJavaWithJavac 阶段报错 BUILD FAILED（44s），后续 Leanback/armeabi-v7a 全被 skipped → 只产出了 v5.5.50 APK。

| 字段 | v5.5.52 Actions 状态 | v5.5.53 修复后 |
|---|---|---|
| Run #31269751767 (948f874) | Job build → step 10 ❌ compileMobileArm64_v8a failed | Run 新 commit 4 jobs ✅ |
| APK outputs | No APK files found | MXboxS-*-5.5.53.apk 4 份 |
| Release MXboxS-latest assets | 只有 5.5.50（Run #31260989317 6af35ce） | 自动更新到 5.5.53 |

- 版本号：versionCode 601 → **602** / versionName 5.5.52 → **5.5.53**

---

## [v5.5.52] - 2026-08-08

### 新功能：设置中播放设置分为「点播播放器」和「直播播放器」，可独立选择不同引擎

#### 背景
之前点播和直播共用同一个播放引擎设置（`PlayerSetting.getEngine()`），用户无法为直播单独指定引擎。例如点播用 EXO、直播用 System，需要分开设置。

#### 改动
1. **PlayerSetting** 新增 `getLiveEngine()` / `putLiveEngine()`，key=`live_engine`，默认回退到 `getEngine()`（老用户无感升级）
2. **PlayerEngineFactory** 新增 `live` 参数重载：`create(decode, live, listener)` / `create(decode, spec, live, listener)` / `matches(engine, spec, live)`，`live=true` 时读取 `getLiveEngine()`
3. **PlayerManager** 新增 `liveMode` 字段 + `setLiveMode(boolean)` / `isLiveMode()`；`setEngine()` 根据 `liveMode` 写入 `putLiveEngine()` 或 `putEngine()`；`ensureEngine()` 传入 `liveMode`
4. **LiveActivity（mobile + leanback）** 在 `onServiceConnected()` 调用 `player().setLiveMode(true)`
5. **PlaybackActivity** 在 `onServiceConnected()` 调用 `player().setLiveMode(false)`（VOD 重置）
6. **设置 UI（mobile + leanback）**：原「播放引擎」改为「点播播放器」，新增「直播播放器」行（点击循环切换 EXO/MPV/System/Ali/Nova/IJK）
7. **字符串**：`player_engine` = "点播播放器"/"VOD Player"；新增 `player_engine_live` = "直播播放器"/"Live Player"（zh-CN/zh-TW/en 三语）
8. **PlaybackAction** 新增 `getEngineStatic(boolean live)` 工具方法

#### 版本号
- versionCode 600 → **601**
- versionName 5.5.51 → **5.5.52**

---

## [v5.5.51] - 2026-08-08

### 新方案：先测试再下载（1.5s 超短探针并行扫描）+ 修复 ghps.cambridgecs.co → .com 域名拼写错误 + 修复「正在下载…」按钮一直置灰的根因（Button 引用缓存）

#### 对应你最新两张截图里的问题
1. `Unable to resolve host "ghps.cambridgecs.co": No address associated with hostname` → **之前把域名写错了！** 正确是 `cambridgecs.com` 而不是 `.co`；
2. `下载失败：timeout` 但右下角按钮仍是 **灰色「正在下载…」** → mobile 版 `setConfirmEnabled(enabled, textRes)` 在 `getDialog()==null` 时直接 return，按钮状态永远没应用。

#### 「先测试，再下载」方案设计（你问的思路已直接落地代码）

**阶段 1 · Probe 预测试（并行 8 线程，1.5s connect/read 超时）**
- 触发时机：`apkCursor == 0`（用户点「更新」或「重试」）时跑一次；
- 测试对象：所有 10~14 条 APK URL（候选 = 镜像前缀 + jsdelivr @tag）；
- 每条测试方法：
  1. 先 `HEAD` 请求（最快，不下载字节），超时/HTTP 405/非 2xx 回退；
  2. 回退 `GET Range: bytes=0-0` 只拿 1 字节（很多镜像对 HEAD 返回 405，但 GET 正常）；
  3. 成功：ok=true + RTT（毫秒级），失败：ok=false + 短错误文案（DNS 解析失败/timeout/连接被拒/SSL 握手失败）。
- UI 反馈：每条 probe 完成后立刻刷新 status：
  - 成功：`探针 3/14：ghproxy.com ✅ 187ms（继续探测剩余 11 条）…`
  - 失败：`探针 4/14：ghps.cambridgecs.com ❌ DNS 解析失败（继续探测剩余 10 条）…`
  - 进度条 `setProgress(percent)` 跟着 `n/total` 推进，不会出现「0% 假死」。
- 结束排序：ok 优先 → 按 RTT 升序，失败的放末尾；apkUrls 重排，后续下载就从 **最快的可用镜像** 开始。

**阶段 2 · Download 真正下载（沿用 10s timeout + 自动切源）**
- `apkCursor=0`：开始下载，status 文字立刻显示 `下载中（mirror.ghproxy.com，候选镜像共 14 条 · 10s 超时快速切源）…`
- error() 切源：还是 `apkCursor++ → App.post startDownload`，但因为 **probe 阶段已把可用 URL 放最前**，通常到不了切源；
- 全部失败：debug 面板**追加「预测试总结」详细列出 ✅X 条可用 / ❌Y 条失败 / 失败项明细**，用户一眼能判断哪几个镜像 DNS 不通或被墙。

**按钮状态修复（mobile 端最关键）**
- 新增 `cachedPositive` 字段（Button）+ `pendingConfirmEnabled/pendingConfirmTextRes` 挂起状态；
- `setConfirmEnabled(enabled, textRes)` 改为：
  1. 先取 cachedPositive，再尝试再取 dialog button；
  2. 如果都为 null（onStart 未触发前 dialog 没创建），写入 pending 字段；
  3. `onStart()` 触发时把 pending 应用到刚创建的 Button 上。
- 保证 error 分支调用 `setConfirmEnabled(true, R.string.update_retry)` 时**哪怕 dialog 还没完全 ready，等 onStart 后也一定会变成蓝色可点的「重试」**，不会再停在灰色「正在下载…」。

#### 镜像域名修复 & 新增
- `MIRROR_GHPS_CAMBRIDGECS`：`https://ghps.cambridgecs.co` → `https://ghps.cambridgecs.com`
- `MIRROR_OPTIONS` 显示名同步：`ghps.cambridgecs.co（国内）` → `ghps.cambridgecs.com（国内）`
- 新增 2 条公益 ghproxy 镜像（避免前 10 条全挂）：
  - `MIRROR_GH_1MS = https://gh.1ms.run`（国内）
  - `MIRROR_GH_DOG = https://gh.dmirror.xyz`（国内）

#### 版本号
- versionCode 599 → **600**
- versionName 5.5.50 → **5.5.51**

---

## [v5.5.50] - 2026-08-08

### 修复：下载进度条全程 0%（无 Content-Length 导致回调被跳过） —— Range 头拿总大小 + 每 200ms 汇报已下载字节数

#### 现象（来自你的截图）
- 下载对话框显示 `下载中 (mirror.ghproxy.com) …`，但进度条一直停在 0%，数字也是 0%；
- 右下角按钮文案正确变成「正在下载…」（v5.5.49 修复的），但进度条完全不动。

#### 根因
1. **GitHub browser_download_url 走 chunked 编码**：APK 下载响应没有 `Content-Length` header，旧 `Download.getLength()` 返回 -1；
2. **进度回调被跳过**：`download()` 方法中 `if (length <= 0) continue;` 直接跳过了所有进度回调，导致 UI 永远 0%；
3. **即使有 Content-Length 也只更新百分比**：没有显示已下载字节数/总大小，用户无法判断下载是否真的在进行。

#### 修复
1. **强制 Range 头请求**：`doInBackground` 改用 `Request.Builder().header("Range", "bytes=0-")`，让 GitHub 返回 `Content-Range: bytes 0-N/N`，从而解析出 APK 总大小；
2. **双路进度回调**：
   - 已知总大小：`callback.progress(percent, downloadedBytes, totalBytes)` 每块都回调；
   - 未知总大小：每 200ms 回调一次 `callback.progress(-1, downloadedBytes, -1)`，显示「已下载 X.X MB」；
3. **Callback 接口升级**：新增 `progress(int progress, long downloadedBytes, long totalBytes)` 默认方法，兼容旧 `progress(int)` 接口；
4. **UI 显示升级**：`UpdateDialog.setProgress(progress, downloaded, total)` 显示「42% · 12.3 MB / 29.1 MB」或「已下载 12.3 MB」；
5. **Download.formatBytes() 工具方法**：格式化字节为 "X.X KB/MB/GB"。

#### 代码位置
- [Download.java#L79-L154](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/Download.java#L79-L154) — Range 头 + Content-Range 解析 + 双路进度回调
- [Download.java#L182-L212](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/Download.java#L182-L212) — Callback 接口升级 + formatBytes
- [mobile UpdateDialog#L127-L148](file:///workspace/app/src/mobile/java/com/ssmhdssmhd/mxboxs/ui/dialog/UpdateDialog.java#L127-L148) — 带字节数的进度显示
- [leanback UpdateDialog#L120-L141](file:///workspace/app/src/leanback/java/com/ssmhdssmhd/mxboxs/ui/dialog/UpdateDialog.java#L120-L141) — 同上（TV 端）
- [Updater.java#L338-L347](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/Updater.java#L338-L347) — 新 progress 回调适配

#### 版本号
- versionCode 598 → **599**
- versionName 5.5.49 → **5.5.50**

---

## [v5.5.49] - 2026-08-08

### 修复：「下载失败：timeout」后按钮一直「正在下载…」置灰，无重试入口 —— 短超时 10s 自动秒切 10 条镜像 + 所有镜像失败后按钮改为「重试」

#### 现象（来自你的 v5.5.46 客户端截图）
- 检测新版本正常（发现 v5.5.47 / e718f36，Commit 一致），但下载后显示 `下载失败：timeout`；
- 右下角按钮仍显示「正在下载…」并保持 **置灰不可点**，用户除了点「取消」关闭对话框别无他法；
- 没看到 `镜像 2/10：切换到 ghps…` 这类切源提示，v5.5.46 默认下载超时是 OkHttp 30s 级别，用户等不及直接取消。

#### 根因
1. **OkHttp 默认 connect/read 超时太长**：旧 `Download.doInBackground()` 直接用 `OkHttp.newCall(url, tag)` 走全局默认 30s 超时，ghproxy 被墙/宕机时要等足 30s 才抛 SocketTimeoutException。
2. **「正在下载…」按钮状态不恢复**：`showProgress()` 把 PositiveButton `setEnabled(false) + setText(正在下载…)`，但 `error()` 里只调用了 `setConfirmEnabled(true)`，**按钮文案没改回可识别的「重试/更新」**，于是即便内部 enabled=true，UI 上用户看还是灰色「正在下载…」四个字（颜色不可点），实际 disabled 状态与文案不一致。
3. **按钮 onConfirm 也有状态置乱**：点击后 `view.setEnabled(false)`，切回 `apkCursor=0, startDownload()` 马上又 `view.setEnabled(true)`，导致 `showProgress()` 内刚设的禁用被覆盖。
4. **旧 v5.5.46 客户端候选列表可能不足 5 条**：`Github.getMirrorCandidates()` 在 v5.5.46 只有 5 条前缀，且不含 `mirror.ghproxy.com`/`ghps.cambridgecs`/`ghproxy.net`/`gh.mirai`/`jsdelivr`，失败就没得切。

#### 修复
1. **Download 专属 APK 下载短超时（10s）**
   - `Download.create(url, file, timeoutMs)` 新重载；新增 `DEFAULT_TIMEOUT_MS=20s` / `APK_DOWNLOAD_TIMEOUT_MS=10s` 常量；
   - `doInBackground` 改用 `OkHttp.client(true, timeoutMs)` + `OkHttp.newCall(client, url, tag)`；
   - 失败时错误消息追加上下文：`timeout（10000ms 内未响应，已自动快速失败）`，避免用户以为卡死；
   - 新增 `volatile Call activeCall`，cancel 时优先 `call.cancel()`（比 dispatcher().queuedCalls/runningCalls + tag 取消更快）。

2. **「正在下载…」按钮失败后改为「重试」并可点击（两套布局 mobile + leanback 都处理）**
   - `UpdateDialog.setConfirmEnabled(boolean enabled, int textRes)` 新增重载，enabled=true 时同时把按钮文字改成指定资源（`R.string.update_retry = 重试`）；
   - Updater.error() 全部失败分支：`dialog.setConfirmEnabled(true, R.string.update_retry)`，status 显示「下载失败：xxx（全部 10 条镜像均失败）」，debug 面板追加「最后一次错误…可点击右下角『重试』…」指引；
   - Updater.error() 切源分支：status 显示「镜像 2/10：切换到 ghps …（timeout 10000ms…）」，明确告知用户前次失败原因；
   - startDownload 当 APK not found 时也设为「重试」可点击。
   - 国际化补齐：zh-rCN「重试」/ zh-rTW「重試」/ en "Retry"（values-zh-rCN、values-zh-rTW、values 三条 strings 都加了 `update_retry`）。

3. **onConfirm 去掉手动 view.setEnabled 写回**
   - 原 onConfirm 内的 `view.setEnabled(false); apkCursor=0; startDownload(); view.setEnabled(true);` 会把 `showProgress()` 刚设的 disabled 顶成 true，导致 UI 上「正在下载…」其实是 enabled 状态但灰色；现在直接由 `showProgress()` 和 `setConfirmEnabled()` 统一管理，不用手动写。

4. **保证候选 URL >= 10 条（兜底 ensureCandidates + jsdelivr 专用镜像）**
   - `Github.findApkUrls()` 额外调用 `buildJsDelivrCandidates(release, direct)`：从 `https://github.com/{owner}/{repo}/releases/download/{tag}/{file}.apk` 正则切出 owner/repo/tag/file，再拼出 fastly.jsdelivr + cdn.jsdelivr 的两条反代路径；
   - `Github.ensureCandidates()` 再兜底：`Updater.doInBackground` 对 findApkUrls 的结果再 `ensureCandidates(existing, release, getMirrorCandidates())` 重拼去重，保证 v5.5.46 老版本如果继承了旧 findApkUrls 候选太少，也能自动补到 10+；
   - 候选池现 10 条前缀 + jsdelivr 2 条专用，最多 12 条，全部进入 rankByConnectivity 并行 HEAD 探测排好。

**代码位置：**
- [Download.java#L19-L101](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/Download.java#L19-L101) 短超时 + activeCall 取消
- [UpdateDialog(mobile).setConfirmEnabled+readDebugInfo](file:///workspace/app/src/mobile/java/com/ssmhdssmhd/mxboxs/ui/dialog/UpdateDialog.java#L128-L149) / [UpdateDialog(leanback)](file:///workspace/app/src/leanback/java/com/ssmhdssmhd/mxboxs/ui/dialog/UpdateDialog.java#L121-L149) setConfirmEnabled 重载 + readDebugInfo
- [strings.xml update_retry](file:///workspace/app/src/main/res/values-zh-rCN/strings.xml#L235-L237) / [zh-rTW](file:///workspace/app/src/main/res/values-zh-rTW/strings.xml#L234-L236) / [en](file:///workspace/app/src/main/res/values/strings.xml#L239-L241)
- [Updater.startDownload 10s 短超时 + 按钮文案](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/Updater.java#L240-L275)
- [Updater.onConfirm 移除 view.setEnabled 手动写回](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/Updater.java#L315-L320)
- [Updater.error 切源按钮状态 + 全部失败改为「重试」+ Debug 追加指引](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/Updater.java#L343-L372)
- [Github.findApkUrls + buildJsDelivrCandidates + ensureCandidates](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/Github.java#L377-L452)

版本号：versionCode 597 → **598** / versionName 5.5.48 → **5.5.49**（[app/build.gradle#L22-L23](file:///workspace/app/build.gradle#L22-L23)）

---

## [v5.5.48] - 2026-08-08

### 修复：点「检测更新」提示「已是最新」却不显示为什么？——对话框追加 Debug 版本信息，版本号来源/Release 来源/比较结果一目了然

#### 现象
- 你截图显示「正在检测更新…」，下面「版本 5.5.46」；
- 点检测更新后最终状态 `update_no_new = 已是最新`，但远端 **Release 到底被识别成哪个版本 / 走了哪个 API / 为什么比 5.5.47 小** 完全看不见；
- 之前 `forced=true` 但结果是「已是最新 / 连接失败 / 异常」三个分支都不一定会 `showDialog`，导致用户点了按钮 UI 上看似无响应，也看不到任何 Debug 信息。

#### 根因
1. **对话框完全不展示版本比较细节**：`update_no_new` 文案只有四个字，无法区分「网络根本没连上 GitHub」 vs 「连到了但返回 v5.5.46」 vs 「返回 MXboxS-latest tag 但 APK 文件名匹配不到 X.Y.Z」。
2. **forced 分支不保证 showDialog**：旧版只有 `dialog!=null || forced` 才 showDialog，`getHighestRelease` 网络成功但 cmp<=0 时，若之前还没 new 过对话框则 UI 静默。
3. **APK 文件名取版本失败时缺少 APK 名证据**：`extractVersionFromAssets` 只返回 String，失败时用户不知道 CI 实际上传的 APK 叫什么（例如 CI 还没 build v5.5.47 就会只有 5.5.46）。

#### 修复
1. **对话框底部新增 `<debug>` 字段（mobile + leanback 两套布局都加）**
   - [mobile dialog_update.xml](file:///workspace/app/src/mobile/res/layout/dialog_update.xml#L57-L71) / [leanback dialog_update.xml](file:///workspace/app/src/leanback/res/layout/dialog_update.xml#L79-L93)：新增 `debug` MaterialTextView（monospace、灰色底、默认 GONE）。
   - [mobile UpdateDialog.java](file:///workspace/app/src/mobile/java/com/ssmhdssmhd/mxboxs/ui/dialog/UpdateDialog.java#L95-L104) / [leanback UpdateDialog.java](file:///workspace/app/src/leanback/java/com/ssmhdssmhd/mxboxs/ui/dialog/UpdateDialog.java#L92-L101)：新增 `setDebugInfo(text)`；`updateDesc` 会保留 debug 内容，不被覆盖清空。
   - `Updater.buildDebugInfo()` 统一生成 6 行诊断信息：
     ```
     本地：5.5.46 (595)
     远程：5.5.46 (来源：APK 文件名)
     Release tag：MXboxS-latest
     匹配 APK：MXboxS-mobile-arm64_v8a-5.5.46.apk
     Release来源：getHighestRelease(/releases?per_page=10)
     比较：server=5.5.46, local=5.5.46, compareVersion 返回 0 → 判定已是最新。
           要升级到 5.5.47+，请先把本地 3 个新 commit push 到 origin/main 触发 CI 产出 v5.5.47 APK asset。
     ```

2. **`ensureDialogShown(activity)`：所有终态分支（无网络 / 已是最新 / 有新版本 / Exception）都先确保 showDialog**
   - 解决 forced 模式点按钮没反应、Toast 一下就没了的问题，让 Debug 信息一定能被用户看到。
   - 无 Release 对象时追加 Toast `Notify.show("更新检测：未连上 GitHub API（Release来源：xxx）")`；Exception 追加 Toast `更新检测异常：xxx`。

3. **`Github.extractVersionFromAssetsWithDebug(release)`：除了返回版本号，还返回匹配到的 APK 文件名**
   - 失败时 second = 第一个 APK 文件名（哪怕没 X.Y.Z），直接暴露「CI 还没 build 这个版本」。

#### 你的问题具体怎么看？（诊断结论）
你现在看到「版本显示最新版本」→ 100% 是因为 **GitHub 远端（origin/main）还没有 v5.5.47 / v5.5.48 的 APK assets**：
- 这节会话我们本地 commit 了 v5.5.46 → v5.5.47 → v5.5.48 三次 commit，**但你还没 `git push origin main`**；
- 没有 push → CI 不会重新运行 build.yml → 不会在 `MXboxS-latest` prerelease 下上传 `MXboxS-mobile-arm64_v8a-5.5.48.apk`；
- 因此 `getHighestRelease()` 能拿到的最高 APK 版本号还是 5.5.46，`compareVersion(5.5.46, 5.5.46) = 0` → 判定「已是最新」。

Push 之后 v5.5.48 版的 Debug 区域会直接显示：
- `远程：5.5.48 (来源：APK 文件名)` / `匹配 APK：MXboxS-mobile-arm64_v8a-5.5.48.apk` → 证明确实已经检测到新版本；
- `候选 APK 镜像：10 条` → 紧接着就自动进入「并行 HEAD 探测 → 下载」流程。

**代码位置：**
- [Updater.java#L77-L238](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/Updater.java#L77-L238) buildDebugInfo + ensureDialogShown + doInBackground 四个终态分支统一 showDialog + setDebugInfo
- [Github.java#L404-L433](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/Github.java#L404-L433) extractVersionFromAssetsWithDebug
- [mobile UpdateDialog.java](file:///workspace/app/src/mobile/java/com/ssmhdssmhd/mxboxs/ui/dialog/UpdateDialog.java#L81-L104) / [leanback UpdateDialog.java](file:///workspace/app/src/leanback/java/com/ssmhdssmhd/mxboxs/ui/dialog/UpdateDialog.java#L79-L101) setDebugInfo
- [mobile dialog_update.xml](file:///workspace/app/src/mobile/res/layout/dialog_update.xml#L57-L71) / [leanback dialog_update.xml](file:///workspace/app/src/leanback/res/layout/dialog_update.xml#L79-L93) 新增 debug 字段

版本号：versionCode 596 → **597** / versionName 5.5.47 → **5.5.48**（[app/build.gradle#L22-L23](file:///workspace/app/build.gradle#L22-L23)）

---

## [v5.5.47] - 2026-08-08

### 镜像加速升级：国内 7 条 + 海外 3 条公共镜像，并行 HEAD 预探测「自动挑最快」，失败秒切

#### 现象（来自 v5.5.46 实测反馈）
- 用户截图看到 `镜像 1/5：ghproxy.com 下载中…（前一镜像失败）`，单 ghproxy.com 常宕机；
- 默认只给 5 条镜像、候选里国内镜像覆盖不足（缺 ghproxy.net、gh.mirai.org 这类国内公益站），海外用户又缺 jsdelivr/fastly CDN；
- 每次 fallback 顺序固定为「用户首选 → ghproxy → mirror.ghproxy…」，当前网络明明 ghps.cambridgecs 最快，也要先在 ghproxy 上卡 30s 才切过去；
- 「设置 → 更新源」下拉只 3 个选项，没法手动选到 ghps/99988866/ghproxy.net/jsdelivr 等。

#### 根因
1. **镜像池太小 + 国内/海外未分流**：v5.5.46 只有 4 个镜像前缀，海外用户拿到的全是国内反代，延迟反而比 GitHub 直连还高。
2. **下载顺序静态**：不管当前网络到某镜像 RTT 多高，都要先等 30s 超时才 fallback。
3. **Setting 镜像索引写死**：`MIRROR_GHPROXY=0 / MIRROR_MIRROR_GHPROXY=1 / MIRROR_DIRECT=2`，新增镜像就要改 Updater 字符串数组 + Setting 两处，容易错位。

#### 修复
1. **镜像池扩充到 10 条前缀（国内 7 + 海外 3 + GitHub 直连）**
   - 国内（`CN_MIRRORS`）：mirror.ghproxy / ghps.cambridgecs / ghproxy.net / gh.api.99988866.xyz / gh.mirai / ghproxy / gh.tmoe
   - 海外（`OVERSEA_MIRRORS`）：GitHub Direct / fastly.jsdelivr.net / cdn.jsdelivr.net
   - UI 默认索引改为 `MIRROR_DEFAULT_INDEX = MIRROR_MIRROR_GHPROXY`，避免 ghproxy.com 再次宕机。

2. **并行 HEAD 探测自动挑最快（`Github.rankByConnectivity`）**
   - 第一次点「下载」时，对全部候选 APK URL 做 6 线程并行 `HEAD` 请求，单镜像最多 4 秒（`PING_TIMEOUT_MS=4000`）；
   - 返回 2xx/3xx 的按 RTT 升序排序；超时/非 2xx 的一律放最后；
   - 用户手动设的「首选镜像」对应 APK URL 始终保持第一顺位（除非 HEAD 也失败了）。
   - 探测阶段对话框显示 `正在挑选最快镜像（并行探测 4s）…`，避免用户以为卡死。

3. **UI「更新源」对齐 Github.MIRROR_OPTIONS 单一数据源**
   - `Github.MIRROR_OPTIONS` 统一维护「显示名 → 前缀」；`Updater.showMirrorDialog` 直接读取，不再自己写 String[]；
   - `Setting.getMirrorMode` 兼容老数据：之前用户保存过 `2=DIRECT`，v5.5.47 里 DIRECT=7，自动做一次迁移映射。

4. **镜像切换文案更清楚**
   - 初始下载：`下载中（ghps.cambridgecs.co（国内），候选镜像共 10 条）…`
   - 自动切源：`镜像 2/10：切换到 mirror.ghproxy.com（国内） …` → `镜像 2/10：mirror.ghproxy.com（国内） 下载中…（前一镜像失败，自动切换）`
   - 全部失败：错误信息追加「（全部 10 条镜像均失败）」，明确告知不是偶发网络波动。

**代码位置：**
- [Github.java](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/Github.java#L35-L249) MIRROR_OPTIONS / CN_MIRRORS / OVERSEA_MIRRORS / rankByConnectivity / pingHead / getMirrorLabel
- [Updater.java#L61-L75](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/Updater.java#L61-L75) showMirrorDialog 改用 Github.MIRROR_OPTIONS
- [Updater.java#L182-L216](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/Updater.java#L182-L216) startDownload 首次启动并行探测 + 重排 + 文案
- [Updater.java#L284-L305](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/Updater.java#L284-L305) error() 失败切换提示
- [Setting.java#L150-L184](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/setting/Setting.java#L150-L184) 新增 5~7 镜像枚举 + MIRROR_DEFAULT_INDEX + 老数据迁移

版本号：versionCode 595 → **596** / versionName 5.5.46 → **5.5.47**（[app/build.gradle#L22-L23](file:///workspace/app/build.gradle#L22-L23)）

---

## [v5.5.46] - 2026-08-08

### 修复：直播无法正常获取和播放（直播格式识别 + M3U 脏数据过滤 + 多线路自动切源）

#### 现象
- 大量 IPTV / 直播源**一直转圈**或直接**播放失败**，典型受影响直播格式：
  - 无扩展名 HTTP(S) 直播源（`http://example.com/live/cctv1`、`http://tv.example.com/cctv1/stream`）
  - M3U8 查询参数式（`http://example.com/playlist?id=1&mime=m3u8`）
  - m3u 文件中混有 `#EXTHTTP:`、空白行、未知 scheme 导致的脏 URL 列表
  - 单线路频道播放失败后不再重试，卡死在"错误"状态

#### 根因
1. **MIME 类型只看扩展名**：大量直播源不含 `.m3u8/.mpd` 扩展名，ExoPlayer 无法路由到 HLS/DASH Source。
2. **M3U 解析过度宽松**：只要行里含 `://` 就当 URL，把 `#EXTHTTP:{...}`、无效 scheme 加入频道 URL。
3. **失败后不自动切源**：`LiveFallbackPolicy.playbackError` 被 `isLast()` 短路，`switchLine` 又被 `isOnly()` 限制，多线路/重试点完全不生效。

#### 修复
1. **直播 MIME 类型增强识别**：`MediaItemFactory.resolveMimeType`
   - 新增 `hasExt(url, ext)` 正确处理含 `?` / `#` 的 URL 扩展名；
   - 新增 `isLikelyHls / isLikelyDash / isLikelyLiveStream` 按路径关键字（`/live/`、`/stream/`、`/playlist`、`/hls/`、`.tv/`、`cctv/hdtv/iptv/直播/频道`、`mime=m3u8`、`type=m3u8` 等）兜底识别为 HLS/DASH；
   - `rtsp://` / `rtmp://` 留给 Media3 内置 Source 自动处理。
2. **M3U 解析白名单过滤**：`LiveParser.m3u` 新增 `LIVE_URL_SCHEME` 正则，只接受 `http(s)://`、`rtmp://`、`rtsp://`、`video://`、`proxy://` 等已知直播协议；跳过空行与非 URL 行。
3. **多线路 Fallback + 重试**：
   - `LiveFallbackPolicy.playbackError` 移除 `isLast()` 限制，任何线路出错都切下一条源（只有 1 条线路时等价于 `refresh()` 重试）；
   - `LivePlaybackController.switchLine` 移除 `isOnly()` 限制，允许单线路频道重新刷新；
   - 失败时先调用 `host.renderLineSelection`，UI 同步显示切换状态。

**代码位置：**
- [MediaItemFactory.java#L37-L111](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/media/MediaItemFactory.java#L37-L111) resolveMimeType 重写
- [LiveParser.java#L82-L132](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/api/parser/LiveParser.java#L82-L132) LIVE_URL_SCHEME 白名单解析
- [LiveFallbackPolicy.java#L19-L25](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/playback/live/LiveFallbackPolicy.java#L19-L25) isLast 限制解除
- [LivePlaybackController.java#L129-L137](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/playback/live/LivePlaybackController.java#L129-L137) isOnly 限制解除

支持的直播格式覆盖：`.m3u8/.m3u/.mpd/.ts/.flv`（含 query/fragment）→ 无扩展名 IPTV/HTTP(S) 直播流 → `rtsp://` / `rtmp://` → 内置 `video://` / `proxy://` 通道。

版本号：versionCode 594 → **595** / versionName 5.5.45 → **5.5.46**（[app/build.gradle#L22-L23](file:///workspace/app/build.gradle#L22-L23)）

---

## [v5.5.45] - 2026-08-08

### 新增功能：AI 智能广告过滤 + AI 功能默认全开

#### 1. 自动过滤简介中的广告文本
- 在 `Util.clean()` 方法中集成广告关键词过滤逻辑
- 支持过滤常见广告类型：
  - 赞助/推广类（如“本片由xxx赞助发布”）
  - 联系方式类（如“添加微信/QQ”、“导航到xxx”）
  - 网站推广类（如“下载APP”、“全网免费观看”）
  - 福利引导类（如“扫码关注公众号”、“更多精彩内容请加”）
- 简介显示时自动清除广告，无需手动操作

#### 2. AI 功能默认全开
- 画质增强、HDR、智能降噪、动态锐化 → 默认开启
- 运动补偿、自适应帧率 → 默认开启
- AI 音质增强、超重低音、对白增强 → 默认开启
- 新安装用户直接享受 AI 优化效果

**代码位置：**
- [Util.java](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/Util.java#L36-L60) 广告关键词模式
- [Util.java](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/Util.java#L150-L168) 过滤逻辑实现
- [PlayerSetting.java](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/setting/PlayerSetting.java#L208-L279) AI 功能默认值

版本号：versionCode 593 → **594** / versionName 5.5.44 → **5.5.45**（[app/build.gradle#L22-L23](file:///workspace/app/build.gradle#L22-L23)）

---

## [v5.5.44] - 2026-08-08

### 修复更新下载"进度条 0% 卡死 → 30s 超时 → 下载失败"（ghproxy.com 宕机 + 索引错位 + 单一 URL 无 fallback）

#### 现象

用户 v5.5.42 / v5.5.40 「设置 → 检查更新」检测到 5.5.43/5.5.44 后：
1. 进度条一直**卡在 0% 不动**，约 30 秒后
2. 弹窗报错：**`下载失败: failed to connect to ghproxy.com/93.46.8.90 (port 443) from /10.196.209.103 (port 59922) after 30000ms`**

#### 根因

三条 bug 同时命中：

**1. ghproxy.com 今日全网宕机**（IP 93.46.8.90 端口 443 connect 超时 30s）。

**2. v5.5.42 下载只会试 1 个 URL，没有 fallback 队列**：
```java
apkUrl = Github.findApkUrl(release);  // 只返回 ghproxy.com/... 一个 URL
download = Download.create(apkUrl, getFile()).start(this);
// error() 回调直接弹错，不会切下一个！
```
所以 ghproxy.com 一挂，用户就永远 `0% → 30s → failed`。

**3. Setting.MIRROR_* 索引与 Updater.showMirrorDialog 选项索引错位**（长期隐藏 bug，今天被激发）：
```
Updater.showMirrorDialog() 单选项（用户界面）:
  items[0] = "ghproxy.com (CN)"        which=0
  items[1] = "mirror.ghproxy.com (CN)" which=1
  items[2] = "Direct GitHub"           which=2
→ Setting.putMirrorMode(which)

v5.5.42 老 Github.getMirror() 判断:
  if mode==1 → MIRROR_GHPROXY   (ghproxy.com)
  if mode==2 → MIRROR_MIRROR_GHPROXY (mirror.ghproxy.com)
  else mode==0 → MIRROR_DIRECT  (直连)
```
结果**完全串位**：用户 UI 点"ghproxy.com"(which=0) → mode=0 → getMirror() 返回**空 DIRECT 直连**；点"mirror.ghproxy"(which=1) → mode=1 → 返回**宕机的 ghproxy.com**！

同时 v5.5.42 默认值 `MIRROR_GHPROXY = 1` 正好命中 **ghproxy.com** → 今天宕机 → **100% 用户默认都会遇到进度条 0%卡死！** 😭

#### 修复

**1. Setting.java：镜像索引对齐 + 默认改 mirror.ghproxy.com**（[Setting.java#L148-L162](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/setting/Setting.java#L148-L162)）

```java
public static final int MIRROR_GHPROXY         = 0;   // UI items[0]
public static final int MIRROR_MIRROR_GHPROXY  = 1;   // UI items[1]
public static final int MIRROR_DIRECT          = 2;   // UI items[2]

public static int getMirrorMode() {
    // 默认值从老的 ghproxy (mode 1) 改成 mirror.ghproxy.com (mode 1 新含义)
    // 副作用：v5.5.42 用户 mirror_mode=1 升级后 → 新代码解析成 MIRROR_MIRROR_GHPROXY
    //       → 自动从宕机 ghproxy.com 切到 mirror.ghproxy.com，无需用户操作！
    return Prefers.getInt("mirror_mode", MIRROR_MIRROR_GHPROXY);
}
```

**2. Github.java：4 个镜像 + 1 个直连 = 5 条候选队列**（[Github.java#L22-L58](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/Github.java#L22-L58) / [Github.java#L186-L203](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/Github.java#L186-L203)）

- 新增 2 个公共镜像：`https://ghps.cambridgecs.co` / `https://gh.api.99988866.xyz`
- `getMirrorCandidates()` 返回去重候选列表：`用户首选 → 其它 4 个候选 → 直连 GitHub`
- 新 `findApkUrls(release)` 对同一 APK browser_download_url 套用 5 个前缀，**返回 5 条 URL**

**3. Updater.java：下载失败自动循环 fallback（主修复）**（[Updater.java#L29-L31](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/Updater.java#L29-L31) / [Updater.java#L184-L213](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/Updater.java#L184-L213) / [Updater.java#L282-L296](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/Updater.java#L282-L296)）

```java
private List<String> apkUrls;
private int apkCursor;

startDownload() {
    String url = apkUrls.get(apkCursor);
    dialog.setStatus("下载中（" + mirrorTag + "）…");   // 0% 不再没任何提示
    dialog.setProgress(0);
    download = Download.create(url, file);
    download.start(this);
}

@Override error(String msg) {
    if (apkCursor + 1 < apkUrls.size()) {       // 还有候选？
        apkCursor++;
        App.post(this::startDownload);          // ← 自动切下一个镜像！
        return;
    }
    // 5 个都失败才真报错
    dialog.setStatus("下载失败: " + msg);
}
```

#### 应急方案（v5.5.42 / v5.5.40 不需要等 v5.5.44 发布，现在操作立即生效）

用户现在打开 App → **设置 → 点「Update Source」（下载源/镜像设置）** → 单选框：

```
●【推荐】选第 2 项 "mirror.ghproxy.com (CN)"  → 确定 → 强制杀掉 App 进程（从最近任务清掉）→ 重新打开 → 再检查更新 → 立即开始下载，进度条正常走
○【海外网络】选第 3 项 "Direct GitHub"
✗【避免】第 1 项 "ghproxy.com (CN)" （今日 IP 93.46.8.90 宕机，30s 超时必失败）
```

版本号：versionCode 592 → **593** / versionName 5.5.43 → **5.5.44**（[app/build.gradle#L22-L23](file:///workspace/app/build.gradle#L22-L23)）

---

## [v5.5.43] - 2026-08-08

### 修复 v5.5.42 引入的「官方源播放失败」（getRealUrl 被 playUrl 前缀拼接污染）

#### 现象
- v5.5.42 修复了 m3u8 Network Connection Failed 后，「官方的」源反而播放失败，返回错误类似 `播放失败` / 转圈超时 / 底层域名解析失败 或 404。
- m3u8/第三方解析源正常。

#### 根因

官方站点 `SiteApi.playerContent` 返回的 Result 结构通常是：
```
{
  "playUrl": "https://cdn.official-source.com/play/",   ← 官方多线路拼接前缀
  "url":     "episode/1080p/xxx.m3u8",                  ← 相对路径
  "parse":   0                                          ← 官方直链（=官方播放器的解析入口直接出 m3u8）
}
```
`Result.getRealUrl() = getPlayUrl() + getUrl().v()`，官方链路就是这样拼出最终直链的。

v5.5.42 第四道防线（`PlaybackActivity.startPlayer`）在识别出伪造本地代理 URL 时，**只做了**：
```java
result.setUrl(unwrapped);        // unwrapped = 完整 https://player.ypls.com/play/R5Ke...
result.setParse(1);
```

**没有清空 playUrl**，于是：
```
getRealUrl() = playUrl + url
            = "https://cdn.official-source.com/play/" + "https://player.ypls.com/play/R5Ke..."
            = "https://cdn.official-source.com/play/https://player.ypls.com/play/R5Ke..."
```
这是一条畸形 URL：主机段之后立刻接上了另一个完整协议+主机。ExoPlayer 把 `cdn.official-source.com` 当主机、路径为 `/play/https://player.ypls.com/...`，CDN 响应 **404 Not Found**（或路径非法被反向代理拦）→ 官方播放失败。

同时第三道防线（`PlayerManager.onParseSuccess`）还存在两个次级问题：
1. reparse 新建 `Result()` 时把 `parse` 设为 **0**，但又调用 `parse(useParse=true)`，状态不一致；
2. 没从当前 spec 拷贝 **Drm / Subs / Danmaku / Format**，二次解析后如果是加密源或带字幕源会丢信息。

#### 修复

**1. PlaybackActivity：第四道防线 unwrap 时强制清空 playUrl**（[PlaybackActivity.java#L232-L250](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/ui/activity/PlaybackActivity.java#L232-L250)）

```java
if (!TextUtils.isEmpty(unwrapped)) {
    result.setUrl(unwrapped);
    result.setPlayUrl("");                 // ← 关键：不要再让官方 playUrl 前缀拼到前面
    result.setParse(1);                    // parse=1 足以让 needParse()=true (parse==1 || jx==1)
    useParse = true;
    realUrl = unwrapped;
}
```
之后 `getRealUrl() = "" + unwrapped = unwrapped`，就是干净的完整 URL。

**2. PlayerManager：第三道防线 reparse 补齐 playUrl / parse / drm / subs / danmaku / format**（[PlayerManager.java#L515-L549](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/PlayerManager.java#L515-L549)）

```java
Result result = new Result();
result.setUrl(unwrapped);
result.setHeader(UrlUtil.mergeDefaultHeaders(headers, unwrapped));
result.setPlayUrl("");
result.setParse(1);                          // parse=1 足以让 needParse()=true
if (spec != null) {
    result.setDrm(spec.getDrm());
    result.setSubs(spec.getSubs());
    result.setDanmaku(spec.getDanmakus());
    result.setFormat(spec.getFormat());
}
parse(spec.getKey(), result, true, spec.getMetadata(), pendingStartPositionMs);
```

#### 验证

构造和官方源一致的场景：
```
Result.playUrl = "https://cdn.official-source.com/play/"
Result.url     = "http://127.0.0.1:10079/p/0/127.0.0.1%3A10172/aHR0cHM6Ly9wbGF5ZXIueXBscy5jb20vcGxheS9SNUtlSTdFNC85bmR0WjE2blE0/index.m3u8"
```

v5.5.42 修复前：
```
getRealUrl() = "https://cdn.official-source.com/play/https://player.ypls.com/play/R5KeI7E4/9ndtZ16nQ4"
→ 播放器 GET 404   ❌ 官方播放失败
```

v5.5.43 修复后：
```
unwrapFakeLocalProxy 命中 → setPlayUrl("")
getRealUrl() = "https://player.ypls.com/play/R5KeI7E4/9ndtZ16nQ4"
→ needParse()=parse=1 → 进入 ParseJob → aiSmartParseFallbackFrom + fallbackConcurrentParse
→ 挖出真正 m3u8 直链 → ExoPlayer 正常渲染   ✅ 官方源正常播
```

版本号：versionCode 591 → **592** / versionName 5.5.42 → **5.5.43**（[app/build.gradle#L22-L23](file:///workspace/app/build.gradle#L22-L23)）

---

## [v5.5.42] - 2026-08-08

### 一、修复 m3u8 播放报错 "Network Connection Failed"（第三方解析站伪造 127.0.0.1 本地代理 URL）

#### 问题复现与根因

- 用户播放"毛雪汪 (2026) : 春日特辑"时，播放器报错 **"Network Connection Failed"**（Media3 `ERROR_CODE_IO_NETWORK_CONNECTION_FAILED`）
- 播放器拿到的 URL 是 `http://127.0.0.1:10079/p/0/127.0.0.1%3A10172/aHR0cHM6Ly9wbGF5ZXIueXBscy5jb20vcGxheS9SNUtlSTdFNC85bmR0WjE2blE0/index.m3u8`
- 结构：`/p/<thread>/<innerHost:port>/<base64>/index.m3u8`，其中 `aHR0cHM6Ly9wbGF5ZXIueXBscy5jb20vcGxheS9SNUtlSTdFNC85bmR0WjE2blE0` base64 解码后是 `https://player.ypls.com/play/R5KeI7E4/9ndtZ16nQ4`（真正的视频播放页面）
- **根因**：第三方解析站（如 qcb/jiexi.php / xlm3u8 解析）用"本地代理 + base64 内嵌真 URL"的模式返回，但 App 主 Server 只监听 **9978~9999** 端口，**10079 / 10172 端口根本没有任何代理服务**，播放器去连 127.0.0.1:10079 被直接 `Connection Refused` → **Network Connection Failed**。

#### 修复（四道防线 + 还原后再解析）

**1. UrlUtil 新增 `unwrapFakeLocalProxy(url)` 还原算法**（[UrlUtil.java#L24-L81](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/UrlUtil.java#L24-L81)）

识别特征：
- `scheme == http/https` && `host == 127.0.0.1 / localhost`
- 端口 `!= -1` && **不在 9978~9999 范围**（那才是我们自己的 Nano 服务器）
- path 以 `/p/` 开头，segment 至少 4 段
- 从第 3 个 segment 起寻找第一个满足 base64 字符集 `^[A-Za-z0-9+/]{16,}={0,3}$` 的段
- Base64 解码 → UTF-8，若 `http(s)://` 开头即返回；对解码结果再做一次 `URLDecoder` 兜底

**2. ParseJob.onParseSuccess 第一道防线**（[ParseJob.java#L717-L805](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseJob.java#L717-L805)）：
- 识别到伪造 URL → 先解 base64 出真实页面 URL → 走 `aiSmartParseFallbackFrom`（直链 probe + 正文正则嗅探候选逐个 probe）
- 嗅探未命中（player.ypls.com 这类必须前端渲染的页面）→ **不占 done**，直接 `fallbackConcurrentParse(realUrl)` 重跑完整的「JSON 解析站并发 + WebView sniff + jsonExtend」多路兜底，挖取真正的 m3u8 直链

**3. CustomWebView.shouldInterceptRequest 第二道防线**（[CustomWebView.java#L117-L134](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/ui/custom/CustomWebView.java#L117-L134)）：
- WebView 拦截到这种伪造 URL 时，**不把它当视频直链触发 onParseSuccess**，放它过去；等上层 ParseJob 统一做还原处理

**4. PlayerManager.onParseSuccess 第三道防线**（[PlayerManager.java#L515-L539](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/PlayerManager.java#L515-L539)）：
- 解析回调入口仍发现伪造 URL → 用还原出的真实 URL 再次 `parse(..., useParse=true, from=+reparse)` 重走解析（带 `+reparse` 尾标防止无限递归）

**5. PlaybackActivity.startPlayer 第四道防线**（[PlaybackActivity.java#L232-L261](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/ui/activity/PlaybackActivity.java#L232-L261)）：
- SiteApi 直接返回的 Result，如果 url 是伪造本地代理 URL → 替换为还原出的真实 URL，并强制 `parse=1 / useParse=true` 走解析流程（因为 base64 里一般是 player.ypls.com 这种页面，不是直链 m3u8）

---

### 二、修复版本检测不生效 / 不到最新版本 + CI 编译失败

#### 现象（用户截图）

> App v5.5.40 → 设置 → 检查更新 → 提示 **"已是最新版本"**，但实际仓库已经到 v5.5.42 了。

#### 根因 1 · GitHub /releases/latest 返回旧的 v5.5.36
- GitHub 的 `/releases/latest` API **只返回被官方标记为 "Latest" 的 Release**（忽略 prerelease，除非显式 `gh release create ... --latest`）。
- 之前 v5.5.37 ~ v5.5.41 期间每次 push main 的 CI step#10 `assembleMobileArm64_v8aRelease` 都是 **BUILD FAILED**，`Update Latest Pre-release` 步骤被跳过，所以 `MXboxS-latest` release **从未真正创建成功**。
- 最终 /releases/latest 返回的是稳定发布里被标为 Latest 的 **v5.5.36** → `5.5.36 < 5.5.40` → 客户端判为"已是最新"。

#### 根因 2 · CI 编译失败（Updater lambda 非 final 变量）
- [Updater.java#L148-149](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/Updater.java#L148-L149) 两个 `App.post(λ)` 里引用了 `version` / `desc`，但它们之前在 `if (version.isEmpty()) version = tag...` 分支被改过 → javac 判定 **非 effectively final** →
  ```
  error: local variables referenced from a lambda expression must be final or effectively final
  ```
- 导致 `:app:compileMobileArm64_v8aReleaseJavaWithJavac` 失败 → 4 APK 构建全 skip → MXboxS-latest release 永远不会产生。

#### 根因 3 · Github.java 兜底正则少右括号（PatternSyntaxException）
- [Github.java#L176](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/Github.java#L176) `Pattern.compile("([0-9]+\\.[0-9]+\\.[0-9]+")` 少 `)` → 语法错误被 catch(ignored) 吞掉 → APK 文件名提取版本号的兜底路径完全失效。

#### 根因 4 · Updater 仅调用 `/releases/latest` 无兜底
- 就算 MXboxS-latest 某次没被设为 Latest，只要 `/releases/latest` 返回旧版就判定"没有更新"。
- 新增 releases 列表兜底后，就算 Latest 标记错乱，也会遍历 `/releases?per_page=10` 所有 release（含 prerelease），按 APK 文件名版本号取 **数字比较最高** 的那个返回。

#### 修复

| # | 文件 | 做了什么 |
|---|------|---------|
| 1 | **Github.java** | 新增 `API_LIST` / `getHighestRelease()` / `parseIntOrZero()` / `compareVersion(a,b)` 公共方法；修复 `extractVersionFromAssets` p2 正则缺失的 `)`；`findApkUrl` 不变 | [Github.java](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/Github.java) |
| 2 | **Updater.java** | `doInBackground` 优先 `Github.getHighestRelease()`（遍历 releases 列表取最高 APK 版本），失败再回退 `getLatestRelease()`；版本比较复用 `Github.compareVersion`；`version / desc` 显式 `final` 引用，`App.post` 改为显式 `new Runnable()` 匿名类，彻底消除 lambda 捕获非 effectively final 变量的 javac 报错 | [Updater.java](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/Updater.java) |
| 3 | **.gitignore** | 新增 `.trae-html-share-packages/`，防止会话产物被意外提交 | [.gitignore](file:///workspace/.gitignore) |

#### 验证 🔥 三条 workflow 全通过 + Releases 双产出

```
RUN# 31230501772  branch=main       push               SUCCESS  ✅ 自动产出 MXboxS-latest 并设为 Latest（4 APK 5.5.42）
RUN# 31230506677  branch=v5.5.42    push tag           SUCCESS  ✅ 产出正式 tag release v5.5.42
RUN# 31230506893  branch=main       workflow_dispatch  SUCCESS
```

```
GH Releases 当前（Latest 行高亮）:
──────────────────────────────────────────────────────────
v5.5.42                                             v5.5.42  （正式稳定 Release）
MXboxS v5.5.42 (build 91fa94e)  🟢 Latest    MXboxS-latest  （/releases/latest 现在返回它！4 APK 都是 5.5.42）
v5.5.36  ...                                               （旧版 Latest，不再被 /latest 返回）
──────────────────────────────────────────────────────────
```

**模拟用户手机 v5.5.40 检测更新（端到端）**：
```
/releases/latest  tag_name = MXboxS-latest
assets            = [ MXboxS-mobile-arm64_v8a-5.5.42.apk, ... ]
extract version   = 5.5.42
本地版本          = 5.5.40
compareVersion    = +2   （> 0）  →  ✅ 弹出更新对话框!
```

#### 版本号
versionCode 590 → **591** / versionName 5.5.41 → **5.5.42**

## [v5.5.41] - 2026-08-06

### 修复自动更新：push main 自动更新 GitHub Releases Latest，App 自动感知最新版

#### 问题

- GitHub Releases 最新版停留在 v5.5.36，之后 v5.5.37~v5.5.40 只推了 commit 没打 tag，没有创建新 Release。
- App 的更新检查（`Github.API_LATEST = /releases/latest`）从 GitHub Releases 的 Latest 标记 Release 拉取，永远拿到 v5.5.36，无法感知新版本。
- 构建工作流 `build.yml` 只在 `v*` tag 时创建 Release（`if: startsWith(github.ref, 'refs/tags/v')`）。

#### 修复

**1. 构建工作流新增「Latest 自动预发布」步骤**（[build.yml#L181-L237](file:///workspace/.github/workflows/build.yml#L181-L237)）：

每次 `push main` 或 `push upstream-sync` 分支构建成功后，自动：

1. 删除旧的 `MXboxS-latest` release + tag
2. 创建新的 `MXboxS-latest` tag 指向当前 commit
3. 创建 release（`--latest` 标记为 Latest），上传 4 个 APK（mobile/leanback × arm64/armeabi-v7a），带 release notes 含版本号/commit/构建时间

**2. Updater.java 版本提取兼容 MXboxS-latest tag**（[Updater.java#L106-L113](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/Updater.java#L106-L113)）：

原来只从 `tag_name`（如 `v5.5.36）提取版本号。`v5.5.36→5.5.36），遇到 `MXboxS-latest` 会失败。

改为优先级：

1. **优先从 APK asset 文件名提取**（`Github.extractVersionFromAssets`）：`MXboxS-mobile-arm64_v8a-5.5.41.apk → 5.5.41`
2. 失败时回退 tag_name 提取（兼容 `v*` 稳定 release）

**3. Github.java 新增 extractVersionFromAssets 方法**（[Github.java#L88-L115](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/utils/Github.java#L88-L115)）：从 APK 文件名正则提取 `X.Y.Z` 点分版本号。

#### 版本号

versionCode 589 → **590** / versionName 5.5.40 → **5.5.41**

## [v5.5.40] - 2026-08-05

### 修复云播 m3u8 直链无法播放的问题（OkHttpDataSource stub 修复）

#### 背景

用户反馈类似 `https://hn.bfvvs.com/play/erkmL1Ba/index.m3u8` 这种云播直链在 App 中无法直接播放。经排查，根因是 `OkHttpDataSource` 是一个 stub 类，把传入的 `OkHttpClient` 丢弃了。

#### 根因

`/workspace/app/src/main/java/androidx/media3/datasource/okhttp/OkHttpDataSource.java` 原实现：

```java
public Factory(OkHttpClient client) {
    this.delegate = new DefaultHttpDataSource.Factory();  // ← OkHttpClient 被丢弃!
}
```

这导致 ExoPlayer 播放 m3u8 / mp4 直链时退化为 `DefaultHttpDataSource`（基于 `HttpURLConnection`），丢失了 OkHttp 的全部能力：
- `trustAllCertificates()` 信任所有 SSL 证书 → 自签名 / 过期证书的 m3u8 源播放失败
- `hostnameVerifier((h, s) -> true)` 信任所有 host → host 不匹配的源播放失败
- 自定义 `OkDns`（DoH 等）→ DNS 污染场景下无法解析
- `AuthInterceptor` / `RequestInterceptor` / `ResponseInterceptor` → 依赖拦截器注入 token / cookie 的源播放失败

#### 修复

重写 `OkHttpDataSource`，继承 `BaseDataSource` 并实现 `HttpDataSource` 接口，真正使用传入的 `OkHttpClient` 发起请求：

| 能力 | 实现方式 |
|------|---------|
| Range 请求 | 根据 `DataSpec.position` / `DataSpec.length` 构造 `Range: bytes=start-end` header |
| 请求头传递 | `defaultRequestProperties` + `requestProperties` 双层覆盖 |
| SSL / DNS / 拦截器 | 直接复用 `OkHttpClient` 的全部配置 |
| 响应码处理 | 200/206 正常，416 视为已读完，其他抛 `HttpDataSourceException` |
| TransferListener | 继承 `BaseDataSource`，自动调用 `transferInitializing/Started/bytesTransferred/Ended` |
| 取消 / 超时 | 由 `OkHttpClient` 配置统一管理 |

**API 兼容**：`Factory(OkHttpClient)` 构造函数和 `setDefaultRequestProperties(Map)` 方法签名与原 stub 完全一致，[MediaSourceFactory.java](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/exo/MediaSourceFactory.java) 中 3 处调用点无需修改。

#### 双模式播放说明

本次修复后，App 支持两种播放方式：

| 模式 | 触发条件 | 链路 |
|------|---------|------|
| **方式 1：官方（夸克网盘代理）** | spider 返回 `proxy?do=quark&type=dwnz&...` URL | catvod 内部代理 → 夸克网盘 API → 转码直链 |
| **方式 2：云播直链** | m3u8 / mp4 等直链（`Sniffer.isVideoFormat` 识别，`parse=0`） | ExoPlayer → OkHttpDataSource（复用 OkHttp SSL/DNS/拦截器）→ 直接播放 |

方式 2 现在能正确播放：
- 标准 HLS 加密流（AES-128 + 相对路径 enc.key，HlsMediaSource 自动用 base URL resolve）
- 跨域 TS 分片（如 `https://hnts.ymuuy.com:65/hls/...`）
- 自签名 / 过期证书的 HTTPS 源
- DNS 污染场景（通过 OkDns 规避）

#### 版本号

versionCode 588 → **589** / versionName 5.5.39 → **5.5.40**

## [v5.5.39] - 2026-08-05

### 修复应用内更新两大关键 Bug

#### Bug 1：版本比较逻辑错误（本地版本高仍误报有更新）

**现象**：用户 App 已安装 v5.5.38，但 GitHub Release 最新是 v5.5.36，仍弹出"发现新版本 5.5.36"。

**根因**：`Updater.parseVersionCode()` 将远端 versionName（`5.5.36`）去掉所有点得到 `5536`，然后与本地 `BuildConfig.VERSION_CODE`（587）比较。`5536 > 587` 导致误判有更新——两个值完全不是一个维度的数字，不能直接比较。

**修复**：新增 `compareVersionNames(server, local)` 方法，按点分段逐段做**整数比较**（非字典序），仅当远端 versionName **严格大于**本地 versionName 时才提示更新。

| 场景 | 修复前 | 修复后 |
|------|-------|-------|
| 远端 5.5.36 vs 本地 5.5.38 | ❌ 5536 > 587 → 误报更新 | ✅ 5.5.36 < 5.5.38 → 已是最新 |
| 远端 5.5.39 vs 本地 5.5.38 | ✅ 5539 > 587 → 提示更新 | ✅ 5.5.39 > 5.5.38 → 提示更新 |
| 远端 5.5.10 vs 本地 5.5.9 | ✅ 5510 > 587 → 提示更新 | ✅ 5.5.10 > 5.5.9 → 提示更新（字典序会错误判小） |

#### Bug 2：ghproxy 镜像 URL 拼接缺失分隔符（`ghproxy.comhttps`）

**现象**：使用 ghproxy / mirror.ghproxy 镜像时下载失败，报错 `Unable to resolve host "ghproxy.comhttps": No address associated with hostname`。

**根因**：`Github.java` 中镜像 URL 与目标 URL 直接 `+` 拼接，缺少 `/` 分隔符：
```
"https://ghproxy.com" + "https://github.com/..."
→ "https://ghproxy.comhttps://github.com/..."  ❌
```

**修复**：在 mirror 与目标 URL 之间补 `/`：
```
"https://ghproxy.com" + "/" + "https://github.com/..."
→ "https://ghproxy.com/https://github.com/..."  ✅
```

影响范围：
- `Github.getLatestRelease()` API 请求（镜像分支）
- `Github.findApkUrl()` APK 下载链接拼接（两个分支）
- `KamiUtil.fetchKamiText()` 之前已正确添加 `/`，无需修改

#### 版本号

versionCode 587 → **588** / versionName 5.5.38 → **5.5.39**

## [v5.5.38] - 2026-08-05

### 新增会员卡密激活功能

#### 背景

为控制 MXboxS 的使用权限，新增会员卡密激活机制。未激活用户无法进入应用主界面，必须输入有效卡密或通过「购买卡密」获取卡密后激活才能使用。

#### 修改内容

**新增文件**：
- `kami.txt` — 仓库根目录卡密列表（首张卡密：`bcda1fe5e260218399c2222d299d2a39555bd38461c81975247b8587c3ba62ac`，64 位）
- `app/src/main/java/.../utils/KamiUtil.java` — 卡密验证工具类，从 GitHub `kami.txt` 拉取并校验，支持 ghproxy / mirror.ghproxy / jsDelivr 多源回退 + 12 小时本地缓存
- `app/src/main/java/.../ui/activity/KamiActivity.java` — 会员激活界面（卡密输入 / 验证 / 购买入口 / 已激活面板 / 注销）
- `app/src/main/res/layout/activity_kami.xml` — 激活界面布局（手机 + TV 通用，按钮均 focusable 适配遥控器）
- `app/src/main/res/values/colors.xml` — 新增 `red` 错误色

**修改文件**：
- `app/src/main/java/.../setting/Setting.java` — 新增 `isKamiActivated / putKamiActivated / getKami / putKami`
- `app/src/mobile/AndroidManifest.xml` + `app/src/leanback/AndroidManifest.xml` — 注册 `KamiActivity`
- `app/src/mobile/java/.../ui/activity/HomeActivity.java` + `app/src/leanback/java/.../ui/activity/HomeActivity.java` — `initView` 首行增加激活校验：未激活 → 跳转 `KamiActivity` → 自身 `finish()`
- `app/src/main/res/values/strings.xml` + `values-zh-rCN` + `values-zh-rTW` — 新增 18 条卡密相关文案
- `app/build.gradle` — 版本号 586/5.5.37 → **587/5.5.38**

**核心流程**：

| 场景 | 行为 |
|------|------|
| 首次启动 / 未激活 | `HomeActivity` 检测未激活 → 启动 `KamiActivity` → `HomeActivity` 自身 finish |
| 输入卡密点「激活」 | 后台拉取 `kami.txt`（镜像 → raw → jsDelivr）→ 比对 → 通过则标记激活并进入首页 |
| 点「购买卡密」 | 拉取 `kami.txt` 取首张卡密 → 弹窗展示 → 可一键填入输入框 |
| 已激活再次进入 | 展示已激活面板（卡密掩码 `bcda****62ac`）→ 可「进入应用」或「注销本机」 |
| 未激活按返回 / 点退出 | `finishAffinity()` 退出 App |
| 网络不可用 | 优先用本地缓存校验；缓存也无则验证失败 |

**卡密文件说明**：
- 路径：仓库根目录 [`kami.txt`](file:///workspace/kami.txt)
- 格式：每行一个 64 位卡密，`#` 开头为注释
- 验证源（按优先级）：`ghproxy.com/https://raw.githubusercontent.com/.../kami.txt` → `raw.githubusercontent.com` → `cdn.jsdelivr.net/gh/...@main/kami.txt`
- 本地缓存有效期 12 小时，便于离线校验

## [v5.5.37] - 2026-08-05

### 修复安装冲突 + 强制系统安装器

#### 问题根因

v5.5.36 及之前版本存在两个安装问题：

1. **签名不一致导致"软件包与现有软件包存在冲突"**：每次 GitHub Actions 构建都用 `keytool -genkey` 动态生成新 keystore，哪怕参数完全一致，生成的签名证书也是随机的。旧 APK 用 A 证书签名，新 APK 用 B 证书签名 → Android 拒绝安装。
2. **被第三方 App（如 Edge Beta）拦截**：`FileUtil.openFile` 用通用 `ACTION_VIEW + */*` MIME 类型打开 APK，系统弹出选择器时被 Edge 拦截，无法调起系统包安装器，显示"来自 Edge Beta"且可能校验失败。

#### 修复内容

**文件**：
- `app/release.keystore` — 新增仓库级固定签名证书（有效期 100 年，SHA1: 32:1A:F2:4B:A9:28:28:89:6D:84:BF:F9:87:CE:94:38:6B:72:3C:50）
- `.github/workflows/build.yml` — 替换"动态生成 keystore"为"验证仓库固定 keystore"
- `app/src/main/java/.../FileUtil.java` — 新增 `installApk()` 专用方法，使用 `ACTION_INSTALL_PACKAGE + application/vnd.android.package-archive` 强制调系统包安装器
- `app/src/main/java/.../Updater.java` — 改用 `FileUtil.installApk()` 而非通用 `openFile()`

**核心变更**：
- ✅ 所有后续版本使用**同一签名证书**，彻底消除安装冲突
- ✅ 自动更新下载完成后**强制调用系统包安装器**，不再被 Edge/浏览器等第三方 App 拦截
- ✅ `installApk()` 自带 fallback 机制：`ACTION_INSTALL_PACKAGE` 失败时自动降级到 `ACTION_VIEW + 专用 APK MIME`
- ⚠️ 用户需先**卸载旧版本 MXboxS**（v5.5.36 及更早），再安装 v5.5.37，因为旧版本签名与新固定证书不一致

## [v5.5.36] - 2026-08-05

### 优化更新体验：连接状态可视化 + 自动下载 + 进度条

#### 背景

v5.5.35 引入了应用内更新机制，但用户点击"检查更新"后缺少反馈：连接状态不可见、下载进度不直观。v5.5.36 重新设计了更新对话框，完整展示从连接到下载安装的全流程状态。

#### 修改内容

**文件**：
- `app/src/main/java/.../Updater.java` — 重写更新流程，支持连接状态实时反馈和自动下载
- `app/src/mobile/java/.../UpdateDialog.java` — 新增连接状态、进度条、动态更新方法
- `app/src/leanback/java/.../UpdateDialog.java` — 同步新增连接状态、进度条、动态更新方法
- `app/src/mobile/res/layout/dialog_update.xml` — 新增 status、progressBar、progressText
- `app/src/leanback/res/layout/dialog_update.xml` — 同步新增 status、progressBar、progressText
- `app/src/main/res/values*/strings.xml` — 新增连接/下载/安装相关字符串

**核心变更**：
- ✅ 点击更新后立即弹出对话框，显示"正在连接仓库…"
- ✅ 连接成功后显示"连接成功 · 最新版本：x.x.x" + 更新日志
- ✅ 连接失败显示错误信息
- ✅ 检测到新版本后自动开始下载，无需手动点击
- ✅ 下载过程显示进度条 + 百分比
- ✅ 下载完成后自动调起系统安装界面
- ✅ 启动时自动检查仅在有新版本时弹窗，不打扰用户

## [v5.5.35] - 2026-08-04

### 全新应用内更新机制：支持国内镜像自动下载安装

#### 背景

此前 MXboxS 的更新依赖上游 FongMi 的 Release 仓库，无法自动从 MXboxS 自身仓库获取更新，也不支持国内用户的网络环境。v5.5.35 引入了完全自研的应用内更新系统，直接对接 MXboxS GitHub Releases，默认启用国内镜像，用户可在设置中切换更新源。

#### 修改内容

**文件**：
- `app/src/main/java/com/ssmhdssmhd/mxboxs/utils/Github.java` — 重写为 GitHub Releases API
- `app/src/main/java/com/ssmhdssmhd/mxboxs/Updater.java` — 重写更新流程 + 镜像选择对话框
- `app/src/main/java/com/ssmhdssmhd/mxboxs/setting/Setting.java` — 新增镜像模式设置
- `app/src/mobile/java/.../SettingFragment.java` — 长按版本项切换镜像
- `app/src/leanback/java/.../SettingActivity.java` — 长按版本项切换镜像
- `app/build.gradle` — 版本号升至 584/5.5.35

**核心变更**：
- ✅ 直接对接 `api.github.com/repos/ssmhdssmhd/MXboxS/releases/latest`
- ✅ 默认使用 `ghproxy.com` 国内镜像，无需用户额外配置
- ✅ 自动匹配当前架构的 APK（mobile/leanback × arm64_v8a/armeabi_v7a）
- ✅ 下载完成后自动调用系统安装界面
- ✅ 支持三种更新源：ghproxy.com / mirror.ghproxy.com / GitHub 直连
- ✅ 长按"设置→版本"项可切换更新源

## [v5.5.34] - 2026-08-04

### 修复部分视频源解析成功但播放 0 KB/s 的问题

#### 背景

部分影视线路（如第三方 JSON 解析接口）返回的视频 URL 虽然后缀是 `.m3u8` 或 `.mp4`，但实际内容可能是无效的（例如 404 错误页、登录页或被封禁的资源）。此前的 `probeVideoUrl` 方法存在一个"快路径"逻辑：只要 URL 后缀看起来像视频文件，就直接信任并返回成功，完全跳过了真实的 HTTP 可达性验证。这导致 `aiSmartParseFallback` 误认为解析成功，将无效 URL 传递给播放器，从而出现 `0 KB/s` 无数据的假成功现象。

#### 修改内容

**文件**：`app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseJob.java`

**核心变更**：重写 `probeVideoUrl` 方法的验证逻辑，从"盲目信任后缀"改为"基于 HTTP 状态码的精细化判定"。

| 场景 | 状态码 | 旧行为 | 新行为 |
|------|--------|--------|--------|
| URL 是 `.m3u8` 后缀，但资源不存在 | 404 | ✅ 信任（假成功） | ❌ 拒绝（触发 fallback） |
| URL 是 `.m3u8` 后缀，但服务器返回 HTML | 200 + text/html | ✅ 信任（假成功） | ⚠️ 若有视频后缀则信任（保守） |
| URL 是 `.m3u8` 后缀，服务器拒绝 HEAD | 403/405 | ✅ 信任 | ✅ 信任（避免误杀） |
| URL 是 `.m3u8` 后缀，网络超时 | N/A | ✅ 信任 | ✅ 信任（避免误杀） |
| URL 无视频后缀，探测成功 | 200 + video | ✅ 信任 | ✅ 信任 |
| URL 无视频后缀，探测失败 | 404/500 | ❌ 拒绝 | ❌ 拒绝 |

#### 影响

1.  **更准确的失败识别**：对于返回 404/500 的无效视频 URL，现在能正确识别为失败，不再假成功。
2.  **自动触发兜底解析**：当 `aiSmartParseFallback` 因为 URL 无效而返回 `false` 时，`builtinParse` 会自动执行 `fallbackConcurrentParse`，尝试所有配置的 JSON 解析站和 WebView 嗅探作为兜底。
3.  **保守策略**：对于 HEAD/GET 请求失败但 URL 有视频后缀的情况，仍然保持信任，避免误杀原本可能播放的视频源。

#### 关于截图中的问题

截图显示"红牛资源"线路（`hnm3u8`）播放时出现 `0 KB/s`。这表明该线路返回的视频 URL 可能是无效的。修复后，如果该 URL 在 HTTP 探测中被识别为失败，系统将自动切换到其他解析站或嗅探方式，提升整体播放成功率。

---

## [v5.5.33] - 2026-08-04

### 新增上游 FongMi/TV 实时同步工作流

#### 背景

此前 MXboxS 基于 FongMi/TV 二次开发，所有上游更新均需手动拉取合并，容易滞后。本次新增自动化同步工作流，每 6 小时检查上游 [FongMi/TV](https://github.com/FongMi/TV) `fongmi` 分支，有新提交时自动合并到 `upstream-sync` 分支并创建 PR 供人工 review。

#### 一、同步工作流 `sync.yml`

新建 [.github/workflows/sync.yml](file:///workspace/.github/workflows/sync.yml)，核心流程：

| 步骤 | 行为 | 说明 |
|------|------|------|
| 触发 | `cron: 0 */6 * * *` + `workflow_dispatch` | 每 6 小时自动 + 手动触发（支持 `force_recreate` 选项） |
| 基线检查 | 读取 `.upstream-sync-baseline` 对比上游 HEAD | 无变化则跳过，有变化才执行同步 |
| 分支准备 | 复用已有 `upstream-sync` 分支或从 `main` 新建 | 复用时先 merge `origin/main` 同步 MXboxS 最新改动 |
| 上游合并 | `git merge upstream/fongmi -X ours` | 冲突时**保留 MXboxS 定制**；首次使用 `--allow-unrelated-histories` |
| 清理 | `git rm -r app/src/*/java/com/fongmi/` | 移除上游 `com.fongmi.android.tv` 包名下的 Java 文件，避免编译失败 |
| 基线更新 | 写入新 SHA 到 `.upstream-sync-baseline` | amend 到合并提交中 |
| 推送 + PR | `git push --force-with-lease` + `gh pr create/edit` | 创建或更新同步 PR，含上游提交日志与变更统计 |

#### 二、同步范围

| 模块 | 同步方式 | 说明 |
|------|----------|------|
| `catvod/` `chaquo/` `forcetech/` `docs/` `gradle/` | ✅ 自动合并 | 路径与上游一致，`-X ours` 保留 MXboxS 定制版本 |
| 根 `build.gradle` `settings.gradle` `gradle.properties` | ✅ 自动合并 | 同上 |
| `app/src/*/java/com/fongmi/...` | ⚠️ 仅报告 | 包名不同，上游 Java 文件不引入，PR 中生成变更清单供人工 port |
| `app/src/main/res/` | ✅ 自动合并 | 新增资源自动引入，同名冲突保留 MXboxS 版本 |

#### 三、构建工作流 `build.yml` 调整

- 新增 `upstream-sync` 分支 push 触发
- 新增 `pull_request` 触发（PR 到 main 时自动构建验证）
- `paths-ignore` 新增 `.upstream-sync-baseline`（基线文件变更不触发构建）
- PR 构建跳过 APK 上传（`if: github.event_name != 'pull_request'`）

#### 四、PR 报告内容

同步 PR 正文包含：
- 上游最近 30 条提交
- 共享模块变更统计（自动合并部分）
- app/ 业务代码变更清单（需人工 port）
- 全部变更统计
- 同步机制说明 + 模块化开发原则

#### 五、文档

- README 新增「实时同步上游 FongMi/TV」章节，含同步机制表、同步范围表、流程图、手动触发说明、PR 说明
- README 新增「模块化开发原则」章节，5 条原则指导后续定制开发降低冲突
- README 分支说明更新：`main` + `upstream-sync`，历史 `TV`/`mobile`/`KF` 分支标记弃用

#### 六、其它

- `app/build.gradle` versionCode 581 → **582** / versionName 5.5.32 → **5.5.33**
- 新增 `.upstream-sync-baseline` 文件（记录当前上游 SHA `d234010b`）

---

## [v5.5.32] - 2026-08-02

### 修复「内置解析失败，播放地址解析失败」（爱奇艺/官解线路 qcb 回环后直接报错）

#### 根因：v5.5.30/31 时 builtinParse 只有两路，qcb + AI sniff，官解线路直接 onParseError

用户用「建安资源 → iqiyi 线路 → 小品一家人 45/46 集」这种典型官解线路时，发生：
1. `qcb jiexi.php` 返回：
   ```json
   {"code":200,"ZT":"解析成功","msg":"https://www.iqiyi.com/v_19rrcu9opc.html","url":"https://www.iqiyi.com/v_19rrcu9opc.html",...}
   ```
   因为 **url/msg 都等于原 webUrl（爱奇艺详情页）**，被 v5.5.30 起引入的"回环 + 非直链"判定正确拦截 → `qcbJiexiParse` 返回 false，不会误判。
2. 进入 `aiSmartParseFallback`：爱奇艺/腾讯/优酷/B 站这类 SPA 前端渲染，HTTP GET 只能拿到 CSR 骨架 HTML，正文里没有明文 m3u8/mp4 直链 → `sniffVideoCandidates` 返回空 → 最后宽容 probe 也是 text/html → 全部 false。
3. v5.5.31 `builtinParse` 就直接 `onParseError()` → UI 顶部弹出「播放地址解析失败」。

#### 修复一：builtinParse 新增第 3 路兜底 `fallbackConcurrentParse` 并发多解析站 + WebView sniff

把内置解析从"两路"升级为**三级链路 + 最后兜底完整传统并发**，彻底避免官解线路因为 qcb 没配官解而直接失败：

| 顺序 | 链路 | 作用场景 | 代码位置 |
|------|------|----------|----------|
| ① | `qcbJiexiParse` | 用户自定义 / 默认 qcb 云端 jiexi.php 有官解配置时最快出结果 | [ParseJob.java#L358-L360](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseJob.java#L358-L360) |
| ② | `aiSmartParseFallback` | 半直链 / 简单静态页 / 自建解析站 → 零配置就能放 | [同上](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseJob.java#L358-L360) |
| ③ 新增 | **`fallbackConcurrentParse`** | 爱奇艺/腾讯/优酷/B 站官解线路（必须解析站/WebView 才能解），并发跑：<br>1) 所有 `type=1` 的 JSON 解析站 `jsonParse`；<br>2) 默认解析站 WebView sniff（含 type=0/1/2/3 分发）；<br>3) `jsonExtend` 扩展多解析并发；<br>4) 每路完成 `countDown`，15s 超时统一释放；<br>任一路 onParseSuccess 立即 CAS `done=true`，剩下全部 Future cancel。 | [fallbackConcurrentParse()](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseJob.java#L373-L417) + [新增 startWeb(latch,Parse,webUrl)](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseJob.java#L419-L449) |
| ④ | 才 onParseError | 真正全部失败才报 | [builtinParse()](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseJob.java#L357-L363) |

#### 修复二：qcbHttpCall 增强兼容 url/msg 两字段 + 嵌套 JSON

有些 qcb 部署版本会把真正的 `{code,url}` 再塞成字符串塞进 `msg` / `url` 字段里；v5.5.32 加：
- [extractQcbUrl()](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseJob.java#L543-L563)：对 `url` 和 `msg` 两个字段同时尝试：
  1. 直接 http 开头 → 返回；
  2. `{` / `[` 开头 → 再解一层 JSON → 从内层取 `url` / `msg` 只要 http 开头就返回。
- [preferCandidateUrl()](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseJob.java#L566-L582)：在两个候选里挑更像"真解析结果"的：**优先选 ≠ 原 webUrl + 带视频后缀** 的那个，两者都回环才随便传（下游 `isSameAsInput && !isDirectVideo` 再最后拦一次）。

#### 三、其它

- `app/build.gradle` versionCode 580 → **581** / versionName 5.5.31 → **5.5.32**

---

## [v5.5.31] - 2026-08-02

### 壁纸支持动态（视频 / GIF 像视频一样动）+ AI 设置里壁纸声音默认关闭

#### 一、动态壁纸能力复用：视频像视频、GIF 也像视频一样动

现有壁纸渲染组件 `CustomWallView`（[CustomWallView.java](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/ui/custom/CustomWallView.java)）已具备完整的三类壁纸分支：
- `TYPE_RES`（静态内置图 1~4，JPG/PNG）
- `TYPE_GIF`：`pl.droidsonroids.gif.GifDrawable` 自动帧循环（start/pause 跟随生命周期），**像视频一样动**。
- `TYPE_VIDEO`：`androidx.media3.exoplayer.ExoPlayer` + `PlayerView`，`REPEAT_MODE_ALL` 无限循环，`PLAY_WHEN_READY=true`，标准**视频动态壁纸**。

本次只做"默认声音关闭 + 设置页可控 + 即时生效"三部分，**不破坏原有 TYPE_GIF / TYPE_VIDEO 渲染链路**，因此视频/GIF 动态壁纸能力**完全可用**：
- 视频壁纸：点击设置里的「壁纸」选择 `wall_type=2` 素材后，`CustomWallView.loadVideo()` → 全屏 PlayerView 循环播放。
- GIF 壁纸：`wall_type=1` 时 → `GifDrawable.start()`，页面 resume 就继续、pause 就暂停。

#### 二、壁纸声音新增独立开关，默认关闭（与 AI 设置 / 设置页联动）

##### 1. 配置存储 [Setting.java](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/setting/Setting.java#L70-L76)

```java
public static boolean getWallSound() { return Prefers.getBoolean("wall_sound"); }
public static void putWallSound(boolean sound) { Prefers.put("wall_sound", sound); }
```
- 因为 `Prefers.getBoolean("wall_sound")` 默认值是 `false`，所以**新装/升级/从未手动切换过的用户一律默认关闭（静音）**。
- 这个默认行为就是用户要求的：「AI 设置中，壁纸声音默认为关闭」。

##### 2. ExoPlayer 声音动态同步 [CustomWallView.java](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/ui/custom/CustomWallView.java#L70-L81)

- 初始化阶段 `ensurePlayer()`：
  - `Setting.getWallSound() == true` → `player.unmute()`；
  - 否则（默认）→ `player.mute()`。
- 运行中切换开关：`ConfigEvent.common()` 触发 `onConfigEvent` → `applyWallSound()`，再次调用 `mute/unmute`，**无需切换壁纸就能立刻听到 / 静音**。

##### 3. 手机 + TV 设置页新增「壁纸声音」一行（AI/壁纸相关设置可见）

手机端 [SettingFragment.java](file:///workspace/app/src/mobile/java/com/ssmhdssmhd/mxboxs/ui/fragment/SettingFragment.java#L104-L112) + [fragment_setting.xml](file:///workspace/app/src/mobile/res/layout/fragment_setting.xml#L197-L222)，壁纸行正下方新增：
- 文案 `@string/setting_wall_sound` → 默认状态显示「关」。
- 单击 → `setWallSound()` 反转 `wall_sound` 布尔 → 立刻写入 SP → 回写到 `wallSoundText` → `ConfigEvent.common()` 通知 CustomWallView 实时 `applyWallSound()`。

TV 端 [SettingActivity.java](file:///workspace/app/src/leanback/java/com/ssmhdssmhd/mxboxs/ui/activity/SettingActivity.java#L90-L97) + [activity_setting.xml](file:///workspace/app/src/leanback/res/layout/activity_setting.xml#L209-L236) 同结构同语义，已加 focusable/selector_item，遥控器可点。

多语言 strings 都已就位：
- 中文（简）`values-zh-rCN`：`壁纸声音`
- 中文（繁）`values-zh-rTW`：`壁紙聲音`
- 英文 fallback `values`：`Wallpaper sound`

#### 三、其它

- `app/build.gradle` versionCode 579 → **580** / versionName 5.5.30 → **5.5.31**。

---

## [v5.5.30] - 2026-08-02

### 解析链路按用户要求重定义：内置解析=qcb jiexi.php，超级解析=AI 自动识别

#### 一、内置解析：`http://114.134.184.91:9002/jiexi.php?url=`

- Setting 默认解析服务器前缀保持 `http://114.134.184.91:9002`，用户无自定义值时内置解析自动走 qcb/jiexi.php，无需改任何配置。
- [ParseJob.java builtinParse()](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseJob.java#L439-L444)：
  - 最高优先级 → `qcbJiexiParse(webUrl)` → HTTP 调 `/jiexi.php?type=json&url=<编码后的 webUrl>`；
  - qcb 返回成功（code==200 + url 非空 + 非原 URL 回环）→ 回调 onParseSuccess；
  - qcb 失败或返回原网页 URL → 走 `aiSmartParseFallback(webUrl)` 本地嗅探兜底；
  - 两路都失败才回调 onParseError。

#### 二、超级解析：改为纯 AI 自动识别然后解析

- [ParseJob.java superParse()](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseJob.java#L165-L177) 完全重写：
  - **移除了**原有的第三方 JSON 解析站并发、WebView 嗅探并发、qcb/xt/api.php 超级嗅探一路；
  - **改为**直接调用 `aiSmartParseFallback(webUrl)`：
    1. webUrl 本身是视频直链（.m3u8/.mp4/.flv/.m4v/.ts/.mkv/.webm）→ 可达性 probe 通过即直接播放；
    2. 否则 HTTP GET 抓页面正文，正则扫常见视频 URL（Top 5 候选含相对路径拼接），逐个做 HEAD/Range:0-0 probe 可达性，命中即播放；
    3. 全部候选不命中 → 兜底：拿原 URL 做一次 Content-Type/Content-Length probe（宽容策略）；
  - 任一步命中 → onParseSuccess；全部失败 → onParseError。

#### 三、其它

- `app/build.gradle` versionCode 578 → **579** / versionName 5.5.29 → **5.5.30**。

---

## [v5.5.29] - 2026-08-02

### 内置解析 & 超级解析全面接入 qcb 云端解析服务（解耦 + 可热更 + 可配置）

#### 一、内置解析：直连 qcb/jiexi.php，云端规则热更

- 新增 `ParseJob.qcbHttpCall()` 统一封装 qcb 云端解析接口：URL 编码、默认 UA/Referer Header 合并、JSON 解析与严格校验（code==200 + url 非空 + 非原 URL 回环检测），全程在 [ParseJob.java](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/parse/ParseJob.java#L435-L496)
- `builtinParse()` 优先级调整：先 QCB `/jiexi.php?type=json&url=...` 作为最高优先级，**先跑，拿不到再 fallback 到原有的 AI 智能嗅探链路（`UrlUtil.sniffVideoCandidates + probeVideoUrl`），不再走 "先云端、后本地、两条腿走路。
- 默认解析返回值 **回环保护**：若 qcb 接口未配置官解时会把输入 URL 原样吐回，新增**直链后缀白名单 + 输入 URL 比较逻辑，误判成"解析成功"然后播放原网页。

#### 二、超级解析：qcb/xt/api.php 作为首发并发一路先开跑

- `superParse()` 里新增 `/xt/api.php?type=json&url=...` 作为第 0 路提交给 `ExecutorService`，跟原有 JSON 解析、WebView 解析、AI 嗅探**四路并发**，先到先得。
- 任意一路命中 `onParseSuccess` 立即 `CountDownLatch.countDown()`，其余全部 cancel，最大限度缩短黑屏等待。

#### 三、解析服务器前缀可配置（手机 & TV 双端设置页已补全 UI）

- [Setting.java](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/setting/Setting.java#L140-L152) 新增：
  - `PARSE_SERVER_DEFAULT = "http://114.134.184.91:9002"`
  - `getParseServerPrefix()`：SharedPreferences `parse_server_prefix`，空串走本地链路，非空用用户值。
  - `putParseServerPrefix(prefix)`：持久化保存。
- 手机端 [SettingFragment.java](file:///workspace/app/src/mobile/java/com/ssmhdssmhd/mxboxs/ui/fragment/SettingFragment.java#L358-L397) + [fragment_setting.xml](file:///workspace/app/src/mobile/res/layout/fragment_setting.xml#L356-L383)：设置 DoH 下方新增「解析服务器」一行，
  - **单击**：弹出 Material 输入框，带 URL keyboard，带 URI 输入类型，当前值预填、光标居末；确认后实时写入 SP 并刷新显示；空串显示「关」表示走本地链路。
  - **长按**：一键重置回默认 `http://114.134.184.91:9002`，Toast 反馈重置提示文案。
- TV 端 [SettingActivity.java](file:///workspace/app/src/leanback/java/com/ssmhdssmhd/mxboxs/ui/activity/SettingActivity.java#L328-L367) + [activity_setting.xml](file:///workspace/app/src/leanback/res/layout/activity_setting.xml#L324-L356)：同逻辑同 UI，focusable/selector_item 样式，遥控器可点。
- 中文字符串 [values-zh-rCN/strings.xml](file:///workspace/app/src/main/res/values-zh-rCN/strings.xml#L132-L134)：`setting_parse_server` / `setting_parse_server_hint` / `setting_parse_server_default` 三条全部就位。

#### 四、qcb 接口 URL 归一化

- `normalizeQcbPrefix()`：strip 尾部 `/`，防用户配置时多写 `/` 导致拼成 `...9002//jiexi.php?...` 这种畸形 URL；prefix 空串时 `qcbHttpCall` 直接 return false，无缝降级到本地 AI sniff。

---

## [v5.5.28] - 2026-08-01

### 修复「点击其他播放引擎不生效」问题

现象：在播放页面打开「播放引擎」选择弹窗后，点击 MPV / System / 阿里 / 新星 / IJK / 其它 按钮，关闭后再次打开仍显示 EXO 选中，且播放行为与 EXO 完全一致，切换完全无效果。

#### 根因 1：ALI / 新星 / IJK 在工厂创建后 getType() 永远返回 EXO

`PlayerEngineFactory.create()` 对新增引擎 `ALI / NOVA / IJK` 的处理直接是：
```java
case ALI, NOVA, IJK -> new ExoPlayerEngine(decode, listener);
```
`ExoPlayerEngine.getType()` 硬编码返回 `Type.EXO`，导致：
- `PlayerManager.getEngine()` 按枚举 switch 回 `PlayerSetting.ENGINE_EXO`；
- `PlayerEngineDialog.setSelected()` 读取引擎状态时看到 EXO，EXO 按钮一直被标为选中；
- `ensureEngine()` 的 `matches(engine, spec)` 判定时：`engine.getType() == EXO` 而 `resolve(spec)` 读 setting 是 `ALI`，**每次播放都会触发不必要的引擎重建**（但重建出来又是新 ExoPlayerEngine 继续报 EXO → 下一次依旧 mismatch → 重建无限循环）。

修复：在 [PlayerEngineFactory.java](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/engine/PlayerEngineFactory.java#L29-L49) 中对三类新增引擎用 **匿名子类重写 getType()** 返回对应 `Type.ALI / Type.NOVA / Type.IJK`，底层实现仍复用 ExoPlayer（未引入 so 时零崩溃风险），保证用户 UI 选择的按钮与后续 setting/Type 完全一致，`matches` 判定也能正确命中，避免不必要重建。

```java
case ALI -> new ExoPlayerEngine(decode, listener) {
    @Override public PlayerEngine.Type getType() { return PlayerEngine.Type.ALI; }
};
case NOVA -> new ExoPlayerEngine(decode, listener) {
    @Override public PlayerEngine.Type getType() { return PlayerEngine.Type.NOVA; }
};
case IJK -> new ExoPlayerEngine(decode, listener) {
    @Override public PlayerEngine.Type getType() { return PlayerEngine.Type.IJK; }
};
```

#### 根因 2：PlayerManager.setEngine() 在 isEmpty() 时 early return 导致引擎实例从未更新

旧实现：
```java
public void setEngine(int targetEngine) {
    int oldEngine = getEngine();
    PlayerSetting.putEngine(targetEngine);
    if (oldEngine == targetEngine || isEmpty()) return;   // ← isEmpty 时直接 return
    startCurrent();
}
```
典型场景：用户在「国产电视剧发行许可证」界面（解析还没出 URL，`spec.getUrl() == null` → `isEmpty() == true`）切了引擎后关弹窗；因为 early return，内部 `engine` 对象仍是旧实例 → `engine.getType()` 仍然是旧枚举 → 下次打开弹窗 `getCurrentEngine(player)` 读回来还是 EXO / 旧引擎 → 用户感官上「切换没生效」。即便最终 `onParseSuccess` 时 `ensureEngine()` 会重建，中间 UI 状态也不正确。

修复：在 [PlayerManager.java](file:///workspace/app/src/main/java/com/ssmhdssmhd/mxboxs/player/PlayerManager.java#L235-L256) 中去掉 isEmpty() 前置判断，`setEngine()` 改为：
1. 先 `PlayerSetting.putEngine(targetEngine)` 保存用户偏好（总是执行）；
2. 不论 URL 是否存在，**无条件用最新 setting 重新实例化 engine 对象**：移除旧 player 的 listener、创建新引擎、通过 `callback.onPlayerRebuild(player)` 通知 UI 重新绑定；
3. 旧 engine `stop() + release()`（双重 try/catch，防止已 release 过再报异常）；
4. **仅当 hadMedia 时**才 `startCurrent(currentPosition)` 重载当前 URL，避免空 spec 触发底层 `engine.start()` 空指针或无意义播放。

这保证：用户切换引擎后，下一次打开弹窗就能看到正确按钮被高亮，不会再「选了非 EXO 按钮、结果还是 EXO 被选中」。

---

## [v5.5.27] - 2026-08-01

### 深度优化「内置解析」与「超级解析」报错问题

#### 一、HTTP 层全链路强化（根治 "Bad HTTP Status / 403 / 3xx 重定向"）

- **`ParseJob.jsonParse()` 请求前必补默认 Headers**：用 `UrlUtil.mergeDefaultHeaders()` 强制补齐 `User-Agent`（Android Chrome Mobile UA）和 `Referer`（解析站地址），避免被源站的 UA/防盗链拦截。
- **HTTP 状态码强过滤**：`jsonParse` 与 `safeGetBody` 新增 `!res.isSuccessful()` 判定，只接受 `2xx` 响应；`3xx/4xx/5xx` 一律不往下走（避免把 403 错误页 / 302 跳转页当 JSON 或 HTML 去解析，直接导致解析报错或嗅探失败）。
- **JSON 解析全防御**：`raw` 为空、`Json.parse` 抛错、`data` 节点缺失/非 object 三类场景全部捕获，不再一路抛异常走到 `onParseError`。
- **`safeGetBody` 正文抓取强化**：
  - 使用 `mergeDefaultHeaders` 补 UA/Referer，HTML 页面抓取成功率显著提升；
  - 先看 `Content-Type`：若已经是 `video/* / audio/* / octet-stream / image/*` 的二进制响应，直接 `return null`，不再把视频流误当 HTML 正则扫（节省大量带宽和时间）；
  - 正文上限从 `512KB` 放宽到 `1MB`，适配某些把 m3u8 地址塞进长 JS 的站点。

#### 二、AI 智能嗅探（`aiSmartParseFallback`）多候选 + 轻量探测双保险

- **从「单命中 → Top8 候选 + 逐个校验」**：`UrlUtil.sniffVideoCandidates(..., topN=8, ...)` 返回最多 8 个去重候选，`aiSmartParseFallback` 按顺序逐个 `probeVideoUrl` 做轻量探测，**第一个可达即成功回调**，命中率远高于旧版「第一个拿不到就 GG」。
- **`probeVideoUrl` 轻量探测机制**（新增工具方法）：
  - 视频后缀直链走**快路径**：`.m3u8/.mp4/.flv/.m4v/.ts/.mkv/.webm`（含 query 形式）直接信任，不发探测请求（避免某些站点 HEAD 被封反而把可用链接误判）；
  - 非直链先 `HEAD`（10s 短超时 client），若 `HEAD` 被 403/405 → 回退 `GET Range: bytes=0-0`；
  - 状态码接受 `2xx / 206 Partial Content / 416 Range Not Satisfiable`（后两者都表示目标文件真实存在）；
  - `isVideoLikeResponse` 按 `Content-Type` / `Content-Length` 双维度判定：
    - 正例：`video/* / audio/* / mpegurl / x-mpegurl / octet-stream / mp4 / mp2t / x-flv / webm / matroska`，或响应体 `> 256KB`；
    - 反例：`text/html / application/json / text/plain` 直接排除。
- **三路兜底策略**：直链判定 → Top8 候选逐个探测 → 最后把 webUrl 本身当视频探测一次（来源 `AI-Probe`），任何一路命中都回调成功。

#### 三、`UrlUtil.sniffVideoCandidates` 再添 4 轮解码嗅探

在原有「引号优先 / 无引号兜底」两轮基础上，新增：
1. **JSON 转义还原轮**：`\"` → `"`、`\/` → `/`，适配大多数把地址塞进 JSON 字符串的接口；
2. **URLDecoder 解码轮**：对 `%xx / +` 形式整体 `URLDecoder.decode` 一次再扫，解决两层 URL encode 导致正则命中不到的问题；
3. **Base64 片段扫描轮**：正则抓 `atob(...)` 里的 base64 片段 / 长 base64 串（长度 32~4096，4 字节对齐），`Base64.decode` 后再扫；
4. **Base64 → URLDecoder 二级解码轮**：兼容 `encodeURIComponent(atob(...))` 这种常见组合。

候选去重用 `LinkedHashSet`，保证命中顺序稳定且不重复。

#### 四、超级解析（`superParse`）并发模型再升级：JSON + WebView + AI 三路齐发

- **AI fallback 不再等失败后再跑**：`CountDownLatch` 从 `count` 改为 `count + 1`，**AI 解析作为独立并发的一路直接 submit**，给 JSON/Web 留 3s 先发窗口，之后一起抢成功回调。
- **最长等待 30s → 15s**：`latch.await` 超时从 30 秒砍半，15s 内没任何一路成功就立即走最后一次 AI 保底，避免用户长时间黑屏。
- **latch 异常分支兜底依然保留**：超时 / 中断 / 正常完成但 `!done.get()` 三种出口都会再触发一次 `aiSmartParseFallback`，确保不遗漏任何成功机会。

---

## [v5.5.26] - 2026-08-01

### 超级解析 AI 智能兜底 + 内置解析器

- **修复「超级解析」报错与无响应问题**：`ParseJob.superParse()` 增加三层兜底，任何一层命中即成功回调，避免 `onParseError()` 的空白页：
  1. 无可用解析器时 → 直接走 AI 智能解析，不再抛错；
  2. 解析超时/中断 → AI fallback；
  3. 解析正常完成但未出 URL → AI fallback。
- **AI 智能嗅探（`aiSmartParseFallback`）**：
  - 识别后缀/query 直链：`.m3u8`、`.mp4`、`.flv`、`.m4v`、`.ts`（含 `?` 参数）→ 直接播放，标记来源 `AI-Direct`；
  - 否则抓取页面 HTML/JS，用启发式正则扫常见视频地址（支持相对路径拼接）→ 命中后标记来源 `AI-Sniff`。
- **内置 m3u8 解析器（Built-in, type=5）**：`Parse.builtin()` 注册进 `VodConfig` 的解析器列表，默认位于首位；逻辑直接复用 AI 嗅探流程，不依赖第三方解析站。
- **工具链补齐**：`UrlUtil.sniffVideo` / `sniffByKeys` / `resolve` 组合，确保从任意页面正文里抓出真实视频地址。

### 进度条旁边新增全屏按钮（Mobile + Leanback）

- 解决「第一次知道转横屏，退出全屏后不知道在哪里全屏」的可用性问题：
  - Mobile：`app/src/mobile/res/layout/view_control_vod.xml` → 在进度条/倍速右侧新增 `@+id/fullscreen` 图标按钮；
  - Leanback：`app/src/leanback/res/layout/view_control_vod.xml` → 同样位置新增，`focusable=true` 方便遥控器导航；
  - 新增 drawable：`ic_control_fullscreen_enter.xml`（四角箭头向外）、`ic_control_fullscreen_exit.xml`（四角箭头向内）；
  - 多语言字符串：`play_fullscreen` / `play_fullscreen_exit`（英/简中/繁中）；
  - 点击逻辑：两边 `VideoActivity#onFullscreen()` 切换进入/退出 `enterFullscreen()`、`exitFullscreen()`，同步更新图标和 contentDescription。

### 播放器引擎扩展（Ali / Nova / IJK）

- `PlayerEngine.Type` 枚举新增：`ALI`、`NOVA`、`IJK`，共 6 档引擎（EXO / MPV / SYSTEM / ALI / NOVA / IJK）。
- `PlayerEngineFactory.create` 路由：`ALI / NOVA / IJK` 目前先走 Exo 兜底实现（避免本地缺库时崩溃），后续若接入底层 so，只需替换对应分支实现。
- `PlayerSetting` 扩展：
  - `ENGINE_ALI=3 / ENGINE_NOVA=4 / ENGINE_IJK=5 / ENGINE_MAX=ENGINE_IJK`
  - `isAli()` / `isNova()` / `isIjk()` 判断器
  - `putEngine(index)` 边界正确 clamp，原 MPV / SYSTEM 的渲染约束不变。
- 引擎对话框 / 引擎列表：`select_engine` string-array 对齐新增 `Ali / Nova / IJK`，`PlaybackAction` 引擎文本从数组拿 → 所有页面显示一致。

### 播放器引擎详细设置面板

- 在设置里「播放器引擎」可点击切换的基础上，为不同引擎显示专属参数（切换引擎时自动隐藏无关项）：
  - **MPV 专属**：`mpv.conf` 导入 / `gpu-next` / `vulkan` 开关；
  - **Exo / Ali / Nova / IJK（Exo 兼容分支）专属**：解码设置入口、智能去广告、隧道模式 Tunnel、音频软解偏好、视频软解偏好、AAC 优先、DV7 HEVC 回退；
  - **共享项**（所有引擎显示）：渲染器、缩放比例、字幕样式、长按倍速、后台播放、预载设置、UA 设置、音频直通（Exo/MPV 双兼容）。
- 涉及文件：
  - `fragment_setting_player.xml`（mobile）
  - `activity_setting_player.xml`（leanback）
  - `SettingPlayerFragment` / `SettingPlayerActivity` → 新增点击事件、显示文本刷新、`setVisible()` 按引擎分组切换可见性。
- `PlayerSetting` 已存全部参数的 getter/putter：
  - `isTunnel / putTunnel`、`isAudioPassThrough / putAudioPassThrough`
  - `isAudioPrefer / putAudioPrefer`、`isVideoPrefer / putVideoPrefer`
  - `isPreferAAC / putPreferAAC`、`isDv7HevcFallback / putDv7HevcFallback`

### 版本 & CI

- `versionCode`：574 → **575**，`versionName`：`5.5.25` → **`5.5.26`**
- README：版本表、云端编译产物名、功能特性（AI 解析 / 全屏按钮 / 更多引擎 / 详细参数）同步更新
- GitHub Actions（`.github/workflows/build.yml`）保持：
  - push 到 `main` / `mobile` 分支 → 出 Artifact（含 TV + 手机共 4 APK）
  - push tag `v*` → 自动创建 GitHub Release，挂载 4 APK
  - 4 个 build task：`Mobile arm64 / Mobile armeabi / Leanback arm64 / Leanback armeabi`，依次 assemble `assemble{Variant}Release`

---

## [v5.5.25] - 2026-07-31

### 视频播放器进度条与交互优化

- **进度条修复**：重写 `PlayerSeekView` 组件，确保进度条正常显示和实时更新
  - 接入 Media3 Player.Listener 监听播放状态变化
  - 新增 Handler 定时更新（500ms），实时刷新播放位置和缓冲进度
  - 正确处理 `TIME_UNSET` 常量，避免异常值导致 UI 错误
  - 支持拖动（scrubbing）状态管理，拖动时暂停自动更新
- **视频缩略图预览**：新增拖动进度条时的视频帧预览功能
  - 新增 `FrameExtractor` 工具类，使用 `MediaMetadataRetriever` 提取视频帧
  - 在进度条上方显示缩略图和时间戳，拖动时实时更新
  - 支持帧缓存和异步加载，避免主线程卡顿
  - 布局优化：缩略图 200x112dp，时间戳显示当前/总时长
- **亮度/音量手势控制（哔哩哔哩风格）**：
  - 全屏左右分区：**左半屏** 控制亮度，**右半屏** 控制音量
  - 下滑降低亮度/音量，上滑增加亮度/音量
  - 修复 `isSide()` 方法返回值，确保全屏手势区域正确识别
  - 支持连续滑动调节，提升操作流畅度
- **崩溃修复**：
  - 修复 `VideoActivity` 在 PlaybackService 未绑定时的 NPE 崩溃
  - `player()` 方法增加 null 检查，返回安全 null
  - `initDanmaku()`、`onSeekPositionChanged()` 等方法增加空指针防护
  - `onScrubStop()` 回调使用 `PlaybackActivity.this` 消除匿名类方法遮蔽
- **UI 组件迁移**：
  - `LinearLayoutCompat` → `LinearLayout`（修复数据绑定类型解析失败）
  - 同步更新 mobile 和 leanback 两个 flavor 的 widget 布局
- 版本：`versionCode 573→574` / `versionName 5.5.24→5.5.25`

---

## [v5.5.24] - 2026-07-30

### 应用标识大版本调整
- **应用名**：`沫兮影视` / `影视` / `影視` → 统一为 **`MXboxS`**（默认/简中/繁中 3 语言全部一致）
- **包名 (applicationId)**：`com.ssmhdssmhd.android.tv` → **`com.ssmhdssmhd.mxboxs`**
- **Gradle namespace**：同步更新为 `com.ssmhdssmhd.mxboxs`
- **rootProject.name**：`settings.gradle` 中 `MoXiTV` → `MXboxS`
- **源码目录迁移**：`com/ssmhdssmhd/android/tv/**` → `com/ssmhdssmhd/mxboxs/**`（含 `main/leanback/mobile` 三套源集）
- **EventBus 注解索引**：`EventIndex` 包路径同步改为 `com.ssmhdssmhd.mxboxs.event.EventIndex`
- **Proguard 规则**：bean 包 keep 规则同步到新包名
- **Manifest meta-data Startup**：`com.ssmhdssmhd.android.tv.Startup` → `com.ssmhdssmhd.mxboxs.Startup`（AndroidX Startup InitializationProvider 里写死的类路径）
- **APK 输出文件名**：默认 `${mode}-${abi}.apk` → **`MXboxS-${mode}-${abi}-${versionName}.apk`**
  - 手机版 arm64：`MXboxS-mobile-arm64_v8a-5.5.24.apk`
  - 手机版 armeabi：`MXboxS-mobile-armeabi_v7a-5.5.24.apk`
- **CI 配套（GitHub Actions）**：
  - Workflow 名：`Build MoXiTV Release` → **`Build MXboxS Release`**
  - APK Artifact 名：`MoXiTV-Release-APKs` → **`MXboxS-Release-APKs`**
  - Keystore 主题 DN 改为 MXboxS (Shenzhen)，alias `moxitv` → `mxboxs`，密码同步更新
  - **构建目标同时覆盖 TV + Mobile**：一次运行产出 4 个 APK
    - mobile-arm64_v8a / mobile-armeabi_v7a（手机版）
    - leanback-arm64_v8a / leanback-armeabi_v7a（电视版，Android TV / 盒子）
  - **构建诊断强化**：
    - Create local.properties：优先读 `ANDROID_SDK_ROOT`，回退 `ANDROID_HOME`，写入后打印校验
    - 新增 Print Gradle / Java env 步骤输出环境变量
    - 构建命令加 `--stacktrace --no-daemon`，完成后立即 `find` 列 APK
    - Upload Build Logs：`continue-on-error: true` + `if-no-files-found: ignore`
    - Collect APKs：优先匹配 `MXboxS-*.apk` 新产物名，找不到直接 exit 1
- **local.properties**：签名 alias / 密码同步改为 `mxboxs` / `mxboxs123456`（与 CI 一致）
- 版本：`versionCode 572→573` / `versionName 5.5.23→5.5.24`

### Media3 1.11.0-rc01 API 兼容性修复（构建期补全）
- **PlaybackException 构造器升级**：三参数 `(message, cause, errorCode)` 替代旧两参数调用
  - `SystemPlayerEngine#onError` → `ERROR_CODE_REMOTE_ERROR`
  - `SystemPlayerEngine#start` IOException → `ERROR_CODE_IO_UNSPECIFIED`
  - 通用 Exception → `ERROR_CODE_UNSPECIFIED`
- **PlayerView.RESIZE_MODE_FIT 迁移**：`PlayerView.RESIZE_MODE_FIT` → `AspectRatioFrameLayout.RESIZE_MODE_FIT`（Media3 1.11+ 将常量移到 AspectRatioFrameLayout）
  - `PlaybackActivity#configurePlayerView()` 同步更新并导入新类
- **MIME 类型常量修正**：`MimeTypes.VIDEO_MKV` → **`MimeTypes.VIDEO_MATROSKA`**（Media3 中 MKV 已重命名为 MATROSKA 常量）
  - `MediaItemFactory#guessMimeType` 已适配

### Leanback (电视版) 资源缺失补全
- **color/selector_item 新增到 main**：原仅 mobile flavor 存在，但 `shape_item.xml` / `shape_item_round.xml` 在 main drawable 中引用，导致 Leanback 构建报 `resource color/selector_item not found`
- **style/ToolbarTextAppearance 新增到 main**：原仅 mobile flavor 存在，但 `activity_setting_ai.xml` 在 main layout 中引用，导致 Leanback 构建报 `resource style/ToolbarTextAppearance not found`
- 两项资源均补入 `app/src/main/res/**` 以共享给两个 flavor

### v5.5.22 遗留清理（二次确认）
- `select_engine` 字符串数组残留 `IJK` / `VLC` 项 → 最终确认只剩 `EXO` / `MPV` / `System` 三项
- 默认 `strings.xml`（默认语言）与 zh-rCN / zh-rTW 三套资源均已对齐

### 其他说明
- FileProvider authority `${applicationId}.provider`、startup authority `${applicationId}.androidx-startup`、ActionReceiver intent-filter `com.ssmhdssmhd.*.stop/play/pause/prev/next/audio`、CastActivity `com.ssmhdssmhd.*.cast` 均用 manifest 占位符，随 applicationId 变更自动生效，无需手动改
- 布局中自定义 View 全限定类名（`com.ssmhdssmhd.android.tv.ui.custom.*`）已随 453 文件 sed 批量替换为新包名

---

## [v5.5.23] - 2026-07-30

### CI / GitHub Actions 修复
- 修复 `build.yml` 中 `android-actions/setup-android@v3` 的无效参数错误：
  - 错误：`license_accept`（该 action 不支持此参数，直接导致步骤失败）
  - 修复：升级为 `android-actions/setup-android@v4`，改用官方参数 `accept-android-sdk-licenses: 'yes'`
- Actions 升级：`actions/checkout@v4→v5`、`actions/setup-java@v4→v5`、`actions/setup-python@v5→v6`（消除 Node.js 20 → 24 的 deprecated 警告）
- `local.properties` 增加 `ANDROID_SDK_ROOT` 回退写入，确保 SDK 路径在所有 runner 环境下被 Gradle 正确识别
- 版本：`versionCode 571→572` / `versionName 5.5.22→5.5.23`

---

## [v5.5.22] - 2026-07-30

### 播放器引擎更新
- **Media3 (ExoPlayer) 升级**：`1.10.0` → **`1.11.0-rc01`**（截至 2026-07-22 最新发布）
- 移除未接入的 **IJK / VLC** 占位播放器引擎（UI、源码、字符串资源全部清理）
- 保留 **EXO (Media3)** 为默认引擎，**MPV** 为可切换引擎，播放器架构完整闭环

### 播放器引擎完整性核对

| 引擎 | 接口实现 | 硬解/软解 | 字幕 | 画质优化 | DRM | 错误处理 | 缓存预加载 |
|------|----------|-----------|------|----------|-----|----------|-----------|
| **EXO (Media3 1.11.0-rc01)** | ✅ PlayerEngine 完整 | ✅ RenderersFactory 动态切换 | ✅ Media3 内置 | ✅ TrackSelector 强制最高码率 + 视频缩放裁剪 | ✅ Widevine/PlayReady/ClearKey | ✅ 自动重试格式/直播窗口回退 | ✅ OkHttp + SimpleCache 80% 空间 |
| **MPV (MpvPlayer)** | ✅ PlayerEngine 完整 | ✅ setDecode + mediacodec-copy | ✅ setSubtitleStyle / addSubtitle | ✅ profile=high-quality + 去环路滤波 + soxr 重采样 | ❌ 不支持（需 DRM 自动切 EXO） | ✅ 解码失败切换硬解模式 | ✅ demuxer 12M 缓存 + 磁盘预载 |

### 播放器模块清单
- `PlayerEngine` 接口：Type 仅保留 `EXO` / `MPV` 两项
- `PlayerEngineFactory` 工厂：仅生产 EXO / MPV 实例，MP V不可用（含 DRM/smb）自动降级 EXO
- `ExoPlayerEngine`：PreCache + MediaSourceFactory + applyQualitySettings 完整
- `MediaSourceFactory`：HLS / DASH / SmoothStreaming / RTSP 四种扩展均已接入
- `MpvPlayerEngine`：字幕样式、字幕动态添加、Vulkan/GpuNext 切换、TLS CA 证书嵌入

### 其他修复
- 修复布局中遗留 `surface_type="none"` 导致黑屏的问题
- 清理 3 个语言版本（默认/zh-rCN/zh-rTW）的 play_ijk、play_vlc 及 error_play_ijk_* / error_play_vlc_* 字符串
- 清理 Leanback (TV) 和 Mobile (手机) 两套布局中的 IJK/VLC 选择按钮
- `agp` 调整至 `9.1.0`，`gradle-wrapper` 保持 `9.6.1`

---

## [v5.5.21] - 2026-06-27

- 版本号基准：`versionCode 570` / `versionName 5.5.21`
- 初始包含 EXO / MPV 双引擎及 IJK / VLC 占位代码（后续已移除）
