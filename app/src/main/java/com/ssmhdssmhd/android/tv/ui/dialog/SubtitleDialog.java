package com.ssmhdssmhd.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.media3.ui.SubtitleView;
import androidx.viewbinding.ViewBinding;

import com.ssmhdssmhd.android.tv.databinding.DialogSubtitleBinding;
import com.ssmhdssmhd.android.tv.player.PlayerManager;
import com.ssmhdssmhd.android.tv.setting.PlayerSetting;
import com.ssmhdssmhd.android.tv.utils.ResUtil;
import com.ssmhdssmhd.android.tv.utils.Util;
import com.github.bassaer.library.MDColor;

public final class SubtitleDialog extends BaseBottomSheetDialog {

    private DialogSubtitleBinding binding;
    private SubtitleView subtitleView;
    private PlayerManager player;
    private float subtitlePosition;
    private float subtitleTextSize;

    public static SubtitleDialog create() {
        return new SubtitleDialog();
    }

    public SubtitleDialog view(SubtitleView subtitleView) {
        this.subtitleView = subtitleView;
        return this;
    }

    public SubtitleDialog player(PlayerManager player) {
        this.player = player;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof SubtitleDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    private boolean isFull() {
        return Util.isFullscreen(getActivity());
    }

    @Override
    protected boolean transparent() {
        return isFull();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogSubtitleBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        int count = binding.getRoot().getChildCount();
        if (isFull()) for (int i = 0; i < count; i++) ((ImageView) binding.getRoot().getChildAt(i)).getDrawable().setTint(MDColor.WHITE);
    }

    @Override
    protected void initEvent() {
        binding.up.setOnClickListener(this::onUp);
        binding.down.setOnClickListener(this::onDown);
        binding.large.setOnClickListener(this::onLarge);
        binding.small.setOnClickListener(this::onSmall);
        binding.reset.setOnClickListener(this::onReset);
    }

    private void onUp(View view) {
        subtitlePosition += 0.005f;
        PlayerSetting.putSubtitlePosition(subtitlePosition);
        subtitleView.setBottomPaddingFraction(subtitlePosition);
        applySubtitleStyle();
    }

    private void onDown(View view) {
        subtitlePosition -= 0.005f;
        PlayerSetting.putSubtitlePosition(subtitlePosition);
        subtitleView.setBottomPaddingFraction(subtitlePosition);
        applySubtitleStyle();
    }

    private void onLarge(View view) {
        subtitleTextSize += 0.002f;
        PlayerSetting.putSubtitleTextSize(subtitleTextSize);
        subtitleView.setFractionalTextSize(subtitleTextSize);
        applySubtitleStyle();
    }

    private void onSmall(View view) {
        subtitleTextSize -= 0.002f;
        PlayerSetting.putSubtitleTextSize(subtitleTextSize);
        subtitleView.setFractionalTextSize(subtitleTextSize);
        applySubtitleStyle();
    }

    private void onReset(View view) {
        PlayerSetting.putSubtitleTextSize(0.0f);
        PlayerSetting.putSubtitlePosition(0.0f);
        subtitlePosition = 0.0f;
        subtitleTextSize = 0.0f;
        subtitleView.setBottomPaddingFraction(0.0f);
        subtitleView.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION);
        applySubtitleStyle();
    }

    private void applySubtitleStyle() {
        if (player != null && !player.isReleased()) player.setSubtitleStyle();
    }

    @Override
    public void onResume() {
        super.onResume();
        getDialog().getWindow().setLayout(ResUtil.dp2px(isFull() ? 232 : 216), -1);
    }
}
