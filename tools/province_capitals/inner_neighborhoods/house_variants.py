"""在原院宅的一开间外廊上作地方变体。多格门、原屋面构件及室内设施保留各自的方块状态。"""
from functools import lru_cache

import numpy as np

from .plan import HOUSES, read, rotate
from province_capitals.central.game_models import rotate_state

# 门洞旁可接构件的一开间，主屋屋面范围，屋脊方向。坐标均为原模板局部坐标。
BAYS={
    "bamboo":((20,8,6),(11,31,41,48),"x"),
    "terrace":((14,18,6),(15,27,36,44),"x"),
    "stream":((33,19,7),(3,16,20,39),"z"),
    "pine_gallery":((13,27,5),(9,29,39,45),"x"),
    "terracotta_longhouse":((8,8,6),(3,11,33,35),"x"),
    "stone_terrace":((6,8,7),(27,23,43,39),"z")
}
MODEL_ROOT=HOUSES.parents[3]/"assets/yuanxian/models/block/housing"


@lru_cache(maxsize=1024)
def roof_shape(state):
    name=state.partition("[")[0]
    props=dict(bit.split("=") for bit in state.partition("[")[2].rstrip("]").split(",") if bit)
    if name.startswith("yuanxian:housing_details_"):
        group=name.rsplit("_",1)[1]
        shape=read(MODEL_ROOT/(group+"_"+props["variant"]+".json"))
        if shape["textures"]["particle"].split("/")[-1] not in ("dark_oak_planks","deepslate_tiles","oxidized_cut_copper"):
            return None
        elements=shape["elements"]
        if len(elements)==1:
            e=elements[0]
            return {((0,0,0),(16,8,16)):"bottom",((0,8,0),(16,16,16)):"top"}.get((tuple(e["from"]),tuple(e["to"])))
    if name in ("minecraft:deepslate_tile_slab","minecraft:dark_oak_slab","minecraft:spruce_slab"):
        return props.get("type")
    if name in ("minecraft:deepslate_tiles","minecraft:dark_oak_planks"):
        return "full"
    return None


def tile_state(kind,shape):
    if shape=="full":
        return {"deepslate_brick":"minecraft:deepslate_bricks","deepslate_tile":"minecraft:deepslate_tiles",
                "sandstone":"minecraft:smooth_sandstone","polished_blackstone_brick":"minecraft:polished_blackstone_bricks"}[kind]
    return "minecraft:"+kind+"_slab[type="+shape+",waterlogged=false]"


def apply_variant(city,o,home):
    profile=city.profile
    ox,oz,_,_=o["box"];w,_,d=home["size"];floor=o["grade"]+1;q=o["turn"]
    blocks=city.blocks
    protected={tuple(p) for a in home["assemblies"] for p in a["parts"]}
    beds={tuple(p) for p in home["beds"]}
    gateway=home["entry"][0]
    bay,roof,axis=BAYS[o["id"]]
    a,b,A,B=roof
    counts={"wall":0,"foot":0,"timber":0,"roof":0,"porch":0,"snow":0}
    def pos(u,v):
        dx,dz=rotate(u,v,w,d,q)
        return ox+dx,oz+dz
    def replace(u,y,v,state,slot):
        x,z=pos(u,v)
        code=city.code(rotate_state(state,q))
        if blocks[floor+y,z,x]!=code:
            blocks[floor+y,z,x]=code;counts[slot]+=1
    skyline={}
    for u,y,v,pi in home["blocks"]:
        if y<0 or (u,y,v) in protected or (u,y,v) in beds:continue
        state=home["palette"][pi];name=state.partition("[")[0]
        if a<=u<A and b<=v<B and y>=5:skyline[u,v]=(max(y,skyline.get((u,v),-100)))
        if name.startswith("minecraft:stripped_") and name.endswith("_log"):
            replace(u,y,v,"minecraft:"+profile["frame"]+state[len(name):],"timber")
        elif name=="minecraft:stone_bricks" and y<=1:
            replace(u,y,v,profile["foot"],"foot")
        elif name in ("minecraft:calcite","minecraft:smooth_sandstone","minecraft:red_terracotta","minecraft:birch_planks") and y>=2:
            replace(u,y,v,profile["wall"],"wall")
        if a<=u<A and b<=v<B and y>=5:
            along=(u-a,A-1-u) if axis=="x" else (v-b,B-1-v)
            across=(v-b,B-1-v) if axis=="x" else (u-a,A-1-u)
            if min(along)<=1 or min(across)<=2 or abs(across[0]-across[1])<=2:
                shape=roof_shape(state)
                if shape:replace(u,y,v,tile_state(profile["tile"],shape),"roof")
    left,back,width=bay
    timber=profile["timber"]
    foot=profile["foot"];log="minecraft:"+profile["frame"]+"[axis=y]"
    beam="minecraft:"+profile["frame"]+"[axis=x]"
    board="minecraft:"+timber+"_planks"
    cap="minecraft:"+timber+"_slab[type=bottom,waterlogged=false]"
    def add(i,y,j,state):
        u,v=left+i,back+j
        if not (0<=u<w and 0<=v<d) or abs(u-gateway)<=1 and v<home["entry"][2]+2:return
        if (u,y,v) in protected or (u,y,v) in beds:return
        x,z=pos(u,v)
        if blocks[floor+y,z,x]==0:replace(u,y,v,state,"porch")
    def column(i,j,height,plinth=1):
        for y in range(height+1):add(i,y,j,foot if y<plinth else log)
    def canopy(cover,over=2):
        for i in (0,width-1):
            column(i,-over,4)
            for j in range(-over,1):add(i,4,j,"minecraft:"+profile["frame"]+"[axis=z]")
        for i in range(width):
            add(i,4,-over,beam)
            for j in range(-over,1):add(i,5,j,cover)
    kind=profile["porch"]
    if kind=="驿廊":
        canopy(cap,3)
        for i in range(2,width-1):add(i,1,-3,"minecraft:dark_oak_slab[type=bottom,waterlogged=false]")
        for i in (1,width-2):add(i,3,-2,"minecraft:lantern[hanging=true,waterlogged=false]")
    elif kind=="风障":
        canopy(cap,2)
        for j in (-2,-1,0):
            for y in range(3):add(0,y,j,foot if y==0 else board)
        for i in range(1,width-1):add(i,0,-2,"minecraft:spruce_slab[type=bottom,waterlogged=false]")
        for (u,v),y in skyline.items():
            half=(v-b)/(B-b) if axis=="x" else (u-a)/(A-a)
            along=(u-a)/(A-a) if axis=="x" else (v-b)/(B-b)
            if not(.52<=half<=.88 and .12<=along<=.81):continue
            x,z=pos(u,v);state=city.states[blocks[floor+y,z,x]]
            if roof_shape(state) in ("full","top") and blocks[floor+y+1,z,x]==0:
                replace(u,y+1,v,"minecraft:snow[layers=1]","snow")
    elif kind=="石槛":
        for i in range(width):
            add(i,0,-2,foot);add(i,0,-1,foot)
            if i in (0,1,width-1):add(i,1,-2,"minecraft:polished_tuff_slab[type=bottom,waterlogged=false]")
        for j in range(-2,1):
            add(0,1,j,"minecraft:tuff_bricks")
        for i in range(2,width-1):add(i,0,-3,"minecraft:stone_brick_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]")
    elif kind=="港廊":
        canopy("minecraft:waxed_exposed_cut_copper_slab[type=bottom,waterlogged=false]",2)
        for i in range(1,width-1):add(i,3,-2,"minecraft:spruce_trapdoor[facing=north,half=top,open=true,powered=false,waterlogged=false]")
        add(1,0,-1,"minecraft:barrel[facing=east,open=false]")
    elif kind=="花墙":
        canopy("minecraft:smooth_sandstone_slab[type=bottom,waterlogged=false]",3)
        for j in range(-3,1):
            for y in range(3):
                if (y+j)%2==0 or y==0:add(0,y,j,"minecraft:cut_sandstone")
        for i in range(1,width-1):add(i,3,-3,"minecraft:cut_sandstone_slab[type=top,waterlogged=false]")
    elif kind=="晒药廊":
        canopy("minecraft:bamboo_mosaic_slab[type=bottom,waterlogged=false]",3)
        for i in range(width):
            for j in (-2,-1):add(i,0,j,"minecraft:jungle_slab[type=bottom,waterlogged=false]")
        for i in range(1,width-1):
            add(i,2,-3,"minecraft:bamboo_trapdoor[facing=north,half=top,open=false,powered=false,waterlogged=false]")
        for i in (0,width-1):add(i,3,-3,"minecraft:stripped_bamboo_block[axis=y]")
    elif kind=="炉街门垛":
        for i in (0,width-1):
            for j in (-2,-1):
                for y in range(4):add(i,y,j,"minecraft:waxed_cut_copper" if y==2 else foot)
                add(i,4,j,"minecraft:polished_blackstone_brick_slab[type=bottom,waterlogged=false]")
        for i in range(width):add(i,4,-1,beam)
        add(0,5,-2,"minecraft:lantern[hanging=false,waterlogged=false]")
    if not counts["porch"]:raise ValueError((city.region,o["id"],"地方构件未落位"))
    for u,y,v,pi in home["blocks"]:
        if (u,y,v) not in protected and (u,y,v) not in beds:continue
        x,z=pos(u,v)
        expected=rotate_state(home["palette"][pi],q)
        if city.states[blocks[floor+y,z,x]]!=expected:
            raise ValueError((city.region,o["id"],(u,y,v),"原有门构件或床位发生变化"))
    return counts
