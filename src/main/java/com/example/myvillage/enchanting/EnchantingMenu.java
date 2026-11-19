package com.example.myvillage.enchanting;

import static com.example.myvillage.enchanting.EnchantingRules.RULES;
import static com.example.myvillage.enchanting.EnchantingRules.key;
import static com.example.myvillage.enchanting.RuneStorage.*;

import com.example.myvillage.spiritstone.SpiritStoneSources;
import com.example.myvillage.spiritstone.SpiritStoneStorage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class EnchantingMenu extends AbstractContainerMenu {
    public enum Mode {
        TABLE("扩展附魔台"), SEAL("篆纹阵"), LIBRARY("仙纹图书馆"), STONE("炼制阵石");
        final String title;
        Mode(String title) { this.title = title; }
    }

    public static final int DRAW = 0, XP = 1, WRITE = 2, UPGRADE = 3, WASH = 4,
            PREVIOUS = 5, NEXT = 6, FOCUS = 7, DEPOSIT = 8, PRINT = 9, REPLACE = 10,
            DRAFT_DOWN = 11, DRAFT_UP = 12, SAVE_SLIP = 13, LOAD_SLIP = 14, RESET = 15,
            PRESET = 16, LEVEL_DOWN = 17, LEVEL_UP = 18, TREASURE = 19;
    public static final int MAX_DRAW_ROWS = 6;
    public static final int SELECT = 24, RAISE = 30, HOLD = 36, LOWER = 42, CATALOGUE_SELECT = 64;
    public static final int WIDTH = 304, HEIGHT = 238;

    private static final int INPUT = 0, INK = 1, MATERIAL = 2, SLIP = 3, OUTPUT = 4, SLOT_COUNT = 5;
    private static final int MODE = 0, STRENGTH = 1, FORMED = 2, FOCUSED = 3, INDEX = 4, STATUS = 5,
            MASK = 6, LEVELS = 7, TARGET_LEVEL = 8, POINTS = 9, MAX_RECORDED = 10, DRAFT_LEVEL = 11,
            COST = 12, PREFERENCE = 13, PRESET_INDEX = 14, FEASIBLE = 15, TAKEN = 16, RETURNED = 17, POINTS_HIGH = 18,
            LEVELS_HIGH = 19, PRICE = 20, TREASURES = 21, RISK = 22, NEGATIVE_INDEX = 23, ACTIVE_DRAW = 24,
            PRICE_GRADE = 25;
    private static final int READY = 0, COMPLETE = 1, INVALID = 2, MATERIALS = 3, STRUCTURE = 4,
            INCOMPATIBLE = 5, INVENTORY = 6, REQUIREMENTS = 7, SPIRIT_STONES = 8;

    private final SimpleContainer work = new SimpleContainer(SLOT_COUNT);
    private final SimpleContainerData data = new SimpleContainerData(26);
    private final Player viewer;
    private final BlockPos origin;
    private final EnchantingDevice device;
    private final Mode serverMode;
    private final List<Holder<Enchantment>> catalogue;
    private final Map<Holder<Enchantment>, Integer> draft = new LinkedHashMap<>();
    private ItemStack previousInput = ItemStack.EMPTY;
    private int mask;
    private final int[] selectedLevels = new int[MAX_DRAW_ROWS];
    private boolean treasure;
    private int index;
    private int preference = -1;
    private int printLevel = 1;
    private int preset;
    private int status;

    public EnchantingMenu(int id, Inventory inv) { this(id, inv, Mode.TABLE, null, null); }

    EnchantingMenu(int id, Inventory inv, Mode mode, BlockPos origin, EnchantingDevice device) {
        super(EnchantingContent.MENU.get(), id);
        viewer = inv.player;
        this.origin = origin;
        this.device = device;
        serverMode = mode;
        catalogue = viewer.registryAccess().registryOrThrow(Registries.ENCHANTMENT).holders()
                .sorted(Comparator.comparing(EnchantingRules::key)).map(h -> (Holder<Enchantment>) h).toList();
        data.set(MODE, mode.ordinal());
        data.set(PREFERENCE, -1);
        data.set(NEGATIVE_INDEX, -1);
        addDataSlots(data);
        int[][] positions = {{28, 111}, {54, 111}, {80, 111}, {106, 111}, {106, 136}};
        for (int i = 0; i < SLOT_COUNT; i++) {
            int slot = i;
            addSlot(new Slot(work, slot, positions[i][0], positions[i][1]) {
                @Override public boolean mayPlace(ItemStack stack) { return accepts(slot, stack); }
                @Override public boolean isActive() {
                    return switch (mode()) {
                        case TABLE -> slot <= MATERIAL;
                        case SEAL -> slot <= INK;
                        case STONE -> slot == INPUT || slot == OUTPUT;
                        case LIBRARY -> true;
                    };
                }
                @Override public int getMaxStackSize() { return slot == INPUT && mode() != Mode.STONE ? 1 : super.getMaxStackSize(); }
            });
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inv, 9 + row * 9 + col, 72 + col * 18, 159 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 72 + col * 18, 217));
    }

    public Mode mode() { return Mode.values()[data.get(MODE)]; }
    public int strength() { return data.get(STRENGTH); }
    public boolean formed() { return data.get(FORMED) != 0; }
    public boolean focused() { return data.get(FOCUSED) != 0; }
    public int selection() { return data.get(INDEX); }
    public List<Holder<Enchantment>> catalogue() { return catalogue; }
    public Holder<Enchantment> current() { return catalogue.get(selection()); }
    public ItemStack input() { return work.getItem(INPUT); }
    public int mask() { return data.get(MASK); }
    public int level(int i) { return (data.get(i < 4 ? LEVELS : LEVELS_HIGH) >>> ((i % 4) * 4)) & 15; }
    public int price() { return data.get(PRICE); }
    public String priceGrade() { return SealRules.Grade.values()[data.get(PRICE_GRADE)].label(); }
    public boolean treasures() { return data.get(TREASURES) != 0; }
    public int risk() { return data.get(RISK); }
    public Holder<Enchantment> negative() {
        int position = data.get(NEGATIVE_INDEX);
        return position >= 0 && position < catalogue.size() ? catalogue.get(position) : null;
    }
    public int targetLevel() { return data.get(TARGET_LEVEL); }
    public int points() { return data.get(POINTS) | (data.get(POINTS_HIGH) << 15); }
    public int recorded() { return data.get(MAX_RECORDED); }
    public int draftLevel() { return data.get(DRAFT_LEVEL); }
    public int cost() { return data.get(COST); }
    public int preset() { return data.get(PRESET_INDEX) + 1; }
    public int taken() { return data.get(TAKEN); }
    public int returned() { return data.get(RETURNED); }
    public boolean feasible() { return data.get(FEASIBLE) != 0; }
    public boolean preferred() { return data.get(PREFERENCE) == selection(); }
    public int existing() { return catalogue.isEmpty() ? 0 : effects(input()).getOrDefault(current(), 0); }
    public int heldIndex() { return candidates(input()).indexOf(read(input()).getString(HELD)); }
    public boolean canHold() {
        return mode() == Mode.SEAL && formed() && strength() >= RULES.lock_strength() && data.get(ACTIVE_DRAW) != 0;
    }
    public String status() {
        return switch (data.get(STATUS)) {
            case COMPLETE -> "已完成";
            case INVALID -> "当前器物或词条无法进行这项操作";
            case MATERIALS -> "消耗品不足";
            case STRUCTURE -> "阵枢未成形";
            case INCOMPATIBLE -> "有词条不适用或互斥";
            case INVENTORY -> "库存、收录等级或容量不足";
            case REQUIREMENTS -> "境界、修为或材料不足";
            case SPIRIT_STONES -> priceGrade() + "灵石不足";
            default -> "";
        };
    }

    private boolean accepts(int slot, ItemStack stack) {
        return switch (slot) {
            case INPUT -> mode() == Mode.STONE ? stoneIngredient(stack)
                    : (mode() == Mode.LIBRARY ? stack.is(Items.ENCHANTED_BOOK) : stack.is(Items.BOOK))
                    || stack.getMaxStackSize() == 1 && !stack.is(Items.ENCHANTED_BOOK)
                    && catalogue.stream().anyMatch(rune -> rune.value().isSupportedItem(stack));
            case INK -> mode() != Mode.STONE && EnchantingContent.isInk(stack);
            case MATERIAL -> mode() == Mode.LIBRARY ? stack.is(Items.BOOK)
                    : mode() == Mode.TABLE && stack.is(Items.LAPIS_LAZULI);
            case SLIP -> mode() == Mode.LIBRARY && stack.is(EnchantingContent.SLIP.get());
            default -> false;
        };
    }

    private boolean stoneIngredient(ItemStack stack) {
        return RULES.stones().stream().anyMatch(stone ->
                stack.is(BuiltInRegistries.ITEM.get(ResourceLocation.parse(stone.ingredient()))));
    }

    private Direction front() {
        return serverMode == Mode.TABLE ? Direction.SOUTH : device.front();
    }

    private EnchantingStructure.Table table() {
        return EnchantingStructure.table(viewer.level(), origin, front(), serverMode == Mode.SEAL);
    }

    @Override public boolean stillValid(Player player) {
        if (origin == null) return player == viewer; // the client menu has no world references
        if (player != viewer || !player.isAlive() || player.isSpectator() || !player.level().isLoaded(origin)
                || player.distanceToSqr(origin.getX() + .5, origin.getY() + .5, origin.getZ() + .5) > 64) return false;
        var level = player.level();
        var block = level.getBlockState(origin);
        return switch (serverMode) {
            case STONE -> block.getBlock() instanceof com.example.myvillage.block.ForgingTableBlock;
            case TABLE -> block.is(Blocks.ENCHANTING_TABLE) && level.getBlockEntity(origin.below()) == device
                    && device.getBlockState().is(EnchantingContent.SOCKET.get());
            case SEAL, LIBRARY -> level.getBlockEntity(origin) == device && !device.isRemoved();
        };
    }

    @Override public void broadcastChanges() {
        if (origin != null && stillValid(viewer)) {
            syncInput();
            refresh();
        }
        super.broadcastChanges();
    }

    private void syncInput() {
        if (ItemStack.matches(previousInput, input())) return;
        previousInput = input().copy();
        draft.clear();
        effects(input()).forEach((rune, level) -> {
            if (supported(rune, level)) draft.put(rune, level);
        });
        mask = 0;
        java.util.Arrays.fill(selectedLevels, 0);
        int held = heldIndex();
        if (held >= 0 && held < selectedLevels.length) selectedLevels[held] = read(input()).getInt(HELD_LEVEL);
        status = READY;
    }

    private List<Holder<Enchantment>> batch() {
        return candidates(input()).stream().limit(serverMode == Mode.SEAL ? MAX_DRAW_ROWS : 4)
                .map(this::findRune).toList();
    }

    private Holder<Enchantment> findRune(String name) {
        for (var rune : catalogue) if (key(rune).equals(name)) return rune;
        return null;
    }

    private String sealSignature(EnchantingStructure.Table table) {
        return SealRules.signature(table.strength(), treasure, device.discTypes());
    }

    private boolean activeBatch(EnchantingStructure.Table table, CompoundTag stored) {
        String seal = stored.getString(SEAL);
        return serverMode == Mode.SEAL ? table.formed() && seal.equals(sealSignature(table)) : seal.isEmpty();
    }

    private boolean firstSealDraw(CompoundTag stored) { return stored.getString(SEAL).isEmpty(); }

    private List<Holder<Enchantment>> negatives(Map<Holder<Enchantment>, Integer> current) {
        if (input().isEmpty()) return List.of();
        return catalogue.stream().filter(rune -> rune.is(EnchantmentTags.CURSE) && !current.containsKey(rune)
                && (input().is(Items.BOOK) || rune.value().isSupportedItem(input())) && compatible(rune, current)).toList();
    }

    private void refresh() {
        data.set(INDEX, index);
        data.set(STATUS, status);
        data.set(PRESET_INDEX, preset);
        if (serverMode == Mode.STONE) return;
        if (serverMode == Mode.LIBRARY) {
            if (catalogue.isEmpty()) return;
            var rune = catalogue.get(index);
            var ledger = device.ledgerCopy();
            int stock = EnchantingDevice.points(ledger, key(rune));
            data.set(POINTS, stock & 32767);
            data.set(POINTS_HIGH, stock >>> 15);
            data.set(MAX_RECORDED, EnchantingDevice.recorded(ledger, key(rune)));
            data.set(TARGET_LEVEL, printLevel);
            data.set(DRAFT_LEVEL, draft.getOrDefault(rune, 0));
            boolean ready = device.formed();
            data.set(FORMED, ready ? 1 : 0);
            var preview = ready ? plan(ledger) : new Change(STRUCTURE, null, 0, 0);
            data.set(FEASIBLE, preview.status == COMPLETE ? 1 : 0);
            data.set(TAKEN, Math.min(32767, preview.taken));
            data.set(RETURNED, Math.min(32767, preview.returned));
            data.set(COST, preview.taken > 0 || preview.returned > 0 ? RULES.swap_ink() : 0);
            return;
        }
        var table = table();
        data.set(STRENGTH, table.strength());
        data.set(FORMED, table.formed() ? 1 : 0);
        data.set(FOCUSED, table.focused() ? 1 : 0);
        data.set(PREFERENCE, preference);
        int packed = 0;
        int upper = 0;
        int ink = 0;
        var stored = read(input());
        boolean active = activeBatch(table, stored);
        var batch = active ? batch() : List.<Holder<Enchantment>>of();
        var current = effects(input());
        data.set(ACTIVE_DRAW, active ? 1 : 0);
        if (!active) mask = 0;
        if (serverMode == Mode.SEAL) {
            var settings = SealRules.RULES.selection(device.discTypes(), firstSealDraw(stored), treasure);
            var price = SealRules.RULES.price(table.strength());
            data.set(PRICE, table.formed() ? price.stones() : 0);
            data.set(PRICE_GRADE, price.grade().ordinal());
            data.set(TREASURES, treasure ? 1 : 0);
            data.set(RISK, negatives(current).isEmpty() ? 0 : SealRules.RULES.risk(settings));
            var negative = active ? findRune(stored.getString(NEGATIVE)) : null;
            data.set(NEGATIVE_INDEX, negative == null ? -1 : catalogue.indexOf(negative));
        }
        for (int i = 0; i < batch.size(); i++) {
            var rune = batch.get(i);
            int level = rune == null || !canWrite(rune, table.strength(), current) ? 0 : selectedLevel(rune, i, table.strength());
            if (i < 4) packed |= Math.min(15, level) << (4 * i);
            else upper |= Math.min(15, level) << (4 * (i - 4));
            if (level == 0) mask &= ~(1 << i);
            else if ((mask & (1 << i)) != 0) ink += RULES.ink(rune, 0, level);
        }
        data.set(MASK, mask);
        data.set(LEVELS, packed);
        data.set(LEVELS_HIGH, upper);
        data.set(COST, ink);
    }

    private int maxLevel(Holder<Enchantment> rune, int strength) {
        return Math.min(15, serverMode == Mode.SEAL ? SealRules.RULES.level(rune, strength) : RULES.maxLevel(rune, strength));
    }

    private boolean drawableHere(Holder<Enchantment> rune) {
        return serverMode == Mode.SEAL ? SealRules.drawable(rune, treasure) : drawable(rune);
    }

    private int selectedLevel(Holder<Enchantment> rune, int i, int strength) {
        int max = maxLevel(rune, strength);
        return selectedLevels[i] == 0 ? max : Math.min(selectedLevels[i], max);
    }

    @Override public boolean clickMenuButton(Player player, int button) {
        if (!(player instanceof ServerPlayer server) || player.containerMenu != this || !stillValid(player)) return false;
        syncInput();
        if (button >= CATALOGUE_SELECT && serverMode != Mode.STONE) {
            int selected = button - CATALOGUE_SELECT;
            if (selected >= catalogue.size()) return false;
            index = selected;
            printLevel = 1;
            status = READY;
        } else if (button == PREVIOUS || button == NEXT) {
            int size = serverMode == Mode.STONE ? RULES.stones().size() : catalogue.size();
            if (size > 0) index = Math.floorMod(index + (button == PREVIOUS ? -1 : 1), size);
            printLevel = 1;
            status = READY;
        } else if (serverMode == Mode.STONE) {
            if (button != WRITE) return false;
            var stone = RULES.stones().get(index);
            if (!work.getItem(OUTPUT).isEmpty()) status = INVALID;
            else if (!EnchantingContent.forgeStone(server, stone, input())) status = REQUIREMENTS;
            else {
                work.setItem(OUTPUT, EnchantingContent.STONES.get(stone.id()).toStack());
                com.example.myvillage.block.ForgingTableBlock.completed(server.serverLevel(), origin);
                complete();
            }
        } else if (serverMode == Mode.LIBRARY) {
            if (catalogue.isEmpty()) return false;
            status = library(button);
        } else {
            status = enchant(server, button);
        }
        refresh();
        broadcastChanges();
        return true;
    }

    private boolean canWrite(Holder<Enchantment> rune, int strength, Map<Holder<Enchantment>, Integer> current) {
        return !input().isEmpty() && !input().is(Items.ENCHANTED_BOOK) && drawableHere(rune)
                && !current.containsKey(rune) && maxLevel(rune, strength) > 0
                && (input().is(Items.BOOK) || rune.value().isSupportedItem(input())) && compatible(rune, current);
    }

    private int enchant(ServerPlayer player, int button) {
        if (catalogue.isEmpty()) return INVALID;
        var table = table();
        boolean seal = serverMode == Mode.SEAL;
        if (seal && !table.formed()) return STRUCTURE;
        var stored = read(input());
        var batch = activeBatch(table, stored) ? batch() : List.<Holder<Enchantment>>of();
        var current = effects(input());
        var rune = catalogue.get(index);
        if (seal && button == TREASURE) {
            treasure = !treasure;
            mask = 0;
            return READY;
        }
        if (button == FOCUS) {
            if (!table.focused() || !canWrite(rune, table.strength(), current)) return INVALID;
            preference = preference == index ? -1 : index;
            return READY;
        }
        if (button == DRAW || button == XP) {
            if (seal) return button == DRAW ? rollSeal(player, table, batch, current, stored) : INVALID;
            return roll(player, button, table, current);
        }
        if (button == UPGRADE || button == WASH) {
            int old = current.getOrDefault(rune, 0);
            if (!supported(rune, old) || input().is(Items.ENCHANTED_BOOK)) return INVALID;
            int next = button == WASH ? 0 : old + 1;
            if (next > 0 && (!drawableHere(rune) || !rune.value().isSupportedItem(input())
                    || next > maxLevel(rune, table.strength()))) return INVALID;
            int fee = next == 0 ? RULES.wash_ink() : RULES.ink(rune, old, next);
            if (!hasInk(fee)) return MATERIALS;
            if (next == 0) current.remove(rune);
            else current.put(rune, next);
            apply(current, fee);
            return COMPLETE;
        }
        if (button == WRITE) {
            int count = Integer.bitCount(mask);
            int max = table.formed() && table.strength() >= RULES.double_strength() ? 2 : 1;
            if (batch.isEmpty() || count < 1 || count > max) return INVALID;
            int cost = 0;
            for (int i = 0; i < batch.size(); i++) {
                if ((mask & (1 << i)) == 0) continue;
                var chosen = batch.get(i);
                if (chosen == null || !canWrite(chosen, table.strength(), current)) return INCOMPATIBLE;
                int level = selectedLevel(chosen, i, table.strength());
                current.put(chosen, level);
                cost += RULES.ink(chosen, 0, level);
            }
            if (seal && !stored.getString(NEGATIVE).isEmpty()) {
                var penalty = findRune(stored.getString(NEGATIVE));
                if (penalty == null || !negatives(current).contains(penalty)) return INCOMPATIBLE;
                current.put(penalty, 1);
            }
            if (!hasInk(cost)) return MATERIALS;
            apply(current, cost);
            return COMPLETE;
        }
        int action = button - SELECT;
        if (action < 0 || action >= 4 * MAX_DRAW_ROWS || action % MAX_DRAW_ROWS >= batch.size()) return INVALID;
        int slot = action % MAX_DRAW_ROWS;
        var chosen = batch.get(slot);
        if (chosen == null || !canWrite(chosen, table.strength(), current)) return INVALID;
        if (button < RAISE) {
            int next = mask ^ (1 << slot);
            int max = table.formed() && table.strength() >= RULES.double_strength() ? 2 : 1;
            if (max == 1 && (next & (1 << slot)) != 0) next = 1 << slot;
            if (Integer.bitCount(next) > max) return INVALID;
            for (int i = 0; i < batch.size(); i++) {
                if (i != slot && (next & (1 << i)) != 0 && batch.get(i) != null
                        && !Enchantment.areCompatible(chosen, batch.get(i))) return INCOMPATIBLE;
            }
            mask = next;
        } else if (button >= HOLD && button < LOWER) {
            if (!seal || table.strength() < RULES.lock_strength()) return INVALID;
            update(input(), tag -> {
                boolean unset = key(chosen).equals(tag.getString(HELD));
                tag.putString(HELD, unset ? "" : key(chosen));
                tag.putInt(HELD_LEVEL, unset ? 0 : selectedLevel(chosen, slot, table.strength()));
            });
            previousInput = input().copy();
        } else {
            int increment = button < HOLD ? 1 : -1;
            selectedLevels[slot] = Math.clamp(selectedLevel(chosen, slot, table.strength()) + increment,
                    1, maxLevel(chosen, table.strength()));
            if (slot == heldIndex()) {
                update(input(), tag -> tag.putInt(HELD_LEVEL, selectedLevels[slot]));
                previousInput = input().copy();
            }
        }
        return READY;
    }

    private int rollSeal(ServerPlayer player, EnchantingStructure.Table table, List<Holder<Enchantment>> last,
            Map<Holder<Enchantment>, Integer> current, CompoundTag stored) {
        var discs = device.discTypes();
        var selection = SealRules.RULES.selection(discs, firstSealDraw(stored), treasure);
        var pool = catalogue.stream().filter(rune -> SealRules.drawable(rune, selection.treasure())
                && canWrite(rune, table.strength(), current)).toList();
        if (pool.isEmpty()) return INVALID;
        var locked = findRune(stored.getString(HELD));
        if (locked != null && (!last.contains(locked) || !pool.contains(locked) || table.strength() < RULES.lock_strength())) locked = null;
        if (pool.size() == 1 && locked != null) return INVALID;

        var access = SpiritStoneSources.open(player);
        var copies = access.copies();
        var price = SealRules.RULES.price(table.strength());
        if (!SpiritStoneStorage.remove(copies, SpiritStoneStorage.ordinary(price.grade().item()), price.stones())) {
            return SPIRIT_STONES;
        }
        var result = SealDraw.choose(pool, last, locked, selection, player.getRandom());
        if (result.isEmpty()) return INVALID;
        var penalties = negatives(current);
        String negative = !penalties.isEmpty() && player.getRandom().nextInt(100) < SealRules.RULES.risk(selection)
                ? key(penalties.get(player.getRandom().nextInt(penalties.size()))) : "";
        int heldLevel = locked == null ? 0 : Math.clamp(stored.getInt(HELD_LEVEL), 1, maxLevel(locked, table.strength()));
        var next = input().copy();
        saveDraw(next, result, locked == null ? "" : key(locked), heldLevel,
                SealRules.signature(table.strength(), treasure, discs), negative);
        if (!access.commit(copies)) return SPIRIT_STONES;
        work.setItem(INPUT, next);
        syncInput();
        mask = locked == null ? 0 : 1;
        device.animate(1);
        return READY;
    }

    private int roll(ServerPlayer player, int source, EnchantingStructure.Table table,
            Map<Holder<Enchantment>, Integer> current) {
        var pool = new ArrayList<>(catalogue.stream().filter(rune -> canWrite(rune, table.strength(), current)).toList());
        if (pool.isEmpty()) return INVALID;
        if (source == DRAW) {
            if (work.getItem(MATERIAL).getCount() < RULES.lapis_cost()) return MATERIALS;
            work.getItem(MATERIAL).shrink(RULES.lapis_cost());
        } else {
            int fee = RULES.xp_cost();
            if (player.experienceLevel == 0 && Math.round(player.experienceProgress * player.getXpNeededForNextLevel()) < fee)
                return MATERIALS;
            player.giveExperiencePoints(-fee);
        }
        var result = new ArrayList<Holder<Enchantment>>();
        while (result.size() < table.candidates() && !pool.isEmpty()) {
            int total = pool.stream().mapToInt(rune -> weight(rune, table)).sum();
            int ticket = player.getRandom().nextInt(total);
            for (int i = 0; i < pool.size(); i++) {
                ticket -= weight(pool.get(i), table);
                if (ticket < 0) {
                    result.add(pool.remove(i));
                    break;
                }
            }
        }
        saveDraw(input(), result, "", 0);
        previousInput = input().copy();
        mask = 0;
        Arrays.fill(selectedLevels, 0);
        return READY;
    }

    private int weight(Holder<Enchantment> rune, EnchantingStructure.Table table) {
        return rune.value().getWeight() * (table.focused() && preference >= 0
                && catalogue.get(preference).equals(rune) ? RULES.preference_weight() : 1);
    }

    private boolean hasInk(int fee) { return work.getItem(INK).getCount() >= fee; }

    private void apply(Map<Holder<Enchantment>, Integer> current, int fee) {
        var result = write(input(), current);
        work.getItem(INK).shrink(fee);
        work.setItem(INPUT, result);
        if (serverMode == Mode.SEAL || serverMode == Mode.LIBRARY) device.animate(2);
        complete();
    }

    private void complete() {
        syncInput();
        status = COMPLETE;
    }

    private int library(int button) {
        var rune = catalogue.get(index);
        var ledger = device.ledgerCopy();
        if (button == DEPOSIT) {
            if (!input().is(Items.ENCHANTED_BOOK)) return INVALID;
            var effects = effects(input());
            if (effects.isEmpty()) return INVALID;
            for (var effect : effects.entrySet()) {
                int level = effect.getValue();
                if (!supported(effect.getKey(), level)) return INCOMPATIBLE;
                if (!EnchantingDevice.adjust(ledger, key(effect.getKey()), RuneStorage.points(level), level)) return INVENTORY;
            }
            device.replaceLedger(ledger);
            work.setItem(INPUT, ItemStack.EMPTY);
            complete();
            return COMPLETE;
        }
        if (button == PRINT || button == LEVEL_DOWN || button == LEVEL_UP) {
            if (button != PRINT) {
                printLevel = Math.clamp(printLevel + (button == LEVEL_DOWN ? -1 : 1), 1,
                        Math.max(1, EnchantingDevice.recorded(ledger, key(rune))));
                return READY;
            }
            if (!supported(rune, printLevel) || printLevel > EnchantingDevice.recorded(ledger, key(rune))
                    || !EnchantingDevice.adjust(ledger, key(rune), -RuneStorage.points(printLevel), 0)) return INVENTORY;
            if (!work.getItem(MATERIAL).is(Items.BOOK) || !work.getItem(OUTPUT).isEmpty()) return MATERIALS;
            var book = write(work.getItem(MATERIAL), Map.of(rune, printLevel));
            device.replaceLedger(ledger);
            work.getItem(MATERIAL).shrink(1);
            work.setItem(OUTPUT, book);
            return COMPLETE;
        }
        if (button == PRESET) {
            preset = 1 - preset;
            return READY;
        }
        if (!device.verifyStructure()) return STRUCTURE;
        if (!equipment()) return INVALID;
        if (button == DRAFT_UP || button == DRAFT_DOWN) {
            int old = draft.getOrDefault(rune, 0);
            int max = Math.max(effects(input()).getOrDefault(rune, 0), EnchantingDevice.recorded(ledger, key(rune)));
            int next = Math.clamp(old + (button == DRAFT_UP ? 1 : -1), 0, max);
            if (next == 0) draft.remove(rune);
            else if (supported(rune, next) && rune.value().isSupportedItem(input())) draft.put(rune, next);
            else return INCOMPATIBLE;
            return READY;
        }
        if (button == RESET) {
            previousInput = ItemStack.EMPTY;
            syncInput();
            return READY;
        }
        if (button == REPLACE) {
            var change = plan(ledger);
            if (change.status != COMPLETE) return change.status;
            if (!hasInk(RULES.swap_ink())) return MATERIALS;
            device.replaceLedger(ledger);
            apply(change.result, RULES.swap_ink());
            return COMPLETE;
        }
        if (button != SAVE_SLIP && button != LOAD_SLIP) return INVALID;
        var slip = work.getItem(SLIP);
        if (!slip.is(EnchantingContent.SLIP.get())) return MATERIALS;
        String label = "ArrayPreset" + preset;
        if (button == SAVE_SLIP) {
            var map = new CompoundTag();
            draft.forEach((effect, level) -> map.putInt(key(effect), level));
            update(slip, tag -> tag.put(label, map));
        } else {
            var tag = read(slip);
            if (!tag.contains(label)) return INVALID;
            var map = tag.getCompound(label);
            var next = new LinkedHashMap<Holder<Enchantment>, Integer>();
            for (var effect : catalogue) {
                int level = map.getInt(key(effect));
                if (supported(effect, level)) next.put(effect, level);
            }
            if (next.size() != map.size()) return INVALID;
            draft.clear();
            draft.putAll(next);
        }
        return READY;
    }

    private boolean equipment() { return !input().isEmpty() && !input().is(Items.BOOK) && !input().is(Items.ENCHANTED_BOOK); }

    private record Change(int status, Map<Holder<Enchantment>, Integer> result, int taken, int returned) {}

    private Change plan(CompoundTag ledger) {
        if (!equipment()) return new Change(INVALID, null, 0, 0);
        var existing = effects(input());
        var result = new LinkedHashMap<>(existing);
        result.entrySet().removeIf(entry -> supported(entry.getKey(), entry.getValue()));
        result.putAll(draft);
        int taken = 0;
        int returned = 0;
        for (var entry : draft.entrySet()) {
            var rune = entry.getKey();
            int next = entry.getValue();
            int old = existing.getOrDefault(rune, 0);
            if (next != old && (!supported(rune, next) || (old != 0 && !supported(rune, old))
                    || !rune.value().isSupportedItem(input()) || !compatible(rune, result))) {
                return new Change(INCOMPATIBLE, null, 0, 0);
            }
        }
        boolean enough = true;
        for (var rune : catalogue) {
            int old = existing.getOrDefault(rune, 0);
            int next = result.getOrDefault(rune, 0);
            if (old == next) continue;
            int delta = RuneStorage.points(old) - RuneStorage.points(next);
            taken += Math.max(0, -delta);
            returned += Math.max(0, delta);
            var name = key(rune);
            if (next > old && next > EnchantingDevice.recorded(ledger, name)) enough = false;
            if (!EnchantingDevice.adjust(ledger, name, delta, old)) enough = false;
        }
        int state = taken == 0 && returned == 0 ? INVALID : enough ? COMPLETE : INVENTORY;
        return new Change(state, result, taken, returned);
    }

    @Override public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide) clearContainer(player, work);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        var slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        var stack = slot.getItem();
        var before = stack.copy();
        boolean moved = index < SLOT_COUNT
                ? moveItemStackTo(stack, SLOT_COUNT, slots.size(), true)
                : moveItemStackTo(stack, INK, OUTPUT, false) || moveItemStackTo(stack, INPUT, INK, false);
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, stack);
        return before;
    }
}
