#!/usr/bin/env node
'use strict';
// Assemble a focused view from the current owning authors; never edits models.
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const {assemble,compactHTML}=require('./core/build.js');
const [id,directory,thumbnailFile]=process.argv.slice(2);assert(/^[a-z0-9_]+$/.test(id),'valid design ID required');
const designs=Array.from({length:5},(_,i)=>JSON.parse(fs.readFileSync(path.join(__dirname,'levels','level'+(i+1)+'.json')))).flat();
const design=designs.find(d=>d.id===id);assert(design,'unknown design');
let html=assemble([design],null,{thumbnails:thumbnailFile?JSON.parse(fs.readFileSync(thumbnailFile)):null});
for(const [from,to]of [
 ['15 套 · 60 件 · 15 种锭材','1 套 · 四件衣甲 · 配套灵材锭'],
 ['<h2>五境十五式</h2>','<h2>'+design.name+' · 本轮精修</h2>'],
 ['选择境界与样式，旋转查看完整衣甲和配套灵材。每套均附材质谱与详细设计说明。','本轮聚焦'+design.name+'。可切换正背侧面、四件与静态／动态表现。']
]){assert.equal(html.split(from).length,2,'viewer presentation anchor changed');html=html.replace(from,to);}
const out=path.resolve(directory);fs.mkdirSync(out,{recursive:true});fs.writeFileSync(path.join(out,id+'.html'),html);fs.writeFileSync(path.join(out,id+'-inline.html'),compactHTML(html));console.log(JSON.stringify({id,out,inlineBytes:Buffer.byteLength(compactHTML(html))}));
