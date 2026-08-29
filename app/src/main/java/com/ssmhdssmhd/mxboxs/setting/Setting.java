package com.ssmhdssmhd.mxboxs.setting;

import com.ssmhdssmhd.mxboxs.R;
import com.ssmhdssmhd.mxboxs.utils.Github;
import com.ssmhdssmhd.mxboxs.utils.ResUtil;
import com.github.catvod.utils.Prefers;

public class Setting {

    private static final int MIN_WALL = 0;
    private static final int MAX_WALL = 4;
    private static final int MIN_WALL_TYPE = 0;
    private static final int MAX_WALL_TYPE = 2;
    private static final int MIN_SITE_MODE = 0;
    private static final int MAX_SITE_MODE = 1;
    private static final int MIN_SYNC_MODE = 0;
    private static final int MAX_SYNC_MODE = 2;
    // ===== JSON 解析结果里 url / msg 字段取播放地址的策略（v5.7.9 新增，高级设置可改）=====
    // 0: url 优先 + msg 兜底（默认，最通用）
    // 1: 只取 url
    // 2: 只取 msg
    public static final int JSON_EXTRACT_URL_FIRST = 0;
    public static final int JSON_EXTRACT_URL_ONLY = 1;
    public static final int JSON_EXTRACT_MSG_ONLY = 2;

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

    // ===== 镜像模式枚举（与 Github.MIRROR_OPTIONS 的顺序严格一一对应，v5.5.61 起统一以 Github.MIRROR_OPTIONS 为准）=====
    // 0: GitHub 直连（默认）
    // 1: ghproxy.com（国内）
    // 2: mirror.ghproxy.com（国内）
    // 3: ghps.cambridgecs.com（国内，公益反代，偶发 HTTP 200 但返回 HTML 错误页 → 新探针会按 ZIP 魔术自动判失败）
    // 4: gh.api.99988866.xyz（国内）
    // 5: ghproxy.net（国内）
    // 6: gh.mirai.org（国内）
    // 7: gh.1ms.run（国内）
    // 注意：v5.5.61 把 GitHub 直连调整为 index 0（默认）。
    //       老用户 saved 值在 getMirrorMode 里会做迁移：
    //         - 旧 7 (=DIRECT) 现在仍 = 旧 DIRECT=7 的含义没变，但 MIRROR_DIRECT_INDEX_NEW=0，所以 7→0 迁移在 getMirrorMode 里显式做
    //         - 旧 0 (=ghproxy.com) 现在变成 1 → 迁移: 0→1
    //         - 旧 1 (=mirror.ghproxy.com) 现在变成 2 → 迁移: 1→2
    //         - 旧 2 (=ghps.cambridgecs) 现在变成 3 → 迁移: 2→3
    //         - 旧 3 (=99988866) 现在变成 4 → 迁移: 3→4
    //         - 旧 4 (=ghproxy.net) 现在变成 5 → 迁移: 4→5
    //         - 旧 5 (=gh.mirai) 现在变成 6 → 迁移: 5→6
    //         - 旧 6 (=jsdelivr) → 现在 jsdelivr 已从 MIRROR_OPTIONS 删除（实测必 404）→ 退回默认 0
    public static final int MIRROR_DIRECT_NEW = 0;
    public static final int MIRROR_GHPROXY = 1;
    public static final int MIRROR_MIRROR_GHPROXY = 2;
    public static final int MIRROR_GHPS_CAMBRIDGECS = 3;
    public static final int MIRROR_GH_API_99988866 = 4;
    public static final int MIRROR_GHPROXY_NET = 5;
    public static final int MIRROR_GH_MIRAI = 6;
    public static final int MIRROR_GH_1MS = 7;
    /** v5.5.61 默认镜像：GitHub 直连（最快最稳，国内 4G/海外加速通道都能直连 github.com；ghproxy 公益反代 HTTP 200 但 body 是错误页太泛滥，让新探针在下载前自动筛掉它们）。*/
    public static final int MIRROR_DEFAULT_INDEX = MIRROR_DIRECT_NEW;

    public static int getMirrorMode() {
        int saved = Prefers.getInt("mirror_mode", MIRROR_DEFAULT_INDEX);
        int totalMirrors = Github.MIRROR_OPTIONS.size();
        // v5.5.61 迁移：老 index → 新 index（GitHub 直连提前到 0，ghproxy.com 各 +1，旧 jsdelivr=6 废弃）
        switch (saved) {
            case 0: saved = 1; break;          // 旧 0 ghproxy.com → 新 1
            case 1: saved = 2; break;          // 旧 1 mirror.ghproxy.com → 新 2
            case 2: saved = 3; break;          // 旧 2 ghps.cambridgecs → 新 3
            case 3: saved = 4; break;          // 旧 3 99988866 → 新 4
            case 4: saved = 5; break;          // 旧 4 ghproxy.net → 新 5
            case 5: saved = 6; break;          // 旧 5 gh.mirai → 新 6
            case 6: saved = MIRROR_DEFAULT_INDEX; break; // 旧 6 jsdelivr (已废弃) → 默认直连 0
            case 7: saved = 0; break;          // 旧 7 DIRECT → 新 0 DIRECT
            default: break;
        }
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

    // ---------------- 高级设置：解锁状态 ----------------
    // 注意：偏好键仍沿用历史名 "social_search_unlocked"，以保证老用户已解锁的状态在升级后仍然有效。

    /** 高级设置是否已解锁（在主设置页点击版本号 20 次后解锁） */
    public static boolean isAdvancedUnlocked() {
        return Prefers.getBoolean("social_search_unlocked", false);
    }

    public static void putAdvancedUnlocked(boolean unlocked) {
        Prefers.put("social_search_unlocked", unlocked);
    }

    public static int getJsonExtractStrategy() {
        int v = Prefers.getInt("json_extract_strategy", JSON_EXTRACT_URL_FIRST);
        if (v < 0 || v > 2) v = JSON_EXTRACT_URL_FIRST;
        return v;
    }

    public static void putJsonExtractStrategy(int strategy) {
        int v = strategy;
        if (v < 0 || v > 2) v = JSON_EXTRACT_URL_FIRST;
        Prefers.put("json_extract_strategy", v);
    }
}
