"""命名空间变更后的资源拥有者重导出；不直接改写生成结果。"""
from pathlib import Path
import argparse,json,subprocess,sys,time,traceback
ROOT=Path(__file__).resolve().parents[1]
STATIC=[
 'gen_branding','gen_qingfeng_sword_model','gen_lingxiao_spear_model','gen_xuantie_gauntlet_model',
 'gen_scripture_shelf_model','gen_manual_textures','gen_sword_pal_anims','gen_blade_cut_sprite',
 'gen_meridian_figure','gen_meditation_motion','gen_cultivation_panel_assets','gen_cultivation_ink_assets',
 'gen_quality_picker_assets','gen_alchemy_assets','gen_spirit_crops','gen_spirit_stone_grades','gen_spirit_stone_bag',
 'gen_forging_assets','gen_forging_array_assets','gen_alchemy_array_assets','gen_talisman_assets','gen_gathering_assets',
 'gen_aptitude_resources','gen_advanced_arts','gen_advanced_art_icons','gen_elemental_arts','gen_technique_catalogue',
 'gen_technique_scroll_assets','gen_flying_swords','gen_ningyuan_tree','gen_blood_spirit_ingot','gen_player_economy_assets',
 'gen_tribulation','gen_sword_qi_decoration','gen_thunder_tiger_support','gen_wilderness_roster','gen_undead_roster',
 'gen_legacy_encounters','gen_technique_growth']
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--start',type=int,default=0);args=parser.parse_args();out=args.out;out.mkdir(parents=True,exist_ok=False)
 commands=[[sys.executable,'-m',f'tools.{name}'] for name in STATIC]
 commands += [[sys.executable,'-m','tools.demon_pact_pillar.generate'],[sys.executable,'-m','tools.equipment','build'],[sys.executable,'-m','tools.immortal_equipment','build','--rename-namespace-from','myvillage'],[sys.executable,'-m','tools.ore_textures.generate']]
 # Model families retain their dedicated authoring systems.
 sys.path.insert(0,str(ROOT))
 from tools.beastgen.build import DEFINITIONS
 from tools.beastgen import legacy,undead
 from tools.npcgen.build import DEFINITIONS as NPCS
 commands += [[sys.executable,'-m','tools.beastgen','build',name] for name in [*DEFINITIONS,*legacy.names(),*undead.names()]]
 commands += [[sys.executable,'-m','tools.npcgen','build',name] for name in NPCS]
 commands += [[sys.executable,'-m','tools.npcgen','variants'],[sys.executable,'-m','tools.npcgen','goldens']]
 commands += [[sys.executable,'-m','tools.buildgen.gen_plaque_assets','--no-png'],
              [sys.executable,'-c','from tools.buildgen import rockery_models as r; r.from_voxel_json(); variants=r.VARIANT_CATALOG+r.HERO_CATALOG; cache={v.variant_id:r.derive_variant_voxels(v.variant_id) for v in variants}; r.write_models(cache,variants); r.write_blockstate(variants)'],
              [sys.executable,'-m','tools.gen_forging_assets'],
              [sys.executable,'-m','tools.generate_region_topology','20260620','--out','src/main/resources/data/yuanxian/worldgen/region_topology_example.json'],
              [sys.executable,'-m','tools.equipment','build'],
              [sys.executable,'-m','tools.immortal_equipment','check'],
              [sys.executable,'-m','tools.gen_ningyuan_tree','--rename-namespace-from','myvillage'],
              [sys.executable,'-m','tools.gen_spirit_crops','--rename-namespace-from','myvillage'],
              [sys.executable,'-m','tools.gen_road_ports','--rename-namespace-from','myvillage']]
 total=len(commands)
 if not 0<=args.start<total:raise ValueError('起始索引超出导出清单')
 commands=commands[args.start:]
 report={'status':'running','start':args.start,'owner_commands_total':total,'selected':len(commands),'completed':[],'failed':[],'unrun':commands.copy()}
 def save(): (out/'result.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
 save()
 for i,cmd in enumerate(commands):
  print(f'[{i+args.start+1}/{total}] '+ ' '.join(cmd),flush=True);start=time.monotonic()
  with (out/f'{i:03d}.log').open('w') as log:r=subprocess.run(cmd,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT)
  row={'command':cmd,'seconds':time.monotonic()-start,'returncode':r.returncode,'log':f'{i:03d}.log'}
  if r.returncode:report['status']='failed';report['failed'].append(row);report['unrun']=commands[i+1:];save();raise SystemExit(r.returncode)
  report['completed'].append(row);report['unrun']=commands[i+1:];save()
 report['status']='passed';save()
if __name__=='__main__':main()
