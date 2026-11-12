"""Corrections to the enemies' models after looking at reference pictures (see
md_cast.py): faction outfits (LOOK_FIX), people one by one (PEOPLE), creatures
one by one (CREATURES), and kinds by name (rule). Simple shapes; colours and
the features each enemy is known by."""
import re

import md_creatures as Cr
import md_models as M

sh = M.shade
GOLD, WHITE, BLACK, BONE = (214, 180, 80), (240, 240, 240), (22, 22, 26), (226, 220, 200)

# Faction outfits, corrected (merged over md_looks.LOOKS).
LOOK_FIX = {
    "kurokumo": {"hair_style": "tail", "tie": None, "inner": (44, 44, 48), "emblem": None, "weapon": "katana"},
    "gear": {"bulk": 1.5, "helmet": (206, 206, 212), "visor": (150, 40, 40), "top": (190, 190, 198), "pants": (60, 60, 70), "pads": (150, 40, 40), "accent": (40, 60, 140), "weapon": "greatsword"},
    "yurodivy": {"hood": None, "coat": (112, 78, 52), "top": (232, 228, 216), "pants": (236, 234, 228), "hair": (50, 44, 40), "weapon": "gun"},
    "pirate": {"coat": (120, 64, 44), "top": (150, 40, 40), "bandana": (200, 170, 90), "hair_style": "long", "weapon": "hook"},
    "fixer": {"hat": (24, 24, 28), "top": (30, 30, 36), "pants": (26, 26, 30), "inner": (60, 60, 66), "weapon": "axe"},
    "tingtang": {"top": (150, 50, 50), "splotches": [(200, 110, 90), (110, 30, 30), (200, 110, 90)], "pants": (30, 30, 34), "hair": (44, 38, 36), "hair_style": "tail", "accent": None, "weapon": "sword"},
    "pequod": {"skin": (236, 236, 236), "top": (236, 236, 236), "coat": None, "hat": None, "hair_style": "bald", "splotches": [BLACK, BLACK, BLACK], "pants": (220, 220, 220), "visor": (20, 20, 22), "accent": None, "weapon": "spear"},
    "bloodfiend": {"full_mask": (240, 238, 232), "glow_eyes": (20, 16, 20), "hat": (90, 40, 140), "crown_h": 5, "brim": 1.6, "band": (30, 20, 40), "top": (26, 24, 32), "coat": (26, 24, 32), "tie": (120, 60, 170), "inner": WHITE, "accent": None, "weapon": "rapier", "weapon_tint": (110, 50, 160)},
    "bloodbag": {"full_mask": (190, 30, 36), "glow_eyes": (90, 10, 14), "top": (38, 36, 48), "tie": (170, 30, 36), "inner": (60, 56, 70), "pants": (30, 28, 38), "hands": (170, 30, 36), "veins": None, "hair_style": "bald"},
    "k_corp": {"top": (28, 30, 32), "helmet": (28, 30, 32), "visor": (160, 222, 60), "accent": (160, 222, 60), "pants": (28, 30, 32), "shoes": (20, 20, 22), "pads": (40, 44, 46), "weapon": "baton", "weapon_tint": (160, 222, 60)},
    "casino": {"hat": (26, 24, 28), "coat": (92, 26, 34), "top": (40, 20, 24), "skin": (240, 240, 240), "visor": None, "tie": None, "weapon": "baton"},
    "n_corp": {"hood": None, "mask": None, "coat": None, "helmet": (222, 222, 226), "full_mask": (214, 214, 220), "glow_eyes": (30, 30, 34), "top": (226, 226, 230), "pants": (196, 196, 204), "pads": (206, 206, 212), "accent": (150, 30, 34), "shoes": (120, 120, 128), "weapon": "hammer"},
    "rosespanner": {"top": (92, 30, 50), "pants": (80, 26, 44), "visor": None, "hair": (26, 24, 26), "accent": (160, 60, 80), "weapon": "hammer"},
    "soldier": {"skin": (172, 162, 162), "hair_style": "bald", "helmet": None, "top": (60, 80, 112), "pants": (40, 44, 56), "accent": (30, 30, 36), "weapon": "katana", "weapon_tint": (170, 30, 36)},
    "ring": {"top": (26, 24, 30), "coat": None, "skirt": (26, 24, 30), "hair": (26, 24, 30), "splotches": None, "accent": (130, 70, 180), "weapon": "spear"},
    "monk": {"hood": (92, 52, 30), "coat": (152, 72, 30), "coat_len": 11, "top": (120, 56, 26), "full_mask": (40, 26, 20), "glow_eyes": (20, 12, 10), "horns": (60, 40, 30), "weapon": None},
    "street": {"hood": (42, 62, 52), "top": (42, 62, 52), "mask": (150, 40, 40), "pants": (26, 26, 30), "accent": None, "weapon": "club", "weapon_tint": (150, 40, 40)},
    "butler": {"coat": None, "skirt": (26, 26, 30), "coat_len": 10, "top": (26, 26, 30), "inner": WHITE, "tie": None, "lapels": None, "hair": (60, 54, 56), "hair_style": "long", "bandana": WHITE, "weapon": "sword"},
    "rabbits": {"full_mask": (192, 84, 44), "glow_eyes": (30, 16, 10), "coat": (92, 62, 42), "coat_len": 9, "top": (60, 44, 34), "inner": None, "accent": None, "weapon": "mace"},
    "le_noir": {"crown_h": 5.5, "brim": 1.8, "full_mask": (44, 40, 44), "glow_eyes": (200, 200, 190), "coat": (28, 28, 32), "coat_len": 11, "inner": None, "tie": None, "weapon": "rapier"},
    "le_rouge": {"hat": None, "hair": (172, 26, 36), "hair_style": "long", "full_mask": (190, 30, 40), "glow_eyes": (60, 10, 14), "top": (120, 16, 26), "skirt": (120, 16, 26), "coat_len": 12, "inner": None, "lapels": None, "claws": BONE, "weapon": "scythe"},
    "tieqiu": {"top": (206, 160, 124), "sleeve": (206, 160, 124), "pants": (222, 216, 202), "accent": (222, 216, 202), "hair_style": "tail", "bulk": 0.8, "weapon": "axe"},
    "podao": {"hat": (172, 152, 102), "brim": 3.6, "crown_h": 2, "coat": (62, 62, 46), "coat_len": 10, "top": (50, 50, 40), "mask": None, "accent": None, "weapon": "greatsword"},
    "middle": {"top": (62, 32, 84), "inner": WHITE, "pants": (236, 236, 236), "hair": (204, 204, 210), "accent": None, "chains": GOLD, "splotches": [(150, 90, 170), (150, 90, 170)], "weapon": "mace"},
    "sweeper": {"hood": None, "coat": None, "bulk": 1.4, "hunch": 8, "top": (42, 42, 52), "helmet": (42, 42, 52), "full_mask": (30, 30, 36), "glow_eyes": (255, 60, 50), "accent": (60, 80, 160), "pants": (34, 34, 44), "hands": (30, 30, 36), "weapon": "scythe", "weapon_tint": (190, 30, 36)},
    "shi": {"weapon": "sword"},
}


def P(base, scale=1.0, tall=None, **over):
    return (base, over, scale, tall)


# People one by one: (base outfit, changes, scale, (legs, torso, thin) or None).
PEOPLE = {
    "Aida": P("named", 1.05, top=(236, 226, 196), coat=None, pants=(60, 110, 70), hair=(120, 80, 50), hat=(150, 100, 50), brim=3.6, band=(220, 130, 40), accent=None, weapon="lantern"),
    "Annette the Alterationist": P("named", 1.2, (1.15, 1.05, 0.9), skin=(236, 236, 240), hair=(230, 230, 232), hair_style="tail", top=(190, 190, 198), coat=None, skirt=(160, 160, 172), coat_len=11, accent=None, weapon="rapier"),
    "Become Strong? Genuinely?": P("named", 1.0, top=(60, 110, 60), coat=None, accent=(190, 40, 40), pants=(40, 40, 44), hair=(40, 36, 36), weapon="spear", weapon_tint=(190, 60, 40)),
    "Casino Security Chief": P("casino", 1.2, hat=(92, 92, 98), coat=(112, 112, 116), coat_len=10, top=(52, 52, 58), weapon="baton"),
    "Cassetti": P("named", 1.2, (1.15, 1.05, 0.92), skin=(230, 200, 200), hair=(150, 20, 30), hair_style="spiky", top=(32, 20, 24), coat=(122, 16, 26), coat_len=10, accent=None, horns=(150, 20, 30), weapon="spear", weapon_tint=(170, 24, 34)),
    "Ch'unokkun - Bind": P("named", 1.3, hood=(22, 20, 24), coat=(26, 24, 28), coat_len=11, top=(26, 24, 28), full_mask=(230, 90, 160), glow_eyes=(90, 230, 240), accent=None, weapon="sword", cape=(18, 16, 20)),
    "Ch'unokkun - Incapacitate": P("named", 1.3, hood=(22, 20, 24), coat=(26, 24, 28), coat_len=11, top=(26, 24, 28), full_mask=(90, 200, 230), glow_eyes=(240, 90, 170), accent=None, weapon="mace", cape=(18, 16, 20)),
    "Clippity-cloppity? Tap Away!": P("named", 1.25, top=(232, 60, 150), coat=None, sleeve=(240, 200, 60), pants=(232, 60, 150), splotches=[(240, 200, 60), (60, 200, 210), BLACK], full_mask=(110, 214, 214), glow_eyes=(240, 60, 150), accent=None, hunch=10, weapon=None),
    "You Want To Get Beat? Hurtily?": P("named", 1.4, bulk=2.0, skin=(192, 42, 42), top=(240, 190, 40), coat=None, sleeve=(192, 42, 42), pants=(30, 30, 60), full_mask=(60, 180, 170), glow_eyes=(240, 240, 240), accent=None, veins=(90, 16, 16), weapon=None),
    "Grown Powerful! For Realsies?": P("named", 1.35, top=(202, 52, 52), coat=None, pants=(40, 110, 110), accent=(40, 150, 150), full_mask=(60, 180, 170), glow_eyes=(255, 220, 120), weapon="lantern", bulk=0.6),
    "Dawn of Green - Doubt - α": P("named", 1.25, (1.35, 1.1, 0.8), slim=True, top=(52, 36, 28), coat=(52, 36, 28), coat_len=10, pants=(30, 26, 26), hair_style="bald", skin=(40, 36, 36), full_mask=(30, 26, 26), glow_eyes=(255, 60, 50), accent=None, weapon="spear", weapon_tint=(150, 30, 30)),
    "Dead Rabbits Boss": P("rabbits", 1.25, bulk=0.8, full_mask=(204, 174, 154), coat=(102, 72, 52), weapon="mace"),
    "Distorted Hohenheim": P("named", 1.4, (1.2, 1.05, 0.9), top=(40, 30, 40), coat=None, skirt=(150, 24, 40), coat_len=12, skirt_wide=3, frill=(200, 40, 60), full_mask=(190, 30, 50), glow_eyes=(90, 10, 20), hair_style="bald", accent=None, crown=(170, 26, 44), weapon=None),
    "District 20 Yurodiviye Captain": P("yurodivy", 1.2, hat=(62, 42, 32), crown_h=4.5, brim=1.8, band=GOLD, coat=(112, 78, 52), beard=(90, 70, 56), visor=(200, 170, 80), weapon="rapier"),
    "Dongbaek E.G.O::Spicebush": P("named", 1.3, (1.15, 1.05, 0.92), top=(236, 236, 230), coat=None, skirt=(236, 236, 230), coat_len=12, hair=(26, 24, 26), hair_style="long", accent=None, horns=(240, 200, 50), splotches=[(240, 200, 50), (240, 200, 50), (240, 200, 50)], weapon=None),
    "Ebony Queen's Apple": P("named", 1.6, (1.25, 1.1, 0.92), top=(26, 22, 30), coat=None, skirt=(112, 42, 162), coat_len=12, skirt_wide=4, frill=(160, 90, 220), skin=(236, 232, 226), full_mask=(236, 232, 226), glow_eyes=(20, 16, 20), hair_style="bald", horns=BLACK, cape=(20, 18, 24), cape_wide=2, accent=None, claws=BLACK, weapon=None),
    "Effloresced E.G.O::Farmwatch": P("named", 1.3, top=(232, 222, 192), coat=(152, 112, 62), coat_len=10, full_mask=(192, 122, 52), glow_eyes=(30, 16, 10), horns=(192, 122, 52), hair_style="bald", accent=None, weapon="scythe"),
    "Everything There of an Inquisitor": P("n_corp", 1.6, bulk=1.6, hunch=8, top=(152, 62, 72), skin=(170, 80, 86), helmet=None, full_mask=(170, 80, 86), glow_eyes=(120, 230, 120), pads=(206, 206, 212), big_arm=(150, 60, 70), claws=BONE, horns=(60, 20, 26), weapon="hammer"),
    "Proceeding Inquisitor": P("n_corp", 1.45, bulk=1.2, hunch=6, top=(160, 70, 80), skin=(170, 80, 86), helmet=None, full_mask=(170, 80, 86), glow_eyes=(120, 230, 120), big_arm=(150, 60, 70), claws=BONE, horns=(60, 20, 26), weapon="hammer"),
    "Slithering Inquisitor": P("n_corp", 1.45, bulk=0.6, hunch=10, top=(42, 72, 42), coat=(52, 92, 52), coat_len=11, helmet=(60, 60, 56), full_mask=(40, 36, 36), glow_eyes=(255, 60, 50), pads=(90, 90, 84), tail=(52, 92, 52), cape=(42, 72, 42), accent=(150, 30, 34), weapon="spear"),
    "Wriggling Beast": P("n_corp", 1.4, bulk=0.6, hunch=10, top=(52, 82, 46), coat=(62, 96, 52), coat_len=11, helmet=(110, 110, 100), full_mask=(40, 36, 36), glow_eyes=(255, 60, 50), tail=(120, 40, 44), cape=(42, 72, 42), accent=(150, 30, 34), weapon=None, claws=BONE),
    "Freezing Miner Slave": P("slave", 1.1, skin=(170, 200, 220), top=(110, 140, 170), horns=(130, 206, 255), splotches=[(130, 206, 255), (200, 236, 255), (130, 206, 255)], weapon="scythe", weapon_tint=(130, 206, 255)),
    "Shivering Miner Slave": P("slave", 1.1, skin=(190, 210, 226), top=(130, 150, 180), horns=(160, 220, 255), splotches=[(160, 220, 255), (200, 236, 255)], weapon="scythe", weapon_tint=(160, 220, 255)),
    "Guido the Persistent": P("n_corp", 1.25, bulk=0.8, helmet=None, full_mask=(232, 228, 222), glow_eyes=(30, 20, 20), hair=(60, 56, 60), top=(62, 58, 62), pants=(50, 46, 50), pads=(80, 76, 80), cape=(152, 24, 30), coat=(122, 20, 26), coat_len=10, accent=GOLD, weapon="greatsword", weapon_tint=(60, 56, 60)),
    "Heathcliff?": P("named", 1.2, (1.15, 1.05, 0.95), hair=(92, 62, 42), hair_style="long", top=(40, 34, 34), coat=(82, 52, 38), coat_len=10, cape=(72, 46, 34), cape_wide=1.5, accent=None, weapon="mace", weapon_tint=(170, 40, 40)),
    "Hindley": P("named", 1.05, hair=(150, 110, 70), top=(92, 82, 92), inner=WHITE, coat=(122, 82, 58), coat_len=9, accent=None, weapon="rapier", weapon_tint=(40, 36, 36)),
    "Hindley of the Wild Hunt": P("named", 1.1, hair=(150, 110, 70), skin=(190, 200, 214), glow_eyes=(110, 200, 240), top=(60, 66, 80), coat=(70, 62, 66), coat_len=9, accent=None, weapon="rapier", weapon_tint=(40, 36, 36)),
    "Hurting Teddy Bear": P("named", 1.4, bulk=2.0, top=(122, 126, 136), skin=(152, 156, 166), pants=(112, 116, 126), shoes=(92, 96, 106), hands=(92, 96, 106), hair_style="bald", ears=(152, 156, 166), snout=(202, 202, 208), eyes=BLACK, eye_white=BLACK, splotches=[(170, 30, 36), (170, 30, 36), (170, 30, 36)], veins=(170, 30, 36), accent=None, weapon=None),
    "Imagined Im Gyeong-eop": P("named", 1.35, (1.15, 1.05, 0.95), top=(92, 20, 24), coat=(62, 14, 18), coat_len=11, hat=(30, 24, 24), brim=3.2, crown_h=2.5, hair=(26, 22, 22), hair_style="long", accent=GOLD, weapon="spear", weapon_tint=(120, 30, 30)),
    "Insane Fashionista Bloodfiend": P("named", 1.25, (1.15, 1.05, 0.92), full_mask=(240, 238, 232), glow_eyes=(20, 16, 20), hair=(26, 22, 24), hair_style="long", top=(120, 16, 26), coat=None, skirt=(152, 20, 30), coat_len=12, skirt_wide=2, accent=None, weapon="scissors"),
    "Mad Fashionista Bloodfiend": P("named", 1.25, (1.15, 1.05, 0.92), full_mask=(240, 238, 232), glow_eyes=(20, 16, 20), hair=(90, 20, 30), hair_style="long", top=(100, 14, 22), coat=None, skirt=(132, 18, 28), coat_len=12, skirt_wide=2, accent=None, weapon="scissors"),
    "Jia Qiu": P("named", 1.3, (1.25, 1.1, 0.92), hair=(24, 22, 26), hair_style="tail", top=(28, 26, 30), coat=(28, 26, 30), coat_len=11, accent=(170, 30, 36), inner=(170, 30, 36), weapon="spear", weapon_tint=(202, 62, 32)),
    "Josephine": P("named", 1.1, hair=(44, 40, 40), hair_style="long", top=(92, 62, 52), coat=(92, 62, 52), coat_len=8, pants=(82, 56, 48), inner=WHITE, accent=None, weapon=None),
    "Josephine of the Wild Hunt": P("named", 1.15, hair=(44, 40, 40), hair_style="long", skin=(190, 200, 214), glow_eyes=(110, 200, 240), top=(72, 62, 62), coat=(72, 62, 62), coat_len=8, inner=WHITE, accent=None, weapon=None),
    "King of Needleworks": P("named", 1.4, (1.4, 1.15, 0.8), slim=True, skin=(202, 172, 112), hair_style="bald", top=(202, 172, 112), pants=(190, 160, 100), crown=GOLD, cape=(42, 122, 132), cape_wide=2, cape_fur=(172, 42, 42), accent=GOLD, eyes=BLACK, weapon="rapier"),
    "Kurokumo Clan Captain Jun": P("kurokumo", 1.2, (1.15, 1.05, 0.95), hair_style="short", coat=(52, 52, 56), coat_len=10, weapon="katana"),
    "L'Inamovible": P("named", 1.6, bulk=2.4, hunch=20, top=(132, 20, 28), skin=(100, 14, 20), pants=(90, 12, 18), hair_style="bald", full_mask=(60, 8, 12), glow_eyes=(255, 120, 90), horns=(40, 6, 10), claws=BLACK, carapace=(60, 8, 12), accent=None, weapon=None),
    "Le Noir Sales Associate Gustave": P("le_noir", 1.2, (1.15, 1.05, 0.95), full_mask=None, hair=(192, 192, 198), hair_style="long", coat=(42, 38, 42)),
    "Le Noir's Brand Manager": P("named", 1.4, bulk=1.0, top=(42, 42, 38), coat=(36, 36, 32), coat_len=10, full_mask=(30, 30, 28), glow_eyes=(200, 190, 110), horns=(60, 60, 50), pads=(142, 132, 72), accent=(142, 132, 72), hair_style="bald", weapon="hammer", weapon_tint=(60, 60, 50), cape=(30, 30, 28)),
    "Le Rouge Sales Associate Mimosa": P("named", 1.2, (1.15, 1.05, 0.92), top=(62, 16, 22), coat=None, pants=(62, 16, 22), veins=(190, 30, 40), full_mask=(190, 30, 40), glow_eyes=(90, 10, 14), crown=(190, 30, 40), hair_style="bald", accent=None, claws=(190, 30, 40), weapon="rapier", weapon_tint=(190, 30, 40)),
    "Linton of the Wild Hunt": P("named", 0.9, hair=(240, 214, 110), top=(28, 28, 32), coat=(28, 28, 32), coat_len=9, accent=None, weapon="rapier"),
    "Lobotomy E.G.O::Red Sheet": P("named", 1.1, top=(62, 56, 52), coat=None, pants=(52, 46, 44), hair=(60, 46, 40), splotches=[(232, 132, 42), (232, 132, 42), (232, 132, 42)], accent=(232, 132, 42), weapon="spear", weapon_tint=(232, 132, 42)),
    "Lobotomy E.G.O::Red Sheet - Marile": P("named", 1.15, top=(62, 56, 52), coat=None, pants=(52, 46, 44), hood=(52, 46, 44), mask=(232, 132, 42), splotches=[(232, 132, 42), (232, 132, 42), (232, 132, 42)], accent=(232, 132, 42), weapon="spear", weapon_tint=(232, 132, 42)),
    "Lobotomy E.G.O::Sloshing": P("named", 1.15, top=(62, 202, 152), coat=None, sleeve=(62, 202, 152), pants=(52, 182, 136), shoes=(40, 150, 110), hair=(62, 52, 52), wings=(130, 236, 240), wing_span=6, accent=(30, 110, 80), weapon="greatsword", weapon_tint=(62, 202, 152)),
    "Mariachi Alegre": P("named", 1.1, top=(42, 52, 92), coat=None, cape=(42, 52, 92), accent=(202, 202, 212), pants=(30, 30, 36), hair=(30, 26, 26), beard=(30, 26, 26), weapon="club", weapon_tint=(150, 60, 50)),
    "Mariachi Vivaz": P("named", 1.1, top=(122, 92, 62), coat=None, cape=(60, 120, 120), accent=(202, 180, 120), pants=(30, 30, 36), hair=(40, 30, 26), hair_style="long", beard=(40, 30, 26), weapon="club", weapon_tint=(150, 60, 50)),
    "Nelly": P("butler", 1.0, hair=(72, 52, 42), hair_style="tail", weapon="club"),
    "Old G Corp. Head Manager": P("named", 1.35, (1.15, 1.05, 0.95), top=(32, 36, 62), coat=(32, 36, 62), coat_len=10, full_mask=(92, 52, 32), glow_eyes=(20, 10, 8), horns=(122, 72, 42), hair_style="bald", accent=(170, 30, 36), claws=(92, 52, 32), weapon=None),
    "One-leg": P("named", 1.35, (1.35, 1.12, 0.82), slim=True, skin=(226, 222, 216), hair=(152, 152, 152), hair_style="long", top=(22, 22, 24), coat=(22, 22, 24), coat_len=12, accent=GOLD, weapon=None),
    "Pallidified Thing-Hook": P("pequod", 1.1, splotches=[(122, 52, 42), (122, 52, 42), (60, 30, 26)], weapon="hook"),
    "Pallidified Thing-Pirate": P("pequod", 1.1, splotches=[(122, 52, 42), (60, 30, 26), (122, 52, 42)], weapon="sword"),
    "Pallidified Thing-Shield": P("pequod", 1.15, visor=None, hair=(150, 110, 80), hair_style="short", cape=(236, 236, 236), cape_wide=2, splotches=[(170, 40, 40), BLACK, (170, 40, 40)], weapon=None),
    "Pink Shoes Enchantee": P("named", 1.2, hunch=30, skin=(62, 58, 62), top=(42, 42, 46), coat=None, pants=(42, 42, 46), shoes=(240, 60, 170), hair=(42, 38, 42), hair_style="long", splotches=[(240, 60, 170), (240, 60, 170), (240, 60, 170)], glow_eyes=(240, 60, 170), claws=(240, 60, 170), accent=None, weapon=None),
    "Pink Shoes Possessee": P("named", 1.1, hunch=18, skin=(92, 86, 90), top=(52, 52, 56), coat=None, pants=(42, 42, 46), shoes=(240, 60, 170), hair=(42, 38, 42), hair_style="long", splotches=[(240, 60, 170), (240, 60, 170)], glow_eyes=(240, 60, 170), accent=None, weapon=None),
    "Rosespanner Workshop Rep Niko": P("rosespanner", 1.15, bulk=0.6, weapon="hammer", weapon_tint=(202, 72, 92)),
    "Rufo": P("named", 1.2, (1.15, 1.05, 0.95), skin=(122, 92, 82), hair=(42, 62, 112), hair_style="long", top=(240, 238, 232), coat=None, skirt=(240, 238, 232), coat_len=12, wings=(232, 222, 192), wing_span=8, accent=None, weapon=None),
    "Shi Huazhen": P("named", 1.2, (1.15, 1.05, 0.92), hair=(26, 24, 30), top=(28, 28, 34), coat=(28, 28, 34), coat_len=10, pants=(26, 26, 30), accent=None, weapon="greatsword", weapon_tint=(40, 40, 46)),
    "Shi Sijing": P("named", 1.1, hair=(52, 46, 42), hair_style="long", top=(72, 92, 62), coat=None, skirt=(72, 92, 62), coat_len=12, shoes=(206, 160, 124), accent=None, weapon=None),
    "Shi Yihua": P("named", 1.25, (1.15, 1.05, 0.95), hair=(192, 32, 42), hair_style="spiky", top=(122, 16, 24), coat=None, skirt=(172, 24, 34), coat_len=12, skirt_wide=4, frill=(220, 60, 60), sleeve=(172, 24, 34), accent=None, weapon=None),
    "So That No One Will Cry": P("named", 1.45, (1.2, 1.05, 0.95), skin=(42, 28, 26), top=(42, 28, 26), coat=None, pants=(42, 28, 26), hair_style="bald", full_mask=(202, 72, 32), glow_eyes=(255, 220, 120), splotches=[(232, 112, 42), (190, 40, 30), (232, 112, 42), (190, 40, 30), (232, 112, 42)], veins=(232, 112, 42), accent=None, weapon=None),
    "The Barber": P("named", 1.25, (1.15, 1.05, 0.92), full_mask=(238, 234, 228), glow_eyes=(20, 16, 20), hood=(152, 20, 30), hair_style="bald", top=(132, 18, 26), coat=None, skirt=(152, 20, 30), coat_len=12, skirt_wide=2, accent=None, cape=(120, 16, 24), weapon="scissors"),
    "The Priest": P("named", 1.25, (1.3, 1.1, 0.85), slim=True, full_mask=(222, 226, 232), glow_eyes=(40, 40, 50), hair=(222, 226, 232), hair_style="spiky", top=(24, 24, 28), coat=None, pants=(24, 24, 28), accent=None, weapon=None),
    "The Ring Apprentice - Albina": P("named", 1.25, (1.15, 1.05, 0.95), hair=WHITE, hair_style="long", top=(242, 238, 222), coat=None, skirt=(242, 238, 222), coat_len=12, skirt_wide=3, frill=GOLD, accent=GOLD, halo=GOLD, weapon="spear", weapon_tint=GOLD),
    "Tingtang Boss": P("tingtang", 1.2, top=(52, 92, 52), splotches=[(90, 140, 80), (30, 60, 30), (90, 140, 80)], hair_style="short", weapon="sword"),
    "Twinhook Pirates First Mate Smee": P("named", 1.15, (1.1, 1.0, 0.95), hair=WHITE, hair_style="long", hat=(152, 30, 34), brim=3, crown_h=2.5, band=GOLD, top=(92, 62, 42), coat=(152, 30, 34), coat_len=9, shoes=(120, 26, 30), accent=GOLD, weapon="hook"),
    "Wayward Passenger": P("named", 1.4, (1.3, 1.1, 0.85), slim=True, skin=(172, 42, 52), top=(42, 36, 46), coat=None, pants=(36, 32, 44), hair_style="bald", glow_eyes=WHITE, veins=(172, 42, 52), accent=None, weapon="katana", weapon_tint=(170, 110, 255)),
    "Wei": P("named", 1.25, hunch=14, hood=(22, 20, 24), top=(22, 20, 24), coat=(22, 20, 24), coat_len=11, cape=(18, 16, 20), cape_wide=1.5, accent=(170, 30, 36), glow_eyes=(255, 80, 60), weapon="spear"),
    "Thirsty Kaki": P("named", 0.8, hair=(42, 40, 40), top=(112, 112, 72), coat=None, pants=(42, 62, 92), shoes=(206, 160, 124), accent=None, weapon=None),
    "Hungry Kaki": P("named", 0.8, hair=(42, 40, 40), top=(132, 92, 62), coat=None, pants=(42, 62, 92), shoes=(206, 160, 124), accent=None, weapon=None),
}
for _g in "βγδ":
    PEOPLE["Dawn of Green - Doubt - " + _g] = PEOPLE["Dawn of Green - Doubt - α"]


def flower(col):
    def add(m, H, L, Wd, bh, hy):
        y = H + bh
        m.box("body", -3, y, -3, 6, 1.2, 6, sh(col, 0.8))
        m.box("body", -2, y + 1.2, -2, 4, 2, 4, col)
        m.box("body", -4, y + 0.6, -1, 8, 0.8, 2, sh(col, 1.15))
        m.box("body", -1, y + 0.6, -4, 2, 0.8, 8, sh(col, 1.15))
    return add


def failure(mid, vine):
    return lambda: Cr.quadruped(mid, {"body": (112, 112, 102), "head": (132, 130, 120), "eye": BLACK, "leg": (92, 92, 84), "tail": (40, 36, 36)}, scale=1.2, H=6, length=13, width=7, bh=5,
                                head=(5, 5, 5), snout=2, spikes=vine, extra=flower(vine), tail=7)


CREATURES = {
    "Blooming Failure": failure("failure_bloom", (232, 100, 150)),
    "Entangled Failure": failure("failure_tangle", (170, 30, 36)),
    "Impaled Failure": failure("failure_impale", (70, 110, 60)),
    "Blubbering Toad": lambda: Cr.quadruped("toad", {"body": (42, 46, 72), "belly": (30, 34, 56), "eye": (50, 100, 255), "jaw": (26, 28, 44), "stripe": (60, 80, 150)}, scale=1.8, squat=True, H=5, length=12, width=13, bh=8, head=(11, 6, 5), snout=2, tail=0, extra=toad_eyes),
    "Crawling Inquisitor": lambda: Cr.quadruped("crawler", {"body": (152, 72, 62), "head": (172, 150, 140), "eye": (120, 230, 120), "jaw": (120, 40, 40), "leg": (120, 50, 46)}, scale=1.4, H=5, length=15, width=8, bh=5, head=(6, 5, 6), snout=3, spikes=BONE, tail=8),
    "Headchicken a.k.a. Bongy": lambda: Cr.quadruped("bongy", {"body": (232, 186, 196), "head": (236, 196, 204), "eye": BLACK, "jaw": (170, 60, 80), "leg": (212, 160, 172)}, scale=1.2, squat=True, H=4, length=10, width=9, bh=8, head=(9, 7, 4), snout=2, tail=0),
    "Dreaming Electric Sheep": lambda: Cr.quadruped("sheep", {"body": (26, 24, 30), "head": (26, 24, 30), "eye": (200, 120, 255), "leg": (20, 18, 22)}, scale=1.4, H=12, length=11, width=8, bh=7, head=(5, 5, 4), snout=2, wool=(224, 204, 240), tail=0),
    "Drifting Fox": lambda: Cr.quadruped("fox", {"body": (62, 72, 86), "belly": (202, 202, 206), "head": (52, 60, 74), "eye": (255, 240, 150), "leg": (40, 46, 56), "tuft": WHITE}, scale=1.5, H=7, length=15, width=6, bh=6, head=(5, 5, 5), snout=4, ears=(40, 46, 56), spikes=(200, 200, 206), tail=12),
    "Four-Legged Beast": lambda: Cr.quadruped("beast4", {"body": (120, 44, 40), "eye": (255, 220, 90), "jaw": (200, 190, 170), "stripe": (60, 20, 22), "leg": (90, 30, 30)}, scale=1.6, H=6, head=(8, 7, 7), snout=3, spikes=BONE, mane=(60, 20, 22)),
    "Rose Hunter": lambda: Cr.quadruped("rose_horse", {"body": (28, 62, 58), "head": (24, 52, 50), "eye": (255, 60, 60), "leg": (20, 44, 42), "tuft": (30, 90, 70), "stripe": (40, 110, 80)}, scale=1.7, H=12, length=17, width=7, bh=8, head=(5, 6, 8), snout=3, mane=(30, 90, 70), ears=(24, 52, 50), spikes=(190, 30, 50), tail=10, extra=flower((190, 30, 50))),
    "Dream-Devouring Siltcurrent": lambda: Cr.serpent("siltcurrent", {"body": (18, 22, 30), "plate": (40, 230, 220), "fin": (40, 230, 220), "eye": (150, 255, 240), "head": (14, 18, 24)}, scale=1.9, legs=False, fins=True, hover=9, mandibles=False, lure=(130, 255, 240), w=6, h=6, back=7),
    "Headless Ichthys": lambda: Cr.serpent("ichthys", {"body": (30, 52, 58), "plate": (20, 36, 40), "fin": (190, 40, 50), "eye": (255, 80, 70), "head": (24, 42, 46)}, scale=1.5, legs=False, fins=True, hover=2, mandibles=False, back=5, front=2, w=6, h=6),
    "Drenched Gossypium": lambda: Cr.plant("gossypium", {"trunk": (22, 20, 22), "vine": (22, 20, 22), "bloom": (236, 236, 236), "accent": (170, 30, 36), "eye": (170, 30, 36)}, scale=1.7, bloom="cotton", trunk=16, arm=6),
    "Faelantern": lambda: Cr.plant("faelantern", {"trunk": (28, 24, 26), "vine": (28, 24, 26), "bloom": (28, 24, 26), "light": (60, 232, 222), "accent": (240, 200, 60)}, scale=1.7, bloom="lantern", thorns=True),
    "Four-hundred Roses": lambda: Cr.plant("roses400", {"trunk": (26, 26, 26), "vine": (26, 26, 26), "bloom": (200, 30, 56), "eye": (200, 30, 56), "accent": (40, 60, 40), "leaf": (30, 50, 30)}, scale=1.9, bloom="rose", thorns=True, arm=9, trunk=17),
    "Fairy Mass": lambda: Cr.winged("fairy_mass", {"body": (32, 112, 92), "wing": (192, 32, 42), "wing2": (150, 24, 34), "spot": WHITE, "eye": WHITE, "leg": (150, 24, 34)}, scale=1.3, span=10, hover=14, antennae=False, long_legs=True),
    "Fairy Queen": lambda: Cr.winged("fairy_queen", {"body": (32, 102, 82), "abdomen": (20, 60, 50), "wing": (160, 170, 180), "wing2": (130, 140, 150), "spot": (220, 230, 236), "eye": (255, 60, 50), "leg": (150, 24, 34)}, scale=1.5, long_legs=True, antennae=True),
    "Fairy-Long-Legs": lambda: Cr.winged("fairy_legs", {"body": (32, 152, 102), "wing": (22, 112, 82), "wing2": (22, 112, 82), "spot": (40, 160, 110), "eye": (20, 60, 40), "leg": (32, 152, 102)}, scale=1.5, long_legs=True, hover=20, span=12, antennae=False),
    "Fruit of Understanding": lambda: Cr.roundling("fruit_understanding", {"body": (92, 62, 132), "limb": (40, 30, 56), "eye": (150, 110, 200), "mouth": (150, 110, 200)}, scale=1.3, shape="blob"),
    "Portrait of a Certain Day": lambda: Cr.roundling("portrait", {"body": (72, 72, 42), "limb": (26, 24, 20), "eye": (20, 16, 12), "mouth": (20, 16, 12)}, scale=1.4, shape="blob"),
    "Grant Us Love": lambda: Cr.totem("grant_love", {"body": (92, 52, 132), "accent": (30, 20, 44), "hand": (22, 18, 28), "face": (150, 110, 200), "eye": (240, 220, 255)}, scale=1.6, core="orb", hover=3),
    "My Form Empties": lambda: Cr.totem("form_empties", {"body": (92, 42, 36), "accent": (52, 26, 24), "head": (110, 50, 42), "hand": (92, 42, 36), "face": (130, 60, 50), "mouth": (40, 10, 10)}, scale=1.7, core="statue", hover=3),
    "Spiral of Contempt": lambda: Cr.totem("spiral", {"body": (110, 60, 140), "accent": (50, 26, 70), "hand": (160, 110, 190), "eye": (255, 220, 120)}, scale=1.6, core="spiral"),
    "kqe-1j-23": lambda: Cr.bot("kqe", {"body": (32, 30, 32), "trim": (132, 30, 34), "light": (232, 92, 232), "light2": (60, 200, 120), "tool": (132, 30, 34)}, scale=1.5, tool="claw", head="visor"),
    "Steam Transport Machine": lambda: Cr.bot("steam_machine", {"body": (130, 100, 70), "trim": (70, 60, 56), "light": (255, 180, 60)}, scale=1.5, tool="claw", head="dome"),
    "Papa Bongy": lambda: Cr.chicken("papa_bongy", {"body": (150, 150, 156), "head": WHITE, "wing": (130, 130, 136), "chest": (232, 150, 50), "leg": (130, 130, 136)}, scale=1.9, apron=(232, 150, 50), bucket=(232, 180, 190)),
}


def toad_eyes(m, H, L, Wd, bh, hy):
    for side, sg in (("Right", 1), ("Left", -1)):
        n = "eye_" + side.lower()
        m.bone(n, "head", (sg * 3.6, hy + 3, L / 2 + 2))
        m.box(n, sg * 3.6 - 2, hy + 1, L / 2 + 0.6, 4, 4, 4, (50, 100, 255), plain=True, marks={"north": [(1.2, 1.2, 1.6, 1.6, BLACK)]})
        m.parts[side + " Eye"] = [n]


def rule(name, low, looks):
    """Kinds of enemies by name, after the reference pictures. Returns (id, builder) or None."""
    if "gnome" in low:
        kind = low.split()[0]
        hood = {"cherry": (202, 42, 46), "leafy": (62, 132, 62), "manager": (202, 42, 46), "shadowy": (40, 36, 44)}.get(kind, (202, 42, 46))
        mid = "gnome_" + kind
        if kind == "shadowy":
            return mid, lambda: Cr.roundling(mid, {"body": (32, 30, 32), "limb": (24, 22, 24), "eye": (255, 230, 120), "mouth": (200, 170, 80), "nails": (32, 30, 32)}, scale=0.8, shape="blob", weapon_kind="chainsaw")
        spec = {"top": hood, "hood": hood, "full_mask": (22, 20, 22), "glow_eyes": (255, 236, 150), "pants": sh(hood, 0.7), "hands": (22, 20, 22), "weapon": "axe", "hair_style": "bald"}
        if kind == "manager":
            spec.update(beard=WHITE, weapon="chainsaw", bulk=1.0)
        return mid, lambda: M.humanoid(mid, spec, scale=0.75 if kind == "manager" else 0.6)
    if "jar monster" in low:
        mid = "jar_" + low.split()[0]
        extra = {"talisman": [(236, 200, 120), (236, 200, 120), (232, 132, 42)], "nailed": [(180, 180, 190), (180, 180, 190)], "entangled": [(232, 132, 42), (40, 30, 30)]}.get(low.split()[0], [])
        spec = {"skin": (62, 42, 36), "top": (92, 52, 32), "pants": (52, 34, 28), "hair_style": "bald", "full_mask": (150, 90, 50), "glow_eyes": (20, 12, 8), "splotches": extra, "bulk": 0.8, "hunch": 12,
                "claws": (30, 20, 16), "horns": (180, 180, 190) if "nailed" in low else None, "pack": (150, 90, 50), "weapon": None}
        return mid, lambda: M.humanoid(mid, spec, scale=1.25)
    if "mermaid" in low:
        whale = "whale" in low
        mid = "mermaid_" + re.sub(r"[^a-z]+", "_", low)[:24].strip("_")
        if "thousand" in low:
            return mid, lambda: Cr.plant(mid, {"trunk": (22, 22, 26), "vine": (22, 22, 26), "bloom": (40, 40, 46), "accent": (150, 140, 130), "eye": (200, 200, 190)}, scale=1.7, bloom="bud", arm=9, thorns=True)
        if whale:
            return mid, lambda: Cr.quadruped(mid, {"body": (28, 48, 54), "head": (22, 38, 44), "eye": WHITE, "leg": (20, 34, 38), "stripe": (60, 200, 200), "tail": (28, 48, 54), "tuft": (40, 90, 100)}, scale=1.5, H=6, length=15, width=7, bh=6,
                                           head=(6, 5, 7), snout=3, spikes=(40, 90, 100), tail=12)
        col = (30, 52, 72) if "blue" in low else (60, 60, 56) if "entwined" in low else (190, 196, 200)
        return mid, lambda: Cr.serpent(mid, {"body": col, "plate": sh(col, 0.6), "fin": (60, 120, 160) if "white" not in low else (60, 60, 66), "eye": (255, 255, 255), "head": sh(col, 0.85)}, scale=1.3, legs=False, fins=True, hover=1,
                                       mandibles=True, back=5, front=2, w=6, h=5)
    if "drone" in low:
        mid = "drone_" + ("arrest" if "arrest" in low else "film" if "filming" in low else "regen")
        return mid, lambda: Cr.totem(mid, {"body": (30, 50, 56), "accent": (20, 30, 34), "eye": (120, 255, 120), "blade": (230, 236, 240)}, scale=0.9, core="drone", hands=False, rotors=True, hover=18)
    if "crab" in low:
        key = low.split()[0]
        kw = {"bayonet": dict(cannon=(70, 60, 40), bayonet=(200, 204, 212), spikes=(40, 46, 24)), "cannoneer": dict(cannon=(70, 60, 40), spikes=(40, 46, 24)), "hard": dict(big_claw=1.4, spikes=(60, 66, 50)),
              "king": dict(crown=GOLD, big_claw=1.5, spikes=(30, 34, 22)), "pointy": dict(spikes=BONE), "sharp": dict(bayonet=(200, 204, 212), spikes=(40, 46, 24))}.get(key)
        if kw is not None:
            mid = "crab_" + key
            shell = {"king": (52, 62, 42), "hard": (72, 82, 62)}.get(key, (92, 102, 52))
            return mid, lambda: Cr.crab(mid, {"shell": shell, "limb": sh(shell, 0.7), "eye": (200, 230, 60), "belly": (60, 60, 40)}, scale={"king": 1.7, "hard": 1.5}.get(key, 1.2), **kw)
    if "sawblade bot" in low or "wrecking ball bot" in low or "combined bot" in low:
        tool = "saw" if "saw" in low else "ball"
        complete = "complete" in low or "combined" in low
        mid = "bot_%s_%s" % (tool, "complete" if complete else "crude")
        return mid, lambda: Cr.bot(mid, {"body": (122, 102, 62) if complete else (92, 96, 104), "trim": (42, 42, 46), "light": (255, 200, 80), "tool": (190, 190, 196)}, scale=1.3 if complete else 1.15, tool=tool, head="dome")
    for k, sin in looks.PECC.items():
        if k in low:
            cls = 3 if "class 3" in low else 2 if "class 2" in low else 1
            mid = "peccatulum_%s_%d" % (sin, cls)
            s = 1.0 + 0.25 * cls
            if sin == "wrath":
                return mid, lambda: Cr.quadruped(mid, {"body": (32, 26, 26), "eye": (255, 150, 40), "stripe": (255, 112, 32), "jaw": (255, 112, 32), "leg": (26, 20, 20), "tuft": (255, 150, 40)}, scale=s, H=7, head=(6, 6, 6), snout=3, spikes=(255, 112, 32))
            if sin == "lust":
                return mid, lambda: Cr.quadruped(mid, {"body": (242, 204, 164), "eye": (190, 60, 30), "stripe": (232, 112, 42), "jaw": (190, 60, 30), "leg": (222, 170, 130)}, scale=s, H=5, length=14, squat=cls == 1, head=(6, 6, 5), snout=2)
            if sin == "gluttony":
                return mid, lambda: Cr.plant(mid, {"trunk": (24, 22, 24), "vine": (24, 22, 24), "bloom": (232, 222, 222), "accent": (172, 30, 40), "eye": (172, 30, 40), "leaf": (24, 22, 24)}, scale=0.8 + 0.3 * cls, bloom="rose", thorns=True)
            if sin == "gloom":
                return mid, lambda: Cr.totem(mid, {"body": (122, 202, 222), "accent": (20, 30, 40), "hand": (20, 30, 40), "face": (200, 236, 244), "eye": (20, 30, 40), "mouth": (190, 40, 50)}, scale=0.8 + 0.25 * cls, core="orb", hover=12)
            if sin == "sloth":
                return mid, lambda: Cr.roundling(mid, {"body": (152, 122, 72), "limb": (120, 30, 30), "eye": (220, 40, 40), "mouth": (30, 24, 20), "mark": (190, 40, 40), "nails": (120, 96, 56) if cls == 3 else None}, scale=0.9 + 0.3 * cls, shape="blob")
            if sin == "pride":
                return mid, lambda: Cr.totem(mid, {"body": (32, 36, 72), "accent": (190, 40, 50), "skirt": (22, 24, 50), "hand": (32, 36, 72), "face": (190, 40, 50), "eye": WHITE}, scale=0.9 + 0.25 * cls, core="disc", hover=10, hands=cls > 1)
    return None
