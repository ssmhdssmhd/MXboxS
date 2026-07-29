package com.fongmi.android.tv.playback.live;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Epg;
import com.fongmi.android.tv.bean.EpgData;

import java.time.ZoneId;

public final class LiveEpgPolicy {

    @Nullable
    public static EpgData next(Channel channel, ZoneId zoneId) {
        if (channel == null) return null;
        Epg epg = channel.getData(zoneId);
        int current = epg.getInRange();
        int position = epg.getSelected() + 1;
        return position <= current && position > 0 && position < epg.getList().size() ? epg.getList().get(position) : null;
    }

    @Nullable
    public static EpgData current(Channel channel, ZoneId zoneId) {
        if (channel == null) return null;
        Epg epg = channel.getData(zoneId).selected();
        int position = epg.getSelected();
        return position >= 0 && position < epg.getList().size() ? epg.getList().get(position) : null;
    }
}
