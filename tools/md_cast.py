"""Which model each Mirror Dungeon enemy gets (see md_models.py, md_creatures.py):
named bosses and Abnormalities one by one, kinds of monsters by rule, and the
rest by faction (md_looks.py's outfits, as 3D people). Shapes and colours follow
what each enemy is known for; the models are our own."""
import re

import md_bosses as Bo
import md_creatures as Cr
import md_models as M

SIN = {"wrath": (196, 48, 40), "lust": (226, 120, 40), "sloth": (214, 178, 52), "gluttony": (120, 170, 60), "gloom": (60, 120, 190),
       "pride": (40, 64, 150), "envy": (130, 60, 160)}
sh = M.shade
GOLD, WHITE, BLACK, BONE = (214, 180, 80), (240, 240, 240), (22, 22, 26), (226, 220, 200)

# People: (base outfit from md_looks, changes, scale).
PEOPLE = {
    "Ahab": ("pequod", {"coat": (30, 40, 70), "top": (30, 40, 70), "hair": (230, 230, 230), "hair_style": "long", "hat": (24, 30, 50), "brim": 2.6, "weapon": "spear", "cape": (24, 30, 50), "bulk": 0.6}, 1.2),
    "Effloresced E.G.O::GasHarpoon": ("pequod", {"coat": (40, 50, 80), "glow_eyes": (120, 220, 255), "weapon": "spear", "weapon_tint": (120, 220, 255), "cape": (20, 26, 44), "bulk": 0.6}, 1.3),
    "Ricardo": ("middle", {"bulk": 2.0, "hair": (224, 50, 120), "hair_style": "spiky", "glasses": (250, 250, 255), "top": (206, 160, 124), "inner": None, "tie": None, "lapels": None, "accent": GOLD, "veins": (70, 60, 70), "sleeve": (206, 160, 124), "pants": (236, 236, 236), "shoes": (110, 50, 150), "gauntlets": GOLD, "cape": (24, 22, 28), "cape_wide": 3, "cape_fur": (20, 18, 24), "emblem": (120, 50, 160), "weapon": None}, 1.45),
    "Kromer": ("n_corp", {"hood": None, "mask": None, "hair": (232, 224, 190), "coat": (240, 236, 222), "top": (240, 236, 222), "pants": (240, 236, 222), "coat_len": 11, "accent": GOLD, "inner": (200, 170, 90), "splotches": [(170, 30, 36), (170, 30, 36), (150, 24, 30)], "gauntlets": (90, 90, 100), "weapon": "greatsword", "weapon_tint": (50, 46, 50), "shoes": (150, 30, 34)}, 1.0),
    "Guido the Persistent": ("n_corp", {"bulk": 1.2, "weapon": "hammer", "pads": (70, 70, 76)}, 1.2),
    "Don Quixote": ("bloodfiend", {"hair": (240, 214, 120), "hair_style": "long", "top": (236, 230, 214), "coat": (236, 230, 214), "accent": GOLD, "pads": GOLD, "cape": (160, 24, 34), "weapon": "lance", "weapon_tint": (190, 30, 40), "bulk": 0.5}, 1.3),
    "Sancho": ("bloodfiend", {"hair": (244, 206, 80), "hair_style": "long", "eyes": (200, 30, 40), "top": (40, 34, 36), "inner": (120, 60, 50), "tie": None, "coat": (30, 26, 30), "coat_len": 9, "accent": (150, 24, 30), "pants": (26, 24, 28), "shoes": (232, 214, 210), "cape": (200, 30, 36), "cape_wide": 3, "cape_fur": (220, 40, 44), "weapon": "lance", "weapon_tint": (190, 30, 40)}, 1.15),
    "Dulcinea": ("bloodfiend", {"hair": (238, 228, 196), "hair_style": "long", "eyes": (150, 80, 220), "top": (34, 26, 44), "inner": (230, 226, 236), "tie": None, "lapels": None, "coat": None, "skirt": (60, 34, 96), "coat_len": 12, "skirt_wide": 5, "frill": (160, 90, 230), "accent": (150, 80, 220), "sleeve": (34, 26, 44), "parasol": (90, 50, 150), "weapon": None, "shoes": (30, 22, 40)}, 1.3),
    "The Barber": ("bloodfiend", {"hair": (170, 30, 40), "hair_style": "long", "top": (30, 20, 24), "coat": None, "skirt": (120, 20, 30), "weapon": "scissors"}, 1.15),
    "The Priest": ("bloodfiend", {"hair": (200, 200, 206), "hair_style": "long", "top": (26, 24, 28), "coat": (26, 24, 28), "coat_len": 10, "inner": WHITE, "tie": None, "accent": (170, 30, 40), "weapon": "mace"}, 1.2),
    "Cassetti": ("bloodfiend", {"hair": (236, 236, 240), "coat": (150, 24, 36), "top": (150, 24, 36), "weapon": "rapier", "cape": (40, 10, 16)}, 1.1),
    "???": ("bloodfiend", {"full_mask": (230, 226, 220), "glow_eyes": (200, 30, 40), "hair_style": "long", "hair": (60, 10, 20), "weapon": "scissors", "bulk": 0.5}, 1.25),
    "Erlking Heathcliff": ("wild_hunt", {"hood": None, "hair": (70, 50, 40), "hair_style": "long", "glow_eyes": None, "eyes": (150, 110, 220), "coat": (40, 38, 46), "top": (56, 52, 60), "pants": (34, 32, 38), "accent": (120, 110, 120), "pads": (60, 58, 66), "pack": (22, 20, 26), "pack_big": True, "weapon": "greatsword", "weapon_tint": (150, 40, 44), "bulk": 0.6}, 1.3),
    "Heathcliff, The Heartbroken": ("wild_hunt", {"hood": None, "hair": (40, 30, 40), "hair_style": "spiky", "coat": (40, 30, 60), "glow_eyes": (190, 120, 255), "chains": (150, 150, 160), "weapon": "greatsword", "weapon_tint": (90, 70, 120), "bulk": 0.8, "hunch": 12}, 1.35),
    "Heathcliff?": ("street", {"hair": (90, 60, 40), "hair_style": "spiky", "top": (90, 60, 120), "weapon": "club", "bulk": 0.4}, 1.05),
    "The Reaved Lamenter of the Wild Hunt": ("wild_hunt", {"bulk": 1.0, "weapon": "scythe", "cape": (20, 24, 34), "horns": (180, 190, 210)}, 1.35),
    "Hindley": ("butler", {"hair": (90, 70, 50), "top": (70, 60, 56), "coat": (70, 60, 56), "weapon": "gun"}, 1.05),
    "Josephine": ("butler", {"hair": (60, 40, 30), "hair_style": "tail", "coat": None, "skirt": (26, 26, 30), "coat_len": 9, "inner": WHITE, "weapon": "baton"}, 1.0),
    "Nelly": ("butler", {"hair": (150, 110, 70), "hair_style": "tail", "coat": None, "skirt": (30, 30, 36), "coat_len": 9, "inner": WHITE, "weapon": "club"}, 1.0),
    "Hindley of the Wild Hunt": ("wild_hunt", {"weapon": "gun", "bulk": 0.4}, 1.1),
    "Josephine of the Wild Hunt": ("wild_hunt", {"skirt": (30, 34, 44), "coat": None, "coat_len": 9, "weapon": "baton"}, 1.05),
    "Linton of the Wild Hunt": ("wild_hunt", {"hood": None, "hair": (230, 210, 140), "weapon": "rapier"}, 1.05),
    "Lei Heng": ("heishou", {"mask": None, "bulk": 1.6, "hair": (230, 140, 40), "hair_style": "spiky", "coat": (24, 22, 26), "top": (24, 22, 26), "accent": GOLD, "pads": (230, 140, 40), "weapon": "greatsword"}, 1.35),
    "Jia Mu": ("shi", {"hair": (200, 200, 206), "hair_style": "tail", "top": (130, 24, 30), "skirt": (130, 24, 30), "coat_len": 11, "accent": GOLD, "weapon": "baton", "weapon_tint": (120, 80, 40)}, 1.0),
    "Jia Qiu": ("heishou", {"mask": None, "hair": (20, 18, 22), "hair_style": "long", "top": (230, 236, 226), "coat": (230, 236, 226), "coat_len": 10, "accent": (70, 150, 110), "weapon": "sword"}, 1.15),
    "Wei": ("heishou", {"hair_style": "tail", "weapon": "spear"}, 1.1),
    "N Corp. E.G.O::Contempt, Awe Jia Huan": ("n_corp", {"hood": None, "hair": (20, 18, 22), "glow_eyes": (255, 220, 120), "coat": (60, 40, 30), "accent": GOLD, "weapon": "spear", "halo": GOLD}, 1.2),
    "N Corp. E.G.O::Fell Bullet Gubo": ("n_corp", {"hood": None, "hat": (30, 28, 32), "glow_eyes": (120, 200, 255), "weapon": "gun", "cape": (30, 28, 32)}, 1.15),
    "Shi Huazhen": ("shi", {"hair_style": "tail", "weapon": "sword", "cape": (110, 20, 24)}, 1.1),
    "Shi Sijing": ("shi", {"hair_style": "short", "weapon": "spear", "pads": GOLD}, 1.1),
    "Shi Yihua": ("shi", {"hair_style": "long", "weapon": "katana", "weapon_tint": (160, 30, 34)}, 1.1),
    "Kurokumo Clan Captain Jun": ("kurokumo", {"hair_style": "tail", "coat": (24, 22, 26), "coat_len": 9, "weapon": "katana", "weapon_tint": (170, 36, 40), "pads": (170, 36, 40)}, 1.15),
    "Twinhook Pirates First Mate Smee": ("pirate", {"bandana": None, "hat": (30, 34, 50), "brim": 2.4, "bulk": 0.8, "weapon": "hook", "beard": (90, 60, 30)}, 1.15),
    "Dead Rabbits Boss": ("rabbits", {"bulk": 1.2, "hair_style": "spiky", "weapon": "club", "ears": None}, 1.2),
    "Tingtang Boss": ("tingtang", {"bulk": 1.0, "hat": (40, 40, 40), "weapon": "gun"}, 1.15),
    "Casino Security Chief": ("casino", {"bulk": 1.2, "weapon": "baton", "pads": (40, 40, 46)}, 1.2),
    "Old G Corp. Head Manager": ("manager", {"big_arm": (90, 140, 70), "claws": (60, 90, 40), "glow_eyes": (160, 255, 120), "hunch": 10, "bulk": 0.6, "weapon": None}, 1.25),
    "Le Noir's Brand Manager": ("le_noir", {"bulk": 0.8, "weapon": "hammer", "weapon_tint": (40, 40, 46), "hair": (200, 200, 206)}, 1.2),
    "Maison du Noir": ("le_noir", {"cape": (20, 20, 24), "weapon": "rapier", "bulk": 0.4}, 1.2),
    "L'Inamovible": ("le_noir", {"bulk": 2.0, "weapon": "shield", "pads": (60, 60, 66), "helmet": (40, 40, 46)}, 1.35),
    "Le Noir Sales Associate Gustave": ("le_noir", {"weapon": "hammer", "hair": (120, 90, 60)}, 1.1),
    "Le Rouge Sales Associate Mimosa": ("le_rouge", {"hair": (240, 210, 90), "hair_style": "long", "skirt": (150, 30, 40), "weapon": "rapier"}, 1.1),
    "Santata": ("named", {"top": (190, 30, 36), "coat": (190, 30, 36), "accent": WHITE, "hat": (190, 30, 36), "brim": 0.6, "crown_h": 5, "band": WHITE, "beard": WHITE, "hair": WHITE, "bulk": 1.6, "pack": (120, 90, 50), "weapon": "club"}, 1.25),
    "Rufo": ("named", {"top": (120, 80, 50), "skin": (150, 104, 70), "horns": (200, 180, 140), "snout": (190, 40, 40), "ears": (120, 80, 50), "bulk": 0.8, "weapon": None, "hair_style": "bald"}, 1.15),
    "Mariachi Alegre": ("mariachi", {"brim": 4.5, "weapon": "club", "weapon_tint": (200, 150, 60)}, 1.05),
    "Mariachi Vivaz": ("mariachi", {"brim": 4.5, "top": (40, 120, 90), "weapon": "gun"}, 1.05),
    "Sous Chef": ("chef", {"crown_h": 5, "brim": 0.3, "weapon": "axe", "bulk": 0.8}, 1.15),
    "Rosespanner Workshop Rep Niko": ("rosespanner", {"bulk": 0.8, "weapon": "hammer", "weapon_tint": (200, 70, 90)}, 1.15),
    "The Ring Apprentice - Albina": ("ring", {"hair": WHITE, "hair_style": "long", "weapon": "spear", "weapon_tint": (220, 60, 60)}, 1.1),
    "The Thumb Apprentice - Lucio": ("thumb", {"hat": None, "hair": (120, 90, 60), "weapon": "sword"}, 1.05),
    "District 20 Yurodiviye Captain": ("yurodivy", {"bulk": 1.2, "chains": (150, 150, 160), "weapon": "mace"}, 1.2),
    "King of Needleworks": ("needle", {"crown": GOLD, "cape": (110, 90, 150), "weapon": "rapier", "bulk": 0.6, "mask": (240, 240, 244)}, 1.35),
    "The Crimson God [Inchoate Life]": ("named", {"top": (150, 20, 30), "skin": (170, 30, 40), "pants": (110, 14, 22), "hair_style": "bald", "glow_eyes": (255, 230, 120), "horns": (60, 10, 16), "bulk": 1.6, "claws": BLACK, "veins": (255, 120, 90), "weapon": None, "halo": (255, 200, 90)}, 1.6),
    "Dongrang, Who Denies All": ("k_corp", {"helmet": None, "visor": (60, 220, 130), "top": (226, 230, 232), "wings": (150, 240, 190), "wing_span": 11, "big_arm": (200, 206, 210), "claws": (60, 200, 120), "bulk": 1.0, "weapon": None, "halo": (120, 255, 180)}, 1.55),
    "Dongbaek E.G.O::Spicebush": ("named", {"top": (236, 200, 90), "skirt": (236, 200, 90), "coat": None, "coat_len": 10, "hair": (60, 40, 30), "hair_style": "long", "accent": (190, 60, 80), "splotches": [(240, 120, 150), (255, 230, 120), (240, 120, 150)], "weapon": "spear", "weapon_tint": (120, 90, 50)}, 1.25),
    "Kkomi and Vergilius?": ("named", {"top": (30, 20, 24), "coat": (110, 20, 26), "hair": (40, 30, 30), "glow_eyes": (255, 60, 50), "weapon": "katana", "weapon_tint": (170, 30, 36), "pack": (230, 220, 200)}, 0.9),
    "Distorted Hohenheim": ("named", {"top": (60, 70, 80), "coat": (236, 236, 230), "coat_len": 10, "hair": (200, 200, 190), "visor": (120, 255, 200), "big_arm": (90, 200, 170), "claws": (40, 120, 100), "hunch": 10, "weapon": None, "bulk": 0.5}, 1.4),
    "Imagined Im Gyeong-eop": ("soldier", {"helmet": (60, 60, 70), "top": (120, 30, 30), "pads": GOLD, "cape": (30, 40, 90), "weapon": "greatsword", "bulk": 1.0, "beard": BLACK}, 1.3),
    "The Time Ripper": ("named", {"top": (26, 26, 30), "coat": (26, 26, 30), "coat_len": 11, "pants": (22, 22, 26), "hat": (22, 22, 26), "crown_h": 5.5, "brim": 1.8, "band": GOLD, "hair_style": "bald", "skin": (40, 38, 40), "full_mask": (30, 28, 30), "glow_eyes": GOLD, "accent": GOLD, "lapels": 1, "emblem": GOLD, "hands": GOLD, "claws": GOLD, "weapon": None}, 1.45),
    "The Knight of Despair": ("named", {"top": (30, 34, 60), "skirt": (30, 34, 60), "coat": None, "coat_len": 10, "hair": (70, 110, 200), "hair_style": "long", "eyes": (60, 120, 255), "accent": (120, 170, 255), "pads": (20, 22, 40), "weapon": "rapier", "halo": (120, 170, 255), "cape": (20, 22, 40)}, 1.35),
    "The King in Binds": ("named", {"top": (90, 70, 50), "coat": (120, 30, 30), "coat_len": 10, "crown": GOLD, "chains": (160, 160, 170), "beard": (200, 200, 200), "hair": (200, 200, 200), "bulk": 1.4, "cape": (90, 20, 24), "weapon": "greatsword", "glow_eyes": (255, 220, 120)}, 1.5),
    "Wayward Passenger": ("named", {"top": (90, 80, 76), "skin": (190, 150, 150), "hair_style": "bald", "big_arm": (170, 80, 90), "claws": BONE, "veins": (170, 40, 60), "hunch": 16, "glow_eyes": (255, 255, 255), "weapon": None, "bulk": 0.8}, 1.45),
    "Headless Ichthys": ("named", {"no_head": True, "top": (60, 90, 110), "coat": (40, 60, 80), "skin": (150, 190, 200), "weapon": "trident", "veins": (120, 200, 210), "bulk": 0.6, "tail": (120, 170, 190)}, 1.4),
    "So That No One Will Cry": ("named", {"top": (200, 196, 180), "skin": (210, 204, 190), "hair_style": "bald", "full_mask": (230, 200, 80), "glow_eyes": (120, 20, 20), "splotches": [(230, 200, 80), (230, 200, 80), (190, 40, 40)], "big_arm": (230, 200, 80), "chains": (150, 110, 60), "weapon": None, "hunch": 8}, 1.45),
    "Fairy Gentleman": ("named", {"top": (60, 214, 150), "coat": None, "skin": (70, 226, 160), "pants": (50, 196, 136), "shoes": (40, 170, 116), "hands": (90, 236, 176), "sleeve": (60, 214, 150), "accent": None, "hair_style": "bald", "eyes": (10, 60, 40), "eye_white": (10, 60, 40), "grin": (20, 90, 60), "ears_long": (70, 226, 160), "tie": BLACK, "tiny_hat": BLACK, "bulk": 3.6, "belly": (70, 226, 160), "big_arm": (90, 236, 176), "wings": (130, 236, 240), "wing_span": 10, "veins": (150, 250, 200), "weapon": None}, 1.6),
    "Hurting Teddy Bear": ("named", {"top": (150, 104, 66), "skin": (160, 112, 72), "pants": (140, 96, 60), "shoes": (120, 80, 50), "hands": (120, 80, 50), "hair_style": "bald", "ears": (150, 104, 66), "snout": (220, 190, 150), "eyes": BLACK, "eye_white": BLACK, "bulk": 2.0, "weapon": None, "splotches": [(200, 60, 60)]}, 1.4),
    "Rose Hunter": ("named", {"top": (40, 70, 50), "coat": (150, 24, 40), "hat": (150, 24, 40), "brim": 2.6, "full_mask": (40, 70, 50), "glow_eyes": (255, 120, 150), "weapon": "rapier", "weapon_tint": (60, 120, 70), "splotches": [(220, 50, 80), (220, 50, 80)]}, 1.35),
    "Crawling Inquisitor": ("n_corp", {"hunch": 30, "claws": (200, 200, 210), "weapon": None, "glow_eyes": (255, 80, 60), "bulk": 0.5}, 1.3),
    "Proceeding Inquisitor": ("n_corp", {"hunch": 8, "horns": (150, 150, 160), "weapon": "hammer", "glow_eyes": (255, 80, 60), "bulk": 1.0}, 1.4),
    "Slithering Inquisitor": ("n_corp", {"hunch": 14, "big_arm": (60, 56, 64), "claws": (200, 200, 210), "weapon": None, "glow_eyes": (255, 80, 60), "tail": (40, 38, 44), "bulk": 0.8}, 1.45),
    "Everything There of an Inquisitor": ("n_corp", {"hunch": 6, "horns": (150, 150, 160), "wings": (60, 56, 64), "big_arm": (60, 56, 64), "weapon": "hammer", "glow_eyes": (255, 80, 60), "bulk": 1.6, "halo": (255, 80, 60)}, 1.6),
    "Effloresced E.G.O::Farmwatch": ("named", {"top": (150, 120, 70), "hat": (190, 160, 90), "brim": 3.2, "full_mask": (120, 90, 50), "glow_eyes": (255, 160, 60), "weapon": "scythe", "coat": (120, 90, 50)}, 1.3),
    "Lobotomy E.G.O::Red Sheet": ("lobotomy", {"splotches": [(200, 40, 40), (200, 40, 40), (200, 40, 40)], "hood": (170, 30, 34), "weapon": "spear"}, 1.1),
    "Lobotomy E.G.O::Red Sheet - Marile": ("lobotomy", {"splotches": [(200, 40, 40), (200, 40, 40)], "hood": (170, 30, 34), "hair": (230, 200, 120), "weapon": "spear", "cape": (170, 30, 34)}, 1.15),
    "Lobotomy E.G.O::Sloshing": ("lobotomy", {"coat": (60, 140, 90), "veins": (140, 240, 160), "weapon": "club", "weapon_tint": (60, 140, 90)}, 1.1),
    "Aida": ("named", {"top": (236, 236, 240), "coat": (236, 236, 240), "hair": (60, 50, 60), "hair_style": "long", "accent": (120, 170, 255), "weapon": "baton"}, 1.05),
    "One-leg": ("pirate", {"bulk": 1.0, "weapon": "hook", "beard": (60, 40, 30)}, 1.2),
    "Annette the Alterationist": ("needle", {"hair": (230, 150, 170), "hair_style": "tail", "skirt": (190, 186, 200), "weapon": "scissors"}, 1.1),
    "Censored Lobotomy Corp. L3 Agent": ("lobotomy", {"full_mask": BLACK, "glow_eyes": (255, 60, 60), "weapon": "baton"}, 1.05),
    "Pink Shoes Enchantee": ("fixer", {"shoes": (240, 120, 170), "skirt": (240, 160, 190), "hair_style": "long", "weapon": None, "glow_eyes": (240, 120, 170)}, 1.05),
    "Pink Shoes Possessee": ("fixer", {"shoes": (240, 120, 170), "accent": (240, 120, 170), "weapon": None, "glow_eyes": (240, 120, 170)}, 1.05),
    "Freezing Miner Slave": ("slave", {"skin": (170, 200, 220), "top": (120, 150, 170), "weapon": "pickaxe"}, 1.0),
    "Shivering Miner Slave": ("slave", {"skin": (190, 210, 226), "top": (140, 160, 180), "weapon": "pickaxe"}, 1.0),
    "Blooming Failure": ("bloodbag", {"skin": (170, 200, 150), "top": (90, 130, 80), "veins": (240, 120, 160), "hunch": 14, "horns": (240, 120, 160)}, 1.15),
    "Entangled Failure": ("bloodbag", {"skin": (170, 200, 150), "top": (70, 110, 70), "veins": (60, 90, 50), "hunch": 18, "chains": (60, 90, 50)}, 1.15),
    "Impaled Failure": ("bloodbag", {"skin": (170, 200, 150), "top": (80, 120, 80), "veins": (200, 200, 210), "hunch": 10, "horns": (200, 200, 210)}, 1.15),
    "Pallidified Thing-Hook": ("pequod", {"skin": (220, 230, 236), "top": (190, 200, 210), "coat": (170, 184, 196), "hat": None, "hair_style": "bald", "glow_eyes": (120, 200, 230), "weapon": "hook"}, 1.1),
    "Pallidified Thing-Pirate": ("pequod", {"skin": (220, 230, 236), "top": (190, 200, 210), "coat": (170, 184, 196), "glow_eyes": (120, 200, 230), "weapon": "sword"}, 1.1),
    "Pallidified Thing-Shield": ("pequod", {"skin": (220, 230, 236), "top": (190, 200, 210), "coat": (170, 184, 196), "hat": None, "hair_style": "bald", "glow_eyes": (120, 200, 230), "weapon": "shield", "bulk": 1.0}, 1.15),
    "Insane Fashionista Bloodfiend": ("bloodfiend", {"hair": (230, 120, 160), "hair_style": "long", "weapon": "scissors", "skirt": (90, 20, 30)}, 1.1),
    "Mad Fashionista Bloodfiend": ("bloodfiend", {"hair": (160, 120, 230), "weapon": "scissors"}, 1.1),
}


def telepole(m, H, L, Wd, bh, hy):
    """Telephone poles stuck in its back, leaning."""
    m.bone("pole", "body", (0, H + bh, -2))
    wood = (86, 60, 50)
    for i, (x, z, hgt) in enumerate(((-1.5, -6, 13), (1.5, -1, 15), (-0.5, 4, 11))):
        m.box("pole", x - 0.6, H + bh - 1, z - 0.6, 1.2, hgt, 1.2, wood)
        m.box("pole", x - 3, H + bh + hgt - 3.5, z - 0.4, 6, 0.8, 0.8, wood)
        m.box("pole", x - 2.2, H + bh + hgt - 5.5, z - 0.4, 4.4, 0.7, 0.7, wood)
    m.parts["Telepole"] = m.parts["Back"] = ["pole"]


def apple_stem(m, H, L, Wd, bh, hy):
    y = H + bh
    m.box("body", -6, y, -6, 12, 1.2, 12, (250, 210, 70))
    m.box("body", -0.7, y + 1, -0.7, 1.4, 3.4, 1.4, (70, 46, 30))
    m.box("body", -0.7, y + 4, -0.7, 3, 1.2, 1.4, (70, 46, 30))
    m.box("body", -Wd / 2 - 0.4, H + 2, -L / 2 - 0.4, 5.5, 8, 6, (30, 22, 18))
    for sx, z in ((1, 4), (-1, -3)):
        m.box("body", sx * (Wd / 2) - 0.6, H + 1, z, 1.2, 6, 1.2, (96, 64, 40))
    gold = (246, 196, 40)
    m.box("body", -Wd / 2 - 1.5, H + 3, -L / 2 + 2.5, Wd + 3, bh - 6, L - 5, gold)
    m.box("body", -Wd / 2 + 2.5, H + 3, -L / 2 - 1.5, Wd - 5, bh - 6, L + 3, gold)
    m.box("body", -Wd / 2 + 2, H - 1.2, -L / 2 + 2, Wd - 4, 1.4, L - 4, (226, 170, 30))


def toad_eyes(m, H, L, Wd, bh, hy):
    for side, sg in (("Right", 1), ("Left", -1)):
        n = "eye_" + side.lower()
        m.bone(n, "head", (sg * 3, hy + 4, L / 2 + 2))
        m.box(n, sg * 3 - 1.6, hy + 2.6, L / 2 + 0.6, 3.2, 3.2, 3.2, (255, 236, 110), plain=True, marks={"north": [(1, 1, 1.2, 1.4, BLACK)]})
        m.parts[side + " Eye"] = [n]


def abno(name, sin_col):
    """The Abnormalities and creatures, one by one. Returns a Model or None."""
    c = sin_col
    A = {
        "Alleyway Watchdog": lambda: Cr.quadruped("watchdog", {"body": (32, 36, 54), "head": (24, 26, 40), "eye": (240, 240, 250), "jaw": (16, 16, 26), "leg": (22, 24, 38), "tuft": (40, 46, 70)}, scale=1.6, length=18, head=(7, 6, 7), snout=5, mane=(22, 24, 38), ears=(24, 26, 40), spikes=(22, 24, 38), tail=12, extra=telepole),
        "Blubbering Toad": lambda: Cr.quadruped("toad", {"body": (60, 110, 150), "belly": (200, 220, 236), "eye": (255, 230, 90), "jaw": (40, 60, 110)}, scale=1.7, squat=True, H=5, length=12, width=12, bh=8, head=(10, 6, 5), snout=2, tail=0, extra=toad_eyes),
        "Brazen Bull - Tearful": lambda: Cr.quadruped("bull", {"body": (126, 44, 30), "head": (110, 36, 26), "eye": (70, 210, 255), "leg": (70, 26, 22), "paw": (70, 210, 255), "stripe": (244, 130, 40), "tuft": (150, 90, 50), "jaw": (190, 40, 40)}, scale=1.9, H=9, length=17, width=10, bh=10, head=(7, 7, 6), snout=3, horns=(50, 30, 30), mane=(100, 34, 26)),
        "Dreaming Electric Sheep": lambda: Cr.quadruped("sheep", {"body": (70, 70, 90), "head": (40, 40, 56), "eye": (120, 230, 255), "leg": (40, 40, 56)}, scale=1.4, H=7, length=13, width=8, bh=7, head=(5, 5, 5), snout=2, wool=(236, 236, 220), horns=(240, 220, 90), tail=3),
        "Drifting Fox": lambda: Cr.quadruped("fox", {"body": (220, 130, 50), "belly": (240, 230, 220), "head": (220, 130, 50), "eye": (255, 240, 120), "leg": (60, 40, 30), "tuft": (240, 240, 240)}, scale=1.5, H=8, length=14, width=6, bh=6, head=(5, 5, 5), snout=4, ears=(60, 40, 30), tail=11),
        "Four-Legged Beast": lambda: Cr.quadruped("beast4", {"body": (90, 40, 50), "eye": (255, 220, 90), "jaw": (200, 190, 170), "stripe": (50, 20, 30)}, scale=1.6, head=(8, 7, 7), snout=3, plates=(200, 190, 170), mane=(50, 20, 30)),
        "Wriggling Beast": lambda: Cr.serpent("wriggler", {"body": (120, 50, 60), "plate": (70, 24, 34), "eye": (255, 220, 90)}, scale=1.5, legs=False, back=7, w=6, h=5),
        "Ardor Blossom Moth": lambda: Cr.winged("moth", {"body": (22, 18, 18), "abdomen": (240, 130, 30), "band": (250, 220, 90), "head": (230, 70, 30), "wing": (240, 120, 30), "wing2": (220, 56, 24), "spot": (255, 226, 100), "eye": (255, 240, 150), "antenna": (22, 18, 18), "leg": (16, 14, 14), "bright": True}, scale=1.8, long_legs=True, span=15, flame=True),
        "Fairy Mass": lambda: Cr.winged("fairy_mass", {"body": (200, 230, 190), "wing": (220, 250, 230), "spot": (160, 220, 180), "eye": (20, 60, 30)}, scale=0.9, span=9, hover=18, antennae=False),
        "Fairy Queen": lambda: Cr.winged("fairy_queen", {"body": (120, 200, 150), "wing": (200, 250, 220), "wing2": (150, 230, 190), "spot": (255, 240, 150), "eye": (20, 60, 30)}, scale=1.6, crown=GOLD, antennae=False),
        "Fairy-Long-Legs": lambda: Cr.winged("fairy_legs", {"body": (60, 80, 70), "wing": (120, 160, 150), "spot": (220, 240, 230), "eye": (255, 240, 150), "leg": (40, 50, 46)}, scale=1.5, long_legs=True, hover=18, span=11),
        "Shock Centipede": lambda: Cr.serpent("centipede", {"body": (34, 44, 76), "plate": (20, 26, 48), "leg": (16, 20, 36), "head": (26, 32, 56), "eye": (255, 50, 60), "fang": (120, 200, 255)}, scale=1.7, back=7, head=(8, 6, 8)),
        "Dream-Devouring Siltcurrent": lambda: Cr.serpent("siltcurrent", {"body": (36, 54, 84), "plate": (24, 36, 60), "fin": (90, 230, 200), "eye": (150, 255, 230), "bright": False}, scale=1.9, legs=False, fins=True, hover=9, mandibles=False, lure=(130, 255, 220), w=6, h=6, back=7),
        "Ambling Pearl": lambda: Cr.clam("pearl", {"shell": (96, 90, 84), "inner": (110, 210, 80), "pearl": (220, 240, 200), "leg": (18, 18, 20), "eye": (40, 80, 30)}, scale=1.6, stilts=12),
        "Ebony Queen's Apple": lambda: Cr.plant("ebony_apple", {"trunk": (56, 36, 46), "vine": (46, 60, 40), "bloom": (40, 16, 36), "eye": (255, 60, 70), "mouth": (200, 30, 40), "leaf": (60, 90, 50)}, scale=1.8, thorns=True, arm=9),
        "Golden Apple": lambda: Cr.quadruped("golden_apple", {"body": (246, 196, 40), "leg": (96, 64, 40), "paw": (70, 46, 30), "belly": (30, 22, 18)}, scale=1.7, H=9, length=15, width=15, bh=14, tail=0, no_head=True, extra=apple_stem),
        "Fruit of Understanding": lambda: Cr.plant("fruit_understanding", {"trunk": (120, 110, 100), "bloom": (236, 220, 230), "eye": (120, 60, 160), "leaf": (150, 120, 200)}, scale=1.4, trunk=10, arm=6),
        "Four-hundred Roses": lambda: Cr.plant("roses400", {"trunk": (40, 80, 50), "bloom": (200, 30, 56), "eye": (255, 230, 120)}, scale=1.9, bloom="rose", thorns=True, arm=9),
        "Sign of Roses": lambda: Cr.plant("rose_sign", {"trunk": (50, 90, 60), "bloom": (230, 90, 130), "eye": (255, 250, 200)}, scale=1.6, bloom="rose", thorns=True),
        "Faelantern": lambda: Cr.plant("faelantern", {"trunk": (56, 76, 56), "vine": (70, 100, 70), "bloom": (76, 60, 44), "light": (255, 236, 130)}, scale=1.7, bloom="lantern"),
        "Drenched Gossypium": lambda: Cr.plant("gossypium", {"trunk": (70, 90, 80), "bloom": (236, 240, 244), "eye": (90, 160, 220)}, scale=1.6, bloom="cotton"),
        "Doomsday Calendar": lambda: Cr.totem("calendar", {"body": (120, 60, 50), "face": (150, 90, 76), "hand": (150, 30, 30), "accent": (80, 34, 30), "skirt": (160, 26, 26), "mouth": (40, 10, 10)}, scale=1.7, core="disc", hover=14),
        "Portrait of a Certain Day": lambda: Cr.totem("portrait", {"body": (200, 166, 76), "hand": (226, 220, 200), "picture": (50, 60, 90)}, scale=1.5, core="frame"),
        "Spiral of Contempt": lambda: Cr.totem("spiral", {"body": (110, 60, 140), "accent": (50, 26, 70), "hand": (160, 110, 190), "eye": (255, 220, 120)}, scale=1.6, core="spiral"),
        "Grant Us Love": lambda: Cr.totem("grant_love", {"body": (220, 90, 130), "accent": GOLD, "hand": (240, 220, 226), "face": (250, 236, 240)}, scale=1.7, core="heart"),
        "My Form Empties": lambda: Cr.totem("form_empties", {"body": (190, 160, 90), "accent": (110, 90, 50), "head": (206, 180, 110), "hand": (206, 180, 110), "face": (220, 196, 130)}, scale=1.7, core="statue", hover=3),
        "Skin Prophet": lambda: Cr.totem("skin_prophet", {"body": (226, 170, 160), "accent": (170, 110, 100), "hand": (226, 170, 160), "face": (240, 220, 200), "mouth": (150, 30, 40)}, scale=1.5, core="orb", hover=2),
        "Steam Transport Machine": lambda: Cr.bot("steam_machine", {"body": (130, 100, 70), "trim": (70, 60, 56), "light": (255, 180, 60)}, scale=1.5, tool="claw", head="dome"),
        "kqe-1j-23": lambda: Cr.bot("kqe", {"body": (200, 204, 210), "trim": (70, 76, 90), "light": (120, 230, 255), "screen": (30, 50, 70)}, scale=1.4, tool="claw", head="screen", pipes=False),
        "You Want To Get Beat? Hurtily?": lambda: Cr.bot("beat_bot", {"body": (236, 190, 60), "trim": (60, 60, 70), "light": (255, 80, 80)}, scale=1.4, tool="ball", head="screen"),
        "Become Strong? Genuinely?": lambda: Cr.bot("strong_bot", {"body": (90, 170, 220), "trim": (50, 60, 80), "light": (255, 240, 120)}, scale=1.4, tool="drill", head="screen"),
        "Grown Powerful! For Realsies?": lambda: Cr.bot("power_bot", {"body": (220, 90, 90), "trim": (70, 40, 44), "light": (255, 240, 120)}, scale=1.55, tool="ball", head="screen"),
        "Clippity-cloppity? Tap Away!": lambda: Cr.bot("tap_bot", {"body": (150, 200, 120), "trim": (50, 70, 50), "light": (255, 255, 255)}, scale=1.3, tool="claw", head="screen"),
        "Ch'unokkun - Bind": lambda: Cr.bot("chunokkun_bind", {"body": (90, 80, 110), "trim": (40, 36, 54), "light": (200, 120, 255)}, scale=1.3, tool="claw", head="dome"),
        "Ch'unokkun - Incapacitate": lambda: Cr.bot("chunokkun_stun", {"body": (110, 80, 80), "trim": (54, 36, 40), "light": (255, 200, 90)}, scale=1.3, tool="gun", head="dome"),
        "BongBong [Orange]": lambda: Cr.chicken("bongbong", {"body": (240, 150, 50), "wing": (220, 120, 30)}, scale=1.0),
        "BunBun [Juicy Squeezer - Orange]": lambda: Cr.chicken("bunbun", {"body": (240, 170, 70), "wing": (230, 130, 40)}, scale=1.3, apron=WHITE),
        "Headchicken a.k.a. Bongy": lambda: Cr.chicken("bongy", {"body": (244, 240, 230), "wing": (220, 214, 200)}, scale=1.2),
        "Papa Bongy": lambda: Cr.chicken("papa_bongy", {"body": (244, 240, 230), "wing": (200, 190, 170), "chest": (236, 200, 120)}, scale=1.9, bucket=WHITE),
        "Spiced-up Papa Bongy": lambda: Cr.chicken("spicy_bongy", {"body": (210, 70, 40), "wing": (150, 40, 26), "chest": (240, 150, 60), "eye": (255, 240, 120)}, scale=2.0, bucket=WHITE),
        "Da Boingy Empworoar": lambda: Cr.chicken("emperor_bongy", {"body": (244, 240, 230), "wing": (200, 40, 50), "chest": GOLD}, scale=2.2, crown=GOLD),
    }
    f = A.get(name)
    return f() if f else None


def rule(name, data, looks):
    """Kinds of monsters by name. Returns (id, builder) or None."""
    low = name.lower()
    for k, sin in looks.PECC.items():
        if k in low:
            col = SIN[sin]
            cls = 3 if "class 3" in low else 2 if "class 2" in low else 1
            spec = {"top": sh(col, 0.5), "skin": sh(col, 0.85), "pants": sh(col, 0.4), "hands": sh(col, 0.35), "hair_style": "bald", "full_mask": sh(col, 1.1),
                    "glow_eyes": WHITE, "veins": sh(col, 1.3), "claws": sh(col, 0.3), "hunch": 14 + cls * 3, "bulk": 0.4 * cls, "weapon": None}
            if cls >= 2:
                spec["horns"] = sh(col, 0.4)
            if cls == 3:
                spec["big_arm"] = sh(col, 0.6)
                spec["carapace"] = sh(col, 0.35)
            return "peccatulum_%s_%d" % (sin, cls), lambda: M.humanoid("peccatulum_%s_%d" % (sin, cls), spec, scale=0.95 + 0.2 * cls)
    if "crab" in low:
        v = {"bayonet artillery": dict(cannon=(80, 84, 96), bayonet=(200, 204, 212)), "cannoneer": dict(cannon=(80, 84, 96)), "hard trash": dict(big_claw=1.4),
             "king trash": dict(crown=GOLD, big_claw=1.5), "pointy trash": dict(spikes=BONE), "sharp trash": dict(bayonet=(200, 204, 212))}
        for key, kw in v.items():
            if key in low:
                mid = "crab_" + key.split()[0]
                shell = {"king": (190, 60, 110), "hard": (120, 110, 130), "pointy": (200, 110, 50), "sharp": (200, 70, 50)}.get(key.split()[0], (170, 80, 60))
                return mid, lambda: Cr.crab(mid, {"shell": shell}, scale=1.5 if "king" in key else 1.1, **kw)
    if "sawblade bot" in low or "wrecking ball bot" in low or "combined bot" in low or "dawn of green" in low:
        tool = "saw" if "saw" in low or "dawn" in low else "ball"
        complete = "complete" in low or "combined" in low
        if "dawn" in low:
            i = "αβγδ".index(name[-1]) if name[-1] in "αβγδ" else 0
            mid = "dawn_green_%d" % i
            return mid, lambda: Cr.bot(mid, {"body": (60, 130 + 14 * i, 80), "trim": (30, 50, 40), "light": (200, 255, 160)}, scale=1.0 + 0.06 * i, tool=("saw", "drill", "gun", "ball")[i], head="dome")
        mid = "bot_%s_%s" % (tool, "complete" if complete else "crude")
        body = (150, 154, 164) if complete else (150, 120, 90)
        return mid, lambda: Cr.bot(mid, {"body": body, "trim": (70, 72, 80), "light": (240, 70, 50)}, scale=1.25 if complete else 1.1, tool=tool, head="visor" if complete else "dome")
    if "drone" in low:
        mid = "drone_" + ("arrest" if "arrest" in low else "film" if "filming" in low else "regen")
        acc = {"drone_arrest": (240, 70, 60), "drone_film": (90, 160, 255), "drone_regen": (70, 220, 130)}[mid]
        return mid, lambda: Cr.totem(mid, {"body": (206, 210, 214), "accent": (70, 90, 80), "eye": acc, "bright": False}, scale=0.8, core="drone", hands=False, rotors=True, hover=18)
    if "jar monster" in low:
        mid = "jar_" + low.split()[0]
        return mid, lambda: Cr.roundling(mid, {"body": (176, 116, 76), "eye": (255, 220, 90), "mark": (236, 220, 150) if "talisman" in low else None,
                                              "nails": (180, 180, 190) if "nailed" in low else None, "limb": (60, 50, 60) if "entangled" in low else (120, 76, 50)}, scale=1.2)
    if "kaki" in low:
        mid = "kaki_" + low.split()[0]
        return mid, lambda: Cr.roundling(mid, {"body": (236, 130, 40), "limb": (120, 70, 30), "mouth": (120, 30, 20)}, shape="fruit", scale=0.9)
    if "mermaid" in low:
        whale = "whale" in low
        mid = "mermaid_" + re.sub(r"[^a-z]+", "_", low)[:24].strip("_")
        spec = dict(looks.LOOKS["mermaid"][0], weapon="trident" if "soldier" in low or whale else None, fish=(90, 130, 170) if whale else (150, 200, 216),
                    hair=(60, 80, 120) if "blue" in low else (236, 240, 244), bulk=0.8 if whale else 0)
        return mid, lambda: Cr.mermaid(mid, spec, scale=1.25 if whale else 1.0)
    if "gnome" in low:
        mid = "gnome_" + low.split()[0]
        hat = {"cherry": (200, 40, 60), "leafy": (70, 150, 60), "manager": (40, 40, 50), "shadowy": (50, 40, 70)}.get(low.split()[0], (190, 40, 40))
        spec = dict(looks.LOOKS["gnome"][0], hat=hat, crown_h=6, brim=0.4, beard=WHITE, weapon="scythe" if "leafy" in low else "club")
        return mid, lambda: M.humanoid(mid, spec, scale=0.6)
    if "bloodbag" in low:
        spec = dict(looks.LOOKS["bloodbag"][0], bulk=0.8, hunch=10, weapon=None, claws=(170, 30, 40))
        return "bloodbag", lambda: M.humanoid("bloodbag", spec, scale=1.05)
    return None


def cast(enemies, looks):
    models, assign = {}, {}

    def put(name, mid, build):
        if mid not in models:
            models[mid] = build()
            assert models[mid].id == mid, (mid, models[mid].id)
        assign[name] = mid

    for name, data in enemies.items():
        base = name[len("Refracted "):] if name.startswith("Refracted ") and name[len("Refracted "):] in enemies else name
        sins = [s["sin"] for s in data["skills"] if s.get("sin") in SIN]
        col = SIN[max(set(sins), key=sins.count)] if sins else SIN["gloom"]
        if base in Bo.BOSSES:
            put(name, "b_" + Bo.BOSSES[base].__name__, Bo.BOSSES[base])
            continue
        m = abno(base, col)
        if m is not None:
            put(name, m.id, lambda m=m: m)
            continue
        if base in PEOPLE:
            look, over, sc = PEOPLE[base]
            mid = "p_" + re.sub(r"[^a-z0-9]+", "_", base.lower()).strip("_")[:40]
            spec = dict(looks.LOOKS[look][0])
            spec["weapon"] = M.WEAPON_OF_ITEM.get(looks.LOOKS[look][1])
            spec.update(over)
            put(name, mid, lambda: M.humanoid(mid, spec, scale=sc))
            continue
        r = rule(base, data, looks)
        if r:
            put(name, r[0], r[1])
            continue
        look, form = looks.look_of(base, data)
        if look.startswith("abno_"):
            # An Abnormality without a body of its own yet: a hunched creature in its sin's colour.
            mid = "abno_" + look[5:]
            spec = {"top": sh(col, 0.55), "skin": sh(col, 0.8), "pants": sh(col, 0.45), "hands": sh(col, 0.35), "hair_style": "bald", "full_mask": sh(col, 0.7),
                    "glow_eyes": (250, 240, 200), "veins": sh(col, 1.2), "claws": sh(col, 0.3), "horns": sh(col, 0.4), "hunch": 16, "bulk": 1.0, "weapon": None}
            put(name, mid, lambda: M.humanoid(mid, spec, scale=1.5))
            continue
        spec = dict(looks.LOOKS[look][0])
        spec["weapon"] = M.WEAPON_OF_ITEM.get(looks.weapon_of(look, data))
        put(name, look, lambda: M.humanoid(look, spec, scale=looks.FORM[form]["scale"] if form in ("human", "small") else 1.0))
    return models, assign
