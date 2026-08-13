package com.ssmhdssmhd.mxboxs.utils;

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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

    // ====================== 内部：解压 + chmod ======================

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

            // 已放置版本标记（ffmpeg 9.0）
            String assetPrefix = DIR_FFMPEG + "/" + abi + "/";
            File ffmpegDst = copyAssetIfChanged(app, assetPrefix + "ffmpeg", new File(dstDir, "ffmpeg"));
            File ffprobeDst = copyAssetIfChanged(app, assetPrefix + "ffprobe", new File(dstDir, "ffprobe"));

            chmod755(ffmpegDst);
            chmod755(ffprobeDst);

            sFFmpegPath = ffmpegDst.getAbsolutePath();
            sFFprobePath = ffprobeDst.getAbsolutePath();
            sInitedOk = Boolean.TRUE;
            return sFFmpegPath;
        }
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
