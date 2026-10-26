#!/usr/bin/env python3
"""Run the opt-in NPC garden dedicated-server subscriber twice in one new world.

This launcher owns only its child Gradle/server process group. It never edits saved NBT,
reuses a previous world, stops a Colab session, or calls a player's mapped controls.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
INITIAL = ('fixture', 'source_gather', 'source_delivery', 'native_plant', 'native_tend', 'save_boundary')
RESTART = ('reload_identity', 'foreign_change_rejected', 'mob_griefing_rejected',
           'observer_withdrawal', 'native_harvest', 'no_duplicate')


def validate_phase(data: dict, phase: str, source: str) -> list[str]:
    """Preserve selected/completed/unrun verbatim; report malformed evidence as a failure."""
    errors = []
    selected = list(INITIAL if phase == 'initial' else RESTART)
    for key, expected in (('phase', phase), ('source', source), ('selected', selected)):
        if data.get(key) != expected:
            errors.append(f'{phase}.{key}: expected {expected!r}, got {data.get(key)!r}')
    completed, unrun = data.get('completed'), data.get('unrun')
    if not isinstance(completed, list) or not isinstance(unrun, list):
        errors.append(f'{phase}: missing completed/unrun lists')
    elif completed + unrun != selected:
        errors.append(f'{phase}: completed+unrun does not equal selected in order')
    checks = data.get('checks')
    if not isinstance(checks, list) or not checks:
        errors.append(f'{phase}: no checks')
    elif any(not isinstance(check, dict) or check.get('pass') is not True for check in checks):
        errors.append(f'{phase}: one or more checks failed')
    if data.get('pass') is not True:
        errors.append(f'{phase}: pass is not true; failure={data.get("failure")}')
    return errors


def report_for_missing(phase: str, source: str, reason: str) -> dict:
    return {'kind': 'npc_garden_native_server', 'phase': phase, 'source': source,
            'selected': list(INITIAL if phase == 'initial' else RESTART),
            'completed': [], 'unrun': list(INITIAL if phase == 'initial' else RESTART),
            'checks': [{'name': 'server_report_written', 'pass': False, 'detail': reason}],
            'pass': False, 'failure': reason}


def merge(initial: dict, restart: dict, source: str, world: str) -> dict:
    errors = validate_phase(initial, 'initial', source) + validate_phase(restart, 'restart', source)
    completed = initial['completed'] + restart['completed']
    selected = list(INITIAL + RESTART)
    unrun = [case for case in selected if case not in completed]
    return {'kind': 'npc_garden_native_server', 'source': source, 'world': world,
            'selected': selected, 'completed': completed, 'unrun': unrun,
            'checks': initial['checks'] + restart['checks'], 'pass': not errors,
            'errors': errors, 'phases': {'initial': initial, 'restart': restart},
            'player_input': 'not_verified; FakePlayer only provides near-field presence',
            'calendar_input': 'explicit fixture ticks via CultivationCalendarSavedData'}


def set_property(text: str, key: str, value: str) -> str:
    lines = text.splitlines()
    pattern = re.compile(rf'^\s*{re.escape(key)}\s*=')
    lines = [line for line in lines if not pattern.match(line)]
    lines.append(f'{key}={value}')
    return '\n'.join(lines) + '\n'


def verify_source(root: Path, source: str, archive: Path | None) -> None:
    if archive is not None:
        digest = hashlib.sha256(archive.read_bytes()).hexdigest()
        if source not in (digest, f'sha256:{digest}'):
            raise ValueError(f'--source does not match --archive SHA-256 ({digest})')
        return
    if not re.fullmatch(r'[0-9a-f]{40}', source):
        raise ValueError('provide --archive for SHA-256 source, or use a full 40-character Git commit')
    head = subprocess.run(['git', 'rev-parse', '--verify', 'HEAD'], cwd=root,
                          text=True, capture_output=True, check=True).stdout.strip()
    if head != source:
        raise ValueError(f'--source {source} does not match checkout HEAD {head}')
    dirty = subprocess.run(['git', 'status', '--porcelain', '--untracked-files=all'], cwd=root,
                           text=True, capture_output=True, check=True).stdout.strip()
    if dirty:
        raise ValueError('fixed Git source checkout has uncommitted or untracked files')


def stop_group(process: subprocess.Popen, reason: str, log) -> None:
    if process.poll() is not None:
        return
    log.write(f'\n[runner] {reason}; terminating only process group {process.pid}\n')
    log.flush()
    os.killpg(process.pid, signal.SIGTERM)
    try:
        process.wait(timeout=20)
    except subprocess.TimeoutExpired:
        os.killpg(process.pid, signal.SIGKILL)
        process.wait(timeout=10)


def run_phase(root: Path, out: Path, source: str, phase: str, timeout: int, reuse_resources: bool = False) -> dict:
    env = dict(os.environ)
    env.update(MYVILLAGE_NPC_GARDEN_EVIDENCE='1', MYVILLAGE_NPC_GARDEN_PHASE=phase,
               MYVILLAGE_NPC_GARDEN_OUT=str(out), MYVILLAGE_NPC_GARDEN_SOURCE=source)
    command = ['./gradlew', '--no-daemon', '--console=plain', '--max-workers=2',
               'runServer', '-PnpcGardenEvidence']
    if reuse_resources:
        for task in ('generateAllStructures', 'generateWorldPlanAssets', 'generateBayTerracesAssets',
                     'generateQianzhenStoryAssets', 'generateQianzhenRegionAssets'):
            command.extend(('-x', task))
    target = out / f'{phase}.json'
    log_file = out / f'{phase}-server.log'
    with log_file.open('w', encoding='utf-8') as log:
        log.write(f'[runner] phase={phase} source={source} command={command!r}\n')
        log.flush()
        process = subprocess.Popen(command, cwd=root, env=env, stdout=log,
                                   stderr=subprocess.STDOUT, start_new_session=True)
        try:
            code = process.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            stop_group(process, f'timeout after {timeout}s', log)
            code = process.returncode
        log.write(f'\n[runner] exit={code} report_exists={target.exists()}\n')
    if not target.exists():
        data = report_for_missing(phase, source, f'server exited {code} before native report; see {log_file}')
        target.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding='utf-8')
        return data
    try:
        data = json.loads(target.read_text(encoding='utf-8'))
    except (OSError, json.JSONDecodeError) as error:
        data = report_for_missing(phase, source, f'malformed native report: {error}; see {log_file}')
        (out / f'{phase}-runner-failure.json').write_text(json.dumps(data, indent=2), encoding='utf-8')
    if code != 0:
        data.setdefault('runner_failure', f'server process exit {code}; see {log_file}')
        data['pass'] = False
    return data


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=ROOT)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--source', required=True, help='fixed commit or exact archive identity')
    parser.add_argument('--archive', type=Path, help='optional fixed source archive; its SHA-256 is verified')
    parser.add_argument('--world', required=True, help='unique new level-name; never reuse a world')
    parser.add_argument('--timeout', type=int, default=600, help='seconds per server phase')
    parser.add_argument('--collect-only', action='store_true', help='merge existing phase JSON without starting Gradle')
    parser.add_argument('--reuse-generated-resources', action='store_true',
                        help='use a completed matching build; caller must record generator input/output hash audit')
    args = parser.parse_args(argv)
    root, out = args.root.resolve(), args.out.resolve()
    if not re.fullmatch(r'[a-zA-Z0-9_-]{4,80}', args.world):
        parser.error('--world must be a simple unique name')
    if args.timeout < 30:
        parser.error('--timeout must be at least 30 seconds')
    if not args.collect_only:
        try:
            verify_source(root, args.source, args.archive)
        except (OSError, ValueError, subprocess.CalledProcessError) as error:
            parser.error(str(error))
    if not args.collect_only and out.exists() and any(out.iterdir()):
        parser.error(f'output directory is not empty; refusing to mix attempts: {out}')
    out.mkdir(parents=True, exist_ok=True)
    if args.collect_only:
        initial = json.loads((out / 'initial.json').read_text(encoding='utf-8'))
        restart = json.loads((out / 'restart.json').read_text(encoding='utf-8'))
    else:
        run_dir = root / 'run'
        run_dir.mkdir(exist_ok=True)
        world_dir = run_dir / args.world
        if world_dir.exists():
            parser.error(f'world already exists; refusing to overwrite/reuse: {world_dir}')
        for phase in ('initial', 'restart'):
            if (out / f'{phase}.json').exists():
                parser.error(f'phase report already exists; choose a new --out: {out / (phase + ".json")}')
        properties = run_dir / 'server.properties'
        eula = run_dir / 'eula.txt'
        tier_config = run_dir / 'config' / 'myvillage-world_sim-server.toml'
        original_properties = properties.read_bytes() if properties.exists() else None
        original_eula = eula.read_bytes() if eula.exists() else None
        original_tier = tier_config.read_bytes() if tier_config.exists() else None
        try:
            source_properties = original_properties.decode('utf-8') if original_properties else ''
            fixture_properties = {'level-name': args.world, 'level-seed': '20261026',
                                  'level-type': 'minecraft\\:flat', 'generate-structures': 'false',
                                  'generator-settings': json.dumps({'layers': [{'block': 'minecraft:bedrock', 'height': 1},
                                      {'block': 'minecraft:dirt', 'height': 2}, {'block': 'minecraft:grass_block', 'height': 1}],
                                      'biome': 'minecraft:plains'}),
                                  'gamemode': 'creative', 'force-gamemode': 'true', 'online-mode': 'false',
                                  'spawn-protection': '0', 'view-distance': '3', 'simulation-distance': '3',
                                  'max-tick-time': '-1', 'difficulty': 'peaceful'}
            for key, value in fixture_properties.items():
                source_properties = set_property(source_properties, key, value)
            properties.write_text(source_properties, encoding='utf-8')
            eula.write_text('eula=true\n', encoding='utf-8')
            tier_config.parent.mkdir(parents=True, exist_ok=True)
            tier_config.write_text('[world_sim]\ntier = "large"\n', encoding='utf-8')
            initial = run_phase(root, out, args.source, 'initial', args.timeout, args.reuse_generated_resources)
            if initial.get('pass') and not validate_phase(initial, 'initial', args.source) and world_dir.exists():
                restart = run_phase(root, out, args.source, 'restart', args.timeout, args.reuse_generated_resources)
            else:
                restart = report_for_missing('restart', args.source,
                                             'not run: initial phase failed or world missing')
                (out / 'restart.json').write_text(json.dumps(restart, ensure_ascii=False, indent=2), encoding='utf-8')
        finally:
            if original_properties is None:
                properties.unlink(missing_ok=True)
            else:
                properties.write_bytes(original_properties)
            if original_eula is None:
                eula.unlink(missing_ok=True)
            else:
                eula.write_bytes(original_eula)
            if original_tier is None:
                tier_config.unlink(missing_ok=True)
            else:
                tier_config.write_bytes(original_tier)
    combined = merge(initial, restart, args.source, args.world)
    (out / 'summary.json').write_text(json.dumps(combined, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({'pass': combined['pass'], 'source': args.source, 'world': args.world,
                      'selected': combined['selected'], 'completed': combined['completed'],
                      'unrun': combined['unrun'], 'errors': combined['errors'], 'out': str(out)}, ensure_ascii=False))
    return 0 if combined['pass'] else 1


if __name__ == '__main__':
    sys.exit(main())
