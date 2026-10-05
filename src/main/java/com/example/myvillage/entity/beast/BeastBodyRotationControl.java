package com.example.myvillage.entity.beast;

import net.minecraft.world.entity.ai.control.BodyRotationControl;

/**
 * While a move is shown, body and head follow the beast's yaw exactly (the server turns it toward
 * the target and then holds the locked yaw), so vanilla's lagging body turn and head-led body
 * rotation never pull the strike off its line. Runs on both sides; the client reads the synced move.
 */
final class BeastBodyRotationControl extends BodyRotationControl {
    private final BeastEntity beast;

    BeastBodyRotationControl(BeastEntity beast) {
        super(beast);
        this.beast = beast;
    }

    @Override
    public void clientTick() {
        if (beast.isMoveShown()) {
            float yaw = beast.getYRot();
            beast.setYBodyRot(yaw);
            beast.setYHeadRot(yaw);
            return;
        }
        super.clientTick();
    }
}
