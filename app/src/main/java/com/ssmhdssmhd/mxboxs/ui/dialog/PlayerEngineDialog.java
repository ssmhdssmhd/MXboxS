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
        if (binding.ali != null) binding.ali.setOnClickListener(view -> selectEngine(PlayerSetting.ENGINE_ALI));
        if (binding.nova != null) binding.nova.setOnClickListener(view -> selectEngine(PlayerSetting.ENGINE_NOVA));
        if (binding.ijk != null) binding.ijk.setOnClickListener(view -> selectEngine(PlayerSetting.ENGINE_IJK));
        if (binding.vlc != null) binding.vlc.setOnClickListener(view -> selectEngine(PlayerSetting.ENGINE_VLC));
        if (binding.mx != null) binding.mx.setOnClickListener(view -> selectEngine(PlayerSetting.ENGINE_MX));
        if (binding.mpvex != null) binding.mpvex.setOnClickListener(view -> selectEngine(PlayerSetting.ENGINE_MPVEX));
        if (binding.mpvnova != null) binding.mpvnova.setOnClickListener(view -> selectEngine(PlayerSetting.ENGINE_MPVNOVA));
        if (binding.kmp != null) binding.kmp.setOnClickListener(view -> selectEngine(PlayerSetting.ENGINE_KMP));
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
        if (binding.ali != null) binding.ali.setSelected(engine == PlayerSetting.ENGINE_ALI);
        if (binding.nova != null) binding.nova.setSelected(engine == PlayerSetting.ENGINE_NOVA);
        if (binding.ijk != null) binding.ijk.setSelected(engine == PlayerSetting.ENGINE_IJK);
        if (binding.vlc != null) binding.vlc.setSelected(engine == PlayerSetting.ENGINE_VLC);
        if (binding.mx != null) binding.mx.setSelected(engine == PlayerSetting.ENGINE_MX);
        if (binding.mpvex != null) binding.mpvex.setSelected(engine == PlayerSetting.ENGINE_MPVEX);
        if (binding.mpvnova != null) binding.mpvnova.setSelected(engine == PlayerSetting.ENGINE_MPVNOVA);
        if (binding.kmp != null) binding.kmp.setSelected(engine == PlayerSetting.ENGINE_KMP);
        binding.debug.setSelected(activity != null && activity.isDebugViewVisible());
    }

    private View getSelectedView() {
        return switch (getCurrentEngine(player)) {
            case PlayerSetting.ENGINE_MPV -> binding.mpv;
            case PlayerSetting.ENGINE_SYSTEM -> binding.system;
            case PlayerSetting.ENGINE_ALI -> binding.ali != null ? binding.ali : binding.exo;
            case PlayerSetting.ENGINE_NOVA -> binding.nova != null ? binding.nova : binding.exo;
            case PlayerSetting.ENGINE_IJK -> binding.ijk != null ? binding.ijk : binding.exo;
            case PlayerSetting.ENGINE_VLC -> binding.vlc != null ? binding.vlc : binding.exo;
            case PlayerSetting.ENGINE_MX -> binding.mx != null ? binding.mx : binding.exo;
            case PlayerSetting.ENGINE_MPVEX -> binding.mpvex != null ? binding.mpvex : binding.exo;
            case PlayerSetting.ENGINE_MPVNOVA -> binding.mpvnova != null ? binding.mpvnova : binding.exo;
            case PlayerSetting.ENGINE_KMP -> binding.kmp != null ? binding.kmp : binding.exo;
            default -> binding.exo;
        };
    }

    private PlaybackActivity getPlaybackActivity() {
        FragmentActivity activity = getActivity();
        return activity instanceof PlaybackActivity owner ? owner : null;
    }
}
