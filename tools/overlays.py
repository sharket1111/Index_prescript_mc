#!/usr/bin/env python3
"""Draws the full-screen skill overlays (camera overlays) after Limbus Company's
skill screens: slanted black bands at the top and bottom for small skills, big
black wedges in the bottom-left and top-right corners for big skills, each
edged with a jagged, glowing yellow line. The texture is stretched over the
whole screen. Run from the repository root: python3 tools/overlays.py
"""
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

W, H = 1280, 720
OUT = Path(__file__).resolve().parent.parent / "paper/pack/assets/index_prescript/textures/misc"
BLACK = (8, 6, 10, 238)


def jagged(a, b, steps, jitter, rng):
    """Points from a to b with random sideways jitter, like a crackling line."""
    (x0, y0), (x1, y1) = a, b
    dx, dy = x1 - x0, y1 - y0
    length = (dx * dx + dy * dy) ** 0.5
    nx, ny = -dy / length, dx / length
    points = []
    for i in range(steps + 1):
        t = i / steps
        offset = 0 if i in (0, steps) else rng.uniform(-jitter, jitter)
        points.append((x0 + dx * t + nx * offset, y0 + dy * t + ny * offset))
    return points


def edge(image, a, b, rng):
    """A glowing yellow crackle along a band's inner edge, with a few sparks."""
    glow = Image.new("RGBA", image.size, (0, 0, 0, 0))
    g = ImageDraw.Draw(glow)
    main = jagged(a, b, 70, 5, rng)
    g.line(main, fill=(255, 190, 40, 200), width=10)
    for _ in range(26):
        i = rng.randrange(1, len(main) - 1)
        x, y = main[i]
        branch = jagged((x, y), (x + rng.uniform(-60, 60), y + rng.uniform(-40, 40)), 6, 4, rng)
        g.line(branch, fill=(255, 200, 60, 160), width=3)
    glow = glow.filter(ImageFilter.GaussianBlur(7))
    image.alpha_composite(glow)
    core = ImageDraw.Draw(image)
    core.line(main, fill=(255, 236, 150, 255), width=3)
    second = jagged((a[0], a[1] + 6), (b[0], b[1] + 6), 50, 4, rng)
    core.line(second, fill=(255, 205, 70, 200), width=2)


def band(points, inner_a, inner_b, seed):
    rng = random.Random(seed)
    image = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(image).polygon(points, fill=BLACK)
    edge(image, inner_a, inner_b, rng)
    return image


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    # Small skills: a band along the top (thicker to the right) and one along the bottom (thicker to the left).
    small = band([(0, 0), (W, 0), (W, 52), (0, 10)], (0, 10), (W, 52), 1)
    small.alpha_composite(band([(0, H), (W, H), (W, H - 10), (0, H - 52)], (0, H - 52), (W, H - 10), 2))
    small.save(OUT / "skill_small.png")
    # Big skills: a wedge in the top-right corner and a larger one in the bottom-left.
    big = band([(int(W * 0.64), 0), (W, 0), (W, int(H * 0.26))], (int(W * 0.64), 0), (W, int(H * 0.26)), 3)
    big.alpha_composite(band([(0, int(H * 0.68)), (0, H), (int(W * 0.44), H)], (0, int(H * 0.68)), (int(W * 0.44), H), 4))
    big.save(OUT / "skill_big.png")


if __name__ == "__main__":
    main()
