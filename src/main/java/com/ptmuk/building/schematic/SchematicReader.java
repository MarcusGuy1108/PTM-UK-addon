package com.ptmuk.building.schematic;

import com.mojang.serialization.Dynamic;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.BlockStateData;
import net.minecraft.util.datafix.fixes.ItemIdFix;
import net.minecraft.util.datafix.fixes.References;

/**
 * Reads the schematic formats people share and turns them into a {@link Schematic} for this game
 * version: Sponge / WorldEdit .schem (versions 1 to 3, also found saved as .schematic), the old
 * MCEdit / WorldEdit .schematic with numeric block ids, Litematica .litematic (all its regions
 * together) and vanilla structure block .nbt files. Blocks and block entities from older game
 * versions go through the game's own data fixers, as when an old world is opened.
 */
public final class SchematicReader {
    /** Data versions: the 1.12.2 world format, the flattening's block names, 1.13.0. */
    private static final int V1_12 = 1343, V_FLATTENED = 1451, V1_13 = 1519;

    private SchematicReader() {
    }

    public static Schematic read(Path file) throws IOException {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        CompoundTag root;
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            in.mark(2);
            int b0 = in.read(), b1 = in.read();
            in.reset();
            InputStream body = b0 == 0x1f && b1 == 0x8b ? new BufferedInputStream(new GZIPInputStream(in)) : in;
            root = NbtIo.read(new DataInputStream(body), new NbtAccounter(1024L * 1024 * 1024));
        }
        if (root.contains("Regions", Tag.TAG_COMPOUND)) {
            return litematic(base, root);
        }
        CompoundTag sponge = root.contains("Schematic", Tag.TAG_COMPOUND) ? root.getCompound("Schematic") : root;
        if (sponge.contains("BlockData") || sponge.contains("Blocks", Tag.TAG_COMPOUND)) {
            return sponge(base, sponge);
        }
        if (root.contains("Blocks", Tag.TAG_BYTE_ARRAY)) {
            return legacy(base, root);
        }
        if (root.contains("blocks", Tag.TAG_LIST) && (root.contains("palette") || root.contains("palettes"))) {
            return structure(base, root);
        }
        throw new IOException("not a schematic format this knows (.schem, .schematic, .litematic or .nbt)");
    }

    // ------------------------------------------------------------------ data fixing

    private static int current() {
        return SharedConstants.getCurrentVersion().getDataVersion().getVersion();
    }

    private static CompoundTag fixState(CompoundTag state, int from) {
        if (from >= current()) {
            return state;
        }
        try {
            Tag t = DataFixers.getDataFixer().update(References.BLOCK_STATE, new Dynamic<>(NbtOps.INSTANCE, state), from, current())
                    .getValue();
            return t instanceof CompoundTag c ? c : state;
        } catch (RuntimeException e) {
            return state;
        }
    }

    private static CompoundTag fixBlockEntity(CompoundTag nbt, int from) {
        if (from >= current()) {
            return nbt;
        }
        try {
            Tag t = DataFixers.getDataFixer().update(References.BLOCK_ENTITY, new Dynamic<>(NbtOps.INSTANCE, nbt), from, current())
                    .getValue();
            return t instanceof CompoundTag c ? c : nbt;
        } catch (RuntimeException e) {
            return nbt;
        }
    }

    /** Collects the palette as it fills the cells: index 0 is air. */
    private static final class Palette {
        final List<CompoundTag> states = new ArrayList<>();
        final Map<String, Integer> index = new HashMap<>();

        Palette() {
            CompoundTag air = new CompoundTag();
            air.putString("Name", "minecraft:air");
            states.add(air);
        }

        int add(CompoundTag state) throws IOException {
            String name = state.getString("Name");
            if (name.isEmpty() || name.equals("minecraft:air") || name.equals("minecraft:cave_air") || name.equals("minecraft:void_air")
                    || name.equals("minecraft:structure_void")) {
                return 0;
            }
            String key = state.toString();
            Integer i = index.get(key);
            if (i == null) {
                if (states.size() >= Schematic.MAX_PALETTE) {
                    throw new IOException("too many different blocks");
                }
                i = states.size();
                states.add(state);
                index.put(key, i);
            }
            return i;
        }
    }

    /** "minecraft:oak_stairs[facing=north,half=bottom]" as Name and Properties. */
    static CompoundTag parseState(String s) {
        CompoundTag tag = new CompoundTag();
        int open = s.indexOf('[');
        String name = (open < 0 ? s : s.substring(0, open)).trim();
        tag.putString("Name", name.contains(":") ? name : "minecraft:" + name);
        if (open >= 0 && s.endsWith("]")) {
            CompoundTag props = new CompoundTag();
            for (String kv : s.substring(open + 1, s.length() - 1).split(",")) {
                int eq = kv.indexOf('=');
                if (eq > 0) {
                    props.putString(kv.substring(0, eq).trim(), kv.substring(eq + 1).trim());
                }
            }
            if (!props.isEmpty()) {
                tag.put("Properties", props);
            }
        }
        return tag;
    }

    private static CompoundTag blockEntity(int x, int y, int z, CompoundTag nbt) {
        CompoundTag be = new CompoundTag();
        be.putInt("x", x);
        be.putInt("y", y);
        be.putInt("z", z);
        CompoundTag data = nbt.copy();
        data.remove("x");
        data.remove("y");
        data.remove("z");
        data.remove("Pos");
        be.put("nbt", data);
        return be;
    }

    // ------------------------------------------------------------------ Sponge (.schem)

    private static Schematic sponge(String name, CompoundTag s) throws IOException {
        int version = s.getInt("Version");
        int w = s.getShort("Width") & 0xFFFF, h = s.getShort("Height") & 0xFFFF, d = s.getShort("Length") & 0xFFFF;
        Schematic.checkSize(w, h, d);
        int dataVersion = s.contains("DataVersion") ? s.getInt("DataVersion") : V1_13;
        CompoundTag blocks = version >= 3 ? s.getCompound("Blocks") : s;
        CompoundTag pal = blocks.getCompound("Palette");
        byte[] data = version >= 3 ? blocks.getByteArray("Data") : s.getByteArray("BlockData");
        int maxId = 0;
        for (String key : pal.getAllKeys()) {
            maxId = Math.max(maxId, pal.getInt(key));
        }
        Palette palette = new Palette();
        int[] remap = new int[maxId + 1];
        for (String key : pal.getAllKeys()) {
            remap[pal.getInt(key)] = palette.add(fixState(parseState(key), dataVersion));
        }
        int[] raw = Schematic.Varints.read(data, w * h * d, maxId + 1);
        // Sponge order is x fastest, then z, then y: the same as ours
        int[] cells = new int[raw.length];
        for (int i = 0; i < raw.length; i++) {
            cells[i] = remap[raw[i]];
        }
        List<CompoundTag> bes = new ArrayList<>();
        ListTag list = version >= 3 ? blocks.getList("BlockEntities", Tag.TAG_COMPOUND)
                : s.getList(s.contains("BlockEntities") ? "BlockEntities" : "TileEntities", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            int[] pos = e.getIntArray("Pos");
            if (pos.length < 3) {
                continue;
            }
            CompoundTag nbt = version >= 3 ? e.getCompound("Data").copy() : e.copy();
            nbt.remove("Pos");
            nbt.remove("Id");
            nbt.putString("id", e.getString("Id"));
            bes.add(blockEntity(pos[0], pos[1], pos[2], fixBlockEntity(nbt, dataVersion)));
        }
        return new Schematic(name, "Sponge schematic v" + Math.max(1, version), w, h, d, palette.states, cells, bes);
    }

    // ------------------------------------------------------------------ MCEdit / old WorldEdit (.schematic, numeric ids)

    private static Schematic legacy(String name, CompoundTag s) throws IOException {
        int w = s.getShort("Width") & 0xFFFF, h = s.getShort("Height") & 0xFFFF, d = s.getShort("Length") & 0xFFFF;
        Schematic.checkSize(w, h, d);
        byte[] ids = s.getByteArray("Blocks"), meta = s.getByteArray("Data"), add = s.getByteArray("AddBlocks");
        int n = w * h * d;
        if (ids.length < n || meta.length < n) {
            throw new IOException("block data is shorter than the schematic");
        }
        Palette palette = new Palette();
        Map<Integer, Integer> seen = new HashMap<>();
        int[] cells = new int[n];
        for (int i = 0; i < n; i++) {
            int id = ids[i] & 0xFF;
            if ((i >> 1) < add.length) {
                id |= (i & 1) == 0 ? (add[i >> 1] & 0x0F) << 8 : (add[i >> 1] & 0xF0) << 4;
            }
            int key = id << 4 | (meta[i] & 0x0F);
            Integer p = seen.get(key);
            if (p == null) {
                Tag t = BlockStateData.getTag(key).convert(NbtOps.INSTANCE).getValue();
                p = t instanceof CompoundTag c ? palette.add(fixState(c, V_FLATTENED)) : 0;
                seen.put(key, p);
            }
            cells[i] = p;                       // MCEdit order is also x fastest, then z, then y
        }
        List<CompoundTag> bes = new ArrayList<>();
        ListTag list = s.getList("TileEntities", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = oldBlockEntity(list.getCompound(i).copy());
            bes.add(blockEntity(e.getInt("x"), e.getInt("y"), e.getInt("z"), fixBlockEntity(e, V1_12)));
        }
        return new Schematic(name, "MCEdit schematic", w, h, d, palette.states, cells, bes);
    }

    /** Block entity names from before 1.11, as old MCEdit schematics have them. */
    private static final Map<String, String> OLD_IDS = Map.ofEntries(Map.entry("Chest", "chest"), Map.entry("Trap", "dispenser"),
            Map.entry("Dropper", "dropper"), Map.entry("Furnace", "furnace"), Map.entry("Hopper", "hopper"), Map.entry("Sign", "sign"),
            Map.entry("MobSpawner", "mob_spawner"), Map.entry("Music", "noteblock"), Map.entry("Piston", "piston"),
            Map.entry("Cauldron", "brewing_stand"), Map.entry("EnchantTable", "enchanting_table"), Map.entry("Airportal", "end_portal"),
            Map.entry("Beacon", "beacon"), Map.entry("Skull", "skull"), Map.entry("DLDetector", "daylight_detector"),
            Map.entry("Comparator", "comparator"), Map.entry("FlowerPot", "flower_pot"), Map.entry("Banner", "banner"),
            Map.entry("Structure", "structure_block"), Map.entry("EndGateway", "end_gateway"), Map.entry("Control", "command_block"),
            Map.entry("RecordPlayer", "jukebox"), Map.entry("EnderChest", "ender_chest"));

    /** Brings a pre-1.11 block entity up to the 1.12 form the data fixers start from: namespaced id, named items. */
    private static CompoundTag oldBlockEntity(CompoundTag e) {
        String id = e.getString("id");
        if (!id.contains(":")) {
            e.putString("id", "minecraft:" + OLD_IDS.getOrDefault(id, id.toLowerCase(java.util.Locale.ROOT)));
        }
        for (String key : new String[]{"Items", "RecordItem", "Item"}) {
            if (e.contains(key, Tag.TAG_LIST)) {
                ListTag items = e.getList(key, Tag.TAG_COMPOUND);
                for (int i = 0; i < items.size(); i++) {
                    numericItem(items.getCompound(i));
                }
            } else if (e.contains(key, Tag.TAG_COMPOUND)) {
                numericItem(e.getCompound(key));
            }
        }
        return e;
    }

    private static void numericItem(CompoundTag item) {
        if (item.contains("id", Tag.TAG_ANY_NUMERIC)) {
            String name = ItemIdFix.getItem(item.getInt("id"));
            item.putString("id", name == null ? "minecraft:air" : name);
        }
    }

    // ------------------------------------------------------------------ Litematica (.litematic)

    private static Schematic litematic(String name, CompoundTag root) throws IOException {
        int dataVersion = root.contains("MinecraftDataVersion") ? root.getInt("MinecraftDataVersion") : V1_13;
        CompoundTag regions = root.getCompound("Regions");
        // the box round every region (a negative size means the region runs the other way from its position)
        int[] lo = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE}, hi = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (String key : regions.getAllKeys()) {
            int[][] r = regionBox(regions.getCompound(key));
            for (int a = 0; a < 3; a++) {
                lo[a] = Math.min(lo[a], r[0][a]);
                hi[a] = Math.max(hi[a], r[1][a]);
            }
        }
        if (lo[0] == Integer.MAX_VALUE) {
            throw new IOException("it has no regions");
        }
        int w = hi[0] - lo[0] + 1, h = hi[1] - lo[1] + 1, d = hi[2] - lo[2] + 1;
        Schematic.checkSize(w, h, d);
        Palette palette = new Palette();
        int[] cells = new int[w * h * d];
        List<CompoundTag> bes = new ArrayList<>();
        for (String key : regions.getAllKeys()) {
            CompoundTag region = regions.getCompound(key);
            int[][] r = regionBox(region);
            int rx = r[1][0] - r[0][0] + 1, ry = r[1][1] - r[0][1] + 1, rz = r[1][2] - r[0][2] + 1;
            ListTag pal = region.getList("BlockStatePalette", Tag.TAG_COMPOUND);
            int[] remap = new int[Math.max(1, pal.size())];
            for (int i = 0; i < pal.size(); i++) {
                remap[i] = palette.add(fixState(pal.getCompound(i), dataVersion));
            }
            long[] bits = region.getLongArray("BlockStates");
            int per = Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, pal.size() - 1)));
            long mask = (1L << per) - 1;
            for (int y = 0; y < ry; y++) {
                for (int z = 0; z < rz; z++) {
                    for (int x = 0; x < rx; x++) {
                        long i = ((long) y * rz + z) * rx + x;
                        long start = i * per;
                        int a = (int) (start >> 6), b = (int) (((i + 1) * per - 1) >> 6), off = (int) (start & 63);
                        if (b >= bits.length) {
                            continue;
                        }
                        long v = a == b ? (bits[a] >>> off) & mask : ((bits[a] >>> off) | (bits[b] << (64 - off))) & mask;
                        int p = v < remap.length ? remap[(int) v] : 0;
                        if (p != 0) {
                            int gx = r[0][0] - lo[0] + x, gy = r[0][1] - lo[1] + y, gz = r[0][2] - lo[2] + z;
                            cells[(gy * d + gz) * w + gx] = p;
                        }
                    }
                }
            }
            ListTag tes = region.getList("TileEntities", Tag.TAG_COMPOUND);
            for (int i = 0; i < tes.size(); i++) {
                CompoundTag e = tes.getCompound(i);
                bes.add(blockEntity(r[0][0] - lo[0] + e.getInt("x"), r[0][1] - lo[1] + e.getInt("y"), r[0][2] - lo[2] + e.getInt("z"),
                        fixBlockEntity(e, dataVersion)));
            }
        }
        return new Schematic(name, "Litematica schematic", w, h, d, palette.states, cells, bes);
    }

    /** {min, max} corners of a Litematica region. */
    private static int[][] regionBox(CompoundTag region) {
        CompoundTag pos = region.getCompound("Position"), size = region.getCompound("Size");
        int[] p = {pos.getInt("x"), pos.getInt("y"), pos.getInt("z")};
        int[] s = {size.getInt("x"), size.getInt("y"), size.getInt("z")};
        int[][] out = new int[2][3];
        for (int a = 0; a < 3; a++) {
            int end = s[a] >= 0 ? p[a] + s[a] - 1 : p[a] + s[a] + 1;
            out[0][a] = Math.min(p[a], end);
            out[1][a] = Math.max(p[a], end);
        }
        return out;
    }

    // ------------------------------------------------------------------ vanilla structure (.nbt)

    private static Schematic structure(String name, CompoundTag root) throws IOException {
        ListTag size = root.getList("size", Tag.TAG_INT);
        if (size.size() < 3) {
            throw new IOException("structure has no size");
        }
        int w = size.getInt(0), h = size.getInt(1), d = size.getInt(2);
        Schematic.checkSize(w, h, d);
        int dataVersion = root.contains("DataVersion") ? root.getInt("DataVersion") : 500;
        ListTag pal = root.contains("palette") ? root.getList("palette", Tag.TAG_COMPOUND)
                : root.getList("palettes", Tag.TAG_LIST).getList(0);
        Palette palette = new Palette();
        int[] remap = new int[pal.size()];
        for (int i = 0; i < pal.size(); i++) {
            remap[i] = palette.add(fixState(pal.getCompound(i), dataVersion));
        }
        int[] cells = new int[w * h * d];
        List<CompoundTag> bes = new ArrayList<>();
        ListTag blocks = root.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag b = blocks.getCompound(i);
            ListTag pos = b.getList("pos", Tag.TAG_INT);
            int state = b.getInt("state");
            if (pos.size() < 3 || state < 0 || state >= remap.length) {
                continue;
            }
            int x = pos.getInt(0), y = pos.getInt(1), z = pos.getInt(2);
            if (x < 0 || y < 0 || z < 0 || x >= w || y >= h || z >= d) {
                continue;
            }
            cells[(y * d + z) * w + x] = remap[state];
            if (b.contains("nbt", Tag.TAG_COMPOUND) && remap[state] != 0) {
                bes.add(blockEntity(x, y, z, fixBlockEntity(b.getCompound("nbt"), dataVersion)));
            }
        }
        return new Schematic(name, "Structure block file", w, h, d, palette.states, cells, bes);
    }
}
