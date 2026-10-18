package com.ptmuk.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import com.ptmuk.PtmUk;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.LevelResource;

/**
 * The world map's picture: everywhere you've been, one pixel per block, shaded like a vanilla
 * map, kept in 512-block regions on disk (in ptmuk/maps, per world and dimension) and on the GPU
 * while they're in use. It's filled in from the chunks the game has loaded, so it only shows
 * places you've seen, and it keeps up as you build.
 */
public final class MapStore {
    public static final int REGION = 512;
    /** Regions kept in memory at once (1 MB each). */
    private static final int KEEP = 48;

    static final class Region {
        final int rx, rz;
        final NativeImage image;
        final DynamicTexture texture;
        final ResourceLocation id;
        boolean upload, save;

        Region(int rx, int rz, NativeImage image) {
            this.rx = rx;
            this.rz = rz;
            this.image = image;
            this.texture = new DynamicTexture(image);
            this.id = PtmUk.id("map/" + (rx + "_" + rz).replace('-', 'm'));
            Minecraft.getInstance().getTextureManager().register(id, texture);
        }
    }

    private static MapStore current;

    private final Path folder;
    private final String dimension;
    private final Map<Long, Region> regions = new LinkedHashMap<>(16, 0.75f, true);

    private MapStore(Path folder, String dimension) {
        this.folder = folder;
        this.dimension = dimension;
    }

    /** The map for the world and dimension the player is in now (switching saves the old one). */
    public static MapStore get() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return null;
        }
        String dim = level.dimension().location().toString();
        Path folder = mc.gameDirectory.toPath().resolve("ptmuk").resolve("maps").resolve(worldName(mc)).resolve(dim.replace(':', '_'));
        if (current == null || !current.folder.equals(folder)) {
            close();
            current = new MapStore(folder, dim);
        }
        return current;
    }

    /** The save folder in single player, the server's address in multiplayer. */
    static String worldName(Minecraft mc) {
        String name;
        if (mc.getSingleplayerServer() != null) {
            Path root = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            name = "local_" + root.getFileName();
        } else {
            ServerData server = mc.getCurrentServer();
            name = "server_" + (server == null ? "unknown" : server.ip);
        }
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    public Path folder() {
        return folder;
    }

    public static void close() {
        if (current != null) {
            current.saveAll();
            for (Region r : current.regions.values()) {
                Minecraft.getInstance().getTextureManager().release(r.id);
            }
            current.regions.clear();
            current = null;
        }
    }

    private static long key(int rx, int rz) {
        return ((long) rx << 32) ^ (rz & 0xffffffffL);
    }

    /** A region's picture, loaded from disk (or blank) if it isn't in memory; null if there's nothing there and create is false. */
    Region region(int rx, int rz, boolean create) {
        Region r = regions.get(key(rx, rz));
        if (r != null) {
            return r;
        }
        Path file = file(rx, rz);
        NativeImage image = null;
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                image = NativeImage.read(in);
            } catch (IOException e) {
                PtmUk.LOGGER.warn("Couldn't read map region {}", file, e);
            }
        }
        if (image == null || image.getWidth() != REGION || image.getHeight() != REGION) {
            if (!create) {
                return null;
            }
            image = new NativeImage(NativeImage.Format.RGBA, REGION, REGION, true);
        }
        r = new Region(rx, rz, image);
        regions.put(key(rx, rz), r);
        // too many in memory: save and drop the least recently used
        for (Iterator<Region> it = regions.values().iterator(); regions.size() > KEEP && it.hasNext(); ) {
            Region old = it.next();
            if (old != r) {
                save(old);
                Minecraft.getInstance().getTextureManager().release(old.id);
                it.remove();
            }
        }
        return r;
    }

    private Path file(int rx, int rz) {
        return folder.resolve("r." + rx + "." + rz + ".png");
    }

    private void save(Region r) {
        if (!r.save) {
            return;
        }
        try {
            Files.createDirectories(folder);
            r.image.writeToFile(file(r.rx, r.rz));
            r.save = false;
        } catch (IOException e) {
            PtmUk.LOGGER.warn("Couldn't save map region {} {}", r.rx, r.rz, e);
        }
    }

    public void saveAll() {
        regions.values().forEach(this::save);
    }

    /** Upload the regions that changed; call before drawing. */
    void flush() {
        for (Region r : regions.values()) {
            if (r.upload) {
                r.upload = false;
                r.texture.upload();
            }
        }
    }

    /** The colour on the map at a block (ABGR, 0 if not seen). */
    public int colour(int x, int z) {
        Region r = region(Math.floorDiv(x, REGION), Math.floorDiv(z, REGION), false);
        return r == null ? 0 : r.image.getPixelRGBA(Math.floorMod(x, REGION), Math.floorMod(z, REGION));
    }

    /** Paint a loaded chunk onto the map. */
    void scan(ClientLevel level, LevelChunk chunk) {
        int cx = chunk.getPos().x, cz = chunk.getPos().z;
        Region r = region(Math.floorDiv(cx << 4, REGION), Math.floorDiv(cz << 4, REGION), true);
        int ox = Math.floorMod(cx << 4, REGION), oz = Math.floorMod(cz << 4, REGION);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        boolean changed = false;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int wx = (cx << 4) + x, wz = (cz << 4) + z;
                int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                if (top <= level.getMinBuildHeight()) {
                    continue;
                }
                int north = z > 0 ? chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z - 1) : level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz - 1) - 1;
                BlockState s = chunk.getBlockState(p.set(wx, top, wz));
                MapColor colour = s.getMapColor(level, p);
                if (colour == MapColor.NONE) {
                    colour = chunk.getBlockState(p.set(wx, top - 1, wz)).getMapColor(level, p);
                }
                MapColor.Brightness b = !s.getFluidState().isEmpty() || north <= level.getMinBuildHeight() ? MapColor.Brightness.NORMAL
                        : top > north ? MapColor.Brightness.HIGH : top < north ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                int c = colour == MapColor.NONE ? 0 : colour.calculateRGBColor(b);
                if (r.image.getPixelRGBA(ox + x, oz + z) != c) {
                    r.image.setPixelRGBA(ox + x, oz + z, c);
                    changed = true;
                }
            }
        }
        if (changed) {
            r.upload = true;
            r.save = true;
        }
    }

    String dimension() {
        return dimension;
    }
}
