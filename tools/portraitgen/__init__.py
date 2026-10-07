"""Layered pixel portraits (64x64 busts) for the world ledger's people.

A portrait is composed from parts (face shape, eyes, brows, mouth, hair front and back, robe,
headwear, marks) chosen deterministically from a person's ledger record: gender, realm, rank, age,
five-element root, personality traits, injury and whether they live. `person.assign` picks the
parts; `render.render` paints them with PIL; `sheet` lays out the preview sheets.

The choice function uses a splitmix64 mix of the person id so the Java client can reproduce the
same picks from the same record (see person.mix).
"""
