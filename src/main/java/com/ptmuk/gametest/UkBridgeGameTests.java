package com.ptmuk.gametest;

import com.ptmuk.PtmUk;
import com.ptmuk.road.RoadBuilder;
import com.ptmuk.road.plan.RoadPlan;
import com.ptmuk.road.plan.RoadStyle;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * A bridge built in the world, well away from the other tests: a street that goes under and one
 * crossing it. Where they cross, the street underneath is at ground level with clear headroom
 * over it, then the concrete deck and the road over it.
 */
@GameTestHolder(PtmUk.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UkBridgeGameTests {
    private UkBridgeGameTests() {
    }

    @GameTest(template = "empty5x4x5", timeoutTicks = 1600)
    public static void buildsABridge(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "bridge-test"));
        int ox = 6000, oz = 6000;
        RoadPlan plan = new RoadPlan();
        RoadPlan.Segment below = plan.addSegment(plan.addNode(ox, oz + 100).id, plan.addNode(ox + 200, oz + 100).id, RoadStyle.residential());
        below.under = true;
        plan.addSegment(plan.addNode(ox + 100, oz).id, plan.addNode(ox + 100, oz + 200).id, RoadStyle.residential());
        RoadBuilder.start(player, InteractionHand.MAIN_HAND, plan);
        helper.succeedWhen(() -> {
            helper.assertFalse(RoadBuilder.busy(player.getUUID()), "still building");
            int x = ox + 102, z = oz + 102;                  // in a lane of each
            int top = level.getMaxBuildHeight() - 1;
            while (top > level.getMinBuildHeight() && level.getBlockState(new BlockPos(x, top, z)).isAir()) {
                top--;
            }
            // down through the road over: its surface, the deck, then headroom down to the road underneath
            int y = top;
            while (y > level.getMinBuildHeight() && !level.getBlockState(new BlockPos(x, y, z)).isAir()) {
                y--;
            }
            int deckBottom = y + 1, air = 0;
            while (y > level.getMinBuildHeight() && level.getBlockState(new BlockPos(x, y, z)).isAir()) {
                y--;
                air++;
            }
            BlockState road = level.getBlockState(new BlockPos(x, y, z));
            String id = ForgeRegistries.BLOCKS.getKey(road.getBlock()).toString();
            helper.assertTrue(air >= 4, "only " + air + " blocks of headroom under the deck (deck bottom " + deckBottom + ", road under at " + y + ")");
            helper.assertTrue(id.contains("asphalt") || id.contains("tarmac") || id.contains("marking"), "under the bridge: " + id + " at " + y);
            String deck = ForgeRegistries.BLOCKS.getKey(level.getBlockState(new BlockPos(x, deckBottom, z)).getBlock()).toString();
            helper.assertTrue(deck.equals("ptmuk:concrete_kerb"), "the deck is " + deck);
        });
    }

    /** A bus stop built in the world: PTM2's transport point in the kerb lane at it, the flag on the pavement. */
    @GameTest(template = "empty5x4x5", timeoutTicks = 1600)
    public static void buildsABusStop(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "bus-stop-test"));
        int ox = 7000, oz = 7000;
        RoadPlan plan = new RoadPlan();
        RoadPlan.Segment road = plan.addSegment(plan.addNode(ox, oz).id, plan.addNode(ox + 160, oz).id, RoadStyle.mainRoad());
        plan.addBusStop(road.id, 0.5, 1, "Test Stop");
        RoadBuilder.start(player, InteractionHand.MAIN_HAND, plan);
        helper.succeedWhen(() -> {
            helper.assertFalse(RoadBuilder.busy(player.getUUID()), "still building");
            int points = 0, flags = 0, others = 0, roadY = Integer.MIN_VALUE;
            for (int x = ox + 60; x <= ox + 100; x++) {
                for (int z = oz - 12; z <= oz + 12; z++) {
                    for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                        String id = ForgeRegistries.BLOCKS.getKey(level.getBlockState(new BlockPos(x, y, z)).getBlock()).toString();
                        points += id.equals("ptm2:transport_point") ? 1 : 0;
                        flags += id.equals("ptmuk:london_bus_stop") ? 1 : 0;
                        others += id.equals("ptm2:traffic_point") ? 1 : 0;
                        roadY = id.contains("asphalt") ? Math.max(roadY, y) : roadY;
                    }
                }
            }
            helper.assertTrue(points == 1 && flags == 1, points + " transport points, " + flags + " bus stop flags, " + others
                    + " other traffic points, road at " + roadY);
        });
    }

    /**
     * A plan too big to build in one go, two roads 2,000 blocks apart and nowhere near the player:
     * Build the whole map builds both, a part after the other, in chunks nobody has loaded.
     */
    @GameTest(template = "empty5x4x5", timeoutTicks = 2400)
    public static void buildsTheWholeMapFarAway(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "whole-map-test"));
        int ox = 20000, oz = 20000;
        RoadPlan plan = new RoadPlan();
        plan.addSegment(plan.addNode(ox, oz).id, plan.addNode(ox + 60, oz).id, RoadStyle.residential());
        plan.addSegment(plan.addNode(ox + 2000, oz).id, plan.addNode(ox + 2060, oz).id, RoadStyle.residential());
        helper.assertTrue(plan.tooBig(), "the plan should be built a part at a time");
        plan.buildX = ox;
        plan.buildZ = oz;
        RoadBuilder.start(player, InteractionHand.MAIN_HAND, plan);
        helper.succeedWhen(() -> {
            helper.assertTrue(plan.segments.stream().allMatch(sg -> sg.built), "still building the parts");
            helper.assertFalse(RoadBuilder.busy(player.getUUID()), "still building");
            for (int x : new int[]{ox + 30, ox + 2030}) {
                boolean road = false;
                java.util.Set<String> seen = new java.util.TreeSet<>();
                for (int z = oz - 3; z <= oz + 3; z++) {
                    for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight() && !road; y++) {
                        String id = ForgeRegistries.BLOCKS.getKey(level.getBlockState(new BlockPos(x, y, z)).getBlock()).toString();
                        road |= id.contains("asphalt") || id.contains("tarmac");
                        seen.add(id);
                    }
                }
                helper.assertTrue(road, "no road built at x " + x + ": " + seen);
            }
        });
    }
}
