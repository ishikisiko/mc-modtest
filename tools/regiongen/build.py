"""Generate the bounded river-region sample without Gradle or Minecraft."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import time

from .assets import ROOT, digest, load_assets
from .planner import plan as make_plan
from .scene import build, validate, export_scene
from buildgen.groups import get_group


def write_json(path, value):
    path.write_text(json.dumps(value,ensure_ascii=False,separators=(',',':'))+'\n')


def render(plan,preview,report,out,base_url):
    template=(ROOT/'tools/regiongen/viewer.html').read_text()
    source=(ROOT/'tools/yunshan/building_viewer.html').read_text()
    shapes=source[source.index('function color('):source.index('function addBox(')]
    for token,value in {'/*DATA*/':json.dumps(preview,ensure_ascii=False,separators=(',',':')),
                        '/*REPORT*/':json.dumps(report,ensure_ascii=False,separators=(',',':')),
                        '/*THREE*/':(ROOT/'tools/web/three.min.js').read_text(),
                        '/*ORBIT*/':(ROOT/'tools/web/OrbitControls.js').read_text(),
                        '/*SHAPES*/':shapes,'/*BASE*/':base_url}.items():
        template=template.replace(token,value)
    (out/'index.html').write_text(template)


def generate(recipe_path, out, seed, base_url='', export=True):
    started=time.monotonic();recipe=json.loads(recipe_path.read_text())
    group=get_group(recipe['group'])
    if group.layout_strategy!='region_recipe':raise ValueError('Wrong group layout')
    assets=load_assets(recipe)
    plan=make_plan(recipe_path,assets,out,seed)
    plan['plan_sha256']=digest(plan);write_json(out/'plan.json',plan)
    volume,preview,expected,signs=build(plan,assets)
    report=validate(plan,volume,expected,preview)
    report['plan_sha256']=plan['plan_sha256']
    write_json(out/'report.json',report)
    if not report['passed']:raise AssertionError('Geometry check failed: '+str(report['failures']))
    if export:report['export']=export_scene(volume,signs,out)
    inputs=[recipe_path,ROOT/'tools/regiongen/java/RegionPlan.java',ROOT/'tools/regiongen/assets.py',
            ROOT/'tools/regiongen/planner.py',ROOT/'tools/regiongen/build.py',ROOT/'tools/regiongen/scene.py',
            ROOT/'tools/regiongen/viewer.html',ROOT/'tools/yunshan/building_viewer.html',
            ROOT/'tools/yunshan/build_functional.py',ROOT/'tools/yunshan/build_district.py',
            *{ROOT/a['public']['source'] for a in assets.values()}]
    report['sources']={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(set(inputs))}
    report['elapsed_seconds']=round(time.monotonic()-started,3)
    write_json(out/'report.json',report);write_json(out/'preview.json',preview)
    render(plan,preview,report,out,base_url)
    print(json.dumps({k:v for k,v in report.items() if k not in ('checks','sources')},ensure_ascii=False))
    return report


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--recipe',type=Path,default=ROOT/'blueprints/regiongen/river_valley_v1.json')
    p.add_argument('--seed',type=int,default=20261024)
    p.add_argument('--out',type=Path,default=ROOT/'reports/regiongen/river-valley-v1')
    p.add_argument('--base-url',default='')
    p.add_argument('--no-export',action='store_true')
    a=p.parse_args();generate(a.recipe.resolve(),a.out.resolve(),a.seed,a.base_url,not a.no_export)


if __name__=='__main__':main()
