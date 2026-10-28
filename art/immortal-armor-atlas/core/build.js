#!/usr/bin/env node
/* 只组装设计册，不生成或修改作者的 levels。 */
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const zlib = require('node:zlib');
const crypto = require('node:crypto');
const core = __dirname, atlas = path.dirname(core), repo = path.resolve(atlas, '../..');
globalThis.THREE = require(path.join(repo, 'tools/web/three.min.js'));
const engine = require('./engine.js');
function compactHTML(html) {
  const packed=zlib.gzipSync(Buffer.from(html,'utf8'),{level:9}).toString('base64');
  return '<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>五境衣甲设计册</title><style>html,body{margin:0;background:transparent;color:var(--foreground,#e8e5df);font-family:sans-serif}</style><p id="unpacking">正在展开离线三维设计册…</p><script>(async()=>{try{if(!globalThis.DecompressionStream)throw new Error("当前浏览器不支持离线压缩包展开，请使用普通未压缩 HTML。");const bytes=Uint8Array.from(atob("'+packed+'"),c=>c.charCodeAt(0));const html=await new Response(new Blob([bytes]).stream().pipeThrough(new DecompressionStream("gzip"))).text();document.open();document.write(html);document.close();}catch(e){document.getElementById("unpacking").textContent=e.message;console.error(e);}})();</script></html>';
}
function assemble(catalogue, output, options={}) {
  if (!Array.isArray(catalogue) || !catalogue.length) throw new Error('catalogue 必须是非空设计数组');
  const ids = new Set();
  catalogue.forEach((spec, i) => {
    try { engine.validateDesign(spec); } catch (err) { throw new Error('catalogue[' + i + '] ' + err.message); }
    if (ids.has(spec.id)) throw new Error('catalogue[' + i + '].id 重复：' + spec.id); ids.add(spec.id);
  });
  const values = {
    '__THREE_JS__': fs.readFileSync(path.join(repo,'tools/web/three.min.js'),'utf8'),
    '__ENGINE_JS__': fs.readFileSync(path.join(core,'engine.js'),'utf8'),
    '__EFFECTS_JS__': fs.readFileSync(path.join(core,'effects.js'),'utf8'),
    '__EFFECT_CATALOGUE_JS__': fs.readFileSync(path.join(atlas,'effects/catalogue.js'),'utf8'),
    '__CATALOGUE_JSON__': JSON.stringify(catalogue).replace(/</g,'\\u003c').replace(/\u2028/g,'\\u2028').replace(/\u2029/g,'\\u2029')
  };
  const template = fs.readFileSync(path.join(core,'viewer.html'),'utf8');
  Object.keys(values).forEach(token => { if (template.split(token).length !== 2) throw new Error('模板占位符应恰好一次：' + token); });
  let html = template.replace(/__THREE_JS__|__ENGINE_JS__|__EFFECTS_JS__|__EFFECT_CATALOGUE_JS__|__CATALOGUE_JSON__/g, token => values[token]);
  if(options.thumbnails){
    const cache=options.thumbnails,hash=v=>crypto.createHash('sha256').update(v).digest('hex');
    if(cache.engineHash!==hash(fs.readFileSync(path.join(core,'engine.js')))||cache.viewerHash!==hash(fs.readFileSync(path.join(core,'viewer.html'))))throw new Error('缩略图与当前引擎/页面来源不符');
    for(const spec of catalogue){if(cache.designHashes?.[spec.id]!==hash(JSON.stringify(spec))||!cache.images?.[spec.id]?.startsWith('data:image/jpeg;base64,'))throw new Error('缩略图与模型不符：'+spec.id);}
    const encoded=JSON.stringify(cache.images).replace(/</g,'\\u003c');
    html=html.replace('<meta charset="utf-8">','<meta charset="utf-8"><script>globalThis.__ARMOR_THUMBNAILS__='+encoded+';</script>');
  }
  if(options.compact)html=compactHTML(html);
  if (output) { fs.mkdirSync(path.dirname(path.resolve(output)),{recursive:true}); fs.writeFileSync(output,html); }
  return html;
}
function main(argv) {
  let output=path.join(core,'test/atlas.html'), catalogueFile=null, fixture=false,compact=false,thumbnailsFile=null;
  for(let i=0;i<argv.length;i++) {
    if(argv[i]==='--output')output=argv[++i];
    else if(argv[i]==='--catalogue')catalogueFile=argv[++i];
    else if(argv[i]==='--fixture')fixture=true;
    else if(argv[i]==='--compact')compact=true;
    else if(argv[i]==='--thumbnails')thumbnailsFile=argv[++i];
    else if(argv[i]==='--help'){process.stdout.write('node core/build.js [--output FILE] [--catalogue ARRAY.json | --fixture] [--compact]\n默认读取 levels/level1.json .. level5.json，每境恰好三款。--compact 生成可供 html_preview 使用的离线 gzip HTML。\n');return;}
    else throw new Error('未知参数 '+argv[i]);
  }
  let catalogue;
  if(fixture) catalogue=[JSON.parse(fs.readFileSync(path.join(core,'fixture.json'),'utf8'))];
  else if(catalogueFile) catalogue=JSON.parse(fs.readFileSync(path.resolve(catalogueFile),'utf8'));
  else {
    catalogue=[];
    for(let level=1;level<=5;level++) {
      const file=path.join(atlas,'levels','level'+level+'.json');
      const designs=JSON.parse(fs.readFileSync(file,'utf8'));
      if(!Array.isArray(designs)||designs.length!==3)throw new Error(file+' 应恰好三款');
      designs.forEach(spec=>{if(spec.level!==level)throw new Error(file+' 的 level 必须是 '+level);});
      catalogue.push(...designs);
    }
  }
  assemble(catalogue,output,{compact,thumbnails:thumbnailsFile?JSON.parse(fs.readFileSync(thumbnailsFile,'utf8')):null});
  process.stdout.write(JSON.stringify({output:path.resolve(output),designs:catalogue.length,bytes:fs.statSync(output).size})+'\n');
}
module.exports={assemble,compactHTML};
if(require.main===module){try{main(process.argv.slice(2));}catch(err){process.stderr.write(err.stack+'\n');process.exitCode=1;}}
