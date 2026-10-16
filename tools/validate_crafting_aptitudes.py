#!/usr/bin/env python3
"""Validate crafting aptitude data, saved authority, owning snapshot and optional jar closure."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))
from tools.validate_cultivation_initiation import _strip_java_comments, _balanced_content, _top_level_parts, _method_body

JAVA = Path('src/main/java/com/example/myvillage')
RESOURCES = Path('src/main/resources')
RULES = RESOURCES / 'data/myvillage/crafting/rules.json'
LANGS = tuple(RESOURCES / f'assets/myvillage/lang/{language}.json' for language in ('en_us', 'zh_cn'))
SOURCE_FILES = (
    JAVA / 'cultivation/crafting/CraftingAptitudes.java',
    JAVA / 'cultivation/crafting/CraftingRules.java',
    JAVA / 'cultivation/crafting/CraftingGenerator.java',
    JAVA / 'cultivation/crafting/CraftingCommands.java',
    JAVA / 'cultivation/CultivationAttachments.java',
    JAVA / 'cultivation/CultivationService.java',
    JAVA / 'cultivation/CultivationProfile.java',
    JAVA / 'cultivation/network/CultivationSnapshotPayload.java',
    JAVA / 'cultivation/network/CultivationPayloads.java',
    JAVA / 'client/cultivation/ClientCultivationState.java',
    JAVA / 'network/ModPayloads.java',
)
JAR_CLASSES = tuple(f'com/example/myvillage/{name}.class' for name in (
    'cultivation/crafting/CraftingAptitudes', 'cultivation/crafting/CraftingRules',
    'cultivation/crafting/CraftingGenerator', 'cultivation/crafting/CraftingCommands',
    'client/cultivation/panel/CraftingPage', 'cultivation/network/CultivationSnapshotPayload',
    'cultivation/CultivationAttachments', 'cultivation/CultivationService',
))


def load_json(raw):
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
    errors = []
    def error(code, detail):
        errors.append(f'{code}: {detail}')
    def keys(value, fields, where):
        if not isinstance(value, dict) or set(value) != set(fields):
            error('CRAFTING_SCHEMA', f'{where}: expected keys {sorted(fields)}')
            return False
        return True
    def score(value):
        return type(value) is int and 0 <= value <= 100
    if not keys(data, ('version', 'initial_roll', 'quality_thresholds'), 'rules'):
        return errors
    if type(data['version']) is not int or data['version'] != 1:
        error('CRAFTING_VERSION', 'rules require integer version 1')
    initial = data['initial_roll']
    if keys(initial, ('min', 'max'), 'initial_roll'):
        if not all(score(initial[k]) for k in ('min', 'max')) or initial['min'] > initial['max']:
            error('CRAFTING_ROLL', 'initial roll endpoints must be ordered integers within 0..100')
    thresholds = data['quality_thresholds']
    if not isinstance(thresholds, list) or len(thresholds) != 4:
        error('CRAFTING_QUALITY', 'exactly four ordered product-quality thresholds are required')
        return errors
    previous = -1
    for expected, threshold in enumerate(thresholds, 1):
        if not keys(threshold, ('quality', 'minimum'), f'quality_thresholds[{expected - 1}]'):
            continue
        if type(threshold['quality']) is not int or threshold['quality'] != expected:
            error('CRAFTING_QUALITY', 'product qualities must be ordered 1..4 with no missing or duplicate quality')
        minimum = threshold['minimum']
        if not score(minimum) or minimum <= previous or (expected == 1 and minimum != 0):
            error('CRAFTING_THRESHOLD', 'quality minima must start at 0 and strictly increase within 0..100')
        if score(minimum):
            previous = minimum
    return errors


def validate(root: Path = ROOT, jar: Path | None = None) -> dict:
    root = Path(root)
    errors = []
    checked = set()
    def error(code, detail):
        errors.append(f'{code}: {detail}')
    def need(ok, code, detail):
        if not ok:
            error(code, detail)
    def read(relative):
        checked.add(relative)
        try:
            return (root / relative).read_text(encoding='utf-8')
        except (OSError, UnicodeError) as exc:
            error('CRAFTING_RESOURCE', f'{relative}: {exc}')
            return ''
    try:
        data = load_json(read(RULES))
        errors.extend(check_rules(data))
    except ValueError as exc:
        error('CRAFTING_RESOURCE', f'{RULES}: {exc}')
    for language in LANGS:
        try:
            values = load_json(read(language))
            for quality in ('pending', 'lower', 'middle', 'upper', 'perfect'):
                key = f'crafting.myvillage.quality.{quality}'
                need(isinstance(values, dict) and isinstance(values.get(key), str) and bool(values[key].strip()),
                     'CRAFTING_LANG', f'{language}: missing product-quality name {key}')
        except ValueError as exc:
            error('CRAFTING_RESOURCE', f'{language}: {exc}')
    sources = {path: _strip_java_comments(read(path)) for path in SOURCE_FILES}
    state = sources[JAVA / 'cultivation/crafting/CraftingAptitudes.java']
    need(bool(re.search(
        r'record\s+CraftingAptitudes\s*\(\s*int\s+version\s*,\s*boolean\s+initialized\s*,\s*int\s+alchemy\s*,\s*int\s+forging\s*\)', state
    )), 'CRAFTING_IMMUTABLE', 'saved state must be the independent immutable version/initialized/alchemy/forging record')
    attachment = sources[JAVA / 'cultivation/CultivationAttachments.java']
    need(bool(re.search(
        r'CRAFTING\s*=.*?register\("crafting_aptitudes".*?serialize\([^;]*CraftingAptitudes\.CODEC\)\s*\.copyOnDeath\(\)',
        attachment, re.DOTALL,
    )), 'CRAFTING_ATTACHMENT', 'separate crafting_aptitudes attachment requires codec and copyOnDeath')
    profile = sources[JAVA / 'cultivation/CultivationProfile.java']
    need(bool(re.search(r'CURRENT_SCHEMA_VERSION\s*=\s*4\b', profile)) and 'CraftingAptitudes' not in profile,
         'CRAFTING_PROFILE', 'crafting attachment must leave CultivationProfile v4 unchanged')
    service = sources[JAVA / 'cultivation/CultivationService.java']
    write_pattern = r'\.setData\s*\(\s*(?:[\w.]*CultivationAttachments\s*\.\s*)?CRAFTING\s*\.\s*get\s*\(\s*\)'
    need(bool(re.search(write_pattern, service)), 'CRAFTING_WRITER', 'CultivationService must own the crafting attachment installer')
    initialize = _method_body(service, 'initializeCraftingAptitudes') or ''
    need(bool(re.search(
        r'if\s*\(\s*current\.initialized\(\)\s*\|\|\s*getProfile\(player\)\.spiritualRoot\(\)\.isEmpty\(\)\s*\)\s*return\s*;',
        initialize,
    )), 'CRAFTING_INITIALIZATION', 'initialize only once, and leave unawakened saves pending')
    need('CraftingGenerator.ensure' in initialize and 'player.getUUID()' in initialize
         and 'getSeed()' in initialize and 'CraftingRules.get()' in initialize,
         'CRAFTING_INITIALIZATION', 'generation must use the world seed, player UUID and bundled rules')
    sync_declaration = re.search(r'private\s+static\s+void\s+syncToClient\(\s*ServerPlayer\s+player\s*,\s*CultivationProfile\s+profile\s*\)\s*\{', service)
    sync = _balanced_content(service, sync_declaration.end() - 1) if sync_declaration else None
    sync_body = sync[0] if sync else ''
    preparation = sync_body.find('initializeCraftingAptitudes(player)')
    send = sync_body.find('new CultivationSnapshotPayload')
    need(preparation >= 0 and send > preparation, 'CRAFTING_INITIALIZATION', 'prepare saved aptitudes before the owning snapshot')
    for path in sorted((root / JAVA).rglob('*.java')):
        relative = path.relative_to(root)
        if relative in sources:
            source = sources[relative]
        else:
            try:
                source = _strip_java_comments(path.read_text(encoding='utf-8'))
            except (OSError, UnicodeError) as exc:
                error('CRAFTING_RESOURCE', f'{relative}: {exc}')
                continue
        if relative != JAVA / 'cultivation/CultivationService.java' and re.search(write_pattern, source):
            error('CRAFTING_WRITER', f'{relative}: crafting replacement belongs only to CultivationService')
        if 'Crafting' in source and re.search(r'PlayerEvent\.Clone|\bClone\s+event\b', source):
            error('CRAFTING_CLONE', f'{relative}: attachment copyOnDeath owns copying, not a Clone hook')
        if 'client' in relative.parts and re.search(r'\b(?:CraftingRules|CraftingGenerator)\b', source):
            error('CRAFTING_CLIENT_AUTHORITY', f'{relative}: clients must read server-computed scores and quality caps')
        if relative != JAVA / 'cultivation/network/CultivationSnapshotPayload.java' and 'implements CustomPacketPayload' in source:
            if re.search(r'\bCraftingAptitudes\b|\b(?:alchemy|forging)QualityCap\b', source):
                error('CRAFTING_PAYLOAD', f'{relative}: crafting state belongs only in the existing owning snapshot')
        for match in re.finditer(r'new\s+CultivationSnapshotPayload\s*\(', source):
            region = _balanced_content(source, match.end() - 1, '(', ')')
            if region is None or len(_top_level_parts(region[0])) != 6:
                error('CRAFTING_SNAPSHOT_SENDER', f'{relative}: production senders must include actual state and both quality caps')
            else:
                args = _top_level_parts(region[0])
                state_arg = args[3]
                actual = bool(re.fullmatch(r'[A-Za-z_$][\w$]*', state_arg)) and bool(re.search(
                    rf'\b{re.escape(state_arg)}\s*=\s*craftingAptitudes\(player\)', source
                ))
                for profession, arg in zip(('ALCHEMY', 'FORGING'), args[4:]):
                    actual = actual and bool(re.fullmatch(
                        rf'{re.escape(state_arg)}\.qualityCap\(\s*CraftingAptitudes\.Profession\.{profession}\s*,\s*rules\s*\)', arg
                    ))
                if not actual:
                    error('CRAFTING_SNAPSHOT_SENDER', f'{relative}: snapshot must send the saved state and server-derived profession caps')
    generator = sources[JAVA / 'cultivation/crafting/CraftingGenerator.java']
    need(not re.search(r'\b(?:System\.(?:currentTimeMillis|nanoTime)|Instant\.now|ThreadLocalRandom)\b|\.getRandom\s*\(|\.random\b', generator),
         'CRAFTING_RNG', 'generation must not use time or the world/root shared random stream')
    need(not re.search(r'import\s+(?:net\.minecraft|net\.neoforged|com\.mojang)\.', generator),
         'CRAFTING_RNG', 'deterministic generation must remain independent of game state and root RNG')
    domains = {name: int(value, 16) for name, value in re.findall(
        r'\b(ALCHEMY_DOMAIN|FORGING_DOMAIN)\s*=\s*(0x[0-9a-fA-F]+)L\b', generator
    )}
    need(len(domains) == 2 and len(set(domains.values())) == 2,
         'CRAFTING_RNG', 'alchemy and forging require distinct stable generation domains')
    need(not re.search(r'new\s+Random\s*\(\s*\)', generator),
         'CRAFTING_RNG', 'generation must seed its own random instance explicitly')
    payload = sources[JAVA / 'cultivation/network/CultivationSnapshotPayload.java']
    need(bool(re.search(r'CraftingAptitudes\s+crafting\s*,\s*int\s+alchemyQualityCap\s*,\s*int\s+forgingQualityCap', payload)),
         'CRAFTING_SNAPSHOT', 'owning cultivation snapshot must carry state and both server-derived quality caps')
    for field in ('crafting', 'alchemyQualityCap', 'forgingQualityCap'):
        need(f'CultivationSnapshotPayload::{field}' in payload,
             'CRAFTING_SNAPSHOT_CODEC', f'snapshot codec must encode {field}')
    need('CraftingAptitudes.CODEC' in payload, 'CRAFTING_SNAPSHOT_CODEC', 'snapshot must use the bounded immutable state codec')
    protocol = sources[JAVA / 'network/ModPayloads.java']
    need(bool(re.search(r'PROTOCOL_VERSION\s*=\s*"28"', protocol)), 'CRAFTING_PROTOCOL', 'crafting snapshot wire format requires protocol 28')
    network = sources[JAVA / 'cultivation/network/CultivationPayloads.java']
    need(bool(re.search(r'playToClient\s*\(\s*CultivationSnapshotPayload\.TYPE', network))
         and not re.search(r'playToServer\s*\(\s*CultivationSnapshotPayload\.TYPE', network),
         'CRAFTING_PAYLOAD', 'the cultivation snapshot must stay clientbound only')
    client = sources[JAVA / 'client/cultivation/ClientCultivationState.java']
    need(not re.search(r'AtomicReference\s*<[^;]*CraftingAptitudes', client),
         'CRAFTING_CLIENT_CACHE', 'crafting must use the existing snapshot cache, never a separate crafting cache')
    need(not re.search(r'\b(?:setData|sendToServer|PacketDistributor)\b', client),
         'CRAFTING_CLIENT_AUTHORITY', 'cultivation snapshot cache must remain read-only')
    if jar is not None:
        try:
            with zipfile.ZipFile(jar) as archive:
                names = set(archive.namelist())
                for name in JAR_CLASSES:
                    need(name in names, 'CRAFTING_JAR', f'{name}: missing runtime class')
                for relative in (RULES, *LANGS):
                    name = relative.relative_to(RESOURCES).as_posix()
                    try:
                        need(archive.read(name) == (root / relative).read_bytes(), 'CRAFTING_JAR', f'{name}: stale bundled resource')
                    except (KeyError, OSError) as exc:
                        error('CRAFTING_JAR', f'{name}: {exc}')
        except (OSError, zipfile.BadZipFile) as exc:
            error('CRAFTING_JAR', str(exc))
    return {'errors': errors, 'checked_files': len(checked)}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=ROOT)
    parser.add_argument('--jar', type=Path)
    args = parser.parse_args(argv)
    report = validate(args.root, args.jar)
    for error in report['errors']:
        print(error)
    if not report['errors']:
        print(f'Crafting aptitudes: PASS ({report["checked_files"]} required files)')
    return int(bool(report['errors']))


if __name__ == '__main__':
    raise SystemExit(main())
