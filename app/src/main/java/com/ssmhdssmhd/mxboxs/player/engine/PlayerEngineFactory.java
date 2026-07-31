package com.ssmhdssmhd.mxboxs.player.engine;

import static com.ssmhdssmhd.mxboxs.player.engine.PlayerEngine.Type.EXO;
import static com.ssmhdssmhd.mxboxs.player.engine.PlayerEngine.Type.MPV;
import static com.ssmhdssmhd.mxboxs.player.engine.PlayerEngine.Type.SYSTEM;

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
        };
    }

    public static boolean matches(PlayerEngine engine, PlaySpec spec) {
        return engine != null && engine.getType() == resolve(spec);
    }

    private static PlayerEngine.Type resolve(PlaySpec spec) {
        if (!isMpvReady()) return PlayerEngine.Type.EXO;
        if (requiresExo(spec)) return PlayerEngine.Type.EXO;
        if (PlayerSetting.isSystem()) return PlayerEngine.Type.SYSTEM;
        return PlayerSetting.isMpv() ? MPV : EXO;
    }

    private static PlayerEngine.Type resolve() {
        if (PlayerSetting.isSystem()) return PlayerEngine.Type.SYSTEM;
        return isMpvReady() ? MPV : EXO;
    }

    private static boolean requiresExo(PlaySpec spec) {
        return spec.getDrm() != null || "smb".equals(UrlUtil.scheme(spec.getUrl()));
    }

    private static boolean isMpvReady() {
        return PlayerSetting.isMpv() && MpvPlayerEngine.isAvailable();
    }
}
