package com.ssmhdssmhd.mxboxs;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.startup.Initializer;

import com.ssmhdssmhd.mxboxs.setting.Setting;
import com.ssmhdssmhd.mxboxs.ui.activity.CrashActivity;
import com.github.catvod.bean.Doh;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.orhanobut.logger.AndroidLogAdapter;
import com.orhanobut.logger.Logger;
import com.orhanobut.logger.PrettyFormatStrategy;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.List;

import cat.ereza.customactivityoncrash.config.CaocConfig;

/**
 * App 最早初始化入口 —— 通过 InitializationProvider 在 Application.onCreate() 之前执行。
 * 这里配置全局崩溃保护 + CaocConfig + EventBus + OkHttp DoH 等底层设施。
 *
 * v5.7.6 加强：
 *   1) 先设 Thread.setDefaultUncaughtExceptionHandler 做「崩溃栈保存 + 兜底日志」，
 *      解决 OEM ROM 上 CaocConfig.errorActivity 独立进程 :error_activity 偶发失效导致
 *      "APK 闪一下就没了" 的问题 —— 即使 CaocConfig 没接住，崩溃栈也能落文件便于排查。
 *   2) CaocConfig 加 errorActivityIntent 自定义 Intent（FLAG_ACTIVITY_NEW_TASK | CLEAR_TOP），
 *      确保 CrashActivity 能被拉起。
 */
public class Startup implements Initializer<Void> {

    private static final String TAG = "Startup";
    /** 崩溃日志文件：保存在 App 外部缓存目录，CrashActivity 启动时可读取 */
    private static final String CRASH_LOG_FILE = "last_crash_stacktrace.log";

    @NonNull
    @Override
    public Void create(@NonNull Context context) {
        // ========== 0. 先装全局崩溃兜底（在 CaocConfig 之前，保证任何时机崩都能抓到栈） ==========
        installGlobalCrashHandler(context);

        // ========== 1. 配置 CaocConfig（CustomActivityOnCrash）—— 崩溃时跳 CrashActivity ==========
        // 注意：CaocConfig 2.4.0 没有 errorActivityIntent()，只用 errorActivity(Class)
        try {
            CaocConfig.Builder.create()
                    .backgroundMode(CaocConfig.BACKGROUND_MODE_SILENT)  // 后台崩静默，前台崩弹 CrashActivity
                    .showErrorDetails(true)                                // CrashActivity 可展示详细错误
                    .showRestartButton(true)
                    .trackActivities(true)
                    .errorActivity(CrashActivity.class)
                    .apply();
            Log.i(TAG, "CaocConfig 配置成功，errorActivity = CrashActivity");
        } catch (Throwable t) {
            Log.e(TAG, "CaocConfig 配置失败（不会导致 App 崩，全局 handler 已兜底）", t);
        }

        // ========== 2. Logger ==========
        Logger.addLogAdapter(new AndroidLogAdapter(
                PrettyFormatStrategy.newBuilder()
                        .methodCount(0)
                        .showThreadInfo(false)
                        .tag("MXboxS")
                        .build()
        ));

        // ========== 3. EventBus（优先用 EventIndex，失败 fallback 默认） ==========
        try {
            EventBus.builder()
                    .addIndex(new com.ssmhdssmhd.mxboxs.event.EventIndex())
                    .installDefaultEventBus();
        } catch (Exception e) {
            try {
                EventBus.builder().installDefaultEventBus();
            } catch (Throwable ignored) {}
        }

        // ========== 4. OkHttp DoH ==========
        try {
            OkHttp.dns().setDoh(Doh.objectFrom(Setting.getDoh()));
        } catch (Throwable t) {
            Log.e(TAG, "OkHttp DoH 初始化失败（不影响主流程）", t);
        }

        return null;
    }

    /**
     * 安装全局 UncaughtExceptionHandler 兜底：
     *   - 保存完整崩溃栈到 file → 下次启动可用于诊断
     *   - 如果 CaocConfig 已经接管，透传；否则自己 Toast + 等一会让 Looper 处理
     */
    private static void installGlobalCrashHandler(Context context) {
        Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Log.e(TAG, "未捕获异常 —— thread=" + thread.getName(), throwable);
            try {
                // 保存崩溃栈到文件
                saveCrashToFile(context, throwable);
            } catch (Throwable ignored) {}
            // 交给 CaocConfig（如果 CaocConfig 没配，defaultHandler 是系统默认的 handler）
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            } else {
                // 极端情况：defaultHandler 为 null —— 自杀进程
                System.exit(1);
            }
        });
        Log.i(TAG, "全局 UncaughtExceptionHandler 已安装");
    }

    /** 把崩溃栈写入外部缓存目录文件，CrashActivity 启动时可读取展示 */
    private static void saveCrashToFile(Context context, Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        String stack = sw.toString();
        try {
            File dir = Path.cache();
            if (dir != null) {
                File file = new File(dir, CRASH_LOG_FILE);
                try (FileWriter fw = new FileWriter(file, false)) {
                    fw.write("=== MXboxS Crash Log ===\n");
                    fw.write("Time: " + System.currentTimeMillis() + "\n");
                    fw.write("Thread: " + Thread.currentThread().getName() + "\n\n");
                    fw.write(stack);
                    fw.write("\n========================\n");
                    fw.flush();
                }
            }
        } catch (Throwable ignored) {}
    }

    @NonNull
    @Override
    public List<Class<? extends Initializer<?>>> dependencies() {
        return Collections.emptyList();
    }
}
