package com.example.myvillage.compat.waystones;

import com.example.myvillage.spiritstone.SpiritStoneSources;
import com.example.myvillage.spiritstone.SpiritStoneStorage;
import java.math.BigInteger;
import java.util.UUID;
import net.blay09.mods.waystones.requirement.ItemRequirement;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Keeps Waystones' standard item rules and exact component matching. Other item costs use the native resolver. */
public final class BagAwareItemRequirement extends ItemRequirement {
    private SpiritStonePayment.Receipt receipt;
    private UUID payer;

    public BagAwareItemRequirement() {
        super(ItemStack.EMPTY, 0);
    }

    private boolean spiritStoneCost() {
        return SpiritStoneStorage.collectible(getItemStack());
    }

    @Override
    public boolean canAfford(Player player) {
        if (isEmpty()) return true;
        if (!spiritStoneCost()) return super.canAfford(player);
        if (player.getAbilities().instabuild) return true;
        return receipt == null && SpiritStonePayment.balance(SpiritStoneSources.open(player), getItemStack())
                .compareTo(BigInteger.valueOf(getCount())) >= 0;
    }

    @Override
    public void consume(Player player) {
        if (!(player instanceof ServerPlayer) || isEmpty()) return;
        if (!spiritStoneCost()) {
            super.consume(player);
            return;
        }
        if (player.getAbilities().instabuild || receipt != null) return;
        receipt = SpiritStonePayment.debit(SpiritStoneSources.open(player), getItemStack(), getCount())
                .orElseThrow(() -> new IllegalStateException("Validated Waystones spirit stone payment could not be committed"));
        payer = player.getUUID();
    }

    @Override
    public void rollback(Player player) {
        if (receipt != null) finish(player, false);
        else if (!spiritStoneCost()) super.rollback(player);
    }

    void finish(Player player, boolean successful) {
        if (receipt != null && player instanceof ServerPlayer && player.getUUID().equals(payer)) {
            receipt.finish(successful, () -> SpiritStoneSources.open(player), item -> player.drop(item, false));
        }
    }
}
