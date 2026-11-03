#!/usr/bin/env python3
"""Draws the status icon for [Mang] (shown in place of Strength): a glowing
golden ring, tilted as if wound round a blade. Writes the Java mob effect
texture and the Bedrock one. Run from the repository root: python3 tools/mang_icon.py
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
TARGETS = [ROOT / "paper/pack/assets/minecraft/textures/mob_effect/strength.png",
           ROOT / "paper/bedrock/textures/ui/strength_effect.png"]
SIZE = 72
SS = 4


def ring(draw, box, width, colour):
    draw.ellipse(box, outline=colour, width=width)


def main():
    big = SIZE * SS
    layer = Image.new("RGBA", (big, big))
    draw = ImageDraw.Draw(layer)
    # A slim blade through the ring, from lower left to upper right, faint behind the gold.
    draw.line([(big * 0.2, big * 0.82), (big * 0.8, big * 0.18)], fill=(200, 205, 220, 150), width=SS * 3)
    # The ring: an ellipse (a circle seen at a slant), squashed across the blade.
    flat = Image.new("RGBA", (big, big))
    fd = ImageDraw.Draw(flat)
    w, h = big * 0.40, big * 0.20
    box = (big / 2 - w, big / 2 - h, big / 2 + w, big / 2 + h)
    ring(fd, box, SS * 6, (255, 196, 40, 255))
    ring(fd, (box[0] + SS * 2, box[1] + SS * 2, box[2] - SS * 2, box[3] - SS * 2), SS * 2, (255, 248, 200, 255))
    flat = flat.rotate(-45, resample=Image.BICUBIC)
    glow = flat.filter(ImageFilter.GaussianBlur(SS * 4))
    glow_alpha = glow.getchannel("A").point(lambda a: min(255, int(a * 1.6)))
    glow = Image.merge("RGBA", (*Image.new("RGB", (big, big), (255, 210, 80)).split(), glow_alpha))
    image = Image.alpha_composite(glow, layer)
    image = Image.alpha_composite(image, flat)
    # A spark where the ring crosses in front of the blade.
    sd = ImageDraw.Draw(image)
    cx, cy, r = big * 0.36, big * 0.63, SS * 4
    sd.polygon([(cx, cy - r * 2), (cx + r * 0.5, cy), (cx, cy + r * 2), (cx - r * 0.5, cy)], fill=(255, 255, 235, 255))
    sd.polygon([(cx - r * 2, cy), (cx, cy - r * 0.5), (cx + r * 2, cy), (cx, cy + r * 0.5)], fill=(255, 255, 235, 255))
    icon = image.resize((SIZE, SIZE), Image.LANCZOS)
    for target in TARGETS:
        target.parent.mkdir(parents=True, exist_ok=True)
        icon.save(target)
    print("wrote", *TARGETS)


if __name__ == "__main__":
    main()
