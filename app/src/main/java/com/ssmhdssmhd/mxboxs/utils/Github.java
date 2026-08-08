package com.ssmhdssmhd.mxboxs.utils;

import com.ssmhdssmhd.mxboxs.BuildConfig;
import com.ssmhdssmhd.mxboxs.setting.Setting;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Github {

    public static final String REPO = "ssmhdssmhd/MXboxS";
    public static final String API_LATEST = "https://api.github.com/repos/" + REPO + "/releases/latest";
    public static final String API_LIST = "https://api.github.com/repos/" + REPO + "/releases?per_page=10";

    public static final String MIRROR_DIRECT = "";
    public static final String MIRROR_GHPROXY = "https://ghproxy.com";
    public static final String MIRROR_MIRROR_GHPROXY = "https://mirror.ghproxy.com";
    /** v5.5.51 修复：之前写成 .co 导致 DNS 解析失败（Unable to resolve host） */
    public static final String MIRROR_GHPS_CAMBRIDGECS = "https://ghps.cambridgecs.com";
    public static final String MIRROR_GH_API_99988866 = "https://gh.api.99988866.xyz";
    /** 国内镜像补充：ghproxy.net（与 ghproxy.com 非同一服务）、gh.mirai.org（镜像 gh release）、gh-proxy.com（常见公益站） */
    public static final String MIRROR_GHPROXY_NET = "https://ghproxy.net";
    public static final String MIRROR_GH_MIRAI = "https://gh.mirai.org";
    /** 海外镜像补充：objects.githubusercontent.com 直连 + JSDelivr（静态文件） + 欧洲 gh.123456789.xyz */
    public static final String MIRROR_JSDELIVR_CN = "https://cdn.jsdelivr.net/gh";
    public static final String MIRROR_JSDELIVR_FASTLY = "https://fastly.jsdelivr.net/gh";
    public static final String MIRROR_CF_CN = "https://gh.tmoe.me";
    /** 再补 2 条公益 ghproxy（避免前 10 条全挂） */
    public static final String MIRROR_GH_1MS = "https://gh.1ms.run";
    public static final String MIRROR_GH_DOG = "https://gh.dmirror.xyz";

    /** 显示名 → 前缀，用户 UI 下拉选单选 */
    public static final LinkedHashMap<String, String> MIRROR_OPTIONS = new LinkedHashMap<>();
    static {
        MIRROR_OPTIONS.put("ghproxy.com（国内）", MIRROR_GHPROXY);
        MIRROR_OPTIONS.put("mirror.ghproxy.com（国内）", MIRROR_MIRROR_GHPROXY);
        MIRROR_OPTIONS.put("ghps.cambridgecs.com（国内）", MIRROR_GHPS_CAMBRIDGECS);
        MIRROR_OPTIONS.put("gh.api.99988866.xyz（国内）", MIRROR_GH_API_99988866);
        MIRROR_OPTIONS.put("ghproxy.net（国内）", MIRROR_GHPROXY_NET);
        MIRROR_OPTIONS.put("gh.mirai.org（国内）", MIRROR_GH_MIRAI);
        MIRROR_OPTIONS.put("gh.1ms.run（国内）", MIRROR_GH_1MS);
        MIRROR_OPTIONS.put("GitHub 直连", MIRROR_DIRECT);
    }

    /** 国内常用镜像池（优先顺序可根据站点稳定性调整） */
    private static final List<String> CN_MIRRORS = Arrays.asList(
            MIRROR_MIRROR_GHPROXY,
            MIRROR_GHPS_CAMBRIDGECS,
            MIRROR_GHPROXY_NET,
            MIRROR_GH_API_99988866,
            MIRROR_GH_MIRAI,
            MIRROR_GHPROXY,
            MIRROR_CF_CN,
            MIRROR_GH_1MS,
            MIRROR_GH_DOG
    );

    /** 海外常用镜像池（GitHub 直连通常对海外/加速通道最快，放前面） */
    private static final List<String> OVERSEA_MIRRORS = Arrays.asList(
            MIRROR_DIRECT,
            MIRROR_JSDELIVR_FASTLY,
            MIRROR_JSDELIVR_CN
    );

    /** 公共镜像池（含直连空串）。会被 getMirrorCandidates 去重。国内/海外会按 rankByConnectivity 重新排序。 */
    private static final List<String> MIRROR_POOL = Arrays.asList(
            MIRROR_GHPROXY,
            MIRROR_MIRROR_GHPROXY,
            MIRROR_GHPS_CAMBRIDGECS,
            MIRROR_GH_API_99988866,
            MIRROR_GHPROXY_NET,
            MIRROR_GH_MIRAI,
            MIRROR_JSDELIVR_FASTLY,
            MIRROR_JSDELIVR_CN,
            MIRROR_CF_CN,
            MIRROR_GH_1MS,
            MIRROR_GH_DOG,
            MIRROR_DIRECT
    );

    /** 并行 HEAD 探测超时时长（毫秒）。给每个镜像最多 4s，超过就认为当前网络到这个镜像延迟过高。 */
    private static final long PING_TIMEOUT_MS = 4000L;

    /** 「先测试再下载」阶段的超短探测：connect 1.5s / read 1.5s（不下载任何字节）。DNS 失败/超时直接快速排除。 */
    public static final long PROBE_TIMEOUT_MS = 1500L;

    /** probe 结果（给 Updater 显示和排序用）。 */
    public static final class ProbeResult {
        public final String url;
        public final boolean ok;
        public final long rttMs;      // 成功的 RTT，失败=Long.MAX_VALUE
        public final String error;    // 失败原因，成功=null
        public ProbeResult(String url, boolean ok, long rttMs, String error) {
            this.url = url; this.ok = ok; this.rttMs = rttMs; this.error = error;
        }
    }

    /** probe 进度回调（在 UI 线程上触发）。index 从 0 开始，total=N, result=当前探测完成的那条。 */
    public interface ProbeListener {
        void onProbeOne(int index, int total, ProbeResult result);
    }

    /**
     * 先测试再下载：并行短超时探测每条 APK URL，返回「可用 URL 按 RTT 升序」的列表。
     * 同时通过 listener 把「每完成一条的结果」回调到 UI，让对话框显示「探测 3/14：ghproxy.com ✅ 187ms」。
     *
     * @param apkUrls 来自 findApkUrls(release) 的候选 URL 列表
     * @param listener UI 进度回调（可以为 null），回调在 UI 线程
     * @return ProbeResult 列表，永远非 null，顺序=ok优先+RTT升序，失败=RTT Long.MAX_VALUE 放末尾
     */
    public static List<ProbeResult> probeUrls(final List<String> apkUrls, final ProbeListener listener) {
        if (apkUrls == null || apkUrls.isEmpty()) return Collections.emptyList();
        final int total = apkUrls.size();
        final List<ProbeResult> results = Collections.synchronizedList(new ArrayList<ProbeResult>(total));
        final java.util.concurrent.atomic.AtomicInteger doneCount = new java.util.concurrent.atomic.AtomicInteger(0);

        ExecutorService pool = Executors.newFixedThreadPool(Math.min(total, 8));
        try {
            List<Future<?>> futures = new ArrayList<>(total);
            for (int i = 0; i < total; i++) {
                final int idx = i;
                final String url = apkUrls.get(i);
                if (url == null) {
                    results.add(new ProbeResult("", false, Long.MAX_VALUE, "URL is null"));
                    continue;
                }
                futures.add(pool.submit(new Callable<Object>() {
                    @Override
                    public Object call() {
                        ProbeResult r = probeOne(url, PROBE_TIMEOUT_MS);
                        // 先填占位：保证 results 顺序与输入一致
                        synchronized (results) {
                            while (results.size() <= idx) results.add(null);
                            results.set(idx, r);
                        }
                        int n = doneCount.incrementAndGet();
                        if (listener != null) {
                            final int fi = idx;
                            final int ft = total;
                            final ProbeResult fr = r;
                            final int fn = n;
                            try {
                                App.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        // 用 fn 表示当前完成的第 n 条，UI 展示 fn/total 更直观
                                        listener.onProbeOne(fi, ft, fr);
                                    }
                                });
                            } catch (Throwable ignored) {
                            }
                        }
                        return null;
                    }
                }));
            }
            pool.shutdown();
            try {
                pool.awaitTermination(PROBE_TIMEOUT_MS + 500L, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            pool.shutdownNow();
        } catch (Throwable ignored) {
            try { pool.shutdownNow(); } catch (Throwable ignore) {}
        }

        // 补空（如果有未完成的）
        while (results.size() < total) results.add(new ProbeResult(apkUrls.get(results.size()), false, Long.MAX_VALUE, "probe cancelled"));

        // 复制成非同步 List，按 ok 优先 + RTT 升序 排序
        List<ProbeResult> sorted = new ArrayList<>(results);
        Collections.sort(sorted, new Comparator<ProbeResult>() {
            @Override
            public int compare(ProbeResult a, ProbeResult b) {
                if (a == null && b == null) return 0;
                if (a == null) return 1;
                if (b == null) return -1;
                if (a.ok && !b.ok) return -1;
                if (!a.ok && b.ok) return 1;
                return Long.compare(a.rttMs, b.rttMs);
            }
        });
        return sorted;
    }

    /** 从 ProbeResult 列表（排序后）中抽取「ok=true 的 URL 在前，ok=false 的 URL 在后」的纯 URL 列表，给下载阶段用。 */
    public static List<String> extractUrls(List<ProbeResult> probes) {
        List<String> out = new ArrayList<>();
        if (probes == null) return out;
        for (ProbeResult p : probes) if (p != null) out.add(p.url);
        return out;
    }

    /** 单条探测：优先 HEAD（快），失败（例如服务器返回 405 Method Not Allowed）回退到 GET bytes=0-0 仅 1 字节探测。 */
    private static ProbeResult probeOne(String url, long timeoutMs) {
        OkHttpClient client = OkHttp.client(false, timeoutMs);
        // 1) 试 HEAD
        okhttp3.Call call = null;
        Response res = null;
        long start = System.currentTimeMillis();
        try {
            Request req = new Request.Builder().url(url).head().build();
            call = client.newCall(req);
            res = call.execute();
            long rtt = System.currentTimeMillis() - start;
            int code = res.code();
            if (code >= 200 && code < 400) {
                return new ProbeResult(url, true, rtt, null);
            }
            // 405/5xx/4xx 回退 GET 0-1 字节再试
        } catch (Throwable e) {
            // HEAD 抛异常（例如 UnknownHost、ConnectTimeout）再试 GET
        } finally {
            if (res != null) try { res.close(); } catch (Throwable ignored) {}
            if (call != null) try { call.cancel(); } catch (Throwable ignored) {}
            res = null; call = null;
        }
        // 2) 回退 GET Range: bytes=0-0
        start = System.currentTimeMillis();
        try {
            Request req = new Request.Builder().url(url).header("Range", "bytes=0-0").build();
            call = client.newCall(req);
            res = call.execute();
            long rtt = System.currentTimeMillis() - start;
            int code = res.code();
            if (code == 206 || (code >= 200 && code < 300) || code == 302 || code == 301 || code == 307) {
                return new ProbeResult(url, true, rtt, null);
            }
            String msg = "HTTP " + code;
            return new ProbeResult(url, false, Long.MAX_VALUE, msg);
        } catch (Throwable e) {
            long rtt = System.currentTimeMillis() - start;
            String msg = e.getMessage();
            if (msg == null || msg.isEmpty()) msg = e.getClass().getSimpleName();
            // 把常见错误压缩成短字符串，便于 UI 显示
            if (msg.contains("Unable to resolve host")) msg = "DNS 解析失败";
            else if (msg.contains("SocketTimeout") || msg.contains("timeout") || msg.contains("timed out")) msg = "timeout";
            else if (msg.contains("Connection refused")) msg = "连接被拒";
            else if (msg.contains("SSLHandshakeException")) msg = "SSL 握手失败";
            else if (msg.length() > 40) msg = msg.substring(0, 37) + "…";
            return new ProbeResult(url, false, Long.MAX_VALUE, msg);
        } finally {
            if (res != null) try { res.close(); } catch (Throwable ignored) {}
            if (call != null) try { call.cancel(); } catch (Throwable ignored) {}
        }
    }

    /**
     * 取镜像显示名（用于 Updater 文案）。
     */
    public static String getMirrorLabel(String mirrorOrUrl) {
        if (mirrorOrUrl == null || mirrorOrUrl.isEmpty()) return "GitHub 直连";
        String url = mirrorOrUrl;
        for (Map.Entry<String, String> e : MIRROR_OPTIONS.entrySet()) {
            String p = e.getValue();
            if (p == null || p.isEmpty()) continue;
            if (url.startsWith(p + "/") || url.startsWith(p)) return e.getKey();
        }
        // 兜底：从 URL host 取
        try {
            int s = url.indexOf("://");
            if (s < 0) return url;
            int e = url.indexOf('/', s + 3);
            return e < 0 ? url.substring(s + 3) : url.substring(s + 3, e);
        } catch (Throwable ignored) {
            return url;
        }
    }

    /** 镜像模式索引（与 Setting 中的常量 + Updater.showMirrorDialog 中的顺序保持一致）*/
    public static String getMirror() {
        int mode = Setting.getMirrorMode();
        List<String> keys = new ArrayList<>(MIRROR_OPTIONS.values());
        if (mode < 0 || mode >= keys.size()) return keys.get(Setting.MIRROR_DEFAULT_INDEX);
        return keys.get(mode);
    }

    /**
     * 返回「优先用户首选 + 所有公共镜像 + 直连」的去重候选列表，供下载失败时自动 fallback。
     *
     * 为什么要有 fallback：
     *   2026-08-08 用户反馈 ghproxy.com IP 93.46.8.90 端口 443 超时 30s → 进度条 0% 后 failed。
     *   之前 Updater 只会尝试唯一 1 个 URL，用户只能手动改镜像。现在会自动循环 4~5 个镜像直到成功。
     */
    public static List<String> getMirrorCandidates() {
        String preferred = getMirror();
        LinkedHashSet<String> set = new LinkedHashSet<>();
        set.add(preferred);
        set.addAll(MIRROR_POOL);
        return new ArrayList<>(set);
    }

    /**
     * 对 APK URL 候选列表做「当前网络连通性」重排：
     *   - 先用默认 getMirrorCandidates() 基础顺序；
     *   - 再对每个候选做 HEAD 探测（最多 4 秒，并行），得到 2xx 响应的毫秒级 RTT；
     *   - 超时 / 非 2xx 的放最后；
     *   - RTT 最短的放最前；
     *   - 用户首选镜像（preferred）始终保持第一顺位（除非 HEAD 完全失败）。
     *
     * @param apkCandidates 来自 findApkUrls(release) 的候选 URL 列表
     * @return 重排后的 APK URL 列表（永远非 null，可直接用）
     */
    public static List<String> rankByConnectivity(final List<String> apkCandidates) {
        if (apkCandidates == null || apkCandidates.isEmpty()) return Collections.emptyList();
        final int size = apkCandidates.size();
        // 候选太少（<2）就没必要探测了，直接原样返回
        if (size < 2) return new ArrayList<>(apkCandidates);

        final String preferred = getMirror();
        // preferred 对应的 APK URL（只要能被 preferred + direct 拼出的那条）
        String preferredApk = null;
        for (String u : apkCandidates) {
            if (preferred == null || preferred.isEmpty()) {
                if (u != null && !u.startsWith("http") && !u.contains("ghproxy") && !u.contains("ghps")
                        && !u.contains("99988866") && !u.contains("mirai") && !u.contains("jsdelivr") && !u.contains("tmoe")) {
                    preferredApk = u; break;
                }
                if (u != null && u.contains("github.com") && u.contains("releases")) { preferredApk = u; break; }
            } else {
                if (u != null && u.startsWith(preferred + "/")) { preferredApk = u; break; }
            }
        }
        final String prefApkFinal = preferredApk;

        // 并行 HEAD 探测
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(size, 6));
        final Map<String, Long> rttMap = new HashMap<>();
        final Map<String, Boolean> okMap = new HashMap<>();
        try {
            List<Future<?>> futures = new ArrayList<>(size);
            for (final String url : apkCandidates) {
                if (url == null) continue;
                futures.add(pool.submit(new Callable<Object>() {
                    @Override
                    public Object call() {
                        pingHead(url, rttMap, okMap);
                        return null;
                    }
                }));
            }
            // 等 PING_TIMEOUT_MS + 200ms 给线程收尾，超时直接往下走（未完成的会被视为超时）
            pool.shutdown();
            try {
                pool.awaitTermination(PING_TIMEOUT_MS + 300L, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            pool.shutdownNow();
        } catch (Throwable ignored) {
            try { pool.shutdownNow(); } catch (Throwable ignore) {}
        }

        // 按 RTT 升序排序，失败的放最后，preferred URL 保持第一（如果还能 2xx 的话）
        List<String> sorted = new ArrayList<>(apkCandidates);
        Collections.sort(sorted, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                if (a == null && b == null) return 0;
                if (a == null) return 1;
                if (b == null) return -1;
                // preferred 保持首位
                boolean aPref = a.equals(prefApkFinal);
                boolean bPref = b.equals(prefApkFinal);
                if (aPref && !bPref) return -1;
                if (!aPref && bPref) return 1;
                boolean aOk = Boolean.TRUE.equals(okMap.get(a));
                boolean bOk = Boolean.TRUE.equals(okMap.get(b));
                if (aOk && !bOk) return -1;
                if (!aOk && bOk) return 1;
                long ra = rttMap.containsKey(a) ? rttMap.get(a) : Long.MAX_VALUE;
                long rb = rttMap.containsKey(b) ? rttMap.get(b) : Long.MAX_VALUE;
                return Long.compare(ra, rb);
            }
        });
        return sorted;
    }

    private static void pingHead(String url, Map<String, Long> rttMap, Map<String, Boolean> okMap) {
        OkHttpClient client = OkHttp.client(false, PING_TIMEOUT_MS);
        Request req = new Request.Builder().url(url).head().build();
        long start = System.currentTimeMillis();
        okhttp3.Call call = null;
        Response res = null;
        try {
            call = client.newCall(req);
            res = call.execute();
            long rtt = System.currentTimeMillis() - start;
            int code = res.code();
            // 2xx/3xx 认为可用（HEAD 对 release asset 下载地址也常返回 302）
            boolean ok = code >= 200 && code < 400;
            rttMap.put(url, rtt);
            okMap.put(url, ok);
        } catch (Throwable e) {
            rttMap.put(url, Long.MAX_VALUE);
            okMap.put(url, false);
        } finally {
            if (res != null) try { res.close(); } catch (Throwable ignored) {}
            if (call != null) try { call.cancel(); } catch (Throwable ignored) {}
        }
    }

    public static String getApiUrl() {
        return API_LATEST;
    }

    public static JSONObject getLatestRelease() {
        String url = getApiUrl();
        String mirror = getMirror();
        try {
            String json = OkHttp.string(url);
            return new JSONObject(json);
        } catch (Exception e) {
            if (!mirror.isEmpty()) {
                try {
                    String json = OkHttp.string(mirror + "/" + url);
                    return new JSONObject(json);
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    /**
     * 获取 releases 列表（按 created_at 倒序，per_page=10），
     * 用于 /releases/latest 不是最新版本（例如 Latest 标记没更新）时的兜底。
     * 遍历所有 releases（包括 prerelease，因为 MXboxS-latest 是 prerelease），
     * 取 APK asset 文件名中版本号最高的 release。
     */
    public static JSONObject getHighestRelease() {
        String mirror = getMirror();
        String listUrl = API_LIST;
        JSONArray arr = null;
        try {
            String json = OkHttp.string(listUrl);
            arr = new JSONArray(json);
        } catch (Exception e) {
            if (!mirror.isEmpty()) {
                try {
                    String json = OkHttp.string(mirror + "/" + listUrl);
                    arr = new JSONArray(json);
                } catch (Exception ignored) {
                }
            }
        }
        if (arr == null || arr.length() == 0) return null;

        JSONObject best = null;
        String bestVersion = "";
        for (int i = 0; i < arr.length(); i++) {
            JSONObject rel = arr.optJSONObject(i);
            if (rel == null) continue;
            String v = extractVersionFromAssets(rel);
            if (v.isEmpty()) {
                String tag = rel.optString("tag_name", "");
                v = tag.startsWith("v") ? tag.substring(1) : tag;
            }
            if (v.isEmpty()) continue;
            if (bestVersion.isEmpty() || compareVersion(v, bestVersion) > 0) {
                bestVersion = v;
                best = rel;
            }
        }
        return best;
    }

    private static int parseIntOrZero(String s) {
        try {
            String cleaned = s == null ? "" : s.replaceAll("[^0-9]", "");
            return cleaned.isEmpty() ? 0 : Integer.parseInt(cleaned);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 按点分段比较两个 X.Y.Z 版本号（纯数字比较，非字典序）。
     * @return 正数表示 a > b，0 相等，负数 a < b
     */
    public static int compareVersion(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        String[] aParts = a.split("\\.");
        String[] bParts = b.split("\\.");
        int max = Math.max(aParts.length, bParts.length);
        for (int i = 0; i < max; i++) {
            int ai = parseIntOrZero(i < aParts.length ? aParts[i] : "0");
            int bi = parseIntOrZero(i < bParts.length ? bParts[i] : "0");
            if (ai != bi) return ai - bi;
        }
        return 0;
    }

    private static String pickDirectApkUrl(JSONObject release) throws Exception {
        if (release == null) return null;
        JSONArray assets = release.optJSONArray("assets");
        if (assets == null) return null;

        String mode = BuildConfig.FLAVOR_mode;
        String abi = BuildConfig.FLAVOR_abi;

        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            String name = asset.optString("name");
            if (name.endsWith(".apk") && name.contains(mode) && name.contains(abi)) {
                return asset.optString("browser_download_url");
            }
        }
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            String name = asset.optString("name");
            if (name.endsWith(".apk") && name.contains(mode)) {
                return asset.optString("browser_download_url");
            }
        }
        return null;
    }

    /**
     * 旧版 API：返回用户首选镜像对应的 APK URL（单一）。
     * 保留给外部可能的引用，代码内部的下载流程请改用 findApkUrls() 走 fallback。
     */
    public static String findApkUrl(JSONObject release) {
        List<String> urls = findApkUrls(release);
        return urls == null || urls.isEmpty() ? null : urls.get(0);
    }

    /**
     * 新版 API：按「用户首选 → 公共镜像池 → 直连 GitHub」的去重候选顺序，返回多个可下载 APK URL。
     * Updater 下载第一个失败时自动切换下一个，避免 ghproxy 单镜像宕机就进度条 0% 卡死、30s 后失败。
     */
    public static List<String> findApkUrls(JSONObject release) {
        List<String> out = new ArrayList<>();
        try {
            String direct = pickDirectApkUrl(release);
            if (direct == null || direct.isEmpty()) return Collections.emptyList();
            // 1) 常规前缀镜像拼接
            for (String mirror : getMirrorCandidates()) {
                String u = (mirror == null || mirror.isEmpty()) ? direct : mirror + "/" + direct;
                if (u != null && !u.isEmpty() && !out.contains(u)) out.add(u);
            }
            // 2) JSDelivr @tag 专用候选（jsdelivr 能直接从 Release 资产拉，但需要形如 @tag + 文件名的路径）
            try {
                List<String> js = buildJsDelivrCandidates(release, direct);
                for (String u : js) if (u != null && !out.contains(u)) out.add(u);
            } catch (Throwable ignored) {
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return out;
    }

    private static List<String> buildJsDelivrCandidates(JSONObject release, String directApkUrl) {
        List<String> out = new ArrayList<>();
        if (release == null || directApkUrl == null) return out;
        // directApkUrl 形如：https://github.com/{owner}/{repo}/releases/download/{tag}/{filename}.apk
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "https?://github\\.com/([^/]+)/([^/]+)/releases/download/([^/]+)/(.+\\.apk)",
                java.util.regex.Pattern.CASE_INSENSITIVE
        ).matcher(directApkUrl);
        if (!m.matches()) return out;
        String owner = m.group(1);
        String repo = m.group(2);
        String tag = m.group(3);
        String file = m.group(4);
        if (owner == null || repo == null || tag == null || file == null) return out;
        // jsdelivr /gh/owner/repo@tag/path/to/file  →  path 用 releases/download/tag/file.apk 有时不稳定，换用 release asset 的 CDN 格式：
        // https://fastly.jsdelivr.net/gh/owner/repo@tag/  只能从 repo 文件系统取，无法直接读 release asset (上传到 GitHub release 的文件不在 git tree)
        // → 但我们能把 fastly/jsdelivr 的前缀，直接当成普通镜像用（走反代回 releases/download 路径），这样即便 jsdelivr 不能从 @tag 读 asset，也会回源到 GitHub。
        // 同时保留 @tag 备用版本，作为「额外候选」丢给 rankByConnectivity 做 HEAD，能通就用。
        String basePath = "/releases/download/" + tag + "/" + file;
        out.add(MIRROR_JSDELIVR_FASTLY + basePath);
        out.add(MIRROR_JSDELIVR_CN + basePath);
        return out;
    }

    /**
     * 兜底增强：若 findApkUrls 返回的候选数不足（v5.5.46 客户端只有 5 条前缀时常见），
     * 这里再用 getMirrorCandidates() 对 direct URL 重拼一遍，保证候选数达到 mirrorCandidates.size() 以上 + jsdelivr 额外 2 条。
     */
    public static List<String> ensureCandidates(List<String> existing, JSONObject release, List<String> mirrorCandidates) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (existing != null) set.addAll(existing);
        try {
            String direct = pickDirectApkUrl(release);
            if (direct != null && !direct.isEmpty()) {
                if (mirrorCandidates != null) {
                    for (String mirror : mirrorCandidates) {
                        String u = (mirror == null || mirror.isEmpty()) ? direct : mirror + "/" + direct;
                        if (u != null && !u.isEmpty()) set.add(u);
                    }
                }
                try {
                    List<String> js = buildJsDelivrCandidates(release, direct);
                    set.addAll(js);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return new ArrayList<>(set);
    }

    /**
     * 从 Release 的 APK asset 文件名中提取版本号。
     * 文件名格式：MXboxS-mobile-arm64_v8a-5.5.40.apk → 提取 "5.5.40"。
     * 用于 tag_name 不是 vX.Y.Z 格式（如 MXboxS-latest 自动预发布 tag）时的版本来源。
     *
     * 返回 Pair.first = 版本号字符串（X.Y.Z 纯数字点分，空串表示没取到）
     *       Pair.second = 对应 APK asset 名（空串表示没取到，方便 Updater 诊断为什么没取到）
     */
    public static android.util.Pair<String, String> extractVersionFromAssetsWithDebug(JSONObject release) {
        String apkName = "";
        if (release != null) {
            try {
                JSONArray assets = release.optJSONArray("assets");
                if (assets != null && assets.length() > 0) {
                    java.util.regex.Pattern p = java.util.regex.Pattern.compile("MXboxS-[A-Za-z0-9_-]+-([0-9]+\\.[0-9]+\\.[0-9]+)\\.apk");
                    for (int i = 0; i < assets.length(); i++) {
                        String name = assets.getJSONObject(i).optString("name");
                        java.util.regex.Matcher m = p.matcher(name);
                        if (m.matches()) return android.util.Pair.create(m.group(1), name);
                    }
                    java.util.regex.Pattern p2 = java.util.regex.Pattern.compile("([0-9]+\\.[0-9]+\\.[0-9]+)");
                    for (int i = 0; i < assets.length(); i++) {
                        String name = assets.getJSONObject(i).optString("name");
                        if (!name.endsWith(".apk")) continue;
                        java.util.regex.Matcher m = p2.matcher(name);
                        if (m.find()) return android.util.Pair.create(m.group(1), name);
                        if (apkName.isEmpty()) apkName = name; // 兜底：记录第一个 APK 文件名（即便没匹配到 X.Y.Z）
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return android.util.Pair.create("", apkName);
    }

    public static String extractVersionFromAssets(JSONObject release) {
        return extractVersionFromAssetsWithDebug(release).first;
    }

    public static String getApkName() {
        return "MXboxS-" + BuildConfig.FLAVOR_mode + "-" + BuildConfig.FLAVOR_abi + "-" + BuildConfig.VERSION_NAME + ".apk";
    }

    public static String getApkNameWithVersion(String version) {
        return "MXboxS-" + BuildConfig.FLAVOR_mode + "-" + BuildConfig.FLAVOR_abi + "-" + version + ".apk";
    }
}