import json
from pathlib import Path
import tempfile
import unittest

from gen_road_ports import ROOT, OUTPUT, generate, assets


class RoadPortsTest(unittest.TestCase):
    def test_item_validator_recognizes_registered_road_slabs(self):
        from validate_mod_items import road_items
        self.assertEqual(road_items(),{'road_grey_flagstone_slab','road_grey_setts_slab','road_grey_ashlar_slab','road_stone_guardrail'})

    def test_guardrail_models_cover_half_support_and_full_step_couplers(self):
        generated=assets();stem=ROOT/'src/main/resources';ident='road_stone_guardrail'
        state=json.loads(generated[stem/f'assets/myvillage/blockstates/{ident}.json'])
        self.assertEqual(len(state['variants']),512)
        for mask in range(256):
            for lowered in [False,True]:
                variant=state['variants'][f'lowered={str(lowered).lower()},mask={mask}']['model']
                model=json.loads(generated[stem/('assets/'+variant.replace(':','/models/',1)+'.json')])
                self.assertEqual(min(e['from'][1] for e in model['elements']),-8 if lowered else 0)
                self.assertTrue(all(0<=e['from'][0]<e['to'][0]<=16 and 0<=e['from'][2]<e['to'][2]<=16 for e in model['elements']))
        model=json.loads(generated[stem/f'assets/myvillage/models/block/{ident}_17.json'])
        self.assertEqual(max(e['to'][1] for e in model['elements']),24)
        loot=json.loads(generated[stem/f'data/myvillage/loot_table/blocks/{ident}.json'])
        self.assertEqual(loot['pools'][0]['entries'][0]['name'],'myvillage:'+ident)

    def test_guardrail_authored_rules_are_embedded_in_runtime_configuration(self):
        self.assertEqual(json.loads(generate())['guardrail'],json.loads((ROOT/'config/roads/guardrail.json').read_text()))

    def test_shifted_guardrail_models_keep_uv_inside_the_stone_texture(self):
        for path,text in assets().items():
            if 'models/block/road_stone_guardrail_' not in path.as_posix():continue
            model=json.loads(text)
            for element in model['elements']:
                for face,properties in element['faces'].items():
                    # 原版侧面默认 UV 使用 16-Y，半砖下移后的石柱会超出纹理至 V=24。
                    uv=properties.get('uv')
                    if uv is None:
                        a,b=element['from'],element['to']
                        uv=[a[0],a[2],b[0],b[2]] if face in ['up','down'] else [0,16-b[1],1,16-a[1]]
                    self.assertTrue(all(0<=v<=16 for v in uv),(path.name,face,uv))

    def test_slab_resources_reuse_approved_textures_and_double_drops_two(self):
        generated=assets()
        config=json.loads((ROOT/'config/roads/prototype.json').read_text())
        for spec in config['style']['slabs']:
            stem=ROOT/'src/main/resources'
            model=json.loads(generated[stem/f"assets/myvillage/models/block/{spec['id']}.json"])
            self.assertEqual(set(model['textures'].values()),{spec['texture']})
            state=json.loads(generated[stem/f"assets/myvillage/blockstates/{spec['id']}.json"])
            self.assertEqual(set(state['variants']),{'type=bottom','type=top','type=double'})
            loot=json.loads(generated[stem/f"data/myvillage/loot_table/blocks/{spec['id']}.json"])
            count=loot['pools'][0]['entries'][0]['functions'][0]
            self.assertEqual(count['count'],2)
            self.assertEqual(count['conditions'][0]['properties'],{'type':'double'})

    def test_generated_slab_files_and_names_are_current(self):
        for path,value in assets().items():self.assertEqual(path.read_text(),value,str(path))

    def test_generated_output_is_current_and_uses_public_doorway(self):
        self.assertEqual(OUTPUT.read_text(), generate())
        ports = {p['region']: p for p in json.loads(generate())['ports']}
        self.assertEqual(ports['nanjiang']['x'], 82)
        self.assertEqual(ports['ximo']['x'], 98)
        self.assertEqual(ports['nanjiang']['port_width'], 3)
        self.assertEqual(ports['ximo']['port_width'], 5)

    def test_narrow_gate_cannot_be_widened_implicitly(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            config = json.loads((ROOT/'config/roads/prototype.json').read_text())
            config['ports'] = config['ports'][:1]
            config['ports'][0]['port_width'] = 5
            target = root/'config/roads/prototype.json'
            target.parent.mkdir(parents=True)
            target.write_text(json.dumps(config))
            layout = json.loads((ROOT/config['ports'][0]['layout']).read_text())
            passage = next(p for p in layout['public_passages'] if p['id'] == config['ports'][0]['passage'])
            passage['bounds'][2] = passage['bounds'][0]+3
            target = root/config['ports'][0]['layout']
            target.parent.mkdir(parents=True)
            target.write_text(json.dumps(layout))
            with self.assertRaisesRegex(ValueError, '完整路幅'):
                generate(root)


if __name__ == '__main__':
    unittest.main()
