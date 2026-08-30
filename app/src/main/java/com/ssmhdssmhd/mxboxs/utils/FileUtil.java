package com.ssmhdssmhd.mxboxs.utils;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.StatFs;
import android.text.TextUtils;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import com.ssmhdssmhd.mxboxs.App;
import com.ssmhdssmhd.mxboxs.R;
import com.ssmhdssmhd.mxboxs.impl.Callback;
import com.github.catvod.utils.Path;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.text.DecimalFormat;
import java.util.Enumeration;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class FileUtil {

    public static File getWall(int index) {
        return Path.files("wallpaper_" + index);
    }

    public static File getWallCache() {
        return Path.files("wallpaper_cache");
    }

    public static void openFile(File file) {
        String name = file.getName();
        if (name != null && name.endsWith(".apk")) {
            installApk(file);
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setDataAndType(getShareUri(file), FileUtil.getMimeType(file.getName()));
        App.get().startActivity(intent);
    }

    /**
     * 安装 APK —— 自动清理旧缓存 + 未知来源权限引导 + 多 Intent fallback。
     * <p>
     * 流程：
     * 1) 安装前先清 cache 目录下所有旧 update*.apk（自动清理缓存，不占用户空间）；
     * 2) Android 8.0+ 检查 canRequestPackageInstalls()，无权限则跳设置页让用户手动开，
     *    用户返回后 UpdateDialog / UpdateService 仍会调本方法（APK 已下载好不用重下）；
     * 3) 优先 ACTION_INSTALL_PACKAGE，fallback 到 ACTION_VIEW + "application/vnd.android.package-archive"。
     */
    public static void installApk(File apk) {
        if (apk == null || !apk.exists() || apk.length() == 0) {
            Notify.show(String.format(ResUtil.getString(R.string.update_install_failed), "APK 文件无效"));
            return;
        }
        // ① 自动清理历史旧缓存（保留当前这个 apk 不动，清理同目录下其它 update*.apk）
        try {
            File dir = apk.getParentFile();
            if (dir != null && dir.isDirectory()) {
                for (File f : dir.listFiles()) {
                    if (f != null && f.getName() != null
                            && f.getName().startsWith("update")
                            && f.getName().endsWith(".apk")
                            && !f.equals(apk)) {
                        f.delete();
                    }
                }
            }
        } catch (Throwable ignored) {}

        // ② 未知来源权限引导
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!isCanInstallApk()) {
                Notify.show("请在打开的设置页勾选「允许安装未知应用」，返回后自动继续安装");
                Intent settingsIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                settingsIntent.setData(Uri.parse("package:" + App.get().getPackageName()));
                settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                App.get().startActivity(settingsIntent);
                return;
            }
        }

        // ③ 构建 Intent：ACTION_INSTALL_PACKAGE 优先，ACTION_VIEW 兜底
        Uri apkUri;
        try {
            apkUri = getShareUri(apk);
        } catch (Throwable t) {
            Notify.show("FileProvider 生成 URI 失败：" + t.getMessage());
            return;
        }

        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        } else {
            intent = new Intent(Intent.ACTION_VIEW);
        }
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        // 防被系统过滤掉 —— 显式指定包名
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, App.get().getPackageName());
        }

        try {
            App.get().startActivity(intent);
        } catch (android.content.ActivityNotFoundException anfe) {
            // 某些国产 ROM 没装 PackageInstaller，最后 fallback：pm install
            Notify.show("系统没找到安装器，请用文件管理器手动打开 update.apk");
        } catch (Throwable t) {
            Notify.show(String.format(ResUtil.getString(R.string.update_install_failed), t.getMessage()));
        }
    }

    /** Android 8.0+ 是否允许本应用安装未知来源 APK */
    public static boolean isCanInstallApk() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                return App.get().getPackageManager().canRequestPackageInstalls();
            }
        } catch (Exception ignored) {}
        return true;
    }

    public static void gzipCompress(File target) {
        byte[] buffer = new byte[16384];
        try (FileInputStream is = new FileInputStream(target); GZIPOutputStream os = new GZIPOutputStream(new FileOutputStream(target.getAbsolutePath() + ".gz"))) {
            int read;
            while ((read = is.read(buffer)) > 0) os.write(buffer, 0, read);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            Path.clear(target);
        }
    }

    public static void gzipDecompress(File target, File path) {
        byte[] buffer = new byte[16384];
        try (GZIPInputStream is = new GZIPInputStream(new BufferedInputStream(new FileInputStream(target))); BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(path))) {
            int read;
            while ((read = is.read(buffer)) != -1) os.write(buffer, 0, read);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void zipDecompress(File target, File path) {
        try (ZipFile zip = new ZipFile(target)) {
            Enumeration<?> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = (ZipEntry) entries.nextElement();
                File out = new File(path, entry.getName());
                if (entry.isDirectory()) out.mkdirs();
                else Path.copy(zip.getInputStream(entry), out);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void clearCache(Callback callback) {
        Task.execute(() -> {
            Path.clear(Path.cache());
            App.post(callback::success);
        });
    }

    public static void getCacheSize(Callback callback) {
        Task.execute(() -> {
            String usage = byteCountToDisplaySize(getDirectorySize(Path.cache()));
            App.post(() -> callback.success(usage));
        });
    }

    public static long getDirectorySize(File dir) {
        long size = 0;
        if (dir == null) return 0;
        if (dir.isDirectory()) for (File file : Path.list(dir)) size += getDirectorySize(file);
        else size = dir.length();
        return size;
    }

    public static long getAvailableStorageSpace(File file) {
        try {
            StatFs stat = new StatFs(file.getAbsolutePath());
            return stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
        } catch (Exception e) {
            return 0;
        }
    }

    public static Uri getShareUri(String path) {
        return getShareUri(new File(path.replace("file://", "")));
    }

    public static Uri getShareUri(File file) {
        return FileProvider.getUriForFile(App.get(), App.get().getPackageName() + ".provider", file);
    }

    private static String getMimeType(String fileName) {
        String mimeType = URLConnection.guessContentTypeFromName(fileName);
        return TextUtils.isEmpty(mimeType) ? "*/*" : mimeType;
    }

    public static String byteCountToDisplaySize(long size) {
        if (size <= 0) return ResUtil.getString(R.string.none);
        String[] units = new String[]{"bytes", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }
}
