#!/usr/bin/env python3
"""Generates the Bedrock (Geyser) side of the Paper plugin's custom items.

Reads the Java resource pack (src/main/resources/assets/index_prescript): the
item model definitions, the 2D icon models and the 3D held models. Writes:

- paper/bedrock/...: textures, item_texture.json, and for each 3D model a
  geometry, an attachable and its hold animations, so Bedrock players see the
  same 3D weapons in hand and the same icons in their inventory;
- paper/src/main/resources/geyser/index_prescript_mappings.json: Geyser custom
  item mappings (format 2) matching the item_model component and the
  custom_model_data string that picks the form or release stage.

Java model elements become Bedrock cubes the way java2bedrock converts them:
x is mirrored about the model centre, and the Java display transforms for
first and third person become animations on a chain of bones
(geyser_custom > _x > _y > _z).

Run from the repository root: python3 tools/bedrock.py
"""
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "src/main/resources/assets/index_prescript"
OUT = ROOT / "paper/bedrock"
MAPPINGS = ROOT / "paper/src/main/resources/geyser/index_prescript_mappings.json"
NS = "index_prescript"

# Java item, item model, the custom_model_data strings that select a model, and names.
ITEMS = [
    ("minecraft:iron_sword", "caduceus", ["hatchet", "stiletto", "bastard_sword", "rapier", "hammer", "greatsword",
                                         "lance", "whip", "scythe"], "카두케우스"),
    ("minecraft:netherite_sword", "laevatein", ["release_1", "release_2", "release_3"], "레바테인"),
    ("minecraft:mace", "renoir_hammer", [], "르누아르의 망치"),
    ("minecraft:paper", "rien_terminal", [], "뤼엔의 단말기"),
    ("minecraft:paper", "laevatein_icon", [], "봉인 해제"),
]


def load_model(name):
    return json.loads((JAVA / "models/item" / f"{name}.json").read_text())


def resolve(node, context_3d, string):
    """Follows an item model definition for a display context (3D or GUI) and a custom_model_data string."""
    kind = node.get("type")
    if kind == "minecraft:model":
        return node["model"].split(":", 1)[1].split("/", 1)[1]
    if kind == "minecraft:select":
        prop = node["property"]
        for case in node["cases"]:
            when = case["when"] if isinstance(case["when"], list) else [case["when"]]
            if prop == "minecraft:display_context" and not context_3d and "gui" in when:
                return resolve(case["model"], context_3d, string)
            if prop == "minecraft:custom_model_data" and string in when:
                return resolve(case["model"], context_3d, string)
        return resolve(node["fallback"], context_3d, string)
    raise ValueError(f"unsupported item model node {kind}")


def texture_of(model, key):
    return model["textures"][key].split(":", 1)[1].split("/", 1)[1]


def face_uv(name, uv):
    u0, v0, u1, v1 = uv
    if name == "up":
        return {"uv": [u1, v1], "uv_size": [u0 - u1, v0 - v1]}
    if name == "down":
        return {"uv": [u1, v0], "uv_size": [u0 - u1, v1 - v0]}
    return {"uv": [u0, v0], "uv_size": [u1 - u0, v1 - v0]}


def cube(element):
    f, t = element["from"], element["to"]
    result = {
        "origin": [round(-t[0] + 8, 4), round(f[1], 4), round(f[2] - 8, 4)],
        "size": [round(t[0] - f[0], 4), round(t[1] - f[1], 4), round(t[2] - f[2], 4)],
        "uv": {name: face_uv(name, face["uv"]) for name, face in element["faces"].items()},
    }
    rotation = element.get("rotation")
    if rotation and rotation.get("angle"):
        angle, axis, origin = rotation["angle"], rotation["axis"], rotation["origin"]
        result["pivot"] = [round(-origin[0] + 8, 4), round(origin[1], 4), round(origin[2] - 8, 4)]
        result["rotation"] = {"x": [-angle, 0, 0], "y": [0, -angle, 0], "z": [0, 0, angle]}[axis]
    return result


def geometry(identifier, model):
    bind = "q.item_slot_to_bone_name(c.item_slot)"
    return {
        "format_version": "1.16.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": identifier,
                "texture_width": 16,
                "texture_height": 16,
                "visible_bounds_width": 4,
                "visible_bounds_height": 4.5,
                "visible_bounds_offset": [0, 0.75, 0],
            },
            "bones": [
                {"name": "geyser_custom", "binding": bind, "pivot": [0, 8, 0]},
                {"name": "geyser_custom_x", "parent": "geyser_custom", "pivot": [0, 8, 0]},
                {"name": "geyser_custom_y", "parent": "geyser_custom_x", "pivot": [0, 8, 0]},
                {"name": "geyser_custom_z", "parent": "geyser_custom_y", "pivot": [0, 8, 0],
                 "cubes": [cube(e) for e in model["elements"]]},
            ],
        }],
    }


def display_bones(display, base):
    """Bone transforms for one Java display context on top of the base hand placement."""
    rotation = display.get("rotation", [0, 0, 0])
    translation = display.get("translation", [0, 0, 0])
    scale = display.get("scale", [1, 1, 1])
    return {
        "geyser_custom": base,
        "geyser_custom_x": {"rotation": [-rotation[0], 0, 0],
                            "position": [-translation[0], translation[1], translation[2]],
                            "scale": scale},
        "geyser_custom_y": {"rotation": [0, -rotation[1], 0]},
        "geyser_custom_z": {"rotation": [0, 0, rotation[2]]},
    }


THIRD_PERSON = {"rotation": [90, 0, 0], "position": [0, 13, -3]}
FIRST_PERSON = {"rotation": [90, 60, -40], "position": [4, 10, 4], "scale": 1.5}


def animations(prefix, model):
    display = model.get("display", {})
    contexts = {
        "thirdperson_main_hand": ("thirdperson_righthand", THIRD_PERSON),
        "thirdperson_off_hand": ("thirdperson_lefthand", THIRD_PERSON),
        "firstperson_main_hand": ("firstperson_righthand", FIRST_PERSON),
        "firstperson_off_hand": ("firstperson_lefthand", FIRST_PERSON),
    }
    return {f"{prefix}.{name}": {"loop": True, "bones": display_bones(display.get(java, {}), base)}
            for name, (java, base) in contexts.items()}


def attachable(identifier, geo, texture, prefix):
    return {
        "format_version": "1.10.0",
        "minecraft:attachable": {
            "description": {
                "identifier": identifier,
                "materials": {"default": "entity_alphatest", "enchanted": "entity_alphatest_glint"},
                "textures": {"default": f"textures/models/{NS}/{texture}",
                             "enchanted": "textures/misc/enchanted_item_glint"},
                "geometry": {"default": geo},
                "animations": {
                    "thirdperson_main_hand": f"{prefix}.thirdperson_main_hand",
                    "thirdperson_off_hand": f"{prefix}.thirdperson_off_hand",
                    "firstperson_main_hand": f"{prefix}.firstperson_main_hand",
                    "firstperson_off_hand": f"{prefix}.firstperson_off_hand",
                },
                "scripts": {"animate": [
                    {"thirdperson_main_hand": "c.item_slot == 'main_hand' && !c.is_first_person"},
                    {"thirdperson_off_hand": "c.item_slot == 'off_hand' && !c.is_first_person"},
                    {"firstperson_main_hand": "c.item_slot == 'main_hand' && c.is_first_person"},
                    {"firstperson_off_hand": "c.item_slot == 'off_hand' && c.is_first_person"},
                ]},
                "render_controllers": [f"controller.render.{NS}.item"],
            }
        },
    }


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent="\t") + "\n")


def main():
    # Only what this script generates; textures/ui and texts are kept by hand.
    for folder in ("attachables", "models", "animations", "render_controllers", "textures/items", "textures/models"):
        shutil.rmtree(OUT / folder, ignore_errors=True)
    item_textures = {}
    all_animations = {}
    mappings = {}

    for java_item, name, strings, title in ITEMS:
        definition = json.loads((JAVA / "items" / f"{name}.json").read_text())["model"]
        group = {"type": "group", "model": f"{NS}:{name}", "definitions": []}
        for string in [None] + strings:
            suffix = name if string is None else f"{name}_{string}"
            identifier = f"{NS}:{suffix}"
            icon = f"{NS}.{suffix}"

            gui_model = load_model(resolve(definition, False, string))
            icon_texture = texture_of(gui_model, "layer0")
            (OUT / "textures/items").mkdir(parents=True, exist_ok=True)
            shutil.copy(JAVA / "textures/item" / f"{icon_texture}.png", OUT / "textures/items" / f"{suffix}.png")
            item_textures[icon] = {"textures": f"textures/items/{suffix}"}

            held_name = resolve(definition, True, string)
            held = load_model(held_name)
            entry = {
                "bedrock_identifier": identifier,
                "display_name": title,
                "bedrock_options": {"icon": icon, "display_handheld": java_item != "minecraft:paper"},
            }
            if string is not None:
                entry["predicate"] = {"type": "match", "property": "custom_model_data", "index": 0, "value": string}
                entry["priority"] = 1
            group["definitions"].append(entry)

            if "elements" in held:
                geo = f"geometry.{NS}.{suffix}"
                prefix = f"animation.{NS}.{suffix}"
                texture = texture_of(held, "0")
                target = OUT / "textures/models" / NS / f"{texture}.png"
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy(JAVA / "textures/item" / f"{texture}.png", target)
                write(OUT / "models/entity" / NS / f"{suffix}.geo.json", geometry(geo, held))
                write(OUT / "attachables" / NS / f"{suffix}.json", attachable(identifier, geo, texture, prefix))
                all_animations.update(animations(prefix, held))
        mappings.setdefault(java_item, []).append(group)

    write(OUT / "textures/item_texture.json",
          {"resource_pack_name": "Index Prescript", "texture_name": "atlas.items", "texture_data": item_textures})
    write(OUT / "animations" / f"{NS}.animation.json", {"format_version": "1.8.0", "animations": all_animations})
    write(OUT / "render_controllers" / f"{NS}.render_controllers.json", {
        "format_version": "1.8.0",
        "render_controllers": {f"controller.render.{NS}.item": {
            "geometry": "Geometry.default",
            "materials": [{"*": "Material.default"}],
            "textures": ["Texture.default"],
        }},
    })
    write(MAPPINGS, {"format_version": 2, "items": mappings})
    print(f"{len(item_textures)} items, {sum(1 for _ in (OUT / 'attachables').rglob('*.json'))} attachables")


if __name__ == "__main__":
    main()
