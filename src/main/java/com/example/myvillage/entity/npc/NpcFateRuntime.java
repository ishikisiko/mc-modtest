package com.example.myvillage.entity.npc;

import com.example.myvillage.aptitude.AptitudeRuntime;
import com.example.myvillage.cultivation.fate.FateService;
import com.example.myvillage.sim.SimRng;
import com.example.myvillage.sim.engine.Purpose;
import com.example.myvillage.sim.fate.NpcFatePool;
import com.example.myvillage.sim.fate.NpcFateRoll;
import com.example.myvillage.sim.model.Person;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import java.util.ArrayList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/** 普通实体自行保存出生结果；命簿化身只读取 WorldSim 的结果。 */
public final class NpcFateRuntime {
    private static RegistryAccess registries;
    private static NpcFatePool pool;
    private NpcFateRuntime() { }

    public static NpcFatePool pool(RegistryAccess access) {
        if (registries != access || pool == null) {
            var definitions = new ArrayList<NpcFatePool.Entry>();
            FateService.definitions(access).forEach((id, value) -> definitions.add(new NpcFatePool.Entry(id,
                    value.cost(), value.group(), value.enabled(), value.element().map(Object::toString).orElse(""),
                    value.minimumAptitude(), value.maximumRoots())));
            pool = new NpcFatePool(NpcFatePool.configuredBudget(), definitions);
            registries = access;
        }
        return pool;
    }

    public static void initialize(NpcEntity npc) {
        if (npc.fates != null) return;
        if (npc.isLedgerAvatar()) throw new IllegalStateException("命簿化身不能自行抽取天赋");
        int[] root = Person.ELEMENTS.stream().mapToInt(id -> AptitudeRuntime.score(npc, id)).toArray();
        var uuid = npc.getUUID();
        npc.fates = pool(npc.registryAccess()).roll(root, npc.prodigy(), SimRng.at(
                npc.level().getServer().overworld().getSeed(), 0, uuid.getMostSignificantBits(),
                Purpose.NPC_FATES, uuid.getLeastSignificantBits()));
    }

    public static void project(NpcEntity npc, int personId) {
        if (!npc.isLedgerAvatar() || npc.ledgerPersonId() != personId)
            throw new IllegalArgumentException("NPC 天赋投影归属错误");
        npc.fates = WorldSimRuntime.sim().flatMap(sim -> sim.npcFates(personId)).orElseThrow();
    }

    public static CompoundTag save(NpcFateRoll roll) {
        var tag = new CompoundTag();
        var selected = new ListTag();
        roll.selected().forEach(id -> selected.add(StringTag.valueOf(id)));
        tag.put("selected", selected);
        tag.putInt("budget", roll.budget());
        tag.putInt("spent", roll.spent());
        return tag;
    }

    public static NpcFateRoll load(CompoundTag tag, boolean prodigy) {
        if (!tag.contains("selected", Tag.TAG_LIST) || !tag.contains("budget", Tag.TAG_INT)
                || !tag.contains("spent", Tag.TAG_INT)) throw new IllegalArgumentException("NPC 天赋存档不完整");
        var selected = new ArrayList<String>();
        for (var id : tag.getList("selected", Tag.TAG_STRING)) selected.add(id.getAsString());
        return new NpcFateRoll(selected, tag.getInt("budget"), tag.getInt("spent")).validateIdentity(prodigy);
    }

    public static void clear() { registries = null; pool = null; }
}
