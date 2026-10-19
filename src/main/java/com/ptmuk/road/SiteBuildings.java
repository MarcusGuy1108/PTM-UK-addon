package com.ptmuk.road;

import com.ptmuk.building.ShopSignBlockEntity;
import com.ptmuk.road.layout.Layout;
import com.ptmuk.road.layout.SiteFrame;
import com.ptmuk.road.layout.SiteLayout;
import com.ptmuk.road.layout.SiteLayout.Brand;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The buildings on sites, built block by block so the road builder can undo them: retail park
 * shop units (glazed fronts, a white fascia with the shop's sign, clad sides, a flat roof,
 * checkouts and fittings for what they sell), the canopy along them on tall masts, and drive-thru
 * restaurants (brick, a glass front, the pay and collect windows on the lane side, a counter,
 * kitchen and tables inside). Coordinates are the building's own frame: u across the front from
 * the left, v back from the front, y up from the floor block.
 */
final class SiteBuildings {
    /** Where blocks go: the builder records what was there for undo. */
    interface Out {
        void set(BlockPos pos, BlockState state);

        /** Blocks whose shape depends on their neighbours (glazing): fixed once everything is placed. */
        void join(BlockPos pos);

        /** Note what's at pos before something else changes it (a whole prefab placed in one go), for undo. */
        void remember(BlockPos pos);

        ServerLevel level();
    }

    private final Out out;
    private final SiteFrame f;
    private final int y0;
    private final RoadBuilder.Palette pal;

    private SiteBuildings(Out out, SiteFrame frame, int floor, RoadBuilder.Palette pal) {
        this.out = out;
        this.f = frame;
        this.y0 = floor - 1;
        this.pal = pal;
    }

    static void build(Out out, Layout.SiteBuilding b, RoadBuilder.Palette pal) {
        SiteBuildings s = new SiteBuildings(out, b.frame(), b.floor(), pal);
        switch (b.kind()) {
            case "unit" -> s.unit(SiteLayout.RETAIL_BRANDS.get(b.brand()));
            case "canopy" -> s.canopy(b.brand());
            case "drive_thru" -> s.driveThru(SiteLayout.DRIVE_THRU_BRANDS.get(b.brand()));
            case "prefab" -> s.prefab(SiteLayout.UNITS.get(b.brand()));
            default -> {
            }
        }
    }

    /** A prefab from the catalogue (a high street unit) on the plot, its front to the site's front. */
    private void prefab(String id) {
        ServerLevel level = out.level();
        java.util.Optional<net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate> t =
                level.getStructureManager().get(com.ptmuk.PtmUk.id("prefab/" + id));
        if (t.isEmpty()) {
            return;
        }
        // prefabs are made with their fronts to the south
        net.minecraft.world.level.block.Rotation rot = switch (f.front()) {
            case 1 -> net.minecraft.world.level.block.Rotation.CLOCKWISE_90;
            case 2 -> net.minecraft.world.level.block.Rotation.CLOCKWISE_180;
            case 3 -> net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90;
            default -> net.minecraft.world.level.block.Rotation.NONE;
        };
        var settings = new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings()
                .setRotation(rot).setIgnoreEntities(true).setKnownShape(false);
        var zero = t.get().getBoundingBox(settings, BlockPos.ZERO);
        BlockPos origin = new BlockPos(f.x0() - zero.minX(), y0 - zero.minY(), f.z0() - zero.minZ());
        var box = t.get().getBoundingBox(settings, origin);
        BlockPos.betweenClosedStream(box).forEach(p -> out.remember(p.immutable()));
        t.get().placeInWorld(level, origin, origin, settings, net.minecraft.util.RandomSource.create(), 2);
    }

    // ------------------------------------------------------------------ helpers

    private BlockPos at(int u, int y, int v) {
        return new BlockPos(f.x(u, v), y0 + y, f.z(u, v));
    }

    private Direction dir(int local) {
        return Direction.from2DDataValue(f.world(local));
    }

    private BlockState b(String id) {
        return pal.get(id, "stone");
    }

    private void set(int u, int y, int v, BlockState s) {
        out.set(at(u, y, v), s);
    }

    private void set(int u, int y, int v, String id) {
        set(u, y, v, b(id));
    }

    private void fill(int u0, int ya, int v0, int u1, int yb, int v1, BlockState s) {
        for (int u = u0; u <= u1; u++) {
            for (int y = ya; y <= yb; y++) {
                for (int v = v0; v <= v1; v++) {
                    set(u, y, v, s);
                }
            }
        }
    }

    private void fill(int u0, int ya, int v0, int u1, int yb, int v1, String id) {
        fill(u0, ya, v0, u1, yb, v1, b(id));
    }

    private void glazing(int u, int y, int v, String id) {
        set(u, y, v, id);
        out.join(at(u, y, v));
    }

    /** A home or shop fitting facing a local direction, with any extra properties. */
    private BlockState fitting(String name, int facing, String... props) {
        BlockState s = RoadBuilder.with(b("ptmuk:" + name), "facing", dir(facing).getSerializedName());
        s = RoadBuilder.with(s, "lit", false);
        for (int i = 0; i + 1 < props.length; i += 2) {
            s = RoadBuilder.with(s, props[i], props[i + 1]);
        }
        return s;
    }

    private void fit(String name, int u, int y, int v, int facing, String... props) {
        set(u, y, v, fitting(name, facing, props));
    }

    private void tall(String name, int u, int y, int v, int facing) {
        fit(name, u, y, v, facing, "half", "lower");
        fit(name, u, y + 1, v, facing, "half", "upper");
    }

    /** A two-block fitting: the right-hand part goes clockwise of the way it faces. */
    private void wide(String name, int u, int y, int v, int facing) {
        int cw = (facing + 1) & 3;
        int du = cw == SiteFrame.LEFT ? -1 : cw == SiteFrame.RIGHT ? 1 : 0;
        int dv = cw == SiteFrame.BACK ? 1 : cw == SiteFrame.FRONT ? -1 : 0;
        fit(name, u, y, v, facing, "part", "left");
        fit(name, u + du, y, v + dv, facing, "part", "right");
    }

    private void door(int u, int y, int v, int facing, boolean rightHinge) {
        BlockState d = RoadBuilder.with(b("birch_door"), "facing", dir(facing).getSerializedName());
        d = RoadBuilder.with(d, "hinge", rightHinge ? "right" : "left");
        set(u, y, v, RoadBuilder.with(d, "half", "lower"));
        set(u, y + 1, v, RoadBuilder.with(d, "half", "upper"));
    }

    /**
     * A shop sign several boards wide and high on the outside of a wall, facing `facing` (front
     * or a side); the text goes on the bottom left-hand board as seen from in front.
     */
    private void sign(int u0, int u1, int ya, int yb, int v, Brand brand, int facing, boolean alongV) {
        BlockState board = RoadBuilder.with(b("ptmuk:shop_sign"), "facing", dir(facing).getSerializedName());
        int left = facing == SiteFrame.FRONT || facing == SiteFrame.RIGHT ? u0 : u1;   // a side wall: along v
        for (int a = u0; a <= u1; a++) {
            for (int y = ya; y <= yb; y++) {
                BlockPos pos = alongV ? at(v, y, a) : at(a, y, v);
                out.set(pos, board);
                CompoundTag tag = new CompoundTag();
                boolean text = a == left && y == ya;
                tag.putString("text", text ? brand.name() : "");
                tag.putString("sub", text ? brand.sub() : "");
                tag.putInt("board", brand.board());
                tag.putInt("ink", brand.ink());
                tag.putBoolean("lit", true);
                if (out.level().getBlockEntity(pos) instanceof ShopSignBlockEntity be) {
                    be.read(tag);
                    be.setChanged();
                    out.level().sendBlockUpdated(pos, board, board, 3);
                }
            }
        }
    }

    // ------------------------------------------------------------------ retail park units

    private static final int UNIT_H = 9;

    private void unit(Brand brand) {
        int w = f.width(), d = f.depth();
        String clad = "light_gray_concrete";
        // shell
        fill(0, 1, 0, w - 1, UNIT_H, d - 1, clad);
        fill(1, 1, 1, w - 2, UNIT_H, d - 2, Blocks.AIR.defaultBlockState());
        // the glazed front, a pair of doors in the middle, the white fascia above
        int mid = w / 2;
        for (int u = 1; u <= w - 2; u++) {
            for (int y = 1; y <= 4; y++) {
                if (u == mid - 1 || u == mid + 2) {
                    set(u, y, 0, "gray_concrete");
                } else if (u == mid || u == mid + 1) {
                    if (y >= 3) {
                        glazing(u, y, 0, "ptmuk:office_glazing");
                    }
                } else {
                    glazing(u, y, 0, "ptmuk:office_glazing");
                }
            }
        }
        door(mid, 1, 0, SiteFrame.BACK, false);
        door(mid + 1, 1, 0, SiteFrame.BACK, true);
        fill(0, 5, 0, w - 1, UNIT_H, 0, "white_concrete");
        int s0 = Math.max(2, mid - 5), s1 = Math.min(w - 3, mid + 6);
        sign(s0, s1, 6, 8, -1, brand, SiteFrame.FRONT, false);
        // flat roof with a parapet, lights through it
        fill(0, UNIT_H + 1, 0, w - 1, UNIT_H + 1, d - 1, "gray_concrete");
        for (int u = 0; u < w; u++) {
            set(u, UNIT_H + 2, 0, "white_concrete");
            set(u, UNIT_H + 2, d - 1, clad);
        }
        for (int v = 1; v < d - 1; v++) {
            set(0, UNIT_H + 2, v, clad);
            set(w - 1, UNIT_H + 2, v, clad);
        }
        for (int u = 2; u < w - 2; u += 4) {
            for (int v = 3; v < d - 2; v += 4) {
                set(u, UNIT_H + 1, v, "sea_lantern");
            }
        }
        fitOut(brand.kind(), w, d);
    }

    /** Checkouts by the door, then fittings for what the shop sells. */
    private void fitOut(String kind, int w, int d) {
        int front = SiteFrame.FRONT, left = SiteFrame.LEFT, right = SiteFrame.RIGHT;
        for (int u = 2; u + 1 <= Math.min(w / 2 - 3, 12); u += 3) {
            wide("checkout", u + 1, 1, 4, front);
        }
        int v0 = 8, v1 = d - 4;
        switch (kind) {
            case "fashion", "outdoor", "sports" -> {
                for (int u = 3; u <= w - 4; u += 3) {
                    for (int v = v0; v <= v1; v += 3) {
                        fit("clothes_rail", u, 1, v, front);
                    }
                }
                for (int v = v0; v <= v1; v++) {
                    tall(kind.equals("fashion") ? "shelves_household" : "shelves_snacks", w - 2, 1, v, left);
                }
            }
            case "home", "furniture" -> {
                for (int u = 3; u + 1 <= w - 4; u += 5) {
                    for (int v = v0; v <= v1 - 2; v += 5) {
                        fit("coffee_table", u, 1, v, front);
                        fit("floor_lamp", u + 1, 1, v + 1, front);
                    }
                }
                for (int v = v0; v <= v1; v++) {
                    tall("shelves_household", 1, 1, v, right);
                    tall("shelves_household", w - 2, 1, v, left);
                }
            }
            default -> {
                String[] goods = switch (kind) {
                    case "beauty" -> new String[]{"shelves_pharmacy"};
                    case "pets" -> new String[]{"shelves_household", "shelves_snacks"};
                    case "electricals" -> new String[]{"shelves_games"};
                    case "diy" -> new String[]{"shelves_diy"};
                    default -> new String[]{"shelves_groceries", "shelves_drinks", "shelves_snacks", "shelves_household"};
                };
                int n = 0;
                for (int u = 3; u + 1 <= w - 4; u += 4) {
                    String g = goods[n++ % goods.length];
                    for (int v = v0; v <= v1; v++) {
                        tall(g, u, 1, v, left);
                        tall(g, u + 1, 1, v, right);
                    }
                }
                if (kind.equals("food")) {
                    for (int u = 2; u <= w - 3; u++) {
                        tall("chiller_cabinet", u, 1, d - 2, front);
                    }
                }
            }
        }
    }

    /** The canopy along the shop fronts: a slab roof on tall galvanised masts, lit underneath. */
    private void canopy(int units) {
        int w = f.width(), d = f.depth();
        BlockState roof = RoadBuilder.with(b("smooth_stone_slab"), "type", "top");
        for (int u = 0; u < w; u++) {
            set(u, 5, 0, "light_gray_concrete");
            for (int v = 1; v < d; v++) {
                set(u, 5, v, u % 6 == 3 && v == d / 2 ? b("sea_lantern") : roof);
            }
        }
        BlockState pole = b("ptmuk:sign_pole_galvanised");
        int n = Math.max(1, units);
        for (int i = 0; i < n; i++) {
            mast(SiteLayout.unitStart(w, n, i), pole);
        }
        mast(w - 1, pole);
    }

    private void mast(int u, BlockState pole) {
        for (int y = 0; y <= 13; y++) {
            if (y != 5) {
                set(u, y, 0, RoadBuilder.with(RoadBuilder.with(pole, "base", y == 0), "cap", y == 13));
            }
        }
    }

    // ------------------------------------------------------------------ drive-thru

    private void driveThru(Brand brand) {
        int w = f.width(), d = f.depth();
        int front = SiteFrame.FRONT, back = SiteFrame.BACK, left = SiteFrame.LEFT, right = SiteFrame.RIGHT;
        String brick = "ptmuk:red_brick";
        fill(0, 1, 0, w - 1, 5, d - 1, brick);
        fill(1, 1, 1, w - 2, 5, d - 2, Blocks.AIR.defaultBlockState());
        // a glass front with the door in the middle, a dark band and the sign over it
        int mid = w / 2;
        for (int u = 1; u <= w - 2; u++) {
            for (int y = 1; y <= 3; y++) {
                if (u == mid) {
                    continue;
                }
                if (u == mid - 1 || u == mid + 1) {
                    set(u, y, 0, brick);
                } else {
                    glazing(u, y, 0, "ptmuk:office_glazing");
                }
            }
        }
        door(mid, 1, 0, back, false);
        set(mid, 3, 0, brick);
        fill(0, 5, 0, w - 1, 5, 0, "black_concrete");
        // the side facing the way in gets a sign too
        sign(2, Math.min(d - 3, 9), 4, 4, -1, brand, left, true);
        // the pay window and the collect window on the lane side, the menu board inside
        for (int v : new int[]{d - 4, d - 3, 3, 4}) {
            for (int y = 2; y <= 3; y++) {
                glazing(w - 1, y, v, "ptmuk:office_glazing");
            }
        }
        // the counter across the middle, the kitchen behind, tables in front
        int cv = Math.max(5, d - 6);
        for (int u = 1; u <= w - 2; u++) {
            if (u != w - 3) {
                fit("kitchen_unit_white", u, 1, cv, front);
            }
        }
        for (int u = 2; u <= w - 4; u += 4) {
            fit("till", u, 2, cv, front, "level", "counter");
        }
        fit("drinks_dispenser", w - 5, 2, cv, front, "level", "counter");
        for (int u = 2; u + 1 <= w - 3; u += 5) {
            wide("menu_board", u + 1, 4, d - 2, front);
        }
        for (int u = 1; u <= Math.min(w - 6, 6); u++) {
            fit("fryer", u, 1, d - 2, front);
        }
        tall("fridge_freezer", w - 4, 1, d - 2, front);
        for (int v : new int[]{d - 4, 3}) {
            fit("till", w - 2, 1, v, right, "level", "counter");
        }
        for (int u = 2; u <= w - 4; u += 4) {
            for (int v = 2; v + 1 < cv - 1; v += 3) {
                set(u, 1, v, "spruce_fence");
                set(u, 2, v, RoadBuilder.with(b("spruce_pressure_plate"), "powered", false));
                set(u - 1, 1, v, stairs("spruce_stairs", left));
                set(u + 1, 1, v, stairs("spruce_stairs", right));
            }
        }
        // flat roof with a parapet, lights through it
        fill(0, 6, 0, w - 1, 6, d - 1, "gray_concrete");
        for (int u = 0; u < w; u++) {
            set(u, 7, d - 1, brick);
        }
        for (int v = 0; v < d; v++) {
            set(0, 7, v, brick);
            set(w - 1, 7, v, brick);
        }
        for (int u = 2; u < w - 2; u += 3) {
            for (int v = 2; v < d - 2; v += 3) {
                set(u, 6, v, "sea_lantern");
            }
        }
        // the name over the door, on the band and the roof edge (after the roof, so it isn't covered)
        sign(2, w - 3, 5, 6, -1, brand, front, false);
    }

    private BlockState stairs(String id, int facing) {
        BlockState s = RoadBuilder.with(b(id), "facing", dir(facing).getSerializedName());
        return RoadBuilder.with(RoadBuilder.with(s, "half", "bottom"), "shape", "straight");
    }
}
