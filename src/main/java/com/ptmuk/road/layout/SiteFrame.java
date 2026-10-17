package com.ptmuk.road.layout;

/**
 * A rectangle of the world seen from one of its sides: u runs across that side from the left
 * (as someone standing outside it, facing in, sees it) and v runs back from it. Sides are
 * numbered as Minecraft's 2D data values: 0 south, 1 west, 2 north, 3 east. Local directions:
 * 0 front (out towards that side), 1 left, 2 back, 3 right.
 */
public record SiteFrame(int x0, int z0, int x1, int z1, int front) {
    public static final int FRONT = 0, LEFT = 1, BACK = 2, RIGHT = 3;

    public int width() {
        return front % 2 == 0 ? x1 - x0 + 1 : z1 - z0 + 1;
    }

    public int depth() {
        return front % 2 == 0 ? z1 - z0 + 1 : x1 - x0 + 1;
    }

    public int x(int u, int v) {
        return switch (front) {
            case 0 -> x0 + u;
            case 1 -> x0 + v;
            case 2 -> x1 - u;
            default -> x1 - v;
        };
    }

    public int z(int u, int v) {
        return switch (front) {
            case 0 -> z1 - v;
            case 1 -> z0 + u;
            case 2 -> z0 + v;
            default -> z1 - u;
        };
    }

    /** Local u of a world column (the inverse of x and z). */
    public int u(int x, int z) {
        return switch (front) {
            case 0 -> x - x0;
            case 1 -> z - z0;
            case 2 -> x1 - x;
            default -> z1 - z;
        };
    }

    public int v(int x, int z) {
        return switch (front) {
            case 0 -> z1 - z;
            case 1 -> x - x0;
            case 2 -> z - z0;
            default -> x1 - x;
        };
    }

    /** The world side (0 south, 1 west, 2 north, 3 east) a local direction points to. */
    public int world(int local) {
        return (front + local) & 3;
    }

    /** The local direction a world side is. */
    public int local(int world) {
        return (world - front) & 3;
    }

    /** A local rectangle as a frame of its own, seen from the side the local direction `facing` points to. */
    public SiteFrame sub(int u0, int v0, int u1, int v1, int facing) {
        int ax = x(u0, v0), az = z(u0, v0), bx = x(u1, v1), bz = z(u1, v1);
        return new SiteFrame(Math.min(ax, bx), Math.min(az, bz), Math.max(ax, bx), Math.max(az, bz), world(facing));
    }
}
