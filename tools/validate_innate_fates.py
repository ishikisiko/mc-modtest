#!/usr/bin/env python3
"""Fate data and authority contract. Static evidence never substitutes for actual lifecycle/UI checks."""
from __future__ import annotations
import argparse
import json
import math
from pathlib import Path
import re
import zipfile

import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from tools.validate_cultivation_initiation import _strip_java_comments, _method_body

ROOT = Path(__file__).resolve().parents[1]
JAVA = 'src/main/java/com/example/myvillage/'
DATA = 'src/main/resources/data/myvillage/myvillage/innate_fate'
EFFECTS = {'health','speed','knockback','fall_reduction','gain','study','fire_bonus','environment_fire_reduction',
           'recovery_threshold','recovery_fraction','recovery_ticks','recovery_cooldown','hunter_bonus',
           'hunter_recovery','hunter_cooldown','low_threshold','low_reduction','low_knockback','damage_bonus',
           'low_speed','low_speed_threshold','power_cap_bonus','skill_refund_fraction',
           'pill_healing_bonus','pill_shield_fraction','pill_shield_ticks','pill_shield_cooldown',
           'sword_stack_bonus','sword_max_stacks','sword_timeout_ticks','bones_threshold','bones_damage_bonus','bones_knockback_radius','bones_knockback_strength'}

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
    need(len(definitions)>=6,'FATE_POOL: at least six selectable definitions required')
    ordinary=[]
    for name,d in definitions.items():
        tier=d.get('tier');need(type(tier) is int and 0<=tier<=5, 'FATE_TIER: '+name)
        need(type(d.get('cost')) is int and d.get('cost')==tier+1 if type(tier) is int else False,'FATE_COST: '+name)
        need(isinstance(d.get('group'),str) and bool(d['group']),'FATE_GROUP: '+name)
        if tier==0:ordinary.append(d.get('group'))
        for k in ('translation_key','description_key'):need(all(d.get(k) in lang for lang in langs),'FATE_LANG: '+name+' '+k)
        effects=d.get('effects')
        placeholder=d.get('placeholder',False)
        need(type(placeholder) is bool,'FATE_PLACEHOLDER: '+name)
        need(isinstance(effects,dict) and (bool(effects) or placeholder is True),'FATE_EFFECTS: '+name)
        if placeholder is True:
            need(effects=={},'FATE_PLACEHOLDER_EFFECTS: '+name)
            for lang,marker in zip(langs,('no gameplay effects','暂无实际效果')):
                need(marker in lang.get(d.get('description_key'),''),'FATE_PLACEHOLDER_LABEL: '+name)
        if not isinstance(effects,dict):continue
        for k,v in effects.items():
            need(k in EFFECTS,'FATE_EFFECT_UNKNOWN: '+name+' '+k)
            valid=type(v) in (int,float) and math.isfinite(v)
            if k.endswith(('ticks','cooldown')):valid=valid and v==int(v) and 1<=v<=24000
            elif k=='sword_max_stacks':valid=valid and v==int(v) and 1<=v<=64
            elif k=='bones_knockback_radius':valid=valid and 0<v<=16
            else:valid=valid and (-.9 if k=='health' else 0)<=v<=1
            need(valid,'FATE_EFFECT_VALUE: '+name+' '+k)
            need(all('fate.myvillage.effect.'+k in lang for lang in langs),'FATE_EFFECT_LANG: '+k)
        if effects.get('recovery_fraction',0)>0:need(all(effects.get(k,0)>0 for k in ('recovery_threshold','recovery_ticks','recovery_cooldown')),'FATE_RECOVERY: '+name)
        if effects.get('hunter_recovery',0)>0:need(effects.get('hunter_cooldown',0)>0,'FATE_HUNTER: '+name)
        if effects.get('pill_shield_fraction',0)>0:need(all(effects.get(k,0)>0 for k in ('pill_shield_ticks','pill_shield_cooldown')),'FATE_PILL_SHIELD: '+name)
        if effects.get('bones_threshold',0)>0:need(all(effects.get(k,0)>0 for k in ('bones_damage_bonus','bones_knockback_radius','bones_knockback_strength')),'FATE_BONES: '+name)
        if effects.get('sword_stack_bonus',0)>0:
            need(all(effects.get(k,0)>0 for k in ('sword_max_stacks','sword_timeout_ticks')),'FATE_SWORD: '+name)
            skill=d.get('derived_skill','')
            need(isinstance(skill,str) and re.fullmatch(r'[a-z0-9_.-]+:[a-z0-9_/.-]+',skill) is not None,'FATE_DERIVED_SKILL: '+name)
            if isinstance(skill,str) and ':' in skill:
                ns,path=skill.split(':',1)
                target=root/'src/main/resources/data'/ns/'combat/skill'/(path+'.json')
                need(target.is_file(),'FATE_DERIVED_SKILL: missing '+skill)
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
    need(bool(re.search(r'record\s+FateIntentPayload\s*\(\s*byte action,\s*long revision,\s*byte value,\s*long sessionToken\s*\)', _strip_java_comments(payload))), 'FATE_INTENT: only action/revision/value/sessionToken fields allowed')
    runtime=read(JAVA+'cultivation/fate/FateRuntime.java')
    for token in ('BYPASSES_INVULNERABILITY','LivingDropsEvent','recoverSpiritualPower','replaceFates','addPermanentModifier'):
        need(token in runtime,'FATE_RUNTIME: '+token)
    need('FateSelection.transition' in read(JAVA+'cultivation/fate/FateService.java'),'FATE_AUTHORITY: production must use tested transition')
    page=_strip_java_comments(read(JAVA+'client/cultivation/panel/FatesPage.java'))
    need('extends PanelPage' in page and 'PanelContext' in page, 'FATE_PANEL')
    need(not re.search(r'sendFate|ClientCultivationIntentSender|FateIntentPayload|new PanelButton|selection\s*\^=', page), 'FATE_PANEL_READONLY: H page must only show results')
    screen=_strip_java_comments(read(JAVA+'client/cultivation/AwakeningScreen.java'))
    need('extends Screen' in screen and 'sessionToken' in screen and 'sendFate' in screen, 'FATE_AWAKENING_SCREEN: independent session-bound screen required')
    need('AwakeningClosePayload' in screen or 'sendAwakeningClose' in screen, 'FATE_SESSION_CLOSE: screen must close the session')
    sessions=_strip_java_comments(read(JAVA+'cultivation/fate/SteleAwakeningSessions.java'))
    valid=_method_body(sessions, 'valid') or ''
    # Pure numeric transitions are tested in Java; these checks enforce the world authority boundary.
    for token in ('SPIRIT_TESTING_STELE', 'isAlive', 'isRemoved', 'isSpectator', 'dimension', 'hasChunkAt', 'canInteractWithBlock'):
        need(token in sessions, 'FATE_STELE_GATE: '+token)
    need(bool(valid) and ('token' in valid.lower()), 'FATE_STELE_GATE: validate the submitted token')
    for token in ('PlayerLoggedOutEvent','PlayerChangedDimensionEvent','LivingDeathEvent','EntityTeleportEvent','ServerStoppingEvent','ServerTickEvent'):
        need(token in sessions, 'FATE_SESSION_LIFECYCLE: '+token)
    service_source=_strip_java_comments(read(JAVA+'cultivation/fate/FateService.java'))
    intent=_method_body(service_source, 'intent') or ''
    need('SteleAwakeningSessions.valid' in intent, 'FATE_SESSION_AUTHORITY: network intent must revalidate stele session')
    need('FateSelection.transition' not in intent or intent.find('SteleAwakeningSessions.valid') < intent.find('FateSelection.transition'), 'FATE_SESSION_AUTHORITY: validate session before transition')
    for name, fields in (('AwakeningOpenPayload', r'BlockPos stelePos,\s*long sessionToken'), ('AwakeningClosePayload', r'long sessionToken')):
        source=_strip_java_comments(read(JAVA+'cultivation/network/'+name+'.java'))
        need(bool(re.search(r'record\s+'+name+r'\s*\(\s*'+fields+r'\s*\)',source)), 'FATE_SESSION_PAYLOAD: '+name)
    opener=_strip_java_comments(read(JAVA+'block/SpiritTestingSteleBlock.java'))
    for token in ('SteleAwakeningSessions.open', 'AwakeningOpenPayload', 'spiritualRoot().isEmpty()', 'FateService.initializeDraft', 'syncToClient'):
        need(token in opener, 'FATE_STELE_OPEN: '+token)
    request=_method_body(service_source, 'request') or ''
    need('if (!authorized)' in request and 'FateSelection.transition' in request, 'FATE_SESSION_AUTHORITY: shared transition must reject unauthorized requests')
    diagnostic=_method_body(service_source, 'diagnosticIntent') or ''
    need('source.hasPermission(2)' in diagnostic, 'FATE_DIAGNOSTIC_PERMISSION: fixture bypass requires permission source')
    network=_strip_java_comments(read(JAVA+'cultivation/network/CultivationPayloads.java'))
    need('diagnosticIntent' not in network, 'FATE_DIAGNOSTIC_NETWORK: network cannot call fixture bypass')
    for p in (root/(JAVA+'cultivation')).rglob('*.java'):
        need(not re.search(r'import\s+(?:net\.minecraft\.client|com\.example\.myvillage\.client)\.', _strip_java_comments(p.read_text())), 'FATE_CLIENT_IMPORT: '+p.name)
    if jar:
        try:
            with zipfile.ZipFile(jar) as z:
                files=[*sorted((root/DATA).glob('*.json')),root/'src/main/resources/data/myvillage/fates/rules.json']
                for p in files:need(z.read(p.relative_to(root/'src/main/resources').as_posix())==p.read_bytes(),'FATE_JAR_RESOURCE: '+p.name)
                for name in ('FateState','FateService','FateRuntime','FateSelection','SteleAwakeningSessions'):
                    need(f'com/example/myvillage/cultivation/fate/{name}.class' in z.namelist(),'FATE_JAR_CLASS: '+name)
                for name in ('AwakeningOpenPayload','AwakeningClosePayload'):
                    need(f'com/example/myvillage/cultivation/network/{name}.class' in z.namelist(), 'FATE_JAR_CLASS: '+name)
                need('com/example/myvillage/client/cultivation/AwakeningScreen.class' in z.namelist(), 'FATE_JAR_CLASS: AwakeningScreen')
        except (OSError,KeyError,zipfile.BadZipFile) as e:errors.append('FATE_JAR: '+str(e))
    return errors

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--root',type=Path,default=ROOT);parser.add_argument('--jar',type=Path);args=parser.parse_args()
    errors=validate(args.root,args.jar)
    for e in errors:print(e)
    print('Innate fates: '+('FAIL' if errors else 'PASS'))
    return bool(errors)
if __name__=='__main__':raise SystemExit(main())
