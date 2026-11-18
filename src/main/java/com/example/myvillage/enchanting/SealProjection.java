package com.example.myvillage.enchanting;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class SealProjection {
    public static final float MODEL_Y = -.86F;
    public static final List<Slot> SLOTS = List.copyOf(Arrays.asList(new Gson().fromJson(
            EnchantingRules.read("casket", JsonObject.class).get("slots"), Slot[].class)));

    public record Slot(float x, float y, float z, float turn, String role, float socket_x) {}
    public record Hit(int slot, double distance) {}

    private SealProjection() {}

    public static String slotName(int index) {
        if (index < 6) return "右侧第 " + (index + 1) + " 槽";
        if (index >= 10) return "左侧第 " + (index - 9) + " 槽";
        return "背面第 " + ((index - 6) / 2 + 1) + " 组" + (index % 2 == 0 ? "定向槽" : "刻法槽");
    }

    /** The same two-sided projection planes are used by the renderer, crosshair and server. */
    public static Hit pick(Player player, BlockPos origin, Direction front) {
        double reach = Math.min(6, player.blockInteractionRange());
        Vec3 eye = player.getEyePosition();
        Vec3 direction = player.getLookAngle();
        Vec3 center = Vec3.atBottomCenterOf(origin);
        if (eye.distanceToSqr(center) > 14 * 14) return null;
        Vec3 start = local(eye.subtract(center), front);
        Vec3 ray = local(direction, front);
        Hit nearest = null;
        for (int i = 0; i < SLOTS.size(); i++) {
            var slot = SLOTS.get(i);
            double angle = Math.toRadians(slot.turn());
            double sin = Math.sin(angle), cos = Math.cos(angle);
            double slope = sin * ray.x + cos * ray.z;
            if (Math.abs(slope) < 1e-6) continue;
            double t = (sin * (slot.x() - start.x) + cos * (slot.z() - start.z)) / slope;
            if (t < 0 || t > reach || nearest != null && t >= nearest.distance()) continue;
            double dx = start.x + t * ray.x - slot.x(), dz = start.z + t * ray.z - slot.z();
            if (Math.abs(dx * cos - dz * sin) <= .51
                    && Math.abs(start.y + t * ray.y - slot.y() - MODEL_Y) <= .86) nearest = new Hit(i, t);
        }
        if (nearest == null) return null;
        var obstruction = player.level().clip(new ClipContext(eye, eye.add(direction.scale(nearest.distance())),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return obstruction.getType() == HitResult.Type.MISS ? nearest : null;
    }

    private static Vec3 local(Vec3 vec, Direction front) {
        var right = front.getCounterClockWise();
        return new Vec3(vec.x * right.getStepX() + vec.z * right.getStepZ(), vec.y,
                vec.x * front.getStepX() + vec.z * front.getStepZ());
    }
}
