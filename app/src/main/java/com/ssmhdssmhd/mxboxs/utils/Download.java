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
import okhttp3.Response;

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
            call = OkHttp.newCall(client, url, tag);
            activeCall = call;
            res = call.execute();
            download(res.body().byteStream(), getLength(res));
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

    private void download(InputStream is, double length) throws IOException {
        try (BufferedInputStream input = new BufferedInputStream(is); FileOutputStream os = new FileOutputStream(Path.create(file))) {
            byte[] buffer = new byte[16384];
            int readBytes;
            long totalBytes = 0;
            while ((readBytes = input.read(buffer)) != -1) {
                if (Thread.interrupted()) return;
                totalBytes += readBytes;
                os.write(buffer, 0, readBytes);
                if (length <= 0) continue;
                int progress = (int) (totalBytes / length * 100.0);
                if (callback != null) App.post(() -> callback.progress(progress));
            }
        }
    }

    private double getLength(Response res) {
        try {
            String header = res.header(HttpHeaders.CONTENT_LENGTH);
            return header != null ? Double.parseDouble(header) : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    public interface Callback {

        void progress(int progress);

        void error(String msg);

        void success(File file);
    }
}
