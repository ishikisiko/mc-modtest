#!/usr/bin/env python3
"""Armor-pressure developer evidence: mapped client input and explicit admin fixtures.

Run clean acceptance stage 1 first. This is not equipment acquisition, combat
feel or multiplayer acceptance. No DevBridge; failures and commands are retained.
"""
from __future__ import annotations
import argparse
import datetime as dt
import json
from pathlib import Path
import secrets
import re
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))
from tools.combat_capture import procs, scene, session as cs
from tools.realm_stats_evidence import Evidence, close

TARGET = '@e[tag=armor_fixture,limit=1]'
ATTACKER = '@e[tag=armor_attacker,limit=1]'


def armor_damage(damage, armor, toughness, scale=1, breach=0):
    """Independent vanilla oracle; Breach subtracts from effectiveness once."""
    effective = min(20, max(armor * .2, armor - (damage / scale) / (2 + toughness / 4)))
    reduction = min(1, max(0, effective / 25 - breach * .15))
    return damage * (1 - reduction)


class ArmorEvidence(Evidence):
    def __init__(self, out):
        super().__init__(out)
        self.report.update(kind='realm_normalized_armor', preparation='administrator realm and equipment fixtures',
                           method='physical client mapped mouse input; RCON fixtures and readback; no DevBridge',
                           equipment_acquisition='not_verified', combat_feel='not_verified',
                           partial_shield_runtime='not_verified', nested_hit_runtime='not_verified',
                           beast_move_runtime='not_verified', other_attack_enchantments_runtime='not_verified')
        self.report.pop('natural_encounter_density', None)

    def start(self, reuse=False):
        started = time.monotonic()
        state = cs.start(cs.SessionConfig(session_id=secrets.token_hex(6), reuse_world=reuse,
                                         lock=str(procs.default_lock_path(ROOT))))
        self.s = cs.Session.attach(ui_check=True)
        self.sessions += 1
        self.report['phases'].append({'phase': 'initial_setup', 'wall_s': round(time.monotonic()-started, 3),
                                     'capture_timings': state.get('timings', {})})
        self.save()

    def trace(self, target=TARGET):
        return self.one(f'myvillage stats trace {target}', 'STATS_TRACE')

    def hp(self, target=TARGET):
        return self.number(f'data get entity {target} Health')

    def fixture(self, armor=20, toughness=8, realm='golden_core', stage=2, assignment=None, z=3):
        self.rc('kill @e[tag=armor_fixture]')
        self.rc(f'summon minecraft:iron_golem .5 -60 {z} '
                '{NoAI:1b,PersistenceRequired:1b,Tags:["armor_fixture"]}')
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            if scene.parse_float_data(self.rc(f'data get entity {TARGET} Health')) is not None:
                break
            time.sleep(.25)
        else:
            raise RuntimeError('Fixture did not enter ticking within 15 seconds')
        self.rc(f'attribute {TARGET} minecraft:generic.max_health base set 1000')
        self.rc(f'data merge entity {TARGET} {{Health:1000f}}')
        self.rc(f'attribute {TARGET} minecraft:generic.armor base set {armor}')
        self.rc(f'attribute {TARGET} minecraft:generic.armor_toughness base set {toughness}')
        if assignment is None and realm is not None:
            assignment = '{realm:"myvillage:' + realm + '",stage:' + str(stage) + '}'
        if assignment is not None:
            self.rc(f'data merge entity {TARGET} {{NeoForgeData:{{"myvillage:stats_realm":{assignment}}}}}')
        values = {key: self.number(f'attribute {TARGET} minecraft:generic.{key} get')
                  for key in ('max_health','armor','armor_toughness')}
        if not all(close(values[k],v) for k,v in [('max_health',1000),('armor',armor),('armor_toughness',toughness)]):
            raise RuntimeError(f'Fixture attributes mismatch: {values}')
        health = self.hp()
        if not close(health,1000):
            raise RuntimeError(f'Fixture health mismatch: {health}')
        self.rc(f'data get entity {TARGET} NeoForgeData')
        return health

    def probe(self, name, damage, *, scale=6.6, armor=20, toughness=8, realm='golden_core', stage=2,
              assignment=None, source='minecraft:mob_attack', breach=0, bypass=False):
        before = self.fixture(armor, toughness, realm, stage, assignment)
        self.rc(f'item replace entity {ATTACKER} weapon.mainhand with minecraft:mace'
                + ('[minecraft:enchantments={levels:{"minecraft:breach":4}}]' if breach else ''))
        self.rc(f'damage {TARGET} {damage} {source} by {ATTACKER}')
        trace, after = self.trace(), self.hp()
        expected = damage if bypass else armor_damage(damage, armor, toughness, scale, breach)
        effective = min(20,max(armor*.2,armor-(damage/scale)/(2+toughness/4)))
        observed = trace.get('armor')
        expected_status = 'invalid_assignment' if assignment is not None else ('unassigned' if realm is None else 'valid')
        armor_ok = observed is None if bypass else (isinstance(observed,dict)
                    and close(observed.get('damageAtArmor'),damage,.005)
                    and close(observed.get('defenderScale'),scale)
                    and close(observed.get('pressureDamage'),damage/scale,.005)
                    and close(observed.get('armorOutput'),expected,.005)
                    and close(observed.get('normalizedEffectiveArmor'),effective,.005)
                    and observed.get('assignmentStatus') == expected_status)
        self.check(name, armor_ok and close(before-after, expected, .005) and close(trace.get('postVanilla'), expected, .005),
                   {'damage': damage, 'scale': scale, 'armor': armor, 'toughness': toughness,
                    'before': before, 'after': after, 'expected': expected, 'trace': trace})
        return trace

    def player_fixture(self, protection=False):
        user = self.s.username
        self.rc(f'effect clear {user}')
        self.rc(f'myvillage cultivation setrealm {user} myvillage:golden_core myvillage:golden_late')
        self.rc(f'attribute {user} minecraft:generic.armor base set 10')
        self.rc(f'attribute {user} minecraft:generic.armor_toughness base set 0')
        self.rc(f'attribute {user} myvillage:physical_resistance base set 0')
        for slot, item in [('head','helmet'), ('chest','chestplate'), ('legs','leggings'), ('feet','boots')]:
            suffix = '[minecraft:enchantments={levels:{"minecraft:protection":4}}]' if protection else ''
            self.rc(f'item replace entity {user} armor.{slot} with minecraft:netherite_{item}{suffix}')
        self.rc(f'effect give {user} minecraft:instant_health 1 10 true')
        time.sleep(1.2)
        armor = self.number(f'attribute {user} minecraft:generic.armor get')
        tough = self.number(f'attribute {user} minecraft:generic.armor_toughness get')
        if not close(armor, 30) or not close(tough, 12):
            raise RuntimeError(f'Equipment fixture mismatch {armor}/{tough}')
        return self.hp(user)

    def initial(self):
        started = time.monotonic(); user = self.s.username
        for cmd in (*scene.world_rules(), *scene.player_setup(user, 'myvillage:qingfeng_sword'),
                    'gamerule naturalRegeneration false', 'myvillage world pause',
                    'forceload add -16 -16 32 32', f'myvillage cultivation awaken {user}',
                    f'myvillage cultivation initiate {user}',
                    'summon minecraft:armor_stand .5 -60 3 {Invulnerable:1b,NoGravity:1b,Tags:["armor_attacker"]}'):
            self.rc(cmd)
        for realm, stage, scale in [('mortal',0,1),('qi_refining',0,1.5),
                                     ('foundation_establishment',0,3),('golden_core',2,6.6)]:
            self.probe('same_realm_'+realm, 10*scale, realm=realm, stage=stage, scale=scale)
        self.probe('unassigned_vanilla', 60, realm=None, scale=1, armor=30, toughness=12)
        for name, assignment in [('unknown_realm','{realm:"myvillage:no_such_realm",stage:0}'),
                                 ('missing_stage','{realm:"myvillage:golden_core"}'),
                                 ('wrong_stage_type','{realm:"myvillage:golden_core",stage:"2"}'),
                                 ('out_of_range','{realm:"myvillage:golden_core",stage:999}')]:
            self.probe('fallback_'+name, 60, assignment=assignment, scale=1, armor=30, toughness=12)
        self.probe('high_attacker_low_defender', 66, realm='qi_refining', stage=0, scale=1.5)
        self.probe('low_attacker_high_defender', 15, armor=30, toughness=12)
        for damage in (66,198,330,331):
            self.probe('armor30_tough12_'+str(damage), damage, armor=30, toughness=12)
        self.probe('heavy_old_gear', 198)
        self.probe('heavy_zero_toughness', 198, armor=30, toughness=0)
        self.probe('breach_four_once', 66, armor=30, toughness=12, breach=4)
        self.probe('armor_eligible_unmapped_explosion', 66, armor=30, toughness=12, source='minecraft:explosion')
        self.probe('armor_bypass_magic', 66, armor=30, toughness=12, source='minecraft:magic', bypass=True)
        self.rc(f'item replace entity {ATTACKER} weapon.mainhand with minecraft:stick')

        # Two damage calls in one frozen tick prove the armor input is the iframe delta.
        before = self.fixture()
        self.rc('tick freeze')
        try:
            self.rc(f'damage {TARGET} 40 minecraft:mob_attack by {ATTACKER}')
            first = self.trace()
            self.rc(f'damage {TARGET} 60 minecraft:mob_attack by {ATTACKER}')
            second, after = self.trace(), self.hp()
        finally:
            self.rc('tick unfreeze')
        expected = armor_damage(40,20,8,6.6) + armor_damage(20,20,8,6.6)
        self.check('iframe_difference_preserved', close(before-after, expected, .005)
                   and close((second.get('armor') or {}).get('damageAtArmor'),20),
                   {'expected_loss':expected,'actual_loss':before-after,'first':first,'second':second})

        for amount, wear in [(66,16),(198,49)]:
            before = self.player_fixture()
            self.rc(f'damage {user} {amount} minecraft:mob_attack by {ATTACKER}')
            trace, after = self.trace(user), self.hp(user)
            worn = self.rc(f'data get entity {user} Inventory')
            expected = armor_damage(amount,30,12,6.6)
            damages = [int(x) for x in re.findall(r'"minecraft:damage": (\d+)', worn)]
            self.check('player_equipment_wear_'+str(amount), close(before-after,expected,.005)
                       and len(damages)==4 and all(x==wear for x in damages),
                       {'expected_loss':expected,'loss':before-after,'wear':damages,'expected_wear':wear,'trace':trace})
        before = self.player_fixture(protection=True)
        self.rc(f'effect give {user} minecraft:resistance 60 0 true')
        self.rc(f'effect give {user} minecraft:absorption 60 0 true')
        self.rc(f'attribute {user} myvillage:physical_resistance base set .5')
        self.rc(f'damage {user} 198 minecraft:mob_attack by {ATTACKER}')
        trace, after = self.trace(user), self.hp(user)
        post = armor_damage(198,30,12,6.6)*.8*.36
        self.check('protection_potion_typed_absorption_once', close(trace.get('postVanilla'),post,.005)
                   and close(trace.get('afterResistance'),post*.5,.005)
                   and close(before-after,post*.5-4,.005), {'trace':trace,'expected_post':post,'loss':before-after})

        # Native shield use is mapped right mouse; no combat payload is manufactured.
        before = self.player_fixture()
        self.rc(f'item replace entity {user} hotbar.0 with minecraft:shield')
        self.rc(f'tp {user} .5 -60 .5 0 0')
        self.s.game.key('1'); self.s.game.require_ingame('mapped shield block')
        self.s.game.xdo('mousedown','3')
        try:
            time.sleep(.7)
            self.rc(f'damage {user} 66 minecraft:mob_attack by {ATTACKER}')
            after = self.hp(user)
            shield_wear = self.number(f'data get entity {user} SelectedItem.components."minecraft:damage"')
            inventory = self.rc(f'data get entity {user} Inventory')
            damages = [int(x) for x in re.findall(r'"minecraft:damage": (\d+)',inventory)]
        finally:
            self.s.game.xdo('mouseup','3')
        self.check('mapped_shield_full_block',close(before,after) and close(shield_wear,67)
                   and damages.count(67)==1 and all(x in (0,67) for x in damages),
                   {'before':before,'after':after,'shield_wear':shield_wear,'inventory_wear':damages})

        self.rc(f'effect clear {user}')
        self.rc(f'attribute {user} myvillage:physical_resistance base set 0')
        self.rc(f'item replace entity {user} hotbar.0 with myvillage:qingfeng_sword')
        self.rc(f'tp {ATTACKER} 8 -60 8')
        self.rc(f'tp {user} .5 -60 .5 0 0')
        self.s.game.key('1'); scene.ensure_cultivation(self.s)
        before = self.fixture(30,12)
        base = self.number(f'attribute {user} minecraft:generic.attack_damage get')
        style = json.loads((ROOT/'src/main/resources/data/myvillage/combat/style/basic_sword.json').read_text())
        requested = base * style['moves'][0]['damage_multiplier']
        self.s.game.require_ingame('normalized armor mapped melee'); self.s.game.click(.06)
        time.sleep(2)
        trace, after = self.trace(), self.hp()
        expected = armor_damage(requested,30,12,6.6)
        self.check('mapped_melee_normalized_armor',close(before-after,expected,.005)
                   and close((trace.get('armor') or {}).get('defenderScale'),6.6),
                   {'base':base,'requested':requested,'expected':expected,'loss':before-after,'trace':trace})

        before = self.fixture(30,12,z=6.5)
        self.rc(f'tp {user} .5 -60 .5 0 0')
        self.rc(f'myvillage cultivation learn {user} myvillage:jianqi_jue')
        self.rc(f'myvillage cultivation qi fill {user}')
        self.s.game.require_ingame('normalized armor mapped sword qi')
        self.s.game.xdo('mousedown','3')
        try:
            time.sleep(.35)
        finally:
            self.s.game.xdo('mouseup','3')
        time.sleep(2.5)
        trace, after = self.trace(), self.hp()
        requested = (trace.get('outgoing') or {}).get('requestedDamage',0)
        expected = armor_damage(requested,30,12,6.6)
        self.check('mapped_sword_qi_normalized_armor',requested>0 and close(before-after,expected,.005)
                   and close((trace.get('armor') or {}).get('defenderScale'),6.6),
                   {'expected':expected,'loss':before-after,'trace':trace})
        self.report['phases'].append({'phase':'armor_checks','wall_s':round(time.monotonic()-started,3)})
        self.save()


def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',type=Path,default=ROOT/'out/preview/realm_armor')
    args=parser.parse_args(argv)
    if not procs.lock_is_free(procs.default_lock_path(ROOT)):
        raise SystemExit('Heavy-work lock held')
    evidence=ArmorEvidence(args.out)
    try:
        evidence.start(); evidence.initial()
    except Exception as exc:
        evidence.report['failures'].append(f'{type(exc).__name__}: {exc}')
        raise
    finally:
        try:
            if evidence.s is not None or cs.supervisor_alive(cs.read_state()): evidence.stop()
        finally:
            evidence.report['finished_utc']=dt.datetime.now(dt.timezone.utc).isoformat()
            evidence.report['pass']=not evidence.report['failures'] and bool(evidence.report['checks']) and all(c['pass'] for c in evidence.report['checks'])
            evidence.save()
    return 0 if evidence.report['pass'] else 1

if __name__=='__main__':
    raise SystemExit(main())
