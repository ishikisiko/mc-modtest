"""按城市街坊的用途施工公共地面、岸线、庭院和待领宅田。"""
import numpy as np
from scipy import ndimage

from .plan import PLAN, area_counts, center, polygon, read, rotate


class UnconnectedCourt(ValueError):
    pass


PROFILES = PLAN / "studies/inner_neighborhoods/streetscapes.json"



def player_surface(city, plot):
    """街侧留夯平的建房面，后侧分出未播种的畦面；用地内都是等高可翻耕的土。"""
    x,z,X,Z=plot["box"]
    zz,xx=np.indices((Z-z,X-x))
    front=city.profile["plot_front"]
    across,depth=(xx,zz) if front in ("north","south") else (zz,xx)
    if front in ("south","east"):
        depth=depth.max()-depth
    width=int(across.max())+1
    if front in ("east","south"):
        across=width-1-across
    total=int(depth.max())+1
    pad=depth<round(total*.54)
    aisle=(across==2)|(across==width-3)|(depth==2)|(depth==total-3)|(depth==round(total*.54))
    dirt=city.code("minecraft:dirt")
    hard=city.code("minecraft:coarse_dirt")
    sod=city.code("minecraft:grass_block")
    rooted=city.code("minecraft:rooted_dirt")
    out=np.full(depth.shape,dirt,dtype=np.uint16)
    out[pad]=hard
    # 宽畦间留下整道草埂。房基地只在边角保留两条地界，宅内没有预置建筑。
    bed=(across-4)//12
    out[(~pad)&(bed%3==1)]=rooted
    out[(~pad)&((across-4)%12<2)]=sod
    out[(~pad)&(depth%total==total-4)]=sod
    out[aisle]=rooted
    return out


class CivicGround:
    def __init__(self,city):
        self.city=city
        self.profile=city.profile
        self.road_profile=read(PLAN/"road_surfaces.json")["profiles"][city.region]
        self.zz,self.xx=np.indices(city.ground.shape)
        self.roads=city.road!=0
        self.water=(city.natural<city.water_top)
        self.water_distance=ndimage.distance_transform_edt(~self.water) if self.water.any() else np.full(city.ground.shape,999)
        self.wall=city.inside & ~ndimage.binary_erosion(city.inside)
        self.wall_distance=ndimage.distance_transform_edt(~self.wall)
        self.road_distance=ndimage.distance_transform_edt(~self.roads)
        self.free=city.inside & ~city.occupied & ~self.roads & ~self.water & ~self.wall
        self.rift=(city.natural<17) if city.region=="moyu" else np.zeros_like(self.roads)
        self.labels=np.zeros(city.ground.shape,dtype=np.uint8)
        self.used=np.zeros_like(self.roads)
        self.objects={o["id"]:o for o in city.objects}
        self.sites=[]
        self.reserve=np.zeros_like(self.roads)
        self.quay=np.zeros_like(self.roads)

    def paint(self,mask,state,label=None):
        z,x=np.nonzero(mask)
        self.city.blocks[self.city.ground[z,x],z,x]=self.city.code(state)
        if label is not None:self.labels[mask]=label

    def rectangles(self,objects,margin):
        mask=np.zeros_like(self.roads)
        for obj in objects:
            x,z,X,Z=obj["box"]
            mask[max(0,z-margin):min(self.city.d,Z+margin),max(0,x-margin):min(self.city.w,X+margin)]=True
        return mask

    def paved_blocks(self):
        c=self.city;p=self.profile;rp=self.road_profile
        free=self.free & ~self.rift
        # 连续铺到相邻地块的后墙。不同街坊使用可以从旧州城辨认出的材料。
        self.paint(free,p["residential"],1)
        houses=[o for o in c.objects if o["type"]=="home" or o.get("role")=="house"]
        back=self.rectangles(houses,9)&free
        self.paint(back,p["backcourt"],1)
        work=[o for o in c.objects if o.get("role") in ("smith","market","inn","warehouse","qianji","apothecary","guide")]
        civic=[o for o in c.objects if o["type"]=="anchor" or o.get("role")=="gate"]
        front=self.rectangles(work,12) & free
        square=self.rectangles(civic,10) & free
        self.paint(front,rp["market_paving"],2)
        self.paint(front & (self.road_distance>6),p["yard"],2)
        self.paint(square,p["court"],3)
        # 主街外沿收一条连续的路牙，巷口与店前平台的材质自然交接。
        dist_major=ndimage.distance_transform_edt(c.major<3)
        walk=free & (self.road_distance<=3.1)
        self.paint(walk,rp["shoulder"],4)
        self.paint(walk & (dist_major<1.5),p["curb"],5)
        self.paint(free & (self.wall_distance<=4),p["stone"],6)
        # 府前与货院大块铺地用两条收边石组织尺度，条带在地块边界终止。
        for o in civic+work:
            x,z,X,Z=o["box"]
            distance=np.maximum.reduce([x-self.xx,self.xx-X+1,z-self.zz,self.zz-Z+1])
            exposed=free & (self.road_distance>=3)
            if o in civic:
                self.paint(exposed & ((distance==3)|(distance==8)),rp["civic"],3)
            elif o["role"] in ("warehouse","market","smith"):
                self.paint(exposed & (distance==8),p["curb"],2)
        # 宅院、原住民门口和府邸地标在切片中自带未处理的地皮。只处理敞天的天然表层。
        top=c.h-1-np.argmax(c.blocks[::-1]!=0,axis=0)
        at=c.blocks[c.ground,self.zz,self.xx]
        native=c.city["terrain"]["top_block"].partition("[")[0]
        raw=np.asarray([s.partition("[")[0] in {native,"minecraft:grass_block","minecraft:dirt"} for s in c.states])
        open_skin=(top==c.ground)&raw[at]
        for o in c.objects:
            if o["type"] in ("plot","garden","qianji"):continue
            x,z,X,Z=o["box"];mask=np.zeros_like(free);mask[z:Z,x:X]=True
            material=p["court"] if o["type"]=="anchor" else rp["lane_base"] if o["type"]=="home" else p["yard"]
            self.paint(mask & open_skin,material,7)
        # 在桥面以外保留原有高度；只在真有基层的主道上铺出完整幅宽。
        solid=(c.street_height==c.ground)&~self.water
        arterial=(c.road>=4)&solid
        channel=(self.roads & ~ndimage.binary_erosion(self.roads))&solid & (c.road>=3)
        viable=np.isin(c.blocks[c.street_height,self.zz,self.xx],
                       [c.code("yuanxian:manor_town_"+s) for s in c.cfg["surface"]])
        self.paint(arterial & viable,rp["main"],8)
        self.paint(channel & viable,rp["edge"],8)

    def waterfront(self):
        c=self.city;p=self.profile;r=c.region
        if r in ("donghai","nanjiang","ximo"):
            # 从真实岸线向陆侧退量。河里、桥下与宅地的地面保持独立。
            shore=self.free & (self.water_distance<=7)
            self.paint(shore,p["bank"],9)
            if r=="donghai":
                line=(self.zz>int(c.d*.49))
            elif r=="nanjiang":
                river=np.median(np.nonzero(self.water)[1])
                line=(self.xx<river)|(self.zz<c.d*.35)|(self.zz>c.d*.71)
            else:
                line=np.ones_like(shore)
            self.quay=shore & (self.water_distance<=4.6) & line
            self.paint(self.quay,("minecraft:"+p["timber"]+"_planks") if r!="ximo" else self.road_profile["shore"],9)
            edge=self.quay & (self.water_distance<=1.5)
            self.paint(edge,p["stone"] if r=="ximo" else "minecraft:stripped_"+p["timber"]+"_log[axis=z]",9)
            for z,x in zip(*np.nonzero(edge & (((self.zz%12==0)&(self.xx%2==0))|((self.xx%14==0)&(self.zz%2==0))))):
                h=c.ground[z,x]
                if c.blocks[h+1,z,x]==0:
                    c.blocks[h+1,z,x]=c.code(p["stone"] if r=="ximo" else "minecraft:stripped_"+p["timber"]+"_log[axis=y]")
        if r in ("moyu","lingyue"):
            change=ndimage.maximum_filter(c.natural,size=5)-ndimage.minimum_filter(c.natural,size=5)
            rim=self.free & (change>=2) & ~self.rift
            self.paint(rim,p["bank"],9)
            # 台面的垂直边缘砌到下一层的真实高度。
            adjacent=ndimage.minimum_filter(c.ground,size=3)
            for z,x in zip(*np.nonzero(rim & (adjacent<c.ground))):
                low,high=int(adjacent[z,x]),int(c.ground[z,x])
                c.blocks[low+1:high+1,z,x]=c.code(p["stone"])
        keep=polygon((c.w,c.d),p["reserve"]) & self.free & (self.road_distance>=4) & (self.wall_distance>=5) & ~self.quay
        groups,_=ndimage.label(keep)
        counts=np.bincount(groups.ravel())
        self.reserve=keep & (counts[groups]>=55)
        self.paint(self.reserve,c.city["terrain"]["top_block"],10)
        kerb=self.reserve & ~ndimage.binary_erosion(self.reserve)
        self.paint(kerb,p["bank"],10)

    def site(self,name,kind,owner,width,depth):
        c=self.city
        owner_box=self.objects[owner]["box"]
        aim=center(owner_box)
        forbidden=(~self.free | self.used | self.rift | self.quay | self.reserve |
                   (self.wall_distance<6) | (self.water_distance<3))
        forbidden|=ndimage.maximum_filter(c.occupied,size=5)
        if c.region in ("moyu","nanjiang"):
            divide=self.rift if c.region=="moyu" else self.water
            axis=np.array([np.mean(np.flatnonzero(row)) for row in divide])
            side=self.xx>axis[:,None]
            forbidden |= side!=side[round(aim[1]),round(aim[0])]
        forbidden|=ndimage.maximum_filter(c.street_height>c.ground+1,size=5)
        candidates=[]
        for shrink in (1,.84,.69,.55):
            for q in range(4):
                a,b=max(14,round(width*shrink)),max(11,round(depth*shrink))
                w,d=(a,b) if q%2==0 else (b,a)
                rz,rx=np.nonzero(area_counts(forbidden,w,d)==0)
                if not len(rx):continue
                dx,dz=rotate(a//2,0,a,b,q)
                ax,az=rx+dx,rz+dz
                road_dist=self.road_distance[az,ax]
                cx,cz=rx+(w-1)/2,rz+(d-1)/2
                heights=np.stack([c.ground[rz,rx],c.ground[rz,rx+w-1],c.ground[rz+d-1,rx],c.ground[rz+d-1,rx+w-1],c.ground[cz.astype(int),cx.astype(int)]])
                relief=np.ptp(heights,axis=0)
                gap_x=np.maximum.reduce((rx+0.*cx-owner_box[2],owner_box[0]-(rx+w),np.zeros_like(cx)))
                gap_z=np.maximum.reduce((rz+0.*cz-owner_box[3],owner_box[1]-(rz+d),np.zeros_like(cz)))
                score=np.hypot(gap_x,gap_z)+.18*np.hypot(cx-aim[0],cz-aim[1])+2*road_dist+9*relief+(1-shrink)*67
                if kind in ("nets","salt","watercourt") and self.water.any():score+=np.minimum(70,self.water_distance[cz.astype(int),cx.astype(int)])*.55
                viable=(relief<=3)&(road_dist<=27)
                if not viable.any():continue
                score[~viable]=np.inf;i=int(np.argmin(score))
                candidates.append((float(score[i]),int(rx[i]),int(rz[i]),q,a,b,w,d))
        if not candidates:raise ValueError((c.region,name,"无可用场地"))
        _,x,z,q,a,b,w,d=min(candidates)
        X,Z=x+w,z+d
        self.used[max(0,z-2):min(c.d,Z+2),max(0,x-2):min(c.w,X+2)]=True
        loc={"name":name,"kind":kind,"owner":owner,"box":[x,z,X,Z],"turn":q,"width":a,"depth":b}
        self.sites.append(loc)
        return loc

    def wall_lanes(self):
        from .plan import route
        from .readback import market_reach
        c=self.city;zz,xx=self.zz,self.xx
        grade=np.where(self.roads,c.street_height,c.ground)
        clear=(c.blocks[grade+1,zz,xx]==0)&(c.blocks[grade+2,zz,xx]==0)
        space=c.inside&~c.occupied&~self.water&~self.rift&~self.wall&~self.quay&clear
        free=space&~self.roads
        # 巡墙巷沿实际墙线走，在城门、府台和水岸让开。
        ring=free&(self.wall_distance>=5)&(self.wall_distance<=8)&~self.reserve
        labels,n=ndimage.label(ring)
        for index in range(1,n+1):
            seg=labels==index
            if seg.sum()<55:continue
            rz,rx=np.nonzero(seg)
            for fraction in (.2,.76):
                i=min(len(rx)-1,round((len(rx)-1)*fraction))
                start=int(rx[i]),int(rz[i])
                path=route(space&(~self.roads|self.reachable_roads),start,self.reachable_roads,levels=grade)
                if not path:continue
                trail=np.zeros_like(free)
                for x,z in path:trail[z,x]=True
                trail=ndimage.binary_dilation(trail)&free&~self.reserve
                c.public_paths|=trail
                self.paint(trail,self.road_profile["lane_base"],13)
            c.public_paths|=seg
            self.paint(seg,self.road_profile["lane_base"],13)
            self.paint(seg&(self.wall_distance<5.8),self.profile["curb"],13)
        self.reachable_roads=market_reach(c,self.roads|c.public_paths,grade)
        self.access_distance=ndimage.distance_transform_edt(~self.reachable_roads)
        self.used|=c.public_paths

    def infill(self):
        from .courts import PublicCourt
        c=self.city;limit=18
        choices=self.profile["infill"]
        for order in range(limit):
            blocked=(~self.free|self.used|self.rift|self.quay|self.reserve|
                     (self.wall_distance<4)|(self.water_distance<2)|
                     ndimage.maximum_filter(c.occupied,size=3))
            results=[]
            for a,b in ((49,29),(39,23),(32,19),(25,15)):
                if results:break
                for q in range(4):
                    w,d=(a,b) if q%2==0 else (b,a)
                    rz,rx=np.nonzero(area_counts(blocked,w,d)==0)
                    if not len(rx):continue
                    du,dv=rotate(a//2,0,a,b,q)
                    dest=self.access_distance[rz+dv,rx+du]
                    heights=[c.ground[rz,rx],c.ground[rz+d-1,rx],c.ground[rz,rx+w-1],c.ground[rz+d-1,rx+w-1]]
                    relief=np.ptp(heights,axis=0)
                    valid=(dest<=20)&(relief<=2)
                    if not valid.any():continue
                    cx,cz=rx+w//2,rz+d//2
                    # 优先填仍然开阔的院背，并让入口靠近已能通行的街。
                    score=dest*1.5+relief*8-.45*self.road_distance[cz,cx]
                    score[~valid]=np.inf
                    i=int(np.argmin(score))
                    results.append((float(score[i]),int(rx[i]),int(rz[i]),q,w,d,a,b))
            if not results:break
            _,x,z,q,w,d,a,b=min(results);X,Z=x+w,z+d
            local=choices[order%len(choices)]
            self.used[max(z-2,0):Z+2,max(x-2,0):X+2]=True
            site={"name":local+" · "+str(order+1),"kind":"ward","use":local,"box":[x,z,X,Z],"turn":q,"width":a,"depth":b}
            self.sites.append(site)
            try:
                PublicCourt(self,site).build()
            except UnconnectedCourt:
                self.sites.pop()
        c.ward_count=sum(s["kind"]=="ward" for s in self.sites)

    def greens_and_seating(self):
        from .courts import plant_tree
        c=self.city
        zz,xx=self.zz,self.xx
        open_at=c.blocks[c.ground+1,zz,xx]==0
        available=self.free & ~self.used & ~self.quay & ~self.rift & open_at
        planted=np.zeros_like(self.free)
        def tree_at(x,z,park=False):
            y=int(c.ground[z,x])
            patch=np.s_[z-3:z+4,x-3:x+4]
            if not available[patch].all() or planted[patch].any() or np.ptp(c.ground[patch])>2:return False
            if not park:
                edge=np.zeros_like(available);edge[z-2:z+3,x-2:x+3]=True
                self.paint(edge,self.profile["curb"],12)
                edge[:]=False;edge[z-1:z+2,x-1:x+2]=True
                self.paint(edge,"minecraft:rooted_dirt",12)
            plant_tree(c,x,z,y,self.profile["plant"],self.profile["plant"]=="spruce")
            planted[patch]=True
            return True
        # 顺着同一幢建筑的街面布置成排树池，让转角、门口与窗前保留视线。
        for o in c.objects:
            if o.get("role") not in ("inn","market","warehouse","apothecary","qianji","gate") and o["type"]!="plot":continue
            face=o["fronts"][0] if o["type"]=="plot" else o.get("front","south")
            x,z,X,Z=o["box"];dx,dz={"north":(0,-1),"east":(1,0),"south":(0,1),"west":(-1,0)}[face]
            for offset in (8,11):
                slots=[(x-1-offset if dx<0 else X+offset,az) for az in range(z+3,Z-2,9)] if dx else [(ax,z-1-offset if dz<0 else Z+offset) for ax in range(x+3,X-2,9)]
                for ax,az in slots:
                    if not(4<=ax<c.w-4 and 4<=az<c.d-4) or self.road_distance[az,ax]>9:continue
                    if o.get("outside") and abs(ax-o["outside"][0])+abs(az-o["outside"][1])<6:continue
                    tree_at(ax,az)
        # 保留地上以数株成丛，水岸和深沟本身保持通透。
        inset=ndimage.distance_transform_edt(self.reserve)
        for z in range(7,c.d-6,8):
            for x in range(6+(z//8%2)*4,c.w-6,11):
                if inset[z,x]>=4 and available[z,x] and not self.rift[z,x] and self.water_distance[z,x]>5:tree_at(x,z,True)
        c.tree_courts=int(ndimage.label(planted)[1])
    def build(self):
        from .courts import PublicCourt
        self.paved_blocks()
        self.waterfront()
        from .readback import market_reach
        self.reachable_roads=market_reach(self.city,self.roads,self.city.street_height)
        self.wall_lanes()
        for args in self.profile["sites"]:
            site=self.site(*args)
            PublicCourt(self,site).build()
        self.infill()
        self.greens_and_seating()
        self.city.ground_sites=self.sites
        self.city.surface_roles=self.labels
        self.city.reserve_ground=self.reserve
        self.city.waterfront_ground=self.quay
