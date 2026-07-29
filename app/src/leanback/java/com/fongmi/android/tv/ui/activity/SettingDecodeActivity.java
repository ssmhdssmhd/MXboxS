package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.databinding.ActivitySettingDecodeBinding;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.base.BaseActivity;

public class SettingDecodeActivity extends BaseActivity {

    private ActivitySettingDecodeBinding mBinding;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingDecodeActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySettingDecodeBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mBinding.tunnel.requestFocus();
        refresh();
    }

    @Override
    protected void initEvent() {
        mBinding.aac.setOnClickListener(this::setAAC);
        mBinding.tunnel.setOnClickListener(this::setTunnel);
        mBinding.audioPrefer.setOnClickListener(this::setAudioPrefer);
        mBinding.videoPrefer.setOnClickListener(this::setVideoPrefer);
        mBinding.dv7Fallback.setOnClickListener(this::setDv7HevcFallback);
        mBinding.audioPassThrough.setOnClickListener(this::setAudioPassThrough);
    }

    private void refresh() {
        mBinding.aacText.setText(Setting.getSwitch(PlayerSetting.isPreferAAC()));
        mBinding.tunnelText.setText(Setting.getSwitch(PlayerSetting.isTunnel()));
        mBinding.audioPreferText.setText(Setting.getSwitch(PlayerSetting.isAudioPrefer()));
        mBinding.videoPreferText.setText(Setting.getSwitch(PlayerSetting.isVideoPrefer()));
        mBinding.dv7FallbackText.setText(Setting.getSwitch(PlayerSetting.isDv7HevcFallback()));
        mBinding.audioPassThroughText.setText(Setting.getSwitch(PlayerSetting.isAudioPassThrough()));
    }

    private void setTunnel(View view) {
        if (PlayerSetting.isMpv()) return;
        PlayerSetting.putTunnel(!PlayerSetting.isTunnel());
        mBinding.tunnelText.setText(Setting.getSwitch(PlayerSetting.isTunnel()));
    }

    private void setAudioPassThrough(View view) {
        PlayerSetting.putAudioPassThrough(!PlayerSetting.isAudioPassThrough());
        mBinding.audioPassThroughText.setText(Setting.getSwitch(PlayerSetting.isAudioPassThrough()));
    }

    private void setAudioPrefer(View view) {
        PlayerSetting.putAudioPrefer(!PlayerSetting.isAudioPrefer());
        mBinding.audioPreferText.setText(Setting.getSwitch(PlayerSetting.isAudioPrefer()));
    }

    private void setVideoPrefer(View view) {
        PlayerSetting.putVideoPrefer(!PlayerSetting.isVideoPrefer());
        mBinding.videoPreferText.setText(Setting.getSwitch(PlayerSetting.isVideoPrefer()));
    }

    private void setDv7HevcFallback(View view) {
        PlayerSetting.putDv7HevcFallback(!PlayerSetting.isDv7HevcFallback());
        mBinding.dv7FallbackText.setText(Setting.getSwitch(PlayerSetting.isDv7HevcFallback()));
    }

    private void setAAC(View view) {
        PlayerSetting.putPreferAAC(!PlayerSetting.isPreferAAC());
        mBinding.aacText.setText(Setting.getSwitch(PlayerSetting.isPreferAAC()));
    }
}
