"""中州千机阁已确认的建筑作者输入：各院、主阁与细部同源构造。"""
from __future__ import annotations
import json, math, hashlib, shutil, random
from pathlib import Path

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[1]
SPEC=json.loads((ROOT/'blueprints/qianji/headquarters_design.json').read_text())
PALETTE=json.loads((ROOT/'blueprints/qianji/headquarters_materials.json').read_text())['palette']

class Scene:
 def __init__(self):
  self.boxes=[];self.meshes=[];self.signs=[];self.lamps=[];self.maps=[];self.bookcases=[];self.part='ground'
  self.bounds={};self.counts={};self.floor_targets=[];self.tree_sites=[]
 def box(self,x,y,z,w,h,d,mat='stone',layer='body',yaw=0):
  if min(w,h,d)<=0:return
  a=math.radians(yaw)/2
  self.boxes.append([round(x+w/2,3),round(y+h/2,3),round(z+d/2,3),round(w,3),round(h,3),round(d,3),mat,self.part,layer,0,round(math.sin(a),5),0,round(math.cos(a),5)])
 def beam(self,a,b,w=.4,d=None,mat='wood_dark',layer='detail'):
  d=w if d is None else d;v=[b[i]-a[i] for i in range(3)];length=math.sqrt(sum(x*x for x in v))
  if length<.01:return
  vx,vy,vz=[x/length for x in v];q=[vz,0,-vx,1+vy];n=math.sqrt(sum(x*x for x in q));q=[x/n for x in q] if n>.00001 else [1,0,0,0]
  self.boxes.append([round((a[i]+b[i])/2,3) for i in range(3)]+[w,round(length,3),d,mat,self.part,layer]+[round(x,5) for x in q])
 def prism(self,cx,y,cz,r,h,mat='stone',sides=8,layer='detail',angle=22.5):
  vertices=[];indices=[]
  for yy in [y,y+h]:
   for i in range(sides):
    a=math.radians(angle+i*360/sides);vertices.extend([cx+math.cos(a)*r,yy,cz+math.sin(a)*r])
  for i in range(sides):
   j=(i+1)%sides;indices.extend([i,j,j+sides,i,j+sides,i+sides])
  for i in range(1,sides-1):indices.extend([0,i+1,i,sides,sides+i,sides+i+1])
  indices=[v for i in range(0,len(indices),3) for v in [indices[i],indices[i+2],indices[i+1]]]
  self.meshes.append(dict(vertices=[round(v,3) for v in vertices],indices=indices,material=mat,part=self.part,layer=layer))
 def floor(self,x,y,z,w,d,mat='paving',layer='floor'):
  self.box(x,y-.45,z,w,.45,d,mat,layer)
 def terrace(self,x,z,w,d,y,rail=False,hole=None):
  pieces=[(x,z,w,d)]
  if hole:
   hx,hz,hw,hd=hole
   pieces=[(x,z,hx-x,d),(hx+hw,z,x+w-hx-hw,d),(hx,z,hw,hz-z),(hx,hz+hd,hw,z+d-hz-hd)]
   self.box(hx,-1.5,hz,hw,y,hd,'stone_dark','base')
  for xx,zz,ww,dd in pieces:
   self.box(xx,-1.5,zz,ww,y+1.05,dd,'stone_dark','base');self.floor(xx,y,zz,ww,dd)
  self.box(x-.25,y-1,z-.25,w+.5,.45,d+.5,'stone','base')
  if hole:
   # 腰线只沿台地外沿，池底保持低于庭院地坪。
   self.boxes.pop()
   for xx,zz,ww,dd in [(x-.25,z-.25,w+.5,.65),(x-.25,z+d-.4,w+.5,.65),(x-.25,z,.65,d),(x+w-.4,z,.65,d)]:self.box(xx,y-1,zz,ww,.45,dd,'stone','base')
  for xx,zz,ww,dd in [(x,z,w,.65),(x,z+d-.65,w,.65),(x,z,.65,d),(x+w-.65,z,.65,d)]:
   self.box(xx,y-.27,zz,ww,.3,dd,'carved','base')
  # 台基有水平石缝、墙脚和少量垂直分缝，保留完整承重主体。
  for yy in range(1,max(1,math.floor(y)),2):
   self.box(x-.03,yy,z-.03,w+.06,.12,.07,'stone','base');self.box(x-.03,yy,z+d-.04,w+.06,.12,.07,'stone','base')
  if rail:
   self.rail((x,y,z),(x,y,z+d),'stone');self.rail((x+w,y,z),(x+w,y,z+d),'stone')
 def stairs(self,cx,z,low,high,width=10,tread=.7,foundation=True):
  n=round((high-low)*2)
  if n and foundation:self.box(cx-width/2,-1.5,z,width,low+1,n*tread+1.5,'stone_dark','stair')
  for i in range(n):self.box(cx-width/2,low-.5,z+i*tread,width,(i+1)*.5+.5,tread+.02,'stone','stair')
  if n:
   self.floor(cx-width/2,high,z+n*tread,width,1.5,'carved','stair')
   for side in [-1,1]:
    x=cx+side*(width/2+.6)
    self.beam([x,low+1,z],[x,high+1,z+n*tread],.45,.65,'carved','stair')
    self.box(x-.35,low,z-.35,.7,1.6,.7,'stone','stair');self.box(x-.45,low+1.6,z-.45,.9,.3,.9,'carved','stair')
 def rail(self,a,b,style='wood',height=1.35,spacing=3):
  x,y,z=a;xx,yy,zz=b;l=math.hypot(xx-x,zz-z);n=max(1,round(l/spacing));m='carved' if style=='stone' else 'wood_red';cap='stone_dark' if style=='stone' else 'bronze_dark'
  for h,w in [(height,.25),(.35,.17)]:self.beam([x,y+h,z],[xx,yy+h,zz],w,w,m)
  for i in range(n+1):
   px=x+(xx-x)*i/n;pz=z+(zz-z)*i/n
   self.box(px-.16,y,pz-.16,.32,height+.18,.32,m)
   self.box(px-.24,y+height+.12,pz-.24,.48,.18,.48,cap)
   if i<n and style=='stone':
    qx=x+(xx-x)*(i+.5)/n;qz=z+(zz-z)*(i+.5)/n
    self.prism(qx,y+.35,qz,.3,.7,'stone_dark',4)
 def column(self,x,y,z,h,w=.75,rich=False):
  self.box(x-w*.85,y-.1,z-w*.85,w*1.7,.65,w*1.7,'stone','detail')
  self.box(x-w*.63,y+.5,z-w*.63,w*1.26,.4,w*1.26,'carved','detail')
  self.box(x-w/2,y+.75,z-w/2,w,h-.75,w,'wood_red' if rich else 'wood_dark','frame')
  self.box(x-w*.62,y+h-.7,z-w*.62,w*1.24,.4,w*1.24,'bronze_dark' if rich else 'wood','frame')
 def dougong(self,x,y,z,scale=1,axis='front',style='civic'):
  # 三层承托与横向拱臂分别建模；档楼采用较短的收敛形式。
  rich=style in ('core','hall','gate');arms=3 if rich else 2
  for i in range(arms):
   yy=y+i*.4*scale;length=(1.2+i*.85)*scale;depth=(.6+i*.28)*scale
   self.box(x-length/2,yy,z-depth/2,length,.28*scale,depth,'wood_red' if rich else 'wood','bracket')
   for k in [-1,1]:
    self.box(x+k*(length/2-.25*scale)-.15*scale,yy+.23*scale,z-depth/2,.3*scale,.35*scale,depth,'bronze_dark' if i==arms-1 and rich else 'wood_dark','bracket')
  self.box(x-.28*scale,y-.3*scale,z-.9*scale,.56*scale,.5*scale,1.8*scale,'wood_dark','bracket')
  if rich:
   self.beam([x-1.6*scale,y+.7*scale,z],[x-.4*scale,y-.3*scale,z],.3*scale,.4*scale,'wood_red','bracket')
   self.beam([x+1.6*scale,y+.7*scale,z],[x+.4*scale,y-.3*scale,z],.3*scale,.4*scale,'wood_red','bracket')
 def lantern(self,x,y,z,kind='hang',ceiling=None):
  # y 为灯罩底部；灯光跟随完整的铜框、灯罩和悬杆。
  if kind=='post':self.box(x-.14,y-2.8,z-.14,.28,2.8,.28,'wood_dark','detail')
  else:self.box(x-.07,y+1,z-.07,.14,max(.1,(ceiling if ceiling is not None else y+2)-y-1),.14,'bronze_dark','detail')
  self.box(x-.42,y-.1,z-.42,.84,.2,.84,'bronze','light_fixture')
  self.box(x-.33,y+.1,z-.33,.66,.85,.66,'lamp','light_fixture')
  for dx in [-.37,.29]:
   for dz in [-.37,.29]:self.box(x+dx,y,z+dz,.08,1.05,.08,'bronze_dark','light_fixture')
  self.box(x-.48,y+1,z-.48,.96,.18,.96,'bronze_dark','light_fixture')
  self.lamps.append(dict(p=[x,y+.5,z],part=self.part))
 def sign(self,text,cx,y,z,w=6,h=1.5):
  self.box(cx-w/2-.2,y-.2,z-.12,w+.4,h+.4,.35,'wood_dark','sign')
  for yy in [y-.1,y+h]:self.box(cx-w/2,yy,z-.18,w,.1,.12,'bronze','sign')
  self.signs.append(dict(text=text,p=[cx,y+h/2,z-.2],size=[w,h],part=self.part))
 def seal(self,x,y,z,r=1.2):
  self.box(x-r*.9,y-r*.9,z,r*1.8,r*1.8,.18,'bronze_dark','detail')
  for a,b in [((-r,0),(0,r)),((0,r),(r,0)),((r,0),(0,-r)),((0,-r),(-r,0))]:
   self.beam([x+a[0],y+a[1],z-.2],[x+b[0],y+b[1],z-.2],.13,.16,'bronze','detail')
  self.box(x-.15,y-r*.5,z-.3,.3,r,.17,'bronze','detail');self.box(x-r*.5,y-.15,z-.3,r,.3,.17,'bronze','detail')

 def roof(self,x,y,z,w,d,kind='xieshan',rise=6,over=2.5,inner=None):
  # 同一材质族保留不同屋顶形制；瓦面使用带竖向搭接的阶梯构造。
  step=1.;left=x-over;front=z-over;W=w+2*over;D=d+2*over;cx=x+w/2;cz=z+d/2
  nx=math.ceil(W/step);nz=math.ceil(D/step);dx=W/nx;dz=D/nz
  hx=w*.15 if kind=='xieshan' else 0
  grid=[]
  for iz in range(nz):
   row=[];zz=front+(iz+.5)*dz;uz=abs(zz-cz)/(D/2)
   for ix in range(nx):
    xx=left+(ix+.5)*dx;ux=abs(xx-cx)/(W/2)
    if inner and inner[0]<=xx<=inner[0]+inner[2] and inner[1]<=zz<=inner[1]+inner[3]:row.append(None);continue
    hz=rise*(1-uz)**1.55
    if kind=='hip' or kind=='ring':h=min(hz,rise*(1-ux)**1.55)
    elif kind=='lean':h=rise*((zz-front)/D)**1.5
    elif kind=='xieshan' and abs(xx-cx)>w/2-hx:
     u=(abs(xx-cx)-(w/2-hx))/(over+hx);h=min(hz,rise*.4*(1-u)**1.5)
    else:h=hz
    edge_up=.55*max(0,(uz-.82)/.18)**2
    corner_up=1.2*max(0,(ux-.75)/.25)**2*max(0,(uz-.7)/.3)**2
    h=round((h+edge_up+corner_up)*4)/4
    mat='tile_edge' if ix in (0,nx-1) or iz in (0,nz-1) else 'tile_alt' if ((ix*13+iz*7)%31==0) else 'tile'
    row.append((h,mat))
   grid.append(row)
  for iz,row in enumerate(grid):
   runs=[];start=0;last=None
   for ix in range(nx+1):
    v=row[ix] if ix<nx else None
    if v!=last:
     if last is not None:runs.append((start,ix,last))
     start=ix;last=v
   for a,b,(h,mat) in runs:
    neighbor=[h]
    for j in (iz-1,iz+1):
     if 0<=j<nz:
      neighbor.extend(grid[j][k][0] for k in range(a,b) if grid[j][k] is not None)
    low=min(neighbor)-.35
    self.box(left+a*dx,y+low,front+iz*dz,(b-a)*dx,h-low+.35,dz+.035,mat,'roof')
  # 檐口的木基层、椽尾和瓦当跟随曲线，瓦面之外仍能读出承托构造。
  rich=self.part in ('gate','hall','core','envoy')
  for iz,sign in [(0,-1),(nz-1,1)]:
   zz=front if sign<0 else front+D
   for ix in range(0,nx,2):
    v=grid[iz][ix]
    if v is None:continue
    hh=v[0];xx=left+(ix+.4)*dx
    self.box(xx,y+hh-.46,zz-.22,1.55,.22,.45,'wood_dark','roof')
    self.beam([xx+.3,y+hh-.23,zz+sign*.3],[xx+.3,y+hh+.05,zz-sign*1.5],.2,.24,'wood','roof')
    if rich:
     self.box(xx+.05,y+hh+.1,zz+sign*.08,.6,.22,.2,'tile_dark','roof')
     self.box(xx+.22,y+hh+.06,zz+sign*.16,.25,.14,.15,'bronze_dark','roof')
  if kind in ('xieshan','gable'):
   end=x+hx if kind=='xieshan' else x
   # 屋顶端部有完整山墙与木构收边。
   for xx in [end,x+w-hx-.4 if kind=='xieshan' else x+w-.4]:
    for iz in range(nz):
     zz=front+(iz+.5)*dz;uz=abs(zz-cz)/(D/2);hh=rise*(1-uz)**1.55
     lower=rise*.4 if kind=='xieshan' else 0
     if hh>lower+.2:self.box(xx,y+lower,front+iz*dz,.4,hh-lower,dz,'plaster','roof')
    self.beam([xx+.15,y+rise*.42,cz-d*.22],[xx+.15,y+rise-.2,cz],.28,.35,'wood_dark','roof')
    self.beam([xx+.15,y+rise-.2,cz],[xx+.15,y+rise*.42,cz+d*.22],.28,.35,'wood_dark','roof')
   rlen=w-2*hx
   self.box(x+hx-.7,y+rise+.25,cz-.45,rlen+1.4,.5,.9,'tile_dark','roof')
   self.box(x+hx-.9,y+rise+.7,cz-.18,rlen+1.8,.22,.36,'bronze_dark','roof')
   if self.part in ('core','hall','gate'):
    for xx,sign in [(x+hx,-1),(x+w-hx,1)]:
     for i in range(3):self.box(xx+sign*(.35+i*.4)-.3,y+rise+.85+i*.45,cz-.3,.6,.55,.6,'bronze','roof')
   else:
    for xx in [x+hx-.3,x+w-hx-.3]:self.box(xx,y+rise+.5,cz-.4,.6,.65,.8,'tile_dark','roof')
  elif kind=='hip' and not inner:
   self.box(cx-w*.18,y+rise+.4,cz-.4,w*.36,.5,.8,'tile_dark','roof')

 def front_module(self,origin_x,origin_z,angle,fn):
  start=len(self.boxes);ms=len(self.meshes);sg=len(self.signs);li=len(self.lamps);bc=len(self.bookcases);fn()
  a=math.radians(angle);c=math.cos(a);s=math.sin(a);qc=math.cos(a/2);qs=math.sin(a/2)
  def point(p):return [origin_x+p[0]*c+p[2]*s,p[1],origin_z-p[0]*s+p[2]*c]
  for b in self.boxes[start:]:
   b[:3]=[round(v,3) for v in point(b[:3])];qx,qy,qz,qw=b[9:13]
   b[9:13]=[round(qc*qx+qs*qz,5),round(qc*qy+qs*qw,5),round(qc*qz-qs*qx,5),round(qc*qw-qs*qy,5)]
  for m in self.meshes[ms:]:
   v=m['vertices'];m['vertices']=[q for i in range(0,len(v),3) for q in point(v[i:i+3])]
  for item in self.signs[sg:]:item['p']=point(item['p']);item['yaw']=angle
  for item in self.lamps[li:]:item['p']=point(item['p'])
  for item in self.bookcases[bc:]:item['p']=point(item['p']);item['yaw']=item.get('yaw',0)+angle

 def window(self,x,y,z,w,h,kind='lattice'):
  self.box(x,y,z+.16,w,h,.12,'glass' if kind!='paper' else 'paper_window','window')
  for xx in [x,x+w-.18]:self.box(xx,y,z,.18,h,.28,'wood_dark','window')
  for yy in [y,y+h-.18]:self.box(x,yy,z,w,.18,.28,'wood_red','window')
  self.box(x-.15,y-.18,z-.1,w+.3,.18,.42,'carved','window')
  if kind=='horizontal':
   for t in [.28,.62]:self.box(x,y+h*t,z-.04,w,.16,.22,'wood','window')
   for t in [.25,.75]:self.box(x+w*t,y,z-.04,.15,h,.22,'wood','window')
  elif kind=='paper':
   for t in [.25,.5,.75]:self.box(x+w*t-.06,y,z-.04,.12,h,.2,'wood','window')
   self.box(x,y+h*.52,z-.05,w,.12,.24,'wood','window')
  elif kind=='seal':
   cx=x+w/2;cy=y+h/2;r=min(w,h)*.4
   for i in range(8):
    a=math.radians(22.5+i*45);b=a+math.pi/4
    self.beam([cx+math.cos(a)*r,cy+math.sin(a)*r,z-.1],[cx+math.cos(b)*r,cy+math.sin(b)*r,z-.1],.16,.22,'bronze_dark','window')
   self.box(cx-.07,y,z-.06,.14,h,.2,'wood','window');self.box(x,cy-.07,z-.06,w,.14,.2,'wood','window')
  else:
   for t in [.33,.67]:self.box(x+w*t-.075,y,z-.03,.15,h,.23,'wood','window')
   for t in [.3,.7]:self.box(x,y+h*t-.07,z-.03,w,.14,.23,'wood','window')
   # 中间的回纹为一组有空隙的四边构件，远景仍能读出主窗格。
   cx=x+w/2;cy=y+h/2;rw=min(w*.22,1.1);rh=min(h*.25,.9)
   self.box(cx-rw/2,cy-rh/2,z-.07,rw,.1,.18,'bronze_dark','window');self.box(cx-rw/2,cy+rh/2,z-.07,rw,.1,.18,'bronze_dark','window')
   for xx in [cx-rw/2,cx+rw/2]:self.box(xx,cy-rh/2,z-.07,.1,rh,.18,'bronze_dark','window')

 def facade(self,length,y,h,profile='public',entry=False,rich=False,bays=None):
  bays=bays or max(3,round(length/6));bays+=1 if bays%2==0 else 0;bay=length/bays
  colw=1 if rich else .65
  for i in range(bays+1):
   px=i*bay
   self.column(px,y,0,h,colw,rich)
   self.dougong(px,y+h-.5,0,.9 if rich else .65,style=self.part)
  for i in range(bays):
   a=i*bay+colw/2+.12;ww=bay-colw-.24;mid=entry and i==bays//2
   if profile=='open':
    if not mid and i%2==0:
     self.beam([i*bay,y+h-2,0],[i*bay+1.8,y+h-.2,0],.25,.4,'wood','frame')
    continue
   if mid:
    dh=min(h-1.5,9 if rich else 5.3)
    self.box(a,y+dh,0,ww,h-dh,.65,'wood_dark','wall')
    for xx in [a,a+ww-.22]:self.box(xx,y,-.25,.22,dh,.4,'bronze_dark' if rich else 'wood_red','detail')
    if rich:
     self.box(a-.2,y+dh-.3,-.35,ww+.4,.4,.45,'wood_red','detail');self.seal(a+ww/2,y+dh+.8,-.36,.55)
    continue
   if profile in ('archive','vault'):
    sy=3.2;wh=min(2.1,h-5);wy=y+sy
    self.box(a,y,0,ww,sy,.65,'brick' if y<15 else 'plaster','wall')
    self.box(a,wy+wh,0,ww,h-sy-wh,.65,'plaster','wall')
    slit=.65;gap=.5;group=3*slit+2*gap;left=a+(ww-group)/2
    self.box(a,wy,0,max(.1,left-a),wh,.65,'plaster','wall');self.box(left+group,wy,0,max(.1,a+ww-left-group),wh,.65,'plaster','wall')
    for k in range(3):
     self.window(left+k*(slit+gap),wy,-.05,slit,wh,'horizontal')
     if k<2:self.box(left+k*(slit+gap)+slit,wy,0,gap,wh,.65,'plaster','wall')
    if profile=='vault':
     self.box(a+.2,y+.4,-.08,ww-.4,1.0,.22,'stone_dark','detail')
     py=y+h-4.2
     self.box(a+.3,py,-.1,ww-.6,2.3,.2,'wood_red','detail')
     self.box(a+.5,py+.2,-.24,ww-1,1.9,.14,'bronze_dark','detail')
     if i==bays//2:self.seal(a+ww/2,py+1.15,-.4,.68)
   else:
    sy=1.5 if h<9 else 2.1;wh=min(h-3.6,4.6 if rich else 3.4);wy=y+sy
    self.box(a,y,0,ww,sy,.65,'stone' if profile=='stone' else 'plaster','wall')
    self.box(a,wy+wh,0,ww,h-sy-wh,.65,'plaster','wall')
    margin=.38 if ww<5 else .7;win=ww-2*margin
    for xx in [a,a+ww-margin]:self.box(xx,wy,0,margin,wh,.65,'plaster','wall')
    self.window(a+margin,wy,-.07,win,wh,{'council':'seal','envoy':'paper','office':'horizontal','public':'lattice','stone':'horizontal'}.get(profile,'lattice'))
    if profile=='envoy':
     for side in [a+.1,a+ww-.35]:self.box(side,wy,-.15,.24,wh,.42,'wood','detail')
   if rich:
    self.box(a,y+h-1.15,-.14,ww,.55,.3,'wood_red','detail')
    self.box(a+.25,y+h-.98,-.19,max(.1,ww-.5),.08,.1,'bronze','detail')
  self.box(-.6,y+h-.35,-.5,length+1.2,.55,1,'wood_dark','frame')
  if rich:self.box(-.6,y+h+.15,-.6,length+1.2,.2,1.2,'bronze_dark','frame')

 def body(self,x,y,z,w,d,h,profile='public',side=None,entry=True,back_entry=False,rich=False,floor=True,front_open=False,bays=None,west_entry=False,east_entry=False):
  if floor:self.floor(x,y+.04,z,w,d,'floor','floor')
  self.front_module(x,z,0,lambda:self.facade(w,y,h,'open' if front_open else profile,entry,rich,bays))
  self.front_module(x+w,z,-90,lambda:self.facade(d,y,h,side or profile,east_entry,False))
  self.front_module(x+w,z+d,180,lambda:self.facade(w,y,h,side or profile,back_entry,False))
  self.front_module(x,z+d,90,lambda:self.facade(d,y,h,side or profile,west_entry,False))
  # 可见的横梁与端部斜撑连接四面墙体。
  for xx in [x+2,x+w-2]:self.box(xx,y+h-.55,z,.55,.7,d,'wood_dark','frame')
  for zz in [z+2,z+d-2]:self.box(x,y+h-.55,zz,w,.7,.55,'wood_dark','frame')

 def room_floor(self,x,y,z,w,d,hole=None):
  if hole is None:self.floor(x,y+.04,z,w,d,'floor');return
  hx,hz,hw,hd=hole
  for xx,zz,ww,dd in [(x,z,hx-x,d),(hx+hw,z,x+w-hx-hw,d),(hx,z,hw,hz-z),(hx,hz+hd,hw,z+d-hz-hd)]:
   self.floor(xx,y+.04,zz,ww,dd,'floor')
 def internal_stair(self,hx,hz,low,high):
  count=round(high-low);r=(high-low)/(2*count);t=.49
  for i in range(count):
   self.box(hx,low+i*r,hz+i*t,2.25,r,t+.03,'wood','interior')
   self.box(hx+3,low+(count+i)*r,hz+(count-1-i)*t,2.25,r,t+.03,'wood','interior')
  self.floor(hx,low+count*r,hz+count*t,5.25,1.1,'wood','interior')
  self.beam([hx,low+.4,hz],[hx,low+count*r+.4,hz+count*t],.35,.3,'wood_dark','interior')
  self.beam([hx+5.25,low+count*r+.4,hz+count*t],[hx+5.25,high+.4,hz],.35,.3,'wood_dark','interior')

 def books(self,x,y,z,w=5,h=3.5):
  self.box(x,y,z,w,h,.7,'wood_dark','interior')
  for yy in [0,h*.33,h*.66,h]:self.box(x-.1,y+yy,z-.45,w+.2,.18,1.15,'wood','interior')
  if getattr(self,'procedural_bookcases',False):
   self.bookcases.append(dict(p=[x,y,z],size=[w,h],yaw=0,part=self.part))
  else:
   rng=random.Random(round(x*113+z*43+y*11))
   for k in range(3):
    xx=x+.15
    while xx<x+w-.3:
     bw=rng.choice([.22,.3,.38]);hh=rng.choice([.6,.75,.9]);mat=rng.choice(['book_blue','book_red','book_cream'])
     self.box(xx,y+k*h/3+.22,z-.38,bw,hh,.45,mat,'interior');xx+=bw+.045
  if h>=4:
   for xx in [x+w-1.2,x+w-.35]:self.beam([xx,y,z-1.3],[xx,y+h+.1,z-.35],.13,.16,'wood_red','interior')
   for i in range(1,math.ceil(h/.45)):
    yy=i*.45;zz=z-1.3+.95*yy/(h+.1);self.box(x+w-1.2,y+yy,zz,.85,.1,.16,'wood','interior')
 def desk(self,x,y,z,w=4,d=1.7,mat='wood'):
  self.box(x,y+1.05,z,w,.3,d,mat,'interior')
  for xx in [x+.2,x+w-.6]:
   for zz in [z+.15,z+d-.45]:self.box(xx,y,zz,.4,1.05,.3,'wood_dark','interior')
  self.box(x+.4,y+1.36,z+.3,w*.35,.05,d*.55,'paper','interior')
  self.box(x+w-.7,y+1.36,z+.3,.3,.45,.3,'ink','interior')
 def stool(self,x,y,z):
  self.box(x-.55,y+.65,z-.45,1.1,.23,.9,'wood_red','interior')
  for dx in [-.42,.25]:self.box(x+dx,y,z-.27,.17,.65,.54,'wood_dark','interior')
 def map_table(self,cx,y,cz,w=10,d=6):
  self.box(cx-w/2,y,cz-d/2,w,.65,d,'wood_dark','interior')
  self.box(cx-w/2-.2,y+.65,cz-d/2-.2,w+.4,.25,d+.4,'bronze_dark','interior')
  self.box(cx-w/2+.2,y+.9,cz-d/2+.2,w-.4,.12,d-.4,'paper','interior')
  kind='ruin' if self.part=='archive' else 'states'
  self.maps.append(dict(p=[cx,y+1.045,cz],size=[w-.55,d-.55],kind=kind,part=self.part))
  for u,v in ([(.18,.38),(.72,.27),(.66,.72),(.5,.5)] if kind=='states' else[(.22,.7),(.7,.35)]):
   xx=cx+(u-.5)*(w-.55);zz=cz+(.5-v)*(d-.55)
   self.box(xx-.08,y+1.05,zz-.08,.16,.45,.16,'bronze_dark','interior')
   self.box(xx-.22,y+1.4,zz-.09,.44,.25,.18,'red','interior')
 def screen(self,x,y,z,length=8):
  for xx in [x,x+length/2,x+length]:self.column(xx,y,z,3.7,.35)
  self.box(x,y+.2,z,length,.8,.35,'wood','interior')
  self.box(x,y+3.4,z,length,.3,.35,'wood_dark','interior')
  for i in range(1,round(length/.6)):
   self.box(x+i*.6,y+1,z,.11,2.4,.22,'wood_red','interior')
  for yy in [y+1.6,y+2.7]:self.box(x,yy,z,length,.12,.22,'bronze_dark','interior')
 def council(self,cx,y,cz):
  self.prism(cx,y,cz,4.2,.8,'wood_dark',8,'interior')
  self.prism(cx,y+.8,cz,4.6,.3,'wood_red',8,'interior')
  self.prism(cx,y+1.1,cz,3.5,.05,'paper',8,'interior')
  for i in range(8):
   a=i*math.pi/4;self.stool(cx+math.cos(a)*5.8,y,cz+math.sin(a)*5.8)
 def gear(self,cx,y,cz,r=1,depth=.35):
  self.prism(cx,y,cz,r*.75,depth,'bronze_dark',12,'interior',0)
  for i in range(12):
   a=i*math.pi/6;self.box(cx+math.cos(a)*r-.2,y,cz+math.sin(a)*r-.2,.4,depth,.4,'bronze','interior',i*30)
  self.prism(cx,y+depth,cz,r*.2,.15,'wood_dark',8,'interior')
 def machine(self,cx,y,cz):
  self.box(cx-2,y,cz-1.6,4,1,3.2,'stone_dark','interior')
  self.box(cx-2.2,y+1,cz-1.8,4.4,.3,3.6,'bronze_dark','interior')
  for xx in [cx-1.8,cx+1.3]:self.box(xx,y+1.3,cz+.8,.5,2.9,.5,'wood_dark','interior')
  self.box(cx-2,y+4.2,cz+.7,4,.6,.7,'wood_red','interior')
  self.gear(cx-.7,y+1.35,cz,.85);self.gear(cx+.8,y+1.35,cz,.55)
  self.box(cx-.22,y+1.4,cz+.7,.44,2.8,.44,'bronze','interior')
 def person(self,x,y,z,mat='robe'):
  self.box(x-.225,y+.675,z-.112,.45,.675,.225,mat,'people')
  self.box(x-.225,y+1.35,z-.225,.45,.45,.45,'skin','people')
  for dx in [-.225,.025]:self.box(x+dx,y,z-.112,.2,.675,.225,'wood_dark','people')
  for dx in [-.45,.225]:
   self.box(x+dx,y+.825,z-.112,.225,.525,.225,mat,'people');self.box(x+dx,y+.675,z-.112,.225,.15,.225,'skin','people')
 def crown(self,x,y,z,rx,ry,rz,mat='pine',seed=1,cell=1):
  for ix in range(math.floor((x-rx)/cell),math.ceil((x+rx)/cell)+1):
   for iy in range(math.floor((y-ry)/cell),math.ceil((y+ry)/cell)+1):
    for iz in range(math.floor((z-rz)/cell),math.ceil((z+rz)/cell)+1):
     xx=(ix+.5)*cell;yy=(iy+.5)*cell;zz=(iz+.5)*cell
     norm=((xx-x)/rx)**2+((yy-y)/ry)**2+((zz-z)/rz)**2
     noise=((ix*181+iy*29+iz*71+seed*43)%101)/101
     if norm>1.12 or norm>.72 and noise<.16:continue
     self.box(ix*cell,iy*cell,iz*cell,cell+.01,cell+.01,cell+.01,mat,'nature')
 def pine(self,x,y,z,h=9,seed=1):
  self.box(x-.45,y,z-.45,.9,h,.9,'wood_dark','nature')
  variants=[(-1.2,.4),(1.1,-.8),(.5,1.3)];dx,dz=variants[seed%3]
  for i,(ratio,span,thick) in enumerate([(.43,3.2,2.0),(.62,2.9,2.0),(.8,2.2,1.7),(.97,1.3,1.5)]):
   ox=dx*(1-ratio)*1.7;oz=dz*(1-ratio)*1.7;yy=y+h*ratio
   self.beam([x,yy-1.5,z],[x+ox,yy,z+oz],.42,.42,'wood_dark','nature')
   self.crown(x+ox,yy,z+oz,span,thick,span*.87,'pine',seed+i*7)
 def cherry(self,x,y,z,h=7):
  self.box(x-.35,y,z-.35,.7,h-1,.7,'wood','nature')
  for a,b in [([x,y+3,z],[x-2,y+5,z+1]),([x,y+4,z],[x+2,y+6,z-1])]:self.beam(a,b,.45,.45,'wood','nature')
  for i,(dx,dy,dz,rx,rz) in enumerate([(-1.8,4.8,.8,2.8,2.5),(.2,6.4,-1.2,2.5,2.8),(2,5.4,1.8,2.7,2.5)]):self.crown(x+dx,y+dy,z+dz,rx,1.8,rz,'cherry',i+5)
 def bamboo(self,x,y,z,h=7):
  for dx,dz,hh in [(-.7,0,h),(0,.65,h-1),(.6,-.3,h+.5)]:
   self.box(x+dx,y,z+dz,.18,hh,.18,'bamboo','nature')
   for i in range(1,int(hh),2):self.box(x+dx-.03,y+i,z+dz-.03,.24,.12,.24,'wood_dark','nature')
   self.crown(x+dx+.45,y+hh-1,z+dz,.9,.65,.55,'leaf',int(h*3),.45)
   self.crown(x+dx-.35,y+hh-.1,z+dz-.3,.55,.65,.85,'leaf',int(h*5),.45)
 def rock(self,x,y,z,scale=1):
  for dx,dy,dz,r,h,n in [(-.2,0,0,1.8,1.3,6),(.5,.8,.2,1,2.5,5),(-.6,2.2,.1,1.3,1.8,5),(-.3,3.6,.2,.8,.8,4)]:
   self.prism(x+dx*scale,y+dy*scale,z+dz*scale,r*scale,h*scale,'rock',n,'nature',17+dy*11)


def ground(s):
 s.part='ground'
 s.box(-3,-4,-3,174,3.5,190,'soil','ground')
 s.floor(-2,-.47,-2,172,188,'grass','ground')
 # 各进院落的完成面按使用区域分别落位，庭园仍保留土壤和水院。
 for x,z,w,d,y in [(7,6,154,57,0),(9,64,150,43,3),(10,110,148,65,6),(52,128,65,49,10)]:
  s.terrace(x,z,w,d,y,hole=[127,130,24,32] if z==110 else None)
 # 用独立草地与院墙界定庭院，地面不依靠整片木板覆盖。
 for x,z,w,d,y in [(9,28,39,29,0),(121,30,35,27,0),(13,141,38,33,6),(122,112,5,60,6),(151,112,5,60,6),(127,112,24,18,6),(127,162,24,10,6)]:s.floor(x,y+.04,z,w,d,'grass','ground')
 for x,z,w,d,y in [(73,1,22,69,0),(72,99,24,10,3),(72,110,24,18,6),(71,128,26,7,10),(44,65,9,70,3),(115,61,8,71,3)]:
  s.floor(x,y+.07,z,w,d,'paving','ground')
  s.floor(x,y+.09,z,.55,d,'paving_dark','ground');s.floor(x+w-.55,y+.09,z,.55,d,'paving_dark','ground')
 # 前院中轴的石纹为铺装图案，比例服从步行尺度。
 for z in [36,47,58]:
  s.box(80,.12,z,8,.07,5,'paving_dark','ground')
  s.box(80.5,.2,z+.5,7,.05,4,'paving','ground')
  s.box(83.65,.26,z+.9,.7,.03,3.2,'bronze_dark','ground')
 s.part='garden'
 s.stairs(84,61,0,3,22);s.stairs(84,106,3,6,20);s.stairs(84,121,6,10,22)
 for x in [47,117]:s.stairs(x,60,0,3,7);s.stairs(x,106,3,6,7)
 # 台地两侧细石栏留出门厅、主路和庭院入口。
 for a,b in [((54,3,66),(71,3,66)),((97,3,66),(116,3,66)),((51,6,112),(72,6,112)),((96,6,112),(120,6,112)),((54,10,130),(72,10,130)),((96,10,130),(115,10,130))]:s.rail(a,b,'stone')
 # 低院墙通过石墙裙、白墙、瓦帽和转折墙墩形成边界。
 for x,z,w,d in [(1,2,2,178),(165,2,2,178),(2,180,164,2),(2,2,50,2),(116,2,50,2)]:
  s.box(x,-.3,z,w,1.7,d,'stone_dark','boundary');s.box(x,1.4,z,w,2.7,d,'plaster','boundary')
  s.box(x-.2,4.1,z-.2,w+.4,.45,d+.4,'tile_dark','roof')
  if w>d:
   for xx in range(int(x),int(x+w),10):s.box(xx,0,z-.25,.8,4.65,d+.5,'stone','boundary')
  else:
   for zz in range(int(z),int(z+d),12):s.box(x-.25,0,zz,w+.5,4.65,.8,'stone','boundary')

def gate(s,inscription_stones=True):
 s.part='gate'
 # 中间门阙与两侧值守房独立成形，通道始终位于中轴。
 s.terrace(68,6,32,22,.45)
 s.body(72,.5,9,24,16,11,'public','stone',True,True,True,front_open=True,bays=3)
 # 两侧厚石门墩支承主梁，正中保持八格以上净宽。
 for x in [70,94]:
  s.box(x,.5,7,4,7,20,'stone','body');s.box(x-.3,1,6.7,4.6,.6,20.6,'carved','detail')
  s.box(x-.3,6.8,6.7,4.6,.55,20.6,'stone_dark','detail')
  s.seal(x+2,4.7,6.7,1.05)
 s.box(75.5,9.5,7.7,17,.8,1.4,'wood_red','frame')
 s.sign('千机阁',84,10.3,7.5,7,1.7)
 s.roof(70,12,8,28,18,'xieshan',5.8,3)
 s.body(76,17.5,13,16,9,4.2,'office','archive',False,False,False)
 s.roof(75,22,12,18,11,'xieshan',4.8,2.8)
 for x in [74,94]:s.lantern(x,6.3,10,ceiling=11.3)
 # 西值守房为低歇山，东验符房增加侧向门廊，入口尺度相同而构造各异。
 s.terrace(52,11,16,17,0)
 s.body(53,0,12,13,14,6.2,'stone','office',True)
 s.roof(52.5,6.8,11.5,14,15,'hip',4.6,1.6)
 s.terrace(100,10,16,19,0)
 s.body(102,0,12,12,14,7.6,'office','envoy',True)
 s.roof(101,8.2,11,14,16,'gable',4.1,1.6)
 s.roof(100,4.7,9,15,3.5,'lean',1.3,1)
 s.desk(104,0,18,5,2);s.stool(108,0,22)
 # 门外照壁式铭石有独立底座、凹槽与铜铭，避免用巨大招牌占满主楼。
 for x in ([61,107] if inscription_stones else []):
  s.box(x-2,0,2,4,.7,2.5,'stone_dark','detail');s.box(x-1.5,.7,2.4,3,3.7,1.7,'stone','detail')
  s.box(x-1.7,4.4,2.2,3.4,.45,2.1,'carved','detail');s.seal(x,2.6,2.25,.8)

def public_court(s):
 s.part='office'
 # 西办事廊：面向前院的连续开放柜台，后部为卷柜和文书房。
 s.terrace(12,29,34,20,0)
 s.body(15,0,32,28,13,6.3,'office','archive',True,False,front_open=True,bays=5)
 s.roof(14,7,31,30,15,'lean',3.1,2)
 for x in [17,25,33]:
  s.desk(x,0,33.5,6,2.2);s.books(x+.5,0,43,5,3.4)
  s.lantern(x+3,4.3,32.5,ceiling=6.3)
 s.sign('委托受理',29,4.6,30.7,7.5,1.2)
 s.part='review'
 # 东侧核验房：独立门屋、候客长凳和可关闭的板门。
 s.terrace(123,32,33,22,0)
 s.body(126,0,36,26,14,7.5,'envoy','office',True,False,False,bays=3)
 s.roof(125,8.2,35,28,16,'gable',4.4,2)
 s.roof(132,5.5,31.7,14,4.5,'lean',1.8,1.5)
 for x in [128,144]:s.column(x,0,32,5.2,.6)
 s.sign('核验结算',139,4.5,34.7,6.6,1.1)
 s.desk(130,0,45,6,2);s.books(144,0,48,5,3)
 for z in [39,45]:
  s.box(125,0,z,1,.7,3.6,'wood_dark','interior');s.box(124.8,.7,z,1.4,.25,3.6,'wood','interior')
 # 前院树池、候客席与铜铭板分别布置，留出中轴视线。
 s.part='garden'
 for x,z,w,d in [(52,37,12,15),(104,42,13,15)]:
  s.box(x,0,z,w,.6,d,'carved','detail');s.floor(x+.5,.65,z+.5,w-1,d-1,'moss','nature')
 s.pine(58,.65,45,10,9);s.cherry(110,.65,50,7)
 for x,z in [(65,51),(105,37)]:s.lantern(x,2.8,z,'post')
 for x,z,w in [(51,58,13),(104,61,13)]:
  s.box(x,0,z,w,.65,1.5,'stone_dark','detail');s.box(x-.15,.65,z-.15,w+.3,.2,1.8,'wood','detail')
 s.person(85,0,33);s.person(35,0,30,'cloth');s.person(136,0,32,'wood_red')

def great_hall(s):
 s.part='hall';s.terrace(52,69,64,32,3)
 # 中殿与两翼有各自的屋面、门槛和檐廊。
 s.body(60,3,75,48,23,12.5,'council','public',True,True,True,bays=5)
 # 六格深前廊的成组梁架。
 for x in [60,69.6,79.2,88.8,98.4,108]:
  s.column(x,3,70,8.5,1,True);s.dougong(x,11,70,1.15,style='hall')
  s.beam([x,11.8,70],[x,15.6,75],.65,.8,'wood_red','frame')
 s.box(58.5,11.9,69.5,51,.65,1.1,'wood_dark','frame')
 s.roof(58,12.4,69,52,7.4,'lean',3.2,2)
 s.roof(59,16.2,74,50,25,'xieshan',7.4,3)
 # 主殿上部保留短的采光颈部和第二重屋面。
 # 前廊低檐与正殿高屋面形成两重轮廓，厅堂上方保留完整的大屋架。
 for x,profile in [(48,'archive'),(110,'office')]:
  s.body(x,3,80,10,17,6.9,profile,profile,True)
  s.roof(x-.4,10.4,79.6,10.8,17.8,'gable',3.8,1.5)
 s.sign('总务大殿',84,10.1,69.1,8.7,1.7)
 # 室内以总图、议案台和两侧夹层组织，不以重复柜台塞满空间。
 s.map_table(84,3,87,14,7)
 for x in [65,96]:
  for z in [80,91]:s.desk(x,3,z,5.5,2.2);s.stool(x+2.7,3,z+3.5)
 s.box(62,8.8,92,15,.45,5,'floor','interior');s.box(91,8.8,92,15,.45,5,'floor','interior')
 s.rail((62,9.25,92),(63.5,9.25,92));s.rail((67,9.25,92),(77,9.25,92));s.rail((91,9.25,92),(103.5,9.25,92))
 s.books(63,9.3,96,9,2.9);s.books(95,9.3,96,9,2.9)
 for x in [64,104]:
  for i in range(12):s.box(x,3+i*.5,80+i*.8,2,.5,.85,'wood','interior')
  s.floor(x,9.25,89.5,2,3,'wood','interior')
 for x in [66,78,90,102]:
  s.lantern(x,7.4,75.8,ceiling=15.1);s.lantern(x,8,93.5,ceiling=15.1)
 s.person(84,3,71,'wood_red')

def firewall(s,x,y,z,depth,peak):
 # 档案院的山墙分三段抬升，压顶和端部石墩分别收口。
 for a,b,h in [(0,depth*.22,peak-4),(depth*.22,depth*.56,peak),(depth*.78,depth*.22,peak-4)]:
  s.box(x,y,z+a,.8,h,b,'plaster','body');s.box(x-.18,y+h,z+a-.12,1.16,.45,b+.24,'tile_dark','roof')
  for zz in [z+a,z+a+b-.55]:s.box(x-.22,y+h-.15,zz,1.24,.85,.55,'carved','detail')

def archive_court(s):
 s.part='archive';s.terrace(10,72,33,67,3)
 # 前库的下部石墙、高侧小窗和防火山墙与主阁采用不同构造。
 s.body(13,3,76,26,24,7.3,'archive','archive',True)
 s.body(13,10.6,76,26,24,6.7,'archive','archive',False,floor=False)
 s.room_floor(13,10.6,76,26,24,[24.25,82,5.5,9]);s.internal_stair(24.25,82,3,10.6)
 s.roof(12.7,17.8,75.7,26.6,24.6,'gable',5.2,1.4)
 firewall(s,12.4,13.5,74.5,27,10.2);firewall(s,39,13.5,74.5,27,10.2)
 for z in [80,87,94]:s.books(15,3,z,8,4.5);s.books(31,3,z,6,4.5)
 s.sign('万卷档库',26,8.4,75.4,6,1.25)
 s.part='atlas'
 # 后部阅图楼偏向内院伸出，开窗比藏卷空间更大。
 for i,(y,h) in enumerate([(6,7.5),(14,8),(22.5,7)]):
  s.body(15+i*.75,y,113+i*.6,26-i*1.5,23-i*1.2,h,'public' if i==0 else 'office','archive',i==0,False,False,floor=False)
  s.room_floor(15+i*.75,y,113+i*.6,26-i*1.5,23-i*1.2,[32,117,5.5,8.5] if i else None)
  if i<2:s.internal_stair(32,117,y,[14,22.5][i])
  if i<2:s.roof(15+i*.75,y+h-.5,113+i*.6,26-i*1.5,23-i*1.2,'ring',2,2,inner=[15+(i+1)*.75,113+(i+1)*.6,26-(i+1)*1.5,23-(i+1)*1.2])
  else:s.roof(16.5,y+h+.3,114.2,23,20.6,'xieshan',5.4,2.2)
 s.map_table(26.5,6,122,8,5);s.books(18,14,133,18,3.5)
 s.part='garden'
 # 两栋楼之间为真正的阅卷小院，雨廊靠西布置。
 s.floor(12,3.08,102,30,9,'paving_dark','ground');s.floor(16,3.12,103,20,7,'moss','nature')
 for z in [100,106,112]:s.column(12.5,3,z,5.4,.55)
 s.roof(10.5,8.8,99,5,14,'gable',2.7,1.2)
 s.pine(23,3.15,107,8,41);s.rock(36,3.15,106,.75);s.stairs(28,106,3,6,7,.7)
 for part,x,y,z,c in [('archive',16,8,76,10.3),('archive',37,8,76,10.3),('atlas',17,11,114,13.5),('atlas',38,11,114,13.5)]:
  s.part=part;s.lantern(x,y,z,ceiling=c)
 s.sign('秘境勘图',28,10.5,112.7,6,1.2)

def workshop_court(s):
 s.part='workshop';s.terrace(124,64,32,43,3)
 # 纵向的工坊以开放前门、承重斜撑和高侧风楼识别。
 s.body(128,3,68,24,24,8.8,'office','office',True,False,False,front_open=True,bays=3)
 for x in [128,140,152]:
  s.beam([x,4,69],[x+(-3 if x==152 else 3),10.8,69],.65,.65,'wood_dark','frame')
  s.beam([x,10.7,70],[x,14.6,80],.6,.65,'wood','frame')
  s.beam([x,14.6,80],[x,10.7,90],.6,.65,'wood','frame')
 s.roof(127,12.3,67,26,26,'gable',6,2)
 # 风楼从主屋面上升，窗扇与坡向配合；不使用主阁的层檐窗格。
 s.body(131,16,76,18,8,3.8,'office','office',False)
 s.roof(130.5,20.2,75.5,19,9,'gable',3.2,1.3)
 for x,z in [(133,74),(145,83)]:s.machine(x,3,z)
 for x,z in [(130,87),(141,88)]:s.desk(x,3,z,7,2.7)
 for xx in [130,150]:s.box(xx,3.2,69,.7,6.4,2.5,'wood_red','detail')
 s.sign('玄枢机造',140,9.2,67.4,7,1.3)
 s.part='storage'
 # 后库为低实墙石库，铜包角和宽门容纳器物搬运。
 s.body(127,3,96,27,9,5.4,'stone','archive',True)
 s.roof(126.5,9,95.5,28,10,'hip',3.2,1.2)
 for x in [130,136,143,149]:
  s.box(x,3.1,99,3.1,2.4,3.1,'wood','interior');s.box(x-.08,3.8,98.92,3.26,.2,3.26,'bronze_dark','interior')
 s.part='workshop'
 for x in [128,152]:s.lantern(x,6.8,68.7,ceiling=11.8)
 s.bamboo(154.5,3,70,7);s.bamboo(153.8,3,92,8)

def water_pavilion(s,cx,y,cz):
 s.prism(cx,4.8,cz,6.5,y-4.8,'stone',8,'base')
 s.prism(cx,y,cz,5.8,.3,'floor',8,'floor')
 for i in range(8):
  a=(22.5+i*45)*math.pi/180;xx=cx+math.cos(a)*4.8;zz=cz+math.sin(a)*4.8;s.column(xx,y,zz,5.8,.48)
  if i not in [2,3]:
   b=a+math.pi/4;s.rail((xx,y,zz),(cx+math.cos(b)*4.8,y,cz+math.sin(b)*4.8),height=1.1,spacing=4)
 for k in range(10):
  t=k/9;r=7.5-6.3*t;yy=y+5.8+4.5*(t**1.2)
  s.prism(cx,yy,cz,r,.45,'tile_edge' if k==0 else 'tile',8,'roof')
 s.prism(cx,y+10.4,cz,.6,1.1,'bronze_dark',8,'roof')
 s.desk(cx-1.8,y+.3,cz-.8,3.6,1.6);s.stool(cx,y+.3,cz+2.2)

def envoy_court(s):
 s.part='envoy'
 # 东院内部有独立水面，地坪由廊、岸和台阶分别围合。
 s.floor(125,5,126,28,41,'stone_dark','ground')
 s.box(127,5.02,130,24,1.14,32,'water','water')
 for x,z,w,d in [(124.5,124,30,2.3),(124.5,164,30,2.2),(123,125,2,41),(153.3,126,2,40)]:s.floor(x,6.35,z,w,d,'paving','ground')
 s.terrace(127,112,26,17,6)
 s.body(129,6,114,22,13,7.2,'envoy','envoy',True)
 s.room_floor(128,14,112,24,17,[143,117,5.5,8])
 s.body(130.5,14,115,19,11,6.6,'envoy','council',False,True,floor=False)
 s.internal_stair(143,117,6,14)
 s.roof(129.5,21.1,114,21,13,'xieshan',4.8,2.1)
 s.roof(127.5,12,111.5,25,4,'lean',1.9,1.2)
 s.rail((128,14,112),(152,14,112),height=1.25)
 s.sign('听雨院',140,10.2,112.3,5.2,1.1)
 s.desk(133,6,122,6,2);s.desk(134,14,123,6,1.8)
 # 折廊用较低的卷棚轮廓连接水亭，保持水面可见。
 for x,z,w,d in [(128,128,4,31),(128,155,12,4)]:
  s.floor(x,6.4,z,w,d,'floor')
  if d>w:
   for zz in range(z,z+d+1,5):
    s.column(x+.4,6.4,zz,4.3,.42);s.column(x+w-.4,6.4,zz,4.3,.42)
    for xx in [x+.4,x+w-.4]:s.box(xx-.2,5,zz-.2,.4,1.4,.4,'wood_dark','frame')
  else:
   for xx in range(x,x+w+1,4):
    s.column(xx,6.4,z+.4,4.3,.42);s.column(xx,6.4,z+d-.4,4.3,.42)
    for zz in [z+.4,z+d-.4]:s.box(xx-.2,5,zz-.2,.4,1.4,.4,'wood_dark','frame')
  s.roof(x,11.1,z,w,d,'hip',1.7,1.1)
 water_pavilion(s,143,6.5,152)
 s.cherry(154,6.4,136,7);s.rock(150,6.4,164,.75)
 for x,y,z,w,d in [(126.7,6.25,135,2.4,5),(128,5.9,136,1.5,3),(129,5.55,136.3,1.2,2.4)]:s.floor(x,y,z,w,d,'carved','detail')
 s.lantern(125,9.2,140,ceiling=10.7);s.lantern(130,9.2,157,ceiling=10.7)

def core(s):
 s.part='core';cx=84;cz=153;hole=[74,153,5.5,9]
 styles=['public','office','vault','council','open']
 for i,level in enumerate(SPEC['core_levels']):
  y=level['y'];h=level['height'];w=level['width'];d=level['depth'];x=cx-w/2;z=cz-d/2
  s.room_floor(x,y,z,w,d,hole)
  s.body(x,y,z,w,d,h,styles[i],styles[i],i!=2,i==0,True,False,i==4,bays=5 if i<3 else 3,west_entry=i==1,east_entry=i==0)
  # 层檐沿真实楼身展开。前廊、露台和局部抱厦另行安排。
  if i<4:
   nxt=SPEC['core_levels'][i+1];nw=nxt['width'];nd=nxt['depth']
   s.roof(x,y+h-.8,z,w,d,'ring',2.4,3.2,inner=[cx-nw/2,cz-nd/2,nw,nd])
  else:s.roof(x-1,y+h+.6,z-.5,w+2,d+1,'xieshan',8.2,3.2)
  if i in (1,3,4):
   s.floor(x-2,y+.05,z-3,w+4,3,'floor')
   s.rail((x-2,y,z-3),(x+w+2,y,z-3),height=1.35,spacing=3.5)
   for xx in [x,x+w]:s.beam([xx,y-2,z],[xx,y-.2,z-2.8],.6,.6,'wood_red','bracket')
  if i<4:s.internal_stair(hole[0],hole[1],y,SPEC['core_levels'][i+1]['y'])
  s.floor_targets.append(dict(part='core',floor=i+1,use=level['use'],y=y,height=h,rect=[x,z,w,d],stair_hole=hole))
  if i==0:
   # 首层两侧为档案核验位，中央保留通高总图空间。
   for px in [68,100]:
    for pz in [138,151,165]:s.column(px,y,pz,h,1.05,True);s.dougong(px,y+h-.7,pz,1,style='core')
    s.box(px-.5,y+h-.6,136,1,1,31,'wood_dark','frame')
   for pz in [138,165]:s.box(67.5,y+h-.5,pz-.5,33,1,1,'wood_red','frame')
   s.floor(75.5,y+.09,138,19,15,'paving_dark','interior')
   s.floor(76,y+.12,138.5,18,14,'paving','interior')
   s.floor(82.5,y+.14,132.5,3,6,'cloth','interior')
   s.screen(60,y,147,10);s.screen(98,y,158,10)
   s.map_table(85,y,145,13,7);s.books(62,y,168,10,4.8);s.books(91,y,169,12,4.8)
   for xx in [62,98]:s.desk(xx,y,140,7,2);s.stool(xx+3,y,143.5)
   # 东侧四格高的取卷夹廊连接来使高廊，仍属于首层大空间。
   s.floor(103,14.05,145,8,19,'floor','interior');s.rail((103,14,145),(103,14,149),height=1.1);s.rail((103,14,152.5),(103,14,164),height=1.1)
   for px,pz in [(104,146),(110,146),(104,163),(110,163)]:s.column(px,10,pz,4,.55)
   for k in range(8):s.box(101,10+k*.5,146+k*.65,2.5,.5,.68,'wood','interior')
   for px in [65,77,91,103]:s.lantern(px,y+6,134,ceiling=y+h-.5);s.lantern(px,y+7,169,ceiling=y+h-.5)
   s.sign('天枢总阁',84,20.1,130.9,10,2)
  elif i==1:
   for xx,zz in [(63,139),(83,139),(93,154),(63,163)]:s.desk(xx,y,zz,7.2,2.6);s.stool(xx+3.5,y,zz+3.7)
   s.books(83,y,168,14,3.3)
   for px,pz in [(66,140),(103,140),(91,165)]:s.lantern(px,y+6,pz,ceiling=y+h-.5)
  elif i==2:
   for xx,zz,ww in [(66,141,12),(86,141,14),(66,149,7),(87,150,13),(81,165,19)]:s.books(xx,y,zz,ww,5)
   s.box(91,y,159,8,2.2,4,'wood_dark','interior')
   for xx in [92,96]:s.box(xx,y+.4,158.8,.2,1.7,.3,'bronze','interior')
   for px,pz in [(68,144),(99,144),(91,163)]:s.lantern(px,y+5,pz,ceiling=y+h-.5)
  elif i==3:
   s.council(87,y,149);s.books(84,y,165,13,3.2);s.desk(69,y,141,6,2.5)
   for px,pz in [(70,143),(97,143),(94,161)]:s.lantern(px,y+6,pz,ceiling=y+h-.5)
  else:
   s.body(81,y,154,12,8,5.8,'envoy','archive',True,False,False)
   s.desk(85,y,159,5.4,1.9);s.books(82,y,161,8,3.3)
   for a,b in [((70,y,142),(98,y,142)),((70,y,142),(70,y,164)),((98,y,142),(98,y,164))]:s.rail(a,b,height=1.15)
   s.lantern(77,y+5,143,ceiling=y+h-.5);s.lantern(94,y+5,143,ceiling=y+h-.5)
 # 下层门廊独立挑出，十二格深度中的门、柱和梁有明确前后关系。
 for px in [61,72.5,95.5,107]:
  s.column(px,10,127,8.7,1.2,True);s.dougong(px,18.1,127,1.35,style='core')
 s.roof(58.5,19,125.7,51,8.8,'lean',4.8,2.2)
 s.sign('千机阁',84,16.3,125.4,9,1.7)
 # 西抱厦处理值事房，东抱厦处理议事露台；形状、标高与进深不同。
 s.floor(50,25,145,12,14,'floor');s.body(51,25,146,10,12,6.2,'office','envoy',True,east_entry=True)
 s.roof(50.5,31.7,145.5,11,13,'xieshan',4,1.8)
 for px,pz in [(52,146),(60,156)]:s.beam([px+3,21,pz],[px,25,pz],.8,.8,'wood_dark','bracket')
 s.floor(99,51,145,11,10,'floor');s.body(100,51,146,9,8,5.5,'council','envoy',True)
 s.roof(99.5,56.9,145.5,10,9,'hip',4,1.8)
 s.rail((99,51,144),(110,51,144),height=1.3)
 s.part='west_rear'
 # 两座后侧楼的高度、屋顶和用途各自安排，形成组合体块。
 s.terrace(42,150,14,23,10);s.stairs(49,144,6,10,6,.7)
 s.body(43,10,151,12,21,8.1,'archive','archive',True)
 s.body(44,18.5,152,10,19,6.4,'office','archive',False,floor=False)
 s.room_floor(44,18.5,152,10,19,[45,154,5.5,8]);s.internal_stair(45,154,10,18.5)
 s.roof(43.5,25.4,151.5,11,20,'gable',4.5,1.5)
 s.part='east_rear'
 s.terrace(113.5,149.5,14,22,10)
 s.body(115,10,151,11,18,6.8,'envoy','office',False,True)
 s.roof(114,17.4,150,13,20,'hip',5.2,1.7)
 s.front_module(120.5,177,180,lambda:s.stairs(0,0,6,10,6,.7))
 s.person(84,10,128,'wood_red')

def gallery(s,a,b,y,width=4,omit_supports=()):
 x,z=a;xx,zz=b
 if abs(xx-x)<.01:
  lo,hi=sorted([z,zz]);s.front_module(x-width/2,hi,90,lambda:gallery(s,(0,width/2),(hi-lo,width/2),y,width,omit_supports));return
 lo,hi=sorted([x,xx]);length=hi-lo
 s.floor(lo,y,z-width/2,length,width,'floor','gallery')
 s.rail((lo,y,z-width/2),(hi,y,z-width/2),height=1.15)
 s.rail((lo,y,z+width/2),(hi,y,z+width/2),height=1.15)
 for i in range(max(1,math.ceil(length/6))+1):
  p=lo+length*i/max(1,math.ceil(length/6))
  for side in [-1,1]:
   s.column(p,y,z+side*(width/2-.25),4.4,.45)
   if y>8 and i not in omit_supports:
    pz=z+side*(width/2-.25)
    s.box(p-.9,6,pz-.9,1.8,1.2,1.8,'stone','gallery')
    s.box(p-.7,7.2,pz-.7,1.4,1.3,1.4,'carved','gallery')
    s.box(p-.48,8.5,pz-.48,.96,y-8.5,.96,'wood_dark','gallery')
    s.box(p-.58,y-3.5,pz-.58,1.16,.45,1.16,'bronze_dark','gallery')
    s.beam([p,y-4,pz],[p+min(2,length/4),y-.2,pz],.6,.65,'wood_red','gallery')
 s.roof(lo,y+4.6,z-width/2,length,width,'gable',2,1.2)

def gardens_and_galleries(s):
 s.part='garden'
 # 西松庭恢复了上一版的独立内院，并在高廊下保留地面路径。
 s.floor(13,6.08,142,29,31,'moss','nature')
 for x,z,w,d in [(14,143,28,2.8),(14,169,29,2.8),(14,143,2.8,29),(39,143,3,29)]:s.floor(x,6.17,z,w,d,'paving','ground')
 s.pine(23,6.2,154,12,19);s.pine(34,6.2,165,8,71);s.rock(32,6.2,151,1.15)
 s.box(17,6.2,160,10,.6,1.8,'stone_dark','detail');s.box(16.8,6.8,159.8,10.4,.3,2.2,'wood','detail')
 for z in [147,155,163]:s.bamboo(11.7,6.2,z,6+(z%2))
 gallery(s,(39.5,124),(48,124),22.5,4)
 gallery(s,(48,124),(48,145),22.5,4,omit_supports=(1,))
 for x in [46.25,49.75]:
  s.box(x-.5,20.8,141,1,1.2,4,'wood_dark','gallery')
  s.beam([x,17.5,141.4],[x,21.8,145.5],.8,.8,'wood_red','gallery')
 s.front_module(48,147,90,lambda:s.stairs(2,0,22.5,25,4,.7,foundation=False))
 gallery(s,(53,145),(60,145),25,4)
 # 东高廊接入主阁首层夹廊和来使院二层，标高低于西侧。
 gallery(s,(110,153),(119,153),14,3.6)
 gallery(s,(119,131),(119,153),14,3.6)
 gallery(s,(119,131),(140,131),14,3.6)
 gallery(s,(140,126),(140,131),14,3.6)
 # 前院通向档院与机造院的低檐步廊各自转折。
 gallery(s,(47,48),(47,64),0,3.8)
 gallery(s,(117,50),(117,63),0,3.8)
 for x,z,y in [(48,101,3),(119,108,3),(60,116,6),(109,116,6),(17,143,6),(39,171,6)]:s.lantern(x,y+2.8,z,'post')
 # 小型景石与地被有固定位置，避免把花园做成均匀的装饰点阵。
 s.rock(57,6.2,115,.75);s.pine(65,6.2,117,8,12)
 s.rock(107,6.2,116,.6);s.bamboo(113,6.2,120,7)
 s.person(30,6.2,169,'cloth');s.person(46,22.5,137,'robe');s.person(138,6.5,150,'wood_red')

def build_scene(scene_type=Scene):
 from .infill import build_scene as current_scene
 return current_scene(scene_type)
