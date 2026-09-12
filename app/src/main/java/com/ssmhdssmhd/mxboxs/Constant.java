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
    // 解析超时：默认(非WebView) 45s；WebView 45s
    // v5.7.20 修复：原 15s 对「服务端解析接口」（如 http://114.134.184.91:8080/api/jx/server?url=）不够——
    // 该接口解析官方站点（优酷/爱奇艺/腾讯等）实测要 19~31s（大头在服务端抓取+官替解析），
    // 15s 一到 ParseJob.execute 就 cancel → OkHttp 抛 IOException → onParseError → 表现为"解析失败/连接超时"无法播放。
    // 提到 45s 后与 WebView 解析对齐；jsonParse 单次 HTTP 调用也用 45s 客户端（见 ParseJob.jsonParse），
    // 保证慢源在总超时之前能完整拿到返回。Live WebView 保持 10s（直播一般不会用慢解析接口）
    public static final long TIMEOUT_PARSE_DEF = TimeUnit.SECONDS.toMillis(45);
    public static final long TIMEOUT_PARSE_WEB = TimeUnit.SECONDS.toMillis(45);
    public static final long TIMEOUT_PARSE_LIVE = TimeUnit.SECONDS.toMillis(10);
    public static final long HISTORY_TIME = TimeUnit.DAYS.toMillis(60);

    public static long getOpEdLimit(long duration) {
        if (duration < TimeUnit.MINUTES.toMillis(15)) return TimeUnit.MINUTES.toMillis(3);
        if (duration < TimeUnit.MINUTES.toMillis(30)) return TimeUnit.MINUTES.toMillis(6);
        return TimeUnit.MINUTES.toMillis(10);
    }
}
