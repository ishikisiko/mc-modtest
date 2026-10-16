#!/usr/bin/env python3
"""Mapped casts for the horizontal thin blade; first person and a second real observer."""
import json
from pathlib import Path
import secrets
import sys
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from tools.combat_capture import session as cs,procs
from tools.combat_capture.sword_qi import SwordQiCapture,trial_plan
from tools.combat_capture.sword_qi_observer import observe

def main():
    out=ROOT/'out/preview/sword_qi_blade';out.mkdir(parents=True,exist_ok=True)
    try:
        cs.start(cs.SessionConfig(session_id=secrets.token_hex(6),lock=str(procs.default_lock_path(ROOT))))
        s=cs.Session.attach(ui_check=True);cap=SwordQiCapture(s,out/'first_person');cap.setup()
        for t in trial_plan():
            if t['id'] in {'tap_hit','charged_hit','tap_wide_edge','charged_wide_edge','tap_outside_width','charged_outside_width','tap_high_edge','charged_high_edge','wall','edge_wall'}:
                cap.one(t)
        for t in [{'id':'tap_normal_speed','expect':'hit','tick_rate':20,'hold':.15},
                  {'id':'charged_normal_speed','expect':'hit','tick_rate':20,'hold':1.3}]:cap.one(t)
        if not cap.manifest['passed']:raise RuntimeError('First-person / collision evidence failed')
        observer=observe(s,out/'observer',tick_rate=20,hold_seconds=1.3)
        if not observer.get('server_hit_observed'):raise RuntimeError('Observer cast was not a real hit')
        (out/'summary.json').write_text(json.dumps({'first_person_cases':len(cap.manifest['trials']),'all_passed':True,
                'observer_server_hit':True,'normal_speed_tps':20,'owner_verdict':'not_verified'},indent=2)+'\n')
    finally:cs.stop()
if __name__=='__main__':main()
