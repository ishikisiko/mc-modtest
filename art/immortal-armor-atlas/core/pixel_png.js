'use strict';
// Encode authored pixel rows without browser, canvas or network dependencies.
const zlib=require('node:zlib');
function crc32(bytes){let crc=0xffffffff;for(const b of bytes){crc^=b;for(let k=0;k<8;k++)crc=(crc>>>1)^((crc&1)?0xedb88320:0);}return (crc^0xffffffff)>>>0;}
function chunk(type,data){const tag=Buffer.from(type),n=Buffer.alloc(4),crc=Buffer.alloc(4);n.writeUInt32BE(data.length);crc.writeUInt32BE(crc32(Buffer.concat([tag,data])));return Buffer.concat([n,tag,data,crc]);}
function encodePixelPNG(art){
 const w=art.rows.length,h=w,rows=[];
 for(const row of art.rows){const bytes=[0];for(const c of row){const hex=art.palette[Number(c)];bytes.push(...[1,3,5].map(i=>parseInt(hex.slice(i,i+2),16)),255);}rows.push(Buffer.from(bytes));}
 const ihdr=Buffer.alloc(13);ihdr.writeUInt32BE(w,0);ihdr.writeUInt32BE(h,4);ihdr[8]=8;ihdr[9]=6;
 return Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',ihdr),chunk('IDAT',zlib.deflateSync(Buffer.concat(rows))),chunk('IEND',Buffer.alloc(0))]);
}
module.exports={encodePixelPNG};
