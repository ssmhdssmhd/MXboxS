package com.ssmhdssmhd.mxboxs.player.extractor;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.ssmhdssmhd.mxboxs.App;
import com.ssmhdssmhd.mxboxs.R;
import com.ssmhdssmhd.mxboxs.api.config.LiveConfig;
import com.ssmhdssmhd.mxboxs.bean.Core;
import com.ssmhdssmhd.mxboxs.exception.ExtractException;
import com.ssmhdssmhd.mxboxs.setting.LiveSetting;
import com.ssmhdssmhd.mxboxs.utils.Download;
import com.ssmhdssmhd.mxboxs.utils.ResUtil;
import com.ssmhdssmhd.mxboxs.utils.UrlUtil;
import com.github.catvod.utils.Path;
import com.google.gson.JsonObject;
import com.orhanobut.logger.Logger;
import com.tvbus.engine.Listener;
import com.tvbus.engine.TVCore;

import java.io.File;
import java.util.concurrent.CountDownLatch;

public class TVBus implements Source.Extractor, Listener {

    private static final String TAG = TVBus.class.getSimpleName();
    private CountDownLatch latch;
    private TVCore tvcore;
    private String hls;
    private Core core;

    @Override
    public boolean match(Uri uri) {
        return "tvbus".equals(UrlUtil.scheme(uri));
    }

    private void init(Core core) {
        try {
            App.get().setHook(core.getHook());
            tvcore = new TVCore(getPath(core.getSo())).listener(this).auth(core.getAuth()).name(core.getName()).pass(core.getPass()).domain(core.getDomain()).broker(core.getBroker());
            for (Core.Option option : core.getOption()) tvcore.option(option.getKey(), option.getValues());
            tvcore.serv(0).play(8902).mode(1).init();
        } catch (Exception ignored) {
        } finally {
            App.get().setHook(null);
        }
    }

    private String getPath(String url) {
        File so = new File(Path.so(), UrlUtil.path(url));
        if (!Path.exists(so)) Download.create(url, so).get();
        return so.getAbsolutePath();
    }

    @Override
    public String fetch(String url) throws Exception {
        Core c = LiveConfig.get().getHome().getCore();
        if (core != null && !core.equals(c)) change();
        if (tvcore == null) init(core = c);
        latch = new CountDownLatch(1);
        tvcore.start(url);
        latch.await();
        return check();
    }

    private void change() throws Exception {
        LiveSetting.putBoot(true);
        // 优雅重启：先提示用户，再用 PendingIntent 重启 App，避免硬闪退
        App.post(() -> {
            try {
                android.widget.Toast.makeText(App.get(),
                    "TVBus 核心已切换，正在重启应用...", android.widget.Toast.LENGTH_SHORT).show();
            } catch (Throwable ignored) {}
            // 延迟 1.5 秒让 Toast 显示，再重启
            App.post(() -> {
                try {
                    Context ctx = App.get();
                    Intent intent = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        PendingIntent pi = PendingIntent.getActivity(ctx, 0, intent,
                            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                        pi.send();
                    }
                } catch (Throwable ignored) {}
                System.exit(0);
            }, 1500);
        }, 100);
        throw new ExtractException(ResUtil.getString(R.string.error_play_url));
    }

    private String check() throws Exception {
        if (hls == null) return "";
        if (!hls.startsWith("-")) return hls;
        throw new ExtractException(ResUtil.getString(R.string.error_play_tvbus, hls));
    }

    @Override
    public void stop() {
        if (tvcore != null) tvcore.stop();
        hls = null;
    }

    @Override
    public void exit() {
        if (tvcore != null) tvcore.stop();
        hls = null;
    }

    @Override
    public void onPrepared(String result) {
        Logger.t(TAG).d(result);
        JsonObject json = App.gson().fromJson(result, JsonObject.class);
        if (json.get("hls") == null) return;
        hls = json.get("hls").getAsString();
        latch.countDown();
    }

    @Override
    public void onStop(String result) {
        Logger.t(TAG).d(result);
        JsonObject json = App.gson().fromJson(result, JsonObject.class);
        hls = json.get("errno").getAsString();
        if (hls.startsWith("-")) latch.countDown();
    }

    @Override
    public void onInited(String result) {
        Logger.t(TAG).d(result);
    }

    @Override
    public void onStart(String result) {
        Logger.t(TAG).d(result);
    }

    @Override
    public void onInfo(String result) {
    }

    @Override
    public void onQuit(String result) {
    }
}
