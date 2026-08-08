package com.ssmhdssmhd.mxboxs.ui.dialog;

import android.text.TextUtils;
import android.view.View;

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
        return builder().setView(getBinding().getRoot()).setCancelable(false);
    }

    @Override
    protected void initView() {
        binding.version.setText(title);
        binding.desc.setText(desc != null ? desc : "");
        binding.desc.setVisibility(desc != null ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void initEvent() {
        binding.confirm.setOnClickListener(this::onConfirm);
        binding.cancel.setOnClickListener(this::onCancel);
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
            CharSequence debug = binding.debug != null ? binding.debug.getText() : "";
            binding.desc.setText(text != null ? text : "");
            binding.desc.setVisibility(text != null ? View.VISIBLE : View.GONE);
            if (binding.debug != null && !TextUtils.isEmpty(debug) && TextUtils.isEmpty(binding.debug.getText())) {
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
        binding.confirm.setEnabled(false);
        binding.confirm.setText(R.string.update_downloading);
    }

    public void setProgress(int progress) {
        if (binding != null) {
            binding.progressBar.setProgress(progress);
            binding.progressText.setText(String.format(Locale.getDefault(), "%1$d%%", progress));
        }
    }

    public void setConfirmEnabled(boolean enabled) {
        binding.confirm.setEnabled(enabled);
        if (enabled) binding.confirm.setText(R.string.update_confirm);
    }

    public void setConfirmEnabled(boolean enabled, int textRes) {
        binding.confirm.setEnabled(enabled);
        if (enabled) binding.confirm.setText(textRes);
    }

    private void onConfirm(View view) {
        listener.onConfirm(view);
    }

    private void onCancel(View view) {
        listener.onCancel(view);
    }
}
