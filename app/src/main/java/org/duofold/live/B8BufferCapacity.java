package org.duofold.live;

/** Session-owned raster capacity. A logical window can visit either physical panel.
 * Keep the larger extent of each axis instead of resizing its BufferQueue on every fold. */
final class B8BufferCapacity {
    private int width, height;
    boolean include(int w, int h) {
        if (w <= 0 || h <= 0) return false;
        int nextW = Math.max(width, w), nextH = Math.max(height, h);
        boolean changed = nextW != width || nextH != height;
        width = nextW; height = nextH;
        return changed;
    }
    int width() { return width; }
    int height() { return height; }
}
