package com.example.myvillage.client.item;

import com.example.myvillage.aptitude.AptitudeRules;
import com.example.myvillage.aptitude.AptitudeRuntime;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.fate.FateDefinition;
import com.example.myvillage.cultivation.fate.FateService;
import com.example.myvillage.item.QualityPicker;
import com.example.myvillage.item.TestUnitConfig;
import com.example.myvillage.item.TestUnitRelations;
import com.example.myvillage.item.TestUnitRuntime;
import com.example.myvillage.network.TestUnitPayload;
import com.example.myvillage.network.TestUnitSnapshotPayload;
import com.example.myvillage.stats.StatsRules;
import com.example.myvillage.entity.npc.NpcSkillLoadout;
import com.example.myvillage.entity.npc.NpcSkillRuntime;
import com.example.myvillage.combat.definition.CombatStyles;
import com.example.myvillage.cultivation.data.TechniqueCategory;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.cultivation.fate.ability.FateAbilityDefinition;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.neoforge.network.PacketDistributor;

/** 复用取物器的原版按钮和配色；所有生成、属性和关系写入都由服务器处理。 */
public final class TestUnitScreen extends Screen {
    private static final String[] TABS = {"召唤", "战斗属性", "天赋词条", "各类资质", "功法技能", "单位关系"};
    private static final String[] TEAMS = {"甲", "乙", "丙", "丁"};
    private final Screen parent;
    private final List<EntityType<?>> types = BuiltInRegistries.ENTITY_TYPE.stream()
            .filter(TestUnitRuntime::allowedType).filter(DefaultAttributes::hasSupplier)
            .sorted(Comparator.<EntityType<?>>comparingInt(t -> id(t).equals("myvillage:cultivator") ? 0 : 1)
                    .thenComparing(t -> id(t))).toList();
    private EntityType<?> selectedType = types.getFirst();
    private final List<StatsRules.RealmDefinition> realms = StatsRules.bundled().realms();
    private int tab, left, top, paneWidth, realmIndex = 1, stage, foundation = 1, golden = 1;
    private int typePage, attributePage, fatePage, aptitudePage, unitPage;
    private String typeSearch = "", attributeSearch = "", fateSearch = "", name = "测试修士";
    private String power = "", powerCap = "", message = "召唤后默认暂停；在单位关系页开始战斗。";
    private boolean paused = true, hostilePlayer, hostileMonsters;
    private int team, enemies;
    private final Map<String, String> attributes = new LinkedHashMap<>(), aptitudes = new LinkedHashMap<>();
    private final List<String> fates = new ArrayList<>();
    private List<JsonObject> units = List.of();
    private UUID unitId, otherId;
    private UUID skillUnit;
    private String skillUnitName="";
    private int skillPage, skillCategory;
    private String skillSearch="", selectedSkill="", runningCore="", weapon="";
    private final Map<String, SkillDraft> skillDrafts=new LinkedHashMap<>();
    private static final class SkillDraft {
        String mastery="0", priority="0";
        boolean automatic;
        SkillDraft(boolean automatic) { this.automatic=automatic; }
    }
    private record SkillOption(String id,String name,TechniqueDefinition definition,FateAbilityDefinition fate) { }
    private int editTeam, editEnemies;
    private boolean editPlayer, editMonsters;
    private final List<Label> labels = new ArrayList<>();
    private record Label(String text, int x, int y, int width, int color) { }

    public TestUnitScreen(Screen parent) {
        super(Component.literal("召唤测试单位")); this.parent = parent;
        TestUnitSnapshotPayload.installReceiver(payload -> {
            if (Minecraft.getInstance().screen instanceof TestUnitScreen screen) screen.receive(payload);
        });
        send("list", new JsonObject());
    }

    private static String id(EntityType<?> type) { return BuiltInRegistries.ENTITY_TYPE.getKey(type).toString(); }
    private static String mark(boolean value) { return value ? "开启" : "关闭"; }
    private static boolean matches(String query, String name, String id) {
        var q = query.strip().toLowerCase(Locale.ROOT); return name.toLowerCase(Locale.ROOT).contains(q) || id.contains(q);
    }
    private void label(String text, int x, int y, int width) { labels.add(new Label(text, x, y, width, 0xFFE5D5AF)); }
    private Button button(String text, int x, int y, int width, Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(text), b -> action.run())
                .bounds(left + x, top + y, width, 18).build());
    }
    private EditBox box(String value, String hint, int x, int y, int width, int max, Consumer<String> changed) {
        var box = new EditBox(font, left + x, top + y, width, 16, Component.literal(hint));
        box.setMaxLength(max); box.setHint(Component.literal(hint)); box.setValue(value); box.setResponder(changed);
        addRenderableWidget(box); return box;
    }
    private void rebuild() { rebuildWidgets(); }
    @Override protected void init() {
        paneWidth = Math.min(440, width - 8); left = (width - paneWidth) / 2; top = (height - 230) / 2;
        labels.clear();
        int tw = (paneWidth - 16) / TABS.length;
        for (int i = 0; i < TABS.length; i++) {
            final int index = i; var b = button(TABS[i], 8 + i * tw, 25, tw - 2, () -> { tab = index; rebuild(); });
            b.active = tab != i;
        }
        switch (tab) { case 0 -> summon(); case 1 -> attributes(); case 2 -> fates(); case 3 -> aptitudes(); case 4 -> skills(); case 5 -> units(); }
        button("返回取物", 8, 200, 70, () -> minecraft.setScreen(parent));
        button(tab==4&&skillUnit!=null?"应用到该 NPC":"召唤所选单位", paneWidth - 104, 200, 96,
                ()->{if(tab==4&&skillUnit!=null)applySkills();else spawn();});
        if(tab==4&&skillUnit!=null)button("用于新召唤",84,200,86,()->{skillUnit=null;message="当前功法配置将用于新召唤的 NPC。";rebuild();});
    }

    private void pagination(int page, int size, int rows, int x, int y, int w, Consumer<Integer> change) {
        button("<", x, y, 22, () -> { change.accept(page - 1); rebuild(); }).active = page > 0;
        button(">", x + w - 22, y, 22, () -> { change.accept(page + 1); rebuild(); }).active = (page + 1) * rows < size;
        label((page + 1) + "/" + Math.max(1, (size + rows - 1) / rows), x + 28, y + 5, w - 54);
    }

    private void summon() {
        int col = paneWidth / 2, rw = paneWidth - col - 8;
        box(typeSearch, "搜索 NPC、妖兽或原版敌怪", 8, 51, col - 16, 64, v -> {
            typeSearch = v; typePage = 0; rebuildKeepingFocus("type");
        }).setTooltip(Tooltip.create(Component.literal("输入中文名称或实体 ID。")));
        var filtered = types.stream().filter(t -> matches(typeSearch, t.getDescription().getString(), id(t))).toList();
        for (int i = 0; i < 5; i++) {
            int index = typePage * 5 + i; if (index >= filtered.size()) break;
            var type = filtered.get(index);
            var b = button(font.plainSubstrByWidth((type == selectedType ? "• " : "") + type.getDescription().getString(), col - 24),
                    8, 73 + i * 21, col - 16, () -> {
                        selectedType = type; attributes.clear(); attributePage = 0;
                        name = type == types.getFirst() ? "测试修士" : "测试" + type.getDescription().getString(); rebuild();
                    });
            b.setTooltip(Tooltip.create(type.getDescription().copy().append("\n" + id(type))));
        }
        pagination(typePage, filtered.size(), 5, 8, 179, col - 16, p -> typePage = p);
        label("名称", col, 51, rw);
        box(name, "单位名称", col, 64, rw, 32, v -> name = v);
        button(realmName() + "（切换）", col, 85, rw, () -> { realmIndex = (realmIndex + 1) % realms.size(); stage = 0; rebuild(); });
        button(stageName() + "（切换）", col, 107, rw, () -> { stage = (stage + 1) % realms.get(realmIndex).stages(); rebuild(); });
        button("筑基品质 " + quality(foundation), col, 129, rw, () -> { foundation = foundation % 4 + 1; rebuild(); }).active = npc();
        button("金丹品质 " + quality(golden), col, 151, rw, () -> { golden = golden % 3 + 1; rebuild(); }).active = npc();
        button("召唤时暂停：" + mark(paused), col, 174, rw, () -> { paused = !paused; rebuild(); });
    }

    /** 搜索框重建时恢复光标，避免每输入一个字就失去焦点。 */
    private void rebuildKeepingFocus(String kind) {
        int cursor = getFocused() instanceof EditBox box ? box.getCursorPosition() : -1;
        rebuild();
        for (var child : children()) if (child instanceof EditBox box && box.getMessage().getString().equals(
                kind.equals("type") ? "搜索 NPC、妖兽或原版敌怪" : kind.equals("attribute") ? "搜索属性" : kind.equals("skill")?"搜索功法或技能":"搜索词条")) {
            setFocused(box); if (cursor >= 0) box.moveCursorTo(Math.min(cursor, box.getValue().length()), false); break;
        }
    }

    private String realmName() {
        var access = minecraft.level.registryAccess().registryOrThrow(ModCultivationRegistries.REALMS);
        var realm = access.get(ResourceLocation.parse(realms.get(realmIndex).id()));
        return realm == null ? realms.get(realmIndex).id() : Component.translatable(realm.translationKey()).getString();
    }
    private String stageName() {
        var realm = minecraft.level.registryAccess().registryOrThrow(ModCultivationRegistries.REALMS)
                .get(ResourceLocation.parse(realms.get(realmIndex).id()));
        return realm == null || stage >= realm.stages().size() ? "第 " + (stage + 1) + " 阶段"
                : Component.translatable(realm.stages().get(stage).translationKey()).getString();
    }
    private static String quality(int value) { return switch (value) { case 1 -> "下品"; case 2 -> "中品"; case 3 -> "上品"; default -> "极品"; }; }
    private boolean npc() { return id(selectedType).equals("myvillage:cultivator") || id(selectedType).equals("myvillage:ridge_reaver"); }

    @SuppressWarnings("unchecked")
    private List<Holder.Reference<Attribute>> availableAttributes() {
        var supplier = DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) selectedType);
        return BuiltInRegistries.ATTRIBUTE.holders().filter(supplier::hasAttribute)
                .sorted(Comparator.comparingInt((Holder.Reference<Attribute> a) -> {
                    String id = a.key().location().toString();
                    int index = List.of("minecraft:generic.max_health", "minecraft:generic.attack_damage", "myvillage:body_defense",
                            "minecraft:generic.armor", "minecraft:generic.movement_speed", "minecraft:generic.follow_range").indexOf(id);
                    return index < 0 ? 100 : index;
                }).thenComparing(a -> a.key().location().toString())).toList();
    }

    @SuppressWarnings("unchecked")
    private void attributes() {
        label("填写境界后的属性值；装备和天赋继续加成。空白按境界。", 8, 50, paneWidth - 16);
        box(attributeSearch, "搜索属性", 8, 66, paneWidth - 16, 64, v -> {
            attributeSearch = v; attributePage = 0; rebuildKeepingFocus("attribute");
        });
        var supplier = DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) selectedType);
        var filtered = availableAttributes().stream().filter(a -> matches(attributeSearch,
                Component.translatable(a.value().getDescriptionId()).getString(), a.key().location().toString())).toList();
        for (int i = 0; i < 4; i++) {
            int index = attributePage * 4 + i; if (index >= filtered.size()) break;
            var attr = filtered.get(index); String id = attr.key().location().toString(); int y = 89 + i * 22;
            String title = Component.translatable(attr.value().getDescriptionId()).getString();
            label(title, 8, y + 4, paneWidth - 120);
            var field = box(attributes.getOrDefault(id, ""), "按境界", paneWidth - 102, y, 94, 24,
                    v -> attributes.put(id, v));
            String bounds = attr.value() instanceof RangedAttribute a ? "\n范围：" + number(a.getMinValue()) + "～" + number(a.getMaxValue()) : "";
            field.setTooltip(Tooltip.create(Component.literal(title + "\n" + id + "\n默认基础值：" + number(supplier.getBaseValue(attr)) + bounds)));
        }
        pagination(attributePage, filtered.size(), 4, 8, 180, paneWidth - 16, p -> attributePage = p);
    }

    private void fates() {
        if (!npc()) { label("天赋词条适用于修士 NPC；请先在召唤页选择修士。", 8, 56, paneWidth - 16); return; }
        label("已选 " + fates.size() + "/3；选满三个使用指定词条，清空后随机。", 8, 50, paneWidth - 16);
        box(fateSearch, "搜索词条", 8, 65, paneWidth - 78, 64, v -> { fateSearch = v; fatePage = 0; rebuildKeepingFocus("fate"); });
        button("清空", paneWidth - 64, 64, 56, () -> { fates.clear(); rebuild(); });
        var definitions = FateService.definitions(minecraft.level.registryAccess());
        var filtered = definitions.entrySet().stream().filter(e -> e.getValue().enabled())
                .filter(e -> matches(fateSearch, Component.translatable(e.getValue().translationKey()).getString(), e.getKey()))
                .sorted(Comparator.<Map.Entry<String, FateDefinition>>comparingInt(e -> fates.contains(e.getKey()) ? 0 : 1)
                        .thenComparing(Map.Entry::getKey)).toList();
        for (int i = 0; i < 4; i++) {
            int index = fatePage * 4 + i; if (index >= filtered.size()) break;
            var entry = filtered.get(index); var def = entry.getValue();
            var b = button((fates.contains(entry.getKey()) ? "☑ " : "☐ ") + Component.translatable(def.translationKey()).getString()
                    + "（" + def.cost() + "点）", 8, 88 + i * 22, paneWidth - 16, () -> {
                        if (!fates.remove(entry.getKey())) {
                            if (fates.size() >= 3) message = "已选满三个词条，请先取消一个。";
                            else fates.add(entry.getKey());
                        }
                        rebuild();
                    });
            b.setTooltip(Tooltip.create(Component.translatable(def.descriptionKey()).append("\n" + entry.getKey())));
        }
        pagination(fatePage, filtered.size(), 4, 8, 179, paneWidth - 16, p -> fatePage = p);
    }

    private void aptitudes() {
        if (!npc()) { label("灵根、兵器、技艺资质及灵力适用于修士 NPC。", 8, 56, paneWidth - 16); return; }
        label("资质填写非负整数；空白使用本次出生值。", 8, 50, paneWidth - 16);
        int col = paneWidth / 2;
        label("起始灵力（空白为满）", 8, 68, col - 16);
        label("灵力上限（空白按境界）", col, 68, col - 8);
        box(power, "满灵力", 8, 81, col - 16, 19, v -> power = v);
        box(powerCap, "境界容量", col, 81, col - 8, 19, v -> powerCap = v);
        var ids = AptitudeRules.get().ids();
        for (int i = 0; i < 3; i++) {
            int index = aptitudePage * 3 + i; if (index >= ids.size()) break;
            String id = ids.get(index); int y = 108 + i * 24;
            label(AptitudeRuntime.name(id), 8, y + 4, paneWidth - 120);
            box(aptitudes.getOrDefault(id, ""), "出生值", paneWidth - 102, y, 94, 10, v -> aptitudes.put(id, v));
        }
        pagination(aptitudePage, ids.size(), 3, 8, 179, paneWidth - 16, p -> aptitudePage = p);
    }

    private List<SkillOption> skillOptions() {
        var result=new ArrayList<SkillOption>();
        var registry=minecraft.level.registryAccess().registryOrThrow(ModCultivationRegistries.TECHNIQUES);
        for(var id:registry.keySet()) {
            var def=registry.get(id);result.add(new SkillOption(id.toString(),Component.translatable(def.translationKey()).getString(),def,null));
        }
        for(var fate:FateAbilityDefinition.all())result.add(new SkillOption(fate.id().toString(),fate.name(),null,fate));
        return result.stream().sorted(Comparator.comparing(SkillOption::id)).toList();
    }
    private static String category(SkillOption option) {
        if(option.fate()!=null)return "天赋能力";
        return switch(option.definition().category()){case CORE->"心法";case ACTIVE->"绝技";case MOVEMENT->"身法";case BODY->"炼体";};
    }
    private boolean autoAllowed(SkillOption option) {
        return NpcSkillRuntime.executable(ResourceLocation.parse(option.id()),option.definition())
                && (option.fate()!=null||option.definition().category()==TechniqueCategory.ACTIVE||option.definition().category()==TechniqueCategory.MOVEMENT);
    }
    private void skills() {
        if(!npc()&&skillUnit==null){label("请选择修士 NPC 后配置功法、技能和武器。",8,56,paneWidth-16);return;}
        int col=paneWidth/2,rw=paneWidth-col-8;
        box(skillSearch,"搜索功法或技能",8,50,paneWidth-128,64,v->{skillSearch=v;skillPage=0;rebuildKeepingFocus("skill");});
        var weapons=CombatStyles.bundled().weapons();
        String current=weapon.isEmpty()?com.example.myvillage.entity.npc.NpcCombatRules.get().weapon():weapon;
        var item=BuiltInRegistries.ITEM.get(ResourceLocation.parse(current));
        button("武器："+font.plainSubstrByWidth(item.getDescription().getString(),72),paneWidth-112,49,104,()->{
            int index=0;for(int i=0;i<weapons.size();i++)if(weapons.get(i).item().toString().equals(current))index=i;
            weapon=weapons.get((index+1)%weapons.size()).item().toString();rebuild();
        }).setTooltip(Tooltip.create(item.getDescription().copy().append("\n点击切换武器。武技仍检查武器家族。")));
        String[] categories={"全部","心法","绝技","身法","炼体","天赋"};
        int cw=(paneWidth-16)/categories.length;
        for(int i=0;i<categories.length;i++){
            final int index=i;
            button(categories[i],8+i*cw,70,cw-2,()->{skillCategory=index;skillPage=0;rebuild();}).active=skillCategory!=i;
        }
        var options=skillOptions();
        var filtered=options.stream().filter(o->matches(skillSearch,o.name(),o.id()))
                .filter(o->skillCategory==0||skillCategory==5?skillCategory==0||o.fate()!=null:
                        o.definition()!=null&&o.definition().category()==TechniqueCategory.values()[skillCategory==1?0:skillCategory==2?1:skillCategory==3?2:3])
                .toList();
        if(selectedSkill.isEmpty()&&!filtered.isEmpty())selectedSkill=filtered.getFirst().id();
        for(int i=0;i<4;i++){
            int index=skillPage*4+i;if(index>=filtered.size())break;var option=filtered.get(index);
            var b=button(font.plainSubstrByWidth((option.id().equals(selectedSkill)?"• ":"")+(skillDrafts.containsKey(option.id())?"[学] ":"")+option.name(),col-24),
                    8,93+i*21,col-16,()->{selectedSkill=option.id();rebuild();});
            b.setTooltip(Tooltip.create(Component.literal(option.name()+"\n"+category(option)+"\n"+option.id()
                    +(NpcSkillRuntime.executable(ResourceLocation.parse(option.id()),option.definition())?"":"\n此功法尚无 NPC 主动施放入口，可保存学习记录。"))));
        }
        pagination(skillPage,filtered.size(),4,8,179,col-16,p->skillPage=p);
        var selected=options.stream().filter(o->o.id().equals(selectedSkill)).findFirst().orElse(null);if(selected==null)return;
        label(selected.name(),col,94,rw);
        var draft=skillDrafts.get(selected.id());
        boolean core=selected.definition()!=null&&selected.definition().isCore();
        button(draft==null?"添加到已学":"移除功法",col,107,core&&draft!=null?rw/2-2:rw,()->{
            if(skillDrafts.remove(selected.id())!=null){if(runningCore.equals(selected.id()))runningCore="";}
            else if(skillDrafts.size()<NpcSkillLoadout.LIMIT)skillDrafts.put(selected.id(),new SkillDraft(autoAllowed(selected)));
            else message="已学功法数量达到上限。";
            rebuild();
        });
        if(draft==null){label("添加后可编辑熟练度和自动施放。",col,135,rw);return;}
        if(core)button(runningCore.equals(selected.id())?"运行中":"运行心法",col+rw/2,107,rw-rw/2,
                ()->{runningCore=runningCore.equals(selected.id())?"":selected.id();rebuild();});
        label("熟练度点数",col,130,rw/2);
        box(draft.mastery,"非负整数",col+rw/2,127,rw-rw/2,19,v->draft.mastery=v);
        button("自动施放："+mark(draft.automatic),col,149,rw,()->{draft.automatic=!draft.automatic;rebuild();}).active=autoAllowed(selected);
        label("优先级",col,173,rw/2);
        box(draft.priority,"小数值优先",col+rw/2,169,rw-rw/2,3,v->draft.priority=v)
                .setTooltip(Tooltip.create(Component.literal("0～999，数值越小越先尝试；同优先级按技能 ID 排序。")));
    }
    private NpcSkillLoadout loadout() {
        var rows=new ArrayList<NpcSkillLoadout.Learned>();
        for(var e:skillDrafts.entrySet())rows.add(new NpcSkillLoadout.Learned(e.getKey(),Long.parseLong(e.getValue().mastery),
                e.getValue().automatic,Integer.parseInt(e.getValue().priority)));
        return new NpcSkillLoadout(weapon,runningCore,rows);
    }
    private void applySkills() {
        try {
            var data=new JsonObject();data.addProperty("unit",skillUnit.toString());data.add("skills",JsonParser.parseString(loadout().json()));
            send("skills",data);message="已提交技能配置，等待服务器回执。";
        }catch(RuntimeException e){message="熟练度需要为非负整数，优先级需要在 0～999 之间。";}
    }

    private JsonObject selectedUnit() { return units.stream().filter(u -> UUID.fromString(u.get("id").getAsString()).equals(unitId)).findFirst().orElse(null); }
    private void select(JsonObject unit) {
        unitId = UUID.fromString(unit.get("id").getAsString()); editTeam = unit.get("team").getAsInt();
        editEnemies = unit.get("enemies").getAsInt(); editPlayer = unit.get("player").getAsBoolean(); editMonsters = unit.get("monsters").getAsBoolean();
        if (unitId.equals(otherId)) otherId = null;
    }
    private void units() {
        int col = paneWidth / 2, rw = paneWidth - col - 8;
        button("刷新", 8, 50, 48, () -> send("list", new JsonObject()));
        button("全部开始", 60, 50, 62, () -> send("resume_all", new JsonObject()));
        button("全部暂停", 126, 50, 62, () -> send("pause_all", new JsonObject()));
        button("清理附近", paneWidth - 86, 50, 78, () -> confirmClear());
        for (int i = 0; i < 4; i++) {
            int index = unitPage * 4 + i; if (index >= units.size()) break;
            var unit = units.get(index); UUID id = UUID.fromString(unit.get("id").getAsString());
            var b = button(font.plainSubstrByWidth((id.equals(unitId) ? "• " : "") + unit.get("name").getAsString(), col - 22),
                    8, 74 + i * 22, col - 16, () -> { select(unit); rebuild(); });
            b.setTooltip(Tooltip.create(Component.literal(unit.get("name").getAsString() + "\n阵营：" + TEAMS[unit.get("team").getAsInt()]
                    + "\n生命：" + number(unit.get("health").getAsDouble()) + "/" + number(unit.get("maximum").getAsDouble())
                    + "\n" + (unit.get("paused").getAsBoolean() ? "AI 已暂停" : "AI 已开启") + "\n" + unit.get("type").getAsString())));
        }
        pagination(unitPage, units.size(), 4, 8, 160, col - 16, p -> unitPage = p);
        var unit = selectedUnit(); boolean editing = unit != null;
        int shownTeam = editing ? editTeam : team, shownEnemies = editing ? editEnemies : enemies;
        label(editing ? "编辑：" + unit.get("name").getAsString() : "新召唤单位的关系", col, 73, rw);
        button("所属阵营：" + TEAMS[shownTeam], col, 86, rw, () -> {
            if (editing) editTeam = (editTeam + 1) % 4; else team = (team + 1) % 4; rebuild();
        });
        for (int i = 0; i < 4; i++) {
            final int t = i; boolean hostile = (shownEnemies & 1 << t) != 0;
            button((hostile ? "敌" : "和") + TEAMS[t], col + i * (rw / 4), 108, rw / 4 - 1, () -> {
                if (editing) editEnemies ^= 1 << t; else enemies ^= 1 << t; rebuild();
            }).setTooltip(Tooltip.create(Component.literal("切换与" + TEAMS[t] + "阵营的关系；任一方设为敌对就会互相攻击。")));
        }
        button("敌对本人：" + mark(editing ? editPlayer : hostilePlayer), col, 130, rw, () -> {
            if (editing) editPlayer = !editPlayer; else hostilePlayer = !hostilePlayer; rebuild();
        }).setTooltip(Tooltip.create(Component.literal("用于玩家对战。玩家需要切换为生存或冒险模式。")));
        button("敌对妖兽/敌怪：" + mark(editing ? editMonsters : hostileMonsters), col, 152, rw, () -> {
            if (editing) editMonsters = !editMonsters; else hostileMonsters = !hostileMonsters; rebuild();
        });
        if (editing) {
            button("应用关系", col, 174, rw / 2 - 2, this::applyRelations);
            button(unit.get("paused").getAsBoolean() ? "开始" : "暂停", col + rw / 2, 174, rw / 2,
                    () -> send(unit.get("paused").getAsBoolean() ? "resume" : "pause", unitData()));
            int bw=(col-28)/4;
            button("对手",8,180,bw,()->minecraft.setScreen(new PairScreen()));
            button("技能",12+bw,180,bw,()->send("inspect_skills",unitData()));
            button("删除",16+bw*2,180,bw,()->send("remove",unitData()));
            button("新建",20+bw*3,180,bw,()->{unitId=null;skillUnit=null;rebuild();});
        } else label("新单位使用右侧配置；单位最多保留 64 个。", 8, 186, paneWidth - 16);
    }

    private void confirmClear() {
        minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(yes -> {
            minecraft.setScreen(this); if (yes) send("clear", new JsonObject());
        }, Component.literal("清理测试单位"), Component.literal("删除附近 64 格内由你召唤的测试单位？")));
    }
    private JsonObject unitData() { var data = new JsonObject(); data.addProperty("unit", unitId.toString()); return data; }
    private void applyRelations() {
        var data = unitData(); data.addProperty("team", editTeam); data.addProperty("enemies", editEnemies);
        data.addProperty("player", editPlayer); data.addProperty("monsters", editMonsters); send("relations", data);
    }
    private static String number(double value) { return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value); }
    private void spawn() {
        try {
            Map<String, Double> attrs = new LinkedHashMap<>(); Map<String, Integer> scores = new LinkedHashMap<>();
            for (var entry : attributes.entrySet()) if (!entry.getValue().isBlank()) attrs.put(entry.getKey(), Double.parseDouble(entry.getValue()));
            if (npc()) for (var entry : aptitudes.entrySet()) if (!entry.getValue().isBlank()) scores.put(entry.getKey(), Integer.parseInt(entry.getValue()));
            var config = new TestUnitConfig(id(selectedType), name, realms.get(realmIndex).id(), stage, foundation, golden,
                    npc() ? fates : List.of(), scores, attrs, npc() && !power.isBlank() ? Long.parseLong(power) : -1,
                    npc() && !powerCap.isBlank() ? Long.parseLong(powerCap) : -1, paused,
                    new TestUnitRelations.Unit(minecraft.player.getUUID(), team, enemies, hostilePlayer, hostileMonsters, Map.of()),
                    npc()?loadout():NpcSkillLoadout.EMPTY);
            PacketDistributor.sendToServer(new TestUnitPayload("spawn", config.json())); message = "已提交召唤请求，等待服务器回执。";
        } catch (RuntimeException failure) { message = "配置无效：请检查数值、品质和三个天赋词条。"; }
    }
    private static void send(String action, JsonObject data) { PacketDistributor.sendToServer(new TestUnitPayload(action, data.toString())); }
    private void receive(TestUnitSnapshotPayload payload) {
        var data = JsonParser.parseString(payload.data()).getAsJsonObject(); message = data.get("message").getAsString();
        if(data.has("skills")) {
            var state=NpcSkillLoadout.parse(data.get("skills").toString());skillUnit=UUID.fromString(data.get("skill_unit").getAsString());
            skillUnitName=data.get("skill_name").getAsString();
            skillDrafts.clear();weapon=state.weapon();runningCore=state.core();
            for(var row:state.learned()){var draft=new SkillDraft(row.automatic());draft.mastery=Long.toString(row.mastery());draft.priority=Integer.toString(row.priority());skillDrafts.put(row.id(),draft);}
            tab=4;rebuild();return;
        }
        var next = new ArrayList<JsonObject>(); data.getAsJsonArray("units").forEach(u -> next.add(u.getAsJsonObject())); units = List.copyOf(next);
        unitPage = Math.min(unitPage, Math.max(0, (units.size() - 1) / 4));
        var unit = selectedUnit(); if (unit != null) select(unit); else unitId = null;
        if (tab == 5) rebuild();
    }
    @Override public void tick() {
        if (minecraft == null || minecraft.player == null || !minecraft.player.isAlive() || !QualityPicker.held(minecraft.player)) onClose();
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public void render(GuiGraphics g, int mx, int my, float delta) {
        renderBackground(g, mx, my, delta);
        g.fill(left - 2, top - 2, left + paneWidth + 2, top + 232, 0xFF786346);
        g.fill(left, top, left + paneWidth, top + 230, 0xFF252823);
        g.drawString(font, tab==4&&skillUnit!=null?Component.literal("编辑技能："+skillUnitName):title, left + 8, top + 8, 0xFFE5D5AF, false);
        for (var label : labels) g.drawString(font, font.plainSubstrByWidth(label.text, label.width), left + label.x, top + label.y, label.color, false);
        g.drawString(font, font.plainSubstrByWidth(message, paneWidth - 16), left + 8, top + 220, 0xFFC5C6B7, false);
        for (var widget : renderables) widget.render(g, mx, my, delta);
        if (mx >= left + 8 && mx < left + paneWidth - 8 && my >= top + 218 && my < top + 230)
            g.renderTooltip(font, Component.literal(message), mx, my);
    }

    private final class PairScreen extends Screen {
        private int page;
        PairScreen() { super(Component.literal("设置两个测试单位的关系")); }
        @Override protected void init() {
            int x = (width - 300) / 2, y = (height - 220) / 2;
            var others = units.stream().filter(u -> !u.get("id").getAsString().equals(unitId.toString())).toList();
            for (int i = 0; i < 5; i++) {
                int index = page * 5 + i; if (index >= others.size()) break;
                var u = others.get(index); UUID id = UUID.fromString(u.get("id").getAsString());
                var b = Button.builder(Component.literal((id.equals(otherId) ? "• " : "") + u.get("name").getAsString()),
                        v -> { otherId = id; rebuildWidgets(); }).bounds(x + 8, y + 34 + i * 23, 284, 20).build(); addRenderableWidget(b);
            }
            addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; rebuildWidgets(); }).bounds(x+8,y+152,30,18).build()).active = page > 0;
            addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; rebuildWidgets(); }).bounds(x+262,y+152,30,18).build()).active = (page+1)*5 < others.size();
            for (int mode = -1; mode <= 1; mode++) {
                final int m = mode;
                addRenderableWidget(Button.builder(Component.literal(mode < 0 ? "按阵营规则" : mode == 0 ? "互相中立" : "互相敌对"), b -> {
                    var data = unitData(); data.addProperty("other", otherId.toString()); data.addProperty("mode", m);
                    minecraft.setScreen(TestUnitScreen.this); send("pair", data);
                }).bounds(x + 8 + (mode+1)*96, y + 176, 92, 20).build()).active = otherId != null;
            }
            addRenderableWidget(Button.builder(Component.literal("返回"), b -> onClose()).bounds(x+8,y+200,284,18).build());
        }
        @Override public void onClose() { minecraft.setScreen(TestUnitScreen.this); }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void render(GuiGraphics g,int mx,int my,float delta) {
            renderBackground(g,mx,my,delta); int x=(width-300)/2,y=(height-220)/2;
            g.fill(x,y,x+300,y+220,0xFF252823);g.drawString(font,title,x+8,y+8,0xFFE5D5AF,false);
            g.drawString(font,Component.literal("为所选单位选择另一个对手："),x+8,y+21,0xFFC5C6B7,false);
            for (var widget : renderables) widget.render(g,mx,my,delta);
        }
    }
}
