"""逐列地形、方块通路与水工；山基从不展开为三维字典。"""
from __future__ import annotations

from array import array
from collections import OrderedDict, Counter
import gzip
import hashlib
import heapq
import json
import math
from pathlib import Path
import struct
import sys

import json_to_nbt as nbt

AIR='minecraft:air'
NONE=-32768
RECORD=struct.Struct('<IH')


def read_i16(path):
    values=array('h',gzip.decompress(path.read_bytes()))
    if sys.byteorder!='little':values.byteswap()
    return values


def i16_bytes(values):
    values=array('h',values)
    if sys.byteorder!='little':values.byteswap()
    return values.tobytes()


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


class TileSpool:
    """Append-only disk records; only ONE chunk column is resolved in memory."""
    def __init__(self, folder, spec):
        self.folder=folder;folder.mkdir(parents=True,exist_ok=True)
        self.spec=spec;self.handles=OrderedDict();self.states=[];self.state_ids={}
        self.tiles=set();self.signs={};self.writes=0

    def put(self,x,y,z,state):
        w,h,d=self.spec['bounds']['width'],self.spec['bounds']['height'],self.spec['bounds']['depth']
        if not (0<=x<w and 0<=z<d and 0<=y<h):
            raise ValueError(f'正式结构超出边界: {(x,y,z)} {state}')
        state_id=self.state_ids.get(state)
        if state_id is None:
            state_id=len(self.states)
            if state_id>65535:raise ValueError('中州 palette 超出稀疏记录编码')
            self.state_ids[state]=state_id;self.states.append(state)
        tile=(x//16,z//16)
        handle=self.handles.pop(tile,None)
        if handle is None:
            handle=(self.folder/f'{tile[0]}_{tile[1]}.bin').open('ab')
        self.handles[tile]=handle
        if len(self.handles)>self.spec['export']['open_spool_files']:
            _,old=self.handles.popitem(last=False);old.close()
        handle.write(RECORD.pack(y*256+(z%16)*16+x%16,state_id))
        self.tiles.add(tile);self.writes+=1

    def close(self):
        for handle in self.handles.values():handle.close()
        self.handles.clear()

    def export(self,out):
        self.close()
        city_id=self.spec['city_id'];height=self.spec['bounds']['height']
        dest=out/f'data/myvillage/structure/province_capitals/v1/{city_id}'
        dest.mkdir(parents=True,exist_ok=True)
        tiles=[];counts=Counter();max_solid=-1;total=0;peak_column=0
        state_tags={i:nbt.palette_state_to_tag(s) for i,s in enumerate(self.states)}
        for number,(tx,tz) in enumerate(sorted(self.tiles,key=lambda t:(t[1],t[0]))):
            path=self.folder/f'{tx}_{tz}.bin'
            cells={index:state for index,state in RECORD.iter_unpack(path.read_bytes())}
            peak_column=max(peak_column,len(cells))
            palette=sorted(set(cells.values()),key=lambda i:self.states[i])
            ids={state:i for i,state in enumerate(palette)}
            blocks=[]
            for index,state in sorted(cells.items()):
                y,rem=divmod(index,256);z,x=divmod(rem,16)
                tags={'pos':nbt.ListTag(nbt.TAG_INT,[nbt.Int(x),nbt.Int(y),nbt.Int(z)]),'state':nbt.Int(ids[state])}
                sign=self.signs.get((tx*16+x,y,tz*16+z))
                if sign and self.states[state].split('[')[0].endswith('_wall_sign'):
                    messages=[json.dumps({'text':line},ensure_ascii=False) for line in (sign,'中州云山','','')]
                    tags['nbt']=nbt._nbt_value_to_tag('nbt',{'id':'minecraft:sign',
                        'front_text':{'messages':messages,'color':'black','has_glowing_text':False},
                        'back_text':{'messages':['{"text":""}']*4,'color':'black','has_glowing_text':False},'is_waxed':True})
                blocks.append(nbt.Compound(tags))
                name=self.states[state].split('[')[0]
                counts[name]+=1
                if name not in (AIR,'minecraft:water'):max_solid=max(max_solid,y)
            tag=nbt.Compound({'DataVersion':nbt.Int(self.spec['export']['data_version']),
                'author':nbt.String('MyVillage 中州云山正式资源 v1'),
                'size':nbt.ListTag(nbt.TAG_INT,[nbt.Int(16),nbt.Int(height),nbt.Int(16)]),
                'palette':nbt.ListTag(nbt.TAG_COMPOUND,[state_tags[i] for i in palette]),
                'blocks':nbt.ListTag(nbt.TAG_COMPOUND,blocks),'entities':nbt.ListTag(nbt.TAG_COMPOUND,[])})
            name=f'tile_{tx}_{tz}';target=dest/f'{name}.nbt'
            nbt.write_gzipped_nbt(tag,str(target))
            tiles.append({'x':tx*16,'z':tz*16,'template':f'myvillage:province_capitals/v1/{city_id}/{name}',
                          'sha256':sha(target),'blocks':len(cells),'bytes':target.stat().st_size})
            total+=len(cells)
            if number%600==0:print(f'导出稀疏区块列 {number}/{len(self.tiles)}',flush=True)
        # Only stale files owned by this city are removed, after successful export.
        keep={t['template'].rsplit('/',1)[-1]+'.nbt' for t in tiles}
        for old in dest.glob('tile_*.nbt'):
            if old.name not in keep:old.unlink()
        return tiles,{'blocks':total,'explicit_air':counts[AIR],'water_blocks':counts['minecraft:water'],
                      'palette_states':len(self.states),'top_solid_y':max_solid,
                      'largest_resolved_column_blocks':peak_column,'appended_records':self.writes,
                      'block_counts':dict(sorted(counts.items()))}


def raster_segment(a,b):
    """Cardinal supercover: diagonal points never leave a diagonal-only walkway."""
    x,z=round(a[0]),round(a[1]);ex,ez=round(b[0]),round(b[1])
    yield x,z
    dx,dz=abs(ex-x),abs(ez-z);sx=1 if ex>x else -1;sz=1 if ez>z else -1
    ix=iz=0
    while ix<dx or iz<dz:
        if ix<dx and (iz>=dz or (ix+.5)/max(dx,1)<(iz+.5)/max(dz,1)):
            x+=sx;ix+=1
        else:z+=sz;iz+=1
        yield x,z


class World:
    def __init__(self,spec,source,folder,adapt,spool):
        self.spec,self.source,self.adapt,self.spool=spec,source,adapt,spool
        self.w=spec['bounds']['width'];self.d=spec['bounds']['depth']
        self.offset=spec['bounds']['source_offset']
        self.h=read_i16(folder/'terrain.bin.gz');self.water=read_i16(folder/'water.bin.gz')
        self.materials=bytearray(gzip.decompress((folder/'materials.bin.gz').read_bytes()))
        self.owners=array('i',[0])*(self.w*self.d)
        self.roads={};self.routes=[];self.access=[];self.waterworks=[];self.wall_columns=set()
        self.cropped_trees=0;self.omitted_trees=0;self.tree_count=0;self.lamp_count=0

    def index(self,x,z):return z*self.w+x
    def inside(self,x,z):return 0<=x<self.w and 0<=z<self.d
    def local(self,p):return round(p[0]-self.offset[0]),round(p[1]),round(p[2]-self.offset[2])
    def ground(self,x,z):return self.h[self.index(x,z)]

    def reserve(self,row,model,transform):
        for x,z in model.floor_footprint:
            wx,wy,wz=transform((x,0,z))
            if not self.inside(wx,wz):raise ValueError(f'建筑地基超界: {row["id"]}')
            i=self.index(wx,wz)
            if self.owners[i] not in (0,row['ordinal']):
                if self.owners[i]==row.get('shared_foundation_ordinal') and self.h[i]==wy:
                    # A named authored compound may share its exactly aligned
                    # foundation. Every other overlap still aborts the export.
                    continue
                raise ValueError(f'实际地基相交: {row["id"]} / {self.owners[i]} at {(wx,wz)}')
            self.owners[i]=row['ordinal'];self.h[i]=wy;self.water[i]=NONE;self.materials[i]=20

    def add_path(self,route_id,points,width,wood=False,local=False):
        pts=[]
        for p in points:
            x,y,z=p if local else self.local((p[0],self.adapt(p[1]),p[2]))
            # Preserve the .5-block surface instead of truncating every street to full cubes.
            foot2=round((p[1] if local else self.adapt(p[1]))*2)
            pts.append((x,foot2,z))
        center=[];detours=0
        for a,b in zip(pts,pts[1:]):
            chain=list(raster_segment((a[0],a[2]),(b[0],b[2])))
            need=math.ceil(abs(b[1]-a[1])/2)
            if len(chain)-1<need:
                # Explicit switchback geometry, not an impossible vertical path.
                extension=math.ceil((need-len(chain)+1)/2)+2
                if abs(b[0]-a[0])>abs(b[2]-a[2]):
                    via=[(a[0],a[2]),(a[0],a[2]+extension),(b[0],a[2]+extension),(b[0],b[2])]
                else:via=[(a[0],a[2]),(a[0]+extension,a[2]),(a[0]+extension,b[2]),(b[0],b[2])]
                chain=[]
                for u,v in zip(via,via[1:]):chain.extend(list(raster_segment(u,v))[:-1])
                chain.append(via[-1]);detours+=1
            for n,(x,z) in enumerate(chain):
                foot2=round(a[1]+(b[1]-a[1])*n/max(1,len(chain)-1))
                if center and center[-1][:2]==(x,z):center[-1]=(x,z,foot2)
                else:center.append((x,z,foot2))
        radius=max(1,round(width/2)-1)
        emitted=0
        for n,(x,z,foot2) in enumerate(center):
            before=center[max(0,n-1)];after=center[min(len(center)-1,n+1)]
            lower=before if before[2]<foot2 else after if after[2]<foot2 else None
            face=None
            if lower and foot2-lower[2]>=2:
                dx,dz=x-lower[0],z-lower[1]
                face=('east' if dx>0 else 'west') if abs(dx)>abs(dz) else ('south' if dz>0 else 'north')
            for dz in range(-radius,radius+1):
                for dx in range(-radius,radius+1):
                    xx,zz=x+dx,z+dz
                    if not self.inside(xx,zz):continue
                    i=self.index(xx,zz)
                    if self.owners[i] or i in self.wall_columns or (self.water[i]!=NONE and not wood):continue
                    self.roads[i]=(foot2,face,wood,route_id);emitted+=1
        self.routes.append({'id':route_id,'width':radius*2+1,'points':[[x,y/2,z] for x,z,y in center],
                            'switchbacks':detours,'surface':'wood_piers' if wood else 'stair_slab_platform',
                            'status':'generated_not_traversal_verified','paint_operations':emitted})

    def connect(self,row,start):
        """Bounded street access avoiding every building footprint and the real water."""
        sx,sy,sz=start
        if row['source_kind']=='watergate':return
        start_idx=self.index(sx,sz)
        # The landing itself belongs to the building. Search outward, never through rooms.
        nearby=[(i,data) for i,data in self.roads.items()
                if abs(i%self.w-sx)<=64 and abs(i//self.w-sz)<=64]
        if not nearby:raise ValueError(f'没有街路接驳候选: {row["id"]}')
        targets={i:data for i,data in nearby if abs(data[0]/2-sy)<=
                 abs(i%self.w-sx)+abs(i//self.w-sz)+1}
        if not targets:raise ValueError(f'街路高度无法接驳: {row["id"]}')
        nearest=sorted(targets,key=lambda i:abs(i%self.w-sx)+abs(i//self.w-sz)+abs(targets[i][0]/2-sy)*2)[:32]
        goals=set(nearest)
        def heuristic(x,z):return min(abs(x-i%self.w)+abs(z-i//self.w) for i in nearest)
        queue=[(heuristic(sx,sz),0,start_idx)];best={start_idx:0};parent={};found=None
        while queue and len(best)<18000:
            _,cost,i=heapq.heappop(queue)
            if best.get(i)!=cost:continue
            if i in goals:found=i;break
            x,z=i%self.w,i//self.w
            for dx,dz in ((1,0),(-1,0),(0,1),(0,-1)):
                xx,zz=x+dx,z+dz
                if not self.inside(xx,zz) or abs(xx-sx)>72 or abs(zz-sz)>72:continue
                j=self.index(xx,zz)
                bridged_water=j in self.roads and self.roads[j][2]
                if self.owners[j] or (self.water[j]!=NONE and not bridged_water) or j in self.wall_columns:continue
                new=cost+1+min(3,abs(self.h[j]-(sy-1))//8)
                if new<best.get(j,10**9):
                    best[j]=new;parent[j]=i;heapq.heappush(queue,(new+heuristic(xx,zz),new,j))
        if found is None:raise ValueError(f'无法为真实入口建立街路: {row["id"]} at {start}')
        chain=[found]
        while chain[-1]!=start_idx:chain.append(parent[chain[-1]])
        chain.reverse();target=targets[found][0]
        # Use the existing landing at the first cell; add each access as a true route.
        points=[[i%self.w,(sy*2+(target-sy*2)*n/max(1,len(chain)-1))/2,i//self.w] for n,i in enumerate(chain)]
        self.add_path('access_'+row['id'],points,3,local=True)
        self.access.append({'building_id':row['id'],'entry':list(start),
                            'street_target':[found%self.w,target/2,found//self.w],
                            'length':len(chain)-1,'status':'generated_not_traversal_verified'})

    def prepare_water(self):
        freeboard=self.spec['waterworks']['bank_freeboard'];banks={}
        for i,level in enumerate(self.water):
            if level==NONE:continue
            self.h[i]=level-self.spec['waterworks']['depth']
            x,z=i%self.w,i//self.w
            for dx,dz in ((1,0),(-1,0),(0,1),(0,-1)):
                xx,zz=x+dx,z+dz
                if self.inside(xx,zz):
                    j=self.index(xx,zz)
                    if self.water[j]==NONE and not self.owners[j]:banks[j]=max(banks.get(j,-32768),level+freeboard)
        for i,top in banks.items():self.h[i]=max(self.h[i],top);self.materials[i]=10
        self.banks=banks

    def emit_surfaces(self):
        mat={3:'minecraft:stone',4:'minecraft:stone',5:'minecraft:dirt',6:'minecraft:gravel',
             7:'minecraft:stone_bricks',8:'minecraft:stone_bricks',9:'minecraft:bricks',
             10:'minecraft:stone_bricks',11:'minecraft:polished_diorite',13:'minecraft:farmland[moisture=7]',
             14:'minecraft:moss_block',15:'minecraft:stone_bricks',16:'minecraft:coarse_dirt',
             17:'minecraft:stone_bricks',18:'minecraft:sand',20:'minecraft:stone_bricks',
             21:'minecraft:red_terracotta',22:'minecraft:dirt',23:'minecraft:spruce_planks',24:'minecraft:grass_block',
             27:'minecraft:stone_bricks'}
        for z in range(self.d):
            for x in range(self.w):
                i=self.index(x,z);h=self.h[i]
                if self.water[i]!=NONE:
                    self.spool.put(x,h,z,self.spec['materials']['bed'])
                    for y in range(h+1,self.water[i]+1):self.spool.put(x,y,z,'minecraft:water[level=0]')
                elif i not in self.roads and not self.owners[i]:
                    material=mat.get(self.materials[i])
                    if material is None and (h>180 or (x and abs(h-self.h[i-1])>2)):
                        material='minecraft:stone'
                    if material:self.spool.put(x,h,z,material)
        print('河湖实心河床、水块与护岸已生成',flush=True)

    def emit_roads(self):
        for i,(foot2,face,wood,route_id) in self.roads.items():
            x,z=i%self.w,i//self.w
            y=foot2//2-1
            if wood:
                if i%5==0:
                    for support in range(self.h[i]+1,y):self.spool.put(x,support,z,'minecraft:oak_log[axis=y]')
                self.spool.put(x,y,z,'minecraft:spruce_planks')
            else:
                self.h[i]=y
                self.spool.put(x,y,z,'minecraft:stone_bricks')
            if foot2%2:
                y+=1;self.spool.put(x,y,z,'minecraft:stone_brick_slab[type=bottom,waterlogged=false]')
                if not wood:self.h[i]=y
            elif face:
                self.spool.put(x,y,z,f'minecraft:stone_brick_stairs[facing={face},half=bottom,shape=straight,waterlogged=false]')
            for head in range(y+1,y+4):self.spool.put(x,head,z,AIR)

    def emit_falls(self):
        for f in self.source['falls']:
            cx,_,z=self.local((f['x'],0,f['z']))
            top=round(self.adapt(f['top']))-1;bottom=round(self.adapt(f['bottom']))-1
            half=round(f['width']/2)
            for x in range(cx-half,cx+half):
                # A watertight weir behind a falling sheet, tied to the high pool.
                for y in range(bottom-3,top):self.spool.put(x,y,z-1,self.spec['materials']['bank'])
                self.spool.put(x,top,z-1,'minecraft:water[level=0]')
                for zz in range(z,z+self.spec['waterworks']['fall_sheet_depth']):
                    i=self.index(x,zz);self.h[i]=bottom-4;self.water[i]=bottom
                    self.spool.put(x,bottom-4,zz,self.spec['materials']['bank'])
                    for y in range(bottom-3,top+1):self.spool.put(x,y,zz,'minecraft:water[level=8]')
                for y in range(bottom-3,bottom+1):self.spool.put(x,y,z+2,'minecraft:water[level=0]')
                self.h[self.index(x,z+2)]=bottom-4
                self.spool.put(x,bottom-4,z+2,self.spec['materials']['bed'])
            for x in (cx-half-1,cx+half):
                for zz in range(z-1,z+3):
                    i=self.index(x,zz);self.h[i]=max(self.h[i],top+1)
                    for y in range(bottom-4,top+2):self.spool.put(x,y,zz,self.spec['materials']['bank'])
            # The southern cliff has a finite collecting basin instead of an
            # unsupported blue plane or an unbounded leak into the lowlands.
            if f['id']=='south_outlet':
                for x in range(cx-half-1,cx+half+1):
                    for zz in range(z+3,z+12):
                        rim=x in (cx-half-1,cx+half) or zz==z+11
                        self.h[self.index(x,zz)]=bottom+1 if rim else bottom-4
                        self.spool.put(x,bottom-4,zz,self.spec['materials']['bank'])
                        for y in range(bottom-3,bottom+2 if rim else bottom+1):
                            self.spool.put(x,y,zz,self.spec['materials']['bank'] if rim else 'minecraft:water[level=0]')
            self.waterworks.append({**f,'position':[cx,bottom,z],'top_y':top,'bottom_y':bottom,
                                    'original_drop':f['top']-f['bottom'],'adapted_drop':top-bottom,
                                    'construction':'stone_bed_weir_backing_sidewalls_water_sheet',
                                    'fluid_simulation':'not_run'})

    def prepare_walls(self):
        self.openings={}
        for opening in self.spec.get('wall_openings',[]):
            cx,floor,cz=self.local((opening['x'],self.adapt(opening['y'])-1,opening['z']))
            for along in range(-(opening['width']//2),opening['width']//2+1):
                for through in (-1,0,1):
                    x=cx+(along if opening['axis']=='x' else through)
                    z=cz+(through if opening['axis']=='x' else along)
                    self.openings[self.index(x,z)]=(floor,opening['clearance'])
        wallmap={}
        for f in self.source['walls']:
            height=9 if f['kind']==1 else 5
            a=self.local((f['ax'],self.adapt(f['ay']-height)+height-1,f['az']))
            b=self.local((f['bx'],self.adapt(f['by']-height)+height-1,f['bz']))
            line=list(raster_segment((a[0],a[2]),(b[0],b[2])))
            radius=max(1,round(f['hw'])-1)
            for n,(x,z) in enumerate(line):
                top=round(a[1]+(b[1]-a[1])*n/max(1,len(line)-1))
                for dx in range(-radius,radius+1):
                    for dz in range(-radius,radius+1):
                        xx,zz=x+dx,z+dz
                        if not self.inside(xx,zz):continue
                        i=self.index(xx,zz)
                        if self.owners[i]:continue
                        wallmap[i]=(top,height,f['kind'])
        self.wallmap=wallmap
        self.wall_columns=set(wallmap)-set(self.openings)

    def emit_walls(self):
        wallmap=self.wallmap
        for i,(top,height,kind) in wallmap.items():
            x,z=i%self.w,i//self.w
            base=min(self.h[i],top-height)
            opening=self.openings.get(i)
            for y in range(base+1,top+1):
                if opening and opening[0]<y<=opening[0]+opening[1]:
                    self.spool.put(x,y,z,AIR)
                else:self.spool.put(x,y,z,'minecraft:stone_bricks' if kind==1 else 'minecraft:red_terracotta')
            if kind==1:
                edge=any(not self.inside(x+dx,z+dz) or self.index(x+dx,z+dz) not in wallmap
                         for dx,dz in ((1,0),(-1,0),(0,1),(0,-1)))
                if edge:
                    if (x+z)%4<2:self.spool.put(x,top+1,z,'minecraft:stone_brick_wall')
                else:
                    for head in range(top+1,top+4):self.spool.put(x,head,z,AIR)
                    lower=next(((dx,dz) for dx,dz in ((1,0),(-1,0),(0,1),(0,-1))
                                if wallmap.get(self.index(x+dx,z+dz),(top,))[0]==top-1),None)
                    if lower:
                        dx,dz=lower
                        face='west' if dx==1 else 'east' if dx==-1 else 'north' if dz==1 else 'south'
                        self.spool.put(x,top,z,f'minecraft:stone_brick_stairs[facing={face},half=bottom,shape=straight,waterlogged=false]')
            else:self.spool.put(x,top+1,z,'minecraft:sandstone_slab[type=bottom,waterlogged=false]')

    def landscape(self,transform_tree):
        leaf={'pine':'spruce','cypress':'spruce','broad':'oak','blossom':'cherry','maple':'azalea','willow':'mangrove','bamboo':'jungle'}
        for tree in self.source['trees']:
            x,_,z=self.local((tree['x'],0,tree['z']))
            if not self.inside(x,z):self.cropped_trees+=1;continue
            i=self.index(x,z)
            near=False
            for dz in range(-2,3):
                for dx in range(-2,3):
                    if self.inside(x+dx,z+dz):
                        j=self.index(x+dx,z+dz)
                        if self.owners[j] or j in self.roads or j in self.wall_columns or self.water[j]!=NONE:near=True
            if near:self.omitted_trees+=1;continue
            model=self.source['tree_models'][tree['id']];species=model['species'];base=self.h[i]+1
            for ox,oy,oz,wood in model['cells']:
                ox,oy,oz=transform_tree((ox,oy,oz),(4-tree['rot'])%4)
                xx,yy,zz=x+ox,base+oy,z+oz
                if not self.inside(xx,zz) or yy>=320 or self.ground(xx,zz)>=yy:continue
                j=self.index(xx,zz)
                if self.owners[j] or j in self.roads or j in self.wall_columns:continue
                if wood:state='minecraft:spruce_log[axis=y]' if species in ('pine','cypress') else 'minecraft:oak_log[axis=y]'
                else:
                    name=leaf[species]+'_leaves'
                    state=f'minecraft:{name}[persistent=true,distance=7,waterlogged=false]'
                self.spool.put(xx,yy,zz,state)
            self.tree_count+=1
        for lamp in self.source['lamps']:
            x,_,z=self.local((lamp['x'],0,lamp['z']))
            if not self.inside(x,z):continue
            i=self.index(x,z)
            if self.owners[i] or i in self.roads or i in self.wall_columns or self.water[i]!=NONE:continue
            y=self.h[i]+1
            for dy in range(3):self.spool.put(x,y+dy,z,'minecraft:dark_oak_fence')
            self.spool.put(x,y+3,z,'minecraft:lantern[hanging=false,waterlogged=false]');self.lamp_count+=1
