#!/usr/bin/env python3
"""Actual Minecraft placement, mapped traversal, screenshots and SAVE exports.

Runs only in the disposable Modal workspace. RCON places fixtures and reads
server state; W/right-click are physical client input. No owner-PC fallback.
"""
from __future__ import annotations
import argparse
import hashlib
import html
import json
import math
from pathlib import Path
import secrets
import shutil
import subprocess
import sys
import time
import zipfile

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT));sys.path.insert(0,str(ROOT/'tools'))
from tools.realm_stats_evidence import Evidence
from tools.combat_capture import session as cs,procs,scene
from tools.alchemy_quality_evidence import read_data
from tools.yunshan.game_export import read_typed,convert_game_save


def aim(pos,target):
    dx,dy,dz=target[0]-pos[0],target[1]-(pos[1]+1.62),target[2]-pos[2]
    return math.degrees(math.atan2(-dx,dz)),math.degrees(-math.atan2(dy,math.hypot(dx,dz)))


def teleport_command(username,pos,yaw=0,pitch=0):
    # Vanilla Vec3 command arguments center integer X/Z literals by +0.5.
    # Explicit decimals preserve the exact fixture and camera coordinates.
    return f'tp {username} '+ ' '.join(f'{float(v):.6f}' for v in (*pos,yaw,pitch))


class BuildingEvidence(Evidence):
    def __init__(self,manifest,out):
        super().__init__(out)
        self.manifest=manifest;self.spec=json.loads(manifest.read_text())
        self.source_paths=[manifest.parent/f for f in self.spec['buildings']]
        self.buildings=[json.loads(p.read_text()) for p in self.source_paths]
        self.source_hashes={d['id']:hashlib.sha256(p.read_bytes()).hexdigest() for d,p in zip(self.buildings,self.source_paths)}
        self.report.update(kind='yunshan_expanded_actual_game',selected=['place_four','mapped_entries','mapped_inn_stair','mapped_facilities','structure_block_save_four','world_save'],
            completed=[],buildings={},method='RCON template placement and readback; physical W/right mouse; actual Minecraft window captures; mapped structure-block SAVE button',
            owner_appearance='not_verified',owner_fps='not_verified',npc_services='not_implemented',software_rendering=True)
        self.hud_hidden=False

    def check(self,name,passed,detail):
        super().check(name,passed,detail)
        if not passed:raise AssertionError(name)

    def poll(self,getter,predicate,timeout,label):
        end=time.monotonic()+timeout;last=None
        while time.monotonic()<end:
            last=getter()
            if predicate(last):return last
            time.sleep(.15)
        raise TimeoutError(f'{label}: {last}')

    def start(self):
        if ROOT!=Path('/workspace/repo'):
            raise RuntimeError('Actual game job is restricted to the Modal disposable /workspace/repo checkout')
        t=time.monotonic()
        # A fresh options file omits unchanged vanilla defaults until a later
        # save. Pin only the disposable test client's actual input bindings.
        cs.merge_options(cs.CLIENT_DIR/'options.txt',{'key_key.forward':'key.keyboard.w','key_key.use':'key.mouse.right'})
        state=cs.start(cs.SessionConfig(session_id=secrets.token_hex(6),language='zh_cn',lock=str(procs.default_lock_path(ROOT))))
        self.s=cs.Session.attach(ui_check=True);self.sessions+=1
        self.report['phases'].append({'phase':'client_server_start','wall_s':round(time.monotonic()-t,3),'capture_timings':state.get('timings',{})})
        bindings=dict(l.split(':',1) for l in (cs.CLIENT_DIR/'options.txt').read_text().splitlines() if ':' in l)
        self.check('mapped_forward_binding',bindings.get('key_key.forward')=='key.keyboard.w',bindings.get('key_key.forward'))
        self.report['graphics']=subprocess.check_output(['glxinfo','-B'],env=self.s.game.env,text=True,timeout=20)
        request=Path('/workspace/request.json')
        if request.exists():self.report['source_commit']=json.loads(request.read_text())['commit']
        self.report['mod_version']=next(l.split('=',1)[1] for l in (ROOT/'gradle.properties').read_text().splitlines() if l.startswith('mod_version='))
        self.save()

    def xyz(self,d,local):
        return [d['site']['world_min'][i]+local[i] for i in range(3)]

    def player_pos(self):
        return read_data(self.rc(f'data get entity {self.s.username} Pos'))

    def teleport(self,pos,yaw=0,pitch=0):
        self.rc(teleport_command(self.s.username,pos,yaw,pitch))
        self.poll(self.player_pos,lambda p:all(abs(p[i]-pos[i])<.2 for i in range(3)),8,'teleport fixture applied')

    def set_hud(self,hidden):
        if hidden!=self.hud_hidden:
            self.s.game.key('F1');self.hud_hidden=hidden

    def photograph(self,d,shot):
        self.rc(f'gamemode spectator {self.s.username}')
        pos=self.xyz(d,shot['pos']);target=self.xyz(d,shot['target']);yaw,pitch=aim(pos,target)
        self.teleport(pos,yaw,pitch);self.set_hud(True)
        folder=self.out/d['presentation']['role'];folder.mkdir(exist_ok=True)
        info=self.s.game.shot(folder/(shot['name']+'.png'),max_wait=3,settle=.8)
        self.report['buildings'][d['id']]['screenshots'].append({'name':shot['name'],'pos':pos,'yaw':yaw,'pitch':pitch,'capture':info})
        self.save()

    def setup(self):
        u=self.s.username
        for command in (*scene.world_rules(),f'op {u}',f'clear {u}',f'gamemode creative {u}',
                        f'effect give {u} minecraft:saturation infinite 255 true','gamerule doFireTick false','gamerule randomTickSpeed 0',
                        'myvillage world pause','forceload add -32 -48 96 112',
                        'fill -16 -62 -32 88 -61 88 minecraft:smooth_stone','setworldspawn 32 -60 -8 0',
                        'defaultgamemode creative'):
            self.rc(command)
        self.teleport([32,-60,-8])
        world=cs.SERVER_DIR/cs.LEVEL_NAME
        pack=world/'datapacks/yunshan_expanded'
        target=pack/'data/myvillage/structure';target.mkdir(parents=True,exist_ok=True)
        (pack/'pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':48,'description':'Yunshan separately authored showcase structures'}}))
        for d in self.buildings:
            name=d['id'].split(':')[1]
            source=self.out.parent/d['presentation']['role']/(name+'.nbt')
            shutil.copyfile(source,target/(name+'.nbt'))
        self.rc('reload')
        self.poll(lambda:self.rc('datapack list enabled'),lambda s:'yunshan_expanded' in s,20,'showcase datapack enabled')
        for d in self.buildings:
            origin=d['site']['world_min'];name=d['id']
            self.report['buildings'][name]={'name':d['presentation']['name'],'size':d['size'],'origin':origin,'screenshots':[],
                'source_sha256':self.source_hashes[name]}
            reply=self.rc(f'place template {name} '+' '.join(map(str,origin)))
            # Authoritative block probe; full-volume equality is checked after SAVE.
            # The fresh sample floor is smooth_stone, so this is not satisfied
            # by the pre-existing fixture. Do not infer placement from wording.
            x,y,z=origin
            probe=self.poll(lambda:self.rc(f'execute if block {x} {y} {z} minecraft:stone_bricks'),lambda s:'passed' in s,10,'placed foundation')
            self.check(name+'_placed',True,{'placement_reply':reply,'world_probe':probe})
        self.report['completed'].append('place_four');self.save()

    def walk(self,d,trial):
        self.set_hud(False);u=self.s.username
        self.rc(f'gamemode survival {u}');start=self.xyz(d,trial['start']);self.teleport(start,trial['yaw'],0)
        self.s.game.require_ingame('mapped W traversal');positions=[];t=time.monotonic()
        end_z=d['site']['world_min'][2]+trial['end_z'];min_y=d['site']['world_min'][1]+trial['min_y']
        self.s.game.xdo('keydown','w')
        try:
            while time.monotonic()-t<trial['timeout_s']:
                p=self.player_pos();positions.append(p)
                if p[2]>=end_z and p[1]>=min_y-.1:break
                time.sleep(.12)
        finally:self.s.game.xdo('keyup','w')
        final=self.player_pos();detail={'start':start,'final':final,'target_z':end_z,'min_y':min_y,'positions':positions,'input':'physical mapped W; no jump key','elapsed_s':round(time.monotonic()-t,3)}
        self.check(d['id']+'_'+trial['name'],final[2]>=end_z and final[1]>=min_y-.1,detail)
        folder=self.out/d['presentation']['role'];folder.mkdir(exist_ok=True)
        self.s.game.shot(folder/(trial['name']+'-walk.png'),max_wait=2,settle=.3)

    def facilities(self,d):
        u=self.s.username;self.set_hud(False);self.rc(f'gamemode survival {u}')
        for service in d.get('service_blocks',[]):
            p=d['walk_targets'][service['approach']];pos=self.xyz(d,[p[0]+.5,p[1],p[2]+.5]);b=service['pos'];target=self.xyz(d,[b[0]+.5,b[1]+.5,b[2]+.5])
            yaw,pitch=aim(pos,target);self.teleport(pos,yaw,pitch)
            before=self.rc(f'myvillage cultivation info {u}') if 'inheritance_stele' in service['state'] else None
            self.s.game.require_ingame('mapped facility use');self.s.game.xdo('click','3')
            if 'inheritance_stele' in service['state']:
                after=self.poll(lambda:self.rc(f'myvillage cultivation info {u}'),lambda s:'myvillage:basic_breathing' in s,10,'mapped inheritance')
                self.check(d['id']+'_mapped_inheritance','myvillage:basic_breathing' not in before,{'before':before,'after':after})
            else:
                self.poll(self.s.game.pointer_grabbed,lambda s:not s,10,'mapped facility UI')
                window=self.s.game.xdo('search','--name','Minecraft').split()[0]
                path=self.out/d['presentation']['role']/(service['approach']+'-ui.png')
                subprocess.run(['import','-silent','-window',window,str(path)],env=self.s.game.env,check=True,timeout=30)
                self.check(d['id']+'_mapped_'+service['approach'],not self.s.game.pointer_grabbed(),{'block':service['state'],'capture':str(path.relative_to(self.out))})
                self.s.game.xdo('key','Escape');self.poll(self.s.game.pointer_grabbed,bool,8,'close facility UI')

    def save_building(self,d):
        origin=d['site']['world_min'];x,y,z=origin;w,h,length=d['size'];name=d['id'].split(':')[1]+'_game'
        block=(x-2,y+1,z)
        tag='{name:"myvillage:'+name+'",mode:"SAVE",posX:2,posY:-1,posZ:0,sizeX:'+str(w)+',sizeY:'+str(h)+',sizeZ:'+str(length)+',ignoreEntities:1b,showboundingbox:0b,powered:0b}'
        self.rc(f'setblock {block[0]} {block[1]} {block[2]} minecraft:structure_block[mode=save]'+tag)
        # Redstone calls saveStructure(false), which only updates memory. The
        # actual GUI SAVE_AREA button calls saveStructure(true) and writes disk.
        self.set_hud(False);self.rc(f'gamemode creative {self.s.username}')
        pos=[block[0]-2.5,y+1,z+.5];target=[block[0]+.5,block[1]+.5,z+.5]
        yaw,pitch=aim(pos,target);self.teleport(pos,yaw,pitch)
        self.s.game.require_ingame('structure-block editor');self.s.game.xdo('click','3')
        self.poll(self.s.game.pointer_grabbed,lambda p:not p,8,'structure editor opened')
        folder=self.out/d['presentation']['role'];window=self.s.game.xdo('search','--name','Minecraft').split()[0]
        subprocess.run(['import','-silent','-window',window,str(folder/'structure-save-ui.png')],env=self.s.game.env,check=True,timeout=30)
        width,height=self.s.game.size;scale=max(1,min(width//320,height//240))
        # Verified against the current 1.21.1 StructureBlockEditScreen sources:
        # saveButton.bounds(width/2+104,185,50,20).
        self.s.game.xdo('mousemove',str((width//scale//2+129)*scale),str(195*scale))
        self.s.game.xdo('click','1')
        self.poll(self.s.game.pointer_grabbed,bool,8,'structure save closes editor')
        generated=cs.SERVER_DIR/cs.LEVEL_NAME/'generated'
        def saved():
            files=list(generated.rglob(name+'.nbt')) if generated.exists() else []
            if not files:return None
            try:read_typed(files[0])
            except (OSError,EOFError,ValueError):return None
            return files[0]
        path=self.poll(saved,bool,15,'Minecraft structure-block SAVE file')
        result=convert_game_save(path,folder/name,d)
        self.check(d['id']+'_game_export',result['base_ids_and_structural_states_match'] and result['nbt_is_unmodified_game_save'],result)
        self.report['buildings'][d['id']]['game_export']=result
        self.rc(f'setblock {block[0]} {block[1]} {block[2]} minecraft:air')

    def package_world(self):
        world=cs.SERVER_DIR/cs.LEVEL_NAME
        with zipfile.ZipFile(self.out/'yunshan_showcase_world.zip','w',zipfile.ZIP_DEFLATED) as z:
            for file in sorted(world.rglob('*')):
                rel=file.relative_to(world)
                if file.is_file() and not file.is_symlink() and not any(p in ('playerdata','stats','advancements','session.lock') for p in rel.parts):
                    z.write(file,'Yunshan Showcase/'+str(rel))

    def page(self):
        cards=[]
        for d in self.buildings:
            role=d['presentation']['role'];name=html.escape(d['presentation']['name']);stem=d['id'].split(':')[1]+'_game'
            cards.append(f'<article><h2>{name}</h2><img src="{role}/exterior.png" alt="{name}游戏内外观"><img src="{role}/interior.png" alt="{name}游戏内室内"><p><a href="{role}/{stem}.schem" download>游戏保存转出的 SCHEM</a> · <a href="{role}/{stem}.nbt" download>原始游戏 NBT</a></p></article>')
        page='''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>云山四栋放大版 · 实际游戏建模</title><style>*{box-sizing:border-box}body{margin:0;background:#eeeae0;color:#30382f;font:15px system-ui}main{max-width:1160px;margin:auto;padding:24px}p{line-height:1.8}a{color:#3e6553}.cards{display:grid;grid-template-columns:1fr 1fr;gap:18px}article{padding:16px;background:#faf8f0;border-radius:9px}article img{width:100%;display:block;margin:9px 0}h2{font-size:20px}.note{padding:17px;background:#faf8f0;border-radius:8px;margin:16px 0}@media(max-width:700px){.cards{grid-template-columns:1fr}}</style><main><h1>云山四栋放大版 · 实际游戏建模</h1><p>在 Modal 的 Minecraft NeoForge 客户端和服务器中实际放置、走入并保存。以下为原始游戏画面，不是离线色块预览。</p><div class="note">每栋主入口净高三格；实际 W 键通行已检查，客栈另检查双宽楼梯。游戏内结构方块保存的 NBT 保持原样，SCHEM 从该保存转换并保留方块实体。云端使用软件渲染，不代表你的电脑 FPS 或最终外观验收。</div><p><a href="yunshan_game_blueprints.zip" download>下载四栋游戏蓝图包</a> · <a href="yunshan_showcase_world.zip" download>下载独立样板世界</a> · <a href="../index.html">回到设计与尺寸</a></p><div class="cards">'''+''.join(cards)+'''</div><p>样板世界需要匹配的 MyVillage 模组及依赖。建筑已超出原小地块；旧街区未修改，回填需重新规划。商人、委托、收费住宿仍未接入。</p></main></html>'''
        (self.out/'index.html').write_text(page)
        with zipfile.ZipFile(self.out/'yunshan_game_blueprints.zip','w',zipfile.ZIP_DEFLATED) as z:
            for d in self.buildings:
                folder=self.out/d['presentation']['role']
                for p in sorted(folder.iterdir()):
                    if p.is_file():z.write(p,str(p.relative_to(self.out)))
            z.write(self.out/'evidence.json','evidence.json')

    def run(self):
        self.start();self.setup()
        for d in self.buildings:
            for trial in d['walk_trials']:self.walk(d,trial)
        self.report['completed']+=['mapped_entries','mapped_inn_stair']
        for d in self.buildings:
            for shot in d['game_shots']:self.photograph(d,shot)
            self.facilities(d)
        self.report['completed'].append('mapped_facilities')
        for d in self.buildings:self.save_building(d)
        self.report['completed'].append('structure_block_save_four')
        self.set_hud(False);self.rc(f'gamemode creative {self.s.username}');self.teleport([32,-60,-8],0,0)
        self.rc('save-all flush');self.stop();self.package_world()
        self.report['completed'].append('world_save');self.report['status']='pass';self.save();self.page()


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--manifest',type=Path,default=ROOT/'blueprints/yunshan/functional_buildings_v2.json')
    p.add_argument('--out',type=Path,default=ROOT/'out/preview/yunshan-functional-v2/runtime')
    args=p.parse_args();e=BuildingEvidence(args.manifest,args.out)
    try:e.run()
    except BaseException as error:
        e.report['status']='fail';e.report['failures'].append(repr(error));e.save();raise
    finally:
        if e.s:
            try:e.s.game.xdo('keyup','w')
            finally:e.stop()
        e.save()


if __name__=='__main__':main()
