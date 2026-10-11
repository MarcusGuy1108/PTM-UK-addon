"""Home furniture for the UK Home tab: living room, kitchen, bathroom and bedroom. Called from
generate_assets.main(). Everything here is drawn from scratch (no other mod's models).

Models are in block pixels with the front facing north (towards whoever placed it). The list in
HOME is the single source of truth: it also writes HomeFurnitureType.java, so the blocks and
their behaviour (seats, storage, switching on, tall and wide pieces, tabletop items that sit down
onto the furniture under them) always match the models.
"""
import inspect
import json
import math
from pathlib import Path

import home_textures as HT
import furniture_hd as H

G = None
TEX = {}
P = "home/"
JAVA = Path(__file__).resolve().parent.parent / "src/main/java/com/ptmuk/home/HomeFurnitureType.java"

ALL = ("north", "east", "south", "west", "up", "down")

# the top of a piece of furniture you can stand things on (px), and how far a tabletop item sinks
SURFACES = {"floor": 16, "low": 8, "table": 12, "counter": 14}
LEVELS = ("floor", "low", "table", "counter")

SOFA_COLOURS = {"grey": (128, 130, 134), "navy": (38, 52, 92), "green": (64, 98, 70), "brown": (106, 64, 38),
                "cream": (214, 200, 176)}
FINISHES = {"white": (238, 238, 234), "oak": (176, 132, 84)}


def bx(frm, to, tex, faces=ALL, full=(), uv=None, emissive=False, rot=None, front=None):
    """A box element. full: faces that show the whole texture; uv: explicit face UVs."""
    el = G.box(frm, to, tex, faces=faces, emissive=emissive, rotation=rot, front=front)
    for f in full:
        if f in el["faces"]:
            el["faces"][f]["uv"] = [0, 0, 16, 16]
    for f, u in (uv or {}).items():
        if f in el["faces"]:
            el["faces"][f]["uv"] = u
    return el


def legs(x0, z0, x1, z1, h, w, tex, y0=0):
    return [bx((x, y0, z), (x + w, h, z + w), tex) for x in (x0, x1 - w) for z in (z0, z1 - w)]


def shift(els, dy):
    """Move a model down (tabletop items standing on lower furniture)."""
    out = json.loads(json.dumps(els))
    for el in out:
        el["from"][1] = round(el["from"][1] + dy, 3)
        el["to"][1] = round(el["to"][1] + dy, 3)
        if "rotation" in el:
            el["rotation"]["origin"][1] = round(el["rotation"]["origin"][1] + dy, 3)
    return out


def t(key):
    return TEX[key]


# ----------------------------------------------------------------- textures

def make_textures():
    s = G.save
    TEX.update({
        "oak": s(HT.timber((176, 132, 84)), P + "oak"),
        "walnut": s(HT.timber((92, 60, 40)), P + "walnut"),
        "white_gloss": s(HT.gloss((238, 238, 234)), P + "white_gloss"),
        "worktop_dark": s(HT.granite(), P + "worktop_dark"),
        "worktop_light": s(HT.granite(base=(196, 194, 188)), P + "worktop_light"),
        "plinth": s(HT.solid((40, 40, 42)), P + "plinth"),
        "stainless": s(H.stainless(64), P + "stainless"),
        "chrome": s(HT.chrome(), P + "chrome"),
        "ceramic": s(HT.ceramic(), P + "ceramic"),
        "black": s(HT.solid((22, 22, 24)), P + "black"),
        "dark_grey": s(HT.solid((58, 60, 64)), P + "dark_grey"),
        "brass": s(HT.solid((176, 140, 70), n=4), P + "brass"),
        "shade": s(HT.fabric((232, 222, 200)), P + "shade"),
        "shade_lit": s(HT.glow((255, 236, 190)), P + "shade_lit"),
        "bulb": s(HT.glow((255, 246, 214)), P + "bulb"),
        "bulb_off": s(HT.solid((200, 196, 186)), P + "bulb_off"),
        "terracotta": s(HT.solid((176, 92, 58), n=4), P + "terracotta"),
        "soil": s(HT.solid((60, 42, 30), n=6), P + "soil"),
        "leaves": s(HT.leaves(), P + "leaves"),
        "books": s(HT.books(), P + "books"),
        "radiator": s(HT.radiator_face(), P + "radiator"),
        "marble": s(HT.granite(base=(232, 230, 226)), P + "marble"),
        "flames": s(HT.flames(), P + "flames", 3),
        "coals": s(HT.coals_off(), P + "coals"),
        "screen_on": s(HT.screen(), P + "screen_on", 50),
        "screen_off": s(HT.screen_off(), P + "screen_off"),
        "monitor_on": s(HT.monitor_screen(), P + "monitor_on"),
        "hob": s(HT.hob(False), P + "hob"),
        "hob_on": s(HT.hob(True), P + "hob_on"),
        "oven": s(HT.oven_front(False), P + "oven"),
        "oven_on": s(HT.oven_front(True), P + "oven_on"),
        "sink_top": s(HT.sink_top(), P + "sink_top"),
        "washer": s(HT.washer_front(1), P + "washer"),
        "washer_on": s(HT.washer_front(8), P + "washer_on", 2),
        "microwave": s(HT.microwave_front(False), P + "microwave"),
        "microwave_on": s(HT.microwave_front(True), P + "microwave_on"),
        "toaster_top": s(HT.toaster_top(), P + "toaster_top"),
        "basin_top": s(HT.basin_top(), P + "basin_top"),
        "bath_top": s(HT.bath_top(), P + "bath_top"),
        "toilet_seat": s(HT.toilet_seat(), P + "toilet_seat"),
        "tiles": s(tiles(), P + "tiles"),
        "glass": s(HT.glass_tex(), P + "shower_glass"),
        "mirror": s(HT.mirror(), P + "mirror"),
        "towel": s(HT.fabric((70, 110, 160)), P + "towel"),
        "office_fabric": s(HT.fabric((36, 38, 42)), P + "office_fabric"),
        "keyboard": s(keys(), P + "keyboard"),
    })
    lo, hi = HT.halves(HT.fridge_front())
    TEX["fridge_lower"], TEX["fridge_upper"] = s(lo, P + "fridge_lower"), s(hi, P + "fridge_upper")
    for name, colour in SOFA_COLOURS.items():
        if name == "brown":
            TEX[f"sofa_{name}"] = s(HT.leather(colour), P + f"sofa_{name}")
            TEX[f"cushion_{name}"] = s(HT.leather(tuple(int(c * 1.08) for c in colour)), P + f"cushion_{name}")
        else:
            TEX[f"sofa_{name}"] = s(HT.fabric(colour), P + f"sofa_{name}")
            TEX[f"cushion_{name}"] = s(HT.cushion(colour), P + f"cushion_{name}")
    for fin, colour in FINISHES.items():
        wood = HT.timber(colour) if fin == "oak" else None
        TEX[f"door_{fin}"] = s(HT.door_front(colour, wood=wood, h=48), P + f"door_{fin}")
        TEX[f"doors_{fin}"] = s(HT.door_front(colour, wood=wood, split="v", h=48), P + f"doors_{fin}")
        TEX[f"drawers_{fin}"] = s(HT.door_front(colour, wood=wood, split=3, h=48), P + f"drawers_{fin}")
        TEX[f"drawers2_{fin}"] = s(HT.door_front(colour, wood=wood, split=2, handle="knob", h=36), P + f"drawers2_{fin}")
        TEX[f"wall_doors_{fin}"] = s(HT.door_front(colour, wood=wood, split="v", h=40), P + f"wall_doors_{fin}")
        lo, hi = HT.halves(HT.tall_door(colour, wood=HT.timber(colour) if fin == "oak" else None,
                                        mirror_panel=fin == "white"))
        TEX[f"wardrobe_lower_{fin}"], TEX[f"wardrobe_upper_{fin}"] = s(lo, P + f"wardrobe_lower_{fin}"), s(hi, P + f"wardrobe_upper_{fin}")
        TEX[f"carcass_{fin}"] = TEX["oak"] if fin == "oak" else TEX["white_gloss"]
    TEX["tv_doors"] = G.save(HT.door_front((92, 60, 40), wood=HT.timber((92, 60, 40)), split="v", shaker=False, h=32),
                             P + "tv_unit_doors")
    TEX["desk_drawers"] = G.save(HT.door_front((176, 132, 84), wood=HT.timber((176, 132, 84)), split=3, h=56),
                                 P + "desk_drawers")


def tiles(size=64):
    from PIL import Image, ImageDraw
    img = HT.ceramic(size)
    d = ImageDraw.Draw(img)
    for i in range(0, size, 16):
        d.line([(i, 0), (i, size)], fill=(200, 204, 208))
        d.line([(0, i), (size, i)], fill=(200, 204, 208))
    return img


def keys(size=32):
    from PIL import Image, ImageDraw
    img = Image.new("RGBA", (size, size), (40, 42, 46, 255))
    d = ImageDraw.Draw(img)
    for r in range(4):
        for c in range(12):
            x, y = 1 + c * 2.6, 4 + r * 6
            d.rectangle([x, y, x + 1.8, y + 4], fill=(70, 72, 78))
    return img


# ----------------------------------------------------------------- living room

def sofa(colour, shape):
    """One seat of a modular sofa. Pieces side by side join up: shape is single (arms both sides),
    west / east (the arm on that end only) or middle (no arms)."""
    f, c = f"#sofa_{colour}", f"#cushion_{colour}"
    arm_w, arm_e = shape in ("single", "west"), shape in ("single", "east")
    x0 = 2.5 if arm_w else 0.15
    x1 = 13.5 if arm_e else 15.85
    els = [bx((0, 1.5, 1.5), (16, 4.5, 15), f),
           bx((x0, 4.5, 1.2), (x1, 7.6, 11.2), f, faces=("north", "east", "west", "down")),
           bx((x0, 7.6, 1.2), (x1, 7.61, 11.2), c, faces=("up",), full=("up",)),
           bx((0 if arm_w else 0, 4.5, 11.2), (16, 15, 15), f),
           bx((x0 + 0.2, 7.6, 9.6), (x1 - 0.2, 14.2, 11.2), c, faces=("north", "up", "east", "west"), full=("north",))]
    for on, xa, xb in ((arm_w, 0, 2.5), (arm_e, 13.5, 16)):
        if on:
            els.append(bx((xa, 4.5, 1.2), (xb, 11, 15), f))
            els.append(bx((xa + 0.2, 11, 1.4), (xb - 0.2, 11.4, 14.8), c, faces=("up", "north", "east", "west")))
    for on, x in ((arm_w, 1), (arm_e, 13.8)):
        if on:
            for z in (2.2, 13.2):
                els.append(bx((x, 0, z), (x + 1.2, 1.5, z + 1.2), "#walnut"))
    return els, {f"sofa_{colour}": t(f"sofa_{colour}"), f"cushion_{colour}": t(f"cushion_{colour}"), "walnut": t("walnut")}


def coffee_table():
    els = [bx((1, 7, 3), (15, 8, 13), "#oak"), bx((2.5, 2.2, 4.5), (13.5, 2.8, 11.5), "#oak")]
    els += legs(1.6, 3.6, 14.4, 12.4, 7, 1.4, "#oak")
    return els, {"oak": t("oak")}


def tv_unit():
    els = [bx((0, 0.6, 3), (16, 8, 15), "#walnut", front="#tv_doors", full=("north",)),
           bx((0.5, 0, 3.5), (15.5, 0.6, 14.5), "#black")]
    return els, {"walnut": t("walnut"), "tv_doors": t("tv_doors"), "black": t("black")}


def tv(on):
    scr = "#screen_on" if on else "#screen_off"
    els = [bx((5, 0, 6.5), (11, 0.5, 10.5), "#black"),
           bx((7.4, 0.5, 8.6), (8.6, 3.2, 9.4), "#black"),
           bx((0, 2.8, 7.6), (16, 12.2, 8.6), "#black"),
           bx((0.35, 3.15, 7.58), (15.65, 11.85, 7.6), scr, faces=("north",), emissive=on,
              uv={"north": [0, 3.5, 16, 12.5]} if on else {"north": [0, 0, 16, 16]})]
    return els, {"black": t("black"), scr[1:]: t(scr[1:])}


def floor_lamp(on):
    shade = "#shade_lit" if on else "#shade"
    els = G.round_column(3, 0, 1, "#brass", top=True, bottom=True)
    els = [json.loads(json.dumps(e)) for e in els]
    els += [bx((7.6, 1, 7.6), (8.4, 25, 8.4), "#brass"),
            bx((4, 24, 4), (12, 31, 12), shade, faces=("north", "east", "south", "west"), emissive=on),
            bx((4.2, 24.1, 4.2), (11.8, 30.9, 11.8), shade, faces=("north", "east", "south", "west"), emissive=on),
            bx((7, 25, 7), (9, 27.5, 9), "#bulb" if on else "#bulb_off", emissive=on)]
    return els, {"brass": t("brass"), shade[1:]: t(shade[1:]), "bulb" if on else "bulb_off": t("bulb" if on else "bulb_off")}


def table_lamp(on):
    shade = "#shade_lit" if on else "#shade"
    els = G.round_column(2.2, 0, 4.2, "#ceramic", top=True)
    els = [json.loads(json.dumps(e)) for e in els]
    els += [bx((7.6, 4.2, 7.6), (8.4, 6, 8.4), "#brass"),
            bx((5, 5.6, 5), (11, 10.6, 11), shade, faces=("north", "east", "south", "west"), emissive=on),
            bx((5.2, 5.7, 5.2), (10.8, 10.5, 10.8), shade, faces=("north", "east", "south", "west"), emissive=on),
            bx((5, 10.6, 5), (11, 10.8, 11), shade, faces=("up",), emissive=on)]
    return els, {"ceramic": t("ceramic"), "brass": t("brass"), shade[1:]: t(shade[1:])}


def pendant_light(on):
    shade = "#shade_lit" if on else "#shade"
    els = [bx((7.8, 11, 7.8), (8.2, 16, 8.2), "#black"),
           bx((7, 10.5, 7), (9, 11.2, 9), "#brass"),
           bx((4.5, 6.5, 4.5), (11.5, 10.5, 11.5), shade, faces=("north", "east", "south", "west", "up"), emissive=on),
           bx((4.7, 6.6, 4.7), (11.3, 10.4, 11.3), shade, faces=("north", "east", "south", "west"), emissive=on),
           bx((7, 6.2, 7), (9, 8.2, 9), "#bulb" if on else "#bulb_off", emissive=on)]
    return els, {"black": t("black"), "brass": t("brass"), shade[1:]: t(shade[1:]),
                 "bulb" if on else "bulb_off": t("bulb" if on else "bulb_off")}


def bookcase():
    els = [bx((0, 0, 15), (16, 32, 16), "#oak"),
           bx((0, 0, 9), (1, 32, 16), "#oak"), bx((15, 0, 9), (16, 32, 16), "#oak"),
           bx((0, 0, 9), (16, 1, 16), "#oak"), bx((0, 31, 9), (16, 32, 16), "#oak")]
    for y0, y1 in ((1, 16), (16, 31)):
        els.append(bx((1, y0, 10), (15, y1, 15), "#books", faces=("north",), full=("north",)))
    for y in (8, 16, 24):
        els.append(bx((1, y - 0.6, 9.6), (15, y + 0.2, 15), "#oak"))
    return els, {"oak": t("oak"), "books": t("books")}


def fireplace(on):
    els = [bx((0, 0, 9), (16, 0.8, 16), "#marble"),
           bx((0, 0.8, 12), (2.8, 13, 16), "#marble"), bx((13.2, 0.8, 12), (16, 13, 16), "#marble"),
           bx((0, 10.4, 12), (16, 13, 16), "#marble"),
           bx((-0.4, 13, 10.8), (16.4, 14, 16), "#marble"),
           bx((2.8, 0.8, 15), (13.2, 10.4, 16), "#black"),
           bx((3.5, 0.8, 12.6), (12.5, 8.5, 15), "#black"),
           bx((4.1, 1.5, 12.58), (11.9, 7.9, 12.6), "#flames" if on else "#coals", faces=("north",),
              full=("north",), emissive=on)]
    return els, {"marble": t("marble"), "black": t("black"), "flames" if on else "coals": t("flames" if on else "coals")}


def radiator():
    els = [bx((0.5, 1.5, 13.4), (15.5, 11.5, 14.8), "#radiator", full=("north",)),
           bx((1, 0, 14), (1.8, 1.5, 14.6), "#chrome"), bx((14.2, 0, 14), (15, 1.5, 14.6), "#chrome"),
           bx((14.8, 1.5, 13.8), (15.8, 3, 14.4), "#chrome")]
    return els, {"radiator": t("radiator"), "chrome": t("chrome")}


def houseplant():
    els = [bx((5, 0, 5), (11, 5, 11), "#terracotta"), bx((5.5, 4.8, 5.5), (10.5, 5, 10.5), "#soil", faces=("up",))]
    for ang in (45, -45):
        els.append(bx((1.5, 4.5, 8), (14.5, 15.5, 8), "#leaves", faces=("north", "south"), full=("north", "south"),
                      rot=("y", ang, (8, 8, 8))))
    els.append(bx((3, 9, 3), (13, 9, 13), "#leaves", faces=("up", "down"), full=("up", "down")))
    return els, {"terracotta": t("terracotta"), "soil": t("soil"), "leaves": t("leaves")}


def dining_table_parts():
    top = [bx((0, 11, 0), (16, 12, 16), "#oak")]
    corner = {"nw": (1, 1), "ne": (13.4, 1), "sw": (1, 13.4), "se": (13.4, 13.4)}
    return top, {k: [bx((x, 0, z), (x + 1.6, 11, z + 1.6), "#oak")] for k, (x, z) in corner.items()}


def dining_chair():
    els = legs(3, 3, 13, 13, 7, 1.2, "#oak")
    els += [bx((3, 7, 3), (13, 8, 13), "#oak"), bx((3.3, 8, 3.3), (12.7, 8.6, 11.8), "#sofa_green"),
            bx((3, 8, 11.8), (4.2, 18, 13), "#oak"), bx((11.8, 8, 11.8), (13, 18, 13), "#oak"),
            bx((4.2, 13, 12), (11.8, 17.5, 12.8), "#oak")]
    return els, {"oak": t("oak"), "sofa_green": t("sofa_green")}


# ----------------------------------------------------------------- kitchen

def base_unit(fin, front, top=None, extra=()):
    """A floor cupboard 90 cm high with a worktop, the plinth set back under the doors."""
    worktop = "#worktop_dark" if fin == "white" else "#worktop_light"
    els = [bx((0.4, 0, 1.6), (15.6, 1, 16), "#plinth"),
           bx((0, 1, 1), (16, 13.2, 16), f"#carcass_{fin}", front=front, full=("north",)),
           bx((0, 13.2, 0.2), (16, 14, 16), worktop, front=None,
              uv={"up": [0, 0, 16, 16]} if top is None else None)]
    if top:
        els[-1]["faces"]["up"] = {"texture": top, "uv": [0, 0, 16, 16]}
    els += list(extra)
    tex = {f"carcass_{fin}": t(f"carcass_{fin}"), worktop[1:]: t(worktop[1:]), "plinth": t("plinth"),
           front[1:]: t(front[1:])}
    if top:
        tex[top[1:]] = t(top[1:])
    return els, tex


def kitchen_unit(fin):
    return base_unit(fin, f"#door_{fin}")


def kitchen_drawers(fin):
    return base_unit(fin, f"#drawers_{fin}")


def kitchen_sink(fin):
    tap = [bx((7.4, 14, 12.4), (8.6, 18.5, 13.6), "#chrome"), bx((7.5, 17.6, 9.2), (8.5, 18.6, 13), "#chrome"),
           bx((7.5, 16.4, 9.2), (8.5, 17.6, 10.2), "#chrome"), bx((6, 14, 12.6), (6.8, 15.5, 13.4), "#chrome"),
           bx((9.2, 14, 12.6), (10, 15.5, 13.4), "#chrome")]
    els, tex = base_unit(fin, f"#doors_{fin}", top="#sink_top", extra=tap)
    tex["chrome"] = t("chrome")
    return els, tex


def kitchen_oven(fin, on):
    hob = "#hob_on" if on else "#hob"
    oven = "#oven_on" if on else "#oven"
    els, tex = base_unit(fin, oven, top=hob)
    els[1]["faces"]["north"] = {"texture": oven, "uv": [0, 0, 16, 16]}
    if on:
        els.append(bx((0.5, 13.21, 0.5), (15.5, 14.01, 15.5), hob, faces=("up",), full=("up",), emissive=True))
        els.append(bx((0.6, 2, 0.98), (15.4, 11.6, 1), oven, faces=("north",), emissive=True,
                      uv={"north": [0.6, 4.4, 15.4, 14]}))
    return els, tex


def kitchen_wall_unit(fin):
    els = [bx((0, 5, 9), (16, 16, 16), f"#carcass_{fin}", front=f"#wall_doors_{fin}", full=("north",))]
    return els, {f"carcass_{fin}": t(f"carcass_{fin}"), f"wall_doors_{fin}": t(f"wall_doors_{fin}")}


def fridge_freezer():
    els = [bx((0.5, 0, 1.2), (15.5, 16, 16), "#white_gloss", front="#fridge_lower", full=("north",)),
           bx((0.5, 16, 1.2), (15.5, 31, 16), "#white_gloss", front="#fridge_upper", full=("north",)),
           bx((1, 0, 1.6), (15, 0.6, 2), "#black")]
    els[1]["faces"]["north"]["uv"] = [0, 0, 16, 15]
    return els, {"white_gloss": t("white_gloss"), "fridge_lower": t("fridge_lower"), "fridge_upper": t("fridge_upper"),
                 "black": t("black")}


def washing_machine(on):
    face = "#washer_on" if on else "#washer"
    els = [bx((0.5, 0, 1), (15.5, 14, 16), "#white_gloss", front=face, full=("north",))]
    if on:
        els.append(bx((2, 1, 0.98), (14, 12, 1), face, faces=("north",), emissive=False, uv={"north": [2, 3, 14, 15]}))
    return els, {"white_gloss": t("white_gloss"), face[1:]: t(face[1:])}


def microwave(on):
    face = "#microwave_on" if on else "#microwave"
    els = [bx((3, 0, 6), (13, 5.6, 13.5), "#black", front=face, full=("north",))]
    if on:
        els.append(bx((3.4, 0.8, 5.98), (10.8, 4.9, 6), face, faces=("north",), emissive=True,
                      uv={"north": [0.6, 1.5, 11, 14.2]}))
    return els, {"black": t("black"), face[1:]: t(face[1:])}


def kettle(on):
    els = [bx((5, 0, 6), (10, 0.6, 11), "#black"), bx((5.5, 0.6, 6.5), (9.5, 6, 10.5), "#stainless"),
           bx((6, 6, 7), (9, 6.6, 10), "#black"), bx((9.5, 1.6, 8), (10.8, 5.6, 9), "#black"),
           bx((4.3, 3.6, 8.1), (5.5, 4.8, 8.9), "#stainless"),
           bx((9.6, 1, 8.2), (10, 1.6, 8.8), "#bulb" if on else "#black", emissive=on)]
    return els, {"black": t("black"), "stainless": t("stainless"), "bulb": t("bulb")}


def toaster(on):
    els = [bx((4, 0.3, 5.5), (12, 5, 10.5), "#stainless"),
           bx((4.3, 0, 5.8), (11.7, 0.3, 10.2), "#black"),
           bx((12, 1.4 if on else 3, 7.5), (12.6, 2.2 if on else 3.8, 8.5), "#black")]
    els[0]["faces"]["up"] = {"texture": "#toaster_top", "uv": [0, 0, 16, 16]}
    return els, {"stainless": t("stainless"), "black": t("black"), "toaster_top": t("toaster_top")}


# ----------------------------------------------------------------- bathroom

def toilet():
    els = [bx((5.2, 0, 4), (10.8, 5.5, 10.5), "#ceramic"),
           bx((4, 4, 1.4), (12, 7, 11.2), "#ceramic"),
           bx((4, 7, 1.4), (12, 7.6, 11.2), "#ceramic", full=("up",)),
           bx((3, 5.5, 11.2), (13, 15, 15.2), "#ceramic"),
           bx((7.4, 15, 12.6), (8.6, 15.2, 13.8), "#chrome")]
    els[2]["faces"]["up"] = {"texture": "#toilet_seat", "uv": [0, 0, 16, 16]}
    return els, {"ceramic": t("ceramic"), "chrome": t("chrome"), "toilet_seat": t("toilet_seat")}


def basin():
    els = [bx((6, 0, 9), (10, 10.5, 13), "#ceramic"),
           bx((2, 10.5, 5), (14, 13.5, 15), "#ceramic"),
           bx((7.4, 13.5, 13), (8.6, 16, 14.2), "#chrome"), bx((7.5, 15.2, 10.6), (8.5, 16.2, 14), "#chrome")]
    els[1]["faces"]["up"] = {"texture": "#basin_top", "uv": [0, 0, 16, 16]}
    return els, {"ceramic": t("ceramic"), "chrome": t("chrome"), "basin_top": t("basin_top")}


def bath(part):
    """Half of a bath (two blocks long): the left half has the taps."""
    left = part == "left"
    els = [bx((0, 0, 4.5), (16, 9, 5.5), "#white_gloss"),
           bx((0, 0, 15), (16, 9, 16), "#ceramic"),
           bx((0, 0, 5.5), (16, 2, 15), "#ceramic"),
           bx((0, 8.2, 4.5), (16, 9, 16), "#ceramic", faces=("up",))]
    els[-1]["faces"]["up"] = {"texture": "#bath_top", "uv": [0, 0, 8, 16] if left else [8, 0, 16, 16]}
    els = els[:3] + [bx((0, 8.2, 4.5), (16, 9, 5.5), "#ceramic"), bx((0, 8.2, 15), (16, 9, 16), "#ceramic")]
    if left:
        els.append(bx((0, 0, 5.5), (1.2, 9, 15), "#ceramic"))
        els += [bx((2, 9, 13.4), (3, 11.4, 14.4), "#chrome"), bx((2, 10.6, 11.4), (3, 11.4, 14.4), "#chrome"),
                bx((4, 9, 13.4), (5, 10.6, 14.4), "#chrome")]
    else:
        els.append(bx((14.8, 0, 5.5), (16, 9, 15), "#ceramic"))
    return els, {"white_gloss": t("white_gloss"), "ceramic": t("ceramic"), "chrome": t("chrome")}


def shower():
    """Walk-in shower: a tray, tiled back and right walls, a glass screen on the left, and a
    rail with a big rain head."""
    els = [bx((0, 0, 0), (16, 1, 16), "#ceramic"),
           bx((0, 1, 15.4), (16, 32, 16), "#tiles", uv={"north": [0, 0, 16, 16]}),
           bx((15.4, 1, 0), (16, 32, 15.4), "#tiles"),
           bx((0, 1, 0.2), (0.3, 30, 15.4), "#glass"),
           bx((0, 30, 0.2), (0.6, 30.4, 15.4), "#chrome"),
           bx((7.6, 8, 14.6), (8.4, 28, 15.4), "#chrome"),
           bx((7.6, 27.4, 8), (8.4, 28, 15.4), "#chrome"),
           bx((5.5, 26.6, 5.5), (10.5, 27.4, 10.5), "#chrome"),
           bx((13, 12, 14.8), (14.4, 14, 15.4), "#chrome")]
    return els, {"ceramic": t("ceramic"), "tiles": t("tiles"), "glass": t("glass"), "chrome": t("chrome")}


def towel_radiator():
    els = [bx((2, 1, 14.2), (3, 16, 15.2), "#chrome"), bx((13, 1, 14.2), (14, 16, 15.2), "#chrome")]
    for y in range(2, 16, 2):
        els.append(bx((3, y, 14.4), (13, y + 0.7, 15), "#chrome"))
    els += [bx((4, 6, 13.4), (12, 13.5, 14.2), "#towel"), bx((4, 9, 15), (12, 13.5, 15.6), "#towel"),
            bx((4, 13.5, 13.4), (12, 14.2, 15.6), "#towel")]
    return els, {"chrome": t("chrome"), "towel": t("towel")}


def bathroom_cabinet():
    els = [bx((2, 4, 11.5), (14, 15, 16), "#white_gloss", front="#mirror", full=("north",))]
    return els, {"white_gloss": t("white_gloss"), "mirror": t("mirror")}


# ----------------------------------------------------------------- bedroom and study

def wardrobe(fin):
    els = [bx((0.4, 0, 2.4), (15.6, 1, 15.6), "#plinth"),
           bx((0, 1, 2), (16, 16, 16), f"#carcass_{fin}", front=f"#wardrobe_lower_{fin}", full=("north",)),
           bx((0, 16, 2), (16, 31, 16), f"#carcass_{fin}", front=f"#wardrobe_upper_{fin}", full=("north",)),
           bx((-0.2, 31, 1.6), (16.2, 32, 16), f"#carcass_{fin}")]
    els[1]["faces"]["north"]["uv"] = [0, 0, 16, 15]
    return els, {"plinth": t("plinth"), f"carcass_{fin}": t(f"carcass_{fin}"),
                 f"wardrobe_lower_{fin}": t(f"wardrobe_lower_{fin}"), f"wardrobe_upper_{fin}": t(f"wardrobe_upper_{fin}")}


def chest_of_drawers(fin):
    els = [bx((0.5, 0, 2.5), (15.5, 1, 15.5), "#plinth"),
           bx((0, 1, 2), (16, 14, 16), f"#carcass_{fin}", front=f"#drawers_{fin}", full=("north",))]
    return els, {"plinth": t("plinth"), f"carcass_{fin}": t(f"carcass_{fin}"), f"drawers_{fin}": t(f"drawers_{fin}")}


def bedside_table(fin):
    els = [bx((1.5, 0, 2.5), (14.5, 8, 15.5), f"#carcass_{fin}", front=f"#drawers2_{fin}", full=("north",))]
    return els, {f"carcass_{fin}": t(f"carcass_{fin}"), f"drawers2_{fin}": t(f"drawers2_{fin}")}


def desk():
    els = [bx((0, 11, 1), (16, 12, 15), "#oak"),
           bx((10.5, 0, 1.6), (15.6, 11, 14.4), "#oak", front="#desk_drawers", full=("north",)),
           bx((0.4, 0, 1.6), (1.6, 11, 14.4), "#oak"),
           bx((1.6, 4, 13.6), (10.5, 11, 14.4), "#oak")]
    return els, {"oak": t("oak"), "desk_drawers": t("desk_drawers")}


def office_chair():
    els = [bx((7.4, 0.8, 2.2), (8.6, 1.8, 8), "#black"), bx((7.4, 0.8, 8), (8.6, 1.8, 13.8), "#black"),
           bx((2.2, 0.8, 7.4), (8, 1.8, 8.6), "#black"), bx((8, 0.8, 7.4), (13.8, 1.8, 8.6), "#black")]
    for x, z in ((2.2, 7.4), (12.8, 7.4), (7.4, 2.2), (7.4, 12.8)):
        els.append(bx((x, 0, z), (x + 1.2, 0.8, z + 1.2), "#dark_grey"))
    els += [bx((7.4, 1.8, 7.4), (8.6, 6.4, 8.6), "#chrome"),
            bx((3, 6.4, 3), (13, 8.6, 13), "#office_fabric"),
            bx((3.5, 8.6, 12.4), (12.5, 19, 13.6), "#office_fabric"),
            bx((7.4, 6.4, 12.6), (8.6, 9.6, 13.4), "#black"),
            bx((2.2, 9.6, 5), (3.2, 10.4, 11), "#black"), bx((12.8, 9.6, 5), (13.8, 10.4, 11), "#black"),
            bx((2.4, 6.4, 8), (3, 9.6, 9), "#black"), bx((13, 6.4, 8), (13.6, 9.6, 9), "#black")]
    return els, {"black": t("black"), "dark_grey": t("dark_grey"), "chrome": t("chrome"),
                 "office_fabric": t("office_fabric")}


def computer(on):
    scr = "#monitor_on" if on else "#screen_off"
    els = [bx((5.5, 0, 9), (10.5, 0.4, 12), "#black"), bx((7.4, 0.4, 10.4), (8.6, 4, 11.2), "#black"),
           bx((1.5, 3, 9.6), (14.5, 11.5, 10.4), "#black"),
           bx((1.8, 3.3, 9.58), (14.2, 11.2, 9.6), scr, faces=("north",), full=("north",), emissive=on),
           bx((3, 0, 3), (12, 0.5, 6.5), "#keyboard"), bx((13, 0, 4), (14.4, 0.6, 6), "#black")]
    els[-2]["faces"]["up"] = {"texture": "#keyboard", "uv": [0, 0, 16, 16]}
    return els, {"black": t("black"), scr[1:]: t(scr[1:]), "keyboard": t("keyboard")}


# ----------------------------------------------------------------- the list

class F:
    """One block. kind: basic, tabletop, sofa, table, tall (2 high) or wide (2 long).
    shape: collision box (px, north facing; for tall pieces the lower block's box, the upper is
    the same box minus 16 clamped to the block). seat: sitting height px. rows: storage rows.
    light: light level when on (0 = can't be switched). surface: what tabletop items standing on it
    sink to. run: ticks before it switches itself off. effect: particles / sounds."""

    def __init__(self, ident, name, builder, kind="basic", shape=(0, 0, 0, 16, 16, 16), seat=0, rows=0, light=0,
                 surface="floor", run=0, effect="none", toggle=False, sound="WOOD", group="living", oven=False,
                 cutout=False, translucent=False):
        self.ident, self.name, self.builder, self.kind = ident, name, builder, kind
        self.shape, self.seat, self.rows, self.light, self.surface = shape, seat, rows, light, surface
        self.run, self.effect, self.toggle, self.sound, self.group = run, effect, toggle or light > 0, sound, group
        self.oven, self.cutout, self.translucent = oven, cutout, translucent


def _home():
    out = []
    for c in SOFA_COLOURS:
        nice = {"grey": "Grey", "navy": "Navy", "green": "Green", "brown": "Brown Leather", "cream": "Cream"}[c]
        out.append(F(f"sofa_{c}", f"Sofa ({nice})", (lambda c=c: lambda shape: sofa(c, shape))(), "sofa",
                     (0, 0, 1, 16, 9, 15), seat=7.6, sound="WOOL"))
    out += [
        F("coffee_table", "Coffee Table", coffee_table, shape=(1, 0, 3, 15, 8, 13), surface="low"),
        F("tv_unit", "TV Unit", tv_unit, shape=(0, 0, 3, 16, 8, 15), rows=2, surface="low"),
        F("tv", "Flat Screen TV", tv, "tabletop", (0, 0, 6.5, 16, 12.2, 10.5), light=4, toggle=True, effect="tv",
          sound="METAL"),
        F("floor_lamp", "Floor Lamp", floor_lamp, "tall", (4, 0, 4, 12, 16, 12), light=14, effect="click", sound="METAL"),
        F("table_lamp", "Table Lamp", table_lamp, "tabletop", (5, 0, 5, 11, 10.8, 11), light=13, effect="click",
          sound="GLASS"),
        F("pendant_light", "Ceiling Pendant Light", pendant_light, shape=(4.5, 6, 4.5, 11.5, 16, 11.5), light=15,
          effect="click", sound="GLASS"),
        F("bookcase", "Bookcase", bookcase, "tall", (0, 0, 9, 16, 16, 16)),
        F("fireplace", "Electric Fire with Mantel", fireplace, shape=(0, 0, 9, 16, 14, 16), light=12, effect="fire",
          sound="STONE"),
        F("radiator", "Radiator", radiator, shape=(0.5, 0, 13.4, 15.5, 11.5, 14.8), sound="METAL"),
        F("houseplant", "House Plant", houseplant, "tabletop", (5, 0, 5, 11, 14, 11), sound="GRASS", cutout=True),
        F("dining_table", "Dining Table", None, "table", (0, 0, 0, 16, 12, 16), surface="table"),
        F("dining_chair", "Dining Chair", dining_chair, shape=(3, 0, 3, 13, 9, 13), seat=8.6),
    ]
    for fin in FINISHES:
        F_ = fin.capitalize()
        out += [
            F(f"kitchen_unit_{fin}", f"Kitchen Cupboard ({F_})", (lambda f=fin: lambda: kitchen_unit(f))(),
              rows=3, surface="counter", group="kitchen"),
            F(f"kitchen_drawers_{fin}", f"Kitchen Drawers ({F_})", (lambda f=fin: lambda: kitchen_drawers(f))(),
              rows=3, surface="counter", group="kitchen"),
            F(f"kitchen_sink_{fin}", f"Kitchen Sink ({F_})", (lambda f=fin: lambda: kitchen_sink(f))(),
              rows=2, surface="counter", toggle=True, run=60, effect="tap", group="kitchen"),
            F(f"kitchen_oven_{fin}", f"Oven and Hob ({F_})", (lambda f=fin: lambda on: kitchen_oven(f, on))(),
              light=7, surface="counter", oven=True, group="kitchen", sound="METAL"),
            F(f"kitchen_wall_unit_{fin}", f"Kitchen Wall Cupboard ({F_})", (lambda f=fin: lambda: kitchen_wall_unit(f))(),
              shape=(0, 5, 9, 16, 16, 16), rows=2, group="kitchen"),
        ]
    out += [
        F("fridge_freezer", "Fridge Freezer", fridge_freezer, "tall", (0.5, 0, 1.2, 15.5, 16, 16), rows=3,
          group="kitchen", sound="METAL"),
        F("washing_machine", "Washing Machine", washing_machine, shape=(0.5, 0, 1, 15.5, 14, 16), toggle=True, run=600,
          effect="washer", surface="counter", group="kitchen", sound="METAL"),
        F("microwave", "Microwave", microwave, "tabletop", (3, 0, 6, 13, 5.6, 13.5), light=6, run=200,
          effect="microwave", group="kitchen", sound="METAL"),
        F("kettle", "Kettle", kettle, "tabletop", (4.3, 0, 6, 10.8, 6.6, 11), toggle=True, run=100, effect="kettle",
          group="kitchen", sound="METAL"),
        F("toaster", "Toaster", toaster, "tabletop", (4, 0, 5.5, 12.6, 5, 10.5), toggle=True, run=80, effect="toaster",
          group="kitchen", sound="METAL"),
        F("toilet", "Toilet", toilet, shape=(3, 0, 1.4, 13, 15.2, 15.2), seat=7.6, effect="flush", group="bathroom",
          sound="STONE"),
        F("basin", "Bathroom Basin", basin, shape=(2, 0, 5, 14, 13.5, 15), toggle=True, run=60, effect="tap",
          group="bathroom", sound="STONE"),
        F("bath", "Bath", bath, "wide", (0, 0, 4.5, 16, 9, 16), seat=2.2, toggle=True, run=100, effect="bath",
          group="bathroom", sound="STONE"),
        F("shower", "Walk-in Shower", shower, "tall", (0, 0, 0, 16, 1, 16), toggle=True, run=400, effect="shower",
          group="bathroom", sound="STONE", translucent=True),
        F("towel_radiator", "Heated Towel Rail", towel_radiator, shape=(2, 0, 13.4, 14, 16, 15.6), group="bathroom",
          sound="METAL"),
        F("bathroom_cabinet", "Bathroom Mirror Cabinet", bathroom_cabinet, shape=(2, 4, 11.5, 14, 15, 16), rows=1,
          group="bathroom", sound="GLASS"),
    ]
    for fin in FINISHES:
        F_ = fin.capitalize()
        out += [
            F(f"wardrobe_{fin}", f"Wardrobe ({F_})", (lambda f=fin: lambda: wardrobe(f))(), "tall", (0, 0, 2, 16, 16, 16),
              rows=6, group="bedroom"),
            F(f"chest_of_drawers_{fin}", f"Chest of Drawers ({F_})", (lambda f=fin: lambda: chest_of_drawers(f))(),
              shape=(0, 0, 2, 16, 14, 16), rows=3, surface="counter", group="bedroom"),
            F(f"bedside_table_{fin}", f"Bedside Table ({F_})", (lambda f=fin: lambda: bedside_table(f))(),
              shape=(1.5, 0, 2.5, 14.5, 8, 15.5), rows=1, surface="low", group="bedroom"),
        ]
    out += [
        F("desk", "Desk", desk, shape=(0, 0, 1, 16, 12, 15), rows=1, surface="table", group="bedroom"),
        F("office_chair", "Office Chair", office_chair, shape=(2.2, 0, 2.2, 13.8, 10, 13.8), seat=8.6, group="bedroom",
          sound="METAL"),
        F("computer", "Computer", computer, "tabletop", (1.5, 0, 3, 14.5, 11.5, 12), light=4, effect="click",
          group="bedroom", sound="METAL"),
    ]
    return out


HOME = None


def home():
    global HOME
    if HOME is None:
        HOME = _home()
    return HOME


# ----------------------------------------------------------------- writing

def _model(els, textures, f):
    textures = dict(textures)
    textures["particle"] = next(iter(textures.values()))
    m = {"textures": textures, "elements": els}
    if f.translucent:
        m["render_type"] = "minecraft:translucent"
    elif f.cutout:
        m["render_type"] = "minecraft:cutout_mipped"
    return m


def _write_model(name, els, textures, f):
    G.write_json(G.ASSETS / f"models/block/home/{name}.json", _model(els, textures, f), compact=True)
    return f"{G.MOD_ID}:block/home/{name}"


def _facings(extra=None, model=None):
    """Variants for the four facings: extra is a 'key=value' prefix, model the model id."""
    out = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        v = {"model": model}
        if y:
            v["y"] = y
        out[(extra + "," if extra else "") + f"facing={facing}"] = v
    return out


def _item_model(name, parent, els):
    lo = [min(e["from"][i] for e in els) for i in range(3)]
    hi = [max(e["to"][i] for e in els) for i in range(3)]
    size = max(hi[0] - lo[0], hi[1] - lo[1], hi[2] - lo[2]) / 16
    s = round(min(1.0, max(0.22, 0.95 / max(size, 0.4))) * 0.68, 3)
    cy = ((lo[1] + hi[1]) / 2) / 16
    ty = round((0.5 - cy) * 16 * s, 2)
    G.write_json(G.ASSETS / f"models/item/{name}.json", {
        "parent": parent,
        "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, ty, 0], "scale": [s, s, s]},
                    "ground": {"translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
                    "fixed": {"scale": [0.5, 0.5, 0.5]},
                    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375] * 3},
                    "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.4] * 3},
                    "firstperson_lefthand": {"rotation": [0, 225, 0], "scale": [0.4] * 3}}})


def write_block(f):
    n = f.ident
    bs = G.ASSETS / f"blockstates/{n}.json"
    lit_states = ("false", "true")
    if f.kind == "sofa":
        variants, first = {}, None
        for shape in ("single", "west", "east", "middle"):
            els, tex = f.builder(shape)
            mid = _write_model(f"{n}_{shape}", els, tex, f)
            first = first or (mid, els)
            variants.update(_facings(f"shape={shape}", mid))
        G.write_json(bs, {"variants": variants})
        _item_model(n, first[0], first[1])
        return
    if f.kind == "table":
        top, corner_legs = dining_table_parts()
        tex = {"oak": t("oak")}
        top_m = _write_model(f"{n}_top", top, tex, f)
        parts = [{"apply": {"model": top_m}}]
        cond = {"nw": ("north", "west"), "ne": ("north", "east"), "sw": ("south", "west"), "se": ("south", "east")}
        for k, els in corner_legs.items():
            m = _write_model(f"{n}_leg_{k}", els, tex, f)
            a, b = cond[k]
            parts.append({"when": {a: "false", b: "false"}, "apply": {"model": m}})
        G.write_json(bs, {"multipart": parts})
        whole = top + sum(corner_legs.values(), [])
        item = _write_model(f"{n}_item", whole, tex, f)
        _item_model(n, item, whole)
        return

    takes_on = f.kind not in ("wide",) and len(inspect.signature(f.builder).parameters) > 0

    def build(on):
        return f.builder(on) if takes_on else f.builder()

    variants = {}
    item = None
    swaps = takes_on                       # the model changes when it's switched on
    if f.kind == "tabletop":
        for on in ((False, True) if swaps else (False,)):
            els, tex = build(on)
            for lvl in LEVELS:
                drop = SURFACES[lvl] - 16 if lvl != "floor" else 0
                mid = _write_model(f"{n}{'_on' if on else ''}_{lvl}", shift(els, drop), tex, f)
                if lvl == "floor" and not on:
                    item = (mid, els)
                for lit in (lit_states if not swaps else ("true" if on else "false",)):
                    variants.update(_facings(f"level={lvl},lit={lit}", mid))
    elif f.kind == "tall":
        empty = _write_model(f"{n}_upper", [], {"p": next(iter(build(False)[1].values()))}, f)
        for on in ((False, True) if swaps else (False,)):
            els, tex = build(on)
            mid = _write_model(f"{n}{'_on' if on else ''}", els, tex, f)
            if not on:
                item = (mid, els)
            for lit in (lit_states if not swaps else ("true" if on else "false",)):
                variants.update(_facings(f"half=lower,lit={lit}", mid))
                variants.update(_facings(f"half=upper,lit={lit}", empty))
    elif f.kind == "wide":
        whole = []
        for part in ("left", "right"):
            els, tex = f.builder(part)
            mid = _write_model(f"{n}_{part}", els, tex, f)
            for lit in lit_states:
                variants.update(_facings(f"lit={lit},part={part}", mid))
            whole += json.loads(json.dumps(els)) if part == "left" else \
                [dict(e, **{"from": [e["from"][0] + 16] + e["from"][1:], "to": [e["to"][0] + 16] + e["to"][1:]}) for e in els]
        for e in whole:
            for k in ("from", "to"):
                e[k][0] = round(e[k][0] / 2, 3)
                e[k][2] = round(8 + (e[k][2] - 8) / 2, 3)
                e[k][1] = round(e[k][1] / 2, 3)
        item_m = _write_model(f"{n}_item", whole, tex, f)
        item = (item_m, whole)
    else:
        for on in ((False, True) if swaps else (False,)):
            els, tex = build(on)
            mid = _write_model(f"{n}{'_on' if on else ''}", els, tex, f)
            if not on:
                item = (mid, els)
            for lit in (lit_states if not swaps else ("true" if on else "false",)):
                variants.update(_facings(f"lit={lit}", mid))
    G.write_json(bs, {"variants": variants})
    _item_model(n, item[0], item[1])


def write_loot(f):
    full = f"{G.MOD_ID}:{f.ident}"
    entry = {"type": "minecraft:item", "name": full}
    conds = [{"condition": "minecraft:survives_explosion"}]
    if f.kind == "tall":
        conds.append({"condition": "minecraft:block_state_property", "block": full, "properties": {"half": "lower"}})
    if f.kind == "wide":
        conds.append({"condition": "minecraft:block_state_property", "block": full, "properties": {"part": "left"}})
    G.write_json(G.DATA / G.MOD_ID / f"loot_tables/blocks/{f.ident}.json",
                 {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [entry], "conditions": conds}]})


def write_java():
    rows = []
    for f in home():
        sx = ", ".join(f"{v}" for v in f.shape)
        rows.append(f'    {f.ident.upper()}("{f.ident}", Kind.{f.kind.upper()}, Group.{f.group.upper()}, new double[]{{{sx}}}, '
                    f'{f.seat}f, {f.rows}, {f.light}, {str(f.toggle).lower()}, Surface.{f.surface.upper()}, {f.run}, '
                    f'Effect.{f.effect.upper()}, {str(f.oven).lower()}, SoundType.{f.sound})')
    body = ",\n".join(rows) + ";"
    JAVA.parent.mkdir(parents=True, exist_ok=True)
    JAVA.write_text(f'''package com.ptmuk.home;

import net.minecraft.world.level.block.SoundType;

/** Generated by tools/home_furniture.py with the models. Do not edit by hand. */
public enum HomeFurnitureType {{
{body}

    public enum Kind {{ BASIC, TABLETOP, SOFA, TABLE, TALL, WIDE }}

    public enum Group {{ LIVING, KITCHEN, BATHROOM, BEDROOM }}

    /** The top of the furniture in pixels, for tabletop items standing on it. */
    public enum Surface {{
        FLOOR(16), LOW(8), TABLE(12), COUNTER(14);

        public final int top;

        Surface(int top) {{
            this.top = top;
        }}
    }}

    public enum Effect {{ NONE, CLICK, TV, FIRE, TAP, BATH, SHOWER, FLUSH, KETTLE, TOASTER, MICROWAVE, WASHER }}

    public final String id;
    public final Kind kind;
    public final Group group;
    /** Collision box in pixels for a north-facing block (the lower block for tall pieces). */
    public final double[] box;
    /** Seat height in pixels, 0 if you can't sit on it. */
    public final float seat;
    /** Rows of storage (nine slots each), 0 for none. */
    public final int rows;
    /** Light level when it's on. */
    public final int light;
    /** Right-click switches it on and off. */
    public final boolean toggle;
    public final Surface surface;
    /** Ticks before it switches itself off again, 0 to stay on. */
    public final int run;
    public final Effect effect;
    /** Works as an oven (cooks food like a smoker). */
    public final boolean oven;
    public final SoundType sound;

    HomeFurnitureType(String id, Kind kind, Group group, double[] box, float seat, int rows, int light, boolean toggle,
                      Surface surface, int run, Effect effect, boolean oven, SoundType sound) {{
        this.id = id;
        this.kind = kind;
        this.group = group;
        this.box = box;
        this.seat = seat;
        this.rows = rows;
        this.light = light;
        this.toggle = toggle;
        this.surface = surface;
        this.run = run;
        this.effect = effect;
        this.oven = oven;
        this.sound = sound;
    }}
}}
''')


def generate(gmod):
    global G
    G = gmod
    make_textures()
    for f in home():
        write_block(f)
    write_java()


def write_data(gmod):
    global G
    G = gmod
    for f in home():
        write_loot(f)
    return [f"{G.MOD_ID}:{f.ident}" for f in home()]


def lang(mid):
    out = {f"block.{mid}.{f.ident}": f.name for f in home()}
    out["itemGroup.ptmuk.home"] = "UK Home Furniture"
    out["entity.ptmuk.seat"] = "Seat"
    return out
