package androidx.media3.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.SurfaceView;
import android.view.TextureView;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;

/**
 * Custom PlayerView with FongMi-compatible extensions.
 * Adds debug view methods removed in standard Media3 1.10.0.
 */
@UnstableApi
public class PlayerView extends FrameLayout {

    private Player player;
    private boolean debugViewVisible;
    private final SubtitleView subtitleView;
    private final FrameLayout surfaceView;

    public PlayerView(@NonNull Context context) {
        this(context, null);
    }

    public PlayerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PlayerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.debugViewVisible = false;
        this.subtitleView = new SubtitleView(context);
        this.surfaceView = new FrameLayout(context);
        addView(surfaceView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(subtitleView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    public void setPlayer(@Nullable Player player) {
        this.player = player;
    }

    @Nullable
    public Player getPlayer() {
        return player;
    }

    public boolean isDebugViewVisible() {
        return debugViewVisible;
    }

    public void toggleDebugView() {
        debugViewVisible = !debugViewVisible;
    }

    public void hideDebugView() {
        debugViewVisible = false;
    }

    @NonNull
    public SubtitleView getSubtitleView() {
        return subtitleView;
    }

    public void setControllerAutoShow(boolean autoShow) {
        // Stub
    }

    public void setUseController(boolean useController) {
        // Stub
    }

    public void setControllerHideOnTouch(boolean hideOnTouch) {
        // Stub
    }

    public void setShowBuffering(int showBuffering) {
        // Stub
    }

    public void onResume() {
        // Stub
    }

    public void onPause() {
        // Stub
    }

    public void setKeepScreenOn(boolean keepScreenOn) {
        // Stub
    }

    public void setResizeMode(int resizeMode) {
        // Stub
    }

    public void setDefaultArtwork(@Nullable android.graphics.drawable.Drawable artwork) {
        // Stub
    }

    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
    }
}