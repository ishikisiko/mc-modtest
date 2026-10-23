package com.example.myvillage.story;

import com.example.myvillage.sim.data.SimJson;
import com.google.gson.*;
import java.io.Reader;
import java.util.*;

/** Closed, startup-only story vocabulary. Unknown fields and duplicate JSON keys fail the load. */
public record StoryDefinition(String id,int version,String title,int range,int sessionTicks,
        Map<String,Role> roles,Opening opening,Map<String,Stage> stages,Map<String,Action> actions,List<Reward> rewards) {
    public static final String RESOURCE="story/qianzhen_chapter_1.json";
    public static final Set<String> TARGETS=Set.of("water_sample","withered_crop","sample_examination","medicine_chest","transport_record","recut_array","fresh_cut","old_array_text","spring_core");
    public record Role(List<String> greeting) {}
    /** A conjunction of bounded facts, never an expression or an executable instruction. */
    public record Condition(Map<String,Boolean> evidence,Boolean returned,Boolean regulated) {
        public boolean matches(StoryProgress p,boolean returned,boolean regulated) {
            return (this.returned==null||this.returned==returned)&&(this.regulated==null||this.regulated==regulated)
                    &&evidence.entrySet().stream().allMatch(e->p.evidence().contains(e.getKey())==e.getValue());
        }
    }
    public record LinesVariant(Condition when,List<String> lines) {}
    public record DialogueVariant(String role,Condition when,List<String> lines) {}
    public record TextVariant(Condition when,String text) {}
    public record Tracking(String objective,String hint,String target) {}
    public record TrackingVariant(Condition when,Tracking value) {}
    public record Opening(String learnText,List<String> lines,List<LinesVariant> variants) {}
    public record Stage(String id,String objective,String hint,String target,String autoNext,List<String> autoRequires,
            Map<String,List<String>> dialogue,List<DialogueVariant> dialogueVariants,List<TrackingVariant> tracking) {}
    public record Action(String id,List<String> stages,String kind,String role,String target,List<String> requires,String evidence,String next,
            String text,String journal,String effect,List<TextVariant> textVariants) {}
    public record Reward(String item,int count) {}
    public static StoryDefinition parse(Reader reader) {
        var root=SimJson.parseStrict(RESOURCE,reader);
        fields(root,"schema","id","version","title","range","session_ticks","roles","opening","stages","actions","rewards");
        if(integer(root,"schema",1,1)!=1)throw bad("schema");
        Map<String,Role> roles=new LinkedHashMap<>();
        var roleObject=root.getAsJsonObject("roles");
        if(!roleObject.keySet().equals(Set.of("guide","pharmacist","zhou_he","warden")))throw bad("roles");
        for(var e:roleObject.entrySet()) {
            var v=e.getValue().getAsJsonObject();fields(v,"greeting");
            roles.put(e.getKey(),new Role(strings(v,"greeting",6,256)));
        }
        var o=root.getAsJsonObject("opening");fields(o,"learn_text","lines","variants");
        var openingVariants=new ArrayList<LinesVariant>();
        for(var element:array(o,"variants",8)) {
            var v=element.getAsJsonObject();fields(v,"when","lines");
            openingVariants.add(new LinesVariant(condition(v),strings(v,"lines",6,256)));
        }
        var opening=new Opening(string(o,"learn_text",96),strings(o,"lines",6,256),List.copyOf(openingVariants));
        Map<String,Stage> stages=new LinkedHashMap<>();
        for(var element:array(root,"stages",32)) {
            var s=element.getAsJsonObject();fields(s,"id","objective","hint","target","auto_next","auto_requires","dialogue","dialogue_variants","tracking");
            String id=identifier(s,"id");var dialogue=new LinkedHashMap<String,List<String>>();
            for(var e:s.getAsJsonObject("dialogue").entrySet()) {
                if(!roles.containsKey(e.getKey()))throw bad("dialogue role");
                dialogue.put(e.getKey(),strings(e.getValue().getAsJsonArray(),6,256));
            }
            var variants=new ArrayList<DialogueVariant>();
            for(var value:array(s,"dialogue_variants",16)) {
                var v=value.getAsJsonObject();fields(v,"role","when","lines");
                variants.add(new DialogueVariant(string(v,"role",64),condition(v),strings(v,"lines",6,256)));
            }
            var tracking=new ArrayList<TrackingVariant>();
            for(var value:array(s,"tracking",16)) {
                var v=value.getAsJsonObject();fields(v,"when","objective","hint","target");
                tracking.add(new TrackingVariant(condition(v),new Tracking(string(v,"objective",256),string(v,"hint",256),string(v,"target",64))));
            }
            var stage=new Stage(id,string(s,"objective",256),string(s,"hint",256),string(s,"target",64),string(s,"auto_next",64),ids(s,"auto_requires"),Map.copyOf(dialogue),List.copyOf(variants),List.copyOf(tracking));
            if(stages.putIfAbsent(id,stage)!=null)throw bad("duplicate stage "+id);
        }
        Map<String,Action> actions=new LinkedHashMap<>();
        for(var element:array(root,"actions",64)) {
            var a=element.getAsJsonObject();fields(a,"id","stages","kind","role","target","requires","evidence","next","text","journal","effect","text_variants");
            String id=identifier(a,"id");
            var variants=new ArrayList<TextVariant>();
            for(var value:array(a,"text_variants",8)) {
                var v=value.getAsJsonObject();fields(v,"when","text");
                variants.add(new TextVariant(condition(v),string(v,"text",96)));
            }
            var action=new Action(id,ids(a,"stages"),string(a,"kind",16),string(a,"role",64),string(a,"target",64),ids(a,"requires"),string(a,"evidence",64),string(a,"next",64),string(a,"text",96),string(a,"journal",256),string(a,"effect",32),List.copyOf(variants));
            if(actions.putIfAbsent(id,action)!=null)throw bad("duplicate action "+id);
        }
        var rewards=new ArrayList<Reward>();var rewardIds=new HashSet<String>();
        for(var element:array(root,"rewards",8)) {
            var r=element.getAsJsonObject();fields(r,"item","count");String item=string(r,"item",128);
            if(!item.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||!rewardIds.add(item))throw bad("reward item");
            rewards.add(new Reward(item,integer(r,"count",1,64)));
        }
        var d=new StoryDefinition(identifier(root,"id"),integer(root,"version",1,10000),string(root,"title",64),integer(root,"range",2,6),integer(root,"session_ticks",20,2400),Collections.unmodifiableMap(roles),opening,Collections.unmodifiableMap(stages),Collections.unmodifiableMap(actions),List.copyOf(rewards));
        d.validate();return d;
    }
    /** Ordered first-match replacements; no appending contradictory narrative fragments. */
    public List<String> openingLines(StoryProgress p,boolean returned,boolean regulated) {
        for(var v:opening.variants)if(v.when.matches(p,returned,regulated))return v.lines;
        return opening.lines;
    }
    public List<String> dialogue(StoryProgress p,String role,boolean returned,boolean regulated) {
        var s=stages.get(p.stage());if(s==null)return List.of("这段行迹暂时无法续写，请保留现有记录。");
        for(var v:s.dialogueVariants)if(v.role.equals(role)&&v.when.matches(p,returned,regulated))return v.lines;
        return s.dialogue.getOrDefault(role,roles.get(role).greeting);
    }
    public Tracking tracking(StoryProgress p,boolean returned,boolean regulated) {
        var s=stages.get(p.stage());if(s==null)return new Tracking("行迹版本暂不兼容，请保留现有记录","","");
        for(var v:s.tracking)if(v.when.matches(p,returned,regulated))return v.value;
        return new Tracking(s.objective,s.hint,s.target);
    }
    public String actionText(Action a,StoryProgress p,boolean returned,boolean regulated) {
        for(var v:a.textVariants)if(v.when.matches(p,returned,regulated))return v.text;
        return a.text;
    }
    private void validate() {
        if(!id.equals(StoryProgress.CHAPTER)||!stages.containsKey("new")||!stages.containsKey("complete")||rewards.isEmpty())throw bad("chapter terminals/rewards");
        Set<String> evidence=new HashSet<>();
        for(var a:actions.values())if(!a.evidence.isEmpty()&&(!StoryProgress.id(a.evidence)||!evidence.add(a.evidence)))throw bad("duplicate evidence");
        for(var a:actions.values()) {
            if(!Set.of("talk","investigate","claim").contains(a.kind)||!Set.of("","return_zhou_he","regulate").contains(a.effect)
                    ||a.stages.isEmpty()||!stages.keySet().containsAll(a.stages)||!a.next.isEmpty()&&!stages.containsKey(a.next)||!evidence.containsAll(a.requires))throw bad("action references "+a.id);
            if(a.kind.equals("investigate") ? !a.role.isEmpty()||!TARGETS.contains(a.target) : !roles.containsKey(a.role)||!a.target.isEmpty())throw bad("action binding "+a.id);
            if(a.kind.equals("claim")&&(!a.id.equals("claim_reward")||!a.role.equals("pharmacist")||!a.effect.isEmpty()))throw bad("reward receipt");
            if(a.effect.equals("return_zhou_he")&&(!a.kind.equals("talk")||!a.role.equals("zhou_he")))throw bad("rescue binding");
            if(a.effect.equals("regulate")&&(!a.kind.equals("investigate")||!a.target.equals("spring_core")))throw bad("array binding");
            if(a.stages.contains("new")&&(!a.id.equals("accept")||!a.kind.equals("talk")||!a.role.equals("guide")))throw bad("explicit acceptance");
            if(a.stages.contains("complete"))throw bad("completed chapter is terminal");
            if(!a.kind.equals("investigate")&&a.text.isBlank())throw bad("empty option text");
            for(var v:a.textVariants) {
                validateCondition(v.when,evidence);
                if(a.kind.equals("investigate")||v.text.isBlank())throw bad("option variant");
            }
            if(a.id.equals("learn")||a.id.equals("close"))throw bad("reserved option");
        }
        if(actions.values().stream().filter(a->a.kind.equals("claim")).count()!=1)throw bad("exactly one reward action");
        if(opening.learnText.isBlank()||opening.lines.isEmpty())throw bad("opening");
        for(var v:opening.variants) {validateCondition(v.when,evidence);if(v.lines.isEmpty())throw bad("opening variant");}
        // Each complete replacement is independently bounded by the network schema.
        for(var st:stages.values())for(var role:roles.keySet()) {
            long count=actions.values().stream().filter(a->!a.kind.equals("investigate")&&a.role.equals(role)&&a.stages.contains(st.id)).count();
            if(count>3)throw bad("dialogue option count");
        }
        for(var s:stages.values()) {
            validateTarget(s,s.target);
            for(var v:s.dialogueVariants) {
                validateCondition(v.when,evidence);
                if(!roles.containsKey(v.role)||v.lines.isEmpty())throw bad("dialogue variant");
            }
            for(var v:s.tracking) {validateCondition(v.when,evidence);validateTarget(s,v.value.target);if(v.value.objective.isBlank())throw bad("tracking objective");}
            if(!evidence.containsAll(s.autoRequires)||(!s.autoNext.isEmpty()&&(!stages.containsKey(s.autoNext)||s.autoRequires.isEmpty())))throw bad("auto stage");
            // Automatic edges must be acyclic, including loops longer than one stage.
            var seen=new HashSet<String>();var current=s;
            while(!current.autoNext.isEmpty()) {if(!seen.add(current.id))throw bad("automatic cycle");current=stages.get(current.autoNext);}
        }
        var reachable=new HashSet<String>();reachable.add("new");boolean changed;
        do {changed=false;for(var a:actions.values())if(a.stages.stream().anyMatch(reachable::contains)){if(!a.next.isEmpty())changed|=reachable.add(a.next);}
            for(var s:stages.values())if(reachable.contains(s.id)&&!s.autoNext.isEmpty())changed|=reachable.add(s.autoNext);
        }while(changed);
        if(!reachable.containsAll(stages.keySet()))throw bad("unreachable stage");
        // Every node must also have a route to the ending, not just be reachable from the opening.
        var ending=new HashSet<String>();ending.add("complete");
        do {changed=false;for(var a:actions.values())if(ending.contains(a.next))changed|=ending.addAll(a.stages);
            for(var s:stages.values())if(ending.contains(s.autoNext))changed|=ending.add(s.id);
        }while(changed);
        if(!ending.containsAll(stages.keySet()))throw bad("stage without ending");
    }
    private void validateTarget(Stage stage,String target) {
        if(!target.isEmpty()&&!TARGETS.contains(target)&&!roles.containsKey(target))throw bad("stage target");
        if(TARGETS.contains(target)&&actions.values().stream().noneMatch(a->a.kind.equals("investigate")&&a.target.equals(target)&&a.stages.contains(stage.id)))
            throw bad("tracking target unavailable in stage "+stage.id);
    }
    private static void validateCondition(Condition c,Set<String> evidence) {
        if(!evidence.containsAll(c.evidence.keySet()))throw bad("condition evidence reference");
    }
    private static Condition condition(JsonObject variant) {
        var o=variant.getAsJsonObject("when");
        if(o==null||o.isEmpty()||!Set.of("evidence","returned","regulated").containsAll(o.keySet()))throw bad("condition fields");
        var evidence=new LinkedHashMap<String,Boolean>();
        if(o.has("evidence")) {
            var facts=o.getAsJsonObject("evidence");if(facts.isEmpty()||facts.size()>32)throw bad("condition evidence bound");
            for(var e:facts.entrySet()) {
                if(!StoryProgress.id(e.getKey()))throw bad("condition evidence id");
                evidence.put(e.getKey(),bool(e.getValue()));
            }
        }
        return new Condition(Map.copyOf(evidence),o.has("returned")?bool(o.get("returned")):null,o.has("regulated")?bool(o.get("regulated")):null);
    }
    private static boolean bool(JsonElement e) {
        if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isBoolean())throw bad("condition boolean");
        return e.getAsBoolean();
    }
    private static IllegalArgumentException bad(String s){return new IllegalArgumentException(RESOURCE+": "+s);}
    private static void fields(JsonObject o,String... keys){Set<String> expected=Set.of(keys);if(!o.keySet().equals(expected))throw bad("missing/unknown fields "+o.keySet());}
    private static String string(JsonObject o,String key,int max){var e=o.get(key);if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isString())throw bad(key);String s=e.getAsString();if(s.length()>max)throw bad(key);return s;}
    private static String identifier(JsonObject o,String key){String s=string(o,key,64);if(!StoryProgress.id(s))throw bad(key);return s;}
    private static int integer(JsonObject o,String key,int min,int max){try{var p=o.getAsJsonPrimitive(key);if(!p.isNumber())throw bad(key);int n=p.getAsBigDecimal().intValueExact();if(n<min||n>max)throw bad(key);return n;}catch(RuntimeException e){throw bad(key);}}
    private static JsonArray array(JsonObject o,String key,int max){var a=o.getAsJsonArray(key);if(a==null||a.size()>max)throw bad(key);return a;}
    private static List<String> strings(JsonObject o,String key,int count,int max){return strings(array(o,key,count),count,max);}
    private static List<String> strings(JsonArray a,int count,int max){if(a.size()>count)throw bad("list bound");var out=new ArrayList<String>();for(var e:a){if(!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isString()||e.getAsString().length()>max)throw bad("string list");out.add(e.getAsString());}return List.copyOf(out);}
    private static List<String> ids(JsonObject o,String key){var v=strings(o,key,32,64);if(new HashSet<>(v).size()!=v.size()||v.stream().anyMatch(s->!StoryProgress.id(s)))throw bad(key);return v;}
}
