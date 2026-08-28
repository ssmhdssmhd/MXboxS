package com.ssmhdssmhd.mxboxs;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.startup.Initializer;

import com.ssmhdssmhd.mxboxs.setting.Setting;
import com.ssmhdssmhd.mxboxs.ui.activity.CrashActivity;
import com.github.catvod.bean.Doh;
import com.github.catvod.net.OkHttp;
import com.orhanobut.logger.AndroidLogAdapter;
import com.orhanobut.logger.Logger;
import com.orhanobut.logger.PrettyFormatStrategy;

import org.greenrobot.eventbus.EventBus;

import java.util.Collections;
import java.util.List;

import cat.ereza.customactivityoncrash.config.CaocConfig;

public class Startup implements Initializer<Void> {

    @NonNull
    @Override
    public Void create(@NonNull Context context) {
        CaocConfig.Builder.create().trackActivities(true).backgroundMode(CaocConfig.BACKGROUND_MODE_SILENT).errorActivity(CrashActivity.class).apply();
        Logger.addLogAdapter(new AndroidLogAdapter(PrettyFormatStrategy.newBuilder().methodCount(0).showThreadInfo(false).tag("TV").build()));
        // EventBus：项目里没有 @Subscriber 注解，EventBus annotation processor 不会生成 EventIndex，
        // 所以不能用 addIndex(new EventIndex()) —— 直接走运行时反射模式即可（速度稍慢但零维护成本）。
        try {
            EventBus.builder().installDefaultEventBus();
        } catch (Exception ignored) {
        }
        OkHttp.dns().setDoh(Doh.objectFrom(Setting.getDoh()));
        return null;
    }

    @NonNull
    @Override
    public List<Class<? extends Initializer<?>>> dependencies() {
        return Collections.emptyList();
    }
}
