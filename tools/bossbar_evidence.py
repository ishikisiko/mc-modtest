#!/usr/bin/env python3
"""Modal-only A HUD fixtures: server changes + actual client frames, not combat acceptance."""
from pathlib import Path
import json
import secrets
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))
from PIL import Image
from tools.combat_capture import procs, scene, session as cs
from tools.thunder_tiger_evidence import TigerEvidence, diagnostic_boss_patch


def cinnabar_pixels(path, expanded=False):
    """Use the top HUD band, excluding the observed red death tint on world mobs.

    Expanded mode is only for explicit stacked/Qi layout frames with living mobs.
    """
    with Image.open(path).convert('RGB') as image:
        region = image.crop((0, 0, image.width, image.height // (2 if expanded else 5)))
        return sum(108 <= r <= 210 and 35 <= g <= 120 and r > g * 1.45 and b < g + 10
                   for r, g, b in region.getdata())


class BossHudEvidence(TigerEvidence):
    def __init__(self):
        super().__init__(ROOT/'out/preview/bossbar-a/runtime', 'visuals')
        self.target = '@e[type=myvillage:thunder_tiger,tag=boss_hud_fixture,limit=1]'
        self.report.update(kind='bossbar_a', selected=['hud'], completed=[], unrun=['hud'],
                           method=__doc__, fixture='disposable flat platform, creative observer, command-driven health/phase/horn')

    def start(self):
        if ROOT != Path('/workspace/repo'):
            raise RuntimeError('Minecraft capture runs only in the Modal disposable checkout')
        state = cs.start(cs.SessionConfig(session_id=secrets.token_hex(6), language='zh_cn',
                                         lock=str(procs.default_lock_path(ROOT))))
        self.s = cs.Session.attach(ui_check=True)
        self.sessions += 1
        self.report['phases'].append({'phase': 'setup', 'capture_timings': state.get('timings', {})})
        self.report['source_commit'] = json.loads(Path('/workspace/request.json').read_text())['commit']
        self.rc(f'op {self.s.username}')
        self.rc('gamerule doMobSpawning false')
        self.rc('time set noon')
        self.rc('weather clear')
        self.rc(f'gamemode creative {self.s.username}')
        self.rc(f'tp {self.s.username} 256.5 201 274.5 180 5')
        time.sleep(2)
        self.rc('fill 224 200 224 288 200 288 minecraft:stone')
        self.rc(f'tp {self.s.username} 256.5 201 274.5 180 5')
        self.rc('summon myvillage:thunder_tiger 256.5 201 256.5 {Silent:1b,PersistenceRequired:1b,Tags:["boss_hud_fixture"]}')
        self.poll(self.status, lambda s: s.get('max', 0) > 0, 15, 'boss attributes initialized')
        self.rc(f'attribute {self.target} minecraft:generic.movement_speed base set 0')
        time.sleep(2)
        self.maximum = self.status()['max']
        self.rc('tick freeze')

    def frame(self, name, expanded=False):
        self.rc('tick step 4')
        time.sleep(1.1)
        self.shot(name, fixture='server prepared HUD state; not natural combat')
        count = cinnabar_pixels(self.out/(name+'.png'), expanded)
        self.report.setdefault('pixel_counts', {})[name] = count
        self.save()
        return count

    def state(self, fraction, phase=1, broken=False):
        self.rc(f'data merge entity {self.target} {diagnostic_boss_patch(self.maximum*fraction,phase,broken)}')

    def run(self):
        self.start()
        full = self.frame('01_full')
        self.must('full_bar_visible', full > 200, full)
        self.state(.72)
        wounded = self.frame('02_wounded')
        self.must('server_damage_shrinks_red_fill', .5*full < wounded < .9*full, [full,wounded])
        self.state(.18,2,True)
        low = self.frame('03_enraged_broken')
        state = self.status()
        self.must('server_phase_and_horn_fixture', state['boss_phase']==2 and state['horn_broken'],state)
        self.must('low_health_remains_readable', 0 < low < .45*full,[full,low])
        self.state(1)
        healed = self.frame('04_healed')
        self.must('healing_restores_fill', abs(full-healed) < .08*full,[full,healed])

        self.rc('bossbar add myvillage:hud_control {"text":"原版对照"}')
        self.rc(f'bossbar set myvillage:hud_control players {self.s.username}')
        self.rc('bossbar set myvillage:hud_control color green')
        self.rc('summon myvillage:thunder_tiger 263.5 201 256.5 {Silent:1b,PersistenceRequired:1b,Tags:["boss_hud_second"]}')
        self.rc('attribute @e[tag=boss_hud_second,limit=1] minecraft:generic.movement_speed base set 0')
        stacked = self.frame('05_two_bosses_and_vanilla', expanded=True)
        self.must('two_boss_events_render_separately', stacked > 1.6*full, [full,stacked])
        self.rc('kill @e[tag=boss_hud_second]')
        self.rc('bossbar remove myvillage:hud_control')
        self.rc('tick step 40')
        time.sleep(2.2)

        self.hud(True)
        hidden = self.frame('06_f1_hidden')
        self.must('f1_hides_custom_bar',hidden < 10,hidden)
        self.hud(False)
        self.rc(f'tp {self.s.username} 256.5 201 374.5 180 5')
        away = self.frame('07_out_of_range')
        self.must('observer_distance_removes_bar',away < 10,away)
        self.rc(f'tp {self.s.username} 256.5 201 274.5 180 5')
        returned = self.frame('08_return')
        self.must('return_restores_current_bar',returned > .9*full,returned)
        self.rc(f'gamemode spectator {self.s.username}')
        spectator = self.frame('09_spectator')
        self.must('spectator_removes_bar',spectator < 10,spectator)
        self.rc(f'gamemode creative {self.s.username}')
        self.frame('10_creative_return')

        self.rc('tick unfreeze')
        user=self.s.username
        for command in (f'myvillage cultivation awaken {user}',f'myvillage cultivation initiate {user}',
                        f'myvillage cultivation setrealm {user} myvillage:qi_refining myvillage:qi_refining_1'):
            self.rc(command)
        self.report['qi_combat_mode']=scene.ensure_cultivation(self.s)
        self.must('qi_mode_confirmed',scene.combat_mode(self.s)=='cultivation',self.report['qi_combat_mode'])
        self.rc('tick freeze')
        qi=self.frame('11_qi_coexistence',expanded=True)
        self.must('qi_layout_keeps_boss_bar',qi > .9*full,qi)

        self.rc(f'kill {self.target}')
        self.rc('tick step 40')
        time.sleep(2.2)
        dead = self.frame('12_dead')
        self.must('death_removes_bar',dead < 10,dead)
        self.report.update(completed=['hud'],unrun=[])


def main():
    evidence=BossHudEvidence()
    try:
        evidence.run()
        evidence.report['status']='pass'
    except BaseException as error:
        evidence.report['status']='fail'
        evidence.report['failures'].append(repr(error))
        raise
    finally:
        if evidence.s:
            try:
                evidence.rc('tick unfreeze')
                evidence.stop()
            except BaseException as error:
                evidence.report['failures'].append('cleanup: '+repr(error))
        evidence.save()
    return int(bool(evidence.report['failures']))


if __name__=='__main__':
    raise SystemExit(main())
