package com.ssmhdssmhd.mxboxs.ui.dialog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.ssmhdssmhd.mxboxs.R;
import com.ssmhdssmhd.mxboxs.databinding.DialogPlayerEngineBinding;
import com.ssmhdssmhd.mxboxs.playback.PlaybackAction;
import com.ssmhdssmhd.mxboxs.player.PlayerManager;
import com.ssmhdssmhd.mxboxs.setting.PlayerSetting;
import com.ssmhdssmhd.mxboxs.ui.activity.PlaybackActivity;

public final class PlayerEngineDialog extends BaseBottomSheetDialog {

    private DialogPlayerEngineBinding binding;
    private PlayerManager player;
    private CharSequence title;
    private TextView target;

    public static void setText(TextView view) {
        setText(view, null);
    }

    public static void setText(TextView view, PlayerManager player) {
        if (view == null) return;
        view.setText(PlaybackAction.getEngineText(player));
    }

    public static void show(FragmentActivity activity, TextView view, PlayerManager player, CharSequence title) {
        for (Fragment fragment : activity.getSupportFragmentManager().getFragments()) if (fragment instanceof PlayerEngineDialog) return;
        PlayerEngineDialog dialog = new PlayerEngineDialog();
        dialog.player = player;
        dialog.target = view;
        dialog.title = title;
        dialog.show(activity.getSupportFragmentManager(), null);
    }

    private static int getCurrentEngine(PlayerManager player) {
        return PlaybackAction.getEngine(player);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogPlayerEngineBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        updateAvailability();
        updateDescriptions();
        setSelected();
        getSelectedView().requestFocus();
    }

    @Override
    protected void initEvent() {
        binding.debug.setOnClickListener(this::selectDebug);
        binding.other.setOnClickListener(this::selectOther);
        binding.exo.setOnClickListener(view -> selectEngine(PlayerSetting.ENGINE_EXO));
        binding.mpv.setOnClickListener(view -> selectEngine(PlayerSetting.ENGINE_MPV));
        binding.system.setOnClickListener(view -> selectEngine(PlayerSetting.ENGINE_SYSTEM));
        binding.ijk.setOnClickListener(view -> selectEngine(PlayerSetting.ENGINE_IJK));
        binding.vlc.setOnClickListener(view -> selectEngine(PlayerSetting.ENGINE_VLC));
        binding.web.setOnClickListener(view -> selectEngine(PlayerSetting.ENGINE_WEB));
    }

    private void updateAvailability() {
        // IJK: only clickable if available, otherwise visually disabled
        boolean ijkAvail = PlayerSetting.isIjkAvailable();
        binding.ijk.setEnabled(ijkAvail);
        binding.ijk.setAlpha(ijkAvail ? 1.0f : 0.45f);
        binding.ijk.setClickable(ijkAvail);
        binding.ijkDesc.setAlpha(ijkAvail ? 1.0f : 0.45f);

        boolean vlcAvail = PlayerSetting.isVlcAvailable();
        binding.vlc.setEnabled(vlcAvail);
        binding.vlc.setAlpha(vlcAvail ? 1.0f : 0.45f);
        binding.vlc.setClickable(vlcAvail);
        binding.vlcDesc.setAlpha(vlcAvail ? 1.0f : 0.45f);

        boolean mpvAvail = com.ssmhdssmhd.mxboxs.player.mpv.MpvPlayerEngine.isAvailable();
        binding.mpv.setEnabled(mpvAvail);
        binding.mpv.setAlpha(mpvAvail ? 1.0f : 0.45f);
        binding.mpv.setClickable(mpvAvail);
        binding.mpvDesc.setAlpha(mpvAvail ? 1.0f : 0.45f);

        // WEB engine is always available via the built-in HLS.js player endpoint
        binding.web.setEnabled(true);
        binding.web.setAlpha(1.0f);
    }

    private void updateDescriptions() {
        binding.exoDesc.setText(R.string.play_engine_desc_exo);
        binding.mpvDesc.setText(R.string.play_engine_desc_mpv);
        binding.systemDesc.setText(R.string.play_engine_desc_system);
        if (PlayerSetting.isIjkAvailable()) binding.ijkDesc.setText(R.string.play_engine_desc_ijk);
        else binding.ijkDesc.setText(getString(R.string.play_engine_desc_ijk) + "（本机未包含 so 库）");
        if (PlayerSetting.isVlcAvailable()) binding.vlcDesc.setText(R.string.play_engine_desc_vlc);
        else binding.vlcDesc.setText(getString(R.string.play_engine_desc_vlc) + "（本机未包含 so 库）");
        binding.webDesc.setText(R.string.play_engine_desc_web);
    }

    private void selectDebug(View view) {
        PlaybackActivity activity = getPlaybackActivity();
        if (activity == null) return;
        activity.toggleDebugView();
        view.setSelected(activity.isDebugViewVisible());
        dismiss();
    }

    private void selectOther(View view) {
        dismiss();
        PlaybackActivity activity = getPlaybackActivity();
        if (activity != null) activity.chooseOtherPlayer(title);
    }

    private void selectEngine(int engine) {
        PlaybackActivity activity = getPlaybackActivity();
        boolean changed = engine != getCurrentEngine(player);
        if (changed && activity != null) activity.hideDebugView();
        if (player == null) PlayerSetting.putEngine(engine);
        else player.setEngine(engine);
        setText(target, player);
        dismiss();
    }

    private void setSelected() {
        int engine = getCurrentEngine(player);
        PlaybackActivity activity = getPlaybackActivity();
        binding.exo.setSelected(engine == PlayerSetting.ENGINE_EXO);
        binding.mpv.setSelected(engine == PlayerSetting.ENGINE_MPV);
        binding.system.setSelected(engine == PlayerSetting.ENGINE_SYSTEM);
        binding.ijk.setSelected(engine == PlayerSetting.ENGINE_IJK);
        binding.vlc.setSelected(engine == PlayerSetting.ENGINE_VLC);
        binding.web.setSelected(engine == PlayerSetting.ENGINE_WEB);
        binding.debug.setSelected(activity != null && activity.isDebugViewVisible());
    }

    private View getSelectedView() {
        return switch (getCurrentEngine(player)) {
            case PlayerSetting.ENGINE_MPV -> binding.mpv;
            case PlayerSetting.ENGINE_SYSTEM -> binding.system;
            case PlayerSetting.ENGINE_IJK -> binding.ijk.isEnabled() ? binding.ijk : binding.exo;
            case PlayerSetting.ENGINE_VLC -> binding.vlc.isEnabled() ? binding.vlc : binding.exo;
            case PlayerSetting.ENGINE_WEB -> binding.web;
            default -> binding.exo;
        };
    }

    private PlaybackActivity getPlaybackActivity() {
        FragmentActivity activity = getActivity();
        return activity instanceof PlaybackActivity owner ? owner : null;
    }
}
