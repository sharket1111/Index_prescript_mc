#!/usr/bin/env python3
"""Assets for Black God mode (fan-made, after Limbus Company), into the plugin pack:

- the halo: a sunburst behind the head (a black disc in a white-gold glow,
  a stepped golden burst, dark spikes and long pale rays), drawn on one flat,
  glowing, two-sided element so an item display can carry it;
- the robe: a 3D long black coat with wide drooping sleeves, a tall collar,
  pale and gold stripes, studs and black ribbons trailing to the ground,
  carried by an item display round the hidden body.

Run from the repository root: python3 tools/black_god_assets.py [preview.png]
"""
import json
import math
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
PACK = ROOT / "paper/pack/assets/index_prescript"
NS = "index_prescript"


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent="\t") + "\n")


# --- halo -----------------------------------------------------------------------

def halo_texture(size=128):
    ss = 4
    big = size * ss
    c = big / 2
    rnd = random.Random(11)
    img = Image.new("RGBA", (big, big))
    d = ImageDraw.Draw(img)

    def ray(angle, inner, outer, width, colour):
        a = math.radians(angle)
        n = (-math.sin(a), math.cos(a))
        p0 = (c + math.cos(a) * inner, c + math.sin(a) * inner)
        p1 = (c + math.cos(a) * outer, c + math.sin(a) * outer)
        w0, w1 = width, width * 0.35
        d.polygon([(p0[0] + n[0] * w0, p0[1] + n[1] * w0), (p1[0] + n[0] * w1, p1[1] + n[1] * w1),
                   (p1[0] - n[0] * w1, p1[1] - n[1] * w1), (p0[0] - n[0] * w0, p0[1] - n[1] * w0)], fill=colour)

    # Dark spikes, between the rays.
    for i in range(16):
        angle = i * 22.5 + 11.25 + rnd.uniform(-4, 4)
        ray(angle, big * 0.12, big * rnd.uniform(0.38, 0.46), big * 0.035, (46, 42, 36, 255))
    # The stepped golden burst.
    for i in range(48):
        angle = i * 7.5
        length = big * (0.2 + 0.14 * abs(math.sin(i * 1.7)) + rnd.uniform(0, 0.04))
        ray(angle, big * 0.08, length, big * 0.03, (214, 176, 110, 255) if i % 2 else (176, 140, 84, 255))
    glow = img.filter(ImageFilter.GaussianBlur(ss * 2))
    img = Image.alpha_composite(glow, img)
    d = ImageDraw.Draw(img)
    # Long pale rays, a little see-through.
    rays = Image.new("RGBA", (big, big))
    rd = ImageDraw.Draw(rays)
    for i in range(12):
        angle = i * 30 + rnd.uniform(-6, 6)
        a = math.radians(angle)
        n = (-math.sin(a), math.cos(a))
        inner, outer, w = big * 0.1, big * rnd.uniform(0.44, 0.5), big * 0.012
        p0 = (c + math.cos(a) * inner, c + math.sin(a) * inner)
        p1 = (c + math.cos(a) * outer, c + math.sin(a) * outer)
        rd.polygon([(p0[0] + n[0] * w, p0[1] + n[1] * w), (p1[0] + n[0] * w, p1[1] + n[1] * w),
                    (p1[0] - n[0] * w, p1[1] - n[1] * w), (p0[0] - n[0] * w, p0[1] - n[1] * w)],
                   fill=(250, 240, 210, 190))
    img = Image.alpha_composite(img, rays)
    d = ImageDraw.Draw(img)
    # The white-gold glow round the black disc.
    ring = Image.new("RGBA", (big, big))
    gd = ImageDraw.Draw(ring)
    r = big * 0.105
    gd.ellipse((c - r, c - r, c + r, c + r), fill=(255, 246, 214, 255))
    ring = ring.filter(ImageFilter.GaussianBlur(ss * 3))
    img = Image.alpha_composite(img, ring)
    d = ImageDraw.Draw(img)
    r = big * 0.075
    d.ellipse((c - r, c - r, c + r, c + r), fill=(12, 10, 10, 255), outline=(120, 100, 70, 255), width=ss)
    return img.resize((size, size), Image.LANCZOS)


def halo():
    tex = PACK / "textures/item/black_god_halo.png"
    tex.parent.mkdir(parents=True, exist_ok=True)
    halo_texture().save(tex)
    t = f"{NS}:item/black_god_halo"
    face = {"uv": [0, 0, 16, 16], "texture": "#0"}
    back = {"uv": [16, 0, 0, 16], "texture": "#0"}
    model = {
        "textures": {"0": t, "particle": t},
        "elements": [{"from": [0, 0, 8], "to": [16, 16, 8], "shade": False, "light_emission": 15,
                      "faces": {"north": back, "south": face}}],
    }
    write(PACK / "models/item/black_god_halo.json", model)
    write(PACK / "items/black_god_halo.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/black_god_halo"}})


# --- the 3D robe -------------------------------------------------------------------

ROBE_SWATCH = {
    "black": (0, 0, (20, 20, 23)),
    "dark": (4, 0, (36, 35, 38)),
    "smoke": (8, 0, (58, 54, 48)),
    "pale": (12, 0, (214, 204, 172)),
    "gold": (0, 4, (156, 134, 84)),
    "stud": (4, 4, (150, 150, 158)),
    "lining": (8, 4, (44, 40, 34)),
}


def robe_palette():
    rnd = random.Random(9)
    img = Image.new("RGBA", (16, 16))
    for x0, y0, (r, g, b) in ROBE_SWATCH.values():
        for y in range(y0, y0 + 4):
            for x in range(x0, x0 + 4):
                d = rnd.randint(-5, 5)
                img.putpixel((x, y), (max(0, r + d), max(0, g + d), max(0, b + d), 255))
    return img


class Robe:
    def __init__(self):
        self.elements = []

    def box(self, x0, y0, z0, x1, y1, z1, mat, rotation=None):
        u, v, _ = ROBE_SWATCH[mat]
        uv = [u + 0.5, v + 0.5, u + 3.5, v + 3.5]
        element = {
            "from": [round(x0, 3), round(y0, 3), round(z0, 3)],
            "to": [round(x1, 3), round(y1, 3), round(z1, 3)],
            "faces": {f: {"uv": uv, "texture": "#0"} for f in ("north", "east", "south", "west", "up", "down")},
        }
        if rotation:
            element["rotation"] = rotation
        for c in element["from"] + element["to"]:
            assert -16 <= c <= 32, (mat, c)
        self.elements.append(element)


def robe_model():
    """The coat in model units (16 = a block): feet at y 0, the body's middle at x = z = 8,
    the front towards +z (south), as an item display facing its wearer's way."""
    m = Robe()
    # The bell of the coat, in tiers, widening to the ground.
    tiers = [(0, 5, 0.0, 1.0), (5, 10, 1.0, 2.0), (10, 15, 2.0, 3.0), (15, 22, 3.2, 3.8)]
    for y0, y1, inset_x, inset_z in tiers:
        m.box(0 + inset_x, y0, 1.2 + inset_z, 16 - inset_x, y1, 14.8 - inset_z, "black")
    # The tall collar where the head would be: thin plates round an open middle, flaring out.
    m.box(4.6, 22, 4.2, 11.4, 23.2, 11.8, "dark")
    m.box(4.0, 22, 4.4, 4.9, 28.0, 11.6, "dark", {"angle": -22.5, "axis": "z", "origin": [4.5, 22, 8]})
    m.box(11.1, 22, 4.4, 12.0, 28.0, 11.6, "dark", {"angle": 22.5, "axis": "z", "origin": [11.5, 22, 8]})
    m.box(4.6, 22, 3.6, 11.4, 29.0, 4.5, "dark", {"angle": -22.5, "axis": "x", "origin": [8, 22, 4.0]})
    m.box(4.6, 22, 11.5, 7.6, 27.0, 12.4, "dark", {"angle": 22.5, "axis": "x", "origin": [8, 22, 12.0]})
    m.box(8.4, 22, 11.5, 11.4, 27.0, 12.4, "dark", {"angle": 22.5, "axis": "x", "origin": [8, 22, 12.0]})
    m.box(7.6, 22, 11.6, 7.9, 26.8, 12.5, "pale", {"angle": 22.5, "axis": "x", "origin": [8, 22, 12.0]})
    m.box(8.1, 22, 11.6, 8.4, 26.8, 12.5, "pale", {"angle": 22.5, "axis": "x", "origin": [8, 22, 12.0]})
    # Pale and gold stripes down the front and back of every tier, studs down the middle.
    for y0, y1, inset_x, inset_z in tiers:
        front, back = 14.8 - inset_z, 1.2 + inset_z
        for x in (inset_x + 1.2, 16 - inset_x - 1.65, 5.2, 10.35):
            if inset_x + 0.5 < x < 16 - inset_x - 0.5:
                m.box(x, y0 + 0.1, front, x + 0.45, y1 - 0.1, front + 0.15, "pale")
                m.box(x, y0 + 0.1, back - 0.15, x + 0.45, y1 - 0.1, back, "pale")
        for x in (7.0, 8.55):
            m.box(x, y0, front, x + 0.45, y1, front + 0.2, "gold")
        m.box(inset_x - 0.15, y0 + 0.1, 7.6, inset_x, y1 - 0.1, 8.4, "pale")
        m.box(16 - inset_x, y0 + 0.1, 7.6, 16 - inset_x + 0.15, y1 - 0.1, 8.4, "pale")
    for y in range(2, 21, 2):
        z = 14.8 - next(iz for y0, y1, ix, iz in tiers if y0 <= y < y1) + 0.2
        m.box(7.7, y, z, 8.3, y + 0.5, z + 0.25, "stud")
    m.box(-0.1, 0, 1.0, 16.1, 0.6, 15.0, "pale")
    # Wide sleeves hanging from the shoulders, drooping outwards, with pale cuffs.
    for side in (-1, 1):
        x_in = 3.4 if side < 0 else 12.6
        x_out = x_in + side * 9.0
        x0, x1 = min(x_in, x_out), max(x_in, x_out)
        origin = [x_in, 20, 8]
        rot = {"angle": -22.5 * side, "axis": "z", "origin": origin}
        m.box(x0, 14.5, 4.5, x1, 21.0, 11.5, "black", rot)
        cuff0, cuff1 = (x0, x0 + 1.2) if side < 0 else (x1 - 1.2, x1)
        m.box(cuff0, 13.6, 3.8, cuff1, 21.6, 12.2, "pale", rot)
        m.box(cuff0 + (0.3 if side < 0 else -0.3), 13.9, 4.1, cuff1 - (0.3 if side > 0 else -0.3), 21.3, 11.9, "lining", rot)
        # Gloved hands open at the sleeve ends.
        hand = x0 - 1.6 if side < 0 else x1
        m.box(hand, 15.0, 6.6, hand + 1.6, 16.4, 9.4, "dark", rot)
    # Black ribbons trailing out behind and to the sides, down to the ground.
    for side in (-1, 1):
        for i, (y, length, slope) in enumerate(((18.0, 13.0, 22.5), (11.0, 12.0, 45.0))):
            x0 = 8 + side * 6
            x1 = x0 + side * length
            lo, hi = min(x0, x1), max(x0, x1)
            z = 3.0 - i * 1.5
            m.box(lo, y, z, hi, y + 1.6, z + 1.0, "black",
                  {"angle": slope * -side, "axis": "z", "origin": [x0, y + 0.8, z + 0.5]})
            m.box(lo, y + 0.6, z - 0.05, hi, y + 0.9, z + 0.02, "pale",
                  {"angle": slope * -side, "axis": "z", "origin": [x0, y + 0.8, z + 0.5]})
    return m


def robe3d():
    tex = PACK / "textures/item/black_god_robe.png"
    tex.parent.mkdir(parents=True, exist_ok=True)
    robe_palette().save(tex)
    t = f"{NS}:item/black_god_robe"
    write(PACK / "models/item/black_god_robe.json", {"textures": {"0": t, "particle": t}, "elements": robe_model().elements})
    write(PACK / "items/black_god_robe.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/black_god_robe"}})


# --- revival overlays ---------------------------------------------------------------

def glitch_frames(count=4, w=256, h=144):
    """Camera overlays for the revival: the screen breaking into dark and golden blocks."""
    for k in range(count):
        rnd = random.Random(100 + k)
        img = Image.new("RGBA", (w, h))
        d = ImageDraw.Draw(img)
        for _ in range(26 + k * 6):
            bw, bh = rnd.randint(8, 46), rnd.randint(4, 14)
            x, y = rnd.randint(-10, w), rnd.randint(-4, h)
            if rnd.random() < 0.55:
                colour = (8, 8, 10, rnd.randint(200, 255))
            else:
                g = rnd.randint(150, 240)
                colour = (g, int(g * 0.8), int(g * 0.45), rnd.randint(110, 200))
            d.rectangle((x, y, x + bw, y + bh), fill=colour)
        for _ in range(5):
            y = rnd.randint(0, h)
            d.rectangle((0, y, w, y + 1), fill=(255, 236, 180, 150))
        path = PACK / f"textures/misc/glitch_{k}.png"
        path.parent.mkdir(parents=True, exist_ok=True)
        img.save(path)
    flash = Image.new("RGBA", (16, 9), (255, 250, 236, 235))
    flash.save(PACK / "textures/misc/white_flash.png")


if __name__ == "__main__":
    halo()
    robe3d()
    glitch_frames()
    if len(sys.argv) > 1:
        sheet = Image.new("RGBA", (420, 200), (90, 96, 110, 255))
        h = Image.open(PACK / "textures/item/black_god_halo.png").resize((192, 192), Image.NEAREST)
        sheet.alpha_composite(h, (4, 4))
        sheet.save(sys.argv[1])
    print("done")
