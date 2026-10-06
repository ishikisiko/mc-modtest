package com.example.myvillage.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DodgeDirectionTest {
    @Test
    void ordinalsAreTheWireOrder() {
        assertEquals(
                List.of("NONE", "FORWARD", "FORWARD_LEFT", "LEFT", "BACK_LEFT", "BACK", "BACK_RIGHT", "RIGHT",
                        "FORWARD_RIGHT"),
                java.util.Arrays.stream(DodgeDirection.values()).map(Enum::name).toList());
    }

    @Test
    void worldYawTableForEveryDirection() {
        // Relative to the view: left is yaw - 90, right yaw + 90, back yaw - 180; NONE is a back step.
        Map<DodgeDirection, Float> offsets = Map.of(
                DodgeDirection.NONE, -180.0F,
                DodgeDirection.FORWARD, 0.0F,
                DodgeDirection.FORWARD_LEFT, -45.0F,
                DodgeDirection.LEFT, -90.0F,
                DodgeDirection.BACK_LEFT, -135.0F,
                DodgeDirection.BACK, -180.0F,
                DodgeDirection.BACK_RIGHT, 135.0F,
                DodgeDirection.RIGHT, 90.0F,
                DodgeDirection.FORWARD_RIGHT, 45.0F);
        assertEquals(DodgeDirection.values().length, offsets.size());
        for (float view : new float[] {0.0F, 30.0F, -170.0F}) {
            for (Map.Entry<DodgeDirection, Float> entry : offsets.entrySet()) {
                assertEquals(view + entry.getValue(), entry.getKey().worldYaw(view), 1.0E-4F,
                        entry.getKey() + " at view yaw " + view);
            }
        }
    }

    @Test
    void leftOfAPlayerFacingSouthIsEast() {
        // Facing +Z (yaw 0), the player's left is +X; Minecraft's forward is (-sin yaw, 0, cos yaw).
        double yaw = Math.toRadians(DodgeDirection.LEFT.worldYaw(0.0F));
        assertEquals(1.0, -Math.sin(yaw), 1.0E-6);
        assertEquals(0.0, Math.cos(yaw), 1.0E-6);
        // A back step from yaw 0 goes to -Z.
        double back = Math.toRadians(DodgeDirection.NONE.worldYaw(0.0F));
        assertEquals(-1.0, Math.cos(back), 1.0E-6);
    }

    @Test
    void fromInputQuantisesWithADeadZoneOfPointThree() {
        assertEquals(DodgeDirection.NONE, DodgeDirection.fromInput(0.0F, 0.0F));
        assertEquals(DodgeDirection.FORWARD, DodgeDirection.fromInput(1.0F, 0.0F));
        assertEquals(DodgeDirection.BACK, DodgeDirection.fromInput(-1.0F, 0.0F));
        assertEquals(DodgeDirection.LEFT, DodgeDirection.fromInput(0.0F, 1.0F));
        assertEquals(DodgeDirection.RIGHT, DodgeDirection.fromInput(0.0F, -1.0F));
        assertEquals(DodgeDirection.FORWARD_LEFT, DodgeDirection.fromInput(1.0F, 1.0F));
        assertEquals(DodgeDirection.FORWARD_RIGHT, DodgeDirection.fromInput(0.31F, -0.31F));
        assertEquals(DodgeDirection.BACK_LEFT, DodgeDirection.fromInput(-1.0F, 1.0F));
        assertEquals(DodgeDirection.BACK_RIGHT, DodgeDirection.fromInput(-0.5F, -0.5F));
        // The dead zone is strict: exactly DEAD_ZONE (and anything inside) reads as no input.
        assertEquals(DodgeDirection.NONE, DodgeDirection.fromInput(0.2F, -0.2F));
        assertEquals(DodgeDirection.NONE, DodgeDirection.fromInput(0.19F, 0.0F));
        assertEquals(DodgeDirection.LEFT, DodgeDirection.fromInput(0.2F, 0.9F));
        // Sneaking scales vanilla's input by 0.3; a held key must still read as its direction.
        assertEquals(DodgeDirection.FORWARD, DodgeDirection.fromInput(0.3F, 0.0F));
        assertEquals(DodgeDirection.FORWARD_RIGHT, DodgeDirection.fromInput(0.3F, -0.3F));
    }
}
