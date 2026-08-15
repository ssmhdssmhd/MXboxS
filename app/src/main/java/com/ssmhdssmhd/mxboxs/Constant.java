package com.ssmhdssmhd.mxboxs;

import java.util.concurrent.TimeUnit;

public class Constant {

    public static final long INTERVAL_SEEK = TimeUnit.SECONDS.toMillis(10);
    public static final long INTERVAL_HIDE = TimeUnit.SECONDS.toMillis(5);
    public static final long TIMEOUT_VOD = TimeUnit.SECONDS.toMillis(30);
    public static final long TIMEOUT_LIVE = TimeUnit.SECONDS.toMillis(30);
    public static final long TIMEOUT_EPG = TimeUnit.SECONDS.toMillis(5);
    public static final long TIMEOUT_XML = TimeUnit.SECONDS.toMillis(15);
    // 起播超时：点播 25s（之前 15s 容易误报慢源），直播 20s；
    // 配合 LoadControl 的 bufferForPlayback 阈值，缓冲阶段不算"卡住"。
    public static final long TIMEOUT_PLAY = TimeUnit.SECONDS.toMillis(25);
    public static final long TIMEOUT_PLAY_LIVE = TimeUnit.SECONDS.toMillis(20);
    public static final long TIMEOUT_SYNC = TimeUnit.SECONDS.toMillis(2);
    // 搜索每站超时：12s（之前 30s 太长，用户感知"搜不出"）；
    // 再配合全局快速搜索早停，首批命中后 UI 先渲染。
    public static final long TIMEOUT_SEARCH = TimeUnit.SECONDS.toMillis(12);
    // 解析超时：默认(非WebView) 15s；WebView 45s（原 15s 对 xmflv.cc 这种带混淆JS+多段noscdn脚本的万能嗅探站不够，
    // 没等 JS 跑完 Xmflv 初始化播放器就触发 stop(error) → 永远拿不到 m3u8 → 表现为 0 KB/s 转圈）
    // Live WebView 保持 15s（直播一般不会用混淆JS 接口）
    public static final long TIMEOUT_PARSE_DEF = TimeUnit.SECONDS.toMillis(15);
    public static final long TIMEOUT_PARSE_WEB = TimeUnit.SECONDS.toMillis(45);
    public static final long TIMEOUT_PARSE_LIVE = TimeUnit.SECONDS.toMillis(10);
    public static final long HISTORY_TIME = TimeUnit.DAYS.toMillis(60);

    public static long getOpEdLimit(long duration) {
        if (duration < TimeUnit.MINUTES.toMillis(15)) return TimeUnit.MINUTES.toMillis(3);
        if (duration < TimeUnit.MINUTES.toMillis(30)) return TimeUnit.MINUTES.toMillis(6);
        return TimeUnit.MINUTES.toMillis(10);
    }
}
