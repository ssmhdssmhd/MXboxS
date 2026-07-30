package com.ssmhdssmhd.android.tv.playback.vod;

import androidx.media3.common.MediaMetadata;

import com.ssmhdssmhd.android.tv.api.DanmakuApi;
import com.ssmhdssmhd.android.tv.bean.Danmaku;
import com.ssmhdssmhd.android.tv.bean.Episode;
import com.ssmhdssmhd.android.tv.bean.History;
import com.ssmhdssmhd.android.tv.bean.Result;
import com.ssmhdssmhd.android.tv.player.PlayerManager;
import com.ssmhdssmhd.android.tv.setting.DanmakuSetting;

import java.util.function.Consumer;

public final class VodPlaybackMedia {

    public static MediaMetadata metadata(History history, Episode episode) {
        String title = history.getVodName();
        String name = episode.getName();
        boolean empty = name.isEmpty() || title.equals(name);
        String artist = empty ? "" : name;
        return PlayerManager.buildMetadata(title, artist, history.getVodPic());
    }

    public static void searchDanmaku(Result result, History history, Episode episode, Consumer<Danmaku> set, Consumer<Danmaku> add) {
        if (!DanmakuApi.canSearch()) return;
        DanmakuApi.search(history.getVodName(), episode.getName(), danmaku -> {
            if (DanmakuSetting.isSpiderFirst() && !result.getDanmaku().isEmpty()) add.accept(danmaku);
            else set.accept(danmaku);
        });
    }
}
