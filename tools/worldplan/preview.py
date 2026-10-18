#!/usr/bin/env python3
"""Export seed atlases using the actual Java planner, without Gradle or Minecraft."""
from __future__ import annotations
import argparse
from pathlib import Path
import subprocess
import sys
import os

ROOT=Path(__file__).resolve().parents[2]
REGION=['RegionGraph','GenRegion','GenEdge','IntRange','RegionQueries','RegionPlacement','RegionContract','RegionJson','RegionGeometry']
def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--seed',type=int,default=20261017)
    p.add_argument('--out',type=Path,default=ROOT/'out/preview/worldplan/20261017')
    p.add_argument('--gson',type=Path);p.add_argument('--radius',type=int,default=4640);p.add_argument('--resolution',type=int,default=256)
    a=p.parse_args()
    if not -(1<<63)<=a.seed<(1<<63):p.error('seed must be a signed 64-bit integer')
    jars=([a.gson] if a.gson else sorted(Path.home().joinpath('.gradle').rglob('gson-*.jar')))
    if not jars or not jars[0].is_file():p.error('Gson is not cached. Run a cloud build first or supply --gson /path/to/gson.jar.')
    subprocess.run([sys.executable,'tools/worldplan/generate_assets.py'],cwd=ROOT,check=True)
    target=ROOT/'build/worldplan/classes';target.mkdir(parents=True,exist_ok=True);java=ROOT/'src/main/java/com/example/myvillage'
    sources=[java/'region/runtime'/f'{name}.java' for name in REGION]+sorted((java/'worldplan').glob('*.java'))
    subprocess.run(['javac','-encoding','UTF-8','-cp',str(jars[0]),'-d',str(target),*map(str,sources)],cwd=ROOT,check=True)
    subprocess.run(['java','-Djava.awt.headless=true','-cp',os.pathsep.join([str(target),str(jars[0])]),'com.example.myvillage.worldplan.WorldPlanCli',str(a.seed),str(ROOT/'build/generated/worldplan/resources/data/myvillage/worldplan/frozen_v4.json'),str(a.out),str(a.radius),str(a.resolution)],cwd=ROOT,check=True)
if __name__=='__main__':main()
