package com.ssmhdssmhd.mxboxs.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowManager;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textview.MaterialTextView;
import com.ssmhdssmhd.mxboxs.R;
import com.ssmhdssmhd.mxboxs.setting.Setting;
import com.ssmhdssmhd.mxboxs.utils.KamiUtil;
import com.ssmhdssmhd.mxboxs.utils.Notify;
import com.ssmhdssmhd.mxboxs.utils.Task;

/**
 * 会员卡密激活界面
 * <p>
 * 启动流程：
 * - HomeActivity 启动时检测未激活 → 跳转本 Activity → HomeActivity 自身 finish
 * - 本 Activity 验证卡密通过 → 启动 HomeActivity → finishAffinity
 * - 用户未激活按返回 / 点退出 → finishAffinity 退出 App
 * <p>
 * 已激活用户再次进入（例如从设置页）会显示已激活面板，可一键进入或注销。
 */
public class KamiActivity extends AppCompatActivity {

    private TextInputLayout kamiInputLayout;
    private TextInputEditText kamiInput;
    private MaterialTextView status;
    private CircularProgressIndicator progress;
    private MaterialButton activateBtn;
    private MaterialButton purchaseBtn;
    private MaterialButton exitBtn;

    private View activatedPanel;
    private MaterialTextView kamiShow;
    private MaterialButton enterBtn;
    private MaterialButton logoutBtn;

    private boolean verifying;

    public static void start(Activity activity) {
        Intent intent = new Intent(activity, KamiActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_kami);
        bindViews();
        setupBackPress();
        // 已激活直接展示已激活面板
        if (KamiUtil.isActivated()) {
            showActivated();
        } else {
            showInput();
        }
    }

    private void bindViews() {
        kamiInputLayout = findViewById(R.id.kamiInputLayout);
        kamiInput = findViewById(R.id.kamiInput);
        status = findViewById(R.id.status);
        progress = findViewById(R.id.progress);
        activateBtn = findViewById(R.id.activateBtn);
        purchaseBtn = findViewById(R.id.purchaseBtn);
        exitBtn = findViewById(R.id.exitBtn);
        activatedPanel = findViewById(R.id.activatedPanel);
        kamiShow = findViewById(R.id.kamiShow);
        enterBtn = findViewById(R.id.enterBtn);
        logoutBtn = findViewById(R.id.logoutBtn);

        activateBtn.setOnClickListener(v -> onActivate());
        purchaseBtn.setOnClickListener(v -> onPurchase());
        exitBtn.setOnClickListener(v -> exitApp());
        enterBtn.setOnClickListener(v -> enterHome());
        logoutBtn.setOnClickListener(v -> onLogout());
    }

    private void setupBackPress() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // 未激活时按返回 = 退出 App；已激活时按返回 = 进入首页
                if (KamiUtil.isActivated()) {
                    enterHome();
                } else {
                    exitApp();
                }
            }
        });
    }

    // ---------------- 未激活：输入卡密 ----------------

    private void showInput() {
        activatedPanel.setVisibility(View.GONE);
        kamiInputLayout.setVisibility(View.VISIBLE);
        activateBtn.setVisibility(View.VISIBLE);
        purchaseBtn.setVisibility(View.VISIBLE);
        exitBtn.setVisibility(View.VISIBLE);
        setStatus("", false);
        if (kamiInput.requestFocus()) {
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
    }

    private void onActivate() {
        if (verifying) return;
        String input = kamiInput.getText() == null ? "" : kamiInput.getText().toString().trim();
        if (TextUtils.isEmpty(input)) {
            kamiInputLayout.setError(getString(R.string.kami_err_empty));
            return;
        }
        kamiInputLayout.setError(null);
        verifying = true;
        setStatus(getString(R.string.kami_verifying), true);
        activateBtn.setEnabled(false);

        Task.execute(() -> {
            boolean ok = KamiUtil.verifyFresh(input);
            runOnUiThread(() -> {
                verifying = false;
                activateBtn.setEnabled(true);
                setStatus("", false);
                if (ok) {
                    KamiUtil.markActivated(input);
                    Notify.show(R.string.kami_activate_success);
                    showActivated();
                    // 自动进入首页
                    enterHome();
                } else {
                    setStatus(getString(R.string.kami_err_invalid), false);
                    status.setTextColor(getColor(R.color.red));
                }
            });
        });
    }

    private void onPurchase() {
        // 拉取远端 kami.txt，将第一张可用卡密作为"购买后获得的卡密"展示
        setStatus(getString(R.string.kami_purchase_loading), true);
        purchaseBtn.setEnabled(false);
        Task.execute(() -> {
            String text = KamiUtil.fetchKamiText();
            java.util.Set<String> set = KamiUtil.parseKamiList(text);
            String first = set.isEmpty() ? "" : set.iterator().next();
            runOnUiThread(() -> {
                setStatus("", false);
                purchaseBtn.setEnabled(true);
                showPurchaseDialog(first);
            });
        });
    }

    private void showPurchaseDialog(String kami) {
        String msg;
        if (TextUtils.isEmpty(kami)) {
            msg = getString(R.string.kami_purchase_fail);
        } else {
            msg = getString(R.string.kami_purchase_msg, kami);
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.kami_purchase)
                .setMessage(msg)
                .setPositiveButton(R.string.kami_copy_and_activate, (d, w) -> {
                    if (!TextUtils.isEmpty(kami) && kamiInput != null) {
                        kamiInput.setText(kami);
                    }
                })
                .setNegativeButton(R.string.dialog_negative, null)
                .show();
    }

    // ---------------- 已激活：进入 / 注销 ----------------

    private void showActivated() {
        kamiInputLayout.setVisibility(View.GONE);
        activateBtn.setVisibility(View.GONE);
        purchaseBtn.setVisibility(View.GONE);
        exitBtn.setVisibility(View.GONE);
        activatedPanel.setVisibility(View.VISIBLE);
        String kami = Setting.getKami();
        kamiShow.setText(mask(kami));
    }

    private void onLogout() {
        KamiUtil.clearActivation();
        Notify.show(R.string.kami_logout_done);
        showInput();
    }

    private void enterHome() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        }
        finishAffinity();
    }

    private void exitApp() {
        finishAffinity();
    }

    // ---------------- 工具 ----------------

    private void setStatus(String text, boolean showProgress) {
        if (status != null) {
            if (TextUtils.isEmpty(text)) {
                status.setVisibility(View.GONE);
            } else {
                status.setVisibility(View.VISIBLE);
                status.setText(text);
                status.setTextColor(resolveColorPrimary());
            }
        }
        if (progress != null) {
            progress.setVisibility(showProgress ? View.VISIBLE : View.GONE);
        }
    }

    private int resolveColorPrimary() {
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.colorPrimary, typedValue, true);
        return typedValue.data;
    }

    private String mask(String kami) {
        if (TextUtils.isEmpty(kami)) return "";
        if (kami.length() <= 8) return kami;
        return kami.substring(0, 4) + "****" + kami.substring(kami.length() - 4);
    }
}
