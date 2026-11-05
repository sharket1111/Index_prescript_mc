"""Draws the Sanity (SP) readout glyphs for the Java resource pack.

One glyph per value from -45 to 45: a small face (smiling, plain or frowning
by band, tinted like the original's SP colours) and the signed number, all
58x16 pixels. The rightmost column carries an almost invisible pixel so every
glyph has the same advance. Also writes the font with the space glyphs the
plugin shifts it with (see paper/.../Hud.java).

    python3 tools/sanity_font.py paper/pack/assets/index_prescript
"""
import json
import os
import sys

from PIL import Image

W, H = 58, 16
COLS = 13
FIRST = 0xE300
PLUS, MINUS = 0xE3A0, 0xE3B0

DIGITS = {
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
    "+": ["00000", "00100", "00100", "11111", "00100", "00100", "00000"],
    "-": ["00000", "00000", "00000", "11111", "00000", "00000", "00000"],
}


def colour(value):
    if value >= 30:
        return (255, 214, 64)
    if value >= 10:
        return (255, 236, 150)
    if value > -10:
        return (235, 235, 235)
    if value > -30:
        return (255, 150, 80)
    return (235, 60, 60)


def put(img, x, y, rgba):
    if 0 <= x < W and 0 <= y < H:
        img.putpixel((x, y), rgba)


def face(img, value):
    c = colour(value) + (255,)
    dark = (25, 20, 20, 255)
    cx, cy, r = 7.5, 7.5, 7.0
    for y in range(16):
        for x in range(16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d <= r - 1.0:
                put(img, x, y, c)
            elif d <= r + 0.2:
                put(img, x, y, dark)
    for ex in (5, 10):
        put(img, ex, 5, dark)
        put(img, ex, 6, dark)
    if value >= 10:
        mouth = [(4, 9), (5, 10), (6, 11), (7, 11), (8, 11), (9, 11), (10, 10), (11, 9)]
    elif value > -10:
        mouth = [(5, 10), (6, 10), (7, 10), (8, 10), (9, 10), (10, 10)]
    else:
        mouth = [(4, 12), (5, 11), (6, 10), (7, 10), (8, 10), (9, 10), (10, 11), (11, 12)]
    for x, y in mouth:
        put(img, x, y, dark)


def text(img, s, x0, value):
    c = colour(value) + (255,)
    outline = (20, 15, 15, 255)
    # 5x7 digits drawn at 2x (10x14), each with a dark outline.
    x = x0
    for ch in s:
        rows = DIGITS[ch]
        pixels = [(x + cx * 2 + dx, 1 + cy * 2 + dy)
                  for cy, row in enumerate(rows) for cx, bit in enumerate(row) if bit == "1"
                  for dx in (0, 1) for dy in (0, 1)]
        for px, py in pixels:
            for ox in (-1, 0, 1):
                for oy in (-1, 0, 1):
                    if img.getpixel((min(W - 1, max(0, px + ox)), min(H - 1, max(0, py + oy))))[3] == 0:
                        put(img, px + ox, py + oy, outline)
        for px, py in pixels:
            put(img, px, py, c)
        x += 12


def glyph(value):
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    face(img, value)
    s = ("+" if value > 0 else "") + str(value)
    text(img, s, 19, value)
    img.putpixel((W - 1, H - 1), (0, 0, 0, 1))
    return img


def main(assets):
    values = list(range(-45, 46))
    rows = (len(values) + COLS - 1) // COLS
    atlas = Image.new("RGBA", (W * COLS, H * rows), (0, 0, 0, 0))
    chars = []
    for row in range(rows):
        line = ""
        for col in range(COLS):
            i = row * COLS + col
            if i < len(values):
                atlas.paste(glyph(values[i]), (col * W, row * H))
                line += chr(FIRST + i)
            else:
                line += "\u0000"
        chars.append(line)
    os.makedirs(os.path.join(assets, "textures/font"), exist_ok=True)
    atlas.save(os.path.join(assets, "textures/font/sanity.png"))
    advances = {}
    for bit in range(11):
        advances[chr(PLUS + bit)] = 1 << bit
        advances[chr(MINUS + bit)] = -(1 << bit)
    font = {"providers": [
        {"type": "space", "advances": advances},
        {"type": "bitmap", "file": "index_prescript:font/sanity.png", "height": 16, "ascent": -45, "chars": chars},
    ]}
    os.makedirs(os.path.join(assets, "font"), exist_ok=True)
    with open(os.path.join(assets, "font/sanity.json"), "w", encoding="utf-8") as f:
        json.dump(font, f, indent="\t")


if __name__ == "__main__":
    main(sys.argv[1])
