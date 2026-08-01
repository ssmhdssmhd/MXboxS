# 更新日志 (Changelog)

格式：`[版本号] - YYYY-MM-DD`

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
