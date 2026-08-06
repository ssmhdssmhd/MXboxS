package com.ssmhdssmhd.mxboxs.utils;

import com.ssmhdssmhd.mxboxs.BuildConfig;
import com.ssmhdssmhd.mxboxs.setting.Setting;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

public class Github {

    public static final String REPO = "ssmhdssmhd/MXboxS";
    public static final String API_LATEST = "https://api.github.com/repos/" + REPO + "/releases/latest";

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
            java.util.regex.Pattern p2 = java.util.regex.Pattern.compile("([0-9]+\\.[0-9]+\\.[0-9]+");
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