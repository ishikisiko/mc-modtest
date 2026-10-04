package com.example.myvillage.client.cultivation;

import com.example.myvillage.client.cultivation.panel.MeditationPage;
import com.example.myvillage.client.cultivation.panel.OverviewPage;
import com.example.myvillage.client.cultivation.panel.PanelButton;
import com.example.myvillage.client.cultivation.panel.PanelContext;
import com.example.myvillage.client.cultivation.panel.PanelPage;
import com.example.myvillage.client.cultivation.panel.PanelTheme;
import com.example.myvillage.client.cultivation.panel.TechniquesPage;
import com.example.myvillage.cultivation.CultivationProfile;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * The cultivation panel (H): a framed hub with a page rail on the left. The screen owns the
 * frame, the header and footer status, page switching, and body scrolling; each page under
 * {@code panel/} draws one system. It is read-only apart from the meditation page's four
 * bounded intents.
 */
public final class CultivationProfileScreen extends Screen {
    private static final int MAX_PANEL_WIDTH = 480;
    private static final int MAX_PANEL_HEIGHT = 246;
    private static final int SCREEN_MARGIN = 4;
    private static final int HEADER_HEIGHT = 32;
    private static final int FOOTER_HEIGHT = 14;
    private static final int PADDING = 5;
    private static final int BODY_GAP = 4;
    private static final int TAB_HEIGHT = 18;
    private static final int TAB_GAP = 3;
    private static final int FACE_SIZE = 22;
    private static final int SCROLL_STEP = 14;

    /** The page the panel was last left on; reopening H returns to it. */
    private static View lastView = View.PROFILE;

    private final Map<View, PanelPage> pages = new EnumMap<>(View.class);
    private final Map<View, PanelButton> tabs = new EnumMap<>(View.class);
    private final Map<View, Integer> docks = new EnumMap<>(View.class);
    private final Map<View, Integer> scrolls = new EnumMap<>(View.class);
    private View view = lastView;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int railWidth;
    private int bodyX;
    private int bodyY;
    private int bodyWidth;
    private int bodyHeight;
    private int contentHeight;

    private enum View {
        PROFILE("screen.myvillage.cultivation.tab.profile"),
        MEDITATION("screen.myvillage.cultivation.tab.meditation"),
        TECHNIQUES("screen.myvillage.cultivation.tab.techniques");

        private final String titleKey;

        View(String titleKey) {
            this.titleKey = titleKey;
        }
    }

    public CultivationProfileScreen() {
        super(Component.translatable("screen.myvillage.cultivation.title"));
    }

    @Override
    protected void init() {
        updatePanelBounds();
        pages.clear();
        tabs.clear();
        docks.clear();
        pages.put(View.PROFILE, new OverviewPage());
        pages.put(View.MEDITATION, new MeditationPage());
        pages.put(View.TECHNIQUES, new TechniquesPage());

        int tabY = panelTop + HEADER_HEIGHT + BODY_GAP;
        for (View candidate : View.values()) {
            tabs.put(candidate, addRenderableWidget(new PanelButton(
                    PanelButton.Style.NAV,
                    PanelTheme.GOLD,
                    panelLeft + 3,
                    tabY,
                    railWidth - 3,
                    TAB_HEIGHT,
                    Component.translatable(candidate.titleKey),
                    button -> setView(candidate))));
            tabY += TAB_HEIGHT + TAB_GAP;
        }
        for (Map.Entry<View, PanelPage> page : pages.entrySet()) {
            docks.put(page.getKey(), page.getValue().init(
                    bodyX, bodyY, bodyWidth, bodyHeight, this::addRenderableWidget));
        }
        refreshWidgets(PanelContext.capture(minecraft, font));
    }

    private void updatePanelBounds() {
        panelWidth = Math.max(1, Math.min(MAX_PANEL_WIDTH, width - SCREEN_MARGIN * 2));
        panelHeight = Math.max(1, Math.min(MAX_PANEL_HEIGHT, height - SCREEN_MARGIN * 2));
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;
        int widestTab = 0;
        for (View candidate : View.values()) {
            widestTab = Math.max(widestTab, font.width(Component.translatable(candidate.titleKey)));
        }
        railWidth = Math.max(44, Math.min(panelWidth / 4, widestTab + 22));
        bodyX = panelLeft + railWidth + PADDING + 1;
        bodyY = panelTop + HEADER_HEIGHT + BODY_GAP;
        bodyWidth = Math.max(1, panelLeft + panelWidth - PADDING - 4 - bodyX);
        bodyHeight = Math.max(1, panelTop + panelHeight - FOOTER_HEIGHT - BODY_GAP - bodyY);
    }

    private void setView(View newView) {
        view = newView;
        lastView = newView;
        refreshWidgets(PanelContext.capture(minecraft, font));
    }

    @Override
    public void tick() {
        refreshWidgets(PanelContext.capture(minecraft, font));
    }

    private void refreshWidgets(PanelContext context) {
        for (Map.Entry<View, PanelButton> tab : tabs.entrySet()) {
            boolean open = tab.getKey() == view;
            tab.getValue().setSelected(open);
            tab.getValue().active = !open;
        }
        for (Map.Entry<View, PanelPage> page : pages.entrySet()) {
            boolean visible = page.getKey() == view && context.profile() != null;
            page.getValue().setVisible(visible);
            page.getValue().refresh(context, visible);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int right = panelLeft + panelWidth;
        int bottom = panelTop + panelHeight;
        int headerBottom = panelTop + HEADER_HEIGHT;
        int footerTop = bottom - FOOTER_HEIGHT;
        graphics.fillGradient(0, 0, width, height, PanelTheme.BACKDROP_TOP, PanelTheme.BACKDROP_BOTTOM);
        PanelTheme.frame(graphics, panelLeft, panelTop, panelWidth, panelHeight);
        graphics.fillGradient(
                panelLeft + 3, panelTop + 3, right - 3, headerBottom, PanelTheme.HEADER_TOP, PanelTheme.HEADER_BOTTOM);
        graphics.fill(panelLeft + 3, headerBottom, right - 3, headerBottom + 1, PanelTheme.GOLD_DIM);
        graphics.fill(panelLeft + 3, headerBottom + 1, panelLeft + railWidth, footerTop, PanelTheme.RAIL);
        graphics.fill(panelLeft + railWidth, headerBottom + 1, panelLeft + railWidth + 1, footerTop, PanelTheme.DIVIDER);
        graphics.fill(panelLeft + 3, footerTop, right - 3, footerTop + 1, PanelTheme.DIVIDER);

        PanelContext context = PanelContext.capture(minecraft, font);
        drawHeader(graphics, context, right);
        drawFooter(graphics, context, right, footerTop);
        if (context.profile() == null) {
            graphics.drawCenteredString(
                    font,
                    Component.translatable("screen.myvillage.cultivation.no_snapshot"),
                    bodyX + bodyWidth / 2,
                    bodyY + bodyHeight / 2 - 4,
                    PanelTheme.MUTED);
        } else {
            drawPage(graphics, context);
        }

        refreshWidgets(context);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawPage(GuiGraphics graphics, PanelContext context) {
        int viewport = viewportHeight();
        int scroll = Math.min(scrolls.getOrDefault(view, 0), Math.max(0, contentHeight - viewport));
        graphics.enableScissor(bodyX, bodyY, bodyX + bodyWidth, bodyY + viewport);
        contentHeight = pages.get(view).render(graphics, context, bodyX, bodyY - scroll, bodyWidth, viewport);
        graphics.disableScissor();

        int maxScroll = Math.max(0, contentHeight - viewport);
        scroll = Math.min(scroll, maxScroll);
        scrolls.put(view, scroll);
        if (maxScroll > 0) {
            int trackX = bodyX + bodyWidth + 2;
            int thumbHeight = Math.max(12, viewport * viewport / contentHeight);
            int thumbY = bodyY + (viewport - thumbHeight) * scroll / maxScroll;
            graphics.fill(trackX, bodyY, trackX + 2, bodyY + viewport, PanelTheme.BAR_TRACK);
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, PanelTheme.GOLD);
        }
    }

    /** The body's visible height: the page rectangle less what the open page docks under it. */
    private int viewportHeight() {
        return Math.max(1, bodyHeight - docks.getOrDefault(view, 0));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The screen renders a stable, sharp backdrop before Screen renders its widgets.
    }

    private void drawHeader(GuiGraphics graphics, PanelContext context, int right) {
        int faceX = panelLeft + 8;
        int faceY = panelTop + 6;
        graphics.fill(faceX - 1, faceY - 1, faceX + FACE_SIZE + 1, faceY + FACE_SIZE + 1, PanelTheme.GOLD_DIM);
        if (minecraft != null && minecraft.player != null) {
            PlayerFaceRenderer.draw(graphics, minecraft.player.getSkin(), faceX, faceY, FACE_SIZE);
        } else {
            graphics.fill(faceX, faceY, faceX + FACE_SIZE, faceY + FACE_SIZE, PanelTheme.DIVIDER);
        }

        CultivationProfile profile = context.profile();
        String calendar = context.text("screen.myvillage.cultivation.calendar") + " " + context.calendarValue();
        String lifespan = context.text("screen.myvillage.cultivation.card.lifespan")
                + " " + context.remainingLifespanValue();
        int statusWidth = Math.max(font.width(calendar), font.width(lifespan));
        int textX = faceX + FACE_SIZE + 7;
        int textWidth = right - 9 - textX;
        boolean showStatus = profile != null && statusWidth + 60 <= textWidth;
        if (showStatus) {
            graphics.drawString(font, calendar, right - 9 - font.width(calendar), panelTop + 7, PanelTheme.MUTED, false);
            graphics.drawString(
                    font,
                    lifespan,
                    right - 9 - font.width(lifespan),
                    panelTop + 18,
                    context.time() != null && context.time().lifespanAvailable() && context.time().exhausted()
                            ? PanelTheme.RED
                            : PanelTheme.MUTED,
                    false);
            textWidth -= statusWidth + 10;
        }

        String playerName = minecraft != null && minecraft.player != null
                ? minecraft.player.getDisplayName().getString()
                : Component.translatable("screen.myvillage.cultivation.unknown_player").getString();
        graphics.drawString(font, PanelTheme.fit(font, playerName, textWidth), textX, panelTop + 7, PanelTheme.TEXT, false);

        String standing;
        if (profile == null || minecraft == null || minecraft.level == null) {
            standing = Component.translatable("screen.myvillage.cultivation.waiting").getString();
        } else {
            standing = context.realmName(profile.realmId()).getString()
                    + " · "
                    + context.stageName(profile.realmId(), profile.stageId()).getString();
        }
        graphics.drawString(
                font, PanelTheme.fit(font, standing, textWidth), textX, panelTop + 18, PanelTheme.GOLD_BRIGHT, false);
    }

    private void drawFooter(GuiGraphics graphics, PanelContext context, int right, int footerTop) {
        int textY = footerTop + 3;
        int x = panelLeft + 8;
        if (context.profile() != null) {
            PanelTheme.diamond(graphics, x + 2, textY + 3, 2, context.sessionColor());
            graphics.drawString(font, context.sessionText(), x + 9, textY, context.sessionColor(), false);
        }
        String hint = Component.translatable(
                "screen.myvillage.cultivation.close_hint",
                ClientCultivationKeyMappings.OPEN_PROFILE.getTranslatedKeyMessage()).getString();
        if (context.profile() != null) {
            hint += "   " + Component.translatable(
                    "screen.myvillage.cultivation.schema", context.profile().schemaVersion()).getString();
        }
        graphics.drawString(font, hint, right - 8 - font.width(hint), textY, PanelTheme.FAINT, false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int viewport = viewportHeight();
        boolean overBody = mouseX >= bodyX
                && mouseX < bodyX + bodyWidth + 4
                && mouseY >= bodyY
                && mouseY < bodyY + viewport;
        if (overBody && contentHeight > viewport) {
            int maxScroll = contentHeight - viewport;
            int scroll = scrolls.getOrDefault(view, 0) - (int) Math.signum(scrollY) * SCROLL_STEP;
            scrolls.put(view, Math.max(0, Math.min(maxScroll, scroll)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ClientCultivationKeyMappings.OPEN_PROFILE.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
