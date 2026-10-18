package com.example.myvillage.client.commerce;

import com.example.myvillage.sim.runtime.commerce.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Paged shop view with native inventory slots; decisions and balances stay on the server. */
public final class CommerceScreen extends AbstractContainerScreen<CommerceMenu> {
    private static final String KEY="screen.myvillage.commerce.";
    private final List<Button> actions=new ArrayList<>();
    private int page,pending=-1;
    private Button previous,next;
    public CommerceScreen(CommerceMenu menu,Inventory inventory,Component title) {
        super(menu,inventory,title);imageWidth=CommerceMenu.WIDTH;imageHeight=CommerceMenu.HEIGHT;
    }
    @Override protected void init() {
        super.init();actions.clear();
        for(int i=0;i<4;i++) {
            final int row=i;
            actions.add(addRenderableWidget(Button.builder(Component.empty(),b->send(page*4+row))
                    .bounds(leftPos+244,topPos+36+i*20,48,18).build()));
        }
        previous=addRenderableWidget(Button.builder(Component.literal("<"),b->{page--;update();}).bounds(leftPos+202,topPos+117,28,16).build());
        next=addRenderableWidget(Button.builder(Component.literal(">"),b->{page++;update();}).bounds(leftPos+264,topPos+117,28,16).build());
        update();
    }
    private void send(int index) {
        if(index<0||index>=menu.offers().size()||pending==menu.sequence())return;
        pending=menu.sequence();
        PacketDistributor.sendToServer(new CommerceIntentPayload(menu.containerId,menu.token(),pending,index));
        update();
    }
    private void update() {
        page=Math.clamp(page,0,(menu.offers().size()-1)/4);
        for(int i=0;i<actions.size();i++) {
            int index=page*4+i;var b=actions.get(i);b.visible=index<menu.offers().size();
            if(!b.visible)continue;
            var offer=menu.offers().get(index);b.active=menu.stock(index)>0&&pending!=menu.sequence();
            b.setMessage(Component.translatable(KEY+offer.kind()));
            b.setTooltip(Tooltip.create(Component.translatable(KEY+"price",offer.price())));
        }
        if(previous!=null){previous.active=page>0;next.active=(page+1)*4<menu.offers().size();}
    }
    @Override protected void renderBg(GuiGraphics g,float delta,int mx,int my) {
        int x=leftPos,y=topPos;
        g.fill(x-1,y-1,x+imageWidth+1,y+imageHeight+1,0xFF8C7850);
        g.fill(x,y,x+imageWidth,y+imageHeight,0xFF242C28);
        g.fill(x+6,y+33,x+imageWidth-6,y+115,0xFF333F37);
        for(var s:menu.slots){g.fill(x+s.x-1,y+s.y-1,x+s.x+17,y+s.y+17,0xFF938570);g.fill(x+s.x,y+s.y,x+s.x+16,y+s.y+16,0xFF171E1A);}
        for(int row=0;row<4;row++) {
            int index=page*4+row;if(index>=menu.offers().size())break;
            var o=menu.offers().get(index);int yy=y+36+row*20;
            String name;
            if(o.kind().equals("rest"))name=Component.translatable(KEY+"rest_name").getString();
            else {
                ItemStack icon=new ItemStack(CommerceInventory.item(o.item()));g.renderItem(icon,x+10,yy);
                name=icon.getHoverName().getString()+(o.quantity()>1?" ×"+o.quantity():"");
            }
            g.drawString(font,font.plainSubstrByWidth(name,128),x+30,yy+1,0xFFDFE8D7,false);
            String detail=Component.translatable(KEY+"row_detail",o.price(),menu.stock(index)).getString();
            g.drawString(font,font.plainSubstrByWidth(detail,205),x+30,yy+10,0xFFBDB698,false);
        }
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my) {
        g.drawString(font,font.plainSubstrByWidth(title.getString(),284),8,7,0xFFE2D3A8,false);
        ItemStack currency=new ItemStack(CommerceInventory.item(menu.currency()));
        long coins=menu.slots.stream().map(slot->slot.getItem()).filter(s->!s.isEmpty()&&ItemStack.isSameItemSameComponents(s,currency)).mapToLong(ItemStack::getCount).sum();
        g.drawString(font,Component.translatable(KEY+"coins",coins),8,23,0xFFCCDEBF,false);
        g.drawString(font,(page+1)+"/"+((menu.offers().size()+3)/4),235,121,0xFFE2D3A8,false);
        String status=Component.translatable(KEY+"status."+menu.status()).getString();
        g.drawString(font,font.plainSubstrByWidth(status,284),8,134,0xFFD6C9AC,false);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta) {
        update();super.render(g,mx,my,delta);renderTooltip(g,mx,my);
        for(int row=0;row<4;row++) {
            int index=page*4+row;if(index>=menu.offers().size())break;
            var o=menu.offers().get(index);
            if(!o.kind().equals("rest")&&mx>=leftPos+10&&mx<leftPos+26&&my>=topPos+36+row*20&&my<topPos+52+row*20)
                g.renderTooltip(font,new ItemStack(CommerceInventory.item(o.item())),mx,my);
        }
    }
    @EventBusSubscriber(modid="myvillage",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class Registration {
        private Registration() { }
        @SubscribeEvent public static void screens(RegisterMenuScreensEvent event){event.register(CommerceMenus.SHOP.get(),CommerceScreen::new);}
    }
}
