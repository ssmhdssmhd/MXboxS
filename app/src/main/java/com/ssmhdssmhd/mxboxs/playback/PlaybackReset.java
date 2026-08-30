package com.ssmhdssmhd.mxboxs.playback;

import com.ssmhdssmhd.mxboxs.bean.Track;
import com.ssmhdssmhd.mxboxs.player.PlayerManager;

public final class PlaybackReset {

    public static void afterError(PlayerManager player) {
        afterError(player, null);
    }

    public static void afterError(PlayerManager player, Runnable beforeReset) {
        if (beforeReset != null) beforeReset.run();
        Track.delete(player.getKey());
        player.resetTrack();
        player.reset();
        player.stop();
    }
}
