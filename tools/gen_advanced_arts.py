#!/usr/bin/env python3
"""Generate the six composite arts, existing-system manuals and bilingual presentation text.

Owns combat/advanced_arts.json, these six technique definitions, survival manual
recipes/unlocks, one book-grant function, one handbook page, and explicitly named locale keys. The legacy
catalogue's hand_written_techniques list reserves these IDs for this generator.
Never modify generated output by hand; --check is read-only and reports drift.
"""
from __future__ import annotations
import argparse
import json
import math
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
SOURCE = Path('tools/advanced_arts/arts.json')
DATA = Path('src/main/resources/data/myvillage')
ASSETS = Path('src/main/resources/assets/myvillage')
ART_FIELDS = set('id name_zh name_en description_zh description_en family school slot qi_cost cooldown_ticks grade minimum_realm minimum_stage study guard_reduction guard_arc_degrees counter_multiplier color particle_count phases requires_ground interrupt_on_damage fx_lifetime_ticks cast_pitch'.split())
PHASE_FIELDS = set('move shape reach arc_degrees width height half_height damage_multiplier maximum_targets step_distance reaction fx'.split())
STUDY_FIELDS = {'points', 'gates', 'gate_stability_cost'}
REACTION_FIELDS = {'hitstun_ticks', 'slide_distance', 'lift', 'lateral_bias'}
META_FIELDS = set('name_zh name_en description_zh description_en grade minimum_realm minimum_stage study'.split())
SHAPES = {'line', 'fan', 'ring'}
FX = {'arc', 'helix', 'ring', 'lotus', 'shock'}


def _pairs(pairs: list[tuple[str, Any]]) -> dict:
    result = {}
    for k, v in pairs:
        if k in result:
            raise ValueError(f'duplicate JSON key {k}')
        result[k] = v
    return result


def read(path: Path) -> Any:
    return json.loads(path.read_text(encoding='utf-8'), object_pairs_hook=_pairs)


def dump(data: Any) -> str:
    return json.dumps(data, ensure_ascii=False, indent=2) + '\n'


def exact(row: Any, keys: set[str], where: str) -> None:
    if not isinstance(row, dict) or set(row) != keys:
        raise ValueError(f'{where}: expected exactly {sorted(keys)}')


def number(row: dict, key: str, low: float, high: float, integer: bool = False) -> None:
    v = row[key]
    if isinstance(v, bool) or not isinstance(v, (int, float)) or not math.isfinite(v) or not low <= v <= high or (integer and not isinstance(v, int)):
        raise ValueError(f'{key}: expected {"integer" if integer else "number"} in {low}..{high}')


def validate(root: Path, doc: dict) -> None:
    exact(doc, {'schema', 'arts'}, 'arts')
    if type(doc['schema']) is not int or doc['schema'] != 1 or not isinstance(doc['arts'], list) or len(doc['arts']) != 6:
        raise ValueError('schema 1 with exactly six initial arts required')
    index = read(root / DATA / 'combat/index.json')
    moves = {}
    for sid in index['styles']:
        style = read(root / DATA / 'combat/style' / (sid.split(':')[1]+'.json'))
        for move in style['moves']:
            moves[move['id']] = (sid, move)
    families: dict[str, set[str]] = {}
    for wid in index['weapons']:
        weapon = read(root / DATA / 'combat/weapon' / (wid.split(':')[1]+'.json'))
        families.setdefault(weapon['family'], set()).add(weapon['style'])
    reserved = read(root / 'tools/technique_catalogue/rules.json')['hand_written_techniques']
    ids, slots = set(), set()
    for art in doc['arts']:
        exact(art, ART_FIELDS, 'art')
        import re
        if not isinstance(art['id'], str) or not re.fullmatch(r'myvillage:[a-z0-9_]+', art['id']):
            raise ValueError('art ID must be myvillage:<path>')
        if art['id'] in ids or (art['family'], art['slot']) in slots:
            raise ValueError('duplicate art ID or family/slot')
        ids.add(art['id']); slots.add((art['family'], art['slot']))
        if art['id'].split(':')[1] not in reserved:
            raise ValueError('reserve art ID in technique_catalogue/rules.json before generating')
        for field in ('name_zh', 'name_en', 'description_zh', 'description_en'):
            if not isinstance(art[field], str) or not art[field].strip():
                raise ValueError(f'missing {field}')
        for field, lo, hi in [('slot',0,1), ('qi_cost',1,1000), ('cooldown_ticks',20,1200), ('grade',1,4), ('particle_count',8,64), ('fx_lifetime_ticks',1,40)]:
            number(art, field, lo, hi, True)
        for field, lo, hi in [('guard_reduction',0,.85), ('guard_arc_degrees',1,180), ('counter_multiplier',1,2), ('cast_pitch',.1,2)]:
            number(art, field, lo, hi)
        for field in ('requires_ground', 'interrupt_on_damage'):
            if type(art[field]) is not bool: raise ValueError(f'{field} must be boolean')
        if not isinstance(art['color'], list) or len(art['color']) != 3: raise ValueError('RGB needs 3 values')
        for c in art['color']: number({'color':c}, 'color', 0, 1)
        if art['family'] not in families: raise ValueError('unknown weapon family')
        school = read(root / DATA / 'myvillage/school' / (art['school'].split(':')[1]+'.json'))
        if school.get('weapon_family') != art['family']: raise ValueError('school/family mismatch')
        realm = read(root / DATA / 'myvillage/realm' / (art['minimum_realm'].split(':')[1]+'.json'))
        if art['minimum_stage'] not in {s['id'] for s in realm['stages']}: raise ValueError('unknown stage')
        exact(art['study'], STUDY_FIELDS, 'study')
        number(art['study'],'points',1,1000000,True)
        number(art['study'],'gates',0,10,True)
        number(art['study'],'gate_stability_cost',0,10000,True)
        if not isinstance(art['phases'], list) or not 1 <= len(art['phases']) <= 6: raise ValueError('phase count 1..6')
        for phase in art['phases']:
            exact(phase, PHASE_FIELDS, 'phase')
            if phase['move'] not in moves or moves[phase['move']][0] not in families[art['family']]:
                raise ValueError('phase move belongs to another weapon family')
            if phase['shape'] not in SHAPES or phase['fx'] not in FX: raise ValueError('unknown shape/fx')
            for key, lo, hi in [('reach',.1,8),('arc_degrees',1,360),('width',.1,2),('height',.1,3),('half_height',.1,2),('damage_multiplier',.01,4),('step_distance',0,1.6)]:
                number(phase,key,lo,hi)
            number(phase,'maximum_targets',1,12,True)
            if phase['shape']=='ring' and phase['arc_degrees']!=360: raise ValueError('ring must cover 360 degrees')
            exact(phase['reaction'], REACTION_FIELDS, 'reaction')
            number(phase['reaction'],'hitstun_ticks',0,60,True)
            for key,lo,hi in [('slide_distance',0,4),('lift',0,.6),('lateral_bias',-1,1)]:
                number(phase['reaction'],key,lo,hi)
    if slots != {(f,s) for f in families for s in (0,1)}:
        raise ValueError('every current weapon family needs exactly two art slots')


def plan(root: Path = ROOT) -> dict[Path, str]:
    source = read(root / SOURCE)
    validate(root, source)
    arts = source['arts']
    output = {DATA / 'combat/advanced_arts.json': dump({'schema':1, 'arts':[
        {k:v for k,v in art.items() if k not in META_FIELDS} for art in arts]})}
    keys = {
        'zh_cn': {
            'key.myvillage.art_primary':'技能快捷键 1（默认 Z）', 'key.myvillage.art_secondary':'技能快捷键 2（默认 C）',
            'combat.myvillage.art.unlearned':'需学会 %s 并满足当前境界条件',
            'combat.myvillage.art.cooldown':'武技冷却：%s 秒',
            'combat.myvillage.art.power':'灵力不足，需要 %s 点',
            'combat.myvillage.art.ground':'此武技需要站稳地面',
            'combat.myvillage.art.counter':'格挡成功：本次反击强化',
            'combat.myvillage.art.locked':'未学', 'combat.myvillage.art.qi':'灵力',
        },
        'en_us': {
            'key.myvillage.art_primary':'Advanced weapon art I', 'key.myvillage.art_secondary':'Advanced weapon art II',
            'combat.myvillage.art.unlearned':'Learn %s and meet its current realm requirements',
            'combat.myvillage.art.cooldown':'Art cooldown: %s seconds',
            'combat.myvillage.art.power':'Not enough spiritual power: %s required',
            'combat.myvillage.art.ground':'This art requires firm ground',
            'combat.myvillage.art.counter':'Guarded: this cast is empowered',
            'combat.myvillage.art.locked':'Locked', 'combat.myvillage.art.qi':'Qi',
        }
    }
    for art in arts:
        rid = art['id'].split(':')[1]
        translation = f'cultivation.technique.myvillage.{rid}'
        output[DATA / f'myvillage/technique/{rid}.json'] = dump({
            'translation_key':translation, 'category':'active', 'grade':art['grade'], 'elements':[],
            'school':art['school'], 'requirements':{'minimum_realm':art['minimum_realm'], 'minimum_stage':art['minimum_stage']},
            'effects':{'active':{'skill':art['id'], 'qi_cost':art['qi_cost'], 'slots':1}}, 'study':art['study']})
        for lang,suffix in [('zh_cn','zh'),('en_us','en')]:
            keys[lang][translation] = art[f'name_{suffix}']
            keys[lang][f'combat.myvillage.art.{rid}.description'] = art[f'description_{suffix}']
    for lang, patch in keys.items():
        path = ASSETS / f'lang/{lang}.json'
        current = read(root / path)
        current.update(patch)
        output[path] = dump(current)
    output[DATA / 'function/advanced_arts_books.mcfunction'] = (
        '# Administrator fixture: grants manuals only; never changes realm or learned state.\n' +
        ''.join(f'myvillage cultivation manual @s {a["id"]}\n' for a in arts))
    guide = '''---
navigation:
  title: 高级武技 / Advanced Weapon Arts
  parent: index.md
  position: 35
  icon: myvillage:qingfeng_sword
---
# 高级武技 / Advanced Weapon Arts

学习对应技能书并达到炼气四层。在修炼战斗模式下，手持对应武器：
Learn the matching manual and reach Qi Refining IV. Hold its weapon in cultivation combat mode.

<KeyBind id="key.myvillage.art_primary" />：第一招 / art I。
<KeyBind id="key.myvillage.art_secondary" />：第二招 / art II。
原右键剑气、普攻和闪避不变。Existing sword qi, basic attacks and dodge keep their bindings.

'''
    for art in arts:
        guide += f'## {art["name_zh"]} / {art["name_en"]}\n\n{art["description_zh"]}\n\n{art["description_en"]}\n\n基础灵力 / base Qi: {art["qi_cost"]} · 冷却 / cooldown: {art["cooldown_ticks"]/20:g}s\n\n'
    guide += '格挡只在第一段起手、正面生效，不是持续无敌。被打断不退灵力，换武器不重置冷却。\nGuards only protect the first wind-up from frontal attacks, not sustained invulnerability. Interruptions do not refund power; changing weapons does not reset cooldowns.\n'
    acquisition = read(root / 'tools/advanced_arts/acquisition.json')
    exact(acquisition, {'schema', 'common', 'materials'}, 'acquisition')
    if acquisition['schema'] != 1 or set(acquisition['materials']) != {a['id'].split(':')[1] for a in arts}:
        raise ValueError('acquisition must cover exactly the six arts')
    guide += '\n## 生存获取\n\n六本武技书可在工作台制作；书中已写明对应武技，仍需研读并满足境界。使用书、墨囊、下品灵石和对应的专属材料，具体数量见配方。配方不消耗已有秘籍，不复制参悟进度。\n\n'
    for art in arts:
        rid = art['id'].split(':')[1]
        ingredients = acquisition['common'] + [{'item': item} for item in acquisition['materials'][rid]]
        if not 1 <= len(ingredients) <= 9 or any(not isinstance(i,dict) or set(i) not in ({'item'},{'tag'}) for i in ingredients):
            raise ValueError('invalid manual ingredients')
        import re
        if any(not isinstance(v,str) or not re.fullmatch(r'[a-z0-9_.-]+:[a-z0-9_./-]+',v)
               for i in ingredients for v in i.values()):
            raise ValueError('invalid acquisition resource id')
        recipe_id = f'myvillage:manual_{rid}'
        output[DATA / f'recipe/manual_{rid}.json'] = dump({
            'type':'minecraft:crafting_shapeless','category':'misc','ingredients':ingredients,
            'result':{'id':'myvillage:manual_active_xuan','count':1,'components':{'myvillage:technique':art['id']}}})
        output[DATA / f'advancement/recipes/manual_{rid}.json'] = dump({
            'parent':'minecraft:recipes/root',
            'criteria':{'has_book':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':'minecraft:book'}]}},
                        'has_the_recipe':{'trigger':'minecraft:recipe_unlocked','conditions':{'recipe':recipe_id}}},
            'requirements':[['has_book','has_the_recipe']], 'rewards':{'recipes':[recipe_id]}})
        guide += f'### {art["name_zh"]}\n\n<Recipe id="{recipe_id}" />\n\n'
    guide += '\n## 流派战法\n\n剑修：普通剑招有效命中或回风剑阵正面接招积累剑意，右键释放剑气时消耗。剑意与天命剑痴的剑势独立。\n\n枪修：枪尖距离命中时出现“枪锋命中”，伤害与破势略增。被贴身时失去这项优势，横扫可拉开距离。\n\n体修：金刚震短暂护体时正面接招获得蓄劲，再施放崩山劲消耗强化。火焰、摔落和背击不能蓄劲。\n\n剑意和蓄劲会超时消散；换武器、退出战斗模式、死亡和离线清空。冷却仍遵守原有武技规则。\n'
    guide += '\n## 统一快捷栏\n\n物品栏两侧各两格：下排是 Z / C 高级武技；左上是右键剑气，右上是 Alt 身法。剑气格显示蓄力和收招，身法格显示实际共享冷却。没有对应已学功法或当前武器不适用时显示锁；临时不可用显示横杠，缺灵力显示红色。地面身法不耗灵力。图标使用当前实际键位；心法、被动和自动触发天命不占主动快捷位。\n'
    guide += '\n## 已学技能按键分配\n\n按 H 打开「技能」页，先选下方键位，再点已学技能右侧的「分配」。预留 Z、C、I、J、K、M 六键；Z/C 默认随武器切换两招，其余留空。可清空选中键、让 Z/C 恢复随武器，或恢复整套默认。绑定同一技能到另一键会移动原绑定。剑气绑定键按住蓄力、松开释放；身法按下闪避。右键与 Alt 原入口也可使用。额外槽位只在分配后显示，仍检查武器、学习和冷却；换键不能绕过共享冷却。\n\n按键冲突会在页内提示；可进入「按键设置」更换该槽的物理按键。绑定保存在当前角色，退出或死亡重生后保留；心法、被动和尚无施放入口的功法在「功法」页查看。\n'
    output[Path('guidebook/getting_started/advanced_arts.md')] = guide
    output[Path('guidebook/_en_us/getting_started/advanced_arts.md')] = guide
    return output


def generate(root: Path = ROOT, check: bool = False) -> list[Path]:
    changed = []
    for path, text in plan(root).items():
        target = root / path
        if not target.exists() or target.read_text(encoding='utf-8') != text:
            changed.append(path)
            if not check:
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(text, encoding='utf-8')
    return changed


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    try:
        changed = generate(check=args.check)
    except (OSError, ValueError, KeyError, TypeError) as exc:
        parser.exit(2, f'advanced arts: {exc}\n')
    for path in changed: print(f'{"STALE" if args.check else "WROTE"} {path}')
    if args.check and not changed: print('Advanced arts: all generated resources match authored inputs')
    return int(args.check and bool(changed))

if __name__ == '__main__':
    raise SystemExit(main())
