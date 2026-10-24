"""Compile only the dependency-free-of-Minecraft Java planning CLI."""
from __future__ import annotations
import json
from pathlib import Path
import subprocess

from .assets import ROOT


def plan(recipe_path, assets, out, seed):
    out.mkdir(parents=True, exist_ok=True)
    catalog = {key: item['public'] for key, item in assets.items()}
    (out/'catalog.json').write_text(json.dumps(catalog, ensure_ascii=False))
    jars = sorted((Path.home()/'.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson').glob('*/*/gson-*.jar'))
    if not jars:
        raise RuntimeError('Gson jar is required; pass an existing Gradle dependency cache, no Gradle is launched')
    classes = out/'classes'
    classes.mkdir(exist_ok=True)
    subprocess.run(['javac', '-encoding', 'UTF-8', '-cp', str(jars[-1]), '-d', str(classes),
                    str(ROOT/'tools/regiongen/java/RegionPlan.java')], check=True)
    subprocess.run(['java', '-cp', str(classes)+':'+str(jars[-1]),
                    'com.example.myvillage.regiongen.RegionPlan', str(seed), str(recipe_path),
                    str(out/'catalog.json'), str(out/'plan.json')], check=True)
    return json.loads((out/'plan.json').read_text())
