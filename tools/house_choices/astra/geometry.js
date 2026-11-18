/* 从已确认的三套独立住宅中提取的纯几何输入；网页原稿保存在 viewer.html。 */
'use strict';
let boxes=[],rooms=[],doors=[],beds=[],fixtures=[],currentBounds=[];
const hash=(a,b)=>{let n=Math.imul(a+37,374761393)^Math.imul(b+19,668265263);n=Math.imul(n^(n>>>13),1274126177);return ((n^(n>>>16))>>>0)/4294967295;};
function box(x0,x1,y0,y1,z0,z1,mat,opt={}){if(x1<=x0||y1<=y0||z1<=z0)return;boxes.push({x0,x1,y0,y1,z0,z1,mat:typeof mat==='string'?mat:palettes[mat][0],...opt});}
function base(x0,x1,z0,z1,y,mat='石砖'){box(x0,x1,0,y,z0,z1,mat);box(x0,x1,y-.5,y,z0,z1,'云杉木板');}
function paving(x0,x1,z0,z1,y=0,mat='砂砾'){box(x0,x1,y-.15,y,z0,z1,mat);}
function wall(axis,start,end,fixed,y0,y1,mat,holes=[],timber=null,opt={}){
 for(let p=start;p<end;p++)for(let y=y0;y<y1;y+=1){let top=Math.min(y+1,y1);if(holes.some(h=>p>=h.a&&p+1<=h.b&&y>=h.lo-.01&&top<=h.hi+.01))continue;let m=timber&&(p===start||p===end-1||(p-start)%6===0||y>=y1-1)?timber:mat;if(axis==='x')box(p,p+1,y,top,fixed,fixed+1,m,opt);else box(fixed,fixed+1,y,top,p,p+1,m,opt);}
 holes.forEach(h=>{if(h.kind==='door')doors.push({axis,fixed,a:h.a,b:h.b,y:h.lo,height:h.hi-h.lo});if(h.kind!=='window')return;const mid=(h.a+h.b)/2,frame=timber||'去皮云杉木',depth=.16;
 if(axis==='x'){box(h.a,h.b,h.lo,h.hi,fixed+.46,fixed+.54,'玻璃',opt);box(mid-.08,mid+.08,h.lo,h.hi,fixed+.35,fixed+.65,frame,opt);box(h.a,h.b,h.lo-.12,h.lo+.12,fixed-.12,fixed+1.12,frame,opt);box(h.a,h.b,h.hi-.1,h.hi+.1,fixed+.2,fixed+.8,frame,opt);}else{box(fixed+.46,fixed+.54,h.lo,h.hi,h.a,h.b,'玻璃',opt);box(fixed+.35,fixed+.65,h.lo,h.hi,mid-.08,mid+.08,frame,opt);box(fixed-.12,fixed+1.12,h.lo-.12,h.lo+.12,h.a,h.b,frame,opt);box(fixed+.2,fixed+.8,h.hi-.1,h.hi+.1,h.a,h.b,frame,opt);}
 });
}
const door=(a,b,y,h=3)=>({a,b,lo:y,hi:y+h,kind:'door'}),win=(a,b,y)=>({a,b,lo:y+2,hi:y+4,kind:'window'});
function doorLeaves(){}
function gable(x0,x1,z0,z1,eave,axis,roofMat,wallMat,beam='去皮云杉木'){
 const opt={roof:true},a=axis==='x'?z0:x0,b=axis==='x'?z1:x1;
 for(let k=a;k<b;k++){const top=eave+.5*Math.min(k-a,b-1-k)+.5;if(axis==='x'){box(x0,x1,top-.5,top,k,k+1,roofMat,opt);if(k>a&&k<b-1){box(x0+1,x0+2,eave,top-.5,k,k+1,wallMat,opt);box(x1-2,x1-1,eave,top-.5,k,k+1,wallMat,opt);}}else{box(k,k+1,top-.5,top,z0,z1,roofMat,opt);if(k>a&&k<b-1){box(k,k+1,eave,top-.5,z0+1,z0+2,wallMat,opt);box(k,k+1,eave,top-.5,z1-2,z1-1,wallMat,opt);}}}
 if(axis==='x'){for(let x=x0+2;x<x1-1;x+=6)box(x,x+.45,eave-.5,eave,z0+1,z1-1,beam,opt);box(x0+1,x1-1,eave+(b-a)/4-1,eave+(b-a)/4-.5,(z0+z1)/2-.25,(z0+z1)/2+.25,beam,opt);}else{for(let z=z0+2;z<z1-1;z+=5)box(x0+1,x1-1,eave-.5,eave,z,z+.45,beam,opt);}
}
function lean(x0,x1,z0,z1,lo,axis,reverse=false,mat='深板岩瓦'){
 const a=axis==='x'?x0:z0,b=axis==='x'?x1:z1;
 for(let k=a;k<b;k++){let y=lo+.5*(reverse?b-1-k:k-a);if(axis==='x')box(k,k+1,y,y+.5,z0,z1,mat,{roof:true});else box(x0,x1,y,y+.5,k,k+1,mat,{roof:true});}
}
function stairs(axis,start,end,across0,across1,baseY,mat='石砖',opt={}){for(let k=start;k<end;k++){let y=baseY+.5*(k-start+1);if(axis==='z')box(across0,across1,baseY,y,k,k+1,mat,opt);else box(k,k+1,baseY,y,across0,across1,mat,opt);}}
function post(x,z,bottom,top,mat='去皮云杉木'){box(x,x+1,bottom,top,z,z+1,mat);}
function table(x,z,y,w=3,d=2,mat='云杉木板'){box(x,x+w,y+.9,y+1.15,z,z+d,mat);[[x,z],[x+w-.3,z],[x,z+d-.3],[x+w-.3,z+d-.3]].forEach(([a,b])=>box(a,a+.3,y,y+1,b,b+.3,'深色原木'));}
function seat(x,z,y,axis='x'){box(x,x+(axis==='x'?2:1),y+.35,y+.6,z,z+(axis==='x'?1:2),'云杉木板');box(x,x+.25,y,y+.4,z,z+.8,'深色原木');}
function bed(x,z,y){beds.push({x,z,y});}
function chest(x,z,y,w=2){fixtures.push({x,z,y,w});}
function shelf(x,z,y,axis='x',n=3){for(let i=0;i<n;i++){let a=x+(axis==='x'?i:0),b=z+(axis==='z'?i:0);chest(a,b,y,1);chest(a,b,y+1.35,1);}if(axis==='x'){box(x,x+n,y+1.2,y+1.35,z,z+1,'云杉木板');box(x,x+.25,y,y+2.3,z,z+1,'去皮云杉木');box(x+n-.25,x+n,y,y+2.3,z,z+1,'去皮云杉木');}else{box(x,x+1,y+1.2,y+1.35,z,z+n,'云杉木板');box(x,x+1,y,y+2.3,z,z+.25,'去皮云杉木');box(x,x+1,y,y+2.3,z+n-.25,z+n,'去皮云杉木');}}
function garden(x0,x1,z0,z1){paving(x0,x1,z0,z1,.12,'泥土');box(x0-.25,x1+.25,0,.35,z0-.25,z0,'去皮云杉木');box(x0-.25,x1+.25,0,.35,z1,z1+.25,'去皮云杉木');box(x0-.25,x0,0,.35,z0,z1,'去皮云杉木');box(x1,x1+.25,0,.35,z0,z1,'去皮云杉木');for(let x=x0+1;x<x1;x+=2)for(let z=z0+1;z<z1;z+=3){box(x,x+.65,.12,.7,z,z+.65,hash(x,z)>.5?'树叶':'麦田');}}
function railing(axis,start,end,fixed,y,mat='去皮云杉木',gaps=[]){for(let p=start;p<end;p+=3){if(gaps.some(g=>p>=g[0]&&p<g[1]))continue;if(axis==='x')box(p,p+.25,y,y+1.2,fixed,fixed+.25,mat);else box(fixed,fixed+.25,y,y+1.2,p,p+.25,mat);}for(let p=start;p<end;p++){if(gaps.some(g=>p>=g[0]&&p<g[1]))continue;if(axis==='x')box(p,p+1,y+.7,y+.95,fixed,fixed+.2,mat);else box(fixed,fixed+.2,y+.7,y+.95,p,p+1,mat);}}
function room(text,x,y,z,level=1){rooms.push({text,p:[x,y,z],level});}
function common(bounds){ currentBounds=bounds; }
function buildA(){common([8,56,48]);
 paving(30,34,0,28,0,'泥土径');paving(24,40,10,27,0,'砂砾');paving(30,34,10,28,.02,'石砖');garden(25,29,12,24);garden(35,39,12,24);
 base(18,46,30,44,1);base(11,21,12,28,1);base(43,53,12,28,1);base(21,25,12,30,1);base(39,43,12,30,1);base(21,43,27,30,1);
 stairs('z',26,28,26,30,0);stairs('z',10,12,21,24,0);stairs('z',10,12,40,43,0);
 wall('x',18,46,30,1,7,'方解石',[door(26,29,1,4),win(20,24,1),win(38,42,1)],'去皮云杉木');wall('x',18,46,43,1,7,'方解石',[win(23,27,1),win(38,42,1)],'去皮云杉木');wall('z',31,43,18,1,7,'方解石',[win(35,39,1)],'去皮云杉木');wall('z',31,43,45,1,7,'方解石',[win(35,39,1)],'去皮云杉木');wall('z',31,43,34,1,7,'方解石',[door(34,36,1)],'去皮云杉木');doorLeaves('x',30,26,29,1,3.8);doorLeaves('z',34,34,36,1);
 for(const [a,b,isWest] of [[11,21,true],[43,53,false]]){wall('x',a,b,12,1,7,'方解石',[win(a+3,a+6,1)],'去皮云杉木');wall('x',a,b,27,1,7,'方解石',[win(a+3,a+6,1)],'去皮云杉木');wall('z',13,27,a,1,7,'方解石',isWest?[win(17,20,1)]:[door(16,18,1),win(21,24,1)],'去皮云杉木');wall('z',13,27,b-1,1,7,'方解石',isWest?[door(16,18,1),win(23,26,1)]:[win(17,20,1)],'去皮云杉木');}
 wall('x',12,20,21,1,7,'方解石',[door(15,17,1)],'去皮云杉木');doorLeaves('z',20,16,18,1);doorLeaves('z',43,16,18,1);doorLeaves('x',21,15,17,1);
 gable(17,47,29,45,7,'x','深板岩瓦','方解石');gable(10,22,11,29,7,'z','深板岩瓦','方解石');gable(42,54,11,29,7,'z','深板岩瓦','方解石');
 lean(20,25,11,31,5,'x',true);lean(39,44,11,31,5,'x');lean(21,43,27,31,5,'z');
 for(const z of [12,18,24,29]){post(24,z,1,5.4);post(39,z,1,5.4);}box(24,24.65,5,5.5,11,31,'去皮云杉木');box(39,39.65,5,5.5,11,31,'去皮云杉木');for(const x of [21,30,42])post(x,27,1,5.5);box(21,43,5,5.5,27,27.65,'去皮云杉木');
 post(29,1,0,4);post(34,1,0,4);post(29,3,0,4);post(34,3,0,4);box(29,34.65,3.6,4.1,1,1.65,'去皮云杉木');box(29,34.65,3.6,4.1,3,3.65,'去皮云杉木');gable(28,36,0,5,4,'x','深板岩瓦','去皮云杉木');
 railing('x',8,29,0,0);railing('x',35,56,0,0);railing('z',0,48,8,0);railing('z',0,48,55.7,0);
 table(25,36,1,4,2);seat(25,34.5,1);seat(25,39,1);paving(23,31,34,41,1.03,'灰色地毯');bed(40,39,1);chest(36,40,1);table(38,32,1,2,1);shelf(12,24,1,'x',6);
 box(12,15,1,2,18,20,'熔炉');box(12,13,2,11.5,19,20,'红砖');table(15,14,1,3,2);chest(18,18,1,1);table(47,22,1,3,2);seat(47,20,1);paving(46,50,15,19,1.05,'灰色地毯');shelf(50,23,1,'z',3);
 room('起居室',26,1.1,37);room('卧室',40,1.1,37);room('厨房',16,1.1,17);room('储藏',16,1.1,25);room('书房',48,1.1,20);room('内院',32,.1,20);
}
function buildB(){common([14,50,40]);
 paving(30,34,0,8,0,'石砖');paving(22,42,1,7,0,'砂砾');paving(30,34,0,8,.03,'石砖');garden(18,26,1,5);garden(38,46,1,5);base(18,46,12,34,1);base(22,42,8,12,1);stairs('z',6,8,30,34,0);
 base(46,50,23,35,1);railing('z',23,35,49.7,1);railing('x',46,50,23,1);railing('x',46,50,34.7,1);
 wall('x',18,46,12,1,6.5,'红色陶瓦',[door(31,34,1,4),win(21,25,1),win(39,43,1)],'深色原木');wall('x',18,46,33,1,6.5,'红色陶瓦',[win(21,25,1),win(31,36,1),win(40,43,1)],'深色原木');wall('z',13,33,18,1,6.5,'红色陶瓦',[win(16,19,1),win(21,24,1)],'深色原木');wall('z',13,33,45,1,6.5,'红色陶瓦',[win(17,20,1),door(30,32,1)],'深色原木');
 wall('z',13,33,28,1,6.5,'白桦木板',[door(15,17,1),door(21,23,1)],'深色原木');wall('x',19,28,27,1,6.5,'白桦木板',[door(22,24,1)],'深色原木');wall('z',13,28,39,1,6.5,'白桦木板',[door(14,17,1)],'深色原木');
 doorLeaves('x',12,31,34,1,3.8);doorLeaves('z',28,21,23,1);doorLeaves('x',27,22,24,1);doorLeaves('z',45,30,32,1);
 stairs('z',14,26,40,43,1,'深色橡木');box(40,45,6.5,7,26,29,'深色橡木');
 box(18,40,6.5,7,12,34,'白桦木板');box(43,46,6.5,7,12,34,'白桦木板');box(40,43,6.5,7,12,14,'白桦木板');box(40,43,6.5,7,28,34,'白桦木板');
 for(const x of [19,28,39,44])box(x,x+.5,6,6.5,13,33,'深色原木');
 wall('x',18,46,12,7,12,'白桦木板',[win(21,25,7),win(31,36,7),win(40,43,7)],'深色原木');wall('x',18,46,33,7,12,'白桦木板',[win(21,25,7),win(31,36,7)],'深色原木');wall('z',13,33,18,7,12,'白桦木板',[win(17,21,7),win(26,30,7)],'深色原木');wall('z',13,33,45,7,12,'白桦木板',[win(17,21,7),win(28,31,7)],'深色原木');
 wall('z',13,33,28,7,12,'白桦木板',[door(29,31,7)],'深色原木');wall('z',13,27,39,7,12,'白桦木板',[],'深色原木');wall('x',29,39,26,7,12,'白桦木板',[door(33,35,7)],'深色原木');doorLeaves('z',28,29,31,7);doorLeaves('x',26,33,35,7);
 railing('z',14,26,43,7);railing('x',40,43,14,7);gable(17,47,11,35,12,'x','深色橡木','白桦木板','深色原木');lean(21,43,7,12,5,'z',false,'深色橡木');for(const x of [22,28,36,41])post(x,8,1,5.7,'深色原木');box(21,43,5,5.5,8,8.65,'深色原木');
 box(19,21,1,2,24,27,'熔炉');box(19,21,2,19,25,27,'红砖');table(22,16,1);seat(22,14.5,1);shelf(20,31,1,'x',6);shelf(20,29,1,'x',4);paving(30,38,24,32,1.03,'灰色地毯');table(32,26,1);seat(32,24,1);seat(32,29,1);table(41,30,1,2,1);bed(23,29,7);chest(20,14,7);shelf(20,24,7,'x',3);table(32,17,7,3,2);paving(31,36,21,25,7.04,'灰色地毯');shelf(35,14,7,'x',3);
 room('门厅',33,1.1,16);room('起居室',34,1.1,26);room('厨房',23,1.1,21);room('储藏',23,1.1,30);room('楼梯 ↑',42,4,21);room('卧室',23,7.1,23,2);room('书房',34,7.1,20,2);room('楼上过厅',35,7.1,30,2);room('楼梯口',42,7.1,26,2);
}
function buildC(){common([8,56,44]);
 paving(29,33,0,24,0,'泥土径');paving(27,52,5,20,0,'砂砾');paving(29,33,0,24,.02,'石砖');garden(39,50,6,14);paving(34,48,16,20,.03,'泥土径');garden(15,23,1,5);
 base(12,26,8,24,1);base(12,37,24,38,1);base(36,50,24,38,3);base(26,36,20,24,1);stairs('z',18,20,28,32,0);box(27,28,0,.5,15,18,'石砖');box(26,27,0,1,15,18,'石砖');base(50,54,26,36,3);
 wall('x',12,26,8,1,6,'平滑砂岩',[win(16,21,1)]);wall('z',9,24,12,1,6,'平滑砂岩',[win(14,18,1)]);wall('z',9,24,25,1,6,'平滑砂岩',[door(15,17,1),win(19,22,1)]);wall('x',13,25,23,1,6,'平滑砂岩',[door(21,24,1)]);doorLeaves('z',25,15,17,1);doorLeaves('x',23,21,24,1);
 wall('x',12,37,24,1,7,'平滑砂岩',[door(21,24,1),door(28,31,1,4)]);wall('x',12,37,37,1,7,'平滑砂岩',[win(14,18,1),win(23,28,1)]);wall('z',25,37,12,1,7,'平滑砂岩',[]);wall('z',25,37,19,1,7,'平滑砂岩',[door(29,31,1)]);doorLeaves('x',24,28,31,1,3.8);doorLeaves('z',19,29,31,1);
 wall('x',36,50,24,3,9,'平滑砂岩',[win(40,45,3)]);wall('x',36,50,37,3,9,'平滑砂岩',[win(40,45,3)]);wall('z',25,37,36,3,9,'平滑砂岩',[door(28,31,3)]);wall('z',25,37,49,3,9,'平滑砂岩',[door(29,31,3),win(33,35,3)]);doorLeaves('z',36,28,31,3);doorLeaves('z',49,29,31,3);
 stairs('x',31,35,28,31,1);box(35,37,1,3,28,31,'石砖');
 box(11,27,6,6.5,7,25,'氧化的切制铜',{roof:true});for(const z of [9,14,19,23])box(12,26,5.5,6,z,z+.5,'金合欢木',{roof:true});
 box(11,37,7,7.5,23,33,'平滑砂岩',{roof:true});box(11,13,7,7.5,33,37,'平滑砂岩',{roof:true});box(13,33,7,7.5,33,37,'云杉木板',{roof:true});box(33,37,7,7.5,33,37,'平滑砂岩',{roof:true});box(11,37,7,7.5,37,39,'平滑砂岩',{roof:true});for(const x of [13,19,25,31,35])box(x,x+.5,6.5,7,24,38,'金合欢木',{roof:true});for(const x of [19,25,31]){post(x,25,1,6.5,'金合欢木');post(x,36,1,6.5,'金合欢木');}box(12,36,6.5,7,31.25,31.75,'金合欢木',{roof:true});for(const x of [19,25,31,35])post(x,31.25,1,6.5,'金合欢木');
 // 屋顶露台栏墙与铺面同属屋顶，去顶时一同隐藏。
 box(11,35,7.5,8.5,23,23.5,'平滑砂岩',{roof:true});box(11,35,7.5,8.5,38.5,39,'平滑砂岩',{roof:true});box(11,11.5,7.5,8.5,23,35,'平滑砂岩',{roof:true});box(34.5,35,7.5,8.5,23,39,'平滑砂岩',{roof:true});box(14,18,7.5,7.6,29,33,'灰色地毯',{roof:true});
 const terraceFurnitureStart=boxes.length;table(20,34,7.5,3,2);seat(16,35,7.5);seat(25,35,7.5);for(const [x,z] of [[29,25],[29,29],[13,25]]){box(x,x+3,7.5,8,z,z+2,'红色陶瓦');box(x+.25,x+2.75,8,8.15,z+.25,z+1.75,'泥土');box(x+.5,x+2.5,8.15,8.65,z+.5,z+1.5,'树叶');}for(let i=terraceFurnitureStart;i<boxes.length;i++)boxes[i].roof=true;
 gable(35,51,23,39,9,'z','氧化的切制铜','平滑砂岩','金合欢木');
 box(26,36,5.5,6,20,25,'氧化的切制铜',{roof:true});post(26,20,1,5.5,'金合欢木');post(35,20,1,5.5,'金合欢木');box(26,36,5,5.5,20,20.65,'金合欢木');
 box(8,12,0,.5,20,22,'石砖');stairs('z',22,36,8,11,.5);box(8,13,0,7.5,36,39,'石砖');railing('z',26,36,53.7,3,'金合欢木');railing('x',50,54,26,3,'金合欢木');railing('x',50,54,35.7,3,'金合欢木');
 for(let z=22;z<36;z+=2){let y=.5+.5*(z-22+1);box(8,8.25,y,y+1.2,z,z+.25,'金合欢木');box(10.75,11,y,y+1.2,z,z+.25,'金合欢木');}for(let z=22;z<36;z++){let y=.5+.5*(z-22+1);box(8,8.2,y+.8,y+1,z,z+1,'金合欢木');box(10.8,11,y+.8,y+1,z,z+1,'金合欢木');}
 box(13,16,1,2,18,21,'熔炉');box(13,14,2,8,20,21,'石砖');table(18,12,1);shelf(13,34,1,'x',5);shelf(13,26,1,'z',6);table(21,33,1);seat(21,35.5,1);paving(22,29,31,36,1.03,'灰色地毯');bed(44,33,3);chest(38,34,3);table(40,26,3,3,1);seat(51,31,3,'z');
 room('厨房',19,1.1,16);room('储藏',16,1.1,31);room('起居室',27,1.1,33);room('卧室 +2',43,3.1,31);room('庭院',33,.1,13);room('卧室露台',52,3.1,31);room('屋顶楼梯 ↑',9.5,5,30);
}
const cfg=[
 {name:'松廊小院',bounds:[8,56,48],summary:'48×48 格 · 三面围院 · 单层 · 北侧完整留白 64×24 格',eye:[78,54,-34],target:[32,3,23],person:[32,1.7,13],aim:[27.5,3.3,31],span:61},
 {name:'赤陶长屋',bounds:[14,50,40],summary:'36×40 格 · 两层长屋 · 集中起居与家务 · 北侧完整留白 64×32 格',eye:[-15,44,-35],target:[32,7,21],person:[32,1.7,4],aim:[32,4,13],span:54},
 {name:'石阶露台居',bounds:[8,56,44],summary:'48×44 格 · 石砌错层 · 可登屋顶露台 · 北侧完整留白 64×28 格',eye:[79,48,-31],target:[32,4,22],person:[32,1.7,12],aim:[29,3.2,25],span:60}
];

const designs=[buildA,buildB,buildC].map((f,i)=>{boxes=[];rooms=[];doors=[];beds=[];fixtures=[];f();return {...cfg[i],id:['pine-gallery','terracotta-longhouse','stone-terrace'][i],boxes,rooms,doors,beds,fixtures,bounds:currentBounds};});
module.exports={designs};
