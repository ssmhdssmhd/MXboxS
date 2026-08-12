package com.ssmhdssmhd.mxboxs.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ScrollView;

import androidx.viewbinding.ViewBinding;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import com.ssmhdssmhd.mxboxs.App;
import com.ssmhdssmhd.mxboxs.BuildConfig;
import com.ssmhdssmhd.mxboxs.R;
import com.ssmhdssmhd.mxboxs.Updater;
import com.ssmhdssmhd.mxboxs.api.config.LiveConfig;
import com.ssmhdssmhd.mxboxs.api.config.VodConfig;
import com.ssmhdssmhd.mxboxs.api.config.WallConfig;
import com.ssmhdssmhd.mxboxs.bean.Config;
import com.ssmhdssmhd.mxboxs.bean.Live;
import com.ssmhdssmhd.mxboxs.bean.Site;
import com.ssmhdssmhd.mxboxs.databinding.ActivitySettingBinding;
import com.ssmhdssmhd.mxboxs.db.AppDatabase;
import com.ssmhdssmhd.mxboxs.event.ConfigEvent;
import com.ssmhdssmhd.mxboxs.event.RefreshEvent;
import com.ssmhdssmhd.mxboxs.impl.Callback;
import com.ssmhdssmhd.mxboxs.impl.ConfigListener;
import com.ssmhdssmhd.mxboxs.impl.LiveListener;
import com.ssmhdssmhd.mxboxs.impl.SiteListener;
import com.ssmhdssmhd.mxboxs.setting.PlayerSetting;
import com.ssmhdssmhd.mxboxs.setting.Setting;
import com.ssmhdssmhd.mxboxs.ui.base.BaseActivity;
import com.ssmhdssmhd.mxboxs.ui.dialog.ConfigDialog;
import com.ssmhdssmhd.mxboxs.ui.dialog.DohDialog;
import com.ssmhdssmhd.mxboxs.ui.dialog.HistoryDialog;
import com.ssmhdssmhd.mxboxs.ui.dialog.LiveDialog;
import com.ssmhdssmhd.mxboxs.ui.dialog.RestoreDialog;
import com.ssmhdssmhd.mxboxs.ui.dialog.SiteDialog;
import com.ssmhdssmhd.mxboxs.utils.FileUtil;
import com.ssmhdssmhd.mxboxs.utils.Notify;
import com.ssmhdssmhd.mxboxs.utils.PermissionUtil;
import com.ssmhdssmhd.mxboxs.utils.QRCode;
import com.ssmhdssmhd.mxboxs.utils.ResUtil;
import com.github.catvod.bean.Doh;
import com.github.catvod.net.OkHttp;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

public class SettingActivity extends BaseActivity implements ConfigListener, SiteListener, LiveListener, DohDialog.Listener {

    private ActivitySettingBinding mBinding;
    private String[] size;
    private int mVersionClickCount = 0;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingActivity.class));
    }

    private int getDohIndex() {
        return Math.max(0, VodConfig.get().getDoh().indexOf(Doh.objectFrom(Setting.getDoh())));
    }

    private String[] getDohList() {
        List<String> list = new ArrayList<>();
        for (Doh item : VodConfig.get().getDoh()) list.add(item.getName());
        return list.toArray(new String[0]);
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySettingBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mBinding.vod.requestFocus();
        mBinding.vodUrl.setText(VodConfig.getDesc());
        mBinding.liveUrl.setText(LiveConfig.getDesc());
        mBinding.wallUrl.setText(WallConfig.getDesc());
        mBinding.versionText.setText(BuildConfig.VERSION_NAME);
        mBinding.versionLabel.setText("v" + BuildConfig.VERSION_NAME);
        setCacheText();
        setOtherText();
        refreshAdvancedVisibility();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAdvancedVisibility();
    }

    /** 高级设置入口默认隐藏（TV 端同移动端逻辑：仅在点击版本号 20 次解锁后可见）。*/
    private void refreshAdvancedVisibility() {
        if (mBinding == null) return;
        boolean unlocked = Setting.isSocialSearchUnlocked();
        mBinding.advanced.setVisibility(unlocked ? View.VISIBLE : View.GONE);
    }

    private void setOtherText() {
        mBinding.dohText.setText(getDohList()[getDohIndex()]);
        mBinding.incognitoText.setText(Setting.getSwitch(Setting.isIncognito()));
        mBinding.wallSoundText.setText(Setting.getSwitch(Setting.getWallSound()));
        mBinding.sizeText.setText((size = ResUtil.getStringArray(R.array.select_size))[PlayerSetting.getSize()]);
        String prefix = Setting.getParseServerPrefix();
        mBinding.parseServerText.setText(prefix.isEmpty() ? getString(R.string.setting_off) : prefix);
    }

    private void setCacheText() {
        FileUtil.getCacheSize(new Callback() {
            @Override
            public void success(String result) {
                mBinding.cacheText.setText(result);
            }
        });
    }

    @Override
    protected void initEvent() {
        mBinding.vod.setOnClickListener(this::onVod);
        mBinding.doh.setOnClickListener(this::setDoh);
        mBinding.live.setOnClickListener(this::onLive);
        mBinding.wall.setOnClickListener(this::onWall);
        mBinding.size.setOnClickListener(this::setSize);
        mBinding.cache.setOnClickListener(this::onCache);
        mBinding.backup.setOnClickListener(this::onBackup);
        mBinding.player.setOnClickListener(this::onPlayer);
        mBinding.aiSetting.setOnClickListener(this::onAiSetting);
        mBinding.danmaku.setOnClickListener(this::onDanmaku);
        mBinding.restore.setOnClickListener(this::onRestore);
        mBinding.version.setOnClickListener(this::onVersion);
        mBinding.version.setOnLongClickListener(this::onVersionMirror);
        mBinding.parseServer.setOnClickListener(this::onParseServer);
        mBinding.parseServer.setOnLongClickListener(this::onParseServerReset);
        mBinding.vod.setOnLongClickListener(this::onVodEdit);
        mBinding.vodHome.setOnClickListener(this::onVodHome);
        mBinding.live.setOnLongClickListener(this::onLiveEdit);
        mBinding.liveHome.setOnClickListener(this::onLiveHome);
        mBinding.wall.setOnLongClickListener(this::onWallEdit);
        mBinding.incognito.setOnClickListener(this::setIncognito);
        mBinding.vodHistory.setOnClickListener(this::onVodHistory);
        mBinding.liveHistory.setOnClickListener(this::onLiveHistory);
        mBinding.wallDefault.setOnClickListener(this::setWallDefault);
        mBinding.wallRefresh.setOnClickListener(this::setWallRefresh);
        mBinding.wallRefresh.setOnLongClickListener(this::onWallHistory);
        mBinding.wallSound.setOnClickListener(this::setWallSound);
        mBinding.advanced.setOnClickListener(this::onAdvanced);
        mBinding.versionLabel.setOnClickListener(this::onVersionLabelClick);
    }

    @Override
    public void setConfig(Config config) {
        if (config.getUrl().startsWith("file")) {
            PermissionUtil.requestFile(this, allGranted -> load(config));
        } else {
            load(config);
        }
    }

    private void load(Config config) {
        switch (config.getType()) {
            case 0:
                VodConfig.load(config, getCallback());
                break;
            case 1:
                LiveConfig.load(config, getCallback());
                break;
            case 2:
                Setting.putWall(0);
                WallConfig.load(config, getCallback());
                break;
        }
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void start() {
                Notify.progress(getActivity());
            }

            @Override
            public void success() {
                Notify.dismiss();
                setCacheText();
            }

            @Override
            public void error(String msg) {
                Notify.dismiss();
                Notify.show(msg);
            }
        };
    }

    @Override
    public void setSite(Site item) {
        VodConfig.get().setHome(item);
    }

    @Override
    public void setLive(Live item) {
        LiveConfig.get().setHome(item);
    }

    private void onVod(View view) {
        ConfigDialog.create().vod().show(this);
    }

    private void onLive(View view) {
        ConfigDialog.create().live().show(this);
    }

    private void onWall(View view) {
        ConfigDialog.create().wall().show(this);
    }

    private boolean onVodEdit(View view) {
        ConfigDialog.create().vod().edit().show(this);
        return true;
    }

    private boolean onLiveEdit(View view) {
        ConfigDialog.create().live().edit().show(this);
        return true;
    }

    private boolean onWallEdit(View view) {
        ConfigDialog.create().wall().edit().show(this);
        return true;
    }

    private void onVodHome(View view) {
        SiteDialog.create().action().show(this);
    }

    private void onLiveHome(View view) {
        LiveDialog.create().action().show(this);
    }

    private void onVodHistory(View view) {
        HistoryDialog.create().vod().show(this);
    }

    private void onLiveHistory(View view) {
        HistoryDialog.create().live().show(this);
    }

    private void onPlayer(View view) {
        SettingPlayerActivity.start(this);
    }

    private void onAiSetting(View view) {
        SettingAiActivity.start(this);
    }

    private void onDanmaku(View view) {
        SettingDanmakuActivity.start(this);
    }

    private void onAdvanced(View view) {
        SettingAdvancedActivity.start(this);
    }

    private void onVersionLabelClick(View view) {
        if (Setting.isSocialSearchUnlocked()) { refreshAdvancedVisibility(); return; }
        mVersionClickCount++;
        int remaining = 20 - mVersionClickCount;
        if (remaining <= 0) {
            Setting.putSocialSearchUnlocked(true);
            refreshAdvancedVisibility();
            Notify.show(R.string.setting_advanced_unlocked);
        } else if (remaining <= 5) {
            Notify.show(getString(R.string.setting_advanced_unlock_hint, remaining));
        }
    }

    private void onVersion(View view) {
        Updater.create().force().start(this);
    }

    private boolean onVersionMirror(View view) {
        Updater.create().showMirrorDialog(this);
        return true;
    }

    private void setWallDefault(View view) {
        Setting.putWall(Setting.getWall() == 4 ? 1 : Setting.getWall() + 1);
        Setting.putWallType(0);
        ConfigEvent.wall();
    }

    private void setWallRefresh(View view) {
        Setting.putWall(0);
        WallConfig.get().load(getCallback());
    }

    private boolean onWallHistory(View view) {
        HistoryDialog.create().wall().show(this);
        return true;
    }

    private void setIncognito(View view) {
        Setting.putIncognito(!Setting.isIncognito());
        mBinding.incognitoText.setText(Setting.getSwitch(Setting.isIncognito()));
    }

    private void setWallSound(View view) {
        Setting.putWallSound(!Setting.getWallSound());
        mBinding.wallSoundText.setText(Setting.getSwitch(Setting.getWallSound()));
        ConfigEvent.common();
    }

    private void setSize(View view) {
        int index = (PlayerSetting.getSize() + 1) % size.length;
        mBinding.sizeText.setText(size[index]);
        PlayerSetting.putSize(index);
        RefreshEvent.size();
    }

    private void setDoh(View view) {
        DohDialog.create().index(getDohIndex()).show(this);
    }

    @Override
    public void setDoh(Doh doh) {
        OkHttp.dns().setDoh(doh);
        Setting.putDoh(doh.toString());
        mBinding.dohText.setText(doh.getName());
    }

    private void onCache(View view) {
        FileUtil.clearCache(new Callback() {
            @Override
            public void success() {
                setCacheText();
            }
        });
    }

    private void onBackup(View view) {
        PermissionUtil.requestFile(this, allGranted -> AppDatabase.backup(new Callback() {
            @Override
            public void success() {
                Notify.show(R.string.backup_success);
            }

            @Override
            public void error() {
                Notify.show(R.string.backup_fail);
            }
        }));
    }

    private void onRestore(View view) {
        PermissionUtil.requestFile(this, allGranted -> RestoreDialog.create().callback(new Callback() {
            @Override
            public void success() {
                Notify.show(R.string.restore_success);
                setOtherText();
                initConfig();
            }

            @Override
            public void error() {
                Notify.show(R.string.restore_fail);
            }
        }).show(this));
    }

    private void onParseServer(View view) {
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        int padTop = (int) (16 * getResources().getDisplayMetrics().density);
        FrameLayout container = new FrameLayout(this);
        container.setPadding(pad, padTop, pad, 0);

        TextInputLayout til = new TextInputLayout(this);
        til.setHint(getString(R.string.setting_parse_server_hint));
        til.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        TextInputEditText et = new TextInputEditText(til.getContext());
        et.setSingleLine(true);
        et.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        String cur = Setting.getParseServerPrefix();
        et.setText(cur.isEmpty() ? "" : cur);
        if (!TextUtils.isEmpty(et.getText())) et.setSelection(et.getText().length());
        til.addView(et);
        container.addView(til);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.setting_parse_server)
                .setView(container)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, (d, w) -> {
                    String val = et.getText() == null ? "" : et.getText().toString().trim();
                    Setting.putParseServerPrefix(val);
                    String prefix = Setting.getParseServerPrefix();
                    mBinding.parseServerText.setText(prefix.isEmpty() ? getString(R.string.setting_off) : prefix);
                    Notify.show(prefix.isEmpty() ? getString(R.string.setting_off) : prefix);
                });
        builder.show();
    }

    private boolean onParseServerReset(View view) {
        Setting.putParseServerPrefix(Setting.PARSE_SERVER_DEFAULT);
        mBinding.parseServerText.setText(Setting.PARSE_SERVER_DEFAULT);
        Notify.show(R.string.setting_parse_server_default);
        return true;
    }

    private void initConfig() {
        VodConfig.get().init().load(getCallback());
        LiveConfig.get().init().load();
        WallConfig.get().init().load();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        if (event.type() != ConfigEvent.Type.COMMON) return;
        mBinding.vodUrl.setText(VodConfig.getDesc());
        mBinding.liveUrl.setText(LiveConfig.getDesc());
        mBinding.wallUrl.setText(WallConfig.getDesc());
    }

}
