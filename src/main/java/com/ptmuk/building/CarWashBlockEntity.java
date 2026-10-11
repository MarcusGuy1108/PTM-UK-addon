package com.ptmuk.building;

import com.ptmuk.registry.ModBlockEntities;
import com.rinventor.ptm2.objects.entities.vehicle.Vehicle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The wash cycle: the gantry runs from its parking place at the far end of the bay to the
 * entrance with the brushes spinning (wash), back again with the dryers on (dry), then the
 * board says PLEASE EXIT. Times are shared through the block entity so everyone sees the same.
 */
public class CarWashBlockEntity extends BlockEntity {
    /** A whole wash in ticks. */
    public static final int DURATION = 440;
    /** Bay half width and half length (blocks), gantry travel (half), from the middle of the bay. */
    public static final float BAY_HALF_W = 2.4f, BAY_HALF_L = 4.5f, TRAVEL = 3.6f;

    public enum Phase { IDLE, STOP, WASH, TURN, DRY, EXIT }

    /** Game time the current wash started, or -1. */
    public long start = -1;
    /** Game time the last wash ended. */
    public long lastEnd = Long.MIN_VALUE / 2;
    private boolean occupied;
    private boolean powered;
    private final RandomSource random = RandomSource.create();

    public CarWashBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CAR_WASH.get(), pos, state);
    }

    public boolean running() {
        return start >= 0;
    }

    /** How far through the wash we are, 0..1, or -1 when idle. */
    public float progress(float partialTick) {
        if (start < 0 || level == null) {
            return -1;
        }
        return Math.min(1f, (level.getGameTime() - start + partialTick) / DURATION);
    }

    public static Phase phase(float p) {
        if (p < 0) {
            return Phase.IDLE;
        }
        if (p < 0.06f) {
            return Phase.STOP;
        }
        if (p < 0.47f) {
            return Phase.WASH;
        }
        if (p < 0.53f) {
            return Phase.TURN;
        }
        return p < 0.92f ? Phase.DRY : Phase.EXIT;
    }

    private static float ease(float t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    /** Where the gantry is along the bay: -TRAVEL is its parking place at the far end, +TRAVEL the entrance. */
    public static float gantry(float p) {
        if (p < 0.06f) {
            return -TRAVEL;
        }
        if (p < 0.47f) {
            return -TRAVEL + 2 * TRAVEL * ease((p - 0.06f) / 0.41f);
        }
        if (p < 0.53f) {
            return TRAVEL;
        }
        if (p < 0.92f) {
            return TRAVEL - 2 * TRAVEL * ease((p - 0.53f) / 0.39f);
        }
        return -TRAVEL;
    }

    public Direction facing() {
        return getBlockState().getValue(HorizontalDirectionalBlock.FACING);
    }

    /** A point in the bay's own frame (x across, +z towards the entrance) as a world position. */
    public Vec3 world(double x, double y, double z) {
        double a = Math.toRadians(-facing().toYRot());
        double wx = x * Math.cos(a) + z * Math.sin(a);
        double wz = -x * Math.sin(a) + z * Math.cos(a);
        return new Vec3(worldPosition.getX() + 0.5 + wx, worldPosition.getY() + y, worldPosition.getZ() + 0.5 + wz);
    }

    private AABB bay() {
        Vec3 a = world(-BAY_HALF_W, 0, -BAY_HALF_L), b = world(BAY_HALF_W, 3, BAY_HALF_L);
        return new AABB(a, b);
    }

    public void begin() {
        if (level == null || running()) {
            return;
        }
        start = level.getGameTime();
        level.playSound(null, worldPosition, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.6f, 0.7f);
        sync();
    }

    public void redstone(boolean signal) {
        if (signal && !powered) {
            begin();
        }
        powered = signal;
    }

    public void serverTick() {
        if (level == null || level.getGameTime() % 5 != 0) {
            return;
        }
        long now = level.getGameTime();
        if (running() && now - start >= DURATION) {
            start = -1;
            lastEnd = now;
            level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.6f, 1.2f);
            sync();
        }
        boolean present = !level.getEntitiesOfClass(Entity.class, bay(), e -> e instanceof Vehicle || e instanceof AbstractMinecart
                || e instanceof Boat || (e instanceof Player p && !p.isSpectator())).isEmpty();
        if (present && !occupied && !running()) {
            begin();
        }
        occupied = present;
    }

    /** Water, foam and dryer air from the gantry, and the sounds, while it runs. */
    public void clientTick() {
        if (level == null || !running()) {
            return;
        }
        float p = progress(0);
        Phase phase = phase(p);
        float z = gantry(p);
        long t = level.getGameTime();
        if (phase == Phase.WASH || phase == Phase.STOP) {
            for (int i = 0; i < 4; i++) {
                double x = (random.nextDouble() - 0.5) * 3.6;
                Vec3 at = world(x, 3.6, z + (random.nextDouble() - 0.5) * 0.4);
                level.addParticle(ParticleTypes.FALLING_WATER, at.x, at.y, at.z, 0, 0, 0);
            }
            for (int side = -1; side <= 1; side += 2) {
                Vec3 at = world(side * 1.35, 0.4 + random.nextDouble() * 2.2, z + (random.nextDouble() - 0.5) * 0.6);
                level.addParticle(ParticleTypes.SPLASH, at.x, at.y, at.z, 0, 0, 0);
                if (random.nextInt(3) == 0) {
                    level.addParticle(ParticleTypes.SNEEZE, at.x, at.y, at.z, 0, 0.01, 0);
                }
            }
            if (t % 20 == 0) {
                level.playLocalSound(worldPosition, SoundEvents.WEATHER_RAIN, SoundSource.BLOCKS, 0.5f, 1.5f, false);
            }
        } else if (phase == Phase.DRY) {
            for (int i = 0; i < 2; i++) {
                Vec3 at = world((random.nextDouble() - 0.5) * 3.4, 3.4, z);
                level.addParticle(ParticleTypes.CLOUD, at.x, at.y, at.z, 0, -0.12, 0);
            }
            if (t % 30 == 0) {
                level.playLocalSound(worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.25f, 0.6f, false);
            }
        }
        if (phase != Phase.EXIT && t % 40 == 0) {
            level.playLocalSound(worldPosition, SoundEvents.MINECART_RIDING, SoundSource.BLOCKS, 0.15f, 0.6f, false);
        }
    }

    private void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("start", start);
        tag.putLong("lastEnd", lastEnd);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        start = tag.contains("start") ? tag.getLong("start") : -1;
        lastEnd = tag.contains("lastEnd") ? tag.getLong("lastEnd") : Long.MIN_VALUE / 2;
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(5, 0, 5).expandTowards(0, 5, 0);
    }
}
