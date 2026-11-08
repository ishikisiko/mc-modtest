package com.example.myvillage.client.cultivation.panel;

import com.example.myvillage.cultivation.fate.FateDefinition;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Confirmed fates as compact paper cards; full effects remain in the hover layer. */
public final class FatesPage extends PanelPage {
    private int px=-1,py=-1;
    private List<Component> hovered=List.of();
    private static String half(double value) { return value == Math.rint(value) ? String.format(java.util.Locale.ROOT,"%.0f",value) : String.format(java.util.Locale.ROOT,"%.1f",value); }
    @Override public void pointer(int x,int y){px=x;py=y;}
    @Override public int render(GuiGraphics g,PanelContext c,int x,int y,int width,int viewport){
        hovered=List.of();
        if(!c.fates().confirmed()){
            PanelAssets.draw(g,PanelAssets.navigation("fates"),x+(width-48)/2,y+10,48);
            CultivationInkTheme.centered(g,c.font(),c.text("awakening.myvillage.visit_stele"),x,y+68,width,false,InkPanelTheme.MUTED);
            return 90;
        }
        CultivationInkTheme.text(g,c.font(),c.text("fate.myvillage.fixed"),x+3,y+2,width-6,InkPanelTheme.MUTED);
        List<String> selected=new ArrayList<>(c.fates().selected());
        int columns=width>=330?3:1,gap=7,cardWidth=(width-(columns-1)*gap)/columns,cardHeight=126;
        for(int i=0;i<selected.size();i++){
            String id=selected.get(i);FateDefinition def=c.fateDefinitions().get(id);
            int left=x+(i%columns)*(cardWidth+gap),top=y+20+(i/columns)*(cardHeight+gap);
            int color=def==null?InkPanelTheme.RED:FatePresentation.color(def.tier());
            g.fill(left,top,left+cardWidth,top+cardHeight,InkPanelTheme.CARD);
            g.renderOutline(left,top,cardWidth,cardHeight,InkPanelTheme.CARD_BORDER);
            g.fill(left+1,top,left+cardWidth-1,top+3,color);
            PanelAssets.draw(g,PanelAssets.navigation("fates"),left+(cardWidth-38)/2,top+12,38);
            if(def==null||c.profile()==null||!def.active(c.profile().spiritualRoot())){
                CultivationInkTheme.text(g,c.font(),c.unavailable(ResourceLocation.parse(id)).getString(),left+6,top+60,cardWidth-12,InkPanelTheme.MUTED);
                continue;
            }
            CultivationInkTheme.centered(g,c.font(),c.text(def.translationKey()),left+4,top+57,cardWidth-8,false,InkPanelTheme.TEXT);
            var lines=c.font().split(Component.translatable(def.descriptionKey()),cardWidth-16);
            for(int row=0;row<Math.min(3,lines.size());row++)g.drawString(c.font(),lines.get(row),left+8,top+76+row*11,InkPanelTheme.MUTED,false);
            if(px>=left&&px<left+cardWidth&&py>=top&&py<top+cardHeight)hovered=FatePresentation.description(def);
        }
        int end=20+((selected.size()+columns-1)/columns)*(cardHeight+gap);
        if (c.fates().confirmed() && c.fates().selected().contains("myvillage:everlasting_embryo")) {
            var value = com.example.myvillage.client.cultivation.ClientCultivationState.snapshot().orElseThrow().everlasting();
            var rules = com.example.myvillage.cultivation.fate.EverlastingRules.get();
            var annual = c.profile() == null ? com.example.myvillage.cultivation.fate.EverlastingRules.Annual.ZERO
                    : rules.at(c.profile().realmId().toString());
            for (String line : List.of(
                    "长生累计：生命上限 +" + half(value.health().points(rules.creditStep()))
                            + "，肉身防御 +" + half(value.defense().points(rules.creditStep()))
                            + "，自身攻击 +" + half(value.attack().points(rules.creditStep())),
                    annual.empty() ? "凡人阶段只延长寿元，进入炼气后开始成长。"
                            : "当前每年：生命 +" + half(annual.health() * rules.creditStep())
                            + "，肉身防御 +" + half(annual.defense() * rules.creditStep())
                            + "，自身攻击 +" + half(annual.attack() * rules.creditStep()),
                    "各项攒满 " + half(rules.creditStep()) + " 点入账，未满进度在死亡与退出后保留。")) {
                for (var row : c.font().split(Component.literal(line),width-6)) {
                    g.drawString(c.font(),row,x+3,y+end,InkPanelTheme.TEXT,false); end += 11;
                }
                end += 3;
            }
        }
        for(var line:com.example.myvillage.client.cultivation.ClientSpiritBodies.details()) {
            for(var row:c.font().split(line,width-6)) { g.drawString(c.font(),row,x+3,y+end,InkPanelTheme.TEXT,false);end+=11; }
            end+=3;
        }
        return end;
    }
    @Override public void renderOverlay(GuiGraphics g,PanelContext c,int x,int y){
        if(!hovered.isEmpty())g.renderComponentTooltip(c.font(),hovered,x,y);
    }
}
