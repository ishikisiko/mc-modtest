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

    def test_ten_registry_entries_group_append_only_style_variants(self):
        self.assertEqual(10,self.report['item_entries']);self.assertEqual(39,self.report['variants'])
        wall=next(f for f in self.config['families'] if f['id']=='courtyard_wall')
        self.assertEqual(['plaster','dado','fan','timber_transom'],[v['key'] for v in wall['variants']])
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

    def test_stone_gate_matches_adjacent_wall_faces_dado_and_coping(self):
        family=next(f for f in self.config['families'] if f['id']=='courtyard_gate')
        stone=geometry(family['variants'][0])
        walls=next(f for f in self.config['families'] if f['id']=='courtyard_wall')
        floors=next(f for f in self.config['families'] if f['id']=='courtyard_floor')
        layers=[geometry(walls['variants'][1]),*[geometry(walls['variants'][0])]*3,geometry(floors['variants'][3])]
        for gate_x,wall_x in [(0,15),(79,0)]:
            for y in range(80):
                for z in range(16):
                    self.assertEqual(stone.get((gate_x,y,z)),layers[y//16].get((wall_x,y%16,z)),(gate_x,y,z))
        projection={(x,y) for x,y,z in stone}
        self.assertTrue(all((x,y,z) in stone for x,y in projection for z in range(16)))
        # A full-depth circular opening stays empty.
        self.assertTrue(all((40,y,z) not in stone for y in range(2,68) for z in range(16)))

    def test_timber_gate_has_six_pixel_frame_and_two_pixel_lattice(self):
        v=next(f for f in self.config['families'] if f['id']=='courtyard_gate')['variants'][1]
        cells=geometry(v)
        for z in range(16):
            frame='timber' if 5<=z<11 else None
            self.assertTrue(all(cells.get((x,y,z))==frame for x in [0,79] for y in range(80)))
            self.assertTrue(all(cells.get((x,79,z))==frame for x in range(80)))
            self.assertEqual(frame,cells.get((4,35,z)))
            self.assertEqual('timber' if z in [7,8] else None,cells.get((10,10,z)))
            self.assertTrue(all((40,y,z) not in cells for y in range(2,68)))

    def test_windows_close_the_wall_and_recess_only_the_lattice(self):
        for v in next(f for f in self.config['families'] if f['id']=='courtyard_window')['variants']:
            cells=geometry(v)
            for z in range(16):
                for edge in [0,47]:
                    self.assertTrue(all(cells.get((edge,n,z))=='plaster' and cells.get((n,edge,z))=='plaster' for n in range(48)))
                self.assertEqual('timber' if z in [0,1,7,8,14,15] else 'plaster',cells.get((43,23,z)))
                self.assertEqual('timber' if z in [7,8] else None,cells.get((31,23,z)))

    def test_gallery_wall_plates_and_gate_heads_reach_the_roof(self):
        for z in [25,32]:
            for x in range(3,24):
                self.assertEqual('myvillage:courtyard_floor[facing=north,variant=1,waterlogged=false]',self.site.blocks[x,8,z])
        for x in [6,12,18]:
            for y in range(3,8):
                self.assertEqual('myvillage:courtyard_wall[facing=north,variant=0,waterlogged=false,part=0]',self.site.blocks[x,y,31])
            for z in range(26,32):
                roof_y=9
                for y in range(8,roof_y):
                    self.assertEqual('myvillage:courtyard_wall[facing=east,variant=3,waterlogged=false,part=0]',self.site.blocks[x,y,z])
                self.assertTrue(self.site.blocks[x,roof_y,z].startswith(('myvillage:courtyard_roof','myvillage:courtyard_ridge')))

    def test_curved_roofs_have_closed_seams_thin_shells_and_raised_ends(self):
        roof=next(f for f in self.config['families'] if f['id']=='courtyard_roof')
        self.assertEqual(['low','high','eave','flat','stone_low','stone_high','stone_eave','stone_flat'],[v['key'] for v in roof['variants'][:8]])
        for key in ['curve_gallery','curve_pavilion','curve_lower']:
            v=next(v for v in roof['variants'] if v['key']==key);cells=geometry(v);W=v['size'][0]*16
            tops=[];bottoms=[]
            for x in range(W):
                ys=[y for a,y,z in cells if a==x and z==8]
                tops.append(max(ys));bottoms.append(min(ys))
                self.assertEqual(v['roof_thickness']+2,len(ys))
                self.assertEqual(list(range(min(ys),max(ys)+1)),sorted(ys))
            self.assertGreater(tops[0]-min(tops[:W//4]),5)
            self.assertGreater(tops[W//2],tops[0])
            for x in range(W-1):
                self.assertLessEqual(abs(tops[x+1]-tops[x]),2,(key,x))
                self.assertLessEqual(max(bottoms[x],bottoms[x+1]),min(tops[x],tops[x+1]))
            self.assertLessEqual(abs(tops[W//2]-tops[W//2-8]),2)
        self.assertLessEqual(self.report['max_collision_boxes'],128)
        self.assertTrue(all(math.prod(v['size'])<=100 for v in roof['variants']))

    def test_main_roof_has_two_separated_tiers_without_old_firewall_spikes(self):
        for z in range(27,37):
            self.assertTrue(self.site.blocks[35,11,z].startswith('myvillage:courtyard_roof'))
            self.assertTrue(self.site.blocks[35,15,z].startswith('myvillage:courtyard_roof'))
        for x in [24,44]:
            for z in range(27,39):
                for y in range(15,20):
                    self.assertNotIn('courtyard_wall',self.site.blocks.get((x,y,z),''))

    def test_transom_is_thin_open_lattice_with_edges_matching_gate_depth(self):
        v=next(f for f in self.config['families'] if f['id']=='courtyard_wall')['variants'][3]
        cells=geometry(v)
        for z in range(16):
            self.assertEqual('timber' if 5<=z<11 else None,cells.get((0,8,z)))
            self.assertEqual('timber' if z in [7,8] else None,cells.get((7,4,z)))
            self.assertIsNone(cells.get((4,4,z)))
        for x in range(16):
            for y in [0,15]:
                self.assertEqual({5,6,7,8,9,10},{z for z in range(16) if (x,y,z) in cells})

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
