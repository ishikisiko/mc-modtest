"""检查正式方块的站位、通路台阶和补光；原生碰撞与实际亮度由游戏另验。"""
from __future__ import annotations
import gzip,json,math
from .game import Voxels
from .model import read,build
from .combat import SCENES


def audit(model):
    vox=Voxels(model['size']);vox.palette=model['palette'];vox.ids={s:i for i,s in enumerate(vox.palette)}
    for x,y,z,height,material in model['runs']:
        for yy in range(y,y+height):vox.data[(yy*vox.d+z)*vox.w+x]=material
    empty=lambda state:state=='minecraft:air' or state.startswith('minecraft:light[')
    failures=[];checks=0
    def stand(p,tag):
        nonlocal checks
        x,y,z=map(round,p);checks+=1
        if empty(vox.get(x,y-1,z)):failures.append({'kind':'没有足下支撑','at':p,'tag':tag})
        for yy in (y,y+1):
            if not empty(vox.get(x,yy,z)):failures.append({'kind':'站立空间被占用','at':[x,yy,z],'tag':tag,'block':vox.get(x,yy,z)})
    for key,p in model['anchors'].items():stand(p,key)
    for group in model['encounters']:
        for actor in group['actors']:stand(actor['pos'],group['id'])
    for x,y,z in model['lighting_probes']:
        checks+=1
        if not any(vox.get(x,yy,z).startswith('minecraft:light[') for yy in (y,y+1,y+2)):
            failures.append({'kind':'检查点缺少直接补光','at':[x,y,z]})
    design=build(model['id']);scene=SCENES[design['spec']['construction']](design['spec'],read('catalog'))
    samples=0
    for edge in model['routes']:
        previous=None
        for a,b in zip(edge['points'],edge['points'][1:]):
            count=max(1,math.ceil(math.dist(a,b)*4))
            for i in range(count+1):
                x,z=[math.floor(a[k]+(b[k]-a[k])*i/count) for k in (0,2)]
                y=math.ceil(scene.walk_level(x+.5,z+.5)[1]);point=[x,y,z];stand(point,edge['a']+'→'+edge['b']);samples+=1
                if previous and abs(y-previous[1])>1:failures.append({'kind':'通路出现超过一格的落差','at':point,'from':previous})
                if previous and y>previous[1] and 'stairs[' not in vox.get(x,y-1,z):
                    failures.append({'kind':'上行位置缺少楼梯','at':point,'from':previous,'block':vox.get(x,y-1,z)})
                previous=point
    return {'id':model['id'],'checks':checks,'route_samples':samples,'failures':failures,'passed':not failures}


def main():
    import argparse
    from pathlib import Path
    p=argparse.ArgumentParser();p.add_argument('--resources',type=Path,required=True);p.add_argument('--out',type=Path,required=True);args=p.parse_args()
    reports=[]
    for name in read('runtime')['maps']:
        model=json.loads(gzip.decompress((args.resources/f'data/myvillage/expedition/{name}.json.gz').read_bytes()))
        reports.append(audit(model))
    args.out.parent.mkdir(parents=True,exist_ok=True);args.out.write_text(json.dumps(reports,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps([dict(id=r['id'],passed=r['passed'],checks=r['checks'],failures=len(r['failures'])) for r in reports],ensure_ascii=False))
    if any(not r['passed'] for r in reports):raise SystemExit(1)

if __name__=='__main__':main()
