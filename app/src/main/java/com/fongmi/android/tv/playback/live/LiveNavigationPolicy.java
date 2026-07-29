package com.fongmi.android.tv.playback.live;

import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.setting.LiveSetting;

class LiveNavigationPolicy {

    private final LivePlaybackController controller;
    private final LivePlaybackState state;
    private final LivePlaybackHost host;

    LiveNavigationPolicy(LivePlaybackController controller, LivePlaybackState state, LivePlaybackHost host) {
        this.controller = controller;
        this.state = state;
        this.host = host;
    }

    void moveChannel(int delta) {
        Group group = state.getGroup();
        if (group == null || group.isEmpty()) return;
        int size = group.getChannel().size();
        int position = group.getPosition() + delta;
        boolean limit = position < 0 || position >= size;
        if (LiveSetting.isAcross() && limit) moveGroup(delta);
        else group.setPosition(limit ? wrap(position, size) : position);
        if (state.getGroup() != null && !state.getGroup().isEmpty()) controller.selectChannel(state.getGroup().current());
    }

    private void moveGroup(int delta) {
        int count = host.getGroupCount();
        if (count <= 1) return;
        Group current = state.getGroup();
        int position = host.getGroupPosition();
        for (int i = 0; i < count; i++) {
            position = wrap(position + delta, count);
            Group target = host.getGroup(position);
            if (target.equals(current)) return;
            if (target.skip()) continue;
            state.setGroup(target);
            host.renderGroupSelection(target);
            host.renderGroupChannels(target);
            target.setPosition(delta > 0 ? 0 : target.getChannel().size() - 1);
            return;
        }
    }

    private int wrap(int position, int size) {
        return ((position % size) + size) % size;
    }
}
