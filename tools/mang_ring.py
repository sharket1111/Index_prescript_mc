#!/usr/bin/env python3
"""Generates the 3D model of [Mang]: a glowing golden ring, built from 16
segments around the model's Z axis (Java models only rotate elements by
multiples of 22.5 degrees up to 45, so each segment is laid along X or Y and
turned by at most 45). Run from the repository root: python3 tools/mang_ring.py
"""
import json
import math
from pathlib import Path

from PIL import Image

PACK = Path(__file__).resolve().parent.parent / "paper/pack/assets/index_prescript"
SEGMENTS = 16
RADIUS = 6.0
THICK = 2.4
CENTER = 8.0


def segment(k):
    angle = k * 360 / SEGMENTS
    rad = math.radians(angle)
    cx, cy = CENTER + RADIUS * math.cos(rad), CENTER + RADIUS * math.sin(rad)
    length = 2 * RADIUS * math.tan(math.pi / SEGMENTS) * 1.12
    tangent = (angle + 90) % 180  # direction of the segment, 0-180
    # Lay the box along X (tangent near 0/180) or Y (near 90) and turn it by the remainder.
    if tangent <= 45 or tangent >= 135:
        size = (length, THICK)
        turn = tangent if tangent <= 45 else tangent - 180
    else:
        size = (THICK, length)
        turn = tangent - 90
    half = (size[0] / 2, size[1] / 2)
    face = {"uv": [0, 0, 16, 16], "texture": "#0"}
    return {
        "from": [round(cx - half[0], 4), round(cy - half[1], 4), round(CENTER - THICK / 2, 4)],
        "to": [round(cx + half[0], 4), round(cy + half[1], 4), round(CENTER + THICK / 2, 4)],
        "rotation": {"angle": turn, "axis": "z", "origin": [round(cx, 4), round(cy, 4), CENTER]},
        "faces": {name: dict(face) for name in ("north", "east", "south", "west", "up", "down")},
    }


def texture():
    image = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            edge = min(x, y, 15 - x, 15 - y)
            t = min(1.0, edge / 6)
            image.putpixel((x, y), (255, int(200 + 50 * t), int(60 + 150 * t), 255))
    return image


def main():
    (PACK / "models/item").mkdir(parents=True, exist_ok=True)
    (PACK / "items").mkdir(parents=True, exist_ok=True)
    (PACK / "textures/item").mkdir(parents=True, exist_ok=True)
    texture().save(PACK / "textures/item/mang_ring.png")
    model = {
        "textures": {"0": "index_prescript:item/mang_ring", "particle": "index_prescript:item/mang_ring"},
        "elements": [segment(k) for k in range(SEGMENTS)],
        "display": {"fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}},
    }
    (PACK / "models/item/mang_ring.json").write_text(json.dumps(model, indent="\t") + "\n")
    (PACK / "items/mang_ring.json").write_text(json.dumps(
        {"model": {"type": "minecraft:model", "model": "index_prescript:item/mang_ring"}}, indent="\t") + "\n")


if __name__ == "__main__":
    main()
