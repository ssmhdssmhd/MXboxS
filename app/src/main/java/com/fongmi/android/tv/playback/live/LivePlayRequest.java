package com.fongmi.android.tv.playback.live;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.EpgData;

public final class LivePlayRequest {

    private final Channel channel;
    private final EpgData data;
    private final long position;
    private final int line;

    private LivePlayRequest(@NonNull Channel channel, @Nullable EpgData data, long position) {
        this.channel = channel;
        this.data = data;
        this.position = position;
        this.line = channel.getIndex();
    }

    public static LivePlayRequest live(@NonNull Channel channel, long position) {
        return new LivePlayRequest(channel, null, position);
    }

    public static LivePlayRequest catchup(@NonNull Channel channel, @NonNull EpgData data, long position) {
        return new LivePlayRequest(channel, data, position);
    }

    public Channel getChannel() {
        return channel;
    }

    @NonNull
    public EpgData getCatchupData() {
        if (data == null) throw new IllegalStateException("Not a catchup request");
        return data;
    }

    public long getPosition() {
        return position;
    }

    public boolean matches(@Nullable Channel current) {
        return channel.equals(current) && line == current.getIndex();
    }
}
