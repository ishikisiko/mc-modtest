"""较大住宅的三套作者草案；仅用于离线三维预览。"""
from __future__ import annotations
import math
from tools.house_choices.model import Study,info,mc,validate


class ComplexStudy(Study):
    def __init__(self,*args):
        super().__init__(*args);self.courts=[];self.flights=[];self.roof_palette='gray'

    def porch(self,bounds,feet=3,top=None,posts=(),wood='dark_oak',roof=None):
        if top is None:
            top=7
            if roof:roof={**roof,'eave':7,'style':'tile'}
        super().porch(bounds,feet,top,posts,wood,roof)

    def roof_all(self):
        for spec in sorted(self.roofs,key=lambda r:bool(r.get('curve_span'))):self.make_roof(spec)

    def make_roof(self,r):
        a,b,c,d=r['bounds'];eave=r['eave'];axis=r.get('axis','x');style=r.get('style','tile')
        if r.get('curve_span'):
            assert r['curve_span']==17 and (c-a if axis=='z'else d-b)==17
            walls=r.get('room_bounds');ends=(walls[1],walls[3]-1)if walls and axis=='z'else(walls[0],walls[2]-1)if walls else()
            with self.as_layer('roof'):
                for t in range(b,d)if axis=='z'else range(a,c):
                    root=(a,eave,t)if axis=='z'else(t,eave,b)
                    self.place(root,'roof','curve_gable'if t in ends else'curve_pavilion','north'if axis=='z'else'east')
            return
        half=((c-a if axis=='z'else d-b)-1)//2
        def profile(x,z):
            choices=[(z-b,'north'),(d-1-z,'south')]if axis=='x'else[(x-a,'west'),(c-1-x,'east')]
            if style=='hip':choices += [(x-a,'west'),(c-1-x,'east')]if axis=='x'else[(z-b,'north'),(d-1-z,'south')]
            if style=='shed':return (z-b,'north')if axis=='x'else(x-a,'west')
            return min(choices,key=lambda pair:pair[0])
        if 'room_bounds'in r:
            ra,rb,rc,rd=r['room_bounds'];wall_top=r['wall_top']
            for x in range(ra,rc):
                for z in range(rb,rd):
                    if x not in(ra,rc-1)and z not in(rb,rd-1):continue
                    y=eave+profile(x,z)[0]//2
                    if y>wall_top:self.box([x,wall_top,z,x+1,y,z+1],r['wall'])
        with self.as_layer('roof'):
            for x in range(a,c):
                for z in range(b,d):
                    n,facing=profile(x,z);y=eave+n//2
                    if self.roof_palette=='gray':
                        key='eave'if n==0 else'flat'if style!='shed'and n==half else'low'if n%2==0 else'high'
                        self.place((x,y,z),'roof',key,facing)
                    else:self.put((x,y,z),mc(r['material']+'_slab',type='bottom'if n%2==0 else'double',waterlogged=False))
            # Native copper roofs keep their ridge in the same material.
            if self.roof_palette!='gray'and style!='shed':
                for t in range(b+1,d-1)if axis=='z'else range(a+1,c-1):
                    x,z=(a+half,t)if axis=='z'else(t,b+half);n,_=profile(x,z)
                    self.put((x,eave+n//2,z),mc(r['material']+'_slab',type='double',waterlogged=False))

    def raised(self,bounds,feet,wood='spruce',stone='stone_bricks'):
        a,b,c,d=bounds
        if feet>3:self.box([a,2,b,c,feet-1,d],mc(stone))
        self.slab(bounds,feet,wood+'_planks',stone)

    def flight(self,x,z,width,length,base=2,axis='z',wood='spruce',solid=False,rails=False):
        for i in range(length):
            feet=base+(i+1)/2;y=math.floor(feet)
            a,b=(x+i,z)if axis=='x'else(x,z+i)
            c,d=(a+1,b+width)if axis=='x'else(a+width,b+1)
            if solid and y>2:self.box([a,2,b,c,y,d],mc('stone_bricks'))
            if feet==y:self.box([a,y-1,b,c,y,d],mc(wood+'_slab',type='double',waterlogged=False))
            else:self.box([a,y,b,c,y+1,d],mc(wood+'_slab',type='bottom',waterlogged=False))
            if rails:
                for side in(-1,width):
                    p=(a,y,b+side)if axis=='x'else(a+side,y,b)
                    facing=('south'if side<0 else'north')if axis=='x'else('east'if side<0 else'west')
                    self.put(p,mc(wood+'_trapdoor',facing=facing,half='bottom',open=True,powered=False,waterlogged=False))
                    if i%2:self.put((p[0],y+1,p[2]),mc(wood+'_fence',north=False,south=False,east=False,west=False,waterlogged=False))
        self.flights.append({'origin':[x,base,z],'axis':axis,'width':width,'length':length,'top':base+length/2,'rails':rails})

    def rail(self,axis,start,end,fixed,feet,wood='spruce',gaps=()):
        for t in range(start,end):
            if any(a<=t<b for a,b in gaps):continue
            p=(t,feet,fixed)if axis=='x'else(fixed,feet,t)
            self.put(p,mc(wood+'_fence',north=axis=='z',south=axis=='z',east=axis=='x',west=axis=='x',waterlogged=False))

    def court(self,name,bounds,feet=2):
        self.courts.append({'name':name,'bounds':bounds,'floor_y':feet})

    def books(self,x,y,z,length=4):
        with self.as_layer('furniture'):self.box([x,y,z,x+length,y+2,z+1],mc('bookshelf'))


def meta(key,name,description,reason,concern,size):
    c=info(key,name,description,reason,concern,size);c['size'][1]=40
    c['views']['overview'].update(eye=[size[0]*1.3,39,-size[1]*.55],target=[size[0]/2,7,size[1]*.52])
    c['views']['cut'].update(name='查看一层',cut=5.8)
    c['views']['plot']['name']='宅田与连续留白'
    return c


def water_courts():
    s=ComplexStudy(meta('water-courts','双院水廊宅','前院以长水面和低桥展开，月洞廊墙后收成竹庭，主楼与低檐廊形成高低两层屋顶。','穿过短门厅后先看到水榭，再由月洞廊进入竹庭，前后两处院子形成不同的围合程度。','月洞廊墙与上层卧房相邻，需在院内判断视线是否拥挤。',[56,52]))
    s.ground();s.surface([3,3,53,49],'gravel')
    s.garden([20,4,34,8],'azalea');s.garden([20,29,28,37],'bamboo');s.garden([29,31,34,37],'fern')
    with s.as_layer('water'):
        for x in range(21,40):
            for z in range(9,21):s.put((x,1,z),mc('water',level=0))
    for b in([20,8,41,9],[20,21,41,22],[20,9,21,21],[40,9,41,21]):s.surface(b,'stone_bricks')
    s.court('前部水院',[18,8,52,24]);s.court('后部竹庭',[19,28,35,39])
    # Continuous low galleries are built before the room doors reserve their cells.
    s.porch([10,11,18,18],posts=[(10,13),(17,13),(10,17)],roof={'bounds':[9,12,19,19],'eave':8,'axis':'z','style':'hip'})
    s.porch([17,18,21,27],posts=[(17,20),(17,26)],roof={'bounds':[16,18,21,28],'eave':8,'axis':'z','style':'shed'})
    s.porch([15,26,19,49],posts=[(18,28),(18,35),(18,42),(18,48)],roof={'bounds':[15,25,19,50],'eave':8,'axis':'z','style':'shed'})
    s.porch([18,24,42,28],top=8,posts=[(18,24),(25,24),(32,24),(41,24)],roof={'bounds':[17,23,43,29],'eave':8,'axis':'x','style':'hip'})
    s.porch([18,36,35,40],posts=[(19,36),(26,36),(34,36)],roof={'bounds':[17,35,36,40],'eave':8,'axis':'x','style':'shed'})
    s.porch([42,22,47,28],posts=[(46,23),(46,27)],roof={'bounds':[41,22,48,28],'eave':8,'axis':'z','style':'shed'})
    s.slab([15,14,44,18],3,'oak_planks')
    s.porch([41,12,51,23],top=9,posts=[(42,13),(49,13),(42,21),(49,21)],wood='dark_oak',roof={'bounds':[39,10,56,25],'eave':9,'axis':'z','curve_span':17})
    s.room('entry','门厅',[7,4,18,12],doors=[('front',10),('back',10)],windows=[('left',7)],roof={'bounds':[5,2,20,14],'eave':9,'axis':'z','style':'hip'})
    s.room('kitchen','厨房',[4,27,15,39],doors=[('right',30)],windows=[('left',31),('front',8)],roof={'bounds':[2,25,17,41],'eave':9,'axis':'z','style':'hip'})
    s.room('store','储藏与备料',[4,40,15,49],doors=[('right',42)],windows=[('left',43),('back',8)],roof={'bounds':[2,39,17,52],'eave':9,'axis':'x','style':'hip'})
    s.room('living','主厅起居',[18,40,35,50],doors=[('front',25)],windows=[('back',22),('back',30),('right',44)],roof={'bounds':[17,35,35,52],'eave':9,'axis':'x','curve_span':17})
    s.room('work','下层书房与工坊',[41,28,52,47],doors=[('left',30),('front',44)],windows=[('right',33),('back',44)])
    s.room('bedroom','二层卧房',[40,35,51,47],feet=9,doors=[('left',40)],windows=[('right',39),('back',44)],roof={'bounds':[38,33,55,50],'eave':15,'axis':'z','curve_span':17})
    s.roofs.append({'bounds':[40,26,54,35],'eave':9,'axis':'x','style':'shed'})
    s.slab([35,28,39,29],3,'spruce_planks');s.flight(35,29,4,12,base=3,wood='spruce',solid=True,rails=True)
    s.porch([35,41,40,47],feet=9,top=14,posts=[(35,42),(35,46)],roof={'bounds':[34,40,40,49],'eave':14,'axis':'z','style':'shed'})
    s.roofs.append({'bounds':[35,28,40,42],'eave':8,'axis':'x','style':'shed'})
    s.rail('z',41,47,35,9);s.rail('x',35,40,46,9)
    s.box([19,3,27,35,8,28],'yuanxian:courtyard_wall[facing=north,variant=0,part=0,waterlogged=false]')
    s.erase([23,3,27,28,8,28]);s.place((23,3,27),'gate','timber')
    s.flight(10,2,5,2,solid=True)
    s.table(23,3,45);s.bedroom(44,9,43,'cyan');s.kitchen(7,3,33);s.storage(8,3,47,4);s.books(44,3,45)
    with s.as_layer('furniture'):
        for x in range(44,49):s.put((x,3,33),mc('oak_slab',type='double',waterlogged=False))
        s.put((44,4,33),mc('crafting_table'))
    s.table(44,3,18,'oak')
    for x,y,z in[(18,6,25),(31,6,24),(18,5,35),(27,5,39)]:s.lantern(x,y,z)
    s.box([41,14,43,51,15,44],mc('stripped_dark_oak_log',axis='x'));s.lantern(48,13,43)
    s.start=[12,2,1];s.targets={'门厅':[12,3,9],'水上桥廊':[31,3,15],'敞亭':[46,3,20],'竹庭':[31,2,29],
        '主厅':[29,3,43],'厨房':[10,3,34],'储藏':[9,3,44],'书房工坊':[46,3,38],'二层卧房':[46,9,41]}
    s.c['views'].update({'court':{'name':'水院与月洞廊','eye':[33,4.62,15],'target':[25,5.6,29],'fov':68},
        'inner':{'name':'后部竹庭','eye':[31,3.7,30],'target':[29,6.3,43],'fov':66},
        'upper':{'name':'查看二层','eye':[65,41,14],'target':[42,9,39],'cut':13,'floor_min':8,'rooms':True,'fov':49}})
    s.roof_all();return s


def library_bridge():
    s=ComplexStudy(meta('library-bridge','叠台书楼宅','两层主屋与高台书楼隔院错开，上层廊桥跨过两座屋顶之间的空隙。','主屋和书楼采用不同台基高度，前院保留较开阔的视野，走到廊桥下再感受两侧建筑的围合。','廊桥下方和楼梯附近的空间较密集，需要检查院内视线与通行尺度。',[54,52]))
    s.ground();s.surface([3,3,51,48],'gravel');s.surface([22,4,30,24],'stone_bricks')
    s.garden([5,6,16,19],'bamboo');s.garden([31,6,42,15],'azalea');s.garden([43,7,50,16],'fern')
    s.court('入户前院',[17,5,43,23]);s.court('廊桥下的小院',[30,29,37,45])
    s.porch([6,24,31,28],posts=[(6,24),(13,24),(21,24),(30,24)],wood='spruce',roof={'bounds':[4,23,33,28],'eave':8,'axis':'x','style':'shed'})
    s.porch([5,15,18,19],posts=[(5,15),(11,15),(17,15)],wood='spruce',roof={'bounds':[4,14,19,19],'eave':8,'axis':'x','style':'shed'})
    s.porch([17,18,21,28],posts=[(17,19),(17,24)],roof={'bounds':[17,17,21,29],'eave':8,'axis':'z','style':'shed'})
    s.room('kitchen','厨房与备餐',[5,28,17,46],wall='dado',wood='spruce',doors=[('front',9),('right',34)],windows=[('left',32),('back',9)])
    s.room('living','下层起居厅',[17,28,31,46],wall='dado',wood='spruce',doors=[('front',22),('left',34)],windows=[('right',32),('back',22)])
    s.room('bedroom','主屋二层卧房',[7,32,29,46],feet=9,wood='spruce',doors=[('right',35)],windows=[('front',13),('left',36),('back',11),('back',23)],roof={'bounds':[5,30,32,51],'eave':15,'axis':'x','style':'tile'})
    s.roofs.append({'bounds':[4,26,33,32],'eave':9,'axis':'x','style':'shed'})
    s.raised([36,27,51,46],5,'oak')
    s.room('workshop','书楼下层工坊',[37,28,50,44],feet=5,wall='stone_bricks',wood='oak',doors=[('front',41)],windows=[('left',31),('right',31),('back',41)])
    s.room('library','高台书房',[37,28,50,44],feet=11,wood='oak',doors=[('left',35)],windows=[('front',41),('right',31),('back',41)],roof={'bounds':[35,26,53,47],'eave':17,'axis':'z','kit':True})
    # Both floor openings follow the complete 12-tread flight.
    s.erase([46,10,31,49,11,43]);s.flight(46,31,3,12,base=5,wood='oak',rails=False)
    s.rail('z',31,42,45,11,'oak');s.rail('x',45,49,31,11,'oak')
    s.flight(41,22,5,6,base=2,wood='stone_brick',solid=True)
    s.slab([31,35,37,39],11,'spruce_planks')
    # Replace the west end by a four-tread rise, with floor levels 9 -> 11.
    s.erase([29,2,35,35,13,39]);s.slab([29,35,31,39],9,'spruce_planks');s.flight(31,35,4,4,base=9,axis='x',wood='spruce',rails=True)
    s.slab([35,35,37,39],11,'spruce_planks')
    for x,z,h in[(32,34,16),(36,39,17)]:s.box([x,2,z,x+1,h,z+1],mc('stripped_spruce_log',axis='y'))
    s.roofs.extend([{'bounds':[28,34,33,40],'eave':14,'axis':'z','style':'shed'},
        {'bounds':[33,34,38,40],'eave':16,'axis':'z','style':'shed'}])
    # The main house has its own outdoor stair, so the bridge is part of a loop.
    s.slab([30,17,34,32],3,'spruce_planks');s.flight(30,15,4,2,base=2,wood='spruce',solid=True)
    s.flight(30,18,4,12,base=3,wood='spruce',solid=False,rails=True)
    s.porch([29,30,35,35],feet=9,top=14,posts=[(34,30),(34,34)],wood='spruce',roof={'bounds':[29,29,35,35],'eave':14,'axis':'z','style':'shed'})
    s.erase([28,9,31,29,13,34]);s.box([28,13,31,29,14,34],mc('stripped_spruce_log',axis='z'))
    s.flight(23,22,5,2,solid=True)
    s.table(21,3,35,'spruce');s.kitchen(7,3,38);s.storage(9,3,44,4);s.bedroom(12,9,41,'green');s.books(20,9,44,6)
    s.table(41,11,32,'oak');s.books(40,11,42,4);s.kitchen(39,5,38);s.storage(40,5,42,4)
    s.box([7,14,40,29,15,41],mc('stripped_spruce_log',axis='x'));s.lantern(20,13,40)
    s.box([37,16,38,50,17,39],mc('stripped_oak_log',axis='x'));s.lantern(42,15,38)
    s.start=[25,2,4];s.targets={'前院':[25,2,17],'起居厅':[24,3,34],'厨房':[11,3,37],'储藏':[12,3,42],
        '主屋卧房':[18,9,39],'台阶廊桥':[35,11,36],'高台书房':[42,11,37],'书楼工坊':[42,5,36]}
    s.c['views'].update({'court':{'name':'院内看廊桥','eye':[33.5,3.62,42],'target':[32,7.5,31],'fov':65},
        'cut':{'name':'查看下层','eye':[72,61,-9],'target':[28,4,33],'cut':7,'rooms':True,'fov':43},
        'upper':{'name':'查看上层与廊桥','eye':[72,49,1],'target':[29,10,36],'cut':13.5,'floor_min':8,'rooms':True,'fov':44},
        'bridge':{'name':'廊桥人视','eye':[32.5,11.62,36.5],'target':[43,13,37],'fov':68}})
    s.roof_all();return s


def workshop_courts():
    s=ComplexStudy(meta('workshop-courts','曲院作坊宅','铜绿屋顶围出前后不同尺度的院子，后部台地略高，回廊在其间折转。','入口前院较开阔，中院由主屋和回廊围合，后庭进一步收窄，让步行时看到的空间逐段变化。','后庭下行台阶与卧房侧门相邻，需判断转身空间和两侧庭院的比例。',[56,50]))
    wood='mangrove';tile='waxed_oxidized_cut_copper'
    s.roof_palette='copper'
    s.ground();s.surface([3,3,54,48],'gravel')
    s.surface([18,6,28,20],'stone_bricks');s.garden([42,5,52,14],'azalea');s.garden([19,8,25,13],'fern')
    s.court('工作前院',[18,6,42,21]);s.court('起居中院',[24,23,42,35],3);s.court('后部小庭',[5,43,24,49],3)
    s.slab([24,24,42,35],3,'smooth_sandstone');s.garden([27,26,35,32],'azalea',top=3)
    s.raised([24,35,54,48],5,wood,'sandstone')
    s.porch([20,20,43,24],posts=[(21,20),(28,20),(35,20),(42,20)],wood=wood,roof={'bounds':[19,19,44,25],'eave':8,'axis':'x','style':'hip','material':tile})
    s.porch([17,14,21,24],posts=[(20,15),(20,22)],wood=wood,roof={'bounds':[17,13,21,25],'eave':8,'axis':'z','style':'shed','material':tile})
    s.porch([23,24,27,35],posts=[(26,25),(26,31)],wood=wood,roof={'bounds':[23,23,27,35],'eave':8,'axis':'z','style':'shed','material':tile})
    s.porch([39,24,43,35],posts=[(39,25),(39,31)],wood=wood,roof={'bounds':[39,23,43,36],'eave':8,'axis':'z','style':'shed','material':tile})
    s.porch([40,35,43,47],feet=5,top=10,posts=[(41,36),(41,46)],wood=wood,roof={'bounds':[40,34,43,48],'eave':10,'axis':'z','style':'shed','material':tile})
    s.slab([5,42,24,49],3,wood+'_planks');s.garden([6,44,13,48],'fern',top=3)
    s.room('entry','门厅',[28,4,40,13],wall='smooth_sandstone',wood=wood,doors=[('front',31),('back',31)],windows=[('right',7)],roof={'bounds':[26,2,42,15],'eave':9,'axis':'x','style':'hip','material':tile})
    s.porch([28,13,39,20],wood=wood,posts=[(28,14),(38,18)],roof={'bounds':[27,13,40,21],'eave':8,'axis':'z','style':'hip','material':tile})
    s.room('workshop','独立作坊',[4,7,17,23],wall='mud_bricks',wood=wood,doors=[('right',14)],windows=[('front',8),('back',8),('left',13)],roof={'bounds':[2,5,19,25],'eave':9,'axis':'z','style':'tile','material':tile})
    s.room('living','起居厅',[5,27,23,42],wall='smooth_sandstone',wood=wood,doors=[('right',31),('front',12),('back',11)],windows=[('left',32)],roof={'bounds':[3,25,25,45],'eave':9,'axis':'z','style':'tile','material':tile})
    s.room('kitchen','厨房与储物',[43,19,53,34],wall='mud_bricks',wood=wood,doors=[('left',25)],windows=[('right',24),('back',46)],roof={'bounds':[41,17,56,36],'eave':9,'axis':'z','style':'hip','material':tile})
    s.room('bedroom','后台地卧房',[25,36,40,47],feet=5,wall='smooth_sandstone',wood=wood,doors=[('front',30),('left',41),('right',40)],windows=[('back',28),('back',35)],roof={'bounds':[23,34,42,50],'eave':11,'axis':'x','style':'tile','material':tile})
    s.room('study','后台地书房',[43,36,53,47],feet=5,wall='smooth_sandstone',wood=wood,doors=[('left',40)],windows=[('back',46),('right',40)],roof={'bounds':[41,34,56,50],'eave':11,'axis':'z','style':'hip','material':tile})
    s.flight(31,2,5,2,solid=True);s.flight(30,32,5,4,base=3,wood=wood,solid=True)
    s.flight(21,42,3,4,base=3,axis='x',wood=wood,solid=True)
    s.flight(23,24,3,2,base=2,axis='x',wood=wood,solid=True)
    s.table(11,3,34,wood);s.table(47,5,41,wood);s.bedroom(30,5,43,'red');s.books(47,5,45,4)
    s.kitchen(50,3,24);s.storage(47,3,31,4);s.storage(7,3,20,5)
    with s.as_layer('furniture'):
        s.box([7,3,15,12,4,16],mc(wood+'_planks'));s.put((7,4,15),mc('crafting_table'))
    s.start=[33,2,1];s.targets={'门厅':[33,3,10],'工作前院':[23,2,16],'作坊':[12,3,17],'起居厅':[15,3,34],
        '起居中院':[37,3,28],'卧房':[34,5,42],'书房':[48,5,39],'后部小庭':[17,3,45],'厨房':[47,3,26],'储物':[48,3,29]}
    s.c['views'].update({'court':{'name':'中院看后台地','eye':[36,4.6,25],'target':[35,7,39],'fov':69},
        'cut':{'name':'查看房间与台阶','eye':[73,62,-9],'target':[29,3,29],'cut':7.2,'rooms':True,'fov':43},
        'rear':{'name':'后庭与卧房侧门','eye':[15,4.6,46],'target':[31,7,42],'fov':68},
        'terrace':{'name':'后台地人视','eye':[41.5,6.6,38.5],'target':[31,7,42],'fov':67}})
    s.roof_all();return s


BUILDERS=[water_courts,library_bridge,workshop_courts]


def build_all():return[fn()for fn in BUILDERS]


def check(site):
    result=validate(site)
    result.update(courtyards=site.courts,stairs=site.flights,layout_scope='只检查离线预览几何、屋面投影和通路；游戏与玩家视觉未验收')
    return result
