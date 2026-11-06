"""四组内容的获取闭合、资源归属与防止遗迹在打包前被清理。"""
import copy,json,unittest,sys
from pathlib import Path
from tools import gen_legacy_encounters as gen
from tools.beastgen.build import Built

class LegacyResourcesTest(unittest.TestCase):
    def test_actual_resources_match_author_inputs(self):
        for path,data in gen.outputs().items():
            self.assertEqual(path.read_bytes(),data,str(path))
        for mob in gen.catalogue()['mobs']:
            for path,data in Built(mob['id']).outputs().items():self.assertEqual(path.read_bytes(),data,str(path))

    def test_native_structures_retain_ready_anchors_after_export(self):
        sys.path.insert(0,str(gen.ROOT/'tools'))
        for kind,block in [('sword','sword_inscription'),('mountain','mountain_stele')]:
            # Read the actual binary, not only the generator's authored dictionary.
            path=gen.RES/f'data/myvillage/structure/legacy_{kind}_ruin.nbt'
            import gzip
            raw=gzip.decompress(path.read_bytes())
            self.assertIn(('myvillage:'+block).encode(),raw)
            self.assertIn(b'myvillage:legacy_stele',raw)
            self.assertIn(b'\x01\x00\x05Ready\x01',raw)
            for directory in ['structure','structure_set','template_pool']:
                self.assertTrue((gen.RES/f'data/myvillage/worldgen/{directory}/legacy_{kind}_ruin.json').is_file())
        pipeline=(gen.ROOT/'tools/generate_all_structures.py').read_text()
        self.assertLess(pipeline.index('clean_nbt_tree(output_dir)'),pipeline.index('"tools.gen_legacy_encounters"'))
        self.assertLess(pipeline.index('"tools.gen_legacy_encounters"'),pipeline.index('copy_default_output(output_dir)'))

    def test_only_environmental_mobs_have_spawn_injection(self):
        for m in gen.catalogue()['mobs']:
            path=gen.RES/f'data/myvillage/neoforge/biome_modifier/add_{m["id"]}_spawns.json'
            self.assertEqual(m['spawn']=='natural',path.exists())
        source=(gen.ROOT/'src/main/java/com/example/myvillage/entity/ModEntityEvents.java').read_text()
        self.assertIn('LegacyEvents::checkSpawn',source)

    def test_bound_art_hud_sprites_are_present_and_distinct(self):
        from PIL import Image
        sprites=[]
        for a in gen.catalogue()['arts']:
            path=gen.RES/f'assets/myvillage/textures/gui/advanced_arts/{a["id"]}.png'
            image=Image.open(path).convert('RGBA')
            self.assertEqual((32,32),image.size)
            self.assertIsNotNone(image.getbbox());sprites.append(image.tobytes())
        self.assertEqual(4,len(set(sprites)))

    def test_manual_costs_and_materials_are_closed(self):
        c=gen.catalogue();reserved=json.loads((gen.ROOT/'tools/technique_catalogue/rules.json').read_text())['hand_written_techniques']
        for a in c['arts']:
            self.assertIn(a['id'],reserved)
            book=json.loads((gen.RES/f'data/myvillage/myvillage/technique/{a["id"]}.json').read_text())
            self.assertEqual(a['qi_cost'],book['effects']['active']['qi_cost'])
            self.assertEqual('myvillage:'+a['id'],book['effects']['active']['skill'])
        source=(gen.ROOT/'src/main/java/com/example/myvillage/legacy/LegacyArtifactItem.java').read_text()
        self.assertIn('isInWaterOrBubble',source);self.assertIn('myvillage:fenying_bu',source)

    def test_uv_masks_and_native_material_alpha_are_exact(self):
        from PIL import Image
        for m in gen.catalogue()['mobs']:
            root=gen.ROOT/'art/entities'/m['id'];mask=Image.open(root/'uv_islands_mask.png').convert('L');mat=Image.open(root/'material_tones.png').convert('RGBA')
            self.assertEqual(mask.size,mat.size);self.assertEqual(mask.tobytes(),mat.getchannel('A').tobytes())
            self.assertLessEqual(set(mask.getdata()),{0,255})

    def test_rejects_out_of_bound_behavior_and_missing_recovery(self):
        c=gen.catalogue();c['arts'][0]['duration_ticks']=999999
        with self.assertRaises(AssertionError):gen.validate(c)
        c=gen.catalogue();c['mobs'][0]['moves'][0]['total_ticks']=c['mobs'][0]['moves'][0]['active_ticks'][1]+1
        with self.assertRaises(AssertionError):gen.validate(c)

if __name__=='__main__':unittest.main()
