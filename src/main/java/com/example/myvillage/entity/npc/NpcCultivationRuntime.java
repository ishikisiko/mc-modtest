package com.example.myvillage.entity.npc;

import com.example.myvillage.cultivation.qi.QiRegeneration;
import com.example.myvillage.cultivation.qi.SpiritualPowerRuntime;
import com.example.myvillage.sim.PersonView;
import com.example.myvillage.sim.cultivation.SpiritualPowerMath;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.example.myvillage.sim.runtime.WorldSimSavedData;
import com.example.myvillage.stats.CultivationStatsRules;
import com.example.myvillage.stats.RealmKey;
import com.example.myvillage.stats.runtime.StatsRuntime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;

/** 命簿人物只投影身份；普通实体保存自己的身份和灵力。恢复计时不包含离线时间。 */
public final class NpcCultivationRuntime {
    private static final String SAVE = "NpcCultivation";
    private static final Map<UUID, QiRegeneration> TIMERS = new HashMap<>();
    private static final Map<UUID, Integer> RESET_TICKS = new HashMap<>();
    private NpcCultivationRuntime() { }

    /** 仅配置新建的普通测试身体，保持正式命簿与自然出生入口不变。 */
    public static void configureTest(NpcEntity npc, com.example.myvillage.item.TestUnitConfig config) {
        if (npc.isLedgerAvatar() || npc.cultivationInitialized)
            throw new IllegalArgumentException("测试配置仅用于尚未入世的普通 NPC");
        var key = com.example.myvillage.stats.StatsRules.bundled().realmKey(config.realm(), config.stage());
        var values = com.example.myvillage.aptitude.AptitudeValues.generate(npc.level().getServer().overworld().getSeed(),
                npc.getUUID(), com.example.myvillage.stats.StatsRules.bundled().realms().stream()
                        .filter(r -> r.id().equals(key.realmId())).findFirst().orElseThrow().order(), true);
        for (var entry : config.aptitudes().entrySet()) values = values.with(entry.getKey(), entry.getValue());
        npc.setData(com.example.myvillage.cultivation.CultivationAttachments.APTITUDES.get(),
                new com.example.myvillage.aptitude.AptitudeState(true, values, Map.of(), Map.of(), java.util.Optional.empty()));
        if (!config.fates().isEmpty()) {
            var definitions = com.example.myvillage.cultivation.fate.FateService.definitions(npc.registryAccess());
            int spent = 0;
            for (String id : config.fates()) {
                var definition = definitions.get(id);
                if (definition == null || !definition.enabled()) throw new IllegalArgumentException("天赋不存在或已禁用：" + id);
                spent += definition.cost();
            }
            int budget = Math.min(18, Math.max(4, spent + 1));
            npc.setProdigy(spent == budget);
            npc.fates = new com.example.myvillage.sim.fate.NpcFateRoll(config.fates(), budget, spent).validateIdentity(npc.prodigy());
            NpcFateRuntime.restore(npc, "");
        } else NpcFateRuntime.initialize(npc);
        npc.testConfigured = true;
        npc.testPowerCap = config.powerCap();
        npc.cultivationInitialized = true;
        npc.foundationQuality = config.foundation(); npc.goldenCoreQuality = config.golden();
    }

    public static void fillTestPower(NpcEntity npc, long power) {
        long cap = cap(npc);
        if (!npc.testConfigured || power < -1 || power > cap) throw new IllegalArgumentException("起始灵力超过容量");
        npc.spiritualPower = power < 0 ? cap : power;
    }

    public static void project(NpcEntity npc, PersonView person) {
        if (!npc.isLedgerAvatar() || npc.ledgerPersonId() != person.id())
            throw new IllegalArgumentException("NPC cultivation projection has the wrong owner");
        npc.cultivationInitialized = true;
        npc.setProdigy(person.prodigy());
        NpcFateRuntime.project(npc, person.id());
        npc.foundationQuality = person.foundationQuality();
        npc.goldenCoreQuality = person.goldenCoreQuality();
    }

    public static void prepare(NpcEntity npc, RealmKey realm) {
        if (npc.isLedgerAvatar()) {
            var person = WorldSimRuntime.sim().flatMap(s -> s.person(npc.ledgerPersonId())).orElseThrow();
            project(npc, person);
            return;
        }
        var rules = CultivationStatsRules.bundled();
        boolean fresh = !npc.cultivationInitialized;
        if (fresh) {
            npc.cultivationInitialized = true;
            npc.setProdigy(npc.getRandom().nextDouble() < rules.npcProdigyChance());
        }
        NpcFateRuntime.initialize(npc);
        boolean foundation = switch (realm.realmId()) {
            case "myvillage:foundation_establishment", "myvillage:golden_core", "myvillage:nascent_soul", "myvillage:spirit_transformation" -> true;
            default -> false;
        };
        boolean golden = realm.realmId().equals("myvillage:golden_core") || (realm.realmId().equals("myvillage:nascent_soul") || realm.realmId().equals("myvillage:spirit_transformation"));
        if (!foundation) npc.foundationQuality = 0;
        else if (npc.foundationQuality == 0) npc.foundationQuality = quality(npc);
        if (!golden) npc.goldenCoreQuality = 0;
        else if (npc.goldenCoreQuality == 0) npc.goldenCoreQuality = quality(npc);
        long cap = npc.testConfigured && npc.testPowerCap >= 0 ? npc.testPowerCap : com.example.myvillage.cultivation.qi.OpenMeridians.capacity(
                rules.at(realm, npc.foundationQuality, npc.goldenCoreQuality).power(),
                com.example.myvillage.cultivation.fate.FateService.effect(npc,"power_cap_bonus"));
        // 仅首次出生赋予起始灵力。之后改变境界只改变容量，不补满。
        npc.spiritualPower = fresh ? cap : SpiritualPowerMath.clamp(npc.spiritualPower, cap);
    }

    private static int quality(NpcEntity npc) {
        return !npc.prodigy() ? 1 : npc.getRandom().nextDouble() < CultivationStatsRules.bundled().npcUpperQualityChance() ? 3 : 2;
    }

    private static RealmKey realm(NpcEntity npc) {
        return StatsRuntime.assignedRealm(npc).orElse(new RealmKey("myvillage:mortal", 1));
    }

    public static long cap(NpcEntity npc) {
        if (npc.isLedgerAvatar()) return WorldSimRuntime.sim().flatMap(s -> s.person(npc.ledgerPersonId()))
                .map(PersonView::spiritualPowerCap).orElse(0L);
        if (npc.testConfigured && npc.testPowerCap >= 0) return npc.testPowerCap;
        return com.example.myvillage.cultivation.qi.OpenMeridians.capacity(
                CultivationStatsRules.bundled().at(realm(npc), npc.foundationQuality, npc.goldenCoreQuality).power(),
                com.example.myvillage.cultivation.fate.FateService.effect(npc,"power_cap_bonus"));
    }

    public static long current(NpcEntity npc) {
        return npc.isLedgerAvatar() ? WorldSimRuntime.sim().flatMap(s -> s.person(npc.ledgerPersonId()))
                .map(PersonView::spiritualPower).orElse(0L) : npc.spiritualPower;
    }

    public static void joined(NpcEntity npc) {
        if (!npc.isLedgerAvatar()) prepare(npc, realm(npc));
        forget(npc); // 服务器完成规则加载后，首个有效 tick 开始完整恢复延迟。
    }

    private static void reset(NpcEntity npc) {
        TIMERS.put(npc.getUUID(), new QiRegeneration(SpiritualPowerRuntime.rules()));
        RESET_TICKS.put(npc.getUUID(), ((ServerLevel) npc.level()).getServer().getTickCount());
    }

    public static boolean trySpend(NpcEntity npc, long cost) {
        if (cost < 0) throw new IllegalArgumentException("灵力消耗不能为负");
        if (!NpcCombatRuntime.canAct(npc)) return false;
        boolean success;
        if (npc.isLedgerAvatar()) {
            success = WorldSimRuntime.sim().map(s -> s.trySpendSpiritualPower(npc.ledgerPersonId(), cost)).orElse(false);
            if (success && cost > 0) dirty(npc);
        } else {
            var next = SpiritualPowerMath.spend(npc.spiritualPower, cap(npc), cost);
            success = next.isPresent();
            if (success) npc.spiritualPower = next.getAsLong();
        }
        if (success && cost > 0) reset(npc);
        return success;
    }

    public static boolean recover(NpcEntity npc, long amount) {
        if (amount < 0) throw new IllegalArgumentException("灵力恢复不能为负");
        if (!NpcCombatRuntime.canAct(npc)) return false;
        if (npc.isLedgerAvatar()) {
            boolean changed = WorldSimRuntime.sim().map(s -> s.recoverSpiritualPower(npc.ledgerPersonId(), amount)).orElse(false);
            if (changed) dirty(npc);
            return changed;
        }
        long next = SpiritualPowerMath.recover(npc.spiritualPower, cap(npc), amount);
        boolean changed = next != npc.spiritualPower;
        npc.spiritualPower = next;
        return changed;
    }

    public static void tick(NpcEntity npc) {
        if (!TIMERS.containsKey(npc.getUUID())) reset(npc);
        int tick = ((ServerLevel) npc.level()).getServer().getTickCount();
        if (RESET_TICKS.getOrDefault(npc.getUUID(), Integer.MIN_VALUE) == tick) return;
        int factor = 10_000;
        boolean meditating = false;
        if (npc.isLedgerAvatar()) {
            var sim = WorldSimRuntime.sim().orElseThrow();
            factor = sim.spiritualPowerRecoveryFactor(npc.ledgerPersonId());
            meditating = sim.life(npc.ledgerPersonId()).map(l -> l.activity().equals("cultivate")
                    && l.phase().equals("working") && !npc.combatEngaged()).orElse(false);
        }
        long recovered = TIMERS.get(npc.getUUID()).tick(SpiritualPowerRuntime.rules(), factor, meditating, current(npc) >= cap(npc));
        if (recovered > 0) recover(npc, recovered);
    }

    public static int recoveryTicksRemaining(NpcEntity npc) {
        var timer = TIMERS.get(npc.getUUID());
        return timer == null ? -1 : timer.ticksRemaining();
    }
    public static void forget(NpcEntity npc) { TIMERS.remove(npc.getUUID()); RESET_TICKS.remove(npc.getUUID()); }
    public static void clear() { TIMERS.clear(); RESET_TICKS.clear(); NpcFateRuntime.clear(); }

    public static void save(NpcEntity npc, CompoundTag tag) {
        if (!npc.isLedgerAvatar() && npc.cultivationInitialized) {
            CompoundTag value = identity(npc);
            value.putInt("schema", 2);
            value.putLong("spiritual_power", npc.spiritualPower);
            if (npc.testConfigured) {
                value.putBoolean("test_configuration", true);
                value.putLong("test_power_cap", npc.testPowerCap);
            }
            value.putString("fate_runtime", NpcFateRuntime.serialized(npc));
            tag.put(SAVE, value);
        }
        CompoundTag view = identity(npc);
        view.putLong("spiritual_power", current(npc));
        view.putLong("spiritual_power_cap", cap(npc));
        view.putInt("recovery_ticks", recoveryTicksRemaining(npc));
        tag.put("NpcCultivationView", view); // 只读诊断，不从它恢复身份或灵力。
    }

    public static void load(NpcEntity npc, CompoundTag tag) {
        if (npc.isLedgerAvatar() || !tag.contains(SAVE, Tag.TAG_COMPOUND)) return;
        CompoundTag value = tag.getCompound(SAVE);
        if (value.getInt("schema") != 2 || !value.contains("prodigy", Tag.TAG_BYTE)
                || !value.contains("foundation_quality", Tag.TAG_INT) || !value.contains("golden_core_quality", Tag.TAG_INT)
                || !value.contains("fates", Tag.TAG_COMPOUND)
                || !value.contains("fate_runtime", Tag.TAG_STRING)
                || !value.contains("spiritual_power", Tag.TAG_LONG)) throw new IllegalArgumentException("Invalid NPC cultivation data");
        npc.setProdigy(value.getBoolean("prodigy"));
        npc.testConfigured = value.getBoolean("test_configuration");
        npc.testPowerCap = npc.testConfigured ? value.getLong("test_power_cap") : -1;
        if (npc.testPowerCap < -1) throw new IllegalArgumentException("Invalid test power capacity");
        npc.fates = NpcFateRuntime.load(value.getCompound("fates"), npc.prodigy());
        NpcFateRuntime.restore(npc, value.getString("fate_runtime"));
        npc.foundationQuality = value.getInt("foundation_quality");
        npc.goldenCoreQuality = value.getInt("golden_core_quality");
        npc.spiritualPower = value.getLong("spiritual_power");
        RealmKey realm = realm(npc);
        int fq = npc.foundationQuality, gq = npc.goldenCoreQuality;
        boolean foundation = realm.realmId().equals("myvillage:foundation_establishment")
                || realm.realmId().equals("myvillage:golden_core") || (realm.realmId().equals("myvillage:nascent_soul") || realm.realmId().equals("myvillage:spirit_transformation"));
        boolean golden = realm.realmId().equals("myvillage:golden_core") || (realm.realmId().equals("myvillage:nascent_soul") || realm.realmId().equals("myvillage:spirit_transformation"));
        if (!(foundation ? npc.testConfigured ? fq >= 1 && fq <= 4 : validQuality(npc.prodigy(), fq) : fq == 0)
                || !(golden ? npc.testConfigured ? gq >= 1 && gq <= 3 : validQuality(npc.prodigy(), gq) : gq == 0)
                || npc.spiritualPower < 0 || npc.spiritualPower > cap(npc)) throw new IllegalArgumentException("Invalid NPC cultivation state");
        npc.cultivationInitialized = true;
    }
    private static boolean validQuality(boolean prodigy, int quality) { return prodigy ? quality == 2 || quality == 3 : quality == 1; }
    private static CompoundTag identity(NpcEntity npc) {
        CompoundTag value = new CompoundTag();
        value.putBoolean("prodigy", npc.prodigy());
        if (npc.fates != null) value.put("fates", NpcFateRuntime.save(npc.fates));
        value.putInt("foundation_quality", npc.foundationQuality);
        value.putInt("golden_core_quality", npc.goldenCoreQuality);
        return value;
    }
    private static void dirty(NpcEntity npc) { WorldSimSavedData.get(((ServerLevel) npc.level()).getServer().overworld()).setDirty(); }
}
