"""Try a separate flat town only after a completed, unsuitable primary search."""
from __future__ import annotations

import copy
from pathlib import Path

from tools.yunshan.auto_sites import select_site, fingerprint
from tools.yunshan.natural_terrain import read_settings, source_core
from tools.yunshan.fit_natural import config_data, plan_fit

ROOT=Path(__file__).resolve().parents[2]


def select_with_fallback(seed,survey_provider,settings=None,*,progress=None):
    settings=copy.deepcopy(settings or read_settings());cache={};reuse=[]
    def survey(window,label,profile):
        key=tuple(window)
        if key in cache:
            data,original=cache[key]
            reuse.append({'requested_label':label,'source_label':original,'window':window,'sha256':fingerprint(data)})
            return data
        data=survey_provider(window,label,profile);cache[key]=(data,label)
        return data
    def primary_progress(report):
        report.update(variant_phase='terraced',selected_variant='terraced')
        if progress:progress(report)
    primary,sampled,result=select_site(seed,survey,settings,progress=primary_progress)
    primary.update(variant_phase='terraced',selected_variant='terraced')
    if primary['selected'] is not None or not settings.get('flat_fallback',{}).get('enabled',False):
        if progress:progress(primary)
        return primary,sampled,result
    if primary['status']!='no_suitable_site_within_budget':
        raise ValueError('Flat fallback requires a completed unsuitable primary search')
    fallback=settings['flat_fallback'];profile=copy.deepcopy(settings)
    profile['dry_height_policy']=fallback['dry_height_policy']
    for key in ('max_expanded_surveys','max_fit_attempts','max_attempts_per_area'):
        profile['automatic'][key]=fallback[key]
    relative=Path(fallback['fit_config'])
    if relative.name!=str(relative):raise ValueError('flat fit config must be a local blueprint filename')
    fit_cfg=config_data(ROOT/'blueprints/yunshan'/relative)
    core=source_core(profile,fit_config=fit_cfg)
    def combined(report):
        report.update(variant_phase='flat_fallback',selected_variant=fallback['variant'],
                      primary=primary,survey_reuse=copy.deepcopy(reuse),
                      trigger='primary_no_suitable_site_within_budget')
        return report
    def fallback_progress(report):
        if progress:progress(combined(report))
    # Candidate labels encode origin/orientation. Equal windows reuse the
    # immutable snapshot; a different window gets its own coordinate label.
    report,sampled,result=select_site(seed,survey,profile,core=core,
        fit_function=lambda data,candidate:plan_fit(data,candidate,fit_cfg),progress=fallback_progress)
    combined(report)
    if progress:progress(report)
    return report,sampled,result
