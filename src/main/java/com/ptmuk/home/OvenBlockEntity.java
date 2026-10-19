package com.ptmuk.home;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The oven (and the microwave) cooks food like a smoker, twice as fast as a furnace, and runs off
 * the mains: it switches itself on whenever there's food in it, no fuel needed (any put in the
 * fuel slot just sits there). The hob glows while it's on.
 */
public class OvenBlockEntity extends AbstractFurnaceBlockEntity {
    public OvenBlockEntity(BlockPos pos, BlockState state) {
        super(HomeBlocks.OVEN_BE.get(), pos, state, RecipeType.SMOKING);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new SmokerMenu(id, inventory, this, this.dataAccess);
    }

    /** Keep it "lit" while there's something in it that it can cook. */
    void power() {
        if (level == null || dataAccess.get(0) > 1 && !getItem(0).isEmpty()) {
            return;
        }
        boolean food = !getItem(0).isEmpty() && level.getRecipeManager().getRecipeFor(RecipeType.SMOKING, this, level).isPresent();
        dataAccess.set(0, food ? 200 : 0);                 // off as soon as it's empty
        dataAccess.set(1, 200);
    }

    /** The furnace code only lights the block when its own fuel catches; switched on by power(), show it. */
    void showLit(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        boolean lit = dataAccess.get(0) > 0;
        if (state.hasProperty(HomeBlock.LIT) && state.getValue(HomeBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(HomeBlock.LIT, lit), 3);
        }
    }
}
