"""Building prefabs: a block-placing builder, Minecraft structure (.nbt) export and thumbnails.

Coordinates used while designing are street-facing: u runs left to right as you look at the
front of the building, y is up (y = 0 is the ground layer the building stands in, replacing the
surface it is placed on) and v runs from the front boundary (v = 0) back. In the exported
structure the front faces south: x = u, z = (depth - 1) - v. The Prefab Tool turns the structure
so the front faces whoever places it.
"""
import gzip
import io
import json
import struct
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

DATA_VERSION = 3465          # Minecraft 1.20.1
HERE = Path(__file__).resolve().parent

# local directions -> block state directions in the exported structure (front faces south)
DIRS = {"front": "south", "back": "north", "left": "west", "right": "east"}
OPPOSITE = {"front": "back", "back": "front", "left": "right", "right": "left"}


# ------------------------------------------------------------------ blocks

class Block:
    __slots__ = ("name", "props", "nbt")

    def __init__(self, name, props=None, nbt=None):
        self.name = name if ":" in name else "minecraft:" + name
        self.props = dict(props or {})
        self.nbt = nbt

    def key(self):
        return self.name, tuple(sorted(self.props.items()))

    def with_props(self, **kw):
        p = dict(self.props)
        p.update({k: str(v).lower() if isinstance(v, bool) else str(v) for k, v in kw.items()})
        return Block(self.name, p, self.nbt)

    def __repr__(self):
        return f"Block({self.name}, {self.props})"


def B(name, **props):
    return Block(name, {k: str(v).lower() if isinstance(v, bool) else str(v) for k, v in props.items()})


AIR = B("air")


def stairs(material, facing, half="bottom"):
    """Stairs whose tall back is towards `facing` (a local direction)."""
    return B(material + "_stairs" if not material.endswith("_stairs") else material,
             facing=DIRS[facing], half=half, shape="straight", waterlogged=False)


def slab(material, kind="bottom"):
    return B(material + "_slab" if not material.endswith("_slab") else material, type=kind, waterlogged=False)


def light(level=13):
    return B("light", level=level, waterlogged=False)


# ------------------------------------------------------------------ builder

class Build:
    def __init__(self, ident, name, category, w, h, d, description=""):
        self.ident, self.name, self.category, self.description = ident, name, category, description
        self.W, self.H, self.D = w, h, d
        self.cells = {}

    # basic placing
    def inside(self, u, y, v):
        return 0 <= u < self.W and 0 <= y < self.H and 0 <= v < self.D

    def set(self, u, y, v, block):
        if not self.inside(u, y, v):
            raise ValueError(f"{self.ident}: ({u},{y},{v}) outside {self.W}x{self.H}x{self.D}")
        if isinstance(block, str):
            block = B(block)
        self.cells[(u, y, v)] = block

    def get(self, u, y, v):
        return self.cells.get((u, y, v), AIR)

    def clear(self, u, y, v):
        self.cells.pop((u, y, v), None)

    def fill(self, u0, y0, v0, u1, y1, v1, block):
        for u in range(min(u0, u1), max(u0, u1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for v in range(min(v0, v1), max(v0, v1) + 1):
                    if block is None:
                        self.clear(u, y, v)
                    else:
                        self.set(u, y, v, block)

    def walls(self, u0, y0, v0, u1, y1, v1, block):
        """Four walls of a box (no floor or ceiling)."""
        self.fill(u0, y0, v0, u1, y1, v0, block)
        self.fill(u0, y0, v1, u1, y1, v1, block)
        self.fill(u0, y0, v0, u0, y1, v1, block)
        self.fill(u1, y0, v0, u1, y1, v1, block)

    def replace(self, u0, y0, v0, u1, y1, v1, old, new):
        for u in range(min(u0, u1), max(u0, u1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for v in range(min(v0, v1), max(v0, v1) + 1):
                    if self.get(u, y, v).name == B(old).name:
                        self.set(u, y, v, new)

    # doors and windows
    def door(self, u, y, v, wood, facing="front", hinge="left"):
        name = wood if wood.endswith("_door") else wood + "_door"
        for half, dy in (("lower", 0), ("upper", 1)):
            self.set(u, y + dy, v, B(name, facing=DIRS[facing], half=half, hinge=hinge, open=False, powered=False))

    def window(self, u0, y0, v, w, h, kind="ptmuk:sash_window"):
        self.fill(u0, y0, v, u0 + w - 1, y0 + h - 1, v, B(kind, north=False, south=False, east=False, west=False,
                                                         waterlogged=False))

    def side_window(self, u, y0, v0, d, h, kind="ptmuk:sash_window"):
        """A window in a side wall (running front to back)."""
        self.fill(u, y0, v0, u, y0 + h - 1, v0 + d - 1, B(kind, north=False, south=False, east=False, west=False,
                                                        waterlogged=False))

    # roofs
    def gable_roof(self, u0, u1, v0, v1, y, material, ridge="ridge", gable_fill=None, end_walls=True):
        """Pitched roof over [u0, u1] x [v0, v1] with the ridge running left to right (parallel to
        the street); eaves at height y along the front (v0) and back (v1). Gable ends are filled
        with `gable_fill` under the slopes (one block in from the roof's ends) when given."""
        depth = v1 - v0 + 1
        half = depth // 2
        for k in range(half):
            yy = y + k
            for u in range(u0, u1 + 1):
                self.set(u, yy, v0 + k, stairs(material, "back"))
                self.set(u, yy, v1 - k, stairs(material, "front"))
            if gable_fill and end_walls:
                for u in (u0 + 1, u1 - 1):
                    self.fill(u, yy, v0 + k + 1, u, yy, v1 - k - 1, gable_fill)
        top = y + half
        if depth % 2:
            for u in range(u0, u1 + 1):
                self.set(u, top - 1 if half else top, v0 + half, slab(material, "top") if half else slab(material))
            # an odd depth leaves a single middle row: a top slab at the last stair height makes a ridge
        return top

    def gable_roof_side(self, u0, u1, v0, v1, y, material, gable_fill=None):
        """Pitched roof with the ridge running front to back (gable end facing the street)."""
        width = u1 - u0 + 1
        half = width // 2
        for k in range(half):
            yy = y + k
            for v in range(v0, v1 + 1):
                self.set(u0 + k, yy, v, stairs(material, "right"))
                self.set(u1 - k, yy, v, stairs(material, "left"))
            if gable_fill:
                for v in (v0 + 1, v1 - 1):
                    self.fill(u0 + k + 1, yy, v, u1 - k - 1, yy, v, gable_fill)
        top = y + half
        if width % 2:
            for v in range(v0, v1 + 1):
                self.set(u0 + half, top - 1 if half else top, v, slab(material, "top") if half else slab(material))
        return top

    def hipped_roof(self, u0, u1, v0, v1, y, material, ridge_slab=True):
        """Hipped roof: slopes on all four sides, shrinking one block per course. Minecraft
        works out the corner shapes of the stairs when the structure is placed."""
        k = 0
        while u0 + k <= u1 - k and v0 + k <= v1 - k:
            a, b, c, d = u0 + k, u1 - k, v0 + k, v1 - k
            yy = y + k
            if b - a <= 0 or d - c <= 0:
                if ridge_slab:
                    self.fill(a, yy - 1, c, b, yy - 1, d, slab(material, "top"))
                break
            for u in range(a, b + 1):
                self.set(u, yy, c, stairs(material, "back"))
                self.set(u, yy, d, stairs(material, "front"))
            for v in range(c + 1, d):
                self.set(a, yy, v, stairs(material, "right"))
                self.set(b, yy, v, stairs(material, "left"))
            k += 1
        return y + k

    def chimney(self, u0, v0, y0, y1, material="ptmuk:red_brick", w=1, d=2, pots=2):
        """Brick stack from y0 to y1 with a stone cap and terracotta pots on top."""
        self.fill(u0, y0, v0, u0 + w - 1, y1, v0 + d - 1, material)
        self.fill(u0, y1 + 1, v0, u0 + w - 1, y1 + 1, v0 + d - 1, slab("ptmuk:portland_stone"))
        # the pots stand on the brick, the stone cap fills round them
        cells = [(u0 + i, v0 + j) for i in range(w) for j in range(d)]
        for (u, v) in cells[:pots]:
            self.set(u, y1 + 1, v, B("flower_pot"))

    def light_grid(self, u0, v0, u1, v1, y, step=4, level=13):
        """Invisible light blocks just under a ceiling so rooms glow through the windows at night."""
        for u in range(u0 + 1, u1, step):
            for v in range(v0 + 1, v1, step):
                if self.get(u, y, v).name == "minecraft:air":
                    self.set(u, y, v, light(level))

    # ------------------------------------------------------------------ export

    def to_structure(self):
        W, H, D = self.W, self.H, self.D
        palette, index, blocks = [], {}, []

        def state_index(block):
            k = block.key()
            if k not in index:
                index[k] = len(palette)
                entry = {"Name": ("string", block.name)}
                if block.props:
                    entry["Properties"] = ("compound", {k2: ("string", v2) for k2, v2 in sorted(block.props.items())})
                palette.append(("compound", entry))
            return index[k]

        # every cell of the box is written (air included), so placing a prefab clears what was there
        for u in range(W):
            for y in range(H):
                for v in range(D):
                    block = self.get(u, y, v)
                    x, z = u, D - 1 - v
                    entry = {"pos": ("list", ("int", [x, y, z])), "state": ("int", state_index(block))}
                    if block.nbt:
                        entry["nbt"] = ("compound", block.nbt)
                    blocks.append(("compound", entry))
        root = {
            "DataVersion": ("int", DATA_VERSION),
            "size": ("list", ("int", [W, H, D])),
            "palette": ("list", ("compound", [p[1] for p in palette])),
            "blocks": ("list", ("compound", [b[1] for b in blocks])),
            "entities": ("list", ("end", [])),
        }
        return root

    def write_nbt(self, path):
        buf = io.BytesIO()
        _write_named(buf, "compound", "", self.to_structure())
        path.parent.mkdir(parents=True, exist_ok=True)
        with open(path, "wb") as f, gzip.GzipFile(filename="", mode="wb", fileobj=f, mtime=0) as gz:
            gz.write(buf.getvalue())


# ------------------------------------------------------------------ NBT writer (big-endian, named root)

TAG = {"end": 0, "byte": 1, "short": 2, "int": 3, "long": 4, "float": 5, "double": 6, "byte_array": 7,
       "string": 8, "list": 9, "compound": 10, "int_array": 11, "long_array": 12}


def _write_string(buf, s):
    b = s.encode("utf-8")
    buf.write(struct.pack(">H", len(b)))
    buf.write(b)


def _write_payload(buf, kind, value):
    if kind == "byte":
        buf.write(struct.pack(">b", int(value)))
    elif kind == "short":
        buf.write(struct.pack(">h", value))
    elif kind == "int":
        buf.write(struct.pack(">i", value))
    elif kind == "long":
        buf.write(struct.pack(">q", value))
    elif kind == "float":
        buf.write(struct.pack(">f", value))
    elif kind == "double":
        buf.write(struct.pack(">d", value))
    elif kind == "string":
        _write_string(buf, value)
    elif kind == "list":
        inner, items = value
        buf.write(struct.pack(">b", TAG[inner]))
        buf.write(struct.pack(">i", len(items)))
        for it in items:
            _write_payload(buf, inner, it)
    elif kind == "compound":
        for k, (kk, vv) in value.items():
            _write_named(buf, kk, k, vv)
        buf.write(b"\x00")
    elif kind == "int_array":
        buf.write(struct.pack(">i", len(value)))
        for i in value:
            buf.write(struct.pack(">i", i))
    else:
        raise ValueError(kind)


def _write_named(buf, kind, name, value):
    buf.write(struct.pack(">b", TAG[kind]))
    _write_string(buf, name)
    _write_payload(buf, kind, value)


def nbt_text(s):
    return ("string", json.dumps({"text": s}))


def sign_nbt(lines, colour="black", glowing=False, kind="minecraft:sign"):
    lines = (list(lines) + ["", "", "", ""])[:4]
    face = {"messages": ("list", ("string", [json.dumps({"text": l}) for l in lines])),
            "color": ("string", colour), "has_glowing_text": ("byte", 1 if glowing else 0)}
    return {"id": ("string", kind), "front_text": ("compound", face), "back_text": ("compound", dict(face)),
            "is_waxed": ("byte", 1)}


def shop_sign_nbt(text, sub="", board=0xFF1C2E4A, ink=0xFFFFFFFF, lit=True):
    def signed(c):
        return c - (1 << 32) if c >= (1 << 31) else c
    return {"id": ("string", "ptmuk:shop_sign"), "text": ("string", text), "sub": ("string", sub),
            "board": ("int", signed(board)), "ink": ("int", signed(ink)), "lit": ("byte", 1 if lit else 0)}


# ------------------------------------------------------------------ thumbnails

_VANILLA = None
_OURS = {}


def _vanilla():
    global _VANILLA
    if _VANILLA is None:
        _VANILLA = json.load(open(HERE / "prefab_vanilla_colours.json"))
    return _VANILLA


TINTS = {"grass_block": (96, 150, 60), "oak_leaves": (70, 120, 46), "birch_leaves": (110, 150, 80),
         "spruce_leaves": (60, 96, 60), "azalea_leaves": (90, 130, 50), "flowering_azalea_leaves": (110, 130, 80),
         "vine": (70, 120, 46), "lily_pad": (60, 120, 40), "fern": (80, 130, 60), "grass": (90, 140, 60),
         "short_grass": (90, 140, 60), "tall_grass": (90, 140, 60), "water": (60, 90, 200)}
SKIP = {"air", "light", "structure_void", "cave_air", "barrier"}


def _ours(name, assets):
    if name not in _OURS:
        col = None
        for p in (assets / f"textures/block/building/{name}.png", assets / f"textures/block/{name}.png"):
            if p.exists():
                a = np.asarray(Image.open(p).convert("RGBA")).astype(float)
                w = a[..., 3] / 255.0
                col = list((a[..., :3] * w[..., None]).sum((0, 1)) / max(w.sum(), 1)) + [255 * w.mean()]
                break
        _OURS[name] = col
    return _OURS[name]


def block_colour(block, assets):
    """Average colour (r, g, b, a) of a block for the thumbnail, or None if it isn't drawn."""
    ns, name = block.name.split(":")
    if name in SKIP:
        return None
    if ns == "ptmuk":
        if name == "shop_sign" and block.nbt:
            c = block.nbt["board"][1] & 0xFFFFFF
            return ((c >> 16) & 255, (c >> 8) & 255, c & 255, 255)
        base = name
        for suf in ("_stairs", "_slab"):
            if base.endswith(suf):
                base = base[:-len(suf)]
        col = _ours(base, assets)
        if col is None and base.startswith("wheelie_bin"):
            col = [40, 44, 40, 255]
        return tuple(col) if col else (128, 128, 128, 255)
    if name in TINTS:
        return TINTS[name] + (255,)
    v = _vanilla()
    base = name
    for suf in ("_stairs", "_slab", "_wall", "_fence_gate", "_fence", "_pressure_plate", "_button", "_wall_sign",
                "_wall_hanging_sign", "_hanging_sign", "_sign"):
        if base.endswith(suf):
            base = base[:-len(suf)]
            break
    if name.endswith("_door"):
        base = name + "_bottom"
    if name.endswith("_pane"):
        base = name[:-5]
    if name.endswith("_carpet"):
        base = name[:-7] + "_wool"
    cands = [base, base + "s", base + "_planks", base + "_block", base + "_side", base + "_top", base + "_front",
             base.replace("smooth_", "") + "_top", base.replace("brick", "bricks"), base.replace("tile", "tiles"),
             base + "_block_side", base.replace("_bed", "_wool"), base.replace("potted_", "")]
    if base in ("smooth_quartz", "quartz"):
        cands.insert(0, "quartz_block_bottom")
    if base == "smooth_stone":
        cands.insert(0, "smooth_stone")
    for c in cands:
        if c in v:
            return tuple(v[c])
    return (128, 128, 128, 255)


def render_thumbnail(build, assets, size=256):
    """Isometric view of the front and right-hand side, as a 3/4 view from the street."""
    # a front-weighted view: the street face nearly square on, a narrow slice of the right side
    AU, BU = 0.92, 0.26          # screen step for +u (right and slightly down)
    AV, BV = 0.40, -0.34         # screen step for +v (back: right and up)

    def P(u, y, v):
        return (u * AU + v * AV, u * BU + v * BV - y)

    # the view direction towards the viewer, for drawing far to near: the step (du, dyk, -1)
    # that projects to the same screen point, i.e. P(du, dyk, -1) = P(0, 0, 0)
    du = AV / AU
    dyk = du * BU - BV
    items, opaque = [], set()
    for (u, y, v), b in build.cells.items():
        col = block_colour(b, assets)
        if col is None:
            continue
        thin = b.name.endswith(("_slab", "_pane", "_door", "_trapdoor", "flower_pot", "_carpet", "_sign", "_bars",
                                "_window", "_glazing", "lantern", "_wall"))
        if col[3] >= 200 and not thin:
            opaque.add((u, y, v))
        items.append((u, y, v, b, col))
    if not items:
        return Image.new("RGBA", (size, size))
    pts = [P(u + du, y + dy, v + dv) for (u, y, v, _, _) in items for du in (0, 1) for dy in (0, 1) for dv in (0, 1)]
    xs, ys = [p[0] for p in pts], [p[1] for p in pts]
    sc, pad = 3, 8
    span = max(max(xs) - min(xs), max(ys) - min(ys))
    scale = (size - 2 * pad) * sc / span
    ox = (size * sc - (max(xs) - min(xs)) * scale) / 2 - min(xs) * scale
    oy = (size * sc - (max(ys) - min(ys)) * scale) / 2 - min(ys) * scale
    img = Image.new("RGBA", (size * sc, size * sc), (0, 0, 0, 0))
    d = ImageDraw.Draw(img, "RGBA")

    def Q(u, y, v):
        x, yy = P(u, y, v)
        return (x * scale + ox, yy * scale + oy)

    items.sort(key=lambda it: (-it[2] + du * it[0] + dyk * it[1]))     # far to near
    for (u, y, v, b, col) in items:
        y0, y1 = 0.0, 1.0
        if b.name.endswith("_slab"):
            kind = b.props.get("type", "bottom")
            y0, y1 = (0.5, 1.0) if kind == "top" else (0.0, 0.5) if kind == "bottom" else (0.0, 1.0)
        elif b.name.endswith("_carpet"):
            y1 = 0.07
        r, g, bb, a = col
        a = int(min(255, max(a, 110)))
        faces = []
        if (u, y + 1, v) not in opaque or y1 < 1:
            faces.append(([Q(u, y + y1, v), Q(u + 1, y + y1, v), Q(u + 1, y + y1, v + 1), Q(u, y + y1, v + 1)], 1.0))
        if (u, y, v - 1) not in opaque:
            faces.append(([Q(u, y + y0, v), Q(u + 1, y + y0, v), Q(u + 1, y + y1, v), Q(u, y + y1, v)], 0.86))
        if (u + 1, y, v) not in opaque:
            faces.append(([Q(u + 1, y + y0, v), Q(u + 1, y + y0, v + 1), Q(u + 1, y + y1, v + 1), Q(u + 1, y + y1, v)], 0.62))
        for poly, shade in faces:
            d.polygon(poly, fill=(int(r * shade), int(g * shade), int(bb * shade), a))
    return img.resize((size, size), Image.LANCZOS)
