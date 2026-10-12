"""Bigger British buildings for the Prefab Tool: large houses, multi-storey flats and whole
housing estates laid out from the smaller buildings (turned to face their streets).
Coordinates as in prefab_lib: u across the front, y up, v from the front boundary back."""
from prefab_lib import AIR, B, Build, DIRS, light, slab, stairs
import prefab_parts as P
import prefab_designs as PD

STONE = P.STONE
RED, STOCK = "ptmuk:red_brick", "ptmuk:london_stock_brick"
SLATE, CLAY, THATCH = "ptmuk:roof_slate", "ptmuk:roof_clay_tiles", "ptmuk:roof_thatch"
PLASTER = "calcite"


def pane(kind):
    return P.pane(kind)


def lights(b, spots, level=12):
    for (u, y, v) in spots:
        if b.inside(u, y, v) and b.get(u, y, v).name == "minecraft:air":
            b.set(u, y, v, light(level))


def quoins(b, corners, y0, y1, stone=STONE):
    """Stone blocks on alternate courses up the corners."""
    for (u, v) in corners:
        for y in range(y0, y1 + 1, 2):
            b.set(u, y, v, stone)


def opening(b, u0, y0, v0, u1, y1, v1):
    b.fill(u0, y0, v0, u1, y1, v1, AIR)


def flowers(b, cells, y=1):
    kinds = ["poppy", "cornflower", "oxeye_daisy", "allium", "azure_bluet", "pink_tulip", "orange_tulip", "lily_of_the_valley"]
    for i, (u, v) in enumerate(cells):
        if b.get(u, y, v).name == "minecraft:air":
            b.set(u, y, v, B(kinds[(u * 7 + v * 3 + i) % len(kinds)]))


def shed(b, u0, v0, wood="spruce"):
    b.fill(u0, 1, v0, u0 + 2, 2, v0 + 2, f"{wood}_planks")
    b.fill(u0, 3, v0, u0 + 2, 3, v0 + 2, slab("dark_oak"))
    b.door(u0 + 1, 1, v0, wood, facing="back", hinge="left")


def bench(b, u, v, facing="back", kind="wood"):
    b.set(u, 1, v, B(f"ptmuk:bench_{kind}", facing=DIRS[facing]))


def leaves(kind="oak_leaves"):
    return B(kind, persistent=True, distance=1, waterlogged=False)


# ================================================================== bigger houses

def victorian_villa(ident="victorian_villa", name="Victorian Detached Villa"):
    W, D = 21, 30
    b = Build(ident, name, "Houses", W, 22, D,
              "A double-fronted Victorian villa: red brick with stone dressings, two-storey bays, a columned porch, "
              "three floors and a hipped slate roof, with a rear wing, big gardens and a side drive.")
    brick, roof, win = RED, SLATE, "ptmuk:sash_window"
    P.lawn(b, 0, 0, W - 1, D - 1)
    # front boundary: dwarf wall with railings, stone gate piers, tiled path; gravel drive at the side
    for u in range(W):
        b.set(u, 1, 0, brick)
        b.set(u, 2, 0, B("iron_bars"))
    for u in (9, 11):
        b.fill(u, 1, 0, u, 2, 0, STONE)
        b.set(u, 3, 0, slab(STONE))
    b.set(10, 1, 0, B("dark_oak_fence_gate", facing="south", open=False, in_wall=False, powered=False))
    b.set(10, 2, 0, AIR)
    P.tiled_path(b, 10, 0, 3)
    b.fill(18, 0, 0, 20, 0, 18, "gravel")
    b.fill(18, 1, 0, 20, 2, 0, AIR)
    P.hedge(b, 1, 8, 1)
    P.hedge(b, 12, 16, 1)
    # main block u3..17, v5..16; floors at y 5 and 10, attic floor at 14
    b.fill(3, 0, 4, 17, 0, 16, "oak_planks")
    b.walls(3, 1, 5, 17, 13, 16, brick)
    for y in (5, 10):
        b.fill(4, y, 6, 16, y, 15, "oak_planks")
        b.fill(3, y, 5, 17, y, 5, STONE)                       # string courses
    b.fill(3, 14, 5, 17, 14, 16, "spruce_planks")
    quoins(b, [(3, 5), (17, 5), (3, 16), (17, 16)], 1, 13)
    # two-storey bays either side of the door
    for (a, z) in ((4, 6), (14, 16)):
        for u in range(a, z + 1):
            b.set(u, 0, 4, "oak_planks")
            b.set(u, 1, 4, brick)
            b.fill(u, 2, 4, u, 3, 4, pane(win))
            b.set(u, 4, 4, STONE)
            b.set(u, 5, 4, STONE)
            b.set(u, 6, 4, brick)
            b.fill(u, 7, 4, u, 8, 4, pane(win))
            b.set(u, 9, 4, STONE)
            b.set(u, 10, 4, stairs(roof, "back"))
            if u == a + 1:                                 # open in the middle; the end windows turn into the wall
                opening(b, u, 1, 5, u, 4, 5)
                opening(b, u, 6, 5, u, 9, 5)
        b.set(a - 1, 10, 4, stairs(roof, "right"))
        b.set(z + 1, 10, 4, stairs(roof, "left"))
    # porch: two columns, a stone canopy, a step; panelled door with fanlight
    for u in (9, 11):
        b.fill(u, 1, 4, u, 3, 4, B("quartz_pillar", axis="y"))
    b.fill(9, 4, 3, 11, 4, 4, slab(STONE))
    b.set(10, 0, 4, "smooth_stone")
    b.door(10, 1, 5, "dark_oak", facing="back", hinge="left")
    b.set(10, 3, 5, pane("ptmuk:georgian_window"))
    # upper windows with sills, side and back windows
    P.sash(b, 10, 7, 5)
    for u in (5, 10, 15):
        P.sash(b, u, 11, 5)
    for v in (8, 13):
        for y in (2, 7, 11):
            b.side_window(3, y, v, 1, 2, win)
            b.side_window(17, y, v, 1, 2, win)
    for u in (5, 7):
        for y in (2, 7, 11):
            b.window(u, y, 16, 1, 2, win)
    # roof and chimneys
    b.hipped_roof(2, 18, 4, 17, 14, roof)
    b.chimney(3, 7, 14, 19, brick, w=1, d=2, pots=2)
    b.chimney(17, 12, 14, 19, brick, w=1, d=2, pots=2)
    # rear wing: kitchen with the bathroom over it
    b.walls(9, 1, 16, 16, 9, 23, brick)
    b.fill(10, 0, 17, 15, 0, 22, "polished_andesite")
    b.fill(10, 5, 17, 15, 5, 22, "oak_planks")
    b.gable_roof_side(8, 17, 17, 24, 10, roof)
    P.close_gable_front(b, 23, 9, 16, 10, brick)
    opening(b, 11, 1, 16, 11, 2, 16)                  # from the end of the hall, both floors
    opening(b, 11, 6, 16, 11, 7, 16)
    b.door(12, 1, 23, "spruce", facing="front", hinge="left")
    b.window(14, 2, 23, 1, 2, win)
    b.window(11, 7, 23, 2, 2, win)
    b.side_window(16, 2, 19, 2, 2, win)
    b.side_window(9, 2, 19, 2, 2, win)
    # inside: a central hall with the stairs, two rooms either side on every floor
    for y0, y1 in ((1, 4), (6, 9), (11, 13)):
        b.fill(8, y0, 6, 8, y1, 15, PLASTER)
        b.fill(12, y0, 6, 12, y1, 15, PLASTER)
        b.fill(4, y0, 11, 7, y1, 11, PLASTER)
        b.fill(13, y0, 11, 16, y1, 11, PLASTER)
        for (u, v) in ((8, 8), (12, 8), (8, 13 if y0 == 1 else 14), (12, 13)):   # upstairs: clear of the next flight
            opening(b, u, y0, v, u, y0 + 1, v)
    for i in range(5):
        b.set(11, 1 + i, 9 + i, stairs("dark_oak", "back"))
    opening(b, 11, 5, 9, 11, 5, 12)
    for i in range(5):
        b.set(9, 6 + i, 13 - i, stairs("dark_oak", "front"))
    opening(b, 9, 10, 10, 9, 10, 13)
    b.set(10, 6, 9, B("dark_oak_fence"))
    # lounge (front left): fireplace, settee, armchair, rug, books
    b.set(4, 1, 8, B("blast_furnace", facing="east", lit=False))
    b.set(4, 2, 8, slab("dark_oak", "bottom"))
    P.sofa(b, 5, 6, 1, 10, "back", "dark_oak")
    P.armchair(b, 7, 1, 7, "right")
    P.rug(b, 5, 6, 7, 9, 1, "red")
    P.bookcase(b, 7, 1, 10)
    # dining room (front right)
    for v in (6, 7):                                   # clear of the door from the hall
        P.table(b, 14, 1, v, top="dark_oak_pressure_plate", leg="dark_oak_fence")
        b.set(13, 1, v, stairs("dark_oak", "left"))
        b.set(15, 1, v, stairs("dark_oak", "right"))
    b.set(16, 1, 10, B("barrel", facing="west", open=False))
    P.rug(b, 13, 6, 16, 9, 1, "green")
    # study (back left), sitting room (back right)
    for u in (4, 5, 6, 7):
        P.bookcase(b, u, 1, 15)
    b.set(5, 1, 13, B("spruce_planks"))
    b.set(5, 1, 12, stairs("spruce", "front"))
    P.sofa(b, 14, 15, 1, 15, "back", "spruce")
    P.plant(b, 16, 1, 12)
    # kitchen in the wing
    P.kitchen_run(b, 18, 22, 1, 15, facing="left", side=True)
    P.table(b, 12, 1, 20)
    b.set(11, 1, 20, stairs("oak", "left"))
    b.set(13, 1, 20, stairs("oak", "right"))
    # bedrooms and bathroom
    P.bed(b, 5, 6, 6, "white", "front")
    P.bed(b, 14, 6, 6, "light_blue", "front")
    P.bed(b, 5, 6, 15, "red", "back")
    P.bed(b, 14, 6, 15, "green", "back")
    for (u, v) in ((7, 6), (13, 9), (4, 12), (16, 13)):
        P.wardrobe(b, u, 6, v)
    P.bath(b, 13, 15, 6, 22)
    b.set(10, 6, 22, B("cauldron"))
    P.bed(b, 5, 11, 6, "pink", "front")
    P.bed(b, 15, 11, 15, "yellow", "back")
    P.bed(b, 14, 11, 6, "cyan", "front")
    # back garden: patio, lawn, trees, shed, flower borders, brick walls
    P.paving(b, 3, 17, 8, 19)
    b.fill(0, 1, 1, 0, 2, D - 1, brick)
    b.fill(W - 1, 1, 19, W - 1, 2, D - 1, brick)
    b.fill(0, 1, D - 1, W - 1, 2, D - 1, brick)
    P.tree(b, 4, 25, h=4, r=2)
    P.tree(b, 17, 26, h=3, r=2, leaves="birch_leaves", log="birch_log")
    shed(b, 9, 26)
    bench(b, 5, 19, "back")
    flowers(b, [(u, 28) for u in range(1, 8)] + [(u, 28) for u in range(13, 20)])
    lights(b, [(5, 4, 7), (14, 4, 7), (5, 4, 13), (14, 4, 13), (10, 4, 7), (10, 4, 14), (12, 4, 20),
               (5, 9, 8), (14, 9, 8), (5, 9, 13), (14, 9, 13), (10, 9, 11), (12, 9, 19),
               (5, 13, 8), (14, 13, 8), (5, 13, 13), (14, 13, 13)])
    return b


def arts_and_crafts(ident="arts_and_crafts_house", name="Edwardian Arts & Crafts House"):
    W, D = 21, 27
    b = Build(ident, name, "Houses", W, 18, D,
              "A large Edwardian Arts & Crafts house: red brick below, roughcast above, a big timbered gable over a "
              "two-storey bay, a deep porch, leaded windows, tall chimneys and a steep clay tile roof.")
    brick, render, roof, timber, win = RED, "ptmuk:pebbledash", CLAY, "stripped_dark_oak_log", "ptmuk:leaded_window"
    P.lawn(b, 0, 0, W - 1, D - 1)
    P.hedge(b, 0, 11, 0)
    P.hedge(b, 15, W - 1, 0)
    for v in range(0, 5):
        b.set(13, 0, v, "packed_mud" if v % 2 else "ptmuk:paving_slabs")
    # main block u3..17, v5..15; ground floor y1..3, first floor y5..7
    b.fill(3, 0, 3, 17, 0, 15, "oak_planks")
    b.walls(3, 1, 5, 17, 3, 15, brick)
    b.walls(3, 4, 5, 17, 7, 15, render)
    b.fill(4, 4, 6, 16, 4, 14, "oak_planks")
    b.fill(3, 4, 5, 17, 4, 5, brick)
    # left: gabled two-storey bay projecting to v3
    b.walls(3, 1, 3, 9, 3, 5, brick)
    b.walls(3, 4, 3, 9, 7, 5, render)
    b.fill(4, 4, 4, 8, 4, 4, "oak_planks")
    b.window(4, 2, 3, 5, 2, win)
    b.window(5, 5, 3, 3, 2, win)
    opening(b, 4, 1, 5, 8, 3, 5)
    opening(b, 4, 5, 5, 8, 7, 5)
    b.fill(3, 4, 3, 9, 4, 3, B(timber, axis="x"))
    # right: deep porch under a tiled canopy, the door and a ribbon window above
    for u in (11, 16):
        b.fill(u, 1, 3, u, 3, 3, B("dark_oak_fence"))
    b.fill(10, 4, 3, 17, 4, 3, stairs(roof, "back"))
    b.fill(10, 4, 4, 17, 4, 4, slab(roof, "top"))
    b.door(13, 1, 5, "dark_oak", facing="back", hinge="left")
    b.set(14, 2, 5, pane(win))
    b.window(15, 2, 5, 2, 1, win)
    b.window(11, 5, 5, 6, 2, win)
    for v in (8, 12):
        b.side_window(3, 2, v, 1, 1, win)
        b.side_window(17, 2, v, 1, 1, win)
        b.side_window(3, 5, v, 1, 2, win)
        b.side_window(17, 5, v, 1, 2, win)
    for u in (5, 6, 14, 15):
        b.window(u, 2, 15, 1, 1, win)
        b.window(u, 5, 15, 1, 2, win)
    b.door(10, 1, 15, "spruce", facing="front", hinge="left")
    # roofs: main ridge left to right, the big front gable with timbering, render gable ends
    b.gable_roof(2, 18, 4, 16, 8, roof)
    b.gable_roof_side(2, 10, 2, 9, 8, roof)
    P.close_gable_front(b, 3, 3, 9, 8, render)
    for u in range(4, 9):
        for y in range(8, 12):
            if b.get(u, y, 3).name == "ptmuk:pebbledash" and u % 2 == 0:
                b.set(u, y, 3, B(timber, axis="y"))
    b.fill(3, 8, 3, 9, 8, 3, B(timber, axis="x"))
    P.close_gable(b, 3, 5, 15, 8, render)
    P.close_gable(b, 17, 5, 15, 8, render)
    b.chimney(3, 10, 8, 16, brick, w=1, d=1, pots=1)
    b.chimney(17, 10, 8, 16, brick, w=1, d=1, pots=1)
    b.chimney(12, 9, 8, 15, brick, w=1, d=1, pots=1)
    # inside
    b.fill(10, 1, 6, 10, 3, 14, PLASTER)
    b.fill(4, 1, 10, 9, 3, 10, PLASTER)
    b.fill(11, 1, 10, 16, 3, 10, PLASTER)
    for (u, v) in ((10, 8), (10, 12), (12, 10)):
        opening(b, u, 1, v, u, 2, v)
    for i in range(4):
        b.set(16, 1 + i, 6 + i, stairs("oak", "back"))
    opening(b, 16, 4, 6, 16, 4, 8)
    b.set(4, 1, 7, B("campfire", facing="east", lit=False, signal_fire=False, waterlogged=False))   # inglenook
    b.fill(4, 2, 6, 4, 3, 8, brick)
    P.sofa(b, 6, 7, 1, 9, "back", "spruce")
    P.armchair(b, 6, 1, 6, "front")
    P.rug(b, 5, 5, 8, 8, 1, "brown")
    P.table(b, 13, 1, 7, top="dark_oak_pressure_plate", leg="dark_oak_fence")
    P.kitchen_run(b, 11, 15, 1, 14, facing="front")
    P.table(b, 7, 1, 13)
    P.bookcase(b, 4, 1, 14)
    b.fill(10, 5, 6, 10, 7, 14, PLASTER)
    b.fill(4, 5, 10, 9, 7, 10, PLASTER)
    for (u, v) in ((10, 8), (10, 12)):
        opening(b, u, 5, v, u, 6, v)
    P.bed(b, 6, 5, 6, "white", "front")
    P.bed(b, 6, 5, 14, "light_blue", "back")
    P.bed(b, 13, 5, 14, "red", "back")
    P.bath(b, 12, 14, 5, 6)
    P.wardrobe(b, 8, 5, 6)
    # garden: terrace, lawn, pergola, apple tree, rose bed, fence
    P.paving(b, 4, 16, 16, 17, "mossy_stone_bricks")
    for u in (5, 8, 11, 14):
        b.fill(u, 1, 21, u, 2, 21, B("spruce_fence"))
    b.fill(5, 3, 21, 14, 3, 21, slab("spruce"))
    flowers(b, [(u, 22) for u in range(5, 15)])
    P.tree(b, 17, 23, h=3, r=2)
    b.fill(0, 1, D - 1, W - 1, 1, D - 1, B("spruce_fence"))
    lights(b, [(6, 3, 7), (6, 3, 12), (13, 3, 7), (13, 3, 12), (6, 7, 7), (6, 7, 12), (13, 7, 7), (13, 7, 12)])
    return b


def executive_house(ident="executive_house", name="Executive Detached House"):
    W, D = 24, 28
    b = Build(ident, name, "Houses", W, 16, D,
              "A large modern five-bedroom house: buff brick and white render, a tall glazed gable, grey windows, "
              "a double garage with EV charger, a block-paved drive and a big back garden.")
    brick, render, roof, win = STOCK, "ptmuk:white_render", SLATE, "ptmuk:grey_framed_window"
    P.lawn(b, 0, 0, W - 1, D - 1)
    P.paving(b, 16, 0, 22, 6, "ptmuk:block_paving_grey")
    P.paving(b, 10, 0, 10, 6, "ptmuk:block_paving_grey")
    # main house u2..15 v7..18, gable wing u2..7 forward to v5
    b.fill(2, 0, 5, 15, 0, 18, "smooth_stone")
    b.walls(2, 1, 7, 15, 7, 18, brick)
    b.walls(2, 1, 5, 7, 7, 7, render)
    b.fill(3, 4, 6, 14, 4, 17, "spruce_planks")
    b.fill(3, 1, 7, 6, 3, 7, AIR)                       # the gable wing opens into the rooms behind it
    b.fill(3, 5, 7, 6, 6, 7, AIR)
    b.window(3, 2, 5, 4, 2, win)
    b.window(3, 5, 5, 4, 3, win)
    b.door(10, 1, 7, "dark_oak", facing="back", hinge="left")
    b.fill(9, 1, 7, 9, 2, 7, pane(win))
    b.fill(9, 3, 6, 11, 3, 6, slab("smooth_stone", "top"))
    b.window(12, 2, 7, 3, 2, win)
    b.window(9, 5, 7, 2, 2, win)
    b.window(12, 5, 7, 3, 2, win)
    # back: bifold doors onto the patio, windows over
    b.window(4, 1, 18, 5, 2, win)
    b.window(11, 2, 18, 3, 2, win)
    for u in (4, 8, 12):
        b.window(u, 5, 18, 2, 2, win)
    b.side_window(2, 2, 12, 2, 2, win)
    b.side_window(2, 5, 12, 2, 2, win)
    # double garage u16..22 v7..14
    b.fill(16, 0, 7, 22, 0, 14, "smooth_stone")
    b.walls(16, 1, 7, 22, 3, 14, brick)
    for u in (17, 20):
        P.garage_door(b, u, u + 1, 7, 1, 2)
    b.set(16, 1, 6, B("ptmuk:ev_charger", facing="south"))
    b.door(19, 1, 14, "spruce", facing="front", hinge="left")
    # roofs
    b.gable_roof(1, 16, 6, 19, 8, roof)
    P.close_gable(b, 2, 7, 18, 8, brick)
    P.close_gable(b, 15, 7, 18, 8, brick)
    b.gable_roof_side(1, 8, 4, 11, 8, roof)
    P.close_gable_front(b, 5, 2, 7, 8, render)
    b.window(4, 8, 5, 2, 2, win)
    b.gable_roof_side(15, 23, 6, 15, 4, roof)
    P.close_gable_front(b, 7, 16, 22, 4, brick)
    P.close_gable_front(b, 14, 16, 22, 4, brick)
    b.chimney(15, 12, 8, 13, brick, w=1, d=1, pots=1)
    # inside: study in the wing, lounge front right, open-plan kitchen family room at the back
    b.fill(8, 1, 8, 8, 3, 12, PLASTER)
    b.fill(3, 1, 12, 14, 3, 12, PLASTER)
    opening(b, 8, 1, 10, 8, 2, 10)
    opening(b, 6, 1, 12, 7, 2, 12)
    opening(b, 11, 1, 12, 12, 2, 12)
    for i in range(4):
        b.set(9, 1 + i, 8 + i, stairs("oak", "back"))
    opening(b, 9, 4, 8, 9, 4, 10)
    b.set(4, 1, 8, stairs("dark_oak", "back"))
    b.set(4, 1, 7, B("spruce_planks"))
    P.bookcase(b, 3, 1, 10)
    P.sofa(b, 12, 14, 1, 11, "back", "spruce")
    P.tv(b, 13, 1, 8)
    P.rug(b, 11, 8, 14, 10, 1, "light_gray")
    P.kitchen_run(b, 13, 17, 1, 14, facing="left", side=True)
    for u in (9, 10, 11):
        b.set(u, 1, 15, "smooth_quartz")
        b.set(u, 2, 15, slab("smooth_quartz"))
    P.table(b, 5, 1, 15)
    P.table(b, 6, 1, 15)
    b.set(5, 1, 14, stairs("oak", "front"))
    b.set(6, 1, 16, stairs("oak", "back"))
    # upstairs: bedrooms either side of the landing, bathroom
    b.fill(8, 5, 8, 8, 7, 17, PLASTER)
    b.fill(3, 5, 12, 14, 7, 12, PLASTER)
    for (u, v) in ((8, 10), (8, 14), (11, 12)):
        opening(b, u, 5, v, u, 6, v)
    P.bed(b, 4, 5, 8, "white", "front")
    P.bed(b, 12, 5, 8, "light_gray", "front")
    P.bed(b, 4, 5, 17, "blue", "back")
    P.bed(b, 12, 5, 17, "lime", "back")
    P.bath(b, 9, 10, 5, 17)
    # garden
    P.paving(b, 2, 19, 15, 21, "ptmuk:paving_slabs")
    b.fill(0, 1, 19, 0, 2, D - 1, B("spruce_fence"))
    b.fill(W - 1, 1, 15, W - 1, 2, D - 1, B("spruce_fence"))
    b.fill(0, 1, D - 1, W - 1, 2, D - 1, B("spruce_fence"))
    P.tree(b, 19, 24, h=3, r=2, leaves="birch_leaves", log="birch_log")
    P.tree(b, 4, 2, h=2, r=1, leaves="azalea_leaves")
    bench(b, 6, 21, "back")
    b.set(23, 1, 16, B("ptmuk:wheelie_bin_grey", facing="west"))
    b.set(23, 1, 17, B("ptmuk:wheelie_bin_blue", facing="west"))
    lights(b, [(4, 3, 9), (12, 3, 9), (5, 3, 15), (11, 3, 15), (4, 7, 9), (12, 7, 9), (4, 7, 15), (12, 7, 15), (19, 3, 10)])
    return b


def tudorbethan_detached(ident="thirties_detached", name="1930s Detached House (Mock Tudor)"):
    W, D = 20, 26
    b = Build(ident, name, "Houses", W, 17, D,
              "A 1930s detached house: red brick, a mock-Tudor timbered gable over a two-storey bay, leaded lights, "
              "an arched porch, a side garage and a hipped clay tile roof.")
    brick, roof, timber, win = RED, CLAY, "stripped_dark_oak_log", "ptmuk:leaded_window"
    P.lawn(b, 0, 0, W - 1, D - 1)
    P.paving(b, 15, 0, 18, 6, "ptmuk:block_paving_red")
    b.fill(10, 0, 0, 10, 0, 4, "ptmuk:block_paving_red")
    P.hedge(b, 0, 9, 0)
    P.hedge(b, 11, 14, 0)
    # house u2..14 v5..15, bay u3..7 forward to v4
    b.fill(2, 0, 4, 14, 0, 15, "oak_planks")
    b.walls(2, 1, 5, 14, 7, 15, brick)
    b.fill(3, 4, 6, 13, 4, 14, "oak_planks")
    for u in range(3, 8):
        b.set(u, 0, 4, "oak_planks")
        b.set(u, 1, 4, brick)
        b.fill(u, 2, 4, u, 3, 4, pane(win))
        b.set(u, 4, 4, brick)
        b.fill(u, 5, 4, u, 6, 4, pane(win))
        b.set(u, 7, 4, B(timber, axis="x"))
        if 3 < u < 7:                                      # the end windows turn back into the wall
            opening(b, u, 1, 5, u, 3, 5)
            opening(b, u, 5, 5, u, 6, 5)
    # arched porch and door
    for u in (9, 11):
        b.fill(u, 1, 4, u, 2, 4, brick)
    b.set(9, 3, 4, stairs(brick, "left", "top"))
    b.set(11, 3, 4, stairs(brick, "right", "top"))
    b.set(10, 3, 4, slab(brick, "top"))
    opening(b, 10, 1, 5, 10, 3, 5)
    b.door(10, 1, 6, "oak", facing="back", hinge="left")
    b.set(10, 3, 6, PLASTER)                               # door head; a fanlight here would open into the hall
    b.window(12, 2, 5, 2, 2, win)
    b.window(10, 5, 5, 1, 2, win)
    b.window(12, 5, 5, 2, 2, win)
    for v in (9, 12):
        b.side_window(2, 2, v, 1, 2, win)
        b.side_window(2, 5, v, 1, 2, win)
    for u in (4, 5, 11, 12):
        b.window(u, 2, 15, 1, 2, "ptmuk:upvc_window")
        b.window(u, 5, 15, 1, 2, "ptmuk:upvc_window")
    b.door(8, 1, 15, "spruce", facing="front", hinge="left")
    # side garage with a flat roof
    b.fill(15, 0, 7, 18, 0, 14, "smooth_stone")
    b.walls(15, 1, 7, 18, 3, 14, brick)
    P.garage_door(b, 16, 17, 7, 1, 2, kind="oak_garage_door")
    b.fill(15, 4, 7, 18, 4, 14, slab("smooth_stone"))
    b.door(14, 1, 12, "spruce", facing="right", hinge="left")      # from the house into the garage
    b.fill(15, 1, 12, 15, 2, 12, AIR)
    # roofs: hipped main roof, timbered gable over the bay
    b.hipped_roof(1, 15, 4, 16, 8, roof)
    b.gable_roof_side(2, 8, 3, 9, 8, roof)
    P.close_gable_front(b, 4, 3, 7, 8, PLASTER)
    for u in range(3, 8):
        for y in range(8, 12):
            if b.get(u, y, 4).name == "minecraft:calcite" and u % 2 == 1:
                b.set(u, y, 4, B(timber, axis="y"))
    b.chimney(9, 10, 9, 14, brick, w=1, d=2, pots=2)
    # inside
    b.fill(8, 1, 6, 8, 3, 14, PLASTER)
    b.fill(3, 1, 10, 7, 3, 10, PLASTER)
    for (u, v) in ((8, 8), (8, 12), (5, 10)):
        opening(b, u, 1, v, u, 2, v)
    for i in range(4):
        b.set(9, 1 + i, 9 + i, stairs("oak", "back"))
    opening(b, 9, 4, 9, 9, 4, 11)
    P.sofa(b, 4, 6, 1, 9, "back", "dark_oak")
    b.set(3, 1, 7, B("blast_furnace", facing="east", lit=False))
    P.rug(b, 4, 5, 6, 8, 1, "green")
    P.kitchen_run(b, 11, 13, 1, 14, facing="front")
    P.table(b, 5, 1, 12)
    b.fill(8, 5, 6, 8, 7, 14, PLASTER)
    b.fill(3, 5, 10, 7, 7, 10, PLASTER)
    for (u, v) in ((8, 8), (8, 12)):
        opening(b, u, 5, v, u, 6, v)
    P.bed(b, 5, 5, 6, "white", "front")
    P.bed(b, 5, 5, 14, "pink", "back")
    P.bed(b, 12, 5, 6, "light_blue", "front")
    P.bath(b, 11, 13, 5, 14)
    b.fill(0, 1, 16, 0, 1, D - 1, B("oak_fence"))
    b.fill(W - 1, 1, 15, W - 1, 1, D - 1, B("oak_fence"))
    b.fill(0, 1, D - 1, W - 1, 1, D - 1, B("oak_fence"))
    P.tree(b, 15, 21, h=3, r=2)
    b.set(14, 1, 16, B("ptmuk:wheelie_bin_black", facing="west"))
    lights(b, [(5, 3, 7), (5, 3, 12), (11, 3, 8), (11, 3, 12), (5, 7, 8), (5, 7, 12), (11, 7, 8), (11, 7, 12)])
    return b


def country_house(ident="georgian_country_house", name="Georgian Country House"):
    W, D = 41, 46
    b = Build(ident, name, "Houses", W, 26, D,
              "A Georgian country house in Portland stone: three storeys, nine bays, a columned portico with a pediment, "
              "a balustraded parapet, two pavilion wings, a gravel carriage sweep with a fountain and formal gardens.")
    stone, trim, roof, win = STONE, "smooth_quartz", SLATE, "ptmuk:georgian_window"
    balustrade = B("sandstone_wall", up=True, north="none", south="none", east="none", west="none", waterlogged=False)
    P.lawn(b, 0, 0, W - 1, D - 1)
    # front boundary wall with railings and gate piers topped with lanterns
    for u in range(W):
        if 17 <= u <= 23:
            continue
        b.set(u, 1, 0, stone)
        b.set(u, 2, 0, B("iron_bars"))
    for u in (16, 24):
        b.fill(u, 1, 0, u, 3, 0, stone)
        b.set(u, 4, 0, B("lantern", hanging=False, waterlogged=False))
    # gravel carriage sweep round an oval lawn with a fountain
    b.fill(4, 0, 1, 36, 0, 12, "gravel")
    for u in range(14, 27):
        for v in range(3, 9):
            if ((u - 20) / 6.5) ** 2 + ((v - 5.5) / 3.2) ** 2 <= 1:
                b.set(u, 0, v, "grass_block")
    # fountain: a sunken basin with a stone kerb all round, water in the middle
    b.fill(18, 0, 4, 22, 0, 7, "stone_bricks")
    b.walls(18, 1, 4, 22, 1, 7, B("stone_brick_wall", up=True, north="none", south="none", east="none", west="none",
                                   waterlogged=False))
    b.fill(19, 0, 5, 21, 0, 6, "water")
    b.fill(20, 1, 5, 20, 1, 6, "stone_bricks")
    b.set(20, 2, 5, B("stone_brick_wall", up=True, north="none", south="none", east="none", west="none", waterlogged=False))
    b.fill(17, 0, 13, 23, 0, 13, "gravel")
    # main block u7..33, v14..29: floors at 5 and 10, cornice at 14, parapet above
    b.fill(7, 0, 14, 33, 0, 29, "polished_diorite")
    for u in range(8, 33):
        for v in range(15, 29):
            if (u + v) % 2 == 0:
                b.set(u, 0, v, "polished_andesite")
    b.walls(7, 1, 14, 33, 14, 29, stone)
    for y in (5, 10):
        b.fill(8, y, 15, 32, y, 28, "oak_planks")
    b.fill(7, 5, 13, 33, 5, 13, slab(trim, "top"))
    b.fill(7, 10, 13, 33, 10, 13, slab(trim, "top"))
    b.fill(8, 14, 15, 32, 14, 28, "spruce_planks")
    b.fill(6, 14, 13, 34, 14, 13, stairs(trim, "back", "top"))
    b.walls(7, 15, 14, 33, 15, 29, balustrade)
    b.walls(7, 16, 14, 33, 16, 29, slab("smooth_sandstone"))
    for u in range(7, 34, 4):
        b.set(u, 15, 14, stone)
        b.set(u, 15, 29, stone)
    b.hipped_roof(8, 32, 15, 28, 15, roof)
    for (u, v) in ((10, 18), (30, 18), (10, 25), (30, 25)):
        b.chimney(u, v, 16, 21, stone, w=1, d=1, pots=2)
    # nine bays of tall sash windows, the door in the middle under the portico
    for u in (9, 12, 15, 17, 20, 23, 25, 28, 31):
        if u != 20:
            b.window(u, 2, 14, 1, 3, win)
        b.window(u, 6, 14, 1, 4, win)
        b.window(u, 11, 14, 1, 2, win)
        b.window(u, 2, 29, 1, 3, win)
        b.window(u, 6, 29, 1, 4, win)
        b.window(u, 11, 29, 1, 2, win)
    b.door(20, 1, 14, "dark_oak", facing="back", hinge="left")
    b.fill(20, 3, 14, 20, 4, 14, pane(win))
    # portico: four giant columns, entablature and pediment, steps up
    for u in (16, 18, 22, 24):
        b.fill(u, 1, 10, u, 9, 10, B("quartz_pillar", axis="y"))
    b.fill(15, 10, 9, 25, 10, 13, trim)
    b.gable_roof_side(15, 25, 9, 13, 11, trim)
    for v in range(9, 14):                             # a solid pediment, not a hollow one
        P.close_gable_front(b, v, 16, 24, 11, trim)
    b.fill(16, 0, 10, 24, 0, 13, "smooth_quartz")
    b.fill(17, 0, 8, 23, 0, 8, stairs(trim, "back"))
    b.fill(17, 0, 9, 23, 0, 9, "smooth_quartz")
    # pavilion wings
    for (a, z) in ((1, 6), (34, 39)):
        b.walls(a, 1, 17, z, 6, 26, stone)
        b.fill(a + 1, 0, 18, z - 1, 0, 25, "polished_diorite")
        b.fill(a, 7, 16, z, 7, 27, slab(trim))
        b.hipped_roof(a, z, 17, 26, 8, roof)
        for v in (19, 22, 25):
            b.side_window(a if a == 1 else z, 2, v, 1, 3, win)
        b.window((a + z) // 2, 2, 17, 1, 3, win)
        link = 7 if a == 1 else 33
        opening(b, link, 1, 21, link, 2, 22)
        opening(b, z if a == 1 else a, 1, 21, z if a == 1 else a, 2, 22)
    # inside: entrance hall with a double staircase, drawing room, library, dining room, bedrooms
    for u in (14, 26):
        b.fill(u, 1, 15, u, 4, 28, PLASTER)
        b.fill(u, 6, 15, u, 9, 28, PLASTER)
        for y in (1, 6):
            opening(b, u, y, 18, u, y + 1, 18)
            opening(b, u, y, 25, u, y + 1, 25)
    for u in (16, 24):
        for i in range(5):
            b.set(u, 1 + i, 22 + i, stairs(trim, "back"))
        opening(b, u, 5, 22, u, 5, 25)              # stairwell: the top step sits in the floor
    b.fill(17, 6, 27, 23, 6, 27, B("iron_bars"))
    P.flight(b, 9, 6, 28, 1, 0, 5, trim)               # back stairs up to the second floor
    b.set(20, 4, 21, B("lantern", hanging=True, waterlogged=False))
    b.set(20, 9, 21, B("lantern", hanging=True, waterlogged=False))
    P.rug(b, 18, 16, 22, 26, 1, "red")
    b.set(8, 1, 21, B("blast_furnace", facing="east", lit=False))
    b.fill(8, 2, 21, 8, 3, 21, "smooth_quartz")
    P.sofa(b, 10, 12, 1, 19, "front", "dark_oak")
    P.sofa(b, 10, 12, 1, 24, "back", "dark_oak")
    P.rug(b, 10, 20, 12, 23, 1, "blue")
    for (u, v) in ((11, 17), (11, 26)):
        b.set(u, 4, v, B("lantern", hanging=True, waterlogged=False))
    for v in range(15, 29):
        if v not in (21, 22):                          # leave the doorway through to the east wing
            P.bookcase(b, 32, 1, v, 3)
    for u in range(27, 32):
        P.bookcase(b, u, 1, 28, 3)
    P.table(b, 29, 1, 21, top="dark_oak_pressure_plate", leg="dark_oak_fence")
    P.armchair(b, 29, 1, 22, "back")
    b.set(29, 4, 21, B("lantern", hanging=True, waterlogged=False))
    for v in range(18, 26):
        P.table(b, 11, 6, v, top="dark_oak_pressure_plate", leg="dark_oak_fence")
        b.set(10, 6, v, stairs("dark_oak", "left"))
        b.set(12, 6, v, stairs("dark_oak", "right"))
    b.set(11, 9, 21, B("lantern", hanging=True, waterlogged=False))
    P.bed(b, 29, 6, 16, "purple", "front")
    P.bed(b, 29, 6, 27, "cyan", "back")
    P.bed(b, 11, 11, 16, "white", "front")
    P.bed(b, 29, 11, 16, "red", "front")
    P.bed(b, 29, 11, 27, "blue", "back")
    # back terrace with balustrade, formal garden with clipped hedges, topiary and trees
    b.fill(7, 0, 30, 33, 0, 32, "smooth_stone")
    b.fill(7, 1, 33, 33, 1, 33, balustrade)
    b.set(20, 1, 33, AIR)
    b.fill(20, 0, 33, 20, 0, D - 1, "gravel")
    b.door(20, 1, 29, "dark_oak", facing="front", hinge="left")
    for (u0, u1) in ((8, 17), (23, 32)):
        P.hedge(b, u0, u1, 36)
        P.hedge(b, u0, u1, 42)
        for v in range(37, 42):
            b.set(u0, 1, v, leaves())
            b.set(u1, 1, v, leaves())
    for (u, v) in ((3, 36), (37, 36), (3, 43), (37, 43), (2, 6), (38, 6)):
        P.tree(b, u, v, h=5, r=2)
    for (u, v) in ((12, 39), (28, 39)):
        b.fill(u, 1, v, u, 2, v, leaves())
        b.set(u, 3, v, leaves("azalea_leaves"))
    lights(b, [(11, 4, 17), (11, 4, 26), (29, 4, 17), (29, 4, 26), (20, 4, 17),
               (11, 9, 17), (29, 9, 20), (29, 9, 25), (20, 9, 26),
               (11, 13, 18), (11, 13, 25), (29, 13, 18), (29, 13, 25), (20, 13, 21),
               (3, 5, 21), (37, 5, 21)], level=13)
    return b


def thatched_cottage(ident="thatched_cottage", name="Thatched Cottage"):
    W, D = 15, 20
    b = Build(ident, name, "Houses", W, 13, D,
              "A whitewashed country cottage under a thick thatch with eyebrow windows, a big brick chimney, "
              "a picket fence and a cottage garden full of flowers.")
    render, thatch, brick, win = "ptmuk:white_render", THATCH, RED, "ptmuk:leaded_window"
    P.lawn(b, 0, 0, W - 1, D - 1)
    # picket fence and gate, a cobbled path, flower beds
    b.fill(0, 1, 0, W - 1, 1, 0, B("birch_fence"))
    b.set(7, 1, 0, B("birch_fence_gate", facing="south", open=False, in_wall=False, powered=False))
    b.fill(7, 0, 0, 7, 0, 3, "mossy_cobblestone")
    flowers(b, [(u, v) for u in range(1, 6) for v in (1, 2)] + [(u, v) for u in range(9, 14) for v in (1, 2)])
    # cottage u2..12, v4..9: walls y1..4, the bedroom up in the thatch
    b.fill(2, 0, 4, 12, 0, 9, "stone_bricks")
    b.walls(2, 1, 4, 12, 4, 9, render)
    b.fill(3, 4, 5, 11, 4, 8, "spruce_planks")
    for u in (4, 7, 10):
        b.fill(u, 4, 5, u, 4, 8, B("stripped_oak_log", axis="z"))
    b.gable_roof(1, 13, 3, 10, 5, thatch)
    P.close_gable(b, 2, 4, 9, 5, render)
    P.close_gable(b, 12, 4, 9, 5, render)
    # eyebrow windows in the thatch, small cottage windows below, a thatched porch
    for u in (4, 10):
        b.set(u, 5, 3, AIR)
        b.set(u, 5, 4, pane(win))
        b.set(u, 6, 3, stairs(thatch, "back"))
        b.set(u - 1, 5, 4, thatch)                         # packed thatch either side, so the glass joins on
        b.set(u + 1, 5, 4, thatch)
        b.fill(u - 1, 2, 4, u, 3, 4, pane(win))
    b.door(7, 1, 4, "spruce", facing="back", hinge="left")
    for u in (6, 8):
        b.fill(u, 1, 3, u, 3, 3, B("oak_fence"))
    b.fill(6, 4, 2, 8, 4, 2, stairs(thatch, "back"))
    b.fill(6, 4, 3, 8, 4, 3, slab(thatch, "top"))
    b.window(5, 2, 9, 1, 2, win)
    b.window(9, 2, 9, 1, 2, win)
    b.door(7, 1, 9, "spruce", facing="front", hinge="left")
    b.side_window(12, 2, 6, 2, 1, win)
    b.side_window(12, 5, 6, 2, 1, win)
    # big brick chimney outside the gable end
    b.fill(1, 1, 6, 1, 10, 7, brick)
    b.fill(1, 11, 6, 1, 11, 7, B("flower_pot"))
    # inside: inglenook, flagstones, beams, table, dresser, armchair; bedroom under the eaves
    b.fill(3, 0, 5, 11, 0, 8, "polished_andesite")
    b.set(3, 1, 6, B("campfire", facing="east", lit=False, signal_fire=False, waterlogged=False))
    b.fill(3, 2, 6, 3, 3, 7, brick)
    b.set(3, 1, 7, brick)
    P.table(b, 9, 1, 7)
    b.set(9, 1, 6, stairs("oak", "back"))
    b.set(9, 1, 8, stairs("oak", "front"))
    b.fill(11, 1, 5, 11, 2, 5, B("barrel", facing="west", open=False))
    P.armchair(b, 5, 1, 7, "left")
    P.rug(b, 4, 5, 6, 8, 1, "red")
    for i in range(4):                                   # cottage stair along the back wall, up into the loft
        b.set(11 - i, 1 + i, 8, stairs("oak", "left"))
        P.clear_over(b, 11 - i, 1 + i, 8, 3 if i < 3 else 2)
    P.bed(b, 5, 5, 6, "white", "back")
    b.set(9, 5, 7, B("barrel", facing="south", open=False))
    # back garden: vegetable beds, compost bin, apple tree, fences
    for u in range(2, 7):
        for v in range(13, 17):
            b.set(u, 0, v, "rooted_dirt" if (u + v) % 2 else "coarse_dirt")
        b.set(u, 1, 14, B("fern"))
    b.set(10, 1, 15, B("composter", level=3))
    P.tree(b, 11, 17, h=3, r=2, leaves="flowering_azalea_leaves")
    b.fill(0, 1, 1, 0, 1, D - 1, B("birch_fence"))
    b.fill(W - 1, 1, 1, W - 1, 1, D - 1, B("birch_fence"))
    b.fill(0, 1, D - 1, W - 1, 1, D - 1, B("birch_fence"))
    lights(b, [(6, 3, 6), (9, 3, 7), (7, 7, 6)])
    return b


# ================================================================== multi-storey flats

def slab_block(ident="slab_block", name="1960s Slab Block (12 Storeys)", storeys=12):
    W, D = 45, 18
    top = PD.STOREY * storeys
    b = Build(ident, name, "Flats", W, top + 6, D,
              f"A long {storeys}-storey 1960s council slab block: precast concrete, rows of balconies, blank end walls, "
              "two stair cores, a glazed entrance and lift motor rooms on the roof.")
    conc, panel = "light_gray_concrete", "white_concrete"
    P.paving(b, 0, 0, W - 1, D - 1)
    b.fill(1, 0, 3, 43, 0, 15, "polished_andesite")
    b.walls(1, 1, 3, 43, top, 15, conc)
    floors = list(range(0, top + 1, PD.STOREY))
    for y in floors[1:-1]:
        b.fill(2, y, 4, 42, y, 14, "smooth_stone")
        b.walls(1, y, 3, 43, y, 15, "polished_andesite")
    for y in floors[1:-1]:
        for k in range(7):                            # seven flats a floor, six blocks each
            a = 2 + 6 * k
            b.window(a, y + 1, 3, 2, 2, "ptmuk:upvc_window")
            b.fill(a + 2, y + 1, 3, a + 2, y + 2, 3, pane("ptmuk:upvc_window"))
            b.fill(a + 3, y + 1, 3, a + 4, y + 2, 3, panel)
            b.fill(a, y, 2, a + 4, y, 2, slab("smooth_stone", "top"))
            b.fill(a, y + 1, 2, a + 4, y + 1, 2, pane("white_stained_glass_pane"))
            b.set(a + 5, y + 1, 2, conc)
            b.window(a + 1, y + 1, 15, 3, 2, "ptmuk:upvc_window")
            lights(b, [(a + 2, y + 3, 7), (a + 2, y + 3, 12)], level=11)
    for u in (1, 43):                                  # blank end walls with a white stripe
        b.fill(u, 1, 9, u, top, 9, panel)
    # ground floor: glazed entrance in the middle, bin store doors, two cores
    b.fill(18, 1, 3, 26, 2, 3, pane("ptmuk:shop_window"))
    b.door(22, 1, 3, "birch", facing="back", hinge="left")
    b.fill(17, 3, 2, 27, 3, 2, slab("smooth_stone", "top"))
    PD.name_plate(b, 19, 25, 4, 2, "HAWTHORN HOUSE", f"Flats 1 - {7 * (storeys - 1)}", 0xFFF2F0EA, 0xFF121214, False)
    for u in (4, 10, 32, 38):
        b.door(u, 1, 3, "iron", facing="back", hinge="left")
    for cu in (12, 31):
        P.stair_core(b, cu, 6, floors[:-1], material="stone_brick")
    P.flat_roof(b, 1, 43, 3, 15, top, deck="gray_concrete", parapet=conc, coping="smooth_stone")
    for cu in (12, 31):
        b.fill(cu - 1, top + 1, 6, cu + 2, top + 3, 11, conc)
        b.set(cu, top + 4, 8, B("lightning_rod", facing="up", powered=False, waterlogged=False))
    for u in (3, 41):
        b.set(u, 1, 1, B("ptmuk:communal_bin", facing="south"))
    return b


def residential_tower(ident="residential_tower", name="Modern Residential Tower (24 Storeys)", storeys=24):
    W, D = 23, 24
    base = 8
    top = base + PD.STOREY * storeys
    b = Build(ident, name, "Flats", W, top + 5, D,
              f"A {storeys}-storey 2010s apartment tower: a double-height glass lobby, alternating glazing and pale "
              "cladding, glass balconies, a glazed penthouse floor and a lit crown.")
    clad, dark, win = "white_concrete", "polished_deepslate", "ptmuk:grey_framed_window"
    P.paving(b, 0, 0, W - 1, D - 1)
    for u in (1, 21):
        for v in (1, 22):
            b.set(u, 0, v, "grass_block")
            b.set(u, 1, v, leaves("azalea_leaves"))
    x0, x1, z0, z1 = 2, 20, 3, 21
    b.fill(x0, 0, z0, x1, 0, z1, "polished_andesite")
    # podium: double-height glazed lobby
    b.walls(x0, 1, z0, x1, base - 1, z1, pane("ptmuk:office_glazing"))
    for (u, v) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        b.fill(u, 1, v, u, top, v, dark)
    b.door(11, 1, z0, "birch", facing="back", hinge="left")
    b.fill(7, base, z0 - 1, 15, base, z0 - 1, slab("smooth_stone", "top"))
    PD.name_plate(b, 8, 14, base - 1, z0 - 1, "THE BELVEDERE", "", 0xFF2A2E34, 0xFFFFFFFF, True)
    floors = [0] + [base + PD.STOREY * i for i in range(storeys + 1)]
    for i, y in enumerate(floors[1:-1]):
        penthouse = i == storeys - 1
        b.fill(x0 + 1, y, z0 + 1, x1 - 1, y, z1 - 1, "light_gray_concrete")
        b.walls(x0, y, z0, x1, y, z1, dark if i == 0 else clad)
        for (side_v, step) in ((z0, -1), (z1, 1)):
            for u in range(x0 + 1, x1):
                solid = (u - x0) % 4 == 0 and not penthouse
                b.fill(u, y + 1, side_v, u, y + 3, side_v, clad if solid else pane(win))
            if not penthouse and i % 2 == 0:
                for (a, z) in ((x0 + 2, x0 + 6), (x1 - 6, x1 - 2)):
                    b.fill(a, y, side_v + step, z, y, side_v + step, slab("smooth_stone", "top"))
                    b.fill(a, y + 1, side_v + step, z, y + 1, side_v + step, pane("glass_pane"))
        for side_u in (x0, x1):
            for v in range(z0 + 1, z1):
                solid = (v - z0) % 4 == 0 and not penthouse
                b.fill(side_u, y + 1, v, side_u, y + 3, v, clad if solid else pane(win))
        lights(b, [(5, y + 3, 6), (17, y + 3, 6), (5, y + 3, 18), (17, y + 3, 18)], level=11)
    P.stair_core(b, 10, 9, floors[:-1], material="stone_brick")
    # crown: a lit band and a plant screen
    P.flat_roof(b, x0, x1, z0, z1, top, deck="gray_concrete", parapet=None)
    b.walls(x0, top + 1, z0, x1, top + 1, z1, "sea_lantern")
    b.walls(x0, top + 2, z0, x1, top + 3, z1, dark)
    b.fill(8, top + 1, 9, 14, top + 3, 15, "light_gray_concrete")
    for (u, v) in ((3, 1), (19, 1)):
        b.set(u, 1, v, B("ptmuk:bike_stand", facing="south"))
    for u in (6, 16):
        b.set(u, 1, 1, B("ptmuk:bench_metal", facing="south"))
    return b


def courtyard_block(ident="courtyard_apartments", name="Courtyard Apartment Block (7 Storeys)"):
    W, D = 37, 35
    b = Build(ident, name, "Flats", W, 30, D,
              "Seven storeys of 2010s flats round a private garden courtyard: stock brick with a dark plinth, "
              "Juliet balconies, a gated archway, and a set-back top floor with roof terraces.")
    brick, plinth, top_clad, win = STOCK, "polished_deepslate", "gray_concrete", "ptmuk:grey_framed_window"
    P.paving(b, 0, 0, W - 1, 1)
    o0, o1, p0, p1 = 1, 35, 2, 33            # outer ring
    i0, i1, q0, q1 = 11, 25, 12, 23          # courtyard
    floors = [PD.STOREY * i for i in range(7)]
    roof = floors[-1]                        # the set-back top floor stands on the roof
    for u in range(o0, o1 + 1):
        for v in range(p0, p1 + 1):
            if i0 <= u <= i1 and q0 <= v <= q1:
                continue
            b.set(u, 0, v, "polished_andesite")
            for y in floors[1:]:
                b.set(u, y, v, "light_gray_concrete")
    b.walls(o0, 1, p0, o1, roof - 1, p1, brick)
    b.walls(i0 - 1, 1, q0 - 1, i1 + 1, roof - 1, q1 + 1, brick)
    b.walls(o0, 1, p0, o1, 3, p1, plinth)
    for y in floors[1:-1]:
        for u in range(o0 + 2, o1 - 1, 3):
            for (v, step) in ((p0, -1), (p1, 1)):
                b.window(u, y + 1, v, 1, 2, win)
                b.set(u, y + 1, v + step, B("iron_bars"))
        for v in range(p0 + 2, p1 - 1, 3):
            for (u, step) in ((o0, -1), (o1, 1)):
                b.side_window(u, y + 1, v, 1, 2, win)
                b.set(u + step, y + 1, v, B("iron_bars"))
        for u in range(i0, i1 + 1, 3):
            b.window(u, y + 1, q0 - 1, 1, 2, win)
            b.window(u, y + 1, q1 + 1, 1, 2, win)
        for v in range(q0, q1 + 1, 3):
            b.side_window(i0 - 1, y + 1, v, 1, 2, win)
            b.side_window(i1 + 1, y + 1, v, 1, 2, win)
        lights(b, [(5, y + 3, 6), (31, y + 3, 6), (5, y + 3, 28), (31, y + 3, 28), (18, y + 3, 6), (18, y + 3, 28),
                   (5, y + 3, 17), (31, y + 3, 17)], level=11)
    for u in range(o0 + 2, o1 - 1, 3):
        b.window(u, 1, p0, 1, 2, win)
    # archway through the front range into the courtyard, with gates
    opening(b, 16, 1, p0, 20, 3, q0 - 1)
    b.fill(15, 1, p0, 15, 3, q0 - 1, brick)               # the passage has walls of its own,
    b.fill(21, 1, p0, 21, 3, q0 - 1, brick)               # with an entrance hall door either side
    b.door(15, 1, 7, "dark_oak", facing="left", hinge="left")
    b.door(21, 1, 7, "dark_oak", facing="right", hinge="right")
    lights(b, [(18, 3, 4), (18, 3, 9)], level=12)
    b.fill(16, 0, p0, 20, 0, q0 - 1, "ptmuk:paving_slabs")
    b.fill(16, 1, p0, 20, 3, p0, B("iron_bars"))
    b.door(18, 1, p0, "iron", facing="back", hinge="left")
    PD.name_plate(b, 15, 21, 4, p0 - 1, "ALBION COURT", "", 0xFF2A2E34, 0xFFFFFFFF, True)
    # set-back top floor with roof terraces behind a glass balustrade
    b.walls(o0, roof, p0, o1, roof, p1, slab("smooth_stone", "top"))
    b.walls(o0, roof + 1, p0, o1, roof + 1, p1, pane("glass_pane"))
    b.walls(o0 + 2, roof + 1, p0 + 2, o1 - 2, roof + 3, p1 - 2, top_clad)
    b.walls(i0 - 1, roof + 1, q0 - 1, i1 + 1, roof + 3, q1 + 1, top_clad)
    for u in range(o0 + 3, o1 - 2, 3):
        b.window(u, roof + 1, p0 + 2, 1, 2, win)
        b.window(u, roof + 1, p1 - 2, 1, 2, win)
    for u in range(o0 + 2, o1 - 1):
        for v in range(p0 + 2, p1 - 1):
            if not (i0 - 1 <= u <= i1 + 1 and q0 - 1 <= v <= q1 + 1):
                b.set(u, roof + 4, v, "gray_concrete")
    b.walls(o0 + 2, roof + 5, p0 + 2, o1 - 2, roof + 5, p1 - 2, slab("smooth_stone"))
    # courtyard garden: lawn, paths, trees, benches
    P.lawn(b, i0, q0, i1, q1)
    b.fill(18, 0, q0, 18, 0, q1, "ptmuk:paving_slabs")
    b.fill(i0, 0, 17, i1, 0, 17, "ptmuk:paving_slabs")
    for (u, v) in ((13, 14), (23, 14), (13, 21), (23, 21)):
        P.tree(b, u, v, h=3, r=1, leaves="birch_leaves", log="birch_log")
    for u in (16, 20):
        b.set(u, 1, 16, B("ptmuk:bench_wood", facing="south"))
    for (cu, cv) in ((7, 5), (28, 5), (7, 25), (28, 25)):
        P.stair_core(b, cu, cv, floors, material="stone_brick")
    return b


def maisonette_block(ident="maisonette_block", name="1960s Maisonette Block (4 Storeys)"):
    S = PD.STOREY
    W, D = 36, 13
    top = 4 * S
    b = Build(ident, name, "Flats", W, top + 2, D,
              "A four-storey 1960s council block of maisonettes: brick and white panels, front doors at ground level "
              "and along an access deck on the second floor, stair towers at both ends.")
    brick, panel, conc = RED, "white_concrete", "light_gray_concrete"
    deck = 2 * S
    P.paving(b, 0, 0, W - 1, D - 1)
    b.fill(4, 0, 4, 31, 0, 10, "oak_planks")
    b.walls(3, 1, 3, 32, top - 1, 11, brick)
    for y in (S, deck, 3 * S):
        b.fill(4, y, 4, 31, y, 10, "oak_planks")
    # access deck along the front on the second floor, with a solid parapet
    b.fill(4, deck, 1, 31, deck, 2, conc)
    b.fill(4, deck + 1, 1, 31, deck + 1, 1, conc)
    woods = ["oak", "spruce", "birch", "dark_oak"]
    for k in range(7):                          # seven maisonettes up and seven down, four blocks each
        a = 5 + 4 * k
        for y0 in (0, deck):                    # front: a door and window, then a full-width window above
            b.door(a, y0 + 1, 3, woods[(k + y0) % 4], facing="back", hinge="left")
            b.window(a + 1, y0 + 1, 3, 2, 2, "ptmuk:upvc_window")
            b.fill(a, y0 + 3, 3, a + 2, y0 + 3, 3, panel)
            b.window(a, y0 + S + 1, 3, 3, 2, "ptmuk:upvc_window")
            b.fill(a, y0 + S + 3, 3, a + 2, y0 + S + 3, 3, panel)
            # stairs up inside each maisonette, against the party wall
            for n in range(S):
                b.set(a + 2, y0 + 1 + n, 5 + n, stairs("oak", "back"))
            b.fill(a + 2, y0 + S, 5, a + 2, y0 + S, 7, AIR)        # stairwell through the floor above
        for y in range(0, top, S):
            b.window(a, y + 1, 11, 3, 2, "ptmuk:upvc_window")
        if k < 6:
            b.fill(a + 3, 1, 4, a + 3, top - 1, 10, brick)            # party wall
        lights(b, [(a + 1, y + 3, 6) for y in range(0, top, S)], level=11)
    # stair towers at each end, up to the deck: switchback stairs with a landing halfway
    for a in (0, 32):
        b.walls(a, 1, 1, a + 3, deck + 2, 8, conc)
        b.fill(a + 1, 0, 2, a + 2, 0, 7, "polished_andesite")
        for y in (S, deck):
            b.fill(a + 1, y, 2, a + 2, y, 7, "polished_andesite")
        b.fill(a, deck + 3, 1, a + 3, deck + 3, 8, conc)
        b.fill(a + 2, 2, 1, a + 2, deck + 2, 1, pane("glass_pane"))
        b.fill(a + 1, 4, 1, a + 1, deck + 2, 1, pane("glass_pane"))
        b.door(a + 1, 1, 1, "birch", facing="back", hinge="left")
        P.stair_core(b, a + 1, 2, [0, S, deck], material="stone_brick")
        inner = a + 3 if a == 0 else a
        opening(b, inner, deck + 1, 2, inner, deck + 2, 2)
        lights(b, [(a + 2, 3, 2), (a + 2, S + 3, 7), (a + 1, deck + 2, 5)], level=11)
    P.flat_roof(b, 3, 32, 3, 11, top, deck="gray_concrete", parapet=None)
    b.fill(3, top + 1, 3, 32, top + 1, 3, slab("smooth_stone"))
    return b


# ================================================================== estates

def _street_lights(b, spots):
    for (u, v, arm) in spots:
        P.street_light(b, u, v, arm)


def thirties_estate(ident="estate_thirties_semis", name="1930s Semi-Detached Estate (Cul-de-sac)"):
    doors = [("oak", "dark_oak"), ("spruce", "birch"), ("dark_oak", "jungle"), ("birch", "oak"), ("jungle", "spruce")]
    pairs = [PD.semi_pair(f"_p{i}", "", "thirties", doors=d) for i, d in enumerate(doors)]
    pw, pd = pairs[0].W, pairs[0].D                 # 23 x 21
    road = 11
    W = 2 * pd + road
    head0 = 2 * pw
    D = head0 + 10 + pd
    b = Build(ident, name, "Estates", W, 18, D,
              "Ten 1930s semis (five pairs) round a cul-de-sac with pavements, street lights, a turning head and "
              "corner greens with trees. The road opens onto your street at the front.")
    P.lawn(b, 0, 0, W - 1, D - 1)
    b.paste(pairs[0], 0, 0, turns=3)
    b.paste(pairs[1], 0, pw, turns=3)
    b.paste(pairs[2], pd + road, 0, turns=1)
    b.paste(pairs[3], pd + road, pw, turns=1)
    ru0, ru1 = pd, pd + road - 1
    P.road_ns(b, ru0, ru1, 0, head0 - 1)
    hu0, hu1 = ru0 - 8, ru1 + 8                      # turning head across the end of the road
    P.road_ew(b, head0, head0 + 9, hu0, hu1)
    b.fill(ru0 + 3, 0, head0, ru1 - 3, 0, head0 + 2, "ptmuk:tarmac")
    for v in range(head0 + 3, head0 + 7):
        b.set(hu0, 0, v, "ptmuk:paving_slabs")
        b.set(hu0 + 1, 0, v, "ptmuk:concrete_kerb")
        b.set(hu1, 0, v, "ptmuk:paving_slabs")
        b.set(hu1 - 1, 0, v, "ptmuk:concrete_kerb")
    b.paste(pairs[4], (W - pw) // 2, head0 + 10, turns=0)
    for (u, v) in ((4, head0 + 4), (W - 5, head0 + 4), (5, D - 6), (W - 6, D - 6)):
        P.tree(b, u, v, h=4, r=2)
    bench(b, 6, head0 + 7, "left")
    _street_lights(b, [(ru0 + 1, 6, "right"), (ru1 - 1, 18, "left"), (ru0 + 1, 30, "right"), (ru1 - 1, 42, "left"),
                       (hu0 + 3, head0 + 1, "back"), (hu1 - 3, head0 + 8, "front")])
    return b


def victorian_street(ident="estate_victorian_street", name="Victorian Terraced Street"):
    left = PD.victorian_terrace("_l", "", 7)
    right = PD.victorian_terrace("_r", "", 7, brick=RED, band=STOCK, doors=["crimson", "dark_oak", "warped", "mangrove"])
    road = 10
    side = left.D                                   # 19
    W, D = 2 * side + road, left.W                   # 48 x 43
    b = Build(ident, name, "Estates", W, 15, D,
              "A Victorian street of fourteen terraced houses facing each other: London stock brick on one side, red "
              "brick on the other, bay windows, railings, back yards and street lights. The street runs straight through.")
    b.paste(left, 0, 0, turns=3)
    b.paste(right, side + road, 0, turns=1)
    P.road_ns(b, side, side + road - 1, 0, D - 1)
    _street_lights(b, [(side + 1, 4, "right"), (side + road - 2, 16, "left"), (side + 1, 28, "right"),
                       (side + road - 2, 40, "left")])
    return b


def council_estate(ident="estate_council", name="Post-war Council Estate"):
    terraces = [PD.council_terrace(f"_c{i}", "", 4) for i in range(4)]       # 25 x 20 each
    flats = PD.deck_access_flats("_f", "")                                   # 25 x 12
    side, run, road, green = terraces[0].D, terraces[0].W, 10, 25
    W = 2 * side + 2 * road + green                                          # 85
    back = 2 * run                                                           # 50
    D = back + road + flats.D + 3
    b = Build(ident, name, "Estates", W, 15, D,
              "A 1950s council estate: sixteen houses in four terraces round a green with a playground, a loop road "
              "with street lights, a block of deck-access flats and rows of lock-up garages at the back.")
    P.lawn(b, 0, 0, W - 1, D - 1)
    lu0, ru0 = side, W - side - road                                         # left and right roads
    P.road_ew(b, back, back + road - 1, lu0, ru0 + road - 1)
    for u0 in (lu0, ru0):
        P.road_ns(b, u0, u0 + road - 1, 0, back - 1)
        b.fill(u0 + 3, 0, back, u0 + road - 4, 0, back + 2, "ptmuk:tarmac")
    for v in range(back + 3, back + road - 2):
        for (u, k) in ((lu0, 1), (ru0 + road - 1, -1)):
            b.set(u, 0, v, "ptmuk:paving_slabs")
            b.set(u + k, 0, v, "ptmuk:paving_slabs")
            b.set(u + 2 * k, 0, v, "ptmuk:concrete_kerb")
    b.paste(terraces[0], 0, 0, turns=3)
    b.paste(terraces[1], 0, run, turns=3)
    b.paste(terraces[2], W - side, 0, turns=1)
    b.paste(terraces[3], W - side, run, turns=1)
    b.paste(flats, (W - flats.W) // 2, back + road, turns=0)
    P.lock_up_garages(b, 3, back + road + 1, 5, facing="front")
    P.lock_up_garages(b, W - 19, back + road + 1, 5, facing="front")
    # the green: paths across it, a playground, trees round the edge, benches, a bin, a phone box
    g0, g1 = lu0 + road, ru0 - 1
    mid = (g0 + g1) // 2
    b.fill(g0, 0, back // 2, g1, 0, back // 2, "ptmuk:paving_slabs")
    b.fill(mid, 0, 0, mid, 0, back - 1, "ptmuk:paving_slabs")
    P.playground(b, g0 + 2, 4, g0 + 11, 13)
    for (u, v) in ((g0 + 2, 32), (g1 - 2, 32), (g0 + 2, 44), (g1 - 2, 44), (g1 - 2, 4), (g1 - 2, 15)):
        P.tree(b, u, v, h=4, r=2)
    for u in (mid - 2, mid + 2):
        b.set(u, 1, back // 2 + 1, B("ptmuk:bench_metal", facing="north"))
    b.set(mid + 1, 1, back // 2 - 1, B("ptmuk:litter_bin", facing="south"))
    b.set(g1, 1, 1, B("ptmuk:phone_box", facing="south"))
    _street_lights(b, [(lu0 + 1, 8, "right"), (lu0 + road - 2, 24, "left"), (lu0 + 1, 40, "right"),
                       (ru0 + road - 2, 8, "left"), (ru0 + 1, 24, "right"), (ru0 + road - 2, 40, "left"),
                       (W // 2, back + road - 2, "front")])
    return b


def newbuild_estate(ident="estate_newbuild", name="New-Build Estate (Cul-de-sac)"):
    looks = [dict(brick=RED, render="ptmuk:white_render", door="dark_oak", bin_colour="grey"),
             dict(brick=STOCK, render=STOCK, door="oak", bin_colour="black"),
             dict(brick="bricks", render="ptmuk:white_render", door="spruce", bin_colour="blue"),
             dict(brick=RED, render=RED, door="crimson", bin_colour="green"),
             dict(brick=STOCK, render="ptmuk:white_render", door="jungle", bin_colour="grey"),
             dict(brick="bricks", render="bricks", door="dark_oak", bin_colour="brown"),
             dict(brick=RED, render="ptmuk:white_render", door="birch", bin_colour="black"),
             dict(brick=STOCK, render=STOCK, door="mangrove", bin_colour="blue")]
    houses = [PD.newbuild_detached(f"_n{i}", "", **look) for i, look in enumerate(looks)]
    hw, hd = houses[0].W, houses[0].D                 # 16 x 21
    road = 10
    W = 2 * hd + road
    head0 = 3 * hw
    D = head0 + 10 + hd
    b = Build(ident, name, "Estates", W, 15, D,
              "Eight 2020s detached new-builds in mixed brick and render round a cul-de-sac with a block-paved turning "
              "head, pavements, street lights and young trees.")
    P.lawn(b, 0, 0, W - 1, D - 1)
    for i in range(3):
        b.paste(houses[i], 0, i * hw, turns=3)
        b.paste(houses[3 + i], hd + road, i * hw, turns=1)
    P.road_ns(b, hd, hd + road - 1, 0, head0 - 1)
    hu0, hu1 = 9, W - 10
    for u in range(hu0, hu1 + 1):
        for v in range(head0, head0 + 10):
            edge = u in (hu0, hu1) or v == head0 + 9
            b.set(u, 0, v, "ptmuk:paving_slabs" if edge else "ptmuk:block_paving_grey")
    b.paste(houses[6], hu0, head0 + 10, turns=0)
    b.paste(houses[7], hu1 - hw + 1, head0 + 10, turns=0)
    for (u, v) in ((3, head0 + 4), (W - 4, head0 + 4)):
        P.tree(b, u, v, h=3, r=1, leaves="birch_leaves", log="birch_log")
    _street_lights(b, [(hd + 1, 8, "right"), (hd + road - 2, 24, "left"), (hd + 1, 40, "right"),
                       (hu0 + 1, head0 + 8, "front")])
    return b


def tower_estate(ident="estate_tower_block", name="Council Tower Block Estate"):
    tower = PD.tower_block("_t", "", storeys=16, house_name="WILLOW HOUSE")     # 17 x 69 x 16
    blocks = [maisonette_block(f"_m{i}", "") for i in range(2)]                 # 36 x 18 x 13
    mw, md = blocks[0].W, blocks[0].D
    W = 2 * md + 30
    D = mw + 20
    b = Build(ident, name, "Estates", W, tower.H, D,
              "A 1960s council estate: a 16-storey tower block between two maisonette blocks, with a car park, "
              "a playground, lock-up garages, paths, trees and street lights.")
    P.lawn(b, 0, 0, W - 1, D - 1)
    b.paste(blocks[0], 0, 4, turns=3)
    b.paste(blocks[1], W - md, 4, turns=1)
    # car park at the front with an entrance from your street
    P.parking_bays(b, md + 1, 1, W - md - 2, 7, along="u", aisle_side="back")
    b.fill(md + 1, 0, 8, W - md - 2, 0, 11, "ptmuk:tarmac")
    P.parking_bays(b, md + 1, 12, W - md - 2, 18, along="u", aisle_side="front")
    b.fill(W // 2 - 3, 0, 0, W // 2 + 2, 0, 11, "ptmuk:tarmac")
    b.paste(tower, (W - tower.W) // 2, 21, turns=0)
    # paths, a playground and garages behind the tower, trees
    b.fill(md, 0, 19, W - md - 1, 0, 20, "ptmuk:paving_slabs")
    P.playground(b, md + 1, D - 13, md + 12, D - 3)
    P.lock_up_garages(b, W - md - 14, D - 8, 4, facing="front")
    for (u, v) in ((md + 2, 24), (W - md - 3, 24), (md + 2, 33), (W - md - 3, 33)):
        P.tree(b, u, v, h=4, r=2)
    _street_lights(b, [(md + 1, 9, "right"), (W - md - 2, 9, "left"), (W // 2, 20, "front")])
    return b


# ================================================================== catalogue

def all_big():
    import prefab_mansion
    return [
        victorian_villa(),
        arts_and_crafts(),
        executive_house(),
        tudorbethan_detached(),
        country_house(),
        prefab_mansion.mansion(),
        thatched_cottage(),
        slab_block(),
        residential_tower(),
        courtyard_block(),
        maisonette_block(),
        PD.tower_block("tower_block_24", "Council Tower Block (24 Storeys)", storeys=24, house_name="ASH COURT"),
        thirties_estate(),
        victorian_street(),
        council_estate(),
        newbuild_estate(),
        tower_estate(),
    ]
