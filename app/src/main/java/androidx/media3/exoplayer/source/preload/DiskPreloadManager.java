package androidx.media3.exoplayer.source.preload;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PriorityTaskManager;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.RenderersFactory;

/**
 * Stub class for DiskPreloadManager (removed in Media3 1.10.0).
 * Provides a no-op implementation for pre-caching functionality.
 */
public class DiskPreloadManager {

    private DiskPreloadManager() {
    }

    public void start(ExoPlayer player, MediaItem mediaItem, Options options) {
        // No-op stub
    }

    public void release() {
        // No-op stub
    }

    public static class Builder {

        private final Cache cache;
        private final DataSource.Factory upstreamFactory;
        private final RenderersFactory renderersFactory;
        private PriorityTaskManager priorityTaskManager;

        public Builder(Cache cache, DataSource.Factory upstreamFactory, RenderersFactory renderersFactory) {
            this.cache = cache;
            this.upstreamFactory = upstreamFactory;
            this.renderersFactory = renderersFactory;
        }

        public Builder setPriorityTaskManager(PriorityTaskManager priorityTaskManager) {
            this.priorityTaskManager = priorityTaskManager;
            return this;
        }

        public DiskPreloadManager build() {
            return new DiskPreloadManager();
        }
    }

    public static class Options {

        private final long durationMs;
        private final int maxThreads;

        private Options(long durationMs, int maxThreads) {
            this.durationMs = durationMs;
            this.maxThreads = maxThreads;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {

            private long durationMs = 30000;
            private int maxThreads = 3;

            public Builder setDurationMs(long durationMs) {
                this.durationMs = durationMs;
                return this;
            }

            public Builder setMaxThreads(int maxThreads) {
                this.maxThreads = maxThreads;
                return this;
            }

            public Options build() {
                return new Options(durationMs, maxThreads);
            }
        }
    }
}