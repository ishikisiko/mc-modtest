import unittest
from tools.alchemy_evidence import stacks, medicine

class AlchemyEvidenceTest(unittest.TestCase):
    def test_reads_block_and_player_stacks_with_typed_slots(self):
        value = '[{Slot: 0b, id: "myvillage:formula_huiling", count: 1}, {count: 2, Slot: 4b, id: "myvillage:pill_huiling"}]'
        for prefix in ('0, -60, 3 has the following block data: ', 'Player has the following entity data: '):
            self.assertEqual(stacks(prefix+value), {0: {'id':'myvillage:formula_huiling','count':1},4:{'id':'myvillage:pill_huiling','count':2}})
        self.assertEqual(stacks('Player has the following entity data: []'), {})

    def test_missing_or_partial_inventory_cannot_pass_as_empty(self):
        for value in ('Could not get the block entity', 'has the following block data: [{Slot: 0b}]',
                      'data: [{Slot: 0b,id:"a:b",count:1},{Slot:0b,id:"a:b",count:1}]'):
            with self.assertRaises(ValueError): stacks(value)

    def test_medicine_readback_distinguishes_empty_from_error(self):
        self.assertEqual(medicine('Found no elements matching NeoForgeData.myvillage_medicine')['cooldown_ticks'], 0)
        self.assertEqual(medicine('Player has the following entity data: {effect: "ningshen", cooldown_ticks: 483, effect_ticks: 1083, healing_elapsed: 0}')['effect_ticks'], 1083)
        for value in ('No entity was found', 'data: {effect:"ningshen",effect_ticks:900}'):
            with self.assertRaises(ValueError): medicine(value)

if __name__ == '__main__': unittest.main()
