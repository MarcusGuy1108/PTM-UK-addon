package com.ptmuk.home;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** An invisible seat that a player sitting on furniture rides. It goes when they stand up or the
 * furniture is broken. */
public class SeatEntity extends Entity {
    public SeatEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && (getPassengers().isEmpty() || !(level().getBlockState(blockPosition()).getBlock() instanceof HomeBlock))) {
            discard();
        }
    }

    @Override
    public double getPassengersRidingOffset() {
        return -0.05;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        BlockPos pos = blockPosition();
        BlockState state = level().getBlockState(pos);
        if (state.getBlock() instanceof HomeBlock) {
            Direction front = state.getValue(HomeBlock.FACING);
            BlockPos out = pos.relative(front);
            if (level().getBlockState(out).getCollisionShape(level(), out).isEmpty()
                    && level().getBlockState(out.above()).getCollisionShape(level(), out.above()).isEmpty()) {
                return Vec3.atBottomCenterOf(out);
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
