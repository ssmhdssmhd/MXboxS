package com.ssmhdssmhd.mxboxs.ui.activity;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 高级设置页面
 * 社交搜索（TG / X）配置在此页面，需在主设置页点击版本号 20 次解锁后才可见。
 *
 * <p>v5.5.62 增强：
 *   1) 主入口「高级设置」默认隐藏（仅解锁后 SettingFragment/SettingActivity 才置 visible）；
 *   2) 新增总开关（合并社交搜索到点播 → 关闭后即使有 token 也不请求）；
 *   3) 自动跳转到 TG App 或 X App 对应页面（BotFather 创建 / X Developer Portal 获取 token）；
 *   4) 连接测试成功后自动拉取并缓存 bot 账号名 / X @xxx，UI 显示缓存，无需重刷；
 *   5) 三档限速：TG 间隔 / X 间隔 / 单轮命中上限，全部带下限保护防误配；
 *   6) SocialApi 内部已做限速 sleep：保证「不要搜太快，避免被封账号」。
 */
public class SettingAdvancedActivity extends AppCompatActivity {

    private static final int SOCIAL_TARGET_TG = 0;
    private static final int SOCIAL_TARGET_X = 1;

    private Toolbar toolbar;
    private MaterialTextView lockedHint;
    private MaterialCardView socialCard;
    private MaterialTextView socialTgText;
    private MaterialTextView socialXText;

    private SwitchMaterial socialEnabledSwitch;
    private MaterialTextView socialTgRateText;
    private MaterialTextView socialXRateText;
    private MaterialTextView socialMaxHitsText;

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
        socialEnabledSwitch = findViewById(R.id.socialEnabledSwitch);
        socialTgRateText = findViewById(R.id.socialTgRateText);
        socialXRateText = findViewById(R.id.socialXRateText);
        socialMaxHitsText = findViewById(R.id.socialMaxHitsText);

        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        updateUnlockState();

        // TG / X 卡片：单击弹出菜单（粘贴/频道列表/清空），长按清空
        findViewById(R.id.socialTg).setOnClickListener(this::onSocialTg);
        findViewById(R.id.socialTg).setOnLongClickListener(v -> { clearSocialToken(SOCIAL_TARGET_TG); return true; });
        findViewById(R.id.socialX).setOnClickListener(this::onSocialX);
        findViewById(R.id.socialX).setOnLongClickListener(v -> { clearSocialToken(SOCIAL_TARGET_X); return true; });
        // 总开关
        View enabledRow = findViewById(R.id.socialEnabledRow);
        if (enabledRow != null) enabledRow.setOnClickListener(this::onToggleSocialEnabled);
        // 限速档
        View tgRate = findViewById(R.id.socialTgRate);
        if (tgRate != null) tgRate.setOnClickListener(v -> onEditRate(SOCIAL_TARGET_TG));
        View xRate = findViewById(R.id.socialXRate);
        if (xRate != null) xRate.setOnClickListener(v -> onEditRate(SOCIAL_TARGET_X));
        View maxHits = findViewById(R.id.socialMaxHits);
        if (maxHits != null) maxHits.setOnClickListener(v -> onEditMaxHits());
        // 跳转对应 App（用户需求：自动跳转到对应 app 配置 token）
        View jumpTg = findViewById(R.id.socialJumpTg);
        if (jumpTg != null) jumpTg.setOnClickListener(v -> onJumpToApp(SOCIAL_TARGET_TG));
        View jumpX = findViewById(R.id.socialJumpX);
        if (jumpX != null) jumpX.setOnClickListener(v -> onJumpToApp(SOCIAL_TARGET_X));
        // 测试
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
        if (unlocked) {
            refreshSocialStatus();
            refreshRateText();
            refreshToggleSwitch();
        }
    }

    private void refreshSocialStatus() {
        // TG：优先显示已缓存的账号标签（上次 testTgBot ok 后持久化到本地），否则显示 token 长度。
        if (Setting.isTgConnected()) {
            String label = Setting.getTgAccountLabel();
            if (!TextUtils.isEmpty(label)) {
                socialTgText.setText(getString(R.string.setting_social_connected_account, label));
            } else {
                int len = Setting.getTgBotToken() == null ? 0 : Setting.getTgBotToken().length();
                socialTgText.setText(getString(R.string.setting_social_connected_len, len));
            }
        } else {
            socialTgText.setText(R.string.setting_social_unconnected);
        }
        // X：同样优先账号缓存。
        if (Setting.isXConnected()) {
            String label = Setting.getXAccountLabel();
            if (!TextUtils.isEmpty(label)) {
                socialXText.setText(getString(R.string.setting_social_connected_account, label));
            } else {
                int len = Setting.getXBearerToken() == null ? 0 : Setting.getXBearerToken().length();
                socialXText.setText(getString(R.string.setting_social_connected_len, len));
            }
        } else {
            socialXText.setText(R.string.setting_social_unconnected);
        }
    }

    private void refreshRateText() {
        if (socialTgRateText != null) {
            socialTgRateText.setText(getString(R.string.setting_social_rate_value,
                    Setting.getSocialTgMinIntervalMs() / 1000.0f));
        }
        if (socialXRateText != null) {
            socialXRateText.setText(getString(R.string.setting_social_rate_value,
                    Setting.getSocialXMinIntervalMs() / 1000.0f));
        }
        if (socialMaxHitsText != null) {
            socialMaxHitsText.setText(getString(R.string.setting_social_rate_hits_value,
                    Setting.getSocialMaxHitsPerSearch()));
        }
    }

    private void refreshToggleSwitch() {
        if (socialEnabledSwitch != null) {
            socialEnabledSwitch.setChecked(Setting.isSocialSearchEnabled());
        }
    }

    private void onToggleSocialEnabled(View view) {
        boolean cur = Setting.isSocialSearchEnabled();
        Setting.putSocialSearchEnabled(!cur);
        socialEnabledSwitch.setChecked(!cur);
        Notify.show(!cur ? "已开启社交搜索合并" : "已关闭社交搜索合并");
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

    /**
     * 用户需求：自动跳转到对应 app（TG → BotFather 创建 bot；X → Developer → Projects & Apps → Bearer Token）。
     *
     * <p>实现：优先发送 ACTION_VIEW 对应 scheme / URL。如果用户手机安装了官方 Telegram / X，
     * Android 系统会在选择器里优先给官方 App；没装就回退默认浏览器。
     */
    private void onJumpToApp(int target) {
        String url;
        String fallbackHint;
        if (target == SOCIAL_TARGET_TG) {
            // 1) 优先 tg://resolve?domain=BotFather（tg 官方 scheme）；
            // 2) 回退 https://t.me/BotFather（手机浏览器可二次跳 TG App）。
            url = "https://t.me/BotFather";
            fallbackHint = "未检测到 Telegram，请先从应用商店安装后再试。";
        } else {
            // X (Twitter)：先跳开发者入口（登录 X 账号后进入 Dashboard 拿 Bearer Token v2）。
            url = "https://developer.x.com/";
            fallbackHint = "未检测到 X (Twitter) 客户端，默认浏览器会打开 X 开发者中心。";
        }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable t) {
            Notify.show(fallbackHint);
        }
    }

    /** 限速编辑（目标为 TG/X，单位 ms；弹窗输入 秒，便于理解）。*/
    private void onEditRate(int target) {
        long curMs = (target == SOCIAL_TARGET_TG)
                ? Setting.getSocialTgMinIntervalMs()
                : Setting.getSocialXMinIntervalMs();
        long floorMs = (target == SOCIAL_TARGET_TG) ? 500L : 800L;
        int titleRes = (target == SOCIAL_TARGET_TG) ? R.string.setting_social_rate_tg : R.string.setting_social_rate_x;
        int hintRes  = (target == SOCIAL_TARGET_TG) ? R.string.setting_social_rate_hint_tg : R.string.setting_social_rate_hint_x;
        float curSec = curMs / 1000.0f;
        showFloatInputDialog(titleRes, hintRes, curSec, 1, 2, valSec -> {
            if (valSec == null) return;
            long ms = Math.max(floorMs, Math.round(valSec * 1000L));
            if (target == SOCIAL_TARGET_TG) Setting.putSocialTgMinIntervalMs(ms);
            else Setting.putSocialXMinIntervalMs(ms);
            refreshRateText();
            Notify.show("已保存：" + getString(R.string.setting_social_rate_value, ms / 1000.0f));
        });
    }

    private void onEditMaxHits() {
        int cur = Setting.getSocialMaxHitsPerSearch();
        showIntInputDialog(R.string.setting_social_rate_hits, R.string.setting_social_rate_hint_hits, cur, 1, 100, val -> {
            if (val == null) return;
            Setting.putSocialMaxHitsPerSearch(val);
            refreshRateText();
            Notify.show("已保存：" + getString(R.string.setting_social_rate_hits_value, val));
        });
    }

    private void onSocialTest(View view) {
        if (!Setting.isSocialSearchEnabled()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("社交搜索已关闭")
                    .setMessage("请先打开上方「合并社交搜索到点播」总开关，再测试连接。")
                    .setPositiveButton(R.string.dialog_positive, null)
                    .show();
            return;
        }
        Notify.progress(this);
        mSocialExec.execute(() -> {
            StringBuilder sb = new StringBuilder();
            boolean anyOk = false;
            if (Setting.isTgConnected()) {
                SocialApi.Result r1 = SocialApi.testTgBot();
                sb.append("TG: ").append(r1.ok ? "✓" : "✗").append(' ').append(r1.message).append('\n');
                if (r1.ok) {
                    anyOk = true;
                    // 连接成功 → 提取并缓存 bot 账号显示名（形如 "@BotFatherBot id=xx" / "昵称: XXX  @yyy"）
                    String label = extractTgAccountLabelFromTestMsg(r1.message);
                    if (!TextUtils.isEmpty(label)) Setting.putTgAccountLabel(label);
                }
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
                if (r3.ok) {
                    anyOk = true;
                    String label = extractXAccountLabelFromTestMsg(r3.message);
                    if (!TextUtils.isEmpty(label)) Setting.putXAccountLabel(label);
                }
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
                sb.append("\n请先点 Telegram Bot / X 卡片粘贴 Token 后再试（或点底部「打开 Telegram / 打开 X」去官方 App 快速创建）。");
            }
            String msg = sb.toString();
            boolean finalAnyOk = anyOk;
            App.post(() -> {
                Notify.dismiss();
                // 刷新 UI 上的账号缓存（如果有新写入）
                refreshSocialStatus();
                new MaterialAlertDialogBuilder(SettingAdvancedActivity.this)
                        .setTitle(finalAnyOk ? "连接与搜索结果" : "未完成配置")
                        .setMessage(msg)
                        .setPositiveButton(R.string.dialog_positive, null)
                        .show();
            });
        });
    }

    private static final Pattern TG_LABEL_AT    = Pattern.compile("@(\\S+)");
    private static final Pattern TG_LABEL_NICK  = Pattern.compile("昵称:\\s*([^\\n]+?)(?:\\s*@|$|\\s+id=)");
    private static final Pattern TG_LABEL_ID    = Pattern.compile("id=(\\d+)");

    private static String extractTgAccountLabelFromTestMsg(String msg) {
        if (TextUtils.isEmpty(msg)) return "";
        // 优先 "@昵称  id=xxx"
        try {
            Matcher mat = TG_LABEL_AT.matcher(msg);
            String at = mat.find() ? mat.group(1) : null;
            Matcher mnick = TG_LABEL_NICK.matcher(msg);
            String nick = mnick.find() ? mnick.group(1) : null;
            Matcher mid = TG_LABEL_ID.matcher(msg);
            String id = mid.find() ? mid.group(1) : null;
            StringBuilder sb = new StringBuilder();
            if (!TextUtils.isEmpty(nick)) sb.append(nick);
            if (!TextUtils.isEmpty(at)) {
                if (sb.length() > 0) sb.append(' ');
                sb.append('@').append(at);
            }
            if (!TextUtils.isEmpty(id)) {
                if (sb.length() > 0) sb.append(' ');
                sb.append("id=").append(id);
            }
            return sb.toString().trim();
        } catch (Throwable ignore) { return ""; }
    }

    private static final Pattern X_LABEL_NICK = Pattern.compile("昵称:\\s*([^\\n]+?)(?:\\s*@|$|\\s+id=)");
    private static final Pattern X_LABEL_AT   = Pattern.compile("@(\\S+)");
    private static final Pattern X_LABEL_ID   = Pattern.compile("id=(\\S+)");

    private static String extractXAccountLabelFromTestMsg(String msg) {
        if (TextUtils.isEmpty(msg)) return "";
        try {
            Matcher mnick = X_LABEL_NICK.matcher(msg);
            String nick = mnick.find() ? mnick.group(1) : null;
            Matcher mat = X_LABEL_AT.matcher(msg);
            String at = mat.find() ? mat.group(1) : null;
            Matcher mid = X_LABEL_ID.matcher(msg);
            String id = mid.find() ? mid.group(1) : null;
            StringBuilder sb = new StringBuilder();
            if (!TextUtils.isEmpty(nick)) sb.append(nick);
            if (!TextUtils.isEmpty(at)) {
                if (sb.length() > 0) sb.append(' ');
                sb.append('@').append(at);
            }
            if (!TextUtils.isEmpty(id)) {
                if (sb.length() > 0) sb.append(' ');
                sb.append("id=").append(id);
            }
            return sb.toString().trim();
        } catch (Throwable ignore) { return ""; }
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
                    if (target == SOCIAL_TARGET_TG) {
                        Setting.putTgBotToken(s);
                        if (TextUtils.isEmpty(s)) Setting.putTgAccountLabel(""); // 清空 token 时也清 label
                    } else {
                        Setting.putXBearerToken(s);
                        if (TextUtils.isEmpty(s)) Setting.putXAccountLabel("");
                    }
                    refreshSocialStatus();
                    Notify.show(TextUtils.isEmpty(s) ? getString(R.string.setting_social_clear) : "已保存");
                });
    }

    private void clearSocialToken(int target) {
        if (target == SOCIAL_TARGET_TG) { Setting.putTgBotToken(""); Setting.putTgAccountLabel(""); }
        else { Setting.putXBearerToken(""); Setting.putXAccountLabel(""); }
        refreshSocialStatus();
        Notify.show(R.string.setting_social_clear);
    }

    // ======================= 通用弹窗工具 =======================

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

    private void showFloatInputDialog(int titleRes, int hintRes, float initialVal,
                                      int minFracDigits, int maxFracDigits,
                                      java.util.function.Consumer<Float> onSave) {
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        int padTop = (int) (16 * getResources().getDisplayMetrics().density);
        FrameLayout container = new FrameLayout(this);
        container.setPadding(pad, padTop, pad, 0);
        TextInputLayout til = new TextInputLayout(this);
        til.setHint(getString(hintRes));
        til.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        TextInputEditText et = new TextInputEditText(til.getContext());
        et.setSingleLine(true);
        et.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        String initStr = java.text.NumberFormat.getInstance(java.util.Locale.ROOT).format(initialVal);
        // 避免 1.20000005 / 1.5 保留一位即可
        try { initStr = String.format(java.util.Locale.ROOT, "%." + maxFracDigits + "f", initialVal); } catch (Throwable ignore) {}
        et.setText(initStr);
        if (et.getText() != null) et.setSelection(et.getText().length());
        til.addView(et); container.addView(til);
        new MaterialAlertDialogBuilder(this)
                .setTitle(titleRes)
                .setView(container)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, (d, w) -> {
                    Float v = null;
                    if (et.getText() != null) {
                        try { v = Float.parseFloat(et.getText().toString().trim()); }
                        catch (Throwable ignore) { Notify.show("格式错误，请输入合法数字（如 1.2）"); return; }
                    }
                    onSave.accept(v);
                })
                .show();
    }

    private void showIntInputDialog(int titleRes, int hintRes, int initialVal,
                                    int min, int max,
                                    java.util.function.Consumer<Integer> onSave) {
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        int padTop = (int) (16 * getResources().getDisplayMetrics().density);
        FrameLayout container = new FrameLayout(this);
        container.setPadding(pad, padTop, pad, 0);
        TextInputLayout til = new TextInputLayout(this);
        til.setHint(getString(hintRes) + "（范围 " + min + "~" + max + "）");
        til.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        TextInputEditText et = new TextInputEditText(til.getContext());
        et.setSingleLine(true);
        et.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        et.setText(String.valueOf(initialVal));
        if (et.getText() != null) et.setSelection(et.getText().length());
        til.addView(et); container.addView(til);
        new MaterialAlertDialogBuilder(this)
                .setTitle(titleRes)
                .setView(container)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, (d, w) -> {
                    Integer v = null;
                    if (et.getText() != null) {
                        try { v = Integer.parseInt(et.getText().toString().trim()); }
                        catch (Throwable ignore) { Notify.show("格式错误，请输入整数"); return; }
                    }
                    onSave.accept(v);
                })
                .show();
    }
}
