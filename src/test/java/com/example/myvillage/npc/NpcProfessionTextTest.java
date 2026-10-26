package com.example.myvillage.npc;

import static org.junit.jupiter.api.Assertions.*;
import com.example.myvillage.sim.profession.ProfessionSupplyView;
import com.example.myvillage.sim.profession.ProfessionSupplyView.GardenView;
import com.example.myvillage.sim.profession.ProfessionView;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NpcProfessionTextTest {
    @Test void ChineseOccupationDoesNotTurnTheOldMerchantIntoAnAlchemist() {
        assertEquals("药铺伙计", NpcProfessionText.role("herbalist"));
        assertEquals("药童", NpcProfessionText.role("herb_child"));
        assertEquals("灵植师", NpcProfessionText.role("spirit_grower"));
        assertEquals("炼丹师", NpcProfessionText.role("alchemist"));
        var view = new ProfessionView(7, "alchemist", 3, 0, 22, 1, 81, 4,
                Map.of("myvillage:spirit_leaf", 2), 9);
        var lines = NpcProfessionText.resident(view);
        assertTrue(lines.get(1).contains("采集 0 级（经验 3）"));
        assertTrue(lines.get(1).contains("种植 1 级（经验 22）"));
        assertTrue(lines.get(1).contains("炼丹 4 级（经验 81）"));
        assertTrue(lines.get(2).contains("灵叶×2"));
        assertTrue(lines.get(2).contains("未分类药材 9"));
        assertTrue(lines.get(3).contains("采集、种植是另外的技艺"));
        for (String role : List.of("herb_child", "spirit_grower", "herbalist")) {
            var resident = new ProfessionView(7, role, 3, 0, 22, 1, 81, 4, Map.of(), 0);
            String joined = String.join("", NpcProfessionText.resident(resident));
            assertTrue(joined.contains(NpcProfessionText.role(role)));
            assertFalse(joined.contains("近况未明"));
        }
        var unassigned = new ProfessionView(7, "", 3, 0, 22, 1, 81, 4, Map.of(), 0);
        assertTrue(NpcProfessionText.resident(unassigned).getFirst().contains("尚未任职"));
        assertEquals(lines.get(1), NpcProfessionText.resident(unassigned).get(1));
    }

    @Test void GardenReadbackDistinguishesSeedsGrowthMaturityAndExpertCare() {
        var view = new ProfessionSupplyView("town", Map.of("myvillage:spirit_leaf", 2), 8, List.of(
                new GardenView("empty", "myvillage:spirit_leaf", -1, -1, false),
                new GardenView("growing", "myvillage:spirit_leaf", 90, 120, true),
                new GardenView("mature", "myvillage:spirit_leaf", 80, 100, false)));
        var lines = NpcProfessionText.supply(view, 100);
        assertTrue(lines.get(0).contains("灵叶×2"));
        assertTrue(lines.get(0).contains("未分类药材 8（不可替代丹方原料）"));
        assertTrue(lines.get(1).contains("待播种，须备有同味药材"));
        assertTrue(lines.get(2).contains("还需 20 生活时钟刻"));
        assertTrue(lines.get(2).contains("已专业照料"));
        assertTrue(lines.get(3).contains("已成熟，待收获"));
        assertTrue(lines.get(3).contains("未专业照料"));
        assertEquals("暂无", NpcProfessionText.inventory(Map.of("myvillage:spirit_leaf", 0)));
        assertTrue(NpcProfessionText.supply(new ProfessionSupplyView("town", Map.of(), 0, List.of()), 100)
                .stream().anyMatch(line -> line.contains("尚未配置命簿药圃")));
    }
}
