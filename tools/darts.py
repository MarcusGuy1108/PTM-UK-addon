"""Darts: the darts' icon, the dart's texture in flight (an arrow's layout, for ArrowRenderer),
the caller's voice lines in sounds.json (the .ogg files are made by darts_caller_voice.py), and
the names."""
from PIL import Image, ImageDraw

G = None
SS = 8
PHRASES = ["game_on", "game_shot", "bust", "darts"]


def _px(d, x, y, c):
    d.rectangle([x, y, x, y], fill=c)


def icon():
    """Three darts, fanned: red flights, silver barrels, steel points."""
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for k, (dx, flight) in enumerate(((0, (210, 40, 40)), (5, (40, 110, 210)), (10, (240, 200, 40)))):
        for i in range(16):
            x, y = 4 + dx + i, 24 - i
            col = (200, 200, 205) if 6 <= i < 11 else (150, 150, 155) if i >= 11 else (60, 60, 62)
            _px(d, x, y, col + (255,))
        for j in range(4):
            _px(d, 3 + dx + j, 22 - j, flight + (255,))
            _px(d, 4 + dx + j, 25 - j, flight + (255,))
            _px(d, 2 + dx + j, 24 - j, flight + (255,))
    return img


def entity():
    """The dart as ArrowRenderer draws it (32 x 32, the arrow's layout): the long view in rows
    0-4 (flights at the left, the point at the right), the end-on cross at 0-4 x 5-9."""
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    flight, shaft, barrel, point = (210, 40, 40, 255), (60, 60, 62, 255), (200, 200, 205, 255), (150, 150, 155, 255)
    for x in range(0, 4):
        for y in range(0 + (3 - x) // 2, 5 - (3 - x) // 2):
            _px(d, x + 1, y, flight)
    for x in range(5, 8):
        _px(d, x, 2, shaft)
    for x in range(8, 13):
        for y in (1, 2, 3):
            _px(d, x, y, barrel)
    for x in range(13, 16):
        _px(d, x, 2, point)
    for i in range(5):                                       # the flights end-on: a cross
        _px(d, i, 7, flight)
        _px(d, 2, 5 + i, flight)
    return img


def generate(gmod):
    global G
    G = gmod
    (G.ASSETS / "textures/item").mkdir(parents=True, exist_ok=True)
    icon().save(G.ASSETS / "textures/item/darts.png")
    G.write_json(G.ASSETS / "models/item/darts.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{G.MOD_ID}:item/darts"}})
    (G.ASSETS / "textures/entity").mkdir(parents=True, exist_ok=True)
    entity().save(G.ASSETS / "textures/entity/dart.png")
    sounds = {}
    for key in [f"score_{n}" for n in range(181)] + PHRASES:
        sounds[f"darts.{key}"] = {"subtitle": f"subtitles.{G.MOD_ID}.darts.call", "sounds": [{"name": f"{G.MOD_ID}:darts/{key}", "stream": False}]}
    G.write_json(G.ASSETS / "sounds.json", sounds)


def lang(mid):
    return {f"item.{mid}.darts": "Darts", f"entity.{mid}.dart": "Dart", f"subtitles.{mid}.darts.call": "Darts caller calls the score"}
