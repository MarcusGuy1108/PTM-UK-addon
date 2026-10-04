package com.ptmuk.block;

import com.rinventor.ptm2.core.properties.TrafficLightTypes;

/**
 * Which UK signal head this is, and the PTM2 traffic light parameters it maps to. The
 * parameters decide which lanes, vehicles and pedestrians PTM2 applies the light to, so
 * each type copies the matching PTM2 light (e.g. LEFT_FILTER behaves like PTM2's traffic_left4).
 */
public enum SignalType {
    /** Red / amber / green. */
    STANDARD("signal", 3, TrafficLightTypes.STRAIGHT, false, true),
    /** Red / amber / green left arrow. */
    LEFT_ARROW("left_arrow_signal", 3, TrafficLightTypes.LEFT, false, true),
    /** Red / amber / green right arrow. */
    RIGHT_ARROW("right_arrow_signal", 3, TrafficLightTypes.RIGHT, false, true),
    /** Red / amber / green ahead arrow. */
    AHEAD_ARROW("ahead_arrow_signal", 3, TrafficLightTypes.STRAIGHT, false, true),
    /** Red / amber / green plus a green left filter arrow beside the green. */
    LEFT_FILTER("left_filter_signal", 4, TrafficLightTypes.SECTION_LEFT, false, true),
    /** Red / amber / green plus a green right filter arrow beside the green. */
    RIGHT_FILTER("right_filter_signal", 4, TrafficLightTypes.SECTION_RIGHT, false, true),
    /** Full-size cycle signal with bicycle symbols. */
    CYCLE("cycle_signal", 3, TrafficLightTypes.CYCLE, true, false),
    /** Small low-level cycle signal mounted at eye height. */
    LOW_LEVEL_CYCLE("low_level_cycle_signal", 3, TrafficLightTypes.CYCLE, true, false),
    /** Far-side pedestrian signal (pelican): red man, green man, flashing green man. */
    PELICAN("pelican_signal", 2, TrafficLightTypes.OTHER, true, false),
    /** Near-side pedestrian signal (puffin): red man and green man, no flashing. */
    PUFFIN("puffin_signal", 2, TrafficLightTypes.OTHER, true, false),
    /** Toucan crossing signal: red man, green man and green cycle; blank during clearance. */
    TOUCAN("toucan_signal", 2, TrafficLightTypes.OTHER, true, false);

    private final String id;
    public final int lightCount;
    public final TrafficLightTypes direction;
    public final boolean pedestrian;
    /** Whether the head can carry a backing board. */
    public final boolean boardable;

    SignalType(String id, int lightCount, TrafficLightTypes direction, boolean pedestrian, boolean boardable) {
        this.id = id;
        this.lightCount = lightCount;
        this.direction = direction;
        this.pedestrian = pedestrian;
        this.boardable = boardable;
    }

    public String id() {
        return id;
    }
}
