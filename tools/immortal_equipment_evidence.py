#!/usr/bin/env python3
"""五境装备映射输入采集；仅父代理在固定源Colab中使用。--plan可本地执行。"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
CASES = ('equip', 'poses', 'glass', 'mix', 'first_person', 'reload', 'profiles', 'two_observers')
RUNNABLE = CASES[:-1]
ARMOR = (('helmet', 103), ('chestplate', 102), ('leggings', 101), ('boots', 100))


def hand_snapshot(rows, selected):
    """Inventory persists the off-hand as signed byte slot -106, not slot 40."""
    if type(selected) is not int or not 0 <= selected <= 8:
        raise RuntimeError('非法热栏选择：' + repr(selected))
    return {'main': rows.get(selected, {}).get('id'), 'off': rows.get(-106, {}).get('id')}


def mapped_swap_hands(driver, label):
    before = driver.hands()
    if not before['main'] and not before['off']:
        raise RuntimeError('空手交换不能证明换手输入')
    expected = {'main': before['off'], 'off': before['main']}
    driver.key('f')
    after = driver.poll(driver.hands, lambda value: value == expected, label='映射F换手')
    driver.check(label + '_映射换手读回', after == expected, {'before': before, 'after': after})
    return after


def load_sets(root=ROOT):
    source = root / 'src/main/resources/data/myvillage/immortal_equipment/catalogue.json'
    return [s['id'] for s in json.loads(source.read_text())['sets']]


def verify_launch_artifacts(root):
    """Dev启动读classes/resources；必须与报告中的唯一jar逐条对应。"""
    jars = list((root / 'build/libs').glob('*.jar'))
    if len(jars) != 1: raise RuntimeError('需要唯一交付jar')
    checked = 0
    with zipfile.ZipFile(jars[0]) as jar:
        for name in jar.namelist():
            if name.endswith('/'):
                continue
            if name.startswith('com/example/myvillage/') and name.endswith('.class'):
                actual = root / 'build/classes/java/main' / name
            elif name.startswith(('assets/myvillage/', 'data/myvillage/')):
                actual = root / 'build/resources/main' / name
            else:
                continue
            if not actual.is_file() or actual.read_bytes() != jar.read(name):
                raise RuntimeError('实际启动产物与交付jar不一致：' + name)
            checked += 1
    if checked == 0: raise RuntimeError('交付jar缺模组代码与资源')
    return checked


def plan(sets):
    return {'kind': 'immortal_equipment', 'selected': list(CASES), 'completed': [], 'unrun': list(CASES),
            'runtime': 'not_verified', 'owner_appearance': 'not_verified', 'sets': sets,
            'automated_cases': list(RUNNABLE),
            'fixed_identity': ['source-manifest.json', 'source-files.json', 'exactly one build/libs jar and SHA256', 'collector SHA256'],
            'steps': {
                'equip': '十五套逐套以RCON补热栏，映射右键穿戴；逐件读回原版Inventory槽位；对应锭映射选中并捕获手持及背包模型。',
                'poses': '第三人称前后视；映射W行走、Shift蹲下、持剑鼠标左键举臂，录屏并读回位移。',
                'glass': '穿九色琉光，用前后第三人称截图记录玻璃背面与局部透明；只标captured_for_review。',
                'mix': '映射背包Shift点击脱掉四件；逐件单穿截图；再穿四套来源不同的混装并读回。',
                'first_person': '映射R进入并读回修仙战斗模式；四套分别持剑、枪、拳套，以F换至副手并读回、F换回、左键挥动和切空手，记录主副袖臂；仅captured_for_review。',
                'reload': '映射F3+T；等待实际原子重载日志；比较前后槽位；截图并保留client latest.log。',
                'profiles': '重启客户端以不同offline UUID取得另一默认身形；读回身形索引；重复十五套穿戴与动作。',
                'two_observers': '需总共三名真实客户端：穿戴者+两个观察者；现有session默认max-players=2，必须由父代理另备三客户端环境。两个观察者录制同一穿戴者走路/蹲下/举臂/换装/重载，操作由映射输入完成。'},
            'unsupported_case_reason': {'two_observers': '当前复用采集器只支持最多两客户端；本脚本不会伪造第三客户端或将先后单观察者拼成同时双观察者证据。'}}


def make_collector():
    # 延迟导入；--plan不加载X11、会话控制或云端依赖。
    from tools.equipment_evidence import EquipmentEvidence
    from tools.combat_capture import session as cs

    class ImmortalEvidence(EquipmentEvidence):
        def __init__(self, out, cases):
            self.designs = load_sets()
            self.active_design = 'session'
            super().__init__(out, cases)
            self.report.update(kind='immortal_equipment', declared_scope=list(CASES),
                               scope_unrun=[c for c in CASES if c not in cases],
                               multiplayer='not_verified', visual_checks='captured_for_review',
                               natural_acquisition='out_of_scope', long_term_balance='out_of_scope',
                               gameplay='out_of_scope', left_handed_first_person='not_verified')
            self.save()

        def capture(self, name, game=None):
            return super().capture(self.active_design + '-' + name, game)

        def start(self, reuse=False):
            cs.merge_options(cs.CLIENT_DIR/'options.txt', {
                'key_key.swapOffhand': 'key.keyboard.f',
                'key_key.myvillage.toggle_combat_mode': 'key.keyboard.r'})
            return super().start(reuse)

        def hands(self):
            return hand_snapshot(self.inventory(), self.data('SelectedItemSlot'))

        def verify_source(self):
            super().verify_source()
            self.report['equipment_driver_sha256'] = hashlib.sha256((ROOT / 'tools/equipment_evidence.py').read_bytes()).hexdigest()
            self.report['collector_sha256'] = hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
            self.report['launch_entries_equal_jar'] = verify_launch_artifacts(ROOT)
            # DemoEvidence校验冻结源与唯一jar；这里补验证客户端实际使用的松散构建资源。
            frozen = ROOT / 'src/main/resources/assets/myvillage/immortal_equipment'
            originals = [p for p in frozen.glob('*.json') if p.stem in self.designs]
            if len(originals) != 15: raise RuntimeError('冻结源缺十五套穿戴资源')
            for original in originals:
                built = ROOT / 'build/resources/main/assets/myvillage/immortal_equipment' / original.name
                if not built.is_file() or built.read_bytes() != original.read_bytes():
                    raise RuntimeError('运行资源与冻结源不同：' + str(built))

        def remove_all(self):
            self.key('e');self.poll(self.s.game.pointer_grabbed, lambda v: v is False, label='背包打开')
            for index, (_, slot) in enumerate(ARMOR):
                if slot in self.armor_rows():
                    self.inventory_shift_click(16, 16 + 18 * index)
                    self.poll(self.armor_rows, lambda rows: slot not in rows, label='映射脱下' + str(slot))
            self.close_menu()
            self.check(self.username + '_已脱下四槽', not self.armor_rows(), self.armor_rows())

        def wear(self, rows, label):
            self.active_design = label
            self.remove_all()
            # 已用映射输入脱下并读回，再清理库存fixture；十五套不会挤满36格而妨碍后续脱装。
            self.rc(f'clear {self.username}')
            for index, (design, suffix, slot) in enumerate(rows):
                identity = 'myvillage:' + design + '_' + suffix
                self.rc(f'item replace entity {self.username} hotbar.{index} with {identity}')
                self.key(str(index + 1));self.right()
                equipped = self.poll(self.armor_rows, lambda values: values.get(slot) == identity, label='映射穿戴' + identity)
                self.check(label + '_' + suffix, equipped.get(slot) == identity, equipped)
            self.key('e');self.poll(self.s.game.pointer_grabbed, lambda v: v is False, label='穿戴后背包')
            self.capture(self.username + '-' + label);self.close_menu()

        def equip(self):
            for design in self.designs:
                self.wear([(design, suffix, slot) for suffix, slot in ARMOR], design)
                ingot='myvillage:'+design+'_ingot'
                self.rc(f'item replace entity {self.username} hotbar.4 with {ingot}')
                self.key('5');self.capture(self.username+'-'+design+'-锭手持')
                self.check(design+'_锭进入实际物品栏',self.count(ingot)==1,self.inventory())
                self.key('e');self.poll(self.s.game.pointer_grabbed,lambda v:v is False,label='锭物品模型')
                self.capture(self.username+'-'+design+'-锭背包');self.close_menu()

        def first_person(self):
            from tools.combat_capture.scene import parse_combat_mode
            mode = lambda: parse_combat_mode(self.rc(f'data get entity {self.username} "neoforge:attachments"'))
            original = mode()
            if original != 'cultivation': self.key('r')
            active = self.poll(mode, lambda value: value == 'cultivation', label='映射进入修仙手臂渲染模式')
            self.check(self.username+'_实际启用武器IK模式', active == 'cultivation', active)
            try:
                for design in ('qi_tingyu','jindan_zixiao','yuanying_jiuse','huashen_taixu'):
                    self.wear([(design,suffix,slot) for suffix,slot in ARMOR],design)
                    for weapon in ('qingfeng_sword','lingxiao_spear','xuantie_gauntlet'):
                        identity='myvillage:'+weapon
                        self.rc(f'item replace entity {self.username} hotbar.4 with {identity}')
                        self.key('5')
                        self.poll(self.hands, lambda value: value == {'main': identity, 'off': None}, label='真实主手与空副手')
                        label=self.username+'-'+design+'-'+weapon
                        self.start_video(label+'-第一人称')
                        self.capture(label+'-持握')
                        self.hold([], .2, mouse=1, sample=lambda:self.capture(label+'-挥动'))
                        mapped_swap_hands(self,label+'-移至副手');self.capture(label+'-副手持物')
                        mapped_swap_hands(self,label+'-返回主手');self.capture(label+'-恢复主手')
                        self.check(label+'_换手不复制物品',self.count(identity)==1,self.inventory())
                        self.key('6')
                        self.poll(self.hands,lambda value: value == {'main':None,'off':None},label='映射切空手')
                        self.capture(label+'-切空手');self.stop_video()
            finally:
                if original != 'cultivation' and mode() == 'cultivation':
                    self.key('r');self.poll(mode,lambda value:value==original,label='还原采集前战斗模式')
            self.report['first_person_visual']='captured_for_review';self.save()

        def poses(self):
            # 代表低境、臂部雷光、玻璃、悬空法相与头/背动效；两种身形同样采样。
            for design in ('qi_moqing', 'jindan_zixiao', 'yuanying_jiuse', 'huashen_taixu', 'huashen_dari'):
                self.wear([(design, suffix, slot) for suffix, slot in ARMOR], design)
                self.start_video(self.username + '-' + design + '-动作')
                super().poses()

        def glass(self):
            self.wear([('yuanying_jiuse', suffix, slot) for suffix, slot in ARMOR], '玻璃穿戴')
            self.key('F5');self.key('F1');self.capture(self.username + '-玻璃背面')
            self.key('F5');self.capture(self.username + '-玻璃正面')
            self.key('F1');self.key('F5')
            self.report['glass_backface'] = 'captured_for_review';self.save()

        def mix(self):
            for suffix, slot in ARMOR:
                self.wear([('huashen_taixu', suffix, slot)], '单件-' + suffix)
            picks = ('qi_moqing', 'jindan_zixiao', 'yuanying_jiuse', 'huashen_dari')
            rows = [(design, suffix, slot) for design, (suffix, slot) in zip(picks, ARMOR)]
            self.wear(rows, '四套混穿')
            self.start_video(self.username + '-四套混穿-动作')
            super().poses()

        def reload(self):
            before = self.armor_rows()
            self.check('重载前至少一件新装备', bool(before) and all(v.startswith('myvillage:') and not v.startswith('myvillage:xuantie_') for v in before.values()), before)
            log = cs.CLIENT_DIR / 'logs/latest.log';offset = log.stat().st_size
            self.guard();self.s.game.xdo('keydown', 'F3')
            try:
                self.s.game.xdo('key', 't')
            finally:
                self.s.game.xdo('keyup', 'F3')
            self.action('mapped_resource_reload', keys=['F3', 't'])
            def tail():
                with log.open('rb') as stream:
                    stream.seek(offset)
                    return stream.read().decode(errors='replace')
            self.poll(tail, lambda text: '仙衣网格已原子加载：15 套' in text, 90, '十五套原子重载日志')
            self.poll(self.s.game.pointer_grabbed, lambda v: v is True, 90, '重载后游戏输入恢复')
            self.check('重载后槽位一致', self.armor_rows() == before, self.armor_rows())
            content = tail()
            self.check('重载无仙衣加载错误', not any(t in content for t in ('加载仙衣网格失败', '仙衣身份或境界与目录不符', '仙衣缺少槽位')), content[-3000:])
            self.key('e');self.poll(self.s.game.pointer_grabbed, lambda v: v is False, label='重载后背包')
            self.capture(self.username + '-资源重载');self.close_menu()
            (self.out / (self.username + '-reload.log')).write_text(content, encoding='utf-8')

        def profiles(self):
            self.stop();self.username = 'CaptureDev';self.start(reuse=True)
            self.equip();self.poses();self.glass();self.mix();self.first_person();self.reload()
            self.check('宽细臂实际默认皮肤', {p['profile'] for p in self.report['profiles']} == {'standard', 'slim'}, self.report['profiles'])

    return ImmortalEvidence


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--plan', action='store_true', help='只输出未运行验收计划；不启动游戏')
    parser.add_argument('--cases', default=','.join(RUNNABLE))
    args = parser.parse_args()
    if args.plan:
        try:
            sets = load_sets()
        except FileNotFoundError:
            sets = [s['id'] for level in range(1, 6) for s in json.loads((ROOT / f'art/immortal-armor-atlas/levels/level{level}.json').read_text())]
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(json.dumps(plan(sets), ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        print(args.out);return
    cases = args.cases.split(',')
    if not cases or len(set(cases)) != len(cases) or any(case not in RUNNABLE for case in cases):
        parser.error('cases需为可运行项；two_observers必须由父代理在三客户端环境单独采集')
    make_collector()(args.out.resolve(), cases).run()


if __name__ == '__main__':
    main()
