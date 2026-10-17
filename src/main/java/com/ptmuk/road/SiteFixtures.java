package com.ptmuk.road;

import com.ptmuk.road.layout.Layout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Things stood on sites: trees, EV chargers, pay machines, parking signs, trolleys, bins,
 * bollards and benches, the painted bay symbols, and a drive-thru's order point and menu board.
 */
final class SiteFixtures {
    interface Setter {
        void set(BlockPos pos, BlockState state);
    }

    private SiteFixtures() {
    }

    static void place(ServerLevel level, Layout.Fixture f, int y0, RoadBuilder.Palette pal, Setter out) {
        Direction facing = Direction.from2DDataValue(f.rot8() / 2);
        BlockPos at = new BlockPos(f.x(), y0, f.z());
        switch (f.type()) {
            case TREE -> tree(level, at, pal, out);
            case EV_CHARGER -> out.set(at, facing(pal.get("ptmuk:ev_charger", "lightning_rod"), facing));
            case PAY_MACHINE -> out.set(at, facing(pal.get("ptmuk:pay_and_display", "lodestone"), facing));
            case BIN -> out.set(at, facing(pal.get("ptmuk:litter_bin", "composter"), facing));
            case BOLLARD -> out.set(at, facing(pal.get("ptmuk:bollard_steel", "stone_brick_wall"), facing));
            case BENCH -> out.set(at, facing(pal.get("ptmuk:bench_metal", "spruce_slab"), facing));
            case DISABLED_BAY -> out.set(at, facing(pal.get("ptmuk:disabled_bay_marking", "white_carpet"), facing));
            case PARENT_BAY -> out.set(at, facing(pal.get("ptmuk:parent_child_bay_marking", "white_carpet"), facing));
            case EV_BAY -> out.set(at, facing(pal.get("ptmuk:ev_bay_marking", "green_carpet"), facing));
            case PARKING_SIGN -> {
                BlockState pole = pal.get("ptmuk:sign_pole_galvanised", "iron_bars");
                for (int k = 0; k < 2; k++) {
                    out.set(at.above(k), RoadBuilder.with(RoadBuilder.with(pole, "base", k == 0), "cap", false));
                }
                // the sign's rotation is the way someone looking at its face looks
                BlockState sign = pal.get("ptmuk:parking_sign", "oak_sign");
                sign = RoadBuilder.with(sign, "rotation", (f.rot8() + 4) & 7);
                out.set(at.above(2), RoadBuilder.with(sign, "attachment", "center"));
            }
            case TROLLEYS -> {
                BlockState trolley = home(pal, "shopping_trolley", facing.getCounterClockWise());
                for (int k = 0; k < 3; k++) {
                    out.set(at.relative(facing, k), trolley);
                }
            }
            case ORDER_POINT -> {
                BlockState speaker = home(pal, "drive_thru_speaker", facing);
                out.set(at, RoadBuilder.with(speaker, "half", "lower"));
                out.set(at.above(), RoadBuilder.with(speaker, "half", "upper"));
            }
            case MENU_BOARD -> {
                BlockState board = home(pal, "menu_board", facing);
                BlockPos right = at.relative(facing.getClockWise());
                out.set(at, pal.get("gray_concrete", "stone"));
                out.set(right, pal.get("gray_concrete", "stone"));
                out.set(at.above(), RoadBuilder.with(board, "part", "left"));
                out.set(right.above(), RoadBuilder.with(board, "part", "right"));
            }
            case PICNIC_TABLE -> {
                out.set(at, pal.get("spruce_fence", "oak_fence"));
                out.set(at.above(), RoadBuilder.with(pal.get("spruce_pressure_plate", "oak_pressure_plate"), "powered", false));
                for (Direction side : new Direction[]{facing, facing.getOpposite()}) {
                    BlockState bench = RoadBuilder.with(pal.get("spruce_stairs", "oak_stairs"), "facing", side.getSerializedName());
                    out.set(at.relative(side), RoadBuilder.with(RoadBuilder.with(bench, "half", "bottom"), "shape", "straight"));
                }
            }
            default -> {
            }
        }
    }

    private static BlockState facing(BlockState s, Direction d) {
        return RoadBuilder.with(s, "facing", d.getSerializedName());
    }

    private static BlockState home(RoadBuilder.Palette pal, String name, Direction d) {
        return RoadBuilder.with(facing(pal.get("ptmuk:" + name, "barrel"), d), "lit", false);
    }

    /** A young street tree: a trunk and a rounded crown, oak or birch. */
    private static void tree(ServerLevel level, BlockPos at, RoadBuilder.Palette pal, Setter out) {
        int hash = Math.floorMod(at.getX() * 31 + at.getZ() * 17, 7);
        boolean birch = hash % 2 == 0;
        int h = 4 + hash % 2;
        BlockState log = RoadBuilder.with(pal.get(birch ? "birch_log" : "oak_log", "oak_log"), "axis", "y");
        BlockState leaves = pal.get(birch ? "birch_leaves" : "oak_leaves", "oak_leaves");
        leaves = RoadBuilder.with(RoadBuilder.with(leaves, "persistent", true), "distance", 1);
        for (int y = 0; y < h; y++) {
            out.set(at.above(y), log);
        }
        int r = 2;
        BlockPos top = at.above(h);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -1; dy <= r; dy++) {
                    if (dx * dx + dz * dz + dy * dy * 1.7 > r * r + 0.8) {
                        continue;
                    }
                    BlockPos p = top.offset(dx, dy, dz);
                    if (level.getBlockState(p).isAir()) {
                        out.set(p, leaves);
                    }
                }
            }
        }
    }
}
