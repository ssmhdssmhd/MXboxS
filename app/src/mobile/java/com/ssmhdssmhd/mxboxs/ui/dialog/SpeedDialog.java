package com.ssmhdssmhd.mxboxs.ui.dialog;

import android.content.DialogInterface;

import androidx.fragment.app.Fragment;
import androidx.viewbinding.ViewBinding;

import com.ssmhdssmhd.mxboxs.R;
import com.ssmhdssmhd.mxboxs.databinding.DialogSpeedBinding;
import com.ssmhdssmhd.mxboxs.impl.SpeedListener;
import com.ssmhdssmhd.mxboxs.setting.PlayerSetting;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SpeedDialog extends BaseAlertDialog {

    private DialogSpeedBinding binding;
    private float value;

    public static void show(Fragment fragment) {
        new SpeedDialog().show(fragment.getChildFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogSpeedBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setTitle(R.string.player_speed).setView(getBinding().getRoot()).setPositiveButton(R.string.dialog_positive, this::onPositive).setNegativeButton(R.string.dialog_negative, this::onNegative);
    }

    @Override
    protected void initView() {
        binding.slider.setValue(value = PlayerSetting.getSpeed());
    }

    private void onPositive(DialogInterface dialog, int which) {
        ((SpeedListener) requireParentFragment()).setSpeed(binding.slider.getValue());
    }

    private void onNegative(DialogInterface dialog, int which) {
        ((SpeedListener) requireParentFragment()).setSpeed(value);
    }
}