"""Courtyard composition built from a small reusable architectural kit."""
from __future__ import annotations
import math


DIRECTIONS=['north','east','south','west']


def offset(x,y,z,turn):
    for _ in range(turn%4):x,z=-z,x
    return x,y,z


class Site:
    def __init__(self,config):
        self.c=config;self.families={f['id']:f for f in config['families']}
        self.blocks={};self.owners={};self.assemblies=[]

    def put(self,p,state):
        assert all(0<=p[i]<self.c['size'][i] for i in range(3)),p
        assert p not in self.owners,('would split whole item',p,self.owners.get(p),state)
        self.blocks[p]=state

    def basic(self,p,family,variant):
        self.place(p,family,variant)

    def place(self,root,family,key,facing='north'):
        family='courtyard_'+family if not family.startswith('courtyard_') else family
        spec=self.families[family];vi=next(i for i,v in enumerate(spec['variants']) if v['key']==key)
        variant=spec['variants'][vi];w,h,d=variant['size'];turn=DIRECTIONS.index(facing)
        count=w*h*d;max_parts=max(math.prod(v['size']) for v in spec['variants'])
        parts=[]
        for y in range(h):
            for z in range(d):
                for x in range(w):
                    delta=offset(x,y,z,turn);p=tuple(root[i]+delta[i] for i in range(3));part=(y*d+z)*w+x
                    state=f'myvillage:{family}[facing={facing},variant={vi},waterlogged=false'+(f',part={part}' if max_parts>1 else '')+']'
                    self.put(p,state);parts.append(p)
        if count>1:
            identity=len(self.assemblies)
            for p in parts:self.owners[p]=identity
            self.assemblies.append({'root':list(root),'family':family,'variant':vi,'facing':facing,'parts':[list(p) for p in parts]})

    def fill(self,bounds,family,key):
        x0,y0,z0,x1,y1,z1=bounds
        for x in range(x0,x1):
            for y in range(y0,y1):
                for z in range(z0,z1):self.basic((x,y,z),family,key)

    def roof(self,x0,x1,z0,z1,eave,axis='z'):
        cross=range(x0,x1) if axis=='z' else range(z0,z1)
        low,high=min(cross),max(cross);center=(low+high)//2
        # Odd spans with an even half-span meet on a low central cap.
        # The ridge shares that cap's cell height instead of floating above it.
        assert (high-low)%4==0,(low,high)
        peak=eave+(center-low)//2
        for x in range(x0,x1):
            for z in range(z0,z1):
                a=x if axis=='z' else z;n=min(a-low,high-a)
                edge=z in (z0,z1-1) if axis=='z' else x in (x0,x1-1)
                profile='eave' if n==0 else ('flat' if a==center else ('low' if n%2==0 else 'high'))
                y=eave+n//2
                # Slopes rise toward the centre. Local +Z is the rising direction.
                facing=('west' if a<center else 'east') if axis=='z' else ('north' if a<center else 'south')
                self.place((x,y,z),'roof',('stone_' if edge else '')+profile,facing)
        return center,peak


def build_scene(config):
    s=Site(config);W,H,L=config['size']
    s.fill([0,0,0,W,2,L],'floor','stone')
    s.fill([0,2,0,W,3,L],'floor','stone_slab')
    # A genuine, fully sealed source-water pool.
    for x in range(4,44):
        for z in range(4,17):
            s.put((x,1,z),'minecraft:water[level=0]');s.put((x,2,z),'minecraft:air')
    s.fill([3,2,9,35,3,12],'floor','wood_slab')
    for x in [7,15,23,33]:
        for z in [9,11]:s.put((x,1,z),'minecraft:spruce_fence[east=false,north=false,south=false,west=false,waterlogged=true]')
    # Six half-block steps lead onto the raised entrance pavilion.
    for i,z in enumerate(range(12,17)):
        y=2+(i+1)//2;key='wood' if i%2==0 else 'wood_slab'
        if y>2:s.fill([31,2,z,38,y,z+1],'floor','stone')
        s.fill([31,y,z,38,y+1,z+1],'floor',key)
    s.fill([29,2,17,40,4,25],'floor','stone')
    s.fill([29,4,17,40,5,25],'floor','wood')
    # Main hall and its open three-bay front.
    s.fill([24,2,25,45,4,38],'floor','stone')
    s.fill([24,4,25,45,5,38],'floor','wood')
    s.fill([24,5,26,25,11,38],'wall','plaster')
    s.fill([44,5,26,45,11,38],'wall','plaster')
    s.fill([24,5,37,45,11,38],'wall','plaster')
    # Gallery and its side room.
    s.fill([3,2,25,24,3,33],'floor','wood')
    s.fill([11,2,32,19,3,39],'floor','wood')
    for z in [25,32]:s.fill([3,3,z,24,8,z+1],'wall','plaster')
    s.fill([3,3,25,4,8,33],'wall','plaster')
    for x in [11,18]:s.fill([x,3,33,x+1,8,39],'wall','plaster')
    s.fill([11,3,38,19,8,39],'wall','plaster')
    # Bank wall, stone base and shallow coping.
    s.fill([3,2,18,29,3,25],'floor','stone')
    s.fill([3,3,20,29,7,21],'wall','plaster')
    s.fill([3,3,20,29,4,21],'wall','dado')
    s.fill([3,7,20,29,8,21],'floor','stone_slab')
    # Side stair between the lower gallery and the raised hall doorway.
    for x,y,key in [(21,3,'wood_slab'),(22,3,'wood'),(23,4,'wood_slab')]:
        s.fill([x,3,27,x+1,y,32],'floor','stone') if y>3 else None
        s.fill([x,y,27,x+1,y+1,32],'floor',key)
    # Volumes first, then whole architectural assemblies: none may be sliced by later writes.
    roof_ridges=[]
    for args in [(26,43,16,26,10,'z'),(22,47,24,40,11,'z'),(2,25,24,33,9,'x'),(10,19,32,40,9,'z')]:
        center,peak=s.roof(*args);roof_ridges.append((args,center,peak))
    # Tall gently scalloped firewalls are laid with the same wall units.
    for x in [24,44]:
        for z in range(27,39):
            height=15+round(4*math.sin(math.pi*(z-27)/11)**2)
            s.fill([x,11,z,x+1,height,z+1],'wall','plaster')
            s.place((x,height,z),'floor','stone_slab')
    # Gable timber infill, clear below the beam level.
    for x in range(29,40):
        h=10+min(x-26,42-x)//2
        if h>10:s.fill([x,10,17,x+1,h,18],'floor','wood')
    for x in range(25,45):
        h=11+min(x-22,46-x)//2
        if h>10:s.fill([x,10,25,x+1,h,26],'floor','wood')
    # End walls meet the stepped underside rather than leaving open gables.
    for x in [3,23]:
        for z in range(25,33):
            h=9+min(z-24,32-z)//2
            s.fill([x,8,z,x+1,h,z+1],'floor','wood')
    for x in range(11,19):
        h=9+min(x-10,18-x)//2
        s.fill([x,8,38,x+1,h,39],'floor','wood')
    # Interior moon screens terminate in timber heads that meet the roof.
    # These sit above whole gate footprints, never inside their owned cells.
    for x in [6,12,18]:
        for z in range(26,31):
            h=9+min(z-24,32-z)//2
            s.fill([x,8,z,x+1,h,z+1],'floor','wood')
    # Structural posts and paired curved/straight beams.
    for x in [30,38]:
        for z in [18,23]:s.place((x,5,z),'column','square')
    for z in [18,23]:
        for x in [30,33,36]:s.place((x,9,z),'beam','cloud' if z==18 else 'straight')
    for x in [30,38]:s.place((x,9,19),'beam','straight','east')
    for x in [29,39]:
        for z in [18,21]:s.place((x,5,z),'railing','diamond','east')
    for x in [26,32,38,42]:
        for z in [26,35]:s.place((x,5,z),'column','round')
    for z in [26,35]:
        for x in [26,29,32,35,38,41]:s.place((x,9,z),'beam','cloud')
    for z in [25,32]:
        # Half-height decorative beams left daylight above the full wall.
        s.fill([3,8,z,24,9,z+1],'floor','wood')
    # Whole windows and whole doors, intentionally not a copy of voxel fragments.
    s.place((8,3,20),'gate','stone')
    s.place((17,4,20),'wall','fan')
    for x in [6,12,18]:s.place((x,3,26),'gate','timber','east')
    s.place((24,5,27),'gate','timber','east')
    for x,key in [(8,'octagonal'),(15,'round'),(21,'square')]:s.place((x,4,25),'window',key)
    s.place((3,4,27),'window','octagonal','east')
    s.place((12,3,32),'gate','timber')
    for x in [12,15]:s.place((x,4,38),'window','octagonal')
    for x in [27,38]:s.place((x,7,37),'window','octagonal')
    s.place((44,7,29),'window','round','east')
    # Ridge units sit above the completed roof surfaces, never inside another assembly.
    for (args,center,peak) in roof_ridges:
        x0,x1,z0,z1,eave,axis=args
        if axis=='z':
            for z in range(z0+1,z1-2,3):s.place((center,peak,z),'ridge','straight','east')
        else:
            for x in range(x0+1,x1-2,3):s.place((x,peak,center),'ridge','straight')
    for p in [(30,8,17),(38,8,17),(30,6,17),(38,6,17),(6,3,10),(33,3,10)]:s.place(p,'lantern','hanging' if p[2]==17 else 'standing')
    # Console: ordinary vanilla furniture keeps the kit focused on architecture.
    for x in [13,15]:s.put((x,3,36),'minecraft:dark_oak_fence[east=false,north=false,south=false,west=false,waterlogged=false]')
    for x in range(13,16):s.put((x,4,36),'minecraft:dark_oak_slab[type=bottom,waterlogged=false]')
    return s
