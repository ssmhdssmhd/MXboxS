package androidx.media3.mpvplayer;

import android.content.Context;

import java.io.File;

/**
 * Custom MpvPlayerConfig class replacing the FongMi-specific implementation.
 * Configuration for MPV player initialization.
 */
public class MpvPlayerConfig {

    public static final String VIDEO_OUTPUT_GPU_NEXT = "gpu-next";

    public final String defaultUserAgent;
    public final boolean hlsHttpPersistent;

    private MpvPlayerConfig(Builder builder) {
        this.defaultUserAgent = builder.defaultUserAgent;
        this.hlsHttpPersistent = builder.hlsHttpPersistent;
    }

    public static class Builder {
        String defaultUserAgent = "";
        boolean hlsHttpPersistent = true;

        public Builder setDefaultUserAgent(String userAgent) { this.defaultUserAgent = userAgent; return this; }
        public Builder setHlsHttpPersistent(boolean persistent) { this.hlsHttpPersistent = persistent; return this; }
        public Builder addConfigDirectory(File dir) { return this; }
        public Builder addAndroidFontConfig(File configDir, File cacheDir) { return this; }
        public Builder addAndroidDefaults(String videoOutput, File cacheDir) { return this; }
        public Builder addTlsCaFileFromAsset(Context context, String assetPath, File outputFile) { return this; }
        public Builder addPostInitStringOption(String key, String value) { return this; }
        public Builder addPreInitStringOption(String key, String value) { return this; }
        public Builder addDiskCacheOptions(File cacheDir, long maxSeconds, long maxMb) { return this; }
        public Builder addAndroidSubtitleOptions(Context context, boolean caption, double position, double scale) { return this; }

        public MpvPlayerConfig build() {
            return new MpvPlayerConfig(this);
        }
    }
}