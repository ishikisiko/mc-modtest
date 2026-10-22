#!/usr/bin/env python3
"""玄枢炼器鼎的独立云端采集器；不会自行创建或停止 Colab VM。

父运行器先准备固定源/依赖。示例：
  python3 tools/forging_array_evidence.py --cases infusion --out reports/array/attempt-1
每次使用空输出目录，保留旧失败；case 可单独重跑。Session 拥有自己的临时
world，只有 lifecycle 重启复用它。RCON 仅布置 fixture、控制测试时钟、读取状态；
放入、取出、按钮、拆除、修补全部由真实客户端鼠标/键盘执行。
"""
from __future__ import annotations

import argparse
from collections import Counter
from copy import deepcopy
import json
import math
from pathlib import Path
import secrets
import subprocess
import sys
import time
import zipfile

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))
from tools.alchemy_evidence import AlchemyEvidence
from tools.alchemy_quality_evidence import SecondClient, quality_stacks, read_data
from tools.combat_capture import procs, scene, session as cs

CASES = ('structure', 'infusion', 'lifecycle', 'two_players', 'visual')
ORIGIN = (0, -59, 3)
VECTORS = {'north': (0, -1), 'east': (1, 0), 'south': (0, 1), 'west': (-1, 0)}
MENU = (316, 248)
POINTS = {'input': (158, 59), 'output': (276, 59), 'start': (186, 128),
          'cancel': (269, 128), 'sharp': (170, 103), 'channeling': (225, 103),
          'tough': (280, 103), 'layer_next': (122, 35)}
MARK, TEMPER = 'myvillage:forge_mark', 'myvillage:temper'
BAG, LEDGER = 'myvillage:spirit_stone_bag', 'myvillage:spirit_stone_balance'


def select_cases(value):
    if value == 'all':
        return list(CASES)
    rows = value.split(',') if isinstance(value, str) else list(value)
    if not rows or len(set(rows)) != len(rows) or not set(rows) <= set(CASES):
        raise ValueError('Unknown, empty or duplicate forging-array cases: '+str(value))
    return [case for case in CASES if case in rows]


def position(pos):
    return ' '.join(format(n, '.8g') for n in pos)


def world_pos(origin, facing, cell):
    fx, fz = VECTORS[facing]
    return (origin[0]-fz*cell['x']-fx*cell['z'], origin[1]+cell['y'],
            origin[2]+fx*cell['x']-fz*cell['z'])


def mesh_pos(origin, facing, xyz):
    # Renderer: controller + (.5, -1, .5) + back*2, then rotate mesh.
    return world_pos((origin[0]+.5, origin[1]-1, origin[2]+.5), facing,
                     dict(x=xyz[0], y=xyz[1], z=xyz[2]+2))


def menu_point(size, x, y):
    width, height = size
    scale = max(1, min(width//320, height//240))
    return (scale*((width//scale-MENU[0])//2+x),
            scale*((height//scale-MENU[1])//2+y))


def inventory_point(slot):
    if type(slot) is not int or not 0 <= slot <= 35:
        raise ValueError('Expected player inventory slot 0..35')
    return ((152+18*slot, 232) if slot < 9 else
            (152+18*((slot-9) % 9), 174+18*((slot-9)//9)))


def saved_items(data):
    rows = data.get('Items', [])
    if not isinstance(rows, list):
        raise ValueError('Expected saved item list')
    result = {}
    for row in rows:
        require_stack(row)
        slot = row.get('Slot')
        if type(slot) is not int or slot in result:
            raise ValueError('Invalid or duplicate saved slot')
        result[slot] = deepcopy({k: v for k, v in row.items() if k != 'Slot'})
    return result


def require_stack(stack):
    if (not isinstance(stack, dict) or not isinstance(stack.get('id'), str)
            or type(stack.get('count')) is not int or stack['count'] < 1
            or not isinstance(stack.get('components', {}), dict)):
        raise ValueError('Incomplete saved ItemStack: '+repr(stack))
    return stack


def frozen_identity(data, progress=True):
    keys = ('Version', 'Frozen', 'Snapshot', 'Reserved', 'Authorizer', 'Trait', 'Quality', 'Duration')
    if any(k not in data for k in keys):
        raise ValueError('Missing saved infusion identity')
    require_stack(data['Frozen']); require_stack(data['Snapshot'])
    if (data['Version'] != 1 or not isinstance(data['Reserved'], list) or not data['Reserved']
            or not isinstance(data['Authorizer'], list) or len(data['Authorizer']) != 4
            or any(type(n) is not int for n in data['Authorizer'])
            or type(data['Quality']) is not int or not 1 <= data['Quality'] <= 4
            or type(data['Duration']) is not int or data['Duration'] <= 0
            or type(data.get('Progress')) is not int or not 0 <= data['Progress'] < data['Duration']):
        raise ValueError('Invalid saved infusion identity')
    for stack in data['Reserved']:
        require_stack(stack)
    return deepcopy({k: data[k] for k in (*keys, 'Progress') if progress or k != 'Progress'})


def stack_key(stack):
    require_stack(stack)
    return json.dumps({k: v for k, v in stack.items() if k not in ('Slot', 'count')},
                      sort_keys=True, separators=(',', ':'))


def multiset(stacks):
    result = Counter()
    for stack in stacks:
        result[stack_key(stack)] += stack['count']
    return result


def holdings(rows):
    """Expand exact ledger entries, preserving their components and integer quantities."""
    result = []
    for stack in rows.values():
        require_stack(stack)
        if stack['id'] != BAG:
            result.append(stack)
            continue
        for entry in stack.get('components', {}).get(LEDGER, []):
            item = deepcopy(require_stack(entry['item']))
            quantity = int(entry['quantity'])
            if quantity <= 0:
                raise ValueError('Nonpositive bag balance')
            item['count'] = quantity
            result.append(item)
    return multiset(result)


def infusion_costs(rules, workshop, recipe_id, trait):
    recipe = next(r for r in rules['recipes'] if r['id'] == recipe_id)
    if trait not in recipe['traits']:
        raise ValueError('Unsupported recipe trait')
    costs = dict(rules['traits'][trait]['costs'])
    spirit = workshop['spirit_ingredient']
    n = recipe['costs'].get(spirit, 0)
    if n:
        costs[spirit] = costs.get(spirit, 0)+n
    if not costs or any(type(n) is not int or n <= 0 for n in costs.values()):
        raise ValueError('Invalid authored infusion costs')
    return costs


def ingredient_item(reference, root=ROOT, seen=()):
    if not reference.startswith('#'):
        return reference
    if reference in seen:
        raise ValueError('Cyclic ingredient tag')
    namespace, name = reference[1:].split(':', 1)
    path = root/'src/main/resources/data'/namespace/'tags/item'/Path(name+'.json')
    values = json.loads(path.read_text())['values']
    if not values:
        raise ValueError('Empty fixture ingredient tag '+reference)
    candidate = values[0]
    if isinstance(candidate, dict):
        candidate = candidate['id']
    return ingredient_item(candidate, root, (*seen, reference))


def look_at(eye, target):
    dx, dy, dz = (target[i]-eye[i] for i in range(3))
    if dx == dy == dz == 0:
        raise ValueError('Coincident look-at points')
    return math.degrees(math.atan2(-dx, dz)), math.degrees(math.atan2(-dy, math.hypot(dx, dz)))


def collision_boxes(layout, mesh, origin, facing, formed=True):
    """Mirror the Java mesh slicing, including its selectable remnant fallback."""
    result = []
    for cell in layout['cells']:
        if cell['role'] == 'air':
            continue
        pos = world_pos(origin, facing, cell)
        boxes = []
        if formed:
            for lo, hi in mesh['collision_boxes']:
                low = [max(0, lo[i]+(.5, -1, 2.5)[i]-cell[axis])
                       for i, axis in enumerate(('x', 'y', 'z'))]
                high = [min(1, hi[i]+(.5, -1, 2.5)[i]-cell[axis])
                        for i, axis in enumerate(('x', 'y', 'z'))]
                if all(a < b for a, b in zip(low, high)):
                    boxes.append((low, high))
            if not boxes:
                boxes = [((.35, 0, .35), (.65, .125, .65))]
        else:
            boxes = [((0, 0, 0), (1, 1, 1))]
        for lo, hi in boxes:
            a = world_pos((pos[0]+.5, pos[1], pos[2]+.5), facing,
                          dict(x=lo[0]-.5, y=lo[1], z=lo[2]-.5))
            b = world_pos((pos[0]+.5, pos[1], pos[2]+.5), facing,
                          dict(x=hi[0]-.5, y=hi[1], z=hi[2]-.5))
            result.append((pos, tuple(min(x, y) for x, y in zip(a, b)),
                           tuple(max(x, y) for x, y in zip(a, b))))
    return result


def ray_distance(eye, target, lo, hi):
    direction = [target[i]-eye[i] for i in range(3)]
    length = math.sqrt(sum(n*n for n in direction))
    if not length:
        return None
    start, end = 0., math.inf
    for i, delta in enumerate(direction):
        delta /= length
        if abs(delta) < 1e-10:
            if not lo[i] <= eye[i] <= hi[i]:
                return None
        else:
            a, b = sorted(((lo[i]-eye[i])/delta, (hi[i]-eye[i])/delta))
            start, end = max(start, a), min(end, b)
            if start > end:
                return None
    return start


def aim_candidates(layout, mesh, origin, facing, cell, formed=True):
    """Only return reachable rays whose *first* sliced block is the requested cell."""
    target_pos = world_pos(origin, facing, cell)
    boxes = collision_boxes(layout, mesh, origin, facing, formed)
    candidates = []
    # Front and side floor cameras; fixture extends the floor under each one.
    cameras = [world_pos(origin, facing, dict(x=x, y=-1, z=z))
               for x, z in ((.5, -2.5), (-1., -2.), (2., -2.), (-3.5, .5), (3.5, .5),
                            (-3.5, 2.5), (3.5, 2.5), (.5, 6.5))]
    for pos, lo, hi in boxes:
        if pos != target_pos:
            continue
        centre = tuple((a+b)/2 for a, b in zip(lo, hi))
        for feet in cameras:
            eye = (feet[0], feet[1]+1.62, feet[2])
            hits = [(d, p) for p, a, b in boxes
                    if (d := ray_distance(eye, centre, a, b)) is not None]
            if not hits:
                continue
            distance, first = min(hits)
            if first == target_pos and distance <= 4.4:
                yaw, pitch = look_at(eye, centre)
                candidates.append(dict(feet=feet, target=centre, yaw=yaw, pitch=pitch,
                                       hit=target_pos, distance=distance))
    candidates.sort(key=lambda row: row['distance'])
    if not candidates:
        raise ValueError('No unobstructed reachable mesh ray to '+str(target_pos))
    return candidates


class ForgingArrayEvidence(AlchemyEvidence):
    def __init__(self, out, cases):
        out = Path(out)
        if out.exists() and any(out.iterdir()):
            raise ValueError('Use an empty --out directory; preserve previous attempts: '+str(out))
        super().__init__(out, 'zh_cn')
        self.cases = select_cases(cases)
        self.layout = json.loads((ROOT/'src/main/resources/data/myvillage/forging/array.json').read_text())
        self.mesh = json.loads((ROOT/'src/main/resources/assets/myvillage/forging_array/formed_mesh.json').read_text())
        self.forging = json.loads((ROOT/'src/main/resources/data/myvillage/forging/rules.json').read_text())
        self.workshop = json.loads((ROOT/'src/main/resources/data/myvillage/forging/workshop.json').read_text())
        self.recipe = next(r for r in self.forging['recipes'] if r['id'] == 'forged_pickaxe')
        self.trait = self.recipe['traits'][0]
        self.costs = infusion_costs(self.forging, self.workshop, self.recipe['id'], self.trait)
        self.origin, self.facing = ORIGIN, 'north'
        self.recorders, self.owned_session_ids, self.guests = [], [], []
        self.report.update(kind='forging_array', selected=self.cases, completed=[], unrun=list(self.cases),
            case_status={c: 'pending' if c in self.cases else 'not_selected' for c in CASES},
            coverage='full' if self.cases == list(CASES) else 'partial', full_acceptance=False,
            mapped_input=[], native_fixtures=[], screenshots=[], videos=[], termination='pending',
            owner_appearance='not_verified', owner_feel='not_verified', owner_sound='not_verified',
            owner_fps='not_verified', broad_multiplayer='not_verified', visual_review='not_verified',
            server_replay_attack='not_verified',
            method='real mapped mouse/key; RCON fixture/readback; own disposable cloud Session',
            input_item=self.recipe['output'], costs=self.costs,
            product_observations=[
                'WorkshopEquipment.craft 只接收 forging rules 的成品；此采集使用精铁镐，不代表原版铁镐兼容。',
                'cancel 当前允许权限等级2的非owner撤炉；双人用例明确deop旁观者，只验证普通玩家授权。',
                '重复start通过禁用按钮的真实鼠标尝试验证，不宣称恶意网络重放已测。'])

    def check(self, name, passed, detail):
        super().check(name, passed, detail)
        if not passed:
            raise AssertionError(name)
        return True

    def log_input(self, kind, user=None, **detail):
        self.report['mapped_input'].append(dict(kind=kind, actor=user or self.s.username,
                                                case=getattr(self, 'current_case', 'setup'),
                                                wall=time.monotonic(), **detail))
        self.save()

    def start(self, reuse=False):
        session_id = secrets.token_hex(6)
        self.owned_session_ids.append(session_id)
        started = time.monotonic()
        state = cs.start(cs.SessionConfig(session_id=session_id, reuse_world=reuse,
                                         language='zh_cn', lock=str(procs.default_lock_path(ROOT))))
        self.s = cs.Session.attach(ui_check=True)
        self.sessions += 1
        self.report['termination'] = 'pending'
        self.report['phases'].append(dict(phase='restart_setup' if reuse else 'initial_setup',
            session_id=session_id, reuse_own_world=reuse, wall_s=time.monotonic()-started,
            capture_timings=state.get('timings', {})))
        self.save()

    def stop(self):
        state = cs.read_state()
        if not state or state.get('session_id') not in self.owned_session_ids:
            if self.s is not None:
                raise RuntimeError('Refusing to stop an unowned capture Session')
            return
        super().stop()
        stopped = cs.read_state() or state
        alive = [role for role, row in stopped.get('procs', {}).items()
                 if procs.is_alive(row['pid'], row.get('start'))]
        if cs.supervisor_alive(stopped):
            alive.append('supervisor')
        self.report['termination'] = 'confirmed' if not alive else 'failed'
        self.report['phases'].append(dict(phase='termination_readback', alive=alive,
                                          session_id=stopped.get('session_id')))
        self.save()
        if alive:
            raise RuntimeError('Owned capture processes still alive: '+str(alive))

    def inspect(self, origin=None):
        return self.one('myvillage forgingarray inspect '+position(origin or self.origin), 'FORGING_ARRAY')

    def saved(self, origin=None):
        state = self.inspect(origin)
        if not state.get('controller') or not isinstance(state.get('nbt'), str):
            raise ValueError('Missing controller serialization: '+str(state))
        data = read_data('data: '+state['nbt'])
        if not isinstance(data, dict):
            raise ValueError('Expected full controller compound')
        return data

    def inventory(self, user=None):
        return quality_stacks(self.rc('data get entity '+(user or self.s.username)+' Inventory'))

    def close_menu(self, game=None, user=None):
        game = game or self.s.game
        if game is self.s.game and getattr(self, 'presentation_hud_hidden', False):
            game.xdo('key', 'F1'); self.presentation_hud_hidden = False
            self.log_input('restore_hud')
        if game.pointer_grabbed() is False:
            game.xdo('key', 'Escape')
            self.log_input('close_menu', user)
            time.sleep(.25)
        game.require_ingame('forging array world input')

    def click(self, point, *, shift=False, game=None, user=None, label='menu_click'):
        game = game or self.s.game
        if game.pointer_grabbed() is not False:
            raise RuntimeError('Refusing menu click while pointer is grabbed')
        px, py = menu_point(game.size, *point)
        game.xdo('mousemove', str(px), str(py))
        if shift:
            game.xdo('keydown', 'Shift_L')
        try:
            game.xdo('click', '1')
        finally:
            if shift:
                game.xdo('keyup', 'Shift_L')
        self.log_input(label, user, gui=list(point), pixel=[px, py], shift=shift)
        time.sleep(.2)

    def aim(self, candidate, user=None):
        user = user or self.s.username
        self.rc(f'tp {user} '+position(candidate['feet'])+f' {candidate["yaw"]:.6f} {candidate["pitch"]:.6f}')
        time.sleep(.4)

    def rays(self, cell):
        return aim_candidates(self.layout, self.mesh, self.origin, self.facing, cell,
                              formed=self.inspect()['formed'])

    def open(self, game=None, user=None):
        game, user = game or self.s.game, user or self.s.username
        self.close_menu(game, user)
        controller = next(c for c in self.layout['cells'] if c['role'] == 'controller')
        # Deduplicate cameras; subsequent attempts use genuinely different rays.
        attempted = set()
        for candidate in self.rays(controller):
            key = tuple(round(n, 3) for n in (*candidate['feet'], candidate['yaw'], candidate['pitch']))
            if key in attempted:
                continue
            attempted.add(key)
            self.aim(candidate, user)
            game.require_ingame('mapped array open'); game.xdo('click', '3')
            self.log_input('open_controller', user, ray=candidate, attempt=len(attempted))
            time.sleep(.5)
            if game.pointer_grabbed() is False:
                # The two-player fixture runs at 2 TPS. The window can appear before
                # its initial DataSlots reach the client; let two server ticks settle
                # before clicking a button whose active state depends on those fields.
                time.sleep(1.2)
                return
            if len(attempted) >= 6:
                break
        self.capture('open-failed.png', game)
        raise RuntimeError('Mapped controller opening failed after mesh-derived retries')

    def capture(self, name, game=None):
        game = game or self.s.game
        game.xdo('mousemove', '0', '0'); time.sleep(.3)
        window = game.xdo('search', '--name', 'Minecraft').split()[0]
        subprocess.run(['import', '-silent', '-window', window, str(self.out/name)],
                       env=game.env, check=True, timeout=30)
        self.report['screenshots'].append(dict(file=name, case=getattr(self, 'current_case', 'setup'),
                                              review='not_verified'))
        self.save()

    def place_fixture(self, origin=ORIGIN, facing='north', omit=None):
        self.close_menu()
        self.origin, self.facing = origin, facing
        x, y, z = origin
        self.rc(f'forceload add {x-7} {z-7} {x+7} {z+7}')
        for corner in ((x-7, y, z-7), (x+7, y, z+7)):
            self.poll(lambda corner=corner: self.rc('execute if loaded '+position(corner)),
                      lambda reply: 'Test passed' in reply, 20, 'fixture chunks loaded')
        self.rc(f'fill {x-6} {y-1} {z-6} {x+6} {y+5} {z+6} minecraft:air')
        self.rc(f'fill {x-7} {y-2} {z-7} {x+7} {y-2} {z+7} minecraft:smooth_stone')
        for cell in self.layout['cells']:
            if cell['role'] == 'air' or cell == omit:
                continue
            self.rc('setblock '+position(world_pos(origin, facing, cell))+
                    ' myvillage:forging_array_'+cell['role']+f'[facing={facing}]')
        self.report['native_fixtures'].append(dict(origin=origin, facing=facing, omitted=omit,
            source='data/myvillage/forging/array.json', cells=deepcopy(self.layout['cells'])))
        self.save()

    def awaken(self, user=None):
        user = user or self.s.username
        for command in (f'gamemode survival {user}', f'myvillage cultivation awaken {user}',
                        f'myvillage cultivation crafting set {user} forging 100'):
            self.rc(command)

    def give(self, item, n=1, user=None):
        self.rc(f'give {user or self.s.username} {item} {n}')

    def item_fixture(self):
        # 精铁镐 is the registered iron-tier workshop product, not a vanilla
        # minecraft:iron_pickaxe (WorkshopEquipment.craft currently rejects it).
        name = '留名青锋剑' if self.recipe['id'] == 'qingfeng_sword' else '留名精铁镐'
        return self.recipe['output']+'[minecraft:custom_name=\'{"text":"'+name+'"}\',minecraft:damage=37,'+TEMPER+'={experience:17,edge:1,endurance:2,channeling:0,treatment:2}]'

    def materials(self, batches=2, *, bag=False, user=None, missing=None):
        user = user or self.s.username
        for ref, n in self.costs.items():
            if ref == missing:
                continue
            item = ingredient_item(ref)
            if bag and ref == self.workshop['spirit_ingredient']:
                entry = json.dumps([dict(item=dict(id=item, count=1), quantity=str(n*batches))])
                self.give(BAG+'['+LEDGER+'='+entry+',minecraft:custom_data={"myvillage:spirit_bag_auto_collect":false}]', user=user)
            else:
                # A distinctive component makes generic replacement refunds observable.
                self.give(item+'[minecraft:custom_data={array_evidence:"reserved"}]', n*batches, user)

    def prepare(self, *, bag=False, material=True, origin=ORIGIN):
        self.close_menu()
        self.rc('tick unfreeze'); self.rc('tick rate 20')
        self.place_fixture(origin)
        self.rc('kill @e[type=minecraft:item]'); self.rc(f'clear {self.s.username}')
        self.awaken()
        self.give(self.item_fixture())
        if material:
            self.materials(bag=bag)
        # Empty active hand prevents item use from intercepting the menu ray.
        self.s.game.key('9'); self.log_input('select_empty_hand', key='9')
        self.poll(self.inspect, lambda d: d.get('formed') is True, 12, 'formed fixture')
        self.open(); self.insert(self.recipe['output'])
        self.click(POINTS[self.trait], label='select_trait')
        self.poll(self.saved, lambda d: d.get('Trait') == ('sharp', 'channeling', 'tough').index(self.trait),
                  5, 'selected supported trait')
        return saved_items(self.saved())[0]

    def insert(self, item, game=None, user=None):
        slot = next(k for k, row in self.inventory(user).items() if row['id'] == item)
        self.click(inventory_point(slot), shift=True, game=game, user=user, label='shift_insert')
        self.poll(self.saved, lambda d: saved_items(d).get(0, {}).get('id') == item, 6, 'actual input transfer')

    def start_batch(self):
        before = self.inventory()
        self.click(POINTS['start'], label='manual_start')
        data = self.poll(self.saved, lambda d: 'Frozen' in d, 6, 'mapped reserved batch')
        identity = frozen_identity(data, progress=False)
        reserved = multiset(data['Reserved'])
        removed = holdings(before)-holdings(self.inventory())
        self.check('start_reserves_exact_component_stacks', removed == reserved and
                   holdings(before) == holdings(self.inventory())+reserved,
                   dict(before=before, after=self.inventory(), reserved=data['Reserved']))
        expected_counts = Counter()
        for ref, n in self.costs.items():
            expected_counts[ingredient_item(ref)] += n
        actual_counts = Counter()
        for row in data['Reserved']:
            actual_counts[row['id']] += row['count']
        self.check('reserve_matches_authored_infusion_costs', actual_counts == expected_counts,
                   dict(costs=self.costs, reserved=data['Reserved']))
        return identity

    def await_output(self):
        data = self.poll(self.saved, lambda d: saved_items(d).get(1, {}).get('count') == 1,
                         self.layout['duration_ticks']/20+18, 'one finished infusion')
        self.check('completed_consumes_reservation_and_input', 'Frozen' not in data and
                   not data.get('Reserved') and 0 not in saved_items(data), data)
        return saved_items(data)[1]

    def retrieve(self, user=None, game=None):
        user = user or self.s.username
        self.click(POINTS['output'], shift=True, user=user, game=game, label='shift_take_output')
        self.poll(self.saved, lambda d: 1 not in saved_items(d), 6, 'actual output transfer')
        return next(row for row in self.inventory(user).values() if row['id'] == self.recipe['output'])

    def casing(self):
        # Select a front-facing wall cell from authored data, never a stale coordinate.
        cells = [c for c in self.layout['cells'] if c['role'] == 'casing']
        for cell in sorted(cells, key=lambda c: (c['z'], -abs(c['x']), c['y'])):
            try:
                self.rays(cell)
                return cell
            except ValueError:
                pass
        raise RuntimeError('No reachable authored casing cell')

    def mine(self, cell):
        self.close_menu()
        pos = world_pos(self.origin, self.facing, cell)
        self.rc(f'item replace entity {self.s.username} hotbar.8 with minecraft:diamond_pickaxe')
        self.s.game.key('9'); self.log_input('equip_mining_tool', key='9')
        ray = self.rays(cell)[0]; self.aim(ray)
        self.s.game.require_ingame('mapped dismantle'); self.s.game.xdo('mousedown', '1')
        self.log_input('mine_'+cell['role'], ray=ray, position=pos)
        try:
            self.poll(lambda: self.rc('execute if block '+position(pos)+' minecraft:air'),
                      lambda r: 'Test passed' in r, 25, 'real '+cell['role']+' removal')
        finally:
            self.s.game.xdo('mouseup', '1')
        return pos

    def repair(self, cell):
        self.close_menu()
        target = world_pos(self.origin, self.facing, cell)
        # Once broken all component shapes are full cubes. Find a real neighbor
        # face adjacent to the missing cell; the right click places against it.
        occupied = {world_pos(self.origin, self.facing, c): c for c in self.layout['cells']
                    if c['role'] != 'air' and c != cell}
        boxes = collision_boxes(self.layout, self.mesh, self.origin, self.facing, False)
        boxes = [box for box in boxes if box[0] != target]
        choice = None
        for neighbor, ncell in occupied.items():
            delta = tuple(target[i]-neighbor[i] for i in range(3))
            if sum(abs(n) for n in delta) != 1:
                continue
            face = tuple(neighbor[i]+.5+delta[i]*.5 for i in range(3))
            # Camera on the missing cell side, outside the floor footprint.
            for x, z in ((.5, -2.5), (-1., -2.), (2., -2.), (-3.5, .5), (3.5, .5),
                         (-3.5, 2.5), (3.5, 2.5), (.5, 6.5)):
                feet = world_pos(self.origin, self.facing, dict(x=x, y=-1, z=z))
                eye = (feet[0], feet[1]+1.62, feet[2])
                if sum((eye[i]-face[i])*delta[i] for i in range(3)) <= 0:
                    continue
                hits = [(d, p) for p, lo, hi in boxes
                        if (d := ray_distance(eye, face, lo, hi)) is not None]
                if hits and min(hits)[1] == neighbor and min(hits)[0] <= 4.4:
                    yaw, pitch = look_at(eye, face)
                    choice = dict(feet=feet, yaw=yaw, pitch=pitch, target=face)
                    break
            if choice:
                break
        if choice is None:
            raise RuntimeError('No reachable placement support for missing casing '+str(cell))
        self.rc(f'item replace entity {self.s.username} hotbar.8 with myvillage:forging_array_'+cell['role'])
        self.s.game.key('9'); self.aim(choice); self.s.game.xdo('click', '3')
        self.log_input('place_missing_'+cell['role'], ray=choice, position=target)
        self.poll(lambda: self.rc('execute if block '+position(target)+' myvillage:forging_array_'+cell['role']),
                  lambda r: 'Test passed' in r, 6, 'mapped repair block readback')
        self.poll(self.inspect, lambda d: d['formed'], 8, 'mapped repair forms array')
        # Clear the fixture tool rather than replacing an earned item.
        self.rc(f'item replace entity {self.s.username} hotbar.8 with minecraft:air')

    def structure(self):
        for i, facing in enumerate(VECTORS):
            origin = (16+16*i, -59, 3)
            missing = next(c for c in self.layout['cells'] if c['role'] == 'casing')
            self.place_fixture(origin, facing, missing)
            data = self.poll(self.inspect, lambda d: not d['formed'], 8, 'missing '+facing)
            self.check('missing_cell_rejected_'+facing, not data['formed'], data)
            self.rc('setblock '+position(world_pos(origin, facing, missing))+
                    ' myvillage:forging_array_'+missing['role']+'[facing=north]')
            data = self.poll(self.inspect, lambda d: d['formed'], 10, 'repaired '+facing)
            self.check('four_direction_fixture_'+facing, data['formed'], data)
        self.report['structure_scope'] = 'administrator placement and authoritative pattern readback; lifecycle separately maps repair'

    def infusion(self):
        original = self.prepare(bag=True)
        self.check('unmarked_named_damaged_tempered_input', MARK not in original.get('components', {}) and
                   original['components']['minecraft:damage'] == 37 and TEMPER in original['components'] and
                   'minecraft:custom_name' in original['components'], original)
        self.rc('tick freeze')
        try:
            identity = self.start_batch(); after = self.inventory()
            for _ in range(2):
                self.click(POINTS['start'], label='repeat_disabled_start_attempt')
            self.check('repeat_start_reserves_only_once', self.inventory() == after and
                       frozen_identity(self.saved(), False) == identity, self.saved())
            self.capture('infusion-reserved-menu.png')
        finally:
            self.rc('tick unfreeze')
        result = self.await_output()
        for component in ('minecraft:custom_name', 'minecraft:damage', TEMPER):
            self.check('preserves_'+component, result['components'][component] == original['components'][component], result)
        self.check('forging_100_quality_four', result['components'][MARK] == dict(trait=self.trait, quality=4), result)
        self.capture('infusion-finished-menu.png')
        obtained = self.retrieve()
        self.check('shift_return_preserves_finished_stack', obtained == result, obtained)
        self.insert(self.recipe['output']); before = self.saved(); inventory = self.inventory()
        self.click(POINTS['start'], label='already_infused_start_attempt')
        self.check('already_infused_not_accepted', saved_items(self.saved()) == saved_items(before) and
                   'Frozen' not in self.saved() and self.inventory() == inventory, self.saved())
        # No recipe knowledge is granted anywhere in prepare; the ordinary tool
        # pattern is not a prerequisite of manual finished-equipment infusion.
        self.report['ordinary_pattern_requirement'] = 'no learning action or knowledge injection used'
        self.prepare(material=False)
        refs = list(self.costs)
        self.materials(missing=refs[-1]); before, inventory = self.saved(), self.inventory()
        self.click(POINTS['start'], label='missing_material_start')
        self.check('missing_materials_atomic', self.inventory() == inventory and
                   saved_items(self.saved()) == saved_items(before) and 'Frozen' not in self.saved() and
                   self.inspect()['status'] == 7, self.inspect())
        self.close_menu()

    def drops(self):
        reply = self.rc('execute as @e[type=minecraft:item] run data get entity @s Item')
        lines = [line for line in reply.splitlines() if 'data: ' in line]
        if not lines:
            raise ValueError('Missing dropped-item readback: '+reply)
        return [require_stack(read_data(line)) for line in lines]

    def lifecycle(self):
        self.prepare(); self.start_batch(); self.close_menu()
        self.poll(self.saved, lambda d: d.get('Progress', 0) > 2, 5, 'real progress')
        self.rc('tick freeze')
        casing = self.casing(); self.mine(casing); self.rc('tick unfreeze')
        self.poll(self.inspect, lambda d: not d['formed'] and not d['running'], 8, 'broken structure pause')
        paused = self.saved(); identity = frozen_identity(paused); time.sleep(1)
        self.check('missing_casing_pauses_exact_batch', frozen_identity(self.saved()) == identity, self.saved())
        self.capture('lifecycle-paused-world.png')
        self.rc('save-all flush'); self.stop(); self.start(reuse=True)
        restored = self.saved()
        self.check('restart_keeps_progress_frozen_snapshot_reserved_authorizer',
                   frozen_identity(restored) == identity and saved_items(restored) == saved_items(paused), restored)
        self.repair(casing); output = self.await_output()
        self.check('repair_completes_same_frozen_once', output == identity['Frozen'], self.saved())
        time.sleep(.6)
        self.check('one_shot_no_second_batch', saved_items(self.saved()) == {1: output}, self.saved())
        # Finished output must survive real controller dismantling too.
        self.rc('kill @e[type=minecraft:item]')
        controller = next(c for c in self.layout['cells'] if c['role'] == 'controller')
        self.mine(controller)
        dropped = self.drops()
        self.check('breaking_finished_controller_does_not_lose_product',
                   multiset(s for s in dropped if s['id'] == output['id']) == multiset([output]), dropped)
        # Independent active furnace: frozen result must never also drop.
        original = self.prepare(); self.rc('tick freeze')
        try:
            self.start_batch(); active = self.saved(); self.close_menu()
            self.rc('kill @e[type=minecraft:item]'); self.mine(controller)
            dropped = self.drops()
            contents = [s for s in dropped if s['id'] != 'myvillage:forging_array_controller']
            self.check('active_controller_returns_only_original_and_exact_escrow',
                       multiset(contents) == multiset([original, *active['Reserved']]), dropped)
        finally:
            self.rc('tick unfreeze')
        # Cancellation is another manual transaction, returning escrow while
        # leaving the original input in the machine, with no generated product.
        original = self.prepare(bag=True); self.rc('tick freeze')
        try:
            self.start_batch(); active = self.saved(); before = holdings(self.inventory())
            self.click(POINTS['cancel'], label='owner_cancel')
            cancelled = self.saved()
            self.check('owner_cancel_refunds_exact_stacks_keeps_original',
                       holdings(self.inventory()) == before+multiset(active['Reserved']) and
                       saved_items(cancelled) == {0: original} and not cancelled.get('Reserved') and
                       'Frozen' not in cancelled, cancelled)
        finally:
            self.rc('tick unfreeze')
        self.close_menu()
        self.report['restart'] = dict(same_owned_world=True, sessions=self.sessions)
        self.unload_pause()

    def unload_pause(self):
        # Separate remote furnace: main world tickets cannot keep this chunk alive.
        origin = (512, -59, 3)
        self.rc('forceload add 496 -16 528 16')
        self.prepare(origin=origin)
        self.rc('tick rate 2')
        try:
            self.start_batch(); self.close_menu()
            initial = self.saved(); identity = frozen_identity(initial, False)
            # Leaving the area and removing *all* fixture tickets is setup, not
            # a simulated gameplay transaction. Observe actual loaded=false.
            self.rc(f'tp {self.s.username} .5 -60 .5')
            self.rc('forceload remove 496 -16 528 16')
            left = time.monotonic()
            self.poll(lambda: self.rc('execute unless loaded '+position(origin)),
                      lambda reply: 'Test passed' in reply, 55, 'whole active array truly unloaded')
            unloading_wall = time.monotonic()-left
            time.sleep(6)
            self.check('active_chunk_remains_unloaded',
                       'Test passed' in self.rc('execute unless loaded '+position(origin)),
                       dict(origin=origin, unloaded_wait_s=6, fixture_tick_rate=2))
            self.rc('tick freeze'); self.rc('forceload add 496 -16 528 16')
            self.poll(lambda: self.rc('execute if loaded '+position(origin)),
                      lambda reply: 'Test passed' in reply, 20, 'remote controller loaded for readback')
            restored = self.poll(self.saved, lambda d: 'Frozen' in d, 12, 'frozen reload of active array')
            maximum = initial['Progress']+math.ceil(unloading_wall*2)+2
            self.check('unload_preserves_escrow_without_offline_catchup',
                       frozen_identity(restored, False) == identity and
                       initial['Progress'] <= restored['Progress'] <= maximum,
                       dict(before=initial, after=restored, unload_wait_s=unloading_wall,
                            maximum_online_progress=maximum, unloaded_wait_s=6))
            self.report['active_unload'] = dict(status='passed', origin=origin,
                actual_unloaded_predicate=True, fixture_tick_rate=2, offline_catchup=False)
        finally:
            self.rc('tick rate 20'); self.rc('tick unfreeze')
        output = self.await_output()
        self.check('unloaded_batch_completes_once_after_reload', output == identity['Frozen'], self.saved())

    def two_players(self):
        original = self.prepare(); self.close_menu()
        # A low fixture tick rate preserves enough active time to boot SecondClient;
        # product duration and production rules are not changed.
        self.rc('tick rate 2')
        try:
            guest = SecondClient(self); self.guests.append(guest)
            with guest:
                user, game = guest.username, guest.game
                self.awaken(user); self.rc(f'deop {user}'); self.rc(f'clear {user}')
                self.materials(user=user)
                self.open(); self.start_batch(); self.close_menu()
                self.rc(f'tp {self.s.username} -5.5 -60 .5')
                # World view from a second actual client, with a real saved item.
                centre = mesh_pos(self.origin, self.facing, (0, 3.5, 0))
                feet = (-2., -60., .5); eye = (feet[0], feet[1]+1.62, feet[2])
                yaw, pitch = look_at(eye, centre)
                self.rc(f'tp {user} '+position(feet)+f' {yaw} {pitch}')
                time.sleep(.8); self.capture('two-players-guest-real-item.png', game)
                state = self.saved(); frozen = frozen_identity(state, False); owner_before = self.inventory()
                self.open(game, user); guest_before = self.inventory(user)
                self.click(POINTS['input'], shift=True, game=game, user=user, label='guest_busy_input_shift')
                self.click(POINTS['sharp'], game=game, user=user, label='guest_busy_trait_attempt')
                self.click(POINTS['start'], game=game, user=user, label='guest_busy_start_attempt')
                self.click(POINTS['cancel'], game=game, user=user, label='guest_nonowner_cancel')
                self.check('guest_cannot_mutate_or_cancel_owner_batch', self.inventory(user) == guest_before and
                           self.inventory() == owner_before and frozen_identity(self.saved(), False) == frozen and
                           saved_items(self.saved()).get(0) == original, self.saved())
                self.capture('two-players-guest-busy-menu.png', game)
                self.close_menu(game, user); self.rc(f'tp {user} -5.5 -60 3.5'); self.open()
                self.click(POINTS['input'], shift=True, label='owner_busy_input_shift')
                self.click(POINTS['channeling'], label='owner_busy_trait_attempt')
                self.click(POINTS['start'], label='owner_busy_start_attempt')
                self.check('owner_busy_input_trait_and_restart_locked', self.inventory() == owner_before and
                           frozen_identity(self.saved(), False) == frozen, self.saved())
                self.close_menu(); self.rc(f'tp {self.s.username} -5.5 -60 .5')
                self.rc('tick rate 20'); output = self.await_output()
                self.open(game, user); obtained = self.retrieve(user, game)
                self.check('nonowner_can_collect_finished_once', obtained == output, obtained)
                self.close_menu(game, user); self.rc(f'tp {user} -5.5 -60 3.5'); self.open()
                self.click(POINTS['output'], shift=True, label='owner_second_collection_attempt')
                total = sum(s['count'] for u in (self.s.username, user) for s in self.inventory(u).values()
                            if s['id'] == output['id'])
                self.check('two_players_exactly_one_finished_item', total == 1 and not saved_items(self.saved()),
                           dict(owner=self.inventory(), guest=self.inventory(user), controller=self.saved()))
                self.close_menu()
        finally:
            self.rc('tick rate 20'); self.rc('tick unfreeze')
        self.report['multiplayer'] = 'focused_two_real_clients; broad_multiplayer remains not_verified'

    def camera(self, facing=None):
        facing = facing or self.facing
        feet = world_pos(self.origin, facing, dict(x=3.7, y=2.2, z=-3.4))
        target = mesh_pos(self.origin, facing, (0, 1.6, 0))
        eye = (feet[0], feet[1]+1.62, feet[2]); yaw, pitch = look_at(eye, target)
        self.rc(f'gamemode spectator {self.s.username}')
        self.rc(f'tp {self.s.username} '+position(feet)+f' {yaw} {pitch}')
        if not getattr(self, 'presentation_hud_hidden', False):
            self.s.game.xdo('key', 'F1'); self.presentation_hud_hidden = True
            self.log_input('hide_hud_for_presentation')
        time.sleep(.5)

    def record(self, name, seconds):
        game = self.s.game
        log = (self.out/(name+'.log')).open('w')
        process = subprocess.Popen(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-f', 'x11grab',
            '-draw_mouse', '0', '-video_size', 'x'.join(map(str, game.size)), '-framerate', '20',
            '-i', game.display+'.0+0,0', '-t', str(seconds), '-c:v', 'libx264', '-preset', 'veryfast',
            '-crf', '22', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', str(self.out/name)],
            stdout=log, stderr=subprocess.STDOUT)
        self.recorders.append((process, log)); self.report['videos'].append(name)
        return process

    def visual(self):
        # Display a real cultivation weapon in addition to the tool used by gameplay cases.
        self.recipe = next(r for r in self.forging['recipes'] if r['id'] == 'qingfeng_sword')
        self.trait = self.recipe['traits'][0]
        self.costs = infusion_costs(self.forging, self.workshop, self.recipe['id'], self.trait)
        self.report['visual_equipment'] = self.recipe['output']
        self.prepare(); self.capture('visual-cold-menu.png'); self.close_menu()
        self.camera(); self.capture('visual-cold-day.png')
        self.rc('time set midnight'); time.sleep(2); self.capture('visual-cold-night.png')
        self.rc('time set noon'); self.rc(f'gamemode survival {self.s.username}'); self.open()
        self.start_batch(); self.capture('visual-running-menu.png'); self.close_menu()
        self.camera(); self.capture('visual-running-day.png')
        self.rc('time set midnight'); time.sleep(2)
        self.check('visual_running_uses_real_input', self.inspect()['running'] and
                   saved_items(self.saved()).get(0, {}).get('id') == self.recipe['output'], self.inspect())
        self.capture('visual-running-night.png')
        recording = self.record('visual-running.mp4', 3)
        recording.wait(timeout=15)
        self.check('video_encoded', recording.returncode == 0 and (self.out/'visual-running.mp4').stat().st_size > 1024,
                   dict(file='visual-running.mp4', returncode=recording.returncode))
        self.rc('time set noon'); self.rc(f'gamemode survival {self.s.username}'); self.await_output()
        self.open(); self.retrieve(); self.close_menu()
        # One full four-orientation set, from fixture layouts instead of rotating
        # the camera around a single north-facing machine.
        for facing in VECTORS:
            self.place_fixture(facing=facing); self.poll(self.inspect, lambda d: d['formed'], 8, 'visual facing')
            self.camera(); self.capture('visual-facing-'+facing+'.png')
            self.rc(f'gamemode survival {self.s.username}')
        self.place_fixture(); self.poll(self.inspect, lambda d: d['formed'], 8, 'visual main fixture')
        missing = self.casing(); self.mine(missing)
        self.poll(self.inspect, lambda d: not d['formed'], 8, 'visual missing cell')
        # Use an empty hand to open the unformed controller's full-cube shape.
        self.rc(f'item replace entity {self.s.username} hotbar.8 with minecraft:air')
        self.open()
        for layer in range(-1, 4):
            self.capture('visual-missing-blueprint-layer-'+str(layer)+'.png')
            if layer != 3:
                self.click(POINTS['layer_next'], label='blueprint_next_layer')
        self.close_menu()
        self.report['visual_review'] = 'pending_human_screenshot_and_video_review'
        self.showcase()

    def showcase(self):
        self.place_fixture(); self.poll(self.inspect, lambda d: d['formed'], 8, 'showcase formed')
        self.rc('time set noon'); self.rc('kill @e[type=minecraft:item]')
        for command in ('fill -9 -61 -2 -5 -61 6 minecraft:smooth_stone',
                        'setblock -7 -60 0 myvillage:spirit_testing_stele',
                        'setblock -7 -60 2 myvillage:technique_inheritance_stele',
                        'setblock -7 -60 4 minecraft:chest'):
            self.rc(command)
        supplies = [(self.item_fixture(), 1), ('myvillage:cultivation_handbook', 1), ('minecraft:bread', 64),
                    *[(ingredient_item(k), min(64, n*8)) for k, n in self.costs.items()]]
        counts = Counter(c['role'] for c in self.layout['cells'] if c['role'] != 'air')
        supplies += [('myvillage:forging_array_'+role, n) for role, n in counts.items()]
        for i, (item, n) in enumerate(supplies):
            self.rc(f'item replace block -7 -60 4 container.{i} with {item} {n}')
        stock = quality_stacks(self.rc('data get block -7 -60 4 Items'))
        self.check('showcase_supplies_and_awakening_station_exist', len(stock) == len(supplies) and
                   'Test passed' in self.rc('execute if block -7 -60 0 myvillage:spirit_testing_stele'), stock)
        self.rc('setworldspawn 0 -60 -1 0'); self.rc(f'tp {self.s.username} .5 -60 .5 0 0')
        self.rc('save-all flush'); self.stop(); self.export_world()

    def export_world(self):
        world = cs.SERVER_DIR/cs.LEVEL_NAME
        name = 'forging_array_preview_world.zip'
        with zipfile.ZipFile(self.out/name, 'w', zipfile.ZIP_DEFLATED) as archive:
            for path in sorted(world.rglob('*')):
                rel = path.relative_to(world)
                if path.is_file() and not path.is_symlink() and not any(p in
                        ('session.lock', 'stats', 'advancements') for p in rel.parts):
                    archive.write(path, '玄枢炼器鼎独立展示/'+rel.as_posix())
            archive.writestr('使用说明.txt', '独立展示存档，不覆盖原存档。鼎位于 0 -59 3，朝北。\n'
                '西侧 -7 -60 0 测灵碑用于觉醒；-7 -60 4 箱子有成品、材料及结构部件。\n'
                '右键阵枢脚台打开界面，放入未炼灵成品，选择专长，手动引火。材料来自操作者背包/灵石袋。\n'
                '使用自己的新角色需要觉醒；炼器资质以新角色实际资质为准，测试角色的100不会转移。\n')
        with zipfile.ZipFile(self.out/name) as archive:
            self.check('world_archive_integrity', archive.testzip() is None and
                       any(p.endswith('/level.dat') for p in archive.namelist()), dict(file=name))
        self.report['world_export'] = dict(file=name, isolated_world=True, owner_saves_modified=False,
                                         supplies=[-7, -60, 4], awakening=[-7, -60, 0])
        self.save()

    def run(self):
        self.start()
        for command in (*scene.world_rules(), *scene.player_setup(self.s.username, 'minecraft:air'),
                        'myvillage world pause', 'forceload add -32 -32 96 32'):
            self.rc(command)
        for case in self.cases:
            self.current_case = case; self.report['case_status'][case] = 'running'; self.save()
            try:
                getattr(self, case)()
            except BaseException:
                self.report['case_status'][case] = 'failed'
                try:
                    if self.s:
                        self.capture(case+'-failure.png')
                except Exception as error:
                    self.report['failures'].append(dict(case=case, capture_error=repr(error)))
                raise
            self.report['completed'].append(case); self.report['unrun'].remove(case)
            self.report['case_status'][case] = 'passed'; self.save()
        self.report['status'] = 'passed'; self.save()

    def shutdown(self):
        errors = []
        for process, log in self.recorders:
            try:
                if process.poll() is None:
                    process.terminate(); process.wait(timeout=15)
            except Exception as error:
                errors.append(repr(error))
            finally:
                log.close()
        for guest in self.guests:
            try:
                remaining = [(pid, started, 'forging array owned guest')
                             for pid, started in guest.identities.items() if procs.is_alive(pid, started)]
                if remaining:
                    procs.kill_pids(remaining, grace=8)
                if any(procs.is_alive(pid, started) for pid, started in guest.identities.items()):
                    raise RuntimeError('Owned guest processes remain alive')
            except Exception as error:
                errors.append(repr(error))
        state = cs.read_state()
        if state and state.get('session_id') in self.owned_session_ids:
            try:
                self.stop()
            except Exception as error:
                errors.append(repr(error))
        if errors:
            self.report['termination'] = 'failed'; self.report['status'] = 'failed'
            self.report['failures'].extend(dict(cleanup_error=e) for e in errors)
        elif not self.owned_session_ids or not state or state.get('session_id') not in self.owned_session_ids:
            self.report['termination'] = 'no_owned_session'
        self.save()
        if errors:
            raise RuntimeError('Evidence cleanup failed: '+str(errors))


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--cases', default='all')
    args = parser.parse_args(argv)
    evidence = ForgingArrayEvidence(args.out, select_cases(args.cases))
    try:
        evidence.run()
    except BaseException as error:
        evidence.report['status'] = 'failed'
        evidence.report['failures'].append(dict(case=getattr(evidence, 'current_case', 'startup'), error=repr(error)))
        evidence.save()
        raise
    finally:
        evidence.shutdown()


if __name__ == '__main__':
    main()
