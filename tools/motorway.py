"""Motorway gantry assets: box-truss beam and legs, MS4 lane signals (one baked dot-matrix
texture per aspect, so they also show in Distant Horizons LODs), matrix sign item icon."""
import json
import math

import numpy as np
from PIL import Image, ImageDraw

G = None
TEXR = {}

# Must match LaneAspect.java
ASPECTS = ["off", "speed_70", "speed_60", "speed_50", "speed_40", "speed_30", "speed_20", "nsl", "red_x",
           "merge_left", "merge_right", "queue", "fog"]

RED = (255, 40, 30)
WHITE = (255, 250, 235)
AMBER = (255, 170, 30)


def font5x7():
    """Read the dot font from DotFont.java so the baked textures match the VMS renderer."""
    src = (G.ROOT / "src/main/java/com/ptmuk/motorway/DotFont.java").read_text()
    glyphs = {}
    for line in src.splitlines():
        line = line.strip()
        if line.startswith("GLYPHS.put("):
            ch = line[len("GLYPHS.put('"):].split("'")[0] if "\\'" not in line else "'"
            nums = line.split("{")[1].split("}")[0]
            glyphs[ch] = [int(n) for n in nums.split(",")]
    return glyphs


# ----------------------------------------------------------------- textures

def noise(size, base, amount, seed):
    rng = np.random.default_rng(seed)
    a = np.array(base, dtype=np.float32)[None, None, :] + rng.normal(0, amount, (size, size, 1))
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGB").convert("RGBA")


def steel():
    img = noise(64, (170, 174, 176), 6, 11)
    d = ImageDraw.Draw(img)
    for y in range(0, 64, 16):
        d.line((0, y, 63, y), fill=(150, 154, 157, 255))
    return img


def lattice():
    """Diagonal lacing between the chords of the truss (transparent between bars)."""
    s = 4
    n = 64 * s
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    col = (160, 164, 167, 255)
    w = int(3.2 * s)
    d.line((0, n, n, 0), fill=col, width=w)
    d.line((0, 0, n, n), fill=col, width=w)
    d.line((0, 0, 0, n), fill=col, width=w)
    d.line((n, 0, n, n), fill=col, width=w)
    return img.resize((64, 64), Image.LANCZOS)


def grating():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for x in range(0, 64, 4):
        d.rectangle((x, 0, x + 1, 63), fill=(120, 124, 128, 255))
    for y in range(0, 64, 16):
        d.rectangle((0, y, 63, y + 1), fill=(110, 114, 118, 255))
    return img


def concrete():
    img = noise(64, (168, 166, 158), 9, 5)
    return img


def housing():
    return noise(64, (40, 42, 44), 3, 7)


# lane signal face: 256 px, 32x32 dot matrix in the middle 192 px, lamps in the corners
FACE = 256
GRID = 32
PITCH = 6
ORIGIN = (FACE - GRID * PITCH) // 2


def aspect_dots(aspect, glyphs):
    """Returns {(col,row): colour} of lit dots."""
    lit = {}
    c = (GRID - 1) / 2

    def text(s, colour, scale, cy):
        width = (len(s) * 6 - 1) * scale
        x0 = (GRID - width) // 2
        y0 = int(cy - 7 * scale / 2 + 0.5)
        for i, ch in enumerate(s):
            rows = glyphs.get(ch, [0] * 7)
            for r, bits in enumerate(rows):
                for col in range(5):
                    if bits >> (4 - col) & 1:
                        for dx in range(scale):
                            for dy in range(scale):
                                lit[(x0 + (i * 6 + col) * scale + dx, y0 + r * scale + dy)] = colour

    if aspect.startswith("speed_"):
        for x in range(GRID):
            for y in range(GRID):
                if 12.6 <= math.hypot(x - c, y - c) <= 15.6:
                    lit[(x, y)] = RED
        text(aspect[6:], WHITE, 2, c + 0.5)
    elif aspect == "nsl":
        for x in range(GRID):
            for y in range(GRID):
                if math.hypot(x - c, y - c) <= 15.6 and abs(x + y - (GRID - 1)) > 3.2:
                    lit[(x, y)] = WHITE
    elif aspect == "red_x":
        for x in range(3, GRID - 3):
            for y in range(3, GRID - 3):
                if abs(x - y) <= 1.6 or abs(x + y - (GRID - 1)) <= 1.6:
                    lit[(x, y)] = RED
    elif aspect.startswith("merge_"):
        def put(x, y):
            for dx in (0, 1):
                for dy in (0, 1):
                    lit[(x + dx, y + dy)] = WHITE
        for i in range(19):            # shaft from top right to bottom left
            put(24 - i, 5 + i)
        for i in range(11):            # L-shaped head at the bottom left
            put(5 + i, 23)
            put(5, 13 + i)
        if aspect == "merge_right":
            lit = {(GRID - 1 - x, y): v for (x, y), v in lit.items()}
    elif aspect in ("queue", "fog"):
        text(aspect.upper(), AMBER, 1, c + 0.5)
    return lit


def lane_face(aspect, glyphs, lamps_on):
    """One frame. lamps_on: list of (corner, colour) to light, corners 0=TL 1=TR 2=BL 3=BR."""
    s = 4
    n = FACE * s
    img = Image.new("RGBA", (n, n), (14, 14, 15, 255))
    d = ImageDraw.Draw(img)
    lit = aspect_dots(aspect, glyphs)
    r_off, r_on = PITCH * s * 0.30, PITCH * s * 0.40
    for x in range(GRID):
        for y in range(GRID):
            cx = (ORIGIN + (x + 0.5) * PITCH) * s
            cy = (ORIGIN + (y + 0.5) * PITCH) * s
            colour = lit.get((x, y))
            if colour:
                glow = tuple(int(v * 0.35) for v in colour)
                d.ellipse((cx - r_on * 1.25, cy - r_on * 1.25, cx + r_on * 1.25, cy + r_on * 1.25), fill=glow + (255,))
                d.ellipse((cx - r_on, cy - r_on, cx + r_on, cy + r_on), fill=colour + (255,))
            else:
                d.ellipse((cx - r_off, cy - r_off, cx + r_off, cy + r_off), fill=(40, 40, 42, 255))
    corners = [(14, 14), (FACE - 14, 14), (14, FACE - 14), (FACE - 14, FACE - 14)]
    on = dict(lamps_on)
    for i, (cx, cy) in enumerate(corners):
        cx, cy = cx * s, cy * s
        rad = 10 * s
        d.ellipse((cx - rad - s * 2, cy - rad - s * 2, cx + rad + s * 2, cy + rad + s * 2), fill=(55, 56, 58, 255))
        col = on.get(i)
        if col:
            d.ellipse((cx - rad, cy - rad, cx + rad, cy + rad), fill=col + (255,))
            d.ellipse((cx - rad * 0.45, cy - rad * 0.6, cx, cy - rad * 0.15), fill=(255, 255, 230, 255))
        else:
            d.ellipse((cx - rad, cy - rad, cx + rad, cy + rad), fill=(70, 55, 30, 255) if i < 2 or True else (0, 0, 0, 255))
    return img.resize((FACE, FACE), Image.LANCZOS)


def lane_texture(aspect, glyphs):
    if aspect in ("off", "nsl"):
        frames = [lane_face(aspect, glyphs, [])]
    elif aspect == "red_x":
        frames = [lane_face(aspect, glyphs, [(0, RED)]), lane_face(aspect, glyphs, [(1, RED)])]
    else:
        frames = [lane_face(aspect, glyphs, [(0, AMBER), (3, AMBER)]),
                  lane_face(aspect, glyphs, [(1, AMBER), (2, AMBER)])]
    strip = Image.new("RGBA", (FACE, FACE * len(frames)))
    for i, f in enumerate(frames):
        strip.paste(f, (0, i * FACE))
    return G.save(strip, f"motorway/lane_{aspect}", frametime=10 if len(frames) > 1 else None)


def make_textures():
    TEXR["steel"] = G.save(steel(), "motorway/steel")
    TEXR["lattice"] = G.save(lattice(), "motorway/lattice")
    TEXR["grating"] = G.save(grating(), "motorway/grating")
    TEXR["concrete"] = G.save(concrete(), "motorway/concrete")
    TEXR["housing"] = G.save(housing(), "motorway/housing")
    glyphs = font5x7()
    for a in ASPECTS:
        TEXR["lane_" + a] = lane_texture(a, glyphs)


# ----------------------------------------------------------------- models

def box(frm, to, tex, **kw):
    return G.box(frm, to, tex, **kw)


def plane(frm, to, faces, tex):
    """Thin lattice panel visible from both sides."""
    return box(frm, to, tex, faces=faces)


def beam_elements():
    els = []
    for y, z in ((1, 1), (13, 1), (1, 13), (13, 13)):
        els.append(box((0, y, z), (16, y + 2, z + 2), "#steel", faces=("north", "south", "up", "down")))
    els.append(plane((0, 3, 1.9), (16, 13, 2.1), ("north", "south"), "#lattice"))
    els.append(plane((0, 3, 13.9), (16, 13, 14.1), ("north", "south"), "#lattice"))
    els.append(plane((0, 13.9, 3), (16, 14.1, 13), ("up", "down"), "#lattice"))
    els.append(plane((0, 1.9, 3), (16, 2.1, 13), ("up", "down"), "#lattice"))
    # maintenance walkway along the front with a handrail
    els.append(box((0, 0.6, -3.5), (16, 1.0, 1), "#grating", faces=("up", "down", "north")))
    els.append(box((0, 6.5, -3.5), (16, 7.0, -3.0), "#steel", faces=("north", "south", "up", "down")))
    els.append(box((0, 3.8, -3.5), (16, 4.1, -3.2), "#steel", faces=("north", "south", "up", "down")))
    for x in (0.5, 8.5):
        els.append(box((x, 1, -3.5), (x + 0.5, 6.5, -3.0), "#steel", faces=("north", "south", "east", "west")))
    return els


def leg_elements(base):
    els = []
    for x, z in ((2, 2), (12, 2), (2, 12), (12, 12)):
        els.append(box((x, 0, z), (x + 2, 16, z + 2), "#steel", faces=("north", "south", "east", "west")))
    els.append(plane((4, 0, 2.9), (12, 16, 3.1), ("north", "south"), "#lattice"))
    els.append(plane((4, 0, 12.9), (12, 16, 13.1), ("north", "south"), "#lattice"))
    els.append(plane((2.9, 0, 4), (3.1, 16, 12), ("east", "west"), "#lattice"))
    els.append(plane((12.9, 0, 4), (13.1, 16, 12), ("east", "west"), "#lattice"))
    if base:
        els.append(box((0.5, 0, 0.5), (15.5, 3, 15.5), "#concrete"))
        els.append(box((1.5, 3, 1.5), (14.5, 3.5, 14.5), "#steel"))
    return els


def lane_elements(aspect):
    els = [
        box((1, 0.5, 5), (15, 14.5, 11), "#housing"),
        # face: full 14x14 dot matrix panel, glowing
        box((1.2, 0.7, 4.95), (14.8, 14.3, 5.0), "#housing", faces=("north",), front=f"#face",
            emissive=True, full_front_uv=True),
        # visor over the top
        box((1, 14.5, 3), (15, 15, 5), "#housing"),
    ]
    for x in (3.5, 11.5):
        els.append(box((x, 14.5, 7.5), (x + 1, 17.2, 8.5), "#steel", faces=("north", "south", "east", "west")))
    return els


def write_facing_states(name, models_by_key, extra=None):
    """models_by_key: {state suffix (without facing) -> model}"""
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for key, mdl in models_by_key.items():
            v = {"model": mdl}
            if y:
                v["y"] = y
            variants[f"facing={facing}" + ("," + key if key else "")] = v
    G.write_json(G.ASSETS / f"blockstates/{name}.json", {"variants": variants})


def block_item(name, parent, scale=0.6, gui_y=0):
    G.write_json(G.ASSETS / f"models/item/{name}.json", {
        "parent": parent,
        "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, gui_y, 0], "scale": [scale] * 3},
                    "ground": {"translation": [0, 3, 0], "scale": [0.25] * 3},
                    "fixed": {"scale": [0.5] * 3},
                    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375] * 3},
                    "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.4] * 3},
                    "firstperson_lefthand": {"rotation": [0, 225, 0], "scale": [0.4] * 3}}})


def vms_icon():
    s = 8
    img = Image.new("RGBA", (16 * s, 16 * s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle((2, 30, 125, 98), fill=(140, 144, 148, 255))
    d.rectangle((8, 36, 119, 92), fill=(16, 16, 17, 255))
    for row, y in enumerate((46, 62, 78)):
        for x in range(18, 112, 7):
            if (x * 7 + row * 13) % 5 != 0:
                d.ellipse((x, y, x + 4, y + 4), fill=(255, 170, 30, 255))
    for cx, cy in ((14, 42), (113, 42), (14, 86), (113, 86)):
        d.ellipse((cx - 4, cy - 4, cx + 4, cy + 4), fill=(255, 170, 30, 255))
    return img.resize((16, 16), Image.LANCZOS)


def generate(gmod):
    global G
    G = gmod
    make_textures()
    mid = G.MOD_ID
    t = TEXR
    # beam
    G.write_json(G.ASSETS / "models/block/motorway/gantry_beam.json",
                 G.model({"particle": t["steel"], "steel": t["steel"], "lattice": t["lattice"], "grating": t["grating"]},
                         beam_elements(), cutout=True), compact=True)
    G.write_json(G.ASSETS / "blockstates/gantry_beam.json", {"variants": {
        "axis=x": {"model": f"{mid}:block/motorway/gantry_beam"},
        "axis=z": {"model": f"{mid}:block/motorway/gantry_beam", "y": 90}}})
    block_item("gantry_beam", f"{mid}:block/motorway/gantry_beam")
    # leg
    variants = {}
    for base in (False, True):
        n = f"motorway/gantry_leg_{'base' if base else 'mid'}"
        G.write_json(G.ASSETS / f"models/block/{n}.json",
                     G.model({"particle": t["steel"], "steel": t["steel"], "lattice": t["lattice"], "concrete": t["concrete"]},
                             leg_elements(base), cutout=True), compact=True)
        variants[f"base={str(base).lower()}"] = {"model": f"{mid}:block/{n}"}
    G.write_json(G.ASSETS / "blockstates/gantry_leg.json", {"variants": variants})
    block_item("gantry_leg", f"{mid}:block/motorway/gantry_leg_base")
    # lane signal
    keyed = {}
    for a in ASPECTS:
        n = f"motorway/lane_signal_{a}"
        G.write_json(G.ASSETS / f"models/block/{n}.json",
                     G.model({"particle": t["housing"], "housing": t["housing"], "steel": t["steel"], "face": t["lane_" + a]},
                             lane_elements(a)), compact=True)
        keyed[f"aspect={a},powered=false"] = f"{mid}:block/{n}"
        keyed[f"aspect={a},powered=true"] = f"{mid}:block/motorway/lane_signal_red_x"
    write_facing_states("lane_signal", keyed)
    block_item("lane_signal", f"{mid}:block/motorway/lane_signal_speed_50", scale=0.7)
    # matrix sign (drawn by its renderer)
    G.write_json(G.ASSETS / "models/block/motorway/matrix_sign.json", {"textures": {"particle": t["housing"]}})
    G.write_json(G.ASSETS / "blockstates/matrix_sign.json", {"variants": {"": {"model": f"{mid}:block/motorway/matrix_sign"}}})
    item_tex = G.ASSETS / "textures/item"
    item_tex.mkdir(parents=True, exist_ok=True)
    vms_icon().save(item_tex / "matrix_sign.png")
    G.write_json(G.ASSETS / "models/item/matrix_sign.json",
                 {"parent": "minecraft:item/generated", "textures": {"layer0": f"{mid}:item/matrix_sign"}})


NAMES = {"gantry_beam": "Motorway Gantry Beam", "gantry_leg": "Motorway Gantry Leg",
         "lane_signal": "Motorway Lane Signal", "matrix_sign": "Variable Message Sign"}


def lang(gmod):
    return {f"block.{gmod.MOD_ID}.{n}": title for n, title in NAMES.items()}
