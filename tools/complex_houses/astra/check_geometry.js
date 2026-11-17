/* 静态几何检查。它不替代 Minecraft 碰撞或玩家体验验收。 */
'use strict';
const fs=require('fs'),path=require('path'),designs=require('./geometry.js');
const results=[];
const overlap=(a,b,c,d)=>a<d-1e-5&&b>c+1e-5;
function obstaclesAt(d,x,z,y,height,r=.04){return d.boxes.filter(b=>overlap(x-r,x+r,b.x0,b.x1)&&overlap(z-r,z+r,b.z0,b.z1)&&overlap(y+.06,y+height,b.y0,b.y1));}
for(const d of designs){
 const errors=[],doorChecks=[],stairChecks=[];
 for(const [i,b] of d.boxes.entries())if(![b.x0,b.x1,b.y0,b.y1,b.z0,b.z1].every(Number.isFinite)||b.x1<=b.x0||b.y1<=b.y0||b.z1<=b.z0)errors.push({kind:'invalid_box',i});
 const reserveOffenders=d.boxes.filter(b=>!['terrain','plot','boundary','compass'].includes(b.kind)&&b.z1>d.bounds[2]+1e-6);
 if(reserveOffenders.length)errors.push({kind:'reserve_occupied',count:reserveOffenders.length});
 for(const door of d.doors){
  const collisions=[];for(let p=door.a+.1;p<door.b;p+=.4){const x=door.axis==='x'?p:door.fixed+.5,z=door.axis==='x'?door.fixed+.5:p;const hits=obstaclesAt(d,x,z,door.y,door.height-.08,.03);if(hits.length)collisions.push({p,hits:hits.map(b=>({kind:b.kind,mat:b.mat,x:b.x0,y:b.y0,z:b.z0}))});}
  const ok=door.b-door.a>=2&&door.height>=3&&!collisions.length;doorChecks.push({id:door.id,width:door.b-door.a,height:door.height,clear:ok});if(!ok)errors.push({kind:'door',id:door.id,collisions});
 }
 for(const s of d.stairs){
  const hits=[];let minHeadroom=99;
  for(const step of s.steps)for(let a=s.a+.1;a<s.b;a+=.4){
   const x=s.axis==='z'?a:step.p+.5,z=s.axis==='z'?step.p+.5:a;
   const overhead=d.boxes.filter(b=>x>b.x0&&x<b.x1&&z>b.z0&&z<b.z1&&b.y0>=step.top+.05).map(b=>b.y0-step.top);
   if(overhead.length)minHeadroom=Math.min(minHeadroom,...overhead);
   const collision=obstaclesAt(d,x,z,step.top,2.8,.03);if(collision.length)hits.push({x,z,top:step.top,objects:collision.map(b=>({kind:b.kind,mat:b.mat,x:b.x0,y:b.y0,z:b.z0}))});
  }
  const ok=s.width>=3&&s.steps.every((q,i)=>q.top===s.bottom+.5*(i+1))&&!hits.length;
  stairChecks.push({id:s.id,width:s.width,steps:s.steps.length,rise:s.top-s.bottom,minHeadroom:minHeadroom===99?'open':Number(minHeadroom.toFixed(2)),clear:ok});if(!ok)errors.push({kind:'stair',id:s.id,hits});
 }
 // 半格网格、0.6 格宽和 1.8 格高的静态包围盒。允许每次升降半格。
 const pitch=.5,nx=128,nz=144,radius=.3,columns=Array.from({length:nx*nz},()=>({blocks:[],levels:new Map(),nodes:[]}));
 for(const b of d.boxes){
  const i0=Math.max(0,Math.floor((b.x0-radius)/pitch)),i1=Math.min(nx-1,Math.floor((b.x1+radius)/pitch));
  const j0=Math.max(0,Math.floor((b.z0-radius)/pitch)),j1=Math.min(nz-1,Math.floor((b.z1+radius)/pitch));
  const surface=['terrain','floor','stair'].includes(b.kind)&&b.y1>=0;
  for(let j=j0;j<=j1;j++)for(let i=i0;i<=i1;i++){
   const x=(i+.5)*pitch,z=(j+.5)*pitch,c=columns[j*nx+i];
   if(overlap(x-radius,x+radius,b.x0,b.x1)&&overlap(z-radius,z+radius,b.z0,b.z1))c.blocks.push(b);
   // 在踏步边缘，脚部包围盒先接触较高踏步，身体随后升高半格。
   // 支承面按脚部范围采样，避免把每个半格踏步边缘误判成不连续。
   if(surface&&overlap(x-radius,x+radius,b.x0,b.x1)&&overlap(z-radius,z+radius,b.z0,b.z1)){const key=Math.round(b.y1*2);c.levels.set(key,Math.max(b.y1,c.levels.get(key)??-Infinity));}
  }
 }
 const nodes=[];
 for(let j=0;j<nz;j++)for(let i=0;i<nx;i++){
  const c=columns[j*nx+i];for(const y of c.levels.values()){
   if(c.blocks.some(b=>overlap(y+.061,y+1.8,b.y0,b.y1)))continue;
   const node={id:nodes.length,i,j,y,x:(i+.5)*pitch,z:(j+.5)*pitch,seen:false};nodes.push(node);c.nodes.push(node);
  }
 }
 const start=nodes.filter(n=>n.x>=30.5&&n.x<=32.5&&n.z<1&&n.y<.2);const queue=[...start];start.forEach(n=>n.seen=true);
 for(let q=0;q<queue.length;q++){
  const a=queue[q];for(const [di,dj] of [[1,0],[-1,0],[0,1],[0,-1]]){const i=a.i+di,j=a.j+dj;if(i<0||i>=nx||j<0||j>=nz)continue;for(const b of columns[j*nx+i].nodes){if(!b.seen&&Math.abs(b.y-a.y)<=.55){b.seen=true;queue.push(b);}}}
 }
 for(let i=0;i<d.stairs.length;i++)stairChecks[i].stepReach=d.stairs[i].steps.map(s=>{const st=d.stairs[i],x=st.axis==='z'?(st.a+st.b)/2:s.p+.5,z=st.axis==='z'?s.p+.5:(st.a+st.b)/2;return nodes.some(n=>Math.abs(n.x-x)<.6&&Math.abs(n.z-z)<.6&&Math.abs(n.y-s.top)<.15&&n.seen);});
 const roomChecks=d.rooms.map(room=>{const matches=nodes.filter(n=>n.x>room.bounds[0]+.3&&n.x<room.bounds[1]-.3&&n.z>room.bounds[2]+.3&&n.z<room.bounds[3]-.3&&Math.abs(n.y-room.y)<.15);return {name:room.name,y:room.y,walkableNodes:matches.length,reachableNodes:matches.filter(n=>n.seen).length,reachable:matches.some(n=>n.seen)};});
 for(const room of roomChecks)if(!room.reachable)errors.push({kind:'unreachable_room',...room});
 results.push({name:d.name,dimensions:d.size,reserve:d.reserve,boxes:d.boxes.length,doors:doorChecks,stairs:stairChecks,rooms:roomChecks,navigation:{pitch,bodyWidth:radius*2,bodyHeight:1.8,nodes:nodes.length,reached:queue.length},errors,passed:errors.length===0});
}
const report={scope:'独立网页模型的尺寸、门洞、楼梯净空和半格网格连通。游戏碰撞未验证。',results};
const target=process.argv[2]||path.join(__dirname,'geometry-checks.json');fs.writeFileSync(target,JSON.stringify(report,null,2));
console.log(JSON.stringify(results.map(d=>({name:d.name,passed:d.passed,rooms:d.rooms.map(r=>[r.name,r.reachable]),stairs:d.stairs,errors:d.errors})),null,2));
if(results.some(r=>!r.passed))process.exitCode=1;
