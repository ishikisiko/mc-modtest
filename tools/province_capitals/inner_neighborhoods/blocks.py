"""把街坊设计稿组装成方块体积。府邸、商住建筑和亭台取自本次源切片。"""

import numpy as np
from scipy import ndimage

from .plan import DESIGNS, DIR, HOUSES, PLAN, ROOT, read, side_point
from .ground import CivicGround, PROFILES, player_surface
from .house_variants import apply_variant
from province_capitals.central.game_models import rotate_state


class BlockCity:
    def __init__(self, source, folder):
        self.city,self.source_hash,self.source_ground,old_states,self.source_grid,_=source
        self.plan=read(folder/"plan.json")
        inputs=read(folder/"inputs.json")
        if inputs["manifest"]!=self.source_hash:
            raise ValueError("设计稿与方块切片来源不符")
        self.region=self.plan["region"]; self.cfg=read(DESIGNS)[self.region]
        self.profile=read(PROFILES)[self.region]
        self.w,self.h,self.d=self.plan["size"]
        with np.load(folder/"ground.npz") as a:
            self.ground,self.natural,self.road,self.major,self.inside=[a[k].copy() for k in ("ground","natural","road","major","inside")]
        self.states=list(old_states);self.ids={s:i for i,s in enumerate(self.states)}
        self.grid=np.zeros((self.h+2,self.d+2,self.w+2),np.uint16)
        self.blocks=self.grid[1:-1,1:-1,1:-1]
        self.source=self.source_grid[1:-1,1:-1,1:-1]
        self.objects=self.plan["objects"]
        self.plantings=[]
        self.variants={}
        self.public_paths=np.zeros_like(self.inside)
        self.occupied=np.zeros((self.d,self.w),bool)
        self.major_lots=np.zeros_like(self.occupied)
        for o in self.objects:
            x,z,X,Z=o["box"];self.occupied[z:Z,x:X]=True
            if o["type"] in ("home","plot","anchor"):self.major_lots[z:Z,x:X]=True
        self.rotation_cache={}
        self.water_top=self.plan["water_top"]
        self.street_height=self.ground.copy()

    def code(self,state):
        if state not in self.ids:
            self.ids[state]=len(self.states);self.states.append(state)
        return self.ids[state]

    def rotate_codes(self,indices,q):
        if not q:return indices
        values,lookup=np.unique(indices,return_inverse=True)
        def get(v):
            key=int(v),q
            if key not in self.rotation_cache:self.rotation_cache[key]=self.code(rotate_state(self.states[int(v)],q))
            return self.rotation_cache[key]
        return np.array([get(v) for v in values],dtype=np.uint16)[lookup].reshape(indices.shape)

    def base(self):
        source_terrain=self.city["terrain"]
        floor=self.code(source_terrain["top_block"]);sub=self.code(source_terrain["subsurface_block"]);bed=self.code(source_terrain["fill_block"])
        # 府邸带入真实地面高差，其余街面都只使用新规划的标高。
        for o in self.objects:
            if o["type"]!="anchor":continue
            x,z,X,Z=o["box"];a,b,A,B=o["source"]
            source=self.source_ground[b:B,a:A]
            # 以门前地面作为两套高程的基准，海府跨岸平台和山城府内台阶均保留。
            if o["id"]=="manor":
                old=next(v for v in self.city["buildings"] if v["id"]=="lord_manor")
                ex=np.clip(old["entrance"][0]-a,0,A-a-1);ez=np.clip(old["entrance"][2]-b,0,B-b-1)
                delta=int(self.natural[z+ez,x+ex])-int(source[ez,ex])
            else:delta=int(np.median(self.natural[z:Z,x:X])-np.median(source))
            delta=max(delta,-int(source.min()))
            o["delta"]=delta
            self.ground[z:Z,x:X]=source+delta
        alt=self.ground
        for y in range(int(alt.max())+1):
            layer=self.blocks[y]
            layer[y<alt-3]=bed
            layer[(y>=alt-3)&(y<alt)]=sub
            layer[y==alt]=floor
        if self.water_top>=0:
            for y in range(self.water_top+1):
                self.blocks[y][alt<y]=self.code("minecraft:water[level=0]")
        main,edge=[self.code("yuanxian:manor_town_"+v) for v in self.cfg["surface"]]
        zz,xx=np.indices(alt.shape)
        paths=(self.road!=0)
        bridge=np.zeros_like(paths)
        if self.region in ("nanjiang","donghai","moyu","lingyue"):
            if self.region=="moyu":
                sources=read(PLAN/(self.region+".json"))["sources"]
                terrain=read(ROOT/sources["city"])["terrain"]
                old_w,_,old_d=read(ROOT/sources["layout"])["dimensions"]
                xs=self.w-1;zs=self.d-1
                rz,rx=np.asarray(terrain["rift_x_by_z"]).T
                axis=np.interp(zz*(old_d-1)/zs,rz,rx)*xs/(old_w-1)
                span=(terrain["half_width"]+terrain["bank_width"]+2)*xs/(old_w-1)
                bridge=paths & (np.abs(xx-axis)<=span)
                # 桥从低岸台面起坡，在高岸陡坎前升到街面。整个桥宽共用高程，
                # 桥头也纳入同一坡线，避免沿天然岸坡出现连续两格的断阶。
                rise=np.clip((xx-axis+span)/(span*1.38),0,1)
                west,east=terrain["west_air_y"]-1,terrain["east_air_y"]-1
                self.street_height[bridge]=np.rint(west+(east-west)*rise[bridge]).astype(np.int16)
            elif self.region=="lingyue":
                u=xx*239/(self.w-1);v=zz*287/(self.d-1)
                bridge=paths&(abs(u-126)<8)&(abs(v-148)<4)
                if bridge.any():
                    for z in np.nonzero(bridge.any(1))[0]:
                        xs=np.flatnonzero(bridge[z]);self.street_height[z,xs]=np.rint(np.linspace(self.natural[z,xs[0]-1],self.natural[z,xs[-1]+1],len(xs))).astype(np.int16)
            else:
                bank={"donghai":7,"nanjiang":13}[self.region]
                bridge=paths&(self.natural<bank-1)
                self.street_height[bridge]=bank
        surface=np.where(self.road>=3,main,edge)
        surface[bridge]=self.code("minecraft:spruce_planks")
        self.blocks[self.street_height[paths],zz[paths],xx[paths]]=surface[paths]
        if bridge.any():
            edges=bridge & ~ndimage.binary_erosion(paths)
            log=self.code("minecraft:stripped_spruce_log[axis=y]")
            for z,x in zip(*np.nonzero(edges)):
                y=int(self.street_height[z,x])
                if x%7==0 or z%9==0:
                    self.blocks[self.ground[z,x]+1:y,z,x]=log
            for z,x in zip(*np.nonzero(edges)):
                if self.occupied[z,x]:continue
                is_x=paths[z,min(self.w-1,x+1)] and paths[z,max(0,x-1)]
                is_z=paths[min(self.d-1,z+1),x] and paths[max(0,z-1),x]
                eastwest=is_x and not is_z
                fence="minecraft:spruce_fence[east=%s,north=%s,south=%s,waterlogged=false,west=%s]"%tuple(str(v).lower() for v in (eastwest,not eastwest,not eastwest,eastwest))
                self.blocks[self.street_height[z,x]+1,z,x]=self.code(fence)

    def copy(self,o):
        x,z,X,Z=o["box"];a,b,A,B=o["source"];q=o.get("turn",0)
        if o["type"]=="anchor":
            delta=o["delta"]
            lo=max(0,-delta);hi=min(self.h,self.h-delta)
            self.blocks[lo+delta:hi+delta,z:Z,x:X]=self.source[lo:hi,b:B,a:A]
            return
        lo,hi=o["source_y"]
        lo=max(0,lo);hi=min(self.h,hi)
        source_floor=o["source_floor"]
        if o.get("family") in ("stilt_longhouse","pier_warehouse"):
            source_floor=int(np.median(self.source_ground[b:B,a:A]))
        delta=o["grade"]-source_floor
        o["delta"]=delta
        lo=max(lo,-delta);hi=min(hi,self.h-delta)
        v=np.rot90(self.source[lo:hi,b:B,a:A],-q,axes=(1,2))
        self.blocks[lo+delta:hi+delta,z:Z,x:X]=self.rotate_codes(v,q)

    def home(self,o):
        h=read(HOUSES/(o["id"]+".json"))
        x,z,X,Z=o["box"];q=o["turn"];w,_,d=h["size"]
        rows=np.asarray(h["blocks"],np.int32);a,y,b,ids=rows.T
        xx,zz=((a,b),(d-1-b,a),(w-1-a,d-1-b),(b,w-1-a))[q]
        floor=o["grade"]+1
        palette=np.asarray([self.code(rotate_state(state,q)) for state in h["palette"]],np.uint16)
        self.blocks[floor+y,z+zz,x+xx]=palette[ids]
        self.variants[o["id"]]=apply_variant(self,o,h)
        # 院宅外门有内缩的，补足模板边沿与街面的铺装。
        ex,ez=o["entry"];ax,az=o["outside"]
        sx,sz=DIR[o["front"]];px,pz=-sz,sx
        length=abs(ex-ax)+abs(ez-az)
        for i in range(1,length+1):
            for j in (-1,0,1):
                xx,zz=ex+sx*i+px*j,ez+sz*i+pz*j
                if 0<=xx<self.w and 0<=zz<self.d:self.blocks[floor-1,zz,xx]=self.code("yuanxian:manor_town_"+self.cfg["surface"][1])

    def player(self,o):
        x,z,X,Z=o["box"];y=o["grade"]
        self.blocks[y,z:Z,x:X]=player_surface(self,o)
        self.blocks[y-2:y,z:Z,x:X]=self.code("minecraft:dirt")
        self.blocks[y+1:,z:Z,x:X]=0
        border=self.code("yuanxian:manor_town_"+self.cfg["surface"][1])
        for xx,zz in [(u,v) for u in range(x-1,X+1) for v in (z-1,Z)]+[(u,v) for v in range(z,Z) for u in (x-1,X)]:
            a=self.ground[zz,xx]
            self.blocks[a,zz,xx]=border
        # 四块齐平角石标出可建边界。
        for xx,zz in ((x-1,z-1),(X,z-1),(X,Z),(x-1,Z)):
            self.blocks[self.ground[zz,xx],zz,xx]=self.code("minecraft:chiseled_stone_bricks")

    def walls(self):
        edge=self.inside & ~ndimage.binary_erosion(self.inside)
        exits=ndimage.maximum_filter(self.road>=3,size=5)
        for z,x in zip(*np.nonzero(edge&~exits&~self.occupied)):
            if self.region=="donghai" and self.natural[z,x]<self.water_top:continue
            y=int(self.ground[z,x]); height=self.city["town_wall"]["height"]
            self.blocks[y+1:y+height+1,z,x]=self.code(self.city["town_wall"]["material"])
            if (x+z)%4<2:self.blocks[y+height+1,z,x]=self.code(self.city["town_wall"]["material"])

    def assemble(self):
        self.base()
        for o in self.objects:
            if o["type"]=="plot":self.player(o)
            elif o["type"]=="home":self.home(o)
            elif o["type"]=="qianji":
                from .qianji import stamp
                stamp(self,o)
            else:self.copy(o)
        self.walls()
        CivicGround(self).build()
        plot=next(o for o in self.objects if o["type"]=="plot")
        x,z,X,Z=plot["box"]; y=plot["grade"]
        if self.blocks[y+1:,z:Z,x:X].any() or not np.array_equal(self.blocks[y,z:Z,x:X],player_surface(self,plot)):
            raise ValueError("宅田含有占位方块或不完整的种植面")
        return self
