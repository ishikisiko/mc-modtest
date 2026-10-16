package com.example.myvillage.forging;

import com.example.myvillage.cultivation.CultivationAttachments;
import net.minecraft.server.level.ServerPlayer;

public final class ForgingKnowledge {
    private ForgingKnowledge(){}
    public static boolean knows(ServerPlayer p,String id){return p.getData(CultivationAttachments.FORGE_PATTERNS.get()).knows(id);}
    public static boolean learn(ServerPlayer p,String id){
        if(ForgingRules.get().recipe(id).isEmpty()||knows(p,id))return false;
        p.setData(CultivationAttachments.FORGE_PATTERNS.get(),p.getData(CultivationAttachments.FORGE_PATTERNS.get()).learn(id));return true;
    }
}
