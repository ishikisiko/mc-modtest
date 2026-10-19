"""Reusable semantic building units, expressed on a 1/16-block voxel grid."""
from __future__ import annotations
import math


def geometry(v):
    W,H,D=(n*16 for n in v['size']);m=v['material'];cells={}
    def box(x0,y0,z0,x1,y1,z1,mat=m):
        assert 0<=x0<x1<=W and 0<=y0<y1<=H and 0<=z0<z1<=D,(v['key'],x0,y0,z0,x1,y1,z1)
        for x in range(x0,x1):
            for y in range(y0,y1):
                for z in range(z0,z1):cells[x,y,z]=mat
    def cube():box(0,0,0,W,H,D)
    def slab():box(0,0,0,W,8,D)
    def dado():
        cube();box(0,0,0,W,6,D,'stone')
    def fan():
        box(0,0,6,W,H,10)
        for x in range(6,W-6):
            for y in range(8,H-3):
                d=math.hypot(x+.5-W/2,y-5)
                if 12<d<23 and y-5>abs(x+.5-W/2)*.8:box(x,y,4,x+1,y+1,6,'ridge' if d>20 else 'stone')
    def window():
        cx,cy=(W-1)/2,(H-1)/2;radius=W/2-5
        trim=v['trim_depth'];lattice=v['lattice_depth'];middle=(D-lattice)//2
        def metric(x,y):
            a,b=abs(x-cx),abs(y-cy)
            if v['outline']=='circle':return math.hypot(a,b)
            if v['outline']=='octagon':return max(a,b,(a+b)/1.45)
            return max(a,b)
        for x in range(W):
            for y in range(H):
                d=metric(x,y)
                if d>radius+3:box(x,y,0,x+1,y+1,D,'plaster')
                elif d>=radius:
                    # Wall reveal remains plaster; wood is trim, not a tunnel.
                    box(x,y,0,x+1,y+1,D,'plaster')
                    box(x,y,0,x+1,y+1,trim)
                    box(x,y,D-trim,x+1,y+1,D)
                    box(x,y,middle,x+1,y+1,middle+lattice)
                elif any(abs(t)<.8 for t in [abs(x-cx)-8,abs(y-cy)-8,abs(x-cx)+abs(y-cy)-10]):
                    box(x,y,middle,x+1,y+1,middle+lattice)
    def gate():
        cx=W/2;cy=35.5;radius=35;thick=4 if v['wall'] else 3
        depth=D if v['wall'] else v['frame_depth'];front=(D-depth)//2
        lattice=v.get('lattice_depth',2);middle=(D-lattice)//2
        for x in range(W):
            crest=round(72+7*max(0,1-((x+.5-cx)/38)**2))
            for y in range(H):
                d=math.hypot(x+.5-cx,y+.5-cy)
                if radius<=d<radius+thick:
                    box(x,y,front,x+1,y+1,front+depth)
                elif d>=radius+thick:
                    if v['wall'] and y<crest:
                        material='stone' if y<v['dado_height'] else v['coping_material'] if y>=crest-v['coping_height'] else 'plaster'
                        box(x,y,0,x+1,y+1,D,material)
                    elif not v['wall']:
                        if x<3 or x>=W-3 or y>H-4:box(x,y,front,x+1,y+1,front+depth)
                        elif min(x,W-1-x)%10<2 or y%10<2:box(x,y,middle,x+1,y+1,middle+lattice)
    def transom():
        bar=v['bar_width'];depth=v['frame_depth'];front=(D-depth)//2
        lattice=v['lattice_depth'];middle=(D-lattice)//2
        for x in range(W):
            for y in range(H):
                if x<bar or x>=W-bar or y<bar or y>=H-bar:
                    box(x,y,front,x+1,y+1,front+depth)
                elif abs(x+.5-W/2)<bar/2 or abs(y+.5-H/2)<bar/2:
                    box(x,y,middle,x+1,y+1,middle+lattice)
    def column():
        box(2,0,2,14,4,14,'stone');box(3,4,3,13,6,13,'stone')
        if v['outline']=='square':box(5,6,5,11,H-7,11)
        else:
            for x in range(4,12):
                for z in range(4,12):
                    if math.hypot(x-7.5,z-7.5)<=3.8:box(x,6,z,x+1,H-7,z+1)
        box(3,H-7,3,13,H-4,13);box(1,H-4,2,15,H-2,14);box(0,H-2,1,16,H,15)
    def beam():
        for x in range(W):
            low=round(3+6*math.sin(math.pi*(x+.5)/W)) if v['curved'] else 8
            box(x,low,4,x+1,15,12)
        box(0,12,3,W,16,13)
    def railing():
        box(0,3,6,W,5,10);box(0,13,5,W,16,11)
        for x in [0,23,46]:box(x,0,5,min(W,x+2),16,11)
        for x in range(2,W-2):
            if v['diagonal']:
                y=6+round(5*abs(((min(x,W-1-x)-2)%22)/11-1));box(x,y,7,x+1,min(13,y+2),9)
            elif min(x,W-1-x)%6<2:box(x,5,7,x+1,13,9)
    def roof():
        for x in range(W):
            for z in range(D):
                h={'low':1+z//2,'high':9+z//2,'eave':8+round(4*((15-z)/15)**2),'flat':1}[v['profile']]
                if m=='tile' and x%4 in (0,3):h=min(16,h+1)
                lo=max(0,h-3)
                box(x,lo,z,x+1,h,z+1)
                if lo>0:box(x,0,z,x+1,lo,z+1,'timber')
    def curved_roof():
        # Rounded crest, a quiet valley and restrained upward ends. Both ends
        # share one analytic profile, so mirror and cell seams are exact.
        for x in range(W):
            u=abs((x+.5-W/2)/(W/2))
            top=round(v['base_height']+v['rise']*math.exp(-(u/v['curve_spread'])**2)+v['eave_lift']*u**v['eave_power'])
            for z in range(D):
                roll=1 if z%8 in (2,3,4,5) else 0
                end=v['closure']!='open' and z in (0,D-1)
                hi=top+roll;lo=hi-v['roof_thickness']
                box(x,lo,z,x+1,hi,z+1,'ridge' if end else 'tile')
                box(x,lo-v['fascia_depth'],z,x+1,lo,z+1,'timber')
                if v['closure']=='timber':
                    box(x,0,5,x+1,lo,11,'timber')
                elif v['closure']=='plaster' and v['gable_inset']*16<=x<W-v['gable_inset']*16:
                    box(x,0,0,x+1,lo,D,'plaster')
                elif v['closure']=='screen':
                    # Match the six-pixel frame and two-pixel lattice below.
                    if x%16 in (0,1,14,15):box(x,0,5,x+1,lo,11,'timber')
                    else:
                        for y in range(lo):
                            if y%16 in (0,1,7,8,14,15):box(x,y,7,x+1,y+1,9,'timber')
            if x//16 in v.get('support_walls',[]):
                for z in range(D):
                    roll=1 if z%8 in (2,3,4,5) else 0
                    box(x,0,z,x+1,top+roll-v['roof_thickness']-v['fascia_depth'],z+1,'plaster')
            inset=v['wall_inset']*16;thick=v['wall_thickness']
            side=inset<=x<inset+thick or W-inset-thick<=x<W-inset
            if v['tier'] in ('sides','gable'):
                if side:box(x,0,0,x+1,H,D,'timber')
                elif v['tier']=='gable' and inset<=x<W-inset:
                    box(x,0,5,x+1,H,11,'timber')
            elif v['tier']=='upper' and side:
                box(x,0,0,x+1,top-v['roof_thickness'],D,'timber')
    def ridge():
        box(0,0,0,W,3,D,'tile')
        for x in range(W):
            h=10
            if v['end']:h+=round(15*(abs(x-(W-1)/2)/(W/2))**5)
            box(x,3,6,x+1,h,10)
            box(x,h-2,5,x+1,h,11)
    def lantern():
        box(4,2,4,12,11,12,'lamp')
        for x in [3,11]:
            for z in [3,11]:box(x,1,z,x+2,12,z+2)
        box(2,0,2,14,2,14,'bronze');box(1,11,1,15,13,15,'tile');box(3,13,3,13,14,13,'tile')
        if v['hanging']:box(7,14,7,9,16,9,'bronze')
        else:box(6,14,6,10,15,10,'tile')
    builders={'cube':cube,'slab':slab,'dado':dado,'fan':fan,'window':window,'gate':gate,'transom':transom,'column':column,'beam':beam,'railing':railing,'roof':roof,'curved_roof':curved_roof,'ridge':ridge,'lantern':lantern}
    builders[v['shape']]()
    return cells


def merge(cells):
    left=dict(cells);out=[]
    for x,y,z in sorted(cells):
        if (x,y,z) not in left:continue
        mat=left[x,y,z];a=x+1
        while left.get((a,y,z))==mat:a+=1
        c=z+1
        while all(left.get((i,y,c))==mat for i in range(x,a)):c+=1
        b=y+1
        while all(left.get((i,b,k))==mat for i in range(x,a) for k in range(z,c)):b+=1
        for i in range(x,a):
            for j in range(y,b):
                for k in range(z,c):del left[i,j,k]
        out.append({'from':[x,y,z],'to':[a,b,c],'material':mat})
    return out


def parts(v,cells):
    w,h,d=v['size'];out=[]
    for y in range(h):
        for z in range(d):
            for x in range(w):
                local={(a-x*16,b-y*16,c-z*16):m for (a,b,c),m in cells.items() if a//16==x and b//16==y and c//16==z}
                out.append(local)
    return out
