"""可执行扩展流程；所有玩家交互只经各自OpeningFlow/StoryInput。"""
from __future__ import annotations

import json
import math
from pathlib import Path
import re
import time

from tools.qianzhen_opening_extended import (PROFILE, identity, inventory_semantics, item_count,
                                             personal, saved_semantics, validate_path)
from tools.qianzhen.story_evidence_helpers import cardinal_path, dialogue_buttons, reward_counts


def marker_centres(image, scale):
    """Match the actual diamond outline and bright centre, not gold text pixels."""
    image = image.convert('RGB')
    gold, light = (243, 216, 148), (255, 243, 201)
    pixels, centres = image.load(), []
    for y in range(6 * scale, image.height - 6 * scale):
        for x in range(6 * scale, image.width - 6 * scale):
            if pixels[x, y] != light:
                continue
            if all(pixels[x + dx * scale, y + dy * scale] == gold
                   for dx, dy in ((0, -4), (4, 0), (0, 4), (-4, 0), (2, 2), (-2, -2))):
                if not any(math.dist((x, y), p) <= 2 * scale for p in centres):
                    centres.append((x, y))
    return centres


class ExtendedScenarios:
    def __init__(self, session):
        self.s, self.marker = session, {}

    @property
    def a(self):
        return self.s.a

    def note(self, label, detail):
        return self.a.snapshot(label, detail)

    def menu_options(self, flow, role='pharmacist'):
        self.s.closed_menu(flow)
        flow.actor(role, None)
        flow.poll(flow.c.game.pointer_grabbed, lambda g: g is False, 8, '实际人物对话未打开')
        def fresh():
            with flow.c.game.client_log.open(errors='replace') as stream:
                stream.seek(flow.c.offset)
                return dialogue_buttons(stream.read(), flow.c.game.size)
        rows = flow.poll(fresh, bool, 8, '没有本次真实对话布局')
        frame = flow.capture('extended-options-' + flow.c.username)
        result = {'options': [r['option'] for r in rows], 'buttons': rows, 'frame': frame}
        self.note('fresh_options', result)
        return result

    def take_supplies(self, flow):
        before, inventory = flow.state(), flow.data('Inventory')
        if before['stage'] != 'work_offer' or before['opening']['supplies']:
            raise ValueError('领苗负例需要本人未领苗的work_offer')
        flow.actor('pharmacist', 'take_supplies')
        after = flow.stage('planting')
        self.s.closed_menu(flow)
        received = item_count(flow.data('Inventory'), flow.chapter['work']['item']) - item_count(inventory, flow.chapter['work']['item'])
        if received != flow.chapter['work']['count'] or not after['opening']['supplies']:
            raise AssertionError('本人一次领苗不符合配置')
        options = self.menu_options(flow)
        if 'take_supplies' in options['options'] or item_count(flow.data('Inventory'), flow.chapter['work']['item']) - item_count(inventory, flow.chapter['work']['item']) != received:
            raise AssertionError('已领苗仍能重复领取')
        self.s.closed_menu(flow)
        return {'before': before, 'after': after, 'received': received, 'repeat': options}

    def crop_config(self):
        item = self.a.chapter['work']['item']
        namespace, name = item.split(':')
        block = item + '_crop'
        source = self.a.root / f'src/main/resources/assets/{namespace}/blockstates/{name}_crop.json'
        ages = [int(k.removeprefix('age=')) for k in json.loads(source.read_text())['variants']]
        if not ages or sorted(ages) != list(range(max(ages) + 1)):
            raise ValueError('实际作物资源阶段不连续')
        return block, min(ages), max(ages)

    def soils(self):
        center = self.a.targets[self.a.chapter['work']['target']]['pos']
        return sorted([p for p, block in self.a.blocks.items() if block.split('[')[0] == 'myvillage:spirit_soil'
                       and math.dist(p, center) <= self.a.chapter['work']['radius']], key=lambda p: (math.dist(p, center), p))

    def is_crop(self, flow, soil, block):
        crop = (soil[0], soil[1] + 1, soil[2])
        return flow.predicate('execute if block ' + ' '.join(map(str, crop)) + ' ' + block)

    def occupied_crop(self, flow, soil):
        from tools.gen_spirit_crops import HERBS
        for herb in HERBS:
            source = flow.root / f'src/main/resources/assets/myvillage/blockstates/{herb}_crop.json'
            for age in json.loads(source.read_text())['variants']:
                block = f'myvillage:{herb}_crop[{age}]'
                if self.is_crop(flow, soil, block):
                    return {'block': f'myvillage:{herb}_crop', 'age': int(age.removeprefix('age='))}
        raise ValueError('占格实物不是可核对的真实六味灵植；不猜测为空或自动清除')

    def approach_crop(self, flow, soil):
        start = tuple(math.floor(v) for v in flow.data('Pos'))
        target = [soil[0] + .5, soil[1] + 1, soil[2] + .5]
        candidates = [p for p in flow.graph if math.dist([p[0] + .5, p[1] + 1.62, p[2] + .5], target) < 3.8
                      and p != (soil[0], soil[1] + 1, soil[2])]
        for approach in sorted(candidates, key=lambda p: math.dist(p, start)):
            try:
                route = cardinal_path(flow.graph, start, approach)
            except ValueError:
                continue
            flow.follow(route)
            flow.look(target)
            return
        raise ValueError('真实药圃无合法可达站位')

    def select_item(self, flow, item):
        slots = [s['Slot'] for s in flow.data('Inventory') if s['id'] == item and 0 <= s['Slot'] < 9]
        if not slots:
            # E and logged inventory geometry can be added by parent; never admin-move a seed/reward.
            raise ValueError('目标物不在真实快捷栏，禁止命令换物: ' + item)
        flow.c.input.press(str(min(slots) + 1))
        return min(slots)

    def plant(self, flow, soil):
        block, initial, _ = self.crop_config()
        if not self.is_crop(flow, soil, 'minecraft:air'):
            raise AssertionError('只播种合法空格；禁止清除他人幼苗')
        before = flow.state()
        inventory_before = flow.data('Inventory')
        self.select_item(flow, flow.chapter['work']['item'])
        self.approach_crop(flow, soil)
        # The other client really acted earlier; peer receipt is checked after EVERY placement.
        peer = self.s.b if flow is self.a else self.a
        other_before = personal(peer.state())
        peer_inventory_before = inventory_semantics(peer.data('Inventory'))
        flow.c.execute({'op': 'right_click'})
        after = flow.poll(flow.state, lambda s: s['opening']['planted'] == before['opening']['planted'] + 1,
                          8, '真实本人播种未提交独立格收据')
        inventory_after = flow.data('Inventory')
        if (not self.is_crop(flow, soil, f'{block}[age={initial}]') or personal(peer.state()) != other_before
                or inventory_semantics(peer.data('Inventory')) != peer_inventory_before
                or item_count(inventory_before, flow.chapter['work']['item']) - item_count(inventory_after, flow.chapter['work']['item']) != 1):
            raise AssertionError('播种归因/实际作物读回失败')
        frame = flow.capture('plant-' + flow.c.username + '-' + str(after['opening']['planted']))
        row = {'player': flow.c.username, 'soil': soil, 'before': before, 'after': after,
               'peer_before': other_before, 'peer_after': personal(peer.state()), 'frame': frame,
               'inventory_before': inventory_before, 'inventory_after': inventory_after,
               'peer_inventory_before': peer_inventory_before, 'peer_inventory_after': inventory_semantics(peer.data('Inventory'))}
        self.note('personal_plant_witness', row)
        self.s.work.setdefault('plant_events', []).append(row)
        if self.marker and flow is self.a and after['stage'] == 'report':
            self.marker['stage_change'] = {'before': before, 'after': after,
                                           'frame': self.marker_frame(flow, 'marker-auto-report')}
        return row

    def harvest_replant(self, flow, soil):
        block, _, mature = self.crop_config()
        self.approach_crop(flow, soil)
        start = time.monotonic()
        frames = [flow.capture('rotation-growing-' + flow.c.username)]
        self.s.work['rotation_attempt'] = {'soil': soil, 'started': start, 'frames': frames,
                                           'maturity_timeout_seconds': self.s.maturity_timeout}
        self.note('normal_rotation_wait_started', self.s.work['rotation_attempt'])
        # Keep the actual crop loaded with the real client. No tick/growth manipulation.
        flow.poll(lambda: self.is_crop(flow, soil, f'{block}[age={mature}]'), bool,
                  self.s.maturity_timeout, '真实成熟等待耗尽；公共地块无有限完成保证')
        frames.append(flow.capture('rotation-mature-' + flow.c.username))
        inventory = flow.data('Inventory')
        peer = self.s.b if flow is self.a else self.a
        peer_before = personal(peer.state())
        self.approach_crop(flow, soil)
        flow.c.input.gate.ready('mature_crop_harvest', allow_escape=False)
        flow.c.input.gate.xdo('mousedown', '1')
        flow.c.emit('mapped_input', player=flow.c.username, display=flow.c.game.display,
                    window=flow.c.input.gate.window, pid=int(flow.c.input.gate.pid), control='left_click', hold_seconds=.18)
        try:
            time.sleep(.18)
        finally:
            flow.c.game.xdo('mouseup', '1', timeout=1)
        flow.poll(lambda: self.is_crop(flow, soil, 'minecraft:air'), bool, 5, '成熟作物没有被实际收获')
        # Walk to the now empty crop to collect real drops, then back to a safe approach.
        crop = (soil[0], soil[1] + 1, soil[2])
        flow.walk_leg((crop[0] + .5, crop[1], crop[2] + .5))
        harvested = flow.poll(lambda: flow.data('Inventory'),
                             lambda rows: item_count(rows, flow.chapter['work']['item']) > item_count(inventory, flow.chapter['work']['item']),
                             8, '成熟收获掉落未被本人拾取')
        if personal(peer.state()) != peer_before:
            raise AssertionError('正常收获不应改变旁人已付出收据')
        replanted = self.plant(flow, soil)
        return {'soil': soil, 'wait_seconds': time.monotonic() - start, 'frames': frames,
                'inventory_before': inventory, 'harvested': harvested, 'replanted': replanted,
                'growth_fixture_commands': [], 'peer_before': peer_before}

    def claim_once(self, flow, *, full=False):
        rewards = flow.chapter['rewards']
        self.s.closed_menu(flow)
        flow.actor('pharmacist', None)
        self.s.closed_menu(flow)
        before_state = flow.state()
        if before_state['reward'] != 'pending':
            raise ValueError('真实领奖须从pending开始')
        if full:
            full_inventory = self.s.fixture.fill(flow)
            before = reward_counts(full_inventory, rewards)
            peer_before = personal(self.s.b.state())
            flow.actor('pharmacist', 'claim_reward')
            self.s.closed_menu(flow)
            pending = flow.state()
            pending_inventory = flow.data('Inventory')
            if (pending['reward'] != 'pending' or pending['completed'] or personal(pending) != personal(before_state)
                    or reward_counts(pending_inventory, rewards) != before
                    or inventory_semantics(pending_inventory) != inventory_semantics(full_inventory)):
                raise AssertionError('满包必须保留真实pending、收据和库存')
            flow.capture('full-inventory-pending')
            self.a.check('full_inventory_pending', True, {'before': before_state, 'after': pending,
                           'full_inventory': full_inventory, 'after_inventory': pending_inventory,
                           'fixture': self.s.fixture.verify()})
            # Face away, physically drop whole unrelated stacks, walk > pickup radius.
            # Leave as many separate slots as configured reward item kinds.
            empty_needed = len({row['item'] for row in rewards})
            fillers = sorted(slot for player, slot in self.s.fixture.owned if player == flow.c.username and slot < 9)
            if len(fillers) < empty_needed:
                raise ValueError('没有足够快捷栏fixture栈供真实丢出；禁止管理员清槽冒充')
            flow.walk('guide')
            pharmacist = flow.state()['npc']['pharmacist']['position']
            flow.look([pharmacist['x'] + .5, pharmacist['y'] + 1.3, pharmacist['z'] + .5])
            flow.look(rotation=(flow.c.rotation(5)[0] + 180, 0))
            for slot in fillers[:empty_needed]:
                flow.c.input.press(str(slot + 1))
                flow.c.input.gate.ready('physical_drop_fixture_stack', allow_escape=False)
                try:
                    flow.c.input.gate.xdo('keydown', 'Control_L')
                    flow.c.input.gate.xdo('key', 'q')
                    flow.c.emit('mapped_input', player=flow.c.username, display=flow.c.game.display,
                                window=flow.c.input.gate.window, pid=int(flow.c.input.gate.pid), control='ctrl+q')
                finally:
                    flow.c.game.xdo('keyup', 'Control_L', timeout=1)
            # Return to a different reachable town point, away from dropped stacks.
            flow.walk('pharmacist')
            empty = {slot for slot in range(36)} - {r['Slot'] for r in flow.data('Inventory')}
            if len(empty) < empty_needed:
                raise AssertionError('实际丢栈没有留下足够奖励空位')
            if personal(self.s.b.state()) != peer_before:
                raise AssertionError('A满包对话修改了B的个人状态')
            self.note('physical_slots_released', {'empty_slots': sorted(empty), 'fixture_stacks': fillers[:empty_needed]})
        before_inventory = flow.data('Inventory')
        before = reward_counts(before_inventory, rewards)
        peer = self.s.b if flow is self.a else self.a
        peer_before_claim = personal(peer.state())
        peer_inventory_before_claim = inventory_semantics(peer.data('Inventory'))
        flow.actor('pharmacist', 'claim_reward')
        complete = flow.stage('complete')
        self.s.closed_menu(flow)
        counts = reward_counts(flow.data('Inventory'), rewards)
        expected = {r['item']: r['count'] for r in rewards}
        delta = {item: counts[item] - before[item] for item in expected}
        if delta != expected or complete['reward'] != 'claimed':
            raise AssertionError('报酬增量不符合生产配置')
        options = self.menu_options(flow)
        repeat_inventory = reward_counts(flow.data('Inventory'), rewards)
        if 'claim_reward' in options['options'] or repeat_inventory != counts or personal(flow.state()) != personal(complete):
            raise AssertionError('重复会话仍有领奖选项/额外收益/收据变化')
        if personal(peer.state()) != peer_before_claim or inventory_semantics(peer.data('Inventory')) != peer_inventory_before_claim:
            raise AssertionError('本人领奖/重复会话改变旁人收据或背包')
        self.s.closed_menu(flow)
        return {'player': flow.c.username, 'before': before_state, 'after': complete, 'inventory_before': before_inventory,
                'delta': delta, 'config': rewards, 'repeat': options, 'repeat_counts': repeat_inventory,
                'peer_before': peer_before_claim, 'peer_after': personal(peer.state()),
                'peer_inventory_before': peer_inventory_before_claim, 'peer_inventory_after': inventory_semantics(peer.data('Inventory'))}

    def prepare_b(self):
        b = self.s.guest_start()
        if 'prepared_b' in self.s.work:
            return b
        initial = b.state()
        a_before = personal(self.a.state())
        if initial['accepted'] or b.data('Inventory'):
            raise ValueError('第二玩家须为真实新身份空背包，不能复用已完成guest')
        self.s.work['identities'] = [self.s.actor_proof(self.a), self.s.actor_proof(b)]
        self.s.work['b_initial'] = initial
        b.recruitment_walk()
        b.voluntary_contract()
        accepted = b.state()
        b.early_teaching()
        self.s.work['b_costs'] = {'accepted': accepted, 'after_teaching': b.state(), 'profile': b.data(PROFILE), 'fates': b.fates()}
        if personal(self.a.state()) != a_before:
            raise AssertionError('B接受/教学推进了A的个人记录')
        self.s.work['prepared_b'] = True
        return b

    def work_reward(self):
        if self.s.work.get('complete'):
            raise RuntimeError('完整工作场景只能执行一次；不得重复计数')
        a, b = self.a, self.s.guest_start()
        initial_b = b.state()
        if initial_b['accepted'] or b.data('Inventory'):
            raise ValueError('B必须为真实未接取、未获得物品的新玩家')
        self.s.work['b_initial'] = initial_b
        self.s.work['identities'] = [self.s.actor_proof(a), self.s.actor_proof(b)]
        b_position = b.data('Pos')
        b.c.input.hold('a', .15)
        if b.data('Pos') == b_position or b.state()['accepted']:
            raise AssertionError('B未接取时应有真实自由移动且不被A任务代接')
        self.s.work['b_free_survival'] = {'before_position': b_position, 'after_position': b.data('Pos'),
                                        'state': b.state(), 'frame': b.capture('late-B-free-unaccepted')}
        needed = a.chapter['work']['count']
        if needed < 2:
            raise ValueError('当前双人归因流程需要至少两格生产工作；不可静默降级')
        supplies = [self.take_supplies(a)]
        soils = self.soils()
        available = [p for p in soils if self.is_crop(a, p, 'minecraft:air')]
        occupied = [p for p in soils if p not in available]
        occupied_before = {str(p): self.occupied_crop(a, p) for p in occupied}
        if len(available) < 2 * needed - 1:
            self.note('plot_contention_blocked', {'empty': available, 'occupied': occupied, 'guaranteed_completion': False})
            raise TimeoutError('合法空位不足以演示独立播种；保留占地，无清苗/加速补救')
        # A finishes first. B then REALLY accepts, learns, pays seed costs and
        # plants positive counts; an idle peer's zero alone is never the proof.
        placements_a = [self.plant(a, soil) for soil in available[:needed]]
        a.stage('report')
        if b.state()['accepted'] or b.state()['receipts'] != initial_b['receipts']:
            raise AssertionError('A播种不得替未接取的B完成个人约定')
        a.check('three_distinct_soil_placements', len({tuple(p['soil']) for p in placements_a}) == needed,
                {'placements': placements_a, 'required': needed, 'story': a.state()})
        # A's growing crops are not removed by B. Observe a live planted crop before waiting for maturity.
        rotation_soil = placements_a[0]['soil']
        crop, _, mature = self.crop_config()
        growing_preserved = any(self.is_crop(b, rotation_soil, f'{crop}[age={age}]') for age in range(mature + 1))
        occupied_after = {str(p): self.occupied_crop(a, p) for p in occupied}
        unchanged_others = all(occupied_after[key]['block'] == row['block'] and occupied_after[key]['age'] >= row['age']
                              for key, row in occupied_before.items())
        a.check('occupied_plot_not_cleared_automatically', growing_preserved and unchanged_others,
                {'a_initial_age0_witness': placements_a[0], 'preserved': growing_preserved, 'occupied_before': occupied_before,
                 'occupied_after': occupied_after, 'growing_frame': b.capture('other-player-growing-preserved')})
        if 'optional_personal_story' in self.s.selected:
            # Leave with real paid effort and a PENDING reward, then resume the conversation.
            self.s.work['home_excursion'] = self.home_excursion(a)
        paid_a = self.claim_once(a, full=True)
        a.check('reward_matches_config', paid_a['delta'] == {r['item']: r['count'] for r in a.chapter['rewards']}, paid_a)
        a.check('repeat_reward_no_gain', 'claim_reward' not in paid_a['repeat']['options'], paid_a)
        # A is COMPLETE before B's actual informed accept and full teaching.
        self.s.fixture.cleanup((a,))
        b = self.prepare_b()
        self.s.work['a_complete_before_b_teaching'] = a.state()['completed']
        supplies.append(self.take_supplies(b))
        a.check('seedlings_received_once', all(row['after']['opening']['supplies'] for row in supplies), {'actors': supplies})
        placements_b = [self.plant(b, soil) for soil in available[needed:2 * needed - 1]]
        a.check('other_player_work_not_counted', a.state()['opening']['planted'] == needed and b.state()['opening']['planted'] == needed - 1,
                {'a_actions': placements_a, 'b_real_positive_actions': placements_b, 'a': a.state(), 'b': b.state(),
                 'evidence': '两实际客户端均有正数亲手播种/成本/个人收据，并逐次比较另一人的完整持久状态与库存'})
        rotation = self.harvest_replant(b, rotation_soil)
        b.stage('report')
        paid_b = self.claim_once(b)
        a.check('plot_rotation_or_recovery_observed', a.state()['completed'] and b.state()['completed'],
                {'rotation': rotation, 'b_payment': paid_b, 'worst_case': '公共格子可被长期抢占/卸载/破坏；无有限等待保证'})
        self.s.work.update(complete=True, placements_a=placements_a, placements_b=placements_b,
                           supplies=supplies, payment_a=paid_a, payment_b=paid_b, rotation=rotation,
                           a_complete_before_b=True, fixture=self.s.fixture.verify())
        a.capture('two-independent-work-rewards')

    def restart_two_players(self):
        if not self.s.work.get('complete'):
            raise ValueError('先完成双人真实工作，不使用空收据重启冒充')
        a, b = self.a, self.s.b
        proofs = [self.s.actor_proof(a), self.s.actor_proof(b)]
        a.check('two_real_client_identities', len({p['uuid'] for p in proofs}) == 2 and len({p['pid'] for p in proofs}) == 2
                and len({p['display'] for p in proofs}) == 2, {'actors': proofs})
        # Each real completed actor opens their own page while peer state stays unchanged.
        isolation = []
        for flow, peer in ((a, b), (b, a)):
            before = personal(peer.state())
            options = self.menu_options(flow)
            self.s.closed_menu(flow)
            isolation.append({'actor': flow.c.username, 'peer_before': before, 'peer_after': personal(peer.state()), 'options': options})
        a.check('second_player_cannot_accept_or_claim_for_first', all(r['peer_before'] == r['peer_after']
                    and not {'accept', 'claim_reward'} & set(r['options']['options']) for r in isolation), {'sessions': isolation, 'personal_work': self.s.work['identities']})
        a.check('personal_progress_isolated', self.s.work['a_complete_before_b'],
                {'a_placements': self.s.work['placements_a'], 'b_placements': self.s.work['placements_b'], 'b_final_rotation': self.s.work['rotation']})
        a.check('receipts_inventory_isolated', self.s.work['payment_a']['player'] != self.s.work['payment_b']['player'],
                {'a': self.s.work['payment_a'], 'b': self.s.work['payment_b']})
        result = self.s.checkpoint()
        a.check('single_flush_confirmed', result['flush']['requests'] == 1 and result['flush']['status'] == 'confirmed', result['flush'])
        a.check('actual_server_restart', result['terminated']['status'] == 'terminated'
                and (result['old_server']['pid'], result['old_server'].get('start')) != (result['new_server']['pid'], result['new_server'].get('start')),
                {'old': result['old_server'], 'new': result['new_server'], 'termination': result['terminated']})
        rejoined = [self.s.actor_proof(a), self.s.actor_proof(self.s.b)]
        a.check('both_clients_rejoined', {r['uuid'] for r in proofs} == {r['uuid'] for r in rejoined}, {'before': proofs, 'after': rejoined})
        compared = {}
        for user, before in result['before'].items():
            after = result['after'][user]
            semantics = saved_semantics(before['story'], after['story'])
            semantics['inventory_equal'] = before['inventory'] == after['inventory']
            semantics['cultivation_identity_equal'] = all(before['profile'].get(k) == after['profile'].get(k)
                        for k in ('spiritual_root', 'realm_id', 'stage_id', 'learned_techniques', 'active_core_technique'))
            semantics['fate_identity_equal'] = fate_identity(before['fates']) == fate_identity(after['fates'])
            compared[user] = semantics
        a.check('saved_personal_and_worldsim_state_equal', all(all(v for k, v in row.items() if k != 'epochs') for row in compared.values()),
                {'semantic_comparisons': compared, 'raw_state_file': 'restart-checkpoint/semantics.json',
                 'excluded_runtime_fields': ['view revision', 'avatar lease epoch', 'online clocks', 'NPC body position'],
                 'worldsim_scope': '剧情人物身份与shared事实；全部原始命簿保存在停服归档，不宣称全部世界时钟不变'})
        a.check('saved_world_archive_hashes', bool(result['required_saved_files']) and result['archive']['playerdata_present'],
                {'archive': result['archive'], 'exact_saved_sources': result['required_saved_files']})
        a.capture('restart-two-players-restored')

    def home_excursion(self, flow):
        home = self.s.home
        if home.get('schema') != 'qianzhen-personal-home-v1':
            raise ValueError('home声明schema不符')
        outbound = validate_path(home['outbound'])
        back = validate_path(home['return'])
        support, placed = tuple(home['support']), tuple(home['placed'])
        if placed != (support[0], support[1] + 1, support[2]) or not flow.predicate('execute if block ' + ' '.join(map(str, placed)) + ' minecraft:air'):
            raise ValueError('home需真实空放置格及下方实物支撑')
        if not flow.predicate('execute if block ' + ' '.join(map(str, support)) + ' ' + home['support_state']):
            raise ValueError('home支撑实物与声明不一致')
        before = personal(flow.state())
        self.follow_declared(flow, outbound)
        empty_hotbar = next((i for i in range(9) if i not in {r['Slot'] for r in flow.data('Inventory')}), None)
        if empty_hotbar is None:
            raise ValueError('home演示需真实空快捷栏fixture格')
        self.s.fixture.command(flow.c.username, empty_hotbar, True, purpose='independent_home_build_material')
        flow.c.input.press(str(empty_hotbar + 1))
        flow.look([support[0] + .5, support[1] + 1, support[2] + .5])
        flow.c.execute({'op': 'right_click'})
        placed_ok = flow.poll(lambda: flow.predicate('execute if block ' + ' '.join(map(str, placed)) + ' ' + self.s.fixture.ITEM), bool, 5, '真实回家建造未放置')
        if item_count(flow.data('Inventory'), self.s.fixture.ITEM) != 63:
            raise AssertionError('home建造没有消耗本人真实持物')
        home_frame = flow.capture('personal-home-built')
        profile_before = flow.data(PROFILE)
        # Existing mapping V starts normal cultivation; verify actual progress/settlement before X.
        bindings = flow.options
        if bindings.get('key_key.myvillage.start_normal_meditation', 'key.keyboard.v') != 'key.keyboard.v':
            raise ValueError('普通修炼键绑定不同；不擅改玩家设置')
        flow.c.input.press('v')
        profile_after = flow.poll(lambda: flow.data(PROFILE), lambda p: p['cultivation_progress'] > profile_before['cultivation_progress']
                                 or p['stability'] > profile_before['stability'], 90, '在家真实修炼无成功所得')
        flow.c.input.press('x')
        combat = self.home_combat(flow, home)
        self.follow_declared(flow, back)
        after = personal(flow.state())
        if before != after:
            raise AssertionError('暂离建造/修炼改变或中断已完成的个人剧情收据')
        return {'before': before, 'after': after, 'outbound': outbound, 'return': back, 'placed': placed,
                'placed_observed': placed_ok, 'frame': home_frame, 'profile_before': profile_before, 'profile_after': profile_after,
                'build_material_fixture': self.s.fixture.verify(), 'combat': combat}

    def home_combat(self, flow, home):
        probe = self.s.marker_fixture
        if probe is None or not probe.combat_uuid:
            raise ValueError('家园自由战斗须声明真实生物UUID及独立读回probe，不能用无战斗画面算完整check')
        self.follow_declared(flow, validate_path(home['combat_approach']))
        before = probe.combat_body()
        if before['Health'] < 10 or before.get('HurtTime', 0):
            raise ValueError('需健康、未在受伤的独立家园敌对生物；不管理员补血')
        pos = before['Pos']
        if math.dist(flow.data('Pos'), pos) > 3:
            raise ValueError('实际敌对生物不在普通攻击距离')
        flow.look([pos[0], pos[1] + 1, pos[2]])
        before_frame = flow.capture('personal-home-combat-before')
        flow.c.execute({'op': 'left_click'})
        after = flow.poll(probe.combat_body, lambda row: row['Health'] < before['Health'] and row.get('HurtTime', 0) > 0,
                          5, '真实映射攻击未产生生命损失/受伤状态')
        return {'before': before, 'after': after, 'before_frame': before_frame,
                'after_frame': flow.capture('personal-home-combat-after'), 'fixture': probe.provenance(),
                'scope': '真实普通攻击与生命损失；不宣称击杀、长时战斗或平衡已验收'}

    def optional_personal_story(self):
        if not self.s.work.get('complete') or self.s.home is None:
            raise ValueError('可选个人剧情需完整双人工作及home实际路线')
        a, b = self.a, self.s.b
        before = personal(a.state())
        excursion = self.s.work['home_excursion']
        a.check('no_forced_dialogue_or_acceptance', not self.s.work['b_initial']['accepted'],
                {'late_player_initial': self.s.work['b_initial'], 'home_frame': excursion['frame']})
        a.check('no_party_or_deadline_gate', self.s.work['a_complete_before_b'],
                {'a_payment': self.s.work['payment_a'], 'b_later_payment': self.s.work['payment_b'], 'separate_identities': self.s.work['identities']})
        a.check('free_cultivation_farming_building_combat', excursion['placed_observed'] and bool(self.s.work['rotation'])
                and excursion['combat']['after']['Health'] < excursion['combat']['before']['Health'],
                {'cultivation_and_building': excursion, 'farming': self.s.work['rotation'],
                 'combat': excursion['combat']})
        a.check('leave_for_home_and_resume', excursion['before'] == excursion['after'] and excursion['after']['reward'] == 'pending', excursion)
        a.check('paid_effort_persisted', set(excursion['before']['receipts']) <= set(a.state()['receipts']), {'before': excursion['before'], 'after': personal(a.state()), 'home': excursion})
        a.check('other_player_completion_keeps_teaching_and_reward', a.state()['completed'] and b.state()['completed']
                and self.s.work['a_complete_before_b_teaching'],
                {'b_teaching': self.s.work['b_costs'], 'a_complete_before_b_final_work': self.s.work['a_complete_before_b'],
                 'b_reward': self.s.work['payment_b'], 'b_rotated_plot': self.s.work['rotation']})
        a.check('plot_contention_worst_case_recorded', bool(self.s.work['rotation']['frames']),
                {'normal_rotation': self.s.work['rotation'], 'worst_case': '持续占格/抢收/区块卸载/灵土破坏可无限阻塞这份可选工作；不阻断家园或自由修炼',
                 'bounded_maturity_timeout_seconds': self.s.maturity_timeout, 'guaranteed_completion': False})

    def marker_frame(self, flow, label, *, visible=True):
        from PIL import Image
        from tools.qianzhen_opening_flow import gui_geometry
        self.s.closed_menu(flow)
        time.sleep(.3)
        frame = flow.capture(label)
        scale, _ = gui_geometry(flow.c.game.size, flow.options)
        with Image.open(flow.c.out / frame['path']) as image:
            centres = marker_centres(image, scale)
        if (len(centres) != 1 if visible else bool(centres)):
            raise AssertionError({'marker_frame': frame, 'expected_visible': visible, 'actual_centres': centres})
        result = {'frame': frame, 'centres': centres, 'actual_position': flow.data('Pos'),
                  'rotation': flow.c.rotation(5), 'state': flow.state(), 'raw_review': '中文字/距离数字/遮挡观感需父线程审未剖PNG和同步视频'}
        self.note('native_marker_observation', result)
        return result

    def marker_prepare(self):
        """Must run while A has a live work_offer actor target, BEFORE work_reward."""
        a = self.a
        if a.state()['stage'] != 'work_offer':
            raise ValueError('marker前置须在work_offer，禁止重置个人章节')
        mf = self.s.marker_fixture
        # Fixture owns its explicit body diagnostics/displacement, never a player tp.
        self.marker['fixture'] = mf.provenance()
        person = a.state()['target']['personId']
        body = mf.body(person)
        a.walk('pharmacist')
        a.look([body['pos'][0], body['pos'][1] + body['height'] + .4, body['pos'][2]])
        near = self.marker_frame(a, 'marker-near-actual-body')
        self.marker['near'] = near
        width, height = a.c.game.size
        if math.dist(near['centres'][0], (width / 2, height / 2)) > 20:
            raise AssertionError('近处当前身体头顶精确标记未对准真实相机中心')
        rotations = []
        base = near['rotation']
        for yaw in (-110, 110, 180):
            a.look(rotation=(base[0] + yaw, 0))
            frame = self.marker_frame(a, 'marker-edge-' + str(yaw).replace('-', 'minus'))
            rotations.append({'relative_yaw': yaw, **frame})
        # Raw left/right geometry is required; exact arrows/text remain parent visual review.
        if not (rotations[0]['centres'][0][0] > width / 2 and rotations[1]['centres'][0][0] < width / 2
                and rotations[2]['centres'][0][1] > height / 2):
            raise AssertionError('屏外左右/正后下方方向错误')
        self.marker['offscreen'] = rotations
        # Find a REAL opaque wall between graph viewpoint and current loaded body.
        occlusion = self.occluded_view(a, body)
        self.marker['occluded'] = occlusion
        far = self.marker_frame(a, 'marker-distance-far')
        if math.dist(far['actual_position'], body['pos']) <= math.dist(near['actual_position'], body['pos']) + 2:
            raise AssertionError('距离矩阵没有真实移动形成不同距离')
        self.marker['distance'] = {'near': near, 'far': far}
        self.marker['toggle'] = self.tracker_cycle(a)
        self.marker['movement'] = self.npc_movement(a, mf, person)
        self.marker['dimension'] = self.portal_round_trip(a)
        a.walk('pharmacist')

    def occluded_view(self, flow, body):
        target = [body['pos'][0], body['pos'][1] + body['height'] + .4, body['pos'][2]]
        start = tuple(math.floor(v) for v in flow.data('Pos'))
        opaque = {'minecraft:stone', 'minecraft:stone_bricks', 'minecraft:smooth_sandstone', 'minecraft:sandstone',
                  'minecraft:oak_planks', 'minecraft:dark_oak_planks', 'minecraft:spruce_planks', 'minecraft:bricks'}
        candidates = []
        for point in flow.graph:
            eye = [point[0] + .5, point[1] + 1.62, point[2] + .5]
            distance = math.dist(eye, target)
            if not 7 <= distance <= 25:
                continue
            blockers = []
            for i in range(1, math.ceil(distance * 4)):
                ratio = i / math.ceil(distance * 4)
                p = tuple(math.floor(eye[k] + (target[k] - eye[k]) * ratio) for k in range(3))
                state = flow.blocks.get(p, '')
                if state.split('[')[0] in opaque:
                    blockers.append((p, state))
            if blockers:
                candidates.append((point, blockers))
        for point, blockers in sorted(candidates, key=lambda r: math.dist(r[0], start)):
            try:
                route = cardinal_path(flow.graph, start, point)
            except ValueError:
                continue
            if not all(flow.predicate('execute if block ' + ' '.join(map(str, p)) + ' ' + state) for p, state in blockers):
                continue
            flow.follow(route)
            flow.look(target)
            return {'blockers': blockers, 'frame': self.marker_frame(flow, 'marker-through-real-wall')}
        raise ValueError('没有实际完整不透明墙遮挡的可达视角；不造静态图替代')

    def tracker_cycle(self, flow):
        frames, modes = [], []
        for _ in range(3):
            offset = flow.c.game.client_log.stat().st_size
            flow.c.input.press('y')
            def mode():
                with flow.c.game.client_log.open(errors='replace') as stream:
                    stream.seek(offset)
                    found = re.findall(r'QIANZHEN_STORY_TRACKER mode=(\w+)', stream.read())
                return found[-1] if found else None
            current = flow.poll(mode, bool, 5, '实际Y未产生新tracker模式')
            modes.append(current)
            frames.append(self.marker_frame(flow, 'marker-Y-' + current, visible=current != 'HIDDEN'))
        if set(modes) != {'COMPACT', 'EXPANDED', 'HIDDEN'}:
            raise AssertionError('Y没有完成三个真实模式循环')
        flow.c.input.press('o')
        flow.poll(flow.c.game.pointer_grabbed, lambda g: g is False, 5, '真实O行迹未打开')
        raw = flow.capture('marker-journal-hidden')
        from PIL import Image
        from tools.qianzhen_opening_flow import gui_geometry
        with Image.open(flow.c.out / raw['path']) as image:
            if marker_centres(image, gui_geometry(flow.c.game.size, flow.options)[0]):
                raise AssertionError('行迹界面仍绘制世界标记')
        self.s.closed_menu(flow)
        flow.c.input.press('F1')
        try:
            f1 = self.marker_frame(flow, 'marker-F1-hidden', visible=False)
        finally:
            flow.c.input.press('F1')
        return {'modes': modes, 'frames': frames, 'journal_hidden': raw, 'F1_hidden': f1,
                'restored': self.marker_frame(flow, 'marker-Y-restored')}

    def npc_movement(self, flow, mf, person):
        before = mf.body(person)
        mf.displace(person, flow.graph)
        displaced = mf.body(person)
        flow.look([displaced['pos'][0], displaced['pos'][1] + displaced['height'] + .4, displaced['pos'][2]])
        first = self.marker_frame(flow, 'marker-NPC-displaced')
        moved = flow.poll(lambda: mf.body(person), lambda row: row['personId'] == person
                          and row['uuid'] == before['uuid'] and row['on_ground']
                          and abs(row['pos'][1] - displaced['pos'][1]) < .4
                          and math.hypot(row['pos'][0] - displaced['pos'][0], row['pos'][2] - displaced['pos'][2]) >= .6,
                          30, 'NPC未出现真实AI位移；命簿目标日志不能替代身体移动')
        second = self.marker_frame(flow, 'marker-NPC-AI-follow')
        if first['centres'] == second['centres']:
            raise AssertionError('同person身体实际移动后标记画面未变化')
        return {'personId': person, 'before': before, 'fixture_displaced': displaced, 'actual_AI_after': moved,
                'before_frame': first, 'after_frame': second, 'fixture': mf.provenance()}

    def portal_round_trip(self, flow):
        portal = self.s.portal
        if portal.get('schema') != 'qianzhen-physical-portal-v1' or portal.get('player_teleport_commands') != []:
            raise ValueError('portal必须声明真实传送门，不允许玩家tp')
        outbound = validate_path(portal['overworld_entry'])
        retreat = validate_path(portal['nether_clear'])
        reenter = validate_path(portal['nether_reenter'])
        if flow.data('Dimension') != 'minecraft:overworld':
            raise ValueError('portal来回须从主世界开始')
        if not self.s.marker_fixture.is_portal('minecraft:overworld', outbound[-1]):
            raise ValueError('路线末格不是实际主世界传送门，不能tp补救')
        self.follow_declared(flow, outbound)
        flow.poll(lambda: flow.data('Dimension'), lambda d: d == 'minecraft:the_nether', 30, '实际步入传送门未换维度')
        self.follow_declared(flow, retreat)
        other = self.marker_frame(flow, 'marker-other-dimension-hidden', visible=False)
        # Vanilla portal cooldown must expire through actual online time.
        for _ in range(16):
            flow.budget()
            time.sleep(1)
        if not self.s.marker_fixture.is_portal('minecraft:the_nether', reenter[-1]):
            raise ValueError('返程末格不是实际下界传送门')
        self.follow_declared(flow, reenter)
        flow.poll(lambda: flow.data('Dimension'), lambda d: d == 'minecraft:overworld', 30, '实际返程传送门未生效')
        self.follow_declared(flow, validate_path(portal['overworld_clear']))
        restored = self.marker_frame(flow, 'marker-portal-return')
        return {'fixture_provenance': portal['provenance'], 'other_dimension': other, 'restored': restored,
                'player_teleport_commands': []}

    def follow_declared(self, flow, path):
        path = validate_path([list(p) for p in path])
        pos = flow.data('Pos')
        start = tuple(math.floor(v) for v in pos)
        if math.dist(pos, (path[0][0] + .5, path[0][1], path[0][2] + .5)) > 1.5:
            if flow.data('Dimension') != 'minecraft:overworld' or start not in flow.graph or path[0] not in flow.graph:
                raise ValueError('声明fixture路线起点与实际玩家不接续；不直穿未知地形/传送')
            flow.follow(cardinal_path(flow.graph, start, path[0]))
        flow.follow(path)

    def marker_matrix(self):
        a = self.a
        if not self.marker or 'stage_change' not in self.marker or not self.s.work.get('complete'):
            raise ValueError('须在同一run工作前调用marker_prepare并保留实际第三格自动推进')
        m = self.marker
        # Real mapped input also occurs in the selected final stage, not only in
        # its explicitly linked earlier camera/input spans.
        self.tracker_cycle_completed(a)
        a.check('occluded_visible', bool(m['occluded']['blockers']), m['occluded'])
        a.check('offscreen_direction', len(m['offscreen']) == 3, {'views': m['offscreen']})
        a.check('distance_readout', m['distance']['near']['centres'] != [] and m['distance']['far']['centres'] != [],
                {**m['distance'], 'numeric_text': 'requires_raw_artifact_review；不以状态日志宣称字体已读对'})
        a.check('near_precise_target', len(m['near']['centres']) == 1, m['near'])
        a.check('tracker_toggle_cycle', set(m['toggle']['modes']) == {'COMPACT', 'EXPANDED', 'HIDDEN'}, m['toggle'])
        change = m['stage_change']
        a.check('stage_change_target_fresh', change['before']['stage'] == 'planting' and change['after']['stage'] == 'report'
                and change['before']['target'] != change['after']['target'], change)
        a.check('moving_npc_uses_same_person', m['movement']['before']['personId'] == m['movement']['actual_AI_after']['personId'], m['movement'])
        no_target = self.marker_frame(a, 'marker-completed-no-target', visible=False)
        a.check('no_target_or_other_dimension_hidden', no_target['state']['target']['position'] is None,
                {'completed': no_target, 'portal': m['dimension']})

    def tracker_cycle_completed(self, flow):
        for _ in range(3):
            flow.c.input.press('y')
            self.marker_frame(flow, 'marker-complete-toggle', visible=False)

    def existing_awakened(self):
        raise RuntimeError('用SavedFixtureSession.existing_awakened(flow)在真实已保存副本运行；fresh扩展不重写NBT')

    def legacy_accepted(self):
        raise RuntimeError('用SavedFixtureSession.legacy_accepted(flow)在accepted-v1副本运行；fresh扩展不造旧progress')


def fate_identity(row):
    required = ('candidates', 'confirmed')
    if any(key not in row for key in required):
        raise ValueError('FATES持久候选/确认字段缺失')
    # Session nonce/token and active UI epoch can legitimately expire after restart.
    return {key: row[key] for key in ('schema', 'rolls', 'locked', 'candidates', 'confirmed', 'selected', 'budget') if key in row}
