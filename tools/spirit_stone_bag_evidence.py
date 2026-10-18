#!/usr/bin/env python3
"""Focused bag capture: mapped use/meditation/forging, actual pickup, player save/restart and death drops."""
import argparse
import json
from pathlib import Path
import sys
import time
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from tools.forging_evidence import ForgingEvidence,POS
from tools.alchemy_quality_evidence import read_data
from tools.combat_capture import scene,session as cs
BAG='myvillage:spirit_stone_bag'
STONE='myvillage:low_grade_spirit_stone'
AUTO='myvillage:spirit_bag_auto_collect'
LEDGER='myvillage:spirit_stone_balance'
HUGE=10**40+17

def stored(stack):
    if stack.get('id')!=BAG:return 0
    contents=stack.get('components',{}).get('minecraft:container',[])
    if not isinstance(contents,list):raise ValueError('Invalid saved bag contents')
    legacy=sum(row['item']['count'] for row in contents if row['item']['id']==STONE)
    return legacy+sum(int(row['quantity']) for row in stack.get('components',{}).get(LEDGER,[]) if row['item']['id']==STONE)

def bag_spec(count):
    return BAG+'[minecraft:container=[{slot:0,item:{id:"'+STONE+'",count:'+str(count)+'}}]]'

def counted_bag_spec(quantity):
    return BAG+'['+LEDGER+'=[{item:{id:"'+STONE+'",count:1},quantity:"'+str(quantity)+'"}]]'

class BagEvidence(ForgingEvidence):
    def __init__(self,out):
        super().__init__(out)
        self.report.update(kind='spirit_stone_bag',selected=['collection','mapped_use','full_inventory_pickup','offhand_meditation','bag_forging','legacy_migration','huge_balance','restart','death_drop'],completed=[],
            method='actual mapped bag right-click, sneak-use, B/X meditation and forging menu; RCON fixtures/readback; actual player file restart',
            commerce_menu='not_verified; bag transaction planner is covered by Java tests',accessory_mod='not_installed; provider API only')
    def balance(self):return sum(stored(stack) for stack in self.inventory().values())
    def bag(self):return next(stack for stack in self.inventory().values() if stack['id']==BAG)
    def wait_sneak(self,expected):
        predicate=json.dumps({'condition':'minecraft:entity_properties','entity':'this',
                              'predicate':{'flags':{'is_sneaking':expected}}},separators=(',',':'))
        command=f'execute as {self.s.username} if predicate {predicate} run data get entity @s SelectedItemSlot'
        self.poll(lambda:self.rc(command),lambda reply:'data: ' in reply,5,'mapped sneak state '+str(expected))
    def use(self,shift=False):
        self.s.game.require_ingame('bag use')
        if shift:self.s.game.xdo('keydown','Shift_L')
        self.wait_sneak(shift)
        try:self.s.game.xdo('click','3');time.sleep(.35)
        finally:
            if shift:
                self.s.game.xdo('keyup','Shift_L')
                # Key-up is processed on the next client tick; don't race the next use.
                self.wait_sneak(False)
        self.report.setdefault('use_readbacks',[]).append({'shift':shift,'inventory':self.inventory()})
    def preview(self):
        self.report.update(selected=['bag_forging_cost_display'],completed=[],kind='spirit_stone_bag_forging_display')
        self.start();u=self.s.username;g=self.s.game
        for cmd in (*scene.world_rules(),*scene.player_setup(u,'minecraft:air'),'myvillage world pause',
                    f'myvillage cultivation awaken {u}',f'myvillage cultivation initiate {u}',f'tp {u} .5 -60 .5 180 -60'):
            self.rc(cmd)
        self.learn('qingfeng_sword');self.rc(f'clear {u}')
        for cmd in [f'setblock {POS} myvillage:forging_table',f'item replace entity {u} hotbar.8 with {bag_spec(30)}',
                    f'give {u} minecraft:diamond 2',f'give {u} minecraft:stick 1',f'give {u} myvillage:edge_tempering_sand 1',
                    f'myvillage cultivation crafting set {u} forging 0']:
            self.rc(cmd)
        g.key('1');self.open_table();time.sleep(1);self.capture('forging-bag-costs-ready.png')
        self.check('display_fixture_has_bag_only_currency',self.balance()==HUGE and self.count(STONE)==0,self.inventory())
        self.click(152,137);time.sleep(.5);self.close_menu()
        self.check('same_cost_still_pays',self.balance()==HUGE-2 and self.count('myvillage:qingfeng_sword')==1,self.inventory())
        self.report['completed'].append('bag_forging_cost_display');self.report['status']='pass'

    def run(self):
        self.start();u=self.s.username;g=self.s.game
        options=cs.CLIENT_DIR/'options.txt';bindings=dict(line.split(':',1) for line in options.read_text().splitlines() if ':' in line)
        for action,key in [('start_spirit_meditation','b'),('stop_meditation','x')]:
            actual=bindings.get('key_key.myvillage.'+action,'key.keyboard.'+key)
            self.check('binding_'+action,actual=='key.keyboard.'+key,actual)
        for cmd in (*scene.world_rules(),*scene.player_setup(u,'minecraft:air'),'myvillage world pause',f'tp {u} .5 -60 .5 180 -60',f'item replace entity {u} hotbar.0 with {BAG}',f'give {u} {STONE} 20'):
            self.rc(cmd)
        g.key('1');self.poll(self.balance,lambda n:n==20,10,'server bag collection')
        self.check('no_loose_stones_after_collection',self.count(STONE)==0,self.inventory());self.report['completed'].append('collection')
        self.use();self.poll(lambda:self.bag().get('components',{}).get('minecraft:custom_data',{}).get(AUTO),lambda value:value==0,5,'mapped collection toggle')
        self.rc(f'give {u} {STONE} 3');time.sleep(.8)
        self.check('pause_leaves_loose_stones',self.count(STONE)==3 and self.balance()==20,self.inventory())
        self.use();self.poll(self.balance,lambda n:n==23,5,'resume collection')
        self.use(shift=True);self.poll(lambda:self.count(STONE),lambda n:n==23,5,'mapped bag withdrawal')
        self.check('withdraw_preserves_total',self.balance()==0,self.inventory())
        self.use();self.poll(self.balance,lambda n:n==23,5,'recollect withdrawal')
        g.key('e');time.sleep(.6);self.capture('bag-in-inventory.png');g.xdo('key','Escape');time.sleep(.3)
        self.report['completed'].append('mapped_use')
        self.rc(f'clear {u}');self.rc(f'item replace entity {u} weapon.offhand with {bag_spec(30)}')
        for i in range(36):self.rc(f'item replace entity {u} inventory.{i-9} with minecraft:stone 64' if i>=9 else f'item replace entity {u} hotbar.{i} with minecraft:stone 64')
        self.rc('summon minecraft:item .5 -59.8 .5 {Tags:["bag_fixture_pickup"],PickupDelay:0s,Item:{id:"'+STONE+'",count:7}}')
        self.poll(self.balance,lambda n:n==37,10,'full inventory offhand pickup')
        self.check('pickup_did_not_need_a_free_slot',len([slot for slot in self.inventory() if 0<=slot<36])==36,self.inventory())
        self.report['completed'].append('full_inventory_pickup')
        self.check('legacy_bag_migrated_once',LEDGER in self.bag().get('components',{}) and 'minecraft:container' not in self.bag().get('components',{}),self.bag())
        self.report['completed'].append('legacy_migration')
        for cmd in [f'myvillage cultivation awaken {u}',f'myvillage cultivation initiate {u}',f'myvillage cultivation setrealm {u} myvillage:qi_refining myvillage:qi_refining_1',f'tp {u} .5 -60 .5 180 -60']:
            self.rc(cmd)
        before=self.balance();time.sleep(1);g.key('b');self.poll(self.balance,lambda n:n<before,15,'mapped spirit meditation bag payment');g.key('x');time.sleep(.4)
        self.check('meditation_only_spends_bag',0<self.balance()<before and self.count(STONE)==0,self.inventory())
        self.report['completed'].append('offhand_meditation')
        self.rc(f'clear {u}');self.learn('qingfeng_sword');self.rc(f'clear {u}')
        for cmd in [f'setblock {POS} myvillage:forging_table',f'item replace entity {u} hotbar.8 with {counted_bag_spec(HUGE)}',f'give {u} minecraft:diamond 2',f'give {u} minecraft:stick 1',f'give {u} myvillage:edge_tempering_sand 1',f'myvillage cultivation crafting set {u} forging 0']:
            self.rc(cmd)
        g.key('1');before=self.balance();self.open_table();self.click(152,137);time.sleep(.5);self.capture('forging-from-bag.png');self.close_menu()
        self.check('forging_spent_exactly_two_bag_stones',self.balance()==before-2,self.inventory())
        self.check('forged_weapon_returned_once',self.count('myvillage:qingfeng_sword')==1,self.inventory());self.report['completed'].append('bag_forging')
        self.check('huge_balance_has_one_entry',self.balance()==HUGE-2 and len(self.bag()['components'][LEDGER])==1,self.bag())
        # Withdraw one legal stack using actual input, then recollect; no expansion of the balance.
        g.key('9');self.use(shift=True);self.poll(lambda:self.count(STONE),lambda n:n==64,5,'huge balance bounded withdrawal')
        self.check('huge_withdrawal_exact',self.balance()==HUGE-66,self.bag())
        self.use();self.poll(self.balance,lambda n:n==HUGE-2,5,'huge balance recollection')
        self.report['completed'].append('huge_balance')
        saved=self.inventory();self.rc('save-all flush');self.stop();self.start(reuse=True)
        self.check('real_player_restart_keeps_bag_and_items',self.inventory()==saved,self.inventory());self.report['completed'].append('restart')
        self.rc('gamerule keepInventory false');self.rc('gamerule doImmediateRespawn false');self.rc(f'kill {u}')
        def dropped():
            reply=self.rc('data get entity @e[type=minecraft:item,nbt={Item:{id:"'+BAG+'"}},limit=1] Item')
            return read_data(reply) if 'data: ' in reply else {}
        drop=self.poll(dropped,lambda stack:stack.get('id')==BAG,8,'dropped bag after real player death')
        self.check('death_drop_keeps_bag_contents',stored(drop)==HUGE-2,drop);self.report['completed'].append('death_drop')
        self.report['status']='pass'

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,default=ROOT/'out/preview/spirit_stone_bag/runtime');p.add_argument('--preview-only',action='store_true');a=p.parse_args();e=BagEvidence(a.out)
    try:
        if a.preview_only:e.preview()
        else:e.run()
    except BaseException as error:e.report['status']='fail';e.report['failures'].append(repr(error));e.save();raise
    finally:
        if e.s:e.stop()
        e.save()
if __name__=='__main__':main()
