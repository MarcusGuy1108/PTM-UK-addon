package com.ptmuk.town;

/** The street furniture the Town Builder lays out along a pavement, and how far apart each goes by default. */
public enum StreetItem {
    BENCH_WOOD("Bench (wooden)", "bench_wood", null, 0, 14),
    BENCH_METAL("Bench (metal)", "bench_metal", null, 0, 14),
    LITTER_BIN("Litter bin", "litter_bin", null, 0, 14),
    DOG_BIN("Dog waste bin", "dog_waste_bin", null, 0, 24),
    BOLLARD_IRON("Cast iron bollard", "bollard_cast_iron", null, 0, 2),
    BOLLARD_STEEL("Steel bollard", "bollard_steel", null, 0, 2),
    CYCLE_STAND("Cycle stand", "bike_stand", null, 0, 3),
    PLANTER("Brick planter", "brick_planter", null, 0, 8),
    TROUGH("Flower trough", "flower_trough", null, 0, 6),
    STREET_LIGHT("Street light", "street_light_led", "sign_pole_galvanised", 5, 20),
    SODIUM_LIGHT("Old orange street light", "street_light_sodium", "sign_pole_galvanised", 5, 20),
    HERITAGE_LAMP("Heritage lamp post", "heritage_lantern", "heritage_post_black", 4, 16),
    HERITAGE_CLUSTER("Heritage lamp, three lanterns", "heritage_lantern_cluster", "heritage_post_black", 4, 16),
    GUARD_RAIL("Black guard rail", "black_guardrail", null, -1, 1),
    PILLAR_BOX("Pillar box", "pillar_box", null, 0, 40),
    GRIT_BIN("Grit bin", "grit_bin", null, 0, 40);

    public final String title;
    /** The block (ptmuk:...): the furniture, the lamp on top, or the fence. */
    final String block;
    /** For lamps, the post it stands on, and how many blocks of post. -1 is a fence that runs the whole way. */
    final String post;
    final int postHeight;
    public final int spacing;

    StreetItem(String title, String block, String post, int postHeight, int spacing) {
        this.title = title;
        this.block = block;
        this.post = post;
        this.postHeight = postHeight;
        this.spacing = spacing;
    }
}
