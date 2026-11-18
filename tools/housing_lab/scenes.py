"""生成 GameTest 使用的空白场景；正式住宅仍从住宅目录加载。"""
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT))
from tools.json_to_nbt import Compound,Int,ListTag,String,TAG_INT,TAG_COMPOUND,write_gzipped_nbt
path=ROOT/'src/dropProbe/resources/data/yuanxian_drop_probe/structure/housing_lab.nbt';path.parent.mkdir(parents=True,exist_ok=True)
write_gzipped_nbt(Compound({'DataVersion':Int(3955),'size':ListTag(TAG_INT,[Int(64),Int(48),Int(64)]),
    'palette':ListTag(TAG_COMPOUND,[Compound({'Name':String('minecraft:air')})]),'blocks':ListTag(TAG_COMPOUND,[]),'entities':ListTag(TAG_COMPOUND,[])}),str(path))
