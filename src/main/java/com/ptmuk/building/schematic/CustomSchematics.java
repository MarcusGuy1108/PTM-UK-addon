package com.ptmuk.building.schematic;

import com.ptmuk.building.PrefabPlacement;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Schematics players have imported with the Prefab Tool, one per player, kept on the server
 * while it runs (the client sends it again when needed). Places them like a prefab: turned and
 * mirrored, every cell of the box set, block entities loaded.
 */
@Mod.EventBusSubscriber(modid = com.ptmuk.PtmUk.MOD_ID)
public final class CustomSchematics {
    public static final String ID_PREFIX = "custom:";
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    /** A schematic ready to place: palette resolved to this server's blocks. */
    public record Loaded(String name, int width, int height, int depth, BlockState[] palette, int[] cells, List<CompoundTag> blockEntities,
                         int missing) {
    }

    private static final Map<UUID, Loaded> BY_PLAYER = new HashMap<>();
    private static final Map<UUID, Upload> UPLOADS = new HashMap<>();

    private CustomSchematics() {
    }

    public static Loaded get(UUID player, String id) {
        Loaded l = BY_PLAYER.get(player);
        return l != null && id.equals(ID_PREFIX + l.name()) ? l : null;
    }

    // ------------------------------------------------------------------ uploads, in pieces

    private static final class Upload {
        final int id;
        final byte[][] parts;
        int received;

        Upload(int id, int total) {
            this.id = id;
            this.parts = new byte[total][];
        }
    }

    /** Most pieces one upload may come in (30 KB each). */
    public static final int MAX_PARTS = 1200;

    /** Adds a piece; returns the whole file once every piece is in, otherwise null. */
    public static byte[] receive(UUID player, int upload, int index, int total, byte[] data) {
        if (total <= 0 || total > MAX_PARTS || index < 0 || index >= total) {
            return null;
        }
        Upload u = UPLOADS.get(player);
        if (u == null || u.id != upload || u.parts.length != total) {
            u = new Upload(upload, total);
            UPLOADS.put(player, u);
        }
        if (u.parts[index] == null) {
            u.parts[index] = data;
            u.received++;
        }
        if (u.received < total) {
            return null;
        }
        UPLOADS.remove(player);
        int size = 0;
        for (byte[] p : u.parts) {
            size += p.length;
        }
        byte[] all = new byte[size];
        int at = 0;
        for (byte[] p : u.parts) {
            System.arraycopy(p, 0, all, at, p.length);
            at += p.length;
        }
        return all;
    }

    public static Loaded store(UUID player, ServerLevel level, byte[] bytes) throws IOException {
        Schematic s = Schematic.decode(bytes);
        var blocks = level.holderLookup(Registries.BLOCK);
        BlockState[] palette = new BlockState[s.palette.size()];
        int missing = 0;
        for (int i = 0; i < palette.length; i++) {
            palette[i] = i == 0 ? Blocks.AIR.defaultBlockState() : NbtUtils.readBlockState(blocks, s.palette.get(i));
            if (i > 0 && palette[i].isAir()) {
                missing++;                         // a block from a mod that isn't installed
            }
        }
        Loaded l = new Loaded(s.name, s.width, s.height, s.depth, palette, s.cells, s.blockEntities, missing);
        BY_PLAYER.put(player, l);
        return l;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        BY_PLAYER.remove(event.getEntity().getUUID());
        UPLOADS.remove(event.getEntity().getUUID());
    }

    // ------------------------------------------------------------------ placing

    public static void paste(ServerLevel level, PrefabPlacement p, Loaded s) {
        for (int y = 0; y < s.height(); y++) {
            for (int z = 0; z < s.depth(); z++) {
                for (int x = 0; x < s.width(); x++) {
                    BlockPos pos = p.origin().offset(StructureTemplate.transform(new BlockPos(x, y, z), p.mirror(), p.rotation(), BlockPos.ZERO));
                    BlockState state = s.palette()[s.cells()[(y * s.depth() + z) * s.width() + x]];
                    if (state.isAir()) {
                        if (!level.getBlockState(pos).isAir()) {
                            level.setBlock(pos, state, FLAGS);
                        }
                        continue;
                    }
                    level.setBlock(pos, state.mirror(p.mirror()).rotate(level, pos, p.rotation()), FLAGS);
                }
            }
        }
        for (CompoundTag be : s.blockEntities()) {
            int x = be.getInt("x"), y = be.getInt("y"), z = be.getInt("z");
            if (x < 0 || y < 0 || z < 0 || x >= s.width() || y >= s.height() || z >= s.depth()) {
                continue;
            }
            BlockPos pos = p.origin().offset(StructureTemplate.transform(new BlockPos(x, y, z), p.mirror(), p.rotation(), BlockPos.ZERO));
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity != null) {
                CompoundTag nbt = be.getCompound("nbt").copy();
                nbt.putInt("x", pos.getX());
                nbt.putInt("y", pos.getY());
                nbt.putInt("z", pos.getZ());
                try {
                    entity.load(nbt);
                    entity.setChanged();
                } catch (RuntimeException ignored) {
                    // data the block doesn't understand: leave it as placed
                }
            }
        }
    }
}
