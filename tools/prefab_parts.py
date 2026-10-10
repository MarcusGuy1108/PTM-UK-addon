"""Reusable parts of British buildings for the prefabs: facades, bays, porches, garden walls,
roofs, chimneys and simple furnished interiors. Coordinates as in prefab_lib (u across the
front, y up, v from the front boundary back)."""
from prefab_lib import AIR, B, slab, stairs

STONE = "ptmuk:portland_stone"
GLASS = "ptmuk:sash_window"


def roof_line(b, u, v, y_from):
    """Lowest roof block in column (u, v) at or above y_from, or None."""
    for y in range(y_from, b.H):
        n = b.get(u, y, v).name
        if n.endswith("_stairs") or n.endswith("_slab"):
            return y
    return None


def close_gable(b, u, v0, v1, y_from, material):
    """Fill a wall column by column up to the underside of the roof above it."""
    for v in range(v0, v1 + 1):
        top = roof_line(b, u, v, y_from)
        if top is None:
            continue
        for y in range(y_from, top):
            if b.get(u, y, v).name == "minecraft:air":
                b.set(u, y, v, material)


def close_gable_front(b, v, u0, u1, y_from, material):
    """Same for a wall running across (a gable end facing front or back)."""
    for u in range(u0, u1 + 1):
        top = roof_line(b, u, v, y_from)
        if top is None:
            continue
        for y in range(y_from, top):
            if b.get(u, y, v).name == "minecraft:air":
                b.set(u, y, v, material)


def sash(b, u, y, v, h=2, kind=GLASS, sill=True, lintel=STONE, sill_v=None):
    """A window in a front-facing wall with a stone sill below (projecting) and a lintel above."""
    b.window(u, y, v, 1, h, kind)
    if sill:
        sv = v - 1 if sill_v is None else sill_v
        if b.inside(u, y - 1, sv) and b.get(u, y - 1, sv).name == "minecraft:air":
            b.set(u, y - 1, sv, slab(STONE, "top"))
    if lintel:
        b.set(u, y + h, v, lintel)


def back_window(b, u, y, v, h=2, kind="ptmuk:upvc_window"):
    b.window(u, y, v, 1, h, kind)
    if b.inside(u, y - 1, v + 1) and b.get(u, y - 1, v + 1).name == "minecraft:air":
        b.set(u, y - 1, v + 1, slab("stone_brick", "top"))


def front_garden(b, u0, u1, v_wall, gate_u, wall="ptmuk:red_brick", railings=True, gate="dark_oak_fence_gate"):
    """Low front wall along v_wall with railings on top and a gate."""
    for u in range(u0, u1 + 1):
        if u == gate_u:
            b.set(u, 1, v_wall, B(gate, facing="south", open=False, in_wall=False, powered=False))
            continue
        b.set(u, 1, v_wall, wall)
        if railings:
            b.set(u, 2, v_wall, B("iron_bars"))
        else:
            b.set(u, 2, v_wall, slab(STONE))


def tiled_path(b, u, v0, v1, y=0):
    for v in range(v0, v1 + 1):
        b.set(u, y, v, "polished_blackstone" if (u + v) % 2 else "polished_diorite")


def bay_window(b, u0, u1, v, y0, h=2, base="ptmuk:red_brick", roof="ptmuk:roof_slate", kind=GLASS):
    """A single-storey box bay standing one block in front of the wall at v + 1."""
    b.fill(u0, y0, v, u1, y0, v, base)
    b.window(u0, y0 + 1, v, u1 - u0 + 1, h, kind)
    for u in range(u0, u1 + 1):
        b.set(u, y0 + 1 + h, v, stairs(roof, "back"))
    # stone cornice band under the bay roof shows as the top of the windows' frame
    return y0 + 1 + h


def corbels(b, u_list, y, v, material=STONE):
    """Brackets under the eaves (upside-down stairs against the wall)."""
    for u in u_list:
        if b.get(u, y, v).name == "minecraft:air":
            b.set(u, y, v, stairs(material, "back", "top"))


def floor(b, u0, v0, u1, v1, y, material="oak_planks"):
    b.fill(u0, y, v0, u1, y, v1, material)


def stair_flight(b, u, v_start, y_start, steps, direction="back", material="oak", well=True, ceiling_y=None):
    """A straight flight of stairs rising towards `direction` (v increasing for "back"), and
    an opening in the floor above it for headroom."""
    dv = 1 if direction == "back" else -1
    for i in range(steps):
        v = v_start + dv * i
        y = y_start + i
        b.set(u, y, v, stairs(material, direction))
        for yy in range(y + 1, y + 4):
            if ceiling_y is not None and yy == ceiling_y and well:
                b.set(u, yy, v, AIR)
            elif b.get(u, yy, v).name not in ("minecraft:air",) and yy < y + 3:
                b.set(u, yy, v, AIR)


# ------------------------------------------------------------------ furniture

def sofa(b, u0, u1, y, v, facing="front", material="dark_oak"):
    """A settee: a row of stairs whose backs are towards `facing`."""
    for u in range(u0, u1 + 1):
        b.set(u, y, v, stairs(material, facing))


def armchair(b, u, y, v, facing, material="spruce"):
    b.set(u, y, v, stairs(material, facing))


def table(b, u, y, v, top="oak_pressure_plate", leg="oak_fence"):
    b.set(u, y, v, B(leg))
    b.set(u, y + 1, v, B(top))


def rug(b, u0, v0, u1, v1, y, colour="red"):
    for u in range(u0, u1 + 1):
        for v in range(v0, v1 + 1):
            if b.get(u, y, v).name == "minecraft:air":
                b.set(u, y, v, B(f"{colour}_carpet"))


def bed(b, u, y, v_head, colour="white", facing="back"):
    """A bed with its head (pillow end) at v_head against the wall towards `facing`."""
    from prefab_lib import DIRS
    dv = 1 if facing == "front" else -1      # the foot lies away from the wall
    b.set(u, y, v_head, B(f"{colour}_bed", facing=DIRS[facing], part="head", occupied=False))
    b.set(u, y, v_head + dv, B(f"{colour}_bed", facing=DIRS[facing], part="foot", occupied=False))


def wardrobe(b, u, y, v, facing="front"):
    from prefab_lib import DIRS
    b.set(u, y, v, B("barrel", facing=DIRS[facing], open=False))
    b.set(u, y + 1, v, B("spruce_planks"))


def kitchen_run(b, u0, u1, y, v, facing="front", side=False):
    """Base units along a wall: cooker at one end, sink in the middle, a tall fridge at the
    other. With side=True the run goes along v (u0..u1 are then v values at column y... ) """
    from prefab_lib import DIRS
    cells = [(u, v) for u in range(u0, u1 + 1)] if not side else [(v, w) for w in range(u0, u1 + 1)]
    for (cu, cv) in cells:
        b.set(cu, y, cv, B("spruce_planks"))
        b.set(cu, y + 2, cv, B("spruce_trapdoor", facing=DIRS[facing], half="top", open=False, powered=False,
                                 waterlogged=False))
    (cu0, cv0), (cu1, cv1) = cells[0], cells[-1]
    mid = cells[len(cells) // 2]
    b.set(cu0, y, cv0, B("smoker", facing=DIRS[facing], lit=False))
    b.set(mid[0], y, mid[1], B("cauldron"))
    b.set(cu1, y, cv1, B("white_concrete"))
    b.set(cu1, y + 1, cv1, B("white_concrete"))
    b.set(cu1, y + 2, cv1, B("white_concrete"))


def bath(b, u0, u1, y, v):
    for u in range(u0, u1 + 1):
        b.set(u, y, v, B("smooth_quartz_slab", type="bottom", waterlogged=False))
    b.set(u0, y, v, B("cauldron"))


def tv(b, u, y, v):
    b.set(u, y, v, B("dark_oak_slab", type="bottom", waterlogged=False))
    b.set(u, y + 1, v, B("black_stained_glass_pane", north=False, south=False, east=False, west=False, waterlogged=False))


def bookcase(b, u, y, v, h=2):
    for i in range(h):
        b.set(u, y + i, v, B("bookshelf"))


def plant(b, u, y, v, kind="potted_azalea_bush"):
    b.set(u, y, v, B(kind))


# ------------------------------------------------------------------ larger buildings

def pane(kind):
    return B(kind, north=False, south=False, east=False, west=False, waterlogged=False)


def hedge(b, u0, u1, v, y=1, h=1, leaves="oak_leaves"):
    for u in range(u0, u1 + 1):
        for yy in range(y, y + h):
            b.set(u, yy, v, B(leaves, persistent=True, distance=1, waterlogged=False))


def lawn(b, u0, v0, u1, v1):
    b.fill(u0, 0, v0, u1, 0, v1, "grass_block")


def paving(b, u0, v0, u1, v1, material="ptmuk:paving_slabs"):
    b.fill(u0, 0, v0, u1, 0, v1, material)


def flat_roof(b, u0, u1, v0, v1, y, deck="gray_concrete", parapet="ptmuk:red_brick", coping=None, h=1):
    """Flat roof at y with a parapet wall round the edge and a coping on top."""
    b.fill(u0, y, v0, u1, y, v1, deck)
    if parapet:
        for yy in range(y + 1, y + 1 + h):
            b.walls(u0, yy, v0, u1, yy, v1, parapet)
        b.walls(u0, y + 1 + h, v0, u1, y + 1 + h, v1, slab(coping or STONE))


def shopfront(b, u0, u1, v, y0, text, sub="", board=0xFF1C2E4A, ink=0xFFFFFFFF, door_u=None, stall="ptmuk:green_faience",
              door="jungle", height=4, sign_v=None, glass="ptmuk:shop_window"):
    """A shop front in the wall line v between pilasters at u0 - 1 and u1 + 1: a tiled stall
    riser, big windows, a glazed door with a transom light, and the fascia sign (editable
    in game) on the course above, projecting one block in front at sign_v."""
    from prefab_lib import shop_sign_nbt, Block
    sv = v - 1 if sign_v is None else sign_v
    door_u = u1 if door_u is None else door_u
    for u in range(u0, u1 + 1):
        b.set(u, y0, v, stall)
        b.fill(u, y0 + 1, v, u, y0 + height - 2, v, pane(glass))
    b.door(door_u, y0, v, door, facing="back", hinge="left")
    b.fill(door_u, y0 + 2, v, door_u, y0 + height - 2, v, pane(glass))
    # fascia: a board over the whole width; the left-hand board carries the name
    for u in range(u0, u1 + 1):
        nbt = shop_sign_nbt(text, sub, board, ink) if u == u0 else shop_sign_nbt("", "", board, ink)
        b.set(u, y0 + height - 1, sv, Block("ptmuk:shop_sign", {"facing": "south"}, nbt))
        b.set(u, y0 + height - 1, v, "black_concrete")


def pilaster(b, u, v, y0, y1, material=STONE, cap=True):
    b.fill(u, y0, v, u, y1, v, material)
    if cap and b.get(u, y1 + 1, v - 1).name == "minecraft:air":
        b.set(u, y1, v - 1, stairs(material if material.startswith("ptmuk") else material, "back", "top")
              if material in (STONE,) else slab("smooth_stone", "top"))


def ceiling_lights(b, u0, v0, u1, v1, y, step=3, block="sea_lantern"):
    """Flush ceiling panels (visible lit at night through windows)."""
    for u in range(u0 + 1, u1, step):
        for v in range(v0 + 1, v1, step):
            b.set(u, y, v, block)


def stair_core(b, cu, vs, y_floors, material="stone_brick"):
    """Switchback stairs in a 2 x 6 core: flights alternate between the two columns, rising
    towards the back then the front, with landings at both ends. y_floors are the floor
    levels (floor blocks at those heights)."""
    for i in range(len(y_floors) - 1):
        base, top = y_floors[i], y_floors[i + 1]
        steps = top - base
        if i % 2 == 0:
            col, vstart, d, facing = cu, vs + 1, 1, "back"
        else:
            col, vstart, d, facing = cu + 1, vs + steps, -1, "front"
        for k in range(steps):
            v = vstart + d * k
            y = base + 1 + k
            b.set(col, y, v, stairs(material, facing))
            for yy in range(y + 1, y + 3):
                if yy <= top + 2:
                    cur = b.get(col, yy, v).name
                    if cur != "minecraft:air" and not cur.endswith("_stairs"):
                        b.clear(col, yy, v)
            if k:                    # a solid stringer under every step but the first
                b.set(col, y - 1, v, material + "s" if material.endswith("brick") else material)


def shop_shelves(b, u0, u1, v, y=1, h=2, kind="bookshelf"):
    for u in range(u0, u1 + 1):
        for yy in range(y, y + h):
            b.set(u, yy, v, kind)


def counter(b, u0, u1, v, y=1, top="polished_andesite_slab"):
    for u in range(u0, u1 + 1):
        b.set(u, y, v, B("spruce_planks"))
        b.set(u, y + 1, v, B(top, type="bottom", waterlogged=False))


def fridge_row(b, u0, u1, v, y=1):
    for u in range(u0, u1 + 1):
        b.set(u, y, v, "white_concrete")
        b.set(u, y + 1, v, "light_blue_stained_glass")
        b.set(u, y + 2, v, "white_concrete")


def office_desks(b, u0, u1, v0, v1, y, every=3):
    for u in range(u0, u1 + 1, every):
        for v in range(v0, v1 + 1, 2):
            if b.get(u, y, v).name == "minecraft:air":
                b.set(u, y, v, B("white_concrete"))
                b.set(u, y + 1, v, B("black_stained_glass_pane", north=False, south=False, east=False, west=False,
                                     waterlogged=False))
                if b.inside(u, y, v + 1) and b.get(u, y, v + 1).name == "minecraft:air":
                    b.set(u, y, v + 1, stairs("dark_oak", "back"))
