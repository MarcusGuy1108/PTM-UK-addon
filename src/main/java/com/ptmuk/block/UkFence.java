package com.ptmuk.block;

import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.SoundType;

/** UK fencing and railings. Connects like a vanilla fence (all are in #minecraft:fences). */
public class UkFence extends FenceBlock {
    public UkFence(SoundType sound) {
        super(Properties.of().strength(2.0F, 6.0F).sound(sound).noOcclusion());
    }
}
