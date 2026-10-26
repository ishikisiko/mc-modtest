package com.example.myvillage.client.cultivation.panel;

import com.example.myvillage.client.combat.ClientSkillBindings;
import com.example.myvillage.combat.SkillBindingRules;
import com.example.myvillage.combat.art.ArtDefinition;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.SchoolDefinition;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.cultivation.data.TechniqueCategory;
import com.example.myvillage.cultivation.fate.ability.FateAbilityDefinition;
import com.example.myvillage.cultivation.qi.SpiritualPower;
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
        var ability=FateAbilityDefinition.find(id);
        if(ability.isPresent())return ability.get().name();
        return c.technique(id).map(d -> c.text(d.translationKey())).orElse(id.toString());
    }
    static String category(PanelContext c, TechniqueDefinition d) {
        return d == null ? "未知" : c.text("screen.myvillage.cultivation.category."+d.category().serializedName());
    }
    static String grade(PanelContext c, TechniqueDefinition d) {
        return d == null ? "未知" : c.text(TechniqueShelf.gradeNameKey(d.grade()));
    }
    static PanelAssets.Grade palette(PanelContext c, ResourceLocation id) {
        return PanelAssets.grade(c.technique(id).map(TechniqueDefinition::grade).orElse(0));
    }
    static PanelAssets.Icon icon(PanelContext c, ResourceLocation id) {
        if(FateAbilityDefinition.find(id).isPresent())return PanelAssets.category(TechniqueCategory.ACTIVE);
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
        String label=FateAbilityDefinition.find(hit.id()).isPresent()?"天命":grade(c,d);int chipWidth=Math.min(w-4,f.width(label)+4);
        g.fill(x+w-chipWidth-2,y+2,x+w-2,y+12,p.color());
        g.drawString(f,InkPanelTheme.fit(f,label,chipWidth-3),x+w-chipWidth,y+2,p.label(),false);
        String bound="";var config=ClientSkillBindings.current();
        if(config!=null)for(int i=0;i<config.slots().size();i++)if(config.slots().get(i).equals(hit.id()))
            bound=com.example.myvillage.client.combat.ClientCombatKeyMappings.SKILL_KEYS.get(i).getTranslatedKeyMessage().getString();
        if(c.profile().activeCoreTechnique().filter(hit.id()::equals).isPresent())bound="主修";
        if(!bound.isEmpty())g.drawString(f,InkPanelTheme.fit(f,bound,Math.max(1,w-chipWidth-5)),x+3,y+2,InkPanelTheme.JADE,false);
        String title=InkPanelTheme.fit(f,name(c,hit.id()),w-8);
        g.drawString(f,title,x+(w-f.width(title))/2,y+h-13,p.text(),false);
    }
    static List<Component> details(PanelContext c, ResourceLocation id) {
        var d=c.technique(id).orElse(null);var lines=new ArrayList<Component>();
        lines.add(Component.literal(name(c,id)).withStyle(s->s.withColor(palette(c,id).color()&0xFFFFFF)));
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
        if(progress!=null)lines.add(Component.translatable("screen.myvillage.cultivation.mastery",progress.masteryPoints()));
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
        d.requirements().minimumRealm().ifPresent(realm->conditions.add(d.requirements().minimumStage()
                .map(stage->c.stageName(realm,stage).getString()).orElse(c.realmName(realm).getString())));
        d.requirements().minimumElementAffinities().forEach((element,points)->conditions.add(c.elementName(element).getString()+String.format(Locale.ROOT," ≥ %.1f%%",points/100.0)));
        lines.add(Component.literal("修习条件："+(conditions.isEmpty()?"无":String.join(" · ",conditions))));
        ArtDefinition.find(id).ifPresent(art->{
            lines.add(Component.translatable("combat."+id.getNamespace()+".art."+id.getPath()+".description"));
            lines.add(Component.literal("灵力 "+art.qiCost()+" · 冷却 "+String.format(Locale.ROOT,"%.1f",art.cooldownTicks()/20.0)+" 秒"));
        });
        if(!d.isCore() && SkillBindingRules.kind(id,d)==SkillBindingRules.Kind.NONE)
            lines.add(Component.literal("此功法没有主动施放入口"));
        return lines;
    }
}
