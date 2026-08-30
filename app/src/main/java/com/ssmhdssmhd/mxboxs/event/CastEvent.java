package com.ssmhdssmhd.mxboxs.event;

import com.ssmhdssmhd.mxboxs.bean.Config;
import com.ssmhdssmhd.mxboxs.bean.Device;
import com.ssmhdssmhd.mxboxs.bean.History;

import org.greenrobot.eventbus.EventBus;

public record CastEvent(Config config, Device device, History history) {

    public static void post(Config config, Device device, History history) {
        EventBus.getDefault().post(new CastEvent(config, device, history));
    }
}
