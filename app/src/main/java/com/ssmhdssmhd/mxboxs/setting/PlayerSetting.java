package com.ssmhdssmhd.mxboxs.setting;

import android.content.Intent;
import android.provider.Settings;

import com.ssmhdssmhd.mxboxs.App;
import com.github.catvod.utils.Prefers;

public class PlayerSetting {

    public static final int ENGINE_EXO = 0;
    public static final int ENGINE_MPV = 1;
    public static final int ENGINE_SYSTEM = 2;
    public static final int ENGINE_ALI = 3;
    public static final int ENGINE_NOVA = 4;
    public static final int ENGINE_IJK = 5;
    public static final int ENGINE_VLC = 6;
    public static final int ENGINE_MX = 7;
    public static final int ENGINE_MPVEX = 8;
    public static final int ENGINE_MPVNOVA = 9;
    public static final int ENGINE_KMP = 10;
    public static final int ENGINE_MAX = ENGINE_KMP;
    public static final int RENDER_SURFACE = 0;
    public static final int RENDER_TEXTURE = 1;
    public static final int MIN_SCALE = 0;
    public static final int MAX_SCALE = 4;
    private static final int MIN_SIZE = 0;
    private static final int MAX_SIZE = 3;
    private static final int MIN_BACKGROUND = 0;
    private static final int MAX_BACKGROUND = 2;
    private static final float MIN_SPEED = 2.0f;
    private static final float MAX_SPEED = 5.0f;

    public static int getEngine() {
        return Math.clamp(Prefers.getInt("player_engine", ENGINE_EXO), ENGINE_EXO, ENGINE_MAX);
    }

    public static void putEngine(int engine) {
        Prefers.put("player_engine", Math.clamp(engine, ENGINE_EXO, ENGINE_MAX));
        if (!isMpv() && !isSystem() && !isExternal() && isTunnel()) Prefers.put("render", RENDER_SURFACE);
    }

    // ===== 直播播放器引擎（独立于点播，默认回退到点播引擎，保证老用户无感升级） =====

    public static int getLiveEngine() {
        return Math.clamp(Prefers.getInt("live_engine", getEngine()), ENGINE_EXO, ENGINE_MAX);
    }

    public static void putLiveEngine(int engine) {
        Prefers.put("live_engine", Math.clamp(engine, ENGINE_EXO, ENGINE_MAX));
    }

    public static boolean isLiveMpv() {
        return getLiveEngine() == ENGINE_MPV;
    }

    public static boolean isLiveSystem() {
        return getLiveEngine() == ENGINE_SYSTEM;
    }

    public static boolean isMpv() {
        return getEngine() == ENGINE_MPV;
    }

    public static boolean isSystem() {
        return getEngine() == ENGINE_SYSTEM;
    }

    public static boolean isAli() {
        return getEngine() == ENGINE_ALI;
    }

    public static boolean isNova() {
        return getEngine() == ENGINE_NOVA;
    }

    public static boolean isIjk() {
        return getEngine() == ENGINE_IJK;
    }

    public static boolean isVlc() {
        return getEngine() == ENGINE_VLC;
    }

    public static boolean isMx() {
        return getEngine() == ENGINE_MX;
    }

    public static boolean isMpvEx() {
        return getEngine() == ENGINE_MPVEX;
    }

    public static boolean isMpvNova() {
        return getEngine() == ENGINE_MPVNOVA;
    }

    public static boolean isKmp() {
        return getEngine() == ENGINE_KMP;
    }

    /** 外部第三方播放器（VLC/MX/mpvEx/mpvNova/KMPlayer）均通过 Intent 调起外部 App，不使用内嵌渲染链路 */
    public static boolean isExternal() {
        int e = getEngine();
        return e == ENGINE_VLC || e == ENGINE_MX || e == ENGINE_MPVEX || e == ENGINE_MPVNOVA || e == ENGINE_KMP;
    }

    public static boolean isMpvGpuNext() {
        return Prefers.getBoolean("mpv_gpu_next");
    }

    public static void putMpvGpuNext(boolean gpuNext) {
        Prefers.put("mpv_gpu_next", gpuNext);
    }

    public static boolean isMpvVulkan() {
        return Prefers.getBoolean("mpv_vulkan");
    }

    public static void putMpvVulkan(boolean vulkan) {
        Prefers.put("mpv_vulkan", vulkan);
    }

    public static int getRender() {
        return Math.clamp(Prefers.getInt("render", RENDER_SURFACE), RENDER_SURFACE, RENDER_TEXTURE);
    }

    public static void putRender(int render) {
        Prefers.put("render", Math.clamp(render, RENDER_SURFACE, RENDER_TEXTURE));
        if ((!isMpv() && !isSystem() && !isExternal()) && isTunnel() && getRender() == RENDER_TEXTURE) Prefers.put("tunnel", false);
    }

    public static boolean isTunnel() {
        return Prefers.getBoolean("tunnel");
    }

    public static void putTunnel(boolean tunnel) {
        Prefers.put("tunnel", tunnel);
        if ((!isMpv() && !isSystem() && !isExternal()) && tunnel) Prefers.put("render", RENDER_SURFACE);
    }

    public static boolean isTunnelingEnabled() {
        return isTunnel() && getRender() == RENDER_SURFACE;
    }

    public static int getSize() {
        return Math.clamp(Prefers.getInt("size", 2), MIN_SIZE, MAX_SIZE);
    }

    public static void putSize(int size) {
        Prefers.put("size", Math.clamp(size, MIN_SIZE, MAX_SIZE));
    }

    public static int getScale() {
        return Math.clamp(Prefers.getInt("scale"), MIN_SCALE, MAX_SCALE);
    }

    public static void putScale(int scale) {
        Prefers.put("scale", Math.clamp(scale, MIN_SCALE, MAX_SCALE));
    }

    public static int getBackground() {
        return Math.clamp(Prefers.getInt("background", 2), MIN_BACKGROUND, MAX_BACKGROUND);
    }

    public static void putBackground(int background) {
        Prefers.put("background", Math.clamp(background, MIN_BACKGROUND, MAX_BACKGROUND));
    }

    public static boolean isBackgroundOff() {
        return getBackground() == 0;
    }

    public static boolean isBackgroundOn() {
        return getBackground() == 1 || getBackground() == 2;
    }

    public static boolean isBackgroundPiP() {
        return getBackground() == 2;
    }

    public static float getSpeed() {
        return Math.clamp(Prefers.getFloat("speed", 3), MIN_SPEED, MAX_SPEED);
    }

    public static void putSpeed(float speed) {
        Prefers.put("speed", Math.clamp(speed, MIN_SPEED, MAX_SPEED));
    }

    public static boolean isCaption() {
        return Prefers.getBoolean("caption");
    }

    public static void putCaption(boolean caption) {
        Prefers.put("caption", caption);
    }

    public static float getSubtitleTextSize() {
        return Prefers.getFloat("subtitle_text_size");
    }

    public static void putSubtitleTextSize(float value) {
        Prefers.put("subtitle_text_size", value);
    }

    public static float getSubtitlePosition() {
        return Prefers.getFloat("subtitle_position");
    }

    public static void putSubtitlePosition(float value) {
        Prefers.put("subtitle_position", value);
    }

    public static boolean hasCaption() {
        return new Intent(Settings.ACTION_CAPTIONING_SETTINGS).resolveActivity(App.get().getPackageManager()) != null;
    }

    public static boolean isAudioPassThrough() {
        return Prefers.getBoolean("audio_pass_through", true);
    }

    public static void putAudioPassThrough(boolean audioPassThrough) {
        Prefers.put("audio_pass_through", audioPassThrough);
    }

    public static boolean isAudioPrefer() {
        return Prefers.getBoolean("audio_prefer");
    }

    public static void putAudioPrefer(boolean audioPrefer) {
        Prefers.put("audio_prefer", audioPrefer);
    }

    public static boolean isVideoPrefer() {
        return Prefers.getBoolean("video_prefer");
    }

    public static void putVideoPrefer(boolean videoPrefer) {
        Prefers.put("video_prefer", videoPrefer);
    }

    public static boolean isPreferAAC() {
        return Prefers.getBoolean("prefer_aac");
    }

    public static void putPreferAAC(boolean preferAAC) {
        Prefers.put("prefer_aac", preferAAC);
    }

    public static boolean isDv7HevcFallback() {
        return Prefers.getBoolean("dv7_hevc_fallback");
    }

    public static void putDv7HevcFallback(boolean fallback) {
        Prefers.put("dv7_hevc_fallback", fallback);
    }

    // AI 设置相关
    public static boolean isAiQualityBoost() {
        return Prefers.getBoolean("ai_quality_boost", true);
    }

    public static void putAiQualityBoost(boolean boost) {
        Prefers.put("ai_quality_boost", boost);
    }

    public static boolean isAiHdr() {
        return Prefers.getBoolean("ai_hdr", true);
    }

    public static void putAiHdr(boolean hdr) {
        Prefers.put("ai_hdr", hdr);
    }

    public static boolean isAiDenoise() {
        return Prefers.getBoolean("ai_denoise", true);
    }

    public static void putAiDenoise(boolean denoise) {
        Prefers.put("ai_denoise", denoise);
    }

    public static boolean isAiSharpness() {
        return Prefers.getBoolean("ai_sharpness", true);
    }

    public static void putAiSharpness(boolean sharpness) {
        Prefers.put("ai_sharpness", sharpness);
    }

    public static boolean isAiSmoothPlayback() {
        return Prefers.getBoolean("ai_smooth_playback", true);
    }

    public static void putAiSmoothPlayback(boolean smooth) {
        Prefers.put("ai_smooth_playback", smooth);
    }

    public static boolean isAiAutoFrameRate() {
        return Prefers.getBoolean("ai_auto_fps", true);
    }

    public static void putAiAutoFrameRate(boolean auto) {
        Prefers.put("ai_auto_fps", auto);
    }

    public static boolean isAiAudioEnhance() {
        return Prefers.getBoolean("ai_audio_enhance", true);
    }

    public static void putAiAudioEnhance(boolean enhance) {
        Prefers.put("ai_audio_enhance", enhance);
    }

    public static boolean isAiBassBoost() {
        return Prefers.getBoolean("ai_bass_boost", true);
    }

    public static void putAiBassBoost(boolean bass) {
        Prefers.put("ai_bass_boost", bass);
    }

    public static boolean isAiDialogEnhance() {
        return Prefers.getBoolean("ai_dialog_enhance", true);
    }

    public static void putAiDialogEnhance(boolean dialog) {
        Prefers.put("ai_dialog_enhance", dialog);
    }

    // ===== 播放优化（高级设置）=====
    // 缓冲模式：0=快起播（minBuffer=15s/maxBuffer=30s/forPlayback=0.5s），1=流畅（30s/120s/2s）
    public static final int BUFFER_FAST = 0;
    public static final int BUFFER_SMOOTH = 1;

    /** 缓存写入开关：开启后播放过的视频会落盘，回看/续播不重新下载。默认开启。 */
    public static boolean isCacheWriteEnabled() {
        return Prefers.getBoolean("play_cache_write", true);
    }

    public static void putCacheWriteEnabled(boolean enabled) {
        Prefers.put("play_cache_write", enabled);
    }

    /** 自适应码率开关：开启后多码率 m3u8 按带宽自动降档（流畅）；关闭则强制最高画质（易卡顿）。默认开启。 */
    public static boolean isAdaptiveBitrateEnabled() {
        return Prefers.getBoolean("play_adaptive_bitrate", true);
    }

    public static void putAdaptiveBitrateEnabled(boolean enabled) {
        Prefers.put("play_adaptive_bitrate", enabled);
    }

    /** 缓冲模式：0=快起播（起播快，弱网易卡顿），1=流畅（起播慢，几乎不卡顿）。默认快起播。 */
    public static int getBufferMode() {
        return Math.clamp(Prefers.getInt("play_buffer_mode", BUFFER_FAST), BUFFER_FAST, BUFFER_SMOOTH);
    }

    public static void putBufferMode(int mode) {
        Prefers.put("play_buffer_mode", Math.clamp(mode, BUFFER_FAST, BUFFER_SMOOTH));
    }

    /** 画质偏好：0=自适应（跟随带宽），1=最高，2=720P，3=480P。默认自适应。 */
    public static final int QUALITY_AUTO = 0;
    public static final int QUALITY_MAX = 1;
    public static final int QUALITY_720 = 2;
    public static final int QUALITY_480 = 3;

    public static int getQualityPref() {
        return Math.clamp(Prefers.getInt("play_quality_pref", QUALITY_AUTO), QUALITY_AUTO, QUALITY_480);
    }

    public static void putQualityPref(int pref) {
        Prefers.put("play_quality_pref", Math.clamp(pref, QUALITY_AUTO, QUALITY_480));
    }

    /**
     * WebView 嗅探默认开启开关：
     *   开（默认）：HTML 嗅探接口（jx/xmflv/qq/duopian 等）解析路径里，默认 WebView 嗅探和多解析站并发一起跑，
     *               更稳，基本不会"0 KB/s 转圈"（但 WebView 稍耗电）。
     *   关：解析仅走云端接口（qcb jiexi/xt）+ 本地正则/并发 probe/LLM 嗅探，
     *       不启 WebView（省电量，适合弱机或追求续航）。
     */
    public static boolean isWebviewSniffDefaultOn() {
        return Prefers.getBoolean("play_webview_sniff_on", true);
    }

    public static void putWebviewSniffDefaultOn(boolean on) {
        Prefers.put("play_webview_sniff_on", on);
    }

    // ===== AI 播放优化（统一总开关）=====
    // 开关打开后，PlaybackAdvisor 会：
    //   1) 从 ExoPlayer 的 BandwidthMeter 读取估算带宽；
    //   2) 弱网（<2Mbps）→ 强制缓冲「流畅」档 + 画质降到 480P；
    //   3) 高速网（>8Mbps）→ 缓冲「快起播」档 + 画质最高；
    //   4) 其余 → 保持用户手动设置。
    //   5) 有切集习惯（播放 >=6 分钟后主动切下一集）时，后台提前预解析下一集。
    // 开关关闭：完全按用户手动设置。
    public static boolean isAiPlayOptEnabled() {
        return Prefers.getBoolean("ai_play_opt", true);
    }

    public static void putAiPlayOptEnabled(boolean enabled) {
        Prefers.put("ai_play_opt", enabled);
    }

    /** 记录用户「播放超过 6 分钟就切下一集」的习惯（用于 PlaybackAdvisor 的智能预加载）。 */
    public static void noteQuickSkipNext() {
        int cnt = Prefers.getInt("ai_quick_skip_count", 0);
        Prefers.put("ai_quick_skip_count", Math.min(cnt + 1, 10));
    }

    /** 是否启用智能预解析下一集：累计 3 次以上"播放 6 分钟就跳下一集"才启动。 */
    public static boolean shouldPreparseNext() {
        return Prefers.getInt("ai_quick_skip_count", 0) >= 3;
    }

    // ===== LLM 嗅探配置（可选云端 endpoint，不配则跳过）=====
    public static String getLlmEndpoint() {
        return Prefers.getString("llm_endpoint");
    }

    public static void putLlmEndpoint(String endpoint) {
        Prefers.put("llm_endpoint", endpoint == null ? "" : endpoint);
    }

    public static String getLlmKey() {
        return Prefers.getString("llm_key");
    }

    public static void putLlmKey(String key) {
        Prefers.put("llm_key", key == null ? "" : key);
    }

    public static String getLlmModel() {
        return Prefers.getString("llm_model", "");
    }

    public static void putLlmModel(String model) {
        Prefers.put("llm_model", model == null ? "" : model);
    }
}
