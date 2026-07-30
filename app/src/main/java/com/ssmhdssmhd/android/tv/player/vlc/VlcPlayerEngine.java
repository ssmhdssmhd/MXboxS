package com.ssmhdssmhd.android.tv.player.vlc;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

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
import androidx.media3.common.BasePlayer;

import com.ssmhdssmhd.android.tv.bean.Sub;
import com.ssmhdssmhd.android.tv.player.engine.PlayerEngine;
import com.ssmhdssmhd.android.tv.player.media.MediaItemFactory;
import com.ssmhdssmhd.android.tv.player.media.PlaySpec;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.interfaces.IVLCVout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;

public class VlcPlayerEngine implements PlayerEngine {

    private final Player.Listener listener;
    private final VlcErrorMsgProvider provider;
    private final Handler handler;
    private VlcPlayerImpl player;
    private PlaySpec spec;
    private int decode;

    public VlcPlayerEngine(int decode, Player.Listener listener) {
        this.listener = listener;
        this.provider = new VlcErrorMsgProvider();
        this.handler = new Handler(Looper.getMainLooper());
        this.decode = decode;
        this.player = new VlcPlayerImpl(listener);
    }

    @Override
    public Type getType() {
        return Type.VLC;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void release() {
        player.releaseInternal();
    }

    @Override
    public Player rebuild() {
        player.releaseInternal();
        player = new VlcPlayerImpl(listener);
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

    private static class VlcPlayerImpl extends BasePlayer {

        private final CopyOnWriteArraySet<Listener> listeners;
        private final Handler handler;
        private final List<String> options;
        private LibVLC libVLC;
        private MediaPlayer mediaPlayer;
        private Media media;
        private MediaItem mediaItem;
        private int playbackState;
        private boolean playWhenReady;
        private long seekPositionMs;
        private boolean isLive;
        private long duration;
        private VideoSize videoSize;
        private PlaybackParameters playbackParameters;
        private int repeatMode;
        private float volume;

        VlcPlayerImpl(Listener listener) {
            this.listeners = new CopyOnWriteArraySet<>();
            this.handler = new Handler(Looper.getMainLooper());
            this.options = buildVlcOptions();
            this.libVLC = new LibVLC(null, options);
            this.mediaPlayer = new MediaPlayer(libVLC);
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

        private List<String> buildVlcOptions() {
            List<String> options = new ArrayList<>();
            // AI quality optimization: VLC hardware acceleration and quality settings
            options.add("--avcodec-hw=any");
            options.add("--avcodec-skip-frame=0");
            options.add("--avcodec-skip-idct=0");
            options.add("--avcodec-threads=auto");
            options.add("--no-video-title-show");
            options.add("--verbose=1");
            // Network optimization
            options.add("--network-caching=3000");
            options.add("--file-caching=3000");
            options.add("--live-caching=3000");
            options.add("--clock-synchro=1");
            options.add("--clock-jitter=0");
            // Video quality optimization
            options.add("--deinterlace=1");
            options.add("--deinterlace-mode=auto");
            options.add("--postproc-q=6");
            options.add("--drop-late-frames");
            options.add("--skip-frames");
            options.add("--hq=high");
            options.add("--sout-avcodec-hq=high");
            options.add("--video-on-top");
            // Audio quality optimization
            options.add("--audio-resampler=soxr");
            options.add("--sout-audio-high-quality");
            options.add("-v");
            return options;
        }

        private void setupCallbacks() {
            mediaPlayer.setEventListener(event -> {
                handler.post(() -> {
                    switch (event.type) {
                        case MediaPlayer.Event.Playing:
                            playbackState = STATE_READY;
                            for (Listener l : listeners) l.onPlaybackStateChanged(playbackState);
                            for (Listener l : listeners) l.onPlayWhenReadyChanged(playWhenReady, PLAY_WHEN_READY_REASON_USER_REQUEST);
                            if (seekPositionMs != C.TIME_UNSET) {
                                mediaPlayer.setTime(seekPositionMs);
                                seekPositionMs = C.TIME_UNSET;
                            }
                            break;
                        case MediaPlayer.Event.Buffering:
                            playbackState = STATE_BUFFERING;
                            for (Listener l : listeners) l.onPlaybackStateChanged(playbackState);
                            break;
                        case MediaPlayer.Event.Stopped:
                            playbackState = STATE_IDLE;
                            for (Listener l : listeners) l.onPlaybackStateChanged(playbackState);
                            break;
                        case MediaPlayer.Event.EndReached:
                            playbackState = STATE_ENDED;
                            for (Listener l : listeners) l.onPlaybackStateChanged(playbackState);
                            break;
                        case MediaPlayer.Event.EncounteredError:
                            playbackState = STATE_IDLE;
                            PlaybackException error = new PlaybackException(null, null, PlaybackException.ERROR_CODE_UNSPECIFIED);
                            for (Listener l : listeners) l.onPlayerError(error);
                            break;
                        case MediaPlayer.Event.TimeChanged:
                            // handled by external polling
                            break;
                        case MediaPlayer.Event.Vout:
                            if (mediaPlayer.getVoutCount() > 0) {
                                IVLCVout vout = mediaPlayer.getVLCVout();
                                if (vout != null) {
                                    vout.setVideoSize(new IVLCVout.OnNewVideoLayoutListener() {
                                        @Override
                                        public void onNewVideoLayout(IVLCVout vout, int width, int height, int visibleWidth, int visibleHeight, int sarNum, int sarDen) {
                                            handler.post(() -> {
                                                videoSize = new VideoSize(width, height);
                                                for (Listener l : listeners) l.onVideoSizeChanged(videoSize);
                                            });
                                        }
                                    });
                                }
                            }
                            break;
                        case MediaPlayer.Event.LengthChanged:
                            duration = mediaPlayer.getLength();
                            break;
                    }
                });
            });
        }

        void setMediaItemInternal(MediaItem item) {
            this.mediaItem = item;
            media = new Media(libVLC, Uri.parse(item.requestMetadata.mediaUri.toString()));
            if (item.requestMetadata.extras != null) {
                for (String key : item.requestMetadata.extras.keySet()) {
                    String value = item.requestMetadata.extras.getString(key);
                    if (value != null) {
                        media.setHWDecoderEnabled(true, false);
                    }
                }
            }
            mediaPlayer.setMedia(media);
            media.release();
        }

        void prepareInternal() {
            try {
                playbackState = STATE_BUFFERING;
                for (Listener l : listeners) l.onPlaybackStateChanged(playbackState);
            } catch (Exception e) {
                PlaybackException error = new PlaybackException(null, null, PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
                for (Listener l : listeners) l.onPlayerError(error);
            }
        }

        void playInternal() {
            mediaPlayer.play();
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
            libVLC.release();
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
            mediaPlayer.setRate(parameters.speed);
            for (Listener l : listeners) l.onPlaybackParametersChanged(parameters);
        }

        @Override
        public long getCurrentPosition() {
            return mediaPlayer.getTime();
        }

        @Override
        public long getDuration() {
            return duration;
        }

        @Override
        public long getBufferedPosition() {
            return mediaPlayer.getTime();
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
            mediaPlayer.play();
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
            mediaPlayer.setTime(positionMs);
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
        }

        @Override
        public void removeMediaItems(int fromIndex, int toIndex) {
        }

        @Override
        public void clearMediaItems() {
            stopInternal();
            mediaItem = null;
        }

        @Override
        public void moveMediaItem(int currentIndex, int newIndex) {
        }

        @Override
        public void moveMediaItems(int fromIndex, int toIndex, int newIndex) {
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
        }

        @Override
        public void seekToPreviousMediaItem() {
        }

        @Override
        public void seekToNext() {
        }

        @Override
        public void seekToPrevious() {
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
            mediaPlayer.setVolume((int) (volume * 100));
        }

        @Override
        public AudioAttributes getAudioAttributes() {
            return AudioAttributes.DEFAULT;
        }

        @Override
        public void setAudioAttributes(AudioAttributes audioAttributes, boolean handleAudioFocus) {
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
        }

        @Override
        public void setMediaMetadata(MediaMetadata mediaMetadata) {
            if (mediaItem != null) {
                mediaItem = mediaItem.buildUpon().setMediaMetadata(mediaMetadata).build();
            }
        }
    }
}