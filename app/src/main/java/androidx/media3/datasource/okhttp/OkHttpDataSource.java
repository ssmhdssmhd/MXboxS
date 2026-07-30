package androidx.media3.datasource.okhttp;

import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.HttpDataSource;

import okhttp3.OkHttpClient;

/**
 * Stub class for OkHttpDataSource (moved to separate module in Media3 1.10.0).
 * Falls back to DefaultHttpDataSource when the okhttp module is not available.
 */
public class OkHttpDataSource extends DefaultHttpDataSource {

    @Deprecated
    public OkHttpDataSource() {
        super();
    }

    @Deprecated
    public OkHttpDataSource(String userAgent) {
        super(userAgent);
    }

    @Deprecated
    public OkHttpDataSource(String userAgent, int connectTimeoutMs, int readTimeoutMs) {
        super(userAgent, connectTimeoutMs, readTimeoutMs);
    }

    @Deprecated
    public OkHttpDataSource(String userAgent, int connectTimeoutMs, int readTimeoutMs, boolean allowCrossProtocolRedirects) {
        super(userAgent, connectTimeoutMs, readTimeoutMs, allowCrossProtocolRedirects);
    }

    @Deprecated
    public OkHttpDataSource(String userAgent, int connectTimeoutMs, int readTimeoutMs, boolean allowCrossProtocolRedirects, RequestProperties defaultRequestProperties) {
        super(userAgent, connectTimeoutMs, readTimeoutMs, allowCrossProtocolRedirects, defaultRequestProperties);
    }

    public static final class Factory implements HttpDataSource.Factory {

        private final DefaultHttpDataSource.Factory delegate;

        public Factory(OkHttpClient client) {
            this.delegate = new DefaultHttpDataSource.Factory();
        }

        @Override
        public HttpDataSource createDataSource() {
            return delegate.createDataSource();
        }

        @Override
        public HttpDataSource.Factory setDefaultRequestProperties(RequestProperties defaultRequestProperties) {
            delegate.setDefaultRequestProperties(defaultRequestProperties);
            return this;
        }
    }
}