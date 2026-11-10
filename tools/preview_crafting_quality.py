#!/usr/bin/env python3
"""从实际配置导出可交互的成败、品质和丹方难度预览。"""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from tools import crafting_quality


def render(root=ROOT):
    root = Path(root)
    rules = crafting_quality.load(root)
    recipes = json.loads((root/'src/main/resources/data/myvillage/alchemy/rules.json').read_text())['recipes']
    names = json.loads((root/'src/main/resources/assets/myvillage/lang/zh_cn.json').read_text())
    payload = {'rules': rules, 'recipes': [dict(r, name=names['item.myvillage.'+r['formula'].split(':')[1]]) for r in recipes],
               'references': [crafting_quality.probabilities(rules, t['baseline']+60, t['id'], d)
                              for t in rules['tiers'] for d in range(rules['maximum_difficulty']+1)]}
    return TEMPLATE.replace('__DATA__', json.dumps(payload, ensure_ascii=False).replace('</', '<\\/'))


TEMPLATE = r'''<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><style>
:root{color-scheme:dark light}*{box-sizing:border-box}body{margin:0;font-family:var(--font-sans);color:var(--foreground)}p{line-height:1.65;margin:8px 0 14px}.muted,small{color:var(--muted-foreground)}.panel{background:var(--card);border-radius:var(--radius);padding:16px;margin:12px 0}.controls{display:flex;gap:12px;flex-wrap:wrap;align-items:end}label{display:flex;flex-direction:column;gap:6px;font-size:13px;flex:1;min-width:120px}select,input,button{font:inherit;color:var(--foreground);background:var(--secondary);border:1px solid var(--border);border-radius:6px;padding:8px;max-width:100%}input[type=range]{width:100%;padding:0;margin:14px 0}button{cursor:pointer}button:hover{border-color:var(--primary)}.formula{font-family:var(--font-mono);font-size:13px;line-height:1.9;white-space:normal}.chart{height:240px;position:relative}canvas{width:100%;height:240px;display:block}.probabilities{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:6px;margin-top:12px}.outcome{border-top:4px solid var(--color);padding-top:8px;font-size:13px}.value{display:block;font-variant-numeric:tabular-nums;margin-top:4px}.scroll{overflow-x:auto}table{width:100%;border-collapse:collapse;white-space:nowrap;font-size:12px}th,td{text-align:right;padding:10px 8px;border-bottom:1px solid var(--border)}th:first-child,td:first-child{text-align:left}th{font-weight:500;color:var(--muted-foreground)}h3{font-size:15px;margin:0 0 12px}.slots{display:flex;gap:8px;flex-wrap:wrap;margin:12px 0}.slot{border:1px solid var(--border);background:var(--secondary);min-width:76px;padding:10px;text-align:center;border-radius:6px}.slot b{display:block;font-size:13px;margin-bottom:5px}#simulation{font-size:13px}#recipeFacts{font-size:13px}.empty{color:var(--muted-foreground)}@media(max-width:450px){.panel{padding:12px}.probabilities{font-size:11px;gap:3px}.outcome{font-size:11px}.value{font-size:12px}th,td{padding:9px 6px}.formula{font-size:12px}}
</style></head><body>
<p>炼丹每颗独立判定，炼器每件判定一次。下图包含失败区间；丹方难度会同时移动四个阈值。</p>
<div class="panel"><div class="controls"><label>配方品阶<select id="tier"></select></label><label>资质 A<input id="aptitude" type="number" min="0" max="2147483647" value="60" step="1"></label><label>丹方难度<select id="difficulty"></select></label></div>
<input id="slider" type="range" min="0" max="200" value="60" aria-label="调整资质">
<div class="formula" id="formula"></div><div class="chart"><canvas id="curve" aria-label="正态分布与五种结果"></canvas></div><div class="probabilities" id="probabilities"></div>
<p class="muted" style="font-size:12px;margin-bottom:0">得分低于首个阈值时失败；恰好达到阈值时进入右侧结果。高资质继续平移分布。</p></div>
<div class="panel"><h3>各品阶在相对资质 60 时的概率</h3><div class="scroll"><table><thead><tr><th>品阶／资质</th><th>失败</th><th>下品</th><th>中品</th><th>上品</th><th>极品</th></tr></thead><tbody id="comparison"></tbody></table></div><p class="muted" style="font-size:12px;margin-bottom:0">比较使用当前所选难度。起点 B 与跨度 W 读取实际配置；当前高阶起点每阶增加 100，W 均为 100，属于本次接入的初始标尺。</p></div>
<div class="panel"><h3>用实际丹方模拟一炉</h3><label>选择丹方<select id="recipe"></select></label><p id="recipeFacts"></p><button id="simulate">模拟一炉</button><p id="simulation" aria-live="polite">选择丹方后，以当前资质进行独立抽取。</p><div class="slots" id="slots"></div><small>这里演示公式，游戏结果由服务器在开炉时确定。失败颗数不生成物品，整炉仍消耗配方的完整原料与燃料。</small></div>
<script>
const DATA=__DATA__,R=DATA.rules,labels=['失败','下品','中品','上品','极品'],tierNames=['炼气','筑基','金丹','元婴','化神'];
const $=id=>document.getElementById(id),fmt=x=>x>0&&x<.00001?'<0.001%':(x*100).toFixed(2)+'%';
const colorNames=['--destructive','--success','--info','--chart-4','--warning'];
function erfc(x){if(x===0)return 1;let z=Math.abs(x),t=1/(1+.5*z),v=t*Math.exp(-z*z-1.26551223+t*(1.00002368+t*(.37409196+t*(.09678418+t*(-.18628806+t*(.27886807+t*(-1.13520398+t*(1.48851587+t*(-.82215223+t*.17087277)))))))));return x>=0?v:2-v}
const cdf=x=>.5*erfc(-x/Math.sqrt(2));
function values(t,a,d){let mean=100*(a-t.baseline)/t.span,cuts=t.thresholds.map(x=>x+d*R.difficulty_threshold_step),prev=0,p=cuts.map(x=>{let c=cdf((x-mean)/R.standard_deviation),v=Math.max(0,c-prev);prev=c;return v});p.push(cdf((mean-cuts[3])/R.standard_deviation));return{mean,cuts,p}}
function colors(){let s=getComputedStyle(document.documentElement);return colorNames.map(n=>s.getPropertyValue(n).trim()||s.getPropertyValue('--foreground').trim())}
function draw(t,a,d){let {mean,cuts}=values(t,a,d),canvas=$('curve'),w=canvas.clientWidth,h=240,ratio=devicePixelRatio||1;canvas.width=w*ratio;canvas.height=h*ratio;let c=canvas.getContext('2d');c.scale(ratio,ratio);let cs=colors(),style=getComputedStyle(document.documentElement),text=style.getPropertyValue('--muted-foreground').trim(),lo=Math.min(cuts[0]-18,mean-4*R.standard_deviation),hi=Math.max(cuts[3]+18,mean+4*R.standard_deviation),x=v=>28+(w-48)*(v-lo)/(hi-lo),y=v=>198-v*150;
const density=v=>Math.exp(-.5*((v-mean)/R.standard_deviation)**2);c.font='11px sans-serif';
for(let k=0;k<5;k++){let begin=k===0?lo:cuts[k-1],end=k===4?hi:cuts[k];c.beginPath();c.moveTo(x(begin),198);for(let v=begin;v<end;v+=(hi-lo)/600)c.lineTo(x(v),y(density(v)));c.lineTo(x(end),y(density(end)));c.lineTo(x(end),198);c.closePath();c.globalAlpha=.3;c.fillStyle=cs[k];c.fill();}c.globalAlpha=1;c.strokeStyle=style.getPropertyValue('--foreground').trim();c.lineWidth=1.5;c.beginPath();for(let i=0;i<=600;i++){let v=lo+(hi-lo)*i/600;i?c.lineTo(x(v),y(density(v))):c.moveTo(x(v),y(density(v)))}c.stroke();
c.fillStyle=text;c.strokeStyle=style.getPropertyValue('--border').trim();c.textAlign='center';cuts.forEach(v=>{c.beginPath();c.moveTo(x(v),37);c.lineTo(x(v),201);c.stroke();c.fillText(String(v),x(v),219)});c.fillText('本次得分',w/2,238);c.fillText('平均分 '+mean.toFixed(1),Math.max(55,Math.min(w-55,x(mean))),24)}
function render(){let i=+$('tier').value,t=R.tiers[i],a=Math.max(0,Math.min(2147483647,Math.trunc(+$('aptitude').value)||0)),d=+$('difficulty').value;let v=values(t,a,d);$('slider').max=Math.max(t.baseline+2*t.span,a);$('slider').value=a;$('formula').textContent='平均分 = 100 × ('+a+' − '+t.baseline+') ÷ '+t.span+' = '+v.mean.toFixed(1)+'；本次得分 = 平均分 + '+R.standard_deviation+' × 标准正态随机数。\n实际阈值：'+v.cuts.join('、');$('probabilities').innerHTML=v.p.map((p,k)=>'<div class="outcome" style="--color:var('+colorNames[k]+')">'+labels[k]+'<strong class="value">'+fmt(p)+'</strong></div>').join('');$('comparison').innerHTML=R.tiers.slice(0,4).map((t,k)=>'<tr><td>'+tierNames[k]+'／'+(t.baseline+60)+'</td>'+values(t,t.baseline+60,d).p.map(p=>'<td>'+fmt(p)+'</td>').join('')+'</tr>').join('');draw(t,a,d)}
R.tiers.forEach((t,i)=>$('tier').add(new Option(tierNames[i]||t.id,i)));for(let i=0;i<=R.maximum_difficulty;i++)$('difficulty').add(new Option('难度 '+i,i));DATA.recipes.forEach((r,i)=>$('recipe').add(new Option(r.name,i)));
$('tier').onchange=()=>{$('aptitude').value=R.tiers[+$('tier').value].baseline+60;render()};$('aptitude').oninput=render;$('slider').oninput=()=>{$('aptitude').value=$('slider').value;render()};$('difficulty').onchange=render;
function chooseRecipe(){
 let r=DATA.recipes[+$('recipe').value],i=R.tiers.findIndex(t=>t.id===r.quality_tier);
 $('tier').value=i;$('difficulty').value=r.difficulty;
 $('aptitude').value=Math.max(+$('aptitude').value,R.tiers[i].baseline+60,r.minimum_aptitude||0);
 $('recipeFacts').textContent='每炉尝试 '+r.output_count+' 颗；门槛 '+Math.max(r.minimum_aptitude||0,R.tiers[i].baseline)+' 点；每颗独立判定。上图可以切换难度比较，模拟使用该丹方的实际品阶与难度。';
 $('simulate').textContent='模拟一炉（'+r.output_count+' 次独立判定）';render()
}
$('recipe').onchange=chooseRecipe;
function showSlots(counts){$('slots').innerHTML=counts.slice(1).map((n,k)=>'<div class="slot '+(n?'':'empty')+'"><b>'+labels[k+1]+'</b>'+(n?'× '+n:'空槽')+'</div>').join('')}
$('simulate').onclick=()=>{
 let r=DATA.recipes[+$('recipe').value],index=R.tiers.findIndex(t=>t.id===r.quality_tier),t=R.tiers[index];
 let a=Math.max(0,Math.min(2147483647,Math.trunc(+$('aptitude').value)||0)),d=r.difficulty;
 $('aptitude').value=a;$('tier').value=index;$('difficulty').value=d;render();
 if(a<Math.max(r.minimum_aptitude||0,t.baseline)){
  $('simulation').textContent='当前资质未达到丹方门槛，游戏中会拒绝开炉。';showSlots([0,0,0,0,0]);return
 }
 let {cuts,mean}=values(t,a,d),counts=[0,0,0,0,0],results=[];
 for(let i=0;i<r.output_count;i++){
  let u=0;while(!u)u=Math.random();
  let z=Math.sqrt(-2*Math.log(u))*Math.cos(2*Math.PI*Math.random()),score=mean+R.standard_deviation*z,q=cuts.findIndex(v=>score<v);
  if(q<0)q=4;counts[q]++;results.push('第 '+(i+1)+' 颗：'+labels[q]+'（得分 '+score.toFixed(1)+'）')
 }
 $('simulation').textContent=results.join('；')+'。炼成 '+(r.output_count-counts[0])+' 颗，失败 '+counts[0]+' 颗。';showSlots(counts)
};
let index=0;for(let t of R.tiers)for(let d=0;d<=R.maximum_difficulty;d++){let p=values(t,t.baseline+60,d).p,expected=DATA.references[index++];if(p.some((v,i)=>Math.abs(v-expected[i])>2e-7))throw Error('概率计算未通过独立参考核对')}
window.addEventListener('resize',render);render();chooseRecipe();showSlots([0,0,0,0,0]);
</script></body></html>'''


def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',type=Path,default=ROOT/'reports/crafting-quality/quality-preview.html')
    args=parser.parse_args(argv)
    args.out.parent.mkdir(parents=True,exist_ok=True)
    args.out.write_text(render(),encoding='utf-8')
    print(args.out)


if __name__=='__main__':
    main()
