package com.ssmhdssmhd.mxboxs.player.media;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;

import com.ssmhdssmhd.mxboxs.bean.Drm;
import com.ssmhdssmhd.mxboxs.bean.Sub;
import com.ssmhdssmhd.mxboxs.player.track.LangUtil;
import com.ssmhdssmhd.mxboxs.player.util.PlayerHelper;

import java.util.ArrayList;
import java.util.List;

public final class MediaItemFactory {

    public static MediaItem from(PlaySpec spec) {
        return buildUpon(spec).build();
    }

    public static MediaItem from(PlaySpec spec, int decode) {
        return buildUpon(spec).build();
    }

    private static MediaItem.Builder buildUpon(PlaySpec spec) {
        String mimeType = resolveMimeType(spec);
        return new MediaItem.Builder().setUri(spec.getUri())
                .setSubtitleConfigurations(buildSubtitleConfigs(spec.getSubs()))
                .setDrmConfiguration(buildDrmConfig(spec.getDrm()))
                .setRequestMetadata(buildRequestMetadata(spec))
                .setMediaMetadata(spec.getMetadata())
                .setMimeType(mimeType)
                .setImageDurationMs(15000)
                .setMediaId(spec.getKey());
    }

    private static String resolveMimeType(PlaySpec spec) {
        String format = spec.getFormat();
        if (format != null && !format.isEmpty()) return format;
        String url = spec.getUrl();
        if (url == null) return null;
        String lowerUrl = url.toLowerCase();
        // 1) 基于扩展名的识别（带 query/fragment 的常见直播 URL）
        if (hasExt(lowerUrl, ".m3u8") || hasExt(lowerUrl, ".m3u") || lowerUrl.contains(".m3u8?") || lowerUrl.contains(".m3u8#")) {
            return MimeTypes.APPLICATION_M3U8;
        }
        if (hasExt(lowerUrl, ".mpd") || lowerUrl.contains(".mpd?") || lowerUrl.contains(".mpd#")) {
            return MimeTypes.APPLICATION_MPD;
        }
        if (hasExt(lowerUrl, ".mp4")) {
            return MimeTypes.VIDEO_MP4;
        }
        if (hasExt(lowerUrl, ".mkv")) {
            return MimeTypes.VIDEO_MATROSKA;
        }
        if (hasExt(lowerUrl, ".webm")) {
            return MimeTypes.VIDEO_WEBM;
        }
        if (hasExt(lowerUrl, ".ts") || lowerUrl.contains(".ts?")) {
            return MimeTypes.VIDEO_MPEG;
        }
        // 2) 直播路径特征识别（很多源没有扩展名，但路径带 live/playlist/stream 等关键字）
        if (isLikelyHls(lowerUrl)) return MimeTypes.APPLICATION_M3U8;
        if (isLikelyDash(lowerUrl)) return MimeTypes.APPLICATION_MPD;
        // 3) rtsp/rtmp 交给 Media3 内部 RTSP/RTMP Source 处理
        if (lowerUrl.startsWith("rtsp://") || lowerUrl.startsWith("rtmp://")) {
            return null;
        }
        // 4) http(s) 直播兜底：没有扩展名但像是直播流（路径带 /live、.tv、iptv、cctv、hdtv、直播等关键字）
        if (lowerUrl.startsWith("http://") || lowerUrl.startsWith("https://")) {
            if (isLikelyLiveStream(lowerUrl)) return MimeTypes.APPLICATION_M3U8;
        }
        return null;
    }

    private static boolean hasExt(String url, String ext) {
        int i = url.lastIndexOf('?');
        int f = url.lastIndexOf('#');
        int end = Math.min(i < 0 ? url.length() : i, f < 0 ? url.length() : f);
        int dot = url.lastIndexOf('.', end);
        if (dot < 0) return false;
        return url.substring(dot, end).equalsIgnoreCase(ext);
    }

    private static boolean isLikelyHls(String url) {
        // .m3u/.m3u8 的不同变种（如带 index_、playlist_、live 前缀）
        return url.contains(".m3u8") || url.contains(".m3u")
                || url.contains("mime=m3u8") || url.contains("format=m3u8")
                || url.contains("type=m3u8") || url.contains("m3u8=");
    }

    private static boolean isLikelyDash(String url) {
        return url.contains(".mpd") || url.contains("mime=mpd") || url.contains("format=mpd");
    }

    private static boolean isLikelyLiveStream(String url) {
        // 常见直播路径关键字：/live/、/stream/、/playlist、/hls/、cctv、hdtv、iptv 等
        return url.contains("/live") || url.contains("live/")
                || url.contains("/stream") || url.contains("stream/")
                || url.contains("/playlist") || url.contains("playlist/")
                || url.contains("/hls") || url.contains("hls/")
                || url.contains(".tv/")
                || url.contains("cctv") || url.contains("hdtv") || url.contains("iptv")
                || url.contains("直播") || url.contains("频道");
    }

    private static MediaItem.RequestMetadata buildRequestMetadata(PlaySpec spec) {
        return new MediaItem.RequestMetadata.Builder().setMediaUri(spec.getUri()).setExtras(PlayerHelper.toBundle(spec.getHeaders())).build();
    }

    private static List<MediaItem.SubtitleConfiguration> buildSubtitleConfigs(List<Sub> subs) {
        List<MediaItem.SubtitleConfiguration> configs = new ArrayList<>();
        if (subs == null || subs.isEmpty()) return configs;
        SubtitleFlags flags = SubtitleFlags.create(subs);
        for (int i = 0; i < subs.size(); i++) configs.add(buildSubConfig(subs.get(i), flags.get(subs.get(i), i)));
        return configs;
    }

    public static MediaItem.SubtitleConfiguration buildSubConfig(Sub sub) {
        return buildSubConfig(sub, sub.getFlag());
    }

    private static MediaItem.SubtitleConfiguration buildSubConfig(Sub sub, int flag) {
        return new MediaItem.SubtitleConfiguration.Builder(sub.getUri()).setLabel(sub.getName()).setMimeType(sub.getFormat()).setSelectionFlags(flag).setLanguage(sub.getLang()).build();
    }

    private static int findPreferredSubtitleIndex(List<Sub> subs) {
        int bestIndex = C.INDEX_UNSET;
        int bestScore = 0;
        for (int i = 0; i < subs.size(); i++) {
            int score = LangUtil.getPreferredTextLanguageScore(subs.get(i).getLang());
            if (score > bestScore) {
                bestIndex = i;
                bestScore = score;
            }
        }
        return bestIndex;
    }

    private static MediaItem.DrmConfiguration buildDrmConfig(Drm drm) {
        return drm == null ? null : new MediaItem.DrmConfiguration.Builder(drm.getUUID()).setMultiSession(!C.CLEARKEY_UUID.equals(drm.getUUID())).setForceDefaultLicenseUri(drm.isForceKey()).setLicenseRequestHeaders(drm.getHeader()).setLicenseUri(drm.getKey()).build();
    }

    private record SubtitleFlags(boolean hasExplicitFlags, int defaultIndex) {

        static SubtitleFlags create(List<Sub> subs) {
            if (subs.size() == 1) return new SubtitleFlags(false, C.INDEX_UNSET);
            if (hasExplicitFlags(subs)) return new SubtitleFlags(true, C.INDEX_UNSET);
            int preferredIndex = findPreferredSubtitleIndex(subs);
            return new SubtitleFlags(false, preferredIndex == C.INDEX_UNSET ? 0 : preferredIndex);
        }

        private static boolean hasExplicitFlags(List<Sub> subs) {
            for (Sub sub : subs) if (sub.getRawFlag() != 0) return true;
            return false;
        }

        int get(Sub sub, int index) {
            if (sub.getRawFlag() != 0) return sub.getFlag();
            if (hasExplicitFlags) return C.SELECTION_FLAG_AUTOSELECT;
            if (defaultIndex == C.INDEX_UNSET) return sub.getFlag();
            return index == defaultIndex ? C.SELECTION_FLAG_DEFAULT : C.SELECTION_FLAG_AUTOSELECT;
        }
    }
}
