"""Generates the ChessSMP resource pack textures (run: python3 tools/make_textures.py)."""
from PIL import Image
import os

BASE = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "resourcepack", "assets")

def save(img, *path):
    full = os.path.join(BASE, *path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    img.save(full)

# ---------------------------------------------------------------- Grandmaster Mace (chess board head, mace shape)
def mace():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    DARK, LIGHT = (30, 24, 20, 255), (238, 226, 196, 255)
    GOLD, GOLD_D = (255, 205, 60, 255), (176, 124, 20, 255)
    WOOD, WOOD_D = (122, 78, 40, 255), (86, 52, 26, 255)
    # handle: diagonal from bottom-left up to the head
    for i in range(0, 9):
        x, y = 1 + i, 14 - i
        px[x, y] = WOOD
        if x + 1 < 16:
            px[x + 1, y] = WOOD_D
    # gold ring below the head
    for (x, y) in [(8, 7), (9, 7), (8, 6), (9, 6)]:
        px[x, y] = GOLD_D
    # head: 7x7 chess board with gold frame, tilted look by offset
    ox, oy = 8, 0
    for dx in range(7):
        for dy in range(7):
            x, y = ox + dx, oy + dy
            edge = dx in (0, 6) or dy in (0, 6)
            if edge:
                px[x, y] = GOLD if (dx + dy) % 2 == 0 else GOLD_D
            else:
                px[x, y] = LIGHT if (dx + dy) % 2 == 0 else DARK
    # spikes on the corners like a mace
    for (x, y) in [(7, 0), (15, 0), (7, 6), (15, 6)]:
        if 0 <= x < 16 and 0 <= y < 16:
            px[x, y] = GOLD
    px[10, 7] = GOLD_D
    return img

# ---------------------------------------------------------------- Remnant (meteor shard with green glow)
def remnant():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    shape = [
        "................",
        ".......XX.......",
        "......XXXX......",
        ".....XXXXXX.....",
        "....XXXXXXXX....",
        "...XXXXXXXXXX...",
        "..XXXXXXXXXXXX..",
        "..XXXXXXXXXXXX..",
        "...XXXXXXXXXXX..",
        "...XXXXXXXXXX...",
        "....XXXXXXXXX...",
        ".....XXXXXXX....",
        "......XXXXX.....",
        ".......XXX......",
        "................",
        "................",
    ]
    rock = [(38, 36, 44, 255), (54, 50, 62, 255), (70, 66, 80, 255)]
    for y, row in enumerate(shape):
        for x, c in enumerate(row):
            if c == "X":
                px[x, y] = rock[(x * 3 + y * 5) % 3]
    # glowing green cracks
    glow = (60, 255, 150, 255)
    glow_d = (20, 160, 90, 255)
    for (x, y) in [(7, 3), (7, 4), (8, 5), (8, 6), (9, 7), (9, 8), (10, 9)]:
        px[x, y] = glow
    for (x, y) in [(6, 4), (9, 5), (7, 7), (10, 8), (8, 9)]:
        px[x, y] = glow_d
    px[5, 8] = glow_d
    px[5, 9] = glow
    return img

# ---------------------------------------------------------------- green villager (profession overlay for "nitwit")
def box_uv(img, u, v, w, h, d, fills):
    """Paints the six faces of a Minecraft box model. fills: dict face -> color or callable(x,y)->color or None."""
    faces = {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "left": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "right": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
    }
    px = img.load()
    for name, (fx, fy, fw, fh) in faces.items():
        fill = fills.get(name, fills.get("all"))
        if fill is None:
            continue
        for yy in range(fh):
            for xx in range(fw):
                x, y = fx + xx, fy + yy
                if 0 <= x < img.width and 0 <= y < img.height:
                    px[x, y] = fill(xx, yy, fw, fh) if callable(fill) else fill

def villager():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    G1, G2, G3 = (30, 140, 70, 255), (22, 108, 52, 255), (80, 210, 120, 255)
    GOLD = (255, 205, 60, 255)

    def robe(xx, yy, fw, fh):
        if yy % 4 == 3:
            return G2
        return G1 if (xx + yy // 2) % 5 else G3

    def trim(xx, yy, fw, fh):
        return GOLD if yy in (0, fh - 1) else robe(xx, yy, fw, fh)

    # robe (long coat), body, arms, legs, hood (hat) - same UV layout as the vanilla villager model
    box_uv(img, 0, 38, 8, 20, 6, {"all": robe, "bottom": trim, "front": lambda x, y, w, h: GOLD if (x == w // 2 and 2 < y < 9) else robe(x, y, w, h)})
    box_uv(img, 16, 20, 8, 12, 6, {"all": G1})
    box_uv(img, 44, 22, 8, 4, 4, {"all": G2})
    box_uv(img, 0, 22, 4, 12, 4, {"all": G2})
    # hat/hood: green everywhere except the face opening (front face stays transparent)
    box_uv(img, 32, 0, 8, 10, 8, {"all": G1, "front": None, "top": G3})
    # hood rim under the face opening
    px = img.load()
    for x in range(40, 48):
        px[x, 17] = GOLD
    return img

save(mace(), "chesssmp", "textures", "item", "grandmaster_mace.png")
save(remnant(), "chesssmp", "textures", "item", "remnant.png")
save(villager(), "minecraft", "textures", "entity", "villager", "profession", "nitwit.png")
print("textures written")
