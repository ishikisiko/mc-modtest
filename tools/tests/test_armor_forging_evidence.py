"""Keep native readback calls and the input guard on their declared boundary."""
import ast
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[2]
class ArmorForgingCollectorTest(unittest.TestCase):
    def test_every_rcon_readback_or_fixture_call_has_exactly_one_command(self):
        tree=ast.parse((ROOT/'tools/armor_forging_evidence.py').read_text())
        calls=[n for n in ast.walk(tree) if isinstance(n,ast.Call) and isinstance(n.func,ast.Attribute) and n.func.attr=='rc']
        self.assertGreater(len(calls),20)
        for call in calls:
            self.assertEqual(1,len(call.args),f'rc arity at line {call.lineno}')
            self.assertFalse(call.keywords,f'rc keywords at line {call.lineno}')
    def test_correctly_scoped_readback_does_not_allow_menu_action_commands(self):
        from tools.armor_forging_evidence import ArmorForgingEvidence
        collector=object.__new__(ArmorForgingEvidence);collector.username='DemoTester'
        for command in ('myvillage workshop start DemoTester','myvillage forgingarray start 0 -59 8','myvillage workshop inspect DemoTester\ngive DemoTester minecraft:diamond'):
            with self.assertRaises(ValueError):collector.rc(command)

if __name__=='__main__':unittest.main()
