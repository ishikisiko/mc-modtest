package com.example.myvillage.sim.runtime.economy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TianhengAuctionTest {
    @BeforeAll static void bootstrap() {
        if (LoadingModList.get() == null) LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static TianhengAuctionData.Lot lot(int opening) {
        return new TianhengAuctionData.Lot(new TianhengMarkets.Goods("sample", "拍品", "测试", "metal",
                new ItemStack(Items.IRON_INGOT, 2), opening));
    }

    @Test void anOpeningBidAndSubsequentIncrementsUseTheAdvertisedPrice() {
        var lot = lot(91);
        assertEquals(91, TianhengAuctions.next(lot));
        lot.price = 91;
        assertEquals(101, TianhengAuctions.next(lot));
        lot.price = 101;
        assertEquals(111, TianhengAuctions.next(lot));
        lot.price = TianhengAuctionData.PRICE_LIMIT;
        assertEquals(0, TianhengAuctions.next(lot));
    }

    @Test void aReloadPreservesTheCurrentBidAndUnclaimedItemsByCityAndOwner() {
        var data = new TianhengAuctionData();
        var winner = UUID.randomUUID();
        var outbid = UUID.randomUUID();
        var lot = lot(91);
        lot.player = winner;
        lot.price = 101;
        lot.attempted = 1;
        var bidder = new TianhengAuctionData.Bidder(17, "张石", "metal", 94);
        data.runs.put("capital_donghuang", new TianhengAuctionData.Run(3, 1234, List.of(lot), List.of(bidder)));
        data.receipt("capital_donghuang", outbid, true).refund = 91;
        data.receipt("capital_ximo", winner, true).awards.add(new ItemStack(Items.GOLD_INGOT));

        var loaded = TianhengAuctionData.load(data.save(new CompoundTag(), RegistryAccess.EMPTY), RegistryAccess.EMPTY);
        var run = loaded.runs.get("capital_donghuang");
        assertEquals(3, run.day());
        assertEquals(1234, run.start());
        assertEquals(winner, run.lots().getFirst().player);
        assertEquals(1, run.lots().getFirst().attempted);
        assertFalse(run.lots().getFirst().settled);
        assertEquals(111, TianhengAuctions.next(run.lots().getFirst()));
        assertEquals(bidder, run.bidders().getFirst());
        assertEquals(91, loaded.receipt("capital_donghuang", outbid, false).refund);
        assertNull(loaded.receipt("capital_donghuang", winner, false));
        assertTrue(loaded.receipt("capital_ximo", winner, false).awards.getFirst().is(Items.GOLD_INGOT));
    }
}
