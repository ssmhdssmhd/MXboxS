package com.ssmhdssmhd.mxboxs;

import android.content.Context;
import android.content.Intent;
import android.view.View;

import androidx.fragment.app.FragmentActivity;

import com.ssmhdssmhd.mxboxs.event.DownloadProgressEvent;
import com.ssmhdssmhd.mxboxs.impl.UpdateListener;
import com.ssmhdssmhd.mxboxs.service.DownloadService;
import com.ssmhdssmhd.mxboxs.setting.Setting;
import com.ssmhdssmhd.mxboxs.ui.dialog.UpdateDialog;
import com.ssmhdssmhd.mxboxs.utils.Github;
import com.ssmhdssmhd.mxboxs.utils.Notify;
import com.ssmhdssmhd.mxboxs.utils.ResUtil;
import com.ssmhdssmhd.mxboxs.utils.Task;
import com.github.catvod.utils.Path;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Updater implements UpdateListener {

    private UpdateDialog dialog;
    private JSONObject release;
    private JSONObject apkAsset;
    private List<String> apkUrls;
    private boolean forced;
    private FragmentActivity activity;
    /** v5.7.11：是否已 startService（防止重复启动 DownloadService） */
    private boolean serviceStarted;
    /** v5.7.11：对话框 dismiss 时是否需要 unregister EventBus（onStart 时才注册） */
    private boolean eventBusRegistered;

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
        this.activity = activity;
        if (!Setting.getUpdate() && !forced) return;
        if (forced) {
            App.post(() -> showDialog(activity));
        }
        Task.execute(() -> doInBackground(activity));
    }

    public void showMirrorDialog(FragmentActivity activity) {
        java.util.ArrayList<String> labels = new java.util.ArrayList<>(Github.MIRROR_OPTIONS.keySet());
        String[] items = labels.toArray(new String[0]);
        int checked = Setting.getMirrorMode();
        if (checked < 0 || checked >= items.length) checked = Setting.MIRROR_DEFAULT_INDEX;
        final String[] finalItems = items;
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.setting_mirror)
                .setSingleChoiceItems(finalItems, checked, (dialog, which) -> {
                    Setting.putMirrorMode(which);
                    Notify.show(finalItems[which]);
                    dialog.dismiss();
                })
                .show();
    }

    private void ensureDialogShown(FragmentActivity activity) {
        if (dialog == null) showDialog(activity);
        // EventBus 注册（对话框首次显示后）
        if (!eventBusRegistered) {
            try {
                EventBus.getDefault().register(this);
                eventBusRegistered = true;
            } catch (Throwable ignored) {}
        }
    }

    private void showDialog(FragmentActivity activity) {
        dismiss();
        dialog = UpdateDialog.create()
                .title(ResUtil.getString(R.string.update_check))
                .desc(null)
                .listener(this)
                .show(activity);
        dialog.setStatus(ResUtil.getString(R.string.update_connecting));
        // 注册 EventBus（接收 DownloadService 进度事件）
        if (!eventBusRegistered) {
            try {
                EventBus.getDefault().register(this);
                eventBusRegistered = true;
            } catch (Throwable ignored) {}
        }
    }

    private void doInBackground(final FragmentActivity activity) {
        try {
            String releaseSource = "getHighestRelease(/releases?per_page=10)";
            release = Github.getHighestRelease();
            if (release == null) {
                release = Github.getLatestRelease();
                releaseSource = "getLatestRelease(/releases/latest)";
            }
            if (release == null) {
                final String errText = "没有拿到任何 Release 对象。\n可能原因：\n(1) 本机网络/代理被墙 api.github.com；\n(2) 还没 push main / 还没完成 CI build；\n(3) 镜像前缀解析不到 GitHub API。";
                App.post(() -> {
                    ensureDialogShown(activity);
                    if (dialog != null) {
                        dialog.setStatus(ResUtil.getString(R.string.update_download_failed, "network error"));
                        dialog.setChangelog(errText);
                        dialog.setConfirmEnabled(false);
                    }
                    Notify.show("更新检测：未连上 GitHub API");
                });
                return;
            }

            String tagName = release.optString("tag_name", "");
            String rawDesc = release.optString("body", "");
            android.util.Pair<String, String> assetVer = Github.extractVersionFromAssetsWithDebug(release);
            String rawVersion = "";
            String debugApkName = "";
            if (assetVer != null && assetVer.first != null && !assetVer.first.isEmpty()) {
                rawVersion = assetVer.first;
                debugApkName = assetVer.second == null ? "" : assetVer.second;
            } else {
                String fromTag = tagName.startsWith("v") ? tagName.substring(1) : tagName;
                if (fromTag != null && !fromTag.isEmpty()) rawVersion = fromTag;
                if (assetVer != null) debugApkName = assetVer.second == null ? "" : assetVer.second;
            }
            final String version = rawVersion;
            // v5.7.11：release.body 清洗（去 markdown 符号 / Full Changelog URL 等）
            final String desc = Github.cleanReleaseBody(rawDesc);

            int cmp = Github.compareVersion(version, BuildConfig.VERSION_NAME);
            if (cmp <= 0) {
                final String changelog = desc == null ? "" : desc;
                App.post(() -> {
                    ensureDialogShown(activity);
                    if (dialog != null) {
                        dialog.setStatus(ResUtil.getString(R.string.update_no_new));
                        dialog.setChangelog(changelog);
                        dialog.setConfirmEnabled(false);
                    }
                });
                return;
            }

            apkUrls = Github.findApkUrls(release);
            try { apkAsset = Github.pickDirectApkAsset(release); } catch (Throwable ignored) { apkAsset = null; }

            App.post(() -> {
                ensureDialogShown(activity);
                if (dialog != null) {
                    dialog.setStatus(ResUtil.getString(R.string.update_connected, version));
                    dialog.updateTitle(ResUtil.getString(R.string.update_version, version));
                    dialog.updateDesc(desc.isEmpty() ? ResUtil.getString(R.string.update_downloading) : desc);
                    dialog.setChangelog(desc == null ? "" : desc);
                    // 自动开始下载 → 启动 DownloadService（前台 Service，进程被系统回收也不丢进度）
                    startDownload();
                }
            });
        } catch (final Exception e) {
            e.printStackTrace();
            final String msg = "更新检测异常：" + e.getClass().getSimpleName() + " " + e.getMessage();
            App.post(() -> {
                ensureDialogShown(activity);
                if (dialog != null) {
                    dialog.setStatus(ResUtil.getString(R.string.update_download_failed, e.getMessage()));
                    dialog.setChangelog(msg);
                    dialog.setConfirmEnabled(false);
                }
                Notify.show(msg);
            });
        }
    }

    /** v5.7.11：启动 DownloadService 前台 Service 来跑下载，替换原来 Updater 自己 Download.start()。
     *  Service 会：probe 镜像 / 断点续传 / 多镜像 fallback / verifyApkIntegrity / 完成后 install，
     *  全部在后台线程完成，进程被系统回收也不丢进度（startForeground 常驻）。 */
    private synchronized void startDownload() {
        if (serviceStarted) return;
        if (apkUrls == null || apkUrls.isEmpty()) {
            if (dialog != null) {
                dialog.setStatus(ResUtil.getString(R.string.update_download_failed, "APK not found"));
                dialog.setConfirmEnabled(true, R.string.update_retry);
            }
            return;
        }
        // 更新对话框显示状态（真正的下载进度由 EventBus 从 Service 推过来）
        if (dialog != null) {
            dialog.showProgress();
            dialog.setProgress(0, 0, -1);
            dialog.setStatus("正在准备下载…");
        }

        Context ctx = App.get();
        Intent intent = new Intent(ctx, DownloadService.class);
        intent.setAction(DownloadService.ACTION_START);
        intent.putStringArrayListExtra(DownloadService.EXTRA_APK_URLS, new ArrayList<>(apkUrls));
        intent.putExtra(DownloadService.EXTRA_APK_FILE, getFile().getAbsolutePath());
        intent.putExtra(DownloadService.EXTRA_VERSION, BuildConfig.VERSION_NAME);
        if (apkAsset != null) {
            intent.putExtra(DownloadService.EXTRA_APK_ASSET_SIZE, apkAsset.optLong("size", -1L));
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent);
            } else {
                ctx.startService(intent);
            }
            serviceStarted = true;
        } catch (Throwable e) {
            // startForegroundService 需要在几秒内 startForeground，否则崩。
            // DownloadService 自己的 onStartCommand 会第一时间 startForeground——这里失败说明 Service 本身起不来。
            if (dialog != null) {
                dialog.setStatus(ResUtil.getString(R.string.update_download_failed, "无法启动下载服务"));
                dialog.setConfirmEnabled(true, R.string.update_retry);
            }
        }
    }

    // ========== EventBus：接收 DownloadService 进度 / 错误 / 完成事件 ==========

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDownloadProgress(DownloadProgressEvent ev) {
        if (dialog == null) return;
        switch (ev.state) {
            case DownloadProgressEvent.STATE_PROBING:
                dialog.showProgress();
                dialog.setStatus(ev.message != null ? ev.message : "正在扫描镜像…");
                break;
            case DownloadProgressEvent.STATE_PROGRESS:
                if (ev.message != null) dialog.setStatus(ev.message);
                dialog.showProgress();
                dialog.setProgress(ev.progress, ev.downloadedBytes, ev.totalBytes);
                break;
            case DownloadProgressEvent.STATE_SUCCESS:
                dialog.setStatus(ResUtil.getString(R.string.update_installing));
                dialog.showProgress();
                dialog.setProgress(100, 0, 0);
                dialog.setConfirmEnabled(false);
                dismiss();
                break;
            case DownloadProgressEvent.STATE_ERROR:
                dialog.setStatus(ResUtil.getString(R.string.update_download_failed, ev.message));
                dialog.setConfirmEnabled(true, R.string.update_retry);
                // 错误也写进 changelog 里让用户能看见（不会像旧版那样一长串 probe 日志污染）
                dialog.setChangelog(ev.message);
                break;
            case DownloadProgressEvent.STATE_CANCELLED:
                dismiss();
                break;
        }
    }

    // ========== UpdateListener（对话框按钮回调） ==========

    @Override
    public void onConfirm(View view) {
        // 手动点"更新/重试"：重置 serviceStarted，然后重新 startDownload()
        serviceStarted = false;
        startDownload();
    }

    @Override
    public void onCancel(View view) {
        Setting.putUpdate(false);
        stopServiceSafe();
        dismiss();
    }

    private void stopServiceSafe() {
        serviceStarted = false;
        try {
            Context ctx = App.get();
            Intent i = new Intent(ctx, DownloadService.class);
            i.setAction(DownloadService.ACTION_CANCEL);
            ctx.startService(i);
        } catch (Throwable ignored) {}
    }

    private void dismiss() {
        try {
            if (eventBusRegistered) {
                EventBus.getDefault().unregister(this);
                eventBusRegistered = false;
            }
        } catch (Throwable ignored) {}
        try {
            if (dialog != null) dialog.dismiss();
        } catch (Exception ignored) {}
        dialog = null;
        // 注意：不 stopService！dismiss 对话框不代表用户想取消下载——
        // 前台 Service 继续跑，用户切后台、杀掉 Activity 也不丢进度。
        // 只有 onCancel (用户点取消) 才 stopServiceSafe。
    }
}
