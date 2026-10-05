"""UK street furniture, fences and road-sign plates. Called from generate_assets.main().

Models are in block pixels, facing north (the side the player looks at when placing).
"""
import json
import math

import numpy as np
from PIL import Image

import bus_extras as B
import furniture_hd as H
import furniture_textures as F

G = None   # the generate_assets module (helpers: box, model, write_json, save, ...)
TEX = {}


def t(key):
    return TEX[key]


def cyl(cx, cz, r, y1, y2, tex, top=False, bottom=False):
    """Smooth 16-sided column centred on (cx, cz)."""
    out = []
    for el in G.round_column(r, y1, y2, tex, top=top, bottom=bottom):
        el = json.loads(json.dumps(el))
        for k in ("from", "to"):
            el[k][0] += cx - 8
            el[k][2] += cz - 8
        if "rotation" in el:
            el["rotation"]["origin"][0] += cx - 8
            el["rotation"]["origin"][2] += cz - 8
        out.append(el)
    return out


def box(frm, to, tex, **kw):
    return G.box(frm, to, tex, **kw)


def face(x1, y1, x2, y2, z, tex, emissive=False, back=None, flip_back=True):
    """A flat decal facing north at depth z (optionally with a back face)."""
    faces = ("north", "south") if back else ("north",)
    el = box((x1, y1, z), (x2, y2, z + 0.02), "#" + (back or tex), faces=faces, front="#" + tex,
             emissive=emissive, full_front_uv=True)
    if back:
        el["faces"]["south"] = {"texture": "#" + back, "uv": [16, 0, 0, 16] if flip_back else [0, 0, 16, 16]}
    return el


# ----------------------------------------------------------------- textures

BIN_COLOURS = {"black": (34, 35, 37), "grey": (104, 107, 110), "green": (38, 92, 48), "blue": (32, 70, 150),
               "brown": (92, 60, 34)}


BIN_LABELS = {"black": "GENERAL WASTE", "grey": "GENERAL WASTE", "green": "GARDEN WASTE", "blue": "RECYCLING",
              "brown": "FOOD WASTE"}


def make_textures():
    s = G.save
    p = "furniture/"
    TEX.update({
        "red": s(H.paint((188, 20, 22), gloss=0.6, wear=0.04), p + "red_paint"),
        "black": s(H.paint((28, 28, 30), gloss=0.5, wear=0.03), p + "black_paint"),
        "cast_iron": s(H.cast_iron(), p + "cast_iron"),
        "stainless": s(H.stainless(), p + "stainless"),
        "green_cab": s(H.paint((40, 74, 54), wear=0.1), p + "green_paint"),
        "grey_cab": s(H.paint((152, 156, 154), wear=0.1), p + "grey_paint"),
        "feeder": s(H.paint((112, 126, 118), wear=0.12), p + "feeder_paint"),
        "yellow": s(H.paint((232, 186, 28), wear=0.08), p + "yellow_paint"),
        "white": s(H.paint((234, 234, 230), streaks=0.6), p + "white_plastic"),
        "blue_metal": s(H.paint((52, 70, 110), gloss=0.3, wear=0.05), p + "blue_paint"),
        "dark": s(H.dark(), p + "dark"),
        "rubber": s(H.rubber(), p + "rubber"),
        "concrete": s(H.concrete(), p + "concrete"),
        "wood": s(H.wood(), p + "wood"),
        "wood_vertical": s(H.wood_vertical(), p + "wood_vertical"),
        "galv": TEXG["pole_galvanised"],
        "telephone": s(H.telephone_sign(), p + "telephone_sign"),
        "litter": s(H.litter_band(), p + "litter_band"),
        "post_plate": s(H.post_plate(), p + "post_plate"),
        "dog_bin": s(H.dog_bin_face(), p + "dog_bin_face"),
        "grit": s(H.grit_face(), p + "grit_face"),
        "hydrant": s(F.hydrant_plate(), p + "hydrant_plate"),
        "timetable": s(H.timetable(), p + "timetable"),
        "bus_flag": s(F.bus_stop_flag(), p + "bus_stop_flag"),
        "poster": s(H.poster(), p + "poster"),
        "pay_display": s(H.pay_display_face(), p + "pay_display_face"),
        "meter": s(H.meter_face(), p + "meter_face"),
        "ev": s(H.ev_face(), p + "ev_face"),
        "cab_green_door": s(H.cabinet_door((40, 74, 54)), p + "cabinet_green_door"),
        "cab_grey_door": s(H.cabinet_door((152, 156, 154), sticker=True), p + "cabinet_grey_door"),
        "feeder_door": s(H.cabinet_door((112, 126, 118), sticker=True), p + "feeder_door"),
        "manhole": s(F.manhole(), p + "manhole"),
        "drain": s(F.drain(), p + "drain"),
        "led_panel": s(F.led_panel(), p + "led_panel"),
        "sodium": s(F.sodium_bowl(), p + "sodium_bowl"),
        "k6_window": s(F.glass_window(), p + "k6_window"),
        "stripes": s(F.stripes(), p + "belisha_stripes"),
        "globe": s(G.T.flash(F.amber_globe(True), F.amber_globe(False)), p + "belisha_globe", 12),
        "mesh": s(F.mesh(), p + "heras_mesh"),
        "shelter_glass": s(shelter_glass(), p + "shelter_glass"),
        "keep_left_lit": s(F.keep_left(), p + "keep_left_lit"),
        "gatso_front": s(F.camera_front("gatso"), p + "gatso_front"),
        "truvelo_front": s(F.camera_front("truvelo"), p + "truvelo_front"),
        "specs_front": s(F.specs_front(), p + "specs_front"),
        "camera_yellow": s(H.paint((232, 186, 28), gloss=0.3, wear=0.03), p + "camera_yellow"),
        "camera_grey": s(H.paint((120, 124, 128), wear=0.05), p + "camera_grey"),
        "cone": s(F.cone_bands(), p + "cone_bands"),
        "orange": s(H.paint(F.ORANGE, gloss=0.3, wear=0.04), p + "orange_paint"),
        "chapter8": s(F.chapter8(), p + "chapter8"),
        "road_closed": s(F.road_closed(), p + "road_closed"),
        "sos": s(H.sos_panel(), p + "sos_panel"),
        "marker_plate": s(F.marker_plate(), p + "marker_plate"),
        "w_beam": s(F.w_beam(), p + "w_beam"),
        "reader": s(B.reader_face(), p + "card_reader"),
        "reader_screen": s(B.reader_screen(), p + "card_reader_screen"),
        "top_up": s(B.top_up_face(), p + "top_up_machine_face"),
        "bus_stop_marking": s(B.marking(["BUS", "STOP"], B.MARK_YELLOW), p + "bus_stop_marking"),
        "bus_lane_marking": s(B.marking(["BUS", "LANE"], B.MARK_WHITE), p + "bus_lane_marking"),
    })
    for name, colour in BIN_COLOURS.items():
        TEX[f"bin_{name}"] = s(H.plastic(colour), p + f"bin_{name}")
        TEX[f"bin_front_{name}"] = s(H.bin_front(colour, BIN_LABELS[name]), p + f"bin_front_{name}")


def shelter_glass(size=64):
    """Clear glass with the white dot band (manifestation) at eye level; cutout."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    for y in range(26, 34):
        for x in range(0, size, 3):
            if (x // 3 + y) % 2 == 0:
                px[x, y] = (235, 240, 240, 255)
    for x in range(size):
        px[x, 0] = px[x, size - 1] = (150, 155, 158, 255)
    return img


# ----------------------------------------------------------------- items

def wheel_x(x1, x2, cy, cz, r, tex):
    """A wheel turning about the x axis: eight planks through the hub at 22.5 degree steps make
    a filled 16-sided disc (element rotations only go to 45 degrees, so half of them are laid
    the other way round)."""
    w = r * math.tan(math.radians(11.25)) * 1.04
    els = []
    for ang in (0, 22.5, -22.5, 45):
        els.append(box((x1, cy - w, cz - r), (x2, cy + w, cz + r), tex, rotation=("x", ang, (x1, cy, cz))))
    for ang in (0, 22.5, -22.5):
        els.append(box((x1, cy - r, cz - w), (x2, cy + r, cz + w), tex, rotation=("x", ang, (x1, cy, cz))))
    return els


def wheelie_bin(colour):
    """240 litre wheelie bin: slightly tapered body, rim, overhanging lid with a grip, the
    handle bar and hinge at the back, two wheels on an axle, a kick foot at the front."""
    b = "#bin_" + colour
    f = "#bin_front_" + colour
    els = [box((3.9, 1.6, 3.2), (12.1, 8.6, 12.8), b),
           box((3.5, 8.6, 2.7), (12.5, 15.4, 13.0), b, front=f, full_front_uv=True),
           box((3.2, 15.4, 2.3), (12.8, 16.0, 13.3), b),                      # rim
           box((3.0, 16.0, 1.9), (13.0, 16.7, 13.7), b),                      # lid
           box((3.6, 16.7, 3.0), (12.4, 16.9, 12.6), b),                      # lid moulding
           box((6.0, 15.4, 1.5), (10.0, 16.4, 1.9), b),                       # lid grip
           box((3.6, 14.8, 13.0), (12.4, 15.8, 14.4), b),                     # hinge housing
           box((4.2, 15.9, 14.0), (11.8, 16.6, 15.0), "#black"),              # handle bar
           box((4.0, 0, 3.0), (12.0, 1.6, 4.6), b),                           # kick foot
           box((4.4, 0, 9.6), (11.6, 1.6, 12.6), b),                          # wheel housing
           box((2.6, 1.55, 12.15), (13.4, 2.05, 12.65), "#black")]            # axle
    for x1, x2 in ((2.3, 3.6), (12.4, 13.7)):
        els += wheel_x(x1, x2, 1.8, 12.4, 1.8, "#rubber")
        els.append(box((x1 - 0.05 if x1 < 8 else x2 - 0.2, 1.2, 11.8), (x1 + 0.2 if x1 < 8 else x2 + 0.05, 2.4, 13.0), "#grey_cab"))
    return els, {"bin_" + colour: t("bin_" + colour), "bin_front_" + colour: t("bin_front_" + colour),
                 "black": t("black"), "rubber": t("rubber"), "grey_cab": t("grey_cab")}, {}


def communal_bin():
    g = "#bin_grey"
    els = [box((0.6, 2.4, 2), (15.4, 19, 15), g),
           box((0.3, 19, 1.6), (15.7, 19.8, 15.4), g),
           box((1.5, 19.8, 2.6), (14.5, 20.6, 14.4), g),
           box((-0.3, 12, 5), (0.6, 13, 12), "#galv"), box((15.4, 12, 5), (16.3, 13, 12), "#galv"),
           box((2, 6, 1.85), (14, 16, 2), g, faces=("north",))]
    for x, z in ((2.5, 3.5), (13.5, 3.5), (2.5, 13.5), (13.5, 13.5)):
        els += cyl(x, z, 1.0, 0, 2.4, "#rubber")
    return els, {"bin_grey": t("bin_grey"), "galv": t("galv"), "rubber": t("rubber")}, {}


def litter_bin():
    els = cyl(8, 8, 4.4, 0.8, 15, "#cast_iron") + cyl(8, 8, 4.65, 0, 0.8, "#cast_iron", top=True)
    els += cyl(8, 8, 4.6, 15, 15.6, "#cast_iron", top=True) + cyl(8, 8, 3.6, 15.6, 16.2, "#cast_iron", top=True)
    els += [box((5.2, 13.4, 3.45), (10.8, 14.6, 3.7), "#dark", faces=("north",)),
            face(5.3, 10.6, 10.7, 12.6, 3.48, "litter")]
    return els, {"cast_iron": t("cast_iron"), "dark": t("dark"), "litter": t("litter")}, {}


def dog_waste_bin():
    els = cyl(8, 8, 0.9, 0, 9.2, "#black") + [
        box((4, 9, 5), (12, 16, 11), "#red"),
        box((3.7, 16, 4.7), (12.3, 16.6, 11.3), "#red"),
        face(4.1, 9.1, 11.9, 15.9, 4.97, "dog_bin")]
    return els, {"black": t("black"), "red": t("red"), "dog_bin": t("dog_bin")}, {}


def grit_bin():
    els = [box((1, 0, 3), (15, 9.5, 13), "#yellow"),
           box((0.6, 9.5, 2.6), (15.4, 10.5, 13.4), "#yellow"),
           box((2, 10.5, 4), (14, 11.4, 12), "#yellow"),
           face(4, 3, 12, 7, 2.97, "grit")]
    return els, {"yellow": t("yellow"), "grit": t("grit")}, {}


def pillar_box():
    els = cyl(8, 8, 5.0, 0, 1.2, "#black", top=True) + cyl(8, 8, 4.5, 1.2, 20.5, "#red")
    els += cyl(8, 8, 4.9, 20.5, 21.5, "#red", top=True, bottom=True) + cyl(8, 8, 4.2, 21.5, 22.6, "#red", top=True)
    els += cyl(8, 8, 3.0, 22.6, 23.4, "#red", top=True) + cyl(8, 8, 1.6, 23.4, 24, "#red", top=True)
    els += [box((5, 16, 3.3), (11, 16.8, 3.6), "#dark", faces=("north", "up", "down")),
            box((4.8, 16.8, 2.9), (11.2, 17.3, 3.7), "#red"),
            face(5.6, 11, 10.4, 14.2, 3.45, "post_plate")]
    return els, {"black": t("black"), "red": t("red"), "dark": t("dark"), "post_plate": t("post_plate")}, {}


def phone_box():
    r = "#red"
    els = [box((0.5, 0, 0.5), (15.5, 1, 15.5), r)]
    for x, z in ((0.8, 0.8), (13.2, 0.8), (0.8, 13.2), (13.2, 13.2)):
        els.append(box((x, 1, z), (x + 2, 24, z + 2), r))
    # glazed walls (both faces), kick panels
    for (frm, to, faces_) in (((2.8, 2, 1.0), (13.2, 23.5, 1.3), ("north", "south")),
                              ((2.8, 2, 14.7), (13.2, 23.5, 15.0), ("north", "south")),
                              ((1.0, 2, 2.8), (1.3, 23.5, 13.2), ("east", "west")),
                              ((14.7, 2, 2.8), (15.0, 23.5, 13.2), ("east", "west"))):
        els.append(box(frm, to, "#k6_window", faces=faces_, overrides={f: "#k6_window" for f in faces_}))
        for f in faces_:
            els[-1]["faces"][f]["uv"] = [0, 0, 16, 16]
        x1, y1, z1 = frm
        x2, _, z2 = to
        els.append(box((x1, 1, z1), (x2, 2, z2), r))
    els.append(box((0.6, 23.5, 0.6), (15.4, 26.5, 15.4), r))
    for side, (x1, z1, x2, z2) in {"north": (3, 0.55, 13, 0.57), "south": (3, 15.43, 13, 15.45),
                                   "west": (0.55, 3, 0.57, 13), "east": (15.43, 3, 15.45, 13)}.items():
        el = box((x1, 24.2, z1), (x2, 26.0, z2), "#telephone", faces=(side,), emissive=True)
        el["faces"][side]["uv"] = [0, 0, 16, 16]
        els.append(el)
    els += [box((0.3, 26.5, 0.3), (15.7, 27.8, 15.7), r),
            box((1.2, 27.8, 1.2), (14.8, 29.2, 14.8), r),
            box((2.6, 29.2, 2.6), (13.4, 30.3, 13.4), r),
            box((6, 30.3, 6), (10, 31.4, 10), r),
            box((1.3, 1, 1.3), (14.7, 1.1, 14.7), "#dark", faces=("up",)),
            box((5.5, 12, 13.4), (10.5, 18, 14.6), "#grey_cab")]               # telephone unit
    return els, {"red": t("red"), "k6_window": t("k6_window"), "telephone": t("telephone"), "dark": t("dark"),
                 "grey_cab": t("grey_cab")}, {"cutout": True, "scale_y": 1.25}


def bus_shelter():
    g = "#galv"
    els = []
    for x in (-15.6, 30.8):
        for z in (2.4, 14.6):
            els.append(box((x, 0, z), (x + 0.8, 28, z + 0.8), g))
    rear = box((-14.8, 1, 15.0), (30.8, 27.6, 15.15), "#glass", faces=("north", "south"))
    side = box((-15.5, 1, 3.2), (-15.35, 27.6, 14.6), "#glass", faces=("east", "west"))
    for el in (rear, side):
        for f in el["faces"].values():
            f["uv"] = [0, 0, 16, 16]
        els.append(el)
    els += [box((29.4, 1, 3.4), (31.6, 27, 14.4), "#dark"),
            box((29.36, 2, 4), (29.4, 26, 13.8), "#poster", faces=("west",), emissive=True),
            box((31.6, 2, 4), (31.64, 26, 13.8), "#poster", faces=("east",), emissive=True),
            box((-16, 28, 1.6), (32, 29.2, 16), "#dark"),
            box((-16, 27.6, 1.3), (32, 29.4, 1.7), g),
            box((-12, 9, 13), (4, 9.8, 15), "#stainless"),
            box((-11, 0, 14.3), (-10.2, 9, 15), g), box((2.2, 0, 14.3), (3, 9, 15), g)]
    for el in els:
        if "#poster" in json.dumps(el):
            for f in el["faces"].values():
                f["uv"] = [0, 0, 16, 16]
    return els, {"galv": t("galv"), "glass": t("shelter_glass"), "dark": t("dark"), "poster": t("poster"),
                 "stainless": t("stainless")}, {"cutout": True}


def bus_shelter_london():
    """London style: black frame and a red fascia band along the front of the roof."""
    els, textures, opts = bus_shelter()
    for el in els:
        if el["faces"] and "#galv" in json.dumps(el):
            for f in el["faces"].values():
                f["texture"] = "#black"
    els.append(box((-16, 27.4, 0.9), (32, 29.8, 1.5), "#red", faces=("north", "up", "down", "east", "west")))
    textures = dict(textures, black=t("black"), red=t("red"))
    return els, textures, opts


def bus_stop_flag():
    els = cyl(8, 8, 1.0, 0, 32, "#galv", top=True)
    flag = box((9, 24, 7.8), (16.2, 31.2, 8.2), "#black", faces=("north", "south", "east", "up", "down"),
               overrides={"north": "#flag", "south": "#flag"})
    flag["faces"]["north"]["uv"] = [0, 0, 16, 16]
    flag["faces"]["south"]["uv"] = [16, 0, 0, 16]
    els += [flag, box((8.9, 25, 7.6), (9.4, 30, 8.4), "#galv"),
            box((4.8, 10.8, 6.6), (11.2, 19.2, 7.2), "#black"),
            face(5.1, 11.1, 10.9, 18.9, 6.58, "timetable")]
    return els, {"galv": t("galv"), "black": t("black"), "flag": t("bus_flag"), "timetable": t("timetable")}, \
        {"scale_y": 1.25}


def bench(kind):
    """Park bench: two end frames with armrests, seat and back slats with gaps, a centre
    support and feet. Wood slats on a cast iron frame, or an all steel version."""
    frame = "#black" if kind == "metal" else "#cast_iron"
    slat = "#black" if kind == "metal" else "#wood"
    els = []
    for x in (0.6, 7.5, 14.4):
        end = x != 7.5
        els += [box((x, 0, 3.4), (x + 1, 6.8, 4.4), frame),                    # front leg
                box((x, 0, 11.2), (x + 1, 6.8, 12.2), frame),                  # back leg
                box((x, 6.2, 3.6), (x + 1, 7.0, 11.8), frame),                 # seat bearer
                box((x, 7.0, 11.0), (x + 1, 15.6, 11.9), frame,
                    rotation=("x", -22.5, (x + 0.5, 7.0, 11.4))),               # raked back post
                box((x - 0.2, 0, 3.0), (x + 1.2, 0.4, 4.8), frame),            # feet
                box((x - 0.2, 0, 10.8), (x + 1.2, 0.4, 12.6), frame)]
        if end:
            els += [box((x - 0.1, 10.2, 3.2), (x + 1.1, 11.0, 11.2), frame),   # armrest
                    box((x, 7.0, 3.6), (x + 1, 10.2, 4.4), frame)]
    for z in (3.6, 5.5, 7.4, 9.3):
        els.append(box((0.4, 7.0, z), (15.6, 7.8, z + 1.5), slat))
    for y in (8.6, 10.6, 12.6):
        els.append(box((0.4, y, 11.6), (15.6, y + 1.5, 12.2), slat, rotation=("x", -22.5, (8, y, 11.9))))
    return els, {"black": t("black"), "cast_iron": t("cast_iron"), "wood": t("wood")}, {}


def bollard(kind):
    if kind == "cast_iron":
        els = cyl(8, 8, 2.9, 0, 1, "#cast_iron", top=True) + cyl(8, 8, 2.6, 1, 11, "#cast_iron")
        els += cyl(8, 8, 2.9, 9.5, 10.3, "#cast_iron", top=True, bottom=True)
        els += cyl(8, 8, 2.3, 11, 12.2, "#cast_iron", top=True) + cyl(8, 8, 1.5, 12.2, 13.2, "#cast_iron", top=True)
        els += cyl(8, 8, 0.7, 13.2, 13.8, "#cast_iron", top=True)
        return els, {"cast_iron": t("cast_iron")}, {}
    els = cyl(8, 8, 2.2, 0, 13, "#stainless", top=True) + cyl(8, 8, 2.26, 10.8, 11.8, "#red")
    return els, {"stainless": t("stainless"), "red": t("red")}, {}


def keep_left_bollard():
    els = [box((4.5, 0, 4.5), (11.5, 10, 11.5), "#white"),
           box((4.8, 10, 4.8), (11.2, 17, 11.2), "#white"),
           box((4.6, 17, 4.6), (11.4, 17.6, 11.4), "#white"),
           box((5.5, 10.8, 4.7), (10.5, 15.8, 4.78), "#sign", faces=("north",), emissive=True, full_front_uv=True),
           box((4.4, 3, 4.4), (11.6, 4, 11.6), "#yellow")]
    return els, {"white": t("white"), "sign": t("keep_left_lit"), "yellow": t("yellow")}, {"cutout": True}


def belisha_beacon():
    els = cyl(8, 8, 1.6, 0, 1.2, "#black", top=True) + cyl(8, 8, 1.15, 1.2, 26.3, "#stripes")
    globe = cyl(8, 8, 2.0, 26, 26.6, "#globe", top=True, bottom=True) + cyl(8, 8, 2.6, 26.6, 30, "#globe") + \
        cyl(8, 8, 2.0, 30, 30.7, "#globe", top=True)
    for el in globe:
        el["shade"] = False
        el["forge_data"] = {"block_light": 15, "sky_light": 15}
    els += globe + cyl(8, 8, 1.1, 30.7, 31.3, "#black", top=True)
    return els, {"black": t("black"), "stripes": t("stripes"), "globe": t("globe")}, {}


def pay_and_display():
    els = [box((4, 0, 5), (12, 3, 11), "#grey_cab"), box((3, 3, 4), (13, 21, 12), "#blue_metal"),
           box((2.6, 21, 3.4), (13.4, 22, 12.4), "#dark"),
           face(3.2, 5, 12.8, 20.5, 3.97, "pay_display")]
    return els, {"grey_cab": t("grey_cab"), "blue_metal": t("blue_metal"), "dark": t("dark"),
                 "pay_display": t("pay_display")}, {}


def parking_meter():
    els = cyl(8, 8, 0.8, 0, 14, "#grey_cab") + [box((5.2, 14, 5.5), (10.8, 21.5, 10.5), "#grey_cab"),
                                                box((5.6, 21.5, 5.9), (10.4, 22.2, 10.1), "#grey_cab"),
                                                face(5.4, 14.4, 10.6, 21.2, 5.47, "meter")]
    return els, {"grey_cab": t("grey_cab"), "meter": t("meter")}, {}


def ev_charger():
    els = [box((4.5, 0, 5.5), (11.5, 21, 10.5), "#white"),
           box((4.3, 21, 5.3), (11.7, 21.6, 10.7), "#dark"),
           face(4.7, 10, 11.3, 19.5, 5.47, "ev"),
           box((9.5, 6, 4.2), (11.2, 9.5, 5.5), "#dark"),
           box((4.35, 2, 6.5), (4.5, 20, 9.5), "#led", faces=("west",), emissive=True)]
    return els, {"white": t("white"), "dark": t("dark"), "ev": t("ev"), "led": t("bin_green")}, {}


def bike_stand():
    els = cyl(3, 8, 0.55, 0, 11.6, "#galv") + cyl(13, 8, 0.55, 0, 11.6, "#galv")
    els += [box((3.8, 12.0, 7.45), (12.2, 13.1, 8.55), "#galv"),
            box((2.5, 11.2, 7.45), (4.3, 12.6, 8.55), "#galv", rotation=("z", -45, (3.4, 11.9, 8))),
            box((11.7, 11.2, 7.45), (13.5, 12.6, 8.55), "#galv", rotation=("z", 45, (12.6, 11.9, 8)))]
    return els, {"galv": t("galv")}, {}


def cabinet(paint, door, x1, x2, h):
    els = [box((x1 - 0.2, 0, 3.7), (x2 + 0.2, 1, 12.3), "#concrete"),
           box((x1, 1, 4), (x2, h, 12), paint),
           box((x1 - 0.3, h, 3.6), (x2 + 0.3, h + 0.8, 12.4), paint),
           face(x1 + 0.1, 1.2, x2 - 0.1, h - 0.2, 3.97, door[1:]),
           box((x1 + 0.1, 1.2, 12.0), (x2 - 0.1, h - 0.2, 12.03), door, faces=("south",))]
    els[-1]["faces"]["south"]["uv"] = [16, 0, 0, 16]
    return els


def telecoms_cabinet():
    return cabinet("#green_cab", "#cab_green_door", 0.5, 15.5, 19), \
        {"concrete": t("concrete"), "green_cab": t("green_cab"), "cab_green_door": t("cab_green_door")}, {}


def controller_cabinet():
    return cabinet("#grey_cab", "#cab_grey_door", 1, 15, 21), \
        {"concrete": t("concrete"), "grey_cab": t("grey_cab"), "cab_grey_door": t("cab_grey_door")}, {}


def feeder_pillar():
    return cabinet("#feeder", "#feeder_door", 3, 13, 14), \
        {"concrete": t("concrete"), "feeder": t("feeder"), "feeder_door": t("feeder_door")}, {}


def hydrant_marker():
    plate = box((5, 7.5, 7.6), (11, 13.5, 8.4), "#yellow", faces=("north", "south", "east", "west", "up"),
                overrides={"north": "#hydrant", "south": "#hydrant"})
    plate["faces"]["north"]["uv"] = [0, 0, 16, 16]
    plate["faces"]["south"]["uv"] = [16, 0, 0, 16]
    return [box((7.3, 0, 7.3), (8.7, 9, 8.7), "#concrete"), plate], \
        {"concrete": t("concrete"), "yellow": t("yellow"), "hydrant": t("hydrant")}, {}


def manhole_cover():
    top = box((1, 0, 1), (15, 0.4, 15), "#manhole", faces=("up", "north", "south", "east", "west"))
    top["faces"]["up"]["uv"] = [0, 0, 16, 16]
    return [box((0.5, 0, 0.5), (15.5, 0.3, 15.5), "#concrete", faces=("up", "north", "south", "east", "west")), top], \
        {"concrete": t("concrete"), "manhole": t("manhole")}, {}


def drain_grate():
    top = box((1, 0, 0.2), (15, 0.4, 6), "#drain", faces=("up", "north", "south", "east", "west"))
    top["faces"]["up"]["uv"] = [0, 0, 16, 16]
    return [top], {"drain": t("drain")}, {}


def street_light(kind):
    els = cyl(8, 8, 1.4, 0, 1.6, "#galv", top=True) + [box((7.4, 1.0, -11), (8.6, 1.9, 8), "#galv")]
    if kind == "led":
        els += [box((5.2, 0.8, -16), (10.8, 2.3, -10.5), "#dark"),
                box((5.6, 0.75, -15.6), (10.4, 0.8, -10.9), "#led", faces=("down",), emissive=True)]
        els[-1]["faces"]["down"]["uv"] = [0, 0, 16, 16]
        return els, {"galv": t("galv"), "dark": t("dark"), "led": t("led_panel")}, {}
    els += [box((4.6, 0.6, -16), (11.4, 2.8, -9), "#grey_cab"),
            box((5.0, -0.6, -15.5), (11.0, 0.6, -9.5), "#sodium", faces=("down", "north", "south", "east", "west"),
                emissive=True)]
    for f in els[-1]["faces"].values():
        f["uv"] = [0, 0, 16, 16]
    return els, {"galv": t("galv"), "grey_cab": t("grey_cab"), "sodium": t("sodium")}, {}



# ----------------------------------------------------------------- cameras, roadworks, motorway

def gatso_camera():
    """Rear-facing Gatso: yellow box on top of a pole (put it on a UK sign pole)."""
    els = cyl(8, 8, 2.2, 0, 2, "#camera_grey", top=True)
    els += [box((3, 2, 1.5), (13, 14.5, 14.5), "#camera_yellow", front="#gatso_front", full_front_uv=True),
            box((2.6, 14.5, 1), (13.4, 15.2, 15), "#camera_grey")]
    return els, {"camera_yellow": t("camera_yellow"), "camera_grey": t("camera_grey"), "gatso_front": t("gatso_front")}, {}


def truvelo_camera():
    """Forward-facing Truvelo on its own post: tall yellow housing with two lenses."""
    els = cyl(8, 8, 1.4, 0, 8, "#camera_grey") + cyl(8, 8, 2.0, 0, 0.8, "#camera_grey", top=True)
    els += [box((4, 8, 3), (12, 24, 13), "#camera_yellow", front="#truvelo_front", full_front_uv=True),
            box((3.6, 24, 2.6), (12.4, 24.6, 13.4), "#camera_grey")]
    return els, {"camera_yellow": t("camera_yellow"), "camera_grey": t("camera_grey"),
                 "truvelo_front": t("truvelo_front")}, {}


def specs_camera():
    """Average-speed camera on an arm off the top of a pole."""
    els = cyl(8, 8, 1.6, 0, 3, "#camera_grey", top=True)
    els += [box((7, 3, 0), (9, 5, 9), "#camera_grey"),
            box((4.5, 1.5, -6), (11.5, 7.5, 1), "#camera_yellow", front="#specs_front", full_front_uv=True),
            box((4.2, 7.5, -6.6), (11.8, 8.1, 1.2), "#camera_grey")]
    return els, {"camera_yellow": t("camera_yellow"), "camera_grey": t("camera_grey"), "specs_front": t("specs_front")}, {}


def traffic_cone():
    els = [box((3, 0, 3), (13, 1, 13), "#rubber")]
    steps = [(4.2, 1, 3), (3.6, 3, 5), (3.0, 5, 7), (2.4, 7, 9), (1.8, 9, 11), (1.2, 11, 12.5)]
    for r, y1, y2 in steps:
        els += cyl(8, 8, r, y1, y2, "#cone", top=True)
    return els, {"rubber": t("rubber"), "cone": t("cone")}, {}


def chapter8_barrier():
    els = [box((0, 10, 7.4), (16, 14, 8.6), "#chapter8"),
           box((1, 0, 4), (4, 1.2, 12), "#rubber"), box((12, 0, 4), (15, 1.2, 12), "#rubber"),
           box((2.1, 1.2, 7.5), (2.9, 10, 8.5), "#rubber"), box((13.1, 1.2, 7.5), (13.9, 10, 8.5), "#rubber")]
    return els, {"chapter8": t("chapter8"), "rubber": t("rubber")}, {}


def road_closed_sign():
    els = [face(1, 6, 15, 18, 7.3, "road_closed"),
           box((1, 6, 7.32), (15, 18, 7.6), "#galv", faces=("south", "up", "down", "east", "west")),
           box((2, 0, 9), (3, 17, 10), "#galv", rotation=("x", -22.5, (2.5, 8, 9.5))),
           box((13, 0, 9), (14, 17, 10), "#galv", rotation=("x", -22.5, (13.5, 8, 9.5))),
           box((1.5, 0, 6.5), (3.5, 1, 8.5), "#rubber"), box((12.5, 0, 6.5), (14.5, 1, 8.5), "#rubber")]
    return els, {"road_closed": t("road_closed"), "galv": t("galv"), "rubber": t("rubber")}, {}


def concrete_barrier():
    """Concrete step barrier, as used in motorway central reserves."""
    els = [box((0, 0, 3), (16, 3, 13), "#concrete"), box((0, 3, 4.5), (16, 9, 11.5), "#concrete"),
           box((0, 9, 5.5), (16, 14, 10.5), "#concrete")]
    return els, {"concrete": t("concrete")}, {}


def emergency_phone():
    els = cyl(8, 8, 1.0, 0, 12, "#orange")
    els += [box((4, 12, 5), (12, 22, 11), "#orange"), face(5, 15, 11, 21, 4.97, "sos"),
            box((3.7, 22, 4.7), (12.3, 22.6, 11.3), "#dark")]
    return els, {"orange": t("orange"), "sos": t("sos"), "dark": t("dark")}, {}


def marker_post():
    els = [box((6.5, 0, 7), (9.5, 18, 9), "#white"), face(6.6, 12, 9.4, 15, 6.97, "marker_plate"),
           box((6.8, 16, 6.95), (9.2, 17.2, 7.0), "#red", faces=("north",), emissive=True)]
    return els, {"white": t("white"), "marker_plate": t("marker_plate"), "red": t("red")}, {}


def card_reader():
    """Stand-alone card reader (as at tram stops): yellow head on a grey post, with the reader
    pad and a little screen on the front."""
    els = cyl(8, 8, 1.0, 0.5, 15, "#grey_cab")
    els += [box((6.2, 0, 6.2), (9.8, 0.6, 9.8), "#dark"),
            box((4.6, 14.6, 6), (11.4, 21.2, 10), "#yellow"),
            box((4.4, 21.2, 5.8), (11.6, 21.8, 10.2), "#dark"),
            face(5.8, 15.4, 10.2, 19.8, 5.97, "reader", emissive=True),
            face(6.2, 19.95, 9.8, 21.05, 5.97, "reader_screen", emissive=True)]
    return els, {"grey_cab": t("grey_cab"), "dark": t("dark"), "yellow": t("yellow"), "reader": t("reader"),
                 "reader_screen": t("reader_screen")}, {}


def top_up_machine():
    """Ticket and top-up machine: a cabinet on a plinth with a hood over the face."""
    els = [box((3, 0, 4.5), (13, 1.2, 12), "#dark"),
           box((2.5, 1.2, 4.5), (13.5, 22.5, 12), "#blue_metal"),
           box((2.2, 22.5, 3.4), (13.8, 24, 12.3), "#dark"),
           box((2.2, 3.0, 3.4), (2.6, 22.5, 4.5), "#dark"),
           box((13.4, 3.0, 3.4), (13.8, 22.5, 4.5), "#dark"),
           face(2.9, 3.6, 13.1, 21.7, 4.47, "top_up")]
    return els, {"dark": t("dark"), "blue_metal": t("blue_metal"), "top_up": t("top_up")}, {}


def road_marking(tex):
    """Road lettering laid on the road, 1.5 blocks wide and 3 long (the block is the middle), to
    read for a driver coming from where the player stood when placing it."""
    top = box((-4, 0.05, -16), (20, 0.1, 32), "#" + tex, faces=("up",))
    top["faces"]["up"]["uv"] = [0, 0, 16, 16]
    top["faces"]["up"]["rotation"] = 180
    return [top], {tex: t(tex)}, {"cutout": True}


FURNITURE = {
    "wheelie_bin_black": (lambda: wheelie_bin("black"), "Wheelie Bin (Black)"),
    "wheelie_bin_grey": (lambda: wheelie_bin("grey"), "Wheelie Bin (Grey)"),
    "wheelie_bin_green": (lambda: wheelie_bin("green"), "Wheelie Bin (Green)"),
    "wheelie_bin_blue": (lambda: wheelie_bin("blue"), "Wheelie Bin (Blue)"),
    "wheelie_bin_brown": (lambda: wheelie_bin("brown"), "Wheelie Bin (Brown)"),
    "communal_bin": (communal_bin, "Communal Bin (1100 L)"),
    "litter_bin": (litter_bin, "Litter Bin"),
    "dog_waste_bin": (dog_waste_bin, "Dog Waste Bin"),
    "grit_bin": (grit_bin, "Grit Bin"),
    "pillar_box": (pillar_box, "Pillar Box"),
    "phone_box": (phone_box, "Red Telephone Box"),
    "bus_shelter": (bus_shelter, "Bus Shelter"),
    "bus_stop_flag": (bus_stop_flag, "Bus Stop"),
    "bus_shelter_london": (bus_shelter_london, "Bus Shelter (London)"),
    "bench_metal": (lambda: bench("metal"), "Bench (Steel)"),
    "bench_wood": (lambda: bench("wood"), "Bench (Wood)"),
    "bollard_cast_iron": (lambda: bollard("cast_iron"), "Bollard (Cast Iron)"),
    "bollard_steel": (lambda: bollard("steel"), "Bollard (Steel)"),
    "keep_left_bollard": (keep_left_bollard, "Illuminated Keep Left Bollard"),
    "belisha_beacon": (belisha_beacon, "Belisha Beacon"),
    "pay_and_display": (pay_and_display, "Pay and Display Machine"),
    "parking_meter": (parking_meter, "Parking Meter"),
    "ev_charger": (ev_charger, "EV Charging Point"),
    "bike_stand": (bike_stand, "Cycle Stand"),
    "telecoms_cabinet": (telecoms_cabinet, "Telecoms Cabinet"),
    "controller_cabinet": (controller_cabinet, "Traffic Signal Controller Cabinet"),
    "feeder_pillar": (feeder_pillar, "Electricity Feeder Pillar"),
    "hydrant_marker": (hydrant_marker, "Fire Hydrant Marker"),
    "manhole_cover": (manhole_cover, "Manhole Cover"),
    "drain_grate": (drain_grate, "Drain Grate"),
    "street_light_led": (lambda: street_light("led"), "Street Light Lantern (LED)"),
    "street_light_sodium": (lambda: street_light("sodium"), "Street Light Lantern (Sodium)"),
    "gatso_camera": (gatso_camera, "Speed Camera (Gatso)"),
    "truvelo_camera": (truvelo_camera, "Speed Camera (Truvelo, forward facing)"),
    "specs_camera": (specs_camera, "Average Speed Camera (SPECS)"),
    "traffic_cone": (traffic_cone, "Traffic Cone"),
    "chapter8_barrier": (chapter8_barrier, "Road Works Barrier"),
    "road_closed_sign": (road_closed_sign, "Road Closed Sign"),
    "concrete_barrier": (concrete_barrier, "Concrete Step Barrier"),
    "emergency_phone": (emergency_phone, "Motorway Emergency Phone"),
    "marker_post": (marker_post, "Motorway Marker Post"),
    "card_reader": (card_reader, "Card Reader"),
    "top_up_machine": (top_up_machine, "Ticket & Top-up Machine"),
    "bus_stop_marking": (lambda: road_marking("bus_stop_marking"), "BUS STOP Road Marking"),
    "bus_lane_marking": (lambda: road_marking("bus_lane_marking"), "BUS LANE Road Marking"),
}


# real-world sizes for things that were built too small (sx, sy, sz)
SCALE = {
    "bus_shelter": (1.0, 1.32, 1.1),            # 2.4 m to the roof, room to stand inside
    "bus_shelter_london": (1.0, 1.32, 1.1),
    "belisha_beacon": (1.15, 1.35, 1.15),       # globe at about 2.6 m
    "pay_and_display": (1.12, 1.22, 1.12),      # 1.7 m
    "parking_meter": (1.1, 1.1, 1.1),
    "ev_charger": (1.1, 1.2, 1.1),              # 1.6 m
    "keep_left_bollard": (1.1, 1.1, 1.1),
    "hydrant_marker": (1.15, 1.15, 1.15),
    "grit_bin": (1.08, 1.08, 1.08),
    "dog_waste_bin": (1.05, 1.05, 1.05),
    "litter_bin": (1.05, 1.05, 1.05),
    "truvelo_camera": (1.1, 1.1, 1.1),
    "emergency_phone": (1.08, 1.08, 1.08),
    "traffic_cone": (1.0, 0.97, 1.0),
    "top_up_machine": (1.1, 1.15, 1.1),          # 1.75 m to the top of the hood
}


# Pavement-level items read small next to a player at true size, so they get a general boost
# on top of SCALE. Benches are also stretched to a real bench length (about 1.8 m).
STREET_BOOST = 1.2
BOOSTED = {"wheelie_bin_black", "wheelie_bin_grey", "wheelie_bin_green", "wheelie_bin_blue", "wheelie_bin_brown",
           "communal_bin", "litter_bin", "dog_waste_bin", "grit_bin", "bollard_cast_iron", "bollard_steel",
           "keep_left_bollard", "pay_and_display", "parking_meter", "ev_charger", "bike_stand", "telecoms_cabinet",
           "controller_cabinet", "feeder_pillar", "hydrant_marker", "traffic_cone", "emergency_phone", "marker_post",
           "bench_metal", "bench_wood", "road_closed_sign", "chapter8_barrier"}
SCALE.update({"bench_metal": (1.65, 1.0, 1.0), "bench_wood": (1.65, 1.0, 1.0), "pillar_box": (1.12, 1.12, 1.12)})


def scale_of(name):
    sx, sy, sz = SCALE.get(name, (1, 1, 1))
    if name in BOOSTED:
        sx, sy, sz = sx * STREET_BOOST, sy * STREET_BOOST, sz * STREET_BOOST
    return sx, sy, sz


def write_furniture(name, builder, tex_keys=None):
    els, textures, opts = builder()
    textures = dict(textures)
    textures["particle"] = next(iter(textures.values()))
    mdl = G.model(textures, els, cutout=opts.get("cutout", False))
    sx, sy, sz = scale_of(name)
    sy *= opts.get("scale_y", 1)
    if (sx, sy, sz) != (1, 1, 1):
        # Forge model transform, scaled about the middle of the block's floor
        mdl["transform"] = {"scale": [sx, sy, sz], "origin": [0.5, 0, 0.5]}
    G.write_json(G.ASSETS / f"models/block/furniture/{name}.json", mdl, compact=True)
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        v = {"model": f"{G.MOD_ID}:block/furniture/{name}"}
        if y:
            v["y"] = y
        variants[f"facing={facing}"] = v
    G.write_json(G.ASSETS / f"blockstates/{name}.json", {"variants": variants})
    # inventory icon: fit the model's real size into the slot instead of a fixed scale, so
    # small things (bollards, cones, markers) are not lost in the corner of the slot
    lo = [min(e["from"][i] for e in els) for i in range(3)]
    hi = [max(e["to"][i] for e in els) for i in range(3)]
    size = max((hi[0] - lo[0]) * sx, (hi[1] - lo[1]) * sy, (hi[2] - lo[2]) * sz) / 16
    s = round(min(1.0, max(0.22, 0.95 / max(size, 0.4))) * 0.68, 3)
    cy = ((lo[1] + hi[1]) / 2 * sy) / 16                     # centre of the model in blocks
    ty = round((0.5 - cy) * 16 * s, 2)
    G.write_json(G.ASSETS / f"models/item/{name}.json", {
        "parent": f"{G.MOD_ID}:block/furniture/{name}",
        "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, ty, 0], "scale": [s, s, s]},
                    "ground": {"translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
                    "fixed": {"scale": [0.5, 0.5, 0.5]},
                    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375] * 3},
                    "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.4] * 3},
                    "firstperson_lefthand": {"rotation": [0, 225, 0], "scale": [0.4] * 3}}})


# ----------------------------------------------------------------- fences

def fence_parts(kind):
    """(post elements, north side elements, textures)"""
    if kind == "palisade_fence":
        post = [box((7, 0, 7), (9, 24, 9), "#galv")]
        side = [box((7.6, 5, 0), (8.4, 6, 8), "#galv"), box((7.6, 18, 0), (8.4, 19, 8), "#galv")]
        for z in (0.4, 2.6, 4.8):
            side.append(box((7.8, 0, z), (8.2, 23, z + 1.3), "#galv"))
            for dz in (0, 0.5, 1.0):
                side.append(box((7.85, 23, z + dz), (8.15, 24.2, z + dz + 0.3), "#galv"))
        return post, side, {"galv": t("galv")}
    if kind == "black_railings":
        post = [box((7.2, 0, 7.2), (8.8, 17, 8.8), "#black"), box((7.5, 17, 7.5), (8.5, 18, 8.5), "#black")]
        side = [box((7.6, 2, 0), (8.4, 2.6, 8), "#black"), box((7.6, 14, 0), (8.4, 14.6, 8), "#black")]
        for z in (0.8, 2.8, 4.8, 6.8):
            side += [box((7.75, 0, z), (8.25, 15.6, z + 0.5), "#black"),
                     box((7.8, 15.6, z + 0.05), (8.2, 16.4, z + 0.45), "#black", rotation=("y", 45, (8, 16, z + 0.25)))]
        return post, side, {"black": t("black")}
    if kind == "pedestrian_guardrail":
        post = [box((7.1, 0, 7.1), (8.9, 16, 8.9), "#galv")]
        side = [box((7.4, 14.8, 0), (8.6, 16, 8), "#galv"), box((7.5, 2, 0), (8.5, 2.8, 8), "#galv")]
        for z in (0.5, 1.8, 3.1, 4.4, 5.7):
            side.append(box((7.85, 2.8, z), (8.15, 14.8, z + 0.4), "#galv"))
        return post, side, {"galv": t("galv")}
    if kind == "close_board_fence":
        post = [box((6.8, 0, 6.8), (9.2, 28, 9.2), "#concrete")]
        side = [box((7.3, 0, 0), (8.7, 2.5, 8), "#concrete"),
                box((7.5, 2.5, 0), (8.5, 28, 8), "#wood_vertical"),
                box((7.2, 28, 0), (8.8, 28.6, 8), "#wood")]
        return post, side, {"concrete": t("concrete"), "wood_vertical": t("wood_vertical"), "wood": t("wood")}
    if kind == "armco_barrier":
        post = [box((7.2, 0, 7.2), (8.8, 9, 8.8), "#galv"), box((7.6, 5.5, 7.6), (8.4, 10.5, 8.4), "#w_beam")]
        side = [box((7.7, 5.5, 0), (8.3, 10.5, 8), "#w_beam", faces=("east", "west", "up", "down"))]
        return post, side, {"galv": t("galv"), "w_beam": t("w_beam")}
    # heras temporary fencing
    post = [box((4.5, 0, 5), (11.5, 2.2, 11), "#rubber"), box((7.6, 2.2, 7.6), (8.4, 31, 8.4), "#galv")]
    mesh = box((7.95, 2.6, 0), (8.05, 30.6, 8), "#mesh", faces=("east", "west"))
    side = [mesh, box((7.75, 30.6, 0), (8.25, 31.2, 8), "#galv"), box((7.75, 2.0, 0), (8.25, 2.6, 8), "#galv")]
    return post, side, {"rubber": t("rubber"), "galv": t("galv"), "mesh": t("mesh")}


FENCES = {
    "palisade_fence": "Palisade Fence",
    "black_railings": "Black Railings",
    "pedestrian_guardrail": "Pedestrian Guardrail",
    "close_board_fence": "Close Board Fence",
    "heras_fence": "Heras Temporary Fencing",
    "armco_barrier": "Armco Crash Barrier",
}


def write_fence(name):
    post, side, textures = fence_parts(name)
    textures = dict(textures, particle=next(iter(textures.values())))
    base = f"{G.MOD_ID}:block/fence/{name}"
    G.write_json(G.ASSETS / f"models/block/fence/{name}_post.json", G.model(textures, post, cutout=True), compact=True)
    G.write_json(G.ASSETS / f"models/block/fence/{name}_side.json", G.model(textures, side, cutout=True), compact=True)
    parts = [{"apply": {"model": base + "_post"}}]
    for direction, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        a = {"model": base + "_side", "uvlock": False}
        if y:
            a["y"] = y
        parts.append({"when": {direction: "true"}, "apply": a})
    G.write_json(G.ASSETS / f"blockstates/{name}.json", {"multipart": parts})
    south = [G.rotate_y90(G.rotate_y90(e)) for e in side]
    inv = G.model(textures, post + side + south, cutout=True)
    inv["display"] = {"gui": {"rotation": [30, 135, 0], "scale": [0.5] * 3},
                      "ground": {"translation": [0, 3, 0], "scale": [0.25] * 3},
                      "fixed": {"scale": [0.5] * 3},
                      "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375] * 3},
                      "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.4] * 3}}
    G.write_json(G.ASSETS / f"models/item/{name}.json", inv)


# ----------------------------------------------------------------- road sign plates

ROAD_SIGNS = {
    # id: (texture painter, plate size px, name)
    "speed_20_sign": (lambda: F.speed_sign(20), 10.5, "Speed Limit 20 Sign"),
    "speed_30_sign": (lambda: F.speed_sign(30), 10.5, "Speed Limit 30 Sign"),
    "speed_40_sign": (lambda: F.speed_sign(40), 10.5, "Speed Limit 40 Sign"),
    "speed_50_sign": (lambda: F.speed_sign(50), 10.5, "Speed Limit 50 Sign"),
    "speed_60_sign": (lambda: F.speed_sign(60), 10.5, "Speed Limit 60 Sign"),
    "speed_70_sign": (lambda: F.speed_sign(70), 10.5, "Speed Limit 70 Sign"),
    "national_speed_limit_sign": (F.national_speed, 10.5, "National Speed Limit Sign"),
    "no_entry_sign": (F.no_entry, 10.5, "No Entry Sign"),
    "give_way_sign": (F.give_way, 12, "Give Way Sign"),
    "stop_sign": (F.stop_sign, 11, "Stop Sign"),
    "one_way_sign": (F.one_way, 12, "One Way Sign"),
    "keep_left_sign": (F.keep_left, 10, "Keep Left Sign"),
    "parking_sign": (F.parking, 9, "Parking Sign"),
    "pay_at_machine_sign": (F.pay_at_machine, 9, "Pay at Machine Sign"),
    "disabled_parking_sign": (F.disabled_parking, 10, "Disabled Parking Sign"),
    "traffic_signals_ahead_sign": (lambda: F.warning("traffic_signals"), 12, "Traffic Signals Ahead Sign"),
    "pedestrian_crossing_sign": (lambda: F.warning("pedestrian_crossing"), 12, "Pedestrian Crossing Sign"),
    "children_sign": (lambda: F.warning("children"), 12, "Children Crossing Sign"),
    "roadworks_sign": (lambda: F.warning("roadworks"), 12, "Road Works Sign"),
    "speed_camera_sign": (F.speed_camera_sign, 12, "Speed Camera Sign"),
}
ROAD_SIGNS.update(B.ROAD_SIGNS)

SIGN_FACE_Z = 10.2
SIGN_BACK_Z = 10.45


def back_of(img):
    """Grey sign back with the face's outline."""
    a = np.asarray(img)[..., 3]
    rgb = np.zeros(a.shape + (3,)) + np.array([128, 131, 134.0]) + np.random.default_rng(7).normal(0, 2, a.shape + (1,))
    return G.T.to_image(rgb, np.where(a > 127, 255, 0))


def roadsign_parts(name, tex):
    painter, size, _ = ROAD_SIGNS[name]
    face_img = painter()
    face_ref = G.save(face_img, f"roadsign/{name}")
    back_ref = G.save(back_of(face_img), f"roadsign/{name}_back")
    h = size / 2
    plate = box((8 - h, 8 - h, SIGN_FACE_Z), (8 + h, 8 + h, SIGN_BACK_Z), "#back", faces=("north", "south"),
                front="#face", full_front_uv=True)
    plate["faces"]["south"]["uv"] = [16, 0, 0, 16]
    textures = {"particle": face_ref, "face": face_ref, "back": back_ref, "metal": tex["grey_metal"]}
    return [plate], textures, (8 - h * 0.6, 8 + h * 0.6 - 0.7)


# ----------------------------------------------------------------- entry point

TEXG = {}


def generate(gmod, tex):
    global G
    G = gmod
    TEXG.update(tex)
    make_textures()
    for name, (builder, _) in FURNITURE.items():
        write_furniture(name, builder)
    for name in FENCES:
        write_fence(name)


def lang():
    out = {f"block.{G.MOD_ID}.{n}": title for n, (_, title) in FURNITURE.items()}
    out.update({f"block.{G.MOD_ID}.{n}": title for n, title in FENCES.items()})
    out.update({f"block.{G.MOD_ID}.{n}": title for n, (_, _, title) in ROAD_SIGNS.items()})
    return out

