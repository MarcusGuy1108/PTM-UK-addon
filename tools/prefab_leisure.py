"""Pubs, inns, a nightclub, hotels and a B&B. Every name on them is made up. Coordinates as in
prefab_lib (u across the front, y up with the ground layer at 0, v from the front boundary back).
"""
from prefab_lib import AIR, B, Build, light, slab, stairs
import prefab_parts as P
from prefab_commercial import big_sign, car_park, fit, tall, wide
from prefab_town import ceiling_lights, dining, high_street_unit, RED_BRICK, STOCK

STOREY = 4
WHITE, GOLD, CREAM = 0xFFFFFFFF, 0xFFD9B44A, 0xFFF0E6C8
SASH, GEORGIAN, LEADED, GREY_WIN = "ptmuk:sash_window", "ptmuk:georgian_window", "ptmuk:leaded_window", "ptmuk:grey_framed_window"
RENDER, THATCH = "ptmuk:white_render", "ptmuk:roof_thatch"


def floors(b, u0, u1, v0, v1, storeys, material="oak_planks"):
    for k in range(1, storeys):
        b.fill(u0, STOREY * k, v0, u1, STOREY * k, v1, material)


def windows(b, cols, y, v, h=2, kind=SASH, lintel=None):
    for u in cols:
        b.window(u, y, v, 1, h, kind)
        if lintel:
            b.set(u, y + h, v, lintel)


def picnic_tables(b, spots, y=1):
    """Pub garden tables: a table with a bench (chair) each side."""
    for u, v in spots:
        P.table(b, u, y, v)
        b.set(u - 1, y, v, stairs("spruce", "left"))
        b.set(u + 1, y, v, stairs("spruce", "right"))


def bar_counter(b, u0, u1, v, facing, pumps=(), tills=(), finish="oak", y=1):
    """A straight bar counter with beer pumps and tills on it."""
    for u in range(u0, u1 + 1):
        fit(b, f"kitchen_unit_{finish}", u, y, v, facing)
        if u in pumps:
            fit(b, "beer_pumps", u, y + 1, v, facing, level="counter")
        elif u in tills:
            fit(b, "till", u, y + 1, v, facing, level="counter")


def toilets(b, u0, u1, v0, v1, wall, door_v, facing="front", h=3):
    """Ladies and gents side by side in u0..u1 x v0..v1 (walls round them drawn here, h high: up
    to the ceiling), doors in the wall at door_v, a toilet and a basin in each."""
    mid = (u0 + u1) // 2
    b.fill(u0 - 1, 1, door_v, u1 + 1, h, door_v, wall)
    b.fill(u0 - 1, 1, min(v0, v1), u0 - 1, h, max(v0, v1), wall)
    b.fill(mid, 1, min(v0, v1), mid, h, max(v0, v1), wall)
    far = v1 if abs(v1 - door_v) > abs(v0 - door_v) else v0
    for a, c in ((u0, mid - 1), (mid + 1, u1)):
        b.door(a + 1 if c > a else a, 1, door_v, "spruce", facing=facing, hinge="left")
        fit(b, "toilet", c, 1, far, "front" if far < door_v else "back")
        fit(b, "basin", a, 1, far, "front" if far < door_v else "back")


# ================================================================== a big town-centre pub

def town_pub(ident="town_pub", name="Big Town Centre Pub"):
    W, D = 24, 30
    b = Build(ident, name, "Pubs & Bars", W, 11, D,
              "A big town-centre pub in an old Portland stone corn exchange: an island bar, dozens of tables, fruit "
              "machines, toilets, a kitchen, a function room with its own bar upstairs and a beer garden at the back. "
              "THE CORN EXCHANGE is a made-up name.")
    stone = P.STONE
    P.paving(b, 0, 0, W - 1, 1)
    b.fill(0, 0, 2, W - 1, 0, 23, "dark_oak_planks")
    b.walls(0, 1, 2, W - 1, 7, 23, stone)
    floors(b, 1, W - 2, 3, 22, 2)
    # the front: tall windows either side of double doors, the name over the top
    b.window(2, 2, 2, 8, 2, SASH)
    b.window(14, 2, 2, 8, 2, SASH)
    b.door(11, 1, 2, "dark_oak", facing="back", hinge="left")
    b.door(12, 1, 2, "dark_oak", facing="back", hinge="right")
    b.window(11, 3, 2, 2, 1, SASH)
    windows(b, (2, 4, 6, 8, 11, 12, 15, 17, 19, 21), 5, 2)
    P.flat_roof(b, 0, W - 1, 2, 23, 8, deck="gray_concrete", parapet=stone, coping="smooth_stone")
    big_sign(b, 6, 17, 8, 9, 1, "THE CORN EXCHANGE", "Pub • Food Served All Day", 0xFF14213D, GOLD)
    # the island bar: counters round a square, the staff inside, stools round it
    for u in range(9, 15):
        fit(b, "kitchen_unit_oak", u, 1, 9, "front")
        fit(b, "bar_stool", u, 1, 8, "back")
        if u != 13:                       # a gap in the back counter to get behind the bar
            fit(b, "kitchen_unit_oak", u, 1, 13, "back")
            fit(b, "bar_stool", u, 1, 14, "front")
    for v in range(10, 13):
        fit(b, "kitchen_unit_oak", 9, 1, v, "left")
        fit(b, "kitchen_unit_oak", 14, 1, v, "right")
    for u in (10, 13):
        fit(b, "beer_pumps", u, 2, 9, "front", level="counter")
        fit(b, "beer_pumps", u, 2, 13, "back", level="counter")
    fit(b, "till", 11, 2, 9, "front", level="counter")
    fit(b, "till", 12, 2, 13, "back", level="counter")
    fit(b, "beer_pumps", 9, 2, 11, "left", level="counter")
    fit(b, "beer_pumps", 14, 2, 11, "right", level="counter")
    for u in (11, 12):
        fit(b, "bottle_fridge", u, 1, 11, "front")
    # tables on both sides, fruit machines, toilets, the kitchen and the stairs at the back
    dining(b, 1, 7, 4, 18)
    dining(b, 16, W - 2, 4, 16)
    for u in (19, 20):
        tall(b, "fruit_machine", u, 1, 18, "front")
    toilets(b, 16, W - 2, 20, 22, stone, 19)
    b.fill(8, 1, 17, 14, 3, 17, stone)
    b.fill(8, 1, 18, 8, 3, 22, stone)
    b.fill(14, 1, 18, 14, 3, 22, stone)
    b.door(11, 1, 17, "spruce", facing="front", hinge="left")
    P.kitchen_run(b, 9, 13, 1, 22, "front", finish="white")
    P.dogleg_stairs(b, 1, 19, [0, 4], "oak")
    b.door(5, 1, 23, "dark_oak", facing="front", hinge="left")
    b.door(6, 1, 23, "dark_oak", facing="front", hinge="right")
    P.rug(b, 1, 3, 7, 18, 1, "red")
    P.rug(b, 16, 3, W - 2, 16, 1, "red")
    ceiling_lights(b, 1, 3, W - 2, 22, 4, step=3)
    # upstairs: a function room with its own bar
    bar_counter(b, 16, 21, 20, "front", pumps=(17, 20), tills=(18,), y=5)
    for u in range(16, 22):
        fit(b, "back_bar", u, 5, 22, "front")
    dining(b, 4, 21, 4, 16, y=5)
    b.light_grid(1, 3, W - 2, 22, 7, step=3, level=13)
    # the beer garden
    P.lawn(b, 0, 24, W - 1, D - 1)
    P.paving(b, 3, 24, 8, 25)
    picnic_tables(b, [(4, 27), (9, 27), (14, 27), (19, 27)])
    for u in range(W):
        b.set(u, 1, D - 1, B("oak_fence"))
    for v in range(24, D - 1):
        b.set(0, 1, v, B("oak_fence"))
        b.set(W - 1, 1, v, B("oak_fence"))
    return b


# ================================================================== a coaching inn with rooms

def guest_room(b, u0, u1, v_wall, v_door_wall, y, colour="white"):
    """A hotel or inn bedroom between u0 and u1, from the outside wall (v_wall) back to the
    corridor wall (v_door_wall, its door at u0): a double bed with its head on the outside wall,
    bedside tables, a TV, a wardrobe and lights."""
    step = 1 if v_door_wall > v_wall else -1
    head = v_wall + step
    facing = "front" if step == 1 else "back"
    bed_u = u0 + 2 if u1 - u0 >= 3 else u0 + 1
    P.bed(b, bed_u, y, head, colour, facing)
    for x in (bed_u - 1, bed_u + 1):
        if u0 <= x <= u1 and x != u0:
            fit(b, "bedside_table_oak", x, y, head, facing)
            fit(b, "table_lamp", x, y + 1, head, facing, level="low")
    far = v_door_wall - step
    if u1 - u0 >= 3:
        fit(b, "tv_unit", u1, y, far, "left")
        fit(b, "tv", u1, y + 1, far, "left", level="low")
    if abs(far - head) > 2:
        tall(b, "wardrobe_oak", u1, y, head + 2 * step, "left")
    b.door(u0, y, v_door_wall, "birch", facing="front" if step == -1 else "back", hinge="left")
    b.light_grid(u0, min(head, far), u1, max(head, far), y + 2, step=2, level=11)


def coaching_inn(ident="coaching_inn", name="Georgian Coaching Inn with Rooms"):
    W, D = 23, 26
    b = Build(ident, name, "Pubs & Bars", W, 22, D,
              "A red-brick Georgian coaching inn: a carriage arch through to the yard, the bar on one side with an "
              "inglenook fire, the restaurant on the other, bedrooms on two floors over them and tables in the yard. "
              "THE WHITE HART is a made-up name.")
    brick, stone = RED_BRICK, P.STONE
    P.paving(b, 0, 0, W - 1, 1)
    b.fill(0, 0, 2, W - 1, 0, 13, "dark_oak_planks")
    b.walls(0, 1, 2, W - 1, 11, 13, brick)
    floors(b, 1, W - 2, 3, 12, 3)
    # carriage arch through the middle, cobbled, out into the yard
    b.fill(10, 0, 2, 12, 0, 13, "ptmuk:granite_setts")
    b.fill(9, 1, 3, 9, 3, 12, brick)
    b.fill(13, 1, 3, 13, 3, 12, brick)
    for v in (2, 13):
        b.fill(10, 1, v, 12, 3, v, AIR)
        b.fill(10, 4, v, 12, 4, v, stone)
    b.fill(10, 1, 3, 12, 3, 12, AIR)
    b.fill(10, 4, 3, 12, 4, 12, "oak_planks")
    # windows: ground floor bar and restaurant, two floors of bedrooms
    windows(b, (2, 4, 15, 18), 2, 2, kind=GEORGIAN, lintel=stone)
    windows(b, (2, 4, 7, 11, 15, 18, 20), 5, 2, kind=GEORGIAN, lintel=stone)
    windows(b, (2, 4, 7, 11, 15, 18, 20), 9, 2, kind=GEORGIAN, lintel=stone)
    windows(b, (7, 11, 15, 18), 5, 13, kind=GEORGIAN)
    windows(b, (7, 11, 15, 18), 9, 13, kind=GEORGIAN)
    b.door(7, 1, 2, "dark_oak", facing="back", hinge="left")
    b.door(20, 1, 2, "dark_oak", facing="back", hinge="left")
    b.door(9, 1, 5, "spruce", facing="left", hinge="left")
    b.door(13, 1, 6, "spruce", facing="right", hinge="left")
    b.fill(0, 4, 2, W - 1, 4, 2, stone)
    big_sign(b, 3, 19, 4, 4, 1, "THE WHITE HART", "Coaching Inn • Rooms • Restaurant", 0xFF1E3A2A, CREAM)
    b.hipped_roof(0, W - 1, 2, 13, 12, "ptmuk:roof_slate")
    b.chimney(2, 6, 12, 18, brick, w=1, d=2, pots=2)
    b.chimney(W - 3, 6, 12, 18, brick, w=1, d=2, pots=2)
    # the bar: inglenook fire, tables, the counter at the back
    fit(b, "fireplace", 8, 1, 3, "back")
    dining(b, 1, 8, 3, 5)
    bar_counter(b, 4, 8, 10, "front", pumps=(5, 7), tills=(6,))
    for u in range(4, 9):
        fit(b, "back_bar", u, 1, 12, "front")
        fit(b, "bar_stool", u, 1, 9, "back")
    P.rug(b, 3, 6, 8, 8, 1, "red")
    # the restaurant
    dining(b, 14, W - 2, 3, 11, chair="spruce")
    ceiling_lights(b, 1, 3, W - 2, 12, 4, step=3)
    # stairs in the bar's back corner up to the bedrooms
    P.dogleg_stairs(b, 1, 7, [0, 4, 8], "oak")
    for y in (5, 9):
        b.fill(3, y, 6, W - 2, y + 2, 6, "calcite")             # corridor walls, clear of the stairs
        b.fill(3, y, 9, W - 2, y + 2, 9, "calcite")
        for wall_u in (5, 10, 15):
            b.fill(wall_u, y, 3, wall_u, y + 2, 5, "calcite")
            b.fill(wall_u, y, 10, wall_u, y + 2, 12, "calcite")
        for u0, u1 in ((6, 9), (11, 14), (16, W - 2)):
            guest_room(b, u0, u1, 2, 6, y)
            guest_room(b, u0, u1, 13, 9, y, colour="light_gray")
        guest_room(b, 3, 4, 2, 6, y, colour="red")
        guest_room(b, 3, 4, 13, 9, y, colour="red")
        b.light_grid(1, 7, W - 2, 8, y + 2, step=3, level=12)
    # the yard
    b.fill(0, 0, 14, W - 1, 0, D - 1, "ptmuk:granite_setts")
    picnic_tables(b, [(3, 17), (8, 17), (15, 17), (20, 17), (3, 21), (20, 21)])
    for u in range(W):
        b.set(u, 1, D - 1, "ptmuk:red_brick")
    return b


# ================================================================== a thatched country pub

def country_pub(ident="country_pub", name="Thatched Country Pub"):
    W, D = 20, 24
    b = Build(ident, name, "Pubs & Bars", W, 12, D,
              "A whitewashed thatched village pub: a low-beamed bar with an open fire, a dartboard and real ales, "
              "picnic tables out the front and a big beer garden behind. THE PLOUGH is a made-up name.")
    P.lawn(b, 0, 0, W - 1, D - 1)
    P.paving(b, 8, 0, 10, 3)
    b.fill(3, 0, 5, 16, 0, 10, "spruce_planks")
    b.walls(2, 1, 4, 17, 3, 11, RENDER)
    for u in (2, 17):
        b.fill(u, 1, 4, u, 3, 4, "dark_oak_log")
        b.fill(u, 1, 11, u, 3, 11, "dark_oak_log")
    windows(b, (4, 6, 13, 15), 2, 4, kind=LEADED)
    windows(b, (5, 14), 2, 11, kind=LEADED)
    b.door(9, 1, 4, "dark_oak", facing="back", hinge="left")
    b.door(10, 1, 11, "dark_oak", facing="front", hinge="left")
    b.gable_roof(1, 18, 3, 12, 4, THATCH)
    P.close_gable(b, 2, 4, 11, 4, RENDER)
    P.close_gable(b, 17, 4, 11, 4, RENDER)
    b.fill(3, 4, 5, 16, 4, 10, "dark_oak_planks")            # the beamed ceiling
    b.chimney(2, 6, 4, 9, RED_BRICK, w=1, d=2, pots=2)
    # inside: the bar along the back, a fire, darts, tables
    bar_counter(b, 6, 12, 8, "front", pumps=(7, 9, 11), tills=(10,))
    for u in range(6, 13):
        if u != 10:                                         # the back door
            fit(b, "back_bar", u, 1, 10, "front")
    for u in (7, 9, 11):
        fit(b, "bar_stool", u, 1, 7, "back")
    fit(b, "fireplace", 3, 1, 7, "right")
    fit(b, "dartboard", 16, 2, 7, "left")
    picnic_tables(b, [(4, 5), (14, 5)])
    fit(b, "sofa_brown", 3, 1, 9, "right", shape="single")
    b.light_grid(3, 5, 16, 10, 3, step=3, level=13)
    # sign on posts by the road, tables out the front and in the garden
    for y in (1, 2):
        b.set(1, y, 1, "dark_oak_log")
        b.set(5, y, 1, "dark_oak_log")
    big_sign(b, 1, 5, 3, 4, 1, "THE PLOUGH", "Free House • Real Ales • Food", 0xFF22331E, CREAM)
    picnic_tables(b, [(13, 1), (16, 1)])
    picnic_tables(b, [(4, 15), (9, 15), (14, 15), (4, 19), (9, 19), (14, 19)])
    for u in range(W):
        b.set(u, 1, D - 1, B("oak_leaves", persistent=True, distance=1, waterlogged=False))
    return b


# ================================================================== a 1960s estate pub

def estate_pub(ident="estate_pub", name="1960s Estate Pub"):
    W, D = 22, 30
    b = Build(ident, name, "Pubs & Bars", W, 12, D,
              "A post-war brick estate pub with a car park: one big bar with a pool table, darts, fruit machines and the "
              "telly, and the landlord's flat upstairs. THE RAILWAY TAVERN is a made-up name.")
    car_park(b, 0, W - 1, 0, 11)
    P.paving(b, 0, 12, W - 1, 13)
    b.fill(2, 0, 14, 19, 0, 27, "spruce_planks")
    b.walls(2, 1, 14, 19, 7, 27, RED_BRICK)
    floors(b, 3, 18, 15, 26, 2)
    windows(b, (4, 5, 6, 13, 14, 15, 16), 2, 14, kind=GREY_WIN)
    windows(b, (4, 7, 12, 16), 5, 14, kind=GREY_WIN)
    b.door(9, 1, 14, "spruce", facing="back", hinge="left")
    b.door(10, 1, 14, "spruce", facing="back", hinge="right")
    P.flat_roof(b, 2, 19, 14, 27, 8, deck="gray_concrete", parapet=RED_BRICK, coping="smooth_stone")
    big_sign(b, 5, 16, 4, 4, 13, "THE RAILWAY TAVERN", "Sky Sports • Pool • Darts", 0xFF7A1E1E, CREAM)
    # the bar along the back, games in front of it
    bar_counter(b, 5, 14, 24, "front", pumps=(6, 9, 12), tills=(8, 13))
    for u in range(5, 15):
        fit(b, "back_bar", u, 1, 26, "front")
        if u % 2:
            fit(b, "bar_stool", u, 1, 23, "back")
    wide(b, "pool_table", 7, 1, 19, "front")
    fit(b, "dartboard", 3, 2, 17, "right")
    for v in (20, 21):
        tall(b, "fruit_machine", 3, 1, v, "right")
    fit(b, "tv_unit", 18, 1, 16, "left")
    fit(b, "tv", 18, 2, 16, "left", level="low")
    dining(b, 11, 18, 18, 20)
    P.rug(b, 3, 15, 18, 22, 1, "red")
    ceiling_lights(b, 3, 15, 18, 26, 4, step=3)
    # stairs at the back right to the landlord's flat
    P.dogleg_stairs(b, 16, 22, [0, 4], "oak")
    P.sofa(b, 3, 4, 5, 15, "front", "spruce")
    P.kitchen_run(b, 3, 6, 5, 26, "front")
    P.bed(b, 10, 5, 26, "white", "back")
    P.table(b, 8, 5, 19)
    b.light_grid(3, 15, 18, 26, 7, step=3, level=12)
    return b


# ================================================================== gastropub

def gastropub():
    def fitout(b, u):
        bar_counter(b, u.x0, u.x0 + 4, u.core_v - 2, "front", pumps=(u.x0 + 1, u.x0 + 3), tills=(u.x0 + 2,))
        for x in range(u.x0, u.x0 + 5):
            fit(b, "back_bar", x, 1, u.core_v, "front")
        fit(b, "fireplace", u.x0, 1, 3, "right")
        dining(b, u.x0 + 1, u.x1, 3, u.core_v - 4, chair="spruce")
        P.kitchen_run(b, u.x0 + 1, u.x0 + 5, 1, u.back - 1, "front", finish="white")
        P.rug(b, u.x0, 2, u.x1, u.core_v - 3, 1, "green")
    return high_street_unit(
        "gastropub", "Victorian Gastropub", "Pubs & Bars", 16, 22, 2,
        ("THE FOX & HOUNDS", "Gastropub • Sunday Roasts • Garden", 0xFF14301F, GOLD),
        "A Victorian street-corner pub done up for food: a long bar, an open fire, tables laid for dinner, the kitchen "
        "at the back and the dining room upstairs. THE FOX & HOUNDS is a made-up name.", fitout, wall=RED_BRICK,
        stall="green_terracotta", glass=SASH, door_u=8, door="dark_oak",
        upper=lambda b, u, y: (dining(b, u.x0, u.core_u - 1, 3, u.core_v - 2, chair="spruce", y=y),
                               b.light_grid(1, 2, u.W - 2, u.back - 1, y + 2, step=3, level=12)))


# ================================================================== nightclub

def nightclub(ident="nightclub", name="Nightclub"):
    W, D = 26, 32
    b = Build(ident, name, "Pubs & Bars", W, 11, D,
              "A town-centre nightclub: a queue barrier and canopy outside, a cloakroom, two long bars, a big light-up "
              "dance floor, a DJ stage and seating booths. PULSE is a made-up name.")
    P.paving(b, 0, 0, W - 1, 3)
    b.fill(0, 0, 4, W - 1, 0, D - 1, "black_concrete")
    b.walls(0, 1, 4, W - 1, 7, D - 1, "black_concrete")
    P.flat_roof(b, 0, W - 1, 4, D - 1, 8, deck="gray_concrete", parapet="black_concrete", coping="smooth_stone")
    for u in (11, 12, 13, 14):
        b.door(u, 1, 4, "iron" if u in (11, 14) else "dark_oak", facing="back", hinge="left" if u % 2 else "right")
    big_sign(b, 7, 18, 5, 7, 3, "PULSE", "Nightclub • Thu - Sat • 10pm - 4am", 0xFF0A0A14, 0xFFFF3EA5)
    # the queue: a barrier along the front
    for u in range(2, 10):
        b.set(u, 1, 1, B("iron_bars", north=False, south=False, east=u < 9, west=u > 2, waterlogged=False))
    # foyer and cloakroom
    b.fill(1, 1, 9, W - 2, 6, 9, "black_concrete")
    b.fill(11, 1, 9, 14, 3, 9, AIR)
    bar_counter(b, 2, 6, 7, "back", tills=(3, 5), finish="white")
    for u in range(2, 7):
        fit(b, "clothes_rail", u, 1, 8, "front")
    # the club: bars down both sides, the dance floor, the stage and DJ
    for v in range(12, D - 4):
        fit(b, "back_bar", 1, 1, v, "right")
        fit(b, "kitchen_unit_white", 3, 1, v, "right")
        fit(b, "back_bar", W - 2, 1, v, "left")
        fit(b, "kitchen_unit_white", W - 4, 1, v, "left")
        if v % 3 == 0:
            fit(b, "till", 3, 2, v, "right", level="counter")
            fit(b, "till", W - 4, 2, v, "left", level="counter")
        if v % 2 == 0:
            fit(b, "bar_stool", 4, 1, v, "left")
            fit(b, "bar_stool", W - 5, 1, v, "right")
    fit(b, "bottle_fridge", 1, 1, 11, "right")
    fit(b, "bottle_fridge", W - 2, 1, 11, "left")
    for u in range(7, W - 7):
        for v in range(13, D - 7):
            b.set(u, 0, v, B("ptmuk:dance_floor", facing="south", lit=True))
    b.fill(7, 1, D - 5, W - 8, 1, D - 3, slab("polished_blackstone", "top"))
    for u in (10, 15):
        fit(b, "kitchen_unit_white", u, 2, D - 4, "front")
    fit(b, "dj_decks", 10, 3, D - 4, "front", level="counter")
    fit(b, "dj_decks", 15, 3, D - 4, "front", level="counter")
    for u in (8, 17):
        P.sofa(b, u, u + 1, 1, 11, "back", "crimson")
    for u in range(2, W - 2, 4):
        for v in range(12, D - 2, 4):
            if b.get(u, 6, v).name == "minecraft:air":
                b.set(u, 6, v, light(10))
    for u in (6, W - 7):
        for v in (13, D - 6):
            b.set(u, 7, v, "sea_lantern")
    return b


# ================================================================== hotels

def hotel_floor(b, x0, x1, y, front, back, corridor, room_w=4, skip_left_back=False, colour="white"):
    """A floor of hotel rooms: rooms along the front (outside wall front[0], corridor wall front[1])
    and the back, a corridor between them; room_w wide with partition walls between."""
    fv, fw = front
    bv, bw = back
    b.fill(x0, y, fw, x1, y + 2, fw, "calcite")
    b.fill(x0, y, bw, x1, y + 2, bw, "calcite")
    u = x0
    while u + room_w - 1 <= x1:
        u1 = u + room_w - 1
        if x1 - u1 < room_w + 1:          # too little left for another room: this one takes it
            u1 = x1
        if u1 + 1 <= x1:
            b.fill(u1 + 1, y, fv + 1, u1 + 1, y + 2, fw - 1, "calcite")
            b.fill(u1 + 1, y, bw + 1, u1 + 1, y + 2, bv - 1, "calcite")
        guest_room(b, u, u1, fv, fw, y, colour)
        if not (skip_left_back and u == x0):
            guest_room(b, u, u1, bv, bw, y, colour)
        u = u1 + 2
    b.light_grid(x0, fw + 1, x1, bw - 1, y + 2, step=3, level=12)


def budget_hotel(ident="budget_hotel", name="Budget Hotel"):
    W, D = 27, 32
    b = Build(ident, name, "Hotels", W, 20, D,
              "A four-storey budget hotel by the main road: car park, reception, a bar and breakfast room, and 30 rooms "
              "with double beds and TVs off long corridors. SNOOZE INN is a made-up name.")
    car_park(b, 0, W - 1, 0, 11)
    P.paving(b, 0, 12, W - 1, 13)
    v0, v1 = 14, 28
    b.fill(0, 0, v0, W - 1, 0, v1, "polished_andesite")
    b.walls(0, 1, v0, W - 1, 15, v1, "white_concrete")
    floors(b, 1, W - 2, v0 + 1, v1 - 1, 4)
    for k in range(4):
        y = STOREY * k + 2 if k == 0 else STOREY * k + 1
        cols = range(2, W - 2, 5) if k else (2, 3, 19, 20, 22, 23)
        windows(b, [c for c in cols for c in (c, c + 1)] if k else cols, y, v0, kind=GREY_WIN)
        if k:
            windows(b, [c for c in range(7, W - 2, 5) for c in (c, c + 1)], y, v1, kind=GREY_WIN)
    for u in range(0, W):
        b.set(u, 4, v0, "cyan_concrete")
    b.door(12, 1, v0, "birch", facing="back", hinge="left")
    b.door(13, 1, v0, "birch", facing="back", hinge="right")
    b.window(9, 1, v0, 3, 3, "ptmuk:office_glazing")
    b.window(14, 1, v0, 3, 3, "ptmuk:office_glazing")
    P.flat_roof(b, 0, W - 1, v0, v1, 16, deck="gray_concrete", parapet="white_concrete", coping="smooth_stone")
    big_sign(b, 8, 18, 16, 17, v0 - 1, "SNOOZE INN", "Hotel • Rooms from £39", 0xFF0E6B73, WHITE)
    # ground floor: reception, bar, breakfast room
    bar_counter(b, 9, 13, 19, "front", tills=(10,), finish="white")
    fit(b, "computer", 12, 2, 19, "front", level="counter")
    P.sofa(b, 2, 4, 1, 15, "front", "warped")
    bar_counter(b, 17, 22, 25, "front", pumps=(18, 21), tills=(19,), finish="white")
    for u in range(17, 23):
        fit(b, "back_bar", u, 1, 27, "front")
    dining(b, 15, W - 2, 16, 22)
    P.dogleg_stairs(b, 1, 21, [0, 4, 8, 12], "stone_brick")
    ceiling_lights(b, 1, v0 + 1, W - 2, v1 - 1, 4, step=3)
    # three floors of rooms
    for k in (1, 2, 3):
        y = STOREY * k + 1
        hotel_floor(b, 3, W - 2, y, (v0, 20), (v1, 23), (21, 22), skip_left_back=False)
    return b


def grand_hotel(ident="grand_hotel", name="Grand Victorian Hotel"):
    W, D = 35, 28
    b = Build(ident, name, "Hotels", W, 30, D,
              "A grand Victorian railway hotel in red brick and Portland stone: a porch over the entrance, a lobby and "
              "lounge, a bar and a restaurant with chandeliers, and five floors of rooms. THE GRAND HOTEL is a made-up "
              "name.")
    brick, stone = RED_BRICK, P.STONE
    P.paving(b, 0, 0, W - 1, 3)
    v0, v1 = 4, D - 2
    b.fill(0, 0, v0, W - 1, 0, v1, "polished_diorite")
    b.walls(0, 1, v0, W - 1, 23, v1, brick)
    floors(b, 1, W - 2, v0 + 1, v1 - 1, 6)
    for k in range(6):
        y = STOREY * k + 1
        if k:
            b.fill(0, STOREY * k, v0, W - 1, STOREY * k, v0, stone)
        windows(b, range(2, W - 2, 3), y if k else 2, v0, kind=SASH, lintel=stone)
        windows(b, range(3, W - 2, 3), y if k else 2, v1, kind=SASH)
    for u in (0, W - 1):
        b.fill(u, 1, v0, u, 23, v0, stone)
    # the porch: stone columns and a balcony over the doors
    b.fill(15, 1, v0, 19, 2, v0, AIR)
    b.fill(15, 3, v0, 19, 3, v0, stone)
    for u in (16, 18):
        b.door(u, 1, v0, "dark_oak", facing="back", hinge="left" if u < 17 else "right")
    for u in (15, 17, 19):
        b.window(u, 1, v0, 1, 2, SASH)
    for u in (14, 20):
        for y in (1, 2, 3):
            b.set(u, y, 1, B("quartz_pillar", axis="y"))
    b.fill(14, 4, 1, 20, 4, 3, stone)
    for u in range(14, 21):
        b.set(u, 5, 1, B("stone_brick_wall", up=True, north="none", south="none", east="low" if u < 20 else "none",
                         west="low" if u > 14 else "none", waterlogged=False))
    P.flat_roof(b, 0, W - 1, v0, v1, 24, deck="gray_concrete", parapet=stone, coping="smooth_stone")
    big_sign(b, 10, 24, 25, 26, v0 + 1, "THE GRAND HOTEL", "Est. 1882", 0xFF1A1A2E, GOLD)
    for u in (0, W - 1):
        b.chimney(u, 10, 24, 27, brick, w=1, d=2, pots=2)
    # ground floor: reception in the lobby, a lounge, the bar, the restaurant
    bar_counter(b, 14, 20, 11, "front", tills=(15,), finish="oak")
    fit(b, "computer", 19, 2, 11, "front", level="counter")
    for u in (3, 6):
        P.sofa(b, u, u + 1, 1, 7, "back", "dark_oak")
        fit(b, "coffee_table", u, 1, 9, "front")
        fit(b, "coffee_table", u + 1, 1, 9, "front")
    fit(b, "fireplace", 1, 1, 9, "right")
    bar_counter(b, 3, 9, 18, "front", pumps=(4, 8), tills=(6,))
    for u in range(3, 10):
        fit(b, "back_bar", u, 1, 20, "front")
        if u % 2:
            fit(b, "bar_stool", u, 1, 17, "back")
    dining(b, 21, W - 2, 6, 20, chair="dark_oak")
    P.rug(b, 11, 5, 23, 15, 1, "red")
    P.dogleg_stairs(b, 11, 18, [0, 4, 8, 12, 16, 20], "stone_brick")
    ceiling_lights(b, 1, v0 + 1, W - 2, v1 - 1, 4, step=3)
    for k in range(1, 6):
        y = STOREY * k + 1
        hotel_floor(b, 1, 10, y, (v0, 12), (v1, 15), (13, 14), colour="white")
        hotel_floor(b, 15, W - 2, y, (v0, 12), (v1, 15), (13, 14), colour="white")
        b.fill(11, y, 12, 14, y + 2, 12, "calcite")
        b.fill(11, y, 15, 14, y + 2, 15, AIR)
        b.light_grid(11, 13, 14, 17, y + 2, step=2, level=12)
    return b


def guest_house():
    def fitout(b, u):
        dining(b, u.x0, u.x1, 3, 5, chair="oak")
        fit(b, "fireplace", u.x0, 1, u.core_v - 1, "right")
        P.armchair(b, u.x0 + 1, 1, u.core_v - 2, "back", "spruce")
        P.kitchen_run(b, u.x0 + 1, u.x0 + 3, 1, u.back - 1, "front")     # clear of the back door at x0

    def rooms(b, u, y):
        guest_room(b, u.x0, u.core_u - 1, 1, u.core_v - 1, y)
        b.fill(u.x0, y, u.core_v - 1, u.core_u - 1, y + 2, u.core_v - 1, "calcite")
        b.door(u.x0, y, u.core_v - 1, "birch", facing="back", hinge="left")
        b.light_grid(u.x0, u.core_v, u.x1, u.back - 1, y + 2, step=2, level=11)
    b = high_street_unit(
        "guest_house", "Seaside Guest House (B&B)", "Hotels", 8, 18, 3,
        ("SEA VIEW", "Guest House • Bed & Breakfast • Vacancies", 0xFF1F4E79, WHITE),
        "A Victorian seaside B&B: a breakfast room and lounge at the front, guest bedrooms on the floors above. SEA VIEW "
        "is a made-up name.", fitout, wall="ptmuk:white_render", stall="light_blue_terracotta", glass=SASH, door_u=4,
        upper=rooms, floor="oak_planks")
    return b


def all_leisure():
    return [town_pub(), coaching_inn(), country_pub(), estate_pub(), gastropub(), nightclub(), budget_hotel(),
            grand_hotel(), guest_house()]
