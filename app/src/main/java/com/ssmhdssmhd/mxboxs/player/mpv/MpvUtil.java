package com.ssmhdssmhd.mxboxs.player.mpv;

import android.text.TextUtils;

import androidx.media3.common.Player;
import androidx.media3.common.util.Util;
import androidx.media3.mpvplayer.MpvPlayer;
import androidx.media3.mpvplayer.MpvPlayerConfig;
import androidx.media3.ui.SubtitleView;

import com.ssmhdssmhd.mxboxs.App;
import com.ssmhdssmhd.mxboxs.player.track.LangUtil;
import com.ssmhdssmhd.mxboxs.player.util.PlayerHelper;
import com.ssmhdssmhd.mxboxs.setting.PlayerSetting;
import com.ssmhdssmhd.mxboxs.setting.PreloadSetting;
import com.ssmhdssmhd.mxboxs.setting.Setting;
import com.github.catvod.utils.Path;

import java.io.File;

public final class MpvUtil {

    private static final String ASSET_CA_FILE = "cacert.pem";
    private static final double DEFAULT_SUB_POS = 100.0;
    private static final double DEFAULT_SUB_SCALE = 1.0;
    private static final double MIN_SUB_SCALE = 0.5;
    private static final double MAX_SUB_SCALE = 3.0;
    private static final double MIN_SUB_POS = 0.0;
    private static final double MAX_SUB_POS = 150.0;
    private static final String OPT_GPU_API = "gpu-api";
    private static final String OPT_GPU_CONTEXT = "gpu-context";
    private static final String OPT_SUB_LANG = "slang";
    private static final String VALUE_ANDROID_VK = "androidvk";
    private static final String VALUE_VULKAN = "vulkan";

    public static boolean isAvailable() {
        try {
            return MpvPlayer.isAvailable();
        } catch (Throwable e) {
            return false;
        }
    }

    public static MpvPlayer buildPlayer(int decode, Player.Listener listener) {
        MpvPlayer player = new MpvPlayer.Builder(App.get()).setDecode(decode).setConfig(buildConfig()).build();
        player.addListener(listener);
        return player;
    }

    public static void setSubtitleStyle(MpvPlayer player) {
        player.setSubtitleOptions(buildSubtitleConfig());
    }

    public static void applyQualitySettings(MpvPlayer player) {
        try {
            player.setOptionString("profile", "high-quality");
            player.setOptionString("hwdec", "mediacodec-copy");
            player.setOptionString("vd-lavc-dr", "yes");
            player.setOptionString("vd-lavc-threads", "auto");
            player.setOptionString("vd-lavc-skiploopfilter", "none");
            player.setOptionString("vd-lavc-skipframe", "none");
            player.setOptionString("vd-lavc-skipidct", "none");
            player.setOptionString("cache", "yes");
            player.setOptionString("cache-default", "153600");
            player.setOptionString("demuxer-max-bytes", "12582912");
            player.setOptionString("demuxer-max-back-bytes", "6291456");
            player.setOptionString("ao", "audiotrack,opensles");
            player.setOptionString("audio-resample-filter-swal", "soxr");
            player.setOptionString("audio-channels", "stereo");
        } catch (Throwable ignored) {
        }
    }

    private static MpvPlayerConfig buildConfig() {
        MpvPlayerConfig.Builder builder = newConfigBuilder();
        addAndroidOptions(builder);
        addTrackLanguageOptions(builder);
        addSubtitleStyleOptions(builder);
        return builder.build();
    }

    private static MpvPlayerConfig buildSubtitleConfig() {
        MpvPlayerConfig.Builder builder = new MpvPlayerConfig.Builder();
        addSubtitleStyleOptions(builder);
        return builder.build();
    }

    private static MpvPlayerConfig.Builder newConfigBuilder() {
        return new MpvPlayerConfig.Builder().setDefaultUserAgent(getDefaultUserAgent()).setHlsHttpPersistent(false);
    }

    private static void addAndroidOptions(MpvPlayerConfig.Builder builder) {
        addAndroidDefaultOptions(builder);
        addTlsCaFile(builder);
        addVideoOutputOptions(builder);
        addPreloadOptions(builder);
    }

    private static void addAndroidDefaultOptions(MpvPlayerConfig.Builder builder) {
        File configDir = Path.mpv();
        File cacheDir = Path.mpvCache();
        builder.addConfigDirectory(configDir).addAndroidFontConfig(configDir, cacheDir).addAndroidDefaults(getVideoOutputDriver(), cacheDir);
    }

    private static void addTlsCaFile(MpvPlayerConfig.Builder builder) {
        builder.addTlsCaFileFromAsset(App.get(), ASSET_CA_FILE, Path.files(ASSET_CA_FILE));
    }

    private static void addTrackLanguageOptions(MpvPlayerConfig.Builder builder) {
        builder.addPostInitStringOption(OPT_SUB_LANG, LangUtil.getPreferredTextLanguageList());
    }

    private static String getVideoOutputDriver() {
        return PlayerSetting.isMpvGpuNext() ? MpvPlayerConfig.VIDEO_OUTPUT_GPU_NEXT : null;
    }

    private static void addVideoOutputOptions(MpvPlayerConfig.Builder builder) {
        if (!PlayerSetting.isMpvVulkan()) return;
        builder.addPreInitStringOption(OPT_GPU_API, VALUE_VULKAN).addPreInitStringOption(OPT_GPU_CONTEXT, VALUE_ANDROID_VK);
    }

    private static void addPreloadOptions(MpvPlayerConfig.Builder builder) {
        if (!PreloadSetting.isPreload()) return;
        builder.addDiskCacheOptions(Path.mpvCache(), PreloadSetting.getPreloadTimeSeconds(), PreloadSetting.getPreloadSizeMb());
    }

    private static void addSubtitleStyleOptions(MpvPlayerConfig.Builder builder) {
        builder.addAndroidSubtitleOptions(App.get(), PlayerSetting.isCaption(), getSubtitlePosition(), getSubtitleScale());
    }

    private static String getDefaultUserAgent() {
        String userAgent = Setting.getUa();
        return TextUtils.isEmpty(userAgent) ? PlayerHelper.getDefaultUa() : userAgent;
    }

    private static double getSubtitlePosition() {
        float position = PlayerSetting.getSubtitlePosition();
        if (position == 0) return DEFAULT_SUB_POS;
        return Util.constrainValue(DEFAULT_SUB_POS - position * 100.0, MIN_SUB_POS, MAX_SUB_POS);
    }

    private static double getSubtitleScale() {
        float textSize = PlayerSetting.getSubtitleTextSize();
        if (textSize == 0) return DEFAULT_SUB_SCALE;
        return Util.constrainValue(textSize / SubtitleView.DEFAULT_TEXT_SIZE_FRACTION, MIN_SUB_SCALE, MAX_SUB_SCALE);
    }
}
