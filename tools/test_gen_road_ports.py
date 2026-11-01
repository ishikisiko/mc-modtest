import json
from pathlib import Path
import tempfile
import unittest

from gen_road_ports import ROOT, OUTPUT, generate, assets


class RoadPortsTest(unittest.TestCase):
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
