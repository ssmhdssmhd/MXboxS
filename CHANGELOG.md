# 更新日志 (Changelog)

格式：`[版本号] - YYYY-MM-DD`

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
