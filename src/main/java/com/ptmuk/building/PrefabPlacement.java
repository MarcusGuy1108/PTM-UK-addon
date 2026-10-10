package com.ptmuk.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Where a prefab goes. Prefab structures are built with their front facing south; the placer
 * turns them so the front faces the player (plus any extra quarter turns chosen with the tool),
 * optionally mirrored left to right, with the middle of the front row on the targeted block and
 * the ground layer replacing it. Shared by the client outline and the server.
 */
public record PrefabPlacement(PrefabCatalog.Entry entry, BlockPos origin, Rotation rotation, Mirror mirror, BoundingBox box) {
    public static final String TAG_ID = "prefab";
    public static final String TAG_ROT = "rotation";
    public static final String TAG_MIRROR = "mirror";

    public static PrefabCatalog.Entry selected(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? null : PrefabCatalog.get(tag.getString(TAG_ID));
    }

    public static int turns(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : Math.floorMod(tag.getInt(TAG_ROT), 4);
    }

    public static boolean mirrored(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(TAG_MIRROR);
    }

    /** The turn that makes a south-facing front face the other way from where the player looks. */
    private static Rotation facingPlayer(Direction look) {
        return switch (look) {
            case SOUTH -> Rotation.CLOCKWISE_180;
            case EAST -> Rotation.CLOCKWISE_90;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    public static PrefabPlacement of(PrefabCatalog.Entry entry, BlockPos target, Direction look, int turns, boolean mirror) {
        Rotation rotation = facingPlayer(look);
        for (int i = 0; i < Math.floorMod(turns, 4); i++) {
            rotation = rotation.getRotated(Rotation.CLOCKWISE_90);
        }
        Mirror m = mirror ? Mirror.FRONT_BACK : Mirror.NONE;
        // the middle of the front row of the ground layer goes on the target block
        BlockPos anchor = new BlockPos(entry.width() / 2, 0, entry.depth() - 1);
        BlockPos moved = StructureTemplate.transform(anchor, m, rotation, BlockPos.ZERO);
        BlockPos origin = target.subtract(moved);
        BlockPos a = origin.offset(StructureTemplate.transform(BlockPos.ZERO, m, rotation, BlockPos.ZERO));
        BlockPos b = origin.offset(StructureTemplate.transform(new BlockPos(entry.width() - 1, entry.height() - 1, entry.depth() - 1),
                m, rotation, BlockPos.ZERO));
        return new PrefabPlacement(entry, origin, rotation, m, BoundingBox.fromCorners(a, b));
    }

    /** Which way the finished building's front faces. */
    public Direction front() {
        Direction d = rotation.rotate(Direction.SOUTH);
        return mirror == Mirror.NONE ? d : mirror.mirror(d);
    }
}
