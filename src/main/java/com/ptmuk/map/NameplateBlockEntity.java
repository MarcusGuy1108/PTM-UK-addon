package com.ptmuk.map;

import com.ptmuk.network.EditBlockDataMessage;
import com.ptmuk.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** The street name, the line under it (district and postcode), and whether that line is red (as in London). */
public class NameplateBlockEntity extends BlockEntity implements EditBlockDataMessage.Editable {
    private static final int MAX = 32;

    public String name = "";
    public String area = "";
    public boolean red;

    public NameplateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.NAMEPLATE.get(), pos, state);
    }

    public void read(CompoundTag tag) {
        name = clip(tag.getString("name"));
        area = clip(tag.getString("area"));
        red = tag.getBoolean("red");
    }

    private static String clip(String s) {
        return s.length() > MAX ? s.substring(0, MAX) : s;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("name", name);
        tag.putString("area", area);
        tag.putBoolean("red", red);
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

    /** New words: shown on the plate, and given to the road beside it on the map. */
    @Override
    public void applyEdit(CompoundTag tag) {
        read(tag);
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        if (level instanceof ServerLevel server && !name.isBlank()) {
            MapPlaces.nameRoadNear(server, worldPosition, name);
        }
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1.5, 0, 1.5);
    }
}
