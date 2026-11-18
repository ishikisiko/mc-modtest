"""以实际搬运的城门重建净空、内侧街道和城域边缘的官道接点。"""
from scipy import ndimage
import numpy as np

from .plan import DIR, route
from .readback import market_reach


def connect_gate(city, binding):
    objects={o["id"]:o for o in city.objects}
    states=city.states
    air=city.code("minecraft:air")
    paving=city.code("yuanxian:manor_town_"+city.cfg["surface"][0])
    road=(city.road!=0)|city.public_paths
    heights=np.where(city.public_paths & (city.road==0),city.ground,city.street_height)
    connected=market_reach(city,road,heights)
    if not connected.any():
        raise ValueError((city.region,"市集门前缺少已生成街面"))

    def section(cx,cz,dx,dz,radius):
        return [(cx-dz*k,cz+dx*k) for k in range(-radius,radius+1)]

    def valid(x,z):
        return 0<=x<city.w and 0<=z<city.d

    def stamp(x,z,y,clearance):
        # 留住原承重层，桥与出城驿道的独立站立标高用最终高度场记录。
        if y<0 or y+clearance>=city.h:
            raise ValueError((city.region,"城门通路高度越界",x,z,y))
        if y>city.ground[z,x]:
            city.blocks[int(city.ground[z,x])+1:y,z,x]=city.code(city.city["town_wall"]["material"])
        city.blocks[y,z,x]=paving
        city.blocks[y+1:y+clearance+1,z,x]=air
        city.street_height[z,x]=y
        city.road[z,x]=max(3,city.road[z,x])

    result=[]
    for original in city.city["blueprint"]["public_passages"]:
        obj=objects[original["owner"]]
        x,z,X,Z=obj["box"]
        a,b,A,B=obj["source"]
        # 城门当前没有旋转；方位由原门楼和官道的作者绑定共同决定。
        if obj.get("turn",0):
            raise ValueError((city.region,original["id"],"正式门楼需要按作者方向排入街坊"))
        face=binding["facing"] if original["id"]==binding["passage"] else obj["front"]
        dx,dz=DIR[face]
        width=binding["port_width"] if original["id"]==binding["passage"] else 3
        gx,gz,GX,GZ=original["bounds"]
        gx,gz,GX,GZ=gx+x-a,gz+z-b,GX+x-a,GZ+z-b
        center_x,center_z=(gx+GX)//2,(gz+GZ)//2
        floor=int(original["y"]+obj["delta"]-1)
        clearance=original["height"]
        inner_x=(X if dx==-1 else x-1) if dx else center_x
        inner_z=(Z if dz==-1 else z-1) if dz else center_z
        sections=[]
        cx,cz=inner_x,inner_z
        while valid(cx,cz):
            line=section(cx,cz,dx,dz,width//2)
            if not all(valid(i,j) for i,j in line):
                raise ValueError((city.region,original["id"],"门洞路幅越界"))
            for u,v in line:
                if city.occupied[v,u] and not (x<=u<X and z<=v<Z):
                    raise ValueError((city.region,original["id"],"城门外路侵入其他地块",u,v))
                if x<=u<X and z<=v<Z:
                    # 只允许在原核准门洞轴线上使用空格，门两侧承重体不参与切削。
                    blocking=[states[city.blocks[y,v,u]] for y in range(floor+1,floor+clearance+1) if city.blocks[y,v,u]]
                    if blocking:
                        raise ValueError((city.region,original["id"],"原门洞轴线受阻",u,floor+1,v,blocking[:3]))
                stamp(u,v,floor,clearance)
            sections.extend([[i,floor+1,j] for i,j in line])
            cx+=dx;cz+=dz

        free=~city.occupied
        pad=1
        mask=ndimage.minimum_filter(free,size=2*pad+1,mode="constant",cval=False)
        start=(inner_x,inner_z)
        if not mask[inner_z,inner_x]:
            sx,sz=inner_x-dx,inner_z-dz
            if not valid(sx,sz) or not mask[sz,sx]:
                raise ValueError((city.region,original["id"],"城门内侧缺少完整三格门前街"))
            start=(sx,sz)
        zz,xx=np.indices(mask.shape)
        run=np.abs(xx-start[0])+np.abs(zz-start[1])
        targets=connected & mask & (np.abs(heights-floor)<=run)
        path=route(mask,start,targets)
        if not path:
            raise ValueError((city.region,original["id"],"门前街无法接入市集路网"))
        end_x,end_z=path[-1]
        end_floor=int(heights[end_z,end_x])
        if abs(end_floor-floor)>len(path)-1:
            raise ValueError((city.region,original["id"],"门前街高差超过一格步高",floor,end_floor))
        for index,(px,pz) in enumerate(path):
            travel=max(1,len(path)-1)
            y=floor+round((end_floor-floor)*index/travel)
            for pz1 in range(pz-pad,pz+pad+1):
                for px1 in range(px-pad,px+pad+1):
                    if not free[pz1,px1]:
                        continue
                    if connected[pz1,px1] and abs(y-int(heights[pz1,px1]))<=1:
                        continue
                    stamp(px1,pz1,y,clearance)
        if dx:
            bounds=[gx,center_z-width//2,GX+1,center_z+width//2+1]
        else:
            bounds=[center_x-width//2,gz,center_x+width//2+1,GZ+1]
        result.append({"id":original["id"],"owner":obj["id"],"bounds":bounds,"y":floor+1,
                       "height":clearance,"width":width,"facing":face,
                       "entry":[inner_x,floor+1,inner_z],"surface_columns":sections})
    if sum(p["id"]==binding["passage"] for p in result)!=1:
        raise ValueError((city.region,"最终城门缺正式官道绑定"))
    return result
