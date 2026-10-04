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
}


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


def block_ids():
    return list(MATERIALS) + [n + "_slab" for n in MATERIALS]


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
    out["itemGroup.ptmuk.building"] = "UK Building Blocks"
    return out
