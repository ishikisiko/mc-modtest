import unittest
from tools.advanced_fates_evidence import parse_scalar
from tools.advanced_fates_fire import parse_fire_logs
from tools.advanced_fates_sword_heart import _cancelled


class AdvancedFateEvidenceTest(unittest.TestCase):
    def test_nbt_numeric_suffixes_are_not_lost(self):
        for value, expected in [('0s',0),('0b',0),('-20s',-20),('120L',120),('1.5f',1.5),('1.5d',1.5),('1e2d',100)]:
            with self.subTest(value=value):
                self.assertEqual(expected,parse_scalar('Player has the following entity data: '+value+'\n'))
    def test_scoreboard_and_attribute_readbacks(self):
        self.assertEqual(0,parse_scalar('CaptureDev has 0 [adv_wind_deaths]\n'))
        self.assertEqual(.125,parse_scalar('Value of attribute Movement Speed for entity CaptureDev is 0.125\n'))
    def test_bad_or_compound_readbacks_are_rejected(self):
        for value in ('No player was found','Husk has the following entity data: {Health:20f}',
                      'Husk has the following entity data: [1,2,3]','Husk has the following entity data: NaNd'):
            with self.subTest(value=value),self.assertRaises(ValueError):parse_scalar(value)
    def test_observed_neoforge_poison_source_is_explicit(self):
        rows=[{'kind':'DODGE_DEBUG','t':'1947','cancelled_damage':'1.00','source':'neoforge:poison'}]
        self.assertEqual(rows,_cancelled(rows,'neoforge:poison'))
        self.assertEqual([],_cancelled(rows,'minecraft:magic'))
    def test_fire_receipts_keep_primary_separate_from_derivatives(self):
        text='FIRE_SPIRIT_HIT owner=a target=b tick=10 primary=9.0 weapon_damage=9.0\nFIRE_SPIRIT_DAMAGE owner=a target=b tick=30 kind=burn requested=0.6 actual=0.6 accepted=true'
        rows=parse_fire_logs(text)
        self.assertEqual(['hit','damage'],[r['event'] for r in rows])
        self.assertEqual('9.0',rows[0]['primary']);self.assertNotIn('primary',rows[1])
