package com.fongmi.android.tv.playback.live;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.Result;

public class LivePlaybackState {

    private LivePlayRequest pendingRequest;
    private Result result;
    private Channel channel;
    private Group group;

    public void reset() {
        pendingRequest = null;
        channel = null;
        result = null;
        group = null;
    }

    public Group getGroup() {
        return group;
    }

    public void setGroup(Group group) {
        this.group = group;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
        this.group = channel != null ? channel.getGroup() : group;
    }

    @Nullable
    public Result getResult() {
        return result;
    }

    public void setResult(Result result) {
        this.result = result;
    }

    @Nullable
    public LivePlayRequest getPendingRequest() {
        return pendingRequest;
    }

    public void setPendingRequest(LivePlayRequest pendingRequest) {
        this.pendingRequest = pendingRequest;
    }

    public void clearPendingRequest() {
        this.pendingRequest = null;
    }
}
