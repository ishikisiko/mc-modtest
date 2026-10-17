"""Actual city composition, support/collision failures and typed tile exports."""
from pathlib import Path
import sys
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools'))
from yunshan import build_district as d
from yunshan.game_export import plain,read_typed
from yunshan.build_functional import rotated_pos,rotated_state
import json_to_nbt as nbt


class DistrictBuildTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.fixture=d.realize(ROOT/'blueprints/yunshan/district_realization_v2.json')
        (cls.cfg,cls.plan,cls.site,cls.land,cls.volume,cls.levels,cls.kinds,
         cls.walking,cls.buildings,cls.houses,cls.signs,cls.planning)=cls.fixture

    def validate(self):
        return d.validate_blocks(self.volume,self.walking,self.buildings,self.houses,self.signs,
                                 self.planning,self.site['world_offset'])

    def test_complete_city_and_all_real_routes(self):
        self.assertEqual(self.volume.minimum,(2850,62,110))
        self.assertEqual(self.volume.size,(210,82,265))
        report=self.validate()
        self.assertTrue(report['passed'],[c for c in report['checks'] if not c['passed']])
        self.assertEqual(len(self.houses),70)
        self.assertTrue(all(self.volume.cells[:210*265]),'continuous world foundation must cover every district column')
        self.assertEqual({h['family'] for h in self.houses},{'hui','jiangnan'})

    def test_one_block_headroom_obstacle_is_rejected(self):
        p=self.validate()['routes']['inn'][4]
        at=(p[0],p[1]+1,p[2]);before=self.volume.get(at)
        try:
            self.volume.set(at,'stone')
            report=self.validate()
            self.assertFalse(next(c for c in report['checks'] if c['name'].startswith('accepted gate routes'))['passed'])
        finally:self.volume.set(at,before)

    def test_floating_building_foundation_is_rejected(self):
        b=self.buildings[0];lo=b['world_min'];at=(lo[0],lo[1]-1,lo[2]);before=self.volume.get(at)
        try:
            self.volume.set(at,'air')
            report=self.validate()
            self.assertFalse(next(c for c in report['checks'] if c['name']==b['key']+': continuous foundation support')['passed'])
        finally:self.volume.set(at,before)

    def test_all_four_rotated_buildings_preserve_every_authored_cell(self):
        for b in self.buildings:
            source=d.read(ROOT/'blueprints/yunshan'/b['source'])
            size,_,blocks,_=nbt.expand_structure(source)
            for p,state in blocks.items():
                rp=rotated_pos(p,size,b['rotation_quarters'])
                world=tuple(b['world_min'][i]+rp[i] for i in range(3))
                self.assertEqual(self.volume.get(world),d.canonical(rotated_state(state,b['rotation_quarters'])),(b['key'],world))

    def test_gate_passage_and_lake_bed_are_real_blocks(self):
        for z in range(346,366):
            self.assertEqual(self.volume.get((2965,68,z)),d.AIR)
            self.assertNotEqual(self.volume.get((2965,67,z)),d.AIR)
        for (x,z),kind in self.kinds.items():
            if kind=='water' and x not in (-145,64) and z not in (140,404):
                self.assertEqual(self.volume.get((x+2995,67,z-30)),'minecraft:water')
                self.assertNotEqual(self.volume.get((x+2995,64,z-30)),d.AIR)

    def test_tiles_preserve_air_absolute_origins_and_sign_text(self):
        volume=d.Volume((100,62,200),(103,65,204))
        volume.set((101,63,202),'oak_wall_sign[facing=north]')
        signs=[{'pos':[101,63,202],'messages':['云山','','','']}]
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp);tiles=d.export_tiles(volume,signs,out,3)
            self.assertEqual(len(tiles),8)
            rebuilt={};texts=[]
            for tile in tiles:
                data=plain(read_typed(out/tile['nbt'])[1])
                self.assertTrue(all(i<=3 for i in data['size']))
                for block in data['blocks']:
                    p=tuple(tile['world_min'][i]+block['pos'][i] for i in range(3))
                    state=data['palette'][block['state']]
                    rebuilt[p]=state['Name']
                    if 'nbt' in block:texts.extend(block['nbt']['front_text']['messages'])
            self.assertEqual(len(rebuilt),80)
            self.assertEqual(rebuilt[101,63,202],'minecraft:oak_wall_sign')
            self.assertIn('云山',texts[0])
            d.schematic(volume,signs,out/'whole.schem')
            data=plain(read_typed(out/'whole.schem')[1])
            self.assertEqual(data['Offset'],[0,0,0])
            self.assertEqual((data['Width'],data['Height'],data['Length']),(4,4,5))
            self.assertEqual(data['BlockEntities'][0]['Pos'],[1,1,2])
            self.assertEqual(len(d.export.decode_varints(data['BlockData'])),80)


if __name__=='__main__':unittest.main()
