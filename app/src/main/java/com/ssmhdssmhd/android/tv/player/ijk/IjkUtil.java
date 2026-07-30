package com.ssmhdssmhd.android.tv.player.ijk;

import tv.danmaku.ijk.media.player.IjkMediaPlayer;

public class IjkUtil {

    public static boolean isAvailable() {
        try {
            IjkMediaPlayer player = new IjkMediaPlayer();
            player.release();
            return true;
        } catch (Throwable e) {
            return false;
        }
    }
}