package com.example.myvillage.client.cultivation.panel;

import com.example.myvillage.client.cultivation.ClientCultivationIntentSender;
import com.example.myvillage.combat.SkillBindingRules;
import com.example.myvillage.cultivation.data.HeartMethod.Slot;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.RealmDefinition;
import com.example.myvillage.cultivation.data.TechniqueCategory;
import com.example.myvillage.cultivation.growth.TechniqueGrowthRules;
import com.example.myvillage.cultivation.technique.HeartTechniques;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** Player-facing view of learned techniques. */
public final class TechniquesPage extends PanelPage {
    private static final int FILTER_HEIGHT = 42;
    private static final int DOCK_HEIGHT = TechniqueGrowthPage.DOCK;
    private final Consumer<ResourceLocation> openSkills;
    private final List<PanelButton> filters = new ArrayList<>();
    private final List<TechniqueTiles.Hit> hits = new ArrayList<>();
    private final TechniqueGrowthPage growth = new TechniqueGrowthPage(() -> {
        detail = false;
        scrollToTop = true;
    });
    private PanelButton action, growthButton, realmButton, slotButton;
    private boolean detail, visible, scrollToTop;
    private TechniqueCategory filter;
    private ResourceLocation realm, selected, hovered;
    private List<ResourceLocation> realms = List.of();
    private Slot slot;
    private int px = -1, py = -1, x, width, top, dockY;

    public TechniquesPage(Consumer<ResourceLocation> openSkills) { this.openSkills = openSkills; }

    @Override public int topInset() { return detail ? 18 : FILTER_HEIGHT; }
    @Override public int dockHeight(int initializedHeight) { return detail ? 0 : initializedHeight; }

    @Override public int init(int x, int y, int width, int height, Consumer<AbstractWidget> widgets) {
        this.x = x;
        this.width = width;
        top = y;
        dockY = y + height - DOCK_HEIGHT;
        filters.clear();
        for (int i = 0; i < 5; i++) {
            TechniqueCategory category = i == 0 ? null : TechniqueShelf.ORDER.get(i - 1);
            String label = i == 0 ? "全部" : Component.translatable(
                    "screen.yuanxian.cultivation.category." + category.serializedName()).getString();
            filters.add(filterButton(i, 5, y, label, () -> {
                filter = category;
                if (category != null && category != TechniqueCategory.CORE) slot = null;
                resetSelection();
            }, widgets));
        }
        realmButton = filterButton(0, 2, y + 20, "全部境界", () -> {
            int next = realm == null ? 0 : realms.indexOf(realm) + 1;
            realm = next < realms.size() ? realms.get(next) : null;
            resetSelection();
        }, widgets);
        slotButton = filterButton(1, 2, y + 20, "全部栏位", () -> {
            int next = slot == null ? 0 : slot.ordinal() + 1;
            slot = next < Slot.values().length ? Slot.values()[next] : null;
            resetSelection();
        }, widgets);
        action = new PanelButton(PanelButton.Style.ACTION, InkPanelTheme.JADE, x + width - 86, dockY + 33,
                86, 20, Component.literal("查看"), button -> useSelection());
        action.setInk(true);
        action.setPrimary(true);
        widgets.accept(action);
        growthButton = new PanelButton(PanelButton.Style.ACTION, InkPanelTheme.JADE, x, dockY + 33,
                86, 20, Component.literal("修习与参悟"), button -> {
                    if (selected == null) return;
                    growth.select(selected);
                    detail = true;
                    scrollToTop = true;
                });
        growthButton.setInk(true);
        widgets.accept(growthButton);
        growth.init(x, y, width, height, widgets);
        return DOCK_HEIGHT;
    }

    private PanelButton filterButton(int index, int total, int y, String label, Runnable click,
            Consumer<AbstractWidget> widgets) {
        int left = x + index * width / total;
        int right = x + (index + 1) * width / total - 3;
        var button = new PanelButton(PanelButton.Style.NAV, InkPanelTheme.JADE, left, y, right - left, 18,
                Component.literal(label), ignored -> click.run());
        button.setInk(true);
        widgets.accept(button);
        return button;
    }

    private void useSelection() {
        var context = PanelContext.capture(Minecraft.getInstance(), PanelFonts.get());
        if (selected == null || context.profile() == null) return;
        context.technique(selected).ifPresent(definition -> {
            if (!context.profile().learnedTechniques().containsKey(selected)) return;
            if (definition.isCore()) {
                ClientCultivationIntentSender.sendCoreSwitch(selected);
            } else if (SkillBindingRules.kind(selected, definition) != SkillBindingRules.Kind.NONE) {
                openSkills.accept(selected);
            }
        });
    }

    private void resetSelection() {
        selected = null;
        scrollToTop = true;
    }

    @Override public void setVisible(boolean value) {
        visible = value;
        boolean listing = value && !detail;
        filters.forEach(button -> button.visible = listing);
        realmButton.visible = slotButton.visible = listing;
        action.visible = growthButton.visible = listing;
        growth.setVisible(value && detail);
    }

    @Override public boolean takeScrollToTop() {
        boolean value = scrollToTop;
        scrollToTop = false;
        return growth.takeScrollToTop() || value;
    }

    @Override public void pointer(int x, int y) { px = x; py = y; }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (detail || button != 0) return false;
        for (var hit : hits) {
            if (hit.contains(x, y)) {
                selected = hit.id();
                return true;
            }
        }
        return false;
    }

    @Override public void refresh(PanelContext context, boolean shown) {
        if (detail) {
            growth.refresh(context, shown);
            return;
        }
        if (!shown || context.profile() == null) return;
        for (int i = 0; i < filters.size(); i++) {
            TechniqueCategory category = i == 0 ? null : TechniqueShelf.ORDER.get(i - 1);
            filters.get(i).setSelected(category == filter);
            filters.get(i).setIcon(i == 0 ? PanelAssets.navigation("techniques") : PanelAssets.category(category));
        }
        realms = context.registries().registry(ModCultivationRegistries.REALMS).map(registry -> registry.keySet().stream()
                .sorted(Comparator.comparingInt(id -> Optional.ofNullable(registry.get(id))
                        .map(RealmDefinition::sortOrder).orElse(Integer.MAX_VALUE))).toList()).orElse(List.of());
        if (realm != null && !realms.contains(realm)) realm = null;
        realmButton.setMessage(realm == null ? Component.literal("全部境界") : context.realmName(realm));
        realmButton.setTooltip(Tooltip.create(Component.literal("按最低学习境界筛选，点击切换")));
        slotButton.setMessage(Component.literal(slot == null ? "全部栏位" : slot == Slot.SPIRIT ? "灵力与修炼" : slot.title()));
        slotButton.active = filter == null || filter == TechniqueCategory.CORE;
        slotButton.setTooltip(Tooltip.create(Component.literal("按心法装备栏位筛选，点击切换")));

        var definition = selected == null ? null : context.technique(selected).orElse(null);
        boolean learned = selected != null && context.profile().learnedTechniques().containsKey(selected);
        boolean equippable = definition != null && definition.isCore();
        boolean equipped = equippable && context.profile().equippedHeart(HeartTechniques.slot(definition))
                .filter(selected::equals).isPresent();
        boolean cast = definition != null && SkillBindingRules.kind(selected, definition) != SkillBindingRules.Kind.NONE;
        action.active = learned && (equippable && !equipped || cast);
        action.setMessage(Component.literal(!learned ? "未选择"
                : equippable ? equipped ? "已装备" : "装备心法" : cast ? "分配按键" : "无需施放"));
        growthButton.active = learned && TechniqueGrowthRules.bundled().find(selected).isPresent();
    }

    @Override public int render(GuiGraphics graphics, PanelContext context, int x, int y, int width, int viewport) {
        if (detail) return growth.render(graphics, context, x, y, width, viewport);
        hits.clear();
        hovered = null;
        var ids = context.profile().learnedTechniques().keySet();
        var items = new ArrayList<TechniqueShelf.Item>();
        for (var id : ids) {
            var definition = context.technique(id).orElse(null);
            if (filter != null && (definition == null || definition.category() != filter)) continue;
            if (realm != null && (definition == null || definition.requirements().minimumRealm().filter(realm::equals).isEmpty())) continue;
            if (slot != null && (definition == null || !definition.isCore() || HeartTechniques.slot(definition) != slot)) continue;
            items.add(new TechniqueShelf.Item(id, definition == null ? null : definition.category(),
                    definition == null ? -1 : definition.grade(), TechniqueTiles.name(context, id)));
        }
        var ordered = TechniqueShelf.group(items).stream().flatMap(group -> group.items().stream()).toList();
        if (selected == null || ordered.stream().noneMatch(item -> item.id().equals(selected))) {
            selected = ordered.isEmpty() ? null : ordered.getFirst().id();
        }
        if (ordered.isEmpty()) {
            CultivationInkTheme.text(graphics, context.font(), "当前筛选下暂无已学功法",
                    x + 8, y + 12, width - 16, InkPanelTheme.MUTED);
            return 38;
        }
        var grid = TechniqueGridLayout.of(width, ordered.size());
        int visibleTop = top + topInset();
        for (int i = 0; i < ordered.size(); i++) {
            int rowY = y + grid.y(i);
            if (rowY + TechniqueGridLayout.HEIGHT <= visibleTop) continue;
            if (rowY >= visibleTop + viewport) break;
            var id = ordered.get(i).id();
            var hit = new TechniqueTiles.Hit(id, x + grid.x(i), rowY, grid.cellWidth(), TechniqueGridLayout.HEIGHT);
            hits.add(hit);
            boolean over = py >= visibleTop && py < visibleTop + viewport && hit.contains(px, py);
            if (over) hovered = id;
            TechniqueTiles.draw(graphics, context, hit, id.equals(selected), over);
        }
        return grid.height();
    }

    @Override public void renderOverlay(GuiGraphics g, PanelContext c, int mouseX, int mouseY) {
        if (detail) {
            growth.renderOverlay(g, c, mouseX, mouseY);
            return;
        }
        if (!visible) return;
        g.fill(x, dockY, x + width, dockY + 1, InkPanelTheme.DIVIDER);
        if (selected != null) {
            PanelAssets.draw(g, TechniqueTiles.icon(c, selected), x + 1, dockY + 7, 26);
            var definition = c.technique(selected).orElse(null);
            var palette = TechniqueTiles.palette(c, selected);
            g.drawString(c.font(), InkPanelTheme.fit(c.font(), TechniqueTiles.name(c, selected), Math.max(1, width - 34)),
                    x + 31, dockY + 6, palette.text(), false);
            String category = definition != null && definition.isCore() ? HeartTechniques.slot(definition).title()
                    : TechniqueTiles.category(c, definition);
            String line = TechniqueTiles.grade(c, definition) + " · " + category;
            g.drawString(c.font(), InkPanelTheme.fit(c.font(), line, Math.max(1, width - 34)),
                    x + 31, dockY + 20, InkPanelTheme.MUTED, false);
        }
        if (hovered != null) g.renderComponentTooltip(c.font(), TechniqueTiles.details(c, hovered), mouseX, mouseY);
    }
}
