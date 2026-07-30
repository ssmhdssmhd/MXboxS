package com.ssmhdssmhd.android.tv.player.vlc;

import androidx.media3.common.PlaybackException;

import com.ssmhdssmhd.android.tv.R;
import com.ssmhdssmhd.android.tv.utils.ResUtil;

public class VlcErrorMsgProvider {

    public String get(PlaybackException e) {
        return switch (e.errorCode) {
            case PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> ResUtil.getString(R.string.error_play_vlc_io);
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> ResUtil.getString(R.string.error_play_vlc_network);
            case PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                 PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
                 PlaybackException.ERROR_CODE_DECODING_FAILED -> ResUtil.getString(R.string.error_play_vlc_decode);
            case PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                 PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> ResUtil.getString(R.string.error_play_vlc_format);
            default -> ResUtil.getString(R.string.error_play_vlc_unknown);
        };
    }
}