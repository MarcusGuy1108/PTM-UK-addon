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
    PUSH_BUTTON_UNIT("push_button_unit", Mount.POLE);

    public enum Mount { BELOW_HEAD, ABOVE_HEAD, POLE }

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
