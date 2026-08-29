package com.ssmhdssmhd.mxboxs.utils;

import com.ssmhdssmhd.mxboxs.App;
import com.ssmhdssmhd.mxboxs.BuildConfig;
import com.ssmhdssmhd.mxboxs.setting.Setting;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
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
import okhttp3.ResponseBody;

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
    public static final String MIRROR_CF_CN = "https://gh.tmoe.me";
    /** 再补 2 条公益 ghproxy（避免前 10 条全挂） */
    public static final String MIRROR_GH_1MS = "https://gh.1ms.run";
    public static final String MIRROR_GH_DOG = "https://gh.dmirror.xyz";

    /** 显示名 → 前缀，用户 UI 下拉选单选（v5.5.61 起把"GitHub 直连"默认放到第一位） */
    public static final LinkedHashMap<String, String> MIRROR_OPTIONS = new LinkedHashMap<>();
    static {
        MIRROR_OPTIONS.put("GitHub 直连", MIRROR_DIRECT);
        MIRROR_OPTIONS.put("ghproxy.com（国内）", MIRROR_GHPROXY);
        MIRROR_OPTIONS.put("mirror.ghproxy.com（国内）", MIRROR_MIRROR_GHPROXY);
        MIRROR_OPTIONS.put("ghps.cambridgecs.com（国内）", MIRROR_GHPS_CAMBRIDGECS);
        MIRROR_OPTIONS.put("gh.api.99988866.xyz（国内）", MIRROR_GH_API_99988866);
        MIRROR_OPTIONS.put("ghproxy.net（国内）", MIRROR_GHPROXY_NET);
        MIRROR_OPTIONS.put("gh.mirai.org（国内）", MIRROR_GH_MIRAI);
        MIRROR_OPTIONS.put("gh.1ms.run（国内）", MIRROR_GH_1MS);
    }

    /** 进程内 host 级别黑名单：上一轮 verifyApkIntegrity 失败的镜像，这轮直接跳过（避免 ghproxy 变体 14 条全白等）。
     *  由 Updater.success() 校验失败后写入；Github.probeUrls 会读取并在探测前就 fail 掉这些 URL。*/
    public static final java.util.Set<String> BAD_MIRROR_HOSTS =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    /** 公共镜像池（含直连空串）。v5.5.61：GitHub 直连放最前（海外加速通、国内 4G 也能通 github.com 下载，比 ghproxy 假 200 HTML 页靠谱）；
     *  去掉两条 jsdelivr（fastly/cdn.jsdelivr 实测必 404 Not Found：release asset 不在 git tree 上，jsdelivr 只能拿 git tree 里的文件）。 */
    private static final List<String> MIRROR_POOL = Arrays.asList(
            MIRROR_DIRECT,
            MIRROR_MIRROR_GHPROXY,
            MIRROR_GHPROXY,
            MIRROR_GHPROXY_NET,
            MIRROR_GH_MIRAI,
            MIRROR_GH_1MS,
            MIRROR_CF_CN,
            MIRROR_GH_DOG,
            MIRROR_GH_API_99988866,
            MIRROR_GHPS_CAMBRIDGECS
    );

    /** 并行探测超时时长（毫秒）。探针现在会实际 GET 1KB 头校验 ZIP，超时拉长到 6s。 */
    private static final long PING_TIMEOUT_MS = 6000L;

    /** 「先测试再下载」阶段探测：v5.5.61 起直接 GET bytes=0-1023 做"三重真校验"，不再依赖容易被反代欺骗的 HEAD/Range 0-0。
     *  6s 以内必须完成连接+拿到 1KB 头，否则认为当前网络到这个镜像延迟过高或返回错误页。 */
    public static final long PROBE_TIMEOUT_MS = 6000L;

    /** ZIP 格式魔术头（所有 APK 都是 ZIP，前 4 字节一定是 0x50 0x4B 0x03 0x04 = ASCII "PK\x03\x04"）。
     *  这条是终极判据：ghproxy 类哪怕 HTTP 200 + Content-Type application/octet-stream，只要 body 是错误页/404，都不可能过这关。 */
    private static final byte[] ZIP_LOCAL_FILE_HEADER_MAGIC = new byte[]{0x50, 0x4B, 0x03, 0x04};

    /** 正常 APK 至少 1MB（我们 release 里最小的也 100M+），Content-Length < 1MB 直接认为是错误页/404 短文本 */
    private static final long MIN_REASONABLE_APK_BYTES = 1_000_000L;

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

    /** 从 URL 里提取 host（用于 BAD_MIRROR_HOSTS 黑名单 match） */
    private static String hostOf(String url) {
        if (url == null || url.isEmpty()) return "";
        try {
            int s = url.indexOf("://");
            if (s < 0) return "";
            int e = url.indexOf('/', s + 3);
            String host = (e < 0 ? url.substring(s + 3) : url.substring(s + 3, e));
            int at = host.lastIndexOf('@');
            if (at >= 0) host = host.substring(at + 1);
            int colon = host.indexOf(':');
            if (colon >= 0) host = host.substring(0, colon);
            return host == null ? "" : host;
        } catch (Throwable t) { return ""; }
    }

    /**
     * 先测试再下载：并行 6s 超时探测每条 APK URL，返回「可用 URL 按 RTT 升序」的列表。
     * v5.5.61 起不再用"HTTP 码判成功"的不可靠探针（ghproxy/mirror 反代 HTTP 200 但 body 是 43KB CookieYes HTML 的情况太常见），
     * 改成实际 GET bytes=0-1023 拿 1KB 头，做【三重真校验】：
     *   1) Content-Type 不能是 text/html / text/plain / application/json / application/xml
     *   2) 响应头 Content-Length（或 Content-Range / total）必须 >= 1MB（否则是 404/500 短错误页）
     *   3) body 头 4 字节必须是 ZIP_LOCAL_FILE_HEADER_MAGIC (50 4B 03 04)
     * 三条全过才叫 ok=true。
     *
     * 同时通过 listener 把「每完成一条的结果」回调到 UI，让对话框显示「探测 3/10：ghproxy.com ✅ 187ms / ghps ❌ 内容不是 APK(ZIP 魔术不匹配)」。
     *
     * BAD_MIRROR_HOSTS 过滤：进程内已经被上一轮 verifyApkIntegrity 拉黑的 host，直接跳过探测（返回 ProbeResult(ok=false, error=host 已列入黑名单)），
     * 避免 ghproxy 同源变体 10 条全白等。
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
                // 黑名单过滤：host 命中则直接 fail，不浪费 6s 再探测一次
                final String host = hostOf(url);
                if (host != null && !host.isEmpty() && BAD_MIRROR_HOSTS.contains(host)) {
                    ProbeResult r = new ProbeResult(url, false, Long.MAX_VALUE, "host " + host + " 已在上轮列入黑名单（verify 失败）");
                    synchronized (results) {
                        while (results.size() <= idx) results.add(null);
                        results.set(idx, r);
                    }
                    int n = doneCount.incrementAndGet();
                    if (listener != null) {
                        final int fi = idx;
                        final ProbeResult fr = r;
                        final int fn = n;
                        try {
                            App.post(new Runnable() {
                                @Override
                                public void run() { listener.onProbeOne(fi, total, fr); }
                            });
                        } catch (Throwable ignored) {}
                    }
                    continue;
                }
                futures.add(pool.submit(new Callable<Object>() {
                    @Override
                    public Object call() {
                        ProbeResult r = probeOne(url, PROBE_TIMEOUT_MS);
                        synchronized (results) {
                            while (results.size() <= idx) results.add(null);
                            results.set(idx, r);
                        }
                        int n = doneCount.incrementAndGet();
                        if (listener != null) {
                            final int fi = idx;
                            final ProbeResult fr = r;
                            final int fn = n;
                            try {
                                App.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        listener.onProbeOne(fi, total, fr);
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
                pool.awaitTermination(PROBE_TIMEOUT_MS + 1000L, TimeUnit.MILLISECONDS);
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

    /** 把 probeUrls 返回的 List<ProbeResult>（已按 ok↑+rtt↑ 排序）抽取为纯 URL 列表（给 Updater 作为 apkUrls 新序）。 */
    public static List<String> extractUrls(List<ProbeResult> results) {
        if (results == null) return null;
        List<String> okUrls = new ArrayList<>(results.size());
        List<String> failUrls = new ArrayList<>(results.size());
        for (ProbeResult r : results) {
            if (r == null) continue;
            if (r.ok) okUrls.add(r.url); else failUrls.add(r.url);
        }
        List<String> merged = new ArrayList<>(okUrls.size() + failUrls.size());
        merged.addAll(okUrls);
        merged.addAll(failUrls);
        return merged;
    }

    /**
     * v5.5.61 新探针：单条 URL 三重真校验。
     *   ① 发 GET Range: bytes=0-1023，强制服务器回前 1KB（或整个 body 如果文件 <1KB；APK 114MB+ 肯定 >=1KB）
     *   ② 读 HTTP code：206/200/302/301/307 允许；非 2xx/3xx 直接 fail=HTTP N
     *   ③ Content-Type check：不能是 text/html / text/plain / application/json / application/xml（忽略大小写）
     *   ④ Content-Length（或 Content-Range 的 total）合理性：必须 >= MIN_REASONABLE_APK_BYTES (1MB)
     *   ⑤ body 头 4 字节 == ZIP_LOCAL_FILE_HEADER_MAGIC (PK\x03\x04)
     * 全部通过才是 ProbeResult.ok=true。
     */
    private static ProbeResult probeOne(String url, long timeoutMs) {
        OkHttpClient client = OkHttp.client(false, timeoutMs);
        okhttp3.Call call = null;
        Response res = null;
        ResponseBody body = null;
        long start = System.currentTimeMillis();
        try {
            Request req = new Request.Builder()
                    .url(url)
                    .header("Range", "bytes=0-1023")
                    .build();
            call = client.newCall(req);
            res = call.execute();
            int code = res.code();
            String codeErr = null;
            if (code == 206 || (code >= 200 && code < 300) || code == 302 || code == 301 || code == 307 || code == 308) {
                // OK，继续校验
            } else {
                codeErr = "HTTP " + code;
                // 这里不直接返回，后面会把 codeErr 作为错误原因，但仍尝试 body 读 ZIP 魔术（尤其 500 也可能返回 HTML 头）
            }
            long rtt = System.currentTimeMillis() - start;

            // ③ Content-Type 检查（对 301/302 跳转允许，真实下 APK 的服务器必然是非 text/*）
            String ct = res.header("Content-Type");
            if (ct != null) {
                String lower = ct.toLowerCase(java.util.Locale.ROOT);
                if (lower.contains("text/html") || lower.contains("text/plain")
                        || lower.contains("application/json") || lower.contains("application/xml")) {
                    // 404 Not Found / 500 Server Error 常见 Content-Type: text/html → 直接失败
                    return new ProbeResult(url, false, Long.MAX_VALUE,
                            "Content-Type 为 " + ct + "（非 APK：服务器返回了 HTML/JSON 错误页）");
                }
            }

            // ④ Content-Length / Content-Range 合理性（>= 1MB）。对 3xx 跳转还没 body，这一步跳过；
            //    对 2xx 响应（200/206）才检查，能直接把返回 100 字节错误页的镜像干掉。
            if (code == 200 || code == 206) {
                long declaredLen = -1L;
                try {
                    // 206 时解析 Content-Range: bytes 0-1023/TOTAL，TOTAL 就是 APK 总大小（>=1MB）
                    String cr = res.header("Content-Range");
                    if (cr != null && !cr.isEmpty()) {
                        int slash = cr.lastIndexOf('/');
                        if (slash >= 0) {
                            String total = cr.substring(slash + 1).trim();
                            if (!"*".equals(total)) declaredLen = Long.parseLong(total);
                        }
                    }
                    if (declaredLen < MIN_REASONABLE_APK_BYTES) {
                        String cl = res.header("Content-Length");
                        if (cl != null && !cl.isEmpty()) declaredLen = Long.parseLong(cl.trim());
                    }
                } catch (Throwable ignored) {
                    // declaredLen 保持 -1
                }
                if (declaredLen > 0 && declaredLen < MIN_REASONABLE_APK_BYTES) {
                    return new ProbeResult(url, false, Long.MAX_VALUE,
                            "响应长度仅 " + declaredLen + "B（<1MB，服务器返回了错误页或 404 短响应，不是真实 APK）");
                }
            }

            // ⑤ 最关键：读 body 头 4 字节，必须是 ZIP 魔术 (PK\x03\x04)
            //    对 3xx 跳转（Location 指向下一条），body 通常为 0 字节，这一步 body == null 会报 "跳转 body 空" → 判失败？
            //    答：对 3xx 我们先跟着 OkHttp 默认自动 followRedirects 再执行的话，拿到的已经是最终响应，不会是 3xx。
            //    OkHttp 默认 followRedirects=true，所以 code 里应该不会出现 302/301。为兼容更怪的情况，我们允许重定向但 body 为空时仍"暂时认为可用"（靠后续真实下载 + verifyApkIntegrity 兜底）。
            byte[] head4 = null;
            int bodyBytesRead = 0;
            try {
                body = res.body();
                if (body != null) {
                    InputStream is = body.byteStream();
                    byte[] buf = new byte[4];
                    int off = 0;
                    while (off < buf.length) {
                        int n = is.read(buf, off, buf.length - off);
                        if (n < 0) break;
                        off += n;
                    }
                    bodyBytesRead = off;
                    if (off >= 4) head4 = buf;
                }
            } catch (Throwable ignored) {
                // 读失败也没关系，当成 head4=null
            } finally {
                if (body != null) try { body.close(); } catch (Throwable ignored) {}
            }

            // 如果响应是 2xx（已经走到真实 APK），那我们必须能读到 4 字节且是 ZIP 魔术
            if (code == 200 || code == 206) {
                if (bodyBytesRead < 4) {
                    return new ProbeResult(url, false, Long.MAX_VALUE,
                            "真实响应只读到 " + bodyBytesRead + " 字节（未读到 ZIP 头，非真实 APK，极可能 404/错误页）");
                }
                boolean zipOk = (head4[0] == ZIP_LOCAL_FILE_HEADER_MAGIC[0])
                        && (head4[1] == ZIP_LOCAL_FILE_HEADER_MAGIC[1])
                        && (head4[2] == ZIP_LOCAL_FILE_HEADER_MAGIC[2])
                        && (head4[3] == ZIP_LOCAL_FILE_HEADER_MAGIC[3]);
                if (!zipOk) {
                    String previewHex = String.format(java.util.Locale.ROOT, "%02X %02X %02X %02X",
                            head4[0] & 0xFF, head4[1] & 0xFF, head4[2] & 0xFF, head4[3] & 0xFF);
                    return new ProbeResult(url, false, Long.MAX_VALUE,
                            "内容不是 APK（ZIP 魔术不匹配，头4字节=" + previewHex + "，服务器返回了 HTML/错误页）");
                }
            } else {
                // 3xx：OkHttp 没自动 follow 的特殊跳转，ZIP 头还读不到，只能"放行到下载阶段 + verifyApkIntegrity 兜底"
                // 但至少 codeErr 如果存在的话，还是要优先失败
                if (codeErr != null) {
                    return new ProbeResult(url, false, Long.MAX_VALUE, codeErr);
                }
            }
            return new ProbeResult(url, true, rtt, null);
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
            if (body != null) try { body.close(); } catch (Throwable ignored) {}
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

    /**
     * 版本检测专用的「直连 + 全镜像兜底」JSON 拉取。
     *
     * 背景（国内更新连不上 GitHub）：
     *   用户默认镜像若为「GitHub 直连」(空串)，api.github.com 在国内常被墙 / DNS 解析异常。
     *   旧逻辑失败后只回退「用户首选的那一个镜像」；直连为默认时根本没有镜像可回退，
     *   → 版本检测永远失败，弹「未连上 GitHub API」，拿不到新版本号 = 无法触发更新。
     *
     * 修复：直连失败后，依次尝试 MIRROR_POOL 里所有非空镜像（每个 8s 短超时，DNS 挂的秒回），
     *   谁先返回合法响应就用谁。下载阶段已有「先测速选最优」逻辑，这里只负责先拿到版本信息。
     */
    private static String getStringViaMirrors(String url) {
        if (url == null || url.isEmpty()) return "";
        okhttp3.OkHttpClient c = OkHttp.client(8000L);
        // 1) 直连优先（走限时 client，避免本地直连能通但慢的场景被默认长超时卡很久）
        String direct = fetchJsonBody(c, url);
        if (direct != null) return direct;
        // 2) 全镜像兜底：首选 + MIRROR_POOL，去重后逐个试；只有真正是 JSON 的响应才算成功
        LinkedHashSet<String> mirrors = new LinkedHashSet<>();
        String preferred = getMirror();
        if (preferred != null && !preferred.isEmpty()) mirrors.add(preferred);
        for (String m : MIRROR_POOL) if (m != null && !m.isEmpty()) mirrors.add(m);
        for (String m : mirrors) {
            String candidate = m + "/" + url;
            String json = fetchJsonBody(c, candidate);
            if (json != null) return json;
        }
        return "";
    }

    /** GET 一个 URL，仅当返回体是合法 JSON（对象或数组）时返回该字符串，否则返回 null（跳过，继续下一个镜像）。 */
    private static String fetchJsonBody(okhttp3.OkHttpClient c, String url) {
        if (url == null || url.isEmpty()) return null;
        okhttp3.Response res = null;
        try {
            res = c.newCall(new okhttp3.Request.Builder().url(url).build()).execute();
            if (!res.isSuccessful()) return null;
            okhttp3.ResponseBody b = res.body();
            if (b == null) return null;
            String json = b.string();
            if (json == null || json.isEmpty()) return null;
            // 必须是合法 JSON 结构（{...} 或 [...]），拒绝镜像/网关返回的 HTML 错误页或纯文本
            String first = json.trim();
            if (first.isEmpty()) return null;
            char c0 = first.charAt(0);
            if (c0 != '{' && c0 != '[') return null;
            return json;
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (res != null) try { res.close(); } catch (Throwable ignored) {}
        }
    }

    public static JSONObject getLatestRelease() {
        try {
            String json = getStringViaMirrors(getApiUrl());
            if (json == null || json.isEmpty()) return null;
            return new JSONObject(json);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取 releases 列表（按 created_at 倒序，per_page=10），
     * 用于 /releases/latest 不是最新版本（例如 Latest 标记没更新）时的兜底。
     * 遍历所有 releases（包括 prerelease，因为 MXboxS-latest 是 prerelease），
     * 取 APK asset 文件名中版本号最高的 release。
     */
    public static JSONObject getHighestRelease() {
        String listUrl = API_LIST;
        JSONArray arr = null;
        String json = getStringViaMirrors(listUrl);
        if (json != null && !json.isEmpty()) {
            try {
                arr = new JSONArray(json);
            } catch (Exception ignored) {
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

    /**
     * 按当前 flavor（mode=mobile|leanback / abi=arm64_v8a|armeabi_v7a|...）精准匹配
     * 匹配度：APK 文件名同时含 {mode} 且含 {abi} > 仅含 {mode}。
     * 不返回 String 形式的 URL，而是返回完整的 asset JSONObject，
     * 让 Updater 下载后能拿 asset.optLong("size") 做【长度完整性校验】——
     * 解决镜像服务器（尤其 ghproxy 国内反代）半路返回 error html 或提前截断，
     * 但 HTTP 仍然 200，导致下载到一个几十字节的"假 APK"，安装时系统报
     * 「解析软件包时出现问题」这种毫无定位价值的错误。
     */
    public static JSONObject pickDirectApkAsset(JSONObject release) throws Exception {
        if (release == null) return null;
        JSONArray assets = release.optJSONArray("assets");
        if (assets == null || assets.length() == 0) return null;
        String mode = BuildConfig.FLAVOR_mode;
        String abi = BuildConfig.FLAVOR_abi;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject a = assets.getJSONObject(i);
            String name = a.optString("name");
            if (name != null && name.endsWith(".apk") && name.contains(mode) && name.contains(abi)) {
                return a;
            }
        }
        for (int i = 0; i < assets.length(); i++) {
            JSONObject a = assets.getJSONObject(i);
            String name = a.optString("name");
            if (name != null && name.endsWith(".apk") && name.contains(mode)) {
                return a;
            }
        }
        return null;
    }

    private static String pickDirectApkUrl(JSONObject release) throws Exception {
        JSONObject asset = pickDirectApkAsset(release);
        if (asset == null) return null;
        return asset.optString("browser_download_url");
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
     *
     * v5.5.61 变化：
     *  - 不再拼接 jsdelivr（fastly/cdn.jsdelivr 实测：release asset 不在 git tree 上，必 404 Not Found）
     *  - 追加「objects.githubusercontent.com 原始直连」候选：比 github.com/releases/download 少一次 302 跳转，
     *    海外/加速通道更快更稳。实现方式：对 browser_download_url 发一次 GET（不 followRedirect=false），
     *    从最终 Location 里提取对象存储 URL，成功则加为候选第 2 顺位（仅次于 DIRECT）。
     */
    public static List<String> findApkUrls(JSONObject release) {
        List<String> out = new ArrayList<>();
        try {
            String direct = pickDirectApkUrl(release);
            if (direct == null || direct.isEmpty()) return Collections.emptyList();
            // 1) 常规前缀镜像拼接（已按 getMirrorCandidates() 去重+首选放在第一）
            for (String mirror : getMirrorCandidates()) {
                String u = (mirror == null || mirror.isEmpty()) ? direct : mirror + "/" + direct;
                if (u != null && !u.isEmpty() && !out.contains(u)) out.add(u);
            }
            // 2) objects.githubusercontent.com 原始直连（比 github.com 多级 302 跳少，更快）
            try {
                String objUrl = resolveObjectsUrl(direct, PROBE_TIMEOUT_MS);
                if (objUrl != null && !objUrl.isEmpty() && !out.contains(objUrl)) {
                    // 插到所有镜像前缀前面（仅次于 DIRECT），作为 DIRECT 之外的备用"原始直连版本"
                    int insertAt = 0;
                    for (int i = 0; i < out.size(); i++) {
                        String c = out.get(i);
                        if (c != null && (c.startsWith("http://github.com/") || c.startsWith("https://github.com/")
                                || c.equals(direct))) {
                            insertAt = i + 1;
                            break;
                        }
                        if (c != null && !c.isEmpty() && c.startsWith("https://") && c.contains("ghproxy")) break;
                    }
                    out.add(Math.min(insertAt, out.size()), objUrl);
                }
            } catch (Throwable ignored) {
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return out;
    }

    /**
     * 把 https://github.com/owner/repo/releases/download/tag/file.apk 解析为最终 objects.githubusercontent.com 的对象存储 URL。
     * 实现：跟随一次 302 跳转（OkHttp 默认 followRedirects=true，最终停在 objects 节点），
     * 直接返回最终请求的 URL。超时使用 PROBE_TIMEOUT_MS（6s），失败返回 null（不阻塞原流程）。
     */
    private static String resolveObjectsUrl(String browserDownloadUrl, long timeoutMs) {
        if (browserDownloadUrl == null || browserDownloadUrl.isEmpty()) return null;
        OkHttpClient client = OkHttp.client(false, timeoutMs);
        okhttp3.Call call = null;
        Response res = null;
        try {
            Request req = new Request.Builder()
                    .url(browserDownloadUrl)
                    .header("Range", "bytes=0-0")
                    .build();
            call = client.newCall(req);
            res = call.execute();
            // OkHttp 已经 followRedirects 到最终请求，拿到最终 URL
            okhttp3.Request finalReq = res.request();
            if (finalReq != null && finalReq.url() != null) {
                String finalUrl = finalReq.url().toString();
                if (finalUrl != null && (finalUrl.contains("objects.githubusercontent.com") || finalUrl.contains("github-production-release-asset"))) {
                    return finalUrl;
                }
            }
            return null;
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (res != null) try { res.close(); } catch (Throwable ignored) {}
            if (call != null) try { call.cancel(); } catch (Throwable ignored) {}
        }
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

    /**
     * 清洗 GitHub release body（markdown）成干净的用户可读纯文本。
     * 目标：去掉「Full Changelog: https://github.com/...compare/...」行、去掉 markdown 粗体符号 **..**、
     * 去掉 GitHub 自动生成的自动 release 元信息、合并多余空行。
     * 让更新对话框里显示好看的版本历史，而不是一坨 raw markdown。
     */
    public static String cleanReleaseBody(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.isEmpty()) return "";

        // 1) 去掉 GitHub 自动追加的 "**Full Changelog**: https://github.com/..." 行（整行删除）
        s = s.replaceAll("(?im)^\\s*\\*\\*Full Changelog\\*\\*[:：]\\s*https?://\\S+\\s*$", "");
        // 也兼容不带 ** 的 plain 版本
        s = s.replaceAll("(?im)^\\s*Full Changelog[:：]\\s*https?://\\S+\\s*$", "");

        // 2) 去掉 GitHub 自动 release 标题前缀，例如 "## What's Changed" 或 "## 更新" 这些一级标题
        s = s.replaceAll("(?im)^#{1,6}\\s*(What'?s Changed|更新日志|Release Notes|Changelog|New Features|Bug Fixes|Improvements|Other Changes)\\s*$", "");

        // 3) 去掉 markdown 粗体/斜体符号 **text** → text、__text__ → text、*text* → text
        s = s.replaceAll("\\*\\*([^*]+)\\*\\*", "$1");
        s = s.replaceAll("__([^_]+)__", "$1");
        s = s.replaceAll("(?<!\\*)\\*([^*]+)\\*(?!\\*)", "$1");

        // 4) 把 markdown 列表项 - / * / 数字. 统一变成 "· "（更清爽）
        //    但保留原始数字编号（方便用户看版本号）
        s = s.replaceAll("(?m)^\\s*[-*+]\\s+", "· ");
        s = s.replaceAll("(?m)^\\s*\\d+\\.\\s+", ""); // 去掉数字编号前缀

        // 5) 把链接 [text](url) 变成纯 text（不想要 URL 污染）
        s = s.replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1");

        // 6) 压缩多余空行（3 连空行 → 1 空行）
        s = s.replaceAll("\\n{3,}", "\n\n");
        s = s.trim();

        return s;
    }
}