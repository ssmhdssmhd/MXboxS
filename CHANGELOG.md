# 更新日志 (Changelog)

格式：`[版本号] - YYYY-MM-DD`

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
