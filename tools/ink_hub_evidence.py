#!/usr/bin/env python3
"""Modal-only H hub verification: mapped navigation, category/core selection, binding and GUI-scale captures."""
from pathlib import Path
import itertools
import json
import math
import secrets
import sys
import time

ROOT=Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:sys.path.insert(0,str(ROOT))
from tools.combat_capture import procs,scene,session as cs
from tools.cultivation_ink_evidence import InkEvidence
from tools.realm_stats_evidence import Evidence


def bounds(size):
    width,height=size
    scale=max(1,min(width//320,height//240))
    gw,gh=math.ceil(width/scale),math.ceil(height/scale)
    pw,ph=min(480,gw-8),min(282,gh-8)
    left,top=(gw-pw)//2,(gh-ph)//2
    return dict(scale=scale,left=left,top=top,x=left+64,y=top+36,width=pw-73,height=ph-54)


def tile_center(box,index,top=22):
    columns=max(1,min(4,(box['width']+4)//68))
    cell=(box['width']-(columns-1)*4)//columns
    return box['x']+index%columns*(cell+4)+cell//2,box['y']+top+index//columns*60+28


def choose_mask(state,definitions):
    for slots in itertools.combinations(range(len(state['candidates'])),3):
        rows=[definitions[state['candidates'][i]] for i in slots]
        if sum(row['cost'] for row in rows)<=state['budget'] and len({row['group'] for row in rows})==3:
            return sum(1<<i for i in slots)
    raise ValueError('No legal fate triple in fixture')


def fate_state(reply):
    state=json.loads(next(line[6:] for line in reply.splitlines() if line.startswith('FATES ')))
    # FateState.CODEC legitimately omits these fields when they equal the defaults.
    state.setdefault('confirmed',False)
    state.setdefault('selected',[])
    state.setdefault('candidates',[])
    if not isinstance(state.get('now'),int) or not isinstance(state.get('budget'),int):
        raise ValueError('Incomplete authoritative fate readback')
    return state


def window_geometry(reply):
    values={key:int(value) for line in reply.splitlines() for key,sep,value in [line.partition('=')]
            if sep and key in ('X','Y','WIDTH','HEIGHT')}
    if set(values)!=set(('X','Y','WIDTH','HEIGHT')):raise ValueError('Incomplete X11 window geometry')
    return values


class HubEvidence(InkEvidence):
    def __init__(self):
        out=ROOT/'out/preview/ink-hub-runtime'
        if out.exists() and any(out.iterdir()):raise ValueError('Preserve previous evidence')
        Evidence.__init__(self,out)
        self.opened=False;self.layout_only=False
        self.report.update(kind='ink_hub_ui',selected=['pages','techniques','bindings','meditation','font_scales'],
                           completed=[],unrun=[],inputs=[],screenshots=[],videos=[],method=__doc__,
                           owner_appearance='not_verified',owner_fps='not_verified',
                           fixture='Administrator learning/fate/realm preparation, real mouse/key actions, disposable flat world')
        self.learned=[]
        self.lang=json.loads((ROOT/'src/main/resources/assets/myvillage/lang/zh_cn.json').read_text())
        self.defs={p.stem:json.loads(p.read_text()) for p in (ROOT/'src/main/resources/data/myvillage/myvillage/technique').glob('*.json')}

    def start(self):
        if ROOT!=Path('/workspace/repo'):raise RuntimeError('Minecraft runs only in Modal')
        state=cs.start(cs.SessionConfig(session_id=secrets.token_hex(6),language='zh_cn',
                                       framebuffer_width=1920,framebuffer_height=1080,
                                       lock=str(procs.default_lock_path(ROOT))))
        self.s=cs.Session.attach(ui_check=True);self.sessions+=1
        self.position_window((960,540))
        self.report['source_commit']=json.loads(Path('/workspace/request.json').read_text())['commit']
        self.report['phases'].append({'phase':'setup','timings':state.get('timings',{}),'framebuffer':state.get('framebuffer')})
        u=self.s.username
        for cmd in (*scene.world_rules(),*scene.player_setup(u,'minecraft:air'),'myvillage world pause',
                    'forceload add -16 -16 32 32',f'myvillage cultivation awaken {u}',f'myvillage cultivation initiate {u}'):
            self.rc(cmd)
        self.poll(lambda:self.rc('execute if loaded 0 -61 0 run time query gametime'),lambda r:'The time is ' in r,30,'fixture loaded')
        self.rc('fill -4 -61 -4 4 -61 4 minecraft:stone');self.rc(f'tp {u} .5 -60 .5 0 0')
        self.rc(f'myvillage cultivation setrealm {u} myvillage:golden_core myvillage:golden_late')
        self.rc(f'myvillage cultivation setroot {u} 4000 1500 1500 1500 1500')
        self.rc(f'myvillage cultivation crafting set {u} alchemy 76')
        self.rc(f'myvillage cultivation crafting set {u} forging 68')
        self.learned=['basic_breathing','tuna_xinfa','qingmu_changchun_gong','liuyun_bu','tiegu_gong','jianqi_jue',
                      'qinglian_jiange','huifeng_jianzhen','canglong_chushui','hengsao_qianjun','bengshan_jin','jingang_zhen']
        # Four grades of ordinary core methods prove that the symbol remains shared.
        for grade in (2,3,4):
            self.learned.append(next(k for k,v in sorted(self.defs.items()) if v['category']=='core' and v['grade']==grade))
        for ident in self.learned:
            if ident!='basic_breathing':self.rc(f'myvillage cultivation learn {u} myvillage:{ident}')
        profile=self.profile()
        self.must('fixture_techniques_learned',all('myvillage:'+i in profile['learned'] for i in self.learned),profile['learned'])
        self.rc(f'myvillage cultivation fate roll {u}')
        state=self.fates()
        definitions={'myvillage:'+p.stem:json.loads(p.read_text()) for p in (ROOT/'src/main/resources/data/myvillage/myvillage/innate_fate').glob('*.json')}
        request_ticks=json.loads((ROOT/'src/main/resources/data/myvillage/fates/rules.json').read_text())['request_ticks']
        self.poll(self.fates,lambda value:value['now']>=state['now']+request_ticks,5,'fate request interval elapsed')
        self.rc(f'myvillage cultivation fate confirm {u} {choose_mask(state,definitions)}')
        confirmed=self.poll(self.fates,lambda value:value['confirmed'],5,'fate fixture confirmed')
        self.must('fixture_fates_confirmed',confirmed['confirmed'],confirmed['selected'])

    def fates(self):
        reply=self.rc(f'myvillage cultivation fate status {self.s.username}')
        return fate_state(reply)

    def position_window(self,size):
        window=self.s.game.xdo('search','--name','Minecraft').split()[0]
        self.s.game.xdo('windowsize','--sync',window,str(size[0]),str(size[1]))
        self.s.game.xdo('windowmove','--sync',window,'0','0')
        actual=window_geometry(self.s.game.xdo('getwindowgeometry','--shell',window))
        self.must('capture_window_geometry',actual==dict(X=0,Y=0,WIDTH=size[0],HEIGHT=size[1]),actual)
        self.s.game.size=size
        self.report['inputs'].append({'method':'mapped X11 window size and position','geometry':actual,'expected_auto_gui_scale':bounds(size)['scale']})
        time.sleep(.8)

    def gui_click(self,x,y,label):
        scale=bounds(self.s.game.size)['scale'];self.click(round(x*scale),round(y*scale),label)

    def show_page(self,index):
        if not self.opened:
            self.s.game.key('h');self.report['inputs'].append({'action':'open H','method':'mapped key H'});time.sleep(.4);self.opened=True
        b=bounds(self.s.game.size)
        self.gui_click(b['left']+26,b['top']+36+index*21+9,'select H page '+str(index))
        self.must('H_pointer_released',self.s.game.pointer_grabbed() is False,'mapped H page is open')

    def panel(self):self.show_page(1)

    def ordered(self,category):
        return sorted([i for i in self.learned if self.defs[i]['category']==category],
                      key=lambda i:(-self.defs[i]['grade'],self.lang[self.defs[i]['translation_key']],'myvillage:'+i))

    def category(self,index):
        b=bounds(self.s.game.size);self.gui_click(b['x']+(2*index+1)*b['width']/10,b['y']+9,'category filter '+str(index))

    def pick(self,index,top=22):
        self.gui_click(*tile_center(bounds(self.s.game.size),index,top),'select technique tile '+str(index))

    def capture_hover(self,name,x,y):
        scale=bounds(self.s.game.size)['scale'];self.s.game.xdo('mousemove',str(round(x*scale)),str(round(y*scale)))
        time.sleep(.4);raw,meta=self.s.game.stable_grab(max_wait=3,settle=.2)
        self.s.game.save_png(raw,self.out/(name+'.png'))
        self.report['screenshots'].append({'file':name+'.png','purpose':'actual hover details','capture':meta});self.save()

    def bindings(self):
        return self.rc(f'execute as {self.s.username} run myvillage combat bindings')

    def run(self):
        self.start()
        for i,name in enumerate(('profile','meditation','techniques','skills','world','fates','crafting')):
            self.show_page(i);self.capture(name+'_wide')
        self.report['completed'].append('pages')

        self.show_page(2);self.category(1);self.capture('core_categories_and_grades')
        self.pick(self.ordered('core').index('tuna_xinfa'))
        b=bounds(self.s.game.size);self.gui_click(b['x']+b['width']-43,b['y']+b['height']-17,'set running core')
        self.poll(self.profile,lambda p:'myvillage:tuna_xinfa' in p['core'],5,'mapped core switch')
        self.must('core_switch_server_confirmed','myvillage:tuna_xinfa' in self.profile()['core'],self.profile()['core'])
        self.capture('core_switched')
        self.category(2);self.capture('six_illustrated_arts')
        index=self.ordered('active').index('canglong_chushui');self.pick(index)
        tx,ty=tile_center(b,index);self.capture_hover('art_hover',tx,ty)
        self.report['completed'].append('techniques')

        self.gui_click(b['x']+b['width']-43,b['y']+b['height']-17,'open selected art in skills')
        # Same geometry as the fixed dock: six slots in one wide row, three columns when narrow.
        columns=6 if b['width']>=350 else 3;rows=6//columns
        dock=b['y']+b['height']-(15+rows*24+22)
        self.gui_click(b['x']+(3%columns+.5)*b['width']/columns,dock+14+(3//columns)*24+11,'select J slot')
        self.gui_click(b['x']+max(66,b['width']//4)//2,dock+15+rows*24+9,'assign selected art to J')
        self.poll(self.bindings,lambda r:'myvillage:canglong_chushui' in r,5,'binding saved by server')
        self.must('binding_server_confirmed','myvillage:canglong_chushui' in self.bindings(),self.bindings())
        self.capture('assigned_art_and_grade')
        # Clear the same slot through its actual control.
        aw=max(66,b['width']//4);cw=(b['width']-aw-4-9)//4
        self.gui_click(b['x']+aw+4+cw//2,dock+15+rows*24+9,'clear J slot')
        self.poll(self.bindings,lambda r:'myvillage:canglong_chushui' not in r,5,'binding cleared')
        self.must('clear_server_confirmed','myvillage:canglong_chushui' not in self.bindings(),self.bindings())
        self.report['completed'].append('bindings')

        self.close_panel();self.rc(f'myvillage cultivation setrealm {self.s.username} myvillage:qi_refining myvillage:qi_refining_6')
        self.rc(f'myvillage cultivation setprogress {self.s.username} 400')
        self.show_page(1);before=self.profile()['progress']
        b=bounds(self.s.game.size);cell=(b['width']-12)//4;action_y=b['y']+b['height']-22+4+9
        self.gui_click(b['x']+cell//2,action_y,'normal meditation')
        self.poll(self.profile,lambda p:p['progress']>before,10,'normal cultivation started')
        self.capture('meditation_active',moving=True)
        self.gui_click(b['x']+3*(cell+4)+cell//2,action_y,'stop meditation')
        self.capture('meditation_stopped');stopped=self.profile()['progress'];time.sleep(.7)
        self.must('meditation_stops',self.profile()['progress']==stopped,self.profile()['progress'])
        self.report['completed'].append('meditation')

        # Real window resizing changes the actual vanilla GUI scale, with no developer pose bridge.
        for size,name in [((640,480),'gui2_narrow'),((1920,1080),'gui4')]:
            self.close_panel();self.position_window(size)
            self.show_page(2);self.category(2);self.capture(name+'_arts')
            self.show_page(1);self.capture(name+'_meditation')
            if size==(640,480):
                self.s.game.xdo('mousemove',str(size[0]//2),str(size[1]//2),'click','--repeat','16','--delay','40','5')
                time.sleep(.3);self.capture('gui2_narrow_meditation_readouts')
                self.show_page(3);self.capture('gui2_narrow_skill_dock')
        self.report['completed'].append('font_scales')
        self.report['unrun']=[case for case in self.report['selected'] if case not in self.report['completed']]


def main():
    evidence=HubEvidence()
    try:
        evidence.run();evidence.report['status']='pass'
    except BaseException as error:
        evidence.report['status']='fail';evidence.report['failures'].append(repr(error));raise
    finally:
        evidence.report['unrun']=[case for case in evidence.report['selected'] if case not in evidence.report['completed']]
        if evidence.s:
            try:evidence.close_panel();evidence.stop()
            except BaseException as error:evidence.report['failures'].append('cleanup: '+repr(error))
        evidence.save()
    return int(bool(evidence.report['failures']))

if __name__=='__main__':raise SystemExit(main())
