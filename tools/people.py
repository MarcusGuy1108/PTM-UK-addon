"""Shopkeepers' skins (64 x 64, the player layout): eight uniforms (see ShopKind.outfit), three
people in each, with different skin tones and hair; the long-haired ones match the Java side's
ShopKeeperEntity.longHair (outfit + person even)."""
from PIL import Image

G = None
TONES = [(241, 194, 160), (224, 172, 128), (198, 134, 90), (141, 85, 56), (96, 60, 40)]
HAIR = [(60, 40, 25), (20, 18, 16), (150, 100, 50), (200, 170, 110), (110, 110, 112), (130, 60, 30)]
# outfit: (shirt, trousers, apron or coat over the front, hat)
OUTFITS = [
    ((40, 120, 70), (30, 30, 34), None, None),                 # supermarket: green polo
    ((40, 50, 82), (52, 76, 120), None, None),                 # corner shop: navy hoodie, jeans
    ((236, 236, 232), (96, 96, 100), None, (240, 240, 236)),   # chippy: whites and a hat
    ((26, 26, 28), (40, 46, 60), (120, 80, 50), None),         # café: black, brown apron
    ((22, 22, 24), (30, 30, 34), None, None),                  # pub: black polo
    ((120, 30, 46), (200, 186, 150), None, None),              # charity: cardigan
    ((18, 18, 20), (18, 18, 20), None, None),                  # salon: black tunic
    ((236, 238, 240), (100, 104, 110), None, None),            # pharmacy: white coat
]


def rect(img, x0, y0, x1, y1, c):
    for y in range(y0, y1):
        for x in range(x0, x1):
            img.putpixel((x, y), c + (255,))


def skin(outfit, person):
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    i = outfit * 3 + person
    tone, hair = TONES[(i * 2 + person) % len(TONES)], HAIR[(i * 5 + outfit) % len(HAIR)]
    long_hair = (outfit + person) % 2 == 0
    shirt, legs, front, hat = OUTFITS[outfit]
    dark = tuple(max(0, v - 40) for v in tone)
    # head
    rect(img, 0, 8, 32, 16, tone)
    rect(img, 8, 0, 24, 8, tone)
    rect(img, 8, 0, 16, 8, hair)                               # top
    rect(img, 24, 8, 32, 16 if long_hair else 13, hair)        # back
    for x0 in (0, 16):                                         # sides
        rect(img, x0, 8, x0 + 8, 14 if long_hair else 10, hair)
    rect(img, 8, 8, 16, 10, hair)                              # fringe
    if long_hair:
        rect(img, 8, 10, 9, 15, hair)
        rect(img, 15, 10, 16, 15, hair)
    for x, c in ((9, (250, 250, 250)), (10, (40, 60, 90)), (13, (40, 60, 90)), (14, (250, 250, 250))):
        img.putpixel((x, 12), c + (255,))
    rect(img, 10, 11, 11, 12, tuple(max(0, v - 30) for v in hair))
    rect(img, 13, 11, 14, 12, tuple(max(0, v - 30) for v in hair))
    rect(img, 11, 13, 13, 14, dark)
    rect(img, 11, 14, 13, 15, (150, 70, 70) if long_hair else dark)
    if hat:
        rect(img, 40, 0, 48, 8, hat)                           # the hat layer's top and band
        rect(img, 32, 8, 64, 10, hat)
    # body, arms, legs
    rect(img, 16, 16, 40, 32, shirt)
    rect(img, 40, 16, 56, 32, shirt)
    rect(img, 32, 48, 48, 64, shirt)
    for x0, y0 in ((40, 26), (32, 58)):                        # hands
        rect(img, x0, y0 + 2, x0 + 16, y0 + 6, tone)
    if outfit in (0, 3, 4, 6):                                 # short sleeves
        rect(img, 40, 24, 56, 28, tone)
        rect(img, 32, 56, 48, 60, tone)
    if front:
        rect(img, 21, 22, 27, 32, front)
    if outfit == 5:                                            # a white shirt under the cardigan, a lanyard
        rect(img, 22, 20, 26, 32, (240, 240, 236))
        rect(img, 23, 20, 25, 26, (200, 30, 40))
    if outfit == 7:
        rect(img, 23, 20, 25, 25, (150, 190, 230))
    if outfit in (0, 4):                                       # collar
        rect(img, 22, 20, 26, 21, tuple(max(0, v - 30) for v in shirt))
    img.putpixel((22, 23), (245, 245, 245, 255))               # name badge
    img.putpixel((23, 23), (245, 245, 245, 255))
    rect(img, 0, 16, 16, 32, legs)
    rect(img, 16, 48, 32, 64, legs)
    rect(img, 0, 30, 16, 32, (30, 28, 26))                     # shoes
    rect(img, 16, 62, 32, 64, (30, 28, 26))
    return img


def generate(gmod):
    global G
    G = gmod
    out = G.ASSETS / "textures/entity/shop_keeper"
    out.mkdir(parents=True, exist_ok=True)
    for o in range(len(OUTFITS)):
        for p in range(3):
            skin(o, p).save(out / f"skin_{o * 3 + p}.png")


def lang(mid):
    return {f"entity.{mid}.shop_keeper": "Shopkeeper", f"itemGroup.{mid}.money": "UK Money & Shop Goods"}
