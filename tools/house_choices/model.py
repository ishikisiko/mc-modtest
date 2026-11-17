"""六套住宅的离线建筑草案；不生成或修改任何游戏资源。"""
from __future__ import annotations
from contextlib import contextmanager
from pathlib import Path
import copy,json,math

from tools.reference_courtyard.assembly_scene import Site

ROOT=Path(__file__).resolve().parents[2]
KIT=json.loads((ROOT/'tools/reference_courtyard/assemblies.json').read_text())


def mc(name,**props):
    return 'minecraft:'+name+('['+','.join(f'{k}={str(v).lower()}'for k,v in props.items())+']'if props else'')


class Study(Site):
    def __init__(self,info):
        super().__init__({**copy.deepcopy(KIT),**copy.deepcopy(info)})
        self.config=self.c;self.layers={};self.layer='architecture';self.rooms=[];self.roofs=[];self.targets={};self.doors=[]

    def put(self,p,block):
        super().put(p,block);self.layers[p]=self.layer

    @contextmanager
    def as_layer(self,layer):
        before=self.layer;self.layer=layer
        try:yield
        finally:self.layer=before

    def box(self,bounds,block):
        a,b,c,d,e,f=bounds
        for x in range(a,d):
            for y in range(b,e):
                for z in range(c,f):self.put((x,y,z),block)

    def erase(self,bounds):
        a,b,c,d,e,f=bounds
        for p in list(self.blocks):
            x,y,z=p
            if a<=x<d and b<=y<e and c<=z<f:
                assert p not in self.owners,('不能切分整件',p)
                del self.blocks[p];del self.layers[p]

    def ground(self,material='grass_block'):
        w,_,d=self.c['size']
        with self.as_layer('ground'):
            self.box([0,0,0,w,1,d],mc('stone_bricks'))
            self.box([1,1,1,w-1,2,d-1],mc(material))
            for b in [[0,1,0,w,2,1],[0,1,d-1,w,2,d],[0,1,1,1,2,d-1],[w-1,1,1,w,2,d-1]]:self.box(b,mc('smooth_stone'))

    def surface(self,bounds,material='gravel',y=1):
        a,b,c,d=bounds
        with self.as_layer('ground'):self.box([a,y,b,c,y+1,d],mc(material))

    def slab(self,bounds,feet=3,wood='spruce_planks',base='stone_bricks'):
        a,b,c,d=bounds
        if 2<feet<=3:self.box([a,2,b,c,feet-1,d],mc(base))
        self.box([a,feet-1,b,c,feet,d],mc(wood))

    def room(self,key,name,bounds,feet=3,wall='plaster',wood='dark_oak',height=6,doors=(),windows=(),roof=None):
        a,b,c,d=bounds;top=feet+height
        self.slab(bounds,feet,wood+'_planks')
        material=(f'yuanxian:courtyard_wall[facing=north,variant={0 if wall=="plaster"else 1},waterlogged=false,part=0]'if wall in('plaster','dado')else mc(wall))
        for box in [[a,feet,b,c,top,b+1],[a,feet,d-1,c,top,d],[a,feet,b+1,a+1,top,d-1],[c-1,feet,b+1,c,top,d-1]]:self.box(box,material)
        for x in (a,c-1):
            for z in(b,d-1):self.box([x,feet,z,x+1,top,z+1],mc('stripped_'+wood+'_log',axis='y'))
        for z in(b,d-1):self.box([a,top-1,z,c,top,z+1],mc('stripped_'+wood+'_log',axis='x'))
        for x in(a,c-1):self.box([x,top-1,b,x+1,top,d],mc('stripped_'+wood+'_log',axis='z'))
        for side,start in doors:
            if side in('front','back'):
                z=b if side=='front'else d-1
                self.erase([start,feet,z,start+5,feet+5,z+1])
                root=(start,feet,z)if side=='front'else(start+4,feet,z)
            else:
                x=a if side=='left'else c-1
                self.erase([x,feet,start,x+1,feet+5,start+5])
                root=(x,feet,start+4)if side=='left'else(x,feet,start)
            facing={'front':'north','back':'south','left':'west','right':'east'}[side]
            self.place(root,'double_door','lattice',facing);self.doors.append(list(root))
        for side,start in windows:
            y=feet+2
            if side in('front','back'):
                z=b if side=='front'else d-1
                self.erase([start,y,z,start+3,y+3,z+1]);root=(start,y,z)if side=='front'else(start+2,y,z)
            else:
                x=a if side=='left'else c-1
                self.erase([x,y,start,x+1,y+3,start+3]);root=(x,y,start+2)if side=='left'else(x,y,start)
            self.place(root,'window','square',{'front':'north','back':'south','left':'west','right':'east'}[side])
        colors=['#c6ad88','#a6b996','#b4c6ca','#bfacb9','#d0c18c']
        self.rooms.append({'id':key,'name':name,'bounds':[a+1,b+1,c-1,d-1],'floor_y':feet,'color':colors[len(self.rooms)%len(colors)]})
        if roof:self.roofs.append({**roof,'room_bounds':bounds,'wall_top':top,'wall':material,'wood':wood})

    def porch(self,bounds,feet=3,top=8,posts=(),wood='dark_oak',roof=None):
        self.slab(bounds,feet,wood+'_planks')
        a,b,c,d=bounds
        for x,z in posts:
            self.box([x,feet,z,x+1,top,z+1],mc('stripped_'+wood+'_log',axis='y'))
        self.box([a,top-1,b,c,top,b+1],mc('stripped_'+wood+'_log',axis='x'))
        self.box([a,top-1,d-1,c,top,d],mc('stripped_'+wood+'_log',axis='x'))
        self.box([a,top-1,b,a+1,top,d],mc('stripped_'+wood+'_log',axis='z'))
        self.box([c-1,top-1,b,c,top,d],mc('stripped_'+wood+'_log',axis='z'))
        if roof:self.roofs.append(roof)

    def stairs(self,x,z,width,length,base=2,axis='z',wood='stone_brick',reverse=False):
        # Half-block treads keep every rise within the player's normal step height.
        for i in range(length):
            u=length-1-i if reverse else i;feet=base+(i+1)*.5
            a,b=(x+u,z)if axis=='x'else(x,z+u)
            c,d=(a+1,b+width)if axis=='x'else(a+width,b+1)
            y=math.floor(feet)
            if y>2:self.box([a,2,b,c,y,d],mc('stone_bricks'))
            if feet==y:self.box([a,y-1,b,c,y,d],mc(wood+'_slab',type='double',waterlogged=False))
            else:self.box([a,y,b,c,y+1,d],mc(wood+'_slab',type='bottom',waterlogged=False))

    def garden(self,bounds,plant='bamboo',top=2,edge='stone_brick_slab'):
        a,b,c,d=bounds;self.surface(bounds,'moss_block',top-1)
        with self.as_layer('landscape'):
            for x in range(a,c):
                for z in(b,d-1):self.put((x,top,z),mc(edge,type='bottom',waterlogged=False))
            for z in range(b+1,d-1):
                for x in(a,c-1):self.put((x,top,z),mc(edge,type='bottom',waterlogged=False))
            if plant=='bamboo':
                for x in range(a+2,c-1,3):
                    for z in range(b+2,d-1,3):
                        for y in range(top,top+5):self.put((x,y,z),mc('bamboo',age=1,leaves='large'if y>=top+3 else'none',stage=1))
            elif plant=='fern':
                for x in range(a+1,c-1,2):
                    for z in range(b+1,d-1,2):self.put((x,top,z),mc('fern'))
            elif plant=='azalea':
                for x in range(a+1,c-1,3):
                    for z in range(b+1,d-1,3):self.put((x,top,z),mc('flowering_azalea'))
            elif plant=='none':pass
            else:raise ValueError(plant)

    def table(self,x,y,z,wood='dark_oak',length=3):
        with self.as_layer('furniture'):
            for dx in range(length):self.put((x+dx,y,z),mc(wood+'_slab',type='bottom',waterlogged=False))
            for dx in range(length):
                for dz in(-1,1):self.put((x+dx,y,z+dz),mc('brown_carpet'))

    def bedroom(self,x,y,z,color='brown'):
        with self.as_layer('furniture'):
            for dx in(0,1):
                for part,dz in(('foot',0),('head',1)):self.put((x+dx,y,z+dz),mc(color+'_bed',part=part,facing='south',occupied=False))
            self.put((x+3,y,z+1),mc('barrel',facing='north',open=False));self.put((x+3,y+1,z+1),mc('lantern',hanging=False,waterlogged=False))

    def kitchen(self,x,y,z,length=4,axis='z'):
        with self.as_layer('furniture'):
            blocks=[mc('smoker',facing='west',lit=False),mc('crafting_table'),mc('furnace',facing='west',lit=False),mc('barrel',facing='up',open=False)]
            for i in range(length):self.put((x+(i if axis=='x'else 0),y,z+(i if axis=='z'else 0)),blocks[i%len(blocks)])

    def storage(self,x,y,z,length=3):
        with self.as_layer('furniture'):
            for dx in range(length):
                for dy in(0,1):self.put((x+dx,y+dy,z),mc('barrel',facing='north',open=False))

    def lantern(self,x,y,z):
        with self.as_layer('furniture'):self.put((x,y,z),mc('lantern',hanging=True,waterlogged=False))

    def roof_all(self):
        for spec in self.roofs:self.make_roof(spec)

    def make_roof(self,r):
        a,b,c,d=r['bounds'];eave=r['eave'];axis=r.get('axis','x');style=r.get('style','tile');material=r.get('material','deepslate_tile')
        cross=c-a if axis=='z'else d-b;half=(cross-1)//2
        def level(x,z):
            n=min(x-a,c-1-x)if axis=='z'else min(z-b,d-1-z)
            if style=='hip':n=min(n,z-b,d-1-z)if axis=='z'else min(n,x-a,c-1-x)
            if style=='shed':n=(x-a)if axis=='z'else(z-b)
            return n
        # End walls continue up to the sloping roof, with timber purlins.
        if 'room_bounds'in r:
            ra,rb,rc,rd=r['room_bounds'];wall_top=r['wall_top']
            for x in range(ra,rc):
                for z in range(rb,rd):
                    if x not in(ra,rc-1)and z not in(rb,rd-1):continue
                    height=eave+level(x,z)//2
                    if height>wall_top:self.box([x,wall_top,z,x+1,height,z+1],r['wall'])
        with self.as_layer('roof'):
            for x in range(a,c):
                for z in range(b,d):
                    n=level(x,z);y=eave+n//2
                    # Higher intersecting roofs retain their continuous skin.
                    if self.layers.get((x,y,z))=='roof':continue
                    if style=='tile'and r.get('kit',False):
                        val=x if axis=='z'else z;lo=a if axis=='z'else b
                        facing=('west'if val<lo+half else'east')if axis=='z'else('north'if val<lo+half else'south')
                        key='eave'if n==0 else'flat'if n==half else'low'if n%2==0 else'high'
                        self.place((x,y,z),'roof',key,facing)
                    else:
                        self.put((x,y,z),mc(material+'_slab',type='bottom'if n%2==0 else'double',waterlogged=False))
            if style!='shed':
                start,end=(b+1,d-1)if axis=='z'else(a+1,c-1)
                for t in range(start,end):
                    x,z=(a+half,t)if axis=='z'else(t,b+half);n=level(x,z);y=eave+n//2
                    if (x,y,z)not in self.owners:self.put((x,y,z),mc('polished_deepslate_slab',type='double',waterlogged=False))


def info(key,name,description,reason,concern,size,author='主代理规划'):
    w,d=size
    return {'id':key,'name':name,'description':description,'reason':reason,'concern':concern,'author':author,'size':[w,36,d],
        'plot_size':[64,72],'plot_origin':[(64-w)//2,72-d-4],'views':{
            'overview':{'name':'全貌','eye':[w*1.35,34,-d*.6],'target':[w/2,5,d*.52],'fov':43},
            'court':{'name':'院内','eye':[w*.45,5.6,d*.19],'target':[w*.52,6,d*.72],'fov':64},
            'cut':{'name':'去顶看房间','eye':[w*1.05,58,-d*.23],'target':[w/2,2,d*.53],'cut':5,'rooms':True,'fov':43},
            'plot':{'name':'放进宅田地块','eye':[-42,83,-40],'target':[32,3,36],'plot':True,'fov':43}}}


def bamboo():
    s=Study(info('bamboo','竹庭廊宅','起居厅和两侧生活房围着竹庭展开，低檐回廊连接三面。','门后的折廊先遮住起居厅，转过竹庭再进入主屋，侧房从廊下进入。','侧廊与竹林之间的宽度，需要从院内机位检查。',[48,48]))
    s.ground();s.surface([2,3,46,44],'gravel');s.surface([4,6,44,13],'smooth_stone')
    s.garden([17,15,25,29]);s.garden([27,19,32,29],'fern');s.garden([4,7,16,12],'azalea')
    s.surface([25,12,28,33],'stone_bricks');s.surface([12,12,36,16],'stone_bricks')
    s.room('living','起居与书案',[13,33,39,44],doors=[('front',24)],windows=[('back',17),('back',32),('left',37),('right',37)],roof={'bounds':[11,31,41,48],'eave':9,'axis':'x','kit':True})
    s.room('bedroom','卧房',[4,16,13,30],doors=[('right',21)],windows=[('left',20),('back',7)],roof={'bounds':[2,14,15,32],'eave':9,'axis':'z','style':'hip'})
    s.room('kitchen','厨房',[35,16,44,30],doors=[('left',21)],windows=[('right',20),('back',38)],roof={'bounds':[33,14,46,32],'eave':9,'axis':'z','style':'hip'})
    s.porch([13,29,35,33],posts=[(16,29),(22,29),(30,29),(34,29)],roof={'bounds':[13,28,35,32],'eave':8,'axis':'x','style':'shed'})
    s.porch([13,16,17,29],posts=[(16,17),(16,23),(16,28)],roof={'bounds':[13,15,17,29],'eave':8,'axis':'z','style':'shed'})
    s.porch([31,16,35,29],posts=[(31,17),(31,23),(31,28)],roof={'bounds':[31,15,35,29],'eave':8,'axis':'z','style':'shed'})
    s.porch([20,9,35,13],posts=[(20,9),(27,9),(34,9)],roof={'bounds':[19,8,36,13],'eave':7,'axis':'x','style':'shed'})
    s.box([20,3,12,27,6,13],'yuanxian:courtyard_wall[facing=north,variant=0,waterlogged=false,part=0]')
    s.stairs(28,7,5,2);s.stairs(25,16,3,2);s.stairs(25,29,3,2)
    s.table(17,3,38);s.bedroom(6,3,26);s.kitchen(41,3,23);s.storage(34,3,41)
    for x,z in[(18,32),(31,32),(16,21),(31,25)]:s.lantern(x,6,z)
    s.targets={'起居厅':[27,3,36],'卧房':[9,3,26],'厨房':[39,3,24],'储物':[33,3,40]};s.start=[30,2,5]
    s.c['views']['court']={'name':'竹庭与回廊','eye':[26,5.6,17],'target':[24,6,35],'fov':66}
    s.roof_all();return s


def terrace():
    s=Study(info('terrace','坡台石木宅','下层安排起居和厨房，上层卧房面向挑出的观景廊。','石砌台基承接高差，楼梯沿侧面上行，住宅体量随台阶逐层收小。','上层屋檐与观景廊之间的高度，需要从人视检查。',[44,46]))
    s.ground();s.surface([3,3,41,23],'gravel');s.garden([4,6,13,17],'fern');s.garden([31,6,40,15],'azalea')
    s.slab([7,19,37,41],3,'spruce_planks','stone_bricks')
    s.room('living','下层起居厅',[14,22,35,40],feet=3,wall='stone_bricks',wood='spruce',doors=[('front',22),('left',29)],windows=[('right',26),('back',28)])
    s.room('kitchen','厨房',[5,26,14,39],feet=3,wall='dado',wood='spruce',doors=[('right',29)],windows=[('left',29)],roof={'bounds':[3,24,16,41],'eave':9,'axis':'z','style':'hip'})
    # The upper floor covers the back half, leaving the front hall double height.
    s.room('bedroom','上层卧房',[17,29,34,40],feet=9,wall='plaster',wood='spruce',doors=[('front',23)],windows=[('back',22),('left',33)],roof={'bounds':[15,27,36,44],'eave':15,'axis':'x','style':'tile','material':'dark_oak'})
    s.roofs.append({'bounds':[13,20,37,29],'eave':9,'axis':'x','style':'shed','material':'dark_oak'})
    s.porch([17,25,34,29],feet=9,top=14,posts=[(17,25),(23,25),(33,25)],wood='spruce',roof={'bounds':[16,24,35,28],'eave':14,'axis':'x','style':'shed','material':'dark_oak'})
    s.slab([35,22,39,37],3,'spruce_planks')
    s.stairs(35,22,4,12,base=3,wood='spruce');s.slab([34,34,39,40],9,'spruce_planks')
    # Upper landing connects through the bedroom's east wall.
    s.erase([33,9,34,34,13,37]);s.box([33,13,34,34,14,37],mc('stripped_spruce_log',axis='z'))
    for x in range(17,34):
        if 23<=x<=27:continue
        s.put((x,9,25),mc('spruce_fence',north=False,south=False,east=x<33,west=x>17,waterlogged=False))
    s.porch([14,18,35,22],feet=3,top=8,posts=[(14,18),(21,18),(28,18),(34,18)],wood='spruce',roof={'bounds':[13,17,36,21],'eave':8,'axis':'x','style':'shed','material':'dark_oak'})
    s.stairs(22,16,5,2);s.surface([22,3,27,17],'stone_bricks')
    s.table(20,3,29,'spruce');s.kitchen(7,3,33);s.storage(29,3,38);s.bedroom(20,9,36,'green')
    s.box([17,14,35,34,15,36],mc('stripped_spruce_log',axis='x'))
    s.lantern(26,13,35);s.lantern(26,6,18)
    s.targets={'下层起居':[25,3,27],'厨房':[10,3,34],'储物':[29,3,36],'上层卧房':[25,9,36]};s.start=[24,2,5]
    s.c['views']['court']={'name':'台阶与挑廊','eye':[29,5,10],'target':[26,11,30],'fov':67}
    s.c['views']['upper']={'name':'上层卧房','eye':[37,13,32],'target':[24,11,36],'fov':67,'cut':14,'open_doors':True}
    s.roof_all();return s


def stream():
    s=Study(info('stream','临溪工坊宅','起居、卧房和工坊沿溪岸铺开，三间屋共用一条连续深檐廊。','溪岸决定长向布局，工坊伸向桥头，卧房在远端形成安静的一翼。','长檐的长度与工坊凸出的体量，需要在全貌里核对比例。',[52,42]))
    s.ground();s.surface([3,3,49,38],'gravel');s.garden([32,5,47,11],'fern');s.garden([21,17,29,22],'azalea')
    with s.as_layer('water'):
        for x in range(3,49):
            for z in range(8,14):s.put((x,1,z),mc('water',level=0))
    s.surface([3,7,49,8],'stone_bricks');s.surface([3,14,49,15],'stone_bricks')
    s.room('workshop','工坊与储藏',[5,18,18,36],wall='dado',wood='oak',doors=[('right',22),('front',9)],windows=[('left',25),('back',10)],roof={'bounds':[3,16,20,39],'eave':10,'axis':'z','style':'hip'})
    s.room('living','起居室',[21,24,35,37],wood='oak',doors=[('front',25)],windows=[('back',26),('left',28)],roof={'bounds':[19,22,37,39],'eave':9,'axis':'x','kit':True})
    s.room('bedroom','卧房',[38,23,48,36],wood='oak',doors=[('front',40)],windows=[('right',28),('back',41)],roof={'bounds':[36,21,51,39],'eave':9,'axis':'z','style':'hip'})
    s.porch([18,19,49,23],posts=[(19,19),(25,19),(33,19),(40,19),(48,19)],wood='oak',roof={'bounds':[18,18,50,24],'eave':8,'axis':'x','style':'shed'})
    s.slab([18,23,38,24],3,'oak_planks')
    s.porch([18,23,21,33],posts=[(18,27),(18,32)],wood='oak',roof={'bounds':[18,23,21,34],'eave':8,'axis':'z','style':'shed'})
    s.porch([35,23,38,33],posts=[(35,28)],wood='oak',roof={'bounds':[35,23,38,34],'eave':8,'axis':'z','style':'shed'})
    s.slab([8,5,14,19],3,'oak_planks');s.stairs(8,3,6,2)
    for x in(8,13):
        for z in range(6,17):s.put((x,3,z),mc('oak_fence',north=z>6,south=z<16,east=False,west=False,waterlogged=False))
    s.surface([14,15,31,19],'stone_bricks');s.stairs(18,16,5,2)
    s.table(24,3,30,'oak');s.bedroom(41,3,32,'blue');s.kitchen(7,3,31);s.storage(11,3,34)
    with s.as_layer('furniture'):
        for x in range(8,13):s.put((x,3,25),mc('oak_slab',type='double',waterlogged=False))
        s.put((8,4,25),mc('crafting_table'))
    for x in(22,33,46):s.lantern(x,6,19)
    s.targets={'工坊':[14,3,27],'厨房':[10,3,31],'储物':[13,3,33],'起居室':[29,3,29],'卧房':[44,3,30]};s.start=[11,2,2]
    s.c['views']['court']={'name':'溪岸深檐','eye':[21,5.3,10],'target':[33,6,26],'fov':68}
    s.roof_all();return s


BUILDERS=[bamboo,terrace,stream]


def build_all():return [fn()for fn in BUILDERS]


def validate(site):
    from tools.moon_courtyard_home.model import walking
    for i,a in enumerate(site.assemblies):
        for pos in a['parts']:
            p=tuple(pos);assert site.owners[p]==i and p in site.blocks,('整件损坏',i,p)
    assert set(site.blocks)==set(site.layers)
    roof_columns={(x,z)for (x,y,z),layer in site.layers.items()if layer=='roof'}
    roof_cells=0
    for room in site.rooms:
        a,b,c,d=room['bounds']
        for x in range(a,c):
            for z in range(b,d):
                assert (x,z)in roof_columns,('房间缺少屋面覆盖',room['name'],x,z)
                roof_cells+=1
    lamps=[p for p,b in site.blocks.items()if b.startswith('minecraft:lantern[')and 'hanging=true'in b]
    for x,y,z in lamps:assert (x,y+1,z)in site.blocks,('悬灯缺少上方支承',x,y,z)
    walk={'body_half_width':.3,'body_height':1.8,'feet_epsilon':.015,'step_height':.6,'edge_samples':4,
          'minimum_feet':2,'maximum_feet':20,'start':site.start,'targets':[{'name':k,'position':v}for k,v in site.targets.items()],'tour':list(site.targets),'service':[]}
    result=walking(site,walk)
    return {'walk':result,'source_cells':len(site.blocks),'whole_assemblies':len(site.assemblies),'roof_covered_room_columns':roof_cells,'supported_hanging_lanterns':len(lamps),
        'owner_visual_acceptance':'pending','game_integration':False,'minecraft_runtime':'not_run'}
