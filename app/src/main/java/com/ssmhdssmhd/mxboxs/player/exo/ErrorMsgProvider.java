package com.ssmhdssmhd.mxboxs.player.exo;

import androidx.media3.common.PlaybackException;

public class ErrorMsgProvider {

    public String get(PlaybackException e) {
        String msg = switch (e.errorCode) {
            case PlaybackException.ERROR_CODE_TIMEOUT -> "请求超时";
            case PlaybackException.ERROR_CODE_UNSPECIFIED -> "未知错误";
            case PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK -> "运行时校验失败";
            case PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> "输入输出错误";
            case PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> badHttpStatusMsg(e);
            case PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> "非法 HTTP 内容类型";
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "网络连接失败";
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "网络连接超时";
            case PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE -> "读取位置超出范围";
            case PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "播放列表格式错误";
            case PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> "媒体容器格式错误";
            case PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> "不支持的播放列表格式";
            case PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "不支持的媒体容器";
            case PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "解码器初始化失败";
            case PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED -> "解码器查询失败";
            case PlaybackException.ERROR_CODE_DECODING_FAILED -> "解码失败";
            case PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "不支持的编码格式";
            case PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED -> "解码资源被回收";
            case PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> "解码规格超出设备能力";
            case PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED -> "音频轨道初始化失败";
            case PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED -> "音频轨道写入失败";
            case PlaybackException.ERROR_CODE_DRM_UNSPECIFIED -> "DRM 错误";
            case PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR -> "DRM 系统错误";
            case PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR -> "DRM 内容错误";
            case PlaybackException.ERROR_CODE_DRM_DEVICE_REVOKED -> "DRM 设备已吊销";
            case PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED -> "DRM 许可证已过期";
            case PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED -> "DRM 配置失败";
            case PlaybackException.ERROR_CODE_DRM_DISALLOWED_OPERATION -> "DRM 操作被禁止";
            case PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED -> "DRM 许可证获取失败";
            default -> e.getErrorCodeName();
        };
        return msg;
    }

    private String badHttpStatusMsg(PlaybackException e) {
        StringBuilder sb = new StringBuilder("服务器拒绝访问");
        if (e.getCause() != null && e.getCause().getMessage() != null) {
            String cause = e.getCause().getMessage();
            if (cause.contains("403")) sb.append("（403 被禁止）");
            else if (cause.contains("404")) sb.append("（404 未找到）");
            else if (cause.contains("500")) sb.append("（500 服务端错误）");
            else if (cause.contains("502")) sb.append("（502 网关错误）");
            else if (cause.contains("503")) sb.append("（503 服务不可用）");
            else if (cause.contains("504")) sb.append("（504 网关超时）");
            else if (cause.contains("429")) sb.append("（429 频率过高）");
        }
        sb.append("，请尝试切换解析或启用 AI 智能解析");
        return sb.toString();
    }
}
