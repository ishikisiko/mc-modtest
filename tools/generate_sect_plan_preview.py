#!/usr/bin/env python3
"""生成当前宗门蓝图的同源平面与三维预览；不生成旧台地方案。"""
import argparse
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'tools'))
from sect_blueprints.generate import export
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--input',type=Path,default=ROOT/'blueprints/sects/cloud_gate.json')
    p.add_argument('--out',type=Path,default=ROOT/'out/preview/sect-blueprints')
    a=p.parse_args();export(a.input,a.out)
