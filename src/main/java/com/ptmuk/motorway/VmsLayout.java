package com.ptmuk.motorway;

/** Shared VMS geometry, in block units: panel from y 0 to HEIGHT, centred on the block. */
public final class VmsLayout {
    public static final float DOTS_PER_BLOCK = 22;
    public static final float PITCH = 1 / DOTS_PER_BLOCK;
    public static final float HEIGHT = 1.35f;

    private VmsLayout() {
    }
}
