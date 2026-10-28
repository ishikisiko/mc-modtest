// Depth-buffered CPU fallback for browsers without WebGL. It renders the same
// mesh positions, smooth normals and material parameters, not a replacement image.
let softCtx=null,softBuffer=null,softDepth=null,softW=0,softH=0;
function softInit(){softCtx=canvas.getContext('2d',{alpha:false});if(!softCtx)throw Error('Canvas 2D 与 WebGL 均不可用')}
function softPoint(m,x,y,z,ox,oy,w,h){const q=m[3]*x+m[7]*y+m[11]*z+m[15];return [ox+(m[0]*x+m[4]*y+m[8]*z+m[12])/q*w*.5+w*.5,oy-(m[1]*x+m[5]*y+m[9]*z+m[13])/q*h*.5+h*.5,(m[2]*x+m[6]*y+m[10]*z+m[14])/q]}
function softShade(px,py,pz,nx,ny,nz,eye,mat){
 const mode=state.shade;
 if(mode==='silhouette')return state.dark?[209,224,230]:[26,41,51];
 const unit=(x,y,z)=>{let l=Math.sqrt(x*x+y*y+z*z)||1;return [x/l,y/l,z/l]};
 let V=unit(eye[0]-px,eye[1]-py,eye[2]-pz),N=unit(nx,ny,nz),nv=N[0]*V[0]+N[1]*V[1]+N[2]*V[2];if(nv<0){N=N.map(x=>-x);nv=-nv}nv=Math.max(nv,.001);
 const base=mode==='clay'?[.48,.49,.48]:(mat.linear||(mat.linear=hex(mat.color))),metal=mode==='clay'?.08:mat.metal||0,rough=mode==='clay'?.62:Math.max(.10,mat.rough??.5),F0=base.map(c=>.04*(1-metal)+c*metal),col=[0,0,0];
 const lights=[[-.4683,.6690,.5854,3.6,3.4,3.1],[.7605,.4056,.5070,1.3,1.6,1.9],[.176,.630,-.756,3.2,3.6,3.8]];
 for(const l of lights){const nl=Math.max(0,N[0]*l[0]+N[1]*l[1]+N[2]*l[2]);if(nl===0)continue;const H=unit(V[0]+l[0],V[1]+l[1],V[2]+l[2]);let nh=Math.max(0,N[0]*H[0]+N[1]*H[1]+N[2]*H[2]),vh=Math.max(0,V[0]*H[0]+V[1]*H[1]+V[2]*H[2]);const a=rough*rough,a2=a*a,dd=nh*nh*(a2-1)+1,D=a2/Math.max(.0001,Math.PI*dd*dd),k=(rough+1)**2*.125,G=(nv/(nv*(1-k)+k))*(nl/(nl*(1-k)+k));const fr=(1-vh)**5;
  for(let c=0;c<3;c++){const F=F0[c]+(1-F0[c])*fr;col[c]+=((1-F)*(1-metal)*base[c]/Math.PI+D*G*F/Math.max(.0001,4*nv*nl))*l[c+3]*nl;}}
 const hemi=.32+.22*(N[1]*.5+.5),R=[-V[0]+2*nv*N[0],-V[1]+2*nv*N[1],-V[2]+2*nv*N[2]],ba=Math.pow(Math.max(0,R[0]*-.5503+R[1]*.6003+R[2]*.5803),85+(5-85)*rough),bb=Math.pow(Math.max(0,R[0]*.8414+R[1]*.3236+R[2]*-.4315),110+(7-110)*rough),fr=(1-nv)**5;
 for(let c=0;c<3;c++){const env=[.22,.25,.28][c]+[1.35,1.25,1.10][c]*ba+[.60,.80,1.00][c]*bb;col[c]+=base[c]*(1-metal)*hemi+env*(F0[c]+(1-F0[c])*fr)*(.65+.40*metal);if(mat.kind===2)col[c]+=base[c]*(1-nv)**4*.18;if(mode==='material')col[c]+=base[c]*(mat.emission||0);let x=col[c]*.95;x=(x*(2.51*x+.03))/(x*(2.43*x+.59)+.14);col[c]=Math.pow(Math.max(0,Math.min(1,x)),1/2.2)*255;}return col;
}
function softRaster(screen,colors,faces,alpha,viewport){
 const pix=softBuffer.data,W=softW,H=softH,depth=softDepth,clipLeft=viewport[0],clipRight=viewport[0]+viewport[2]-1;
 for(let fi=0;fi<faces.length;fi+=3){const ia=faces[fi]*3,ib=faces[fi+1]*3,ic=faces[fi+2]*3;const ax=screen[ia],ay=screen[ia+1],az=screen[ia+2],bx=screen[ib],by=screen[ib+1],bz=screen[ib+2],cx=screen[ic],cy=screen[ic+1],cz=screen[ic+2];
  if(az<-1||bz<-1||cz<-1||az>1||bz>1||cz>1)continue;
  let den=(by-cy)*(ax-cx)+(cx-bx)*(ay-cy);if(Math.abs(den)<.035)continue;
  const minx=Math.max(clipLeft,Math.floor(Math.min(ax,bx,cx))),maxx=Math.min(clipRight,Math.ceil(Math.max(ax,bx,cx))),miny=Math.max(0,Math.floor(Math.min(ay,by,cy))),maxy=Math.min(H-1,Math.ceil(Math.max(ay,by,cy)));if(minx>maxx||miny>maxy)continue;
  const aa=(by-cy)/den,ab=(cx-bx)/den,ba=(cy-ay)/den,bb=(ax-cx)/den;
  for(let y=miny;y<=maxy;y++){let u=aa*(minx+.5-cx)+ab*(y+.5-cy),v=ba*(minx+.5-cx)+bb*(y+.5-cy),idx=y*W+minx;
   for(let x=minx;x<=maxx;x++,idx++,u+=aa,v+=ba){const ww=1-u-v;if(u<-.001||v<-.001||ww<-.001)continue;const z=u*az+v*bz+ww*cz;if(z>=depth[idx])continue;const p=idx*4;
    if(alpha>.999){pix[p]=u*colors[ia]+v*colors[ib]+ww*colors[ic];pix[p+1]=u*colors[ia+1]+v*colors[ib+1]+ww*colors[ic+1];pix[p+2]=u*colors[ia+2]+v*colors[ib+2]+ww*colors[ic+2];depth[idx]=z;}
    else{pix[p]=pix[p]*(1-alpha)+(u*colors[ia]+v*colors[ib]+ww*colors[ic])*alpha;pix[p+1]=pix[p+1]*(1-alpha)+(u*colors[ia+1]+v*colors[ib+1]+ww*colors[ic+1])*alpha;pix[p+2]=pix[p+2]*(1-alpha)+(u*colors[ia+2]+v*colors[ib+2]+ww*colors[ic+2])*alpha;}
   }
  }
 }
}
function softView(m,ox,oy,w,h,shared){
 const ms=shown(m);if(!ms.length)return;const bb=bounds(ms),aspect=w/h;let target,dist;if(state.part==='all'){target=[0,1.19,0];dist=Math.max(2.55/2/Math.tan(.34),1.64/aspect/2/Math.tan(.34))*1.08}else{const b=shared||bb;target=b.center;dist=Math.max(.32,Math.max(b.size[1],b.size[0]/aspect,b.size[2])*.5/Math.tan(.34)*1.34)}dist*=state.zoom;
 const eye=[target[0]+Math.sin(state.yaw)*Math.cos(state.pitch)*dist,target[1]+Math.sin(state.pitch)*dist,target[2]+Math.cos(state.yaw)*Math.cos(state.pitch)*dist],vp=multiply(perspective(.68,aspect,.025,40),lookAt(eye,target));
 if(['all','boots','ingot'].includes(state.part)){
  const cp=softPoint(vp,0,bb.min[1],0,ox,oy,w,h),rx=w*(state.part==='all'?.18:.23)/state.zoom,ry=h*.018/state.zoom,pix=softBuffer.data;
  for(let y=Math.max(0,Math.floor(cp[1]-ry*3));y<Math.min(softH,cp[1]+ry*3);y++)for(let x=Math.max(ox,Math.floor(cp[0]-rx*3));x<Math.min(ox+w,cp[0]+rx*3);x++){const f=1-.17*Math.exp(-(((x-cp[0])/rx)**2+((y-cp[1])/ry)**2));const p=(y*softW+x)*4;pix[p]*=f;pix[p+1]*=f;pix[p+2]*=f;}}
 const ordered=[...ms].sort((a,b)=>(m.materials[a.material].alpha??1)-(m.materials[b.material].alpha??1)).reverse();const wire=[];
 for(const mesh of ordered){const p=mesh.positions,n=mesh.normals,screen=new Float32Array(p.length),colors=new Float32Array(p.length),mat=m.materials[mesh.material];
  for(let i=0;i<p.length;i+=3){const xy=softPoint(vp,p[i],p[i+1],p[i+2],ox,oy,w,h);screen[i]=xy[0];screen[i+1]=xy[1];screen[i+2]=xy[2];if(state.shade!=='wire'){const c=softShade(p[i],p[i+1],p[i+2],n[i],n[i+1],n[i+2],eye,mat);colors[i]=c[0];colors[i+1]=c[1];colors[i+2]=c[2]}}
  if(state.shade==='wire')wire.push([screen,mesh.indices]);else softRaster(screen,colors,mesh.indices,state.shade==='material'?(mat.alpha??1):1,[ox,oy,w,h]);drawCalls++;
 }
 return wire;
}
function softRender(){
 const rect=canvas.getBoundingClientRect();const scale=Math.min(window.devicePixelRatio||1,1.4,1000/Math.max(rect.width,rect.height));const W=Math.round(rect.width*scale),H=Math.round(rect.height*scale);if(W<5||H<5)return;
 if(W!==softW||H!==softH){softW=W;softH=H;canvas.width=W;canvas.height=H;softBuffer=softCtx.createImageData(W,H);softDepth=new Float32Array(W*H)}
 const pix=softBuffer.data;softDepth.fill(Infinity);drawCalls=0;
 const top=state.dark?[48,63,77]:[225,230,230],bottom=state.dark?[17,27,37]:[174,188,198];
 for(let y=0;y<H;y++)for(let x=0;x<W;x++){const p=(y*W+x)*4;let f=Math.min(1,Math.sqrt(((x/W-.46)*.7)**2+((y/H-.24)*.9)**2));for(let c=0;c<3;c++)pix[p+c]=top[c]*(1-f)+bottom[c]*f;pix[p+3]=255;}
 let wires=[];
 if(state.compare&&otherModel){const half=Math.floor(W/2),bb=bounds([...shown(model),...shown(otherModel)]);wires.push(...softView(model,0,0,half,H,bb),...softView(otherModel,half,0,W-half,H,bb))}else wires=softView(model,0,0,W,H);
 softCtx.putImageData(softBuffer,0,0);
 if(state.shade==='wire'){softCtx.strokeStyle=state.dark?'#a2b8cd75':'#293b4d60';softCtx.lineWidth=.45;for(const [s,f] of wires){softCtx.beginPath();for(let i=0;i<f.length;i+=3){const a=f[i]*3,b=f[i+1]*3,c=f[i+2]*3;softCtx.moveTo(s[a],s[a+1]);softCtx.lineTo(s[b],s[b+1]);softCtx.lineTo(s[c],s[c+1]);softCtx.closePath()}softCtx.stroke()}}
 dirty=false;
}
