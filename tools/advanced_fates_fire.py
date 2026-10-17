"""Focused mapped-input fire-spirit evidence; invoked by the shared cloud harness only."""
from __future__ import annotations

import math
import re
import time
import uuid

from tools.alchemy_quality_evidence import SecondClient, read_data
from tools.combat_capture import session as cs


def parse_fire_logs(text):
    """Keep source identities and server ticks, without interpreting other combat log lines."""
    result = []
    for line in text.splitlines():
        found = re.search(r'\bFIRE_SPIRIT_(HIT|BURST|DAMAGE)\s+(.*)', line)
        if found:
            row = dict(re.findall(r'([a-z_]+)=([^\s]+)', found[2]))
            row['event'] = found[1].lower()
            result.append(row)
    return result


def _entity_uuid(e, target):
    values = read_data(e.rc(f'data get entity {target} UUID'))
    if not isinstance(values, list) or len(values) != 4:
        raise ValueError('Missing four-part entity UUID: '+repr(values))
    return str(uuid.UUID(int=sum((part & 0xffffffff) << (96 - i * 32)
                                for i, part in enumerate(values))))


class _GuestEvidence:
    """Give this one guest run its own artifacts when other cases already used SecondClient."""
    def __init__(self, evidence):
        self.e = evidence
        self.out = evidence.out/'fire-spirit-pvp'
        self.out.mkdir(parents=True, exist_ok=True)

    def __getattr__(self, name):
        return getattr(self.e, name)


def run(e):
    u = e.s.username
    log = cs.LOG_DIR/'server.log'
    start = log.stat().st_size
    artifacts = e.report.setdefault('fire_spirit', {})
    effects = e.definitions['myvillage:fire_spirit_body']['effects']
    duration = int(effects['fire_burn_ticks'])
    interval = int(effects['fire_burn_interval_ticks'])
    installments = math.ceil(duration/interval)
    fraction = effects['fire_burn_fraction']
    required = int(effects['fire_burst_hits'])
    cooldown = int(effects['fire_burst_cooldown_ticks'])
    restored = {}

    def logs(offset=start):
        with log.open('rb') as stream:
            stream.seek(offset)
            return stream.read().decode(errors='replace')

    def rows(target_id, offset=start, event=None):
        return [row for row in parse_fire_logs(logs(offset))
                if row.get('target') == target_id and (event is None or row['event'] == event)]

    def damage(target_id, offset=start, kind='burn'):
        return [row for row in rows(target_id, offset, 'damage') if row.get('kind') == kind]

    def check(name, passed, detail):
        e.check('fire_spirit_'+name, bool(passed), detail)

    def close(left, right, tolerance=.02):
        return abs(float(left)-float(right)) <= tolerance

    def now():
        return e.state()['now']

    def wait_tick(tick, label):
        return e.poll(now, lambda value: value >= tick,
                      timeout=max(8, (tick-now())/20+8), label=label)

    def cooldowns():
        return e.state().get('fire_spirit', {}).get('deadlines', {})

    def dummy(tag, pos=(.5, -60, 2.5), fire_resistance=0):
        target = e.dummy(tag, pos)
        e.rc(f'tag {target} add advanced_fire_fixture')
        e.rc(f'attribute {target} myvillage:physical_resistance base set 0')
        e.rc(f'attribute {target} myvillage:fire_resistance base set {fire_resistance}')
        return target, _entity_uuid(e, target)

    def mapped_hit(target, target_id):
        # Vanilla iron-sword attacks make one primary receipt and never invoke a combo sweep.
        # The read-only receipt survives later same-tick DOT overwriting StatsRuntime.lastTrace.
        time.sleep(.7)
        before = e.hp(target)
        offset = log.stat().st_size
        e.s.game.require_ingame('fire spirit mapped primary melee')
        e.s.game.xdo('click', '1')
        receipts = e.poll(lambda: rows(target_id, offset, 'hit'), bool,
                          timeout=5, label='unique mapped primary health-loss receipt')
        if len(receipts) != 1 or float(receipts[0]['primary']) <= 0:
            raise AssertionError('Expected one positive primary receipt per mapped click: '+repr(receipts))
        receipt = receipts[0]
        return {'before': before, 'primary': float(receipt['primary']),
                'tick': int(receipt['tick']), 'receipt': receipt}

    def single_burn(label, resistance):
        target, identity = dummy('advanced_fire_'+label, fire_resistance=resistance)
        offset = log.stat().st_size
        hit = mapped_hit(target, identity)
        received = e.poll(lambda: damage(identity, offset),
                          lambda value: len(value) >= installments,
                          timeout=duration/20+10, label=label+' burn installments')
        wait_tick(int(received[-1]['tick'])+interval+2, label+' no recursive fire work')
        received = damage(identity, offset)
        expected = hit['primary']*fraction
        requested = sum(float(row['requested']) for row in received)
        actual = sum(float(row['actual']) for row in received)
        ticks = [int(row['tick']) for row in received]
        expected_ticks = [hit['tick']+min(duration, interval*i)
                          for i in range(1, installments+1)]
        detail = {'hit': hit, 'events': received, 'expected_raw_burn': expected,
                  'expected_ticks': expected_ticks, 'health_after': e.hp(target)}
        artifacts[label] = detail
        check(label+'_fraction_and_installments', len(received) == installments
              and close(requested, expected) and ticks == expected_ticks, detail)
        check(label+'_fire_resistance_once', close(actual, expected*(1-resistance))
              and close(hit['before']-e.hp(target), hit['primary']+actual), detail)
        check(label+'_derived_does_not_recurse', not rows(identity, offset, 'burst')
              and all(row.get('kind') == 'burn' for row in rows(identity, offset, 'damage')), detail)
        fire = e.number(f'data get entity {target} Fire')
        trace = e.one(f'myvillage stats trace {target}', 'STATS_TRACE')
        check(label+'_typed_fire_without_vanilla_ignition', fire <= 0
              and trace.get('type') == 'fire' and trace.get('sourceId') == 'myvillage:fire_spirit'
              and close(trace.get('afterResistance', -1), trace.get('postVanilla', -1)*(1-resistance)),
              {'fire_ticks': fire, 'trace': trace})
        e.rc(f'kill {target}')
        # LivingEntity remains pickable throughout its 20-tick death animation.
        # @e adds an isAlive predicate; exact UUID lookup observes actual removal.
        e.poll(lambda:e.rc(f'execute if entity {identity}'),lambda reply:'passed' not in reply,
               timeout=4,label=label+' dying target removed before reusing aim point')

    def burst_hits(target, identity, count):
        return [mapped_hit(target, identity) for _ in range(count)]

    try:
        # Dead or living dummies in front of the stele can intercept the mapped right-click.
        e.rc('kill @e[tag=advanced_fire_fixture]')
        e.poll(lambda: e.rc('execute if entity @e[tag=advanced_fire_fixture]'),
               lambda reply: 'passed' not in reply, timeout=4, label='fire stele sightline cleared')
        e.choose_focus('fire_spirit_body')
        e.home()
        e.equip('minecraft:iron_sword')
        for attribute in ('myvillage:fire_resistance_penetration', 'myvillage:resistance_penetration'):
            restored[attribute] = e.number(f'attribute {u} {attribute} base get')
            e.rc(f'attribute {u} {attribute} base set 0')
        single_burn('unresisted', 0)
        single_burn('resisted', .5)

        first, first_id = dummy('advanced_fire_first')
        first_offset = log.stat().st_size
        hits = burst_hits(first, first_id, required)
        burst = e.poll(lambda: rows(first_id, first_offset, 'burst'), bool,
                       timeout=4, label='mapped third hit queues flame burst')[0]
        paid = e.poll(lambda: damage(first_id, first_offset, 'burst'), bool,
                      timeout=4, label='queued flame burst delivered')
        check('third_hit_one_weapon_burst', len(paid) == 1
              and close(paid[0]['requested'], float(burst['weapon_damage'])*effects['fire_burst_multiplier'])
              and int(burst['ready_at'])-int(burst['tick']) == cooldown,
              {'primary_hits': hits, 'burst': burst, 'damage': paid})

        suppressed = burst_hits(first, first_id, required)
        deadline = int(burst['ready_at'])
        check('same_target_cooldown_blocks_three_more_hits', now() < deadline
              and len(rows(first_id, first_offset, 'burst')) == 1
              and cooldowns().get(first_id) == deadline,
              {'now': now(), 'deadline': deadline, 'hits': suppressed, 'events': rows(first_id, first_offset)})
        wait_tick(deadline, 'same target flame burst cooldown naturally expires')
        renewed_hit = mapped_hit(first, first_id)  # A banked stack still needs one new real attack.
        renewed = e.poll(lambda: rows(first_id, first_offset, 'burst'),
                         lambda value: len(value) == 2, timeout=4, label='next real hit renews burst')[1]
        renewed_deadline = int(renewed['ready_at'])

        e.rc(f'tp {first} 5.5 -60 2.5')
        second, second_id = dummy('advanced_fire_second')
        second_offset = log.stat().st_size
        second_hits = burst_hits(second, second_id, required)
        independent = e.poll(lambda: rows(second_id, second_offset, 'burst'), bool,
                             timeout=4, label='second target independent flame burst')[0]
        check('second_target_has_independent_cooldown', int(independent['tick']) < renewed_deadline
              and cooldowns().get(first_id) == renewed_deadline
              and cooldowns().get(second_id) == int(independent['ready_at']),
              {'first': renewed, 'second': independent, 'deadlines': cooldowns()})
        final_primary_tick = max(renewed_hit['tick'], second_hits[-1]['tick'])
        wait_tick(final_primary_tick+duration+interval, 'all primary burn budgets delivered without burst recursion')
        first_burn = damage(first_id, first_offset)
        second_burn = damage(second_id, second_offset)
        first_expected = sum(hit['primary'] for hit in [*hits, *suppressed, renewed_hit])*fraction
        second_expected = sum(hit['primary'] for hit in second_hits)*fraction
        check('burst_damage_does_not_create_more_burn_or_stacks',
              close(sum(float(row['requested']) for row in first_burn), first_expected, .05)
              and close(sum(float(row['requested']) for row in second_burn), second_expected, .05)
              and len(rows(first_id, first_offset, 'burst')) == 2
              and len(rows(second_id, second_offset, 'burst')) == 1,
              {'first_expected': first_expected, 'first_burn': first_burn,
               'second_expected': second_expected, 'second_burn': second_burn})
        artifacts['cooldown_targets'] = {'first': first_id, 'second': second_id}
        e.rc('kill @e[tag=advanced_fire_fixture]')

        with SecondClient(_GuestEvidence(e)) as guest:
            v = guest.username
            e.rc(f'gamemode survival {v}')
            e.rc(f'myvillage cultivation fate reset {v}')
            e.rc(f'attribute {v} minecraft:generic.max_health base set 200')
            e.rc(f'attribute {v} minecraft:generic.knockback_resistance base set 1')
            e.rc(f'attribute {v} minecraft:generic.armor base set 0')
            e.rc(f'attribute {v} myvillage:physical_resistance base set 0')
            e.rc(f'attribute {v} myvillage:fire_resistance base set 0')
            e.rc(f'effect give {v} minecraft:instant_health 1 10 true')
            e.home()
            e.rc(f'tp {v} .5 -60 2.5 180 0')
            victim_id, owner_id = _entity_uuid(e, v), _entity_uuid(e, u)
            guest_before = e.one(f'myvillage cultivation fate status {v}', 'FATES')
            offset = log.stat().st_size
            pvp_hit = mapped_hit(v, victim_id)
            pvp_burn = e.poll(lambda: damage(victim_id, offset),
                             lambda value: len(value) >= installments,
                             timeout=duration/20+10, label='real second player receives attributed fire')
            guest_after = e.one(f'myvillage cultivation fate status {v}', 'FATES')
            check('two_clients_mapped_pvp_source_and_private_state', len(pvp_burn) == installments
                  and all(row['owner'] == owner_id and row['target'] == victim_id for row in pvp_burn)
                  and guest_before.get('fire_spirit', {}) == guest_after.get('fire_spirit', {})
                  and 'myvillage:fire_spirit_body' not in guest_after.get('selected', []),
                  {'hit': pvp_hit, 'events': pvp_burn, 'guest_before': guest_before, 'guest_after': guest_after})

            # Reverse the actual mapped attack: being struck by an unawakened player grants no fire.
            e.heal()
            e.rc(f'item replace entity {v} hotbar.0 with minecraft:iron_sword')
            guest.game.key('1')
            guest.game.require_ingame('guest mapped reverse fire attribution boundary')
            time.sleep(.7)
            before = e.hp()
            offset = log.stat().st_size
            guest.game.xdo('click', '1')
            e.poll(e.hp, lambda value: value < before, timeout=5, label='guest mapped reverse melee lands')
            wait_tick(now()+duration+interval, 'reverse attack produces no inherited fire')
            check('victim_fate_does_not_transfer_to_attacker', not parse_fire_logs(logs(offset)),
                  {'before': before, 'after': e.hp(), 'events': parse_fire_logs(logs(offset))})
            artifacts['multiplayer'] = 'two_actual_clients_mapped_pvp_owner_uuid_and_private_state'

        # Leave a fresh active deadline for the parent harness's shared death/restart probe.
        e.home()
        e.equip('minecraft:iron_sword')
        lifecycle, lifecycle_id = dummy('advanced_fire_lifecycle')
        burst_hits(lifecycle, lifecycle_id, required)
        e.poll(lambda: cooldowns().get(lifecycle_id, 0), lambda ready: ready > now(),
               timeout=4, label='fresh fire cooldown snapshot')
        snapshot = {'selected': e.state()['selected'], 'fire_spirit': e.state().get('fire_spirit', {}),
                    'target_uuid': lifecycle_id, 'target_selector': lifecycle, 'now': now()}
        e.report['fire_spirit_lifecycle_snapshot'] = snapshot
        artifacts['runtime_owner_feel'] = 'not_verified'
        return snapshot
    finally:
        text = logs()
        (e.out/'fire-spirit-server.log').write_text(text)
        artifacts['events'] = parse_fire_logs(text)
        for attribute, value in restored.items():
            e.rc(f'attribute {u} {attribute} base set {value}')
        e.save()
