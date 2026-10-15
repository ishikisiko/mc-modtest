#!/usr/bin/env python3
"""Cloud-only combat ruins evidence. Setup is administrative; attacks and menus use mapped input.

Frozen enemies and enhanced player strength isolate authority checks, not combat balance.
No direct calls to the interaction/completion/reward methods are used.
"""
from __future__ import annotations
import argparse
import json
import re
import sys
import time
import uuid
from pathlib import Path
from types import SimpleNamespace
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from tools.danxia_ruin_evidence import RuinEvidence, saved_cores
from tools.alchemy_quality_evidence import read_data, SecondClient
from tools.combat_capture import scene, session as cs

RULES={name:json.loads((ROOT/f'src/main/resources/data/myvillage/ruins/{name}.json').read_text()) for name in ('duange_ruin','fuyao_ruin')}

def entity_uuid(values):
    return str(uuid.UUID(int=sum((v & 0xffffffff) << (32*(3-i)) for i,v in enumerate(values))))

class BattleEvidence(RuinEvidence):
    def __init__(self,out):
        super().__init__(out)
        self.report.update(kind='combat_ruins',selected=['natural_generation','locked_cache','route','arena','peaceful','unloaded_cache_death','partial_restart','mapped_rewards','two_client_authority','looted_restart','rotated_fixture'],completed=[],combat_balance='not_verified',appearance='not_verified')
        self.report['combat_fixture']='Resistance/strength effects and NoAI freeze isolate mapped hit/death authority; not a balance test.'
    @staticmethod
    def map(site,p):
        dx,dy,dz=[a-b for a,b in zip(p,RULES[site['kind']]['cache'])]
        for _ in range(site['turn']):dx,dz=-dz,dx
        return tuple(a+b for a,b in zip(site['core'],(dx,dy,dz)))
    def data(self,site):return read_data(self.rc(f'data get block {self.coords(site["core"])}'))
    def state(self,site):return self.data(site).get('BattleState')
    def guard_ids(self,site):return [entity_uuid(g['Id']) for g in self.data(site).get('Guards',[]) if 'Id' in g and not g.get('Dead')]
    def loaded(self,site):return 'passed' in self.rc(f'execute if loaded {self.coords(site["core"])}')
    def approach_cache(self,site,game=None,user=None):
        x,y,z=RULES[site['kind']]['cache'];p=self.map(site,(x,y,z-2));c=site['core']
        self.look((p[0]+.5,p[1],p[2]+.5),(c[0]+.5,c[1]+.6,c[2]+.5),game=game,user=user)
    def trigger(self,site,wave):
        p=self.map(site,RULES[site['kind']]['waves'][wave]['trigger']);c=site['core']
        self.look((p[0]+.5,p[1],p[2]+.5),(c[0]+.5,c[1]+.5,c[2]+.5))
    def setup_player(self):
        user=self.s.username
        self.rc(f'op {user}');self.rc(f'effect give {user} minecraft:resistance infinite 4 true');self.rc(f'effect give {user} minecraft:strength infinite 30 true')
        self.rc(f'item replace entity {user} hotbar.0 with minecraft:netherite_sword');self.s.game.key('1')
    def locate(self,kind):
        self.rc(f'gamemode spectator {self.s.username}')
        reply=self.rc(f'locate structure myvillage:{kind}')
        match=re.search(r'\[(-?\d+),\s*(?:~|-?\d+),\s*(-?\d+)\]',reply)
        self.must(kind+'_natural_locate',bool(match),reply);x,z=map(int,match.groups())
        bounds=(x-64,z-64,x+64,z+64);self.rc('forceload add '+' '.join(map(str,bounds)))
        self.rc(f'tp {self.s.username} {x} 120 {z}')
        def find():
            self.rc('save-all flush')
            rows=saved_cores(cs.SERVER_DIR/cs.LEVEL_NAME/'region',(x,z),6,'myvillage:battle_cache')
            rows=[row for row in rows if row.get('Kind')==kind]
            return min(rows,key=lambda b:(b['x']-x)**2+(b['z']-z)**2) if rows else None
        be=self.poll(kind+' natural cache',find,90);core=(be['x'],be['y'],be['z']);turn=None
        for i,face in enumerate(['north','east','south','west']):
            reply=self.rc(f'execute if block {self.coords(core)} myvillage:battle_cache[facing={face}] run data get block {self.coords(core)} BattleState')
            if 'READY' in reply:turn=i;break
        self.must(kind+'_natural_saved_ready',turn is not None,be)
        site={'core':core,'turn':turn,'kind':kind,'bounds':bounds};self.sites[kind]=site;self.report['sites']=self.sites;self.save()
        views=([(4,27,4),(21,6,4),(21,8,43)] if kind=='duange_ruin' else [(3,31,4),(22,13,3),(22,6,24)])
        aims=([(21,7,32),(21,7,20),(21,9,51)] if kind=='duange_ruin' else [(22,11,28),(22,12,12),(22,7,38)])
        for label,p,t in zip(['overview','entry','arena'],views,aims):
            pp=self.map(site,p);tt=self.map(site,t);self.look((pp[0]+.5,pp[1],pp[2]+.5),(tt[0]+.5,tt[1],tt[2]+.5),'spectator');self.capture(kind+'-'+label+'.png')
        return site
    def locked(self,site):
        # Main-hand item interaction must reach the seal too.
        self.approach_cache(site);self.click();self.must(site['kind']+'_locked_no_menu',self.s.game.pointer_grabbed(),self.state(site))
        self.must(site['kind']+'_locked_no_items',not self.data(site).get('Items'),self.data(site))
        c=site['core'];hopper=(c[0],c[1]-1,c[2]);self.rc(f'setblock {self.coords(hopper)} minecraft:hopper')
        time.sleep(.6);self.must(site['kind']+'_hopper_cannot_bypass',read_data(self.rc(f'data get block {self.coords(hopper)} Items'))==[],str(hopper))
        self.s.game.xdo('mousedown','1');time.sleep(.7);self.s.game.xdo('mouseup','1')
        self.must(site['kind']+'_survival_mining_cannot_bypass',self.state(site) in ('READY','ACTIVE'),self.state(site))
    def wait_guards(self,site,wave):
        expected=len(RULES[site['kind']]['waves'][wave]['guards'])
        ids=self.poll(site['kind']+f' wave {wave} bound',lambda: (ids if len(ids:=self.guard_ids(site))==expected else None),25)
        for entity in ids:self.rc(f'data merge entity {entity} {{NoAI:1b}}')
        self.must(site['kind']+f'_wave_{wave}_spawned',len(ids)==expected,ids)
        return ids
    def attack(self,entity):
        values=read_data(self.rc(f'data get entity {entity} Pos'));x,y,z=values
        self.look((x,y,z-1.7),(x,y+.8,z))
        for attempt in range(8):
            self.s.game.xdo('click','1');time.sleep(.65)
            reply=self.rc(f'data get entity {entity} Health')
            if 'data: ' not in reply or read_data(reply)<=0:break
        else:raise RuntimeError('Mapped attack did not defeat '+entity)
        self.must('mapped_guard_defeat_'+entity,True,{'attempts':attempt+1,'last':reply})
    def wave(self,site,index):
        self.trigger(site,index);ids=self.wait_guards(site,index)
        for entity in ids:self.attack(entity)
        self.poll(site['kind']+' stage completion',lambda:self.data(site).get('Wave')==index+1,15)
    def unload_death(self,site,entity):
        self.rc(f'gamemode spectator {self.s.username}');self.rc('forceload add 1200 1200 1215 1215')
        self.rc('fill 1200 99 1200 1208 99 1208 minecraft:stone')
        self.rc(f'tp {self.s.username} 1204 102 1204');self.rc(f'tp {entity} 1204 100 1204')
        self.rc('forceload remove '+' '.join(map(str,site['bounds'])))
        self.poll('cache actually unloaded',lambda:not self.loaded(site),40)
        # This single remote death uses a command, explicitly separate from mapped combat.
        self.rc(f'kill {entity}')
        self.rc('forceload add '+' '.join(map(str,site['bounds'])))
        self.poll('late death receipt applied',lambda:self.loaded(site) and self.data(site).get('Wave')==1,45)
        self.must('unloaded_cache_received_bound_death',self.data(site)['Wave']==1,self.data(site));self.report['completed'].append('unloaded_cache_death');self.save()
    def partial_restart(self,camp,temple,alive):
        self.rc(f'damage {alive} 1 minecraft:generic');before=read_data(self.rc(f'data get entity {alive} Health'));saved=self.data(camp)
        self.rc('save-all flush');self.stop();self.start(reuse=True);self.setup_player()
        self.poll('saved camp loaded',lambda:self.loaded(camp))
        after=self.data(camp);self.must('partial_wave_survives_restart',after['Wave']==saved['Wave'] and after['Guards']==saved['Guards'],{'before':saved,'after':after})
        health=read_data(self.rc(f'data get entity {alive} Health'));self.must('living_guard_not_healed',health==before,{'before':before,'after':health})
        self.must('unstarted_arena_stays_ready',self.state(temple)=='READY',self.state(temple));self.report['completed'].append('partial_restart');self.save()
    def loot(self,site):
        self.poll('reward committed',lambda:self.state(site)=='REWARDED')
        items=self.data(site)['Items'];manual=RULES[site['kind']]['manual']
        self.must(site['kind']+'_reward_manual',sum(i.get('components',{}).get('myvillage:technique')==manual for i in items)==1,items)
        c=site['core'];self.must(site['kind']+'_unlocked_light','passed' in self.rc(f'execute if block {self.coords(c)} myvillage:battle_cache[lit=true]'),str(c))
        self.approach_cache(site);self.click();self.poll('actual cache menu',lambda:self.s.game.pointer_grabbed() is False,8)
        self.s.game.save_png(self.s.game.grab_raw(),self.out/(site['kind']+'-cache.png'))
        self.s.game.xdo('mousemove','336','156');self.s.game.xdo('keydown','Shift_L')
        try:self.s.game.xdo('click','1')
        finally:self.s.game.xdo('keyup','Shift_L')
        time.sleep(.4);self.s.game.xdo('key','Escape');self.s.game.require_ingame('cache closed')
        inv=read_data(self.rc(f'data get entity {self.s.username} Inventory'))
        self.must(site['kind']+'_mapped_manual_pickup',any(i.get('components',{}).get('myvillage:technique')==manual for i in inv),inv)
    def arena(self,temple):
        self.locked(temple) # first mapped use activates, still cannot open or extract
        self.must('arena_started_by_mapped_use',self.state(temple)=='ACTIVE',self.state(temple))
        ids=self.wait_guards(temple,0);self.rc('difficulty peaceful')
        self.poll('peaceful removes living guard entities',lambda:all('data: ' not in self.rc(f'data get entity {e} Health') for e in ids),15)
        self.must('peaceful_does_not_clear',self.data(temple)['Wave']==0 and not self.data(temple).get('Items'),self.data(temple))
        self.rc('difficulty normal');self.trigger(temple,0);replacement=self.wait_guards(temple,0)
        self.must('peaceful_guard_slots_can_resume',set(ids).isdisjoint(replacement),replacement);self.report['completed'].append('peaceful')
        self.attack(replacement[0])
        # Actual second player cannot restart the shared active encounter or open its cache.
        adapter=SimpleNamespace(out=self.out,s=self.s,report=self.report,rc=self.rc,check=self.check,poll=lambda fn,predicate,timeout,name:self.poll(name,lambda:predicate(fn()),timeout))
        with SecondClient(adapter) as guest:
            self.rc(f'op {guest.username}');self.rc(f'effect give {guest.username} minecraft:resistance infinite 4 true')
            before=self.data(temple);self.approach_cache(temple,guest.game,guest.username);self.click(game=guest.game)
            self.must('guest_cannot_reset_or_open_active_cache',self.data(temple)['Guards']==before['Guards'] and guest.game.pointer_grabbed(),self.data(temple))
            self.attack(replacement[1]);self.poll('arena first wave clear',lambda:self.data(temple)['Wave']==1)
            self.wave(temple,1);self.wave(temple,2);self.loot(temple)
            self.approach_cache(temple,guest.game,guest.username);self.click(game=guest.game);self.poll('guest shared menu',lambda:guest.game.pointer_grabbed() is False,8)
            self.must('guest_sees_already_looted_shared_inventory',not any(i.get('components',{}).get('myvillage:technique')==RULES[temple['kind']]['manual'] for i in self.data(temple)['Items']),self.data(temple)['Items'])
            guest.game.xdo('key','Escape');guest.game.require_ingame('guest cache closed')
        self.report['completed']+=['arena','two_client_authority'];self.save()
    def final_restart(self):
        expected={name:self.data(site)['Items'] for name,site in self.sites.items()}
        self.rc('save-all flush');self.stop();self.start(reuse=True)
        for name,site in self.sites.items():
            self.poll(name+' loaded',lambda:self.loaded(site));self.must(name+'_looted_inventory_persists',self.state(site)=='REWARDED' and self.data(site)['Items']==expected[name],self.data(site))
            self.approach_cache(site);self.click();self.poll('reopened saved cache',lambda:self.s.game.pointer_grabbed() is False,8)
            self.s.game.xdo('key','Escape');self.s.game.require_ingame('saved cache closed')
            self.must(name+'_reopen_does_not_refill',self.data(site)['Items']==expected[name],self.data(site)['Items'])
        inv=read_data(self.rc(f'data get entity {self.s.username} Inventory'))
        for r in RULES.values():self.must(r['id']+'_looted_manual_saved',sum(i.get('components',{}).get('myvillage:technique')==r['manual'] for i in inv)==1,inv)
        self.report['completed'].append('looted_restart');self.save()
    def rotated(self):
        self.rc('forceload add 208 -16 304 64');self.rc(f'gamemode spectator {self.s.username}');self.rc(f'tp {self.s.username} 256 140 20')
        self.rc('place template myvillage:fuyao_ruin 256 100 0 clockwise_90 none')
        r=RULES['fuyao_ruin'];x,y,z=r['cache'];site={'kind':'fuyao_ruin','core':(256-z,100+y,x),'turn':1}
        self.poll('rotated template ready',lambda:self.state(site)=='READY');self.setup_player();self.approach_cache(site);self.click();self.trigger(site,0);ids=self.wait_guards(site,0)
        for i,entity in enumerate(ids):
            actual=read_data(self.rc(f'data get entity {entity} Pos'));target=self.map(site,r['waves'][0]['guards'][i]['pos'])
            # AI may move before freeze; prove the spawn remains in the rotated chamber.
            self.must('rotated_guard_in_chamber_'+str(i),sum((a-b)**2 for a,b in zip(actual,target))<100,{'pos':actual,'target':target})
        self.report['completed'].append('rotated_fixture');self.save()
    def run(self):
        self.start()
        for command in scene.world_rules():self.rc(command)
        self.rc('difficulty normal');self.rc('gamerule doMobSpawning false')
        camp=self.locate('duange_ruin');temple=self.locate('fuyao_ruin');self.report['completed'].append('natural_generation');self.setup_player()
        self.locked(camp);self.must('route_cache_cannot_skip_waves',self.state(camp)=='READY',self.state(camp));self.report['completed'].append('locked_cache')
        # Obstruct one authored spawn site: no overwrite and no implicit defeat.
        spawn=self.map(camp,RULES['duange_ruin']['waves'][0]['guards'][0]['pos']);self.rc(f'setblock {self.coords(spawn)} minecraft:stone');self.trigger(camp,0)
        self.poll('unobstructed guard bound',lambda:len(self.guard_ids(camp))==1)
        self.must('blocked_spawn_preserves_player_block','passed' in self.rc(f'execute if block {self.coords(spawn)} minecraft:stone'),self.data(camp))
        self.must('blocked_spawn_does_not_count_as_defeat',self.data(camp)['Wave']==0 and not self.data(camp).get('Items'),self.data(camp))
        self.rc(f'setblock {self.coords(spawn)} minecraft:air');ids=self.wait_guards(camp,0);self.attack(ids[0]);self.unload_death(camp,ids[1])
        self.trigger(camp,1);ids=self.wait_guards(camp,1);self.attack(ids[0]);self.partial_restart(camp,temple,ids[1])
        self.setup_player()
        for entity in ids[1:]:self.attack(entity)
        self.poll('camp second wave clear',lambda:self.data(camp)['Wave']==2);self.wave(camp,2);self.loot(camp);self.report['completed'].append('route')
        self.arena(temple);self.report['completed'].append('mapped_rewards');self.final_restart();self.rotated()


def main():
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--out',type=Path,default=ROOT/'out/preview/combat_ruins_runtime');args=ap.parse_args();e=BattleEvidence(args.out)
    try:e.run();e.report['status']='pass'
    except BaseException as exc:e.report['status']='fail';e.report['failures'].append(repr(exc));e.save();raise
    finally:
        if e.s:e.stop()
        e.save()
    return 0
if __name__=='__main__':raise SystemExit(main())
