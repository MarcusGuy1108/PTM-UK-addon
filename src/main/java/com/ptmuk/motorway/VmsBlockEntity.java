package com.ptmuk.motorway;

import com.ptmuk.network.EditBlockDataMessage;
import com.ptmuk.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** A variable message sign: three lines of amber dot-matrix text and optional flashing lanterns. */
public class VmsBlockEntity extends BlockEntity implements EditBlockDataMessage.Editable {
    public static final int MIN_WIDTH = 2;
    public static final int MAX_WIDTH = 8;
    public static final int LINES = 3;
    public static final int MAX_CHARS = 24;

    public int width = 5;
    public boolean lanterns = true;
    public final String[] lines = {"", "", ""};

    public VmsBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VMS.get(), pos, state);
    }

    /** Characters that fit on one line at this width. */
    public int charsPerLine() {
        return Math.min(MAX_CHARS, (int) ((width - 0.3) * VmsLayout.DOTS_PER_BLOCK / 6));
    }

    public boolean blank() {
        for (String l : lines) {
            if (!l.isBlank()) {
                return false;
            }
        }
        return true;
    }

    public void write(CompoundTag tag) {
        tag.putInt("width", width);
        tag.putBoolean("lanterns", lanterns);
        for (int i = 0; i < LINES; i++) {
            tag.putString("line" + i, lines[i]);
        }
    }

    public void read(CompoundTag tag) {
        width = Mth.clamp(tag.contains("width") ? tag.getInt("width") : 5, MIN_WIDTH, MAX_WIDTH);
        lanterns = !tag.contains("lanterns") || tag.getBoolean("lanterns");
        for (int i = 0; i < LINES; i++) {
            String s = tag.getString("line" + i).toUpperCase();
            lines[i] = s.length() > MAX_CHARS ? s.substring(0, MAX_CHARS) : s;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        write(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        read(tag);
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
    public void applyEdit(CompoundTag tag) {
        read(tag);
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(width / 2.0 + 1, 0, width / 2.0 + 1).expandTowards(0, 2, 0);
    }
}
