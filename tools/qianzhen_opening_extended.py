"""显式接线的原生扩展：双客户端、有限fixture、单次flush与分场景留证。

不拥有远程VM，不绕过固定源/StoryInput/evidence审计。父worker必须在同源
验证后创建本对象，使用dispatch，最后close；不能使用旧worker的依赖表。
"""
from __future__ import annotations

from contextlib import contextmanager
from dataclasses import replace
import json
import math
import os
from pathlib import Path
import re
import shutil
from types import SimpleNamespace
import time

from tools.qianzhen_opening_evidence import digest, fingerprint, fixture, MappedOpeningInput, source_snapshot
from tools.qianzhen_opening_collect import OpeningCollector
from tools.qianzhen_opening_flow import OpeningFlow, selected_scenarios

PROFILE = '"neoforge:attachments"."myvillage:cultivation_profile"'
PERSONAL = ('player', 'chapter', 'schema', 'definition_version', 'site', 'dimension',
            'stage', 'accepted', 'completed', 'evidence', 'receipts', 'reward', 'opening')


def personal(state):
    """Explicit persisted contract, excluding view revision/positions/lease clocks."""
    return {key: state[key] for key in PERSONAL}


def identity(state):
    result = {role: row['personId'] for role, row in state['npc'].items()}
    if len(result) != 4 or any(type(v) is not int or v < 0 for v in result.values()) or len(set(result.values())) != 4:
        raise ValueError('需要四个不同的真实命簿人物')
    return result


def saved_semantics(before, after):
    """Epoch changes are recorded, never mistaken for person recreation."""
    return {'personal_equal': personal(before) == personal(after),
            'people_equal': identity(before) == identity(after),
            'shared_equal': before['shared'] == after['shared'],
            'epochs': {role: {'before': before['npc'][role]['epoch'], 'after': after['npc'][role]['epoch']}
                       for role in before['npc']}}


def item_count(rows, item):
    return sum(row.get('count', row.get('Count', 0)) for row in rows if row['id'] == item)


def inventory_semantics(rows):
    # Order is not a save invariant; slot/components/count are. Ignore nothing inside a stack.
    return sorted(rows, key=lambda row: row['Slot'])


def validate_path(path):
    if not isinstance(path, list) or not 1 <= len(path) <= 512:
        raise ValueError('实际路线必须有界1..512格')
    for point in path:
        if not isinstance(point, (list, tuple)) or len(point) != 3 or any(type(v) is not int for v in point):
            raise ValueError('路线必须为整数三坐标')
    for a, b in zip(path, path[1:]):
        if abs(a[0] - b[0]) + abs(a[2] - b[2]) != 1 or abs(a[1] - b[1]) > 1:
            raise ValueError('路线不能斜行/跳层')
    return [tuple(p) for p in path]


class ActorCollector(OpeningCollector):
    """Each Game owns its own gate; all actors share one sequence/readback sink.

    This is an explicit adapter, not a monkeypatch of the core collector.
    """
    def __init__(self, primary, game, username):
        self.primary, self.game, self.username = primary, game, username
        self.out, self.stream, self.readbacks = primary.out, primary.stream, primary.readbacks
        self.read_command = primary.read_command
        self.offset = game.client_log.stat().st_size
        self.input = MappedOpeningInput(game, self.rotation, self.emit, username)

    @property
    def sequence(self):
        return self.primary.sequence

    @property
    def readback_id(self):
        return self.primary.readback_id

    @readback_id.setter
    def readback_id(self, value):
        self.primary.readback_id = value

    @property
    def capture_id(self):
        return self.primary.capture_id

    @capture_id.setter
    def capture_id(self, value):
        self.primary.capture_id = value

    def emit(self, kind, **detail):
        self.primary.emit(kind, **detail)


class InventoryFixture:
    """Only previously empty personal inventory slots; never crop/root/story/reward writes.

    Commands live in a separately declared ledger. Player actions contain only an
    observation pointing at its receipt, so continuous actions remain auditable.
    Parent must attach this ledger AND run verify() before accepting the extension.
    """
    ITEM = 'minecraft:cobblestone'

    def __init__(self, send, path, primary):
        self.send, self.path, self.primary = send, path, primary
        self.stream = path.open('x')
        self.owned, self.rows = {}, []

    def command(self, player, slot, fill, *, purpose):
        if not re.fullmatch(r'[A-Za-z0-9_]{1,16}', player) or type(slot) is not int or not 0 <= slot < 36:
            raise ValueError('fixture玩家或槽位越界')
        key = (player, slot)
        if not fill and key not in self.owned:
            raise ValueError('只清理本fixture填入且仍持有的槽位')
        name = ('hotbar.' + str(slot)) if slot < 9 else ('inventory.' + str(slot - 9))
        command = f'item replace entity {player} {name} with ' + (self.ITEM + ' 64' if fill else 'minecraft:air')
        row = dict(kind='independent_inventory_fixture', purpose=purpose, player=player, slot=slot,
                   before='minecraft:air' if fill else self.ITEM, command=command, time=time.monotonic())
        try:
            row['reply'] = self.send(command, 8)
            if not re.search(r'Replaced .* with ', row['reply']):
                raise ValueError('fixture写入未获明确命令收据: ' + row['reply'])
            if fill:
                self.owned[key] = True
            else:
                del self.owned[key]
        except BaseException as error:
            row['failure'] = repr(error)
            raise
        finally:
            self.rows.append(row)
            self.stream.write(json.dumps(row, ensure_ascii=False) + '\n')
            self.stream.flush()
            self.primary.emit('observation', label='declared_inventory_fixture_receipt',
                              fixture_file=self.path.name, receipt=len(self.rows), receipt_sha256=fingerprint(row))

    def fill(self, flow):
        rows = flow.data('Inventory')
        prohibited = {r['item'] for r in flow.chapter['rewards']} | {flow.chapter['work']['item']}
        if self.ITEM in prohibited:
            raise ValueError('fixture物品与生产工作/报酬冲突')
        # Fail instead of modifying existing partial reward stacks.
        if any(row['id'] in {r['item'] for r in flow.chapter['rewards']} for row in rows):
            raise ValueError('满包负例需要无既有奖励堆叠的新鲜库存')
        occupied = {row['Slot'] for row in rows}
        for slot in range(36):
            if slot not in occupied:
                self.command(flow.c.username, slot, True, purpose='full_inventory_pending')
        actual = flow.data('Inventory')
        if {row['Slot'] for row in actual if 0 <= row['Slot'] < 36} != set(range(36)):
            raise AssertionError('fixture未真实填满36槽')
        return actual

    def cleanup(self, flows):
        for flow in flows:
            by_slot = {r['Slot']: r for r in flow.data('Inventory')}
            for player, slot in list(self.owned):
                if player != flow.c.username:
                    continue
                row = by_slot.get(slot)
                if row and row['id'] == self.ITEM and item_count([row], self.ITEM) == 64:
                    self.command(player, slot, False, purpose='fixture_cleanup')
                else:
                    # Dropped/placed/changed stacks are no longer owned, never delete player loot.
                    del self.owned[player, slot]

    def verify(self):
        if not self.rows or any('failure' in row for row in self.rows):
            raise AssertionError('fixture账本为空或存在失败')
        return {'path': self.path.name, 'sha256': digest(self.path), 'receipts': len(self.rows),
                'purposes': sorted({r['purpose'] for r in self.rows}), 'remaining_owned': list(self.owned)}


class ExtendedSession:
    """Connect only AFTER the parent's exact-source/candidate/request validation.

    cfg/state/read command remain owned by parent; restart updates state in place
    so its existing RCON closure and finally cleanup point at the new server.
    parent_record_stop/start explicitly delimit primary video across the restart.
    """
    def __init__(self, flow, state, cfg, out, *, run_id, request_path, parent_record_stop, parent_record_start,
                 home=None, portal=None, marker_fixture=None, maturity_timeout=900, allow_nether=False):
        if os.environ.get('MYVILLAGE_CLOUD_PROVIDER') != 'Colab' or not Path('/content').is_dir():
            raise ValueError('扩展原生适配器仅允许显式Colab，没有本机回退')
        from tools.qianzhen_opening_run import verify_owned_session
        verify_owned_session(state, run_id, flow.root)
        from tools.qianzhen.evidence_candidate import runtime_request
        self.request_path = Path(request_path)
        request = runtime_request(flow.root, self.request_path, 'Colab', flow.candidate)
        snap = source_snapshot(flow.root)
        if snap['tracked_status'] or snap['untracked_runtime'] or snap['commit'] != json.loads(self.request_path.read_text())['commit']:
            raise ValueError('扩展固定源不干净或请求提交漂移')
        self.binding = {'source_commit': snap['commit'], 'source_fingerprint': snap['fingerprint'],
                        'request_sha256': digest(self.request_path), 'run_id': run_id}
        for label, spec in (('home', home), ('portal', portal)):
            if spec is not None and (spec.get('binding') != self.binding or not spec.get('provenance')):
                raise ValueError(label + ' fixture未绑定本run固定源/请求/准备来源')
        if type(maturity_timeout) not in (int, float) or not math.isfinite(maturity_timeout) or not 10 <= maturity_timeout <= 1800:
            raise ValueError('成熟观察预算10..1800秒')
        self.a, self.state, self.cfg, self.out, self.run_id = flow, state, cfg, out, run_id
        self.parent_record_stop, self.parent_record_start = parent_record_stop, parent_record_start
        self.home, self.portal, self.marker_fixture = home, portal, marker_fixture
        self.maturity_timeout = maturity_timeout
        self.allow_nether = allow_nether
        self.guest = self.b = None
        self.generation, self.flush_count = 0, 0
        self.recorders, self.videos, self.results = [], [], {}
        self.fixture = InventoryFixture(flow.c.read_command, out / 'inventory-fixture.jsonl', flow.c)
        self.work = {}
        self.selected = []
        self.active = None
        flow.extensions = self
        from tools.qianzhen_opening_extended_scenarios import ExtendedScenarios
        self.scenarios = ExtendedScenarios(self)

    def closed_menu(self, flow):
        if flow.c.game.pointer_grabbed() is False:
            flow.c.input.close_menu()

    def guest_start(self):
        if self.b is not None:
            return self.b
        from tools.alchemy_quality_evidence import SecondClient
        from tools.qianzhen.story_input import StoryInput
        a = self.a
        adapter = SimpleNamespace(out=self.out, language='zh_cn', s=SimpleNamespace(state=self.state),
                                  poll=a.poll, rc=a.c.read, report={'phases': []},
                                  check=lambda name, passed, detail: self.technical(name, passed, detail))
        def prepare(game):
            StoryInput(game, None, lambda kind, **detail: a.c.emit(kind, player=SecondClient.username, **detail)).activate('extended_guest_start')
        self.guest = SecondClient(adapter, timeout=min(240, max(1, a.deadline - time.monotonic())),
                                  output_name='extended-guest-' + str(self.generation), prepare_game=prepare)
        self.guest.__enter__()
        c = ActorCollector(a.c, self.guest.game, self.guest.username)
        self.record_guest(c)
        c.capture("guest-before-flow-" + str(self.generation))
        b = OpeningFlow(c, a.candidate, a.root, a.world, a.deadline,
                        options_path=self.guest.directory / 'options.txt')
        if b.c.game.display == a.c.game.display or b.c.username == a.c.username:
            raise ValueError('双人必须独立display及username')
        self.b = b
        return b

    def assert_source(self):
        snap = source_snapshot(self.a.root)
        if (snap['tracked_status'] or snap['untracked_runtime'] or snap['commit'] != self.binding['source_commit']
                or snap['fingerprint'] != self.binding['source_fingerprint']
                or digest(self.request_path) != self.binding['request_sha256']):
            raise ValueError('扩展运行期间固定源/请求身份漂移；不跳过检查')

    def record_guest(self, collector):
        from tools.qianzhen_opening_extended_integration import start_recording
        path = self.out / ('guest-' + str(self.generation) + '.mp4')
        self.recorders.append(start_recording(collector.game, path, self.a.deadline))

    def stop_recorders(self):
        from tools.combat_capture.capture import stop_recording
        failures = []
        while self.recorders:
            process, log, path = self.recorders.pop()
            try:
                code = stop_recording(process, timeout=10)
                if code != 0:
                    raise RuntimeError('guest录像非零退出: ' + str(code))
                self.videos.append({'path': str(path.relative_to(self.out)), 'sha256': digest(path)})
            except BaseException as error:
                if process.poll() is None:
                    process.kill()
                    process.wait(timeout=3)
                failures.append(repr(error))
            finally:
                log.close()
        if failures:
            raise RuntimeError('; '.join(failures))

    def technical(self, name, passed, detail):
        self.a.snapshot(name, {'pass': bool(passed), 'detail': detail})
        if not passed:
            raise AssertionError(name + ': ' + repr(detail))

    def actor_proof(self, flow):
        flow.c.input.gate.ready('extended_actor_identity', allow_escape=False)
        gate = flow.c.input.gate
        return {'name': flow.c.username, 'uuid': flow.state()['player'], 'display': flow.c.game.display,
                'window': gate.window, 'pid': int(gate.pid)}

    @contextmanager
    def collect_checks(self, name):
        old = self.a.checks, self.a.screens
        self.a.checks, self.a.screens = [], []
        try:
            yield
        finally:
            self.results[name] = {'checks': self.a.checks, 'screens': self.a.screens}
            self.a.checks, self.a.screens = old

    def checkpoint(self):
        """One explicit flush, true stop, immutable stopped archive, true dual rejoin."""
        from tools.qianzhen_opening_run import single_flush, stop_owned, archive_world, verify_owned_session
        from tools.combat_capture import session as cs, procs
        from tools.combat_capture.xgame import Game
        from tools.qianzhen.story_lifecycle import lifecycle_command
        if self.flush_count:
            raise RuntimeError('本run单次flush已经使用；禁止重试')
        self.closed_menu(self.a)
        self.closed_menu(self.b)
        before = {f.c.username: {'story': f.state(), 'inventory': inventory_semantics(f.data('Inventory')),
                                'profile': f.data(PROFILE), 'fates': f.fates()} for f in (self.a, self.b)}
        folder = self.out / 'restart-checkpoint'
        folder.mkdir()
        self.flush_count += 1
        flush = single_flush(self.state, self.a.root, folder)
        self.stop_recorders()
        self.guest.__exit__(None, None, None)
        self.guest, self.b = None, None
        self.parent_record_stop()
        old_server = dict(self.state['procs']['server'])
        terminated = stop_owned(self.a.root, self.run_id)
        if procs.is_alive(old_server['pid'], old_server.get('start')):
            raise AssertionError('旧server仍活着，不能称真实重启')
        archive = archive_world(self.a.world, folder)
        uuids = [s['story']['player'] for s in before.values()]
        required = ['data/myvillage_world_sim.dat', 'data/myvillage_qianzhen_scene.dat'] + ['playerdata/' + u + '.dat' for u in uuids]
        if not all(name in archive['files'] for name in required):
            raise AssertionError('停服归档缺少精确WorldSim/两名playerdata来源')
        # No replacement/regeneration: reuse the SAME isolated saved directory.
        from tools.qianzhen_opening_extended_integration import start_session
        new = start_session(replace(self.cfg, reuse_world=True), allow_nether=self.allow_nether,
                       wait_timeout=min(1350, max(1, self.a.deadline - time.monotonic())), log=lambda _: None)
        verify_owned_session(new, self.run_id, self.a.root)
        self.state.clear()
        self.state.update(new)
        self.generation += 1
        self.a.c.game = Game(new['display'], Path(new['client_log']), size=(960, 540))
        self.a.c.input = MappedOpeningInput(self.a.c.game, self.a.c.rotation, self.a.c.emit, self.a.c.username)
        self.a.c.offset = 0
        self.parent_record_start(self.a.c.game, self.generation)
        self.a.candidate.verify_installed(self.a.world)
        self.guest_start()
        for f in (self.a, self.b):
            f.poll(lambda: lifecycle_command(self.state, 'list', 5), lambda s: f.c.username in s, 60, '真实重入未出现')
            self.actor_proof(f)
        after = {f.c.username: {'story': f.state(), 'inventory': inventory_semantics(f.data('Inventory')),
                               'profile': f.data(PROFILE), 'fates': f.fates()} for f in (self.a, self.b)}
        result = dict(before=before, after=after, flush=flush, terminated=terminated, archive=archive,
                      required_saved_files={name: archive['files'][name] for name in required},
                      old_server=old_server, new_server=dict(new['procs']['server']))
        (folder / 'semantics.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
        return result

    def work_reward(self):
        return self.scenarios.work_reward()

    def restart_two_players(self):
        return self.scenarios.restart_two_players()

    def optional_personal_story(self):
        return self.scenarios.optional_personal_story()

    def marker_matrix(self):
        return self.scenarios.marker_matrix()

    def existing_awakened(self):
        return self.scenarios.existing_awakened()

    def legacy_accepted(self):
        return self.scenarios.legacy_accepted()

    def cursor(self):
        return {'sequence': self.a.c.sequence, 'readback_id': self.a.c.readback_id}

    def span(self, before):
        return {'begin_sequence_exclusive': before['sequence'], 'end_sequence_inclusive': self.a.c.sequence,
                'begin_readback_exclusive': before['readback_id'], 'end_readback_inclusive': self.a.c.readback_id,
                **self.binding}

    def require_coverage(self, name):
        expected = next(s['checks'] for s in fixture()['stages'] if s['id'] == name)
        if {c['id'] for c in self.a.checks} != set(expected) or len(self.a.checks) != len(expected):
            raise AssertionError('扩展场景覆盖缺失/重复: ' + name)

    def dispatch(self, names):
        """Generator: parent persists each yielded stage, preserves failures/unrun.

        It never audits or promotes results to native pass. Marker preparation runs
        BEFORE work changes the target; every selected result has exact fixture IDs.
        """
        selected = selected_scenarios(names)
        self.selected = selected
        self.assert_source()
        if {'legacy_accepted', 'existing_awakened'} & set(selected):
            raise ValueError('真实保存副本场景须独立SavedFixtureSession；不能混进fresh自然世界')
        if 'optional_personal_story' in selected and self.home is None:
            raise ValueError('缺实际home路线/放置目标fixture，拒绝开跑')
        if 'optional_personal_story' in selected and (self.marker_fixture is None or not self.marker_fixture.combat_uuid):
            raise ValueError('缺实际home敌对生物UUID/只读probe，不能把无战斗分支算完整场景')
        if 'marker_matrix' in selected and (self.portal is None or self.marker_fixture is None):
            raise ValueError('缺实际portal/遮挡/AI fixture，拒绝开跑')
        completed = set()
        wanted = set(selected)
        order = ['natural_spawn', 'recruitment_walk', 'voluntary_contract', 'early_teaching']
        needs_work = bool(wanted & {'work_reward', 'restart_two_players', 'optional_personal_story', 'marker_matrix'})
        required_indices = [order.index(name) for name in selected if name in order]
        if 'roof_seed20261024' in wanted:
            required_indices.append(1)
        if needs_work:
            required_indices.append(3)
        order = order[:max(required_indices) + 1]
        for name in order:
            self.active = name
            self.active_begin = self.cursor()
            self.assert_source()
            self.a.checks, self.a.screens = [], []
            begin = self.cursor()
            getattr(self.a, name)()
            self.require_coverage(name)
            completed.add(name)
            result = dict(id=name, status='captured', checks=self.a.checks[:], screens=self.a.screens[:],
                          counted_as_selected=name in wanted, source_spans=[self.span(begin)])
            self.results[name] = result
            yield result
        if needs_work:
            self.active = 'work_reward'
            if 'marker_matrix' in wanted:
                self.active = 'marker_matrix'
                self.active_begin = self.cursor()
                self.a.checks, self.a.screens = [], []
                marker_begin = self.cursor()
                self.scenarios.marker_prepare()
                self.scenarios.marker['source_spans'] = [self.span(marker_begin)]
            self.active = 'work_reward'
            self.active_begin = self.cursor()
            self.a.checks, self.a.screens = [], []
            begin = self.cursor()
            self.work_reward()
            self.require_coverage('work_reward')
            work_span = self.span(begin)
            if 'marker_matrix' in wanted:
                self.scenarios.marker['source_spans'].append(work_span)
            result = dict(id='work_reward', status='captured', checks=self.a.checks[:], screens=self.a.screens[:],
                          counted_as_selected='work_reward' in wanted, source_spans=[work_span])
            self.results['work_reward'] = result
            yield result
        for name in selected:
            if name in completed or name == 'work_reward':
                continue
            self.a.checks, self.a.screens = [], []
            self.active = name
            self.active_begin = self.cursor()
            self.assert_source()
            begin = self.cursor()
            getattr(self.a, name)()
            self.require_coverage(name)
            spans = self.scenarios.marker['source_spans'][:] if name == 'marker_matrix' else []
            spans.append(self.span(begin))
            result = dict(id=name, status='captured', checks=self.a.checks[:], screens=self.a.screens[:], counted_as_selected=True,
                          source_spans=spans)
            self.results[name] = result
            yield result

    def run_selected(self, names, persist_stage):
        """A bounded capture manifest; only parent's unchanged audit may complete stages.

        persist_stage receives each exact result, including prerequisite captures;
        it must write primary/guest video segment + action/readback artifacts.
        """
        selected = selected_scenarios(names)
        report = dict(schema='qianzhen-opening-extended-dispatch-v1', **self.binding,
                      selected=selected, captured=[], completed=[], unrun=selected.copy(), failed=[], stages=[],
                      status='running', evidence_tier='native_capture_not_accepted')
        path = self.out / 'extended-dispatch.json'
        try:
            for result in self.dispatch(selected):
                result.update(self.binding)
                persist_stage(result)
                report['stages'].append(result)
                if result['counted_as_selected']:
                    report['captured'].append(result['id'])
                path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
            report['status'] = 'captured_not_accepted'
        except BaseException as error:
            report.update(status='failed', failure=repr(error), active=self.active)
            report['failed'] = [self.active] if self.active in selected else []
            report['failed_prerequisite'] = self.active if self.active not in selected else None
            report['partial_checks'] = self.a.checks
            report['partial_work'] = self.work
            report['partial_marker'] = self.scenarios.marker
            try:
                report['failure_frame'] = self.a.capture('extended-failure')
            except BaseException as capture_error:
                report['capture_failure'] = repr(capture_error)
            raise
        finally:
            from tools.qianzhen_opening_extended_integration import finalize_levels
            finalize_levels(report)
            path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
        return report

    def close(self):
        """Call before parent's final flush/stop; even failures retain all artifacts."""
        errors = []
        try:
            if self.b is not None:
                self.fixture.cleanup((self.a, self.b))
        except BaseException as error:
            errors.append(repr(error))
        try:
            self.stop_recorders()
        except BaseException as error:
            errors.append(repr(error))
        if self.guest is not None:
            try:
                self.guest.__exit__(None, None, None)
            except BaseException as error:
                errors.append(repr(error))
            finally:
                self.guest = self.b = None
        self.fixture.stream.close()
        if self.marker_fixture is not None:
            self.marker_fixture.close()
        summary = {'status': 'failed' if errors else 'closed', 'errors': errors, 'videos': self.videos,
                   'flush_requests': self.flush_count, 'fixture': {'path': self.fixture.path.name, 'sha256': digest(self.fixture.path)},
                   'native_acceptance': 'not_verified'}
        (self.out / 'extended-cleanup.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2) + '\n')
        if errors:
            raise RuntimeError('扩展清理失败: ' + '; '.join(errors))
        return summary
