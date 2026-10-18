"""Generator-owned material textures and bounded vanilla JSON elements."""
import hashlib
import struct
import zlib

def model_elements(boxes):
    result=[]
    for b in boxes:
        x0,y0,z0=b['from'];x1,y1,z1=b['to']
        faces={
            'down':([x0,16-z1,x1,16-z0],y0==0),
            'up':([x0,z0,x1,z1],y1==16),
            'north':([16-x1,16-y1,16-x0,16-y0],z0==0),
            'south':([x0,16-y1,x1,16-y0],z1==16),
            'west':([z0,16-y1,z1,16-y0],x0==0),
            'east':([16-z1,16-y1,16-z0,16-y0],x1==16),
        }
        result.append({'from':b['from'],'to':b['to'],'faces':{side:{'uv':uv,'texture':'#'+b['material'],**({'cullface':side} if boundary else {})} for side,(uv,boundary) in faces.items()}})
    return result

def png_texture(material,spec,config):
    """Generator-owned pixel materials, no external or AI image dependency."""
    size=config['texture_size'];noise=config['texture_noise']
    rgb=tuple(int(spec['color'][i:i+2],16) for i in [1,3,5]);pixels=bytearray()
    for y in range(size):
        pixels.append(0)
        for x in range(size):
            seed=int.from_bytes(hashlib.sha256(f'{material}:{x}:{y}'.encode()).digest()[:4],'big')
            d=seed%(2*noise+1)-noise
            if material in ('timber','deck'):
                d+=(x%4==0)*-8+(y%8==0 and (x+y)%4==0)*-5
            elif material=='stone':
                if y in (0,8) or (x+(8 if y>=8 else 0))%16==0:d-=19
            elif material=='tile':
                d+=(x%4==0)*9+(y%8==0)*-8
            elif material=='plaster':
                d=round(d*.5)
            elif material=='lamp':
                d=round(d*.3)
            pixels.extend(max(0,min(255,c+d)) for c in rgb)
            pixels.append(255)
    def chunk(kind,data):return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',size,size,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(bytes(pixels),9))+chunk(b'IEND',b'')
