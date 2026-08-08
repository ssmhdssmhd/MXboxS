package com.ssmhdssmhd.mxboxs.ui.dialog;

import android.text.TextUtils;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.ssmhdssmhd.mxboxs.R;
import com.ssmhdssmhd.mxboxs.databinding.DialogUpdateBinding;
import com.ssmhdssmhd.mxboxs.impl.UpdateListener;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;

public class UpdateDialog extends BaseAlertDialog {

    private DialogUpdateBinding binding;
    private UpdateListener listener;
    private String title;
    private String desc;

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
    }

    @Override
    public void onStart() {
        super.onStart();
        AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog != null) dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(view -> listener.onCancel(view));
        if (dialog != null) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> listener.onConfirm(view));
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
            CharSequence debug = binding.debug != null ? binding.debug.getText() : "";
            binding.desc.setText(text != null ? text : "");
            binding.desc.setVisibility(text != null ? View.VISIBLE : View.GONE);
            // 如果更新时清掉了 debug 字段，保留它：防止 updateDesc 覆盖后 debug 信息丢失
            if (binding.debug != null && TextUtils.isEmpty(debug) == false && TextUtils.isEmpty(binding.debug.getText())) {
                binding.debug.setText(debug);
                binding.debug.setVisibility(View.VISIBLE);
            }
        }
    }

    public void setDebugInfo(String text) {
        if (binding == null || binding.debug == null) return;
        if (text == null || text.isEmpty()) {
            binding.debug.setVisibility(View.GONE);
            binding.debug.setText("");
        } else {
            binding.debug.setText(text);
            binding.debug.setVisibility(View.VISIBLE);
        }
    }

    public CharSequence readDebugInfo() {
        if (binding == null || binding.debug == null) return "";
        CharSequence t = binding.debug.getText();
        return t == null ? "" : t;
    }

    public void showProgress() {
        if (binding != null) {
            binding.progressBar.setVisibility(View.VISIBLE);
            binding.progressText.setVisibility(View.VISIBLE);
            binding.progressBar.setProgress(0);
            binding.progressText.setText("0%");
        }
        AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog != null) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(R.string.update_downloading);
        }
    }

    public void setProgress(int progress) {
        if (binding != null) {
            binding.progressBar.setProgress(progress);
            binding.progressText.setText(String.format(Locale.getDefault(), "%1$d%%", progress));
        }
    }

    public void setConfirmEnabled(boolean enabled) {
        setConfirmEnabled(enabled, R.string.update_confirm);
    }

    public void setConfirmEnabled(boolean enabled, int textRes) {
        AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog == null) return;
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(enabled);
        if (enabled) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(textRes);
        }
    }
}
