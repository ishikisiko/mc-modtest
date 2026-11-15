"""安装独立工作台服务；前端沿用现有静态域名，不修改8765服务或原隧道。"""
from __future__ import annotations
import argparse,json,os,shlex,shutil,subprocess,time
from pathlib import Path

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--repo',type=Path,required=True);p.add_argument('--site',type=Path,required=True);p.add_argument('--password-file',type=Path,required=True);p.add_argument('--name',default='myvillage-drop-workbench');p.add_argument('--port',type=int,default=8791);p.add_argument('--public',type=Path,default=Path.home()/'.local/share/myvillage-assets/www/drop-workbench');a=p.parse_args()
    if not a.password_file.is_file() or not a.password_file.stat().st_size:raise SystemExit('需要私密密码文件')
    if not (a.site/'index.html').is_file() or not (a.site/'catalogue.json').is_file():raise SystemExit('请先生成完整网页与目录')
    catalogue=json.loads((a.site/'catalogue.json').read_text())
    if not catalogue.get('categories') or any(not v.get('portrait') for v in catalogue.get('entities',[])):raise SystemExit('目录缺少分类或生物三视图，请先运行 portraits 并重新生成 catalogue')
    home=Path.home();base=home/'.local/share'/a.name;state=home/'.local/state'/a.name;app=base/'app';release=base/'releases'/(time.strftime('%Y%m%dT%H%M%S',time.gmtime())+'-'+str(time.time_ns()%1000000000));release.mkdir(parents=True,exist_ok=False);state.mkdir(parents=True,exist_ok=True);state.chmod(0o700)
    shutil.copytree(a.site,release,dirs_exist_ok=True);(app/'tools').mkdir(parents=True,exist_ok=True);(app/'tools/__init__.py').touch();shutil.copytree(Path(__file__).parent,app/'tools/drop_workbench',dirs_exist_ok=True,ignore=shutil.ignore_patterns('__pycache__','*.pyc'))
    password=state/'access-password'
    if a.password_file.resolve()!=password.resolve():shutil.copyfile(a.password_file,password)
    password.chmod(0o600)
    a.public.parent.mkdir(parents=True,exist_ok=True);link=a.public.with_name('.'+a.public.name+'-next')
    if link.is_symlink():link.unlink()
    if a.public.exists() and not a.public.is_symlink():raise SystemExit('部署目标已存在且不是工作台发布链接，停止覆盖')
    link.symlink_to(release);os.replace(link,a.public)
    command=['/usr/bin/python3','-m','tools.drop_workbench.serve','--repo',str(a.repo.resolve()),'--site',str(a.public),'--state',str(state),'--password-file',str(password),'--port',str(a.port)]
    unit='\n'.join(['[Unit]','Description=Yuanxian monster drop workbench','After=network-online.target','','[Service]','Type=simple','WorkingDirectory='+str(app),'ExecStart='+' '.join(shlex.quote(x) for x in command),'Restart=on-failure','RestartSec=3','KillMode=control-group','NoNewPrivileges=true','UMask=0077','','[Install]','WantedBy=default.target',''])
    path=home/'.config/systemd/user'/(a.name+'.service');path.parent.mkdir(parents=True,exist_ok=True);path.write_text(unit)
    subprocess.run(['systemctl','--user','daemon-reload'],check=True);subprocess.run(['systemctl','--user','enable',a.name+'.service'],check=True)
    subprocess.run(['systemctl','--user','restart',a.name+'.service'],check=True)
    print(json.dumps({'service':a.name+'.service','site':str(a.public),'repo':str(a.repo.resolve()),'port':a.port,'access_password':'private file; value not logged'},ensure_ascii=False))
if __name__=='__main__':main()
