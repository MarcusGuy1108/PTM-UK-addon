package com.ptmuk.block;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/** Plain UK building, roofing and paving materials (block, slab and stairs each). Textures come from tools/building.py. */
public enum BuildingMaterial {
    PAVING_SLABS("paving_slabs", MapColor.STONE, SoundType.STONE),
    BLOCK_PAVING_RED("block_paving_red", MapColor.TERRACOTTA_RED, SoundType.STONE),
    BLOCK_PAVING_GREY("block_paving_grey", MapColor.STONE, SoundType.STONE),
    TACTILE_PAVING_BUFF("tactile_paving_buff", MapColor.SAND, SoundType.STONE),
    TACTILE_PAVING_RED("tactile_paving_red", MapColor.TERRACOTTA_RED, SoundType.STONE),
    TACTILE_PAVING_CORDUROY("tactile_paving_corduroy", MapColor.SAND, SoundType.STONE),
    GRANITE_SETTS("granite_setts", MapColor.STONE, SoundType.STONE),
    CONCRETE_KERB("concrete_kerb", MapColor.STONE, SoundType.STONE),
    TARMAC("tarmac", MapColor.COLOR_BLACK, SoundType.STONE),
    TARMAC_RED("tarmac_red", MapColor.TERRACOTTA_RED, SoundType.STONE),
    RED_BRICK("red_brick", MapColor.TERRACOTTA_RED, SoundType.STONE),
    LONDON_STOCK_BRICK("london_stock_brick", MapColor.SAND, SoundType.STONE),
    BLUE_ENGINEERING_BRICK("blue_engineering_brick", MapColor.COLOR_BLUE, SoundType.STONE),
    PEBBLEDASH("pebbledash", MapColor.TERRACOTTA_WHITE, SoundType.STONE),
    WHITE_RENDER("white_render", MapColor.SNOW, SoundType.STONE),
    PORTLAND_STONE("portland_stone", MapColor.SAND, SoundType.STONE),
    GREEN_FAIENCE("green_faience", MapColor.COLOR_GREEN, SoundType.STONE),
    BURGUNDY_FAIENCE("burgundy_faience", MapColor.CRIMSON_NYLIUM, SoundType.STONE),
    ROOF_SLATE("roof_slate", MapColor.COLOR_GRAY, SoundType.STONE),
    ROOF_CLAY_TILES("roof_clay_tiles", MapColor.TERRACOTTA_RED, SoundType.STONE),
    ROOF_CONCRETE_TILES("roof_concrete_tiles", MapColor.TERRACOTTA_BROWN, SoundType.STONE);

    private final String id;
    private final MapColor colour;
    private final SoundType sound;

    BuildingMaterial(String id, MapColor colour, SoundType sound) {
        this.id = id;
        this.colour = colour;
        this.sound = sound;
    }

    public String id() {
        return id;
    }

    public BlockBehaviour.Properties properties() {
        return BlockBehaviour.Properties.of().mapColor(colour).strength(1.5F, 6.0F).sound(sound).requiresCorrectToolForDrops();
    }
}
