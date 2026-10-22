package com.example.myvillage.combat.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Owner-only readout. It carries no intent and cannot authorize a cast. */
public record CombatHotbarPayload(int entityId, ResourceLocation dimension, ResourceLocation weapon,
                                  List<Slot> slots) implements CustomPacketPayload {
    public static final int LOCKED=0, READY=1, COOLDOWN=2, CHARGING=3, RECOVERING=4, BLOCKED=5;
    public static final int MAX_TICKS=12000;
    public static final int SLOT_COUNT=8;
    public static final Type<CombatHotbarPayload> TYPE=new Type<>(ResourceLocation.parse("myvillage:combat_hotbar"));
    public record Slot(Optional<ResourceLocation> technique,int state,boolean power,int remaining,int total,int charge) {
        public Slot {
            Objects.requireNonNull(technique);
            if(technique.map(id->id.toString().length()>128).orElse(false))throw new IllegalArgumentException("Hotbar ID too long");
            if(state<LOCKED || state>BLOCKED || remaining<0 || total<remaining || total>MAX_TICKS || charge<0 || charge>100)
                throw new IllegalArgumentException("Invalid hotbar slot");
        }
    }
    public CombatHotbarPayload {
        if(entityId<0)throw new IllegalArgumentException("Invalid hotbar owner");
        Objects.requireNonNull(dimension);Objects.requireNonNull(weapon);slots=List.copyOf(slots);
        if(dimension.toString().length()>128 || weapon.toString().length()>128)throw new IllegalArgumentException("Hotbar ID too long");
        if(slots.size()!=SLOT_COUNT)throw new IllegalArgumentException("Exactly eight hotbar slots");
    }
    public static final StreamCodec<RegistryFriendlyByteBuf,CombatHotbarPayload> STREAM_CODEC=StreamCodec.of((b,p)->{
        b.writeVarInt(p.entityId);b.writeResourceLocation(p.dimension);b.writeResourceLocation(p.weapon);
        // Fixed cardinality: a malicious length can never allocate a list.
        for(var s:p.slots) {
            b.writeBoolean(s.technique.isPresent());s.technique.ifPresent(b::writeResourceLocation);
            b.writeByte(s.state);b.writeBoolean(s.power);b.writeVarInt(s.remaining);b.writeVarInt(s.total);b.writeByte(s.charge);
        }
    },b->{
        int owner=b.readVarInt();var dimension=b.readResourceLocation();var weapon=b.readResourceLocation();
        List<Slot> slots=new ArrayList<>(SLOT_COUNT);
        for(int i=0;i<SLOT_COUNT;i++)slots.add(new Slot(b.readBoolean()?Optional.of(b.readResourceLocation()):Optional.empty(),
                b.readUnsignedByte(),b.readBoolean(),b.readVarInt(),b.readVarInt(),b.readUnsignedByte()));
        return new CombatHotbarPayload(owner,dimension,weapon,slots);
    });
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
