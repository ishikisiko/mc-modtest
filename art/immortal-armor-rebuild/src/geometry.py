"""Authored continuous-surface geometry. Coordinates: metres, +Y up, +Z front.
No image assets, external models, or image generation are used.
"""
import math, json, struct, zlib, base64
from pathlib import Path
import numpy as np
TAU=math.tau

def catmull(points, steps=6):
    a=np.asarray(points,dtype=float); out=[]
    for i in range(len(a)-1):
        p0=a[max(0,i-1)];p1=a[i];p2=a[i+1];p3=a[min(len(a)-1,i+2)]
        for j in range(steps):
            t=j/steps
            out.append(.5*((2*p1)+(-p0+p2)*t+(2*p0-5*p1+4*p2-p3)*t*t+(-p0+3*p1-3*p2+p3)*t*t*t))
    return np.array(out+[a[-1]])

def normals(v,f):
    n=np.zeros_like(v); q=np.cross(v[f[:,1]]-v[f[:,0]],v[f[:,2]]-v[f[:,0]])
    for k in range(3):np.add.at(n,f[:,k],q)
    lengths=np.linalg.norm(n,axis=1); good=lengths>1e-11;n[good]/=lengths[good,None]; n[~good]=[0,1,0]
    return n

class Builder:
    def __init__(self, mats):self.meshes=[];self.mats=mats
    def add(self,name,slot,material,v,f,uv=None,**extra):
        v=np.round(np.asarray(v,float),6);f=np.asarray(f,int)
        # Remove numerical degeneracy at swept ends; retain every authored surface.
        areas=np.linalg.norm(np.cross(v[f[:,1]]-v[f[:,0]],v[f[:,2]]-v[f[:,0]]),axis=1)
        f=f[areas>1e-11]
        assert np.isfinite(v).all() and len(f)>0,(name,'invalid mesh')
        d=dict(name=name,slot=slot,material=material,positions=np.round(v,6).ravel().tolist(),normals=np.round(normals(v,f),5).ravel().tolist(),indices=f.ravel().tolist())
        if uv is not None:d['uvs']=np.round(np.asarray(uv),5).ravel().tolist()
        d.update(extra);self.meshes.append(d);return d
    def loft(self,name,slot,mat,rings,segments=48,thickness=.006,arc=(0,TAU),fold=0,folds=8,power=2,hem=0,steps=4):
        # rings=(height, x radius, z radius, x centre, z centre). One connected shell.
        rings=catmull(rings,steps);closed=abs(arc[1]-arc[0]-TAU)<1e-4
        count=segments if closed else segments+1; v=[];uv=[];f=[]
        def sp(x,e):return math.copysign(abs(x)**e,x)
        for side in range(2):
            for j,(y,rx,rz,cx,cz) in enumerate(rings):
                u=j/(len(rings)-1)
                for k in range(count):
                    a=arc[0]+(arc[1]-arc[0])*k/segments
                    wave=fold*math.cos(folds*a)*(1-u*.7)
                    rr=thickness*side
                    xx=cx+(max(.002,rx-rr)+wave)*sp(math.sin(a),2/power)
                    zz=cz+(max(.002,rz-rr)+wave*.6)*sp(math.cos(a),2/power)
                    yy=y-hem*(1-u)**3*(.5-.5*math.cos(a))
                    v.append([xx,yy,zz]);uv.append([k/segments,u])
        M=len(rings)*count
        for side in range(2):
            off=side*M
            for j in range(len(rings)-1):
                for k in range(segments):
                    k2=(k+1)%count;a=off+j*count+k;b=off+j*count+k2;c=off+(j+1)*count+k2;d=off+(j+1)*count+k
                    f.extend([[a,b,c],[a,c,d]] if side==0 else [[a,c,b],[a,d,c]])
        for j in [0,len(rings)-1]:
            for k in range(segments):
                a=j*count+k;b=j*count+(k+1)%count
                f.extend([[a,a+M,b+M],[a,b+M,b]] if j==0 else [[a,b+M,a+M],[a,b,b+M]])
        if not closed:
            for j in range(len(rings)-1):
                for k in [0,count-1]:
                    a=j*count+k;b=(j+1)*count+k;f.extend([[a,b,a+M],[b,b+M,a+M]])
        return self.add(name,slot,mat,v,f,uv)
    def ellipsoid(self,name,slot,mat,center,scale,seg=32,rows=18):
        v=[];uv=[];f=[]
        for j in range(rows+1):
            a=.0001+(math.pi-.0002)*j/rows
            for k in range(seg):
                t=TAU*k/seg
                v.append([center[0]+scale[0]*math.sin(a)*math.sin(t),center[1]+scale[1]*math.cos(a),center[2]+scale[2]*math.sin(a)*math.cos(t)]);uv.append([k/seg,j/rows])
        for j in range(rows):
            for k in range(seg):
                a=j*seg+k;b=j*seg+(k+1)%seg;c=b+seg;d=a+seg;f.extend([[a,c,b],[a,d,c]])
        return self.add(name,slot,mat,v,f,uv)
    def tube(self,name,slot,mat,points,radius=.004,segments=8,steps=5):
        p=catmull(points,steps);v=[];f=[];uv=[]
        for j,c in enumerate(p):
            t=p[min(len(p)-1,j+1)]-p[max(0,j-1)];t/=max(1e-8,np.linalg.norm(t))
            ref=np.array([0,0,1.]) if abs(t[2])<.85 else np.array([0,1.,0])
            x=np.cross(t,ref);x/=max(1e-8,np.linalg.norm(x));y=np.cross(t,x)
            r=radius if isinstance(radius,(int,float)) else np.interp(j/(len(p)-1),np.linspace(0,1,len(radius)),radius)
            for k in range(segments):
                a=TAU*k/segments;v.append(c+r*(math.cos(a)*x+math.sin(a)*y));uv.append([k/segments,j/(len(p)-1)])
        for j in range(len(p)-1):
            for k in range(segments):
                a=j*segments+k;b=j*segments+(k+1)%segments;c=b+segments;d=a+segments;f.extend([[a,b,c],[a,c,d]])
        for idx,rev in [(0,True),(len(p)-1,False)]:
            cidx=len(v);v.append(p[idx]);uv.append([.5,.5])
            for k in range(segments):
                a=idx*segments+k;b=idx*segments+(k+1)%segments;f.append([cidx,a,b] if rev else [cidx,b,a])
        return self.add(name,slot,mat,v,f,uv)
    def ribbon(self,name,slot,mat,points,widths,thickness=.005,normal=(0,0,1),steps=8,bulge=.008):
        p=catmull(points,steps);w=np.interp(np.linspace(0,1,len(p)),np.linspace(0,1,len(widths)),widths)
        v=[];uv=[];f=[];cols=6;n=np.array(normal,float);n/=np.linalg.norm(n)
        for side in range(2):
            for j,c in enumerate(p):
                t=p[min(len(p)-1,j+1)]-p[max(0,j-1)];t/=max(1e-8,np.linalg.norm(t))
                lat=np.cross(t,n);lat/=max(1e-8,np.linalg.norm(lat))
                nn=np.cross(lat,t);nn/=max(1e-8,np.linalg.norm(nn))
                for k in range(cols+1):
                    u=k/cols*2-1
                    v.append(c+lat*u*w[j]*.5+nn*(bulge*(1-u*u)+(-.5 if side else .5)*thickness));uv.append([k/cols,j/(len(p)-1)])
        stride=cols+1;M=stride*len(p)
        for side in range(2):
            for j in range(len(p)-1):
                for k in range(cols):
                    a=side*M+j*stride+k;b=a+1;c=b+stride;d=a+stride;f.extend([[a,b,c],[a,c,d]] if side==0 else [[a,c,b],[a,d,c]])
        for j in range(len(p)-1):
            for k in [0,cols]:
                a=j*stride+k;b=a+stride;f.extend([[a,b,a+M],[b,b+M,a+M]])
        for j in [0,len(p)-1]:
            for k in range(cols):
                a=j*stride+k;b=a+1;f.extend([[a,a+M,b],[b,a+M,b+M]])
        return self.add(name,slot,mat,v,f,uv)
    def disc(self,name,slot,mat,center,rx,ry,start=0,end=TAU,tube=.008,tilt=0):
        pts=[]
        for a in np.linspace(start,end,65):
            x=rx*math.cos(a);y=ry*math.sin(a)
            pts.append([center[0]+x*math.cos(tilt),center[1]+y,center[2]+x*math.sin(tilt)])
        return self.tube(name,slot,mat,pts,tube,10,1)
    def gem(self,name,slot,mat,center,size,shape='diamond'):
        sx,sy,sz=size;x,y,z=center
        v=[[x,y+sy,z],[x+sx,y,z],[x,y-sy,z],[x-sx,y,z],[x,y,z+sz],[x,y,z-sz]]
        f=[[0,1,4],[1,2,4],[2,3,4],[3,0,4],[1,0,5],[2,1,5],[3,2,5],[0,3,5]]
        # Duplicate vertices per triangle for polished facet normals.
        vv=[];ff=[]
        for tri in f:ff.append(list(range(len(vv),len(vv)+3)));vv.extend([v[i] for i in tri])
        return self.add(name,slot,mat,vv,ff)
    def transform_last(self,start,func):
        for m in self.meshes[start:]:
            v=np.array(m['positions']).reshape(-1,3);v=np.array([func(p) for p in v]);f=np.array(m['indices']).reshape(-1,3)
            m['positions']=np.round(v,6).ravel().tolist();m['normals']=np.round(normals(v,f),5).ravel().tolist()

def save_glb(meshes,materials,path):
    """glTF 2.0 binary, portable mesh geometry and metallic/roughness materials."""
    blob=bytearray();views=[];access=[];gmeshes=[];nodes=[]
    def buf(arr,component,atype,target=None):
        while len(blob)%4:blob.append(0)
        arr=np.ascontiguousarray(arr);offset=len(blob);raw=arr.tobytes();blob.extend(raw)
        vw=dict(buffer=0,byteOffset=offset,byteLength=len(raw))
        if target:vw['target']=target
        vi=len(views);views.append(vw);a=dict(bufferView=vi,componentType=component,count=len(arr),type=atype)
        if atype=='VEC3':a.update(min=arr.min(axis=0).astype(float).tolist(),max=arr.max(axis=0).astype(float).tolist())
        ai=len(access);access.append(a);return ai
    matkeys=list(materials);mats=[]
    for k in matkeys:
        m=materials[k];c=[int(m['color'][i:i+2],16)/255 for i in [1,3,5]]
        gm=dict(name=m['name'],pbrMetallicRoughness=dict(baseColorFactor=c+[m.get('alpha',1)],metallicFactor=m.get('metal',0),roughnessFactor=m.get('rough',.5)),doubleSided=True)
        if m.get('emission',0):gm['emissiveFactor']=[v*m['emission'] for v in c]
        if m.get('alpha',1)<1:gm['alphaMode']='BLEND'
        mats.append(gm)
    for m in meshes:
        v=np.asarray(m['positions'],np.float32).reshape(-1,3);n=np.asarray(m['normals'],np.float32).reshape(-1,3);f=np.asarray(m['indices'],np.uint32)
        at=dict(POSITION=buf(v,5126,'VEC3',34962),NORMAL=buf(n,5126,'VEC3',34962))
        if m.get('uvs'):at['TEXCOORD_0']=buf(np.asarray(m['uvs'],np.float32).reshape(-1,2),5126,'VEC2',34962)
        mi=len(gmeshes);gmeshes.append(dict(name=m['name'],primitives=[dict(attributes=at,indices=buf(f,5125,'SCALAR',34963),material=matkeys.index(m['material']))]))
        nodes.append(dict(name=m['name'],mesh=mi,extras=dict(slot=m['slot'])))
    gltf=dict(asset=dict(version='2.0',generator='Xianzhu authored geometry 2026-10-28'),scene=0,scenes=[dict(nodes=list(range(len(nodes))))],nodes=nodes,meshes=gmeshes,materials=mats,accessors=access,bufferViews=views,buffers=[dict(byteLength=len(blob))])
    j=json.dumps(gltf,ensure_ascii=False,separators=(',',':')).encode();j+=b' '*((-len(j))%4);blob+=b'\0'*((-len(blob))%4)
    data=struct.pack('<III',0x46546c67,2,12+8+len(j)+8+len(blob))+struct.pack('<II',len(j),0x4E4F534A)+j+struct.pack('<II',len(blob),0x004E4942)+blob
    Path(path).write_bytes(data)

def save_obj(meshes,materials,path):
    path=Path(path);lines=['# Authored mesh; +Y up; metres',f'mtllib {path.stem}.mtl'];offset=1
    for m in meshes:
        lines += [f'o {m["name"].replace(" ","_")}',f'usemtl {m["material"]}']
        v=np.asarray(m['positions']).reshape(-1,3);n=np.asarray(m['normals']).reshape(-1,3)
        lines.extend('v %.6f %.6f %.6f'%tuple(p) for p in v);lines.extend('vn %.5f %.5f %.5f'%tuple(p) for p in n)
        for a,b,c in np.asarray(m['indices']).reshape(-1,3)+offset:lines.append(f'f {a}//{a} {b}//{b} {c}//{c}')
        offset+=len(v)
    path.write_text('\n'.join(lines),encoding='utf-8');ml=[]
    for k,m in materials.items():
        c=[int(m['color'][i:i+2],16)/255 for i in [1,3,5]]
        ml.extend([f'newmtl {k}','Kd '+' '.join(map(str,c)),f'Pr {m.get("rough",.5)}',f'Pm {m.get("metal",0)}',f'd {m.get("alpha",1)}',''])
    path.with_suffix('.mtl').write_text('\n'.join(ml),encoding='utf-8')
