"""Turns the makeshift furniture in the prefabs (stairs for sofas and chairs, fences and pressure
plates for tables, a blast furnace for a fire, a cauldron for a basin, quartz stairs for a toilet)
into the mod's real home furniture, turns each piece so its back is against a wall, and works out
how sofas and tables join up. Runs over every prefab after it's built (see prefab_designs)."""
from prefab_lib import AIR, B, Block

# world directions in design coordinates (u east, v north)
VEC = {"north": (0, 1), "south": (0, -1), "east": (1, 0), "west": (-1, 0)}
OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}
CW = {"north": "east", "east": "south", "south": "west", "west": "north"}
CCW = {v: k for k, v in CW.items()}

SOFA_COLOUR = {"dark_oak": "brown", "spruce": "grey", "oak": "cream", "birch": "cream", "jungle": "green",
               "acacia": "green", "mangrove": "navy", "crimson": "navy", "warped": "green", "cherry": "cream"}
WOOD = ("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "crimson", "warped", "bamboo")


def home(name, **props):
    return Block("ptmuk:" + name, {k: str(v).lower() if isinstance(v, bool) else str(v) for k, v in props.items()})


def solid(b, u, y, v):
    blk = b.get(u, y, v) if b.inside(u, y, v) else None
    if blk is None or blk.name == "minecraft:air":
        return False
    n = blk.name
    return not n.endswith(("_carpet", "_pressure_plate", "_door", "_trapdoor", "light", "torch", "_sign")) and \
        n not in ("minecraft:light",)


def back_to_wall(b, u, y, v, prefer=None):
    """The way a piece should face so its back is against something solid (prefer keeps the
    designer's choice when that already works)."""
    options = [prefer] if prefer else []
    options += [d for d in ("south", "north", "east", "west") if d != prefer]
    for d in options:
        du, dv = VEC[OPP[d]]
        front_u, front_v = VEC[d]
        if solid(b, u + du, y, v + dv) and not solid(b, u + front_u, y, v + front_v):
            return d
    return prefer or "south"


def fits_tall(b, u, y, v):
    return b.inside(u, y + 1, v) and b.get(u, y + 1, v).name in ("minecraft:air", "minecraft:light")


# ------------------------------------------------------------------ helpers used by prefab_parts

def sofa(b, u0, u1, y, v, back, material):
    """A sofa along u: back is the design-local side its back rests against."""
    from prefab_lib import DIRS
    facing = OPP[DIRS[back]]
    colour = SOFA_COLOUR.get(material, "grey")
    for u in range(u0, u1 + 1):
        b.set(u, y, v, home(f"sofa_{colour}", facing=facing, lit=False, shape="single"))


def kitchen(b, cells, y, facing, finish):
    """A run of fitted kitchen units: an oven and hob at one end, the sink in the middle,
    cupboards and drawers between, a fridge freezer at the far end, wall cupboards over
    alternate units and a kettle, toaster and microwave on the worktop."""
    n = len(cells)
    for i, (u, v) in enumerate(cells):
        if i == 0:
            kind = f"kitchen_oven_{finish}"
        elif i == n - 1 and n >= 3 and fits_tall(b, u, y, v):
            b.set(u, y, v, home("fridge_freezer", facing=facing, lit=False, half="lower"))
            b.set(u, y + 1, v, home("fridge_freezer", facing=facing, lit=False, half="upper"))
            continue
        elif i == n // 2:
            kind = f"kitchen_sink_{finish}"
        else:
            kind = f"kitchen_drawers_{finish}" if i % 2 else f"kitchen_unit_{finish}"
        b.set(u, y, v, home(kind, facing=facing, lit=False))
        above = b.get(u, y + 1, v).name if b.inside(u, y + 1, v) else None
        if above != "minecraft:air":
            continue
        if i in (0, n // 2):
            b.set(u, y + 1, v, home(f"kitchen_wall_unit_{finish}", facing=facing, lit=False))
        elif i == 1:
            b.set(u, y + 1, v, home("kettle", facing=facing, lit=False, level="counter"))
        elif i == n - 2 and n >= 4:
            b.set(u, y + 1, v, home("microwave", facing=facing, lit=False, level="counter"))
        elif i % 3 == 2:
            b.set(u, y + 1, v, home("toaster", facing=facing, lit=False, level="counter"))
        else:
            b.set(u, y + 1, v, home(f"kitchen_wall_unit_{finish}", facing=facing, lit=False))


def bath(b, cells, y):
    """A bath across two of the cells (taps at the left as you face it), a basin in a third."""
    if len(cells) < 2:
        return
    (u0, v0), (u1, v1) = cells[0], cells[1]
    along = (u1 - u0, v1 - v0)
    # the bath faces away from the wall behind it; its right half lies clockwise of its facing
    for facing in ("south", "north", "east", "west"):
        if VEC[CW[facing]] == along or VEC[CW[facing]] == (-along[0], -along[1]):
            du, dv = VEC[OPP[facing]]
            if solid(b, u0 + du, y, v0 + dv) or solid(b, u1 + du, y, v1 + dv):
                break
    else:
        facing = "south"
    if VEC[CW[facing]] == along:
        left, right = (u0, v0), (u1, v1)
    else:
        left, right = (u1, v1), (u0, v0)
    b.set(left[0], y, left[1], home("bath", facing=facing, lit=False, part="left"))
    b.set(right[0], y, right[1], home("bath", facing=facing, lit=False, part="right"))
    if len(cells) >= 3:
        u, v = cells[2]
        b.set(u, y, v, home("basin", facing=back_to_wall(b, u, y, v, facing), lit=False))


# ------------------------------------------------------------------ the pass over a finished building

def _is_staircase_step(b, u, y, v, blk):
    d = blk.props.get("facing")
    if d not in VEC:
        return False
    du, dv = VEC[d]
    for k in (1, -1):
        n = b.get(u + du * k, y + k, v + dv * k) if b.inside(u + du * k, y + k, v + dv * k) else None
        if n is not None and n.name == blk.name and n.props.get("facing") == d:
            return True
    return False


def furnish(b):
    cells = b.cells
    # tables: a wooden fence with a pressure plate on it
    for (u, y, v), blk in list(cells.items()):
        n = blk.name
        if n.endswith("_fence") and n.split(":")[1][:-6] in WOOD:
            top = cells.get((u, y + 1, v))
            if top is not None and top.name.endswith("_pressure_plate"):
                b.set(u, y, v, home("dining_table"))
                b.set(u, y + 1, v, AIR)
    # the fire, the basin and the toilet
    for (u, y, v), blk in list(cells.items()):
        n = blk.name
        if n == "minecraft:blast_furnace":
            b.set(u, y, v, home("fireplace", facing=back_to_wall(b, u, y, v, blk.props.get("facing")), lit=False))
        elif n == "minecraft:cauldron":
            b.set(u, y, v, home("basin", facing=back_to_wall(b, u, y, v), lit=False))
        elif n == "minecraft:quartz_stairs" and blk.props.get("half") == "bottom" and not _is_staircase_step(b, u, y, v, blk):
            b.set(u, y, v, home("toilet", facing=back_to_wall(b, u, y, v, OPP[blk.props["facing"]]), lit=False))
    # chairs: wooden stairs next to a table become dining chairs facing it
    for (u, y, v), blk in list(cells.items()):
        n = blk.name
        if not (n.endswith("_stairs") and blk.props.get("half") == "bottom" and n.split(":")[1][:-7] in WOOD):
            continue
        if _is_staircase_step(b, u, y, v, blk):
            continue
        for d, (du, dv) in VEC.items():
            t = cells.get((u + du, y, v + dv))
            if t is not None and t.name == "ptmuk:dining_table":
                b.set(u, y, v, home("dining_chair", facing=d, lit=False))
                break
    # wardrobes (a barrel under planks) stand against a wall
    for (u, y, v), blk in list(cells.items()):
        if blk.name == "minecraft:barrel" and cells.get((u, y + 1, v)) is not None and \
                cells[(u, y + 1, v)].name == "minecraft:spruce_planks":
            facing = back_to_wall(b, u, y, v, blk.props.get("facing"))
            b.set(u, y, v, home("wardrobe_oak", facing=facing, lit=False, half="lower"))
            b.set(u, y + 1, v, home("wardrobe_oak", facing=facing, lit=False, half="upper"))
    tvs(b)
    joins(b)


def tvs(b):
    """A TV on its unit against the wall opposite each sofa, if there's a clear spot that isn't
    in front of a door."""
    cells = b.cells
    done = set()
    for (u, y, v), blk in sorted(cells.items()):
        if not blk.name.startswith("ptmuk:sofa_") or (u, y, v) in done:
            continue
        f = blk.props["facing"]
        run = [(u, y, v)]
        for side in (CW[f], CCW[f]):
            du, dv = VEC[side]
            k = 1
            while (o := cells.get((u + du * k, y, v + dv * k))) is not None and o.name == blk.name:
                run.append((u + du * k, y, v + dv * k))
                k += 1
        done.update(run)
        mu, my, mv = sorted(run)[len(run) // 2]
        du, dv = VEC[f]
        for k in range(2, 7):
            cu, cv = mu + du * k, mv + dv * k
            if not b.inside(cu, my, cv) or solid(b, cu, my, cv):
                break
            if solid(b, cu + du, my, cv + dv):
                near_door = any((n := cells.get((cu + a, my, cv + c))) is not None and n.name.endswith("_door")
                                for a, c in VEC.values())
                floor = b.get(cu, my - 1, cv)
                if not near_door and floor.name != "minecraft:air" and b.get(cu, my + 1, cv).name == "minecraft:air" \
                        and not solid(b, cu - dv, my, cv + du) and not solid(b, cu + dv, my, cv - du):
                    b.set(cu, my, cv, home("tv_unit", facing=OPP[f], lit=False))
                    b.set(cu, my + 1, cv, home("tv", facing=OPP[f], lit=False, level="low"))
                break


def joins(b):
    """Set the shapes the game would work out: sofa ends and middles, table tops joined up."""
    cells = b.cells
    for (u, y, v), blk in list(cells.items()):
        n = blk.name
        if n.startswith("ptmuk:sofa_"):
            f = blk.props["facing"]

            def same(d):
                du, dv = VEC[d]
                o = cells.get((u + du, y, v + dv))
                return o is not None and o.name == n and o.props.get("facing") == f
            w, e = same(CCW[f]), same(CW[f])
            shape = "middle" if w and e else "east" if w else "west" if e else "single"
            cells[(u, y, v)] = blk.with_props(shape=shape)
        elif n == "ptmuk:dining_table":
            props = {}
            for d, (du, dv) in VEC.items():
                o = cells.get((u + du, y, v + dv))
                props[d] = o is not None and o.name == n
            cells[(u, y, v)] = Block(n, {k: str(x).lower() for k, x in props.items()})
