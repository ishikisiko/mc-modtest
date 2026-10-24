#!/usr/bin/env python3
"""扩展原生可执行入口：接入本run独立新世界，执行、停服、审计并保留失败。

只在显式Colab运行；不创建VM、不构建、不接管其他run。父driver完成其原有
固定源/build/candidate/自然首次启动后，可写attachment调用本入口，或直接
调用execute_attachment。保存副本分支见extended_saved，不能混入fresh入口。
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import threading
import time

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT / 'tools'))

from tools.qianzhen_opening_evidence import audit, digest, fingerprint, fixture
from tools.qianzhen_opening_flow import OpeningFlow, selected_scenarios
from tools.qianzhen_opening_extended import ExtendedSession
from tools.qianzhen_opening_extended_fixtures import NativeFixtureProbe


def validate_execution_manifest(spec, root, out):
    if spec.get('schema') != 'qianzhen-opening-extended-attachment-v1':
        raise ValueError('扩展attachment schema不符')
    selected = selected_scenarios(spec['scenarios'])
    if set(selected) & {'existing_awakened', 'legacy_accepted'} and not spec.get('build_awakened_fixture'):
        raise ValueError('保存副本不可混入fresh入口；调用SavedFixtureSession')
    if spec.get('build_awakened_fixture') and selected != ['existing_awakened']:
        raise ValueError('B builder必须单独选择existing_awakened')
    if type(spec.get('timeout')) not in (int, float) or not 300 <= spec['timeout'] <= 7200:
        raise ValueError('扩展整体预算须300..7200秒')
    run_root = root.resolve().parent
    if (root.name != 'repo' or run_root.name != spec['run_id'] or out.exists()
            or not out.resolve().is_relative_to(run_root) or out.resolve().is_relative_to(root.resolve())):
        raise ValueError('扩展输出须在本run独立根内、源码目录外且不存在')
    cfg = spec['session_config']
    if cfg['session_id'] != spec['run_id'] or cfg['username'] != spec['username'] or cfg.get('reuse_world'):
        raise ValueError('cfg必须属于本run初次自然会话，身份不能换名')
    if not cfg.get('natural_terrain') or cfg.get('world_seed') != fixture()['seed']:
        raise ValueError('fresh扩展入口须自然固定seed')
    if 'optional_personal_story' in selected and (not spec.get('home') or not spec.get('native_fixture', {}).get('combat_uuid')):
        raise ValueError('缺home/真实敌对生物fixture')
    if 'marker_matrix' in selected and (not spec.get('portal') or not spec.get('native_fixture')):
        raise ValueError('缺portal/NPC实际fixture')
    if 'marker_matrix' in selected and spec.get('allow_nether') is not True:
        raise ValueError('marker跨维度须启动前显式启用下界')
    from tools.qianzhen_opening_extended_fixtures import validate_fixture_bundle
    validate_fixture_bundle(spec, selected)
    return selected


def execute_attachment(spec, root, out):
    """Concrete execution, called only after independent source/session validation."""
    from tools.qianzhen_opening_collect import validate_attachment, OpeningCollector
    from tools.qianzhen_opening_run import (verify_owned_session, verify_dependencies, PAL, CATALOG,
                                            split_stage_artifacts, single_flush, stop_owned, archive_world, write_json)
    from tools.qianzhen.evidence_candidate import Candidate
    from tools.qianzhen.story_lifecycle import lifecycle_command
    from tools.combat_capture import session as cs
    from tools.combat_capture.xgame import Game
    from tools.combat_capture.capture import stop_recording
    selected = validate_execution_manifest(spec, root, out)
    state = validate_attachment(spec, root)  # unchanged exact-source/fingerprint/session/candidate gate
    verify_owned_session(state, spec['run_id'], root)
    if spec.get('allow_nether') and state.get('allow_nether') is not True:
        raise ValueError('实际本run服务器未启用下界')
    if state['username'] != spec['username']:
        raise ValueError('实际启动玩家与声明身份不符')
    dependencies = verify_dependencies(root, spec['pal_sha256'])
    jar = Path(spec['jar']).resolve()
    if not jar.is_relative_to((root / 'build/libs').resolve()) or digest(jar) != spec['jar_sha256']:
        raise ValueError('本次固定jar文件/身份不符')
    candidate = Candidate(Path(spec['candidate']), root)
    import tempfile
    with tempfile.TemporaryDirectory(prefix='qianzhen-extended-pack-') as binding_pack:
        candidate.prepare_pack(Path(binding_pack))
    world = root / 'run-acceptance/combat_capture_world'
    candidate.verify_installed(world)
    out.mkdir(parents=True, exist_ok=False)
    request_path = Path(spec['request'])
    shutil.copy2(request_path, out / 'request.json')
    write_json(out / 'candidate-identity.json', candidate.identity)
    write_json(out / 'execution-binding.json', {key: spec[key] for key in
               ('schema', 'run_id', 'source_commit', 'source_fingerprint', 'request_sha256', 'jar_sha256', 'scenarios')})
    game = Game(state['display'], Path(state['client_log']), size=tuple(spec.get('size', [960, 540])))
    c = OpeningCollector(game, lambda command, timeout: lifecycle_command(state, command, timeout), spec['username'], out / 'raw')
    deadline = time.monotonic() + spec['timeout']
    flow = None
    trace = {'schema': 'qianzhen-opening-trace-v1', 'evidence_tier': 'native', 'status': 'running',
             'run_id': spec['run_id'], 'selected': selected, 'completed': [], 'unrun': selected.copy(), 'failed': [], 'stages': [],
             'fixture_sha256': digest(root / 'tools/qianzhen_opening_fixture.json'),
             'source': {'commit': spec['source_commit'], 'tracked_status': '', 'fixed': True,
                        'fingerprint': spec['source_fingerprint'], 'request_sha256': spec['request_sha256'],
                        'candidate_identity_sha256': fingerprint(candidate.identity), 'scene_version': candidate.identity['scene_version'],
                        'resource_sha256': candidate.identity['resource_sha256'],
                        'dependency_sha256': {'PAL': digest(root / PAL), 'catalog': digest(root / CATALOG), 'jar': digest(jar),
                                              'gradle-wrapper': digest(root / 'gradle/wrapper/gradle-wrapper.properties')}},
             'world': {'fresh': True, 'isolation_root': str(root.parent), 'directory': str(world),
                       'preserved_world': False, 'seed': fixture()['seed']},
             'source_dependencies': dependencies, 'not_verified': fixture()['not_verified'], 'player_acceptance': 'not_verified'}
    if spec.get('build_awakened_fixture'):
        trace['requested_scenarios'] = trace['selected']
        trace['selected'] = []
        trace['fixture_builder'] = 'current_version_generated'
    record, clips, ext, probe, failure = None, [], None, None, None
    checkpoint = None
    write_json(out / 'trace.json', trace)
    def record_start(current_game, generation):
        nonlocal record
        if record is not None:
            raise RuntimeError('前一段主录像尚未收尾')
        path = out / ('primary-' + str(generation) + '.mp4')
        from tools.qianzhen_opening_extended_integration import start_recording
        process, log, path = start_recording(current_game, path, deadline)
        record = (process, log, path, c.sequence)
        c.capture('primary-start-' + str(generation))
    def record_stop():
        nonlocal record
        if record is None:
            return
        process, log, path, begin = record
        record = None
        try:
            code = stop_recording(process, timeout=10)
            if code != 0:
                raise RuntimeError('主录像非零退出: ' + str(code))
            clips.append({'path': path.name, 'sha256': digest(path), 'begin_sequence': begin, 'end_sequence': c.sequence,
                          'player': c.username, 'generation': len(clips)})
        finally:
            if process.poll() is None:
                process.kill()
                process.wait(timeout=3)
            log.close()
    def persist(row):
        if row['status'] == 'captured':
            if record is None or record[0].poll() is not None:
                raise RuntimeError('主录像在场景完成前已退出')
            if ext and any(process.poll() is not None for process, _, _ in ext.recorders):
                raise RuntimeError('guest录像在场景完成前已退出')
        spans = row['source_spans']
        c.stream.flush()
        c.readbacks.flush()
        actions = [json.loads(line) for line in (c.out / 'actions.jsonl').read_text().splitlines()]
        reads = [json.loads(line) for line in (c.out / 'readbacks.jsonl').read_text().splitlines()]
        actions = [r for r in actions if any(s['begin_sequence_exclusive'] < r['sequence'] <= s['end_sequence_inclusive'] for s in spans)]
        reads = [r for r in reads if any(s['begin_readback_exclusive'] < r['id'] <= s['end_readback_inclusive'] for s in spans)]
        if row['counted_as_selected']:
            # Videos are finalized after cleanup; don't hash an actively written mp4.
            frame = c.out / row['screens'][-1]['path'] if row['screens'] else None
            split_stage_artifacts(out, row, actions, reads, None, frame)
            trace['stages'].append(row)
        else:
            trace.setdefault('prerequisite_captures', []).append({**row, 'actions': actions, 'readbacks': reads})
        write_json(out / 'trace.json', trace)
    def interrupted(signum, frame):
        raise TimeoutError('扩展整体预算/终止，保留失败并进入finally')
    previous = signal.signal(signal.SIGTERM, interrupted)
    timer = threading.Timer(spec['timeout'], lambda: os.kill(os.getpid(), signal.SIGTERM))
    timer.daemon = True
    timer.start()
    try:
        record_start(game, 0)
        flow = OpeningFlow(c, candidate, root, world, deadline)
        native = spec.get('native_fixture')
        if native:
            probe = NativeFixtureProbe(c.read_command, out / 'native-fixture.jsonl', root, c.emit,
                                       combat_uuid=native.get('combat_uuid'), provenance=native['provenance'])
        if spec.get('build_awakened_fixture'):
            from tools.qianzhen_opening_extended_saved import SavedCheckpoint, prepare_unaccepted_awakened
            checkpoint = SavedCheckpoint(state, cs.SessionConfig(**spec['session_config']), out, spec['run_id'],
                                         request_path, record_stop=record_stop, record_start=record_start)
            flow.natural_spawn()
            flow.recruitment_walk()
            trace['builder'] = prepare_unaccepted_awakened(flow, checkpoint, generation_kind='current_version_free_cultivation')
            from tools.qianzhen_opening_extended_saved import write_awakened_manifest
            trace['generated_fixture'] = write_awakened_manifest(trace['builder'], flow, out, spec)
            trace['status'] = 'captured_fixture_not_upgrade_acceptance'
            trace['attempted'] = []
        else:
            ext = ExtendedSession(flow, state, cs.SessionConfig(**spec['session_config']), out, run_id=spec['run_id'],
                                  request_path=request_path, parent_record_stop=record_stop, parent_record_start=record_start,
                                  home=spec.get('home'), portal=spec.get('portal'), marker_fixture=probe,
                                  maturity_timeout=spec.get('maturity_timeout', 900), allow_nether=spec.get('allow_nether', False))
            ext.run_selected(selected, persist)
            trace['status'] = 'captured_not_accepted'
    except BaseException as error:
        failure = error
        trace.update(status='failed', failure=repr(error))
        if ext and ext.active:
            if ext.active in selected:
                trace['failed'].append(ext.active)
            else:
                trace['failed_prerequisite'] = ext.active
            try:
                persist(dict(id=ext.active, status='failed', failure=repr(error), checks=flow.checks, screens=flow.screens,
                             counted_as_selected=ext.active in selected, source_spans=[ext.span(ext.active_begin)], **ext.binding))
            except BaseException as persist_error:
                trace['partial_persist_error'] = repr(persist_error)
        elif spec.get('build_awakened_fixture'):
            trace['attempted'] = []
            trace['fixture_builder_failed'] = True
        try:
            trace['failure_frame'] = c.capture('extended-entry-failure')
        except BaseException as screenshot_error:
            trace['screenshot_error'] = repr(screenshot_error)
    finally:
        # Keep the overall watchdog active through flush/stop; only release it
        # once every owned process has been addressed. Never retry a flush.
        for name, operation in (
            ('extended_cleanup', lambda: ext.close() if ext else probe.close() if probe else None),
            ('primary_recording', record_stop),
            ('flush', lambda: single_flush(state, root, out) if not (ext and ext.flush_count) and not (checkpoint and checkpoint.called) else {'requests': 0, 'reason': 'checkpoint_already_flushed_once'}),
            ('termination', lambda: stop_owned(root, spec['run_id'])),
        ):
            try:
                trace[name] = operation()
            except BaseException as error:
                trace[name] = {'status': 'failed', 'failure': repr(error)}
                failure = failure or error
        for stream in (c.stream, c.readbacks):
            try:
                stream.close()
            except BaseException as error:
                trace.setdefault('cleanup_errors', []).append(repr(error))
                failure = failure or error
        timer.cancel()
        signal.signal(signal.SIGTERM, previous)
        if trace.get('termination', {}).get('status') == 'terminated':
            try:
                trace['saved_world'] = archive_world(world, out)
            except BaseException as error:
                trace['archive_error'] = repr(error)
                failure = failure or error
        trace['primary_video_segments'] = clips
        trace['guest_video_segments'] = ext.videos if ext else []
        for row in trace['stages']:
            relevant = [clip for clip in clips if any(clip['end_sequence'] >= span['begin_sequence_exclusive']
                        and clip['begin_sequence'] <= span['end_sequence_inclusive'] for span in row['source_spans'])]
            if relevant:
                row['artifacts']['video'] = {k: relevant[-1][k] for k in ('path', 'sha256')}
                row['video_segments'] = relevant  # All same-run clips remain explicit; no false seamless video.
        if failure:
            trace['status'] = 'failed'
        # Compare JSON contract values with the raw JSONL, including positions
        # originally held as Python tuples; never change an assertion/result.
        trace = json.loads(json.dumps(trace, ensure_ascii=False))
        write_json(out / 'trace.json', trace)
        try:
            checked = audit(trace, out) if not spec.get('build_awakened_fixture') else {
                'status': 'fixture_builder_not_scenario_acceptance', 'errors': [], 'completed': [],
                'native_verdict': 'requires_existing_awakened_on_independent_copy'}
        except BaseException as error:
            checked = {'status': 'invalid', 'errors': [repr(error)], 'completed': []}
            failure = failure or error
        # Captured images are evidence for parent review, not machine-granted native acceptance.
        trace['structural_audit'] = checked
        from tools.qianzhen_opening_extended_integration import finalize_levels
        finalize_levels(trace)
        if failure or checked['errors']:
            trace['status'] = 'failed'
        logs = out / 'process-logs'
        logs.mkdir(exist_ok=True)
        for folder in (root / 'run-combat-capture/logs', root / 'run-acceptance/logs', root / 'run-combat-capture/client/logs'):
            if folder.exists():
                for path in folder.glob('*.log'):
                    try:
                        shutil.copy2(path, logs / (folder.parent.name + '-' + path.name))
                    except OSError as error:
                        trace.setdefault('log_copy_errors', []).append(repr(error))
                        failure = failure or error
        if failure:
            trace['status'] = 'failed'
        write_json(out / 'audit.json', checked)
        write_json(out / 'trace.json', trace)
    if failure:
        raise failure
    if checked['errors']:
        raise ValueError('扩展结构审计失败，保留trace/audit；未计通过')
    return trace


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--execute', type=Path, required=True, help='父driver在同源首次启动后写入的真实attachment')
    parser.add_argument('--out', type=Path, required=True, help='本run源码目录外的新证据目录')
    args = parser.parse_args(argv)
    execute_attachment(json.loads(args.execute.read_text()), ROOT, args.out.resolve())
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
