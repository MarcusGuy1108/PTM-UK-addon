package com.ptmuk.block;

import com.ptmuk.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Marker block entity for incandescent (classic) heads. It stores nothing; it exists so the
 * client can draw the lamps fading on and off like real filament bulbs. The brightness
 * fields are client-side render state only.
 */
public class ClassicLampBlockEntity extends BlockEntity {
    /** Current lamp brightness per aspect (0..1), in signal_parts.json aspect order. */
    public float[] brightness;
    public long lastFrameNanos;

    public ClassicLampBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CLASSIC_LAMP.get(), pos, state);
    }

    @Override
    public AABB getRenderBoundingBox() {
        // heads are drawn up to a block and a bit away from their own block (post mounting)
        return new AABB(worldPosition).inflate(1.5);
    }
}
