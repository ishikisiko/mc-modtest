#!/usr/bin/env python3
"""资质培养物品的作者入口；使用已有果实与原版书籍外观。"""
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def plan(root=ROOT):
    source = json.loads((root / 'tools/aptitudes/content.json').read_text())
    rules = json.loads((root / 'src/main/resources/data/myvillage/aptitudes/rules.json').read_text())
    assert set(source['topics']) == set(rules['elements'] + rules['weapons'] + rules['crafts'])
    result = {}
    for item, parent in source['models'].items():
        result[Path(f'src/main/resources/assets/myvillage/models/item/{item}.json')] = {'parent': parent}
    for topic, ingredient in source['topics'].items():
        item = 'aptitude_fruit' if topic in rules['elements'] else 'aptitude_treatise'
        for tier, catalyst in enumerate(source['tier_ingredients'], 1):
            out = {'id': 'myvillage:' + item, 'count': 1,
                   'components': {'minecraft:custom_data': {'myvillage_aptitude': {'aptitude': topic, 'tier': tier}}}}
            recipe = {'type': 'minecraft:crafting_shapeless', 'ingredients': [
                {'item': 'myvillage:ningyuan_fruit_low' if item == 'aptitude_fruit' else 'minecraft:book'},
                {'item': ingredient}, {'item': catalyst}], 'result': out}
            result[Path(f'src/main/resources/data/myvillage/recipe/aptitude_{topic}_{tier}.json')] = recipe
        kinds = ['combat_memory'] if topic in rules['weapons'] else ['research_notes'] if topic in rules['crafts'] else []
        for kind in kinds:
            for tier, catalyst in enumerate(source['tier_ingredients'], 1):
                result[Path(f'src/main/resources/data/myvillage/recipe/{kind}_{topic}_{tier}.json')] = {
                    'type': 'minecraft:crafting_shapeless',
                    'ingredients': [{'item': 'minecraft:writable_book'}, {'item': ingredient}, {'item': catalyst}],
                    'result': {'id': 'myvillage:' + kind, 'count': 1,
                               'components': {'minecraft:custom_data': {'myvillage_aptitude': {'aptitude': topic, 'tier': tier}}}}}
    for locale in ['zh_cn', 'en_us']:
        path = Path(f'src/main/resources/assets/myvillage/lang/{locale}.json')
        lang = json.loads((root / path).read_text())
        for item, name in source['names'].items():
            lang['item.myvillage.' + item] = name
        if locale == 'zh_cn':
            lang['screen.myvillage.cultivation.tab.crafting'] = '资质'
            lang['screen.myvillage.crafting.aptitude'] = '资质点数'
            lang['screen.myvillage.crafting.meaning'] = '五行、兵器与技艺资质可独立培养；学习条件和效果按对应点数计算。'
            lang['screen.myvillage.crafting.awaken'] = '前往测灵碑觉醒后查看各项资质。'
        result[path] = lang
    return {path: json.dumps(doc, ensure_ascii=False, indent=2) + '\n' for path, doc in result.items()}


def main():
    p = argparse.ArgumentParser(); p.add_argument('--check', action='store_true'); args = p.parse_args()
    stale = []
    for path, content in plan().items():
        target = ROOT / path
        if not target.exists() or target.read_text() != content:
            stale.append(str(path))
            if not args.check:
                target.parent.mkdir(parents=True, exist_ok=True); target.write_text(content)
    if args.check and stale:
        raise SystemExit('资质资源未同步：' + ', '.join(stale))
    print(f'资质资源：{len(plan())} 文件；' + ('检查通过' if args.check else f'更新 {len(stale)} 文件'))


if __name__ == '__main__':
    main()
