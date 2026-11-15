"""What the shops sell: item icons (32 px, drawn big and shrunk) and names. Made-up packs in
plain colours, no real brands. The names and prices live in the Java Product enum; the list
here only needs the ids and titles in the same words."""
from PIL import Image, ImageDraw

G = None
SS = 8
PRODUCTS = {
    "milk": "Semi-skimmed milk, 2 pints", "butter": "Butter", "cheese": "Mature cheddar", "meal_deal": "Sandwich meal deal",
    "bread": "Sliced white bread", "eggs": "Free range eggs (6)", "beans": "Baked beans", "soup": "Tomato soup", "cereal": "Corn flakes",
    "tea_bags": "Tea bags (80)", "pasta": "Pasta", "apples": "Apples (6)", "bananas": "Bananas (5)", "frozen_pizza": "Frozen pizza",
    "ice_cream": "Ice cream tub", "crisps": "Crisps", "chocolate": "Chocolate bar", "sweets": "Bag of sweets", "cola": "Can of cola",
    "water": "Bottle of water", "energy_drink": "Energy drink", "toilet_roll": "Toilet roll (9)", "washing_up": "Washing-up liquid",
    "batteries": "Batteries (4)", "light_bulb": "Light bulb", "newspaper": "Newspaper", "magazine": "Magazine", "scratchcard": "Scratchcard",
    "paracetamol": "Paracetamol (16)", "plasters": "Plasters", "toothpaste": "Toothpaste", "fish_and_chips": "Cod and chips",
    "chips": "Chips", "sausage": "Battered sausage", "curry_sauce": "Curry sauce", "mushy_peas": "Mushy peas", "tea": "Cup of tea",
    "coffee": "Flat white", "bacon_roll": "Bacon roll", "cake": "Slice of cake", "toastie": "Cheese toastie", "pint": "Pint of bitter",
    "pie": "Steak pie and mash", "kebab": "Doner kebab", "pizza": "12\" pizza",
}


def _c(rgb, a=255):
    return tuple(rgb) + (a,)


def _dk(rgb, f=0.7):
    return tuple(int(v * f) for v in rgb) + (255,)


def _canvas():
    img = Image.new("RGBA", (32 * SS, 32 * SS), (0, 0, 0, 0))
    return img, ImageDraw.Draw(img)


def _r(d, x0, y0, x1, y1, fill, outline=None, rad=0):
    box = [x0 * SS, y0 * SS, x1 * SS, y1 * SS]
    if rad:
        d.rounded_rectangle(box, radius=rad * SS, fill=_c(fill), outline=_dk(outline or fill), width=SS // 2)
    else:
        d.rectangle(box, fill=_c(fill), outline=_dk(outline or fill), width=SS // 2)


def _e(d, x0, y0, x1, y1, fill):
    d.ellipse([x0 * SS, y0 * SS, x1 * SS, y1 * SS], fill=_c(fill), outline=_dk(fill), width=SS // 2)


def _p(d, pts, fill):
    d.polygon([(x * SS, y * SS) for x, y in pts], fill=_c(fill), outline=_dk(fill))


def can(label, top=(200, 200, 205)):
    img, d = _canvas()
    _r(d, 9, 6, 23, 28, label, rad=2)
    _r(d, 9, 5, 23, 8, top, rad=1)
    _r(d, 10, 14, 22, 18, tuple(min(255, v + 60) for v in label))
    return img


def bottle(body, cap, label=(255, 255, 255)):
    img, d = _canvas()
    _r(d, 9, 10, 23, 29, body, rad=3)
    _r(d, 12, 6, 20, 11, body)
    _r(d, 13, 3, 19, 7, cap, rad=1)
    _r(d, 9, 16, 23, 22, label)
    return img


def box(colour, band=(255, 255, 255), tall=True):
    img, d = _canvas()
    y0 = 3 if tall else 10
    _r(d, 7, y0, 25, 29, colour)
    _r(d, 7, y0 + (26 - y0) // 2, 25, y0 + (26 - y0) // 2 + 5, band)
    return img


def packet(colour, window=None):
    img, d = _canvas()
    _p(d, [(8, 5), (24, 5), (25, 27), (7, 27)], colour)
    _r(d, 8, 4, 24, 7, _dk(colour)[:3])
    if window:
        _e(d, 11, 12, 21, 22, window)
    return img


def tub(colour, lid):
    img, d = _canvas()
    _p(d, [(7, 12), (25, 12), (23, 27), (9, 27)], colour)
    _r(d, 6, 9, 26, 13, lid, rad=1)
    return img


def cup(colour, sleeve, lid=None, steam=False):
    img, d = _canvas()
    _p(d, [(9, 9), (23, 9), (21, 28), (11, 28)], colour)
    _r(d, 9, 15, 23, 21, sleeve)
    if lid:
        _r(d, 8, 6, 24, 10, lid, rad=1)
    if steam:
        for x in (13, 17):
            d.line([(x * SS, 5 * SS), ((x + 1) * SS, 2 * SS)], fill=(240, 240, 240, 200), width=SS)
    return img


def mug(drink):
    img, d = _canvas()
    _r(d, 7, 10, 22, 28, (236, 236, 232), rad=2)
    _r(d, 8, 10, 21, 13, drink)
    d.arc([20 * SS, 14 * SS, 27 * SS, 23 * SS], -90, 90, fill=(210, 210, 206, 255), width=2 * SS)
    for x in (11, 16):
        d.line([(x * SS, 7 * SS), ((x + 1) * SS, 3 * SS)], fill=(240, 240, 240, 200), width=SS)
    return img


def paper_wrap(food):
    img, d = _canvas()
    _p(d, [(4, 12), (28, 12), (24, 28), (8, 28)], (245, 242, 230))
    for colour, (x0, y0, x1, y1) in food:
        _e(d, x0, y0, x1, y1, colour)
    return img


GOLD, CHIP = (212, 160, 70), (236, 196, 100)


def painter(pid):
    if pid == "milk":
        return bottle((248, 248, 246), (40, 160, 70), (220, 236, 255))
    if pid == "water":
        return bottle((170, 210, 240), (40, 90, 200), (60, 140, 220))
    if pid == "washing_up":
        return bottle((60, 180, 80), (240, 240, 240), (230, 250, 230))
    if pid == "cola":
        return can((200, 30, 40))
    if pid == "energy_drink":
        return can((30, 30, 30), (120, 220, 60))
    if pid == "beans":
        return can((40, 150, 160))
    if pid == "soup":
        return can((190, 40, 40), (220, 220, 220))
    if pid in ("butter",):
        return box((240, 200, 60), (250, 240, 200), tall=False)
    if pid == "cheese":
        img, d = _canvas()
        _p(d, [(5, 24), (27, 24), (27, 12)], (250, 200, 60))
        _e(d, 18, 17, 21, 20, (230, 170, 40))
        return img
    if pid == "meal_deal":
        img, d = _canvas()
        _p(d, [(4, 26), (20, 26), (4, 10)], (230, 210, 170))
        _r(d, 20, 8, 28, 28, (200, 30, 40), rad=2)
        return img
    if pid == "bread":
        img, d = _canvas()
        _r(d, 5, 10, 27, 27, (250, 248, 240), rad=5)
        _r(d, 5, 16, 27, 21, (40, 90, 200))
        return img
    if pid == "eggs":
        img, d = _canvas()
        _r(d, 4, 14, 28, 26, (170, 160, 140), rad=1)
        for x in (6, 13, 20):
            _e(d, x, 9, x + 6, 17, (236, 210, 170))
        return img
    if pid == "cereal":
        return box((240, 200, 40), (220, 40, 40))
    if pid == "tea_bags":
        return box((150, 40, 40), (240, 220, 180), tall=False)
    if pid == "pasta":
        return packet((250, 250, 250), (240, 200, 80))
    if pid == "apples":
        img, d = _canvas()
        _e(d, 4, 12, 16, 24, (200, 30, 30))
        _e(d, 15, 10, 27, 22, (90, 170, 40))
        return img
    if pid == "bananas":
        img, d = _canvas()
        for k in range(3):
            d.arc([4 * SS, (4 + k * 3) * SS, 28 * SS, (24 + k * 3) * SS], 20, 160, fill=(245, 215, 50, 255), width=3 * SS)
        return img
    if pid == "frozen_pizza":
        img, d = _canvas()
        _r(d, 4, 8, 28, 26, (40, 90, 170))
        _e(d, 8, 10, 24, 24, (220, 120, 50))
        return img
    if pid == "ice_cream":
        return tub((248, 240, 230), (230, 120, 160))
    if pid == "crisps":
        return packet((220, 40, 40), (240, 200, 90))
    if pid == "chocolate":
        img, d = _canvas()
        _r(d, 4, 11, 28, 22, (90, 40, 130), rad=1)
        _r(d, 4, 14, 10, 19, (220, 220, 225))
        return img
    if pid == "sweets":
        return packet((240, 120, 200), (120, 220, 240))
    if pid == "toilet_roll":
        img, d = _canvas()
        for x in (5, 13, 21):
            for y in (8, 17):
                _e(d, x, y, x + 8, y + 8, (250, 250, 250))
        return img
    if pid == "batteries":
        img, d = _canvas()
        _r(d, 5, 4, 27, 28, (240, 240, 240))
        for x in (8, 13, 18, 23):
            _r(d, x - 1, 9, x + 3, 25, (40, 40, 40))
            _r(d, x - 1, 9, x + 3, 13, (200, 120, 40))
        return img
    if pid == "light_bulb":
        img, d = _canvas()
        _e(d, 8, 3, 24, 19, (250, 245, 200))
        _r(d, 12, 18, 20, 27, (180, 180, 185))
        return img
    if pid == "newspaper":
        img, d = _canvas()
        _r(d, 5, 5, 27, 27, (238, 236, 228))
        _r(d, 7, 7, 25, 11, (40, 40, 40))
        for y in (14, 17, 20, 23):
            _r(d, 7, y, 25, y + 1, (150, 150, 150))
        return img
    if pid == "magazine":
        img, d = _canvas()
        _r(d, 7, 3, 25, 29, (240, 80, 120))
        _r(d, 9, 5, 23, 9, (255, 255, 255))
        _e(d, 10, 12, 22, 26, (250, 210, 170))
        return img
    if pid == "scratchcard":
        img, d = _canvas()
        _r(d, 4, 7, 28, 25, (250, 210, 40), rad=1)
        _r(d, 7, 12, 25, 21, (180, 180, 190))
        _r(d, 7, 12, 14, 21, (230, 60, 60))
        return img
    if pid == "paracetamol":
        return box((250, 250, 250), (220, 40, 40), tall=False)
    if pid == "plasters":
        return box((230, 200, 170), (240, 240, 240), tall=False)
    if pid == "toothpaste":
        img, d = _canvas()
        _p(d, [(4, 12), (24, 10), (24, 22), (4, 20)], (250, 250, 250))
        _r(d, 24, 13, 28, 19, (40, 90, 200))
        _r(d, 8, 13, 18, 18, (40, 140, 220))
        return img
    if pid == "fish_and_chips":
        return paper_wrap([(GOLD, (6, 13, 20, 21))] + [(CHIP, (x, 18, x + 4, 27)) for x in (14, 18, 22)])
    if pid == "chips":
        img, d = _canvas()
        for x in (9, 12, 15, 18, 21):
            _r(d, x, 5 + (x % 3), x + 3, 16, CHIP)
        _p(d, [(7, 13), (25, 13), (22, 29), (10, 29)], (240, 60, 60))
        return img
    if pid == "sausage":
        img, d = _canvas()
        _r(d, 4, 12, 28, 20, GOLD, rad=4)
        return img
    if pid == "curry_sauce":
        return tub((240, 240, 236), (170, 110, 40))
    if pid == "mushy_peas":
        return tub((240, 240, 236), (100, 170, 50))
    if pid == "tea":
        return mug((160, 110, 70))
    if pid == "coffee":
        return cup((250, 250, 250), (150, 100, 60), lid=(40, 40, 40))
    if pid == "bacon_roll":
        img, d = _canvas()
        _e(d, 4, 12, 28, 26, (220, 170, 100))
        _r(d, 5, 17, 27, 20, (190, 70, 60))
        return img
    if pid == "cake":
        img, d = _canvas()
        _p(d, [(4, 26), (28, 26), (28, 12)], (240, 210, 160))
        _p(d, [(4, 26), (28, 12), (28, 9), (2, 24)], (250, 240, 240))
        _e(d, 23, 6, 28, 11, (200, 30, 40))
        return img
    if pid == "toastie":
        img, d = _canvas()
        _p(d, [(4, 26), (26, 26), (4, 6)], (200, 140, 60))
        _r(d, 5, 22, 20, 24, (250, 210, 60))
        return img
    if pid == "pint":
        img, d = _canvas()
        _p(d, [(9, 7), (23, 7), (22, 29), (10, 29)], (190, 110, 40))
        _r(d, 9, 5, 23, 9, (245, 240, 225), rad=1)
        return img
    if pid == "pie":
        img, d = _canvas()
        _e(d, 2, 10, 30, 28, (240, 240, 240))
        _e(d, 5, 12, 17, 24, (200, 140, 70))
        _e(d, 16, 14, 27, 24, (248, 244, 230))
        return img
    if pid == "kebab":
        img, d = _canvas()
        _p(d, [(8, 4), (24, 4), (16, 29)], (230, 200, 150))
        _r(d, 11, 7, 21, 13, (150, 90, 60))
        _r(d, 12, 13, 20, 16, (80, 170, 60))
        return img
    if pid == "pizza":
        img, d = _canvas()
        _e(d, 3, 3, 29, 29, (220, 150, 70))
        _e(d, 6, 6, 26, 26, (220, 60, 40))
        for x, y in ((10, 10), (17, 12), (12, 18), (19, 19)):
            _e(d, x, y, x + 4, y + 4, (150, 30, 40))
        return img
    raise KeyError(pid)


def generate(gmod):
    global G
    G = gmod
    tex = G.ASSETS / "textures/item"
    tex.mkdir(parents=True, exist_ok=True)
    for pid in PRODUCTS:
        painter(pid).resize((32, 32), Image.LANCZOS).save(tex / f"{pid}.png")
        G.write_json(G.ASSETS / f"models/item/{pid}.json",
                     {"parent": "minecraft:item/generated", "textures": {"layer0": f"{G.MOD_ID}:item/{pid}"}})


def lang(mid):
    return {f"item.{mid}.{pid}": title for pid, title in PRODUCTS.items()}
