"""Draws the clash UI after Limbus Company for the Java resource pack.

- font "clash": the panel beside a skill's user (see paper/.../Clash.java):
  coins, the jagged badge holding the clash power, big digits, the banner
  the skill's name sits on (left cap, segment, right cap), small Sanity
  circles, and spaces to shift by. The badge and banner are drawn white
  and grey so the text colour tints them in the skill's sin colour.
- font "clash_count": the clash count between the two, a glowing brush
  numeral with the word 합 (1 to 20).
- font "sanity": the HUD Sanity circle at the bottom left (blue at 0 and up,
  red below), one glyph per value from -45 to 45, plus its spaces.

    python3 tools/clash_font.py paper/pack/assets/index_prescript
"""
import json
import math
import os
import random
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

SERIF_BOLD_ITALIC = "/usr/share/fonts/truetype/freefont/FreeSerifBoldItalic.ttf"
HEI = "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc"

DIGITS5x7 = {
    "0": ["01110", "10001", "10011", "10101", "11001", "10001", "01110"],
    "1": ["00100", "01100", "00100", "00100", "00100", "00100", "01110"],
    "2": ["01110", "10001", "00001", "00010", "00100", "01000", "11111"],
    "3": ["11110", "00001", "00001", "01110", "00001", "00001", "11110"],
    "4": ["00010", "00110", "01010", "10010", "11111", "00010", "00010"],
    "5": ["11111", "10000", "11110", "00001", "00001", "10001", "01110"],
    "6": ["00110", "01000", "10000", "11110", "10001", "10001", "01110"],
    "7": ["11111", "00001", "00010", "00100", "01000", "01000", "01000"],
    "8": ["01110", "10001", "10001", "01110", "10001", "10001", "01110"],
    "9": ["01110", "10001", "10001", "01111", "00001", "00010", "01100"],
    "-": ["00000", "00000", "00000", "11111", "00000", "00000", "00000"],
    "?": ["01110", "10001", "00001", "00110", "00100", "00000", "00100"],
}

FONT_FIRST = 0xE400
COIN_CHARS = [chr(0xE401 + i) for i in range(10)]
BADGE = chr(0xE410)
BIG_DIGITS = [chr(0xE420 + i) for i in range(11)]  # 0-9 and ?
BANNER_L, BANNER_M, BANNER_R = chr(0xE430), chr(0xE431), chr(0xE432)
SP_FIRST = 0xE500
COUNT_FIRST = 0xE600
CLASH_PLUS, CLASH_MINUS = 0xE4A0, 0xE4B0
SANITY_FIRST = 0xE300
SANITY_PLUS, SANITY_MINUS = 0xE3A0, 0xE3B0


def spaces(plus, minus, bits=11):
    out = {}
    for bit in range(bits):
        out[chr(plus + bit)] = 1 << bit
        out[chr(minus + bit)] = -(1 << bit)
    return out


def digits_pixels(s, scale=1, gap=1):
    """Pixels of a string in the 5x7 digit font, from (0, 0)."""
    pixels = []
    x = 0
    for ch in s:
        for cy, row in enumerate(DIGITS5x7[ch]):
            for cx, bit in enumerate(row):
                if bit == "1":
                    for dx in range(scale):
                        for dy in range(scale):
                            pixels.append((x + cx * scale + dx, cy * scale + dy))
        x += 5 * scale + gap
    return pixels, x - gap


def outlined(img, pixels, ox, oy, fill, outline):
    w, h = img.size
    for px, py in pixels:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                x, y = px + ox + dx, py + oy + dy
                if 0 <= x < w and 0 <= y < h and img.getpixel((x, y))[3] == 0:
                    img.putpixel((x, y), outline)
    for px, py in pixels:
        x, y = px + ox, py + oy
        if 0 <= x < w and 0 <= y < h:
            img.putpixel((x, y), fill)


# --- the panel ------------------------------------------------------------------------------

def octagon_points(cx, cy, r):
    return [(cx + r * math.cos(math.radians(22.5 + 45 * k)), cy + r * math.sin(math.radians(22.5 + 45 * k))) for k in range(8)]


def coin(kind):
    """Coins drawn white (tinted by the text colour): round plain coins, octagonal unbreakable ones, cracked forms."""
    s = 14
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if kind == "spin_a":
        d.ellipse([4, 1, 9, 12], fill=(255, 220, 90, 255), outline=(90, 60, 10, 255))
        return img
    if kind == "spin_b":
        d.ellipse([2, 3, 11, 10], fill=(255, 200, 60, 255), outline=(90, 60, 10, 255))
        return img
    if kind in ("crack", "split", "shards", "dust"):
        return breaking(kind)
    dark = (20, 12, 12, 255)
    body, inner = (255, 255, 255, 255), (200, 200, 200, 255)
    if kind.startswith("round"):
        d.ellipse([1, 1, 12, 12], fill=body, outline=dark)
        d.ellipse([3, 3, 10, 10], outline=inner)
    else:
        d.polygon(octagon_points(6.5, 6.5, 6.6), fill=body, outline=dark)
        d.polygon(octagon_points(6.5, 6.5, 4.0), outline=inner)
        # The emblem the original's unbreakable coins carry.
        d.line([(6, 4), (6, 9)], fill=inner)
        d.line([(4, 6), (9, 6)], fill=inner)
    if kind.endswith("cracked"):
        d.line([(5, 1), (7, 5), (5, 8), (7, 12)], fill=dark, width=1)
        d.line([(7, 5), (11, 6)], fill=dark)
        d.line([(5, 8), (2, 9)], fill=dark)
    return img


def breaking(kind):
    """The frames of a coin being destroyed: it cracks, splits, flies apart and fades."""
    s = 14
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    red, rim, dark = (215, 40, 40, 255), (255, 200, 190, 255), (20, 10, 10, 255)
    if kind == "crack":
        d.ellipse([1, 1, 12, 12], fill=(240, 90, 80, 255), outline=dark)
        d.line([(6, 1), (7, 5), (5, 8), (7, 12)], fill=(255, 255, 255, 255), width=1)
        d.line([(7, 5), (10, 6)], fill=(255, 255, 255, 255))
        d.line([(5, 8), (2, 9)], fill=(255, 255, 255, 255))
    elif kind == "split":
        # Two halves pulling apart, tilted.
        d.pieslice([-1, 1, 10, 12], 90, 270, fill=red, outline=dark)
        d.pieslice([3, 0, 14, 11], 270, 90, fill=red, outline=dark)
        d.line([(4, 2), (4, 11)], fill=rim)
        d.line([(9, 1), (9, 10)], fill=rim)
    elif kind == "shards":
        for (x, y, w, h) in [(0, 1, 3, 3), (10, 0, 3, 3), (1, 10, 3, 3), (10, 10, 3, 3), (5, 5, 3, 2), (6, 0, 2, 2), (0, 6, 2, 2), (12, 6, 2, 2)]:
            d.rectangle([x, y, x + w - 1, y + h - 1], fill=red, outline=dark)
        for x, y in [(3, 4), (9, 3), (4, 9), (9, 9)]:
            img.putpixel((x, y), (255, 240, 200, 255))
    else:
        for (x, y) in [(0, 0), (13, 1), (1, 13), (12, 12), (6, 13), (13, 6), (0, 7), (7, 0)]:
            img.putpixel((x, y), (255, 120, 100, 150))
    return img


def badge():
    """A jagged hexagon: white rim, grey fill (both tinted by the text colour)."""
    w, h = 32, 34
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx, cy = w / 2, h / 2
    pts = []
    for i in range(6):
        a = math.radians(60 * i - 90)
        pts.append((cx + math.cos(a) * 15, cy + math.sin(a) * 16.5))
    d.polygon(pts, fill=(255, 255, 255, 255))
    inner = [(cx + (x - cx) * 0.82, cy + (y - cy) * 0.82) for x, y in pts]
    d.polygon(inner, fill=(70, 70, 70, 235))
    random.seed(7)
    # Ragged rim, like the brushed edge of the original.
    for i in range(40):
        a = random.uniform(0, math.tau)
        r = random.uniform(14.5, 16.5)
        x, y = cx + math.cos(a) * r, cy + math.sin(a) * r * 1.08
        d.point((x, y), fill=(255, 255, 255, 255))
    img.putpixel((w - 1, h - 1), (0, 0, 0, 1))
    return img


def big_digit(ch):
    w, h = 12, 18
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    pixels, _ = digits_pixels(ch, scale=2, gap=0)
    outlined(img, pixels, 1, 2, (255, 255, 255, 255), (15, 10, 10, 255))
    img.putpixel((w - 1, h - 1), (0, 0, 0, 1))
    return img


def banner(part):
    h = 14
    w = {"l": 6, "m": 8, "r": 10}[part]
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for x in range(w):
        for y in range(1, h - 1):
            if part == "l" and y < 1 + (w - 1 - x) // 2:
                continue
            if part == "r" and x > w - 1 - (y - 1) // 1.4:
                continue
            shade = 255 if 3 <= y <= h - 5 else 200
            img.putpixel((x, y), (shade, shade, shade, 240))
        if img.getpixel((x, h - 2))[3]:
            img.putpixel((x, h - 2), (120, 120, 120, 255))
    return img


DIGITS3x5 = {
    "0": ["111", "101", "101", "101", "111"], "1": ["010", "110", "010", "010", "111"],
    "2": ["111", "001", "111", "100", "111"], "3": ["111", "001", "111", "001", "111"],
    "4": ["101", "101", "111", "001", "001"], "5": ["111", "100", "111", "001", "111"],
    "6": ["111", "100", "111", "101", "111"], "7": ["111", "001", "010", "010", "010"],
    "8": ["111", "101", "111", "101", "111"], "9": ["111", "101", "111", "001", "111"],
    "-": ["000", "000", "111", "000", "000"],
}


def small_pixels(s):
    pixels = []
    x = 0
    for ch in s:
        for cy, row in enumerate(DIGITS3x5[ch]):
            for cx, bit in enumerate(row):
                if bit == "1":
                    pixels.append((x + cx, cy))
        x += 4
    return pixels, x - 1


def sp_circle(value, size):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    fill = (40, 110, 210) if value >= 0 else (200, 40, 40)
    ring = (170, 210, 255) if value >= 0 else (255, 150, 150)
    d.ellipse([0, 0, size - 1, size - 1], fill=(15, 15, 25, 255))
    d.ellipse([1, 1, size - 2, size - 2], fill=fill + (255,), outline=ring + (255,))
    if size < 20:
        pixels, width = small_pixels(str(value))
        outlined(img, pixels, (size - width) // 2, (size - 5) // 2, (255, 255, 255, 255), (10, 10, 30, 255))
    else:
        pixels, width = digits_pixels(str(value), scale=1, gap=1)
        outlined(img, pixels, (size - width) // 2, (size - 7) // 2, (255, 255, 255, 255), (10, 10, 30, 255))
    return img


# --- the clash count ------------------------------------------------------------------------

def count_glyph(n):
    w, h = 132, 64
    big = 4
    canvas = Image.new("L", (w * big, h * big), 0)
    d = ImageDraw.Draw(canvas)
    numeral = ImageFont.truetype(SERIF_BOLD_ITALIC, 60 * big)
    word = ImageFont.truetype(HEI, 36 * big)
    text = str(n)
    bbox = d.textbbox((0, 0), text, font=numeral)
    tx = 6 * big - bbox[0]
    ty = (h * big - (bbox[3] - bbox[1])) // 2 - bbox[1]
    d.text((tx, ty), text, font=numeral, fill=255, stroke_width=3 * big, stroke_fill=255)
    nx = tx + bbox[2] + 2 * big
    d.text((nx, 18 * big), "합", font=word, fill=255, stroke_width=2 * big, stroke_fill=255)
    # Ink: roughen the strokes with a few drips and splashes.
    random.seed(n)
    for _ in range(14):
        x = random.randint(4 * big, (w - 8) * big)
        y = random.randint(4 * big, (h - 8) * big)
        r = random.randint(1 * big, 3 * big)
        if canvas.getpixel((min(w * big - 1, x), min(h * big - 1, y))) > 0:
            d.ellipse([x - r, y - r, x + r * 2, y + r], fill=255)
    ink = canvas.resize((w, h), Image.LANCZOS)
    glow = ink.filter(ImageFilter.MaxFilter(5)).filter(ImageFilter.GaussianBlur(1.6))
    rim = ImageChops.subtract(ink.filter(ImageFilter.MaxFilter(3)), ink)
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    gold = Image.new("RGBA", (w, h), (255, 200, 80, 0))
    gold.putalpha(glow.point(lambda v: int(v * 0.85)))
    img = Image.alpha_composite(img, gold)
    edge = Image.new("RGBA", (w, h), (255, 235, 170, 0))
    edge.putalpha(rim)
    img = Image.alpha_composite(img, edge)
    body = Image.new("RGBA", (w, h), (35, 24, 18, 0))
    body.putalpha(ink.point(lambda v: 255 if v > 110 else int(v * 2)))
    img = Image.alpha_composite(img, body)
    img.putpixel((w - 1, h - 1), (0, 0, 0, 1))
    return img


# --- writing --------------------------------------------------------------------------------

def strip(images):
    w, h = images[0].size
    out = Image.new("RGBA", (w * len(images), h), (0, 0, 0, 0))
    for i, im in enumerate(images):
        out.paste(im, (i * w, 0))
    return out


def grid(images, cols):
    w, h = images[0].size
    rows = (len(images) + cols - 1) // cols
    out = Image.new("RGBA", (w * cols, h * rows), (0, 0, 0, 0))
    for i, im in enumerate(images):
        out.paste(im, ((i % cols) * w, (i // cols) * h))
    return out, rows


def chars_grid(first, count, cols, rows):
    lines = []
    for r in range(rows):
        line = ""
        for c in range(cols):
            i = r * cols + c
            line += chr(first + i) if i < count else "\u0000"
        lines.append(line)
    return lines


def main(assets):
    tex = os.path.join(assets, "textures/font")
    os.makedirs(tex, exist_ok=True)
    os.makedirs(os.path.join(assets, "font"), exist_ok=True)

    strip([coin(k) for k in ["round", "octagon", "round_cracked", "octagon_cracked", "spin_a", "spin_b", "crack", "split", "shards", "dust"]]).save(os.path.join(tex, "clash_coins.png"))
    badge().save(os.path.join(tex, "clash_badge.png"))
    strip([big_digit(c) for c in "0123456789?"]).save(os.path.join(tex, "clash_digits.png"))
    for part in "lmr":
        banner(part).save(os.path.join(tex, "clash_banner_%s.png" % part))
    values = list(range(-45, 46))
    sp_atlas, sp_rows = grid([sp_circle(v, 16) for v in values], 13)
    sp_atlas.save(os.path.join(tex, "clash_sp.png"))

    clash = {"providers": [
        {"type": "space", "advances": spaces(CLASH_PLUS, CLASH_MINUS)},
        {"type": "bitmap", "file": "index_prescript:font/clash_coins.png", "height": 12, "ascent": 12, "chars": ["".join(COIN_CHARS)]},
        {"type": "bitmap", "file": "index_prescript:font/clash_badge.png", "height": 32, "ascent": 20, "chars": [BADGE]},
        {"type": "bitmap", "file": "index_prescript:font/clash_digits.png", "height": 16, "ascent": 12, "chars": ["".join(BIG_DIGITS)]},
        {"type": "bitmap", "file": "index_prescript:font/clash_banner_l.png", "height": 14, "ascent": 10, "chars": [BANNER_L]},
        {"type": "bitmap", "file": "index_prescript:font/clash_banner_m.png", "height": 14, "ascent": 10, "chars": [BANNER_M]},
        {"type": "bitmap", "file": "index_prescript:font/clash_banner_r.png", "height": 14, "ascent": 10, "chars": [BANNER_R]},
        {"type": "bitmap", "file": "index_prescript:font/clash_sp.png", "height": 14, "ascent": 10,
         "chars": chars_grid(SP_FIRST, len(values), 13, sp_rows)},
    ]}
    with open(os.path.join(assets, "font/clash.json"), "w", encoding="utf-8") as f:
        json.dump(clash, f, indent="\t")

    counts, count_rows = grid([count_glyph(n) for n in range(1, 21)], 5)
    counts.save(os.path.join(tex, "clash_count.png"))
    with open(os.path.join(assets, "font/clash_count.json"), "w", encoding="utf-8") as f:
        json.dump({"providers": [{"type": "bitmap", "file": "index_prescript:font/clash_count.png", "height": 48, "ascent": 40,
                                  "chars": chars_grid(COUNT_FIRST, 20, 5, count_rows)}]}, f, indent="\t")

    # The pink boss bar drawn see-through: its name alone shows (the TOTAL taken, top right).
    sprites = os.path.join(assets, "..", "minecraft", "textures", "gui", "sprites", "boss_bar")
    os.makedirs(sprites, exist_ok=True)
    for name in ("pink_background.png", "pink_progress.png"):
        Image.new("RGBA", (182, 5), (0, 0, 0, 0)).save(os.path.join(sprites, name))

    # The HUD readout at the bottom left: the original's Sanity circle.
    hud = [sp_circle(v, 22) for v in values]
    for im in hud:
        im.putpixel((21, 21), im.getpixel((21, 21))[:3] + (max(1, im.getpixel((21, 21))[3]),))
    hud_atlas, hud_rows = grid(hud, 13)
    hud_atlas.save(os.path.join(tex, "sanity.png"))
    with open(os.path.join(assets, "font/sanity.json"), "w", encoding="utf-8") as f:
        json.dump({"providers": [
            {"type": "space", "advances": spaces(SANITY_PLUS, SANITY_MINUS)},
            {"type": "bitmap", "file": "index_prescript:font/sanity.png", "height": 22, "ascent": -43,
             "chars": chars_grid(SANITY_FIRST, len(values), 13, hud_rows)},
        ]}, f, indent="\t")


if __name__ == "__main__":
    main(sys.argv[1])
