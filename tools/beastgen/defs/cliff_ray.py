from ..ridge import build_model as model, extra_outputs, finalize, glow, paint, ray_clips as clips

HITBOX = (1.48, 1.34)


def build_model():
    return model("cliff_ray")
