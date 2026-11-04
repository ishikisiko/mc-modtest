package com.example.myvillage.forging;

import com.example.myvillage.cultivation.CultivationAttachments;
import net.minecraft.server.level.ServerPlayer;

/** Sole server-owned writer for craft experience. Inventory identity/progress is committed by the caller first. */
public final class ForgingSkill {
    private ForgingSkill(){}
    public static int experience(ServerPlayer player){return player.getData(CultivationAttachments.FORGING_SKILL.get());}
    public static int level(ServerPlayer player){return FormingMath.level(experience(player),FoundryRules.get().skill());}
    public static void award(ServerPlayer player,int previous,int completed,WorkshopRules.Craft craft){
        if(player.isCreative()||player.isSpectator()||completed<=previous)return;
        var rules=FoundryRules.get().skill();int gained=FormingMath.experience(previous,completed,craft.grade(),level(player),rules);
        player.setData(CultivationAttachments.FORGING_SKILL.get(),(int)Math.min(rules.maximum_experience(),(long)experience(player)+gained));
    }
}
