package com.ssmhdssmhd.mxboxs.player.exo;

import static androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.ts.TsExtractor;

import com.ssmhdssmhd.mxboxs.App;
import com.ssmhdssmhd.mxboxs.setting.PlayerSetting;
import com.ssmhdssmhd.mxboxs.setting.PreloadSetting;
import com.ssmhdssmhd.mxboxs.utils.FileUtil;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;

import java.io.File;
import java.util.Map;

public class MediaSourceFactory implements MediaSource.Factory {

    private static final int CACHE_SPACE_PERCENT = 80;

    private static StandaloneDatabaseProvider databaseProvider;
    private static Cache cache;

    private final DefaultMediaSourceFactory defaultMediaSourceFactory;
    // v5.7.3 修复：缓存外部注入的 DRM 管理器与错误重试策略。此前 setDrmSessionManagerProvider /
    // setLoadErrorHandlingPolicy 只作用在 defaultMediaSourceFactory 上，而 createMediaSource() 又
    // 每次 new 一个全新的 DefaultMediaSourceFactory，导致 DRM 授权与弱网重试全部丢失 → DRM 视频
    // 不能播放、高延迟源偶发 403 重试次数退回默认 1 次后 Network Connection Failed。
    private DrmSessionManagerProvider drmSessionManagerProvider;
    private LoadErrorHandlingPolicy loadErrorHandlingPolicy;
    private HttpDataSource.Factory httpDataSourceFactory;
    private DataSource.Factory dataSourceFactory;
    private ExtractorsFactory extractorsFactory;

    public MediaSourceFactory() {
        defaultMediaSourceFactory = new DefaultMediaSourceFactory(getDataSourceFactory(), getExtractorsFactory());
    }

    static synchronized Cache getCache() {
        if (cache != null) return cache;
        File dir = Path.exoCache();
        return cache = new SimpleCache(dir, new LeastRecentlyUsedCacheEvictor(getMaxCacheSize(dir)), getDatabaseProvider());
    }

    private static StandaloneDatabaseProvider getDatabaseProvider() {
        if (databaseProvider == null) databaseProvider = new StandaloneDatabaseProvider(App.get());
        return databaseProvider;
    }

    private static long getMaxCacheSize(File dir) {
        long usedBytes = FileUtil.getDirectorySize(dir);
        long availableBytes = Math.max(0, FileUtil.getAvailableStorageSpace(dir));
        long storageBudget = (usedBytes + availableBytes) * CACHE_SPACE_PERCENT / 100;
        return Math.min(PreloadSetting.getPreloadSizeBytes(), storageBudget);
    }

    @NonNull
    @Override
    public MediaSource.Factory setDrmSessionManagerProvider(@NonNull DrmSessionManagerProvider drmSessionManagerProvider) {
        // v5.7.3：必须同时缓存到字段，createMediaSource 构造新工厂时再透传，避免 DRM 授权丢失
        this.drmSessionManagerProvider = drmSessionManagerProvider;
        defaultMediaSourceFactory.setDrmSessionManagerProvider(drmSessionManagerProvider);
        return this;
    }

    @NonNull
    @Override
    public MediaSource.Factory setLoadErrorHandlingPolicy(@NonNull LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
        // v5.7.3：同上，缓存后透传给 per-item 工厂，保证段失败至少重试 3 次
        this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
        defaultMediaSourceFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy);
        return this;
    }

    @NonNull
    @Override
    public @C.ContentType int[] getSupportedTypes() {
        return defaultMediaSourceFactory.getSupportedTypes();
    }

    @NonNull
    @Override
    public MediaSource createMediaSource(@NonNull MediaItem mediaItem) {
        // v5.7.3 深度修复（借鉴上游 FongMi 复用配置工厂的做法）：per-item 工厂需要单独构建以注入
        // 播放专用 headers（Referer/UA）到 OkHttpDataSource，但必须把外部注入的 DRM 管理器与
        // LoadErrorHandlingPolicy 一并透传，不能像旧代码那样丢弃 → DRM 授权 & 弱网重试全部保留。
        DataSource.Factory perItemDataSourceFactory = createDataSourceFactory(mediaItem);
        DefaultMediaSourceFactory factory = new DefaultMediaSourceFactory(perItemDataSourceFactory, getExtractorsFactory());
        if (drmSessionManagerProvider != null) factory.setDrmSessionManagerProvider(drmSessionManagerProvider);
        if (loadErrorHandlingPolicy != null) factory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy);
        return factory.createMediaSource(mediaItem);
    }

    private DataSource.Factory createDataSourceFactory(MediaItem mediaItem) {
        // v5.6.8 修复：MediaItem 里可能没 header 或 header 的 Referer 仍是旧版 mergeDefaultHeaders 版
        // （含 ?vkey=xx 长 query），这里再强制走一遍播放专用 headers 流程；并把 MediaItem URL 作为
        // playlistUrl 灌给 OkHttpDataSource.Factory，用于跨域 TS 段动态修正 Referer。
        // 解决 cache.0567890.xyz:4433 → cdn.hls.one Network Connection Failed。
        String playbackUrl = mediaItem != null && mediaItem.localConfiguration != null
                ? (mediaItem.localConfiguration.uri != null ? mediaItem.localConfiguration.uri.toString() : "")
                : "";
        Map<String, String> headers = ExoUtil.extractHeaders(mediaItem);
        Map<String, String> safeHeaders = com.ssmhdssmhd.mxboxs.utils.UrlUtil.mergeDefaultHeadersForPlayback(headers, playbackUrl);
        OkHttpDataSource.Factory httpFactory = new OkHttpDataSource.Factory(OkHttp.player());
        httpFactory.setDefaultRequestProperties(safeHeaders);
        httpFactory.setPlaylistUrl(playbackUrl);
        return () -> getCacheDataSource(new DefaultDataSource.Factory(App.get(), httpFactory)).createDataSource();
    }

    private ExtractorsFactory getExtractorsFactory() {
        if (extractorsFactory == null) extractorsFactory = new DefaultExtractorsFactory().setTsExtractorFlags(FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS).setTsExtractorTimestampSearchBytes(TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES * 10);
        return extractorsFactory;
    }

    private DataSource.Factory getDataSourceFactory() {
        if (dataSourceFactory == null) dataSourceFactory = () -> getCacheDataSource(new DefaultDataSource.Factory(App.get(), getHttpDataSourceFactory())).createDataSource();
        return dataSourceFactory;
    }

    private CacheDataSource.Factory getCacheDataSource(DataSource.Factory upstreamFactory) {
        CacheDataSource.Factory factory = new CacheDataSource.Factory()
                .setCache(getCache())
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
        // 缓存写入受高级设置「缓存视频到本地」开关控制：关闭时不落盘（仅读缓存），开启时正常写入。
        if (!PlayerSetting.isCacheWriteEnabled()) {
            factory.setCacheWriteDataSinkFactory(null);
        }
        return factory;
    }

    private HttpDataSource.Factory getHttpDataSourceFactory() {
        if (httpDataSourceFactory == null) httpDataSourceFactory = new OkHttpDataSource.Factory(OkHttp.player());
        return httpDataSourceFactory;
    }
}
