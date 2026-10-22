package com.example.myvillage.combat.network;

import com.example.myvillage.combat.DodgeDirection;
import com.example.myvillage.combat.SkillBindings;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** The server resolves the saved slot. No client-selected skill, cost, target or timing. */
public record BoundSkillIntentPayload(int slot,DodgeDirection direction) implements CustomPacketPayload {
    public static final Type<BoundSkillIntentPayload> TYPE=new Type<>(ResourceLocation.parse("myvillage:bound_skill_intent"));
    public BoundSkillIntentPayload {if(slot<0 || slot>=SkillBindings.SIZE || direction==null)throw new IllegalArgumentException("Invalid bound skill intent");}
    public static final StreamCodec<RegistryFriendlyByteBuf,BoundSkillIntentPayload> STREAM_CODEC=StreamCodec.of(
        (b,p)->{b.writeByte(p.slot);CombatNetworkCodecs.DODGE_DIRECTION.encode(b,p.direction);},
        b->new BoundSkillIntentPayload(b.readUnsignedByte(),CombatNetworkCodecs.DODGE_DIRECTION.decode(b)));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
