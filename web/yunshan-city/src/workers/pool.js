// Small priority job pool over module workers. Jobs are keyed; owners may re-prioritise or cancel
// pending jobs every frame. Lower priority value = more urgent.
export class WorkerPool {
  constructor(count, makeWorker) {
    this.workers = [];
    this.pending = new Map();
    this.inflight = new Map();
    this.ready = 0;
    this.nextId = 1;
    this.readyPromise = null;
    this.errors = [];
    for (let i = 0; i < count; i++) {
      const w = makeWorker(i);
      w.busy = 0;
      w.onmessage = (e) => this._onMessage(w, e.data);
      w.onerror = (e) => {
        this.errors.push(String(e.message || e));
        console.error('worker error', e);
      };
      this.workers.push(w);
    }
  }

  init(plan) {
    this.readyPromise = new Promise((resolve) => {
      this._resolveReady = resolve;
    });
    for (const w of this.workers) w.postMessage({ type: 'init', plan });
    return this.readyPromise;
  }

  _onMessage(w, msg) {
    if (msg.type === 'ready') {
      this.ready++;
      if (this.ready === this.workers.length && this._resolveReady) this._resolveReady();
      return;
    }
    w.busy--;
    const job = this.inflight.get(msg.id);
    this.inflight.delete(msg.id);
    if (msg.type === 'error') {
      this.errors.push(msg.message);
      console.error('worker job failed', msg.message);
      if (job && job.onError) job.onError(msg.message);
    } else if (job) {
      job.onDone(msg);
    }
    this.pump();
  }

  submit(key, payload, priority, onDone, onError) {
    const existing = this.pending.get(key);
    if (existing) {
      existing.priority = priority;
      return existing;
    }
    const job = { key, payload, priority, onDone, onError };
    this.pending.set(key, job);
    return job;
  }

  has(key) {
    return this.pending.has(key);
  }

  cancel(key) {
    this.pending.delete(key);
  }

  get busy() {
    return this.inflight.size;
  }

  pump() {
    if (this.ready < this.workers.length) return;
    for (const w of this.workers) {
      while (w.busy < 2 && this.pending.size) {
        let best = null;
        for (const job of this.pending.values()) if (!best || job.priority < best.priority) best = job;
        this.pending.delete(best.key);
        const id = this.nextId++;
        this.inflight.set(id, best);
        w.busy++;
        w.postMessage({ ...best.payload, id });
      }
    }
  }
}
