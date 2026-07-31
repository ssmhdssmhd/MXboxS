package com.ssmhdssmhd.mxboxs.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

import androidx.viewbinding.ViewBinding;

import com.ssmhdssmhd.mxboxs.R;
import com.ssmhdssmhd.mxboxs.databinding.ActivitySettingPlayerBinding;
import com.ssmhdssmhd.mxboxs.impl.SpeedListener;
import com.ssmhdssmhd.mxboxs.impl.UaListener;
import com.ssmhdssmhd.mxboxs.setting.PlayerSetting;
import com.ssmhdssmhd.mxboxs.setting.Setting;
import com.ssmhdssmhd.mxboxs.ui.base.BaseActivity;
import com.ssmhdssmhd.mxboxs.ui.dialog.MpvConfDialog;
import com.ssmhdssmhd.mxboxs.ui.dialog.SpeedDialog;
import com.ssmhdssmhd.mxboxs.ui.dialog.UaDialog;
import com.ssmhdssmhd.mxboxs.utils.ResUtil;

import java.text.DecimalFormat;

public class SettingPlayerActivity extends BaseActivity implements UaListener, SpeedListener {

    private ActivitySettingPlayerBinding mBinding;
    private DecimalFormat format;
    private String[] caption;
    private String[] render;
    private String[] scale;
    private String[] engine;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingPlayerActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySettingPlayerBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        setVisible();
        setPlaybackModeText();
        mBinding.engine.requestFocus();
        format = new DecimalFormat("0.#");
        mBinding.speedText.setText(format.format(PlayerSetting.getSpeed()));
        mBinding.adblockText.setText(Setting.getSwitch(Setting.isAdblock()));
        mBinding.mpvVulkanText.setText(Setting.getSwitch(PlayerSetting.isMpvVulkan()));
        mBinding.mpvGpuNextText.setText(Setting.getSwitch(PlayerSetting.isMpvGpuNext()));
        mBinding.tunnelText.setText(Setting.getSwitch(PlayerSetting.isTunnel()));
        mBinding.audioPassThroughText.setText(Setting.getSwitch(PlayerSetting.isAudioPassThrough()));
        mBinding.audioPreferText.setText(Setting.getSwitch(PlayerSetting.isAudioPrefer()));
        mBinding.videoPreferText.setText(Setting.getSwitch(PlayerSetting.isVideoPrefer()));
        mBinding.preferAacText.setText(Setting.getSwitch(PlayerSetting.isPreferAAC()));
        mBinding.dv7FallbackText.setText(Setting.getSwitch(PlayerSetting.isDv7HevcFallback()));
        mBinding.backgroundText.setText(Setting.getSwitch(PlayerSetting.isBackgroundOn()));
        mBinding.scaleText.setText((scale = ResUtil.getStringArray(R.array.select_scale))[PlayerSetting.getScale()]);
        mBinding.captionText.setText((caption = ResUtil.getStringArray(R.array.select_caption))[PlayerSetting.isCaption() ? 1 : 0]);
    }

    @Override
    protected void initEvent() {
        mBinding.engine.setOnClickListener(this::setEngine);
        mBinding.mpvConf.setOnClickListener(this::onMpvConf);
        mBinding.mpvGpuNext.setOnClickListener(this::setMpvGpuNext);
        mBinding.mpvVulkan.setOnClickListener(this::setMpvVulkan);
        mBinding.render.setOnClickListener(this::setRender);
        mBinding.scale.setOnClickListener(this::setScale);
        mBinding.caption.setOnClickListener(this::setCaption);
        mBinding.caption.setOnLongClickListener(this::onCaption);
        mBinding.speed.setOnClickListener(this::onSpeed);
        mBinding.background.setOnClickListener(this::onBackground);
        mBinding.adblock.setOnClickListener(this::setAdblock);
        mBinding.tunnel.setOnClickListener(this::setTunnel);
        mBinding.audioPassThrough.setOnClickListener(this::setAudioPassThrough);
        mBinding.audioPrefer.setOnClickListener(this::setAudioPrefer);
        mBinding.videoPrefer.setOnClickListener(this::setVideoPrefer);
        mBinding.preferAac.setOnClickListener(this::setPreferAac);
        mBinding.dv7Fallback.setOnClickListener(this::setDv7Fallback);
        mBinding.preload.setOnClickListener(this::onPreloadSetting);
        mBinding.decode.setOnClickListener(this::onDecodeSetting);
        mBinding.ua.setOnClickListener(this::onUa);
    }

    private void setVisible() {
        boolean exo = !PlayerSetting.isMpv() && !PlayerSetting.isSystem();
        boolean mpv = PlayerSetting.isMpv();
        if (PlayerSetting.isBackgroundPiP()) PlayerSetting.putBackground(1);
        mBinding.mpvConf.setVisibility(mpv ? View.VISIBLE : View.GONE);
        mBinding.mpvVulkan.setVisibility(mpv ? View.VISIBLE : View.GONE);
        mBinding.mpvGpuNext.setVisibility(mpv ? View.VISIBLE : View.GONE);
        mBinding.decode.setVisibility(exo ? View.VISIBLE : View.GONE);
        mBinding.adblock.setVisibility(exo ? View.VISIBLE : View.GONE);
        mBinding.tunnel.setVisibility(exo ? View.VISIBLE : View.GONE);
        mBinding.audioPassThrough.setVisibility(exo || mpv ? View.VISIBLE : View.GONE);
        mBinding.audioPrefer.setVisibility(exo ? View.VISIBLE : View.GONE);
        mBinding.videoPrefer.setVisibility(exo ? View.VISIBLE : View.GONE);
        mBinding.preferAac.setVisibility(exo ? View.VISIBLE : View.GONE);
        mBinding.dv7Fallback.setVisibility(exo ? View.VISIBLE : View.GONE);
        mBinding.caption.setVisibility(PlayerSetting.hasCaption() ? View.VISIBLE : View.GONE);
    }

    private void setEngine(View view) {
        int index = (PlayerSetting.getEngine() + 1) % engine.length;
        PlayerSetting.putEngine(index);
        setPlaybackModeText();
        setVisible();
    }

    private void onMpvConf(View view) {
        MpvConfDialog.show(this);
    }

    private void setMpvGpuNext(View view) {
        PlayerSetting.putMpvGpuNext(!PlayerSetting.isMpvGpuNext());
        mBinding.mpvGpuNextText.setText(Setting.getSwitch(PlayerSetting.isMpvGpuNext()));
    }

    private void setMpvVulkan(View view) {
        PlayerSetting.putMpvVulkan(!PlayerSetting.isMpvVulkan());
        mBinding.mpvVulkanText.setText(Setting.getSwitch(PlayerSetting.isMpvVulkan()));
    }

    private void setRender(View view) {
        int index = (PlayerSetting.getRender() + 1) % render.length;
        PlayerSetting.putRender(index);
        setPlaybackModeText();
    }

    private void setPlaybackModeText() {
        engine = ResUtil.getStringArray(R.array.select_engine);
        render = ResUtil.getStringArray(R.array.select_render);
        mBinding.engineText.setText(engine[PlayerSetting.getEngine()]);
        mBinding.renderText.setText(render[PlayerSetting.getRender()]);
    }

    private void setScale(View view) {
        int index = (PlayerSetting.getScale() + 1) % scale.length;
        mBinding.scaleText.setText(scale[index]);
        PlayerSetting.putScale(index);
    }

    private void setCaption(View view) {
        PlayerSetting.putCaption(!PlayerSetting.isCaption());
        mBinding.captionText.setText(caption[PlayerSetting.isCaption() ? 1 : 0]);
    }

    private boolean onCaption(View view) {
        if (PlayerSetting.isCaption()) startActivity(new Intent(Settings.ACTION_CAPTIONING_SETTINGS));
        return PlayerSetting.isCaption();
    }

    private void onSpeed(View view) {
        SpeedDialog.show(this);
    }

    @Override
    public void setSpeed(float speed) {
        mBinding.speedText.setText(format.format(speed));
        PlayerSetting.putSpeed(speed);
    }

    private void onBackground(View view) {
        PlayerSetting.putBackground(PlayerSetting.isBackgroundOn() ? 0 : 1);
        mBinding.backgroundText.setText(Setting.getSwitch(PlayerSetting.isBackgroundOn()));
    }

    private void setAdblock(View view) {
        Setting.putAdblock(!Setting.isAdblock());
        mBinding.adblockText.setText(Setting.getSwitch(Setting.isAdblock()));
    }

    private void setTunnel(View view) {
        PlayerSetting.putTunnel(!PlayerSetting.isTunnel());
        mBinding.tunnelText.setText(Setting.getSwitch(PlayerSetting.isTunnel()));
        setVisible();
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

    private void setPreferAac(View view) {
        PlayerSetting.putPreferAAC(!PlayerSetting.isPreferAAC());
        mBinding.preferAacText.setText(Setting.getSwitch(PlayerSetting.isPreferAAC()));
    }

    private void setDv7Fallback(View view) {
        PlayerSetting.putDv7HevcFallback(!PlayerSetting.isDv7HevcFallback());
        mBinding.dv7FallbackText.setText(Setting.getSwitch(PlayerSetting.isDv7HevcFallback()));
    }

    private void onPreloadSetting(View view) {
        SettingPreloadActivity.start(this);
    }

    private void onDecodeSetting(View view) {
        SettingDecodeActivity.start(this);
    }

    private void onUa(View view) {
        UaDialog.show(this);
    }

    @Override
    public void setUa(String ua) {
        Setting.putUa(ua);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mBinding != null) setPlaybackModeText();
    }
}
