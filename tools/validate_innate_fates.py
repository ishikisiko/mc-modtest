#!/usr/bin/env python3
"""Fate data and authority contract. Static evidence never substitutes for actual lifecycle/UI checks."""
from __future__ import annotations
import argparse
import json
import math
from pathlib import Path
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]
JAVA = 'src/main/java/com/example/myvillage/'
DATA = 'src/main/resources/data/myvillage/myvillage/innate_fate'
EFFECTS = {'health','speed','knockback','fall_reduction','gain','study','fire_bonus','environment_fire_reduction',
           'recovery_threshold','recovery_fraction','recovery_ticks','recovery_cooldown','hunter_bonus',
           'hunter_recovery','hunter_cooldown','low_threshold','low_reduction','low_knockback','damage_bonus',
           'low_speed','low_speed_threshold'}

def validate(root: Path = ROOT, jar: Path | None = None) -> list[str]:
    errors=[]
    def need(ok, message):
        if not ok: errors.append(message)
    def read(path):
        try:return (root/path).read_text()
        except OSError:errors.append('Missing '+str(path));return ''
    try:
        definitions={p.stem:json.loads(p.read_text()) for p in sorted((root/DATA).glob('*.json'))}
        rules=json.loads(read('src/main/resources/data/myvillage/fates/rules.json'))
        langs=[json.loads(read('src/main/resources/assets/myvillage/lang/'+l+'.json')) for l in ('en_us','zh_cn')]
    except (ValueError,TypeError) as e:return ['Fate JSON: '+str(e)]
    need(len(definitions)>=6,'FATE_POOL: at least six implemented definitions required')
    ordinary=[]
    for name,d in definitions.items():
        tier=d.get('tier');need(type(tier) is int and 0<=tier<=5, 'FATE_TIER: '+name)
        need(type(d.get('cost')) is int and d.get('cost')==tier+1 if type(tier) is int else False,'FATE_COST: '+name)
        need(isinstance(d.get('group'),str) and bool(d['group']),'FATE_GROUP: '+name)
        if tier==0:ordinary.append(d.get('group'))
        for k in ('translation_key','description_key'):need(all(d.get(k) in lang for lang in langs),'FATE_LANG: '+name+' '+k)
        effects=d.get('effects');need(isinstance(effects,dict) and bool(effects),'FATE_EFFECTS: '+name)
        if not isinstance(effects,dict):continue
        for k,v in effects.items():
            need(k in EFFECTS,'FATE_EFFECT_UNKNOWN: '+name+' '+k)
            valid=type(v) in (int,float) and math.isfinite(v)
            if k.endswith(('ticks','cooldown')):valid=valid and v==int(v) and 1<=v<=24000
            else:valid=valid and (-.9 if k=='health' else 0)<=v<=1
            need(valid,'FATE_EFFECT_VALUE: '+name+' '+k)
            need(all('fate.myvillage.effect.'+k in lang for lang in langs),'FATE_EFFECT_LANG: '+k)
        if effects.get('recovery_fraction',0)>0:need(all(effects.get(k,0)>0 for k in ('recovery_threshold','recovery_ticks','recovery_cooldown')),'FATE_RECOVERY: '+name)
        if effects.get('hunter_recovery',0)>0:need(effects.get('hunter_cooldown',0)>0,'FATE_HUNTER: '+name)
        need(0<=d.get('minimum_share',0)<=10000 and 1<=d.get('maximum_roots',5)<=5,'FATE_ROOT: '+name)
    need(len(set(ordinary))>=3,'FATE_LOW_BUDGET: three distinct ordinary groups required')
    weights=rules.get('tier_weights',[])
    need(len(weights)==6 and all(type(v) in (int,float) and math.isfinite(v) and v>=0 for v in weights) and sum(weights)>0,'FATE_WEIGHTS')
    need(type(rules.get('request_ticks')) is int and rules['request_ticks']>0,'FATE_THROTTLE')
    service=read(JAVA+'cultivation/CultivationService.java')
    need('player.setData(CultivationAttachments.FATES.get(), replacement)' in service,'FATE_WRITER: service boundary missing')
    for p in (root/(JAVA+'cultivation')).rglob('*.java'):
        if p.name!='CultivationService.java':need(not re.search(r'\.setData\(CultivationAttachments\.FATES',p.read_text()),'FATE_WRITER: '+p.name)
    attachment=read(JAVA+'cultivation/CultivationAttachments.java')
    need('FateState.CODEC).copyOnDeath()' in attachment,'FATE_PERSISTENCE: copyOnDeath required')
    payload=read(JAVA+'cultivation/network/FateIntentPayload.java')
    need('byte action, long revision, byte value' in payload,'FATE_INTENT: intent-only bounded fields required')
    runtime=read(JAVA+'cultivation/fate/FateRuntime.java')
    for token in ('BYPASSES_INVULNERABILITY','LivingDropsEvent','recoverSpiritualPower','replaceFates','addPermanentModifier'):
        need(token in runtime,'FATE_RUNTIME: '+token)
    need('FateSelection.transition' in read(JAVA+'cultivation/fate/FateService.java'),'FATE_AUTHORITY: production must use tested transition')
    page=read(JAVA+'client/cultivation/panel/FatesPage.java')
    need('extends PanelPage' in page and 'PanelContext' in page and 'sendFate' in page,'FATE_PANEL')
    for p in (root/(JAVA+'cultivation/fate')).glob('*.java'):
        need('net.minecraft.client' not in p.read_text(),'FATE_CLIENT_IMPORT: '+p.name)
    if jar:
        try:
            with zipfile.ZipFile(jar) as z:
                files=[*sorted((root/DATA).glob('*.json')),root/'src/main/resources/data/myvillage/fates/rules.json']
                for p in files:need(z.read(p.relative_to(root/'src/main/resources').as_posix())==p.read_bytes(),'FATE_JAR_RESOURCE: '+p.name)
                for name in ('FateState','FateService','FateRuntime','FateSelection'):
                    need(f'com/example/myvillage/cultivation/fate/{name}.class' in z.namelist(),'FATE_JAR_CLASS: '+name)
        except (OSError,KeyError,zipfile.BadZipFile) as e:errors.append('FATE_JAR: '+str(e))
    return errors

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--root',type=Path,default=ROOT);parser.add_argument('--jar',type=Path);args=parser.parse_args()
    errors=validate(args.root,args.jar)
    for e in errors:print(e)
    print('Innate fates: '+('FAIL' if errors else 'PASS'))
    return bool(errors)
if __name__=='__main__':raise SystemExit(main())
