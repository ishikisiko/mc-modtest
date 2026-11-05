"""传送亭台的空间、原路与原方块实体保护。"""
import copy
import gzip
import hashlib
import json
import io
from pathlib import Path
import struct
import tempfile
import unittest

from .generate import Builder, MATERIALS, SITES, approach_columns
from province_capitals.blueprint_plan.nbt_entities import block_entities, write_tile
from province_capitals.game.compile import resource_file
import json_to_nbt as nbt


class CapitalWaystoneGenerationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.materials = json.loads(MATERIALS.read_text())
        self.rules = json.loads(SITES.read_text())['constraints']
        path = self.root/'data/myvillage/capitals/v4/capital_zhongzhou/terrain.bin.gz'
        path.parent.mkdir(parents=True); path.write_bytes(gzip.compress(struct.pack('<'+'h'*1024,*([7]*1024)),mtime=0))
        self.manifest = {'city_id':'capital_zhongzhou','size':[32,32,32], 'tiles':[],
                         'terrain':{'resource':'myvillage:capitals/v4/capital_zhongzhou/terrain.bin.gz',
                                    'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),
                                    'top_block':'minecraft:grass_block','fill_block':'minecraft:stone','subsurface_block':'minecraft:dirt'}}

    def builder(self):
        return Builder(self.root,copy.deepcopy(self.manifest),self.materials,self.rules)

    def station(self,size=9,kind='pavilion',facing='south'):
        r=size//2;dx,dz={'south':(0,1),'west':(-1,0),'north':(0,-1),'east':(1,0)}[facing]
        return {'id':'zhongzhou_01','name':'试验亭','center':[16,8,16], 'size':size,'bounds':[16-r,16-r,16+r,16+r],
                'kind':kind,'facing':facing,'approach':[[16+dx*i,8,16+dz*i] for i in range(r,r+4)]}

    def test_all_sizes_and_orientations_keep_arrival_and_three_wide_entry_clear(self):
        for size in (9,11,13):
            for kind in ('array','pavilion'):
                for face in ('north','east','south','west'):
                    with self.subTest(size=size,kind=kind,face=face):
                        b=self.builder();s=self.station(size,kind,face);b.build({'id':'zhongzhou','short_name':'中州'},s)
                        for x in range(14,19):
                            for z in range(14,19):
                                self.assertTrue(b.world.walkable((x,8,z)))
                                for y in range(8,12):self.assertEqual('minecraft:air',b.world.get((x,y,z)))
                        for x,y,z in s['approach']:self.assertTrue(b.world.walkable((x,y,z)))
                        if kind=='pavilion':self.assertNotEqual('minecraft:air',b.world.get((16,8+5+size//2-1,16)))

    def test_existing_road_state_is_preserved(self):
        b=self.builder();s=self.station();p=(16,7,23);state='minecraft:stone_brick_slab[type=bottom,waterlogged=false]'
        b.world.set(p,state,'原道路');b.build({'id':'zhongzhou','short_name':'中州'},s)
        self.assertEqual(state,b.world.get(p));self.assertNotIn(p,b.edits)

    def test_foreign_structure_is_rejected_and_not_cleared(self):
        b=self.builder();p=(16,8,16);b.world.set(p,'minecraft:oak_planks','原建筑')
        with self.assertRaisesRegex(ValueError,'覆盖原构件'):b.build({'id':'zhongzhou','short_name':'中州'},self.station())
        self.assertEqual('minecraft:oak_planks',b.world.get(p))

    def test_road_centerline_keeps_its_elevation_at_turns(self):
        s={'approach':[[4,8,4],[4,9,5],[5,10,5],[6,10,5]]};field=approach_columns(s)
        for x,y,z in s['approach']:self.assertEqual(y,field[x,z])

    def test_block_entity_payload_is_byte_identical_after_a_nearby_station(self):
        name='myvillage:province_capitals/v4/capital_zhongzhou/tile_0_0';path=resource_file(self.root,name,template=True);path.parent.mkdir(parents=True)
        payload=io.BytesIO()
        nbt._write_named_tag(payload,'id',nbt.String('minecraft:chest'))
        payload.write(bytes([nbt.TAG_LONG]));nbt._write_utf(payload,'LootTableSeed');payload.write(struct.pack('>q',9223372036854775000))
        nbt._write_named_tag(payload,'KeepPacked',nbt.Byte(1));payload.write(bytes([nbt.TAG_END]))
        state='minecraft:chest[facing=north,type=single,waterlogged=false]'
        entities={(3,8,3):{'block':'minecraft:chest','payload':payload.getvalue()}}
        write_tile(path,{(3,8,3):(state,'source','building')},32,entities)
        before=block_entities(path);self.assertEqual(entities,before)
        self.manifest['tiles']=[{'x':0,'z':0,'template':name,'sha256':hashlib.sha256(path.read_bytes()).hexdigest()}]
        b=self.builder();b.build({'id':'zhongzhou','short_name':'中州'},self.station());b.export(self.root)
        self.assertEqual(before,block_entities(path))


if __name__=='__main__':unittest.main()
