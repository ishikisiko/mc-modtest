"""离线剧情约束生成基础；不接入运行时，也不隐式加载地点插件。"""
from .core import (
    Adjacency, GenerationContext, SceneContract, SemanticPlan, SemanticSpace,
    SiteBuilder, SiteContract, SiteResult, compose_scene, derive_seed,
    generate_sites, load_contract, validate_result,
)

__all__ = [
    'Adjacency', 'GenerationContext', 'SceneContract', 'SemanticPlan', 'SemanticSpace',
    'SiteBuilder', 'SiteContract', 'SiteResult', 'compose_scene', 'derive_seed',
    'generate_sites', 'load_contract', 'validate_result',
]
