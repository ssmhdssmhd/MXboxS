package com.ssmhdssmhd.mxboxs.utils;

import android.net.Uri;
import android.text.TextUtils;

import com.ssmhdssmhd.mxboxs.server.Server;
import com.github.catvod.utils.UriUtil;
import com.google.common.net.HttpHeaders;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UrlUtil {

    public static Uri uri(String url) {
        url = url.trim().replace("\\", "");
        return url.startsWith("/") ? Uri.fromFile(new File(url)) : Uri.parse(url);
    }

    public static String scheme(String url) {
        return url == null ? "" : scheme(uri(url));
    }

    public static String scheme(Uri uri) {
        String scheme = uri.getScheme();
        return scheme == null ? "" : scheme.toLowerCase().trim();
    }

    public static String host(String url) {
        return url == null ? "" : host(uri(url));
    }

    public static String host(Uri uri) {
        String host = uri.getHost();
        return host == null ? "" : host.toLowerCase().trim();
    }

    public static String path(String url) {
        return url == null ? "" : path(uri(url));
    }

    public static String path(Uri uri) {
        String path = uri.getLastPathSegment();
        return path == null ? "" : path.trim();
    }

    public static String resolve(String baseUri, String referenceUri) {
        return UriUtil.resolve(baseUri, referenceUri);
    }

    public static String convert(String url) {
        String scheme = scheme(url);
        String prefix = scheme + "://";
        return switch (scheme) {
            case "assets" -> url.replace(prefix, Server.get().getAddress("/"));
            case "proxy" -> url.replace(prefix, Server.get().getAddress("/proxy?"));
            case "file" -> Server.get().getAddress("/file/") + Uri.encode(url.substring((prefix).length()), "/");
            default -> url;
        };
    }

    public static String getName(String url) {
        Uri uri = uri(url);
        String path = path(uri);
        String host = host(uri);
        return !path.isEmpty() ? path : !host.isEmpty() ? host : url;
    }

    public static String fixHeader(String key) {
        if (HttpHeaders.USER_AGENT.equalsIgnoreCase(key)) return HttpHeaders.USER_AGENT;
        if (HttpHeaders.REFERER.equalsIgnoreCase(key)) return HttpHeaders.REFERER;
        if (HttpHeaders.COOKIE.equalsIgnoreCase(key)) return HttpHeaders.COOKIE;
        return key;
    }

    /**
     * 从 HTML/JS 正文中嗅探第一个命中的视频直链 URL（m3u8/mp4/flv 等）。
     * 会按传入的关键字顺序（或默认 m3u8/mp4/...）优先匹配。
     * 命中相对路径时会用 baseUrl 做一次解析补齐。
     */
    public static String sniffVideo(String body, String baseUrl, String... exts) {
        if (TextUtils.isEmpty(body)) return null;
        String[] keys = (exts != null && exts.length > 0) ? exts : new String[]{"m3u8", "mp4", "flv", "m4v", "index.m3u8", "playlist.m3u8"};
        String url = sniffByKeys(body, keys, true);
        if (TextUtils.isEmpty(url)) url = sniffByKeys(body, keys, false);
        if (TextUtils.isEmpty(url)) return null;
        if (!TextUtils.isEmpty(baseUrl) && (url.startsWith("/") || url.startsWith("./") || url.startsWith("../") || !url.startsWith("http"))) {
            try {
                String resolved = resolve(baseUrl, url);
                if (!TextUtils.isEmpty(resolved)) url = resolved;
            } catch (Throwable ignored) {
            }
        }
        if (!url.startsWith("http")) return null;
        return url;
    }

    private static String sniffByKeys(String body, String[] keys, boolean quoted) {
        for (String key : keys) {
            String regex = quoted
                    ? "([\"'])((?:https?:)?//[^\"'\\s]+?" + Pattern.quote(key) + "(?:[?#][^\"'\\s]*)?)\\1"
                    : "((?:https?:)?//[^\\s<>\"']+?" + Pattern.quote(key) + "(?:[?#][^\\s<>\"']*)?)";
            try {
                Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(body);
                if (m.find()) {
                    String u = quoted ? m.group(2) : m.group(1);
                    if (!TextUtils.isEmpty(u)) {
                        if (u.startsWith("//")) u = "https:" + u;
                        return u;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
}
