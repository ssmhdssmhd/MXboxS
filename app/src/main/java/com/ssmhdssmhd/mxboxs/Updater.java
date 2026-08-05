package com.ssmhdssmhd.mxboxs;

import android.view.View;

import androidx.fragment.app.FragmentActivity;

import com.ssmhdssmhd.mxboxs.impl.UpdateListener;
import com.ssmhdssmhd.mxboxs.setting.Setting;
import com.ssmhdssmhd.mxboxs.ui.dialog.UpdateDialog;
import com.ssmhdssmhd.mxboxs.utils.Download;
import com.ssmhdssmhd.mxboxs.utils.FileUtil;
import com.ssmhdssmhd.mxboxs.utils.Github;
import com.ssmhdssmhd.mxboxs.utils.Notify;
import com.ssmhdssmhd.mxboxs.utils.ResUtil;
import com.ssmhdssmhd.mxboxs.utils.Task;
import com.github.catvod.utils.Path;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.io.File;

public class Updater implements Download.Callback, UpdateListener {

    private Download download;
    private UpdateDialog dialog;
    private JSONObject release;

    private Updater() {
    }

    public static Updater create() {
        return new Updater();
    }

    private File getFile() {
        return Path.cache("update.apk");
    }

    public Updater force() {
        Notify.show(R.string.update_check);
        Setting.putUpdate(true);
        return this;
    }

    public void start(FragmentActivity activity) {
        if (!Setting.getUpdate()) return;
        Task.execute(() -> doInBackground(activity));
    }

    public void showMirrorDialog(FragmentActivity activity) {
        String[] items = new String[]{
                ResUtil.getString(R.string.mirror_ghproxy),
                ResUtil.getString(R.string.mirror_mirror_ghproxy),
                ResUtil.getString(R.string.mirror_direct)
        };
        int checked = Setting.getMirrorMode();
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.setting_mirror)
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    Setting.putMirrorMode(which);
                    Notify.show(items[which]);
                    dialog.dismiss();
                })
                .show();
    }

    private void doInBackground(FragmentActivity activity) {
        try {
            release = Github.getLatestRelease();
            if (release == null) return;

            String tagName = release.optString("tag_name", "");
            String version = tagName.startsWith("v") ? tagName.substring(1) : tagName;
            String desc = release.optString("body", "");
            int code = parseVersionCode(version);

            if (code <= BuildConfig.VERSION_CODE) return;

            App.post(() -> show(activity, version, desc));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int parseVersionCode(String version) {
        try {
            String cleaned = version.replaceAll("[^0-9]", "");
            if (cleaned.isEmpty()) return 0;
            return Integer.parseInt(cleaned);
        } catch (Exception e) {
            return 0;
        }
    }

    private void show(FragmentActivity activity, String version, String desc) {
        dismiss();
        dialog = UpdateDialog.create()
                .title(ResUtil.getString(R.string.update_version, version))
                .desc(desc)
                .listener(this)
                .show(activity);
    }

    @Override
    public void onConfirm(View view) {
        view.setEnabled(false);
        String apkUrl = Github.findApkUrl(release);
        if (apkUrl == null) {
            Notify.show(R.string.update_check);
            dismiss();
            return;
        }
        download = Download.create(apkUrl, getFile());
        download.start(this);
    }

    @Override
    public void onCancel(View view) {
        Setting.putUpdate(false);
        if (download != null) download.cancel();
        dismiss();
    }

    private void dismiss() {
        try {
            if (dialog != null) dialog.dismiss();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void progress(int progress) {
        if (dialog != null) dialog.setProgress(progress);
    }

    @Override
    public void error(String msg) {
        Notify.show(msg);
        dismiss();
    }

    @Override
    public void success(File file) {
        FileUtil.openFile(file);
        dismiss();
    }
}