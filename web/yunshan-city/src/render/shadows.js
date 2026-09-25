// Cascaded shadow maps for the key light (sun by day, moon by night).
import * as THREE from 'three';

export const SHADOW_PARS = /* glsl */ `
uniform sampler2DShadow uShadow0;
uniform sampler2DShadow uShadow1;
uniform sampler2DShadow uShadow2;
uniform mat4 uShadowMat0;
uniform mat4 uShadowMat1;
uniform mat4 uShadowMat2;
uniform vec4 uShadowTexel; // world size of one texel per cascade (xyz), w = enabled
uniform vec3 uShadowLight;  // direction toward the key light
uniform vec3 uShadowFar;    // far distance of each cascade

float sampleCascade(sampler2DShadow sm, mat4 mat, vec3 wp, float texel) {
  vec4 sp = mat * vec4(wp, 1.0);
  vec3 p = sp.xyz / sp.w * 0.5 + 0.5;
  if (p.x <= 0.001 || p.y <= 0.001 || p.x >= 0.999 || p.y >= 0.999 || p.z >= 1.0) return -1.0;
  float ts = 1.0 / float(textureSize(sm, 0).x);
  float s = 0.0;
  s += texture(sm, vec3(p.xy + vec2(-0.5, -0.5) * ts, p.z));
  s += texture(sm, vec3(p.xy + vec2(0.5, -0.5) * ts, p.z));
  s += texture(sm, vec3(p.xy + vec2(-0.5, 0.5) * ts, p.z));
  s += texture(sm, vec3(p.xy + vec2(0.5, 0.5) * ts, p.z));
  return s * 0.25;
}

float getShadow(vec3 wp, vec3 N) {
  if (uShadowTexel.w < 0.5) return 1.0;
  float d = length(wp - cameraPosition);
  float ndl = dot(N, uShadowLight);
  if (ndl <= 0.0) return 0.0;
  float slopeK = clamp(sqrt(1.0 - ndl * ndl) / max(ndl, 0.15), 0.0, 4.0);
  if (d < uShadowFar.x) {
    vec3 p = wp + N * uShadowTexel.x * (1.2 + slopeK) + uShadowLight * uShadowTexel.x * 0.6;
    float s = sampleCascade(uShadow0, uShadowMat0, p, uShadowTexel.x);
    if (s >= 0.0) {
      // blend into the next cascade near the edge
      float edge = smoothstep(uShadowFar.x * 0.85, uShadowFar.x, d);
      if (edge > 0.0) {
        vec3 p1 = wp + N * uShadowTexel.y * (1.2 + slopeK) + uShadowLight * uShadowTexel.y * 0.6;
        float s1 = sampleCascade(uShadow1, uShadowMat1, p1, uShadowTexel.y);
        if (s1 >= 0.0) s = mix(s, s1, edge);
      }
      return s;
    }
  }
  if (d < uShadowFar.y) {
    vec3 p = wp + N * uShadowTexel.y * (1.2 + slopeK) + uShadowLight * uShadowTexel.y * 0.6;
    float s = sampleCascade(uShadow1, uShadowMat1, p, uShadowTexel.y);
    if (s >= 0.0) {
      float edge = smoothstep(uShadowFar.y * 0.85, uShadowFar.y, d);
      if (edge > 0.0) {
        vec3 p2 = wp + N * uShadowTexel.z * (1.2 + slopeK) + uShadowLight * uShadowTexel.z * 0.6;
        float s2 = sampleCascade(uShadow2, uShadowMat2, p2, uShadowTexel.z);
        if (s2 >= 0.0) s = mix(s, s2, edge);
      }
      return s;
    }
  }
  if (d < uShadowFar.z) {
    vec3 p = wp + N * uShadowTexel.z * (1.2 + slopeK) + uShadowLight * uShadowTexel.z * 0.6;
    float s = sampleCascade(uShadow2, uShadowMat2, p, uShadowTexel.z);
    if (s >= 0.0) return mix(s, 1.0, smoothstep(uShadowFar.z * 0.8, uShadowFar.z, d));
  }
  return 1.0;
}
`;

export class CascadedShadows {
  constructor(renderer, { size = 2048, splits = [36, 170, 900], enabled = true } = {}) {
    this.renderer = renderer;
    this.size = size;
    this.splits = splits;
    this.enabled = enabled;
    this.cams = [];
    this.targets = [];
    this.mats = [new THREE.Matrix4(), new THREE.Matrix4(), new THREE.Matrix4()];
    this.texel = new THREE.Vector4(1, 1, 1, enabled ? 1 : 0);
    this.frame = 0;
    for (let i = 0; i < 3; i++) {
      const dt = new THREE.DepthTexture(size, size);
      dt.type = THREE.UnsignedIntType;
      dt.format = THREE.DepthFormat;
      dt.compareFunction = THREE.LessEqualCompare;
      dt.minFilter = THREE.LinearFilter;
      dt.magFilter = THREE.LinearFilter;
      const rt = new THREE.WebGLRenderTarget(size, size, { depthTexture: dt, depthBuffer: true });
      rt.texture.minFilter = THREE.NearestFilter;
      rt.texture.magFilter = THREE.NearestFilter;
      rt.texture.generateMipmaps = false;
      this.targets.push(rt);
      const cam = new THREE.OrthographicCamera(-1, 1, 1, -1, 0.1, 10);
      cam.layers.set(1); // only shadow casters live on layer 1
      this.cams.push(cam);
    }
    this.uniforms = {
      uShadow0: { value: this.targets[0].depthTexture },
      uShadow1: { value: this.targets[1].depthTexture },
      uShadow2: { value: this.targets[2].depthTexture },
      uShadowMat0: { value: this.mats[0] },
      uShadowMat1: { value: this.mats[1] },
      uShadowMat2: { value: this.mats[2] },
      uShadowTexel: { value: this.texel },
      uShadowLight: { value: new THREE.Vector3(0, 1, 0) },
      uShadowFar: { value: new THREE.Vector3(...splits) },
    };
    this._tmp = new THREE.Vector3();
    this._center = new THREE.Vector3();
    // allocate & clear the depth textures so samplers are valid even with shadows off
    const prev = renderer.getRenderTarget();
    for (const rt of this.targets) {
      renderer.setRenderTarget(rt);
      renderer.clear(true, true, false);
    }
    renderer.setRenderTarget(prev);
  }

  setEnabled(on) {
    this.enabled = on;
    this.texel.w = on ? 1 : 0;
  }

  // compute cascade camera for frustum slice [near, far] of the main camera
  _fit(i, camera, near, far, lightDir) {
    const cam = this.cams[i];
    // bounding sphere of the frustum slice (camera-space symmetric approximation)
    const fov = THREE.MathUtils.degToRad(camera.fov);
    const tanV = Math.tan(fov / 2);
    const tanH = tanV * camera.aspect;
    const k = Math.sqrt(1 + tanV * tanV + tanH * tanH);
    let cz;
    let radius;
    const kk = tanV * tanV + tanH * tanH;
    // optimal sphere centre along the view axis
    cz = 0.5 * (near + far) * (1 + kk);
    if (cz > far) {
      cz = far;
      radius = far * Math.sqrt(kk);
    } else {
      radius = Math.sqrt((far - cz) * (far - cz) + far * far * kk);
    }
    void k;
    const dir = camera.getWorldDirection(this._tmp);
    this._center.copy(camera.position).addScaledVector(dir, cz);
    radius = Math.ceil(radius * 1.02);
    // light basis
    const L = lightDir;
    const up = Math.abs(L.y) > 0.99 ? new THREE.Vector3(1, 0, 0) : new THREE.Vector3(0, 1, 0);
    const right = new THREE.Vector3().crossVectors(up, L).normalize();
    const up2 = new THREE.Vector3().crossVectors(L, right).normalize();
    // snap centre to texel grid in light space
    const texel = (radius * 2) / this.size;
    let cx = this._center.dot(right);
    let cy = this._center.dot(up2);
    const cd = this._center.dot(L);
    cx = Math.round(cx / texel) * texel;
    cy = Math.round(cy / texel) * texel;
    const back = 2200; // how far toward the light casters may sit
    const c = new THREE.Vector3().addScaledVector(right, cx).addScaledVector(up2, cy).addScaledVector(L, cd);
    cam.position.copy(c).addScaledVector(L, back);
    cam.up.copy(up2);
    cam.lookAt(c);
    cam.left = -radius;
    cam.right = radius;
    cam.top = radius;
    cam.bottom = -radius;
    cam.near = 1;
    cam.far = back + radius * 2 + 400;
    cam.updateProjectionMatrix();
    cam.updateMatrixWorld(true);
    return texel;
  }

  // render cascades. casters: array of meshes with userData.depthMaterial
  update(scene, camera, lightDir, casters) {
    if (!this.enabled) return;
    const r = this.renderer;
    this.frame++;
    this.uniforms.uShadowLight.value.copy(lightDir);
    const saved = [];
    for (const m of casters) {
      if (!m.visible || !m.userData.depthMaterial) continue;
      saved.push([m, m.material]);
      m.material = m.userData.depthMaterial;
    }
    const prevRT = r.getRenderTarget();
    const prevAuto = r.autoClear;
    const prevOverride = scene.overrideMaterial;
    const prevBg = scene.background;
    scene.background = null;
    r.autoClear = true;
    let near = camera.near;
    const texels = [0, 0, 0];
    for (let i = 0; i < 3; i++) {
      const far = this.splits[i];
      // stagger far cascades
      const period = i === 0 ? 1 : i === 1 ? 2 : 3;
      if (this.frame % period === 0 || this.frame < 4) {
        texels[i] = this._fit(i, camera, near, far, lightDir);
        r.setRenderTarget(this.targets[i]);
        r.clear(false, true, false);
        r.render(scene, this.cams[i]);
        const cam = this.cams[i];
        this.mats[i].multiplyMatrices(cam.projectionMatrix, cam.matrixWorldInverse);
        this.texel.setComponent(i, texels[i]);
      }
      near = far * 0.9;
    }
    r.setRenderTarget(prevRT);
    r.autoClear = prevAuto;
    scene.overrideMaterial = prevOverride;
    scene.background = prevBg;
    for (const [m, mat] of saved) m.material = mat;
  }
}
