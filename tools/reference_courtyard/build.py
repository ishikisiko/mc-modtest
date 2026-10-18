#!/usr/bin/env python3
"""Independent reference study: real unit voxels -> VOX, GLB and offline viewer.

Not a settlement generator or Minecraft block palette. Authored geometry lives
here and in model.json; exports are disposable out/ artifacts. No game needed.
"""
from __future__ import annotations

import argparse
import base64
from collections import Counter, defaultdict, deque
import hashlib
import json
import math
from pathlib import Path
import struct
import zipfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]


class Model:
    def __init__(self, config):
        self.c = config
        self.voxels = {}
        self.colors = list(config['palette'])

    def put(self, x, y, z, material, part):
        p = (int(x), int(y), int(z))
        assert all(0 <= p[i] < self.c['size'][i] for i in range(3)), p
        if material is None:
            self.voxels.pop(p, None)
        else:
            self.voxels[p] = (material, part)

    def box(self, bounds, material, part='architecture'):
        x0, y0, z0, x1, y1, z1 = bounds
        for x in range(x0, x1):
            for y in range(y0, y1):
                for z in range(z0, z1):
                    self.put(x, y, z, material, part)

    def paving(self, bounds, wood=False):
        x0, y0, z0, x1, y1, z1 = bounds
        for x in range(x0, x1):
            for z in range(z0, z1):
                if wood:
                    color = 'deck' if (x + z // 16) % 4 else 'timber_light'
                else:
                    color = ['stone', 'stone_light', 'stone'][((x + (z // 5) % 2 * 4) // 8 + z // 5) % 3]
                    if z % 5 == 0 or (x + (z // 5) % 2 * 4) % 8 == 0:
                        color = 'stone_dark'
                self.box([x, y0, z, x+1, y1, z+1], color, 'floor')

    def portal(self, axis, plane, depth, center, radius, thickness, color, part='architecture', clear=True):
        u, cy = center
        for a in range(u-radius-thickness, u+radius+thickness+1):
            for y in range(max(0, cy-radius-thickness), cy+radius+thickness+1):
                d = math.hypot(a+.5-u, y+.5-cy)
                material = color if radius <= d < radius+thickness else None
                if d >= radius+thickness or (d < radius and not clear):
                    continue
                for b in range(plane, plane+depth):
                    self.put(a if axis == 'z' else b, y, b if axis == 'z' else a, material, part)

    def roof(self, spec, axis='z'):
        x0, x1, z0, z1, eave, rise, kick = spec
        center = (x0+x1-1)/2 if axis == 'z' else (z0+z1-1)/2
        half = (x1-x0)/2 if axis == 'z' else (z1-z0)/2
        for x in range(x0, x1):
            for z in range(z0, z1):
                t = abs((x if axis == 'z' else z)-center)/half
                # Broad central crown, low shoulder, gently raised eaves.
                h = round(eave + rise * math.cos(t*math.pi/2)**2 + kick*t**10)
                edge = z in (z0,z1-1) if axis == 'z' else x in (x0,x1-1)
                if edge:
                    h += 1
                run = z if axis == 'z' else x
                color = 'ridge' if edge else ('tile_light' if run%3 == 0 else 'tile')
                self.put(x,h-2,z,'timber','roof')
                self.put(x,h-1,z,'timber_light','roof')
                self.put(x,h,z,color,'roof')
                if not edge and run%3 == 0:
                    self.put(x,h+1,z,color,'roof')
                if abs((x if axis == 'z' else z)-center)<1:
                    self.put(x,h+1,z,'ridge','roof')
        # Curved timber fascia at both gable ends, leaving open space beneath.
        along = range(x0+2,x1-2) if axis == 'z' else range(z0+2,z1-2)
        ends = [z0+1,z1-2] if axis == 'z' else [x0+1,x1-2]
        for a in along:
            t=abs(a-center)/half
            h=round(eave+rise*math.cos(t*math.pi/2)**2+kick*t**10)
            for b in ends:
                for y in range(h-5,h-1):
                    self.put(a if axis=='z' else b,y,b if axis=='z' else a,'timber_light','roof')

    def window(self, axis, plane, center, radius, depth=3):
        u,cy=center
        for a in range(u-radius-1,u+radius+2):
            for y in range(cy-radius-1,cy+radius+2):
                du,dv=abs(a-u),abs(y-cy)
                inside=max(du,dv)<=radius and du+dv<=round(radius*1.5)
                if not inside:
                    continue
                edge=max(du,dv)>=radius-1 or du+dv>=round(radius*1.5)-1
                grille = a in (u-3,u+3) or y in (cy-3,cy+3) or du+dv==3
                for b in range(plane,plane+depth):
                    mat='timber_light' if edge or (grille and b==plane+1) else None
                    self.put(a if axis=='z' else b,y,b if axis=='z' else a,mat,'architecture')

    def frame(self, x0,x1,z0,z1,floor,top,px,pz):
        self.paving([x0,floor-2,z0,x1,floor,z1],True)
        for x in px:
            for z in pz:
                self.box([x-1,floor,z-1,x+3,floor+2,z+3],'stone','architecture')
                self.box([x,floor+2,z,x+2,top,z+2],'timber_light')
                self.box([x-1,top-4,z-1,x+3,top-2,z+3],'timber')
                self.box([x-2,top-2,z-2,x+4,top,z+4],'timber_light')
        for z in pz:
            self.box([x0,top-2,z,x1,top,z+2],'timber')
        for x in px:
            self.box([x,top-2,z0,x+2,top,z1],'timber')

    def build(self):
        c=self.c
        W,_,L=c['size']
        self.box([0,0,0,W,4,L],'stone_dark','ground')
        self.paving([0,4,0,W,10,L])
        pool=c['pool']['bounds']; x0,y0,z0,x1,y1,z1=pool
        self.box([x0,4,z0,x1,10,z1],None)
        self.box([x0,y0,z0,x1,y0+1,z1],'poolbed','ground')
        self.box([x0,y0+1,z0,x1,y1,z1],'water','water')
        for b in [[x0-2,9,z0-2,x1+2,11,z0],[x0-2,9,z1,x1+2,11,z1+2], [x0-2,9,z0,x0,11,z1],[x1,9,z0,x1+2,11,z1]]:
            self.paving(b)
        self.paving(c['bridge']['bounds'],True)
        for x in c['bridge']['pier_x']:
            for z in [34,40]:
                self.box([x,4,z,x+2,10,z+2],'timber','architecture')
        s=c['stairs']; a,b=s['z']
        for z in range(a,b):
            h=round(s['bottom']+(s['top']-s['bottom'])*(z-a)/(b-a-1))
            self.box([s['x'][0],8,z,s['x'][1],h,z+1],'stone_dark','floor')
            self.paving([s['x'][0],h-1,z,s['x'][1],h,z+1],True)
            for x in [s['x'][0],s['x'][1]-1]:
                self.box([x,h+5,z,x+1,h+6,z+1],'timber_light')
                self.box([x,h+2,z,x+1,h+3,z+1],'timber_light')
                if (z-a)%5==0:
                    self.box([x,h,z,x+1,h+7,z+1],'timber')
        for key in ['pavilion','hall']:
            p=c[key]; x0,f,z0,x1,top,z1=p['bounds']
            self.box([x0-1,10,z0-1,x1+1,f-2,z1+1],'stone','floor')
            self.frame(x0,x1,z0,z1,f,top,p['posts_x'],p['posts_z'])
            if key=='hall':
                for b in [[x0,f,z0,x0+2,top,z1],[x1-2,f,z0,x1,top,z1],[x0,f,z1-2,x1,top,z1]]:
                    self.box(b,'plaster')
                # Rear and side octagonal lattice windows, front three open bays.
                for x in [36,57,77]:
                    self.window('z',z1-2,(x,34),7,2)
                self.window('x',x0,(114,34),8,2)
                self.box([x1-2,f,101,x1,34,115],None)
                self.box([x0,f,z0,x1,f+4,z0+2],'timber')
                self.box([48,f,z0,69,f+22,z0+2],None)
            else:
                for x in [x0+2,x1-3]:
                    self.box([x,f+6,z0+5,x+1,f+7,z1-4],'timber_light')
                    self.box([x,f+2,z0+5,x+1,f+3,z1-4],'timber')
                    for z in range(z0+6,z1-4,3):
                        self.box([x,f+2,z,x+1,f+7,z+1],'timber_light')
            self.roof(p['roof'])
        self.paving([48,16,89,70,18,98],True)
        # Side stair physically connects the hall to the lower gallery.
        for x in range(87,98):
            h=max(11,18-(x-87))
            self.paving([x,10,101,x+1,h,115],True)
        w=c['wall']; a,b=w['x']; z0,z1=w['z']; gx,gy,r,t=w['gate']
        for x in range(a,b):
            crest=round(w['crest_height']*max(0,1-((x-gx)/(r+5))**2))
            h=w['top']+crest
            self.box([x,w['bottom'],z0,x+1,h,z1],'plaster_light' if x%9==0 else 'plaster')
            self.box([x,w['bottom'],z0-1,x+1,w['bottom']+4,z1],'stone')
            self.box([x,h,z0-1,x+1,h+1,z1+1],'ridge','roof')
            self.box([x,h+1,z0-1,x+1,h+3,z1+1],'tile','roof')
        self.portal('z',z0-1,z1-z0+2,(gx,gy),r,t,'ridge')
        # Fan-shaped shallow relief in the blank wall panel.
        for x in range(100,119):
            for y in range(22,31):
                d=math.hypot(x-109,y-19)
                if 7<d<12 and y-19>abs(x-109)*.65:
                    self.put(x,y,z0-1,'stone' if d<10 else 'ridge','details')
        for fw in c['firewalls']:
            a,b=fw['z']
            for z in range(a,b):
                t=(z-a)/(b-a-1)
                h=round(fw['base']+fw['height']*(.5-.5*math.cos(t*math.pi*2)))
                self.box([fw['x'][0],fw['bottom'],z,fw['x'][1],h,z+1],'plaster')
                self.box([fw['x'][0]-1,h,z,fw['x'][1]+1,h+2,z+1],'tile','roof')
                self.box([fw['x'][0]-1,h+2,z,fw['x'][1]+1,h+3,z+1],'ridge','roof')
        self.box([89,18,101,91,35,115],None)
        g=c['gallery']; x0,f,z0,x1,top,z1=g['bounds']
        self.paving([x0,f-1,z0,x1,f,z1],True)
        for z in [z0,z1-2]:
            self.box([x0,f,z,x1,top,z+2],'plaster')
            self.box([x0,f,z-1,x1,f+4,z+2],'stone')
        self.box([x1-2,f,z0,x1,top,z1],'plaster')
        self.box([x1-2,f,z0,x1,f+4,z1],'stone')
        self.window('x',x1-2,(108,26),6,2)
        for x in [113,138,161]:
            self.window('z',z0,(x,25),6,2)
        for x in g['frames_x']:
            self.box([x,f,z0,x+2,top,z1],'timber')
            self.portal('x',x,2,(g['center_z'],f+g['radius']),g['radius'],1,'timber_light')
            # Open rectangular lattice in corners surrounding the circular frame.
            for y in range(f+3,top-2):
                for z in range(z0+2,z1-2):
                    if math.hypot(z+.5-g['center_z'],y+.5-f-g['radius'])>g['radius']+1 and y%4 and z%4:
                        self.box([x,y,z,x+2,y+1,z+1],None)
            for z in [z0-1,z1-2]:
                self.box([x-1,f,z,x+2,top,z+2],'timber_light')
        self.roof(g['roof'],'x')
        # Visible repeated pitched crossbeams above the round screens.
        for x in g['frames_x']:
            for z in range(z0,z1):
                t=abs(z-(z0+z1-1)/2)/((z1-z0)/2+4)
                h=round(top+12*math.cos(t*math.pi/2)**2)-4
                self.box([x,h-2,z,x+2,h,z+1],'timber','roof')
        # Side alcove provides the large lateral moon opening in reference 2.
        a=c['alcove']; ax,af,az,bx,at,bz=a['bounds']
        self.paving([ax,af-1,az,bx,af,bz],True)
        for b in [[ax,af,az,ax+2,at,bz],[bx-2,af,az,bx,at,bz],[ax,af,bz-2,bx,at,bz]]:
            self.box(b,'plaster')
        self.box([ax,af,z1-2,bx,at,z1],'timber')
        u,cy,r,t=a['gate']
        self.portal('z',z1-3,4,(u,cy),r,t,'timber_light')
        for x in [121,135]:
            self.window('z',bz-2,(x,25),5,2)
        self.roof(a['roof'])
        # Console and empty vase, kept as block geometry, with no foliage.
        self.box([123,18,135,134,20,139],'timber_light','details')
        for x in [123,132]:
            for z in [135,137]:
                self.box([x,11,z,x+2,18,z+2],'timber','details')
        self.box([127,20,135,131,24,139],'water','details')
        self.box([128,24,136,130,25,138],'ridge','details')
        # Modest stone table and two stools along the pool wall.
        self.box([109,10,69,112,14,72],'stone','details')
        self.box([106,14,67,115,16,74],'stone_light','details')
        for x in [101,119]:
            self.box([x,10,69,x+3,13,72],'stone','details')
        for x,y,z in c['lanterns']:
            self.box([x,y,z,x+3,y+4,z+3],'lamp','details')
            self.box([x-1,y-1,z-1,x+4,y,z+4],'bronze','details')
            self.box([x-1,y+4,z-1,x+4,y+5,z+4],'tile','details')
            self.box([x,y+5,z,x+3,y+6,z+3],'tile','details')
            for dx in [0,2]:
                for dz in [0,2]:
                    self.box([x+dx,y,z+dz,x+dx+1,y+4,z+dz+1],'timber','details')
        return self


def vox_bytes(m):
    def chunk(name,data):
        return name+struct.pack('<II',len(data),0)+data
    W,H,L=m.c['size']
    assert max(W,H,L)<=256
    # MagicaVoxel uses Z-up, our model uses Y-up. Swap y/z explicitly.
    # Reflect authored X so a camera facing +Z sees the pavilion on the left,
    # matching the supplied reference. Apply the same map to mesh and cameras.
    xyzi=struct.pack('<I',len(m.voxels))+bytes(v for (x,y,z),(mat,_) in sorted(m.voxels.items()) for v in (W-1-x,z,y,m.colors.index(mat)+1))
    palette=b''.join(bytes.fromhex(m.c['palette'][k][1:])+b'\xff' for k in m.colors)
    palette+=b'\0'*(1024-len(palette))
    children=chunk(b'SIZE',struct.pack('<iii',W,L,H))+chunk(b'XYZI',xyzi)+chunk(b'RGBA',palette)
    return b'VOX '+struct.pack('<I',150)+b'MAIN'+struct.pack('<II',0,len(children))+children


def mesh(m):
    """Greedy surface quads; voxel occupancy, viewer and GLB share one source."""
    planes=defaultdict(set)
    # Remove hidden faces; retain the solid underside behind transparent water.
    for p,(mat,part) in m.voxels.items():
        for axis in range(3):
            for sign in [-1,1]:
                n=list(p); n[axis]+=sign
                other=m.voxels.get(tuple(n))
                if other is not None and not (other[1]=='water' and part!='water'):
                    continue
                u,v=[i for i in range(3) if i!=axis]
                planes[(part,mat,axis,sign,p[axis]+(sign>0))].add((p[u],p[v]))
    buckets=defaultdict(lambda: {'positions':[],'normals':[],'indices':[]})
    for (part,mat,axis,sign,plane),cells in sorted(planes.items()):
        u,v=[i for i in range(3) if i!=axis]
        data=buckets[(part,mat)]
        for a,b in sorted(cells):
            if (a,b) not in cells:
                continue
            w=1
            while (a+w,b) in cells: w+=1
            h=1
            while all((j,b+h) in cells for j in range(a,a+w)): h+=1
            for j in range(a,a+w):
                for k in range(b,b+h): cells.remove((j,k))
            corners=[]
            for j,k in [(a,b),(a+w,b),(a+w,b+h),(a,b+h)]:
                p=[0,0,0];p[axis]=plane;p[u]=j;p[v]=k;corners.append(p)
            normal=[0,0,0];normal[axis]=sign
            # x cross y = z; x cross z = -y; y cross z = x.
            if (1 if axis!=1 else -1)!=sign:
                corners.reverse()
            base=len(data['positions'])//3
            data['positions'].extend(c for p in corners for c in p)
            data['normals'].extend(normal*4)
            data['indices'].extend([base,base+1,base+2,base,base+2,base+3])
    for b in buckets.values():
        for i in range(0,len(b['positions']),3):
            b['positions'][i]=m.c['size'][0]-b['positions'][i]
            b['normals'][i]=-b['normals'][i]
        for i in range(0,len(b['indices']),3):
            b['indices'][i+1],b['indices'][i+2]=b['indices'][i+2],b['indices'][i+1]
    return buckets


def glb_bytes(m,buckets):
    binary=bytearray(); views=[]; accessors=[]; meshes=[]; nodes=[]; materials=[]
    def buf(values,fmt,kind,component,bounds=False):
        while len(binary)%4: binary.append(0)
        offset=len(binary);binary.extend(struct.pack('<'+fmt*len(values),*values))
        vi=len(views);views.append({'buffer':0,'byteOffset':offset,'byteLength':len(binary)-offset})
        n={'SCALAR':1,'VEC3':3}[kind]
        ac={'bufferView':vi,'componentType':component,'count':len(values)//n,'type':kind}
        if bounds:
            ac.update(min=[min(values[i::n]) for i in range(n)],max=[max(values[i::n]) for i in range(n)])
        accessors.append(ac);return len(accessors)-1
    for name in m.colors:
        rgb=[int(m.c['palette'][name][i:i+2],16)/255 for i in [1,3,5]]
        mat={'name':name,'pbrMetallicRoughness':{'baseColorFactor':rgb+[.72 if name=='water' else 1],'metallicFactor':0,'roughnessFactor':.9}}
        if name=='water':mat['alphaMode']='BLEND';mat['doubleSided']=True
        if name=='lamp':mat['emissiveFactor']=[.7,.38,.08]
        materials.append(mat)
    for (part,mat),b in sorted(buckets.items()):
        p=[v*m.c['voxel_meters'] for v in b['positions']]
        primitive={'attributes':{'POSITION':buf(p,'f','VEC3',5126,True),'NORMAL':buf(b['normals'],'f','VEC3',5126)},'indices':buf(b['indices'],'I','SCALAR',5125),'material':m.colors.index(mat)}
        nodes.append({'name':part+'/'+mat,'mesh':len(meshes)})
        meshes.append({'primitives':[primitive]})
    doc={'asset':{'version':'2.0','generator':'MyVillage reference-courtyard voxel study'},'scene':0,'scenes':[{'nodes':list(range(len(nodes)))}],'nodes':nodes,'meshes':meshes,'materials':materials,'buffers':[{'byteLength':len(binary)}],'bufferViews':views,'accessors':accessors,'extras':{'voxelMeters':m.c['voxel_meters'],'source':'model.json + build.py','minecraftRuntimeVerified':False}}
    j=json.dumps(doc,separators=(',',':')).encode();j+=b' '*((-len(j))%4)
    binary+=b'\0'*((-len(binary))%4)
    total=12+8+len(j)+8+len(binary)
    return struct.pack('<4sII',b'glTF',2,total)+struct.pack('<I4s',len(j),b'JSON')+j+struct.pack('<I4s',len(binary),b'BIN\0')+binary


def validate(m,vox,glb):
    # Parse export payloads, and check meaningful openings and walkable routes.
    assert vox[:8]==b'VOX '+struct.pack('<I',150)
    xyz=vox.index(b'XYZI'); count=struct.unpack_from('<I',vox,xyz+12)[0]
    restored={}
    for off in range(xyz+16,xyz+16+count*4,4):
        x,z,y,c=vox[off:off+4];restored[(m.c['size'][0]-1-x,y,z)]=m.colors[c-1]
    assert restored=={p:v[0] for p,v in m.voxels.items()}
    assert struct.unpack_from('<I',glb,8)[0]==len(glb)
    n=struct.unpack_from('<I',glb,12)[0];doc=json.loads(glb[20:20+n])
    for accessor in doc['accessors']:
        bv=doc['bufferViews'][accessor['bufferView']]
        assert bv.get('byteOffset',0)+bv['byteLength']<=doc['buffers'][0]['byteLength']
    binary=glb[20+n+8:]
    def read_accessor(index):
        ac=doc['accessors'][index];bv=doc['bufferViews'][ac['bufferView']]
        count=ac['count']*{'SCALAR':1,'VEC3':3}[ac['type']]
        fmt={5126:'f',5125:'I'}[ac['componentType']]
        return struct.unpack_from('<'+fmt*count,binary,bv.get('byteOffset',0))
    for mesh_doc in doc['meshes']:
        primitive=mesh_doc['primitives'][0]
        pos=read_accessor(primitive['attributes']['POSITION'])
        normals=read_accessor(primitive['attributes']['NORMAL'])
        indices=read_accessor(primitive['indices'])
        assert max(indices)<len(pos)//3 and len(indices)%3==0
        for i in range(0,len(indices),3):
            a,b,c=(pos[j*3:j*3+3] for j in indices[i:i+3])
            u=[b[k]-a[k] for k in range(3)];v=[c[k]-a[k] for k in range(3)]
            cross=[u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0]]
            normal=normals[indices[i]*3:indices[i]*3+3]
            assert sum(cross[k]*normal[k] for k in range(3))>0,'inverted or degenerate GLB triangle'
    # Center passage must be clear, including two-voxel standing height margin.
    gate=m.c['wall']['gate'];gx,gy,r,_=gate
    for y in range(11,18):
        for z in range(76,81): assert (gx,y,z) not in m.voxels,('gate blocked',gx,y,z)
    gallery_bounds=m.c['gallery']['bounds']
    for x in range(gallery_bounds[0]+2,gallery_bounds[3]-2):
        for y in range(13,20): assert (x,y,108) not in m.voxels,('gallery blocked',x,y)
    # Foot support and 1.75 m clearance, <= .25 m step up/down.
    def floor_at(x,z):
        for y in range(20,8,-1):
            base=m.voxels.get((x,y-1,z))
            if base and base[1]!='water' and all((x,k,z) not in m.voxels for k in range(y,y+7)):
                return y
        return None
    surfaces={(x,z):floor_at(x,z) for x in range(192) for z in range(144)}
    start=(184,37);seen={start};q=deque([start])
    while q:
        x,z=q.popleft();h=surfaces[(x,z)]
        for p in [(x-1,z),(x+1,z),(x,z-1),(x,z+1)]:
            nh=surfaces.get(p)
            if nh is not None and h is not None and abs(nh-h)<=1 and p not in seen:
                seen.add(p);q.append(p)
    targets={'bridge':(85,37),'pavilion':(58,72),'hall':(58,110),'moon_gate':(147,78),'gallery':(155,108),'alcove':(128,130)}
    for name,p in targets.items(): assert p in seen,('unreachable',name,p,surfaces[p])
    return {'vox_roundtrip':True,'glb_buffers':True,'glb_indices_and_winding':True,'clear_portals':True,'walkable_targets':list(targets),'reachable_floor_cells':len(seen),'runtime':'not_verified','owner_appearance':'not_verified'}


def main():
    ap=argparse.ArgumentParser();ap.add_argument('--out',type=Path,default=ROOT/'out/preview/reference-courtyard');args=ap.parse_args()
    c=json.loads((HERE/'model.json').read_text());m=Model(c).build()
    buckets=mesh(m);vox=vox_bytes(m);glb=glb_bytes(m,buckets)
    evidence=validate(m,vox,glb)
    out=args.out;out.mkdir(parents=True,exist_ok=True)
    (out/'courtyard.vox').write_bytes(vox);(out/'courtyard.glb').write_bytes(glb)
    payload={'config':c,'parts':[],'voxel_count':len(m.voxels)}
    for (part,mat),b in sorted(buckets.items()):
        payload['parts'].append({'part':part,'material':mat,'positions':base64.b64encode(struct.pack('<'+'f'*len(b['positions']),*b['positions'])).decode(),'normals':base64.b64encode(struct.pack('<'+'b'*len(b['normals']),*b['normals'])).decode(),'indices':base64.b64encode(struct.pack('<'+'I'*len(b['indices']),*b['indices'])).decode()})
    template=(HERE/'viewer.html').read_text()
    html=template.replace('/*DATA*/',json.dumps(payload,separators=(',',':'),ensure_ascii=False)).replace('/*THREE*/',(ROOT/'tools/web/three.min.js').read_text()).replace('/*ORBIT*/',(ROOT/'tools/web/OrbitControls.js').read_text())
    (out/'index.html').write_text(html)
    with zipfile.ZipFile(out/'source.zip','w',compression=zipfile.ZIP_DEFLATED) as archive:
        for p in sorted([*HERE.glob('*.py'),*HERE.glob('*.json'),*HERE.glob('*.html'),*HERE.glob('*.md'),*HERE.glob('*.mjs'),ROOT/'tools/web/three.min.js',ROOT/'tools/web/OrbitControls.js']):
            info=zipfile.ZipInfo(str(p.relative_to(ROOT)),(2026,10,19,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
            archive.writestr(info,p.read_bytes())
    report={'name':c['name'],'size_voxels':c['size'],'voxel_meters':c['voxel_meters'],'occupied_voxels':len(m.voxels),'mesh_quads':sum(len(b['indices'])//6 for b in buckets.values()),'parts':dict(Counter(v[1] for v in m.voxels.values())),'checks':evidence,'artifacts':{name:hashlib.sha256((out/name).read_bytes()).hexdigest() for name in ['courtyard.vox','courtyard.glb','index.html','source.zip']}}
    (out/'manifest.json').write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n')
    print(json.dumps(report,indent=2,ensure_ascii=False))


if __name__=='__main__':main()
