package com.example.myvillage.client.cultivation.panel;

import com.example.myvillage.client.cultivation.ClientCultivationIntentSender;
import com.example.myvillage.combat.SkillBindingRules;
import com.example.myvillage.cultivation.data.TechniqueCategory;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Learned catalogue: four category filters, shared symbols and six illustrated art overrides. */
public final class TechniquesPage extends PanelPage {
    private static final int FILTER_HEIGHT=22, DOCK_HEIGHT=39;
    private final Consumer<ResourceLocation> openSkills;
    private final List<PanelButton> filters=new ArrayList<>();
    private final List<TechniqueTiles.Hit> hits=new ArrayList<>();
    private PanelButton action;
    private TechniqueCategory filter;
    private ResourceLocation selected, hovered;
    private int px=-1,py=-1,x,width,dockY;
    private boolean visible,scrollToTop;
    public TechniquesPage(Consumer<ResourceLocation> openSkills) { this.openSkills=openSkills; }
    @Override public int topInset(){return FILTER_HEIGHT;}
    @Override public int init(int x,int y,int width,int height,Consumer<AbstractWidget> widgets){
        this.x=x;this.width=width;dockY=y+height-DOCK_HEIGHT;
        for(int i=0;i<5;i++){
            final int index=i;int left=x+i*width/5,right=x+(i+1)*width/5-3;
            String label=i==0?"全部":Component.translatable("screen.myvillage.cultivation.category."+TechniqueShelf.ORDER.get(i-1).serializedName()).getString();
            PanelButton b=new PanelButton(PanelButton.Style.NAV,InkPanelTheme.JADE,left,y,right-left,18,Component.literal(label),pressed->{
                filter=index==0?null:TechniqueShelf.ORDER.get(index-1);scrollToTop=true;selected=null;
            });
            b.setInk(true);filters.add(b);widgets.accept(b);
        }
        action=new PanelButton(PanelButton.Style.ACTION,InkPanelTheme.JADE,x+width-86,dockY+12,86,20,Component.literal("查看"),button->{
            var c=PanelContext.capture(net.minecraft.client.Minecraft.getInstance(),PanelFonts.get());
            if(selected==null||c.profile()==null||!c.profile().learnedTechniques().containsKey(selected))return;
            c.technique(selected).ifPresent(d->{
                if(d.isCore())ClientCultivationIntentSender.sendCoreSwitch(selected);
                else if(SkillBindingRules.kind(selected,d)!=SkillBindingRules.Kind.NONE)openSkills.accept(selected);
            });
        });
        action.setInk(true);action.setPrimary(true);widgets.accept(action);return DOCK_HEIGHT;
    }
    @Override public void setVisible(boolean value){visible=value;filters.forEach(b->b.visible=value);action.visible=value;}
    @Override public boolean takeScrollToTop(){boolean value=scrollToTop;scrollToTop=false;return value;}
    @Override public void pointer(int x,int y){px=x;py=y;}
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button!=0)return false;
        for(var h:hits)if(h.contains(x,y)){selected=h.id();return true;}return false;
    }
    @Override public void refresh(PanelContext c,boolean shown){
        for(int i=0;i<filters.size();i++){
            TechniqueCategory category=i==0?null:TechniqueShelf.ORDER.get(i-1);
            filters.get(i).setSelected(category==filter);
            filters.get(i).setIcon(i==0?PanelAssets.navigation("techniques"):PanelAssets.category(category));
        }
        var d=selected==null?null:c.technique(selected).orElse(null);
        boolean learned=c.profile()!=null&&selected!=null&&c.profile().learnedTechniques().containsKey(selected);
        boolean running=learned&&c.profile().activeCoreTechnique().filter(selected::equals).isPresent();
        boolean cast=d!=null&&SkillBindingRules.kind(selected,d)!=SkillBindingRules.Kind.NONE;
        action.active=shown&&learned&&d!=null&&((d.isCore()&&!running)||cast);
        action.setMessage(Component.literal(d==null?"未选择":d.isCore()?running?"正在主修":"设为主修":cast?"分配按键":"无需施放"));
    }
    @Override public int render(GuiGraphics g,PanelContext c,int x,int y,int width,int viewport){
        hits.clear();hovered=null;List<TechniqueShelf.Item> items=new ArrayList<>();
        for(var id:c.profile().learnedTechniques().keySet()){
            var d=c.technique(id).orElse(null);if(filter!=null&&(d==null||d.category()!=filter))continue;
            items.add(new TechniqueShelf.Item(id,d==null?null:d.category(),d==null?-1:d.grade(),TechniqueTiles.name(c,id)));
        }
        var ordered=TechniqueShelf.group(items).stream().flatMap(group->group.items().stream()).toList();
        if(selected==null||ordered.stream().noneMatch(item->item.id().equals(selected)))selected=ordered.isEmpty()?null:ordered.getFirst().id();
        if(ordered.isEmpty()){
            CultivationInkTheme.text(g,c.font(),"尚未学会此类功法",x+8,y+12,width-16,InkPanelTheme.MUTED);return 38;
        }
        var grid=TechniqueGridLayout.of(width,ordered.size());
        for(int i=0;i<ordered.size();i++){
            var hit=new TechniqueTiles.Hit(ordered.get(i).id(),x+grid.x(i),y+grid.y(i),grid.cellWidth(),TechniqueGridLayout.HEIGHT);
            hits.add(hit);boolean over=hit.contains(px,py);if(over)hovered=hit.id();
            TechniqueTiles.draw(g,c,hit,hit.id().equals(selected),over);
        }
        return grid.height();
    }
    @Override public void renderOverlay(GuiGraphics g,PanelContext c,int mouseX,int mouseY){
        if(!visible)return;g.fill(x,dockY,x+width,dockY+1,InkPanelTheme.DIVIDER);
        if(selected!=null){
            PanelAssets.draw(g,TechniqueTiles.icon(c,selected),x+1,dockY+7,26);
            var d=c.technique(selected).orElse(null);var p=TechniqueTiles.palette(c,selected);
            g.drawString(c.font(),InkPanelTheme.fit(c.font(),TechniqueTiles.name(c,selected),Math.max(1,width-122)),x+31,dockY+6,p.text(),false);
            String line=TechniqueTiles.grade(c,d)+" · "+TechniqueTiles.category(c,d);
            g.drawString(c.font(),InkPanelTheme.fit(c.font(),line,Math.max(1,width-122)),x+31,dockY+20,InkPanelTheme.MUTED,false);
        }
        if(hovered!=null)g.renderComponentTooltip(c.font(),TechniqueTiles.details(c,hovered),mouseX,mouseY);
    }
}
