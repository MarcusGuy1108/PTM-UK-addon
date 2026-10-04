#!/usr/bin/env python3
"""Generates textures, block models, blockstates, item models, lang, tags and loot tables
for the UK traffic signals.

The signal heads are built from cuboids in code so every attachment/rotation/aspect
combination stays consistent. Head placement mirrors PTM2's own 3-aspect light
(traffic_regular3) so our heads line up with PTM2 streetposts and walls.

Run from the repo root:  python3 tools/generate_assets.py
Requires Pillow (pip install pillow).
"""
import json
import math
import random
import shutil
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

MOD_ID = "ptmuk"
ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets" / MOD_ID
DATA = ROOT / "src/main/resources/data"

# PTM2 TrafficLightColors ids -> which aspects are lit on a UK 3-aspect head.
# red_amber is only meaningful for YELLOW (id 2), see UkTrafficSignal.
ASPECTS = ["off", "red", "red_amber", "amber", "amber_flash", "green"]
STATE_TO_ASPECT = {
    0: "off",
    1: "red",
    2: "amber",
    3: "amber_flash",          # PTM night mode (blinking yellow)
    4: "green",
    5: "green",                # UK signals never flash green
    6: "red", 7: "amber", 8: "amber_flash", 9: "green", 10: "green",
    11: "red", 12: "amber", 13: "amber_flash", 14: "green", 15: "green",
}
LIT = {
    "off": (),
    "red": ("red",),
    "red_amber": ("red", "amber"),
    "amber": ("amber",),
    "amber_flash": ("amber_flash",),
    "green": ("green",),
}

# Offsets (in pixels) of the head for each PTM2 attachment, copied from traffic_regular3 models.
ATTACHMENTS = ["center", "wall", "left", "right", "post", "left_post", "right_post"]
STRAIGHT_OFFSET = {
    "center": (0, 0), "wall": (0, 5), "left": (5, 0), "right": (-5, 0),
    "post": (0, 11), "left_post": (11, 0), "right_post": (-11, 0),
}
# Diagonal (45 degree) placements only ever use these three attachments.
DIAGONAL_OFFSET = {"center": 0.0, "wall": 9.3, "post": 17.4}

STYLES = ["modern", "classic"]

random.seed(1108)


# ---------------------------------------------------------------- textures

def save_png(img, name):
    path = ASSETS / "textures/block" / f"{name}.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)


def noisy_fill(size, base, spread, vertical_shade=0):
    img = Image.new("RGBA", (size, size))
    px = img.load()
    for y in range(size):
        shade = int(vertical_shade * (0.5 - y / (size - 1)))
        for x in range(size):
            n = random.randint(-spread, spread)
            px[x, y] = tuple(max(0, min(255, c + n + shade)) for c in base) + (255,)
    return img


def housing_modern():
    img = noisy_fill(16, (26, 27, 29), 3, vertical_shade=6)
    d = ImageDraw.Draw(img)
    # moulded seams between the three aspect modules
    for y in (5, 10):
        d.line([(0, y), (15, y)], fill=(14, 14, 16, 255))
    return img


def housing_classic():
    img = noisy_fill(16, (33, 34, 33), 5, vertical_shade=10)
    d = ImageDraw.Draw(img)
    # cast aluminium ribs and door hinge line
    for y in range(0, 16, 3):
        d.line([(0, y), (15, y)], fill=(24, 25, 24, 255))
    d.line([(15, 0), (15, 15)], fill=(52, 53, 51, 255))
    return img


def board():
    return noisy_fill(16, (13, 13, 14), 2)


def border(color, glitter):
    img = noisy_fill(16, color, 6)
    px = img.load()
    # retroreflective micro-prism sparkle
    for _ in range(glitter):
        x, y = random.randrange(16), random.randrange(16)
        px[x, y] = tuple(min(255, c + 30) for c in color) + (255,)
    return img


UK_COLOURS = {
    # (lit core, lit edge, unlit glass) -- UK green is distinctly blue-green
    "red": ((255, 214, 196), (235, 28, 18), (58, 10, 8)),
    "amber": ((255, 246, 200), (255, 158, 0), (62, 38, 4)),
    "green": ((214, 255, 240), (0, 214, 150), (6, 44, 34)),
}


def radial(size, inner, outer, radius, power=1.6):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    c = (size - 1) / 2
    for y in range(size):
        for x in range(size):
            r = math.hypot(x - c, y - c) / radius
            if r <= 1:
                t = r ** power
                px[x, y] = tuple(int(inner[i] * (1 - t) + outer[i] * t) for i in range(3)) + (255,)
    return img


def lens_base(size=32):
    """Black faceplate with a chrome-less recessed lens ring."""
    img = Image.new("RGBA", (size, size), (12, 12, 13, 255))
    d = ImageDraw.Draw(img)
    d.ellipse([1, 1, size - 2, size - 2], fill=(5, 5, 6, 255))
    return img


def led_lens(colour, lit):
    core, edge, glass = UK_COLOURS[colour]
    size = 32
    img = lens_base(size)
    c = (size - 1) / 2
    radius = 13.0
    if lit:
        glow = radial(size, core, edge, radius, power=0.9)
        img.alpha_composite(glow)
    else:
        img.alpha_composite(radial(size, tuple(int(v * 0.55) for v in glass), (8, 8, 9), radius, 1.0))
    px = img.load()
    # hexagonal LED grid
    for row in range(-7, 8):
        for col in range(-7, 8):
            x = c + col * 1.9 + (0.95 if row % 2 else 0)
            y = c + row * 1.65
            if math.hypot(x - c, y - c) > radius - 1.2:
                continue
            ix, iy = int(round(x)), int(round(y))
            if lit:
                px[ix, iy] = tuple(min(255, int(v * 0.6 + 255 * 0.4)) for v in core) + (255,)
            else:
                px[ix, iy] = tuple(min(255, int(v * 0.9) + 10) for v in glass) + (255,)
    # smoked outer bezel ring
    d = ImageDraw.Draw(img)
    d.ellipse([c - radius - 1, c - radius - 1, c + radius + 1, c + radius + 1], outline=(30, 30, 32, 255))
    return img


def incandescent_lens(colour, lit):
    core, edge, glass = UK_COLOURS[colour]
    size = 32
    img = lens_base(size)
    c = (size - 1) / 2
    radius = 13.5
    if lit:
        body = radial(size, core, edge, radius, power=0.7)
    else:
        # coloured glass is visible even when unlit, catching a little daylight at the top
        body = radial(size, tuple(min(255, int(v * 1.7)) for v in glass), glass, radius, power=1.2)
    img.alpha_composite(body)
    px = img.load()
    # fresnel rings
    for ring in (4.5, 7.5, 10.5, 12.8):
        for a in range(0, 360, 3):
            x = int(round(c + ring * math.cos(math.radians(a))))
            y = int(round(c + ring * math.sin(math.radians(a))))
            r, g, b, _ = px[x, y]
            k = 0.82 if lit else 1.25
            px[x, y] = (min(255, int(r * k)), min(255, int(g * k)), min(255, int(b * k)), 255)
    if not lit:
        # sun-phantom highlight
        d = ImageDraw.Draw(img)
        d.arc([c - 10, c - 11, c + 10, c + 9], 200, 290, fill=(150, 150, 150, 255))
    d = ImageDraw.Draw(img)
    d.ellipse([c - radius - 1, c - radius - 1, c + radius + 1, c + radius + 1], outline=(40, 41, 40, 255))
    return img


def flash_strip(on, off):
    strip = Image.new("RGBA", (on.width, on.height * 2))
    strip.paste(on, (0, 0))
    strip.paste(off, (0, on.height))
    return strip


def write_textures():
    tex_dir = ASSETS / "textures/block"
    if tex_dir.exists():
        shutil.rmtree(tex_dir)
    save_png(housing_modern(), "signal/housing_modern")
    save_png(housing_classic(), "signal/housing_classic")
    save_png(board(), "signal/backing_board")
    save_png(border((242, 196, 0), 40), "signal/border_yellow")
    save_png(border((236, 236, 232), 25), "signal/border_white")
    for style, make in (("modern", led_lens), ("classic", incandescent_lens)):
        for colour in ("red", "amber", "green"):
            save_png(make(colour, True), f"signal/{style}_{colour}_on")
            save_png(make(colour, False), f"signal/{style}_{colour}_off")
        save_png(flash_strip(make("amber", True), make("amber", False)), f"signal/{style}_amber_flash")
        meta = ASSETS / "textures/block" / f"signal/{style}_amber_flash.png.mcmeta"
        meta.write_text(json.dumps({"animation": {"frametime": 10}}, indent=2) + "\n")


# ---------------------------------------------------------------- models

def faces(tex, uv=None, only=None):
    out = {}
    for f in ("north", "east", "south", "west", "up", "down"):
        if only and f not in only:
            continue
        face = {"texture": tex}
        if uv:
            face["uv"] = uv
        out[f] = face
    return out


def face_uv(face, frm, to):
    """Explicit UV for a face, kept inside 0..16. Without it Minecraft derives UVs from the
    element position, and elements outside the block (offset heads, board edges) would
    sample neighbouring sprites in the texture atlas."""
    x1, y1, z1 = frm
    x2, y2, z2 = to
    if face in ("north", "south"):
        u1, v1, u2, v2 = x1, 16 - y2, x2, 16 - y1
    elif face in ("east", "west"):
        u1, v1, u2, v2 = z1, 16 - y2, z2, 16 - y1
    else:
        u1, v1, u2, v2 = x1, z1, x2, z2
    out = []
    for a, b in ((u1, u2), (v1, v2)):
        size = min(b - a, 16)
        a = min(max(a, 0), 16 - size)
        out.append((round(a, 3), round(a + size, 3)))
    return [out[0][0], out[1][0], out[0][1], out[1][1]]


def cube(frm, to, face_map, emissive=False):
    for name, face in face_map.items():
        face.setdefault("uv", face_uv(name, frm, to))
    el = {"from": list(frm), "to": list(to), "faces": face_map}
    if emissive:
        el["shade"] = False
        el["forge_data"] = {"block_light": 15, "sky_light": 15}
    return el


ASPECT_ROWS = {"red": 11, "amber": 6, "green": 1}  # lens bottom y (px)


def head_elements(style, lit):
    """Elements of one head in the 'center' frame, front facing north (-Z)."""
    els = []
    housing = "#housing"
    classic = style == "classic"

    # backing board with retroreflective border (in front of the board, behind the housing)
    x1, x2, y1, y2 = 2, 14, -1, 17
    els.append(cube((x1, y1, 11), (x2, y2, 11.5), faces("#board")))
    b = 0.75
    for frm, to in (
        ((x1, y2 - b, 10.9), (x2, y2, 11)),
        ((x1, y1, 10.9), (x2, y1 + b, 11)),
        ((x1, y1 + b, 10.9), (x1 + b, y2 - b, 11)),
        ((x2 - b, y1 + b, 10.9), (x2, y2 - b, 11)),
    ):
        els.append(cube(frm, to, faces("#border", only=("north", "up", "down", "east", "west"))))

    # housing
    if classic:
        els.append(cube((4.75, 0, 7), (11.25, 16, 11), faces(housing)))
        els.append(cube((5.25, 16, 7.5), (10.75, 16.5, 10.5), faces(housing)))  # domed cap
        els.append(cube((11.25, 2, 8), (11.6, 14, 9), faces(housing)))           # door hinge
    else:
        els.append(cube((5, 0, 7), (11, 16, 11), faces(housing)))

    # lenses and hoods
    hood_front = 3.5 if classic else 5.0
    for colour, y0 in ASPECT_ROWS.items():
        if colour == "amber" and "amber_flash" in lit:
            lens_tex, on = "#amber_flash", True
        else:
            on = colour in lit
            lens_tex = f"#{colour}_{'on' if on else 'off'}"
        els.append(cube((6, y0, 6.5), (10, y0 + 4, 7),
                        {"north": {"texture": lens_tex, "uv": [0, 0, 16, 16]},
                         "east": {"texture": housing}, "west": {"texture": housing},
                         "up": {"texture": housing}, "down": {"texture": housing}},
                        emissive=on))
        top = y0 + 4.6
        side_bottom = y0 + (0.6 if classic else 1.6)
        els.append(cube((5.4, top, hood_front), (10.6, top + 0.5, 7), faces(housing)))
        els.append(cube((5.4, side_bottom, hood_front), (5.9, top, 7), faces(housing)))
        els.append(cube((10.1, side_bottom, hood_front), (10.6, top, 7), faces(housing)))
    return els


def place(elements, attachment, diagonal):
    out = []
    for el in elements:
        el = json.loads(json.dumps(el))
        if diagonal:
            dz = DIAGONAL_OFFSET[attachment]
            el["from"][2] += dz
            el["to"][2] += dz
            el["rotation"] = {"angle": -45.0, "axis": "y", "origin": [8, 8, 8]}
        else:
            dx, dz = STRAIGHT_OFFSET[attachment]
            el["from"][0] += dx
            el["to"][0] += dx
            el["from"][2] += dz
            el["to"][2] += dz
        out.append(el)
    return out


def textures_for(style):
    t = {
        "particle": f"{MOD_ID}:block/signal/housing_{style}",
        "housing": f"{MOD_ID}:block/signal/housing_{style}",
        "board": f"{MOD_ID}:block/signal/backing_board",
        "border": f"{MOD_ID}:block/signal/border_{'yellow' if style == 'modern' else 'white'}",
        "amber_flash": f"{MOD_ID}:block/signal/{style}_amber_flash",
    }
    for colour in ("red", "amber", "green"):
        for s in ("on", "off"):
            t[f"{colour}_{s}"] = f"{MOD_ID}:block/signal/{style}_{colour}_{s}"
    return t


def model_name(style, attachment, diagonal, aspect):
    return f"uk_signal_{style}/{attachment}{'45' if diagonal else ''}_{aspect}"


def write_json(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n")


def write_models():
    model_dir = ASSETS / "models/block"
    if model_dir.exists():
        shutil.rmtree(model_dir)
    for style in STYLES:
        for aspect in ASPECTS:
            base = head_elements(style, LIT[aspect])
            for attachment in ATTACHMENTS:
                for diagonal in (False, True):
                    if diagonal and attachment not in DIAGONAL_OFFSET:
                        continue
                    write_json(model_dir / f"{model_name(style, attachment, diagonal, aspect)}.json", {
                        "textures": textures_for(style),
                        "elements": place(base, attachment, diagonal),
                    })


def write_blockstates():
    for style in STYLES:
        variants = {}
        for attachment in ATTACHMENTS:
            for rotation in range(8):
                diagonal = rotation % 2 == 1 and attachment in DIAGONAL_OFFSET
                for state, aspect in STATE_TO_ASPECT.items():
                    for red_amber in (False, True):
                        shown = "red_amber" if (red_amber and state == 2) else aspect
                        v = {"model": f"{MOD_ID}:block/{model_name(style, attachment, diagonal, shown)}"}
                        y = 90 * (rotation // 2)
                        if y:
                            v["y"] = y
                        key = f"attachment={attachment},red_amber={str(red_amber).lower()},rotation={rotation},state={state}"
                        variants[key] = v
        write_json(ASSETS / f"blockstates/uk_signal_{style}.json", {"variants": variants})


def write_items():
    for style in STYLES:
        write_json(ASSETS / f"models/item/uk_signal_{style}.json", {
            "parent": f"{MOD_ID}:block/{model_name(style, 'center', False, 'red_amber')}",
            "display": {
                "gui": {"rotation": [15, 200, 0], "translation": [0, 0, 0], "scale": [0.7, 0.7, 0.7]},
                "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.4, 0.4, 0.4]},
                "fixed": {"rotation": [0, 180, 0], "scale": [0.6, 0.6, 0.6]},
                "thirdperson_righthand": {"rotation": [75, 225, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
                "firstperson_righthand": {"rotation": [0, 225, 0], "scale": [0.4, 0.4, 0.4]},
                "firstperson_lefthand": {"rotation": [0, 45, 0], "scale": [0.4, 0.4, 0.4]},
            },
        })


def write_lang():
    write_json(ASSETS / "lang/en_us.json", {
        "itemGroup.ptmuk.main": "PTM UK Addon",
        "block.ptmuk.uk_signal_modern": "UK Traffic Signal (Modern LED)",
        "block.ptmuk.uk_signal_classic": "UK Traffic Signal (Classic)",
    })


def write_data():
    blocks = [f"{MOD_ID}:uk_signal_{s}" for s in STYLES]
    write_json(DATA / "ptm2/tags/blocks/traffic_lights.json", {"replace": False, "values": blocks})
    write_json(DATA / "minecraft/tags/blocks/mineable/pickaxe.json", {"replace": False, "values": blocks})
    for style in STYLES:
        name = f"uk_signal_{style}"
        write_json(DATA / f"{MOD_ID}/loot_tables/blocks/{name}.json", {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1,
                "entries": [{"type": "minecraft:item", "name": f"{MOD_ID}:{name}"}],
                "conditions": [{"condition": "minecraft:survives_explosion"}],
            }],
        })


if __name__ == "__main__":
    write_textures()
    write_models()
    write_blockstates()
    write_items()
    write_lang()
    write_data()
    print("assets generated")
