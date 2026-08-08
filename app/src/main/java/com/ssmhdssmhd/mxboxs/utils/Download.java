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
import java.util.concurrent.Future;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class Download {

    public static final long DEFAULT_TIMEOUT_MS = 20_000L;
    /** APK 下载专属「快速失败」短超时：让 5.5.46 客户端在 ghproxy.com 30s 超时前自动切源。 */
    public static final long APK_DOWNLOAD_TIMEOUT_MS = 10_000L;

    private final File file;
    private final String url;
    private final long timeoutMs;
    private Callback callback;
    private Future<?> future;
    private String tag;
    private volatile Call activeCall;

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
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS;
    }

    public Download tag(String tag) {
        this.tag = tag;
        return this;
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
        try {
            // 加 Range: bytes=0- 头：强制 GitHub browser_download_url 返回 Content-Range，从而拿到 APK 总大小
            // 没有这个头 GitHub 会走 chunked 编码，响应体没有 Content-Length，getLength() 返回 -1，进度永远 0%
            Request request = new Request.Builder()
                    .url(url)
                    .tag(tag)
                    .header("Range", "bytes=0-")
                    .build();
            call = client.newCall(request);
            activeCall = call;
            res = call.execute();
            long total = getContentLength(res);
            download(res.body().byteStream(), total);
            if (callback != null) App.post(() -> callback.success(file));
        } catch (Exception e) {
            Path.clear(file);
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

    private void download(InputStream is, long totalBytes) throws IOException {
        try (BufferedInputStream input = new BufferedInputStream(is); FileOutputStream os = new FileOutputStream(Path.create(file))) {
            byte[] buffer = new byte[16384];
            int readBytes;
            long downloadedBytes = 0;
            long lastReportTime = 0;
            while ((readBytes = input.read(buffer)) != -1) {
                if (Thread.interrupted()) return;
                downloadedBytes += readBytes;
                os.write(buffer, 0, readBytes);

                // 进度回调策略：
                // 1) 如果已知 totalBytes，计算百分比；
                // 2) 如果未知 totalBytes，每 200ms 回调一次已下载字节数（给 UI 显示「已下载 X.X MB」）
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
            // 下载完成：100% 回调一次，确保 UI 显示完成状态
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

    /** 优先从 Content-Range 解析总大小（Range: bytes=0- 会让 GitHub 返回 Content-Range），退化到 Content-Length。 */
    private long getContentLength(Response res) {
        try {
            // Content-Range: bytes 0-1234567/1234567  ← 总大小为 / 后面的数字
            String contentRange = res.header(HttpHeaders.CONTENT_RANGE);
            if (contentRange != null && !contentRange.isEmpty()) {
                int slash = contentRange.lastIndexOf('/');
                if (slash >= 0 && slash < contentRange.length() - 1) {
                    String total = contentRange.substring(slash + 1);
                    try {
                        long v = Long.parseLong(total.trim());
                        if (v > 0) return v;
                    } catch (Throwable ignored) {}
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

        /**
         * 进度回调（新接口，带字节数信息）。
         *
         * @param progress 百分比 0-100，若 totalBytes=-1 则为 -1 表示总大小未知
         * @param downloadedBytes 已下载字节数
         * @param totalBytes 总字节数，-1 表示未知（服务器未返回 Content-Length/Range）
         */
        default void progress(int progress, long downloadedBytes, long totalBytes) {
            // 默认回退：只调旧的 int progress(int) 接口，方便旧代码兼容
            progress(progress);
        }

        /** 旧接口：仅百分比，保留兼容 */
        default void progress(int progress) {
        }

        void error(String msg);

        void success(File file);
    }

    /** 工具方法：将字节数格式化为 "X.X MB" / "X.X GB" */
    public static String formatBytes(long bytes) {
        if (bytes < 0) return "";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024) return String.format(java.util.Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format(java.util.Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024));
        return String.format(java.util.Locale.getDefault(), "%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
