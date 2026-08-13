package com.ssmhdssmhd.mxboxs.playback.vod;

import androidx.media3.common.C;

import com.ssmhdssmhd.mxboxs.api.config.VodConfig;
import com.ssmhdssmhd.mxboxs.bean.Episode;
import com.ssmhdssmhd.mxboxs.bean.Flag;
import com.ssmhdssmhd.mxboxs.bean.History;
import com.ssmhdssmhd.mxboxs.bean.Parse;
import com.ssmhdssmhd.mxboxs.bean.Result;
import com.ssmhdssmhd.mxboxs.bean.Vod;
import com.ssmhdssmhd.mxboxs.setting.PlayerSetting;

import java.util.Collections;
import java.util.List;

public class VodPlaybackController {

    private final VodHistoryPolicy historyPolicy;
    private final VodFallbackPolicy fallbackPolicy;
    private final VodPlaybackState state;
    private final VodPlaybackHost host;
    private History lastHistory;
    private boolean preparseTriggered;

    public VodPlaybackController(VodPlaybackHost host, VodPlaybackState state) {
        this.historyPolicy = new VodHistoryPolicy();
        this.state = state;
        this.host = host;
        this.fallbackPolicy = new VodFallbackPolicy(this, state, host);
    }

    public void reset() {
        state.reset();
    }

    public void checkId() {
        String id = host.getVodId();
        if (id.startsWith("push://")) {
            host.usePushId(id.substring(7));
            id = host.getVodId();
        }
        if (id.isEmpty() || id.startsWith("msearch:")) detailEmpty(false);
        else requestDetail();
    }

    public void requestDetail() {
        host.requestDetail(host.getVodKey(), host.getVodId());
    }

    public void onDetailResult(Result result) {
        if (result.getList().isEmpty()) detailEmpty(result.hasMsg());
        else detailLoaded(result.getVod());
        host.showDetailMessage(result.getMsg());
    }

    public void onPlayerResult(Result result) {
        VodPlayRequest request = state.getPendingRequest();
        if (request == null) request = currentRequest();
        if (cannotApply(result, request)) return;
        applyPlayerResult(result, request);
    }

    public void reclaim(long position) {
        VodPlayRequest request = state.getPlayingRequest();
        Result result = state.getQuality();
        if (cannotApply(result, request)) return;
        startPlayback(result, position);
    }

    private void applyPlayerResult(Result result, VodPlayRequest request) {
        state.setQuality(result);
        state.setPlayingRequest(request);
        state.setUseParse(result.isUseParse());
        host.renderUseParse(state.isUseParse());
        result.getUrl().set(state.getQualityPosition());
        host.renderQuality(result, result.getUrl().isMulti());
        if (result.hasDesc()) host.renderDescription(result.getDesc());
        if (result.hasArtwork()) host.renderArtwork(result.getArtwork());
        if (result.hasPosition()) state.getHistory().setPosition(result.getPosition());
        startPlayback(result, startPositionMs());
        host.loadDanmaku(result, state.getHistory(), state.getEpisode());
    }

    private void startPlayback(Result result, long startPositionMs) {
        host.startPlayback(result, state.isUseParse(), startPositionMs, state.getHistory(), state.getEpisode());
    }

    public void onSearchResult(Result result) {
        fallbackPolicy.onSearchResult(result);
    }

    public void selectFlag(Flag item) {
        selectFlag(item, false);
    }

    private void selectFlag(Flag item, boolean force) {
        if (!state.hasFlags()) return;
        Flag selected = resolveFlag(item);
        if (!force && selected.isSelected()) return;
        for (Flag flag : state.getFlags()) flag.setSelected(selected);
        host.renderFlagSelection(selected);
        host.renderEpisodes(selected.getEpisodes());
        host.renderQualityVisible(false);
        seamless(selected);
    }

    public void selectEpisode(Episode item) {
        if (!state.hasFlags()) return;
        Flag selected = state.getFlag();
        for (Flag flag : state.getFlags()) flag.toggle(flag == selected, item);
        historyPolicy.updateEpisode(state.getHistory(), state.getFlag(), item);
        host.renderEpisodeSelection(item);
        preparseTriggered = false;
        // A3 切集秒开：如果目标集的解析结果已经在缓存里，直接起播真实 URL，跳过 HTTP requestPlayer 回环
        if (com.ssmhdssmhd.mxboxs.utils.FeatureFlags.isEnabled(
                com.ssmhdssmhd.mxboxs.utils.FeatureFlags.PREPARSE_NEXT, 100)) {
            String cacheKey = host.parseCacheKey(host.getVodKey(), selected, item);
            com.ssmhdssmhd.mxboxs.player.parse.ParseJob.CacheEntry hit =
                    com.ssmhdssmhd.mxboxs.player.parse.ParseJob.hitCache(cacheKey);
            if (hit != null) {
                startPlaybackWithCached(hit, selected, item);
                if (host.isFullscreenForPlayback()) host.showEpisodeReady(item);
                return;
            }
        }
        if (host.isFullscreenForPlayback()) host.showEpisodeReady(item);
        refresh();
    }

    /** A3 命中解析缓存直接起播：拿 CacheEntry 的 (headers + url) 当直链起播。 */
    private void startPlaybackWithCached(
            com.ssmhdssmhd.mxboxs.player.parse.ParseJob.CacheEntry hit,
            Flag flag, Episode episode) {
        Result minimal = host.buildMinimalResultFor(host.getVodKey(), flag, episode);
        state.setUseParse(false);
        state.setPlayingRequest(VodPlayRequest.create(host.getVodKey(), flag, episode));
        // 缓存直链：直接以真实 URL + headers 构造 PlaySpec 起播（不走 ParseJob → HTTP API → 回环）
        androidx.media3.common.MediaMetadata metadata =
                com.ssmhdssmhd.mxboxs.playback.vod.VodPlaybackMedia.metadata(state.getHistory(), episode);
        com.ssmhdssmhd.mxboxs.player.media.PlaySpec spec = com.ssmhdssmhd.mxboxs.player.media.PlaySpec.from(
                host.getVodKey(), hit.url, hit.headers, metadata);
        // 把 url/header 也回填到 state.getQuality()，用于重播/画质切换能读到
        state.setQuality(minimal);
        minimal.setUrl(hit.url);
        minimal.setHeader(hit.headers);
        minimal.setParse(0);
        host.renderQuality(state.getQuality(), false);
        host.startPlayback(state.getQuality(), false, 0L, state.getHistory(), episode);
    }

    public void selectQuality(Result result) {
        if (!state.hasEpisode()) return;
        state.setQuality(result);
        state.setQualityPosition(result.getUrl().getPosition());
        startPlayback(result, host.getPlayerPosition());
    }

    public void selectParse(Parse item) {
        VodConfig.get().setParse(item);
        refresh();
    }

    public void mergeFlags(List<Flag> items) {
        if (items.isEmpty()) return;
        if (!state.hasFlags()) {
            state.setFlags(items);
            host.renderFlags(state.getFlags());
            return;
        }
        Flag activated = state.getFlag();
        for (Flag item : items) mergeFlag(activated, item);
        host.renderFlags(state.getFlags());
    }

    public void selectSource(Vod item) {
        switchSource(item, false);
    }

    void fallbackSource(Vod item) {
        switchSource(item, true);
    }

    private void switchSource(Vod item, boolean autoFallback) {
        state.setAutoFallback(autoFallback);
        state.clearPlayRequest();
        saveCurrentHistory();
        host.prepareSource(item);
        requestDetail();
    }

    public void search(String keyword, boolean autoFallback) {
        fallbackPolicy.search(keyword, autoFallback);
    }

    public void manualSwitchSource() {
        fallbackPolicy.manualSwitchSource();
    }

    public void playbackError(String msg) {
        host.resetPlaybackForError(msg);
        fallbackPolicy.playbackError();
    }

    public void playbackEnded() {
        nextEpisode(true);
    }

    public void replay() {
        if (state.getHistory() != null) state.getHistory().setPosition(C.TIME_UNSET);
        if (host.isPlayerEmpty()) refresh();
        else host.replay(startPositionMs());
    }

    public void refresh() {
        saveCurrentHistory();
        host.stopPlaybackForRefresh();
        if (!state.hasFlags() || !state.hasEpisode()) return;
        requestPlayer(state.getFlag(), state.getEpisode());
    }

    public void nextEpisode(boolean notify) {
        if (state.getHistory() != null && state.getHistory().isRevPlay()) prevEpisode(notify, true);
        else nextEpisode(notify, false);
    }

    public void prevEpisode(boolean notify) {
        if (state.getHistory() != null && state.getHistory().isRevPlay()) nextEpisode(notify, true);
        else prevEpisode(notify, false);
    }

    private void nextEpisode(boolean notify, boolean reversed) {
        if (!state.hasEpisode()) return;
        // AI 习惯学习：播放超过 6 分钟就切下一集，记录习惯用于智能预解析
        if (host.getPlayerPosition() >= 6 * 60 * 1000L) {
            PlayerSetting.noteQuickSkipNext();
        }
        Episode item = getRelativeEpisode(1);
        if (!item.isSelected()) selectEpisode(item);
        else if (notify) host.showNoNext(reversed);
    }

    private void prevEpisode(boolean notify, boolean reversed) {
        if (!state.hasEpisode()) return;
        Episode item = getRelativeEpisode(-1);
        if (!item.isSelected()) selectEpisode(item);
        else if (notify) host.showNoPrev(reversed);
    }

    public void reverseEpisode(boolean scroll) {
        if (!state.hasFlags()) return;
        for (Flag flag : state.getFlags()) Collections.reverse(flag.getEpisodes());
        host.renderReverseEpisodes(state.getFlag().getEpisodes(), scroll);
    }

    private void saveCurrentHistory() {
        historyPolicy.save(currentHistory());
    }

    public void saveHistory(boolean exit, long time, long position, long duration) {
        History history = exit ? historyForExit() : currentHistory();
        if (position > 0 && duration > 0) historyPolicy.updateTime(history, time, position, duration);
        historyPolicy.save(history, exit);
    }

    public void syncHistory() {
        historyPolicy.sync(currentHistory());
    }

    public void onTimeChanged(long time, long position, long duration) {
        History history = currentHistory();
        historyPolicy.updateTime(history, time, position, duration);
        if (history != null && history.getEnding() > 0 && history.getEnding() + position >= duration) nextEpisode(false);
        // AI 预解析下一集：进度 >= 85% 且习惯已成型时，后台预缓存下一集直链
        if (!preparseTriggered && duration > 0 && position * 100 >= duration * 85
                && PlayerSetting.shouldPreparseNext() && state.hasEpisode()) {
            Episode next = getRelativeEpisode(1);
            if (!next.isSelected()) {
                preparseTriggered = true;
                host.preparseNext(state.getFlag(), next);
            }
        }
    }

    public long startPositionMs() {
        return historyPolicy.startPositionMs(state.getHistory());
    }

    private History currentHistory() {
        History history = state.getHistory();
        if (history != null) lastHistory = history;
        return history;
    }

    private History historyForExit() {
        History history = currentHistory();
        return history == null ? lastHistory : history;
    }

    public void setOpening(long opening) {
        if (state.getHistory() != null) state.getHistory().setOpening(opening);
    }

    public void setEnding(long ending) {
        if (state.getHistory() != null) state.getHistory().setEnding(ending);
    }

    public void setSpeed(float speed) {
        if (state.getHistory() != null) state.getHistory().setSpeed(speed);
    }

    public void setScale(int scale) {
        if (state.getHistory() != null) state.getHistory().setScale(scale);
    }

    public void setRevSort(boolean revSort) {
        if (state.getHistory() != null) state.getHistory().setRevSort(revSort);
    }

    public void setRevPlay(boolean revPlay) {
        if (state.getHistory() != null) state.getHistory().setRevPlay(revPlay);
    }

    /** 给 Host 层（VideoActivity）访问 history 用：preparseNext / 弹幕预加载都要它。 */
    public History getHistoryForHost() {
        return state.getHistory();
    }

    private void detailEmpty(boolean finish) {
        if (host.isFromCollect() || finish) {
            host.finishVod();
        } else if (host.getVodName().isEmpty()) {
            host.renderEmptyDetail();
        } else {
            host.renderFallbackName(host.getVodName());
            host.onDetailFallbackScheduled();
            fallbackPolicy.emptyDetail();
        }
    }

    private void detailLoaded(Vod item) {
        item.checkPic(host.getVodPic());
        item.checkName(host.getVodName());
        state.setFlags(item.getFlags());
        state.setHistory(historyPolicy.findOrCreate(host.getHistoryKey(), host.getVodMark(), item));
        lastHistory = state.getHistory();
        host.renderDetail(item, state.getHistory());
        host.renderFlags(item.getFlags());
        host.renderHistory(state.getHistory());
        host.onDetailFallbackCancelled();
        if (item.getFlags().isEmpty()) {
            fallbackPolicy.emptyFlag();
        } else {
            selectFlag(state.getHistory().getFlag(), true);
            if (state.getHistory().isRevSort()) reverseEpisode(true);
        }
    }

    private void requestPlayer(Flag flag, Episode episode) {
        historyPolicy.updateEpisode(state.getHistory(), flag, episode);
        VodPlayRequest request = VodPlayRequest.create(host.getVodKey(), flag, episode);
        state.setPendingRequest(request);
        host.requestPlayer(request);
    }

    private void seamless(Flag flag) {
        History history = state.getHistory();
        Episode episode = history == null ? null : flag.find(history.getVodRemarks(), host.getVodMark().isEmpty());
        host.renderQualityVisible(episode != null && episode.isSelected() && state.getQuality().getUrl().isMulti());
        if (episode == null || episode.isSelected()) return;
        history.setVodRemarks(episode.getName());
        selectEpisode(episode);
    }

    private void mergeFlag(Flag activated, Flag item) {
        Flag target = findFlag(item);
        if (target == null) {
            state.getFlags().add(item);
        } else {
            target.mergeEpisodes(item.getEpisodes(), state.getHistory() != null && state.getHistory().isRevSort());
            if (target.equals(activated)) host.renderEpisodes(target.getEpisodes());
        }
    }

    private Flag resolveFlag(Flag item) {
        Flag flag = findFlag(item);
        if (flag != null) return flag;
        return state.getFlags().get(0);
    }

    private Flag findFlag(Flag item) {
        if (item != null) for (Flag flag : state.getFlags()) if (flag.equals(item)) return flag;
        return null;
    }

    private boolean cannotApply(Result result, VodPlayRequest request) {
        return host.isHostFinishing() || !state.hasEpisode() || request == null || !request.matches(host.getVodKey(), state.getFlag(), state.getEpisode()) || !request.accepts(result);
    }

    private VodPlayRequest currentRequest() {
        return state.hasEpisode() ? VodPlayRequest.create(host.getVodKey(), state.getFlag(), state.getEpisode()) : null;
    }

    private Episode getRelativeEpisode(int offset) {
        List<Episode> episodes = state.getFlag().getEpisodes();
        int current = state.getFlag().getPosition();
        int position = Math.clamp(current + offset, 0, episodes.size() - 1);
        return episodes.get(position);
    }
}
