package com.example.myvillage.compat.waystones;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.cultivation.CultivationService;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import net.blay09.mods.waystones.api.WaystoneTeleportContext;
import net.blay09.mods.waystones.api.requirement.VariableResolver;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Read-only quote variable. The native server teleport independently resolves its own price. */
public final class WaystonesPricingVariable implements VariableResolver {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "waystone_spirit_stone_cost");
    private static Function<Player, Optional<ResourceLocation>> clientRealm = ignored -> Optional.empty();

    public static void setClientRealmReader(Function<Player, Optional<ResourceLocation>> reader) {
        clientRealm = Objects.requireNonNull(reader);
    }

    @Override public ResourceLocation getId() { return ID; }

    @Override public float resolve(WaystoneTeleportContext context) {
        var entity = context.getEntity();
        ResourceLocation realm = entity instanceof ServerPlayer serverPlayer
                ? CultivationService.getProfile(serverPlayer).realmId()
                : entity instanceof Player player ? clientRealm.apply(player).orElse(null) : null;
        var target = context.getTargetWaystone();
        boolean interdimensional = target != null && !entity.level().dimension().equals(target.getDimension());
        double distance = 0;
        if (target != null && !interdimensional) {
            var source = context.getFromWaystone();
            double sourceX = source.map(value -> value.getPos().getX() + .5).orElse(entity.getX());
            double sourceZ = source.map(value -> value.getPos().getZ() + .5).orElse(entity.getZ());
            distance = Math.hypot(target.getPos().getX() + .5 - sourceX, target.getPos().getZ() + .5 - sourceZ);
        }
        return WaystonesPricingConfig.rules().cost(realm, distance, interdimensional);
    }
}
