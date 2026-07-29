package com.fongmi.android.tv.playback.vod;

import android.text.TextUtils;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.Result;

public final class VodPlayRequest {

    private final String key;
    private final String flag;
    private final String id;
    private final String title;

    private VodPlayRequest(String key, String flag, String id, String title) {
        this.key = key == null ? "" : key;
        this.flag = flag == null ? "" : flag;
        this.id = id == null ? "" : id;
        this.title = title == null ? "" : title;
    }

    public static VodPlayRequest create(String key, Flag flag, Episode episode) {
        return new VodPlayRequest(key, flag.getFlag(), episode.getUrl(), episode.getName());
    }

    public String getKey() {
        return key;
    }

    public String getFlag() {
        return flag;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public boolean matches(String key, Flag flag, Episode episode) {
        return TextUtils.equals(this.key, key) && flag != null && episode != null && TextUtils.equals(this.flag, flag.getFlag()) && TextUtils.equals(this.id, episode.getUrl());
    }

    public boolean accepts(Result result) {
        return result != null && (result.getFlag().isEmpty() || TextUtils.equals(flag, result.getFlag()));
    }
}
