package com.ssmhdssmhd.mxboxs.utils;

import android.text.TextUtils;

import com.ssmhdssmhd.mxboxs.setting.Setting;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Prefers;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 卡密验证工具类
 * <p>
 * 从 GitHub 仓库根目录的 kami.txt 拉取有效卡密列表，并校验用户输入的卡密是否有效。
 * 支持 ghproxy / mirror.ghproxy 国内镜像，失败时回退到镜像。
 * 拉取结果会缓存到本地，便于离线校验。
 */
public class KamiUtil {

    /** 仓库根目录的 kami.txt raw 地址 */
    public static final String RAW_URL = "https://raw.githubusercontent.com/" + Github.REPO + "/main/kami.txt";
    /** jsDelivr CDN 加速地址（国内更稳） */
    public static final String JSDELIVR_URL = "https://cdn.jsdelivr.net/gh/" + Github.REPO + "@main/kami.txt";

    /** 缓存 key：上次成功拉取到的卡密集合（分号分隔） */
    private static final String CACHE_KEY = "kami_cache";
    /** 缓存 key：上次成功拉取的时间戳 */
    private static final String CACHE_TIME_KEY = "kami_cache_time";
    /** 缓存有效期：12 小时 */
    private static final long CACHE_TTL = TimeUnit.HOURS.toMillis(12);

    private KamiUtil() {
    }

    /** 拉取远端 kami.txt 全文（依次尝试：镜像 → GitHub raw → jsDelivr） */
    public static String fetchKamiText() {
        String mirror = Github.getMirror();
        // 1. 镜像（ghproxy 国内加速）
        if (!mirror.isEmpty()) {
            String text = OkHttp.string(mirror + "/" + RAW_URL);
            if (looksValid(text)) return text;
        }
        // 2. GitHub raw 直连
        String text = OkHttp.string(RAW_URL);
        if (looksValid(text)) return text;
        // 3. jsDelivr CDN 兜底
        text = OkHttp.string(JSDELIVR_URL);
        if (looksValid(text)) return text;
        // 4. 全部失败
        return "";
    }

    /** 简单判断响应是否像合法的 kami.txt（非空且包含换行或长度 ≥ 16） */
    private static boolean looksValid(String text) {
        if (TextUtils.isEmpty(text)) return false;
        return text.contains("\n") || text.length() >= 16;
    }

    /** 从 kami.txt 文本解析出有效卡密集合（忽略空行和 # 注释行） */
    public static Set<String> parseKamiList(String text) {
        Set<String> set = new HashSet<>();
        if (TextUtils.isEmpty(text)) return set;
        for (String line : text.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.startsWith("#")) continue;
            set.add(trimmed.toLowerCase());
        }
        return set;
    }

    /**
     * 校验卡密是否有效。
     * 先用本地缓存校验，缓存过期或为空时再拉取远端。
     *
     * @param input 用户输入的卡密
     * @return true 表示卡密在 kami.txt 中存在
     */
    public static boolean verify(String input) {
        if (TextUtils.isEmpty(input)) return false;
        String key = input.trim().toLowerCase();
        if (key.isEmpty()) return false;

        // 1. 先用缓存校验（缓存有效期内）
        Set<String> cached = getCachedKamiSet();
        if (cached != null && !cached.isEmpty() && isCacheValid()) {
            return cached.contains(key);
        }

        // 2. 缓存不可用，拉取远端
        String text = fetchKamiText();
        Set<String> remote = parseKamiList(text);
        if (remote.isEmpty()) {
            // 远端拉取失败，回退用缓存（即便过期）
            if (cached != null && !cached.isEmpty()) return cached.contains(key);
            return false;
        }
        // 更新缓存
        saveCache(remote);
        return remote.contains(key);
    }

    /** 强制刷新：拉取远端并校验（用户点"激活"按钮时使用，避免缓存误判） */
    public static boolean verifyFresh(String input) {
        if (TextUtils.isEmpty(input)) return false;
        String key = input.trim().toLowerCase();
        if (key.isEmpty()) return false;
        String text = fetchKamiText();
        Set<String> remote = parseKamiList(text);
        if (remote.isEmpty()) {
            // 远端失败，回退缓存
            Set<String> cached = getCachedKamiSet();
            return cached != null && cached.contains(key);
        }
        saveCache(remote);
        return remote.contains(key);
    }

    // ---------------- 缓存相关 ----------------

    private static boolean isCacheValid() {
        long t = Prefers.getLong(CACHE_TIME_KEY, 0L);
        if (t <= 0) return false;
        return System.currentTimeMillis() - t < CACHE_TTL;
    }

    private static Set<String> getCachedKamiSet() {
        String raw = Prefers.getString(CACHE_KEY, "");
        if (raw.isEmpty()) return new HashSet<>();
        Set<String> set = new HashSet<>();
        for (String s : raw.split(";")) {
            if (!s.isEmpty()) set.add(s);
        }
        return set;
    }

    private static void saveCache(Set<String> set) {
        Prefers.put(CACHE_KEY, String.join(";", set));
        Prefers.put(CACHE_TIME_KEY, System.currentTimeMillis());
    }

    /** 当前是否已激活（本地保存的激活标记 + 已保存的卡密） */
    public static boolean isActivated() {
        return Setting.isKamiActivated() && !TextUtils.isEmpty(Setting.getKami());
    }

    /** 标记为已激活并保存卡密 */
    public static void markActivated(String kami) {
        Setting.putKamiActivated(true);
        Setting.putKami(kami);
    }

    /** 清除激活状态（注销） */
    public static void clearActivation() {
        Setting.putKamiActivated(false);
        Setting.putKami("");
    }
}
