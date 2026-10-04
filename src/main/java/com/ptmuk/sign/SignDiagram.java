package com.ptmuk.sign;

/** Junction diagram drawn in the middle of a direction sign. */
public enum SignDiagram {
    NONE("No diagram"),
    AHEAD("Ahead"),
    LEFT("Turn left"),
    RIGHT("Turn right"),
    CROSSROADS("Crossroads"),
    T_JUNCTION("T-junction"),
    AHEAD_LEFT("Ahead + left"),
    AHEAD_RIGHT("Ahead + right"),
    ROUNDABOUT("Roundabout"),
    ROUNDABOUT_3("Roundabout (3 exits)"),
    LANES_2("2 lane arrows"),
    LANES_3("3 lane arrows"),
    LANES_4("4 lane arrows");

    public final String label;

    SignDiagram(String label) {
        this.label = label;
    }

    public SignDiagram next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public boolean isLanes() {
        return this == LANES_2 || this == LANES_3 || this == LANES_4;
    }

    public static SignDiagram byName(String name) {
        for (SignDiagram d : values()) {
            if (d.name().equals(name)) {
                return d;
            }
        }
        return NONE;
    }
}
