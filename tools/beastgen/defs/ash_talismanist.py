"""ash_talismanist owned model, material and animation adapter."""
from .. import roster
from ...npcgen import wilderness as humanoid
ID = "myvillage:ash_talismanist"
NAME = "ash_talismanist"
HITBOX = tuple(roster.entry(NAME)["dimensions"][k] for k in ("width","height"))
def build_model(): return humanoid.build_model(NAME)
def paint(t): return humanoid.painter(NAME)(t)
def glow(t): return (35,25,5,255) if t.cube=="talisman_paper" else roster.glow(NAME)(t)
def clips(model,server): return roster.clips(model,server)
def authored_texture(model,fallback): return roster.authored_texture(NAME,model,fallback)
SKIP_CREASE = tuple(c.name for _,c in build_model().cubes())
