"""Mapped-input wind-step cases for the parent advanced-fates Modal harness.

Only ``run(e)`` is an entry point. RCON prepares geometry/health/qi and reads
results; Space and Left Alt exercise the same client input as a player.
No flight, no-gravity fixture, direct runtime invocation, or player-NBT edits.
"""
from __future__ import annotations

import json
import math
from pathlib import Path

from tools.alchemy_quality_evidence import read_data
from tools.combat_capture import scene, session as cs
from tools.combat_capture.dodge import dodge_events


RATE = 5
X, Y, Z = 40.5, -60.0, .5


def run(e):
    """Run on an already started/awakened PurpleEvidence-compatible instance."""
    u, game = e.s.username, e.s.game
    root = Path(__file__).resolve().parents[1]
    movement = json.loads((root / 'src/main/resources/data/myvillage/myvillage/technique/'
                          'taxue_wuhen.json').read_text())['effects']['movement']
    cost, cooldown = movement['qi_cost'], movement['cooldown_ticks']
    log = cs.LOG_DIR / 'server.log'
    trials = e.report.setdefault('wind_step_trials', {})
    e.report['wind_step_method'] = ('real survival Space jump + Left Alt; RCON geometry, qi/HP fixtures '
                                    'and FATES.wind_step/DODGE_DEBUG readback; no direct dodge calls')
    prior_fire_tick = 'false' if e.rc('gamerule doFireTick').strip().endswith('false') else 'true'

    def state():
        full = e.state()
        return {**full, 'wind_step': {'air_spent': True, 'ready_at': 0,
                                    **full.get('wind_step', {})}}

    def pos():
        return read_data(e.rc(f'data get entity {u} Pos'))

    def snapshot():
        return {'fates': state(), 'power': e.power(), 'pos': pos(), 'hp': e.hp()}

    def rows(mark):
        with log.open('rb') as stream:
            stream.seek(mark)
            lines = stream.read().decode('utf-8', errors='replace').splitlines()
        return [row for row in dodge_events(lines) if row.get('player') == u]

    def ticks_after(tick, count):
        return e.poll(state, lambda value: value['now'] >= tick + count,
                      timeout=count / RATE + 10, label='wind-step actual server ticks')

    def ready():
        return e.poll(state, lambda value: not value['wind_step']['air_spent']
                      and value['now'] >= value['wind_step']['ready_at'],
                      timeout=cooldown / RATE + 12, label='wind-step landed charge and shared cooldown')

    def stand(z=Z):
        # TP is a documented fixture only. Positive movement evidence always begins with Space.
        e.rc(f'tp {u} {X} {Y} {z} 180 0')
        e.poll(pos, lambda value: abs(value[1] - Y) < .001 and abs(value[2] - z) < .01,
               label='wind-step fixture coordinates accepted')
        ready()

    def jump():
        before = pos()
        game.require_ingame('wind-step real Space jump')
        game.xdo('keydown', 'space')
        try:
            airborne = e.poll(pos, lambda value: value[1] > before[1] + .15,
                              timeout=4, label='mapped jump leaves actual platform')
        finally:
            game.xdo('keyup', 'space')
        return {'before': before, 'airborne': airborne}

    def press(label):
        mark = log.stat().st_size
        game.key('Alt_L')
        events = e.poll(lambda: rows(mark),
                        lambda values: any(v['kind'] in ('started', 'rejected') for v in values),
                        timeout=5, label=label + ' server dodge decision')
        decision = next(row for row in events if row['kind'] in ('started', 'rejected'))
        trials.setdefault(label, {}).update(decision=decision, events=events)
        e.save()
        return decision, mark

    def outcome(label, decision, expected, before, after):
        actual = decision['kind'] if decision['kind'] == 'started' else decision.get('reason')
        trials.setdefault(label, {}).update(before=before, after=after)
        e.check('wind_' + label, actual == expected,
                {'expected': expected, 'decision': decision, 'before': before, 'after': after})

    def paid_air(label):
        before = snapshot()
        e.check('wind_' + label + '_charge_fixture', not before['fates']['wind_step']['air_spent'], before)
        flight = jump()
        decision, mark = press(label)
        after = snapshot()
        trials[label]['jump'] = flight
        outcome(label, decision, 'started', before, after)
        e.check('wind_' + label + '_authored_payment_and_charge',
                before['power']['current'] - after['power']['current'] == cost
                and after['fates']['wind_step']['air_spent']
                and after['fates']['wind_step']['ready_at'] == decision['t'] + cooldown,
                {'qi_cost': cost, 'cooldown_ticks': cooldown, 'before': before, 'after': after})
        return decision, mark, after

    try:
        e.choose_focus('wind_step')
        e.equip('myvillage:qingfeng_sword')
        scene.ensure_cultivation(e.s)
        e.rc(f'gamemode survival {u}')
        e.rc(f'tp {u} {X} {Y} {Z} 180 0')
        e.poll(lambda: e.rc('execute if loaded 40 -60 5'), lambda reply: 'passed' in reply,
               timeout=25, label='wind-step arena chunk loaded')
        e.rc('fill 28 -60 -9 55 -52 13 minecraft:air')
        e.rc('fill 28 -61 -9 55 -61 13 minecraft:stone')
        e.rc('tick rate 5')
        stand()
        e.rc(f'myvillage cultivation qi fill {u}')

        # A successful ground dodge is still free and grants the configured transient speed bonus.
        before = snapshot()
        speed = e.number(f'attribute {u} minecraft:generic.movement_speed get')
        decision, _ = press('ground_free')
        after = snapshot()
        outcome('ground_free', decision, 'started', before, after)
        bonus = e.definitions['myvillage:wind_step']['effects']['dodge_speed_bonus']
        enhanced_speed = e.number(f'attribute {u} minecraft:generic.movement_speed get')
        e.check('wind_ground_remains_free_and_has_speed_bonus',
                after['power']['current'] == before['power']['current']
                and not after['fates']['wind_step']['air_spent']
                and math.isclose(enhanced_speed, speed * (1 + bonus), rel_tol=.01),
                {'before': before, 'after': after, 'speed_before': speed, 'speed_after': enhanced_speed,
                 'authored_bonus': bonus})

        stand()
        e.rc(f'myvillage cultivation qi fill {u}')
        decision, _, after = paid_air('air_once')
        ticks_after(decision['t'], 4)  # Observe the client's real four-tick input throttle.
        e.check('wind_repeat_is_still_airborne', pos()[1] > Y + .1, pos())
        repeated, _ = press('repeat_air')
        repeated_after = snapshot()
        outcome('repeat_air', repeated, 'AIRBORNE', after, repeated_after)
        e.check('wind_repeat_never_pays_twice', repeated_after['power']['current'] >= after['power']['current']
                and repeated_after['fates']['wind_step']['air_spent'], repeated_after)

        landed = e.poll(snapshot, lambda value: abs(value['pos'][1] - Y) < .001
                        and not value['fates']['wind_step']['air_spent'], timeout=7,
                        label='real post-jump landing restores air charge')
        e.check('wind_landing_preserves_live_shared_cooldown',
                landed['fates']['now'] < landed['fates']['wind_step']['ready_at'], landed)
        blocked, _ = press('landed_shared_cooldown')
        blocked_after = snapshot()
        outcome('landed_shared_cooldown', blocked, 'COOLDOWN', landed, blocked_after)
        e.check('wind_cooldown_refusal_costs_nothing',
                blocked_after['power']['current'] >= landed['power']['current']
                and not blocked_after['fates']['wind_step']['air_spent'], blocked_after)
        stand()
        e.rc(f'myvillage cultivation qi fill {u}')
        paid_air('after_landing_reset')

        # A failed air action must leave the same flight's charge available for a subsequent press.
        stand()
        e.rc(f'myvillage cultivation qi set {u} 0')
        before = snapshot()
        jump()
        decision, _ = press('insufficient_power')
        after = snapshot()
        outcome('insufficient_power', decision, 'NO_POWER', before, after)
        e.check('wind_no_power_keeps_charge_and_never_debits',
                not after['fates']['wind_step']['air_spent']
                and after['power']['current'] >= before['power']['current'], after)
        e.rc(f'myvillage cultivation qi fill {u}')
        ticks_after(decision['t'], 4)
        e.check('wind_refill_probe_same_flight', pos()[1] > Y + .1, pos())
        retry_before = snapshot()
        retry, _ = press('refilled_same_flight')
        retry_after = snapshot()
        outcome('refilled_same_flight', retry, 'started', retry_before, retry_after)
        e.check('wind_refilled_retry_pays_once',
                retry_before['power']['current'] - retry_after['power']['current'] == cost
                and retry_after['fates']['wind_step']['air_spent'], retry_after)

        stand()
        e.rc('fill 39 -60 1 42 -54 1 minecraft:stone')
        stand(z=.7)  # Body front is z=1: every positive step intersects the wall.
        e.rc(f'myvillage cultivation qi fill {u}')
        before = snapshot()
        jump()
        decision, _ = press('touching_wall')
        after = snapshot()
        outcome('touching_wall', decision, 'BLOCKED', before, after)
        e.check('wind_wall_failure_preserves_payment_and_charge',
                not after['fates']['wind_step']['air_spent']
                and after['power']['current'] == before['power']['current'], after)
        e.rc('fill 39 -60 1 42 -54 1 minecraft:air')
        ticks_after(decision['t'], 4)
        e.check('wind_removed_wall_probe_same_flight', pos()[1] > Y + .1, pos())
        retry_before = snapshot()
        retry, _ = press('wall_removed_same_flight')
        retry_after = snapshot()
        outcome('wall_removed_same_flight', retry, 'started', retry_before, retry_after)
        e.check('wind_wall_retry_pays_once',
                retry_before['power']['current'] - retry_after['power']['current'] == cost
                and retry_after['fates']['wind_step']['air_spent'], retry_after)

        # A pane's endpoint is clear, but its thin collision shape across the path must stop the dash.
        stand()
        e.rc('fill 39 -60 3 42 -54 3 minecraft:glass_pane[east=true,west=true]')
        e.rc(f'myvillage cultivation qi fill {u}')
        decision, _, after = paid_air('thin_pane')
        e.check('wind_thin_wall_stops_entire_body',
                0 < float(decision['distance']) < movement['dash_distance']
                and after['pos'][2] + .3 <= 3 + 7 / 16 + .002,
                {'decision': decision, 'after': after, 'pane_front_z': 3 + 7 / 16})
        e.rc('fill 39 -60 3 42 -54 3 minecraft:air')

        # Regression for the reviewed Entity.move transaction bug. The old path called fire's
        # entityInside synchronously BEFORE payment/window installation and killed a vulnerable
        # one-HP player. These real fire blocks have empty collision shapes and a flammable side.
        stand()
        e.rc('gamerule doFireTick false')
        e.rc('fill 41 -61 4 41 -54 6 minecraft:oak_planks')
        e.rc('fill 40 -60 4 40 -54 6 minecraft:fire[east=true]')
        fire = e.rc('execute if block 40 -59 5 minecraft:fire')
        e.check('wind_fire_fixture_is_real_block', 'passed' in fire, fire)
        e.rc('scoreboard objectives add adv_wind_deaths deathCount')
        e.rc(f'scoreboard players set {u} adv_wind_deaths 0')
        e.rc('tick rate 20')
        e.heal()
        e.rc(f'damage {u} {e.maximum() - 1} minecraft:generic')
        e.poll(e.hp, lambda hp: abs(hp - 1) < .01, label='fire regression one-HP fixture')
        damaged_at = state()['now']
        ticks_after(damaged_at, 21)  # Generic damage's vanilla immunity must have really expired.
        e.check('wind_fire_fixture_has_no_recent_hurt_animation',
                e.number(f'data get entity {u} HurtTime') == 0, {'damaged_at': damaged_at, 'now': state()['now']})
        e.rc('tick rate 5')
        e.rc(f'myvillage cultivation qi fill {u}')
        decision, mark, after = paid_air('one_hp_fire_endpoint')
        contact = e.poll(lambda: rows(mark), lambda values: any(
            row['kind'] == 'cancelled_damage' and row.get('source') == 'minecraft:in_fire'
            for row in values), timeout=3, label='actual fire block contact reaches active dodge window')
        actual = snapshot()
        deaths = e.number(f'scoreboard players get {u} adv_wind_deaths')
        e.rc('fill 40 -60 4 40 -54 6 minecraft:air')
        trials['one_hp_fire_endpoint'].update(contact=contact, contact_snapshot=actual, deaths=deaths)
        e.check('wind_fire_contact_never_kills_before_payment_and_window',
                abs(actual['hp'] - 1) < .01 and deaths == 0
                and actual['power']['current'] == after['power']['current']
                and actual['fates']['wind_step']['air_spent']
                and any(row.get('source') == 'minecraft:in_fire' and row.get('kind') == 'cancelled_damage'
                        and decision['t'] <= row['t'] < decision['t'] + movement['invulnerable_ticks']
                        for row in contact),
                {'decision': decision, 'contact': contact, 'snapshot': actual, 'deaths': deaths})
        e.report['wind_step_lifecycle'] = 'restart/reconnect cases are owned by the parent harness'
    finally:
        game.xdo('keyup', 'space', 'keyup', 'Alt_L')
        e.rc('tick rate 20')
        e.rc('fill 40 -60 4 40 -54 6 minecraft:air')
        e.rc('fill 41 -61 4 41 -54 6 minecraft:air')
        e.rc('fill 39 -60 1 42 -54 3 minecraft:air')
        e.rc('gamerule doFireTick ' + prior_fire_tick)
        e.home()
        e.save()
