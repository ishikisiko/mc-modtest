package com.example.myvillage.forging;

import com.example.myvillage.combat.definition.CombatTestData;
import com.example.myvillage.combat.session.CombatSkillSession;
import com.example.myvillage.cultivation.fate.FateDefinition;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class OpenMeridiansForgingRefundTest {
    @Test void actualChannelingCalculationIsThePaidReceiptForEachQualityAndCharge() throws Exception {
        var skill = CombatTestData.styles().skill(ResourceLocation.parse("myvillage:jianqi_jue")).orElseThrow();
        var fate = FateDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/data/myvillage/myvillage/innate_fate/open_meridians.json")))).getOrThrow();
        var rules = ForgingRules.get();
        for (int quality = 1; quality <= 4; quality++) for (int elapsed : new int[]{0, 10, 20}) {
            var cast = new CombatSkillSession(skill, skill.id(), 8, 100, quality);
            long nominal = cast.costAt(100 + elapsed);
            long paid = ForgedEquipment.qiCost(nominal, new ForgeMark("channeling", quality), rules);
            assertTrue(paid < nominal);
            cast.release(100 + elapsed, Vec3.ZERO, new Vec3(0, 0, 1), paid,
                    fate.effect("skill_refund_fraction"));
            assertEquals(paid, cast.paidPower());
            assertEquals(paid / 4, cast.claimHitRefund());
            assertEquals(0, cast.claimHitRefund());
            if (quality == 4 && elapsed == 20) {
                assertEquals(12, nominal); assertEquals(9, paid);
            }
        }
    }
}
