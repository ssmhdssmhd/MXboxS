package androidx.media3.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;

public class PlayerSeekView extends FrameLayout {

    private final DefaultTimeBar timeBar;
    private Player player;
    private final Handler handler;
    private final Runnable updateRunnable;
    private boolean isScrubbing;
    private boolean updatesStarted;

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onPlaybackStateChanged(int state) {
            updateTimeBar();
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            updateTimeBar();
        }

        @Override
        public void onPlaybackParametersChanged(@NonNull PlaybackParameters playbackParameters) {
            updateTimeBar();
        }

        @Override
        public void onEvents(@NonNull Player player, @NonNull Player.Events events) {
            updateTimeBar();
        }
    };

    public PlayerSeekView(@NonNull Context context) {
        this(context, null);
    }

    public PlayerSeekView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PlayerSeekView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        timeBar = new DefaultTimeBar(context, attrs);
        timeBar.setId(R.id.exo_progress);
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        addView(timeBar, lp);

        handler = new Handler(Looper.getMainLooper());
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isScrubbing) {
                    updateTimeBar();
                }
                handler.postDelayed(this, 500);
            }
        };
    }

    public void setPlayer(@Nullable Player player) {
        if (this.player != null) {
            this.player.removeListener(playerListener);
        }
        this.player = player;
        if (player != null) {
            player.addListener(playerListener);
            updateTimeBar();
            startUpdates();
        } else {
            stopUpdates();
        }
    }

    @Nullable
    public Player getPlayer() {
        return player;
    }

    public TimeBar getTimeBar() {
        return timeBar;
    }

    private void startUpdates() {
        if (updatesStarted) return;
        updatesStarted = true;
        handler.removeCallbacks(updateRunnable);
        handler.post(updateRunnable);
    }

    private void stopUpdates() {
        updatesStarted = false;
        handler.removeCallbacks(updateRunnable);
    }

    public void setScrubbing(boolean scrubbing) {
        this.isScrubbing = scrubbing;
        if (!scrubbing) updateTimeBar();
    }

    private void updateTimeBar() {
        if (player == null || isScrubbing) return;
        try {
            long duration = player.getDuration();
            if (duration == C.TIME_UNSET) duration = 0;
            long position = player.getCurrentPosition();
            if (position == C.TIME_UNSET) position = 0;
            long buffered = player.getBufferedPosition();
            if (buffered == C.TIME_UNSET) buffered = 0;
            timeBar.setPosition(position);
            timeBar.setDuration(duration);
            timeBar.setBufferedPosition(buffered);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopUpdates();
    }
}
