package com.ssmhdssmhd.mxboxs.ui.dialog;

import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.ssmhdssmhd.mxboxs.R;
import com.ssmhdssmhd.mxboxs.databinding.DialogUpdateBinding;
import com.ssmhdssmhd.mxboxs.impl.UpdateListener;
import com.ssmhdssmhd.mxboxs.setting.Setting;
import com.ssmhdssmhd.mxboxs.utils.Notify;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;

public class UpdateDialog extends BaseAlertDialog {

    private DialogUpdateBinding binding;
    private UpdateListener listener;
    private String title;
    private String desc;

    /** v5.5.51 新增：缓存 PositiveButton 引用，避免 dialog=null 时 setConfirmEnabled 直接 return 造成「正在下载…」一直置灰 */
    private android.widget.Button cachedPositive;

    /** 待应用的按钮状态（如果按钮还没准备好），onStart 会一并应用 */
    private Boolean pendingConfirmEnabled;
    private Integer pendingConfirmTextRes;

    public static UpdateDialog create() {
        return new UpdateDialog();
    }

    public UpdateDialog title(String title) {
        this.title = title;
        return this;
    }

    public UpdateDialog desc(String desc) {
        this.desc = desc;
        return this;
    }

    public UpdateDialog listener(UpdateListener listener) {
        this.listener = listener;
        return this;
    }

    public UpdateDialog show(FragmentActivity activity) {
        show(activity.getSupportFragmentManager(), null);
        return this;
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogUpdateBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setTitle(title).setView(getBinding().getRoot()).setPositiveButton(R.string.update_confirm, null).setNegativeButton(R.string.dialog_negative, null).setCancelable(false);
    }

    @Override
    protected void initView() {
        binding.desc.setText(desc != null ? desc : "");
        binding.desc.setVisibility(desc != null ? View.VISIBLE : View.GONE);
        // 授权激活码：读取已保存的显示；绑定保存点击；输入法 Done = 保存
        refreshLicenseUi();
        if (binding.licenseSave != null) {
            binding.licenseSave.setOnClickListener(v -> saveLicenseCode());
        }
        if (binding.licenseCode != null) {
            binding.licenseCode.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    saveLicenseCode();
                    return true;
                }
                return false;
            });
        }
    }

    private void refreshLicenseUi() {
        if (binding == null) return;
        String saved = Setting.getKami();
        boolean activated = Setting.isKamiActivated();
        if (binding.licenseCode != null && !TextUtils.isEmpty(saved)) {
            // 只在输入框当前为空时回填，避免覆盖用户正在输入的内容
            CharSequence cur = binding.licenseCode.getText();
            if (cur == null || TextUtils.isEmpty(cur.toString().trim())) {
                binding.licenseCode.setText(saved);
                binding.licenseCode.setSelection(saved.length());
            }
        }
        if (binding.licenseStatus != null) {
            if (activated && !TextUtils.isEmpty(saved)) {
                binding.licenseStatus.setText("激活状态：已激活");
            } else if (!TextUtils.isEmpty(saved)) {
                binding.licenseStatus.setText("激活状态：已保存（未核验）");
            } else {
                binding.licenseStatus.setText("激活状态：未激活");
            }
        }
    }

    private void saveLicenseCode() {
        if (binding == null || binding.licenseCode == null) return;
        String code = binding.licenseCode.getText() == null ? "" : binding.licenseCode.getText().toString().trim();
        Setting.putKami(code);
        // 简单标记：非空即视为已激活（后续若接入在线核验，可在此替换为在线校验逻辑）
        Setting.putKamiActivated(!TextUtils.isEmpty(code));
        refreshLicenseUi();
        Notify.show(TextUtils.isEmpty(code) ? "已清空激活码" : "激活码已保存");
    }

    @Override
    public void onStart() {
        super.onStart();
        AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog != null) {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (positive != null) {
                cachedPositive = positive;
                positive.setOnClickListener(view -> listener.onConfirm(view));
                // 把挂起的 pending 状态应用到新创建的按钮（error/setConfirmEnabled 先于 onStart 调用时会用）
                if (pendingConfirmEnabled != null) {
                    positive.setEnabled(pendingConfirmEnabled);
                    if (Boolean.TRUE.equals(pendingConfirmEnabled) && pendingConfirmTextRes != null) {
                        positive.setText(pendingConfirmTextRes);
                    }
                    pendingConfirmEnabled = null;
                    pendingConfirmTextRes = null;
                }
            }
            if (negative != null) negative.setOnClickListener(view -> listener.onCancel(view));
        }
    }

    public void setStatus(String text) {
        if (binding != null) binding.status.setText(text);
    }

    public void updateTitle(String text) {
        this.title = text;
        AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog != null) dialog.setTitle(text);
    }

    public void updateDesc(String text) {
        this.desc = text;
        if (binding != null) {
            binding.desc.setText(text != null ? text : "");
            binding.desc.setVisibility(text != null ? View.VISIBLE : View.GONE);
        }
    }

    /** 兼容接口：老代码用 setDebugInfo(...) 塞 debug 信息。改造后不再展示 debug，
     *  改为把传入文本作为「更新内容」的来源之一（若还没设置 changelog 才显示它，
     *  避免覆盖 release.body 等更正式的更新内容）。 */
    public void setDebugInfo(String text) {
        // 不再显示 debug 面板；仅作为 fallback 更新 changelog
        if (text == null || text.isEmpty()) return;
        if (binding == null || binding.changelogText == null) return;
        CharSequence cur = binding.changelogText.getText();
        if (cur == null || TextUtils.isEmpty(cur.toString().trim())) {
            setChangelog(text);
        }
    }

    public CharSequence readDebugInfo() {
        if (binding == null || binding.changelogText == null) return "";
        CharSequence t = binding.changelogText.getText();
        return t == null ? "" : t;
    }

    /** 设置下部「更新内容」。传入 null/空 = 隐藏该区域。 */
    public void setChangelog(String text) {
        if (binding == null || binding.changelogPanel == null || binding.changelogText == null) return;
        if (text == null || text.isEmpty()) {
            binding.changelogPanel.setVisibility(View.GONE);
            binding.changelogText.setText("");
        } else {
            binding.changelogText.setText(text);
            binding.changelogPanel.setVisibility(View.VISIBLE);
        }
    }

    public void showProgress() {
        if (binding != null) {
            binding.progressBar.setVisibility(View.VISIBLE);
            binding.progressText.setVisibility(View.VISIBLE);
            binding.progressBar.setProgress(0);
            binding.progressText.setText("0%");
        }
        // 直接用 cachedPositive，或尝试再取一次，避免 dialog==null 时跳过
        Button btn = cachedPositive;
        if (btn == null) {
            AlertDialog dialog = (AlertDialog) getDialog();
            if (dialog != null) btn = cachedPositive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        }
        if (btn != null) {
            btn.setEnabled(false);
            btn.setText(R.string.update_downloading);
        } else {
            // 按钮尚未创建（onStart 未触发），挂到 pending 里等 onStart 应用
            pendingConfirmEnabled = false;
            pendingConfirmTextRes = R.string.update_downloading;
        }
    }

    public void setProgress(int progress) {
        if (binding != null) {
            binding.progressBar.setProgress(progress);
            binding.progressText.setText(String.format(Locale.getDefault(), "%1$d%%", progress));
        }
    }

    /** 带字节数的进度显示：已知总大小时显示「已下载 X.X MB / 总 Y.Y MB」，未知时显示「已下载 X.X MB」。 */
    public void setProgress(int progress, long downloadedBytes, long totalBytes) {
        if (binding == null) return;
        StringBuilder sb = new StringBuilder();
        if (progress >= 0) sb.append(progress).append("%");
        if (downloadedBytes > 0) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(com.ssmhdssmhd.mxboxs.utils.Download.formatBytes(downloadedBytes));
            if (totalBytes > 0 && totalBytes >= downloadedBytes) {
                sb.append(" / ").append(com.ssmhdssmhd.mxboxs.utils.Download.formatBytes(totalBytes));
            }
        }
        binding.progressBar.setProgress(Math.max(0, Math.min(100, progress)));
        binding.progressText.setText(sb.toString());
    }

    public void setConfirmEnabled(boolean enabled) {
        setConfirmEnabled(enabled, R.string.update_confirm);
    }

    public void setConfirmEnabled(boolean enabled, int textRes) {
        Button btn = cachedPositive;
        if (btn == null) {
            AlertDialog dialog = (AlertDialog) getDialog();
            if (dialog != null) btn = cachedPositive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        }
        if (btn != null) {
            btn.setEnabled(enabled);
            if (enabled) btn.setText(textRes);
        } else {
            // 按钮还没创建出来，缓存 pending，onStart 时再应用
            pendingConfirmEnabled = enabled;
            pendingConfirmTextRes = textRes;
        }
    }
}
