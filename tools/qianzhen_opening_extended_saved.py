"""真实保存副本适配；不改NBT，不以新候选包替换旧世界的原资源身份。"""
from __future__ import annotations

import json
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import time
from types import SimpleNamespace
import zipfile

from tools.qianzhen_opening_evidence import digest, fingerprint
from tools.qianzhen_opening_flow import OpeningFlow, saved_geometry_expected
from tools.qianzhen_opening_extended import PROFILE, identity, item_count, personal, saved_semantics
from tools.qianzhen_opening_extended_scenarios import fate_identity


def extract_saved_fixture(spec, destination):
    """Hash both sides of read-only ZIP extraction; no owner world is ever opened live."""
    archive = Path(spec['archive'])
    if spec.get('schema') != 'qianzhen-saved-fixture-v1' or not spec.get('provenance'):
        raise ValueError('保存fixture缺明确schema/来源')
    if not re.fullmatch(r'[0-9a-f]{64}', spec.get('archive_sha256', '')) or digest(archive) != spec['archive_sha256']:
        raise ValueError('真实原件ZIP身份不符')
    if destination.exists() or destination.resolve().is_relative_to(archive.resolve()):
        raise ValueError('副本目标已存在或越界')
    prefix = PurePosixPath(spec['archive_root'])
    if prefix.is_absolute() or '..' in prefix.parts or (not prefix.parts and spec['archive_root'] != '.'):
        raise ValueError('ZIP根目录非法')
    with zipfile.ZipFile(archive) as zipped:
        entries, seen = [], set()
        if sum(info.file_size for info in zipped.infolist()) > 512 * 1024 * 1024:
            raise ValueError('保存fixture超过512MiB有界解压上限')
        for info in zipped.infolist():
            name = PurePosixPath(info.filename)
            if ('\\' in info.filename or name.is_absolute() or '..' in name.parts or info.filename in seen
                    or stat.S_ISLNK(info.external_attr >> 16) or not name.is_relative_to(prefix)):
                raise ValueError('ZIP路径/重复项/符号链接/根目录越界')
            seen.add(info.filename)
            if not info.is_dir():
                relative = name.relative_to(prefix)
                if not relative.parts:
                    raise ValueError('ZIP文件与根目录重合')
                entries.append((info, relative))
        required = {PurePosixPath('level.dat'), PurePosixPath('data/myvillage_qianzhen_scene.dat'),
                    PurePosixPath('data/myvillage_world_sim.dat'), PurePosixPath('playerdata/' + spec['player_uuid'] + '.dat')}
        if not required <= {relative for _, relative in entries}:
            raise ValueError('真实保存fixture缺level/scene/WorldSim/实际玩家')
        destination.mkdir(parents=True, exist_ok=False)
        for info, relative in entries:
            target = destination / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            with zipped.open(info) as source, target.open('xb') as output:
                shutil.copyfileobj(source, output)
    if digest(archive) != spec['archive_sha256']:
        raise ValueError('解压期间原件ZIP漂移')
    from tools.qianzhen_opening_run import tree_hashes
    files = tree_hashes(destination)
    from tools.buildgen.nbtread import read_gzipped_nbt
    _, player = read_gzipped_nbt(str(destination / 'playerdata' / (spec['player_uuid'] + '.dat')))
    progress = player.get('neoforge:attachments', {}).get('myvillage:story_progress', {})
    if spec['kind'] == 'legacy_accepted':
        if progress.get('stage', 'new') in ('new', 'complete') or progress.get('definition_version', 1) != 1:
            raise ValueError('旧续玩夹具必须为真实未完成的accepted-v1进度')
    elif spec['kind'] == 'existing_awakened':
        profile = player.get('neoforge:attachments', {}).get('myvillage:cultivation_profile', {})
        if progress.get('stage', 'new') != 'new' or not profile.get('spiritual_root') or not profile.get('learned_techniques'):
            raise ValueError('已有修行fixture须真实未接任务且已有root/功法，不制造NBT')
    else:
        raise ValueError('保存fixture类型未知')
    return {'archive': str(archive), 'archive_sha256': spec['archive_sha256'], 'original_unchanged': True,
            'destination': str(destination), 'files': files, 'sha256': fingerprint(files),
            'provenance': spec['provenance'], 'saved_story': progress,
            'exact_sources': {key.as_posix(): files[key.as_posix()] for key in required}}


def materialize_saved_resources(spec, world, destination):
    """Copy only the original seven resources, without installing a new pack."""
    source = spec['resources']
    kind = source['kind']
    paths = ['data/myvillage/story/qianzhen_scene.json']
    sites = ('relay_inn', 'apothecary', 'canal_gardens', 'old_relay', 'spring_array', 'initiation_court')
    paths += ['data/myvillage/structure/qianzhen/' + site + '.nbt' for site in sites]
    destination.mkdir(parents=True, exist_ok=False)
    if kind == 'original_jar':
        jar = Path(source['path'])
        if digest(jar) != source['sha256']:
            raise ValueError('真实历史jar hash不符')
        with zipfile.ZipFile(jar) as zipped:
            content = {p: zipped.read(p) for p in paths}
    elif kind == 'embedded_datapack':
        pack = (world / 'datapacks' / source['name']).resolve()
        if not pack.is_relative_to((world / 'datapacks').resolve()):
            raise ValueError('原datapack路径越界')
        if pack.is_dir():
            content = {p: (pack / p).read_bytes() for p in paths}
        else:
            with zipfile.ZipFile(pack) as zipped:
                content = {p: zipped.read(p) for p in paths}
    else:
        raise ValueError('只接受原jar或ZIP内原datapack，不接新candidate替换')
    import hashlib
    actual = {p: hashlib.sha256(raw).hexdigest() for p, raw in content.items()}
    if actual != source['files']:
        raise ValueError('原七资源与声明来源不符')
    for p, raw in content.items():
        target = destination / p
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(raw)
    return actual


class SavedSceneFlow(OpeningFlow):
    """Explicit saved-only factory; never runs natural-spawn/entrance/deploy setup."""
    def __init__(self, collector, root, world, deadline, resources, *, options_path):
        from tools.qianzhen_opening_repair import saved_geometry
        from tools.buildgen.nbtread import read_gzipped_nbt, state_string
        from tools.qianzhen.story_assets import graph_for
        from tools.qianzhen.game_routes import opened_door_graph
        self.c, self.root, self.world, self.deadline = collector, root, world, deadline
        saved, layout = saved_geometry(world, root)
        bundled = json.loads((resources / 'data/myvillage/story/qianzhen_scene.json').read_text())
        if saved_geometry_expected(layout) != saved_geometry_expected(bundled):
            raise ValueError('真实历史保存几何与原资源包不一致')
        self.saved_version = saved['version']
        self.chapter = json.loads((root / 'src/main/resources/data/myvillage/story/qianzhen_opening.json').read_text())
        self.options = dict(line.split(':', 1) for line in options_path.read_text().splitlines() if ':' in line)
        self.extensions = None
        self.records, self.checks, self.screens, self.walked = {}, [], [], 0
        origin = [saved[k] for k in ('x', 'y', 'z')]
        self.entrance = {'origin': origin, 'route': [], 'notice': None, 'saved_only': True}
        self.candidate = SimpleNamespace(layout=layout)
        add = lambda p: tuple(p[i] + origin[i] for i in range(3))
        self.blocks = {}
        for site in layout['sites']:
            path = resources / ('data/myvillage/structure/qianzhen/' + site['id'] + '.nbt')
            if digest(path) != site['sha256']:
                raise ValueError('原模板NBT与历史site hash不符')
            _, nbt = read_gzipped_nbt(str(path))
            palette = [state_string(row) for row in nbt['palette']]
            for block in nbt['blocks']:
                p = add([block['pos'][i] + site['origin'][i] for i in range(3)])
                self.blocks[p] = palette[block['state']]
        for row in layout['roads']:
            self.blocks[add(row['pos'])] = row['state']
        self.graph = opened_door_graph(graph_for(self.blocks), self.blocks)
        self.positions = {key: {field: add(row[field]) for field in ('pos', 'approach')} for key, row in layout['positions'].items()}
        self.targets = {row['id']: {**row, 'pos': add(row['pos']), 'approach': add(row['approach'])}
                        for row in layout['targets'] + layout.get('facilities', [])}

    def natural_spawn(self):
        raise RuntimeError('真实保存副本不提供新世界自然出生证据')


class SavedFixtureSession:
    """Small executable adapter; parent owns start/stop and provides one checkpoint.

    restart(flow) must return before/after story + single flush receipt + actual
    server identities + stopped archive sources, as documented in coord handoff.
    The callback is invoked, never replaced by a plan or administrator status.
    """
    def __init__(self, spec, copied, restart):
        self.spec, self.copied, self.restart = spec, copied, restart

    def original(self):
        if digest(Path(self.spec['archive'])) != self.spec['archive_sha256']:
            raise ValueError('原件ZIP前后hash漂移')
        return {'archive': self.spec['archive'], 'sha256': self.spec['archive_sha256'], 'unchanged': True}

    def validate_restart(self, result):
        if (result['flush']['requests'] != 1 or result['flush']['status'] != 'confirmed'
                or result['termination']['status'] != 'terminated' or not result['archive']['exact_sources']
                or result['old_server'] == result['new_server']):
            raise AssertionError('保存适配器需要真单次flush/停服/重启/原始归档，不接受reload/status代替')
        compared = saved_semantics(result['before'], result['after'])
        if not all(compared[key] for key in ('personal_equal', 'people_equal', 'shared_equal')):
            raise AssertionError('保存重进语义不保持')
        return compared

    def legacy_accepted(self, flow, *, actions=None):
        if self.spec['kind'] != 'legacy_accepted':
            raise ValueError('夹具类型不匹配')
        initial = flow.state()
        if initial['player'] != self.spec['player_uuid'] or initial['definition_version'] != 1 or not initial['accepted']:
            raise ValueError('实际登录身份/旧版本不是声明的真实旧存档')
        flow.check('legacy_fixture_identity', True, {'copied': self.copied, 'original': self.original(), 'actual': initial})
        chapter_path = flow.root / 'src/main/resources/data/myvillage/story/qianzhen_chapter_1.json'
        if digest(chapter_path) != self.spec['legacy_definition_sha256']:
            raise ValueError('accepted-v1定义来源不符')
        definition = json.loads(chapter_path.read_text())
        by_id = {row['id']: row for row in definition['actions']}
        chosen = actions or self.spec['continuation_actions']
        if not chosen or len(chosen) > 32 or len(set(chosen)) != len(chosen):
            raise ValueError('旧续玩动作必须有界且唯一')
        observations, kinds = [], set()
        for action_id in chosen:
            flow.budget()
            row, before = by_id[action_id], flow.state()
            if before['stage'] not in row['stages'] or not set(row['requires']) <= set(before['evidence']):
                raise ValueError('真实旧进度不满足下一实际动作: ' + action_id)
            if row['kind'] == 'investigate':
                flow.use(row['target'])
            elif row['kind'] in ('talk', 'claim'):
                flow.actor(row['role'], action_id)
            else:
                raise ValueError('旧章不接受管理员/服务器fact推进')
            after = flow.poll(flow.state, lambda s: s['revision'] > before['revision'], 12, '真实旧对话/调查未推进')
            if after['definition_version'] != 1 or not set(initial['receipts']) <= set(after['receipts']):
                raise AssertionError('旧版本/原收据丢失')
            if flow.c.game.pointer_grabbed() is False:
                flow.c.input.close_menu()
            kinds.add(row['kind'])
            observations.append({'action': action_id, 'before': before, 'after': after,
                                 'frame': flow.capture('legacy-' + action_id)})
        if not {'talk', 'investigate'} <= kinds:
            raise AssertionError('至少真实旧对话和真实调查，不能只status读取或只领奖')
        result = self.restart(flow)
        compared = self.validate_restart(result)
        final = flow.state()
        flow.check('old_chapter_preserved', final['definition_version'] == 1 and final['site'] == initial['site'],
                   {'initial': initial, 'final': final, 'restart': compared})
        flow.check('old_receipts_preserved', set(initial['receipts']) <= set(final['receipts']),
                   {'initial': initial, 'final': final, 'restart': result, 'original': self.original()})
        flow.check('old_route_reachable', {'talk', 'investigate'} <= kinds, {'actual_continuation': observations,
                   'scope': '保存前缀之后实际续玩；不冒称新跑了旧9调查全链'})
        flow.check('same_worldsim_people', identity(initial) == identity(final), {'before': identity(initial), 'after': identity(final), 'restart': compared})
        return observations

    def existing_awakened(self, flow):
        if self.spec['kind'] != 'existing_awakened':
            raise ValueError('夹具类型不匹配')
        initial, profile, fates = flow.state(), flow.data(PROFILE), flow.fates()
        if initial['player'] != self.spec['player_uuid'] or initial['accepted'] or not profile.get('spiritual_root') or not fates['confirmed']:
            raise ValueError('需要真实未接任务、已测灵定命的保存玩家')
        flow.check('awakened_fixture_identity', True, {'copied': self.copied, 'original': self.original(), 'state': initial, 'profile': profile, 'fates': fates})
        offset = flow.c.game.client_log.stat().st_size
        flow.actor('pharmacist', 'learn')
        flow.capture('awakened-real-terms')
        flow.c.execute({'op': 'dialogue_option', 'option': 'accept'})
        offered = flow.stage('work_offer')
        if flow.c.game.pointer_grabbed() is False:
            flow.c.input.close_menu()
        if not offered['opening']['experienced'] or offered['opening']['supplies'] or offered['reward'] != 'locked':
            raise AssertionError('接受只能读真实既有经历，不能自动发苗/发奖')
        result = self.restart(flow)
        self.validate_restart(result)
        after, fate_after = flow.data(PROFILE), flow.fates()
        flow.check('root_unchanged', profile['spiritual_root'] == after['spiritual_root'], {'before': profile, 'after': after})
        flow.check('fate_candidates_unchanged', fate_identity(fates) == fate_identity(fate_after), {'before': fates, 'after': fate_after})
        flow.check('existing_techniques_preserved', profile['learned_techniques'] == after['learned_techniques']
                   and profile.get('active_core_technique') == after.get('active_core_technique'), {'before': profile, 'after': after})
        flow.check('realm_unchanged', all(profile[key] == after[key] for key in ('realm_id', 'stage_id', 'cultivation_progress', 'stability')),
                   {'before': profile, 'after': after})
        # Opening a stele would itself be an action; the skip path must not open one.
        with flow.c.game.client_log.open(errors='replace') as stream:
            # Log rotates at restart: before segment must be preserved by checkpoint.
            current = stream.read()
        if 'client_log_before' not in result:
            raise ValueError('已有修行重启必须归档接受之前起的原日志段')
        with Path(result['client_log_before']).open('rb') as stream:
            stream.seek(offset)  # stat() is a BYTE offset, including Chinese log lines.
            prior = stream.read().decode(errors='replace')
        flow.check('no_forced_reawakening', 'AWAKENING_UI gui=' not in prior + current and flow.state()['stage'] == 'work_offer',
                   {'offered': offered, 'rejoined': flow.state(), 'before_log': result['client_log_before'],
                    'before_log_sha256': digest(Path(result['client_log_before'])), 'original': self.original()})


class SavedCheckpoint:
    """Concrete single-client lifecycle hook for SavedFixtureSession.

    Parent still owns the session and final stop. No source/pack replacement is
    performed here; config must already describe this copied historical world.
    """
    def __init__(self, state, cfg, out, run_id, request_path, *, record_stop, record_start):
        self.state, self.cfg, self.out, self.run_id = state, cfg, out, run_id
        self.request_path = Path(request_path)
        self.request_sha256 = digest(self.request_path)
        self.record_stop, self.record_start, self.called = record_stop, record_start, False

    def __call__(self, flow):
        import os
        from dataclasses import replace
        from tools.qianzhen_opening_evidence import source_snapshot, MappedOpeningInput
        from tools.qianzhen_opening_run import verify_owned_session, single_flush, stop_owned, archive_world
        from tools.combat_capture import session as cs, procs
        from tools.combat_capture.xgame import Game
        if self.called:
            raise RuntimeError('保存副本checkpoint已经请求过flush，禁止重试')
        if os.environ.get('MYVILLAGE_CLOUD_PROVIDER') != 'Colab' or not Path('/content').is_dir():
            raise ValueError('保存原生checkpoint只允许显式Colab')
        verify_owned_session(self.state, self.run_id, flow.root)
        request, snap = json.loads(self.request_path.read_text()), source_snapshot(flow.root)
        if (digest(self.request_path) != self.request_sha256 or request['commit'] != snap['commit']
                or request['source_fingerprint'] != snap['fingerprint'] or snap['tracked_status'] or snap['untracked_runtime']):
            raise ValueError('保存副本固定请求/源码身份漂移')
        before = flow.state()
        folder = self.out / 'saved-checkpoint'
        folder.mkdir(exist_ok=False)
        self.called = True
        flushed = single_flush(self.state, flow.root, folder)
        self.record_stop()
        old = dict(self.state['procs']['server'])
        terminated = stop_owned(flow.root, self.run_id)
        if procs.is_alive(old['pid'], old.get('start')):
            raise AssertionError('旧进程仍活着')
        prior_log = folder / 'client-before-restart.log'
        shutil.copy2(flow.c.game.client_log, prior_log)
        archive = archive_world(flow.world, folder)
        required = ['playerdata/' + before['player'] + '.dat', 'data/myvillage_world_sim.dat', 'data/myvillage_qianzhen_scene.dat']
        if not all(p in archive['files'] for p in required):
            raise AssertionError('保存副本停服归档缺原始来源')
        archive['exact_sources'] = {p: archive['files'][p] for p in required}
        new = cs.start(replace(self.cfg, reuse_world=True), wait_timeout=min(1350, max(1, flow.deadline - time.monotonic())), log=lambda _: None)
        verify_owned_session(new, self.run_id, flow.root)
        self.state.clear()
        self.state.update(new)
        flow.c.game = Game(new['display'], Path(new['client_log']), size=(960, 540))
        flow.c.input = MappedOpeningInput(flow.c.game, flow.c.rotation, flow.c.emit, flow.c.username)
        flow.c.offset = 0
        self.record_start(flow.c.game, 1)
        flow.c.input.gate.ready('saved_fixture_rejoined', allow_escape=False)
        after = flow.poll(flow.state, lambda s: s['player'] == before['player'] and s['accepted'] == before['accepted'], 60, '真实保存玩家重入失败')
        result = {'before': before, 'after': after, 'flush': flushed, 'termination': terminated,
                  'archive': archive, 'old_server': old, 'new_server': dict(new['procs']['server']),
                  'client_log_before': str(prior_log), 'request_sha256': self.request_sha256}
        (folder / 'semantics.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
        return result


def prepare_unaccepted_awakened(flow, checkpoint, *, generation_kind):
    """Create B by REAL free cultivation and normal saving, never by NBT writes.

    generation_kind distinguishes current-version persistence from an old-jar
    upgrade fixture; it does not infer historical provenance from current files.
    """
    from tools.qianzhen_opening_flow import awakening_controls, fate_triple
    if generation_kind not in ('current_version_free_cultivation', 'historical_jar_free_cultivation'):
        raise ValueError('必须区分现版保存与历史jar升级的真实生成来源')
    if generation_kind == 'historical_jar_free_cultivation':
        raise ValueError('本builder使用现版设施/status；历史jar须用匹配旧collector真实生成后另交原始保存包，不可冒称旧版本运行')
    initial, inventory = flow.state(), flow.data('Inventory')
    if initial['accepted'] or initial['opening']['supplies']:
        raise ValueError('自由修炼fixture须从真实未接受章节开始')
    flow.use('testing_stele')
    offset = flow.c.offset
    def ui():
        with flow.c.game.client_log.open(errors='replace') as stream:
            stream.seek(offset)
            text = stream.read()
        return awakening_controls(text, flow.c.game.size, flow.options) if 'AWAKENING_UI gui=' in text else None
    _, controls = flow.poll(ui, lambda row: row is not None and row[0]['step'] == 'root' and 'continue' in row[1], 10, '自由测灵真实UI缺失')
    flow.c.input.click_menu('free_awakening_continue', *controls['continue'])
    flow.poll(ui, lambda row: row is not None and row[0]['step'] == 'fates' and 'confirm' in row[1], 8, '自由天命页缺失')
    fates = flow.fates()
    definitions = {'myvillage:' + path.stem: json.loads(path.read_text()) for path in
                   (flow.root / 'src/main/resources/data/myvillage/myvillage/innate_fate').glob('*.json')}
    for slot in fate_triple(fates, definitions):
        _, controls = ui()
        flow.c.input.click_menu('free_fate_' + str(slot), *controls['card_' + str(slot)])
        time.sleep(.2)
    for _ in range(2):
        _, controls = ui()
        flow.c.input.click_menu('free_fates_confirm', *controls['confirm'])
        time.sleep(.3)
    flow.poll(flow.fates, lambda row: row['confirmed'], 10, '自由天命未实际确认')
    flow.c.input.close_menu()
    flow.use('inheritance_stele')
    flow.poll(lambda: flow.data(PROFILE), lambda p: 'myvillage:basic_breathing' in p.get('learned_techniques', {}), 10, '自由传承未实际学会')
    if flow.c.game.pointer_grabbed() is False:
        flow.c.input.close_menu()
    before = flow.data(PROFILE)
    if flow.options.get('key_key.myvillage.start_normal_meditation', 'key.keyboard.v') != 'key.keyboard.v':
        raise ValueError('普通修炼键绑定不同，不擅改')
    flow.c.input.press('v')
    after = flow.poll(lambda: flow.data(PROFILE), lambda p: p['cultivation_progress'] > before['cultivation_progress']
                      or p['stability'] > before['stability'], 90, '自由吐纳没有真实成功结算')
    flow.c.input.press('x')
    final = flow.state()
    if final['accepted'] or final['opening']['supplies'] or final['receipts'] != initial['receipts'] or flow.data('Inventory') != inventory:
        raise AssertionError('自由修炼不应接受章节/发物/写章节收据')
    observed = {'generation_kind': generation_kind, 'before': initial, 'after': final,
                'profile_before_meditation': before, 'profile_after_meditation': after,
                'fates': flow.fates(), 'frame': flow.capture('free-cultivation-still-unaccepted')}
    result = checkpoint(flow)
    if result['after']['accepted'] or result['flush']['requests'] != 1 or result['termination']['status'] != 'terminated':
        raise AssertionError('自由修炼Bfixture没有真实未接状态保存/重入')
    return {'observed': observed, 'checkpoint': result, 'fixture_archive': result['archive'],
            'status': 'captured_fixture_not_upgrade_acceptance'}


def write_awakened_manifest(result, flow, out, attachment):
    """A real stopped ZIP generated by mapped actions, explicitly current version."""
    archive = out / 'saved-checkpoint' / result['fixture_archive']['path']
    if digest(archive) != result['fixture_archive']['sha256']:
        raise ValueError('B实际停服ZIP身份漂移')
    from tools.qianzhen.evidence_candidate import PACK_NAME
    spec = {'schema': 'qianzhen-saved-fixture-v1', 'kind': 'existing_awakened',
            'archive': str(archive.resolve()), 'archive_sha256': digest(archive), 'archive_root': '.',
            'player_name': flow.c.username, 'player_uuid': result['checkpoint']['before']['player'],
            'provenance': {'kind': 'current_version_generated', 'generation_kind': 'current_version_free_cultivation',
                           'source_commit': attachment['source_commit'], 'run_id': attachment['run_id'],
                           'request_sha256': attachment['request_sha256'], 'historical_upgrade_proven': False},
            'resources': {'kind': 'embedded_datapack', 'name': PACK_NAME,
                          'files': flow.candidate.identity['resource_sha256']}}
    path = out / 'awakened-fixture.json'
    path.write_text(json.dumps(spec, ensure_ascii=False, indent=2) + '\n')
    return {'path': path.name, 'sha256': digest(path), 'generation_kind': 'current_version_generated',
            'historical_upgrade_proven': False}
