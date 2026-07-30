package androidx.media3.datasource.okhttp;

import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.HttpDataSource;

import java.util.Map;

import okhttp3.OkHttpClient;

/**
 * Stub class for OkHttpDataSource (moved to separate module in Media3 1.10.0).
 * Falls back to DefaultHttpDataSource when the okhttp module is not available.
 */
public class OkHttpDataSource {

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
        public HttpDataSource.Factory setDefaultRequestProperties(Map<String, String> defaultRequestProperties) {
            delegate.setDefaultRequestProperties(defaultRequestProperties);
            return this;
        }
    }
}