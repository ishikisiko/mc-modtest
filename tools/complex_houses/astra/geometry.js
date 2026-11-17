/* 独立设计预览；单位为格，x 向东、z 向北。这里只构造网页几何。 */
(function(global){
'use strict';
const designs=[];
const hash=(x,z)=>{let n=Math.imul(x+173,374761393)^Math.imul(z+37,668265263);return ((n^(n>>>13))>>>0)/4294967295;};
function make(info,author){
 const d={...info,boxes:[],rooms:[],doors:[],stairs:[],roofs:[],courts:[],routes:[],supports:[]};
 let ctx={level:1,kind:'structure'};
 function group(meta,fn){const old=ctx;ctx={...ctx,...meta};fn();ctx=old;}
 function box(x0,x1,y0,y1,z0,z1,mat,meta={}){if(x1<=x0||y1<=y0||z1<=z0)return;d.boxes.push({x0,x1,y0,y1,z0,z1,mat,...ctx,...meta});}
 const slab=(x0,x1,z0,z1,y,mat='石砖',meta={})=>box(x0,x1,y-.5,y,z0,z1,mat,{kind:'floor',...meta});
 function platform(x0,x1,z0,z1,y,mat='石砖',top='云杉木板',meta={}){box(x0,x1,0,y-.5,z0,z1,mat,{kind:'foundation',...meta});slab(x0,x1,z0,z1,y,top,meta);}
 function door(a,b,y,h=3,id='',side=1){return {a,b,lo:y,hi:y+h,kind:'door',id,side};}
 const win=(a,b,y)=>({a,b,lo:y+1.5,hi:y+3.5,kind:'window'});
 function wall(axis,start,end,fixed,y0,y1,mat,holes=[],timber='去皮云杉木',meta={}){
  const cuts=[...new Set([start,end,...holes.flatMap(h=>[h.a,h.b])])].sort((a,b)=>a-b);
  const ys=[...new Set([y0,y1,...holes.flatMap(h=>[Math.max(y0,h.lo),Math.min(y1,h.hi)])])].sort((a,b)=>a-b);
  for(let i=0;i<cuts.length-1;i++)for(let j=0;j<ys.length-1;j++){
   const a=cuts[i],b=cuts[i+1],lo=ys[j],hi=ys[j+1];if(holes.some(h=>a>=h.a&&b<=h.b&&lo>=h.lo&&hi<=h.hi))continue;
   for(let p=a;p<b;p+=1){const q=Math.min(b,p+1),m=timber&&(p===start||q===end||(p-start)%5===0)?timber:mat;
    if(axis==='x')box(p,q,lo,hi,fixed,fixed+1,m,{kind:'wall',...meta});else box(fixed,fixed+1,lo,hi,p,q,m,{kind:'wall',...meta});
   }
  }
  if(timber){if(axis==='x')box(start,end,y1-.5,y1,fixed,fixed+1,timber,{kind:'beam',...meta});else box(fixed,fixed+1,y1-.5,y1,start,end,timber,{kind:'beam',...meta});}
  holes.forEach(h=>{
   if(h.kind==='door'){
    d.doors.push({id:h.id||`门洞${d.doors.length+1}`,axis,fixed,a:h.a,b:h.b,y:h.lo,height:h.hi-h.lo,level:ctx.level});
    // 两扇门贴靠墙内侧打开，完整门洞保持畅通。
    const length=(h.b-h.a)/2,dep=.18;
    const c=h.side===-1?fixed-length:fixed+1,e=h.side===-1?fixed:fixed+1+length;
    if(axis==='x'){box(h.a-dep,h.a,h.lo,h.hi-.15,c,e,'木门',{kind:'door',...meta});box(h.b,h.b+dep,h.lo,h.hi-.15,c,e,'木门',{kind:'door',...meta});}
    else{box(c,e,h.lo,h.hi-.15,h.a-dep,h.a,'木门',{kind:'door',...meta});box(c,e,h.lo,h.hi-.15,h.b,h.b+dep,'木门',{kind:'door',...meta});}
   }else{
    const m=(h.a+h.b)/2,t=timber||'去皮云杉木';
    if(axis==='x'){box(h.a,h.b,h.lo,h.hi,fixed+.45,fixed+.55,'玻璃',{kind:'glass',...meta});box(m-.1,m+.1,h.lo,h.hi,fixed+.3,fixed+.7,t,meta);box(h.a,h.b,h.lo-.12,h.lo+.12,fixed-.1,fixed+1.1,t,meta);box(h.a,h.b,(h.lo+h.hi)/2-.08,(h.lo+h.hi)/2+.08,fixed+.3,fixed+.7,t,meta);}
    else{box(fixed+.45,fixed+.55,h.lo,h.hi,h.a,h.b,'玻璃',{kind:'glass',...meta});box(fixed+.3,fixed+.7,h.lo,h.hi,m-.1,m+.1,t,meta);box(fixed-.1,fixed+1.1,h.lo-.12,h.lo+.12,h.a,h.b,t,meta);box(fixed+.3,fixed+.7,(h.lo+h.hi)/2-.08,(h.lo+h.hi)/2+.08,h.a,h.b,t,meta);}
   }
  });
 }
 function shell(x0,x1,z0,z1,y,top,mat,open={},timber='去皮云杉木'){
  if(open.S!==false)wall('x',x0,x1,z0,y,top,mat,open.S||[],timber);
  if(open.N!==false)wall('x',x0,x1,z1-1,y,top,mat,open.N||[],timber);
  if(open.W!==false)wall('z',z0+1,z1-1,x0,y,top,mat,open.W||[],timber);
  if(open.E!==false)wall('z',z0+1,z1-1,x1-1,y,top,mat,open.E||[],timber);
 }
 function post(x,z,y,top,mat='去皮云杉木',w=1){box(x,x+w,y,top,z,z+w,mat,{kind:'post'});d.supports.push({x,z,y,top,w});}
 function gable(id,x0,x1,z0,z1,eave,axis,mat,fill='方解石',beam='去皮云杉木'){
  const a=axis==='x'?z0:x0,b=axis==='x'?z1:x1;let ridge=eave;
  group({roof:true,kind:'roof'},()=>{
   for(let k=a;k<b;k++){
    const y=eave+.5*Math.min(k-a,b-1-k),top=y+.5;ridge=Math.max(ridge,top);
    if(axis==='x'){box(x0,x1,y,top,k,k+1,mat);if(k>a&&k<b-1){box(x0+1,x0+2,eave,y,k,k+1,fill);box(x1-2,x1-1,eave,y,k,k+1,fill);}}
    else{box(k,k+1,y,top,z0,z1,mat);if(k>a&&k<b-1){box(k,k+1,eave,y,z0+1,z0+2,fill);box(k,k+1,eave,y,z1-2,z1-1,fill);}}
   }
   if(axis==='x'){for(let x=x0+2;x<x1-1;x+=5)box(x,x+.5,eave-.5,eave,z0+1,z1-1,beam);box(x0,x1,ridge-.5,ridge+.25,(z0+z1)/2-.5,(z0+z1)/2+.5,mat);}
   else{for(let z=z0+2;z<z1-1;z+=5)box(x0+1,x1-1,eave-.5,eave,z,z+.5,beam);box((x0+x1)/2-.5,(x0+x1)/2+.5,ridge-.5,ridge+.25,z0,z1,mat);}
  });
  d.roofs.push({id,type:'双坡',bounds:[x0,x1,z0,z1],eave,ridge:ridge+.25,axis,mat});
 }
 function lean(id,x0,x1,z0,z1,lo,axis,reverse,mat='深板岩瓦'){
  const a=axis==='x'?x0:z0,b=axis==='x'?x1:z1;
  for(let k=a;k<b;k++){const y=lo+.5*(reverse?b-1-k:k-a);if(axis==='x')box(k,k+1,y,y+.5,z0,z1,mat,{roof:true,kind:'roof'});else box(x0,x1,y,y+.5,k,k+1,mat,{roof:true,kind:'roof'});}
  d.roofs.push({id,type:'单坡',bounds:[x0,x1,z0,z1],eave:lo,ridge:lo+(b-a)*.5,axis,reverse,mat});
 }
 function rail(axis,a,b,fixed,y,mat='去皮云杉木',gaps=[]){
  for(let p=a;p<b;p+=.5){if(gaps.some(g=>p>=g[0]&&p<g[1]))continue;
   if(axis==='x')box(p,Math.min(p+.5,b),y+.9,y+1.15,fixed,fixed+.25,mat,{kind:'rail'});else box(fixed,fixed+.25,y+.9,y+1.15,p,Math.min(p+.5,b),mat,{kind:'rail'});
  }
  for(let p=a;p<=b-.25;p+=2){if(gaps.some(g=>p>=g[0]&&p<g[1]))continue;if(axis==='x')box(p,p+.25,y,y+1.25,fixed,fixed+.25,mat,{kind:'rail'});else box(fixed,fixed+.25,y,y+1.25,p,p+.25,mat,{kind:'rail'});}
 }
 function stairs(id,axis,start,end,a,b,y,mat='石砖',guard=true){
  const steps=[];
  for(let p=start;p<end;p++){const top=y+.5*(p-start+1);if(axis==='z')box(a,b,y,top,p,p+1,mat,{kind:'stair',stair:id});else box(p,p+1,y,top,a,b,mat,{kind:'stair',stair:id});steps.push({p,top});
   if(guard){for(const edge of [a-.35,b+.1]){if(axis==='z'){box(edge,edge+.25,top+.85,top+1.1,p,p+1,'去皮云杉木',{kind:'rail'});if((p-start)%2===0)box(edge,edge+.25,top,top+1.2,p,p+.25,'去皮云杉木',{kind:'rail'});}else{box(p,p+1,top+.85,top+1.1,edge,edge+.25,'去皮云杉木',{kind:'rail'});if((p-start)%2===0)box(p,p+.25,top,top+1.2,edge,edge+.25,'去皮云杉木',{kind:'rail'});}}}
  }
  d.stairs.push({id,axis,start,end,a,b,bottom:y,top:y+(end-start)*.5,width:b-a,steps,guard});
 }
 function table(x,z,y,w=3,depth=2,mat='云杉木板'){
  box(x,x+w,y+1,y+1.25,z,z+depth,mat,{kind:'furniture'});for(const [a,b] of [[x,z],[x+w-.5,z],[x,z+depth-.5],[x+w-.5,z+depth-.5]])box(a,a+.5,y,y+1,b,b+.5,'深色原木',{kind:'furniture'});
 }
 function bench(x,z,y,w=3,axis='x'){box(x,x+(axis==='x'?w:1),y+.5,y+1,z,z+(axis==='x'?1:w),'云杉木板',{kind:'furniture'});for(const p of [0,w-1])box(x+(axis==='x'?p:0),x+(axis==='x'?p+1:1),y,y+.5,z+(axis==='x'?0:p),z+(axis==='x'?1:p+1),'深色原木',{kind:'furniture'});}
 function bed(x,z,y){box(x,x+2,y,y+.5,z,z+2,'深色原木',{kind:'furniture'});box(x,x+2,y+.5,y+.8,z,z+1.5,'床褥',{kind:'furniture'});box(x,x+2,y+.5,y+.85,z+1.5,z+2,'枕头',{kind:'furniture'});box(x,x+2,y,y+1,z+2,z+2.25,'去皮云杉木',{kind:'furniture'});}
 const chest=(x,z,y,n=2)=>box(x,x+n,y,y+1,z,z+1,'箱子',{kind:'furniture'});
 function shelf(x,z,y,n=4,axis='x',books=false){
  for(let j=0;j<2;j++)for(let i=0;i<n;i++)box(x+(axis==='x'?i:0),x+(axis==='x'?i+1:1),y+j*1.5+.25,y+j*1.5+1.25,z+(axis==='x'?0:i),z+(axis==='x'?1:i+1),books?'书册':'箱子',{kind:'furniture'});
  for(const t of [0,1.5,3])box(x,x+(axis==='x'?n:1),y+t,y+t+.25,z,z+(axis==='x'?1:n),'深色橡木',{kind:'furniture'});
  for(const i of [0,n-.25])box(x+(axis==='x'?i:0),x+(axis==='x'?i+.25:1),y,y+3.25,z+(axis==='x'?0:i),z+(axis==='x'?1:i+.25),'深色原木',{kind:'furniture'});
 }
 function planter(x0,x1,z0,z1,y,mat='石砖',height=.5){
  box(x0,x1,y,y+height,z0,z1,mat,{kind:'garden'});box(x0+.5,x1-.5,y+height,y+height+.08,z0+.5,z1-.5,'泥土',{kind:'garden'});
  for(let x=x0+1;x<x1-1;x+=2)for(let z=z0+1;z<z1-1;z+=2)box(x,x+1,y+height,y+height+.75,z,z+1,'树叶',{kind:'garden'});
 }
 function tree(x,z,y,h=5,flower=false){
  box(x,x+1,y,y+h,z,z+1,'深色原木',{kind:'garden'});box(x-1,x+2,y+h-1,y+h,z,z+1,'去皮云杉木',{kind:'garden'});
  box(x-2,x+3,y+h-1,y+h+1,z-2,z+3,flower?'白花叶':'树叶',{kind:'garden'});box(x-1,x+2,y+h+1,y+h+2,z-1,z+2,flower?'白花叶':'树叶',{kind:'garden'});
 }
 function pond(x0,x1,z0,z1,y){
  box(x0,x1,y-.8,y-.6,z0,z1,'凝灰岩',{kind:'garden'});box(x0+.5,x1-.5,y-.6,y-.15,z0+.5,z1-.5,'水',{kind:'water'});
  for(const [a,b,c,e] of [[x0,x1,z0,z0+.5],[x0,x1,z1-.5,z1],[x0,x0+.5,z0,z1],[x1-.5,x1,z0,z1]])box(a,b,y-.6,y+.25,c,e,'石砖',{kind:'garden'});
 }
 function room(name,bounds,y,level=1,usage=''){d.rooms.push({name,bounds,y,level,usage,p:[(bounds[0]+bounds[1])/2,y+.1,(bounds[2]+bounds[3])/2]});}
 function court(name,bounds,y,usage){d.courts.push({name,bounds,y,usage});room(name,bounds,y,1,usage);}
 function route(name,points){d.routes.push({name,points});}
 function common(){
  const [x0,x1,z1]=d.bounds;
  box(0,64,-1,-.1,0,72,'泥土',{kind:'terrain',level:0});box(0,64,-.1,0,0,72,'草方块',{kind:'terrain',level:0});
  // 留白仅画尺寸边界，地面不放任何未来设施。
  for(const [a,b,c,e] of [[0,64,0,.15],[0,64,71.85,72],[0,.15,0,72],[63.85,64,0,72]])box(a,b,.02,.08,c,e,'石砖',{kind:'plot',level:0});
  for(let x=0;x<64;x+=4)box(x,x+2,.02,.1,z1,z1+.18,'方解石',{kind:'boundary',level:0});
  for(const x of [x0,x1-.5])box(x,x+.5,0,1.5,0,z1,'石砖',{kind:'boundary'});
  box(x0,x1,0,1.5,z1-.5,z1,'石砖',{kind:'boundary'});
  box(x0,29,0,1.5,0,.5,'石砖',{kind:'boundary'});box(34,x1,0,1.5,0,.5,'石砖',{kind:'boundary'});
  slab(29,34,0,3,0,'石砖');
  post(29,1,0,4.5,d.timber);post(33,1,0,4.5,d.timber);post(29,3,0,4.5,d.timber);post(33,3,0,4.5,d.timber);
  box(29,34,4,4.5,1,2,d.timber,{kind:'beam'});box(29,34,4,4.5,3,4,d.timber,{kind:'beam'});
  gable('南入口门屋',28,35,0,5,4.5,'x',d.roofMat,d.timber,d.timber);
  d.doors.push({id:'宅院南入口',axis:'x',fixed:1,a:30,b:33,y:0,height:4,level:1});
  const nx=x1-3;box(nx,nx+.4,.03,.08,2,5,'方解石',{kind:'compass',level:0});box(nx-.7,nx+1.1,.03,.08,4,4.5,'方解石',{kind:'compass',level:0});box(nx-.3,nx+.7,.03,.08,4.5,5,'方解石',{kind:'compass',level:0});
 }
 const h={d,box,slab,platform,group,door,win,wall,shell,post,gable,lean,rail,stairs,table,bench,bed,chest,shelf,planter,tree,pond,room,court,route,common};author(h);designs.push(d);
}

make({id:'shanlang',name:'杉廊双院宅',bounds:[5,59,50],size:'54×50',reserve:'64×22',timber:'去皮云杉木',roofMat:'深板岩瓦',cut:5.45,upperY:6,
 reason:'低回廊把前后两院分开，白墙和深灰屋面围住院内视线；东北角的二层主屋高于其余屋顶，南侧留出开敞的入院空地。',
 question:'在后院人视里，横廊与东北二层之间的院宽是否足够舒展？',
 cameras:{overview:{eye:[83,53,-37],target:[32,4,25],span:64},person:{eye:[32,2.7,24],target:[29,4.2,35]},gallery:{eye:[22.5,2.7,24.5],target:[33,3.3,27]},upper:{eye:[73,51,6],target:[44,6,40],span:36},plan:{eye:[32,100,25],target:[32,0,25],span:56},plot:{eye:[32,115,36],target:[32,0,36],span:80}}
},h=>{
 const {d,box,slab,platform,group,door,win,wall,shell,post,gable,lean,rail,stairs,table,bench,bed,chest,shelf,planter,tree,pond,room,court,route,common}=h;
 common();
 platform(21,44,7,34,1,'石砖','砂砾');platform(10,21,9,31,1);platform(44,55,10,28,1);platform(14,35,34,49,1);platform(34,55,31,49,1);
 platform(44,55,28,31,1,'石砖','石砖');slab(30,34,7,34,1.02,'石砖');stairs('前院入口台阶','z',5,7,30,34,0,'石砖',false);
 court('迎客前院',[25,40,7,18],1,'南入口与横廊之间留出会客落脚区，边缘设置树池和坐凳。');
 court('后院水石庭',[25,40,22,30],1,'靠近起居室的安静小院，中央通路两侧分置水池与石景。');
 planter(25,29,9,15,1);tree(26,11,1.5,4);bench(35,12,1,3);planter(36,40,8,10,1);pond(25.5,29.5,23,28,1.5);planter(35,39,25,29,1);box(35.5,37,1.5,3,26,27.5,'凝灰岩',{kind:'garden'});box(37,38,1.5,2.5,26.5,28,'凝灰岩',{kind:'garden'});
 // 两侧服务厢房。
 shell(10,21,9,31,1,6.5,'方解石',{S:[win(13,17,1)],N:[win(13,17,1)],W:[win(14,18,1),win(25,28,1)],E:[door(14,17,1,3,'厨房东门'),door(25,27,1,3,'储藏东门')]});
 wall('x',11,20,22,1,6.5,'方解石',[door(15,17,1,3,'厨房至储藏')]);
 shell(44,55,10,28,1,6.5,'方解石',{S:[win(47,51,1)],N:[win(47,51,1)],W:[door(14,16,1,3,'工坊西门'),door(23,25,1,3,'茶室西门')],E:[win(13,16,1),win(22,25,1)]});
 wall('x',45,54,19,1,6.5,'方解石',[door(48,50,1,3,'工坊至茶室')]);
 gable('西侧服务厢房',9,22,8,32,6.5,'z','深板岩瓦');gable('东侧工坊茶室',43,56,9,29,6.5,'z','深板岩瓦');
 // 北侧低厅与东北两层楼共享 x=34 的墙线。
 shell(14,35,34,49,1,6.5,'方解石',{S:[door(29,32,1,4,'起居室南门'),win(18,23,1)],N:[win(18,23,1),win(28,32,1)],W:[win(39,43,1)],E:[door(36,39,1,3,'起居室至楼梯厅')]});
 shell(34,55,31,49,1,5.5,'方解石',{S:[win(37,41,1),win(46,50,1)],N:[win(38,42,1)],W:false,E:[win(35,39,1),win(43,46,1)]});
 wall('z',32,34,34,1,5.5,'方解石',[]);
 gable('北侧起居厅',13,35,33,50,6.5,'x','深板岩瓦');
 // 二层楼板分块形成 5×13 的完整楼梯开口。
 group({level:2},()=>{
  slab(34,48,31,49,6);slab(53,55,31,49,6);slab(48,53,31,32,6);slab(48,53,45,49,6);
  shell(34,55,31,49,6,11.5,'方解石',{S:[win(37,41,6)],N:[win(38,42,6),win(48,52,6)],W:[],E:[win(35,39,6),win(43,46,6)]});
  wall('z',32,48,44,6,11.5,'方解石',[door(34,36,6,3,'二层书房东门'),door(42,44,6,3,'卧室东门')]);
  wall('x',35,44,39,6,11.5,'方解石',[]);
  rail('z',32,45,47.75,6,'去皮云杉木');rail('x',48,53,32,6);rail('z',32,45,53,6); // 梯口北端留出 3 格落脚区。
  bed(37,44,6);chest(41,46,6,2);table(36,33,6,3,1);bench(36,35,6,2);shelf(41,33,6,4,'z',true);
  gable('东北二层主屋',33,56,30,50,11.5,'z','深板岩瓦');
 });
 stairs('东北室内楼梯','z',35,45,49,52,1,'云杉木板',true);
 // 梁由内墙或立柱承托，避开楼梯开口。
 box(34,48,5,5.5,39,40,'去皮云杉木',{kind:'beam'});post(44,39,1,5.5);post(47,31,1,5.5);post(47,48,1,5.5);
 // 4 格宽回廊的柱子贴外缘，柱间净路至少 3 格。
 slab(21,25,7,34,1,'云杉木板');slab(40,44,7,31,1,'云杉木板');slab(25,40,18,22,1,'云杉木板');slab(25,40,30,34,1,'云杉木板');
 lean('西廊',21,25,8,34,5,'x',true);lean('东廊',40,44,8,31,5,'x',false);gable('两院之间的横廊',25,40,18,22,5.5,'x','深板岩瓦','去皮云杉木');lean('北厅前廊',25,40,30,34,5,'z',false);
 for(const z of [8,17,23,30]){post(24,z,1,5);post(40,z,1,5);}box(24,25,4.5,5,8,34,'去皮云杉木',{kind:'beam'});box(40,41,4.5,5,8,31,'去皮云杉木',{kind:'beam'});
 for(const x of [25,29,35,39]){post(x,18,1,5.5);post(x,21,1,5.5);}box(25,40,5,5.5,18,19,'去皮云杉木',{kind:'beam'});box(25,40,5,5.5,21,22,'去皮云杉木',{kind:'beam'});
 for(const x of [25,35,39])post(x,30,1,5);box(25,40,4.5,5,30,31,'去皮云杉木',{kind:'beam'});
 // 家具和庭院细部留出门前通道。
 box(11,14,1,2,17,20,'熔炉',{kind:'furniture'});box(11,12,2,11.5,19,20,'红砖',{kind:'chimney'});table(14,11,1,3,2);shelf(11,27,1,6);chest(17,24,1);
 table(48,12,1,4,2);chest(51,17,1,2);shelf(45,17,1,3,'x',true);table(48,23,1,3,2);bench(48,21,1,3);
 slab(18,26,39,46,1.04,'灰色地毯');table(20,41,1,4,2);bench(20,39,1,4);bench(20,44,1,4);shelf(16,46,1,6,'x',true);table(37,43,1,3,2);bench(37,46,1);
 planter(7,12,3,7,0);tree(8,4,.5,4);planter(51,56,3,7,0);tree(52,4,.5,4);
 room('厨房',[11,20,10,22],1);room('储藏',[11,20,23,30],1);room('工坊',[45,54,11,19],1);room('茶室',[45,54,20,27],1);room('起居室',[15,34,35,48],1);room('楼梯厅',[35,54,32,48],1);room('独立卧室',[35,44,40,48],6,2);room('二层书房',[35,44,32,39],6,2);room('上层过厅',[45,48,32,48],6,2);
 route('南门至起居室',[[31.5,0,0],[32,0,5],[32,1,7],[32,1,16],[32,1,24],[32,1,32],[30.5,1,36],[30.5,1,40]]);
 route('西廊家务路线',[[30.5,1,36],[28,1,32],[22.5,1,32],[22.5,1,15.5],[18,1,15.5],[16,1,25.5]]);
 route('起居室至二层卧室',[[30.5,1,37.5],[37,1,37.5],[46,1,37.5],[46,1,33.5],[50.5,1,33.5],[50.5,6,46.5],[46,6,46.5],[46,6,43],[42,6,43]]);
 route('工坊与茶室',[[32,1,20],[42.5,1,20],[42.5,1,15],[47,1,15],[49,1,23]]);
});

make({id:'chitao',name:'赤陶桥院楼',bounds:[4,60,52],size:'56×52',reserve:'64×20',timber:'深色原木',roofMat:'深色橡木',cut:6.45,upperY:7,
 reason:'两栋赤陶与浅木色楼房在南北方向错开，中间留出 14 格宽的内院。二层连桥压低北侧的视线，南侧前庭保持开敞，形成从亮处走入廊下的变化。',
 question:'从内院向北看，连桥的开口和两侧高墙是否仍显得压迫？',
 cameras:{overview:{eye:[52,58,-52],target:[31,6,27],span:65},person:{eye:[31,2.7,25],target:[33,7.5,38]},gallery:{eye:[37,2.7,25],target:[29,3.2,34]},upper:{eye:[79,56,-5],target:[32,8,34],span:55},plan:{eye:[32,110,26],target:[32,0,26],span:58},plot:{eye:[32,115,36],target:[32,0,36],span:80}}
},h=>{
 const {d,box,slab,platform,group,door,win,wall,shell,post,gable,lean,rail,stairs,table,bench,bed,chest,shelf,planter,tree,pond,room,court,route,common}=h;
 const timber='深色原木';common();
 platform(10,55,5,15,1,'红砖','砂砾');platform(8,27,16,45,1,'石砖','白桦木板');platform(39,56,22,49,1,'石砖','白桦木板');platform(27,39,16,49,1,'红砖','砂砾');platform(56,59,6,49,1,'红砖','石砖');
 stairs('南门入院台阶','z',3,5,30,33,0,'红砖',false);slab(30,33,5,11,1.02,'红砖');slab(14,33,10,14,1.02,'石砖');slab(14,18,14,17,1.02,'石砖');
 court('前庭',[11,55,5,15],1,'迎客与入户转向的开敞前庭，左右树池错开主路。');court('内院工作庭',[27,39,22,35],1,'厨房外设置洗涤池、工作台和可停留的空地，连桥在北侧形成遮阴。');
 planter(10,18,5,9,1,'红砖');tree(12,6,1.5,4);planter(39,46,6,11,1,'红砖');tree(41,7,1.5,4,true);planter(49,54,6,9,1,'红砖');bench(46,12,1,4);
 pond(29,32,26,30,2);table(29,32,1,3,1,'金合欢木');planter(28,32,43,47,1,'红砖');tree(29,44,1.5,4);slab(32,35,21,49,1.02,'红砖');
 // 西楼：南部起居，北部餐厅，东侧有独立楼梯间。
 shell(8,27,16,45,1,6.5,'红色陶瓦',{S:[door(14,17,1,4,'西楼正门'),win(10,12,1),win(22,25,1)],N:[win(11,16,1),win(21,24,1)],W:[win(20,24,1),win(36,40,1)],E:[door(36,39,1,3,'餐厅东门'),win(23,27,1)]},timber);
 wall('z',17,32,20,1,6.5,'红色陶瓦',[door(17,20,1,3,'起居室至楼梯',-1)],timber);wall('x',9,21,32,1,6.5,'红色陶瓦',[door(14,17,1,3,'起居室至餐厅')],timber);
 // 东楼：厨房与储藏之间保留两条实际门洞。
 shell(39,56,22,49,1,6.5,'红色陶瓦',{S:[win(42,46,1),win(51,54,1)],N:[win(42,46,1),win(51,54,1)],W:[door(26,29,1,3,'厨房西门'),door(39,41,1,3,'储藏西门')],E:[door(27,29,1,3,'厨房服务门'),door(43,45,1,3,'后勤入口'),win(34,37,1)]},timber);
 wall('x',40,55,36,1,6.5,'红色陶瓦',[door(46,49,1,3,'厨房至储藏')],timber);wall('z',37,48,49,1,6.5,'红色陶瓦',[door(40,43,1,3,'储藏至后勤廊')],timber);
 // 二层楼板的开口覆盖楼梯起步平台和全部梯段。
 group({level:2},()=>{
  slab(8,21,16,45,7,'白桦木板');slab(26,27,16,45,7,'白桦木板');slab(21,26,16,18,7,'白桦木板');slab(21,26,32,45,7,'白桦木板');
  shell(8,27,16,45,7,12.5,'白桦木板',{S:[door(14,17,7,3,'书房南阳台门')],N:[win(11,16,7),win(22,25,7)],W:[win(21,25,7),win(38,41,7)],E:[door(36,39,7,3,'西楼连桥门'),win(22,26,7)]},timber);
  wall('z',17,32,20,7,12.5,'白桦木板',[],timber);wall('x',9,21,32,7,12.5,'白桦木板',[door(14,17,7,3,'书房北门')],timber);
  wall('x',9,21,36,7,12.5,'白桦木板',[door(15,17,7,3,'卧室南门')],timber);wall('z',37,44,20,7,12.5,'白桦木板',[door(39,41,7,3,'卧室东门')],timber);
  rail('z',18,32,25.75,7,timber);rail('x',21,26,18,7,timber);rail('z',18,32,20.75,7,timber);
  slab(39,56,22,49,7,'白桦木板');
  shell(39,56,22,49,7,12.5,'白桦木板',{S:[win(43,47,7),win(51,54,7)],N:[win(43,47,7),win(51,54,7)],W:[door(36,39,7,3,'东楼连桥门'),win(26,29,7)],E:[win(26,30,7),win(41,45,7)]},timber);
  wall('x',40,55,35,7,12.5,'白桦木板',[door(46,49,7,3,'静修室北门')],timber);wall('x',40,55,40,7,12.5,'白桦木板',[door(46,49,7,3,'藏书室南门')],timber);
  // 连桥和南阳台均由下方柱梁支承。
  slab(26,40,35,40,7,'白桦木板');rail('x',27,39,35,7,timber);rail('x',27,39,39.75,7,timber);
  slab(10,24,13,16,7,'白桦木板');rail('x',10,24,13,7,timber);rail('z',13,16,10,7,timber);rail('z',13,16,23.75,7,timber);
  bed(11,40,7);chest(17,42,7,2);table(11,22,7,4,2);bench(11,25,7,3);shelf(9,28,7,7,'x',true);
  slab(44,51,27,32,7.03,'灰色地毯');table(43,24,7,3,1);bench(44,32,7,3);shelf(40,45,7,7,'x',true);shelf(54,42,7,5,'z',true);table(48,43,7,3,2);
  gable('西楼主屋面',7,28,15,46,12.5,'z','深色橡木','白桦木板',timber);gable('东楼主屋面',38,57,21,50,12.5,'z','深色橡木','白桦木板',timber);gable('二层连桥',26,40,34,41,10.5,'x','深色橡木',timber,timber);
 });
 stairs('西楼室内楼梯','z',20,32,22,25,1,'白桦木板',true);
 // 中间梁均落在墙线或柱线上，不横跨楼梯开口。
 box(8,21,6,6.5,32,33,timber,{kind:'beam'});box(39,57,6,6.5,36,37,timber,{kind:'beam'});post(49,36,1,6.5,timber);
 for(const x of [28,33,38]){post(x,35,1,6.5,timber);post(x,39,1,6.5,timber);}box(26,40,6.25,6.5,35,36,timber,{kind:'beam'});box(26,40,6.25,6.5,39,40,timber,{kind:'beam'});
 for(const x of [10,19,23])post(x,13,1,6.5,timber);box(10,24,6,6.5,13,14,timber,{kind:'beam'});
 group({level:2},()=>{for(const x of [28,33,38]){post(x,35,7,10.5,timber);post(x,39,7,10.5,timber);}box(26,40,10,10.5,35,36,timber,{kind:'beam'});box(26,40,10,10.5,39,40,timber,{kind:'beam'});});
 // 一层回廊从餐厅东门向南绕过内院，再到厨房。
 slab(27,39,17,21,1,'白桦木板');slab(35,39,20,36,1,'白桦木板');slab(27,39,35,40,1,'石砖');
 gable('南侧横廊',27,39,17,21,5.5,'x','深色橡木',timber,timber);lean('厨房西廊',35,39,21,35,5,'x',false,'深色橡木');
 for(const x of [28,34,38])post(x,17,1,5.5,timber);box(27,39,5,5.5,17,18,timber,{kind:'beam'});for(const z of [22,30,34])post(35,z,1,5,timber);box(35,36,4.5,5,21,35,timber,{kind:'beam'});
 // 东侧有一条连续的三格服务通路。
 platform(53,59,5,15,1,'红砖','石砖');slab(56,59,6,49,1.04,'石砖');planter(56,59,49,51,0,'红砖');
 table(11,25,1,4,2);bench(11,23,1,4);bench(11,28,1,4);slab(10,17,22,30,1.03,'灰色地毯');table(12,37,1,5,3);bench(12,35,1,5);bench(12,41,1,5);
 box(40,44,1,2,30,33,'熔炉',{kind:'furniture'});box(40,41,2,19,32,33,'红砖',{kind:'chimney'});table(47,26,1,4,2);chest(53,31,1,2);shelf(40,45,1,7);chest(46,43,1,2);
 room('起居室',[9,20,17,32],1);room('餐厅',[9,26,33,44],1);room('楼梯间',[21,26,17,32],1);room('厨房',[40,55,23,36],1);room('储藏',[40,49,37,48],1);room('后勤廊',[50,55,37,48],1);
 room('书房',[9,20,17,32],7,2);room('独立卧室',[9,20,37,44],7,2);room('西楼过厅',[9,26,33,36],7,2);room('有顶连桥',[27,39,36,39],7,2);room('静修室',[40,55,23,35],7,2);room('东楼过厅',[40,55,36,40],7,2);room('藏书室',[40,55,41,48],7,2);room('南阳台',[10,24,13,16],7,2);
 route('南门入户',[[31.5,0,0],[31.5,0,3],[31.5,1,5],[31.5,1,11.5],[15.5,1,11.5],[15.5,1,19],[15.5,1,25]]);
 route('餐厅至厨房',[[18,1,37.5],[30,1,37.5],[36.5,1,37.5],[37,1,27.5],[43,1,27.5]]);
 route('上楼与连桥',[[18,1,18.5],[23.5,1,18.5],[23.5,7,33.5],[24,7,37.5],[33,7,37.5],[41,7,37.5],[47.5,7,37.5],[47.5,7,32]]);
 route('厨房后勤路线',[[56.8,1,8],[56.8,1,28],[53,1,28],[47.5,1,33.5],[47.5,1,38.5],[51,1,41.5],[54,1,44]]);
 // 从全貌和院内视点检查后，将西楼整体西移 2 格，扩大内院。
 for(const b of d.boxes){
  if(b.x1<=28&&b.z0>=13){b.x0-=2;b.x1-=2;}
  else if(b.x0===27&&b.x1===39&&b.z0>=16&&b.z1<=49)b.x0=25;
  else if(b.x0===26&&b.x1===40&&b.z0>=34&&b.z1<=41)b.x0=24;
  else if(b.roof&&b.x0===28&&b.x1===29&&b.z0>=17&&b.z1<=21){b.x0-=2;b.x1-=2;}
 }
 // 连桥南边减少一根中央落地柱，梁增厚到 1 格，打开正面视线。
 d.boxes=d.boxes.filter(b=>!(b.kind==='post'&&b.x0===33&&b.z0===35&&b.y0===1));
 for(const b of d.boxes)if(b.kind==='beam'&&b.x0===24&&b.x1===40&&b.z0===35&&b.y1===6.5)b.y0=5.5;
 d.supports=d.supports.filter(p=>!(p.x===33&&p.z===35&&p.y===1));
 for(const p of d.supports)if(p.x<=27&&p.z>=13)p.x-=2;
 for(const r of d.rooms){if(r.bounds[1]<=26){r.bounds[0]-=2;r.bounds[1]-=2;}else if(r.bounds[0]===27)r.bounds[0]=25;r.p=[(r.bounds[0]+r.bounds[1])/2,r.y+.1,(r.bounds[2]+r.bounds[3])/2];}
 d.courts[1].bounds[0]=25;
 for(const r of d.roofs){if(r.id==='西楼主屋面'){r.bounds[0]-=2;r.bounds[1]-=2;}if(r.id==='二层连桥')r.bounds[0]=24;if(r.id==='南侧横廊')r.bounds[0]=25;}
 for(const p of d.doors){if(p.axis==='z'&&p.fixed<30)p.fixed-=2;else if(p.axis==='x'&&p.b<=20){p.a-=2;p.b-=2;}}
 for(const s of d.stairs)if(s.id==='西楼室内楼梯'){s.a-=2;s.b-=2;}
 for(const r of d.routes)for(const p of r.points)if(p[0]<=26)p[0]-=2;
});

make({id:'yantai',name:'岩台水庭居',bounds:[6,58,48],size:'52×48',reserve:'64×24',timber:'金合欢木',roofMat:'氧化的切制铜',cut:6.45,upperY:7.5,
 reason:'水庭向南打开，北侧用石台、卧室坡顶和屋顶茶亭形成三个高度。铜绿屋面与浅色石墙相接，从低廊转入水边后，可以看见完整的错层立面。',
 question:'从水庭向北看，卧室石台和屋顶茶亭的高度关系是否让居住部分显得过重？',
 cameras:{overview:{eye:[82,53,-32],target:[32,4,24],span:65},person:{eye:[34,2.7,18],target:[37,5.5,35]},gallery:{eye:[27.5,2.7,20.5],target:[41,3.6,21.5]},upper:{eye:[-5,48,7],target:[23,8,36],span:44},plan:{eye:[32,100,24],target:[32,0,24],span:55},plot:{eye:[32,115,36],target:[32,0,36],span:80}}
},h=>{
 const {d,box,slab,platform,group,door,win,wall,shell,post,gable,lean,rail,stairs,table,bench,bed,chest,shelf,planter,tree,pond,room,court,route,common}=h;
 const timber='金合欢木';common();
 platform(10,33,5,10,1,'凝灰岩','砂砾');platform(12,26,10,27,1,'凝灰岩','云杉木板');platform(26,34,10,32,1,'凝灰岩','石砖');platform(12,34,28,45,1,'凝灰岩','云杉木板');platform(34,50,31,45,3,'石砖','白桦木板');platform(50,56,31,44,3,'石砖','云杉木板');
 platform(7,12,22,29,1,'石砖','石砖');platform(19,26,26,29,1);platform(30,55,7,12,1,'石砖','砂砾');platform(30,38,12,29,1,'石砖','砂砾');platform(51,55,12,27,1,'石砖','砂砾');platform(38,55,25,29,1,'石砖','砂砾');
 stairs('南门入院台阶','z',3,5,30,33,0,'石砖',false);slab(30,33,5,28,1.02,'石砖');slab(26,30,9,28,1.02,'云杉木板');
 court('西侧工作庭',[10,29,5,10],1,'厨房和储藏前的工作落脚区，工作台靠边，中央通向有顶西廊。');court('东侧水庭',[30,55,7,29],1,'沿池边保留三至四格步道，北侧观水平台连向卧室外台阶。');
 planter(11,17,5,8,1,'平滑砂岩');tree(12,6,1.5,4);table(20,6,1,4,2,'金合欢木');pond(38,51,12,25,1);box(39,41,1,2.5,14,16,'凝灰岩',{kind:'garden'});box(40,42,1,3,15,17,'凝灰岩',{kind:'garden'});box(47,49,1,1.5,21,23,'凝灰岩',{kind:'garden'});planter(46,52,7,11,1,'平滑砂岩');tree(48,8,1.5,4,true);bench(42,26,1,4);planter(53,56,15,22,1,'平滑砂岩');
 // 西侧厨房、储藏与北厅由两处门和短廊连通。
 shell(12,26,10,27,1,6.5,'平滑砂岩',{S:[door(20,23,1,3,'厨房南门'),win(14,17,1)],N:[door(20,23,1,3,'储藏北门')],W:[win(14,18,1),door(23,25,1,3,'储藏西门')],E:[door(14,17,1,3,'厨房东门'),door(23,25,1,3,'储藏东门')]},timber);
 wall('x',13,25,22,1,6.5,'平滑砂岩',[door(18,20,1,3,'厨房至储藏',-1)],timber);
 gable('厨房与储藏',11,27,9,28,6.5,'z','氧化的切制铜','平滑砂岩',timber);
 shell(12,34,28,45,1,7,'平滑砂岩',{S:[door(29,32,1,4,'起居厅南门'),door(20,23,1,3,'起居厅服务门'),win(14,17,1)],N:[win(16,21,1),win(26,30,1)],W:[win(33,37,1)],E:[door(35,38,3,3,'起居厅卧室台阶门')]},timber);
 // 起居厅屋顶为真实可上人的平屋面；去顶保留此楼板，上层视角可看茶亭。
 group({level:2},()=>{
  slab(11,34,27,46,7.5,'平滑砂岩',{walkableRoof:true});slab(14,31,31,44,7.54,'云杉木板');
  rail('x',11,34,27,7.5,timber);rail('x',11,34,45.75,7.5,timber);rail('z',27,46,11,7.5,timber,[[41,44]]);rail('z',27,46,33.75,7.5,timber);
  // 茶亭屋面由六根落地到露台的柱子支承；下方梁柱连续至一层基础。
  for(const x of [15,21,27]){post(x,35,7.5,11.5,timber);post(x,43,7.5,11.5,timber);}box(15,28,11,11.5,35,36,timber,{kind:'beam'});box(15,28,11,11.5,43,44,timber,{kind:'beam'});
  gable('露台茶亭',14,29,34,45,11.5,'x','氧化的切制铜',timber,timber);
  table(18,39,7.5,4,2,'金合欢木');bench(18,37,7.5,4);bench(18,42,7.5,4);planter(28,32,29,32,7.5,'平滑砂岩');
  // 西侧高平台兼作楼梯出口，南面保持完整开口。
  platform(7,12,41,45,7.5,'石砖','平滑砂岩');rail('z',41,45,6.75,7.5,timber);rail('x',7,12,44.75,7.5,timber);
 });
 d.roofs.push({id:'起居厅可上人屋顶',type:'平屋面及露台',bounds:[11,34,27,46],eave:7,ridge:7.5,axis:'平',mat:'平滑砂岩'});
 // 茶亭和露台下方的柱梁均有实体支承，靠边放置。
 for(const x of [15,21,27]){post(x,43,1,7,timber);box(x,x+1,6.5,7,28,44,timber,{kind:'beam'});}for(const x of [15,27])post(x,35,1,7,timber);
 stairs('西侧屋顶楼梯','z',28,41,7,10,1,'石砖',true);
 // 东卧室与书房位于 +2 的石台上。
 shell(34,50,31,45,3,8.5,'平滑砂岩',{S:[win(36,40,3),win(45,48,3)],N:[win(36,40,3),win(45,48,3)],W:[door(35,38,3,3,'卧室西门')],E:[door(36,39,3,3,'书房露台门')]},timber);
 wall('z',32,44,43,3,8.5,'平滑砂岩',[door(38,40,3,3,'卧室至书房')],timber);
 gable('抬高卧室与书房',33,51,30,46,8.5,'z','氧化的切制铜','平滑砂岩',timber);
 stairs('室内卧室台阶','x',28,32,35,38,1,'石砖',true);platform(32,35,35,38,3,'石砖','白桦木板');
 stairs('水庭至卧室露台','z',27,31,51,54,1,'石砖',true);rail('x',50,56,31,3,timber,[[51,54]]);rail('x',50,56,43.75,3,timber);rail('z',31,44,55.75,3,timber);
 // 厨房东廊和起居厅门廊在低檐下接续。
 lean('厨房东廊',26,30,10,28,5,'x',true,'氧化的切制铜');lean('起居厅门廊',26,34,25,29,5,'z',false,'氧化的切制铜');
 for(const z of [10,18,24])post(29,z,1,5,timber);box(29,30,4.5,5,10,28,timber,{kind:'beam'});post(26,25,1,5,timber);post(33,25,1,5,timber);box(26,34,4.5,5,25,26,timber,{kind:'beam'});
 box(13,16,1,2,17,20,'熔炉',{kind:'furniture'});box(13,14,2,11.5,19,20,'红砖',{kind:'chimney'});table(16,12,1,3,2);shelf(13,25,1,5);chest(23,24,1,1);
 slab(16,25,36,42,1.03,'灰色地毯');table(18,38,1,4,2,'金合欢木');bench(18,36,1,4);bench(18,41,1,4);shelf(13,30,1,5,'z',true);bed(37,40,3);chest(40,42,3,2);table(45,32,3,3,1,'金合欢木');shelf(48,41,3,3,'z',true);bench(52,40,3,3,'z');
 room('厨房',[13,25,11,22],1);room('储藏',[13,25,23,26],1);room('起居厅',[13,33,29,44],1);room('独立卧室',[35,43,32,44],3);room('书房',[44,49,32,44],3);room('卧室露台',[50,56,31,44],3);room('屋顶露台',[11,34,27,46],7.5,2);room('露台茶亭',[16,27,36,43],7.5,2);
 route('南门至起居厅',[[31.5,0,0],[31.5,0,3],[31.5,1,5],[31.5,1,20],[31.5,1,26],[30.5,1,30],[25,1,33]]);
 route('西廊家务路线',[[25,1,33],[21.5,1,30],[21.5,1,24.5],[19,1,24.5],[19,1,19],[27.5,1,15.5],[27.5,1,7.5],[22,1,7.5]]);
 route('卧室与水庭环线',[[25,1,33],[26.5,1,36.5],[28,1,36.5],[32,3,36.5],[37,3,36.5],[40,3,39],[46,3,39],[47.5,3,37.5],[52.5,3,37.5],[52.5,3,31.5],[52.5,1,26.5],[34,1,26.5],[31.5,1,26.5]]);
 route('屋顶露台路线',[[21.5,1,24],[10,1,24],[8.5,1,26.5],[8.5,7.5,42.5],[13,7.5,42.5],[13,7.5,33],[18,7.5,33],[18,7.5,38]]);
});
global.HouseDesigns=designs;
if(typeof module!=='undefined')module.exports=designs;
})(typeof window!=='undefined'?window:globalThis);
