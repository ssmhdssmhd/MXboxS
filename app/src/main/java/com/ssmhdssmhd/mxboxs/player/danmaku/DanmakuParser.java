package com.ssmhdssmhd.mxboxs.player.danmaku;

import android.text.TextUtils;

import com.ssmhdssmhd.mxboxs.api.DanmakuApi;
import com.ssmhdssmhd.mxboxs.bean.Danmaku;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Response;

public class DanmakuParser {

    public interface OnDanmakuLoadedListener {
        void onDanmakuLoaded(List<DanmakuEngine.DanmakuItem> items);
        void onDanmakuError(String error);
    }

    public static void loadDanmakuList(String videoName, String episodeName, OnDanmakuLoadedListener listener) {
        DanmakuApi.search(videoName, episodeName, danmaku -> {
            if (danmaku != null && !danmaku.isEmpty()) {
                loadDanmakuContent(danmaku, listener);
            } else {
                listener.onDanmakuError("No danmaku found");
            }
        });
    }

    public static void loadDanmakuContent(Danmaku danmaku, OnDanmakuLoadedListener listener) {
        if (danmaku == null || danmaku.isEmpty()) {
            listener.onDanmakuError("Empty danmaku");
            return;
        }
        new Thread(() -> {
            try {
                Response response = OkHttp.newCall(danmaku.getUrl()).execute();
                if (response.isSuccessful() && response.body() != null) {
                    String content = response.body().string();
                    List<DanmakuEngine.DanmakuItem> items = parseDanmakuContent(content, danmaku.getUrl());
                    if (items != null && !items.isEmpty()) {
                        listener.onDanmakuLoaded(items);
                    } else {
                        listener.onDanmakuError("No danmaku items parsed");
                    }
                } else {
                    listener.onDanmakuError("Failed to load danmaku");
                }
            } catch (Exception e) {
                listener.onDanmakuError(e.getMessage());
            }
        }).start();
    }

    private static List<DanmakuEngine.DanmakuItem> parseDanmakuContent(String content, String url) {
        if (TextUtils.isEmpty(content)) return null;
        if (url.endsWith(".xml") || url.contains("xml") || content.trim().startsWith("<")) {
            return parseXmlDanmaku(content);
        } else if (url.endsWith(".json") || url.contains("json") || content.trim().startsWith("{")) {
            return parseJsonDanmaku(content);
        }
        return parseAutoDanmaku(content);
    }

    private static List<DanmakuEngine.DanmakuItem> parseXmlDanmaku(String xml) {
        List<DanmakuEngine.DanmakuItem> items = new ArrayList<>();
        try {
            Pattern pattern = Pattern.compile("<d[^>]*p=\"([^\"]*)\"[^>]*>([^<]*)</d>");
            Matcher matcher = pattern.matcher(xml);
            while (matcher.find()) {
                String p = matcher.group(1);
                String text = matcher.group(2);
                if (TextUtils.isEmpty(text)) continue;
                try {
                    long time = (long) (Float.parseFloat(p) * 1000);
                    items.add(new DanmakuEngine.DanmakuItem(time, text, DanmakuEngine.TYPE_SCROLL, DanmakuEngine.COLOR_WHITE, 1.0f, false));
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
        }
        return items;
    }

    private static List<DanmakuEngine.DanmakuItem> parseJsonDanmaku(String json) {
        List<DanmakuEngine.DanmakuItem> items = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(json);
            JSONArray danmakuArray = null;
            if (root.has("danmaku")) {
                danmakuArray = root.getJSONArray("danmaku");
            } else if (root.has("items")) {
                danmakuArray = root.getJSONArray("items");
            } else if (root.has("list")) {
                danmakuArray = root.getJSONArray("list");
            } else if (json.startsWith("[")) {
                danmakuArray = new JSONArray(json);
            }
            if (danmakuArray != null) {
                for (int i = 0; i < danmakuArray.length(); i++) {
                    JSONObject obj = danmakuArray.getJSONObject(i);
                    long time = obj.optLong("time", obj.optLong("t", 0));
                    String text = obj.optString("text", obj.optString("content", ""));
                    int type = obj.optInt("type", obj.optInt("mode", DanmakuEngine.TYPE_SCROLL));
                    int color = obj.optInt("color", DanmakuEngine.COLOR_WHITE);
                    items.add(new DanmakuEngine.DanmakuItem(time, text, type, color, 1.0f, false));
                }
            }
        } catch (Exception e) {
        }
        return items;
    }

    private static List<DanmakuEngine.DanmakuItem> parseAutoDanmaku(String content) {
        List<DanmakuEngine.DanmakuItem> items = new ArrayList<>();
        if (content.trim().startsWith("[")) {
            try {
                JSONArray array = new JSONArray(content);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    long time = obj.optLong("time", obj.optLong("t", 0));
                    String text = obj.optString("text", obj.optString("content", ""));
                    if (!TextUtils.isEmpty(text)) {
                        int type = obj.optInt("type", DanmakuEngine.TYPE_SCROLL);
                        int color = obj.optInt("color", DanmakuEngine.COLOR_WHITE);
                        items.add(new DanmakuEngine.DanmakuItem(time, text, type, color, 1.0f, false));
                    }
                }
                if (!items.isEmpty()) return items;
            } catch (Exception ignored) {
            }
        }
        Pattern xmlPattern = Pattern.compile("<d[^>]*p=\"([^\"]*)\"[^>]*>([^<]*)</d>");
        Matcher xmlMatcher = xmlPattern.matcher(content);
        while (xmlMatcher.find()) {
            String p = xmlMatcher.group(1);
            String text = xmlMatcher.group(2);
            if (TextUtils.isEmpty(text)) continue;
            try {
                long time = (long) (Float.parseFloat(p) * 1000);
                items.add(new DanmakuEngine.DanmakuItem(time, text, DanmakuEngine.TYPE_SCROLL, DanmakuEngine.COLOR_WHITE, 1.0f, false));
            } catch (Exception ignored) {
            }
        }
        return items;
    }
}
