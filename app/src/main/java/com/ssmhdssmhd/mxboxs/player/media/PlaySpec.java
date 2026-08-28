package com.ssmhdssmhd.mxboxs.player.media;

import android.net.Uri;

import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;

import com.ssmhdssmhd.mxboxs.bean.Danmaku;
import com.ssmhdssmhd.mxboxs.bean.Drm;
import com.ssmhdssmhd.mxboxs.bean.Result;
import com.ssmhdssmhd.mxboxs.bean.Sub;
import com.ssmhdssmhd.mxboxs.player.util.PlayerHelper;
import com.ssmhdssmhd.mxboxs.setting.Setting;
import com.ssmhdssmhd.mxboxs.utils.UrlUtil;
import com.google.common.net.HttpHeaders;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlaySpec {

    private Map<String, String> headers;
    private List<Danmaku> danmakus;
    private MediaMetadata metadata;
    private List<Sub> subs;
    private String format;
    private String key;
    private String url;
    private Drm drm;

    private PlaySpec(String key, String url, Map<String, String> headers, String format, Drm drm, List<Sub> subs, List<Danmaku> danmakus, MediaMetadata metadata) {
        this.key = key;
        this.url = url;
        this.drm = drm;
        this.subs = subs;
        this.format = format;
        this.headers = headers;
        this.danmakus = danmakus;
        this.metadata = metadata;
    }

    public static PlaySpec from(String key, String url, Map<String, String> headers, MediaMetadata metadata) {
        return new PlaySpec(key, url, headers, null, null, null, null, metadata);
    }

    public static PlaySpec from(Result result, String key, MediaMetadata metadata) {
        return new PlaySpec(key, result.getRealUrl(), result.getHeader(), result.getFormat(), result.getDrm(), result.getSubs(), result.getDanmaku(), metadata);
    }

    public static PlaySpec fromParse(Result result, String key, MediaMetadata metadata) {
        return new PlaySpec(key, null, null, result.getFormat(), result.getDrm(), result.getSubs(), result.getDanmaku(), metadata);
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Uri getUri() {
        return UrlUtil.uri(url);
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public Drm getDrm() {
        return drm;
    }

    public List<Sub> getSubs() {
        return subs;
    }

    public List<Danmaku> getDanmakus() {
        return danmakus;
    }

    public MediaMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(MediaMetadata metadata) {
        this.metadata = metadata;
    }

    public PlaySpec checkUa() {
        if (headers == null) headers = new HashMap<>();
        // UA 优先级链：
        //   1) headers 里已有 UA（解析器精确指定的，不覆盖）
        //   2) Setting.getUa() 用户自定义 UA
        //   3) UrlUtil.pickUaByUrl() 按当前播放 URL 场景从 UA 池选（手机/TV/PC/引擎）
        //      → 兜底不会再走到 ExoPlayer 默认 UA（ExoPlayer/xxx 格式被多数 CDN 拒绝）
        if (headers.keySet().stream().noneMatch(HttpHeaders.USER_AGENT::equalsIgnoreCase)) {
            String ua = Setting.getUa();
            if (ua == null || ua.isEmpty()) {
                ua = com.ssmhdssmhd.mxboxs.utils.UrlUtil.pickUaByUrl(url);
            }
            headers.put(HttpHeaders.USER_AGENT, ua);
        }
        return this;
    }

    public void setSub(Sub sub) {
        if (subs == null) subs = new ArrayList<>();
        if (sub == null) return;
        subs.remove(sub);
        clearForcedSubtitles();
        subs.add(0, sub);
    }

    private void clearForcedSubtitles() {
        for (Sub sub : subs) if (sub.isForced()) sub.setFlag(C.SELECTION_FLAG_AUTOSELECT);
    }

    public void setDanmaku(Danmaku item) {
        if (danmakus == null) danmakus = new ArrayList<>();
        if (!item.isEmpty() && !danmakus.contains(item)) danmakus.add(0, item);
        danmakus.forEach(danmaku -> danmaku.setSelected(danmaku.getUrl().equals(item.getUrl())));
    }

    public void addDanmaku(Danmaku item) {
        if (danmakus == null) danmakus = new ArrayList<>();
        if (item.isEmpty() || danmakus.contains(item)) return;
        danmakus.add(item);
    }
}
