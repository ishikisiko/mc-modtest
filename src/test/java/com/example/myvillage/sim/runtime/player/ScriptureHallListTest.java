package com.example.myvillage.sim.runtime.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.sim.PlayerMemberView;
import com.example.myvillage.sim.runtime.net.ScriptureHallPayload;
import com.example.myvillage.sim.runtime.player.ScriptureHallList.Def;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

/** The scripture hall's rows from the ledger's borrowable list, and who gets no list at a shelf. */
class ScriptureHallListTest {
    private static final int SECT = 7;

    private static final Map<String, Def> REGISTRY = Map.of(
            "qingfeng_jue", new Def(Component.translatable("technique.myvillage.qingfeng_jue"), 1, "core"),
            "liuyun_bu", new Def(Component.translatable("technique.myvillage.liuyun_bu"), 2, "movement"),
            "tianjian_shi", new Def(Component.translatable("technique.myvillage.tianjian_shi"), 4, "active"));
    private static final Function<String, Optional<Def>> LOOKUP = id -> Optional.ofNullable(REGISTRY.get(id));

    private static Optional<PlayerMemberView> memberOf(int sectId) {
        return Optional.of(new PlayerMemberView("uuid", "Dev", sectId, sectId < 0 ? "" : "青云宗", "outer", 12, -1, "",
                0, List.of(), Map.of(), -1, -1, "mortal", 1, true, 4000, "", 0, -1, -1));
    }

    @Test
    void entriesFollowTheLedgerOrderWithNameGradeAndCategory() {
        List<ScriptureHallPayload.Entry> entries = ScriptureHallList.entries(
                List.of("liuyun_bu", "qingfeng_jue"), LOOKUP, id -> false, Map.of());
        assertEquals(List.of("liuyun_bu", "qingfeng_jue"),
                entries.stream().map(ScriptureHallPayload.Entry::techniqueId).toList());
        ScriptureHallPayload.Entry first = entries.get(0);
        assertEquals(Component.translatable("technique.myvillage.liuyun_bu"), first.name());
        assertEquals(2, first.grade());
        assertEquals("movement", first.category());
        assertFalse(first.borrowed());
        assertEquals(0, first.cost());
    }

    @Test
    void borrowedAndCostComeFromThePredicateAndTheGradeTable() {
        Set<String> borrowed = Set.of("qingfeng_jue");
        List<ScriptureHallPayload.Entry> entries = ScriptureHallList.entries(
                List.of("qingfeng_jue", "liuyun_bu", "tianjian_shi"), LOOKUP, borrowed::contains,
                Map.of("1", 0, "2", 30, "4", -5));
        assertTrue(entries.get(0).borrowed());
        assertFalse(entries.get(1).borrowed());
        assertEquals(0, entries.get(0).cost());
        assertEquals(30, entries.get(1).cost());
        // a negative cost in the rules reads as free; a grade missing from the table too
        assertEquals(0, entries.get(2).cost());
        assertEquals(0, ScriptureHallList.entries(List.of("liuyun_bu"), LOOKUP, id -> false, Map.of("1", 9))
                .get(0).cost());
    }

    @Test
    void unknownIdsAndBadGradesAreSkipped() {
        Function<String, Optional<Def>> lookup = id -> id.equals("odd")
                ? Optional.of(new Def(Component.literal("odd"), 7, "core"))
                : LOOKUP.apply(id);
        List<ScriptureHallPayload.Entry> entries = ScriptureHallList.entries(
                List.of("missing", "qingfeng_jue", "odd", ""), lookup, id -> false, Map.of());
        assertEquals(List.of("qingfeng_jue"), entries.stream().map(ScriptureHallPayload.Entry::techniqueId).toList());
        assertTrue(ScriptureHallList.entries(List.of(), LOOKUP, id -> false, Map.of()).isEmpty());
    }

    @Test
    void entriesAreCappedAndLongCategoriesClipped() {
        List<String> many = new ArrayList<>();
        for (int i = 0; i < ScriptureHallPayload.MAX_ENTRIES + 4; i++) {
            many.add("t" + i);
        }
        Function<String, Optional<Def>> lookup = id -> Optional.of(new Def(Component.literal(id), 1, "c".repeat(40)));
        List<ScriptureHallPayload.Entry> entries = ScriptureHallList.entries(many, lookup, id -> false, Map.of());
        assertEquals(ScriptureHallPayload.MAX_ENTRIES, entries.size());
        assertEquals(ScriptureHallPayload.MAX_WORD, entries.get(0).category().length());
    }

    @Test
    void aMemberOfThisSectHasNoRefusal() {
        assertEquals(Optional.empty(), ScriptureHallList.refusal(memberOf(SECT), SECT, true));
    }

    @Test
    void outsidersAreRefusedByWhoTheyAre() {
        assertEquals(Optional.of(ScriptureHallList.NOT_MEMBER), ScriptureHallList.refusal(Optional.empty(), SECT, true));
        // a former member (record kept, no sect now)
        assertEquals(Optional.of(ScriptureHallList.NOT_MEMBER), ScriptureHallList.refusal(memberOf(-1), SECT, true));
        assertEquals(Optional.of(ScriptureHallList.MEMBER_ELSEWHERE), ScriptureHallList.refusal(memberOf(3), SECT, true));
    }

    @Test
    void anInactiveSectRefusesEveryone() {
        assertEquals(Optional.of(ScriptureHallList.INACTIVE), ScriptureHallList.refusal(memberOf(SECT), SECT, false));
        assertEquals(Optional.of(ScriptureHallList.INACTIVE), ScriptureHallList.refusal(Optional.empty(), SECT, false));
    }

    @Test
    void everyRefusalHasAChatLineInBothLanguages() throws Exception {
        for (String lang : List.of("zh_cn", "en_us")) {
            String path = "/assets/myvillage/lang/" + lang + ".json";
            String text;
            try (var in = ScriptureHallListTest.class.getResourceAsStream(path)) {
                text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
            for (String reason : ScriptureHallList.REFUSALS) {
                assertTrue(text.contains("\"" + ScriptureHall.REFUSED_PREFIX + reason + "\""), lang + " " + reason);
            }
            for (String reason : List.of(ScriptureHallList.NOT_MEMBER, ScriptureHallList.MEMBER_ELSEWHERE,
                    ScriptureHallList.INACTIVE)) {
                assertTrue(text.contains("\"screen.myvillage.scripture_hall.refused." + reason + "\""), lang + " " + reason);
            }
        }
    }
}
