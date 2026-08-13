package com.ssmhdssmhd.mxboxs.player.exo;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.trackselection.TrackSelector;
import androidx.media3.exoplayer.util.EventLogger;

import com.ssmhdssmhd.mxboxs.App;
import com.ssmhdssmhd.mxboxs.BuildConfig;
import com.ssmhdssmhd.mxboxs.player.PlaybackAdvisor;
import com.ssmhdssmhd.mxboxs.player.engine.PlayerEngine;
import com.ssmhdssmhd.mxboxs.player.track.LangUtil;
import com.ssmhdssmhd.mxboxs.setting.PlayerSetting;

import java.util.concurrent.Executors;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class ExoUtil {

    public static ExoPlayer buildPlayer(int decode, Player.Listener listener) {
        ExoPlayer player = new ExoPlayer.Builder(App.get())
                .setTrackSelector(buildTrackSelector())
                .setRenderersFactory(buildPlaybackRenderersFactory(decode))
                .setMediaSourceFactory(buildMediaSourceFactory())
                .setLoadControl(buildLoadControl())
                .build();
        if (BuildConfig.DEBUG) player.addAnalyticsListener(new EventLogger());
        // AI 播放优化：把 BandwidthMeter 的带宽估算喂给 PlaybackAdvisor。
        // 总开关打开时，Advisor 会根据估算带宽自动调缓冲模式和画质偏好。
        // 注意：media3 不同子版本 getBandwidthMeter() 返回值/签名偶尔变，这里反射注册更稳妥。
        registerBandwidthAdvisor(player);
        player.setAudioAttributes(AudioAttributes.DEFAULT, true);
        player.setHandleAudioBecomingNoisy(true);
        player.setPlayWhenReady(true);
        player.addListener(listener);
        return player;
    }

    /**
     * 根据高级设置「缓冲模式」构建 LoadControl：
     * - 快起播：minBuffer=15s / maxBuffer=30s / forPlayback=0.5s（起播快，弱网易卡顿）
     * - 流畅：minBuffer=30s / maxBuffer=120s / forPlayback=2s（起播慢，几乎不卡顿）
     */
    private static androidx.media3.exoplayer.DefaultLoadControl buildLoadControl() {
        boolean smooth = PlayerSetting.getBufferMode() == PlayerSetting.BUFFER_SMOOTH;
        int minBufferMs = smooth ? 30_000 : 15_000;
        int maxBufferMs = smooth ? 120_000 : 30_000;
        int bufferForPlaybackMs = smooth ? 2_000 : 500;
        int bufferForPlaybackAfterRebufferMs = smooth ? 3_000 : 1_000;
        return new androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(minBufferMs, maxBufferMs, bufferForPlaybackMs, bufferForPlaybackAfterRebufferMs)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();
    }

    public static String getMimeType(int errorCode) {
        switch (errorCode) {
            case PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED:
            case PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED:
            case PlaybackException.ERROR_CODE_IO_UNSPECIFIED:
            case PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED:
            case PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED:
                return MimeTypes.APPLICATION_M3U8;
            default:
                return null;
        }
    }

    /**
     * @deprecated setVideoScalingMode 已在 Media3 1.11.0-rc01 移除，默认 Surface 的
     *             "scale to fit" 行为已足够，这里保留空方法仅为兼容旧调用。
     */
    @Deprecated
    public static void applyQualitySettings(ExoPlayer player) {}

    /**
     * 把 PlaybackAdvisor 挂到 BandwidthMeter 上，反射注册以兼容不同 media3 子版本的 API 差异。
     * 只要 getBandwidthMeter() 存在、且有 addEventListener(Executor, EventListener) /
     * addListener(Executor, BandwidthMeter.EventListener) 两种之一就接上。
     */
    private static void registerBandwidthAdvisor(ExoPlayer player) {
        try {
            java.lang.reflect.Method getBw = ExoPlayer.class.getMethod("getBandwidthMeter");
            Object bwMeter = getBw.invoke(player);
            if (bwMeter == null) return;
            Object advisor = PlaybackAdvisor.get();
            java.util.concurrent.Executor exec = Executors.newSingleThreadExecutor();
            // 先尝试 2 参数：(Executor, BandwidthMeter.EventListener) 旧签名
            try {
                Class<?> listenerClass = Class.forName("androidx.media3.exoplayer.upstream.BandwidthMeter$EventListener");
                java.lang.reflect.Method add = bwMeter.getClass().getMethod("addEventListener",
                        java.util.concurrent.Executor.class, listenerClass);
                add.invoke(bwMeter, exec, advisor);
                return;
            } catch (NoSuchMethodException ignored) {}
            // 再尝试：addListener(Executor, BandwidthMeter.EventListener)
            try {
                Class<?> listenerClass = Class.forName("androidx.media3.exoplayer.upstream.BandwidthMeter$EventListener");
                java.lang.reflect.Method add = bwMeter.getClass().getMethod("addListener",
                        java.util.concurrent.Executor.class, listenerClass);
                add.invoke(bwMeter, exec, advisor);
                return;
            } catch (NoSuchMethodException ignored) {}
            // 再尝试：EventListener 在 common 包名
            try {
                Class<?> listenerClass = Class.forName("androidx.media3.common.BandwidthMeter$EventListener");
                java.lang.reflect.Method add;
                try {
                    add = bwMeter.getClass().getMethod("addEventListener",
                            java.util.concurrent.Executor.class, listenerClass);
                } catch (NoSuchMethodException e) {
                    add = bwMeter.getClass().getMethod("addListener",
                            java.util.concurrent.Executor.class, listenerClass);
                }
                add.invoke(bwMeter, exec, advisor);
            } catch (NoSuchMethodException ignored) {}
        } catch (Throwable ignored) {}
    }

    public static Map<String, String> extractHeaders(MediaItem item) {
        Bundle extras = item.requestMetadata.extras;
        if (extras == null) return new HashMap<>();
        return extras.keySet().stream().filter(key -> extras.getString(key) != null).collect(Collectors.toMap(key -> key, extras::getString));
    }

    private static int getRenderMode(int decode) {
        return decode == PlayerEngine.HARD ? DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON : DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER;
    }

    private static TrackSelector buildTrackSelector() {
        DefaultTrackSelector trackSelector = new DefaultTrackSelector(App.get());
        DefaultTrackSelector.Parameters.Builder builder = trackSelector.buildUponParameters();

        // NOTE: Many Parameters.Builder setters were renamed or removed between
        // Media3 1.3.x and 1.11.x (e.g. setForceHighestSupportedBitrate,
        // setMaxVideoSize, setAllowVideoNonSeamlessAdaptiveness, etc). To keep
        // this source compatible with Media3 1.11.0-rc01 we call every setter
        // reflectively and simply skip it when the method does not exist. The
        // default Parameters values are always valid; these setters only express
        // additional quality/preference preferences.

        // Preferred AAC audio
        trySet(builder, "setPreferredAudioMimeType", new Class<?>[]{String.class},
            MimeTypes.AUDIO_AAC, PlayerSetting.isPreferAAC());

        // Preferred text / subtitle languages
        Object prefLangs = LangUtil.getPreferredTextLanguages();
        if (prefLangs != null) {
            // setPreferredTextLanguages takes either List<String> or String...
            try {
                java.lang.reflect.Method m = findMethod(builder.getClass(),
                    "setPreferredTextLanguages", java.util.List.class);
                if (m != null) m.invoke(builder, prefLangs);
                else {
                    m = findMethod(builder.getClass(),
                        "setPreferredTextLanguages", String[].class);
                    if (m != null) {
                        if (prefLangs instanceof java.util.List<?>) {
                            java.util.List<?> l = (java.util.List<?>) prefLangs;
                            m.invoke(builder, (Object) l.toArray(new String[0]));
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        // Tunneling
        trySet(builder, "setTunnelingEnabled", new Class<?>[]{boolean.class},
            PlayerSetting.isTunnelingEnabled(), true);

        // 画质与码率策略（受高级设置「自适应码率」「画质偏好」控制）：
        // - 自适应开启：forceHighest=false，让 ExoPlayer 按带宽在多码率间自动切换；
        //   画质偏好提供上限（720P/480P 时 setMaxVideoSize 限制分辨率+码率）。
        // - 自适应关闭：forceHighest=true，锁最高画质（弱网易卡顿，仅供用户主动选择）。
        boolean adaptive = PlayerSetting.isAdaptiveBitrateEnabled();
        int qualityPref = PlayerSetting.getQualityPref();
        trySet(builder, "setForceHighestSupportedBitrate",
            new Class<?>[]{boolean.class}, !adaptive, true);

        if (qualityPref == PlayerSetting.QUALITY_720) {
            trySetMany(builder, "setMaxVideoSize", new Class<?>[]{int.class, int.class},
                new Object[]{1280, 720}, true);
            trySet(builder, "setMaxVideoBitrate", new Class<?>[]{int.class}, 5_000_000, true);
        } else if (qualityPref == PlayerSetting.QUALITY_480) {
            trySetMany(builder, "setMaxVideoSize", new Class<?>[]{int.class, int.class},
                new Object[]{854, 480}, true);
            trySet(builder, "setMaxVideoBitrate", new Class<?>[]{int.class}, 2_000_000, true);
        } else {
            // 自适应 / 最高：不设分辨率与码率上限
            trySetMany(builder, "setMaxVideoSize", new Class<?>[]{int.class, int.class},
                new Object[]{Integer.MAX_VALUE, Integer.MAX_VALUE}, true);
            trySet(builder, "setMaxVideoBitrate", new Class<?>[]{int.class}, Integer.MAX_VALUE, true);
        }
        trySet(builder, "setMaxVideoFrameRate",
            new Class<?>[]{int.class}, Integer.MAX_VALUE, true);

        // Adaptive flexibility flags
        trySet(builder, "setAllowVideoNonSeamlessAdaptiveness",
            new Class<?>[]{boolean.class}, true, true);
        trySet(builder, "setAllowAudioMixedSampleRateAdaptiveness",
            new Class<?>[]{boolean.class}, true, true);
        trySet(builder, "setAllowAudioMixedMimeTypeAdaptiveness",
            new Class<?>[]{boolean.class}, true, true);
        trySet(builder, "setAllowVideoMixedMimeTypeAdaptiveness",
            new Class<?>[]{boolean.class}, true, true);

        trackSelector.setParameters(builder.build());
        return trackSelector;
    }

    // ---- reflection helpers (fail-safe) ----

    private static void trySet(Object target, String method, Class<?>[] sig,
                               Object arg, boolean enabled) {
        if (!enabled) return;
        try {
            java.lang.reflect.Method m = findMethod(target.getClass(), method, sig);
            if (m != null) m.invoke(target, arg);
        } catch (Throwable ignored) {
        }
    }

    private static void trySetMany(Object target, String method, Class<?>[] sig,
                                   Object[] args, boolean enabled) {
        if (!enabled) return;
        try {
            java.lang.reflect.Method m = findMethod(target.getClass(), method, sig);
            if (m != null) m.invoke(target, args);
        } catch (Throwable ignored) {
        }
    }

    private static java.lang.reflect.Method findMethod(Class<?> clazz,
                                                       String name, Class<?>... sig) {
        try {
            return clazz.getMethod(name, sig);
        } catch (NoSuchMethodException e) {
            // Walk superclasses (Builder extends Object, but Media3 builders
            // often use intermediate abstract classes, so superclass scan is safe)
            Class<?> s = clazz.getSuperclass();
            while (s != null && s != Object.class) {
                try {
                    return s.getMethod(name, sig);
                } catch (NoSuchMethodException e2) {
                    s = s.getSuperclass();
                }
            }
            return null;
        }
    }

    private static RenderersFactory buildPlaybackRenderersFactory(int decode) {
        return buildRenderersFactory(getRenderMode(decode), PlayerSetting.isAudioPrefer(), PlayerSetting.isVideoPrefer());
    }

    static RenderersFactory buildRenderersFactory() {
        return buildRenderersFactory(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER, PlayerSetting.isAudioPrefer(), PlayerSetting.isVideoPrefer());
    }

    private static RenderersFactory buildRenderersFactory(int renderMode, boolean audioPrefer, boolean videoPrefer) {
        DefaultRenderersFactory factory = new DefaultRenderersFactory(App.get()) {
            @Override
            protected AudioSink buildAudioSink(@NonNull Context context, boolean enableFloatOutput, boolean enableAudioOutputPlaybackParams) {
                return ExoUtil.buildAudioSink(context, enableFloatOutput, enableAudioOutputPlaybackParams);
            }
        };
        return factory.setEnableDecoderFallback(true).setExtensionRendererMode(renderMode);
    }

    private static AudioSink buildAudioSink(Context context, boolean enableFloatOutput, boolean enableAudioOutputPlaybackParams) {
        DefaultAudioSink.Builder builder = new DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput);
        // The API for enabling audio-track playback parameters was renamed across
        // Media3 minor releases (setEnableAudioOutputPlaybackParameters →
        // setEnableAudioTrackPlaybackParams → others). Try the known names via
        // reflection, keep defaults if none match. This keeps the source
        // compatible with 1.5.x through 1.11.x without hardcoding a specific name.
        String[] methodNames = new String[] {
            "setEnableAudioOutputPlaybackParameters",
            "setEnableAudioTrackPlaybackParams",
            "setAudioTrackPlaybackParametersEnabled",
        };
        for (String name : methodNames) {
            try {
                java.lang.reflect.Method m = builder.getClass()
                    .getMethod(name, boolean.class);
                m.invoke(builder, enableAudioOutputPlaybackParams);
                break;
            } catch (Throwable ignored) {
                // try next
            }
        }
        return builder.build();
    }

    private static MediaSource.Factory buildMediaSourceFactory() {
        return new MediaSourceFactory();
    }
}
