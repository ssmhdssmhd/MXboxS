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
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UrlUtil {

    private static final String DEFAULT_UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36";

    /**
     * 有些第三方解析站会把真实视频页面 URL 包成「假本地代理 URL」返回，形如：
     *   http://127.0.0.1:10079/p/0/127.0.0.1%3A10172/aHR0cHM6Ly9wbGF5ZXI...Lw/index.m3u8
     *   结构: /p/<thread>/<innerHost:port>/<base64>/<suffix>
     *   base64 解码后通常是真实页面 URL（如 https://player.ypls.com/play/R5Ke...）。
     * 由于我们并没有在 10079 / 10172 端口启动任何代理服务器，播放器去连会直接
     * Connection Refused（Network Connection Failed）。
     *
     * 该方法识别这种 URL 并尝试还原出 base64 里真正的 http(s) URL。
     * 还原失败时返回空串，调用方继续走原 URL（交给上层 fallback）。
     */
    public static String unwrapFakeLocalProxy(String url) {
        if (TextUtils.isEmpty(url)) return "";
        try {
            Uri u = Uri.parse(url.trim());
            String scheme = (u.getScheme() == null ? "" : u.getScheme()).toLowerCase();
            if (!scheme.equals("http") && !scheme.equals("https")) return "";
            String host = u.getHost();
            if (host == null) return "";
            // 典型是 127.0.0.1 / localhost
            if (!"127.0.0.1".equals(host) && !"localhost".equalsIgnoreCase(host)) return "";
            // 端口在 9978~9999 范围的才是我们自己的 Nano 服务器，其他端口一律视为第三方伪造
            int port = u.getPort();
            if (port > 0 && port >= 9978 && port <= 9999) return "";
            String path = u.getPath();
            if (TextUtils.isEmpty(path)) return "";
            // 匹配 /p/<n>/.../... 结构 (segment 至少 4 段：/p + thread + innerHostPort + base64 + (optional) suffix)
            List<String> segs = u.getPathSegments();
            if (segs == null || segs.size() < 4) return "";
            if (!"p".equalsIgnoreCase(segs.get(0))) return "";
            // 找到第一段看起来像 base64 的 segment（长度 >= 16 且 base64 字符集）
            java.util.regex.Pattern base64Re = java.util.regex.Pattern.compile("^[A-Za-z0-9+/]{16,}={0,3}$");
            String b64Seg = null;
            for (int i = 2; i < segs.size(); i++) {
                String s = segs.get(i);
                if (base64Re.matcher(s).matches()) { b64Seg = s; break; }
            }
            if (TextUtils.isEmpty(b64Seg)) return "";
            try {
                byte[] bytes = android.util.Base64.decode(b64Seg, android.util.Base64.DEFAULT);
                if (bytes == null || bytes.length == 0) return "";
                String decoded = new String(bytes, "UTF-8").trim();
                if (TextUtils.isEmpty(decoded)) return "";
                String dlc = decoded.toLowerCase();
                if (dlc.startsWith("http://") || dlc.startsWith("https://")) return decoded;
                // 有些 base64 里还 URL 编码了一层
                String maybe = java.net.URLDecoder.decode(decoded, "UTF-8");
                if (!maybe.equals(decoded) && (maybe.toLowerCase().startsWith("http://") || maybe.toLowerCase().startsWith("https://"))) {
                    return maybe;
                }
                return "";
            } catch (Throwable ignored) {
                return "";
            }
        } catch (Throwable ignored) {
            return "";
        }
    }

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
     * 识别一个 URL 是否像「HTML 嗅探接口」（俗称"万能解析"/"嗅探器"）：
     *   - 特征 1：URL 里带 ?url= / &url= / ?v= 这样的"把真实视频页作为参数"的结构
     *     例: https://jx.xmflv.cc/?url=https://v.youku.com/v_show/id_xxx.html
     *   - 特征 2：路径里带 jiexi.php / api.php / jx.php / parse.php 这类典型嗅探接口名
     *   - 特征 3：host 命中一批公开嗅探域名（qq/xmflv/duopian/zf/cfss/... 等关键字）
     *
     * 识别为 HTML 嗅探接口的 URL 不能直接丢给 ExoPlayer（ExoPlayer 会拉到 HTML 文本当视频解析 → 0 KB/s 永久转圈），
     * 必须走 WebView 嗅探 / 后端嗅探接口的解析流程。
     */
    public static boolean isLikelyHtmlSniffer(String url) {
        if (TextUtils.isEmpty(url)) return false;
        try {
            String lc = url.toLowerCase();
            // 视频直链直接放过
            if (lc.contains(".m3u8") || lc.contains(".mp4") || lc.contains(".flv")
                    || lc.contains(".m4v") || lc.contains(".ts") || lc.contains(".mkv")
                    || lc.contains(".webm") || lc.contains(".mov")) return false;
            // 特征 1：?url= / &url= / ?v= 这类"把目标页作为参数"的结构
            if (lc.contains("?url=") || lc.contains("&url=") || lc.contains("?v=") || lc.contains("&v=")) {
                // 再排除一些正常图片/静态资源 CDN 里带 url 参数的场景
                if (!lc.contains(".jpg") && !lc.contains(".png") && !lc.contains(".gif")
                        && !lc.contains(".css") && !lc.contains(".js")) return true;
            }
            // 特征 2：路径里带典型嗅探脚本名
            String[] sniffScripts = {
                    "jiexi.php", "jiexi.php", "api.php", "jx.php", "parse.php", "player.php",
                    "json.php", "getVideo", "play.php", "index.php"
            };
            for (String s : sniffScripts) if (lc.contains(s)) return true;
            // 特征 3：host 命中公开嗅探域名关键字
            String[] sniffHostHints = {
                    "xmflv", "qq.com", "duopian", "cfss", "zf.com", "boosj", "player",
                    "jx.", "jiexi", "sohu", "letv", "bilibili", "mgtv", "iqiyi"
            };
            int q = lc.indexOf("://");
            if (q > 0) {
                String hostPart = lc.substring(q + 3);
                int slash = hostPart.indexOf('/');
                String host = slash > 0 ? hostPart.substring(0, slash) : hostPart;
                for (String h : sniffHostHints) if (host.contains(h)) return true;
            }
        } catch (Throwable ignored) {}
        return false;
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
        // 5) HTML 嗅探接口（比如 xmflv / qq / 虾米 / duopian 等）通常返回一个 HTML，
        //    里面通过 <iframe src="..." /> / <video src="..." /> / <source src="..." />
        //    这类标签指向真正的视频播放页/直链。我们把这些 src 提取出来当候选，再跑一轮 base64/正则嗅探。
        try {
            List<String> tagSrcs = sniffHtmlTagSrcs(body);
            for (String src : tagSrcs) {
                if (TextUtils.isEmpty(src)) continue;
                // src 本身就是带目标视频参数的嗅探 URL（?url= / &url= / ?v=）：
                // 里面 url 参数可能是编码过的视频页，再递归取一层
                addCandidates(seen, sniffAllByKeys(src, keys, true), baseUrl);
                addCandidates(seen, sniffAllByKeys(src, keys, false), baseUrl);
                String decodedTag = safeUrlDecode(src);
                if (decodedTag != null && !decodedTag.equals(src)) {
                    addCandidates(seen, sniffAllByKeys(decodedTag, keys, true), baseUrl);
                    addCandidates(seen, sniffAllByKeys(decodedTag, keys, false), baseUrl);
                }
                // src 里可能嵌了 base64（atob(url) 拼到 src 里）
                List<String> tagB64Pieces = sniffBase64Blobs(src);
                for (String piece : tagB64Pieces) {
                    String decoded = safeBase64Decode(piece);
                    if (TextUtils.isEmpty(decoded)) continue;
                    addCandidates(seen, sniffAllByKeys(decoded, keys, true), baseUrl);
                    addCandidates(seen, sniffAllByKeys(decoded, keys, false), baseUrl);
                }
            }
        } catch (Throwable ignored) {}
        // ===== v5.6.7 新增：6) 混淆 JS 变量赋值嗅探（专门对付 jx.xmflv.cc 这种带 var _0x1ef3 混淆的万能嗅探站）=====
        //     xmflv / qq / cfss / duopian 等公开嗅探站 JS 会把真实视频 URL 赋值给几个常见变量名，
        //     只要能抓到就能省掉一次 WebView 启动，加快起播速度。
        try {
            // 顺序：先抓「带引号的字面量赋值」最靠谱；再抓「window.xxx / config.xxx」属性；
            //       最后再扫一遍 JS 对象字面量里 url: "...", src: "...", video: "..."
            Pattern[] jsp = new Pattern[]{
                    // var now = "https://xxx.m3u8?sign=..." / let playUrl = "https://..." / const video_url = ...
                    Pattern.compile("(?:var|let|const)\\s+(?:now|playUrl|play_url|videoUrl|video_url|playerUrl|player_url|mediaUrl|media_url|sourceUrl|source_url|m3u8|src|url|realUrl|real_url)\\s*=\\s*[\"']([^\"']{12,})[\"']", Pattern.CASE_INSENSITIVE),
                    // window.player_url = "https://..."; window.Xmflv = { url: "..." } 的属性赋值
                    Pattern.compile("(?:window|self|top|globalThis)\\s*\\.\\s*(?:now|playUrl|play_url|videoUrl|video_url|playerUrl|player_url|mediaUrl|media_url|sourceUrl|source_url|url|src|Xmflv|DPlayer)\\s*(?:\\[[^\\]]+\\])?\\s*=\\s*[\"']([^\"']{12,})[\"']", Pattern.CASE_INSENSITIVE),
                    // JS 对象字面量：{ url: "https://xxx.m3u8", ... } 或 { "url": "..." }
                    Pattern.compile("[\"']?(?:url|src|video|m3u8|playUrl|play_url|playerUrl|now)\\s*[\"']?\\s*:\\s*[\"']([^\"']{12,})[\"']", Pattern.CASE_INSENSITIVE),
                    // config.xxx.player.xxx.xxx.url = "..." 点式属性链
                    Pattern.compile("(?:config|player|playerData|option|options|setting|settings)\\s*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*\\s*\\.\\s*(?:url|src|now|video)\\s*=\\s*[\"']([^\"']{12,})[\"']", Pattern.CASE_INSENSITIVE),
            };
            for (Pattern p : jsp) {
                Matcher m = p.matcher(body);
                while (m.find()) {
                    String cand = m.group(1);
                    if (TextUtils.isEmpty(cand)) continue;
                    addCandidates(seen, Arrays.asList(cand), baseUrl);
                    // URLDecode 一遍
                    String dec = safeUrlDecode(cand);
                    if (dec != null && !dec.equals(cand)) addCandidates(seen, Arrays.asList(dec), baseUrl);
                }
            }
            // 再扫一遍 URLDecode 后的 body（很多站 URL 被先 encode 再塞到字符串里）
            String decodedBody = safeUrlDecode(body);
            if (decodedBody != null && !decodedBody.equals(body)) {
                for (Pattern p : jsp) {
                    Matcher m = p.matcher(decodedBody);
                    while (m.find()) {
                        String cand = m.group(1);
                        if (TextUtils.isEmpty(cand)) continue;
                        addCandidates(seen, Arrays.asList(cand), baseUrl);
                        String dec = safeUrlDecode(cand);
                        if (dec != null && !dec.equals(cand)) addCandidates(seen, Arrays.asList(dec), baseUrl);
                    }
                }
            }
            // 最后补：把所有 "..." / '...' 字面量（长度>24 且含 http 或 .m3u8/.mp4 典型特征）全扫一遍，去重
            Matcher anyQuoted = Pattern.compile("[\"']((?:https?:[^\"']{12,})|(?:[^\"']{8,}\\.(?:m3u8|mp4|flv|m4v|ts|mpd)[^\"']*))[\"']", Pattern.CASE_INSENSITIVE).matcher(body);
            while (anyQuoted.find()) {
                String cand = anyQuoted.group(1);
                if (TextUtils.isEmpty(cand)) continue;
                addCandidates(seen, Arrays.asList(cand), baseUrl);
                String dec = safeUrlDecode(cand);
                if (dec != null && !dec.equals(cand)) addCandidates(seen, Arrays.asList(dec), baseUrl);
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

    /**
     * 从 HTML 正文里抓 <iframe src="..."> / <video src="..."> / <source src="...">
     * / <script src="..."> / <embed src="..."> 等常见媒体标签的 src 属性，
     * 用于 HTML 嗅探接口返回的页面再做一次候选扩展。
     * 同时支持单引号 / 双引号 / 无引号（空格前截断）。
     */
    private static List<String> sniffHtmlTagSrcs(String body) {
        List<String> out = new ArrayList<>();
        if (TextUtils.isEmpty(body)) return out;
        try {
            // 兼容大小写与空白
            String regex = "<(?:iframe|video|source|script|embed)[^>]+?\\bsrc\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>\"'`]+))";
            Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(body);
            while (m.find()) {
                String src = m.group(1);
                if (TextUtils.isEmpty(src)) src = m.group(2);
                if (TextUtils.isEmpty(src)) src = m.group(3);
                if (!TextUtils.isEmpty(src)) {
                    String t = src.trim();
                    if (!t.isEmpty()) out.add(t);
                }
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
