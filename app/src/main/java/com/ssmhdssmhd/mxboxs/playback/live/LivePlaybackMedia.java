package com.ssmhdssmhd.mxboxs.playback.live;

import androidx.media3.common.MediaMetadata;

import com.ssmhdssmhd.mxboxs.bean.Channel;
import com.ssmhdssmhd.mxboxs.player.PlayerManager;

public final class LivePlaybackMedia {

    public static MediaMetadata metadata(Channel channel, CharSequence artist) {
        String title = channel == null ? "" : channel.getShow();
        String logo = channel == null ? "" : channel.getLogo();
        return PlayerManager.buildMetadata(title, artist == null ? "" : artist.toString(), logo);
    }
}
