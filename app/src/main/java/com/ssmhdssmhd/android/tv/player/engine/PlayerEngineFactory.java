package com.ssmhdssmhd.android.tv.player.engine;

import static com.ssmhdssmhd.android.tv.player.engine.PlayerEngine.Type.EXO;
import static com.ssmhdssmhd.android.tv.player.engine.PlayerEngine.Type.IJK;
import static com.ssmhdssmhd.android.tv.player.engine.PlayerEngine.Type.MPV;
import static com.ssmhdssmhd.android.tv.player.engine.PlayerEngine.Type.VLC;

import androidx.media3.common.Player;

import com.ssmhdssmhd.android.tv.player.exo.ExoPlayerEngine;
import com.ssmhdssmhd.android.tv.player.ijk.IjkPlayerEngine;
import com.ssmhdssmhd.android.tv.player.media.PlaySpec;
import com.ssmhdssmhd.android.tv.player.mpv.MpvPlayerEngine;
import com.ssmhdssmhd.android.tv.player.vlc.VlcPlayerEngine;
import com.ssmhdssmhd.android.tv.setting.PlayerSetting;
import com.ssmhdssmhd.android.tv.utils.UrlUtil;

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
            case IJK -> new IjkPlayerEngine(decode, listener);
            case VLC -> new VlcPlayerEngine(decode, listener);
        };
    }

    public static boolean matches(PlayerEngine engine, PlaySpec spec) {
        return engine != null && engine.getType() == resolve(spec);
    }

    private static PlayerEngine.Type resolve(PlaySpec spec) {
        if (requiresExo(spec)) return EXO;
        if (PlayerSetting.isIjk() && isIjkReady()) return IJK;
        if (PlayerSetting.isVlc() && isVlcReady()) return VLC;
        if (PlayerSetting.isMpv() && isMpvReady()) return MPV;
        return isIjkReady() ? IJK : (isVlcReady() ? VLC : (isMpvReady() ? MPV : EXO));
    }

    private static PlayerEngine.Type resolve() {
        if (isIjkReady()) return IJK;
        if (isVlcReady()) return VLC;
        if (isMpvReady()) return MPV;
        return EXO;
    }

    private static boolean requiresExo(PlaySpec spec) {
        return spec.getDrm() != null || "smb".equals(UrlUtil.scheme(spec.getUrl()));
    }

    private static boolean isMpvReady() {
        return PlayerSetting.isMpv() && MpvPlayerEngine.isAvailable();
    }

    private static boolean isIjkReady() {
        return com.ssmhdssmhd.android.tv.player.ijk.IjkUtil.isAvailable();
    }

    private static boolean isVlcReady() {
        return com.ssmhdssmhd.android.tv.player.vlc.VlcUtil.isAvailable();
    }
}
