package com.ssmhdssmhd.mxboxs.player.engine;

import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;

import com.ssmhdssmhd.mxboxs.bean.Sub;
import com.ssmhdssmhd.mxboxs.player.media.PlaySpec;

public interface PlayerEngine {

    int SOFT = 0;
    int HARD = 1;

    Type getType();

    Player getPlayer();

    void release();

    Player rebuild();

    boolean setDecode(int decode);

    void start(PlaySpec spec, long startPositionMs);

    default void stop() {
        getPlayer().stop();
    }

    boolean isLive();

    boolean isVod();

    default void setSubtitleStyle() {
    }

    default boolean addSubtitle(Sub sub) {
        return false;
    }

    String getErrorMessage(PlaybackException e);

    ErrorAction handleError(PlaybackException e);

    enum ErrorAction {
        RECOVERED,
        DECODE,
        FATAL
    }

    enum Type {
        EXO,
        MPV,
        SYSTEM
    }
}
