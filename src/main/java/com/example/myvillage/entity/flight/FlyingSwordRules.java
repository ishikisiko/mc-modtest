package com.example.myvillage.entity.flight;

import com.google.gson.Gson;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** One data owner. Rules are frozen for a flight; resource reload applies to the next launch. */
public record FlyingSwordRules(boolean require_awakened, String minimum_realm,
        double cruise_speed, double boost_speed, double vertical_speed, double landing_speed,
        double empty_horizontal_speed, double response, double drag,
        long takeoff_cost, long cruise_cost, long boost_cost, int billing_ticks, long low_qi,
        int takeoff_ticks, int input_timeout_ticks, int empty_steering_ticks) {
    public FlyingSwordRules {
        if (ResourceLocation.tryParse(minimum_realm) == null
                || !positive(cruise_speed, 1) || !positive(boost_speed, 1.2) || boost_speed < cruise_speed
                || !positive(vertical_speed, .5) || !positive(landing_speed, .4)
                || !positive(empty_horizontal_speed, .15) || !positive(response, 1) || !positive(drag, .99)
                || takeoff_cost < 1 || cruise_cost < 1 || boost_cost < cruise_cost
                || billing_ticks < 1 || billing_ticks > 100 || low_qi < boost_cost
                || takeoff_ticks < 1 || input_timeout_ticks < 1 || input_timeout_ticks > 20
                || empty_steering_ticks < 0 || empty_steering_ticks > 200)
            throw new IllegalArgumentException("御剑规则非法");
    }
    private static boolean positive(double value, double max) { return Double.isFinite(value) && value > 0 && value <= max; }
    public static FlyingSwordRules load(ServerPlayer player) {
        var id = ResourceLocation.fromNamespaceAndPath("myvillage", "flying_sword/rules.json");
        try (var reader = player.getServer().getResourceManager().getResource(id).orElseThrow().openAsReader()) {
            return new Gson().fromJson(reader, FlyingSwordRules.class);
        } catch (java.io.IOException e) { throw new IllegalStateException("无法读取御剑规则", e); }
    }
}
