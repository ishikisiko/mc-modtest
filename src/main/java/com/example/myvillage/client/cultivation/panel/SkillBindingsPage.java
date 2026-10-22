package com.example.myvillage.client.cultivation.panel;

import com.example.myvillage.client.combat.ClientCombatKeyMappings;
import com.example.myvillage.client.combat.ClientSkillBindings;
import com.example.myvillage.combat.SkillBindings;
import com.example.myvillage.combat.SkillBindingRules;
import com.example.myvillage.combat.art.ArtDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import java.util.function.Consumer;

/** Learned active skills above a fixed six-key dock; every edit waits for the owning server. */
public final class SkillBindingsPage extends PanelPage {
    private static final int DOCK=88,ROW=30;
    private final List<PanelButton> keys=new ArrayList<>(),controls=new ArrayList<>(),shown=new ArrayList<>();
    private int selected,px=-1,py=-1,dockX,dockY,dockWidth;
    private boolean visible;
    @Override public int init(int x,int y,int width,int height,Consumer<AbstractWidget> widgets) {
        dockX=x;dockY=y+height-DOCK;dockWidth=width;
        int cell=(width-6)/3;
        for(int i=0;i<SkillBindings.SIZE;i++) {
            final int slot=i;
            var b=new PanelButton(PanelButton.Style.ACTION,PanelTheme.JADE,x+(i%3)*(cell+3),dockY+15+(i/3)*22,
                    cell,20,Component.empty(),button->{selected=slot;});keys.add(b);widgets.accept(b);
        }
        int cw=(width-9)/4;
        controls.add(button(x,dockY+64,cw,"清空此键",()->ClientSkillBindings.assign(selected,SkillBindings.EMPTY)));
        controls.add(button(x+cw+3,dockY+64,cw,"跟随武器",()->ClientSkillBindings.assign(selected,SkillBindings.AUTO)));
        controls.add(button(x+2*(cw+3),dockY+64,cw,"恢复默认",()->ClientSkillBindings.assign(SkillBindings.SIZE,SkillBindings.AUTO)));
        controls.add(button(x+3*(cw+3),dockY+64,width-3*(cw+3),"按键设置",()->{
            var mc=Minecraft.getInstance();mc.setScreen(new KeyBindsScreen(mc.screen,mc.options));
        }));controls.forEach(widgets);
        return DOCK+3;
    }
    private static PanelButton button(int x,int y,int w,String text,Runnable action){
        return new PanelButton(PanelButton.Style.ACTION,PanelTheme.GOLD,x,y,w,17,Component.literal(text),b->action.run());
    }
    @Override public void setVisible(boolean value){visible=value;keys.forEach(b->b.visible=value);controls.forEach(b->b.visible=value);}
    @Override public void refresh(PanelContext context,boolean shown) {
        var bindings=ClientSkillBindings.current();boolean ready=bindings!=null && !ClientSkillBindings.pending();
        for(int i=0;i<keys.size();i++) {
            var key=ClientCombatKeyMappings.SKILL_KEYS.get(i);String name=bindings==null?"同步中":bindingName(context,bindings.slots().get(i),i);
            String label=(selected==i?"▸ ":"")+key.getTranslatedKeyMessage().getString()+" · "+name;
            keys.get(i).setMessage(Component.literal(PanelTheme.fit(context.font(),label,keys.get(i).getWidth()-6)));
            keys.get(i).active=ready;
        }
        for(var c:controls)c.active=ready;
        if(controls.size()>1)controls.get(1).active=ready && selected<2;
        if(controls.size()>3)controls.get(3).active=true;
    }
    private static String bindingName(PanelContext context,ResourceLocation id,int slot) {
        if(id.equals(SkillBindings.EMPTY))return "未分配";
        if(id.equals(SkillBindings.AUTO))return "随武器"+(slot+1);
        String name=context.technique(id).map(d->context.text(d.translationKey())).orElse("未知技能");
        return context.profile()!=null && context.profile().learnedTechniques().containsKey(id)?name:"已遗忘";
    }
    @Override public void pointer(int x,int y){px=x;py=y;}
    @Override public boolean mouseClicked(double x,double y,int button){for(var b:shown)if(b.mouseClicked(x,y,button))return true;return false;}
    @Override public int render(GuiGraphics g,PanelContext c,int x,int y,int width,int viewportHeight) {
        shown.clear();var profile=c.profile();var bindings=ClientSkillBindings.current();
        var learned=profile.learnedTechniques().keySet().stream()
                .filter(id->SkillBindingRules.kind(id,c.technique(id).orElse(null))!=SkillBindingRules.Kind.NONE)
                .sorted(Comparator.comparing((ResourceLocation id)->c.technique(id).map(d->d.grade()).orElse(0)).reversed()
                        .thenComparing(id->c.technique(id).map(d->c.text(d.translationKey())).orElse(id.toString())))
                .toList();
        g.drawString(c.font(),"已学主动技能 · "+learned.size(),x+3,y+2,PanelTheme.GOLD_BRIGHT,false);
        g.drawString(c.font(),PanelTheme.fit(c.font(),"先选下方键位，再点技能右侧「分配」",width-6),x+3,y+15,PanelTheme.MUTED,false);
        int cursor=y+29;
        for(var id:learned) {
            var def=c.technique(id).orElseThrow();var kind=SkillBindingRules.kind(id,def);
            String bound="";
            if(bindings!=null)for(int i=0;i<SkillBindings.SIZE;i++)if(bindings.slots().get(i).equals(id))
                bound=" · "+ClientCombatKeyMappings.SKILL_KEYS.get(i).getTranslatedKeyMessage().getString();
            g.fill(x,cursor,x+width,cursor+ROW-2,PanelTheme.CARD);g.fill(x,cursor,x+2,cursor+ROW-2,PanelTheme.GOLD_DIM);
            g.drawString(c.font(),PanelTheme.fit(c.font(),c.text(def.translationKey())+bound,width-54),x+6,cursor+3,PanelTheme.TEXT,false);
            String hint=switch(kind) {
                case WAVE -> "持对应武器 · 按住蓄力，松开释放";
                case MOVEMENT -> "身法 · 按下闪避";
                case ART -> ArtDefinition.find(id).map(a->switch(a.family()){case "sword"->"剑诀 · 需持剑";case "spear"->"枪诀 · 需持枪";default->"体术 · 需持拳套";}).orElse("武技");
                default -> "";
            };
            g.drawString(c.font(),PanelTheme.fit(c.font(),hint,width-54),x+6,cursor+16,PanelTheme.MUTED,false);
            var b=button(x+width-44,cursor+5,41,"分配",()->ClientSkillBindings.assign(selected,id));
            b.active=bindings!=null && !ClientSkillBindings.pending();b.render(g,px,py,0);shown.add(b);cursor+=ROW;
        }
        if(learned.isEmpty()) {g.drawString(c.font(),"尚未学会可分配的主动技能",x+4,cursor+4,PanelTheme.MUTED,false);cursor+=23;}
        int other=profile.learnedTechniques().size()-learned.size();
        if(other>0){g.drawString(c.font(),PanelTheme.fit(c.font(),"另有 "+other+" 部被动或无施放入口的功法，见「功法」。",width-6),x+3,cursor+4,PanelTheme.FAINT,false);cursor+=18;}
        return cursor-y;
    }
    @Override public void renderOverlay(GuiGraphics g,PanelContext c,int mouseX,int mouseY) {
        if(!visible)return;
        var conflicts=ClientSkillBindings.conflicts(selected);var key=ClientCombatKeyMappings.SKILL_KEYS.get(selected);
        String text=key.isUnbound()?"此槽尚未设置物理按键":!conflicts.isEmpty()?"按键冲突："+String.join("、",conflicts):
                "当前键位 "+key.getTranslatedKeyMessage().getString()+"  "+ClientSkillBindings.feedback();
        g.drawString(c.font(),PanelTheme.fit(c.font(),text,dockWidth-3),dockX+2,dockY+2,
                conflicts.isEmpty()?PanelTheme.GOLD_BRIGHT:PanelTheme.RED,false);
        for(int i=0;i<keys.size();i++)if(keys.get(i).isMouseOver(mouseX,mouseY)) {
            var config=ClientSkillBindings.current();if(config==null)break;
            var errors=ClientSkillBindings.conflicts(i);
            String label=bindingName(c,config.slots().get(i),i)+(errors.isEmpty()?"":"；冲突："+String.join("、",errors));
            g.renderTooltip(c.font(),Component.literal(label),mouseX,mouseY);break;
        }
    }
}
