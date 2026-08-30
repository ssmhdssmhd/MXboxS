package com.ssmhdssmhd.mxboxs.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.ssmhdssmhd.mxboxs.App;
import com.ssmhdssmhd.mxboxs.R;
import com.ssmhdssmhd.mxboxs.utils.Download;
import com.ssmhdssmhd.mxboxs.utils.FileUtil;
import com.ssmhdssmhd.mxboxs.utils.Github;
import com.github.catvod.utils.Path;

import org.json.JSONObject;

import java.io.File;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * APK 更新下载前台服务。
 * <p>
 * 职责：
 * 1. 常驻前台（foregroundServiceType=dataSync），保证后台下载不被系统杀掉；
 * 2. 通知栏持久化进度条（0-100%，同时显示 "已下载 X.X MB / Y.Y MB"）；
 * 3. 复用 Github.findApkUrls + probeUrls 的镜像切换逻辑，下载完成后做完整性双校验；
 * 4. 自动调用 FileUtil.installApk() 拉起系统安装器；
 * 5. 安装后清理旧的 update.apk 缓存（自动清理，不占用户存储空间）。
 * <p>
 * 触发方式：App 内 Updater 检测到新版本后，可通过 startService(Intent) 启动本服务。
 * 如果下载时对话框还在显示，服务和对话框共用同一个 Download 实例。
 */
public class UpdateService extends Service implements Download.Callback {

    public static final String CHANNEL_ID = "update_download";
    public static final int NOTIF_ID = 8888;

    public static final String ACTION_START = "com.ssmhdssmhd.mxboxs.action.UPDATE_START";
    public static final String ACTION_CANCEL = "com.ssmhdssmhd.mxboxs.action.UPDATE_CANCEL";

    public static final String EXTRA_APK_URLS = "apk_urls";       // ArrayList<String>
    public static final String EXTRA_APK_ASSET = "apk_asset";     // JSONObject (size / name) 可空
    public static final String EXTRA_VERSION = "version_name";    // String（通知标题显示 "更新到 vX.X.X"）

    private Download download;
    private List<String> apkUrls;
    private int apkCursor;
    private JSONObject apkAsset;
    private String versionName;
    private NotificationManagerCompat nm;

    /** 上一次发送的进度百分比，避免每 1% 都重建 Notification（耗电） */
    private int lastProgress = -1;

    public static void start(Context ctx, List<String> urls, JSONObject asset, String version) {
        Intent i = new Intent(ctx, UpdateService.class);
        i.setAction(ACTION_START);
        i.putStringArrayListExtra(EXTRA_APK_URLS, new java.util.ArrayList<>(urls));
        if (asset != null) i.putExtra(EXTRA_APK_ASSET, asset.toString());
        i.putExtra(EXTRA_VERSION, version);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(i);
        } else {
            ctx.startService(i);
        }
    }

    public static void cancel(Context ctx) {
        Intent i = new Intent(ctx, UpdateService.class);
        i.setAction(ACTION_CANCEL);
        ctx.startService(i);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        nm = NotificationManagerCompat.from(this);
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if (ACTION_CANCEL.equals(action)) {
            stopDownload();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_START.equals(action)) {
            apkUrls = intent.getStringArrayListExtra(EXTRA_APK_URLS);
            String assetStr = intent.getStringExtra(EXTRA_APK_ASSET);
            apkAsset = null;
            if (!TextUtils.isEmpty(assetStr)) {
                try { apkAsset = new JSONObject(assetStr); } catch (Throwable ignored) {}
            }
            versionName = intent.getStringExtra(EXTRA_VERSION);
            apkCursor = 0;
            startForeground(NOTIF_ID, buildNotification("正在准备下载…", -1, 0, 0, true));
            startRealDownload();
        }
        return START_STICKY;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "应用更新下载",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            ch.setDescription("MXboxS APK 下载进度");
            NotificationManager nmSys = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nmSys != null) nmSys.createNotificationChannel(ch);
        }
    }

    /**
     * 构建下载进度 Notification。
     * indeterminate=true 时只显示 "下载中…" 的动画环；
     * false 时显示确定进度条 (progress, 0, 100)。
     */
    private Notification buildNotification(String text, int progress, long downloaded, long total, boolean indeterminate) {
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("MXboxS 更新" + (!TextUtils.isEmpty(versionName) ? " → v" + versionName : ""))
                .setContentText(text)
                .setOngoing(true)
                .setProgress(indeterminate ? 0 : 100, indeterminate ? 0 : progress, indeterminate)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(false);
        if (downloaded > 0) {
            StringBuilder sb = new StringBuilder();
            sb.append(Download.formatBytes(downloaded));
            if (total > 0) sb.append(" / ").append(Download.formatBytes(total));
            b.setSubText(sb.toString());
        }
        return b.build();
    }

    private void updateNotification(String text, int progress, long downloaded, long total) {
        // 进度至少变 1% 才更新通知（避免过度重建）
        int key = progress < 0 ? -1 : progress;
        if (key != -1 && key != 100 && key == lastProgress && total > 0) return;
        lastProgress = key;
        Notification n = buildNotification(text, Math.max(0, key), downloaded, total, progress < 0);
        nm.notify(NOTIF_ID, n);
    }

    private void startRealDownload() {
        if (apkUrls == null || apkUrls.isEmpty() || apkCursor >= apkUrls.size()) {
            notifyError("未找到 APK 下载地址");
            stopSelf();
            return;
        }
        String url = apkUrls.get(apkCursor);
        String label = Github.getMirrorLabel(url);
        updateNotification("下载中（" + label + "，镜像 " + (apkCursor + 1) + "/" + apkUrls.size() + "）…", 0, 0, 0);

        File target = Path.cache("update.apk");
        // 每次开新下载前先清旧文件，避免残留损坏
        try { if (target.exists()) target.delete(); } catch (Throwable ignored) {}

        if (download != null) download.cancel();
        download = Download.create(url, target, Download.APK_DOWNLOAD_TIMEOUT_MS).tag("UpdateService:" + url);
        download.start(this);
    }

    private void stopDownload() {
        if (download != null) download.cancel();
        download = null;
    }

    private void notifyError(String msg) {
        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("MXboxS 更新失败")
                .setContentText(msg)
                .setOngoing(false)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();
        nm.notify(NOTIF_ID + 1, n);
    }

    private void notifyCompleted() {
        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("MXboxS 下载完成")
                .setContentText("正在拉起安装器…")
                .setOngoing(false)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build();
        nm.notify(NOTIF_ID + 2, n);
        nm.cancel(NOTIF_ID); // 关闭进度条通知
    }

    // ========== Download.Callback ==========

    @Override
    public void progress(int progress, long downloadedBytes, long totalBytes) {
        String text;
        if (progress < 0) {
            text = "下载中…";
        } else {
            text = "下载中 " + progress + "%";
        }
        updateNotification(text, progress, downloadedBytes, totalBytes);
    }

    @Override
    public void progress(int progress) {
        progress(progress, -1, -1);
    }

    @Override
    public void error(String msg) {
        // 自动切换下一个镜像（和 Updater 逻辑一致）
        if (apkUrls != null && apkCursor + 1 < apkUrls.size()) {
            apkCursor++;
            App.post(this::startRealDownload);
            return;
        }
        notifyError(msg + "（全部镜像均失败）");
        stopSelf();
    }

    @Override
    public void success(File file) {
        // 完整性校验
        String reason = verifyApkIntegrity(file);
        if (reason != null) {
            try { if (file != null && file.exists()) file.delete(); } catch (Throwable ignored) {}
            error("下载文件损坏：" + reason);
            return;
        }
        notifyCompleted();
        // 清理历史旧缓存（之前版本的 update.apk 留着没用，还占空间）
        try {
            File oldUpdate = Path.cache("update.apk");
            // 当前 file 就是 update.apk，不删；清理其它可能的旧名
            File dir = oldUpdate.getParentFile();
            if (dir != null && dir.isDirectory()) {
                for (File f : dir.listFiles()) {
                    if (f != null && f.getName() != null
                            && f.getName().startsWith("update")
                            && f.getName().endsWith(".apk")
                            && !f.equals(file)) {
                        f.delete();
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 拉起安装
        try {
            FileUtil.installApk(file);
        } catch (Throwable t) {
            notifyError("无法拉起安装器：" + t.getMessage());
        }
        stopSelf();
    }

    /** 和 Updater.verifyApkIntegrity 保持同一份校验逻辑（长度 + ZIP 结构 + Manifest/arsc/dex） */
    private String verifyApkIntegrity(File file) {
        if (file == null || !file.exists() || file.length() <= 0) return "文件不存在或大小为 0";
        long fileLen = file.length();
        long expected = apkAsset != null ? apkAsset.optLong("size", -1) : -1;
        if (expected > 0) {
            double ratio = (double) fileLen / expected;
            if (ratio < 0.90d || ratio > 1.10d) {
                return "长度不匹配（官方 " + expected + "B，实际 " + fileLen + "B）";
            }
        }
        ZipFile zf = null;
        try {
            zf = new ZipFile(file);
            boolean hasManifest = false, hasArsc = false, hasDex = false;
            int count = 0;
            Enumeration<?> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = (ZipEntry) en.nextElement();
                count++;
                String n = e.getName();
                if (n == null) continue;
                if ("AndroidManifest.xml".equals(n)) hasManifest = true;
                else if ("resources.arsc".equals(n)) hasArsc = true;
                else if (n.startsWith("classes") && n.endsWith(".dex")) hasDex = true;
            }
            if (count <= 0) return "ZIP 为空";
            if (!hasManifest) return "缺少 AndroidManifest.xml（可能是错误页）";
            if (!hasArsc) return "缺少 resources.arsc";
            if (!hasDex) return "缺少 classes.dex";
            return null;
        } catch (Throwable t) {
            return "ZIP 校验失败：" + t.getMessage();
        } finally {
            if (zf != null) { try { zf.close(); } catch (Throwable ignored) {} }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopDownload();
        try { nm.cancel(NOTIF_ID); } catch (Throwable ignored) {}
        super.onDestroy();
    }
}
