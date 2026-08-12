package com.ssmhdssmhd.mxboxs.setting;

import com.ssmhdssmhd.mxboxs.R;
import com.ssmhdssmhd.mxboxs.utils.ResUtil;
import com.github.catvod.utils.Prefers;

import android.text.TextUtils;

public class Setting {

    private static final int MIN_WALL = 0;
    private static final int MAX_WALL = 4;
    private static final int MIN_WALL_TYPE = 0;
    private static final int MAX_WALL_TYPE = 2;
    private static final int MIN_SITE_MODE = 0;
    private static final int MAX_SITE_MODE = 1;
    private static final int MIN_SYNC_MODE = 0;
    private static final int MAX_SYNC_MODE = 2;

    public static String getSwitch(boolean value) {
        return ResUtil.getString(value ? R.string.setting_on : R.string.setting_off);
    }

    public static String getDoh() {
        return Prefers.getString("doh");
    }

    public static void putDoh(String doh) {
        Prefers.put("doh", doh);
    }

    public static String getUa() {
        return Prefers.getString("ua");
    }

    public static void putUa(String ua) {
        Prefers.put("ua", ua);
    }

    public static String getKeyword() {
        return Prefers.getString("keyword");
    }

    public static void putKeyword(String keyword) {
        Prefers.put("keyword", keyword);
    }

    public static String getHot() {
        return Prefers.getString("hot");
    }

    public static void putHot(String hot) {
        Prefers.put("hot", hot);
    }

    public static int getWall() {
        return Math.clamp(Prefers.getInt("wall", 1), MIN_WALL, MAX_WALL);
    }

    public static void putWall(int wall) {
        Prefers.put("wall", Math.clamp(wall, MIN_WALL, MAX_WALL));
    }

    public static int getWallType() {
        return Math.clamp(Prefers.getInt("wall_type", 0), MIN_WALL_TYPE, MAX_WALL_TYPE);
    }

    public static void putWallType(int type) {
        Prefers.put("wall_type", Math.clamp(type, MIN_WALL_TYPE, MAX_WALL_TYPE));
    }

    public static boolean getWallSound() {
        return Prefers.getBoolean("wall_sound");
    }

    public static void putWallSound(boolean sound) {
        Prefers.put("wall_sound", sound);
    }

    public static int getThemeColor() {
        return Prefers.getInt("theme_color", -1);
    }

    public static void putThemeColor(int color) {
        Prefers.put("theme_color", color);
    }

    public static int getWallColor() {
        return Prefers.getInt("wall_color", 0);
    }

    public static void putWallColor(int color) {
        Prefers.put("wall_color", color);
    }

    public static int getDynamicColor() {
        int color = getThemeColor();
        if (color == -1) return 0;
        return color != 0 ? color : getWallColor();
    }

    public static int getSiteMode() {
        return Math.clamp(Prefers.getInt("site_mode"), MIN_SITE_MODE, MAX_SITE_MODE);
    }

    public static void putSiteMode(int mode) {
        Prefers.put("site_mode", Math.clamp(mode, MIN_SITE_MODE, MAX_SITE_MODE));
    }

    public static int getSyncMode() {
        return Math.clamp(Prefers.getInt("sync_mode"), MIN_SYNC_MODE, MAX_SYNC_MODE);
    }

    public static void putSyncMode(int mode) {
        Prefers.put("sync_mode", Math.clamp(mode, MIN_SYNC_MODE, MAX_SYNC_MODE));
    }

    public static boolean isIncognito() {
        return Prefers.getBoolean("incognito");
    }

    public static void putIncognito(boolean incognito) {
        Prefers.put("incognito", incognito);
    }

    public static boolean getUpdate() {
        return Prefers.getBoolean("update", true);
    }

    public static void putUpdate(boolean update) {
        Prefers.put("update", update);
    }

    public static boolean isAdblock() {
        return Prefers.getBoolean("adblock", true);
    }

    public static void putAdblock(boolean adblock) {
        Prefers.put("adblock", adblock);
    }

    public static boolean isZhuyin() {
        return Prefers.getBoolean("zhuyin");
    }

    public static void putZhuyin(boolean zhuyin) {
        Prefers.put("zhuyin", zhuyin);
    }

    public static final String PARSE_SERVER_DEFAULT = "http://114.134.184.91:9002";

    // ===== 镜像模式枚举（与 Github.MIRROR_OPTIONS 的顺序严格一一对应，v5.5.47 起统一以 Github.MIRROR_OPTIONS 为准）=====
    // 0: ghproxy.com（国内）
    // 1: mirror.ghproxy.com（国内）
    // 2: ghps.cambridgecs.co（国内）
    // 3: gh.api.99988866.xyz（国内）
    // 4: ghproxy.net（国内）
    // 5: gh.mirai.org（国内）
    // 6: jsdelivr CDN（海外）
    // 7: GitHub 直连
    // 注意：此顺序故意与 v5.5.42 及以前不同。v5.5.42 默认 mirror_mode=1，
    //       升级后会被解析为 mirror.ghproxy.com，正好帮用户从今日宕机的 ghproxy.com (93.46.8.90) 自动切走。
    public static final int MIRROR_GHPROXY = 0;
    public static final int MIRROR_MIRROR_GHPROXY = 1;
    public static final int MIRROR_GHPS_CAMBRIDGECS = 2;
    public static final int MIRROR_GH_API_99988866 = 3;
    public static final int MIRROR_GHPROXY_NET = 4;
    public static final int MIRROR_GH_MIRAI = 5;
    public static final int MIRROR_JSDELIVR = 6;
    public static final int MIRROR_DIRECT = 7;
    /** 默认索引：对中国大陆用户默认 mirror.ghproxy.com（v5.5.44 之后 ghproxy.com 经常宕机，mirror.ghproxy 更稳） */
    public static final int MIRROR_DEFAULT_INDEX = MIRROR_MIRROR_GHPROXY;

    public static int getMirrorMode() {
        int saved = Prefers.getInt("mirror_mode", MIRROR_DEFAULT_INDEX);
        // 防御：如果是 v5.5.46 及之前保存的 2 (=DIRECT)，现在 DIRECT=7，需显式做一次迁移映射。
        // 其他老值 0/1 还能对得上，保留即可；超出范围用默认索引。
        if (saved == 2 && MIRROR_DIRECT == 7) return MIRROR_DIRECT;
        int totalMirrors = 8;
        if (saved < 0 || saved >= totalMirrors) return MIRROR_DEFAULT_INDEX;
        return saved;
    }

    public static void putMirrorMode(int mode) {
        Prefers.put("mirror_mode", mode);
    }

    public static String getParseServerPrefix() {
        String v = Prefers.getString("parse_server_prefix");
        if (v == null) return PARSE_SERVER_DEFAULT;
        String t = v.trim();
        if (t.isEmpty()) return "";
        return t;
    }

    public static void putParseServerPrefix(String prefix) {
        Prefers.put("parse_server_prefix", prefix == null ? "" : prefix.trim());
    }

    // ---------------- 会员卡密激活 ----------------

    /** 是否已通过卡密激活（本地标记） */
    public static boolean isKamiActivated() {
        return Prefers.getBoolean("kami_activated", false);
    }

    public static void putKamiActivated(boolean activated) {
        Prefers.put("kami_activated", activated);
    }

    /** 已激活保存的卡密 */
    public static String getKami() {
        return Prefers.getString("kami", "");
    }

    public static void putKami(String kami) {
        Prefers.put("kami", kami == null ? "" : kami.trim());
    }

    // ---------------- TG / X 社交搜索配置 ----------------

    /** 扫描保存的用途标记，内部常量 */
    public static final String SOCIAL_PURPOSE_TG = "tg_bot_token";
    public static final String SOCIAL_PURPOSE_X  = "x_bearer_token";

    /** TG Bot Token（形如 123456:ABC...），由扫码或手动粘贴填入，纯本地保存 */
    public static String getTgBotToken() {
        return Prefers.getString("tg_bot_token", "");
    }

    public static void putTgBotToken(String token) {
        Prefers.put("tg_bot_token", token == null ? "" : token.trim());
    }

    public static boolean isTgConnected() {
        String t = getTgBotToken();
        return !TextUtils.isEmpty(t) && t.contains(":") && t.length() > 10;
    }

    /**
     * TG 搜索来源频道用户名列表，逗号分隔。
     * 例如 "subsplease_movies,nyaa_updates,xxx_resource"
     *
     * <p>真实搜索会调 public 预览页 t.me/s/{channel} 做关键词匹配（Bot API 本身不提供全局搜索，
     * 但公开频道列表可直接 HTML 解析出标题/磁链/帖子文本，结果可再合并进 App 搜索）。
     */
    public static String getTgChannelList() {
        return Prefers.getString("tg_channels", "");
    }

    public static void putTgChannelList(String s) {
        Prefers.put("tg_channels", s == null ? "" : s.trim());
    }

    /** X Bearer Token（以 "AAAAAAAAAAAAAAAAAAAA..." 或 "xoxb-" 开头都行，由扫码或粘贴填入） */
    public static String getXBearerToken() {
        return Prefers.getString("x_bearer_token", "");
    }

    public static void putXBearerToken(String token) {
        Prefers.put("x_bearer_token", token == null ? "" : token.trim());
    }

    public static boolean isXConnected() {
        String t = getXBearerToken();
        return !TextUtils.isEmpty(t) && t.length() > 20;
    }

    /** X 搜索请求前可选自定义前缀（自建 X API 代理用；空 = 直连 api.x.com） */
    public static String getXEndpointPrefix() {
        String v = Prefers.getString("x_endpoint_prefix", "");
        if (v == null) return "";
        String t = v.trim();
        if (t.isEmpty()) return "";
        if (t.endsWith("/")) return t.substring(0, t.length() - 1);
        return t;
    }

    public static void putXEndpointPrefix(String prefix) {
        Prefers.put("x_endpoint_prefix", prefix == null ? "" : prefix.trim());
    }
}
