#!/usr/bin/env python3
"""Assets for Black God mode (fan-made, after Limbus Company), into the plugin pack:

- the halo: a sunburst behind the head (a black disc in a white-gold glow,
  a stepped golden burst, dark spikes and long pale rays), drawn on one flat,
  glowing, two-sided element so an item display can carry it;
- the robe: a long black coat with pale and gold stripes and studs, as a
  custom equipment asset worn on the chest, legs and feet.

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


# --- robe -----------------------------------------------------------------------

def robe_layer(legs):
    """A 64x32 armour layer: everything but the helmet part, as a striped black coat."""
    rnd = random.Random(5 if legs else 3)
    img = Image.new("RGBA", (64, 32))
    for y in range(16, 32):
        for x in range(0, 56):
            n = rnd.randint(-6, 6)
            base = (26 + n, 25 + n, 28 + n, 255)
            if rnd.random() < 0.18:
                base = (54 + n, 48 + n, 40 + n, 255)
            column = x % 7
            if column == 2:
                base = (208, 198, 168, 255)
            elif column == 3:
                base = (140, 120, 76, 255)
            img.putpixel((x, y), base)
    # Studs down the front of the coat and its edges.
    for y in range(21, 32, 3):
        for x in (19, 23, 24, 28):
            img.putpixel((x, y), (150, 150, 156, 255))
    # A pale hem.
    for x in range(0, 56):
        img.putpixel((x, 31), (196, 186, 150, 255))
    return img


def robe():
    for layer, legs in (("humanoid", False), ("humanoid_leggings", True)):
        path = PACK / "textures/entity/equipment" / layer / "black_god_robe.png"
        path.parent.mkdir(parents=True, exist_ok=True)
        robe_layer(legs).save(path)
    write(PACK / "equipment/black_god_robe.json", {"layers": {
        "humanoid": [{"texture": f"{NS}:black_god_robe"}],
        "humanoid_leggings": [{"texture": f"{NS}:black_god_robe"}],
    }})


if __name__ == "__main__":
    halo()
    robe()
    if len(sys.argv) > 1:
        sheet = Image.new("RGBA", (420, 200), (90, 96, 110, 255))
        h = Image.open(PACK / "textures/item/black_god_halo.png").resize((192, 192), Image.NEAREST)
        sheet.alpha_composite(h, (4, 4))
        r = Image.open(PACK / "textures/entity/equipment/humanoid/black_god_robe.png").resize((192, 96), Image.NEAREST)
        sheet.alpha_composite(r, (210, 50))
        sheet.save(sys.argv[1])
    print("done")
