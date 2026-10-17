package com.ptmuk.road.layout;

/** Where the ground is, so roads can be levelled into it. */
public interface Terrain {
    /** Height of the top face of the ground in this column (the y of the top solid block, plus one). */
    int groundTop(int x, int z);

    static Terrain flat(int top) {
        return (x, z) -> top;
    }
}
