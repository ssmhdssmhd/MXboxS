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
import java.util.List;

public class Updater implements Download.Callback, UpdateListener {

    private Download download;
    private UpdateDialog dialog;
    private JSONObject release;
    private List<String> apkUrls;
    private int apkCursor;
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
        if (checked < 0 || checked >= items.length) checked = 0;
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
            // 优先策略：
            // 1) 用 getHighestRelease() 遍历 /releases?per_page=10，从 APK 文件名里提取版本号取最高的那个。
            //    这解决了 GitHub /releases/latest 只返回被官方设为 "Latest" 标记的 Release，
            //    而我们 push main 自动构建的 MXboxS-latest 是 prerelease，/latest 永远不会返回它的问题。
            // 2) 若失败则回退 getLatestRelease()（保留原语义）
            release = Github.getHighestRelease();
            if (release == null) release = Github.getLatestRelease();
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
            String rawDesc = release.optString("body", "");
            // 优先从 APK asset 文件名提取版本号（兼容 MXboxS-latest 自动预发布 tag）
            // 否则从 tag_name 提取（v5.5.36 这种稳定发布 tag）
            String rawVersion = Github.extractVersionFromAssets(release);
            if (rawVersion.isEmpty()) {
                rawVersion = tagName.startsWith("v") ? tagName.substring(1) : tagName;
            }
            final String version = rawVersion;
            final String desc = rawDesc;

            if (Github.compareVersion(version, BuildConfig.VERSION_NAME) <= 0) {
                // 已是最新版本
                App.post(new Runnable() {
                    @Override
                    public void run() {
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
                    }
                });
                return;
            }

            // 找到 APK 下载链接（多镜像候选列表，下载失败自动 fallback）
            apkUrls = Github.findApkUrls(release);
            apkCursor = 0;

            // 连接成功，有新版本
            App.post(new Runnable() {
                @Override
                public void run() {
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
        if (apkUrls == null || apkUrls.isEmpty() || apkCursor >= apkUrls.size()) {
            if (dialog != null) {
                dialog.setStatus(ResUtil.getString(R.string.update_download_failed, "APK not found"));
                dialog.setConfirmEnabled(true);
            }
            return;
        }
        String url = apkUrls.get(apkCursor);
        // 进度条状态提示：当前正在下载的镜像名（如果不是直连 github.com 的话），避免"0% 卡死时不知道正在连哪个"
        String mirrorTag;
        if (url == null) mirrorTag = "APK";
        else if (url.startsWith(Github.MIRROR_GHPROXY + "/")) mirrorTag = "ghproxy.com";
        else if (url.startsWith(Github.MIRROR_MIRROR_GHPROXY + "/")) mirrorTag = "mirror.ghproxy.com";
        else if (url.startsWith(Github.MIRROR_GHPS_CAMBRIDGECS + "/")) mirrorTag = "ghps.cambridgecs.co";
        else if (url.startsWith(Github.MIRROR_GH_API_99988866 + "/")) mirrorTag = "gh.api.99988866.xyz";
        else mirrorTag = "GitHub";
        if (apkCursor > 0 && dialog != null) {
            dialog.setStatus("镜像 " + (apkCursor) + "/" + apkUrls.size() + "：" + mirrorTag + " 下载中…（前一镜像失败）");
        } else if (dialog != null) {
            dialog.setStatus("下载中（" + mirrorTag + "）…");
        }
        if (dialog != null) {
            dialog.showProgress();
            dialog.setProgress(0);
        }
        if (download != null) download.cancel();
        download = Download.create(url, getFile());
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
        apkCursor = 0;
        startDownload();
        view.setEnabled(true);
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
        // 下载失败自动切换下一个镜像（如果还有候选）
        if (apkUrls != null && apkCursor + 1 < apkUrls.size()) {
            apkCursor++;
            // 下一轮 startDownload 会从 apkUrls[apkCursor] 继续
            App.post(this::startDownload);
            return;
        }
        // 所有镜像都失败：才真正显示错误
        if (dialog != null) {
            dialog.setStatus(ResUtil.getString(R.string.update_download_failed, msg));
            dialog.setConfirmEnabled(true);
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
