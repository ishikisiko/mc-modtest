"""Pin evidence-oracle reference results independently of Java helpers."""
import unittest
from tools.realm_armor_evidence import armor_damage


class ArmorEvidenceOracleTest(unittest.TestCase):
    def test_same_realm_equipment_keeps_fraction(self):
        for scale, expected in [(1,3),(1.5,4.5),(3,9),(6.6,19.8)]:
            self.assertAlmostEqual(expected,armor_damage(10*scale,20,8,scale))

    def test_heavy_and_cross_realm_examples(self):
        for args, expected in [((198,30,12,6.6),39.6),((198,20,8,6.6),99),
                               ((198,30,0,6.6),79.2),((66,20,8,1.5),42.24),
                               ((15,30,12,6.6),3),((60,30,12,1),16.8)]:
            self.assertAlmostEqual(expected,armor_damage(*args))

    def test_base_cap_boundary_and_breach_addition(self):
        self.assertAlmostEqual(66,armor_damage(330,30,12,6.6))
        self.assertGreater(armor_damage(331,30,12,6.6),331*.2)
        self.assertAlmostEqual(52.8,armor_damage(66,30,12,6.6,4))

    def test_native_zero_armor_and_zero_damage(self):
        self.assertEqual(66,armor_damage(66,0,0,6.6))
        self.assertEqual(0,armor_damage(0,30,12,6.6))
