"""原worker实际保存副本分支；原ZIP只读，固定资源，单次flush/重启/最终停服。"""
from __future__ import annotations

import json
from pathlib import Path
import re
import shutil
import time

from tools.qianzhen_opening_evidence import audit, digest, fingerprint, fixture, source_snapshot
from tools.qianzhen_opening_extended_integration import start_recording, finalize_levels
from tools.qianzhen_opening_extended_saved import (
    extract_saved_fixture, materialize_saved_resources, SavedSceneFlow, SavedFixtureSession, SavedCheckpoint)


def install_frozen_original(spec, world, resources, hashes, *, root=None):
    """A: install original seven pinned resources; C: keep original embedded pack."""
    if spec['resources']['kind'] == 'embedded_datapack':
        return {'mode': 'original_embedded_unchanged', 'resource_sha256': hashes}
    pack = world / 'datapacks/qianzhen_frozen_original'
    pack.mkdir(parents=True, exist_ok=False)
    root = root or Path(__file__).resolve().parents[1]
    metadata = json.loads((root / 'src/main/resources/pack.mcmeta').read_text())
    metadata['pack']['description'] = '真实旧保存的原七资源冻结副本'
    (pack / 'pack.mcmeta').write_text(json.dumps(metadata, ensure_ascii=False))
    for name, sha in hashes.items():
        target = pack / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(resources / name, target)
        if digest(target) != sha:
            raise ValueError('原历史资源安装身份不符')
    return {'mode': 'original_jar_seven_resources_frozen', 'pack': pack.name, 'resource_sha256': hashes}


def execute_saved_worker(args, root, out, jar, snap, deadline):
    from tools.combat_capture import session as cs
    from tools.combat_capture.xgame import Game
    from tools.combat_capture.capture import stop_recording
    from tools.qianzhen.story_lifecycle import lifecycle_command
    from tools.qianzhen_opening_collect import OpeningCollector
    from tools.qianzhen_opening_run import write_json, verify_owned_session, single_flush, stop_owned, archive_world, split_stage_artifacts
    out.mkdir(parents=True, exist_ok=False)
    selected = args.scenarios
    trace = {'schema': 'qianzhen-opening-saved-trace-v1', 'run_id': args.run_id, 'status': 'running',
             'evidence_tier': 'native_saved_copy', 'selected': selected, 'completed': [], 'captured': [],
             'failed': [], 'unrun': selected.copy(), 'stages': [], 'player_acceptance': 'not_verified',
             'not_verified': fixture()['not_verified'], 'termination': None}
    state, collector, checkpoint, failure, record = None, None, None, None, None
    clips = []
    world = root / 'run-acceptance/combat_capture_world'
    request_path = root.parent / 'saved-request.json'
    def record_start(game, generation):
        nonlocal record
        if record is not None:
            raise RuntimeError('保存主录像未收尾')
        record = start_recording(game, out / ('primary-' + str(generation) + '.mp4'), deadline)
        collector.capture('saved-start-' + str(generation))
    def record_stop():
        nonlocal record
        if record is None:
            return
        process, log, path = record
        record = None
        try:
            code = stop_recording(process, timeout=10)
            if code != 0:
                raise RuntimeError('保存录像非零退出: ' + str(code))
            clips.append({'path': path.name, 'sha256': digest(path)})
        finally:
            if process.poll() is None:
                process.kill()
                process.wait(timeout=3)
            log.close()
    row, flow = None, None
    try:
        spec = json.loads(Path(args.saved_fixture).read_text())
        if selected != [spec['kind']] or not re.fullmatch(r'[A-Za-z0-9_]{1,16}', spec['player_name']):
            raise ValueError('保存manifest类型/真实玩家名与选择不符')
        import uuid
        # Offline-mode login identity is deterministic; never replace playerdata UUID.
        import hashlib
        raw = bytearray(hashlib.md5(('OfflinePlayer:' + spec['player_name']).encode()).digest())
        raw[6], raw[8] = (raw[6] & 15) | 48, (raw[8] & 63) | 128
        if str(uuid.UUID(bytes=bytes(raw))) != spec['player_uuid']:
            raise ValueError('保存玩家名与原UUID不匹配，禁止重造身份')
        copied = extract_saved_fixture(spec, world)
        resources = out / 'frozen-resources'
        hashes = materialize_saved_resources(spec, world, resources)
        trace['resource_install'] = install_frozen_original(spec, world, resources, hashes, root=root)
        trace['fixture'] = copied
        trace['fixture_manifest'] = spec
        trace['world'] = {'fresh': False, 'preserved_world': True, 'isolation_root': str(root.parent), 'directory': str(world)}
        request = {'schema': 'qianzhen-saved-request-v1', 'run_id': args.run_id, 'commit': args.commit,
                   'source_fingerprint': snap['fingerprint'], 'fixture_sha256': digest(Path(args.saved_fixture)),
                   'original_archive_sha256': spec['archive_sha256'], 'resource_sha256': hashes,
                   'jar_sha256': digest(jar), 'player_name': spec['player_name'], 'player_uuid': spec['player_uuid']}
        write_json(request_path, request, exclusive=True)
        shutil.copy2(request_path, out / 'request.json')
        trace['source'] = {**request, 'request_sha256': digest(request_path)}
        trace['attempted'] = selected.copy()
        cfg = cs.SessionConfig(session_id=args.run_id, username=spec['player_name'], reuse_world=True,
                               lock='/content/.mc-heavy.lock', lock_timeout=20, natural_terrain=True, natural_monsters=True,
                               world_seed=fixture()['seed'], language='zh_cn', settle=2, server_timeout=600,
                               client_timeout=600, ingame_timeout=90, gradle_jvmargs='-Xmx2g')
        state = cs.start(cfg, wait_timeout=min(1350, max(1, deadline - time.monotonic())), log=lambda _: None)
        verify_owned_session(state, args.run_id, root)
        game = Game(state['display'], Path(state['client_log']), size=(960, 540))
        collector = OpeningCollector(game, lambda command, timeout: lifecycle_command(state, command, timeout), spec['player_name'], out / 'raw')
        record_start(game, 0)
        flow = SavedSceneFlow(collector, root, world, deadline, resources, options_path=root / 'run-combat-capture/client/options.txt')
        flow.c.input.gate.ready('saved_actual_login', allow_escape=False)
        row = {'id': spec['kind'], 'run_id': args.run_id, 'source_commit': args.commit,
               'request_sha256': digest(request_path), 'status': 'running', 'checks': []}
        trace['stages'].append(row)
        checkpoint = SavedCheckpoint(state, cfg, out, args.run_id, request_path, record_stop=record_stop, record_start=record_start)
        session = SavedFixtureSession(spec, copied, checkpoint)
        getattr(session, spec['kind'])(flow)
        if record is None or record[0].poll() is not None:
            raise RuntimeError('保存副本录像在场景完成前已退出')
        expected = next(r['checks'] for r in fixture()['stages'] if r['id'] == spec['kind'])
        if {r['id'] for r in flow.checks} != set(expected) or len(flow.checks) != len(expected):
            raise AssertionError('保存适配断言覆盖缺失/重复')
        if digest(Path(spec['archive'])) != spec['archive_sha256'] or source_snapshot(root) != snap:
            raise ValueError('保存适配期间原件/固定源发生漂移')
        flow.capture('saved-finished')
        row.update(status='captured', checks=flow.checks)
        trace['status'] = 'captured_not_accepted'
    except BaseException as error:
        failure = error
        trace.update(status='failed', failure=repr(error))
        if trace.get('attempted'):
            trace['failed'] = selected.copy()
        if row is not None:
            row.update(status='failed', failure=repr(error), checks=flow.checks if flow else [])
        if collector:
            try:
                trace['failure_frame'] = collector.capture('saved-failure')
            except BaseException as capture_error:
                trace['capture_failure'] = repr(capture_error)
    finally:
        # Durable partial trace before any optional archive/audit operation.
        write_json(out / 'trace.json', trace)
        operations = [('recording', record_stop)]
        if state and not (checkpoint and checkpoint.called):
            operations.append(('flush', lambda: single_flush(state, root, out)))
        operations.append(('termination', lambda: stop_owned(root, args.run_id)))
        for name, operation in operations:
            try:
                trace[name] = operation()
            except BaseException as error:
                trace[name] = {'status': 'failed', 'failure': repr(error)}
                failure = failure or error
        if trace.get('termination', {}).get('status') == 'terminated' and world.exists():
            try:
                trace['saved_world'] = archive_world(world, out)
            except BaseException as error:
                trace['archive_failure'] = repr(error)
                failure = failure or error
        if collector:
            try:
                collector.stream.flush()
                collector.readbacks.flush()
                if row is not None:
                    actions = [json.loads(l) for l in (collector.out / 'actions.jsonl').read_text().splitlines()]
                    reads = [json.loads(l) for l in (collector.out / 'readbacks.jsonl').read_text().splitlines()]
                    screens = flow.screens if flow else []
                    frame = collector.out / screens[-1]['path'] if screens else None
                    split_stage_artifacts(out, row, actions, reads, None, frame)
                    if clips:
                        row['artifacts']['video'] = clips[-1]
                        row['video_segments'] = clips
            except BaseException as error:
                trace['artifact_failure'] = repr(error)
                failure = failure or error
            finally:
                collector.stream.close()
                collector.readbacks.close()
        logs = out / 'process-logs'
        logs.mkdir(exist_ok=True)
        for folder in (root / 'run-combat-capture/logs', root / 'run-combat-capture/client/logs', root / 'run-acceptance/logs'):
            for path in folder.glob('*.log'):
                try:
                    shutil.copy2(path, logs / (folder.parent.name + '-' + path.name))
                except OSError as error:
                    trace.setdefault('log_failures', []).append(repr(error))
                    failure = failure or error
        trace['primary_video_segments'] = clips
        trace = json.loads(json.dumps(trace, ensure_ascii=False))
        finalize_levels(trace)
        if failure:
            trace['status'] = 'failed'
        write_json(out / 'trace.json', trace)
        # The unchanged core is fresh-world-only. Keep its honest rejection;
        # do not relabel a historical copy as fresh to turn audit green.
        try:
            checked = audit(trace, out)
        except BaseException as error:
            checked = {'status': 'invalid', 'errors': [repr(error)]}
        write_json(out / 'core-audit.json', checked)
        write_json(out / 'audit.json', {'status': 'captured_saved_copy_requires_raw_review' if not failure else 'failed',
                   'completed': [], 'captured': trace['captured'], 'failed': trace['failed'], 'unrun': trace['unrun'],
                   'fresh_core_applicable': False, 'core_result': 'core-audit.json',
                   'native_verdict': 'requires_raw_artifact_review', 'historical_upgrade_proven': False, 'historical_source':
                   trace.get('fixture_manifest', {}).get('provenance', {}).get('kind') == 'real_historical_saved_world'})
    if failure:
        raise failure
    return trace
