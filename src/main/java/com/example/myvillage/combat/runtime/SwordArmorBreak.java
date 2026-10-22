package com.example.myvillage.combat.runtime;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.*;
import java.util.*;

/** Non-stacking transient armour reduction, cleared on expiry and lifecycle removal. */
public final class SwordArmorBreak {
    public static final ResourceLocation ID=ResourceLocation.parse("myvillage:sword_armor_break");
    private record Entry(LivingEntity entity,long until) {}
    private static final Map<UUID,Entry> ACTIVE=new HashMap<>();
    private SwordArmorBreak() {}
    public static void apply(LivingEntity target,double fraction,int ticks) {
        if(fraction<=0 || ticks<=0)return;
        var armor=target.getAttribute(Attributes.ARMOR);if(armor==null)return;
        var previous=armor.getModifier(ID);
        double amount=previous==null?-fraction:Math.min(previous.amount(),-fraction);
        armor.removeModifier(ID);
        armor.addTransientModifier(new AttributeModifier(ID,amount,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        ACTIVE.put(target.getUUID(),new Entry(target,target.getServer().overworld().getGameTime()+ticks));
    }
    public static void tick(MinecraftServer server) {
        long now=server.overworld().getGameTime();
        for(var id:List.copyOf(ACTIVE.keySet())) {
            var e=ACTIVE.get(id);if(!e.entity().isAlive() || e.entity().isRemoved() || now>=e.until())forget(id);
        }
    }
    public static void forget(UUID id) {
        var e=ACTIVE.remove(id);if(e==null)return;
        var armor=e.entity().getAttribute(Attributes.ARMOR);if(armor!=null)armor.removeModifier(ID);
    }
    public static void clear() {for(var id:List.copyOf(ACTIVE.keySet()))forget(id);}
}
