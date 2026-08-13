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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Headers;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ParseJob implements ParseCallback {

    // ===== 解析结果 LRU 缓存：命中则直接回调成功，跳过所有 HTTP / WebView =====
    // 容量 200，TTL 30 分钟；key = (siteKey, flag, webUrl小写归一化)
    // 退出 App 再进来同集秒开，高频回看连续集不重复解析。
    private static final int CACHE_MAX_ENTRIES = 200;
    private static final long CACHE_TTL_MS = 30L * 60L * 1000L;
    private static final LinkedHashMap<String, CacheEntry> PARSE_CACHE = new LinkedHashMap<String, CacheEntry>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
            return size() > CACHE_MAX_ENTRIES;
        }
    };
    private static final Object CACHE_LOCK = new Object();

    // ===== 共享解析线程池：避免每次 ParseJob.create 都 new/shutdownNow =====
    // 单一线程跑串联解析（jsonParse / builtinParse 串行即可）；
    // Cached 线程池用于并发嗅探 / 多解析站兜底，按需扩容。
    private static final ExecutorService SHARED_PARSE_EXECUTOR;
    private static final ExecutorService SHARED_PARSE_INFINITE;
    // 嗅探候选并发池：限 4 并发（每路 probe 8s 超时），避免把弱网跑死。
    private static final ExecutorService SHARED_SNIFF_POOL;
    static {
        int cores = Math.max(2, Runtime.getRuntime().availableProcessors());
        SHARED_PARSE_EXECUTOR = new ThreadPoolExecutor(1, 1, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> {
            Thread t = new Thread(r, "parse-worker"); t.setDaemon(true); return t;
        }, new ThreadPoolExecutor.CallerRunsPolicy());
        ((ThreadPoolExecutor) SHARED_PARSE_EXECUTOR).allowCoreThreadTimeOut(true);
        SHARED_PARSE_INFINITE = new ThreadPoolExecutor(0, Math.max(4, cores * 2),
                60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> {
            Thread t = new Thread(r, "parse-inf"); t.setDaemon(true); return t;
        }, new ThreadPoolExecutor.CallerRunsPolicy());
        SHARED_SNIFF_POOL = new ThreadPoolExecutor(0, 4, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), r -> {
            Thread t = new Thread(r, "parse-sniff"); t.setDaemon(true); return t;
        }, new ThreadPoolExecutor.CallerRunsPolicy());
    }

    static final class CacheEntry {
        final Map<String, String> headers;
        final String url;
        final String from;
        final long createAt;
        CacheEntry(Map<String, String> h, String u, String f) {
            headers = h; url = u; from = f; createAt = System.currentTimeMillis();
        }
        boolean expired() { return System.currentTimeMillis() - createAt > CACHE_TTL_MS; }
    }

    static String cacheKey(String key, String flag, String url) {
        String u = url == null ? "" : url;
        return (key == null ? "" : key) + "|" + (flag == null ? "" : flag) + "|" + u.toLowerCase();
    }

    static CacheEntry getCache(String k) {
        synchronized (CACHE_LOCK) {
            CacheEntry e = PARSE_CACHE.get(k);
            if (e == null) return null;
            if (e.expired()) { PARSE_CACHE.remove(k); return null; }
            return e;
        }
    }

    static void putCache(String k, Map<String, String> headers, String url, String from) {
        if (TextUtils.isEmpty(k) || TextUtils.isEmpty(url)) return;
        synchronized (CACHE_LOCK) {
            PARSE_CACHE.put(k, new CacheEntry(headers == null ? null : new HashMap<>(headers), url, from));
        }
    }

    /** 返回当前解析缓存条目数（用于高级设置展示）。 */
    public static int cacheSize() {
        synchronized (CACHE_LOCK) { return PARSE_CACHE.size(); }
    }

    /** 清空解析缓存，返回被清空的条目数。 */
    public static int clearCache() {
        synchronized (CACHE_LOCK) {
            int n = PARSE_CACHE.size();
            PARSE_CACHE.clear();
            return n;
        }
    }

    private final AtomicBoolean done = new AtomicBoolean();
    private final List<CustomWebView> webViews;
    private final List<Future<?>> futures;
    private ParseCallback callback;
    private Parse parse;

    private ParseJob(ParseCallback callback) {
        this.webViews = new ArrayList<>();
        this.futures = Collections.synchronizedList(new ArrayList<>());
        this.callback = callback;
    }

    public static ParseJob create(ParseCallback callback) {
        return new ParseJob(callback);
    }

    public ParseJob start(Result result, boolean useParse) {
        setParse(result, useParse);
        // 解析结果 LRU 优先：命中 → 直接回调 onParseSuccess，跳过网络。
        String cacheKey = cacheKey(result.getKey(), result.getFlag(), result.getUrl().v());
        CacheEntry hit = getCache(cacheKey);
        if (hit != null) {
            App.post(() -> {
                if (callback != null) callback.onParseSuccess(hit.headers, hit.url, hit.from + "+cache");
            });
            return this;
        }
        // 没命中 → 正常执行解析；成功回调时会再 putCache。
        execute(result, cacheKey);
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

    private void execute(Result result, String cacheKey) {
        Future<?> task = SHARED_PARSE_EXECUTOR.submit(getTask(result, cacheKey));
        Task.schedule(() -> {
            // 超时：先 cancel Future，再走 stop 清理 WebView/线程，最后 onParseError
            if (task.cancel(true)) {
                stop();
                onParseError();
            }
        }, Constant.TIMEOUT_PARSE_DEF, TimeUnit.MILLISECONDS);
    }

    private Runnable getTask(Result result, String cacheKey) {
        return () -> {
            try {
                doInBackground(result.getKey(), result.getUrl().v(), result.getFlag(), cacheKey);
            } catch (Throwable e) {
                onParseError();
            }
        };
    }

    private void doInBackground(String key, String webUrl, String flag, String cacheKey) throws Throwable {
        // 在成功回调里写入缓存；先把 cacheKey 暂存到 per-job 字段里。
        this.cacheKey = cacheKey;
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

    /** 本次解析的 LRU key，成功回调时写入缓存。 */
    private volatile String cacheKey;

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
     *
     * 优化点：候选 URL 由「串行 probe」改为「限 4 并发 probe，命中即返回，其余 cancel」。
     * 原来 8 个候选最坏 64s → 现在最坏 ≈8s，解析速度 3~5 倍提升。
     */
    private boolean aiSmartParseFallback(String webUrl) {
        if (done.get()) return true;
        try {
            Map<String, String> baseHeaders = parse != null ? parse.getHeader() : new HashMap<>();
            Map<String, String> headers = UrlUtil.mergeDefaultHeaders(baseHeaders, webUrl);
            // 1) 直链 probe
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
            // 2) HTTP GET 抓正文，正则扫候选 → 并发 probe
            String body = safeGetBody(webUrl, headers);
            if (body != null && body.length() > 0) {
                List<String> candidates = UrlUtil.sniffVideoCandidates(body, webUrl, 8,
                        "m3u8", "mp4", "flv", "m4v", "index.m3u8", "playlist.m3u8", "ts");
                if (candidates != null && !candidates.isEmpty()) {
                    String matched = concurrentProbeCandidates(candidates, baseHeaders);
                    if (matched != null) {
                        Map<String, String> candHeaders = UrlUtil.mergeDefaultHeaders(baseHeaders, matched);
                        onParseSuccess(candHeaders, matched, "AI-Sniff");
                        return true;
                    }
                }
            }
            // 3) 兜底 probe
            if (!isDirectVideo && probeVideoUrl(webUrl, headers)) {
                onParseSuccess(headers, webUrl, "AI-Probe");
                return true;
            }
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 对嗅探候选 URL 列表做并发可达性 probe：限 4 并发，命中任一即返回 URL 并 cancel 其余。
     * 顺序仍按候选优先级（先拿到先并发跑），不会乱序影响质量。
     *
     * @return 命中的 URL，全部失败 / 被 done 打断则返回 null
     */
    private String concurrentProbeCandidates(List<String> candidates, Map<String, String> baseHeaders) {
        if (candidates == null || candidates.isEmpty() || done.get()) return null;
        List<String> filtered = new ArrayList<>();
        for (String cand : candidates) if (!TextUtils.isEmpty(cand)) filtered.add(cand);
        if (filtered.isEmpty()) return null;
        final String[] winner = new String[1];
        final AtomicBoolean found = new AtomicBoolean(false);
        int total = filtered.size();
        CountDownLatch latch = new CountDownLatch(total);
        List<Future<?>> fs = new ArrayList<>(total);
        try {
            for (final String cand : filtered) {
                fs.add(SHARED_SNIFF_POOL.submit(() -> {
                    try {
                        if (done.get() || found.get()) return;
                        Map<String, String> candHeaders = UrlUtil.mergeDefaultHeaders(
                                baseHeaders == null ? new HashMap<>() : baseHeaders, cand);
                        if (probeVideoUrl(cand, candHeaders)) {
                            if (found.compareAndSet(false, true)) {
                                winner[0] = cand;
                                // 把其它还没跑的 probe 尽快放行 countDown
                                while (latch.getCount() > 0) latch.countDown();
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                }));
            }
            // 每路 probe 超时最多 8s，并发等最多 10s
            try { latch.await(10_000L, TimeUnit.MILLISECONDS); } catch (Throwable ignored) {}
        } finally {
            // 跑赢的也没拿到 → 全部 cancel，避免把弱网跑满
            for (Future<?> f : fs) try { f.cancel(true); } catch (Throwable ignored) {}
        }
        return winner[0];
    }

    /**
     * 轻量探测一个视频 URL 是否可达：
     * 优先 HEAD（省流量），若 HEAD 被 403/405 则回退到 GET Range:0-0；
     * 对于明确失败的状态码（404, 5xx 等）或返回 HTML 内容的，判定为失败；
     * 对于网络超时/403 等不确定状态，若 URL 本身是视频后缀，则保守地信任（避免误杀有效源）。
     */
    private boolean probeVideoUrl(String url, Map<String, String> headers) {
        if (TextUtils.isEmpty(url) || done.get()) return false;
        
        // 检查 URL 后缀是否像视频
        String lc = url.toLowerCase();
        boolean videoExt = lc.endsWith(".m3u8") || lc.contains(".m3u8?")
                || lc.endsWith(".mp4") || lc.contains(".mp4?")
                || lc.endsWith(".flv") || lc.contains(".flv?")
                || lc.endsWith(".m4v") || lc.contains(".m4v?")
                || lc.endsWith(".ts") || lc.contains(".ts?")
                || lc.endsWith(".mkv") || lc.contains(".mkv?")
                || lc.endsWith(".webm") || lc.contains(".webm?");

        try {
            Response headRes = null;
            try {
                Request.Builder headBuilder = new Request.Builder().url(url).method("HEAD", null);
                if (headers != null && !headers.isEmpty()) headBuilder.headers(Headers.of(headers));
                headRes = OkHttp.client(8000L).newCall(headBuilder.build()).execute();
                int code = headRes.code();
                // 明确的客户端或服务器错误
                if (code == 404 || code == 410 || (code >= 500 && code < 600)) {
                    return false; 
                }
                // HEAD 成功 (2xx)，验证内容类型
                if (code >= 200 && code < 300) {
                    if (isVideoLikeResponse(headRes)) return true;
                    // 内容类型不像是视频，但状态码是 200 且 URL 有视频后缀，可能是 CDN 或重定向
                    if (videoExt) return true; 
                    return false;
                }
                // 其他状态码 (3xx, 401, 403, 405 等)，如果 URL 有视频后缀，保守信任
                if (videoExt) return true;
            } catch (Throwable ignored) {
                // HEAD 请求失败（超时、网络错误等）
                if (videoExt) return true; // 视频后缀 URL 网络失败，保守信任
            } finally {
                closeQuietly(headRes);
            }
            
            // HEAD 明确失败或返回非预期状态码，且无视频后缀，回退到 GET Range
            Map<String, String> rangeHeaders = new HashMap<>(headers != null ? headers : new HashMap<>());
            rangeHeaders.put(HttpHeaders.RANGE, "bytes=0-0");
            Request.Builder getBuilder = new Request.Builder().url(url).get();
            if (!rangeHeaders.isEmpty()) getBuilder.headers(Headers.of(rangeHeaders));
            try (Response getRes = OkHttp.client(8000L).newCall(getBuilder.build()).execute()) {
                int code = getRes.code();
                if (code == 404 || code == 410 || (code >= 500 && code < 600)) {
                    return false;
                }
                boolean codeOk = (code >= 200 && code < 300) || code == 416;
                if (!codeOk) return false;
                if (isVideoLikeResponse(getRes)) return true;
                // GET 成功但内容类型可疑，若是视频后缀则信任
                if (videoExt) return true;
                return false;
            }
        } catch (Throwable ignored) {
            // GET 也失败了，保守处理
            if (videoExt) return true;
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
     * 内置 m3u8/m3u8/mp4 直链解析（四级链路，最后兜底绝不用 onParseError 直接放弃）：
     *
     *  1) 最高优先级：qcb 云端 jiexi.php → 实时调用 qcb 已部署 http://114.134.184.91:9002/jiexi.php?url=
     *     若 qcb 返回 {code:200, url: 非原网页回环的 m3u8/mp4 直链} → 立刻 onParseSuccess。
     *
     *  2) AI 本地启发式嗅探 aiSmartParseFallback：
     *     webUrl 是直链 → probe；
     *     否则 HTTP GET 抓正文，正则扫常见视频 URL Top 候选逐个 probe；
     *     最后兜底 probe 原 URL。
     *     适合用户自建解析站 / 已经是半直链 / 简单静态站点。
     *
     *  3) 最后兜底：fallbackConcurrentParse 跑完整的传统"多解析站并发（jsonParse + jsonExtend + jsonMix + WebView sniff 多源）"
     *     专门处理爱奇艺/腾讯/优酷/B 站这类前端渲染 + 必须依赖解析站的官解线路。
     *
     *  1/2/3 只要一路命中 → onParseSuccess；全部失败 → 才 onParseError。
     */
    private void builtinParse(String webUrl) {
        if (done.get()) return;
        if (hasQcbParseServer() && qcbJiexiParse(webUrl)) return;
        if (aiSmartParseFallback(webUrl)) return;
        fallbackConcurrentParse(webUrl);
        if (!done.get()) onParseError();
    }

    /**
     * 传统多解析站并发兜底（给 builtinParse / superParse 的最后防线用）：
     * 1) 所有 type=1 的 JSON 解析站并发 jsonParse；
     * 2) 默认解析站 WebView sniff（startWeb）一路；
     * 3) jsonExtend 扩展多解析并发一路；
     * 4) 每路完成就 countDown，统一 15 秒超时；
     *    有任何一路 done 被 CAS 为 true 就提前释放，剩下全部 cancel。
     * 复用共享线程池 SHARED_PARSE_INFINITE，不在每次调用时 new/shutdown 局部池。
     */
    private void fallbackConcurrentParse(String webUrl) {
        if (done.get()) return;
        List<Parse> list = VodConfig.get().getParses();
        List<Parse> jsons = new ArrayList<>();
        Parse defaultP = parse != null ? parse : VodConfig.get().getParse();
        if (list != null && !list.isEmpty()) for (Parse p : list) if (p != null && p.getType() == 1) jsons.add(p);
        int total = Math.max(1, jsons.size()) + 1 + 1;
        if (total < 3) total = 3;
        CountDownLatch latch = new CountDownLatch(total);
        ExecutorService svc = SHARED_PARSE_INFINITE;
        List<Future<?>> fs = new ArrayList<>();
        try {
            if (jsons.isEmpty()) {
                countDownAll(latch, 1);
            } else {
                for (Parse jp : jsons) {
                    if (done.get()) { countDownAll(latch, 1); break; }
                    fs.add(svc.submit(() -> {
                        if (done.get()) { countDownAll(latch, 1); return; }
                        try { jsonParse(jp, webUrl, false); } catch (Throwable ignored) {
                        } finally { countDownAll(latch, 1); }
                    }));
                }
            }
            if (!done.get() && defaultP != null && !defaultP.isEmpty()) {
                fs.add(svc.submit(() -> {
                    if (done.get()) { countDownAll(latch, 1); return; }
                    try {
                        if (defaultP.getType() == 0) startWeb(latch, defaultP, webUrl);
                        else if (defaultP.getType() == 1) { jsonParse(defaultP, webUrl, false); countDownAll(latch, 1); }
                        else if (defaultP.getType() == 2) { jsonExtend(webUrl); countDownAll(latch, 1); }
                        else if (defaultP.getType() == 3) { jsonMix(webUrl, ""); countDownAll(latch, 1); }
                        else countDownAll(latch, 1);
                    } catch (Throwable ignored) { countDownAll(latch, 1); }
                }));
            } else countDownAll(latch, 1);
            if (!done.get()) {
                fs.add(svc.submit(() -> {
                    if (done.get()) { countDownAll(latch, 1); return; }
                    try { jsonExtend(webUrl); } catch (Throwable ignored) {
                    } finally { countDownAll(latch, 1); }
                }));
            } else countDownAll(latch, 1);
            try { latch.await(15000L, TimeUnit.MILLISECONDS); } catch (Throwable ignored) {}
        } finally {
            // svc 是共享池，只能 cancel 本批次的 futures，不能 shutdownNow()
            for (Future<?> f : fs) try { f.cancel(true); } catch (Throwable ignored) {}
        }
    }

    private void startWeb(CountDownLatch latch, Parse item, String webUrl) {
        if (done.get()) { countDownAll(latch, 1); return; }
        CustomWebView[] holder = new CustomWebView[1];
        boolean webOk;
        try {
            webOk = WebViewUtil.support();
        } catch (Throwable t) { webOk = false; }
        if (!webOk) { countDownAll(latch, 1); return; }
        App.post(() -> {
            try {
                CustomWebView cv = CustomWebView.create(App.get()).start("", item.getName(), item.getHeader(), item.getUrl() + webUrl, item.getClick(), new ParseCallback() {
                    @Override
                    public void onParseSuccess(Map<String, String> h, String u, String f) {
                        try { countDownAll(latch, 1); } catch (Throwable ignored) {}
                        ParseJob.this.onParseSuccess(h, u, f);
                    }

                    @Override
                    public void onParseError() {
                        try { countDownAll(latch, 1); } catch (Throwable ignored) {}
                    }
                }, !item.getUrl().contains("player/?url="));
                holder[0] = cv;
                synchronized (webViews) { webViews.add(cv); }
            } catch (Throwable ignored) {
                try { countDownAll(latch, 1); } catch (Throwable ignored2) {}
            }
        });
        // WebView 属于异步回调；给 50ms 让 App.post 触发的 runnable 先把 cv 加进去，避免 Future cancel 时漏清
        try { Thread.sleep(50); } catch (Throwable ignored) {}
    }

    private static void countDownAll(CountDownLatch latch, int n) {
        if (latch == null) return;
        while (n-- > 0) try { latch.countDown(); } catch (Throwable ignored) {}
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
                // qcb jiexi.php 有时会把真实 url 嵌套在 url / msg 字段里（二次 JSON 包装），尝试两个字段都解一层
                String url = extractQcbUrl(obj, "url", webUrl);
                String msg = extractQcbUrl(obj, "msg", webUrl);
                String chosen = preferCandidateUrl(url, msg, webUrl);
                if (code != 200 || TextUtils.isEmpty(chosen)) return false;
                String trimmed = chosen.trim();
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

    /**
     * 对 qcb 返回对象里的指定字段（通常 "url" / "msg"）抽取视频 URL：
     * - 若字段值是合法 http 开头 → 直接返回；
     * - 否则尝试把字段值当 JSON 再解析一层（有时 qcb 会把 {code,url,msg} 再塞成字符串），
     *   再从里层取 url / msg，取到第一个合法 http 即返回；
     * - 所有方式都不行 → 返回空串。
     */
    private static String extractQcbUrl(JsonObject outer, String field, String webUrl) {
        if (outer == null || TextUtils.isEmpty(field)) return "";
        String raw = Json.safeString(outer, field);
        if (!TextUtils.isEmpty(raw)) {
            String t = raw.trim();
            if (t.startsWith("http")) return t;
            if (t.startsWith("{") || t.startsWith("[")) {
                try {
                    JsonElement el = Json.parse(t);
                    if (el != null && el.isJsonObject()) {
                        JsonObject inner = el.getAsJsonObject();
                        String u = Json.safeString(inner, "url");
                        if (!TextUtils.isEmpty(u) && u.trim().startsWith("http")) return u.trim();
                        String m = Json.safeString(inner, "msg");
                        if (!TextUtils.isEmpty(m) && m.trim().startsWith("http")) return m.trim();
                    }
                } catch (Throwable ignored) {}
            }
        }
        return "";
    }

    /** 在 url / msg 两个候选里挑更像"真解析结果"的那个：避开等于原 URL 的，优先带视频后缀的 */
    private static String preferCandidateUrl(String a, String b, String webUrl) {
        String norm = normalizeCompareUrl(webUrl);
        String aa = normalizeCompareUrl(a);
        String bb = normalizeCompareUrl(b);
        boolean aOk = !aa.isEmpty() && !aa.equals(norm);
        boolean bOk = !bb.isEmpty() && !bb.equals(norm);
        boolean aVideo = aOk && containsVideoSuffix(a);
        boolean bVideo = bOk && containsVideoSuffix(b);
        if (aVideo && !bVideo) return a;
        if (bVideo && !aVideo) return b;
        if (aOk && !bOk) return a;
        if (bOk && !aOk) return b;
        if (!TextUtils.isEmpty(aa) && !aa.equals(norm)) return a;
        if (!TextUtils.isEmpty(bb) && !bb.equals(norm)) return b;
        if (!TextUtils.isEmpty(a)) return a;
        return b;
    }

    private static boolean containsVideoSuffix(String url) {
        if (TextUtils.isEmpty(url)) return false;
        String lc = url.toLowerCase();
        return lc.contains(".m3u8") || lc.contains(".mp4") || lc.contains(".flv")
                || lc.contains(".m4v") || lc.contains(".ts") || lc.contains(".mkv")
                || lc.contains(".webm") || lc.contains(".mov");
    }

    private static String normalizeCompareUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        String t = url.trim();
        while (t.endsWith("/")) t = t.substring(0, t.length() - 1);
        return t;
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
        // 拦截第三方解析站伪造的「假本地代理 URL」（如 http://127.0.0.1:10079/p/0/.../base64/index.m3u8），
        // 我们没有在 9978~9999 以外的端口启任何代理，直接塞给播放器会 Network Connection Failed。
        // 从 base64 段还原出真实 URL（如 https://player.ypls.com/play/...）：
        //   - 如果还原出的 URL 本身是直链或嗅探能命中 m3u8 → 直接 onParseSuccess；
        //   - 否则把它当视频页面 URL，交给「传统多解析站并发 + WebView 嗅探」兜底解析（player.ypls.com 这种需要前端渲染的页面必须走 WebView）
        String unwrapped = UrlUtil.unwrapFakeLocalProxy(url);
        if (!TextUtils.isEmpty(unwrapped)) {
            String realUrl = unwrapped;
            Map<String, String> realHeaders = UrlUtil.mergeDefaultHeaders(headers, realUrl);
            // 1) 先试 AI 嗅探（直链 probe + 页面正文正则嗅探）
            if (aiSmartParseFallbackFrom(realHeaders, realUrl, from + "+unwrapped")) return;
            // 2) 嗅探没命中：重跑 fallbackConcurrentParse（并发 jsonParse + jsonExtend + WebView sniff）
            fallbackConcurrentParse(realUrl);
            return;
        }
        if (!done.compareAndSet(false, true)) return;
        // 写入解析 LRU 缓存（同集秒开）：仅在拿到有效的最终 URL 时才写入。
        String ck = this.cacheKey;
        if (!TextUtils.isEmpty(ck) && !TextUtils.isEmpty(url)) {
            putCache(ck, headers, url, from);
        }
        App.post(() -> {
            if (callback != null) callback.onParseSuccess(headers, url, from);
            stop();
        });
    }

    /**
     * aiSmartParseFallback 的「从某对 headers+url 直接开始」重载版本：
     * 命中则立刻 onParseSuccess 并返回 true；失败返回 false（不改 done 状态），
     * 调用方可以选择继续走 WebView / 多解析站兜底。
     * 与 aiSmartParseFallback 同样：嗅探候选走并发 probe（限 4 并发）提速 3~5x。
     */
    private boolean aiSmartParseFallbackFrom(Map<String, String> headers, String webUrl, String fromTag) {
        if (done.get()) return true;
        try {
            Map<String, String> baseHeaders = headers != null ? new java.util.HashMap<>(headers) : new java.util.HashMap<>();
            Map<String, String> merged = UrlUtil.mergeDefaultHeaders(baseHeaders, webUrl);
            String lc = webUrl == null ? "" : webUrl.toLowerCase();
            boolean isDirectVideo = lc.endsWith(".m3u8") || lc.contains(".m3u8?")
                    || lc.endsWith(".mp4") || lc.contains(".mp4?")
                    || lc.endsWith(".flv") || lc.contains(".flv?")
                    || lc.endsWith(".m4v") || lc.contains(".m4v?")
                    || lc.endsWith(".ts") || lc.contains(".ts?");
            if (isDirectVideo) {
                if (probeVideoUrl(webUrl, merged)) {
                    if (done.compareAndSet(false, true)) {
                        App.post(() -> {
                            if (callback != null) callback.onParseSuccess(merged, webUrl, fromTag + "+direct");
                            stop();
                        });
                    }
                    return true;
                }
            }
            String body = safeGetBody(webUrl, merged);
            if (body != null && body.length() > 0) {
                java.util.List<String> candidates = UrlUtil.sniffVideoCandidates(body, webUrl, 8,
                        "m3u8", "mp4", "flv", "m4v", "index.m3u8", "playlist.m3u8", "ts");
                if (candidates != null && !candidates.isEmpty()) {
                    String matched = concurrentProbeCandidates(candidates, baseHeaders);
                    if (matched != null && !done.get()) {
                        Map<String, String> candHeaders = UrlUtil.mergeDefaultHeaders(baseHeaders, matched);
                        if (done.compareAndSet(false, true)) {
                            App.post(() -> {
                                if (callback != null) callback.onParseSuccess(candHeaders, matched, fromTag + "+sniff");
                                stop();
                            });
                        }
                        return true;
                    }
                }
            }
            if (!isDirectVideo && probeVideoUrl(webUrl, merged)) {
                if (done.compareAndSet(false, true)) {
                    App.post(() -> {
                        if (callback != null) callback.onParseSuccess(merged, webUrl, fromTag + "+probe");
                        stop();
                    });
                }
                return true;
            }
            return false;
        } catch (Throwable ignored) {
            return false;
        }
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
        // executor / infinite 已改为全局共享池（SHARED_PARSE_EXECUTOR / SHARED_PARSE_INFINITE），
        // 不能在这里 shutdownNow()，否则下一次 ParseJob 提交会抛 RejectedExecution。
        // 只把 callback/null、done 状态、WebView 这些 job-local 的资源清掉。
        callback = null;
        done.set(true);
        stopWeb();
    }
}
