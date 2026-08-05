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
    private String apkUrl;
    private boolean forced;

    private Updater() {
    }

    public static Updater create() {
        return new Updater();
    }

    private File getFile() {
        return Path.cache("update.apk");
    }

    public Updater force() {
        forced = true;
        Notify.show(R.string.update_check);
        Setting.putUpdate(true);
        return this;
    }

    public void start(FragmentActivity activity) {
        if (!Setting.getUpdate() && !forced) return;
        if (forced) {
            // 手动检查：立即弹出对话框，显示"正在连接仓库"
            App.post(() -> showDialog(activity));
        }
        // 后台线程连接仓库获取版本信息
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

    private void showDialog(FragmentActivity activity) {
        dismiss();
        dialog = UpdateDialog.create()
                .title(ResUtil.getString(R.string.update_check))
                .desc(null)
                .listener(this)
                .show(activity);
        dialog.setStatus(ResUtil.getString(R.string.update_connecting));
    }

    private void doInBackground(FragmentActivity activity) {
        try {
            release = Github.getLatestRelease();
            if (release == null) {
                // 连接失败
                App.post(() -> {
                    if (dialog != null) {
                        dialog.setStatus(ResUtil.getString(R.string.update_download_failed, "network error"));
                        dialog.setConfirmEnabled(false);
                    } else if (forced) {
                        showDialog(activity);
                        if (dialog != null) {
                            dialog.setStatus(ResUtil.getString(R.string.update_download_failed, "network error"));
                            dialog.setConfirmEnabled(false);
                        }
                    }
                });
                return;
            }

            String tagName = release.optString("tag_name", "");
            String version = tagName.startsWith("v") ? tagName.substring(1) : tagName;
            String desc = release.optString("body", "");

            if (compareVersionNames(version, BuildConfig.VERSION_NAME) <= 0) {
                // 已是最新版本
                App.post(() -> {
                    if (dialog != null) {
                        dialog.setStatus(ResUtil.getString(R.string.update_no_new));
                        dialog.setConfirmEnabled(false);
                    } else if (forced) {
                        showDialog(activity);
                        if (dialog != null) {
                            dialog.setStatus(ResUtil.getString(R.string.update_no_new));
                            dialog.setConfirmEnabled(false);
                        }
                    }
                });
                return;
            }

            // 找到 APK 下载链接
            apkUrl = Github.findApkUrl(release);

            // 连接成功，有新版本
            App.post(() -> {
                if (dialog == null) {
                    // 非强制模式（自动检查）首次弹出对话框
                    showDialog(activity);
                }
                if (dialog != null) {
                    dialog.setStatus(ResUtil.getString(R.string.update_connected, version));
                    dialog.updateTitle(ResUtil.getString(R.string.update_version, version));
                    dialog.updateDesc(desc.isEmpty() ? ResUtil.getString(R.string.update_downloading) : desc);
                    // 自动开始下载
                    startDownload();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            App.post(() -> {
                if (dialog != null) {
                    dialog.setStatus(ResUtil.getString(R.string.update_download_failed, e.getMessage()));
                    dialog.setConfirmEnabled(false);
                } else if (forced) {
                    showDialog(activity);
                    if (dialog != null) {
                        dialog.setStatus(ResUtil.getString(R.string.update_download_failed, e.getMessage()));
                        dialog.setConfirmEnabled(false);
                    }
                }
            });
        }
    }

    private void startDownload() {
        if (apkUrl == null) {
            if (dialog != null) {
                dialog.setStatus(ResUtil.getString(R.string.update_download_failed, "APK not found"));
            }
            return;
        }
        if (dialog != null) {
            dialog.showProgress();
        }
        download = Download.create(apkUrl, getFile());
        download.start(this);
    }

    /**
     * 按点分段比较两个版本号（数字比较，非字典序）。
     *
     * @return 正数表示 server > local（有更新），0 表示相等，负数表示 server < local
     */
    private int compareVersionNames(String server, String local) {
        if (server == null) server = "";
        if (local == null) local = "";
        String[] sParts = server.split("\\.");
        String[] lParts = local.split("\\.");
        int max = Math.max(sParts.length, lParts.length);
        for (int i = 0; i < max; i++) {
            int s = parseIntOrZero(i < sParts.length ? sParts[i] : "0");
            int l = parseIntOrZero(i < lParts.length ? lParts[i] : "0");
            if (s != l) return s - l;
        }
        return 0;
    }

    private int parseIntOrZero(String s) {
        try {
            String cleaned = s.replaceAll("[^0-9]", "");
            return cleaned.isEmpty() ? 0 : Integer.parseInt(cleaned);
        } catch (Exception e) {
            return 0;
        }
    }

    @Deprecated
    private int parseVersionCode(String version) {
        try {
            String cleaned = version.replaceAll("[^0-9]", "");
            if (cleaned.isEmpty()) return 0;
            return Integer.parseInt(cleaned);
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public void onConfirm(View view) {
        view.setEnabled(false);
        startDownload();
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
        if (dialog != null) {
            dialog.setStatus(ResUtil.getString(R.string.update_download_failed, msg));
        }
        Notify.show(msg);
    }

    @Override
    public void success(File file) {
        if (dialog != null) {
            dialog.setStatus(ResUtil.getString(R.string.update_installing));
        }
        FileUtil.installApk(file);
        dismiss();
    }
}
