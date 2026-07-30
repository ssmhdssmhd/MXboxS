package com.ssmhdssmhd.android.tv.player.vlc;

import org.videolan.libvlc.LibVLC;

public class VlcUtil {

    public static boolean isAvailable() {
        try {
            LibVLC libVLC = new LibVLC(null);
            libVLC.release();
            return true;
        } catch (Throwable e) {
            return false;
        }
    }
}