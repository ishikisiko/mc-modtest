"""独立真实旧档的屋架管理员回归；绝不作为11个玩家剧情场景的完成证据。"""
from __future__ import annotations
import gzip
import json
from pathlib import Path
import re
import time

from tools.qianzhen_opening_evidence import digest
from tools.qianzhen_opening_flow import frozen_geometry


class RepairFixtureAPI:
    """Separate write authority: one frozen fixture, no story/cultivation/give/tp API."""
    ALLOWED = {'tick freeze', 'tick unfreeze', 'myvillage qianzhen repair_roof preview', 'myvillage qianzhen repair_roof apply'}

    def __init__(self, send, stream):
        self.send, self.stream = send, stream

    def command(self, command):
        ticket = re.fullmatch(r'forceload (?:add|remove) (-?\d+) (-?\d+) (-?\d+) (-?\d+)', command)
        if ticket:
            x0, z0, x1, z1 = map(int, ticket.groups())
            if x0 > x1 or z0 > z1 or (x1 // 16 - x0 // 16 + 1) * (z1 // 16 - z0 // 16 + 1) > 4:
                raise ValueError('屋架独立fixture tickets最多4chunk')
        if command not in self.ALLOWED and not ticket:
            raise ValueError('独立屋架fixture写权限越界')
        row = {'kind': 'admin_fixture', 'purpose': 'legacy_roof_repair', 'command': command}
        try:
            reply = self.send(command, 30)
            row['reply'] = reply
            return reply
        except BaseException as error:
            row['failure'] = repr(error)
            raise
        finally:
            self.stream.write(json.dumps(row, ensure_ascii=False) + '\n')
            self.stream.flush()


def saved_geometry(world, root):
    from tools.buildgen.nbtread import read_gzipped_nbt
    _, value = read_gzipped_nbt(str(world / 'data/myvillage_qianzhen_scene.dat'))
    saved = value['data']
    if saved['phase'] != 'COMPLETE':
        raise ValueError('真实旧场景并未部署完成')
    if saved.get('geometry'):
        layout = frozen_geometry(saved['geometry'])
    else:
        folder = root / 'src/main/resources/data/myvillage/story/entrance_legacy'
        manifests = json.loads((folder / 'provenance.json').read_text())['snapshots']
        rows = [r for r in manifests if r['version'] == saved['version']]
        if len(rows) != 1:
            raise ValueError('旧version不在精确只读历史白名单')
        raw = gzip.decompress((folder / (saved['version'] + '.json.gz')).read_bytes())
        import hashlib
        if hashlib.sha256(raw).hexdigest() != rows[0]['sha256']:
            raise ValueError('真实历史几何来源hash不符')
        layout = json.loads(raw)
    if layout['version'] != saved['version']:
        raise ValueError('旧布局身份不符')
    return saved, layout


def block_changes(before_world, after_world, limit=10000):
    """Compare every preexisting saved chunk's block states, including outside patch.

    Metadata, lighting and timestamps are excluded; no source world is modified.
    A disappeared region/chunk/section is an error, never interpreted as air.
    """
    from tools.yunshan.natural_terrain import read_region, decode_section, section_state
    changes = []
    for path in sorted((before_world / 'region').glob('*.mca')):
        other = after_world / 'region' / path.name
        if not other.is_file():
            raise ValueError('原有region消失: ' + path.name)
        before, _ = read_region(path)
        after, _ = read_region(other)
        for coord, original in before.items():
            if coord not in after:
                raise ValueError('原有chunk消失')
            latest = after[coord]
            old = {int(s['Y']): s for s in original.get('sections', [])}
            new = {int(s['Y']): s for s in latest.get('sections', [])}
            for y in old.keys() | new.keys():
                a, b = old.get(y), new.get(y)
                if a is None or b is None:
                    raise ValueError('原有chunk section集合改变，需人工排查')
                if a.get('block_states') == b.get('block_states'):
                    continue
                aa, bb = decode_section(a), decode_section(b)
                for dy in range(16):
                    for dz in range(16):
                        for dx in range(16):
                            first, last = section_state(aa, dx, dy, dz), section_state(bb, dx, dy, dz)
                            if first != last:
                                changes.append({'pos': [original['xPos'] * 16 + dx, y * 16 + dy, original['zPos'] * 16 + dz], 'before': first, 'after': last})
                                if len(changes) > limit:
                                    raise ValueError('非目标方块变化超限')
    return changes


def require_only_patch_changes(changes, allowed):
    expected = {tuple(r['pos']): (r['before'], r['after']) for r in allowed}
    actual = {tuple(r['pos']): (r['before'], r['after']) for r in changes}
    if len(actual) != len(changes) or actual != expected:
        raise AssertionError('旧档存在非目标改变、漏补或意外替换: ' + repr({'actual': actual, 'expected': expected}))


def saved_story_bindings(world):
    from tools.buildgen.nbtread import read_gzipped_nbt
    _, value = read_gzipped_nbt(str(world / 'data/myvillage_world_sim.dat'))
    sim = json.loads(bytes(value['data']['payload']))
    bindings = [row for row in sim['life']['story_actors'] if row['story_id'] == 'qianzhen_chapter_1']
    roles = {'guide', 'pharmacist', 'zhou_he', 'warden'}
    if len(bindings) != 4 or {row['actor_key'] for row in bindings} != roles:
        raise AssertionError('持久剧情人物绑定不完整或重复')
    ids = {row['person_id'] for row in bindings}
    if len(ids) != 4 or not ids <= {row['id'] for row in sim['persons']}:
        raise AssertionError('持久剧情人物缺失或身份重复')
    return sorted(bindings, key=lambda row: row['actor_key'])


def require_same_story_bindings(before, after):
    if json.dumps(before, sort_keys=True) != json.dumps(after, sort_keys=True):
        raise AssertionError('完整持久story_actor绑定改变')


def require_same_people(before, after):
    """Person IDs persist; activateLife gives each new embodiment a fresh lease epoch."""
    roles = {'guide', 'pharmacist', 'zhou_he', 'warden'}
    if set(before) != roles or set(after) != roles:
        raise AssertionError('原场景命簿角色集合改变或不完整')
    for role in sorted(roles):
        first, last = before[role], after[role]
        if type(first.get('personId')) is not int or first['personId'] <= 0 or last.get('personId') != first['personId']:
            raise AssertionError('原场景命簿人物身份改变: ' + role)
        if any(type(row.get('epoch')) is not int or row['epoch'] < 0 for row in (first, last)) or last['epoch'] < first['epoch']:
            raise AssertionError('原场景命簿执行epoch无效或回退: ' + role)
    if len({row['personId'] for row in after.values()}) != len(roles):
        raise AssertionError('原场景命簿人物身份重复')


def run_repair(args, root, out, jar):
    from tools.qianzhen_opening_run import (copy_fixture, tree_hashes, write_json, single_flush, stop_owned,
                                           archive_world, verify_owned_session)
    from tools.qianzhen.story_lifecycle import lifecycle_command
    from tools.combat_capture import session as cs
    spec_path = Path(args.roof_repair_fixture)
    spec = json.loads(spec_path.read_text())
    source = Path(spec['world']).resolve()
    world = root / 'run-acceptance/combat_capture_world'
    world.parent.mkdir(parents=True, exist_ok=True)
    copied = copy_fixture(spec, world)
    before, layout = saved_geometry(world, root)
    initial_bindings = saved_story_bindings(source)
    patch = json.loads((root / 'blueprints/qianzhen_roof_repair/sample_pavilion.json').read_text())
    selected = [s for s in patch['supported'] if s['version'] == before['version']]
    if len(selected) != 1:
        raise ValueError('屋架补丁不支持该真实旧档；不改版本、不重放模板')
    site = next(s for s in layout['sites'] if s['id'] == patch['site_id'])
    if site != selected[0]['site']:
        raise ValueError('旧渠圃身份不符')
    origin = [before[k] + site['origin'][i] for i, k in enumerate(('x', 'y', 'z'))]
    add = lambda p: [p[i] + origin[i] for i in range(3)]
    targets = [{**r, 'pos': add(r['pos'])} for r in patch['targets']]
    guards = [{**r, 'pos': add(r['pos'])} for r in patch['guards']]
    rejecting = spec.get('expect') == 'reject'
    result = {'kind': 'admin_fixture_roof_repair', 'source_commit': args.commit, 'jar_sha256': digest(jar),
              'fixture': copied, 'patch_sha256': digest(root / 'blueprints/qianzhen_roof_repair/sample_pavilion.json'),
              'selected': ['preview_readonly', 'apply_exact', 'idempotent', 'non_targets_preserved', 'old_version_preserved'],
              'completed': [], 'unrun': [], 'gameplay_scenarios_completed': [], 'player_acceptance': 'not_verified'}
    if rejecting:
        result['selected'] = ['preview_rejected', 'apply_rejected', 'non_targets_preserved', 'old_version_preserved']
    result['selected'] += ['actual_restart', 'saved_personal_worldsim_preserved', 'restarted_preview_idempotent']
    state, error, initial_missing, ticketed = None, None, [], False
    cells = [r['pos'] for r in targets + guards]
    loaded_points = list({(p[0] // 16, p[2] // 16): p for p in cells}.values())
    ticket_bounds = ' '.join(map(str, (min(p[0] for p in cells), min(p[2] for p in cells), max(p[0] for p in cells), max(p[2] for p in cells))))
    with (out / 'repair-actions.jsonl').open('x') as actions, (out / 'repair-readbacks.jsonl').open('x') as reads:
        def send(cmd, timeout):
            reply = lifecycle_command(state, cmd, timeout)
            reads.write(json.dumps({'command': cmd, 'reply': reply}, ensure_ascii=False) + '\n')
            reads.flush()
            return reply
        api = RepairFixtureAPI(send, actions)
        def matches(rows):
            for offset in range(0, len(rows), 12):
                group = rows[offset:offset + 12]
                command = 'execute' + ''.join(' if block ' + ' '.join(map(str, row['pos'])) + ' ' + row['state'] for row in group)
                reply = send(command, 8)
                if 'Test passed' not in reply:
                    raise AssertionError('真实保护格/目标块态不匹配，不清空不覆盖: ' + reply)
        def target_state():
            rows = []
            for row in targets:
                point = ' '.join(map(str, row['pos']))
                reply = send('execute if block ' + point + ' ' + row['before'], 8)
                state_name = row['before'] if 'Test passed' in reply else row['after']
                rows.append({'pos': row['pos'], 'state': state_name})
            matches(rows)
            return rows
        try:
            state = cs.start(cs.SessionConfig(session_id=args.run_id, username='RepairObserver', lock='/content/.mc-heavy.lock',
                             lock_timeout=20, reuse_world=True, language='zh_cn', natural_terrain=True, world_seed=20261024,
                             server_timeout=600, client_timeout=600, ingame_timeout=90, settle=1), wait_timeout=1350, log=lambda _: None)
            verify_owned_session(state, args.run_id, root)
            api.command('tick freeze')
            # Explicit bounded ticket fixture, separately logged. The production
            # repair itself still refuses unloaded chunks and never force-loads.
            api.command('forceload add ' + ticket_bounds)
            ticketed = True
            deadline = time.monotonic() + 8
            for point in loaded_points:
                while 'Test passed' not in send('execute if loaded ' + ' '.join(map(str, point)), 3):
                    if time.monotonic() >= deadline:
                        raise TimeoutError('屋架fixture区块未实际加载')
                    time.sleep(.1)
            from tools.qianzhen.story_evidence_helpers import status_object
            initial_people = status_object(send('myvillage story status RepairObserver', 8))['npc']
            if rejecting:
                for action in ('preview', 'apply'):
                    reply = api.command('myvillage qianzhen repair_roof ' + action)
                    if '拒绝' not in reply:
                        raise AssertionError('真实改建旧档未被生产整组拒绝')
                    result['completed'].append(action + '_rejected')
            else:
                original = target_state()
                matches(guards)
                initial_missing = [r for r, old in zip(targets, original) if old['state'] == r['before']]
                reply = api.command('myvillage qianzhen repair_roof preview')
                if not re.search('只读预览通过：待改 ' + str(len(initial_missing)) + ' 格', reply):
                    raise AssertionError('屋架preview拒绝或变化数不符')
                matches(original + guards)
                result['completed'].append('preview_readonly')
                reply = api.command('myvillage qianzhen repair_roof apply')
                if '新增 ' + str(len(initial_missing)) + ' 格支架' not in reply:
                    raise AssertionError('屋架apply未按预览完成')
                matches([{'pos': r['pos'], 'state': r['after']} for r in targets] + guards)
                result['completed'].append('apply_exact')
                reply = api.command('myvillage qianzhen repair_roof apply')
                if '新增 0 格支架' not in reply:
                    raise AssertionError('再次apply不幂等')
                matches([{'pos': r['pos'], 'state': r['after']} for r in targets] + guards)
                result['completed'].append('idempotent')
            result['flush'] = single_flush(state, root, out)
            api.command('forceload remove ' + ticket_bounds)
            ticketed = False
            stop_owned(root, args.run_id)
            state = cs.start(cs.SessionConfig(session_id=args.run_id, username='RepairObserver', lock='/content/.mc-heavy.lock',
                             lock_timeout=20, reuse_world=True, language='zh_cn', natural_terrain=True, world_seed=20261024,
                             server_timeout=600, client_timeout=600, ingame_timeout=90, settle=1), wait_timeout=1350, log=lambda _: None)
            verify_owned_session(state, args.run_id, root)
            result['completed'].append('actual_restart')
            api.command('tick freeze')
            api.command('forceload add ' + ticket_bounds)
            ticketed = True
            deadline = time.monotonic() + 8
            for point in loaded_points:
                while 'Test passed' not in send('execute if loaded ' + ' '.join(map(str, point)), 3):
                    if time.monotonic() >= deadline:
                        raise TimeoutError('重启后屋架fixture区块未实际加载')
                    time.sleep(.1)
            reply = api.command('myvillage qianzhen repair_roof preview')
            if ('拒绝' not in reply if rejecting else '只读预览通过：待改 0 格' not in reply):
                raise AssertionError('重启后preview不符合已修/拒绝原状态')
            result['completed'].append('restarted_preview_idempotent')
            people = status_object(send('myvillage story status RepairObserver', 8))['npc']
            result['people_lifecycle'] = {'before': initial_people, 'after': people,
                'contract': 'stable_personId_nonregressing_execution_epoch_LifeEngine.activate'}
            require_same_people(initial_people, people)
            if any(digest(world / name) != value for name, value in spec['files'].items() if name.startswith('playerdata/')):
                raise AssertionError('原存档已有玩家数据改变')
            result['personal_files_before_final_save'] = 'unchanged'
        except BaseException as failure:
            error = failure
            result['failure'] = repr(failure)
        finally:
            if ticketed:
                try:
                    api.command('forceload remove ' + ticket_bounds)
                except BaseException as failure:
                    result['ticket_cleanup_failure'] = repr(failure)
                    error = error or failure
            result['termination'] = stop_owned(root, args.run_id)
            result['original_unchanged'] = tree_hashes(source) == spec['files']
            if not result['original_unchanged']:
                error = error or AssertionError('源旧档已漂移')
            if world.exists() and result['termination']['status'] == 'terminated':
                result['archive'] = archive_world(world, out)
                try:
                    final_bindings = saved_story_bindings(world)
                    result['persistent_story_bindings'] = {'before': initial_bindings, 'after': final_bindings}
                    require_same_story_bindings(initial_bindings, final_bindings)
                    if any(digest(world / name) != value for name, value in spec['files'].items() if name.startswith('playerdata/')):
                        raise AssertionError('最终停服后原存档已有玩家数据改变')
                    if 'people_lifecycle' in result:
                        result['completed'].append('saved_personal_worldsim_preserved')
                    changes = block_changes(source, world)
                    result['block_changes'] = changes
                    require_only_patch_changes(changes, initial_missing if 'apply_exact' in result['completed'] else [])
                    result['completed'].append('non_targets_preserved')
                    after, _ = saved_geometry(world, root)
                    if any(after[k] != before[k] for k in ('id', 'version', 'dimension', 'x', 'y', 'z')):
                        raise AssertionError('旧布局版本/位置改变')
                    result['completed'].append('old_version_preserved')
                except BaseException as failure:
                    result['comparison_failure'] = repr(failure)
                    error = error or failure
            result['unrun'] = [n for n in result['selected'] if n not in result['completed']]
            result['status'] = 'captured_not_accepted' if error is None else 'failed'
            write_json(out / 'repair.json', result)
    if error:
        raise error
    return result
