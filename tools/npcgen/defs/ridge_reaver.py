"""Armed ridge reaver; npcgen owns anatomy, clothing, UV and shared sword retargeting."""
from .. import wilderness
from ...beastgen import roster
ID = "myvillage:ridge_reaver"
ENTITY = "ridge_reaver"
NAME = "ridge_reaver"
LOOK = "default"
HITBOX = (.6,1.9)
EYE_WHITE = "#F8FAFF"
HAIR = ((30,34,40),)*7
IRIS_TOP = "#25201d"
IRIS = "#25201d"
IRIS_LOW = "#25201d"
def build_model(): return wilderness.build_model(NAME)
def painter(model): return wilderness.painter(NAME)
def clips(model): return wilderness.clips(model)
def authored_texture(model,fallback): return roster.authored_texture(NAME,model,fallback)
