package com.ssmhdssmhd.android.tv.event;

import com.ssmhdssmhd.android.tv.bean.Config;
import com.ssmhdssmhd.android.tv.bean.Device;
import com.ssmhdssmhd.android.tv.bean.History;

import org.greenrobot.eventbus.EventBus;

public record CastEvent(Config config, Device device, History history) {

    public static void post(Config config, Device device, History history) {
        EventBus.getDefault().post(new CastEvent(config, device, history));
    }
}
