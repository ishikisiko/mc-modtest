package com.example.myvillage.cultivation.fate;

import com.example.myvillage.cultivation.CultivationAttachments;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** 角色专属的一次性物资、伴生剑与奖励箱判定。 */
public record FateExtras(int weapon, boolean received, Optional<UUID> sword, int form, List<String> victories,
                         Set<String> examinedCaches) {
    public static final FateExtras EMPTY = new FateExtras(-1, false, Optional.empty(), 0, List.of(), Set.of());
    private static final Codec<Set<String>> CACHES = Codec.STRING.listOf().xmap(Set::copyOf, s -> s.stream().sorted().toList());
    public static final Codec<FateExtras> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(-1, 5).optionalFieldOf("weapon",-1).forGetter(FateExtras::weapon),
            Codec.BOOL.optionalFieldOf("received",false).forGetter(FateExtras::received),
            UUIDUtil.CODEC.optionalFieldOf("sword").forGetter(FateExtras::sword),
            Codec.intRange(0, 4).optionalFieldOf("form",0).forGetter(FateExtras::form),
            Codec.STRING.listOf().optionalFieldOf("victories",List.of()).forGetter(FateExtras::victories),
            CACHES.optionalFieldOf("examined_caches",Set.of()).forGetter(FateExtras::examinedCaches)
    ).apply(i,FateExtras::new));

    public FateExtras { victories = List.copyOf(victories); examinedCaches = Set.copyOf(examinedCaches); }
    public static FateExtras of(ServerPlayer player) { return player.getData(CultivationAttachments.FATE_EXTRAS.get()); }
    public FateExtras choose(int choice) {
        return received || weapon == choice ? this : new FateExtras(choice,false,sword,form,victories,examinedCaches);
    }
    public FateExtras redeemed() { return new FateExtras(weapon,true,sword,form,victories,examinedCaches); }
    public FateExtras withSword(UUID id) { return new FateExtras(weapon,received,Optional.of(id),form,victories,examinedCaches); }
    public FateExtras withVictory(int next, String description) {
        if (next <= form) return this;
        var history = new java.util.ArrayList<>(victories);
        history.add(description);
        return new FateExtras(weapon,received,sword,next,history,examinedCaches);
    }
    public FateExtras examined(Set<String> keys) {
        var next = new HashSet<>(examinedCaches); next.addAll(keys);
        return new FateExtras(weapon,received,sword,form,victories,next);
    }
}
