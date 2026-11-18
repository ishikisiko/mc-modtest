"""从组装后的实际方块检查街道地面、两格净空和高差，供设计稿快速回读。"""
from collections import deque

import numpy as np

from .plan import DIR, side_point


def market_reach(city, road, y):
    zz,xx=np.indices(road.shape)
    blocks=city.blocks
    foot,head=blocks[y+1,zz,xx],blocks[y+2,zz,xx]
    surfaces=blocks[y,zz,xx]
    water=np.array([state.split("[",1)[0]=="minecraft:water" for state in city.states])
    walk=road & (foot==0) & (head==0) & (surfaces!=0) & ~water[surfaces]
    market=next(o for o in city.objects if o["id"]=="market")
    x,z=market["outside"]
    reached=np.zeros_like(walk)
    if walk[z,x]:
        queue=deque([(x,z)]);reached[z,x]=True
        while queue:
            x,z=queue.popleft()
            for dx,dz in DIR.values():
                u,v=x+dx,z+dz
                if 0<=u<city.w and 0<=v<city.d and walk[v,u] and not reached[v,u] and abs(int(y[v,u])-int(y[z,x]))<=1:
                    reached[v,u]=True;queue.append((u,v))
    return reached


def check(city):
    road=(city.road!=0)|city.public_paths
    y=np.where(city.public_paths & (city.road==0),city.ground,city.street_height)
    reached=market_reach(city,road,y)
    market=next(o for o in city.objects if o["id"]=="market")
    def at(pos):
        x,z=pos
        return bool(reached[z,x])
    homes={o["id"]:at(o["outside"]) for o in city.objects if o["type"]=="home"}
    plot=next(o for o in city.objects if o["type"]=="plot")
    fronts={f:any(at(side_point(plot["box"],f,t,4)) for t in (.15,.35,.5,.65,.85)) for f in plot["fronts"]}
    office=next(o for o in city.objects if o["id"]=="qianji")
    from qianji.enclosure_check import audit, check_market_street
    compound=audit(city.qianji_model,city.qianji,city.blocks,city.states,city.road,city.public_paths,city.street_height,at(office["outside"]))
    if city.region=="ximo":compound["market_street"]=check_market_street(city,reached)
    return {"qianji_enclosure":compound,"market_front":at(market["outside"]),"qianji_front":at(office["outside"]),
            "home_fronts":homes,"plot_fronts":fronts,
            "public_courts":{s["name"]:at(s["entry"][::2]) for s in city.ground_sites},"reachable_street_columns":int(reached.sum()),
            "engine_collision":"not_verified"}
