"""Technique catalogue: one source table feeds the player technique registry and the world-sim ledger.

Sources (checked in, hand-edited): ``catalogue.json`` (one row per technique), ``schools.json``,
``heritages.json`` and ``rules.json`` (the generator's mapping rules). ``generator`` turns them into the
datapack files under ``data/myvillage/myvillage/{technique,school,heritage}/``,
``data/myvillage/world_sim/{techniques,heritages}.json`` and the ``cultivation.{technique,school,heritage}.``
language keys. ``importer`` is the one-off step that seeded ``catalogue.json`` from the ledger's
``techniques.json``. Run ``python3 tools/gen_technique_catalogue.py [--check]``.
"""
