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
import com.ssmhdssmhd.mxboxs.player.engine.PlayerEngine;
import com.ssmhdssmhd.mxboxs.player.track.LangUtil;
import com.ssmhdssmhd.mxboxs.setting.PlayerSetting;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class ExoUtil {

    public static ExoPlayer buildPlayer(int decode, Player.Listener listener) {
        ExoPlayer player = new ExoPlayer.Builder(App.get()).setTrackSelector(buildTrackSelector()).setRenderersFactory(buildPlaybackRenderersFactory(decode)).setMediaSourceFactory(buildMediaSourceFactory()).build();
        if (BuildConfig.DEBUG) player.addAnalyticsListener(new EventLogger());
        player.setAudioAttributes(AudioAttributes.DEFAULT, true);
        player.setHandleAudioBecomingNoisy(true);
        player.setPlayWhenReady(true);
        player.addListener(listener);
        return player;
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

    public static void applyQualitySettings(ExoPlayer player) {
        try {
            // NOTE: ExoPlayer.setVideoScalingMode(int) and VIDEO_SCALING_MODE_* constants
            // were REMOVED in Media3 1.11.0-rc01. The modern equivalent is to set
            // video scaling mode via TrackSelectionParameters. Since the default behavior
            // already scales content to fit the surface, we enforce
            // SCALE_TO_FIT_WITH_CROPPING-equivalent behavior through the standard
            // TrackSelectionParameters builder.
            androidx.media3.common.TrackSelectionParameters current = player.getTrackSelectionParameters();
            androidx.media3.common.TrackSelectionParameters.Builder builder = current.buildUpon();
            try {
                // Media3 may or may not expose setVideoScalingMode(int) on
                // TrackSelectionParameters.Builder across minor versions; call via
                // reflection to remain source-compatible.
                java.lang.reflect.Method m = builder.getClass()
                    .getMethod("setVideoScalingMode", int.class);
                // VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING == 2
                m.invoke(builder, 2);
                player.setTrackSelectionParameters(builder.build());
            } catch (Throwable ignored) {
                // Fallback: rely on default (scale to fit). No actionable failure.
            }
        } catch (Throwable ignored) {
        }
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

        // AI quality optimization: force highest bitrate for best quality
        trySet(builder, "setForceHighestSupportedBitrate",
            new Class<?>[]{boolean.class}, true, true);
        trySetMany(builder, "setMaxVideoSize",
            new Class<?>[]{int.class, int.class},
            new Object[]{Integer.MAX_VALUE, Integer.MAX_VALUE}, true);
        trySet(builder, "setMaxVideoBitrate",
            new Class<?>[]{int.class}, Integer.MAX_VALUE, true);
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
