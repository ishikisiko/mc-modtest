package com.example.myvillage.client.courtyard;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.courtyard.CourtyardAssemblyItem;
import com.example.myvillage.network.CourtyardVariantPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Native variant cards; changing the style keeps the same item count and category. */
public final class CourtyardVariantScreen extends Screen {
    private final CourtyardAssemblyItem item;private final InteractionHand hand;private final List<ItemStack> previews=new ArrayList<>();
    private int left,top;
    public CourtyardVariantScreen(CourtyardAssemblyItem item,InteractionHand hand){super(Component.translatable("screen.myvillage.courtyard.title"));this.item=item;this.hand=hand;}
    @Override protected void init(){
        int count=item.assembly().family().variants().size();int rows=(count+2)/3;
        left=(width-306)/2;top=(height-(50+rows*61))/2;previews.clear();
        for(int i=0;i<count;i++){
            final int variant=i;previews.add(CourtyardAssemblyItem.stack(item,i));
            int x=left+5+(i%3)*100,y=top+31+(i/3)*61;
            addRenderableWidget(Button.builder(Component.translatable("variant.myvillage."+item.assembly().familyId()+"."+i),b->{
                PacketDistributor.sendToServer(new CourtyardVariantPayload(BuiltInRegistries.ITEM.getKey(item),hand==InteractionHand.OFF_HAND,variant));onClose();
            }).bounds(x,y+32,96,20).build());
        }
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float delta){
        renderBackground(g,mouseX,mouseY,delta);int rows=(previews.size()+2)/3;
        g.fill(left-3,top-3,left+309,top+44+rows*61,0xee262c28);
        g.drawCenteredString(font,title,width/2,top+6,0xf0e4c4);
        g.drawCenteredString(font,item.getDescription(),width/2,top+19,0xbac6bc);
        for(int i=0;i<previews.size();i++){
            int x=left+5+(i%3)*100,y=top+31+(i/3)*61;var v=item.assembly().family().variant(i);
            g.fill(x,y,x+96,y+31,0xff3c443c);g.renderItem(previews.get(i),x+8,y+7);
            g.drawString(font,v.width()+"×"+v.height()+"×"+v.depth(),x+34,y+12,0xdfddcd,false);
        }
        g.drawCenteredString(font,Component.translatable("screen.myvillage.courtyard.hint"),width/2,top+30+rows*61,0xbac6bc);
        super.render(g,mouseX,mouseY,delta);
    }
    @Override public boolean isPauseScreen(){return false;}
    @EventBusSubscriber(modid=MyVillageMod.MOD_ID,value=Dist.CLIENT)
    public static final class Input {
        @SubscribeEvent public static void use(InputEvent.InteractionKeyMappingTriggered event){
            var mc=Minecraft.getInstance();
            if(!event.isUseItem()||mc.screen!=null||mc.player==null||!mc.player.isShiftKeyDown())return;
            var hand=event.getHand();var stack=mc.player.getItemInHand(hand);
            if(stack.getItem() instanceof CourtyardAssemblyItem item){event.setCanceled(true);event.setSwingHand(false);mc.setScreen(new CourtyardVariantScreen(item,hand));}
        }
        @SubscribeEvent public static void inventory(net.neoforged.neoforge.client.event.ScreenEvent.Init.Post event){
            if(Boolean.getBoolean("myvillage.courtyard.evidence")&&event.getScreen() instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen){
                var tab=com.example.myvillage.courtyard.CourtyardContent.TAB.get();
                org.slf4j.LoggerFactory.getLogger(CourtyardVariantScreen.class).info("COURTYARD_CREATIVE display={} search={}",tab.getDisplayItems().size(),tab.getSearchTabDisplayItems().size());
            }
        }
    }
}
