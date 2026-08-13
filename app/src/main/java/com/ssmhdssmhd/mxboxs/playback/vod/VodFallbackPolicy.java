package com.ssmhdssmhd.mxboxs.playback.vod;

import com.ssmhdssmhd.mxboxs.api.config.VodConfig;
import com.ssmhdssmhd.mxboxs.bean.Flag;
import com.ssmhdssmhd.mxboxs.bean.Result;
import com.ssmhdssmhd.mxboxs.bean.Site;
import com.ssmhdssmhd.mxboxs.bean.Vod;
import com.ssmhdssmhd.mxboxs.player.SourceQualityStore;

import java.util.ArrayList;
import java.util.List;

public class VodFallbackPolicy {

    private final VodPlaybackController controller;
    private final VodPlaybackState state;
    private final VodPlaybackHost host;

    public VodFallbackPolicy(VodPlaybackController controller, VodPlaybackState state, VodPlaybackHost host) {
        this.controller = controller;
        this.state = state;
        this.host = host;
    }

    public void playbackError() {
        fallbackToNextLineOrSource();
    }

    public void emptyFlag() {
        fallbackToNextLineOrSource();
    }

    public void emptyDetail() {
        fallbackToNextSource(false);
    }

    public void manualSwitchSource() {
        fallbackToNextSource(true);
    }

    public void search(String keyword, boolean autoFallback) {
        state.setSearchKeyword(keyword);
        state.setAutoFallback(autoFallback);
        state.setSelectFirstSource(autoFallback);
        host.onSearchStarted(keyword);
        host.requestSearch(getSearchableSites(), keyword);
    }

    public void onSearchResult(Result result) {
        List<Vod> items = new ArrayList<>(result.getList());
        items.removeIf(this::mismatch);
        // AI 源质量评分降序：质量高（解析成功率高/起播快/用户少换源）的源排前面。
        // 同分保留相对顺序（稳定性）。
        if (com.ssmhdssmhd.mxboxs.utils.FeatureFlags.isEnabled(
                com.ssmhdssmhd.mxboxs.utils.FeatureFlags.SOURCE_QUALITY, 100)) {
            items.sort((a, b) -> Integer.compare(
                    SourceQualityStore.getScore(b.getSiteKey()),
                    SourceQualityStore.getScore(a.getSiteKey())));
        }
        state.setSources(items);
        host.renderSources(state.getSources());
        if (state.isSelectFirstSource()) nextSource();
        if (items.isEmpty()) return;
        host.onSearchResult();
    }

    private void fallbackToNextLineOrSource() {
        if (!host.isSiteChangeable()) return;
        if (fallbackToNextLine()) return;
        fallbackToNextSource(false);
    }

    private boolean fallbackToNextLine() {
        int position = state.getFlagPosition() + 1;
        if (position >= state.getFlags().size()) return false;
        Flag flag = state.getFlags().get(position);
        host.showSwitchLine(flag);
        controller.selectFlag(flag);
        return true;
    }

    private void fallbackToNextSource(boolean force) {
        if (!state.hasSources()) search(host.getVodName(), true);
        else if (state.isAutoFallback() || force) nextSource();
    }

    private void nextSource() {
        if (!state.hasSources()) return;
        Vod item = state.removeFirstSource();
        // AI 源质量评分：记录"用户/AI 切走了这个源"，提高 switchAwayRate 用于后续评分降权
        SourceQualityStore.recordSwitchAway(host.getVodKey());
        host.renderSources(state.getSources());
        host.showSwitchSource(item);
        state.addFailedId(host.getVodId());
        state.setSelectFirstSource(false);
        controller.fallbackSource(item);
    }

    private List<Site> getSearchableSites() {
        List<Site> sites = new ArrayList<>();
        for (Site site : VodConfig.get().getSites()) if (isPass(site)) sites.add(site);
        return sites;
    }

    private boolean isPass(Site item) {
        if (state.isAutoFallback() && !item.isChangeable()) return false;
        return item.isSearchable();
    }

    private boolean mismatch(Vod item) {
        if (host.getVodId().equals(item.getId())) return true;
        if (state.hasFailedId(item.getId())) return true;
        if (state.isAutoFallback()) return !item.getName().equals(state.getSearchKeyword());
        return !item.getName().contains(state.getSearchKeyword());
    }
}
