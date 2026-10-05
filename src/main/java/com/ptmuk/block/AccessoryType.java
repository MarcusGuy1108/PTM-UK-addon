package com.ptmuk.block;

/** Non-switching signal furniture that mounts in line with a signal head. */
public enum AccessoryType {
    NO_LEFT_TURN_SIGN("no_left_turn_sign", Mount.BELOW_HEAD),
    NO_RIGHT_TURN_SIGN("no_right_turn_sign", Mount.BELOW_HEAD),
    NO_U_TURN_SIGN("no_u_turn_sign", Mount.BELOW_HEAD),
    AHEAD_ONLY_SIGN("ahead_only_sign", Mount.BELOW_HEAD),
    TURN_LEFT_SIGN("turn_left_sign", Mount.BELOW_HEAD),
    TURN_RIGHT_SIGN("turn_right_sign", Mount.BELOW_HEAD),
    EXCEPT_BUSES_SIGN("except_buses_sign", Mount.BELOW_HEAD),
    /** Above-ground vehicle detector that sits on top of a head. */
    DETECTOR("signal_detector", Mount.ABOVE_HEAD),
    /** Pedestrian push-button unit with WAIT indicator, mounted on the pole at waist height. */
    PUSH_BUTTON_UNIT("push_button_unit", Mount.POLE),

    // UK road signs, mounted on a pole (or wall) with clamp brackets
    SPEED_20_SIGN("speed_20_sign", Mount.SIGN),
    SPEED_30_SIGN("speed_30_sign", Mount.SIGN),
    SPEED_40_SIGN("speed_40_sign", Mount.SIGN),
    SPEED_50_SIGN("speed_50_sign", Mount.SIGN),
    SPEED_60_SIGN("speed_60_sign", Mount.SIGN),
    SPEED_70_SIGN("speed_70_sign", Mount.SIGN),
    NATIONAL_SPEED_LIMIT_SIGN("national_speed_limit_sign", Mount.SIGN),
    NO_ENTRY_SIGN("no_entry_sign", Mount.SIGN),
    GIVE_WAY_SIGN("give_way_sign", Mount.SIGN),
    STOP_SIGN("stop_sign", Mount.SIGN),
    ONE_WAY_SIGN("one_way_sign", Mount.SIGN),
    KEEP_LEFT_SIGN("keep_left_sign", Mount.SIGN),
    PARKING_SIGN("parking_sign", Mount.SIGN),
    PAY_AT_MACHINE_SIGN("pay_at_machine_sign", Mount.SIGN),
    DISABLED_PARKING_SIGN("disabled_parking_sign", Mount.SIGN),
    TRAFFIC_SIGNALS_AHEAD_SIGN("traffic_signals_ahead_sign", Mount.SIGN),
    PEDESTRIAN_CROSSING_SIGN("pedestrian_crossing_sign", Mount.SIGN),
    CHILDREN_SIGN("children_sign", Mount.SIGN),
    ROADWORKS_SIGN("roadworks_sign", Mount.SIGN),
    SPEED_CAMERA_SIGN("speed_camera_sign", Mount.SIGN),
    BUS_LANE_SIGN("bus_lane_sign", Mount.SIGN),
    BUSES_ONLY_SIGN("buses_only_sign", Mount.SIGN),
    BUS_STOP_CLEARWAY_SIGN("bus_stop_clearway_sign", Mount.SIGN);

    public enum Mount { BELOW_HEAD, ABOVE_HEAD, POLE, SIGN }

    private final String id;
    public final Mount mount;

    AccessoryType(String id, Mount mount) {
        this.id = id;
        this.mount = mount;
    }

    public String id() {
        return id;
    }
}
