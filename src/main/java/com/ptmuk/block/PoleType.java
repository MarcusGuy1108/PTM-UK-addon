package com.ptmuk.block;

/** UK street furniture poles. Radius in block pixels (1 px = 62.5 mm). */
public enum PoleType {
    /** 114 mm traffic signal pole, painted black. */
    SIGNAL_BLACK("signal_pole_black", 1.85),
    /** 114 mm traffic signal pole, galvanised grey. */
    SIGNAL_GREY("signal_pole_grey", 1.85),
    /** 76 mm sign pole, galvanised. */
    SIGN_GALVANISED("sign_pole_galvanised", 1.25),
    /** 76 mm sign pole, painted black. */
    SIGN_BLACK("sign_pole_black", 1.25);

    private final String id;
    public final double radius;

    PoleType(String id, double radius) {
        this.id = id;
        this.radius = radius;
    }

    public String id() {
        return id;
    }
}
