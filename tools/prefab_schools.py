"""Big British schools: a modern primary school (classrooms off a long corridor, a hall, a
playground and a field) and a secondary academy (three storeys of classrooms, science labs and
computer rooms, a sports hall, a canteen and library, an all-weather pitch, a courtyard, a car
park and bus bays). The names are made up. Coordinates as in prefab_lib (u across the front, y up
with the ground layer at 0, v from the front boundary back).
"""
from prefab_lib import B, Build, light, slab, stairs
import prefab_parts as P
from prefab_commercial import big_sign, fit, tall, wide
from prefab_town import RED_BRICK, ceiling_lights
from prefab_leisure import bar_counter, floors, picnic_tables, toilets, GREY_WIN

STOREY = 4
WHITE = 0xFFFFFFFF


def fence(b, u0, u1, v0, v1, gaps=()):
    """School railings round a rectangle, with gaps (u or v values) for gates."""
    for u in range(u0, u1 + 1):
        for v in (v0, v1):
            if u not in gaps:
                b.set(u, 1, v, B("iron_bars", north=False, south=False, east=u < u1, west=u > u0, waterlogged=False))
    for v in range(v0 + 1, v1):
        for u in (u0, u1):
            if v not in gaps:
                b.set(u, 1, v, B("iron_bars", north=True, south=True, east=False, west=False, waterlogged=False))


# ------------------------------------------------------------------ rooms

def classroom(b, u0, u1, v0, v1, y, kind, door_v):
    """A room between u0..u1 and v0..v1 facing a whiteboard on its left wall (u0 - 1), its door
    at (u0 + 1, door_v) in the wall at one end; the teacher's desk goes in the far corner."""
    for v in range(v0 + 1, v1):
        b.set(u0 - 1, y + 1, v, "white_concrete")
    if kind == "primary":                     # tables of four, a carpet by the board, a book corner
        P.rug(b, u0, v0, u0 + 2, v1, y, "blue")
        for u in range(u0 + 4, u1, 4):
            for v in range(v0 + 1, v1, 4):
                for du in (0, 1):
                    P.table(b, u + du, y, v)
                    if v - 1 >= v0:
                        b.set(u + du, y, v - 1, stairs("birch", "front"))
                    if v + 1 <= v1:
                        b.set(u + du, y, v + 1, stairs("birch", "back"))
        P.bookcase(b, u1, y, v1)
    elif kind == "lab":                        # benches with sinks, stools along them
        for u in range(u0 + 3, u1, 3):
            for v in range(v0 + 1, v1):
                fit(b, "kitchen_sink_white" if v in (v0 + 1, v1 - 1) else "kitchen_unit_white", u, y, v, "left")
                if v % 2:
                    fit(b, "bar_stool", u - 1, y, v, "right")
    elif kind == "ict":                        # computers round the walls and down the middle
        for v in range(v0, v1 + 1, 2):
            fit(b, "desk", u1, y, v, "left")
            fit(b, "computer", u1, y + 1, v, "left", level="table")
            fit(b, "office_chair", u1 - 1, y, v, "right")
        for v in range(v0 + 1, v1, 2):
            fit(b, "desk", u0 + 3, y, v, "right")
            fit(b, "computer", u0 + 3, y + 1, v, "right", level="table")
    else:                                      # rows of desks facing the board
        for u in range(u0 + 2, u1, 3):
            for v in range(v0, v1 + 1):
                if (v - v0) % 3 != 2:
                    P.table(b, u, y, v)
                    b.set(u + 1, y, v, stairs("birch", "right"))
    far = v1 if abs(door_v - v0) < abs(door_v - v1) else v0
    fit(b, "desk", u0, y, far, "right")
    b.light_grid(u0, v0, u1, v1, y + 2, step=3, level=14)


def teaching_block(b, u0, u1, v0, v1, storeys, wall, kinds, room_w=9, entrance=True, windows_kind=GREY_WIN):
    """A block of classrooms each side of a corridor, stairs at both ends, `storeys` high.
    kinds(k, i) gives each room's kind (floor k, room index i). The ground floor's middle front
    room is the entrance hall, open to the corridor, with doors out the front."""
    top = STOREY * storeys
    b.fill(u0, 0, v0, u1, 0, v1, "polished_andesite")
    b.walls(u0, 1, v0, u1, top - 1, v1, wall)
    floors(b, u0 + 1, u1 - 1, v0 + 1, v1 - 1, storeys)
    c0 = (v0 + v1) // 2 - 1
    c1 = c0 + 1
    for cu in (u0 + 1, u1 - 2):
        if storeys > 1:
            P.dogleg_stairs(b, cu, c0, [STOREY * k for k in range(storeys)], "stone_brick")
        b.door(u0 if cu == u0 + 1 else u1, 1, c0, "birch", facing="left" if cu == u0 + 1 else "right", hinge="left")
    middle = (u0 + u1) // 2
    for k in range(storeys):
        y = STOREY * k + 1
        b.fill(u0 + 1, y, c0 - 1, u1 - 1, y + 2, c0 - 1, "calcite")
        b.fill(u0 + 4, y, c1 + 1, u1 - 4, y + 2, c1 + 1, "calcite")
        i = 0
        for strip, (start, stop, sv0, sv1, door_v, out_v) in enumerate((
                (u0 + 1, u1 - 1, v0 + 1, c0 - 2, c0 - 1, v0),
                (u0 + 4, u1 - 4, c1 + 2, v1 - 1, c1 + 1, v1))):
            u = start
            while u + room_w - 1 <= stop:
                r1 = u + room_w - 1
                if stop - r1 < room_w + 1:
                    r1 = stop
                if r1 < stop:
                    b.fill(r1 + 1, y, sv0, r1 + 1, y + 2, sv1, "calcite")
                for x in range(u + 1, r1, 2):
                    b.window(x, y + 1, out_v, 1, 2, windows_kind)
                if entrance and k == 0 and strip == 0 and u <= middle <= r1:
                    # the entrance hall: open to the corridor, double doors out to the front
                    b.fill(u, y, door_v, r1, y + 2, door_v, "air")
                    for x in range(u + 1, r1, 2):
                        b.fill(x, y, out_v, x, y + 2, out_v, wall)
                    dl = max(u + 1, min(middle, r1 - 2))      # both doors inside the hall, clear of its walls
                    b.door(dl, 1, v0, "birch", facing="back", hinge="left")
                    b.door(dl + 1, 1, v0, "birch", facing="back", hinge="right")
                    bar_counter(b, u + 1, u + 4, sv1 - 2, "front", tills=(), finish="white")
                    fit(b, "computer", u + 2, 2, sv1 - 2, "front", level="counter")
                    if dl >= u + 3:                                         # clear of the doors
                        P.sofa(b, u + 1, u + 2, 1, sv0, "front", "warped")
                    elif dl + 1 <= r1 - 3:
                        P.sofa(b, r1 - 2, r1 - 1, 1, sv0, "front", "warped")
                    b.light_grid(u, sv0, r1, sv1, y + 2, step=3, level=14)
                else:
                    b.door(u + 1, y, door_v, "birch", facing="front" if strip == 0 else "back", hinge="left")
                    classroom(b, u, r1, sv0, sv1, y, kinds(k, i), door_v)
                    i += 1
                u = r1 + 2
        b.light_grid(u0 + 1, c0, u1 - 1, c1, y + 2, step=3, level=14)
    P.flat_roof(b, u0, u1, v0, v1, top, deck="gray_concrete", parapet=wall, coping="smooth_stone")
    return top


# ================================================================== primary school

def large_primary(ident="large_primary_school", name="Large Modern Primary School"):
    W, D = 74, 74
    b = Build(ident, name, "Civic", W, 12, D,
              "A big modern primary school: a staff car park and front lawn, a long single-storey block of classrooms "
              "either side of a corridor with an entrance hall and office, a school hall with a stage, and behind it a "
              "playground with games markings, play equipment and a playing field. OAKFIELD is a made-up name.")
    P.paving(b, 0, 0, 33, 11, "ptmuk:tarmac")
    P.parking_bays(b, 1, 1, 32, 5, "u", 3, "back")
    P.parking_bays(b, 1, 7, 32, 11, "u", 3, "front")
    P.lawn(b, 34, 0, W - 1, 11)
    P.paving(b, 35, 0, 38, 12, "ptmuk:paving_slabs")
    for u in range(41, W - 2, 8):
        P.tree(b, u, 5, 4, 2)
    fence(b, 0, W - 1, 12, D - 1, gaps=(35, 36, 37, 38))
    P.paving(b, 1, 13, W - 2, 13, "ptmuk:paving_slabs")
    teaching_block(b, 4, 69, 14, 43, 1, RED_BRICK, lambda k, i: "primary", room_w=10)
    big_sign(b, 28, 45, 3, 3, 13, "OAKFIELD PRIMARY SCHOOL", "", 0xFF1B5E20, WHITE)
    # the school hall behind the block, with a stage and the kitchen
    P.paving(b, 1, 44, W - 2, 45, "ptmuk:paving_slabs")
    b.fill(22, 0, 46, 51, 0, 62, "oak_planks")
    b.walls(22, 1, 46, 51, 6, 62, RED_BRICK)
    for x in range(24, 50, 3):
        b.window(x, 2, 46, 1, 3, GREY_WIN)
    b.door(36, 1, 46, "birch", facing="back", hinge="left")
    b.door(37, 1, 46, "birch", facing="back", hinge="right")
    b.fill(23, 1, 58, 50, 1, 61, slab("oak", "top"))
    b.set(36, 2, 59, B("lectern", facing="south", has_book=False, powered=False))
    for u in (23, 50):
        b.set(u, 2, 61, B("potted_azalea_bush"))
    for u in range(25, 49, 2):
        for v in range(49, 56, 2):
            if u not in (36, 37):
                fit(b, "dining_chair", u, 1, v, "back")
    P.flat_roof(b, 22, 51, 46, 62, 7, deck="gray_concrete", parapet=RED_BRICK, coping="smooth_stone")
    b.light_grid(23, 47, 50, 61, 5, step=4, level=14)
    # the playground and field
    b.fill(1, 0, 46, 20, 0, D - 2, "ptmuk:tarmac")
    b.fill(53, 0, 46, W - 2, 0, D - 2, "ptmuk:tarmac")
    for k in range(10):
        b.set(5 + k % 2, 0, 50 + k, ("red_concrete", "yellow_concrete", "blue_concrete", "lime_concrete")[k % 4])
    for u in range(10, 19):
        b.set(u, 0, 52, "white_concrete")
        b.set(u, 0, 60, "white_concrete")
    for v in range(52, 61):
        b.set(10, 0, v, "white_concrete")
        b.set(18, 0, v, "white_concrete")
    P.playground(b, 55, 48, 70, 58)
    P.lawn(b, 22, 63, W - 2, D - 2)
    for v in range(64, D - 2):
        if v % 2:
            b.set(30, 0, v, "white_concrete")
    for (u, v) in ((4, 66), (12, 66), (60, 66), (68, 66)):
        P.tree(b, u, v, 4, 2)
    for u in (8, 14):
        b.set(u, 1, 47, B("ptmuk:bench_metal", facing="south"))
    return b


# ================================================================== secondary school

def secondary_school(ident="secondary_school", name="Secondary School (Academy)"):
    W, D = 110, 100
    b = Build(ident, name, "Civic", W, 16, D,
              "A big secondary school: bus bays and a car park out front, a three-storey teaching block of classrooms, "
              "computer rooms and science labs either side of long corridors with stairs at both ends, a sports hall, "
              "a canteen with a servery and a library, a courtyard with benches and an all-weather pitch with "
              "floodlights. RIVERSIDE ACADEMY is a made-up name.")
    # bus bays along the front, the car park on the right, a plaza in front of the doors
    P.paving(b, 0, 0, 66, 9, "ptmuk:tarmac")
    for u in range(4, 63):
        b.set(u, 0, 1, "yellow_concrete")
        b.set(u, 0, 8, "yellow_concrete")
    for u in range(4, 63, 14):
        for v in range(1, 9):
            b.set(u, 0, v, "yellow_concrete")
    P.paving(b, 67, 0, W - 1, 14, "ptmuk:tarmac")
    P.paving(b, 67, 15, W - 1, 15, "ptmuk:paving_slabs")
    P.parking_bays(b, 68, 1, W - 2, 6, "u", 3, "back")
    P.parking_bays(b, 68, 9, W - 2, 14, "u", 3, "front")
    P.paving(b, 0, 10, 66, 15, "ptmuk:paving_slabs")
    for u in range(6, 64, 10):
        P.tree(b, u, 12, 4, 1, leaves="birch_leaves", log="birch_log")
    for u in range(2, 66, 12):
        b.set(u, 1, 14, B("ptmuk:bench_metal", facing="south"))
    # the teaching block: ground floor classrooms, first floor classrooms and ICT, top floor labs
    def kinds(k, i):
        return ("class", "ict" if i % 3 == 0 else "class", "lab")[k]
    teaching_block(b, 4, 67, 16, 39, 3, "ptmuk:london_stock_brick", kinds, room_w=9)
    for y in (4, 8):
        b.fill(4, y, 16, 67, y, 16, "cyan_terracotta")
    big_sign(b, 24, 47, 12, 13, 15, "RIVERSIDE ACADEMY", "Achieve • Respect • Belong", 0xFF0B2545, WHITE)
    # sports hall
    hv0, hv1 = 16, 47
    b.fill(72, 0, hv0, 105, 0, hv1, "oak_planks")
    b.walls(72, 1, hv0, 105, 10, hv1, "light_gray_concrete")
    b.fill(72, 7, hv0, 105, 10, hv0, "cyan_terracotta")
    b.door(88, 1, hv0, "birch", facing="back", hinge="left")
    b.door(89, 1, hv0, "birch", facing="back", hinge="right")
    for u in range(74, 104):
        b.set(u, 0, 19, "red_concrete")
        b.set(u, 0, hv1 - 5, "red_concrete")
    for v in range(19, hv1 - 4):
        b.set(74, 0, v, "red_concrete")
        b.set(103, 0, v, "red_concrete")
        b.set(88, 0, v, "white_concrete")
    toilets(b, 76, 82, hv1 - 3, hv1 - 1, "light_gray_concrete", hv1 - 4, h=10)
    P.flat_roof(b, 72, 105, hv0, hv1, 11, deck="gray_concrete", parapet="light_gray_concrete", coping="smooth_stone")
    for u in range(74, 104, 5):
        for v in range(hv0 + 2, hv1, 5):
            b.set(u, 11, v, "sea_lantern")
    # canteen and library
    cv0, cv1 = 42, 58
    b.fill(4, 0, cv0, 50, 0, cv1, "smooth_quartz")
    b.walls(4, 1, cv0, 50, 5, cv1, "ptmuk:london_stock_brick")
    for x in range(6, 49, 3):
        b.window(x, 2, cv0, 1, 2, GREY_WIN)
        b.window(x, 2, cv1, 1, 2, GREY_WIN)
    b.door(20, 1, cv0, "birch", facing="back", hinge="left")
    b.door(21, 1, cv0, "birch", facing="back", hinge="right")
    b.door(42, 1, cv0, "birch", facing="back", hinge="left")
    b.door(20, 1, cv1, "birch", facing="front", hinge="left")
    b.fill(34, 1, cv0 + 1, 34, 5, cv1 - 1, "ptmuk:london_stock_brick")
    bar_counter(b, 6, 16, cv1 - 3, "front", tills=(7, 11, 15), finish="white")
    P.kitchen_run(b, 6, 12, 1, cv1 - 1, "front", finish="white")
    for u in range(8, 32, 4):
        for v in range(cv0 + 2, cv1 - 5, 2):
            P.table(b, u, 1, v)
            P.table(b, u + 1, 1, v)
            b.set(u - 1, 1, v, stairs("birch", "left"))
            b.set(u + 2, 1, v, stairs("birch", "right"))
    for u in range(36, 49, 3):
        for v in (cv0 + 5, cv0 + 6, cv0 + 9, cv0 + 10):
            P.bookcase(b, u, 1, v)
    for v in (cv1 - 3, cv1 - 1):
        P.table(b, 40, 1, v)
        b.set(39, 1, v, stairs("oak", "left"))
        b.set(41, 1, v, stairs("oak", "right"))
    b.door(34, 1, cv0 + 2, "birch", facing="left", hinge="left")
    P.flat_roof(b, 4, 50, cv0, cv1, 6, deck="gray_concrete", parapet="ptmuk:london_stock_brick", coping="smooth_stone")
    b.light_grid(5, cv0 + 1, 49, cv1 - 1, 4, step=3, level=14)
    # the courtyard and the pitch
    b.fill(1, 0, cv1 + 1, 66, 0, D - 2, "ptmuk:tarmac")
    b.fill(1, 0, 40, 66, 0, 41, "ptmuk:paving_slabs")
    b.fill(51, 0, 40, 66, 0, cv1, "ptmuk:paving_slabs")
    picnic_tables(b, [(10, 64), (18, 64), (26, 64), (10, 70), (18, 70), (26, 70)])
    for u in range(36, 62):
        b.set(u, 0, 66, "white_concrete")
        b.set(u, 0, 82, "white_concrete")
    for v in range(66, 83):
        b.set(36, 0, v, "white_concrete")
        b.set(61, 0, v, "white_concrete")
    pv0 = 52
    b.fill(72, 0, pv0, 105, 0, D - 2, "lime_concrete")
    for u in range(74, 104):
        b.set(u, 0, pv0 + 2, "white_concrete")
        b.set(u, 0, D - 4, "white_concrete")
        b.set(u, 0, (pv0 + D) // 2, "white_concrete")
    for v in range(pv0 + 2, D - 3):
        b.set(74, 0, v, "white_concrete")
        b.set(103, 0, v, "white_concrete")
    fence(b, 71, 106, pv0 - 1, D - 1, gaps=(88, 89))
    for (u, v) in ((71, 60), (71, 80), (106, 60), (106, 80)):
        P.street_light(b, u, v, "right" if u < 80 else "left", 9)
    fence(b, 0, W - 1, 15, D - 1, gaps=tuple(range(4, 68)) + (88, 89))
    return b


def all_schools():
    return [large_primary(), secondary_school()]
