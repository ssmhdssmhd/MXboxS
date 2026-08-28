package com.ssmhdssmhd.mxboxs.player;

import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.MediaChapter;
import androidx.media3.common.MediaEdition;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.ui.danmaku.DanmakuConfig;

import com.ssmhdssmhd.mxboxs.App;
import com.ssmhdssmhd.mxboxs.Constant;
import com.ssmhdssmhd.mxboxs.R;
import com.ssmhdssmhd.mxboxs.bean.Danmaku;
import com.ssmhdssmhd.mxboxs.bean.Result;
import com.ssmhdssmhd.mxboxs.bean.Sub;
import com.ssmhdssmhd.mxboxs.bean.Track;
import com.ssmhdssmhd.mxboxs.impl.ParseCallback;
import com.ssmhdssmhd.mxboxs.player.engine.PlayerEngine;
import com.ssmhdssmhd.mxboxs.player.engine.PlayerEngineFactory;
import com.ssmhdssmhd.mxboxs.player.media.PlaySpec;
import com.ssmhdssmhd.mxboxs.player.parse.ParseJob;
import com.ssmhdssmhd.mxboxs.player.track.TrackUtil;
import com.ssmhdssmhd.mxboxs.setting.DanmakuSetting;
import com.ssmhdssmhd.mxboxs.setting.PlayerSetting;
import com.ssmhdssmhd.mxboxs.utils.Notify;
import com.ssmhdssmhd.mxboxs.utils.ResUtil;
import com.ssmhdssmhd.mxboxs.utils.Util;
import com.google.common.net.HttpHeaders;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PlayerManager implements ParseCallback {

    private final Runnable runnable;
    private final Callback callback;
    private PlayerEngine engine;
    private VideoSize videoSize;
    private ParseJob parseJob;
    private PlaySpec spec;
    private Player player;

    private DanmakuConfig danmakuConfig;
    private long pendingStartPositionMs;
    private boolean danmakuEnabled;
    private boolean initTrack;
    private int retry;
    private int decode;
    /** 直播模式标记：true 时引擎创建/切换走 PlayerSetting.getLiveEngine() 而非 getEngine() */
    private boolean liveMode;

    public PlayerManager(Callback callback) {
        this.callback = callback;
        this.runnable = this::onPlayTimeout;
        this.decode = PlayerEngine.HARD;
        this.engine = PlayerEngineFactory.create(decode, liveMode, listener);
        this.player = engine.getPlayer();
        this.pendingStartPositionMs = C.TIME_UNSET;
        this.danmakuConfig = DanmakuSetting.getConfig();
        this.danmakuEnabled = DanmakuSetting.isShow();
    }

    /** 设置直播模式：LiveActivity 在 onServiceConnected 中调 setLiveMode(true)，VOD 调 setLiveMode(false) */
    public void setLiveMode(boolean live) {
        if (this.liveMode == live) return;
        this.liveMode = live;
        // 模式切换后立即重建引擎，使 live/VOD 引擎各自独立
        setEngine(live ? PlayerSetting.getLiveEngine() : PlayerSetting.getEngine());
    }

    public boolean isLiveMode() {
        return liveMode;
    }

    public static MediaMetadata buildMetadata(String title, String artist, String artUri) {
        Uri artwork = TextUtils.isEmpty(artUri) ? null : Uri.parse(artUri);
        return new MediaMetadata.Builder().setTitle(title).setArtist(artist).setArtworkUri(artwork).build();
    }

    public void release() {
        App.removeCallbacks(runnable);
        if (player != null) player.removeListener(listener);
        if (engine != null) {
            try { engine.release(); } catch (Throwable ignored) {}
        }
        engine = null;
        player = null;
    }

    public Player getPlayer() {
        return player;
    }

    public Tracks getCurrentTracks() {
        return player == null ? Tracks.EMPTY : player.getCurrentTracks();
    }

    public List<MediaChapter> getCurrentMediaChapters() {
        return Collections.emptyList();
    }

    public List<MediaEdition> getCurrentMediaEditions() {
        return Collections.emptyList();
    }

    public MediaItem getCurrentMediaItem() {
        return player == null ? null : player.getCurrentMediaItem();
    }

    public int getPlaybackState() {
        return player == null ? Player.STATE_IDLE : player.getPlaybackState();
    }

    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    public boolean isReleased() {
        return player == null;
    }

    public String getUrl() {
        return spec != null ? spec.getUrl() : null;
    }

    public String getKey() {
        return spec != null ? spec.getKey() : null;
    }

    public List<Danmaku> getDanmakus() {
        return spec != null ? spec.getDanmakus() : null;
    }

    private void setDanmakus(List<Danmaku> items) {
        if (spec != null) spec.setDanmaku(getSelectedDanmaku(items));
        notifyDanmakuSourceChanged();
    }

    private void notifyDanmakuSourceChanged() {
        callback.onDanmakuSourceChanged(getSelectedDanmakuUri());
    }

    public MediaMetadata getMetadata() {
        return spec != null ? spec.getMetadata() : null;
    }

    public void setMetadata(MediaMetadata data) {
        if (spec != null) spec.setMetadata(data);
        if (player == null) return;
        MediaItem current = player.getCurrentMediaItem();
        if (current != null) player.replaceMediaItem(player.getCurrentMediaItemIndex(), current.buildUpon().setMediaMetadata(data).build());
    }

    public Map<String, String> getHeaders() {
        return spec == null || spec.getHeaders() == null ? new HashMap<>() : spec.getHeaders();
    }

    public float getSpeed() {
        return player == null ? 1.0f : player.getPlaybackParameters().speed;
    }

    public boolean isEmpty() {
        return spec == null || TextUtils.isEmpty(spec.getUrl());
    }

    public boolean isPortrait() {
        return getVideoHeight() > getVideoWidth();
    }

    public boolean isLandscape() {
        return getVideoWidth() > getVideoHeight();
    }

    public boolean isLive() {
        return engine != null && engine.isLive();
    }

    public boolean isVod() {
        return engine != null && engine.isVod();
    }

    public boolean haveTrack(int type) {
        return TrackUtil.count(getCurrentTracks(), type) > 0;
    }

    public boolean haveEdition() {
        return !getCurrentMediaEditions().isEmpty();
    }

    public boolean haveChapter() {
        return !getCurrentMediaChapters().isEmpty();
    }

    public boolean haveDanmaku() {
        return !getSelectedDanmaku().isEmpty();
    }

    public boolean canSetOpening(long position, long duration) {
        return position > 0 && duration > 0 && position <= Constant.getOpEdLimit(duration);
    }

    public boolean canSetEnding(long position, long duration) {
        return position > 0 && duration > 0 && duration - position <= Constant.getOpEdLimit(duration);
    }

    public int getVideoWidth() {
        return videoSize == null ? 0 : videoSize.width;
    }

    public int getVideoHeight() {
        return videoSize == null ? 0 : videoSize.height;
    }

    public long getPosition() {
        return player == null ? 0 : player.getCurrentPosition();
    }

    public String getSizeText() {
        return (getVideoWidth() == 0 && getVideoHeight() == 0) ? "" : getVideoWidth() + " x " + getVideoHeight();
    }

    public String getSpeedText() {
        return String.format(Locale.getDefault(), "%.2f", getSpeed());
    }

    public String getDecodeText() {
        return ResUtil.getStringArray(R.array.select_decode)[decode];
    }

    public int getEngine() {
        return switch (engine.getType()) {
            case MPV -> PlayerSetting.ENGINE_MPV;
            case SYSTEM -> PlayerSetting.ENGINE_SYSTEM;
            case ALI -> PlayerSetting.ENGINE_ALI;
            case NOVA -> PlayerSetting.ENGINE_NOVA;
            case IJK -> PlayerSetting.ENGINE_IJK;
            case VLC -> PlayerSetting.ENGINE_VLC;
            case MX -> PlayerSetting.ENGINE_MX;
            case MPVEX -> PlayerSetting.ENGINE_MPVEX;
            case MPVNOVA -> PlayerSetting.ENGINE_MPVNOVA;
            case KMP -> PlayerSetting.ENGINE_KMP;
            default -> PlayerSetting.ENGINE_EXO;
        };
    }

    public void setEngine(int targetEngine) {
        // 直播模式写入 live_engine，点播模式写入 player_engine
        if (liveMode) PlayerSetting.putLiveEngine(targetEngine);
        else PlayerSetting.putEngine(targetEngine);
        // 无条件重建 engine 实例：
        // ① 保证 PlayerManager.getEngine() 在保存设置后立即返回对应用户选择的引擎常量
        //    (否则旧 engine.getType() 会在下次打开引擎选择弹窗时把显示状态拉回 EXO)
        // ② 解决 isEmpty() 场景下旧代码 early return 导致引擎对象从未更新的问题
        //    （用户在解析完成前切换引擎，若无后续 URL 加载，旧引擎会常驻）
        long currentPosition = isEmpty() ? C.TIME_UNSET : getPosition();
        boolean hadMedia = !isEmpty();
        PlayerEngine oldEngine = this.engine;
        if (player != null) player.removeListener(listener);
        // 根据新 setting 直接创建目标类型引擎（不受 DRM/SMB 强制 EXO 影响，因为此时 spec 可能还没设置）
        this.engine = PlayerEngineFactory.create(decode, liveMode, listener);
        this.player = engine.getPlayer();
        callback.onPlayerRebuild(player);
        if (oldEngine != null) {
            try { oldEngine.stop(); } catch (Throwable ignored) {}
            try { oldEngine.release(); } catch (Throwable ignored) {}
        }
        // 仅当已有可播放 URL 时才重新载入，避免空 URL 触发内部异常
        if (hadMedia) startCurrent(currentPosition);
    }

    public String getPositionTime(long delta) {
        return Util.timeMs(Math.clamp(getPosition() + delta, 0, Math.max(0, getDuration())));
    }

    public long getDuration() {
        return player == null ? 0 : player.getDuration();
    }

    public String getDurationTime() {
        return Util.timeMs(Math.max(0, getDuration()));
    }

    public void setSub(Sub sub) {
        if (spec != null) spec.setSub(sub);
        if (engine.addSubtitle(sub)) play();
        else startCurrent();
    }

    public void setFormat(String format) {
        if (spec != null) spec.setFormat(format);
        startCurrent();
    }

    public void selectChapter(MediaChapter chapter) {
        // No-op: MediaChapter API removed in Media3 1.10.0
    }

    public void selectEdition(MediaEdition edition) {
        // No-op: MediaEdition API removed in Media3 1.10.0
    }

    public void setDanmakuConfig(DanmakuConfig config) {
        danmakuConfig = config;
        callback.onDanmakuConfigChanged(danmakuConfig);
    }

    public void setDanmakuEnabled(boolean enabled) {
        if (danmakuEnabled == enabled) return;
        danmakuEnabled = enabled;
        callback.onDanmakuEnabledChanged(danmakuEnabled);
    }

    public void sendDanmaku(String text) {
        callback.onDanmakuSent(text);
    }

    public String setSpeed(float speed) {
        if (!player.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH)) return getSpeedText();
        player.setPlaybackParameters(player.getPlaybackParameters().withSpeed(speed));
        return getSpeedText();
    }

    public String addSpeed() {
        float speed = getSpeed();
        float step = speed >= 2 ? 1f : 0.25f;
        return setSpeed(speed >= 5 ? 0.25f : Math.min(speed + step, 5.0f));
    }

    public String addSpeed(float value) {
        return setSpeed(Math.clamp(getSpeed() + value, 0.25f, 5.0f));
    }

    public String subSpeed(float value) {
        return setSpeed(Math.clamp(getSpeed() - value, 0.25f, 5.0f));
    }

    public String toggleSpeed() {
        return setSpeed(getSpeed() == 1 ? PlayerSetting.getSpeed() : 1);
    }

    public void setTrack(List<Track> tracks) {
        if (!tracks.isEmpty()) TrackUtil.setTrackSelection(player, tracks);
    }

    public void setSubtitleStyle() {
        if (engine != null) engine.setSubtitleStyle();
    }

    public void play() {
        if (player != null) player.play();
    }

    public void pause() {
        if (player != null) player.pause();
    }

    public void stop() {
        if (engine != null) {
            try { engine.stop(); } catch (Throwable ignored) {}
        }
        stopParse();
    }

    public void clearMediaItems() {
        if (player != null) player.clearMediaItems();
    }

    public boolean isRepeatOne() {
        return player != null && player.getRepeatMode() == Player.REPEAT_MODE_ONE;
    }

    public void setRepeatOne(boolean repeat) {
        if (player != null) player.setRepeatMode(repeat ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
    }

    public void replay(long positionMs) {
        if (player == null) return;
        if (positionMs == C.TIME_UNSET) player.seekToDefaultPosition();
        else player.seekTo(positionMs);
        player.play();
    }

    public void seekTo(long time) {
        if (player != null) player.seekTo(time);
    }

    public long getTextOffsetMs() {
        return 0;
    }

    public void setTextOffsetMs(long offsetMs) {
        // No-op: text offset API changed in Media3 1.10.0
    }

    public long getAudioOffsetMs() {
        return 0;
    }

    public void setAudioOffsetMs(long offsetMs) {
        // No-op: audio offset API changed in Media3 1.10.0
    }

    public void reset() {
        App.removeCallbacks(runnable);
        retry = 0;
    }

    public void clear() {
        spec = null;
    }

    public void resetTrack() {
        TrackUtil.reset(player);
    }

    public void toggleDecode() {
        decode = isHard() ? PlayerEngine.SOFT : PlayerEngine.HARD;
        boolean rebuild = engine.setDecode(decode);
        callback.onDecodeChanged();
        if (!rebuild) return;
        setPlayer(engine.rebuild());
        startCurrent(getPosition());
    }

    private void handleDecodeError(PlaybackException e) {
        if (++retry > 1) {
            callback.onError(engine.getErrorMessage(e));
        } else {
            Notify.show(R.string.error_decode_fallback);
            toggleDecode();
        }
    }

    private boolean isHard() {
        return decode == PlayerEngine.HARD;
    }

    private void onPlayTimeout() {
        stop();
        callback.onError(ResUtil.getString(R.string.error_play_timeout));
    }

    private void ensureEngine(PlaySpec spec) {
        if (PlayerEngineFactory.matches(engine, spec, liveMode)) return;
        PlayerEngine old = engine;
        player.removeListener(listener);
        engine = PlayerEngineFactory.create(decode, spec, liveMode, listener);
        setPlayer(engine.getPlayer());
        old.release();
    }

    private void setPlayer(Player player) {
        this.player = player;
        callback.onPlayerRebuild(player);
    }

    public void browse(PlaySpec spec, long startPositionMs) {
        reset();
        clear();
        stopParse();
        // 点播 25s / 直播 20s；配合下面 BUFFERING state 的 reset 让缓冲阶段不算超时。
        long timeout = liveMode ? Constant.TIMEOUT_PLAY_LIVE : Constant.TIMEOUT_PLAY;
        start(spec, timeout, startPositionMs);
    }

    public void start(PlaySpec spec, long timeout) {
        start(spec, timeout, C.TIME_UNSET);
    }

    public void start(PlaySpec spec, long timeout, long startPositionMs) {
        this.spec = spec;
        setMediaItem(timeout, startPositionMs);
    }

    public void parse(String key, Result result, boolean useParse, MediaMetadata metadata) {
        parse(key, result, useParse, metadata, C.TIME_UNSET);
    }

    public void parse(String key, Result result, boolean useParse, MediaMetadata metadata, long startPositionMs) {
        stopParse();
        pendingStartPositionMs = startPositionMs;
        spec = PlaySpec.fromParse(result, key, metadata);
        parseJob = ParseJob.create(this).start(result, useParse);
    }

    private void stopParse() {
        if (parseJob != null) parseJob.stop();
        parseJob = null;
        pendingStartPositionMs = C.TIME_UNSET;
    }

    private void setMediaItem(long timeout, long startPositionMs) {
        if (spec == null || spec.getUrl() == null) return;
        ensureEngine(spec.checkUa());
        engine.start(spec, startPositionMs);
        setDanmakus(spec.getDanmakus());
        App.post(runnable, timeout);
        callback.onPrepare();
        initTrack = false;
    }

    private void startCurrent() {
        startCurrent(getPosition());
    }

    private void startCurrent(long startPositionMs) {
        setMediaItem(Constant.TIMEOUT_PLAY, startPositionMs);
    }

    private Danmaku getSelectedDanmaku(List<Danmaku> items) {
        if (items == null || items.isEmpty()) return Danmaku.empty();
        return items.stream().filter(Danmaku::isSelected).findFirst().orElse(items.get(0));
    }

    public Danmaku getSelectedDanmaku() {
        return getSelectedDanmaku(getDanmakus());
    }

    public Uri getSelectedDanmakuUri() {
        return getSelectedDanmaku().getUri();
    }

    public void setDanmaku(Danmaku item) {
        if (spec == null) return;
        spec.setDanmaku(item);
        notifyDanmakuSourceChanged();
    }

    public void addDanmaku(Danmaku item) {
        if (spec != null) spec.addDanmaku(item);
    }

    @Override
    public void onParseSuccess(Map<String, String> headers, String url, String from) {
        // 第三道防线：第三方解析站有时会返回伪造本地代理 URL（http://127.0.0.1:非9978端口/p/.../base64/index.m3u8），
        // 这些端口根本没有服务器，播放器去连会直接 Network Connection Failed。
        // 如果检测到这种 URL：用还原出的真实 URL 再次触发 parse（走 WebView + AI 嗅探流程挖出真实 m3u8）。
        // 重跑 parse 时要从当前 spec 拷贝 drm / subs / danmaku / format 信息，不然 reparse 后会丢失字幕/弹幕/DRM。
        String unwrapped = com.ssmhdssmhd.mxboxs.utils.UrlUtil.unwrapFakeLocalProxy(url);
        if (!TextUtils.isEmpty(unwrapped)) {
            if (from != null && !from.endsWith("+reparse")) {
                Result result = new Result();
                result.setUrl(unwrapped);
                Map<String, String> realHeaders = com.ssmhdssmhd.mxboxs.utils.UrlUtil.mergeDefaultHeaders(headers, unwrapped);
                result.setHeader(realHeaders);
                result.setPlayUrl("");
                // 强制解析：还原出的 URL 是完整 http(s) 的 player 页面，不要让 PlaySpec 再拼前缀
                // parse=1 足以让 needParse() 返回 true（needParse 内部是 parse==1 || jx==1），避免 jx 缺失 setter
                result.setParse(1);
                if (spec != null) {
                    // 尽量保留原 result 的附加信息：drm/subs/format 在 fromParse 构造 PlaySpec 时会用到
                    // danmaku 无 public setter，弹幕是体验增强，不影响播放，跳过拷贝
                    result.setDrm(spec.getDrm());
                    result.setSubs(spec.getSubs());
                    result.setFormat(spec.getFormat());
                }
                if (spec != null) parse(spec.getKey(), result, true, spec.getMetadata(), pendingStartPositionMs);
                return;
            }
        }
        if (!TextUtils.isEmpty(from)) Notify.show(ResUtil.getString(R.string.parse_from, from));
        if (headers != null) headers.remove(HttpHeaders.RANGE);
        // 参考上游 FongMi/TV：信任解析器返回的 headers，直接存入 spec。
        // UA/Referer 兜底由后续链路保证：
        //   PlaySpec.checkUa() → MediaSourceFactory.createMediaSource() 中的 mergeDefaultHeadersForPlayback()
        //   → OkHttpDataSource.open() 跨域动态 Referer 修正
        // 三道兜底都是补缺式（不覆盖已有值），不会污染解析器精确指定的 headers。
        if (spec != null) spec.setHeaders(headers);
        if (spec != null) spec.setUrl(url);
        startCurrent(pendingStartPositionMs);
        pendingStartPositionMs = C.TIME_UNSET;
    }

    @Override
    public void onParseError() {
        pendingStartPositionMs = C.TIME_UNSET;
        callback.onError(ResUtil.getString(R.string.error_play_parse));
    }

    public interface Callback {

        void onPrepare();

        void onTracksChanged();

        void onDecodeChanged();

        void onMediaOptionsChanged();

        void onError(String msg);

        void onPlayerRebuild(Player newPlayer);

        void onDanmakuSourceChanged(Uri uri);

        void onDanmakuConfigChanged(DanmakuConfig config);

        void onDanmakuEnabledChanged(boolean enabled);

        void onDanmakuSent(String text);
    }

    private final Player.Listener listener = new Player.Listener() {

        @Override
        public void onPlaybackStateChanged(int state) {
            if (state == Player.STATE_READY || state == Player.STATE_ENDED) {
                App.removeCallbacks(runnable);
                // AI 自学习：缓冲结束，记录卡顿时长用于阈值自整定
                PlaybackAdvisor.get().onBufferingEnded();
            } else if (state == Player.STATE_BUFFERING) {
                // AI 自学习：缓冲开始
                PlaybackAdvisor.get().onBufferingStarted();
                // 缓冲中：说明播放器正在拉数据（真在干活），重置起播超时倒计时，
                // 避免"弱网一直在缓冲却被判超时"。超时仍保留上限以免真挂死。
                if (spec != null && spec.getUrl() != null) {
                    long timeout = liveMode ? Constant.TIMEOUT_PLAY_LIVE : Constant.TIMEOUT_PLAY;
                    App.removeCallbacks(runnable);
                    App.post(runnable, timeout);
                }
            }
        }

        @Override
        public void onVideoSizeChanged(@NonNull VideoSize size) {
            videoSize = size;
        }

        @Override
        public void onTracksChanged(@NonNull Tracks tracks) {
            if (tracks.isEmpty() || initTrack) return;
            setTrack(Track.find(getKey()));
            callback.onTracksChanged();
            initTrack = true;
        }

        @Override
        public void onPlayerError(@NonNull PlaybackException e) {
            if (spec == null) return;
            if (engine == null) {
                App.removeCallbacks(runnable);
                callback.onError(e.getMessage() != null ? e.getMessage() : "播放器错误");
                return;
            }
            try {
                PlayerEngine.ErrorAction action = engine.handleError(e);
                // 引擎已自行恢复（如重试成功）时不取消定时回调，避免误删正在运行的切换任务
                if (action != PlayerEngine.ErrorAction.RECOVERED) App.removeCallbacks(runnable);
                switch (action) {
                    case DECODE -> handleDecodeError(e);
                    case RECOVERED -> setDanmakus(spec.getDanmakus());
                    case FATAL -> callback.onError(engine.getErrorMessage(e));
                }
            } catch (Throwable t) {
                // 引擎处理错误时本身出错，兜底报错避免崩溃
                App.removeCallbacks(runnable);
                callback.onError(t.getMessage() != null ? t.getMessage() : "播放器内部错误");
            }
        }
    };

}
