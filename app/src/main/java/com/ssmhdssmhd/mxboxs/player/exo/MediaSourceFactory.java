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

/**
 * MediaSource 工厂 —— 参考上游 FongMi/TV 的 ExoMediaSourceFactory 实现。
 *
 * <p>关键设计：构造时用 {@link #getDataSourceFactory()}（懒加载、绑定单例 httpDataSourceFactory）
 * 和 {@link #getExtractorsFactory()} 一次性初始化好 {@link #defaultMediaSourceFactory}。后续
 * {@link #createMediaSource(MediaItem)} 只更新单例 httpDataSourceFactory 的 headers 和 playlistUrl，
 * 然后直接委托 defaultMediaSourceFactory 创建 MediaSource。
 *
 * <p>这样保证：
 * <ul>
 *   <li>ExoPlayer.Builder.setMediaSourceFactory() 注入的 DrmSessionManagerProvider /
 *       LoadErrorHandlingPolicy 不会被每次 new DefaultMediaSourceFactory 丢弃 —— 修复 DRM 加密视频
 *       无法播放、HLS/DASH 段失败不重试的问题；</li>
 *   <li>跨域 CDN Referer 修正（setPlaylistUrl）能正确生效 —— 因为 httpDataSourceFactory 是单例，
 *       createMediaSource 更新后，getDataSourceFactory() 闭包下次 createDataSource() 自然使用
 *       更新后的 headers/playlistUrl；</li>
 *   <li>OKHttp 的完整配置（信任所有证书、自定义 DNS/DoH、拦截器）通过 OkHttp.player() 贯穿始终。</li>
 * </ul>
 */
public class MediaSourceFactory implements MediaSource.Factory {

    private static final int CACHE_SPACE_PERCENT = 80;

    private static StandaloneDatabaseProvider databaseProvider;
    private static Cache cache;

    /** 构造时绑定好 dataSourceFactory + extractorsFactory + DRM/LoadError 策略的默认工厂 —— 永远复用。 */
    private final DefaultMediaSourceFactory defaultMediaSourceFactory;
    /** 单例 httpDataSourceFactory：createMediaSource 时只更新它的 headers / playlistUrl。 */
    private HttpDataSource.Factory httpDataSourceFactory;
    /** 懒加载：闭包绑定了单例 httpDataSourceFactory，headers/playlistUrl 更新后自动生效。 */
    private DataSource.Factory dataSourceFactory;
    private ExtractorsFactory extractorsFactory;

    public MediaSourceFactory() {
        this.defaultMediaSourceFactory = new DefaultMediaSourceFactory(getDataSourceFactory(), getExtractorsFactory());
    }

    /**
     * 供 Preload / 预下载链路使用的便捷工厂：按 headers 创建一个独立的 DataSource.Factory。
     * 播放主链路不使用此方法，以保持单例 httpDataSourceFactory 的 headers/playlistUrl 生命周期。
     */
    static DataSource.Factory createUpstreamDataSourceFactory(Map<String, String> headers) {
        HttpDataSource.Factory factory = new OkHttpDataSource.Factory(OkHttp.player());
        factory.setDefaultRequestProperties(headers);
        return new DefaultDataSource.Factory(App.get(), factory);
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

    /**
     * ExoPlayer.Builder.setMediaSourceFactory() 会调用本方法注入 DRM 授权管理器。
     * 我们把它转交给 defaultMediaSourceFactory（构造时已绑定好 dataSourceFactory），
     * 然后 createMediaSource 永远复用 defaultMediaSourceFactory —— 保证 DRM 不会丢失。
     */
    @NonNull
    @Override
    public MediaSource.Factory setDrmSessionManagerProvider(@NonNull DrmSessionManagerProvider drmSessionManagerProvider) {
        defaultMediaSourceFactory.setDrmSessionManagerProvider(drmSessionManagerProvider);
        return this;
    }

    /**
     * ExoPlayer.Builder.setMediaSourceFactory() 会调用本方法注入 LoadErrorHandlingPolicy（段失败重试等）。
     * 同上：转发给 defaultMediaSourceFactory，createMediaSource 复用时策略不会丢失。
     */
    @NonNull
    @Override
    public MediaSource.Factory setLoadErrorHandlingPolicy(@NonNull LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
        defaultMediaSourceFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy);
        return this;
    }

    @NonNull
    @Override
    public @C.ContentType int[] getSupportedTypes() {
        return defaultMediaSourceFactory.getSupportedTypes();
    }

    /**
     * 核心方法：为给定 MediaItem 创建 MediaSource。
     *
     * <p>参考上游 FongMi/TV 的做法：
     * <ol>
     *   <li>从 MediaItem 的 requestMetadata.extras 提取 headers（由 MediaItemFactory 放入）；</li>
     *   <li>调用 UrlUtil.mergeDefaultHeadersForPlayback() 仅做「补缺」而非覆盖 —— 信任解析器返回的
     *       Referer/UA，只在确实缺失时才补兜底值；</li>
     *   <li>更新单例 httpDataSourceFactory 的 headers 和 playlistUrl（跨域 Referer 修正）；</li>
     *   <li>委托构造时已绑定好 dataSourceFactory + extractorsFactory + DRM + LoadError 的
     *       defaultMediaSourceFactory 创建 MediaSource。</li>
     * </ol>
     *
     * <p>之前每次 new DefaultMediaSourceFactory(dataSourceFactory, ...) 的写法会把
     * setDrmSessionManagerProvider / setLoadErrorHandlingPolicy 注入的内容全丢了 —— 这就是
     * 「上游能播、本地不能播」的最严重根因。
     */
    @NonNull
    @Override
    public MediaSource createMediaSource(@NonNull MediaItem mediaItem) {
        String playbackUrl = mediaItem.localConfiguration != null && mediaItem.localConfiguration.uri != null
                ? mediaItem.localConfiguration.uri.toString()
                : "";
        // 1. 从 MediaItem.requestMetadata.extras 提取 headers（由 MediaItemFactory 放入）
        Map<String, String> headers = ExoUtil.extractHeaders(mediaItem);
        // 2. 补缺式 merge —— 信任解析器返回的 headers，只在缺 Referer/UA 时补兜底值
        Map<String, String> safeHeaders = com.ssmhdssmhd.mxboxs.utils.UrlUtil.mergeDefaultHeadersForPlayback(headers, playbackUrl);
        // 3. 更新单例 httpDataSourceFactory：headers + playlistUrl（用于跨域 TS/chunk 段动态修正 Referer）
        getHttpDataSourceFactory()
                .setDefaultRequestProperties(safeHeaders)
                .setPlaylistUrl(playbackUrl);
        // 4. 复用构造时绑定好 dataSourceFactory + extractorsFactory + DRM + LoadError 的 defaultMediaSourceFactory
        return defaultMediaSourceFactory.createMediaSource(mediaItem);
    }

    private ExtractorsFactory getExtractorsFactory() {
        if (extractorsFactory == null) extractorsFactory = new DefaultExtractorsFactory().setTsExtractorFlags(FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS).setTsExtractorTimestampSearchBytes(TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES * 10);
        return extractorsFactory;
    }

    /**
     * 懒加载 DataSource.Factory 闭包：
     * <pre>() -> getCacheDataSource(new DefaultDataSource.Factory(App.get(), getHttpDataSourceFactory())).createDataSource()</pre>
     *
     * <p>每次 DefaultMediaSourceFactory 需要打开连接时，都会通过这个闭包调用 createDataSource()，
     * 进而拿到**最新** headers 和 playlistUrl 的 httpDataSource —— 因为闭包每次都调用
     * {@link #getHttpDataSourceFactory()}，而它返回的是同一个单例（createMediaSource 只更新它的属性）。
     */
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

    /** 单例 OkHttpDataSource.Factory —— createMediaSource 时只更新它的 headers / playlistUrl。 */
    private HttpDataSource.Factory getHttpDataSourceFactory() {
        if (httpDataSourceFactory == null) httpDataSourceFactory = new OkHttpDataSource.Factory(OkHttp.player());
        return httpDataSourceFactory;
    }
}
