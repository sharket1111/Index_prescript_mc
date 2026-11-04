#!/usr/bin/env python3
"""Assets for the Brand Manager's hammer mechanics (fan-made, after Limbus Company):

- the [Promised Nail]: a glowing golden nail, point down, shown by item
  displays as it falls on a foe and while it stays driven in
  (paper/pack, plugin only);
- the [Black God's Voice] icon held in the off hand, whose stack size is the
  value (src/main/resources, so Bedrock gets it through tools/bedrock.py);
- the [Preservation] status icon and name: the vanilla Unluck effect,
  re-iconed and renamed (paper/pack).

Run from the repository root: python3 tools/renoir_assets.py
"""
import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
PACK = ROOT / "paper/pack/assets"
SHARED = ROOT / "src/main/resources/assets/index_prescript"
NS = "index_prescript"


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent="\t") + "\n")


# --- the nail ----------------------------------------------------------------

def box(x0, y0, z0, x1, y1, z1):
    return {
        "from": [x0, y0, z0], "to": [x1, y1, z1], "shade": False, "light_emission": 15,
        "faces": {f: {"uv": [0, 0, 16, 16], "texture": "#0"} for f in ("north", "east", "south", "west", "up", "down")},
    }


def nail():
    """Point at the bottom (y 0), a square head at the top, along the model's Y axis."""
    def square(half, y0, y1):
        return box(8 - half, y0, 8 - half, 8 + half, y1, 8 + half)

    elements = [
        square(0.25, 0.0, 1.2),
        square(0.5, 1.2, 2.6),
        square(0.75, 2.6, 4.0),
        square(0.9, 4.0, 13.0),
        square(1.4, 13.0, 13.6),
        square(3.0, 13.6, 15.0),
        square(2.2, 15.0, 15.6),
    ]
    tex = f"{NS}:item/mang_glow"
    write(PACK / NS / "models/item/promised_nail.json", {"textures": {"0": tex, "particle": tex}, "elements": elements})
    write(PACK / NS / "items/promised_nail.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/promised_nail"}})

    # A half-see-through copy for Strike Again's rain of nails: the same shimmer at about 55% opacity.
    glow = PACK / NS / "textures/item/mang_glow.png"
    ghost = Image.open(glow).convert("RGBA")
    alpha = ghost.getchannel("A").point(lambda a: int(a * 0.55))
    ghost.putalpha(alpha)
    ghost.save(PACK / NS / "textures/item/promised_nail_ghost.png")
    (PACK / NS / "textures/item/promised_nail_ghost.png.mcmeta").write_text(
        (PACK / NS / "textures/item/mang_glow.png.mcmeta").read_text())
    ghost_tex = f"{NS}:item/promised_nail_ghost"
    write(PACK / NS / "models/item/promised_nail_ghost.json",
          {"textures": {"0": ghost_tex, "particle": ghost_tex}, "elements": elements})
    write(PACK / NS / "items/promised_nail_ghost.json",
          {"model": {"type": "minecraft:model", "model": f"{NS}:item/promised_nail_ghost"}})


# --- the Black God's Voice icon ---------------------------------------------

VOICE = [
    "..L...........L.",
    ".LY.L.......L.YL",
    "..YYL.......LYY.",
    "...YY.LLLL.YY...",
    "....YYKKKKYY....",
    "....YKKKKKKY....",
    "....YKYYYYKY....",
    "....YKKKKKKY....",
    "....YKKYYKKY....",
    ".....YKYYKY.....",
    ".....YKKKKY.....",
    "......YKKY......",
    "......YKKY......",
    ".......YY.......",
    "................",
    "................",
]
COLOURS = {"Y": (214, 188, 70, 255), "L": (246, 230, 150, 255), "K": (20, 20, 24, 255)}


def voice_icon():
    img = Image.new("RGBA", (16, 16))
    for y, row in enumerate(VOICE):
        for x, ch in enumerate(row):
            if ch in COLOURS:
                img.putpixel((x, y + 1), COLOURS[ch])
    target = SHARED / "textures/item/black_god_voice.png"
    target.parent.mkdir(parents=True, exist_ok=True)
    img.save(target)
    write(SHARED / "models/item/black_god_voice.json",
          {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/black_god_voice"}})
    write(SHARED / "items/black_god_voice.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/black_god_voice"}})


# --- the Preservation status icon -------------------------------------------

def preservation_icon():
    """A gold disc behind a dark cross, with gold spikes on the diagonals."""
    size, ss = 72, 4
    big = size * ss
    img = Image.new("RGBA", (big, big))
    d = ImageDraw.Draw(img)
    c = big / 2
    for angle in (45, 135, 225, 315):
        import math
        a = math.radians(angle)
        tip = (c + math.cos(a) * big * 0.48, c + math.sin(a) * big * 0.48)
        left = (c + math.cos(a + 0.35) * big * 0.22, c + math.sin(a + 0.35) * big * 0.22)
        right = (c + math.cos(a - 0.35) * big * 0.22, c + math.sin(a - 0.35) * big * 0.22)
        d.polygon([tip, left, right], fill=(204, 176, 96, 255), outline=(90, 74, 40, 255))
    r = big * 0.3
    d.ellipse((c - r, c - r, c + r, c + r), fill=(180, 152, 82, 255), outline=(250, 232, 160, 255), width=ss * 3)
    arm, width = big * 0.27, big * 0.11
    d.rectangle((c - width, c - arm, c + width, c + arm), fill=(22, 22, 26, 255))
    d.rectangle((c - arm, c - width, c + arm, c + width), fill=(22, 22, 26, 255))
    d.rectangle((c - width * 0.35, c - arm * 0.8, c + width * 0.35, c + arm * 0.8), fill=(70, 62, 44, 255))
    glow = img.filter(ImageFilter.GaussianBlur(ss * 2))
    icon = Image.alpha_composite(glow, img).resize((size, size), Image.LANCZOS)
    target = PACK / "minecraft/textures/mob_effect/unluck.png"
    target.parent.mkdir(parents=True, exist_ok=True)
    icon.save(target)
    for lang, name in (("ko_kr", "보존"), ("en_us", "Preservation")):
        path = PACK / "minecraft/lang" / f"{lang}.json"
        data = json.loads(path.read_text())
        data["effect.minecraft.unluck"] = name
        path.write_text(json.dumps(data, ensure_ascii=False, indent="\t") + "\n")


if __name__ == "__main__":
    nail()
    voice_icon()
    preservation_icon()
    print("done")
