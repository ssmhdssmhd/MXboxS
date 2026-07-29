package com.fongmi.android.tv.setting;

import com.github.catvod.utils.Prefers;

public class AISetting {

    private static final int MIN_SPEED_FACTOR = 0;
    private static final int MAX_SPEED_FACTOR = 4;

    public static boolean isEnabled() {
        return Prefers.getBoolean("ai_enabled", false);
    }

    public static void putEnabled(boolean enabled) {
        Prefers.put("ai_enabled", enabled);
    }

    public static boolean isSpeedUp() {
        return Prefers.getBoolean("ai_speed_up", false);
    }

    public static void putSpeedUp(boolean speedUp) {
        Prefers.put("ai_speed_up", speedUp);
    }

    public static boolean isAutoRemoveAd() {
        return Prefers.getBoolean("ai_remove_ad", false);
    }

    public static void putAutoRemoveAd(boolean removeAd) {
        Prefers.put("ai_remove_ad", removeAd);
    }

    public static boolean isAutoSkipInterlude() {
        return Prefers.getBoolean("ai_skip_interlude", false);
    }

    public static void putAutoSkipInterlude(boolean skip) {
        Prefers.put("ai_skip_interlude", skip);
    }

    public static int getSpeedFactor() {
        return Math.clamp(Prefers.getInt("ai_speed_factor", 1), MIN_SPEED_FACTOR, MAX_SPEED_FACTOR);
    }

    public static void putSpeedFactor(int factor) {
        Prefers.put("ai_speed_factor", Math.clamp(factor, MIN_SPEED_FACTOR, MAX_SPEED_FACTOR));
    }

    public static boolean isSmartSkip() {
        return Prefers.getBoolean("ai_smart_skip", false);
    }

    public static void putSmartSkip(boolean smartSkip) {
        Prefers.put("ai_smart_skip", smartSkip);
    }

    public static boolean isAutoNext() {
        return Prefers.getBoolean("ai_auto_next", false);
    }

    public static void putAutoNext(boolean autoNext) {
        Prefers.put("ai_auto_next", autoNext);
    }
}
