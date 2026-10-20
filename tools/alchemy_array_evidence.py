#!/usr/bin/env python3
"""Disposable-world array evidence using real mouse/key input and server readback.

Run through Modal array-runtime/array-play, never on an owner save. Commands only
prepare fixtures and inspect state. Every case is independently selectable; the
report distinguishes native fixtures, mapped actions and unobserved acceptance.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import subprocess
import sys
import time
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from tools.alchemy_evidence import AlchemyEvidence
from tools.alchemy_quality_evidence import quality_stacks, read_data, read_batch, pill_quality
from tools.combat_capture import scene, session as cs
from tools.modal_runner.remote import select_array_cases

ORIGIN = (0, -59, 3)
VECTORS = {'north': (0, -1), 'east': (1, 0), 'south': (0, 1), 'west': (-1, 0)}
MENU = (368, 250)
# Updated with the screen implementation, all coordinates are GUI-local centres.
POINTS = {'start': (193, 148), 'stop': (305, 148), 'recipe': (69, 60),
          'recipe_tab': (38, 34), 'blueprint': (100, 34)}


def world_pos(origin, facing, cell):
    """Fixture transform: local right, up, back in vanilla world coordinates."""
    fx, fz = VECTORS[facing]
    return (origin[0]-fz*cell['x']-fx*cell['z'], origin[1]+cell['y'],
            origin[2]+fx*cell['x']-fz*cell['z'])


def position(pos):
    return ' '.join(str(n) for n in pos)


def outward(facing, role):
    names = list(VECTORS)
    offset = {'input': -1, 'output': 1, 'fuel': 2}.get(role, 0)
    return names[(names.index(facing)+offset) % 4]


def menu_point(size, x, y, dimensions=MENU):
    width, height = size
    scale = max(1, min(width//320, height//240))
    return (scale*((width//scale-dimensions[0])//2+x),
            scale*((height//scale-dimensions[1])//2+y))


def saved_items(data):
    # Use the strict component-preserving parser instead of silently dropping
    # quality or nested tags when checking conservation and restart identity.
    return quality_stacks('data: '+json.dumps(data.get('Items', [])))


def frozen_identity(data):
    """Only fields required to survive pause/restart; progress is measured apart."""
    required = ('Batch', 'Reserved', 'SelectedRecipe', 'Authorizer', 'Score', 'Cap', 'Identity')
    if any(name not in data for name in required) or not isinstance(data['Batch'], dict):
        raise ValueError('Missing frozen array batch/authorization identity')
    read_batch('data: '+json.dumps(data['Batch']))
    return {name: data[name] for name in required}



class ArrayEvidence(AlchemyEvidence):
    def __init__(self, out, cases):
        super().__init__(out, 'zh_cn')
        self.cases = cases
        self.layout = json.loads((ROOT/'src/main/resources/data/myvillage/alchemy/array.json').read_text())
        self.recorders = []
        self.report.update(kind='alchemy_array', selected=cases, completed=[], unrun=list(cases),
                           mapped_input=[], native_fixtures=[],
                           owner_feel='not_verified', owner_sound='not_verified', owner_fps='not_verified',
                           owner_appearance='not_verified', broad_multiplayer='not_verified',
                           method='real mapped mouse/key; administrator fixtures and readback; isolated Modal world')

    def check(self, name, passed, detail):
        result = super().check(name, passed, detail)
        if not result:
            raise AssertionError(name)
        return result

    def inspect(self, origin=ORIGIN):
        return self.one('myvillage array inspect '+position(origin), 'ALCHEMY_ARRAY')

    def saved(self, origin=ORIGIN):
        data = read_data(self.rc('data get block '+position(origin)))
        if not isinstance(data, dict):
            raise ValueError('Expected full controller serialization')
        return data

    def inventory(self):
        return quality_stacks(self.rc('data get entity '+self.s.username+' Inventory'))

    def close_menu(self):
        if self.s.game.pointer_grabbed() is False:
            self.s.game.key('Escape')
            time.sleep(.25)
        self.s.game.require_ingame('array world input')

    def click(self, x, y, *, shift=False, label='menu', dimensions=MENU):
        px, py = menu_point(self.s.game.size, x, y, dimensions)
        game = self.s.game
        game.xdo('mousemove', str(px), str(py))
        if shift:
            game.xdo('keydown', 'Shift_L')
        try:
            game.xdo('click', '1')
        finally:
            if shift:
                game.xdo('keyup', 'Shift_L')
        self.report['mapped_input'].append({'kind': label, 'gui': [x, y], 'shift': shift})
        time.sleep(.3)

    def open(self):
        self.close_menu()
        self.rc(f'tp {self.s.username} .5 -60 .5 0 2.3')
        time.sleep(.4)
        self.s.game.xdo('click', '3')
        self.report['mapped_input'].append({'kind': 'open_controller', 'origin': ORIGIN})
        self.poll(self.s.game.pointer_grabbed, lambda grabbed: grabbed is False, 10, 'mapped array menu')

    def slot(self, index, item, count=1, origin=ORIGIN):
        self.rc(f'item replace block {position(origin)} container.{index} with {item} {count}')

    def place_fixture(self, origin=ORIGIN, facing='north', omit=None):
        """Administrative placement is explicitly separate from mapped placement."""
        self.close_menu()
        x, y, z = origin
        self.rc(f'fill {x-4} {y-1} {z-4} {x+4} {y+3} {z+4} minecraft:air')
        self.rc(f'fill {x-4} {y-2} {z-4} {x+4} {y-2} {z+4} minecraft:smooth_stone')
        for cell in self.layout['cells']:
            role = cell['role'].lower()
            if role == 'air' or (cell['x'], cell['y'], cell['z']) == omit:
                continue
            block = 'myvillage:alchemy_array_'+role
            self.rc(f'setblock {position(world_pos(origin, facing, cell))} {block}[facing={outward(facing,role)}]')
        self.report['native_fixtures'].append({'origin': origin, 'facing': facing, 'omitted': omit})

    def prepare(self):
        self.place_fixture()
        u = self.s.username
        for command in (f'clear {u}', f'gamemode survival {u}', f'myvillage cultivation awaken {u}',
                        f'myvillage cultivation initiate {u}',
                        f'myvillage cultivation setrealm {u} myvillage:qi_refining myvillage:qi_refining_1',
                        f'myvillage cultivation crafting set {u} alchemy 100'):
            self.rc(command)
        self.poll(self.inspect, lambda data: data['formed'], 10, 'complete array')
        # Formula use is an actual mapped right click; no knowledge NBT injection.
        recipe = self.rules['recipes'][0]
        self.rc(f'item replace entity {u} hotbar.0 with {recipe["formula"]}')
        self.rc(f'tp {u} .5 -60 .5 180 -60')
        self.s.game.key('1')
        time.sleep(.3)
        self.s.game.xdo('click', '3')
        self.report['mapped_input'].append({'kind': 'read_formula', 'item': recipe['formula']})
        time.sleep(.4)
        return recipe

    def select(self):
        self.open()
        self.click(*POINTS['recipe'], label='select_learned_formula')
        self.poll(self.saved, lambda data: data.get('SelectedRecipe') == self.rules['recipes'][0]['id'],
                  8, 'selected learned recipe')

    def start_batch(self):
        self.select()
        self.click(*POINTS['start'], label='enable_continuous_brewing')
        self.poll(self.saved, lambda data: isinstance(data.get('Batch'), dict), 8, 'mapped authorized batch')

    def stop_next(self):
        if self.s.game.pointer_grabbed():
            self.open()
        self.click(*POINTS['stop'], label='stop_next_batch')
        self.poll(self.saved, lambda data: not data.get('Enabled'), 8, 'mapped stop')

    def materials(self, recipe, batches=1):
        for i, item in enumerate(recipe['ingredients']):
            self.slot(i, item, batches)
        self.slot(2, 'myvillage:low_grade_spirit_stone', batches*recipe['fuel_count'])

    def await_output(self, recipe):
        return self.poll(self.saved, lambda data: any(row['id'] == recipe['output']
                         for row in saved_items(data).values()), recipe['duration_ticks']/20+15, 'array product')

    def structure(self):
        # This case proves native world structure queries, never player placement.
        for i, facing in enumerate(VECTORS):
            origin = (12+12*i, -59, 3)
            self.place_fixture(origin, facing)
            state = self.poll(lambda: self.inspect(origin), lambda data: data['formed'], 10, facing+' fixture')
            self.check('native_four_direction_'+facing, state['formed'], state)
            self.rc('setblock '+position(world_pos(origin, facing, {'x':0,'y':0,'z':1}))+' minecraft:stone')
            state = self.poll(lambda: self.inspect(origin), lambda data: not data['formed'], 10, 'blocked chamber')
            self.check('native_blocked_chamber_'+facing, not state['formed'], state)
            self.rc('setblock '+position(world_pos(origin, facing, {'x':0,'y':0,'z':1}))+' minecraft:air')
            self.poll(lambda: self.inspect(origin), lambda data: data['formed'], 10, 'chamber repaired')
            missing = world_pos(origin, facing, {'x':-1,'y':1,'z':0})
            self.rc('setblock '+position(missing)+' minecraft:air')
            state = self.poll(lambda: self.inspect(origin), lambda data: not data['formed'], 10, 'missing casing')
            self.check('native_missing_casing_'+facing, not state['formed'], state)
        self.place_fixture()
        self.poll(self.inspect,lambda data:data['formed'],10,'main probe structure')
        for role,local in (('input',{'x':-1,'y':0,'z':1}), ('fuel',{'x':0,'y':0,'z':2}),
                           ('output',{'x':1,'y':-1,'z':1})):
            port = world_pos(ORIGIN, 'north', local)
            probe = self.one('myvillage array port '+position(port), 'ALCHEMY_ARRAY_PORT')
            self.check('native_capability_probe_'+position(port), probe.get('bound') is True
                       and probe.get('simulation_unchanged') is True
                       and {side['side'] for side in probe['sides'] if side.get('available')}
                       == ({outward('north',role),'down'} if role=='output' else {outward('north',role)})
                       and all(row['minecraft:cobblestone']==0 for side in probe['sides']
                               for row in side.get('probes',[])), probe)
        stale=self.one('myvillage array stale -1 -59 4','ALCHEMY_ARRAY_STALE')
        self.check('native_cached_handler_cannot_bypass_replacement',
                   all(stale.get(key) is True for key in
                       ('broken_rejected','replacement_rejected','fresh_accepted','restored')),stale)
        # Place across x=96, retain only the controller chunk, then observe real
        # unloading through vanilla's loaded predicate before claiming a pause.
        boundary=(95,-59,3)
        self.rc('forceload add 80 0 111 15'); self.place_fixture(boundary)
        self.poll(lambda:self.inspect(boundary),lambda data:data['formed'],10,'boundary initially loaded')
        self.rc(f'tp {self.s.username} -48.5 -60 .5')
        self.rc('forceload remove 96 0 111 15')
        self.poll(lambda:self.rc('execute unless loaded 96 -59 4 run say ARRAY_EDGE_UNLOADED'),
                  lambda reply:'ARRAY_EDGE_UNLOADED' in reply,45,'actual neighboring chunk unload')
        state=self.poll(lambda:self.inspect(boundary),lambda data:not data['formed'],10,'unloaded structure refusal')
        self.check('native_unloaded_component_chunk_refuses_structure',not state['formed'],state)
        self.rc('forceload add 96 0 111 15')
        self.poll(lambda:self.inspect(boundary),lambda data:data['formed'],15,'component chunk reload')
        self.check('native_component_reload_recovers',True,self.inspect(boundary))

    def mapped(self):
        recipe = self.prepare()
        self.close_menu()
        # Correct one actual missing casing using the mapped use key and verify
        # the exact new block, rather than accepting an animation or command plan.
        self.rc('setblock -1 -58 3 minecraft:air')
        self.poll(self.inspect, lambda data: not data['formed'], 10, 'missing mapped target')
        self.rc(f'item replace entity {self.s.username} hotbar.0 with myvillage:alchemy_array_casing')
        self.rc('setblock -1 -58 1 minecraft:smooth_stone')
        self.rc(f'tp {self.s.username} -.5 -57 1.5 0 52.64')
        self.s.game.key('1'); time.sleep(.5); self.s.game.xdo('click', '3')
        self.report['mapped_input'].append({'kind':'place_missing_casing','position':[-1,-58,3]})
        self.poll(lambda:self.rc('execute if block -1 -58 3 myvillage:alchemy_array_casing'),
                  lambda reply:'Test passed' in reply, 8, 'real casing placement')
        self.poll(self.inspect, lambda data:data['formed'], 10, 'mapped repaired array')
        self.select()
        self.capture('mapped-selected-formula.png')
        self.click(*POINTS['blueprint'], label='blueprint_tab')
        for layer,x in [('bottom',28),('middle',70),('top',112)]:
            self.click(x,57,label='blueprint_'+layer)
            self.capture('mapped-blueprint-'+layer+'.png')
        self.close_menu(); self.materials(recipe, 2); self.start_batch()
        before = self.saved(); self.stop_next(); stopped = self.saved()
        self.check('mapped_stop_preserves_active_batch', frozen_identity(stopped)==frozen_identity(before), stopped)
        self.capture('mapped-stopped-active.png'); self.close_menu()
        done = self.await_output(recipe)
        time.sleep(1)
        self.check('mapped_stop_only_blocks_next_batch', 'Batch' not in self.saved() and
                   saved_items(done)[0]['count']==1 and saved_items(done)[1]['count']==1, self.saved())

    def automation(self):
        recipe = self.prepare()
        self.select(); self.close_menu()
        # The two input inventories feed the real NeoForge block capability via
        # vanilla hoppers. No direct insertion into controller slots here.
        self.rc('setblock -2 -59 4 minecraft:hopper[facing=east]')
        self.rc('setblock 0 -59 6 minecraft:hopper[facing=north]')
        for i, item in enumerate(recipe['ingredients']):
            self.rc(f'item replace block -2 -59 4 container.{i} with {item} 2')
        self.rc(f'item replace block 0 -59 6 container.0 with myvillage:low_grade_spirit_stone {recipe["fuel_count"]*2}')
        self.poll(self.saved, lambda data: all(saved_items(data).get(i,{}).get('count')==2 for i in (0,1)),
                  15, 'real hopper herbs')
        self.poll(self.saved, lambda data:saved_items(data).get(2,{}).get('count')==2*recipe['fuel_count'],
                  15, 'real hopper fuel')
        self.check('actual_hoppers_transfer_herbs_and_spirit_stones', True, self.saved())
        # The output is a bottom-layer interface: a real vanilla hopper below
        # pulls from its DOWN capability and deposits into a separate chest.
        self.rc('setblock 1 -62 4 minecraft:chest')
        self.rc('setblock 1 -61 4 minecraft:hopper[facing=down]')
        self.start_batch(); self.stop_next(); self.close_menu()
        product = self.poll(lambda: quality_stacks(self.rc('data get block 1 -62 4 Items')),
                            lambda rows:sum(s['count'] for s in rows.values() if s['id']==recipe['output'])==recipe['output_count'],
                            recipe['duration_ticks']/20+25, 'actual hopper product extraction')
        self.check('actual_hopper_output_conserves_one_batch', 'Batch' not in self.saved() and
                   sum(s['count'] for i,s in saved_items(self.saved()).items() if i>=3)==0, product)
        # Disable extraction, then verify a full output pauses with frozen quality
        # and unchanged input costs. It may reserve a batch but cannot consume it.
        self.rc('setblock 1 -61 4 minecraft:smooth_stone')
        for slot in range(3,7):
            self.slot(slot, recipe['output']+'[myvillage:pill_quality=4]',64)
        before = self.saved(); self.select(); self.click(*POINTS['start'], label='start_full_outputs'); self.close_menu()
        self.poll(self.saved,lambda data:isinstance(data.get('Batch'),dict),8,'full-output frozen batch')
        paused=self.saved(); time.sleep(1); after = self.saved()
        self.check('full_output_does_not_consume_or_advance', after['Progress']==paused['Progress']==0 and
                   frozen_identity(paused)==frozen_identity(after) and saved_items(before)==saved_items(after),
                   {'before':before,'after':after})

    def lifecycle(self):
        recipe = self.prepare(); self.materials(recipe,2); self.start_batch(); self.stop_next(); self.close_menu()
        before = self.poll(self.saved, lambda data:data.get('Progress',0)>5, 8, 'active progress')
        identity = frozen_identity(before)
        self.rc('setblock -1 -58 3 minecraft:air')
        self.poll(self.inspect, lambda data:not data['formed'], 8, 'broken structure pause')
        paused = self.saved(); time.sleep(1); still = self.saved()
        self.check('broken_array_pauses_frozen_batch', frozen_identity(still)==identity and
                   still['Progress']==paused['Progress'] and saved_items(still)==saved_items(paused), still)
        self.rc('save-all flush'); self.stop(); self.start(reuse=True)
        restored = self.saved()
        self.check('real_restart_keeps_batch_and_authorization', frozen_identity(restored)==identity and
                   restored['Progress']==paused['Progress'] and saved_items(restored)==saved_items(paused), restored)
        self.rc('setblock -1 -58 3 myvillage:alchemy_array_casing[facing=north]')
        self.poll(self.inspect, lambda data:data['formed'], 8, 'repaired restart structure')
        done = self.await_output(recipe)
        outputs = [s for i,s in saved_items(done).items() if i>=3]
        self.check('repair_resumes_same_quality_once', len(outputs)==1 and outputs[0]['count']==recipe['output_count']
                   and pill_quality(outputs[0])==identity['Batch']['Quality'], {'frozen':identity,'after':done})
        self.report['restart'] = {'same_world':True,'sessions':self.sessions,'batch_identity_preserved':True}

    def record(self, name, seconds):
        log = (self.out/(name+'.log')).open('w')
        process = subprocess.Popen(['ffmpeg','-hide_banner','-loglevel','error','-y','-f','x11grab',
                                    '-draw_mouse','0','-video_size','960x540','-framerate','20','-i',
                                    self.s.state['display']+'.0+0,0','-t',str(seconds),'-c:v','libx264',
                                    '-preset','veryfast','-crf','22','-pix_fmt','yuv420p',str(self.out/name)],
                                   stdout=log,stderr=subprocess.STDOUT)
        self.recorders.append((process,log))
        return process

    def visual(self):
        recipe = self.prepare(); self.close_menu()
        self.rc('setblock 4 -59 -1 minecraft:smooth_stone')
        self.rc(f'tp {self.s.username} 4.5 -58 -.5 40 15')
        self.s.game.key('F1'); time.sleep(.5); self.capture('array-idle-world.png'); self.s.game.key('F1')
        self.materials(recipe,2); self.start_batch(); self.close_menu()
        self.rc('setblock 4 -59 -1 minecraft:smooth_stone')
        self.rc(f'tp {self.s.username} 4.5 -58 -.5 40 15')
        self.s.game.key('F1'); time.sleep(.5)
        state = self.inspect()
        self.check('running_capture_has_server_activity', state.get('running') is True, state)
        self.capture('array-running-world.png'); recording = self.record('array-running.mp4',5)
        time.sleep(1); self.capture('array-running-later.png'); recording.wait(timeout=15)
        self.s.game.key('F1'); self.open(); self.capture('array-running-menu.png'); self.stop_next(); self.close_menu()
        self.report['visual_review'] = 'pending_screenshot_review'

    def legacy(self):
        recipe = self.prepare(); self.close_menu()
        self.rc('setblock 0 -60 0 myvillage:alchemy_furnace[facing=north]')
        # Reuse the real legacy UI only for this one bounded compatibility case.
        self.rc(f'tp {self.s.username} .5 -60 -2.5 0 20.5')
        time.sleep(.4)
        for i,item in ((1,recipe['ingredients'][0]),(2,recipe['ingredients'][1]),(3,'myvillage:low_grade_spirit_stone')):
            self.rc(f'item replace block 0 -60 0 container.{i} with {item} {recipe["fuel_count"] if i==3 else 1}')
        self.s.game.xdo('click','3'); time.sleep(.5)
        self.poll(self.s.game.pointer_grabbed, lambda grabbed:grabbed is False, 8, 'legacy furnace menu')
        self.capture('legacy-single-furnace-menu.png')
        # Legacy selection and start points are read from its established screen.
        self.click(54,50,label='legacy_select_learned_formula',dimensions=(304,222))
        self.click(200,113,label='legacy_manual_start',dimensions=(304,222)); self.close_menu()
        result = self.poll(lambda:read_data(self.rc('data get block 0 -60 0')),
                           lambda data:any(row['id']==recipe['output'] for row in data.get('Items',[])),
                           recipe['duration_ticks']/20+15,'legacy manual furnace output')
        self.check('legacy_single_furnace_still_produces', True, result)

    def export_world(self):
        world = cs.SERVER_DIR/cs.LEVEL_NAME
        with zipfile.ZipFile(self.out/'alchemy_array_preview_world.zip','w',zipfile.ZIP_DEFLATED) as archive:
            for file in sorted(world.rglob('*')):
                rel = file.relative_to(world)
                if file.is_file() and not file.is_symlink() and not any(part in
                        ('session.lock','stats','advancements') for part in rel.parts):
                    archive.write(file, '青铜丹炉阵独立展示/'+rel.as_posix())
        self.report['world_export'] = {'file':'alchemy_array_preview_world.zip',
                                     'isolated_world':True,'owner_saves_modified':False}

    def run(self):
        self.start()
        for command in (*scene.world_rules(),*scene.player_setup(self.s.username,'minecraft:air'),
                        'myvillage world pause','forceload add -32 -32 80 32','tick rate 20'):
            self.rc(command)
        for case in self.cases:
            getattr(self,case)()
            self.report['completed'].append(case); self.report['unrun'].remove(case); self.save()
        # Leave a working showcase instead of the missing-block native fixtures.
        recipe=self.prepare(); self.select(); self.close_menu(); self.materials(recipe,8)
        self.rc('setblock 3 -60 0 minecraft:chest')
        for index,(item,count) in enumerate([(recipe['formula'],1),
                *[(item,32) for item in recipe['ingredients']],('myvillage:low_grade_spirit_stone',32),
                ('myvillage:alchemy_array_casing',21),('myvillage:alchemy_array_controller',1),
                ('myvillage:alchemy_array_input',1),('myvillage:alchemy_array_output',1),
                ('myvillage:alchemy_array_fuel',1),('myvillage:alchemy_array_vent',1),('minecraft:hopper',8)]):
            self.rc(f'item replace block 3 -60 0 container.{index} with {item} {count}')
        self.rc('setworldspawn 0 -60 -1 0'); self.rc('save-all flush'); self.stop()
        self.export_world(); self.report['status']='pass'; self.save()

    def shutdown(self):
        for process,log in self.recorders:
            if process.poll() is None:
                process.terminate(); process.wait(timeout=15)
            log.close()
        if self.s:
            self.stop()
        self.save()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',type=Path,default=ROOT/'out/preview/alchemy_array/runtime')
    parser.add_argument('--cases',default='all')
    args = parser.parse_args()
    evidence = ArrayEvidence(args.out,select_array_cases(args.cases))
    try:
        evidence.run()
    except BaseException as error:
        evidence.report['status']='fail'; evidence.report['failures'].append(repr(error)); evidence.save()
        raise
    finally:
        evidence.shutdown()


if __name__=='__main__':
    main()
