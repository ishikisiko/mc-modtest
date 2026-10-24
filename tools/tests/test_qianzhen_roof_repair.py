"""Bounded repair resource and current real seeded geometry, without reports or Git dependencies."""
import copy
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from qianzhen import roof_repair as repair
from qianzhen import story_assets as assets
from qianzhen.storygen import GenerationContext, load_contract
from qianzhen.storygen.canal_gardens import generate


class RoofRepairResourceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.raw = json.loads(repair.SOURCE.read_text())
        cls.after = assets.nbt.expand_structure(generate(GenerationContext(load_contract().site('canal_gardens'),20261024)).to_blueprint())[2]

    def test_actual_fixed_plugin_equals_final_targets_and_all_original_guards(self):
        repair.validate(self.raw)
        for row in self.raw['targets']:
            self.assertEqual(assets.canonical(self.after[tuple(row['pos'])]), row['after'])
        for row in self.raw['guards']:
            self.assertEqual(assets.canonical(self.after.get(tuple(row['pos']), 'minecraft:air')),row['state'],row['pos'])

    def test_every_roof_cell_is_a_read_only_guard_and_targets_are_only_air_to_logs(self):
        guards = {tuple(r['pos']): r['state'] for r in self.raw['guards']}
        roofs = {p:s for p,s in self.after.items() if 'deepslate_tile' in s and 31<=p[0]<=44 and 7<=p[2]<=15}
        self.assertGreater(len(roofs),100)
        for p,state in roofs.items():
            self.assertEqual(guards[p],assets.canonical(state))
        self.assertTrue(all(r['before']=='minecraft:air' and r['after'].startswith('minecraft:spruce_log[') for r in self.raw['targets']))
        self.assertTrue(all(r['pos'][1]>=6 for r in self.raw['targets']))

    def test_resource_is_deterministic_and_binds_authoritative_source(self):
        data = repair.generate()
        self.assertEqual(data,repair.generate())
        generated = json.loads(data)
        self.assertEqual(generated['authored_patch_sha256'],repair.hashlib.sha256(repair.SOURCE.read_bytes()).hexdigest())
        with tempfile.TemporaryDirectory() as tmp:
            source=Path(tmp)/'patch.json';source.write_text(repair.SOURCE.read_text())
            self.assertEqual(data,repair.generate(source))

    def test_missing_guard_duplicate_target_replacement_and_expansion_are_rejected(self):
        mutations = [lambda d:d['guards'].pop(),lambda d:d['targets'].append(d['targets'][0]),
                     lambda d:d['targets'][0].update(before='minecraft:chest'),
                     lambda d:d['targets'][0].update(after='minecraft:diamond_block'),
                     lambda d:d['targets'][0].update(pos=[44,6,9]),
                     lambda d:d['supported'][0]['site'].update(sha256='0'*64)]
        for change in mutations:
            raw=copy.deepcopy(self.raw);change(raw)
            with self.assertRaises(ValueError):repair.validate(raw)


if __name__=='__main__':
    unittest.main()
