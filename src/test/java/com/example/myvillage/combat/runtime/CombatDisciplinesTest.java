package com.example.myvillage.combat.runtime;

import com.google.gson.JsonParser;
import com.example.myvillage.combat.elemental.ElementalCharge;
import com.example.myvillage.combat.session.CombatSession;
import com.example.myvillage.cultivation.data.HeartLinkRules;
import com.example.myvillage.cultivation.data.HeartMethod.Slot;
import com.example.myvillage.cultivation.data.TechniqueTraits;
import com.example.myvillage.cultivation.data.TechniqueTraits.ContactState;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatDisciplinesTest {
    @Test void aMultiTargetMoveCannotRechargeAfterSpendOrExpiry() {
        var state = new DisciplinePool(); var rules = DisciplineRules.get().sword();
        assertTrue(state.gain(10,0,rules));
        assertFalse(state.gain(10,1,rules));
        assertEquals(1+rules.bonus(),state.consume(1,rules),1e-9);
        assertFalse(state.gain(10,2,rules));
        assertFalse(state.gain(9,3,rules));
        assertFalse(state.gain(10,10000,rules));
        assertTrue(state.gain(11,10001,rules));
    }
    @Test void cappedAndExpiresAtBoundary() {
        var state = new DisciplinePool(); var rules = DisciplineRules.get().sword();
        for (int i=1;i<=20;i++) state.gain(i,i,rules);
        assertEquals(rules.maximum(),state.stacks(20+rules.timeoutTicks()-1));
        assertEquals(0,state.stacks(20+rules.timeoutTicks()));
        assertEquals(1,state.consume(2000,rules));
    }
    @Test void bodyChargeIsBoundedAndSingleUse() {
        var state = new DisciplinePool(); var rules = DisciplineRules.get().body();
        state.gain(1,0,rules); state.gain(2,1,rules);
        assertEquals(rules.maximum(),state.stacks(1));
        assertEquals(1+rules.maximum()*rules.bonus(),state.consume(1,rules),1e-9);
        assertEquals(1,state.consume(1,rules));
    }
    @Test void spearUsesWideFiniteBandAndNoCloseBonus() {
        var r = DisciplineRules.get();
        assertFalse(r.spearTip(r.spearMin()-.01)); assertFalse(r.spearTip(r.spearMax()+.01));
        assertTrue(r.spearTip(r.spearMin())); assertTrue(r.spearTip(r.spearMax()));
        assertFalse(r.spearTip(Double.NaN)); assertFalse(r.spearTip(Double.POSITIVE_INFINITY));
    }
    @Test void invalidTuningCannotStartRuntime() throws Exception {
        try(var in=getClass().getResourceAsStream("/data/yuanxian/combat/disciplines.json")) {
            String text=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            var root=JsonParser.parseString(text).getAsJsonObject();
            root.getAsJsonObject("sword").addProperty("maximum",1.5);
            assertThrows(IllegalArgumentException.class,()->DisciplineRules.parse(root));
            root.getAsJsonObject("sword").addProperty("maximum",3);
            root.getAsJsonObject("spear").addProperty("minimum_distance",8);
            assertThrows(IllegalArgumentException.class,()->DisciplineRules.parse(root));
        }
    }

    @Test void taggedArtsLoadTheirActualContactRules() {
        var sidestep = TechniqueTraits.contactRules(ResourceLocation.parse("yuanxian:cuobu_zhan"));
        assertEquals(java.util.List.of(
                new TechniqueTraits.ContactRule(TechniqueTraits.ContactState.SWORD_INTENT, TechniqueTraits.ContactRole.GAIN),
                new TechniqueTraits.ContactRule(TechniqueTraits.ContactState.PURSUIT, TechniqueTraits.ContactRole.SPEND)), sidestep);
        var stamp = TechniqueTraits.contactRules(ResourceLocation.parse("yuanxian:zhenyue_yin"));
        assertEquals(TechniqueTraits.ContactState.EARTH_UNBALANCE, stamp.getFirst().state());
        assertEquals(TechniqueTraits.ContactRole.SPEND, stamp.getFirst().role());
    }

    @Test void heartSpendsOneLayerWithoutRefreshingTheRemainder() {
        var sword = new DisciplinePool();
        var rules = DisciplineRules.get().sword();
        assertTrue(sword.gain(1, 0, rules));
        assertTrue(sword.gain(2, 1, rules));
        assertTrue(sword.consumeOne(2));
        assertEquals(1, sword.stacks(2));
        assertFalse(sword.consumeOne(1 + rules.timeoutTicks()));

        var fire = new ElementalCharge();
        fire.gain(0, 3, 50);
        fire.gain(1, 3, 50);
        assertTrue(fire.consumeOne(2));
        assertEquals(1, fire.value(50));
        assertFalse(fire.consumeOne(51));
    }

    @Test void oneHeartContactTracksAttackAndSpiritSeparately() {
        var contact = new CombatSession.HeartContact(ResourceLocation.parse("yuanxian:liufeng_zhan"));
        assertTrue(contact.ready(Slot.ATTACK));
        assertTrue(contact.ready(Slot.SPIRIT));
        contact.begin();
        assertFalse(contact.ready(Slot.ATTACK));
        assertFalse(contact.ready(Slot.SPIRIT));
        contact.end();
        assertTrue(contact.ready(Slot.ATTACK));
        contact.use(Slot.ATTACK);
        assertFalse(contact.ready(Slot.ATTACK));
        assertTrue(contact.ready(Slot.SPIRIT));
        contact.use(Slot.SPIRIT);
        assertFalse(contact.ready(Slot.SPIRIT));
    }

    @Test void attackAndSpiritCanSettleWithOnePaidFistCharge() {
        var pool = new DisciplinePool();
        pool.gain(0, DisciplineRules.get().body());
        var recovered = new AtomicInteger();
        var contact = new CombatSession.HeartContact(null);
        var attack = new HeartCombatLinks.Effect(1.2, ContactState.FIST_FORCE, ignored -> pool.consumeOne(1));
        var spirit = new HeartCombatLinks.Effect(1, ContactState.FIST_FORCE, paid -> {
            assertTrue(paid);
            recovered.addAndGet(2);
            return true;
        });
        var hit = new HeartCombatLinks.Hit(contact, attack, spirit);
        assertEquals(1.2, hit.multiplier());
        hit.accepted();
        hit.accepted();
        assertEquals(0, pool.stacks(1));
        assertEquals(2, recovered.get());
        assertFalse(contact.ready(Slot.ATTACK));
        assertFalse(contact.ready(Slot.SPIRIT));
    }

    @Test void rejectedAttackDoesNotGiveFreeRecoveryOrReserveSpirit() {
        var pool = new DisciplinePool();
        pool.gain(0, DisciplineRules.get().body());
        var contact = new CombatSession.HeartContact(null);
        var attack = new HeartCombatLinks.Effect(1.2, ContactState.FIST_FORCE, ignored -> false);
        var spirit = new HeartCombatLinks.Effect(1, ContactState.FIST_FORCE, paid -> {
            assertFalse(paid);
            return pool.consumeOne(1);
        });
        new HeartCombatLinks.Hit(contact, attack, spirit).accepted();
        assertEquals(0, pool.stacks(1));
        assertTrue(contact.ready(Slot.ATTACK));
        assertFalse(contact.ready(Slot.SPIRIT));
    }

    @Test void passiveTypesUseTheirAuthoredSlotsAndStateTags() {
        var links = TechniqueTraits.heartLinks();
        assertEquals(12, links.passive().size());
        var duration = links.passive().get(ResourceLocation.parse("yuanxian:dayan_jue"));
        assertEquals(new HeartLinkRules.Passive(Slot.ATTACK, HeartLinkRules.PassiveUse.DURATION,
                java.util.List.of(ContactState.PURSUIT), 25), duration);
        var shield = ResourceLocation.parse("yuanxian:tai_su_gui_xi_jing");
        assertEquals(HeartLinkRules.PassiveUse.ABSORPTION, links.passive().get(shield).use());
        assertEquals("shield", TechniqueTraits.of(shield).getFirst().id());
        assertEquals("常驻", TechniqueTraits.of(shield).getFirst().role());
    }

    @Test void heartTrackSharesRulesWithDisplayedFeatures() {
        var links = TechniqueTraits.heartLinks();
        assertEquals(80, links.attack().size());
        assertEquals(8, links.defense().size());
        assertEquals(8, links.spirit().size());
        var first = ResourceLocation.parse("yuanxian:mofeng_pian");
        var last = ResourceLocation.parse("yuanxian:tai_xu_guan_jian_pian");
        assertEquals(HeartLinkRules.Route.SWORD, links.attack().get(first).route());
        assertEquals(HeartLinkRules.AttackUse.GRANT, links.attack().get(last).use());
        assertTrue(links.attack().get(last).cooldown() < links.attack().get(first).cooldown());
        assertEquals("sword_intent", TechniqueTraits.of(first).getFirst().id());
        var use = ResourceLocation.parse("yuanxian:hanfeng_zhenjie");
        assertEquals(TechniqueTraits.ContactState.PURSUIT, links.attack().get(use).state());
        assertEquals("消耗", TechniqueTraits.of(use).getFirst().role());
    }
}
