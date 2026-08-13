package com.ssmhdssmhd.mxboxs.utils;

import com.ssmhdssmhd.mxboxs.App;
import com.github.catvod.utils.Prefers;

import java.util.UUID;

/**
 * Feature Flag / A-B 分桶骨架。
 * <p>
 * 基于设备唯一标识尾号做稳定分桶（同一设备每次结果一致），
 * 用于 AI 新功能灰度发布：先 10% 灰度 → 数据好 → 全量。
 * <p>
 * 用法：
 * <pre>
 * if (FeatureFlags.isEnabled(FeatureFlags.LLM_SNIFFER)) { ... }
 * if (FeatureFlags.bucketPercent() < 10) { // 灰度 10% }
 * </pre>
 */
public final class FeatureFlags {

    // ===== Flag 常量 =====
    /** LLM 嗅探（常规嗅探全失败后调 LLM 提取候选 URL）。 */
    public static final String LLM_SNIFFER = "ff_llm_sniffer";
    /** AI 源质量评分（搜索结果按历史质量加权排序）。 */
    public static final String SOURCE_QUALITY = "ff_source_quality";
    /** AI 预解析下一集（进度 85% 时后台预缓存下一集直链）。 */
    public static final String PREPARSE_NEXT = "ff_preparse_next";
    /** AI 超分增强（预留，需 NCNN/模型文件，暂不生效）。 */
    public static final String AI_SUPER_RES = "ff_ai_super_res";

    private static final String KEY_DEVICE_ID = "ff_device_id";
    private static final String KEY_MASTER = "ff_master_switch";  // 总灰度开关

    private static volatile int bucketCache = -1;

    private FeatureFlags() {}

    /** 总灰度开关：关闭时所有 flag 都返回 false。 */
    public static boolean isMasterEnabled() {
        return Prefers.getBoolean(KEY_MASTER, true);
    }

    public static void setMasterEnabled(boolean enabled) {
        Prefers.put(KEY_MASTER, enabled);
    }

    /**
     * 某个 feature flag 是否生效。
     * 条件：总开关开 + 该 flag 已置为 true + 设备在灰度桶内。
     *
     * @param flag   flag 常量
     * @param rollout 灰度比例 0-100（100 = 全量）
     */
    public static boolean isEnabled(String flag, int rollout) {
        if (!isMasterEnabled()) return false;
        if (!Prefers.getBoolean(flag, true)) return false;
        return bucketPercent() < rollout;
    }

    /** 设备灰度桶编号 0-99（稳定，同一设备每次一致）。 */
    public static int bucketPercent() {
        if (bucketCache >= 0) return bucketCache;
        String id = Prefers.getString(KEY_DEVICE_ID);
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString();
            Prefers.put(KEY_DEVICE_ID, id);
        }
        // 用 id 的 hashCode 取模 100，稳定分桶
        bucketCache = Math.abs(id.hashCode()) % 100;
        return bucketCache;
    }

    /** 手动开关某个 flag（高级设置里用）。 */
    public static void setFlag(String flag, boolean enabled) {
        Prefers.put(flag, enabled);
    }

    public static boolean isFlagOn(String flag) {
        return Prefers.getBoolean(flag, true);
    }
}
