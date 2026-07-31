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
        try (Response res = OkHttp.newCall(item.getUrl() + webUrl, item.getHeader()).execute()) {
            JsonObject object = Json.parse(res.body().string()).getAsJsonObject();
            String url = Json.safeString(object, "url");
            JsonObject data = object.getAsJsonObject("data");
            if (url.isEmpty()) url = Json.safeString(data, "url");
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

    private void superParse(String webUrl, String flag) throws Exception {
        List<Parse> json = VodConfig.get().getParses(1, flag);
        List<Parse> webs = VodConfig.get().getParses(0, flag);
        int count = json.size() + (webs.isEmpty() ? 0 : 1);
        if (count == 0) {
            // 没有可用解析器，直接尝试 AI 智能解析 fallback
            if (aiSmartParseFallback(webUrl)) return;
            onParseError();
            return;
        }
        CountDownLatch latch = new CountDownLatch(count);
        for (Parse item : json) {
            Future<?> future = infinite.submit(() -> {
                try {
                    jsonParse(item, webUrl, false);
                } catch (Exception e) {
                    // 单个解析失败不影响其它，只记录（避免污染日志）
                } finally {
                    try { latch.countDown(); } catch (Exception ignored) {}
                }
            });
            futures.add(future);
        }
        if (!webs.isEmpty()) startWeb(latch, webs, webUrl);
        try {
            boolean ok = latch.await(30, TimeUnit.SECONDS);
            // 未完成的 future 不影响 latch，但保证我们不会直接 NPE
            for (Future<?> f : futures) {
                try { if (!f.isDone()) f.cancel(true); } catch (Exception ignored) {}
            }
            if (!ok && !done.get()) {
                // 超时但还没成功，尝试 AI 智能解析 fallback
                if (!aiSmartParseFallback(webUrl)) onParseError();
            } else if (!done.get()) {
                // 正常完成但解析失败，尝试 AI fallback
                if (!aiSmartParseFallback(webUrl)) onParseError();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (!done.get()) {
                if (!aiSmartParseFallback(webUrl)) onParseError();
            }
        }
    }

    /**
     * AI 智能解析 fallback：
     * 当传统解析（json / mix / extend / 超级）失败时，
     * 先通过启发式规则嗅探页面中的真实视频 URL（m3u8/mp4/flv...），
     * 若命中则直接用该 URL 播放，不再依赖第三方解析站。
     */
    private boolean aiSmartParseFallback(String webUrl) {
        if (done.get()) return true;
        try {
            Map<String, String> headers = parse != null ? parse.getHeader() : new HashMap<>();
            // 1) 如果本身就是 m3u8 / mp4 / flv 等直链，直接放行
            String lc = webUrl == null ? "" : webUrl.toLowerCase();
            if (lc.endsWith(".m3u8") || lc.contains(".m3u8?")
                    || lc.endsWith(".mp4") || lc.contains(".mp4?")
                    || lc.endsWith(".flv") || lc.contains(".flv?")
                    || lc.endsWith(".m4v") || lc.contains(".m4v?")
                    || lc.endsWith(".ts") || lc.contains(".ts?")) {
                onParseSuccess(headers, webUrl, "AI-Direct");
                return true;
            }
            // 2) 用简单 HTTP GET 抓页面正文，正则扫常见视频 URL
            String body = safeGetBody(webUrl, headers);
            if (body != null && body.length() > 0) {
                String sniffed = UrlUtil.sniffVideo(body, webUrl, "m3u8", "mp4", "flv", "m4v", "index.m3u8", "playlist.m3u8");
                if (sniffed != null && !sniffed.isEmpty()) {
                    onParseSuccess(headers, sniffed, "AI-Sniff");
                    return true;
                }
            }
            // 3) 失败：返回 false，让上层决定走 onParseError
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String safeGetBody(String url, Map<String, String> headers) {
        try (Response res = OkHttp.newCall(url, headers).execute()) {
            if (res.body() != null) {
                String s = res.body().string();
                if (s.length() > 512 * 1024) s = s.substring(0, 512 * 1024);
                return s;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * 内置 m3u8/m3u8/mp4 直链解析：
     * 1) 若 webUrl 本身是视频直链，直接放行；
     * 2) 否则抓取 HTML/JS 正文，调用 UrlUtil.sniffVideo 启发式正则嗅探视频地址；
     * 3) 再失败时走 onParseError 让上层提示/重试。
     */
    private void builtinParse(String webUrl) {
        if (done.get()) return;
        if (aiSmartParseFallback(webUrl)) return;
        onParseError();
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
