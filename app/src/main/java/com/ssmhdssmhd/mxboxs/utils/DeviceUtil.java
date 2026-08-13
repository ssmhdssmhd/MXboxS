package com.ssmhdssmhd.mxboxs.utils;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Build;

import com.ssmhdssmhd.mxboxs.App;

/**
 * 设备 / 网络 / 电量状态工具：供 AI 预解析、AB 灰度面板等使用。
 * 无静态缓存，每次实时查系统状态（调用频率低，够用）。
 */
public final class DeviceUtil {

    private DeviceUtil() {}

    /** 是否 Wi-Fi 网络（非蜂窝数据）。 */
    public static boolean isWifiConnected() {
        try {
            ConnectivityManager cm = (ConnectivityManager) App.get().getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            Network net = cm.getActiveNetwork();
            if (net == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(net);
            if (caps == null) return false;
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    && !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false;
            return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 当前电量百分比 0-100（不插电）。插充电时返回 100 视为电量充足。 */
    public static int batteryPercent() {
        try {
            IntentFilter f = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent b = App.get().registerReceiver(null, f);
            if (b == null) return 50;
            int plugged = b.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
            if (plugged > 0) return 100;  // 插充电：视为充足
            int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level < 0 || scale <= 0) return 50;
            return (int) Math.floor(level * 100.0 / scale);
        } catch (Throwable ignored) {
            return 50;
        }
    }

    /** AI 省流量/省电判定：是否允许后台预解析 / 预加载弹幕。 */
    public static boolean allowBackgroundPreload() {
        if (!isWifiConnected()) return false;
        return batteryPercent() >= 30;
    }

    public static String modelName() {
        return Build.MODEL == null ? "" : Build.MODEL;
    }
}
