package com.ssmhdssmhd.mxboxs.player.engine;

import static com.ssmhdssmhd.mxboxs.player.engine.PlayerEngine.Type.ALI;
import static com.ssmhdssmhd.mxboxs.player.engine.PlayerEngine.Type.EXO;
import static com.ssmhdssmhd.mxboxs.player.engine.PlayerEngine.Type.IJK;
import static com.ssmhdssmhd.mxboxs.player.engine.PlayerEngine.Type.MPV;
import static com.ssmhdssmhd.mxboxs.player.engine.PlayerEngine.Type.NOVA;
import static com.ssmhdssmhd.mxboxs.player.engine.PlayerEngine.Type.SYSTEM;

import androidx.media3.common.Player;

import com.ssmhdssmhd.mxboxs.player.exo.ExoPlayerEngine;
import com.ssmhdssmhd.mxboxs.player.media.PlaySpec;
import com.ssmhdssmhd.mxboxs.player.mpv.MpvPlayerEngine;
import com.ssmhdssmhd.mxboxs.player.system.SystemPlayerEngine;
import com.ssmhdssmhd.mxboxs.setting.PlayerSetting;
import com.ssmhdssmhd.mxboxs.utils.UrlUtil;

public final class PlayerEngineFactory {

    // ===== 旧接口（点播，live=false）保留兼容 =====

    public static PlayerEngine create(int decode, Player.Listener listener) {
        return create(decode, false, listener);
    }

    public static PlayerEngine create(int decode, PlaySpec spec, Player.Listener listener) {
        return create(decode, spec, false, listener);
    }

    public static boolean matches(PlayerEngine engine, PlaySpec spec) {
        return matches(engine, spec, false);
    }

    // ===== 新接口（带 live 参数）=====

    public static PlayerEngine create(int decode, boolean live, Player.Listener listener) {
        return create(decode, resolve(live), listener);
    }

    public static PlayerEngine create(int decode, PlaySpec spec, boolean live, Player.Listener listener) {
        return create(decode, resolve(spec, live), listener);
    }

    public static boolean matches(PlayerEngine engine, PlaySpec spec, boolean live) {
        return engine != null && engine.getType() == resolve(spec, live);
    }

    // ===== 内部实现 =====

    private static PlayerEngine create(int decode, PlayerEngine.Type type, Player.Listener listener) {
        return switch (type) {
            case EXO -> new ExoPlayerEngine(decode, listener);
            case MPV -> new MpvPlayerEngine(decode, listener);
            case SYSTEM -> new SystemPlayerEngine(decode, listener);
            case ALI -> new ExoPlayerEngine(decode, listener) {
                @Override public PlayerEngine.Type getType() { return PlayerEngine.Type.ALI; }
            };
            case NOVA -> new ExoPlayerEngine(decode, listener) {
                @Override public PlayerEngine.Type getType() { return PlayerEngine.Type.NOVA; }
            };
            case IJK -> new ExoPlayerEngine(decode, listener) {
                @Override public PlayerEngine.Type getType() { return PlayerEngine.Type.IJK; }
            };
        };
    }

    private static PlayerEngine.Type resolve(PlaySpec spec, boolean live) {
        if (requiresExo(spec)) return PlayerEngine.Type.EXO;
        PlayerEngine.Type preferred = resolveFromSetting(live);
        if (preferred == MPV && !isMpvReady(live)) return EXO;
        return preferred;
    }

    private static PlayerEngine.Type resolve(boolean live) {
        PlayerEngine.Type preferred = resolveFromSetting(live);
        if (preferred == MPV && !isMpvReady(live)) return EXO;
        return preferred;
    }

    /**
     * 将 PlayerSetting 中保存的 engine 常量映射到枚举。
     * live=true 时读取直播专属引擎设置（live_engine），否则读取点播引擎（player_engine）。
     */
    private static PlayerEngine.Type resolveFromSetting(boolean live) {
        int engine = live ? PlayerSetting.getLiveEngine() : PlayerSetting.getEngine();
        return switch (engine) {
            case PlayerSetting.ENGINE_MPV -> MPV;
            case PlayerSetting.ENGINE_SYSTEM -> SYSTEM;
            case PlayerSetting.ENGINE_ALI -> ALI;
            case PlayerSetting.ENGINE_NOVA -> NOVA;
            case PlayerSetting.ENGINE_IJK -> IJK;
            default -> EXO;
        };
    }

    private static boolean requiresExo(PlaySpec spec) {
        return spec.getDrm() != null || "smb".equals(UrlUtil.scheme(spec.getUrl()));
    }

    private static boolean isMpvReady(boolean live) {
        boolean isMpv = live ? PlayerSetting.isLiveMpv() : PlayerSetting.isMpv();
        return isMpv && MpvPlayerEngine.isAvailable();
    }
}
