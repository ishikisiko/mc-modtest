#!/usr/bin/env python3
"""Render the exact generated static cauldron mesh; no game, shaders or invented detail."""
from pathlib import Path
import argparse
import json
import sys
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))

def main():
    import numpy as np
    from PIL import Image
    from tools.beastgen.preview import Frame, render
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',type=Path,default=ROOT/'out/preview/alchemy_array/sculpted/model')
    args=parser.parse_args();args.out.mkdir(parents=True,exist_ok=True)
    mesh=json.loads((ROOT/'src/main/resources/assets/myvillage/alchemy_array/formed_mesh.json').read_text())
    texture=np.asarray(Image.open(ROOT/'src/main/resources/assets/myvillage/textures/block/alchemy_array_formed.png').convert('RGBA'),dtype=np.float32)/255
    quads=[]
    for face in mesh['faces']:
        i=face['m'];x,y=(i%4)*4,(i//4)*4
        uv=[(x,y),(x+4,y),(x+4,y+4),(x,y+4)]
        quads.append((np.array(face['p'],dtype=float),uv,texture,None))
    for name,camera in [('front',(0,.12,-1)),('quarter',(-.9,.42,-1.5)),('side',(-1,.12,0)),('rear',(.9,.42,1.5))]:
        render(quads,Frame(camera,900,800,155,(0,.45,0),ss=1),bg=(193,189,176),ground=False,body_rot=180,cull=False).save(args.out/(name+'.png'))
    print('Exact static mesh preview:',args.out)

if __name__=='__main__':main()
