package com.ptmuk.sign;

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

/** Contents of an editable direction sign. Synced to clients; edited via EditSignMessage. */
public class DirectionSignBlockEntity extends BlockEntity implements EditBlockDataMessage.Editable {
    public static final int MAX_WIDTH = 8;
    public static final int MAX_HEIGHT = 6;
    private static final int MAX_TEXT = 400;

    public SignScheme scheme;
    public SignDiagram diagram = SignDiagram.NONE;
    public int width = 2;
    public int height = 1;
    public boolean legs = true;
    public String header = "";
    public String ahead = "Town Centre";
    public String left = "";
    public String right = "";

    public DirectionSignBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DIRECTION_SIGN.get(), pos, state);
        this.scheme = state.getBlock() instanceof DirectionSignBlock b ? b.defaultScheme : SignScheme.LOCAL;
    }

    public void write(CompoundTag tag) {
        tag.putString("scheme", scheme.name());
        tag.putString("diagram", diagram.name());
        tag.putInt("width", width);
        tag.putInt("height", height);
        tag.putBoolean("legs", legs);
        tag.putString("header", header);
        tag.putString("ahead", ahead);
        tag.putString("left", left);
        tag.putString("right", right);
    }

    public void read(CompoundTag tag) {
        if (tag.contains("scheme")) {
            scheme = SignScheme.byName(tag.getString("scheme"));
        }
        diagram = SignDiagram.byName(tag.getString("diagram"));
        width = Mth.clamp(tag.contains("width") ? tag.getInt("width") : 2, 1, MAX_WIDTH);
        height = Mth.clamp(tag.contains("height") ? tag.getInt("height") : 1, 1, MAX_HEIGHT);
        legs = !tag.contains("legs") || tag.getBoolean("legs");
        header = clip(tag.getString("header"));
        ahead = clip(tag.getString("ahead"));
        left = clip(tag.getString("left"));
        right = clip(tag.getString("right"));
    }

    private static String clip(String s) {
        return s.length() > MAX_TEXT ? s.substring(0, MAX_TEXT) : s;
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

    /** Apply an edit from a player and push it to everyone watching. */
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
        // panel spreads sideways and upwards from this block, legs reach down to the ground
        return new AABB(worldPosition).inflate(width / 2.0 + 1, 0, width / 2.0 + 1).expandTowards(0, height + 1, 0)
                .expandTowards(0, -12, 0);
    }
}
