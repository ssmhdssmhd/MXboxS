package com.ssmhdssmhd.mxboxs.playback.live;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaMetadata;

import com.ssmhdssmhd.mxboxs.bean.Channel;
import com.ssmhdssmhd.mxboxs.bean.EpgData;
import com.ssmhdssmhd.mxboxs.bean.Group;
import com.ssmhdssmhd.mxboxs.bean.Result;

import java.time.ZoneId;

public interface LivePlaybackHost {

    int getGroupCount();

    int getGroupPosition();

    Group getGroup(int position);

    boolean isPlayerLive();

    boolean hasPlaybackSession();

    boolean isPlaybackServiceReady();

    void restorePlaybackKey(@Nullable String key);

    long getPlayerPosition();

    ZoneId getZoneId();

    void onCatchupRequested();

    void stopPlaybackForRefresh();

    void startPlayback(Result result, long position, MediaMetadata metadata);

    void resetPlaybackForError(String msg);

    void renderGroupSelection(Group group);

    void renderGroupChannels(Group group);

    void renderChannelSelection(Channel channel);

    void renderLineSelection(Channel channel, boolean show);

    void renderEpgSelection(EpgData data);

    void renderPlaybackMetadata(MediaMetadata metadata);

    void showCatchupReady(EpgData data);

    void showProgress();
}
