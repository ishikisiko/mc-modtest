#!/usr/bin/env python3
"""Spirit stone bag resources. Reuses the vanilla bundle icon; rules JSON stays authored."""
import argparse
import json
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
LANG = {
    'item.yuanxian.spirit_stone_bag': ('Spirit Stone Bag', '灵石袋'),
    'tooltip.yuanxian.spirit_stone_bag.balance': ('Stored: %s low-grade spirit stones', '已存：%s 枚下品灵石'),
    'tooltip.yuanxian.spirit_stone_bag.capacity': ('Capacity: unlimited', '容量：无上限'),
    'tooltip.yuanxian.spirit_stone_bag.on': ('Automatic collection: on', '自动收纳：开启'),
    'tooltip.yuanxian.spirit_stone_bag.off': ('Automatic collection: paused (payment remains available)', '自动收纳：暂停（仍可直接支付）'),
    'tooltip.yuanxian.spirit_stone_bag.use': ('Right-click: toggle collection. Carried bags pay automatically.', '右键切换收纳；随身携带即可自动支付。'),
    'tooltip.yuanxian.spirit_stone_bag.withdraw': ('Sneak + right-click: pause this bag and withdraw a stack.', '潜行右键：暂停此袋并取出一叠。'),
    'message.yuanxian.spirit_stone_bag.full': ('Not enough inventory space to withdraw a stack.', '背包空间不足，无法取出一叠。'),
}
def encoded(value): return (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode()
def outputs(root=ROOT):
    base = root/'src/main/resources'
    out = {
        base/'assets/yuanxian/models/item/spirit_stone_bag.json': encoded({'parent':'minecraft:item/generated','textures':{'layer0':'minecraft:item/bundle'}}),
        base/'data/yuanxian/advancement/recipes/misc/spirit_stone_bag.json': encoded({'parent':'minecraft:recipes/root',
            'criteria':{'has_leather':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':['minecraft:leather']}]}},
            'has_recipe':{'trigger':'minecraft:recipe_unlocked','conditions':{'recipe':'yuanxian:spirit_stone_bag'}}},
            'requirements':[['has_leather','has_recipe']],'rewards':{'recipes':['yuanxian:spirit_stone_bag']}}),
        base/'data/yuanxian/tags/item/spirit_stone_bags.json': encoded({'replace':False,'values':['yuanxian:spirit_stone_bag']}),
        base/'data/yuanxian/recipe/spirit_stone_bag.json': encoded({'type':'minecraft:crafting_shapeless','category':'misc',
            'ingredients':[{'item':'minecraft:leather'},{'item':'minecraft:leather'},{'item':'minecraft:string'},{'tag':'c:spirit_stones/low_grade'}],
            'result':{'id':'yuanxian:spirit_stone_bag','count':1}}),
    }
    for lang, index in [('en_us',0),('zh_cn',1)]:
        path=base/f'assets/yuanxian/lang/{lang}.json';current=json.loads(path.read_text());current.update({k:v[index] for k,v in LANG.items()});out[path]=encoded(current)
    return out
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--check',action='store_true');a=p.parse_args();stale=[]
    for path,data in outputs().items():
        if a.check:
            if not path.exists() or path.read_bytes()!=data:stale.append(str(path.relative_to(ROOT)))
        else:path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
    if stale:print('\n'.join(stale));return 1
    print('Spirit stone bag resources '+('match' if a.check else 'generated'));return 0
if __name__=='__main__':raise SystemExit(main())
