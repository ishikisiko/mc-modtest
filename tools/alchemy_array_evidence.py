#!/usr/bin/env python3
"""Disposable-world array evidence using real mouse/key input and server readback.

Run in a disposable cloud world, never on an owner save. Commands only
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
                           partial_active_component_unload={'status':'not_verified'},
                           method='real mapped mouse/key; administrator fixtures and readback; isolated disposable Colab world')

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
            self.s.game.xdo('key', 'Escape')
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
        expected = item.split('[', 1)[0]
        self.poll(lambda: saved_items(self.saved(origin)).get(index, {}),
                  lambda row: row.get('id') == expected and row.get('count') == count,
                  3, 'administrator input slot readback')

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
        for command in ('kill @e[type=minecraft:item]',f'clear {u}', f'gamemode survival {u}', f'myvillage cultivation awaken {u}',
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
        # Input slots intentionally reject items before a learned formula is
        # selected. Establish that through the real menu before administrative
        # material placement, rather than silently ignoring /item refusals.
        if self.saved().get('SelectedRecipe') != recipe['id']:
            self.select()
            self.close_menu()
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
                       ('broken_rejected','replacement_rejected','unused_replacement_rejected','fresh_accepted','restored')),stale)
        # A forced ticking chunk also retains its neighbors at FULL status.
        # Verify full empty-machine unload/reload, using the same hasChunkAt
        # predicate as production, without claiming a partial active unload.
        boundary=(479,-59,3)
        self.rc('forceload add 464 0 495 15'); self.place_fixture(boundary)
        self.poll(lambda:self.inspect(boundary),lambda data:data['formed'],10,'remote array initially loaded')
        before=self.saved(boundary)
        self.check('unload_fixture_is_empty_and_inactive',not saved_items(before) and 'Batch' not in before,before)
        self.rc(f'tp {self.s.username} .5 -60 .5')
        self.rc('forceload remove 464 0 495 15')
        for location in (boundary,(480,-59,4)):
            self.poll(lambda location=location:self.one('myvillage array loaded '+position(location),'ALCHEMY_ARRAY_LOADED'),
                      lambda data:data.get('loaded') is False,45,'actual complete array unload '+position(location))
        self.report['partial_active_component_unload']={
            'status':'not_verified',
            'reason':'A ticking controller ticket retains adjacent component chunks at FULL; full empty-machine unload tested separately.'}
        self.rc('forceload add 464 0 495 15')
        state=self.poll(lambda:self.inspect(boundary),lambda data:data.get('formed') is True,20,'remote array reload')
        after=self.saved(boundary)
        self.check('native_whole_empty_array_unload_reload_preserves_identity',
                   before['Identity']==after['Identity'] and not saved_items(after) and 'Batch' not in after,state)

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

    def controller_boundary(self):
        recipe=self.prepare(); self.select(); self.close_menu()
        self.open(); self.click(*POINTS['blueprint'],label='final_blueprint_orientation')
        for layer,x in [('bottom',28),('middle',70),('top',112)]:
            self.click(x,57,label='final_blueprint_'+layer)
            self.capture('final-blueprint-'+layer+'.png')
        self.close_menu()
        probe=self.one('myvillage array port '+position(ORIGIN),'ALCHEMY_ARRAY_PORT')
        self.check('controller_does_not_publish_any_item_capability',
                   all(not side['available'] for side in probe['sides']),probe)
        # This hopper touches the controller's front, not an item port. Vanilla
        # has a Container fallback even when no ItemHandler was advertised.
        self.rc('setblock 0 -59 2 minecraft:hopper[facing=south]')
        self.rc('item replace block 0 -59 2 container.0 with '+recipe['ingredients'][0]+' 8')
        try:
            self.rc('tick freeze')
            before_time=int(self.number('time query gametime'))
            self.rc('tick step 24')
            self.poll(lambda:int(self.number('time query gametime')),lambda tick:tick>=before_time+24,
                      8,'direct-controller hopper insertion attempts')
            donor=quality_stacks(self.rc('data get block 0 -59 2 Items'))
            self.check('vanilla_hopper_cannot_insert_directly_into_controller',
                       not saved_items(self.saved()) and donor.get(0,{}).get('count')==8,donor)
            # Replacing the bottom casing with an extractor also breaks the
            # structure. Neither idle inputs nor finished outputs may leak.
            self.rc('setblock 0 -60 3 minecraft:hopper[facing=down]')
            self.slot(0,recipe['ingredients'][0],3)
            self.rc('data modify block '+position(ORIGIN)+' Items append value '
                    +json.dumps({'Slot':3,'id':recipe['output'],'count':2}))
            before=saved_items(self.saved())
            self.check('controller_extraction_fixture_has_inputs_and_finished_output',
                       before.get(0,{}).get('count')==3 and before.get(3,{}).get('count')==2,before)
            before_time=int(self.number('time query gametime'))
            self.rc('tick step 24')
            self.poll(lambda:int(self.number('time query gametime')),lambda tick:tick>=before_time+24,
                      8,'direct-controller hopper extraction attempts')
            extracted=quality_stacks(self.rc('data get block 0 -60 3 Items'))
            self.check('vanilla_hopper_cannot_extract_controller_inputs_or_outputs',
                       not extracted and saved_items(self.saved())==before,
                       {'controller':self.saved(),'extractor':extracted})
        finally:
            self.rc('tick unfreeze')
        self.rc('setblock 0 -59 2 minecraft:air')
        self.rc('setblock 0 -60 3 myvillage:alchemy_array_casing[facing=north]')
        self.poll(self.inspect,lambda state:state['formed'],8,'controller boundary fixture repaired')

    def hopper_feed(self, recipe, batches=1):
        """Administrator puts supply into donors; real hoppers perform transfer."""
        for i,item in enumerate(recipe['ingredients']):
            self.rc(f'item replace block -2 -59 4 container.{i} with {item} {batches}')
        self.rc(f'item replace block 0 -59 6 container.0 with myvillage:low_grade_spirit_stone {recipe["fuel_count"]*batches}')

    def chest_products(self):
        return quality_stacks(self.rc('data get block 1 -62 4 Items'))

    def automation_stores(self):
        """Read every owned donor, receiver, output hopper and controller slot."""
        stores={'controller':saved_items(self.saved())}
        for label,location in (('herb_hopper','-2 -59 4'),('fuel_hopper','0 -59 6'),
                               ('output_hopper','1 -61 4'),('chest','1 -62 4')):
            stores[label]=quality_stacks(self.rc('data get block '+location+' Items'))
        return stores

    def automation(self):
        recipe = self.prepare()
        self.select(); self.close_menu()
        self.rc('setblock -2 -59 4 minecraft:hopper[facing=east]')
        self.rc('setblock 0 -59 6 minecraft:hopper[facing=north]')
        self.rc('setblock 1 -62 4 minecraft:chest')
        self.rc('setblock 1 -61 4 minecraft:hopper[facing=down]')
        self.hopper_feed(recipe)
        self.poll(self.saved,lambda data:all(saved_items(data).get(i,{}).get('count')==1 for i in (0,1))
                  and saved_items(data).get(2,{}).get('count')==recipe['fuel_count'],15,'first real hopper supply')
        self.check('actual_hoppers_transfer_herbs_and_spirit_stones',True,self.saved())
        self.start_batch(); self.close_menu()
        self.poll(self.saved,lambda data:0<data.get('Progress',0)<recipe['duration_ticks']-20,
                  8,'active first batch before controlled hopper top-up')
        # Freeze only world ticking while putting the second supply in the
        # donors. Real step ticks then move it through vanilla hoppers while the
        # first batch remains active, before ordinary ticking resumes.
        self.rc('tick freeze')
        try:
            before=self.saved(); identity=frozen_identity(before)
            self.hopper_feed(recipe)
            self.rc('tick step 20')
            topped=self.poll(self.saved,lambda data:all(saved_items(data).get(i,{}).get('count')==2 for i in (0,1))
                              and saved_items(data).get(2,{}).get('count')==2*recipe['fuel_count']
                              and data.get('Progress',0)>=before['Progress']+20,10,'active hopper top-up')
            self.check('active_hopper_topup_retains_same_batch_and_reserved_costs',
                       frozen_identity(topped)==identity and topped['Progress']==before['Progress']+20<recipe['duration_ticks'],
                       {'before':before,'after':topped})
        finally:
            self.rc('tick unfreeze')
        count=lambda rows:sum(stack['count'] for stack in rows.values() if stack['id']==recipe['output'])
        product=self.poll(self.chest_products,lambda rows:count(rows)==2*recipe['output_count'],
                          2*recipe['duration_ticks']/20+25,'two automatically consecutive batches')
        waiting=self.poll(self.inspect,lambda data:data.get('enabled') is True and data.get('status')==1
                          and not data.get('batch'),10,'enabled waiting for materials')
        stores=self.automation_stores()
        self.check('continuous_two_batches_conserve_inputs_and_output',
                   all(not rows for label,rows in stores.items() if label!='chest')
                   and count(product)==2*recipe['output_count'],{'waiting':waiting,'stores':stores})
        # After an actual material shortage, a fresh donor refill must start a
        # third batch without another menu selection or start intent.
        self.hopper_feed(recipe)
        third=self.poll(self.saved,lambda data:isinstance(data.get('Batch'),dict),15,'automatic restart from hopper refill')
        self.check('shortage_refill_starts_without_new_ui_authorization',third.get('Enabled') is not None
                   and bool(third['Enabled']) and third['Authorizer']==identity['Authorizer'],third)
        self.stop_next(); self.close_menu()
        product=self.poll(self.chest_products,lambda rows:count(rows)==3*recipe['output_count'],
                          recipe['duration_ticks']/20+25,'third stopped-after-current hopper output')
        final=self.poll(self.saved,lambda data:'Batch' not in data and not data.get('Enabled'),10,'third batch retired')
        stores=self.automation_stores()
        self.check('three_supplied_batches_have_exact_output_and_no_residual_costs',
                   all(not rows for label,rows in stores.items() if label!='chest')
                   and count(product)==3*recipe['output_count'],{'controller':final,'stores':stores})
        self.report['continuous_automation']={'supplied_batches':3,'completed_batches':3,
            'mapped_start_count':1,'active_topup_batch_preserved':True,'shortage_refill_auto_resumed':True,
            'output_count':count(product),'stopped_after_current':True}
        # This independent fixture prevents extraction or earlier production from
        # hiding a full-output transaction error. Fill all four quality variants.
        recipe=self.prepare(); self.select(); self.close_menu(); self.materials(recipe)
        for quality,slot in enumerate(range(3,7),1):
            # /item replace honors canPlaceItem, so intentionally closed output
            # slots need an explicit NBT fixture. Verify it before testing pause.
            stack={'Slot':slot,'id':recipe['output'],'count':64}
            if quality>1: stack['components']={'myvillage:pill_quality':quality}
            self.rc('data modify block '+position(ORIGIN)+' Items append value '+json.dumps(stack))
        before=self.saved()
        filled=saved_items(before)
        self.check('full_output_fixture_has_all_four_quality_stacks',
                   all(filled.get(slot,{}).get('count')==64
                       and filled[slot]['id']==recipe['output'] and pill_quality(filled[slot])==slot-2
                       for slot in range(3,7)),before)
        self.start_batch(); self.close_menu()
        paused=self.saved(); time.sleep(1); after=self.saved()
        self.check('full_output_does_not_consume_or_advance',after['Progress']==paused['Progress']==0
                   and frozen_identity(paused)==frozen_identity(after) and saved_items(before)==saved_items(after),
                   {'before':before,'after':after})
        # Drain through a real hopper. Snapshot all stores under tick freeze so
        # transfer between two readbacks cannot masquerade as loss or gain.
        self.rc('setblock 1 -62 4 minecraft:air')
        self.rc('setblock 1 -62 4 minecraft:chest')
        self.rc('setblock 1 -61 4 minecraft:hopper[facing=down]')
        try:
            self.rc('tick rate 80')
            self.poll(self.saved,lambda data:'Batch' not in data
                      and all(slot not in saved_items(data) for slot in range(3)),
                      recipe['duration_ticks']/80+20,'blocked batch resumes after real hopper extraction')
            self.rc('tick freeze')
            total=sum(row['count'] for row in saved_items(self.saved()).values())
            total+=sum(row['count'] for row in quality_stacks(self.rc('data get block 1 -61 4 Items')).values())
            total+=sum(row['count'] for row in self.chest_products().values())
            self.check('unblocked_output_finishes_once_without_loss',total==256+recipe['output_count'],
                       {'total_finished_items':total,'initial_finished_items':256,'batch_output':recipe['output_count']})
        finally:
            self.rc('tick rate 20'); self.rc('tick unfreeze')

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
        # Spectator is only a fixed observation camera. Real recipe/menu input
        # below still runs in survival; no flight/gravity drift between frames.
        self.rc(f'gamemode spectator {self.s.username}')
        self.rc(f'tp {self.s.username} 5.2 -58 -3.2 38 10')
        self.s.game.key('F1'); time.sleep(.5); self.capture('array-idle-world.png')
        self.rc(f'tp {self.s.username} 3.5 -57.8 -1.0 29 13')
        time.sleep(.5); self.capture('array-hero.png')
        self.rc(f'tp {self.s.username} -5.2 -58 -3.2 322 10')
        time.sleep(.5); self.capture('array-input-side.png')
        self.rc(f'tp {self.s.username} -5.2 -58 11.2 218 10')
        time.sleep(.5); self.capture('array-rear-world.png')
        self.rc(f'tp {self.s.username} 5.2 -58 -3.2 38 10')
        time.sleep(.4)
        # The formed shell is intentionally reversible: break one casing, then
        # capture the same camera while it re-assembles after the real repair.
        self.rc('setblock -1 -58 3 minecraft:air')
        self.poll(self.inspect, lambda data:not data['formed'], 8, 'visual broken shell')
        time.sleep(.3); self.capture('array-unformed.png')
        recording = self.record('array-assembly.mp4',4)
        self.rc('setblock -1 -58 3 myvillage:alchemy_array_casing[facing=north]')
        self.poll(self.inspect, lambda data:data['formed'], 8, 'visual assembled shell')
        time.sleep(.12); self.capture('array-assembling-early.png')
        time.sleep(.4); self.capture('array-assembling-mid.png')
        time.sleep(1.0); self.capture('array-assembled.png')
        recording.wait(timeout=15)
        # The component mask must also unwind when the controller itself is removed.
        self.rc('setblock 0 -59 3 minecraft:air')
        time.sleep(.8); self.capture('array-controller-removed.png')
        self.rc('setblock 0 -59 3 myvillage:alchemy_array_controller[facing=north]')
        self.poll(self.inspect, lambda data:data['formed'], 8, 'controller restored')
        time.sleep(2.3); self.capture('array-controller-restored.png')
        self.s.game.key('F1')
        self.rc(f'gamemode survival {self.s.username}')
        self.materials(recipe,2); self.start_batch(); self.close_menu()
        self.rc(f'gamemode spectator {self.s.username}')
        self.rc(f'tp {self.s.username} 5.2 -58 -3.2 38 10')
        self.s.game.key('F1'); time.sleep(.5)
        state = self.inspect()
        self.check('running_capture_has_server_activity', state.get('running') is True, state)
        self.capture('array-running-world.png'); recording = self.record('array-running.mp4',5)
        time.sleep(1); self.capture('array-running-later.png'); recording.wait(timeout=15)
        self.rc('time set midnight')
        self.check('night_fixture_server_time', '18000' in self.rc('time query daytime'),
                   {'expected_daytime':18000})
        # Time packets are periodic, so a sub-second capture can still be day.
        time.sleep(2.0); self.capture('array-running-night.png')
        self.rc('time set noon'); time.sleep(1.2)
        self.rc(f'gamemode survival {self.s.username}')
        self.s.game.key('F1'); self.open(); self.capture('array-running-menu.png'); self.stop_next(); self.close_menu()
        # Render-only directional samples; fixture placement is administrator
        # input, not a claim of player assembly or four-way gameplay coverage.
        self.rc(f'gamemode spectator {self.s.username}')
        self.s.game.key('F1')
        for facing, yaw in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
            self.place_fixture(facing=facing)
            self.poll(self.inspect, lambda data:data['formed'], 8, 'directional formed shell')
            cx, cy, cz = world_pos(ORIGIN, facing, {'x':4.7, 'y':1, 'z':-6.7})
            self.rc(f'tp {self.s.username} {cx+.5} {cy} {cz+.5} {yaw+38} 10')
            time.sleep(2.3); self.capture('array-facing-'+facing+'.png')
        self.s.game.key('F1')
        self.rc(f'gamemode survival {self.s.username}')
        # A new client and server load an already formed saved array: catches
        # stale/missing chunk-model masking on load, beyond a forced rerender.
        self.place_fixture()
        self.poll(self.inspect, lambda data:data['formed'], 8, 'saved formed fixture')
        self.rc('save-all flush'); self.stop(); self.start(reuse=True)
        self.poll(self.inspect, lambda data:data['formed'], 20, 'formed after world restart')
        self.rc(f'gamemode spectator {self.s.username}')
        self.rc(f'tp {self.s.username} 3.5 -57.8 -1.0 29 13')
        self.s.game.key('F1'); time.sleep(2.3); self.capture('array-reloaded.png')
        self.s.game.key('F1'); self.rc(f'gamemode survival {self.s.username}')
        self.check('formed_visual_fixture_survives_restart', self.inspect()['formed'],
                   {'visual_review':'pending_screenshot_review','scope':'component visibility, not inventory lifecycle'})
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

    def two_players(self):
        from tools.alchemy_array_multiplayer import run_multiplayer
        run_multiplayer(self)

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
        # Replace the buried test chest with an accessible, preconnected output.
        for command in ('setblock 1 -62 4 minecraft:stone',
                        'setblock 2 -61 4 minecraft:chest',
                        'setblock 1 -61 4 minecraft:hopper[facing=east]',
                        'setblock -2 -59 4 minecraft:hopper[facing=east]',
                        'setblock -2 -58 4 minecraft:chest',
                        'setblock 0 -59 6 minecraft:hopper[facing=north]',
                        'setblock 0 -58 6 minecraft:chest',
                        'kill @e[type=minecraft:item]'):
            self.rc(command)
        self.rc('setblock 3 -60 0 minecraft:chest')
        for index,(item,count) in enumerate([(recipe['formula'],1),
                *[(item,32) for item in recipe['ingredients']],('myvillage:low_grade_spirit_stone',32),
                ('myvillage:alchemy_array_casing',21),('myvillage:alchemy_array_controller',1),
                ('myvillage:alchemy_array_input',1),('myvillage:alchemy_array_output',1),
                ('myvillage:alchemy_array_fuel',1),('myvillage:alchemy_array_vent',1),('minecraft:hopper',8)]):
            self.rc(f'item replace block 3 -60 0 container.{index} with {item} {count}')
        # New owner UUIDs must be able to awaken and learn in the exported world.
        # Keep this handoff station outside the disposable machine fixture bounds.
        for command in ('fill -8 -61 -1 -5 -61 5 minecraft:smooth_stone',
                        'fill -8 -60 -1 -5 -57 5 minecraft:air',
                        'setblock -6 -60 0 myvillage:spirit_testing_stele',
                        'setblock -6 -60 2 myvillage:technique_inheritance_stele',
                        'setblock -6 -60 4 minecraft:chest'):
            self.rc(command)
        supplies=[('myvillage:cultivation_handbook',1),('minecraft:iron_pickaxe',1),('minecraft:bread',64),
                  ('myvillage:low_grade_spirit_stone',64)]
        supplies.extend((definition['formula'],1) for definition in self.rules['recipes'])
        supplies.extend((herb,32) for definition in self.rules['recipes'] for herb in definition['ingredients'])
        for index,(item,count) in enumerate(supplies):
            self.rc(f'item replace block -6 -60 4 container.{index} with {item} {count}')
        self.check('showcase_awaken_and_formula_supplies_exist',
                   'Test passed' in self.rc('execute if block -6 -60 0 myvillage:spirit_testing_stele')
                   and len(quality_stacks(self.rc('data get block -6 -60 4 Items')))==len(supplies),
                   {'testing_stele':[-6,-60,0],'inheritance_stele':[-6,-60,2],'supplies':[-6,-60,4]})
        self.check('showcase_is_formed_with_accessible_output_chest',self.inspect()['formed']
                   and 'Test passed' in self.rc('execute if block 1 -61 4 minecraft:hopper[facing=east]')
                   and 'Test passed' in self.rc('execute if block 2 -61 4 minecraft:chest'),
                   {'herb_chest':[-2,-58,4],'fuel_chest':[0,-58,6],'output_chest':[2,-61,4]})
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
