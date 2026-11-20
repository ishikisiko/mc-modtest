package com.example.myvillage.client.cultivation.panel;

import com.example.myvillage.client.combat.ClientSkillBindings;
import com.example.myvillage.combat.SkillBindingRules;
import com.example.myvillage.combat.art.ArtDefinition;
import com.example.myvillage.cultivation.data.HeartMethod;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.SchoolDefinition;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.cultivation.data.TechniqueCategory;
import com.example.myvillage.cultivation.fate.ability.FateAbilityDefinition;
import com.example.myvillage.cultivation.observation.ObservationRules;
import com.example.myvillage.cultivation.qi.SpiritualPower;
import com.example.myvillage.cultivation.technique.HeartTechniques;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Only reads synchronized definitions/progress. Palette and art are independent presentation resources. */
final class TechniqueTiles {
    record Hit(ResourceLocation id, int x, int y, int width, int height) {
        boolean contains(double px, double py) { return px >= x && px < x+width && py >= y && py < y+height; }
    }
    private TechniqueTiles() {}
    static String name(PanelContext c, ResourceLocation id) {
        if(ObservationRules.ID.equals(id))return ObservationRules.bundled().name();
        var ability=FateAbilityDefinition.find(id);
        if(ability.isPresent())return ability.get().name();
        return c.technique(id).map(d -> c.text(d.translationKey())).orElse(id.toString());
    }
    static String category(PanelContext c, TechniqueDefinition d) {
        return d == null ? "未知" : c.text("screen.yuanxian.cultivation.category."+d.category().serializedName());
    }
    static String grade(PanelContext c, TechniqueDefinition d) {
        return d == null ? "未知" : c.text(TechniqueShelf.gradeNameKey(d.grade()));
    }
    static PanelAssets.Grade palette(PanelContext c, ResourceLocation id) {
        return PanelAssets.grade(c.technique(id).map(TechniqueDefinition::grade).orElse(0));
    }
    static PanelAssets.Icon icon(PanelContext c, ResourceLocation id) {
        if(ObservationRules.ID.equals(id) || FateAbilityDefinition.find(id).isPresent())return PanelAssets.category(TechniqueCategory.ACTIVE);
        return PanelAssets.technique(id,c.technique(id).map(TechniqueDefinition::category).orElse(null));
    }
    static void draw(GuiGraphics g, PanelContext c, Hit hit, boolean selected, boolean hovered) {
        var f = c.font();var d = c.technique(hit.id()).orElse(null);var p = palette(c,hit.id());
        int x=hit.x(), y=hit.y(), w=hit.width(), h=hit.height();
        g.fillGradient(x,y,x+w,y+h,0xFFF1EBDD,p.wash());
        g.renderOutline(x,y,w,h,p.edge());g.fill(x,y+h-2,x+w,y+h,p.color());
        if (hovered) g.renderOutline(x+1,y+1,w-2,h-2,InkPanelTheme.JADE);
        if (selected) {
            g.renderOutline(x+1,y+1,w-2,h-2,InkPanelTheme.TEXT);
            g.fill(x+w-7,y+h-8,x+w-1,y+h-2,InkPanelTheme.JADE);
            g.fill(x+w-6,y+h-6,x+w-5,y+h-4,CultivationInkTheme.PAPER);
            g.fill(x+w-5,y+h-5,x+w-3,y+h-4,CultivationInkTheme.PAPER);
        }
        int size=Math.min(30,Math.max(8,w-8));
        PanelAssets.draw(g,icon(c,hit.id()),x+(w-size)/2,y+12,size);
        String label=ObservationRules.ID.equals(hit.id())?"基础":FateAbilityDefinition.find(hit.id()).isPresent()?"天命":grade(c,d);int chipWidth=Math.min(w-4,f.width(label)+4);
        g.fill(x+w-chipWidth-2,y+2,x+w-2,y+12,p.color());
        g.drawString(f,InkPanelTheme.fit(f,label,chipWidth-3),x+w-chipWidth,y+2,p.label(),false);
        String bound = "";
        var config = ClientSkillBindings.current();
        if (config != null) {
            for (int i = 0; i < config.slots().size(); i++) {
                if (config.slots().get(i).equals(hit.id())) {
                    bound = com.example.myvillage.client.combat.ClientCombatKeyMappings.SKILL_KEYS.get(i).getTranslatedKeyMessage().getString();
                    break;
                }
            }
        }
        if (bound.isEmpty() && d != null && d.isCore()
                && c.profile().equippedHeart(HeartTechniques.slot(d)).filter(hit.id()::equals).isPresent()) {
            bound = HeartTechniques.slot(d) == HeartMethod.Slot.SPIRIT ? "主修" : "已装备";
        }
        if(!bound.isEmpty())g.drawString(f,InkPanelTheme.fit(f,bound,Math.max(1,w-chipWidth-5)),x+3,y+2,InkPanelTheme.JADE,false);
        String title=InkPanelTheme.fit(f,name(c,hit.id()),w-8);
        g.drawString(f,title,x+(w-f.width(title))/2,y+h-13,p.text(),false);
    }
    static List<Component> details(PanelContext c, ResourceLocation id) {
        var d=c.technique(id).orElse(null);var lines=new ArrayList<Component>();
        lines.add(Component.literal(name(c,id)).withStyle(s->s.withColor(palette(c,id).color()&0xFFFFFF)));
        if(ObservationRules.ID.equals(id)) {
            var rules=ObservationRules.bundled();
            lines.add(Component.literal("达到").append(com.example.myvillage.exploration.ExplorationRuntime.realmText(rules.unlock()))
                    .append("后自动获得，需自行分配技能槽位。"));
            lines.add(Component.literal("观察准星指向且无遮挡的目标，判断其修为。"));
            lines.add(Component.literal("相差不超过 "+rules.exactDistance()+" 个小境界时显示具体修为，其余显示固定区间。"));
            lines.add(Component.literal(String.format(Locale.ROOT,"观察距离 %.1f 格 · 冷却 %.1f 秒 · 不消耗灵力",rules.range(),rules.cooldownTicks()/20.0)));
            return lines;
        }
        var ability=FateAbilityDefinition.find(id);
        if(ability.isPresent()) {
            var a=ability.get();
            String fateName=java.util.Optional.ofNullable(c.fateDefinitions().get(a.requiredFate().toString()))
                    .map(fate->c.text(fate.translationKey())).orElse(a.requiredFate().toString());
            lines.add(Component.literal("天命主动能力 · "+fateName));
            lines.add(Component.literal(a.description()));
            long cap=c.profile()==null?0:SpiritualPower.cap(c.registries(),c.profile(),c.fates());
            lines.add(Component.literal(String.format(Locale.ROOT,"耗灵 %d（上限的 %s%%，向上取整，至少 %d） · 冷却 %.1f 秒",
                    a.cost(cap),java.math.BigDecimal.valueOf(a.costFraction()).movePointRight(2).stripTrailingZeros().toPlainString(),
                    a.minimumCost(),a.cooldownTicks()/20.0)));
            lines.add(Component.literal(String.format(Locale.ROOT,"范围半径 %.1f 格",a.radius())));
            if(!a.granted(c.fates(),c.registries()))lines.add(Component.literal("所需天命未确认、已禁用或不可用"));
            return lines;
        }
        if(d==null){lines.add(c.unavailable(id));return lines;}
        lines.add(Component.literal(grade(c,d)+" · "+category(c,d)));
        var progress=c.profile().learnedTechniques().get(id);
        if(progress!=null)lines.add(Component.translatable("screen.yuanxian.cultivation.mastery",progress.masteryPoints()));
        if(progress!=null)com.example.myvillage.cultivation.growth.TechniqueGrowthRules.bundled().find(id).ifPresent(growth->{
            lines.add(Component.literal("修习阶段："+com.example.myvillage.cultivation.growth.TechniqueGrowthRules.bundled().stageNames().get(growth.stage(progress.masteryPoints()))));
            lines.add(Component.literal("成长收益："+TechniqueGrowthPage.currentEffects(c,id)));
            lines.add(Component.literal("选择后点击「修习与参悟」查看潜力与承接关系"));
        });
        d.school().flatMap(school->ModCultivationRegistries.school(c.registries(),school))
                .map(SchoolDefinition::translationKey).ifPresent(key->lines.add(Component.translatable(key)));
        if(!d.elements().isEmpty())lines.add(Component.literal(String.join(" · ",d.elements().stream().map(e->c.elementName(e).getString()).toList())));
        for(var heritage:ModCultivationRegistries.heritagesContaining(c.registries(),id))
            lines.add(Component.literal(c.text(heritage.getValue().translationKey())+" "+TechniqueShelf.chainPosition(heritage.getValue().indexOf(id),heritage.getValue().techniques().size())));
        var conditions=new ArrayList<String>();
        d.requirements().minimumRealm().ifPresent(realm -> conditions.add(c.realmName(realm).getString()));
        d.requirements().minimumElementAptitudes().forEach((element,points)->conditions.add(c.elementName(element).getString()+String.format(Locale.ROOT," ≥ %.1f%%",points/100.0)));
        lines.add(Component.literal("修习条件："+(conditions.isEmpty()?"无":String.join(" · ",conditions))));
        HeartTechniques.method(d).ifPresent(heart -> heartDetails(lines, c, d, heart));
        ArtDefinition.find(id).ifPresent(art->{
            lines.add(Component.translatable("combat."+id.getNamespace()+".art."+id.getPath()+".description"));
            lines.add(Component.literal("灵力 "+art.qiCost()+" · 冷却 "+String.format(Locale.ROOT,"%.1f",art.cooldownTicks()/20.0)+" 秒"));
        });
        if(!d.isCore() && SkillBindingRules.kind(id,d)==SkillBindingRules.Kind.NONE)
            lines.add(Component.literal("此功法没有主动施放入口"));
        return lines;
    }

    private static void heartDetails(List<Component> lines, PanelContext c, TechniqueDefinition definition, HeartMethod heart) {
        lines.add(Component.literal("栏位 " + heart.slot().title() + " · " + heart.direction().title()));
        String elements = definition.elements().isEmpty() ? "五行"
                : String.join("、", definition.elements().stream().map(id -> c.elementName(id).getString()).toList());
        switch (heart) {
            case HeartMethod.Attack attack -> {
                bonus(lines, "常驻攻击", attack.attack(), " 点");
                bonus(lines, "常驻固定破防", attack.flatPenetration(), " 点");
                attack.extra().ifPresent(extra -> {
                    String action = attack.direction() == HeartMethod.Direction.SPELL ? elements + "法术"
                            : attack.direction().weapon() ? attack.direction().title() + "系" : "";
                    action += extra.paidOnly() ? "耗灵绝技" : extra.artOnly() ? "绝技"
                            : attack.direction().weapon() ? "攻击" : "";
                    bonus(lines, action + "命中额外伤害基数", extra.amount(), " 点");
                });
            }
            case HeartMethod.Defense defense -> {
                bonus(lines, "常驻生命上限", defense.maxHealth(), " 点");
                bonus(lines, "常驻肉身防御", defense.defense(), " 点");
                defense.extra().ifPresent(extra -> {
                    String hold = defense.direction().weapon() ? "持" + defense.direction().title() + "受到" : "受到";
                    String incoming = defense.direction() == HeartMethod.Direction.SPELL ? elements + "伤害" : switch (extra.guard()) {
                        case NONE -> "攻击";
                        case MELEE -> "近战攻击";
                        case RANGED -> "投射物或五行法术";
                    };
                    bonus(lines, hold + incoming + "时额外防御", extra.amount(), " 点");
                });
            }
            case HeartMethod.Spirit spirit -> {
                bonus(lines, "常驻灵力上限", spirit.capacity(), " 点");
                bonus(lines, "常驻自然回灵", spirit.recoveryQuarters() / 4.0, " 点/秒");
                bonus(lines, "每批基础修为", spirit.cultivation(), " 点");
                bonus(lines, "灵石修炼每批额外修为", spirit.stoneExtra(), " 点");
            }
        }
    }

    private static void bonus(List<Component> lines, String label, double value, String unit) {
        if (value > 0) lines.add(Component.literal(label + " +" + java.math.BigDecimal.valueOf(value)
                .stripTrailingZeros().toPlainString() + unit));
    }
}
