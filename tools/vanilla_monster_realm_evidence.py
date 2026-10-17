#!/usr/bin/env python3
"""Two isolated dedicated-server boots; native fixture evidence, no client or DevBridge."""
import json
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'reports/vanilla-monster-realms'


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    run = ROOT / 'run-acceptance'
    run.mkdir(exist_ok=True)
    if (run / 'vanilla-realm-world').exists() or (OUT / 'restart.json').exists():
        raise RuntimeError('Use a fresh Modal archive; refusing to overwrite prior evidence/world')
    (run / 'eula.txt').write_text('eula=true\n')
    (run / 'server.properties').write_text('\n'.join([
        'level-name=vanilla-realm-world', 'level-seed=42', 'level-type=minecraft:flat',
        'generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains","structures":{"structures":{}}}',
        'generate-structures=false', 'online-mode=false', 'server-ip=127.0.0.1', 'server-port=25579',
        'view-distance=2', 'simulation-distance=2', 'difficulty=normal', 'max-tick-time=180000',
        'spawn-protection=0', 'sync-chunk-writes=true', 'enable-command-block=false', '']) )
    # The release jar must exist already, and must not contain the opt-in evidence subscriber.
    jars=list((ROOT/'build/libs').glob('myvillage-*.jar'))
    assert len(jars)==1, jars
    with zipfile.ZipFile(jars[0]) as jar:
        assert not any('VanillaRealmEvidence' in name for name in jar.namelist())
    for phase in ('initial', 'restart'):
        command=['./gradlew','runAcceptanceServer','-PvanillaRealmEvidence','-x','generateAllStructures',
                 '--no-daemon','--max-workers=4','-Dorg.gradle.jvmargs=-Xmx2g','--console=plain']
        with (OUT/f'{phase}-stdout.log').open('w') as log:
            subprocess.run(command,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT,check=True,timeout=900)
        result=json.loads((OUT/f'{phase}.json').read_text())
        if not result['passed']:
            raise RuntimeError(f'{phase} failed: {result.get("error")}')
        print(f'{phase}: {len(result["checks"])} checks passed',flush=True)
    return 0


if __name__=='__main__':
    raise SystemExit(main())
