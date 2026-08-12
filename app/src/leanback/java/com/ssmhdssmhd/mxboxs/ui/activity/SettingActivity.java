package com.ssmhdssmhd.mxboxs.ui.activity;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
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
import com.ssmhdssmhd.mxboxs.utils.SocialApi;
import com.github.catvod.bean.Doh;
import com.github.catvod.net.OkHttp;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class SettingActivity extends BaseActivity implements ConfigListener, SiteListener, LiveListener, DohDialog.Listener {

    private static final int SOCIAL_TARGET_TG = 0;
    private static final int SOCIAL_TARGET_X  = 1;

    private ActivitySettingBinding mBinding;
    private String[] size;
    private final Executor mSocialExec = Executors.newSingleThreadExecutor();

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
        setCacheText();
        setOtherText();
        refreshSocialStatus();
    }

    private void refreshSocialStatus() {
        if (mBinding == null) return;
        if (Setting.isTgConnected()) {
            int len = Setting.getTgBotToken() == null ? 0 : Setting.getTgBotToken().length();
            mBinding.socialTgText.setText(getString(R.string.setting_social_connected_len, len));
        } else {
            mBinding.socialTgText.setText(R.string.setting_social_unconnected);
        }
        if (Setting.isXConnected()) {
            int len = Setting.getXBearerToken() == null ? 0 : Setting.getXBearerToken().length();
            mBinding.socialXText.setText(getString(R.string.setting_social_connected_len, len));
        } else {
            mBinding.socialXText.setText(R.string.setting_social_unconnected);
        }
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

        // TG / X 社交搜索设置（TV端没有摄像头，用粘贴/输入代替扫码）
        mBinding.socialTg.setOnClickListener(this::onSocialTg);
        mBinding.socialTg.setOnLongClickListener(this::onSocialTgClear);
        mBinding.socialX.setOnClickListener(this::onSocialX);
        mBinding.socialX.setOnLongClickListener(this::onSocialXClear);
        mBinding.socialTest.setOnClickListener(this::onSocialTest);
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

    // ======================= TG / X 社交搜索配置 =======================

    private void onSocialTg(View view) {
        String[] items = new String[] {
            getString(R.string.setting_social_paste),
            getString(R.string.setting_social_channels),
            getString(R.string.setting_social_clear)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.setting_social_tg)
                .setItems(items, (d, w) -> {
                    switch (w) {
                        case 0: pasteTokenTo(SOCIAL_TARGET_TG); break;
                        case 1: showChannelListDialog(); break;
                        case 2: clearSocialToken(SOCIAL_TARGET_TG); break;
                    }
                })
                .setNegativeButton(R.string.dialog_negative, null)
                .show();
    }

    private boolean onSocialTgClear(View v) { clearSocialToken(SOCIAL_TARGET_TG); return true; }

    private void onSocialX(View view) {
        String[] items = new String[] {
            getString(R.string.setting_social_paste),
            getString(R.string.setting_social_endpoint),
            getString(R.string.setting_social_clear)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.setting_social_x)
                .setItems(items, (d, w) -> {
                    switch (w) {
                        case 0: pasteTokenTo(SOCIAL_TARGET_X); break;
                        case 1: showXEndpointDialog(); break;
                        case 2: clearSocialToken(SOCIAL_TARGET_X); break;
                    }
                })
                .setNegativeButton(R.string.dialog_negative, null)
                .show();
    }

    private boolean onSocialXClear(View v) { clearSocialToken(SOCIAL_TARGET_X); return true; }

    private void onSocialTest(View view) {
        Notify.progress(getActivity());
        mSocialExec.execute(() -> {
            StringBuilder sb = new StringBuilder();
            boolean anyOk = false;
            if (Setting.isTgConnected()) {
                SocialApi.Result r1 = SocialApi.testTgBot();
                sb.append("TG: ").append(r1.ok ? "✓" : "✗").append(' ').append(r1.message).append('\n');
                if (r1.ok) anyOk = true;
                SocialApi.Result r2 = SocialApi.searchTg("1080p", 3);
                sb.append("TG 搜索 \"1080p\": ").append(r2.ok ? "✓" : "✗").append(' ')
                        .append(r2.message).append('\n');
                if (r2.hits != null && !r2.hits.isEmpty()) {
                    for (int i = 0; i < Math.min(3, r2.hits.size()); i++) {
                        sb.append("   • ").append(r2.hits.get(i).toString()).append('\n');
                    }
                }
            } else {
                sb.append("TG: (未配置 Token，跳过)\n");
            }
            if (Setting.isXConnected()) {
                SocialApi.Result r3 = SocialApi.testX();
                sb.append("X : ").append(r3.ok ? "✓" : "✗").append(' ').append(r3.message).append('\n');
                if (r3.ok) anyOk = true;
                SocialApi.Result r4 = SocialApi.searchX("movie trailer", 15);
                sb.append("X 搜索 \"movie trailer\": ").append(r4.ok ? "✓" : "✗").append(' ')
                        .append(r4.message).append('\n');
                if (r4.hits != null && !r4.hits.isEmpty()) {
                    for (int i = 0; i < Math.min(3, r4.hits.size()); i++) {
                        sb.append("   • ").append(r4.hits.get(i).toString()).append('\n');
                    }
                }
            } else {
                sb.append("X : (未配置 Bearer Token，跳过)\n");
            }
            if (!Setting.isTgConnected() && !Setting.isXConnected()) {
                sb.append("\n请先点 Telegram Bot / X 卡片粘贴 Token 后再试。");
            }
            String msg = sb.toString();
            boolean finalAnyOk = anyOk;
            App.post(() -> {
                Notify.dismiss();
                new MaterialAlertDialogBuilder(getActivity())
                        .setTitle(finalAnyOk ? "连接与搜索结果" : "未完成配置")
                        .setMessage(msg)
                        .setPositiveButton(R.string.dialog_positive, null)
                        .show();
            });
        });
    }

    private void pasteTokenTo(int target) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData cd = cm == null ? null : cm.getPrimaryClip();
        String txt = null;
        if (cd != null && cd.getItemCount() > 0 && cd.getItemAt(0) != null) {
            CharSequence cs = cd.getItemAt(0).coerceToText(this);
            txt = cs == null ? null : cs.toString();
        }
        if (TextUtils.isEmpty(txt)) {
            txt = "";
        }
        String prev = (target == SOCIAL_TARGET_TG) ? Setting.getTgBotToken() : Setting.getXBearerToken();
        int hintRes = (target == SOCIAL_TARGET_TG) ? R.string.setting_social_tg_hint : R.string.setting_social_x_hint;
        showTokenInputDialog(
                (target == SOCIAL_TARGET_TG) ? R.string.setting_social_tg : R.string.setting_social_x,
                hintRes,
                TextUtils.isEmpty(txt) ? prev : txt.trim(),
                v -> {
                    String s = v == null ? "" : v.trim();
                    if (target == SOCIAL_TARGET_TG) Setting.putTgBotToken(s);
                    else Setting.putXBearerToken(s);
                    refreshSocialStatus();
                    Notify.show(TextUtils.isEmpty(s) ? getString(R.string.setting_social_clear) : "已保存");
                });
    }

    private void clearSocialToken(int target) {
        if (target == SOCIAL_TARGET_TG) Setting.putTgBotToken("");
        else Setting.putXBearerToken("");
        refreshSocialStatus();
        Notify.show(R.string.setting_social_clear);
    }

    private void showTokenInputDialog(int titleRes, int hintRes, String initialText,
                                      java.util.function.Consumer<String> onSave) {
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        int padTop = (int) (16 * getResources().getDisplayMetrics().density);
        FrameLayout container = new FrameLayout(this);
        container.setPadding(pad, padTop, pad, 0);
        TextInputLayout til = new TextInputLayout(this);
        til.setHint(getString(hintRes));
        til.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        TextInputEditText et = new TextInputEditText(til.getContext());
        et.setSingleLine(false);
        et.setMinLines(3);
        et.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        if (!TextUtils.isEmpty(initialText)) {
            et.setText(initialText);
            et.setSelection(initialText.length());
        }
        til.addView(et);
        container.addView(til);
        new MaterialAlertDialogBuilder(this)
                .setTitle(titleRes)
                .setView(container)
                .setNegativeButton(R.string.dialog_negative, null)
                .setNeutralButton(R.string.setting_social_clear, (d, w) -> onSave.accept(""))
                .setPositiveButton(R.string.dialog_positive, (d, w) -> {
                    String val = et.getText() == null ? "" : et.getText().toString();
                    onSave.accept(val);
                })
                .show();
    }

    private void showChannelListDialog() {
        String cur = Setting.getTgChannelList();
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        int padTop = (int) (16 * getResources().getDisplayMetrics().density);
        FrameLayout container = new FrameLayout(this);
        container.setPadding(pad, padTop, pad, 0);
        TextInputLayout til = new TextInputLayout(this);
        til.setHint(getString(R.string.setting_social_channels_hint));
        til.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        TextInputEditText et = new TextInputEditText(til.getContext());
        et.setSingleLine(false);
        et.setMinLines(3);
        et.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        if (!TextUtils.isEmpty(cur)) { et.setText(cur); et.setSelection(cur.length()); }
        til.addView(et); container.addView(til);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.setting_social_channels)
                .setView(container)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, (d, w) -> {
                    Setting.putTgChannelList(et.getText() == null ? "" : et.getText().toString());
                    Notify.show("频道列表已保存");
                })
                .show();
    }

    private void showXEndpointDialog() {
        String cur = Setting.getXEndpointPrefix();
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        int padTop = (int) (16 * getResources().getDisplayMetrics().density);
        FrameLayout container = new FrameLayout(this);
        container.setPadding(pad, padTop, pad, 0);
        TextInputLayout til = new TextInputLayout(this);
        til.setHint(getString(R.string.setting_social_prefix_hint));
        til.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        TextInputEditText et = new TextInputEditText(til.getContext());
        et.setSingleLine(true);
        et.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        if (!TextUtils.isEmpty(cur)) { et.setText(cur); et.setSelection(cur.length()); }
        til.addView(et); container.addView(til);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.setting_social_endpoint)
                .setView(container)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, (d, w) -> {
                    Setting.putXEndpointPrefix(et.getText() == null ? "" : et.getText().toString());
                    Notify.show("X 端点已保存");
                })
                .show();
    }

}
