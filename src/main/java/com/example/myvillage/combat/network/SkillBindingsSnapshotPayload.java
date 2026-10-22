package com.example.myvillage.combat.network;

import com.example.myvillage.combat.SkillBindings;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

public record SkillBindingsSnapshotPayload(int entityId,ResourceLocation dimension,SkillBindings bindings) implements CustomPacketPayload {
    public static final Type<SkillBindingsSnapshotPayload> TYPE=new Type<>(ResourceLocation.parse("myvillage:skill_bindings_snapshot"));
    public SkillBindingsSnapshotPayload {if(entityId<0 || dimension==null || dimension.toString().length()>128 || bindings==null)throw new IllegalArgumentException("Invalid binding owner");}
    public static final StreamCodec<RegistryFriendlyByteBuf,SkillBindingsSnapshotPayload> STREAM_CODEC=StreamCodec.of((b,p)->{
        b.writeVarInt(p.entityId);b.writeResourceLocation(p.dimension);p.bindings.slots().forEach(b::writeResourceLocation);
    },b->{
        int entity=b.readVarInt();var dimension=ResourceLocation.parse(b.readUtf(128));var slots=new ArrayList<ResourceLocation>(SkillBindings.SIZE);
        for(int i=0;i<SkillBindings.SIZE;i++)slots.add(ResourceLocation.parse(b.readUtf(128)));
        return new SkillBindingsSnapshotPayload(entity,dimension,new SkillBindings(slots));
    });
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
