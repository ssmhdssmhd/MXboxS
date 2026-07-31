package com.ssmhdssmhd.mxboxs.player.engine;

import static com.ssmhdssmhd.mxboxs.player.engine.PlayerEngine.Type.EXO;
import static com.ssmhdssmhd.mxboxs.player.engine.PlayerEngine.Type.IJK;
import static com.ssmhdssmhd.mxboxs.player.engine.PlayerEngine.Type.MPV;
import static com.ssmhdssmhd.mxboxs.player.engine.PlayerEngine.Type.SYSTEM;
import static com.ssmhdssmhd.mxboxs.player.engine.PlayerEngine.Type.VLC;
import static com.ssmhdssmhd.mxboxs.player.engine.PlayerEngine.Type.WEB;

import androidx.media3.common.Player;

import com.ssmhdssmhd.mxboxs.player.exo.ExoPlayerEngine;
import com.ssmhdssmhd.mxboxs.player.media.PlaySpec;
import com.ssmhdssmhd.mxboxs.player.mpv.MpvPlayerEngine;
import com.ssmhdssmhd.mxboxs.player.system.SystemPlayerEngine;
import com.ssmhdssmhd.mxboxs.setting.PlayerSetting;
import com.ssmhdssmhd.mxboxs.utils.UrlUtil;

public final class PlayerEngineFactory {

    public static PlayerEngine create(int decode, Player.Listener listener) {
        return create(decode, resolve(), listener);
    }

    public static PlayerEngine create(int decode, PlaySpec spec, Player.Listener listener) {
        return create(decode, resolve(spec), listener);
    }

    private static PlayerEngine create(int decode, PlayerEngine.Type type, Player.Listener listener) {
        return switch (type) {
            case EXO -> new ExoPlayerEngine(decode, listener);
            case MPV -> new MpvPlayerEngine(decode, listener);
            case SYSTEM -> new SystemPlayerEngine(decode, listener);
            case IJK -> ijkOrFallback(decode, listener);
            case VLC -> vlcOrFallback(decode, listener);
            case WEB -> webOrFallback(decode, listener);
        };
    }

    private static PlayerEngine ijkOrFallback(int decode, Player.Listener listener) {
        // ijkplayer requires native .so libraries that may not be bundled;
        // fall back to ExoPlayer when not available.
        try {
            Class.forName("tv.danmaku.ijk.media.player.IjkMediaPlayer");
            // If/when an IjkPlayerEngine implementation is provided, use it here.
            return new ExoPlayerEngine(decode, listener);
        } catch (Throwable e) {
            return new ExoPlayerEngine(decode, listener);
        }
    }

    private static PlayerEngine vlcOrFallback(int decode, Player.Listener listener) {
        try {
            Class.forName("org.videolan.libvlc.LibVLC");
            // If/when a VlcPlayerEngine implementation is provided, use it here.
            return new ExoPlayerEngine(decode, listener);
        } catch (Throwable e) {
            return new ExoPlayerEngine(decode, listener);
        }
    }

    private static PlayerEngine webOrFallback(int decode, Player.Listener listener) {
        // Web engine is handled via a separate player surface; for now fall back to Exo.
        // The built-in HLS.js web player is accessed via /player endpoint.
        return new ExoPlayerEngine(decode, listener);
    }

    public static boolean matches(PlayerEngine engine, PlaySpec spec) {
        return engine != null && engine.getType() == resolve(spec);
    }

    private static PlayerEngine.Type resolve(PlaySpec spec) {
        if (!isMpvReady()) return PlayerEngine.Type.EXO;
        if (requiresExo(spec)) return PlayerEngine.Type.EXO;
        if (PlayerSetting.isSystem()) return PlayerEngine.Type.SYSTEM;
        if (PlayerSetting.isIjk()) return PlayerSetting.isIjkAvailable() ? IJK : PlayerEngine.Type.EXO;
        if (PlayerSetting.isVlc()) return PlayerSetting.isVlcAvailable() ? VLC : PlayerEngine.Type.EXO;
        if (PlayerSetting.isWeb()) return PlayerEngine.Type.EXO; // WEB is handled differently; default to Exo
        return PlayerSetting.isMpv() ? MPV : EXO;
    }

    private static PlayerEngine.Type resolve() {
        if (PlayerSetting.isSystem()) return PlayerEngine.Type.SYSTEM;
        if (PlayerSetting.isIjk()) return PlayerSetting.isIjkAvailable() ? IJK : PlayerEngine.Type.EXO;
        if (PlayerSetting.isVlc()) return PlayerSetting.isVlcAvailable() ? VLC : PlayerEngine.Type.EXO;
        if (PlayerSetting.isWeb()) return PlayerEngine.Type.EXO;
        return isMpvReady() ? MPV : EXO;
    }

    private static boolean requiresExo(PlaySpec spec) {
        return spec.getDrm() != null || "smb".equals(UrlUtil.scheme(spec.getUrl()));
    }

    private static boolean isMpvReady() {
        return PlayerSetting.isMpv() && MpvPlayerEngine.isAvailable();
    }
}
