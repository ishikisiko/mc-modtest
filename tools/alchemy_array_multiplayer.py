#!/usr/bin/env python3
"""Bounded two-client array checks in ArrayEvidence's disposable Modal world.

No session/server is started here. The caller owns the main capture session;
SecondClient owns and cleans up only its guest's Xvfb/Gradle/client processes.
Menus, disconnect and reconnect use real mapped input. RCON only prepares the
fixture, controls test tick speed and reads authoritative serialized state.
"""
from __future__ import annotations

import secrets
import subprocess
import time

from tools.alchemy_quality_evidence import SecondClient, pill_quality, quality_stacks, read_data


def _scale(game):
    width, height = game.size
    scale = max(1, min(width // 320, height // 240))
    return scale, width // scale, height // scale


def _screen_click(evidence, game, x, y, label, actor):
    scale, _, _ = _scale(game)
    game.xdo('mousemove', str(round(scale * x)), str(round(scale * y)))
    game.xdo('click', '1')
    evidence.report['mapped_input'].append({'kind': label, 'actor': actor,
                                            'screen_gui': [x, y]})
    time.sleep(.35)


def _menu_click(evidence, game, point, label, actor, *, shift=False):
    # Import lazily: the main evidence module may import this helper for its case.
    from tools.alchemy_array_evidence import menu_point
    px, py = menu_point(game.size, *point)
    game.xdo('mousemove', str(px), str(py))
    if shift:
        game.xdo('keydown', 'Shift_L')
    try:
        game.xdo('click', '1')
    finally:
        if shift:
            game.xdo('keyup', 'Shift_L')
    evidence.report['mapped_input'].append({'kind': label, 'actor': actor,
                                            'gui': list(point), 'shift': shift})
    time.sleep(.35)


def _capture(evidence, game, name):
    game.xdo('mousemove', '0', '0')
    time.sleep(.35)
    window = game.xdo('search', '--name', 'Minecraft').split()[0]
    subprocess.run(['import', '-silent', '-window', window, str(evidence.out / name)],
                   env=game.env, check=True, timeout=30)
    return name


def _player_formulas(evidence, username):
    player = read_data(evidence.rc('data get entity ' + username))
    found = []

    def visit(value):
        if isinstance(value, dict):
            for key, child in value.items():
                if key == 'myvillage:learned_formulas':
                    if not isinstance(child, list) or not all(isinstance(v, str) for v in child):
                        raise ValueError('Malformed saved player formula knowledge')
                    found.extend(child)
                else:
                    visit(child)
        elif isinstance(value, list):
            for child in value:
                visit(child)

    visit(player)
    return sorted(found)


def _connected(evidence, username):
    # A native entity selector avoids usernames accidentally matching list text.
    reply = evidence.rc(f'execute if entity @a[name={username}] run say ARRAY_PLAYER_ONLINE_{username}')
    return 'ARRAY_PLAYER_ONLINE_' + username in reply


def _reconnect(evidence):
    """PauseScreen disconnect returns to JoinMultiplayerScreen in MC 1.21.1."""
    game = evidence.s.game
    _, width, height = _scale(game)
    # The multiplayer footer has a 44px two-row layout centered in its 64px
    # footer; the middle button of its first row is vanilla Direct Connect.
    _screen_click(evidence, game, width / 2, height - 44,
                  'owner_direct_connect_screen', evidence.s.username)
    game.xdo('key', 'ctrl+a')
    game.xdo('type', '--delay', '30', '127.0.0.1:' + str(evidence.s.state['server_port']))
    _screen_click(evidence, game, width / 2, height // 4 + 118,
                  'owner_reconnect_to_same_server', evidence.s.username)
    evidence.poll(lambda: _connected(evidence, evidence.s.username), bool, 45,
                  'main player reconnects to same live server')
    if not (game.wait_ingame(30, poll=1) or game.recover_grab()):
        raise RuntimeError('Reconnected main client did not reach game')


def run_multiplayer(evidence):
    """Run ``two_players``; append checks/screenshots and return native evidence.

    Integrate using ``def two_players(self): run_multiplayer(self)``. The caller
    marks selected/completed/unrun in its normal case dispatch. Screenshot review
    and broad multiplayer/owner acceptance remain separate from native checks.
    """
    from tools.alchemy_array_evidence import POINTS, frozen_identity, saved_items

    started = time.monotonic()
    recipe = evidence.prepare()  # Includes actual held-formula mapped learning.
    evidence.materials(recipe, 2)
    evidence.select()
    owner = evidence.s.username
    owner_uuid = read_data(evidence.rc('data get entity ' + owner + ' UUID'))
    evidence.check('array_owner_formula_was_really_learned',
                   recipe['id'] in _player_formulas(evidence, owner), {'owner': owner})
    result = {'method': 'two_real_clients_mapped_menus_disconnect_and_reconnect',
              'owner': owner, 'screenshots': [], 'tick_rates': [5, 80, 5, 80, 20],
              'ui_values': 'pending_screenshot_review', 'broad_multiplayer': 'not_verified',
              'status': 'running'}
    evidence.report['array_multiplayer'] = result

    class ArrayGuest(SecondClient):
        # A new player identity prevents knowledge from earlier cases/runs from
        # making an allegedly unlearned guest silently authorized.
        username = 'ArrGuest' + secrets.token_hex(3)

    disconnected = False
    try:
        with ArrayGuest(evidence) as guest:
            user, game = guest.username, guest.game
            result['guest'] = user
            for command in (f'gamemode survival {user}', f'clear {user}',
                            f'myvillage cultivation awaken {user}',
                            f'myvillage cultivation crafting set {user} alchemy 0',
                            f'deop {user}'):
                evidence.rc(command)
            evidence.check('array_guest_has_no_learned_formulas',
                           _player_formulas(evidence, user) == [], {'guest': user})
            # Move the owner aside without closing its menu so the second real
            # player can raycast the same controller unobstructed.
            evidence.rc(f'tp {owner} -2 -60 .5')
            evidence.rc(f'tp {user} .5 -60 .5 0 2.3')
            time.sleep(.5)
            game.xdo('click', '3')
            evidence.report['mapped_input'].append({'kind': 'guest_open_shared_array', 'actor': user})
            evidence.poll(game.pointer_grabbed, lambda value: value is False, 10,
                          'guest opens shared controller')
            before = evidence.saved()
            _menu_click(evidence, game, POINTS['recipe'], 'guest_unknown_recipe_position', user)
            _menu_click(evidence, game, POINTS['start'], 'guest_unknown_formula_start', user)
            after = evidence.saved()
            evidence.check('array_unlearned_guest_cannot_select_or_start', before == after
                           and not after.get('Enabled') and 'Batch' not in after,
                           {'before': before, 'after': after})
            result['screenshots'].append(_capture(evidence, game, 'array-guest-unlearned-menu.png'))

            # Keep both menus open, with the guest standing clear of the owner.
            evidence.rc(f'tp {user} -2 -60 .5')
            evidence.rc('tick rate 5')
            evidence.click(*POINTS['start'], label='owner_authorizes_shared_array')
            first = evidence.poll(evidence.saved, lambda data: isinstance(data.get('Batch'), dict),
                                  8, 'owner starts authorized multiplayer batch')
            first_identity = frozen_identity(first)
            evidence.check('array_shared_batch_belongs_to_owner',
                           first['Batch']['Brewer'] == owner_uuid and first['Authorizer'] == owner_uuid
                           and first['Score'] == 100 and first['Cap'] == 4, first_identity)
            _menu_click(evidence, game, POINTS['start'], 'guest_cannot_replace_active_authorization', user)
            _menu_click(evidence, game, POINTS['recipe'], 'guest_cannot_change_active_recipe', user)
            for point in ((162, 46), (186, 46), (230, 46)):
                _menu_click(evidence, game, point, 'guest_cannot_take_reserved_input', user, shift=True)
            guarded = evidence.saved()
            guest_inventory = quality_stacks(evidence.rc('data get entity ' + user + ' Inventory'))
            evidence.check('array_guest_cannot_replace_batch_or_take_reserved_inputs',
                           frozen_identity(guarded) == first_identity and saved_items(guarded) == saved_items(first)
                           and not guest_inventory, {'batch': guarded, 'guest_inventory': guest_inventory})
            progress = evidence.poll(evidence.saved, lambda data: data.get('Progress', 0) > 5,
                                     8, 'shared batch progress')
            result['shared_progress'] = progress
            result['screenshots'].append(_capture(evidence, evidence.s.game, 'array-owner-shared-progress.png'))
            result['screenshots'].append(_capture(evidence, game, 'array-guest-shared-progress.png'))

            # PauseScreen places Disconnect last, including NeoForge's Mods
            # row. Backward keyboard focus selects that last enabled widget;
            # actual disconnect is confirmed independently on the live server.
            evidence.close_menu()
            evidence.s.game.key('Escape')
            time.sleep(.4)
            evidence.s.game.xdo('key', 'shift+Tab')
            evidence.s.game.key('Return')
            evidence.report['mapped_input'].append({'kind': 'owner_disconnect_via_pause_menu', 'actor': owner})
            evidence.poll(lambda: _connected(evidence, owner), lambda value: not value, 15,
                          'main player actually disconnects')
            disconnected = True
            offline = evidence.saved()
            evidence.check('array_owner_disconnect_keeps_authorization_and_current_batch',
                           _connected(evidence, user) and frozen_identity(offline) == first_identity
                           and bool(offline.get('Enabled')), offline)
            evidence.rc('tick rate 80')
            def second_started(data):
                return isinstance(data.get('Batch'), dict) and sum(
                    row['count'] for i, row in saved_items(data).items()
                    if i >= 3 and row['id'] == recipe['output']) == recipe['output_count']
            second = evidence.poll(evidence.saved, second_started,
                                   recipe['duration_ticks'] / 80 + 15, 'offline authorizer automatically starts second batch')
            evidence.rc('tick rate 5')
            second_identity = frozen_identity(second)
            first_output = [row for i, row in saved_items(second).items() if i >= 3]
            evidence.check('array_owner_offline_completes_first_and_authorizes_second',
                           not _connected(evidence, owner) and _connected(evidence, user)
                           and second['Batch']['Brewer'] == owner_uuid and second['Score'] == 100
                           and second['Cap'] == 4 and second['Authorizer'] == owner_uuid
                           and all(pill_quality(row) == first['Batch']['Quality'] for row in first_output)
                           and saved_items(second).get(0, {}).get('count') == 1
                           and saved_items(second).get(1, {}).get('count') == 1, second)
            result['offline_second_batch'] = second
            result['screenshots'].append(_capture(evidence, game, 'array-guest-owner-offline-second-batch.png'))

            _reconnect(evidence)
            disconnected = False
            rejoined = evidence.saved()
            evidence.check('array_owner_reconnect_does_not_reroll_active_second_batch',
                           frozen_identity(rejoined) == second_identity
                           and rejoined['Progress'] >= second['Progress'],
                           {'before': second, 'after': rejoined})
            evidence.open()
            evidence.stop_next()
            evidence.rc('tick rate 80')
            expected = 2 * recipe['output_count']
            done = evidence.poll(evidence.saved, lambda data: 'Batch' not in data and sum(
                row['count'] for i, row in saved_items(data).items()
                if i >= 3 and row['id'] == recipe['output']) == expected,
                recipe['duration_ticks'] / 80 + 15, 'two conserved multiplayer batches')
            evidence.rc('tick rate 20')
            evidence.check('array_two_shared_batches_conserve_inputs_and_outputs',
                           not done.get('Enabled') and all(i not in saved_items(done) for i in range(3)), done)
            result['screenshots'].append(_capture(evidence, evidence.s.game, 'array-owner-shared-output.png'))
            result['screenshots'].append(_capture(evidence, game, 'array-guest-shared-output.png'))
            # A genuine second-menu output transaction proves both client
            # sessions reference the same inventory, beyond merely opening UI.
            for slot in range(4):
                _menu_click(evidence, game, (284 + slot * 18, 84), 'guest_extract_shared_output', user, shift=True)
            remaining = evidence.saved()
            received = quality_stacks(evidence.rc('data get entity ' + user + ' Inventory'))
            evidence.check('array_guest_takes_only_shared_finished_output',
                           not saved_items(remaining) and sum(row['count'] for row in received.values()
                           if row['id'] == recipe['output']) == expected,
                           {'controller': remaining, 'guest_inventory': received})
            result['screenshots'].append(_capture(evidence, evidence.s.game, 'array-owner-observes-guest-output-extraction.png'))
            result['final'] = {'controller': remaining, 'guest_inventory': received}
            evidence.close_menu()
            if game.pointer_grabbed() is False:
                game.key('Escape')
                time.sleep(.25)
    except BaseException as error:
        result['status'] = 'failed'
        result['failure'] = repr(error)
        raise
    finally:
        # Restoring test speed never starts/stops the caller-owned server. On
        # failure, leave the main client usable when reconnect remains possible.
        try:
            evidence.rc('tick rate 20')
            if disconnected:
                _reconnect(evidence)
        finally:
            result['wall_s'] = round(time.monotonic() - started, 3)
            evidence.save()
    result['wall_s'] = round(time.monotonic() - started, 3)
    result['status'] = 'passed_native_checks_screenshot_review_pending'
    evidence.report['array_multiplayer'] = result
    evidence.report['multiplayer'] = 'two_actual_clients_shared_inventory_and_offline_authorization_observed'
    evidence.save()
    return result
