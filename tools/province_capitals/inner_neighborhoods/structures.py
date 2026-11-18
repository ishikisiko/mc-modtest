"""把已核对的围院与整城布局导出为 Minecraft 原生结构 NBT，并从文件重建方块核对。"""
from collections import defaultdict
from functools import lru_cache
import gzip
import hashlib
from pathlib import Path

import numpy as np

from .plan import rotate, write
from buildgen.nbtread import read_gzipped_nbt, state_string
from province_capitals.blueprint_plan.nbt_entities import block_entities, write_tile
from province_capitals.game.compile import resource_file
from qianji.runtime_export import sign_payload


def digest(data):
    return hashlib.sha256(data).hexdigest()


@lru_cache(maxsize=16384)
def canonical(state):
    name,bracket,props=state.partition("[")
    return name+"["+",".join(sorted(props[:-1].split(",")))+"]" if bracket else name


def source_entities(city, resources):
    """随完整原楼搬运已有方块实体。新分阁的匾额由本次整阁模型产生。"""
    by_chunk=defaultdict(dict)
    for row in city.city["tiles"]:
        file=resource_file(resources,row["template"],template=True)
        for (x,y,z),payload in block_entities(file).items():
            by_chunk[row["x"]//16,row["z"]//16][x+row["x"],y,z+row["z"]]=payload
    result={}
    for o in city.objects:
        if "source" not in o:continue
        a,b,A,B=o["source"];x,z,X,Z=o["box"];turn=o.get("turn",0)
        if o["type"]=="anchor":
            low,high=0,city.h;delta=o["delta"]
        else:
            low,high=o["source_y"];floor=o["source_floor"]
            if o.get("family") in ("stilt_longhouse","pier_warehouse"):
                floor=int(np.median(city.source_ground[b:B,a:A]))
            delta=o["grade"]-floor
        for tx in range(a//16,(A-1)//16+1):
            for tz in range(b//16,(B-1)//16+1):
                for (u,y,v),data in by_chunk[tx,tz].items():
                    if not (a<=u<A and b<=v<B and low<=y<high and 0<=y+delta<city.h):continue
                    du,dv=rotate(u-a,v-b,A-a,B-b,turn)
                    pos=x+du,y+delta,z+dv
                    if city.states[city.blocks[pos[1],pos[2],pos[0]]].partition("[")[0]==data["block"]:
                        if pos in result and result[pos]!=data:raise ValueError(("方块实体落位冲突",pos))
                        result[pos]=data
    for s in city.qianji["signs"]:
        result[tuple(s["position"])]={"block":"minecraft:dark_oak_wall_sign","payload":sign_payload(s["text"])}
    return result


def check_nbt(path,size,cells,entities):
    _,nbt=read_gzipped_nbt(str(path))
    if nbt["size"]!=list(size) or len(nbt["blocks"])!=len(cells):raise ValueError(("结构大小或方块数量失配",path))
    palette=[state_string(v) for v in nbt["palette"]]
    found={tuple(cell["pos"]):palette[cell["state"]] for cell in nbt["blocks"]}
    if len(found)!=len(cells) or any(found.get(p)!=canonical(v[0]) for p,v in cells.items()):
        raise ValueError(("结构文件回读差异",path))
    if block_entities(path)!=entities:raise ValueError(("方块实体回读差异",path))
    return found


def emit(city,directory,resources):
    directory=Path(directory);directory.mkdir(parents=True,exist_ok=True)
    states=city.states;volume=city.blocks
    objects=source_entities(city,resources)
    ox,oz,X,Z=city.qianji["bounds"];base=city.qianji["base_y"]
    obj=next(o for o in city.objects if o["type"]=="qianji")
    model=city.qianji_model;turn=obj["turn"]
    cells={}
    for u,y,v in model.blocks:
        dx,dz=rotate(u,v,model.w,model.d,turn)
        cells[dx,y,dz]=(states[volume[base+y,oz+dz,ox+dx]],"qianji","court")
    signs={(x-ox,y-base,z-oz):data for (x,y,z),data in objects.items() if ox<=x<X and oz<=z<Z and base<=y<base+model.h}
    size=(X-ox,model.h,Z-oz)
    compound=directory/"compound.nbt"
    write_tile(compound,cells,model.h,signs,size=size)
    check_nbt(compound,size,cells,signs)
    terrain=city.city["terrain"]
    foundation=[city.code(terrain[k]) for k in ("top_block","subsurface_block","fill_block")]
    ground=city.ground.astype("<i2")
    terrain_file=directory/"terrain.bin.gz"
    terrain_file.write_bytes(gzip.compress(ground.tobytes(),mtime=0))
    if gzip.decompress(terrain_file.read_bytes())!=ground.tobytes():raise ValueError("城镇地形回读差异")
    by_tile=defaultdict(dict)
    for (x,y,z),data in objects.items():
        by_tile[x//16,z//16][x%16,y,z%16]=data
    canonical_ids={state:index for index,state in enumerate(sorted({canonical(s) for s in states}))}
    normalize=np.asarray([canonical_ids[canonical(s)] for s in states],np.uint16)
    levels=np.arange(city.h,dtype=np.int16)[:,None,None]
    file_rows=[];counts=0;entity_count=0
    for z in range(0,city.d,16):
        for x in range(0,city.w,16):
            heights=ground[None,z:z+16,x:x+16]
            blank=np.zeros((city.h,16,16),np.uint16)
            blank[levels<heights-terrain["subsurface_depth"]]=foundation[2]
            blank[(levels>=heights-terrain["subsurface_depth"])&(levels<heights)]=foundation[1]
            blank[levels==heights]=foundation[0]
            tile=volume[:,z:z+16,x:x+16]
            top=city.h-1-np.argmax(tile[::-1]!=0,axis=0)
            # 屋下、桥下和门廊的空气明确写入。露天地面使用高度场与真实铺装。
            changed=(tile!=blank)|((tile==0)&(levels>heights)&(levels<top))
            rows={(int(u),int(y),int(v)):(states[tile[y,v,u]],"town","layout") for y,v,u in np.argwhere(changed)}
            entities=by_tile[x//16,z//16]
            for pos,data in entities.items():
                u,y,v=pos
                if pos not in rows:rows[pos]=(states[tile[y,v,u]],"town","object")
            if not rows:
                if not np.array_equal(normalize[tile],normalize[blank]):raise ValueError(("空切片回读差异",x,z))
                continue
            name=f"tiles/tile_{x//16}_{z//16}.nbt";path=directory/name
            n,colors,b=write_tile(path,rows,city.h,entities)
            decoded=check_nbt(path,(16,city.h,16),rows,entities)
            restored=normalize[blank]
            for (u,y,v),state in decoded.items():
                restored[y,v,u]=canonical_ids[state]
            if not np.array_equal(restored,normalize[tile]):
                raise ValueError(("高度场与切片未能复原整城",x,z))
            file_rows.append({"file":name,"x":x,"z":z,"sha256":digest(path.read_bytes()),"blocks":n})
            counts+=n;entity_count+=b
    record={"format":"minecraft_structure_templates","region":city.region,"size":city.plan["size"],
            "terrain":{**{k:terrain[k] for k in ("top_block","subsurface_block","fill_block","subsurface_depth")},
                       "file":terrain_file.name,"encoding":"i16le_z_major_top_solid","sha256":digest(terrain_file.read_bytes())},
            "compound":{"file":compound.name,"sha256":digest(compound.read_bytes()),"size":size,"origin":[ox,base,oz],"signs":len(signs)},
            "tiles":file_rows,"tile_blocks":counts,"preserved_block_entities":entity_count,"entry":city.qianji["entry"],"board":city.qianji["board"]}
    write(directory/"layout.json",record)
    return record
