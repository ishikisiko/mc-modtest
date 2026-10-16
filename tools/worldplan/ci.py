"""Focused isolated-cloud worldplan checks. No unrelated release suite or source mutation."""
from pathlib import Path
import subprocess
import sys
import json
import time

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'reports/worldplan';OUT.mkdir(parents=True,exist_ok=True)
commands=[
    [sys.executable,'tools/worldplan/generate_assets.py'],
    [sys.executable,'tools/worldplan/generate_assets.py','--check'],
    ['./gradlew','test','jar','--tests','*worldplan*','--tests','*region.runtime*','--tests','*WorldSimGenesisTest*','--tests','*SimPurityGuardTest*','-x','generateAllStructures','--no-daemon','--max-workers=2','-Dorg.gradle.jvmargs=-Xmx3g'],
    [sys.executable,'tools/worldplan/preview.py','--seed','20261017'],
    [sys.executable,'tools/worldplan/preview.py','--seed','-9223372036854775808','--out','out/preview/worldplan/min-seed'],
]
if (ROOT/'tools/worldplan/acceptance.py').exists():commands.append([sys.executable,'tools/worldplan/acceptance.py'])
result={'source':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'environment':'isolated GitHub Actions; Modal credentials unavailable; not owner PC','commands':[]}
try:
    for i,cmd in enumerate(commands):
        start=time.monotonic();print('RUN',cmd,flush=True)
        with (OUT/f'{i:02}-command.log').open('w') as log:proc=subprocess.run(cmd,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT)
        entry={'command':cmd,'returncode':proc.returncode,'seconds':round(time.monotonic()-start,3)};result['commands'].append(entry);print(entry,flush=True)
        if proc.returncode:
            print((OUT/f'{i:02}-command.log').read_text()[-14000:],flush=True);raise SystemExit(proc.returncode)
finally:(OUT/'checks.json').write_text(json.dumps(result,ensure_ascii=False,indent=2))
