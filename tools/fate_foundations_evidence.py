#!/usr/bin/env python3
"""天命基础验收：Colab 原生客户端、H 绑定、真实按键／鼠标；RCON 仅 fixture／读回。"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import re
import shutil
import secrets
import sys
import threading
import time
import traceback
from types import SimpleNamespace

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

CASES = ('selection_binding', 'wood_root', 'lifesteal', 'foundations', 'lifecycle', 'two_players')
FIRST_PASS = CASES[:4]
ABILITY = 'myvillage:wood_root'
FATE = 'wood_spirit_body'


def select_cases(value=None):
    if value is None:
        return list(FIRST_PASS)
    words = value.split(',') if isinstance(value, str) else list(value)
    words = [w.strip() for w in words]
    words = ['selection_binding' if w == 'binding' else w for w in words]
    if words == ['all']:
        return list(CASES)
    if not words or any(not w or w not in CASES for w in words) or len(set(words)) != len(words):
        raise ValueError('用例必须非空、不重复且属于：' + ','.join(CASES))
    return [name for name in CASES if name in words]


def parse_status(reply):
    rows = []
    for line in reply.splitlines():
        if line.startswith('FOUNDATION '):
            row = json.loads(line[len('FOUNDATION '):])
            required = ('uuid', 'now', 'health', 'max_health', 'rooted', 'shield')
            if not isinstance(row, dict) or any(k not in row for k in required):
                raise ValueError('基础系统读回字段缺失')
            for key in ('now', 'health', 'max_health', 'shield'):
                if type(row[key]) not in (int, float) or not math.isfinite(row[key]) or row[key] < 0:
                    raise ValueError('基础系统数值非法：' + key)
            if type(row['rooted']) is not bool:
                raise ValueError('根缠读回必须为布尔值')
            if not isinstance(row['uuid'], str) or not row['uuid']:
                raise ValueError('实体身份缺失')
            if 'wood_root' in row:
                ability = row['wood_root']
                if not isinstance(ability, dict) or type(ability.get('eligible')) is not bool:
                    raise ValueError('主动能力资格读回非法')
                for key in ('cost', 'cooldown_remaining'):
                    if type(ability.get(key)) is not int or ability[key] < 0:
                        raise ValueError('主动能力整数读回非法：' + key)
                if type(row.get('qi')) is not int or row['qi'] < 0:
                    raise ValueError('灵力读回非法')
            rows.append(row)
    if not rows:
        raise ValueError('缺少 FOUNDATION 读回：' + reply)
    return rows


def close(a, b, tolerance=.015):
    return type(a) in (int, float) and math.isfinite(a) and abs(a - b) <= tolerance


def lifesteal_expected(actual_loss, fraction, maximum, cap_fraction, already=0, missing=math.inf):
    values = (actual_loss, fraction, maximum, cap_fraction, already)
    if any(type(v) not in (int, float) or not math.isfinite(v) or v < 0 for v in values):
        raise ValueError('吸血输入必须是非负有限数值')
    if missing < 0 or math.isnan(missing):
        raise ValueError('缺血量非法')
    return min(actual_loss * fraction, max(0, maximum * cap_fraction - already), missing)


def assert_colab(root=ROOT, environ=None):
    env = os.environ if environ is None else environ
    if env.get('MYVILLAGE_CLOUD_PROVIDER') != 'Colab' or not str(root.resolve()).startswith('/content/'):
        raise RuntimeError('实机采集仅允许主代理指定的 Colab /content/ 工作区')


def allow_rcon(command):
    """拒绝管理员路径冒充绑定／施法；execute 仅容许读回 bindings。"""
    text = command.strip().lstrip('/')
    if '\n' in text or '\r' in text:
        return False
    if text.startswith('execute as '):
        return bool(re.fullmatch(r'execute as [A-Za-z0-9_]+ run myvillage combat bindings', text))
    if text.startswith('execute '):
        return ((text.startswith(('execute if loaded ', 'execute if entity ')) and ' run ' not in text)
                or bool(re.fullmatch(r'execute if loaded -?\d+ -?\d+ -?\d+ run time query gametime', text)))
    if text.startswith('myvillage '):
        return bool(re.match(r'myvillage (?:foundation (?:status|grant|cleanse|clear)\b|cultivation (?:awaken|initiate|setrealm|setroot|qi|learn|forget)\b|cultivation fate (?:roll|reset|status)\b|world pause$)', text))
    return text.split(' ', 1)[0] in {'tick', 'time', 'data', 'attribute', 'effect', 'gamemode', 'gamerule',
        'forceload', 'fill', 'setblock', 'tp', 'spawnpoint', 'summon', 'kill', 'item', 'team',
        'op', 'deop', 'damage', 'save-all', 'list', 'weather', 'clear', 'experience', 'difficulty'} and not text.startswith(('data modify ', 'data remove '))


def initial_report(selected):
    return {'kind': 'fate_foundations', 'selected': list(selected), 'completed': [], 'unrun': list(CASES),
            'cases': {}, 'failures': [], 'checks': [], 'commands': [], 'inputs': [], 'screenshots': [],
            'phases': [], 'passed': False, 'runtime_retained': True, 'provider': 'Colab',
            'method': __doc__, 'owner_appearance': 'not_verified', 'owner_feel': 'not_verified',
            'owner_audio': 'not_verified', 'owner_fps': 'not_verified', 'owner_balance': 'not_verified',
            'broad_multiplayer': 'not_verified'}


# 复用既有客户端、RCON、窗口定位与会话所有权；导入不会启动游戏。
from tools.elemental_arts_evidence import ElementalEvidence
from tools.locked_sword_evidence import LockedEvidence
from tools.innate_fates_evidence import FateEvidence
from tools.fates_purple_evidence import PurpleEvidence
from tools.combat_capture import session as cs, scene, procs
from tools.combat_capture.rcon import Rcon, RconError
from tools.combat_capture.xgame import GameInputError
from tools.alchemy_quality_evidence import SecondClient, read_data
from tools.ink_hub_evidence import bounds, tile_center


class EarlySessionFreeze:
    """只监控本次新 session；RCON 就绪即冻结，不等待客户端冷启动。"""
    def __init__(self, session_id, timeout, poll=.1):
        self.session_id, self.timeout, self.poll = session_id, timeout, poll
        self.cancel = threading.Event()
        self.done = threading.Event()
        self.result = self.error = None
        self.owner_state = None  # 仅内部持有，用于失败时核对日志／清理句柄；不写报告。
        self.diagnostics = []
        self.thread = threading.Thread(target=self._run, name='foundation-early-freeze', daemon=True)

    def owned_state(self):
        state = cs.read_state()
        if not state or state.get('session_id') != self.session_id:
            return None
        if state.get('phase') in ('failed', 'stopped', 'stopping'):
            raise RuntimeError('早期冻结目标会话已终止')
        return state

    def require_owner(self, state, *, allow_cancel=False):
        current = self.owned_state()
        keys = ('supervisor', 'rcon_port', 'rcon_password')
        # procs 会随客户端启动增加；只固定服务器身份。
        if (state.get('session_id') != self.session_id or not current
                or any(current.get(k) != state.get(k) for k in keys)
                or current.get('procs', {}).get('server') != state.get('procs', {}).get('server')):
            raise RuntimeError('早期冻结会话身份已变化')
        if self.cancel.is_set() and not allow_cancel:
            raise RuntimeError('早期冻结已取消')
        return current

    def response_diagnostic(self, command, reply, state, **extra):
        # 仅记录固定命令名、脱敏响应的长度／哈希／分类，绝不保留原文。
        safe = reply.replace(state['rcon_password'], '[redacted]')
        return {'command': command, 'length': len(safe), 'sha256': hashlib.sha256(safe.encode()).hexdigest(), **extra}

    def read_frozen_time(self, state, deadline):
        values = []
        for attempt in range(1, 4):
            self.require_owner(state)
            if time.monotonic() >= deadline:
                raise TimeoutError('读取冻结时钟超过 worker 期限')
            rcon = Rcon('127.0.0.1', state['rcon_port'], state['rcon_password'], timeout=1)
            try:
                rcon.connect()
                self.require_owner(state)
                reply = rcon.cmd('time query gametime')
                self.require_owner(state)
                match = re.fullmatch(r'The time is ([0-9]+)', reply.strip())
                self.diagnostics.append(self.response_diagnostic('time query gametime', reply, state,
                    attempt=attempt, classification='valid' if match else 'invalid_format'))
                if match:
                    value = int(match[1])
                    if values and value != values[0]:
                        self.diagnostics[-1]['classification'] = 'clock_changed'
                        raise RuntimeError('冻结时钟在独立查询之间发生变化')
                    values.append(value)
                    if len(values) == 2:
                        return value
            except (OSError, RconError) as error:
                self.diagnostics.append({'command': 'time query gametime', 'attempt': attempt,
                                         'classification': 'transport_error', 'error_type': type(error).__name__})
            finally:
                rcon.close()
            if attempt < 3 and self.cancel.wait(self.poll):
                raise RuntimeError('独立时钟查询已取消')
        raise RuntimeError('三次独立查询未取得两次一致的合法冻结时钟')

    def _run(self):
        deadline = time.monotonic() + self.timeout
        seen = False
        stage = '等待指定会话 RCON'
        try:
            while not self.cancel.is_set():
                if time.monotonic() >= deadline:
                    raise TimeoutError('等待指定会话 RCON 早期冻结超时')
                state = self.owned_state()
                if seen and state is None:
                    raise RuntimeError('早期冻结目标会话被替换')
                seen = seen or state is not None
                if (state and state.get('phase') in ('starting_server', 'starting_client', 'ready')
                        and state.get('rcon_port') and state.get('rcon_password')
                        and state.get('procs', {}).get('server')):
                    if self.owner_state is None:
                        self.owner_state = state
                    self.require_owner(self.owner_state)
                    rcon = Rcon('127.0.0.1', state['rcon_port'], state['rcon_password'], timeout=1)
                    try:
                        stage = '连接前核对会话身份'
                        self.require_owner(state)
                        try:
                            rcon.connect()
                        except (OSError, RconError):
                            # 服务器尚未监听；连接失败也必须关闭可能已创建的 socket。
                            if self.cancel.wait(self.poll):
                                return
                            stage = '等待指定会话 RCON'
                            continue
                        stage = '冻结前核对会话身份'
                        self.require_owner(state)
                        stage = 'tick freeze 命令及成功回执'
                        reply = rcon.cmd('tick freeze')
                        self.diagnostics.append(self.response_diagnostic('tick freeze', reply, state,
                            classification='valid' if reply.strip() == 'The game is frozen' else 'invalid_format'))
                        if reply.strip() != 'The game is frozen':
                            raise RuntimeError('早期 tick freeze 未收到成功回执')
                    finally:
                        rcon.close()
                    stage = '独立连接读取并复核冻结 gametime'
                    value = self.read_frozen_time(state, deadline)
                    self.result = {'session_id': self.session_id, 'frozen_gametime': value}
                    return
                if self.cancel.wait(self.poll):
                    return
        except Exception as error:
            # 不保留异常原文、state 或连接对象，避免密码进入 JSON/traceback。
            self.error = ('早期冻结失败（' + stage + '）：' + type(error).__name__
                          + '；安全诊断=' + json.dumps(self.diagnostics, ensure_ascii=False))
        finally:
            self.done.set()

    def start_session(self, config):
        if config.session_id != self.session_id or not config.reuse_world:
            raise ValueError('早期冻结仅用于指定的新 reuse session')
        self.thread.start()
        try:
            state = cs.start(config)
            if not self.done.wait(10):
                raise TimeoutError('客户端已就绪但早期冻结 worker 未完成')
            if self.error or self.result is None:
                raise RuntimeError(self.error or '早期冻结未取得时钟证据')
            self.require_owner(state)
            return state, self.result
        finally:
            self.cancel.set()
            self.thread.join(timeout=5)
            if self.thread.is_alive():
                raise RuntimeError('早期冻结 worker 未在清理期限内退出')


class FoundationEvidence(ElementalEvidence):
    def __init__(self, out, cases=None, attach=False):
        selected = select_cases(cases)
        super().__init__(out, selected, attach)
        self.root_out = out
        self.report.update(initial_report(selected))
        self.report['source_commit'] = json.loads((ROOT / 'source-manifest.json').read_text())['commit'] if (ROOT / 'source-manifest.json').exists() else 'unknown'
        self.report['jar_sha256'] = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in (ROOT / 'build/libs').glob('*.jar')}
        self.definitions = {'myvillage:' + p.stem: json.loads(p.read_text()) for p in (ROOT / 'src/main/resources/data/myvillage/myvillage/innate_fate').glob('*.json')}
        self.ability_config = json.loads((ROOT / 'src/main/resources/data/myvillage/combat/fate_abilities.json').read_text())
        self.field_config = json.loads((ROOT / 'src/main/resources/data/myvillage/combat/foundation/fields.json').read_text())
        self.healing_config = json.loads((ROOT / 'src/main/resources/data/myvillage/combat/foundation/healing.json').read_text())
        self.report['configuration'] = {'abilities': self.ability_config, 'fields': self.field_config, 'healing': self.healing_config}
        self.case = None
        self.pvp_enabled = True

    def save(self):
        root = getattr(self, 'root_out', self.out)
        root.mkdir(parents=True, exist_ok=True)
        (root / 'evidence.json').write_text(json.dumps(self.report, ensure_ascii=False, indent=2) + '\n')

    def rc(self, command):
        if not allow_rcon(command):
            raise ValueError('RCON 超出 fixture／读回边界：' + command)
        return super().rc(command)

    def log(self, offset=0):
        return LockedEvidence.log(self, offset)

    def start(self, reuse=False):
        assert_colab()
        self._session_unusable = True
        clock = None
        clock_diagnostics = None
        if self.attach:
            if reuse:
                raise ValueError('重启早期冻结必须创建新隔离会话，不能 attach')
            state = cs.read_state()
        else:
            config = cs.SessionConfig(session_id=secrets.token_hex(6), language='zh_cn', pvp=self.pvp_enabled,
                                      reuse_world=reuse, lock=str(procs.default_lock_path(ROOT)))
            if reuse:
                fixture = EarlySessionFreeze(config.session_id, config.lock_timeout + config.server_timeout + 60)
                try:
                    state, clock = fixture.start_session(config)
                except Exception:
                    self.s = None
                    phase = {'phase': '重启早期冻结失败', 'session_id': config.session_id,
                             'early_freeze_diagnostics': fixture.diagnostics, 'recovery': 'unavailable'}
                    try:
                        if fixture.owner_state is not None:
                            owned = fixture.require_owner(fixture.owner_state, allow_cancel=True)
                            if owned.get('phase') == 'ready' and cs.supervisor_alive(owned):
                                self.s = cs.Session(owned, ui_check=False); self.sessions += 1
                                phase['recovery'] = 'logs_and_cleanup_only'
                    except Exception as error:
                        phase['recovery_error_type'] = type(error).__name__
                    self.report['phases'].append(phase)
                    raise
                clock_diagnostics = fixture.diagnostics
            else:
                state = cs.start(config)
        if not state or state.get('phase') != 'ready':
            raise RuntimeError('没有就绪的隔离会话')
        if bool(state.get('pvp')) != self.pvp_enabled:
            raise RuntimeError('已有会话 PvP 配置与所选场景不符')
        if not cs.supervisor_alive(state):
            raise RuntimeError('隔离会话 supervisor 已退出')
        # 绑定已经核对的快照，避免再次 attach 读到其他线程替换的会话。
        self.s = cs.Session(state, ui_check=True); self.sessions += 1
        self.freeze() if reuse else self.rc('tick unfreeze')
        self.position(self.s.game)
        self.report['phases'].append({'phase': '重启' if reuse else '启动', 'pvp': self.pvp_enabled,
                                      'timings': state.get('timings', {}), 'early_freeze': clock,
                                      'early_freeze_diagnostics': clock_diagnostics})
        if reuse:
            self.check('重启早期冻结持续至客户端就绪', self.now() == clock['frozen_gametime'], clock)
            self._session_unusable = False
            return
        for command in (*scene.world_rules(), *scene.player_setup(self.s.username, 'minecraft:air'),
                        'gamerule naturalRegeneration false', 'myvillage world pause',
                        'forceload add -32 -32 32 48'):
            self.rc(command)
        self.poll(lambda: self.rc('execute if loaded 0 -61 0 run time query gametime'),
                  lambda r: 'The time is' in r, label='隔离场景区块就绪')
        self.rc('fill -16 -61 -16 16 -61 40 minecraft:stone')
        self.configure(self.s.username)
        self.rc(f'item replace entity {self.s.username} hotbar.0 with minecraft:air')
        scene.ensure_cultivation(self.s); self.freeze()
        self._session_unusable = False

    def capture(self, name, game=None):
        super().capture(name, game)
        self.report['screenshots'][-1]['file'] = str((self.out / self.report['screenshots'][-1]['file']).relative_to(self.root_out))
        self.save()

    def status(self, target=None):
        rows = parse_status(self.rc('myvillage foundation status ' + (target or self.s.username)))
        if len(rows) != 1:
            raise ValueError('单目标读回返回多实体')
        return rows[0]

    def state(self):
        return FateEvidence.state(self)

    decoded_state = staticmethod(FateEvidence.decoded_state)

    def reset(self):
        return FateEvidence.reset(self)

    def home(self):
        self.rc(f'tp {self.s.username} .5 -60 .5 0 0')
        self.poll(lambda: self.rc('execute if loaded 0 -60 2'), lambda reply: 'passed' in reply,
                  timeout=25, label='返回战斗场景后 fixture 区块加载')
        # 测灵碑与底座占用 z=2 的命中通道；只清理这两块 fixture。
        self.rc('setblock 0 -59 2 minecraft:air')
        self.rc('setblock 0 -60 2 minecraft:air')
        self.s.game.require_ingame('天命基础验收')

    def stele_fixture(self):
        return FateEvidence.stele_fixture(self)

    def open_stele(self):
        return FateEvidence.open_stele(self)

    def leave_stele(self):
        return FateEvidence.leave_stele(self)

    def press(self, control):
        x, y, w, h = FateEvidence.geometry(self)[control]
        self.s.game.xdo('mousemove', str(round(2*(x+w/2))), str(round(2*(y+h/2))), 'click', '1')
        self.report['inputs'].append({'method': '映射觉醒界面点击', 'control': control})
        time.sleep(.4)

    def choose_wood(self):
        self.rc('tick unfreeze')
        PurpleEvidence.choose_focus(self, FATE)
        self.freeze()
        self.check('木体具有主动能力资格', self.status()['wood_root']['eligible'], self.status())

    def skills_page(self):
        self.rc('tick unfreeze')
        self.key('h'); self.opened = True; time.sleep(.6)
        b = bounds(self.s.game.size)
        self.click(b['left']+26, b['top']+36+3*21+9, '技能页')
        return b

    def close_skills(self):
        self.s.game.xdo('key', 'Escape'); time.sleep(.3); self.opened = False
        self.s.game.require_ingame('关闭技能页'); self.freeze()

    def bind_wood(self):
        # 每例清空已学功法 fixture，木体成为唯一可绑定条目，不猜测其他模块排序。
        for ident in self.defs:
            self.rc(f'myvillage cultivation forget {self.s.username} myvillage:{ident}')
        self.step(5)
        b = self.skills_page()
        self.click(*tile_center(b, 0, 18), '选择木体缠根')
        columns = 6 if b['width'] >= 350 else 3
        rows = 6 // columns
        dock = b['y']+b['height']-(15+rows*24+22)
        self.click(b['x']+.5*b['width']/columns, dock+25, '选择 Z 槽')
        self.click(b['x']+max(66, b['width']//4)//2, dock+15+rows*24+9, '分配木体缠根')
        self.poll(self.bindings, lambda s: re.findall(r'myvillage:[a-z0-9_]+', s)[:1] == [ABILITY], label='H 真实点击绑定 wood_root')
        self.capture('H绑定木体缠根'); self.close_skills(); self.assigned = {'wood_root': 'z'}

    def prepare_case(self, name):
        self.s.game.xdo('keyup', 'w', 'keyup', 'a', 'keyup', 's', 'keyup', 'd', 'keyup', 'z', 'mouseup', '1', 'mouseup', '3')
        if self.opened:
            self.s.game.xdo('key', 'Escape'); self.opened = False
        self.freeze()
        self.rc('tick rate 20')
        self.rc('kill @e[type=!minecraft:player]')
        self.rc('fill -12 -60 -12 12 -48 16 minecraft:air')
        self.rc('fill -12 -61 -12 12 -61 16 minecraft:stone')
        self.rc('team remove foundation_allies')
        self.rc(f'myvillage foundation clear {self.s.username}')
        self.configure(self.s.username)
        self.home()
        self.rc(f'item replace entity {self.s.username} hotbar.0 with minecraft:air')
        scene.ensure_cultivation(self.s)
        if name != 'selection_binding':
            self.choose_wood(); self.bind_wood()
            wait = self.status()['wood_root']['cooldown_remaining']
            self.step(wait+2)
            self.rc(f'myvillage cultivation qi fill {self.s.username}')
        self.report['cases'][name]['fixture'] = '独立平坦世界；管理员准备角色／目标，玩家行为仅映射输入'

    def cast_wood(self, accepted=True):
        before = self.status(); mark = self.offset()
        self.key('z'); time.sleep(.3)
        after = self.status()
        if accepted:
            self.poll(self.status, lambda s: s['wood_root']['cooldown_remaining'] > 0, label='真实 Z 键施放')
            after = self.status()
            self.check('单次扣费', before['qi']-after['qi'] == before['wood_root']['cost'], {'before': before, 'after': after})
            self.check('产生冷却', after['wood_root']['cooldown_remaining'] > 0, after)
        else:
            self.check('拒绝不扣费不刷新冷却', before['qi'] == after['qi'] and before['wood_root']['cooldown_remaining'] == after['wood_root']['cooldown_remaining'], {'before': before, 'after': after})
        return before, after, mark

    def selection_binding(self):
        self.choose_wood(); self.bind_wood()
        self.capture('选中木体可见')
        self.reset()
        self.check('无词条无资格', not self.status()['wood_root']['eligible'], self.status())
        b = self.skills_page()
        # 点击旧卡片与分配区，确认残留选择也无法绑定／施放。
        self.click(*tile_center(b, 0, 18), '无资格旧卡片位置')
        columns = 6 if b['width'] >= 350 else 3; rows = 6//columns
        dock = b['y']+b['height']-(15+rows*24+22)
        self.click(b['x']+max(66, b['width']//4)//2, dock+15+rows*24+9, '无资格分配尝试')
        self.capture('无木体资格技能页'); self.close_skills()
        self.cast_wood(False)
        self.check('无资格不产生领域', not self.status()['fields'], self.status())

    def wood_root(self):
        enemy = self.dummy('.5 -60 2.5', tag='foundation_enemy', knockback=0)
        ally = self.dummy('2.5 -60 .5', tag='foundation_ally', knockback=0)
        wall = self.dummy('.5 -60 -2.5', tag='foundation_wall', knockback=0)
        self.rc('team add foundation_allies'); self.rc('team modify foundation_allies friendlyFire false')
        self.rc(f'team join foundation_allies {self.s.username}'); self.rc('team join foundation_allies @e[tag=foundation_ally]')
        self.rc('fill -2 -60 -1 2 -55 -1 minecraft:stone')
        speed = self.number(f'attribute {enemy} minecraft:generic.movement_speed get')
        self.cast_wood(); self.step(2)
        self.check('敌方根缠', self.status(enemy)['rooted'], self.status(enemy))
        self.check('友军不受控', not self.status(ally)['rooted'], self.status(ally))
        self.check('墙后不受控', not self.status(wall)['rooted'], self.status(wall))
        # 移动冲量只是 fixture；受控位置是否保持由真实服务器 tick 决定。
        before = read_data(self.rc(f'data get entity {enemy} Pos'))
        self.rc(f'data merge entity {enemy} {{Motion:[0.3d,0d,0d]}}'); self.step(3)
        after = read_data(self.rc(f'data get entity {enemy} Pos'))
        self.check('根缠期间水平不移动', abs(after[0]-before[0]) < .03 and abs(after[2]-before[2]) < .03, {'before': before, 'after': after})
        self.capture('真实按键敌方定身'); self.cast_wood(False)
        duration = self.field_config['wood_root']['root_ticks']
        self.step(duration+3)
        self.check('根缠到期恢复', not self.status(enemy)['rooted'] and close(self.number(f'attribute {enemy} minecraft:generic.movement_speed get'), speed), self.status(enemy))
        self.step(self.status()['wood_root']['cooldown_remaining']+2)
        self.rc(f'myvillage cultivation qi set {self.s.username} 0'); self.cast_wood(False)

    def wound(self, amount=40):
        self.report['inputs'].append({'method': '管理员缺血 fixture', 'amount': amount})
        self.rc(f'damage {self.s.username} {amount} minecraft:generic')
        self.step(21)

    def attack(self, target):
        before = self.hp(target)
        self.s.game.require_ingame('真实鼠标近战')
        self.s.game.xdo('click', '1')
        self.report['inputs'].append({'method': '映射鼠标左键攻击', 'target': target, 'server_tick': self.now()})
        self.step(1)
        self.poll(lambda: self.hp(target), lambda n: n < before, label='真实鼠标造成扣血')
        return before-self.hp(target)

    def lifesteal(self):
        u = self.s.username
        self.wound()
        self.rc(f'item replace entity {u} hotbar.0 with minecraft:iron_sword')
        target = self.dummy('.5 -60 2.5', tag='foundation_lifesteal')
        # 原版模式避免功法派生与攻击前摇干扰基础收据；R 键真实切换。
        if scene.parse_combat_mode(self.rc(f'data get entity {u} "neoforge:attachments"')) == 'cultivation':
            self.key('r'); time.sleep(.3)
        self.step(25)
        effects = self.definitions['myvillage:'+FATE]['effects']
        fraction = effects['lifesteal_fraction']; cap_fraction = effects['lifesteal_cap_fraction']
        before = self.hp(u); damage = self.attack(target); gained = self.hp(u)-before
        expected = lifesteal_expected(damage, fraction, self.status()['max_health'], cap_fraction, missing=self.status()['max_health']-before)
        self.check('按实际主伤害吸血', close(gained, expected), {'damage': damage, 'gained': gained, 'expected': expected})
        self.capture('真实攻击吸血')
        self.lifesteal_cap_probe(target, fraction, cap_fraction)
        self.derived_no_lifesteal()

    def lifesteal_cap_probe(self, target, fraction, cap_fraction):
        # 提高玩家攻击属性作为 fixture，一击压到每秒上限，并在同一窗口再真实攻击。
        u = self.s.username
        maximum = self.status()['max_health']
        if maximum <= 0 or fraction <= 0 or cap_fraction <= 0:
            raise ValueError('吸血上限夹具必须具有正最大生命、吸血比例和上限比例')
        cap = maximum * cap_fraction
        threshold = cap / fraction
        self.step(25); self.wound(15)
        old_attack = self.number(f'attribute {u} minecraft:generic.attack_damage base get')
        try:
            # 境界、手持武器等修饰可将基础攻击放大；先标定实际值 = offset + scale * base。
            self.rc(f'attribute {u} minecraft:generic.attack_damage base set 0')
            offset = self.number(f'attribute {u} minecraft:generic.attack_damage get')
            self.rc(f'attribute {u} minecraft:generic.attack_damage base set 1')
            scale = self.number(f'attribute {u} minecraft:generic.attack_damage get') - offset
            self.check('吸血攻击线性校准可解', math.isfinite(offset) and math.isfinite(scale) and scale > 0,
                       {'offset': offset, 'scale': scale})
            attack_base = max(0, math.ceil((threshold * 1.5 - offset) / scale))
            self.rc(f'attribute {u} minecraft:generic.attack_damage base set {attack_base}')
            attack_damage = self.number(f'attribute {u} minecraft:generic.attack_damage get')
            self.check('吸血实际攻击达到阈值且符合校准', attack_damage >= threshold
                       and close(attack_damage, offset + scale * attack_base),
                       {'threshold': threshold, 'actual': attack_damage, 'base': attack_base,
                        'offset': offset, 'scale': scale})
            dummy_hp = math.ceil(attack_damage * 2 + 1)
            self.report['inputs'].append({'method': '管理员吸血上限线性攻击校准 fixture',
                'offset': offset, 'scale': scale, 'base': attack_base, 'actual_attack': attack_damage,
                'threshold': threshold, 'required_dummy_hp': dummy_hp})
            def durable_dummy(entity):
                preparable = self.number(f'attribute {entity} minecraft:generic.max_health get')
                self.check('吸血目标可准备生命覆盖两次命中', dummy_hp <= preparable,
                           {'target': entity, 'required_hp': dummy_hp, 'preparable_hp': preparable})
                self.rc(f'attribute {entity} minecraft:generic.max_health base set {dummy_hp}')
                self.rc(f'data merge entity {entity} {{Health:{dummy_hp}f,HurtTime:0s}}')
                actual = self.hp(entity)
                self.check('吸血上限目标承受两次命中', actual >= dummy_hp,
                           {'target': entity, 'required_hp': dummy_hp, 'actual_hp': actual,
                            'attack_base': attack_base, 'attack_damage': attack_damage})
            durable_dummy(target)
            self.step(21)
            # 对齐计费窗口，避免墙钟或截图耗时跨秒。
            window = self.healing_config['lifesteal_window_ticks']
            self.step(window-self.now()%window)
            before = self.hp(u)
            self.check('吸血上限夹具缺血足够', maximum-before >= cap,
                       {'health': before, 'maximum': maximum, 'cap': cap})
            first_started = self.now()
            damage = self.attack(target); first = self.hp(u)-before
            first_hit = self.now()
            self.rc(f'tp {target} 8 -60 8')
            second = self.dummy('.5 -60 2.5', tag='foundation_lifesteal_second')
            durable_dummy(second)
            # 新实体 spawn/pick 同步与原版 missTime 恢复都需要时间；不在同一客户端帧急点。
            self.step(10)
            time.sleep(.6)
            second_started = self.now()
            self.check('第二击准备仍在首击计费窗口',
                       first_started//window == first_hit//window == second_started//window
                       and (second_started+1)//window == first_started//window,
                       {'window_ticks': window, 'first_started': first_started, 'first_hit': first_hit,
                        'second_started': second_started})
            second_damage = self.attack(second); total = self.hp(u)-before
            second_hit = self.now()
            self.check('两次实际命中位于同一吸血窗口',
                       damage > 0 and second_damage > 0 and first_hit//window == second_hit//window,
                       {'window_ticks': window, 'first_hit': first_hit, 'second_hit': second_hit,
                        'first_damage': damage, 'second_damage': second_damage})
            self.check('每秒最大生命吸血上限', close(first, cap) and close(total, first),
                       {'first': first, 'total': total, 'cap': cap, 'damage': damage,
                        'attack_base': attack_base, 'dummy_hp': dummy_hp,
                        'first_hit': first_hit, 'second_hit': second_hit})
            self.step(window+1)
            self.check('新窗口目标仍可承受满强度命中', self.hp(second) > attack_damage,
                       {'health': self.hp(second), 'attack_damage': attack_damage})
            before = self.hp(u); self.attack(second)
            self.check('新窗口恢复额度', self.hp(u) > before, {'before': before, 'after': self.hp(u)})
        finally:
            self.rc(f'attribute {u} minecraft:generic.attack_damage base set {old_attack}')

    def derived_no_lifesteal(self):
        # 元素灼烧是现有可信派生伤害入口；以真实火系技能按键制造 DOT。
        ids = ['linghuo_shu']
        scene.ensure_cultivation(self.s)
        self.fixture_loadout(ids)
        self.rc(f'item replace entity {self.s.username} hotbar.0 with minecraft:air')
        self.ready(220)
        target = self.dummy('.5 -60 5', tag='locked_fixture'); self.wound(15)
        self.cast(ids[0]); before = self.hp(self.s.username); victim = self.hp(target)
        self.step(25)
        self.check('派生持续伤害不重复吸血', self.hp(target) < victim and close(self.hp(self.s.username), before), {'target_before': victim, 'target_after': self.hp(target), 'player_before': before, 'player_after': self.hp(self.s.username)})

    def hostile_hit(self):
        u = self.s.username
        player_pos = read_data(self.rc(f'data get entity {u} Pos'))
        x, y, z = player_pos
        target = self.dummy(f'{x:g} {y:g} {z+1.3:g}', tag='foundation_attacker', knockback=1)
        try:
            self.rc(f'attribute {target} minecraft:generic.attack_damage base set 6')
            self.rc(f'attribute {target} minecraft:generic.follow_range base set 16')
            self.rc(f'attribute {target} minecraft:generic.movement_speed base set 0.23')
            self.rc(f'data merge entity {target} {{NoAI:0b}}')
            fixture = {'method': '管理员按玩家实际位置准备近身敌人；受击由敌人AI触发',
                       'player': u, 'target': target, 'player_pos': player_pos,
                       'attacker_pos': read_data(self.rc(f'data get entity {target} Pos'))}
            self.report['inputs'].append(fixture)
            before = self.status()
            for _ in range(6):
                self.step(5); after = self.status()
                if after['health'] < before['health'] or after['shield'] < before['shield']:
                    fixture['player_pos_after'] = read_data(self.rc(f'data get entity {u} Pos'))
                    fixture['attacker_pos_after'] = read_data(self.rc(f'data get entity {target} Pos'))
                    return before, after
            raise AssertionError('真实敌人攻击未命中；不能用伤害命令替代')
        finally:
            self.rc('kill @e[tag=foundation_attacker]')

    def poise_charging(self):
        u = self.s.username
        self.home()
        self.rc('kill @e[tag=foundation_control]')
        self.rc('kill @e[tag=foundation_attacker]')
        self.rc(f'myvillage cultivation learn {u} myvillage:jianqi_jue')
        self.rc(f'item replace entity {u} hotbar.0 with myvillage:qingfeng_sword')
        self.rc(f'myvillage cultivation qi fill {u}')
        self.rc(f'myvillage foundation grant poise {u} myvillage:fixture_charge 200')
        scene.ensure_cultivation(self.s)
        self.step(25)
        mark = self.offset()
        self.s.game.require_ingame('霸体受击期间真实剑气蓄力')
        self.s.game.xdo('mousedown', '3')
        self.report['inputs'].append({'method': '映射右键按住蓄力', 'server_tick': self.now()})
        try:
            self.step(3)
            before, after = self.hostile_hit()
            self.check('霸体蓄力期间真实受击', after['health'] < before['health'], {'before': before, 'after': after})
            self.capture('霸体蓄力真实受击')
            self.step(5)
        finally:
            self.s.game.xdo('mouseup', '3')
            self.report['inputs'].append({'method': '映射右键释放', 'server_tick': self.now()})
        self.poll(lambda: self.log(mark), lambda text: 'result=released skill=myvillage:jianqi_jue' in text,
                  label='霸体受击后剑气真实释放')
        log = self.log(mark)
        self.check('霸体受击不取消蓄力', 'result=released skill=myvillage:jianqi_jue' in log, log)
        self.step(30)

    def foundations(self):
        u = self.s.username
        self.rc(f'myvillage foundation grant shield {u} myvillage:fixture_a 8 100')
        self.rc(f'myvillage foundation grant shield {u} myvillage:fixture_b 6 100')
        self.check('多来源护盾叠加', close(self.status()['shield'], 14), self.status())
        self.rc(f'myvillage foundation grant shield {u} myvillage:fixture_a 4 100')
        self.check('同源覆盖不叠加', close(self.status()['shield'], 10), self.status())
        before, after = self.hostile_hit()
        self.check('真实受击消费护盾', after['shield'] < before['shield'] and close(after['health'], before['health']), {'before': before, 'after': after})
        self.capture('多来源护盾真实受击'); self.step(101)
        self.check('护盾到期', self.status()['shield'] == 0, self.status())
        target = self.dummy('.5 -60 2.5', tag='foundation_control')
        self.rc(f'myvillage foundation grant root {u} {target} 40')
        self.check('控制fixture生效', self.status(target)['rooted'], self.status(target))
        self.rc(f'myvillage foundation cleanse {target}')
        self.check('净化解除根缠', not self.status(target)['rooted'], self.status(target))
        self.rc(f'myvillage foundation grant poise {target} myvillage:fixture_poise 80')
        self.rc(f'effect give {target} minecraft:glowing 20 0 true')
        self.rc(f'myvillage foundation grant root {u} {target} 40')
        self.check('霸体拒绝根缠', self.status(target)['poised'] and not self.status(target)['rooted'], self.status(target))
        effects_before = self.rc(f'data get entity {target} active_effects')
        self.rc(f'myvillage foundation cleanse {target}')
        effects_after = self.rc(f'data get entity {target} active_effects')
        self.check('净化保留霸体与无关药水', self.status(target)['poised'] and 'minecraft:glowing' in effects_before and 'minecraft:glowing' in effects_after, {'status': self.status(target), 'effects': effects_after})
        self.wound(30)
        # fixture 治疗修饰，实际治疗由原版瞬间治疗药水事件触发，不能调用 heal 指令。
        self.rc(f'myvillage foundation grant healing_modifier {u} myvillage:fixture_heal -0.5 100')
        before = self.hp(u); self.rc(f'effect give {u} minecraft:instant_health 1 0 true'); self.step(1)
        reduced = self.hp(u)-before
        self.rc(f'myvillage foundation clear {u}'); before = self.hp(u)
        self.rc(f'effect give {u} minecraft:instant_health 1 0 true'); self.step(1)
        normal = self.hp(u)-before
        self.check('治疗统一修正', normal > 0 and close(reduced, normal*.5), {'reduced': reduced, 'normal': normal})
        self.rc(f'myvillage foundation grant healing_field {u} 3 2 40 5')
        before = self.hp(u); self.step(25)
        self.check('治疗领域脉冲实际回血', self.hp(u) > before, {'before': before, 'after': self.hp(u)})
        self.capture('治疗领域脉冲')
        self.rc(f'myvillage foundation clear {u}')
        self.poise_charging()

    def lifecycle(self):
        self.logout_reconnect()
        self.cast_wood()
        u = self.s.username
        self.wound(20)
        self.rc(f'myvillage foundation grant shield {u} myvillage:fixture_lifecycle 8 400')
        self.rc(f'myvillage foundation grant healing_modifier {u} myvillage:fixture_lifecycle -0.5 400')
        before = self.status(); bindings = self.bindings()
        self.rc('save-all flush'); self.stop(); self.attach = False; self.start(reuse=True)
        self.freeze(); after = self.status()
        self.check_cooldown_persisted('重启冷却未重置', before, after)
        self.check('重启暂态护盾控制领域清理', after['shield'] == 0 and not after['rooted'] and not after['fields'], after)
        self.check('重启绑定保持', ABILITY in self.bindings() and ABILITY in bindings, self.bindings())
        self.cast_wood(False)
        self.assert_healing_restored('重启')
        self.report['cases']['lifecycle']['observed'] = ['同服退出重连', '服务器重启', '冷却保持', '护盾控制领域清理', '治疗修饰清理', '绑定保持']

    def check_cooldown_persisted(self, label, before, after):
        elapsed = after['now'] - before['now']
        expected = before['wood_root']['cooldown_remaining'] - elapsed
        self.check(label, elapsed >= 0 and expected > 0 and after['wood_root']['cooldown_remaining'] == expected,
                   {'before': before, 'after': after, 'elapsed_ticks': elapsed, 'expected_remaining': expected})

    def assert_healing_restored(self, label):
        self.wound(15)
        before = self.hp(self.s.username)
        self.rc(f'effect give {self.s.username} minecraft:instant_health 1 0 true'); self.step(1)
        self.check(label+'治疗修饰清理', close(self.hp(self.s.username)-before, 4), {'before': before, 'after': self.hp(self.s.username)})

    def logout_reconnect(self):
        parent = self.out; original = self.s
        # 两客户端不能重叠在测灵碑射线上；主玩家一直避让到 guest 验证结束。
        self.rc(f'tp {original.username} 10 -60 0')
        try:
            self._logout_reconnect_parked(parent, original)
        finally:
            self.s = original; self.guest = None; self.out = parent
            self.home(); self.freeze()

    def _logout_reconnect_parked(self, parent, original):
        self.out = parent/'退出前'; self.out.mkdir()
        self.rc('tick unfreeze')
        with SecondClient(self) as guest:
            self.guest = guest; self.position(guest.game); self.configure(guest.username)
            self.s = SimpleNamespace(username=guest.username, game=guest.game, run=original.run, state=original.state, log=original.log)
            try:
                self.freeze(); scene.ensure_cultivation(self.s); self.choose_wood(); self.bind_wood()
                self.step(self.status()['wood_root']['cooldown_remaining']+2)
                self.rc(f'myvillage cultivation qi fill {guest.username}')
                self.cast_wood()
                self.rc(f'myvillage foundation grant shield {guest.username} myvillage:fixture_logout 8 400')
                self.rc(f'myvillage foundation grant healing_modifier {guest.username} myvillage:fixture_logout -0.5 400')
                before = self.status()
            finally:
                self.s = original; self.guest = None
        self.poll(lambda: self.rc('list'), lambda text: 'AlchemyGuest' not in text, label='真实退出完成')
        self.out = parent/'重连后'; self.out.mkdir()
        self.freeze()
        reconnect_tick = self.now()
        with SecondClient(self) as guest:
            self.guest = guest; self.s = SimpleNamespace(username=guest.username, game=guest.game, run=original.run, state=original.state, log=original.log)
            try:
                self.freeze(); after = self.status()
                self.check('同服重连启动期间时钟冻结', self.now() == reconnect_tick,
                           {'before_join': reconnect_tick, 'after_join': self.now()})
                self.check_cooldown_persisted('同服重连冷却保持', before, after)
                self.check('退出清理暂态', after['shield'] == 0 and not after['rooted'] and not after['fields'], after)
                self.cast_wood(False); self.assert_healing_restored('退出重连')
            finally:
                self.s = original; self.guest = None
        self.out = parent; self.freeze()

    def two_players(self):
        self.rc('tick unfreeze')
        with SecondClient(self) as guest:
            self.guest = guest; self.position(guest.game); self.configure(guest.username)
            self.freeze(); u = self.s.username; v = guest.username
            self.rc(f'tp {v} .5 -60 2.5 180 0')
            self.rc(f'myvillage cultivation fate reset {v}')
            other = self.status(v); self.cast_wood(); self.step(2)
            self.check('PvP开启敌方玩家受控', self.status(v)['rooted'], self.status(v))
            self.check('冷却灵力按玩家隔离', self.status(v)['qi'] == other['qi'] and self.status(v)['wood_root']['cooldown_remaining'] == other['wood_root']['cooldown_remaining'], {'before': other, 'after': self.status(v)})
            self.rooted_guest_input(guest)
            self.capture('双客户端根缠', guest.game)
            self.rc(f'myvillage foundation cleanse {v}')
            self.totem_lifesteal(v)
            self.rc('team add foundation_allies'); self.rc('team modify foundation_allies friendlyFire false')
            self.rc(f'team join foundation_allies {u}'); self.rc(f'team join foundation_allies {v}')
            self.step(self.status()['wood_root']['cooldown_remaining']+2); self.rc(f'myvillage cultivation qi fill {u}')
            self.cast_wood(); self.step(2)
            self.check('友军玩家不受控', not self.status(v)['rooted'], self.status(v))
            self.rc('team remove foundation_allies')
            self.rc(f'myvillage foundation grant shield {u} myvillage:fixture_private 8 50')
            self.check('护盾按玩家隔离', self.status(v)['shield'] == 0 and self.status()['shield'] == 8, {'owner': self.status(), 'guest': self.status(v)})
            self.guest = None; self.rc('tick unfreeze')
        self.freeze()
        # 第二轮换显式 pvp=false 的隔离服务器配置；禁止用队伍规则冒充服务端开关。
        original_out = self.out
        self.stop(); self.attach = False; self.pvp_enabled = False; self.start(reuse=True)
        self.prepare_case('two_players')
        self.out = original_out/'关闭PvP'; self.out.mkdir()
        self.rc('tick unfreeze')
        with SecondClient(self) as guest:
            self.guest = guest; self.configure(guest.username); self.freeze()
            self.rc(f'tp {guest.username} .5 -60 2.5 180 0')
            self.cast_wood(); self.step(2)
            self.check('服务端关闭PvP不控制玩家', not self.status(guest.username)['rooted'], self.status(guest.username))
            self.capture('关闭PvP边界', guest.game)
            self.guest = None; self.rc('tick unfreeze')
        self.out = original_out; self.freeze()

    def rooted_guest_input(self, guest):
        v, game = guest.username, guest.game
        try:
            game.require_ingame('根缠期间真实鼠标转向')
        except GameInputError:
            game.recover_grab()
            game.require_ingame('恢复抓取后真实鼠标转向')
        # 根缠开始的服务器位置纠正可能让 GLFW 重定位；等待客户端帧，不推进世界时钟。
        time.sleep(.3)
        before = read_data(self.rc(f'data get entity {v} Pos'))
        rotation_before = read_data(self.rc(f'data get entity {v} Rotation'))
        rotation_after = rotation_before
        for attempt in range(6):
            self.check('鼠标采样仍在根缠窗口', self.status(v)['rooted'], self.status(v))
            game.xdo('mousemove_relative', '--', '80', '0')
            self.report['inputs'].append({'method': '第二客户端独立映射鼠标转向', 'actor': v,
                                          'attempt': attempt+1, 'server_tick': self.now()})
            time.sleep(.2)
            self.step(1)
            # 服务器移动包可晚于 step 回执；等待实际 Rotation，不用 tp 伪造视角。
            for _ in range(4):
                rotation_after = read_data(self.rc(f'data get entity {v} Rotation'))
                if abs((rotation_after[0]-rotation_before[0]+180) % 360-180) > 1:
                    break
                time.sleep(.1)
            else:
                if attempt == 1:
                    game.recover_grab()
                    game.require_ingame('再次抓取后真实鼠标转向')
                continue
            break
        turned = abs((rotation_after[0]-rotation_before[0]+180) % 360-180) > 1
        self.check('根缠期间服务器视角确实更新', turned and self.status(v)['rooted'],
                   {'rotation_before': rotation_before, 'rotation_after': rotation_after})
        try:
            game.xdo('keydown', 'w')
            game.xdo('keydown', 'space')
            self.report['inputs'].append({'method': '第二客户端映射按住W与空格', 'actor': v,
                                          'server_tick': self.now()})
            time.sleep(.2)
            self.step(4)
            airborne = read_data(self.rc(f'data get entity {v} Pos'))
            self.step(2)
        finally:
            game.xdo('keyup', 'w', 'keyup', 'space')
        after = read_data(self.rc(f'data get entity {v} Pos'))
        self.check('移动采样仍在根缠窗口', self.status(v)['rooted'], self.status(v))
        self.check('根缠约束玩家移动包', abs(after[0]-before[0]) < .03 and abs(after[2]-before[2]) < .03,
                   {'before': before, 'after': after})
        self.check('根缠保留Y与视角更新', airborne[1] > before[1]+.1 and turned,
                   {'before': before, 'airborne': airborne,
                    'rotation_before': rotation_before, 'rotation_after': rotation_after})

    def totem_lifesteal(self, target):
        u = self.s.username
        self.rc(f'tp {u} .5 -60 .5 0 0')
        self.rc(f'tp {target} .5 -60 2.5 180 0')
        self.rc(f'item replace entity {u} hotbar.0 with myvillage:qingfeng_sword')
        self.rc(f'item replace entity {target} weapon.offhand with minecraft:totem_of_undying')
        self.rc(f'myvillage foundation clear {target}')
        self.rc(f'effect clear {target}')
        hp = self.hp(target)
        if hp > 1:
            self.report['inputs'].append({'method': '管理员图腾前1HP fixture', 'actor': target})
            self.rc(f'damage {target} {hp-1:.8f} minecraft:generic_kill')
        prepared = self.hp(target)
        self.check('图腾目标准备1HP', close(prepared, 1, .005), {'health': prepared})
        scene.ensure_cultivation(self.s)
        self.wound(20)
        window = self.healing_config['lifesteal_window_ticks']
        self.step(window+1)
        self.step(window-self.now()%window)
        before = self.hp(u)
        self.s.game.require_ingame('图腾真实自定义近战')
        self.s.game.xdo('click', '1')
        self.report['inputs'].append({'method': '映射左键青锋自定义近战触发图腾', 'target': target, 'server_tick': self.now()})
        self.step(16)
        inventory = read_data(self.rc(f'data get entity {target} Inventory'))
        target_after = self.hp(target); gained = self.hp(u)-before
        self.check('真实近战消费图腾且目标存活', not any(item.get('id') == 'minecraft:totem_of_undying' for item in inventory) and target_after > 0,
                   {'inventory': inventory, 'target_health': target_after})
        fraction = self.definitions['myvillage:'+FATE]['effects']['lifesteal_fraction']
        expected = prepared*fraction
        self.check('图腾按实际主伤害吸血且无过量', close(gained, expected, .005) and gained <= expected+.005,
                   {'prepared_health': prepared, 'gained': gained, 'expected': expected, 'target_after': target_after})
        self.capture('图腾自定义近战吸血')

    def run(self):
        assert_colab()
        self.start()
        for name in self.report['selected']:
            if self.s is None or getattr(self, '_session_unusable', False):
                self.report['phases'].append({'phase': '后续用例未运行', 'reason': '会话未通过启动检查',
                    'remaining': [case for case in self.report['selected'] if case in self.report['unrun']]})
                break
            self.case = name; self.out = self.root_out / name; self.out.mkdir()
            self.report['unrun'].remove(name)
            self.report['cases'][name] = {'result': 'running'}
            started = time.monotonic(); mark = self.offset()
            try:
                self.prepare_case(name)
                getattr(self, name)()
                self.report['cases'][name]['result'] = 'pass'
                self.report['completed'].append(name)
            except Exception as error:
                self.report['cases'][name].update(result='fail', error=repr(error))
                self.report['failures'].append(name + '：' + repr(error))
                (self.out / 'failure.txt').write_text(traceback.format_exc())
                if self.s is not None:
                    try:
                        self.capture('失败现场')
                    except Exception as shot_error:
                        self.report['cases'][name]['screenshot_error'] = repr(shot_error)
            finally:
                self.report['cases'][name]['wall_seconds'] = round(time.monotonic()-started, 3)
                try:
                    (self.out / 'server.log').write_text(self.log(mark))
                    if self.s:
                        shutil.copy2(self.s.game.client_log, self.out / 'client.log')
                except Exception as error:
                    self.report['failures'].append('保存日志：' + repr(error))
                self.save()
        self.out = self.root_out


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cases', default=None, help='逗号分隔用例；默认首轮四例；all 为全部')
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--attach', action='store_true', help='复用主代理已准备的隔离会话；结束仍清理游戏进程')
    parser.add_argument('--keep-game-on-failure', action='store_true')
    args = parser.parse_args(argv)
    cases = select_cases(args.cases)
    assert_colab()
    evidence = FoundationEvidence(args.out, cases, args.attach)
    try:
        evidence.run()
    except BaseException as error:
        evidence.report['failures'].append(repr(error)); traceback.print_exc()
    finally:
        evidence.out = evidence.root_out
        try:
            if evidence.s:
                evidence.rc('tick unfreeze')
                if evidence.report['failures'] and args.keep_game_on_failure:
                    evidence.report['game_retained_for_debug'] = True
                else:
                    evidence.stop()
        except Exception as error:
            evidence.report['failures'].append('清理失败：' + repr(error))
        evidence.report['passed'] = not evidence.report['failures'] and evidence.report['completed'] == cases
        evidence.report['unselected'] = [c for c in CASES if c not in cases]
        evidence.save()
    return 0 if evidence.report['passed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
