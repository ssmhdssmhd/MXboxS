package com.ssmhdssmhd.mxboxs.utils;

import android.content.Context;
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

    /** SharedPreferences key：存"用户授权返回后要自动安装的 APK 路径" */
    private static final String PREF = "update_prefs";
    private static final String KEY_PENDING_APK = "pending_install_apk_path";

    /**
     * 安装 APK —— 自动清理旧缓存 + 未知来源权限引导 + 多 Intent fallback + 权限返回自动续接。
     * <p>
     * 流程：
     * 1) 安装前先清 cache 目录下所有旧 update*.apk（自动清理缓存，不占用户空间）；
     * 2) Android 8.0+ 检查 canRequestPackageInstalls()，无权限则：
     *    a. 把 apk.getAbsolutePath() 存到 SharedPreferences（KEY_PENDING_APK）；
     *    b. 跳设置页让用户开启「允许安装未知应用」；
     *    c. 用户返回 App 后由 App.onActivityResumed() 自动检查并续接（见 onResumePendingInstallIfAny）；
     * 3) 优先 ACTION_INSTALL_PACKAGE，fallback 到 ACTION_VIEW + "application/vnd.android.package-archive"；
     * 4) 国产 ROM（小米/HyperOS、OPPO/ColorOS）对 PackageInstaller URI 权限有白名单限制，
     *    额外用 grantUriPermission 兜底。
     */
    public static void installApk(File apk) {
        if (apk == null || !apk.exists() || apk.length() == 0) {
            Notify.show("APK 文件无效");
            return;
        }

        // ① 自动清理历史旧缓存（保留当前这个 apk 不动）
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

        // ② 未知来源权限引导 —— 存路径 + 跳设置页，授权返回后自动续接
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!isCanInstallApk()) {
                // 先存待安装路径
                try {
                    android.content.SharedPreferences sp = App.get().getSharedPreferences(PREF, Context.MODE_PRIVATE);
                    sp.edit().putString(KEY_PENDING_APK, apk.getAbsolutePath()).apply();
                } catch (Throwable ignored) {}

                Notify.show("请开启「允许安装未知应用」，返回后自动继续安装");
                Intent settingsIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                settingsIntent.setData(Uri.parse("package:" + App.get().getPackageName()));
                settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                App.get().startActivity(settingsIntent);
                return; // 等用户回来
            }
        }

        // 清掉旧的 pending（如果这次要装的就是 pending 那个，清掉避免重复触发）
        try {
            android.content.SharedPreferences sp = App.get().getSharedPreferences(PREF, Context.MODE_PRIVATE);
            sp.edit().remove(KEY_PENDING_APK).apply();
        } catch (Throwable ignored) {}

        // ③ 构建 Intent + 拉起安装器
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
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, App.get().getPackageName());
        }

        // 国产 ROM 兜底：显式 grantUriPermission 给 PackageInstaller
        try {
            String installerPkg = "com.android.packageinstaller";
            App.get().grantUriPermission(installerPkg, apkUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Throwable ignored) {}

        try {
            App.get().startActivity(intent);
        } catch (android.content.ActivityNotFoundException anfe) {
            Notify.show("未找到系统安装器，请用文件管理器手动打开 " + apk.getName());
        } catch (Throwable t) {
            Notify.show("拉起安装器失败：" + t.getMessage());
        }
    }

    /**
     * 权限返回自动续接 —— 由 App.onActivityResumed() 调用。
     * 条件：SharedPreferences 里有 KEY_PENDING_APK + 当前已获得安装权限 + 文件还在。
     */
    public static void onResumePendingInstallIfAny() {
        try {
            android.content.SharedPreferences sp = App.get().getSharedPreferences(PREF, Context.MODE_PRIVATE);
            String path = sp.getString(KEY_PENDING_APK, null);
            if (path == null) return;
            File apk = new File(path);
            if (!apk.exists() || apk.length() <= 0) {
                // 文件没了，清掉 pending
                sp.edit().remove(KEY_PENDING_APK).apply();
                return;
            }
            if (!isCanInstallApk()) return; // 用户还没授权，下次回来再检查
            // 已授权 + 文件存在 → 清 pending + 自动安装
            sp.edit().remove(KEY_PENDING_APK).apply();
            Notify.show("检测到已授权，正在自动安装…");
            // 延迟 500ms 等当前 Activity resume 完
            App.post(() -> installApk(apk), 500);
        } catch (Throwable ignored) {}
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
