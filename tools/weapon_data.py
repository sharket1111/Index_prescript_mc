"""Builds paper/src/main/resources/gacha/weapons.json: the gacha's 2-star weapons,
each one an original 2-star Identity's weapon with its three skills at every
Uptie (Limbus Company Wiki identity pages, CC BY-SA 4.0).

    python3 tools/weapon_data.py <ids.json>

<ids.json> maps page title -> wikitext. Skill numbers per Uptie come from the
page's prefixed fields ("2spower", "3ce1"...: Uptie 2/3; unprefixed: Uptie 4;
a missing field falls back to the next higher Uptie's). Coin effects are read
into the same plain actions as the E.G.O (tools/ego_data.py), and put in Korean
by a small translator.
"""
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ego_data import actions, plain  # noqa: E402

OUT = os.path.join("paper", "src", "main", "resources", "gacha", "weapons.json")

# Identity -> (id, Korean weapon name, item model, Korean skill names)
WEAPONS = {
    "Seven Assoc. South Section 6 Yi Sang": ("seven_rapier", "세븐 협회 레이피어", "minecraft:iron_sword", ["플레슈", "리포스트", "물리네"]),
    "Shi Assoc. South Section 5 Ishmael": ("shi_blade", "시 협회 환도", "minecraft:iron_sword", ["비검", "섬격", "숨 고르기"]),
    "N Corp. Kleinhammer Heathcliff": ("kleinhammer", "N사 작은 망치", "minecraft:mace", ["어설픈 못박기", "정…화!", "허약한 응징"]),
    "Liu Assoc. South Section 6 Meursault": ("liu_gauntlet", "료 협회 권갑", "minecraft:blaze_rod", ["돌격", "굳건한 자세", "완성된 사권"]),
    "Zwei Assoc. South Section 4 Faust": ("zwei_sword", "츠바이 협회 대검", "minecraft:netherite_sword", ["순찰", "의뢰인 보호", "법과 질서"]),
    "Kurokumo Clan Wakashu Hong Lu": ("kurokumo_katana", "흑운회 와카슈 도", "minecraft:diamond_sword", ["베기", "구름 가르기", "운파"]),
    "W Corp. L2 Cleanup Agent Faust": ("w_corp_blade", "W사 정리 요원 곤봉", "minecraft:breeze_rod", ["에너지 순환", "도약", "과충전"]),
    "T Corp. Class 2 Collection Staff Rodion": ("t_corp_mace", "T사 징수직 곤봉", "minecraft:iron_shovel", ["징수 준비", "T사 무력 진압", "징수 집행"]),
    "Cinq Assoc. South Section 4 Outis": ("cinq_epee", "생크 협회 에페", "minecraft:trident", ["마르슈", "퓌니시옹", "발레스트라 팡트"]),
    "Los Mariachis Jefe Sinclair": ("mariachi_maracas", "로스 마리아치스 마라카스", "minecraft:goat_horn", ["바일레 이 롤라", "열정의 춤", "피냐타 파티"]),
}
# The passive, in the gift language (md_gift_dsl.py), its four values for Uptie 1-4, and its Korean name.
PASSIVES = {
    "seven_rapier": ("예리한 연구", "always: clash 1/1/2/2"),
    "shi_blade": ("죽음의 선을 걷는 자", "hit: gain poise 1/1/2/2 0 @hp<50"),
    "kleinhammer": ("젠장…", "ally_down: sp -10; ally_down: buff power 1/1/2/2; hit: inflict bleed 1/1/1/2 0 @has:bleed !turn 2"),
    "liu_gauntlet": ("태산", "hurt: inflict burn 1/1/2/2 0 random !turn 4"),
    "zwei_sword": ("구역 수호", "combat_start: buff defense 2/2/3/4; combat_start: buff defense 2 @hp<50"),
    "kurokumo_katana": ("흑운도", "hit: inflict bleed 3/3/4/4 0 !turn 1"),
    "w_corp_blade": ("자가 충전", "hit: gain charge 0 1/1/1/2 !turn 3"),
    "t_corp_mace": ("골든 타임 - 대치", "turn_end: heal 60% @hp<20 !combat 1; always: clash 0/0/1/1 @hp<50"),
    "cinq_epee": ("방향 전환", "hurt: buff haste 1/1/1/2 !turn 1"),
    "mariachi_maracas": ("쾌락의 환희", "clash_win: inflict sinking 1/1/2/3 0; clash_lose: buff protect 1 !turn 1"),
}

SINNER_KO = {"Yi Sang": "이상", "Faust": "파우스트", "Don Quixote": "돈키호테", "Ryōshū": "료슈", "Meursault": "뫼르소", "Hong Lu": "홍루",
             "Heathcliff": "히스클리프", "Ishmael": "이스마엘", "Rodion": "로쟈", "Sinclair": "싱클레어", "Outis": "오티스", "Gregor": "그레고르"}
KO = [
    (r"\[Heads Hit\]", "[앞면 적중시]"), (r"\[Tails Hit\]", "[뒷면 적중시]"), (r"\[On Hit\]", "[적중시]"), (r"\[Clash Win\]", "[합 승리시]"),
    (r"\[Clash Lose\]", "[합 패배시]"), (r"\[On Use\]", "[사용시]"), (r"\[Combat Start\]", "[전투 시작시]"), (r"\[Attack End\]", "[공격 종료시]"),
    (r"\[On Crit\]", "[치명타시]"), (r"\[On Kill\]", "[처치시]"), (r"\[Hit after Clash Win\]", "[합 승리 후 적중시]"),
    (r"Inflict (\d+) ([A-Z][a-z]+(?: [A-Z][a-z]+)*) Count", r"\2 횟수 \1 부여"), (r"Inflict (\d+) ([A-Z][a-z]+(?: [A-Z][a-z]+)*)", r"\2 \1 부여"),
    (r"Gain (\d+) ([A-Z][a-z]+(?: [A-Z][a-z]+)*) Count", r"\2 횟수 \1 얻음"), (r"Gain (\d+) ([A-Z][a-z]+(?: [A-Z][a-z]+)*)", r"\2 \1 얻음"),
    (r"next turn", "(다음 턴)"), (r"Heal (\d+) SP", r"정신력 \1 회복"), (r"Heal (\d+) HP", r"체력 \1 회복"), (r"deal \+(\d+)% (?:more )?damage", r"피해량 +\1%"),
    (r"Trigger Tremor Burst", "진동 폭발"), (r"Final Coin inflicts \+1 more Stack of its effects?", "마지막 코인 효과 1 더 부여"),
    (r"\bBurn\b", "화상"), (r"\bBleed\b", "출혈"), (r"\bTremor\b", "진동"), (r"\bRupture\b", "파열"), (r"\bSinking\b", "침잠"), (r"\bPoise\b", "호흡"),
    (r"\bCharge\b", "충전"), (r"\bParalyze\b", "마비"), (r"\bBind\b", "속박"), (r"\bFragile\b", "취약"), (r"\bHaste\b", "신속"),
    (r"\bProtection\b", "보호"), (r"Damage Up", "피해량 증가"), (r"Power Up", "위력 증가"), (r"Offense Level Up", "공격 레벨 증가"),
    (r"Offense Level Down", "공격 레벨 감소"), (r"Defense Level Down", "방어 레벨 감소"), (r"Defense Level Up", "방어 레벨 증가"),
    (r"Pierce Fragility", "관통 취약"), (r"Slash Fragility", "참격 취약"), (r"Blunt Fragility", "타격 취약"), (r"Power Down", "위력 감소"),
]


def ko(text):
    for a, b in KO:
        text = re.sub(a, b, text)
    return text


def field(body, name):
    m = re.search(r"\|" + re.escape(name) + r"=([^\n]*)", body)
    return m.group(1).strip() if m else None


def at(body, name, uptie):
    """A field at Uptie {@code uptie}: its own prefix, else the next higher Uptie's, else the plain (Uptie 4) one."""
    for u in range(uptie, 4):
        v = field(body, "%d%s" % (u, name))
        if v is not None and v != "":
            return v
    return field(body, name)


def template(text, name):
    start = text.find("|" + name + "={{")
    if start < 0:
        return None
    i = text.index("{{", start)
    depth = 0
    for j in range(i, len(text)):
        if text.startswith("{{", j):
            depth += 1
        elif text.startswith("}}", j):
            depth -= 1
            if depth == 0:
                return text[i + 2:j]
    return None


def skill(body, uptie, name_ko):
    coins = int(at(body, "coin", uptie) or 1)
    lines, effects = [], []
    se = at(body, "se", uptie)
    if se:
        for part in re.split(r"<br\s*/?>", se):
            if plain(part) and not plain(part).startswith("{{"):
                lines.append(ko(plain(part)))
                effects += [dict(a, coin=-1) for a in actions(part)]
    for c in range(1, coins + 1):
        ce = at(body, "ce%d" % c, uptie)
        if not ce:
            continue
        for part in re.split(r"<br\s*/?>", ce):
            if plain(part):
                lines.append("코인 %d: %s" % (c, ko(plain(part))))
                effects += [dict(a, coin=c - 1) for a in actions(part)]
    cp = (at(body, "cpower", uptie) or "+ 1").replace(" ", "")
    return {"name": name_ko, "name_en": field(body, "name"), "type": (field(body, "type") or "slash").lower(),
            "sin": (field(body, "sin") or "wrath").lower(), "power": int(at(body, "spower", uptie) or 3), "coin_power": int(cp),
            "coins": coins, "weight": int(at(body, "atkweight", uptie) or 1), "text": lines, "effects": effects}


def split_top(body):
    """A template body split on its own pipes (not those of templates inside it)."""
    parts, depth, cur, i = [], 0, "", 0
    while i < len(body):
        if body.startswith("{{", i):
            depth += 1
            cur += "{{"
            i += 2
            continue
        if body.startswith("}}", i):
            depth -= 1
            cur += "}}"
            i += 2
            continue
        if body[i] == "|" and depth == 0:
            parts.append(cur)
            cur = ""
        else:
            cur += body[i]
        i += 1
    parts.append(cur)
    return parts


def main(path):
    pages = json.load(open(path, encoding="utf-8"))
    out = []
    for title, (wid, name, model, skill_names) in WEAPONS.items():
        text = pages[title]
        bodies = [template(text, "skill%d" % i) for i in (1, 2, 3)]
        upties = []
        for u in (1, 2, 3, 4):
            upties.append([skill(b, u, skill_names[i]) for i, b in enumerate(bodies)])
        body = template(text, "passive1") or ""
        parts = split_top(body)
        pname = parts[1].strip() if len(parts) > 1 else ""
        texts = [x for x in parts[2:] if "=" not in x.split("{{")[0]]
        ptext = ko(plain(" ".join(texts)))
        sinner = re.search(r"\|sinner=([^\n]+)", text).group(1).strip()
        from md_gift_dsl import parse
        pko, pspec = PASSIVES[wid]
        per = parse(pspec, 4)
        out.append({"id": wid, "name": name, "identity": title,
                    "passive_name": pko, "passive_effects": [e for e, _ in per], "passive_ko": [k for _, k in per], "sinner": SINNER_KO.get(sinner, sinner), "rarity": 2, "model": model,
                    "passive": {"name": pname, "text": ptext}, "upties": upties})
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    note = "From the Limbus Company Wiki (limbuscompany.wiki.gg), CC BY-SA 4.0. Generated by tools/weapon_data.py."
    with open(OUT, "w", encoding="utf-8") as fh:
        json.dump({"about": note, "weapons": out}, fh, ensure_ascii=False, separators=(",", ":"))
    print("weapons", len(out))


if __name__ == "__main__":
    main(sys.argv[1])
