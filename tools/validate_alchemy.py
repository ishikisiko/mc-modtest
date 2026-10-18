#!/usr/bin/env python3
"""Validate bundled alchemy rules, survival resources, modeled furnace and optional jar."""
from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path
import re
import struct
import zipfile

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = Path('src/main/resources')
ASSETS = RESOURCES / 'assets/myvillage'
DATA = RESOURCES / 'data/myvillage'
RULES = DATA / 'alchemy/rules.json'
JAVA = Path('src/main/java/com/example/myvillage')
SOURCE_FILES = tuple(JAVA / f'{name}.java' for name in (
    'alchemy/LearnedFormulas', 'alchemy/AlchemyKnowledge', 'alchemy/AlchemyAutofill',
    'item/FormulaItem', 'alchemy/AlchemyMenu', 'alchemy/AlchemyBatch', 'alchemy/PillQuality', 'alchemy/MedicineState',
    'alchemy/MedicineRuntime', 'block/entity/AlchemyFurnaceBlockEntity',
    'item/AlchemyPillItem', 'item/ModDataComponents', 'client/alchemy/AlchemyScreen',
))
HERBS = ('spirit_leaf', 'dew_blossom', 'sunroot', 'blood_berry', 'cloud_fungus', 'calm_lotus')
KINDS = ('huiling', 'yangyuan', 'ningshen')
ITEMS = ('alchemy_furnace', *HERBS, *(f'formula_{k}' for k in KINDS), *(f'pill_{k}' for k in KINDS))
PLANTS = ('fern', 'blue_orchid', 'carrot', 'sweet_berries', 'brown_mushroom', 'lily_pad')
FUEL = '#c:spirit_stones/low_grade'
PAIRS = dict(zip(KINDS, zip(HERBS[::2], HERBS[1::2])))
JAR_CLASSES = tuple(f'com/example/myvillage/{name}.class' for name in (
    'alchemy/LearnedFormulas', 'alchemy/AlchemyKnowledge', 'alchemy/AlchemyAutofill',
    'alchemy/AlchemyRules', 'alchemy/BrewingProgress', 'alchemy/AlchemyMenu', 'alchemy/AlchemyMenus',
    'block/AlchemyFurnaceBlock', 'block/entity/AlchemyFurnaceBlockEntity', 'client/alchemy/AlchemyScreen',
    'item/AlchemyPillItem', 'item/FormulaItem', 'alchemy/MedicineEffects', 'alchemy/MedicineState',
    'alchemy/MedicineRuntime', 'alchemy/AlchemyBatch', 'alchemy/PillQuality', 'item/ModDataComponents',
))


def load_json(raw: bytes):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError(f'duplicate JSON key {key!r}')
            result[key] = value
        return result
    def invalid(value):
        raise ValueError(f'nonfinite JSON number {value}')
    return json.loads(raw, object_pairs_hook=unique, parse_constant=invalid)


def check_rules(data) -> list[str]:
    """Check the authored schema without depending on runtime implementation text."""
    errors = []
    def error(code, detail):
        errors.append(f'{code}: {detail}')
    def keys(obj, expected, where):
        if not isinstance(obj, dict) or set(obj) != set(expected):
            error('ALCHEMY_SCHEMA', f'{where}: expected keys {sorted(expected)}')
            return False
        return True
    def number(obj, key, low, high, where, integer=False, exclusive=False):
        value = obj.get(key)
        valid = type(value) is int if integer else type(value) in (int, float)
        try:
            valid = valid and math.isfinite(value) and (low < value if exclusive else low <= value) and value <= high
        except OverflowError:
            valid = False
        if not valid:
            error('ALCHEMY_BOUNDS', f'{where}.{key}: invalid number {value!r}')
    if not keys(data, ('version', 'use_ticks', 'shared_cooldown_ticks', 'recipes', 'effects', 'quality'), 'rules'):
        return errors
    if type(data['version']) is not int or data['version'] != 2:
        error('ALCHEMY_VERSION', 'rules version must be integer 2')
    number(data, 'use_ticks', 1, 72000, 'rules', True)
    number(data, 'shared_cooldown_ticks', 1, 1200000, 'rules', True)
    quality = data['quality']
    if keys(quality, ('weights_by_cap', 'strength_multipliers'), 'quality'):
        weights = quality['weights_by_cap']
        if not isinstance(weights, list) or len(weights) != 4:
            error('ALCHEMY_QUALITY_WEIGHTS', 'quality.weights_by_cap must have exactly four cap rows')
        else:
            for cap, row in enumerate(weights, 1):
                if (not isinstance(row, list) or len(row) != 4
                        or any(type(weight) is not int or not 0 <= weight <= 1000000 for weight in row)):
                    error('ALCHEMY_QUALITY_WEIGHTS', f'cap {cap}: four integer weights in 0..1000000 required')
                elif any(weight <= 0 for weight in row[:cap]) or any(row[cap:]):
                    error('ALCHEMY_QUALITY_WEIGHTS', f'cap {cap}: eligible qualities need positive weights; qualities above cap need zero')
        multipliers = quality['strength_multipliers']
        valid = isinstance(multipliers, list) and len(multipliers) == 4
        if valid:
            try:
                valid = (all(type(value) in (int, float) and math.isfinite(value) and 1 <= value <= 16
                             for value in multipliers)
                         and multipliers[0] == 1
                         and all(a < b for a, b in zip(multipliers, multipliers[1:])))
            except OverflowError:
                valid = False
        if not valid:
            error('ALCHEMY_QUALITY_MULTIPLIERS', 'four finite strength multipliers must start at 1 and strictly increase up to 16')
    recipes = data['recipes']
    if not isinstance(recipes, list) or len(recipes) != 3:
        error('ALCHEMY_RECIPES', 'exactly three brewing recipes required')
        recipes = recipes if isinstance(recipes, list) else []
    seen = set()
    for index, recipe in enumerate(recipes):
        where = f'recipes[{index}]'
        if not keys(recipe, ('id', 'formula', 'ingredients', 'fuel_count', 'duration_ticks', 'output', 'output_count'), where):
            continue
        kind = recipe['id']
        if not isinstance(kind, str) or kind not in KINDS or kind in seen:
            error('ALCHEMY_REFERENCE', f'{where}: duplicate or unknown recipe id')
            continue
        seen.add(kind)
        ingredients = recipe['ingredients']
        if (not isinstance(ingredients, list) or len(ingredients) != 2
                or any(not isinstance(i, str) for i in ingredients)
                or set(ingredients) != {f'myvillage:{h}' for h in PAIRS[kind]}):
            error('ALCHEMY_REFERENCE', f'{where}: expected the two herbs for {kind}')
        if recipe['formula'] != f'myvillage:formula_{kind}' or recipe['output'] != f'myvillage:pill_{kind}':
            error('ALCHEMY_REFERENCE', f'{where}: formula and pill must match recipe identity')
        number(recipe, 'fuel_count', 1, 64, where, True)
        # ContainerData uses a signed short for current/total progress.
        number(recipe, 'duration_ticks', 1, 32767, where, True)
        number(recipe, 'output_count', 1, 16, where, True)
    if seen != set(KINDS):
        error('ALCHEMY_RECIPES', 'recipe identities must cover all three medicines once')
    effects = data['effects']
    if not keys(effects, KINDS, 'effects'):
        return errors
    specs = {
        'huiling': {'power_fraction': (0, 1, False, True)},
        'yangyuan': {'duration_ticks': (1, 1200000, True, False),
                     'interval_ticks': (1, 1200000, True, False), 'heal_fraction': (0, 1, False, True)},
        'ningshen': {'duration_ticks': (1, 1200000, True, False), 'stability_multiplier': (1, 16, False, True)},
    }
    for kind, fields in specs.items():
        effect = effects[kind]
        if not keys(effect, fields, f'effects.{kind}'):
            continue
        for key, limits in fields.items():
            number(effect, key, *limits[:2], f'effects.{kind}', *limits[2:])
        if kind == 'yangyuan' and all(type(effect[k]) is int for k in ('interval_ticks', 'duration_ticks')):
            if effect['interval_ticks'] > effect['duration_ticks']:
                error('ALCHEMY_BOUNDS', 'healing interval cannot exceed duration')
    return errors


def _java_code(source: str) -> str:
    """Ignore comments and literals when checking code-level authority boundaries."""
    tokens = re.compile(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/')
    return tokens.sub(' ', source)


def check_sources(sources: dict[Path, str]) -> list[str]:
    """Guard side and transport boundaries; runtime tests prove batch transactions."""
    errors = []
    for relative, raw in sources.items():
        source = _java_code(raw)
        if 'client' not in relative.parts and re.search(r'\bimport\s+(?:static\s+)?(?:net\.minecraft|net\.neoforged\.neoforge)\.client\.', source):
            errors.append(f'ALCHEMY_SIDE: {relative}: common alchemy code imports client types')
        if 'client' in relative.parts and re.search(
                r'\brollQuality\s*\(|\bPillQuality\s*\.\s*apply\s*\(|\bMath\s*\.\s*random\s*\(|'
                r'\bnew\s+(?:java\.util\.)?(?:Random|SplittableRandom)\s*\(|\bRandomSource\b|\bgetRandom\s*\(', source):
            errors.append(f'ALCHEMY_CLIENT_AUTHORITY: {relative}: the client cannot choose or assign pill quality')
        if re.search(r'\bimplements\s+[^\{;]*\bCustomPacketPayload\b', source):
            errors.append(f'ALCHEMY_PAYLOAD: {relative}: alchemy uses vanilla menu start intent and synchronization')
        for match in re.finditer(r'\bnew\s+ContainerData\s*\(\s*\)\s*\{', source):
            start, depth, end = match.end(), 1, match.end()
            while end < len(source) and depth:
                depth += (source[end] == '{') - (source[end] == '}')
                end += 1
            if re.search(r'\.\s*(?:quality|rollQuality)\s*\(|\bPillQuality\b', source[start:end]):
                errors.append(f'ALCHEMY_HIDDEN_QUALITY: {relative}: menu data exposes the hidden batch outcome')
    return errors


def validate(root: Path = ROOT, jar: Path | None = None) -> dict:
    root = Path(root)
    errors = []
    checked = set()
    def error(code, detail):
        errors.append(f'{code}: {detail}')
    def read(relative):
        checked.add(relative)
        try:
            value = load_json((root / relative).read_bytes())
            if not isinstance(value, dict):
                raise ValueError('expected a JSON object')
            return value
        except (OSError, ValueError) as exc:
            error('ALCHEMY_RESOURCE', f'{relative}: {exc}')
            return {}
    rules = read(RULES)
    errors.extend(check_rules(rules))
    sources = {}
    source_paths = set(SOURCE_FILES)
    for directory in ('alchemy', 'client/alchemy'):
        source_paths.update(path.relative_to(root) for path in (root / JAVA / directory).rglob('*.java'))
    for relative in sorted(source_paths):
        try:
            sources[relative] = (root / relative).read_text(encoding='utf-8')
        except (OSError, UnicodeError) as exc:
            error('ALCHEMY_SOURCE', f'{relative}: {exc}')
    errors.extend(check_sources(sources))
    icons = []
    for item in ITEMS:
        model = read(ASSETS / f'models/item/{item}.json')
        if item == 'alchemy_furnace':
            if model.get('parent') != 'myvillage:block/alchemy_furnace':
                error('ALCHEMY_ITEM_MODEL', 'furnace item must use its placed 3D model')
            continue
        texture = f'myvillage:item/{item}'
        if model.get('parent') != 'minecraft:item/generated' or model.get('textures') != {'layer0': texture}:
            error('ALCHEMY_ITEM_MODEL', f'{item}: requires its dedicated generated item texture')
        path = ASSETS / f'textures/item/{item}.png'
        checked.add(path)
        try:
            raw = (root / path).read_bytes()
            if raw[:8] != b'\x89PNG\r\n\x1a\n' or len(raw) < 33:
                raise ValueError('not a PNG')
            width, height = struct.unpack('>II', raw[16:24])
            if not 16 <= width <= 256 or width != height:
                raise ValueError('expected square item texture, 16..256 pixels')
            icons.append(hashlib.sha256(raw).digest())
        except (OSError, ValueError) as exc:
            error('ALCHEMY_TEXTURE', f'{path}: {exc}')
    if len(set(icons)) != 12:
        error('ALCHEMY_TEXTURE_DISTINCT', 'all twelve herb, formula and pill icons must be distinct')
    for suffix in ('', '_lit'):
        model = read(ASSETS / f'models/block/alchemy_furnace{suffix}.json')
        elements = model.get('elements')
        textures = model.get('textures', {})
        if not isinstance(elements, list) or len(elements) < 8 or model.get('parent'):
            error('ALCHEMY_FURNACE_MODEL', f'{suffix or "unlit"}: requires explicit body, legs, lid and handle geometry')
            continue
        if not isinstance(textures, dict) or not textures:
            error('ALCHEMY_TEXTURE', 'furnace model requires texture mappings')
            textures = {}
        for texture in textures.values():
            if not isinstance(texture, str) or not texture.startswith('myvillage:block/alchemy_furnace'):
                error('ALCHEMY_TEXTURE', f'invalid dedicated furnace texture {texture!r}')
            else:
                path = ASSETS / 'textures' / (texture.split(':')[1] + '.png')
                checked.add(path)
                try:
                    if not (root / path).read_bytes().startswith(b'\x89PNG\r\n\x1a\n'):
                        raise ValueError('not a PNG')
                except (OSError, ValueError) as exc:
                    error('ALCHEMY_TEXTURE', f'{path}: {exc}')
        geometry = set()
        for element in elements:
            try:
                start, end = element['from'], element['to']
                if (len(start) != 3 or len(end) != 3 or any(type(x) not in (int, float) or not math.isfinite(x) for x in [*start, *end])
                        or any(not 0 <= a < b <= 16 for a, b in zip(start, end))):
                    raise ValueError('invalid bounds or coordinates outside block')
                geometry.add((*start, *end))
                faces = element['faces']
                if not isinstance(faces, dict) or not faces:
                    raise ValueError('no visible faces')
                for face in faces.values():
                    ref = face.get('texture', '')
                    if not isinstance(ref, str) or not ref.startswith('#') or ref[1:] not in textures:
                        raise ValueError('unresolved face texture')
            except (TypeError, KeyError, ValueError, OverflowError, AttributeError) as exc:
                error('ALCHEMY_FURNACE_MODEL', f'{suffix}: {exc}')
        if len(geometry) < 8 or (0, 0, 0, 16, 16, 16) in geometry:
            error('ALCHEMY_FURNACE_MODEL', 'furnace must have distinct modeled parts without a full cube')
    states = read(ASSETS / 'blockstates/alchemy_furnace.json')
    variants = states.get('variants', {})
    expected = {f'facing={d},lit={lit}': {'model': f'myvillage:block/alchemy_furnace{"_lit" if lit == "true" else ""}', 'y': rotation}
                for d, rotation in (('north', 0), ('east', 90), ('south', 180), ('west', 270)) for lit in ('false', 'true')}
    if variants != expected or 'multipart' in states:
        error('ALCHEMY_BLOCKSTATE', 'all eight facing/lit variants must select matching geometry and rotation')
    for language in ('en_us', 'zh_cn'):
        lang = read(ASSETS / f'lang/{language}.json')
        for item in ITEMS:
            key = f'{"block" if item == "alchemy_furnace" else "item"}.myvillage.{item}'
            if not isinstance(lang.get(key), str) or not lang[key].strip():
                error('ALCHEMY_LANG', f'{language}: missing {key}')
    for item in ('alchemy_furnace', *HERBS, *(f'formula_{k}' for k in KINDS)):
        recipe = read(DATA / f'recipe/{item}.json')
        result = recipe.get('result', {})
        if result != {'id': f'myvillage:{item}', 'count': 1}:
            error('ALCHEMY_ACQUISITION', f'{item}: survival recipe must yield exactly one expected item')
        if item == 'alchemy_furnace':
            if recipe.get('type') != 'minecraft:crafting_shaped':
                error('ALCHEMY_ACQUISITION', 'furnace requires shaped copper, iron and furnace recipe')
            pattern, key = recipe.get('pattern', []), recipe.get('key', {})
            if (not isinstance(pattern, list) or not 1 <= len(pattern) <= 3
                    or not all(isinstance(row, str) and 1 <= len(row) <= 3 for row in pattern)
                    or len({len(row) for row in pattern}) != 1 or not isinstance(key, dict)
                    or set(key) != {char for row in pattern for char in row if char != ' '}):
                error('ALCHEMY_ACQUISITION', 'malformed furnace crafting recipe')
                continue
            ingredients = [key.get(char) for row in pattern for char in row if char != ' ']
            required = {'minecraft:copper_ingot', 'minecraft:iron_ingot', 'minecraft:furnace'}
        else:
            if recipe.get('type') != 'minecraft:crafting_shapeless':
                error('ALCHEMY_ACQUISITION', f'{item}: expected shapeless recipe')
            ingredients = recipe.get('ingredients', [])
            required = ({FUEL, f'minecraft:{PLANTS[HERBS.index(item)]}'} if item in HERBS
                        else {FUEL, 'minecraft:paper', *(f'myvillage:{h}' for h in PAIRS[item.removeprefix('formula_')])})
        actual = {i['item'] if set(i) == {'item'} and isinstance(i['item'], str) else
                  '#' + i['tag'] if set(i) == {'tag'} and isinstance(i['tag'], str) else None
                  for i in ingredients if isinstance(i, dict)} if isinstance(ingredients, list) else set()
        if actual != required or (item != 'alchemy_furnace' and (not isinstance(ingredients, list) or len(ingredients) != len(required))):
            error('ALCHEMY_ACQUISITION', f'{item}: wrong or duplicate survival ingredients')
    loot = read(DATA / 'loot_table/blocks/alchemy_furnace.json')
    pools = loot.get('pools', [])
    if (loot.get('type') != 'minecraft:block' or not isinstance(pools, list) or len(pools) != 1
            or not isinstance(pools[0], dict) or pools[0].get('rolls') != 1 or pools[0].get('bonus_rolls', 0) != 0
            or pools[0].get('entries') != [{'type': 'minecraft:item', 'name': 'myvillage:alchemy_furnace'}]
            or pools[0].get('conditions', []) not in ([], [{'condition': 'minecraft:survives_explosion'}])
            or pools[0].get('functions') or loot.get('functions')):
        error('ALCHEMY_LOOT', 'furnace block must drop exactly one furnace item without count modifiers')
    tag = read(RESOURCES / 'data/minecraft/tags/block/mineable/pickaxe.json')
    values = tag.get('values', [])
    if (not isinstance(values, list) or values.count('myvillage:alchemy_furnace') != 1
            or tag.get('replace', False) is not False):
        error('ALCHEMY_TAG', 'pickaxe tag must include furnace exactly once')
    if jar is not None:
        try:
            with zipfile.ZipFile(jar) as archive:
                for name in JAR_CLASSES:
                    if name not in archive.namelist():
                        error('ALCHEMY_JAR', f'{name}: missing runtime class')
                for relative in sorted(checked):
                    name = relative.relative_to(RESOURCES).as_posix()
                    try:
                        if archive.read(name) != (root / relative).read_bytes():
                            error('ALCHEMY_JAR', f'{name}: stale packaged resource')
                    except (KeyError, OSError) as exc:
                        error('ALCHEMY_JAR', f'{name}: {exc}')
        except (OSError, zipfile.BadZipFile) as exc:
            error('ALCHEMY_JAR', str(exc))
    return {'errors': errors, 'checked_resources': sorted(str(p) for p in checked),
            'checked_sources': sorted(str(p) for p in source_paths)}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=ROOT)
    parser.add_argument('--jar', type=Path)
    args = parser.parse_args(argv)
    report = validate(args.root, args.jar)
    for error in report['errors']:
        print(error)
    if not report['errors']:
        print(f'Alchemy foundation: PASS ({len(report["checked_resources"])} resources)')
    return int(bool(report['errors']))


if __name__ == '__main__':
    raise SystemExit(main())
