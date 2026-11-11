"""Font "md_hpbar": the Mirror Dungeon enemies' health bar, after Limbus Company's
(a long thin bar with the HP number in front, stagger marks on it, and a round
level badge at its end). See paper/.../MdHpBar.java.

- U+E800..U+E832: the bar, 0/50 to 50/50 full (drawn white: the text colour makes it red)
- U+E840: a stagger mark; U+E841: the level badge
- U+E850.., U+E860..: spaces of +1, +2, +4 ... and -1, -2, -4 ... pixels

    python3 tools/md_hpbar.py paper/pack/assets/index_prescript
"""
import json
import os
import sys

from PIL import Image, ImageDraw

BAR_FIRST = 0xE800
STEPS = 50
TICK, BADGE = 0xE840, 0xE841
PLUS, MINUS = 0xE850, 0xE860
WIDTH, HEIGHT = 82, 5


def bar(fill):
    img = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))
    g = ImageDraw.Draw(img)
    g.rectangle([0, 0, WIDTH - 1, HEIGHT - 1], fill=(40, 40, 40, 230))
    g.rectangle([1, 1, WIDTH - 2, HEIGHT - 2], fill=(70, 70, 70, 200))
    n = round((WIDTH - 2) * fill / STEPS)
    if n > 0:
        g.rectangle([1, 1, n, HEIGHT - 2], fill=(255, 255, 255, 255))
        g.line([1, 1, n, 1], fill=(255, 230, 230, 255))
    return img


def tick():
    img = Image.new("RGBA", (1, 7), (255, 255, 255, 255))
    return img


def badge():
    img = Image.new("RGBA", (15, 15), (0, 0, 0, 0))
    g = ImageDraw.Draw(img)
    g.ellipse([0, 0, 14, 14], fill=(170, 172, 180, 255))
    g.ellipse([1, 1, 13, 13], fill=(34, 34, 40, 255))
    g.arc([2, 2, 12, 12], 200, 340, fill=(90, 92, 100, 255))
    return img


def strip(images):
    w = max(i.width for i in images)
    out = Image.new("RGBA", (w * len(images), max(i.height for i in images)), (0, 0, 0, 0))
    for k, i in enumerate(images):
        out.paste(i, (k * w, 0))
    return out


def main(assets):
    tex = os.path.join(assets, "textures/font")
    os.makedirs(tex, exist_ok=True)
    strip([bar(i) for i in range(STEPS + 1)]).save(os.path.join(tex, "md_hpbar.png"))
    tick().save(os.path.join(tex, "md_tick.png"))
    badge().save(os.path.join(tex, "md_badge.png"))
    advances = {}
    for bit in range(8):
        advances[chr(PLUS + bit)] = 1 << bit
        advances[chr(MINUS + bit)] = -(1 << bit)
    font = {"providers": [
        {"type": "space", "advances": advances},
        {"type": "bitmap", "file": "index_prescript:font/md_hpbar.png", "height": HEIGHT, "ascent": 5,
         "chars": ["".join(chr(BAR_FIRST + i) for i in range(STEPS + 1))]},
        {"type": "bitmap", "file": "index_prescript:font/md_tick.png", "height": 7, "ascent": 6, "chars": [chr(TICK)]},
        {"type": "bitmap", "file": "index_prescript:font/md_badge.png", "height": 13, "ascent": 9, "chars": [chr(BADGE)]},
    ]}
    with open(os.path.join(assets, "font/md_hpbar.json"), "w", encoding="utf-8") as f:
        json.dump(font, f, indent="\t")
    print("md_hpbar font written")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "paper/pack/assets/index_prescript")
