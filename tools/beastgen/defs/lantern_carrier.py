from ..ridge import build_model as model, extra_outputs, finalize, glow, paint, lantern_clips as clips

HITBOX = (1.42, 2.05)


def build_model():
    return model("lantern_carrier")
