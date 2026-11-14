package com.ptmuk.building.schematic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ptmuk.road.plan.RoadPlan;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import net.minecraft.nbt.CompoundTag;

/**
 * 3D models made into buildings: Wavefront .obj (with its .mtl materials and their textures) and
 * glTF (.gltf with its .bin and images, or one .glb), such as a street exported from Blender. Read
 * in metres the way Blender exports them (y up, x east, z south), scaled to the planner's blocks a
 * metre, and every block the model's surface passes through made the block whose colour is
 * nearest the model's there (its texture, or its material's colour): walls and roofs, hollow
 * inside. A file named name@lat,lon (or a glTF whose scene has lat and lon, as Blosm's does when
 * Blender exports custom properties) knows where on Earth its origin is, so it can go at its real
 * spot on the planner's imported map.
 */
public final class ModelReader {
    public static final List<String> EXTENSIONS = List.of(".obj", ".gltf", ".glb");

    /** What it's built of, each with its average colour (from its texture). */
    private static final Object[] BLOCKS = {
            "minecraft:white_concrete", 0xCFD5D6,
            "minecraft:white_terracotta", 0xD1B2A1,
            "minecraft:orange_concrete", 0xE06100,
            "minecraft:orange_terracotta", 0xA15325,
            "minecraft:magenta_concrete", 0xA9309F,
            "minecraft:magenta_terracotta", 0x95586C,
            "minecraft:light_blue_concrete", 0x2389C6,
            "minecraft:light_blue_terracotta", 0x716C89,
            "minecraft:yellow_concrete", 0xF0AF15,
            "minecraft:yellow_terracotta", 0xBA8523,
            "minecraft:lime_concrete", 0x5EA818,
            "minecraft:lime_terracotta", 0x677534,
            "minecraft:pink_concrete", 0xD5658E,
            "minecraft:pink_terracotta", 0xA14E4E,
            "minecraft:gray_concrete", 0x36393D,
            "minecraft:gray_terracotta", 0x392A23,
            "minecraft:light_gray_concrete", 0x7D7D73,
            "minecraft:light_gray_terracotta", 0x876A61,
            "minecraft:cyan_concrete", 0x157788,
            "minecraft:cyan_terracotta", 0x565B5B,
            "minecraft:purple_concrete", 0x641F9C,
            "minecraft:purple_terracotta", 0x764656,
            "minecraft:blue_concrete", 0x2C2E8F,
            "minecraft:blue_terracotta", 0x4A3B5B,
            "minecraft:brown_concrete", 0x603B1F,
            "minecraft:brown_terracotta", 0x4D3323,
            "minecraft:green_concrete", 0x495B24,
            "minecraft:green_terracotta", 0x4C532A,
            "minecraft:red_concrete", 0x8E2020,
            "minecraft:red_terracotta", 0x8F3D2E,
            "minecraft:black_concrete", 0x080A0F,
            "minecraft:black_terracotta", 0x251610,
            "minecraft:terracotta", 0x985E43,
            "minecraft:stone", 0x7D7D7D,
            "minecraft:stone_bricks", 0x7A797A,
            "minecraft:smooth_stone", 0x9E9E9E,
            "minecraft:andesite", 0x888888,
            "minecraft:polished_andesite", 0x848685,
            "minecraft:diorite", 0xBCBCBC,
            "minecraft:polished_diorite", 0xC0C1C2,
            "minecraft:granite", 0x956755,
            "minecraft:polished_granite", 0x9A6A59,
            "minecraft:deepslate_bricks", 0x464647,
            "minecraft:deepslate_tiles", 0x363637,
            "minecraft:polished_deepslate", 0x484849,
            "minecraft:bricks", 0x966153,
            "minecraft:mud_bricks", 0x89674F,
            "minecraft:packed_mud", 0x8E6A4F,
            "minecraft:smooth_sandstone", 0xDFD6AA,
            "minecraft:cut_sandstone", 0xD9CE9F,
            "minecraft:smooth_red_sandstone", 0xB5611F,
            "minecraft:quartz_block", 0xEBE5DE,
            "minecraft:smooth_quartz", 0xECE6DF,
            "minecraft:calcite", 0xDFE0DC,
            "minecraft:tuff", 0x6C6D66,
            "minecraft:polished_blackstone", 0x353038,
            "minecraft:polished_blackstone_bricks", 0x302A31,
            "minecraft:nether_bricks", 0x2C151A,
            "minecraft:oak_planks", 0xA2824E,
            "minecraft:spruce_planks", 0x725430,
            "minecraft:birch_planks", 0xC0AF79,
            "minecraft:dark_oak_planks", 0x422B14,
            "minecraft:jungle_planks", 0xA07350,
            "minecraft:acacia_planks", 0xA85A32,
            "minecraft:white_wool", 0xE9ECEC,
            "minecraft:light_gray_wool", 0x8E8E86,
            "minecraft:gray_wool", 0x3E4447,
            "minecraft:black_wool", 0x141519,
            "minecraft:moss_block", 0x596D2D,
            "minecraft:smooth_basalt", 0x48484E,
            "minecraft:dripstone_block", 0x866B5C,
            "minecraft:mossy_stone_bricks", 0x737969,
            "minecraft:prismarine_bricks", 0x63AB9E,
            "ptmuk:red_brick", 0x9D5847,
            "ptmuk:london_stock_brick", 0xB7A172,
            "ptmuk:blue_engineering_brick", 0x403E47,
            "ptmuk:pebbledash", 0xC8C0B3,
            "ptmuk:white_render", 0xEBE9E3,
            "ptmuk:portland_stone", 0xDAD2BB,
            "ptmuk:green_faience", 0x205D3C,
            "ptmuk:burgundy_faience", 0x6A1C25,
            "ptmuk:roof_slate", 0x3A3E48,
            "ptmuk:roof_clay_tiles", 0x86412E,
            "ptmuk:roof_concrete_tiles", 0x5E4637
    };
    private static final int[] RGB = new int[BLOCKS.length / 2];
    /** The nearest block to each colour (5 bits a channel), worked out as needed. */
    private static final int[] NEAREST = new int[1 << 15];
    private static final Pattern AT = Pattern.compile("@\\s*(-?\\d+(?:\\.\\d+)?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?)");
    /** How far apart the points on a triangle are sampled, in blocks: under one, so no block's missed. */
    private static final double STEP = 0.45;

    static {
        for (int i = 0; i < RGB.length; i++) {
            RGB[i] = (Integer) BLOCKS[2 * i + 1];
        }
        Arrays.fill(NEAREST, -1);
    }

    private ModelReader() {
    }

    public static boolean isModel(Path file) {
        String n = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return EXTENSIONS.stream().anyMatch(n::endsWith);
    }

    /** A material: its colour (0..1 a channel) and its texture, if it has one that could be read. */
    private record Material(float r, float g, float b, BufferedImage texture) {
        static final Material GREY = new Material(0.7f, 0.7f, 0.7f, null);
    }

    /** Triangles in metres: three corners (x, y, z), their texture coordinates (u, v down from the top; NaN if none), a material each. */
    private static final class Triangles {
        float[] xyz = new float[9 * 1024], uv = new float[6 * 1024];
        Material[] mat = new Material[1024];
        int n;
        double lat = Double.NaN, lon = Double.NaN;

        void add(float[] p, float[] t, Material m) {
            if (n == mat.length) {
                xyz = Arrays.copyOf(xyz, xyz.length * 2);
                uv = Arrays.copyOf(uv, uv.length * 2);
                mat = Arrays.copyOf(mat, mat.length * 2);
            }
            System.arraycopy(p, 0, xyz, 9 * n, 9);
            System.arraycopy(t, 0, uv, 6 * n, 6);
            mat[n++] = m;
        }
    }

    public static Schematic read(Path file, double scale) throws IOException {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        Triangles t = new Triangles();
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (ext.equals("obj")) {
            obj(file, t);
        } else {
            gltf(file, t);
        }
        Matcher m = AT.matcher(base);
        if (m.find()) {
            t.lat = Double.parseDouble(m.group(1));
            t.lon = Double.parseDouble(m.group(2));
            base = base.substring(0, m.start()).trim();
        }
        if (t.n == 0) {
            throw new IOException("there are no triangles in it");
        }
        return voxelise(base.isEmpty() ? "model" : base, ext.toUpperCase(Locale.ROOT) + " model", t, scale);
    }

    /**
     * Where the box of a model with a known origin goes on the planner's imported map: the block
     * x, z of its north-west corner; null if it doesn't know where it is.
     */
    public static int[] realSpot(Schematic s, RoadPlan.MapImport mi) {
        if (Double.isNaN(s.lat) || Double.isNaN(s.lon) || mi == null) {
            return null;
        }
        return new int[]{(int) Math.floor(mi.x(s.lon)) - s.originX, (int) Math.floor(mi.z(s.lat)) - s.originZ};
    }

    // ------------------------------------------------------------------ into blocks

    private static Schematic voxelise(String name, String format, Triangles t, double scale) throws IOException {
        double x0 = Double.MAX_VALUE, y0 = Double.MAX_VALUE, z0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, y1 = -Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
        for (int i = 0; i < t.n * 3; i++) {
            float x = t.xyz[3 * i], y = t.xyz[3 * i + 1], z = t.xyz[3 * i + 2];
            if (Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z)) {
                x0 = Math.min(x0, x);
                y0 = Math.min(y0, y);
                z0 = Math.min(z0, z);
                x1 = Math.max(x1, x);
                y1 = Math.max(y1, y);
                z1 = Math.max(z1, z);
            }
        }
        int fx = (int) Math.floor(x0 * scale), fy = (int) Math.floor(y0 * scale), fz = (int) Math.floor(z0 * scale);
        long w = (long) Math.floor(x1 * scale) - fx + 1, h = (long) Math.floor(y1 * scale) - fy + 1, d = (long) Math.floor(z1 * scale) - fz + 1;
        try {
            Schematic.checkSize(w, h, d);
        } catch (IOException e) {
            throw new IOException(e.getMessage() + String.format(Locale.ROOT, " at %.1f blocks a metre: export a smaller area", scale));
        }
        int W = (int) w, H = (int) h, D = (int) d;
        int[] colour = new int[W * H * D];
        byte[] count = new byte[W * H * D];
        double[] p = new double[9];
        for (int k = 0; k < t.n; k++) {
            for (int c = 0; c < 3; c++) {
                p[3 * c] = t.xyz[9 * k + 3 * c] * scale - fx;
                p[3 * c + 1] = t.xyz[9 * k + 3 * c + 1] * scale - fy;
                p[3 * c + 2] = t.xyz[9 * k + 3 * c + 2] * scale - fz;
            }
            double longest = Math.max(dist(p, 0, 3), Math.max(dist(p, 0, 6), dist(p, 3, 6)));
            if (!Double.isFinite(longest)) {
                continue;
            }
            int steps = Math.max(1, (int) Math.ceil(longest / STEP));
            Material m = t.mat[k];
            for (int i = 0; i <= steps; i++) {
                for (int j = 0; i + j <= steps; j++) {
                    double a = i / (double) steps, b = j / (double) steps, o = 1 - a - b;
                    int x = (int) Math.floor(o * p[0] + a * p[3] + b * p[6]), y = (int) Math.floor(o * p[1] + a * p[4] + b * p[7]);
                    int z = (int) Math.floor(o * p[2] + a * p[5] + b * p[8]);
                    if (x < 0 || y < 0 || z < 0 || x >= W || y >= H || z >= D) {
                        continue;
                    }
                    int cell = (y * D + z) * W + x;
                    int rgb = colourAt(m, t.uv, 6 * k, o, a, b);
                    int seen = (count[cell] & 0xFF) + 1;
                    if (seen <= 255) {                               // a running average, a channel at a time
                        int old = colour[cell];
                        colour[cell] = mix(old >> 16 & 255, rgb >> 16 & 255, seen) << 16 | mix(old >> 8 & 255, rgb >> 8 & 255, seen) << 8
                                | mix(old & 255, rgb & 255, seen);
                        count[cell] = (byte) seen;
                    }
                }
            }
        }
        List<CompoundTag> palette = new ArrayList<>();
        palette.add(state("minecraft:air"));
        Map<Integer, Integer> used = new HashMap<>();
        int[] cells = new int[W * H * D];
        for (int i = 0; i < cells.length; i++) {
            if (count[i] != 0) {
                int block = nearest(colour[i]);
                cells[i] = used.computeIfAbsent(block, bl -> {
                    palette.add(state((String) BLOCKS[2 * bl]));
                    return palette.size() - 1;
                });
            }
        }
        Schematic s = new Schematic(name, format, W, H, D, palette, cells, new ArrayList<>());
        s.lat = t.lat;
        s.lon = t.lon;
        s.originX = -fx;
        s.originZ = -fz;
        return s;
    }

    private static int mix(int old, int now, int seen) {
        return old + (now - old) / seen;
    }

    private static double dist(double[] p, int i, int j) {
        return Math.sqrt((p[i] - p[j]) * (p[i] - p[j]) + (p[i + 1] - p[j + 1]) * (p[i + 1] - p[j + 1]) + (p[i + 2] - p[j + 2]) * (p[i + 2] - p[j + 2]));
    }

    private static CompoundTag state(String block) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", block);
        return tag;
    }

    /** The model's colour at a point of a triangle (weights o, a, b of its corners): its texture there times its material's colour. */
    private static int colourAt(Material m, float[] uv, int at, double o, double a, double b) {
        float r = m.r(), g = m.g(), bl = m.b();
        if (m.texture() != null && !Float.isNaN(uv[at])) {
            double u = o * uv[at] + a * uv[at + 2] + b * uv[at + 4], v = o * uv[at + 1] + a * uv[at + 3] + b * uv[at + 5];
            BufferedImage img = m.texture();
            int px = Math.floorMod((int) Math.floor(u * img.getWidth()), img.getWidth());
            int py = Math.floorMod((int) Math.floor(v * img.getHeight()), img.getHeight());
            int c = img.getRGB(px, py);
            r *= (c >> 16 & 255) / 255f;
            g *= (c >> 8 & 255) / 255f;
            bl *= (c & 255) / 255f;
        }
        return Math.round(r * 255) << 16 | Math.round(g * 255) << 8 | Math.round(bl * 255);
    }

    /** The block whose colour is nearest (weighted for how eyes see red, green and blue). */
    static int nearest(int rgb) {
        int key = (rgb >> 19 & 31) << 10 | (rgb >> 11 & 31) << 5 | (rgb >> 3 & 31);
        int hit = NEAREST[key];
        if (hit >= 0) {
            return hit;
        }
        int r = (rgb >> 16 & 255), g = (rgb >> 8 & 255), b = rgb & 255, best = 0;
        long bestD = Long.MAX_VALUE;
        for (int i = 0; i < RGB.length; i++) {
            int r2 = RGB[i] >> 16 & 255, g2 = RGB[i] >> 8 & 255, b2 = RGB[i] & 255, mean = (r + r2) / 2;
            long dr = r - r2, dg = g - g2, db = b - b2;
            long dd = ((512 + mean) * dr * dr >> 8) + 4 * dg * dg + ((767 - mean) * db * db >> 8);
            if (dd < bestD) {
                bestD = dd;
                best = i;
            }
        }
        NEAREST[key] = best;
        return best;
    }

    /** A block's colour, as used for matching. */
    public static int colourOf(String block) {
        for (int i = 0; i < RGB.length; i++) {
            if (BLOCKS[2 * i].equals(block)) {
                return RGB[i];
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ Wavefront .obj

    private static void obj(Path file, Triangles t) throws IOException {
        float[] v = new float[3 * 4096], vt = new float[2 * 4096];
        int nv = 0, nvt = 0;
        Map<String, Material> materials = new HashMap<>();
        Material current = Material.GREY;
        float[] p = new float[9], uv = new float[6];
        try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.charAt(0) == '#') {
                    continue;
                }
                String[] w = line.split("\\s+");
                switch (w[0]) {
                    case "v" -> {
                        if (nv + 3 > v.length) {
                            v = Arrays.copyOf(v, v.length * 2);
                        }
                        for (int i = 0; i < 3; i++) {
                            v[nv++] = Float.parseFloat(w[1 + i]);
                        }
                    }
                    case "vt" -> {
                        if (nvt + 2 > vt.length) {
                            vt = Arrays.copyOf(vt, vt.length * 2);
                        }
                        vt[nvt++] = Float.parseFloat(w[1]);
                        vt[nvt++] = w.length > 2 ? 1 - Float.parseFloat(w[2]) : 0;   // .obj counts v up from the bottom
                    }
                    case "mtllib" -> materials.putAll(mtl(file.resolveSibling(line.substring(6).trim())));
                    case "usemtl" -> current = materials.getOrDefault(line.substring(6).trim(), Material.GREY);
                    case "f" -> {
                        int corners = w.length - 1;
                        int[] vi = new int[corners], ti = new int[corners];
                        for (int i = 0; i < corners; i++) {
                            String[] parts = w[1 + i].split("/");
                            vi[i] = index(parts[0], nv / 3);
                            ti[i] = parts.length > 1 && !parts[1].isEmpty() ? index(parts[1], nvt / 2) : -1;
                        }
                        for (int i = 1; i + 1 < corners; i++) {                // a polygon as a fan of triangles
                            int[] c = {0, i, i + 1};
                            for (int k = 0; k < 3; k++) {
                                int a = vi[c[k]], b = ti[c[k]];
                                if (a < 0 || a >= nv / 3) {
                                    throw new IOException("a face points at a corner that isn't there");
                                }
                                System.arraycopy(v, 3 * a, p, 3 * k, 3);
                                uv[2 * k] = b >= 0 && b < nvt / 2 ? vt[2 * b] : Float.NaN;
                                uv[2 * k + 1] = b >= 0 && b < nvt / 2 ? vt[2 * b + 1] : Float.NaN;
                            }
                            t.add(p, uv, current);
                        }
                    }
                    default -> {
                    }
                }
            }
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException | StringIndexOutOfBoundsException e) {
            throw new IOException("not a .obj this can read: " + e.getMessage());
        }
    }

    /** A corner's number in a face: counted from 1, or back from the last one if it's negative. */
    private static int index(String s, int count) {
        int i = Integer.parseInt(s);
        return i < 0 ? count + i : i - 1;
    }

    private static Map<String, Material> mtl(Path file) {
        Map<String, Material> out = new HashMap<>();
        if (!Files.isRegularFile(file)) {
            return out;
        }
        try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line, name = null;
            float[] kd = {0.7f, 0.7f, 0.7f};
            BufferedImage tex = null;
            while (true) {
                line = in.readLine();
                String[] w = line == null ? new String[]{"newmtl"} : line.trim().split("\\s+");
                if (w[0].equals("newmtl")) {
                    if (name != null) {
                        // with a texture its colour is the texture's (Blender writes its default grey as Kd beside one)
                        out.put(name, tex != null ? new Material(1, 1, 1, tex) : new Material(kd[0], kd[1], kd[2], null));
                    }
                    if (line == null) {
                        break;
                    }
                    name = line.trim().substring(6).trim();
                    kd = new float[]{0.7f, 0.7f, 0.7f};
                    tex = null;
                } else if (w[0].equals("Kd") && w.length >= 4) {
                    kd = new float[]{Float.parseFloat(w[1]), Float.parseFloat(w[2]), Float.parseFloat(w[3])};
                } else if (w[0].equals("map_Kd") && w.length >= 2) {
                    tex = image(file.resolveSibling(w[w.length - 1]));      // options come first; the file's last
                }
            }
        } catch (IOException | RuntimeException e) {
            // a broken material file: what's read so far, the rest grey
        }
        return out;
    }

    private static BufferedImage image(Path file) {
        try {
            return Files.isRegularFile(file) ? ImageIO.read(file.toFile()) : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static BufferedImage image(byte[] data) {
        try {
            return ImageIO.read(new ByteArrayInputStream(data));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ glTF

    private static void gltf(Path file, Triangles t) throws IOException {
        byte[] all = Files.readAllBytes(file);
        JsonObject j;
        byte[] bin = null;
        try {
            if (all.length >= 20 && all[0] == 'g' && all[1] == 'l' && all[2] == 'T' && all[3] == 'F') {
                ByteBuffer b = ByteBuffer.wrap(all).order(ByteOrder.LITTLE_ENDIAN);
                int at = 12;
                String json = null;
                while (at + 8 <= all.length) {
                    int len = b.getInt(at), type = b.getInt(at + 4);
                    if (len < 0 || at + 8 + len > all.length) {
                        throw new IOException("a broken .glb");
                    }
                    if (type == 0x4E4F534A) {                            // "JSON"
                        json = new String(all, at + 8, len, StandardCharsets.UTF_8);
                    } else if (type == 0x004E4942 && bin == null) {      // "BIN"
                        bin = Arrays.copyOfRange(all, at + 8, at + 8 + len);
                    }
                    at += 8 + ((len + 3) & ~3);
                }
                if (json == null) {
                    throw new IOException("a .glb with no JSON in it");
                }
                j = JsonParser.parseString(json).getAsJsonObject();
            } else {
                j = JsonParser.parseString(new String(all, StandardCharsets.UTF_8)).getAsJsonObject();
            }
            new Gltf(file, j, bin, t).read();
        } catch (IllegalStateException | ClassCastException | NullPointerException | IndexOutOfBoundsException
                 | NumberFormatException | UnsupportedOperationException | com.google.gson.JsonParseException e) {
            throw new IOException("not a glTF this can read: " + e.getMessage());
        }
    }

    private static final class Gltf {
        final Path file;
        final JsonObject j;
        final byte[] glbBin;
        final Triangles t;
        final List<byte[]> buffers = new ArrayList<>();
        final Map<Integer, BufferedImage> images = new HashMap<>();
        final Map<Integer, Material> materials = new HashMap<>();

        Gltf(Path file, JsonObject j, byte[] glbBin, Triangles t) {
            this.file = file;
            this.j = j;
            this.glbBin = glbBin;
            this.t = t;
        }

        JsonArray list(String key) {
            return j.has(key) ? j.getAsJsonArray(key) : new JsonArray();
        }

        void read() throws IOException {
            for (JsonElement e : list("extensionsRequired")) {
                String x = e.getAsString();
                if (x.equals("KHR_draco_mesh_compression") || x.equals("EXT_meshopt_compression")) {
                    throw new IOException("it's compressed (" + x + "): export it again with compression off");
                }
            }
            for (JsonElement e : list("buffers")) {
                JsonObject b = e.getAsJsonObject();
                buffers.add(b.has("uri") ? data(b.get("uri").getAsString()) : glbBin);
            }
            JsonArray scenes = list("scenes");
            JsonObject scene = scenes.isEmpty() ? null : scenes.get(j.has("scene") ? j.get("scene").getAsInt() : 0).getAsJsonObject();
            // where on Earth (Blosm keeps the scene's lat and lon; Blender writes them with custom properties on)
            JsonObject extras = scene != null && scene.has("extras") ? scene.getAsJsonObject("extras") : null;
            if (extras != null && extras.has("lat") && extras.has("lon")) {
                t.lat = extras.get("lat").getAsDouble();
                t.lon = extras.get("lon").getAsDouble();
            }
            JsonArray roots = scene != null && scene.has("nodes") ? scene.getAsJsonArray("nodes") : null;
            if (roots == null) {
                roots = new JsonArray();
                for (int i = 0; i < list("nodes").size(); i++) {
                    roots.add(i);                                   // no scene: every node (shortcut: children are then done twice)
                }
            }
            for (JsonElement r : roots) {
                node(r.getAsInt(), identity(), 0);
            }
        }

        /** A buffer or image's bytes from its uri: data inline, or a file beside the model (nowhere else). */
        byte[] data(String uri) throws IOException {
            if (uri.startsWith("data:")) {
                return Base64.getDecoder().decode(uri.substring(uri.indexOf(',') + 1));
            }
            String path = java.net.URLDecoder.decode(uri.replace("+", "%2B"), StandardCharsets.UTF_8);
            Path folder = file.toAbsolutePath().getParent().normalize(), f = folder.resolve(path).normalize();
            if (!f.startsWith(folder)) {
                throw new IOException("it points at a file outside its folder: " + uri);
            }
            return Files.readAllBytes(f);
        }

        void node(int i, double[] parent, int depth) throws IOException {
            if (depth > 64) {
                throw new IOException("its nodes go round in a loop");
            }
            JsonObject n = list("nodes").get(i).getAsJsonObject();
            double[] m = mul(parent, local(n));
            if (n.has("mesh")) {
                mesh(list("meshes").get(n.get("mesh").getAsInt()).getAsJsonObject(), m);
            }
            if (n.has("children")) {
                for (JsonElement c : n.getAsJsonArray("children")) {
                    node(c.getAsInt(), m, depth + 1);
                }
            }
        }

        void mesh(JsonObject mesh, double[] m) throws IOException {
            float[] p = new float[9], uv = new float[6];
            for (JsonElement pe : mesh.getAsJsonArray("primitives")) {
                JsonObject prim = pe.getAsJsonObject();
                if (prim.has("mode") && prim.get("mode").getAsInt() != 4) {
                    continue;                                       // points and lines: nothing to build
                }
                JsonObject at = prim.getAsJsonObject("attributes");
                float[] pos = accessor(at.get("POSITION").getAsInt());
                float[] tex = at.has("TEXCOORD_0") ? accessor(at.get("TEXCOORD_0").getAsInt()) : null;
                int corners = pos.length / 3;
                float[] idx = prim.has("indices") ? accessor(prim.get("indices").getAsInt()) : null;
                Material mat = prim.has("material") ? material(prim.get("material").getAsInt()) : Material.GREY;
                int count = idx != null ? idx.length : corners;
                for (int k = 0; k + 2 < count; k += 3) {
                    for (int c = 0; c < 3; c++) {
                        int v = idx != null ? (int) idx[k + c] : k + c;
                        if (v < 0 || v >= corners) {
                            throw new IOException("a triangle points at a corner that isn't there");
                        }
                        double x = pos[3 * v], y = pos[3 * v + 1], z = pos[3 * v + 2];
                        p[3 * c] = (float) (m[0] * x + m[4] * y + m[8] * z + m[12]);
                        p[3 * c + 1] = (float) (m[1] * x + m[5] * y + m[9] * z + m[13]);
                        p[3 * c + 2] = (float) (m[2] * x + m[6] * y + m[10] * z + m[14]);
                        uv[2 * c] = tex != null ? tex[2 * v] : Float.NaN;
                        uv[2 * c + 1] = tex != null ? tex[2 * v + 1] : Float.NaN;
                    }
                    t.add(p, uv, mat);
                }
            }
        }

        Material material(int i) throws IOException {
            Material known = materials.get(i);
            if (known != null) {
                return known;
            }
            JsonObject mat = list("materials").get(i).getAsJsonObject();
            JsonObject pbr = mat.has("pbrMetallicRoughness") ? mat.getAsJsonObject("pbrMetallicRoughness") : new JsonObject();
            float[] f = {1, 1, 1};
            if (pbr.has("baseColorFactor")) {
                JsonArray a = pbr.getAsJsonArray("baseColorFactor");
                for (int k = 0; k < 3; k++) {
                    f[k] = (float) Math.pow(a.get(k).getAsDouble(), 1 / 2.2);   // stored linear; textures and blocks are sRGB
                }
            }
            BufferedImage img = null;
            if (pbr.has("baseColorTexture")) {
                JsonObject tx = list("textures").get(pbr.getAsJsonObject("baseColorTexture").get("index").getAsInt()).getAsJsonObject();
                if (tx.has("source")) {
                    img = image(tx.get("source").getAsInt());
                }
            }
            Material out = new Material(f[0], f[1], f[2], img);
            materials.put(i, out);
            return out;
        }

        BufferedImage image(int i) throws IOException {
            if (images.containsKey(i)) {
                return images.get(i);
            }
            JsonObject im = list("images").get(i).getAsJsonObject();
            byte[] bytes;
            if (im.has("bufferView")) {
                JsonObject bv = list("bufferViews").get(im.get("bufferView").getAsInt()).getAsJsonObject();
                byte[] buf = buffers.get(bv.get("buffer").getAsInt());
                int off = bv.has("byteOffset") ? bv.get("byteOffset").getAsInt() : 0;
                bytes = Arrays.copyOfRange(buf, off, off + bv.get("byteLength").getAsInt());
            } else {
                bytes = data(im.get("uri").getAsString());
            }
            BufferedImage img = ModelReader.image(bytes);
            images.put(i, img);
            return img;
        }

        /** An accessor's numbers as floats (normalised integers made 0..1, as glTF says). */
        float[] accessor(int i) throws IOException {
            JsonObject a = list("accessors").get(i).getAsJsonObject();
            int count = a.get("count").getAsInt(), type = a.get("componentType").getAsInt();
            int comps = switch (a.get("type").getAsString()) {
                case "SCALAR" -> 1;
                case "VEC2" -> 2;
                case "VEC3" -> 3;
                case "VEC4" -> 4;
                default -> throw new IOException("an accessor of " + a.get("type").getAsString());
            };
            boolean norm = a.has("normalized") && a.get("normalized").getAsBoolean();
            float[] out = new float[count * comps];
            if (!a.has("bufferView")) {
                return out;                                         // all zeros, as glTF says
            }
            JsonObject bv = list("bufferViews").get(a.get("bufferView").getAsInt()).getAsJsonObject();
            byte[] buf = buffers.get(bv.get("buffer").getAsInt());
            if (buf == null) {
                throw new IOException("a buffer's missing");
            }
            int size = switch (type) {
                case 5120, 5121 -> 1;
                case 5122, 5123 -> 2;
                case 5125, 5126 -> 4;
                default -> throw new IOException("numbers of type " + type);
            };
            int start = (bv.has("byteOffset") ? bv.get("byteOffset").getAsInt() : 0) + (a.has("byteOffset") ? a.get("byteOffset").getAsInt() : 0);
            int stride = bv.has("byteStride") ? bv.get("byteStride").getAsInt() : size * comps;
            ByteBuffer b = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
            for (int e = 0; e < count; e++) {
                for (int c = 0; c < comps; c++) {
                    int at = start + e * stride + c * size;
                    out[e * comps + c] = switch (type) {
                        case 5120 -> norm ? Math.max(b.get(at) / 127f, -1) : b.get(at);
                        case 5121 -> norm ? (b.get(at) & 0xFF) / 255f : b.get(at) & 0xFF;
                        case 5122 -> norm ? Math.max(b.getShort(at) / 32767f, -1) : b.getShort(at);
                        case 5123 -> norm ? (b.getShort(at) & 0xFFFF) / 65535f : b.getShort(at) & 0xFFFF;
                        case 5125 -> b.getInt(at) & 0xFFFFFFFFL;
                        default -> b.getFloat(at);
                    };
                }
            }
            return out;
        }

        /** A node's own transform: its matrix, or its translation, rotation and scale. */
        static double[] local(JsonObject n) {
            if (n.has("matrix")) {
                double[] m = new double[16];
                JsonArray a = n.getAsJsonArray("matrix");
                for (int k = 0; k < 16; k++) {
                    m[k] = a.get(k).getAsDouble();
                }
                return m;
            }
            double[] tr = vec(n, "translation", 0, 0, 0), q = vec(n, "rotation", 0, 0, 0, 1), s = vec(n, "scale", 1, 1, 1);
            double x = q[0], y = q[1], z = q[2], w = q[3];
            // column-major: rotation times scale, then the translation
            return new double[]{
                    (1 - 2 * (y * y + z * z)) * s[0], 2 * (x * y + z * w) * s[0], 2 * (x * z - y * w) * s[0], 0,
                    2 * (x * y - z * w) * s[1], (1 - 2 * (x * x + z * z)) * s[1], 2 * (y * z + x * w) * s[1], 0,
                    2 * (x * z + y * w) * s[2], 2 * (y * z - x * w) * s[2], (1 - 2 * (x * x + y * y)) * s[2], 0,
                    tr[0], tr[1], tr[2], 1};
        }

        static double[] vec(JsonObject n, String key, double... dflt) {
            if (!n.has(key)) {
                return dflt;
            }
            JsonArray a = n.getAsJsonArray(key);
            double[] out = new double[dflt.length];
            for (int k = 0; k < out.length; k++) {
                out[k] = a.get(k).getAsDouble();
            }
            return out;
        }

        static double[] identity() {
            return new double[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
        }

        static double[] mul(double[] a, double[] b) {
            double[] out = new double[16];
            for (int c = 0; c < 4; c++) {
                for (int r = 0; r < 4; r++) {
                    double v = 0;
                    for (int k = 0; k < 4; k++) {
                        v += a[k * 4 + r] * b[c * 4 + k];
                    }
                    out[c * 4 + r] = v;
                }
            }
            return out;
        }
    }
}
