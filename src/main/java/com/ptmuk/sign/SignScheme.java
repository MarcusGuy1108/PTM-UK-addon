package com.ptmuk.sign;

/** UK direction sign colour schemes (TSRGD): background, legend and border colours, ARGB. */
public enum SignScheme {
    LOCAL("Local (white)", 0xFFF8F8F6, 0xFF111111, 0xFF111111, true),
    PRIMARY("Primary route (green)", 0xFF00703C, 0xFFF8F8F6, 0xFFF8F8F6, false),
    MOTORWAY("Motorway (blue)", 0xFF0057A0, 0xFFF8F8F6, 0xFFF8F8F6, false),
    TOURIST("Tourist (brown)", 0xFF6B3A1E, 0xFFF8F8F6, 0xFFF8F8F6, false),
    DIVERSION("Temporary (yellow)", 0xFFFFCC00, 0xFF111111, 0xFF111111, true),
    STREET_NAME("Street name", 0xFFF8F8F6, 0xFF111111, 0xFF111111, true);

    public final String label;
    public final int background;
    public final int legend;
    public final int border;
    /** Black-on-light signs use the heavier typeface (Transport Heavy); white legends Transport Medium. */
    public final boolean heavy;

    SignScheme(String label, int background, int legend, int border, boolean heavy) {
        this.label = label;
        this.background = background;
        this.legend = legend;
        this.border = border;
        this.heavy = heavy;
    }

    public SignScheme next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static SignScheme byName(String name) {
        for (SignScheme s : values()) {
            if (s.name().equals(name)) {
                return s;
            }
        }
        return LOCAL;
    }
}
