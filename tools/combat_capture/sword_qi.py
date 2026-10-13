"""Mapped-right-mouse sword-qi evidence. Admin-prepared boundary trials only.
The manifest never equates these trials with natural acquisition or owner acceptance.
"""
from __future__ import annotations

import datetime
import html
import json
import re
import subprocess
import time
from pathlib import Path

from . import scene
from .capture import ffmpeg_record_argv, press_f5_to
from .session import LOG_DIR, REPO

DEFAULT_OUT = REPO / 'out/preview/sword_qi'
TECHNIQUE = 'myvillage:jianqi_jue'
WEAPON = 'myvillage:qingfeng_sword'
TARGET = '@e[tag=sword_qi_target,limit=1]'
FIELD = re.compile(r'(\w+)=([^\s]+)')
RATE = 10


def parse_events(lines):
    return [dict(FIELD.findall(line.split('SWORD_QI ', 1)[1]))
            for line in lines if 'SWORD_QI ' in line]


def parse_power(reply):
    values = dict(FIELD.findall(reply))
    try:
        return int(values['current'].rstrip(','))
    except (KeyError, ValueError):
        return None


def trial_plan():
    return [
        {'id': 'not_learned', 'learned': False, 'expect': 'client_gate'},
        {'id': 'tap_hit', 'expect': 'hit'},
        {'id': 'charged_hit', 'hold': 2.4, 'expect': 'hit'},
        {'id': 'wall', 'wall': 'full', 'expect': 'wall'},
        {'id': 'edge_wall', 'wall': 'edge', 'target_x': 1.25, 'expect': 'wall'},
        {'id': 'out_of_range', 'target_z': 11.5, 'expect': 'range'},
        {'id': 'same_weapon_slot_cancel', 'cancel': 'same_weapon', 'expect': 'cancel'},
        {'id': 'insufficient', 'power': 0, 'expect': 'POWER'},
        {'id': 'switch_cancel', 'cancel': 'switch', 'expect': 'cancel'},
        {'id': 'damage_cancel', 'cancel': 'damage', 'expect': 'cancel'},
        {'id': 'menu_cancel', 'cancel': 'menu', 'expect': 'cancel'},
        {'id': 'dodge_cancel', 'cancel': 'dodge', 'expect': 'cancel'},
    ]


def expectation_met(trial, events, health_before, health_after):
    """Fail closed: both explicit server attribution and measured health required."""
    if health_before is None or health_after is None:
        return False
    result = trial['expect']
    released = [e for e in events if e.get('result') == 'released']
    hits = [e for e in events if e.get('result') == 'hit']
    if result == 'client_gate':
        return not events and health_after == health_before
    if result == 'hit':
        return len(released) == 1 and bool(hits) and health_after < health_before
    if result == 'wall':
        return bool(released) and any(e.get('result') == 'wave_stop' and e.get('reason') == 'WALL'
                                     for e in events) and not hits and health_after == health_before
    if result == 'range':
        return len(released) == 1 and any(e.get('result') == 'wave_stop' and e.get('reason') == 'RANGE'
                                        for e in events) and not hits and health_after == health_before
    if result == 'cancel':
        return any(e.get('result') == 'charging' for e in events) and any(
            e.get('result') == 'cancelled' for e in events) and not released and not hits and health_after == health_before
    return any(e.get('result') == 'rejected' and e.get('reason') == result for e in events
               ) and not released and not hits and health_after == health_before


class SwordQiCapture:
    def __init__(self, session, out, log=print):
        self.s, self.g, self.user, self.out, self.log = session, session.game, session.username, Path(out), log
        self.out.mkdir(parents=True, exist_ok=True)
        self.manifest = {'kind': 'sword_qi', 'created_utc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
                         'method': 'mapped right mouse down/up; server RCON preparation and health; no DevBridge or pose probe',
                         'preparation': 'admin boundary fixtures (learn/fill/targets); not natural acquisition evidence',
                         'owner_verdict': 'not_verified', 'natural_acquisition': 'not_verified',
                         'restart_persistence': 'not_verified', 'two_clients': 'not_verified',
                         'commands': [], 'trials': [], 'session_timings': session.state.get('timings', {})}

    def run(self, *cmds):
        replies = self.s.run(*cmds, warn=False)
        self.manifest['commands'].extend({'command': c, 'reply': r} for c, r in zip(cmds, replies))
        return replies

    def health(self):
        return scene.parse_float_data(self.run(f'data get entity {TARGET} Health')[0])

    def profile(self):
        return self.run(f'myvillage cultivation qi {self.user}')[0]

    def setup(self):
        self.run(*scene.world_rules(), *scene.player_setup(self.user, WEAPON),
                 'gamerule naturalRegeneration false', 'gamerule doMobLoot false',
                 f'myvillage cultivation awaken {self.user}', f'myvillage cultivation initiate {self.user}',
                 f'myvillage cultivation learn {self.user} myvillage:taxue_wuhen')
        self.g.key('1')
        scene.ensure_cultivation(self.s)
        press_f5_to(self.s, 'first')

    def prepare(self, trial):
        self.run('tick rate 20', 'tick unfreeze', 'kill @e[tag=sword_qi_target]',
                 'fill -3 -60 2 3 -56 4 minecraft:air', f'tp {self.user} 0.5 -60 0.5 0 0',
                 f'item replace entity {self.user} hotbar.0 with {WEAPON}',
                 f'item replace entity {self.user} hotbar.1 with {WEAPON if trial.get("cancel") == "same_weapon" else "minecraft:stick"}',
                 f'effect give {self.user} minecraft:instant_health 1 10 true')
        self.g.key('1')
        self.run(f'myvillage cultivation {"learn" if trial.get("learned", True) else "forget"} {self.user} {TECHNIQUE}',
                 (f'myvillage cultivation qi set {self.user} {trial["power"]}' if 'power' in trial else f'myvillage cultivation qi fill {self.user}'))
        x = trial.get('target_x', .5)
        self.run(f'summon minecraft:iron_golem {x} -60 {trial.get("target_z", 6.5)} {{NoAI:1b,PersistenceRequired:1b,Tags:["sword_qi_target"]}}')
        if trial.get('wall') == 'full':
            self.run('fill -2 -60 3 2 -56 3 minecraft:stone')
        elif trial.get('wall') == 'edge':
            self.run('fill 1 -60 3 1 -56 3 minecraft:stone')
        time.sleep(1.0)
        self.run(f'tick rate {RATE}')

    def one(self, trial):
        self.prepare(trial)
        started = datetime.datetime.now(datetime.timezone.utc).isoformat(); t0 = time.monotonic()
        hp = self.health(); before = self.profile()
        log_file = LOG_DIR / 'server.log'; mark = log_file.stat().st_size
        name = trial['id']; video = self.out / f'{name}.mp4'
        hold = trial.get('hold', .35)
        recorder = subprocess.Popen(ffmpeg_record_argv(self.s.state['display'], video, hold + 5),
                                    stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        try:
            self.g.require_ingame('sword qi right mouse')
            self.g.xdo('mousedown', '3')
            try:
                time.sleep(.3)
                if trial.get('cancel') in ('switch', 'same_weapon'): self.g.key('2')
                elif trial.get('cancel') == 'damage': self.run(f'damage {self.user} 1 minecraft:generic')
                elif trial.get('cancel') == 'dodge': self.g.key('Alt_L')
                elif trial.get('cancel') == 'menu': self.g.key('e')
                time.sleep(max(0, hold - .3))
            finally:
                self.g.xdo('mouseup', '3')
            time.sleep(2.5)
            if trial.get('cancel') == 'menu':
                self.g.xdo('key','Escape'); time.sleep(.6)
            after = self.health(); after_profile = self.profile()
            with log_file.open('rb') as stream:
                stream.seek(mark); lines = stream.read().decode('utf-8', 'replace').splitlines()
            lines = [line for line in lines if 'SWORD_QI ' in line]
            events = parse_events(lines)
            (self.out / f'{name}.log').write_text('\n'.join(lines)+'\n')
            rc = recorder.wait(timeout=15)
            if rc: raise RuntimeError(f'ffmpeg {name} exited {rc}: {recorder.stderr.read().decode()[-500:]}')
            power_before, power_after = parse_power(before), parse_power(after_profile)
            costs = [int(e['cost']) for e in events if e.get('result') == 'released' and 'cost' in e]
            cost_ok = (power_before is not None and power_after is not None
                       and (power_before - power_after == sum(costs) if costs else power_after >= power_before))
            row = dict(trial, start_utc=started, end_utc=datetime.datetime.now(datetime.timezone.utc).isoformat(),
                       wall_s=round(time.monotonic()-t0,3), health_before=hp, health_after=after,
                       profile_before=before, profile_after=after_profile, power_before=power_before, power_after=power_after,
                       expected_cost=sum(costs), cost_passed=cost_ok, events=events, video=video.name,
                       passed=expectation_met(trial, events, hp, after) and cost_ok)
            self.manifest['trials'].append(row)
            self.log(f'{name}: {"PASS" if row["passed"] else "FAIL"}, health {hp} -> {after}, {row["wall_s"]} s')
        finally:
            if recorder.poll() is None:
                recorder.terminate(); recorder.wait(timeout=10)
            self.run('tick rate 20')
            self.save()

    def save(self):
        self.manifest['passed'] = bool(self.manifest['trials']) and all(t['passed'] for t in self.manifest['trials'])
        (self.out / 'manifest.json').write_text(json.dumps(self.manifest, indent=2)+'\n')
        rows=''.join(f'<tr><td>{html.escape(t["id"])}</td><td>{t["passed"]}</td><td>{t["health_before"]} → {t["health_after"]}</td><td>{t["wall_s"]}</td><td><a href="{t["video"]}">video</a></td></tr>' for t in self.manifest['trials'])
        (self.out / 'index.html').write_text('<!doctype html><meta charset="utf-8"><title>Sword qi evidence</title>'
            '<style>body{font:16px system-ui;max-width:1100px;margin:32px auto}td,th{padding:8px;border:1px solid #ddd}table{border-collapse:collapse}</style>'
            '<h1>Sword qi — developer evidence</h1><p>Mapped right mouse; admin-prepared boundary fixtures. Natural acquisition, two clients, restart and owner verdict remain not_verified.</p>'
            '<table><tr><th>Trial</th><th>Pass</th><th>Target health</th><th>Seconds</th><th>Recording</th></tr>'+rows+'</table><p><a href="manifest.json">Raw manifest</a></p>')


def run_sword_qi(session, out=DEFAULT_OUT, log=print, trials=None):
    cap = SwordQiCapture(session, out, log)
    plan = trial_plan()
    if trials:
        unknown=set(trials)-{t['id'] for t in plan}
        if unknown: raise ValueError(f'unknown sword qi trials: {sorted(unknown)}')
        plan=[t for t in plan if t['id'] in trials]
    try:
        cap.setup()
        for trial in plan: cap.one(trial)
    finally:
        try:
            cap.g.xdo('mouseup','3'); cap.run('tick rate 20','tick unfreeze')
        finally: cap.save()
    return cap.manifest


def capture_hud(session, out=DEFAULT_OUT/'hud', log=print):
    """Resize the real window; capture both idle HUD and the server-driven charge bar."""
    out=Path(out); out.mkdir(parents=True,exist_ok=True)
    g=session.game; ids=g.xdo('search','--name','Minecraft').split()
    if not ids: raise RuntimeError('Minecraft window not found')
    window=ids[0]; language=session.state.get('language','en_us'); images=[]
    scene.ensure_cultivation(session)
    start=time.monotonic()
    try:
        for width,height in ((960,540),(854,480),(640,480)):
            session.run(f'myvillage cultivation qi fill {session.username}', warn=False)
            g.xdo('windowsize',window,str(width),str(height))
            g.xdo('windowmove',window,'0','0')
            time.sleep(1)
            g.recover_grab()
            for state in ('idle','charge'):
                try:
                    if state=='charge':
                        g.require_ingame('charge HUD capture'); g.xdo('mousedown','3'); time.sleep(.3)
                    name=f'{language}_{width//2}x{height//2}_{state}.png'
                    subprocess.run(['import','-silent','-window',window,str(out/name)],env=g.env,check=True,timeout=30)
                    images.append({'file':name,'language':language,'window_pixels':[width,height],
                                   'expected_gui_size':[width//2,height//2],'state':state})
                finally:
                    if state=='charge': g.xdo('mouseup','3'); time.sleep(1.5)
    finally:
        g.xdo('mouseup','3'); g.xdo('windowsize',window,'960','540'); time.sleep(.5); g.recover_grab()
    row={'kind':'sword_qi_hud','language':language,'wall_s':round(time.monotonic()-start,3),'images':images,
         'gui_scale':'automatic (expected 2 at these dimensions; inspect screenshots)',
         'owner_verdict':'not_verified'}
    (out/f'{language}.json').write_text(json.dumps(row,indent=2)+'\n')
    log(f'HUD {language}: {len(images)} real-client PNGs in {out}, {row["wall_s"]} s')
    return row


def zero_power_regression(session, out=DEFAULT_OUT/'zero_power', log=print):
    """Mapped ordinary attack and dodge while qi starts at zero; no skill payload."""
    out=Path(out);out.mkdir(parents=True,exist_ok=True);u=session.username;g=session.game
    start=time.monotonic(); commands=[]
    def run(*items):
        replies=session.run(*items,warn=False);commands.extend({'command':c,'reply':r} for c,r in zip(items,replies));return replies
    def qi():return parse_power(run(f'myvillage cultivation qi {u}')[0])
    def pos():return [float(x) for x in re.findall(r'(-?\d+(?:\.\d+)?)d',run(f'data get entity {u} Pos')[0])]
    run('tick rate 20','tick unfreeze',f'tp {u} .5 -60 .5 0 0',
        'kill @e[tag=sword_qi_target]',f'item replace entity {u} hotbar.0 with {WEAPON}',
        'summon minecraft:iron_golem .5 -60 3 {NoAI:1b,PersistenceRequired:1b,Tags:["sword_qi_target"]}')
    g.key('1');scene.ensure_cultivation(session);time.sleep(1)
    # A real charged-skill spend starts the existing recovery delay; the following fixture only sets current qi.
    run(f'myvillage cultivation qi fill {u}')
    g.xdo('mousedown','3');time.sleep(.15);g.xdo('mouseup','3');time.sleep(1.2)
    run('tick rate 5',f'myvillage cultivation qi set {u} 0')
    before=scene.parse_float_data(run(f'data get entity {TARGET} Health')[0]);p0=qi();g.require_ingame('zero-qi ordinary melee');g.click()
    time.sleep(3)
    after=scene.parse_float_data(run(f'data get entity {TARGET} Health')[0])
    p_after=qi()
    melee={'power_at_input':p0,'power_after_action':p_after,'health_before':before,'health_after':after,
           'passed':p0==0 and p_after==0 and before is not None and after is not None and after<before}
    run('tick rate 20');time.sleep(2)
    # A real charged-skill spend starts the existing recovery delay; the following fixture only sets current qi.
    run(f'myvillage cultivation qi fill {u}')
    g.xdo('mousedown','3');time.sleep(.15);g.xdo('mouseup','3');time.sleep(1.2)
    run('tick rate 5',f'myvillage cultivation qi set {u} 0')
    beforepos=pos();q0=qi();mark=(LOG_DIR/'server.log').stat().st_size;g.key('Alt_L');time.sleep(2)
    afterpos=pos()
    with (LOG_DIR/'server.log').open('rb') as stream:
        stream.seek(mark);lines=[l for l in stream.read().decode(errors='replace').splitlines() if 'DODGE_DEBUG' in l]
    dist=sum((b-a)**2 for a,b in zip(beforepos,afterpos))**.5 if len(beforepos)==len(afterpos)==3 else 0
    q_after=qi()
    dodge={'power_at_input':q0,'power_after_action':q_after,'position_before':beforepos,'position_after':afterpos,'distance':dist,'server_lines':lines,
           'passed':q0==0 and q_after==0 and dist>.2 and any('result=started' in l for l in lines)}
    run('tick rate 20');report={'melee':melee,'dodge':dodge,'passed':melee['passed'] and dodge['passed'],
                             'wall_s':round(time.monotonic()-start,3),'commands':commands}
    (out/'evidence.json').write_text(json.dumps(report,indent=2)+'\n');log('ZERO_POWER '+json.dumps(report))
    return report
