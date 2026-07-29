package com.fongmi.android.tv.player.exo;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PriorityTaskManager;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.preload.DiskPreloadManager;

import com.fongmi.android.tv.setting.PreloadSetting;

public class PreCache {

    private final PriorityTaskManager priorityTaskManager;
    private DiskPreloadManager manager;
    private MediaItem mediaItem;
    private ExoPlayer player;

    public PreCache() {
        this.priorityTaskManager = new PriorityTaskManager();
    }

    public void start(ExoPlayer player, MediaItem mediaItem) {
        this.mediaItem = mediaItem;
        this.player = player;
        restart();
    }

    public void stop() {
        stopManager();
        player = null;
        mediaItem = null;
    }

    public void release() {
        stop();
    }

    private void restart() {
        stopManager();
        if (player == null || mediaItem == null) return;
        if (!PreloadSetting.isPreload()) return;
        if (!canPreload(mediaItem)) return;
        manager = createManager(mediaItem);
        player.setPriorityTaskManager(priorityTaskManager);
        manager.start(player, mediaItem, createOptions());
    }

    private void stopManager() {
        if (manager == null) return;
        manager.release();
        manager = null;
        if (player != null) player.setPriorityTaskManager(null);
    }

    private DiskPreloadManager createManager(MediaItem mediaItem) {
        return new DiskPreloadManager.Builder(MediaSourceFactory.getCache(), MediaSourceFactory.createUpstreamDataSourceFactory(ExoUtil.extractHeaders(mediaItem)), ExoUtil.buildRenderersFactory()).setPriorityTaskManager(priorityTaskManager).build();
    }

    private DiskPreloadManager.Options createOptions() {
        return DiskPreloadManager.Options.builder().setDurationMs(PreloadSetting.getPreloadDurationMs()).setMaxThreads(PreloadSetting.getPreloadThreads()).build();
    }

    private boolean canPreload(MediaItem mediaItem) {
        if (mediaItem.localConfiguration == null) return false;
        String scheme = mediaItem.localConfiguration.uri.getScheme();
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }
}
