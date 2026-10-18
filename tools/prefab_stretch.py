"""Makes prefabs bigger by doubling slices of them: chosen columns (u), rows (v) and layers (y)
are repeated once, so rooms, windows, walls and gardens grow and everything stays joined up.
Slices are picked where they do no harm (through rooms and gardens, not through doors, beds,
two-block furniture or walls running the same way), and a layer is added at the top of each
storey (5-block storeys instead of 4). Staircases that a new layer cuts through are re-laid one
step longer (starting a block further back, finishing a block further on, or with a turned step
up onto them from the side), so they still climb a block a step."""
from prefab_lib import AIR, B, Build
import prefab_check as C

HANGING = ("pendant", "chain", "chandelier", "hanging_sign")
# which way a block's two halves (or a door's swing) lie: along u for these facings
_ALONG_U = {"north", "south"}
_TOWARDS = {d: f for f, d in C.DELTA.items()}


def _facing(blk):
    return blk.props.get("facing")


def _breaks(b, axis, p, blk):
    """Would doubling the slice through p break this block?"""
    n, f = blk.name, _facing(blk)
    across = f in (_ALONG_U if axis == "u" else {"east", "west"})           # the block's width lies along the slice axis
    if blk.nbt is not None and n != "ptmuk:shop_sign":
        return True                                            # one of a kind: a pub sign, a price board
    if n.endswith("_door") or n.endswith("_bed") and not across or blk.props.get("part") in ("left", "right") and across:
        return True
    # along a flight: it would get a flat step and lose its stairwell
    return n.endswith("_stairs") and blk.props.get("half") == "bottom" and not across and p not in b.roof_cells


def _penalty(b, cells, axis):
    """How clumsy doubling a slice (these cells) would make things: thicker walls, wider gates."""
    bad = 0
    du, dv = (0, 1) if axis == "u" else (1, 0)
    for (u, y, v), blk in cells:
        if blk.name.endswith("_fence_gate") or blk.name.endswith("_trapdoor"):
            bad += 10
        elif C.full_cube(blk) or C.is_pane(blk):
            # a wall (or window) running along the slice would get twice as thick
            a, c = b.cells.get((u + du, y, v + dv)), b.cells.get((u - du, y, v - dv))
            if a is not None and c is not None and (C.full_cube(a) or C.is_pane(a)) and (C.full_cube(c) or C.is_pane(c)):
                bad += 3
    return bad


def pick(b, axis, count):
    """The `count` least harmful slices, spread out across the building."""
    size = b.W if axis == "u" else b.D
    if count <= 0 or size < 5:
        return []
    slices = {}
    for p, blk in b.cells.items():
        slices.setdefault(p[0] if axis == "u" else p[2], []).append((p, blk))
    across = b.D if axis == "u" else b.W
    scores = {}
    for k in range(1, size - 1):
        cells = slices.get(k, [])
        if not any(_breaks(b, axis, p, blk) for p, blk in cells):
            scores[k] = _penalty(b, cells, axis) * min(1.0, 20 / across)     # a long row of houses has a lot of wall in it
    chosen = []
    for _ in range(count):
        best = None
        for k, s in scores.items():
            if k in chosen:
                continue
            # keep picks apart: being next to another pick costs a lot, being near it a little
            near = min((abs(k - c) for c in chosen), default=size)
            cost = s + (1000 if near < 2 else 40 if near < 3 else 0) + (size / (near + 1))
            if best is None or cost < best[0]:
                best = (cost, k)
        if best is None:
            break
        chosen.append(best[1])
    return sorted(chosen)


def storey_tops(b):
    """The layer just under each upper floor (and under a flat roof or attic floor): where a new
    layer makes the storey a block taller."""
    # a floor is a layer of solid blocks with open rooms under it (a wall has wall under it)
    over_room, solid, signs = {}, {}, set()
    for (u, y, v), blk in b.cells.items():
        if blk.name == "ptmuk:shop_sign":
            signs.add(y)
        if (u, y, v) not in b.roof_cells and C.full_cube(blk):
            solid[y] = solid.get(y, 0) + 1
            if y >= 3 and b.get(u, y - 1, v).name == "minecraft:air":
                over_room[y] = over_room.get(y, 0) + 1
    out = []
    for y in sorted(over_room):
        if over_room[y] >= 12 and (not out or y - 1 - out[-1] >= 3):
            top, floor = y - 1, out[-1] + 1 if out else 0
            # partitions stopping short of the ceiling, or a shop sign (which would read twice): make
            # the layer under them taller (but never through a door, which reaches floor + 2)
            if (solid.get(top, 0) < 0.8 * solid.get(top - 1, 0) or top in signs) and top - 1 > floor + 2:
                top -= 1
            out.append(top)
    return out


def _flights(b):
    """Every staircase in the build: its steps from the bottom, each one up and one along."""
    steps = {p: blk for p, blk in b.cells.items()
             if blk.name.endswith("_stairs") and blk.props.get("half") == "bottom" and p not in b.roof_cells
             and _facing(blk) in C.DELTA}
    out, used = [], set()
    for p, blk in steps.items():
        du, dv = C.DELTA[_facing(blk)]
        prev = (p[0] - du, p[1] - 1, p[2] - dv)
        if prev in steps and _facing(steps[prev]) == _facing(blk):
            continue                                           # not the bottom of its flight
        run = [p]
        while True:
            u, y, v = run[-1]
            nxt = (u + du, y + 1, v + dv)
            if nxt in steps and _facing(steps[nxt]) == _facing(blk):
                run.append(nxt)
            else:
                break
        if len(run) >= 2 and not used.intersection(run):
            used.update(run)
            out.append((run, blk))
    return out


def stretch(b, us=(), ys=(), vs=()):
    """A copy of b with each slice in us, ys and vs doubled (the copy goes just after it)."""
    us, ys, vs = sorted(us), sorted(ys), sorted(vs)

    def place(x, cuts):
        shift = sum(1 for c in cuts if c < x)
        return [x + shift, x + shift + 1] if x in cuts else [x + shift]

    nb = Build(b.ident, b.name, b.category, b.W + len(us), b.H + len(ys), b.D + len(vs), b.description, sink=b.sink)
    for (u, y, v), blk in b.cells.items():
        hanging = any(h in blk.name for h in HANGING)
        ny = place(y, ys)
        for nu in place(u, us):
            for k, yy in enumerate(ny):
                for nv in place(v, vs):
                    nb.cells[(nu, yy, nv)] = AIR if hanging and k < len(ny) - 1 else blk
                    if (u, y, v) in b.roof_cells:
                        nb.roof_cells.add((nu, yy, nv))
    # staircases a new layer cut through: re-laid a step longer (see _relay)
    if ys:
        for run, blk in sorted(_flights(b), key=lambda f: f[0][0][1]):     # lowest first: the floors above are reached by them
            y0, y1 = run[0][1], run[-1][1]
            if not any(y0 <= c <= y1 for c in ys):
                continue
            _relay(nb, run, blk, us, ys, vs, place)
    return nb


def _relay(nb, run, blk, us, ys, vs, place):
    """Re-lays one flight in the stretched build nb a step longer: starting a block further back (a
    straight flight from a hall), finishing a block further on (a dog-leg, which turns beside its
    bottom step) or with a turned step at the bottom; of the ways that pass the stair checks, the
    one that leaves the most floor reachable."""
    du, dv = C.DELTA[_facing(blk)]
    old = []
    for u, y, v in run:
        for nu in place(u, us):
            for nv in place(v, vs):
                for yy in place(y, ys):
                    old.append((nu, yy, nv))
    ty = place(run[-1][1], ys)[-1]
    by = place(run[0][1], ys)[0]
    n = ty - by + 1
    columns = [(nu, nv) for nu in place(run[0][0], us) for nv in place(run[0][2], vs)]
    top_cols = [(nu, nv) for nu in place(run[-1][0], us) for nv in place(run[-1][2], vs)]
    flight = [(nu, nv) for u, _, v in run for nu in place(u, us) for nv in place(v, vs)]
    options = [
        [(tu - du * j, ty - j, tv - dv * j, blk) for (tu, tv) in top_cols for j in range(n)],     # back from the top
        [(bu + du * k, by + k, bv + dv * k, blk) for (bu, bv) in columns for k in range(n)],     # on from the bottom
    ]
    # or where it was, a block higher, with a turned step up onto it from the hall beside the bottom
    for a, c in ((dv, du), (-dv, -du)):
        side = [(bu + a, bv + c) for (bu, bv) in columns if (bu + a, bv + c) not in flight]
        if side and nb.get(side[0][0], by, side[0][1]).name == "minecraft:air":
            turned = B(blk.name, **{**blk.props, "facing": _TOWARDS[(-a, -c)]})
            options.append([(bu + du * k, by + 1 + k, bv + dv * k, blk) for (bu, bv) in columns for k in range(n - 1)]
                           + [(side[0][0], by, side[0][1], turned)])
    best = None
    for steps in options:
        touched = set(old) | {s[:3] for s in steps}
        before = {p: nb.cells.get(p) for p in touched}
        for p in old:
            nb.cells[p] = AIR
        for u, y, v, step in steps:
            nb.cells[(u, y, v)] = step
        # only this flight's own problems count (a flight beside it may not be re-laid yet)
        mine = {(u + k * du, v + k * dv) for u, _, v, _ in steps for k in (-1, 0, 1)}
        lo, hi = min(s[1] for s in steps) - 1, max(s[1] for s in steps) + 4
        g = C.Grid(nb)
        if not [p for p in C.stair_problems(g) if (p[0][0], p[0][2]) in mine and lo <= p[0][1] <= hi]:
            reached = len(C.walkable(g)[1])                    # and the most floor you can still get to
            if best is None or reached > best[0]:
                best = (reached, steps)
        for p, x in before.items():                            # undo, try the next way
            if x is None:
                nb.cells.pop(p, None)
            else:
                nb.cells[p] = x
    for p in old:                                              # none passes: lay the first, the checker will say
        nb.cells[p] = AIR
    for u, y, v, step in best[1] if best else options[0]:
        nb.cells[(u, y, v)] = step

def enlarge(b, factor=0.3, taller=True):
    """b about `factor` wider and deeper, with 5-block storeys."""
    us = pick(b, "u", round(b.W * factor))
    vs = pick(b, "v", round(b.D * factor))
    ys = storey_tops(b) if taller else []
    return stretch(b, us, ys, vs)
