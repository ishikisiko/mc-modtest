"""独立工作台API：只写统一掉落表，提供权限、校验、备份和并发冲突保护。"""
from __future__ import annotations
import argparse,base64,fcntl,hashlib,hmac,json,os,secrets,tempfile,threading,time
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit
from .rules import Invalid,decode,validate,MAX_BYTES
SOURCE=Path('src/main/resources/data/yuanxian/drops/monster_drops.json')
ORIGIN='https://assets.ishiki333hhh.win'
class Conflict(Exception):pass
class Store:
    def __init__(self,repo,catalogue,state):
        self.file=repo.resolve()/SOURCE;self.catalogue=catalogue;self.state=state;self.state.mkdir(parents=True,exist_ok=True);(state/'history').mkdir(exist_ok=True);self.lock=threading.RLock()
    def read(self):
        raw=self.file.read_bytes();return {'config':decode(raw),'revision':hashlib.sha256(raw).hexdigest(),'saved_at':self.file.stat().st_mtime,'path':SOURCE.as_posix()}
    def save(self,data,expected):
        validate(data,self.catalogue);raw=(json.dumps(data,ensure_ascii=False,indent=2)+'\n').encode('utf-8')
        if len(raw)>MAX_BYTES:raise Invalid('统一表超过1 MiB')
        with self.lock,(self.state/'write.lock').open('a+b') as lock:
            fcntl.flock(lock,fcntl.LOCK_EX);current=self.read()
            if not expected or not hmac.compare_digest(expected,current['revision']):raise Conflict('统一表已被其他页面或工具修改，请先载入最新内容；当前草稿保留。')
            if current['config']==data:return current
            previous=self.file.read_bytes();backup=self.state/'history'/(time.strftime('%Y%m%dT%H%M%S',time.gmtime())+'-'+current['revision'][:16]+'.json')
            if not backup.exists():backup.write_bytes(previous);backup.chmod(0o600)
            fd,name=tempfile.mkstemp(prefix='.monster-drops-',suffix='.tmp',dir=self.file.parent)
            try:
                with os.fdopen(fd,'wb') as f:f.write(raw);f.flush();os.fsync(f.fileno())
                os.chmod(name,self.file.stat().st_mode & 0o777);os.replace(name,self.file)
                directory=os.open(self.file.parent,os.O_DIRECTORY)
                try:os.fsync(directory)
                finally:os.close(directory)
            finally:
                if os.path.exists(name):os.unlink(name)
            return self.read()
class App(ThreadingHTTPServer):
    def __init__(self,address,store,password):
        self.store=store;self.key=hashlib.pbkdf2_hmac('sha256',password.strip(),b'yuanxian-drop-workbench-v1',180000);self.attempts={};self.auth_lock=threading.Lock();super().__init__(address,Handler)
    def token(self):
        payload=str(int(time.time())+43200)+'.'+secrets.token_hex(16);signature=hmac.new(self.key,payload.encode(),hashlib.sha256).hexdigest();return payload+'.'+signature
    def allowed(self,header):
        try:
            token=header.removeprefix('Bearer ');expiry,nonce,signature=token.split('.');payload=expiry+'.'+nonce
            return int(expiry)>time.time() and hmac.compare_digest(signature,hmac.new(self.key,payload.encode(),hashlib.sha256).hexdigest())
        except (ValueError,AttributeError):return False
class Handler(BaseHTTPRequestHandler):
    server:App
    def log_message(self,fmt,*args):
        # 不记录密码、令牌、请求体或含凭据的URL。
        pass
    def reply(self,status,value):
        raw=json.dumps(value,ensure_ascii=False,separators=(',',':')).encode();self.send_response(status);self.send_header('Content-Type','application/json; charset=utf-8');self.send_header('Content-Length',str(len(raw)));self.send_header('Cache-Control','no-store');self.send_header('Access-Control-Allow-Origin',ORIGIN);self.send_header('Vary','Origin');self.send_header('X-Content-Type-Options','nosniff');self.end_headers();self.wfile.write(raw)
    def origin_allowed(self):return self.headers.get('Origin') in (None,ORIGIN)
    def body(self):
        try:n=int(self.headers.get('Content-Length','0'))
        except ValueError:raise Invalid('请求长度无效')
        if not 0<n<=MAX_BYTES:raise Invalid('请求内容为空或超过1 MiB')
        return decode(self.rfile.read(n))
    def do_OPTIONS(self):
        if not self.origin_allowed():return self.reply(403,{'error':'来源不允许'})
        self.send_response(204);self.send_header('Access-Control-Allow-Origin',ORIGIN);self.send_header('Access-Control-Allow-Methods','GET,POST,OPTIONS');self.send_header('Access-Control-Allow-Headers','Content-Type,Authorization,If-Match');self.send_header('Access-Control-Max-Age','600');self.end_headers()
    def do_GET(self):
        path=urlsplit(self.path).path
        if not self.origin_allowed():return self.reply(403,{'error':'来源不允许'})
        if path=='/api/health':return self.reply(200,{'status':'ok','authenticated':self.server.allowed(self.headers.get('Authorization',''))})
        if path=='/api/state':return self.reply(200,self.server.store.read())
        if path=='/api/history':
            if not self.server.allowed(self.headers.get('Authorization','')):return self.reply(401,{'error':'请先解锁编辑'})
            records=sorted((self.server.store.state/'history').glob('*.json'),key=lambda p:(p.stat().st_mtime,p.name),reverse=True)[:40]
            return self.reply(200,{'records':[{'id':p.stem,'saved_at':p.stat().st_mtime} for p in records]})
        return self.reply(404,{'error':'接口不存在'})
    def do_POST(self):
        if not self.origin_allowed():return self.reply(403,{'error':'来源不允许'})
        if self.headers.get('Content-Type','').split(';')[0]!='application/json':return self.reply(415,{'error':'仅接受JSON请求'})
        try:
            data=self.body();path=urlsplit(self.path).path
            if path=='/api/session':
                now=time.time();client=self.headers.get('CF-Connecting-IP',self.client_address[0])
                with self.server.auth_lock:
                    attempts=[t for t in self.server.attempts.get(client,[]) if now-t<60]
                    if len(attempts)>=8:return self.reply(429,{'error':'尝试次数较多，请一分钟后重试'})
                    self.server.attempts[client]=attempts+[now]
                password=data.get('password','') if isinstance(data,dict) else ''
                if not isinstance(password,str) or len(password)>1024:raise Invalid('密码格式无效')
                key=hashlib.pbkdf2_hmac('sha256',password.encode().strip(),b'yuanxian-drop-workbench-v1',180000)
                if not hmac.compare_digest(key,self.server.key):return self.reply(401,{'error':'密码不正确'})
                with self.server.auth_lock:self.server.attempts.pop(client,None)
                return self.reply(200,{'token':self.server.token(),'expires_in':43200})
            if not self.server.allowed(self.headers.get('Authorization','')):return self.reply(401,{'error':'请先解锁编辑'})
            if path=='/api/save':return self.reply(200,self.server.store.save(data,self.headers.get('If-Match','')))
            if path=='/api/restore':
                id=data.get('id','')
                if not isinstance(id,str) or not __import__('re').fullmatch('[0-9]{8}T[0-9]{6}-[0-9a-f]{16}',id):raise Invalid('备份ID无效')
                backup=self.server.store.state/'history'/(id+'.json')
                if not backup.is_file():return self.reply(404,{'error':'备份不存在'})
                return self.reply(200,self.server.store.save(decode(backup.read_bytes()),self.headers.get('If-Match','')))
            return self.reply(404,{'error':'接口不存在'})
        except Conflict as e:self.reply(409,{'error':str(e)})
        except Invalid as e:self.reply(400,{'error':str(e)})
        except (OSError,ValueError,TypeError,KeyError) as e:self.reply(500,{'error':'保存未完成，请保留草稿后重试','detail':type(e).__name__})

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--repo',type=Path,required=True);p.add_argument('--catalogue',type=Path,required=True);p.add_argument('--state',type=Path,required=True);p.add_argument('--password-file',type=Path,required=True);p.add_argument('--port',type=int,default=8791);a=p.parse_args();password=a.password_file.read_bytes().strip()
    if not password:raise SystemExit('需要非空访问密码')
    App(('127.0.0.1',a.port),Store(a.repo,json.loads(a.catalogue.read_text()),a.state),password).serve_forever()
if __name__=='__main__':main()
