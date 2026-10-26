from pathlib import Path
import json
import tempfile
import unittest
from tools.npcgen.pack import compile_resources, PREFIX


class NpcPackingTests(unittest.TestCase):
    CONFIG = {'schema': 1, 'kinds': ['model', 'animations'], 'minimum_copies': 2}

    def test_only_identical_payloads_share_and_every_old_path_resolves_exactly(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory)
            contents = {'a_model.json': b'{"schema":1,"bones":[1]}',
                        'b_model.json': b'{"schema":1,"bones":[1]}',
                        'c_model.json': b'{"schema":1,"bones":[2]}',
                        'a_animations.json': b'{"schema":1,"clips":[3]}',
                        'b_animations.json': b'{"schema":1,"clips":[3]}'}
            for name,data in contents.items(): (source/name).write_bytes(data)
            result, report = compile_resources(source,self.CONFIG)
            self.assertEqual(2,report['shared_payloads'])
            self.assertEqual(4,report['reference_files'])
            for name,data in contents.items():
                self.assertEqual(data,(source/name).read_bytes())
                packed = result[PREFIX/name]; obj=json.loads(packed)
                if 'resource_ref' in obj:
                    relative=obj['resource_ref'].split('npc/',1)[1]
                    packed=result[PREFIX/relative]
                self.assertEqual(data,packed)
            self.assertEqual(contents['c_model.json'],result[PREFIX/'c_model.json'])
            self.assertEqual(result,compile_resources(source,self.CONFIG)[0])

    def test_ref_inputs_and_invalid_configuration_fail(self):
        with tempfile.TemporaryDirectory() as directory:
            source=Path(directory);(source/'a_model.json').write_text('{"resource_ref":"x:y"}')
            with self.assertRaises(ValueError):compile_resources(source,self.CONFIG)
            with self.assertRaises(ValueError):compile_resources(source,{**self.CONFIG,'minimum_copies':1})


if __name__=='__main__':unittest.main()
