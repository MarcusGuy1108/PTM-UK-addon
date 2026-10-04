package com.ptmuk.motorway;

import java.util.HashMap;
import java.util.Map;

/** 5x7 dot-matrix font as used on UK motorway signals and variable message signs. */
public final class DotFont {
    public static final int WIDTH = 5;
    public static final int HEIGHT = 7;
    private static final Map<Character, int[]> GLYPHS = new HashMap<>();

    static {
        GLYPHS.put('A', new int[]{14, 17, 17, 31, 17, 17, 17});
        GLYPHS.put('B', new int[]{30, 17, 17, 30, 17, 17, 30});
        GLYPHS.put('C', new int[]{14, 17, 16, 16, 16, 17, 14});
        GLYPHS.put('D', new int[]{30, 17, 17, 17, 17, 17, 30});
        GLYPHS.put('E', new int[]{31, 16, 16, 30, 16, 16, 31});
        GLYPHS.put('F', new int[]{31, 16, 16, 30, 16, 16, 16});
        GLYPHS.put('G', new int[]{14, 17, 16, 23, 17, 17, 15});
        GLYPHS.put('H', new int[]{17, 17, 17, 31, 17, 17, 17});
        GLYPHS.put('I', new int[]{14, 4, 4, 4, 4, 4, 14});
        GLYPHS.put('J', new int[]{7, 2, 2, 2, 2, 18, 12});
        GLYPHS.put('K', new int[]{17, 18, 20, 24, 20, 18, 17});
        GLYPHS.put('L', new int[]{16, 16, 16, 16, 16, 16, 31});
        GLYPHS.put('M', new int[]{17, 27, 21, 21, 17, 17, 17});
        GLYPHS.put('N', new int[]{17, 17, 25, 21, 19, 17, 17});
        GLYPHS.put('O', new int[]{14, 17, 17, 17, 17, 17, 14});
        GLYPHS.put('P', new int[]{30, 17, 17, 30, 16, 16, 16});
        GLYPHS.put('Q', new int[]{14, 17, 17, 17, 21, 18, 13});
        GLYPHS.put('R', new int[]{30, 17, 17, 30, 20, 18, 17});
        GLYPHS.put('S', new int[]{15, 16, 16, 14, 1, 1, 30});
        GLYPHS.put('T', new int[]{31, 4, 4, 4, 4, 4, 4});
        GLYPHS.put('U', new int[]{17, 17, 17, 17, 17, 17, 14});
        GLYPHS.put('V', new int[]{17, 17, 17, 17, 17, 10, 4});
        GLYPHS.put('W', new int[]{17, 17, 17, 21, 21, 21, 10});
        GLYPHS.put('X', new int[]{17, 17, 10, 4, 10, 17, 17});
        GLYPHS.put('Y', new int[]{17, 17, 10, 4, 4, 4, 4});
        GLYPHS.put('Z', new int[]{31, 1, 2, 4, 8, 16, 31});
        GLYPHS.put('0', new int[]{14, 17, 17, 17, 17, 17, 14});
        GLYPHS.put('1', new int[]{4, 12, 4, 4, 4, 4, 14});
        GLYPHS.put('2', new int[]{14, 17, 1, 2, 4, 8, 31});
        GLYPHS.put('3', new int[]{31, 2, 4, 2, 1, 17, 14});
        GLYPHS.put('4', new int[]{2, 6, 10, 18, 31, 2, 2});
        GLYPHS.put('5', new int[]{31, 16, 30, 1, 1, 17, 14});
        GLYPHS.put('6', new int[]{6, 8, 16, 30, 17, 17, 14});
        GLYPHS.put('7', new int[]{31, 1, 2, 4, 8, 8, 8});
        GLYPHS.put('8', new int[]{14, 17, 17, 14, 17, 17, 14});
        GLYPHS.put('9', new int[]{14, 17, 17, 15, 1, 2, 12});
        GLYPHS.put(' ', new int[]{0, 0, 0, 0, 0, 0, 0});
        GLYPHS.put('-', new int[]{0, 0, 0, 31, 0, 0, 0});
        GLYPHS.put('.', new int[]{0, 0, 0, 0, 0, 12, 12});
        GLYPHS.put(',', new int[]{0, 0, 0, 0, 12, 4, 8});
        GLYPHS.put('\'', new int[]{12, 4, 8, 0, 0, 0, 0});
        GLYPHS.put('/', new int[]{0, 1, 2, 4, 8, 16, 0});
        GLYPHS.put(':', new int[]{0, 12, 12, 0, 12, 12, 0});
        GLYPHS.put('!', new int[]{4, 4, 4, 4, 4, 0, 4});
        GLYPHS.put('?', new int[]{14, 17, 1, 2, 4, 0, 4});
        GLYPHS.put('(', new int[]{2, 4, 8, 8, 8, 4, 2});
        GLYPHS.put(')', new int[]{8, 4, 2, 2, 2, 4, 8});
        GLYPHS.put('+', new int[]{0, 4, 4, 31, 4, 4, 0});
        GLYPHS.put('&', new int[]{12, 18, 20, 8, 21, 18, 13});
        GLYPHS.put('>', new int[]{8, 4, 2, 1, 2, 4, 8});
        GLYPHS.put('<', new int[]{2, 4, 8, 16, 8, 4, 2});
    }

    private DotFont() {
    }

    /** Row bits for a character, top row first; bit 4 is the leftmost dot. Unknown characters are blank. */
    public static int[] glyph(char c) {
        int[] g = GLYPHS.get(Character.toUpperCase(c));
        return g != null ? g : GLYPHS.get(' ');
    }

    public static boolean dot(char c, int col, int row) {
        return (glyph(c)[row] >> (WIDTH - 1 - col) & 1) != 0;
    }
}
