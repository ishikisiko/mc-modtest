"""在街坊内保留整个分阁。仅迁排占用院址、门前街和完整改线路幅的地块。"""

from collections import deque

import numpy as np

from .plan import DIR, FACES, center, read, rotate, side_point
from qianji.enclosure import ROOT, rules, variants


def stroke(shape,points,width):
    """正交街每段按中心线铺出实际宽度，端点停在给定坐标。"""
    left=width//2;right=width-left
    out=np.zeros(shape,bool)
    def span(x,z,X,Z):
        out[max(0,z):min(shape[0],Z),max(0,x):min(shape[1],X)]=True
    for (x,z),(X,Z) in zip(points,points[1:]):
        if x==X:span(x-left,min(z,Z),x+right,max(z,Z)+1)
        elif z==Z:span(min(x,X),z-left,max(x,X)+1,z+right)
        else:raise ValueError(("街道折点未正交",points))
    for x,z in points[1:-1]:span(x-left,z-left,x+right,z+right)
    return out


def recast(plan):
    rule=rules()["towns"][plan.id]
    spec=read(ROOT/"blueprints/qianji"/(variants()[plan.id]+".json"))
    x,z,X,Z=rule["box"];turn=rule["turn"]
    w,d=spec["size"]
    if (X-x,Z-z)!=((d,w) if turn%2 else (w,d)):raise ValueError((plan.id,"整阁占地尺寸不符"))
    if not plan.inside[z:Z,x:X].all() or plan.unbuildable[z:Z,x:X].any():raise ValueError((plan.id,"围院跨出城内可建地形"))
    dx,dz=rotate(spec["paths"][0][0],0,w,d,turn)
    front=FACES[turn];vx,vz=DIR[front];gate=[x+dx,z+dz];outside=[gate[0]+vx,gate[1]+vz]
    if outside!=rule["approach"][0]:raise ValueError((plan.id,"门前街未接到院门",outside))
    original=plan.objects[:]
    old_ids={o["id"] for o in original if o.get("role")=="qianji"}
    if old_ids!={"qianji_"+r["id"] for r in spec["rooms"]}:raise ValueError((plan.id,"分阁楼房清单不符",old_ids))
    base_roads={r["id"]:r for r in plan.layout["roads"]}
    if not set(rule["roads"])<=base_roads.keys():raise ValueError("改线引用未知街道")
    retained=np.zeros_like(plan.inside);superseded=retained.copy()
    corridors=[];dest=retained.copy()
    for rid,road in base_roads.items():
        old_points=[plan.map(*pt) for pt in road["points"]]
        mask=stroke(plan.road.shape,old_points,road["width"])
        if rid not in rule["roads"]:
            retained|=mask
            continue
        superseded|=mask
        corridors.append({"id":rid,"name":road["name"],"width":road["width"],"points":rule["roads"][rid],"previous":old_points})
    corridors.extend(dict(row,id="qianji_market_link") for row in rule.get("links",[]))
    corridors.append({"id":"qianji_approach","name":"院门前街","width":rules()["gate"]["width"],"points":rule["approach"]})
    for row in corridors:
        mask=stroke(plan.road.shape,row["points"],row["width"])
        if mask[z:Z,x:X].any():raise ValueError((plan.id,row["name"],"公共街面穿过围院"))
        dest|=mask
    if retained[z:Z,x:X].any():raise ValueError((plan.id,"现有公共街道须先绕院改线"))
    plan.road[superseded & ~retained]=0
    plan.major[superseded & ~retained]=0
    plan.road[z:Z,x:X]=0;plan.major[z:Z,x:X]=0
    for row in corridors:
        mask=stroke(plan.road.shape,row["points"],row["width"])
        level=4 if row["width"]>=5 else 3
        plan.road[mask]=np.maximum(plan.road[mask],level)
        plan.major[mask]=np.maximum(plan.major[mask],level)
    occupied=dest.copy()
    occupied[max(0,z-2):Z+2,max(0,x-2):X+2]=True
    moved=[];plan.objects=[];plan.occupied.fill(False)
    compound=plan.mark({"id":"qianji","type":"qianji","role":"qianji","variant":spec["id"],"name":spec["name"],
                        "box":rule["box"],"turn":turn,"size":[w,d],"front":front,"entry":gate,"outside":outside,"street":rule["frontage"]})
    for o in original:
        if o["id"] in old_ids:continue
        a,b,A,B=o["box"]
        if o.get("role")=="gate":
            if not (A<=x or a>=X or B<=z or b>=Z):raise ValueError("院址压到原有城门")
            plan.mark(o)
        elif o["type"] in ("anchor","plot"):
            if dest[b:B,a:A].any() or not(A<=x or a>=X or B<=z or b>=Z):
                raise ValueError((plan.id,o["id"],"街道或围院压到保留地块"))
            plan.mark(o)
        elif occupied[b:B,a:A].any():
            moved.append(o)
        else:plan.mark(o)
    # 为各户、商铺和传送亭保留原有身份和整栋几何。
    plot=next(o for o in original if o["type"]=="plot")
    streets=plan.major.copy()
    for face in plot["fronts"]:
        edge=[side_point(plot["box"],face,t,4) for t in (0,1)]
        streets[stroke(streets.shape,edge,5)&(plan.road!=0)]=3
    pending=deque(sorted(moved,key=lambda o:(o["type"] not in ("home","pavilion"),o.get("role")=="house",-np.prod(o["size"]))))
    settled=set()
    while pending:
        old=pending.popleft()
        base={k:v for k,v in old.items() if k not in ("entry","outside","box","front","turn","grade")}
        aim=center(old["box"])
        if plan.id=="ximo" and old["id"]=="market":aim=[177,71]
        params=dict(margin=2,wall_gap=7,facing=old["front"],min_ground=True)
        pos=plan.find(base,aim,**params)
        if pos is None:pos=plan.find(base,aim,streets=streets,**params)
        if pos is None:
            movable=[o for o in plan.objects if o["id"] not in settled and
                     (o["type"]=="garden" or (o.get("role")=="house" and max(o["size"])<=22))]
            hard=plan.occupied.copy()
            for o in movable:
                a,b,A,B=o["box"];hard[b:B,a:A]=False
            pos=plan.find(base,aim,obstacles=hard,streets=streets,movable=movable,**params)
            if pos is None:raise ValueError((plan.id,old["id"],"整院迁排后没有安全落位"))
            _,px,pz,_,_,ww,dd,*_=pos
            for o in movable:
                a,b,A,B=o["box"]
                if px<A+2 and px+ww>a-2 and pz<B+2 and pz+dd>b-2:
                    plan.objects.remove(o);plan.occupied[b:B,a:A]=False
                    pending.append(o)
        item=plan.place_at(base,pos)
        a,b,A,B=item["box"]
        if streets[b:B,a:A].any():raise ValueError((plan.id,item["id"],"保护的正式路幅被占"))
        plan.road[b:B,a:A]=0
        plan.frontage(item);settled.add(item["id"])
    origins={o["id"]:o for o in original}
    history=[{"id":o["id"],"name":o["name"],"kind":o.get("role",o["type"]),"from":origins[o["id"]]["box"],"to":o["box"]}
             for o in plan.objects if o["id"] in origins and o["box"]!=origins[o["id"]]["box"]]
    # 改线之后重新接通原有临街门口。门外进公共街，院内由分阁本身的路廊负责。
    for o in plan.objects:
        if "outside" not in o or o["type"] in ("plot","qianji"):continue
        px,pz=o["outside"]
        if plan.road[pz,px]==0:plan.connect(o["outside"])
    if plan.road[z:Z,x:X].any():raise ValueError("住宅支路侵入千机阁围院")
    if {o["id"]for o in plan.objects}!={o["id"]for o in original if o["id"]not in old_ids}|{"qianji"}:
        raise ValueError("改线过程中丢失楼房或经营设施")
    return {"compound":compound["id"],"streets":corridors,"moves":history,"retired":[]}


def stamp(city,obj):
    from qianji.enclosure import build_enclosed
    from province_capitals.central.game_models import rotate_state
    model,model_checks=build_enclosed(city.region,validate=False)
    x,z,_,_=obj["box"];q=obj["turn"];base=obj["grade"]
    def point(p):
        u,y,v=p;a,b=rotate(u,v,model.w,model.d,q)
        return [x+a,base+y,z+b]
    if base+model.h>city.h:raise ValueError((city.region,"分阁屋脊超出城域高度"))
    codes={s:city.code(rotate_state(s,q)) for s in sorted(set(model.blocks.values()))}
    for local,state in model.blocks.items():
        px,y,pz=point(local)
        city.blocks[y,pz,px]=codes[state]
    rooms=[]
    for room in model.rooms:
        a,b,w,d=room["rect"];corners=[point((i,0,j))for i in (a,a+w-1)for j in(b,b+d-1)]
        bounds=[min(v[0]for v in corners),min(v[2]for v in corners),max(v[0]for v in corners)+1,max(v[2]for v in corners)+1]
        rooms.append({"id":"qianji_"+room["id"],"source_room":room["id"],"name":room["name"],"bounds":bounds,"entry":point((a+w//2,room["y"],b)),
                      "floors":room["floors"],"targets":[point(v["target"])for v in room["levels"]]})
    city.qianji={"variant":model.spec["id"],"name":model.spec["name"],"bounds":obj["box"],"base_y":base,
                 "entry":point(model_checks["local_gate"]),"outside":[obj["outside"][0],base+1,obj["outside"][1]],"facing":obj["front"],
                 "board":{"position":point(model.spec["board"]),"reader":point(model.spec["reader"]),"facing":obj["front"]},"rooms":rooms,
                 "signs":[{"position":point(s["position"]),"text":s["text"],"facing":obj["front"]}for s in model.signs],
                 "targets":[{"id":t["id"],"position":point(t["position"])}for t in model.targets],
                 "wall_columns":model_checks["wall_columns"],"gate_width":model_checks["gate_columns"],"gate_height":model_checks["gate_clear_height"]}
    city.qianji_model=model
