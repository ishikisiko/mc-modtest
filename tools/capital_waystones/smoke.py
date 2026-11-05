"""仅在云端隔离新世界验证传送亭台生成、原生石碑身份与拆除保护。"""
from __future__ import annotations

import json
import os
from pathlib import Path
import secrets
import signal
import socket
import subprocess
import sys
import time
import traceback

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools'))
from combat_capture.rcon import Rcon, RconError


def write(path,data):
    Path(path).parent.mkdir(parents=True,exist_ok=True)
    Path(path).write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')


def port():
    with socket.socket() as s:s.bind(('127.0.0.1',0));return s.getsockname()[1]


class Server:
    def __init__(self,out,optional,game=None):
        self.out=Path(out);self.out.mkdir(parents=True)
        self.game=Path(game) if game else self.out/'server'
        self.optional=optional;self.password=secrets.token_hex(16);self.sport=port();self.rport=port();self.commands=[]

    def command(self,value):
        with Rcon('127.0.0.1',self.rport,self.password,timeout=240) as c:result=c.cmd(value)
        self.commands.append({'command':value,'response':result});write(self.out/'commands.json',self.commands)
        return result

    def query(self,value):
        text=self.command('capitalstonesprobe '+value)
        if 'CAPITAL_WAYSTONES_PROBE ' not in text:raise RuntimeError(text)
        return json.loads(text.split('CAPITAL_WAYSTONES_PROBE ',1)[1])

    def __enter__(self):
        self.game.mkdir(parents=True,exist_ok=True)
        props={'server-ip':'127.0.0.1','server-port':self.sport,'online-mode':'false','enforce-secure-profile':'false',
               'enable-rcon':'true','rcon.port':self.rport,'rcon.password':self.password,'broadcast-rcon-to-ops':'false',
               'level-name':'world','level-type':'myvillage\\:cultivation','level-seed':'6166082384136082383',
               'generate-structures':'true','spawn-protection':0,'difficulty':'peaceful','view-distance':2,
               'simulation-distance':2,'max-tick-time':600000,'max-players':2,'allow-flight':'true'}
        (self.game/'server.properties').write_text('\n'.join(f'{k}={v}' for k,v in props.items())+'\n')
        (self.game/'eula.txt').write_text('eula=true\n')
        log=(self.out/'server.log').open('w');self.log=log
        args=['./gradlew','-I','tools/waystones_smoke/runtime.init.gradle','runAcceptanceServer','-x','processResources',
              '--no-daemon','--console=plain','--max-workers=2','-Dorg.gradle.jvmargs=-Xmx2g']
        if self.optional:args+=['-Pwaystones_test','-PwaystonesEvidence']
        self.process=subprocess.Popen(args,cwd=ROOT,env=dict(os.environ,MYVILLAGE_WAYSTONES_GAME=str(self.game)),stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
        try:
            deadline=time.monotonic()+900
            while time.monotonic()<deadline:
                if self.process.poll() is not None:raise RuntimeError(f'server exited {self.process.returncode}')
                try:
                    if 'players online' in self.command('list'):
                        self.command('gamerule randomTickSpeed 0');self.command('gamerule doMobSpawning false');return self
                except (OSError,RconError):pass
                time.sleep(3)
            raise TimeoutError('server startup')
        except BaseException:self.__exit__(None,None,None);raise

    def __exit__(self,*args):
        try:
            self.command('stop');self.process.wait(timeout=90)
        except (OSError,RconError,subprocess.TimeoutExpired):
            if self.process.poll() is None:
                os.killpg(self.process.pid,signal.SIGTERM)
                try:self.process.wait(timeout=25)
                except subprocess.TimeoutExpired:os.killpg(self.process.pid,signal.SIGKILL);self.process.wait(timeout=10)
        self.log.close();write(self.out/'cleanup.json',{'stopped':self.process.poll() is not None,'exit_code':self.process.returncode})


def inspect(server,index):
    row=server.query(f'inspect {index}')
    # 区块 BE 的 onLoad 可能在下一次服务器更新完成；保留每次原始回读。
    for _ in range(10):
        if row.get('valid') or row.get('marker'):return row
        time.sleep(.5);row=server.query(f'inspect {index}')
    return row


def validate(row):
    assert row.get('valid') is True,row
    assert row.get('registered') is True,row
    assert row['uuid']==row['upper_uuid'],row
    assert row['name']==row['expected_name'],row
    assert row['origin']=='VILLAGE',row
    assert 'half=lower' in row['lower'] and 'half=upper' in row['upper'],row
    assert row['arrival_supported']==25 and row['arrival_clear']==98,row


def main(out):
    if os.environ.get('MYVILLAGE_CLOUD_CHECK')!='capital_waystones':raise RuntimeError('该服务器检查只在获授权的云端副本执行')
    out=Path(out);out.mkdir(parents=True,exist_ok=False)
    result={'selected':['without_waystones','all_25_generated','reload_identity','broken_stone_not_rebuilt'],
            'completed':[],'failed':[],'unrun':[],'mapped_input':'not_run','owner_appearance':'not_verified'}
    try:
        with Server(out/'without-waystones',False) as server:
            assert 'players online' in server.command('list')
            result['completed'].append('without_waystones')
        write(out/'result.json',result)
        with Server(out/'with-waystones',True) as server:
            rows=server.query('list')['sites'];assert len(rows)==25
            first=[]
            for i in range(25):
                row=inspect(server,i);validate(row);first.append(row);write(out/'first-load.json',first)
            registered=server.query('registered')['registered'];assert len(registered)==25 and len({r['name'] for r in registered})==25
            write(out/'registered-first.json',registered);result['completed'].append('all_25_generated')
            marker=server.query('mark 0');assert marker['marker'] is True;write(out/'marker-before.json',marker)
            game=server.game
        write(out/'result.json',result)
        with Server(out/'after-restart',True,game=game) as server:
            marker=inspect(server,0);assert marker['marker'] is True and marker['lower_be'] is False
            after=[]
            for i in range(1,25):
                row=inspect(server,i);validate(row);assert row['uuid']==first[i]['uuid'],(first[i],row);after.append(row);write(out/'after-restart.json',after)
            registered=server.query('registered')['registered'];assert len(registered)==24 and len({r['name'] for r in registered})==24
            result['completed']+=['reload_identity','broken_stone_not_rebuilt'];write(out/'registered-after.json',registered)
    except BaseException as e:
        result['failed'].append({'error':repr(e),'traceback':traceback.format_exc()});raise
    finally:
        result['unrun']=[s for s in result['selected'] if s not in result['completed']]
        result['status']='pass' if not result['failed'] and not result['unrun'] else 'fail'
        write(out/'result.json',result)
    return result


if __name__=='__main__':main(sys.argv[1])
