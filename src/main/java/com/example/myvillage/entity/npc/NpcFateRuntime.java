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
                    value.cost(), value.group(), value.enabled()&&value.npcEligible(), value.element().map(Object::toString).orElse(""),
                    value.minimumAptitude(), value.maximumRoots(),value.effects())));
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
        restore(npc, "");
    }

    public static void project(NpcEntity npc, int personId) {
        if (!npc.isLedgerAvatar() || npc.ledgerPersonId() != personId)
            throw new IllegalArgumentException("NPC 天赋投影归属错误");
        npc.fates = WorldSimRuntime.sim().flatMap(sim -> sim.npcFates(personId)).orElseThrow();
        String saved = WorldSimRuntime.sim().flatMap(sim -> sim.npcFateRuntime(personId)).orElseThrow();
        if (npc.fateState == null || !saved.equals(npc.fateRuntime)) restore(npc, saved);
    }

    public static com.example.myvillage.cultivation.fate.FateState state(NpcEntity npc) {
        if (npc.fateState == null) {
            if (npc.fates == null) return com.example.myvillage.cultivation.fate.FateState.empty();
            restore(npc, npc.fateRuntime);
        }
        return npc.fateState;
    }

    static void restore(NpcEntity npc, String saved) {
        npc.fates.validateRuntime(saved);
        var value = saved.isEmpty() ? com.example.myvillage.cultivation.fate.FateState.empty()
                .choice(npc.fates.selected(), "", npc.fates.selected(), true, 1)
                : com.example.myvillage.cultivation.fate.FateState.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                    com.google.gson.JsonParser.parseString(saved)).getOrThrow();
        if (!value.confirmed() || !value.selected().equals(npc.fates.selected()))
            throw new IllegalArgumentException("NPC 天赋运行状态与出生词条不符");
        npc.fateState = value;
        npc.fateRuntime = saved;
    }

    public static void replace(NpcEntity npc, com.example.myvillage.cultivation.fate.FateState replacement) {
        if (!replacement.confirmed() || !replacement.selected().equals(npc.fates.selected()))
            throw new IllegalArgumentException("NPC 效果不能改写出生天赋");
        if (replacement.equals(state(npc))) return;
        var encoded = com.example.myvillage.cultivation.fate.FateState.CODEC.encodeStart(
                com.mojang.serialization.JsonOps.INSTANCE, replacement).getOrThrow().getAsJsonObject();
        var previous=extras(npc);
        if(!previous.isEmpty())encoded.add("npc_effects",previous);
        commit(npc,replacement,encoded.toString());
    }

    public static com.google.gson.JsonObject extras(NpcEntity npc) {
        if(npc.fateRuntime.isEmpty())return new com.google.gson.JsonObject();
        var saved=com.google.gson.JsonParser.parseString(npc.fateRuntime).getAsJsonObject();
        return saved.has("npc_effects")?saved.getAsJsonObject("npc_effects").deepCopy():new com.google.gson.JsonObject();
    }

    public static void extras(NpcEntity npc,com.google.gson.JsonObject extras) {
        var state=state(npc);
        var encoded=com.example.myvillage.cultivation.fate.FateState.CODEC.encodeStart(
                com.mojang.serialization.JsonOps.INSTANCE,state).getOrThrow().getAsJsonObject();
        encoded.add("npc_effects",extras.deepCopy());
        commit(npc,state,encoded.toString());
    }

    private static void commit(NpcEntity npc,com.example.myvillage.cultivation.fate.FateState replacement,String next) {
        if (npc.isLedgerAvatar()) {
            var sim = WorldSimRuntime.sim().orElseThrow();
            if (!sim.replaceNpcFateRuntime(npc.ledgerPersonId(), npc.combatEpoch(), npc.fateRuntime, next))
                throw new IllegalStateException("NPC 天赋状态提交的身体租约已失效");
            com.example.myvillage.sim.runtime.WorldSimSavedData.get(npc.level().getServer().overworld()).setDirty();
        }
        npc.fateState = replacement;
        npc.fateRuntime = next;
    }

    static String serialized(NpcEntity npc) { return npc.fateRuntime; }

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
