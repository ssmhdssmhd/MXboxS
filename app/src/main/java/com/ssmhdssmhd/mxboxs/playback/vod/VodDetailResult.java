package com.ssmhdssmhd.mxboxs.playback.vod;

import android.text.TextUtils;

import com.ssmhdssmhd.mxboxs.bean.Result;

public record VodDetailResult(String key, String id, Result result) {

    public boolean matches(String key, String id) {
        return TextUtils.equals(this.key, key) && TextUtils.equals(this.id, id);
    }
}
