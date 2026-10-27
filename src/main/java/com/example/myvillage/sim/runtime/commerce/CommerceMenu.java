package com.example.myvillage.sim.runtime.commerce;

import com.example.myvillage.entity.npc.NpcEntity;
import com.example.myvillage.sim.commerce.CommerceRules;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.example.myvillage.sim.runtime.avatar.WorldSimAvatars;
import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Private view of a shared merchant. No temporary item storage and no close-time duplication. */
public final class CommerceMenu extends AbstractContainerMenu {
    public static final int WIDTH=300,HEIGHT=240;
    public static final List<String> STATUS=List.of("ok","bought","sold","rested","off_duty","unavailable",
            "out_of_stock","cannot_buy","insufficient_funds","missing_items","inventory_full","rest_cooldown",
            "no_recovery","stale","paused","treasury_full","no_room","hostile","busy","invalid_quantity");
    private final Inventory inventory;
    private final NpcEntity npc;
    private final long token;
    private final String currency;
    private final int maxQuantity;
    private final List<CommerceRules.Offer> offers;
    private final ContainerData data;
    private int lastActionTick = Integer.MIN_VALUE;
    public CommerceMenu(int id,Inventory inventory,RegistryFriendlyByteBuf buf) {
        this(id,inventory,null,buf.readLong(),buf.readUtf(160),buf.readVarInt(),readOffers(buf));
    }
    public CommerceMenu(int id,Inventory inventory,NpcEntity npc,long token,String currency,int maxQuantity,List<CommerceRules.Offer> offers) {
        super(CommerceMenus.SHOP.get(),id);this.inventory=inventory;this.npc=npc;this.token=token;
        this.currency=currency;this.offers=List.copyOf(offers);
        if(maxQuantity<1||maxQuantity>CommerceIntentPayload.MAX_QUANTITY)throw new IllegalArgumentException("Quantity limit");
        this.maxQuantity=maxQuantity;
        if(!inventory.player.level().isClientSide()&&npc==null)throw new IllegalArgumentException("Server commerce requires a real NPC");
        if(offers.isEmpty()||offers.size()>CommerceRules.MAX_OFFERS)throw new IllegalArgumentException("Offer limit");
        data=new SimpleContainerData(offers.size()+2);addDataSlots(data);
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inventory,9+row*9+col,69+col*18,155+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inventory,col,69+col*18,215));
    }
    public List<CommerceRules.Offer> offers(){return offers;}
    public String currency(){return currency;}
    public int maxQuantity(){return maxQuantity;}
    public long token(){return token;}
    public int sequence(){return data.get(0);}
    public String status(){return STATUS.get(Math.clamp(data.get(1),0,STATUS.size()-1));}
    public int stock(int index){return data.get(index+2);}
    private void result(String value){int i=STATUS.indexOf(value);data.set(1,i<0?STATUS.indexOf("unavailable"):i);}
    @Override public boolean stillValid(Player player){return player==inventory.player&&(player.level().isClientSide()
            ||player instanceof ServerPlayer server&&NpcCommerce.valid(server,npc,WorldSimRuntime.sim().orElse(null)));}
    public void act(ServerPlayer player,CommerceIntentPayload intent) {
        if(player!=inventory.player||player.containerMenu!=this||intent.menuId()!=containerId
                ||intent.token()!=token||intent.sequence()!=sequence()||intent.offer()>=offers.size())return;
        if(sequence()>=30_000){player.closeContainer();return;}
        // Each received intent is single-use even when it fails. Replays cannot spend or recover twice.
        data.set(0,sequence()+1);
        var sim=WorldSimRuntime.sim().orElse(null);
        if(!NpcCommerce.valid(player,npc,sim)){result("unavailable");player.closeContainer();return;}
        if ((long)player.tickCount-lastActionTick < sim.commerceRules().actionIntervalTicks()) {
            result("busy");broadcastChanges();return;
        }
        lastActionTick=player.tickCount;
        var offer=offers.get(intent.offer());
        var quote=sim.quoteCommerce(npc.ledgerPersonId(),WorldSimAvatars.lifeEpoch(npc),offer.id(),player.getUUID().toString(),intent.quantity());
        if(!quote.ready()){result(quote.result());broadcastChanges();return;}
        if(offer.kind().equals("rest")) {
            String refusal=NpcCommerce.restRefusal(player,npc,sim);
            if(!refusal.equals("ok")){result(refusal);broadcastChanges();return;}
        }
        var payment=com.example.myvillage.spiritstone.SpiritStoneSources.open(player);
        var planned=CommerceInventory.plan(payment.copies(),currency,offer,intent.quantity(),36);
        if(!planned.ready()){result(planned.result());broadcastChanges();return;}
        if(!payment.current()){result("stale");broadcastChanges();return;}
        if(!sim.commitCommerce(quote,player.getUUID().toString())){result("stale");broadcastChanges();return;}
        // Server thread, no event callbacks between ledger commit and copying the prepared inventory.
        if(!payment.commit(planned.items()))throw new IllegalStateException("Commerce inventory changed inside ledger commit");
        inventory.setChanged();NpcCommerce.dirty(player);
        if(offer.kind().equals("rest"))NpcCommerce.restore(player,sim);
        result(switch(offer.kind()){case "buy"->"bought";case "sell"->"sold";default->"rested";});
        broadcastChanges();
    }
    @Override public void broadcastChanges() {
        if(inventory.player instanceof ServerPlayer player) {
            var sim=WorldSimRuntime.sim().orElse(null);
            if(NpcCommerce.valid(player,npc,sim)) {
                for(int i=0;i<offers.size();i++) {
                    var quote=sim.quoteCommerce(npc.ledgerPersonId(),WorldSimAvatars.lifeEpoch(npc),offers.get(i).id(),player.getUUID().toString());
                    int count=quote.ready()?quote.available():0;
                    if(offers.get(i).kind().equals("rest")&&!NpcCommerce.restRefusal(player,npc,sim).equals("ok"))count=0;
                    // -1 denotes an unlimited catalogue offer, not a quantity the client may submit.
                    data.set(i+2,offers.get(i).unlimited()?-1:Math.clamp(count,0,30_000));
                }
                NpcCommerce.pause(player,npc,sim);
            } else for(int i=0;i<offers.size();i++)data.set(i+2,0);
        }
        super.broadcastChanges();
    }
    @Override public ItemStack quickMoveStack(Player player,int index) {
        if(index<0||index>=slots.size())return ItemStack.EMPTY;
        var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;
        ItemStack stack=slot.getItem(),copy=stack.copy();
        if(index<27?!moveItemStackTo(stack,27,36,false):!moveItemStackTo(stack,0,27,false))return ItemStack.EMPTY;
        if(stack.getCount()==copy.getCount())return ItemStack.EMPTY;
        if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();slot.onTake(player,stack);return copy;
    }
    public static void writeOpening(RegistryFriendlyByteBuf buf,long token,String currency,int maxQuantity,List<CommerceRules.Offer> offers) {
        buf.writeLong(token);buf.writeUtf(currency,160);buf.writeVarInt(maxQuantity);buf.writeVarInt(offers.size());
        for(var o:offers) {
            buf.writeUtf(o.id(),96);buf.writeUtf(o.role(),32);buf.writeUtf(o.kind(),16);buf.writeUtf(o.item(),160);
            buf.writeVarInt(o.quantity());buf.writeVarInt(o.price());
        }
    }
    private static List<CommerceRules.Offer> readOffers(RegistryFriendlyByteBuf buf) {
        int size=buf.readVarInt();if(size<1||size>CommerceRules.MAX_OFFERS)throw new IllegalArgumentException("Commerce opening limit");
        List<CommerceRules.Offer> result=new ArrayList<>();
        for(int i=0;i<size;i++) {
            String id=buf.readUtf(96),role=buf.readUtf(32),kind=buf.readUtf(16),item=buf.readUtf(160);
            int quantity=buf.readVarInt(),price=buf.readVarInt();
            if(!Set.of("buy","sell","rest").contains(kind)||quantity<0||quantity>64||price<1||price>10_000)
                throw new IllegalArgumentException("Invalid offer snapshot");
            // Client receives display data only, not the ledger settlement contract.
            result.add(new CommerceRules.Offer(id,role,kind,item,quantity,price,"",0,0,1,0));
        }
        return List.copyOf(result);
    }
}
