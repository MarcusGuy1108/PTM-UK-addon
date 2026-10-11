package com.ptmuk.home;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The oven cooks food like a smoker (twice as fast as a furnace); the hob glows while it's on. */
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

    @Override
    protected int getBurnDuration(ItemStack fuel) {
        return super.getBurnDuration(fuel) / 2;
    }
}
