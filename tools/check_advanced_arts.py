#!/usr/bin/env python3
"""Lightweight JDK checks of production pure classes plus grammar parsing; no Gradle/game launch."""
from pathlib import Path
import subprocess
import tempfile
import shutil

ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'src/main/java/com/example/myvillage'
PURE=[BASE/'combat/art/ArtMath.java',BASE/'combat/art/ArtCooldowns.java',BASE/'client/combat/ArtRibbonShape.java']

def main() -> int:
    if not shutil.which('javac') or not shutil.which('java'):
        raise SystemExit('JDK 21 required for the lightweight domain checks')
    sources=[*PURE,ROOT/'tools/advanced_arts/AdvancedArtsCoreChecks.java',ROOT/'tools/advanced_arts/JavaSyntaxCheck.java']
    with tempfile.TemporaryDirectory(prefix='advanced-arts-java-') as out:
        subprocess.run(['javac','--release','21','-d',out,*map(str,sources)],check=True,cwd=ROOT)
        subprocess.run(['java','-ea','-cp',out,'AdvancedArtsCoreChecks'],check=True,cwd=ROOT)
        parsed=set((BASE/'combat/art').glob('*.java'))
        for area in ['combat/network','combat/runtime','combat/session']:
            parsed.update((BASE/area).glob('*Art*.java'))
        parsed.update(PURE)
        parsed.update(BASE/rel for rel in ['client/combat/ClientAdvancedArts.java','client/combat/ClientCombatEvents.java','client/combat/ClientCombatKeyMappings.java',
            'combat/runtime/CombatDamageService.java','combat/session/CombatSessionManager.java','combat/CombatEvents.java'])
        parsed.update((ROOT/'src/test/java/com/example/myvillage/combat/art').glob('*.java'))
        parsed.add(ROOT/'src/test/java/com/example/myvillage/client/combat/AdvancedArtClientStateTest.java')
        subprocess.run(['java','-cp',out,'JavaSyntaxCheck',*map(str,sorted(parsed))],check=True,cwd=ROOT)
    return 0

if __name__=='__main__':raise SystemExit(main())
