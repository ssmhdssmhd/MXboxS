package com.ssmhdssmhd.mxboxs.playback;

import com.ssmhdssmhd.mxboxs.bean.Result;

public record PlaybackResult<T>(T request, Result result) {
}
