package com.ssmhdssmhd.mxboxs.playback.live;

import com.ssmhdssmhd.mxboxs.bean.Channel;
import com.ssmhdssmhd.mxboxs.bean.EpgData;
import com.ssmhdssmhd.mxboxs.setting.LiveSetting;

class LiveFallbackPolicy {

    private final LivePlaybackController controller;
    private final LivePlaybackState state;
    private final LivePlaybackHost host;

    LiveFallbackPolicy(LivePlaybackController controller, LivePlaybackState state, LivePlaybackHost host) {
        this.controller = controller;
        this.state = state;
        this.host = host;
    }

    void playbackError() {
        Channel channel = state.getChannel();
        if (!LiveSetting.isChange() || channel == null) return;
        // 任何线路出错都尝试切换到下一条源（不再用 isLast 限制，即便只有一条也让 UI 有机会重试）
        host.renderLineSelection(channel, true);
        controller.nextLine(true);
    }

    void playbackEnded() {
        if (host.isPlayerLive()) checkNext();
        else controller.nextChannel();
    }

    private void checkNext() {
        Channel channel = state.getChannel();
        if (channel == null) return;
        EpgData data = host.getNextEpgData(channel);
        if (data != null && controller.selectEpg(data)) return;
        data = host.getCurrentEpgData(channel);
        if (data != null) host.renderEpgSelection(channel, data);
        controller.refresh();
    }
}
