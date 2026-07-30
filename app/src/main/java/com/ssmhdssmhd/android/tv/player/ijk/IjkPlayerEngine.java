package com.ssmhdssmhd.android.tv.player.ijk;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.DeviceInfo;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.CueGroup;
import androidx.media3.exoplayer.BasePlayer;

import com.ssmhdssmhd.android.tv.bean.Sub;
import com.ssmhdssmhd.android.tv.player.engine.PlayerEngine;
import com.ssmhdssmhd.android.tv.player.media.MediaItemFactory;
import com.ssmhdssmhd.android.tv.player.media.PlaySpec;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

public class IjkPlayerEngine implements PlayerEngine {

    private final Player.Listener listener;
    private final IjkErrorMsgProvider provider;
    private final Handler handler;
    private IjkPlayerImpl player;
    private PlaySpec spec;
    private int decode;

    public IjkPlayerEngine(int decode, Player.Listener listener) {
        this.listener = listener;
        this.provider = new IjkErrorMsgProvider();
        this.handler = new Handler(Looper.getMainLooper());
        this.decode = decode;
        this.player = new IjkPlayerImpl(listener);
    }

    @Override
    public Type getType() {
        return Type.IJK;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void release() {
        player.releaseInternal();
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public Player rebuild() {
        player.releaseInternal();
        player = new IjkPlayerImpl(listener);
        return player;
    }

    @Override
    public boolean setDecode(int decode) {
        this.decode = decode;
        return true;
    }

    @Override
    public void start(PlaySpec spec, long startPositionMs) {
        this.spec = spec;
        player.setMediaItemInternal(MediaItemFactory.from(spec));
        player.seekToInternal(startPositionMs);
        player.prepareInternal();
        player.playInternal();
    }

    @Override
    public void stop() {
        player.stopInternal();
    }

    @Override
    public boolean isLive() {
        return player.getDuration() < TimeUnit.MINUTES.toMillis(1) || player.isCurrentMediaItemLive();
    }

    @Override
    public boolean isVod() {
        return player.getDuration() > TimeUnit.MINUTES.toMillis(1) && !player.isCurrentMediaItemLive();
    }

    @Override
    public boolean addSubtitle(Sub sub) {
        if (sub == null || player.getCurrentMediaItem() == null) return false;
        if (player.getPlaybackState() == Player.STATE_IDLE || player.getPlaybackState() == Player.STATE_ENDED)
            return false;
        return true;
    }

    @Override
    public String getErrorMessage(PlaybackException e) {
        return provider.get(e);
    }

    @Override
    public ErrorAction handleError(PlaybackException e) {
        return switch (e.errorCode) {
            case PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                 PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
                 PlaybackException.ERROR_CODE_DECODING_FAILED -> ErrorAction.DECODE;
            case PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                 PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                 PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> ErrorAction.FATAL;
            default -> ErrorAction.FATAL;
        };
    }

    private static class IjkPlayerImpl extends BasePlayer {

        private final CopyOnWriteArraySet<Listener> listeners;
        private final Handler handler;
        private IjkMediaPlayer mediaPlayer;
        private MediaItem mediaItem;
        private int playbackState;
        private boolean playWhenReady;
        private long seekPositionMs;
        private boolean isLive;
        private long duration;
        private long position;
        private VideoSize videoSize;
        private PlaybackParameters playbackParameters;
        private int repeatMode;
        private float volume;

        IjkPlayerImpl(Listener listener) {
            this.listeners = new CopyOnWriteArraySet<>();
            this.handler = new Handler(Looper.getMainLooper());
            this.mediaPlayer = new IjkMediaPlayer();
            this.playbackState = STATE_IDLE;
            this.playWhenReady = true;
            this.seekPositionMs = C.TIME_UNSET;
            this.videoSize = VideoSize.UNKNOWN;
            this.playbackParameters = PlaybackParameters.DEFAULT;
            this.repeatMode = REPEAT_MODE_OFF;
            this.volume = 1.0f;
            if (listener != null) listeners.add(listener);
            setupCallbacks();
        }

        private void setupCallbacks() {
            mediaPlayer.setOnPreparedListener(mp -> {
                handler.post(() -> {
                    duration = mediaPlayer.getDuration();
                    playbackState = STATE_READY;
                    for (Listener l : listeners) l.onPlaybackStateChanged(playbackState);
                    for (Listener l : listeners) l.onPlayWhenReadyChanged(playWhenReady, PLAY_WHEN_READY_REASON_USER_REQUEST);
                    if (seekPositionMs != C.TIME_UNSET) {
                        mediaPlayer.seekTo(seekPositionMs);
                        seekPositionMs = C.TIME_UNSET;
                    }
                });
            });

            mediaPlayer.setOnVideoSizeChangedListener((mp, width, height, sarNum, sarDen) -> {
                handler.post(() -> {
                    videoSize = new VideoSize(width, height);
                    for (Listener l : listeners) l.onVideoSizeChanged(videoSize);
                });
            });

            mediaPlayer.setOnCompletionListener(mp -> {
                handler.post(() -> {
                    playbackState = STATE_ENDED;
                    for (Listener l : listeners) l.onPlaybackStateChanged(playbackState);
                });
            });

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                handler.post(() -> {
                    playbackState = STATE_IDLE;
                    @PlaybackException.ErrorCode int errorCode = getErrorCode(what);
                    PlaybackException error = new PlaybackException(null, null, errorCode);
                    for (Listener l : listeners) l.onPlayerError(error);
                });
                return true;
            });

            mediaPlayer.setOnInfoListener((mp, what, extra) -> {
                if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_START) {
                    handler.post(() -> {
                        playbackState = STATE_BUFFERING;
                        for (Listener l : listeners) l.onPlaybackStateChanged(playbackState);
                    });
                } else if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_END) {
                    handler.post(() -> {
                        playbackState = STATE_READY;
                        for (Listener l : listeners) l.onPlaybackStateChanged(playbackState);
                    });
                }
                return false;
            });

            mediaPlayer.setOnBufferingUpdateListener((mp, percent) -> {
                // handled by MEDIA_INFO_BUFFERING_START/END
            });
        }

        private static @PlaybackException.ErrorCode int getErrorCode(int what) {
            return switch (what) {
                case IMediaPlayer.MEDIA_ERROR_UNKNOWN -> PlaybackException.ERROR_CODE_IO_UNSPECIFIED;
                case IMediaPlayer.MEDIA_ERROR_SERVER_DIED -> PlaybackException.ERROR_CODE_REMOTE_ERROR;
                case IMediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK -> PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED;
                case IMediaPlayer.MEDIA_ERROR_IO -> PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED;
                case IMediaPlayer.MEDIA_ERROR_MALFORMED -> PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED;
                case IMediaPlayer.MEDIA_ERROR_UNSUPPORTED -> PlaybackException.ERROR_CODE_DECODING_FAILED;
                default -> PlaybackException.ERROR_CODE_UNSPECIFIED;
            };
        }

        void setMediaItemInternal(MediaItem item) {
            this.mediaItem = item;
            try {
                // AI quality optimization: configure IjkMediaPlayer for best quality
                IjkMediaPlayer.native_setLogLevel(IjkMediaPlayer.IJK_LOG_INFO);
                mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1);
                mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1);
                mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 1);
                mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", 1);
                mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "soundtouch", 1);
                mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 5);
                mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 1);
                // Quality optimization
                mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", 1572864);
                mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "min-frames", 5);
                mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max_cached_duration", 3000);
                mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "enable-accurate-seek", 1);
                mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_clear", 1);
                mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect", 1);
                mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "timeout", 10000000);
                mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "user_agent", "Mozilla/5.0");
                // Video quality optimization
                mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "video-pictq-size", 0);
                mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "skip_loop_filter", 48);
                mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "skip_frame", 0);
                mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48);

                mediaPlayer.setDataSource(item.requestMetadata.mediaUri.toString());
            } catch (Exception e) {
                PlaybackException error = new PlaybackException(null, null, PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
                for (Listener l : listeners) l.onPlayerError(error);
            }
        }

        void prepareInternal() {
            try {
                mediaPlayer.prepareAsync();
                playbackState = STATE_BUFFERING;
                for (Listener l : listeners) l.onPlaybackStateChanged(playbackState);
            } catch (Exception e) {
                PlaybackException error = new PlaybackException(null, null, PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
                for (Listener l : listeners) l.onPlayerError(error);
            }
        }

        void playInternal() {
            mediaPlayer.start();
            playWhenReady = true;
            for (Listener l : listeners) l.onPlayWhenReadyChanged(true, PLAY_WHEN_READY_REASON_USER_REQUEST);
        }

        void stopInternal() {
            mediaPlayer.stop();
            playbackState = STATE_IDLE;
            for (Listener l : listeners) l.onPlaybackStateChanged(playbackState);
        }

        void seekToInternal(long positionMs) {
            if (positionMs != C.TIME_UNSET) {
                seekPositionMs = positionMs;
            }
        }

        void releaseInternal() {
            listeners.clear();
            mediaPlayer.release();
        }

        @Override
        public Looper getApplicationLooper() {
            return Looper.getMainLooper();
        }

        @Override
        public void addListener(Listener listener) {
            listeners.add(listener);
        }

        @Override
        public void removeListener(Listener listener) {
            listeners.remove(listener);
        }

        @Override
        public int getPlaybackState() {
            return playbackState;
        }

        @Override
        public int getPlaybackSuppressionReason() {
            return PLAYBACK_SUPPRESSION_REASON_NONE;
        }

        @Override
        public boolean isPlaying() {
            return playbackState == STATE_READY && playWhenReady && mediaPlayer.isPlaying();
        }

        @Override
        public PlaybackParameters getPlaybackParameters() {
            return playbackParameters;
        }

        @Override
        public void setPlaybackParameters(PlaybackParameters parameters) {
            this.playbackParameters = parameters;
            mediaPlayer.setSpeed(parameters.speed);
            for (Listener l : listeners) l.onPlaybackParametersChanged(parameters);
        }

        @Override
        public long getCurrentPosition() {
            return mediaPlayer.getCurrentPosition();
        }

        @Override
        public long getDuration() {
            return duration;
        }

        @Override
        public long getBufferedPosition() {
            return mediaPlayer.getCurrentPosition();
        }

        @Override
        public long getTotalBufferedDuration() {
            return 0;
        }

        @Override
        public boolean isCurrentMediaItemLive() {
            return isLive;
        }

        @Override
        public boolean isCurrentMediaItemDynamic() {
            return false;
        }

        @Override
        public boolean isCurrentMediaItemSeekable() {
            return true;
        }

        @Override
        public boolean isCommandAvailable(int command) {
            return switch (command) {
                case COMMAND_PLAY_PAUSE,
                     COMMAND_PREPARE,
                     COMMAND_STOP,
                     COMMAND_SEEK_TO_DEFAULT_POSITION,
                     COMMAND_SEEK_TO_MEDIA_ITEM,
                     COMMAND_SEEK_BACK,
                     COMMAND_SEEK_FORWARD,
                     COMMAND_SET_SPEED_AND_PITCH,
                     COMMAND_SET_REPEAT_MODE,
                     COMMAND_SET_VOLUME,
                     COMMAND_GET_CURRENT_MEDIA_ITEM,
                     COMMAND_GET_TIMELINE,
                     COMMAND_GET_MEDIA_ITEMS_METADATA,
                     COMMAND_GET_TRACKS,
                     COMMAND_GET_TEXT,
                     COMMAND_SET_MEDIA_ITEM,
                     COMMAND_CHANGE_MEDIA_ITEMS -> true;
                default -> false;
            };
        }

        @Override
        public void play() {
            mediaPlayer.start();
            playWhenReady = true;
            for (Listener l : listeners) l.onPlayWhenReadyChanged(true, PLAY_WHEN_READY_REASON_USER_REQUEST);
        }

        @Override
        public void pause() {
            mediaPlayer.pause();
            playWhenReady = false;
            for (Listener l : listeners) l.onPlayWhenReadyChanged(false, PLAY_WHEN_READY_REASON_USER_REQUEST);
        }

        @Override
        public void stop() {
            stopInternal();
        }

        @Override
        public void seekToDefaultPosition() {
            seekTo(0);
        }

        @Override
        public void seekTo(long positionMs) {
            mediaPlayer.seekTo(positionMs);
            for (Listener l : listeners) l.onPositionDiscontinuity(DISCONTINUITY_REASON_SEEK);
        }

        @Override
        public void setMediaItem(MediaItem mediaItem) {
            setMediaItem(mediaItem, C.TIME_UNSET);
        }

        @Override
        public void setMediaItem(MediaItem mediaItem, long startPositionMs) {
            setMediaItemInternal(mediaItem);
            seekToInternal(startPositionMs);
        }

        @Override
        public void setMediaItems(List<MediaItem> mediaItems) {
            if (!mediaItems.isEmpty()) setMediaItem(mediaItems.get(0));
        }

        @Override
        public void setMediaItems(List<MediaItem> mediaItems, boolean resetPosition) {
            if (!mediaItems.isEmpty()) setMediaItem(mediaItems.get(0));
        }

        @Override
        public void setMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
            if (startIndex < mediaItems.size()) setMediaItem(mediaItems.get(startIndex), startPositionMs);
        }

        @Override
        public void addMediaItem(MediaItem mediaItem) {
            setMediaItem(mediaItem);
        }

        @Override
        public void addMediaItem(int index, MediaItem mediaItem) {
            setMediaItem(mediaItem);
        }

        @Override
        public void addMediaItems(List<MediaItem> mediaItems) {
            if (!mediaItems.isEmpty()) setMediaItem(mediaItems.get(0));
        }

        @Override
        public void addMediaItems(int index, List<MediaItem> mediaItems) {
            if (!mediaItems.isEmpty()) setMediaItem(mediaItems.get(0));
        }

        @Override
        public void removeMediaItem(int index) {
            // not supported
        }

        @Override
        public void removeMediaItems(int fromIndex, int toIndex) {
            // not supported
        }

        @Override
        public void clearMediaItems() {
            stopInternal();
            mediaItem = null;
        }

        @Override
        public void moveMediaItem(int currentIndex, int newIndex) {
            // not supported
        }

        @Override
        public void moveMediaItems(int fromIndex, int toIndex, int newIndex) {
            // not supported
        }

        @Override
        public void replaceMediaItem(int index, MediaItem mediaItem) {
            setMediaItem(mediaItem);
        }

        @Override
        public void replaceMediaItems(int fromIndex, int toIndex, List<MediaItem> mediaItems) {
            if (!mediaItems.isEmpty()) setMediaItem(mediaItems.get(0));
        }

        @Override
        public boolean hasNextMediaItem() {
            return false;
        }

        @Override
        public boolean hasPreviousMediaItem() {
            return false;
        }

        @Override
        public void seekToNextMediaItem() {
            // not supported
        }

        @Override
        public void seekToPreviousMediaItem() {
            // not supported
        }

        @Override
        public void seekToNext() {
            // not supported
        }

        @Override
        public void seekToPrevious() {
            // not supported
        }

        @Override
        public int getRepeatMode() {
            return repeatMode;
        }

        @Override
        public void setRepeatMode(int repeatMode) {
            this.repeatMode = repeatMode;
        }

        @Override
        public boolean getShuffleModeEnabled() {
            return false;
        }

        @Override
        public void setShuffleModeEnabled(boolean shuffleModeEnabled) {
            // not supported
        }

        @Override
        public Timeline getCurrentTimeline() {
            return Timeline.EMPTY;
        }

        @Override
        public int getCurrentPeriodIndex() {
            return 0;
        }

        @Override
        public int getCurrentMediaItemIndex() {
            return 0;
        }

        @Override
        public MediaItem getCurrentMediaItem() {
            return mediaItem;
        }

        @Override
        public long getContentPosition() {
            return getCurrentPosition();
        }

        @Override
        public void setPlayWhenReady(boolean playWhenReady) {
            if (playWhenReady) play();
            else pause();
        }

        @Override
        public boolean getPlayWhenReady() {
            return playWhenReady;
        }

        @Override
        public VideoSize getVideoSize() {
            return videoSize;
        }

        @Override
        public float getVolume() {
            return volume;
        }

        @Override
        public void setVolume(float volume) {
            this.volume = volume;
            mediaPlayer.setVolume(volume, volume);
        }

        @Override
        public AudioAttributes getAudioAttributes() {
            return AudioAttributes.DEFAULT;
        }

        @Override
        public void setAudioAttributes(AudioAttributes audioAttributes, boolean handleAudioFocus) {
            // not supported
        }

        @Override
        public CueGroup getCurrentCues() {
            return CueGroup.EMPTY_TIME_ZERO;
        }

        @Override
        public DeviceInfo getDeviceInfo() {
            return DeviceInfo.UNKNOWN;
        }

        @Override
        public int getDeviceVolume() {
            return (int) (volume * 100);
        }

        @Override
        public boolean isDeviceMuted() {
            return volume == 0;
        }

        @Override
        public void setDeviceVolume(int volume) {
            setVolume(volume / 100f);
        }

        @Override
        public void increaseDeviceVolume() {
            setVolume(Math.min(volume + 0.1f, 1.0f));
        }

        @Override
        public void decreaseDeviceVolume() {
            setVolume(Math.max(volume - 0.1f, 0f));
        }

        @Override
        public void setDeviceMuted(boolean muted) {
            setVolume(muted ? 0 : 1);
        }

        @Override
        public void prepare() {
            prepareInternal();
        }

        @Override
        public void stop(boolean reset) {
            stopInternal();
        }

        @Override
        public void release() {
            releaseInternal();
        }

        @Override
        public Tracks getCurrentTracks() {
            return Tracks.EMPTY;
        }

        @Override
        public TrackSelectionParameters getTrackSelectionParameters() {
            return TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT;
        }

        @Override
        public void setTrackSelectionParameters(TrackSelectionParameters parameters) {
            // not supported
        }

        @Override
        public MediaMetadata getMediaMetadata() {
            return mediaItem != null ? mediaItem.mediaMetadata : MediaMetadata.EMPTY;
        }

        @Override
        public MediaMetadata getPlaylistMetadata() {
            return MediaMetadata.EMPTY;
        }

        @Override
        public void setPlaylistMetadata(MediaMetadata mediaMetadata) {
            // not supported
        }

        @Override
        public void setMediaMetadata(MediaMetadata mediaMetadata) {
            if (mediaItem != null) {
                mediaItem = mediaItem.buildUpon().setMediaMetadata(mediaMetadata).build();
            }
        }
    }
}