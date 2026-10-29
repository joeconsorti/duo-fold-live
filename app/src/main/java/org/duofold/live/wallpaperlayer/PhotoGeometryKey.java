package org.duofold.live.wallpaperlayer;

/* loaded from: classes3.dex */
final class PhotoGeometryKey {
    final int height;
    final Object parent;
    final Object photo;
    final String physical;
    final int rotation;
    final int stack;
    final int width;

    PhotoGeometryKey(Object parent, Object photo, String physical, int width, int height, int rotation, int stack) {
        this.parent = parent;
        this.photo = photo;
        this.physical = physical;
        this.width = width;
        this.height = height;
        this.rotation = rotation;
        this.stack = stack;
    }

    boolean same(PhotoGeometryKey other) {
        return other != null && this.parent == other.parent && this.photo == other.photo && this.physical.equals(other.physical) && this.width == other.width && this.height == other.height && this.rotation == other.rotation && this.stack == other.stack;
    }
}
