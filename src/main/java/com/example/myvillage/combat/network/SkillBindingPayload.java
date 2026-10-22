package com.example.myvillage.combat.network;

import com.example.myvillage.combat.SkillBindings;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** One assignment. Slot SIZE with AUTO is the explicit reset-to-default request. */
public record SkillBindingPayload(int slot,ResourceLocation technique) implements CustomPacketPayload {
    public static final Type<SkillBindingPayload> TYPE=new Type<>(ResourceLocation.parse("myvillage:skill_binding"));
    public SkillBindingPayload {
        if(slot<0 || slot>SkillBindings.SIZE || technique==null || technique.toString().length()>128
                || (slot==SkillBindings.SIZE && !technique.equals(SkillBindings.AUTO))
                || (slot>=2 && slot<SkillBindings.SIZE && technique.equals(SkillBindings.AUTO)))throw new IllegalArgumentException("Invalid binding request");
    }
    public static final StreamCodec<RegistryFriendlyByteBuf,SkillBindingPayload> STREAM_CODEC=StreamCodec.of(
            (b,p)->{b.writeByte(p.slot);b.writeResourceLocation(p.technique);},
            b->new SkillBindingPayload(b.readUnsignedByte(),ResourceLocation.parse(b.readUtf(128))));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
