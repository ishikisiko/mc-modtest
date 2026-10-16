import json
from pathlib import Path
import tempfile
import unittest
from tools.forging import materials,acquisition
from tools.modal_runner.remote import commands_for

ROOT=Path(__file__).resolve().parents[2]
class ForgingTagsTest(unittest.TestCase):
    def test_ore_classification_covers_both_blocks_and_items(self):
        generated=acquisition.outputs();base=ROOT/'src/main/resources/data/c/tags'
        for kind in ('item','block'):
            self.assertEqual({'replace':False,'values':['#c:ores/xuantie']},json.loads(generated[base/kind/'ores.json']))
            self.assertEqual({'replace':False,'values':['myvillage:xuantie_ore']},json.loads(generated[base/kind/'ores/xuantie.json']))
    def test_unsafe_or_duplicate_material_tag_paths_fail_before_writing(self):
        source=json.loads((ROOT/'tools/forging/materials.json').read_text())
        for tag in ['myvillage:../../escape','myvillage:/absolute','myvillage:bad//segment',source['materials'][1]['tag']]:
            data=json.loads(json.dumps(source));data['materials'][0]['tag']=tag
            with tempfile.TemporaryDirectory() as temp:
                p=Path(temp)/'materials.json';p.write_text(json.dumps(data))
                with self.assertRaises(ValueError):materials.load(p)
    def test_tag_runtime_is_bounded_and_does_not_repeat_natural_worldgen(self):
        commands=commands_for('forging-tags')
        self.assertEqual(['python3','tools/forging_tags_evidence.py'],commands[-1])
        self.assertEqual(['python3','tools/gen_forging_assets.py','--check'],commands[0])
        self.assertIn('jar',commands[1]);self.assertNotIn('test',commands[1])
        self.assertNotIn('forging_acquisition_evidence.py',' '.join(' '.join(c) for c in commands))
