"""按实际用途给掉落候选物品分类；分类顺序同时用于网页排序。"""
from __future__ import annotations
import json,re

CATEGORIES=[('materials','材料'),('pills','丹药与药水'),('weapons','武器'),('armor','防具'),('artifacts','法器与符箓'),('manuals','秘籍、丹方与图样'),('tools','工具'),('food','食物'),('utility','功能物品'),('eggs','刷怪蛋'),('decoration','装饰物'),('blocks','方块'),('other','其他物品')]
MATERIALS=set('coal charcoal diamond emerald lapis_lazuli redstone quartz amethyst_shard echo_shard flint clay_ball brick nether_brick stick string leather rabbit_hide rabbit_foot rotten_flesh bone bone_meal spider_eye fermented_spider_eye ender_pearl ender_eye blaze_rod blaze_powder ghast_tear slime_ball magma_cream gunpowder phantom_membrane shulker_shell prismarine_shard prismarine_crystals scute turtle_scute armadillo_scute feather ink_sac glow_ink_sac honeycomb beeswax paper sugar wheat cocoa_beans glowstone_dust nether_star breeze_rod wind_charge nautilus_shell heart_of_the_sea resin_clump nether_wart fire_charge snowball egg popped_chorus_fruit chorus_fruit netherite_scrap dragon_breath experience_bottle'.split())
ARTIFACTS={'beast_fire_seed','cold_fire_seed','earth_fire_seed','samadhi_fire_seed','thunder_fire_seed','demon_lure_pearl','fire_capture_bottle','chiyu_gourd','zhaoshui_mirror','zhenyue_ruler'}

def classify(root,reader,items):
    alchemy=json.loads((root/'src/main/resources/data/yuanxian/alchemy/rules.json').read_text())
    pills={r['output'] for r in alchemy['recipes']};herbs={v for r in alchemy['recipes'] for v in r['ingredients']}
    weapon_ids={p.stem for p in (root/'src/main/resources/data/yuanxian/combat/weapon').glob('*.json')}
    def category(v):
        id=v['id'];ns,n=id.split(':');label=v['name'];model=reader.model(ns+':item/'+n)
        if id in pills or n.startswith('pill_') or n in {'potion','splash_potion','lingering_potion','tipped_arrow'}:return 'pills'
        if n.startswith('workpiece_'):return 'materials'
        if n.startswith('pattern_'):return 'manuals'
        if n.endswith(('_helmet','_chestplate','_leggings','_boots','_horse_armor')) or n in {'elytra','turtle_helmet','wolf_armor','shield'}:return 'armor'
        if n in weapon_ids or n.endswith(('_sword','_spear','_gauntlet')) or n in {'bow','crossbow','trident','mace','arrow','spectral_arrow'}:return 'weapons'
        if n in ARTIFACTS or 'talisman' in n or 'flying_sword' in n:return 'artifacts'
        if n.startswith(('formula_','manual_','technique_')) or n.endswith(('_treatise','_handbook','_rubbing')) or n in {'combat_memory','research_notes','watermarked_silk','enchanted_book','book','writable_book','written_book'} or '秘籍' in label:return 'manuals'
        if n.endswith(('_pickaxe','_axe','_shovel','_hoe','_hammer')) or n in {'shears','brush','fishing_rod','flint_and_steel','carrot_on_a_stick','warped_fungus_on_a_stick','compass','recovery_compass','clock','spyglass','lead','name_tag'}:return 'tools'
        if n.endswith('_spawn_egg'):return 'eggs'
        if id in herbs or n in MATERIALS or n.endswith(('_ingot','_nugget','_handle','_scrap','_dye','_shard','_plume','_fang','_hide','_scale','_pottery_sherd','_smithing_template')) or n.startswith(('raw_','low_grade_spirit_stone','medium_grade_spirit_stone','high_grade_spirit_stone')) or ns=='yuanxian' and n in {'thorn_bone','ember_cinnabar','edge_tempering_sand','spirit_silk','lingcao_fiber','spiritual_ink','stele_stone_heart','sword_spirit_core'}:return 'materials'
        if ns=='minecraft' and (n.startswith(('cooked_','raw_')) or n in {'apple','golden_apple','enchanted_golden_apple','bread','beef','porkchop','mutton','chicken','rabbit','cod','salmon','tropical_fish','pufferfish','carrot','golden_carrot','potato','baked_potato','poisonous_potato','beetroot','beetroot_soup','mushroom_stew','rabbit_stew','suspicious_stew','cookie','pumpkin_pie','melon_slice','sweet_berries','glow_berries','honey_bottle','dried_kelp','rotten_flesh','spider_eye'}):return 'food'
        if ns=='yuanxian' and ('fruit' in n or '灵果' in label):return 'food'
        parent=model.get('parent','')
        if n in {'spirit_stone_bag','ningyuan_fertilizer','quality_picker'}:return 'utility'
        block=v.get('is_block') or parent.startswith(('minecraft:block/','yuanxian:block/','block/'))
        if block or ns=='yuanxian' and re.search(r'(匾|瓦|窗|梁|柱|栏|檐|牌坊|庭院|假山|石狮|地毯)',label):
            if any(s in n for s in ['furnace','array_','anvil','table','kiln','grind','hearth','jianghu_board','gathering_']):return 'utility'
            if any(s in n for s in ['flower','sapling','carpet','banner','painting','lantern','sign','plaque','candle','head','skull','pot','vase','window','roof','courtyard','sculpture','rockery','decor','furniture']) or any(s in label for s in ['匾','窗','雕','装饰','牌坊']):return 'decoration'
            return 'blocks'
        if n.endswith(('_bucket','_boat','_raft','_minecart')) or n in {'bucket','saddle','bundle','totem_of_undying','ominous_bottle','goat_horn','firework_rocket','firework_star','map','filled_map'}:return 'utility'
        if n.startswith('music_disc_') or n in {'item_frame','glow_item_frame','painting','armor_stand','flower_pot'}:return 'decoration'
        return 'other'
    for v in items:v['category']=category(v)
    rank={key:i for i,(key,_) in enumerate(CATEGORIES)}
    items.sort(key=lambda v:(rank[v['category']],v['namespace']!='yuanxian',v['id'].split(':')[1].startswith('workpiece_'),v['id']))
    return [{'id':key,'name':name,'count':sum(v['category']==key for v in items)} for key,name in CATEGORIES]
