// Camera rig: orbit (default), free flight, guided tour along the road network, and smooth
// fly-to transitions between viewpoints. Keeps the eye above the voxel ground.
import * as THREE from 'three';

const tmpV = new THREE.Vector3();

export class CameraRig {
  constructor(camera, dom, groundAt) {
    this.camera = camera;
    this.dom = dom;
    this.groundAt = groundAt;
    this.mode = 'orbit';
    this.target = new THREE.Vector3(0, 100, -100);
    this.dist = 600;
    this.yaw = 0; // around +y, 0 looks toward -z
    this.pitch = -0.35;
    this.keys = new Set();
    this.velocity = new THREE.Vector3();
    this.flySpeed = 30;
    this.transition = null;
    this.tour = null;
    this.tourT = 0;
    this.tourSpeed = 9; // m/s
    this.tourPlaying = true;
    this.onModeChange = null;
    this._pointers = new Map();
    this._bind();
  }

  _bind() {
    const el = this.dom;
    el.addEventListener('contextmenu', (e) => e.preventDefault());
    el.addEventListener('pointerdown', (e) => {
      el.setPointerCapture(e.pointerId);
      this._pointers.set(e.pointerId, { x: e.clientX, y: e.clientY, button: e.button, shift: e.shiftKey });
      this._pinch = null;
      this.autoOrbit = false;
      this.cancelTransition();
    });
    el.addEventListener('pointerup', (e) => {
      this._pointers.delete(e.pointerId);
      this._pinch = null;
    });
    el.addEventListener('pointercancel', (e) => {
      this._pointers.delete(e.pointerId);
      this._pinch = null;
    });
    el.addEventListener('pointermove', (e) => {
      const p = this._pointers.get(e.pointerId);
      if (!p) return;
      const dx = e.clientX - p.x;
      const dy = e.clientY - p.y;
      p.x = e.clientX;
      p.y = e.clientY;
      if (this._pointers.size >= 2) {
        const pts = [...this._pointers.values()];
        const d = Math.hypot(pts[0].x - pts[1].x, pts[0].y - pts[1].y);
        const cx = (pts[0].x + pts[1].x) / 2;
        const cy = (pts[0].y + pts[1].y) / 2;
        if (this._pinch) {
          this.zoom(this._pinch.d / Math.max(d, 1));
          this.pan(cx - this._pinch.cx, cy - this._pinch.cy);
        }
        this._pinch = { d, cx, cy };
        return;
      }
      if (this.mode === 'tour') {
        // look around while touring
        this.tourLookYaw = (this.tourLookYaw || 0) - dx * 0.004;
        this.tourLookPitch = THREE.MathUtils.clamp((this.tourLookPitch || 0) - dy * 0.003, -0.8, 0.6);
        return;
      }
      const panning = p.button === 2 || p.button === 1 || p.shift;
      if (panning && this.mode === 'orbit') this.pan(dx, dy);
      else {
        this.yaw -= dx * 0.0042;
        this.pitch = THREE.MathUtils.clamp(this.pitch - dy * 0.0035, -1.45, this.mode === 'fly' ? 1.4 : 0.35);
      }
    });
    el.addEventListener(
      'wheel',
      (e) => {
        e.preventDefault();
        this.cancelTransition();
        if (this.mode === 'fly') {
          this.flySpeed = THREE.MathUtils.clamp(this.flySpeed * Math.exp(-e.deltaY * 0.001), 2, 400);
        } else if (this.mode === 'orbit') this.zoom(Math.exp(e.deltaY * 0.0012));
      },
      { passive: false },
    );
    window.addEventListener('keydown', (e) => {
      if (e.target && (e.target.tagName === 'INPUT' || e.target.tagName === 'SELECT')) return;
      this.keys.add(e.code);
    });
    window.addEventListener('keyup', (e) => this.keys.delete(e.code));
    window.addEventListener('blur', () => this.keys.clear());
  }

  zoom(f) {
    if (this.mode !== 'orbit') return;
    this.dist = THREE.MathUtils.clamp(this.dist * f, 4, 2600);
  }

  pan(dx, dy) {
    if (this.mode !== 'orbit') return;
    const s = this.dist * 0.0014;
    const right = tmpV.set(Math.cos(this.yaw), 0, -Math.sin(this.yaw));
    this.target.addScaledVector(right, -dx * s);
    const fwd = new THREE.Vector3(-Math.sin(this.yaw), 0, -Math.cos(this.yaw));
    this.target.addScaledVector(fwd, dy * s);
  }

  setMode(mode) {
    if (mode === this.mode) return;
    const cam = this.camera;
    if (mode === 'fly') {
      // keep the current view
      const dir = cam.getWorldDirection(tmpV);
      this.yaw = Math.atan2(-dir.x, -dir.z);
      this.pitch = Math.asin(THREE.MathUtils.clamp(dir.y, -1, 1));
    } else if (mode === 'orbit') {
      const dir = cam.getWorldDirection(tmpV).clone();
      const d = Math.max(30, Math.min(400, this.dist));
      this.target.copy(cam.position).addScaledVector(dir, d);
      this.dist = d;
      this.yaw = Math.atan2(-dir.x, -dir.z);
      this.pitch = Math.asin(THREE.MathUtils.clamp(dir.y, -1, 1));
    } else if (mode === 'tour') {
      this.tourLookYaw = 0;
      this.tourLookPitch = 0;
      if (this.tour) this.tourT = this._nearestTourT(cam.position);
    }
    this.mode = mode;
    this.cancelTransition();
    if (this.onModeChange) this.onModeChange(mode);
  }

  setTour(points) {
    const pts = points.map((p) => new THREE.Vector3(p[0], p[1], p[2]));
    this.tour = new THREE.CatmullRomCurve3(pts, false, 'centripetal', 0.5);
    this.tourLen = this.tour.getLength();
  }

  _nearestTourT(pos) {
    let best = 0;
    let bd = 1e18;
    for (let i = 0; i <= 400; i++) {
      const t = i / 400;
      const d = this.tour.getPointAt(t, tmpV).distanceToSquared(pos);
      if (d < bd) {
        bd = d;
        best = t;
      }
    }
    return best;
  }

  flyTo(view, duration = 2.6) {
    const cam = this.camera;
    const fromPos = cam.position.clone();
    const fromTarget = cam.position.clone().add(cam.getWorldDirection(new THREE.Vector3()).multiplyScalar(this.mode === 'orbit' ? this.dist : 60));
    if (this.mode === 'orbit') fromTarget.copy(this.target);
    if (this.mode === 'tour') this.setMode('orbit');
    this.transition = {
      t: 0,
      duration,
      fromPos,
      fromTarget,
      toPos: new THREE.Vector3(...view.pos),
      toTarget: new THREE.Vector3(...view.target),
    };
  }

  cancelTransition() {
    if (!this.transition) return;
    // adopt the in-between state
    const cam = this.camera;
    const dir = cam.getWorldDirection(new THREE.Vector3());
    if (this.mode === 'orbit') {
      this.target.copy(cam.position).addScaledVector(dir, this.dist);
    }
    this.transition = null;
  }

  update(dt) {
    const cam = this.camera;
    const k = this.keys;
    const fast = k.has('ShiftLeft') || k.has('ShiftRight') ? 4 : 1;
    if (this.transition) {
      const tr = this.transition;
      tr.t += dt / tr.duration;
      const t = Math.min(1, tr.t);
      const e = t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
      const pos = tr.fromPos.clone().lerp(tr.toPos, e);
      // arc upward mid-flight for long hops
      const hop = tr.fromPos.distanceTo(tr.toPos);
      pos.y += Math.sin(Math.PI * e) * Math.min(160, hop * 0.18);
      const tgt = tr.fromTarget.clone().lerp(tr.toTarget, e);
      cam.position.copy(pos);
      cam.lookAt(tgt);
      if (t >= 1) {
        this.transition = null;
        this.mode = 'orbit';
        this.target.copy(tr.toTarget);
        const d = tr.toPos.clone().sub(tr.toTarget);
        this.dist = d.length();
        this.yaw = Math.atan2(d.x, d.z);
        this.pitch = -Math.asin(THREE.MathUtils.clamp(d.y / this.dist, -1, 1));
        if (this.onModeChange) this.onModeChange('orbit');
      }
      return;
    }
    if (this.mode === 'orbit') {
      if (this.autoOrbit) this.yaw += dt * 0.05;
      const mv = new THREE.Vector3();
      const fwd = new THREE.Vector3(-Math.sin(this.yaw), 0, -Math.cos(this.yaw));
      const right = new THREE.Vector3(Math.cos(this.yaw), 0, -Math.sin(this.yaw));
      if (k.has('KeyW') || k.has('ArrowUp')) mv.add(fwd);
      if (k.has('KeyS') || k.has('ArrowDown')) mv.sub(fwd);
      if (k.has('KeyD') || k.has('ArrowRight')) mv.add(right);
      if (k.has('KeyA') || k.has('ArrowLeft')) mv.sub(right);
      if (k.has('KeyE')) mv.y += 1;
      if (k.has('KeyQ')) mv.y -= 1;
      if (mv.lengthSq() > 0) this.target.addScaledVector(mv.normalize(), dt * Math.max(8, this.dist * 0.6) * fast);
      const g = this.groundAt(this.target.x, this.target.z);
      if (this.target.y < g) this.target.y = g;
      const cp = Math.cos(this.pitch);
      const off = new THREE.Vector3(Math.sin(this.yaw) * cp, -Math.sin(this.pitch), Math.cos(this.yaw) * cp).multiplyScalar(this.dist);
      cam.position.copy(this.target).add(off);
      const cg = this.groundAt(cam.position.x, cam.position.z) + 1.5;
      if (cam.position.y < cg) cam.position.y = cg;
      cam.lookAt(this.target);
    } else if (this.mode === 'fly') {
      const dir = new THREE.Vector3(-Math.sin(this.yaw) * Math.cos(this.pitch), Math.sin(this.pitch), -Math.cos(this.yaw) * Math.cos(this.pitch));
      const right = new THREE.Vector3(Math.cos(this.yaw), 0, -Math.sin(this.yaw));
      const mv = new THREE.Vector3();
      if (k.has('KeyW') || k.has('ArrowUp')) mv.add(dir);
      if (k.has('KeyS') || k.has('ArrowDown')) mv.sub(dir);
      if (k.has('KeyD') || k.has('ArrowRight')) mv.add(right);
      if (k.has('KeyA') || k.has('ArrowLeft')) mv.sub(right);
      if (k.has('KeyE')) mv.y += 1;
      if (k.has('KeyQ') || k.has('KeyC')) mv.y -= 1;
      if (mv.lengthSq() > 0) mv.normalize().multiplyScalar(this.flySpeed * fast);
      this.velocity.lerp(mv, 1 - Math.exp(-dt * 6));
      cam.position.addScaledVector(this.velocity, dt);
      const g = this.groundAt(cam.position.x, cam.position.z) + 1.7;
      if (cam.position.y < g) cam.position.y = g;
      cam.lookAt(cam.position.clone().add(dir));
    } else if (this.mode === 'tour' && this.tour) {
      if (this.tourPlaying) this.tourT += (dt * this.tourSpeed) / this.tourLen;
      if (this.tourT >= 1) {
        // finale: hand over to a slow orbit around the last sight
        this.tourT = 0;
        if (this.tourEnd) {
          // continue smoothly: orbit the final sight from where the camera is now
          this.mode = 'orbit';
          this.target.set(...this.tourEnd.target);
          const d = this.camera.position.clone().sub(this.target);
          this.dist = Math.max(20, d.length());
          this.yaw = Math.atan2(d.x, d.z);
          this.pitch = THREE.MathUtils.clamp(-Math.asin(d.y / d.length()), -1.4, 0.3);
          this.autoOrbit = true;
          if (this.onModeChange) this.onModeChange('orbit');
          return;
        }
      }
      if (this.tourT < 0) this.tourT = 1;
      const p = this.tour.getPointAt(this.tourT);
      const ahead = this.tour.getPointAt(Math.min(1, this.tourT + 26 / this.tourLen));
      const g = this.groundAt(p.x, p.z) + 1.7;
      if (p.y < g) p.y = g;
      cam.position.lerp(p, 1 - Math.exp(-dt * 4));
      const look = ahead.clone().sub(cam.position);
      look.y = look.y * 0.5;
      const yaw = Math.atan2(-look.x, -look.z) + (this.tourLookYaw || 0);
      const pitch = Math.atan2(look.y, Math.hypot(look.x, look.z)) + (this.tourLookPitch || 0) - 0.05;
      const dir = new THREE.Vector3(-Math.sin(yaw) * Math.cos(pitch), Math.sin(pitch), -Math.cos(yaw) * Math.cos(pitch));
      cam.lookAt(cam.position.clone().add(dir));
      this.tourLookYaw = (this.tourLookYaw || 0) * Math.exp(-dt * 0.4);
      this.tourLookPitch = (this.tourLookPitch || 0) * Math.exp(-dt * 0.4);
    }
  }
}
