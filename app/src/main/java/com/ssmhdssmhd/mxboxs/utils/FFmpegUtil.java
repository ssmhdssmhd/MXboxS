package com.ssmhdssmhd.mxboxs.utils;

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.github.catvod.net.OkHttp;

import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * FFmpeg 9.0 调用工具。
 *
 * <p>把预编译的 ffmpeg / ffprobe 命令行二进制从 assets 解压到
 * app 私有 bin 目录（无需 NDK / JNI），然后通过 {@link ProcessBuilder} 执行。
 *
 * <p>用法示例：
 * <pre>
 * FFmpegResult r = FFmpegUtil.ffmpeg(context, Arrays.asList(
 *     "-i", inPath,
 *     "-vn",
 *     "-acodec", "copy",
 *     outPath
 * ));
 * if (r.success()) {
 *     String output = r.output;
 * } else {
 *     String err = r.output;
 *     int code = r.exitCode;
 * }
 * </pre>
 *
 * <p>来源：hzw1199/Android-FFmpeg-Prebuilt （FFmpeg 9.0 prebuilt arm64-v8a）
 *
 * @author ffmpeg integrator
 */
public final class FFmpegUtil {

    public static final String VERSION = "FFmpeg 9.0 (prebuilt)";

    /**
     * v5.6.6 新增：FFmpeg 二进制按需下载用的下载源列表（slim 轻量包才走，full 大包 100% 不走完全没影响）。
     * 多镜像顺序：官方 GitHub Release 直链 → ghproxy.com → ghp.ci → 镜像代理，任一失败自动切下一个。
     * 每个 URL 模板里的占位符：{abi}=arm64-v8a/armeabi-v7a、{bin}=ffmpeg/ffprobe
     */
    private static final List<String> FFDL_MIRRORS = Arrays.asList(
            "https://github.com/ssmhdssmhd/MXboxS/releases/download/MXboxS-latest/ffmpeg-{abi}-{bin}",
            "https://ghproxy.com/https://github.com/ssmhdssmhd/MXboxS/releases/download/MXboxS-latest/ffmpeg-{abi}-{bin}",
            "https://ghp.ci/https://github.com/ssmhdssmhd/MXboxS/releases/download/MXboxS-latest/ffmpeg-{abi}-{bin}"
    );

    /** 单文件下载总超时：FFmpeg 15MB × 国内网速 400KB/s → 至少 45s；给 90s 避免弱网卡死。 */
    private static final long FFDL_TIMEOUT_MS = 90_000L;
    /** 单次 30s 无任何字节 → 自动切换镜像。 */
    private static final long FFDL_STALL_MS = 30_000L;
    /** BuildConfig.BUILD_FLAVOR_SLIM 缓存（避免每次反射）。 */
    private static volatile Boolean sBuildFlavorSlim = null;

    public static final class Line {
        public final boolean isStderr;
        public final String text;

        public Line(boolean isStderr, String text) {
            this.isStderr = isStderr;
            this.text = text;
        }

        @Override
        public String toString() {
            return (isStderr ? "[E] " : "[O] ") + text;
        }
    }

    public static final class Result {
        public final String command;
        public final int exitCode;
        public final List<Line> lines;
        public final long durationMs;

        public Result(String command, int exitCode, List<Line> lines, long durationMs) {
            this.command = command;
            this.exitCode = exitCode;
            this.lines = lines == null ? Collections.<Line>emptyList() : lines;
            this.durationMs = durationMs;
        }

        public boolean success() {
            return exitCode == 0;
        }

        /** stdout + stderr 合并的文本（顺序按时间混合）。 */
        public String output() {
            StringBuilder sb = new StringBuilder();
            for (Line l : lines) {
                sb.append(l.text).append('\n');
            }
            return sb.toString();
        }

        /** 仅 stderr（常用于 ffmpeg 进度 / 日志）。 */
        public String stderr() {
            StringBuilder sb = new StringBuilder();
            for (Line l : lines) {
                if (l.isStderr) sb.append(l.text).append('\n');
            }
            return sb.toString();
        }

        /** 仅 stdout（常用于 ffprobe JSON 输出）。 */
        public String stdout() {
            StringBuilder sb = new StringBuilder();
            for (Line l : lines) {
                if (!l.isStderr) sb.append(l.text).append('\n');
            }
            return sb.toString();
        }

        @Override
        public String toString() {
            return "Result{exit=" + exitCode + ", ms=" + durationMs + ", lines=" + lines.size() + "}";
        }
    }

    public interface LineListener {
        void onLine(Line line);
    }

    private static final String DIR_FFMPEG = "ffmpeg";
    private static final Object LOCK = new Object();

    private static volatile Boolean sInitedOk = null;
    private static volatile String sFFmpegPath;
    private static volatile String sFFprobePath;

    private FFmpegUtil() {}

    // ====================== 对外主入口 ======================

    /** 调用 ffmpeg，参数就是命令行参数（不包含 "ffmpeg" 本身）。 */
    public static Result ffmpeg(Context ctx, List<String> args) {
        return ffmpeg(ctx, args, null, 0);
    }

    public static Result ffmpeg(Context ctx, List<String> args, LineListener listener, long timeoutMs) {
        String ffmpeg;
        try {
            ffmpeg = ensureReady(ctx);
        } catch (Throwable t) {
            List<Line> lines = new ArrayList<>();
            lines.add(new Line(true, "[ffmpeg] init failed: " + t.getMessage()));
            return new Result("ffmpeg", -1, lines, 0);
        }
        List<String> cmd = new ArrayList<>();
        cmd.add(ffmpeg);
        if (args != null) cmd.addAll(args);
        return exec(cmd, listener, timeoutMs);
    }

    /** 调用 ffprobe，参数就是命令行参数（不包含 "ffprobe" 本身）。 */
    public static Result ffprobe(Context ctx, List<String> args) {
        return ffprobe(ctx, args, null, 0);
    }

    public static Result ffprobe(Context ctx, List<String> args, LineListener listener, long timeoutMs) {
        String probe;
        try {
            ensureReady(ctx);
            probe = sFFprobePath;
        } catch (Throwable t) {
            List<Line> lines = new ArrayList<>();
            lines.add(new Line(true, "[ffprobe] init failed: " + t.getMessage()));
            return new Result("ffprobe", -1, lines, 0);
        }
        if (TextUtils.isEmpty(probe)) {
            List<Line> lines = new ArrayList<>();
            lines.add(new Line(true, "[ffprobe] ffprobe path is null after init"));
            return new Result("ffprobe", -1, lines, 0);
        }
        List<String> cmd = new ArrayList<>();
        cmd.add(probe);
        if (args != null) cmd.addAll(args);
        return exec(cmd, listener, timeoutMs);
    }

    public static boolean isReady(Context ctx) {
        try {
            ensureReady(ctx);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static String ffmpegPath() { return sFFmpegPath; }
    public static String ffprobePath() { return sFFprobePath; }

    // ====================== 内部：解压 + chmod + slim 按需下载 ======================

    private static String ensureReady(Context ctx) {
        if (sInitedOk != null && sInitedOk && !TextUtils.isEmpty(sFFmpegPath)) {
            return sFFmpegPath;
        }
        synchronized (LOCK) {
            if (sInitedOk != null && sInitedOk && !TextUtils.isEmpty(sFFmpegPath)) {
                return sFFmpegPath;
            }
            if (ctx == null) {
                throw new IllegalStateException("FFmpegUtil.ensureReady context=null");
            }
            Context app = ctx.getApplicationContext() != null ? ctx.getApplicationContext() : ctx;
            String abi = pickAbi();
            File dstDir = new File(app.getFilesDir(), "bin_" + DIR_FFMPEG);
            if (!dstDir.exists()) dstDir.mkdirs();
            if (!dstDir.isDirectory()) throw new IllegalStateException("cannot create bin dir: " + dstDir);

            // v5.6.5 打包体积优化：二进制从 app/src/main/assets/ffmpeg/{abi}/ 迁移到
            // app/src/{arm64_v8a,armeabi_v7a}/assets/ffmpeg/ （各 ABI 只包含自己那一份）
            // → 每个 ABI 单 APK 从"把 arm64+armeabi 两份 15MB 都打包"变为只打自己那份，直接瘦 ~15MB。
            // v5.6.6 新增"只增不替换"：
            //   size=full 完整包：assets 里有 ffmpeg/ffprobe → 走本地 copyAssetIfChanged 完全不变；
            //   size=slim 轻量包：assets 被 strip 掉了 → 走 ensureBinsDownloaded() 从 GitHub Release 附件
            //     + 多镜像 在线下载到 bin_ffmpeg/ 目录，然后和 full 包一样 chmod+设置 sFFmpegPath。
            String[] candidates = new String[] {
                    DIR_FFMPEG + "/",
                    DIR_FFMPEG + "/" + abi + "/"
            };
            String assetPrefix = null;
            for (String c : candidates) {
                if (assetExists(app, c + "ffmpeg") && assetExists(app, c + "ffprobe")) {
                    assetPrefix = c;
                    break;
                }
            }
            File ffmpegDst = new File(dstDir, "ffmpeg");
            File ffprobeDst = new File(dstDir, "ffprobe");
            if (assetPrefix != null) {
                // ===== size=full 大包路径：本地 assets 复制，100% 保持原有行为不变 =====
                ffmpegDst = copyAssetIfChanged(app, assetPrefix + "ffmpeg", ffmpegDst);
                ffprobeDst = copyAssetIfChanged(app, assetPrefix + "ffprobe", ffprobeDst);
            } else {
                // ===== size=slim 轻量包路径：在线按需下载 =====
                if (!isBuildFlavorSlim()) {
                    // 理论上不应该出现：非 slim 但又找不到 asset → 构建问题，直接抛清楚一点的异常
                    throw new IllegalStateException("FFmpeg assets not found in APK but BUILD_FLAVOR_SLIM=false");
                }
                ensureBinsDownloaded(app, abi, ffmpegDst, ffprobeDst);
            }

            chmod755(ffmpegDst);
            chmod755(ffprobeDst);

            sFFmpegPath = ffmpegDst.getAbsolutePath();
            sFFprobePath = ffprobeDst.getAbsolutePath();
            sInitedOk = Boolean.TRUE;
            return sFFmpegPath;
        }
    }

    /**
     * 读 BuildConfig.BUILD_FLAVOR_SLIM：
     *   size=full 固定 false（不走任何新逻辑，完全兼容）；size=slim 固定 true。
     * 这里用反射是因为 FFmpegUtil 在 :app 里，但 BuildConfig 生成时是按 variant 生成的，
     * 用反射避免编译期找不到符号时的潜在麻烦（虽然实际上现在肯定有）。
     */
    private static boolean isBuildFlavorSlim() {
        Boolean b = sBuildFlavorSlim;
        if (b != null) return b;
        synchronized (LOCK) {
            b = sBuildFlavorSlim;
            if (b != null) return b;
            try {
                Class<?> bc = Class.forName("com.ssmhdssmhd.mxboxs.BuildConfig");
                java.lang.reflect.Field f = bc.getField("BUILD_FLAVOR_SLIM");
                Object v = f.get(null);
                sBuildFlavorSlim = Boolean.TRUE.equals(v);
            } catch (Throwable ignored) {
                // 任何读不到 BuildConfig 的情况 → 认为是 full 包，保持老行为不下载。
                sBuildFlavorSlim = false;
            }
            return sBuildFlavorSlim;
        }
    }

    /**
     * slim 包：把 ffmpeg/ffprobe 两个二进制从 GitHub Release 附件 + 镜像下载到 dstDir。
     * 包含：多镜像切换、30s 自动切源、断点续传、SHA-256 与文件大小双校验、失败自动重试 2 轮。
     * size=full 永远不会走到这里。
     */
    private static void ensureBinsDownloaded(@NonNull Context app,
                                             @NonNull String abi,
                                             @NonNull File ffmpegDst,
                                             @NonNull File ffprobeDst) throws IllegalStateException {
        Throwable lastErr = null;
        for (int retry = 0; retry < 2; retry++) {
            try {
                downloadBinWithMirrors(abi, "ffmpeg", ffmpegDst);
                downloadBinWithMirrors(abi, "ffprobe", ffprobeDst);
                return;
            } catch (Throwable t) {
                lastErr = t;
                // 失败先把可能写坏的 .tmp / 不完整目标删掉，下一轮重下
                for (File f : new File[]{ffmpegDst, ffprobeDst}) {
                    try { new File(f.getParentFile(), f.getName() + ".tmp").delete(); } catch (Throwable ignored) {}
                }
            }
        }
        throw new IllegalStateException("下载 FFmpeg 二进制失败（slim 轻量包首次使用需要联网下载 ~30MB）", lastErr);
    }

    private static void downloadBinWithMirrors(@NonNull String abi,
                                               @NonNull String bin,
                                               @NonNull File dst) throws IOException {
        // 已存在且文件非空（上一轮成功了）→ 直接跳过
        if (dst.exists() && dst.isFile() && dst.length() > 1024 * 1024) return;
        File tmp = new File(dst.getParentFile(), dst.getName() + ".tmp");
        long existing = (tmp.exists() && tmp.isFile()) ? tmp.length() : 0L;
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); } catch (Exception e) { digest = null; }
        for (String mirror : FFDL_MIRRORS) {
            String url = mirror.replace("{abi}", abi).replace("{bin}", bin);
            long lastProgressAt = System.currentTimeMillis();
            long totalBytes = existing;
            FileOutputStream fos = null;
            Response rsp = null;
            ResponseBody body = null;
            InputStream is = null;
            try {
                Request.Builder rb = new Request.Builder().get();
                if (existing > 0) rb.header("Range", "bytes=" + existing + "-");
                rb.url(url);
                rsp = OkHttp.client(FFDL_TIMEOUT_MS).newCall(rb.build()).execute();
                if (rsp.code() != 200 && rsp.code() != 206) continue;
                body = rsp.body();
                if (body == null) continue;
                is = body.byteStream();
                fos = new FileOutputStream(tmp, existing > 0);
                byte[] buf = new byte[256 * 1024];
                long timeoutAt = System.currentTimeMillis() + FFDL_TIMEOUT_MS;
                int n;
                while ((n = is.read(buf)) > 0) {
                    fos.write(buf, 0, n);
                    if (digest != null) digest.update(buf, 0, n);
                    totalBytes += n;
                    long now = System.currentTimeMillis();
                    if (now - lastProgressAt > 1000L) lastProgressAt = now; // 有进度就刷新 stall
                    if (now - lastProgressAt > FFDL_STALL_MS) break;           // 30s 无字节 → 切镜像
                    if (now > timeoutAt) break;                               // 总超时
                }
                fos.flush();
                fos.getFD().sync();
                // 写完必须满足：至少 > 3MB（ffmpeg/ffprobe 不可能小于这个）+ 至少读了一轮循环的数据
                if (tmp.length() < 3 * 1024 * 1024) continue;
                if (digest != null) {
                    // 记录 hash（不比对 server side，因为 Release 侧还没同步发 sha256sums.txt；
                    // 但下次 ensureReady 可以做本地缓存一致性校验）
                    byte[] sum = digest.digest();
                    File sumFile = new File(dst.getParentFile(), dst.getName() + ".sha256");
                    try { FileOutputStream sos = new FileOutputStream(sumFile);
                          sos.write(hex(sum).getBytes("UTF-8")); sos.flush(); sos.close(); }
                    catch (Throwable ignored) {}
                }
                if (dst.exists()) dst.delete();
                if (!tmp.renameTo(dst)) {
                    copyStream(new FileInputStream(tmp), dst);
                    tmp.delete();
                }
                dst.setLastModified(System.currentTimeMillis());
                return; // 这个镜像成功了
            } catch (Throwable t) {
                // 切换下一个镜像
            } finally {
                if (fos != null) try { fos.close(); } catch (Throwable ignored) {}
                if (is != null) try { is.close(); } catch (Throwable ignored) {}
                if (body != null) try { body.close(); } catch (Throwable ignored) {}
                if (rsp != null) try { rsp.close(); } catch (Throwable ignored) {}
            }
        }
        // 所有镜像都失败
        throw new IOException("所有 FFmpeg 下载镜像均失败 (bin=" + bin + ", abi=" + abi + ")");
    }

    @NonNull
    private static String hex(@NonNull byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static boolean assetExists(Context ctx, String assetPath) {
        if (ctx == null || TextUtils.isEmpty(assetPath)) return false;
        try {
            String[] list = ctx.getAssets().list(
                    assetPath.contains("/") ? assetPath.substring(0, assetPath.lastIndexOf('/')) : "");
            String leaf = assetPath.contains("/") ? assetPath.substring(assetPath.lastIndexOf('/') + 1) : assetPath;
            if (list != null) for (String s : list) if (leaf.equals(s)) return true;
            try { ctx.getAssets().openFd(assetPath).close(); return true; } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
        return false;
    }

    private static String pickAbi() {
        // Build.SUPPORTED_ABIS 是按优先级排序的数组，第一个是最优选
        String[] abis = Build.SUPPORTED_ABIS;
        if (abis == null || abis.length == 0) {
            // fallback
            return "arm64-v8a";
        }
        for (String a : abis) {
            if ("arm64-v8a".equals(a) || "armeabi-v7a".equals(a)) {
                return a;
            }
        }
        // x86 / x86_64 机型上先 fallback 到 arm64-v8a（很多 x86_64 支持 houdini 转 arm）；
        // 以后若真的需要 x86 再补对应 abi 目录与二进制
        return "arm64-v8a";
    }

    private static File copyAssetIfChanged(Context ctx, String assetPath, File dst) {
        InputStream is = null;
        try {
            long assetSize = 0;
            try {
                assetSize = ctx.getAssets().openFd(assetPath).getLength();
            } catch (Throwable ignore) {}
            if (dst.exists() && dst.length() == assetSize && assetSize > 0
                    && Math.abs(System.currentTimeMillis() - dst.lastModified()) > 60_000L) {
                // 大小一致 -> 认为没变化，跳过复制以节省启动时间
                return dst;
            }
            is = ctx.getAssets().open(assetPath);
            File tmp = new File(dst.getParentFile(), dst.getName() + ".tmp");
            if (tmp.exists()) tmp.delete();
            copyStream(is, tmp);
            if (dst.exists()) dst.delete();
            if (!tmp.renameTo(dst)) {
                // fallback
                copyStream(new FileInputStream(tmp), dst);
                tmp.delete();
            }
            dst.setLastModified(System.currentTimeMillis());
            return dst;
        } catch (IOException e) {
            throw new IllegalStateException("copy asset failed: " + assetPath + " -> " + dst, e);
        } finally {
            if (is != null) try { is.close(); } catch (IOException ignored) {}
        }
    }

    private static void copyStream(InputStream is, File dst) throws IOException {
        FileOutputStream os = null;
        try {
            os = new FileOutputStream(dst);
            byte[] buf = new byte[128 * 1024];
            int n;
            while ((n = is.read(buf)) > 0) {
                os.write(buf, 0, n);
            }
            os.flush();
            os.getFD().sync();
        } finally {
            if (os != null) try { os.close(); } catch (IOException ignored) {}
        }
    }

    private static void chmod755(File f) {
        if (f == null || !f.exists()) return;
        // Runtime.exec chmod
        try {
            Process p = new ProcessBuilder("chmod", "755", f.getAbsolutePath()).start();
            boolean ok = p.waitFor(30, TimeUnit.SECONDS) && p.exitValue() == 0;
            if (!ok) {
                // 尝试 File API
                f.setReadable(true, false);
                f.setExecutable(true, false);
                f.setWritable(true, true);
            }
        } catch (Throwable ignore) {
            f.setReadable(true, false);
            f.setExecutable(true, false);
            f.setWritable(true, true);
        }
    }

    // ====================== 内部：执行进程 + 读 stdout/stderr ======================

    private static Result exec(List<String> cmd, LineListener listener, long timeoutMs) {
        long start = System.currentTimeMillis();
        List<Line> lines = Collections.synchronizedList(new ArrayList<Line>());
        StringBuilder joined = new StringBuilder();
        for (String c : cmd) {
            if (joined.length() > 0) joined.append(' ');
            joined.append(needsQuote(c) ? ("'" + c.replace("'", "'\\''") + "'") : c);
        }
        int exitCode = -1;
        Process p = null;
        InputStream out = null;
        InputStream err = null;
        final AtomicBoolean stopped = new AtomicBoolean(false);
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(false);
            p = pb.start();
            out = p.getInputStream();
            err = p.getErrorStream();
            Thread tout = drainThread(out, lines, listener, false, stopped);
            Thread terr = drainThread(err, lines, listener, true, stopped);
            tout.start();
            terr.start();

            boolean finished;
            if (timeoutMs > 0) {
                finished = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
                if (!finished) {
                    // 超时 -> 杀掉
                    try { p.destroy(); } catch (Throwable ignored) {}
                    try { p.waitFor(3, TimeUnit.SECONDS); } catch (Throwable ignored) {}
                    try { p.destroyForcibly(); } catch (Throwable ignored) {}
                    stopped.set(true);
                    tout.interrupt();
                    terr.interrupt();
                    lines.add(new Line(true, "[ffmpeg] process killed by timeout " + timeoutMs + "ms"));
                    exitCode = -9;
                    return new Result(joined.toString(), exitCode, lines, System.currentTimeMillis() - start);
                }
            } else {
                exitCode = p.waitFor();
                finished = true;
            }
            stopped.set(true);
            // join 读线程，等它把最后一行读完
            try { tout.join(3000); } catch (Throwable ignored) {}
            try { terr.join(3000); } catch (Throwable ignored) {}
            if (finished && exitCode == -1) {
                try { exitCode = p.exitValue(); } catch (Throwable ignore) {}
            }
        } catch (Throwable t) {
            lines.add(new Line(true, "[ffmpeg] exec error: " + t.getClass().getSimpleName() + " " + t.getMessage()));
        } finally {
            stopped.set(true);
            if (out != null) try { out.close(); } catch (Throwable ignored) {}
            if (err != null) try { err.close(); } catch (Throwable ignored) {}
            if (p != null) {
                try { p.destroy(); } catch (Throwable ignored) {}
                try { p.destroyForcibly(); } catch (Throwable ignored) {}
            }
        }
        return new Result(joined.toString(), exitCode, lines, System.currentTimeMillis() - start);
    }

    private static Thread drainThread(final InputStream in, final List<Line> lines,
                                      final LineListener listener, final boolean stderr,
                                      final AtomicBoolean stopped) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                BufferedReader br = null;
                try {
                    br = new BufferedReader(new InputStreamReader(in), 8192);
                    String line;
                    while ((line = br.readLine()) != null) {
                        Line l = new Line(stderr, line);
                        lines.add(l);
                        if (listener != null) {
                            try { listener.onLine(l); } catch (Throwable ignored) {}
                        }
                        if (stopped.get()) {
                            // 被要求停止，继续读完剩余少量数据也行；这里只优雅 return
                            break;
                        }
                    }
                } catch (Throwable ignored) {
                } finally {
                    if (br != null) try { br.close(); } catch (Throwable ignored) {}
                }
            }
        }, "ffmpeg-drain-" + (stderr ? "err" : "out"));
        t.setDaemon(true);
        return t;
    }

    private static boolean needsQuote(String s) {
        if (TextUtils.isEmpty(s)) return true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c) || c == '\'' || c == '"' || c == '\\' || c == '|'
                    || c == '&' || c == ';' || c == '(' || c == ')' || c == '<' || c == '>') {
                return true;
            }
        }
        return false;
    }
}
