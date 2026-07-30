package androidx.media3.mpvplayer;

import android.content.Context;
import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;

import androidx.media3.common.util.Size;

import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
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
import androidx.media3.exoplayer.ExoPlayer;

import java.util.List;

/**
 * Custom MpvPlayer class replacing the FongMi-specific implementation.
 * This is a stub implementation that wraps ExoPlayer.
 * Since isAvailable() returns false, this class is never instantiated at runtime.
 */
public class MpvPlayer implements Player {

    private final ExoPlayer exoPlayer;

    private MpvPlayer(ExoPlayer exoPlayer) {
        this.exoPlayer = exoPlayer;
    }

    public static boolean isAvailable() {
        return false;
    }

    public void setDecode(int decode) {
        // Stub
    }

    public void setSubtitleOptions(MpvPlayerConfig config) {
        // Stub
    }

    public void addSubtitle(MediaItem.SubtitleConfiguration subtitle) {
        // Stub
    }

    // Player interface delegation
    @Override
    public Looper getApplicationLooper() { return exoPlayer.getApplicationLooper(); }

    @Override
    public void addListener(Listener listener) { exoPlayer.addListener(listener); }

    @Override
    public void removeListener(Listener listener) { exoPlayer.removeListener(listener); }

    @Override
    public void setMediaItems(List<MediaItem> mediaItems) { exoPlayer.setMediaItems(mediaItems); }

    @Override
    public void setMediaItems(List<MediaItem> mediaItems, boolean resetPosition) { exoPlayer.setMediaItems(mediaItems, resetPosition); }

    @Override
    public void setMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs) { exoPlayer.setMediaItems(mediaItems, startIndex, startPositionMs); }

    @Override
    public void setMediaItem(MediaItem mediaItem) { exoPlayer.setMediaItem(mediaItem); }

    @Override
    public void setMediaItem(MediaItem mediaItem, long startPositionMs) { exoPlayer.setMediaItem(mediaItem, startPositionMs); }

    @Override
    public void setMediaItem(MediaItem mediaItem, boolean resetPosition) { exoPlayer.setMediaItem(mediaItem, resetPosition); }

    @Override
    public void addMediaItem(MediaItem mediaItem) { exoPlayer.addMediaItem(mediaItem); }

    @Override
    public void addMediaItem(int index, MediaItem mediaItem) { exoPlayer.addMediaItem(index, mediaItem); }

    @Override
    public void addMediaItems(List<MediaItem> mediaItems) { exoPlayer.addMediaItems(mediaItems); }

    @Override
    public void addMediaItems(int index, List<MediaItem> mediaItems) { exoPlayer.addMediaItems(index, mediaItems); }

    @Override
    public void moveMediaItem(int currentIndex, int newIndex) { exoPlayer.moveMediaItem(currentIndex, newIndex); }

    @Override
    public void moveMediaItems(int fromIndex, int toIndex, int newIndex) { exoPlayer.moveMediaItems(fromIndex, toIndex, newIndex); }

    @Override
    public void removeMediaItem(int index) { exoPlayer.removeMediaItem(index); }

    @Override
    public void removeMediaItems(int fromIndex, int toIndex) { exoPlayer.removeMediaItems(fromIndex, toIndex); }

    @Override
    public void clearMediaItems() { exoPlayer.clearMediaItems(); }

    @Override
    public boolean isCommandAvailable(int command) { return exoPlayer.isCommandAvailable(command); }

    @Override
    public boolean canAdvertiseSession() { return exoPlayer.canAdvertiseSession(); }

    @Override
    public Commands getAvailableCommands() { return exoPlayer.getAvailableCommands(); }

    @Override
    public void prepare() { exoPlayer.prepare(); }

    @Override
    public int getPlaybackState() { return exoPlayer.getPlaybackState(); }

    @Override
    public int getPlaybackSuppressionReason() { return exoPlayer.getPlaybackSuppressionReason(); }

    @Override
    public boolean isPlaying() { return exoPlayer.isPlaying(); }

    @Override
    public PlaybackException getPlayerError() { return exoPlayer.getPlayerError(); }

    @Override
    public void play() { exoPlayer.play(); }

    @Override
    public void pause() { exoPlayer.pause(); }

    @Override
    public void stop() { exoPlayer.stop(); }

    @Override
    public void setPlayWhenReady(boolean playWhenReady) { exoPlayer.setPlayWhenReady(playWhenReady); }

    @Override
    public boolean getPlayWhenReady() { return exoPlayer.getPlayWhenReady(); }

    @Override
    public void setRepeatMode(int repeatMode) { exoPlayer.setRepeatMode(repeatMode); }

    @Override
    public int getRepeatMode() { return exoPlayer.getRepeatMode(); }

    @Override
    public void setShuffleModeEnabled(boolean shuffleModeEnabled) { exoPlayer.setShuffleModeEnabled(shuffleModeEnabled); }

    @Override
    public boolean getShuffleModeEnabled() { return exoPlayer.getShuffleModeEnabled(); }

    @Override
    public long getDuration() { return exoPlayer.getDuration(); }

    @Override
    public long getCurrentPosition() { return exoPlayer.getCurrentPosition(); }

    @Override
    public long getBufferedPosition() { return exoPlayer.getBufferedPosition(); }

    @Override
    public long getTotalBufferedDuration() { return exoPlayer.getTotalBufferedDuration(); }

    @Override
    public boolean isPlayingAd() { return exoPlayer.isPlayingAd(); }

    @Override
    public int getCurrentAdGroupIndex() { return exoPlayer.getCurrentAdGroupIndex(); }

    @Override
    public int getCurrentAdIndexInAdGroup() { return exoPlayer.getCurrentAdIndexInAdGroup(); }

    @Override
    public long getContentDuration() { return exoPlayer.getContentDuration(); }

    @Override
    public long getContentPosition() { return exoPlayer.getContentPosition(); }

    @Override
    public long getContentBufferedPosition() { return exoPlayer.getContentBufferedPosition(); }

    @Override
    public void seekTo(long positionMs) { exoPlayer.seekTo(positionMs); }

    @Override
    public void seekTo(int mediaItemIndex, long positionMs) { exoPlayer.seekTo(mediaItemIndex, positionMs); }

    @Override
    public long getSeekBackIncrement() { return exoPlayer.getSeekBackIncrement(); }

    @Override
    public void seekBack() { exoPlayer.seekBack(); }

    @Override
    public long getSeekForwardIncrement() { return exoPlayer.getSeekForwardIncrement(); }

    @Override
    public void seekForward() { exoPlayer.seekForward(); }

    @Override
    public void seekToPreviousMediaItem() { exoPlayer.seekToPreviousMediaItem(); }

    @Override
    public long getMaxSeekToPreviousPosition() { return exoPlayer.getMaxSeekToPreviousPosition(); }

    @Override
    public void seekToNext() { exoPlayer.seekToNext(); }

    @Override
    public void seekToNextMediaItem() { exoPlayer.seekToNextMediaItem(); }

    @Override
    public void setPlaybackParameters(PlaybackParameters playbackParameters) { exoPlayer.setPlaybackParameters(playbackParameters); }

    @Override
    public void setPlaybackSpeed(float speed) { exoPlayer.setPlaybackSpeed(speed); }

    @Override
    public PlaybackParameters getPlaybackParameters() { return exoPlayer.getPlaybackParameters(); }

    @Override
    public void release() { exoPlayer.release(); }

    @Override
    public Timeline getCurrentTimeline() { return exoPlayer.getCurrentTimeline(); }

    @Override
    public int getCurrentPeriodIndex() { return exoPlayer.getCurrentPeriodIndex(); }

    @Override
    public int getCurrentMediaItemIndex() { return exoPlayer.getCurrentMediaItemIndex(); }

    @Override
    @Nullable
    public MediaItem getCurrentMediaItem() { return exoPlayer.getCurrentMediaItem(); }

    @Override
    public int getMediaItemCount() { return exoPlayer.getMediaItemCount(); }

    @Override
    public MediaItem getMediaItemAt(int index) { return exoPlayer.getMediaItemAt(index); }

    @Override
    public long getCurrentLiveOffset() { return exoPlayer.getCurrentLiveOffset(); }

    @Override
    public TrackSelectionParameters getTrackSelectionParameters() { return exoPlayer.getTrackSelectionParameters(); }

    @Override
    public MediaMetadata getMediaMetadata() { return exoPlayer.getMediaMetadata(); }

    @Override
    public MediaMetadata getPlaylistMetadata() { return exoPlayer.getPlaylistMetadata(); }

    @Override
    public void setPlaylistMetadata(MediaMetadata mediaMetadata) { exoPlayer.setPlaylistMetadata(mediaMetadata); }

    @Override
    public Tracks getCurrentTracks() { return exoPlayer.getCurrentTracks(); }

    @Override
    public VideoSize getVideoSize() { return exoPlayer.getVideoSize(); }

    @Override
    public Size getSurfaceSize() { return exoPlayer.getSurfaceSize(); }

    @Override
    public void setVolume(float volume) { exoPlayer.setVolume(volume); }

    @Override
    public float getVolume() { return exoPlayer.getVolume(); }

    @Override
    public void clearVideoSurface() { exoPlayer.clearVideoSurface(); }

    @Override
    public void clearVideoSurface(@Nullable Surface surface) { exoPlayer.clearVideoSurface(surface); }

    @Override
    public void clearVideoTextureView(@Nullable TextureView textureView) { exoPlayer.clearVideoTextureView(textureView); }

    @Override
    public void setVideoSurface(@Nullable Surface surface) { exoPlayer.setVideoSurface(surface); }

    @Override
    public void setVideoSurfaceView(@Nullable SurfaceView surfaceView) { exoPlayer.setVideoSurfaceView(surfaceView); }

    @Override
    public void setVideoTextureView(@Nullable TextureView textureView) { exoPlayer.setVideoTextureView(textureView); }

    @Override
    public void setVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) { exoPlayer.setVideoSurfaceHolder(surfaceHolder); }

    @Override
    public void clearVideoSurfaceView(@Nullable SurfaceView surfaceView) { exoPlayer.clearVideoSurfaceView(surfaceView); }

    @Override
    public void clearVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) { exoPlayer.clearVideoSurfaceHolder(surfaceHolder); }

    @Override
    public CueGroup getCurrentCues() { return exoPlayer.getCurrentCues(); }

    @Override
    public DeviceInfo getDeviceInfo() { return exoPlayer.getDeviceInfo(); }

    @Override
    public int getDeviceVolume() { return exoPlayer.getDeviceVolume(); }

    @Override
    public boolean isDeviceMuted() { return exoPlayer.isDeviceMuted(); }

    @Override
    public void setDeviceVolume(int volume) { exoPlayer.setDeviceVolume(volume); }

    @Override
    public void setDeviceVolume(int volume, int flags) { exoPlayer.setDeviceVolume(volume, flags); }

    @Override
    public void increaseDeviceVolume() { exoPlayer.increaseDeviceVolume(); }

    @Override
    public void increaseDeviceVolume(int flags) { exoPlayer.increaseDeviceVolume(flags); }

    @Override
    public void decreaseDeviceVolume() { exoPlayer.decreaseDeviceVolume(); }

    @Override
    public void decreaseDeviceVolume(int flags) { exoPlayer.decreaseDeviceVolume(flags); }

    @Override
    public void setDeviceMuted(boolean muted) { exoPlayer.setDeviceMuted(muted); }

    @Override
    public void setDeviceMuted(boolean muted, int flags) { exoPlayer.setDeviceMuted(muted, flags); }

    @Override
    public void setAudioAttributes(AudioAttributes audioAttributes, boolean handleAudioFocus) { exoPlayer.setAudioAttributes(audioAttributes, handleAudioFocus); }

    @Override
    public AudioAttributes getAudioAttributes() { return exoPlayer.getAudioAttributes(); }

    // Additional Player interface methods
    @Override
    public int getBufferedPercentage() { return exoPlayer.getBufferedPercentage(); }

    @Override
    public int getCurrentWindowIndex() { return exoPlayer.getCurrentWindowIndex(); }

    @Override
    public int getNextMediaItemIndex() { return exoPlayer.getNextMediaItemIndex(); }

    @Override
    public int getNextWindowIndex() { return exoPlayer.getNextWindowIndex(); }

    @Override
    public int getPreviousMediaItemIndex() { return exoPlayer.getPreviousMediaItemIndex(); }

    @Override
    public int getPreviousWindowIndex() { return exoPlayer.getPreviousWindowIndex(); }

    @Override
    public boolean hasNextMediaItem() { return exoPlayer.hasNextMediaItem(); }

    @Override
    public boolean hasPreviousMediaItem() { return exoPlayer.hasPreviousMediaItem(); }

    @Override
    public boolean isCurrentMediaItemDynamic() { return exoPlayer.isCurrentMediaItemDynamic(); }

    @Override
    public boolean isCurrentMediaItemLive() { return exoPlayer.isCurrentMediaItemLive(); }

    @Override
    public boolean isCurrentMediaItemSeekable() { return exoPlayer.isCurrentMediaItemSeekable(); }

    @Override
    public boolean isCurrentWindowDynamic() { return exoPlayer.isCurrentWindowDynamic(); }

    @Override
    public boolean isCurrentWindowLive() { return exoPlayer.isCurrentWindowLive(); }

    @Override
    public boolean isCurrentWindowSeekable() { return exoPlayer.isCurrentWindowSeekable(); }

    @Override
    public boolean isLoading() { return exoPlayer.isLoading(); }

    @Override
    public void mute() { exoPlayer.mute(); }

    @Override
    public void unmute() { exoPlayer.unmute(); }

    @Override
    public void replaceMediaItem(int index, MediaItem mediaItem) { exoPlayer.replaceMediaItem(index, mediaItem); }

    @Override
    public void replaceMediaItems(int fromIndex, int toIndex, List<MediaItem> mediaItems) { exoPlayer.replaceMediaItems(fromIndex, toIndex, mediaItems); }

    @Override
    public void seekToDefaultPosition() { exoPlayer.seekToDefaultPosition(); }

    @Override
    public void seekToDefaultPosition(int mediaItemIndex) { exoPlayer.seekToDefaultPosition(mediaItemIndex); }

    @Override
    public void seekToPrevious() { exoPlayer.seekToPrevious(); }

    @Override
    public Object getCurrentManifest() { return exoPlayer.getCurrentManifest(); }

    public static class Builder {
        private final Context context;
        private int decode;
        private MpvPlayerConfig config;

        public Builder(Context context) {
            this.context = context;
        }

        public Builder setDecode(int decode) {
            this.decode = decode;
            return this;
        }

        public Builder setConfig(MpvPlayerConfig config) {
            this.config = config;
            return this;
        }

        public MpvPlayer build() {
            ExoPlayer exoPlayer = new ExoPlayer.Builder(context).build();
            return new MpvPlayer(exoPlayer);
        }
    }
}