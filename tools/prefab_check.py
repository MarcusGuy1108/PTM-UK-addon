"""Checks every prefab for the two things that make a building feel broken in game:

* gaps: windows and panes that end in thin air (the half block beside a pane's end is open, so
  you can see and walk into the house through the side of a bay window), and loose panes that
  connect to nothing;
* staircases you can't use: a step with a ceiling or wall over it, a top step with no floor to
  step onto, or a bottom step you can't get to.

Run it with no arguments for a summary of every prefab, or name prefabs to list each problem.
It exits with status 1 if anything is found, so the asset generator can refuse to write broken
buildings.
"""
import sys

import prefab_designs

# local axes: u is east, v is north (front faces south)
DELTA = {"north": (0, 1), "south": (0, -1), "east": (1, 0), "west": (-1, 0)}

# blocks you can walk through
PASSABLE_SUFFIX = ("_carpet", "_pressure_plate", "_button", "_sapling", "_tulip", "torch", "_sign", "_banner", "rail",
                   "_flower", "_vine", "_vines")
PASSABLE = {"minecraft:air", "minecraft:light", "minecraft:grass", "minecraft:tall_grass", "minecraft:fern",
            "minecraft:poppy", "minecraft:dandelion", "minecraft:cornflower", "minecraft:oxeye_daisy",
            "minecraft:allium", "minecraft:azure_bluet", "minecraft:lily_of_the_valley", "minecraft:blue_orchid",
            "minecraft:sweet_berry_bush", "minecraft:redstone_wire", "minecraft:lever", "minecraft:tripwire",
            "minecraft:snow", "minecraft:water", "minecraft:lily_pad", "minecraft:peony", "minecraft:rose_bush",
            "minecraft:lilac", "minecraft:sunflower", "minecraft:large_fern", "minecraft:dead_bush",
            "minecraft:flower_pot", "minecraft:potted_red_tulip", "minecraft:cave_vines"}

# blocks that are not full cubes, so a pane beside them doesn't join on
PARTIAL_SUFFIX = ("_stairs", "_slab", "_fence", "_fence_gate", "_door", "_trapdoor", "_wall", "_pane", "_carpet",
                  "_button", "_pressure_plate", "_sign", "_banner", "_bed", "_candle", "torch", "_rod", "_chain",
                  "_lantern", "_leaves", "_bars", "_window", "_glazing", "rail")
PARTIAL = {"minecraft:air", "minecraft:light", "minecraft:lectern", "minecraft:anvil", "minecraft:cauldron",
           "minecraft:flower_pot", "minecraft:daylight_detector", "minecraft:lightning_rod", "minecraft:end_rod",
           "minecraft:bell", "minecraft:grindstone", "minecraft:hopper", "minecraft:brewing_stand",
           "minecraft:enchanting_table", "minecraft:composter", "minecraft:chest", "minecraft:barrel",
           "minecraft:bamboo", "minecraft:scaffolding", "minecraft:campfire", "minecraft:stonecutter",
           "minecraft:dirt_path"}

PANE_SUFFIX = ("_pane", "_window", "office_glazing", "iron_bars")


def passable(block):
    n = block.name
    return n in PASSABLE or n.endswith(PASSABLE_SUFFIX) or (n.startswith("minecraft:potted_"))


def full_cube(block):
    n = block.name
    if n in PARTIAL or n.endswith(PARTIAL_SUFFIX) or passable(block):
        return False
    if n.startswith("ptmuk:") and not _ptmuk_full(n):
        return False
    return True


_PTMUK_FULL_WORDS = ("brick", "render", "pebbledash", "stone", "faience", "roof_", "paving", "tarmac", "setts",
                     "kerb", "tactile")


def _ptmuk_full(n):
    base = n.split(":")[1]
    return any(w in base for w in _PTMUK_FULL_WORDS) and not base.endswith(("_stairs", "_slab"))


def is_pane(block):
    return block.name.endswith(PANE_SUFFIX)


def stands_on(block):
    """Something you can stand on top of: a full block, a top slab, stairs, or a bottom slab (half a step)."""
    if block.name == "minecraft:air" or passable(block):
        return False
    return True


class Grid:
    def __init__(self, build):
        self.b = build
        self.cells = build.cells

    def get(self, u, y, v):
        if not self.b.inside(u, y, v):
            return None
        return self.b.get(u, y, v)

    def solid(self, u, y, v):
        blk = self.get(u, y, v)
        return blk is not None and not passable(blk)

    def clear(self, u, y, v):
        blk = self.get(u, y, v)
        return blk is None or passable(blk)


# ------------------------------------------------------------------ panes

def outside(g):
    """Air reachable from the edge of the structure without going through a wall, door or window."""
    b = g.b
    seen = set()
    stack = []
    for u in range(b.W):
        for y in range(-b.sink, b.H):
            for v in range(b.D):
                if (u in (0, b.W - 1) or v in (0, b.D - 1) or y == b.H - 1) and _open(g, u, y, v):
                    seen.add((u, y, v))
                    stack.append((u, y, v))
    while stack:
        u, y, v = stack.pop()
        for du, dy, dv in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            n = (u + du, y + dy, v + dv)
            if n not in seen and b.inside(*n) and _open(g, *n):
                seen.add(n)
                stack.append(n)
    return seen


def _open(g, u, y, v):
    blk = g.get(u, y, v)
    return blk is not None and passable(blk)


# the four quarters of a pane's block and the two sides each one touches
QUARTERS = {"ne": ("north", "east"), "nw": ("north", "west"), "se": ("south", "east"), "sw": ("south", "west")}
# an arm towards d separates the two quarters either side of it
ARM_SPLITS = {"north": ("nw", "ne"), "south": ("sw", "se"), "east": ("ne", "se"), "west": ("nw", "sw")}


def pane_problems(g, out_cells):
    out = []
    for (u, y, v), blk in g.cells.items():
        if not is_pane(blk):
            continue
        joins = {}
        for d, (du, dv) in DELTA.items():
            n = g.get(u + du, y, v + dv)
            joins[d] = n is not None and (is_pane(n) or full_cube(n))
        if not any(joins.values()):
            if not blk.name.endswith(("iron_bars", "stained_glass_pane")):     # bars, desk monitors
                out.append(((u, y, v), "loose pane, joins nothing", blk.name))
            continue
        # join the quarters that no arm separates, then see what each region opens onto
        parent = {q: q for q in QUARTERS}

        def find(q):
            while parent[q] != q:
                q = parent[q]
            return q
        for d, (a, c) in ARM_SPLITS.items():
            if not joins[d]:
                parent[find(a)] = find(c)
        regions = {}
        for q, sides in QUARTERS.items():
            regions.setdefault(find(q), set()).update(sides)
        for sides in regions.values():
            kinds = set()
            for d in sides:
                if joins[d]:
                    continue
                du, dv = DELTA[d]
                n = (u + du, y, v + dv)
                if not g.b.inside(*n) or n in out_cells:
                    kinds.add("out")
                elif _open(g, *n):
                    kinds.add("in")
            if kinds == {"out", "in"}:
                out.append(((u, y, v), "gap beside the pane between outside and a room: open to " +
                            "/".join(sorted(d for d in sides if not joins[d])), blk.name))
                break
    return out


# ------------------------------------------------------------------ stairs

def _stair_dir(blk):
    if not blk.name.endswith("_stairs") or blk.props.get("half") != "bottom":
        return None
    return DELTA.get(blk.props.get("facing"))


def staircases(g):
    """Runs of three or more bottom stairs, each one up and along from the last (roofs left out)."""
    runs = []
    for (u, y, v), blk in g.cells.items():
        d = _stair_dir(blk)
        if d is None or (u, y, v) in g.b.roof_cells or "roof_" in blk.name or "thatch" in blk.name:
            continue
        du, dv = d
        prev = g.get(u - du, y - 1, v - dv)
        if prev is not None and _stair_dir(prev) == d:
            continue
        run = [(u, y, v)]
        while True:
            pu, py, pv = run[-1]
            nxt = g.get(pu + du, py + 1, pv + dv)
            if nxt is None or _stair_dir(nxt) != d:
                break
            run.append((pu + du, py + 1, pv + dv))
        if len(run) >= 3:
            runs.append((run, d, blk.name))
    return runs


def _standable(g, u, y, v):
    """Can you stand with your feet in cell (u, y, v)?"""
    return g.clear(u, y, v) and g.clear(u, y + 1, v) and g.solid(u, y - 1, v) and stands_on(g.get(u, y - 1, v))


def stair_problems(g):
    """Walking up, you step onto the next stair while still half over the last one, which lifts your head
    a third of a block above it: every step but the top needs three clear blocks over it, not two."""
    out = []
    for run, (du, dv), name in staircases(g):
        perp = [(dv, du), (-dv, -du)]
        for k, (u, y, v) in enumerate(run):
            for h in ((1, 2) if k == len(run) - 1 else (1, 2, 3)):
                if not g.clear(u, y + h, v):
                    out.append(((u, y + h, v), f"no headroom over step {k + 1} of {len(run)}", g.get(u, y + h, v).name))
                    break
        # the top: step off forwards or to either side, onto a floor at the top step's level
        u, y, v = run[-1]
        exits = [(u + du, y + 1, v + dv)] + [(u + a, y + 1, v + c) for a, c in perp]
        if not any(_standable(g, *e) for e in exits):
            out.append(((u, y, v), f"nowhere to step off the top of a {len(run)}-step stair", name))
        # the bottom: walk on from in front or from either side, with room for your head as you step up
        u, y, v = run[0]
        entries = [(u - du, y, v - dv)] + [(u + a, y, v + c) for a, c in perp]
        if not any(_standable(g, *e) and g.clear(e[0], e[1] + 2, e[2]) for e in entries):
            out.append(((u, y, v), f"no way onto the bottom of a {len(run)}-step stair", name))
    return out


# ------------------------------------------------------------------ getting round the building

def _walk_clear(g, u, y, v):
    blk = g.get(u, y, v)
    return blk is None or passable(blk) or blk.name.endswith(("_door", "_fence_gate")) or (
        blk.name.endswith("_trapdoor") and "iron" not in blk.name)          # wooden ones open by hand


def _feet_ok(g, u, y, v):
    """Feet in (u, y, v): room for a player (doors open) and something to stand on."""
    if not (_walk_clear(g, u, y, v) and _walk_clear(g, u, y + 1, v)):
        return False
    below = g.get(u, y - 1, v)
    return below is not None and not passable(below) and not below.name.endswith(("_door", "_fence_gate"))


def _low_step(blk):
    """A step you walk up without jumping: stairs, or a bottom slab."""
    return blk is not None and (blk.name.endswith("_stairs") and blk.props.get("half") == "bottom" or
                                blk.name.endswith("_slab") and blk.props.get("type") == "bottom")


FURNITURE_TOPS = ("barrel", "bookshelf", "hay_block", "iron_block", "furnace", "smoker", "crafting_table", "loom",
                  "jukebox", "note_block", "cartography_table", "fletching_table", "smithing_table", "_bed",
                  "white_concrete", "glass", "_log", "_wood", "pumpkin", "melon", "target", "_shulker_box")


def _floor(blk):
    """A real floor to stand on (not the top of a cupboard or a bed)."""
    n = blk.name
    if n.endswith("_slab") or n.endswith("_stairs"):
        return True
    return full_cube(blk) and not n.endswith(FURNITURE_TOPS)


def walkable(g):
    stand = set()
    for (u, y, v), blk in g.cells.items():
        if _feet_ok(g, u, y + 1, v):
            stand.add((u, y + 1, v))
    seen, todo = set(), []
    for p in stand:
        u, y, v = p
        if u in (0, g.b.W - 1) or v in (0, g.b.D - 1):
            seen.add(p)
            todo.append(p)
    while todo:
        u, y, v = todo.pop()
        head3 = _walk_clear(g, u, y + 2, v)
        for du, dv in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nu, nv = u + du, v + dv
            for dy in (0, 1, -1, -2, -3):
                n = (nu, y + dy, nv)
                if n not in stand or n in seen:
                    continue
                if dy == 1:
                    step = _low_step(g.get(nu, y, nv)) or _low_step(g.get(u, y - 1, v))
                    if not (step or head3):
                        continue
                if dy < 0 and not all(_walk_clear(g, nu, y + k, nv) for k in range(dy, 2)):
                    continue
                seen.add(n)
                todo.append(n)
                break
    return stand, seen


def reach_problems(g, out_cells):
    """Rooms you can't get into from outside by walking, climbing stairs, jumping onto things or
    dropping down (attic spaces under the roof slopes are left out)."""
    b = g.b
    stand, seen = walkable(g)
    lost = {p for p in stand - seen if p[1] < b.H and p not in out_cells and _floor(g.get(p[0], p[1] - 1, p[2]))}
    out = []
    done = set()
    for p in sorted(lost):
        if p in done:
            continue
        group, todo = [], [p]
        done.add(p)
        while todo:
            u, y, v = todo.pop()
            group.append((u, y, v))
            for du, dy, dv in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (1, 1, 0), (-1, 1, 0), (0, 1, 1),
                               (0, 1, -1), (1, -1, 0), (-1, -1, 0), (0, -1, 1), (0, -1, -1)):
                n = (u + du, y + dy, v + dv)
                if n in lost and n not in done:
                    done.add(n)
                    todo.append(n)
        under_roof = all(any((u, yy, v) in b.roof_cells for yy in range(y + 1, b.H)) for (u, y, v) in group)
        lit = False                          # an attic has no windows or doors of its own
        for (u, y, v) in group:
            for du, dv in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                for dy in (0, 1):
                    n = g.get(u + du, y + dy, v + dv)
                    if n is not None and (is_pane(n) or n.name.endswith("_door")):
                        lit = True
        attic = under_roof and not lit and min(c[1] for c in group) > 2      # lofts are upstairs, garages aren't
        if len(group) >= 4 and not attic:
            us = [c[0] for c in group]
            ys = [c[1] for c in group]
            vs = [c[2] for c in group]
            out.append(((min(us), min(ys), min(vs)), f"room of {len(group)} can't be reached: u {min(us)}-{max(us)}, "
                        f"level {min(ys)}-{max(ys)}, v {min(vs)}-{max(vs)}", ""))
    return out


def check(build):
    g = Grid(build)
    out_cells = outside(g)
    return pane_problems(g, out_cells), stair_problems(g) + reach_problems(g, out_cells)


def main(names):
    builds = prefab_designs.all_prefabs()
    bad = 0
    for b in builds:
        if names and b.ident not in names:
            continue
        panes, stairs_ = check(b)
        if panes or stairs_:
            bad += 1
        print(f"{b.ident:28s} panes {len(panes):4d}   stairs {len(stairs_):3d}")
        if names:
            for (pos, msg, blk) in panes + stairs_:
                print(f"    {pos}  {msg}  [{blk}]")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
