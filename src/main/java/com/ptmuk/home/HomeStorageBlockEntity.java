package com.ptmuk.home;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The inside of a cupboard, drawers, wardrobe or fridge: one to six rows of nine slots. */
public class HomeStorageBlockEntity extends RandomizableContainerBlockEntity {
    private final int rows;
    private NonNullList<ItemStack> items;

    public HomeStorageBlockEntity(BlockPos pos, BlockState state) {
        super(HomeBlocks.STORAGE_BE.get(), pos, state);
        int r = state.getBlock() instanceof HomeBlock home ? home.type().rows : 3;
        rows = r == 4 || r == 5 ? 6 : Math.max(1, Math.min(6, r));
        items = NonNullList.withSize(rows * 9, ItemStack.EMPTY);
    }

    @Override
    public int getContainerSize() {
        return rows * 9;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> list) {
        items = list;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return switch (rows) {
            case 1 -> new ChestMenu(MenuType.GENERIC_9x1, id, inventory, this, 1);
            case 2 -> new ChestMenu(MenuType.GENERIC_9x2, id, inventory, this, 2);
            case 6 -> ChestMenu.sixRows(id, inventory, this);
            default -> ChestMenu.threeRows(id, inventory, this);
        };
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        if (!tryLoadLootTable(tag)) {
            ContainerHelper.loadAllItems(tag, items);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!trySaveLootTable(tag)) {
            ContainerHelper.saveAllItems(tag, items);
        }
    }

    @Override
    public void startOpen(Player player) {
        if (!player.isSpectator() && level != null) {
            level.playSound(null, worldPosition, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.5F, 1.2F);
        }
    }

    @Override
    public void stopOpen(Player player) {
        if (!player.isSpectator() && level != null) {
            level.playSound(null, worldPosition, SoundEvents.BARREL_CLOSE, SoundSource.BLOCKS, 0.5F, 1.2F);
        }
    }
}
