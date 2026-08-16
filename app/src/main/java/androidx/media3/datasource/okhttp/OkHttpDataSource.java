package androidx.media3.datasource.okhttp;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.HttpDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 基于 okhttp3 的 HttpDataSource 实现（替代原先的 stub）。
 * <p>
 * 复用传入 {@link OkHttpClient} 的全部配置，让 ExoPlayer 播放 m3u8 / mp4 等直链时能够：
 * <ul>
 *   <li>信任所有 SSL 证书（OkHttp 的 trustAllCertificates 生效，解决自签名 / 过期证书）</li>
 *   <li>使用 OkHttp 自定义 DNS（DoH 等，规避 DNS 污染）</li>
 *   <li>走 OkHttp 拦截器（AuthInterceptor / RequestInterceptor 等，支持 token 注入）</li>
 * </ul>
 * <p>
 * 修复 stub 把 OkHttpClient 丢弃导致 ExoPlayer 退化为 DefaultHttpDataSource 的问题。
 */
public class OkHttpDataSource extends BaseDataSource implements HttpDataSource {

    private final OkHttpClient client;
    private final Map<String, String> defaultRequestProperties;
    private final Map<String, String> requestProperties;
    // v5.6.8 新增：playlist 级 Referer 推导缓存（由 Factory 传入，跨域 TS 段 open() 时用）
    private final @Nullable String playlistUrl;
    private final @Nullable String playlistOriginReferer;
    private final @Nullable String playlistHostPort;

    private @Nullable Response response;
    private @Nullable ResponseBody responseBody;
    private @Nullable InputStream responseStream;

    private boolean opened;

    /**
     * 工厂类。API 与原 stub 完全一致，调用方无需修改。
     */
    public static final class Factory implements HttpDataSource.Factory {

        private OkHttpClient client;
        private final Map<String, String> defaultRequestProperties = new HashMap<>();
        private @Nullable String userAgent;
        // v5.6.8 新增：playlist（m3u8/mpd）的原始 URL，用于推断跨域 TS / chunk 段请求时的 Referer。
        // 之前的 Referer 是合并时塞进 defaultRequestProperties，但它可能是 playlist 推导的 DIRECTORY 版
        // （scheme://host:port/path_dir/），当请求的是另一个域名（如 cdn.hls.one）的绝对段 URL 时，
        // 很多 CDN 对 Referer 带第三方 path 敏感；因此跨域请求时动态切换为 playlist 的 ORIGIN 版
        // （scheme://host:port/），这是浏览器标准行为，CDN 鉴权兼容性最高。
        private @Nullable String playlistUrl;
        private @Nullable String playlistOriginReferer;
        private @Nullable String playlistHostPort;

        public Factory() {
            // 允许无参构造（兼容旧代码）
        }

        public Factory(OkHttpClient client) {
            this.client = client;
        }

        /** v5.6.8 新增：告诉 Factory 顶层播放的 playlist URL，用于跨域 TS/chunk 段动态修正 Referer。 */
        @androidx.annotation.NonNull
        public Factory setPlaylistUrl(@Nullable String playlistUrl) {
            this.playlistUrl = playlistUrl;
            if (!android.text.TextUtils.isEmpty(playlistUrl)) {
                this.playlistOriginReferer = com.ssmhdssmhd.mxboxs.utils.UrlUtil.inferOriginReferer(playlistUrl);
                try {
                    android.net.Uri pu = android.net.Uri.parse(playlistUrl);
                    String h = pu.getHost();
                    if (h != null) {
                        int p = pu.getPort();
                        playlistHostPort = (p > 0) ? (h + ":" + p) : h;
                    }
                } catch (Throwable ignored) { playlistHostPort = null; }
            } else {
                this.playlistOriginReferer = null;
                this.playlistHostPort = null;
            }
            return this;
        }

        @NonNull
        @Override
        public Factory setDefaultRequestProperties(Map<String, String> defaultRequestProperties) {
            this.defaultRequestProperties.clear();
            if (defaultRequestProperties != null) {
                this.defaultRequestProperties.putAll(defaultRequestProperties);
            }
            return this;
        }

        public Factory setUserAgent(@Nullable String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        @NonNull
        @Override
        public OkHttpDataSource createDataSource() {
            OkHttpClient c = client;
            if (c == null) c = com.github.catvod.net.OkHttp.player();
            OkHttpDataSource ds = new OkHttpDataSource(c, defaultRequestProperties,
                    playlistUrl, playlistOriginReferer, playlistHostPort);
            if (userAgent != null && !userAgent.isEmpty()) {
                ds.setRequestProperty("User-Agent", userAgent);
            }
            return ds;
        }
    }

    public OkHttpDataSource(OkHttpClient client) {
        this(client, Collections.emptyMap(), null, null, null);
    }

    public OkHttpDataSource(OkHttpClient client, Map<String, String> defaultRequestProperties) {
        this(client, defaultRequestProperties, null, null, null);
    }

    OkHttpDataSource(OkHttpClient client, Map<String, String> defaultRequestProperties,
                     @Nullable String playlistUrl,
                     @Nullable String playlistOriginReferer,
                     @Nullable String playlistHostPort) {
        super(true);
        this.client = client;
        this.defaultRequestProperties = defaultRequestProperties != null ? defaultRequestProperties : Collections.emptyMap();
        this.requestProperties = new HashMap<>();
        this.playlistUrl = playlistUrl;
        this.playlistOriginReferer = playlistOriginReferer;
        this.playlistHostPort = playlistHostPort;
    }

    // ---------------- HttpDataSource ----------------

    @Override
    public void setRequestProperty(@NonNull String name, @NonNull String value) {
        requestProperties.put(name, value);
    }

    @Override
    public void clearRequestProperty(@NonNull String name) {
        requestProperties.remove(name);
    }

    @Override
    public void clearAllRequestProperties() {
        requestProperties.clear();
    }

    @Override
    public int getResponseCode() {
        return response == null ? -1 : response.code();
    }

    @NonNull
    @Override
    public Map<String, List<String>> getResponseHeaders() {
        if (response == null) return Collections.emptyMap();
        return response.headers().toMultimap();
    }

    @Nullable
    @Override
    public Uri getUri() {
        if (response == null) return null;
        return Uri.parse(response.request().url().toString());
    }

    // ---------------- DataSource ----------------

    @Override
    public long open(@NonNull DataSpec dataSpec) throws HttpDataSourceException {
        close();
        opened = false;
        transferInitializing(dataSpec);

        Request.Builder builder = new Request.Builder().url(dataSpec.uri.toString());

        // 默认请求头
        for (Map.Entry<String, String> e : defaultRequestProperties.entrySet()) {
            builder.header(e.getKey(), e.getValue());
        }
        // 单次请求头覆盖默认
        for (Map.Entry<String, String> e : requestProperties.entrySet()) {
            builder.header(e.getKey(), e.getValue());
        }

        // v5.6.8 核心修复：跨域 TS/chunk 段请求时，动态把 Referer 从 playlist 目录级（含 path）
        // 降级为 playlist ORIGIN 级（只含 scheme://host:port/），解决 cache.0567890.xyz:4433
        // → cdn.hls.one 的 CDN sign 鉴权 403 → Network Connection Failed 白屏。
        // 判定：
        //   1) 有 playlistHostPort（说明是 HLS/DASH 播放链路），并且
        //   2) 当前 dataSpec.uri 的 host:port 与 playlist 的 host:port 不同，并且
        //   3) 当前请求 URL 看起来像音视频段（ts/m4s/m4v/webm/fmp4/mkv）或 不是 m3u8/mpd（不是 playlist 重拉）
        if (playlistHostPort != null && playlistOriginReferer != null && dataSpec.uri != null) {
            String urlLc = null;
            try {
                String reqHost = dataSpec.uri.getHost();
                int reqPort = dataSpec.uri.getPort();
                String reqHostPort = reqHost != null ? ((reqPort > 0) ? (reqHost + ":" + reqPort) : reqHost) : null;
                if (reqHostPort != null && !reqHostPort.equalsIgnoreCase(playlistHostPort)) {
                    // 只有请求「段资源」时才替换；m3u8/mpd 的 playlist 重拉仍然保留用户指定的 Referer
                    urlLc = dataSpec.uri.toString().toLowerCase();
                    boolean isPlaylist = urlLc.contains(".m3u8") || urlLc.contains(".mpd");
                    if (!isPlaylist) {
                        builder.removeHeader("Referer");
                        builder.header("Referer", playlistOriginReferer);
                    }
                }
            } catch (Throwable ignored) { /* 保险：Referer 修正失败不影响请求主流程 */ }
        }

        // Range 请求
        long position = dataSpec.position;
        long length = dataSpec.length;
        if (position > 0 || length > 0) {
            String range = "bytes=" + position + "-";
            if (length > 0) range += (position + length - 1);
            builder.header("Range", range);
        }

        try {
            response = client.newCall(builder.build()).execute();
            opened = true;

            int code = response.code();
            if (!response.isSuccessful() && code != 416) {
                throw new HttpDataSourceException(
                        "HTTP " + code + " " + response.message(),
                        dataSpec,
                        HttpDataSourceException.TYPE_OPEN);
            }

            responseBody = response.body();
            if (responseBody == null) {
                throw new HttpDataSourceException("Empty response body", dataSpec, HttpDataSourceException.TYPE_OPEN);
            }
            responseStream = responseBody.byteStream();

            long contentLength = responseBody.contentLength();
            long bytesToRead;
            if (code == 416) {
                // Range Not Satisfiable - 已读完全部数据
                bytesToRead = 0;
            } else if (contentLength > 0) {
                bytesToRead = contentLength;
            } else {
                bytesToRead = C.LENGTH_UNSET;
            }

            transferStarted(dataSpec);
            return bytesToRead;
        } catch (HttpDataSourceException e) {
            throw e;
        } catch (IOException e) {
            throw new HttpDataSourceException(
                    "OkHttp open failed: " + e.getMessage(),
                    e,
                    dataSpec,
                    HttpDataSourceException.TYPE_OPEN);
        } catch (Exception e) {
            // 非 IOException 的运行时异常（如 IllegalStateException）包装为 IOException 再抛出
            throw new HttpDataSourceException(
                    "OkHttp open failed: " + e.getMessage(),
                    new IOException(e),
                    dataSpec,
                    HttpDataSourceException.TYPE_OPEN);
        }
    }

    @Override
    public int read(@NonNull byte[] buffer, int offset, int length) throws HttpDataSourceException {
        if (length == 0) return 0;
        if (responseStream == null) return C.RESULT_END_OF_INPUT;

        try {
            int read = responseStream.read(buffer, offset, length);
            if (read == -1) {
                return C.RESULT_END_OF_INPUT;
            }
            bytesTransferred(read);
            return read;
        } catch (IOException e) {
            throw new HttpDataSourceException(
                    e,
                    /* dataSpecForReporting */ null,
                    HttpDataSourceException.TYPE_READ);
        }
    }

    @Override
    public void close() {
        if (responseBody != null) {
            try { responseBody.close(); } catch (Exception ignored) {}
            responseBody = null;
        }
        responseStream = null;
        if (response != null) {
            try { response.close(); } catch (Exception ignored) {}
            response = null;
        }
        if (opened) {
            opened = false;
            transferEnded();
        }
    }
}
