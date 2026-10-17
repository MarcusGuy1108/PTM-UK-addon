package com.ptmuk.road.plan;

import java.util.List;

/**
 * The cross-section of a road: lanes, markings, kerbs, verges and pavements. Widths are in
 * blocks. Across a two-way road the centre line takes a block of its own, so the carriageway is
 * always an odd number of blocks wide and every line runs down the middle of a block.
 */
public final class RoadStyle {
    public enum Centre { NONE, DASHED, HAZARD, DOUBLE_WHITE }

    /** Yellow lines along the kerbs (parking restrictions). AUTO: single, double near junctions. */
    public enum Yellow { NONE, SINGLE, DOUBLE, AUTO }

    /** What the pavements are surfaced with (a slab of the same is used for half heights). */
    public static final List<String> FOOTWAYS = List.of("ptm2:fresh_stone_sidewalk", "ptm2:used_asphalt_sidewalk",
            "ptm2:fresh_asphalt_sidewalk", "ptmuk:paving_slabs", "ptmuk:block_paving_grey");

    public String name = "Main road";
    /** Lanes in each direction (a one-way road: lanes in total). */
    public int lanes = 1;
    public int laneWidth = 6;
    public boolean oneWay;
    /** Dual carriageway: the two directions split by a central reservation. */
    public boolean dual;
    public int reservation = 3;
    public Centre centre = Centre.DASHED;
    public boolean laneLinesSolid;
    public boolean edgeLines;
    public Yellow yellow = Yellow.AUTO;
    /** Older plans: double yellows on or off (now {@link #yellow}). */
    public boolean doubleYellow;
    public String footSurface = FOOTWAYS.get(0);
    /** Raised kerb and pavement half a block above the road. */
    public boolean kerbs = true;
    /** Each side, counting the kerb. 0 = no pavement. */
    public int pavement = 4;
    /** Grass between the kerb and the pavement (or beside the road when there's no pavement). */
    public int verge = 0;
    public boolean lights = true;
    public int lightSpacing = 28;
    /** Radius of the kerb round the corners where this road meets another. */
    public int cornerRadius = 8;
    public String surface = "ptm2:fresh_asphalt_road";

    public RoadStyle copy() {
        RoadStyle s = new RoadStyle();
        s.name = name;
        s.lanes = lanes;
        s.laneWidth = laneWidth;
        s.oneWay = oneWay;
        s.dual = dual;
        s.reservation = reservation;
        s.centre = centre;
        s.laneLinesSolid = laneLinesSolid;
        s.edgeLines = edgeLines;
        s.doubleYellow = doubleYellow;
        s.yellow = yellow;
        s.footSurface = footSurface;
        s.kerbs = kerbs;
        s.pavement = pavement;
        s.verge = verge;
        s.lights = lights;
        s.lightSpacing = lightSpacing;
        s.cornerRadius = cornerRadius;
        s.surface = surface;
        return s;
    }

    /** Blocks of carriageway for one direction (lanes plus the lane lines between them). */
    public int directionWidth() {
        return lanes * laneWidth + (lanes - 1);
    }

    /** Distance from the centre line to the edge of the carriageway. */
    public double halfCarriageway() {
        if (oneWay) {
            int w = directionWidth();
            return (w % 2 == 0 ? w + 1 : w) / 2.0;
        }
        double middle = dual ? reservationWidth() / 2.0 : 0.5;
        return middle + directionWidth();
    }

    public int reservationWidth() {
        int r = Math.max(1, reservation);
        return r % 2 == 0 ? r + 1 : r;
    }

    public int kerbWidth() {
        return kerbs ? 1 : 0;
    }

    /** Paved footway behind the kerb (and verge). */
    public int footway() {
        return Math.max(0, pavement - kerbWidth());
    }

    public double halfOuter() {
        return halfCarriageway() + kerbWidth() + verge + footway();
    }

    public int totalWidth() {
        return (int) Math.round(halfOuter() * 2);
    }

    // ------------------------------------------------------------------ presets

    public static RoadStyle mainRoad() {
        return new RoadStyle();
    }

    public static RoadStyle residential() {
        RoadStyle s = new RoadStyle();
        s.name = "Residential street";
        s.yellow = Yellow.NONE;
        s.laneWidth = 4;
        s.pavement = 3;
        s.centre = Centre.NONE;
        s.lightSpacing = 24;
        s.cornerRadius = 6;
        return s;
    }

    public static RoadStyle wideMainRoad() {
        RoadStyle s = new RoadStyle();
        s.name = "Wide main road (4 lanes)";
        s.lanes = 2;
        s.laneWidth = 5;
        s.centre = Centre.DOUBLE_WHITE;
        s.cornerRadius = 7;
        return s;
    }

    public static RoadStyle busyHighStreet() {
        RoadStyle s = new RoadStyle();
        s.name = "High street (double yellows)";
        s.laneWidth = 5;
        s.yellow = Yellow.DOUBLE;
        s.footSurface = "ptmuk:paving_slabs";
        s.pavement = 5;
        s.lightSpacing = 22;
        return s;
    }

    public static RoadStyle dualCarriageway() {
        RoadStyle s = new RoadStyle();
        s.name = "Dual carriageway";
        s.yellow = Yellow.NONE;
        s.lanes = 2;
        s.laneWidth = 5;
        s.dual = true;
        s.reservation = 3;
        s.centre = Centre.NONE;
        s.edgeLines = true;
        s.kerbs = false;
        s.pavement = 0;
        s.verge = 3;
        s.lightSpacing = 32;
        s.cornerRadius = 9;
        return s;
    }

    public static RoadStyle countryLane() {
        RoadStyle s = new RoadStyle();
        s.name = "Country road";
        s.yellow = Yellow.NONE;
        s.laneWidth = 4;
        s.kerbs = false;
        s.pavement = 0;
        s.verge = 2;
        s.edgeLines = true;
        s.lights = false;
        s.cornerRadius = 6;
        return s;
    }

    public static RoadStyle oneWayStreet() {
        RoadStyle s = new RoadStyle();
        s.name = "One-way street";
        s.oneWay = true;
        s.lanes = 2;
        s.laneWidth = 4;
        s.centre = Centre.NONE;
        s.pavement = 3;
        s.cornerRadius = 5;
        return s;
    }

    public static RoadStyle slipRoad() {
        RoadStyle s = new RoadStyle();
        s.name = "Slip road";
        s.yellow = Yellow.NONE;
        s.oneWay = true;
        s.lanes = 1;
        s.laneWidth = 5;
        s.centre = Centre.NONE;
        s.edgeLines = true;
        s.kerbs = false;
        s.pavement = 0;
        s.verge = 3;
        s.lights = false;
        s.cornerRadius = 9;
        return s;
    }

    public static List<RoadStyle> presets() {
        return List.of(mainRoad(), residential(), busyHighStreet(), wideMainRoad(), dualCarriageway(), countryLane(),
                oneWayStreet(), slipRoad());
    }
}
