"""Generates the base Caduceus and terminal textures and the mod icon.

Usage: python3 tools/textures.py src/main/resources/assets/index_prescript
"""
import sys
from PIL import Image

out = sys.argv[1]

pal = {
    ".": (0, 0, 0, 0),
    "K": (24, 24, 28, 255),     # outline
    # terminal
    "W": (236, 238, 240, 255),  # body light
    "L": (196, 200, 206, 255),  # body mid
    "G": (140, 146, 156, 255),  # body shade
    "N": (20, 34, 52, 255),     # screen
    "C": (80, 210, 250, 255),   # asterisk
    "c": (40, 110, 160, 255),   # asterisk glow
    # weapon
    "Y": (226, 214, 150, 255),  # brass light
    "O": (170, 150, 90, 255),   # brass mid
    "D": (110, 95, 55, 255),    # brass dark / chain
    "I": (16, 16, 20, 255),     # ink
    "i": (70, 72, 82, 255),     # ink highlight
}

terminal = [
    "................",
    "................",
    "..........KKKKK.",
    ".........KWWWWLK",
    ".KKKKKKKKKWLLLLK",
    ".KWWWWWWWWWWWWLK",
    ".KWKKKKKKKKKKWGK",
    ".KWKcNcNcNcNKLGK",
    ".KWKCcCcCcCcKLGK",
    ".KWKcNcNcNcNKLGK",
    ".KWKKKKKKKKKKLGK",
    ".KLLLLLLLLLLLLGK",
    ".KGGGGGGGGGGGGGK",
    "..KKKKKKKKKKKKK.",
    "................",
    "................",
]

weapon = [
    "......D.D.D.....",
    "....D.......KKK.",
    "...D.......KYYK.",
    "..D.......KYOKK.",
    "..D......KYOK...",
    "...D....KYOK....",
    "....D..KDDK.....",
    ".....DKYOK......",
    "....iKYOK.......",
    "...IKDDKI.......",
    "..IKYOKIIi......",
    ".IKYOKIII.......",
    "IIKKKIIi...i....",
    ".IIIIIIIIIIII...",
    "..iII...IIIIIIi.",
    "...........iI...",
]


def draw(rows):
    img = Image.new("RGBA", (16, 16))
    for y, row in enumerate(rows):
        assert len(row) == 16, (y, row)
        for x, ch in enumerate(row):
            img.putpixel((x, y), pal[ch])
    return img


draw(weapon).save(f"{out}/textures/item/caduceus.png")
draw(terminal).save(f"{out}/textures/item/rien_terminal.png")
draw(terminal).resize((128, 128), Image.NEAREST).save(f"{out}/icon.png")
