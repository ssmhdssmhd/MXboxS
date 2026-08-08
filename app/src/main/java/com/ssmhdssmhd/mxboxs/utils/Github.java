package com.ssmhdssmhd.mxboxs.utils;

import com.ssmhdssmhd.mxboxs.BuildConfig;
import com.ssmhdssmhd.mxboxs.setting.Setting;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

public class Github {

    public static final String REPO = "ssmhdssmhd/MXboxS";
    public static final String API_LATEST = "https://api.github.com/repos/" + REPO + "/releases/latest";
    public static final String API_LIST = "https://api.github.com/repos/" + REPO + "/releases?per_page=10";

    public static final String MIRROR_DIRECT = "";
    public static final String MIRROR_GHPROXY = "https://ghproxy.com";
    public static final String MIRROR_MIRROR_GHPROXY = "https://mirror.ghproxy.com";
    public static final String MIRROR_GHPS_CAMBRIDGECS = "https://ghps.cambridgecs.co";
    public static final String MIRROR_GH_API_99988866 = "https://gh.api.99988866.xyz";

    /** 公共镜像池（含直连空串）。会被 getMirrorCandidates 去重。**/
    private static final List<String> MIRROR_POOL = Arrays.asList(
            MIRROR_GHPROXY,
            MIRROR_MIRROR_GHPROXY,
            MIRROR_GHPS_CAMBRIDGECS,
            MIRROR_GH_API_99988866,
            MIRROR_DIRECT
    );

    /** 镜像模式索引（与 Setting 中的常量 + Updater.showMirrorDialog 中的顺序保持一致）*/
    public static String getMirror() {
        int mode = Setting.getMirrorMode();
        if (mode == Setting.MIRROR_GHPROXY) return MIRROR_GHPROXY;
        if (mode == Setting.MIRROR_MIRROR_GHPROXY) return MIRROR_MIRROR_GHPROXY;
        return MIRROR_DIRECT;
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