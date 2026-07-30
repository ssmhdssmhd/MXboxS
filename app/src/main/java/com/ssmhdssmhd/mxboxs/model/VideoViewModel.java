package com.ssmhdssmhd.mxboxs.model;

import com.ssmhdssmhd.mxboxs.playback.vod.VodPlaybackController;
import com.ssmhdssmhd.mxboxs.playback.vod.VodPlaybackHost;
import com.ssmhdssmhd.mxboxs.playback.vod.VodPlaybackState;

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
