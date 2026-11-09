#!/usr/bin/env python3
"""宗门柜台和江湖榜的方块资源、中文名称及交互提示。"""
from __future__ import annotations
import argparse,json
from pathlib import Path
from language_assets import language_text,load_language

ROOT=Path(__file__).resolve().parents[1]

def encoded(value):return (json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()

def cuboid(low,high,texture):
    return {'from':low,'to':high,'faces':{side:{'texture':'#'+texture} for side in ('north','south','west','east','up','down')}}

def outputs(root=ROOT):
    base=root/'src/main/resources';out={}
    out[base/'data/myvillage/regiongen/settlement_board.json']=(root/'blueprints/economy/settlement_board.json').read_bytes()
    models={
        'linlang_counter':{'wood':'minecraft:block/dark_oak_planks','top':'minecraft:block/chiseled_bookshelf_top','metal':'minecraft:block/gold_block'},
        'jianghu_board':{'wood':'minecraft:block/stripped_spruce_log','paper':'minecraft:block/cartography_table_top','cap':'minecraft:block/dark_oak_planks'}}
    for key,textures in models.items():
        if key=='linlang_counter':
            elements=[cuboid([1,0,2],[15,12,14],'wood'),cuboid([0,12,0],[16,15,16],'top'),cuboid([3,4,1.8],[13,10,2],'metal')]
        else:
            elements=[cuboid([1,0,4],[4,16,12],'wood'),cuboid([12,0,4],[15,16,12],'wood'),cuboid([2,4,5],[14,15,11],'wood'),
                      cuboid([4,6,4.9],[12,14,5],'paper'),cuboid([0,15,3],[16,16,13],'cap')]
        model={'parent':'minecraft:block/block','textures':{'particle':textures['wood'],**textures},'elements':elements}
        out[base/f'assets/myvillage/models/block/{key}.json']=encoded(model)
        out[base/f'assets/myvillage/blockstates/{key}.json']=encoded({'variants':{f'facing={facing}':{'model':f'myvillage:block/{key}',**({'y':rotation} if rotation else {})} for facing,rotation in [('north',0),('east',90),('south',180),('west',270)]}})
        out[base/f'assets/myvillage/models/item/{key}.json']=encoded({'parent':f'myvillage:block/{key}'})
        out[base/f'data/myvillage/loot_table/blocks/{key}.json']=encoded({'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':f'myvillage:{key}'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    lang_path=base/'assets/myvillage/lang/zh_cn.json';original=load_language(lang_path.read_text());lang=dict(original)
    for key in ('screen.myvillage.scripture.borrow','screen.myvillage.scripture.borrow_cost',
                'message.myvillage.world.scripture.refused.insufficient_contribution','message.myvillage.world.scripture.refused.inventory_full',
                'message.myvillage.world.scripture.refused.inventory_changed'):lang.pop(key,None)
    lang.update({'block.myvillage.linlang_counter':'琳琅阁兑换柜台','block.myvillage.jianghu_board':'江湖榜',
                 'screen.myvillage.scripture_hall.borrow':'免费领取','screen.myvillage.scripture_hall.borrow_cost':'兑换（%1$s贡献）',
                 'screen.myvillage.commerce.status.reputation_low':'江湖声望不足，尚未解锁此商品。'})
    status={'ok':'请选择要办理的事务。','accepted':'事务已接取，目标和奖励已保存。','completed':'事务已交付，声望和灵石已到账。','exchanged':'兑换完成，物品已放入背包。',
            'not_member':'需要加入本宗才能兑换。','rank_too_low':'当前宗门身份尚未达到要求。','insufficient_contribution':'宗门贡献不足。','inventory_full':'背包空间不足，请整理后重试。',
            'missing_items':'交付物品不足，或物品带有额外组件。','task_active':'请先完成手中的江湖事务。','task_done_today':'本城今天的事务已领过，明天再来。','not_ready':'事务目标尚未完成。',
            'wrong_board':'请回接取任务的江湖榜交付。','stale':'事务或背包已经变化，请重新打开界面。','busy':'操作过快，请稍后再试。','unavailable':'此处暂时无法办理。'}
    lang.update({f'screen.myvillage.economy.status.{key}':value for key,value in status.items()})
    out[lang_path]=language_text(original,lang).encode()
    return out

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--check',action='store_true');a=p.parse_args();stale=[]
    for path,data in outputs().items():
        if a.check:
            if not path.exists() or path.read_bytes()!=data:stale.append(str(path.relative_to(ROOT)))
        else:path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
    if stale:raise SystemExit('资源未同步：'+', '.join(stale))
    print('玩家经济资源：'+('一致' if a.check else '已生成'))

if __name__=='__main__':main()
