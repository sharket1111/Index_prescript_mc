#!/usr/bin/env python3
"""Writes the Korean item and enchantment names the PlayerTrade web page uses
(trade/src/main/resources/web/ko.json), from Minecraft's own ko_kr language
file: {"items": {"diamond": "다이아몬드", ...}, "enchants": {"sharpness": "날카로움", ...}}.

Usage: python3 tools/web_names.py <path to ko_kr.json>
(the file is in the game's assets: minecraft/lang/ko_kr.json)
"""
import json
import sys
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "trade/src/main/resources/web/ko.json"


def main(source):
    lang = json.loads(Path(source).read_text(encoding="utf-8"))
    items, enchants = {}, {}
    for key, value in lang.items():
        parts = key.split(".")
        if len(parts) != 3 or parts[1] != "minecraft":
            continue
        kind, name = parts[0], parts[2]
        if kind == "block" and name not in items:
            items[name] = value
        elif kind == "item":
            items[name] = value
        elif kind == "enchantment":
            enchants[name] = value
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps({"items": dict(sorted(items.items())), "enchants": dict(sorted(enchants.items()))},
                              ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"{len(items)} items, {len(enchants)} enchantments -> {OUT}")


if __name__ == "__main__":
    main(sys.argv[1])
