package com.ssmhdssmhd.mxboxs.playback.vod;

import com.ssmhdssmhd.mxboxs.bean.Episode;
import com.ssmhdssmhd.mxboxs.bean.Flag;
import com.ssmhdssmhd.mxboxs.bean.History;
import com.ssmhdssmhd.mxboxs.bean.Result;
import com.ssmhdssmhd.mxboxs.bean.Site;
import com.ssmhdssmhd.mxboxs.bean.Vod;

import java.util.List;

public interface VodPlaybackHost {

    String getVodKey();

    String getVodId();

    String getVodName();

    String getVodPic();

    String getVodMark();

    String getHistoryKey();

    boolean isSiteChangeable();

    boolean isFromCollect();

    boolean isHostFinishing();

    boolean isPlayerEmpty();

    boolean isFullscreenForPlayback();

    long getPlayerPosition();

    void usePushId(String id);

    void requestDetail(String key, String id);

    void requestPlayer(VodPlayRequest request);

    void requestSearch(List<Site> sites, String keyword);

    void prepareSource(Vod item);

    void stopPlaybackForRefresh();

    void resetPlaybackForError(String msg);

    void replay(long position);

    void startPlayback(Result result, boolean useParse, long startPositionMs, History history, Episode episode);

    void loadDanmaku(Result result, History history, Episode episode);

    void renderDetail(Vod item, History history);

    void renderEmptyDetail();

    void renderFallbackName(String name);

    void renderFlags(List<Flag> items);

    void renderEpisodes(List<Episode> items);

    void renderFlagSelection(Flag item);

    void renderEpisodeSelection(Episode item);

    void renderReverseEpisodes(List<Episode> items, boolean scroll);

    void renderQuality(Result result, boolean visible);

    void renderQualityVisible(boolean visible);

    void renderSources(List<Vod> items);

    void renderHistory(History history);

    void renderUseParse(boolean useParse);

    void renderArtwork(String url);

    void renderDescription(String desc);

    void onDetailFallbackScheduled();

    void onDetailFallbackCancelled();

    void onSearchStarted(String keyword);

    void onSearchResult();

    void showDetailMessage(String msg);

    void showSwitchLine(Flag flag);

    void showSwitchSource(Vod item);

    void showEpisodeReady(Episode item);

    void showNoNext(boolean reversed);

    void showNoPrev(boolean reversed);

    void finishVod();

    /**
     * AI 预解析下一集：进度到 85% 且用户有快速切集习惯时触发。
     * Host 实现可 override 此方法做真正的后台预缓存（调 API 拿 result → ParseJob 预解析）。
     * 默认空实现，不影响现有代码。
     */
    default void preparseNext(Flag flag, Episode episode) {}

    /**
     * AI 预加载下一集弹幕：进度到 85% 时和 preparseNext 一起触发。
     * Host 可 override 为真正的后台下载（不渲染到 UI，只下载到临时对象池）。
     * 默认空实现。
     */
    default void preloadNextDanmaku(Result result, History history, Episode episode) {}

    /**
     * 构造最小 Result，用于 "解析缓存命中时直接起播" 跳过 HTTP requestPlayer 回环。
     * 字段只填 ParseJob 需要的 key/flag/url，其他留空。
     */
    default Result buildMinimalResultFor(String siteKey, Flag flag, Episode episode) {
        Result r = Result.empty();
        r.setKey(siteKey);
        r.setFlag(flag == null ? "" : flag.getFlag());
        r.setUrl(episode == null ? "" : episode.getUrl());
        return r;
    }

    /** 生成解析缓存 key（与 ParseJob.cacheKey 规则严格一致：key|flag|urlLower）。 */
    default String parseCacheKey(String siteKey, Flag flag, Episode episode) {
        return com.ssmhdssmhd.mxboxs.player.parse.ParseJob.cacheKey(
                siteKey,
                flag == null ? "" : flag.getFlag(),
                episode == null ? "" : episode.getUrl());
    }
}
