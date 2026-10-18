package com.example.myvillage.client.commerce;

import com.example.myvillage.sim.runtime.commerce.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
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

/** Shared buy/sell view. Only bounded quantity intentions leave the client. */
public final class CommerceScreen extends AbstractContainerScreen<CommerceMenu> {
    private static final String KEY="screen.myvillage.commerce.";
    private static final int ROW_Y=60,ROWS=3;
    private final List<Button> actions=new ArrayList<>();
    private List<Integer> visibleOffers=List.of();
    private int page,pending=-1;
    private boolean selling;
    private Button previous,next,buyTab,sellTab,minus,plus;
    private EditBox quantity;
    public CommerceScreen(CommerceMenu menu,Inventory inventory,Component title) {
        super(menu,inventory,title);imageWidth=CommerceMenu.WIDTH;imageHeight=CommerceMenu.HEIGHT;
    }
    @Override protected void init() {
        String value=quantity==null?"1":quantity.getValue();
        super.init();actions.clear();
        for(int i=0;i<ROWS;i++) {
            final int row=i;
            actions.add(addRenderableWidget(Button.builder(Component.empty(),b->send(row))
                    .bounds(leftPos+244,topPos+ROW_Y+i*20,48,18).build()));
        }
        buyTab=addRenderableWidget(Button.builder(Component.translatable(KEY+"buy"),b->tab(false)).bounds(leftPos+8,topPos+36,54,18).build());
        sellTab=addRenderableWidget(Button.builder(Component.translatable(KEY+"sell"),b->tab(true)).bounds(leftPos+64,topPos+36,54,18).build());
        minus=addRenderableWidget(Button.builder(Component.literal("−"),b->adjust(-1)).bounds(leftPos+190,topPos+36,18,18).build());
        quantity=new EditBox(font,leftPos+211,topPos+36,60,18,Component.translatable(KEY+"quantity"));
        quantity.setMaxLength(5);
        quantity.setFilter(s->s.isEmpty()||s.matches("[0-9]{1,5}")&&Integer.parseInt(s)<=menu.maxQuantity());
        quantity.setValue(value);addRenderableWidget(quantity);
        quantity.setTooltip(Tooltip.create(Component.translatable(KEY+"quantity_hint",menu.maxQuantity())));
        plus=addRenderableWidget(Button.builder(Component.literal("+"),b->adjust(1)).bounds(leftPos+274,topPos+36,18,18).build());
        minus.setTooltip(Tooltip.create(Component.translatable(KEY+"decrease")));
        plus.setTooltip(Tooltip.create(Component.translatable(KEY+"increase")));
        previous=addRenderableWidget(Button.builder(Component.literal("<"),b->{page--;update();}).bounds(leftPos+202,topPos+123,28,16).build());
        next=addRenderableWidget(Button.builder(Component.literal(">"),b->{page++;update();}).bounds(leftPos+264,topPos+123,28,16).build());
        update();
    }
    private void tab(boolean sell) {
        selling=sell;page=0;quantity.setValue("1");update();
    }
    private int amount() {
        if(quantity==null||quantity.getValue().isEmpty())return 0;
        return Integer.parseInt(quantity.getValue());
    }
    private void adjust(int delta) {
        quantity.setValue(Integer.toString(Math.clamp(amount()+delta,1,menu.maxQuantity())));update();
    }
    private int index(int row) {
        int at=page*ROWS+row;return at<visibleOffers.size()?visibleOffers.get(at):-1;
    }
    private void send(int row) {
        int index=index(row);
        if(index<0||pending==menu.sequence())return;
        int count=menu.offers().get(index).kind().equals("rest")?1:amount();
        if(count<1||count>menu.maxQuantity())return;
        pending=menu.sequence();
        PacketDistributor.sendToServer(new CommerceIntentPayload(menu.containerId,menu.token(),pending,index,count));
        update();
    }
    private void update() {
        visibleOffers=java.util.stream.IntStream.range(0,menu.offers().size())
                .filter(i->menu.offers().get(i).kind().equals("sell")==selling).boxed().toList();
        page=Math.clamp(page,0,Math.max(0,(visibleOffers.size()-1)/ROWS));
        for(int i=0;i<actions.size();i++) {
            int index=index(i);var b=actions.get(i);b.visible=index>=0;
            if(!b.visible)continue;
            var offer=menu.offers().get(index);int count=offer.kind().equals("rest")?1:amount();
            // Show a precise server refusal for insufficient stock instead of a silent disabled button.
            b.active=count>0&&pending!=menu.sequence();
            b.setMessage(Component.translatable(KEY+offer.kind()));
            b.setTooltip(Tooltip.create(Component.translatable(KEY+"total_price",(long)offer.price()*count)));
        }
        if(previous!=null) {
            previous.active=page>0;next.active=(page+1)*ROWS<visibleOffers.size();
            buyTab.active=selling;sellTab.active=!selling;
            boolean goods=visibleOffers.stream().anyMatch(i->!menu.offers().get(i).kind().equals("rest"));
            quantity.setEditable(goods);quantity.active=goods;
            minus.active=goods&&amount()>1;plus.active=goods&&amount()<menu.maxQuantity();
        }
    }
    @Override protected void renderBg(GuiGraphics g,float delta,int mx,int my) {
        int x=leftPos,y=topPos;
        g.fill(x-1,y-1,x+imageWidth+1,y+imageHeight+1,0xFF8C7850);
        g.fill(x,y,x+imageWidth,y+imageHeight,0xFF242C28);
        g.fill(x+6,y+57,x+imageWidth-6,y+121,0xFF333F37);
        for(var s:menu.slots){g.fill(x+s.x-1,y+s.y-1,x+s.x+17,y+s.y+17,0xFF938570);g.fill(x+s.x,y+s.y,x+s.x+16,y+s.y+16,0xFF171E1A);}
        if(visibleOffers.isEmpty())g.drawString(font,Component.translatable(KEY+"empty_tab"),x+12,y+70,0xFFBDB698,false);
        for(int row=0;row<ROWS;row++) {
            int index=index(row);if(index<0)break;
            var o=menu.offers().get(index);int yy=y+ROW_Y+row*20;boolean rest=o.kind().equals("rest");
            int count=rest?1:amount();String name;
            if(rest)name=Component.translatable(KEY+"rest_name").getString();
            else {
                ItemStack icon=new ItemStack(CommerceInventory.item(o.item()));g.renderItem(icon,x+10,yy);
                name=icon.getHoverName().getString()+" ×"+((long)o.quantity()*count);
            }
            g.drawString(font,font.plainSubstrByWidth(name,205),x+30,yy+1,0xFFDFE8D7,false);
            String detail=Component.translatable(KEY+"batch_detail",(long)o.price()*count,menu.stock(index)).getString();
            g.drawString(font,font.plainSubstrByWidth(detail,205),x+30,yy+10,0xFFBDB698,false);
        }
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my) {
        g.drawString(font,font.plainSubstrByWidth(title.getString(),284),8,7,0xFFE2D3A8,false);
        var coins=CommerceInventory.balance(com.example.myvillage.spiritstone.SpiritStoneSources.open(minecraft.player).copies(),menu.currency());
        g.drawString(font,Component.translatable(KEY+"coins",coins.toString()),8,23,0xFFCCDEBF,false);
        g.drawString(font,Component.translatable(KEY+"quantity"),145,41,0xFFE2D3A8,false);
        g.drawString(font,(page+1)+"/"+Math.max(1,(visibleOffers.size()+ROWS-1)/ROWS),235,127,0xFFE2D3A8,false);
        String status=Component.translatable(KEY+"status."+(amount()==0&&quantity.active?"invalid_quantity":menu.status())).getString();
        g.drawString(font,font.plainSubstrByWidth(status,284),8,142,0xFFD6C9AC,false);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        // Inventory hotkeys must not close the shop while editing or pasting a quantity.
        if(quantity!=null&&quantity.isFocused()&&key!=256&&key!=258)return quantity.keyPressed(key,scan,modifiers);
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta) {
        update();super.render(g,mx,my,delta);renderTooltip(g,mx,my);
        for(int row=0;row<ROWS;row++) {
            int index=index(row);if(index<0)break;
            var o=menu.offers().get(index);
            if(!o.kind().equals("rest")&&mx>=leftPos+10&&mx<leftPos+26&&my>=topPos+ROW_Y+row*20&&my<topPos+ROW_Y+16+row*20)
                g.renderTooltip(font,new ItemStack(CommerceInventory.item(o.item())),mx,my);
        }
    }
    @EventBusSubscriber(modid="myvillage",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class Registration {
        private Registration() { }
        @SubscribeEvent public static void screens(RegisterMenuScreensEvent event){event.register(CommerceMenus.SHOP.get(),CommerceScreen::new);}
    }
}
