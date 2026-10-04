"""Assets for the editable direction signs (drawn by DirectionSignRenderer in game).

The block model is particle-only; the inventory icon is a small painted sign per scheme.
"""
from PIL import Image, ImageDraw

# id -> (title, background, legend/border colour). Must match SignScheme / ModBlocks.
SIGNS = {
    "direction_sign_local": ("Direction Sign (Local, White)", (248, 248, 246), (17, 17, 17)),
    "direction_sign_primary": ("Direction Sign (Primary, Green)", (0, 112, 60), (248, 248, 246)),
    "direction_sign_motorway": ("Direction Sign (Motorway, Blue)", (0, 87, 160), (248, 248, 246)),
    "direction_sign_tourist": ("Direction Sign (Tourist, Brown)", (107, 58, 30), (248, 248, 246)),
    "direction_sign_diversion": ("Direction Sign (Temporary, Yellow)", (255, 204, 0), (17, 17, 17)),
    "street_name_sign": ("Street Name Sign", (248, 248, 246), (17, 17, 17)),
}


def icon(name, bg, fg):
    s = 8  # paint at 128 px, downsample to 16
    img = Image.new("RGBA", (16 * s, 16 * s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    grey = (150, 154, 158, 255)
    if name == "street_name_sign":
        d.rounded_rectangle((4, 40, 124, 92), 10, fill=fg + (255,))
        d.rounded_rectangle((10, 46, 118, 86), 6, fill=bg + (255,))
        for x0, x1 in ((22, 50), (56, 74), (80, 106)):
            d.rectangle((x0, 60, x1, 72), fill=fg + (255,))
    else:
        d.rectangle((26, 80, 34, 127), fill=grey)
        d.rectangle((94, 80, 102, 127), fill=grey)
        d.rounded_rectangle((2, 8, 125, 96), 12, fill=fg + (255,))
        d.rounded_rectangle((8, 14, 119, 90), 8, fill=bg + (255,))
        # ahead + left + right junction diagram
        d.rectangle((58, 50, 70, 86), fill=fg + (255,))
        d.rectangle((24, 44, 104, 54), fill=fg + (255,))
        d.polygon([(64, 22), (50, 40), (78, 40)], fill=fg + (255,))
        d.rectangle((60, 36, 68, 50), fill=fg + (255,))
        d.polygon([(14, 49), (28, 38), (28, 60)], fill=fg + (255,))
        d.polygon([(114, 49), (100, 38), (100, 60)], fill=fg + (255,))
        if name == "direction_sign_primary":
            d.rectangle((80, 66, 108, 78), fill=(255, 204, 0, 255))
    return img.resize((16, 16), Image.LANCZOS)


def generate(G):
    item_tex = G.ASSETS / "textures/item"
    item_tex.mkdir(parents=True, exist_ok=True)
    bus_stop_icon().save(item_tex / "bus_stop_flag.png")
    G.write_json(G.ASSETS / "models/block/bus_stop_flag.json", {"textures": {"particle": f"{G.MOD_ID}:block/pole/galvanised"}})
    G.write_json(G.ASSETS / "blockstates/bus_stop_flag.json", {"variants": {"": {"model": f"{G.MOD_ID}:block/bus_stop_flag"}}})
    G.write_json(G.ASSETS / "models/item/bus_stop_flag.json",
                 {"parent": "minecraft:item/generated", "textures": {"layer0": f"{G.MOD_ID}:item/bus_stop_flag"}})
    for name, (_, bg, fg) in SIGNS.items():
        icon(name, bg, fg).save(item_tex / f"{name}.png")
        G.write_json(G.ASSETS / f"models/block/{name}.json",
                     {"textures": {"particle": f"{G.MOD_ID}:block/pole/galvanised"}})
        G.write_json(G.ASSETS / f"blockstates/{name}.json", {"variants": {"": {"model": f"{G.MOD_ID}:block/{name}"}}})
        G.write_json(G.ASSETS / f"models/item/{name}.json",
                     {"parent": "minecraft:item/generated", "textures": {"layer0": f"{G.MOD_ID}:item/{name}"}})


def bus_stop_icon():
    s = 8
    img = Image.new("RGBA", (16 * s, 16 * s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle((16, 6, 26, 127), fill=(180, 184, 188, 255))
    d.rectangle((14, 2, 28, 10), fill=(216, 32, 42, 255))
    d.rectangle((30, 14, 122, 104), fill=(90, 94, 98, 255))
    d.rectangle((33, 17, 119, 56), fill=(216, 32, 42, 255))
    cx, cy, r = 76, 36, 14
    d.polygon([(cx, cy - r), (cx + 12, cy - 7), (cx + 12, cy + 7), (cx, cy + r), (cx - 12, cy + 7), (cx - 12, cy - 7)], fill=(250, 250, 248, 255))
    d.rectangle((33, 57, 119, 74), fill=(39, 53, 90, 255))
    d.rectangle((33, 75, 119, 86), fill=(230, 232, 234, 255))
    for x in (36, 64, 92):
        d.rectangle((x, 88, x + 24, 101), fill=(250, 250, 248, 255))
    return img.resize((16, 16), Image.LANCZOS)


def lang(G):
    out = {f"block.{G.MOD_ID}.{n}": title for n, (title, _, _) in SIGNS.items()}
    out[f"block.{G.MOD_ID}.bus_stop_flag"] = "Bus Stop (London style)"
    return out
