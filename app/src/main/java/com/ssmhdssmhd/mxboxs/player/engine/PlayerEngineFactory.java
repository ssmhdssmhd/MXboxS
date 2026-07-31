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
            // 新增引擎：未集成原生 so 时降级到 ExoPlayer，保证不崩溃
            case ALI, NOVA, IJK -> new ExoPlayerEngine(decode, listener);
        };
    }

    public static boolean matches(PlayerEngine engine, PlaySpec spec) {
        return engine != null && engine.getType() == resolve(spec);
    }

    private static PlayerEngine.Type resolve(PlaySpec spec) {
        if (requiresExo(spec)) return PlayerEngine.Type.EXO;
        PlayerEngine.Type preferred = resolveFromSetting();
        if (preferred == MPV && !isMpvReady()) return EXO;
        return preferred;
    }

    private static PlayerEngine.Type resolve() {
        PlayerEngine.Type preferred = resolveFromSetting();
        if (preferred == MPV && !isMpvReady()) return EXO;
        return preferred;
    }

    /**
     * 将 PlayerSetting 中保存的 engine 常量映射到枚举。
     * 新增引擎（ALI/NOVA/IJK）在未引入专用实现时会在 create 里降级到 Exo，
     * 这里直接返回对应枚举类型以便 UI / 配置面板区分展示。
     */
    private static PlayerEngine.Type resolveFromSetting() {
        return switch (PlayerSetting.getEngine()) {
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

    private static boolean isMpvReady() {
        return PlayerSetting.isMpv() && MpvPlayerEngine.isAvailable();
    }
}
