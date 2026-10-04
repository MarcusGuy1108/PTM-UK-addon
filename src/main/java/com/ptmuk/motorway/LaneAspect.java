package com.ptmuk.motorway;

import net.minecraft.util.StringRepresentable;

/** What a motorway lane signal (MS4) shows. Each one has its own baked dot-matrix texture. */
public enum LaneAspect implements StringRepresentable {
    OFF("off", "Blank"),
    SPEED_70("speed_70", "70"),
    SPEED_60("speed_60", "60"),
    SPEED_50("speed_50", "50"),
    SPEED_40("speed_40", "40"),
    SPEED_30("speed_30", "30"),
    SPEED_20("speed_20", "20"),
    NSL("nsl", "End of restriction"),
    RED_X("red_x", "Lane closed (red X)"),
    MERGE_LEFT("merge_left", "Move to left lane"),
    MERGE_RIGHT("merge_right", "Move to right lane"),
    QUEUE("queue", "Queue"),
    FOG("fog", "Fog");

    private final String name;
    public final String label;

    LaneAspect(String name, String label) {
        this.name = name;
        this.label = label;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public LaneAspect next(boolean backwards) {
        LaneAspect[] v = values();
        return v[(ordinal() + (backwards ? v.length - 1 : 1)) % v.length];
    }
}
