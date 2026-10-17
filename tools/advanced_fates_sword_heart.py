"""Mapped Sword Heart case for the parent's isolated advanced-fates harness.

The two positive openings require an actual second client's mapped iron-sword attack.
Commands only prepare/read fixtures or supply explicitly labelled negative boundaries.
This module neither starts the main session nor runs itself, builds, or restarts a server.
"""
from __future__ import annotations

import re

from tools.alchemy_quality_evidence import SecondClient, read_data
from tools.combat_capture import scene, session as cs


WAVE = 'myvillage:innate_sword_heart_wave'
NEAR_TAG = 'advanced_heart_near'
FAR_TAG = 'advanced_heart_far'


def _offset():
    return (cs.LOG_DIR / 'server.log').stat().st_size


def _logs(offset):
    with (cs.LOG_DIR / 'server.log').open('rb') as stream:
        stream.seek(offset)
        return stream.read().decode(errors='replace')


def _rows(e, offset):
    rows = []
    for line in _logs(offset).splitlines():
        match = re.search(r'\b(SWORD_HEART|DODGE_DEBUG|DERIVED_QI) (.*)', line)
        if match:
            row = dict(re.findall(r'\b([a-z_]+)=([^\s]+)', match[2]))
            if row.get('player') == e.s.username:
                rows.append({'kind': match[1], **row})
    return rows


def _heart(state):
    return {'opening_expires_at': 0, 'cooldown_ready_at': 0, **state.get('sword_heart', {})}


def _position(e, user):
    return read_data(e.rc(f'data get entity {user} Pos'))


def _wait_ticks(e, ticks, label):
    end = e.state()['now'] + ticks
    return e.poll(e.state, lambda state: state['now'] >= end, timeout=20, label=label)


def _dodge_ready(e):
    # No short opening is active when this helper is called. Fast-forward by normal ticking only.
    e.rc('tick rate 20')
    def remaining():
        reply = e.rc(f'myvillage combat dodge status {e.s.username}')
        match = re.search(r'\bcooldown_remaining=(\d+)', reply)
        if not match:
            raise ValueError('Missing dodge cooldown readback: ' + reply)
        return int(match[1])
    e.poll(remaining, lambda value: value == 0, timeout=12, label='normal movement cooldown expires')


def _heart_ready(e):
    e.rc('tick rate 20')
    return e.poll(e.state, lambda state: state['now'] >= _heart(state)['cooldown_ready_at'],
                  timeout=15, label='persistent Sword Heart cooldown expires')


def _press_dodge(e, offset):
    game = e.s.game
    game.require_ingame('mapped Sword Heart dodge')
    game.xdo('keyup', 'w', 'a', 's', 'd', 'space', 'Shift_L')
    game.xdo('keydown', 'Alt_L')
    try:
        rows = e.poll(lambda: _rows(e, offset),
                      lambda rows: any(row['kind'] == 'DODGE_DEBUG' and row.get('result') == 'started'
                                       for row in rows), timeout=4, label='mapped dodge actually started')
    finally:
        game.xdo('keyup', 'Alt_L')
    return next(row for row in rows if row['kind'] == 'DODGE_DEBUG' and row.get('result') == 'started')


def _cancelled(rows, source):
    return [row for row in rows if row['kind'] == 'DODGE_DEBUG'
            and row.get('source') == source and float(row.get('cancelled_damage', 0)) > 0]


def _actual_opening(e, guest, label, report):
    """Bounded timing retries remain visible; a miss never becomes a command-created opening."""
    attempts = []
    report.setdefault('mapped_opening_attempts', {})[label] = attempts
    for attempt in range(1, 5):
        _heart_ready(e)
        _dodge_ready(e)
        e.heal()
        e.home()
        # Facing +Z, the owner's backstep approaches this attacker. After the first ground
        # impulse they are already within vanilla reach, leaving several of the four ticks.
        e.rc(f'tp {guest.username} .5 -60 -4 0 0')
        guest.game.require_ingame('second client actual precise-dodge attacker')
        e.rc('tick rate 5')
        before_hp = e.hp()
        start = _offset()
        begun = _press_dodge(e, start)
        try:
            e.poll(lambda: (_position(e, e.s.username), _position(e, guest.username)),
                   lambda positions: sum((a-b)**2 for a, b in zip(*positions)) <= 2.8**2,
                   timeout=1.2, label='backstep enters actual guest sword reach')
            guest.game.click(.12)
            observed = e.poll(lambda: (e.state(), _rows(e, start)),
                              lambda result: _heart(result[0])['opening_expires_at'] > result[0]['now']
                              and bool(_cancelled(result[1], 'minecraft:player_attack')),
                              timeout=2.5, label='actual mapped enemy hit earns precise opening')
        except TimeoutError as error:
            snapshot = e.state()
            attempts.append({'attempt': attempt, 'dodge': begun, 'state': snapshot,
                             'hp_before': before_hp, 'hp_after': e.hp(), 'events': _rows(e, start),
                             'fixture_timing_retry': str(error)})
            # Any unexpected grant is evidence, not permission to silently wait it away and retry.
            if _heart(snapshot)['opening_expires_at'] > snapshot['now']:
                raise
            continue
        state, events = observed
        attempts.append({'attempt': attempt, 'dodge': begun, 'state': state,
                         'hp_before': before_hp, 'hp_after': e.hp(), 'events': events})
        e.check(label + '_actual_enemy_attack_cancelled_without_health_loss',
                abs(e.hp() - before_hp) < .001
                and any(row['kind'] == 'SWORD_HEART' and row.get('result') == 'ready' for row in events),
                attempts[-1])
        return state, begun
    raise AssertionError(label + ': four real mapped attempts failed the precise-dodge timing fixture')


def _negative(e, guest, name, source, commands, report, *, active_cooldown=False):
    _dodge_ready(e)
    if not active_cooldown:
        _heart_ready(e)
    e.home()
    e.rc(f'tp {guest.username} 8 -60 -8 0 0')
    initial = e.state()
    e.check(name + '_starts_without_opening', _heart(initial)['opening_expires_at'] == 0, initial)
    if active_cooldown:
        e.check(name + '_actually_inside_persistent_cooldown',
                initial['now'] < _heart(initial)['cooldown_ready_at'], initial)
    else:
        e.check(name + '_not_masked_by_cooldown',
                initial['now'] >= _heart(initial)['cooldown_ready_at'], initial)
    e.rc('tick rate 5')
    start = _offset()
    begun = _press_dodge(e, start)
    precision_ticks = int(e.definitions['myvillage:innate_sword_heart']['effects']['heart_window_ticks'])
    def canceled_inside_precision(rows):
        return any(0 <= int(row['t'])-int(begun['t']) < precision_ticks
                   for row in _cancelled(rows, source))
    try:
        for command in commands:
            e.rc(command)
        events = e.poll(lambda: _rows(e, start), canceled_inside_precision,
                        timeout=2, label=name + ' source actually canceled inside precise window')
    finally:
        if name == 'poison_boundary':
            e.rc(f'effect clear {e.s.username} minecraft:poison')
    end_tick = int(begun['t']) + int(begun['invuln']) + 1
    after = e.poll(e.state, lambda state: state['now'] >= end_tick,
                   timeout=5, label=name + ' dodge window completed')
    events = _rows(e, start)
    e.check(name + '_does_not_grant_or_refresh_opening',
            _heart(after)['opening_expires_at'] == 0
            and _heart(after)['cooldown_ready_at'] == _heart(initial)['cooldown_ready_at']
            and not any(row['kind'] == 'SWORD_HEART' and row.get('result') == 'ready' for row in events),
            {'before': initial, 'after': after, 'events': events})
    report.setdefault('negative_boundaries', {})[name] = {
        'method': 'mapped dodge; explicitly labelled command/effect source boundary, not positive mapped-attack proof',
        'fixture_commands': commands, 'before': initial, 'after': after, 'events': events}


def run(e):
    """Run within a started PurpleEvidence-compatible session; the parent owns save/stop/restart."""
    report = e.report.setdefault('sword_heart', {})
    report.update(method='actual two-client mapped precise dodge and authored melee; labelled command/effect negatives',
                  owner_feel='not_verified', owner_visual='not_verified',
                  aiming='derived wave uses current server yaw when the accepted sword hit consumes the opening')
    log_start = _offset()
    user = e.s.username
    try:
        e.rc('tick rate 20')
        e.choose_focus('innate_sword_heart')
        e.equip('myvillage:qingfeng_sword')
        scene.ensure_cultivation(e.s)
        e.rc(f'myvillage cultivation qi fill {user}')
        e.heal()
        _heart_ready(e)
        # Both targets have identical armor-free mitigation. The distant target isolates wave
        # damage, allowing the near target's total loss to be split into melee and wave shares.
        near = e.dummy(NEAR_TAG, (.5, -60, 2.5))
        far = e.dummy(FAR_TAG, (.5, -60, 8))
        e.home()
        before = e.hp(near)
        e.s.game.require_ingame('mapped baseline first sword thrust')
        e.s.game.click(.12)
        e.poll(lambda: e.hp(near), lambda hp: hp < before, timeout=5, label='baseline real sword hit')
        _wait_ticks(e, 26, 'baseline move and combo timeout complete')
        baseline = before - e.hp(near)
        e.check('sword_heart_baseline_is_positive', baseline > 0, {'damage': baseline})
        report['baseline_melee_damage'] = baseline

        with SecondClient(e) as guest:
            visitor = guest.username
            for command in (f'gamemode survival {visitor}', f'effect clear {visitor}',
                            f'myvillage cultivation fate reset {visitor}',
                            f'item replace entity {visitor} weapon.offhand with minecraft:air',
                            *(f'item replace entity {visitor} armor.{slot} with minecraft:air'
                              for slot in ('head', 'chest', 'legs', 'feet'))):
                e.rc(command)
            guest.game.key('1')
            e.rc(f'item replace entity {visitor} weapon.mainhand with minecraft:iron_sword')
            def guest_mode():
                return scene.parse_combat_mode(e.rc(f'data get entity {visitor} "neoforge:attachments"'))
            if guest_mode() != 'vanilla':
                guest.game.key('r')
            e.poll(guest_mode, lambda mode: mode == 'vanilla', timeout=5, label='guest has vanilla iron-sword attacks')
            guest_before = e.one(f'myvillage cultivation fate status {visitor}', 'FATES')
            # Establish that this exact second client's mapped input can really hit this owner.
            e.rc(f'tp {user} .5 -60 -1.5 0 0')
            e.rc(f'tp {visitor} .5 -60 -4 0 0')
            _wait_ticks(e, 22, 'guest iron-sword attack cooldown fully ready')
            hp = e.hp()
            guest.game.require_ingame('calibrate actual second-client sword hit')
            guest.game.click(.12)
            loss = e.poll(e.hp, lambda value: value < hp, timeout=5, label='guest real iron sword causes health loss')
            e.check('mapped_guest_attack_fixture_is_real', loss < hp, {'hp_before': hp, 'hp_after': loss})
            e.heal()

            # A real dodge input without an attack cannot manufacture the reward.
            _dodge_ready(e)
            e.home()
            e.rc(f'tp {visitor} 8 -60 -8 0 0')
            e.rc('tick rate 5')
            no_attack_before = e.state()
            start = _offset()
            begun = _press_dodge(e, start)
            end_tick = int(begun['t']) + int(begun['invuln']) + 1
            no_attack_after = e.poll(e.state, lambda state: state['now'] >= end_tick,
                                     timeout=5, label='unattacked mapped dodge completed')
            e.check('mapped_dodge_key_alone_does_not_award_opening',
                    _heart(no_attack_after)['opening_expires_at'] == 0
                    and _heart(no_attack_after)['cooldown_ready_at'] == _heart(no_attack_before)['cooldown_ready_at']
                    and not any(row['kind'] == 'SWORD_HEART' and row.get('result') == 'ready'
                                for row in _rows(e, start)),
                    {'before': no_attack_before, 'after': no_attack_after, 'events': _rows(e, start)})

            opening, dodge = _actual_opening(e, guest, 'first_precise_dodge', report)
            granted = _heart(opening)
            e.capture('sword-heart-precise-ready.png')
            # Everything needed for the hit was prepared before earning the 60-tick opening.
            # At rate five it lasts twelve wall seconds; normal dodge protection ends at seven ticks.
            e.home()
            attack_ready = int(dodge['t']) + int(dodge['invuln'])
            ready = e.poll(e.state, lambda state: state['now'] >= attack_ready,
                           timeout=5, label='normal dodge invulnerability ends before sword intent')
            e.check('enhanced_sword_attempt_is_inside_opening',
                    ready['now'] < granted['opening_expires_at'], ready)
            near_before, far_before = e.hp(near), e.hp(far)
            strike_offset = _offset()
            e.s.game.require_ingame('mapped sword consumes precise opening')
            e.s.game.click(.12)
            observed = e.poll(lambda: (e.state(), _rows(e, strike_offset)),
                              lambda result: _heart(result[0])['opening_expires_at'] == 0
                              and any(row['kind'] == 'SWORD_HEART' and row.get('result') == 'trigger'
                                      for row in result[1]), timeout=5, label='accepted real sword spends one opening')
            wave_rows = e.poll(lambda: _rows(e, strike_offset),
                               lambda rows: any(row['kind'] == 'DERIVED_QI' and row.get('skill') == WAVE
                                                and row.get('result') == 'stopped' for row in rows),
                               timeout=7, label='derived sword wave reaches observed stop')
            near_loss, wave_loss = near_before - e.hp(near), far_before - e.hp(far)
            releases = [row for row in wave_rows if row['kind'] == 'DERIVED_QI'
                        and row.get('skill') == WAVE and row.get('result') == 'released']
            hits = [row for row in wave_rows if row['kind'] == 'DERIVED_QI'
                    and row.get('skill') == WAVE and row.get('result') == 'hit']
            e.check('next_sword_critical_matches_damage_and_launches_one_wave',
                    len(releases) == 1 and len(hits) == 2
                    and len({row['target'] for row in hits}) == 2
                    and all(abs(float(row['damage'])-wave_loss) < .1 for row in hits)
                    and wave_loss > 0 and abs((near_loss-wave_loss) - baseline*1.5) < .15,
                    {'baseline': baseline, 'near_total_loss': near_loss, 'isolated_wave_loss': wave_loss,
                     'inferred_critical_melee_loss': near_loss-wave_loss, 'events': wave_rows})
            after = e.state()
            first_events = _rows(e, strike_offset)
            e.check('derived_hits_do_not_rearm_opening_refresh_cooldown_or_refund',
                    _heart(after)['opening_expires_at'] == 0
                    and _heart(after)['cooldown_ready_at'] == granted['cooldown_ready_at']
                    and len([row for row in first_events if row['kind'] == 'SWORD_HEART'
                             and row.get('result') == 'trigger']) == 1
                    and not any(row['kind'] == 'SWORD_HEART' and row.get('result') == 'ready' for row in first_events)
                    and 'result=hit_refund' not in _logs(strike_offset),
                    {'granted': granted, 'after': after, 'events': first_events})
            report['enhanced_strike'] = {'opening': opening, 'consumed': observed[0], 'after_wave': after,
                                         'baseline': baseline, 'near_loss': near_loss, 'wave_loss': wave_loss,
                                         'events': wave_rows}
            e.capture('sword-heart-one-wave-completed.png')

            # The actual mapped path above establishes the feature. Commands below isolate source
            # and cooldown boundaries without pretending to be additional mapped positive attacks.
            _negative(e, guest, 'cooldown_boundary', 'minecraft:player_attack',
                      [f'damage {user} 1 minecraft:player_attack by {visitor}'], report, active_cooldown=True)
            _negative(e, guest, 'environment_boundary', 'minecraft:generic',
                      [f'damage {user} 1 minecraft:generic'], report)
            _negative(e, guest, 'poison_boundary', 'neoforge:poison',
                      [f'effect give {user} minecraft:poison 3 4 true'], report)
            _negative(e, guest, 'reflection_boundary', 'minecraft:thorns',
                      [f'damage {user} 1 minecraft:thorns by {visitor}'], report)

            death_ready, _ = _actual_opening(e, guest, 'death_precise_dodge', report)
            death_heart = _heart(death_ready)
            selected = death_ready['selected']
            e.rc(f'kill {user}')
            e.poll(e.hp, lambda value: value > 0, timeout=12, label='normal owner respawn after live opening')
            death_after = e.poll(e.state,
                                 lambda state: _heart(state)['opening_expires_at'] == 0,
                                 timeout=5, label='death discards opening')
            e.check('death_clears_live_opening_but_preserves_cooldown_and_choices',
                    _heart(death_after)['cooldown_ready_at'] == death_heart['cooldown_ready_at']
                    and death_after['selected'] == selected,
                    {'before': death_ready, 'after': death_after})
            report['death'] = {'before': death_ready, 'after': death_after}
            e.report['multiplayer'] = 'two_actual_clients_mapped_sword_precise_dodge_and_private_fate_state'
            other = e.one(f'myvillage cultivation fate status {visitor}', 'FATES')
            e.check('second_player_does_not_inherit_sword_heart_opening',
                    _heart(other) == _heart(guest_before), {'before': guest_before, 'after': other})
        return report
    finally:
        # The parent retains failures and owns session shutdown. Restore ordinary simulation even
        # if a fixture, timing assertion, or the second client fails.
        try:
            e.s.game.xdo('keyup', 'Alt_L', 'w', 'a', 's', 'd', 'space', 'Shift_L')
            e.rc(f'effect clear {user} minecraft:poison')
        finally:
            e.rc('tick rate 20')
            (e.out / 'sword-heart-events.log').write_text(_logs(log_start))
