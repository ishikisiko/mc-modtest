"""在实际街坊的整块余地上建市棚、作业院、园圃和坐台。"""
import numpy as np
from scipy import ndimage

from .plan import DIR, FACES, rotate, route
from .ground import UnconnectedCourt
from province_capitals.central.game_models import rotate_state


def slab(kind):
    return "minecraft:"+kind+"_slab[type=bottom,waterlogged=false]"


def plant_tree(c,x,z,y,kind,tall=False):
    h=8 if tall else 6
    log=c.code("minecraft:"+kind+"_log[axis=y]")
    leaf=c.code("minecraft:"+kind+"_leaves[distance=1,persistent=true,waterlogged=false]")
    c.blocks[y+1:y+h+1,z,x]=log
    crown=((3,2),(4,3),(5,2),(6,2),(7,1),(8,1)) if kind=="spruce" else ((h-3,2),(h-2,3),(h-1,3),(h,2),(h+1,1))
    for level,r in crown:
        for dx in range(-r,r+1):
            for dz in range(-r,r+1):
                if abs(dx)+abs(dz)>r+1 or (not dx and not dz and level<=h):continue
                c.blocks[y+level,z+dz,x+dx]=leaf


class PublicCourt:
    def __init__(self, fabric,site):
        self.f=fabric;self.c=fabric.city;self.s=site;self.p=fabric.profile
        self.w,self.d=site["width"],site["depth"]
        self.q=site["turn"]
        self.x,self.z,self.X,self.Z=site["box"]
        self.timber=self.p["timber"]
        self.log="minecraft:stripped_"+self.timber+"_log[axis=y]"
        self.block="minecraft:"+self.timber+"_planks"
        self.kerb=self.p["curb"]
        self.stone=self.p["stone"]

    def point(self,u,v):
        dx,dz=rotate(u,v,self.w,self.d,self.q)
        return self.x+dx,self.z+dz

    def put(self,u,v,up,state):
        if not(0<=u<self.w and 0<=v<self.d):
            raise ValueError((self.s["name"],u,v,"越出场内"))
        x,z=self.point(u,v)
        self.c.blocks[self.y+up,z,x]=self.c.code(rotate_state(state,self.q))

    def fill(self,x,z,X,Z,y,state):
        for v in range(z,Z):
            for u in range(x,X):self.put(u,v,y,state)

    def beam(self,u,v,U,V,h):
        axis="x" if u!=U else "z"
        self.fill(u,v,U+1,V+1,h,"minecraft:stripped_"+self.timber+"_log[axis="+axis+"]")

    def post(self,u,v,h):
        self.put(u,v,1,self.stone)
        for y in range(2,h+1):self.put(u,v,y,self.log)

    def bench(self,u,v,length=3):
        if u+length>self.w-1:return
        self.fill(u,v,u+length,v+1,1,"minecraft:"+self.timber+"_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]")

    def plant(self,u,v):
        x,z=self.point(u,v)
        plant_tree(self.c,x,z,self.y,self.p["plant"],self.p["plant"]=="spruce")

    def connect(self):
        c,f=self.c,self.f
        u=self.w//2;x,z=self.point(u,0);sx,sz=DIR[FACES[self.q]]
        start=(x+sx,z+sz)
        zz,xx=np.indices(c.ground.shape)
        mask=c.inside & ~c.occupied & ~f.rift & (~f.water|f.roads)
        levels=np.where(f.roads,c.street_height,c.ground)
        mask &= (c.blocks[levels+1,zz,xx]==0)&(c.blocks[levels+2,zz,xx]==0)
        for s in f.sites:
            a,b,A,B=s["box"]
            mask[b:B,a:A]=False
        dry_front=f.reachable_roads & (c.street_height==c.ground)
        mask &= ~f.roads | dry_front
        dry_front |= f.reachable_roads & c.public_paths
        path=route(mask,start,dry_front,levels=levels)
        if not path:raise UnconnectedCourt((c.region,self.s["name"],start,"找不到公共入口"))
        route_mask=np.zeros_like(f.free)
        for px,pz in path:route_mask[pz,px]=True
        route_mask=ndimage.binary_dilation(route_mask) & mask & ~f.roads & ~f.water
        f.paint(route_mask,self.p["curb"],4)
        f.used|=route_mask
        c.public_paths|=route_mask
        self.y=int(c.ground[start[1],start[0]])
        self.s["entry"]=[x,self.y+1,z]
        self.s["connection_length"]=len(path)

    def build(self):
        c=self.c
        self.connect()
        for z in range(self.z,self.Z):
            for x in range(self.x,self.X):
                before=c.ground[z,x]
                if before>self.y:c.blocks[self.y+1:before+1,z,x]=0
                if before<self.y:c.blocks[before:self.y,z,x]=c.code(self.stone)
                c.ground[z,x]=self.y;c.street_height[z,x]=self.y
        self.fill(0,0,self.w,self.d,0,self.p["court"])
        self.fill(1,1,self.w-1,self.d-1,0,self.f.road_profile["market_paving"])
        self.fill(2,2,self.w-2,self.d-2,0,self.p["yard"])
        self.f.labels[self.z:self.Z,self.x:self.X]=11
        self.fill(self.w//2-2,0,self.w//2+3,3,0,self.kerb)
        for v in range(3):
            for u in range(self.w//2-2,self.w//2+3):
                x,z=self.point(u,v);c.public_paths[z,x]=True
        getattr(self,"make_"+self.s["kind"])()
        self.s["grade"]=self.y

    def canopy(self,u,v,U,V,cover=None):
        roof=cover or slab(self.timber)
        for x in (u+1,U-2):
            for z in (v+1,V-2):self.post(x,z,4)
        self.beam(u+1,v+1,U-2,v+1,4)
        self.beam(u+1,V-2,U-2,V-2,4)
        self.fill(u,v,U,V,5,roof)
        self.fill(u+1,v+1,U-1,v+2,5,self.block)
        if V-v>=5:
            self.fill(u,v+1,U,v+2,6,slab(self.timber))

    def make_freight(self):
        self.canopy(2,self.d-8,self.w//2+2,self.d-2)
        for u in range(3,self.w//2-1,3):
            self.put(u,self.d-5,1,"minecraft:barrel[facing=up,open=false]")
            self.put(u+1,self.d-4,1,self.block)
        for u in (self.w*2//3,self.w*2//3+3):
            self.fill(u,3,u+1,self.d-2,0,self.kerb)
        for v in (4,self.d-5):self.post(self.w-4,v,2)

    def make_horse(self):
        self.canopy(2,self.d-8,self.w-3,self.d-2)
        for u in range(3,self.w-4,5):
            self.post(u,self.d-8,2)
            self.beam(u,self.d-8,min(u+2,self.w-4),self.d-8,2)
        self.trough(self.w-6,3,self.w-2,max(7,self.d-10))
        self.fill(3,4,5,self.d-8,0,self.stone)

    def trough(self,x,z,X,Z):
        self.fill(x,z,X,Z,0,self.stone)
        self.fill(x+1,z+1,X-1,Z-1,-1,self.stone)
        self.fill(x+1,z+1,X-1,Z-1,0,"minecraft:water[level=0]")

    def make_market(self):
        right=self.w//2
        self.canopy(2,self.d-8,right,self.d-2)
        self.canopy(right+2,self.d-8,self.w-2,self.d-2)
        for u in (3,right+3):
            self.fill(u,self.d-6,u+3,self.d-5,1,slab(self.timber))
            self.put(u,self.d-4,1,"minecraft:barrel[facing=up,open=false]")
        self.fill(2,4,self.w-2,6,0,self.p["court"])

    def make_grain(self):
        self.fill(3,4,self.w-3,self.d-4,0,"minecraft:bricks")
        for u in range(5,self.w-4,7):
            self.fill(u,5,u+1,self.d-5,0,self.kerb)
            if u!=self.w//2:self.put(u,self.d-4,1,"minecraft:hay_block[axis=y]")
        self.canopy(2,self.d-7,min(11,self.w-2),self.d-2)
        self.post(self.w-4,4,3)

    def make_timber(self):
        for v in range(4,self.d-5,5):
            for n in range(3):
                self.fill(3,v+n,self.w//2,v+n+1,1,"minecraft:spruce_log[axis=x]")
            self.fill(4,v+1,self.w//2-1,v+2,2,"minecraft:spruce_log[axis=x]")
        self.canopy(self.w//2+1,self.d-8,self.w-2,self.d-2)
        self.bench(self.w//2+2,4)

    def make_salt(self):
        # 每池有封闭的硬质池底和池堤。两组晒池之间留有可推车的堤道。
        u=self.w//2
        for x,X in ((3,u-1),(u+2,self.w-3)):
            z0,z1=4,self.d-4
            self.fill(x,z0,X,z1,0,"minecraft:cut_sandstone")
            middle=(z0+z1)//2
            self.fill(x+1,z0+1,X-1,middle,0,"minecraft:calcite")
            self.trough(x,middle,X,z1)
        self.post(2,self.d-3,2);self.post(self.w-3,self.d-3,2)

    def make_nets(self):
        self.fill(2,4,self.w-2,self.d-2,0,self.block)
        for u in (4,self.w-5):
            self.post(u,5,4);self.post(u,self.d-4,4)
            self.beam(u,5,u,self.d-4,4)
        for v in range(6,self.d-4,3):
            self.put(4,v,3,"minecraft:oak_trapdoor[facing=west,half=top,open=true,powered=false,waterlogged=false]")
        self.bench(self.w//2,self.d-4)
        self.put(3,3,1,"minecraft:barrel[facing=east,open=false]")

    def make_herb(self):
        for x in range(3,self.w-4,6):
            X=min(x+4,self.w-2)
            self.fill(x,4,X,self.d-3,0,"minecraft:mud_bricks")
            self.fill(x+1,5,X-1,self.d-4,0,"yuanxian:spirit_soil")
            for v in range(5,self.d-4):
                for u in range(x+1,X-1):self.put(u,v,1,"yuanxian:spirit_leaf_crop[age=2]" if (x//6)%2 else "yuanxian:dew_blossom_crop[age=1]")
        self.trough(2,self.d-4,self.w-2,self.d-1)

    def make_grove(self):
        mid=self.w//2
        for x,X in ((2,mid-2),(mid+2,self.w-2)):
            self.fill(x,3,X,self.d-2,0,self.stone)
            self.fill(x+1,4,X-1,self.d-3,0,"minecraft:grass_block")
            if X-x>=6:
                self.plant((x+X)//2,self.d//2)
        self.bench(mid-1,self.d-3)

    def make_rockery(self):
        self.make_grove()
        for u,v,h in ((4,5,2),(5,6,3),(self.w-6,self.d-6,3),(self.w-5,self.d-5,2)):
            for i in range(1,h+1):self.put(u,v,i,self.p["bank"])
        self.fill(self.w//2-1,3,self.w//2+2,self.d-3,0,self.kerb)

    def make_terrace(self):
        self.fill(3,self.d//2,self.w-3,self.d-2,1,self.stone)
        self.fill(4,self.d//2+2,self.w-4,self.d-3,2,self.p["court"])
        self.fill(self.w//2-2,self.d//2-1,self.w//2+2,self.d//2,1,
                  "minecraft:stone_brick_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]")
        self.fill(self.w//2-2,self.d//2+1,self.w//2+2,self.d//2+2,2,
                  "minecraft:stone_brick_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]")
        for u in (4,self.w-5):
            self.put(u,self.d-4,3,slab("stone_brick"))
        self.bench(3,4)

    def make_watercourt(self):
        self.trough(3,self.d//2-1,self.w-3,self.d-4)
        self.canopy(2,3,min(self.w//2+2,self.w-2),8)
        self.bench(self.w-7,4)
        for u in (3,self.w-4):self.post(u,self.d-3,2)

    def make_forge(self):
        self.fill(3,4,self.w-3,self.d-3,0,"minecraft:polished_blackstone_bricks")
        for x in (4,self.w-7):
            self.fill(x,6,x+2,self.d-4,0,"minecraft:waxed_cut_copper")
            for v in range(7,self.d-5):
                self.put(x,v,-1,"minecraft:shroomlight")
                self.put(x,v,0,"minecraft:waxed_copper_grate[waterlogged=false]")
        for u in (3,self.w-4):self.post(u,self.d-3,3)
        self.put(self.w//2,self.d-5,1,"minecraft:anvil[facing=north]")

    def make_civic(self):
        self.fill(3,3,self.w-3,self.d-2,0,self.f.road_profile["civic"])
        self.fill(4,4,self.w-4,self.d-3,0,self.p["court"])
        for u in (self.w//2-2,self.w//2+2):
            self.fill(u,3,u+1,self.d-3,0,self.kerb)
        for u in (3,self.w-6):self.bench(u,self.d-3)

    def shop_range(self,u,v,U,V):
        """带后墙和三开间柱廊的街坊作业房。木梁下留连续店面。"""
        w=U-u;d=V-v
        self.fill(u,v,U,V,0,self.stone)
        for level in range(1,5):
            self.fill(u,V-2,U,V-1,level,self.p["wall"])
            for x in (u+1,U-2):
                self.fill(x,v+2,x+1,V-1,level,self.p["wall"])
        for x in (u+1,(u+U)//2,U-2):
            self.post(x,v+1,4)
            for z in (V-2,):self.post(x,z,4)
        self.beam(u+1,v+1,U-2,v+1,4)
        self.beam(u+1,V-2,U-2,V-2,4)
        roof=self.p["tile"]
        for j in range(d):
            depth=min(j,d-1-j)
            y=5+depth//2
            self.fill(u,v+j,U,v+j+1,y,"minecraft:"+roof+"_slab[type="+("top" if depth%2 else "bottom")+",waterlogged=false]")
        for x in (u+3,U-4):
            self.put(x,V-3,1,"minecraft:barrel[facing=up,open=false]")
        self.fill(u+2,v+1,min(U-3,u+5),v+2,1,slab(self.timber))

    def make_ward(self):
        use=self.s["use"];w,d=self.w,self.d;mid=w//2
        orchard=use in ("林院","松院","树院","荫院","石园","水院","暖院","药园")
        # 前门接小巷，后墙和两翼界定街坊，门外仍可沿原街绕行。
        self.fill(1,2,2,d-1,1,self.stone)
        self.fill(w-2,2,w-1,d-1,1,self.stone)
        self.fill(1,d-2,w-1,d-1,1,self.stone)
        for x in range(2,w-2,8):
            self.put(x,d-2,2,self.stone)
        for x in (mid-4,mid+4):
            self.put(x,1,1,self.stone);self.put(x,1,2,slab("stone_brick"))
        self.fill(mid-2,1,mid+3,d-3,0,self.kerb)
        if orchard:
            for a,A in ((3,mid-3),(mid+3,w-3)):
                self.fill(a,3,A,d-3,0,self.p["bank"])
                self.fill(a+1,4,A-1,d-4,0,"minecraft:podzol[snowy=false]" if self.c.region=="beiyuan" else "minecraft:grass_block")
                for v in sorted({max(6,d//3),min(d-7,2*d//3)}):
                    self.plant((a+A)//2,v)
            if use=="水院":self.trough(3,d-8,mid-3,d-3)
            elif use=="药园":
                self.fill(4,4,mid-4,d-4,0,"yuanxian:spirit_soil")
                for z in range(5,d-5,2):
                    for x in range(5,mid-4,2):self.put(x,z,1,"yuanxian:dew_blossom_crop[age=1]")
            elif use=="石园":
                for x,z,h in ((4,5,2),(6,6,3),(w-7,d-6,2)):
                    for y in range(1,h+1):self.put(x,z,y,self.p["bank"])
            self.bench(mid-1,d-4)
            return
        # 同一院内，一侧为有顶作业面，对侧留树荫或露天备料场。
        self.shop_range(2,d-11,max(14,mid+1),d-2)
        self.fill(mid+3,4,w-3,d-3,0,self.p["court"])
        if use in ("网作","水作","港栈"):
            self.fill(mid+4,5,w-4,d-4,0,self.block)
            for x in (mid+4,w-5):self.post(x,5,3)
            self.beam(mid+4,5,w-5,5,3)
        elif use in ("炉作","山作","仓作","驿作","驼栈"):
            for v in (5,d-5):
                self.put(w-6,v,1,"minecraft:barrel[facing=up,open=false]")
                self.put(w-5,v+1,1,self.block)
            if use=="炉作":
                self.put(w-6,d//2,1,"minecraft:anvil[facing=east]")
            elif use=="驼栈":self.trough(mid+3,5,w-4,9)
        else:
            self.fill(w-10,4,w-3,11,0,self.stone)
            self.fill(w-9,5,w-4,10,0,"minecraft:grass_block")
            self.plant(w-7,7)
        self.bench(3,3)
