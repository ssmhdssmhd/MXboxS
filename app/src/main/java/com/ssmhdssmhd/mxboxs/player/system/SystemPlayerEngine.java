package com.ssmhdssmhd.mxboxs.player.system;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.view.SurfaceHolder;

import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;

import com.ssmhdssmhd.mxboxs.App;
import com.ssmhdssmhd.mxboxs.bean.Sub;
import com.ssmhdssmhd.mxboxs.player.engine.PlayerEngine;
import com.ssmhdssmhd.mxboxs.player.media.PlaySpec;

import java.io.IOException;

/**
 * 基于 Android 系统 MediaPlayer 的播放器引擎。
 * 主要作为兼容性备选，适用于不支持 ExoPlayer/MPV 的极端场景。
 */
public class SystemPlayerEngine implements PlayerEngine {

    private final Player.Listener listener;
    private MediaPlayer mediaPlayer;
    private boolean isPrepared = false;

    public SystemPlayerEngine(int decode, Player.Listener listener) {
        this.listener = listener;
    }

    @Override
    public Type getType() {
        return Type.SYSTEM;
    }

    @Override
    public Player getPlayer() {
        // SystemPlayerEngine 不使用 Media3 的 Player 实例，返回 null
        // 外部调用 getPlayer() 的地方需要注意这个区别
        return null;
    }

    @Override
    public void release() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception ignored) {
            }
            mediaPlayer = null;
        }
        isPrepared = false;
    }

    @Override
    public Player rebuild() {
        release();
        return null;
    }

    @Override
    public boolean setDecode(int decode) {
        // 系统播放器无法切换硬解/软解
        return false;
    }

    @Override
    public void start(PlaySpec spec, long startPositionMs) {
        release();
        try {
            mediaPlayer = new MediaPlayer();
            Context context = App.get();
            Uri uri = Uri.parse(spec.getUrl());
            mediaPlayer.setDataSource(context, uri);
            
            mediaPlayer.setOnPreparedListener(mp -> {
                isPrepared = true;
                mp.start();
                if (startPositionMs > 0) {
                    mp.seekTo((int) startPositionMs);
                }
                listener.onIsPlayingChanged(true);
                listener.onPlaybackStateChanged(Player.STATE_READY);
            });
            
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                listener.onPlayerError(new PlaybackException("System player error: " + what, null));
                return true;
            });
            
            mediaPlayer.setOnCompletionListener(mp -> {
                listener.onPlaybackStateChanged(Player.STATE_ENDED);
                listener.onIsPlayingChanged(false);
            });
            
            mediaPlayer.setOnSeekCompleteListener(mp -> {
                listener.onPlaybackStateChanged(Player.STATE_READY);
            });

            mediaPlayer.prepareAsync();
            listener.onPlaybackStateChanged(Player.STATE_BUFFERING);
        } catch (IOException e) {
            listener.onPlayerError(new PlaybackException("Failed to start system player", e));
        } catch (Exception e) {
            listener.onPlayerError(new PlaybackException("System player error", e));
        }
    }

    @Override
    public void stop() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
                listener.onIsPlayingChanged(false);
                listener.onPlaybackStateChanged(Player.STATE_IDLE);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public boolean isLive() {
        // 简单假设：时长小于5分钟的认为是直播
        if (mediaPlayer != null && isPrepared) {
            try {
                return mediaPlayer.getDuration() <= 300000;
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    @Override
    public boolean isVod() {
        return !isLive();
    }

    @Override
    public String getErrorMessage(PlaybackException e) {
        return "系统播放器错误: " + (e.getMessage() != null ? e.getMessage() : "未知错误");
    }

    @Override
    public ErrorAction handleError(PlaybackException e) {
        return ErrorAction.FATAL;
    }

    public void setDisplay(SurfaceHolder holder) {
        if (mediaPlayer != null) {
            mediaPlayer.setDisplay(holder);
        }
    }

    public void setSurface(android.view.Surface surface) {
        if (mediaPlayer != null) {
            mediaPlayer.setSurface(surface);
        }
    }
}
