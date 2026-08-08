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

    /**
     * 生成版本检测 Debug 信息（用于对话框底部「本地/远程/比较」三行），
     * 帮助用户定位「为什么显示已是最新」「为什么提示 5.5.46 而不是 5.5.47」等问题。
     */
    private static String buildDebugInfo(String tagName, String remoteVersion, String versionSource,
                                         String debugApkName, String debugSource,
                                         String compareResult) {
        StringBuilder sb = new StringBuilder();
        sb.append("本地：").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(")").append("\n");
        if (remoteVersion == null || remoteVersion.isEmpty()) {
            sb.append("远程：<未取到>").append("\n");
        } else {
            sb.append("远程：").append(remoteVersion);
            if (versionSource != null && !versionSource.isEmpty()) sb.append(" (来源：").append(versionSource).append(")");
            sb.append("\n");
        }
        if (tagName != null && !tagName.isEmpty()) {
            sb.append("Release tag：").append(tagName).append("\n");
        }
        if (debugApkName != null && !debugApkName.isEmpty()) {
            sb.append("匹配 APK：").append(debugApkName).append("\n");
        }
        if (debugSource != null && !debugSource.isEmpty()) {
            sb.append("Release来源：").append(debugSource).append("\n");
        }
        if (compareResult != null && !compareResult.isEmpty()) {
            sb.append("比较：").append(compareResult);
        }
        return sb.toString().trim();
    }

    private void ensureDialogShown(FragmentActivity activity) {
        if (dialog == null) showDialog(activity);
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

    private void doInBackground(final FragmentActivity activity) {
        try {
            // 优先策略：
            // 1) 用 getHighestRelease() 遍历 /releases?per_page=10，从 APK 文件名里提取版本号取最高的那个。
            //    这解决了 GitHub /releases/latest 只返回被官方设为 "Latest" 标记的 Release，
            //    而我们 push main 自动构建的 MXboxS-latest 是 prerelease，/latest 永远不会返回它的问题。
            // 2) 若失败则回退 getLatestRelease()（保留原语义）
            String releaseSource = "getHighestRelease(/releases?per_page=10)";
            release = Github.getHighestRelease();
            if (release == null) {
                release = Github.getLatestRelease();
                releaseSource = "getLatestRelease(/releases/latest)";
            }
            if (release == null) {
                // 连接失败：强制模式（手动检查）也要 showDialog，否则用户点了「检测更新」看不到对话框
                releaseSource = "network error（getHighestRelease 和 getLatestRelease 都返回 null）";
                final String debugSource = releaseSource;
                App.post(() -> {
                    ensureDialogShown(activity);
                    if (dialog != null) {
                        dialog.setStatus(ResUtil.getString(R.string.update_download_failed, "network error"));
                        dialog.setDebugInfo(buildDebugInfo("", "", "", "", debugSource,
                                "没有拿到任何 Release 对象 → 视为「已是最新」。\n可能原因：(1) 本机网络/代理被墙 api.github.com；(2) 还没 push main / 还没完成 CI build；(3) 镜像前缀解析不到 GitHub API。"));
                        dialog.setConfirmEnabled(false);
                    }
                    Notify.show("更新检测：未连上 GitHub API（Release来源：" + debugSource + "）");
                });
                return;
            }

            String tagName = release.optString("tag_name", "");
            String rawDesc = release.optString("body", "");
            // 优先从 APK asset 文件名提取版本号（兼容 MXboxS-latest 自动预发布 tag）
            // 否则从 tag_name 提取（v5.5.36 这种稳定发布 tag）
            android.util.Pair<String, String> assetVer = Github.extractVersionFromAssetsWithDebug(release);
            String versionSource = "";
            String debugApkName = "";
            String rawVersion = "";
            if (assetVer != null && assetVer.first != null && !assetVer.first.isEmpty()) {
                rawVersion = assetVer.first;
                versionSource = "APK 文件名";
                debugApkName = assetVer.second == null ? "" : assetVer.second;
            } else {
                String fromTag = tagName.startsWith("v") ? tagName.substring(1) : tagName;
                if (fromTag != null && !fromTag.isEmpty()) {
                    rawVersion = fromTag;
                    versionSource = "tag_name";
                }
                if (assetVer != null) debugApkName = assetVer.second == null ? "" : assetVer.second;
            }
            final String version = rawVersion;
            final String desc = rawDesc;
            final String fTagName = tagName;
            final String fVersionSource = versionSource;
            final String fApkName = debugApkName;
            final String fReleaseSource = releaseSource;

            int cmp = Github.compareVersion(version, BuildConfig.VERSION_NAME);
            if (cmp <= 0) {
                // 已是最新版本：强制模式必须 showDialog，否则用户看不到对话框，也无法查看 Debug 信息
                final String compareLine = "server=" + (version.isEmpty() ? "<empty>" : version)
                        + ", local=" + BuildConfig.VERSION_NAME
                        + ", compareVersion 返回 " + cmp + "（<=0 → 判定已是最新）。\n"
                        + "要升级到 5.5.47+，请先把本地 3 个新 commit push 到 origin/main 触发 CI 产出 v5.5.47 APK asset，这里才能检测到。";
                App.post(new Runnable() {
                    @Override
                    public void run() {
                        ensureDialogShown(activity);
                        if (dialog != null) {
                            dialog.setStatus(ResUtil.getString(R.string.update_no_new));
                            dialog.setDebugInfo(buildDebugInfo(fTagName, version, fVersionSource,
                                    fApkName, fReleaseSource, compareLine));
                            dialog.setConfirmEnabled(false);
                        }
                    }
                });
                return;
            }

            // 找到 APK 下载链接（多镜像候选列表，下载失败自动 fallback）
            apkUrls = Github.findApkUrls(release);
            apkCursor = 0;

            // 连接成功，有新版本
            final String compareLine = "server=" + version + " > local=" + BuildConfig.VERSION_NAME
                    + "（compareVersion=" + cmp + "）→ 有新版本";
            App.post(new Runnable() {
                @Override
                public void run() {
                    ensureDialogShown(activity);
                    if (dialog != null) {
                        dialog.setDebugInfo(buildDebugInfo(fTagName, version, fVersionSource,
                                fApkName, fReleaseSource, compareLine
                                        + "\n候选 APK 镜像：" + (apkUrls == null ? 0 : apkUrls.size()) + " 条"));
                        dialog.setStatus(ResUtil.getString(R.string.update_connected, version));
                        dialog.updateTitle(ResUtil.getString(R.string.update_version, version));
                        dialog.updateDesc(desc.isEmpty() ? ResUtil.getString(R.string.update_downloading) : desc);
                        // 自动开始下载
                        startDownload();
                    }
                }
            });
        } catch (final Exception e) {
            e.printStackTrace();
            final String debugSource = "Exception: " + (e.getClass().getSimpleName()) + " " + e.getMessage();
            App.post(() -> {
                ensureDialogShown(activity);
                if (dialog != null) {
                    dialog.setStatus(ResUtil.getString(R.string.update_download_failed, e.getMessage()));
                    dialog.setDebugInfo(buildDebugInfo("", "", "", "", debugSource,
                            "异常抛出，详情见堆栈 → 视为「已是最新」不弹窗。\n修复后会显示错误原因，也能再次手动重试。"));
                    dialog.setConfirmEnabled(false);
                }
                Notify.show("更新检测异常：" + e.getMessage());
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
        // 只有 apkCursor==0（第一次开始下载）时才做并行 HEAD 连通性排序，失败 fallback 的后续轮次不再重排
        if (apkCursor == 0 && apkUrls.size() >= 2 && dialog != null) {
            dialog.setStatus("正在挑选最快镜像（并行探测 4s）…");
        }
        if (apkCursor == 0 && apkUrls.size() >= 2) {
            try {
                // 同步调用会阻塞「后台下载发起线程」，最多 ~4.3s；超时/异常直接用默认顺序
                apkUrls = Github.rankByConnectivity(apkUrls);
            } catch (Throwable ignored) {
            }
        }
        String url = apkUrls.get(apkCursor);
        // 进度条状态提示：当前正在下载的镜像名（如果不是直连 github.com 的话），避免"0% 卡死时不知道正在连哪个"
        String mirrorTag = Github.getMirrorLabel(url);
        if (apkCursor > 0 && dialog != null) {
            dialog.setStatus("镜像 " + (apkCursor + 1) + "/" + apkUrls.size() + "：" + mirrorTag + " 下载中…（前一镜像失败，自动切换）");
        } else if (dialog != null) {
            dialog.setStatus("下载中（" + mirrorTag + "，候选镜像共 " + apkUrls.size() + " 条）…");
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
            final int cur = apkCursor + 1;
            final int total = apkUrls.size();
            final String nextLabel = Github.getMirrorLabel(apkUrls.get(apkCursor));
            if (dialog != null) {
                dialog.setStatus("镜像 " + cur + "/" + total + "：切换到 " + nextLabel + " …");
            }
            App.post(this::startDownload);
            return;
        }
        // 所有镜像都失败：才真正显示错误
        if (dialog != null) {
            dialog.setStatus(ResUtil.getString(R.string.update_download_failed, msg + "（全部 " + (apkUrls == null ? 0 : apkUrls.size()) + " 条镜像均失败）"));
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
