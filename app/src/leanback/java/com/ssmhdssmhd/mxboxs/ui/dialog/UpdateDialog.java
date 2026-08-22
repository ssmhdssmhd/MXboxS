package com.ssmhdssmhd.mxboxs.ui.dialog;

import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;

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

    /** v5.5.51：leanback 版使用 binding.confirm（自己的按钮），无缓存问题；但为了保持对称 & 避免 future 改动，加 pending 兜底 + 状态访问安全 */
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
        return builder().setView(getBinding().getRoot()).setCancelable(false);
    }

    @Override
    protected void initView() {
        binding.version.setText(title);
        binding.desc.setText(desc != null ? desc : "");
        binding.desc.setVisibility(desc != null ? View.VISIBLE : View.GONE);
        // 上部：授权激活码 - 回填 & 按钮 & 输入法 Done
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
        try {
            if (binding == null || binding.licenseCode == null) return;
            String code = binding.licenseCode.getText() == null ? "" : binding.licenseCode.getText().toString().trim();
            Setting.putKami(code);
            Setting.putKamiActivated(!TextUtils.isEmpty(code));
            refreshLicenseUi();
            Notify.show(TextUtils.isEmpty(code) ? "已清空激活码" : "激活码已保存");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void initEvent() {
        binding.confirm.setOnClickListener(this::onConfirm);
        binding.cancel.setOnClickListener(this::onCancel);
        // initEvent 发生在布局 inflate 之后，这时 binding.confirm 已经可用，应用 pending
        if (pendingConfirmEnabled != null) {
            binding.confirm.setEnabled(pendingConfirmEnabled);
            if (Boolean.TRUE.equals(pendingConfirmEnabled) && pendingConfirmTextRes != null) {
                binding.confirm.setText(pendingConfirmTextRes);
            }
            pendingConfirmEnabled = null;
            pendingConfirmTextRes = null;
        }
    }

    public void setStatus(String text) {
        if (binding != null) binding.status.setText(text);
    }

    public void updateTitle(String text) {
        this.title = text;
        if (binding != null) binding.version.setText(text);
    }

    public void updateDesc(String text) {
        this.desc = text;
        if (binding != null) {
            binding.desc.setText(text != null ? text : "");
            binding.desc.setVisibility(text != null ? View.VISIBLE : View.GONE);
        }
    }

    /** 兼容：旧代码调用 setDebugInfo 不再显示 debug，改为 fallback 填充「更新内容」（若还没设置）。 */
    public void setDebugInfo(String text) {
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

    /** 下部「更新内容」显示：空 = 隐藏 */
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
        if (binding != null && binding.confirm != null) {
            binding.confirm.setEnabled(false);
            binding.confirm.setText(R.string.update_downloading);
        } else {
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
        if (binding != null && binding.confirm != null) {
            binding.confirm.setEnabled(enabled);
            if (enabled) binding.confirm.setText(R.string.update_confirm);
        } else {
            pendingConfirmEnabled = enabled;
            pendingConfirmTextRes = R.string.update_confirm;
        }
    }

    public void setConfirmEnabled(boolean enabled, int textRes) {
        if (binding != null && binding.confirm != null) {
            binding.confirm.setEnabled(enabled);
            if (enabled) binding.confirm.setText(textRes);
        } else {
            pendingConfirmEnabled = enabled;
            pendingConfirmTextRes = textRes;
        }
    }

    private void onConfirm(View view) {
        listener.onConfirm(view);
    }

    private void onCancel(View view) {
        listener.onCancel(view);
    }
}
