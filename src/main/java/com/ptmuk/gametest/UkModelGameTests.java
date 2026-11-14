package com.ptmuk.gametest;

import com.ptmuk.PtmUk;
import com.ptmuk.building.schematic.ModelReader;
import com.ptmuk.building.schematic.Schematic;
import com.ptmuk.road.plan.RoadPlan;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Comparator;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 3D models made into buildings: a 10 m cube as a Wavefront .obj (its top brick red, its sides a
 * green texture, named for where it is on Earth) and a square as a glTF (a texture inside it, two
 * nodes' transforms, and the place on Earth in its scene), read at the planner's 1.8 blocks a
 * metre, and put at their real spot on an imported map.
 */
@GameTestHolder(PtmUk.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UkModelGameTests {
    private UkModelGameTests() {
    }

    private static final String CUBE_OBJ = """
            mtllib cube.mtl
            v 0 0 0
            v 10 0 0
            v 10 0 10
            v 0 0 10
            v 0 10 0
            v 10 10 0
            v 10 10 10
            v 0 10 10
            vt 0 0
            vt 1 0
            vt 1 1
            vt 0 1
            usemtl brick
            f 5 8 7 6
            usemtl leaves
            f 1/1 2/2 6/3 5/4
            f 2/1 3/2 7/3 6/4
            f 3/1 4/2 8/3 7/4
            f 4/1 1/2 5/3 8/4
            usemtl
            f 1 4 3 2
            """;

    private static final String CUBE_MTL = """
            newmtl brick
            Kd 0.62 0.33 0.27
            newmtl leaves
            Kd 0.8 0.8 0.8
            map_Kd green.png
            """;

    @GameTest(template = "empty5x4x5")
    public static void readsObjModels(GameTestHelper helper) throws IOException {
        Path dir = Files.createTempDirectory("ptmuk_model");
        try {
            Files.writeString(dir.resolve("cube@51.5,-0.1.obj"), CUBE_OBJ);
            Files.writeString(dir.resolve("cube.mtl"), CUBE_MTL);
            Files.write(dir.resolve("green.png"), png(0x28A028));
            Schematic s = ModelReader.read(dir.resolve("cube@51.5,-0.1.obj"), RoadPlan.MapImport.SCALE);
            helper.assertTrue(s.name.equals("cube") && s.lat == 51.5 && s.lon == -0.1, "its name and place: " + s.name + " " + s.lat + "," + s.lon);
            helper.assertTrue(s.width == 19 && s.height == 19 && s.depth == 19, "10 m should be 19 blocks across: " + s.width + "x" + s.height + "x" + s.depth);
            helper.assertTrue(block(s, 9, 9, 9).equals("minecraft:air"), "it should be hollow, has " + block(s, 9, 9, 9));
            helper.assertTrue(block(s, 9, 18, 9).equals("ptmuk:red_brick"), "the brick-red top should be red brick, is " + block(s, 9, 18, 9));
            int side = ModelReader.colourOf(block(s, 0, 9, 9));
            helper.assertTrue(side >= 0 && (side >> 8 & 255) > (side >> 16 & 255) && (side >> 8 & 255) > (side & 255),
                    "the textured side should be green, is " + block(s, 0, 9, 9));
            helper.assertTrue(!block(s, 9, 0, 9).equals("minecraft:air"), "the bottom should be there (grey, with no material)");
            // the same at the server: what's sent decodes the same
            Schematic back = Schematic.decode(s.encode());
            helper.assertTrue(back.width == 19 && back.solidBlocks() == s.solidBlocks(), "it didn't survive being sent");
            // at its real spot: its origin (its north-west corner) on the map's point for 51.5, -0.1
            RoadPlan.MapImport mi = new RoadPlan.MapImport();
            mi.lat0 = 51.5;
            mi.lon0 = -0.1;
            mi.x = 1000;
            mi.z = 2000;
            int[] at = ModelReader.realSpot(s, mi);
            helper.assertTrue(at != null && at[0] == 1000 && at[1] == 2000, "its real spot should be 1000, 2000: " + java.util.Arrays.toString(at));
            // too big at this scale: says to export less
            Files.writeString(dir.resolve("huge.obj"), "v 0 0 0\nv 1000 0 0\nv 0 0 1\nf 1 2 3\n");
            try {
                ModelReader.read(dir.resolve("huge.obj"), RoadPlan.MapImport.SCALE);
                helper.fail("a kilometre-wide model should be too big");
            } catch (IOException e) {
                helper.assertTrue(e.getMessage().contains("smaller area"), "it should say to export less: " + e.getMessage());
            }
        } finally {
            delete(dir);
        }
        helper.succeed();
    }

    @GameTest(template = "empty5x4x5")
    public static void readsGltfModels(GameTestHelper helper) throws IOException {
        // a 4 m square on the ground, in a node scaled 2x east-west inside one moved 20 m east and 5 m south
        ByteBuffer b = ByteBuffer.allocate(48 + 32 + 12).order(ByteOrder.LITTLE_ENDIAN);
        for (float[] v : new float[][]{{0, 0, 0}, {4, 0, 0}, {4, 0, 4}, {0, 0, 4}}) {
            b.putFloat(v[0]).putFloat(v[1]).putFloat(v[2]);
        }
        for (float[] t : new float[][]{{0, 0}, {1, 0}, {1, 1}, {0, 1}}) {
            b.putFloat(t[0]).putFloat(t[1]);
        }
        for (short i : new short[]{0, 2, 1, 0, 3, 2}) {
            b.putShort(i);
        }
        String buffer = "data:application/octet-stream;base64," + Base64.getEncoder().encodeToString(b.array());
        String image = "data:image/png;base64," + Base64.getEncoder().encodeToString(png(0x2840C0));
        String gltf = """
                {"asset": {"version": "2.0"},
                 "scene": 0, "scenes": [{"nodes": [0], "extras": {"lat": 51.6, "lon": -0.2}}],
                 "nodes": [{"translation": [20, 0, 5], "children": [1]}, {"scale": [2, 1, 1], "mesh": 0}],
                 "meshes": [{"primitives": [{"attributes": {"POSITION": 0, "TEXCOORD_0": 1}, "indices": 2, "material": 0}]}],
                 "materials": [{"pbrMetallicRoughness": {"baseColorTexture": {"index": 0}}}],
                 "textures": [{"source": 0}], "images": [{"uri": "IMAGE"}],
                 "buffers": [{"byteLength": 92, "uri": "BUFFER"}],
                 "bufferViews": [{"buffer": 0, "byteOffset": 0, "byteLength": 48}, {"buffer": 0, "byteOffset": 48, "byteLength": 32},
                                 {"buffer": 0, "byteOffset": 80, "byteLength": 12}],
                 "accessors": [{"bufferView": 0, "componentType": 5126, "count": 4, "type": "VEC3"},
                               {"bufferView": 1, "componentType": 5126, "count": 4, "type": "VEC2"},
                               {"bufferView": 2, "componentType": 5123, "count": 6, "type": "SCALAR"}]}
                """.replace("IMAGE", image).replace("BUFFER", buffer);
        Path dir = Files.createTempDirectory("ptmuk_model");
        try {
            Files.writeString(dir.resolve("square.gltf"), gltf);
            Schematic s = ModelReader.read(dir.resolve("square.gltf"), RoadPlan.MapImport.SCALE);
            // 20..28 m east is blocks 36..50, 5..9 m south blocks 9..16
            helper.assertTrue(s.width == 15 && s.height == 1 && s.depth == 8, "should be 15 x 8, 1 high: " + s.width + "x" + s.depth + ", " + s.height);
            helper.assertTrue(s.originX == -36 && s.originZ == -9 && s.lat == 51.6 && s.lon == -0.2,
                    "its origin and place: " + s.originX + ", " + s.originZ + " at " + s.lat + "," + s.lon);
            int c = ModelReader.colourOf(block(s, 7, 0, 4));
            helper.assertTrue(c >= 0 && (c & 255) > (c >> 16 & 255) && (c & 255) > (c >> 8 & 255), "the texture's blue: " + block(s, 7, 0, 4));
            Files.writeString(dir.resolve("draco.gltf"), "{\"asset\": {\"version\": \"2.0\"}, \"extensionsRequired\": [\"KHR_draco_mesh_compression\"]}");
            try {
                ModelReader.read(dir.resolve("draco.gltf"), RoadPlan.MapImport.SCALE);
                helper.fail("a Draco-compressed glTF can't be read");
            } catch (IOException e) {
                helper.assertTrue(e.getMessage().contains("compression off"), "it should say to export without compression: " + e.getMessage());
            }
        } finally {
            delete(dir);
        }
        helper.succeed();
    }

    private static String block(Schematic s, int x, int y, int z) {
        return s.palette.get(s.cells[s.index(x, y, z)]).getString("Name");
    }

    private static byte[] png(int rgb) throws IOException {
        BufferedImage img = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < 4; i++) {
            img.setRGB(i % 2, i / 2, rgb);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static void delete(Path dir) throws IOException {
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path p : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }
}
