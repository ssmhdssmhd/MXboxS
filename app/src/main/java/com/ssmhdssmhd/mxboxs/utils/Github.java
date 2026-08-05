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
                    String json = OkHttp.string(mirror + url);
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
                        return mirror + downloadUrl;
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
                        return mirror + downloadUrl;
                    }
                    return downloadUrl;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String getApkName() {
        return "MXboxS-" + BuildConfig.FLAVOR_mode + "-" + BuildConfig.FLAVOR_abi + "-" + BuildConfig.VERSION_NAME + ".apk";
    }

    public static String getApkNameWithVersion(String version) {
        return "MXboxS-" + BuildConfig.FLAVOR_mode + "-" + BuildConfig.FLAVOR_abi + "-" + version + ".apk";
    }
}