package androidx.media3.common;

/**
 * Stub class for MediaChapter (removed in Media3 1.10.0).
 * Used by ChapterAdapter and PlayerManager for chapter display.
 */
public class MediaChapter {

    public static final MediaChapter EMPTY = new MediaChapter();

    public boolean selected;
    public long timeUs;
    public String label;

    public MediaChapter() {
        this.timeUs = C.TIME_UNSET;
        this.label = "";
        this.selected = false;
    }
}