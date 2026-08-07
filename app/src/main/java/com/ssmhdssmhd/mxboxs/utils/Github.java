package com.ssmhdssmhd.mxboxs.utils;

import com.ssmhdssmhd.mxboxs.BuildConfig;
import com.ssmhdssmhd.mxboxs.setting.Setting;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

public class Github {

    public static final String REPO = "ssmhdssmhd/MXboxS";
    public static final String API_LATEST = "https://api.github.com/repos/" + REPO + "/releases/latest";
    public static final String API_LIST = "https://api.github.com/repos/" + REPO + "/releases?per_page=10";

    public static final String MIRROR_DIRECT = "";
    public static final String MIRROR_GHPROXY = "https://ghproxy.com";
    public static final String MIRROR_MIRROR_GHPROXY = "https://mirror.ghproxy.com";

    public static String getApiUrl() {
        return API_LATEST;
    }

    public static String getMirror() {
        int mode = Setting.getMirrorMode();
        if (mode == 1) return MIRROR_GHPROXY;
        if (mode == 2) return MIRROR_MIRROR_GHPROXY;
        return MIRROR_DIRECT;
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

    public static String findApkUrl(JSONObject release) {
        if (release == null) return null;
        try {
            JSONArray assets = release.optJSONArray("assets");
            if (assets == null) return null;

            String mode = BuildConfig.FLAVOR_mode;
            String abi = BuildConfig.FLAVOR_abi;

            String mirror = getMirror();

            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.getJSONObject(i);
                String name = asset.optString("name");
                if (name.endsWith(".apk") && name.contains(mode) && name.contains(abi)) {
                    String downloadUrl = asset.optString("browser_download_url");
                    if (!mirror.isEmpty()) {
                        return mirror + "/" + downloadUrl;
                    }
                    return downloadUrl;
                }
            }

            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.getJSONObject(i);
                String name = asset.optString("name");
                if (name.endsWith(".apk") && name.contains(mode)) {
                    String downloadUrl = asset.optString("browser_download_url");
                    if (!mirror.isEmpty()) {
                        return mirror + "/" + downloadUrl;
                    }
                    return downloadUrl;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
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