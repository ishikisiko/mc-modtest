package com.example.myvillage.client.sim;

import com.example.myvillage.client.cultivation.panel.PanelTheme;
import com.example.myvillage.client.portrait.PortraitTextures;
import com.example.myvillage.sim.runtime.net.SectDialoguePayload;
import com.example.myvillage.sim.runtime.net.SectIntentPayload;
import com.example.myvillage.sim.runtime.player.SectDialogueScenes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The sect dialogue (守山执事 / 长老): draws one {@link SectDialoguePayload} page the server built.
 * The left column holds the avatar's 64-px portrait with the name and role under it; the right
 * column the title {@code avatar · role · sect}, a muted line with the region and the player's
 * standing, and the wrapped lines; under both, one button per offered option. JOIN and LEAVE send a
 * {@link SectIntentPayload} (the server checks it and answers with a new page, shown in place by
 * {@link #update}); FAREWELL tells the server and closes. Esc closes. It does not pause the game.
 *
 * <p>After every layout each button is logged as
 * {@code SECT_DIALOGUE option=<JOIN|LEAVE|FAREWELL> x= y= w= h=} in screen pixels (GUI
 * coordinates times the GUI scale), for the headless evidence script to click.
 */
public final class SectDialogueScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger(SectDialogueScreen.class);
    private static final String KEY = "screen.myvillage.sect_dialogue.";
    static final int MAX_PANEL_WIDTH = 400;
    static final int PORTRAIT = 64;
    static final int COLUMN_GAP = 10;
    static final int PADDING = 10;
    static final int BUTTON_WIDTH = 80;
    static final int BUTTON_HEIGHT = 20;
    static final int BUTTON_GAP = 8;
    static final int LINE_HEIGHT = 10;
    static final int PARAGRAPH_GAP = 4;

    private SectDialoguePayload page;
    private List<List<FormattedCharSequence>> paragraphs = List.of();
    private Component heading = Component.empty();
    private Component subheading = Component.empty();
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private String portraitName = "";
    private String portraitRole = "";

    public SectDialogueScreen(SectDialoguePayload page) {
        super(Component.translatable(KEY + "title", page.avatarName(), role(page.role()), page.sectName()));
        this.page = page;
    }

    /** The avatar this dialogue is with. */
    public int entityId() {
        return page.entityId();
    }

    /** Shows a new page from the same avatar in place. */
    public void update(SectDialoguePayload next) {
        this.page = next;
        rebuildWidgets();
    }

    private static Component role(String role) {
        return Component.translatableWithFallback(KEY + "role." + role, role);
    }

    @Override
    protected void init() {
        heading = Component.translatable(KEY + "title", page.avatarName(), role(page.role()), page.sectName());
        subheading = subheading(page);
        panelWidth = Math.min(MAX_PANEL_WIDTH, width - 16);
        int textWidth = Math.max(40, panelWidth - 2 * PADDING - PORTRAIT - COLUMN_GAP);
        int columnWidth = PORTRAIT + COLUMN_GAP - 4;
        portraitName = PanelTheme.fit(font, page.avatarName(), columnWidth);
        portraitRole = PanelTheme.fit(font, role(page.role()).getString(), columnWidth);
        List<List<FormattedCharSequence>> wrapped = new ArrayList<>();
        int bodyHeight = 0;
        for (Component line : page.lines()) {
            List<FormattedCharSequence> rows = font.split(line, textWidth);
            wrapped.add(rows);
            bodyHeight += rows.size() * LINE_HEIGHT + PARAGRAPH_GAP;
        }
        paragraphs = wrapped;
        int leftHeight = PORTRAIT + 4 + LINE_HEIGHT + LINE_HEIGHT;
        int rightHeight = LINE_HEIGHT + 2 + LINE_HEIGHT + 6 + bodyHeight;
        panelHeight = PADDING + Math.max(leftHeight, rightHeight) + 6 + BUTTON_HEIGHT + PADDING;
        panelLeft = (width - panelWidth) / 2;
        panelTop = Math.max(4, (height - panelHeight) / 2);

        List<SectDialogueScenes.Option> options = new ArrayList<>();
        for (int id : page.options()) {
            options.add(SectDialogueScenes.Option.of(id));
        }
        int total = options.size() * BUTTON_WIDTH + Math.max(0, options.size() - 1) * BUTTON_GAP;
        int x = (width - total) / 2;
        int y = panelTop + panelHeight - PADDING - BUTTON_HEIGHT;
        double scale = minecraft == null ? 1.0 : minecraft.getWindow().getGuiScale();
        for (SectDialogueScenes.Option option : options) {
            Button button = Button.builder(Component.translatable(KEY + "option." + option.name().toLowerCase(Locale.ROOT)),
                            b -> choose(option))
                    .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build();
            addRenderableWidget(button);
            LOGGER.info("SECT_DIALOGUE option={} x={} y={} w={} h={}", option.name(),
                    Math.round(button.getX() * scale), Math.round(button.getY() * scale),
                    Math.round(button.getWidth() * scale), Math.round(button.getHeight() * scale));
            x += BUTTON_WIDTH + BUTTON_GAP;
        }
    }

    private static Component subheading(SectDialoguePayload page) {
        MutableComponent out = Component.literal(page.regionName());
        if (!page.myRank().isEmpty()) {
            out.append(" · ").append(Component.translatableWithFallback("world_sim.rank." + page.myRank(), page.myRank()));
        }
        if (!page.myRank().isEmpty() || page.myStanding() != 0) {
            out.append(" · ").append(Component.translatable(KEY + "standing", String.valueOf(page.myStanding())));
        }
        return out;
    }

    private void choose(SectDialogueScenes.Option option) {
        PacketDistributor.sendToServer(new SectIntentPayload(option, page.entityId(), page.sectId()));
        if (option == SectDialogueScenes.Option.FAREWELL) {
            onClose();
        }
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
        int left = panelLeft + PADDING;
        int top = panelTop + PADDING;
        PortraitTextures.draw(graphics, page.portrait(), left, top, PORTRAIT);
        int nameY = top + PORTRAIT + 4;
        graphics.drawString(font, portraitName, left, nameY, PanelTheme.GOLD_BRIGHT, false);
        graphics.drawString(font, portraitRole, left, nameY + LINE_HEIGHT, PanelTheme.MUTED, false);

        int x = left + PORTRAIT + COLUMN_GAP;
        int y = top;
        graphics.drawString(font, heading, x, y, PanelTheme.GOLD_BRIGHT, false);
        y += LINE_HEIGHT + 2;
        graphics.drawString(font, subheading, x, y, PanelTheme.MUTED, false);
        y += LINE_HEIGHT + 2;
        graphics.fill(x, y, panelLeft + panelWidth - PADDING, y + 1, PanelTheme.DIVIDER);
        y += 4;
        for (List<FormattedCharSequence> rows : paragraphs) {
            for (FormattedCharSequence row : rows) {
                graphics.drawString(font, row, x, y, PanelTheme.TEXT, false);
                y += LINE_HEIGHT;
            }
            y += PARAGRAPH_GAP;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
