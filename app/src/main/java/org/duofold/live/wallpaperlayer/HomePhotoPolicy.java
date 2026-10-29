package org.duofold.live.wallpaperlayer;

/* loaded from: classes3.dex */
public final class HomePhotoPolicy {
    public static long reattachDelay(int failures) {
        if (failures <= 0) {
            return 100L;
        }
        return 250 << Math.min(failures - 1, 3);
    }

    public static boolean accepts(int width, int height, int displayWidth, int displayHeight) {
        return displayWidth > 0 && displayHeight > 0 && width > 0 && height > 0 && width <= displayWidth && height <= displayHeight && ((long) width) * 100 >= ((long) displayWidth) * 90 && ((long) height) * 100 >= ((long) displayHeight) * 80;
    }
}
