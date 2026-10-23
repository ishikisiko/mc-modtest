"""Failure boundaries of the standalone story-location blueprint checks."""
from pathlib import Path
import sys
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from qianzhen.build import check_pairs_and_supports, check_walks


class QianzhenChecks(unittest.TestCase):
    def test_water_is_not_a_walkable_floor(self):
        blocks={(0,0,0):'minecraft:water[level=0]'}
        with self.assertRaisesRegex(ValueError,'unsupported'):
            check_walks(blocks,{'entry':[0,1,0]},[1,4,1])

    def test_orphan_bed_is_rejected(self):
        with self.assertRaisesRegex(ValueError,'bed'):
            check_pairs_and_supports({(0,1,0):'minecraft:red_bed[facing=north,occupied=false,part=foot]'})

    def test_matching_bed_pair_is_preserved(self):
        result=check_pairs_and_supports({(0,1,1):'minecraft:red_bed[facing=north,occupied=false,part=foot]',
                                        (0,1,0):'minecraft:red_bed[facing=north,occupied=false,part=head]'})
        self.assertEqual(result['bed_halves'],2)

    def test_exposed_water_edge_is_rejected(self):
        with self.assertRaisesRegex(ValueError,'water opens'):
            check_pairs_and_supports({(1,1,1):'minecraft:water[level=0]',(1,0,1):'minecraft:stone'})

    def test_sealed_cell_allows_open_surface(self):
        blocks={(1,1,1):'minecraft:water[level=0]'}
        for pos in [(1,0,1),(0,1,1),(2,1,1),(1,1,0),(1,1,2)]:blocks[pos]='minecraft:stone'
        self.assertEqual(check_pairs_and_supports(blocks)['water_cells'],1)

    def test_target_behind_full_wall_is_rejected(self):
        blocks={(x,0,0):'minecraft:stone' for x in range(3)}
        blocks.update({(1,y,0):'minecraft:stone' for y in (1,2,3)})
        with self.assertRaisesRegex(ValueError,'unreachable'):
            check_walks(blocks,{'entry':[0,1,0],'clue':[2,1,0]},[3,5,1])


if __name__=='__main__':unittest.main()
