"""A big English country house for the Prefab Tool: red brick with Portland stone dressings, white
sashes and clay tile hipped roofs, set round a block-paved courtyard with room to park and a car
care corner (a two-post lift, a workbench, tyres and oil drums). A separate four-bay coach house
garage with up-and-over doors stands on the right, joined to the house by a covered oak walkway.
Behind the house: a stone terrace, a swimming pool with a pool house, lawns, a greenhouse,
vegetable beds and trees. Coordinates as in prefab_lib (u across the front, y up with the ground
layer at 0, v from the front boundary back); the pool goes 3 below ground.
"""
from prefab_lib import AIR, B, Build, light, slab
import prefab_parts as P
import prefab_furnish as PF

STONE = P.STONE
RED = "ptmuk:red_brick"
CLAY = "ptmuk:roof_clay_tiles"
PLASTER = "calcite"
WIN = "ptmuk:sash_window"


def home(b, name, u, y, v, facing, **props):
    b.set(u, y, v, PF.home(name, facing=facing, lit=False, **props))


def tall(b, name, u, y, v, facing):
    home(b, name, u, y, v, facing, half="lower")
    home(b, name, u, y + 1, v, facing, half="upper")


def lights_at(b, spots, level=12):
    for (u, y, v) in spots:
        if b.inside(u, y, v) and b.get(u, y, v).name == "minecraft:air":
            b.set(u, y, v, light(level))


def door(b, u, y, v, facing, wood="dark_oak", hinge="left"):
    b.door(u, y, v, wood, facing=facing, hinge=hinge)


def wall_post(material, u, y0, y1, v, b):
    for y in range(y0, y1 + 1):
        b.set(u, y, v, B(material, up=True, north="none", south="none", east="none", west="none", waterlogged=False))


def lantern_post(b, u, v, h=3):
    wall_post("polished_blackstone_wall", u, 1, h, v, b)
    b.set(u, h + 1, v, B("lantern", hanging=False, waterlogged=False))


def flower(b, u, v, kind):
    b.set(u, 1, v, B(kind, half="lower"))
    b.set(u, 2, v, B(kind, half="upper"))


def carpet(b, u0, v0, u1, v1, y, colour):
    """Fitted carpet: every clear spot on a solid floor (not over the stairwell)."""
    for u in range(u0, u1 + 1):
        for v in range(v0, v1 + 1):
            if b.get(u, y, v).name == "minecraft:air" and b.get(u, y - 1, v).name == "minecraft:oak_planks":
                b.set(u, y, v, B(f"{colour}_carpet"))


def garage_door(b, u, v0, v1, y0, y1, facing, kind="green_garage_door"):
    """An up-and-over door filling the opening u x [v0, v1] x [y0, y1] in a side wall."""
    for v in range(v0, v1 + 1):
        for y in range(y0, y1 + 1):
            b.set(u, y, v, B(f"ptmuk:{kind}", facing=facing, open=False, powered=False, top=(y == y1)))


def mansion(ident="country_mansion", name="Country Mansion with Pool & Coach House"):
    W, D, H, SINK = 60, 64, 24, 3
    b = Build(ident, name, "Houses", W, H, D,
              "A big English country house: red brick, stone dressings and clay tile roofs round a block-paved "
              "courtyard with room to park and a car care corner with a lift. A separate four-bay coach house "
              "garage with up-and-over doors and a workshop, joined to the house by a covered walkway. Four "
              "bedrooms (two en-suite), a family bathroom, drawing, dining and family rooms, a study and a big "
              "kitchen. Behind: a terrace, a swimming pool and pool house, lawns, a greenhouse and an orchard.",
              sink=SINK)

    # ------------------------------------------------------------ the ground: earth underneath, lawn on top
    b.fill(0, -SINK, 0, W - 1, -1, D - 1, "dirt")
    P.lawn(b, 0, 0, W - 1, D - 1)

    # ------------------------------------------------------------ boundary: brick and railings, gate piers
    for u in range(W):
        if 25 <= u <= 33:
            continue
        b.set(u, 1, 1, RED)
        if u % 4 == 0 or u in (0, W - 1):
            b.set(u, 2, 1, RED)
            b.set(u, 3, 1, slab(STONE))
        else:
            b.set(u, 2, 1, B("iron_bars", north=False, south=False, east=False, west=False, waterlogged=False))
    for u in (24, 34):                                               # gate piers with lanterns on top
        b.fill(u, 1, 1, u, 3, 1, RED)
        b.set(u, 4, 1, STONE)
        b.set(u, 5, 1, B("lantern", hanging=False, waterlogged=False))
    for v in range(2, D):                                            # garden walls down the sides
        for u in (0, W - 1):
            b.set(u, 1, v, RED)
            b.set(u, 2, v, slab(STONE))
    P.hedge(b, 1, W - 2, D - 1, h=2, leaves="spruce_leaves")         # yew hedge along the back
    for u in range(1, W - 1):                                        # shrubs and flowers behind the railings
        if 23 <= u <= 35:
            continue
        if u % 3:
            b.set(u, 1, 2, B("oak_leaves", persistent=True, distance=1, waterlogged=False))
        else:
            flower(b, u, 2, ("rose_bush", "peony", "lilac")[(u // 3) % 3])

    # ------------------------------------------------------------ the courtyard
    b.fill(1, 0, 3, 41, 0, 20, "ptmuk:block_paving_red")
    b.fill(25, 0, 0, 33, 0, 2, "ptmuk:block_paving_red")
    b.fill(1, 0, 3, 41, 0, 3, "ptmuk:granite_setts")
    b.fill(12, 0, 21, 28, 0, 26, "ptmuk:block_paving_red")           # forecourt between the wings
    for u in range(2, 41):                                           # a charcoal border round the paving
        for v in (4, 20):
            b.set(u, 0, v, "ptmuk:block_paving_grey")
    for v in range(4, 21):
        b.set(40, 0, v, "ptmuk:block_paving_grey")
    # the turning circle: a fountain in a ring of flowers, edged with setts
    fu, fv = 21, 12
    for u in range(fu - 4, fu + 5):
        for v in range(fv - 4, fv + 5):
            d2 = (u - fu) ** 2 + (v - fv) ** 2
            if d2 <= 2:
                b.set(u, 0, v, "stone_bricks")
                b.set(u, 1, v, B("water", level=0))
            elif d2 <= 5:
                b.set(u, 0, v, "stone_bricks")
                b.set(u, 1, v, "stone_bricks")
                b.set(u, 2, v, slab("smooth_stone"))
            elif d2 <= 10:
                b.set(u, 0, v, "grass_block")
                if (u + v) % 2:
                    b.set(u, 1, v, B(("poppy", "oxeye_daisy", "cornflower", "allium")[(u * 3 + v) % 4]))
            elif d2 <= 16:
                b.set(u, 0, v, "ptmuk:granite_setts")
    b.set(fu, 1, fv, "chiseled_stone_bricks")
    wall_post("stone_brick_wall", fu, 2, 2, fv, b)
    b.set(fu, 3, fv, B("decorated_pot", facing="south", waterlogged=False))
    for (u, v) in ((12, 4), (40, 4), (2, 19), (40, 19), (13, 20), (27, 20)):
        lantern_post(b, u, v)
    # car care corner: an oil-stained pad, a two-post lift, a workbench along the wall, tyres, drums
    for u in range(1, 11):
        for v in range(4, 12):
            b.set(u, 0, v, "gray_concrete" if (u * 3 + v) % 5 else "light_gray_concrete")
    for u in (3, 8):
        wall_post("andesite_wall", u, 1, 4, 7, b)
    b.fill(3, 5, 7, 8, 5, 7, B("iron_bars", north=False, south=False, east=True, west=True, waterlogged=False))
    home(b, "kitchen_drawers_white", 1, 1, 4, "east")                # the tool chest
    b.set(1, 1, 5, B("smithing_table"))
    b.set(1, 1, 6, B("crafting_table"))
    b.set(1, 1, 7, B("anvil", facing="north"))
    b.set(1, 1, 8, B("grindstone", face="floor", facing="east"))
    b.set(1, 1, 10, B("barrel", facing="up", open=False))
    b.set(1, 1, 11, B("barrel", facing="up", open=False))
    b.fill(9, 1, 4, 10, 1, 4, "black_concrete")                      # a stack of tyres
    b.set(10, 2, 4, "black_concrete")
    for u in (4, 10):                                                # chargers on the front of the left wing
        b.set(u, 1, 20, B("ptmuk:ev_charger", facing="south"))

    # ------------------------------------------------------------ the coach house garage
    gx0, gx1, gz0, gz1 = 42, 56, 4, 26
    b.fill(gx0, 0, gz0, gx1, 0, gz1, "smooth_stone")
    b.walls(gx0, 1, gz0, gx1, 5, gz1, RED)
    b.fill(gx0, 4, gz0, gx0, 4, 20, STONE)                           # stone band over the doors
    for vb in (5, 9, 13, 17):                                        # four bays onto the courtyard
        garage_door(b, gx0, vb, vb + 2, 1, 3, "west")
    b.fill(gx0 - 1, 0, 4, gx0 - 1, 0, 20, "ptmuk:granite_setts")
    for v in (8, 12, 16, 20):
        b.side_window(gx1, 2, v, 1, 2, WIN)
    for u in (45, 49, 53):
        b.window(u, 2, gz0, 1, 2, WIN)
    for u in (50, 53):
        b.window(u, 2, gz1, 1, 2, WIN)
    door(b, gx0, 1, 24, "left", "spruce")                            # side door to the covered walkway
    b.hipped_roof(gx0 - 1, gx1 + 1, gz0 - 1, gz1 + 1, 6, CLAY)
    cu, cv = 49, 15                                                  # cupola and weathervane on the ridge
    for y in (14, 15):
        for (u, v) in ((cu - 1, cv - 1), (cu + 1, cv - 1), (cu - 1, cv + 1), (cu + 1, cv + 1)):
            b.set(u, y, v, "white_concrete")
        for (u, v) in ((cu, cv - 1), (cu, cv + 1), (cu - 1, cv), (cu + 1, cv)):
            b.set(u, y, v, B("white_stained_glass_pane" if y == 15 else "spruce_fence"))
    b.fill(cu - 1, 16, cv - 1, cu + 1, 16, cv + 1, slab("dark_prismarine"))
    b.set(cu, 15, cv, B("lantern", hanging=True, waterlogged=False))
    b.set(cu, 17, cv, B("lightning_rod", facing="up", powered=False, waterlogged=False))
    # inside: a workshop bench along the back wall, EV chargers, lights
    for u in range(43, 48):
        home(b, "kitchen_drawers_white" if u % 2 else "kitchen_unit_white", u, 1, gz1 - 1, "south")
        home(b, "kitchen_wall_unit_white", u, 3, gz1 - 1, "south")
    b.set(48, 1, gz1 - 1, B("smithing_table"))
    b.set(49, 1, gz1 - 1, B("crafting_table"))
    b.set(gx1 - 1, 1, 6, B("ptmuk:ev_charger", facing="west"))
    b.set(gx1 - 1, 1, 14, B("ptmuk:ev_charger", facing="west"))
    lights_at(b, [(u, 5, v) for u in (46, 52) for v in (7, 12, 17, 22)], level=14)

    # ------------------------------------------------------------ the covered walkway into the house
    b.fill(38, 0, 22, 41, 0, 26, "ptmuk:granite_setts")
    for u in (38, 41):
        for v in (22, 26):
            b.fill(u, 1, v, u, 3, v, B("stripped_oak_log", axis="y"))
    for v in (22, 26):
        b.fill(38, 4, v, 41, 4, v, B("stripped_oak_log", axis="x"))
    b.gable_roof(38, 41, 21, 27, 4, CLAY)
    b.fill(gx0, 6, 21, gx0, 6, 27, RED)                              # close the garage eaves behind it

    # ------------------------------------------------------------ the house: main block and two front wings
    x0, x1, z0, z1 = 3, 37, 27, 43
    wings = ((3, 11), (29, 37))
    wz0 = 21
    b.fill(x0, 0, z0, x1, 0, z1, "oak_planks")
    for (a, c) in wings:
        b.fill(a, 0, wz0, c, 0, z0, "oak_planks")
    b.walls(x0, 1, z0, x1, 8, z1, RED)
    for (a, c) in wings:
        b.walls(a, 1, wz0, c, 8, z0, RED)
    for y in (4, 8):                                                 # first floor and the loft floor
        b.fill(x0 + 1, y, z0 + 1, x1 - 1, y, z1 - 1, "oak_planks")
        for (a, c) in wings:
            b.fill(a + 1, y, wz0 + 1, c - 1, y, z0 - 1, "oak_planks")
    for y in (4, 8):                                                 # stone string course and cornice
        b.walls(x0, y, z0, x1, y, z1, STONE)
        for (a, c) in wings:
            b.walls(a, y, wz0, c, y, z0, STONE)
    for (u, v) in ((x0, z1), (x1, z1), (3, wz0), (11, wz0), (29, wz0), (37, wz0), (11, z0), (29, z0)):
        for y in range(1, 8, 2):                                     # quoins
            b.set(u, y, v, STONE)
    for u in range(16, 25):                                          # black and white hall floor
        for v in range(28, 43):
            b.set(u, 0, v, "quartz_block" if (u + v) % 2 else "polished_blackstone")
    b.fill(26, 0, 35, 36, 0, 42, "polished_diorite")                 # kitchen
    b.fill(30, 0, 22, 36, 0, 26, "polished_diorite")                 # boot room

    # partitions: the hall up the middle on both floors, two rooms either side
    for y0, y1 in ((1, 3), (5, 7)):
        b.fill(15, y0, 28, 15, y1, 42, PLASTER)
        b.fill(25, y0, 28, 25, y1, 42, PLASTER)
        b.fill(4, y0, 36, 14, y1, 36, PLASTER)
        b.fill(26, y0, 34, 36, y1, 34, PLASTER)
    b.fill(16, 5, 37, 24, 7, 37, PLASTER)                            # family bathroom behind the landing
    for (u, v) in ((15, 31), (15, 39), (25, 31), (25, 39), (31, 34), (9, 36), (7, 27), (33, 27)):
        b.fill(u, 1, v, u, 2, v, AIR)                                # open doorways downstairs
    door(b, 15, 5, 31, "left")                                       # master bedroom
    door(b, 15, 5, 39, "left")                                       # bedroom 2
    door(b, 25, 5, 31, "right")                                      # bedroom 3
    door(b, 25, 5, 39, "right")                                      # bedroom 4
    door(b, 20, 5, 37, "back")                                       # family bathroom
    door(b, 5, 5, 27, "front")                                       # master en-suite in the left wing
    door(b, 35, 5, 27, "front")                                      # bedroom 3's en-suite in the right wing

    # ------------------------------------------------------------ the front: porch with a balcony over it
    b.fill(17, 0, 23, 23, 0, 26, STONE)
    for u in (17, 23):
        for v in (23, 25):
            b.fill(u, 1, v, u, 3, v, B("quartz_pillar", axis="y"))
    b.fill(16, 4, 22, 24, 4, 26, slab(STONE, "top"))
    for u in range(16, 25):
        b.set(u, 5, 22, B("sandstone_wall", up=True, north="none", south="none", east="low", west="low",
                          waterlogged=False))
    for v in range(23, 27):
        for u in (16, 24):
            b.set(u, 5, v, B("sandstone_wall", up=True, north="low", south="low", east="none", west="none",
                             waterlogged=False))
    door(b, 20, 1, z0, "front", "dark_oak")                          # front door with a fanlight
    b.set(20, 3, z0, P.pane(WIN))
    door(b, 20, 5, z0, "front", "birch")                             # out onto the balcony
    # sash windows with stone sills and heads
    for u in (13, 18, 22, 27):
        P.sash(b, u, 2, z0)
        P.sash(b, u, 6, z0, sill=u not in (18, 22))
    for (a, c) in wings:
        for u in (a + 2, a + 4, a + 6):
            P.sash(b, u, 2, wz0)
            P.sash(b, u, 6, wz0)
    for v in (23, 25):
        b.side_window(3, 2, v, 1, 2, WIN)
        b.side_window(3, 6, v, 1, 2, WIN)
    door(b, 37, 1, 24, "right", "dark_oak")                          # from the walkway into the boot room
    for v in (30, 33, 39):
        for u in (x0, x1):
            b.side_window(u, 2, v, 1, 2, WIN)
            b.side_window(u, 6, v, 1, 2, WIN)
    for u in (6, 9, 12, 17, 23, 28, 32):
        b.window(u, 2, z1, 1, 2, WIN)
        b.window(u, 6, z1, 1, 2, WIN)
    b.window(19, 6, z1, 3, 2, WIN)
    door(b, 20, 1, z1, "front", "dark_oak")                          # garden door from the hall
    door(b, 35, 1, z1, "front", "dark_oak")                          # kitchen door to the terrace

    # ------------------------------------------------------------ roofs and chimneys
    b.hipped_roof(x0 - 1, x1 + 1, z0 - 1, z1 + 1, 9, CLAY)
    for (a, c) in wings:
        b.hipped_roof(a - 1, c + 1, wz0 - 1, z0 + 2, 9, CLAY)
        P.close_gable_front(b, wz0, a, c, 9, RED)
    for (u, v) in ((6, 35), (33, 35), (13, 31), (27, 31)):
        b.chimney(u, v, 9, 21, RED, w=2, d=1, pots=2)

    # ------------------------------------------------------------ the staircase: three wide, up to a galleried landing
    for u in (19, 20, 21):
        P.flight(b, u, 1, 30, 0, 1, 4, "dark_oak")
    for v in (30, 31, 32):
        for u in (18, 22):
            b.set(u, 5, v, B("dark_oak_fence"))
    for u in (19, 20, 21):
        b.set(u, 5, 29, B("dark_oak_fence"))

    # ------------------------------------------------------------ ground floor
    # drawing room: a fire with a sofa facing it, a coffee table, bookcases, a lamp, an armchair
    home(b, "fireplace", 4, 1, 32, "east")
    for v in (31, 32, 33):
        home(b, "sofa_green", 9, 1, v, "west", shape="single")
    home(b, "coffee_table", 7, 1, 32, "east")
    P.rug(b, 5, 30, 8, 34, 1, "red")
    for u in (5, 6, 7):
        tall(b, "bookcase", u, 1, 35, "south")
    tall(b, "floor_lamp", 4, 1, 29, "east")
    home(b, "sofa_cream", 12, 1, 29, "west", shape="single")
    P.plant(b, 14, 1, 28)
    # study in the left wing
    home(b, "desk", 7, 1, 22, "north")
    home(b, "computer", 7, 2, 22, "north", level="table")
    home(b, "office_chair", 7, 1, 23, "south")
    for v in (23, 24, 25):
        tall(b, "bookcase", 10, 1, v, "west")
    P.plant(b, 4, 1, 26)
    # dining room: a long table with chairs all round, a sideboard, pendants over the table
    for u in range(6, 12):
        for v in (39, 40):
            b.set(u, 1, v, PF.home("dining_table"))
        home(b, "dining_chair", u, 1, 38, "north")
        home(b, "dining_chair", u, 1, 41, "south")
    for v in (39, 40):
        home(b, "dining_chair", 5, 1, v, "east")
        home(b, "dining_chair", 12, 1, v, "west")
    home(b, "chest_of_drawers_oak", 4, 1, 42, "east")
    for (u, v) in ((7, 39), (10, 40)):
        home(b, "pendant_light", u, 3, v, "south")
    # hall
    for (u, v) in ((16, 28), (24, 28), (16, 42), (24, 42)):
        P.plant(b, u, 1, v)
    home(b, "chest_of_drawers_white", 16, 1, 35, "east")
    home(b, "table_lamp", 16, 2, 35, "east", level="counter")
    # family room: a long sofa facing the TV (placed opposite it by prefab_furnish), a rug, a lamp
    for u in range(28, 33):
        home(b, "sofa_navy", u, 1, 31, "south", shape="single")
    P.rug(b, 28, 29, 32, 30, 1, "gray")
    tall(b, "floor_lamp", 26, 1, 33, "north")
    # kitchen: units along the back wall, an island with stools, a breakfast table
    P.kitchen_run(b, 27, 34, 1, 42, facing="front", finish="white")
    for u in range(29, 33):
        home(b, "kitchen_drawers_oak" if u % 2 else "kitchen_unit_oak", u, 1, 38, "south")
    for u in (29, 31):
        home(b, "dining_chair", u, 1, 37, "north")
    for v in (35, 36):
        b.set(35, 1, v, PF.home("dining_table"))
        home(b, "dining_chair", 34, 1, v, "east")
    home(b, "dining_chair", 36, 1, 35, "west")
    # boot room and cloakroom in the right wing, in from the walkway
    home(b, "washing_machine", 31, 1, 22, "north")
    home(b, "kitchen_sink_white", 32, 1, 22, "north")
    home(b, "kitchen_unit_white", 33, 1, 22, "north")
    home(b, "toilet", 36, 1, 22, "west")
    home(b, "basin", 36, 1, 26, "west")
    tall(b, "wardrobe_oak", 30, 1, 26, "south")

    # ------------------------------------------------------------ first floor
    # master bedroom: a double bed, bedside tables and lamps, wardrobes, a little sofa
    for u in (8, 9):
        P.bed(b, u, 5, 28, "white", "front")
    for u in (7, 10):
        home(b, "bedside_table_white", u, 5, 28, "north")
        home(b, "table_lamp", u, 6, 28, "north", level="low")
    for v in (31, 32):
        tall(b, "wardrobe_white", 4, 5, v, "east")
    home(b, "chest_of_drawers_white", 4, 5, 34, "east")
    home(b, "fireplace", 14, 5, 34, "west")
    for v in (33, 34):
        home(b, "sofa_cream", 12, 5, v, "east", shape="single")
    P.plant(b, 14, 5, 28)
    # master en-suite
    PF.bath(b, [(4, 23), (4, 24)], 5)
    tall(b, "shower", 10, 5, 22, "west")
    home(b, "toilet", 10, 5, 25, "west")
    home(b, "basin", 8, 5, 22, "north")
    home(b, "bathroom_cabinet", 8, 6, 22, "north")
    home(b, "towel_radiator", 9, 5, 26, "south")
    # bedroom 2 (back left)
    for u in (8, 9):
        P.bed(b, u, 5, 42, "light_blue", "back")
    for u in (7, 10):
        home(b, "bedside_table_oak", u, 5, 42, "south")
        home(b, "table_lamp", u, 6, 42, "south", level="low")
    for v in (37, 38):
        tall(b, "wardrobe_oak", 4, 5, v, "east")
    home(b, "desk", 13, 5, 42, "south")
    home(b, "office_chair", 13, 5, 41, "north")
    # bedroom 3 (front right) and its en-suite in the right wing
    for u in (31, 32):
        P.bed(b, u, 5, 28, "pink", "front")
    for u in (30, 33):
        home(b, "bedside_table_white", u, 5, 28, "north")
        home(b, "table_lamp", u, 6, 28, "north", level="low")
    for v in (31, 32):
        tall(b, "wardrobe_white", 36, 5, v, "west")
    tall(b, "shower", 36, 5, 22, "west")
    home(b, "toilet", 36, 5, 25, "west")
    home(b, "basin", 32, 5, 22, "north")
    home(b, "bathroom_cabinet", 32, 6, 22, "north")
    home(b, "towel_radiator", 30, 5, 22, "north")
    # bedroom 4 (back right)
    for u in (31, 32):
        P.bed(b, u, 5, 42, "lime", "back")
    for u in (30, 33):
        home(b, "bedside_table_oak", u, 5, 42, "south")
        home(b, "table_lamp", u, 6, 42, "south", level="low")
    for v in (37, 38):
        tall(b, "wardrobe_oak", 36, 5, v, "west")
    home(b, "chest_of_drawers_oak", 27, 5, 42, "south")
    # family bathroom behind the landing
    PF.bath(b, [(18, 42), (19, 42)], 5)
    home(b, "basin", 22, 5, 42, "south")
    home(b, "bathroom_cabinet", 22, 6, 42, "south")
    home(b, "toilet", 24, 5, 40, "west")
    tall(b, "shower", 16, 5, 42, "east")
    home(b, "towel_radiator", 24, 5, 38, "west")
    # landing
    P.plant(b, 16, 5, 28)
    P.plant(b, 24, 5, 28)
    home(b, "pendant_light", 20, 7, 34, "south")

    P.plant(b, 13, 5, 37)
    P.plant(b, 27, 5, 37)
    P.plant(b, 27, 5, 33)
    carpet(b, 4, 28, 14, 35, 5, "light_gray")                        # master bedroom
    carpet(b, 4, 37, 14, 42, 5, "light_blue")                        # bedroom 2
    carpet(b, 26, 28, 36, 33, 5, "light_gray")                       # bedroom 3
    carpet(b, 26, 35, 36, 42, 5, "white")                            # bedroom 4
    carpet(b, 16, 28, 24, 36, 5, "red")                              # landing
    carpet(b, 26, 28, 36, 33, 1, "brown")                            # family room
    carpet(b, 4, 22, 10, 26, 1, "green")                             # study

    for y in (3, 7):                                                 # lights in every room
        lights_at(b, [(u, y, v) for u in (7, 11, 18, 22, 29, 33) for v in (30, 33, 39, 41)])
        lights_at(b, [(7, y, 24), (33, y, 24), (20, y, 40)])

    # ------------------------------------------------------------ terrace
    b.fill(3, 0, 44, 37, 0, 47, "ptmuk:paving_slabs")
    for u in range(3, 38):
        if u not in (19, 20, 21, 34, 35, 36):
            b.set(u, 1, 47, B("sandstone_wall", up=True, north="none", south="none", east="low", west="low",
                              waterlogged=False))
    for u in (6, 12):                                                # garden tables and chairs
        for du in (0, 1):
            b.set(u + du, 1, 45, PF.home("dining_table"))
            home(b, "dining_chair", u + du, 1, 44, "north")
            home(b, "dining_chair", u + du, 1, 46, "south")
    b.fill(19, 0, 48, 21, 0, 48, "ptmuk:paving_slabs")

    # ------------------------------------------------------------ swimming pool: 20 x 8, three deep, steps in at the shallow end
    pu0, pu1, pv0, pv1 = 9, 28, 51, 58
    b.fill(pu0 - 3, 0, pv0 - 2, pu1 + 3, 0, pv1 + 3, "ptmuk:paving_slabs")
    b.fill(pu0 - 1, -3, pv0 - 1, pu1 + 1, -1, pv1 + 1, "light_blue_concrete")
    b.fill(pu0 - 1, 0, pv0 - 1, pu1 + 1, 0, pv1 + 1, "smooth_quartz")
    b.fill(pu0, -2, pv0, pu1, 0, pv1, B("water", level=0))
    for v in range(pv0 + 1, pv1):
        b.set(pu0, -1, v, B("quartz_stairs", facing="west", half="bottom", shape="straight", waterlogged=True))
        b.set(pu0 + 1, -2, v, B("quartz_stairs", facing="west", half="bottom", shape="straight", waterlogged=True))
    for u in (14, 20, 26):
        for v in (pv0 + 2, pv1 - 2):
            b.set(u, -3, v, "sea_lantern")
    for u in (11, 15, 22, 26):                                       # sun loungers along the far side
        b.set(u, 1, pv1 + 2, B("birch_stairs", facing="north", half="bottom", shape="straight", waterlogged=False))
        b.set(u, 1, pv1 + 3, B("birch_slab", type="bottom", waterlogged=False))
    for u in (13, 24):                                               # parasols
        b.fill(u, 1, pv1 + 2, u, 3, pv1 + 2, B("oak_fence"))
        for (a, c) in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
            b.set(u + a, 4, pv1 + 2 + c, "white_wool")

    # ------------------------------------------------------------ pool house: shower, WC, a fridge and a sink
    hx0, hx1, hz0, hz1 = 33, 40, 50, 58
    b.fill(hx0, 0, hz0, hx1, 0, hz1, "smooth_quartz")
    b.walls(hx0, 1, hz0, hx1, 4, hz1, RED)
    b.fill(hx0, 2, hz0 + 1, hx0, 3, hz1 - 1, P.pane("ptmuk:grey_framed_window"))
    door(b, hx0, 1, 54, "left", "birch")
    b.fill(hx0 + 1, 4, hz0 + 1, hx1 - 1, 4, hz1 - 1, "spruce_planks")
    b.hipped_roof(hx0 - 1, hx1 + 1, hz0 - 1, hz1 + 1, 5, CLAY)
    tall(b, "shower", hx1 - 1, 1, hz0 + 1, "west")
    home(b, "toilet", hx1 - 1, 1, hz1 - 1, "west")
    home(b, "basin", hx1 - 1, 1, 54, "west")
    home(b, "towel_radiator", hx0 + 2, 1, hz1 - 1, "south")
    home(b, "kitchen_sink_white", hx0 + 2, 1, hz0 + 1, "north")
    tall(b, "fridge_freezer", hx0 + 3, 1, hz0 + 1, "north")
    lights_at(b, [(36, 3, 52), (36, 3, 56)])

    # ------------------------------------------------------------ gardens: borders, trees, greenhouse, vegetables
    for v in (48, 62):
        for u in range(3, 38, 2):
            if b.get(u, 1, v).name == "minecraft:air" and b.get(u, 0, v).name == "minecraft:grass_block":
                flower(b, u, v, ("rose_bush", "peony")[(u // 2) % 2])
    for (u, v) in ((3, 53), (3, 59), (35, 62), (8, 62), (44, 50), (50, 53), (56, 49), (47, 58), (54, 60)):
        birch = (u + v) % 3 == 0
        P.tree(b, u, v, h=4, r=2, leaves="birch_leaves" if birch else "oak_leaves", log="birch_log" if birch else "oak_log")
    gx, gz = 45, 31                                                  # greenhouse
    b.fill(gx, 0, gz, gx + 8, 0, gz + 6, "gravel")
    b.walls(gx, 1, gz, gx + 8, 3, gz + 6, "glass")
    b.walls(gx, 1, gz, gx + 8, 1, gz + 6, "white_concrete")
    b.fill(gx, 4, gz, gx + 8, 4, gz + 6, "glass")
    b.fill(gx + 4, 1, gz, gx + 4, 2, gz, AIR)
    for u in range(gx + 1, gx + 8):
        for v in (gz + 1, gz + 5):
            b.set(u, 0, v, B("farmland", moisture=7))
            b.set(u, 1, v, B("carrots" if u % 2 else "potatoes", age=7))
    for u in range(44, 56, 2):                                       # vegetable beds
        for v in range(40, 46):
            b.set(u, 0, v, B("farmland", moisture=7))
            b.set(u, 1, v, B("wheat", age=7) if v % 2 else B("beetroots", age=3))
    for (u, v, f) in ((6, 49, "south"), (30, 49, "south"), (10, 61, "north"), (28, 61, "north")):
        b.set(u, 1, v, B("ptmuk:bench_wood", facing=f))
    for (u, v) in ((3, 48), (37, 48), (42, 28), (57, 28)):
        lantern_post(b, u, v)
    b.fill(57, 0, 3, 58, 0, D - 2, "gravel")                         # side path past the garage
    return b
