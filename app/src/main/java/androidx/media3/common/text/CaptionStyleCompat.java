package androidx.media3.common.text;

import android.graphics.Color;
import android.graphics.Typeface;

/**
 * Stub for CaptionStyleCompat (removed from standard Media3 1.10.0).
 * Provides compatibility with FongMi's usage of this class.
 */
public class CaptionStyleCompat {

    public static final int EDGE_TYPE_NONE = 0;
    public static final int EDGE_TYPE_OUTLINE = 1;
    public static final int EDGE_TYPE_DROP_SHADOW = 2;
    public static final int EDGE_TYPE_RAISED = 3;
    public static final int EDGE_TYPE_DEPRESSED = 4;

    public static final int USE_TRACK_COLOR_SETTINGS = 1;

    public final int foregroundColor;
    public final int backgroundColor;
    public final int windowColor;
    public final int edgeType;
    public final int edgeColor;
    public final Typeface typeface;

    public CaptionStyleCompat(int foregroundColor, int backgroundColor, int windowColor,
                              int edgeType, int edgeColor, Typeface typeface) {
        this.foregroundColor = foregroundColor;
        this.backgroundColor = backgroundColor;
        this.windowColor = windowColor;
        this.edgeType = edgeType;
        this.edgeColor = edgeColor;
        this.typeface = typeface;
    }

    public static CaptionStyleCompat createFromCaptionStyle(
            android.view.accessibility.CaptioningManager.CaptionStyle captionStyle) {
        return new CaptionStyleCompat(
                captionStyle.foregroundColor,
                captionStyle.backgroundColor,
                Color.TRANSPARENT,
                captionStyle.edgeType,
                captionStyle.edgeColor,
                captionStyle.getTypeface()
        );
    }
}