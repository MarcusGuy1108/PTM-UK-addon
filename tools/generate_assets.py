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
import shutil
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import numpy as np  # noqa: E402
import signal_textures as T  # noqa: E402
import furniture  # noqa: E402
import signs  # noqa: E402
import motorway  # noqa: E402
import pylons  # noqa: E402
import building  # noqa: E402
import bus_alx400  # noqa: E402
import bus_ukdd  # noqa: E402
import car_k4  # noqa: E402

MOD_ID = "ptmuk"
ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets" / MOD_ID
DATA = ROOT / "src/main/resources/data"

# Must match SignalType / SignalStyle / AccessoryType / ModBlocks.stylesFor in Java.
STYLES = ["led", "led_tunnel", "classic"]
ALL_STYLES = STYLES + ["classic_large_green"]
SIGNAL_TYPES = {
    # type id: (styles, layout, boardable)
    "signal": (ALL_STYLES, "standard", True),
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
POLES = {
    # id: (radius px, surface, collar at the foot, cap style, name)
    "signal_pole_black": (1.85, "black", True, "dome", "UK Traffic Signal Pole (Black)"),
    "signal_pole_grey": (1.85, "galvanised", True, "dome", "UK Traffic Signal Pole (Galvanised)"),
    "sign_pole_galvanised": (1.25, "galvanised", False, "plastic", "UK Sign Pole (Galvanised)"),
    "sign_pole_black": (1.25, "black", False, "plastic", "UK Sign Pole (Black)"),
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
ACCESSORIES.update({name: "roadsign" for name in furniture.ROAD_SIGNS})

STYLE_NAMES = {"led": "LED", "led_tunnel": "LED, Tunnel Hoods", "classic": "Classic Bulb",
               "classic_large_green": "Classic Bulb, Large Green"}


def is_bulb(style):
    return style.startswith("classic")
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
# Post mounts sit 1 px further from the pole than PTM2's own lights, leaving the small gap
# real UK heads have between head and pole, bridged by clamp brackets (see clamps()).
STRAIGHT_OFFSET = {
    "center": (0, 0), "wall": (0, 5), "left": (5, 0), "right": (-5, 0),
    "post": (0, 10), "left_post": (10, 0), "right_post": (-10, 0),
}
# Diagonal (45 degree) placements only ever use these three attachments.
DIAGONAL_OFFSET = {"center": 0.0, "wall": 9.3, "post": 16.4}
# Where the pole is, in the head's own (centre) frame, for each post mount.
POLE_IN_FRAME = {"post": (8, 14), "post45": (8, 8 + 16 * math.sqrt(2) - 16.4), "onpole": (8, 14), "onpole45": (8, 14),
                 "left_post": (14, 8), "right_post": (2, 8)}
CLAMP_R = 2.05


def placements():
    """(attachment, rotation, geometry key, diagonal, y rotation) for every blockstate combination."""
    for attachment in ATTACHMENTS:
        for rotation in range(8):
            diagonal = rotation % 2 == 1 and attachment in DIAGONAL_OFFSET
            yield attachment, rotation, attachment + ("45" if diagonal else ""), diagonal, 90 * (rotation // 2)


GEOMETRIES = sorted({(g, d, a) for a, _, g, d, _ in placements()})
# Pole-top mounting: a free-standing head or sign sitting on a UK pole is pushed forward so
# the pole can carry on up behind it (chosen at render time by client/PoleMountedModel).
STRAIGHT_OFFSET["onpole"] = (0, -6)
DIAGONAL_OFFSET_ONPOLE = -6.0
GEOMETRIES += [("onpole", False, "onpole"), ("onpole45", True, "onpole")]


# =================================================================== textures

TEX = ASSETS / "textures/block"


def save(img, name, frametime=None):
    path = TEX / f"{name}.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)
    if frametime:
        (TEX / f"{name}.png.mcmeta").write_text(json.dumps({"animation": {"frametime": frametime}}) + "\n")
    return f"{MOD_ID}:block/{name}"


def write_textures():
    if TEX.exists():
        shutil.rmtree(TEX)
    t = {}
    for style in ALL_STYLES:
        t[f"housing_{style}"] = save(T.housing(style), f"signal/housing_{style}")
    t["hood_inside"] = save(T.hood_inside(), "signal/hood_inside")
    t["grey_metal"] = save(T.grey_metal(), "signal/grey_metal")
    t["detector_lens"] = save(T.detector_lens(), "signal/detector_lens")
    t["push_button"] = save(T.push_button_face(False), "signal/push_button_face")
    t["push_button_wait"] = save(T.push_button_face(True), "signal/push_button_face_wait")
    t["yellow_plastic"] = save(T.yellow_plastic(), "signal/yellow_plastic")
    for surface in ("black", "galvanised", "plastic"):
        t[f"pole_{surface}"] = save(T.pole(surface), f"pole/{surface}")

    arrows = {d: T.arrow_mask(d) for d in ("left", "right", "ahead")}
    bike, standing, walking = T.bicycle_mask(), T.standing_man_mask(), T.walking_man_mask()
    for prefix, make in (("led", T.lens_led), ("classic", T.lens_classic)):  # both LED styles share lenses
        for colour in ("red", "amber", "green"):
            for lit in (True, False):
                key = f"{prefix}_{colour}_{'on' if lit else 'off'}"
                t[key] = save(make(colour, lit), f"signal/{key}")
        t[f"{prefix}_amber_flash"] = save(T.flash(make("amber", True), make("amber", False)), f"signal/{prefix}_amber_flash", 10)
        for direction, m in arrows.items():
            for lit in (True, False):
                key = f"{prefix}_green_{direction}_{'on' if lit else 'off'}"
                t[key] = save(make("green", lit, symbol=m), f"signal/{key}")
        for colour, m, name in (("red", standing, "red_man"), ("green", walking, "green_man")):
            for lit in (True, False):
                key = f"{prefix}_{name}_{'on' if lit else 'off'}"
                t[key] = save(T.square_lens(colour, lit, m, prefix), f"signal/{key}")
        if prefix == "led":
            # round LED pedestrian aspects (square module, round lens, cowl)
            man_r, man_g = T.fit_mask(standing, 0.72), T.fit_mask(walking, 0.72)
            toucan = np.maximum(T.fit_mask(walking, 0.46, -0.2), T.fit_mask(bike, 0.42, 0.19))
            for name, colour, m in (("red_man", "red", man_r), ("green_man", "green", man_g), ("green_toucan", "green", toucan)):
                for lit in (True, False):
                    key = f"led_round_{name}_{'on' if lit else 'off'}"
                    t[key] = save(T.lens_led(colour, lit, symbol=m), f"signal/{key}")
            t["led_round_green_man_flash"] = save(T.flash(T.lens_led("green", True, symbol=man_g),
                                                          T.lens_led("green", False, symbol=man_g)),
                                                  "signal/led_round_green_man_flash", 10)
        t[f"{prefix}_green_man_flash"] = save(T.flash(T.square_lens("green", True, walking, prefix),
                                                      T.square_lens("green", False, walking, prefix)),
                                              f"signal/{prefix}_green_man_flash", 10)
    for colour in ("red", "amber", "green"):
        for lit in (True, False):
            key = f"led_cycle_{colour}_{'on' if lit else 'off'}"
            t[key] = save(T.lens_led(colour, lit, symbol=bike), f"signal/{key}")
    for lit in (True, False):
        key = f"led_green_cycle_{'on' if lit else 'off'}"
        t[key] = save(T.square_lens("green", lit, bike, "led"), f"signal/{key}")
    for kind in ("no_left_turn", "no_right_turn", "no_u_turn", "ahead_only", "turn_left", "turn_right", "except_buses"):
        t[f"sign_{kind}"] = save(T.sign(kind), f"signal/sign_{kind}")
    return t


FACEPLATES = {}


def faceplate_ref(style, rect, lenses):
    """Texture for one module front, with recesses where its lenses sit."""
    x1, y1, x2, y2 = rect
    inside = tuple((round(cx - x1, 3), round(cy - y1, 3), dia, rnd) for _, cx, cy, dia, rnd in lenses
                   if x1 <= cx <= x2 and y1 <= cy <= y2)
    key = ("classic" if is_bulb(style) else "led", round(x2 - x1, 3), round(y2 - y1, 3), inside)
    if key not in FACEPLATES:
        FACEPLATES[key] = save(T.faceplate(key[0], key[1], key[2], inside), f"signal/face_{len(FACEPLATES)}")
    return FACEPLATES[key]


BOARDS = {}
BOARD_FILL = {"classic_large_green": (112, 115, 118)}   # older light grey boards


def board_ref(w, h, style="led"):
    fill = BOARD_FILL.get(style, (44, 46, 49))
    key = (round(w, 2), round(h, 2), fill)
    if key not in BOARDS:
        BOARDS[key] = save(T.board(key[0], key[1], fill=fill), f"signal/board_{len(BOARDS)}")
    return BOARDS[key]


# =================================================================== geometry
#
# Heads are modelled in the "centre" frame: front facing north (-z), housing back at z = 11,
# like PTM2's own lights. Attachments shift the elements (see place()); diagonal placements
# get a model-level -45 degree transform, which leaves each element free to use its own
# rotation for the rounded hoods.

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


def box(frm, to, tex, faces=ALL_FACES, front=None, emissive=False, full_front_uv=False, rotation=None, overrides=None):
    """front: texture for the north face. overrides: {face: texture}. rotation: (axis, angle, origin)."""
    fm = {}
    for f in faces:
        t = (overrides or {}).get(f) or (front if (f == "north" and front) else tex)
        uv = [0, 0, 16, 16] if (f == "north" and full_front_uv) else face_uv(f, frm, to)
        fm[f] = {"texture": t, "uv": uv}
    el = {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to], "faces": fm}
    if rotation:
        axis, angle, origin = rotation
        el["rotation"] = {"angle": angle, "axis": axis, "origin": [round(v, 3) for v in origin], "rescale": False}
    if emissive:
        el["shade"] = False
        el["forge_data"] = {"block_light": 15, "sky_light": 15}
    return el


LENS_Z = 6.8      # lens face, just in front of the faceplate
FACE_Z = 6.9      # faceplate front


def lens(cx, cy, dia, tex_key, emissive, forward=0.0):
    r = dia / 2
    z = LENS_Z - forward
    return box((cx - r, cy - r, z), (cx + r, cy + r, z + 0.05), "#lens", faces=("north",),
               front=f"#{tex_key}", emissive=emissive, full_front_uv=True)


def place(elements, attachment, diagonal):
    out = []
    for el in elements:
        el = json.loads(json.dumps(el))
        if diagonal:
            dx, dz = 0, DIAGONAL_OFFSET_ONPOLE if attachment == "onpole" else DIAGONAL_OFFSET[attachment]
        else:
            dx, dz = STRAIGHT_OFFSET[attachment]
        for k in ("from", "to"):
            el[k][0] += dx
            el[k][2] += dz
        if "rotation" in el:
            el["rotation"]["origin"][0] += dx
            el["rotation"]["origin"][2] += dz
        # Minecraft only accepts elements within one block either side of their own block;
        # clip (e.g. the back strap of a clamp on a diagonal post) and drop what is left empty.
        el["from"] = [min(max(v, -16), 32) for v in el["from"]]
        el["to"] = [min(max(v, -16), 32) for v in el["to"]]
        if all(b > a for a, b in zip(el["from"], el["to"])):
            out.append(el)
    return out


# Aspect layout per head: (aspect id, centre x, centre y, lens diameter, round).
STD_Y = (16 * 5 / 6, 8.0, 16 / 6)


def layout(kind):
    std = [("red", 8, STD_Y[0], 4.4, True), ("amber", 8, STD_Y[1], 4.4, True), ("green", 8, STD_Y[2], 4.4, True)]
    if kind == "standard":
        return std
    if kind in ("left_arrow", "right_arrow", "ahead_arrow"):
        return std[:2] + [("green_" + kind.split("_")[0], 8, STD_Y[2], 4.4, True)]
    if kind == "left_filter":   # viewer's left is +x
        return std + [("green_left", 14, STD_Y[2], 4.4, True)]
    if kind == "right_filter":
        return std + [("green_right", 2, STD_Y[2], 4.4, True)]
    if kind == "cycle":
        return [("cycle_" + a, x, y, d, r) for a, x, y, d, r in std]
    if kind == "low_level_cycle":
        return [("cycle_red", 8, 10.4, 2.7, True), ("cycle_amber", 8, 7.1, 2.7, True), ("cycle_green", 8, 3.8, 2.7, True)]
    if kind == "large_green":
        return [("red", 8, 13.45, 3.6, True), ("amber", 8, 9.25, 3.6, True), ("green", 8, 3.55, 5.6, True)]
    if kind == "pelican":
        return [("red_man", 8, 12.0, 5.0, False), ("green_man", 8, 5.9, 5.0, False)]
    if kind == "pelican_round":
        return [("red_man", 8, 12.35, 5.2, True), ("green_man", 8, 5.65, 5.2, True)]
    if kind == "toucan_round":
        return [("red_man", 8, 12.35, 5.2, True), ("green_toucan", 8, 5.65, 5.2, True)]
    if kind == "puffin":
        return [("red_man", 8, 11.6, 3.4, False), ("green_man", 8, 7.9, 3.4, False)]
    if kind == "toucan":
        return [("red_man", 8, 12.0, 4.6, False), ("green_man", 5.6, 5.9, 4.2, False),
                ("green_cycle", 10.4, 5.9, 4.2, False)]
    raise ValueError(kind)


def module_rects(kind):
    """Housing modules (x1, y1, x2, y2): one per aspect for vehicle heads (stacked modules meet
    exactly, so no flickering coplanar faces), one box for pedestrian and small heads."""
    if kind == "pelican":
        return [(4.5, 2.6, 11.5, 15.3)]
    if kind in ("pelican_round", "toucan_round"):
        return [(4.4, 2.0, 11.6, 9.0), (4.4, 9.0, 11.6, 16.0)]
    if kind == "large_green":
        return [(4.6, 0.0, 11.4, 7.0), (5.65, 7.0, 10.35, 11.35), (5.65, 11.35, 10.35, 15.55)]
    if kind == "toucan":
        return [(2.9, 3.0, 13.1, 15.0)]
    if kind == "puffin":
        return [(5.5, 5.6, 10.5, 13.9)]
    if kind == "low_level_cycle":
        return [(6.0, 2.1, 10.0, 12.1)]
    third = 16 / 3
    rects = []
    for _, cx, cy, _, _ in layout(kind):
        row = int(cy // third)
        rects.append((cx - 3, row * third, cx + 3, (row + 1) * third))
    return rects


def head_bbox(kind):
    rects = module_rects(kind)
    return (min(r[0] for r in rects), min(r[1] for r in rects), max(r[2] for r in rects), max(r[3] for r in rects))


def hood_ring(cx, cy, r, phis, depth, t=0.3):
    """Rounded hood from plates, one every 22.5 degrees around the lens centre. Each plate
    is built at the nearest of top / right / left / bottom and rotated into place about the
    lens axis, so the hood is a smooth tube rather than a box."""
    w = 2 * (r + t) * math.tan(math.radians(11.25)) + 0.16
    els = []
    for phi in phis:
        p = ((phi + 45) % 360) - 45
        d = depth(phi)
        z1, z2 = FACE_Z - d, FACE_Z
        if 45 <= p <= 135:
            frm, to, ang, inner = (cx - w / 2, cy + r, z1), (cx + w / 2, cy + r + t, z2), p - 90, "down"
        elif p < 45:
            frm, to, ang, inner = (cx + r, cy - w / 2, z1), (cx + r + t, cy + w / 2, z2), p, "west"
        elif p <= 225:
            frm, to, ang, inner = (cx - r - t, cy - w / 2, z1), (cx - r, cy + w / 2, z2), p - 180, "east"
        else:
            frm, to, ang, inner = (cx - w / 2, cy - r - t, z1), (cx + w / 2, cy - r, z2), p - 270, "up"
        outer = {"down": "up", "up": "down", "west": "east", "east": "west"}[inner]
        els.append(box(frm, to, "#housing", faces=("north", inner, outer), overrides={inner: "#inside"},
                       rotation=("z", ang, (cx, cy, FACE_Z)) if ang else None))
    return els


def steps(a, b):
    out, x = [], a
    while x <= b + 1e-6:
        out.append(round(x, 1))
        x += 22.5
    return out


def hood(style, kind, cx, cy, dia, rnd):
    r = dia / 2 + 0.32
    if not rnd:
        # straight visor over a square pedestrian aspect, with short tapering cheeks
        half, top = dia / 2 + 0.35, cy + dia / 2 + 0.35
        d = 1.6 if kind != "puffin" else 1.1
        return [box((cx - half, top, FACE_Z - d), (cx + half, top + 0.3, FACE_Z), "#housing", overrides={"down": "#inside"}),
                box((cx - half - 0.3, cy, FACE_Z - d * 0.6), (cx - half, top + 0.3, FACE_Z), "#housing", overrides={"east": "#inside"}),
                box((cx + half, cy, FACE_Z - d * 0.6), (cx + half + 0.3, top + 0.3, FACE_Z), "#housing", overrides={"west": "#inside"})]
    if kind == "low_level_cycle":
        return hood_ring(cx, cy, r, steps(22.5, 157.5), lambda p: 1.1 + 0.5 * math.sin(math.radians(p)), t=0.25)
    if style == "led":
        # Helios-style cowl: deep at the top, cut back towards the sides, open underneath
        return hood_ring(cx, cy, r, steps(-22.5, 202.5),
                         lambda p: 2.9 * (0.36 + 0.64 * max(0.0, math.sin(math.radians(p)))))
    if style == "classic_large_green":
        # older "pods": long at the top, cut back steeply towards the bottom
        return hood_ring(cx, cy, r + 0.05, steps(-67.5, 247.5),
                         lambda p: dia * 0.95 * (0.35 + 0.65 * (math.sin(math.radians(p)) + 1) / 2), t=0.34)
    if style == "led_tunnel":
        return hood_ring(cx, cy, r, steps(-67.5, 247.5), lambda p: 4.8 * (0.84 + 0.16 * math.sin(math.radians(p))))
    # classic: deep full tube with a slot at the bottom for drainage
    return hood_ring(cx, cy, r + 0.05, steps(-67.5, 247.5), lambda p: 4.0 * (0.8 + 0.2 * math.sin(math.radians(p))), t=0.34)


def body_elements(style, kind):
    """Housing, faceplates, hoods and bracket (everything but lenses and board).
    Returns (elements, {texture var: texture}) for the per-module faceplate textures."""
    els, faces = [], {}
    h = "#housing"
    lenses = layout(kind)
    rects = module_rects(kind)
    classic = is_bulb(style)
    for i, (x1, y1, x2, y2) in enumerate(rects):
        var = f"face{i}"
        faces[var] = faceplate_ref(style, (x1, y1, x2, y2), lenses)
        if classic:
            els.append(box((x1, y1, 7.0), (x2, y2, 10.0), h, faces=("east", "west", "up", "down")))
            els.append(box((x1 + 0.35, y1 + 0.25, 10.0), (x2 - 0.35, y2 - 0.25, 10.6), h))
            els.append(box((x1 + 0.8, y1 + 0.6, 10.6), (x2 - 0.8, y2 - 0.6, 11.0), h))
            els.append(box((x1 - 0.08, y1 + 0.04, 6.85), (x2 + 0.08, y2 - 0.04, 7.0), h, front=f"#{var}", full_front_uv=True))
            # door hinges on one side, latch on the other
            for hy in (y1 + 0.7, y2 - 1.3):
                els.append(box((x2 + 0.08, hy, 6.9), (x2 + 0.42, hy + 0.6, 7.7), "#metal"))
            els.append(box((x1 - 0.3, (y1 + y2) / 2 - 0.3, 6.95), (x1 - 0.08, (y1 + y2) / 2 + 0.3, 7.5), "#metal"))
        else:
            els.append(box((x1 + 0.12, y1 + 0.05, 7.0), (x2 - 0.12, y2 - 0.05, 10.5), h, faces=("east", "west", "up", "down", "south")))
            els.append(box((x1 + 0.45, y1 + 0.35, 10.5), (x2 - 0.45, y2 - 0.35, 11.0), h))
            els.append(box((x1, y1, FACE_Z), (x2, y2, 7.0), h, front=f"#{var}", full_front_uv=True))
    x1, y1, x2, y2 = head_bbox(kind)
    if classic:
        els.append(box((x1 + 0.3, y2, 7.2), (x2 - 0.3, y2 + 0.3, 10.4), h))          # domed top
        els.append(box((x1 + 0.9, y2 + 0.3, 7.6), (x2 - 0.9, y2 + 0.5, 10.0), h))
    # mounting bracket behind the head
    els.append(box(((x1 + x2) / 2 - 0.7, y1 + 1.2, 11.0), ((x1 + x2) / 2 + 0.7, y2 - 1.2, 12.2), "#metal",
                   faces=("east", "west", "up", "down", "south")))
    for _, cx, cy, dia, rnd in lenses:
        els += hood(style, kind, cx, cy, dia, rnd)
    return els, faces


def clamps(geometry, heights, back=11.0, half_width=3.0):
    """Clamp brackets for post mounts: an arm from the head to a strap ring around the pole,
    one per height. Built in the head's centre frame; place() then shifts them with the head."""
    if geometry not in POLE_IN_FRAME:
        return []
    px, pz = POLE_IN_FRAME[geometry]
    R, t, h = CLAMP_R, 0.3, 0.7
    els = []
    for y in heights:
        # strap ring around the pole
        els += [box((px - R - t, y, pz - R - t), (px + R + t, y + h, pz - R), "#metal"),
                box((px - R - t, y, pz + R), (px + R + t, y + h, pz + R + t), "#metal"),
                box((px - R - t, y, pz - R), (px - R, y + h, pz + R), "#metal"),
                box((px + R, y, pz - R), (px + R + t, y + h, pz + R), "#metal")]
        # arm from the head to the strap
        if geometry in ("post", "post45"):
            els.append(box((px - 0.5, y, back), (px + 0.5, y + h, pz - R - t), "#metal"))
        elif geometry == "left_post":
            els.append(box((8 + half_width, y, 7.5), (px - R - t, y + h, 8.5), "#metal"))
        else:
            els.append(box((px + R + t, y, 7.5), (8 - half_width, y + h, 8.5), "#metal"))
    return els


def board_dims(kind):
    x1, y1, x2, y2 = head_bbox(kind)
    return x1 - 2.1, -1.0, x2 + 2.1, 17.0


def board_elements(bx1, by1, bx2, by2):
    return [box((bx1, by1, 10.95), (bx2, by2, 11.0), "#board", faces=("north",), full_front_uv=True),
            box((bx1 + 0.6, by1 + 0.6, 11.0), (bx2 - 0.6, by2 - 0.6, 11.3), "#board_back",
                faces=("east", "west", "up", "down", "south"))]


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
                lit["green_toucan"] = "on"
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


def lens_texture(style, aspect, look, round_ped=False):
    prefix = "classic" if is_bulb(style) else "led"
    if round_ped:
        return f"led_round_{aspect}_{look}"
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
        m["render_type"] = "minecraft:cutout_mipped"
    return m


def common_textures(style, tex):
    return {
        "particle": tex[f"housing_{style}"],
        "housing": tex[f"housing_{style}"],
        "inside": tex["hood_inside"],
        "metal": tex["grey_metal"],
        "lens": tex["hood_inside"],
    }


def geometry_kind(style, kind):
    """Which layout a head uses: some styles change the aspect sizes (large green pod) or
    lens shape (round LED pedestrian aspects)."""
    if style == "classic_large_green":
        return "large_green"
    if style == "led" and kind in ("pelican", "toucan"):
        return kind + "_round"
    return kind


def write_signal(block, style, kind, boardable, tex):
    textures = common_textures(style, tex)
    gkind = geometry_kind(style, kind)
    round_ped = gkind.endswith("_round")
    aspects = layout(gkind)

    usage = set()
    for state in range(16):
        for ra in (False, True):
            lit = lens_looks(kind, state, ra)
            for aspect, *_ in aspects:
                usage.add((aspect, lit.get(aspect, "off")))

    body, faces = body_elements(style, gkind)
    textures.update(faces)
    board_tex = board_els = None
    if boardable:
        bx1, by1, bx2, by2 = board_dims(gkind)
        board_tex = board_ref(bx2 - bx1, by2 - by1, style)
        board_els = board_elements(bx1, by1, bx2, by2)
    x1, y1, x2, y2 = head_bbox(gkind)
    clamp_heights = (y1 + 1.6, y2 - 2.3) if y2 - y1 > 8 else ((y1 + y2) / 2 - 0.35,)
    for geometry, diagonal, attachment in GEOMETRIES:
        mounted = body + clamps(geometry, clamp_heights, half_width=(x2 - x1) / 2)
        parts = {"body": model(textures, place(mounted, attachment, diagonal))}
        if boardable:
            parts["board"] = model({"particle": board_tex, "board": board_tex, "board_back": tex["grey_metal"]},
                                   place(board_els, attachment, diagonal), cutout=True)
        for aspect, look in usage:
            _, cx, cy, dia, rnd = next(x for x in aspects if x[0] == aspect)
            key = lens_texture(style, aspect, look, round_ped)
            parts[f"{aspect}_{look}"] = model({"particle": tex[key], "glass": tex[key], "lens": tex[key]},
                                              # classic lit lenses are drawn over the unlit ones by
                                              # ClassicLampRenderer, so sit them just in front
                                              place([lens(cx, cy, dia, "glass", look != "off",
                                                          forward=0.03 if is_bulb(style) and look != "off" else 0.0)],
                                                    attachment, diagonal),
                                              cutout=rnd)
        write_parts(block, geometry, diagonal, parts)

    looks = {}
    for state in range(16):
        for ra in (False, True):
            if ra and not (main_colour(state) == 2 and kind not in ("pelican", "puffin", "toucan")):
                continue
            lit = lens_looks(kind, state, ra)
            looks[f"{state}+ra" if ra else str(state)] = {a: lit.get(a, "off") for a, *_ in aspects}
    INDEX[block] = {"board": boardable, "fade": is_bulb(style), "aspects": [a for a, *_ in aspects], "looks": looks}
    write_json(ASSETS / f"blockstates/{block}.json", {"variants": {"": {"model": f"{MOD_ID}:block/{block}/body_center_y0"}}})

    # item: centre placement showing a representative aspect
    show = {"pelican": (4, False), "puffin": (1, False), "toucan": (4, False)}.get(kind, (2, True))
    if kind.endswith("filter"):
        show = (6, False)  # red with filter arrow lit
    lit = lens_looks(kind, *show)
    els, item_tex = list(body), dict(textures)
    if boardable:
        els += board_els
        item_tex.update({"board": board_tex, "board_back": tex["grey_metal"]})
    for aspect, cx, cy, dia, _ in aspects:
        look = lit.get(aspect, "off")
        look = "on" if look == "flash" else look
        k = lens_texture(style, aspect, look, round_ped)
        item_tex[k] = tex[k]
        els.append(lens(cx, cy, dia, k, look != "off"))
    write_item(block, model(item_tex, els, cutout=True), small=kind in ("puffin", "low_level_cycle"))


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
    a blockstate "y": 90. Point (x, z) -> (16 - z, x); an element rotation about z becomes
    one about x with the angle negated, one about x becomes one about z."""
    el = json.loads(json.dumps(el))
    (x1, y1, z1), (x2, y2, z2) = el["from"], el["to"]
    el["from"], el["to"] = [round(16 - z2, 3), y1, x1], [round(16 - z1, 3), y2, x2]
    el["faces"] = {FACE_CW[f]: v for f, v in el["faces"].items()}
    if "rotation" in el:
        rot = el["rotation"]
        ox, oy, oz = rot["origin"]
        rot["origin"] = [round(16 - oz, 3), oy, ox]
        if rot["axis"] == "z":
            rot["axis"], rot["angle"] = "x", -rot["angle"]
        elif rot["axis"] == "x":
            rot["axis"] = "z"
    return el


def write_parts(block, geometry, diagonal, parts):
    """Each part at the four 90 degree rotations, pre-rotated so the client baker
    (client/SignalModels.java) never has to rotate anything. Diagonal placements add a
    model-level -45 degree turn (Forge root transform) on top."""
    mdir = ASSETS / "models/block" / block
    for name, mdl in parts.items():
        for y in (0, 90, 180, 270):
            out = dict(mdl)
            if diagonal:
                out["transform"] = {"rotation": {"y": -45}, "origin": "center"}
            write_json(mdir / f"{name}_{geometry}_y{y}.json", out, compact=True)
            mdl = dict(mdl, elements=[rotate_y90(e) for e in mdl["elements"]])


# ---- accessories

def accessory_parts(kind, name, tex):
    """(body elements, textures, (board elements, board texture) or None)."""
    textures = {"particle": tex["housing_led"], "housing": tex["housing_led"], "inside": tex["hood_inside"],
                "metal": tex["grey_metal"]}
    if kind == "sign":
        sign = name[:-len("_sign")]
        x1, y1, x2, y2 = 5.0, 10.0, 11.0, 16.0
        textures["face"] = faceplate_ref("led", (x1, y1, x2, y2), [("sign", 8.0, 13.0, 5.0, True)])
        textures["sign"] = tex[f"sign_{sign}"]
        els = [box((x1 + 0.12, y1 + 0.05, 7.0), (x2 - 0.12, y2 - 0.05, 10.5), "#housing", faces=("east", "west", "up", "down", "south")),
               box((x1 + 0.45, y1 + 0.35, 10.5), (x2 - 0.45, y2 - 0.35, 11.0), "#housing"),
               box((x1, y1, FACE_Z), (x2, y2, 7.0), "#housing", front="#face", full_front_uv=True),
               box((5.5, 10.5, LENS_Z), (10.5, 15.5, LENS_Z + 0.05), "#housing", faces=("north",), front="#sign",
                   emissive=True, full_front_uv=True)]
        els += hood_ring(8.0, 13.0, 2.85, steps(22.5, 157.5), lambda p: 1.6 + 0.6 * math.sin(math.radians(p)))
        bx1, _, bx2, _ = board_dims("standard")
        board = (board_elements(bx1, 9.2, bx2, 15.0), board_ref(bx2 - bx1, 15.0 - 9.2))
        return els, textures, board
    if kind == "detector":
        textures.update({"particle": tex["grey_metal"], "housing": tex["grey_metal"], "lens": tex["detector_lens"]})
        els = [box((7.4, 0.0, 9.2), (8.6, 1.8, 10.2), "#metal"),                     # stalk on top of the head
               box((6.0, 1.8, 7.2), (10.0, 4.6, 10.8), "#housing"),                  # detector body
               box((6.3, 2.1, 10.8), (9.7, 4.3, 11.2), "#housing"),                  # rounded back
               box((6.5, 2.3, 6.9), (9.5, 4.2, 7.2), "#housing", front="#lens", full_front_uv=True),
               box((5.8, 4.6, 6.2), (10.2, 4.95, 10.8), "#housing")]                 # rain lip
        return els, textures, None
    if kind == "push_button":
        textures.update({"face": tex["push_button"], "yellow": tex["yellow_plastic"]})
        els = [box((5.5, 4.0, 8.2), (10.5, 12.0, 11.0), "#housing", faces=("east", "west", "up", "down", "south")),
               box((5.5, 4.0, 8.0), (10.5, 12.0, 8.2), "#housing", front="#face", full_front_uv=True),
               box((5.75, 4.25, 8.6), (10.25, 11.75, 11.25), "#housing"),             # rounded back shell
               # yellow tactile cones underneath
               box((6.0, 3.5, 8.4), (7.1, 4.0, 9.4), "#yellow"),
               box((8.9, 3.5, 8.4), (10.0, 4.0, 9.4), "#yellow")]
        return els, textures, None
    raise ValueError(kind)


def write_accessory(name, kind, tex):
    if kind == "roadsign":
        els, textures, clamp_heights = furniture.roadsign_parts(name, tex)
        board = None
    else:
        els, textures, board = accessory_parts(kind, name, tex)
        clamp_heights = {"sign": (12.6,), "push_button": (5.2, 10.0)}.get(kind, ())
    for geometry, diagonal, attachment in GEOMETRIES:
        mounted = els + clamps(geometry, clamp_heights, half_width=2.5,
                               back=furniture.SIGN_BACK_Z if kind == "roadsign" else 11.0)
        parts = {"body": model(textures, place(mounted, attachment, diagonal), cutout=kind == "roadsign")}
        if board:
            parts["board"] = model({"particle": board[1], "board": board[1], "board_back": tex["grey_metal"]},
                                   place(board[0], attachment, diagonal), cutout=True)
        write_parts(name, geometry, diagonal, parts)
    INDEX[name] = {"board": board is not None, "wait": kind == "push_button", "aspects": [], "looks": {}}
    if kind == "push_button":
        # lit WAIT panel, shown while the button has been pressed
        wait = box((5.5, 4.0, 7.97), (10.5, 12.0, 8.0), "#housing", faces=("north",), front="#face",
                   emissive=True, full_front_uv=True)
        wait_tex = {"particle": tex["push_button_wait"], "housing": tex["push_button_wait"], "face": tex["push_button_wait"]}
        for geometry, diagonal, attachment in GEOMETRIES:
            write_parts(name, geometry, diagonal, {"wait": model(wait_tex, place([wait], attachment, diagonal))})
    write_json(ASSETS / f"blockstates/{name}.json", {"variants": {"": {"model": f"{MOD_ID}:block/{name}/body_center_y0"}}})
    item_els, item_tex = list(els), dict(textures)
    if board:
        item_els += board[0]
        item_tex.update({"board": board[1], "board_back": tex["grey_metal"]})
    write_item(name, model(item_tex, item_els, cutout=bool(board) or kind == "roadsign"), small=True)


# ---- poles

def round_column(r, y1, y2, tex, top=False, bottom=False):
    """A smooth 16-sided column: 16 plates around the axis (each built on the north, east,
    south or west side and turned up to 45 degrees about the column), plus a filled top /
    bottom when the end is visible."""
    w = 2 * r * math.tan(math.radians(11.25)) + 0.06
    t = max(0.3, r * 0.28)
    ends = (("up",) if top else ()) + (("down",) if bottom else ())
    els = []
    sides = {"north": ((8 - w / 2, 8 - r), (8 + w / 2, 8 - r + t)), "south": ((8 - w / 2, 8 + r - t), (8 + w / 2, 8 + r)),
             "west": ((8 - r, 8 - w / 2), (8 - r + t, 8 + w / 2)), "east": ((8 + r - t, 8 - w / 2), (8 + r, 8 + w / 2))}
    for face, ((x1, z1), (x2, z2)) in sides.items():
        # north/south plates also cover the four diagonals (+-45), east/west only +-22.5: 16 in all
        for ang in ((-45, -22.5, 0, 22.5, 45) if face in ("north", "south") else (-22.5, 0, 22.5)):
            els.append(box((x1, y1, z1), (x2, y2, z2), tex, faces=(face,) + ends,
                           rotation=("y", ang, (8, 8, 8)) if ang else None))
    if ends:
        h = r / math.sqrt(2)
        for ang in (0, 22.5, 45, -22.5):
            els.append(box((8 - h, y1 + (0.005 if bottom else 0), 8 - h), (8 + h, y2 - (0.005 if top else 0), 8 + h), tex,
                           faces=ends, rotation=("y", ang, (8, 8, 8)) if ang else None))
    return els


def write_pole(name, radius, surface, collar, cap, tex):
    textures = {"particle": tex[f"pole_{surface}"], "pole": tex[f"pole_{surface}"], "plastic": tex["pole_plastic"]}
    body = round_column(radius, 0, 16, "#pole")
    variants = {}
    for base in (False, True):
        for top in (False, True):
            els = list(body)
            if base and collar:
                els += round_column(radius + 0.55, 0, 3.6, "#pole", top=True)
                els += round_column(radius + 0.3, 3.6, 4.0, "#pole", top=True)
            if top:
                if cap == "dome":
                    els += round_column(radius + 0.12, 15.2, 16.0, "#pole", top=True)
                    els += round_column(radius * 0.7, 16.0, 16.35, "#pole", top=True)
                else:
                    els += round_column(radius + 0.1, 15.3, 16.1, "#plastic", top=True)
            model_name = f"{name}/{'base' if base else 'mid'}_{'cap' if top else 'open'}"
            write_json(ASSETS / f"models/block/{model_name}.json", model(textures, els), compact=True)
            variants[f"base={str(base).lower()},cap={str(top).lower()}"] = {"model": f"{MOD_ID}:block/{model_name}"}
    write_json(ASSETS / f"blockstates/{name}.json", {"variants": variants})
    item = {"parent": f"{MOD_ID}:block/{name}/base_cap", "display": {
        "gui": {"rotation": [30, 225, 0], "scale": [0.625, 0.625, 0.625]},
        "ground": {"translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
        "fixed": {"scale": [0.5, 0.5, 0.5]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.4, 0.4, 0.4]},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "scale": [0.4, 0.4, 0.4]}}}
    write_json(ASSETS / f"models/item/{name}.json", item)


# =================================================================== data + lang

def all_signal_blocks():
    for type_id, (styles, kind, boardable) in SIGNAL_TYPES.items():
        for style in styles:
            yield f"{style}_{type_id}", style, type_id, kind, boardable


def write_lang():
    lang = {"itemGroup.ptmuk.main": "PTM UK Addon"}
    for name, (*_, title) in POLES.items():
        lang[f"block.{MOD_ID}.{name}"] = title
    for block, style, type_id, _, _ in all_signal_blocks():
        lang[f"block.{MOD_ID}.{block}"] = f"{TYPE_NAMES[type_id]} ({STYLE_NAMES[style]})"
    for name in ACCESSORIES:
        if name in ACCESSORY_NAMES:
            lang[f"block.{MOD_ID}.{name}"] = ACCESSORY_NAMES[name]
    lang.update(furniture.lang())
    lang.update(signs.lang(sys.modules[__name__]))
    lang.update(motorway.lang(sys.modules[__name__]))
    lang.update(pylons.lang(sys.modules[__name__]))
    lang.update(building.lang(sys.modules[__name__]))
    lang.update({"item.ptmuk.alx400": "Alexander ALX400 (Double Decker)", "entity.ptmuk.ptm_124d_alx400": "Alexander ALX400",
                 "item.ptmuk.uk_double_decker": "UK Double Decker", "entity.ptmuk.ptm_124e_ukdd": "UK Double Decker",
                 "item.ptmuk.kia_k4_gt_line_s": "Kia K4 GT-Line S (Red)", "entity.ptmuk.ptm_035k_k4": "Kia K4 GT-Line S",
                 "itemGroup.ptmuk.buses": "UK Buses & Cars"})
    lang.update({"itemGroup.ptmuk.main": "UK Traffic Lights", "itemGroup.ptmuk.poles": "UK Poles",
                 "itemGroup.ptmuk.signs": "UK Road Signs", "itemGroup.ptmuk.street": "UK Street Furniture",
                 "itemGroup.ptmuk.motorway": "UK Motorway"})
    write_json(ASSETS / "lang/en_us.json", lang)


def write_data():
    signals = [f"{MOD_ID}:{b}" for b, *_ in all_signal_blocks()]
    fences = [f"{MOD_ID}:{n}" for n in furniture.FENCES]
    everything = [f"{MOD_ID}:{n}" for n in POLES] + signals + [f"{MOD_ID}:{n}" for n in ACCESSORIES] + \
        [f"{MOD_ID}:{n}" for n in furniture.FURNITURE] + fences + \
        [f"{MOD_ID}:{n}" for n in signs.SIGNS] + [f"{MOD_ID}:london_bus_stop"] + [f"{MOD_ID}:{n}" for n in motorway.NAMES]
    write_json(DATA / "minecraft/tags/blocks/fences.json", {"replace": False, "values": fences})
    write_json(DATA / "ptm2/tags/items/vehicles.json", {"replace": False, "values": [f"{MOD_ID}:alx400", f"{MOD_ID}:uk_double_decker", f"{MOD_ID}:kia_k4_gt_line_s"]})
    loot = DATA / MOD_ID / "loot_tables"
    if loot.exists():
        shutil.rmtree(loot)
    write_json(DATA / "ptm2/tags/blocks/traffic_lights.json", {"replace": False, "values": signals})
    write_json(DATA / "minecraft/tags/blocks/mineable/pickaxe.json",
               {"replace": False, "values": everything + [f"{MOD_ID}:{n}" for n in pylons.TOWERS if "pole" not in n]})
    write_json(DATA / "minecraft/tags/blocks/mineable/axe.json",
               {"replace": False, "values": [f"{MOD_ID}:{n}" for n in pylons.TOWERS if "pole" in n]})
    everything += [f"{MOD_ID}:{n}" for n in building.block_ids()]
    write_json(DATA / "minecraft/tags/blocks/mineable/pickaxe.json",
               {"replace": False, "values": everything + [f"{MOD_ID}:{n}" for n in pylons.TOWERS if "pole" not in n]})
    write_json(DATA / "minecraft/tags/blocks/slabs.json",
               {"replace": False, "values": [f"{MOD_ID}:{n}_slab" for n in building.MATERIALS]})
    write_json(DATA / "minecraft/tags/items/slabs.json",
               {"replace": False, "values": [f"{MOD_ID}:{n}_slab" for n in building.MATERIALS]})
    for full in everything:
        name = full.split(":")[1]
        if name.endswith("_slab") and name[:-5] in building.MATERIALS:
            write_json(loot / f"blocks/{name}.json", building.slab_loot(full))
            continue
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
    furniture.G = sys.modules[__name__]
    for block, style, _, kind, boardable in all_signal_blocks():
        write_signal(block, style, kind, boardable, tex)
    for name, kind in ACCESSORIES.items():
        write_accessory(name, kind, tex)
    for name, (radius, surface, collar, cap, _) in POLES.items():
        write_pole(name, radius, surface, collar, cap, tex)
    furniture.generate(sys.modules[__name__], tex)
    signs.generate(sys.modules[__name__])
    motorway.generate(sys.modules[__name__])
    pylons.generate(sys.modules[__name__])
    building.generate(sys.modules[__name__])
    bus_alx400.main()
    bus_ukdd.main()
    car_k4.main()
    write_json(ASSETS / "signal_parts.json", INDEX, compact=True)
    write_lang()
    write_data()
    check_bounds()
    print("assets generated")


def check_bounds():
    """Minecraft refuses models with elements outside -16..32; fail here instead of in game."""
    for path in (ASSETS / "models").rglob("*.json"):
        for el in json.loads(path.read_text()).get("elements", []):
            if min(el["from"] + el["to"]) < -16 or max(el["from"] + el["to"]) > 32:
                raise SystemExit(f"element out of bounds in {path}: {el['from']} {el['to']}")


if __name__ == "__main__":
    main()
