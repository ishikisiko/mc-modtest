package com.example.myvillage.entity.npc;

import com.example.myvillage.aptitude.AptitudeRules;
import com.example.myvillage.aptitude.AptitudeRuntime;
import com.example.myvillage.combat.art.ArtDefinition;
import com.example.myvillage.combat.definition.CombatStyles;
import com.example.myvillage.combat.runtime.CombatCaster;
import com.example.myvillage.combat.session.CombatSessionManager;
import com.example.myvillage.combat.session.CombatArtActions;
import com.example.myvillage.combat.session.CombatSkillActions;
import com.example.myvillage.cultivation.*;
import com.example.myvillage.cultivation.data.*;
import com.example.myvillage.cultivation.fate.ability.FateAbilityDefinition;
import com.example.myvillage.cultivation.growth.*;
import com.example.myvillage.stats.StatsRules;
import com.example.myvillage.stats.runtime.StatsRuntime;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;

/** 普通测试 NPC 的学习配置；技能动作由正式 CombatSessionManager 推进。 */
public final class NpcSkillRuntime {
    private static final String TAG = "myvillage:npc_skills", CDS = "myvillage:npc_skill_cooldowns";
    private static final Map<NpcEntity, NpcSkillLoadout> CACHE = new WeakHashMap<>();
    private NpcSkillRuntime() { }
    public static NpcSkillLoadout get(NpcEntity npc) {
        return CACHE.computeIfAbsent(npc, n -> n.getPersistentData().contains(TAG)
                ? NpcSkillLoadout.parse(n.getPersistentData().getString(TAG)) : NpcSkillLoadout.EMPTY);
    }
    public static RealmDefinition realm(NpcEntity npc, ResourceLocation id) {
        var registered = npc.registryAccess().registryOrThrow(ModCultivationRegistries.REALMS).get(id);
        if (registered != null) return registered;
        var numerical = StatsRules.bundled().realms().stream().filter(r -> r.id().equals(id.toString())).findFirst().orElse(null);
        if (numerical == null) return null;
        var stages = new ArrayList<RealmStageDefinition>();
        for (int i = 0; i < numerical.stages(); i++) stages.add(new RealmStageDefinition(
                ResourceLocation.parse(id + "_stage_" + i), "cultivation.realm." + id.getPath(), i));
        return new RealmDefinition("cultivation.realm." + id.getPath(), numerical.order() + 1, Integer.MAX_VALUE, stages, Optional.empty());
    }
    public static CultivationProfile profile(NpcEntity npc) {
        var key = StatsRuntime.assignedRealm(npc).orElse(new com.example.myvillage.stats.RealmKey("myvillage:mortal", 1));
        var realmId = ResourceLocation.parse(key.realmId());
        var root = new LinkedHashMap<ResourceLocation, Integer>();
        for (String id : AptitudeRules.get().elements()) root.put(ResourceLocation.parse("myvillage:" + id), AptitudeRuntime.score(npc, id));
        var learned = new LinkedHashMap<ResourceLocation, TechniqueProgress>();
        for (var row : get(npc).learned()) if (ModCultivationRegistries.technique(npc.registryAccess(), ResourceLocation.parse(row.id())).isPresent())
            learned.put(ResourceLocation.parse(row.id()), new TechniqueProgress(row.mastery()));
        return new CultivationProfile(CultivationProfile.CURRENT_SCHEMA_VERSION, realmId,
                realm(npc, realmId).stages().get(key.stage()).id(), 0, 0, NpcCultivationRuntime.current(npc),
                CultivationProfile.DEFAULT_SPIRITUAL_AFFINITY, 0, 0, Optional.of(new SpiritualRoot(root)), learned,
                get(npc).core().isEmpty() ? Optional.empty() : Optional.of(ResourceLocation.parse(get(npc).core())),
                npc.foundationQuality(), npc.goldenCoreQuality());
    }
    public static boolean executable(ResourceLocation id, TechniqueDefinition technique) {
        if (FateAbilityDefinition.find(id).isPresent()) return true;
        if (technique == null) return false;
        return switch (technique.category()) {
            case CORE, BODY, MOVEMENT -> true;
            case ACTIVE -> technique.effects().flatMap(TechniqueEffects::active).filter(a ->
                    ArtDefinition.find(a.skill()).filter(art -> !art.family().equals("legacy")).isPresent()
                            || CombatStyles.bundled().skill(a.skill()).isPresent()).isPresent();
        };
    }
    public static void validate(NpcEntity npc, NpcSkillLoadout state) {
        if (npc.isLedgerAvatar() || com.example.myvillage.item.TestUnitRuntime.relations(npc) == null)
            throw new IllegalArgumentException("技能编辑仅用于取物器生成的普通 NPC");
        if (!state.weapon().isEmpty()) {
            var item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(state.weapon())).orElseThrow(
                    () -> new IllegalArgumentException("武器不存在"));
            if (CombatStyles.bundled().weapon(new ItemStack(item)).isEmpty()) throw new IllegalArgumentException("此物品没有战斗招式");
        }
        for (var row : state.learned()) {
            var id = ResourceLocation.parse(row.id());
            var technique = ModCultivationRegistries.technique(npc.registryAccess(), id).orElse(null);
            var fate = FateAbilityDefinition.find(id).orElse(null);
            if (technique == null && fate == null) throw new IllegalArgumentException("功法或技能不存在：" + id);
            if (fate != null && !fate.granted(com.example.myvillage.cultivation.fate.FateService.state(npc), npc.registryAccess()))
                throw new IllegalArgumentException("尚未拥有此技能要求的天赋：" + fate.name());
            if (row.automatic() && (!executable(id, technique) || technique != null
                    && technique.category() != TechniqueCategory.ACTIVE && technique.category() != TechniqueCategory.MOVEMENT))
                throw new IllegalArgumentException("这门功法没有 NPC 主动施放入口：" + id);
        }
        if (!state.core().isEmpty() && ModCultivationRegistries.technique(npc.registryAccess(), ResourceLocation.parse(state.core()))
                .filter(TechniqueDefinition::isCore).isEmpty()) throw new IllegalArgumentException("运行心法必须选择心法类功法");
    }
    public static void configure(NpcEntity npc, NpcSkillLoadout state) {
        validate(npc, state);
        CombatSessionManager.interruptNpc(npc, com.example.myvillage.combat.session.CombatStopReason.DISALLOWED, true);
        com.example.myvillage.combat.runtime.CombatDodgeService.clear(npc.getUUID());
        clearBody(npc, get(npc));
        CACHE.put(npc, state); npc.getPersistentData().putString(TAG, state.json());
        if (!state.weapon().isEmpty() && !BuiltInRegistries.ITEM.getKey(npc.getMainHandItem().getItem()).toString().equals(state.weapon())) {
            npc.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(state.weapon()))));
            npc.setDropChance(EquipmentSlot.MAINHAND, 0);
        }
        refreshBody(npc);
    }
    private static ResourceLocation modifier(String id) {
        var key = ResourceLocation.parse(id); return ResourceLocation.parse("myvillage:npc_technique/" + key.getNamespace() + "/" + key.getPath());
    }
    private static void clearBody(NpcEntity npc, NpcSkillLoadout state) {
        for (var row : state.learned()) for (var attribute : BuiltInRegistries.ATTRIBUTE.holders().toList()) {
            var value = npc.getAttribute(attribute); if (value != null) value.removeModifier(modifier(row.id()));
        }
    }
    public static void refreshBody(NpcEntity npc) {
        clearBody(npc, get(npc));
        for (var row : get(npc).learned()) {
            var def = ModCultivationRegistries.technique(npc.registryAccess(), ResourceLocation.parse(row.id())).orElse(null);
            if (def == null || !CombatCaster.eligible(npc, def)) continue;
            def.effects().flatMap(TechniqueEffects::body).ifPresent(body -> body.attributes().forEach((id, value) -> {
                var holder = BuiltInRegistries.ATTRIBUTE.getHolder(id).orElse(null);
                var attribute = holder == null ? null : npc.getAttribute(holder);
                if (attribute != null) attribute.addPermanentModifier(new AttributeModifier(modifier(row.id()), value, AttributeModifier.Operation.ADD_VALUE));
            }));
        }
        if (npc.getHealth() > npc.getMaxHealth()) npc.setHealth(npc.getMaxHealth());
    }
    public static boolean poised(NpcEntity npc) {
        return get(npc).learned().stream().map(row -> ModCultivationRegistries.technique(npc.registryAccess(), ResourceLocation.parse(row.id())).orElse(null))
                .filter(Objects::nonNull).filter(def -> CombatCaster.eligible(npc, def))
                .flatMap(def -> def.effects().flatMap(TechniqueEffects::body).stream()).anyMatch(body -> body.staggerResistance() > 0);
    }
    public static int multiplier(NpcEntity npc, ResourceLocation id, TechniqueGrowthRules.Effect effect) {
        return TechniqueGrowth.multiplier(TechniqueGrowthRules.bundled(), id, profile(npc), effect);
    }
    public static void hit(NpcEntity npc, ResourceLocation id) {
        var spec=TechniqueGrowthRules.bundled().find(id).orElse(null);
        award(npc,id,spec==null?TechniqueGrowthRules.bundled().defaultActiveHitGain():spec.hitGain());
    }
    public static void award(NpcEntity npc, ResourceLocation id, long amount) {
        if (!NpcCombatRuntime.canAct(npc)) return;
        var old = get(npc);
        var rows = new ArrayList<NpcSkillLoadout.Learned>();
        for (var row : old.learned()) {
            if (!row.id().equals(id.toString())) { rows.add(row); continue; }
            var progress = new TechniqueProgress(row.mastery());
            var spec = TechniqueGrowthRules.bundled().find(id).orElse(null);
            var next = spec == null ? new TechniqueProgress(row.mastery() + Math.min(amount,Long.MAX_VALUE-row.mastery()))
                    : TechniqueGrowth.gain(spec,progress,amount);
            rows.add(new NpcSkillLoadout.Learned(row.id(),next.masteryPoints(),row.automatic(),row.priority()));
        }
        var next = new NpcSkillLoadout(old.weapon(),old.core(),rows);
        CACHE.put(npc,next);npc.getPersistentData().putString(TAG,next.json());
    }
    public static int recoveryFactor(NpcEntity npc) {
        var profile = profile(npc);
        int base = com.example.myvillage.cultivation.technique.CoreTechniqueFactor.progressBasisPoints(profile,
                id -> ModCultivationRegistries.technique(npc.registryAccess(), id).orElse(null),
                com.example.myvillage.sim.runtime.WorldSimRuntime.data().map(data -> data.rules()));
        int growth = profile.activeCoreTechnique().map(id -> multiplier(npc, id, TechniqueGrowthRules.Effect.RECOVERY)).orElse(TechniqueGrowthRules.UNIT);
        return (int) Math.min(Integer.MAX_VALUE, (long) base * growth / TechniqueGrowthRules.UNIT);
    }
    public static void cooldown(NpcEntity npc, ResourceLocation id, long until) {
        var tag = npc.getPersistentData().getCompound(CDS); tag.putLong(id.toString(), until); npc.getPersistentData().put(CDS, tag);
    }
    public static int cooldown(NpcEntity npc, ResourceLocation id) {
        return (int) Math.clamp(npc.getPersistentData().getCompound(CDS).getLong(id.toString()) - npc.level().getGameTime(), 0, Integer.MAX_VALUE);
    }
    public static boolean charging(NpcEntity npc) { return CombatSessionManager.charging(npc); }
    private static double range(ArtDefinition art) {
        return art.elemental().map(e->e.range()).orElseGet(()->art.lock().map(l->l.range())
                .orElseGet(()->art.phases().stream().mapToDouble(p->p.reach()+p.stepDistance()).max().orElse(0)));
    }
    private static boolean readyOffense(NpcEntity npc,LivingEntity target,NpcSkillLoadout.Learned row) {
        var id=ResourceLocation.parse(row.id());
        var fate=FateAbilityDefinition.find(id).orElse(null);
        if(fate!=null)return npc.distanceToSqr(target)<=fate.radius()*fate.radius()
                &&com.example.myvillage.cultivation.fate.ability.FateAbilityRuntime.available(npc,id)
                &&com.example.myvillage.cultivation.fate.ability.FateAbilityRuntime.cooldownRemaining(npc,id)==0
                &&NpcCultivationRuntime.current(npc)>=com.example.myvillage.cultivation.fate.ability.FateAbilityRuntime.cost(npc,id);
        var def=ModCultivationRegistries.technique(npc.registryAccess(),id).orElse(null);
        if(def==null||def.category()!=TechniqueCategory.ACTIVE||!CombatCaster.eligible(npc,def)||!npc.hasLineOfSight(target))return false;
        var active=def.effects().flatMap(TechniqueEffects::active).orElse(null);if(active==null)return false;
        var art=ArtDefinition.find(active.skill()).orElse(null);
        if(art!=null)return npc.distanceToSqr(target)<=range(art)*range(art)&&CombatArtActions.authorized(npc,art)
                &&CombatArtActions.cooldownRemaining(npc,art)==0&&NpcCultivationRuntime.current(npc)>=CombatArtActions.cost(npc,art);
        var choice=CombatSkillActions.choice(npc,id).orElse(null);
        return choice!=null&&cooldown(npc,id)==0&&npc.distanceToSqr(target)<=choice.skill().range(255)*choice.skill().range(255)
                &&NpcCultivationRuntime.current(npc)>=com.example.myvillage.forging.ForgedEquipment.qiCost(npc.getMainHandItem(),
                    TechniqueGrowth.cost(choice.skill().qiCost(choice.baseCost(),255),multiplier(npc,id,TechniqueGrowthRules.Effect.QI_COST)));
    }
    public static boolean hasReadyOffense(NpcEntity npc,LivingEntity target) {
        return get(npc).automatic().stream().anyMatch(row->readyOffense(npc,target,row));
    }
    public static boolean tryMovement(NpcEntity npc,LivingEntity target) {
        boolean threatened=target instanceof NpcEntity other&&CombatSessionManager.hasAction(other)
                ||target instanceof com.example.myvillage.entity.beast.BeastEntity beast&&beast.isMoveRunning()
                ||npc.getHealth()/npc.getMaxHealth()<=NpcCombatRules.get().retreatFraction();
        if(!threatened)return false;
        for(var row:get(npc).automatic()){
            var id=ResourceLocation.parse(row.id());var def=ModCultivationRegistries.technique(npc.registryAccess(),id).orElse(null);
            if(def!=null&&def.category()==TechniqueCategory.MOVEMENT&&CombatCaster.eligible(npc,def)
                    &&com.example.myvillage.combat.runtime.CombatDodgeService.handleIntent(npc,com.example.myvillage.combat.DodgeDirection.BACK,id))return true;
        }
        return false;
    }
    public static boolean tryAutomatic(NpcEntity npc, LivingEntity target) {
        if (!NpcCombatRuntime.canAct(npc) || target == null || !target.isAlive()
                || !com.example.myvillage.combat.runtime.CombatActors.mayHarm(npc,target)) return false;
        if (CombatSessionManager.busy(npc) || com.example.myvillage.combat.runtime.CombatDodgeService.isDodging(npc)) return true;
        aim(npc,target);
        for (var row : get(npc).automatic()) {
            var id=ResourceLocation.parse(row.id());
            var fate=FateAbilityDefinition.find(id).orElse(null);
            if(fate!=null) {
                if(npc.distanceToSqr(target)>fate.radius()*fate.radius())continue;
                if(com.example.myvillage.cultivation.fate.ability.FateAbilityRuntime.cast(npc,id))return true;
                continue;
            }
            var def=ModCultivationRegistries.technique(npc.registryAccess(),id).orElse(null);
            if(def==null||!CombatCaster.eligible(npc,def))continue;
            if(def.category()==TechniqueCategory.MOVEMENT) {
                boolean threatened=target instanceof NpcEntity other && CombatSessionManager.hasAction(other)
                        || target instanceof com.example.myvillage.entity.beast.BeastEntity beast && beast.isMoveRunning()
                        || npc.getHealth()/npc.getMaxHealth()<=NpcCombatRules.get().retreatFraction();
                if(threatened&&com.example.myvillage.combat.runtime.CombatDodgeService.handleIntent(npc,com.example.myvillage.combat.DodgeDirection.BACK,id))return true;
                continue;
            }
            if(def.category()!=TechniqueCategory.ACTIVE)continue;
            var active=def.effects().flatMap(TechniqueEffects::active).orElse(null);if(active==null)continue;
            var art=ArtDefinition.find(active.skill()).orElse(null);
            if(art!=null) {
                double range=range(art);
                if(npc.distanceToSqr(target)>range*range||!npc.hasLineOfSight(target)
                        ||!com.example.myvillage.combat.session.CombatArtActions.authorized(npc,art)
                        ||com.example.myvillage.combat.session.CombatArtActions.cooldownRemaining(npc,art)>0
                        ||NpcCultivationRuntime.current(npc)<com.example.myvillage.combat.session.CombatArtActions.cost(npc,art))continue;
                if(CombatSessionManager.handleArtIntent(npc,active.skill()))return true;
            } else {
                var choice=com.example.myvillage.combat.session.CombatSkillActions.choice(npc,id).orElse(null);
                if(choice==null||cooldown(npc,id)>0||npc.distanceToSqr(target)>choice.skill().range(255)*choice.skill().range(255)
                        ||!npc.hasLineOfSight(target))continue;
                long required=com.example.myvillage.forging.ForgedEquipment.qiCost(npc.getMainHandItem(),
                        TechniqueGrowth.cost(choice.skill().qiCost(choice.baseCost(),255),multiplier(npc,id,TechniqueGrowthRules.Effect.QI_COST)));
                if(NpcCultivationRuntime.current(npc)<required)continue;
                if(CombatSessionManager.handleChargeIntent(npc,id))return true;
            }
        }
        return false;
    }
    public static void aim(NpcEntity npc, LivingEntity target) {
        var delta=target.getBoundingBox().getCenter().subtract(npc.getEyePosition());
        float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
        npc.setYRot(yaw);npc.setYHeadRot(yaw);npc.setYBodyRot(yaw);
        npc.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.horizontalDistanceSqr()))));
    }
    public static void joined(NpcEntity npc) { if (npc.getPersistentData().contains(TAG)) { validate(npc, get(npc)); refreshBody(npc); } }
    public static void forget(NpcEntity npc) { CACHE.remove(npc); }
}
