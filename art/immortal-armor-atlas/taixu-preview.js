#!/usr/bin/env node
'use strict';
// A presentation wrapper for the single reviewed design. Geometry, materials,
// controls and exports come directly from the shared viewer and current author.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {assemble,compactHTML}=require('./core/build.js');
const args=process.argv.slice(2),out=path.resolve(args[0]);
const design=JSON.parse(fs.readFileSync(path.join(__dirname,'levels/level5.json'))).find(d=>d.id==='huashen_taixu');
assert(design);const thumbnails=args[1]?JSON.parse(fs.readFileSync(args[1])):null;
let html=assemble([design],null,{thumbnails});
for(const [from,to]of [
 ['15 套 · 60 件 · 15 种锭材','1 套 · 四件衣甲 · 配套灵材锭'],
 ['<h2>五境十五式</h2>','<h2>太虚无相 · 本轮精修</h2>'],
 ["d.id==='yuanying_xinghe'","d.id==='huashen_taixu'"],
 ['选择境界与样式，旋转查看完整衣甲和配套灵材。每套均附材质谱与详细设计说明。','本轮仅精修太虚无相。先看背部，再切换正面、侧面与四件；拖动可自由旋转。']
]){assert.equal(html.split(from).length,2,'presentation anchor changed: '+from);html=html.replace(from,to);}
fs.mkdirSync(out,{recursive:true});fs.writeFileSync(path.join(out,'taixu.html'),html);
fs.writeFileSync(path.join(out,'taixu-inline.html'),compactHTML(html));
console.log(JSON.stringify({design:design.id,out,inlineBytes:Buffer.byteLength(compactHTML(html))}));
