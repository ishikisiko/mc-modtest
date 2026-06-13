"""Building generation pipeline for the medieval_village building library.

Layer stack (each layer maps to a module):

    Style Profile        styles/*.json + style.py
    Building Archetype   archetypes.py
    Scale Tier           archetypes.py (SCALE_TIERS)
    Massing Graph        massing.py
    Facade Grammar       facade.py
    Build Ops            ops.py
    Pass + Protection    passes.py + grid.py (tags/priority/PROTECTED)
    Quality Check        quality.py
    Resource export      export.py (NBT + mcfunction)
"""
