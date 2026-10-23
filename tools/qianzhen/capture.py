#!/usr/bin/env python3
"""Colab/offline counterpart of the repository's Yunshan browser preview capture."""
from __future__ import annotations
import argparse
import functools
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import threading
from playwright.sync_api import sync_playwright


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    p.add_argument('--only', nargs='+', help='只重新采集指定地点，其余已有画面保留')
    args = p.parse_args()
    out = args.out.resolve()
    data = json.loads((out/'batch-report.json').read_text())
    rows = data['buildings']
    if args.only:
        missing = set(args.only) - {r['id'] for r in rows}
        if missing:
            p.error('未知地点: ' + ', '.join(sorted(missing)))
        rows = [r for r in rows if r['id'] in args.only]
    class QuietHandler(SimpleHTTPRequestHandler):
        def log_message(self, *_):
            pass
    server = ThreadingHTTPServer(('127.0.0.1', 0), functools.partial(QuietHandler, directory=str(out)))
    worker = threading.Thread(target=server.serve_forever, daemon=True)
    worker.start()
    base = f'http://127.0.0.1:{server.server_port}'
    results = []
    try:
        with sync_playwright() as pw:
            browser = pw.chromium.launch(headless=True, args=['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--use-gl=angle'])
            try:
                for row in rows:
                    folder = out/row['id']
                    page = browser.new_page(viewport={'width':1200,'height':1000}, device_scale_factor=1)
                    errors = []
                    page.on('pageerror', lambda error: errors.append(str(error)))
                    page.goto(f'{base}/{row["id"]}/index.html', wait_until='networkidle')
                    page.wait_for_function('window.__ready===true', timeout=90000)
                    info = page.evaluate('window.__previewInfo')
                    for view in info['views']:
                        page.evaluate('(name)=>window.setView(name)', view)
                        page.locator('#scene').screenshot(path=str(folder/(view+'.png')))
                    page.set_viewport_size({'width':390,'height':844})
                    overflow = page.evaluate('document.documentElement.scrollWidth > innerWidth')
                    if errors or overflow:
                        raise RuntimeError(json.dumps({'id':row['id'],'errors':errors,'overflow':overflow}))
                    results.append({'id':row['id'], 'errors':errors, 'mobile_overflow':overflow, **info})
                    print(row['id']+': '+str(len(info['views']))+' views', flush=True)
                    page.close()
                page = browser.new_page(viewport={'width':1200,'height':1000})
                page.goto(base+'/index.html', wait_until='networkidle')
                page.screenshot(path=str(out/'overview.png'), full_page=True)
                missing = page.evaluate('[...document.images].filter(i=>!i.complete||i.naturalWidth===0).map(i=>i.src)')
                page.set_viewport_size({'width':390,'height':844})
                overflow = page.evaluate('document.documentElement.scrollWidth>innerWidth')
                if missing or overflow:
                    raise RuntimeError(json.dumps({'missing_images':missing,'overflow':overflow}))
                result = {'status':'passed','renderer':'repository-vendored Three.js / Chromium SwiftShader on Colab',
                          'selected':[r['id'] for r in results], 'completed':[r['id'] for r in results], 'unrun':[],
                          'buildings':results,'overview':{'missing_images':missing,'mobile_overflow':overflow},
                          'native_game_evidence':False}
                (out/'browser-check.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
            finally:
                browser.close()
    finally:
        server.shutdown()
        server.server_close()


if __name__ == '__main__':
    main()
