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
            if(def==null||c.profile()==null||!def.eligible(c.profile().spiritualRoot())){
                CultivationInkTheme.text(g,c.font(),c.unavailable(ResourceLocation.parse(id)).getString(),left+6,top+60,cardWidth-12,InkPanelTheme.MUTED);
                continue;
            }
            CultivationInkTheme.centered(g,c.font(),c.text(def.translationKey()),left+4,top+57,cardWidth-8,false,InkPanelTheme.TEXT);
            var lines=c.font().split(Component.translatable(def.descriptionKey()),cardWidth-16);
            for(int row=0;row<Math.min(3,lines.size());row++)g.drawString(c.font(),lines.get(row),left+8,top+76+row*11,InkPanelTheme.MUTED,false);
            if(px>=left&&px<left+cardWidth&&py>=top&&py<top+cardHeight)hovered=FatePresentation.description(def);
        }
        return 20+((selected.size()+columns-1)/columns)*(cardHeight+gap);
    }
    @Override public void renderOverlay(GuiGraphics g,PanelContext c,int x,int y){
        if(!hovered.isEmpty())g.renderComponentTooltip(c.font(),hovered,x,y);
    }
}
