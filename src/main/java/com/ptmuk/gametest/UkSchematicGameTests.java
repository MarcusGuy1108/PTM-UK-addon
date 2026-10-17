package com.ptmuk.gametest;

import com.ptmuk.PtmUk;
import com.ptmuk.building.schematic.Schematic;
import com.ptmuk.building.schematic.SchematicReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * The Prefab Tool's schematic import: the same little hut written as an old MCEdit .schematic
 * (numeric ids, a pre-1.11 chest with a numeric item), a Sponge .schem and a Litematica file
 * with a second, negative-size region, each read back and checked block by block.
 */
@GameTestHolder(PtmUk.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UkSchematicGameTests {
    private static final int W = 7, H = 5, D = 6;

    private UkSchematicGameTests() {
    }

    private static String hut(int x, int y, int z) {
        if (y == 0) {
            return "minecraft:stone_bricks";
        }
        boolean edge = x == 0 || x == W - 1 || z == 0 || z == D - 1;
        if (y <= 3 && edge) {
            return z == D - 1 && x == 3 && y <= 2 ? "minecraft:air" : "minecraft:spruce_planks";
        }
        if (y == 4) {
            return z == 0 ? "minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]"
                    : z == D - 1 ? "minecraft:oak_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]"
                    : "minecraft:oak_slab[type=bottom,waterlogged=false]";
        }
        return y == 1 && x == 1 && z == 1 ? "minecraft:chest[facing=south,type=single,waterlogged=false]" : "minecraft:air";
    }

    private static ListTag diamonds(boolean numeric) {
        CompoundTag item = new CompoundTag();
        item.putByte("Slot", (byte) 0);
        if (numeric) {
            item.putShort("id", (short) 264);
            item.putShort("Damage", (short) 0);
        } else {
            item.putString("id", "minecraft:diamond");
        }
        item.putByte("Count", (byte) 5);
        ListTag items = new ListTag();
        items.add(item);
        return items;
    }

    private static Schematic readBack(CompoundTag root, String file) throws IOException {
        Path tmp = Files.createTempDirectory("ptmuk_schematic");
        Path path = tmp.resolve(file);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtIo.writeCompressed(root, out);
        Files.write(path, out.toByteArray());
        try {
            // also through the compact form the server gets
            return Schematic.decode(SchematicReader.read(path).encode());
        } finally {
            Files.deleteIfExists(path);
            Files.deleteIfExists(tmp);
        }
    }

    private static String name(Schematic s, int x, int y, int z) {
        return s.palette.get(s.cells[s.index(x, y, z)]).getString("Name");
    }

    private static void checkHut(GameTestHelper helper, Schematic s, String what) {
        helper.assertTrue(name(s, 0, 0, 0).equals("minecraft:stone_bricks"), what + ": floor is " + name(s, 0, 0, 0));
        helper.assertTrue(name(s, 0, 2, 2).equals("minecraft:spruce_planks"), what + ": wall is " + name(s, 0, 2, 2));
        helper.assertTrue(name(s, 3, 1, D - 1).equals("minecraft:air"), what + ": doorway is " + name(s, 3, 1, D - 1));
        CompoundTag stairs = s.palette.get(s.cells[s.index(2, 4, 0)]);
        helper.assertTrue(stairs.getString("Name").equals("minecraft:oak_stairs")
                && stairs.getCompound("Properties").getString("facing").equals("north"), what + ": roof edge is " + stairs);
        helper.assertTrue(name(s, 1, 1, 1).equals("minecraft:chest"), what + ": no chest, " + name(s, 1, 1, 1));
        CompoundTag chest = s.blockEntities.stream().filter(b -> b.getInt("x") == 1 && b.getInt("y") == 1 && b.getInt("z") == 1)
                .findFirst().orElse(null);
        helper.assertTrue(chest != null, what + ": chest data missing");
        ListTag items = chest.getCompound("nbt").getList("Items", Tag.TAG_COMPOUND);
        helper.assertTrue(items.size() == 1 && items.getCompound(0).getString("id").equals("minecraft:diamond")
                && items.getCompound(0).getByte("Count") == 5, what + ": chest holds " + items);
    }

    @GameTest(template = "empty5x4x5")
    public static void readsOldMcEditSchematic(GameTestHelper helper) throws IOException {
        Map<String, int[]> ids = Map.of("minecraft:stone_bricks", new int[]{98, 0}, "minecraft:spruce_planks", new int[]{5, 1},
                "minecraft:air", new int[]{0, 0});
        byte[] blocks = new byte[W * H * D], data = new byte[W * H * D];
        for (int y = 0; y < H; y++) {
            for (int z = 0; z < D; z++) {
                for (int x = 0; x < W; x++) {
                    String n = hut(x, y, z);
                    int[] id = ids.containsKey(n) ? ids.get(n) : n.startsWith("minecraft:chest") ? new int[]{54, 3}
                            : n.contains("facing=north") ? new int[]{53, 3} : n.contains("facing=south") ? new int[]{53, 2} : new int[]{126, 0};
                    int i = (y * D + z) * W + x;
                    blocks[i] = (byte) id[0];
                    data[i] = (byte) id[1];
                }
            }
        }
        CompoundTag root = new CompoundTag();
        root.putShort("Width", (short) W);
        root.putShort("Height", (short) H);
        root.putShort("Length", (short) D);
        root.putString("Materials", "Alpha");
        root.putByteArray("Blocks", blocks);
        root.putByteArray("Data", data);
        CompoundTag chest = new CompoundTag();
        chest.putString("id", "Chest");
        chest.putInt("x", 1);
        chest.putInt("y", 1);
        chest.putInt("z", 1);
        chest.put("Items", diamonds(true));
        ListTag tes = new ListTag();
        tes.add(chest);
        root.put("TileEntities", tes);
        checkHut(helper, readBack(root, "hut.schematic"), "MCEdit");
        helper.succeed();
    }

    private static Map<String, Integer> palette() {
        Map<String, Integer> pal = new LinkedHashMap<>();
        pal.put("minecraft:air", 0);
        for (int y = 0; y < H; y++) {
            for (int z = 0; z < D; z++) {
                for (int x = 0; x < W; x++) {
                    pal.putIfAbsent(hut(x, y, z), pal.size());
                }
            }
        }
        return pal;
    }

    @GameTest(template = "empty5x4x5")
    public static void readsSpongeSchematic(GameTestHelper helper) throws IOException {
        Map<String, Integer> pal = palette();
        ByteArrayOutputStream cells = new ByteArrayOutputStream();
        for (int y = 0; y < H; y++) {
            for (int z = 0; z < D; z++) {
                for (int x = 0; x < W; x++) {
                    cells.write(pal.get(hut(x, y, z)));
                }
            }
        }
        CompoundTag root = new CompoundTag();
        root.putInt("Version", 2);
        root.putInt("DataVersion", 3465);
        root.putShort("Width", (short) W);
        root.putShort("Height", (short) H);
        root.putShort("Length", (short) D);
        CompoundTag p = new CompoundTag();
        pal.forEach(p::putInt);
        root.put("Palette", p);
        root.putByteArray("BlockData", cells.toByteArray());
        CompoundTag chest = new CompoundTag();
        chest.put("Pos", new IntArrayTag(new int[]{1, 1, 1}));
        chest.putString("Id", "minecraft:chest");
        chest.put("Items", diamonds(false));
        ListTag bes = new ListTag();
        bes.add(chest);
        root.put("BlockEntities", bes);
        checkHut(helper, readBack(root, "hut.schem"), "Sponge");
        helper.succeed();
    }

    private static long[] pack(int[] values, int bits) {
        long[] out = new long[(values.length * bits + 63) / 64];
        for (int i = 0; i < values.length; i++) {
            long start = (long) i * bits;
            int a = (int) (start >> 6), off = (int) (start & 63);
            out[a] |= (long) values[i] << off;
            if (off + bits > 64) {
                out[a + 1] |= (long) values[i] >>> (64 - off);
            }
        }
        return out;
    }

    private static CompoundTag xyz(int x, int y, int z) {
        CompoundTag t = new CompoundTag();
        t.putInt("x", x);
        t.putInt("y", y);
        t.putInt("z", z);
        return t;
    }

    private static CompoundTag stateTag(String s) {
        CompoundTag t = new CompoundTag();
        int open = s.indexOf('[');
        t.putString("Name", open < 0 ? s : s.substring(0, open));
        if (open >= 0) {
            CompoundTag props = new CompoundTag();
            for (String kv : s.substring(open + 1, s.length() - 1).split(",")) {
                props.putString(kv.split("=")[0], kv.split("=")[1]);
            }
            t.put("Properties", props);
        }
        return t;
    }

    @GameTest(template = "empty5x4x5")
    public static void readsLitematicaWithTwoRegions(GameTestHelper helper) throws IOException {
        List<String> pal = new ArrayList<>(palette().keySet());
        int[] values = new int[W * H * D];
        for (int y = 0; y < H; y++) {
            for (int z = 0; z < D; z++) {
                for (int x = 0; x < W; x++) {
                    values[(y * D + z) * W + x] = pal.indexOf(hut(x, y, z));
                }
            }
        }
        int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(pal.size() - 1));
        CompoundTag house = new CompoundTag();
        house.put("Position", xyz(0, 0, 0));
        house.put("Size", xyz(W, H, D));
        ListTag housePal = new ListTag();
        pal.forEach(n -> housePal.add(stateTag(n)));
        house.put("BlockStatePalette", housePal);
        house.put("BlockStates", new LongArrayTag(pack(values, bits)));
        CompoundTag chest = xyz(1, 1, 1);
        chest.putString("id", "minecraft:chest");
        chest.put("Items", diamonds(false));
        ListTag tes = new ListTag();
        tes.add(chest);
        house.put("TileEntities", tes);
        // a garden wall one block east of the house, stored with a negative size as Litematica does
        CompoundTag wall = new CompoundTag();
        wall.put("Position", xyz(W + 1, 2, D - 1));
        wall.put("Size", xyz(-1, -2, -D));
        ListTag wallPal = new ListTag();
        wallPal.add(stateTag("minecraft:air"));
        wallPal.add(stateTag("minecraft:cobblestone_wall[east=none,north=low,south=low,up=true,waterlogged=false,west=none]"));
        wall.put("BlockStatePalette", wallPal);
        int[] ones = new int[2 * D];
        java.util.Arrays.fill(ones, 1);
        wall.put("BlockStates", new LongArrayTag(pack(ones, 2)));
        CompoundTag regions = new CompoundTag();
        regions.put("house", house);
        regions.put("wall", wall);
        CompoundTag root = new CompoundTag();
        root.putInt("Version", 6);
        root.putInt("MinecraftDataVersion", 3465);
        root.put("Regions", regions);
        Schematic s = readBack(root, "hut.litematic");
        helper.assertTrue(s.width == W + 2 && s.height == H && s.depth == D, "box is " + s.width + "x" + s.height + "x" + s.depth);
        checkHut(helper, s, "Litematica");
        for (int z = 0; z < D; z++) {
            for (int y = 1; y <= 2; y++) {
                helper.assertTrue(name(s, W + 1, y, z).equals("minecraft:cobblestone_wall"), "wall missing at " + z + "," + y);
            }
        }
        helper.assertTrue(name(s, W + 1, 3, 0).equals("minecraft:air"), "wall too tall");
        helper.succeed();
    }
}
