#!/usr/bin/env python3
"""Real Minecraft proof for the independent courtyard; Modal workspace only."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import sys
import time
import zipfile

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT));sys.path.insert(0,str(ROOT/'tools'))
from tools.realm_stats_evidence import Evidence
from tools.yunshan.game_evidence import BuildingEvidence,aim
from tools.yunshan.game_export import read_typed,saved_cells
from tools.combat_capture import session as cs,scene
from tools.alchemy_quality_evidence import read_data
import json_to_nbt as nbt


class CourtyardEvidence(BuildingEvidence):
    def __init__(self,out):
        Evidence.__init__(self,out)
        base=out.parent
        self.spec=json.loads((base/'build-manifest.json').read_text())
        self.d=json.loads((base/'reference_courtyard.structure.json').read_text())
        self.hud_hidden=False
        self.report.update(kind='minecraft_reference_courtyard',selected=['place_template','mapped_walks','mapped_block_placement','game_screenshots','native_structure_save','restart_readback','world_export'],completed=[],unrun=[],
            buildings={self.d['id']:{'screenshots':[]}},software_rendering=True,
            owner_appearance='not_verified',owner_fps='not_verified',multiplayer='not_verified')

    def probe(self,pos,state,label):
        world=self.xyz(self.d,pos)
        reply=self.rc('execute if block '+' '.join(str(int(v)) for v in world)+' '+state)
        self.check(label,'passed' in reply,{'position':world,'state':state,'reply':reply})

    def setup(self):
        u=self.s.username
        for cmd in (*scene.world_rules(),f'op {u}',f'clear {u}',f'gamemode creative {u}',
                    f'effect give {u} minecraft:saturation infinite 255 true',
                    'gamerule doFireTick false','gamerule randomTickSpeed 0','myvillage world pause',
                    'forceload add -16 -16 80 64','fill -8 -62 -8 64 -61 48 minecraft:smooth_stone',
                    'setworldspawn 45 -57 2 0','defaultgamemode creative'):
            self.rc(cmd)
        origin=self.d['site']['world_min']
        self.rc(f'place template {self.d["id"]} '+' '.join(map(str,origin)))
        # Actual registry/template readback, not success wording alone.
        rows=self.spec['fixture_cells']
        self.probe(rows[0][:3],rows[0][3],'template_foundation')
        water=next(r for r in rows if r[3].startswith('minecraft:water'))
        wet=next(r for r in rows if 'waterlogged=true' in r[3])
        self.probe(water[:3],water[3],'real_water_source')
        self.probe(wet[:3],wet[3],'waterlogged_micro_piece')
        self.report['completed'].append('place_template');self.save()

    def traverse(self,name,start,yaw,axis,target,minimum_y):
        u=self.s.username;self.set_hud(False);self.rc(f'gamemode survival {u}')
        self.teleport(self.xyz(self.d,start),yaw,0)
        self.s.game.require_ingame('courtyard mapped walk')
        positions=[];begin=time.monotonic();world_target=self.d['site']['world_min'][axis]+target
        increasing=target>start[axis]
        self.s.game.xdo('keydown','w')
        try:
            while time.monotonic()-begin<15:
                p=self.player_pos();positions.append(p)
                if (p[axis]>=world_target if increasing else p[axis]<=world_target):break
                time.sleep(.15)
        finally:self.s.game.xdo('keyup','w')
        p=self.player_pos();crossed=p[axis]>=world_target if increasing else p[axis]<=world_target
        self.check('mapped_'+name,crossed and p[1]>=self.d['site']['world_min'][1]+minimum_y-.15,
                   {'start':start,'end':p,'target':world_target,'positions':positions,'input':'physical W, autoJump=false; no teleport during traversal'})
        self.s.game.shot(self.out/'courtyard'/f'walk-{name}.png',max_wait=3,settle=.5)

    def placement(self):
        u=self.s.username;self.set_hud(False);self.rc(f'gamemode survival {u}')
        catalog=json.loads((ROOT/'src/main/resources/data/myvillage/courtyard/pieces.json').read_text())['pieces']
        identity=next(p['id'] for p in catalog if p['family']=='timber' and not p['full'] and len(p['boxes'])>2)
        self.rc('fill 54 -60 6 60 -59 13 minecraft:smooth_stone')
        self.rc('fill 54 -58 6 60 -54 13 minecraft:air')
        self.rc(f'clear {u}');self.rc(f'item replace entity {u} hotbar.0 with myvillage:{identity} 3')
        pos=[57.5,-58,11.2];target=[57.5,-58.01,8.5]
        yaw,pitch=aim(pos,target);self.teleport(pos,yaw,pitch)
        self.s.game.xdo('key','1');self.s.game.shot(self.out/'courtyard'/'piece-before-place.png',max_wait=2,settle=.6)
        self.s.game.require_ingame('mapped building piece placement');self.s.game.xdo('click','3')
        reply=self.poll(lambda:self.rc(f'execute if block 57 -58 8 myvillage:{identity}'),lambda s:'passed' in s,8,'piece placed by right click')
        count=read_data(self.rc(f'data get entity {u} Inventory[{{Slot:0b}}].count'))
        self.check('mapped_piece_placement',count==2,{'id':identity,'remaining':count,'world_readback':reply,'input':'physical right mouse'})
        self.s.game.shot(self.out/'courtyard'/'piece-placed.png',max_wait=3,settle=.6)
        self.report['completed'].append('mapped_block_placement');self.save()

    def screenshots(self):
        for name in ['overview','reference','gate','corridor','alcove']:
            v=self.spec['viewpoints'][name];eye=v['eye'];target=v['target']
            # The authored preview is Y-up, quarter metres, with mirrored X.
            ep=[(192-eye[0])/4,eye[1]/4,eye[2]/4]
            tp=[(192-target[0])/4,target[1]/4,target[2]/4]
            if name in ('overview','reference'):
                ep=[tp[i]+.72*(ep[i]-tp[i]) for i in range(3)]
            ep[1]-=1.62
            self.photograph(self.d,{'name':name,'pos':ep,'target':tp})
        self.report['completed'].append('game_screenshots');self.save()

    def strict_export(self):
        self.save_building(self.d)
        path=self.out/'courtyard'/'reference_courtyard_game.nbt'
        _,root=read_typed(path);size,actual=saved_cells(root)
        expected_size,_,expected,_=nbt.expand_structure(self.d)
        mismatches=[]
        for pos,state in expected.items():
            if nbt.parse_block_state(state)!=nbt.parse_block_state(actual[pos]):
                mismatches.append({'pos':pos,'expected':state,'actual':actual[pos]})
        self.check('strict_game_saved_states',size==expected_size and not mismatches,
                   {'cells':len(expected),'mismatches':mismatches[:20],'compares':'IDs + facing + mirrored + waterlogged + source water levels; includes air'})
        self.report['completed'].append('native_structure_save');self.save()

    def restart_probe(self):
        self.set_hud(False);self.rc('save-all flush');self.stop()
        Evidence.start(self,reuse=True);self.hud_hidden=False
        samples=[]
        rows=self.spec['fixture_cells']
        for predicate in [lambda s:'mirrored=true' in s,lambda s:'waterlogged=true' in s,lambda s:s.startswith('minecraft:water'),lambda s:'facing=west' in s]:
            row=next(r for r in rows if predicate(r[3]));samples.append(row)
        for i,row in enumerate(samples):self.probe(row[:3],row[3],f'restart_state_{i}')
        self.report['completed'].append('restart_readback');self.save()

    def package(self):
        world=cs.SERVER_DIR/cs.LEVEL_NAME
        with zipfile.ZipFile(self.out/'courtyard_sample_world.zip','w',zipfile.ZIP_DEFLATED) as archive:
            for path in sorted(world.rglob('*')):
                rel=path.relative_to(world)
                if path.is_file() and not path.is_symlink() and not any(p in ('playerdata','stats','advancements','session.lock') for p in rel.parts):
                    archive.write(path,'Moon Gate Courtyard/'+str(rel))
        logs=[self.out/f'session-{self.sessions}-client.log',self.out/'session-1-client.log']
        errors=[]
        for path in logs:
            if not path.exists():continue
            for line in path.read_text(errors='replace').splitlines():
                if 'courtyard_' in line and any(s in line.lower() for s in ['unable to load','missing texture','exception','error']):errors.append(line)
        self.check('no_courtyard_model_load_errors',not errors,errors[:20])
        self.report['completed'].append('world_export');self.save()

    def run(self):
        if ROOT!=Path('/workspace/repo'):raise RuntimeError('Only the disposable Modal checkout may run this game job')
        (self.out/'courtyard').mkdir(exist_ok=True)
        self.start();self.setup()
        self.traverse('stairs',[33.5,3.0,9.7],0,2,18,4.5)
        self.traverse('moon_gate',[11.25,2.5,17.5],0,2,22,2.5)
        self.traverse('gallery',[23.5,2.75,27],90,0,6.5,2.75)
        self.traverse('side_room',[15,2.75,28.2],0,2,33,2.75)
        self.report['completed'].append('mapped_walks');self.save()
        self.placement();self.screenshots();self.strict_export();self.restart_probe()
        self.rc(f'gamemode creative {self.s.username}');self.teleport([45,-57,2])
        self.rc('save-all flush');self.stop();self.package()
        self.report['unrun']=[s for s in self.report['selected'] if s not in self.report['completed']]
        self.check('all_selected_completed',not self.report['unrun'],self.report['completed'])
        self.report['status']='pass';self.save()


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--out',type=Path,default=ROOT/'out/preview/reference-courtyard-mc/runtime');args=parser.parse_args()
    e=CourtyardEvidence(args.out)
    try:e.run()
    except BaseException as error:
        e.report['status']='fail';e.report['failures'].append(repr(error));e.report['unrun']=[s for s in e.report['selected'] if s not in e.report['completed']];e.save();raise
    finally:
        if e.s:
            try:e.s.game.xdo('keyup','w')
            finally:e.stop()
        e.save()


if __name__=='__main__':main()
