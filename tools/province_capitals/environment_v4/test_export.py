import copy
import gzip
import json
from pathlib import Path
import struct
import tempfile
import unittest

from province_capitals.environment_v3.world import World,AIR
from .export_game import read,ENV,DESIGN,POLICY,CATALOG,source_cells,replace


class FurnitureReplacementTest(unittest.TestCase):
    def setUp(self):
        temporary=tempfile.TemporaryDirectory();self.addCleanup(temporary.cleanup);self.root=Path(temporary.name)
        terrain=self.root/'data/myvillage/capitals/v3/test/terrain.bin.gz';terrain.parent.mkdir(parents=True);terrain.write_bytes(gzip.compress(struct.pack('<'+'h'*1024,*([3]*1024)),mtime=0))
        self.manifest={'size':[32,40,32],'terrain':{'resource':'myvillage:capitals/v3/test/terrain.bin.gz','top_block':'minecraft:stone','fill_block':'minecraft:stone'},'tiles':[],
                       'life':{'places':[]},'entries':[],'roads':[],'access_paths':[],'buildings':[],'environment':{'decorations':[]}}
        self.config=read(ENV/'materials.json');self.design=read(DESIGN);self.policy=read(POLICY);self.catalog=read(CATALOG)
        self.world=World(self.root,copy.deepcopy(self.manifest),self.config);self.base=World(self.root,copy.deepcopy(self.manifest),self.config)

    def fixture(self,ident,recipe,origin,scale):
        record={'id':ident,'recipe':recipe,'origin':origin,'scale':scale,'rotation_quarters':0};self.world.manifest['environment']['decorations'].append(record)
        for pos,state in source_cells(record,self.design,self.config['materials']).items():self.world.set(pos,state,'旧设施')
        return record

    def clean_start(self):self.world.edit_source.clear();self.world.changes.clear();self.world.dirty.clear()

    def test_both_fixtures_become_complete_custom_blocks(self):
        self.fixture('lamp','rune_lamp',[6,4,6],.4);self.fixture('rack','drying_rack',[20,4,20],.55);self.clean_start()
        records=replace(self.world,{},self.design,self.policy,self.catalog,self.base)
        self.assertEqual(2,len(records));self.assertEqual([2,4],[len(r['new']) for r in records])
        self.assertTrue(all(self.world.get(p)==state for r in records for p,state in r['new'].items()))
        self.assertTrue(all(d['kind']=='custom_furniture' for d in self.world.manifest['environment']['decorations']))

    def test_gate_orientation_avoids_existing_barrel_without_dropping_fixture(self):
        self.fixture('garden_1_晒架','drying_rack',[10,4,10],.55)
        barrel=(10,4,9);state='minecraft:barrel[facing=up,open=false]';self.world.set(barrel,state,'原货桶');self.clean_start()
        plan={'gardens':[{'id':'garden_1','bounds':[6,6,14,14],'gate':[14,10]}]}
        records=replace(self.world,plan,self.design,self.policy,self.catalog,self.base)
        self.assertEqual(1,len(records));self.assertEqual('east',records[0]['facing']);self.assertEqual([0,0,1],records[0]['relocation'])
        self.assertEqual(state,self.world.get(barrel));self.assertEqual(4,len(records[0]['new']))

    def test_blocked_footprint_is_rejected_when_no_authorized_offset_exists(self):
        self.fixture('garden_1_晒架','drying_rack',[10,4,10],.55);self.world.set((10,4,9),'minecraft:barrel[facing=up,open=false]','原货桶');self.clean_start()
        policy=copy.deepcopy(self.policy);policy['placement_offsets']=[[0,0]]
        with self.assertRaisesRegex(ValueError,'没有合法空间'):
            replace(self.world,{'gardens':[{'id':'garden_1','bounds':[6,6,14,14],'gate':[14,10]}]},self.design,policy,self.catalog,self.base)

    def test_real_building_member_matching_an_old_fixture_material_is_preserved(self):
        self.fixture('lamp','rune_lamp',[10,4,10],.4);state=self.config['materials']['slate'];pos=(10,4,10)
        self.base.set(pos,state,'原房屋石构');self.world.manifest['buildings']=[{'id':'house','position':[10,4,10],'bounds':{'min':[10,4,10],'max':[10,4,10]}}];self.clean_start()
        records=replace(self.world,{},self.design,self.policy,self.catalog,self.base)
        self.assertEqual(state,self.world.get(pos));self.assertNotEqual(list(pos),records[0]['root'])


if __name__=='__main__':unittest.main()
