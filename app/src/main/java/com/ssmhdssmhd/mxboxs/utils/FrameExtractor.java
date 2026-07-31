package com.ssmhdssmhd.mxboxs.utils;

import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FrameExtractor {

    private static final Map<String, MediaMetadataRetriever> retrievers = new ConcurrentHashMap<>();
    private static final Map<String, Long> videoDurations = new ConcurrentHashMap<>();

    public static Bitmap getFrame(String url, long timeUs, int width, int height) {
        try {
            MediaMetadataRetriever retriever = getOrCreateRetriever(url);
            if (retriever == null) return null;
            Bitmap frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (frame == null) return null;
            if (width > 0 && height > 0) {
                Bitmap scaled = Bitmap.createScaledBitmap(frame, width, height, true);
                if (scaled != frame) frame.recycle();
                return scaled;
            }
            return frame;
        } catch (Exception e) {
            return null;
        }
    }

    public static Bitmap getFrame(String url, long positionMs, int width, int height, long durationMs) {
        long timeUs = positionMs * 1000;
        if (durationMs > 0 && positionMs >= durationMs) {
            timeUs = (durationMs - 100) * 1000;
        } else if (positionMs < 0) {
            timeUs = 0;
        }
        return getFrame(url, timeUs, width, height);
    }

    public static synchronized void registerVideo(String url, MediaMetadataRetriever retriever, long durationMs) {
        releaseRetriever(url);
        retrievers.put(url, retriever);
        videoDurations.put(url, durationMs);
    }

    public static synchronized void releaseRetriever(String url) {
        MediaMetadataRetriever old = retrievers.remove(url);
        if (old != null) {
            try {
                old.release();
            } catch (Exception ignored) {
            }
        }
        videoDurations.remove(url);
    }

    public static synchronized void releaseAll() {
        for (Map.Entry<String, MediaMetadataRetriever> entry : retrievers.entrySet()) {
            try {
                entry.getValue().release();
            } catch (Exception ignored) {
            }
        }
        retrievers.clear();
        videoDurations.clear();
    }

    private static MediaMetadataRetriever getOrCreateRetriever(String url) {
        MediaMetadataRetriever existing = retrievers.get(url);
        if (existing != null) {
            try {
                existing.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                return existing;
            } catch (Exception e) {
                releaseRetriever(url);
            }
        }
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(url, new HashMap<>());
            return retriever;
        } catch (Exception e) {
            return null;
        }
    }
}