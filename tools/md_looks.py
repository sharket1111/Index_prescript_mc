"""Mirror Dungeon enemy looks: outfits for the player rig, drawn here (our own
pixel art, not the original's), one per faction or kind of enemy, after the
colours and shapes each faction is known by (Kurokumo's black suits, the
Pirates' coats and bandanas, N Corp.'s hooded robes, the Sweepers' black
suits and white eyes...). Abnormalities get creature bodies in their sin's
colour, larger and with longer arms.

Writes:
* paper/pack/assets/index_prescript/textures/item/rig/md_<look>.png (then run tools/rig.py)
* paper/pack/assets/index_prescript/textures/item/md_wing.png, md_tail.png and their item models
* paper/src/main/resources/md/looks.json: each look's size, bone proportions and weapon; each enemy's look

Run from the repository root: python3 tools/md_looks.py && python3 tools/rig.py
"""
import hashlib
import json
import os
import re

from PIL import Image, ImageDraw

NS = "paper/pack/assets/index_prescript"
RIG = os.path.join(NS, "textures/item/rig")
ENEMIES = "paper/src/main/resources/md/enemies.json"
OUT = "paper/src/main/resources/md/looks.json"

# Box regions in the skin layout: (u, v, w, h, d), inner and outer layer.
BOXES = {
    "head": ((0, 0, 8, 8, 8), (32, 0, 8, 8, 8)),
    "body": ((16, 16, 8, 12, 4), (16, 32, 8, 12, 4)),
    "right_arm": ((40, 16, 4, 12, 4), (40, 32, 4, 12, 4)),
    "left_arm": ((32, 48, 4, 12, 4), (48, 48, 4, 12, 4)),
    "right_leg": ((0, 16, 4, 12, 4), (0, 32, 4, 12, 4)),
    "left_leg": ((16, 48, 4, 12, 4), (0, 48, 4, 12, 4)),
}

SIN = {"wrath": (196, 48, 40), "lust": (226, 120, 40), "sloth": (214, 178, 52), "gluttony": (120, 170, 60), "gloom": (60, 120, 190),
       "pride": (40, 64, 150), "envy": (130, 60, 160)}


def rgb(c):
    return tuple(c) + (255,) if len(c) == 3 else tuple(c)


class Skin:
    def __init__(self):
        self.img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        self.g = ImageDraw.Draw(self.img)

    def faces(self, box):
        u, v, w, h, d = box
        return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
                "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}

    def rect(self, x, y, w, h, c):
        if w > 0 and h > 0:
            self.g.rectangle([x, y, x + w - 1, y + h - 1], fill=rgb(c))

    def band(self, part, y0, y1, c, outer=False, sides=("front", "back", "left", "right")):
        """Rows y0..y1-1 (from the top of the part) all around, in colour c; the top/bottom faces too at the ends."""
        box = BOXES[part][1 if outer else 0]
        f = self.faces(box)
        h = box[3]
        for s in sides:
            x, y, w, hh = f[s]
            self.rect(x, y + y0, w, min(y1, h) - y0, c)
        if y0 == 0 and "top" not in sides:
            x, y, w, d = f["top"]
            self.rect(x, y, w, d, c)
        if y1 >= h:
            x, y, w, d = f["bottom"]
            self.rect(x, y, w, d, c)

    def fill(self, part, c, outer=False):
        self.band(part, 0, 99, c, outer)

    def front(self, part, x, y, w, h, c, outer=False, side="front"):
        fx, fy, fw, fh = self.faces(BOXES[part][1 if outer else 0])[side]
        self.rect(fx + x, fy + y, w, h, c)

    def px(self, part, x, y, c, outer=False, side="front"):
        self.front(part, x, y, 1, 1, c, outer, side)

    def save(self, path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        self.img.save(path)


def shade(c, f):
    return tuple(max(0, min(255, int(x * f))) for x in c[:3])


def draw(spec):
    """One outfit from a spec: skin, hair, clothes, extras."""
    s = Skin()
    skin = spec.get("skin", (206, 160, 124))
    top, pants, shoes = spec["top"], spec.get("pants", shade(spec["top"], 0.8)), spec.get("shoes", (24, 22, 26))
    hair = spec.get("hair", (40, 32, 28))
    # Head: skin, hair on top and around, a face.
    s.fill("head", skin)
    style = spec.get("hair_style", "short")
    if style != "bald":
        s.band("head", 0, 2 if style == "short" else 1, hair)
        s.band("head", 0, 6 if style == "long" else 3, hair, sides=("back", "left", "right"))
        x, y, w, d = s.faces(BOXES["head"][0])["top"]
        s.rect(x, y, w, d, hair)
    eye = spec.get("eyes", (40, 46, 70))
    white = spec.get("eye_white", (240, 240, 240))
    for x in (1, 5):
        s.px("head", x, 4, white)
        s.px("head", x + 1, 4, eye)
    if spec.get("glow_eyes"):
        for x in (1, 2, 5, 6):
            s.px("head", x, 4, spec["glow_eyes"])
    s.front("head", 3, 6, 2, 1, shade(skin, 0.75))
    # Clothes: body, sleeves, trousers, shoes.
    s.fill("body", top)
    for arm in ("right_arm", "left_arm"):
        s.fill(arm, spec.get("sleeve", top))
        s.band(arm, 10, 12, spec.get("hands", skin))
    for leg in ("right_leg", "left_leg"):
        s.fill(leg, pants)
        s.band(leg, 9, 12, shoes)
    accent = spec.get("accent")
    inner = spec.get("inner")
    if inner:  # a shirt down the front between the lapels
        s.front("body", 2, 0, 4, 5, inner)
    if spec.get("tie"):
        s.front("body", 3, 0, 2, 8, spec["tie"])
    if accent:  # trim: belt and cuffs
        s.front("body", 0, 8, 8, 1, accent)
        s.band("body", 8, 9, accent, sides=("back", "left", "right"))
        for arm in ("right_arm", "left_arm"):
            s.band(arm, 9, 10, accent)
    if spec.get("lapels"):
        s.front("body", 1, 0, 1, 6, shade(top, 0.7))
        s.front("body", 6, 0, 1, 6, shade(top, 0.7))
    if spec.get("coat"):  # a long coat over the legs (outer layer)
        for leg in ("right_leg", "left_leg"):
            s.band(leg, 0, 7, spec["coat"], outer=True)
        s.fill("body", spec["coat"], outer=True)
        s.front("body", 2, 0, 4, 12, (0, 0, 0, 0), outer=True)
    if spec.get("hood"):
        s.fill("head", spec["hood"], outer=True)
        s.front("head", 1, 2, 6, 6, (0, 0, 0, 0), outer=True)
    if spec.get("hat"):
        s.band("head", 0, 2, spec["hat"], outer=True)
    if spec.get("bandana"):
        s.band("head", 1, 3, spec["bandana"], outer=True)
    if spec.get("mask"):  # covers the lower face
        s.front("head", 0, 5, 8, 3, spec["mask"], outer=True)
    if spec.get("visor"):  # covers the eyes
        s.front("head", 0, 3, 8, 2, spec["visor"], outer=True)
    if spec.get("full_mask"):
        s.front("head", 0, 0, 8, 8, spec["full_mask"], outer=True)
        for x in (1, 5):
            s.front("head", x, 4, 2, 1, spec.get("glow_eyes", (20, 20, 20)), outer=True)
    if spec.get("helmet"):
        s.band("head", 0, 4, spec["helmet"], outer=True)
    if spec.get("splotches"):  # paint (the Ring)
        h = int(hashlib.md5(repr(spec).encode()).hexdigest(), 16)
        for i, c in enumerate(spec["splotches"]):
            x, y = (h >> (i * 5)) % 7, (h >> (i * 5 + 3)) % 10
            s.front("body", x, y, 2, 2, c)
    if spec.get("emblem"):
        s.front("body", 5, 2, 2, 2, spec["emblem"])
    if spec.get("carapace"):  # creatures and crabs: plates on the back and arms
        s.band("body", 0, 12, spec["carapace"], outer=True, sides=("back",))
        for arm in ("right_arm", "left_arm"):
            s.band(arm, 0, 6, spec["carapace"], outer=True)
    if spec.get("veins"):
        for y in (2, 5, 8):
            s.front("body", 1 + y % 3, y, 3, 1, spec["veins"])
    return s


# Each look: a drawing spec, the rig's scale and bone proportions, and a weapon (an item, or "" for none).
LOOKS = {
    "kurokumo": ({"top": (24, 22, 26), "inner": (230, 228, 220), "tie": (170, 36, 40), "lapels": 1, "hair": (20, 18, 22), "emblem": (200, 200, 210)}, "netherite_sword"),
    "pirate": ({"top": (34, 52, 92), "inner": (226, 220, 200), "coat": (40, 58, 100), "bandana": (190, 40, 36), "accent": (180, 150, 70), "pants": (70, 56, 40), "hair": (90, 60, 30)}, "iron_sword"),
    "k_corp": ({"top": (220, 224, 226), "accent": (70, 170, 90), "helmet": (200, 206, 210), "visor": (60, 200, 120), "pants": (180, 186, 190), "shoes": (60, 64, 70)}, "mace"),
    "k_drone": ({"top": (200, 206, 210), "full_mask": (190, 196, 200), "glow_eyes": (80, 230, 140), "accent": (70, 170, 90), "pants": (150, 156, 160), "skin": (170, 176, 180)}, ""),
    "n_corp": ({"top": (30, 28, 32), "hood": (36, 34, 38), "mask": (226, 222, 214), "accent": (180, 160, 90), "coat": (32, 30, 34), "pants": (30, 28, 32)}, "mace"),
    "t_corp": ({"top": (90, 92, 96), "accent": (230, 190, 40), "inner": (230, 190, 40), "hat": (70, 72, 76), "pants": (70, 72, 76)}, "iron_axe"),
    "sweeper": ({"top": (16, 16, 18), "hood": (14, 14, 16), "full_mask": (12, 12, 14), "glow_eyes": (240, 240, 250), "coat": (18, 18, 20), "pants": (16, 16, 18), "hands": (16, 16, 18)}, "trident"),
    "street": ({"top": (90, 70, 60), "pants": (50, 56, 70), "hair": (60, 40, 30), "accent": (130, 110, 90)}, "iron_shovel"),
    "rabbits": ({"top": (60, 58, 52), "inner": (200, 190, 170), "accent": (130, 40, 40), "hair": (120, 90, 50), "pants": (40, 40, 44)}, "iron_axe"),
    "casino": ({"top": (20, 20, 24), "inner": (230, 230, 230), "tie": (30, 30, 34), "visor": (16, 16, 18), "lapels": 1, "hair": (30, 26, 24)}, "iron_shovel"),
    "bloodfiend": ({"top": (90, 20, 30), "inner": (230, 220, 210), "skin": (232, 214, 210), "eyes": (200, 20, 30), "hair": (230, 230, 236), "coat": (60, 14, 22), "accent": (200, 170, 90), "lapels": 1}, "iron_sword"),
    "bloodbag": ({"top": (120, 60, 60), "skin": (220, 196, 190), "eyes": (180, 20, 30), "hair": (80, 40, 40), "pants": (80, 50, 50), "veins": (170, 30, 40)}, ""),
    "butler": ({"top": (22, 22, 26), "inner": (236, 236, 236), "tie": (236, 236, 236), "lapels": 1, "hands": (240, 240, 240), "hair": (70, 60, 50), "coat": (24, 24, 28)}, "iron_sword"),
    "ring": ({"top": (232, 228, 220), "splotches": [(220, 60, 60), (60, 120, 220), (240, 200, 40), (70, 180, 90)], "pants": (60, 60, 70), "hair": (200, 80, 120), "coat": (230, 226, 218)}, "iron_sword"),
    "automanikin": ({"top": (200, 190, 200), "skin": (226, 220, 230), "eyes": (200, 60, 120), "accent": (200, 60, 120), "hair_style": "bald", "pants": (180, 170, 180)}, "iron_sword"),
    "thumb": ({"top": (26, 24, 24), "inner": (180, 150, 70), "tie": (180, 150, 70), "lapels": 1, "hat": (24, 22, 22), "accent": (180, 150, 70)}, "crossbow"),
    "middle": ({"top": (30, 30, 34), "accent": (230, 200, 40), "inner": (230, 200, 40), "hair": (30, 26, 24), "lapels": 1}, "mace"),
    "index": ({"top": (236, 234, 228), "accent": (200, 170, 70), "coat": (236, 234, 228), "inner": (200, 170, 70), "hair": (200, 196, 190)}, "iron_sword"),
    "shi": ({"top": (150, 30, 34), "accent": (220, 180, 70), "pants": (30, 26, 28), "hair": (24, 20, 20), "hair_style": "long"}, "iron_sword"),
    "heishou": ({"top": (34, 30, 36), "accent": (150, 40, 50), "mask": (30, 28, 32), "hair": (20, 18, 22), "coat": (40, 34, 42)}, "iron_sword"),
    "lineage": ({"top": (60, 60, 66), "coat": (70, 70, 76), "hat": (40, 36, 30), "accent": (200, 200, 210), "hair": (20, 18, 20)}, "iron_sword"),
    "fixer": ({"top": (70, 74, 84), "inner": (210, 210, 210), "accent": (160, 140, 90), "hair": (70, 50, 40), "lapels": 1}, "iron_sword"),
    "rosespanner": ({"top": (90, 50, 60), "accent": (200, 70, 90), "visor": (60, 60, 70), "pants": (60, 50, 50)}, "mace"),
    "yurodivy": ({"top": (110, 96, 80), "hood": (100, 86, 70), "pants": (80, 70, 60), "accent": (60, 50, 40)}, "iron_shovel"),
    "gear": ({"top": (110, 80, 50), "accent": (190, 150, 60), "visor": (190, 150, 60), "pants": (80, 60, 40)}, "iron_pickaxe"),
    "le_noir": ({"top": (26, 26, 30), "inner": (226, 222, 214), "tie": (26, 26, 30), "lapels": 1, "hat": (20, 20, 24), "hair": (30, 28, 30)}, "mace"),
    "le_rouge": ({"top": (150, 30, 40), "inner": (226, 222, 214), "lapels": 1, "hat": (130, 24, 34), "hair": (60, 30, 30)}, "iron_sword"),
    "monk": ({"top": (70, 30, 30), "coat": (80, 34, 34), "hair_style": "bald", "skin": (190, 170, 160), "eyes": (200, 30, 30), "accent": (210, 180, 90)}, "mace"),
    "mermaid": ({"top": (150, 196, 210), "skin": (200, 226, 232), "eyes": (40, 90, 120), "hair": (230, 240, 244), "hair_style": "long", "pants": (110, 160, 180), "veins": (110, 170, 190)}, "trident"),
    "pequod": ({"top": (70, 76, 86), "coat": (60, 66, 76), "hat": (50, 56, 66), "accent": (170, 150, 110), "pants": (50, 50, 56)}, "trident"),
    "soldier": ({"top": (80, 90, 70), "helmet": (70, 80, 60), "accent": (60, 50, 40), "pants": (70, 80, 60)}, "iron_sword"),
    "crab": ({"top": (190, 60, 40), "carapace": (210, 80, 50), "skin": (200, 90, 60), "hair_style": "bald", "eyes": (10, 10, 10), "pants": (150, 50, 36)}, ""),
    "bot": ({"top": (130, 134, 140), "skin": (150, 154, 160), "full_mask": (120, 124, 130), "glow_eyes": (240, 70, 50), "accent": (220, 180, 40), "hair_style": "bald"}, "iron_axe"),
    "gnome": ({"top": (60, 110, 60), "hat": (190, 40, 40), "hair": (230, 230, 230), "pants": (90, 70, 50)}, "iron_hoe"),
    "jar": ({"top": (170, 110, 70), "skin": (180, 120, 80), "full_mask": (160, 104, 66), "glow_eyes": (30, 20, 16), "carapace": (190, 130, 90), "hair_style": "bald"}, ""),
    "kaki": ({"top": (220, 120, 40), "skin": (230, 150, 70), "hair_style": "bald", "eyes": (30, 20, 10), "carapace": (200, 100, 30)}, ""),
    "needle": ({"top": (190, 186, 200), "accent": (120, 100, 160), "mask": (230, 230, 236), "hair": (110, 90, 150)}, "trident"),
    "tingtang": ({"top": (230, 200, 60), "accent": (40, 40, 40), "pants": (40, 40, 40), "hair": (30, 30, 30)}, "iron_shovel"),
    "slave": ({"top": (120, 110, 96), "pants": (90, 80, 70), "skin": (180, 150, 120), "hair": (60, 50, 40), "shoes": (90, 80, 70)}, "iron_pickaxe"),
    "tieqiu": ({"top": (40, 90, 140), "accent": (230, 230, 230), "pants": (30, 40, 60), "hair": (24, 20, 20)}, "mace"),
    "podao": ({"top": (60, 40, 30), "accent": (170, 40, 30), "mask": (60, 40, 30), "hair": (20, 18, 18)}, "iron_sword"),
    "wild_hunt": ({"top": (30, 34, 44), "coat": (36, 40, 52), "hood": (30, 34, 44), "glow_eyes": (110, 200, 240), "accent": (110, 200, 240)}, "iron_sword"),
    "manager": ({"top": (40, 40, 50), "inner": (220, 220, 220), "tie": (70, 140, 80), "lapels": 1, "hair": (120, 120, 126)}, "iron_sword"),
    "lobotomy": ({"top": (26, 26, 30), "inner": (220, 220, 220), "tie": (30, 30, 34), "lapels": 1, "accent": (190, 40, 40), "hair": (40, 34, 30)}, "iron_sword"),
    "mariachi": ({"top": (160, 30, 40), "accent": (230, 190, 60), "hat": (30, 26, 26), "pants": (30, 26, 26), "hair": (30, 24, 20)}, "iron_shovel"),
    "chef": ({"top": (236, 236, 236), "hat": (246, 246, 246), "accent": (40, 40, 40), "pants": (40, 40, 44), "hair": (90, 60, 40)}, "iron_sword"),
    "named": ({"top": (60, 50, 70), "coat": (70, 60, 80), "accent": (200, 180, 120), "hair": (150, 100, 60)}, "iron_sword"),
}

# Abnormalities: one creature look per sin colour and per size.
for sin, col in SIN.items():
    LOOKS["abno_" + sin] = ({"top": shade(col, 0.55), "skin": shade(col, 0.8), "hair_style": "bald", "glow_eyes": (250, 240, 200),
                             "full_mask": shade(col, 0.7), "carapace": shade(col, 0.4), "veins": shade(col, 1.2), "pants": shade(col, 0.45),
                             "hands": shade(col, 0.35), "shoes": shade(col, 0.3)}, "")
    LOOKS["peccatulum_" + sin] = ({"top": shade(col, 0.5), "skin": shade(col, 0.9), "hair_style": "bald", "glow_eyes": (255, 255, 255),
                                   "full_mask": shade(col, 0.85), "veins": shade(col, 1.3), "pants": shade(col, 0.4)}, "")

# Name/faction patterns in order: the first that matches gives the look.
MATCH = [
    (r"kurokumo", "kurokumo"), (r"twinhook|pirate", "pirate"), (r"k corp.*drone|regen drone|arrest drone|filming drone", "k_drone"),
    (r"k corp", "k_corp"), (r"n corp|inquisitor", "n_corp"), (r"t corp", "t_corp"), (r"sweeper", "sweeper"), (r"dead rabbits", "rabbits"),
    (r"casino", "casino"), (r"bloodfiend|dulcinea|cassetti|fashionista|sancho|don quixote", "bloodfiend"), (r"bloodbag", "bloodbag"),
    (r"butler|edgar|wuthering|nelly", "butler"), (r"automanikin", "automanikin"), (r"of the ring|ring apprentice|albina", "ring"),
    (r"thumb|soldato|lucio", "thumb"), (r"middle|werner", "middle"), (r"index|proselyte", "index"), (r"shi family|shi huazhen|shi sijing|shi yihua", "shi"),
    (r"heishou", "heishou"), (r"blade lineage|salsu", "lineage"), (r"rosespanner|niko", "rosespanner"), (r"office fixer|fixer", "fixer"),
    (r"yurodiv", "yurodivy"), (r"gear", "gear"), (r"le noir|maison du noir|brand manager|gustave", "le_noir"), (r"le rouge|mimosa", "le_rouge"),
    (r"deathrite|monk|priest", "monk"), (r"mermaid", "mermaid"), (r"pequod|ahab|pallidified", "pequod"), (r"soldier|veteran", "soldier"),
    (r"crab", "crab"), (r"bot\b", "bot"), (r"gnome", "gnome"), (r"jar monster", "jar"), (r"kaki", "kaki"), (r"needle|needlework", "needle"),
    (r"tingtang", "tingtang"), (r"slave|miner", "slave"), (r"tieqiu", "tieqiu"), (r"podao", "podao"), (r"wild hunt|heathcliff|hindley|linton|josephine", "wild_hunt"),
    (r"g corp|manager", "manager"), (r"dawn of green", "bot"), (r"lobotomy", "lobotomy"), (r"mariachi", "mariachi"), (r"chef", "chef"),
    (r"\bjia\b|\bwei\b", "heishou"), (r"hooligan|stranger|leather wallet|putty|flannel", "street"),
]
PECC = {"irae": "wrath", "luxuriae": "lust", "pigritiae": "sloth", "gulae": "gluttony", "morositatis": "gloom", "superbiae": "pride", "invidiae": "envy"}

# Size (the rig's scale and its hitbox) and proportions per kind of body.
FORM = {
    "human": {"scale": 1.0, "bones": {}},
    "small": {"scale": 0.7, "bones": {"head": [1.2, 1.2, 1.2]}},
    "wide": {"scale": 0.95, "bones": {"body": [1.4, 0.9, 1.4], "right_arm": [1.4, 1.0, 1.4], "left_arm": [1.4, 1.0, 1.4], "head": [1.2, 0.9, 1.2]}},
    "brute": {"scale": 1.25, "bones": {"body": [1.25, 1.05, 1.25], "right_arm": [1.3, 1.15, 1.3], "left_arm": [1.3, 1.15, 1.3]}},
    "creature": {"scale": 1.6, "bones": {"right_arm": [1.1, 1.5, 1.1], "left_arm": [1.1, 1.5, 1.1], "head": [1.25, 1.25, 1.25], "body": [1.15, 1.0, 1.15]}},
    "titan": {"scale": 2.1, "bones": {"right_arm": [1.2, 1.6, 1.2], "left_arm": [1.2, 1.6, 1.2], "head": [1.3, 1.3, 1.3], "body": [1.25, 1.05, 1.25]}},
}
FORM_OF_LOOK = {"crab": "wide", "gnome": "small", "kaki": "small", "jar": "wide", "bot": "brute", "k_drone": "small"}
RISK_FORM = {"zayin": "creature", "teth": "creature", "he": "creature", "waw": "titan", "aleph": "titan"}


def look_of(name, data):
    low = name.lower()
    for k, sin in PECC.items():
        if k in low:
            return "peccatulum_" + sin, "brute" if "class 3" in low else "creature" if "class 2" in low else "human"
    risk = (data.get("risk") or "").lower()
    if (risk and risk not in ("human",) or re.search(r"refracted|failure", low)) and not re.search(r"e\.g\.o::", low):
        sins = [s["sin"] for s in data["skills"] if s.get("sin") in SIN]
        sin = max(set(sins), key=sins.count) if sins else "gloom"
        return "abno_" + sin, RISK_FORM.get(risk[:5].strip("-"), "creature")
    hay = low + " " + (data.get("faction") or "").lower()
    for pat, look in MATCH:
        if re.search(pat, hay):
            return look, FORM_OF_LOOK.get(look, "human")
    return "named", "human"


def weapon_of(look, data):
    w = LOOKS[look][1]
    if w or look.startswith(("abno_", "peccatulum_", "crab", "kaki", "jar", "k_drone", "bloodbag")):
        return w
    return "iron_sword"


def wing_texture(path):
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    g = ImageDraw.Draw(img)
    g.polygon([(2, 30), (30, 2), (30, 10), (22, 16), (26, 20), (14, 24), (16, 28)], fill=(255, 255, 255, 230))
    for i in range(4):
        g.line([(4 + i * 2, 29 - i), (28 - i * 4, 6 + i * 5)], fill=(200, 200, 200, 255))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def tail_texture(path):
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    g = ImageDraw.Draw(img)
    pts = [(4, 28), (10, 22), (16, 18), (22, 12), (26, 6), (28, 2)]
    for i in range(len(pts) - 1):
        g.line([pts[i], pts[i + 1]], fill=(255, 255, 255, 255), width=max(1, 5 - i))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def flat_model(texture):
    """A flat, two-sided 16x16 picture (a wing or tail), tinted by the display's dye (layer 0)."""
    return {"textures": {"layer0": texture, "particle": texture},
            "elements": [{"from": [0, 0, 7.9], "to": [16, 16, 8.1], "faces": {
                "north": {"uv": [16, 0, 0, 16], "texture": "#layer0", "tintindex": 0},
                "south": {"uv": [0, 0, 16, 16], "texture": "#layer0", "tintindex": 0}}}],
            "display": {}}


def main():
    enemies = json.load(open(ENEMIES, encoding="utf-8"))["enemies"]
    used = {}
    out_enemies = {}
    for name, data in enemies.items():
        look, form = look_of(name, data)
        used.setdefault(look, set()).add(form)
        out_enemies[name] = {"look": look, "form": form, "weapon": weapon_of(look, data)}
    for look in sorted(used):
        draw(LOOKS[look][0]).save(os.path.join(RIG, "md_" + look + ".png"))
    for stale in os.listdir(RIG):
        if stale.startswith("md_") and stale[3:-4] not in used:
            os.remove(os.path.join(RIG, stale))
    wing_texture(os.path.join(NS, "textures/item/md_wing.png"))
    tail_texture(os.path.join(NS, "textures/item/md_tail.png"))
    for part in ("wing", "tail"):
        p = os.path.join(NS, "models/item/md_" + part + ".json")
        with open(p, "w") as f:
            json.dump(flat_model("index_prescript:item/md_" + part), f, indent=1)
        with open(os.path.join(NS, "items/md_" + part + ".json"), "w") as f:
            json.dump({"model": {"type": "minecraft:model", "model": "index_prescript:item/md_" + part,
                                 "tints": [{"type": "minecraft:dye", "default": 16777215}]}}, f, indent=1)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump({"about": "Enemy looks (tools/md_looks.py): outfit md_<look> on the player rig, a form (scale and bone proportions), a weapon item.",
                   "forms": FORM, "enemies": out_enemies}, f, ensure_ascii=False, indent=1)
    print(len(used), "looks for", len(out_enemies), "enemies:", ", ".join(sorted(used)))


if __name__ == "__main__":
    main()
