// Compile the approved model's positions and pixel paint without a browser.
const fs = require('node:fs/promises');
const path = require('node:path');
const { makeBoss } = require('../../../art/entities/lotus_guard/author.cjs');

/* The author uses only image data and one-pixel shading marks on its texture tiles. */
class PixelCanvas {
  getContext(type) {
    if (type !== '2d') throw new Error('lotus guard requires a pixel canvas');
    return {
      createImageData: (width, height) => ({width, height, data: new Uint8ClampedArray(4 * width * height)}),
      putImageData: image => {this.pixels = image.data;},
      fillRect: (x, y, width, height) => {
        const [r,g,b,alpha] = this.context.fillStyle.slice(5, -1).split(',').map(Number);
        for (let py = Math.max(0, y); py < Math.min(y+height, this.height); py++) {
          for (let px = Math.max(0, x); px < Math.min(x+width, this.width); px++) {
            const i = (py*this.width+px)*4;
            for (const [ch, value] of [r,g,b].entries()) {
              this.pixels[i+ch] = this.pixels[i+ch]*(1-alpha) + value*alpha;
            }
          }
        }
      },
    };
  }
}

async function extract() {
  const library = await fs.readFile(path.resolve(__dirname, '../../../web/yunshan-city/vendor/three.module.min.js'));
  const THREE = await import('data:text/javascript;base64,' + library.toString('base64'));
  globalThis.document = {createElement(tag) {
    if (tag !== 'canvas') throw Error('unexpected model resource ' + tag);
    const canvas = new PixelCanvas();
    canvas.context = canvas.getContext('2d');
    canvas.getContext = () => canvas.context;
    return canvas;
  }};
  const {group, mats} = makeBoss(THREE);
  group.updateMatrixWorld(true);
  const id = new Map(Object.entries(mats).map(([name, material]) => [material, name]));
  const bones = [];
  const faces = [];
  function owner(node) {
    if (!node) return 'root';
    return node.userData.pivot ? node.name : owner(node.parent);
  }
  group.traverse(node => {
    if (node.userData.pivot) {
      bones.push([node.name, owner(node.parent), node.userData.pivot]);
    }
    if (!node.isMesh) return;
    const bone = owner(node.parent);
    const {geometry} = node;
    const pos = geometry.getAttribute('position');
    const uv = geometry.getAttribute('uv');
    const index = geometry.getIndex();
    for (const {start, count, materialIndex} of geometry.groups.length ?
      geometry.groups : [{start:0,count:index?.count ?? pos.count,materialIndex:0}]) {
      const paint = id.get(Array.isArray(node.material) ? node.material[materialIndex] : node.material);
      for (let i = start; i < start + count; i += 3) {
        const face = [];
        const points = [];
        for (let v = 0; v < 3; v++) {
          const n = index ? index.getX(i + v) : i + v;
          const p = new THREE.Vector3().fromBufferAttribute(pos, n).applyMatrix4(node.matrixWorld);
          points.push(p);
          face.push(p.x, p.y, p.z, uv.getX(n), uv.getY(n));
        }
        if (points[1].sub(points[0]).cross(points[2].sub(points[0])).lengthSq() > 1e-12) {
          faces.push([bone, paint, face]);
        }
      }
    }
  });
  const paint = Object.fromEntries(Object.entries(mats).filter(([,material]) => material.map)
    .map(([name, material]) => [name, Buffer.from(material.map.image.pixels).toString('base64')]));
  process.stdout.write(JSON.stringify({bones, faces, paint}));
}

extract().catch(error => {console.error(error); process.exitCode = 1;});
