package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivitySettingAiBinding;
import com.fongmi.android.tv.setting.AISetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.ResUtil;

public class SettingAIActivity extends BaseActivity {

    private ActivitySettingAiBinding mBinding;
    private String[] speedFactors;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingAIActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySettingAiBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mBinding.enabled.requestFocus();
        mBinding.enabledText.setText(Setting.getSwitch(AISetting.isEnabled()));
        mBinding.speedUpText.setText(Setting.getSwitch(AISetting.isSpeedUp()));
        mBinding.removeAdText.setText(Setting.getSwitch(AISetting.isAutoRemoveAd()));
        mBinding.skipInterludeText.setText(Setting.getSwitch(AISetting.isAutoSkipInterlude()));
        mBinding.smartSkipText.setText(Setting.getSwitch(AISetting.isSmartSkip()));
        mBinding.autoNextText.setText(Setting.getSwitch(AISetting.isAutoNext()));
        mBinding.speedFactorText.setText((speedFactors = ResUtil.getStringArray(R.array.select_ai_speed))[AISetting.getSpeedFactor()]);
    }

    @Override
    protected void initEvent() {
        mBinding.enabled.setOnClickListener(this::setEnabled);
        mBinding.speedUp.setOnClickListener(this::setSpeedUp);
        mBinding.removeAd.setOnClickListener(this::setRemoveAd);
        mBinding.skipInterlude.setOnClickListener(this::setSkipInterlude);
        mBinding.smartSkip.setOnClickListener(this::setSmartSkip);
        mBinding.autoNext.setOnClickListener(this::setAutoNext);
        mBinding.speedFactor.setOnClickListener(this::setSpeedFactor);
    }

    private void setEnabled(View view) {
        AISetting.putEnabled(!AISetting.isEnabled());
        mBinding.enabledText.setText(Setting.getSwitch(AISetting.isEnabled()));
    }

    private void setSpeedUp(View view) {
        AISetting.putSpeedUp(!AISetting.isSpeedUp());
        mBinding.speedUpText.setText(Setting.getSwitch(AISetting.isSpeedUp()));
    }

    private void setRemoveAd(View view) {
        AISetting.putAutoRemoveAd(!AISetting.isAutoRemoveAd());
        mBinding.removeAdText.setText(Setting.getSwitch(AISetting.isAutoRemoveAd()));
    }

    private void setSkipInterlude(View view) {
        AISetting.putAutoSkipInterlude(!AISetting.isAutoSkipInterlude());
        mBinding.skipInterludeText.setText(Setting.getSwitch(AISetting.isAutoSkipInterlude()));
    }

    private void setSmartSkip(View view) {
        AISetting.putSmartSkip(!AISetting.isSmartSkip());
        mBinding.smartSkipText.setText(Setting.getSwitch(AISetting.isSmartSkip()));
    }

    private void setAutoNext(View view) {
        AISetting.putAutoNext(!AISetting.isAutoNext());
        mBinding.autoNextText.setText(Setting.getSwitch(AISetting.isAutoNext()));
    }

    private void setSpeedFactor(View view) {
        int index = (AISetting.getSpeedFactor() + 1) % speedFactors.length;
        mBinding.speedFactorText.setText(speedFactors[index]);
        AISetting.putSpeedFactor(index);
    }
}
