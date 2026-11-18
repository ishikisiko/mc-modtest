"""七城住宅重排的设计稿。读取现役切片清单，输出按真实占地排过的总平面。"""
import argparse
import hashlib
import json
import os
import sys
from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "tools"))
from province_capitals.regional.v2.city import Capital
from province_capitals.regional.v2.common import DIR

PLAN = ROOT / "blueprints/province_capitals/blueprint_plan"
DESIGNS = PLAN / "studies/inner_neighborhoods/cities.json"
HOUSING_RESOURCES = Path(os.environ.get("YUANXIAN_HOUSING_RESOURCES",ROOT / "build/generated/housing/resources"))
HOUSES = HOUSING_RESOURCES / "data/yuanxian/housing/houses"
FACES = ("north", "east", "south", "west")


def read(path):
    return json.loads(Path(path).read_text())


def write(path, data):
    path.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")) + "\n")


def center(box):
    return (box[0] + box[2] - 1) / 2, (box[1] + box[3] - 1) / 2


def rotate(x, z, w, d, q):
    return ((x, z), (d-1-z, x), (w-1-x, d-1-z), (z, w-1-x))[q]


def entry_face(rect, entry):
    x,z,X,Z=rect
    distances=(abs(entry[2]-z),abs(entry[0]-X+1),abs(entry[2]-Z+1),abs(entry[0]-x))
    return FACES[distances.index(min(distances))]


def side_point(box, face, along=.5, distance=0):
    x, z, X, Z = box
    u, v = round(x + (X-x-1)*along), round(z + (Z-z-1)*along)
    return ((u, z-distance), (X-1+distance, v), (u, Z-1+distance), (x-distance, v))[FACES.index(face)]


def area_counts(mask, w, d):
    acc = np.pad(mask.astype(np.int32), ((1, 0), (1, 0))).cumsum(0).cumsum(1)
    return acc[d:, w:] - acc[:-d, w:] - acc[d:, :-w] + acc[:-d, :-w]


def polygon(size, points):
    image = Image.new("L", size)
    ImageDraw.Draw(image).polygon([tuple(p) for p in points], fill=1)
    return np.asarray(image, dtype=bool)


def route(mask, start, targets, *, levels=None):
    """用四邻域公共空间连接临街入口，同时避免穿墙、穿院和穿河。"""
    d, w = mask.shape
    sx, sz = start
    if not (0 <= sx < w and 0 <= sz < d and mask[sz, sx]):
        return []
    previous = np.zeros((d, w), dtype=np.int8)
    previous[sz, sx] = -1
    queue = deque([(sx, sz)])
    while queue:
        x, z = queue.popleft()
        if targets[z, x]:
            points = [(x, z)]
            while previous[z, x] > 0:
                dx, dz = DIR[FACES[previous[z, x]-1]]
                x, z = x-dx, z-dz
                points.append((x, z))
            return points[::-1]
        for i, face in enumerate(FACES):
            dx, dz = DIR[face]
            a, b = x+dx, z+dz
            if 0 <= a < w and 0 <= b < d and mask[b, a] and previous[b, a] == 0:
                if levels is not None and abs(int(levels[b,a])-int(levels[z,x]))>1:
                    continue
                previous[b, a] = i+1
                queue.append((a, b))
    return []


class Neighborhood:
    def __init__(self, region, config, manifest):
        self.id, self.config, self.manifest = region, config, manifest
        self.spec = read(PLAN / (region+".json"))
        src = self.spec["sources"]
        self.layout, self.city, self.env = [read(ROOT / src[k]) for k in ("layout", "city", "environment")]
        self.w, self.d = config["dimensions"]
        self.ow, _, self.od = self.layout["dimensions"]
        self.shift = [manifest["blueprint"]["manor"]["bounds"][i]-self.layout["manor"]["bounds"][i] for i in (0,1)]
        self.terrain = self.natural_terrain()
        self.boundary = [self.map(*point) for point in self.env["wall"]["points"]]
        self.inside = polygon((self.w, self.d), self.boundary)
        self.wall_distance = ndimage.distance_transform_edt(self.inside)
        self.water_top = self.city.get("water_top_y", self.city["terrain"].get("water_top_y", self.city["terrain"].get("sea_y", -9)))
        self.wet = self.terrain < self.water_top
        self.unbuildable = self.wet.copy()
        if region == "moyu":
            self.unbuildable |= self.terrain < self.city["terrain"]["west_air_y"]-2
        self.occupied = np.zeros_like(self.inside)
        self.road = np.zeros(self.inside.shape, np.uint8)
        self.major = self.road.copy()
        self.objects=[]
        self.layout_boxes = {b["id"]:b["box"] for b in self.layout["buildings"]}

    def map(self, x, z):
        return [int(round(x*(self.w-1)/(self.ow-1))), int(round(z*(self.d-1)/(self.od-1)))]

    def natural_terrain(self):
        # 使用原州域地貌函数，不把旧宅区整地、院墙和路面带入新址。
        model = object.__new__(Capital)
        model.city, model.width, model.depth = self.city, self.ow, self.od
        raw = np.array([[round(model.natural_air(x,z))-1 for x in range(self.ow)] for z in range(self.od)], np.int16)
        zz, xx = np.meshgrid(np.linspace(0,self.od-1,self.d), np.linspace(0,self.ow-1,self.w), indexing="ij")
        return ndimage.map_coordinates(raw.astype(float), [zz,xx], order=1).round().astype(np.int16)

    def build_streets(self):
        for road in self.layout["roads"]:
            image = Image.new("L", (self.w,self.d))
            pts = [tuple(self.map(*p)) for p in road["points"]]
            width = road["width"]
            ImageDraw.Draw(image).line(pts, fill=1, width=width, joint="curve")
            mask = np.asarray(image,dtype=bool)
            self.road[mask] = np.maximum(self.road[mask], 4 if width >= 5 else 3)
        self.major[:] = self.road

    def mark(self, obj):
        x,z,X,Z = obj["box"]
        if not (0<=x<X<=self.w and 0<=z<Z<=self.d) or self.occupied[z:Z,x:X].any():
            raise ValueError((self.id,obj["id"],obj["box"],"地块重叠或越界"))
        self.occupied[z:Z,x:X] = True
        self.objects.append(obj)
        return obj

    def fixed_anchor(self, key):
        old = self.manifest["blueprint"][key]["bounds"]
        ox,oz,x1,z1=old
        src=[ox,oz,x1+1,z1+1]
        w,d=x1+1-ox,z1+1-oz
        cx,cz=self.map(*center(self.layout[key]["bounds"]))
        x,z=round(cx-(w-1)/2),round(cz-(d-1)/2)
        self.mark({"id":key,"name":"城主府" if key=="manor" else self.manifest["blueprint"][key]["name"],
                   "type":"anchor","source":src,"box":[x,z,x+w,z+d]})
        self.road[z:z+d,x:x+w]=0

    def find(self, item, aim, *, margin=2, wall_gap=8, facing=None, avoid_roads=True, min_ground=False, obstacles=None, streets=None, movable=()):
        w0,d0=item["size"]; door=item.get("door", [w0//2,0]); initial=item.get("facing","north")
        occupied=self.occupied if obstacles is None else obstacles
        roads=self.road if streets is None else streets
        block=ndimage.maximum_filter(occupied, size=2*margin+1) | (self.wall_distance<wall_gap)
        if not item.get("shore",False):
            block |= ndimage.maximum_filter(self.unbuildable,size=13) if item["type"]=="plot" else self.unbuildable
        if avoid_roads:
            block |= roads!=0
        road_distance=ndimage.distance_transform_edt(roads==0)
        best=None
        for q in item.get("rotations", range(4)):
            w,d=(w0,d0) if q%2==0 else (d0,w0)
            zz,xx=np.nonzero(area_counts(block,w,d)==0)
            if len(xx)==0:continue
            dx,dz=rotate(*door,w0,d0,q)
            face=FACES[(FACES.index(initial)+q)%4]
            ix,iz=DIR[face]
            # 优先让门口正对道路。大院外门有时比用地边界内缩数格。
            px = 0 if face=="west" else w-1 if face=="east" else dx
            pz = 0 if face=="north" else d-1 if face=="south" else dz
            ax,az=xx+px+ix,zz+pz+iz
            if not item.get("shore",False):
                usable=~self.unbuildable[az,ax] & ~occupied[az,ax]
                xx,zz,ax,az=xx[usable],zz[usable],ax[usable],az[usable]
                if not len(xx):continue
            cx,cz=xx+(w-1)/2,zz+(d-1)/2
            cost=np.hypot(cx-aim[0],cz-aim[1]) + 2.4*np.minimum(60,road_distance[az,ax])
            if facing and face!=facing:cost+=32 if face==FACES[(FACES.index(facing)+2)%4] else 16
            if item["type"]=="ordinary":cost+=2*min(q,4-q)
            if not avoid_roads:
                cost+=area_counts(roads!=0,w,d)[zz,xx]*.35
            for resident in movable:
                a,b,A,B=resident["box"]
                hits=(xx<A+margin)&(xx+w>a-margin)&(zz<B+margin)&(zz+d>b-margin)
                cost+=hits*(90 if resident["type"]=="ordinary" else 45)
            if min_ground:
                contour=[self.terrain[zz,xx],self.terrain[zz+d-1,xx+w-1],self.terrain[zz,xx+w-1],self.terrain[zz+d-1,xx],self.terrain[cz.astype(int),cx.astype(int)]]
                cost+=5*np.ptp(contour,axis=0)
            i=int(cost.argmin())
            candidate=(float(cost[i]),int(xx[i]),int(zz[i]),q,face,w,d,dx,dz,int(ax[i]),int(az[i]))
            if best is None or candidate<best:best=candidate
        return best

    def place(self,item,aim,**kwargs):
        pos=self.find(item,aim,**kwargs)
        if pos is None:raise ValueError((self.id,item["id"],"街坊余地不足",aim))
        return self.place_at(item,pos)

    def place_at(self,item,pos):
        _,x,z,q,face,w,d,dx,dz,ax,az=pos
        obj={**item,"box":[x,z,x+w,z+d],"turn":q,"front":face,"entry":[x+dx,z+dz],"outside":[ax,az]}
        return self.mark(obj)

    def lane(self,points,width=3,tier=2):
        mask=Image.new("L",(self.w,self.d))
        pen=ImageDraw.Draw(mask)
        if len(points)>1:pen.line([tuple(p) for p in points],fill=1,width=width,joint="curve")
        elif points:pen.point(tuple(points[0]),fill=1)
        valid=np.asarray(mask,dtype=bool)&self.inside&~self.unbuildable&~self.occupied
        self.road[valid]=np.maximum(self.road[valid],tier)

    def connect(self,p,targets=None,width=3):
        if targets is None:targets=self.road!=0
        mask=self.inside & ~self.occupied & (~self.unbuildable | (self.major!=0))
        path=route(mask,tuple(p),targets)
        if not path:raise ValueError((self.id,"入口没有通路",p))
        self.lane(path,width)
        return path

    def frontage(self,obj):
        self.connect(obj["outside"])
        x,z,X,Z=obj["box"];face=obj["front"]
        # 门内缩时，将院外甬道接到模板边界。庭内铺装由住宅模板自身施工。
        if obj["type"]=="home":
            a,b=side_point(obj["box"],face,.16,2),side_point(obj["box"],face,.84,2)
            self.lane([a,b],3)

    def player_plot(self):
        item={"id":"player_land","name":"玩家宅田","type":"plot","size":[64,72],"rotations":[0,1]}
        aim=self.map(*self.config["plot"])
        options=dict(margin=self.config.get("plot_setback",3),wall_gap=self.config.get("wall_gap",23),min_ground=True)
        strict=self.find(item,aim,**options)
        # 允许穿过宅田的旧巷改沿地块外缘，防止算法把宅田赶到城根。
        reroute=self.find(item,aim,avoid_roads=False,**options)
        if reroute is None:raise ValueError("宅田在城内没有候选")
        use_strict=strict is not None and strict[0] < reroute[0]+20
        plot=self.place(item,aim,avoid_roads=use_strict,**options)
        x,z,X,Z=plot["box"]
        old=self.road.copy()
        clipped=bool(np.any(old[z:Z,x:X]))
        self.road[z:Z,x:X]=0
        target=self.road!=0
        for face in self.config["streets"]:
            a,b=side_point(plot["box"],face,0,4),side_point(plot["box"],face,1,4)
            self.lane([a,b],5,3)
        # 若旧街切过地块，围绕地块接回原来的两个断面。
        if clipped:
            corners=[(x-4,z-4),(X+3,z-4),(X+3,Z+3),(x-4,Z+3),(x-4,z-4)]
            self.lane(corners,3,2)
            for axis, s in enumerate([old[z-1,x:X],old[z:Z,X],old[Z,x:X],old[z:Z,x-1]]):
                for offset in np.flatnonzero(s):
                    p=([x+int(offset),z-1],[X,z+int(offset)],[x+int(offset),Z],[x-1,z+int(offset)])[axis]
                    dx,dz=DIR[FACES[axis]]
                    self.lane([p,[p[0]+dx*4,p[1]+dz*4]],3)
        for face in self.config["streets"]:
            p=side_point(plot["box"],face,.5,4)
            self.connect(p,target)
        plot["fronts"]=self.config["streets"]
        plot["rerouted"]=clipped
        return plot

    def ordinary(self):
        active={}
        for b in self.manifest["buildings"]:
            if b["family"] in ("lord_manor","housing_residences") or b["id"]=="lord_manor":continue
            a,B=b["bounds"]["min"],b["bounds"]["max"]
            src=[a[0],a[2],B[0]+1,B[2]+1]; ent=b["entrance"]
            original=self.layout_boxes.get(b["id"])
            if original:aim=self.map(*center(original))
            else:
                x,z=center(src);aim=self.map(x-self.shift[0],z-self.shift[1])
            active[b["id"]]={"id":b["id"],"name":b["name"],"type":"ordinary","role":b["role"],"family":b["family"],
                "source":src,"source_y":[a[1],B[1]+1],"source_floor":b["position"][1]-1,
                "size":[src[2]-src[0],src[3]-src[1]],"door":[ent[0]-src[0],ent[2]-src[1]],"facing":b.get("facing",entry_face(src,ent)),"aim":aim}
        return active

    def gate(self,item):
        w,d=item["size"];cx,cz=item["aim"]
        x=max(1,min(self.w-w-1,round(cx-(w-1)/2)));z=max(1,min(self.d-d-1,round(cz-(d-1)/2)))
        if self.occupied[z:z+d,x:x+w].any():
            obj=self.place(item,item["aim"])
            self.frontage(obj);return
        self.mark({**item,"box":[x,z,x+w,z+d],"turn":0,"front":item["facing"],
                   "entry":[x+item["door"][0],z+item["door"][1]]})

    def make(self):
        self.build_streets()
        self.fixed_anchor("manor");self.fixed_anchor("landmark")
        ordinary=self.ordinary()
        for key in [k for k,i in ordinary.items() if i["role"]=="gate"]:
            self.gate(ordinary.pop(key))
        plot=self.player_plot()
        homes=[]
        for tid,tx,tz in self.config["homes"]:
            h=read(HOUSES / (tid+".json"))
            home={"id":tid,"name":h["name"],"type":"home","size":[h["size"][0],h["size"][2]],"door":h["entry"][::2]}
            obj=self.place(home,self.map(tx,tz),wall_gap=13,margin=3,min_ground=True)
            self.frontage(obj); homes.append(obj)
        # 先给宅田和新院宅的对街留出原有小户、铺宅的门面，再安置其他旧建筑。
        target_slots=[]
        for face in plot["fronts"]:
            for t in (.28,.72):
                target_slots.append((side_point(plot["box"],face,t,15),FACES[(FACES.index(face)+2)%4]))
        if self.config.get("outer_neighbors"):
            for face in FACES:
                if face not in plot["fronts"]:
                    target_slots.insert(0,(side_point(plot["box"],face,.46,14),FACES[(FACES.index(face)+2)%4]))
        for home in homes:
            target_slots.append((side_point(home["box"],home["front"],.5,15),FACES[(FACES.index(home["front"])+2)%4]))
        for i,(aim,facing) in enumerate(target_slots):
            choices=[v for v in ordinary.values() if v["role"]=="house" and max(v["size"])<=21]
            if not choices:break
            item=min(choices,key=lambda v: np.hypot(v["aim"][0]-aim[0],v["aim"][1]-aim[1])+(0 if i%3 or v["family"]=="shop_house" else 40))
            obj=self.place(item,aim,facing=facing)
            self.frontage(obj);ordinary.pop(item["id"])
        for item in sorted(ordinary.values(),key=lambda v:(v["role"]=="house", -np.prod(v["size"]),v["id"])):
            # 沿海码头在原位置保留水上的木构空间。
            item["shore"]=self.id=="donghai" and item["family"] in ("pier_warehouse","dock_hall")
            obj=self.place(item,item["aim"],margin=2,wall_gap=7)
            self.frontage(obj)
        return plot,homes

    def amenities(self):
        c=self.manifest
        for r,s in zip(c["waystone_reservations"],c["waystones"]):
            assert r["id"]==s["id"]
            x,z,X,Z=r["bounds"];w,d=X+1-x,Z+1-z
            ax,az=self.map((x+X)/2-self.shift[0],(z+Z)/2-self.shift[1])
            item={"id":s["id"],"name":s["name"],"role":"waystone","type":"pavilion","source":[x,z,X+1,Z+1],
                  "source_floor":s["position"][1]-1,"source_y":[s["position"][1]-2,s["position"][1]+14],
                  "size":[w,d],"door":[w//2,0],"rotations":[0]}
            obj=self.place(item,[ax,az],wall_gap=5,margin=1)
            self.frontage(obj)
        for obj in [v for v in self.objects if v["type"]=="anchor"]:
            src=obj["source"]; box=obj["box"]
            if obj["id"]=="manor":
                b=next(b for b in c["buildings"] if b["id"]=="lord_manor")
                p=b["entrance"];face=b.get("facing",entry_face(src,p))
                along=np.clip((p[0]-src[0])/(src[2]-src[0]) if face in ("north","south") else (p[2]-src[1])/(src[3]-src[1]),0,1)
            else:face="south";along=.5
            outside=side_point(box,face,float(along),1)
            if not self.occupied[outside[1],outside[0]] and not self.unbuildable[outside[1],outside[0]]:
                self.connect(outside)
        for g in c["blueprint"]["gardens"]:
            if g["access"]!="public":continue
            x,z,X,Z=g["bounds"];w,d=X-x+1,Z-z+1
            item={"id":"court_"+g["id"],"name":g["name"],"type":"garden","size":[w,d],"door":[w//2,0],"source":[x,z,X+1,Z+1],
                  "source_floor":g["y"]-1,"source_y":[g["y"]-1,g["y"]+15],"rotations":[0,1,2,3]}
            aim=self.map((x+X)/2-self.shift[0],(z+Z)/2-self.shift[1])
            if self.find(item,aim,wall_gap=5,margin=1) is not None:
                obj=self.place(item,aim,wall_gap=5,margin=1)
                self.frontage(obj)

    def connect_network(self):
        # 共用的环场、跨河街与居民巷合成同一张四邻接路网。
        for _ in range(80):
            labels,count=ndimage.label(self.road!=0)
            if count<=1:return
            sizes=np.bincount(labels.ravel());sizes[0]=0
            main=labels==sizes.argmax()
            todo=np.flatnonzero((sizes>0)&(np.arange(len(sizes))!=sizes.argmax()))
            nearest=ndimage.distance_transform_edt(~main)
            progress=False
            for i in sorted(todo,key=lambda i:-sizes[i]):
                zz,xx=np.nonzero((labels==i)&self.inside&~self.occupied)
                if not len(xx):
                    self.road[labels==i]=0;progress=True;continue
                j=int(np.argmin(nearest[zz,xx]))
                mask=self.inside & ~self.occupied & (~self.unbuildable | (self.road!=0))
                path=route(mask,(int(xx[j]),int(zz[j])),main)
                if path:
                    self.lane(path,3)
                    # 既有桥段已拥有桥面。桥头一格也参与原路线的衔接。
                    for x,z in path:
                        if self.road[z,x]==0:self.road[z,x]=1
                    progress=True;break
            if not progress:raise ValueError((self.id,"街网被割断",sizes.tolist()))
        raise ValueError((self.id,"未完成街网"))

    def grade(self):
        heights=self.terrain.copy()
        for obj in self.objects:
            x,z,X,Z=obj["box"]
            if obj["type"]=="anchor":continue
            outside=obj.get("outside",obj.get("entry",[x,z]))
            y=int(heights[outside[1],outside[0]])
            if obj["type"]=="plot":y=int(np.median(self.terrain[z:Z,x:X]))
            obj["grade"]=y
            heights[z:Z,x:X]=y
            if obj["type"]=="plot":continue
            # 在门前铺面上接顺入口。院内地形不改动邻街的纵向坡度。
            face=obj.get("front","north")
            for s in range(1,5):
                u,v=outside[0]+DIR[face][0]*(s-1),outside[1]+DIR[face][1]*(s-1)
                if 0<=u<self.w and 0<=v<self.d and not self.occupied[v,u]:
                    heights[v,u]=int(np.clip(heights[v,u],y-(s-1),y+(s-1)))
        return heights

    def metrics(self,plot,homes):
        x,z,X,Z=plot["box"]
        assert np.all(self.inside[z:Z,x:X]) and not self.road[z:Z,x:X].any()
        roads=self.road!=0
        ordinary=[v for v in self.objects if v["type"]=="ordinary" and v["role"] not in ("gate","qianji")]
        def neighbors(o):
            a,b,A,B=o["box"]; out={}
            for house in ordinary:
                u,v,U,V=house["box"]
                nx=max(0,a-U,u-A);nz=max(0,b-V,v-B)
                if max(nx,nz)>22:continue
                for side,condition,gap in (("north",V<=b and min(A,U)>max(a,u),b-V),("south",v>=B and min(A,U)>max(a,u),v-B),
                                          ("east",u>=A and min(B,V)>max(b,v),u-A),("west",U<=a and min(B,V)>max(b,v),a-U)):
                    if condition and gap<=22:out.setdefault(side,[]).append(house["id"])
            return out
        plot_neighbors=neighbors(plot)
        if len(plot_neighbors)<3:raise ValueError((self.id,"宅地周边原有门面不足",plot_neighbors))
        nearby={}
        for o in homes:
            ax,az=o["outside"]
            assert roads[az,ax]
            nb=neighbors(o)
            if not nb:raise ValueError((self.id,"院宅远离已有民居",o["id"]))
            nearby[o["id"]]=nb
        frontages=[]
        for face in plot["fronts"]:
            u,v=side_point(plot["box"],face,.5,4)
            if not roads[v,u]:raise ValueError((self.id,"宅地缺少街面",face))
            frontages.append(face)
        return {"plot_wall_clearance":int(self.wall_distance[z:Z,x:X].min()),"plot_fronts":frontages,"plot_neighbors":plot_neighbors,
                "home_neighbors":nearby,"connected_street_networks":ndimage.label(roads)[1],"ordinary":len(ordinary)}

    def export(self,dest):
        plot,homes=self.make()
        self.amenities()
        from .qianji import recast
        changes=recast(self)
        homes=[next(o for o in self.objects if o["id"]==home["id"]) for home in homes]
        self.connect_network()
        ground=self.grade()
        stats=self.metrics(plot,homes)
        dest.mkdir(parents=True,exist_ok=True)
        data={"region":self.id,"name":self.manifest["display_name"],"description":self.config["description"],"size":[self.w,self.manifest["size"][1],self.d],
              "wall":self.boundary,"water_top":self.water_top,"objects":self.objects,"metrics":stats,"qianji_layout":changes}
        write(dest/"plan.json",data)
        np.savez_compressed(dest/"ground.npz",ground=ground,natural=self.terrain,road=self.road,major=self.major,inside=self.inside)
        # 带有独立图例的平面稿由查看器生成；这张缩略图供布局作者检查街道占地。
        img=np.full((self.d,self.w,3),[166,182,157],np.uint8)
        img[~self.inside]=[140,150,130];img[self.unbuildable]=[91,145,164];img[self.road!=0]=[220,202,167]
        for o in self.objects:
            x,z,X,Z=o["box"];img[z:Z,x:X]={"home":[158,78,64],"qianji":[72,112,125],"plot":[236,200,100],"anchor":[82,90,88],"ordinary":([72,112,125] if o["role"]=="qianji" else [129,113,98]) if o["type"]=="ordinary" else [129,113,98],"garden":[68,132,89],"pavilion":[95,144,145]}[o["type"]]
        Image.fromarray(img).save(dest/"footprints.png")
        return stats


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--resources",type=Path,required=True);p.add_argument("--output",type=Path,required=True);p.add_argument("cities",nargs="*")
    args=p.parse_args();config=read(DESIGNS)
    for region in args.cities or config:
        path=args.resources/"data/yuanxian/capitals/v4"/("capital_"+region)/"city.json"
        out=args.output/region
        stats=Neighborhood(region,config[region],read(path)).export(out)
        print(region,stats["ordinary"],stats["plot_wall_clearance"],list(stats["plot_neighbors"]),list(stats["home_neighbors"]),flush=True)
        write(out/"inputs.json",{"manifest":hashlib.sha256(path.read_bytes()).hexdigest(),"design":hashlib.sha256(DESIGNS.read_bytes()).hexdigest()})


if __name__=="__main__":
    main()
