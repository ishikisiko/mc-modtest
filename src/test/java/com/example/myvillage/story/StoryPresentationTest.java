package com.example.myvillage.story;

import com.google.gson.*;
import java.io.StringReader;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Executes the same data selectors used by Runtime.open/sync, without a game bootstrap. */
class StoryPresentationTest {
    private final StoryDefinition d=StoryTestData.definition();
    private static final String[] MEDICAL={"accept","commission","inspect_water_sample","inspect_withered_crop","choose_medical","examine_sample","read_record","return_zhou_he","hear_warden","inspect_recut_array","inspect_fresh_cut","inspect_old_array_text","regulate_spring","claim_reward","recognize_contract"};
    private static final String[] ROAD={"accept","commission","inspect_withered_crop","inspect_water_sample","choose_road","read_record","inspect_chest","return_zhou_he","hear_warden","inspect_old_array_text","inspect_fresh_cut","inspect_recut_array","regulate_spring","claim_reward","recognize_contract"};
    private StoryProgress at(String stage,String... evidence) {
        return new StoryProgress(1,d.id(),1,"qianzhen_test","minecraft:overworld",stage,1,List.of(evidence),List.of(),List.of());
    }
    private String lines(StoryProgress p,String role,boolean returned,boolean regulated) {
        return String.join("\n",d.dialogue(p,role,returned,regulated));
    }
    @Test void returnedZhouUsesCompleteReplacementAndConfirmationWithoutGrantingPersonalProgress() {
        for(boolean regulated:new boolean[]{false,true}) {
            var p=at("zhou","transport_record");
            String before=lines(p,"zhou_he",false,regulated),after=lines(p,"zhou_he",true,regulated);
            assertTrue(before.contains("请替我安排回药庐"));
            assertTrue(after.contains("已经回到药庐"));assertTrue(after.contains("没有丢货"));
            assertFalse(after.contains("请替我安排回药庐"));assertFalse(after.contains("不敢独自"));
            var a=d.actions().get("return_zhou_he");
            assertEquals("我安排人送你回药庐",d.actionText(a,p,false,regulated));
            assertTrue(d.actionText(a,p,true,regulated).contains("确认"));
            assertTrue(StoryEngine.offered(d,p,a));
            var plan=StoryTestData.act(d,p,a.id());assertTrue(plan.changed());assertEquals("warden",plan.progress().stage());
            assertEquals("return_zhou_he",plan.effect()); // Same idempotent facade effect, no alternate rescue action.
            assertFalse(plan.progress().journal().getLast().contains("我安排"));
            assertEquals("zhou",p.stage());assertEquals(List.of("transport_record"),p.evidence());
        }
    }
    @Test void returnedZhouStillRequiresTheActualRecordAndDirectsPlayerBackToIt() {
        var p=at("zhou");var text=lines(p,"zhou_he",true,false);
        assertTrue(text.contains("已经被送回药庐"));assertTrue(text.contains("运药账还留在旧驿"));
        assertFalse(text.contains("请替我安排"));
        assertFalse(StoryEngine.offered(d,p,d.actions().get("return_zhou_he")));
        assertEquals("transport_record",d.tracking(p,true,false).target());
        var recorded=StoryTestData.act(d,p,"read_record").progress();
        assertEquals("zhou_he",d.tracking(recorded,true,false).target());
        assertTrue(d.tracking(recorded,true,false).objective().contains("药庐"));
    }
    @Test void regulatedArrayIsVerificationAndNeverCompletesPersonalStepsByItself() {
        var p=at("regulate");
        assertTrue(d.tracking(p,false,false).objective().contains("恢复"));
        assertTrue(d.tracking(p,true,true).objective().contains("核对已经恢复"));
        assertEquals("spring_core",d.tracking(p,true,true).target());
        assertTrue(lines(at("warden"),"warden",true,true).contains("已经恢复旧限额"));
        assertFalse(lines(at("warden"),"warden",true,true).contains("再碰泉心"));
        var plan=StoryTestData.act(d,p,"regulate_spring");assertTrue(plan.changed());assertEquals("regulate",plan.effect());
        assertEquals("report",plan.progress().stage());assertEquals("regulate",p.stage());
        assertFalse(StoryTestData.act(d,StoryProgress.EMPTY,"regulate_spring").changed());
    }
    @Test void trackingFollowsRemainingEvidenceInEitherInvestigationOrder() {
        assertEquals("water_sample",d.tracking(at("samples"),false,false).target());
        assertEquals("withered_crop",d.tracking(at("samples","water_sample"),false,false).target());
        assertEquals("water_sample",d.tracking(at("samples","withered_crop"),false,false).target());
        assertEquals("transport_record",d.tracking(at("road","medicine_chest"),true,true).target());
        assertEquals("medicine_chest",d.tracking(at("road","transport_record"),true,true).target());
        for(boolean regulated:new boolean[]{false,true}) {
            assertEquals("recut_array",d.tracking(at("array","old_array_text"),true,regulated).target());
            assertEquals("fresh_cut",d.tracking(at("array","recut_array","old_array_text"),true,regulated).target());
            assertEquals("old_array_text",d.tracking(at("array","recut_array","fresh_cut"),true,regulated).target());
        }
    }
    @Test void everyReachableRoutePrefixHasAnAvailableTargetAndBoundedDialogueForAllSharedStates() {
        for(var route:new String[][]{MEDICAL,ROAD}) {
            var p=StoryProgress.EMPTY;
            for(int i=0;i<=route.length;i++) {
                for(boolean returned:new boolean[]{false,true})for(boolean regulated:new boolean[]{false,true}) {
                    var target=d.tracking(p,returned,regulated).target();
                    if(StoryDefinition.TARGETS.contains(target)) {
                        var progress=p;
                        assertTrue(d.actions().values().stream().anyMatch(a->a.target().equals(target)&&StoryEngine.offered(d,progress,a)),p.stage()+":"+target);
                    }else assertTrue(target.isEmpty()&&p.completed()||d.roles().containsKey(target),p.stage()+":"+target);
                    for(String role:d.roles().keySet()) {
                        var text=d.dialogue(p,role,returned,regulated);assertFalse(text.isEmpty());assertTrue(text.size()<=6);
                        assertTrue(text.stream().allMatch(line->line.length()<=256));
                    }
                }
                if(i<route.length)p=StoryTestData.act(d,p,route[i]).progress();
            }
        }
    }
    @Test void openingAdaptsSharedFactsAndDoesNotInventAnotherRescue() {
        assertTrue(String.join("",d.openingLines(StoryProgress.EMPTY,false,false)).contains("还滞在旧驿"));
        assertTrue(String.join("",d.openingLines(StoryProgress.EMPTY,true,false)).contains("送回药庐"));
        assertFalse(String.join("",d.openingLines(StoryProgress.EMPTY,true,false)).contains("还滞在旧驿"));
        assertTrue(String.join("",d.openingLines(StoryProgress.EMPTY,true,true)).contains("已将古阵"));
        assertFalse(lines(StoryProgress.EMPTY,"guide",true,true).contains("比往常暗"));
        assertTrue(StoryEngine.offered(d,StoryProgress.EMPTY,d.actions().get("accept")));
    }
    @Test void storyContentAndFirstMatchSelectionReallyComeFromData() throws Exception {
        var root=source();root.getAsJsonObject("opening").getAsJsonArray("lines").set(0,new JsonPrimitive("数据中的开场"));
        stage(root,"zhou").getAsJsonArray("dialogue_variants").get(0).getAsJsonObject().getAsJsonArray("lines").set(0,new JsonPrimitive("数据中的已回药庐对白"));
        stage(root,"samples").getAsJsonArray("tracking").get(0).getAsJsonObject().addProperty("objective","数据中的目标");
        action(root,"return_zhou_he").getAsJsonArray("text_variants").get(0).getAsJsonObject().addProperty("text","数据中的核实选项");
        var changed=parse(root);
        assertEquals("数据中的开场",changed.openingLines(StoryProgress.EMPTY,false,false).getFirst());
        assertEquals("数据中的已回药庐对白",changed.dialogue(at("zhou"),"zhou_he",true,false).getFirst());
        assertEquals("数据中的目标",changed.tracking(at("samples"),false,false).objective());
        assertEquals("数据中的核实选项",changed.actionText(changed.actions().get("return_zhou_he"),at("zhou"),true,false));
    }
    @Test void conditionFactsUseExactBooleansAndConjunctionRatherThanTruthiness() {
        var c=new StoryDefinition.Condition(Map.of("transport_record",true,"medicine_chest",false),false,true);
        assertTrue(c.matches(at("zhou","transport_record"),false,true));
        assertFalse(c.matches(at("zhou"),false,true));
        assertFalse(c.matches(at("zhou","transport_record","medicine_chest"),false,true));
        assertFalse(c.matches(at("zhou","transport_record"),true,true));
        assertFalse(c.matches(at("zhou","transport_record"),false,false));
    }
    @Test void rejectsScriptsUnknownFactsNonBooleansAndDanglingEvidenceInEverySelector() throws Exception {
        for(String invalid:List.of("{\"command\":\"say hi\"}","{\"stage\":\"zhou\"}","{\"returned\":\"true\"}","{\"regulated\":1}","{\"returned\":null}","{\"evidence\":{\"imaginary\":true}}","{\"evidence\":{\"transport_record\":\"false\"}}","{\"evidence\":{}}","{}")) {
            for(int slot=0;slot<4;slot++) {
                var root=source();JsonObject v=switch(slot) {
                    case 0 -> root.getAsJsonObject("opening").getAsJsonArray("variants").get(0).getAsJsonObject();
                    case 1 -> stage(root,"zhou").getAsJsonArray("dialogue_variants").get(0).getAsJsonObject();
                    case 2 -> stage(root,"samples").getAsJsonArray("tracking").get(0).getAsJsonObject();
                    default -> action(root,"return_zhou_he").getAsJsonArray("text_variants").get(0).getAsJsonObject();
                };
                v.add("when",JsonParser.parseString(invalid));assertThrows(RuntimeException.class,()->parse(root),invalid+" slot="+slot);
            }
        }
    }
    @Test void rejectsDanglingVariantTargetsWrongStageTargetsAndUnknownDialogueRoles() throws Exception {
        for(String target:List.of("imaginary","spring_core")) {
            var root=source();stage(root,"samples").getAsJsonArray("tracking").get(0).getAsJsonObject().addProperty("target",target);
            assertThrows(RuntimeException.class,()->parse(root),target);
        }
        var root=source();stage(root,"zhou").getAsJsonArray("dialogue_variants").get(0).getAsJsonObject().addProperty("role","imaginary");
        assertThrows(RuntimeException.class,()->parse(root));
    }
    @Test void rejectsOversizedReplacementsAndASecondActorNameSource() throws Exception {
        var root=source();var lines=stage(root,"zhou").getAsJsonArray("dialogue_variants").get(0).getAsJsonObject().getAsJsonArray("lines");
        while(lines.size()<=6)lines.add("重复");assertThrows(RuntimeException.class,()->parse(root));
        var named=source();named.getAsJsonObject("roles").getAsJsonObject("guide").addProperty("name","覆盖命簿");
        assertThrows(RuntimeException.class,()->parse(named));
    }
    @Test void medicalHintAndEndingExplainTheStoryWithoutImplementationNotes() {
        assertEquals("沈岚已备好对照样，在水釜边比照药性。",d.stages().get("medical").hint());
        assertFalse(d.stages().get("road").hint().contains("交战"));
        assertTrue(d.actions().get("examine_sample").journal().contains("水量未少"));
        assertTrue(d.actions().get("inspect_recut_array").journal().contains("额外取灵超量"));
        var ending=lines(at("final_guide"),"guide",true,true);
        assertTrue(ending.contains("签押我认得"));assertTrue(ending.contains("三十日早已过去"));assertTrue(ending.contains("旧阵仍要护着水脉"));
        var p=StoryTestData.run(Arrays.copyOf(MEDICAL,MEDICAL.length-1));
        assertEquals("final_guide",p.stage());assertEquals(List.of(StoryEngine.RECEIPT),p.receipts());assertFalse(p.completed());
        assertTrue(StoryTestData.act(d,p,"recognize_contract").progress().completed());
    }
    private JsonObject source() throws Exception {return JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/myvillage/story/qianzhen_chapter_1.json"))).getAsJsonObject();}
    private static JsonObject stage(JsonObject root,String id) {return find(root,"stages",id);}
    private static JsonObject action(JsonObject root,String id) {return find(root,"actions",id);}
    private static JsonObject find(JsonObject root,String key,String id) {
        for(var v:root.getAsJsonArray(key))if(v.getAsJsonObject().get("id").getAsString().equals(id))return v.getAsJsonObject();
        throw new AssertionError(id);
    }
    private static StoryDefinition parse(JsonObject root) {return StoryDefinition.parse(new StringReader(root.toString()));}
}
