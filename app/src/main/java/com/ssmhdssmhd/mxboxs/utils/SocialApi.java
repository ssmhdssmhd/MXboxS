package com.ssmhdssmhd.mxboxs.utils;

import android.text.TextUtils;

import com.github.catvod.net.OkHttp;
import com.ssmhdssmhd.mxboxs.setting.Setting;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * TG / X 搜索与连接验证工具（纯网络调用，不涉及 UI）。
 *
 * <p>原则：
 *   1. 所有 Token 只从 {@link Setting} 读取，不上报、不写入日志；
 *   2. 连接失败一律用 Result.ok=false + 可读 message 反馈给 UI；
 *   3. 网络必须放在后台线程调用（AsyncTask / Thread / Worker，此处不做调度，由调用方保证）。
 *
 * @author mxboxs integrator
 */
public final class SocialApi {

    // ==================== 限速 + 开关门控（用户需求：不要搜索太快，避免被封账号）====================

    /** 最近一次发起 TG HTTP 请求的墙上时钟时间戳（ms）；配合 getSocialTgMinIntervalMs 做节流。 */
    private static volatile long sLastTgAtMs = 0L;
    /** 最近一次发起 X HTTP 请求的墙上时钟时间戳。 */
    private static volatile long sLastXAtMs  = 0L;
    /** X 的 testX / searchX 虽然是两个方法，但用户仍可能在同一秒内两次点测试 → 仍然按同一把锁 sleep。*/
    private static final Object X_LOCK = new Object();
    /** TG 同理。*/
    private static final Object TG_LOCK = new Object();

    /** 所有对外 public 方法统一先过门控：
     *  ① isSocialSearchEnabled() == false → 直接返回明确的 fail 结果（UI 也会告知用户开关关了）；
     *  ② 然后按目标走一次 sleep 节流（节流失败时不抛异常，continue 保证请求仍能发出去，避免用户调了过大的值反而卡死）。
     *
     * <p>注：testTgBot/testX 虽然不是搜索，但 X Bearer Token 一旦短时间大量调用 /users/me 同样会被 tier limit 打 429，
     * 所以同样纳入限速范围（更保守更安全）。
     */
    private static Result preflightTg() {
        if (!Setting.isSocialSearchEnabled()) return Result.fail("社交搜索总开关已关闭，跳过 TG 请求。");
        long want = Setting.getSocialTgMinIntervalMs();
        if (want > 0L) rateLimitSleep(TG_LOCK, want, new long[]{0}, TAG_TG);
        return null;
    }
    private static Result preflightX() {
        if (!Setting.isSocialSearchEnabled()) return Result.fail("社交搜索总开关已关闭，跳过 X 请求。");
        long want = Setting.getSocialXMinIntervalMs();
        if (want > 0L) rateLimitSleep(X_LOCK, want, new long[]{0}, TAG_X);
        return null;
    }
    private static final boolean TAG_TG = true;
    private static final boolean TAG_X  = false;

    /** 让当前调用线程 sleep 足够时间，保证当前距上次实际发起请求的间隔 >= minIntervalMs。
     *  若中途 InterruptedException → 清标记并提前返回，避免吞掉 interrupt。*/
    private static void rateLimitSleep(Object lock, long minIntervalMs, long[] unused0, boolean isTg) {
        // 给 TG/X 各保留独立的 lastAt
        synchronized (lock) {
            long now = System.currentTimeMillis();
            long last = isTg ? sLastTgAtMs : sLastXAtMs;
            long waitMs = last == 0L ? 0L : (minIntervalMs - (now - last));
            if (waitMs > 0L) {
                try { Thread.sleep(waitMs); }
                catch (InterruptedException ie) { Thread.currentThread().interrupt(); /* caller 若支持会随后退出 */ }
            }
            // 修正后的「now」（睡完之后）
            long stamped = System.currentTimeMillis();
            if (isTg) sLastTgAtMs = stamped; else sLastXAtMs = stamped;
        }
    }

    public static final class Result {
        public final boolean ok;
        public final String message;
        /** 搜索时非空；连接验证时可能为空。 */
        public final List<Hit> hits;

        public Result(boolean ok, String message, List<Hit> hits) {
            this.ok = ok;
            this.message = message == null ? "" : message;
            this.hits = hits == null ? Collections.<Hit>emptyList() : hits;
        }

        public static Result ok(String msg) { return new Result(true, msg, null); }
        public static Result ok(String msg, List<Hit> h) { return new Result(true, msg, h); }
        public static Result fail(String msg) { return new Result(false, msg, null); }
    }

    public static final class Hit {
        public final String source; // "tg" / "x"
        public final String title;
        public final String content;
        public final String url;

        public Hit(String source, String title, String content, String url) {
            this.source = source;
            this.title = title == null ? "" : title;
            this.content = content == null ? "" : content;
            this.url = url == null ? "" : url;
        }

        @Override
        public String toString() {
            if (!TextUtils.isEmpty(title)) return "[" + source + "] " + title;
            String c = content.length() > 60 ? content.substring(0, 60) + "…" : content;
            return "[" + source + "] " + c;
        }
    }

    private SocialApi() {}

    // ===================== TG =====================

    /**
     * 验证 TG Bot Token 有效性（调用 getMe）。
     * @return ok=true 时 message 形如 "@BotFatherBot id=123456"
     */
    public static Result testTgBot() {
        Result pf = preflightTg();
        if (pf != null) return pf;
        String token = Setting.getTgBotToken();
        if (TextUtils.isEmpty(token)) return Result.fail("请先扫码或粘贴 TG Bot Token");
        try {
            String url = "https://api.telegram.org/bot" + token + "/getMe";
            String body = OkHttp.string(url);
            JSONObject j = new JSONObject(body);
            if (!j.optBoolean("ok")) {
                String desc = j.optString("description", "unknown");
                int ec = j.optInt("error_code", -1);
                return Result.fail("TG Bot 校验失败 (" + ec + "): " + desc);
            }
            JSONObject r = j.getJSONObject("result");
            long id = r.optLong("id", 0);
            String fn = r.optString("first_name", "");
            String un = r.optString("username", "");
            StringBuilder sb = new StringBuilder();
            sb.append("TG Bot 已连接");
            if (!TextUtils.isEmpty(fn)) sb.append(" 昵称: ").append(fn);
            if (!TextUtils.isEmpty(un)) sb.append("  (@").append(un).append(")");
            if (id != 0) sb.append("  id=").append(id);
            return Result.ok(sb.toString());
        } catch (Throwable t) {
            return Result.fail("TG Bot 连接异常: " + safeMsg(t));
        }
    }

    /**
     * 在用户配置的公开频道列表里按关键词做 HTML 搜索（公开页 t.me/s/<channel>）。
     * 返回命中帖子列表（按频道顺序，每频道最多 5 条）。
     */
    public static Result searchTg(String keyword, int maxPerChannel) {
        Result pf = preflightTg();
        if (pf != null) return pf;
        if (TextUtils.isEmpty(keyword)) return Result.fail("关键词为空");
        String list = Setting.getTgChannelList();
        if (TextUtils.isEmpty(list)) return Result.fail("请先在设置里配置 TG 搜索频道列表（逗号分隔）");
        String[] channels = list.split("[,，;；\\s]+");
        List<Hit> hits = new ArrayList<>();
        StringBuilder failures = new StringBuilder();
        int limit = maxPerChannel > 0 ? maxPerChannel : 5;
        Pattern p = Pattern.compile(Pattern.quote(keyword), Pattern.CASE_INSENSITIVE);
        for (String raw : channels) {
            String ch = cleanChannel(raw);
            if (TextUtils.isEmpty(ch)) continue;
            try {
                String html = OkHttp.string("https://t.me/s/" + ch);
                List<String> posts = parseTelegramChannelPosts(html);
                int got = 0;
                for (String post : posts) {
                    Matcher m = p.matcher(post);
                    if (m.find()) {
                        hits.add(new Hit("tg", "#" + ch, snippetOf(post, keyword), "https://t.me/s/" + ch));
                        got++;
                        if (got >= limit) break;
                    }
                }
            } catch (Throwable t) {
                if (failures.length() > 0) failures.append("; ");
                failures.append(ch).append(": ").append(safeMsg(t));
            }
        }
        String msg;
        if (hits.isEmpty()) {
            msg = failures.length() > 0 ? ("未命中；错误: " + failures) : "未命中任何公开帖子";
            return new Result(failures.length() == 0, msg, hits);
        }
        return Result.ok("命中 " + hits.size() + " 条" + (failures.length() > 0 ? "（部分频道失败: " + failures + "）" : ""), hits);
    }

    private static String cleanChannel(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.startsWith("@")) t = t.substring(1);
        if (t.startsWith("https://t.me/")) t = t.substring("https://t.me/".length());
        if (t.startsWith("t.me/")) t = t.substring("t.me/".length());
        if (t.startsWith("s/")) t = t.substring(2);
        if (t.endsWith("/")) t = t.substring(0, t.length() - 1);
        int sp = t.indexOf('/');
        if (sp > 0) t = t.substring(0, sp);
        return t;
    }

    /** 非常朴素的 TG channel preview 解析：提取 <div class="tgme_widget_message_text"> 里的可见文本。 */
    private static List<String> parseTelegramChannelPosts(String html) {
        if (TextUtils.isEmpty(html)) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        Pattern block = Pattern.compile("class=\"tgme_widget_message_text\"[^>]*>([\\s\\S]*?)</div>");
        Matcher mb = block.matcher(html);
        while (mb.find()) {
            String raw = mb.group(1);
            if (raw == null) continue;
            String text = raw
                    .replaceAll("<br[^>]*>", "\n")
                    .replaceAll("<[^>]+>", "")
                    .replace("&nbsp;", " ")
                    .replace("&amp;", "&")
                    .replace("&gt;", ">")
                    .replace("&lt;", "<")
                    .replace("&quot;", "\"")
                    .replaceAll("[ \t]+", " ")
                    .replaceAll("\n{3,}", "\n\n")
                    .trim();
            if (!TextUtils.isEmpty(text)) out.add(text);
        }
        return out;
    }

    // ===================== X =====================

    /** 验证 X Bearer Token：GET /2/users/me。 */
    public static Result testX() {
        Result pf = preflightX();
        if (pf != null) return pf;
        String token = Setting.getXBearerToken();
        if (TextUtils.isEmpty(token)) return Result.fail("请先扫码或粘贴 X Bearer Token");
        try {
            String prefix = Setting.getXEndpointPrefix();
            String url = (TextUtils.isEmpty(prefix) ? "https://api.x.com" : prefix) + "/2/users/me";
            OkHttpClient cli = OkHttp.client();
            Request req = new Request.Builder()
                    .url(url)
                    .headers(Headers.of("Authorization",
                            token.toLowerCase().startsWith("bearer ") ? token : ("Bearer " + token),
                            "User-Agent", "MXboxS/" + com.ssmhdssmhd.mxboxs.BuildConfig.VERSION_NAME))
                    .get()
                    .build();
            Response resp = cli.newCall(req).execute();
            int code = resp.code();
            String body = resp.body() == null ? "" : resp.body().string();
            if (code == 200) {
                JSONObject j = new JSONObject(body == null ? "{}" : body);
                JSONObject d = j.optJSONObject("data");
                if (d != null) {
                    String n = d.optString("name", "");
                    String sn = d.optString("username", "");
                    String id = d.optString("id", "");
                    StringBuilder sb = new StringBuilder();
                    sb.append("X 已连接");
                    if (!TextUtils.isEmpty(n)) sb.append(" 昵称: ").append(n);
                    if (!TextUtils.isEmpty(sn)) sb.append("  @").append(sn);
                    if (!TextUtils.isEmpty(id)) sb.append("  id=").append(id);
                    return Result.ok(sb.toString());
                }
                return Result.ok("X 已连接 (200 OK)");
            }
            String msg = code + " " + (resp.message() == null ? "" : resp.message());
            if (!TextUtils.isEmpty(body) && body.length() < 500) msg += " body=" + body;
            return Result.fail("X 校验失败: " + msg);
        } catch (Throwable t) {
            return Result.fail("X 连接异常: " + safeMsg(t));
        }
    }

    /** X v2 最近 7 天搜索。maxResults 合法范围 [10,100]，超限会被服务端 400。 */
    public static Result searchX(String keyword, int maxResults) {
        Result pf = preflightX();
        if (pf != null) return pf;
        if (TextUtils.isEmpty(keyword)) return Result.fail("关键词为空");
        String token = Setting.getXBearerToken();
        if (TextUtils.isEmpty(token)) return Result.fail("请先扫码或粘贴 X Bearer Token");
        int n = Math.max(10, Math.min(100, maxResults));
        try {
            String prefix = Setting.getXEndpointPrefix();
            String base = TextUtils.isEmpty(prefix) ? "https://api.x.com" : prefix;
            // 按 RFC 3986 拼 query，keyword 再简单做 URL encode（不用 URLEncoder 避免 + 与 %20 的差异，OkHttp 里已有拦截器但这里自己拼的 query 仍然要转）
            String url = base + "/2/search/recent?max_results=" + n + "&query=" + encodeQuery(keyword);
            OkHttpClient cli = OkHttp.client();
            Request req = new Request.Builder()
                    .url(url)
                    .headers(Headers.of("Authorization",
                            token.toLowerCase().startsWith("bearer ") ? token : ("Bearer " + token),
                            "User-Agent", "MXboxS/" + com.ssmhdssmhd.mxboxs.BuildConfig.VERSION_NAME))
                    .get()
                    .build();
            Response resp = cli.newCall(req).execute();
            int code = resp.code();
            String body = resp.body() == null ? "" : resp.body().string();
            if (code != 200) {
                String msg = code + " " + (resp.message() == null ? "" : resp.message());
                if (!TextUtils.isEmpty(body) && body.length() < 600) msg += " body=" + body;
                return Result.fail("X 搜索失败: " + msg);
            }
            JSONObject root = new JSONObject(body == null ? "{}" : body);
            JSONArray data = root.optJSONArray("data");
            List<Hit> hits = new ArrayList<>();
            if (data != null) {
                for (int i = 0; i < data.length(); i++) {
                    JSONObject d = data.optJSONObject(i);
                    if (d == null) continue;
                    String id = d.optString("id", "");
                    String text = d.optString("text", "");
                    String auId = d.optString("author_id", "");
                    String title = TextUtils.isEmpty(auId) ? "推文" : ("用户 " + auId + " 的推文");
                    String u = TextUtils.isEmpty(id) ? "https://x.com/home" : ("https://x.com/i/web/status/" + id);
                    hits.add(new Hit("x", title, text, u));
                }
            }
            return Result.ok("命中 " + hits.size() + " 条推文", hits);
        } catch (Throwable t) {
            return Result.fail("X 搜索异常: " + safeMsg(t));
        }
    }

    // ===================== 小工具 =====================

    private static String snippetOf(String post, String kw) {
        if (post == null) return "";
        if (TextUtils.isEmpty(kw)) {
            return post.length() <= 200 ? post : post.substring(0, 200) + "…";
        }
        int idx = Math.max(0, indexOfIgnoreCase(post, kw));
        int start = Math.max(0, idx - 40);
        int end = Math.min(post.length(), idx + kw.length() + 80);
        String s = post.substring(start, end);
        if (start > 0) s = "…" + s;
        if (end < post.length()) s = s + "…";
        return s;
    }

    private static int indexOfIgnoreCase(String src, String sub) {
        if (src == null || sub == null) return -1;
        int n = src.length();
        int m = sub.length();
        if (m == 0) return 0;
        for (int i = 0; i + m <= n; i++) {
            if (src.regionMatches(true, i, sub, 0, m)) return i;
        }
        return -1;
    }

    private static String encodeQuery(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '~') {
                sb.append(c);
            } else {
                byte[] bytes = utf8Bytes(String.valueOf(c));
                for (byte b : bytes) sb.append(String.format("%%%02X", (b & 0xff)));
            }
        }
        return sb.toString();
    }

    private static byte[] utf8Bytes(String s) {
        try { return s.getBytes("UTF-8"); } catch (Throwable t) { return s.getBytes(); }
    }

    private static String safeMsg(Throwable t) {
        if (t == null) return "(unknown error)";
        String cn = t.getClass().getSimpleName();
        String m = t.getMessage();
        if (m != null && m.length() > 160) m = m.substring(0, 160) + "…";
        if (TextUtils.isEmpty(m)) return cn;
        return cn + ": " + m;
    }
}
