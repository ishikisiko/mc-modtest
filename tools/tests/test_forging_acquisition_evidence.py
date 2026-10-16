import unittest
from tools.forging_acquisition_evidence import section_positions,ORE,SAND
from tools.modal_runner.remote import commands_for

class AcquisitionEvidenceTest(unittest.TestCase):
    def test_palette_decode_uses_padded_longs_and_world_coordinates(self):
        palette=[{'Name':'minecraft:stone'} for _ in range(17)];palette[16]={'Name':ORE}
        data=[0]*342
        for index in (11,12,4095):data[index//12]|=16<<((index%12)*5)
        section={'Y':-2,'block_states':{'palette':palette,'data':data}}
        self.assertEqual([(ORE,(-5,-32,32)),(ORE,(-4,-32,32)),(ORE,(-1,-17,47))],section_positions(section,-1,2,{ORE}))
    def test_signed_long_is_decoded_unsigned_and_short_data_is_rejected(self):
        palette=[{'Name':'minecraft:stone'} for _ in range(16)];palette[15]={'Name':SAND}
        data=[0]*256;data[0]=-(1<<60)
        self.assertEqual([(SAND,(15,0,0))],section_positions({'Y':0,'block_states':{'palette':palette,'data':data}},0,0,{SAND}))
        with self.assertRaises(ValueError):section_positions({'Y':0,'block_states':{'palette':palette,'data':data[:-1]}},0,0,{SAND})
    def test_runtime_preset_builds_current_sources_and_runs_only_acquisition(self):
        commands=commands_for('forging-acquisition')
        self.assertEqual(['python3','tools/forging_acquisition_evidence.py'],commands[-1])
        self.assertFalse(any('release_gate.py' in ' '.join(c) for c in commands))
        self.assertIn('com.example.myvillage.forging.*',commands[-2])
