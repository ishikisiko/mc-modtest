package com.example.myvillage.client.forging;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.forging.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import java.util.*;

public final class ForgingScreen extends AbstractContainerScreen<ForgingMenu> {
    private static final String KEY="screen.myvillage.forging.";
    private final List<Button> recipes=new ArrayList<>(),traits=new ArrayList<>();
    private Button forge,repair;
    private int recipePage,traitPage;
    public ForgingScreen(ForgingMenu menu,Inventory inventory,Component title){
        super(menu,inventory,title);imageWidth=ForgingMenu.WIDTH;imageHeight=ForgingMenu.HEIGHT;
        inventoryLabelX=8;inventoryLabelY=160;
    }
    private void send(int action){if(minecraft!=null&&minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action);}
    private Component itemName(String id){return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)).getDescription();}
    @Override protected void init(){
        super.init();recipes.clear();traits.clear();
        addRenderableWidget(Button.builder(Component.translatable("screen.myvillage.infusion.title"),b->send(ForgingMenu.INFUSE))
                .bounds(leftPos+204,topPos+4,88,18).build());
        for(int i=0;i<ForgingRules.get().recipes().size();i++){
            final int index=i;
            recipes.add(addRenderableWidget(Button.builder(itemName(ForgingRules.get().recipes().get(i).output()),b->send(ForgingMenu.RECIPE_BASE+index))
                    .bounds(leftPos+8,topPos+30+(i%2)*24,96,20).build()));
        }
        for(int i=0;i<ForgeMark.TRAITS.size();i++){
            final int index=i;String id=ForgeMark.TRAITS.get(i);
            var b=Button.builder(Component.translatable("forging.myvillage.trait."+id),v->send(ForgingMenu.TRAIT_BASE+index))
                    .bounds(leftPos+8,topPos+84+(i%3)*22,96,20).build();
            b.setTooltip(Tooltip.create(Component.translatable(KEY+"trait."+id)));traits.add(addRenderableWidget(b));
        }
        addRenderableWidget(Button.builder(Component.literal("器谱 ›"),b->recipePage=(recipePage+1)%((recipes.size()+1)/2)).bounds(leftPos+8,topPos+20,96,10).build());
        addRenderableWidget(Button.builder(Component.literal("专长 ›"),b->traitPage=(traitPage+1)%((traits.size()+2)/3)).bounds(leftPos+8,topPos+74,96,10).build());
        forge=addRenderableWidget(Button.builder(Component.translatable(KEY+"forge"),b->send(ForgingMenu.FORGE))
                .bounds(leftPos+112,topPos+128,86,18).build());
        forge.setTooltip(Tooltip.create(Component.translatable(KEY+"forge_hint")));
        repair=addRenderableWidget(Button.builder(Component.translatable(KEY+"repair"),b->send(ForgingMenu.REPAIR))
                .bounds(leftPos+204,topPos+128,88,18).build());
        var hint=Component.translatable(KEY+"repair_hint");
        ForgingRules.get().repairCosts().forEach((id,n)->hint.append("\n").append(ForgingIngredients.description(id)).append(" ×"+n));
        repair.setTooltip(Tooltip.create(hint));updateButtons();
    }
    private void updateButtons(){
        if(forge==null)return;
        for(int i=0;i<recipes.size();i++){
            var b=recipes.get(i);b.visible=i/2==recipePage;b.active=menu.knows(i);
            b.setMessage(Component.literal(menu.recipeIndex()==i?"• ":"").append(itemName(ForgingRules.get().recipes().get(i).output())));
            b.setTooltip(b.active?null:Tooltip.create(Component.translatable(KEY+"learn_hint")));
        }
        for(int i=0;i<traits.size();i++){
            String id=ForgeMark.TRAITS.get(i);var b=traits.get(i);b.visible=i/3==traitPage;b.active=menu.recipe().traits().contains(id);
            b.setMessage(Component.literal(menu.traitIndex()==i?"• ":"").append(Component.translatable("forging.myvillage.trait."+id)));
            b.setTooltip(Tooltip.create(Component.translatable(KEY+(b.active?"trait."+id:"unsupported"))));
        }
        forge.active=menu.canForge();repair.active=menu.canRepair();
    }
    @Override protected void renderBg(GuiGraphics g,float delta,int mx,int my){
        int x=leftPos,y=topPos;
        g.fill(x-2,y-2,x+imageWidth+2,y+imageHeight+2,0xFF786346);
        g.fill(x,y,x+imageWidth,y+imageHeight,0xFF252823);
        g.fill(x+108,y+24,x+296,y+124,0xFF343A31);
        for(var s:menu.slots){g.fill(x+s.x-1,y+s.y-1,x+s.x+17,y+s.y+17,0xFF8B826C);g.fill(x+s.x,y+s.y,x+s.x+16,y+s.y+16,0xFF171D18);}
        int i=0;
        for(var e:ForgingRules.get().costs(menu.recipe(),menu.trait()).entrySet()){
            var inventory=com.example.myvillage.spiritstone.SpiritStoneSources.open(minecraft.player).copies();
            ItemStack stack=ForgingIngredients.display(e.getKey(),inventory);
            int px=x+116+(i++)*42;g.renderItem(stack,px,y+52);
            var count=ForgingIngredients.available(e.getKey(),inventory);
            g.drawString(font,"×"+e.getValue(),px,y+71,count.compareTo(java.math.BigInteger.valueOf(e.getValue()))>=0?0xFFC5D8B7:0xFFF08C76,false);
        }
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){
        g.drawString(font,title,10,8,0xFFE5D5AF,false);
        Component cap=menu.qualityCap()>0?Component.translatable(KEY+"cap",ForgingQualityDisplay.name(menu.qualityCap())):Component.translatable(KEY+"awaken");
        g.drawString(font,cap,112,26,0xFFD9B568,false);
        g.drawString(font,Component.translatable(KEY+"costs"),112,40,0xFFC5C6B7,false);
        g.drawWordWrap(font,Component.translatable(KEY+"status."+menu.status()),112,86,116,0xFFE5D5AF);
        g.drawString(font,Component.translatable(KEY+"slots"),240,84,0xFFC5C6B7,false);
        g.drawString(font,playerInventoryTitle,inventoryLabelX,inventoryLabelY,0xFFC5C6B7,false);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        updateButtons();super.render(g,mx,my,delta);renderTooltip(g,mx,my);
        int i=0;
        for(var e:ForgingRules.get().costs(menu.recipe(),menu.trait()).entrySet()){
            int x=leftPos+116+(i++)*42,y=topPos+52;
            if(mx>=x&&mx<x+32&&my>=y&&my<y+28)g.renderTooltip(font,ForgingIngredients.description(e.getKey()).copy().append(" ×"+e.getValue()),mx,my);
        }
    }
    @EventBusSubscriber(modid=MyVillageMod.MOD_ID,value=Dist.CLIENT)
    public static final class Events{
        @SubscribeEvent public static void screens(RegisterMenuScreensEvent event){
            event.register(ForgingMenus.TABLE.get(),ForgingScreen::new);
            event.register(ForgingMenus.WORKSHOP.get(),WorkshopScreen::new);
            event.register(ForgingMenus.INFUSION.get(),CopperInfusionScreen::new);
        }
    }
}
