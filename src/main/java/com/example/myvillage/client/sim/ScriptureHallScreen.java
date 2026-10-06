package com.example.myvillage.client.sim;

import com.example.myvillage.client.cultivation.panel.PanelTheme;
import com.example.myvillage.sim.runtime.net.ScriptureBorrowPayload;
import com.example.myvillage.sim.runtime.net.ScriptureHallPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The scripture hall (藏经阁) at one shelf: draws the {@link ScriptureHallPayload} the server built.
 * Title {@code sect · 藏经阁 · my rank}; for a member one row per borrowable technique
 * ({@code 《name》 · grade · category}) with a borrow button on the right (disabled and marked
 * 已借 once borrowed, the contribution cost in the label when it is above 0) and a muted hint that
 * mortal-grade methods come from the inheritance stele; for anyone else the reason in one line.
 * A borrow sends a {@link ScriptureBorrowPayload} (the server checks it and answers with a new
 * hall, shown in place by {@link #update}). Esc or the leave button closes. It does not pause.
 *
 * <p>After every layout each borrow button is logged as
 * {@code SCRIPTURE_HALL_UI technique=<id> borrowed=<true|false> x= y= w= h=} in screen pixels (GUI coordinates times
 * the GUI scale), for the headless evidence script to click.
 */
public final class ScriptureHallScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScriptureHallScreen.class);
    private static final String KEY = "screen.myvillage.scripture_hall.";
    static final int MAX_PANEL_WIDTH = 340;
    static final int PADDING = 10;
    static final int BORROW_WIDTH = 96;
    static final int CLOSE_WIDTH = 80;
    static final int BUTTON_HEIGHT = 20;
    static final int ROW_HEIGHT = 22;
    static final int LINE_HEIGHT = 10;

    private ScriptureHallPayload hall;
    private Component heading = Component.empty();
    private List<FormattedCharSequence> message = List.of();
    private final List<Row> rows = new ArrayList<>();
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int bodyTop;
    private int hintY;

    /** One drawn technique row: its text (already cut to fit) and where it sits. */
    private record Row(FormattedCharSequence text, int y, boolean borrowed) {
    }

    public ScriptureHallScreen(ScriptureHallPayload hall) {
        super(title(hall));
        this.hall = hall;
    }

    /** The shelf this hall was opened at. */
    public BlockPos pos() {
        return hall.pos();
    }

    /** Shows a new hall from the same shelf in place. */
    public void update(ScriptureHallPayload next) {
        this.hall = next;
        rebuildWidgets();
    }

    private static Component title(ScriptureHallPayload hall) {
        Component rank = hall.myRank().isEmpty()
                ? Component.translatable(KEY + "visitor")
                : Component.translatableWithFallback("world_sim.rank." + hall.myRank(), hall.myRank());
        return Component.translatable(KEY + "title", hall.sectName(), rank);
    }

    @Override
    protected void init() {
        heading = title(hall);
        rows.clear();
        panelWidth = Math.min(MAX_PANEL_WIDTH, width - 16);
        int textWidth = panelWidth - 2 * PADDING;
        boolean member = hall.member();
        int bodyHeight;
        if (!member) {
            message = font.split(Component.translatableWithFallback(KEY + "refused." + hall.reason(), hall.reason()),
                    textWidth);
            bodyHeight = message.size() * LINE_HEIGHT;
        } else if (hall.entries().isEmpty()) {
            message = font.split(Component.translatable(KEY + "empty"), textWidth);
            bodyHeight = message.size() * LINE_HEIGHT;
        } else {
            message = List.of();
            bodyHeight = hall.entries().size() * ROW_HEIGHT;
        }
        int hintHeight = member ? LINE_HEIGHT + 6 : 0;
        panelHeight = PADDING + LINE_HEIGHT + 6 + bodyHeight + 6 + hintHeight + BUTTON_HEIGHT + PADDING;
        panelLeft = (width - panelWidth) / 2;
        panelTop = Math.max(4, (height - panelHeight) / 2);
        bodyTop = panelTop + PADDING + LINE_HEIGHT + 6;
        hintY = bodyTop + bodyHeight + 6;
        double scale = minecraft == null ? 1.0 : minecraft.getWindow().getGuiScale();

        if (member) {
            int buttonX = panelLeft + panelWidth - PADDING - BORROW_WIDTH;
            int rowTextWidth = textWidth - BORROW_WIDTH - 6;
            int y = bodyTop;
            for (ScriptureHallPayload.Entry entry : hall.entries()) {
                Component text = Component.translatable(KEY + "row", entry.name(), grade(entry.grade()),
                        category(entry.category()));
                rows.add(new Row(Language.getInstance().getVisualOrder(font.substrByWidth(text, rowTextWidth)),
                        y, entry.borrowed()));
                Button button = Button.builder(label(entry), b -> borrow(entry.techniqueId()))
                        .bounds(buttonX, y, BORROW_WIDTH, BUTTON_HEIGHT)
                        .build();
                button.active = !entry.borrowed();
                addRenderableWidget(button);
                LOGGER.info("SCRIPTURE_HALL_UI technique={} borrowed={} x={} y={} w={} h={}", entry.techniqueId(),
                        entry.borrowed(), Math.round(button.getX() * scale), Math.round(button.getY() * scale),
                        Math.round(button.getWidth() * scale), Math.round(button.getHeight() * scale));
                y += ROW_HEIGHT;
            }
        }
        addRenderableWidget(Button.builder(Component.translatable(KEY + "close"), b -> onClose())
                .bounds((width - CLOSE_WIDTH) / 2, panelTop + panelHeight - PADDING - BUTTON_HEIGHT,
                        CLOSE_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    private static Component label(ScriptureHallPayload.Entry entry) {
        if (entry.borrowed()) {
            return Component.translatable(KEY + "borrowed");
        }
        return entry.cost() > 0
                ? Component.translatable(KEY + "borrow_cost", String.valueOf(entry.cost()))
                : Component.translatable(KEY + "borrow");
    }

    private static Component grade(int grade) {
        return Component.translatable("screen.myvillage.cultivation.grade_name." + grade);
    }

    private static Component category(String category) {
        return Component.translatableWithFallback("screen.myvillage.cultivation.category." + category, category);
    }

    private void borrow(String techniqueId) {
        PacketDistributor.sendToServer(new ScriptureBorrowPayload(hall.pos(), techniqueId));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, width, height, 0x60000000, 0x90000000);
        int right = panelLeft + panelWidth;
        int bottom = panelTop + panelHeight;
        graphics.fill(panelLeft - 1, panelTop - 1, right + 1, bottom + 1, PanelTheme.GOLD_DIM);
        graphics.fill(panelLeft, panelTop, right, bottom, PanelTheme.PANEL);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int x = panelLeft + PADDING;
        int y = panelTop + PADDING;
        graphics.drawString(font, heading, x, y, PanelTheme.GOLD_BRIGHT, false);
        y += LINE_HEIGHT + 2;
        graphics.fill(x, y, panelLeft + panelWidth - PADDING, y + 1, PanelTheme.DIVIDER);
        y = bodyTop;
        for (FormattedCharSequence line : message) {
            graphics.drawString(font, line, x, y, PanelTheme.TEXT, false);
            y += LINE_HEIGHT;
        }
        for (Row row : rows) {
            graphics.drawString(font, row.text(), x, row.y() + (BUTTON_HEIGHT - 8) / 2,
                    row.borrowed() ? PanelTheme.MUTED : PanelTheme.TEXT, false);
        }
        if (hall.member()) {
            graphics.drawString(font, Component.translatable(KEY + "mortal_hint"), x, hintY, PanelTheme.MUTED, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
