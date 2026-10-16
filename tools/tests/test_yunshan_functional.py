"""Focused spatial/export regressions for the separately saved building batch."""
import contextlib
import copy
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools'))
from json_to_nbt import expand_structure
from yunshan.build_inn import canonical,check_roof,check_wall_roof_joins
from yunshan.build_functional import build_batch,rotated_pos,rotated_state,validate_site,validate_services

SOURCE=ROOT/'blueprints/yunshan'


class FunctionalBuildingsTests(unittest.TestCase):
    def data(self,name):
        return json.loads((SOURCE/name).read_text())

    def test_non_square_site_rotation_and_direction_are_checked(self):
        d=self.data('apothecary_v1.json');ref=self.data('d1_site_reference.json')
        self.assertEqual(validate_site(d,ref)['size_after_rotation'],[11,20,13])
        d['site']['rotation_quarters']=1
        with self.assertRaises(AssertionError):
            validate_site(d,ref)

    def test_rotation_roundtrip_for_every_block_and_direction(self):
        manifest=self.data('functional_buildings_v1.json')
        for name in manifest['buildings']:
            d=self.data(name);size,_,blocks,_=expand_structure(d);q=d['site']['rotation_quarters']
            rotated_size=[size[2],size[1],size[0]] if q%2 else size
            for pos,state in blocks.items():
                p=rotated_pos(pos,size,q)
                self.assertEqual(rotated_pos(p,rotated_size,4-q),pos)
                self.assertEqual(canonical(rotated_state(rotated_state(state,q),4-q)),canonical(state))

    def test_longitudinal_roof_gap_detected_on_other_roof_axis(self):
        d=self.data('apothecary_v1.json');size,_,blocks,_=expand_structure(d)
        for x in range(13):
            blocks[x,4,1]='minecraft:deepslate_tile_slab[type=bottom,waterlogged=false]'
        with self.assertRaisesRegex(AssertionError,'roof shell gap'):
            check_roof(blocks,d['roof_checks'][0],size)

    def test_v3_seals_the_wall_joint_that_v2_did_not_cover(self):
        old=self.data('south_gate_inn_v2.json');new=self.data('south_gate_inn_v3.json')
        _,_,before,_=expand_structure(old);_,_,after,_=expand_structure(new)
        with self.assertRaisesRegex(AssertionError,'wall/eave gap'):
            check_wall_roof_joins(before,new['wall_roof_joins'])
        self.assertEqual(check_wall_roof_joins(after,new['wall_roof_joins']),22)
        changed={p for p in before if before[p]!=after[p]}
        self.assertEqual(changed,{(x,10,z) for x in (1,11) for z in range(2,15)})

    def test_service_overwrite_and_invalid_state_are_rejected(self):
        d=self.data('smithy_v1.json');_,_,blocks,_=expand_structure(d)
        service=d['service_blocks'][0];pos=tuple(service['pos'])
        overwritten=dict(blocks);overwritten[pos]='minecraft:stone_bricks'
        with self.assertRaisesRegex(AssertionError,'overwritten facility'):
            validate_services(d,overwritten)
        bad=copy.deepcopy(d);bad['service_blocks'][0]['state']='myvillage:forging_table[facing=up,lit=false]'
        blocks[pos]=bad['service_blocks'][0]['state']
        with self.assertRaisesRegex(AssertionError,'unknown facility state'):
            validate_services(bad,blocks)

    def test_all_site_and_default_exports_roundtrip_and_are_reproducible(self):
        with tempfile.TemporaryDirectory() as tmp, contextlib.redirect_stdout(io.StringIO()):
            a,b=Path(tmp)/'a',Path(tmp)/'b'
            ra=build_batch(SOURCE/'functional_buildings_v1.json',a)
            rb=build_batch(SOURCE/'functional_buildings_v1.json',b)
            self.assertEqual(ra,rb)
            self.assertEqual(len(ra['buildings']),4)
            for entry in ra['buildings']:
                self.assertTrue(all(entry['walk_targets'].values()))
                self.assertTrue(entry['placement']['state_rotation_verified'])
            files=list(a.rglob('*.schem'))+list(a.rglob('*.nbt'))
            self.assertEqual(len(files),16)
            for file in files:
                self.assertEqual(file.read_bytes(),(b/file.relative_to(a)).read_bytes())


if __name__=='__main__':unittest.main()
