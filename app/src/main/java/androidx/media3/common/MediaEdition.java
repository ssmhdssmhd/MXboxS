package androidx.media3.common;

/**
 * Stub class for MediaEdition (removed in Media3 1.10.0).
 * Used by EditionAdapter and PlayerManager for edition display.
 */
public class MediaEdition {

    public static final MediaEdition EMPTY = new MediaEdition();

    public boolean selected;
    public long durationUs;
    public String label;

    public MediaEdition() {
        this.durationUs = C.TIME_UNSET;
        this.label = "";
        this.selected = false;
    }
}