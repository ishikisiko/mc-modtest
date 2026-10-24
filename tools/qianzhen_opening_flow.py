"""自然开局的真实输入流程与有界路线；不拥有 VM，不提供章节推进命令。"""
from __future__ import annotations

from collections import deque
import base64
import gzip
import io
from itertools import combinations
import json
import math
from pathlib import Path
import re
import time

from tools.qianzhen_opening_evidence import digest, fixture

FACT_ACTIONS = {'confirm_root', 'confirm_inheritance', 'confirm_breath', 'report_work'}
SUPPORTED = {'natural_spawn', 'recruitment_walk', 'voluntary_contract', 'early_teaching',
             'work_reward', 'roof_seed20261024'}
# Missing adversarial/long-running cases are explicit, never inferred from the happy path.
DEFERRED = {
    'legacy_accepted': '需要真实旧 accepted-v1 fixture；本轮未实现旧章全路线回放',
    'existing_awakened': '需要真实既有修行 fixture；本轮未实现成熟玩家交互回归',
    'restart_two_players': '双真实客户端/死亡/跨维度/重入全链尚未实现',
    'optional_personal_story': '离家建设战斗、后到人轮作全链尚未实现',
    'marker_matrix': '八类 marker 原生画面及人物迁移矩阵尚未实现',
}


def selected_scenarios(names):
    known = [s['id'] for s in fixture()['stages']]
    if not names or len(names) != len(set(names)) or set(names) - set(known):
        raise ValueError('场景选择为空、重复或未知')
    return [name for name in known if name in names]


def gui_geometry(size, options):
    """Minecraft calculateScale: actual framebuffer, saved guiScale, Unicode parity."""
    width, height = size
    limit = int(options.get('guiScale', '0'))
    scale = 1
    while scale != limit and width // (scale + 1) >= 320 and height // (scale + 1) >= 240:
        scale += 1
    if options.get('forceUnicodeFont') == 'true' and scale % 2:
        scale += 1
    return scale, (math.ceil(width / scale), math.ceil(height / scale))


def awakening_controls(text, size, options):
    scale, gui = gui_geometry(size, options)
    header = re.compile(r'AWAKENING_UI gui=(\d+)x(\d+) step=(\w+) revision=(\d+) selection=(\d+) confirming=(true|false) confirmed=(true|false) token=(\d+)')
    control = re.compile(r'AWAKENING_UI control=(\w+) x=(-?\d+) y=(-?\d+) w=(\d+) h=(\d+)')
    current, rows = None, {}
    for line in text.splitlines():
        match = header.search(line)
        if match:
            w, h, step, rev, selection, confirming, confirmed, token = match.groups()
            current = dict(gui=(int(w), int(h)), step=step, revision=int(rev), selection=int(selection),
                           confirming=confirming == 'true', confirmed=confirmed == 'true', token=int(token))
            rows = {}
        match = control.search(line)
        if match and current:
            name, x, y, w, h = match.groups()
            x, y, w, h = map(int, (x, y, w, h))
            if w <= 0 or h <= 0 or x < 0 or y < 0 or x + w > gui[0] or y + h > gui[1]:
                raise ValueError('觉醒日志矩形不在当前 GUI 内')
            rows[name] = (round(scale * (x + w / 2)), round(scale * (y + h / 2)))
    if not current or current['gui'] != gui or current['token'] <= 0:
        raise ValueError('没有本次实际尺寸的有效觉醒 UI')
    return current, rows


def fate_triple(state, definitions):
    candidates = state['candidates']
    if len(candidates) != 6 or len(set(candidates)) != 6:
        raise ValueError('真实天命候选必须为六个不同条目')
    for slots in combinations(range(6), 3):
        rows = [definitions[candidates[i]] for i in slots]
        if sum(row['cost'] for row in rows) <= state['budget'] and len({row['group'] for row in rows}) == 3:
            return slots
    raise ValueError('当前真实候选没有合法三选；不重抽或改写天命')


def entrance_receipt(world, candidate):
    from tools.buildgen.nbtread import read_gzipped_nbt
    path = world / 'data/myvillage_qianzhen_entrance.dat'
    _, root = read_gzipped_nbt(str(path))
    data = root['data']
    if data.get('phase') != 'COMPLETE':
        raise ValueError('自然入口未 COMPLETE，不允许管理员 deploy 补救')
    plan, rules = data['plan'], json.loads(data['rules'])
    origin, raw = plan['origin'], plan['route']
    if len(origin) != 3 or len(raw) % 2 or not 2 <= len(raw) <= 2 * rules['max_route_length']:
        raise ValueError('入口记录路线非法')
    route = [(raw[i], origin[1] + 1, raw[i + 1]) for i in range(0, len(raw), 2)]
    if any(sum(abs(a[i] - b[i]) for i in (0, 2)) != 1 for a, b in zip(route, route[1:])):
        raise ValueError('入口记录不连续')
    # New deployments persist schema2 frozen geometry; the older Candidate
    # helper deliberately expects schema1 and cannot validate this new save.
    from tools.qianzhen.story_deployment import saved_deployment
    deployed = saved_deployment(world, candidate.layout, origin)
    if not deployed or deployed['phase'] != 'COMPLETE' or deployed.get('schema') != 2 or not deployed.get('geometry'):
        raise ValueError('本次新世界缺少schema2冻结部署，不允许把旧版读回当通过')
    frozen = frozen_geometry(deployed['geometry'])
    for field in ('id', 'version', 'sites', 'bounds', 'positions', 'targets', 'roads', 'facilities'):
        if frozen.get(field) != candidate.layout.get(field):
            raise ValueError('实际冻结几何与候选不符: ' + field)
    return dict(origin=list(origin), route=route, notice=list(data['notice']), rules=rules,
                receipt_sha256=digest(path), deployment=deployed)


def frozen_geometry(encoded):
    if not isinstance(encoded, str) or not encoded.startswith('gzip:') or len(encoded) > 65000:
        raise ValueError('冻结布局必须有界gzip')
    with gzip.GzipFile(fileobj=io.BytesIO(base64.b64decode(encoded[5:], validate=True))) as stream:
        raw = stream.read(4 * 1024 * 1024 + 1)
    if len(raw) > 4 * 1024 * 1024:
        raise ValueError('冻结布局解压超限')
    return json.loads(raw)


def build_scene(candidate, origin, road):
    from tools.qianzhen.story_assets import graph_for, nbt
    from tools.qianzhen.game_routes import opened_door_graph
    layout, blocks = candidate.layout, {}
    add = lambda p: tuple(p[i] + origin[i] for i in range(3))
    for site in layout['sites']:
        _, _, expanded, _ = nbt.expand_structure(candidate.blueprints[site['id']])
        for pos, block in expanded.items():
            blocks[add([pos[i] + site['origin'][i] for i in range(3)])] = block
    for row in layout['roads']:
        blocks[add(row['pos'])] = row['state']
    # The saved entrance owns its real route; never guess a fixed spawn or town origin.
    for x, y, z in road:
        blocks[x, y - 1, z] = 'minecraft:smooth_sandstone'
    graph = opened_door_graph(graph_for(blocks), blocks)
    positions = {key: {field: add(row[field]) for field in ('pos', 'approach')}
                 for key, row in layout['positions'].items()}
    targets = {row['id']: {**row, 'pos': add(row['pos']), 'approach': add(row['approach'])}
               for row in layout['targets']}
    for row in layout['facilities']:
        if row['id'] in targets:
            raise ValueError('设施与目标重复')
        targets[row['id']] = {**row, 'pos': add(row['pos']), 'approach': add(row['approach'])}
    return graph, blocks, positions, targets


def bounded_path(start, target, neighbours, budget=768):
    start, target = tuple(start), tuple(target)
    parents, queue = {start: None}, deque([start])
    while queue:
        node = queue.popleft()
        if node == target:
            result = []
            while node is not None:
                result.append(node)
                node = parents[node]
            return result[::-1]
        for other in neighbours(node):
            other = tuple(other)
            if sum(abs(other[i] - node[i]) for i in (0, 2)) != 1 or abs(other[1] - node[1]) > 1:
                raise ValueError('路线不能斜行或跳层')
            if other not in parents:
                parents[other] = node
                if len(parents) > budget:
                    raise TimeoutError('有界路线搜索耗尽；不传送、不改地形')
                queue.append(other)
    raise ValueError('实际地形无法连通目标')


def meditation_button(image, scale):
    """Locate real GOLD action outlines; no guessed text width/rail width."""
    image = image.convert('RGB')
    gold, found = (156, 121, 60), []
    for y in range(max(0, image.height - 110 * scale), image.height - 18 * scale + 1):
        x = 0
        while x < image.width:
            if image.getpixel((x, y)) != gold:
                x += 1
                continue
            end = x
            while end < image.width and image.getpixel((end, y)) == gold:
                end += 1
            width, bottom = end - x, y + 18 * scale - 1
            if width >= 40 * scale and bottom < image.height and all(
                    image.getpixel((p, bottom)) == gold for p in range(x, end)) and all(
                    image.getpixel((x, p)) == gold and image.getpixel((end - 1, p)) == gold for p in range(y, bottom + 1)):
                found.append((x, y, width, 18 * scale))
            x = end
    if not found:
        raise ValueError('没有实际渲染的可用普通修炼按钮边框')
    x, y, w, h = min(found, key=lambda r: (r[1], r[0]))
    return x + w // 2, y + h // 2


class OpeningFlow:
    def __init__(self, collector, candidate, root, world, deadline):
        self.c, self.candidate, self.root, self.world, self.deadline = collector, candidate, root, world, deadline
        self.options = dict(line.split(':', 1) for line in (root / 'run-combat-capture/client/options.txt').read_text().splitlines() if ':' in line)
        self.chapter = json.loads((root / 'src/main/resources/data/myvillage/story/qianzhen_opening.json').read_text())
        self.entrance = entrance_receipt(world, candidate)
        self.graph, self.blocks, self.positions, self.targets = build_scene(candidate, self.entrance['origin'], self.entrance['route'])
        self.records, self.checks, self.screens = {}, [], []
        self.walked = 0

    def budget(self):
        if time.monotonic() >= self.deadline:
            raise TimeoutError('流程整体预算耗尽')

    def poll(self, fn, predicate, timeout, label):
        deadline = min(self.deadline, time.monotonic() + timeout)
        while time.monotonic() < deadline:
            value = fn()
            if predicate(value):
                return value
            time.sleep(.15)
        raise TimeoutError(label)

    def data(self, path):
        from tools.alchemy_quality_evidence import read_data
        return read_data(self.c.read(f'data get entity {self.c.username} {path}'))

    def state(self):
        from tools.qianzhen.story_evidence_helpers import status_object
        return status_object(self.c.read(f'myvillage story status {self.c.username}'))

    def snapshot(self, label, state):
        self.c.emit('observation', label=label)
        self.c.readback_id += 1
        row = dict(id=self.c.readback_id, after_sequence=self.c.sequence, player=self.c.username, state=state)
        self.c.readbacks.write(json.dumps(row, ensure_ascii=False) + '\n')
        self.c.readbacks.flush()
        return row

    def check(self, name, passed, observed):
        row = self.snapshot(name, observed)
        self.checks.append(dict(id=name, **{'pass': bool(passed)}, readback_id=row['id'],
                                readback_after_sequence=row['after_sequence'], observation=observed))
        if not passed:
            raise AssertionError(name + ': ' + repr(observed))

    def capture(self, name):
        row = self.c.capture(name)
        self.screens.append(row)
        return row

    def stage(self, name, timeout=12):
        state = self.poll(self.state, lambda s: s['stage'] == name, timeout, '服务器未自动到 ' + name)
        from tools.qianzhen.story_evidence_helpers import client_views
        self.poll(lambda: client_views(self.c.game.client_log.read_text(errors='replace')),
                  lambda rows: bool(rows and rows[-1]['revision'] >= state['revision']), 10, '客户端未应用本次 revision')
        return state

    def look(self, target=None, *, rotation=None):
        self.budget()
        if rotation is None:
            feet = self.data('Pos')
            # The flow never presses sneak/swim and requires real grounded readback.
            # Pose isn't serialized by Entity.saveWithoutId; querying that absent NBT
            # path would incorrectly turn every native camera operation into a failure.
            self.poll(lambda: self.data('OnGround'), bool, 3, '相机必须先落地；禁止猜测腾空眼高')
            # The server evaluates the distance from its anchored eye origin to
            # this player's real feet. Predicate-only execute cannot write state.
            eye = f'execute as {self.c.username} at @s anchored eyes positioned ^ ^ ^ if entity @s[distance=1.615..1.625]'
            if not self.predicate(eye):
                raise ValueError('服务器真实眼高不符合站立1.62；拒绝用猜测相机继续')
            dx, dy, dz = target[0] - feet[0], target[1] - feet[1] - 1.62, target[2] - feet[2]
            rotation = (-math.degrees(math.atan2(dx, dz)), -math.degrees(math.atan2(dy, math.hypot(dx, dz))))
        self.c.input.gate.ready('opening_camera', allow_escape=False)
        for _ in range(32):
            actual = self.c.rotation(5)
            errors = ((rotation[0] - actual[0] + 180) % 360 - 180, rotation[1] - actual[1])
            if max(map(abs, errors)) < .3:
                self.snapshot('real_camera', {'actual': actual, 'target': rotation, 'standing_eye_height': 1.62})
                return
            dx, dy = (round(max(-240, min(240, v / .15))) for v in errors)
            self.c.input.gate.xdo('mousemove_relative', '--', str(dx), str(dy))
            self.c.emit('mapped_input', player=self.c.username, display=self.c.game.display,
                        window=self.c.input.gate.window, pid=int(self.c.input.gate.pid), control='mouse', dx=dx, dy=dy)
            time.sleep(.15)
        raise TimeoutError('实际鼠标相机未收敛')

    def predicate(self, command):
        reply = self.c.read(command)
        if 'Test passed' in reply:
            return True
        if 'Test failed' in reply or 'test failed' in reply:
            return False
        raise ValueError('未知 block predicate 回复: ' + reply)

    def walk_leg(self, point):
        self.budget()
        end, last, stalled = time.monotonic() + 25, None, 0
        while time.monotonic() < min(end, self.deadline):
            pos = self.data('Pos')
            delta = math.hypot(point[0] - pos[0], point[2] - pos[2])
            if delta < .16 and abs(point[1] - pos[1]) < .65:
                self.walked += 1
                return
            if last is not None and math.dist(pos, last) < .015:
                stalled += 1
                if stalled >= 4:
                    raise AssertionError('真实步行受阻；保留失败，不 tp/清除方块')
            else:
                stalled = 0
            last = pos
            self.look([point[0], pos[1] + 1.62, point[2]])
            # look() already performed the full prime/grab/rotation gate. Repeating
            # it here would prime another mouse delta after camera convergence.
            keys = ['w'] + (['space'] if point[1] > pos[1] + .5 else [])
            seconds = max(.035, min(.6, delta / 4.3 * .8))
            try:
                for key in keys:
                    self.c.input.gate.xdo('keydown', key)
                    self.c.emit('mapped_input', player=self.c.username, display=self.c.game.display, window=self.c.input.gate.window,
                                pid=int(self.c.input.gate.pid), control=key, hold_seconds=seconds)
                time.sleep(seconds)
            finally:
                for key in reversed(keys):
                    self.c.game.xdo('keyup', key, timeout=1)
        raise TimeoutError('单段步行25秒预算耗尽')

    def follow(self, path):
        from tools.qianzhen.game_routes import walk_point, straight_ends
        from tools.qianzhen.story_evidence import route_ends
        for node in (route_ends(path, self.blocks) if any('_door[' in self.blocks.get(tuple(p), '') for p in path) else straight_ends(path)):
            block = self.blocks.get(tuple(node), '')
            if '_door[' in block:
                pos = self.data('Pos')
                target = [node[0] + .5, node[1] + .65, node[2] + .5]
                if math.dist([pos[0], pos[1] + 1.62, pos[2]], target) > 4:
                    raise AssertionError('门前实际距离过远')
                if not self.predicate('execute if block ' + ' '.join(map(str, node)) + ' ' + block.split('[')[0] + '[open=true]'):
                    self.look(target)
                    self.c.execute({'op': 'right_click'})
            self.walk_leg(walk_point(tuple(node), self.blocks))
        self.snapshot('walk_route', {'path': path, 'actual': self.data('Pos'), 'teleports': 0})

    def walk(self, key):
        from tools.qianzhen.story_evidence_helpers import cardinal_path
        destination = self.targets.get(key, self.positions.get(key))
        if destination is None:
            raise ValueError('固定候选无目标: ' + key)
        start = tuple(math.floor(v) for v in self.data('Pos'))
        state = self.state()
        blocked = {tuple(row['position'][k] for k in ('x', 'y', 'z')) for row in state['npc'].values() if row['position']}
        self.follow(cardinal_path(self.graph, start, tuple(destination['approach']), blocked))

    def use(self, key):
        self.walk(key)
        target = self.targets[key]
        expected = target['block']
        if not self.predicate('execute if block ' + ' '.join(map(str, target['pos'])) + ' ' + expected):
            raise AssertionError('真实目标方块与候选不一致: ' + key)
        self.look([v + .5 for v in target['pos']])
        self.c.execute({'op': 'right_click'})

    def actor(self, role, option):
        if option in FACT_ACTIONS:
            raise ValueError('server-only fact 禁止成为玩家选项')
        self.walk(role)
        state = self.state()
        row = state['npc'][role]
        actual = row['position']
        if not actual or row['personId'] < 0:
            raise ValueError('真实命簿人物缺失')
        self.look([actual['x'] + .5, actual['y'] + 1.3, actual['z'] + .5])
        self.c.execute({'op': 'right_click'})
        if option:
            self.c.execute({'op': 'dialogue_option', 'option': option})
        return state

    def natural_spawn(self):
        from tools.buildgen.nbtread import read_gzipped_nbt
        _, data = read_gzipped_nbt(str(self.world / 'level.dat'))
        settings = data['Data']['WorldGenSettings']
        kind = settings['dimensions']['minecraft:overworld']['generator']['type']
        self.check('normal_world_spawn', settings['seed'] == 20261024 and kind != 'minecraft:flat',
                   {'seed': settings['seed'], 'generator': kind, 'spawn': [data['Data'][k] for k in ('SpawnX', 'SpawnY', 'SpawnZ')], 'actual': self.data('Pos')})
        inventory = self.data('Inventory')
        self.check('empty_inventory', inventory == [], {'inventory': inventory})
        self.check('survival_mode', self.predicate(f'execute if entity {self.c.username}[gamemode=survival]'), {'player': self.c.username})
        state = self.state()
        self.initial = state
        self.check('chapter_unaccepted', state['stage'] == 'new' and not state['accepted'], state)
        self.c.input.gate.ready('natural_spawn_no_forced_screen', allow_escape=False)
        self.check('no_forced_story_screen', self.c.input.gate.state().ingame, {'grabbed_ingame': True, 'state': state})
        before = self.data('Pos')
        self.c.input.hold('w', .15)
        after = self.data('Pos')
        self.check('free_survival_input', math.dist(before, after) > .02 and not self.state()['accepted'], {'before': before, 'after': after})
        self.capture('natural-spawn')

    def spawn_to_road(self):
        start = tuple(math.floor(v) for v in self.data('Pos'))
        target = self.entrance['route'][0]
        # Real per-column predicates cover the untouched spawn buffer, with no terrain writes.
        radius = self.entrance['rules']['spawn_buffer'] + 20
        cache = {}
        def standing(node):
            if node in cache:
                return cache[node]
            x, y, z = node
            if abs(x - start[0]) + abs(z - start[2]) > radius or abs(y - start[1]) > 8:
                return False
            air = f'execute if block {x} {y} {z} minecraft:air if block {x} {y + 1} {z} minecraft:air'
            if not self.predicate(air):
                cache[node] = False
                return False
            cache[node] = any(self.predicate(f'execute if block {x} {y - 1} {z} {block}') for block in (
                '#minecraft:dirt', '#minecraft:base_stone_overworld', 'minecraft:smooth_sandstone', 'minecraft:sand', 'minecraft:gravel'))
            return cache[node]
        def neighbours(node):
            x, y, z = node
            # Bias toward the actual target while retaining bounded BFS, never a guessed coordinate.
            options = [(x + dx, y + dy, z + dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)) for dy in (0, 1, -1)]
            return [p for p in sorted(options, key=lambda p: math.dist(p, target)) if standing(p)]
        path = bounded_path(start, target, neighbours, 1400)
        self.follow(path)

    def recruitment_walk(self):
        self.spawn_to_road()
        self.check('walk_from_spawn', self.walked > 0, {'walked_legs': self.walked, 'entrance': self.entrance})
        notice = self.entrance['notice']
        self.look([v + .5 for v in notice])
        self.c.execute({'op': 'right_click'})
        self.poll(lambda: self.c.game.client_log.read_text(errors='replace'), lambda s: self.entrance['rules']['recruitment'] in s, 8, '真实招募木牌未显示')
        self.check('discover_recruitment', not self.state()['accepted'], {'notice': notice, 'screen': self.capture('recruitment')})
        self.check('recruitment_does_not_accept', not self.state()['accepted'], self.state())
        # Click the actual gold chat link. Even the public tracking command is
        # never submitted through RCON or typed as a replacement for this UI.
        gate = self.c.input.gate
        gate.ready('track_chat', allow_escape=False)
        gate.xdo('key', 't')
        gate.menu('track_chat_open')
        frame = self.capture('track-chat-link')
        from PIL import Image
        scale, (_, gh) = gui_geometry(self.c.game.size, self.options)
        with Image.open(self.c.out / frame['path']).convert('RGB') as image:
            hits = [(x, y) for y in range(scale * (gh - 49), scale * (gh - 40))
                    for x in range(8 * scale, min(180 * scale, image.width)) if image.getpixel((x, y)) == (255, 170, 0)]
        if len(hits) < 8:
            raise ValueError('实际聊天末行没有招募金色链接，拒绝猜坐标或命令代点')
        self.c.input.click_menu('actual_chat_recruitment_link', *hits[len(hits) // 2])
        # Clicking RUN_COMMAND normally keeps chat open; close only through its menu gate.
        if self.c.game.pointer_grabbed() is False:
            self.c.input.close_menu()
        self.c.input.gate.ready('after_track', allow_escape=False)
        tracked = self.poll(self.state, lambda state: state['target']['personId'] == state['npc']['guide']['personId']
                            and state['target']['personId'] >= 0 and state['target']['position'] is not None
                            and not state['accepted'], 8, '真实聊天追踪点击未被服务器应用')
        self.snapshot('recruitment_tracking_applied', tracked)
        self.follow(self.entrance['route'])
        self.check('actual_town_reached', math.dist(self.data('Pos'), [self.entrance['route'][-1][0] + .5, self.entrance['route'][-1][1], self.entrance['route'][-1][2] + .5]) < 1, self.state())
        self.actor('guide', 'learn')
        self.check('cheng_yan_introduction', not self.state()['accepted'], {'story': self.state(), 'frame': self.capture('guide-introduction')})
        self.c.input.close_menu()
        self.foundation_views()

    def foundation_views(self):
        # Runtime block-column predicates and exterior camera, on the actual natural deployment.
        # The production column receipt, when present, is preserved as raw evidence too.
        origin = self.entrance['origin']
        nodes = [self.entrance['route'][0], self.entrance['route'][len(self.entrance['route']) // 2], self.entrance['route'][-1]]
        samples = []
        for x, y, z in nodes:
            samples.append({'x': x, 'z': z, 'road': self.predicate(f'execute if block {x} {y - 1} {z} minecraft:smooth_sandstone'),
                            'below': [self.predicate(f'execute if block {x} {y - depth} {z} minecraft:air') for depth in range(2, 7)]})
        self.snapshot('natural_foundation_columns', {'origin': origin, 'samples': samples, 'receipt': self.entrance['receipt_sha256']})
        self.look([origin[0], origin[1] - 2, origin[2]])
        self.capture('natural-entrance-foundation-exterior')
        if any(not row['road'] or row['below'][0] for row in samples):
            raise AssertionError('实际入口路面不匹配或其紧邻地基为空气')
        bounds = self.candidate.layout['bounds']
        # Sample the prepared margin, outside template floors, on all four sides.
        lo, hi = bounds['min'], bounds['max']
        middle_x, middle_z = (lo[0] + hi[0]) // 2, (lo[2] + hi[2]) // 2
        edges = {'west': (lo[0] - 2, middle_z), 'east': (hi[0] + 2, middle_z),
                 'north': (middle_x, lo[2] - 2), 'south': (middle_x, hi[2] + 2)}
        for name, (local_x, local_z) in edges.items():
            x, z = local_x + origin[0], local_z + origin[2]
            column = [{'y': origin[1] - depth,
                       'air': self.predicate(f'execute if block {x} {origin[1] - depth} {z} minecraft:air'),
                       'water': self.predicate(f'execute if block {x} {origin[1] - depth} {z} minecraft:water'),
                       'lava': self.predicate(f'execute if block {x} {origin[1] - depth} {z} minecraft:lava')}
                      for depth in range(1, 4)]
            self.snapshot('natural_town_foundation_' + name, {'x': x, 'z': z, 'column': column})
            if any(row['air'] or row['water'] or row['lava'] for row in column):
                raise AssertionError('镇外侧真实基础存在空层/液体: ' + name)
            from tools.qianzhen.story_evidence_helpers import cardinal_path
            start = tuple(math.floor(v) for v in self.data('Pos'))
            # Reach a connected outer viewpoint through the real scene graph.
            options = sorted(self.graph, key=lambda p: math.hypot(p[0] - x, p[2] - z))[:30]
            route = None
            for point in options:
                try:
                    route = cardinal_path(self.graph, start, point)
                    break
                except ValueError:
                    continue
            if route is None:
                raise ValueError('镇外侧观察位不可达: ' + name)
            self.follow(route)
            self.look([x + .5, origin[1] - 5, z + .5])
            self.capture('natural-town-foundation-' + name)
        self.walk('guide')

    def voluntary_contract(self):
        before = self.actor('pharmacist', 'learn')
        self.check('terms_visible_before_accept', not before['accepted'], {'state': self.state(), 'frame': self.capture('informed-terms')})
        self.check('no_accept_before_click', not self.state()['accepted'], self.state())
        self.c.execute({'op': 'dialogue_option', 'option': 'accept'})
        after = self.stage('testing')
        self.check('accept_by_physical_dialogue', after['accepted'] and after['definition_version'] == 2, after)
        self.check('same_worldsim_people', before['npc'] == after['npc'], {'before': before['npc'], 'after': after['npc']})
        # The real dialogue may remain open after updating; close through menu gate.
        if self.c.game.pointer_grabbed() is False:
            self.c.input.close_menu()
        self.capture('accepted-testing')

    def fates(self):
        reply = self.c.read(f'myvillage cultivation fate status {self.c.username}')
        rows = [json.loads(line[6:]) for line in reply.splitlines() if line.startswith('FATES ')]
        if len(rows) != 1:
            raise ValueError('真实 FATES 读回缺失')
        return rows[0]

    def early_teaching(self):
        baseline = self.data('"neoforge:attachments"."myvillage:cultivation_profile"')
        self.use('testing_stele')
        offset = self.c.offset
        def ui():
            with self.c.game.client_log.open(errors='replace') as stream:
                stream.seek(offset)
                text = stream.read()
            if 'AWAKENING_UI gui=' not in text:
                return None
            return awakening_controls(text, self.c.game.size, self.options)
        current, controls = self.poll(ui, lambda row: row is not None and row[0]['step'] == 'root' and 'continue' in row[1], 10, '本次测灵 UI 缺失')
        self.check('actual_testing_stele', self.fates()['awakening_session'] > 0, {'ui': current, 'frame': self.capture('testing-stele')})
        self.c.input.click_menu('awakening_continue', *controls['continue'])
        self.poll(ui, lambda row: row is not None and row[0]['step'] == 'fates' and 'confirm' in row[1], 8, '天命页未显示')
        state = self.fates()
        definitions = {'myvillage:' + path.stem: json.loads(path.read_text()) for path in
                       (self.root / 'src/main/resources/data/myvillage/myvillage/innate_fate').glob('*.json')}
        for slot in fate_triple(state, definitions):
            _, controls = ui()
            self.c.input.click_menu('fate_card_' + str(slot), *controls['card_' + str(slot)])
            time.sleep(.2)
        for _ in range(2):
            _, controls = ui()
            self.c.input.click_menu('fate_confirm', *controls['confirm'])
            time.sleep(.3)
        confirmed = self.poll(self.fates, lambda s: s.get('confirmed') is True, 10, '天命未真实确认')
        self.check('root_and_fate_confirmed', confirmed['confirmed'], {'fates': confirmed, 'frame': self.capture('fates-confirmed')})
        self.c.input.close_menu()
        self.stage('inheritance')
        self.use('inheritance_stele')
        after = self.stage('first_breath')
        self.check('actual_inheritance_stele', after['stage'] == 'first_breath', after)
        profile = self.data('"neoforge:attachments"."myvillage:cultivation_profile"')
        self.check('basic_core_available', 'myvillage:basic_breathing' in profile.get('learned_techniques', {}), profile)
        self.c.input.press('h')
        self.poll(self.c.game.pointer_grabbed, lambda value: value is False, 5, '真实 H 菜单未打开')
        scale, (gw, gh) = gui_geometry(self.c.game.size, self.options)
        left, top = (gw - min(480, gw - 8)) // 2, (gh - min(282, gh - 8)) // 2
        self.c.input.click_menu('H_meditation_tab', scale * (left + 15), scale * (top + 36 + 21 + 9))
        time.sleep(.4)
        frame = self.capture('H-normal-before')
        from PIL import Image
        with Image.open(self.c.out / frame['path']) as image:
            button = meditation_button(image, scale)
        self.c.input.click_menu('H_real_normal_button', *button)
        self.c.input.close_menu()
        state = self.stage('work_offer', 90)
        self.c.input.press('x')
        self.check('meditation_settlement', state['opening']['breath_settled'] and not state['opening']['experienced'], state)
        self.check('teaching_before_work', not state['opening']['supplies'] and state['opening']['planted'] == 0, state)
        final = self.data('"neoforge:attachments"."myvillage:cultivation_profile"')
        self.check('no_granted_spell_or_realm', 'realm_id' in final and 'realm_id' in baseline and final['realm_id'] == baseline['realm_id'] and
                   set(final.get('learned_techniques', {})) - set(baseline.get('learned_techniques', {})) <= {'myvillage:basic_breathing'}, {'before': baseline, 'after': final})
        self.capture('first-breath-work-offer')

    def work_reward(self):
        self.actor('pharmacist', 'take_supplies')
        state = self.stage('planting')
        self.check('seedlings_received_once', state['opening']['supplies'], {'story': state, 'inventory': self.data('Inventory')})
        if self.c.game.pointer_grabbed() is False:
            self.c.input.close_menu()
        item, needed = self.chapter['work']['item'], self.chapter['work']['count']
        inventory = self.data('Inventory')
        slots = [s['Slot'] for s in inventory if s['id'] == item and 0 <= s['Slot'] <= 8]
        if len(slots) != 1:
            raise ValueError('自然种苗未位于唯一快捷栏位置，拒绝猜测或命令换物')
        self.c.input.press(str(slots[0] + 1))
        center = self.targets['apprentice_plot']['pos']
        soils = [p for p, block in self.blocks.items() if block.split('[')[0] == 'myvillage:spirit_soil' and math.dist(p, center) <= self.chapter['work']['radius']]
        used = []
        for soil in sorted(soils, key=lambda p: math.dist(p, center)):
            if len(used) == needed:
                break
            crop = (soil[0], soil[1] + 1, soil[2])
            if not self.predicate('execute if block ' + ' '.join(map(str, crop)) + ' minecraft:air'):
                continue  # Preserve growing crops, never clear or accelerate them.
            candidates = [p for p in self.graph if math.dist([p[0] + .5, p[1] + 1.62, p[2] + .5], [soil[0] + .5, soil[1] + 1, soil[2] + .5]) < 3.8 and p != crop]
            start = tuple(math.floor(v) for v in self.data('Pos'))
            from tools.qianzhen.story_evidence_helpers import cardinal_path
            route = None
            for approach in sorted(candidates, key=lambda p: math.dist(p, start)):
                try:
                    route = cardinal_path(self.graph, start, approach)
                    break
                except ValueError:
                    continue
            if route is None:
                continue
            self.follow(route)
            self.look([soil[0] + .5, soil[1] + 1, soil[2] + .5])
            self.c.execute({'op': 'right_click'})
            before = len(used)
            state = self.poll(self.state, lambda s: s['opening']['planted'] == before + 1, 8, '真实播种未提交个人格收据')
            used.append(soil)
            self.capture('planted-' + str(len(used)))
        self.check('three_distinct_soil_placements', len(set(used)) == needed, {'positions': used, 'story': state})
        self.stage('report')
        self.actor('pharmacist', 'claim_reward')
        final = self.stage('complete')
        from tools.qianzhen.story_evidence_helpers import reward_counts
        counts = reward_counts(self.data('Inventory'), self.chapter['rewards'])
        self.check('reward_matches_config', counts == {r['item']: r['count'] for r in self.chapter['rewards']}, {'counts': counts, 'config': self.chapter['rewards'], 'state': final})
        self.capture('work-reward-complete')
        # Keep fixture requirements intact: happy-path completion is NOT full work_reward acceptance.
        raise NotImplementedError('已执行3格与领奖；满包pending/重复领取/双人归因/真实成熟轮作尚无完整原生分支，work_reward保持failed/unrun')

    def roof_seed20261024(self):
        self.check('candidate_seed_20261024', self.candidate.identity['root_seed'] == 20261024, self.candidate.identity)
        self.candidate.verify_installed(self.world)
        self.check('candidate_pack_hashes', bool(self.candidate.pack_hashes), self.candidate.pack_hashes)
        self.walk('water_sample')
        target = self.targets['water_sample']['pos']
        self.look([target[0] + .5, target[1] + 2, target[2] + .5])
        self.capture('roof-inside-uncut')
        # Representative exterior must be on the actual authored reachable graph.
        nodes = [p for p in self.graph if 5 < math.dist(p, target) < 10 and abs(p[1] - target[1]) < 3]
        if not nodes:
            raise ValueError('候选无可达棚外观察位置')
        from tools.qianzhen.story_evidence_helpers import cardinal_path
        start = tuple(math.floor(v) for v in self.data('Pos'))
        routes = []
        for point in nodes:
            try:
                routes.append(cardinal_path(self.graph, start, point))
            except ValueError:
                continue
        if not routes:
            raise ValueError('棚外观察位不连通')
        self.follow(min(routes, key=len))
        self.look([target[0] + .5, target[1] + 3, target[2] + .5])
        self.check('uncut_roof_support_views', True, {'frame': self.capture('roof-exterior-uncut'), 'interpretation': 'requires_raw_artifact_review'})
        self.walk('water_sample')
        self.check('walk_under_roof', True, {'actual': self.data('Pos'), 'frame': self.capture('roof-walk-under')})
        self.check('water_target_reachable', math.dist(self.data('Pos'), [v + .5 if i != 1 else v for i, v in enumerate(self.targets['water_sample']['approach'])]) < 1, {'actual': self.data('Pos'), 'target': self.targets['water_sample']})
