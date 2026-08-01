package com.ssmhdssmhd.mxboxs.utils;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;

import com.ssmhdssmhd.mxboxs.server.Server;
import com.github.catvod.utils.UriUtil;
import com.google.common.net.HttpHeaders;

import java.io.File;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UrlUtil {

    private static final String DEFAULT_UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36";

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
     * 给请求头补齐 Referer 和 User-Agent 的合理默认值。
     * 返回值永远非 null，便于 OkHttp / Media3 免空判。
     */
    public static java.util.Map<String, String> mergeDefaultHeaders(java.util.Map<String, String> userHeaders, String refererUrl) {
        java.util.HashMap<String, String> out = new java.util.HashMap<>();
        if (userHeaders != null) {
            for (java.util.Map.Entry<String, String> e : userHeaders.entrySet())
                if (e != null && e.getKey() != null) out.put(fixHeader(e.getKey()), e.getValue() == null ? "" : e.getValue());
        }
        if (!out.containsKey(HttpHeaders.USER_AGENT) || TextUtils.isEmpty(out.get(HttpHeaders.USER_AGENT))) {
            out.put(HttpHeaders.USER_AGENT, DEFAULT_UA);
        }
        if (!TextUtils.isEmpty(refererUrl) && !out.containsKey(HttpHeaders.REFERER)) {
            out.put(HttpHeaders.REFERER, refererUrl);
        }
        return out;
    }

    public static String defaultUA() {
        return DEFAULT_UA;
    }

    /**
     * 从 HTML/JS 正文中嗅探视频直链 URL（m3u8/mp4/flv 等），返回第一个命中。
     * 保留旧 API 供已有调用直接使用。
     */
    public static String sniffVideo(String body, String baseUrl, String... exts) {
        List<String> all = sniffVideoCandidates(body, baseUrl, 1, exts);
        return all.isEmpty() ? null : all.get(0);
    }

    /**
     * 嗅探最多 topN 个候选视频直链 URL（去重），
     * 按 keys 优先级（m3u8 优先）与「引号匹配优先 / 无引号匹配兜底」顺序返回。
     * 同时支持 JSON 转义 URL、base64 编码 URL、URLDecoder 解码后的二次嗅探。
     */
    public static List<String> sniffVideoCandidates(String body, String baseUrl, int topN, String... exts) {
        List<String> out = new ArrayList<>();
        if (TextUtils.isEmpty(body)) return out;
        if (topN <= 0) topN = 8;
        String[] keys = (exts != null && exts.length > 0) ? exts : new String[]{"m3u8", "mp4", "flv", "m4v", "index.m3u8", "playlist.m3u8", "ts"};
        Set<String> seen = new LinkedHashSet<>();
        // 1) 原始正文里按引号优先 / 无引号兜底扫
        addCandidates(seen, sniffAllByKeys(body, keys, true), baseUrl);
        addCandidates(seen, sniffAllByKeys(body, keys, false), baseUrl);
        // 2) 把 JSON 常见转义 \" 恢复成 " 再扫一轮（很多接口里是把地址塞进字符串）
        try {
            String unescaped = body.replace("\\\"", "\"").replace("\\/", "/");
            if (!unescaped.equals(body)) {
                addCandidates(seen, sniffAllByKeys(unescaped, keys, true), baseUrl);
                addCandidates(seen, sniffAllByKeys(unescaped, keys, false), baseUrl);
            }
        } catch (Throwable ignored) {}
        // 3) URLDecoder 解码一次再扫（有些站会把地址 encode 两三层）
        try {
            String decoded = safeUrlDecode(body);
            if (decoded != null && !decoded.equals(body)) {
                addCandidates(seen, sniffAllByKeys(decoded, keys, true), baseUrl);
                addCandidates(seen, sniffAllByKeys(decoded, keys, false), baseUrl);
            }
        } catch (Throwable ignored) {}
        // 4) 扫 base64 模样的串，尝试解码后再嗅探（atob / encodeURIComponent 组合常见）
        try {
            List<String> b64Pieces = sniffBase64Blobs(body);
            for (String piece : b64Pieces) {
                String decoded = safeBase64Decode(piece);
                if (TextUtils.isEmpty(decoded)) continue;
                addCandidates(seen, sniffAllByKeys(decoded, keys, true), baseUrl);
                addCandidates(seen, sniffAllByKeys(decoded, keys, false), baseUrl);
                // 再尝试 URLDecoder 解一次 base64 解码后的内容
                String decoded2 = safeUrlDecode(decoded);
                if (decoded2 != null && !decoded2.equals(decoded)) {
                    addCandidates(seen, sniffAllByKeys(decoded2, keys, true), baseUrl);
                    addCandidates(seen, sniffAllByKeys(decoded2, keys, false), baseUrl);
                }
            }
        } catch (Throwable ignored) {}
        for (String u : seen) {
            if (u != null && u.startsWith("http")) out.add(u);
            if (out.size() >= topN) break;
        }
        return out;
    }

    private static String safeUrlDecode(String s) {
        if (TextUtils.isEmpty(s)) return null;
        try {
            // 如果串里没 %，那大概率不用 decode
            if (s.indexOf('%') < 0 && s.indexOf('+') < 0) return s;
            return URLDecoder.decode(s, "UTF-8");
        } catch (Throwable t) {
            return null;
        }
    }

    private static List<String> sniffBase64Blobs(String body) {
        List<String> out = new ArrayList<>();
        if (TextUtils.isEmpty(body)) return out;
        // 抓长度在 32~4096 的 base64 模样的串（典型是视频地址被编码）
        try {
            Matcher m = Pattern.compile("(?:atob\\s*\\(\\s*)?[\"']?([A-Za-z0-9+/]{32,4096}={0,3})[\"']?\\s*\\)?", Pattern.CASE_INSENSITIVE).matcher(body);
            while (m.find()) {
                String g = m.group(1);
                if (!TextUtils.isEmpty(g) && (g.length() % 4 == 0)) out.add(g);
                if (out.size() > 32) break;
            }
        } catch (Throwable ignored) {}
        return out;
    }

    private static String safeBase64Decode(String piece) {
        if (TextUtils.isEmpty(piece)) return null;
        try {
            byte[] bytes = Base64.decode(piece, Base64.DEFAULT);
            if (bytes == null || bytes.length == 0) return null;
            String s = new String(bytes, "UTF-8");
            // 看起来像 URL 或包含关键字才返回，避免把图片 base64 之类塞进来
            if (s.contains("http") || s.contains(".m3u8") || s.contains(".mp4") || s.contains(".flv")) return s;
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void addCandidates(Set<String> sink, List<String> candidates, String baseUrl) {
        if (candidates == null || candidates.isEmpty()) return;
        for (String raw : candidates) {
            if (TextUtils.isEmpty(raw)) continue;
            String u = raw;
            if (u.startsWith("//")) u = "https:" + u;
            if (!TextUtils.isEmpty(baseUrl) && (u.startsWith("/") || u.startsWith("./") || u.startsWith("../") || !u.startsWith("http"))) {
                try {
                    String resolved = resolve(baseUrl, u);
                    if (!TextUtils.isEmpty(resolved)) u = resolved;
                } catch (Throwable ignored) {}
            }
            if (!TextUtils.isEmpty(u) && u.startsWith("http")) sink.add(u);
        }
    }

    private static List<String> sniffAllByKeys(String body, String[] keys, boolean quoted) {
        List<String> out = new ArrayList<>();
        for (String key : keys) {
            String regex = quoted
                    ? "([\"'])((?:https?:)?//[^\"'\\s]+?" + Pattern.quote(key) + "(?:[?#][^\"'\\s]*)?)\\1"
                    : "((?:https?:)?//[^\\s<>\"']+?" + Pattern.quote(key) + "(?:[?#][^\\s<>\"']*)?)";
            try {
                Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(body);
                while (m.find()) {
                    String u = quoted ? m.group(2) : m.group(1);
                    if (!TextUtils.isEmpty(u)) out.add(u);
                }
            } catch (Throwable ignored) {}
        }
        return out;
    }
}
