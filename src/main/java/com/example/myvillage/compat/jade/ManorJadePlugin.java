package com.example.myvillage.compat.jade;

import com.example.myvillage.client.manor.ManorJadeProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.*;

/** 仅由 Jade 自己发现；主模组与公共构件不引用可选 API。 */
@WailaPlugin
public final class ManorJadePlugin implements IWailaPlugin {
    public static final ResourceLocation NAMES=ResourceLocation.fromNamespaceAndPath("myvillage","manor_names");
    public static final ResourceLocation LOCATION=ResourceLocation.fromNamespaceAndPath("myvillage","manor_location");
    @Override public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(new ManorJadeDataProvider(),Block.class);
    }
    @Override public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(new ManorJadeProvider(),Block.class);
        registration.addConfig(LOCATION,true);
    }
}
