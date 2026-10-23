package com.example.myvillage.client.cultivation.panel;

import com.example.myvillage.client.combat.ClientCombatKeyMappings;
import com.example.myvillage.client.combat.ClientSkillBindings;
import com.example.myvillage.combat.SkillBindings;
import com.example.myvillage.combat.SkillBindingRules;
import com.example.myvillage.cultivation.fate.ability.FateAbilityDefinition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Active skills only. The fixed six-slot dock displays server-confirmed bindings, never optimistic local state. */
public final class SkillBindingsPage extends PanelPage {
    private final List<PanelButton> keys=new ArrayList<>(),controls=new ArrayList<>();
    private final List<TechniqueTiles.Hit> hits=new ArrayList<>();
    private PanelButton assign;
    private ResourceLocation selectedTechnique,hovered;
    private int selectedSlot,px=-1,py=-1,dockX,dockY,width,topY,dockHeight;
    private boolean visible,scrollToTop;

    public void selectTechnique(ResourceLocation id){selectedTechnique=id;scrollToTop=true;}
    @Override public int topInset(){return 18;}
    @Override public boolean takeScrollToTop(){boolean value=scrollToTop;scrollToTop=false;return value;}
    @Override public int init(int x,int y,int width,int height,Consumer<AbstractWidget> widgets){
        this.width=width;dockX=x;topY=y;
        int columns=width>=350?6:3,rows=6/columns;
        dockHeight=15+rows*24+22;dockY=y+height-dockHeight;
        for(int i=0;i<SkillBindings.SIZE;i++){
            final int slot=i;int left=x+(i%columns)*width/columns,right=x+(i%columns+1)*width/columns-3;
            var button=button(left,dockY+14+(i/columns)*24,right-left,22,"",()->selectedSlot=slot);
            keys.add(button);widgets.accept(button);
        }
        int controlsY=dockY+15+rows*24;
        int assignWidth=Math.max(66,width/4);
        assign=button(x,controlsY,assignWidth,19,"分配",()->{
            var c=PanelContext.capture(Minecraft.getInstance(),PanelFonts.get());
            if(selectedTechnique!=null&&assignable(c,selectedTechnique))
                ClientSkillBindings.assign(selectedSlot,selectedTechnique);
        });
        assign.setPrimary(true);widgets.accept(assign);
        int rest=width-assignWidth-4,buttonWidth=(rest-9)/4;
        controls.add(button(x+assignWidth+4,controlsY,buttonWidth,19,"清空",()->ClientSkillBindings.assign(selectedSlot,SkillBindings.EMPTY)));
        controls.add(button(x+assignWidth+7+buttonWidth,controlsY,buttonWidth,19,"随武器",()->ClientSkillBindings.assign(selectedSlot,SkillBindings.AUTO)));
        controls.add(button(x+assignWidth+10+buttonWidth*2,controlsY,buttonWidth,19,"默认",()->ClientSkillBindings.assign(SkillBindings.SIZE,SkillBindings.AUTO)));
        controls.add(button(x+assignWidth+13+buttonWidth*3,controlsY,buttonWidth,19,"键位",()->{
            var mc=Minecraft.getInstance();mc.setScreen(new KeyBindsScreen(mc.screen,mc.options));
        }));
        controls.forEach(widgets);return dockHeight;
    }
    private static PanelButton button(int x,int y,int w,int h,String text,Runnable action){
        var b=new PanelButton(PanelButton.Style.ACTION,InkPanelTheme.JADE,x,y,w,h,Component.literal(text),pressed->action.run());
        b.setInk(true);return b;
    }
    @Override public void setVisible(boolean value){visible=value;keys.forEach(b->b.visible=value);controls.forEach(b->b.visible=value);assign.visible=value;}
    @Override public void refresh(PanelContext c,boolean shown){
        var bindings=ClientSkillBindings.current();boolean ready=bindings!=null&&!ClientSkillBindings.pending();
        for(int i=0;i<keys.size();i++){
            var button=keys.get(i);var key=ClientCombatKeyMappings.SKILL_KEYS.get(i);
            ResourceLocation id=bindings==null?SkillBindings.EMPTY:bindings.slots().get(i);
            String name=bindings==null?"同步中":id.equals(SkillBindings.EMPTY)?"空":id.equals(SkillBindings.AUTO)?"随武器":"";
            button.setMessage(Component.literal(key.getTranslatedKeyMessage().getString()+(name.isEmpty()?"":" · "+name)));
            button.setIcon(id.equals(SkillBindings.EMPTY)||id.equals(SkillBindings.AUTO)?null:TechniqueTiles.icon(c,id));
            button.setSelected(i==selectedSlot);button.active=shown&&ready;
        }
        boolean available=selectedTechnique!=null&&assignable(c,selectedTechnique);
        assign.active=shown&&ready&&available;
        assign.setMessage(Component.literal("分配到 "+ClientCombatKeyMappings.SKILL_KEYS.get(selectedSlot).getTranslatedKeyMessage().getString()));
        for(var b:controls)b.active=shown&&ready;
        // Key settings never depend on a synchronized binding snapshot.
        controls.getLast().active=shown;
    }
    @Override public void pointer(int x,int y){px=x;py=y;}
    private static boolean assignable(PanelContext c,ResourceLocation id) {
        return c.profile()!=null&&SkillBindingRules.assignable(id,c.profile().learnedTechniques().keySet(),
                c.technique(id).orElse(null),c.fates(),c.fateDefinitions());
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button!=0)return false;
        for(var hit:hits)if(hit.contains(x,y)){selectedTechnique=hit.id();return true;}return false;
    }
    @Override public int render(GuiGraphics g,PanelContext c,int x,int y,int width,int viewport){
        hits.clear();hovered=null;
        var candidates=new java.util.HashSet<>(c.profile().learnedTechniques().keySet());
        FateAbilityDefinition.all().stream().filter(ability->ability.granted(c.fates(),c.registries()))
                .map(FateAbilityDefinition::id).forEach(candidates::add);
        var learned=candidates.stream().filter(id->assignable(c,id))
                .sorted(Comparator.comparingInt((ResourceLocation id)->c.technique(id).map(d->d.grade()).orElse(0)).reversed()
                        .thenComparing(id->TechniqueTiles.name(c,id))).toList();
        if(selectedTechnique==null||!learned.contains(selectedTechnique))selectedTechnique=learned.isEmpty()?null:learned.getFirst();
        if(learned.isEmpty()){
            CultivationInkTheme.text(g,c.font(),"尚无可分配的主动技能",x+5,y+12,width-10,InkPanelTheme.MUTED);return 36;
        }
        var grid=TechniqueGridLayout.of(width,learned.size());
        for(int i=0;i<learned.size();i++){
            var hit=new TechniqueTiles.Hit(learned.get(i),x+grid.x(i),y+grid.y(i),grid.cellWidth(),TechniqueGridLayout.HEIGHT);
            hits.add(hit);boolean over=hit.contains(px,py);if(over)hovered=hit.id();
            TechniqueTiles.draw(g,c,hit,hit.id().equals(selectedTechnique),over);
        }
        return grid.height();
    }
    @Override public void renderOverlay(GuiGraphics g,PanelContext c,int mouseX,int mouseY){
        if(!visible)return;
        CultivationInkTheme.text(g,c.font(),selectedTechnique==null?"可用主动技能":TechniqueTiles.name(c,selectedTechnique),dockX+2,topY+3,width-4,
                selectedTechnique==null?InkPanelTheme.MUTED:TechniqueTiles.palette(c,selectedTechnique).text());
        g.fill(dockX,dockY,dockX+width,dockY+1,InkPanelTheme.DIVIDER);
        var conflicts=ClientSkillBindings.conflicts(selectedSlot);var key=ClientCombatKeyMappings.SKILL_KEYS.get(selectedSlot);
        String feedback=key.isUnbound()?"此槽尚未设置物理按键":!conflicts.isEmpty()?"按键冲突："+String.join("、",conflicts):"选择键位 · "+ClientSkillBindings.feedback();
        CultivationInkTheme.text(g,c.font(),feedback,dockX+2,dockY+3,width-4,conflicts.isEmpty()?InkPanelTheme.MUTED:InkPanelTheme.RED);
        var bindings=ClientSkillBindings.current();
        if(bindings!=null)for(int i=0;i<keys.size();i++){
            var button=keys.get(i);var id=bindings.slots().get(i);
            if(!id.equals(SkillBindings.EMPTY)&&!id.equals(SkillBindings.AUTO)){
                var p=TechniqueTiles.palette(c,id);g.fill(button.getX()+1,button.getY()+button.getHeight()-2,button.getX()+button.getWidth()-1,button.getY()+button.getHeight()-1,p.color());
            }
            if(button.isMouseOver(mouseX,mouseY)){
                String name=id.equals(SkillBindings.EMPTY)?"空槽":id.equals(SkillBindings.AUTO)?"跟随所持武器":TechniqueTiles.name(c,id);
                var errors=ClientSkillBindings.conflicts(i);
                g.renderTooltip(c.font(),Component.literal(name+(errors.isEmpty()?"":"；冲突："+String.join("、",errors))),mouseX,mouseY);
                return;
            }
        }
        if(hovered!=null)g.renderComponentTooltip(c.font(),TechniqueTiles.details(c,hovered),mouseX,mouseY);
    }
}
