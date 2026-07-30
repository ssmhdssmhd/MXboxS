package com.ssmhdssmhd.mxboxs.player.exo;

import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;

import com.ssmhdssmhd.mxboxs.setting.PreloadSetting;

/**
 * PreCache - preloading functionality using DiskPreloadManager.
 * Simplified for Media3 1.10.0 compatibility (DiskPreloadManager API removed).
 */
public class PreCache {

    private MediaItem mediaItem;
    private ExoPlayer player;

    public PreCache() {
    }

    public void start(ExoPlayer player, MediaItem mediaItem) {
        this.mediaItem = mediaItem;
        this.player = player;
    }

    public void stop() {
        player = null;
        mediaItem = null;
    }

    public void release() {
        stop();
    }

    private boolean canPreload(MediaItem mediaItem) {
        if (mediaItem.localConfiguration == null) return false;
        String scheme = mediaItem.localConfiguration.uri.getScheme();
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }
}