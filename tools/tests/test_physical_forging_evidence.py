"""Keep the collector's operator fixtures separate from actual smithing input."""
import ast
from pathlib import Path
import unittest
from tools.physical_forging_evidence import physical_readback,blank

class PhysicalForgingCollectorTest(unittest.TestCase):
    def test_scoped_extra_routes_are_read_only(self):
        for command in ('myvillage workshop anvil 0 -60 3','myvillage workshop inspect DemoTester',
                        'execute as @e[type=minecraft:item,nbt={Item:{id:"myvillage:workpiece_lingxiao_spear_rough"}}] run data get entity @s Item'):
            self.assertTrue(physical_readback(command),command)
        for command in ('myvillage workshop start DemoTester','myvillage workshop strike DemoTester',
                        'myvillage workshop anvil 0 -60 3\ngive DemoTester minecraft:diamond',
                        'execute as @e[type=minecraft:item] run kill @s'):
            self.assertFalse(physical_readback(command),command)
    def test_fixture_identity_matches_its_saved_stage(self):
        self.assertIn('workpiece_lingxiao_spear_rough[',blank('lingxiao_spear'))
        self.assertIn('stage:0,blows:0,treatment:0',blank('lingxiao_spear'))
        self.assertIn('stage:1,blows:12,treatment:2',blank('xuantie_chestplate',1,12))
    def test_every_readback_or_fixture_is_exactly_one_command(self):
        path=Path(__file__).resolve().parents[1]/'physical_forging_evidence.py'
        tree=ast.parse(path.read_text())
        calls=[n for n in ast.walk(tree) if isinstance(n,ast.Call) and isinstance(n.func,ast.Attribute) and n.func.attr=='rc']
        self.assertGreater(len(calls),20)
        for call in calls:
            self.assertEqual(1,len(call.args),f'rc arity at line {call.lineno}')
            self.assertFalse(call.keywords,f'rc keywords at line {call.lineno}')

if __name__=='__main__':unittest.main()
