package com.ptmuk.block;

/** Visual family of a UK signal head. Behaviour is identical; only the models differ. */
public enum SignalStyle {
    /** Modern LED heads with short angled cowls (Siemens Helios style). */
    LED("led"),
    /** Modern LED heads with long tunnel hoods. */
    LED_TUNNEL("led_tunnel"),
    /** Older incandescent bulb heads with deep hoods and fresnel lenses. */
    CLASSIC("classic");

    private final String id;

    SignalStyle(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
