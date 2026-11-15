"""保留原资源服务，独立提供API和自动维护连接地址的HTTPS隧道。"""
from __future__ import annotations
import argparse,json,os,re,signal,subprocess,tempfile,threading,time
from pathlib import Path
from .server import App,Store

def publish(path,value):
    fd,name=tempfile.mkstemp(prefix='.connection-',dir=path.parent)
    try:
        with os.fdopen(fd,'w') as f:json.dump(value,f,ensure_ascii=False);f.write('\n')
        os.chmod(name,0o644);os.replace(name,path)
    finally:
        if os.path.exists(name):os.unlink(name)

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--repo',type=Path,required=True);p.add_argument('--site',type=Path,required=True);p.add_argument('--state',type=Path,required=True);p.add_argument('--password-file',type=Path,required=True);p.add_argument('--port',type=int,default=8791);p.add_argument('--cloudflared',default='/usr/bin/cloudflared');a=p.parse_args()
    a.state.mkdir(parents=True,exist_ok=True);catalogue=json.loads((a.site/'catalogue.json').read_text());password=a.password_file.read_bytes().strip()
    if not password:raise SystemExit('访问密码不能为空')
    server=App(('127.0.0.1',a.port),Store(a.repo,catalogue,a.state),password);threading.Thread(target=server.serve_forever,daemon=True).start();stop=threading.Event();child=None
    def end(*_):
        stop.set()
        if child and child.poll() is None:child.terminate()
    signal.signal(signal.SIGTERM,end);signal.signal(signal.SIGINT,end)
    try:
        while not stop.is_set():
            publish(a.site/'connection.json',{'status':'connecting','api_url':None,'updated_at':time.time()})
            args=[a.cloudflared,'tunnel','--no-autoupdate','--protocol','http2','--url',f'http://127.0.0.1:{a.port}']
            child=subprocess.Popen(args,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,bufsize=1)
            with (a.state/'tunnel.log').open('a') as log:
                for line in child.stdout:
                    log.write(line);log.flush()
                    found=re.search(r'https://[a-z0-9-]+\.trycloudflare\.com',line)
                    if found:publish(a.site/'connection.json',{'status':'ready','api_url':found.group(0),'updated_at':time.time()});print('掉落工作台HTTPS接口已就绪',flush=True)
                    if stop.is_set():break
            if child.poll() is None:
                try:child.wait(timeout=8)
                except subprocess.TimeoutExpired:child.kill();child.wait()
            if not stop.is_set():stop.wait(5)
    finally:
        end();server.shutdown();server.server_close()
        if child and child.poll() is None:
            try:child.wait(timeout=8)
            except subprocess.TimeoutExpired:child.kill();child.wait()
        publish(a.site/'connection.json',{'status':'offline','api_url':None,'updated_at':time.time()})
if __name__=='__main__':main()
