package com.ssmhdssmhd.mxboxs.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;

import com.ssmhdssmhd.mxboxs.App;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FrameExtractor {

    private static final Map<String, MediaMetadataRetriever> retrievers = new ConcurrentHashMap<>();
    private static final Map<String, Object> urlLocks = new ConcurrentHashMap<>();

    /** 兼容旧签名：不带 headers。 */
    public static Bitmap getFrame(String url, long timeUs, int width, int height) {
        return getFrame(url, timeUs, width, height, null);
    }

    public static Bitmap getFrame(String url, long timeUs, int width, int height, Map<String, String> headers) {
        if (url == null || url.isEmpty()) return null;
        Object lock = urlLocks.computeIfAbsent(url, k -> new Object());
        // MediaMetadataRetriever 非线程安全：同一 URL 的并发取帧会崩溃/返回 null，必须串行化。
        synchronized (lock) {
            try {
                MediaMetadataRetriever retriever = retrievers.get(url);
                if (retriever == null) {
                    retriever = createRetriever(url, headers);
                    if (retriever == null) return null;
                    retrievers.put(url, retriever);
                }
                Bitmap frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                if (frame == null) return null;
                if (width > 0 && height > 0) {
                    Bitmap scaled = Bitmap.createScaledBitmap(frame, width, height, true);
                    if (scaled != frame) frame.recycle();
                    return scaled;
                }
                return frame;
            } catch (Exception e) {
                // 取帧异常（源失效/被释放）→ 释放缓存，下次重建
                releaseRetriever(url);
                return null;
            }
        }
    }

    /** 兼容旧签名：不带 headers。 */
    public static Bitmap getFrame(String url, long positionMs, int width, int height, long durationMs) {
        return getFrame(url, positionMs, width, height, durationMs, null);
    }

    /**
     * v5.7.22 修复：m3u8(HLS) 播放地址 MediaMetadataRetriever 根本不支持（setDataSource 抛异常），
     * 旧实现导致缩略图永远空白；现在：
     *   1) HLS 直接走内置 FFmpeg 取帧；
     *   2) 渐进式源先用 MMR（带 UA/Referer），失败再 FFmpeg 兜底。
     */
    public static Bitmap getFrame(String url, long positionMs, int width, int height, long durationMs, Map<String, String> headers) {
        if (url == null || url.isEmpty()) return null;
        long timeUs = positionMs * 1000;
        if (durationMs > 0 && positionMs >= durationMs) {
            timeUs = (durationMs - 100) * 1000;
        } else if (positionMs < 0) {
            timeUs = 0;
        }
        if (isHls(url)) return getFrameByFFmpeg(url, positionMs, width, height, headers);
        Bitmap frame = getFrame(url, timeUs, width, height, headers);
        if (frame != null) return frame;
        return getFrameByFFmpeg(url, positionMs, width, height, headers);
    }

    private static boolean isHls(String url) {
        String lc = url.toLowerCase();
        return lc.contains(".m3u8") || lc.contains("m3u8?") || lc.contains("playlist");
    }

    /**
     * 用内置 FFmpeg 从视频/HLS 流提取一帧缩略图（写入临时文件再解码）。
     * 支持 -headers 传 UA/Referer，兼容反盗链源；单次最长 15s，失败返回 null。
     */
    private static Bitmap getFrameByFFmpeg(String url, long positionMs, int width, int height, Map<String, String> headers) {
        File out = null;
        try {
            Context ctx = App.get();
            if (ctx == null || !FFmpegUtil.isReady(ctx)) return null;
            out = File.createTempFile("thumb", ".jpg", ctx.getCacheDir());
            List<String> args = new ArrayList<>();
            args.add("-y");
            args.add("-ss");
            args.add(String.valueOf(Math.max(0, positionMs) / 1000.0));
            String headerBlock = buildFFmpegHeaders(headers);
            if (!headerBlock.isEmpty()) {
                args.add("-headers");
                args.add(headerBlock);
            }
            args.add("-i");
            args.add(url);
            args.add("-frames:v");
            args.add("1");
            if (width > 0 && height > 0) {
                args.add("-vf");
                args.add("scale=" + width + ":" + height);
            }
            args.add("-f");
            args.add("image2");
            args.add(out.getAbsolutePath());
            FFmpegUtil.Result res = FFmpegUtil.ffmpeg(ctx, args, null, 15000);
            if (res == null || !res.success() || !out.exists() || out.length() == 0) return null;
            return BitmapFactory.decodeFile(out.getAbsolutePath());
        } catch (Throwable t) {
            return null;
        } finally {
            if (out != null) try { out.delete(); } catch (Throwable ignored) {}
        }
    }

    /** ffmpeg -headers 要求 "Key1: val1\r\nKey2: val2" 格式。 */
    private static String buildFFmpegHeaders(Map<String, String> headers) {
        StringBuilder sb = new StringBuilder();
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                if (e.getKey() == null || e.getValue() == null || e.getValue().isEmpty()) continue;
                if (sb.length() > 0) sb.append("\r\n");
                sb.append(e.getKey()).append(": ").append(e.getValue());
            }
        }
        return sb.toString();
    }

    private static MediaMetadataRetriever createRetriever(String url, Map<String, String> headers) {
        try {
            Map<String, String> h = new HashMap<>();
            if (headers != null) {
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    if (e.getKey() == null || e.getValue() == null) continue;
                    h.put(UrlUtil.fixHeader(e.getKey()), e.getValue());
                }
            }
            if (!h.containsKey("User-Agent")) h.put("User-Agent", UrlUtil.defaultUA());
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(url, h);
            return retriever;
        } catch (Exception e) {
            return null;
        }
    }

    public static synchronized void registerVideo(String url, MediaMetadataRetriever retriever, long durationMs) {
        releaseRetriever(url);
        retrievers.put(url, retriever);
    }

    public static synchronized void releaseRetriever(String url) {
        MediaMetadataRetriever old = retrievers.remove(url);
        if (old != null) {
            try {
                old.release();
            } catch (Exception ignored) {
            }
        }
    }

    public static synchronized void releaseAll() {
        for (Map.Entry<String, MediaMetadataRetriever> entry : retrievers.entrySet()) {
            try {
                entry.getValue().release();
            } catch (Exception ignored) {
            }
        }
        retrievers.clear();
        urlLocks.clear();
    }
}
