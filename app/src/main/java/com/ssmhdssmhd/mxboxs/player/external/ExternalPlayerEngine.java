package com.ssmhdssmhd.mxboxs.player.external;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;

import com.ssmhdssmhd.mxboxs.App;
import com.ssmhdssmhd.mxboxs.R;
import com.ssmhdssmhd.mxboxs.player.engine.PlayerEngine;
import com.ssmhdssmhd.mxboxs.player.exo.ExoPlayerEngine;
import com.ssmhdssmhd.mxboxs.player.media.PlaySpec;
import com.ssmhdssmhd.mxboxs.player.util.PlayerHelper;
import com.ssmhdssmhd.mxboxs.utils.FileUtil;
import com.ssmhdssmhd.mxboxs.utils.Notify;
import com.ssmhdssmhd.mxboxs.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 外部第三方播放器引擎。继承 ExoPlayerEngine 保证 getPlayer() / release() / setDecode 等基础契约不被破坏，
 * 当用户选择此引擎时，start() 会通过 Intent 调起设备上安装的对应 App（VLC / MX Player / mpvExtended / mpvNova / KMPlayer）。
 * 如果目标 App 未安装，会提示并跳转应用商店/官网。
 */
public class ExternalPlayerEngine extends ExoPlayerEngine {

    private final Type type;
    private boolean started = false;

    /** 第三方 App 包名定义（免费版为主，找不到时尝试备选） */
    public static final String PKG_VLC = "org.videolan.vlc";
    public static final String PKG_VLC_BETA = "org.videolan.vlc.betav7neon";
    public static final String PKG_MX_FREE = "com.mxtech.videoplayer.ad";
    public static final String PKG_MX_PRO = "com.mxtech.videoplayer.pro";
    public static final String PKG_MPVEX = "is.xyz.mpvex";
    public static final String PKG_MPVNOVA = "com.github.prayagp.mpv";
    public static final String PKG_KMP = "com.kmplayer";
    public static final String PKG_KMP_TV = "com.kmplayer.tv";

    public ExternalPlayerEngine(int decode, Type type, Player.Listener listener) {
        super(decode, listener);
        this.type = type;
    }

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public void start(PlaySpec spec, long startPositionMs) {
        if (started) return;
        started = true;
        try {
            String url = spec == null ? null : spec.getUrl();
            if (TextUtils.isEmpty(url)) {
                fireError("播放地址为空");
                return;
            }
            String[] candidates = resolvePackageCandidates(type);
            String pkg = findInstalled(candidates);
            CharSequence label = getDisplayLabel(type);
            if (pkg == null) {
                Notify.show(String.format(ResUtil.getString(R.string.external_player_not_installed), label));
                openInstallPage(candidates[0]);
                fireError("未安装 " + label);
                return;
            }
            launchExternal(pkg, url, spec == null ? null : spec.getHeaders(), startPositionMs, spec == null ? null : spec.getTitle());
            // 已经交给外部播放，通知 UI 层结束本地播放（避免进度条卡 0%）
            try {
                App.post(() -> {
                    try {
                        listener.onPlaybackStateChanged(Player.STATE_ENDED);
                        listener.onIsPlayingChanged(false);
                    } catch (Throwable ignored) {}
                });
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            fireError(t.getMessage());
        }
    }

    private void fireError(String msg) {
        started = false;
        final PlaybackException ex = new PlaybackException("External: " + (msg == null ? "未知错误" : msg), null, PlaybackException.ERROR_CODE_REMOTE_ERROR);
        try {
            listener.onPlayerError(ex);
        } catch (Throwable ignored) {}
    }

    /** 根据引擎类型返回候选包名列表（优先免费版/官方版，找不到再用备选） */
    public static String[] resolvePackageCandidates(Type type) {
        switch (type) {
            case VLC: return new String[]{PKG_VLC, PKG_VLC_BETA};
            case MX: return new String[]{PKG_MX_FREE, PKG_MX_PRO};
            case MPVEX: return new String[]{PKG_MPVEX};
            case MPVNOVA: return new String[]{PKG_MPVNOVA};
            case KMP: return new String[]{PKG_KMP, PKG_KMP_TV};
            default: return new String[0];
        }
    }

    /** 返回第一个已安装的包名，未找到返回 null */
    public static String findInstalled(String[] pkgs) {
        if (pkgs == null) return null;
        PackageManager pm = App.get().getPackageManager();
        for (String pkg : pkgs) {
            try {
                pm.getPackageInfo(pkg, 0);
                return pkg;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    /** 包名 -> 显示名 */
    public static CharSequence getDisplayLabel(Type type) {
        switch (type) {
            case VLC: return "VLC";
            case MX: return "MX Player";
            case MPVEX: return "mpvExtended";
            case MPVNOVA: return "mpvNova";
            case KMP: return "KMPlayer";
            default: return "External";
        }
    }

    /** 跳转到应用商店或浏览器下载页 */
    private void openInstallPage(String pkg) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            App.get().startActivity(i);
        } catch (Throwable t1) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + pkg));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                App.get().startActivity(i);
            } catch (Throwable ignored) {}
        }
    }

    /** 调用外部播放器：优先用包名指定 App，支持传递标题、HTTP Headers、起始位置 */
    private void launchExternal(String pkg, String url, Map<String, String> headers, long positionMs, CharSequence title) {
        try {
            Uri data = (url.startsWith("file://") || url.startsWith("/")) ? FileUtil.getShareUri(url) : Uri.parse(url);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setPackage(pkg);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setDataAndType(data, "video/*");
            if (!TextUtils.isEmpty(title)) {
                intent.putExtra("title", title);
                intent.putExtra("name", title);
            }
            if (positionMs > 0) {
                intent.putExtra("position", (int) positionMs);
            }
            if (headers != null && !headers.isEmpty()) {
                List<String> list = new ArrayList<>();
                headers.forEach((k, v) -> { list.add(k); list.add(v); });
                intent.putExtra("headers", list.toArray(String[]::new));
                Bundle bundle = PlayerHelper.toBundle(headers);
                intent.putExtra("extra_headers", bundle);
            }
            intent.putExtra("return_result", true);
            intent.putExtra("from", App.get().getPackageName());
            try {
                App.get().startActivity(intent);
                return;
            } catch (Throwable noActivity) {
                Intent fallback = new Intent(Intent.ACTION_VIEW);
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                fallback.setDataAndType(data, "video/*");
                if (!TextUtils.isEmpty(title)) fallback.putExtra("title", title);
                App.get().startActivity(Intent.createChooser(fallback, null));
            }
        } catch (Throwable t) {
            throw new RuntimeException("启动外部播放器失败: " + t.getMessage(), t);
        }
    }
}
