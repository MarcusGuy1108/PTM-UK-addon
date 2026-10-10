"""UK building and paving blocks (full blocks + slabs). Must match BuildingMaterial.java."""
import math

import numpy as np
from PIL import Image, ImageDraw

G = None
S = 64
rng = np.random.default_rng(42)


def img_from(rgb):
    return Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8), "RGB").convert("RGBA")


def noise(scale, shape=(S, S, 1)):
    return rng.normal(0, scale, shape)


def bond(brick_w, brick_h, mortar, colours, mortar_col, offset=0.5, jitter=10, speckle=0.0):
    """Stretcher bond: rows of bricks offset by half a brick."""
    rgb = np.zeros((S, S, 3)) + np.array(mortar_col, float)
    rows = S // brick_h
    for r in range(rows):
        y0 = r * brick_h
        shift = int(brick_w * offset) if r % 2 else 0
        for c in range(-1, S // brick_w + 1):
            x0 = c * brick_w + shift
            base = np.array(colours[rng.integers(len(colours))], float) + rng.normal(0, jitter, 3)
            for x in range(x0, x0 + brick_w - mortar):
                if 0 <= x < S:
                    rgb[y0:y0 + brick_h - mortar, x] = base
    rgb += noise(5)
    if speckle:
        dots = rng.random((S, S)) < speckle
        rgb[dots] *= 0.55
    return img_from(rgb)


def paving_slabs():
    rgb = np.zeros((S, S, 3))
    for i in range(2):
        for j in range(2):
            base = np.array([170, 168, 162.0]) + rng.normal(0, 6)
            rgb[i * 32:(i + 1) * 32, j * 32:(j + 1) * 32] = base
    rgb += noise(5)
    rgb[0, :] = rgb[:, 0] = rgb[31, :] = rgb[:, 31] = (110, 108, 104)
    rgb[32, :] = rgb[:, 32] = (110, 108, 104)
    return img_from(rgb)


def tactile(base, kind="blister"):
    rgb = np.zeros((S, S, 3)) + np.array(base, float) + noise(5)
    yy, xx = np.mgrid[0:S, 0:S].astype(float)
    if kind == "blister":
        pitch = S / 6
        cx = (xx % pitch) - pitch / 2
        cy = (yy % pitch) - pitch / 2
        r = np.sqrt(cx ** 2 + cy ** 2)
        dome = np.clip(1 - r / (pitch * 0.36), 0, 1)
        light = dome * (1 - (cx + cy) / (pitch * 0.7))
        rgb += (light * 40)[..., None] - ((r < pitch * 0.38) & (cx + cy > 0))[..., None] * 18
    else:
        pitch = S / 6
        c = (yy % pitch) - pitch / 2
        rib = np.cos(c / pitch * math.pi * 2) * 22
        rgb += rib[..., None]
    rgb[0, :] = rgb[:, 0] = np.array(base) * 0.6
    return img_from(rgb)


def setts():
    return bond(16, 11, 2, [(120, 122, 126), (100, 102, 106), (138, 136, 134), (90, 92, 96)], (60, 60, 62), jitter=8)


def kerb():
    rgb = np.zeros((S, S, 3)) + np.array([176, 174, 168.0]) + noise(6)
    rgb[:, 31:33] = (120, 118, 114)
    rgb[0:3, :] *= 1.08
    return img_from(rgb)


def tarmac(base):
    rgb = np.zeros((S, S, 3)) + np.array(base, float) + noise(9)
    stones = rng.random((S, S)) < 0.06
    rgb[stones] += rng.normal(25, 10, (int(stones.sum()), 1))
    return img_from(rgb)


def pebbledash():
    rgb = np.zeros((S, S, 3)) + np.array([214, 208, 196.0]) + noise(6)
    for _ in range(900):
        x, y = rng.integers(0, S, 2)
        shade = rng.normal(0, 30)
        c = np.array([180, 168, 150.0]) + shade
        rgb[y, x] = c
        if rng.random() < 0.5 and x + 1 < S:
            rgb[y, x + 1] = c * 0.8
    return img_from(rgb)


def render():
    return img_from(np.zeros((S, S, 3)) + np.array([236, 234, 228.0]) + noise(3) +
                    (np.sin(np.mgrid[0:S, 0:S][1] / 3.0) * 1.5)[..., None])


def portland():
    rgb = np.zeros((S, S, 3)) + np.array([222, 214, 190.0]) + noise(5)
    for _ in range(40):
        x, y = rng.integers(0, S, 2)
        rgb[y, x:x + 2] -= 25
    rgb[31:32, :] = rgb[63:64, :] = (176, 168, 150)
    rgb[0:32, 0:1] = rgb[32:64, 32:33] = (176, 168, 150)
    return img_from(rgb)


def roof_slate():
    """Welsh slate: dark blue-grey slates in courses, each overlapping the one below."""
    rgb = np.zeros((S, S, 3)) + np.array([30, 32, 38.0])
    course = 16
    for r in range(S // course):
        y0 = r * course
        x = -int(rng.integers(0, 12)) if r % 2 else 0
        while x < S:
            wdt = int(rng.integers(11, 15))
            base = np.array([70, 74, 86.0]) * rng.normal(1.0, 0.08) + rng.normal(0, 2, 3)
            x0, x1 = max(x, 0), min(x + wdt - 1, S)
            if x1 > x0:
                rgb[y0:y0 + course - 2, x0:x1] = base
                rgb[y0 + course - 3:y0 + course - 2, x0:x1] = base * 0.7          # thick lower edge
                rgb[y0:y0 + 2, x0:x1] = base * 0.82                                  # shadow of the course above
            x += wdt
    rgb += noise(3)
    return img_from(rgb)


def roof_clay_tiles():
    """Plain clay tiles: small red-brown tiles in tight courses, each course shadowing the next."""
    rgb = np.zeros((S, S, 3))
    course = 8
    for r in range(S // course):
        y0 = r * course
        x = -4 if r % 2 else 0
        while x < S:
            wdt = 8
            base = np.array([142, 70, 50.0]) * rng.normal(1.0, 0.07) + rng.normal(0, 3, 3)
            x0, x1 = max(x, 0), min(x + wdt, S)
            if x1 > x0:
                for k in range(course):
                    shade = 0.62 if k < 2 else 1.0 + 0.04 * (k - 2)       # shadow under the course above
                    rgb[y0 + k, x0:x1] = base * shade
                if x1 - 1 >= 0 and x1 - 1 < S:
                    rgb[y0 + 2:y0 + course, x1 - 1] *= 0.7                 # joint between tiles
            x += wdt
    rgb += noise(4)
    return img_from(rgb)


def roof_concrete_tiles():
    """Interlocking concrete tiles: brown, with a rounded roll every half tile."""
    rgb = np.zeros((S, S, 3)) + np.array([104, 78, 62.0]) + noise(5)
    xx = np.mgrid[0:S, 0:S][1]
    roll = np.cos(xx / 16.0 * 2 * math.pi)
    rgb += (roll * 16)[..., None]
    for r in range(S // 16):
        y0 = r * 16
        rgb[y0:y0 + 3, :] *= 0.62
        rgb[y0 + 3:y0 + 5, :] *= 0.86
    for _ in range(300):
        x, y = rng.integers(0, S, 2)
        rgb[y, x] += rng.normal(0, 18)
    return img_from(rgb)


def faience(base):
    """Glazed faience tiles on pub and shop fronts: glossy blocks in courses, thin dark joints."""
    rgb = np.zeros((S, S, 3)) + np.array(base, float) * 0.55
    th, tw = 16, 32
    yy, xx = np.mgrid[0:S, 0:S]
    for r in range(S // th):
        for c in range(-1, S // tw + 1):
            x0 = c * tw + (tw // 2 if r % 2 else 0)
            y0 = r * th
            col = np.array(base, float) * rng.normal(1.0, 0.06)
            m = (xx >= x0 + 1) & (xx < x0 + tw) & (yy >= y0 + 1) & (yy < y0 + th)
            shine = 1.0 + 0.18 * np.exp(-((xx - x0 - 8) ** 2 / 30.0 + (yy - y0 - 4) ** 2 / 6.0))
            rgb[m] = (col[None, :] * shine[m][:, None])
    rgb += noise(2)
    return img_from(rgb)


MATERIALS = {
    "paving_slabs": ("Concrete Paving Slabs", paving_slabs),
    "block_paving_red": ("Block Paving (Red)", lambda: bond(16, 8, 1, [(150, 64, 48), (130, 56, 44), (164, 82, 58)], (90, 86, 80))),
    "block_paving_grey": ("Block Paving (Grey)", lambda: bond(16, 8, 1, [(128, 128, 128), (110, 110, 112), (142, 140, 138)], (80, 80, 80))),
    "tactile_paving_buff": ("Tactile Paving (Buff)", lambda: tactile((196, 170, 112))),
    "tactile_paving_red": ("Tactile Paving (Red)", lambda: tactile((160, 62, 52))),
    "tactile_paving_corduroy": ("Tactile Paving (Corduroy)", lambda: tactile((196, 170, 112), "corduroy")),
    "granite_setts": ("Granite Setts", setts),
    "concrete_kerb": ("Concrete Kerb", kerb),
    "tarmac": ("Tarmac", lambda: tarmac((52, 52, 54))),
    "tarmac_red": ("Red Tarmac", lambda: tarmac((128, 52, 44))),
    "red_brick": ("Red Brick", lambda: bond(16, 8, 1, [(156, 66, 46), (140, 58, 42), (170, 80, 56), (124, 52, 40)], (190, 184, 172))),
    "london_stock_brick": ("London Stock Brick", lambda: bond(16, 8, 1, [(206, 178, 120), (190, 160, 104), (176, 146, 96)], (196, 190, 178), speckle=0.05)),
    "blue_engineering_brick": ("Blue Engineering Brick", lambda: bond(16, 8, 1, [(54, 52, 66), (44, 44, 56), (62, 58, 74)], (120, 118, 114), jitter=5)),
    "pebbledash": ("Pebbledash", pebbledash),
    "white_render": ("White Render", render),
    "portland_stone": ("Portland Stone", portland),
    "green_faience": ("Green Faience Tiles", lambda: faience((34, 96, 62))),
    "burgundy_faience": ("Burgundy Faience Tiles", lambda: faience((112, 30, 40))),
    "roof_slate": ("Welsh Slate Roof", roof_slate),
    "roof_clay_tiles": ("Clay Roof Tiles", roof_clay_tiles),
    "roof_concrete_tiles": ("Concrete Roof Tiles", roof_concrete_tiles),
}


def stairs_blockstate(mid, name):
    """The same variants as vanilla's stairs (checked against oak_stairs.json)."""
    base = {"east": 0, "south": 90, "west": 180, "north": 270}
    out = {}
    for facing, y0 in base.items():
        for half in ("bottom", "top"):
            for shape in ("straight", "inner_left", "inner_right", "outer_left", "outer_right"):
                model = f"{mid}:block/building/{name}_stairs" + ("_inner" if shape.startswith("inner") else
                                                                 "_outer" if shape.startswith("outer") else "")
                if half == "bottom":
                    y = y0 - 90 if shape.endswith("left") else y0
                    x = 0
                else:
                    y = y0 + 90 if shape.endswith("right") else y0
                    x = 180
                y %= 360
                v = {"model": model}
                if x:
                    v["x"] = x
                if y:
                    v["y"] = y
                if x or y:
                    v["uvlock"] = True
                out[f"facing={facing},half={half},shape={shape}"] = v
    return {"variants": out}


def generate(gmod):
    global G
    G = gmod
    mid = G.MOD_ID
    for name, (_, painter) in MATERIALS.items():
        tex = G.save(painter(), f"building/{name}")
        G.write_json(G.ASSETS / f"models/block/building/{name}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": tex}})
        slab_tex = {"bottom": tex, "top": tex, "side": tex}
        G.write_json(G.ASSETS / f"models/block/building/{name}_slab.json", {"parent": "minecraft:block/slab", "textures": slab_tex})
        G.write_json(G.ASSETS / f"models/block/building/{name}_slab_top.json", {"parent": "minecraft:block/slab_top", "textures": slab_tex})
        G.write_json(G.ASSETS / f"blockstates/{name}.json", {"variants": {"": {"model": f"{mid}:block/building/{name}"}}})
        G.write_json(G.ASSETS / f"blockstates/{name}_slab.json", {"variants": {
            "type=bottom": {"model": f"{mid}:block/building/{name}_slab"},
            "type=top": {"model": f"{mid}:block/building/{name}_slab_top"},
            "type=double": {"model": f"{mid}:block/building/{name}"}}})
        G.write_json(G.ASSETS / f"models/item/{name}.json", {"parent": f"{mid}:block/building/{name}"})
        G.write_json(G.ASSETS / f"models/item/{name}_slab.json", {"parent": f"{mid}:block/building/{name}_slab"})
        for suffix, parent in (("", "stairs"), ("_inner", "inner_stairs"), ("_outer", "outer_stairs")):
            G.write_json(G.ASSETS / f"models/block/building/{name}_stairs{suffix}.json",
                         {"parent": f"minecraft:block/{parent}", "textures": slab_tex})
        G.write_json(G.ASSETS / f"blockstates/{name}_stairs.json", stairs_blockstate(mid, name))
        G.write_json(G.ASSETS / f"models/item/{name}_stairs.json", {"parent": f"{mid}:block/building/{name}_stairs"})
    generate_shop_sign(mid)
    generate_windows(mid)
    (G.ASSETS / "textures/item").mkdir(parents=True, exist_ok=True)
    prefab_tool_icon().save(G.ASSETS / "textures/item/prefab_tool.png")
    G.write_json(G.ASSETS / "models/item/prefab_tool.json", {"parent": "minecraft:item/generated",
                                                             "textures": {"layer0": f"{mid}:item/prefab_tool"}})


# ---------------------------------------------------------------- framed windows (glass panes)

def _glass(tint=(170, 205, 225), alpha=86, sheen=True):
    """Window glass: a pale tint, a little sky reflection at the top and a diagonal sheen."""
    yy, xx = np.mgrid[0:S, 0:S].astype(float)
    rgb = np.zeros((S, S, 3)) + np.array(tint, float)
    rgb += (1 - yy / S)[..., None] * 18
    a = np.zeros((S, S)) + alpha
    if sheen:
        band = np.exp(-(((xx + yy) - S * 0.8) / 7.0) ** 2) + 0.6 * np.exp(-(((xx + yy) - S * 1.05) / 4.0) ** 2)
        rgb += band[..., None] * 40
        a += band * 50
    return rgb, a


def _frame(rgb, a, colour, t, top=None, bottom=None):
    c = np.array(colour, float)
    tb = t if top is None else top
    bb = t if bottom is None else bottom
    for sl in ((slice(0, tb), slice(None)), (slice(S - bb, S), slice(None)), (slice(None), slice(0, t)), (slice(None), slice(S - t, S))):
        rgb[sl] = c
        a[sl] = 255
    # a slightly darker inner edge so the frame reads as moulded
    rgb[tb:tb + 1, t:S - t] = c * 0.8
    rgb[t:S - bb, t:t + 1] = c * 0.86


def _bars(rgb, a, colour, cols, rows, w):
    c = np.array(colour, float)
    for i in range(1, cols):
        x = int(S * i / cols)
        rgb[:, x - w // 2:x + (w + 1) // 2] = c
        a[:, x - w // 2:x + (w + 1) // 2] = 255
    for j in range(1, rows):
        y = int(S * j / rows)
        rgb[y - w // 2:y + (w + 1) // 2, :] = c
        a[y - w // 2:y + (w + 1) // 2, :] = 255


def _rgba(rgb, a):
    out = np.dstack([np.clip(rgb, 0, 255), np.clip(a, 0, 255)]).astype(np.uint8)
    return Image.fromarray(out, "RGBA")


WHITE = (238, 238, 232)


def window_upvc():
    rgb, a = _glass()
    _frame(rgb, a, WHITE, 6, top=7, bottom=8)
    return _rgba(rgb, a)


def window_sash():
    rgb, a = _glass()
    _frame(rgb, a, WHITE, 5, top=5, bottom=9)        # heavier bottom rail, like a sash's meeting rail
    _bars(rgb, a, WHITE, 2, 1, 3)
    return _rgba(rgb, a)


def window_georgian():
    rgb, a = _glass()
    _frame(rgb, a, WHITE, 4)
    _bars(rgb, a, WHITE, 2, 3, 3)
    return _rgba(rgb, a)


def window_leaded():
    """1930s leaded lights: small diamonds of lead in a white frame."""
    rgb, a = _glass((190, 205, 200), 96)
    yy, xx = np.mgrid[0:S, 0:S]
    lead = ((xx + yy) % 16 == 0) | ((xx - yy) % 16 == 0)
    rgb[lead] = (60, 62, 66)
    a[lead] = 255
    _frame(rgb, a, WHITE, 5)
    return _rgba(rgb, a)


def window_shop():
    rgb, a = _glass((180, 210, 225), 60)
    _frame(rgb, a, (28, 28, 30), 2)
    return _rgba(rgb, a)


def window_office():
    """Curtain wall: blue-green reflective glass between dark grey mullions and transoms."""
    yy, xx = np.mgrid[0:S, 0:S].astype(float)
    rgb = np.zeros((S, S, 3)) + np.array([70, 110, 130.0]) + (yy / S)[..., None] * np.array([-20, -10, 10.0])
    a = np.zeros((S, S)) + 150
    band = np.exp(-(((xx + yy) - S * 0.7) / 9.0) ** 2)
    rgb += band[..., None] * 50
    _frame(rgb, a, (58, 62, 68), 2)
    return _rgba(rgb, a)


def window_grey():
    rgb, a = _glass()
    _frame(rgb, a, (64, 68, 74), 5, top=6, bottom=7)
    return _rgba(rgb, a)


WINDOWS = {
    "upvc_window": ("White UPVC Window", window_upvc, WHITE),
    "sash_window": ("Sash Window", window_sash, WHITE),
    "georgian_window": ("Georgian Window", window_georgian, WHITE),
    "leaded_window": ("Leaded Window", window_leaded, WHITE),
    "shop_window": ("Shop Window", window_shop, (28, 28, 30)),
    "office_glazing": ("Office Glazing", window_office, (58, 62, 68)),
    "grey_framed_window": ("Grey Framed Window", window_grey, (64, 68, 74)),
}


def generate_windows(mid):
    for name, (_, painter, edge_col) in WINDOWS.items():
        tex = G.save(painter(), f"building/{name}")
        edge = G.save(_rgba(np.zeros((S, S, 3)) + np.array(edge_col, float), np.zeros((S, S)) + 255), f"building/{name}_edge")
        for part in ("post", "side", "side_alt", "noside", "noside_alt"):
            textures = {"pane": tex, "particle": tex}
            if part in ("post", "side", "side_alt"):
                textures["edge"] = edge
            G.write_json(G.ASSETS / f"models/block/building/{name}_{part}.json",
                         {"parent": f"minecraft:block/template_glass_pane_{part}", "textures": textures,
                          "render_type": "minecraft:translucent"})
        m = f"{mid}:block/building/{name}"
        G.write_json(G.ASSETS / f"blockstates/{name}.json", {"multipart": [
            {"apply": {"model": f"{m}_post"}},
            {"apply": {"model": f"{m}_side"}, "when": {"north": "true"}},
            {"apply": {"model": f"{m}_side", "y": 90}, "when": {"east": "true"}},
            {"apply": {"model": f"{m}_side_alt"}, "when": {"south": "true"}},
            {"apply": {"model": f"{m}_side_alt", "y": 90}, "when": {"west": "true"}},
            {"apply": {"model": f"{m}_noside"}, "when": {"north": "false"}},
            {"apply": {"model": f"{m}_noside_alt"}, "when": {"east": "false"}},
            {"apply": {"model": f"{m}_noside_alt", "y": 90}, "when": {"south": "false"}},
            {"apply": {"model": f"{m}_noside", "y": 270}, "when": {"west": "false"}}]})
        G.write_json(G.ASSETS / f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": tex}})


def shop_sign_board():
    """Edge and back of a painted fascia board (the front is painted live by ShopSignRenderer)."""
    rgb = np.zeros((S, S, 3)) + np.array([38, 40, 46.0]) + noise(3)
    rgb[:, ::8] *= 0.9
    return img_from(rgb)


def prefab_tool_icon():
    """A little terraced house with a builder's ruler across the corner."""
    n = 32
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.polygon([(4, 14), (16, 4), (28, 14)], fill=(52, 56, 70, 255), outline=(30, 32, 40, 255))       # slate roof
    d.rectangle((6, 14, 26, 29), fill=(196, 168, 112, 255), outline=(120, 96, 60, 255))               # stock brick
    d.rectangle((21, 5, 23, 10), fill=(150, 70, 50, 255))                                              # chimney
    d.rectangle((8, 17, 14, 23), fill=(238, 238, 232, 255))                                            # bay
    d.rectangle((9, 18, 13, 22), fill=(130, 180, 210, 255))
    d.rectangle((17, 19, 21, 29), fill=(110, 30, 40, 255))                                             # front door
    d.rectangle((8, 26, 15, 29), fill=(150, 70, 50, 255))
    d.line([(2, 30), (13, 19)], fill=(240, 200, 60, 255), width=3)                                     # ruler
    for i in range(3, 13, 3):
        d.point((2 + i, 30 - i - 1), fill=(60, 50, 20, 255))
    return img


def generate_shop_sign(mid):
    tex = G.save(shop_sign_board(), "building/shop_sign_board")
    G.write_json(G.ASSETS / "models/block/building/shop_sign.json", {
        "parent": "minecraft:block/block",
        "textures": {"particle": tex, "board": tex},
        "elements": [{"from": [0, 0, 0], "to": [16, 16, 3], "faces": {
            f: {"texture": "#board", "uv": uv} for f, uv in (("north", [0, 0, 16, 16]), ("south", [0, 0, 16, 16]),
                                                           ("east", [0, 0, 3, 16]), ("west", [13, 0, 16, 16]),
                                                           ("up", [0, 0, 16, 3]), ("down", [0, 13, 16, 16]))}}]})
    G.write_json(G.ASSETS / "blockstates/shop_sign.json", {"variants": {
        f"facing={f}": ({"model": f"{mid}:block/building/shop_sign", "y": y} if y else {"model": f"{mid}:block/building/shop_sign"})
        for f, y in (("south", 0), ("west", 90), ("north", 180), ("east", 270))}})
    G.write_json(G.ASSETS / "models/item/shop_sign.json", {"parent": f"{mid}:block/building/shop_sign"})


def block_ids():
    return list(MATERIALS) + [n + "_slab" for n in MATERIALS] + [n + "_stairs" for n in MATERIALS] + ["shop_sign"] + list(WINDOWS)


def slab_loot(full):
    return {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": full, "functions": [
        {"function": "minecraft:set_count", "count": 2, "add": False,
         "conditions": [{"condition": "minecraft:block_state_property", "block": full,
                         "properties": {"type": "double"}}]},
        {"function": "minecraft:explosion_decay"}]}]}]}


def lang(gmod):
    out = {}
    for n, (title, _) in MATERIALS.items():
        out[f"block.{gmod.MOD_ID}.{n}"] = title
        out[f"block.{gmod.MOD_ID}.{n}_slab"] = title + " Slab"
        out[f"block.{gmod.MOD_ID}.{n}_stairs"] = title + " Stairs"
    out[f"block.{gmod.MOD_ID}.shop_sign"] = "Shop Sign (Fascia)"
    for n, (title, _, _) in WINDOWS.items():
        out[f"block.{gmod.MOD_ID}.{n}"] = title
    out[f"item.{gmod.MOD_ID}.prefab_tool"] = "Prefab Tool (British Buildings)"
    out["key.ptmuk.prefab_rotate"] = "Turn prefab building"
    out["key.categories.ptmuk"] = "PTM UK Addon"
    out["itemGroup.ptmuk.building"] = "UK Buildings"
    return out
