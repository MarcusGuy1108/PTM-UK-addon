package com.ptmuk.client.building;

import com.mojang.blaze3d.platform.NativeImage;
import com.ptmuk.PtmUk;
import com.ptmuk.building.schematic.Schematic;
import com.ptmuk.building.schematic.SchematicReader;
import com.ptmuk.network.PtmUkNetwork;
import com.ptmuk.network.SchematicUploadMessage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Custom tab's schematics: files in the game folder's schematics folder (made if missing)
 * and WorldEdit's, read in the background, drawn as a little map from above, and sent to the
 * server when chosen.
 */
@Mod.EventBusSubscriber(modid = PtmUk.MOD_ID, value = Dist.CLIENT)
public final class SchematicFiles {
    public static final List<String> EXTENSIONS = List.of(".schem", ".schematic", ".litematic", ".nbt");
    public static final int THUMB = 128;

    /** A file in one of the folders; label is its path inside the folder. */
    public record FileEntry(Path path, String label, long bytes) {
    }

    /** A file being read or read: the schematic, its map and the bytes to send, or what went wrong. */
    public static final class Result {
        public volatile Schematic schematic;
        public volatile byte[] encoded;
        public volatile String error;
        public volatile NativeImage image;
        public volatile int solid;
        DynamicTexture texture;
        ResourceLocation textureId;
        final long modified;

        Result(long modified) {
            this.modified = modified;
        }

        public boolean done() {
            return encoded != null || error != null;
        }

        /** The map texture, made on the render thread the first time it's asked for. */
        public ResourceLocation texture() {
            if (textureId == null && image != null) {
                texture = new DynamicTexture(image);
                textureId = Minecraft.getInstance().getTextureManager().register("ptmuk_schematic", texture);
            }
            return textureId;
        }
    }

    private static final Map<Path, Result> CACHE = new HashMap<>();
    /** Files sent to the server this time you joined it. */
    private static final Set<String> SENT = new HashSet<>();
    private static int uploads;

    private SchematicFiles() {
    }

    public static Path mainFolder() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("schematics");
    }

    public static List<Path> folders() {
        Path game = Minecraft.getInstance().gameDirectory.toPath();
        return List.of(mainFolder(), game.resolve("config/worldedit/schematics"));
    }

    public static List<FileEntry> list() {
        try {
            Files.createDirectories(mainFolder());
        } catch (IOException ignored) {
            // read-only game folder: just list what's there
        }
        List<FileEntry> out = new ArrayList<>();
        for (Path folder : folders()) {
            if (!Files.isDirectory(folder)) {
                continue;
            }
            String prefix = folder.equals(mainFolder()) ? "" : "WorldEdit/";
            try (Stream<Path> walk = Files.walk(folder, 6)) {
                walk.filter(Files::isRegularFile).filter(p -> EXTENSIONS.stream().anyMatch(e -> p.toString().toLowerCase(Locale.ROOT).endsWith(e)))
                        .forEach(p -> {
                            long size;
                            try {
                                size = Files.size(p);
                            } catch (IOException e) {
                                size = 0;
                            }
                            out.add(new FileEntry(p, prefix + folder.relativize(p).toString().replace('\\', '/'), size));
                        });
            } catch (IOException ignored) {
                // a folder we can't read
            }
        }
        out.sort((a, b) -> a.label().compareToIgnoreCase(b.label()));
        return out;
    }

    /** The file read (or being read in the background). */
    public static Result get(FileEntry f) {
        long modified;
        try {
            modified = Files.getLastModifiedTime(f.path()).toMillis();
        } catch (IOException e) {
            modified = 0;
        }
        Result r = CACHE.get(f.path());
        if (r != null && r.modified == modified) {
            return r;
        }
        Result fresh = new Result(modified);
        CACHE.put(f.path(), fresh);
        CompletableFuture.runAsync(() -> {
            try {
                Schematic s = SchematicReader.read(f.path());
                fresh.solid = s.solidBlocks();
                fresh.image = map(s);
                fresh.schematic = s;
                fresh.encoded = s.encode();
            } catch (IOException | RuntimeException | OutOfMemoryError e) {
                fresh.error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
        }, Util.backgroundExecutor());
        return fresh;
    }

    public static boolean sent(FileEntry f) {
        return SENT.contains(f.label());
    }

    /** Sends the read schematic to the server in pieces; it sets the tool to place it. */
    public static void upload(FileEntry f, Result r, InteractionHand hand) {
        byte[] all = r.encoded;
        if (all == null) {
            return;
        }
        int pieces = Math.max(1, (all.length + SchematicUploadMessage.PIECE - 1) / SchematicUploadMessage.PIECE);
        int id = ++uploads;
        for (int i = 0; i < pieces; i++) {
            byte[] part = Arrays.copyOfRange(all, i * SchematicUploadMessage.PIECE, Math.min(all.length, (i + 1) * SchematicUploadMessage.PIECE));
            PtmUkNetwork.CHANNEL.sendToServer(new SchematicUploadMessage(id, i, pieces, f.label(), hand == InteractionHand.OFF_HAND, part));
        }
        SENT.add(f.label());
    }

    @SubscribeEvent
    public static void onLeave(ClientPlayerNetworkEvent.LoggingOut event) {
        SENT.clear();
    }

    // ------------------------------------------------------------------ a map from above

    /** The top of every column in its map colour, shaded by height; the front (south) at the bottom. */
    private static NativeImage map(Schematic s) {
        int[] colour = new int[s.palette.size()];
        var lookup = BuiltInRegistries.BLOCK.asLookup();
        for (int i = 1; i < colour.length; i++) {
            BlockState state = NbtUtils.readBlockState(lookup, s.palette.get(i));
            MapColor mc = state.isAir() ? MapColor.NONE : state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            colour[i] = mc == MapColor.NONE ? -1 : mc.col;
        }
        int[] top = new int[s.width * s.depth];
        int[] rgb = new int[s.width * s.depth];
        for (int z = 0; z < s.depth; z++) {
            for (int x = 0; x < s.width; x++) {
                top[z * s.width + x] = -1;
                for (int y = s.height - 1; y >= 0; y--) {
                    int c = s.cells[s.index(x, y, z)];
                    if (c != 0 && colour[c] >= 0) {
                        top[z * s.width + x] = y;
                        rgb[z * s.width + x] = colour[c];
                        break;
                    }
                }
            }
        }
        NativeImage img = new NativeImage(THUMB, THUMB, true);
        int side = Math.max(s.width, s.depth);
        int ox = (THUMB - s.width * THUMB / side) / 2, oz = (THUMB - s.depth * THUMB / side) / 2;
        for (int py = 0; py < THUMB; py++) {
            for (int px = 0; px < THUMB; px++) {
                int x = (px - ox) * side / THUMB, z = (py - oz) * side / THUMB;
                if (x < 0 || z < 0 || x >= s.width || z >= s.depth || px < ox || py < oz || top[z * s.width + x] < 0) {
                    img.setPixelRGBA(px, py, 0);
                    continue;
                }
                int i = z * s.width + x;
                int up = z > 0 && top[i - s.width] >= 0 ? top[i - s.width] : top[i];
                double shade = 0.72 + 0.28 * (top[i] + 1) / (double) s.height + (top[i] > up ? 0.1 : top[i] < up ? -0.12 : 0);
                int c = rgb[i];
                int r = clamp((c >> 16 & 255) * shade), g = clamp((c >> 8 & 255) * shade), b = clamp((c & 255) * shade);
                img.setPixelRGBA(px, py, 0xFF000000 | b << 16 | g << 8 | r);
            }
        }
        return img;
    }

    private static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, v));
    }
}
