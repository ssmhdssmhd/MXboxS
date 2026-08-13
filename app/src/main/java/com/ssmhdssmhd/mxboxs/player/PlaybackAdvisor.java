package com.ssmhdssmhd.mxboxs.player;

import androidx.media3.common.util.Clock;
import androidx.media3.exoplayer.upstream.BandwidthMeter;

import com.ssmhdssmhd.mxboxs.setting.PlayerSetting;

import java.util.concurrent.TimeUnit;

/**
 * AI 播放优化决策器：基于 ExoPlayer BandwidthMeter 的实时带宽估算，
 * 自动调整缓冲模式和画质偏好，弱网更流畅、高速网更快+更高清。
 *
 *  带宽阈值（经验值，可后续调）：
 *   - < 2 Mbps (2_000_000 bps)  → 弱网：缓冲=流畅，画质=480P，避免卡顿
 *   - > 8 Mbps                 → 高速网：缓冲=快起播，画质=最高，秒开 + 清晰
 *   - 中间                    → 保持用户手动设置，不做强制改写
 *
 * 节流：两次调整之间最少间隔 2 分钟，避免频繁抖动切档。
 * 仅在总开关 isAiPlayOptEnabled() 打开时才生效。
 */
public final class PlaybackAdvisor implements BandwidthMeter.EventListener {

    private static final long MIN_ADJUST_INTERVAL_MS = TimeUnit.MINUTES.toMillis(2);
    private static final long WEAK_NET_BPS = 2_000_000L;   // < 2 Mbps → 弱网
    private static final long FAST_NET_BPS = 8_000_000L;   // > 8 Mbps → 高速网

    // 节流：单例全局一份，避免不同 Player 实例各调各的
    private static final PlaybackAdvisor INSTANCE = new PlaybackAdvisor();
    public static PlaybackAdvisor get() { return INSTANCE; }

    private long lastAdjustAtMs;
    private int lastAppliedBufferMode = -1;   // -1 表示还没生效过调整
    private int lastAppliedQualityPref = -1;

    private PlaybackAdvisor() {}

    @Override
    public void onBandwidthSample(int elapsedMs, long bytes, long bitrateEstimate) {
        if (!PlayerSetting.isAiPlayOptEnabled()) return;
        // bitrateEstimate 是 BandwidthMeter 已经 EWMA 平滑过的估算，直接用即可
        if (bitrateEstimate <= 0) return;
        long now = Clock.DEFAULT.elapsedRealtime();
        if (now - lastAdjustAtMs < MIN_ADJUST_INTERVAL_MS) return;

        int targetBuffer = -1;
        int targetQuality = -1;
        if (bitrateEstimate < WEAK_NET_BPS) {
            targetBuffer = PlayerSetting.BUFFER_SMOOTH;
            targetQuality = PlayerSetting.QUALITY_480;
        } else if (bitrateEstimate > FAST_NET_BPS) {
            targetBuffer = PlayerSetting.BUFFER_FAST;
            targetQuality = PlayerSetting.QUALITY_MAX;
        } else {
            // 中间档：不强制，只在首次/之前生效过弱/强档时还原成用户设置的默认值
            if (lastAppliedBufferMode != -1) {
                targetBuffer = PlayerSetting.getBufferMode();
                targetQuality = PlayerSetting.getQualityPref();
            }
        }

        if (targetBuffer == -1) return;   // 中间档且没改过 → 跳过
        if (lastAppliedBufferMode == targetBuffer && lastAppliedQualityPref == targetQuality) {
            // 和上次一样 → 仍算一次调整，刷新节流时间
            lastAdjustAtMs = now;
            return;
        }
        // 写入配置：下次起播的 Player 实例会读到新值
        PlayerSetting.putBufferMode(targetBuffer);
        PlayerSetting.putQualityPref(targetQuality);
        lastAppliedBufferMode = targetBuffer;
        lastAppliedQualityPref = targetQuality;
        lastAdjustAtMs = now;
    }
}
