"""读回城市中实际落位的分阁，检查围墙、唯一出入口、各层通行与屋面。"""
from collections import deque

import numpy as np

from qianji.enclosure import opening, perimeter, rules


def audit(model, town, blocks, states, road, public_paths, surface_heights, outside_reached):
    """blocks 和坐标均属于最终城市；以已能从市场抵达的院门外格为起点。"""
    from province_capitals.central.game_models import rotate_state
    x,z,X,Z=town["bounds"];base=town["base_y"]
    faces=("north","east","south","west");q=faces.index(town["facing"])
    # np.rot90 将城市方向恢复为作者方向。下标始终是 y/z/x。
    room=np.rot90(blocks[base:,z:Z,x:X],q,axes=(1,2))
    if tuple(room.shape[1:])!=(model.d,model.w):raise ValueError("院址尺寸与实际方块不符")
    if road[z:Z,x:X].any() or public_paths[z:Z,x:X].any():raise ValueError("公共道路穿院")
    if not outside_reached:raise ValueError("从市场到不了院门")
    palette={s:i for i,s in enumerate(states)}
    expected={s:palette[rotate_state(s,q)] for s in set(model.blocks.values())}
    for (xx,y,zz),s in model.blocks.items():
        if room[y,zz,xx]!=expected[s]:raise ValueError((town["variant"],"整院回读差异",xx,y,zz,s))
    gate_x,feet,gap=opening(model)
    solid=np.array([v.split("[",1)[0] not in ("minecraft:air","minecraft:water","minecraft:lava") and
                    not any(k in v for k in ("_fence","_sign","type=bottom")) for v in states])
    walk=np.zeros_like(room,dtype=bool)
    walk[1:-1]=solid[room[:-2]] & (room[1:-1]==0) & (room[2:]==0)
    out_x,_,out_z=town["outside"]
    dx,dz={"north":(0,-1),"east":(1,0),"south":(0,1),"west":(-1,0)}[town["facing"]]
    radius=len(gap)//2
    for lateral in range(-radius,radius+1):
        px,pz=out_x+dz*lateral,out_z-dx*lateral
        height_at=int(surface_heights[pz,px])
        if not road[pz,px] or abs(height_at-(base+feet-1))>1 or not solid[blocks[height_at,pz,px]] or np.any(blocks[height_at+1:height_at+3,pz,px]):
            raise ValueError((town["variant"],"街面与院门之间存在断口",px,pz))
    aperture=[]
    height=rules()["wall"]["height"]
    for a,b in perimeter(model.w,model.d):
        if walk[feet:feet+height,b,a].any():aperture.append((a,b))
    if set(aperture)!={(a,0)for a in gap}:raise ValueError(("院墙出现额外通口",aperture))
    goals={tuple(t["position"]) for t in model.targets}
    todo=set(goals);todo.add(tuple(model.spec["reader"]))
    seen=np.zeros_like(walk)
    queue=deque([(gate_x,feet,0)]);seen[feet,0,gate_x]=True
    while queue and todo:
        a,y,b=queue.popleft();todo.discard((a,y,b))
        for dx,dz in ((1,0),(-1,0),(0,1),(0,-1)):
            u,v=a+dx,b+dz
            if 0<=u<model.w and 0<=v<model.d:
                for level in (y,y+1,y-1):
                    if 0<level<room.shape[0]-1 and walk[level,v,u] and not seen[level,v,u]:
                        seen[level,v,u]=True;queue.append((u,level,v))
    if todo:raise ValueError((town["variant"],"院内不可达",sorted(todo)))
    roofs={}
    roof_ids=[palette[s]for s in (model.material["roof"],model.material["roof_slab"]) if s in palette]
    for spec in model.spec["rooms"]:
        a,b,w,d=spec["box"];inset=spec["levels"][-1]["inset"]
        a+=inset;b+=inset;w-=2*inset;d-=2*inset
        top=spec["floor_y"]+sum(level["height"]for level in spec["levels"])
        # 核对整层檐口投影，包括八角屋面的对角接缝。
        span=min(w,d)+4;checked=0
        for v in range(b-2,b+d+2):
            for u in range(a-2,a+w+2):
                if spec["roof"]=="pyramidal_roof":
                    edge_x=min(u-a+2,a+w+1-u);edge_z=min(v-b+2,b+d+1-v)
                    if edge_x+edge_z<span//5:continue
                if not np.isin(room[top:,v,u],roof_ids).any():
                    raise ValueError((town["variant"],spec["id"],"屋面开口",u,v))
                checked+=1
        roofs[spec["id"]]=checked
    bx,by,bz=town["board"]["position"]
    if states[blocks[by,bz,bx]]!="yuanxian:jianghu_board[facing="+town["facing"]+"]":raise ValueError("任务榜落点或朝向不符")
    return {"enclosed":True,"perimeter_columns":len(aperture)+town["wall_columns"],"gate_width":len(aperture),
            "gate_height":town["gate_height"],"public_through_cells":0,"reachable_targets":len(goals),
            "roof_columns":roofs,"structure_cells":len(model.blocks),"board_reader_reached":True}


def check_market_street(city, reached):
    from tools.province_capitals.inner_neighborhoods.qianji import stroke
    row=next(r for r in city.plan["qianji_layout"]["streets"] if r["id"]=="replan_05")
    if row["points"][0]!=row["previous"][0] or row["points"][-1]!=row["previous"][-1]:
        raise ValueError("北驿商街的原有街口改变")
    mask=stroke(city.road.shape,row["points"],row["width"])
    if not np.all(reached[mask]):raise ValueError(("北驿商街路幅存在阻挡",int((mask&~reached).sum())))
    if np.any(city.waterfront_ground & mask):raise ValueError("北驿商街覆盖泉岸")
    heights=city.street_height
    steps=[]
    for axis in (0,1):
        left=np.take(mask,range(0,mask.shape[axis]-1),axis=axis)
        right=np.take(mask,range(1,mask.shape[axis]),axis=axis)
        steps.append(int(np.abs(np.diff(heights,axis=axis))[left & right].max()))
    if max(steps)>1:raise ValueError(("北驿商街出现断阶",steps))
    return {"name":row["name"],"maximum_step":max(steps),"endpoints":[row["points"][0],row["points"][-1]],"width":row["width"],
            "road_columns":int(mask.sum()),"points":row["points"]}
