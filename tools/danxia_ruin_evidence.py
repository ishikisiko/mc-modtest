#!/usr/bin/env python3
"""Bounded cloud-only ruins evidence: natural worldgen, mapped use, reward and restart.

RCON prepares fixtures and reads state. It never invokes the interaction method.
The player's machine is not involved. Owner aesthetics/combat balance stay unverified.
"""
from __future__ import annotations
import argparse
import io
import json
import math
from pathlib import Path
import re
import secrets
import struct
import sys
import time
from types import SimpleNamespace
import zlib

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from tools.combat_capture import session as cs, scene, procs
from tools.combat_capture.rcon import Rcon
from tools.realm_stats_evidence import Evidence
from tools.alchemy_quality_evidence import read_data, SecondClient
from tools.buildgen.nbtread import _read_utf, _read_payload, TAG_COMPOUND

RULES=json.loads((ROOT/'src/main/resources/data/myvillage/ruins/danxia.json').read_text())


def saved_cores(region_dir, center, radius=4, block_entity_id="myvillage:ruin_core"):
    """Read only flushed chunks around the located structure; no world mutation or broad scan."""
    result=[]; cx,cz=center[0]//16,center[1]//16
    for x in range(cx-radius,cx+radius+1):
        for z in range(cz-radius,cz+radius+1):
            path=region_dir/f'r.{x//32}.{z//32}.mca'
            if not path.exists(): continue
            with path.open('rb') as f:
                f.seek(4*((x%32)+(z%32)*32)); header=f.read(4)
                if len(header)!=4: continue
                offset=int.from_bytes(header[:3],'big')
                if not offset: continue
                f.seek(offset*4096); size=int.from_bytes(f.read(4),'big'); kind=f.read(1)
                if kind!=b'\x02': raise ValueError(f'Unsupported region compression {kind}')
                data=io.BytesIO(zlib.decompress(f.read(size-1)))
                if data.read(1)!=b'\x0a': raise ValueError('Not a chunk compound')
                _read_utf(data); chunk=_read_payload(data,TAG_COMPOUND)
                for be in chunk.get('block_entities',[]):
                    if be.get('id')==block_entity_id: result.append(be)
    return result


class RuinEvidence(Evidence):
    def __init__(self,out):
        super().__init__(out)
        self.report.update(kind='danxia_ruin', natural_terrain=True,
                           selected=['natural_generation','mapped_garden','mapped_vault','modified_target','two_client_authority','repeat','restart'],
                           completed=[], appearance='not_verified',combat_balance='not_verified',multiplayer='not_verified')
        self.report.pop('natural_encounter_density',None)
        self.sites={}

    def start(self,reuse=False):
        t=time.monotonic()
        st=cs.start(cs.SessionConfig(session_id=secrets.token_hex(6),reuse_world=reuse,natural_terrain=True,
                                    natural_monsters=True,language='zh_cn',lock=str(procs.default_lock_path(ROOT))))
        self.s=cs.Session.attach(ui_check=True); self.sessions+=1
        self.report['phases'].append({'phase':'restart' if reuse else 'initial_setup','wall_s':round(time.monotonic()-t,3),'capture_timings':st.get('timings',{})})
        self.save()

    def must(self,name,passed,detail):
        if not self.check(name,passed,detail): raise RuntimeError(name)

    def rc(self,command):
        with Rcon('127.0.0.1',self.s.state['rcon_port'],self.s.state['rcon_password'],timeout=120) as r:
            reply=r.cmd(command)
        self.report['commands'].append({'command':command,'reply':reply}); self.save(); return reply

    def poll(self,name,fn,timeout=45):
        end=time.monotonic()+timeout; last=None
        while time.monotonic()<end:
            last=fn()
            if last: return last
            time.sleep(.5)
        raise RuntimeError(f'{name} timed out: {last}')

    @staticmethod
    def map(site,canonical):
        dx,dy,dz=[a-b for a,b in zip(canonical,RULES['core'])]
        for _ in range(site['turn']): dx,dz=-dz,dx
        return tuple(a+b for a,b in zip(site['core'],(dx,dy,dz)))

    @staticmethod
    def coords(p): return ' '.join(str(v) for v in p)

    def choice(self,site): return read_data(self.rc(f'data get block {self.coords(site["core"])} Choice'))

    def look(self,pos,target,mode='survival',game=None,user=None):
        user=user or self.s.username; game=game or self.s.game
        self.rc(f'gamemode {mode} {user}')
        # Teleport's default command anchor is feet; calculate the actual eye ray explicitly.
        dx,dy,dz=target[0]-pos[0],target[1]-(pos[1]+1.62),target[2]-pos[2]
        yaw=math.degrees(math.atan2(-dx,dz)); pitch=-math.degrees(math.atan2(dy,math.hypot(dx,dz)))
        self.rc(f'tp {user} {self.coords(pos)} {yaw} {pitch}')
        if mode!='spectator': game.key('1')
        time.sleep(.5)
        game.require_ingame('ruin mapped input')

    def approach(self,site):
        p=self.map(site,(20,3,27)); c=site['core']
        self.rc(f'clear {self.s.username}')
        self.look((p[0]+.5,p[1],p[2]+.5),(c[0]+.5,c[1]+.6,c[2]+.5))

    def click(self,sneak=False,game=None):
        game=game or self.s.game; game.require_ingame('ruin right click')
        if sneak: game.xdo('keydown','Shift_L'); time.sleep(.15)
        try: game.xdo('click','3')
        finally:
            if sneak: game.xdo('keyup','Shift_L')
        time.sleep(.3)

    def capture(self,name):
        self.s.game.key('F1')
        try: self.s.game.shot(self.out/name,max_wait=3)
        finally: self.s.game.key('F1')

    def natural(self):
        user=self.s.username
        for cmd in scene.world_rules(): self.rc(cmd)
        self.rc(f'op {user}'); self.rc(f'gamemode spectator {user}')
        reply=self.rc('execute positioned 0 100 0 run locate structure myvillage:danxia_ruin')
        match=re.search(r'\[(-?\d+), (?:~|-?\d+), (-?\d+)\]',reply)
        self.must('natural_locate',bool(match),reply)
        x,z=map(int,match.groups())
        self.rc(f'forceload add {x-48} {z-48} {x+48} {z+48}')
        self.rc(f'tp {user} {x} 120 {z}')
        region=cs.SERVER_DIR/cs.LEVEL_NAME/'region'
        def find():
            self.rc('save-all flush')
            cores=saved_cores(region,(x,z))
            return min(cores,key=lambda b:(b['x']-x)**2+(b['z']-z)**2) if cores else None
        be=self.poll('natural core in flushed chunks',find,90)
        core=(be['x'],be['y'],be['z']); turn=None
        for i,face in enumerate(['north','east','south','west']):
            reply=self.rc(f'execute if block {self.coords(core)} myvillage:ruin_core[facing={face}] run data get block {self.coords(core)} Choice')
            if 'CHARGED' in reply: turn=i; break
        self.must('natural_charged_core',turn is not None,{'be':be,'turn':turn})
        site={'core':core,'turn':turn}; self.sites['natural']=site
        self.report['sites']=self.sites; self.save()
        self.rc('execute as @e[tag=danxia_guardian] run data merge entity @s {NoAI:1b}')
        count=self.rc(f'execute positioned {self.coords(core)} if entity @e[tag=danxia_guardian,distance=..80]')
        self.must('natural_guardians_present','count: 2' in count,count)
        for index in range(len(RULES['guardians'])):
            selector=f'@e[tag=danxia_guardian,nbt={{NeoForgeData:{{"myvillage:ruin_guardian":{{index:{index}}}}}}},limit=1]'
            data=read_data(self.rc(f'data get entity {selector}'))
            marker=data.get('NeoForgeData',{}).get('myvillage:ruin_guardian',{})
            realm=data.get('NeoForgeData',{}).get('myvillage:stats_realm',{})
            self.must(f'guardian_{index}_initialized_once',marker.get('pending')==0 and realm.get('realm')==RULES['guardians'][index]['realm'],{'marker':marker,'realm':realm})
        for point,block in [((5,10,15),'minecraft:mossy_stone_bricks'),((20,3,29),'myvillage:ruin_core'),
                            ((29,3,34),'minecraft:barrel'),((31,10,18),'minecraft:barrel')]:
            pos=self.map(site,point)
            reply=self.rc(f'execute if block {self.coords(pos)} {block} run data get block {self.coords(core)} Choice')
            self.must(f'cross_chunk_landmark_{point}','CHARGED' in reply,{'pos':pos,'reply':reply})
        for name,pos,target in [('natural-overview.png',(0,29,1),(20,10,25)),
                                ('natural-entry.png',(20,12,0),(20,12,25)),
                                ('natural-chamber.png',(21,4,26),(22,4,33))]:
            p=self.map(site,pos); t=self.map(site,target)
            self.look((p[0]+.5,p[1],p[2]+.5),(t[0]+.5,t[1],t[2]+.5),'spectator'); self.capture(name)
        self.report['completed'].append('natural_generation'); self.save()
        return site

    def garden(self,site):
        self.approach(site)
        self.s.game.shot(self.out/'core-before-use.png',max_wait=3)
        self.click(); self.must('first_click_only_previews',self.choice(site)=='CHARGED',self.choice(site))
        self.poll('first mapped preview reaches client',lambda:'残泉仅余一次' in self.s.game.client_log.read_text(),5)
        self.click(); self.must('mapped_garden_commit',self.choice(site)=='GARDEN',self.choice(site))
        for plant in RULES['garden']:
            p=self.map(site,plant['pos'])
            reply=self.rc(f'execute if block {self.coords(p)} {plant["crop"]}[age=2] if block {p[0]} {p[1]-1} {p[2]} myvillage:spirit_soil run data get block {self.coords(site["core"])} Choice')
            self.must('mature_'+str(plant['pos']),'GARDEN' in reply,reply)
        vault=self.map(site,RULES['vault'])
        self.must('garden_does_not_grant_archive',read_data(self.rc(f'data get block {self.coords(vault)} Items'))==[],str(vault))
        self.click(True); self.click(True)
        self.must('spent_core_rejects_other_branch',self.choice(site)=='GARDEN',self.choice(site))
        p=self.map(site,(10,12,15)); t=self.map(site,(10,10,23))
        self.look((p[0]+.5,p[1],p[2]+.5),(t[0]+.5,t[1],t[2]+.5),'spectator'); self.capture('garden-restored.png')
        self.report['completed']+=['mapped_garden','repeat']; self.save()

    def vault_fixture(self):
        # Separate rotated admin fixture isolates the alternate branch without resetting the natural site.
        base=(256,100,0); self.rc('forceload add 208 -48 320 80')
        self.rc(f'tp {self.s.username} 260 130 0')
        self.rc('place template myvillage:danxia_ruin 256 100 0 clockwise_90 none')
        # clockwise_90: block transform (x,z)->(-z,x).
        cx,cy,cz=RULES['core']; site={'core':(base[0]-cz,base[1]+cy,base[2]+cx),'turn':1}
        self.sites['vault']=site; self.report['sites']=self.sites; self.save()
        self.poll('rotated fixture charged',lambda: self.choice(site)=='CHARGED')
        self.rc('execute as @e[tag=danxia_guardian] run data merge entity @s {NoAI:1b}')
        v=self.map(site,RULES['vault'])
        self.rc(f'item replace block {self.coords(v)} container.0 with minecraft:stone')
        self.approach(site); self.click(True); self.click(True)
        self.must('occupied_archive_preserves_charge',self.choice(site)=='CHARGED',self.choice(site))
        self.must('occupied_archive_preserves_items',read_data(self.rc(f'data get block {self.coords(v)} Items'))[0]['id']=='minecraft:stone',str(v))
        self.rc(f'item replace block {self.coords(v)} container.0 with minecraft:air')
        adapter=SimpleNamespace(out=self.out,s=self.s,report=self.report,rc=self.rc,check=self.check,
            poll=lambda fn,predicate,timeout,name:self.poll(name,lambda:predicate(fn()),timeout))
        with SecondClient(adapter) as guest:
            self.rc(f'op {guest.username}'); self.rc(f'clear {guest.username}')
            p=self.map(site,(22,3,29)); c=site['core']
            self.look((p[0]+.5,p[1],p[2]+.5),(c[0]+.5,c[1]+.6,c[2]+.5),game=guest.game,user=guest.username)
            self.click(True)
            self.click(True,guest.game)
            self.must('another_player_cannot_confirm_first_players_preview',self.choice(site)=='CHARGED',self.choice(site))
            self.click(True,guest.game)
            self.must('guest_commits_shared_choice',self.choice(site)=='VAULT',self.choice(site))
            self.click(); self.click()
            self.must('first_player_cannot_spend_shared_charge_again',self.choice(site)=='VAULT',self.choice(site))
        self.report['multiplayer']='two_real_clients_mapped_authority_observed; owner_acceptance_not_verified'
        self.report['completed'].append('two_client_authority'); self.save()
        self.must('mapped_rotated_vault_commit',self.choice(site)=='VAULT',self.choice(site))
        items=read_data(self.rc(f'data get block {self.coords(v)} Items'))
        self.must('archive_contains_readable_manual',any(i.get('components',{}).get('myvillage:technique')==RULES['manual'] for i in items),items)
        self.must('archive_contains_formula',any(i.get('id')=='myvillage:formula_ningshen' for i in items),items)
        # Open the actual barrel and shift-click its manual into the real player's inventory.
        p=(v[0]+.5,v[1],v[2]-1.5)
        self.look(p,(v[0]+.5,v[1]+.5,v[2]+.5))
        self.click()
        self.poll('barrel menu',lambda:self.s.game.pointer_grabbed() is False,5)
        self.s.game.save_png(self.s.game.grab_raw(),self.out/'archive-menu.png')
        # Vanilla 3-row chest, GUI scale 2 at 960x540: origin (304,104), slot 0 center (336,156).
        self.s.game.xdo('mousemove','336','156'); self.s.game.xdo('keydown','Shift_L')
        try: self.s.game.xdo('click','1')
        finally: self.s.game.xdo('keyup','Shift_L')
        time.sleep(.4); self.s.game.xdo('key','Escape'); self.s.game.require_ingame('close archive')
        inv=read_data(self.rc(f'data get entity {self.s.username} Inventory'))
        self.must('mapped_manual_pickup',any(i.get('components',{}).get('myvillage:technique')==RULES['manual'] for i in inv),inv)
        self.report['completed']+=['mapped_vault','modified_target']; self.save()
        return site

    def restart_checks(self):
        self.rc('tag @e[tag=danxia_guardian,sort=nearest,limit=1] add danxia_persist_guard')
        self.rc('damage @e[tag=danxia_persist_guard,limit=1] 1 minecraft:generic')
        guard_before=read_data(self.rc('data get entity @e[tag=danxia_persist_guard,limit=1] Health'))
        self.rc('save-all flush'); self.stop(); self.start(reuse=True)
        inv=read_data(self.rc(f'data get entity {self.s.username} Inventory'))
        self.must('picked_up_manual_saved',any(i.get('components',{}).get('myvillage:technique')==RULES['manual'] for i in inv),inv)
        guard_after=read_data(self.rc('data get entity @e[tag=danxia_persist_guard,limit=1] Health'))
        self.must('guardian_does_not_heal_on_reload',guard_before==guard_after,{'before':guard_before,'after':guard_after})
        for name,site in self.sites.items():
            self.approach(site)
            expected='GARDEN' if name=='natural' else 'VAULT'
            self.must(name+'_saved_choice',self.choice(site)==expected,self.choice(site))
            self.click(); self.click(True)
            self.must(name+'_no_refill',self.choice(site)==expected,self.choice(site))
        vault=self.map(self.sites['vault'],RULES['vault'])
        items=read_data(self.rc(f'data get block {self.coords(vault)} Items'))
        self.must('looted_manual_does_not_respawn',not any(i.get('components',{}).get('myvillage:technique')==RULES['manual'] for i in items),items)
        self.report['completed'].append('restart'); self.save()


def main():
    ap=argparse.ArgumentParser(description=__doc__); ap.add_argument('--out',type=Path,default=ROOT/'out/preview/danxia_runtime'); args=ap.parse_args()
    e=RuinEvidence(args.out); code=1
    try:
        e.start(); natural=e.natural(); e.garden(natural); e.vault_fixture(); e.restart_checks()
        e.report['status']='pass'; code=0
    except BaseException as exc:
        e.report['status']='fail'; e.report['failures'].append(repr(exc)); e.save(); raise
    finally:
        if e.s: e.stop()
        e.save()
    return code

if __name__=='__main__': raise SystemExit(main())
