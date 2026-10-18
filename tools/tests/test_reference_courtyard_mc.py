"""Architectural kit boundaries: grouped entries, whole units, exact collision and ownership."""
import json
import math
import unittest
from tools.reference_courtyard import minecraft as mc
from tools.reference_courtyard.assembly_shapes import geometry,parts
from tools.reference_courtyard.assembly_scene import Site


def mask(boxes):
    out=set()
    for b in boxes:
        x0,y0,z0,x1,y1,z1=b
        for x in range(x0,x1):
            for y in range(y0,y1):
                for z in range(z0,z1):out.add((x,y,z))
    return out


class CourtyardKitTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.outputs,cls.report,cls.catalog,cls.site=mc.generate()
        cls.config=json.loads((mc.HERE/'assemblies.json').read_text())

    def test_ten_registry_entries_hide_thirty_style_variants(self):
        self.assertEqual(10,self.report['item_entries']);self.assertEqual(30,self.report['variants'])
        self.assertEqual(len(self.catalog),len({f['id'] for f in self.catalog}))
        for f in self.catalog:
            model=json.loads(self.outputs[mc.ASSETS/f'models/item/{f["id"]}.json'])
            self.assertEqual(len(f['variants']),len(model['overrides']))
        content=(mc.JAVA/'CourtyardContent.java').read_text()
        self.assertIn('ITEMS_BY_FAMILY.values().forEach(item->output.accept(item.get()))',content)

    def test_window_is_one_item_with_nine_real_parts(self):
        window=next(f for f in self.catalog if f['id']=='courtyard_window')
        for variant in window['variants']:
            self.assertEqual([3,3,1],variant['size']);self.assertEqual(9,len(variant['parts']))
        gate=next(f for f in self.catalog if f['id']=='courtyard_gate')
        self.assertTrue(all(len(v['parts'])==25 for v in gate['variants']))

    def test_visible_shape_and_collision_are_identical_for_every_part(self):
        for f,generated in zip(self.config['families'],self.catalog):
            for vi,v in enumerate(f['variants']):
                split=parts(v,geometry(v))
                for pi,local in enumerate(split):
                    collision=mask(generated['variants'][vi]['parts'][pi]['boxes'])
                    visual=json.loads(self.outputs[mc.ASSETS/f'models/block/courtyard/{f["id"]}/{vi}_{pi}.json'])
                    visible=mask([e['from']+e['to'] for e in visual['elements']])
                    self.assertEqual(set(local),collision,(f['id'],vi,pi))
                    self.assertEqual(visible,collision)

    def test_all_whole_shapes_support_true_world_mirroring(self):
        for f in self.config['families']:
            for v in f['variants']:
                cells=geometry(v);width=v['size'][0]*16
                self.assertEqual(cells,{(width-1-x,y,z):m for (x,y,z),m in cells.items()},(f['id'],v['key']))

    def test_scene_never_cuts_or_overlaps_a_whole_assembly(self):
        occupied=set()
        for unit in self.site.assemblies:
            for part,pos in enumerate(unit['parts']):
                p=tuple(pos);self.assertNotIn(p,occupied);occupied.add(p)
                name,props=mc.nbt.parse_block_state(self.site.blocks[p])
                self.assertEqual(name,'myvillage:'+unit['family'])
                self.assertEqual(int(props['part']),part)
                self.assertEqual(int(props['variant']),unit['variant'])
                self.assertEqual(props['facing'],unit['facing'])

    def test_only_root_loot_can_yield_one_item_and_preserves_variant(self):
        for f in self.config['families']:
            max_parts=max(math.prod(v['size']) for v in f['variants'])
            pools=json.loads(self.outputs[mc.DATA/f'loot_table/blocks/{f["id"]}.json'])['pools']
            for vi,pool in enumerate(pools):
                properties=pool['conditions'][0]['properties']
                self.assertEqual(properties['variant'],str(vi))
                self.assertEqual(properties.get('part'),'0' if max_parts>1 else None)
                entry=pool['entries'][0];self.assertEqual(entry['name'],'myvillage:'+f['id'])
                self.assertEqual(entry['functions'][0]['components']['minecraft:block_state'],{'variant':str(vi)})

    def test_item_icons_show_whole_units_inside_vanilla_bounds(self):
        for f in self.config['families']:
            for vi in range(len(f['variants'])):
                model=json.loads(self.outputs[mc.ASSETS/f'models/item/courtyard/{f["id"]}_{vi}.json'])
                self.assertTrue(model['elements'])
                for e in model['elements']:
                    self.assertTrue(all(0<=n<=16 for n in e['from']+e['to']))
    def test_template_uses_source_water_and_stays_in_structure_block_bounds(self):
        self.assertTrue(any(s=='minecraft:water[level=0]' for s in self.site.blocks.values()))
        self.assertLessEqual(max(self.config['size']),48)
        self.assertTrue(self.report['template_roundtrip'])

    def test_roof_sections_meet_without_open_seams_and_ridge_closes_cap(self):
        roof=next(f for f in self.config['families'] if f['id']=='courtyard_roof')
        for args in [(26,43,16,26,10,'z'),(22,47,24,40,11,'z'),(2,25,24,33,9,'x'),(10,19,32,40,9,'z')]:
            s=Site(self.config);s.roof(*args);x0,x1,z0,z1,_,axis=args
            spans={}
            for (x,y,z),state in s.blocks.items():
                if (z!=(z0+z1)//2 if axis=='z' else x!=(x0+x1)//2):continue
                _,props=mc.nbt.parse_block_state(state);cells=geometry(roof['variants'][int(props['variant'])])
                for _ in range(['north','east','south','west'].index(props['facing'])):
                    cells={(15-c,b,a):m for (a,b,c),m in cells.items()}
                for edge in [0,15]:
                    ys=[y*16+b for a,b,c in cells if (a==edge and c==8 if axis=='z' else c==edge and a==8)]
                    spans[x if axis=='z' else z,edge]=(min(ys),max(ys))
            cross=sorted({a for a,_ in spans})
            for a in cross[:-1]:
                left,right=spans[a,15],spans[a+1,0]
                self.assertLessEqual(max(left[0],right[0]),min(left[1],right[1])+1,(args,a,left,right))
        ridge=next(f for f in self.config['families'] if f['id']=='courtyard_ridge')
        for v in ridge['variants']:
            cells=geometry(v)
            self.assertTrue(all((x,0,z) in cells for x in range(v['size'][0]*16) for z in range(16)))


if __name__=='__main__':unittest.main()
