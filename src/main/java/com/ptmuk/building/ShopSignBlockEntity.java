package com.ptmuk.building;

import com.ptmuk.network.EditBlockDataMessage;
import com.ptmuk.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** One board of a shop fascia. Boards side by side make a row, and rows stacked on top of each other
 * make one taller sign; the bottom left-hand board carries the text for all of it. */
public class ShopSignBlockEntity extends BlockEntity implements EditBlockDataMessage.Editable {
    /** Longest row of boards treated as one sign. */
    public static final int MAX_SPAN = 24;
    /** Most rows stacked into one sign. */
    public static final int MAX_ROWS = 8;
    private static final int MAX_TEXT = 40;

    public String text = "";
    public String sub = "";
    public int board = 0xFF1C2E4A;
    public int ink = 0xFFFFFFFF;
    public boolean lit = true;

    public ShopSignBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHOP_SIGN.get(), pos, state);
    }

    /** The direction text runs in, as seen from in front of a board facing {@code facing}. */
    public static Direction along(Direction facing) {
        return facing.getCounterClockWise();
    }

    private static boolean sameRow(BlockGetter level, BlockPos pos, Direction facing) {
        BlockState s = level.getBlockState(pos);
        return s.getBlock() instanceof ShopSignBlock && s.getValue(ShopSignBlock.FACING) == facing;
    }

    /** The bottom left-hand board of the sign {@code pos} is part of. */
    public static BlockPos anchor(BlockGetter level, BlockPos pos, Direction facing) {
        Direction back = along(facing).getOpposite();
        BlockPos p = pos;
        for (int i = 0; i < MAX_SPAN + MAX_ROWS; i++) {
            if (sameRow(level, p.below(), facing)) {
                p = p.below();
            } else if (sameRow(level, p.relative(back), facing)) {
                p = p.relative(back);
            } else {
                break;
            }
        }
        return p;
    }

    /** How many boards make up the row starting at this (anchor) board. */
    public int span() {
        if (level == null) {
            return 1;
        }
        Direction facing = getBlockState().getValue(ShopSignBlock.FACING);
        Direction dir = along(facing);
        int n = 1;
        while (n < MAX_SPAN && sameRow(level, worldPosition.relative(dir, n), facing)) {
            n++;
        }
        return n;
    }

    /** How many rows of boards are stacked from this (anchor) board up. */
    public int rows() {
        if (level == null) {
            return 1;
        }
        Direction facing = getBlockState().getValue(ShopSignBlock.FACING);
        int n = 1;
        while (n < MAX_ROWS && sameRow(level, worldPosition.above(n), facing)) {
            n++;
        }
        return n;
    }

    /** Another board of the same sign directly above or below (so no moulding between them). */
    public boolean joined(Direction dir) {
        return level != null && sameRow(level, worldPosition.relative(dir), getBlockState().getValue(ShopSignBlock.FACING));
    }

    public boolean isAnchor() {
        if (level == null) {
            return true;
        }
        Direction facing = getBlockState().getValue(ShopSignBlock.FACING);
        return !sameRow(level, worldPosition.relative(along(facing).getOpposite()), facing)
                && !sameRow(level, worldPosition.below(), facing);
    }

    public void write(CompoundTag tag) {
        tag.putString("text", text);
        tag.putString("sub", sub);
        tag.putInt("board", board);
        tag.putInt("ink", ink);
        tag.putBoolean("lit", lit);
    }

    public void read(CompoundTag tag) {
        text = clip(tag.getString("text"));
        sub = clip(tag.getString("sub"));
        if (tag.contains("board")) {
            board = tag.getInt("board") | 0xFF000000;
        }
        if (tag.contains("ink")) {
            ink = tag.getInt("ink") | 0xFF000000;
        }
        lit = !tag.contains("lit") || tag.getBoolean("lit");
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

    /** Text goes to this (anchor) board; the colours go to every board of the sign. */
    @Override
    public void applyEdit(CompoundTag tag) {
        if (level == null) {
            return;
        }
        read(tag);
        sync();
        Direction dir = along(getBlockState().getValue(ShopSignBlock.FACING));
        int span = span(), rows = rows();
        for (int r = 0; r < rows; r++) {
            for (int i = 0; i < span; i++) {
                if ((r > 0 || i > 0) && level.getBlockEntity(worldPosition.relative(dir, i).above(r)) instanceof ShopSignBlockEntity other) {
                    other.board = board;
                    other.ink = ink;
                    other.lit = lit;
                    other.text = "";
                    other.sub = "";
                    other.sync();
                }
            }
        }
    }

    private void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(MAX_SPAN, MAX_ROWS, MAX_SPAN);
    }
}
