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
    public static final String MIRROR_GHPS_CAMBRIDGECS = "https://ghps.cambridgecs.co";
    public static final String MIRROR_GH_API_99988866 = "https://gh.api.99988866.xyz";
    /** 国内镜像补充：ghproxy.net（与 ghproxy.com 非同一服务）、gh.mirai.org（镜像 gh release）、gh-proxy.com（常见公益站） */
    public static final String MIRROR_GHPROXY_NET = "https://ghproxy.net";
    public static final String MIRROR_GH_MIRAI = "https://gh.mirai.org";
    /** 海外镜像补充：objects.githubusercontent.com 直连 + JSDelivr（静态文件） + 欧洲 gh.123456789.xyz */
    public static final String MIRROR_JSDELIVR_CN = "https://cdn.jsdelivr.net/gh";
    public static final String MIRROR_JSDELIVR_FASTLY = "https://fastly.jsdelivr.net/gh";
    public static final String MIRROR_CF_CN = "https://gh.tmoe.me";

    /** 显示名 → 前缀，用户 UI 下拉选单选 */
    public static final LinkedHashMap<String, String> MIRROR_OPTIONS = new LinkedHashMap<>();
    static {
        MIRROR_OPTIONS.put("ghproxy.com（国内）", MIRROR_GHPROXY);
        MIRROR_OPTIONS.put("mirror.ghproxy.com（国内）", MIRROR_MIRROR_GHPROXY);
        MIRROR_OPTIONS.put("ghps.cambridgecs.co（国内）", MIRROR_GHPS_CAMBRIDGECS);
        MIRROR_OPTIONS.put("gh.api.99988866.xyz（国内）", MIRROR_GH_API_99988866);
        MIRROR_OPTIONS.put("ghproxy.net（国内）", MIRROR_GHPROXY_NET);
        MIRROR_OPTIONS.put("gh.mirai.org（国内）", MIRROR_GH_MIRAI);
        MIRROR_OPTIONS.put("jsdelivr CDN（海外）", MIRROR_JSDELIVR_FASTLY);
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
            MIRROR_CF_CN
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
            MIRROR_DIRECT
    );

    /** 并行 HEAD 探测超时时长（毫秒）。给每个镜像最多 4s，超过就认为当前网络到这个镜像延迟过高。 */
    private static final long PING_TIMEOUT_MS = 4000L;

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
            for (String mirror : getMirrorCandidates()) {
                String u = (mirror == null || mirror.isEmpty()) ? direct : mirror + "/" + direct;
                if (u != null && !u.isEmpty() && !out.contains(u)) out.add(u);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return out;
    }

    /**
     * 从 Release 的 APK asset 文件名中提取版本号。
     * 文件名格式：MXboxS-mobile-arm64_v8a-5.5.40.apk → 提取 "5.5.40"。
     * 用于 tag_name 不是 vX.Y.Z 格式（如 MXboxS-latest 自动预发布 tag）时的版本来源。
     */
    public static String extractVersionFromAssets(JSONObject release) {
        if (release == null) return "";
        try {
            JSONArray assets = release.optJSONArray("assets");
            if (assets == null || assets.length() == 0) return "";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("MXboxS-[A-Za-z0-9_-]+-([0-9]+\\.[0-9]+\\.[0-9]+)\\.apk");
            for (int i = 0; i < assets.length(); i++) {
                String name = assets.getJSONObject(i).optString("name");
                java.util.regex.Matcher m = p.matcher(name);
                if (m.matches()) return m.group(1);
            }
            // 兜底：从 APK 文件名里找 X.Y.Z 数字点分格式
            java.util.regex.Pattern p2 = java.util.regex.Pattern.compile("([0-9]+\\.[0-9]+\\.[0-9]+)");
            for (int i = 0; i < assets.length(); i++) {
                String name = assets.getJSONObject(i).optString("name");
                if (!name.endsWith(".apk")) continue;
                java.util.regex.Matcher m = p2.matcher(name);
                if (m.find()) return m.group(1);
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    public static String getApkName() {
        return "MXboxS-" + BuildConfig.FLAVOR_mode + "-" + BuildConfig.FLAVOR_abi + "-" + BuildConfig.VERSION_NAME + ".apk";
    }

    public static String getApkNameWithVersion(String version) {
        return "MXboxS-" + BuildConfig.FLAVOR_mode + "-" + BuildConfig.FLAVOR_abi + "-" + version + ".apk";
    }
}