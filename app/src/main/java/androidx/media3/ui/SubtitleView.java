package androidx.media3.ui;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.text.CaptionStyleCompat;
import androidx.media3.common.text.Cue;
import androidx.media3.common.util.UnstableApi;

import java.util.List;

/**
 * Custom SubtitleView with FongMi-compatible extensions.
 * Adds position/text-size adjustment methods removed in standard Media3 1.10.0.
 */
@UnstableApi
public class SubtitleView extends android.widget.FrameLayout {

    public static final float DEFAULT_TEXT_SIZE_FRACTION = 0.0533f;

    private float position;
    private float textSize;
    private CaptionStyleCompat style;
    private boolean applyEmbeddedStyles;
    private boolean applyEmbeddedFontSizes;

    public SubtitleView(@NonNull Context context) {
        this(context, null);
    }

    public SubtitleView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        this.position = 0.0f;
        this.textSize = 0.0f;
    }

    public void addPosition(float delta) {
        position += delta;
    }

    public void subPosition(float delta) {
        position -= delta;
    }

    public float getPosition() {
        return position;
    }

    public void addTextSize(float delta) {
        textSize += delta;
    }

    public void subTextSize(float delta) {
        textSize -= delta;
    }

    public float getTextSize() {
        return textSize;
    }

    public void reset() {
        position = 0.0f;
        textSize = 0.0f;
    }

    public void setBottomPosition(float position) {
        this.position = position;
    }

    public void setFractionalTextSize(float textSize) {
        this.textSize = textSize;
    }

    public void setStyle(@Nullable CaptionStyleCompat style) {
        this.style = style;
    }

    public void setApplyEmbeddedStyles(boolean applyEmbeddedStyles) {
        this.applyEmbeddedStyles = applyEmbeddedStyles;
    }

    public void setApplyEmbeddedFontSizes(boolean applyEmbeddedFontSizes) {
        this.applyEmbeddedFontSizes = applyEmbeddedFontSizes;
    }

    public void setCues(@Nullable List<Cue> cues) {
        // Stub
    }
}