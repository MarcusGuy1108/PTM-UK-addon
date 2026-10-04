#!/usr/bin/env python3
"""Generates textures, block models, blockstates, item models, lang, tags and loot tables
for the UK signals and signal accessories.

Every head is assembled from parts (housing, backing board, and one small model per lens
per look: on / off / flashing), each written pre-rotated for the four 90 degree turns.
signal_parts.json says which lens looks each PTM2 light state uses; the client
(client/SignalModels.java) bakes every part once and combines them per block state.
That keeps memory low: a blockstate-driven approach bakes the same quads thousands of times. Head placement mirrors PTM2's own lights so heads line up with PTM2
streetposts and walls.

Run from the repo root:  python3 tools/generate_assets.py
Requires Pillow (pip install pillow).
"""
import json
import math
import random
import shutil
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

MOD_ID = "ptmuk"
ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets" / MOD_ID
DATA = ROOT / "src/main/resources/data"
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"

random.seed(1108)

# Must match SignalType / SignalStyle / AccessoryType / ModBlocks.stylesFor in Java.
STYLES = ["led", "led_tunnel", "classic"]
SIGNAL_TYPES = {
    # type id: (styles, layout, boardable)
    "signal": (STYLES, "standard", True),
    "left_arrow_signal": (STYLES, "left_arrow", True),
    "right_arrow_signal": (STYLES, "right_arrow", True),
    "ahead_arrow_signal": (STYLES, "ahead_arrow", True),
    "left_filter_signal": (STYLES, "left_filter", True),
    "right_filter_signal": (STYLES, "right_filter", True),
    "cycle_signal": (["led"], "cycle", False),
    "low_level_cycle_signal": (["led"], "low_level_cycle", False),
    "pelican_signal": (["led", "classic"], "pelican", False),
    "puffin_signal": (["led"], "puffin", False),
    "toucan_signal": (["led"], "toucan", False),
}
ACCESSORIES = {
    "no_left_turn_sign": "sign",
    "no_right_turn_sign": "sign",
    "no_u_turn_sign": "sign",
    "ahead_only_sign": "sign",
    "turn_left_sign": "sign",
    "turn_right_sign": "sign",
    "except_buses_sign": "sign",
    "signal_detector": "detector",
    "push_button_unit": "push_button",
}

STYLE_NAMES = {"led": "LED", "led_tunnel": "LED, Tunnel Hoods", "classic": "Classic Bulb"}
TYPE_NAMES = {
    "signal": "UK Traffic Signal",
    "left_arrow_signal": "UK Left Arrow Signal",
    "right_arrow_signal": "UK Right Arrow Signal",
    "ahead_arrow_signal": "UK Ahead Arrow Signal",
    "left_filter_signal": "UK Left Filter Signal",
    "right_filter_signal": "UK Right Filter Signal",
    "cycle_signal": "UK Cycle Signal",
    "low_level_cycle_signal": "UK Low-Level Cycle Signal",
    "pelican_signal": "UK Far-Side Pedestrian Signal",
    "puffin_signal": "UK Puffin Near-Side Pedestrian Signal",
    "toucan_signal": "UK Toucan Signal",
}
ACCESSORY_NAMES = {
    "no_left_turn_sign": "Signal Sign: No Left Turn",
    "no_right_turn_sign": "Signal Sign: No Right Turn",
    "no_u_turn_sign": "Signal Sign: No U-Turn",
    "ahead_only_sign": "Signal Sign: Ahead Only",
    "turn_left_sign": "Signal Sign: Turn Left",
    "turn_right_sign": "Signal Sign: Turn Right",
    "except_buses_sign": "Signal Sign: Except Buses, Taxis & Cycles",
    "signal_detector": "Signal Vehicle Detector",
    "push_button_unit": "Pedestrian Push-Button Unit",
}

# Offsets (in pixels) of the head for each PTM2 attachment, copied from PTM2's light models.
ATTACHMENTS = ["center", "wall", "left", "right", "post", "left_post", "right_post"]
STRAIGHT_OFFSET = {
    "center": (0, 0), "wall": (0, 5), "left": (5, 0), "right": (-5, 0),
    "post": (0, 11), "left_post": (11, 0), "right_post": (-11, 0),
}
# Diagonal (45 degree) placements only ever use these three attachments.
DIAGONAL_OFFSET = {"center": 0.0, "wall": 9.3, "post": 17.4}


def placements():
    """(attachment, rotation, geometry key, diagonal, y rotation) for every blockstate combination."""
    for attachment in ATTACHMENTS:
        for rotation in range(8):
            diagonal = rotation % 2 == 1 and attachment in DIAGONAL_OFFSET
            yield attachment, rotation, attachment + ("45" if diagonal else ""), diagonal, 90 * (rotation // 2)


GEOMETRIES = sorted({(g, d, a) for a, _, g, d, _ in placements()})


# =================================================================== textures

TEX = ASSETS / "textures/block"


def save(img, name, frametime=None):
    path = TEX / f"{name}.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)
    if frametime:
        (TEX / f"{name}.png.mcmeta").write_text(json.dumps({"animation": {"frametime": frametime}}) + "\n")
    return f"{MOD_ID}:block/{name}"


def noisy(size, base, spread, shade=0, w=None):
    w = w or size
    img = Image.new("RGBA", (w, size))
    px = img.load()
    for y in range(size):
        s = int(shade * (0.5 - y / max(1, size - 1)))
        for x in range(w):
            n = random.randint(-spread, spread)
            px[x, y] = tuple(max(0, min(255, c + n + s)) for c in base) + (255,)
    return img


def supersample(draw_fn, size, scale=8, mode="RGBA", bg=(0, 0, 0, 0)):
    big = Image.new(mode, (size * scale, size * scale), bg)
    draw_fn(ImageDraw.Draw(big), size * scale)
    return big.resize((size, size), Image.LANCZOS)


# ---- colours: (lit core, lit edge, unlit glass)
UK = {
    "red": ((255, 225, 205), (238, 30, 18), (70, 12, 9)),
    "amber": ((255, 246, 205), (255, 150, 0), (75, 44, 4)),
    "green": ((215, 255, 238), (0, 205, 140), (6, 52, 40)),
    "white": ((255, 255, 255), (235, 235, 225), (40, 40, 40)),
}


def radial(size, inner, outer, radius, power=1.6, centre=None):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    cx, cy = centre or ((size - 1) / 2, (size - 1) / 2)
    for y in range(size):
        for x in range(size):
            r = math.hypot(x - cx, y - cy) / radius
            if r <= 1:
                t = r ** power
                px[x, y] = tuple(int(inner[i] * (1 - t) + outer[i] * t) for i in range(3)) + (255,)
    return img


def circle_mask(size, radius):
    return supersample(lambda d, s: d.ellipse([s / 2 - radius * s / size, s / 2 - radius * s / size,
                                               s / 2 + radius * s / size, s / 2 + radius * s / size], fill=255),
                       size, mode="L", bg=0)


def led_dots(size, mask, lit_colour, off_colour, pitch=2.0):
    """Hexagonal LED dot matrix inside mask. Returns RGBA with dots only."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    m = mask.load()
    rows = int(size / (pitch * 0.87)) + 2
    for row in range(rows):
        y = row * pitch * 0.87
        for col in range(int(size / pitch) + 2):
            x = col * pitch + (pitch / 2 if row % 2 else 0)
            ix, iy = int(x), int(y)
            if 0 <= ix < size and 0 <= iy < size and m[ix, iy] > 128:
                px[ix, iy] = (lit_colour if lit_colour else off_colour) + (255,)
    return img


def lens_led(colour, lit, symbol=None, size=32):
    """LED aspect. symbol is an 'L' mask (white = symbol) for arrows / cycles / figures."""
    core, edge, glass = UK[colour]
    img = Image.new("RGBA", (size, size), (10, 10, 11, 255))
    face = circle_mask(size, 15)
    dark = Image.new("RGBA", (size, size), (17, 18, 19, 255))
    img.paste(dark, (0, 0), face)
    shape = symbol if symbol is not None else circle_mask(size, 13.2)
    if lit:
        glow = radial(size, core, edge, 15, power=0.75)
        img.paste(glow, (0, 0), shape)
        img.alpha_composite(led_dots(size, shape, tuple(min(255, int(c * 0.55 + 115)) for c in core), None))
        if symbol is None:
            hot = radial(size, (255, 255, 245), core, 3.2, power=1.2)
            img.paste(hot, (0, 0), circle_mask(size, 3.2))
    else:
        faint = tuple(int(c * 0.5) + 14 for c in glass)
        img.alpha_composite(led_dots(size, shape, None, faint))
        if symbol is None:
            # the small coloured "phantom" centre visible on unlit UK LED aspects
            img.paste(Image.new("RGBA", (size, size), tuple(min(255, int(c * 2.2)) for c in glass) + (255,)),
                      (0, 0), circle_mask(size, 1.6))
    d = ImageDraw.Draw(img)
    d.ellipse([0.5, 0.5, size - 1.5, size - 1.5], outline=(32, 33, 35, 255))
    return img


def lens_classic(colour, lit, symbol=None, size=32):
    """Incandescent aspect: fresnel rings, visible bulb, coloured glass even when unlit."""
    core, edge, glass = UK[colour]
    img = Image.new("RGBA", (size, size), (14, 14, 14, 255))
    shape = circle_mask(size, 14)
    if symbol is not None:
        # stencilled aspect: black mask with the symbol cut out of it
        body = radial(size, core, edge, 14, power=0.55) if lit else radial(size, glass, glass, 14)
        img.paste(Image.new("RGBA", (size, size), (20, 20, 20, 255)), (0, 0), shape)
        img.paste(body, (0, 0), symbol)
    else:
        if lit:
            body = radial(size, core, edge, 14, power=0.6)
        else:
            body = radial(size, tuple(min(255, int(c * 1.9)) for c in glass), glass, 14, power=1.1)
        img.paste(body, (0, 0), shape)
        px = img.load()
        c = (size - 1) / 2
        for ring in (4.0, 6.8, 9.6, 12.2):
            for a in range(0, 360, 2):
                x = int(round(c + ring * math.cos(math.radians(a))))
                y = int(round(c + ring * math.sin(math.radians(a))))
                r, g, b, _ = px[x, y]
                k = 0.8 if lit else 1.35
                px[x, y] = (min(255, int(r * k)), min(255, int(g * k)), min(255, int(b * k)), 255)
        # the bulb filament hot-spot just above centre
        spot = (255, 255, 235) if lit else tuple(min(255, int(v * 3)) for v in glass)
        img.paste(Image.new("RGBA", (size, size), spot + (255,)), (0, 0),
                  supersample(lambda d, s: d.ellipse([s * 0.44, s * 0.40, s * 0.56, s * 0.52], fill=255), size, mode="L", bg=0))
    d = ImageDraw.Draw(img)
    if not lit:
        d.arc([5, 4, size - 6, size - 8], 200, 290, fill=(150, 150, 150, 255))
    d.ellipse([1, 1, size - 2, size - 2], outline=(45, 45, 44, 255))
    return img


def flash(on, off):
    strip = Image.new("RGBA", (on.width, on.height * 2))
    strip.paste(on, (0, 0))
    strip.paste(off, (0, on.height))
    return strip


# ---- symbol masks (drawn on a 32 grid, supersampled)

def mask(fn, size=32):
    return supersample(lambda d, s: fn(d, s / 32.0), size, mode="L", bg=0)


def arrow_mask(direction):
    def draw(d, k):
        # pointing up, then rotated
        pts = [(16, 4), (26, 15), (20, 15), (20, 28), (12, 28), (12, 15), (6, 15)]
        ang = {"ahead": 0, "left": 90, "right": -90}[direction]
        a = math.radians(ang)
        out = []
        for x, y in pts:
            x, y = x - 16, y - 16
            out.append(((x * math.cos(a) + y * math.sin(a) + 16) * k, (-x * math.sin(a) + y * math.cos(a) + 16) * k))
        d.polygon(out, fill=255)
    return mask(draw)


def bicycle_mask():
    def draw(d, k):
        w = 2.2 * k
        d.ellipse([3 * k, 15 * k, 13 * k, 25 * k], outline=255, width=int(w))
        d.ellipse([19 * k, 15 * k, 29 * k, 25 * k], outline=255, width=int(w))
        for a, b in (((8, 20), (14, 12)), ((14, 12), (23, 12)), ((23, 12), (24, 20)), ((8, 20), (16, 20)),
                     ((16, 20), (23, 12)), ((14, 12), (16, 20)), ((23, 12), (22, 8)), ((20, 8), (25, 8)),
                     ((12, 10), (16, 10))):
            d.line([(a[0] * k, a[1] * k), (b[0] * k, b[1] * k)], fill=255, width=int(w))
    return mask(draw)


def standing_man_mask():
    def draw(d, k):
        d.ellipse([13 * k, 3 * k, 19 * k, 9 * k], fill=255)
        d.rounded_rectangle([11 * k, 10 * k, 21 * k, 20 * k], radius=2 * k, fill=255)
        d.rectangle([9 * k, 10.5 * k, 11.5 * k, 19 * k], fill=255)
        d.rectangle([20.5 * k, 10.5 * k, 23 * k, 19 * k], fill=255)
        d.rectangle([12 * k, 19 * k, 15.5 * k, 29 * k], fill=255)
        d.rectangle([16.5 * k, 19 * k, 20 * k, 29 * k], fill=255)
    return mask(draw)


def walking_man_mask():
    def draw(d, k):
        w = int(3.4 * k)
        d.ellipse([14 * k, 3 * k, 20 * k, 9 * k], fill=255)
        d.line([(16 * k, 10 * k), (14 * k, 19 * k)], fill=255, width=int(4.5 * k))
        d.line([(15.5 * k, 12 * k), (9 * k, 17 * k)], fill=255, width=w)
        d.line([(15.5 * k, 12 * k), (22 * k, 16 * k)], fill=255, width=w)
        d.line([(14 * k, 19 * k), (9 * k, 28 * k)], fill=255, width=w)
        d.line([(14 * k, 19 * k), (20 * k, 23 * k), (21 * k, 29 * k)], fill=255, width=w)
    return mask(draw)


def square_lens(colour, lit, symbol, style, size=32):
    """Pedestrian / toucan aspect: square black lens with a lit figure."""
    core, edge, glass = UK[colour]
    img = Image.new("RGBA", (size, size), (14, 14, 15, 255))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, size - 1, size - 1], outline=(36, 37, 38, 255))
    if lit:
        body = radial(size, core, edge, 22, power=0.8)
        img.paste(body, (0, 0), symbol)
        if style == "led":
            img.alpha_composite(led_dots(size, symbol, tuple(min(255, int(c * 0.5 + 125)) for c in core), None, pitch=1.6))
    else:
        dim = tuple(int(c * 0.55) + 18 for c in glass)
        img.paste(Image.new("RGBA", (size, size), dim + (255,)), (0, 0), symbol)
        if style == "led":
            img.alpha_composite(led_dots(size, symbol, None, tuple(int(c * 0.35) + 10 for c in glass), pitch=1.6))
    return img


# ---- housings, boards, accessories

def housing(style):
    if style == "classic":
        img = noisy(16, (30, 31, 30), 5, shade=10)
        d = ImageDraw.Draw(img)
        for y in range(0, 16, 3):
            d.line([(0, y), (15, y)], fill=(22, 23, 22, 255))
        d.point([(1, 1), (14, 1), (1, 14), (14, 14)], fill=(70, 70, 66, 255))  # screws
        return img
    base = (24, 25, 27) if style == "led" else (28, 29, 31)
    img = noisy(16, base, 3, shade=6)
    d = ImageDraw.Draw(img)
    d.line([(0, 15), (15, 15)], fill=(13, 13, 15, 255))
    return img


def hood_inside():
    return noisy(16, (8, 8, 9), 2)


def board_texture(w, h, fill, border):
    """Square texture for a w x h px board: rounded corners, retroreflective white border.
    Drawn pre-squashed so it looks right once stretched over the non-square board element."""
    size = 128
    sx, sy = size / w, size / h
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    def rr(inset, colour, radius):
        # rounded rectangle with independent x / y scaling
        x1, y1, x2, y2 = inset * sx, inset * sy, size - inset * sx, size - inset * sy
        rx, ry = radius * sx, radius * sy
        d.rectangle([x1 + rx, y1, x2 - rx, y2], fill=colour)
        d.rectangle([x1, y1 + ry, x2, y2 - ry], fill=colour)
        for cx, cy in ((x1 + rx, y1 + ry), (x2 - rx, y1 + ry), (x1 + rx, y2 - ry), (x2 - rx, y2 - ry)):
            d.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=colour)

    rr(0, border + (255,), 1.6)
    rr(0.85, fill + (255,), 1.0)
    px = img.load()
    for y in range(size):
        for x in range(size):
            r, g, b, a = px[x, y]
            if a:
                n = random.randint(-4, 4)
                px[x, y] = (max(0, min(255, r + n)), max(0, min(255, g + n)), max(0, min(255, b + n)), 255)
    return img


def sign_texture(kind):
    size = 128
    img = Image.new("RGBA", (size, size), (16, 16, 17, 255))
    d = ImageDraw.Draw(img)
    c, r = size / 2, size / 2 - 5
    red, blue, black, white = (204, 20, 30), (0, 82, 160), (15, 15, 15), (250, 250, 250)

    def arrow_poly(pts, k=1.0):
        d.polygon([(c + x * k, c + y * k) for x, y in pts], fill=black)

    if kind in ("ahead_only", "turn_left", "turn_right"):
        d.ellipse([c - r, c - r, c + r, c + r], fill=white)
        d.ellipse([c - r + 3, c - r + 3, c + r - 3, c + r - 3], fill=blue)
        pts = [(0, -38), (24, -10), (9, -10), (9, 36), (-9, 36), (-9, -10), (-24, -10)]
        ang = {"ahead_only": 0, "turn_left": 90, "turn_right": -90}[kind]
        a = math.radians(ang)
        d.polygon([(c + x * math.cos(a) + y * math.sin(a), c - x * math.sin(a) + y * math.cos(a)) for x, y in pts],
                  fill=white)
    elif kind == "except_buses":
        d.ellipse([c - r, c - r, c + r, c + r], fill=white, outline=black, width=3)
        font = ImageFont.truetype(FONT, 17)
        for i, line in enumerate(["Except", "buses,", "taxis &", "cycles"]):
            tw = d.textlength(line, font=font)
            d.text((c - tw / 2, 22 + i * 21), line, fill=black, font=font)
    else:
        d.ellipse([c - r, c - r, c + r, c + r], fill=red)
        d.ellipse([c - r + 13, c - r + 13, c + r - 13, c + r - 13], fill=white)
        w = 12
        if kind in ("no_left_turn", "no_right_turn"):
            s = 1 if kind == "no_right_turn" else -1
            d.line([(c - 14 * s, c + 36), (c - 14 * s, c - 6), (c + 2 * s, c - 6)], fill=black, width=w)
            d.polygon([(c + 2 * s, c - 22), (c + 26 * s, c - 6), (c + 2 * s, c + 10)], fill=black)
            bar = [(c - 34, c + 34), (c + 34, c - 34)] if s == 1 else [(c - 34, c - 34), (c + 34, c + 34)]
        else:  # no U-turn
            d.line([(c - 16, c + 34), (c - 16, c - 8)], fill=black, width=w)
            d.arc([c - 22, c - 26, c + 22, c + 10], 180, 360, fill=black, width=w)
            d.line([(c + 16, c - 8), (c + 16, c + 8)], fill=black, width=w)
            d.polygon([(c + 2, c + 6), (c + 30, c + 6), (c + 16, c + 26)], fill=black)
            bar = [(c - 34, c - 34), (c + 34, c + 34)]
        d.line(bar, fill=red, width=11)
    return img.resize((64, 64), Image.LANCZOS)


def push_button_face():
    size = 64
    img = noisy(size, (34, 35, 37), 3)
    d = ImageDraw.Draw(img)
    d.rectangle([6, 5, size - 7, 24], fill=(8, 8, 8, 255))
    font = ImageFont.truetype(FONT, 13)
    tw = d.textlength("WAIT", font=font)
    d.text((size / 2 - tw / 2, 7), "WAIT", fill=(255, 140, 20, 255), font=font)
    small = ImageFont.truetype(FONT, 6)
    for i, line in enumerate(["PUSH BUTTON", "AND WAIT FOR", "SIGNAL"]):
        tw = d.textlength(line, font=small)
        d.text((size / 2 - tw / 2, 28 + i * 7), line, fill=(225, 225, 225, 255), font=small)
    d.ellipse([24, 49, 40, 63], fill=(190, 190, 185, 255), outline=(80, 80, 80, 255))
    return img


def write_textures():
    if TEX.exists():
        shutil.rmtree(TEX)
    t = {}
    for style in STYLES:
        t[f"housing_{style}"] = save(housing(style), f"signal/housing_{style}")
    t["hood_inside"] = save(hood_inside(), "signal/hood_inside")
    t["grey_metal"] = save(noisy(16, (120, 123, 125), 6, shade=12), "signal/grey_metal")
    t["detector_lens"] = save(noisy(16, (18, 22, 30), 3), "signal/detector_lens")
    t["push_button"] = save(push_button_face(), "signal/push_button_face")

    arrows = {d: arrow_mask(d) for d in ("left", "right", "ahead")}
    bike, standing, walking = bicycle_mask(), standing_man_mask(), walking_man_mask()
    for style in STYLES:
        make = lens_classic if style == "classic" else lens_led
        prefix = "classic" if style == "classic" else "led"  # both LED styles share lenses
        if f"{prefix}_red_on" in t:
            continue
        for colour in ("red", "amber", "green"):
            for lit in (True, False):
                t[f"{prefix}_{colour}_{'on' if lit else 'off'}"] = save(make(colour, lit), f"signal/{prefix}_{colour}_{'on' if lit else 'off'}")
        t[f"{prefix}_amber_flash"] = save(flash(make("amber", True), make("amber", False)), f"signal/{prefix}_amber_flash", 10)
        for direction, m in arrows.items():
            for lit in (True, False):
                key = f"{prefix}_green_{direction}_{'on' if lit else 'off'}"
                t[key] = save(make("green", lit, symbol=m), f"signal/{key}")
        for colour, m, name in (("red", standing, "red_man"), ("green", walking, "green_man")):
            for lit in (True, False):
                key = f"{prefix}_{name}_{'on' if lit else 'off'}"
                t[key] = save(square_lens(colour, lit, m, prefix), f"signal/{key}")
        t[f"{prefix}_green_man_flash"] = save(flash(square_lens("green", True, walking, prefix),
                                                    square_lens("green", False, walking, prefix)),
                                              f"signal/{prefix}_green_man_flash", 10)
    for colour in ("red", "amber", "green"):
        for lit in (True, False):
            key = f"led_cycle_{colour}_{'on' if lit else 'off'}"
            t[key] = save(lens_led(colour, lit, symbol=bike), f"signal/{key}")
    for lit in (True, False):
        key = f"led_green_cycle_{'on' if lit else 'off'}"
        t[key] = save(square_lens("green", lit, bike, "led"), f"signal/{key}")
    for kind in ("no_left_turn", "no_right_turn", "no_u_turn", "ahead_only", "turn_left", "turn_right", "except_buses"):
        t[f"sign_{kind}"] = save(sign_texture(kind), f"signal/sign_{kind}")
    return t


# =================================================================== geometry

def face_uv(face, frm, to):
    """Explicit UV kept inside 0..16. Without it Minecraft derives UVs from the element
    position, and elements outside the block would sample neighbouring atlas sprites."""
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


ALL_FACES = ("north", "east", "south", "west", "up", "down")


def box(frm, to, tex, faces=ALL_FACES, front=None, emissive=False, full_front_uv=False):
    """front: texture for the north face (defaults to tex)."""
    fm = {}
    for f in faces:
        t = front if (f == "north" and front) else tex
        uv = [0, 0, 16, 16] if (f == "north" and full_front_uv) else face_uv(f, frm, to)
        fm[f] = {"texture": t, "uv": uv}
    el = {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to], "faces": fm}
    if emissive:
        el["shade"] = False
        el["forge_data"] = {"block_light": 15, "sky_light": 15}
    return el


def lens(x1, y1, x2, y2, tex_key, emissive, z=6.5):
    return box((x1, y1, z), (x2, y2, z + 0.5), "#lens", faces=("north",), front=f"#{tex_key}",
               emissive=emissive, full_front_uv=True)


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


# Aspect layout per head: list of (aspect id, x1, y1, x2, y2, round). The housing module for
# each aspect is the lens rectangle grown by MARGIN.
def layout(kind):
    std = [("red", 6, 11.2, 10, 15.2, True), ("amber", 6, 6, 10, 10, True), ("green", 6, 0.8, 10, 4.8, True)]
    if kind == "standard":
        return std
    if kind in ("left_arrow", "right_arrow", "ahead_arrow"):
        return std[:2] + [("green_" + kind.split("_")[0], 6, 0.8, 10, 4.8, True)]
    if kind == "left_filter":   # viewer's left is +x
        return std + [("green_left", 12, 0.8, 16, 4.8, True)]
    if kind == "right_filter":
        return std + [("green_right", 0, 0.8, 4, 4.8, True)]
    if kind == "cycle":
        return [("cycle_red", 6, 11.2, 10, 15.2, True), ("cycle_amber", 6, 6, 10, 10, True),
                ("cycle_green", 6, 0.8, 10, 4.8, True)]
    if kind == "low_level_cycle":
        return [("cycle_red", 6.75, 9.0, 9.25, 11.5, True), ("cycle_amber", 6.75, 5.75, 9.25, 8.25, True),
                ("cycle_green", 6.75, 2.5, 9.25, 5.0, True)]
    if kind == "pelican":
        return [("red_man", 5.5, 9.5, 10.5, 14.5, False), ("green_man", 5.5, 3.5, 10.5, 8.5, False)]
    if kind == "puffin":
        return [("red_man", 6.25, 10, 9.75, 13.5, False), ("green_man", 6.25, 6, 9.75, 9.5, False)]
    if kind == "toucan":
        return [("red_man", 5.75, 9.5, 10.25, 14, False), ("green_man", 3.5, 3.5, 7.75, 7.75, False),
                ("green_cycle", 8.25, 3.5, 12.5, 7.75, False)]
    raise ValueError(kind)


def module_rects(kind):
    """Housing rectangles (x1, y1, x2, y2) the head is built from: one module per aspect,
    stacked modules meet exactly (no overlap, so no flickering coplanar faces)."""
    if kind in ("pelican", "toucan"):
        xs = [a[1] for a in layout(kind)] + [a[3] for a in layout(kind)]
        return [(min(xs) - 1.0, 2.5, max(xs) + 1.0, 15.5)]
    if kind == "puffin":
        return [(5.25, 5, 10.75, 14.5)]
    if kind == "low_level_cycle":
        return [(5.75, 1.5, 10.25, 12.5)]
    columns = {}
    for _, x1, y1, x2, y2, _ in layout(kind):
        columns.setdefault((x1, x2), []).append((y1, y2))
    rects = []
    for (x1, x2), spans in columns.items():
        spans.sort()
        for i, (y1, y2) in enumerate(spans):
            bottom = max(0.0, y1 - 0.8) if i == 0 else (spans[i - 1][1] + y1) / 2
            top = (16.0 if y2 > 15 else y2 + 0.8) if i == len(spans) - 1 else (y2 + spans[i + 1][0]) / 2
            rects.append((x1 - 1.0, bottom, x2 + 1.0, top))
    return rects


def head_bbox(kind):
    rects = module_rects(kind)
    return (min(r[0] for r in rects), min(r[1] for r in rects), max(r[2] for r in rects), max(r[3] for r in rects))


def body_elements(style, kind):
    """Housing and hoods (everything except lenses and the board), centre frame, front north."""
    els = []
    h, inside = "#housing", "#inside"
    back = 11.0
    for x1, y1, x2, y2 in module_rects(kind):
        if style == "classic":
            els.append(box((x1, y1, 7.0), (x2, y2, back - 0.6), h))
            els.append(box((x1 + 0.4, y1 + 0.4, back - 0.6), (x2 - 0.4, y2 - 0.4, back), h))  # bevelled back
        else:
            els.append(box((x1, y1, 7.0), (x2, y2, back), h))
        # dark front panel around the lenses
        els.append(box((x1 + 0.2, y1 + 0.2, 6.9), (x2 - 0.2, y2 - 0.2, 7.0), inside, faces=("north",)))
    if style == "classic" and kind not in ("pelican",):
        x2 = max(r[2] for r in module_rects(kind))
        els.append(box((x2, 2, 8.5), (x2 + 0.4, 14, 9.3), h))  # door hinge
    for aspect, x1, y1, x2, y2, rnd in layout(kind):
        els += hood(style, kind, x1, y1, x2, y2)
    return els


def hood(style, kind, x1, y1, x2, y2):
    h, inside = "#housing", "#inside"
    small = kind in ("puffin", "low_level_cycle")
    ped = kind in ("pelican", "toucan", "puffin")
    t = 0.45
    x1, x2, top = x1 - 0.5, x2 + 0.5, y2 + 0.55
    if ped or small:
        depth = 1.2 if small else 1.8
        return [box((x1, top, 6.9 - depth), (x2, top + t, 7.0), h)]
    if style == "led":
        # short cowl, angled: deep at the top, shallow at the bottom of the sides
        d = 2.6
        return [
            box((x1, top, 7.0 - d), (x2, top + t, 7.0), h),
            box((x1, y1 + (y2 - y1) * 0.45, 7.0 - d), (x1 + t, top, 7.0), h),
            box((x2 - t, y1 + (y2 - y1) * 0.45, 7.0 - d), (x2, top, 7.0), h),
            box((x1, y1 - 0.2, 7.0 - d * 0.45), (x1 + t, y1 + (y2 - y1) * 0.45, 7.0), h),
            box((x2 - t, y1 - 0.2, 7.0 - d * 0.45), (x2, y1 + (y2 - y1) * 0.45, 7.0), h),
        ]
    if style == "led_tunnel":
        d = 4.4
        return [
            box((x1 - 0.2, top, 7.0 - d), (x2 + 0.2, top + t, 7.0), h),
            box((x1 - 0.2, y1 + 0.6, 7.0 - d), (x1 + t - 0.2, top, 7.0), h),
            box((x2 - t + 0.2, y1 + 0.6, 7.0 - d), (x2 + 0.2, top, 7.0), h),
            box((x1, y1 - 0.3, 7.0 - d * 0.35), (x2, y1 + 0.15, 7.0), h),
        ]
    # classic: deep full hood, open at the bottom
    d = 3.6
    return [
        box((x1, top, 7.0 - d), (x2, top + t, 7.0), h),
        box((x1, y1 - 0.3, 7.0 - d), (x1 + t, top, 7.0), h),
        box((x2 - t, y1 - 0.3, 7.0 - d), (x2, top, 7.0), h),
    ]


def board_dims(kind):
    x1, y1, x2, y2 = head_bbox(kind)
    return x1 - 2.2, -1.0, x2 + 2.2, 17.0


def board_elements(kind, tex="#board"):
    bx1, by1, bx2, by2 = board_dims(kind)
    return [box((bx1 + 0.5, by1 + 0.5, 11.0), (bx2 - 0.5, by2 - 0.5, 11.4), "#board_edge",
                faces=("east", "west", "up", "down", "south")),
            box((bx1, by1, 10.95), (bx2, by2, 11.0), tex, faces=("north",), full_front_uv=True)]


# =================================================================== looks per state

def main_colour(state):
    return 0 if state == 0 else (state - 1) % 5 + 1


def lens_looks(kind, state, red_amber):
    """{aspect id: 'on' | 'flash'} for aspects lit in this PTM2 state; others are off."""
    m = main_colour(state)
    arrow = 0 if state == 0 else (state - 1) // 5
    lit = {}
    if kind in ("pelican", "puffin", "toucan"):
        # PTM2 normalises 2-light states: yellow -> red, blinking yellow -> off
        if m in (1, 2):
            lit["red_man"] = "on"
        elif m == 4:
            lit["green_man"] = "on"
            if kind == "toucan":
                lit["green_cycle"] = "on"
        elif m == 5:
            if kind == "pelican":
                lit["green_man"] = "flash"      # flashing green man
            elif kind == "puffin":
                lit["red_man"] = "on"           # puffins show red during clearance
            # toucans go blank during clearance
        return lit
    prefix = "cycle_" if kind in ("cycle", "low_level_cycle") else ""
    green = next(a for a, *_ in layout(kind) if a.startswith(prefix + "green") and not
                 (kind.endswith("filter") and a in ("green_left", "green_right")))
    if m == 1:
        lit[prefix + "red"] = "on"
    elif m == 2:
        lit[prefix + "amber"] = "on"
        if red_amber:
            lit[prefix + "red"] = "on"
    elif m == 3:
        lit[prefix + "amber"] = "flash" if not prefix else "on"
    elif m in (4, 5):
        lit[green] = "on"          # UK signals never flash green
    if kind.endswith("filter") and arrow:
        lit["green_" + kind.split("_")[0]] = "on"
    return lit


def lens_texture(style, aspect, look):
    prefix = "classic" if style == "classic" else "led"
    if look == "flash":
        return f"{prefix}_{aspect}_flash"
    if aspect.startswith("cycle_"):
        return f"led_cycle_{aspect[6:]}_{look}"
    if aspect == "green_cycle":
        return f"led_green_cycle_{look}"
    return f"{prefix}_{aspect}_{look}"


# =================================================================== models + blockstates

def write_json(path, obj, compact=False):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text((json.dumps(obj, separators=(",", ":")) if compact else json.dumps(obj, indent=1)) + "\n")


def model(textures, elements, cutout=False):
    m = {"textures": textures, "elements": elements}
    if cutout:
        m["render_type"] = "minecraft:cutout"
    return m


def common_textures(style, tex):
    return {
        "particle": tex[f"housing_{style}"],
        "housing": tex[f"housing_{style}"],
        "inside": tex["hood_inside"],
        "lens": tex["hood_inside"],
    }


def board_texture_ref(kind, tex_cache):
    x1, y1, x2, y2 = board_dims(kind)
    w, h = round(x2 - x1, 2), round(y2 - y1, 2)
    key = f"board_{w}x{h}".replace(".", "_")
    if key not in tex_cache:
        tex_cache[key] = save(board_texture(w, h, (36, 38, 40), (236, 236, 232)), f"signal/{key}")
    return tex_cache[key]


def write_signal(block, style, kind, boardable, tex, tex_cache):
    mdir = ASSETS / "models/block" / block
    textures = common_textures(style, tex)
    aspects = layout(kind)

    # every (aspect, look) used by any state -> which (state, red_amber) pairs light it
    usage = {}
    for state in range(16):
        for ra in (False, True):
            lit = lens_looks(kind, state, ra)
            for aspect, *_ in aspects:
                look = lit.get(aspect, "off")
                usage.setdefault((aspect, look), set()).add((state, ra))

    body = body_elements(style, kind)
    board_tex = board_texture_ref(kind, tex_cache) if boardable else None
    for geometry, diagonal, attachment in GEOMETRIES:
        parts = {"body": model(textures, place(body, attachment, diagonal))}
        if boardable:
            parts["board"] = model({"particle": board_tex, "board": board_tex, "board_edge": tex["grey_metal"]},
                                   place(board_elements(kind), attachment, diagonal), cutout=True)
        for (aspect, look) in usage:
            a = next(x for x in aspects if x[0] == aspect)
            key = lens_texture(style, aspect, look)
            el = lens(a[1], a[2], a[3], a[4], "glass", look != "off")
            parts[f"{aspect}_{look}"] = model({"particle": tex[key], "glass": tex[key], "lens": tex[key]},
                                              place([el], attachment, diagonal))
        write_parts(block, geometry, parts)

    looks = {}
    for state in range(16):
        for ra in (False, True):
            if ra and not (main_colour(state) == 2 and kind not in ("pelican", "puffin", "toucan")):
                continue
            lit = lens_looks(kind, state, ra)
            looks[f"{state}+ra" if ra else str(state)] = {a: lit.get(a, "off") for a, *_ in aspects}
    INDEX[block] = {"board": boardable, "aspects": [a for a, *_ in aspects], "looks": looks}
    write_json(ASSETS / f"blockstates/{block}.json", {"variants": {"": {"model": f"{MOD_ID}:block/{block}/body_center_y0"}}})

    # item: centre placement showing a representative aspect
    show = {"pelican": (4, False), "puffin": (1, False), "toucan": (4, False)}.get(kind, (2, True))
    if kind.endswith("filter"):
        show = (6, False)  # red with filter arrow lit
    lit = lens_looks(kind, *show)
    els = list(body)
    item_tex = dict(textures)
    if boardable:
        els += board_elements(kind)
        item_tex.update({"board": board_tex, "board_edge": tex["grey_metal"]})
    for aspect, x1, y1, x2, y2, _ in aspects:
        look = lit.get(aspect, "off")
        if look == "flash":
            look = "on"
        k = lens_texture(style, aspect, look)
        item_tex[k] = tex[k]
        els.append(lens(x1, y1, x2, y2, k, look != "off"))
    write_item(block, model(item_tex, els, cutout=boardable), small=kind in ("puffin", "low_level_cycle"))


def write_item(block, mdl, small=False):
    s = 0.9 if small else 0.75
    mdl["display"] = {
        "gui": {"rotation": [10, 200, 0], "translation": [0, 0, 0], "scale": [s, s, s]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.4, 0.4, 0.4]},
        "fixed": {"rotation": [0, 180, 0], "scale": [0.7, 0.7, 0.7]},
        "thirdperson_righthand": {"rotation": [75, 225, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
        "firstperson_righthand": {"rotation": [0, 225, 0], "scale": [0.4, 0.4, 0.4]},
        "firstperson_lefthand": {"rotation": [0, 45, 0], "scale": [0.4, 0.4, 0.4]},
    }
    write_json(ASSETS / f"models/item/{block}.json", mdl)


INDEX = {}
FACE_CW = {"north": "east", "east": "south", "south": "west", "west": "north", "up": "up", "down": "down"}


def rotate_y90(el):
    """Rotate an element 90 degrees clockwise (seen from above) about the block centre, like
    a blockstate "y": 90. Diagonal elements keep their -45 rotation: both turn about the
    vertical axis through the centre, so the rotations combine."""
    el = json.loads(json.dumps(el))
    (x1, y1, z1), (x2, y2, z2) = el["from"], el["to"]
    el["from"], el["to"] = [round(16 - z2, 3), y1, x1], [round(16 - z1, 3), y2, x2]
    el["faces"] = {FACE_CW[f]: v for f, v in el["faces"].items()}
    return el


def write_parts(block, geometry, parts):
    """Each part at the four 90 degree rotations, pre-rotated so the client baker
    (client/SignalModels.java) never has to rotate anything."""
    mdir = ASSETS / "models/block" / block
    for name, mdl in parts.items():
        for y in (0, 90, 180, 270):
            write_json(mdir / f"{name}_{geometry}_y{y}.json", mdl, compact=True)
            mdl = dict(mdl, elements=[rotate_y90(e) for e in mdl["elements"]])


# ---- accessories

def accessory_parts(kind, name, tex):
    """(body elements, textures, board elements or None)."""
    if kind == "sign":
        sign = name[:-len("_sign")]
        x1, y1, x2, y2 = 5.0, 10.0, 11.0, 16.0
        els = [box((x1, y1, 7.0), (x2, y2, 11.0), "#housing"),
               box((x1 + 0.2, y1 + 0.2, 6.9), (x2 - 0.2, y2 - 0.2, 7.0), "#inside", faces=("north",)),
               box((5.5, 10.5, 6.6), (10.5, 15.5, 6.9), "#housing", faces=("north",), front="#sign",
                   emissive=True, full_front_uv=True),
               box((4.6, 15.6, 5.0), (11.4, 16.0, 7.0), "#housing")]
        textures = {"particle": tex["housing_led"], "housing": tex["housing_led"], "inside": tex["hood_inside"],
                    "sign": tex[f"sign_{sign}"]}
        bx1, bx2 = board_dims("standard")[0], board_dims("standard")[2]
        board = [box((bx1, 9.2, 11.0), (bx2, 15.0, 11.4), "#board_edge", faces=("east", "west", "up", "down", "south")),
                 box((bx1, 9.2, 10.95), (bx2, 15.0, 11.0), "#board", faces=("north",), full_front_uv=True)]
        return els, textures, (board, (bx2 - bx1, 15.0 - 9.2))
    if kind == "detector":
        els = [box((7.4, 0.0, 9.0), (8.6, 2.0, 10.0), "#metal"),            # stalk on top of the head
               box((6.0, 2.0, 7.0), (10.0, 4.8, 11.0), "#metal"),           # detector body
               box((6.6, 2.4, 6.6), (9.4, 4.4, 7.0), "#metal", faces=("north",), front="#lens", full_front_uv=True),
               box((5.8, 4.8, 6.0), (10.2, 5.2, 11.0), "#metal")]           # rain lip
        return els, {"particle": tex["grey_metal"], "metal": tex["grey_metal"], "lens": tex["detector_lens"]}, None
    if kind == "push_button":
        els = [box((5.5, 4.0, 8.0), (10.5, 12.0, 11.0), "#housing", front="#face", full_front_uv=True),
               box((6.5, 3.0, 8.5), (9.5, 4.0, 10.5), "#housing"),           # tactile cone housing
               box((5.3, 12.0, 7.6), (10.7, 12.4, 11.0), "#housing")]
        return els, {"particle": tex["housing_led"], "housing": tex["housing_led"], "face": tex["push_button"]}, None
    raise ValueError(kind)


def write_accessory(name, kind, tex):
    mdir = ASSETS / "models/block" / name
    els, textures, board = accessory_parts(kind, name, tex)
    board_tex = None
    if board:
        w, h = board[1]
        board_tex = save(board_texture(w, h, (36, 38, 40), (236, 236, 232)), "signal/board_sign")
    for geometry, diagonal, attachment in GEOMETRIES:
        parts = {"body": model(textures, place(els, attachment, diagonal))}
        if board:
            parts["board"] = model({"particle": board_tex, "board": board_tex, "board_edge": tex["grey_metal"]},
                                   place(board[0], attachment, diagonal), cutout=True)
        write_parts(name, geometry, parts)
    INDEX[name] = {"board": board is not None, "aspects": [], "looks": {}}
    write_json(ASSETS / f"blockstates/{name}.json", {"variants": {"": {"model": f"{MOD_ID}:block/{name}/body_center_y0"}}})
    item_els, item_tex = list(els), dict(textures)
    if board:
        item_els += board[0]
        item_tex.update({"board": board_tex, "board_edge": tex["grey_metal"]})
    write_item(name, model(item_tex, item_els, cutout=bool(board)), small=True)


# =================================================================== data + lang

def all_signal_blocks():
    for type_id, (styles, kind, boardable) in SIGNAL_TYPES.items():
        for style in styles:
            yield f"{style}_{type_id}", style, type_id, kind, boardable


def write_lang():
    lang = {"itemGroup.ptmuk.main": "PTM UK Addon"}
    for block, style, type_id, _, _ in all_signal_blocks():
        lang[f"block.{MOD_ID}.{block}"] = f"{TYPE_NAMES[type_id]} ({STYLE_NAMES[style]})"
    for name in ACCESSORIES:
        lang[f"block.{MOD_ID}.{name}"] = ACCESSORY_NAMES[name]
    write_json(ASSETS / "lang/en_us.json", lang)


def write_data():
    signals = [f"{MOD_ID}:{b}" for b, *_ in all_signal_blocks()]
    everything = signals + [f"{MOD_ID}:{n}" for n in ACCESSORIES]
    loot = DATA / MOD_ID / "loot_tables"
    if loot.exists():
        shutil.rmtree(loot)
    write_json(DATA / "ptm2/tags/blocks/traffic_lights.json", {"replace": False, "values": signals})
    write_json(DATA / "minecraft/tags/blocks/mineable/pickaxe.json", {"replace": False, "values": everything})
    for full in everything:
        name = full.split(":")[1]
        write_json(loot / f"blocks/{name}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": full}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}],
        })


def main():
    for d in ("models", "blockstates"):
        if (ASSETS / d).exists():
            shutil.rmtree(ASSETS / d)
    tex = write_textures()
    tex_cache = {}
    for block, style, _, kind, boardable in all_signal_blocks():
        write_signal(block, style, kind, boardable, tex, tex_cache)
    for name, kind in ACCESSORIES.items():
        write_accessory(name, kind, tex)
    write_json(ASSETS / "signal_parts.json", INDEX, compact=True)
    write_lang()
    write_data()
    print("assets generated")


if __name__ == "__main__":
    main()
