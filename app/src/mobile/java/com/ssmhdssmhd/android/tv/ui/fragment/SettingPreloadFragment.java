package com.ssmhdssmhd.android.tv.ui.fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

import com.ssmhdssmhd.android.tv.R;
import com.ssmhdssmhd.android.tv.databinding.FragmentSettingPreloadBinding;
import com.ssmhdssmhd.android.tv.setting.PlayerSetting;
import com.ssmhdssmhd.android.tv.setting.PreloadSetting;
import com.ssmhdssmhd.android.tv.setting.Setting;
import com.ssmhdssmhd.android.tv.ui.base.BaseFragment;
import com.ssmhdssmhd.android.tv.ui.dialog.PreloadDialog;
import com.ssmhdssmhd.android.tv.utils.FileUtil;

public class SettingPreloadFragment extends BaseFragment {

    private FragmentSettingPreloadBinding mBinding;

    public static SettingPreloadFragment newInstance() {
        return new SettingPreloadFragment();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingPreloadBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        refresh();
    }

    @Override
    protected void initEvent() {
        mBinding.preload.setOnClickListener(this::setPreload);
        mBinding.preloadSize.setOnClickListener(view -> PreloadDialog.show(this, PreloadDialog.SIZE));
        mBinding.preloadTime.setOnClickListener(view -> PreloadDialog.show(this, PreloadDialog.TIME));
        mBinding.preloadThread.setOnClickListener(view -> PreloadDialog.show(this, PreloadDialog.THREADS));
    }

    private void refresh() {
        mBinding.preloadText.setText(Setting.getSwitch(PreloadSetting.isPreload()));
        setPreloadThreadsText();
        setPreloadSizeText();
        setPreloadTimeText();
        setVisible();
    }

    private void setVisible() {
        boolean preload = PreloadSetting.isPreload();
        mBinding.preloadSize.setVisibility(preload ? View.VISIBLE : View.GONE);
        mBinding.preloadTime.setVisibility(preload ? View.VISIBLE : View.GONE);
        mBinding.preloadThread.setVisibility(preload && !PlayerSetting.isMpv() ? View.VISIBLE : View.GONE);
    }

    private void setPreload(View view) {
        PreloadSetting.putPreload(!PreloadSetting.isPreload());
        mBinding.preloadText.setText(Setting.getSwitch(PreloadSetting.isPreload()));
        setVisible();
    }

    public void setPreload(int type, int value) {
        if (type == PreloadDialog.THREADS) {
            PreloadSetting.putPreloadThreads(value);
            setPreloadThreadsText();
        } else if (type == PreloadDialog.SIZE) {
            PreloadSetting.putPreloadSizeMb(value);
            setPreloadSizeText();
        } else if (type == PreloadDialog.TIME) {
            PreloadSetting.putPreloadTimeSeconds(value);
            setPreloadTimeText();
        }
    }

    private void setPreloadSizeText() {
        mBinding.preloadSizeText.setText(FileUtil.byteCountToDisplaySize(PreloadSetting.getPreloadSizeBytes()));
    }

    private void setPreloadTimeText() {
        mBinding.preloadTimeText.setText(getString(R.string.player_preload_time_value, PreloadSetting.getPreloadTimeSeconds()));
    }

    private void setPreloadThreadsText() {
        mBinding.preloadThreadText.setText(getString(R.string.player_preload_threads_value, PreloadSetting.getPreloadThreads()));
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (!hidden) refresh();
    }
}
