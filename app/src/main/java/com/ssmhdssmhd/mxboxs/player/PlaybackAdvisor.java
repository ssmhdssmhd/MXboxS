package com.ssmhdssmhd.mxboxs.player;

import androidx.media3.common.util.Clock;
import androidx.media3.exoplayer.upstream.BandwidthMeter;

import com.ssmhdssmhd.mxboxs.setting.PlayerSetting;
import com.github.catvod.utils.Prefers;

import java.util.concurrent.TimeUnit;

/**
 * AI 播放优化决策器：基于 ExoPlayer BandwidthMeter 的实时带宽估算，
 * 自动调整缓冲模式和画质偏好，弱网更流畅、高速网更快+更高清。
 *
 *  带宽阈值（自学习，初始值可被卡顿事件自整定）：
 *   - < weakNetBps  → 弱网：缓冲=流畅，画质=480P
 *   - > fastNetBps  → 高速网：缓冲=快起播，画质=最高
 *   - 中间         → 保持用户手动设置
 *
 *  自学习：弱网档仍频繁卡顿 → 提高 weakNetBps（更保守地判定弱网）；
 *          高速档几乎不卡顿 → 降低 fastNetBps（更激进地升档）。
 *
 *  节流：两次调整之间最少间隔 2 分钟，避免频繁抖动切档。
 *  仅在总开关 isAiPlayOptEnabled() 打开时才生效。
 */
public final class PlaybackAdvisor implements BandwidthMeter.EventListener {

    private static final long MIN_ADJUST_INTERVAL_MS = TimeUnit.MINUTES.toMillis(2);
    private static final long WEAK_NET_BPS_INIT = 2_000_000L;   // 初始 2 Mbps
    private static final long FAST_NET_BPS_INIT = 8_000_000L;   // 初始 8 Mbps
    private static final long WEAK_NET_BPS_MAX = 4_000_000L;   // 自学习上限
    private static final long FAST_NET_BPS_MIN = 4_000_000L;   // 自学习下限
    private static final long WEAK_NET_BPS_STEP = 200_000L;    // 每次 +0.2 Mbps
    private static final long FAST_NET_BPS_STEP = 500_000L;    // 每次 -0.5 Mbps
    private static final long STALL_THRESHOLD_MS = 3_000L;     // 卡顿 > 3s 算一次

    // 节流：单例全局一份，避免不同 Player 实例各调各的
    private static final PlaybackAdvisor INSTANCE = new PlaybackAdvisor();
    public static PlaybackAdvisor get() { return INSTANCE; }

    private long lastAdjustAtMs;
    private int lastAppliedBufferMode = -1;
    private int lastAppliedQualityPref = -1;

    // 自学习阈值（从 Prefers 加载，卡顿事件自整定后回写）
    private long weakNetBps;
    private long fastNetBps;
    private long bufferStartMs;        // STATE_BUFFERING 开始时间（elapsedRealtime）
    private int stallCountSinceAdjust; // 自上次阈值调整后的卡顿次数

    private PlaybackAdvisor() {
        weakNetBps = Prefers.getLong("ai_weak_net_bps", WEAK_NET_BPS_INIT);
        fastNetBps = Prefers.getLong("ai_fast_net_bps", FAST_NET_BPS_INIT);
    }

    /** PlayerManager 在 STATE_BUFFERING 时调用，记录缓冲开始时间。 */
    public void onBufferingStarted() {
        bufferStartMs = Clock.DEFAULT.elapsedRealtime();
    }

    /**
     * PlayerManager 在 STATE_BUFFERING→READY（或 ENDED）时调用。
     * 如果缓冲持续 > 3s 算一次卡顿，用于自学习阈值。
     */
    public void onBufferingEnded() {
        if (bufferStartMs <= 0) return;
        long dur = Clock.DEFAULT.elapsedRealtime() - bufferStartMs;
        bufferStartMs = 0;
        if (dur < STALL_THRESHOLD_MS) return;
        stallCountSinceAdjust++;
        // 连续 2 次卡顿 → 自整定阈值
        if (stallCountSinceAdjust >= 2 && PlayerSetting.isAiPlayOptEnabled()) {
            selfTuneThresholds();
            stallCountSinceAdjust = 0;
        }
    }

    /** 自学习：根据当前档位 + 卡顿情况调整阈值。 */
    private void selfTuneThresholds() {
        if (lastAppliedBufferMode == PlayerSetting.BUFFER_SMOOTH) {
            // 已经在弱网档还卡顿 → 提高 weakNetBps（更保守判定弱网）
            weakNetBps = Math.min(WEAK_NET_BPS_MAX, weakNetBps + WEAK_NET_BPS_STEP);
            Prefers.put("ai_weak_net_bps", weakNetBps);
        } else if (lastAppliedBufferMode == PlayerSetting.BUFFER_FAST) {
            // 高速档也卡顿 → 网络比想象差，降低 fastNetBps（更保守判定高速网）
            fastNetBps = Math.max(FAST_NET_BPS_MIN, fastNetBps - FAST_NET_BPS_STEP);
            Prefers.put("ai_fast_net_bps", fastNetBps);
        }
    }

    @Override
    public void onBandwidthSample(int elapsedMs, long bytes, long bitrateEstimate) {
        if (!PlayerSetting.isAiPlayOptEnabled()) return;
        if (bitrateEstimate <= 0) return;
        long now = Clock.DEFAULT.elapsedRealtime();
        if (now - lastAdjustAtMs < MIN_ADJUST_INTERVAL_MS) return;

        int targetBuffer = -1;
        int targetQuality = -1;
        if (bitrateEstimate < weakNetBps) {
            targetBuffer = PlayerSetting.BUFFER_SMOOTH;
            targetQuality = PlayerSetting.QUALITY_480;
        } else if (bitrateEstimate > fastNetBps) {
            targetBuffer = PlayerSetting.BUFFER_FAST;
            targetQuality = PlayerSetting.QUALITY_MAX;
        } else {
            if (lastAppliedBufferMode != -1) {
                targetBuffer = PlayerSetting.getBufferMode();
                targetQuality = PlayerSetting.getQualityPref();
            }
        }

        if (targetBuffer == -1) return;
        if (lastAppliedBufferMode == targetBuffer && lastAppliedQualityPref == targetQuality) {
            lastAdjustAtMs = now;
            return;
        }
        PlayerSetting.putBufferMode(targetBuffer);
        PlayerSetting.putQualityPref(targetQuality);
        lastAppliedBufferMode = targetBuffer;
        lastAppliedQualityPref = targetQuality;
        lastAdjustAtMs = now;
    }
}
