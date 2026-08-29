package com.ssmhdssmhd.mxboxs.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.ssmhdssmhd.mxboxs.App;
import com.ssmhdssmhd.mxboxs.BuildConfig;
import com.ssmhdssmhd.mxboxs.R;
import com.ssmhdssmhd.mxboxs.event.DownloadProgressEvent;
import com.ssmhdssmhd.mxboxs.utils.Download;
import com.ssmhdssmhd.mxboxs.utils.FileUtil;
import com.ssmhdssmhd.mxboxs.utils.Github;
import com.ssmhdssmhd.mxboxs.utils.Path;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * APK 下载前台 Service（v5.7.11）。
 *
 * 为什么要前台 Service：
 *   用户点击对话框「更新」后，Activity 随时可能被用户关掉（返回键 / 杀后台 / 系统回收）。
 *   旧逻辑直接在 Updater 里 Download.start() → Updater 被 GC → Download 中断 → 下次又要从头下。
 *   现在把下载搬到独立的前台 Service：
 *     startForeground() 让 Service 进入 Android 的「重要进程」白名单，系统不会杀；
 *     通知栏常驻下载进度（% + MB/MB），用户切后台也能看见；
 *     Download 内置断点续传（.tmp 文件 Range 续传），关掉重开对话框 → 继续下；
 *     完成后自动调 FileUtil.installApk() 安装。
 *
 * 和 UI（Updater 对话框）的通信：EventBus，订阅 DownloadProgressEvent 即可。
 *
 * Manifest 要求：
 *   <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
 *   <service android:name=".service.DownloadService"
 *            android:foregroundServiceType="dataSync|download"
 *            android:exported="false" />
 */
public class DownloadService extends Service implements Download.Callback {

    public static final String ACTION_START = BuildConfig.APPLICATION_ID + ".ACTION_START_DOWNLOAD";
    public static final String ACTION_CANCEL = BuildConfig.APPLICATION_ID + ".ACTION_CANCEL_DOWNLOAD";

    public static final String EXTRA_APK_URLS = "apk_urls";      // ArrayList<String>
    public static final String EXTRA_APK_FILE = "apk_file";      // String (path)
    public static final String EXTRA_VERSION = "version";        // String (显示用)
    public static final String EXTRA_APK_ASSET_SIZE = "apk_size";// long (可选，完整 APK 大小，安装前校验用)

    private static final String CHANNEL_ID = "mxboxs_download";
    private static final int NOTIFICATION_ID = 9528;

    private final IBinder binder = new LocalBinder();
    private Download download;
    private File targetFile;
    private List<String> apkUrls;
    private int apkCursor;
    private long apkAssetSize;
    private String version;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** 通知栏构造器缓存（频繁更新 notification，不重建 builder） */
    private NotificationCompat.Builder notifBuilder;

    public class LocalBinder extends Binder {
        public DownloadService getService() { return DownloadService.this; }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String action = intent.getAction();
        if (ACTION_CANCEL.equals(action)) {
            stopDownload("用户取消");
            return START_NOT_STICKY;
        }

        if (ACTION_START.equals(action) && !running.get()) {
            startForeground(NOTIFICATION_ID, buildProgressNotification("正在准备下载…", 0, 0, -1));

            apkUrls = intent.getStringArrayListExtra(EXTRA_APK_URLS);
            String fileStr = intent.getStringExtra(EXTRA_APK_FILE);
            targetFile = (fileStr != null) ? new File(fileStr) : Path.cache("update.apk");
            apkAssetSize = intent.getLongExtra(EXTRA_APK_ASSET_SIZE, -1L);
            version = intent.getStringExtra(EXTRA_VERSION);
            apkCursor = 0;

            // 先 probe 镜像（和 Updater 原有逻辑一致，但 UI 是通知栏 + EventBus 事件）
            if (apkUrls != null && apkUrls.size() >= 2) {
                EventBus.getDefault().post(DownloadProgressEvent.probing("正在扫描可用镜像…"));
                runProbeThenStart();
            } else {
                startNextUrl();
            }
        }
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // UI 解绑（对话框 dismiss）不影响下载，Service 继续跑
        return super.onUnbind(intent);
    }

    // ========== 探针 ==========

    private void runProbeThenStart() {
        // 在后台线程跑 probe（DownloadService 自身就是前台 Service，可以直接 Task.execute）
        com.ssmhdssmhd.mxboxs.utils.Task.execute(() -> {
            try {
                List<Github.ProbeResult> results = Github.probeUrls(apkUrls, null);
                List<String> sorted = Github.extractUrls(results);
                if (sorted != null && !sorted.isEmpty()) {
                    apkUrls = sorted;
                }
                apkCursor = 0;
                startNextUrl();
            } catch (Throwable t) {
                // probe 失败直接试第一条
                startNextUrl();
            }
        });
    }

    // ========== 下载循环（多镜像 fallback） ==========

    private synchronized void startNextUrl() {
        if (apkUrls == null || apkCursor >= apkUrls.size()) {
            stopDownload("所有镜像下载失败");
            return;
        }
        running.set(true);
        String url = apkUrls.get(apkCursor);
        // 如果是切源（apkCursor > 0），尝试断点续传从 tmp 继续，
        // 但换了一个 host，tmp 是旧 host 写的 —— 不能续！所以 noResume() 强制从头下
        boolean isFallback = apkCursor > 0;
        EventBus.getDefault().post(new DownloadProgressEvent(
                DownloadProgressEvent.STATE_PROGRESS, 0, 0, -1,
                isFallback ? "切换镜像 " + (apkCursor + 1) + "/" + apkUrls.size() + "：" + Github.getMirrorLabel(url)
                           : "开始下载：" + Github.getMirrorLabel(url)));
        updateNotification("开始下载：" + Github.getMirrorLabel(url), 0, 0, -1);

        if (download != null) download.cancel();
        download = Download.create(url, targetFile, Download.APK_DOWNLOAD_TIMEOUT_MS).tag(url);
        if (isFallback) download.noResume();
        download.start(this);
    }

    private void stopDownload(String reason) {
        running.set(false);
        if (download != null) {
            download.cancel();
            download = null;
        }
        EventBus.getDefault().post(DownloadProgressEvent.error(reason));
        try { stopForeground(STOP_FOREGROUND_REMOVE); } catch (Throwable ignored) {}
        stopSelf();
    }

    // ========== Download.Callback ==========

    @Override
    public void progress(int progress, long downloadedBytes, long totalBytes) {
        if (!running.get()) return;
        EventBus.getDefault().post(DownloadProgressEvent.progress(progress, downloadedBytes, totalBytes));
        updateNotification(null, progress, downloadedBytes, totalBytes);
    }

    @Override
    public void error(String msg) {
        // 当前镜像失败 → 自动切下一条（和 Updater 原有 fallback 逻辑一致，但 Service 内部完成）
        // 先做 APK 完整性校验（和 Updater.verifyApkIntegrity 一致）
        String verifyFail = verifyIfNeeded();
        if (verifyFail != null) {
            // 坏 tmp 清掉，下次从头下
            try {
                File t = (download != null) ? download.tmpFile() : null;
                if (t != null && t.exists()) t.delete();
                if (targetFile != null && targetFile.exists()) targetFile.delete();
            } catch (Throwable ignored) {}
        }
        apkCursor++;
        String hostError = msg;
        if (apkUrls != null && apkCursor < apkUrls.size()) {
            EventBus.getDefault().post(new DownloadProgressEvent(
                    DownloadProgressEvent.STATE_PROGRESS, -1, 0, -1,
                    "镜像 " + apkCursor + "/" + apkUrls.size() + " 失败（" + trimMsg(msg) + "），自动切换下一条…"));
            runNext();
        } else {
            // 全部失败：最终 error
            EventBus.getDefault().post(DownloadProgressEvent.error("下载失败：" + trimMsg(hostError) + "（已尝试 " + (apkUrls == null ? 0 : apkUrls.size()) + " 条镜像）"));
            updateNotification("下载失败", -1, 0, -1);
            try { stopForeground(STOP_FOREGROUND_REMOVE); } catch (Throwable ignored) {}
            running.set(false);
            stopSelf();
        }
    }

    private void runNext() {
        com.ssmhdssmhd.mxboxs.utils.Task.execute(() -> startNextUrl());
    }

    @Override
    public void success(File file) {
        // 完整性校验（长度 + ZIP 魔术）
        String verifyFail = verifyIfNeeded();
        if (verifyFail != null) {
            EventBus.getDefault().post(new DownloadProgressEvent(
                    DownloadProgressEvent.STATE_PROGRESS, -1, 0, -1,
                    "APK 校验失败（" + verifyFail + "），自动切换镜像…"));
            // 清坏文件
            try {
                if (file != null && file.exists()) file.delete();
            } catch (Throwable ignored) {}
            apkCursor++;
            if (apkUrls != null && apkCursor < apkUrls.size()) runNext();
            else {
                EventBus.getDefault().post(DownloadProgressEvent.error("APK 校验失败：" + verifyFail));
                try { stopForeground(STOP_FOREGROUND_REMOVE); } catch (Throwable ignored) {}
                running.set(false);
                stopSelf();
            }
            return;
        }
        // 成功 → 发 Event + install
        EventBus.getDefault().post(DownloadProgressEvent.success(file == null ? "" : file.getAbsolutePath()));
        updateNotification("下载完成，正在安装…", 100, file == null ? 0 : file.length(), file == null ? 0 : file.length());
        try { stopForeground(STOP_FOREGROUND_DETACH); } catch (Throwable ignored) {}
        running.set(false);
        FileUtil.installApk(file);
        stopSelf();
    }

    // ========== APK 完整性校验（简化版，和 Updater.verifyApkIntegrity 一致的三层校验） ==========

    private String verifyIfNeeded() {
        File f = download != null ? download.tmpFile() : targetFile;
        if (f == null) f = targetFile;
        if (f == null || !f.exists() || f.length() <= 0) return "文件不存在或大小为 0";
        long len = f.length();
        if (apkAssetSize > 0) {
            double ratio = (double) len / apkAssetSize;
            if (ratio < 0.90d || ratio > 1.10d) {
                return "长度不匹配（官方 " + apkAssetSize + "B，实际 " + len + "B）";
            }
        }
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(f)) {
            java.util.Enumeration<?> en = zf.entries();
            int count = 0;
            boolean hasManifest = false, hasArsc = false;
            while (en.hasMoreElements()) {
                java.util.zip.ZipEntry e = (java.util.zip.ZipEntry) en.nextElement();
                count++;
                String n = e.getName();
                if (n == null) continue;
                if ("AndroidManifest.xml".equals(n)) hasManifest = true;
                else if ("resources.arsc".equals(n)) hasArsc = true;
            }
            if (count <= 0) return "Zip 文件读不出任何 entry";
            if (!hasManifest) return "缺少 AndroidManifest.xml（可能是 HTML 错误页）";
            if (!hasArsc) return "缺少 resources.arsc";
            return null;
        } catch (Throwable t) {
            return "ZipFile 打开失败：" + t.getMessage();
        }
    }

    private static String trimMsg(String s) {
        if (s == null) return "";
        return s.length() > 60 ? s.substring(0, 57) + "…" : s;
    }

    // ========== 通知栏 ==========

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel ch = new NotificationChannel(
                        CHANNEL_ID, "MXboxS 更新下载",
                        NotificationManager.IMPORTANCE_LOW);
                ch.setShowBadge(false);
                nm.createNotificationChannel(ch);
            }
        }
    }

    private Notification buildProgressNotification(String status, int progress, long downloadedBytes, long totalBytes) {
        if (notifBuilder == null) {
            notifBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("MXboxS v" + (version == null ? BuildConfig.VERSION_NAME : version) + " 更新下载")
                    .setOngoing(true)
                    .setProgress(100, 0, false);
        }
        if (status != null) notifBuilder.setContentText(status);

        // 只在已知 totalBytes 时设置进度百分比
        if (totalBytes > 0) {
            int p = (int) (Math.max(0, Math.min(100,
                    progress >= 0 ? progress : (downloadedBytes * 100L / totalBytes))));
            notifBuilder.setProgress(100, p, false);
            String detail = Download.formatBytes(downloadedBytes) + " / " + Download.formatBytes(totalBytes);
            notifBuilder.setSubText(detail);
        } else if (downloadedBytes > 0) {
            notifBuilder.setProgress(0, 0, true); // 不确定进度条
            notifBuilder.setSubText("已下载 " + Download.formatBytes(downloadedBytes));
        } else {
            notifBuilder.setProgress(0, 0, true);
            notifBuilder.setSubText(null);
        }
        return notifBuilder.build();
    }

    private void updateNotification(String status, int progress, long downloadedBytes, long totalBytes) {
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID,
                    buildProgressNotification(status, progress, downloadedBytes, totalBytes));
        } catch (Throwable ignored) {}
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        running.set(false);
        if (download != null) try { download.cancel(); } catch (Throwable ignored) {}
    }
}
