package com.ssmhdssmhd.mxboxs.ui.activity;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textview.MaterialTextView;
import com.ssmhdssmhd.mxboxs.App;
import com.ssmhdssmhd.mxboxs.BuildConfig;
import com.ssmhdssmhd.mxboxs.R;
import com.ssmhdssmhd.mxboxs.setting.Setting;
import com.ssmhdssmhd.mxboxs.utils.Notify;
import com.ssmhdssmhd.mxboxs.utils.SocialApi;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 高级设置页面
 * 社交搜索（TG / X）配置在此页面，需在主设置页点击版本号 20 次解锁后才可见。
 */
public class SettingAdvancedActivity extends AppCompatActivity {

    private static final int SOCIAL_TARGET_TG = 0;
    private static final int SOCIAL_TARGET_X = 1;

    private Toolbar toolbar;
    private LinearLayout lockedHint;
    private LinearLayout socialCard;
    private MaterialTextView socialTgText;
    private MaterialTextView socialXText;
    private final Executor mSocialExec = Executors.newSingleThreadExecutor();

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingAdvancedActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting_advanced);

        toolbar = findViewById(R.id.toolbar);
        lockedHint = findViewById(R.id.lockedHint);
        socialCard = findViewById(R.id.socialCard);
        socialTgText = findViewById(R.id.socialTgText);
        socialXText = findViewById(R.id.socialXText);

        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        updateUnlockState();

        findViewById(R.id.socialTg).setOnClickListener(this::onSocialTg);
        findViewById(R.id.socialTg).setOnLongClickListener(v -> { clearSocialToken(SOCIAL_TARGET_TG); return true; });
        findViewById(R.id.socialX).setOnClickListener(this::onSocialX);
        findViewById(R.id.socialX).setOnLongClickListener(v -> { clearSocialToken(SOCIAL_TARGET_X); return true; });
        findViewById(R.id.socialTest).setOnClickListener(this::onSocialTest);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUnlockState();
    }

    private void updateUnlockState() {
        boolean unlocked = Setting.isSocialSearchUnlocked();
        lockedHint.setVisibility(unlocked ? View.GONE : View.VISIBLE);
        socialCard.setVisibility(unlocked ? View.VISIBLE : View.GONE);
        if (unlocked) refreshSocialStatus();
    }

    private void refreshSocialStatus() {
        if (Setting.isTgConnected()) {
            int len = Setting.getTgBotToken() == null ? 0 : Setting.getTgBotToken().length();
            socialTgText.setText(getString(R.string.setting_social_connected_len, len));
        } else {
            socialTgText.setText(R.string.setting_social_unconnected);
        }
        if (Setting.isXConnected()) {
            int len = Setting.getXBearerToken() == null ? 0 : Setting.getXBearerToken().length();
            socialXText.setText(getString(R.string.setting_social_connected_len, len));
        } else {
            socialXText.setText(R.string.setting_social_unconnected);
        }
    }

    // ======================= TG / X 社交搜索配置 =======================

    private void onSocialTg(View view) {
        String[] items = new String[]{
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

    private void onSocialX(View view) {
        String[] items = new String[]{
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

    private void onSocialTest(View view) {
        Notify.progress(this);
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
                new MaterialAlertDialogBuilder(SettingAdvancedActivity.this)
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
        if (TextUtils.isEmpty(txt)) txt = "";
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
