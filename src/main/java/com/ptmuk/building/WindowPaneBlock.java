package com.ptmuk.building;

import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/** A framed window that joins up like a glass pane (UPVC, sash, shop, office glazing...). */
public class WindowPaneBlock extends IronBarsBlock {
    public WindowPaneBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(0.3F).sound(SoundType.GLASS).noOcclusion());
    }
}
