// HDR pipeline: scene -> half-float target (+depth) -> bloom mip chain -> tonemapped composite.
import * as THREE from 'three';

const FS_VERT = /* glsl */ `
varying vec2 vUv;
void main() {
  vUv = position.xy * 0.5 + 0.5;
  gl_Position = vec4(position.xy, 0.0, 1.0);
}
`;

function fsMaterial(frag, uniforms) {
  return new THREE.ShaderMaterial({ vertexShader: FS_VERT, fragmentShader: frag, uniforms, depthTest: false, depthWrite: false });
}

const BRIGHT = /* glsl */ `
uniform sampler2D tSrc;
uniform float uThreshold;
uniform vec2 uTexel;
varying vec2 vUv;
void main() {
  vec3 c = vec3(0.0);
  c += texture2D(tSrc, vUv + uTexel * vec2(-1.0, -1.0)).rgb;
  c += texture2D(tSrc, vUv + uTexel * vec2(1.0, -1.0)).rgb;
  c += texture2D(tSrc, vUv + uTexel * vec2(-1.0, 1.0)).rgb;
  c += texture2D(tSrc, vUv + uTexel * vec2(1.0, 1.0)).rgb;
  c *= 0.25;
  float l = max(max(c.r, c.g), c.b);
  float k = max(l - uThreshold, 0.0);
  float soft = clamp(l - uThreshold + 0.5, 0.0, 1.0);
  soft = soft * soft * 0.5;
  float w = max(k, soft) / max(l, 1e-4);
  gl_FragColor = vec4(min(c * w, vec3(60.0)), 1.0);
}
`;

const DOWN = /* glsl */ `
uniform sampler2D tSrc;
uniform vec2 uTexel;
varying vec2 vUv;
void main() {
  vec3 c = texture2D(tSrc, vUv).rgb * 0.5;
  c += texture2D(tSrc, vUv + uTexel * vec2(-1.0, -1.0)).rgb * 0.125;
  c += texture2D(tSrc, vUv + uTexel * vec2(1.0, -1.0)).rgb * 0.125;
  c += texture2D(tSrc, vUv + uTexel * vec2(-1.0, 1.0)).rgb * 0.125;
  c += texture2D(tSrc, vUv + uTexel * vec2(1.0, 1.0)).rgb * 0.125;
  gl_FragColor = vec4(c, 1.0);
}
`;

const UP = /* glsl */ `
uniform sampler2D tSrc;
uniform sampler2D tBase;
uniform vec2 uTexel;
uniform float uWeight;
varying vec2 vUv;
void main() {
  vec3 c = vec3(0.0);
  c += texture2D(tSrc, vUv + uTexel * vec2(-1.0, 0.0)).rgb * 2.0;
  c += texture2D(tSrc, vUv + uTexel * vec2(1.0, 0.0)).rgb * 2.0;
  c += texture2D(tSrc, vUv + uTexel * vec2(0.0, 1.0)).rgb * 2.0;
  c += texture2D(tSrc, vUv + uTexel * vec2(0.0, -1.0)).rgb * 2.0;
  c += texture2D(tSrc, vUv + uTexel * vec2(-1.0, -1.0)).rgb;
  c += texture2D(tSrc, vUv + uTexel * vec2(1.0, -1.0)).rgb;
  c += texture2D(tSrc, vUv + uTexel * vec2(-1.0, 1.0)).rgb;
  c += texture2D(tSrc, vUv + uTexel * vec2(1.0, 1.0)).rgb;
  c += texture2D(tSrc, vUv).rgb * 4.0;
  c /= 16.0;
  gl_FragColor = vec4(texture2D(tBase, vUv).rgb + c * uWeight, 1.0);
}
`;

const COMPOSITE = /* glsl */ `
uniform sampler2D tScene;
uniform sampler2D tBloom;
uniform sampler2D tClouds;
uniform float uCloudsOn;
uniform float uExposure;
uniform float uBloom;
uniform float uNight;
uniform float uTime;
uniform vec2 uRes;
varying vec2 vUv;
vec3 aces(vec3 x) {
  const float a = 2.51;
  const float b = 0.03;
  const float c = 2.43;
  const float d = 0.59;
  const float e = 0.14;
  return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}
float h12(vec2 p) {
  vec3 p3 = fract(vec3(p.xyx) * 0.1031);
  p3 += dot(p3, p3.yzx + 33.33);
  return fract((p3.x + p3.y) * p3.z);
}
void main() {
  vec3 c = texture2D(tScene, vUv).rgb;
  if (uCloudsOn > 0.5) {
    vec2 ct = 1.0 / vec2(textureSize(tClouds, 0));
    vec4 cl = texture2D(tClouds, vUv) * 0.4;
    cl += texture2D(tClouds, vUv + vec2(ct.x, 0.0)) * 0.15;
    cl += texture2D(tClouds, vUv - vec2(ct.x, 0.0)) * 0.15;
    cl += texture2D(tClouds, vUv + vec2(0.0, ct.y)) * 0.15;
    cl += texture2D(tClouds, vUv - vec2(0.0, ct.y)) * 0.15;
    c = c * cl.a + cl.rgb;
  }
  c += texture2D(tBloom, vUv).rgb * uBloom;
  c *= uExposure;
  // gentle ink-wash grade: lift shadows toward blue-grey, keep warm highlights
  float lum = dot(c, vec3(0.2126, 0.7152, 0.0722));
  c = mix(vec3(lum), c, 0.92 - uNight * 0.12);
  c = aces(c);
  c = pow(c, vec3(1.0 / 2.2));
  // vignette
  vec2 q = vUv - 0.5;
  c *= 1.0 - dot(q, q) * 0.38;
  // dither
  c += (h12(gl_FragCoord.xy + fract(uTime) * 91.0) - 0.5) / 255.0;
  gl_FragColor = vec4(c, 1.0);
}
`;

export class PostFX {
  constructor(renderer, { bloom = true } = {}) {
    this.renderer = renderer;
    this.bloomOn = bloom;
    this.scene = new THREE.Scene();
    this.cam = new THREE.OrthographicCamera(-1, 1, 1, -1, 0, 1);
    const tri = new THREE.BufferGeometry();
    tri.setAttribute('position', new THREE.Float32BufferAttribute([-1, -1, 0, 3, -1, 0, -1, 3, 0], 3));
    this.quad = new THREE.Mesh(tri, null);
    this.quad.frustumCulled = false;
    this.scene.add(this.quad);
    this.brightMat = fsMaterial(BRIGHT, { tSrc: { value: null }, uThreshold: { value: 1.4 }, uTexel: { value: new THREE.Vector2() } });
    this.downMat = fsMaterial(DOWN, { tSrc: { value: null }, uTexel: { value: new THREE.Vector2() } });
    this.upMat = fsMaterial(UP, { tSrc: { value: null }, tBase: { value: null }, uTexel: { value: new THREE.Vector2() }, uWeight: { value: 1 } });
    this.compMat = fsMaterial(COMPOSITE, {
      tScene: { value: null },
      tBloom: { value: null },
      tClouds: { value: null },
      uCloudsOn: { value: 0 },
      uExposure: { value: 1 },
      uBloom: { value: 0.08 },
      uNight: { value: 0 },
      uTime: { value: 0 },
      uRes: { value: new THREE.Vector2() },
    });
    this.black = new THREE.DataTexture(new Uint8Array([0, 0, 0, 255]), 1, 1);
    this.black.needsUpdate = true;
    this.sceneRT = null;
    this.mips = [];
    this.ups = [];
  }

  setSize(w, h) {
    this.w = w;
    this.h = h;
    if (this.sceneRT) this.sceneRT.dispose();
    const depth = new THREE.DepthTexture(w, h);
    depth.type = THREE.FloatType;
    this.sceneRT = new THREE.WebGLRenderTarget(w, h, {
      type: THREE.HalfFloatType,
      depthTexture: depth,
      depthBuffer: true,
      samples: 0,
    });
    for (const m of this.mips) m.dispose();
    for (const m of this.ups) m.dispose();
    this.mips = [];
    this.ups = [];
    let mw = Math.max(1, w >> 1);
    let mh = Math.max(1, h >> 1);
    for (let i = 0; i < 6; i++) {
      const opts = { type: THREE.HalfFloatType, depthBuffer: false, minFilter: THREE.LinearFilter, magFilter: THREE.LinearFilter };
      this.mips.push(new THREE.WebGLRenderTarget(mw, mh, opts));
      this.ups.push(new THREE.WebGLRenderTarget(mw, mh, opts));
      mw = Math.max(1, mw >> 1);
      mh = Math.max(1, mh >> 1);
    }
    this.compMat.uniforms.uRes.value.set(w, h);
  }

  _pass(mat, target) {
    this.quad.material = mat;
    this.renderer.setRenderTarget(target);
    this.renderer.render(this.scene, this.cam);
  }

  render({ exposure, night, time, bloom, clouds }) {
    const r = this.renderer;
    let bloomTex = this.black;
    if (this.bloomOn) {
      const bm = this.brightMat.uniforms;
      bm.tSrc.value = this.sceneRT.texture;
      bm.uTexel.value.set(1 / this.w, 1 / this.h);
      bm.uThreshold.value = 1.1 / exposure;
      this._pass(this.brightMat, this.mips[0]);
      for (let i = 1; i < this.mips.length; i++) {
        const dm = this.downMat.uniforms;
        dm.tSrc.value = this.mips[i - 1].texture;
        dm.uTexel.value.set(1 / this.mips[i - 1].width, 1 / this.mips[i - 1].height);
        this._pass(this.downMat, this.mips[i]);
      }
      // upsample
      let src = this.mips[this.mips.length - 1];
      for (let i = this.mips.length - 2; i >= 0; i--) {
        const um = this.upMat.uniforms;
        um.tSrc.value = src.texture;
        um.tBase.value = this.mips[i].texture;
        um.uTexel.value.set(1 / src.width, 1 / src.height);
        um.uWeight.value = 1.0;
        this._pass(this.upMat, this.ups[i]);
        src = this.ups[i];
      }
      bloomTex = this.ups[0].texture;
    }
    const cm = this.compMat.uniforms;
    cm.tScene.value = this.sceneRT.texture;
    cm.tBloom.value = bloomTex;
    cm.tClouds.value = clouds || this.black;
    cm.uCloudsOn.value = clouds ? 1 : 0;
    cm.uExposure.value = exposure;
    cm.uBloom.value = bloom;
    cm.uNight.value = night;
    cm.uTime.value = time;
    this._pass(this.compMat, null);
    r.setRenderTarget(null);
  }
}
