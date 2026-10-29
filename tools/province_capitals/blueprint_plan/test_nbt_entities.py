"""招牌文字与 NBT 数值类型在原生蓝图重打包后保持原字节。"""
from pathlib import Path
import tempfile
import unittest

import json_to_nbt as nbt
from buildgen.nbtread import read_gzipped_nbt
from .nbt_entities import block_entities, write_tile


class BlockEntityTests(unittest.TestCase):
    def source(self, path):
        sign = nbt.Compound({'front_text': nbt.Compound({
            'has_glowing_text': nbt.Byte(0), 'color': nbt.String('black'),
            'messages': nbt.ListTag(nbt.TAG_STRING, [nbt.String('{"text":"药堂"}')]*4)}),
            'is_waxed': nbt.Byte(1)})
        root = nbt.Compound({
            'palette': nbt.ListTag(nbt.TAG_COMPOUND, [nbt.palette_state_to_tag('minecraft:oak_sign[rotation=0,waterlogged=false]')]),
            'blocks': nbt.ListTag(nbt.TAG_COMPOUND, [nbt.Compound({
                'pos': nbt.ListTag(nbt.TAG_INT, [nbt.Int(v) for v in (1,2,3)]),
                'state': nbt.Int(0), 'nbt': sign})]),
            'entities': nbt.ListTag(nbt.TAG_COMPOUND, [])})
        nbt.write_gzipped_nbt(root,str(path))

    def test_same_block_keeps_exact_nested_nbt_with_property_changes(self):
        with tempfile.TemporaryDirectory() as folder:
            source, out = Path(folder)/'source.nbt', Path(folder)/'out.nbt'
            self.source(source); before = block_entities(source)
            counts = write_tile(out, {(1,2,3):('minecraft:oak_sign[rotation=4,waterlogged=false]','','')}, 16, before)
            self.assertEqual((1,1,1), counts)
            self.assertEqual(before,block_entities(out))
            raw=read_gzipped_nbt(str(out))[1]
            self.assertEqual('{"text":"药堂"}',raw['blocks'][0]['nbt']['front_text']['messages'][0])

    def test_replaced_block_cannot_inherit_foreign_block_entity(self):
        with tempfile.TemporaryDirectory() as folder:
            source, out = Path(folder)/'source.nbt', Path(folder)/'out.nbt'
            self.source(source)
            counts=write_tile(out,{(1,2,3):('minecraft:air','','')},16,block_entities(source))
            self.assertEqual(0,counts[2])
            self.assertEqual({},block_entities(out))


if __name__=='__main__':unittest.main()
