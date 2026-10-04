package com.ptmuk.block;

import net.minecraft.world.level.block.SoundType;

/**
 * UK street furniture. The shape is the collision / selection box for a north-facing block
 * (block pixels); it is rotated for other facings. Models come from tools/furniture.py.
 */
public enum FurnitureType {
    WHEELIE_BIN_BLACK("wheelie_bin_black", 3, 0, 2, 13, 17, 14, 0, SoundType.WOOD),
    WHEELIE_BIN_GREY("wheelie_bin_grey", 3, 0, 2, 13, 17, 14, 0, SoundType.WOOD),
    WHEELIE_BIN_GREEN("wheelie_bin_green", 3, 0, 2, 13, 17, 14, 0, SoundType.WOOD),
    WHEELIE_BIN_BLUE("wheelie_bin_blue", 3, 0, 2, 13, 17, 14, 0, SoundType.WOOD),
    WHEELIE_BIN_BROWN("wheelie_bin_brown", 3, 0, 2, 13, 17, 14, 0, SoundType.WOOD),
    COMMUNAL_BIN("communal_bin", 0, 0, 0, 16, 21, 16, 0, SoundType.METAL),
    LITTER_BIN("litter_bin", 3, 0, 3, 13, 16, 13, 0, SoundType.METAL),
    DOG_WASTE_BIN("dog_waste_bin", 4, 0, 5, 12, 16, 11, 0, SoundType.METAL),
    GRIT_BIN("grit_bin", 1, 0, 3, 15, 12, 13, 0, SoundType.WOOD),
    PILLAR_BOX("pillar_box", 3, 0, 3, 13, 24, 13, 0, SoundType.METAL),
    PHONE_BOX("phone_box", 1, 0, 1, 15, 24, 15, 6, SoundType.METAL),
    BUS_SHELTER("bus_shelter", 0, 0, 2, 16, 24, 16, 9, SoundType.METAL),
    BUS_STOP_FLAG("bus_stop_flag", 6, 0, 6, 10, 24, 10, 0, SoundType.METAL),
    BENCH_METAL("bench_metal", 0, 0, 3, 16, 9, 13, 0, SoundType.METAL),
    BENCH_WOOD("bench_wood", 0, 0, 3, 16, 9, 13, 0, SoundType.WOOD),
    BOLLARD_CAST_IRON("bollard_cast_iron", 5, 0, 5, 11, 15, 11, 0, SoundType.METAL),
    BOLLARD_STEEL("bollard_steel", 5.5, 0, 5.5, 10.5, 14, 10.5, 0, SoundType.METAL),
    KEEP_LEFT_BOLLARD("keep_left_bollard", 4, 0, 4, 12, 18, 12, 7, SoundType.STONE),
    BELISHA_BEACON("belisha_beacon", 6, 0, 6, 10, 24, 10, 10, SoundType.METAL),
    PAY_AND_DISPLAY("pay_and_display", 3, 0, 4, 13, 24, 12, 3, SoundType.METAL),
    PARKING_METER("parking_meter", 6, 0, 6, 10, 22, 10, 0, SoundType.METAL),
    EV_CHARGER("ev_charger", 4, 0, 5, 12, 22, 11, 4, SoundType.METAL),
    BIKE_STAND("bike_stand", 1, 0, 7, 15, 13, 9, 0, SoundType.METAL),
    TELECOMS_CABINET("telecoms_cabinet", 0, 0, 4, 16, 20, 12, 0, SoundType.METAL),
    CONTROLLER_CABINET("controller_cabinet", 1, 0, 4, 15, 22, 12, 0, SoundType.METAL),
    FEEDER_PILLAR("feeder_pillar", 3, 0, 5, 13, 15, 11, 0, SoundType.METAL),
    HYDRANT_MARKER("hydrant_marker", 6, 0, 6, 10, 13, 10, 0, SoundType.METAL),
    MANHOLE_COVER("manhole_cover", 0, 0, 0, 16, 0.6, 16, 0, SoundType.METAL),
    DRAIN_GRATE("drain_grate", 1, 0, 0, 15, 0.6, 7, 0, SoundType.METAL),
    STREET_LIGHT_LED("street_light_led", 5, 0, 5, 11, 4, 11, 15, SoundType.METAL),
    STREET_LIGHT_SODIUM("street_light_sodium", 5, 0, 5, 11, 4, 11, 15, SoundType.METAL);

    private final String id;
    public final double[] box;
    public final int light;
    public final SoundType sound;

    FurnitureType(String id, double x1, double y1, double z1, double x2, double y2, double z2, int light, SoundType sound) {
        this.id = id;
        this.box = new double[]{x1, y1, z1, x2, y2, z2};
        this.light = light;
        this.sound = sound;
    }

    public String id() {
        return id;
    }
}
