package com.fongmi.android.tv.playback;

import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.player.PlayerManager;

public final class PlaybackReset {

    public static void afterError(PlayerManager player) {
        afterError(player, null);
    }

    public static void afterError(PlayerManager player, Runnable beforeReset) {
        Track.delete(player.getKey());
        if (beforeReset != null) beforeReset.run();
        player.resetTrack();
        player.reset();
        player.stop();
    }
}
