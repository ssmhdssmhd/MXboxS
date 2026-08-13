package com.ssmhdssmhd.mxboxs.player;

import android.text.TextUtils;

import com.github.catvod.utils.Prefers;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * AI 源质量评分存储：按 siteKey 记录解析成功率、平均起播耗时、切源率。
 * <p>
 * 数据持久化在 SharedPreferences（JSON），轻量、无 Room 依赖。
 * 搜索结果排序时用 {@link #getScore(String)} 加权，质量高的源排前面，
 * 用户不用手动"换源"。
 * <p>
 * 评分模型（0-100）：
 * <pre>
 * score = 50                                        // 基线
 *       + 30 * successRate                          // 成功率权重最大
 *       - 15 * (avgParseMs / 8000ms)               // 起播越慢扣分越多
 *       - 20 * switchAwayRate                       // 用户中途切走 = 体验差
 * </pre>
 */
public final class SourceQualityStore {

    private static final String KEY_PREFIX = "sq_";  // sq_<siteKey>
    private static final int MAX_SAMPLES = 50;       // 滑动窗口：最多记 50 次

    private SourceQualityStore() {}

    /** 记录一次解析结果（成功/失败）+ 起播耗时。 */
    public static void recordParse(String siteKey, boolean success, long parseDurationMs) {
        if (TextUtils.isEmpty(siteKey)) return;
        Stats stats = load(siteKey);
        stats.samples = Math.min(stats.samples + 1, MAX_SAMPLES);
        stats.successCount += success ? 1 : 0;
        // 起播耗时 EWMA 平滑
        if (success && parseDurationMs > 0) {
            stats.avgParseMs = stats.avgParseMs == 0
                    ? parseDurationMs
                    : (long) (stats.avgParseMs * 0.7 + parseDurationMs * 0.3);
        }
        save(siteKey, stats);
    }

    /** 记录用户中途切走（换源/退出），用于计算 switchAwayRate。 */
    public static void recordSwitchAway(String siteKey) {
        if (TextUtils.isEmpty(siteKey)) return;
        Stats stats = load(siteKey);
        stats.switchAwayCount++;
        save(siteKey, stats);
    }

    /**
     * 获取源质量评分 0-100。数据不足（samples < 3）时返回 50（中性）。
     */
    public static int getScore(String siteKey) {
        if (TextUtils.isEmpty(siteKey)) return 50;
        Stats stats = load(siteKey);
        if (stats.samples < 3) return 50;
        double successRate = (double) stats.successCount / stats.samples;
        double timePenalty = Math.min(1.0, stats.avgParseMs / 8000.0);
        double switchRate = Math.min(1.0, (double) stats.switchAwayCount / Math.max(1, stats.samples));
        double score = 50
                + 30.0 * successRate
                - 15.0 * timePenalty
                - 20.0 * switchRate;
        return (int) Math.max(0, Math.min(100, Math.round(score)));
    }

    // ===== 内部 =====

    private static final class Stats {
        int samples = 0;
        int successCount = 0;
        long avgParseMs = 0;
        int switchAwayCount = 0;
    }

    private static Stats load(String siteKey) {
        Stats s = new Stats();
        try {
            String json = Prefers.getString(KEY_PREFIX + siteKey);
            if (json != null && !json.isEmpty()) {
                JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                s.samples = obj.has("n") ? obj.get("n").getAsInt() : 0;
                s.successCount = obj.has("ok") ? obj.get("ok").getAsInt() : 0;
                s.avgParseMs = obj.has("ms") ? obj.get("ms").getAsLong() : 0;
                s.switchAwayCount = obj.has("sw") ? obj.get("sw").getAsInt() : 0;
            }
        } catch (Throwable ignored) {}
        return s;
    }

    private static void save(String siteKey, Stats s) {
        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("n", s.samples);
            obj.addProperty("ok", s.successCount);
            obj.addProperty("ms", s.avgParseMs);
            obj.addProperty("sw", s.switchAwayCount);
            Prefers.put(KEY_PREFIX + siteKey, obj.toString());
        } catch (Throwable ignored) {}
    }
}
