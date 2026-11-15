"""从作者输入导出东荒生态规则与小型据点。"""
from pathlib import Path
import argparse,json
ROOT=Path(__file__).resolve().parents[2]

def generate():
    data=json.loads((ROOT/'blueprints/wilderness/habitats.json').read_text())
    templates={}
    for kind in data['kinds']:
        blocks={}
        def put(x,y,z,state):blocks[(x,y,z)]=state
        radius=data['site_radius']
        # 明确的施工包络和净空；月度刷新只更新生物，不重放模板。
        for x in range(-radius,radius+1):
            for z in range(-radius,radius+1):
                if x*x+z*z>radius*radius+(x*13+z*7)%7-3:continue
                for y in range(0,5):put(x,y,z,'minecraft:air')
                put(x,-1,z,'minecraft:coarse_dirt' if kind['faction']=='bandit' else 'minecraft:moss_block')
        ident=kind['id']
        if ident=='bandit_camp':
            for x in range(-5,0):
                for z in range(-5,0):put(x,3,z,'minecraft:spruce_slab[type=bottom,waterlogged=false]')
            for x,z in [(-5,-5),(-1,-5),(-5,-1),(-1,-1)]:
                for y in range(3):put(x,y,z,'minecraft:spruce_log[axis=y]')
            for x in range(-4,0):
                for y in range(2):put(x,y,-5,'minecraft:spruce_planks')
            put(2,0,2,'minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]')
            put(-4,0,-3,'minecraft:barrel[facing=up,open=false]')
            for x in range(2,6):put(x,0,-4,'minecraft:spruce_fence[east=true,north=false,south=false,waterlogged=false,west=true]')
            put(5,1,-4,'minecraft:lantern[hanging=false,waterlogged=false]')
        elif ident in ('wolf_den','lizard_shelf'):
            for x in range(-5,6):
                for y in range(1+(x*x%3)):put(x,y,-5,'minecraft:mossy_cobblestone' if ident=='wolf_den' else 'minecraft:andesite')
            if ident=='wolf_den':
                put(4,0,4,'minecraft:bone_block[axis=x]');put(5,0,3,'minecraft:bone_block[axis=z]')
        else:
            for x,z in [(-4,-3),(3,-4),(-3,4),(4,3)]:
                put(x,0,z,'minecraft:dark_oak_log[axis=y]');put(x,1,z,'minecraft:brown_mushroom')
            put(0,0,0,'minecraft:shroomlight')
        for z in range(3,8):put(0,-1,z,'minecraft:gravel')
        templates[ident]=[dict(x=x,y=y,z=z,block=state)for(x,y,z),state in sorted(blocks.items())]
    data['templates']=templates
    return data

def main():
    p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');a=p.parse_args()
    path=ROOT/'src/main/resources/data/yuanxian/wilderness/habitats.json'
    content=json.dumps(generate(),ensure_ascii=False,indent=2)+'\n'
    if a.check:
        if not path.is_file() or path.read_text()!=content:raise SystemExit('东荒生态输出需要重新生成')
    else:path.parent.mkdir(parents=True,exist_ok=True);path.write_text(content)
if __name__=='__main__':main()
