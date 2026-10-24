package com.example.myvillage.story.scene;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QianzhenRoofRepairPlanTest {
    private JsonObject raw;
    private QianzhenRoofRepairPlan plan;
    private QianzhenRoofRepairPlan.Site site;
    private QianzhenRoofRepairPlan.Receipt receipt;
    private FakeWorld world;
    private static final QianzhenRoofRepairPlan.Pos ORIGIN=new QianzhenRoofRepairPlan.Pos(-2048,64,1024);
    @BeforeEach void setup() throws Exception {
        raw=JsonParser.parseString(Files.readString(Path.of("blueprints/qianzhen_roof_repair/sample_pavilion.json"))).getAsJsonObject();
        plan=QianzhenRoofRepairPlan.parse(raw);
        site=new QianzhenRoofRepairPlan.Site("canal_gardens","myvillage:qianzhen/canal_gardens",new QianzhenRoofRepairPlan.Pos(122,0,65),new QianzhenRoofRepairPlan.Pos(48,22,47),"f9c90f21a201cec9da1f05617b2c7a2f2323c78ecc9d453fc18d80331bc4dcb8");
        receipt=receipt("1-f795e60913419e05",site);
        world=new FakeWorld();
        for(var element:raw.getAsJsonArray("guards")) {
            var row=element.getAsJsonObject(); world.cells.put(at(row),row.get("state").getAsString());
        }
        for(var element:raw.getAsJsonArray("targets")) {
            var row=element.getAsJsonObject(); world.cells.put(at(row),row.get("before").getAsString());
        }
    }
    private QianzhenRoofRepairPlan.Receipt receipt(String version,QianzhenRoofRepairPlan.Site s) {
        return new QianzhenRoofRepairPlan.Receipt(true,"qianzhen_chapter_1",version,"minecraft:overworld","minecraft:overworld",ORIGIN,"qianzhen_chapter_1",version,s);
    }
    private QianzhenRoofRepairPlan.Pos at(JsonObject row) {
        var p=row.getAsJsonArray("pos");return ORIGIN.add(site.origin()).add(new QianzhenRoofRepairPlan.Pos(p.get(0).getAsInt(),p.get(1).getAsInt(),p.get(2).getAsInt()));
    }
    @Test void previewHas45RealWorldCoordinatesAndNoWrites() {
        var changes=plan.preview(receipt,world);
        assertEquals(45,changes.size()); assertEquals(0,world.writes);
        assertTrue(changes.stream().allMatch(c->c.before().equals("minecraft:air")&&c.after().startsWith("minecraft:spruce_log[")));
        assertEquals(ORIGIN.add(site.origin()).add(new QianzhenRoofRepairPlan.Pos(33,6,9)),changes.getFirst().pos());
    }
    @Test void bothProvenVersionsAcceptButAuthoredAndUnknownRefuse() {
        assertEquals(45,plan.preview(receipt("1-56da335c3dd1262f",site),world).size());
        for(String version:new String[]{"1-93b7ddbc5c59392e","1-unknown","1-20bf7cbefcc74760"})
            assertTrue(assertThrows(IllegalStateException.class,()->plan.apply(receipt(version,site),world)).getMessage().contains("unsupported"));
        assertEquals(0,world.writes);
    }
    @Test void fullReceiptDimensionAndExactFrozenSiteAreMandatory() {
        var wrongOrigin=new QianzhenRoofRepairPlan.Site(site.id(),site.template(),new QianzhenRoofRepairPlan.Pos(123,0,65),site.size(),site.sha256());
        var wrongSha=new QianzhenRoofRepairPlan.Site(site.id(),site.template(),site.origin(),site.size(),"0".repeat(64));
        var wrongSize=new QianzhenRoofRepairPlan.Site(site.id(),site.template(),site.origin(),new QianzhenRoofRepairPlan.Pos(47,22,47),site.sha256());
        for(var s:new QianzhenRoofRepairPlan.Site[]{wrongOrigin,wrongSha,wrongSize})
            assertThrows(IllegalStateException.class,()->plan.apply(receipt(receipt.version(),s),world));
        for(var r:new QianzhenRoofRepairPlan.Receipt[]{
                new QianzhenRoofRepairPlan.Receipt(false,receipt.id(),receipt.version(),receipt.dimension(),receipt.actualDimension(),ORIGIN,receipt.layoutId(),receipt.layoutVersion(),site),
                new QianzhenRoofRepairPlan.Receipt(true,"other",receipt.version(),receipt.dimension(),receipt.actualDimension(),ORIGIN,receipt.layoutId(),receipt.layoutVersion(),site),
                new QianzhenRoofRepairPlan.Receipt(true,receipt.id(),receipt.version(),receipt.dimension(),"minecraft:the_nether",ORIGIN,receipt.layoutId(),receipt.layoutVersion(),site),
                new QianzhenRoofRepairPlan.Receipt(true,receipt.id(),receipt.version(),receipt.dimension(),receipt.actualDimension(),ORIGIN,receipt.layoutId(),"other",site)})
            assertThrows(IllegalStateException.class,()->plan.apply(r,world));
        assertEquals(0,world.reads);assertEquals(0,world.writes);
    }
    @Test void anyMissingChunkRefusesBeforeAllBlockReads() {
        world.unloaded.add(at(raw.getAsJsonArray("targets").get(44).getAsJsonObject()));
        assertThrows(IllegalStateException.class,()->plan.apply(receipt,world));
        assertEquals(0,world.reads);assertEquals(0,world.writes);
    }
    @Test void everySingleGuardMutationRefusesTheWholeApplyWithZeroWrites() {
        for(var element:raw.getAsJsonArray("guards")) {
            var p=at(element.getAsJsonObject()); String original=world.cells.put(p,"minecraft:diamond_block");
            assertThrows(IllegalStateException.class,()->plan.apply(receipt,world),p.toString());
            world.cells.put(p,original);
        }
        assertEquals(0,world.writes);
    }
    @Test void everyTargetMutationAndBlockEntityRefuseTheWholeApply() {
        for(var element:raw.getAsJsonArray("targets")) {
            var p=at(element.getAsJsonObject()); String original=world.cells.put(p,"minecraft:chest[facing=north,type=single,waterlogged=false]");
            assertThrows(IllegalStateException.class,()->plan.apply(receipt,world));world.cells.put(p,original);
            world.blockEntities.add(p);assertThrows(IllegalStateException.class,()->plan.apply(receipt,world));world.blockEntities.remove(p);
        }
        assertEquals(0,world.writes);
    }
    @Test void applyTwiceIsIdempotentAndKeepsEveryRoofGroundAndFurnitureGuard() {
        Map<QianzhenRoofRepairPlan.Pos,String> before=Map.copyOf(world.cells);
        assertEquals(45,plan.apply(receipt,world));assertEquals(0,plan.apply(receipt,world));
        assertEquals(45,world.writes);assertEquals(0,plan.preview(receipt,world).size());
        for(var element:raw.getAsJsonArray("guards")) {
            var p=at(element.getAsJsonObject());assertEquals(before.get(p),world.cells.get(p));
        }
    }
    @Test void partiallyRepairedSupportsAreAcceptedWithoutRewritingThem() {
        var row=raw.getAsJsonArray("targets").get(5).getAsJsonObject();world.cells.put(at(row),row.get("after").getAsString());
        assertEquals(44,plan.preview(receipt,world).size());assertEquals(44,plan.apply(receipt,world));assertEquals(44,world.writes);
    }
    @Test void changedWorldAfterPreviewIsRecheckedByApply() {
        assertEquals(45,plan.preview(receipt,world).size());
        world.cells.put(at(raw.getAsJsonArray("targets").get(44).getAsJsonObject()),"minecraft:stone");
        assertThrows(IllegalStateException.class,()->plan.apply(receipt,world));assertEquals(0,world.writes);
    }
    @Test void falseAndThrownWritesAfterMutationBothRollBackEveryAttemptedCell() {
        Map<QianzhenRoofRepairPlan.Pos,String> before=Map.copyOf(world.cells);
        for(boolean throwsAfterMutation:new boolean[]{false,true}) {
            world.writes=0;world.failAt=17;world.throwFailure=throwsAfterMutation;
            assertTrue(assertThrows(IllegalStateException.class,()->plan.apply(receipt,world)).getMessage().contains("均已回滚"));
            assertEquals(before,world.cells);assertEquals(45,plan.preview(receipt,world).size());
        }
    }
    @Test void failedRollbackIsReportedAndOriginalFailureDiagnosisIsRetained() {
        world.failAt=17;
        world.rejectRollback=at(raw.getAsJsonArray("targets").get(3).getAsJsonObject());
        var failure=assertThrows(IllegalStateException.class,()->plan.apply(receipt,world));
        assertTrue(failure.getMessage().contains("回滚未完整完成"));
        assertNotNull(failure.getCause());assertEquals(1,failure.getCause().getSuppressed().length);
        assertEquals(44,plan.preview(receipt,world).size());
    }
    @Test void invalidManifestCannotExpandTheReviewedPatch() {
        raw.getAsJsonArray("targets").get(0).getAsJsonObject().addProperty("before","minecraft:stone");
        assertThrows(IllegalStateException.class,()->QianzhenRoofRepairPlan.parse(raw));
    }
    private static final class FakeWorld implements QianzhenRoofRepairPlan.World {
        final Map<QianzhenRoofRepairPlan.Pos,String> cells=new HashMap<>();
        final Set<QianzhenRoofRepairPlan.Pos> unloaded=new HashSet<>(),blockEntities=new HashSet<>();
        int writes,reads,failAt=-1;boolean throwFailure;
        QianzhenRoofRepairPlan.Pos rejectRollback;
        public boolean loaded(QianzhenRoofRepairPlan.Pos p){return !unloaded.contains(p);}
        public boolean matches(QianzhenRoofRepairPlan.Pos p,String state){reads++;return state.equals(cells.get(p));}
        public boolean hasBlockEntity(QianzhenRoofRepairPlan.Pos p){reads++;return blockEntities.contains(p);}
        public boolean write(QianzhenRoofRepairPlan.Pos p,String state){
            if(p.equals(rejectRollback) && state.equals("minecraft:air"))return false;
            writes++;cells.put(p,state);
            if(writes==failAt){if(throwFailure)throw new IllegalStateException("injected write failure after mutation");return false;}
            return true;
        }
    }
}
