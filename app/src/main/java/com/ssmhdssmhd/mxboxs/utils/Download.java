package com.ssmhdssmhd.mxboxs.utils;

import com.ssmhdssmhd.mxboxs.App;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.google.common.net.HttpHeaders;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.concurrent.Future;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class Download {

    public static final long DEFAULT_TIMEOUT_MS = 20_000L;
    /** APK 下载专属超时：原 10s 对 114MB 大文件过短，国内移动网络 400KB/s 都要将近 5 分钟。
     * v5.5.61 调大为 60_000ms（60s），配合 Github.probeUrls "先测后下载" 把假镜像在 6s 探针阶段就排除，
     * 进入真下载的源已经是被三重校验（类型/长度/ZIP魔术）判过 OK 的，不会再白等。*/
    public static final long APK_DOWNLOAD_TIMEOUT_MS = 60_000L;

    private final File file;
    private final File tmpFile;   // 断点续传用的临时文件（file + ".tmp"）
    private final String url;
    private final long timeoutMs;
    private Callback callback;
    private Future<?> future;
    private String tag;
    private volatile Call activeCall;
    /** v5.7.11：断点续传开关。默认 true —— 如果 file 或 .tmp 已存在且 >0 字节，尝试 Range 续传。 */
    private boolean resumeEnabled = true;

    public static Download create(String url, File file) {
        return new Download(url, file, DEFAULT_TIMEOUT_MS);
    }

    public static Download create(String url, File file, long timeoutMs) {
        return new Download(url, file, timeoutMs);
    }

    public Download(String url, File file) {
        this(url, file, DEFAULT_TIMEOUT_MS);
    }

    public Download(String url, File file, long timeoutMs) {
        this.tag = url;
        this.url = url;
        this.file = file;
        this.tmpFile = new File(file.getAbsolutePath() + ".tmp");
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS;
    }

    public Download tag(String tag) {
        this.tag = tag;
        return this;
    }

    /** v5.7.11：关闭断点续传（用于必须从头下的场景） */
    public Download noResume() {
        this.resumeEnabled = false;
        return this;
    }

    /** 返回临时文件（.tmp）。断点续传时写它，完成后 rename 到正式文件。 */
    public File tmpFile() { return tmpFile; }

    /** 当前已下载的字节数（.tmp 大小，0 表示从头来） */
    public long currentSize() {
        if (tmpFile != null && tmpFile.exists()) return tmpFile.length();
        if (file != null && file.exists()) return file.length();
        return 0;
    }

    public File get() {
        doInBackground();
        return file;
    }

    public void start(Callback callback) {
        this.callback = callback;
        future = Task.submit(this::doInBackground);
    }

    public Download cancel() {
        if (future != null) future.cancel(true);
        OkHttp.cancel(tag);
        Call c = activeCall;
        if (c != null) try { c.cancel(); } catch (Throwable ignored) {}
        activeCall = null;
        future = null;
        return this;
    }

    private void doInBackground() {
        OkHttpClient client = OkHttp.client(true, timeoutMs);
        Call call = null;
        Response res = null;
        boolean appendMode = false;     // 断点续传时用
        long existingBytes = 0;          // 已下载大小（resume 时作为 Range 起点）

        try {
            // ---- 断点续传检查 ----
            if (resumeEnabled) {
                // 优先找 .tmp（上次没下完），其次找正式 file（可能之前 rename 了一半）
                File resumeFrom = tmpFile.exists() ? tmpFile : (file.exists() ? file : null);
                if (resumeFrom != null && resumeFrom.length() > 0) {
                    existingBytes = resumeFrom.length();
                    appendMode = true;
                }
            }

            // ---- 构建 Request ----
            Request.Builder rb = new Request.Builder().url(url).tag(tag);
            if (appendMode && existingBytes > 0) {
                // 断点续传：从 existingBytes 开始拿剩下的
                // 用 Range: bytes=N- （没有尾部）让服务器返回 N 到文件末尾，总字节数 = 服务器声明总大小
                rb.header("Range", "bytes=" + existingBytes + "-");
                // 保留一个额外的 "bytes=0-" 头让部分服务器正确返回 Content-Range: bytes start-end/TOTAL
                rb.header("X-Resume-From", String.valueOf(existingBytes));
            } else {
                // 新下载：Range: bytes=0- 让 GitHub 返回 Content-Range，从而拿到总大小（否则 chunked 没 Content-Length）
                rb.header("Range", "bytes=0-");
            }
            call = client.newCall(rb.build());
            activeCall = call;
            res = call.execute();

            int code = res.code();
            long total = getContentLength(res);
            ResponseBody body = res.body();
            if (body == null) throw new IOException("empty response body (HTTP " + code + ")");

            if (appendMode) {
                // 306 Partial Content = 服务器支持断点续传；200 也可能（部分服务器忽略 Range 头），
                // 但 200 表示服务器没续传、从头发了 —— 此时我们要覆盖写，不能 append
                if (code == 306) {
                    // 真续传：从 existingBytes 后面写
                    downloadAppend(body.byteStream(), existingBytes, total);
                } else {
                    // 服务器不支持 Range 或忽略了 —— 从头覆盖写，删掉 .tmp
                    appendMode = false;
                    existingBytes = 0;
                    Path.clear(tmpFile);
                    downloadFresh(body.byteStream(), total);
                }
            } else {
                downloadFresh(body.byteStream(), total);
            }

            // 下载完成：.tmp → 正式 file
            if (tmpFile.exists()) {
                if (file.exists()) file.delete();
                if (!tmpFile.renameTo(file)) {
                    // rename 失败（跨文件系统等），fallback copy + delete
                    try {
                        java.nio.file.Files.move(tmpFile.toPath(), file.toPath(),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } catch (Throwable t) {
                        // 最后兜底：读 tmp 写 file
                        copyFile(tmpFile, file);
                        tmpFile.delete();
                    }
                }
            }

            if (callback != null) App.post(() -> callback.success(file));
        } catch (Exception e) {
            // 失败时保留 .tmp（下次可以续传），**不要删**——这是断点续传的关键
            // 只有明确 "从头来"（resumeEnabled=false 或 noResume）才清
            if (!resumeEnabled) {
                Path.clear(tmpFile);
                Path.clear(file);
            }
            if (callback != null) {
                String msg = e.getMessage();
                if (msg == null || msg.isEmpty()) msg = (e instanceof java.net.SocketTimeoutException ? "timeout" : e.getClass().getSimpleName());
                final String fmsg = msg + "（" + timeoutMs + "ms 内未响应，已自动快速失败）";
                App.post(() -> callback.error(fmsg));
            } else {
                throw new RuntimeException(e.getMessage(), e);
            }
        } finally {
            if (res != null) try { res.close(); } catch (Throwable ignored) {}
            activeCall = null;
        }
    }

    /** 从头下载：覆写写入 .tmp */
    private void downloadFresh(InputStream is, long totalBytes) throws IOException {
        Path.clear(tmpFile);
        download(is, tmpFile, 0, totalBytes);
    }

    /** 断点续传：用 RandomAccessFile seek 到 existingBytes 后 write，避免 Stream 不支持 append */
    private void downloadAppend(InputStream is, long existingBytes, long totalBytes) throws IOException {
        // 确保 .tmp 已存在（可能上一轮没下完）
        if (!tmpFile.exists()) {
            Path.create(tmpFile);
        }
        // 用 RandomAccessFile 追加写（比 FileOutputStream 的 append 模式更可控）
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(tmpFile, "rw");
            raf.seek(existingBytes);
            byte[] buffer = new byte[16384];
            int readBytes;
            long downloadedBytes = existingBytes;
            long lastReportTime = 0;
            // totalBytes 来自服务器 Content-Range 的 total 部分
            // 如果服务器返回的是剩余大小（total - existingBytes），我们需要加回来
            long realTotal = totalBytes;
            if (totalBytes > 0 && totalBytes < existingBytes) {
                // 服务器返回的是剩余 size，加上已下载
                realTotal = existingBytes + totalBytes;
            }
            while ((readBytes = is.read(buffer)) != -1) {
                if (Thread.interrupted()) return;
                raf.write(buffer, 0, readBytes);
                downloadedBytes += readBytes;

                // 进度回调
                if (realTotal > 0) {
                    int progress = (int) (downloadedBytes * 100L / realTotal);
                    if (callback != null) {
                        final int p = progress;
                        final long d = downloadedBytes;
                        final long t = realTotal;
                        App.post(() -> callback.progress(p, d, t));
                    }
                } else {
                    long now = System.currentTimeMillis();
                    if (now - lastReportTime >= 200) {
                        if (callback != null) {
                            final long d = downloadedBytes;
                            App.post(() -> callback.progress(-1, d, -1));
                        }
                        lastReportTime = now;
                    }
                }
            }
            // 100% final callback
            if (callback != null) {
                final long d = downloadedBytes;
                final long t = realTotal;
                App.post(() -> callback.progress(100, d, t));
            }
        } finally {
            if (raf != null) try { raf.close(); } catch (Throwable ignored) {}
        }
    }

    /** 普通下载（从头开始）：用 FileOutputStream 直接覆写 */
    private void download(InputStream is, File target, long alreadyDownloaded, long totalBytes) throws IOException {
        try (BufferedInputStream input = new BufferedInputStream(is);
             FileOutputStream os = new FileOutputStream(target, alreadyDownloaded > 0)) {
            byte[] buffer = new byte[16384];
            int readBytes;
            long downloadedBytes = alreadyDownloaded;
            long lastReportTime = 0;
            while ((readBytes = input.read(buffer)) != -1) {
                if (Thread.interrupted()) return;
                downloadedBytes += readBytes;
                os.write(buffer, 0, readBytes);

                long now = System.currentTimeMillis();
                if (totalBytes > 0) {
                    int progress = (int) (downloadedBytes * 100L / totalBytes);
                    if (callback != null) {
                        final int p = progress;
                        final long d = downloadedBytes;
                        final long t = totalBytes;
                        App.post(() -> callback.progress(p, d, t));
                    }
                } else if (now - lastReportTime >= 200) {
                    if (callback != null) {
                        final long d = downloadedBytes;
                        App.post(() -> callback.progress(-1, d, -1));
                    }
                    lastReportTime = now;
                }
            }
            if (callback != null && totalBytes > 0) {
                final long d = downloadedBytes;
                final long t = totalBytes;
                App.post(() -> callback.progress(100, d, t));
            } else if (callback != null) {
                final long d = downloadedBytes;
                App.post(() -> callback.progress(100, d, -1));
            }
        }
    }

    private static void copyFile(File src, File dst) throws IOException {
        try (java.io.FileInputStream in = new java.io.FileInputStream(src);
             java.io.FileOutputStream out = new java.io.FileOutputStream(dst)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
    }

    /** 优先从 Content-Range 解析总大小（Range: bytes=0- 会让 GitHub 返回 Content-Range），退化到 Content-Length。 */
    private long getContentLength(Response res) {
        try {
            // Content-Range: bytes 0-1234567/1234567  ← 总大小为 / 后面的数字
            String contentRange = res.header(HttpHeaders.CONTENT_RANGE);
            if (contentRange != null && !contentRange.isEmpty()) {
                int slash = contentRange.lastIndexOf('/');
                if (slash >= 0 && slash < contentRange.length() - 1) {
                    String total = contentRange.substring(slash + 1);
                    if ("*".equals(total.trim())) {
                        // 服务器没给总大小（部分服务器 Range 续传时会这样），fallback Content-Length
                    } else {
                        try {
                            long v = Long.parseLong(total.trim());
                            if (v > 0) return v;
                        } catch (Throwable ignored) {}
                    }
                }
            }
            // 退化：Content-Length
            String cl = res.header(HttpHeaders.CONTENT_LENGTH);
            if (cl != null && !cl.isEmpty()) {
                long v = Long.parseLong(cl.trim());
                if (v > 0) return v;
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    public interface Callback {
        default void progress(int progress, long downloadedBytes, long totalBytes) {
            progress(progress);
        }
        default void progress(int progress) {
        }
        void error(String msg);
        void success(File file);
    }

    public static String formatBytes(long bytes) {
        if (bytes < 0) return "";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024) return String.format(java.util.Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format(java.util.Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024));
        return String.format(java.util.Locale.getDefault(), "%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
