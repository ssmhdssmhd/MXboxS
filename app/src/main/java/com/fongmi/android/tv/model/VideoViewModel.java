package com.fongmi.android.tv.model;

import com.fongmi.android.tv.playback.vod.VodPlaybackController;
import com.fongmi.android.tv.playback.vod.VodPlaybackHost;
import com.fongmi.android.tv.playback.vod.VodPlaybackState;

public class VideoViewModel extends SiteViewModel {

    private final VodPlaybackState playbackState;

    public VideoViewModel() {
        playbackState = new VodPlaybackState();
    }

    public VodPlaybackController createPlaybackController(VodPlaybackHost host) {
        return new VodPlaybackController(host, playbackState);
    }

    @Override
    protected void onCleared() {
        playbackState.reset();
        super.onCleared();
    }
}
