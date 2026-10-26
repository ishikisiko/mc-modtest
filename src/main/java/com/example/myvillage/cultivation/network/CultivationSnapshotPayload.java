package com.example.myvillage.cultivation.network;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.fate.FateState;
import com.example.myvillage.cultivation.crafting.CraftingAptitudes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.Objects;

public record CultivationSnapshotPayload(CultivationProfile profile, FateState fates, int fateBudget,
        CraftingAptitudes crafting, int alchemyQualityCap, int forgingQualityCap, int talismanQualityCap) implements CustomPacketPayload {
    public static final Type<CultivationSnapshotPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "cultivation_snapshot"));
    // Seven fields exceed StreamCodec.composite's arity; keep one explicit symmetric wire codec.
    public static final StreamCodec<RegistryFriendlyByteBuf, CultivationSnapshotPayload> STREAM_CODEC = new StreamCodec<>() {
        public CultivationSnapshotPayload decode(RegistryFriendlyByteBuf b) {
            return new CultivationSnapshotPayload(ByteBufCodecs.fromCodec(CultivationProfile.CODEC).decode(b),
                    ByteBufCodecs.fromCodec(FateState.CODEC).decode(b), b.readVarInt(),
                    ByteBufCodecs.fromCodec(CraftingAptitudes.CODEC).decode(b), b.readVarInt(), b.readVarInt(), b.readVarInt());
        }
        public void encode(RegistryFriendlyByteBuf b, CultivationSnapshotPayload p) {
            ByteBufCodecs.fromCodec(CultivationProfile.CODEC).encode(b, p.profile());
            ByteBufCodecs.fromCodec(FateState.CODEC).encode(b, p.fates()); b.writeVarInt(p.fateBudget());
            ByteBufCodecs.fromCodec(CraftingAptitudes.CODEC).encode(b, p.crafting());
            b.writeVarInt(p.alchemyQualityCap()); b.writeVarInt(p.forgingQualityCap()); b.writeVarInt(p.talismanQualityCap());
        }
    };
    public CultivationSnapshotPayload(CultivationProfile profile, FateState fates, int budget,
            CraftingAptitudes crafting, int alchemy, int forging) {
        this(profile, fates, budget, crafting, alchemy, forging, 0);
    }
    public CultivationSnapshotPayload(CultivationProfile profile) { this(profile, FateState.empty(), 8); }
    public CultivationSnapshotPayload(CultivationProfile profile, FateState fates, int fateBudget) {
        this(profile, fates, fateBudget, CraftingAptitudes.EMPTY, 0, 0, 0);
    }
    public CultivationSnapshotPayload {
        Objects.requireNonNull(profile, "profile"); Objects.requireNonNull(fates, "fates");
        Objects.requireNonNull(crafting, "crafting");
        if (crafting.talismanInitialized() ? talismanQualityCap < 1 || talismanQualityCap > 4 : talismanQualityCap != 0)
            throw new IllegalArgumentException("Invalid talisman quality cap");
        if (fateBudget < 3 || fateBudget > 18) throw new IllegalArgumentException("Invalid fate budget");
        if (crafting.initialized() ? alchemyQualityCap < 1 || alchemyQualityCap > 4 || forgingQualityCap < 1 || forgingQualityCap > 4
                : alchemyQualityCap != 0 || forgingQualityCap != 0)
            throw new IllegalArgumentException("Invalid initialized crafting quality caps");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
