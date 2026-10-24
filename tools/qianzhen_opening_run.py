#!/usr/bin/env python3
"""固定提交的 Colab 自然开局执行入口。默认只显示帮助；--session 才会远程执行。

本机控制器只调用既有 Colab，固定源的远程 worker 才能构建/启动 Minecraft。
失败从不整套重试；不覆盖旧 checkout/world/output，不停止共享 VM。
"""
from __future__ import annotations

import argparse
from contextlib import contextmanager
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import signal
import subprocess
import sys
import tempfile
import threading
import time
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT / 'tools'))
from tools.qianzhen_opening_evidence import digest, fingerprint, fixture, audit, source_snapshot
from tools.qianzhen_opening_flow import OpeningFlow, selected_scenarios, SUPPORTED, DEFERRED

DEFAULT_SESSION = 'qianzhen-91f0063-round4-20261024T061100Z'
PAL = 'PlayerAnimationLibNeoforge-1.1.4+mc.1.21.1.jar'
CATALOG = 'exmod/mod_block_catalog.json'
CATALOG_SHA256 = '56f6844e00d975f01483332590ee62bb5399b62c92a0e74618a35baf6f9f7c24'
CATALOG_BYTES = 1556624


class FixtureCompleted(Exception):
    """Internal dispatch only; finally still owns termination and artifact packaging."""


def write_json(path, value, *, exclusive=False):
    with path.open('x' if exclusive else 'w') as stream:
        stream.write(json.dumps(value, ensure_ascii=False, indent=2) + '\n')


def validate_identity(commit, run_id):
    if not re.fullmatch(r'[0-9a-f]{40}', commit):
        raise ValueError('必须传固定完整40位 commit，不接受分支/HEAD')
    if not re.fullmatch(r'[A-Za-z0-9_-]{8,100}', run_id):
        raise ValueError('run_id需8..100位安全字符')


def build_argv():
    return ['./gradlew', '--no-daemon', '--max-workers=2', '-Dorg.gradle.jvmargs=-Xmx3g',
            'test', '--tests', 'com.example.myvillage.story.*', '--tests', 'com.example.myvillage.client.story.*', 'jar']


def verify_dependencies(root, pal_sha256):
    expected = {PAL: (pal_sha256, None), CATALOG: (CATALOG_SHA256, CATALOG_BYTES)}
    result = {}
    tracked = subprocess.check_output(['git', 'ls-files', '--', CATALOG], cwd=root, text=True, timeout=10)
    if tracked.strip():
        raise ValueError('外置catalog不得伪装为tracked源')
    for name, (sha256, size) in expected.items():
        path = root / name
        if not path.is_file() or path.is_symlink():
            raise ValueError('缺失或非法外置依赖: ' + name)
        actual = {'path': name, 'sha256': digest(path), 'bytes': path.stat().st_size, 'external': True}
        if actual['sha256'] != sha256 or (size is not None and actual['bytes'] != size):
            raise ValueError('外置依赖身份漂移: ' + name)
        result[name] = actual
    return result


def tree_hashes(root):
    if root.is_symlink() or not root.is_dir():
        raise ValueError('世界来源必须为真实目录')
    result = {}
    for path in sorted(root.rglob('*')):
        if path.is_symlink():
            raise ValueError('世界来源不允许symlink')
        if path.is_file() and path.name != 'session.lock':
            result[path.relative_to(root).as_posix()] = digest(path)
    if 'level.dat' not in result or not any(name.startswith('playerdata/') for name in result):
        raise ValueError('真实旧档必须含level.dat与playerdata')
    return result


def copy_fixture(spec, destination):
    """Declared complete hashes + double read prove an untouched, quiescent source copy."""
    source = Path(spec['world']).resolve()
    if spec.get('quiescent') is not True or not spec.get('provenance') or not spec.get('files'):
        raise ValueError('旧档缺真实来源/静止声明/全文件哈希')
    if destination.exists() or destination.resolve().is_relative_to(source) or source.is_relative_to(destination.resolve()):
        raise ValueError('旧档目标存在或与原件嵌套')
    before = tree_hashes(source)
    if before != spec['files'] or fingerprint(before) != spec.get('sha256'):
        raise ValueError('真实fixture来源哈希不符，禁止造旧进度')
    shutil.copytree(source, destination, ignore=shutil.ignore_patterns('session.lock'))
    if tree_hashes(source) != before or tree_hashes(destination) != before:
        raise ValueError('复制期间真实旧档漂移，保留副本并失败')
    return {'source': str(source), 'destination': str(destination), 'sha256': fingerprint(before),
            'files': before, 'provenance': spec['provenance'], 'original_unchanged': True}


@contextmanager
def owned_command(argv, cwd, log, timeout, env=None):
    """Single process group only; the timeout/finally never kills another checkout's job."""
    from tools.combat_capture import procs
    procs.set_child_subreaper()
    with log.open('xb') as stream:
        process = subprocess.Popen(argv, cwd=cwd, env=env, stdout=stream, stderr=subprocess.STDOUT, start_new_session=True)
        started = procs.start_time(process.pid)
        try:
            yield process
            process.wait(timeout=timeout)
            if process.returncode:
                raise RuntimeError(f'命令退出{process.returncode}；日志 {log.name}')
        finally:
            descendants = [(pid, procs.start_time(pid), 'opening-owned-child') for pid in procs.descendants(process.pid)] if procs.is_alive(process.pid, started) else []
            if process.poll() is None:
                try:
                    os.killpg(process.pid, signal.SIGTERM)
                except ProcessLookupError:
                    pass
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid, signal.SIGKILL)
                    process.wait(timeout=3)
            procs.kill_pids(descendants, grace=5, log=lambda _: None)


def verify_authored_jar(root, jar, out):
    from tools.qianzhen.story_assets import assemble, write_scene
    expected_dir = out / 'authored-jar-check'
    expected_dir.mkdir()
    layout, blueprints, assembled = assemble(root / 'blueprints/qianzhen')
    write_scene(expected_dir, layout, blueprints, assembled)
    expected = {p.relative_to(expected_dir).as_posix(): digest(p) for p in expected_dir.rglob('*') if p.is_file()}
    with zipfile.ZipFile(jar) as archive:
        for name, value in expected.items():
            if hashlib.sha256(archive.read(name)).hexdigest() != value:
                raise ValueError('默认authored jar资源不符: ' + name)
        for name in ('qianzhen_opening.json', 'qianzhen_entrance.json'):
            entry = 'data/myvillage/story/' + name
            if archive.read(entry) != (root / 'src/main/resources' / entry).read_bytes():
                raise ValueError('入口/学徒配置未打入同源jar')
        repair = root / 'blueprints/qianzhen_roof_repair/sample_pavilion.json'
        if repair.exists():
            from tools.qianzhen.roof_repair import generate as generate_repair
            if archive.read('data/myvillage/story/qianzhen_roof_repair.json') != generate_repair(repair):
                raise ValueError('局部屋架补丁未按同源生成器精确打包')
    return {'jar_sha256': digest(jar), 'scene_version': layout['version'], 'resources': expected}


def verify_owned_session(state, run_id, root):
    from tools.combat_capture import procs
    if state.get('session_id') != run_id:
        raise ValueError('拒绝停止非本次session')
    for info in state.get('procs', {}).values():
        if procs.is_alive(info['pid'], info.get('start')):
            cwd = Path(f'/proc/{info["pid"]}/cwd').resolve()
            # Xvfb starts in the isolated repo too.
            if not cwd.is_relative_to(root):
                raise ValueError('进程cwd不属本次checkout，拒绝接管')


def stop_owned(root, run_id):
    from tools.combat_capture import session as cs, procs
    state = cs.read_state()
    if not state:
        return {'status': 'not_started', 'vm': 'retained'}
    verify_owned_session(state, run_id, root)
    stopped = cs.stop(timeout=45, log=lambda _: None)
    remaining = [role for role, p in stopped.get('procs', {}).items() if procs.is_alive(p['pid'], p.get('start'))]
    supervisor = stopped.get('supervisor', {})
    if supervisor and procs.is_alive(supervisor['pid'], supervisor.get('start')):
        remaining.append('supervisor')
    result = {'status': 'terminated' if not remaining else 'failed', 'remaining': remaining, 'vm': 'retained'}
    if remaining:
        raise RuntimeError('本次进程未回收: ' + repr(remaining))
    return result


def single_flush(state, root, out):
    from tools.qianzhen.story_lifecycle import lifecycle_command
    log = root / 'run-acceptance/logs/latest.log'
    stat, deadline = log.stat(), time.monotonic() + 90
    result = {'requests': 0, 'status': 'waiting'}
    try:
        result['requests'] = 1
        reply = lifecycle_command(state, 'save-all flush', max(.1, deadline - time.monotonic()))
        if reply.splitlines() != ['Saving the game (this may take a moment!)', 'Saved the game']:
            raise ValueError('单次flush响应不完整或歧义')
        while time.monotonic() < deadline:
            current = log.stat()
            if current.st_ino != stat.st_ino or current.st_size < stat.st_size:
                raise ValueError('flush期间日志旋转')
            with log.open('rb') as stream:
                stream.seek(stat.st_size)
                tail = stream.read()
            (out / 'flush-server.log').write_bytes(tail)
            if any('[Server thread/INFO]' in line and 'ThreadedAnvilChunkStorage: All dimensions are saved' in line
                   for line in tail.decode(errors='replace').splitlines()):
                if time.monotonic() >= deadline:
                    break
                result.update(status='confirmed', log_sha256=digest(out / 'flush-server.log'))
                return result
            time.sleep(.1)
        raise TimeoutError('flush无本次服务端保存收据，不重试')
    except BaseException as error:
        result.update(status='failed', error=repr(error))
        raise
    finally:
        write_json(out / 'flush.json', result)


def archive_world(world, out):
    hashes = {p.relative_to(world).as_posix(): digest(p) for p in sorted(world.rglob('*')) if p.is_file() and p.name != 'session.lock'}
    archive = out / 'world.zip'
    with zipfile.ZipFile(archive, 'x', zipfile.ZIP_DEFLATED) as stream:
        for name in hashes:
            stream.write(world / name, name)
    return {'path': archive.name, 'sha256': digest(archive), 'files': hashes,
            'playerdata_present': any(n.startswith('playerdata/') for n in hashes),
            'worldsim_files': [n for n in hashes if 'sim' in n.lower()]}


def split_stage_artifacts(out, row, actions, readbacks, video, screenshot):
    directory = out / 'stages' / row['id']
    directory.mkdir(parents=True)
    for name, values in (('actions', actions), ('readbacks', readbacks)):
        path = directory / (name + '.jsonl')
        path.write_text(''.join(json.dumps(v, ensure_ascii=False) + '\n' for v in values))
        row.setdefault('artifacts', {})[name] = {'path': str(path.relative_to(out)), 'sha256': digest(path)}
    row['actions'] = actions
    for name, path in (('video', video), ('screenshot', screenshot)):
        if path and path.is_file():
            row['artifacts'][name] = {'path': str(path.relative_to(out)), 'sha256': digest(path)}


def run_worker(args):
    from tools.qianzhen.evidence_candidate import Candidate, assert_cloud, runtime_request
    from tools.qianzhen.storygen.pipeline import generate
    from tools.combat_capture import session as cs, procs
    from tools.combat_capture.xgame import Game
    from tools.combat_capture.capture import ffmpeg_record_argv, stop_recording
    from tools.qianzhen.story_lifecycle import lifecycle_command
    from tools.qianzhen_opening_collect import OpeningCollector
    validate_identity(args.commit, args.run_id)
    assert_cloud(ROOT, 'Colab')
    if ROOT.parent.name != args.run_id or ROOT.name != 'repo':
        raise ValueError('远程worker只能运行在run_id/repo独立checkout')
    snap = source_snapshot(ROOT)
    if snap['commit'] != args.commit or snap['tracked_status'] or snap['untracked_runtime']:
        raise ValueError('固定源不干净或commit不符')
    out = ROOT.parent / 'evidence'
    out.mkdir(exist_ok=False)
    write_json(out / 'source-snapshot.json', snap)
    selected = [] if args.roof_repair_fixture else selected_scenarios(args.scenarios)
    report = {'schema': 'qianzhen-opening-trace-v1', 'status': 'running', 'evidence_tier': 'native',
              'fixed_source_commit': args.commit, 'source_fingerprint': snap['fingerprint'],
              'run_id': args.run_id, 'fixture_sha256': digest(Path(__file__).with_name('qianzhen_opening_fixture.json')),
              'selected': selected, 'completed': [], 'unrun': selected.copy(), 'failed': [], 'stages': [],
              'termination': 'not_started', 'not_verified': fixture()['not_verified'], 'player_acceptance': 'not_verified'}
    state, collector, recorder, failure = None, None, None, None
    world = ROOT / 'run-acceptance/combat_capture_world'
    deadline = time.monotonic() + args.timeout
    def interrupted(signum, frame):
        raise TimeoutError('受控worker收到终止/整体超时，进入finally清理')
    previous = signal.signal(signal.SIGTERM, interrupted)
    timer = threading.Timer(args.timeout, lambda: os.kill(os.getpid(), signal.SIGTERM))
    timer.daemon = True
    timer.start()
    try:
        if selected and selected[0] not in SUPPORTED:
            name = selected[0]
            report['failed'].append(name)
            report['stages'].append({'id': name, 'status': 'failed', 'failure': DEFERRED[name], 'checks': []})
            raise NotImplementedError(DEFERRED[name])
        # Reuse cache, never workspaces. Supervisor and build serialize through the same shared lock.
        missing = procs.missing_programs()
        if missing:
            raise RuntimeError('远程显示/录像依赖缺失: ' + ','.join(missing))
        if world.exists() or (ROOT / 'run-combat-capture').exists():
            raise FileExistsError('新checkout已有世界/会话，拒绝覆盖')
        java = subprocess.check_output(['java', '-version'], stderr=subprocess.STDOUT, text=True, timeout=10)
        if not re.search(r'version "21[.]', java):
            raise ValueError('必须Java21')
        report['source_dependencies'] = verify_dependencies(ROOT, args.pal_sha256)
        write_json(out / 'source-dependencies.json', report['source_dependencies'])
        shutil.copy2(ROOT.parent / 'bootstrap-dependencies.json', out / 'bootstrap-dependencies.json')
        pal = ROOT / PAL
        # Dependency hash is supplied by fixed request/bootstrap, and kept in evidence.
        if digest(pal) != args.pal_sha256:
            raise ValueError('PAL根jar哈希漂移')
        lock = Path('/content/.mc-heavy.lock')
        fd = procs.acquire_lock(lock, timeout=20)
        try:
            with owned_command(build_argv(), ROOT, out / 'build.log', min(1800, args.timeout)):
                pass
        finally:
            os.close(fd)
        jars = [p for p in (ROOT / 'build/libs').glob('*.jar') if not any(word in p.name for word in ('sources', 'javadoc'))]
        if len(jars) != 1:
            raise ValueError('交付jar不唯一')
        report['authored_jar'] = verify_authored_jar(ROOT, jars[0], out)
        shutil.copy2(jars[0], out / jars[0].name)
        if args.roof_repair_fixture:
            from tools.qianzhen_opening_repair import run_repair
            report['evidence_tier'] = 'admin_fixture'
            report['fixed_source_commit'] = args.commit
            report['repair'] = run_repair(args, ROOT, out, jars[0])
            report['status'] = 'captured_not_accepted'
            raise FixtureCompleted()
        candidate_dir = ROOT.parent / 'candidate'
        generate(candidate_dir, 20261024, ROOT / 'blueprints/qianzhen')
        candidate = Candidate(candidate_dir, ROOT)  # first binding actually replays all six plugins
        request_path = ROOT.parent / 'request.json'
        request = {'commit': args.commit, 'provider': 'Colab', 'repo_root': str(ROOT), 'candidate': candidate.identity, 'source_dependencies': report['source_dependencies']}
        write_json(request_path, request, exclusive=True)
        runtime_request(ROOT, request_path, 'Colab', candidate)
        shutil.copy2(request_path, out / 'request.json')
        shutil.copytree(candidate_dir, out / 'candidate')
        report['source'] = {'commit': args.commit, 'tracked_status': '', 'fixed': True,
                            'fingerprint': snap['fingerprint'], 'request_sha256': digest(request_path),
                            'candidate_identity_sha256': fingerprint(candidate.identity), 'scene_version': candidate.identity['scene_version'],
                            'resource_sha256': candidate.identity['resource_sha256'],
                            'dependency_sha256': {'PAL': digest(pal), 'catalog': digest(ROOT / CATALOG), 'jar': digest(jars[0]),
                                                  'gradle-wrapper': digest(ROOT / 'gradle/wrapper/gradle-wrapper.properties')}}
        report['candidate'] = candidate.identity
        report['java'] = java
        report['build_command'] = build_argv()
        pack = candidate.prepare_pack(ROOT.parent)
        report['world'] = {'fresh': True, 'isolation_root': str(ROOT.parent), 'directory': str(world), 'preserved_world': False,
                           'seed': 20261024, 'natural_terrain': True, 'before_start_absent': not world.exists()}
        cfg = cs.SessionConfig(session_id=args.run_id, username='OpeningA', lock=str(lock), lock_timeout=20,
                               natural_terrain=True, natural_monsters=True, language='zh_cn', world_seed=20261024,
                               server_timeout=600, client_timeout=600, ingame_timeout=90, settle=2, gradle_jvmargs='-Xmx2g', **pack)
        # Startup pack is installed by supervisor before first level.dat/world initialization.
        state = cs.start(cfg, wait_timeout=1350, log=lambda _: None)
        verify_owned_session(state, args.run_id, ROOT)
        candidate.verify_installed(world)
        report['initial_level_dat_sha256'] = digest(world / 'level.dat')
        game = Game(state['display'], Path(state['client_log']), size=(960, 540))
        windows = game.xdo('search', '--onlyvisible', '--name', 'Minecraft').split()
        if len(windows) != 1 or not Path('/proc/' + game.xdo('getwindowpid', windows[0]).strip() + '/cwd').resolve().is_relative_to(ROOT):
            raise ValueError('实际Minecraft窗口不属于独立checkout')
        collector = OpeningCollector(game, lambda cmd, t: lifecycle_command(state, cmd, t), 'OpeningA', out / 'raw')
        report['player'] = {'name': 'OpeningA', 'display': game.display, 'window': windows[0],
                            'pid': int(game.xdo('getwindowpid', windows[0]))}
        with (out / 'recording.log').open('xb') as log:
            recorder = subprocess.Popen(ffmpeg_record_argv(game.display, out / 'capture.mp4', args.timeout, size=game.size),
                                        stdin=subprocess.PIPE, stdout=log, stderr=log)
        if recorder.poll() is not None:
            raise RuntimeError('录像启动失败')
        flow = OpeningFlow(collector, candidate, ROOT, world, deadline)
        # Earlier dependencies really execute, but are not silently added to the user's selection.
        prerequisites = ['natural_spawn', 'recruitment_walk', 'voluntary_contract', 'early_teaching']
        executed = set()
        for name in selected:
            row = {'id': name, 'run_id': args.run_id, 'source_commit': args.commit,
                   'request_sha256': digest(request_path), 'checks': [], 'status': 'running'}
            report['stages'].append(row)
            begin_action, begin_read = collector.sequence, collector.readback_id
            flow.checks, flow.screens = [], []
            try:
                if recorder.poll() is not None:
                    raise RuntimeError('录像在场景开始前提前结束')
                if name not in SUPPORTED:
                    raise NotImplementedError(DEFERRED[name])
                needed = prerequisites[:prerequisites.index(name) + 1] if name in prerequisites else prerequisites[:2] if name == 'roof_seed20261024' else prerequisites + ['work_reward']
                for dependency in needed:
                    if dependency not in executed:
                        flow.checks, flow.screens = [], []
                        getattr(flow, dependency)()
                        executed.add(dependency)
                        if dependency != name:
                            report.setdefault('prerequisite_captures', []).append({'id': dependency, 'counted_as_selected': False,
                                                                                 'checks': flow.checks, 'screens': flow.screens})
                if name not in executed:
                    flow.checks, flow.screens = [], []
                    getattr(flow, name)()
                    executed.add(name)
                # A selected dependency already run is not fabricated here; its earlier raw checks are reused only within this exact run.
                expected = next(s['checks'] for s in fixture()['stages'] if s['id'] == name)
                if {c['id'] for c in flow.checks} != set(expected):
                    raise AssertionError('场景断言覆盖未完整实现: ' + name)
                if recorder.poll() is not None:
                    raise RuntimeError('录像在场景结束前提前结束')
                row.update(status='captured', checks=flow.checks)
            except BaseException as error:
                row.update(status='failed', checks=flow.checks, failure=repr(error))
                report['failed'].append(name)
                failure = error
                try:
                    flow.capture('failure-' + name)
                except Exception:
                    pass
            finally:
                collector.stream.flush()
                collector.readbacks.flush()
                actions = [json.loads(l) for l in (collector.out / 'actions.jsonl').read_text().splitlines() if json.loads(l)['sequence'] > begin_action]
                reads = [json.loads(l) for l in (collector.out / 'readbacks.jsonl').read_text().splitlines() if json.loads(l)['id'] > begin_read]
                split_stage_artifacts(out, row, actions, reads, out / 'capture.mp4', collector.out / flow.screens[-1]['path'] if flow.screens else None)
                write_json(out / 'trace.json', report)
            if failure:
                break  # Minimal loop first; no retries or unrelated gameplay expansion after a failure.
        report['status'] = 'captured_not_accepted' if failure is None else 'failed'
        # One explicit flush only, even for a partial run. Failure is terminal, never replayed.
        report['flush'] = single_flush(state, ROOT, out)
    except FixtureCompleted:
        pass
    except BaseException as error:
        failure = error
        report.update(status='failed', failure=repr(error))
    finally:
        if recorder:
            try:
                code = stop_recording(recorder, timeout=10)
                if code != 0:
                    raise RuntimeError('录像退出非零: ' + str(code))
                report['recording'] = {'exit_code': code, 'terminated': True}
            except BaseException as error:
                recorder.kill()
                recorder.wait(timeout=3)
                report['recording'] = {'failure': repr(error), 'terminated': recorder.poll() is not None}
                report['status'] = 'failed'
                failure = failure or error
        if collector:
            collector.stream.close()
            collector.readbacks.close()
        try:
            report['termination'] = stop_owned(ROOT, args.run_id)
        except BaseException as error:
            report['termination'] = {'status': 'failed', 'error': repr(error), 'vm': 'retained'}
            failure = failure or error
        # Never archive a running world as a saved-world proof.
        if (world.exists() and not (out / 'world.zip').exists() and isinstance(report['termination'], dict)
                and report['termination'].get('status') == 'terminated'):
            report['saved_world'] = archive_world(world, out)
        # Normalize hashes only after ffmpeg has finalized. Still requires raw review.
        for row in report['stages']:
            video = out / 'capture.mp4'
            if video.exists():
                row.setdefault('artifacts', {})['video'] = {'path': video.name, 'sha256': digest(video)}
        if report.get('source'):
            checked = audit(report, out)
            unsafe = (not report.get('recording', {}).get('terminated') or 'failure' in report.get('recording', {})
                      or report.get('termination', {}).get('status') != 'terminated')
            if unsafe:
                checked['completed'] = []
                checked['unrun'] = selected.copy()
                checked['errors'].append('录像/进程生命周期不完整，所有场景保持unrun')
            if report.get('status') == 'failed' or failure:
                # Audit structural integrity cannot erase a failed stage/recording/cleanup.
                checked['errors'].extend([row['id'] + ': failed execution' for row in report['stages'] if row['status'] == 'failed'])
                checked['completed'] = [s for s in checked['completed'] if s not in report['failed']]
                checked['unrun'] = [s for s in selected if s not in checked['completed']]
                checked['status'] = 'invalid'
            if checked['errors']:
                report['status'] = 'failed'
                failure = failure or ValueError('规范化证据审计失败，见audit.json')
            report['completed'], report['unrun'] = checked['completed'], checked['unrun']
            write_json(out / 'audit.json', checked)
        if failure:
            report['status'] = 'failed'
        write_json(out / 'trace.json', report)
        # Logs are retained without copying the session state/RCON password or server.properties.
        logs = out / 'process-logs'
        logs.mkdir(exist_ok=True)
        for folder in (ROOT / 'run-combat-capture/logs', ROOT / 'run-acceptance/logs', ROOT / 'run-combat-capture/client/logs'):
            if folder.exists():
                for path in folder.glob('*.log'):
                    shutil.copy2(path, logs / (folder.parent.name + '-' + path.name))
        bundle = ROOT.parent / 'evidence.zip'
        with zipfile.ZipFile(bundle, 'x', zipfile.ZIP_DEFLATED) as archive:
            for path in sorted(out.rglob('*')):
                if path.is_file():
                    archive.write(path, path.relative_to(out))
        write_json(ROOT.parent / 'done.json', {'status': report['status'], 'exit_code': 1 if failure else 0,
                   'selected': selected, 'completed': report['completed'], 'unrun': report['unrun'],
                   'evidence_sha256': digest(bundle), 'vm': 'retained'})
        timer.cancel()
        signal.signal(signal.SIGTERM, previous)
    return 1 if failure else 0


def remote_bootstrap(config):
    """Self-contained code sent via colab exec; source/tools are read only from fixed commit."""
    code = '''import json,os,pathlib,subprocess,sys
c=json.loads(%r)
b=pathlib.Path('/content/qianzhen-opening-runs')/c['run_id']
b.mkdir(parents=True,exist_ok=False)
r=b/'repo'
with (b/'bootstrap.log').open('xb') as log:
 subprocess.run(['git','clone','--no-hardlinks','--no-checkout',c['source_repo'],str(r)],check=True,stdout=log,stderr=log,timeout=120)
 if pathlib.Path(c['source_repo']).is_dir():
  upstream=subprocess.check_output(['git','-C',c['source_repo'],'remote','get-url','origin'],text=True,timeout=10).strip()
  subprocess.run(['git','remote','set-url','origin',upstream],cwd=r,check=True,stdout=log,stderr=log,timeout=10)
 subprocess.run(['git','fetch','origin',c['commit']],cwd=r,check=True,stdout=log,stderr=log,timeout=120)
 subprocess.run(['git','checkout','--detach',c['commit']],cwd=r,check=True,stdout=log,stderr=log,timeout=60)
pal=pathlib.Path(c['dependency_root'])/%r
import hashlib,shutil
assert hashlib.sha256(pal.read_bytes()).hexdigest()==c['pal_sha256'],'PAL hash drift'
shutil.copy2(pal,r/pal.name)
catalog=pathlib.Path(c['dependency_root'])/%r
assert catalog.is_file() and not catalog.is_symlink(),'catalog missing or symlink'
assert catalog.stat().st_size==%r,'catalog size drift'
assert hashlib.sha256(catalog.read_bytes()).hexdigest()==%r,'catalog hash drift'
assert not subprocess.check_output(['git','ls-files','--',%r],cwd=r,text=True).strip(),'catalog must remain external'
(r/catalog.relative_to(c['dependency_root'])).parent.mkdir(parents=True,exist_ok=True)
shutil.copy2(catalog,r/catalog.relative_to(c['dependency_root']))
deps={}
for source,target in [(pal,r/pal.name),(catalog,r/catalog.relative_to(c['dependency_root']))]:
 sha=hashlib.sha256(source.read_bytes()).hexdigest()
 assert hashlib.sha256(target.read_bytes()).hexdigest()==sha,'copied dependency hash drift'
 deps[str(target.relative_to(r))]={'source':str(source),'sha256':sha,'bytes':target.stat().st_size,'external':True}
(b/'bootstrap-dependencies.json').write_text(json.dumps(deps,indent=2))
env=dict(os.environ,MYVILLAGE_CLOUD_PROVIDER='Colab',GRADLE_USER_HOME='/content/qianzhen-gradle-cache')
argv=[sys.executable,str(r/'tools/qianzhen_opening_run.py'),'--worker','--commit',c['commit'],'--run-id',c['run_id'],'--timeout',str(c['timeout']),'--pal-sha256',c['pal_sha256']]
if c.get('roof_repair_fixture'): argv+=['--roof-repair-fixture',c['roof_repair_fixture']]
else: argv+=['--scenarios',*c['scenarios']]
with (b/'worker.log').open('xb') as log:
 p=subprocess.Popen(argv,cwd=r,env=env,stdout=log,stderr=log,start_new_session=True)
start=pathlib.Path('/proc/'+str(p.pid)+'/stat').read_text().rsplit(')',1)[1].split()[19]
(b/'worker-pid.json').write_text(json.dumps({'pid':p.pid,'start':start,'run_id':c['run_id']}))
print(json.dumps({'run_id':c['run_id'],'pid':p.pid,'remote':str(b)}))
''' % (json.dumps(config), PAL, CATALOG, CATALOG_BYTES, CATALOG_SHA256, CATALOG)
    # Kernel RPC returns promptly. Clone/fetch/build never keep a Colab exec
    # request alive for minutes, and an uncertain transport is never relaunched.
    payload = code.replace("b.mkdir(parents=True,exist_ok=False)\n", '')
    return '''import json,os,pathlib,subprocess,sys
b=pathlib.Path(%r)
b.mkdir(parents=True,exist_ok=False)
script=b/'bootstrap.py'
script.write_text(%r)
with (b/'bootstrap-process.log').open('xb') as log:
 p=subprocess.Popen([sys.executable,str(script)],cwd=b,stdout=log,stderr=log,start_new_session=True)
start=pathlib.Path('/proc/'+str(p.pid)+'/stat').read_text().rsplit(')',1)[1].split()[19]
(b/'worker-pid.json').write_text(json.dumps({'pid':p.pid,'start':start,'run_id':%r}))
print('OPENING_LAUNCHED '+str(b))
''' % ('/content/qianzhen-opening-runs/' + config['run_id'], payload, config['run_id'])


def remote_poll_code(remote):
    return '''import json,pathlib
b=pathlib.Path(%r)
done=b/'done.json'
if done.exists(): print('OPENING_DONE '+done.read_text())
else:
 info=json.loads((b/'worker-pid.json').read_text())
 p=pathlib.Path('/proc/'+str(info['pid'])+'/stat')
 live=p.exists() and p.read_text().rsplit(')',1)[1].split()[19]==info['start'] and p.read_text().rsplit(')',1)[1].split()[0]!='Z'
 print('OPENING_RUNNING' if live else 'OPENING_DEAD')
''' % remote


def remote_cancel_code(remote):
    return '''import json,os,pathlib,signal
b=pathlib.Path(%r)
i=json.loads((b/'worker-pid.json').read_text())
p=pathlib.Path('/proc/'+str(i['pid']))
if p.exists() and (p/'stat').read_text().rsplit(')',1)[1].split()[19]==i['start']:
 cwd=(p/'cwd').resolve()
 assert cwd.is_relative_to(b),'refuse shared process'
 os.killpg(i['pid'],signal.SIGTERM)
 print('OPENING_CANCEL_SENT')
else: print('OPENING_ALREADY_EXITED')
''' % remote


def colab_exec(session, code):
    with tempfile.NamedTemporaryFile('w', suffix='.py', delete=False) as stream:
        stream.write(code)
        path = Path(stream.name)
    try:
        return subprocess.run(['colab', 'exec', '-s', session, '--timeout', '60', '-f', str(path)],
                              check=True, capture_output=True, text=True, timeout=75).stdout
    finally:
        path.unlink()


def controller(args):
    validate_identity(args.commit, args.run_id)
    if not args.session:
        raise ValueError('必须显式指定既有Colab session')
    selected = [] if args.roof_repair_fixture else selected_scenarios(args.scenarios)
    destination = args.out.resolve()
    destination.mkdir(parents=True, exist_ok=False)
    status = subprocess.run(['colab', 'status', '-s', args.session], check=True, capture_output=True, text=True, timeout=30).stdout
    (destination / 'colab-status.txt').write_text(status)
    config = dict(run_id=args.run_id, commit=args.commit, source_repo=args.source_repo,
                  dependency_root=args.dependency_root, pal_sha256=args.pal_sha256, timeout=args.timeout, scenarios=selected,
                  roof_repair_fixture=args.roof_repair_fixture)
    write_json(destination / 'launch.json', config, exclusive=True)
    (destination / 'launch.log').write_text(colab_exec(args.session, remote_bootstrap(config)))
    # Bounded controller, one launch only. A lost/failed poll is not permission to relaunch.
    remote = '/content/qianzhen-opening-runs/' + args.run_id
    deadline = time.monotonic() + args.timeout + 180
    while time.monotonic() < deadline:
        reply = colab_exec(args.session, remote_poll_code(remote))
        if 'OPENING_DEAD' in reply:
            (destination / 'remote-failure.txt').write_text(reply)
            raise RuntimeError('远程worker/启动器已退出且未生成done；保留bootstrap/worker日志，不重复启动')
        rows = [line.split('OPENING_DONE ', 1)[1] for line in reply.splitlines() if 'OPENING_DONE ' in line]
        if rows:
            done = json.loads(rows[-1])
            write_json(destination / 'done.json', done, exclusive=True)
            subprocess.run(['colab', 'download', '-s', args.session, remote + '/evidence.zip', str(destination / 'evidence.zip')], check=True, timeout=180)
            if digest(destination / 'evidence.zip') != done['evidence_sha256']:
                raise ValueError('下载证据包hash不符')
            print(json.dumps(done, ensure_ascii=False))
            return done['exit_code']
        time.sleep(10)
    (destination / 'cancel.log').write_text(colab_exec(args.session, remote_cancel_code(remote)))
    raise TimeoutError('控制器到期；已请求本run进程组finally清理。保留失败/日志，VM不停止，不重启整套')


def parser():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--session', help='既有Colab名称；不创建/重启/停止VM')
    p.add_argument('--commit', required=True)
    p.add_argument('--run-id', required=True)
    p.add_argument('--source-repo', default='/content/qianzhen', help='远程可取到固定commit的git源，旧checkout只读')
    p.add_argument('--dependency-root', default='/content/qianzhen')
    p.add_argument('--pal-sha256', required=True)
    p.add_argument('--timeout', type=int, default=5400)
    p.add_argument('--out', type=Path, default=Path('reports/qianzhen-opening/native'))
    p.add_argument('--scenarios', nargs='+', default=['natural_spawn', 'recruitment_walk', 'voluntary_contract', 'early_teaching'])
    p.add_argument('--worker', action='store_true', help=argparse.SUPPRESS)
    p.add_argument('--roof-repair-fixture', help='远程真实旧档来源manifest；单独管理员回归，不计11剧情场景')
    return p


def main():
    args = parser().parse_args()
    if not 300 <= args.timeout <= 7200:
        raise ValueError('整体预算需300..7200秒')
    if not re.fullmatch(r'[0-9a-f]{64}', args.pal_sha256):
        raise ValueError('必须固定PAL根jar的完整sha256')
    return run_worker(args) if args.worker else controller(args)


if __name__ == '__main__':
    raise SystemExit(main())
