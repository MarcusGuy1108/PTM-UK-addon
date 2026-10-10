package com.ptmuk.building;

import com.ptmuk.PtmUk;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Places prefabs on the server, fills foundations under them and keeps an undo history. */
public final class PrefabPlacer {
    /** How far below the ground layer foundations are filled on uneven ground. */
    private static final int FOUNDATION_DEPTH = 12;
    private static final int UNDO_STEPS = 8;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private record Saved(BlockPos pos, BlockState state, CompoundTag nbt) {
    }

    private record Undo(ResourceKey<Level> dimension, String name, List<Saved> blocks) {
    }

    private static final Map<UUID, Deque<Undo>> HISTORY = new HashMap<>();

    private PrefabPlacer() {
    }

    public static boolean place(ServerPlayer player, PrefabPlacement p) {
        ServerLevel level = player.serverLevel();
        Optional<StructureTemplate> template = level.getStructureManager().get(PtmUk.id("prefab/" + p.entry().id()));
        if (template.isEmpty()) {
            player.displayClientMessage(Component.literal("Prefab " + p.entry().id() + " is missing from the mod"), true);
            return false;
        }
        BoundingBox box = p.box();
        if (!level.isLoaded(new BlockPos(box.minX(), box.minY(), box.minZ())) || !level.isLoaded(new BlockPos(box.maxX(), box.minY(), box.maxZ()))
                || box.minY() < level.getMinBuildHeight() || box.maxY() >= level.getMaxBuildHeight()) {
            player.displayClientMessage(Component.literal("That spot is outside the loaded world"), true);
            return false;
        }
        List<Saved> saved = new ArrayList<>();
        BlockPos.betweenClosedStream(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())
                .forEach(pos -> saved.add(save(level, pos)));
        // clear first, so containers don't spill and old blocks don't linger where the prefab has air
        for (Saved s : saved) {
            BlockEntity be = level.getBlockEntity(s.pos());
            Clearable.tryClear(be);
        }
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(p.rotation()).setMirror(p.mirror())
                .setIgnoreEntities(true).setKnownShape(false);
        template.get().placeInWorld(level, p.origin(), p.origin(), settings, RandomSource.create(), FLAGS);
        foundations(level, box, saved);
        if (p.mirror() != net.minecraft.world.level.block.Mirror.NONE) {
            fixShopSigns(level, box);
        }
        Deque<Undo> history = HISTORY.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());
        history.push(new Undo(level.dimension(), p.entry().name(), saved));
        while (history.size() > UNDO_STEPS) {
            history.removeLast();
        }
        player.displayClientMessage(Component.literal("Placed " + p.entry().name() + " (" + box.getXSpan() + " x "
                + box.getZSpan() + ", " + box.getYSpan() + " high)"), true);
        return true;
    }

    private static Saved save(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return new Saved(pos.immutable(), level.getBlockState(pos), be == null ? null : be.saveWithFullMetadata());
    }

    /** Fills air, water and plants under the ground layer so a prefab doesn't float on a slope. */
    private static void foundations(ServerLevel level, BoundingBox box, List<Saved> saved) {
        int y0 = box.minY();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                BlockState top = level.getBlockState(new BlockPos(x, y0, z));
                if (top.isAir()) {
                    continue;
                }
                BlockState fill = top.is(Blocks.GRASS_BLOCK) || top.is(Blocks.DIRT) ? Blocks.DIRT.defaultBlockState()
                        : Blocks.STONE_BRICKS.defaultBlockState();
                for (int y = y0 - 1; y >= y0 - FOUNDATION_DEPTH && y >= level.getMinBuildHeight(); y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState here = level.getBlockState(pos);
                    if (!(here.isAir() || here.canBeReplaced() || !here.getFluidState().isEmpty())) {
                        break;
                    }
                    saved.add(save(level, pos));
                    level.setBlock(pos, fill, FLAGS);
                }
            }
        }
    }

    /** Mirroring swaps the ends of a shop sign row: move the text back to the left-hand board. */
    private static void fixShopSigns(ServerLevel level, BoundingBox box) {
        BlockPos.betweenClosedStream(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()).forEach(pos -> {
            if (level.getBlockEntity(pos) instanceof ShopSignBlockEntity sign && !sign.text.isBlank() && !sign.isAnchor()) {
                Direction facing = sign.getBlockState().getValue(ShopSignBlock.FACING);
                BlockPos anchor = ShopSignBlockEntity.anchor(level, pos, facing);
                if (level.getBlockEntity(anchor) instanceof ShopSignBlockEntity first) {
                    CompoundTag tag = new CompoundTag();
                    sign.write(tag);
                    first.read(tag);
                    sign.text = "";
                    sign.sub = "";
                    first.setChanged();
                    sign.setChanged();
                    level.sendBlockUpdated(anchor, first.getBlockState(), first.getBlockState(), 3);
                    level.sendBlockUpdated(pos, sign.getBlockState(), sign.getBlockState(), 3);
                }
            }
        });
    }

    public static void undo(ServerPlayer player) {
        Deque<Undo> history = HISTORY.get(player.getUUID());
        if (history == null || history.isEmpty()) {
            player.displayClientMessage(Component.literal("Nothing to undo"), true);
            return;
        }
        Undo undo = history.pop();
        ServerLevel level = player.server.getLevel(undo.dimension());
        if (level == null) {
            return;
        }
        for (Saved s : undo.blocks()) {
            Clearable.tryClear(level.getBlockEntity(s.pos()));
            level.setBlock(s.pos(), Blocks.AIR.defaultBlockState(), FLAGS);
        }
        for (Saved s : undo.blocks()) {
            level.setBlock(s.pos(), s.state(), FLAGS);
            if (s.nbt() != null) {
                BlockEntity be = level.getBlockEntity(s.pos());
                if (be != null) {
                    be.load(s.nbt());
                    be.setChanged();
                }
            }
        }
        player.displayClientMessage(Component.literal("Removed " + undo.name()), true);
    }
}
