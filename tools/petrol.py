"""Petrol station kit: fuel pump and air & water faces, the car wash floor plate, built-in logos
for the fuel price sign (all made up - no real brands), item icons, block models and lang.
The fuel price sign and car wash are drawn by their block entity renderers; their block models
here are just the parts that stand still (the sign's plinth, the car wash's floor plate)."""
import math

from PIL import Image, ImageDraw

from furniture_hd import Panel

G = None

# built-in logos for the fuel price sign; name -> title shown in the editor
LOGOS = {"drop": "Drop", "flame": "Flame", "leaf": "Leaf", "star": "Star", "bolt": "Bolt", "crown": "Crown",
         "wave": "Wave", "cube": "Cube"}


def pump_face():
    """Front of a fuel dispenser (6 x 9 px face): grade buttons, LCD sale display, card reader."""
    p = Panel(120, 180, (240, 240, 236))
    p.rect(0, 0, 120, 16, (30, 130, 70))
    p.text(60, 8, "SELF SERVICE", 8, (255, 255, 255))
    p.rect(10, 24, 110, 84, (30, 32, 36), r=4)
    p.rect(14, 28, 106, 80, (16, 20, 18))
    for i, (label, value) in enumerate((("SALE £", "41.20"), ("LITRES", "28.24"), ("PRICE p", "145.9"))):
        y = 37 + i * 15
        p.text(30, y, label, 6, (150, 200, 150))
        p.text(82, y, value, 11, (255, 190, 40))
    for i, (col, word) in enumerate((((30, 140, 70), "UNLEADED"), ((24, 24, 26), "DIESEL"), ((190, 30, 40), "SUPER"))):
        x0 = 8 + i * 36
        p.rect(x0, 92, x0 + 32, 112, col, r=3)
        p.text(x0 + 16, 102, word, 5.5, (255, 255, 255))
    p.rect(30, 122, 90, 160, (210, 214, 214), outline=(150, 154, 154), width=1.5, r=5)
    p.text(60, 134, "PAY AT PUMP", 7, (40, 44, 48))
    for k in range(3):
        p.d.arc(p.k(50 - k * 5, 140 - k * 2, 70 + k * 5, 156 + k * 2), -40, 40, fill=(60, 120, 160), width=int(2 * p.ss))
    p.rect(0, 168, 120, 180, (30, 130, 70))
    return p.done()


def air_face():
    """Air & water machine front: AIR / WATER, a pressure gauge and a coin slot."""
    p = Panel(110, 150, (30, 90, 170))
    p.text(55, 18, "AIR", 22, (255, 255, 255))
    p.text(55, 38, "& WATER", 11, (255, 255, 255))
    p.ellipse(25, 52, 85, 112, (240, 240, 236), outline=(20, 20, 24), width=3)
    for a in range(0, 271, 30):
        ang = math.radians(225 - a)
        p.line([(55 + 22 * math.cos(ang), 82 - 22 * math.sin(ang)), (55 + 27 * math.cos(ang), 82 - 27 * math.sin(ang))],
               (20, 20, 24), 1.5)
    p.line([(55, 82), (55 + 20 * math.cos(math.radians(80)), 82 - 20 * math.sin(math.radians(80)))], (200, 30, 30), 2)
    p.text(55, 100, "PSI", 7, (40, 40, 40))
    p.rect(35, 122, 75, 140, (210, 214, 214), r=3)
    p.rect(52, 126, 58, 136, (20, 20, 22))
    return p.done()


def car_wash_plate():
    """The car wash's floor plate: concrete with a yellow stop line and a drain channel."""
    p = Panel(64, 64, (150, 152, 150))
    p.rect(0, 28, 64, 36, (60, 62, 64))
    for x in range(2, 64, 5):
        p.rect(x, 29, x + 2, 35, (30, 30, 32))
    p.rect(0, 10, 64, 14, (240, 200, 30))
    p.text(32, 50, "STOP", 9, (240, 200, 30))
    return p.done()


def logo(name, size=128):
    """A made-up brand mark, drawn on transparency."""
    ss = 4
    n = size * ss
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = n / 2

    def circle(r, fill):
        d.ellipse((c - r, c - r, c + r, c + r), fill=fill)
    if name == "drop":
        circle(n * 0.47, (20, 90, 180, 255))
        pts = [(c, n * 0.14)] + [(c + n * 0.24 * math.sin(math.radians(a)), n * 0.58 - n * 0.24 * math.cos(math.radians(a)))
                                  for a in range(80, 281, 10)]
        d.polygon(pts, fill=(255, 255, 255, 255))
    elif name == "flame":
        circle(n * 0.47, (230, 110, 20, 255))
        d.polygon([(c, n * 0.12), (n * 0.72, n * 0.55), (n * 0.66, n * 0.8), (c, n * 0.88), (n * 0.34, n * 0.8),
                   (n * 0.28, n * 0.55)], fill=(255, 220, 60, 255))
        d.polygon([(c, n * 0.4), (n * 0.6, n * 0.66), (c, n * 0.82), (n * 0.4, n * 0.66)], fill=(230, 60, 20, 255))
    elif name == "leaf":
        circle(n * 0.47, (30, 140, 70, 255))
        pts = []
        for i in range(41):                     # a pointed leaf along the diagonal
            t = -1 + i / 20
            pts.append((t, 0.42 * (1 - t * t)))
        pts += [(t, -w) for t, w in reversed(pts)]
        ca, sa = math.cos(math.radians(-45)), math.sin(math.radians(-45))
        d.polygon([(c + n * 0.34 * (x * ca - y * sa), c + n * 0.34 * (x * sa + y * ca)) for x, y in pts],
                  fill=(240, 250, 230, 255))
        d.line([(c - n * 0.26, c + n * 0.26), (c + n * 0.22, c - n * 0.22)], fill=(30, 140, 70, 255), width=int(n * 0.03))
    elif name == "star":
        circle(n * 0.47, (200, 25, 35, 255))
        pts = []
        for k in range(10):
            r = n * (0.36 if k % 2 == 0 else 0.15)
            a = math.radians(-90 + k * 36)
            pts.append((c + r * math.cos(a), c + r * math.sin(a)))
        d.polygon(pts, fill=(255, 255, 255, 255))
    elif name == "bolt":
        circle(n * 0.47, (20, 160, 200, 255))
        d.polygon([(n * 0.56, n * 0.12), (n * 0.3, n * 0.56), (n * 0.48, n * 0.56), (n * 0.42, n * 0.88),
                   (n * 0.72, n * 0.42), (n * 0.53, n * 0.42)], fill=(255, 240, 80, 255))
    elif name == "crown":
        d.rounded_rectangle((n * 0.04, n * 0.04, n * 0.96, n * 0.96), radius=n * 0.12, fill=(30, 40, 90, 255))
        d.polygon([(n * 0.18, n * 0.7), (n * 0.18, n * 0.3), (n * 0.36, n * 0.5), (c, n * 0.22), (n * 0.64, n * 0.5),
                   (n * 0.82, n * 0.3), (n * 0.82, n * 0.7)], fill=(240, 190, 50, 255))
        d.rectangle((n * 0.18, n * 0.72, n * 0.82, n * 0.8), fill=(240, 190, 50, 255))
    elif name == "wave":
        circle(n * 0.47, (255, 255, 255, 255))
        for k, col in enumerate(((20, 120, 200, 255), (20, 170, 220, 255), (20, 90, 160, 255))):
            y = n * (0.36 + 0.14 * k)
            pts = [(n * 0.12 + i * n * 0.04, y + n * 0.06 * math.sin(i * 0.9)) for i in range(20)]
            d.line(pts, fill=col, width=int(n * 0.07))
    elif name == "cube":
        d.rounded_rectangle((n * 0.04, n * 0.04, n * 0.96, n * 0.96), radius=n * 0.12, fill=(200, 30, 40, 255))
        top = [(c, n * 0.18), (n * 0.8, n * 0.34), (c, n * 0.5), (n * 0.2, n * 0.34)]
        d.polygon(top, fill=(255, 255, 255, 255))
        d.polygon([(n * 0.2, n * 0.38), (n * 0.48, n * 0.53), (n * 0.48, n * 0.84), (n * 0.2, n * 0.68)], fill=(235, 235, 235, 255))
        d.polygon([(n * 0.8, n * 0.38), (n * 0.52, n * 0.53), (n * 0.52, n * 0.84), (n * 0.8, n * 0.68)], fill=(210, 210, 210, 255))
    return img.resize((size, size), Image.LANCZOS)


def price_sign_icon():
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle((9, 1, 22, 30), fill=(240, 240, 236, 255), outline=(90, 94, 100, 255))
    d.ellipse((12, 3, 19, 10), fill=(200, 25, 35, 255))
    for i, col in enumerate(((30, 140, 70), (24, 24, 26), (190, 30, 40))):
        y = 12 + i * 5
        d.rectangle((10, y, 15, y + 3), fill=col + (255,))
        d.rectangle((16, y, 21, y + 3), fill=(10, 10, 10, 255))
        d.line([(17, y + 1), (20, y + 1)], fill=(255, 180, 30, 255))
    d.rectangle((12, 28, 19, 31), fill=(120, 124, 128, 255))
    return img


def car_wash_icon():
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle((2, 4, 29, 8), fill=(240, 240, 236, 255), outline=(30, 90, 170, 255))
    d.rectangle((2, 8, 5, 30), fill=(240, 240, 236, 255))
    d.rectangle((26, 8, 29, 30), fill=(240, 240, 236, 255))
    for x in (8, 22):
        for y in range(10, 28, 3):
            d.rectangle((x - 2, y, x + 2, y + 1), fill=(40, 100, 220, 255) if (y // 3) % 2 else (220, 50, 50, 255))
    for y in range(10, 15):
        d.line([(11, y), (19, y)], fill=(40, 100, 220, 255) if y % 2 else (220, 50, 50, 255))
    for x in range(9, 23, 3):
        d.point((x, 17), fill=(120, 190, 255, 255))
        d.point((x + 1, 20), fill=(120, 190, 255, 255))
    d.rectangle((0, 30, 31, 31), fill=(120, 124, 128, 255))
    return img


def generate(gmod):
    global G
    G = gmod
    mid = G.MOD_ID
    for name in LOGOS:
        (G.ASSETS / "textures/logo").mkdir(parents=True, exist_ok=True)
        logo(name).save(G.ASSETS / f"textures/logo/{name}.png")
    (G.ASSETS / "textures/item").mkdir(parents=True, exist_ok=True)
    price_sign_icon().save(G.ASSETS / "textures/item/fuel_price_sign.png")
    car_wash_icon().save(G.ASSETS / "textures/item/car_wash.png")
    plate = G.save(car_wash_plate(), "petrol/car_wash_plate")
    plinth = "minecraft:block/smooth_stone"
    # fuel price sign: just the plinth (the totem is drawn by its renderer)
    G.write_json(G.ASSETS / "models/block/petrol/fuel_price_sign.json", {
        "parent": "minecraft:block/block", "textures": {"particle": plinth, "s": plinth},
        "elements": [{"from": [3, 0, 4], "to": [13, 4, 12],
                      "faces": {f: {"texture": "#s"} for f in ("north", "south", "east", "west", "up", "down")}}]})
    G.write_json(G.ASSETS / "models/block/petrol/car_wash.json", {
        "parent": "minecraft:block/block", "textures": {"particle": plate, "p": plate},
        "elements": [{"from": [0, 0, 0], "to": [16, 0.5, 16],
                      "faces": {"up": {"texture": "#p"}, "down": {"texture": "#p"},
                                "north": {"texture": "#p", "uv": [0, 0, 16, 0.5]}, "south": {"texture": "#p", "uv": [0, 0, 16, 0.5]},
                                "east": {"texture": "#p", "uv": [0, 0, 16, 0.5]}, "west": {"texture": "#p", "uv": [0, 0, 16, 0.5]}}}]})
    for name in ("fuel_price_sign", "car_wash"):
        G.write_json(G.ASSETS / f"blockstates/{name}.json", {"variants": {
            f"facing={f}": ({"model": f"{mid}:block/petrol/{name}", "y": y} if y else {"model": f"{mid}:block/petrol/{name}"})
            for f, y in (("south", 0), ("west", 90), ("north", 180), ("east", 270))}})
        G.write_json(G.ASSETS / f"models/item/{name}.json", {"parent": "minecraft:item/generated",
                                                             "textures": {"layer0": f"{mid}:item/{name}"}})


def lang(mid):
    return {f"block.{mid}.fuel_price_sign": "Fuel Price Sign",
            f"block.{mid}.car_wash": "Automatic Car Wash",
            f"block.{mid}.fuel_pump": "Fuel Pump",
            f"block.{mid}.air_water_machine": "Air & Water Machine"}
