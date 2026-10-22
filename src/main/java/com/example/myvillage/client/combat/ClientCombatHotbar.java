package com.example.myvillage.client.combat;

import com.example.myvillage.combat.network.CombatHotbarPayload;
import net.minecraft.client.Minecraft;

/** Presentation interpolation only; the server sends selected techniques, costs and availability. */
final class ClientCombatHotbar {
    private static CombatHotbarPayload latest;
    private static long received;
    private ClientCombatHotbar(){}
    static void receive(CombatHotbarPayload value) {
        var mc=Minecraft.getInstance();
        if(mc.player==null || mc.level==null || value.entityId()!=mc.player.getId()
                || !value.dimension().equals(mc.level.dimension().location()))return;
        latest=value;received=ClientCombatClock.ticks();
    }
    static CombatHotbarPayload latest(){return latest;}
    static int remaining(CombatHotbarPayload.Slot slot) {
        return remaining(slot.remaining(),received,ClientCombatClock.ticks());
    }
    static int remaining(int ticks,long at,long now){return (int)Math.max(0,ticks-Math.max(0,now-at));}
    static void clear(){latest=null;received=0;}
}
