"""从现有七城资源制作住宅街坊设计稿、方块体积和街面回读。"""
import argparse
import hashlib
import json
from pathlib import Path

import numpy as np

from .plan import DESIGNS, HOUSES, Neighborhood, read, write
from .blocks import BlockCity
from .ground import PROFILES
from .readback import check
from road_port_sources import CitySource
from qianji.enclosure import ROOT, RULES, variants


def load_source(resources,region):
    source=CitySource(resources,region)
    city=source.city
    w,h,d=city["size"]
    ground=np.asarray(source.terrain,dtype=np.int16).reshape(d,w)
    states=["minecraft:air"]
    ids={s:0 for s in ("minecraft:air","minecraft:cave_air","minecraft:void_air")}
    def code(state):
        if state not in ids:
            ids[state]=len(states)
            states.append(state)
        return ids[state]
    terrain=city["terrain"]
    top,sub,fill=[code(terrain[k]) for k in ("top_block","subsurface_block","fill_block")]
    grid=np.zeros((h+2,d+2,w+2),np.uint16)
    volume=grid[1:-1,1:-1,1:-1]
    for y in range(int(ground.max())+1):
        layer=volume[y]
        layer[y<ground-terrain["subsurface_depth"]]=fill
        layer[(y>=ground-terrain["subsurface_depth"])&(y<ground)]=sub
        layer[y==ground]=top
    total=0
    for (tx,tz) in source.tiles:
        source.block(tx*16,0,tz*16)
        cells=source.cache.pop((tx,tz))
        for (x,y,z),state in cells.items():
            volume[y,tz*16+z,tx*16+x]=code(state)
        total+=len(cells)
    return city,hashlib.sha256(source.raw).hexdigest(),ground,states,grid,total


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--resources",type=Path,required=True,help="现有城市导出的 resources 目录")
    p.add_argument("--output",type=Path,required=True,help="设计稿的独立输出目录")
    p.add_argument("--structures",action="store_true",help="导出整城的高度场、原生 NBT 切片和带匾额的独立围院模板并逐块回读")
    p.add_argument("--volume",action="store_true",help="额外保存带有完整方块状态的 y/z/x 三维体积")
    p.add_argument("cities",nargs="*")
    args=p.parse_args()
    designs=read(DESIGNS)
    for region in args.cities or designs:
        config=designs[region];dest=args.output/region
        source=load_source(args.resources,region)
        Neighborhood(region,config,source[0]).export(dest)
        write(dest/"inputs.json",{
            "manifest":source[1],"streetscape":hashlib.sha256(json.dumps(read(PROFILES)[region],ensure_ascii=False,sort_keys=True).encode()).hexdigest(),"design":hashlib.sha256(json.dumps(config,ensure_ascii=False,sort_keys=True).encode()).hexdigest(),
            "qianji":{str(path.relative_to(ROOT)):hashlib.sha256(path.read_bytes()).hexdigest() for path in (
                RULES,ROOT/"blueprints/qianji"/(variants()[region]+".json"),ROOT/"blueprints/qianji/materials.json",
                ROOT/"blueprints/qianji/lighting.json",ROOT/"tools/qianji/model.py",ROOT/"tools/qianji/enclosure.py",
                ROOT/"tools/province_capitals/inner_neighborhoods/qianji.py",ROOT/"exmod/mod_block_catalog.json")},
            "houses":{tid:hashlib.sha256((HOUSES/(tid+".json")).read_bytes()).hexdigest() for tid,_,_ in config["homes"]}})
        city=BlockCity(source,dest).assemble()
        write(dest/"streetscape.json",{"sites":city.ground_sites,"variants":city.variants,
                                       "description":city.profile["description"],"variant_note":city.profile["variant_note"]})
        np.savez_compressed(dest/"surfaces.npz",roles=city.surface_roles,ground=city.ground,waterfront=city.waterfront_ground,reserve=city.reserve_ground)
        write(dest/"qianji.json",city.qianji)
        actual=check(city)
        write(dest/"street-readback.json",actual)
        if not all([actual["market_front"],actual["qianji_front"],*actual["home_fronts"].values(),*actual["plot_fronts"].values(),*actual["public_courts"].values()]):
            raise ValueError(region+" 的宅院、宅田或千机阁与市场之间缺少可用街面，请查看 street-readback.json")
        if args.structures:
            from .structures import emit
            emit(city,dest/"structures",args.resources)
        if args.volume:
            from .structures import canonical
            states=["minecraft:air",*sorted({canonical(s) for s in city.states}-{"minecraft:air"})]
            index={state:i for i,state in enumerate(states)}
            code=np.asarray([index[canonical(s)] for s in city.states],dtype=np.uint16)
            np.savez_compressed(dest/"blocks.npz",blocks=code[city.blocks],palette=np.asarray(states))
        print(f"{region}　{sum(actual['home_fronts'].values())} 座院宅可从市场街抵达",flush=True)


if __name__=="__main__":
    main()
