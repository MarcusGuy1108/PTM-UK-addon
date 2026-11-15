package com.ptmuk.darts;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A dart in flight: sticks where it lands; on a dartboard, the board scores it. It's taken out of
 * the board when its thrower's three darts are done (or after half a minute anywhere).
 */
public class DartEntity extends AbstractArrow {
    private int collectIn = -1;

    public DartEntity(EntityType<? extends AbstractArrow> type, Level level) {
        super(type, level);
        pickup = Pickup.DISALLOWED;
        setBaseDamage(0.5);
    }

    public DartEntity(Level level, LivingEntity thrower) {
        super(DartsRegistry.DART.get(), thrower, level);
        pickup = Pickup.DISALLOWED;
        setBaseDamage(0.5);
    }

    /** Out of the board in a moment (the thrower's visit's over). */
    public void collect(int ticks) {
        if (collectIn < 0 || collectIn > ticks) {
            collectIn = ticks;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            if (collectIn >= 0 && --collectIn == 0 || inGroundTime > 600) {
                discard();
            }
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        BlockPos pos = hit.getBlockPos();
        if (!level().isClientSide && getOwner() instanceof Player thrower && level().getBlockEntity(pos) instanceof DartboardBlockEntity board) {
            board.hit(thrower, hit.getLocation(), this);
        }
    }

    @Override
    protected SoundEvent getDefaultHitGroundSoundEvent() {
        return SoundEvents.WOOD_HIT;
    }

    @Override
    protected ItemStack getPickupItem() {
        return ItemStack.EMPTY;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("collect", collectIn);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        collectIn = tag.getInt("collect");
    }
}
