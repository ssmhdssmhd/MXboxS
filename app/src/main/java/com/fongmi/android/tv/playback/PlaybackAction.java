package com.fongmi.android.tv.playback;

import android.view.View;
import android.widget.TextView;

import androidx.media3.common.C;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.utils.ResUtil;

public final class PlaybackAction {

    public static void setPlaybackMode(PlayerManager player, TextView engine, TextView decode) {
        setText(engine, getEngineText(player));
        setText(decode, getDecodeText(player));
    }

    public static float addSpeed(PlayerManager player, TextView speed) {
        return applySpeedText(player, speed, player.addSpeed());
    }

    public static float addSpeed(PlayerManager player, TextView speed, float value) {
        return applySpeedText(player, speed, player.addSpeed(value));
    }

    public static float subSpeed(PlayerManager player, TextView speed, float value) {
        return applySpeedText(player, speed, player.subSpeed(value));
    }

    public static float setSpeed(PlayerManager player, TextView speed, float value) {
        return applySpeedText(player, speed, player.setSpeed(value));
    }

    public static void setSpeedText(PlayerManager player, TextView speed) {
        setText(speed, getSpeedText(player));
    }

    public static float toggleSpeed(PlayerManager player, TextView speed) {
        return applySpeedText(player, speed, player.toggleSpeed());
    }

    public static void toggleDecode(PlayerManager player) {
        player.toggleDecode();
    }

    public static void setTracks(PlayerManager player, View text, View audio, View video) {
        setVisible(text, hasTextTrack(player));
        setVisible(audio, hasAudioTrack(player));
        setVisible(video, hasVideoTrack(player));
    }

    public static void setTracks(PlayerManager player, View text, View audio, View video, View speed) {
        setTracks(player, text, audio, video);
        setVisible(speed, hasSpeed(player));
    }

    public static void setMediaOptions(PlayerManager player, View edition, View chapter) {
        setVisible(edition, hasEdition(player));
        setVisible(chapter, hasChapter(player));
    }

    public static String getEngineText(PlayerManager player) {
        return ResUtil.getStringArray(R.array.select_engine)[getEngine(player)];
    }

    public static int getEngine(PlayerManager player) {
        if (player == null || player.isReleased()) return PlayerSetting.getEngine();
        return player.getEngine();
    }

    private static String getDecodeText(PlayerManager player) {
        return player == null ? "" : player.getDecodeText();
    }

    private static String getSpeedText(PlayerManager player) {
        return player == null ? "" : player.getSpeedText();
    }

    private static boolean hasTextTrack(PlayerManager player) {
        return player != null && (player.haveTrack(C.TRACK_TYPE_TEXT) || player.isVod());
    }

    private static boolean hasAudioTrack(PlayerManager player) {
        return player != null && player.haveTrack(C.TRACK_TYPE_AUDIO);
    }

    private static boolean hasVideoTrack(PlayerManager player) {
        return player != null && player.haveTrack(C.TRACK_TYPE_VIDEO);
    }

    private static boolean hasSpeed(PlayerManager player) {
        return player != null && player.isVod();
    }

    private static boolean hasEdition(PlayerManager player) {
        return player != null && player.haveEdition();
    }

    private static boolean hasChapter(PlayerManager player) {
        return player != null && player.haveChapter();
    }

    private static float applySpeedText(PlayerManager player, TextView speed, String text) {
        setText(speed, text);
        return player.getSpeed();
    }

    private static void setText(TextView view, CharSequence text) {
        if (view != null) view.setText(text);
    }

    private static void setVisible(View view, boolean visible) {
        if (view != null) view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}
