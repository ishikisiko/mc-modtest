"""Deterministic cuboid armor, nearest-pixel textures, BBModel and project-contract exports.
No image generation, smooth meshes, downloaded skins, or runtime registration.
Run: python src/build.py [--out PATH]
"""
from __future__ import annotations
import argparse,base64,copy,hashlib,io,json,math,uuid
from pathlib import Path
from PIL import Image,ImageDraw
from catalog import DATA,REALMS
ROOT=Path(__file__).resolve().parents[1]
BASE='fdd3ba3e5227b7b543dfb29cfe5e8362beea6df5'
BONES={'head':[0,0,0],'body':[0,0,0],'right_arm':[-5,2,0],'left_arm':[5,2,0],'right_leg':[-1.9,12,0],'left_leg':[1.9,12,0]}
POSES={'standing':{},'walk':{'right_arm':{'rotation':[-28,0,0]},'left_arm':{'rotation':[28,0,0]},'right_leg':{'rotation':[38,0,0]},'left_leg':{'rotation':[-38,0,0]}},'raised':{'right_arm':{'rotation':[-110,0,-12]},'left_arm':{'rotation':[-18,0,12]},'head':{'rotation':[-12,-15,0]}},'crouch':{'body':{'rotation':[28.64789,0,0],'position':[0,3.2,0]},'head':{'position':[0,4.2,0]},'right_arm':{'rotation':[22.91831,0,0],'position':[-5,5.2,0]},'left_arm':{'rotation':[22.91831,0,0],'position':[5,5.2,0]},'right_leg':{'position':[-1.9,12.2,4]},'left_leg':{'position':[1.9,12.2,4]}}}
FACES=['up','down','east','north','west','south']
CORNERS=[[(1,0,1),(0,0,1),(0,0,0),(1,0,0)],[(1,1,0),(0,1,0),(0,1,1),(1,1,1)],[(0,0,0),(0,0,1),(0,1,1),(0,1,0)],[(1,0,0),(0,0,0),(0,1,0),(1,1,0)],[(1,0,1),(1,0,0),(1,1,0),(1,1,1)],[(0,0,1),(1,0,1),(1,1,1),(0,1,1)]]
def world(p):return [-p[0],24-p[1],-p[2]]
def native(p):return [-p[0],24-p[1],-p[2]]
def rounded(v):return [round(x,6) for x in v]
def box_faces(b):
 faces=cube_faces(b['size'],b['uv'])
 for k,r in b.get('face_uvs',{}).items():faces[int(k)]=(faces[int(k)][0],r)
 return faces
def cube_faces(size,uv,density=4):
 w,h,d=[x*density for x in size];u,v=uv
 rects=[[u+d,v,u+d+w,v+d],[u+d+w,v+d,u+d+2*w,v],[u,v+d,u+d,v+d+h],[u+d,v+d,u+d+w,v+d+h],[u+d+w,v+d,u+2*d+w,v+d+h],[u+2*d+w,v+d,u+2*d+2*w,v+d+h]]
 return list(zip(CORNERS,rects))
class Model:
 def __init__(self,s):self.s=s;self.boxes=[]
 def box(self,name,bone,center,size,mat='metal',pattern='plain',slot='chest',rot=None,pivot=None,fx=False):
  n=native(center);b=BONES.get(bone,[0,0,0]);lo=[n[i]-size[i]/2-b[i] for i in range(3)]
  box={'name':name,'bone':bone,'from':rounded(lo),'size':list(size),'material':mat,'pattern':pattern,'slot':slot}
  if rot:box['rotation']=rot;box['pivot']=rounded([native(pivot or center)[i]-b[i] for i in range(3)])
  if 'arm' in bone:box['fit']='arm'
  if fx:box['fx']=True
  self.boxes.append(box);return box
 def line(self,name,a,b,width=.5,depth=.5,mat='trim',fx=True):
  dx,dy,dz=[b[i]-a[i] for i in range(3)]
  # This authoring helper is intentionally restricted to the x/y plane.
  assert abs(dz)<1e-6
  length=math.hypot(dx,dy);angle=-math.degrees(math.atan2(dx,dy))
  return self.box(name,'body',[(a[i]+b[i])/2 for i in range(3)],[width,length,depth],mat,'plain','chest',[0,0,angle],fx=fx)
 def path(self,name,pts,width=.5,mat='trim',fx=True):
  for i,(a,b) in enumerate(zip(pts,pts[1:])):self.line(f'{name}_{i}',a,b,width,width,mat,fx)

def make_armor(s):
 m=Model(s);level=s['level'];kind=s['kind'];heavy=kind=='armor';main='metal' if heavy else 'cloth'
 # Unmodified standard player dimensions, original neutral pixel skin.
 m.box('展示头部','head',[0,28,0],[8,8,8],'skin','face','skin')
 m.box('展示躯干','body',[0,18,0],[8,12,4],'skin','under','skin')
 for sign,bone in [(1,'right_arm'),(-1,'left_arm')]:m.box('展示手臂_'+bone,bone,[6*sign,18,0],[4,12,4],'skin','hand','skin')
 for sign,bone in [(1,'right_leg'),(-1,'left_leg')]:m.box('展示腿_'+bone,bone,[1.9*sign,6,0],[4,12,4],'skin','under','skin')
 # One continuous torso; texture, not strips of geometry, carries the crossed collar.
 m.box('完整主胸衣','body',[0,18,0],[8.5,12.5,4.5],main,'torso')
 m.box('完整腰封','body',[0,13,0],[8.75,1.5,4.75],'dark','belt')
 m.box('腰心灵髓','body',[0,13,2.625],[1.5,1.25,.5],'gem','gem')
 if kind=='armor':m.box('胸心低凸嵌印','body',[0,18.5,2.5],[1.75,2,.5],'gem','gem')
 # Arms retain square shoulders and vanilla joints. Wide cuffs are not tapered cylinders.
 for sign,bone in [(1,'right_arm'),(-1,'left_arm')]:
  x=sign*6
  m.box('连续整袖_'+bone,bone,[x,19,-.125],[4.5,10.5,4.5],'cloth','sleeve')
  cuff=5.5 if s['sleeve']=='wide' else 6 if s['sleeve']=='bell' else 4.75
  cm='metal' if s['sleeve']=='bracer' else 'inner'
  m.box('完整方袖口_'+bone,bone,[x+sign*(cuff-4.5)/2,15,0],[cuff,3,4.75],cm,'cuff')
  sh=s['shoulder'];width,height= {'small':(4.75,1.25),'rise':(5.5,1.75),'asym':(5,1.5),'collar':(5,1.25),'broad':(6,2.5),'lotus':(4.75,1.25),'wing':(7,1.75),'massive':(6.5,3)}[sh]
  shoulder_mat='inner' if sh=='asym' and sign==1 else 'metal'
  m.box('完整肩壳_'+bone,bone,[x+sign*(width-4.5)/2,24,0],[width,height,5],shoulder_mat,'shoulder',rot=[0,0,-sign*22.5] if sh in ('wing','rise') else None)
  if sh in ('rise','wing'):
   m.box('局部肩尖_'+bone,bone,[x+sign*(width-2.25),25.25,0],[1.25,1.5,3],'trim','plain',rot=[0,0,-sign*22.5])
  if sh=='lotus':
   for z in [-1.5,1.5]:m.box('局部莲领_'+bone+str(z),bone,[sign*4.75,24.75,z],[2.75,1.5,2],'metal','petal',rot=[0,0,sign*22.5])
  if sh=='collar':m.box('领端小玉_'+bone,bone,[sign*7.5,24.75,1],[1,1,.75],'gem','gem')
 # Native leg pivots, no waist slimming or lengthening. Robe panels belong to each leg.
 for sign,bone in [(1,'right_leg'),(-1,'left_leg')]:
  x=1.9*sign
  m.box('完整内腿_'+bone,bone,[sign*2.125,6.5,0],[4,11.5,4.25],'inner' if not heavy else 'cloth','pant','legs')
  h=s['hem'];width=4.25
  # Complete panels with no horizontal armor banding. A narrow centre seam allows motion.
  m.box('完整前袍_'+bone,bone,[sign*2.25,12-h/2,2.625],[width,h,.75],'cloth','robe_R' if sign==1 else 'robe_L','legs')
  m.box('完整侧袍_'+bone,bone,[sign*4.625,12-h/2,-.125],[.75,h,5],'cloth','robe_side','legs')
  if heavy:
   m.box('局部护膝_'+bone,bone,[x,5.5,2.5],[3.5,2.5,.75],'metal','knee','legs')
  m.box('完整靴筒_'+bone,bone,[sign*2.25,2.5,0],[4,5,4.5],'metal','boot','feet')
  m.box('方靴脚背_'+bone,bone,[sign*2.25,.875,2.375],[4,1.75,1],'metal','toe','feet')
  m.box('方靴底_'+bone,bone,[sign*2.25,.125,.375],[4.25,.5,5.25],'dark','plain','feet')
 # Back cloth: clear large contiguous pieces, no shredded ribbons.
 back=s['back'];end=3 if back in ('long','tails','asym') and level>=2 else 8 if back=='asym' else 13
 if back=='tails':
  for sign in [-1,1]:m.box('完整后披帛_'+str(sign),'body',[sign*4.5,(22+end)/2,-3.5],[3,22-end,.5],'cloth','cape',rot=[0,0,sign*5])
 elif back=='asym':m.box('一幅偏侧长披','body',[2.75,(23+end)/2,-3.25],[6,23-end,.5],'cloth','cape')
 else:m.box('完整后披','body',[0,(23+end)/2,-2.875],[8,23-end,.5],'cloth','cape')
 # Crown is always square and face-open. Three actual bands, not a rounded head shell.
 m.box('整额冠梁','head',[0,31.25,4.25],[8.75,1.5,.5],'metal','crown','head')
 for sign in [-1,1]:m.box('方冠侧梁_'+str(sign),'head',[sign*4.25,31.25,.125],[.5,1.5,8.75],'metal','crown','head')
 m.box('方冠后梁','head',[0,31.25,-4.25],[8.5,1.5,.5],'metal','crown','head')
 m.box('额心方玉','head',[0,31.25,4.625],[1.5,1.25,.5],'gem','gem','head')
 crown=s['crown'];height=2+level*.5
 if crown in ('gate','twin'):
  for sign in [-1,1]:m.box('冠双峰_'+str(sign),'head',[sign*1.75,32+height/2,2],[1.5,height,1.25],'metal','crown','head',rot=[0,0,-sign*12] if crown=='twin' else None)
  if crown=='gate':m.box('阙冠顶梁','head',[0,32+height,2],[5,1,1.25],'trim','plain','head')
 elif crown=='bar':m.box('横衡玉冠','head',[0,32.75,1.5],[7.5,1.5,1.5],'metal','crown','head')
 elif crown=='side':m.box('偏峰玉冠','head',[2.5,33.25,2],[1.5,3,1.25],'metal','crown','head',rot=[0,0,-22.5])
 elif crown=='crystal':
  m.box('星阙方晶','head',[0,34.5,1.75],[1.5,5,1.5],'gem','gem','head')
  for sign in [-1,1]:m.box('星阙低梁_'+str(sign),'head',[sign*2,32.5,2],[1.25,2,1.25],'trim','plain','head')
 elif crown in ('three','phoenix','lotus'):
  for sign in [-1,0,1]:
   h=height if sign==0 else height*.55
   m.box('冠峰_'+str(sign),'head',[sign*2.5,32+h/2,2],[1.5,h,1.5],'metal','crown','head',rot=[0,0,-sign*22.5] if crown=='phoenix' else None)
 else:m.box('短冠顶','head',[0,32.75,2],[3,1.5,1.25],'metal','crown','head')

 # Set-specific, large-form differences. Each addition remains a contiguous cuboid.
 sid=s['id']
 if sid in ('xueque','tianlv','xuanming'):
  m.box('完整后立领','body',[0,24,-2.625],[8.5,3,.75],'metal','crown')
  for sign in [-1,1]:m.box('立领侧角'+str(sign),'body',[sign*4.25,24,-1.5],[.75,3,2.5],'metal','crown')
 if sid=='biheng':m.box('一体玉肩领','body',[0,23.75,-.375],[10,1.25,5.5],'metal','shoulder')
 if sid=='zhaoye':m.box('偏肩银披','right_arm',[6.5,22.5,-.375],[5.75,3.5,5.25],'inner','sleeve')
 if sid in ('cangming','taiyin','xingque'):
  m.box('侧垂整幅披帛','left_arm',[-9,18,-1.5],[2.5,11,.5],'inner','cape',rot=[0,0,-12])
 if sid=='wuxiang':
  m.box('太虚整护胸','body',[0,20,2.5],[7,5,.5],'metal','plain')
 if sid=='xingque':m.box('肩端方晶','right_arm',[9,25,0],[1.25,2.5,1.25],'gem','gem',rot=[0,0,-22.5])
 # Optional divine silhouettes use straight cuboids, flat pixels, and intentional negative space.
 a=s['aura'];z=-5.25
 if a=='sun':m.path('日轮折弧',[[-6,24,z],[-6,29,z],[0,34,z],[6,29,z],[6,24,z]],.5,'trim')
 elif a=='tide':m.path('潮法弧',[[-11,12,z],[-14,21,z],[-10,30,z],[-2,35,z],[6,34,z]],.5,'gem')
 elif a=='lotus':m.path('方莲法轮',[[0,34,z],[8,27,z],[5,21,z],[0,18,z],[-5,21,z],[-8,27,z],[0,34,z]],.5,'trim')
 elif a=='thunder':m.path('天律雷印',[[3,36,z],[-2,29,z],[2,29,z],[-3,21,z]],.75,'gem')
 elif a=='moon':m.path('像素弦月',[[2,36,z],[-6,35,z],[-12,29,z],[-14,21,z],[-10,13,z],[-4,10,z],[-8,17,z],[-9,24,z],[-5,31,z],[2,36,z]],.75,'trim')
 elif a=='phoenix':
  for sign in [-1,1]:m.path('凰仪法翼'+str(sign),[[sign*3,18,z],[sign*10,23,z],[sign*15,31,z],[sign*12,29,z],[sign*10,34,z]],.75,'trim')
 elif a=='seal':
  m.path('镇渊方印',[[-10,16,z],[-10,34,z],[10,34,z],[10,16,z],[-10,16,z]],.75,'trim')
  m.path('渊印上篆',[[-5,30,z],[5,30,z],[5,27,z],[-5,27,z]],.5,'gem')
 elif a=='origin':
  m.path('太初外天框',[[-7,36,z],[7,36,z],[13,30,z],[13,18,z],[7,12,z],[-7,12,z],[-13,18,z],[-13,30,z],[-7,36,z]],.5,'trim')
  m.path('太初内菱印',[[0,35,z-.25],[10,25,z-.25],[0,15,z-.25],[-10,25,z-.25],[0,35,z-.25]],.5,'gem')
 elif a=='star':
  pts=[[-15,19,z],[-9,31,z],[4,37,z],[15,31,z],[9,19,z],[-4,13,z],[-15,19,z]];m.path('星阙轨',pts,.5,'trim')
  for i,p in enumerate(pts[:-1]):m.box('轨道星髓'+str(i),'body',p,[1.25,1.25,.75],'gem','gem',fx=True)
 elif a=='void':
  m.path('太虚左断框',[[-4,37,z],[-12,32,z],[-15,21,z],[-9,11,z]],.75,'metal')
  m.path('太虚右断框',[[4,13,z],[12,18,z],[15,29,z],[9,38,z]],.75,'trim')
 if level>=4:
  for sign in [-1,1]:m.box('腰侧小佩'+str(sign),'body',[sign*4.9,11.75,1.75],[.75,2.5,.75],'gem','gem')
 return m.boxes

def make_ingot(s):
 m=Model(s)
 # The flat top follows the project's FLAT_TOP projected silhouette, inverted
 # from its fixed isometric view. Each touching row is one slab, not floating plates.
 pts=[(3,17),(9,10),(24,8),(29,14),(23,18),(7,22)]
 # Use the canonical six-point top as a flat plan. Icons below preserve the exact 32px mask.
 coords=[((u-16)*.5, -(v-15)*.5) for u,v in pts]
 mn=min(p[1] for p in coords);mx=max(p[1] for p in coords);step=.5;z=math.floor(mn/step)*step;i=0
 while z<mx:
  mid=z+step/2;xs=[]
  for a,b in zip(coords,coords[1:]+coords[:1]):
   if (a[1]>mid)!=(b[1]>mid):xs.append(a[0]+(mid-a[1])*(b[0]-a[0])/(b[1]-a[1]))
  if len(xs)>=2:
   left=round(min(xs)*2)/2;right=round(max(xs)*2)/2
   if right>left:m.box('同一扁锭连续体'+str(i),'body',[(left+right)/2,1,z+step/2],[right-left,2,step],'metal','ingot','ingot')
  z+=step;i+=1
 # Adjacent slabs share a single planar pixel texture; internal seams are never painted.
 return m.boxes

def rgb(c):return tuple(int(c[i:i+2],16) for i in (1,3,5))
def blend(a,b,f):return tuple(round(x*(1-f)+y*f) for x,y in zip(a,b))
def palette(s):
 a,b,c,d,e=map(rgb,s['colors'])
 return {'metal':a,'cloth':b,'inner':c,'trim':d,'gem':e,'dark':(30,36,45),'skin':(188,157,128)}
def glyph(draw,kind,cx,cy,r,col,width=1):
 r=max(2,int(r));cx=int(cx);cy=int(cy)
 def line(pts):draw.line([(cx+int(x*r),cy+int(y*r)) for x,y in pts],fill=col,width=width)
 if kind in ('wave','leaf'):
  line([(-1,.3),(-.6,.3),(-.6,-.2),(-.1,-.2),(-.1,-.65),(.5,-.65),(.5,-.15),(1,-.15)])
  if kind=='wave':line([(-.8,.8),(-.3,.8),(-.3,.35),(.3,.35),(.3,.05)])
 elif kind in ('flame','phoenix'):
  line([(-.8,.7),(-.8,0),(-.4,.2),(-.4,-.45),(0,-1),(0,-.25),(.5,-.65),(.5,.3),(.8,0),(.8,.7),(-.8,.7)])
 elif kind=='moon':
  line([(.6,-1),(-.25,-1),(-.8,-.5),(-.8,.5),(-.25,1),(.6,1),(.1,.5),(.1,-.5),(.6,-1)])
 elif kind=='thunder':line([(.45,-1),(-.5,.1),(.25,.1),(-.5,1)])
 elif kind=='lotus':line([(-1,.25),(-.5,.25),(-.5,-.4),(0,-1),(.5,-.4),(.5,.25),(1,.25),(.6,.8),(-.6,.8),(-1,.25)])
 elif kind=='star':
  line([(-1,0),(-.3,-.3),(0,-1),(.3,-.3),(1,0),(.3,.3),(0,1),(-.3,.3),(-1,0)])
 elif kind=='spire':line([(-1,.8),(-1,-.4),(-.3,-.4),(-.3,-1),(.3,-1),(.3,-.4),(1,-.4),(1,.8)]);line([(-.25,.8),(-.25,.1),(.25,.1),(.25,.8)])
 else:
  line([(-1,-1),(1,-1),(1,1),(-1,1),(-1,-1)])
  if kind in ('origin','void'):line([(0,-.7),(.7,0),(0,.7),(-.7,0),(0,-.7)])
  if kind=='seal':line([(-.5,-.4),(.5,-.4),(.5,.4),(-.5,.4)])

def face_texture(s,b,face,w,h):
 # 4 texels / model unit, painted on 2-texel clusters. No blur or photo textures.
 W=max(1,math.ceil(w/2));H=max(1,math.ceil(h/2));p=palette(s);base=p[b['material']]
 shade=blend(base,(8,17,27),.23);light=blend(base,(255,249,222),.22);trim=p['trim'];ink=p['gem'];inner=p['inner']
 im=Image.new('RGBA',(W,H),base+(255,));d=ImageDraw.Draw(im);pat=b['pattern'];isfront=face==3;isback=face==5
 if pat=='face':
  if face==0: d.rectangle((0,0,W,H),fill=(39,38,40))
  elif face in (2,4,5):
   d.rectangle((0,0,W,H*.72),fill=(42,40,44));d.rectangle((0,0,W,2),fill=(61,53,52))
  elif isfront:
   d.rectangle((0,0,W,max(1,int(H*.2))),fill=(39,38,40));d.rectangle((0,H*.15,W*.15,H*.65),fill=(45,41,43));d.rectangle((W*.86,H*.15,W,H*.65),fill=(45,41,43))
   ey=int(H*.48)
   for u in [.27,.67]:
    x=int(W*u);d.rectangle((x,ey,x+2,ey+1),fill=(225,225,214));d.rectangle((x+1,ey,x+1,ey+1),fill=(58,68,75));d.line((x,ey-2,x+2,ey-2),fill=(74,59,51))
   d.rectangle((W*.44,H*.62,W*.56,H*.7),fill=(160,121,97));d.line((W*.39,H*.8,W*.6,H*.8),fill=(117,84,73))
 elif pat in ('under','hand'):
  d.rectangle((0,0,W,H*(.8 if pat=='hand' else 1)),fill=(57,69,77))
 else:
  if W>2 and H>2:
   # Flat shade bands, not per-surface PBR. Avoid outlining invisible ingot row seams.
   if pat not in ('ingot','ingotmark'):
    d.line((0,0,W-1,0),fill=light);d.line((0,0,0,H-1),fill=light);d.line((W-1,1,W-1,H-1),fill=shade);d.line((1,H-1,W-1,H-1),fill=shade)
   if b['material']=='metal' and pat not in ('ingot',):
    d.rectangle((1,1,max(1,W//5),max(1,H-2)),fill=blend(base,light,.5))
  if pat=='torso' and isfront:
   # Pixel cross-collar lies on ONE chest face, so there is no geometric patchwork.
   d.line([(int(W*.15),0),(int(W*.57),int(H*.34)),(int(W*.83),int(H*.12))],fill=inner,width=max(2,W//6))
   d.line([(int(W*.15),0),(int(W*.57),int(H*.34))],fill=trim,width=1)
   for x in [1,W-2]:d.line((x,H*.15,x,H*.88),fill=trim,width=1)
   if s['kind']=='armor':
    for side in [-1,1]:
     cx=W//2+side*int(W*.3);d.line((cx,H*.35,cx-side*2,H*.64),fill=light,width=1)
   if s['kind']!='armor':
    glyph(d,s['motif'],W/2,H*.55,min(W*.2,4),trim)
    if s['level']>=3:d.line((W//2,H*.65,W//2,H*.84),fill=trim)
   if s['level']>=3:
    for cx in [W*.22,W*.78]:glyph(d,s['motif'],cx,H*.72,min(W,H)*.11,trim)
  elif pat=='torso' and isback:
   glyph(d,s['motif'],W/2,H*.35,min(W,H)*.23,trim)
   d.line((W*.5,H*.56,W*.5,H*.9),fill=shade)
  elif pat in ('robe_R','robe_L','robe_side','cape'):
   if face in (3,5) and W>4:
    
    if pat in ('robe_R','cape','robe_side'):d.line((1,1,1,H-2),fill=trim)
    if pat in ('robe_L','cape','robe_side'):d.line((W-2,1,W-2,H-2),fill=trim)
    d.rectangle((2,H-3,W-3,H-2),fill=trim)
    d.rectangle((max(3,W//3),2,max(3,W//3)+1,H-4),fill=blend(base,shade,.4))
    if H>12:
     glyph(d,s['motif'],W/2,H-7,min(3,W*.22),ink if s['level']>=4 else trim)
    if pat=='cape' and H>22:glyph(d,s['motif'],W/2,H*.3,min(W*.26,5),trim)
  elif pat in ('cuff','boot','toe','shoulder','crown','knee','petal'):
   if W>2 and H>3:
    d.line((1,1,W-2,1),fill=trim);d.line((1,H-2,W-2,H-2),fill=trim)
    if isfront and H>6 and W>6:glyph(d,s['motif'],W/2,H*.5,min(W,H)*.23,trim)
  elif pat in ('sigil','ingotmark'):
   if (isfront and pat=='sigil') or (face==0 and pat=='ingotmark'):glyph(d,s['motif'],W/2,H/2,min(W,H)*.35,trim)
  elif pat=='belt' and isfront:
   d.line((0,1,W-1,1),fill=trim);d.line((0,H-2,W-1,H-2),fill=trim)
  elif pat=='gem':
   d.rectangle((0,0,W//2,H//2),fill=blend(base,(255,255,242),.45));d.rectangle((W//2,H//2,W,H),fill=blend(base,(26,60,70),.3))
 return im.resize((w,h),Image.Resampling.NEAREST)

def fit_box(b,profile):
 b=copy.deepcopy(b)
 if profile=='slim' and b.get('fit')=='arm':
  anchor=1 if b['bone']=='right_arm' else -1
  b['from'][0]=anchor+(b['from'][0]-anchor)*.75;b['size'][0]*=.75
  if 'pivot' in b:b['pivot'][0]=anchor+(b['pivot'][0]-anchor)*.75
 return b

def compile_model(s,armor,ingot):
 atlas=Image.new('RGBA',(512,512));x=y=2;row=0;cache={};profiles={}
 for profile in ['standard','slim']:
  arr=[]
  for src in armor+ingot:
   b=fit_box(src,profile);key=(tuple(b['size']),b['material'],b['pattern'])
   if key not in cache:
    w,h,d=[v*4 for v in b['size']];ww=math.ceil(2*(w+d))+2;hh=math.ceil(h+d)+2
    if x+ww>=512:x=2;y+=row+2;row=0
    if y+hh>=512:raise ValueError(f'Atlas overflow {s["id"]} {y+hh}')
    uv=[x,y];cache[key]=uv
    for fi,(_,r) in enumerate(cube_faces(b['size'],uv)):
     u0,v0,u1,v1=r;left,top=math.floor(min(u0,u1)),math.floor(min(v0,v1));W=max(1,math.ceil(max(u0,u1))-left);H=max(1,math.ceil(max(v0,v1))-top)
     img=face_texture(s,b,fi,W,H)
     if u1<u0:img=img.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
     if v1<v0:img=img.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
     atlas.paste(img,(left,top))
    x+=ww+2;row=max(row,hh)
   b['uv']=cache[key];arr.append(b)
  profiles[profile]=arr

 # Shared 64x32 top tile, aligned in world x/z across all touching ingot slabs.
 assert y+row<474, 'Reserved top tile would overlap atlas'
 top=Image.new('RGBA',(64,32),palette(s)['metal']+(255,));td=ImageDraw.Draw(top);base=palette(s)['metal'];trim=palette(s)['trim']
 td.polygon([(0,0),(30,0),(10,32),(0,32)],fill=blend(base,(255,251,226),.24))
 td.polygon([(60,0),(64,0),(64,32),(42,32)],fill=blend(base,(28,32,44),.18))
 glyph(td,s['motif'],32,16,9,trim,2)
 top=top.resize((32,16),Image.Resampling.NEAREST).resize((64,32),Image.Resampling.NEAREST)
 atlas.paste(top,(2,478))
 for boxes in profiles.values():
  for b in boxes:
   if b['slot']!='ingot':continue
   nb=BONES[b['bone']];lo=[b['from'][i]+nb[i] for i in range(3)];hi=[lo[i]+b['size'][i] for i in range(3)]
   # quad top: x-min,z-min -> x-max,z-min -> x-max,z-max -> x-min,z-max
   xmin,xmax=-hi[0],-lo[0];zmin,zmax=-hi[2],-lo[2]
   b['face_uvs']={'0':[2+(xmax+8)*4,478+(zmin+4)*4,2+(xmin+8)*4,478+(zmax+4)*4]}
 buf=io.BytesIO();atlas.save(buf,format='PNG',optimize=True)
 return {'id':s['id'],'brief':s,'profiles':profiles,'texture':'data:image/png;base64,'+base64.b64encode(buf.getvalue()).decode(),'texture_size':512,'texel_density':4},atlas

def rotate(p,r):
 x,y,z=p
 for axis,angle in enumerate(r):
  a=math.radians(angle);c=math.cos(a);s=math.sin(a)
  if axis==0:y,z=y*c-z*s,y*s+z*c
  elif axis==1:x,z=x*c+z*s,-x*s+z*c
  else:x,y=x*c-y*s,x*s+y*c
 return [x,y,z]
def corners_world(b):
 bone=BONES.get(b['bone'],[0,0,0]);pv=b.get('pivot',[0,0,0]);out=[]
 for cr,rect in box_faces(b):
  vs=[]
  for v in cr:
   p=[b['from'][i]+b['size'][i]*v[i] for i in range(3)]
   if 'rotation' in b:p=[a+c for a,c in zip(rotate([p[i]-pv[i] for i in range(3)],b['rotation']),pv)]
   vs.append(world([p[i]+bone[i] for i in range(3)]))
  u0,v0,u1,v1=rect;out.append((vs,[(u1,v0),(u0,v0),(u0,v1),(u1,v1)]))
 return out

def uid(text):return str(uuid.uuid5(uuid.NAMESPACE_URL,'mc-modtest:'+text))
def bbmodel(model,slot='armor',profile='standard'):
 s=model['brief'];elements=[];groups={bone:[] for bone in BONES}
 for b in model['profiles'][profile]:
  if slot=='ingot' and b['slot']!='ingot':continue
  if slot=='armor' and b['slot'] in ('skin','ingot'):continue
  bone=BONES[b['bone']];low=[b['from'][i]+bone[i] for i in range(3)];hi=[low[i]+b['size'][i] for i in range(3)]
  loW=world(hi);hiW=world(low);pivot=world([b.get('pivot',[0,0,0])[i]+bone[i] for i in range(3)])
  eid=uid(s['id']+b['name']+profile);faces={}
  # Native axes are inverted in the display frame. UV conversion keeps the same pixels.
  native_to_world={'up':'up','down':'down','east':'east','west':'west','north':'south','south':'north'}
  for fi,(_,rect) in enumerate(box_faces(b)):
   u0,v0,u1,v1=rect;faces[native_to_world[FACES[fi]]]={'uv':[u0,v0,u1,v1],'texture':0,'rotation':180 if fi in (0,1) else 0}
  elements.append({'name':b['name'],'type':'cube','uuid':eid,'from':rounded(loW),'to':rounded(hiW),'origin':rounded(pivot),'rotation':b.get('rotation',[0,0,0]),'shade':True,'box_uv':False,'faces':faces,'autouv':0,'color':s['level']})
  groups[b['bone']].append(eid)
 outliner=[{'name':bone,'origin':world(BONES[bone]),'rotation':[0,0,0],'uuid':uid(s['id']+bone+profile),'export':True,'isOpen':True,'children':children} for bone,children in groups.items() if children]
 return {'meta':{'format_version':'4.10','model_format':'free','box_uv':False},'name':s['name']+' · MC方块防具' if slot=='armor' else s['material']+'锭','model_identifier':'myvillage:mc_'+s['id'],'resolution':{'width':512,'height':512},'elements':elements,'outliner':outliner,'textures':[{'name':s['id']+'.png','id':'0','uuid':uid(s['id']+'texture'),'mode':'bitmap','source':model['texture'],'width':512,'height':512,'uv_width':512,'uv_height':512,'particle':True,'visible':True}],'animations':[]}

def project_spec(s,boxes):
 mapping=[('helmet','head','冠'),('chestplate','chest','胸衣'),('leggings','legs','护腿'),('boots','feet','靴')]
 keep=('name','bone','from','size','material','fit','rotation','pivot')
 return {'version':1,'kind':'armor_set','enabled':False,'id':'myvillage:mc_'+s['id']+'_armor','name':s['name']+' · MC设计稿','material':'minecraft:iron','durability_multiplier':15,'texture_size':512,'texel_density':4,'items':[{'id':'myvillage:mc_'+s['id']+'_'+k,'name':s['name']+label,'slot':slot,'boxes':[{a:b[a] for a in keep if a in b} for b in boxes if b['slot']==slot and not b.get('fx')]} for k,slot,label in mapping]}

def save_obj(model,dest,kind):
 boxes=[b for b in model['profiles']['standard'] if (b['slot']=='ingot' if kind=='ingot' else b['slot'] not in ('skin','ingot'))]
 lines=['# Minecraft pixel-unit cuboid design. Coordinates in blocks.','mtllib '+kind+'.mtl','usemtl pixel_atlas'];n=0
 for b in boxes:
  lines.append('g '+b['name'])
  for vs,uv in corners_world(b):
   for v in vs:lines.append('v '+' '.join(f'{a/16:.7f}' for a in v))
   for u,v in uv:lines.append(f'vt {u/512:.7f} {1-v/512:.7f}')
   lines.append('f '+' '.join(f'{n+i}/{n+i}' for i in [1,2,3,4]));n+=4
 (dest/(kind+'.obj')).write_text('\n'.join(lines),encoding='utf-8')
 (dest/(kind+'.mtl')).write_text('newmtl pixel_atlas\nKd 1 1 1\nKa 0.2 0.2 0.2\nKs 0 0 0\nd 1\nmap_Kd texture.png\n',encoding='utf-8')

def main():
 ap=argparse.ArgumentParser();ap.add_argument('--out',type=Path,default=ROOT);args=ap.parse_args();out=args.out;out.mkdir(parents=True,exist_ok=True)
 models=[];stats=[]
 for s in DATA:
  armor,ingot=make_armor(s),make_ingot(s);model,atlas=compile_model(s,armor,ingot);models.append(model)
  d=out/'models'/s['id'];d.mkdir(parents=True,exist_ok=True);atlas.save(d/'texture.png')
  icon=Image.new('RGBA',(32,32));pen=ImageDraw.Draw(icon);p=palette(s);pen.polygon([(3,17),(9,10),(24,8),(29,14),(23,23),(7,26),(3,22)],fill=blend(p['metal'],(18,25,33),.35));pen.polygon([(3,17),(23,15),(29,14),(25,22),(7,26),(3,22)],fill=blend(p['metal'],(18,25,33),.17));pen.polygon([(3,17),(9,10),(24,8),(29,14),(23,18),(7,22)],fill=blend(p['metal'],(255,248,220),.18));glyph(pen,s['motif'],16,15,3,p['trim']);icon.save(d/'ingot-icon.png')
  for profile in ['standard','slim']:(d/f'armor-{profile}.bbmodel').write_text(json.dumps(bbmodel(model,profile=profile),ensure_ascii=False,separators=(',',':')),encoding='utf-8')
  (d/'ingot.bbmodel').write_text(json.dumps(bbmodel(model,'ingot'),ensure_ascii=False,separators=(',',':')),encoding='utf-8')
  spec=project_spec(s,armor)
  for item in spec['items']:
   for box in item['boxes']:box['material']='mc_'+s['id']+'_'+box['material']
  (d/'armor.authored.json').write_text(json.dumps(spec,ensure_ascii=False,indent=2),encoding='utf-8')
  cols=palette(s);hexc=lambda c:'#'+''.join(f'{v:02X}' for v in c)
  mats={'mc_'+s['id']+'_'+k:{'pattern':'cloth' if k in ('cloth','inner','dark') else 'metal','base':hexc(c),'light':hexc(blend(c,(255,250,225),.22)),'dark':hexc(blend(c,(15,24,32),.25)),'noise_scale':0,'rivets':False,'stitches':False} for k,c in cols.items() if k!='skin'}
  (d/'materials.json').write_text(json.dumps(mats,ensure_ascii=False,indent=2),encoding='utf-8')
  keep=['name','bone','from','size','uv','pivot','rotation']
  compiled={'version':1,'id':spec['id'],'texture_size':512,'texel_density':4,'texture':'myvillage:textures/equipment/mc_'+s['id']+'.png','profiles':{profile:[{'id':item['id'],'name':item['name'],'slot':item['slot'],'boxes':[{k:b[k] for k in keep if k in b} for b in model['profiles'][profile] if b['slot']==item['slot'] and not b.get('fx')]} for item in spec['items']] for profile in ['standard','slim']}}
  (d/'armor.compiled.json').write_text(json.dumps(compiled,ensure_ascii=False,indent=2),encoding='utf-8')
  # This is the exact preview data, not a substituted high-poly mesh.
  (d/'model.json').write_text(json.dumps(model,ensure_ascii=False,separators=(',',':')),encoding='utf-8')
  for kind in ['armor','ingot']:save_obj(model,d,kind)
  stats.append({'id':s['id'],'armor_cuboids':sum(b['slot'] not in ('skin','ingot') for b in armor),'fx_cuboids':sum(bool(b.get('fx')) for b in armor),'smooth_meshes':0})
 for model in models:
  model['bbmodels']={p:bbmodel(model,profile=p) for p in ['standard','slim']}
  model['bbmodels']['ingot']=bbmodel(model,'ingot')
 data={'version':1,'base_commit':BASE,'bones':BONES,'poses':POSES,'realms':REALMS,'models':models}
 (out/'catalog.json').write_text(json.dumps(data,ensure_ascii=False,separators=(',',':')),encoding='utf-8')
 data['thumbnails']={s['id']:'data:image/png;base64,'+base64.b64encode((out/'previews'/(s['id']+'.png')).read_bytes()).decode() for s in DATA if (out/'previews'/(s['id']+'.png')).exists()}
 template=(ROOT/'src/viewer.html').read_text();script=(ROOT/'src/viewer.js').read_text()
 html=template.replace('/*__VIEWER__*/',script).replace('/*__DATA__*/',json.dumps(data,ensure_ascii=False,separators=(',',':')))
 (out/'index.html').write_text(html,encoding='utf-8')
 (out/'tests').mkdir(exist_ok=True);(out/'tests/build-report.json').write_text(json.dumps(stats,ensure_ascii=False,indent=2),encoding='utf-8')
 design=['# 仙铸 · Minecraft 方块风格防具十五式','', '本稿替代前一版写实人体草稿。全套按 MC 方块人比例设计，不是旧模型体素化。','', '主体完整，允许少量局部分片；整体不得全由碎片、鳞片或补丁组成。','', '头 8×8×8，躯干 8×12×4；标准臂 4×12×4，细臂 3×12×4。保持项目骨骼与姿态坐标。4纹素/模型单位，手绘色块按最近邻采样。','', '交付是三维美术设计与模型源，不是已注册可安装模组。数值为 authored schema 占位，法相不包含在运行时 authored 防具里。','']
 for s in DATA:
  design+=['## '+REALMS[s['level']-1]+' · '+s['name']+' / '+s['material']+'锭','',s['title'],'',s['idea'],'','**四件结构**','']+['- '+p for p in s['parts']]+['','**锭：**'+s['ingot'],'','**局部分片：**'+s['fragment'],'','**辨识与定位：**'+s['identity'],'','**色板：** '+' / '.join(s['colors']),'']
 (out/'DESIGN_BOOK.md').write_text('\n'.join(design),encoding='utf-8')
 print(json.dumps({'sets':len(models),'html_bytes':(out/'index.html').stat().st_size,'stats':stats},ensure_ascii=False))
if __name__=='__main__':main()
