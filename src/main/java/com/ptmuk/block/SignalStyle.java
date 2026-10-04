package com.ptmuk.block;

/** Visual family of a UK signal head. Behaviour is identical; only the models differ. */
public enum SignalStyle {
    /** Modern LED heads with short angled cowls (Siemens Helios style). */
    LED("led"),
    /** Modern LED heads with long tunnel hoods. */
    LED_TUNNEL("led_tunnel"),
    /** Older incandescent bulb heads with deep hoods and fresnel lenses. */
    CLASSIC("classic"),
    /** Older bulb heads with small red / amber aspects over a large 300 mm green "pod". */
    CLASSIC_LARGE_GREEN("classic_large_green");

    private final String id;

    SignalStyle(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** Incandescent heads fade their lamps on and off (see ClassicLampRenderer). */
    public boolean isBulb() {
        return this == CLASSIC || this == CLASSIC_LARGE_GREEN;
    }
}
