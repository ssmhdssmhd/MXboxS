package androidx.media3.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.Player;

/**
 * Custom PlayerSeekView that wraps a DefaultTimeBar for seek functionality.
 * This is a custom implementation replacing the FongMi-specific PlayerSeekView.
 */
public class PlayerSeekView extends FrameLayout {

    private final DefaultTimeBar timeBar;
    private Player player;

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
        addView(timeBar, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    public void setPlayer(@Nullable Player player) {
        this.player = player;
    }

    @Nullable
    public Player getPlayer() {
        return player;
    }

    public TimeBar getTimeBar() {
        return timeBar;
    }
}