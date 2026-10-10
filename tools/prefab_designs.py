"""British building prefabs for the Prefab Tool. Run through generate_assets.py (or directly):
writes data/ptmuk/structures/prefab/*.nbt, the catalogue thumbnails and PrefabCatalog.java."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from prefab_lib import AIR, B, Build, DIRS, light, render_thumbnail, shop_sign_nbt, sign_nbt, slab, stairs  # noqa: E402
import prefab_parts as P  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/ptmuk"
DATA = ROOT / "src/main/resources/data/ptmuk"
JAVA = ROOT / "src/main/java/com/ptmuk/building/PrefabCatalog.java"

CATEGORIES = ["Houses", "Shops", "Offices", "Flats", "Other"]


# ================================================================== Victorian terraced houses

def victorian_unit(b, o, mirror, brick, band, door, roof="ptmuk:roof_slate", bin_colour="black"):
    """One two-up two-down Victorian terraced house with a rear outrigger, between party walls
    at o and o + 6 (shared with its neighbours). The bay is on the left unless mirrored."""
    def U(lu):
        return o + (6 - lu if mirror else lu)

    def S(side):            # left/right as drawn, swapped in a mirrored house
        if not mirror:
            return side
        return {"left": "right", "right": "left"}.get(side, side)

    lo, hi = min(U(0), U(6)), max(U(0), U(6))
    # ---- ground: front garden, floors, back yard
    for lu in range(0, 7):
        u = U(lu)
        b.set(u, 0, 0, "grass_block")
        b.set(u, 0, 1, "grass_block")
        for v in range(2, 11):
            b.set(u, 0, v, "oak_planks")
        for v in range(11, 19):
            b.set(u, 0, v, "ptmuk:paving_slabs")
    for v in range(11, 15):
        for lu in (4, 5):
            b.set(U(lu), 0, v, "polished_andesite")              # kitchen floor
    P.tiled_path(b, U(5), 0, 3)
    # ---- front garden wall, gate, bin, shrub
    for lu in range(0, 7):
        u = U(lu)
        if lu == 5:
            b.set(u, 1, 0, B("dark_oak_fence_gate", facing="south", open=False, in_wall=False, powered=False))
        else:
            b.set(u, 1, 0, brick)
            b.set(u, 2, 0, B("iron_bars"))
    for lu in (0, 6):
        b.set(U(lu), 1, 1, brick)
    b.set(U(4), 1, 1, B(f"ptmuk:wheelie_bin_{bin_colour}", facing="south"))
    # ---- main house shell: front wall v2, back wall v10, party walls
    b.fill(lo, 1, 2, hi, 8, 2, brick)
    b.fill(lo, 1, 10, hi, 8, 10, brick)
    for lu in (0, 6):
        b.fill(U(lu), 1, 2, U(lu), 8, 10, brick)
    b.fill(lo, 4, 2, hi, 4, 2, band)                       # string course at first floor level
    # floors
    b.fill(min(U(1), U(5)), 4, 3, max(U(1), U(5)), 4, 9, "oak_planks")
    b.fill(min(U(1), U(5)), 8, 3, max(U(1), U(5)), 8, 9, "oak_planks")
    # ---- bay window (ground floor, left) and the front room behind it
    for lu in (1, 2, 3):
        u = U(lu)
        b.set(u, 1, 1, brick)
        b.fill(u, 2, 1, u, 3, 1, B("ptmuk:sash_window", north=False, south=False, east=False, west=False, waterlogged=False))
        b.set(u, 4, 1, stairs(roof, "back"))
        b.fill(u, 1, 2, u, 3, 2, AIR)                      # the room opens into the bay
        b.set(u, 0, 1, "oak_planks")
    # ---- porch, front door and fanlight
    b.fill(U(5), 1, 2, U(5), 3, 2, AIR)
    b.door(U(5), 1, 3, door, facing="back", hinge="left")
    b.set(U(5), 3, 3, B("ptmuk:sash_window", north=False, south=False, east=False, west=False, waterlogged=False))
    b.fill(U(4), 1, 3, U(4), 3, 9, "calcite")      # partition, front room / hall
    b.fill(U(6), 1, 3, U(6), 3, 3, brick)
    # ---- first floor front windows with sills and lintels
    P.sash(b, U(2), 5, 2)
    P.sash(b, U(5), 5, 2)
    for lu in range(0, 7):                                  # stone cornice under the eaves
        if b.get(U(lu), 7, 1).name == "minecraft:air":
            b.set(U(lu), 7, 1, slab(P.STONE, "top"))
    # ---- interior: hall and stairs
    b.fill(U(4), 1, 4, U(4), 2, 4, AIR)                     # door to the front room
    b.fill(U(4), 1, 9, U(4), 2, 9, AIR)                     # door to the back room
    b.fill(U(1), 1, 6, U(3), 3, 6, "calcite")      # front room / back room wall
    b.fill(U(3), 1, 6, U(3), 2, 6, AIR)
    for i in range(4):
        b.set(U(5), 1 + i, 5 + i, stairs("oak", "back"))
    b.fill(U(5), 4, 5, U(5), 4, 7, AIR)                     # stairwell
    b.set(U(5), 5, 4, B("oak_fence"))
    # ground floor rooms
    b.set(U(1), 1, 4, B("blast_furnace", facing=DIRS[S("right")], lit=False))   # cast-iron fireplace
    b.set(U(1), 2, 4, slab("spruce", "bottom"))
    b.fill(U(1), 3, 4, U(1), 3, 4, brick)
    P.sofa(b, U(2), U(3), 1, 5, "back", "dark_oak")
    P.rug(b, U(2), 3, U(3), 4, 1, "red")
    b.set(U(2), 1, 8, B("oak_fence"))
    b.set(U(2), 2, 8, B("oak_pressure_plate", powered=False))
    b.set(U(1), 1, 8, stairs("oak", S("left")))
    b.set(U(3), 1, 8, stairs("oak", S("right")))
    P.bookcase(b, U(1), 1, 9)
    # first floor: front bedroom, box room over the hall, back bedroom
    b.fill(U(4), 5, 3, U(4), 7, 9, "calcite")
    b.fill(U(4), 5, 4, U(4), 6, 4, AIR)                     # box room off the front bedroom
    b.fill(U(4), 5, 9, U(4), 6, 9, AIR)                     # landing to the back bedroom
    b.fill(U(1), 5, 7, U(3), 7, 7, "calcite")
    b.fill(U(3), 5, 7, U(3), 6, 7, AIR)
    for lu in (1, 2):
        P.bed(b, U(lu), 5, 6, "white", "back")
    P.wardrobe(b, U(3), 5, 6)
    P.bed(b, U(5), 5, 3, "light_blue", "front")
    P.bed(b, U(1), 5, 9, "blue", "back")
    P.wardrobe(b, U(3), 5, 9)
    # back of the main house: windows onto the side return
    P.back_window(b, U(2), 2, 10)
    P.back_window(b, U(2), 5, 10)
    # ---- rear outrigger: kitchen with the bathroom over it
    b.fill(U(3), 1, 11, U(3), 7, 15, brick)
    b.fill(U(6), 1, 11, U(6), 7, 15, brick)
    b.fill(U(3), 1, 15, U(6), 7, 15, brick)
    b.fill(U(4), 4, 11, U(5), 4, 14, "oak_planks")
    b.fill(U(5), 1, 10, U(5), 2, 10, AIR)                   # hall to kitchen
    b.fill(U(5), 5, 10, U(5), 6, 10, AIR)                   # landing to bathroom
    b.door(U(3), 1, 13, "spruce", facing=S("right"), hinge="left")
    b.side_window(U(3), 2, 11, 1, 2, "ptmuk:upvc_window")
    b.side_window(U(3), 6, 12, 1, 2, "ptmuk:leaded_window")
    b.window(U(4), 2, 15, 1, 2, "ptmuk:upvc_window")
    b.window(U(4), 6, 15, 1, 2, "ptmuk:upvc_window")
    P.kitchen_run(b, 12, 14, 1, U(5), facing=S("left"), side=True)
    P.bath(b, min(U(4), U(5)), max(U(4), U(5)), 5, 14)
    b.set(U(4), 5, 11, stairs("quartz", S("left")))
    # outrigger roof (ridge front to back) and its gable
    b.gable_roof_side(min(U(3), U(6)), max(U(3), U(6)), 12, 15, 8, roof)
    P.close_gable_front(b, 15, min(U(3), U(6)), max(U(3), U(6)), 8, brick)
    # ---- yard walls and back gate
    b.fill(U(0), 1, 11, U(0), 2, 18, brick)
    b.fill(U(6), 1, 16, U(6), 2, 18, brick)
    b.fill(lo, 1, 18, hi, 2, 18, brick)
    b.door(U(1), 1, 18, "spruce", facing="front", hinge="left")
    b.set(U(2), 1, 17, B("ptmuk:wheelie_bin_green", facing="south"))
    # ---- lights
    for (lu, y, v) in ((2, 3, 4), (2, 3, 8), (5, 3, 4), (2, 7, 4), (2, 7, 8), (5, 7, 3), (4, 3, 12), (4, 7, 12), (5, 7, 9)):
        if b.get(U(lu), y, v).name == "minecraft:air":
            b.set(U(lu), y, v, light(12))


def victorian_terrace(ident, name, n, brick="ptmuk:london_stock_brick", band="ptmuk:red_brick", doors=None,
                      roof="ptmuk:roof_slate"):
    doors = doors or ["dark_oak", "crimson", "warped", "mangrove"]
    W = 6 * n + 1
    b = Build(ident, name, "Houses", W, 15, 19,
              f"{n} Victorian terraced house{'s' if n > 1 else ''}: bay window, slate roof, rear outrigger and yard.")
    for i in range(n):
        victorian_unit(b, 6 * i, i % 2 == 1, brick, band, doors[i % len(doors)], roof,
                       bin_colour=["black", "grey", "blue", "brown"][i % 4])
    # main roof over the whole row, then the party walls up to it and a chimney on each
    b.gable_roof(0, W - 1, 1, 11, 8, roof)
    for i in range(n + 1):
        P.close_gable(b, 6 * i, 2, 10, 8, brick)
        b.chimney(6 * i, 6, 9, 13, brick, w=1, d=2, pots=2)
    return b


# ================================================================== semi-detached pairs (Edwardian, 1930s)

def semi_house(b, o, mirror, style, door, y_top=8):
    """One half of a semi-detached pair, walls at o and o + 8 (the party wall is shared).
    Edwardian: red brick, two-storey bay under a mock-Tudor gable, clay tiles, tiled porch canopy.
    1930s: red brick below and pebbledash above, two-storey bay under a little hipped roof, leaded
    lights and an arched porch."""
    def U(lu):
        return o + (8 - lu if mirror else lu)

    def S(side):
        return {"left": "right", "right": "left"}.get(side, side) if mirror else side
    brick = "ptmuk:red_brick"
    upper = "ptmuk:pebbledash" if style == "thirties" else brick
    lo, hi = min(U(0), U(8)), max(U(0), U(8))
    win_top = "ptmuk:leaded_window"
    win = "ptmuk:upvc_window" if style == "thirties" else "ptmuk:sash_window"
    # ground: front garden, house floor, back garden
    for lu in range(0, 9):
        u = U(lu)
        b.set(u, 0, 0, "grass_block")
        for v in (1, 2):
            b.set(u, 0, v, "grass_block")
        for v in range(3, 13):
            b.set(u, 0, v, "oak_planks")
        for v in range(13, 21):
            b.set(u, 0, v, "grass_block")
    P.tiled_path(b, U(6), 0, 3) if style != "thirties" else b.fill(U(6), 0, 0, U(6), 0, 3, "ptmuk:block_paving_red")
    for lu in range(0, 9):
        u = U(lu)
        if lu == 6:
            b.set(u, 1, 0, B("oak_fence_gate", facing="south", open=False, in_wall=False, powered=False))
        else:
            b.set(u, 1, 0, brick)
    P.hedge(b, min(U(1), U(4)), max(U(1), U(4)), 1)
    b.set(U(7), 1, 2, B("ptmuk:wheelie_bin_black", facing="south"))
    # shell
    b.fill(lo, 1, 3, hi, 3, 3, brick)
    b.fill(lo, 4, 3, hi, y_top, 3, upper)
    b.fill(lo, 1, 12, hi, 3, 12, brick)
    b.fill(lo, 4, 12, hi, y_top, 12, upper)
    for lu in (0, 8):
        b.fill(U(lu), 1, 3, U(lu), 3, 12, brick)
        b.fill(U(lu), 4, 3, U(lu), y_top, 12, upper)
    a, z = min(U(1), U(7)), max(U(1), U(7))
    b.fill(a, 4, 4, z, 4, 11, "oak_planks")
    b.fill(a, 8, 4, z, 8, 11, "oak_planks")
    # two-storey bay on the left
    for lu in (1, 2, 3):
        u = U(lu)
        b.set(u, 0, 2, "oak_planks")
        b.set(u, 1, 2, brick)
        b.set(u, 2, 2, P.pane(win))
        b.set(u, 3, 2, P.pane(win_top))
        b.set(u, 4, 2, upper if style == "thirties" else "ptmuk:portland_stone")
        b.set(u, 5, 2, P.pane(win))
        b.set(u, 6, 2, P.pane(win_top))
        b.set(u, 7, 2, upper)
        b.fill(u, 1, 3, u, 3, 3, AIR)
        b.fill(u, 5, 3, u, 7, 3, AIR)
    # porch and door
    if style == "thirties":
        b.fill(U(6), 1, 3, U(6), 3, 3, AIR)
        b.set(U(5), 3, 3, stairs(brick, S("left"), "top"))
        b.set(U(7), 3, 3, stairs(brick, S("right"), "top"))
        b.door(U(6), 1, 4, door, facing="back", hinge="left")
        b.set(U(6), 3, 4, P.pane("ptmuk:leaded_window"))
        P.sash(b, U(6), 5, 3, kind="ptmuk:leaded_window", lintel=None)
    else:
        b.door(U(6), 1, 3, door, facing="back", hinge="left")
        b.set(U(6), 3, 3, P.pane("ptmuk:leaded_window"))
        for lu in (5, 6, 7):
            b.set(U(lu), 4, 2, stairs("ptmuk:roof_clay_tiles", "back"))
        P.sash(b, U(6), 5, 3, kind="ptmuk:sash_window")
    # inside: partition, hall stairs, rooms
    b.fill(U(5), 1, 4, U(5), 3, 11, "calcite")
    b.fill(U(5), 1, 5, U(5), 2, 5, AIR)
    b.fill(U(5), 1, 10, U(5), 2, 10, AIR)
    b.fill(min(U(1), U(4)), 1, 8, max(U(1), U(4)), 3, 8, "calcite")
    b.fill(U(2), 1, 8, U(2), 2, 8, AIR)
    for i in range(4):
        b.set(U(7), 1 + i, 5 + i, stairs("oak", "back"))
    b.fill(U(7), 4, 5, U(7), 4, 7, AIR)
    P.sofa(b, U(2), U(3), 1, 7, "back", "dark_oak")
    P.rug(b, U(2), 4, U(3), 5, 1, "green" if style == "thirties" else "red")
    b.set(U(1), 1, 5, B("blast_furnace", facing=DIRS[S("right")], lit=False))
    b.set(U(2), 1, 10, B("oak_fence"))
    b.set(U(2), 2, 10, B("oak_pressure_plate", powered=False))
    P.kitchen_run(b, min(U(1), U(4)), max(U(1), U(4)), 1, 11, facing="front")
    b.set(U(3), 1, 10, stairs("oak", S("right")))
    # first floor
    b.fill(U(5), 5, 4, U(5), 7, 11, "calcite")
    b.fill(U(5), 5, 9, U(5), 6, 9, AIR)
    b.fill(U(5), 5, 6, U(5), 6, 6, AIR)
    b.fill(min(U(1), U(4)), 5, 8, max(U(1), U(4)), 7, 8, "calcite")
    b.fill(U(4), 5, 8, U(4), 6, 8, AIR)
    for lu in (2, 3):
        P.bed(b, U(lu), 5, 7, "white", "back")
    P.wardrobe(b, U(1), 5, 7)
    P.bed(b, U(2), 5, 11, "light_blue", "back")
    P.wardrobe(b, U(4), 5, 11)
    P.bath(b, min(U(6), U(7)), max(U(6), U(7)), 5, 11)
    # back windows and back door
    P.back_window(b, U(2), 2, 12, kind=win)
    P.back_window(b, U(2), 5, 12, kind=win)
    P.back_window(b, U(6), 6, 12, h=1, kind="ptmuk:leaded_window")
    b.door(U(6), 1, 12, "spruce", facing="front", hinge="left")
    # back garden: patio, lawn, fences, shed
    b.fill(lo, 0, 13, hi, 0, 14, "ptmuk:paving_slabs")
    b.fill(U(0), 1, 13, U(0), 1, 20, B("spruce_fence"))
    b.fill(lo, 1, 20, hi, 1, 20, B("spruce_fence"))
    b.fill(min(U(1), U(3)), 1, 18, max(U(1), U(3)), 2, 19, "spruce_planks")
    b.fill(min(U(1), U(3)), 3, 18, max(U(1), U(3)), 3, 19, slab("dark_oak"))
    # lights
    for (lu, y, v) in ((3, 3, 5), (3, 3, 10), (6, 3, 4), (3, 7, 5), (3, 7, 10), (6, 7, 10), (7, 7, 4)):
        if b.get(U(lu), y, v).name == "minecraft:air":
            b.set(U(lu), y, v, light(12))


def semi_pair(ident, name, style):
    side = 3 if style == "thirties" else 2
    W = 17 + 2 * side
    b = Build(ident, name, "Houses", W, 18, 21,
              ("A pair of 1930s semis: pebbledash over red brick, two-storey bays, leaded lights, clay tile roof and drives."
               if style == "thirties" else
               "A pair of Edwardian semis: red brick, two-storey bays under mock-Tudor gables, tiled porches, clay tile roof."))
    o = side
    semi_house(b, o, False, style, "crimson" if style != "thirties" else "oak")
    semi_house(b, o + 8, True, style, "warped" if style != "thirties" else "dark_oak")
    # side paths / drives to the back
    for u0 in (0, W - side):
        b.fill(u0, 0, 0, u0 + side - 1, 0, 20, "ptmuk:block_paving_grey" if style == "thirties" else "ptmuk:paving_slabs")
    roof = "ptmuk:roof_clay_tiles"
    b.hipped_roof(o - 1, o + 16 + 1, 2, 13, 8, roof)
    for (mid, mir) in ((o, False), (o + 16, True)):
        # the bay's own roof, joining the main roof
        bu0, bu1 = (mid, mid + 4) if not mir else (mid - 4, mid)
        if style == "thirties":
            b.hipped_roof(bu0, bu1, 1, 4, 8, roof)
        else:
            b.gable_roof_side(bu0, bu1, 1, 4, 8, roof)
            P.close_gable_front(b, 2, bu0 + 1, bu1 - 1, 8, "calcite")
            for u in range(bu0 + 1, bu1):
                for y in range(8, 11):
                    if b.get(u, y, 2).name == "minecraft:white_terracotta" and (u - bu0) % 2 == 0:
                        b.set(u, y, 2, B("stripped_dark_oak_log", axis="y"))
            b.fill(bu0 + 1, 7, 2, bu1 - 1, 7, 2, B("stripped_dark_oak_log", axis="x"))
    b.chimney(o + 8, 7, 9, 15, "ptmuk:red_brick", w=1, d=2, pots=2)
    b.chimney(o, 9, 9, 14, "ptmuk:red_brick", w=1, d=1, pots=1)
    b.chimney(o + 16, 9, 9, 14, "ptmuk:red_brick", w=1, d=1, pots=1)
    return b


# ================================================================== post-war council terrace

def council_terrace(ident="council_terrace", name="Post-war Council Houses (Terrace of 4)", n=4):
    W = 6 * n + 1
    b = Build(ident, name, "Houses", W, 15, 20,
              "Four plain post-war council houses: red brick, wide windows, concrete door canopies, concrete tile roof, hedged gardens.")
    brick, roof = "ptmuk:red_brick", "ptmuk:roof_concrete_tiles"
    doors = ["oak", "spruce", "birch", "dark_oak"]
    for i in range(n):
        o, mirror = 6 * i, i % 2 == 1

        def U(lu, o=o, mirror=mirror):
            return o + (6 - lu if mirror else lu)
        lo, hi = min(U(0), U(6)), max(U(0), U(6))
        P.lawn(b, lo, 0, hi, 3)
        b.fill(U(5), 0, 0, U(5), 0, 3, "ptmuk:paving_slabs")
        P.hedge(b, min(U(0), U(4)), max(U(0), U(4)), 0)
        b.set(U(6), 1, 0, B("oak_leaves", persistent=True, distance=1, waterlogged=False)) if i == n - 1 and not mirror else None
        b.fill(lo, 0, 4, hi, 0, 11, "oak_planks")
        P.lawn(b, lo, 12, hi, 19)
        b.fill(lo, 0, 12, hi, 0, 13, "ptmuk:paving_slabs")
        b.fill(lo, 1, 4, hi, 7, 4, brick)
        b.fill(lo, 1, 11, hi, 7, 11, brick)
        for lu in (0, 6):
            b.fill(U(lu), 1, 4, U(lu), 7, 11, brick)
        b.fill(min(U(1), U(5)), 4, 5, max(U(1), U(5)), 4, 10, "oak_planks")
        b.window(min(U(1), U(3)), 2, 4, 3, 2, "ptmuk:upvc_window")
        b.door(U(5), 1, 4, doors[i % len(doors)], facing="back", hinge="left")
        for lu in (4, 5):
            b.set(U(lu), 3, 3, slab("smooth_stone", "top"))
        b.window(min(U(1), U(2)), 5, 4, 2, 2, "ptmuk:upvc_window")
        b.window(U(4), 5, 4, 1, 2, "ptmuk:upvc_window")
        b.window(U(5), 6, 4, 1, 1, "ptmuk:upvc_window")
        # inside
        b.fill(U(4), 1, 5, U(4), 3, 10, "calcite")
        b.fill(U(4), 1, 6, U(4), 2, 6, AIR)
        b.fill(U(4), 1, 9, U(4), 2, 9, AIR)
        for k in range(4):
            b.set(U(5), 1 + k, 6 + k, stairs("oak", "back"))
        b.fill(U(5), 4, 6, U(5), 4, 8, AIR)
        P.sofa(b, U(2), U(3), 1, 7, "back", "spruce")
        P.kitchen_run(b, min(U(1), U(3)), max(U(1), U(3)), 1, 10, facing="front")
        b.fill(U(4), 5, 5, U(4), 7, 10, "calcite")
        b.fill(U(4), 5, 10, U(4), 6, 10, AIR)
        P.bed(b, U(2), 5, 6, "red", "front")
        P.bed(b, U(2), 5, 10, "lime", "back")
        b.window(U(2), 2, 11, 1, 2, "ptmuk:upvc_window")
        b.window(U(2), 5, 11, 1, 2, "ptmuk:upvc_window")
        b.door(U(5), 1, 11, "spruce", facing="front", hinge="left")
        b.fill(U(0), 1, 12, U(0), 1, 19, B("spruce_fence"))
        b.fill(lo, 1, 19, hi, 1, 19, B("spruce_fence"))
        b.set(U(3), 1, 12, B("ptmuk:wheelie_bin_" + ["green", "black", "blue", "grey"][i % 4], facing="south"))
        for (lu, y, v) in ((2, 3, 6), (2, 3, 9), (2, 7, 7), (5, 7, 10)):
            if b.get(U(lu), y, v).name == "minecraft:air":
                b.set(U(lu), y, v, light(12))
    b.fill(W - 1, 1, 12, W - 1, 1, 19, B("spruce_fence"))
    b.gable_roof(0, W - 1, 3, 12, 8, roof)
    for i in range(n + 1):
        P.close_gable(b, 6 * i, 4, 11, 8, brick)
        if i % 2 == 0:
            b.chimney(6 * i, 7, 9, 13, brick, w=1, d=1, pots=1)
    return b


# ================================================================== modern detached new-build

def newbuild_detached(ident="newbuild_detached", name="New-Build Detached House"):
    W, D = 16, 21
    b = Build(ident, name, "Houses", W, 15, D,
              "A 2020s new-build: red brick with a rendered front gable, grey windows, a canopy porch, garage and block-paved drive.")
    brick, roof = "ptmuk:red_brick", "ptmuk:roof_concrete_tiles"
    P.lawn(b, 0, 0, W - 1, D - 1)
    b.fill(10, 0, 0, 14, 0, 4, "ptmuk:block_paving_grey")
    b.fill(6, 0, 0, 6, 0, 4, "ptmuk:block_paving_grey")
    # main house: u1..u9 x v5..v13; front gable wing u1..u5 forward to v4
    b.fill(1, 0, 4, 9, 0, 13, "oak_planks")
    b.walls(1, 1, 5, 9, 7, 13, brick)
    b.walls(1, 1, 4, 5, 7, 5, brick)
    b.fill(2, 4, 5, 8, 4, 12, "oak_planks")
    b.fill(2, 4, 5, 4, 4, 5, "oak_planks")
    # rendered panel on the gable wing's first floor
    b.fill(1, 4, 4, 5, 7, 4, "ptmuk:white_render")
    b.window(2, 2, 4, 3, 2, "ptmuk:grey_framed_window")
    b.window(2, 5, 4, 3, 2, "ptmuk:grey_framed_window")
    # front door and canopy, landing window over
    b.door(7, 1, 5, "dark_oak", facing="back", hinge="left")
    b.set(7, 3, 5, P.pane("ptmuk:grey_framed_window"))
    for u in (6, 7, 8):
        b.set(u, 4, 4, stairs(roof, "back"))
    b.window(7, 5, 5, 2, 2, "ptmuk:grey_framed_window")
    # garage: u10..u14, single storey
    b.fill(10, 0, 5, 14, 0, 13, "smooth_stone")
    b.walls(10, 1, 5, 14, 3, 13, brick)
    for u in (11, 12, 13):
        for y in (1, 2):
            b.set(u, y, 5, B("iron_trapdoor", facing="north", half="bottom", open=True, powered=False, waterlogged=False))
    b.gable_roof_side(10, 14, 4, 13, 4, roof)
    P.close_gable_front(b, 5, 11, 13, 4, brick)
    P.close_gable_front(b, 13, 11, 13, 4, brick)
    # roofs: main ridge across, the wing's gable facing the street
    b.gable_roof(0, 10, 4, 14, 8, roof)
    P.close_gable(b, 1, 5, 13, 8, brick)
    P.close_gable(b, 9, 5, 13, 8, brick)
    b.gable_roof_side(0, 6, 3, 8, 8, roof)
    P.close_gable_front(b, 4, 1, 5, 8, "ptmuk:white_render")
    # back: french doors and windows
    b.window(3, 1, 13, 2, 2, "ptmuk:grey_framed_window")
    b.window(3, 5, 13, 2, 2, "ptmuk:grey_framed_window")
    b.window(7, 5, 13, 1, 2, "ptmuk:grey_framed_window")
    b.fill(1, 0, 14, 9, 0, 15, "ptmuk:paving_slabs")
    b.fill(0, 1, D - 1, W - 1, 1, D - 1, B("spruce_fence"))
    b.fill(0, 1, 14, 0, 1, D - 1, B("spruce_fence"))
    b.fill(W - 1, 1, 14, W - 1, 1, D - 1, B("spruce_fence"))
    # inside
    b.fill(5, 1, 6, 5, 3, 12, "calcite")
    b.fill(5, 1, 7, 5, 2, 7, AIR)
    b.fill(5, 1, 11, 5, 2, 11, AIR)
    for k in range(4):
        b.set(8, 1 + k, 7 + k, stairs("oak", "back"))
    b.fill(8, 4, 7, 8, 4, 9, AIR)
    P.sofa(b, 2, 3, 1, 8, "back", "spruce")
    P.kitchen_run(b, 2, 4, 1, 12, facing="front")
    b.fill(5, 5, 6, 5, 7, 12, "calcite")
    b.fill(5, 5, 11, 5, 6, 11, AIR)
    b.fill(5, 5, 6, 5, 6, 6, AIR)
    P.bed(b, 3, 5, 8, "light_gray", "back")
    P.bed(b, 3, 5, 12, "blue", "back")
    b.set(9, 1, 3, B("ptmuk:wheelie_bin_grey", facing="south"))
    for (u, y, v) in ((3, 3, 7), (3, 3, 11), (7, 3, 6), (3, 7, 7), (3, 7, 11), (7, 7, 10), (12, 3, 9)):
        if b.get(u, y, v).name == "minecraft:air":
            b.set(u, y, v, light(12))
    return b


# ================================================================== bungalow

def bungalow(ident="bungalow", name="1960s Bungalow"):
    W, D = 16, 18
    b = Build(ident, name, "Houses", W, 10, D,
              "A 1960s bungalow: brick under a hipped concrete tile roof, big picture window, side drive and a neat lawn.")
    brick, roof = "ptmuk:red_brick", "ptmuk:roof_concrete_tiles"
    P.lawn(b, 0, 0, W - 1, D - 1)
    b.fill(12, 0, 0, 14, 0, D - 4, "ptmuk:block_paving_grey")
    b.fill(1, 0, 3, 11, 0, 11, "oak_planks")
    b.walls(1, 1, 3, 11, 3, 11, brick)
    b.window(2, 1, 3, 4, 3, "ptmuk:upvc_window")
    b.fill(2, 1, 3, 5, 1, 3, brick)
    b.window(2, 2, 3, 4, 2, "ptmuk:upvc_window")
    b.door(8, 1, 3, "birch", facing="back", hinge="left")
    b.set(9, 1, 3, P.pane("ptmuk:leaded_window"))
    b.set(9, 2, 3, P.pane("ptmuk:leaded_window"))
    b.set(8, 3, 3, P.pane("ptmuk:upvc_window"))
    b.window(3, 2, 11, 2, 1, "ptmuk:upvc_window")
    b.window(8, 2, 11, 2, 1, "ptmuk:upvc_window")
    b.side_window(1, 2, 6, 2, 1, "ptmuk:upvc_window")
    b.door(11, 1, 9, "spruce", facing="left", hinge="left")
    b.hipped_roof(0, 12, 2, 12, 4, roof)
    b.chimney(4, 8, 4, 8, brick, w=1, d=1, pots=1)
    P.hedge(b, 0, 11, 0)
    b.fill(7, 0, 0, 8, 0, 2, "ptmuk:paving_slabs")
    b.fill(7, 1, 0, 8, 1, 0, AIR)
    b.fill(0, 1, D - 1, W - 1, 1, D - 1, B("oak_fence"))
    # inside: lounge, kitchen, two bedrooms
    b.fill(6, 1, 4, 6, 3, 10, "calcite")
    b.fill(6, 1, 5, 6, 2, 5, AIR)
    b.fill(2, 1, 7, 5, 3, 7, "calcite")
    b.fill(4, 1, 7, 4, 2, 7, AIR)
    b.fill(7, 1, 7, 10, 3, 7, "calcite")
    b.fill(9, 1, 7, 9, 2, 7, AIR)
    P.sofa(b, 2, 4, 1, 6, "back", "spruce")
    P.bed(b, 3, 1, 10, "pink", "back")
    P.kitchen_run(b, 7, 10, 1, 10, facing="front")
    for (u, y, v) in ((3, 3, 5), (3, 3, 9), (8, 3, 5), (8, 3, 9)):
        if b.get(u, y, v).name == "minecraft:air":
            b.set(u, y, v, light(12))
    return b


# ================================================================== Georgian townhouse

def georgian_townhouse(ident="georgian_townhouse", name="Georgian Townhouse"):
    W, D = 7, 18
    b = Build(ident, name, "Houses", W, 24, D,
              "A London Georgian townhouse: stucco ground floor, stock brick above, tall sash windows, iron balconettes, railings and a parapet.")
    brick, stucco, stone = "ptmuk:london_stock_brick", "calcite", P.STONE
    b.fill(0, 0, 0, W - 1, 0, 2, "ptmuk:paving_slabs")
    for u in range(W):
        if u != 5:
            b.fill(u, 1, 0, u, 2, 0, B("iron_bars"))
    b.fill(0, 0, 3, W - 1, 0, 13, "oak_planks")
    floors = [0, 5, 10, 14, 18]
    b.walls(0, 1, 3, W - 1, 18, 13, brick)
    b.fill(0, 1, 3, W - 1, 4, 3, stucco)
    b.fill(0, 5, 3, W - 1, 5, 3, stone)
    for y in floors[1:-1]:
        b.fill(1, y, 4, W - 2, y, 12, "oak_planks")
    # ground floor: two windows and the door with a fanlight and a column each side
    for u in (1, 3):
        b.window(u, 2, 3, 1, 2, "ptmuk:georgian_window")
    b.door(5, 1, 3, "dark_oak", facing="back", hinge="left")
    b.set(5, 3, 3, P.pane("ptmuk:georgian_window"))
    b.set(4, 1, 2, B("quartz_pillar", axis="y"))
    b.set(4, 2, 2, B("quartz_pillar", axis="y"))
    b.fill(4, 3, 2, 6, 3, 2, slab("smooth_quartz", "bottom"))
    b.set(5, 0, 2, "smooth_quartz")
    # first floor (piano nobile): tall windows with an iron balconette
    for u in (1, 3, 5):
        b.window(u, 6, 3, 1, 3, "ptmuk:georgian_window")
        b.set(u, 9, 3, stone)
    b.fill(1, 5, 2, 5, 5, 2, slab(stone, "top"))
    b.fill(1, 6, 2, 5, 6, 2, B("iron_bars"))
    # second and third floors
    for u in (1, 3, 5):
        b.window(u, 11, 3, 1, 2, "ptmuk:georgian_window")
        b.window(u, 15, 3, 1, 2, "ptmuk:georgian_window")
        b.set(u, 10, 2, slab(stone, "top"))
    # cornice and parapet
    b.fill(0, 18, 2, W - 1, 18, 2, slab(stone, "top"))
    b.fill(0, 19, 3, W - 1, 19, 3, brick)
    b.fill(0, 20, 3, W - 1, 20, 3, slab(stone, "bottom"))
    b.gable_roof(0, W - 1, 4, 12, 18, "ptmuk:roof_slate")
    P.close_gable(b, 0, 4, 12, 18, brick)
    P.close_gable(b, W - 1, 4, 12, 18, brick)
    b.chimney(0, 7, 19, 22, brick, w=1, d=2, pots=2)
    b.chimney(W - 1, 7, 19, 22, brick, w=1, d=2, pots=2)
    # back: windows, small garden
    for y in (2, 6, 11, 15):
        b.window(2, y, 13, 1, 2, "ptmuk:sash_window")
        b.window(4, y, 13, 1, 2, "ptmuk:sash_window")
    P.lawn(b, 0, 14, W - 1, D - 1)
    b.fill(0, 1, 14, 0, 2, D - 1, brick)
    b.fill(W - 1, 1, 14, W - 1, 2, D - 1, brick)
    b.fill(0, 1, D - 1, W - 1, 2, D - 1, brick)
    # inside: stairs up the back corner, rooms
    P.stair_core(b, 4, 6, floors[:-1], material="oak")
    for i, y in enumerate(floors[:-1]):
        P.rug(b, 1, 4, 2, 6, y + 1, ["red", "blue", "green", "purple"][i])
        b.set(1, y + 3, 5, light(12)) if b.get(1, y + 3, 5).name == "minecraft:air" else None
        b.set(2, y + 3, 10, light(12)) if b.get(2, y + 3, 10).name == "minecraft:air" else None
    P.bookcase(b, 1, 6, 12, 3)
    P.sofa(b, 1, 2, 6, 7, "back", "dark_oak")
    P.bed(b, 2, 11, 12, "red", "back")
    P.bed(b, 2, 15, 12, "white", "back")
    P.kitchen_run(b, 1, 3, 1, 12, facing="front")
    return b


# ================================================================== shops

from prefab_lib import Block  # noqa: E402

RED_BOARD, NAVY, BLACK, GREEN_BOARD, CREAM = 0xFFB3202A, 0xFF1C2E4A, 0xFF121214, 0xFF1E4D2B, 0xFFF0E6C8
WHITE_INK, GOLD_INK, YELLOW_INK = 0xFFFFFFFF, 0xFFD9B44A, 0xFFF2C21B


def side_signs(b, u, v0, v1, y, text, sub, board, ink, facing="right"):
    """A fascia along a side wall (facing left or right). The left-hand board as seen from that
    side holds the name: the front-most one for a right-facing side."""
    anchor = v0 if facing == "right" else v1
    for v in range(v0, v1 + 1):
        nbt = shop_sign_nbt(text, sub, board, ink) if v == anchor else shop_sign_nbt("", "", board, ink)
        b.set(u, y, v, Block("ptmuk:shop_sign", {"facing": DIRS[facing]}, nbt))


def hanging_sign(b, u, y, v, lines, facing="right", wood="dark_oak"):
    b.set(u, y, v, Block(f"{wood}_wall_hanging_sign", {"facing": DIRS[facing], "waterlogged": "false"},
                         sign_nbt(lines, "white", True, "minecraft:hanging_sign")))


def shop_interior(b, kind, u0, u1, v0, v1, y=1):
    """Fit-out seen through a shop window."""
    if kind == "grocer":
        P.shop_shelves(b, u0, u0, v0 + 1, y, 2)
        for v in range(v0 + 1, v1):
            b.set(u0, y, v, "bookshelf")
            b.set(u0, y + 1, v, "bookshelf")
        P.fridge_row(b, u0 + 1, u1 - 2, v1)
        b.fill((u0 + u1) // 2, y, v0 + 2, (u0 + u1) // 2, y, v1 - 2, "bookshelf")
        P.counter(b, u1 - 1, u1, v1 - 2, y)
    elif kind == "bakery":
        P.counter(b, u0 + 1, u1 - 1, v0 + 2, y, top="birch_slab")
        for u in range(u0 + 1, u1, 2):
            b.set(u, y + 2, v0 + 2, B("cake", bites=0))
        b.fill(u0, y, v1, u1, y + 1, v1, B("barrel", facing="south", open=False))
        b.fill(u0, y + 2, v1, u1, y + 2, v1, "hay_block")
    elif kind == "charity":
        P.shop_shelves(b, u0, u0, v0 + 1, y, 2)
        for v in range(v0 + 1, v1 + 1):
            b.set(u0, y, v, "bookshelf")
            b.set(u1, y, v, B("chest", facing="west", type="single", waterlogged=False))
        P.counter(b, u1 - 1, u1, v1 - 1, y)
        P.rug(b, u0 + 1, v0 + 1, u1 - 1, v1 - 1, y, "purple")
    elif kind == "barber":
        for u in range(u0 + 1, u1, 2):
            b.set(u, y, v1 - 1, stairs("red_nether_brick", "back"))
            b.fill(u, y + 1, v1, u, y + 2, v1, P.pane("glass_pane"))
        b.fill(u0, y, v0 + 1, u0, y, v0 + 2, stairs("dark_oak", "left"))
        P.rug(b, u0 + 1, v0 + 1, u1, v1 - 2, y, "white")
    elif kind == "cafe":
        for u in range(u0 + 1, u1, 2):
            b.set(u, y, v0 + 2, B("spruce_fence"))
            b.set(u, y + 1, v0 + 2, B("spruce_pressure_plate", powered=False))
            b.set(u, y, v0 + 3, stairs("spruce", "back"))
        P.counter(b, u0, u1, v1 - 1, y, top="smooth_quartz_slab")
        b.set(u0 + 1, y + 2, v1 - 1, B("cake", bites=1))
        b.set(u1 - 1, y + 2, v1 - 1, B("brewing_stand", has_bottle_0=False, has_bottle_1=False, has_bottle_2=False))
    elif kind == "takeaway":
        P.counter(b, u0, u1, v0 + 2, y, top="smooth_quartz_slab")
        b.fill(u0, y, v1, u1, y, v1, B("smoker", facing="north", lit=False))
        b.fill(u0, y + 1, v1, u1, y + 1, v1, "iron_block")
    elif kind == "convenience":
        for u in range(u0 + 2, u1 - 2, 3):
            b.fill(u, y, v0 + 2, u, y + 1, v1 - 3, "bookshelf")
        P.fridge_row(b, u0, u1 - 3, v1)
        P.counter(b, u1 - 2, u1, v0 + 2, y)


def corner_shop(ident="corner_shop", name="Corner Shop with Flat Above"):
    W, D = 10, 14
    b = Build(ident, name, "Shops", W, 14, D,
              "A Victorian corner shop: shop front and fascia round the corner, flat above with its own door, slate roof.")
    brick = "ptmuk:red_brick"
    P.paving(b, 0, 0, W - 1, D - 1)
    b.fill(0, 0, 1, 8, 0, 12, "oak_planks")
    b.walls(0, 1, 1, 8, 8, 12, brick)
    b.fill(1, 4, 2, 7, 4, 11, "oak_planks")
    # front shop front and the side window, fascia round the corner
    P.shopfront(b, 1, 7, 1, 1, "CORNER STORES", "Newsagent • Off Licence • Groceries", RED_BOARD, WHITE_INK,
                door_u=7, stall="ptmuk:burgundy_faience")
    b.set(8, 1, 1, "ptmuk:portland_stone")
    for v in (2, 3, 4):
        b.set(8, 1, v, "ptmuk:burgundy_faience")
        b.fill(8, 2, v, 8, 3, v, P.pane("ptmuk:shop_window"))
        b.set(8, 4, v, "black_concrete")
    side_signs(b, 9, 2, 4, 4, "CORNER STORES", "", RED_BOARD, WHITE_INK, "right")
    b.fill(0, 1, 1, 0, 4, 1, "ptmuk:portland_stone")
    # the flat: its own door on the side street, windows above
    b.door(8, 1, 9, "dark_oak", facing="left", hinge="left")
    b.set(8, 3, 9, P.pane("ptmuk:sash_window"))
    for u in (2, 5):
        P.sash(b, u, 6, 1)
    for v in (4, 8):
        b.side_window(8, 6, v, 1, 2, "ptmuk:sash_window")
        b.set(9, 5, v, slab(P.STONE, "top"))
    b.fill(0, 5, 1, 8, 5, 1, P.STONE)
    b.fill(1, 1, 7, 7, 3, 7, "calcite")
    b.fill(3, 1, 7, 3, 2, 7, AIR)
    b.hipped_roof(-0 + 0, 8, 1, 12, 9, "ptmuk:roof_slate")
    b.chimney(1, 10, 10, 12, brick, w=1, d=1, pots=1)
    shop_interior(b, "grocer", 1, 7, 2, 6)
    for k in range(4):
        b.set(7, 1 + k, 8 + k, stairs("oak", "back"))
    b.fill(7, 4, 8, 7, 4, 10, AIR)
    P.bed(b, 2, 5, 11, "orange", "back")
    P.sofa(b, 3, 4, 5, 4, "back", "spruce")
    P.ceiling_lights(b, 1, 2, 7, 6, 4, step=3)
    for (u, y, v) in ((3, 7, 3), (3, 7, 9), (5, 3, 9)):
        if b.get(u, y, v).name == "minecraft:air":
            b.set(u, y, v, light(12))
    b.fill(1, 5, 2, 7, 5, 6, B("brown_carpet"))
    return b


def high_street_parade(ident="high_street_parade", name="Victorian High Street Parade (3 Shops)"):
    W, D = 22, 15
    b = Build(ident, name, "Shops", W, 17, D,
              "Three Victorian shops with two floors of flats over: bakery, charity shop and barbers. Rename the signs in game.")
    brick, stone = "ptmuk:london_stock_brick", P.STONE
    P.paving(b, 0, 0, W - 1, D - 1)
    b.fill(0, 0, 1, W - 1, 0, 12, "oak_planks")
    b.walls(0, 1, 1, W - 1, 12, 12, brick)
    shops = [("MAPLE & CO", "Family Bakers since 1952", CREAM, 0xFF6B3A1E, "bakery", "ptmuk:green_faience"),
             ("HOPE", "Charity Shop", 0xFF5B2C83, WHITE_INK, "charity", "ptmuk:burgundy_faience"),
             ("SHARP CUTS", "Gentlemen's Barbers", BLACK, GOLD_INK, "barber", "black_terracotta")]
    for i, (text, sub, board, ink, kind, stall) in enumerate(shops):
        o = 7 * i
        b.fill(o, 1, 1, o, 12, 11, brick)
        P.shopfront(b, o + 1, o + 6, 1, 1, text, sub, board, ink, door_u=o + 6 if i != 1 else o + 1, stall=stall)
        b.fill(o + 1, 4, 2, o + 6, 4, 11, "oak_planks")
        b.fill(o + 1, 8, 2, o + 6, 8, 11, "oak_planks")
        shop_interior(b, kind, o + 1, o + 6, 2, 7)
        b.fill(o + 1, 1, 8, o + 6, 3, 8, "calcite")
        b.fill(o + 3, 1, 8, o + 3, 2, 8, AIR)
        P.ceiling_lights(b, o + 1, 2, o + 6, 7, 4, step=3)
        for u in (o + 2, o + 5):
            P.sash(b, u, 5, 1)
            P.sash(b, u, 9, 1)
        for (u, y, v) in ((o + 3, 7, 4), (o + 3, 11, 4), (o + 3, 7, 9), (o + 3, 11, 9)):
            if b.get(u, y, v).name == "minecraft:air":
                b.set(u, y, v, light(11))
        b.door(o + 3, 1, 12, "spruce", facing="front", hinge="left")
        b.window(o + 2, 5, 12, 1, 2, "ptmuk:upvc_window")
        b.window(o + 5, 9, 12, 1, 2, "ptmuk:upvc_window")
    # pilasters with console brackets between the shops
    for u in (0, 7, 14, 21):
        b.fill(u, 1, 1, u, 4, 1, stone)
        b.set(u, 4, 0, stairs(stone, "back", "top"))
    b.fill(0, 8, 1, W - 1, 8, 1, stone)
    b.fill(0, 12, 0, W - 1, 12, 0, slab(stone, "top"))
    b.fill(0, 13, 1, W - 1, 13, 1, brick)
    b.fill(0, 14, 1, W - 1, 14, 1, slab(stone))
    b.gable_roof(0, W - 1, 2, 12, 12, "ptmuk:roof_slate")
    P.close_gable(b, 0, 2, 11, 12, brick)
    P.close_gable(b, W - 1, 2, 11, 12, brick)
    for u in (7, 14):
        b.chimney(u, 6, 13, 15, brick, w=1, d=2, pots=2)
    P.paving(b, 0, 13, W - 1, D - 1)
    return b


def corner_pub(ident="corner_pub", name="Victorian Corner Pub"):
    W, D = 14, 15
    b = Build(ident, name, "Shops", W, 18, D,
              "A corner pub: green glazed tiles, gold-lettered fascia round the corner, hanging sign, bar and fireplace inside.")
    brick, stone, tile = "ptmuk:red_brick", P.STONE, "ptmuk:green_faience"
    P.paving(b, 0, 0, W - 1, D - 1)
    b.fill(0, 0, 1, 12, 0, 12, "oak_planks")
    b.walls(0, 1, 1, 12, 9, 12, brick)
    b.fill(0, 1, 1, 12, 4, 1, tile)
    b.fill(12, 1, 1, 12, 4, 12, tile)
    b.fill(0, 5, 1, 12, 5, 1, stone)                     # stone string course over the tiles
    b.fill(12, 5, 1, 12, 5, 12, stone)
    b.fill(1, 5, 2, 11, 5, 11, "oak_planks")
    # windows: etched glass below, leaded lights above
    for u in (1, 2, 4, 5, 7, 8):
        b.set(u, 2, 1, P.pane("ptmuk:sash_window"))
        b.set(u, 3, 1, P.pane("ptmuk:leaded_window"))
    for v in (3, 4, 6, 7, 9, 10):
        b.set(12, 2, v, P.pane("ptmuk:sash_window"))
        b.set(12, 3, v, P.pane("ptmuk:leaded_window"))
    b.door(10, 1, 1, "dark_oak", facing="back", hinge="left")
    b.door(11, 1, 1, "dark_oak", facing="back", hinge="right")
    b.fill(10, 3, 1, 11, 3, 1, P.pane("ptmuk:leaded_window"))
    b.door(12, 1, 2, "dark_oak", facing="left", hinge="left")
    b.set(12, 3, 2, P.pane("ptmuk:leaded_window"))
    for u in range(0, 12):
        nbt = shop_sign_nbt("THE RED LION", "Free House • Real Ales • Food Served Daily", BLACK, GOLD_INK) if u == 0 \
            else shop_sign_nbt("", "", BLACK, GOLD_INK)
        b.set(u, 4, 0, Block("ptmuk:shop_sign", {"facing": "south"}, nbt))
    side_signs(b, 13, 1, 11, 4, "THE RED LION", "", BLACK, GOLD_INK, "right")
    hanging_sign(b, 3, 6, 0, ["", "The Red", "Lion", ""], facing="right")
    # upper floors: brick with stone sills, flower boxes under the windows
    for u in (2, 5, 8):
        P.sash(b, u, 6, 1)
    for v in (4, 8):
        b.side_window(12, 6, v, 1, 2, "ptmuk:sash_window")
    b.hipped_roof(0, 12, 1, 12, 10, "ptmuk:roof_slate")
    b.chimney(2, 9, 10, 16, brick, w=2, d=1, pots=2)
    # inside: bar, pumps, tables, fireplace
    for u in range(3, 10):
        b.set(u, 1, 8, "dark_oak_planks")
        b.set(u, 2, 8, slab("dark_oak", "bottom"))
    for u in (4, 6, 8):
        b.set(u, 3, 8, B("lightning_rod", facing="up", powered=False, waterlogged=False))
    b.fill(3, 1, 11, 9, 3, 11, B("barrel", facing="north", open=False))
    for (u, v) in ((2, 3), (5, 3), (8, 4)):
        b.set(u, 1, v, B("dark_oak_fence"))
        b.set(u, 2, v, B("dark_oak_pressure_plate", powered=False))
        b.set(u, 1, v + 1, stairs("dark_oak", "back"))
    b.set(1, 1, 6, B("campfire", facing="east", lit=False, signal_fire=False, waterlogged=False))
    b.fill(1, 2, 6, 1, 4, 6, brick)
    P.rug(b, 1, 2, 11, 7, 1, "red")
    for (u, v) in ((3, 4), (6, 4), (9, 4), (6, 9)):
        b.set(u, 4, v, B("lantern", hanging=True, waterlogged=False))
    for (u, y, v) in ((4, 8, 4), (8, 8, 4), (6, 8, 9)):
        b.set(u, y, v, light(11))
    return b


def cafe_takeaway(ident="cafe_takeaway", name="Cafe and Takeaway (Pair)"):
    W, D = 15, 13
    b = Build(ident, name, "Shops", W, 11, D,
              "A 1950s two-shop parade with flats over: a cafe and a Chinese takeaway under a flat roof.")
    brick = "ptmuk:red_brick"
    P.paving(b, 0, 0, W - 1, D - 1)
    b.fill(0, 0, 1, W - 1, 0, 11, "oak_planks")
    b.walls(0, 1, 1, W - 1, 8, 11, brick)
    b.fill(7, 1, 1, 7, 8, 11, brick)
    units = [("BLUEBELL CAFE", "Breakfasts • Lunches • Cakes", 0xFF6FA8DC, WHITE_INK, "cafe"),
             ("LOTUS KITCHEN", "Chinese Takeaway", RED_BOARD, YELLOW_INK, "takeaway")]
    for i, (text, sub, board, ink, kind) in enumerate(units):
        o = 7 * i
        P.shopfront(b, o + 1, o + 6, 1, 1, text, sub, board, ink, door_u=o + 1 if i == 0 else o + 6, stall="calcite")
        b.fill(o + 1, 4, 2, o + 6, 4, 10, "oak_planks")
        shop_interior(b, kind, o + 1, o + 6, 2, 6)
        P.ceiling_lights(b, o + 1, 2, o + 6, 6, 4, step=3)
        b.window(o + 2, 5, 1, 2, 2, "ptmuk:upvc_window")
        b.window(o + 5, 5, 1, 1, 2, "ptmuk:upvc_window")
        b.set(o + 3, 7, 4, light(11))
        b.door(o + 3, 1, 11, "spruce", facing="front", hinge="left")
    P.flat_roof(b, 0, W - 1, 1, 11, 8, deck="gray_concrete", parapet=brick, coping=P.STONE)
    return b


def bank(ident="bank", name="Portland Stone Bank"):
    W, D = 13, 15
    b = Build(ident, name, "Shops", W, 16, D,
              "An Edwardian bank in Portland stone: tall windows between pilasters, a carved name band and a balustrade.")
    stone = P.STONE
    P.paving(b, 0, 0, W - 1, D - 1)
    b.fill(0, 0, 2, W - 1, 0, 13, "polished_diorite")
    b.walls(0, 1, 2, W - 1, 12, 13, stone)
    for u in (1, 4, 8, 11):
        b.fill(u, 1, 1, u, 8, 1, B("quartz_pillar", axis="y"))
    for u in (2, 3, 9, 10):
        b.window(u, 2, 2, 1, 5, "ptmuk:georgian_window")
    b.door(6, 1, 2, "dark_oak", facing="back", hinge="left")
    b.fill(6, 3, 2, 6, 6, 2, P.pane("ptmuk:georgian_window"))
    b.set(6, 0, 1, "smooth_quartz")
    b.fill(0, 9, 1, W - 1, 9, 1, slab(stone, "top"))
    for u in range(1, 12):
        nbt = shop_sign_nbt("NORTHERN COUNTIES BANK", "", 0xFFE6DCC2, 0xFF3A3A3A, False) if u == 1 else shop_sign_nbt("", "", 0xFFE6DCC2, 0xFF3A3A3A, False)
        b.set(u, 10, 1, Block("ptmuk:shop_sign", {"facing": "south"}, nbt))
    b.fill(0, 11, 1, W - 1, 11, 1, slab(stone, "top"))
    b.fill(0, 13, 2, W - 1, 13, 13, "smooth_stone")
    b.walls(0, 14, 2, W - 1, 14, 13, B("sandstone_wall", up=True, north="none", south="none", east="none", west="none",
                                        waterlogged=False))
    b.walls(0, 15, 2, W - 1, 15, 13, slab("smooth_sandstone"))
    for u in (0, 4, 8, 12):
        b.set(u, 14, 2, stone)
    # banking hall
    for u in range(2, 11):
        b.set(u, 1, 9, "dark_oak_planks")
        b.set(u, 2, 9, slab("polished_andesite", "bottom"))
        b.set(u, 3, 9, P.pane("glass_pane"))
    for u in range(1, 12):
        for v in range(3, 13):
            if (u + v) % 2 == 0 and b.get(u, 0, v).name == "minecraft:polished_diorite":
                b.set(u, 0, v, "polished_andesite")
    P.ceiling_lights(b, 1, 3, 11, 12, 12, step=3)
    return b


def convenience_store(ident="convenience_store", name="Modern Convenience Store"):
    W, D = 16, 17
    b = Build(ident, name, "Shops", W, 7, D,
              "A single-storey modern convenience store: glass frontage, big lit sign, flat roof and a small car park.")
    P.paving(b, 0, 0, W - 1, D - 1, "ptmuk:tarmac")
    for u in (2, 6, 10):
        b.fill(u, 0, 0, u, 0, 3, "white_concrete")
    b.fill(0, 0, 4, W - 1, 0, 4, "ptmuk:paving_slabs")
    b.fill(0, 0, 5, W - 1, 0, 15, "smooth_stone")
    b.walls(0, 1, 5, W - 1, 4, 15, "light_gray_concrete")
    b.window(1, 1, 5, 14, 3, "ptmuk:shop_window")
    b.door(7, 1, 5, "birch", facing="back", hinge="left")
    b.door(8, 1, 5, "birch", facing="back", hinge="right")
    b.set(14, 2, 5, B("dropper", facing="south", triggered=False))
    b.set(14, 1, 5, "light_gray_concrete")
    b.set(14, 3, 5, "light_gray_concrete")
    for u in range(0, W):
        nbt = shop_sign_nbt("DAILY FRESH", "Groceries • Hot Food • Cash Machine • Open 7am - 11pm", 0xFF2E8B3C,
                            WHITE_INK) if u == 0 else shop_sign_nbt("", "", 0xFF2E8B3C, WHITE_INK)
        b.set(u, 4, 4, Block("ptmuk:shop_sign", {"facing": "south"}, nbt))
    P.flat_roof(b, 0, W - 1, 5, 15, 5, deck="gray_concrete", parapet=None)
    b.fill(0, 5, 5, W - 1, 5, 5, "light_gray_concrete")
    b.set(4, 6, 12, "iron_block")
    b.set(11, 6, 12, "iron_block")
    shop_interior(b, "convenience", 1, 14, 6, 14)
    P.ceiling_lights(b, 0, 5, W - 1, 15, 5, step=3)
    b.set(15, 1, 16, B("ptmuk:communal_bin", facing="north"))
    return b


# ================================================================== offices

def name_plate(b, u0, u1, y, v, text, sub="", board=0xFF2A2E34, ink=WHITE_INK, lit=True):
    for u in range(u0, u1 + 1):
        nbt = shop_sign_nbt(text, sub, board, ink, lit) if u == u0 else shop_sign_nbt("", "", board, ink, lit)
        b.set(u, y, v, Block("ptmuk:shop_sign", {"facing": "south"}, nbt))


def office_floors(b, u0, u1, v0, v1, floors, core):
    """Floor slabs with flush ceiling lights, desks and a stair core."""
    cu, cv = core
    for i, y in enumerate(floors[:-1]):
        nxt = floors[i + 1]
        if i:
            b.fill(u0 + 1, y, v0 + 1, u1 - 1, y, v1 - 1, "light_gray_concrete")
        P.ceiling_lights(b, u0, v0, u1, v1, nxt, step=3)
        if i:
            P.office_desks(b, u0 + 2, u1 - 2, v0 + 2, cv - 2, y + 1, every=3)
    P.stair_core(b, cu, cv, floors, material="stone_brick")


def glass_office(ident="glass_office", name="Modern Glass Office Block"):
    W, D = 16, 15
    floors = [0, 4, 8, 12, 16, 20, 24]
    b = Build(ident, name, "Offices", W, 29, D,
              "Six storeys of curtain-wall glazing with dark spandrel bands, a canopied entrance and roof plant.")
    P.paving(b, 0, 0, W - 1, D - 1)
    b.fill(0, 0, 1, W - 1, 0, 14, "polished_andesite")
    top = floors[-1]
    for y in range(1, top):
        for u in range(0, W):
            for v in (1, 14):
                b.set(u, y, v, P.pane("ptmuk:office_glazing"))
        for v in range(2, 14):
            for u in (0, W - 1):
                b.set(u, y, v, P.pane("ptmuk:office_glazing"))
    for y in floors[1:]:
        b.walls(0, y, 1, W - 1, y, 14, "gray_concrete")
    for (u, v) in ((0, 1), (W - 1, 1), (0, 14), (W - 1, 14)):
        b.fill(u, 1, v, u, top, v, "polished_deepslate")
    # entrance: doors, canopy and name
    b.door(7, 1, 1, "birch", facing="back", hinge="left")
    b.door(8, 1, 1, "birch", facing="back", hinge="right")
    b.fill(5, 4, 0, 10, 4, 0, slab("smooth_stone", "top"))
    name_plate(b, 6, 9, 3, 0, "MERIDIAN HOUSE")
    b.fill(1, 1, 3, 3, 1, 3, "white_concrete")
    b.fill(1, 2, 3, 3, 2, 3, slab("polished_andesite", "bottom"))
    office_floors(b, 0, W - 1, 1, 14, floors, (7, 8))
    P.flat_roof(b, 0, W - 1, 1, 14, top, deck="gray_concrete", parapet="polished_deepslate", coping="polished_deepslate")
    b.fill(5, top + 1, 6, 10, top + 3, 11, "light_gray_concrete")
    b.fill(2, top + 1, 3, 3, top + 1, 4, "iron_block")
    b.fill(12, top + 1, 3, 13, top + 1, 4, "iron_block")
    return b


def sixties_office(ident="sixties_office", name="1960s Concrete Office Block"):
    W, D = 18, 13
    floors = [0, 4, 8, 12, 16, 20, 24, 28]
    b = Build(ident, name, "Offices", W, 33, D,
              "Seven storeys of 1960s precast concrete: ribbon windows, vertical fins and a recessed glazed ground floor.")
    P.paving(b, 0, 0, W - 1, D - 1)
    top = floors[-1]
    b.fill(0, 0, 2, W - 1, 0, 12, "polished_andesite")
    b.walls(0, 5, 1, W - 1, top, 12, "polished_andesite")
    for y in floors[1:-1]:
        b.fill(0, y + 1, 1, W - 1, y + 2, 1, P.pane("ptmuk:grey_framed_window"))
        b.fill(0, y + 1, 12, W - 1, y + 2, 12, P.pane("ptmuk:grey_framed_window"))
        b.fill(0, y + 1, 2, 0, y + 2, 11, P.pane("ptmuk:grey_framed_window"))
        b.fill(W - 1, y + 1, 2, W - 1, y + 2, 11, P.pane("ptmuk:grey_framed_window"))
    for u in range(0, W, 3):
        b.fill(u, 5, 0, u, top, 0, "light_gray_concrete")
    # ground floor: columns in front of a recessed glass wall
    b.fill(1, 1, 2, W - 2, 3, 2, P.pane("ptmuk:shop_window"))
    b.fill(0, 1, 2, 0, 4, 12, "polished_andesite")
    b.fill(W - 1, 1, 2, W - 1, 4, 12, "polished_andesite")
    b.fill(0, 1, 12, W - 1, 4, 12, "polished_andesite")
    for u in range(0, W, 3):
        b.fill(u, 1, 1, u, 4, 1, "light_gray_concrete")
    b.fill(0, 4, 1, W - 1, 4, 2, "light_gray_concrete")
    b.door(8, 1, 2, "birch", facing="back", hinge="left")
    b.door(9, 1, 2, "birch", facing="back", hinge="right")
    name_plate(b, 6, 11, 4, 0, "NORWOOD HOUSE", "", 0xFF6E7378, WHITE_INK, False)
    office_floors(b, 0, W - 1, 1, 12, floors, (8, 5))
    P.flat_roof(b, 0, W - 1, 1, 12, top, deck="gray_concrete", parapet="light_gray_concrete", coping="smooth_stone")
    b.fill(6, top + 1, 4, 11, top + 3, 9, "polished_andesite")
    return b


def brick_office(ident="brick_office", name="1990s Brick Office"):
    W, D = 13, 15
    floors = [0, 4, 8, 12]
    b = Build(ident, name, "Offices", W, 19, D,
              "A three-storey 1990s office: red brick, tinted glazing, a glass stair tower and a hipped roof over a car park.")
    brick = "ptmuk:red_brick"
    P.paving(b, 0, 0, W - 1, 3, "ptmuk:tarmac")
    for u in (1, 4, 7):
        b.fill(u, 0, 0, u, 0, 2, "white_concrete")
    P.paving(b, 0, 4, W - 1, D - 1)
    b.fill(0, 0, 5, 9, 0, 14, "polished_andesite")
    b.walls(0, 1, 5, 9, 11, 14, brick)
    for y in floors[1:-1]:
        b.fill(0, y, 5, 9, y, 5, P.STONE)
    for y0 in (1, 5, 9):
        for u in (1, 2, 4, 5, 7, 8):
            b.window(u, y0 + 1, 5, 1, 2, "ptmuk:office_glazing")
        for v in (7, 8, 11, 12):
            b.side_window(0, y0 + 1, v, 1, 2, "ptmuk:office_glazing")
    # glass stair tower on the right front corner
    b.fill(10, 0, 4, 12, 0, 8, "polished_andesite")
    for y in range(1, 14):
        b.walls(10, y, 4, 12, y, 8, P.pane("ptmuk:office_glazing"))
    b.fill(10, 1, 4, 10, 14, 4, "light_gray_concrete")
    b.fill(12, 1, 4, 12, 14, 4, "light_gray_concrete")
    b.fill(10, 14, 4, 12, 14, 8, "light_gray_concrete")
    b.door(11, 1, 4, "birch", facing="back", hinge="left")
    b.fill(9, 4, 3, 13 - 1, 4, 3, slab("smooth_stone", "top"))
    name_plate(b, 1, 8, 4, 4, "RIVERSIDE COURT", "", 0xFF1C2E4A, WHITE_INK, True)
    b.hipped_roof(0, 9, 5, 14, 12, "ptmuk:roof_concrete_tiles")
    for i, y in enumerate(floors[:-1]):
        if i:
            b.fill(1, y, 6, 8, y, 13, "light_gray_concrete")
        P.ceiling_lights(b, 0, 5, 9, 14, floors[i + 1], step=3)
        P.office_desks(b, 2, 7, 7, 12, y + 1, every=3)
    for i, y in enumerate(floors[:-1]):
        b.fill(11, y + 1, 5, 11, y + 3, 5, AIR)
    for k in range(12):
        v = 5 + (k % 4) if (k // 4) % 2 == 0 else 8 - (k % 4)
    for (y, vs, facing) in ((1, 5, "back"), (5, 8, "front"), (9, 5, "back")):
        for k in range(4):
            v = vs + k if facing == "back" else vs - k
            b.set(11, y + k, v, stairs("stone_brick", facing))
    for y in floors[1:]:
        b.fill(10, y, 5, 10, y, 7, "light_gray_concrete") if y < 12 else None
    for y in (4, 8):
        b.fill(9, y + 1, 6, 9, y + 2, 6, AIR)
    b.fill(9, 1, 6, 9, 2, 6, AIR)
    return b


# ================================================================== flats

def tower_block(ident="tower_block", name="Council Tower Block (16 Storeys)"):
    W, D = 17, 16
    floors = [3 * i for i in range(17)]
    top = floors[-1]
    b = Build(ident, name, "Flats", W, top + 5, D,
              "A 1960s council tower block, refurbished: concrete and white panels, balconies, a named entrance and lift room.")
    P.paving(b, 0, 0, W - 1, D - 1)
    b.fill(0, 0, 2, W - 1, 0, 15, "polished_andesite")
    b.walls(0, 1, 2, W - 1, top, 15, "light_gray_concrete")
    for y in floors[1:-1]:
        b.walls(0, y, 2, W - 1, y, 15, "polished_andesite")
        b.fill(1, y, 3, W - 2, y, 14, "light_gray_concrete")
    for y in floors[1:-1]:
        for u in (2, 3, 6, 10, 13, 14):
            b.set(u, y + 1, 2, P.pane("ptmuk:upvc_window"))
            b.set(u, y + 1, 15, P.pane("ptmuk:upvc_window"))
        for u in (7, 9):
            b.fill(u, y + 1, 2, u, y + 2, 2, P.pane("ptmuk:upvc_window"))
        for v in (4, 5, 11, 12):
            b.set(0, y + 1, v, P.pane("ptmuk:upvc_window"))
            b.set(W - 1, y + 1, v, P.pane("ptmuk:upvc_window"))
        for u in (1, 4, 12, 15):
            b.set(u, y + 1, 2, "white_concrete")
            b.set(u, y + 2, 2, "white_concrete")
        # balconies on the front
        for u0 in (2, 12):
            b.fill(u0, y, 1, u0 + 2, y, 1, slab("smooth_stone", "top"))
            b.fill(u0, y + 1, 1, u0 + 2, y + 1, 1, P.pane("white_stained_glass_pane"))
        for u in (3, 13):
            b.fill(u, y + 1, 2, u, y + 2, 2, P.pane("ptmuk:upvc_window"))
        b.set(4, y + 2, 7, light(11)) if b.get(4, y + 2, 7).name == "minecraft:air" else None
        b.set(12, y + 2, 7, light(11)) if b.get(12, y + 2, 7).name == "minecraft:air" else None
        b.set(4, y + 2, 12, light(10)) if b.get(4, y + 2, 12).name == "minecraft:air" else None
    # ground floor: entrance with canopy and name, bin store
    b.fill(6, 1, 2, 10, 2, 2, P.pane("ptmuk:shop_window"))
    b.door(8, 1, 2, "birch", facing="back", hinge="left")
    b.fill(5, 3, 1, 11, 3, 1, slab("smooth_stone", "top"))
    name_plate(b, 6, 10, 4, 1, "ROWAN HOUSE", "Flats 1 - 64", 0xFFF2F0EA, 0xFF121214, False)
    b.set(1, 1, 1, B("ptmuk:communal_bin", facing="south"))
    b.set(15, 1, 1, B("ptmuk:communal_bin", facing="south"))
    # core and roof
    P.stair_core(b, 7, 8, floors, material="stone_brick")
    P.flat_roof(b, 0, W - 1, 2, 15, top, deck="gray_concrete", parapet="light_gray_concrete", coping="smooth_stone")
    b.fill(6, top + 1, 7, 10, top + 3, 12, "light_gray_concrete")
    b.fill(7, top + 4, 8, 9, top + 4, 11, slab("smooth_stone"))
    return b


def mansion_block(ident="mansion_block", name="1930s Mansion Block"):
    W, D = 21, 14
    floors = [0, 4, 8, 12, 16, 20]
    top = floors[-1]
    b = Build(ident, name, "Flats", W, top + 9, D,
              "Five storeys of 1930s red-brick mansion flats: stone bands, a stone entrance bay with balconies, slate roof.")
    brick, stone = "ptmuk:red_brick", P.STONE
    P.paving(b, 0, 0, W - 1, 1)
    b.fill(0, 0, 2, W - 1, 0, 13, "oak_planks")
    b.walls(0, 1, 2, W - 1, top, 13, brick)
    for y in floors[1:]:
        b.walls(0, y, 2, W - 1, y, 13, stone)
        b.fill(1, y, 3, W - 2, y, 12, "oak_planks") if y < top else None
    for y in floors[:-1]:
        for u in (2, 3, 6, 7, 13, 14, 17, 18):
            b.fill(u, y + 1, 2, u, y + 2, 2, P.pane("ptmuk:grey_framed_window"))
            b.fill(u, y + 1, 13, u, y + 2, 13, P.pane("ptmuk:grey_framed_window"))
        for v in (5, 6, 9, 10):
            b.fill(0, y + 1, v, 0, y + 2, v, P.pane("ptmuk:grey_framed_window"))
            b.fill(W - 1, y + 1, v, W - 1, y + 2, v, P.pane("ptmuk:grey_framed_window"))
        for (u, v) in ((4, 6), (16, 6), (4, 10), (16, 10)):
            b.set(u, y + 3, v, light(11)) if y + 3 < top else None
    # central entrance bay in stone, projecting, with balconies over the door
    b.fill(8, 1, 1, 12, top, 1, stone)
    for y in floors[:-1]:
        b.fill(9, y + 1, 1, 11, y + 2, 1, P.pane("ptmuk:grey_framed_window"))
        if y:
            b.fill(9, y, 0, 11, y, 0, slab(stone, "top"))
            b.fill(9, y + 1, 0, 11, y + 1, 0, B("iron_bars"))
    b.door(10, 1, 1, "dark_oak", facing="back", hinge="left")
    b.set(9, 1, 1, stone)
    b.set(11, 1, 1, stone)
    b.set(10, 3, 1, P.pane("ptmuk:georgian_window"))
    name_plate(b, 8, 12, 4, 0, "PARKVIEW MANSIONS", "", 0xFFE6DCC2, 0xFF2A2A2A, False)
    b.fill(0, top, 1, W - 1, top, 1, slab(stone, "top"))
    b.hipped_roof(0, W - 1, 2, 13, top + 1, "ptmuk:roof_slate")
    for u in (3, 17):
        b.chimney(u, 7, top + 2, top + 7, brick, w=1, d=2, pots=2)
    P.stair_core(b, 9, 6, floors, material="oak")
    return b


def modern_apartments(ident="modern_apartments", name="Modern Apartment Block"):
    W, D = 19, 15
    floors = [0, 3, 6, 9, 12, 15]
    top = floors[-1]
    b = Build(ident, name, "Flats", W, top + 3, D,
              "A 2010s apartment block: stock brick and grey cladding, big grey-framed windows, glass balconies and solar panels.")
    brick, clad = "ptmuk:london_stock_brick", "polished_deepslate"
    P.paving(b, 0, 0, W - 1, 1)
    b.fill(0, 0, 2, W - 1, 0, 14, "polished_andesite")
    b.walls(0, 1, 2, W - 1, 6, 14, brick)
    b.walls(0, 7, 2, W - 1, top, 14, clad)
    for y in floors[1:-1]:
        b.fill(1, y, 3, W - 2, y, 13, "light_gray_concrete")
    for y in floors[:-1]:
        for u0 in (1, 6, 11, 15):
            b.fill(u0, y + 1, 2, u0 + 2, y + 2, 2, P.pane("ptmuk:grey_framed_window"))
            b.fill(u0, y + 1, 14, u0 + 2, y + 2, 14, P.pane("ptmuk:grey_framed_window"))
        if y:
            for u0 in (1, 11):
                b.fill(u0, y, 1, u0 + 2, y, 1, slab("smooth_stone", "top"))
                b.fill(u0, y + 1, 1, u0 + 2, y + 1, 1, P.pane("glass_pane"))
        for (u, v) in ((3, 6), (13, 6), (3, 11), (13, 11)):
            b.set(u, y + 2, v, light(11)) if b.get(u, y + 2, v).name == "minecraft:air" else None
    # glazed entrance lobby
    b.fill(6, 1, 2, 9, 2, 2, P.pane("ptmuk:shop_window"))
    b.door(8, 1, 2, "birch", facing="back", hinge="left")
    name_plate(b, 6, 9, 3, 1, "THE COBALT BUILDING", "", 0xFF2A2E34, WHITE_INK, True)
    P.stair_core(b, 8, 7, floors, material="stone_brick")
    P.flat_roof(b, 0, W - 1, 2, 14, top, deck="gray_concrete", parapet=clad, coping="polished_deepslate")
    for u in range(2, W - 2, 2):
        for v in (4, 5, 11, 12):
            b.set(u, top + 1, v, B("daylight_detector", inverted=False, power=0))
    b.fill(16, 0, 0, 18, 0, 1, "ptmuk:paving_slabs")
    for u in (16, 17, 18):
        b.set(u, 1, 0, B("ptmuk:bike_stand", facing="south"))
    return b


def deck_access_flats(ident="deck_access_flats", name="1960s Deck-Access Flats"):
    W, D = 25, 12
    floors = [0, 4, 8, 12]
    top = floors[-1]
    b = Build(ident, name, "Flats", W, top + 3, D,
              "Low-rise 1960s council flats: brown brick, concrete walkway decks with front doors, stair towers at each end.")
    brick = "ptmuk:red_brick"
    P.lawn(b, 0, 0, W - 1, 1)
    b.fill(0, 0, 2, W - 1, 0, 11, "polished_andesite")
    b.walls(3, 1, 3, W - 4, top, 11, brick)
    for y in floors[1:-1]:
        b.fill(4, y, 4, W - 5, y, 10, "oak_planks")
        # walkway deck in front with a concrete balustrade
        b.fill(3, y, 2, W - 4, y, 2, "light_gray_concrete")
        b.fill(3, y + 1, 2, W - 4, y + 1, 2, "polished_andesite")
    b.fill(3, top, 2, W - 4, top, 2, "light_gray_concrete")
    for y in floors[:-1]:
        for i, u in enumerate(range(5, W - 5, 4)):
            b.door(u, y + 1, 3, ["oak", "spruce", "birch", "dark_oak"][i % 4],
                   facing="back", hinge="left")
            b.fill(u + 1, y + 2, 3, u + 2, y + 2, 3, P.pane("ptmuk:upvc_window"))
            b.fill(u, y + 2, 11, u + 2, y + 3, 11, P.pane("ptmuk:upvc_window")) if y + 3 < top else b.fill(u, y + 2, 11, u + 2, y + 2, 11, P.pane("ptmuk:upvc_window"))
            b.set(u + 1, y + 3, 7, light(11)) if b.get(u + 1, y + 3, 7).name == "minecraft:air" else None
    # stair towers at each end
    for u0 in (0, W - 3):
        b.walls(u0, 1, 2, u0 + 2, top + 1, 6, "light_gray_concrete")
        for y in range(2, top, 2):
            b.set(u0 + 1, y, 2, P.pane("glass_pane"))
        b.door(u0 + 1, 1, 2, "birch", facing="back", hinge="left")
        for (y, vs, facing) in ((1, 3, "back"), (5, 6, "front"), (9, 3, "back")):
            for k in range(4):
                v = vs + k if facing == "back" else vs - k
                if 3 <= v <= 5:
                    b.set(u0 + 1, y + k, v, stairs("stone_brick", facing))
        for y in floors[1:]:
            inner = u0 + 2 if u0 == 0 else u0
            b.fill(inner, y + 1, 2, inner, y + 2, 2, AIR) if y < top else None
    P.flat_roof(b, 3, W - 4, 2, 11, top, deck="gray_concrete", parapet=None)
    b.fill(3, top + 1, 2, W - 4, top + 1, 2, slab("smooth_stone"))
    return b


# ================================================================== other

def church(ident="church", name="Victorian Parish Church"):
    W, D = 15, 27
    b = Build(ident, name, "Other", W, 32, D,
              "A small Victorian Gothic church: stone tower and spire, steep slate roof, lancet windows, pews and a churchyard.")
    stone, trim = "stone_bricks", "polished_andesite"
    P.lawn(b, 0, 0, W - 1, D - 1)
    b.fill(6, 0, 0, 8, 0, 6, "gravel")
    # nave
    b.fill(2, 0, 7, 12, 0, 25, "polished_andesite")
    b.walls(2, 1, 7, 12, 8, 25, stone)
    for v in (9, 12, 15, 18, 21):
        for u in (2, 12):
            b.fill(u, 2, v, u, 5, v, P.pane(["purple_stained_glass_pane", "blue_stained_glass_pane", "red_stained_glass_pane"][v % 3]))
            b.set(u, 6, v, trim)
    b.fill(6, 3, 25, 8, 7, 25, P.pane("blue_stained_glass_pane"))
    b.fill(7, 8, 25, 7, 8, 25, P.pane("red_stained_glass_pane"))
    b.gable_roof_side(1, 13, 7, 26, 9, "ptmuk:roof_slate")
    P.close_gable_front(b, 7, 2, 12, 9, stone)
    P.close_gable_front(b, 25, 2, 12, 9, stone)
    # tower and spire at the front
    b.walls(4, 1, 1, 10, 18, 7, stone)
    b.fill(5, 0, 2, 9, 0, 6, "polished_andesite")
    b.door(7, 1, 1, "dark_oak", facing="back", hinge="left")
    b.set(7, 3, 1, P.pane("purple_stained_glass_pane"))
    b.fill(6, 1, 1, 6, 3, 1, stone)
    b.fill(7, 6, 1, 7, 9, 1, P.pane("blue_stained_glass_pane"))
    for u in (6, 8):
        b.fill(u, 15, 1, u, 17, 1, B("iron_bars"))
    for v in (3, 5):
        b.fill(4, 15, v, 4, 17, v, B("iron_bars"))
        b.fill(10, 15, v, 10, 17, v, B("iron_bars"))
    b.walls(4, 18, 1, 10, 18, 7, trim)
    b.hipped_roof(4, 10, 1, 7, 19, "ptmuk:roof_slate")
    b.fill(7, 22, 4, 7, 24, 4, B("andesite_wall", up=True, north="none", south="none", east="none", west="none", waterlogged=False))
    b.set(7, 25, 4, B("lightning_rod", facing="up", powered=False, waterlogged=False))
    # inside: pews, aisle, altar
    for v in range(10, 22, 2):
        for u in (3, 4, 5, 9, 10, 11):
            b.set(u, 1, v, stairs("dark_oak", "front"))
    P.rug(b, 6, 8, 8, 23, 1, "red")
    b.fill(5, 1, 23, 9, 1, 24, "smooth_quartz")
    b.fill(6, 2, 24, 8, 2, 24, slab("smooth_quartz"))
    for (u, v) in ((7, 11), (7, 17), (7, 22)):
        b.set(u, 7, v, B("lantern", hanging=True, waterlogged=False))
    # churchyard: gravestones, a yew and the noticeboard
    for (u, v) in ((1, 10), (1, 14), (1, 18), (13, 11), (13, 15), (13, 19), (1, 22), (13, 23)):
        b.set(u, 1, v, B("andesite_wall", up=True, north="none", south="none", east="none", west="none", waterlogged=False))
    b.fill(0, 1, 1, 1, 3, 2, B("spruce_leaves", persistent=True, distance=1, waterlogged=False))
    b.set(0, 1, 2, B("spruce_log", axis="y"))
    for u in (10, 12):
        b.set(u, 1, 0, B("dark_oak_fence"))
    for u in (10, 11, 12):
        nbt = shop_sign_nbt("ST MARY'S", "Sunday Service 10.30am • All Welcome", 0xFF1C2E4A, GOLD_INK, False) if u == 10 \
            else shop_sign_nbt("", "", 0xFF1C2E4A, GOLD_INK, False)
        b.set(u, 2, 0, Block("ptmuk:shop_sign", {"facing": "south"}, nbt))
    b.fill(0, 1, 0, 5, 1, 0, "stone_bricks")
    b.fill(9, 1, 0, 9, 1, 0, "stone_bricks")
    b.fill(13, 1, 0, 14, 1, 0, "stone_bricks")
    return b


def petrol_station(ident="petrol_station", name="Petrol Station"):
    W, D = 22, 21
    b = Build(ident, name, "Other", W, 9, D,
              "A forecourt with a lit canopy over four pump islands (EV chargers on one), a price board and a kiosk shop.")
    P.paving(b, 0, 0, W - 1, D - 1, "smooth_stone")
    b.fill(0, 0, 0, W - 1, 0, 1, "ptmuk:tarmac")
    # canopy on four columns, lit underneath, with a fascia
    for (u, v) in ((4, 4), (15, 4), (4, 10), (15, 10)):
        b.fill(u, 1, v, u, 5, v, "white_concrete")
    b.fill(2, 6, 2, 17, 6, 12, "white_concrete")
    for u in range(2, 18):
        for v in (2, 12):
            b.set(u, 6, v, "red_concrete")
        for v in range(3, 12, 3):
            if 3 <= u <= 16 and (u % 3 == 0):
                b.set(u, 6, v, "sea_lantern")
    for v in range(3, 12):
        b.set(2, 6, v, "red_concrete")
        b.set(17, 6, v, "red_concrete")
    name_plate(b, 7, 12, 6, 1, "FILL & GO", "", 0xFFB3202A, WHITE_INK, True)
    # pump islands
    for u0 in (6, 12):
        for v0 in (5, 9):
            b.fill(u0, 1, v0, u0 + 1, 1, v0 + 1, slab("smooth_stone"))
            if u0 == 12 and v0 == 9:
                b.set(u0, 2, v0, B("ptmuk:ev_charger", facing="south"))
                b.set(u0 + 1, 2, v0 + 1, B("ptmuk:ev_charger", facing="north"))
            else:
                b.set(u0, 2, v0, "white_concrete")
                b.set(u0, 3, v0, "light_gray_concrete")
                b.set(u0 + 1, 2, v0 + 1, "white_concrete")
                b.set(u0 + 1, 3, v0 + 1, "light_gray_concrete")
    # price totem at the front corner
    b.fill(20, 1, 1, 20, 2, 1, "white_concrete")
    for i, (t, sub) in enumerate((("Diesel 152.9", ""), ("Unleaded 145.9", ""), ("FILL & GO", ""))):
        b.set(20, 3 + i, 1, Block("ptmuk:shop_sign", {"facing": "south"},
                                  shop_sign_nbt(t, sub, 0xFFB3202A if i == 2 else 0xFF121214, WHITE_INK if i == 2 else YELLOW_INK)))
    # kiosk shop at the back
    b.walls(5, 1, 15, 16, 4, 20, "light_gray_concrete")
    b.fill(5, 0, 15, 16, 0, 20, "polished_andesite")
    b.window(6, 1, 15, 10, 3, "ptmuk:shop_window")
    b.door(10, 1, 15, "birch", facing="back", hinge="left")
    for u in range(5, 17):
        nbt = shop_sign_nbt("FILL & GO SHOP", "Food to go • Coffee • Car Wash • Air & Water", 0xFFB3202A, WHITE_INK) \
            if u == 5 else shop_sign_nbt("", "", 0xFFB3202A, WHITE_INK)
        b.set(u, 4, 14, Block("ptmuk:shop_sign", {"facing": "south"}, nbt))
    P.flat_roof(b, 5, 16, 15, 20, 5, deck="gray_concrete", parapet=None)
    shop_interior(b, "convenience", 6, 15, 16, 19)
    P.ceiling_lights(b, 5, 15, 16, 20, 5, step=3)
    return b


# ================================================================== catalogue

def all_prefabs():
    return [
        victorian_terrace("victorian_terrace", "Victorian Terraced House", 1),
        victorian_terrace("victorian_terrace_row", "Victorian Terrace (Row of 4)", 4),
        victorian_terrace("victorian_terrace_red", "Victorian Terraced House (Red Brick)", 1, brick="ptmuk:red_brick",
                          band="ptmuk:london_stock_brick", doors=["mangrove"]),
        semi_pair("edwardian_semis", "Edwardian Semi-Detached Pair", "edwardian"),
        semi_pair("thirties_semis", "1930s Semi-Detached Pair", "thirties"),
        council_terrace(),
        newbuild_detached(),
        bungalow(),
        georgian_townhouse(),
        corner_shop(),
        high_street_parade(),
        corner_pub(),
        cafe_takeaway(),
        bank(),
        convenience_store(),
        glass_office(),
        sixties_office(),
        brick_office(),
        tower_block(),
        mansion_block(),
        modern_apartments(),
        deck_access_flats(),
        church(),
        petrol_station(),
    ]


def write_java(builds):
    rows = ",\n".join(
        f'            new Entry("{b.ident}", "{b.name}", "{b.category}", {b.W}, {b.H}, {b.D}, "{b.description}")'
        for b in builds)
    JAVA.write_text(f'''package com.ptmuk.building;

import java.util.List;

/** Generated by tools/prefab_designs.py together with the structures. Do not edit by hand. */
public final class PrefabCatalog {{
    public record Entry(String id, String name, String category, int width, int height, int depth, String description) {{
    }}

    public static final List<String> CATEGORIES = List.of({", ".join(f'"{c}"' for c in CATEGORIES)});

    public static final List<Entry> ENTRIES = List.of(
{rows});

    public static Entry get(String id) {{
        for (Entry e : ENTRIES) {{
            if (e.id().equals(id)) {{
                return e;
            }}
        }}
        return null;
    }}

    private PrefabCatalog() {{
    }}
}}
''')


def generate():
    builds = all_prefabs()
    out = DATA / "structures/prefab"
    if out.exists():
        for f in out.glob("*.nbt"):
            f.unlink()
    thumbs = ASSETS / "textures/gui/prefab"
    thumbs.mkdir(parents=True, exist_ok=True)
    for f in thumbs.glob("*.png"):
        f.unlink()
    for b in builds:
        b.write_nbt(out / f"{b.ident}.nbt")
        render_thumbnail(b, ASSETS).save(thumbs / f"{b.ident}.png")
        print(f"  prefab {b.ident}: {b.W}x{b.H}x{b.D}, {len(b.cells)} blocks")
    write_java(builds)
    return builds


if __name__ == "__main__":
    generate()
