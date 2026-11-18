package com.example.myvillage.client.housing;

import com.example.myvillage.housing.HousingOffer;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

@Mod(value="yuanxian",dist=Dist.CLIENT)
public final class HousingClient {
    public HousingClient(){HousingOffer.installReceiver(p->{var mc=Minecraft.getInstance();
        if(mc.screen instanceof HousingScreen screen&&screen.site().equals(p.site()))screen.update(p);
        else mc.setScreen(new HousingScreen(p));
    });}
}
