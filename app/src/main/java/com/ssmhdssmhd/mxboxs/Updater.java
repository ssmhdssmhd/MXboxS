package com.ssmhdssmhd.mxboxs;

import android.text.TextUtils;
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
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;

public class Updater implements Download.Callback, UpdateListener {

    private Download download;
    private UpdateDialog dialog;
    private JSONObject release;
    private JSONObject apkAsset; // 匹配到的 release asset（含 size/name），下载后用 asset.size 校验完整性
    private List<String> apkUrls;
    private int apkCursor;
    private boolean forced;
    /** 预测试阶段完成后保存的 probe 总结文案，用于全部失败时追加到 debug 面板（让用户知道哪几个镜像 DNS 解析失败） */
    private String lastProbeSummary;

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
                // 已是最新版本：强制模式必须 showDialog；更新内容显示 release.body（若有）
                final String changelog = desc == null ? "" : desc;
                App.post(new Runnable() {
                    @Override
                    public void run() {
                        ensureDialogShown(activity);
                        if (dialog != null) {
                            dialog.setStatus(ResUtil.getString(R.string.update_no_new));
                            dialog.setChangelog(changelog);
                            dialog.setConfirmEnabled(false);
                        }
                    }
                });
                return;
            }

            // 找到 APK 下载链接（多镜像候选列表，下载失败自动 fallback）。
            // v5.5.61 起 Github.findApkUrls 内部已经做了：
            //   - 去重（用户首选 + MIRROR_POOL 全部前缀 + objects.githubusercontent.com 原始直连 2 条版本）
            //   - 因此不再调用旧的 ensureCandidates()（该方法已删除，jsdelivr 无效候选也一并移除）
            apkUrls = Github.findApkUrls(release);
            apkCursor = 0;
            // 顺便拿到匹配到的 APK asset（含 size/name），下载完成做完整性校验用
            try { apkAsset = Github.pickDirectApkAsset(release); } catch (Throwable ignored) { apkAsset = null; }

            // 连接成功，有新版本
            App.post(new Runnable() {
                @Override
                public void run() {
                    ensureDialogShown(activity);
                    if (dialog != null) {
                        dialog.setStatus(ResUtil.getString(R.string.update_connected, version));
                        dialog.updateTitle(ResUtil.getString(R.string.update_version, version));
                        dialog.updateDesc(desc.isEmpty() ? ResUtil.getString(R.string.update_downloading) : desc);
                        // 下部「更新内容」展示 release.body（changelog）
                        dialog.setChangelog(desc == null ? "" : desc);
                        // 自动开始下载
                        startDownload();
                    }
                }
            });
        } catch (final Exception e) {
            e.printStackTrace();
            final String msg = "更新检测异常：" + e.getClass().getSimpleName() + " " + e.getMessage();
            App.post(() -> {
                ensureDialogShown(activity);
                if (dialog != null) {
                    dialog.setStatus(ResUtil.getString(R.string.update_download_failed, e.getMessage()));
                    // 网络错误/解析错误时，更新内容显示错误原因便于排错
                    dialog.setChangelog(msg);
                    dialog.setConfirmEnabled(false);
                }
                Notify.show(msg);
            });
        }
    }

    private void startDownload() {
        if (apkUrls == null || apkUrls.isEmpty() || apkCursor >= apkUrls.size()) {
            if (dialog != null) {
                dialog.setStatus(ResUtil.getString(R.string.update_download_failed, "APK not found"));
                dialog.setConfirmEnabled(true, R.string.update_retry);
            }
            return;
        }

        // 先测试再下载：仅 apkCursor == 0（新开始 / 用户点重试）触发一次预测试。
        //   1. probeUrls 并行跑，1.5s 超短探测（HEAD 失败回退 GET 0-0 字节），避免 ghps.cambridgecs.co 之类 DNS 解析失败拖到真正下载阶段才抛异常
        //   2. UI 实时滚动「探针 3/14：ghproxy.com ✅ 187ms / ghps.cambridgecs.com ❌ DNS 解析失败」
        //   3. probe 结束后 apkUrls 重新赋值为 extractUrls(sorted)：可用的放前面，RTT 小的放最前
        if (apkCursor == 0 && apkUrls.size() >= 2) {
            // UI：预测试模式，progressBar 用不确定进度（如果支持），这里用 setProgress(0,0,0) 也能提示用户「准备阶段」
            final int total = apkUrls.size();
            final AtomicInteger doneCount = new AtomicInteger(0);
            final StringBuilder probeSummary = new StringBuilder();
            if (dialog != null) {
                dialog.setStatus("预测试：正在扫描 " + total + " 条镜像可用性（最快 1.5s 出结果，失败的直接跳过，不进入下载阶段）…");
                dialog.showProgress();
                dialog.setProgress(0);
            }
            final List<String> currentCandidates = apkUrls;
            List<Github.ProbeResult> results = Github.probeUrls(currentCandidates, new Github.ProbeListener() {
                @Override
                public void onProbeOne(int index, int t, Github.ProbeResult r) {
                    UpdateDialog d = UpdateDialogWrap.access(Updater.this);
                    if (d == null) return;
                    int dn = doneCount.incrementAndGet();
                    String label = Github.getMirrorLabel(r.url);
                    String mark;
                    if (r.ok) mark = "✅";
                    else mark = "❌";
                    String extra = "";
                    if (r.ok) extra = r.rttMs < Long.MAX_VALUE ? (r.rttMs + "ms") : "";
                    else if (r.error != null) extra = r.error;
                    probeSummary.setLength(0);
                    probeSummary.append(label).append(" ").append(mark).append(" ").append(extra);
                    d.setStatus("探针 " + dn + "/" + total + "：" + label + " " + mark + " " + extra + "（继续探测剩余 " + Math.max(0, total - dn) + " 条）…");
                    // 用 setProgress 的「整数进度」模拟探测比例，让 UI 有反馈
                    int percent = (int) (dn * 100L / Math.max(1, total));
                    d.setProgress(percent);
                }
            });
            // 构建总结文案（给 debug 面板用）和新 apkUrls（可用的放前面）
            StringBuilder sb = new StringBuilder("预测试结果：" + total + " 条候选 → ");
            int ok = 0, fail = 0;
            for (Github.ProbeResult r : results) {
                if (r == null) { fail++; continue; }
                if (r.ok) ok++; else fail++;
            }
            sb.append("✅ ").append(ok).append(" 条可用，❌ ").append(fail).append(" 条失败；失败项明细：");
            for (Github.ProbeResult r : results) {
                if (r == null || r.ok) continue;
                sb.append("\n  - ").append(Github.getMirrorLabel(r.url)).append(" → ").append(r.error == null ? "unknown" : r.error);
            }
            lastProbeSummary = sb.toString();
            // 按 ok 优先 + RTT 升序，重排 apkUrls 列表（extractUrls 直接按排序后的顺序抽出）
            List<String> sorted = Github.extractUrls(results);
            if (sorted != null && !sorted.isEmpty()) apkUrls = sorted;
        }

        String url = apkUrls.get(apkCursor);
        // 进度条状态提示：当前正在下载的镜像名（如果不是直连 github.com 的话），避免"0% 卡死时不知道正在连哪个"
        String mirrorTag = Github.getMirrorLabel(url);
        if (apkCursor > 0 && dialog != null) {
            dialog.setStatus("镜像 " + (apkCursor + 1) + "/" + apkUrls.size() + "：" + mirrorTag + " 下载中…（前一镜像失败，自动切换）");
        } else if (dialog != null) {
            dialog.setStatus("下载中（" + mirrorTag + "，候选镜像共 " + apkUrls.size() + " 条 · 10s 超时快速切源）…");
        }
        if (dialog != null) {
            dialog.showProgress();
            dialog.setProgress(0, 0, 0);
        }
        if (download != null) download.cancel();
        download = Download.create(url, getFile(), Download.APK_DOWNLOAD_TIMEOUT_MS).tag(url);
        download.start(this);
    }

    /** 小技巧：ProbeListener.onProbeOne 在 Github 内部通过 App.post 回 UI 线程，但内部类访问 Updater.this.dialog 要避免 null——直接从静态包装类拿引用。 */
    private static final class UpdateDialogWrap {
        static UpdateDialog access(Updater u) { return u.dialog; }
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
        // 手动点右下角（"更新"/"重试"）：把游标重置到候选列表开头，重新按并行 HEAD 挑最快镜像再下载。
        // 按钮禁用/文案切换统一由 startDownload()->showProgress() 或 error()->setConfirmEnabled(true, 重试) 管理，不要在这里手动写回 enabled。
        apkCursor = 0;
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
        // 兼容旧接口（如果其他地方直接调用 progress(int) 而非新接口）
        if (dialog != null) dialog.setProgress(progress);
    }

    @Override
    public void progress(int progress, long downloadedBytes, long totalBytes) {
        if (dialog != null) dialog.setProgress(progress, downloadedBytes, totalBytes);
    }

    @Override
    public void error(String msg) {
        // 下载失败自动切换下一个镜像（如果还有候选）。
        // v5.5.61 新增：连续"同一 host（尤其 ghproxy 变体，10+ 条都是同反代）"会进入 Github.BAD_MIRROR_HOSTS 黑名单，
        // 这一步直接跳过所有 host 命中黑名单的候选，避免用户 14 条镜像全是同源变体时白白等待 14×60s。
        if (apkUrls != null) {
            while (apkCursor + 1 < apkUrls.size()) {
                apkCursor++;
                String nextUrl = apkUrls.get(apkCursor);
                // 黑名单命中：host 在上一轮 verifyApkIntegrity 失败里加过的直接跳
                String host = "";
                try {
                    int s = nextUrl == null ? -1 : nextUrl.indexOf("://");
                    if (s >= 0) {
                        int e = nextUrl.indexOf('/', s + 3);
                        host = (e < 0 ? nextUrl.substring(s + 3) : nextUrl.substring(s + 3, e));
                    }
                } catch (Throwable ignored) {}
                if (!host.isEmpty() && Github.BAD_MIRROR_HOSTS.contains(host)) continue;
                // 找到了一条未被拉黑的，跳出
                final int cur = apkCursor + 1;
                final int total = apkUrls.size();
                final String nextLabel = Github.getMirrorLabel(nextUrl);
                if (dialog != null) {
                    dialog.setStatus("镜像 " + cur + "/" + total + "：切换到 " + nextLabel + " …（" + msg + "）");
                    dialog.setConfirmEnabled(false);
                }
                App.post(this::startDownload);
                return;
            }
        }
        // 所有镜像都失败：才真正显示错误，并把「正在下载…」按钮改为「重试」可点击
        if (dialog != null) {
            dialog.setStatus(ResUtil.getString(R.string.update_download_failed, msg + "（全部 " + (apkUrls == null ? 0 : apkUrls.size()) + " 条镜像均失败）"));
            dialog.setConfirmEnabled(true, R.string.update_retry);
            // 将失败详情追加到「更新内容」区域（下部），便于用户看到失败全貌
            StringBuilder more = new StringBuilder();
            if (lastProbeSummary != null && !lastProbeSummary.isEmpty()) {
                more.append("============ 预测试总结 ============\n").append(lastProbeSummary).append("\n\n");
            }
            if (!Github.BAD_MIRROR_HOSTS.isEmpty()) {
                more.append("已加入黑名单的坏镜像 host（本次运行下的重试/重测将自动跳过）：\n");
                for (String h : Github.BAD_MIRROR_HOSTS) more.append("  · ").append(h).append('\n');
                more.append('\n');
            }
            more.append("最后一次错误：").append(msg)
                    .append("\n已尝试 ").append(apkUrls == null ? 0 : apkUrls.size()).append(" 条候选镜像（先测后下载：探针 6s / 条读前 1KB 校验 ZIP 魔术，下载阶段 60s 未响应自动切源，同源坏 host 同轮被拉黑不再重复等待）。")
                    .append("\n可点击右下角『重试』再次从第一个镜像重新预测试并按连通性排序后下载；或到「设置 → 更新源」切换首选镜像（建议默认选择『GitHub 直连』最快最稳；国内 ghproxy 公益反代经常出现 HTTP 200 但 body=错误页的假响应）。");
            CharSequence prev = dialog.readDebugInfo();
            dialog.setChangelog(prev == null || TextUtils.isEmpty(prev.toString()) ? more.toString() : (prev + "\n\n" + more.toString()));
        }
        Notify.show(msg);
    }

    @Override
    public void success(File file) {
        // ========== 下载完成 + 安装前，做「完整性双校验」 ==========
        // 过去国内镜像（尤其 ghproxy/ghps）常见：半路返回 <html>error 502</html> 但 HTTP 200，
        // 或者被 CDN 缓存截断到一半字节数。App 仍当"下载成功"调 installApk，
        // 系统 PackageParser 读坏 zip 就弹用户"解析软件包时出现问题"，没任何有效信息。
        // 这里先自己校验，失败直接走 error()，会自动切换到下一个镜像重下。
        // v5.5.61 新增：校验失败时同时把当前 URL 的 host 加入 Github.BAD_MIRROR_HOSTS，
        // 同一进程内后续的 probeUrls / error() 都会直接跳过这些 host，避免 14 条同源 ghproxy 变体全部白等。
        String verifyFail = verifyApkIntegrity(file);
        if (verifyFail != null) {
            // 0) 加入 host 黑名单（如果能拿到当前正在用的 URL）
            try {
                String currentUrl = null;
                if (apkUrls != null && apkCursor >= 0 && apkCursor < apkUrls.size()) {
                    currentUrl = apkUrls.get(apkCursor);
                }
                if (currentUrl != null && !currentUrl.isEmpty()) {
                    int s = currentUrl.indexOf("://");
                    if (s >= 0) {
                        int e = currentUrl.indexOf('/', s + 3);
                        String host = (e < 0 ? currentUrl.substring(s + 3) : currentUrl.substring(s + 3, e));
                        if (host != null && !host.isEmpty()) Github.BAD_MIRROR_HOSTS.add(host);
                    }
                }
            } catch (Throwable ignored) {
            }
            // 这个文件是坏的，先删掉（免得下次继续用同一个缓存文件）
            try { if (file != null && file.exists()) file.delete(); } catch (Throwable ignored) {}
            Notify.show("APK 校验失败，自动切换下一镜像：" + verifyFail);
            error("下载文件损坏（" + verifyFail + "）");
            return;
        }
        if (dialog != null) {
            dialog.setStatus(ResUtil.getString(R.string.update_installing));
        }
        FileUtil.installApk(file);
        dismiss();
    }

    /**
     * 对刚下好的 APK 做两层校验，任何一层失败都返回错误描述（用于日志/UI 提示）。
     *   ① 长度校验：release.asset.size （GitHub 官方声明） vs file.length()
     *     允许 2% 误差（极少数 CDN 会做 gzip/br 传输，但下载器一般是解压写盘；给个 buffer 不要因此误伤）
     *   ② zip 结构校验：能 new ZipFile(file) 并读到 entries，且能看到
     *     `AndroidManifest.xml` + `resources.arsc` + `classes.dex` 三个典型文件
     *     （覆盖了假 apk = html 报错页、zip 中心目录损坏、下载截断导致一半文件头好但缺 entries 等情况）
     */
    private String verifyApkIntegrity(File file) {
        if (file == null) return "文件对象为空";
        if (!file.exists()) return "文件不存在";
        long fileLen = file.length();
        if (fileLen <= 0) return "文件大小为 0";

        // ① 长度完整性（优先：我们有 release.asset.size 的时候才做）
        long expected = -1;
        String apkNameForDebug = "";
        if (apkAsset != null) {
            expected = apkAsset.optLong("size", -1);
            apkNameForDebug = apkAsset.optString("name", "");
        }
        if (expected > 0) {
            // 允许 2% 容差，但绝对长度不能小于官方的 90%（严重截断），也不能大 1.1 倍以上
            double ratio = (double) fileLen / expected;
            if (ratio < 0.90d || ratio > 1.10d) {
                return String.format("文件长度不匹配（官方 %dB，实际 %dB，ratio %.2f，对应 APK %s）",
                        expected, fileLen, ratio, apkNameForDebug.isEmpty() ? "未知" : apkNameForDebug);
            }
        }

        // ② zip 结构 + 典型 entries 存在校验（这一步能覆盖绝大多数"假 APK = html 页"）
        ZipFile zf = null;
        try {
            zf = new ZipFile(file);
            boolean hasManifest = false, hasArsc = false, hasClassesDex = false;
            int entryCount = 0;
            Enumeration<?> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = (ZipEntry) en.nextElement();
                entryCount++;
                String n = e.getName();
                if (n == null) continue;
                if ("AndroidManifest.xml".equals(n)) hasManifest = true;
                else if ("resources.arsc".equals(n)) hasArsc = true;
                else if (n.startsWith("classes") && n.endsWith(".dex")) hasClassesDex = true;
            }
            if (entryCount <= 0) return "Zip 文件读不出任何 entry（可能只写了 4 字节头就被截断）";
            if (!hasManifest) return "Zip 内缺少 AndroidManifest.xml（这不是 APK，可能是镜像返回了 HTML 错误页）";
            if (!hasArsc) return "Zip 内缺少 resources.arsc（APK 结构不完整）";
            if (!hasClassesDex) return "Zip 内缺少 classes*.dex（APK 结构不完整）";
            return null;
        } catch (Throwable t) {
            return "ZipFile 打开失败：" + t.getClass().getSimpleName() + " " + t.getMessage();
        } finally {
            if (zf != null) { try { zf.close(); } catch (Throwable ignored) {} }
        }
    }
}
