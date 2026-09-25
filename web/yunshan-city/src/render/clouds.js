// Volumetric sea of clouds + mid-mountain mist bands + waterfall spray, ray-marched at half
// resolution against the scene depth. Output: rgb = in-scattered light, a = transmittance.
import * as THREE from 'three';
import { COMMON, ATMOSPHERE } from './glsl.js';

function makeNoise3D(N = 64) {
  // tileable 3D fbm value noise (R8)
  const rnd = (x, y, z, p) => {
    x = ((x % p) + p) % p;
    y = ((y % p) + p) % p;
    z = ((z % p) + p) % p;
    let h = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791) ^ (p * 2654435761);
    h = Math.imul(h ^ (h >>> 13), 0x5bd1e995);
    h ^= h >>> 15;
    return (h >>> 0) / 4294967296;
  };
  const vn = (x, y, z, period) => {
    const xi = Math.floor(x);
    const yi = Math.floor(y);
    const zi = Math.floor(z);
    const fx = x - xi;
    const fy = y - yi;
    const fz = z - zi;
    const sx = fx * fx * (3 - 2 * fx);
    const sy = fy * fy * (3 - 2 * fy);
    const sz = fz * fz * (3 - 2 * fz);
    let v = 0;
    for (let k = 0; k < 8; k++) {
      const dx = k & 1;
      const dy = (k >> 1) & 1;
      const dz = (k >> 2) & 1;
      const w = (dx ? sx : 1 - sx) * (dy ? sy : 1 - sy) * (dz ? sz : 1 - sz);
      v += w * rnd(xi + dx, yi + dy, zi + dz, period);
    }
    return v;
  };
  const data = new Uint8Array(N * N * N);
  for (let z = 0; z < N; z++)
    for (let y = 0; y < N; y++)
      for (let x = 0; x < N; x++) {
        let s = 0;
        let a = 0.5;
        let norm = 0;
        for (let o = 0; o < 4; o++) {
          const per = 4 << o;
          const f = per / N;
          s += a * vn(x * f, y * f, z * f, per);
          norm += a;
          a *= 0.5;
        }
        data[(z * N + y) * N + x] = Math.round((s / norm) * 255);
      }
  const tex = new THREE.Data3DTexture(data, N, N, N);
  tex.format = THREE.RedFormat;
  tex.type = THREE.UnsignedByteType;
  tex.minFilter = THREE.LinearFilter;
  tex.magFilter = THREE.LinearFilter;
  tex.wrapS = tex.wrapT = tex.wrapR = THREE.RepeatWrapping;
  tex.unpackAlignment = 1;
  tex.needsUpdate = true;
  return tex;
}

function makeNoise2D(N = 256) {
  // tileable fbm value noise, 4 channels at different base periods
  const lattice = (x, y, p, seed) => {
    x = ((x % p) + p) % p;
    y = ((y % p) + p) % p;
    let h = Math.imul(x, 374761393) ^ Math.imul(y, 668265263) ^ Math.imul(seed, 2246822519);
    h = Math.imul(h ^ (h >>> 13), 1274126177);
    h ^= h >>> 16;
    return (h >>> 0) / 4294967296;
  };
  const vn = (x, y, p, seed) => {
    const xi = Math.floor(x);
    const yi = Math.floor(y);
    const fx = x - xi;
    const fy = y - yi;
    const sx = fx * fx * (3 - 2 * fx);
    const sy = fy * fy * (3 - 2 * fy);
    const a = lattice(xi, yi, p, seed);
    const b = lattice(xi + 1, yi, p, seed);
    const c = lattice(xi, yi + 1, p, seed);
    const d = lattice(xi + 1, yi + 1, p, seed);
    return (a * (1 - sx) + b * sx) * (1 - sy) + (c * (1 - sx) + d * sx) * sy;
  };
  const data = new Uint8Array(N * N * 4);
  const bases = [4, 8, 16, 6];
  for (let ch = 0; ch < 4; ch++) {
    for (let y = 0; y < N; y++)
      for (let x = 0; x < N; x++) {
        let s = 0;
        let a = 0.5;
        let norm = 0;
        for (let o = 0; o < 4; o++) {
          const per = bases[ch] << o;
          const f = per / N;
          let v = vn(x * f, y * f, per, ch * 17 + o);
          if (ch === 3) v = 1 - Math.abs(v * 2 - 1);
          s += a * v;
          norm += a;
          a *= 0.5;
        }
        data[(y * N + x) * 4 + ch] = Math.round((s / norm) * 255);
      }
  }
  const tex = new THREE.DataTexture(data, N, N, THREE.RGBAFormat, THREE.UnsignedByteType);
  tex.wrapS = tex.wrapT = THREE.RepeatWrapping;
  tex.minFilter = THREE.LinearMipmapLinearFilter;
  tex.magFilter = THREE.LinearFilter;
  tex.generateMipmaps = true;
  tex.needsUpdate = true;
  return tex;
}

const VERT = /* glsl */ `
varying vec2 vUv;
void main() {
  vUv = position.xy * 0.5 + 0.5;
  gl_Position = vec4(position.xy, 0.0, 1.0);
}
`;

const FRAG = /* glsl */ `
precision highp sampler3D;
${COMMON}
${ATMOSPHERE}
uniform sampler2D tDepth;
uniform sampler3D tNoise;
uniform sampler2D tNoise2;
uniform mat4 uInvProj;
uniform mat4 uInvView;
uniform vec3 uCamPos;
uniform float uNear;
uniform float uFar;
uniform float uSeaTop;
uniform float uMist;      // mist band strength
uniform float uSteps;
uniform vec3 uFall;       // waterfall base
uniform vec2 uRes;
uniform vec2 uCity;       // city centre xz for night glow
uniform vec3 uLampCol;
varying vec2 vUv;

float n3(vec3 p) { return texture(tNoise, p).r; }
vec4 n2(vec2 p) { return texture2D(tNoise2, p); }

float seaTop(vec2 xz) {
  vec2 w = vec2(uTime * 1.2, uTime * 0.5);
  float a = n2((xz + w) / 2400.0).r;
  float b = n2((xz - w * 1.4) / 700.0).g;
  float c = n2((xz + w * 2.0) / 190.0).b;
  return uSeaTop + (a - 0.5) * 46.0 + (b - 0.5) * 30.0 + (c - 0.5) * 9.0;
}

// interior density of the sea of clouds
float seaDensity(vec3 p, out float topDist) {
  float top = seaTop(p.xz);
  topDist = top - p.y;
  if (topDist < -3.0) return 0.0;
  float det = n3(p * vec3(1.0 / 150.0, 1.0 / 90.0, 1.0 / 150.0) + vec3(uTime * 0.002, 0.0, uTime * 0.001));
  return smoothstep(-2.0, 8.0, topDist + (det - 0.5) * 6.0) * (0.035 + 0.03 * det);
}

// mist bands on the mountains (山腰云带) + waterfall spray
float mistDensity(vec3 p) {
  float d = 0.0;
  float band = exp(-pow((p.y - 215.0) / 30.0, 2.0)) + 0.7 * exp(-pow((p.y - 305.0) / 24.0, 2.0));
  if (band > 0.02 && uMist > 0.0) {
    float m = n2(p.xz / 1300.0 + vec2(uTime * 0.0006, -uTime * 0.0004)).g * 0.65 + n3(p / vec3(320.0, 110.0, 320.0) + vec3(uTime * 0.002, 0.0, 0.0)) * 0.35;
    d += smoothstep(0.5, 0.8, m) * band * 0.002 * uMist;
  }
  vec3 fp = p - uFall;
  float fr = length(fp * vec3(1.0, 1.3, 1.0));
  if (fr < 60.0) {
    float sp = n3(p / 30.0 + vec3(0.0, uTime * 0.03, 0.0));
    d += smoothstep(60.0, 5.0, fr) * 0.008 * (0.5 + 0.5 * sp);
  }
  return d;
}

bool slab(float y0, float y1, vec3 o, vec3 d, float tMax, out float ta, out float tb) {
  ta = 0.0;
  tb = tMax;
  if (abs(d.y) > 1e-5) {
    float a = (y0 - o.y) / d.y;
    float b = (y1 - o.y) / d.y;
    ta = max(ta, min(a, b));
    tb = min(tb, max(a, b));
  } else if (o.y < y0 || o.y > y1) return false;
  return tb > ta;
}

float hg(float c, float g) {
  float g2 = g * g;
  return (1.0 - g2) / (4.0 * 3.14159 * pow(1.0 + g2 - 2.0 * g * c, 1.5));
}

void main() {
  // reconstruct view ray
  vec4 ndc = vec4(vUv * 2.0 - 1.0, 1.0, 1.0);
  vec4 vp = uInvProj * ndc;
  vp /= vp.w;
  vec3 dirV = normalize(vp.xyz);
  vec3 dir = normalize((uInvView * vec4(dirV, 0.0)).xyz);
  float depth = texture2D(tDepth, vUv).r;
  float zn = depth * 2.0 - 1.0;
  float lin = 2.0 * uNear * uFar / (uFar + uNear - zn * (uFar - uNear));
  float sceneDist = depth >= 1.0 ? 1e6 : lin / max(-dirV.z, 1e-4);

  float tMaxRay = min(sceneDist, 12000.0);
  float cosT = dot(dir, uSunDir);
  float phase = mix(hg(cosT, 0.55), hg(cosT, -0.2), 0.35) * 4.0 * 3.14159;
  float moonPhase = hg(dot(dir, uMoonDir), 0.4) * 4.0 * 3.14159;
  float sunUp = smoothstep(-0.04, 0.06, uSunDir.y);
  float moonUp = smoothstep(-0.02, 0.1, uMoonDir.y) * (1.0 - sunUp);
  float jitter = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
  float T = 1.0;
  vec3 S = vec3(0.0);
  float tFirst = 1e9;

  // ---- mist bands & spray (thin, few samples) ----
  float ma, mb;
  if (uMist > 0.0 && slab(100.0, 345.0, uCamPos, dir, tMaxRay, ma, mb)) {
    float len = min(mb - ma, 1600.0);
    const float NM = 18.0;
    for (float i = 0.0; i < NM; i += 1.0) {
      float t = ma + len * (i + jitter) / NM;
      vec3 p = uCamPos + dir * t;
      float d = mistDensity(p);
      if (d > 1e-6) {
        tFirst = min(tFirst, t);
        vec3 amb = uAmbSky * 0.8;
        vec3 L = uSunCol * phase * sunUp * 0.8 + uMoonCol * moonPhase * moonUp * 1.4 + amb;
        float cityD = length(p.xz - uCity);
        L += uLampCol * uLamp * 0.3 * exp(-cityD / 260.0);
        float tr = exp(-d * len / NM);
        S += T * L * (1.0 - tr);
        T *= tr;
      }
    }
  }

  // ---- sea of clouds: find the top surface, then integrate inside ----
  float seaHi = uSeaTop + 34.0;
  float sa, sb;
  if (slab(-150.0, seaHi, uCamPos, dir, tMaxRay, sa, sb) && T > 0.02) {
    float tE = -1.0;
    vec3 p0 = uCamPos + dir * sa;
    if (seaTop(p0.xz) - p0.y > 0.0) tE = sa;
    else {
      float span = min(sb - sa, 6000.0);
      float dtS = span / 28.0;
      float t = sa;
      for (int i = 0; i < 28; i++) {
        float tn = t + dtS;
        vec3 q = uCamPos + dir * tn;
        if (seaTop(q.xz) - q.y > -1.0) {
          // bisection refine
          float lo = t;
          float hi = tn;
          for (int k = 0; k < 5; k++) {
            float mid = 0.5 * (lo + hi);
            vec3 qm = uCamPos + dir * mid;
            if (seaTop(qm.xz) - qm.y > -1.0) hi = mid;
            else lo = mid;
          }
          tE = lo;
          break;
        }
        t = tn;
      }
    }
    if (tE >= 0.0 && tE < tMaxRay) {
      tFirst = min(tFirst, tE);
      float t = tE - 2.0;
      float stepL = 1.6;
      for (int i = 0; i < 64; i++) {
        if (float(i) >= uSteps) break;
        float dt = stepL * (0.75 + 0.5 * jitter);
        t += dt;
        if (t > tMaxRay) break;
        vec3 p = uCamPos + dir * t;
        float topDist;
        float d = seaDensity(p, topDist);
        if (d > 1e-5) {
          float below = max(topDist, 0.0);
          vec3 q1 = p + uSunDir * 14.0;
          vec3 q2 = p + uSunDir * 40.0;
          float o1 = max(seaTop(q1.xz) - q1.y, 0.0);
          float o2 = max(seaTop(q2.xz) - q2.y, 0.0);
          float tauSun = (o1 * 0.8 + o2 * 0.5 + below * 0.25) * 0.06 + d * 12.0;
          float tauMoon = below * 0.05 / max(uMoonDir.y, 0.08) + d * 12.0;
          float powder = 1.0 - exp(-d * 60.0);
          vec3 amb = mix(uAmbGround * 1.2, uAmbSky, clamp(0.6 - below * 0.02, 0.15, 1.0)) * 0.6;
          vec3 L = uSunCol * exp(-tauSun) * phase * sunUp * mix(0.55, 1.0, powder) + uMoonCol * exp(-tauMoon) * moonPhase * moonUp * 1.6 + amb;
          float cityD = length(p.xz - uCity);
          L += uLampCol * uLamp * 0.18 * exp(-cityD / 300.0);
          float tr = exp(-d * dt);
          S += T * L * (1.0 - tr);
          T *= tr;
          if (T < 0.01) break;
        }
        stepL *= 1.16;
      }
    }
  }
  if (tFirst > 1e8) {
    gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
    return;
  }
  float len = 0.0;
  float t0 = tFirst;
  // aerial perspective on the clouds themselves
  float tMid = t0 + len * 0.3 + 20.0;
  vec3 fogged = applyFog(S, uCamPos + dir * tMid, uCamPos) - applyFog(vec3(0.0), uCamPos + dir * tMid, uCamPos) * T;
  gl_FragColor = vec4(max(fogged, 0.0), T);
}
`;

export class CloudPass {
  constructor(renderer, uniforms, { steps = 40, scale = 0.5 } = {}) {
    this.renderer = renderer;
    this.scale = scale;
    this.noise = makeNoise3D(64);
    this.uniforms = {
      ...uniforms,
      tDepth: { value: null },
      tNoise: { value: this.noise },
      tNoise2: { value: makeNoise2D(256) },
      uInvProj: { value: new THREE.Matrix4() },
      uInvView: { value: new THREE.Matrix4() },
      uCamPos: { value: new THREE.Vector3() },
      uNear: { value: 0.3 },
      uFar: { value: 16000 },
      uSeaTop: { value: 4 },
      uMist: { value: 1 },
      uSteps: { value: steps },
      uFall: { value: new THREE.Vector3(-8, 150, -392) },
      uRes: { value: new THREE.Vector2() },
      uCity: { value: new THREE.Vector2(-10, 60) },
    };
    this.mat = new THREE.ShaderMaterial({ uniforms: this.uniforms, vertexShader: VERT, fragmentShader: FRAG, depthTest: false, depthWrite: false });
    const tri = new THREE.BufferGeometry();
    tri.setAttribute('position', new THREE.Float32BufferAttribute([-1, -1, 0, 3, -1, 0, -1, 3, 0], 3));
    this.quad = new THREE.Mesh(tri, this.mat);
    this.quad.frustumCulled = false;
    this.scene = new THREE.Scene();
    this.scene.add(this.quad);
    this.cam = new THREE.OrthographicCamera(-1, 1, 1, -1, 0, 1);
    this.rt = null;
  }

  setSize(w, h) {
    const W = Math.max(1, Math.floor(w * this.scale));
    const H = Math.max(1, Math.floor(h * this.scale));
    if (this.rt) this.rt.dispose();
    this.rt = new THREE.WebGLRenderTarget(W, H, { type: THREE.HalfFloatType, depthBuffer: false, minFilter: THREE.LinearFilter, magFilter: THREE.LinearFilter });
    this.uniforms.uRes.value.set(W, H);
  }

  render(camera, depthTex) {
    const u = this.uniforms;
    u.tDepth.value = depthTex;
    u.uInvProj.value.copy(camera.projectionMatrixInverse);
    u.uInvView.value.copy(camera.matrixWorld);
    u.uCamPos.value.copy(camera.position);
    u.uNear.value = camera.near;
    u.uFar.value = camera.far;
    this.renderer.setRenderTarget(this.rt);
    this.renderer.render(this.scene, this.cam);
    return this.rt.texture;
  }
}
