// 云山巨城 — bootstrap: plan, workers, renderer, day/night loop.
import * as THREE from 'three';
import { buildPlan } from './world/plan.js';
import { createField } from './world/field.js';
import { WorkerPool } from './workers/pool.js';
import { TerrainLOD } from './render/terrainLOD.js';
import { createTerrainMaterial, createTerrainDepthMaterial } from './render/terrainMaterial.js';
import { createSky } from './render/sky.js';
import { createStructMaterial, createStructDepthMaterial } from './render/structMaterial.js';
import { StructureManager } from './render/structures.js';
import { CascadedShadows } from './render/shadows.js';
import { PostFX } from './render/post.js';
import { CloudPass } from './render/clouds.js';
import { buildLightMap } from './render/lightmap.js';
import { DayNight } from './sim/daynight.js';
import { CameraRig } from './ui/camera.js';
import { QUALITY, pickQuality } from './config.js';
import { HUD } from './ui/hud.js';

const params = new URLSearchParams(location.search);
const $ = (id) => document.getElementById(id);

function setLoading(text, frac) {
  const el = $('loading-text');
  if (el) el.textContent = text;
  const bar = $('loading-bar');
  if (bar && frac !== undefined) bar.style.width = `${Math.round(frac * 100)}%`;
}

function makeUniforms() {
  const v3 = () => ({ value: new THREE.Vector3() });
  return {
    uTime: { value: 0 },
    uSunDir: v3(),
    uMoonDir: v3(),
    uSunCol: v3(),
    uMoonCol: v3(),
    uSkyZenith: v3(),
    uSkyHorizon: v3(),
    uSkyGlow: v3(),
    uAmbSky: v3(),
    uAmbGround: v3(),
    uHaze: v3(),
    uFogDensity: { value: 0.001 },
    uFogFalloff: { value: 0.006 },
    uFogBase: { value: 0 },
    uNight: { value: 0 },
    uLamp: { value: 0 },
    uWindows: { value: 0 },
    uStars: { value: 0 },
    uStarRot: { value: 0 },
    uPole: { value: new THREE.Vector3(0, Math.sin((29.5 * Math.PI) / 180), -Math.cos((29.5 * Math.PI) / 180)) },
    uLampCol: v3(),
    uLightMap: { value: null },
    uLightMapBounds: { value: new THREE.Vector4() },
  };
}

function fail(msg) {
  setLoading(msg);
  const bar = document.querySelector('.load-track');
  if (bar) bar.style.display = 'none';
}

async function main() {
  if (location.protocol === 'file:' && !window.__YUNSHAN_BUNDLE__) {
    fail('请通过本地服务器打开：在项目目录运行 npm start（或 python3 -m http.server），再访问 http://localhost:8080/');
    return;
  }
  {
    const test = document.createElement('canvas').getContext('webgl2');
    if (!test) {
      fail('此浏览器不支持 WebGL2，请使用最新版 Chrome / Edge / Firefox / Safari 打开');
      return;
    }
  }
  let stored = {};
  try {
    stored = JSON.parse(localStorage.getItem('yunshan-settings') || '{}');
  } catch (e) {
    stored = {};
  }
  const qName = params.get('q') || stored.quality || pickQuality();
  const Q = { ...(QUALITY[qName] || QUALITY.medium) };
  const canvas = $('c');
  const renderer = new THREE.WebGLRenderer({ canvas, antialias: false, powerPreference: 'high-performance', preserveDrawingBuffer: params.has('shot') });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, Q.pixelRatio));
  renderer.outputColorSpace = THREE.LinearSRGBColorSpace;
  renderer.toneMapping = THREE.NoToneMapping;
  renderer.autoClear = true;

  setLoading('规划城池…', 0.05);
  await new Promise((r) => setTimeout(r, 30));
  const t0 = performance.now();
  const plan = buildPlan(1);
  const field = createField(plan);
  console.log(`plan ${(performance.now() - t0).toFixed(0)} ms, lots ${plan.lotCount}, trees ${plan.treeCount}, instances ${plan.instances.length}`);

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(55, window.innerWidth / window.innerHeight, 0.3, 16000);
  const uniforms = makeUniforms();
  const lm = buildLightMap(plan.lights);
  uniforms.uLightMap.value = lm.tex;
  uniforms.uLightMapBounds.value.copy(lm.bounds);

  const shadows = new CascadedShadows(renderer, { size: Q.shadowSize, enabled: Q.shadows });
  const shared = { ...uniforms, ...shadows.uniforms };
  const terrainMat = createTerrainMaterial(shared);
  const terrainDepth = createTerrainDepthMaterial();
  const sky = createSky(uniforms);
  scene.add(sky);

  const nWorkers = Math.max(2, Math.min(6, (navigator.hardwareConcurrency || 4) - 1));
  const makeWorker = () => {
    if (window.__YUNSHAN_WORKER_SRC__) {
      // single-file build: worker code is embedded as a string
      const url = URL.createObjectURL(new Blob([window.__YUNSHAN_WORKER_SRC__], { type: 'text/javascript' }));
      return new Worker(url);
    }
    return new Worker(new URL('./workers/gen.worker.js', import.meta.url), { type: 'module' });
  };
  const pool = new WorkerPool(nWorkers, makeWorker);
  setLoading('唤醒工匠…', 0.1);
  await pool.init(plan);

  const terrain = new TerrainLOD({ scene, pool, material: terrainMat, depthMaterial: terrainDepth, lodK: Q.lodK });
  const structMat = createStructMaterial(shared);
  const structs = new StructureManager({ scene, pool, plan, material: structMat, depthMaterial: createStructDepthMaterial(), lodScale: Q.structLod });
  const post = new PostFX(renderer, { bloom: Q.bloom });
  const clouds = Q.clouds ? new CloudPass(renderer, uniforms, { steps: Q.cloudSteps || 36, scale: 0.5 }) : null;

  const day = new DayNight();
  if (params.has('time')) day.setTime(parseFloat(params.get('time')));
  if (params.has('paused')) day.playing = false;

  const groundAt = (x, z) => field.heightAt(x, z);
  const rig = new CameraRig(camera, canvas, groundAt);
  rig.setTour(plan.tour);
  rig.tourEnd = plan.views.summit;
  const view = plan.views[params.get('view') || 'overview'] || plan.views.overview;
  if (params.has('tour')) {
    rig.mode = 'tour';
    rig.tourT = parseFloat(params.get('tour')) || 0;
    rig.tourPlaying = !params.has('paused');
  }
  camera.position.set(...view.pos);
  camera.lookAt(...view.target);
  rig.target.set(...view.target);
  if (rig.mode === 'tour') {
    const p = rig.tour.getPointAt(rig.tourT);
    camera.position.copy(p);
  }
  {
    const d = new THREE.Vector3(...view.pos).sub(new THREE.Vector3(...view.target));
    rig.dist = d.length();
    rig.yaw = Math.atan2(d.x, d.z);
    rig.pitch = -Math.asin(d.y / rig.dist);
  }

  function resize() {
    const w = window.innerWidth;
    const h = window.innerHeight;
    renderer.setSize(w, h, false);
    camera.aspect = w / h;
    camera.updateProjectionMatrix();
    const pr = renderer.getPixelRatio();
    post.setSize(Math.floor(w * pr), Math.floor(h * pr));
    if (clouds) clouds.setSize(Math.floor(w * pr), Math.floor(h * pr));
  }
  window.addEventListener('resize', resize);
  resize();

  // initial refinement before revealing
  setLoading('堆叠山岳…', 0.2);
  const startWait = performance.now();
  for (;;) {
    camera.updateMatrixWorld();
    terrain.update(camera);
    structs.update(camera);
    pool.pump();
    const pending = terrain.pendingCount();
    const sp = structs.pendingCount();
    const total = structs.prefabs.size;
    const frac = 0.5 * Math.min(1, terrain.drawn.length / 60) + 0.5 * ((total - sp) / total);
    setLoading(pending ? `堆叠山岳… ${terrain.drawn.length} 块地形` : `营造城池… ${total - sp}/${total} 种建筑`, 0.2 + 0.75 * frac);
    if (params.has('debug') && Math.random() < 0.05) console.log(`loading: terrain pending ${pending} drawn ${terrain.drawn.length}, prefabs pending ${sp}, pool pending ${pool.pending.size} inflight ${pool.inflight.size} errors ${pool.errors.length}`);
    if ((pending === 0 && sp === 0 && terrain.drawn.length > 0) || performance.now() - startWait > (params.has('shot') ? 240000 : 20000)) break;
    await new Promise((r) => setTimeout(r, 50));
  }
  $('loading').classList.add('done');

  // ---- HUD & settings ----
  const settings = {
    quality: qName,
    labels: stored.labels !== undefined ? stored.labels : true,
    clouds: stored.clouds !== undefined ? stored.clouds : true,
    shadows: Q.shadows,
  };
  const saveSettings = () => {
    try {
      localStorage.setItem('yunshan-settings', JSON.stringify({ quality: settings.quality, labels: settings.labels, clouds: settings.clouds, seenHelp: true }));
    } catch (e) {
      /* storage unavailable */
    }
  };
  settings.onChange = (k) => {
    if (k === 'shadows') shadows.setEnabled(settings.shadows);
    saveSettings();
  };
  const applyQuality = (q) => {
    const nq = QUALITY[q];
    if (!nq) return;
    Object.assign(Q, nq);
    settings.quality = q;
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, Q.pixelRatio));
    terrain.lodK = Q.lodK;
    structs.lod = [60, 150, 360, 850].map((d) => d * Q.structLod);
    structs.dirty = true;
    if (clouds) clouds.uniforms.uSteps.value = Q.cloudSteps;
    settings.shadows = Q.shadows;
    shadows.setEnabled(Q.shadows);
    resize();
    hud.setQualityLabel(q);
    saveSettings();
  };
  const hud = new HUD({ day, rig, plan, camera, settings, onQuality: applyQuality });
  if (params.has('nohud')) {
    document.getElementById('hud').style.display = 'none';
    hud.labelLayer.style.display = 'none';
  }
  if (!params.has('shot') && !stored.seenHelp) {
    hud.toggleHelp(true);
    stored.seenHelp = true;
    try {
      localStorage.setItem('yunshan-settings', JSON.stringify({ ...stored, quality: settings.quality }));
    } catch (e) {
      /* ignore */
    }
  }

  // adaptive resolution: keep the frame rate smooth on unknown hardware
  let dynScale = 1;
  let slowT = 0;
  let fastT = 0;
  const adapt = (dt) => {
    if (shotMode || document.hidden) return;
    const fps = 1 / Math.max(dt, 1e-3);
    if (fps < 26) {
      slowT += dt;
      fastT = 0;
    } else if (fps > 52) {
      fastT += dt;
      slowT = 0;
    } else {
      slowT = Math.max(0, slowT - dt);
      fastT = Math.max(0, fastT - dt);
    }
    if (slowT > 2.5 && dynScale > 0.55) {
      dynScale = Math.max(0.55, dynScale * 0.85);
      slowT = 0;
      renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, Q.pixelRatio) * dynScale);
      resize();
    } else if (fastT > 4 && dynScale < 1) {
      dynScale = Math.min(1, dynScale / 0.85);
      fastT = 0;
      renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, Q.pixelRatio) * dynScale);
      resize();
    }
  };
  const clock = new THREE.Clock();
  const shotMode = params.has('shot');
  let shotLeft = Number(params.get('frames') || 4);
  let frames = 0;
  let fpsT = 0;
  const stats = $('stats');
  function frame() {
    const rawDt = clock.getDelta();
    const dt = Math.min(0.1, rawDt);
    adapt(rawDt);
    uniforms.uTime.value += dt;
    day.update(dt);
    day.applyTo(uniforms);
    uniforms.uStarRot.value = (day.time / 24) * Math.PI * 2;
    rig.update(dt);
    camera.updateMatrixWorld();
    hud.update();
    terrain.update(camera);
    structs.update(camera);
    pool.pump();
    shadows.update(scene, camera, day.keyDir, [...terrain.meshes, ...structs.meshes]);
    renderer.setRenderTarget(post.sceneRT);
    renderer.render(scene, camera);
    let cloudTex = null;
    if (clouds && settings.clouds) {
      clouds.uniforms.uMist.value = 0.8 + 0.8 * Math.exp(-Math.pow((day.time - 6.5) / 2.0, 2));
      cloudTex = clouds.render(camera, post.sceneRT.depthTexture);
    }
    post.render({ exposure: day.exposure, night: day.night, time: uniforms.uTime.value, bloom: 0.06 + day.night * 0.1, clouds: cloudTex });
    frames++;
    fpsT += dt;
    if (fpsT > 0.5 && stats) {
      stats.textContent = `${Math.round(frames / fpsT)} fps · 地形 ${terrain.stats.drawn} 块 ${(terrain.stats.quads / 1000).toFixed(0)}k 面 · 建筑 ${structs.stats.drawn} 批 ${(structs.stats.quads / 1000).toFixed(0)}k 面 · 队列 ${pool.pending.size}`;
      frames = 0;
      fpsT = 0;
    }
    if (shotMode && --shotLeft <= 0) {
      window.__shotDone = true;
      return;
    }
    requestAnimationFrame(frame);
  }
  window.__app = { renderer, scene, camera, terrain, structs, pool, day, rig, plan, field, uniforms };
  requestAnimationFrame(frame);
}

main().catch((e) => {
  console.error(e);
  setLoading('出错了：' + e.message);
});
