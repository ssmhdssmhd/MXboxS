package com.ssmhdssmhd.android.tv.model;

import com.ssmhdssmhd.android.tv.playback.vod.VodPlaybackController;
import com.ssmhdssmhd.android.tv.playback.vod.VodPlaybackHost;
import com.ssmhdssmhd.android.tv.playback.vod.VodPlaybackState;

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
