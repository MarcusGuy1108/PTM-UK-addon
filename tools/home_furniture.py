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
import shop_textures as ST

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


def hbar(x0, x1, y, zf, tex="#chrome", d=0.9, t=0.5):
    """A bar handle across a front at height y, standing d proud of the face at z = zf."""
    return [bx((x0, y - t / 2, zf - d), (x1, y + t / 2, zf - d + t), tex),
            bx((x0 + 0.3, y - 0.2, zf - d + t), (x0 + 0.8, y + 0.2, zf), tex),
            bx((x1 - 0.8, y - 0.2, zf - d + t), (x1 - 0.3, y + 0.2, zf), tex)]


def vbar(x, y0, y1, zf, tex="#chrome", d=0.9, t=0.5):
    """An upright bar handle at x from y0 to y1, standing d proud of the face at z = zf."""
    return [bx((x - t / 2, y0, zf - d), (x + t / 2, y1, zf - d + t), tex),
            bx((x - 0.2, y0 + 0.3, zf - d + t), (x + 0.2, y0 + 0.8, zf), tex),
            bx((x - 0.2, y1 - 0.8, zf - d + t), (x + 0.2, y1 - 0.3, zf), tex)]


def knob(x, y, zf, r=0.55, d=0.7, tex="#chrome"):
    """A small round-ish knob (a box with a smaller cap) on a front at z = zf."""
    return [bx((x - r, y - r, zf - d * 0.6), (x + r, y + r, zf), tex),
            bx((x - r * 0.7, y - r * 0.7, zf - d), (x + r * 0.7, y + r * 0.7, zf - d * 0.6), tex)]


def front(x0, y0, x1, y1, zf, tex, side, d=0.4):
    """A door or drawer front standing d proud of the carcass face at z = zf: tex on its face (whole
    texture), side on its edges."""
    return bx((x0, y0, zf - d), (x1, y1, zf), side, front=tex, full=("north",), faces=("north", "east", "west", "up", "down"))


def split_front(x0, y0, x1, y1, zf, tex, side, uv, d=0.4):
    """A front taller than a block, in two pieces split at the block line (one face spanning two
    blocks is lit wrongly by smooth lighting): uv is the whole front's [u0, v0, u1, v1]."""
    u0, v0, u1, v1 = uv
    vm = v0 + (v1 - v0) * (y1 - 16) / (y1 - y0)
    lo, hi = front(x0, y0, x1, 16, zf, tex, side, d), front(x0, 16, x1, y1, zf, tex, side, d)
    lo["faces"]["north"]["uv"] = [u0, vm, u1, v1]
    hi["faces"]["north"]["uv"] = [u0, v0, u1, vm]
    del lo["faces"]["up"], hi["faces"]["down"]
    return [lo, hi]


def moved(els, dx=0.0, dz=0.0):
    """Elements moved sideways (round columns are built round the middle of the block)."""
    out = json.loads(json.dumps(els))
    for el in out:
        for k in ("from", "to"):
            el[k][0] = round(el[k][0] + dx, 3)
            el[k][2] = round(el[k][2] + dz, 3)
        if "rotation" in el:
            el["rotation"]["origin"][0] = round(el["rotation"]["origin"][0] + dx, 3)
            el["rotation"]["origin"][2] = round(el["rotation"]["origin"][2] + dz, 3)
    return out


def bowl(x0, z0, x1, z1, y0, y1, tex, floor=None):
    """The inside of a sink, basin or bath: a floor at y0 and four walls up to y1 facing inwards."""
    return [bx((x0, y0 - 0.2, z0), (x1, y0, z1), floor or tex, faces=("up",)),
            bx((x0, y0, z0), (x1, y1, z0 + 0.2), tex, faces=("south",)),
            bx((x0, y0, z1 - 0.2), (x1, y1, z1), tex, faces=("north",)),
            bx((x0, y0, z0), (x0 + 0.2, y1, z1), tex, faces=("east",)),
            bx((x1 - 0.2, y0, z0), (x1, y1, z1), tex, faces=("west",))]


def rim(x0, z0, x1, z1, ox0, oz0, ox1, oz1, y0, y1, tex, up=None):
    """A top surface (x0..x1, z0..z1) with a hole (ox0..ox1, oz0..oz1) cut in it."""
    els = [bx((x0, y0, z0), (x1, y1, oz0), tex), bx((x0, y0, oz1), (x1, y1, z1), tex),
           bx((x0, y0, oz0), (ox0, y1, oz1), tex), bx((ox1, y0, oz0), (x1, y1, oz1), tex)]
    if up:
        for el in els:
            el["faces"]["up"]["texture"] = up
    return els


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
        "oak": s(HT.timber((176, 132, 84), 128), P + "oak"),
        "walnut": s(HT.timber((92, 60, 40), 128), P + "walnut"),
        "white_gloss": s(HT.gloss((238, 238, 234), 128), P + "white_gloss"),
        "worktop_dark": s(HT.granite(), P + "worktop_dark"),
        "worktop_light": s(HT.granite(base=(196, 194, 188)), P + "worktop_light"),
        "plinth": s(HT.solid((40, 40, 42)), P + "plinth"),
        "stainless": s(H.stainless(64), P + "stainless"),
        "chrome": s(HT.chrome(), P + "chrome"),
        "ceramic": s(HT.ceramic(128), P + "ceramic"),
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
        "oven_door": s(HT.oven_door(False), P + "oven_door"),
        "oven_door_on": s(HT.oven_door(True), P + "oven_door_on"),
        "oven_fascia": s(HT.oven_fascia(), P + "oven_fascia"),
        "sink_top": s(HT.sink_top(), P + "sink_top"),
        "washer": s(HT.washer_front(1), P + "washer"),
        "washer_on": s(HT.washer_front(8), P + "washer_on", 2),
        "microwave_door": s(HT.microwave_door(False), P + "microwave_door"),
        "microwave_door_on": s(HT.microwave_door(True), P + "microwave_door_on"),
        "microwave_panel": s(HT.microwave_panel(False), P + "microwave_panel"),
        "microwave_panel_on": s(HT.microwave_panel(True), P + "microwave_panel_on"),
        "water": s(HT.water(), P + "bath_water"),
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
    for name, colour in SOFA_COLOURS.items():
        if name == "brown":
            TEX[f"sofa_{name}"] = s(HT.leather(colour, 128), P + f"sofa_{name}")
            TEX[f"cushion_{name}"] = s(HT.leather(tuple(int(c * 1.08) for c in colour), 128), P + f"cushion_{name}")
        else:
            TEX[f"sofa_{name}"] = s(HT.fabric(colour, 128), P + f"sofa_{name}")
            TEX[f"cushion_{name}"] = s(HT.cushion(colour, 128), P + f"cushion_{name}")
    for fin, colour in FINISHES.items():
        wood = HT.timber(colour) if fin == "oak" else None
        # fronts drawn to the shape of the door or drawer they go on (handles are 3D)
        TEX[f"door_plain_{fin}"] = s(HT.door_front(colour, wood=wood, handle=None, h=48), P + f"door_plain_{fin}")
        TEX[f"door_half_{fin}"] = s(HT.door_front(colour, size=32, wood=wood, handle=None, h=48), P + f"door_half_{fin}")
        TEX[f"wall_door_{fin}"] = s(HT.door_front(colour, size=32, wood=wood, handle=None, h=44), P + f"wall_door_{fin}")
        TEX[f"drawer_{fin}"] = s(HT.door_front(colour, wood=wood, handle=None, shaker=False, h=16), P + f"drawer_{fin}")
        TEX[f"drawer_half_{fin}"] = s(HT.door_front(colour, size=32, wood=wood, handle=None, shaker=False, h=16),
                                      P + f"drawer_half_{fin}")
        TEX[f"wardrobe_{fin}"] = s(HT.tall_door(colour, wood=HT.timber(colour) if fin == "oak" else None,
                                                mirror_panel=fin == "white", handles=False), P + f"wardrobe_{fin}")
        TEX[f"carcass_{fin}"] = TEX["oak"] if fin == "oak" else TEX["white_gloss"]
    TEX["tv_doors"] = G.save(HT.door_front((92, 60, 40), wood=HT.timber((92, 60, 40)), split="v", shaker=False, h=32),
                             P + "tv_unit_doors")
    TEX["desk_drawers"] = G.save(HT.door_front((176, 132, 84), wood=HT.timber((176, 132, 84)), split=3, h=56),
                                 P + "desk_drawers")
    make_shop_textures()


# what's on each run of shelving: (rows of texture a, rows of texture b)
SHELF_GOODS = {"groceries": (("cereal", "tins"), ("jars", "pasta")),
               "drinks": (("bottles", "cans"), ("cartons", "bottles")),
               "household": (("detergent", "rolls"), ("sprays", "detergent")),
               "snacks": (("crisps", "biscuits"), ("chocolate", "crisps")),
               "diy": (("paint", "tools"), ("tools", "paint")),
               "games": (("games", "consoles"), ("games", "games")),
               "pharmacy": (("medicine", "toiletries"), ("toiletries", "medicine")),
               "newsagent": (("magazines", "chocolate"), ("crisps", "magazines")),
               "chilled": (("milk", "yoghurt"), ("meat", "meals"))}


GARMENTS = {"navy": (36, 48, 90), "red": (180, 30, 40), "white": (236, 236, 232), "grey": (130, 132, 136),
            "green": (60, 110, 70), "mustard": (210, 160, 40)}


def _round_alpha(img):
    """Clear the corners outside the circle (tyre and drum ends, drawn on a square face)."""
    from PIL import ImageDraw
    from PIL import Image as _I
    mask = _I.new("L", img.size, 0)
    ImageDraw.Draw(mask).ellipse([0, 0, img.size[0] - 1, img.size[1] - 1], fill=255)
    out = img.copy()
    out.putalpha(mask)
    return out


def make_shop_textures():
    s, Q = G.save, P + "shop/"
    for kind, (a, b) in SHELF_GOODS.items():
        TEX[f"goods_{kind}_a"] = s(ST.goods(*a), Q + f"goods_{kind}_a")
        TEX[f"goods_{kind}_b"] = s(ST.goods(*b), Q + f"goods_{kind}_b")
    TEX.update({
        "pegboard": s(ST.perforated(), Q + "pegboard"),
        "belt": s(ST.belt(), Q + "belt"),
        "scanner": s(ST.scanner(), Q + "scanner"),
        "till_screen": s(ST.till_screen(), Q + "till_screen"),
        "kiosk_screen": s(ST.kiosk_screen(), Q + "kiosk_screen"),
        "card_reader": s(ST.card_reader(), Q + "card_reader"),
        "lane_light": s(ST.lane_light(), Q + "lane_light"),
        "mesh": s(ST.mesh(), Q + "trolley_mesh"),
        "plastic_blue": s(ST.plastic((40, 90, 190)), Q + "plastic_blue"),
        "plastic_red": s(ST.plastic((200, 30, 36)), Q + "plastic_red"),
        "plastic_grey": s(ST.plastic((150, 154, 160)), Q + "plastic_grey"),
        "plastic_yellow": s(ST.plastic((246, 200, 40)), Q + "plastic_yellow"),
        "produce": s(ST.produce(), Q + "produce"),
        "crate": s(ST.crate_side(), Q + "crate"),
        "frozen_lid": s(ST.frozen_lid(), Q + "frozen_lid"),
        "promo_green": s(ST.promo_band((40, 140, 70)), Q + "promo_green"),
        "promo_blue": s(ST.promo_band((40, 90, 190)), Q + "promo_blue"),
        "promo_pink": s(ST.promo_band((220, 60, 140)), Q + "promo_pink"),
        "chiller_back": s(HT.glow((236, 244, 250)), Q + "chiller_back"),
        "popcorn": s(ST.popcorn(), Q + "popcorn"),
        "sweets": s(ST.sweets(), Q + "sweets"),
        "drinks_panel": s(ST.drinks_panel(), Q + "drinks_panel"),
        "atm": s(ST.atm(), Q + "cash_machine"),
        "boxes": s(ST.boxes(), Q + "boxes"),
        "pallet": s(ST.pallet(), Q + "pallet"),
        "rack_blue": s(ST.plastic((30, 70, 160)), Q + "rack_blue"),
        "rack_orange": s(ST.plastic((240, 110, 20)), Q + "rack_orange"),
        "velvet_red": s(ST.velvet((150, 20, 30)), Q + "velvet_red"),
        "velvet_blue": s(ST.velvet((30, 50, 120)), Q + "velvet_blue"),
    })
    clear_round = _round_alpha
    TEX.update({
        "toolchest_front": s(ST.toolchest_front(), Q + "toolchest_front"),
        "tyre_tread": s(ST.tyre_tread(), Q + "tyre_tread"),
        "tyre_side": s(clear_round(ST.tyre_side()), Q + "tyre_side"),
        "drum_side": s(ST.drum_side(), Q + "drum_side"),
        "drum_top": s(clear_round(ST.drum_top()), Q + "drum_top"),
        "oil": s(ST.oil(), Q + "oil"),
        "order_screen": s(ST.order_screen(), Q + "order_screen"),
        "grille": s(ST.grille(), Q + "grille"),
        "ticket_screen": s(ST.ticket_screen(), Q + "ticket_screen"),
        "lens": s(ST.lens(), Q + "lens"),
        "plants": s(ST.plants(), Q + "plants"),
        "slats": s(ST.slats(), Q + "slats"),
        "till_face": s(ST.till_face(), Q + "till_face"),
    })
    board = ST.menu_board()
    TEX["menu_left"] = s(board.crop((0, 0, 128, 128)), Q + "menu_left")
    TEX["menu_right"] = s(board.crop((128, 0, 256, 128)), Q + "menu_right")
    for name, colour in GARMENTS.items():
        TEX[f"garment_{name}"] = s(ST.garment(colour), Q + f"garment_{name}")
    TEX.update({
        "pump_clips": s(ST.pump_clips(), Q + "pump_clips"),
        "back_bar": s(ST.back_bar(), Q + "back_bar"),
        "dartboard": s(ST.dartboard(), Q + "dartboard"),
        "oven_brick": s(ST.oven_brick(), Q + "oven_brick"),
        "doner": s(ST.doner(), Q + "doner"),
        "heater": s(ST.heater(False), Q + "heater"),
        "heater_on": s(ST.heater(True), Q + "heater_on"),
        "cakes": s(ST.cakes(), Q + "cakes"),
        "vinyl": s(ST.vinyl(), Q + "vinyl"),
        "mixer": s(ST.mixer(), Q + "mixer"),
        "dance_floor": s(ST.dance_floor(), Q + "dance_floor", 4),
        "dance_floor_off": s(ST.dance_floor_off(), Q + "dance_floor_off"),
        "bottle_fridge": s(ST.bottle_fridge(), Q + "bottle_fridge"),
        "gym_screen": s(ST.gym_screen(), Q + "gym_screen"),
        "leather_red": s(HT.leather((150, 24, 30)), Q + "leather_red"),
        "leather_black": s(HT.leather((30, 30, 32)), Q + "leather_black"),
        "baize": s(ST.baize(), Q + "baize"),
    })
    top = ST.pool_top()
    TEX["pool_left"] = s(top.crop((0, 0, 64, 64)), Q + "pool_left")
    TEX["pool_right"] = s(top.crop((64, 0, 128, 64)), Q + "pool_right")
    for on in (False, True):
        lo, hi = ST.fruit_machine(on)
        sfx = "_on" if on else ""
        TEX["fruit_lower" + sfx] = s(lo, Q + "fruit_lower" + sfx, 3 if on else None)
        TEX["fruit_upper" + sfx] = s(hi, Q + "fruit_upper" + sfx, 3 if on else None)
    lo, hi = HT.halves(ST.frost_doors())
    TEX["freezer_lower"], TEX["freezer_upper"] = s(lo, Q + "freezer_lower"), s(hi, Q + "freezer_upper")
    for film in ST.POSTERS:
        lo, hi = HT.halves(ST.poster(film))
        TEX[f"poster_{film}_lower"], TEX[f"poster_{film}_upper"] = s(lo, Q + f"poster_{film}_lower"), s(hi, Q + f"poster_{film}_upper")


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
    west / east (the arm on that end only) or middle (no arms). Plump seat and back cushions with
    rounded crowns, rolled arms and turned feet."""
    f, c = f"#sofa_{colour}", f"#cushion_{colour}"
    arm_w, arm_e = shape in ("single", "west"), shape in ("single", "east")
    x0 = 2.6 if arm_w else 0.0
    x1 = 13.4 if arm_e else 16.0
    els = [bx((0, 1.4, 1.6), (16, 4.6, 15), f),
           # seat cushion and its crown
           bx((x0 + 0.1, 4.6, 1.3), (x1 - 0.1, 7.1, 11.0), c, faces=("north", "east", "west")),
           bx((x0 + 0.35, 7.1, 1.55), (x1 - 0.35, 7.7, 10.8), c, faces=("up", "north", "east", "west"), full=("up",)),
           # back frame with a rolled top
           bx((0, 4.6, 11.0), (16, 14.0, 15), f),
           bx((0, 14.0, 11.3), (16, 14.6, 14.7), f, faces=("up", "north", "south", "east", "west")),
           # back cushion, puffed out in the middle
           bx((x0 + 0.2, 7.1, 9.2), (x1 - 0.2, 13.6, 11.0), c, faces=("north", "east", "west", "up")),
           bx((x0 + 0.5, 7.6, 8.7), (x1 - 0.5, 12.9, 9.2), c, faces=("north", "east", "west", "up", "down"), full=("north",))]
    for on, xa, xb in ((arm_w, 0, 2.6), (arm_e, 13.4, 16)):
        if on:
            els += [bx((xa, 4.6, 1.5), (xb, 10.0, 15), f),
                    bx((xa, 10.0, 1.6), (xb, 10.9, 15), c, faces=("up", "north", "east", "west")),
                    bx((xa + 0.3, 4.9, 1.25), (xb - 0.3, 10.6, 1.5), f, faces=("north", "east", "west"))]
    for on, x in ((arm_w, 0.9), (arm_e, 13.9)):
        if on:
            for z in (2.2, 13.2):
                els += [bx((x, 0.7, z), (x + 1.2, 1.4, z + 1.2), "#walnut"),
                        bx((x + 0.2, 0, z + 0.2), (x + 1.0, 0.7, z + 1.0), "#walnut")]
    return els, {f"sofa_{colour}": t(f"sofa_{colour}"), f"cushion_{colour}": t(f"cushion_{colour}"), "walnut": t("walnut")}


def coffee_table():
    els = [bx((1, 7, 3), (15, 8, 13), "#oak"),
           bx((1.6, 6.2, 3.6), (14.4, 7, 12.4), "#oak", faces=("north", "east", "south", "west")),
           bx((2.4, 2.2, 4.4), (13.6, 2.8, 11.6), "#oak")]
    els += legs(1.6, 3.6, 14.4, 12.4, 7, 1.4, "#oak")
    return els, {"oak": t("oak")}


def tv_unit():
    els = [bx((0.2, 1.2, 3.4), (15.8, 7.4, 15), "#walnut", faces=("east", "south", "west", "down")),
           bx((0, 7.4, 3), (16, 8, 15.2), "#walnut"),
           front(0.4, 1.4, 15.6, 7.2, 3.4, "#tv_doors", "#walnut")]
    els += legs(0.8, 3.8, 15.2, 14.6, 1.2, 0.8, "#black")
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
    """A dining chair: tapered front legs, back legs running up into the back posts, side and
    cross stretchers, an upholstered pad on the seat and a back of three spindles under a top rail."""
    o = "#oak"
    els = []
    for x in (3.2, 11.8):
        els += [bx((x + 0.1, 0, 3.3), (x + 0.9, 3.6, 4.1), o), bx((x, 3.6, 3.2), (x + 1.0, 6.6, 4.2), o),
                bx((x, 0, 11.8), (x + 1.0, 6.6, 12.8), o), bx((x, 6.6, 12.0), (x + 1.0, 19.6, 13.0), o)]
    els += [bx((3.5, 2.2, 4.2), (4.1, 2.8, 11.8), o), bx((12.1, 2.2, 4.2), (12.7, 2.8, 11.8), o),
            bx((4.2, 1.6, 7.7), (11.8, 2.2, 8.3), o),
            bx((3.0, 6.6, 3.0), (13.0, 7.6, 13.0), o),
            bx((3.3, 7.6, 3.2), (12.7, 8.3, 11.8), "#sofa_green"),
            bx((3.6, 8.3, 3.5), (12.4, 8.7, 11.5), "#cushion_green", faces=("up", "north", "east", "west"), full=("up",)),
            bx((4.2, 17.4, 12.0), (11.8, 19.4, 12.9), o), bx((4.2, 10.6, 12.1), (11.8, 11.6, 12.7), o)]
    for x in (5.5, 7.6, 9.7):
        els.append(bx((x, 11.6, 12.2), (x + 0.8, 17.4, 12.6), o))
    return els, {"oak": t("oak"), "sofa_green": t("sofa_green"), "cushion_green": t("cushion_green")}


# ----------------------------------------------------------------- kitchen

def base_unit(fin, fronts, top=None, extra=(), worktop=True):
    """A floor cupboard 90 cm high: a recessed plinth, the carcass, fronts standing proud of it with
    their handles, and a worktop overhanging the front."""
    carcass = f"#carcass_{fin}"
    wt = "#worktop_dark" if fin == "white" else "#worktop_light"
    els = [bx((0.4, 0, 1.8), (15.6, 1.2, 16), "#plinth"),
           bx((0, 1.2, 1.2), (16, 13.2, 16), carcass, faces=("north", "east", "south", "west"))]
    if worktop:
        els.append(bx((0, 13.2, 0), (16, 14, 16), wt))
        if top:
            els[-1]["faces"]["up"] = {"texture": top, "uv": [0, 0, 16, 16]}
    els += list(fronts) + list(extra)
    tex = {f"carcass_{fin}": t(f"carcass_{fin}"), wt[1:]: t(wt[1:]), "plinth": t("plinth"), "chrome": t("chrome")}
    return els, tex


def kitchen_unit(fin):
    els, tex = base_unit(fin, [front(0.2, 1.4, 15.8, 13.0, 1.2, f"#door_plain_{fin}", f"#carcass_{fin}")]
                         + hbar(10.4, 14.6, 11.6, 0.8))
    tex[f"door_plain_{fin}"] = t(f"door_plain_{fin}")
    return els, tex


def kitchen_drawers(fin):
    fronts = []
    for y0, y1 in ((1.4, 4.9), (5.1, 8.9), (9.1, 13.0)):
        fronts.append(front(0.2, y0, 15.8, y1, 1.2, f"#drawer_{fin}", f"#carcass_{fin}"))
        fronts += hbar(5.4, 10.6, y1 - 1.0, 0.8)
    els, tex = base_unit(fin, fronts)
    tex[f"drawer_{fin}"] = t(f"drawer_{fin}")
    return els, tex


def kitchen_sink(fin):
    """The sink unit: a stainless bowl let into the worktop with a ribbed drainer beside it, a
    swan-neck mixer tap, and a pair of doors underneath."""
    wt = "#worktop_dark" if fin == "white" else "#worktop_light"
    ox0, oz0, ox1, oz1 = 2.2, 2.6, 9.4, 12.4
    fronts = [front(0.2, 1.4, 7.9, 13.0, 1.2, f"#door_half_{fin}", f"#carcass_{fin}"),
              front(8.1, 1.4, 15.8, 13.0, 1.2, f"#door_half_{fin}", f"#carcass_{fin}")]
    fronts += vbar(7.2, 9.6, 12.4, 0.8) + vbar(8.8, 9.6, 12.4, 0.8)
    top = rim(0, 0, 16, 16, ox0, oz0, ox1, oz1, 13.2, 14, wt)
    top += bowl(ox0, oz0, ox1, oz1, 10.6, 14, "#stainless")
    top += [bx((ox0 - 0.4, 14, oz0 - 0.4), (ox1 + 0.4, 14.12, oz0), "#stainless"),
            bx((ox0 - 0.4, 14, oz1), (ox1 + 0.4, 14.12, oz1 + 0.4), "#stainless"),
            bx((ox0 - 0.4, 14, oz0), (ox0, 14.12, oz1), "#stainless"),
            bx((ox1, 14, oz0), (ox1 + 0.4, 14.12, oz1), "#stainless"),
            bx((ox1 + 0.4, 14, oz0 - 0.4), (15.2, 14.12, oz1 + 0.4), "#stainless", uv={"up": [10, 1.5, 15, 14.5]}),
            bx((5.3, 10.61, 7.0), (6.3, 10.62, 8.0), "#black", faces=("up",))]
    for el in top[-2:-1]:
        el["faces"]["up"]["texture"] = "#sink_top"
    tap = [bx((5.2, 14.12, 12.6), (6.4, 15.0, 13.8), "#chrome"),
           bx((5.4, 15.0, 12.8), (6.2, 18.6, 13.6), "#chrome"),
           bx((5.4, 18.6, 9.8), (6.2, 19.4, 13.6), "#chrome"),
           bx((5.4, 17.4, 9.8), (6.2, 18.6, 10.6), "#chrome"),
           bx((6.2, 16.0, 12.9), (7.8, 16.5, 13.5), "#chrome")]
    els, tex = base_unit(fin, fronts, extra=top + tap, worktop=False)
    tex.update({f"door_half_{fin}": t(f"door_half_{fin}"), "stainless": t("stainless"), "sink_top": t("sink_top"),
                "black": t("black")})
    return els, tex


def kitchen_oven(fin, on):
    """Built-under oven with a ceramic hob above: a steel control strip with four knobs and a clock,
    a black glass door with a bar handle (the window glows when it's on) and the hob's rings glow red."""
    hob = "#hob_on" if on else "#hob"
    door = "#oven_door_on" if on else "#oven_door"
    fronts = [front(0.2, 10.9, 15.8, 13.0, 1.2, "#oven_fascia", "#stainless"),
              front(0.2, 1.4, 15.8, 10.7, 1.2, door, "#stainless")]
    if on:
        fronts[-1]["shade"] = False
        fronts[-1]["forge_data"] = {"block_light": 15, "sky_light": 15}
    for x in (2.0, 4.0, 12.0, 14.0):
        fronts += knob(x, 11.95, 0.8)
    fronts += hbar(2.0, 14.0, 9.6, 0.8, d=1.1)
    glass = bx((0.8, 14.0, 1.0), (15.2, 14.2, 14.4), hob, faces=("up", "north", "east", "west", "south"),
               full=("up",), emissive=on)
    els, tex = base_unit(fin, fronts, extra=[glass])
    tex.update({"oven_fascia": t("oven_fascia"), door[1:]: t(door[1:]), "stainless": t("stainless"), hob[1:]: t(hob[1:])})
    return els, tex


def kitchen_wall_unit(fin):
    carcass = f"#carcass_{fin}"
    els = [bx((0, 5, 9.4), (16, 16, 16), carcass, faces=("east", "south", "west", "up", "down")),
           front(0.2, 5.2, 7.9, 15.8, 9.4, f"#wall_door_{fin}", carcass),
           front(8.1, 5.2, 15.8, 15.8, 9.4, f"#wall_door_{fin}", carcass)]
    els += vbar(7.2, 5.8, 8.4, 9.0) + vbar(8.8, 5.8, 8.4, 9.0)
    return els, {f"carcass_{fin}": t(f"carcass_{fin}"), f"wall_door_{fin}": t(f"wall_door_{fin}"), "chrome": t("chrome")}


def fridge_freezer():
    """Fridge above, freezer below: two doors with a gap between, upright handles, a kick plate."""
    g = "#white_gloss"
    els = [bx((0.5, 0.8, 1.6), (15.5, 16, 16), g, faces=("east", "south", "west")),
           bx((0.5, 16, 1.6), (15.5, 31, 16), g, faces=("east", "south", "west", "up")),
           bx((1, 0, 2), (15, 0.8, 15.6), "#black"),
           front(0.5, 0.8, 15.5, 12.4, 1.6, g, g, d=0.5),
           bx((2.4, 25.4, 1.05), (5.4, 27.4, 1.1), "#dark_grey", faces=("north",))]
    els[3]["faces"]["north"]["uv"] = [0, 0, 16, 16]
    els += split_front(0.5, 12.6, 15.5, 31, 1.6, g, g, [0, 0, 16, 16], d=0.5)
    els += vbar(13.4, 8.0, 11.8, 1.1, d=0.9) + vbar(13.4, 13.4, 20.4, 1.1, d=0.9)
    return els, {"white_gloss": t("white_gloss"), "black": t("black"), "chrome": t("chrome"), "dark_grey": t("dark_grey")}


def washing_machine(on):
    face = "#washer_on" if on else "#washer"
    els = [bx((0.5, 0, 1.2), (15.5, 14, 16), "#white_gloss", front=face, full=("north",))]
    if on:
        els.append(bx((2, 1, 0.98), (14, 12, 1), face, faces=("north",), emissive=False, uv={"north": [2, 3, 14, 15]}))
    els += ring(8.0, 5.7, 4.7, 1.2) + knob(12.6, 12.7, 1.2, r=0.9, d=0.7, tex="#white_gloss")
    els += [bx((1.4, 11.6, 0.8), (5.6, 13.3, 1.2), "#white_gloss"), bx((2.0, 12.0, 0.75), (5.0, 12.5, 0.8), "#dark_grey",
                                                                        faces=("north",))]
    return els, {"white_gloss": t("white_gloss"), face[1:]: t(face[1:]), "chrome": t("chrome"), "dark_grey": t("dark_grey")}


def ring(cx, cy, r, zf, tex="#chrome", w=0.8, d=0.4):
    """An eight-sided ring standing proud of a front at z = zf (the washing machine's door)."""
    side = 2 * r * math.tan(math.radians(22.5)) + w * 0.4
    els = [bx((cx - side / 2, cy + r - w / 2, zf - d), (cx + side / 2, cy + r + w / 2, zf), tex),
           bx((cx - side / 2, cy - r - w / 2, zf - d), (cx + side / 2, cy - r + w / 2, zf), tex),
           bx((cx - r - w / 2, cy - side / 2, zf - d), (cx - r + w / 2, cy + side / 2, zf), tex),
           bx((cx + r - w / 2, cy - side / 2, zf - d), (cx + r + w / 2, cy + side / 2, zf), tex)]
    q = r / math.sqrt(2)
    for sx, sy in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
        px, py = cx + sx * q, cy + sy * q
        els.append(bx((px - side / 2, py - w / 2, zf - d), (px + side / 2, py + w / 2, zf), tex,
                      rot=("z", -45 if sx == sy else 45, (px, py, zf - d / 2))))
    return els


def microwave(on):
    door = "#microwave_door_on" if on else "#microwave_door"
    panel = "#microwave_panel_on" if on else "#microwave_panel"
    els = [bx((3, 0.3, 6.3), (13, 5.6, 13.5), "#black", faces=("east", "south", "west", "up", "down")),
           front(3.1, 0.4, 10.3, 5.5, 6.3, door, "#black", d=0.3),
           front(10.4, 0.4, 12.9, 5.5, 6.3, panel, "#black", d=0.15)]
    if on:
        for el in els[1:3]:
            el["shade"] = False
            el["forge_data"] = {"block_light": 15, "sky_light": 15}
    els += vbar(9.8, 1.0, 4.9, 6.0, tex="#dark_grey", d=0.6, t=0.4)
    els += [bx((x, 0, z), (x + 0.6, 0.3, z + 0.6), "#dark_grey") for x in (3.4, 12.0) for z in (6.6, 12.6)]
    return els, {"black": t("black"), door[1:]: t(door[1:]), panel[1:]: t(panel[1:]), "dark_grey": t("dark_grey")}


def kettle(on):
    """A jug kettle: round base, round steel body and lid, a handle on one side, the spout on the other."""
    els = moved(G.round_column(2.6, 0, 0.6, "#black", top=True), -0.5, 0.5)
    els += moved(G.round_column(2.1, 0.6, 5.6, "#stainless", top=True), -0.5, 0.5)
    els += moved(G.round_column(1.3, 5.6, 6.2, "#black", top=True), -0.5, 0.5)
    els = [json.loads(json.dumps(e)) for e in els]
    els += [bx((9.8, 1.6, 8.1), (10.6, 5.4, 8.9), "#black"), bx((9.2, 4.8, 8.1), (10.6, 5.4, 8.9), "#black"),
            bx((9.2, 1.6, 8.1), (10.6, 2.2, 8.9), "#black"),
            bx((4.3, 3.6, 8.1), (5.6, 4.6, 8.9), "#stainless"),
            bx((9.75, 2.6, 8.2), (9.85, 3.2, 8.8), "#bulb" if on else "#black", emissive=on)]
    return els, {"black": t("black"), "stainless": t("stainless"), "bulb": t("bulb")}


def toaster(on):
    els = [bx((4.0, 0, 5.5), (12.0, 0.4, 10.5), "#black"),
           bx((4.3, 0.4, 5.8), (11.7, 4.4, 10.2), "#stainless"),
           bx((4.6, 4.4, 6.1), (11.4, 4.9, 9.9), "#stainless"),
           bx((12, 1.4 if on else 3, 7.5), (12.6, 2.2 if on else 3.8, 8.5), "#black"),
           bx((3.6, 1.4, 7.6), (4.3, 2.2, 8.4), "#black")]
    els[2]["faces"]["up"] = {"texture": "#toaster_top", "uv": [0, 0, 16, 16]}
    return els, {"stainless": t("stainless"), "black": t("black"), "toaster_top": t("toaster_top")}


# ----------------------------------------------------------------- bathroom

def toilet():
    """A close-coupled toilet: foot, pedestal and a rounded bowl, a white seat with the lid down,
    the cistern behind with its lid and a dual flush button."""
    c = "#ceramic"
    els = [bx((5.0, 0, 4.6), (11.0, 0.6, 11.0), c), bx((5.6, 0.6, 5.2), (10.4, 4.2, 10.8), c),
           bx((4.6, 4.2, 2.8), (11.4, 5.8, 11.4), c), bx((4.0, 5.8, 1.8), (12.0, 7.0, 11.6), c),
           bx((4.0, 7.0, 1.6), (12.0, 7.5, 11.4), "#white_gloss"),
           bx((4.1, 7.5, 1.7), (11.9, 8.0, 11.3), "#white_gloss"),
           bx((5.2, 7.0, 11.4), (6.2, 7.8, 12.0), "#chrome"), bx((9.8, 7.0, 11.4), (10.8, 7.8, 12.0), "#chrome"),
           bx((3.4, 6.4, 11.8), (12.6, 14.6, 15.2), c), bx((3.2, 14.6, 11.6), (12.8, 15.2, 15.4), c),
           bx((6.9, 15.2, 13.0), (7.9, 15.4, 14.0), "#chrome"), bx((8.1, 15.2, 13.0), (9.1, 15.4, 14.0), "#chrome")]
    return els, {"ceramic": t("ceramic"), "chrome": t("chrome"), "white_gloss": t("white_gloss")}


def basin():
    """A pedestal basin: a flared pedestal, the basin with a real bowl in it, a plug and a mixer tap."""
    c = "#ceramic"
    els = [bx((6.0, 0, 9.0), (10.0, 0.6, 13.4), c), bx((6.4, 0.6, 9.4), (9.6, 6.0, 13.0), c),
           bx((6.0, 6.0, 9.0), (10.0, 10.2, 13.4), c), bx((3.0, 10.2, 6.0), (13.0, 11.6, 14.6), c)]
    els += rim(2, 5, 14, 15, 4, 6.6, 12, 12.8, 11.6, 13.5, c)
    els += bowl(4, 6.6, 12, 12.8, 11.8, 13.5, c)
    els += [bx((7.6, 11.81, 9.2), (8.4, 11.82, 10.0), "#chrome", faces=("up",)),
            bx((7.4, 13.5, 13.4), (8.6, 15.4, 14.4), "#chrome"), bx((7.5, 14.8, 11.0), (8.5, 15.6, 14.0), "#chrome"),
            bx((7.7, 15.6, 13.0), (8.3, 16.2, 14.6), "#chrome")]
    return els, {"ceramic": t("ceramic"), "chrome": t("chrome")}


def bath(part, on=False):
    """Half of a fitted bath (two blocks long): a panel along the front, a rolled rim, a real tub
    inside with a sloped back at the far end; the left half has the deck mixer, the overflow and
    the plug. Full of water when the taps have been run (on)."""
    left = part == "left"
    c = "#ceramic"
    ox0, ox1 = (1.2, 16) if left else (0, 14.6)
    els = [bx((0, 0, 4.5), (16, 8.2, 5.1), "#white_gloss", faces=("north", "down")),
           bx((0, 0, 15.2), (16, 8.2, 16), c, faces=("south", "down")),
           bx((0, 0, 5.9), (16, 1.8, 15.2), c, faces=("down",))]
    els += [e for e in rim(0, 4.5, 16, 16, ox0, 5.9, ox1, 14.4, 8.2, 9.0, c)
            if e["to"][0] - e["from"][0] > 0.01 and e["to"][2] - e["from"][2] > 0.01]
    inside = bowl(ox0, 5.9, ox1, 14.4, 2.0, 8.2, c)
    els += inside[:3] + ([inside[3]] if left else [inside[4]])
    els.append(bx((0 if left else 15.2, 0, 5.1), (0.8 if left else 16, 8.2, 15.2), c, faces=("west",) if left else ("east",)))
    if left:
        els += [bx((ox0, 6.4, 9.6), (ox0 + 0.05, 7.2, 10.4), "#chrome", faces=("east",)),
                bx((2.4, 2.01, 9.6), (3.2, 2.02, 10.4), "#chrome", faces=("up",)),
                bx((3.4, 9.0, 14.6), (4.4, 11.0, 15.4), "#chrome"), bx((3.5, 10.6, 12.2), (4.3, 11.4, 15.0), "#chrome"),
                bx((2.0, 9.0, 14.7), (2.8, 10.0, 15.3), "#chrome"), bx((5.0, 9.0, 14.7), (5.8, 10.0, 15.3), "#chrome"),
                bx((1.7, 10.0, 14.6), (3.1, 10.3, 15.4), "#chrome"), bx((4.7, 10.0, 14.6), (6.1, 10.3, 15.4), "#chrome")]
    else:
        els.append(bx((13.2, 2.0, 6.0), (13.6, 8.6, 14.3), c, rot=("z", 22.5, (13.4, 2.0, 10.0))))
    tex = {"white_gloss": t("white_gloss"), "ceramic": t("ceramic"), "chrome": t("chrome")}
    if on:
        els.append(bx((ox0, 6.4, 5.9), (ox1, 6.5, 14.4), "#water", faces=("up",)))
        tex["water"] = t("water")
    return els, tex


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
    els = [bx((2, 4, 12), (14, 15, 16), "#white_gloss", faces=("east", "south", "west", "up", "down")),
           front(2.1, 4.1, 13.9, 14.9, 12, "#mirror", "#white_gloss", d=0.5),
           bx((2.6, 3.4, 11.2), (13.4, 4.0, 12.0), "#white_gloss")]
    return els, {"white_gloss": t("white_gloss"), "mirror": t("mirror")}


# ----------------------------------------------------------------- bedroom and study

def wardrobe(fin):
    carcass = f"#carcass_{fin}"
    doors = split_front(8.05, 1.2, 15.85, 30.4, 2.4, f"#wardrobe_{fin}", carcass, [0, 0, 8, 16]) \
        + split_front(0.15, 1.2, 7.95, 30.4, 2.4, f"#wardrobe_{fin}", carcass, [8, 0, 16, 16])
    els = [bx((0.4, 0, 2.8), (15.6, 1, 15.6), "#plinth"),
           bx((0, 1, 2.4), (16, 16, 16), carcass, faces=("east", "south", "west")),
           bx((0, 16, 2.4), (16, 30.6, 16), carcass, faces=("east", "south", "west")),
           bx((-0.3, 30.6, 1.6), (16.3, 31.4, 16), carcass), bx((0, 31.4, 2.0), (16, 32, 16), carcass)] + doors
    els += vbar(7.3, 13.2, 19.6, 2.0) + vbar(8.7, 13.2, 19.6, 2.0)
    return els, {"plinth": t("plinth"), f"carcass_{fin}": t(f"carcass_{fin}"), f"wardrobe_{fin}": t(f"wardrobe_{fin}"),
                 "chrome": t("chrome")}


def chest_of_drawers(fin):
    """Two small drawers over three big ones, each with its own handle, under an overhanging top."""
    carcass = f"#carcass_{fin}"
    els = [bx((0.5, 0, 2.8), (15.5, 1, 15.5), "#plinth"),
           bx((0, 1, 2.4), (16, 13.2, 16), carcass, faces=("east", "south", "west")),
           bx((0, 13.2, 1.6), (16, 14, 16), carcass)]
    for y0, y1 in ((1.2, 4.2), (4.4, 7.4), (7.6, 10.4)):
        els.append(front(0.2, y0, 15.8, y1, 2.4, f"#drawer_{fin}", carcass))
        els += hbar(5.8, 10.2, (y0 + y1) / 2 + 0.4, 2.0, d=0.8)
    els += [front(8.1, 10.6, 15.8, 13.0, 2.4, f"#drawer_half_{fin}", carcass),
            front(0.2, 10.6, 7.9, 13.0, 2.4, f"#drawer_half_{fin}", carcass)]
    els += knob(4.0, 11.8, 2.0) + knob(12.0, 11.8, 2.0)
    return els, {"plinth": t("plinth"), f"carcass_{fin}": t(f"carcass_{fin}"), f"drawer_{fin}": t(f"drawer_{fin}"),
                 f"drawer_half_{fin}": t(f"drawer_half_{fin}"), "chrome": t("chrome")}


def bedside_table(fin):
    carcass = f"#carcass_{fin}"
    els = [bx((1.6, 0.8, 2.9), (14.4, 7.4, 15.4), carcass, faces=("east", "south", "west", "down")),
           bx((1.4, 7.4, 2.3), (14.6, 8, 15.5), carcass),
           front(1.7, 1.0, 14.3, 4.0, 2.9, f"#drawer_{fin}", carcass),
           front(1.7, 4.2, 14.3, 7.2, 2.9, f"#drawer_{fin}", carcass)]
    els += knob(8, 2.5, 2.5) + knob(8, 5.7, 2.5)
    els += legs(1.8, 3.0, 14.2, 15.2, 0.8, 0.8, carcass)
    return els, {f"carcass_{fin}": t(f"carcass_{fin}"), f"drawer_{fin}": t(f"drawer_{fin}"), "chrome": t("chrome")}


def desk():
    els = [bx((0, 11, 1), (16, 12, 15), "#oak"),
           bx((10.4, 0.3, 1.8), (15.6, 11, 14.4), "#oak", faces=("east", "south", "west", "down")),
           bx((0.4, 0, 1.6), (1.6, 11, 14.4), "#oak"),
           bx((1.6, 4, 13.6), (10.4, 11, 14.4), "#oak")]
    for y0, y1 in ((0.5, 3.8), (4.0, 7.3), (7.5, 10.8)):
        els.append(front(10.6, y0, 15.4, y1, 1.8, "#drawer_half_oak", "#oak"))
        els += hbar(11.8, 14.2, y1 - 0.9, 1.4, d=0.7)
    return els, {"oak": t("oak"), "drawer_half_oak": t("drawer_half_oak"), "chrome": t("chrome")}


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


# ----------------------------------------------------------------- supermarket

def _row(r):
    """UV of goods row r (0 or 1) on a goods texture; the price strip is below them."""
    return [0, r * 6, 16, r * 6 + 6]


def _flip(uv):
    return [uv[2], uv[1], uv[0], uv[3]]


def shelving(kind):
    """A run of gondola shelving, two blocks high and stocked on both sides: four shelves of goods
    each side of a pegboard spine, a price strip along every shelf edge."""
    a, b = f"#goods_{kind}_a", f"#goods_{kind}_b"
    levels = (1.8, 8.6, 15.4, 22.2)
    front = ((a, 1), (b, 1), (b, 0), (a, 0))          # bottom shelf to top
    back = ((b, 1), (a, 1), (a, 0), (b, 0))
    els = [bx((0, 0, 1), (16, 1.8, 15), "#plinth"),
           bx((0, 0, 7.3), (16, 29, 8.7), "#pegboard", uv={"north": [0, 0, 16, 16], "south": [0, 0, 16, 16]}),
           bx((0, 28.8, 6.6), (16, 29.6, 9.4), "#white_gloss")]
    for y, (tf, rf), (tb, rb) in zip(levels, front, back):
        if y > 2:
            els.append(bx((0, y - 0.5, 1.4), (16, y, 14.6), "#white_gloss", faces=("up", "down")))
        for z0, z1, lip, side in ((1.6, 7.3, 1.0, "north"), (8.7, 14.4, 14.6, "south")):
            tex, r = (tf, rf) if side == "north" else (tb, rb)
            row = _row(r)
            mid = [0, row[1] + 3, 16, row[1] + 3.5]
            els.append(bx((0.1, y, z0), (15.9, y + 6, z1), tex, faces=(side, "east", "west", "up"),
                          uv={side: row if side == "north" else _flip(row), "east": [0, row[1], 6, row[3]],
                              "west": [10, row[1], 16, row[3]], "up": mid}))
            if y > 2:
                lz0, lz1 = (lip, lip + 0.4) if side == "north" else (lip - 0.4, lip)
                els.append(bx((0, y - 1.4, lz0), (16, y + 0.3, lz1), tf, faces=(side, "up", "down", "east", "west"),
                              uv={side: [0, 12, 16, 16], "up": [0, 15, 16, 16], "down": [0, 15, 16, 16],
                                  "east": [0, 12, 1, 16], "west": [0, 12, 1, 16]}))
    tex = {"plinth": t("plinth"), "pegboard": t("pegboard"), "white_gloss": t("white_gloss"),
           f"goods_{kind}_a": t(f"goods_{kind}_a"), f"goods_{kind}_b": t(f"goods_{kind}_b")}
    return els, tex


def chiller():
    """An open-fronted multideck chiller: stepped shelves of milk, yoghurts, meat and ready meals,
    lit from the canopy, a grille in the base."""
    a, b = "#goods_chilled_a", "#goods_chilled_b"
    els = [bx((0, 0, 3), (16, 3, 16), "#plinth"),
           bx((0, 3, 14.6), (16, 26, 16), "#chiller_back", faces=("north",), emissive=True,
              uv={"north": [0, 0, 16, 16]}),
           bx((0, 3, 14.6), (16, 26, 16), "#white_gloss", faces=("east", "west", "south")),
           bx((0, 26, 1.6), (16, 31, 16), "#white_gloss", front="#promo_green", faces=("north", "east", "west", "south", "up"),
              uv={"north": [0, 4, 16, 14]}),
           bx((0.5, 25.98, 1.8), (15.5, 26, 14.4), "#chiller_back", faces=("down",), emissive=True),
           bx((0, 3, 3), (0.4, 26, 16), "#white_gloss"), bx((15.6, 3, 3), (16, 26, 16), "#white_gloss"),
           bx((0.4, 3, 3), (15.6, 5.2, 3.4), "#white_gloss", front="#promo_green", uv={"north": [0, 9, 16, 12]})]
    shelves = ((3, 3.4, b, 0), (9.4, 6.4, b, 1), (15.4, 8.6, a, 1), (21.2, 10.8, a, 0))
    for y, z0, tex, r in shelves:
        row = _row(r)
        if y > 4:
            els.append(bx((0.4, y - 0.5, z0), (15.6, y, 14.6), "#white_gloss", faces=("up", "down", "north")))
            els.append(bx((0.4, y - 1.2, z0 - 0.4), (15.6, y + 0.3, z0), a, faces=("north", "up", "down"),
                          uv={"north": [0, 12, 16, 16]}))
        els.append(bx((0.4, y, z0 + 0.3), (15.6, y + 5, 14.5), tex, faces=("north", "up"),
                      uv={"north": [0, row[1] + 0.5, 16, row[3]], "up": [0, row[1] + 3, 16, row[1] + 3.5]}))
    tex = {"plinth": t("plinth"), "chiller_back": t("chiller_back"), "white_gloss": t("white_gloss"),
           "promo_green": t("promo_green"), "goods_chilled_a": t("goods_chilled_a"), "goods_chilled_b": t("goods_chilled_b")}
    return els, tex


def upright_freezer():
    els = [bx((0, 0, 1.5), (16, 16, 16), "#white_gloss", faces=("east", "west", "south", "down")),
           bx((0, 16, 1.5), (16, 31, 16), "#white_gloss", faces=("east", "west", "south", "up")),
           bx((0, 0, 1.5), (16, 16, 1.6), "#freezer_lower", faces=("north",), full=("north",), emissive=True),
           bx((0, 16, 1.5), (16, 31, 1.6), "#freezer_upper", faces=("north",), emissive=True,
              uv={"north": [0, 0, 16, 15]}),
           bx((0, 31, 1.2), (16, 32, 16), "#white_gloss", front="#promo_blue", uv={"north": [0, 8, 16, 16]})]
    return els, {"white_gloss": t("white_gloss"), "freezer_lower": t("freezer_lower"),
                 "freezer_upper": t("freezer_upper"), "promo_blue": t("promo_blue")}


def freezer_island():
    els = [bx((0, 0, 1), (16, 13, 15), "#promo_blue", faces=("north", "south"), uv={"north": [0, 0, 16, 16], "south": [0, 0, 16, 16]}),
           bx((0, 0, 1), (16, 13, 15), "#white_gloss", faces=("east", "west", "down")),
           bx((0, 12.6, 1), (16, 13.4, 15), "#white_gloss", faces=("up",)),
           bx((0.6, 13.4, 1.6), (15.4, 13.5, 14.4), "#frozen_lid", faces=("up",), full=("up",)),
           bx((-0.01, 3, 0.6), (16.01, 4, 15.4), "#plastic_grey", faces=("north", "south", "up"))]
    els[2]["faces"]["up"] = {"texture": "#white_gloss", "uv": [0, 0, 16, 16]}
    return els, {"promo_blue": t("promo_blue"), "white_gloss": t("white_gloss"), "frozen_lid": t("frozen_lid"),
                 "plastic_grey": t("plastic_grey")}


def produce_stand():
    """Fruit and veg in tilted wooden crates, two tiers."""
    els = [bx((0, 0, 1), (16, 7, 15), "#crate", uv={"north": [0, 0, 16, 7]}),
           bx((0, 7, 1), (16, 9, 8), "#crate", faces=("north", "east", "west", "up")),
           bx((0, 9, 8), (16, 12, 15), "#crate", faces=("north", "east", "west", "up", "south"))]
    els[1]["faces"]["up"] = {"texture": "#produce", "uv": [0, 0, 16, 8]}
    els[2]["faces"]["up"] = {"texture": "#produce", "uv": [0, 8, 16, 16]}
    return els, {"crate": t("crate"), "produce": t("produce")}


def checkout(part):
    """A staffed checkout, two blocks long: the belt on the left, the scanner, till and card reader
    on the right, and the lane light."""
    els = [bx((0, 0, 2), (16, 11, 14), "#white_gloss", front="#promo_green", uv={"north": [0, 4, 16, 15]}),
           bx((0, 0, 2.3), (16, 1, 13.7), "#plinth")]
    tex = {"white_gloss": t("white_gloss"), "promo_green": t("promo_green"), "plinth": t("plinth")}
    if part == "left":
        els += [bx((0, 11, 3), (16, 11.6, 13), "#belt", faces=("up",), full=("up",)),
                bx((0, 11, 2), (16, 12.2, 3), "#stainless"), bx((0, 11, 13), (16, 12.2, 14), "#stainless"),
                bx((4, 11.6, 4), (4.8, 12.6, 12), "#plastic_grey")]
        tex.update({"belt": t("belt"), "stainless": t("stainless"), "plastic_grey": t("plastic_grey")})
    else:
        els += [bx((0, 11, 2), (16, 11.6, 14), "#worktop_light"),
                bx((1.5, 11.6, 4), (9, 11.7, 12), "#scanner", faces=("up",), full=("up",)),
                bx((11, 11.6, 12), (12, 17, 13), "#black"),
                bx((8.5, 16, 12.2), (14.5, 21, 13.2), "#black", faces=("east", "west", "up", "down")),
                bx((8.5, 16, 13.2), (14.5, 21, 13.21), "#till_screen", faces=("south",), full=("south",), emissive=True),
                bx((8.5, 16, 12.19), (14.5, 21, 12.2), "#kiosk_screen", faces=("north",), full=("north",), emissive=True),
                bx((12, 11.6, 3), (14.5, 13.8, 5.2), "#black"),
                bx((12.1, 13.8, 3.1), (14.4, 13.9, 5.1), "#card_reader", faces=("up",), full=("up",)),
                bx((15, 11.6, 13), (15.7, 26, 13.7), "#black"),
                bx((13.6, 26, 12.4), (16, 28.4, 14.3), "#lane_light", full=("north", "south", "east", "west"),
                   emissive=True)]
        tex.update({"worktop_light": t("worktop_light"), "scanner": t("scanner"), "black": t("black"),
                    "till_screen": t("till_screen"), "kiosk_screen": t("kiosk_screen"),
                    "card_reader": t("card_reader"), "lane_light": t("lane_light")})
    return els, tex


def self_checkout():
    els = [bx((3, 0, 6), (13, 11, 14), "#white_gloss", front="#promo_green", uv={"north": [0, 4, 16, 15]}),
           bx((2, 11, 4), (14, 12, 14), "#plastic_grey"),
           bx((3, 12, 5), (10, 12.1, 10), "#scanner", faces=("up",), full=("up",)),
           bx((7.4, 12, 12), (8.6, 18, 13), "#black"),
           bx((3, 16, 11), (13, 23, 12), "#black", faces=("east", "west", "up", "down", "south"),
              rot=("x", 22.5, (8, 19.5, 11.5))),
           bx((3.3, 16.3, 10.98), (12.7, 22.7, 11), "#kiosk_screen", faces=("north",), full=("north",), emissive=True,
              rot=("x", 22.5, (8, 19.5, 11.5))),
           bx((11, 12, 5), (13.5, 13.6, 7.5), "#black"),
           bx((11.1, 13.6, 5.1), (13.4, 13.7, 7.4), "#card_reader", faces=("up",), full=("up",)),
           bx((3, 12, 13), (3.8, 26, 13.8), "#black"),
           bx((2.4, 26, 12.4), (4.4, 28, 14.4), "#lane_light", full=("north", "south", "east", "west"), emissive=True),
           bx((13, 8, 4.5), (16, 9, 14), "#plastic_grey")]
    return els, {"white_gloss": t("white_gloss"), "promo_green": t("promo_green"), "plastic_grey": t("plastic_grey"),
                 "scanner": t("scanner"), "black": t("black"), "kiosk_screen": t("kiosk_screen"),
                 "card_reader": t("card_reader"), "lane_light": t("lane_light")}


def trolley():
    """A shopping trolley: wire basket on a wheeled chassis, a blue handle."""
    m = "#mesh"
    els = []
    for x, z in ((4.3, 2), (10.5, 2), (4.3, 12.6), (10.5, 12.6)):
        els.append(bx((x, 0, z), (x + 1.2, 1.4, z + 1.2), "#black"))
    els += [bx((4.5, 1.4, 2.2), (5.1, 2, 13.8), "#plastic_grey"), bx((10.9, 1.4, 2.2), (11.5, 2, 13.8), "#plastic_grey"),
            bx((5.1, 2.2, 2.6), (10.9, 2.25, 13.2), m, faces=("up", "down")),
            bx((4.5, 2, 13), (5.1, 6, 13.6), "#plastic_grey"), bx((10.9, 2, 13), (11.5, 6, 13.6), "#plastic_grey"),
            bx((4.5, 2, 2.4), (5.1, 6, 3), "#plastic_grey"), bx((10.9, 2, 2.4), (11.5, 6, 3), "#plastic_grey"),
            # the basket: thin walls with both faces so the far side shows through the near one
            bx((4, 6, 2), (12, 14, 2.05), m, faces=("north", "south")),
            bx((4, 6, 13), (12, 14, 13.05), m, faces=("north", "south")),
            bx((4, 6, 2), (4.05, 14, 13), m, faces=("east", "west")),
            bx((11.95, 6, 2), (12, 14, 13), m, faces=("east", "west")),
            bx((4, 6, 2), (12, 6.05, 13), m, faces=("up", "down")),
            bx((3.9, 14, 1.9), (12.1, 14.4, 2.3), "#plastic_grey"), bx((3.9, 14, 12.8), (12.1, 14.4, 13.2), "#plastic_grey"),
            bx((3.9, 14, 2.3), (4.3, 14.4, 12.8), "#plastic_grey"), bx((11.7, 14, 2.3), (12.1, 14.4, 12.8), "#plastic_grey"),
            bx((4.2, 13, 13.2), (4.8, 15, 14.2), "#plastic_grey"), bx((11.2, 13, 13.2), (11.8, 15, 14.2), "#plastic_grey"),
            bx((3.6, 14.6, 13.8), (12.4, 15.6, 14.8), "#plastic_blue")]
    return els, {"black": t("black"), "plastic_grey": t("plastic_grey"), "mesh": t("mesh"), "plastic_blue": t("plastic_blue")}


def pallet_racking():
    """Warehouse racking: blue uprights, orange beams, two pallets of boxes."""
    els = []
    for x in (0, 15):
        for z in (1, 14):
            els.append(bx((x, 0, z), (x + 1, 32, z + 1), "#rack_blue", uv={"north": [0, 0, 1, 16], "south": [0, 0, 1, 16],
                                                                             "east": [0, 0, 1, 16], "west": [0, 0, 1, 16]}))
    for y in (1, 15.4, 29.6):
        for z in (1, 14):
            els.append(bx((1, y, z), (15, y + 1.6, z + 1), "#rack_orange"))
    for y in (2.6, 17):
        els += [bx((1.2, y, 2), (14.8, y + 1.2, 14), "#pallet"),
                bx((1.6, y + 1.2, 2.4), (14.4, y + 10.8, 13.6), "#boxes", full=("north", "south", "east", "west"))]
    return els, {"rack_blue": t("rack_blue"), "rack_orange": t("rack_orange"), "pallet": t("pallet"), "boxes": t("boxes")}


def cash_machine():
    """A cash machine set into a wall: the block is the wall, the fascia faces out."""
    els = [bx((0, 0, 1), (16, 16, 16), "#plastic_grey"),
           bx((0, 0, 0.99), (16, 16, 1), "#atm", faces=("north",), full=("north",)),
           bx((4, 7.4, 0.97), (12, 11.6, 0.98), "#atm", faces=("north",), emissive=True, uv={"north": [3.5, 3.5, 12, 8.5]}),
           bx((1, 14.4, 0), (15, 15.2, 1), "#plastic_grey"),
           bx((3, 4.4, 0), (13, 5, 1), "#plastic_grey")]
    return els, {"plastic_grey": t("plastic_grey"), "atm": t("atm")}


# ----------------------------------------------------------------- cinema

def cinema_seat(colour):
    v = f"#velvet_{colour}"
    els = [bx((3, 0, 4), (13, 3, 13), "#black"),
           bx((2, 3, 1.5), (14, 6.5, 12.5), v),
           bx((2, 3, 12.5), (14, 20, 15.5), v),
           bx((2.3, 20, 13), (13.7, 20.8, 15.3), v)]
    for x in (0, 14):
        els += [bx((x, 0, 2), (x + 2, 10, 14), "#dark_grey"),
                bx((x + 0.3, 10, 2.4), (x + 1.7, 10.4, 4), "#black")]
    return els, {"black": t("black"), "dark_grey": t("dark_grey"), v[1:]: t(v[1:])}


def popcorn_machine(on):
    pop = "#popcorn"
    els = [bx((3, 0, 4), (13, 2, 12), "#plastic_red")]
    for x, z in ((3, 4), (12.4, 4), (3, 11.4), (12.4, 11.4)):
        els.append(bx((x, 2, z), (x + 0.6, 13, z + 0.6), "#plastic_red"))
    els += [bx((3.6, 2, 4.6), (12.4, 5.5, 11.4), pop, faces=("north", "east", "west", "south", "up"), emissive=on),
            bx((6.5, 9, 6.5), (9.5, 11, 9.5), "#stainless"),
            bx((7.7, 11, 7.7), (8.3, 13, 8.3), "#stainless"),
            bx((7.2, 12.2, 7.2), (8.8, 12.8, 8.8), "#bulb" if on else "#bulb_off", emissive=on),
            bx((2.8, 13, 3.8), (13.2, 14.6, 12.2), "#plastic_red"),
            bx((3.6, 14.6, 4.6), (12.4, 15.2, 11.4), "#plastic_yellow"),
            bx((3.3, 2, 4.3), (12.7, 13, 11.7), "#glass", faces=("north", "east", "west", "south"))]
    return els, {"plastic_red": t("plastic_red"), "popcorn": t("popcorn"), "stainless": t("stainless"),
                 "bulb" if on else "bulb_off": t("bulb" if on else "bulb_off"), "plastic_yellow": t("plastic_yellow"),
                 "glass": t("glass")}


def pick_and_mix():
    els = [bx((0, 0, 4), (16, 9, 16), "#white_gloss", front="#promo_pink", uv={"north": [0, 2, 16, 16]}),
           bx((0.2, 9, 3.6), (15.8, 10.6, 9.8), "#white_gloss", faces=("north", "east", "west", "up")),
           bx((0.2, 9, 9.8), (15.8, 12.6, 16), "#white_gloss", faces=("north", "east", "west", "up", "south"))]
    els[1]["faces"]["up"] = {"texture": "#sweets", "uv": [0, 0, 16, 8]}
    els[2]["faces"]["up"] = {"texture": "#sweets", "uv": [0, 8, 16, 16]}
    return els, {"white_gloss": t("white_gloss"), "promo_pink": t("promo_pink"), "sweets": t("sweets")}


def drinks_dispenser():
    els = [bx((2, 1, 5), (14, 14, 14), "#stainless"),
           bx((2.4, 7, 4.98), (13.6, 13.4, 5), "#drinks_panel", faces=("north",), full=("north",), emissive=True),
           bx((2.5, 0, 2.6), (13.5, 1, 14), "#plastic_grey")]
    for x in (3.4, 6.2, 9, 11.8):
        els.append(bx((x, 5, 4.2), (x + 0.8, 6.4, 5), "#black"))
    return els, {"stainless": t("stainless"), "drinks_panel": t("drinks_panel"), "plastic_grey": t("plastic_grey"),
                 "black": t("black")}


def film_poster(film):
    """A lit poster case on the wall, two blocks high."""
    lo, hi = f"#poster_{film}_lower", f"#poster_{film}_upper"
    els = [bx((0.5, 0.5, 15), (15.5, 16, 16), "#black"), bx((0.5, 16, 15), (15.5, 31.5, 16), "#black"),
           bx((1, 1, 14.9), (15, 16, 15), lo, faces=("north",), emissive=True, uv={"north": [1, 0, 15, 15]}),
           bx((1, 16, 14.9), (15, 31, 15), hi, faces=("north",), emissive=True, uv={"north": [1, 1, 15, 16]}),
           bx((0.5, 0.5, 14.6), (15.5, 1, 15), "#chrome"), bx((0.5, 31, 14.6), (15.5, 31.5, 15), "#chrome"),
           bx((0.5, 1, 14.6), (1, 16, 15), "#chrome"), bx((0.5, 16, 14.6), (1, 31, 15), "#chrome"),
           bx((15, 1, 14.6), (15.5, 16, 15), "#chrome"), bx((15, 16, 14.6), (15.5, 31, 15), "#chrome")]
    return els, {"black": t("black"), lo[1:]: t(lo[1:]), hi[1:]: t(hi[1:]), "chrome": t("chrome")}


# ----------------------------------------------------------------- workshop, fast food, garden centre

def tool_chest():
    els = [bx((0.5, 1.4, 2), (15.5, 14, 15), "#plastic_red", front="#toolchest_front", full=("north",)),
           bx((0.3, 14, 1.8), (15.7, 14.6, 15.2), "#black"),
           bx((15.5, 9, 4), (16.2, 10, 13), "#chrome")]
    for x, z in ((1, 2.6), (13.4, 2.6), (1, 12.8), (13.4, 12.8)):
        els.append(bx((x, 0, z), (x + 1.6, 1.4, z + 1.6), "#black"))
    return els, {"plastic_red": t("plastic_red"), "toolchest_front": t("toolchest_front"), "black": t("black"),
                 "chrome": t("chrome")}


def _disc(y, r, tex, face="up"):
    """A flat round end (a square face whose texture is clear outside the circle)."""
    return bx((8 - r, y - 0.01, 8 - r), (8 + r, y, 8 + r), tex, faces=(face,), full=(face,))


def tyre_stack():
    els = []
    for y in (0, 4.6, 9.2):
        els += [json.loads(json.dumps(e)) for e in G.round_column(6.5, y, y + 4.5, "#tyre_tread")]
    els.append(_disc(13.71, 6.6, "#tyre_side"))
    return els, {"tyre_tread": t("tyre_tread"), "tyre_side": t("tyre_side")}


def oil_drum():
    els = [json.loads(json.dumps(e)) for e in G.round_column(6.2, 0, 14.5, "#drum_side", bottom=True)]
    els.append(_disc(14.5, 6.3, "#drum_top"))
    return els, {"drum_side": t("drum_side"), "drum_top": t("drum_top")}


def car_lift():
    """One post of a two-post car lift: its arms reach out under the car."""
    els = [bx((2, 0, 2), (14, 0.6, 14), "#plastic_grey"),
           bx((5, 0.6, 6), (11, 30, 12), "#plastic_yellow", uv={"north": [0, 0, 6, 16], "south": [0, 0, 6, 16],
                                                                "east": [0, 0, 6, 16], "west": [0, 0, 6, 16]}),
           bx((4.5, 30, 5.5), (11.5, 31, 12.5), "#plastic_grey"),
           bx((4.6, 6, 4.6), (11.4, 9.4, 6), "#plastic_grey"),
           bx((5, 6.6, -9), (6.6, 7.8, 4.6), "#dark_grey"), bx((9.4, 6.6, -9), (11, 7.8, 4.6), "#dark_grey"),
           bx((4.6, 7.8, -9), (7, 8.6, -6.6), "#black"), bx((9, 7.8, -9), (11.4, 8.6, -6.6), "#black"),
           bx((11, 3, 9), (12.4, 6, 11), "#black")]
    return els, {"plastic_grey": t("plastic_grey"), "plastic_yellow": t("plastic_yellow"), "dark_grey": t("dark_grey"),
                 "black": t("black")}


def fryer(on):
    """A twin-well deep fat fryer; the baskets go down into the oil when it's on."""
    els = [bx((0, 0, 1), (16, 13.4, 16), "#stainless"),
           bx((1, 1, 0.8), (15, 11, 1), "#stainless", faces=("north", "east", "west", "up", "down")),
           bx((2, 9, 0.2), (14, 9.8, 0.8), "#black"),
           bx((0, 13.4, 1), (16, 14, 2.5), "#stainless"), bx((0, 13.4, 12), (16, 14, 16), "#stainless"),
           bx((0, 13.4, 2.5), (1.5, 14, 12), "#stainless"), bx((7.5, 13.4, 2.5), (8.5, 14, 12), "#stainless"),
           bx((14.5, 13.4, 2.5), (16, 14, 12), "#stainless"),
           bx((1.5, 12.8, 2.5), (7.5, 13.2, 12), "#oil", faces=("up",), full=("up",)),
           bx((8.5, 12.8, 2.5), (14.5, 13.2, 12), "#oil", faces=("up",), full=("up",)),
           bx((0, 14, 14), (16, 20, 16), "#stainless")]
    for x in (2, 9):
        if on:
            els += [bx((x, 11, 3), (x + 5, 14.4, 11), "#mesh", faces=("north", "south", "east", "west")),
                    bx((x + 2, 14, 0.6), (x + 3, 14.8, 3), "#black")]
        else:
            els += [bx((x, 15, 12), (x + 5, 18.4, 13.9), "#mesh", faces=("north", "south", "east", "west", "down")),
                    bx((x + 2, 18, 13), (x + 3, 19, 14), "#black")]
    return els, {"stainless": t("stainless"), "oil": t("oil"), "mesh": t("mesh"),
                 "black": t("black")}


def menu_board(part):
    """A lit menu board on the wall, two blocks wide."""
    left = part == "left"
    face = "#menu_left" if left else "#menu_right"
    x0, x1 = (0.5, 16) if left else (0, 15.5)
    els = [bx((x0, 0.5, 14), (x1, 15.5, 16), "#black"),
           bx((x0, 0.5, 13.9), (x1, 15.5, 14), face, faces=("north",), emissive=True, uv={"north": [x0, 0.5, x1, 15.5]}),
           bx((x0, 15.5, 13.6), (x1, 16, 16), "#plastic_grey"), bx((x0, 0, 13.6), (x1, 0.5, 16), "#plastic_grey")]
    return els, {"black": t("black"), face[1:]: t(face[1:]), "plastic_grey": t("plastic_grey")}


def drive_thru_speaker():
    els = [bx((5, 0, 7), (11, 1, 11), "#dark_grey"),
           bx((6.8, 1, 8), (9.2, 14, 10), "#dark_grey"),
           bx((2, 14, 6), (14, 28, 10), "#dark_grey"),
           bx((3, 19, 5.98), (13, 27, 6), "#order_screen", faces=("north",), full=("north",), emissive=True),
           bx((5, 15, 5.98), (11, 18, 6), "#grille", faces=("north",), full=("north",)),
           bx((1.5, 28, 5), (14.5, 29, 11), "#black")]
    return els, {"dark_grey": t("dark_grey"), "order_screen": t("order_screen"), "grille": t("grille"), "black": t("black")}


def projector():
    els = [bx((3, 0, 4), (13, 6, 14), "#black"),
           bx((6, 1.5, 1.5), (10, 5.5, 4), "#black", front="#lens", full=("north",)),
           bx((3.5, 6, 6), (12.5, 6.2, 12), "#grille", faces=("up",), full=("up",)),
           bx((13, 1.5, 6), (13.1, 4.5, 12), "#grille", faces=("east",), full=("east",))]
    return els, {"black": t("black"), "lens": t("lens"), "grille": t("grille")}


def ticket_machine():
    els = [bx((2, 0, 5), (14, 26, 14), "#plastic_red", uv={"north": [0, 0, 12, 16], "south": [0, 0, 12, 16],
                                                           "east": [0, 0, 9, 16], "west": [0, 0, 9, 16]}),
           bx((3, 13, 4.98), (13, 23, 5), "#ticket_screen", faces=("north",), full=("north",), emissive=True),
           bx((5, 8, 4.6), (11, 9, 5), "#black"),
           bx((9.5, 10, 4.2), (13, 12.4, 5), "#black"),
           bx((9.7, 12.4, 4.4), (12.8, 12.5, 4.9), "#card_reader", faces=("up",), full=("up",)),
           bx((2, 26, 5), (14, 28, 14), "#black")]
    return els, {"plastic_red": t("plastic_red"), "ticket_screen": t("ticket_screen"), "black": t("black"),
                 "card_reader": t("card_reader")}


def plant_bench():
    els = legs(0.5, 1, 15.5, 15, 9, 1.2, "#dark_grey")
    els += [bx((0, 9, 0.5), (16, 10, 15.5), "#slats"),
            bx((0.4, 10, 1), (15.6, 13, 15), "#plants", uv={"north": [0, 0, 16, 3], "south": [0, 0, 16, 3],
                                                           "east": [0, 0, 14, 3], "west": [0, 0, 14, 3]})]
    els[-1]["faces"]["up"] = {"texture": "#plants", "uv": [0, 0, 16, 16]}
    return els, {"dark_grey": t("dark_grey"), "slats": t("slats"), "plants": t("plants")}


def till():
    els = [bx((3, 0, 5), (13, 3, 13), "#black"),
           bx((3.5, 3, 5.5), (12.5, 3.8, 10.5), "#black"),
           bx((3.6, 3.8, 5.6), (12.4, 3.81, 10.4), "#till_face", faces=("up",), full=("up",)),
           bx((7.4, 3, 11), (8.6, 6, 12), "#black"),
           bx((4.5, 6, 11), (11.5, 10.5, 12), "#black", faces=("east", "west", "up", "down")),
           bx((4.5, 6, 10.98), (11.5, 10.5, 11), "#till_screen", faces=("north",), full=("north",), emissive=True),
           bx((4.5, 6, 12), (11.5, 10.5, 12.02), "#till_screen", faces=("south",), full=("south",), emissive=True)]
    return els, {"black": t("black"), "till_face": t("till_face"), "till_screen": t("till_screen")}


def clothes_rail():
    els = [bx((0.5, 0, 7.4), (1.5, 15, 8.6), "#chrome"), bx((14.5, 0, 7.4), (15.5, 15, 8.6), "#chrome"),
           bx((0, 0, 3), (2, 0.8, 13), "#chrome"), bx((14, 0, 3), (16, 0.8, 13), "#chrome"),
           bx((0.5, 14, 7.6), (15.5, 14.8, 8.4), "#chrome")]
    tex = {"chrome": t("chrome")}
    names = list(GARMENTS)
    for i, x in enumerate((2.2, 4.4, 6.6, 8.8, 11, 13.2)):
        g = f"garment_{names[(i * 5) % len(names)]}"
        els += [bx((x, 4, 3), (x + 1.4, 13.4, 13), "#" + g),
                bx((x + 0.4, 13.4, 7.6), (x + 1, 14.4, 8.4), "#chrome")]
        tex[g] = t(g)
    return els, tex


# ----------------------------------------------------------------- pubs, bars, clubs, cafes, gyms

def beer_pumps():
    """Three hand pulls on the bar, with pump clips for made-up ales, and a drip tray."""
    els = [bx((1, 0, 6), (15, 0.6, 12), "#stainless"), bx((1.5, 0.6, 9), (14.5, 2.2, 11), "#black")]
    for k, x in enumerate((3, 7.4, 11.8)):
        els += [bx((x, 2.2, 9.4), (x + 1.2, 4, 10.6), "#chrome"),
                bx((x + 0.2, 4, 9.6), (x + 1, 11, 10.4), "#walnut"),
                bx((x - 0.4, 7.2, 9.2), (x + 1.6, 10.4, 9.6), "#pump_clips", faces=("north", "south"),
                   uv={"north": [k * 5.33, 1, k * 5.33 + 5.33, 15], "south": [k * 5.33 + 5.33, 1, k * 5.33, 15]}),
                bx((x + 0.2, 2.2, 7.6), (x + 1, 3, 9.4), "#chrome")]
    return els, {"stainless": t("stainless"), "black": t("black"), "chrome": t("chrome"), "walnut": t("walnut"),
                 "pump_clips": t("pump_clips")}


def back_bar():
    """The wall behind a bar: optics, shelves of bottles and glasses against a mirror."""
    els = [bx((0, 0, 13), (16, 16, 16), "#walnut", front="#back_bar", full=("north",)),
           bx((0, 7, 11), (16, 7.6, 13), "#walnut"), bx((0, 2.8, 11.4), (16, 3.4, 13), "#walnut")]
    return els, {"walnut": t("walnut"), "back_bar": t("back_bar")}


def bar_stool():
    els = G.round_column(0.7, 0, 10.4, "#chrome")
    els = [json.loads(json.dumps(e)) for e in els]
    els += [bx((4.5, 0, 4.5), (11.5, 0.5, 11.5), "#chrome"),
            bx((5.5, 3.5, 5.5), (10.5, 4, 10.5), "#chrome")]
    els += [json.loads(json.dumps(e)) for e in G.round_column(4, 10.4, 11.8, "#leather_red", top=True, bottom=True)]
    return els, {"chrome": t("chrome"), "leather_red": t("leather_red")}


def pool_table(part):
    left = part == "left"
    top = "#pool_left" if left else "#pool_right"
    x0, x1 = (0, 16) if left else (0, 16)
    els = [bx((x0, 11, 1), (x1, 12.6, 15), "#walnut", faces=("north", "south", "east", "west", "down")),
           bx((x0 + (1 if left else 0), 12.6, 2), (x1 - (0 if left else 1), 12.61, 14), top, faces=("up",),
              uv={"up": [1 if left else 0, 1, 16 if left else 15, 15]}),
           bx((x0, 12.6, 1), (x1, 13.4, 2), "#walnut"), bx((x0, 12.6, 14), (x1, 13.4, 15), "#walnut"),
           bx((0, 12.6, 2), (1, 13.4, 14), "#walnut") if left else bx((15, 12.6, 2), (16, 13.4, 14), "#walnut")]
    lx = 1.5 if left else 12.5
    els += [bx((lx, 0, 2.5), (lx + 2, 11, 4.5), "#walnut"), bx((lx, 0, 11.5), (lx + 2, 11, 13.5), "#walnut")]
    return els, {"walnut": t("walnut"), top[1:]: t(top[1:])}


def dartboard():
    els = [bx((2.5, 2.5, 15.2), (13.5, 13.5, 16), "#black", faces=("north", "east", "west", "up", "down")),
           bx((3, 3, 15.18), (13, 13, 15.2), "#dartboard", faces=("north",), full=("north",))]
    return els, {"black": t("black"), "dartboard": t("dartboard")}


def fruit_machine(on):
    lo, hi = ("#fruit_lower_on", "#fruit_upper_on") if on else ("#fruit_lower", "#fruit_upper")
    els = [bx((1, 0, 4), (15, 16, 15), "#dark_grey", faces=("east", "west", "south", "down")),
           bx((1, 16, 4), (15, 30, 15), "#dark_grey", faces=("east", "west", "south", "up")),
           bx((1, 0, 3.98), (15, 16, 4), lo, faces=("north",), full=("north",), emissive=on),
           bx((1, 16, 3.98), (15, 30, 4), hi, faces=("north",), emissive=on, uv={"north": [0, 2, 16, 16]}),
           bx((1, 12, 2.5), (15, 13, 4), "#black")]
    return els, {"dark_grey": t("dark_grey"), lo[1:]: t(lo[1:]), hi[1:]: t(hi[1:]), "black": t("black")}


def barber_chair():
    els = [json.loads(json.dumps(e)) for e in G.round_column(4, 0, 0.8, "#chrome", top=True)]
    els += [json.loads(json.dumps(e)) for e in G.round_column(1.2, 0.8, 6, "#chrome")]
    els += [bx((3, 6, 2.5), (13, 9, 12.5), "#leather_red"),
            bx((3, 9, 11.5), (13, 22, 14), "#leather_red"),
            bx((5, 22, 12), (11, 25, 13.6), "#leather_red"),
            bx((1.6, 9, 3), (3, 11, 11.5), "#chrome"), bx((13, 9, 3), (14.4, 11, 11.5), "#chrome"),
            bx((4, 2, 0.5), (12, 2.6, 3), "#chrome")]
    return els, {"chrome": t("chrome"), "leather_red": t("leather_red")}


def pizza_oven(on):
    fire = "#flames" if on else "#coals"
    els = [bx((0, 0, 1), (16, 7, 16), "#oven_brick"),
           bx((1, 7, 2), (15, 13, 15), "#oven_brick"),
           bx((3, 13, 4), (13, 16, 13), "#oven_brick"),
           bx((5, 7.5, 1.6), (11, 11.5, 2), "#black", faces=("north",)),
           bx((5.4, 7.6, 1.98), (10.6, 11, 2), fire, faces=("north",), full=("north",), emissive=on),
           bx((6.5, 16, 8), (9.5, 16, 11), "#black", faces=("up",)),
           bx((0, 7, 0.4), (16, 7.6, 2), "#marble")]
    return els, {"oven_brick": t("oven_brick"), "black": t("black"), fire[1:]: t(fire[1:]), "marble": t("marble")}


def doner_spit(on):
    heat = "#heater_on" if on else "#heater"
    els = [bx((3, 0, 3), (13, 1, 13), "#stainless"),
           bx((7.6, 1, 7.6), (8.4, 15, 8.4), "#stainless"),
           bx((5, 1.5, 5), (11, 4, 11), "#doner"), bx((5.5, 4, 5.5), (10.5, 9, 10.5), "#doner"),
           bx((6, 9, 6), (10, 13, 10), "#doner"),
           bx((3, 1, 12), (13, 14, 13.4), "#stainless", faces=("east", "west", "up", "south")),
           bx((3.5, 2, 11.98), (12.5, 13, 12), heat, faces=("north",), full=("north",), emissive=on)]
    return els, {"stainless": t("stainless"), "doner": t("doner"), heat[1:]: t(heat[1:])}


def coffee_machine():
    els = [bx((1.5, 0, 6), (14.5, 9, 14), "#stainless"),
           bx((1.5, 9, 6), (14.5, 10, 14), "#black"),
           bx((2.5, 10, 7), (5, 12, 9.5), "#ceramic"), bx((6, 10, 7), (8.5, 12, 9.5), "#ceramic"),
           bx((1.5, 0, 3.5), (14.5, 0.6, 6), "#black")]
    for x in (3.5, 9.5):
        els += [bx((x, 5.5, 4.6), (x + 3, 7, 6), "#chrome"), bx((x + 1, 0.6, 4.4), (x + 2.2, 2.4, 5.6), "#ceramic")]
    els.append(bx((12.6, 3, 5), (13.4, 7, 6), "#chrome"))
    return els, {"stainless": t("stainless"), "black": t("black"), "ceramic": t("ceramic"), "chrome": t("chrome")}


def cake_display():
    els = [bx((0, 0, 1), (16, 8, 15), "#white_gloss"),
           bx((0, 8, 1), (16, 8.4, 15), "#marble"),
           bx((0.5, 8.4, 1.5), (15.5, 14, 14.5), "#cakes", faces=("north", "up"),
              uv={"north": [0, 0, 16, 5.6], "up": [0, 10, 16, 16]}, emissive=True),
           bx((0.4, 8.4, 1.4), (15.6, 14.2, 14.6), "#glass", faces=("north", "east", "west", "up"))]
    return els, {"white_gloss": t("white_gloss"), "marble": t("marble"), "cakes": t("cakes"), "glass": t("glass")}


def dj_decks():
    els = [bx((0, 0, 4), (16, 2.4, 13), "#black")]
    for x in (0.6, 10.2):
        els += [bx((x, 2.4, 5), (x + 5.2, 2.5, 12), "#vinyl", faces=("up",), full=("up",)),
                bx((x + 4.4, 2.4, 10.4), (x + 5, 3.4, 11.8), "#chrome")]
    els.append(bx((6.2, 2.4, 5), (9.8, 2.6, 12), "#mixer", faces=("up",), full=("up",), emissive=True))
    return els, {"black": t("black"), "vinyl": t("vinyl"), "chrome": t("chrome"), "mixer": t("mixer")}


def dance_floor(on):
    tex = "#dance_floor" if on else "#dance_floor_off"
    els = [bx((0, 0, 0), (16, 16, 16), "#black", faces=("north", "east", "south", "west", "down")),
           bx((0, 15.99, 0), (16, 16, 16), tex, faces=("up",), full=("up",), emissive=on)]
    return els, {"black": t("black"), tex[1:]: t(tex[1:])}


def treadmill(part):
    left = part == "left"
    els = [bx((0 if left else 0, 0, 3), (16, 2, 13), "#dark_grey"),
           bx((0, 2, 4), (16, 2.2, 12), "#belt", faces=("up",), full=("up",))]
    tex = {"dark_grey": t("dark_grey"), "belt": t("belt")}
    if left:
        els += [bx((1, 2, 3), (2, 16, 4), "#dark_grey"), bx((1, 2, 12), (2, 16, 13), "#dark_grey"),
                bx((0, 15, 3), (3, 16, 13), "#dark_grey"),
                bx((0.5, 16, 5), (3.5, 20, 11), "#black", rot=("z", 22.5, (2, 16, 8))),
                bx((3.51, 16.5, 5.5), (3.52, 19.5, 10.5), "#gym_screen", faces=("east",), full=("east",), emissive=True,
                   rot=("z", 22.5, (2, 16, 8)))]
        tex.update({"black": t("black"), "gym_screen": t("gym_screen")})
    return els, tex


def weight_bench():
    els = legs(3, 2, 13, 14, 6, 1.2, "#chrome")
    els += [bx((4, 6, 2), (12, 8, 14), "#leather_black"),
            bx((2, 6, 13), (3, 18, 14), "#chrome"), bx((13, 6, 13), (14, 18, 14), "#chrome"),
            bx((-2, 17, 13.1), (18, 17.8, 13.9), "#chrome")]
    for x in (-1.6, 15.6):
        els.append(bx((x, 13.5, 11.5), (x + 2, 21.5, 15.5), "#black"))
    return els, {"chrome": t("chrome"), "leather_black": t("leather_black"), "black": t("black")}


def bottle_fridge():
    els = [bx((0, 0, 1), (16, 14, 16), "#black", faces=("east", "west", "south", "up", "down")),
           bx((0, 0, 0.98), (16, 14, 1), "#bottle_fridge", faces=("north",), full=("north",), emissive=True)]
    return els, {"black": t("black"), "bottle_fridge": t("bottle_fridge")}


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
        F("microwave", "Microwave", microwave, "tabletop", (3, 0, 6, 13, 5.6, 13.5), light=6, oven=True,
          effect="microwave", group="kitchen", sound="METAL"),
        F("kettle", "Kettle", kettle, "tabletop", (4.3, 0, 6, 10.8, 6.6, 11), toggle=True, run=100, effect="kettle",
          group="kitchen", sound="METAL"),
        F("toaster", "Toaster", toaster, "tabletop", (4, 0, 5.5, 12.6, 5, 10.5), toggle=True, run=80, effect="toaster",
          group="kitchen", sound="METAL"),
        F("toilet", "Toilet", toilet, shape=(3, 0, 1.4, 13, 15.2, 15.2), seat=8.0, effect="flush", group="bathroom",
          sound="STONE"),
        F("basin", "Bathroom Basin", basin, shape=(2, 0, 5, 14, 13.5, 15), toggle=True, run=60, effect="tap",
          group="bathroom", sound="STONE"),
        F("bath", "Bath", bath, "wide", (0, 0, 4.5, 16, 9, 16), seat=2.2, toggle=True, effect="bath",
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
    for kind in ("groceries", "drinks", "household", "snacks", "diy", "games", "pharmacy", "newsagent"):
        nice = "DIY" if kind == "diy" else kind.capitalize()
        out.append(F(f"shelves_{kind}", f"Supermarket Shelves ({nice})",
                     (lambda k=kind: lambda: shelving(k))(), "tall", (0, 0, 1, 16, 16, 15), rows=3, group="shop",
                     sound="METAL"))
    out += [
        F("chiller_cabinet", "Chiller Cabinet", chiller, "tall", (0, 0, 1.6, 16, 16, 16), rows=3, group="shop",
          sound="METAL"),
        F("upright_freezer", "Upright Freezer", upright_freezer, "tall", (0, 0, 1.2, 16, 16, 16), rows=3, group="shop",
          sound="METAL"),
        F("freezer_island", "Chest Freezer", freezer_island, shape=(0, 0, 1, 16, 13.5, 15), rows=3, group="shop",
          sound="METAL"),
        F("produce_stand", "Fruit and Veg Stand", produce_stand, shape=(0, 0, 1, 16, 12, 15), group="shop"),
        F("checkout", "Checkout", checkout, "wide", (0, 0, 2, 16, 12, 14), group="shop", sound="METAL"),
        F("self_checkout", "Self-Checkout", self_checkout, "tall", (2, 0, 4, 14, 16, 14), group="shop", sound="METAL"),
        F("shopping_trolley", "Shopping Trolley", trolley, shape=(4, 0, 1.5, 12, 15, 15), group="shop", sound="METAL",
          cutout=True),
        F("pallet_racking", "Pallet Racking", pallet_racking, "tall", (0, 0, 1, 16, 16, 15), rows=6, group="shop",
          sound="METAL"),
        F("cash_machine", "Cash Machine", cash_machine, group="shop", sound="METAL"),
        F("cinema_seat_red", "Cinema Seat (Red)", lambda: cinema_seat("red"), shape=(0, 0, 1.5, 16, 10, 15.5), seat=6.5,
          group="cinema", sound="WOOL"),
        F("cinema_seat_blue", "Cinema Seat (Blue)", lambda: cinema_seat("blue"), shape=(0, 0, 1.5, 16, 10, 15.5),
          seat=6.5, group="cinema", sound="WOOL"),
        F("popcorn_machine", "Popcorn Machine", popcorn_machine, "tabletop", (3, 0, 4, 13, 15.2, 12), light=9,
          effect="click", group="cinema", sound="GLASS", translucent=True),
        F("pick_and_mix", "Pick and Mix", pick_and_mix, shape=(0, 0, 3.6, 16, 12.6, 16), group="cinema"),
        F("drinks_dispenser", "Drinks Dispenser", drinks_dispenser, "tabletop", (2, 0, 2.6, 14, 14, 14), group="cinema",
          sound="METAL"),
    ]
    out += [
        F("till", "Till", till, "tabletop", (3, 0, 5, 13, 10.5, 13), group="shop", sound="METAL"),
        F("clothes_rail", "Clothes Rail", clothes_rail, shape=(0, 0, 3, 16, 15, 13), group="shop", sound="WOOL"),
        F("plant_bench", "Garden Centre Plant Bench", plant_bench, shape=(0, 0, 0.5, 16, 13, 15.5), group="shop",
          sound="GRASS"),
        F("tool_chest", "Tool Chest", tool_chest, shape=(0, 0, 1.8, 16, 14.6, 15.2), rows=3, surface="counter",
          group="shop", sound="METAL"),
        F("tyre_stack", "Stack of Tyres", tyre_stack, shape=(1.5, 0, 1.5, 14.5, 13.7, 14.5), group="shop", sound="WOOL",
          cutout=True),
        F("oil_drum", "Oil Drum", oil_drum, shape=(1.8, 0, 1.8, 14.2, 14.5, 14.2), group="shop", sound="METAL",
          cutout=True),
        F("car_lift", "Car Lift Post", car_lift, "tall", (5, 0, 6, 11, 16, 12), group="shop", sound="METAL"),
        F("fryer", "Deep Fat Fryer", fryer, shape=(0, 0, 1, 16, 14, 16), light=6, effect="click", surface="counter",
          group="shop", sound="METAL", cutout=True),
        F("menu_board", "Menu Board", menu_board, "wide", (0, 0, 13.6, 16, 16, 16), group="shop", sound="GLASS"),
        F("drive_thru_speaker", "Drive-Thru Order Point", drive_thru_speaker, "tall", (2, 0, 6, 14, 16, 10),
          group="shop", sound="METAL"),
        F("projector", "Cinema Projector", projector, "tabletop", (3, 0, 1.5, 13, 6.2, 14), group="cinema",
          sound="METAL"),
        F("ticket_machine", "Ticket Machine", ticket_machine, "tall", (2, 0, 5, 14, 16, 14), group="cinema",
          sound="METAL"),
    ]
    out += [
        F("beer_pumps", "Beer Pumps", beer_pumps, "tabletop", (1, 0, 6, 15, 11, 12), group="shop", sound="METAL"),
        F("back_bar", "Back Bar Shelves", back_bar, shape=(0, 0, 11, 16, 16, 16), rows=2, group="shop", sound="GLASS"),
        F("bar_stool", "Bar Stool", bar_stool, shape=(4, 0, 4, 12, 11.8, 12), seat=11.8, group="shop", sound="METAL"),
        F("pool_table", "Pool Table", pool_table, "wide", (0, 0, 1, 16, 13.4, 15), group="shop"),
        F("dartboard", "Dartboard", dartboard, shape=(2.5, 2.5, 15, 13.5, 13.5, 16), group="shop"),
        F("fruit_machine", "Fruit Machine", fruit_machine, "tall", (1, 0, 2.5, 15, 16, 15), light=6, effect="click",
          group="shop", sound="METAL"),
        F("barber_chair", "Barber's Chair", barber_chair, shape=(2, 0, 1, 14, 11, 14), seat=9, group="shop",
          sound="METAL"),
        F("pizza_oven", "Pizza Oven", pizza_oven, shape=(0, 0, 0.4, 16, 16, 16), light=11, effect="fire", oven=True,
          group="shop", sound="STONE"),
        F("doner_spit", "Doner Kebab Grill", doner_spit, "tabletop", (3, 0, 3, 13, 15, 13.4), light=5, effect="click",
          group="shop", sound="METAL"),
        F("coffee_machine", "Coffee Machine", coffee_machine, "tabletop", (1.5, 0, 3.5, 14.5, 12, 14), toggle=True,
          run=80, effect="kettle", group="shop", sound="METAL"),
        F("cake_display", "Cake Display Counter", cake_display, shape=(0, 0, 1, 16, 14.2, 15), surface="counter",
          group="shop", sound="GLASS", translucent=True),
        F("dj_decks", "DJ Decks", dj_decks, "tabletop", (0, 0, 4, 16, 3.4, 13), group="shop", sound="METAL"),
        F("dance_floor", "Light-Up Dance Floor", dance_floor, light=12, effect="click", group="shop", sound="GLASS"),
        F("treadmill", "Treadmill", treadmill, "wide", (0, 0, 3, 16, 2.2, 13), group="shop", sound="METAL"),
        F("weight_bench", "Weight Bench", weight_bench, shape=(2, 0, 2, 14, 8, 14), seat=8, group="shop", sound="METAL"),
        F("bottle_fridge", "Bottle Fridge", bottle_fridge, shape=(0, 0, 1, 16, 14, 16), rows=2, surface="counter",
          group="shop", sound="GLASS"),
    ]
    for film, title in ST.POSTERS.items():
        out.append(F(f"film_poster_{film}", f"Film Poster ({title})", (lambda f=film: lambda: film_poster(f))(), "tall",
                     (0.5, 0, 14.5, 15.5, 16, 16), group="cinema", sound="GLASS"))
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
        fills = len(inspect.signature(f.builder).parameters) > 1     # its model changes when it's on
        for part in ("left", "right"):
            for lit in lit_states:
                on = fills and lit == "true"
                els, tex = f.builder(part, on) if fills else f.builder(part)
                mid = _write_model(f"{n}_{part}{'_on' if on else ''}", els, tex, f)
                variants.update(_facings(f"lit={lit},part={part}", mid))
            els, tex = f.builder(part)
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

    public enum Group {{ LIVING, KITCHEN, BATHROOM, BEDROOM, SHOP, CINEMA }}

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
    out["itemGroup.ptmuk.shop_fittings"] = "UK Shop & Commercial Fittings"
    out["entity.ptmuk.seat"] = "Seat"
    return out
