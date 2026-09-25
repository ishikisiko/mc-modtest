// Single-file build: bundles three.js, the app and the generation worker into one HTML file
// that also works when opened directly from disk (file://).  Usage: npm run build
import { build } from 'esbuild';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const threePath = path.join(root, 'vendor/three.module.min.js');
const aliasThree = {
  name: 'alias-three',
  setup(b) {
    b.onResolve({ filter: /^three$/ }, () => ({ path: threePath }));
  },
};

const worker = await build({
  entryPoints: [path.join(root, 'src/workers/gen.worker.js')],
  bundle: true,
  format: 'iife',
  minify: true,
  write: false,
  target: 'es2020',
  logLevel: 'warning',
});
const app = await build({
  entryPoints: [path.join(root, 'src/main.js')],
  bundle: true,
  format: 'iife',
  minify: true,
  write: false,
  target: 'es2020',
  plugins: [aliasThree],
  logLevel: 'error',
});

const workerSrc = worker.outputFiles[0].text;
const appSrc = app.outputFiles[0].text;
const css = fs.readFileSync(path.join(root, 'src/ui/style.css'), 'utf8');
let html = fs.readFileSync(path.join(root, 'index.html'), 'utf8');
const esc = (s) => s.replace(/<\/script/gi, '<\\/script');
// (replacer functions: bundles contain `$` sequences that string replacements would expand)
html = html.replace(/<link rel="stylesheet"[^>]*>/, () => `<style>\n${css}\n</style>`);
html = html.replace(/<script type="importmap">[\s\S]*?<\/script>\n?/, () => '');
html = html.replace(
  /<script type="module" src="\.\/src\/main\.js"><\/script>/,
  () => `<script>window.__YUNSHAN_BUNDLE__ = true; window.__YUNSHAN_WORKER_SRC__ = ${esc(JSON.stringify(workerSrc))};</script>\n<script>${esc(appSrc)}</script>`,
);
fs.mkdirSync(path.join(root, 'dist'), { recursive: true });
const out = path.join(root, 'dist/yunshan-city.html');
fs.writeFileSync(out, html);
console.log(`wrote ${path.relative(root, out)} (${(html.length / 1024).toFixed(0)} KB)`);
