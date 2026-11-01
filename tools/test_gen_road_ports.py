import json
from pathlib import Path
import tempfile
import unittest

from gen_road_ports import ROOT, OUTPUT, generate


class RoadPortsTest(unittest.TestCase):
    def test_generated_output_is_current_and_uses_public_doorway(self):
        self.assertEqual(OUTPUT.read_text(), generate())
        ports = {p['region']: p for p in json.loads(generate())['ports']}
        self.assertEqual(ports['nanjiang']['x'], 82)
        self.assertEqual(ports['ximo']['x'], 98)

    def test_narrow_gate_cannot_be_widened_implicitly(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            config = json.loads((ROOT/'config/roads/prototype.json').read_text())
            config['ports'] = config['ports'][:1]
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
