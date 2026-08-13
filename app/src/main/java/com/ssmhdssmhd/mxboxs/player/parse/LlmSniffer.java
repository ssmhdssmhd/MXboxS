package com.ssmhdssmhd.mxboxs.player.parse;

import android.text.TextUtils;

import com.ssmhdssmhd.mxboxs.setting.PlayerSetting;
import com.github.catvod.net.OkHttp;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM 嗅探接口骨架：常规正则嗅探全部失败后，把页面 HTML/JS 片段喂给 LLM，
 * 让模型直接输出候选播放地址 JSON。
 * <p>
 * 设计为"可选云端 endpoint"：用户在高级设置里配置 LLM API 地址 + Key，
 * 不配则跳过（{@link #isAvailable()} 返回 false），不影响现有解析流程。
 * <p>
 * 协议约定（POST JSON）：
 * <pre>
 * 请求: {"html": "...4KB 片段...", "hint": "m3u8/mp4/playUrl"}
 * 响应: {"candidates": ["https://...", "https://..."]}
 * </pre>
 * 兼容 OpenAI Chat Completions 格式（如果 endpoint 含 /v1/chat/completions）：
 * 系统提示词要求模型只输出 JSON 数组。
 */
public final class LlmSniffer {

    private static final int TIMEOUT_MS = 8_000;
    private static final int MAX_HTML_LEN = 4096;

    /** LLM 是否可用（endpoint + key 都配了才 true）。 */
    public static boolean isAvailable() {
        return !TextUtils.isEmpty(PlayerSetting.getLlmEndpoint()) && !TextUtils.isEmpty(PlayerSetting.getLlmKey());
    }

    /**
     * 用 LLM 从 HTML/JS 片段中嗅探候选播放地址。
     *
     * @param html 页面 HTML 或 JS 文本片段（会截断到 4KB）
     * @return 候选 URL 列表（可能为空；null 表示调用失败/不可用）
     */
    public static List<String> sniff(String html) {
        if (!isAvailable() || TextUtils.isEmpty(html)) return null;
        String endpoint = PlayerSetting.getLlmEndpoint();
        String key = PlayerSetting.getLlmKey();
        String payload = buildPayload(html.length() > MAX_HTML_LEN ? html.substring(0, MAX_HTML_LEN) : html);
        try {
            okhttp3.RequestBody body = okhttp3.RequestBody.create(payload,
                    okhttp3.MediaType.parse("application/json"));
            okhttp3.Request req = new okhttp3.Request.Builder()
                    .url(endpoint)
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build();
            okhttp3.Response resp = OkHttp.client().newCall(req).execute();
            String respBody = resp.body() != null ? resp.body().string() : "";
            resp.close();
            return parseCandidates(respBody, endpoint);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 构造请求 JSON：自动适配 OpenAI Chat / 自定义两种格式。 */
    private static String buildPayload(String html) {
        JsonObject obj = new JsonObject();
        String endpoint = PlayerSetting.getLlmEndpoint();
        if (endpoint.contains("/v1/chat/completions") || endpoint.contains("/chat/completions")) {
            // OpenAI Chat Completions 格式
            obj.addProperty("model", PlayerSetting.getLlmModel().isEmpty() ? "gpt-4o-mini" : PlayerSetting.getLlmModel());
            JsonArray messages = new JsonArray();
            JsonObject sys = new JsonObject();
            sys.addProperty("role", "system");
            sys.addProperty("content", "你是一个视频地址嗅探助手。从给定的 HTML/JS 片段中提取视频播放地址（m3u8/mp4/whip）。"
                    + "只返回 JSON 数组，如 [\"https://...\"], 不要任何其他文字。如果没有找到返回 []。");
            JsonObject user = new JsonObject();
            user.addProperty("role", "user");
            user.addProperty("content", html);
            messages.add(sys);
            messages.add(user);
            obj.add("messages", messages);
            obj.addProperty("temperature", 0.1);
        } else {
            // 自定义格式
            obj.addProperty("html", html);
            obj.addProperty("hint", "m3u8,mp4,playUrl,video_url");
        }
        return obj.toString();
    }

    /** 从响应中提取候选 URL 列表。 */
    private static List<String> parseCandidates(String respBody, String endpoint) {
        if (TextUtils.isEmpty(respBody)) return new ArrayList<>();
        try {
            JsonObject root = JsonParser.parseString(respBody).getAsJsonObject();
            // OpenAI 格式：choices[0].message.content
            if (root.has("choices")) {
                JsonArray choices = root.getAsJsonArray("choices");
                if (choices.size() > 0) {
                    JsonObject msg = choices.get(0).getAsJsonObject().getAsJsonObject("message");
                    String content = msg.get("content").getAsString().trim();
                    // content 可能是 ["url1","url2"] 或纯文本
                    return extractUrlsFromText(content);
                }
            }
            // 自定义格式：{"candidates": [...]}
            if (root.has("candidates")) {
                List<String> urls = new ArrayList<>();
                for (JsonElement e : root.getAsJsonArray("candidates")) {
                    String u = e.getAsString().trim();
                    if (u.startsWith("http")) urls.add(u);
                }
                return urls;
            }
        } catch (Throwable ignored) {}
        return new ArrayList<>();
    }

    /** 从文本中提取 http(s) 开头的 URL。 */
    private static List<String> extractUrlsFromText(String text) {
        List<String> urls = new ArrayList<>();
        // 先尝试整体当 JSON 数组解析
        try {
            JsonArray arr = JsonParser.parseString(text).getAsJsonArray();
            for (JsonElement e : arr) {
                String u = e.getAsString().trim();
                if (u.startsWith("http")) urls.add(u);
            }
            return urls;
        } catch (Throwable ignored) {}
        // 回退：正则提取
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("https?://[^\\s\"'<>]+").matcher(text);
        while (m.find()) urls.add(m.group());
        return urls;
    }
}
