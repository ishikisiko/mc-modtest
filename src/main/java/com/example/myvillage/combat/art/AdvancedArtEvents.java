package com.example.myvillage.combat.art;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.combat.session.AscendantFields;
import com.example.myvillage.combat.session.AscendantWards;
import com.example.myvillage.combat.session.CombatArtActions;
import com.example.myvillage.combat.session.EarlyRealmEffects;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

@EventBusSubscriber(modid=MyVillageMod.MOD_ID)
public final class AdvancedArtEvents {
    private AdvancedArtEvents() {}
    @SubscribeEvent public static void startup(ServerStartedEvent event) { ArtDefinition.all(); }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void damage(LivingIncomingDamageEvent event) { CombatArtActions.guard(event); }
    @SubscribeEvent public static void projectile(net.neoforged.neoforge.event.tick.EntityTickEvent.Pre event) {
        EarlyRealmEffects.projectile(event);
        AscendantFields.projectile(event);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void projectileImpact(ProjectileImpactEvent event) { AscendantWards.projectileImpact(event); }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void absorb(LivingDamageEvent.Pre event) { AscendantWards.absorb(event); }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if(event.getEntity() instanceof ServerPlayer p) CombatArtActions.syncCooldowns(p);
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if(event.getEntity() instanceof ServerPlayer p) CombatArtActions.syncCooldowns(p);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if(event.getEntity() instanceof ServerPlayer p) CombatArtActions.syncCooldowns(p);
    }
}
