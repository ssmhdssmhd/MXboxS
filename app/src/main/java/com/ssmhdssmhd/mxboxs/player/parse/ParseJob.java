package com.ssmhdssmhd.mxboxs.player.parse;

import android.text.TextUtils;

import com.ssmhdssmhd.mxboxs.App;
import com.ssmhdssmhd.mxboxs.Constant;
import com.ssmhdssmhd.mxboxs.api.config.VodConfig;
import com.ssmhdssmhd.mxboxs.api.loader.BaseLoader;
import com.ssmhdssmhd.mxboxs.bean.Parse;
import com.ssmhdssmhd.mxboxs.bean.Result;
import com.ssmhdssmhd.mxboxs.impl.ParseCallback;
import com.ssmhdssmhd.mxboxs.server.Server;
import com.ssmhdssmhd.mxboxs.setting.Setting;
import com.ssmhdssmhd.mxboxs.ui.custom.CustomWebView;
import com.ssmhdssmhd.mxboxs.utils.Task;
import com.ssmhdssmhd.mxboxs.utils.UrlUtil;
import com.ssmhdssmhd.mxboxs.utils.WebViewUtil;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Util;
import com.google.common.net.HttpHeaders;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Headers;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ParseJob implements ParseCallback {

    private final AtomicBoolean done = new AtomicBoolean();
    private final List<CustomWebView> webViews;
    private final List<Future<?>> futures;
    private ExecutorService executor;
    private ExecutorService infinite;
    private ParseCallback callback;
    private Parse parse;

    private ParseJob(ParseCallback callback) {
        this.executor = Executors.newSingleThreadExecutor();
        this.infinite = Executors.newCachedThreadPool();
        this.webViews = new ArrayList<>();
        this.futures = new ArrayList<>();
        this.callback = callback;
    }

    public static ParseJob create(ParseCallback callback) {
        return new ParseJob(callback);
    }

    public ParseJob start(Result result, boolean useParse) {
        setParse(result, useParse);
        execute(result);
        return this;
    }

    private void setParse(Result result, boolean useParse) {
        if (useParse) parse = VodConfig.get().getParse();
        if (result.getPlayUrl().startsWith("json:")) parse = Parse.get(1, result.getPlayUrl().substring(5));
        if (result.getPlayUrl().startsWith("parse:")) parse = VodConfig.get().getParse(result.getPlayUrl().substring(6));
        if (parse == null || parse.isEmpty()) parse = Parse.get(0, result.getPlayUrl());
        parse.setHeader(result.getHeader());
        parse.setClick(getClick(result));
    }

    private String getClick(Result result) {
        String click = VodConfig.get().getSite(result.getKey()).getClick();
        if (!TextUtils.isEmpty(click)) return click;
        return result.getClick();
    }

    private void execute(Result result) {
        Future<?> task = executor.submit(getTask(result));
        Task.schedule(() -> {
            if (task.cancel(true)) onParseError();
        }, Constant.TIMEOUT_PARSE_DEF, TimeUnit.MILLISECONDS);
    }

    private Runnable getTask(Result result) {
        return () -> {
            try {
                doInBackground(result.getKey(), result.getUrl().v(), result.getFlag());
            } catch (Throwable e) {
                onParseError();
            }
        };
    }

    private void doInBackground(String key, String webUrl, String flag) throws Throwable {
        switch (parse.getType()) {
            case 0:
                startWeb(key, parse, webUrl);
                break;
            case 1:
                jsonParse(parse, webUrl, true);
                break;
            case 2:
                jsonExtend(webUrl);
                break;
            case 3:
                jsonMix(webUrl, flag);
                break;
            case 4:
                superParse(webUrl, flag);
                break;
            case 5:
                builtinParse(webUrl);
                break;
        }
    }

    private void jsonParse(Parse item, String webUrl, boolean fatal) throws Exception {
        Map<String, String> headers = UrlUtil.mergeDefaultHeaders(item.getHeader(), item.getUrl());
        try (Response res = OkHttp.newCall(item.getUrl() + webUrl, headers).execute()) {
            if (!res.isSuccessful() || res.body() == null) {
                if (fatal) onParseError();
                return;
            }
            String raw = res.body().string();
            if (TextUtils.isEmpty(raw)) {
                if (fatal) onParseError();
                return;
            }
            JsonObject object;
            try {
                object = Json.parse(raw).getAsJsonObject();
            } catch (Throwable t) {
                if (fatal) onParseError();
                return;
            }
            String url = Json.safeString(object, "url");
            try {
                JsonObject data = object.getAsJsonObject("data");
                if (url.isEmpty()) url = Json.safeString(data, "url");
            } catch (Throwable ignored) {}
            checkResult(getHeader(object), url, item.getName(), fatal);
        }
    }

    private void jsonExtend(String webUrl) throws Throwable {
        LinkedHashMap<String, String> jxs = new LinkedHashMap<>();
        for (Parse item : VodConfig.get().getParses()) if (item.getType() == 1) jxs.put(item.getName(), item.extUrl());
        checkResult(Result.fromObject(BaseLoader.get().jsonExt(parse.getUrl(), jxs, webUrl)));
    }

    private void jsonMix(String webUrl, String flag) throws Throwable {
        LinkedHashMap<String, HashMap<String, String>> jxs = new LinkedHashMap<>();
        for (Parse item : VodConfig.get().getParses()) jxs.put(item.getName(), item.mixMap());
        checkResult(Result.fromObject(BaseLoader.get().jsonExtMix(flag, parse.getUrl(), parse.getName(), jxs, webUrl)));
    }

    /**
     * 超级解析（AI 自动识别然后解析）：
     * 不再走第三方 JSON 解析站、WebView 嗅探、或 qcb/xt 超级嗅探接口，
     * 直接调用 AI 启发式自动识别链路 aiSmartParseFallback：
     *   1) webUrl 本身是视频直链 → 可达性 probe 后直接播放；
     *   2) 否则 HTTP 抓正文，正则扫常见视频 URL（m3u8/mp4/flv/m4v/ts...）候选，逐个做可达性 probe；
     *   3) 全部不命中时兜底：拿原 URL 做一次 Content-Type probe。
     * 任一路命中 → onParseSuccess 回调；全部失败才 onParseError。
     */
    private void superParse(String webUrl, String flag) throws Exception {
        if (aiSmartParseFallback(webUrl)) return;
        onParseError();
    }

    /**
     * AI 智能解析 fallback：
     * 当传统解析（json / mix / extend / 超级）失败时，
     * 先通过启发式规则嗅探页面中的真实视频 URL（m3u8/mp4/flv...），
     * 若命中则直接用该 URL 播放，不再依赖第三方解析站。
     * 多候选 URL 逐个做轻量校验（HTTP 状态码 / Content-Type），
     * 只要有一个可达即成功回调，显著提升命中率。
     */
    private boolean aiSmartParseFallback(String webUrl) {
        if (done.get()) return true;
        try {
            Map<String, String> baseHeaders = parse != null ? parse.getHeader() : new HashMap<>();
            Map<String, String> headers = UrlUtil.mergeDefaultHeaders(baseHeaders, webUrl);
            // 1) 如果本身就是 m3u8 / mp4 / flv 等直链，先做可达性校验，通过则直接放行
            String lc = webUrl == null ? "" : webUrl.toLowerCase();
            boolean isDirectVideo = lc.endsWith(".m3u8") || lc.contains(".m3u8?")
                    || lc.endsWith(".mp4") || lc.contains(".mp4?")
                    || lc.endsWith(".flv") || lc.contains(".flv?")
                    || lc.endsWith(".m4v") || lc.contains(".m4v?")
                    || lc.endsWith(".ts") || lc.contains(".ts?");
            if (isDirectVideo) {
                if (probeVideoUrl(webUrl, headers)) {
                    onParseSuccess(headers, webUrl, "AI-Direct");
                    return true;
                }
            }
            // 2) 用简单 HTTP GET 抓页面正文，正则扫常见视频 URL，拿 Top 5 候选逐个校验
            String body = safeGetBody(webUrl, headers);
            if (body != null && body.length() > 0) {
                List<String> candidates = UrlUtil.sniffVideoCandidates(body, webUrl, 8,
                        "m3u8", "mp4", "flv", "m4v", "index.m3u8", "playlist.m3u8", "ts");
                if (candidates != null && !candidates.isEmpty()) {
                    for (String cand : candidates) {
                        if (done.get()) return true;
                        if (TextUtils.isEmpty(cand)) continue;
                        Map<String, String> candHeaders = UrlUtil.mergeDefaultHeaders(baseHeaders, cand);
                        if (probeVideoUrl(cand, candHeaders)) {
                            onParseSuccess(candHeaders, cand, "AI-Sniff");
                            return true;
                        }
                    }
                }
            }
            // 3) 即使嗅探没有命中候选，最后也兜底尝试：直接用 webUrl 当直链放一次（Content-Type 校验）
            if (!isDirectVideo && probeVideoUrl(webUrl, headers)) {
                onParseSuccess(headers, webUrl, "AI-Probe");
                return true;
            }
            // 4) 全部失败：返回 false，让上层决定走 onParseError
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 轻量探测一个视频 URL 是否可达：
     * 优先 HEAD（省流量），若 HEAD 被 403/405 则回退到 GET Range:0-0；
     * 要求 2xx 状态码 + Content-Type 非纯 HTML/text，或 Content-Length 明显大于典型 HTML 页。
     */
    private boolean probeVideoUrl(String url, Map<String, String> headers) {
        if (TextUtils.isEmpty(url) || done.get()) return false;
        // 快路径：如果后缀已经明确是视频，跳过探测直接信任（某些站点 HEAD 会被封）
        String lc = url.toLowerCase();
        boolean trustExt = lc.endsWith(".m3u8") || lc.contains(".m3u8?")
                || lc.endsWith(".mp4") || lc.contains(".mp4?")
                || lc.endsWith(".flv") || lc.contains(".flv?")
                || lc.endsWith(".m4v") || lc.contains(".m4v?")
                || lc.endsWith(".ts") || lc.contains(".ts?")
                || lc.endsWith(".mkv") || lc.contains(".mkv?")
                || lc.endsWith(".webm") || lc.contains(".webm?");
        if (trustExt) return true;
        try {
            Response headRes = null;
            try {
                Request.Builder headBuilder = new Request.Builder().url(url).method("HEAD", null);
                if (headers != null && !headers.isEmpty()) headBuilder.headers(Headers.of(headers));
                headRes = OkHttp.client(10000L).newCall(headBuilder.build()).execute();
                if (headRes.isSuccessful() && isVideoLikeResponse(headRes)) return true;
            } catch (Throwable ignored) {
            } finally {
                closeQuietly(headRes);
            }
            // HEAD 不行，回退 GET Range 0-0
            Map<String, String> rangeHeaders = new HashMap<>(headers != null ? headers : new HashMap<>());
            rangeHeaders.put(HttpHeaders.RANGE, "bytes=0-0");
            Request.Builder getBuilder = new Request.Builder().url(url).get();
            if (!rangeHeaders.isEmpty()) getBuilder.headers(Headers.of(rangeHeaders));
            try (Response getRes = OkHttp.client(10000L).newCall(getBuilder.build()).execute()) {
                int code = getRes.code();
                // 200 / 206 都算可用；416 Range Not Satisfiable 但存在资源也 OK
                boolean codeOk = (code >= 200 && code < 300) || code == 416;
                if (!codeOk) return false;
                // 若 416，但没有 Accept-Ranges / Content-Range，也可能是假的
                if (code == 416) {
                    String cr = getRes.header("Content-Range");
                    String ar = getRes.header("Accept-Ranges");
                    if (TextUtils.isEmpty(cr) && TextUtils.isEmpty(ar)) return false;
                }
                return isVideoLikeResponse(getRes) || code == 416;
            }
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isVideoLikeResponse(Response res) {
        if (res == null) return false;
        String ct = res.header(HttpHeaders.CONTENT_TYPE);
        String cl = res.header(HttpHeaders.CONTENT_LENGTH);
        if (!TextUtils.isEmpty(ct)) {
            String lc = ct.toLowerCase();
            if (lc.contains("video") || lc.contains("audio")
                    || lc.contains("mpegurl") || lc.contains("x-mpegurl")
                    || lc.contains("octet-stream") || lc.contains("mp4")
                    || lc.contains("mp2t") || lc.contains("x-flv")
                    || lc.contains("webm") || lc.contains("matroska")) {
                return true;
            }
            // 明确是 HTML / JSON / text 的排除
            if (lc.contains("text/html") || lc.contains("application/json") || lc.contains("text/plain")) {
                return false;
            }
        }
        if (!TextUtils.isEmpty(cl)) {
            try {
                long len = Long.parseLong(cl.trim());
                if (len > 256 * 1024) return true; // > 256KB 基本不可能是错误页面
            } catch (Throwable ignored) {}
        }
        return false;
    }

    private void closeQuietly(Response res) {
        if (res == null) return;
        try { res.close(); } catch (Throwable ignored) {}
    }

    private String safeGetBody(String url, Map<String, String> headers) {
        if (TextUtils.isEmpty(url)) return null;
        Map<String, String> h = UrlUtil.mergeDefaultHeaders(headers, url);
        try (Response res = OkHttp.newCall(url, h).execute()) {
            // 只接受 2xx，拒绝把 403/404/5xx 的错误页面当正文去嗅探
            if (!res.isSuccessful() || res.body() == null) return null;
            // 若 Content-Type 是二进制/视频，没必要当正文去正则，直接跳过
            String ct = res.header(HttpHeaders.CONTENT_TYPE);
            if (!TextUtils.isEmpty(ct)) {
                String lc = ct.toLowerCase();
                if (lc.contains("video") || lc.contains("audio")
                        || lc.contains("octet-stream") || lc.contains("image")) {
                    return null;
                }
            }
            String s = res.body().string();
            if (TextUtils.isEmpty(s)) return null;
            if (s.length() > 1024 * 1024) s = s.substring(0, 1024 * 1024);
            return s;
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * 内置 m3u8/m3u8/mp4 直链解析：
     * 1) 优先走 qcb 原创仓库 jiexi.php（HTTP 实时调用，服务端可独立更新）；
     * 2) 再 AI 智能解析 fallback；
     * 3) 全部失败再走 onParseError。
     */
    private void builtinParse(String webUrl) {
        if (done.get()) return;
        if (hasQcbParseServer() && qcbJiexiParse(webUrl)) return;
        if (aiSmartParseFallback(webUrl)) return;
        onParseError();
    }

    // ========== qcb 原创仓库 jiexi.php + xt/api.php 远程 HTTP 解析 ==========

    private static boolean hasQcbParseServer() {
        String p = Setting.getParseServerPrefix();
        return p != null && !p.isEmpty();
    }

    private static String normalizeQcbPrefix(String p) {
        if (p == null) return "";
        String s = p.trim();
        if (s.isEmpty()) return "";
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    /**
     * 远程调一次 qcb 接口：
     * - path: "/jiexi.php" 或 "/xt/api.php"
     * - 查询参数自动加 type=json（只对 jiexi.php 生效，xt/api.php 忽略）+ url=编码后的 webUrl
     * - 统一 JSON: {code, url, msg, ZT, time, KFZ}
     * - 判定条件：code==200, url 非空，且 (url != 原 webUrl 或 url 本身是 m3u8/mp4 直链)
     *   因为 qcb 官解接口没配置时会把原 URL 原样写回 url，这种情况当失败处理，走 fallback。
     */
    private boolean qcbHttpCall(String path, String fromTag, String webUrl) {
        if (done.get()) return true;
        try {
            String prefix = normalizeQcbPrefix(Setting.getParseServerPrefix());
            if (prefix.isEmpty()) return false;
            String fullUrl = prefix + path + "?type=json&url=" + android.net.Uri.encode(webUrl, "-_.~");
            Map<String, String> baseHeaders = parse != null ? parse.getHeader() : new HashMap<>();
            Map<String, String> headers = UrlUtil.mergeDefaultHeaders(baseHeaders, prefix + "/");
            try (Response res = OkHttp.client(15000L).newCall(new Request.Builder().url(fullUrl).get().headers(Headers.of(headers)).build()).execute()) {
                if (!res.isSuccessful() || res.body() == null) return false;
                String raw = res.body().string();
                if (TextUtils.isEmpty(raw)) return false;
                JsonObject obj;
                try {
                    obj = Json.parse(raw).getAsJsonObject();
                } catch (Throwable t) {
                    return false;
                }
                if (obj == null) return false;
                int code = -1;
                try { code = obj.get("code").getAsInt(); } catch (Throwable ignored) {}
                String url = Json.safeString(obj, "url");
                if (code != 200 || TextUtils.isEmpty(url)) return false;
                String trimmed = url.trim();
                if (trimmed.isEmpty()) return false;
                if (!trimmed.startsWith("http")) return false;
                boolean isSameAsInput = trimmed.equals(webUrl);
                // 对输入 URL 做路径归一化的比较，避免末尾 / 影响判断
                try {
                    String n1 = webUrl == null ? "" : webUrl.trim();
                    String n2 = trimmed;
                    if (n1.endsWith("/")) n1 = n1.substring(0, n1.length() - 1);
                    if (n2.endsWith("/")) n2 = n2.substring(0, n2.length() - 1);
                    if (n1.equals(n2)) isSameAsInput = true;
                } catch (Throwable ignored) {}
                String lc = trimmed.toLowerCase();
                boolean isDirectVideo = lc.endsWith(".m3u8") || lc.contains(".m3u8?")
                        || lc.endsWith(".mp4") || lc.contains(".mp4?")
                        || lc.endsWith(".flv") || lc.contains(".flv?")
                        || lc.endsWith(".m4v") || lc.contains(".m4v?")
                        || lc.endsWith(".ts") || lc.contains(".ts?")
                        || lc.endsWith(".mkv") || lc.contains(".mkv?")
                        || lc.endsWith(".webm") || lc.contains(".webm?");
                if (isSameAsInput && !isDirectVideo) return false;
                Map<String, String> outHeaders = getHeader(obj);
                if (outHeaders == null || outHeaders.isEmpty()) outHeaders = headers;
                onParseSuccess(outHeaders, trimmed, fromTag);
                return true;
            }
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 内置解析走 qcb/jiexi.php（TVBox/影视APP 专用公开接口壳） */
    private boolean qcbJiexiParse(String webUrl) {
        return qcbHttpCall("/jiexi.php", "QCB-jiexi", webUrl);
    }

    /** 超级解析走 qcb 原创仓库的超级嗅探核心 xt/api.php */
    private boolean qcbXtApiParse(String webUrl) {
        return qcbHttpCall("/xt/api.php", "QCB-XT-super", webUrl);
    }

    private void startWeb(CountDownLatch latch, List<Parse> items, String webUrl) {
        StringBuilder sb = new StringBuilder();
        for (Parse item : items) sb.append(item.getUrl()).append(";");
        startWeb(latch, new HashMap<>(), Server.get().getAddress("/parse?jxs=" + Util.substring(sb.toString()) + "&url=" + webUrl));
    }

    private void startWeb(CountDownLatch latch, Map<String, String> headers, String url) {
        startWeb(latch, "", "", headers, url, "");
    }

    private void startWeb(CountDownLatch latch, String key, String from, Map<String, String> headers, String url, String click) {
        if (!WebViewUtil.support()) {
            try { latch.countDown(); } catch (Exception ignored) {}
        } else {
            App.post(() -> {
                CustomWebView webView = CustomWebView.create(App.get()).start(key, from, headers, url, click, new ParseCallback() {
                    @Override
                    public void onParseSuccess(Map<String, String> h, String u, String f) {
                        try { latch.countDown(); } catch (Exception ignored) {}
                        ParseJob.this.onParseSuccess(h, u, f);
                    }

                    @Override
                    public void onParseError() {
                        try { latch.countDown(); } catch (Exception ignored) {}
                        ParseJob.this.onParseError();
                    }
                }, !url.contains("player/?url="));
                webViews.add(webView);
            });
        }
    }

    private void jsonParse(CountDownLatch latch, Parse item, String webUrl) {
        try {
            jsonParse(item, webUrl, false);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            latch.countDown();
        }
    }

    private void checkResult(Map<String, String> headers, String url, String from, boolean fatal) {
        if (url.length() > 40) onParseSuccess(headers, url, from);
        else if (fatal) onParseError();
    }

    private void checkResult(Result result) {
        result.setHeader(parse.getHeader());
        if (result.getUrl().isEmpty()) onParseError();
        else if (result.needParse()) startWeb(result.getHeader(), UrlUtil.convert(result.getUrl().v()));
        else onParseSuccess(result.getHeader(), result.getUrl().v(), result.getJxFrom());
    }

    private void startWeb(List<Parse> items, String webUrl) {
        StringBuilder sb = new StringBuilder();
        for (Parse item : items) sb.append(item.getUrl()).append(";");
        startWeb(new HashMap<>(), Server.get().getAddress("/parse?jxs=" + Util.substring(sb.toString()) + "&url=" + webUrl));
    }

    private void startWeb(String key, Parse item, String webUrl) {
        startWeb(key, item.getName(), item.getHeader(), item.getUrl() + webUrl, item.getClick());
    }

    private void startWeb(Map<String, String> headers, String url) {
        startWeb("", "", headers, url, "");
    }

    private void startWeb(String key, String from, Map<String, String> headers, String url, String click) {
        if (!WebViewUtil.support()) {
            onParseError();
        } else {
            App.post(() -> webViews.add(CustomWebView.create(App.get()).start(key, from, headers, url, click, this, !url.contains("player/?url="))));
        }
    }

    private Map<String, String> getHeader(JsonObject object) {
        Map<String, String> headers = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) if (!entry.getValue().isJsonNull() && (entry.getKey().equalsIgnoreCase(HttpHeaders.USER_AGENT) || entry.getKey().equalsIgnoreCase(HttpHeaders.REFERER) || entry.getKey().equalsIgnoreCase(HttpHeaders.COOKIE) || entry.getKey().equalsIgnoreCase("ua"))) headers.put(UrlUtil.fixHeader(entry.getKey()), entry.getValue().getAsString());
        return headers.isEmpty() ? parse.getHeader() : headers;
    }

    @Override
    public void onParseSuccess(Map<String, String> headers, String url, String from) {
        if (!done.compareAndSet(false, true)) return;
        App.post(() -> {
            if (callback != null) callback.onParseSuccess(headers, url, from);
            stop();
        });
    }

    @Override
    public void onParseError() {
        if (!done.compareAndSet(false, true)) return;
        App.post(() -> {
            if (callback != null) callback.onParseError();
            stop();
        });
    }

    private void stopWeb() {
        for (CustomWebView webView : webViews) webView.stop(false);
        for (CustomWebView webView : webViews) webView.destroy();
        if (!webViews.isEmpty()) webViews.clear();
    }

    public void stop() {
        for (Future<?> future : futures) future.cancel(true);
        futures.clear();
        if (executor != null) executor.shutdownNow();
        if (infinite != null) infinite.shutdownNow();
        infinite = null;
        executor = null;
        callback = null;
        done.set(true);
        stopWeb();
    }
}
