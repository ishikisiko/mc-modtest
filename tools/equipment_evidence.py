#!/usr/bin/env python3
"""Scoped Colab armor evidence: actual equip/unequip, movement, two default skins and F3+T.

RCON only supplies inventory/scene fixtures and reads vanilla state. Pose images
are developer captures; neither mapped input nor a successful build is an owner
appearance verdict. The Colab VM is deliberately retained by the calling workflow.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import secrets
import sys
import time
import uuid

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from tools.cultivation_demo_evidence import DemoEvidence, status_json, menu_point
from tools.combat_capture import procs, scene, session as cs

CASES=('equip','poses','reload','profiles')
ARMOR=(('helmet',103),('chestplate',102),('leggings',101),('boots',100))


def default_profile(username):
    raw=bytearray(hashlib.md5(('OfflinePlayer:'+username).encode()).digest())
    raw[6]=(raw[6]&15)|48;raw[8]=(raw[8]&63)|128
    identity=uuid.UUID(bytes=bytes(raw))
    words=[int.from_bytes(raw[i:i+4],'big') for i in range(0,16,4)]
    value=words[0]^words[1]^words[2]^words[3]
    if value>=2**31:value-=2**32
    return {'uuid':str(identity),'default_skin_index':value%18,'profile':'slim' if value%18<9 else 'standard'}


class EquipmentEvidence(DemoEvidence):
    def verify_source(self):
        super().verify_source()
        self.report['input_driver_sha256']=self.report['collector_sha256']
        self.report['collector_sha256']=hashlib.sha256(Path(__file__).read_bytes()).hexdigest()

    def __init__(self,out,cases):
        super().__init__(out,cases)
        self.username='DemoTester'
        self.report.update(kind='equipment_authoring',method=__doc__,collector_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                           multiplayer='not_verified',profiles=[],visual_checks='captured_for_review')
        self.save()

    def start(self,reuse=False):
        cs.merge_options(cs.CLIENT_DIR/'options.txt', {'rawMouseInput':'false','maxFps':'30','fov':'0.0',
            'key_key.use':'key.mouse.right','key_key.togglePerspective':'key.keyboard.f5','hideGui':'false'})
        state=cs.start(cs.SessionConfig(session_id=secrets.token_hex(6),username=self.username,language='zh_cn',
            reuse_world=reuse,prebuilt_resources=True,lock=str(procs.default_lock_path(ROOT)),
            gradle_jvmargs='-Xmx2g',view_distance=3,world_seed=20261027))
        self.s=cs.Session(state,ui_check=True);self.sessions+=1
        self.report['phases'].append({'phase':'start','timings':state.get('timings'),'session_id':state['session_id'],'username':self.username})
        self.attach_window();self.poll(self.s.game.pointer_grabbed,lambda v:v is True,label='game input captured')
        self.poll(lambda:status_json(self.rc('myvillage qianzhen realm_status '+self.username),'QIANZHEN_REALM_STATUS '),
                  lambda s:not s['building'] and not s['waiting'],150,'new player opening setup completes')
        for command in (*scene.world_rules(),'myvillage world pause',f'op {self.username}',f'gamemode survival {self.username}',
                        f'execute in minecraft:overworld run tp {self.username} .5 -60 .5 0 0',
                        'forceload add -16 -16 16 16','fill -12 -61 -12 12 -61 12 minecraft:smooth_stone',
                        f'clear {self.username}',f'effect clear {self.username}',
                        f'effect give {self.username} minecraft:saturation infinite 255 true'):
            self.rc(command)
        self.poll(lambda:self.pos(),lambda p:abs(p[1]+60)<.05,label='fixture player settled')
        # Server coordinates settle before the client's dimension-loading screen closes.
        # Do not send inventory/equip input until the real game has reacquired its pointer.
        time.sleep(1)
        self.poll(self.s.game.pointer_grabbed,lambda v:v is True,90,'fixture dimension transfer finished')
        self.report['profiles'].append({'username':self.username,**default_profile(self.username)})
        self.save()

    def armor_rows(self):
        return {slot:row['id'] for slot,row in self.inventory().items() if 100<=slot<=103}

    def inventory_shift_click(self,x,y):
        game=self.s.game;px,py=menu_point(game.size,x,y,width=176,height=166)
        self.guard(menu=True);game.xdo('mousemove','--sync',str(px),str(py));game.xdo('keydown','Shift_L')
        try:game.xdo('click','1')
        finally:game.xdo('keyup','Shift_L')
        self.action('mapped_inventory_shift_click',x=px,y=py);time.sleep(.4)

    def equip(self):
        u=self.username
        self.check(u+'_starts_without_armor',not self.armor_rows(),self.inventory())
        for index,(suffix,slot) in enumerate(ARMOR):
            self.rc(f'item replace entity {u} hotbar.{index} with myvillage:xuantie_{suffix}')
        self.start_video(u+'-equip-and-poses')
        self.key('e');self.poll(self.s.game.pointer_grabbed,lambda v:v is False,label='inventory opened')
        self.capture(u+'-inventory-icons');self.close_menu()
        for index,(suffix,slot) in enumerate(ARMOR):
            self.key(str(index+1));self.right()
            expected='myvillage:xuantie_'+suffix
            rows=self.poll(self.armor_rows,lambda rows:rows.get(slot)==expected,label='mapped equip '+suffix)
            self.check(u+'_equip_'+suffix,rows.get(slot)==expected,rows)
        rows=self.inventory()
        self.check(u+'_four_unique_items',all(sum(r['count'] for r in rows.values() if r['id']=='myvillage:xuantie_'+s)==1 for s,_ in ARMOR),rows)
        armor=self.rc(f'attribute {u} minecraft:generic.armor get')
        self.check(u+'_vanilla_armor_total','15.0' in armor or armor.rstrip().endswith('15'),armor)
        self.key('e');self.poll(self.s.game.pointer_grabbed,lambda v:v is False,label='worn inventory')
        self.capture(u+'-inventory-worn')
        self.inventory_shift_click(16,16)
        self.poll(self.armor_rows,lambda rows:103 not in rows,label='mapped helmet removal')
        rows=self.inventory();slot=next(k for k,v in rows.items() if v['id']=='myvillage:xuantie_helmet')
        self.check(u+'_helmet_returns_once',0<=slot<=35 and sum(v['count'] for v in rows.values() if v['id']=='myvillage:xuantie_helmet')==1,rows)
        if slot<9:self.inventory_shift_click(16+18*slot,150)
        else:self.inventory_shift_click(16+18*((slot-9)%9),92+18*((slot-9)//9))
        self.poll(self.armor_rows,lambda rows:rows.get(103)=='myvillage:xuantie_helmet',label='mapped helmet re-equip')
        self.close_menu()

    def poses(self):
        u=self.username
        self.key('5');self.key('F5');self.key('F1');self.capture(u+'-back')
        self.key('F5');self.capture(u+'-front')
        self.hold(['Shift_L'],.8,sample=lambda:self.capture(u+'-crouch'))
        before=self.pos()
        self.hold(['w'],.8,sample=lambda:self.capture(u+'-walk'))
        after=self.pos()
        self.check(u+'_mapped_walk_moves',sum((a-b)**2 for a,b in zip(after,before))>.15,{'before':before,'after':after})
        self.rc(f'item replace entity {u} hotbar.4 with myvillage:qingfeng_sword')
        self.key('5');self.capture(u+'-holding-sword')
        self.hold([], .3, mouse=1, sample=lambda:self.capture(u+'-swing'))
        self.key('F1');self.key('F5')
        self.stop_video()

    def reload(self):
        log=cs.CLIENT_DIR/'logs/latest.log';offset=log.stat().st_size
        self.guard();self.s.game.xdo('keydown','F3')
        try:self.s.game.xdo('key','t')
        finally:self.s.game.xdo('keyup','F3')
        self.action('mapped_resource_reload',keys=['F3','t'])
        def tail():
            with log.open('rb') as stream:
                stream.seek(offset)
                return stream.read().decode(errors='replace')
        self.poll(tail,lambda s:'EQUIPMENT_MODELS loaded 8 item/profile models' in s,60,'equipment resource reload')
        time.sleep(5)
        self.key('e');self.poll(self.s.game.pointer_grabbed,lambda v:v is False,label='inventory after reload')
        self.capture(self.username+'-reload');self.close_menu()
        self.check('reload_preserves_equipped_items',len(self.armor_rows())==4,self.armor_rows())
        self.check('reload_no_equipment_errors',not any(s in tail() for s in ('Cannot load armor model','Equipment model not loaded','Unknown armor slot')),tail()[-2000:])

    def profiles(self):
        self.stop()
        self.username='CaptureDev'
        self.start(reuse=True)
        self.equip();self.poses()
        self.check('both_default_skin_profiles',set(p['profile'] for p in self.report['profiles'])=={'standard','slim'},self.report['profiles'])


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',type=Path,required=True);parser.add_argument('--cases',default=','.join(CASES))
    args=parser.parse_args();cases=args.cases.split(',')
    if not cases or len(set(cases))!=len(cases) or any(c not in CASES for c in cases):parser.error('invalid cases')
    EquipmentEvidence(args.out.resolve(),cases).run()


if __name__=='__main__':main()
