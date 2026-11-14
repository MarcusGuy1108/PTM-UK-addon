package com.ptmuk.gametest;

import com.ptmuk.PtmUk;
import com.ptmuk.registry.ModItems;
import com.ptmuk.road.MapBuildings;
import com.ptmuk.road.RoadBuilder;
import com.ptmuk.road.RoadPlans;
import com.ptmuk.road.plan.RoadPlan;
import com.ptmuk.road.plan.RoadStyle;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * A real map's worth of plan built with one press of Build, as from the planner: far too big for
 * one go, so it's built a part at a time on its own, every part, wherever the player is. A grid of
 * streets 1.6 km by 0.9 km with a point every 80 blocks (as an imported map has), a kilometre of
 * road with no points along it, a broken road of no length, buildings by the streets and one far
 * from any road. Then every road and building is there, and the planner (put away by then) has
 * the plan with it all marked built.
 */
@GameTestHolder(PtmUk.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UkBigMapGameTests {
    private static final int OX = 60000, OZ = 60000;

    private UkBigMapGameTests() {
    }

    @GameTest(template = "empty5x4x5", batch = "bigmap", timeoutTicks = 24000)
    public static void buildsAWholeBigMapWithOneBuild(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "big-map-test"));
        player.getInventory().selected = 0;
        ItemStack planner = new ItemStack(ModItems.ROAD_PLANNER.get());
        player.getInventory().setItem(0, planner);
        RoadPlan plan = new RoadPlan();
        RoadStyle street = RoadStyle.residential();
        // streets every 300 blocks each way, a point every 80 along them, joined where they cross
        java.util.Map<Long, Integer> at = new java.util.HashMap<>();
        for (int z = 0; z <= 900; z += 300) {
            line(plan, at, street, 0, z, 1600, z);
        }
        for (int x = 0; x <= 1500; x += 300) {
            line(plan, at, street, x, 0, x, 900);
        }
        // a kilometre with nothing along it, and a road of no length
        plan.addSegment(plan.addNode(OX, OZ + 1100).id, plan.addNode(OX + 1000, OZ + 1100).id, street);
        plan.addSegment(plan.addNode(OX + 1450, OZ + 450).id, plan.addNode(OX + 1450, OZ + 450).id, street);
        helper.assertTrue(plan.tooBig(), "the plan should be built a part at a time");
        // buildings: one by a street at each end of the map, one 600 blocks from any road
        int[][] houses = {{OX + 20, OZ + 20}, {OX + 1570, OZ + 870}, {OX + 2200, OZ + 450}};
        MapBuildings.Batch batch = new MapBuildings.Batch();
        batch.box = new int[]{OX - 3000, OZ - 3000, OX + 5000, OZ + 5000};
        batch.scale = RoadPlan.MapImport.SCALE;
        for (int[] h : houses) {
            MapBuildings.Footprint f = new MapBuildings.Footprint();
            f.kind = "house";
            f.height = 9;
            f.floors = 2;
            f.x = new int[]{h[0] * 4, (h[0] + 10) * 4, (h[0] + 10) * 4, h[0] * 4, h[0] * 4};
            f.z = new int[]{h[1] * 4, h[1] * 4, (h[1] + 14) * 4, (h[1] + 14) * 4, h[1] * 4};
            batch.buildings.add(f);
        }
        // as the planner sends them: the buildings, then the plan, from a view in the middle of the map
        MapBuildings.receive(player.getUUID(), MapBuildings.pack(batch));
        plan.buildX = OX + 800;
        plan.buildZ = OZ + 450;
        RoadBuilder.start(player, InteractionHand.MAIN_HAND, plan);
        player.getInventory().selected = 1;                 // put the planner away while it builds
        helper.succeedWhen(() -> {
            long left = plan.segments.stream().filter(sg -> !sg.built).count();
            helper.assertTrue(left == 0, "still building: " + left + " roads to go");
            helper.assertFalse(RoadBuilder.busy(player.getUUID()), "still building");
            for (int[] p : new int[][]{{40, 0}, {1560, 0}, {40, 900}, {1560, 900}, {0, 450}, {1500, 450}, {900, 450}, {500, 1100}, {960, 1100}}) {
                helper.assertTrue(road(level, OX + p[0], OZ + p[1]), "no road built at " + p[0] + ", " + p[1]);
            }
            for (int[] h : houses) {
                helper.assertTrue(building(level, h[0] + 5, h[1] + 7), "no building at " + (h[0] - OX) + ", " + (h[1] - OZ));
            }
            RoadPlan saved = RoadPlans.read(player.getInventory().getItem(0));
            long unsaved = saved.segments.stream().filter(sg -> !sg.built).count();
            helper.assertTrue(saved.segments.size() == plan.segments.size() && unsaved == 0,
                    "the planner should have the plan all built, has " + unsaved + " of " + saved.segments.size() + " unbuilt");
        });
    }

    /** A street from (x0, z0) to (x1, z1) blocks from the map's corner, with a point every 80 blocks; points shared where streets cross. */
    private static void line(RoadPlan plan, java.util.Map<Long, Integer> at, RoadStyle style, int x0, int z0, int x1, int z1) {
        int steps = (int) Math.ceil(Math.hypot(x1 - x0, z1 - z0) / 80);
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i <= steps; i++) {
            int x = OX + x0 + (x1 - x0) * i / steps, z = OZ + z0 + (z1 - z0) * i / steps;
            ids.add(at.computeIfAbsent((long) x << 32 | (z & 0xffffffffL), k -> plan.addNode(x, z).id));
        }
        for (int i = 0; i + 1 < ids.size(); i++) {
            plan.addSegment(ids.get(i), ids.get(i + 1), style);
        }
    }

    private static boolean road(ServerLevel level, int x, int z) {
        for (int dz = -3; dz <= 3; dz++) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                    String id = ForgeRegistries.BLOCKS.getKey(level.getBlockState(new BlockPos(x + dx, y, z + dz)).getBlock()).toString();
                    if (id.contains("asphalt") || id.contains("tarmac")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean building(ServerLevel level, int x, int z) {
        for (int dz = -7; dz <= 7; dz++) {
            for (int dx = -5; dx <= 5; dx++) {
                for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                    String id = ForgeRegistries.BLOCKS.getKey(level.getBlockState(new BlockPos(x + dx, y, z + dz)).getBlock()).toString();
                    if (id.startsWith("ptmuk:") && (id.contains("brick") || id.contains("render") || id.contains("pebbledash") || id.contains("window"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
