"""Which model each Mirror Dungeon enemy gets (see md_models.py): people by
faction (from md_looks.py's outfits, in 3D), then creatures and bosses."""
import md_models as M


def cast(enemies, looks):
    models, assign = {}, {}
    for name, data in enemies.items():
        look, form = looks.look_of(name, data)
        spec = dict(looks.LOOKS[look][0])
        spec["weapon"] = M.WEAPON_OF_ITEM.get(looks.weapon_of(look, data))
        mid = look
        if mid not in models:
            models[mid] = M.humanoid(mid, spec, scale=looks.FORM[form]["scale"])
        assign[name] = mid
    return models, assign
