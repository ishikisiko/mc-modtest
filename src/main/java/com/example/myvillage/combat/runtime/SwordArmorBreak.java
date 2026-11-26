package com.example.myvillage.combat.runtime;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.*;
import java.util.*;

/** Non-stacking transient armour reduction, cleared on expiry and lifecycle removal. */
public final class SwordArmorBreak {
    public static final ResourceLocation ID=ResourceLocation.parse("yuanxian:sword_armor_break");
    private record Entry(LivingEntity entity,StrongestTimedEffect effects) {}
    private static final Map<UUID,Entry> ACTIVE=new HashMap<>();
    private SwordArmorBreak() {}
    public static boolean apply(LivingEntity target,double fraction,int ticks) {
        if(target.level().isClientSide() || fraction<=0 || ticks<=0
                || com.example.myvillage.cultivation.fate.FateDefenses.immune(target)) return false;
        var armor=target.getAttribute(Attributes.ARMOR);if(armor==null)return false;
        long now=target.getServer().overworld().getGameTime();
        var entry=ACTIVE.computeIfAbsent(target.getUUID(),ignored->new Entry(target,new StrongestTimedEffect()));
        if (!entry.effects().apply(fraction,ticks,now,com.example.myvillage.combat.foundation.ControlRules.get().maximumSources()))
            return false;
        refresh(entry,now);
        return true;
    }
    public static boolean active(LivingEntity target) {
        var entry = ACTIVE.get(target.getUUID());
        return entry != null && entry.entity() == target && entry.effects().strongest(target.getServer().overworld().getGameTime()) > 0;
    }

    public static void tick(MinecraftServer server) {
        long now=server.overworld().getGameTime();
        for(var id:List.copyOf(ACTIVE.keySet())) {
            var e=ACTIVE.get(id);
            if(!e.entity().isAlive() || e.entity().isRemoved() || e.effects().strongest(now)<=0)forget(id);
            else refresh(e,now);
        }
    }
    private static void refresh(Entry entry,long now) {
        var armor=entry.entity().getAttribute(Attributes.ARMOR);if(armor==null)return;
        double amount=-entry.effects().strongest(now);
        var previous=armor.getModifier(ID);
        if(previous==null || previous.amount()!=amount)
            armor.addOrUpdateTransientModifier(new AttributeModifier(ID,amount,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
    public static void forget(UUID id) {
        var e=ACTIVE.remove(id);if(e==null)return;
        var armor=e.entity().getAttribute(Attributes.ARMOR);if(armor!=null)armor.removeModifier(ID);
    }
    public static void clear() {for(var id:List.copyOf(ACTIVE.keySet()))forget(id);}
}
