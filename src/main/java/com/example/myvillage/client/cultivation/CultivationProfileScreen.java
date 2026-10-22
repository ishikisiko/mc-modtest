package com.example.myvillage.client.cultivation;

import com.example.myvillage.client.cultivation.panel.MeditationPage;
import com.example.myvillage.client.cultivation.panel.OverviewPage;
import com.example.myvillage.client.cultivation.panel.PanelButton;
import com.example.myvillage.client.cultivation.panel.PanelContext;
import com.example.myvillage.client.cultivation.panel.PanelPage;
import com.example.myvillage.client.cultivation.panel.InkPanelTheme;
import com.example.myvillage.client.cultivation.panel.PanelFonts;
import com.example.myvillage.client.cultivation.panel.PanelAssets;
import net.minecraft.client.gui.components.AbstractWidget;
import com.example.myvillage.client.cultivation.panel.TechniquesPage;
import com.example.myvillage.client.cultivation.panel.WorldPage;
import com.example.myvillage.client.cultivation.panel.CultivationInkTheme;
import com.example.myvillage.cultivation.CultivationProfile;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * The cultivation panel (H): a framed hub with a page rail on the left. The screen owns the
 * frame, the header and footer status, page switching, and body scrolling; each page under
 * {@code panel/} draws one system. It is read-only apart from the meditation page's four
 * bounded intents. Pages may take clicks and hover inside the body (the 天下 page drills down
 * through rows); the screen only forwards them.
 */
public final class CultivationProfileScreen extends Screen {
    private static final int MAX_PANEL_WIDTH = 480;
    private static final int MAX_PANEL_HEIGHT = 282;
    private static final int SCREEN_MARGIN = 4;
    private static final int HEADER_HEIGHT = 32;
    private static final int FOOTER_HEIGHT = 14;
    private static final int PADDING = 5;
    private static final int BODY_GAP = 4;
    private static final int TAB_HEIGHT = 18;
    private static final int TAB_GAP = 3;
    private static final int FACE_SIZE = 22;
    /** Width the header keeps for the player's name and standing before it shortens the date. */
    private static final int HEADER_NAME_ROOM = 130;
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
    private final Map<View, Integer> heights = new EnumMap<>(View.class);
    /** Whether the open page was drawn this frame (it is not while no profile has arrived). */
    private boolean pageShown;

    private enum View {
        PROFILE("screen.myvillage.cultivation.tab.profile"),
        MEDITATION("screen.myvillage.cultivation.tab.meditation"),
        TECHNIQUES("screen.myvillage.cultivation.tab.techniques"),
        SKILLS("screen.myvillage.cultivation.tab.skills"),
        WORLD("screen.myvillage.cultivation.tab.world"),
        FATES("screen.myvillage.cultivation.tab.fates"),
        CRAFTING("screen.myvillage.cultivation.tab.crafting");

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
        font = PanelFonts.get();
        heights.clear();
        updatePanelBounds();
        pages.clear();
        tabs.clear();
        docks.clear();
        pages.put(View.PROFILE, new OverviewPage());
        pages.put(View.MEDITATION, new MeditationPage());
        pages.put(View.TECHNIQUES, new TechniquesPage(id -> {
            ((com.example.myvillage.client.cultivation.panel.SkillBindingsPage) pages.get(View.SKILLS)).selectTechnique(id);
            setView(View.SKILLS);
        }));
        pages.put(View.SKILLS, new com.example.myvillage.client.cultivation.panel.SkillBindingsPage());
        pages.put(View.WORLD, new WorldPage());
        pages.put(View.FATES, new com.example.myvillage.client.cultivation.panel.FatesPage());
        pages.put(View.CRAFTING, new com.example.myvillage.client.cultivation.panel.CraftingPage());

        int tabY = panelTop + HEADER_HEIGHT + BODY_GAP;
        for (View candidate : View.values()) {
            tabs.put(candidate, addPanelWidget(new PanelButton(
                    PanelButton.Style.NAV,
                    InkPanelTheme.GOLD,
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
                    bodyX, bodyY, bodyWidth, bodyHeight, this::addPanelWidget));
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
        railWidth = Math.max(58, Math.min(panelWidth / 4, widestTab + 36));
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
            tab.getValue().setInk(true);
            tab.getValue().setIcon(PanelAssets.navigation(switch(tab.getKey()) {
                case PROFILE -> "profile"; case MEDITATION -> "meditation"; case TECHNIQUES -> "techniques";
                case SKILLS -> "skills"; case WORLD -> "world"; case FATES -> "fates"; case CRAFTING -> "crafting";
            }));
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
        graphics.fillGradient(0, 0, width, height, InkPanelTheme.BACKDROP_TOP, InkPanelTheme.BACKDROP_BOTTOM);
        CultivationInkTheme.paper(graphics, panelLeft, panelTop, panelWidth, panelHeight);
        graphics.fill(panelLeft + 5, headerBottom, right - 5, headerBottom + 1, CultivationInkTheme.RULE);
        graphics.fill(panelLeft + railWidth, headerBottom + 1, panelLeft + railWidth + 1, footerTop, CultivationInkTheme.RULE);
        graphics.fill(panelLeft + 5, footerTop, right - 5, footerTop + 1, CultivationInkTheme.RULE);

        PanelContext context = PanelContext.capture(minecraft, font);
        drawHeader(graphics, context, right);
        drawFooter(graphics, context, right, footerTop);
        pageShown = context.profile() != null;
        if (!pageShown) {
            graphics.drawCenteredString(
                    font,
                    Component.translatable("screen.myvillage.cultivation.no_snapshot"),
                    bodyX + bodyWidth / 2,
                    bodyY + bodyHeight / 2 - 4,
                    InkPanelTheme.MUTED);
        } else {
            drawPage(graphics, context, mouseX, mouseY);
        }

        refreshWidgets(context);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (pageShown) pages.get(view).renderOverlay(graphics, context, mouseX, mouseY);
    }

    private void drawPage(GuiGraphics graphics, PanelContext context, int mouseX, int mouseY) {
        PanelPage page = pages.get(view);
        if (page.takeScrollToTop()) {
            scrolls.put(view, 0);
        }
        int viewport = viewportHeight();
        int scroll = Math.min(scrolls.getOrDefault(view, 0), Math.max(0, heights.getOrDefault(view, viewport) - viewport));
        boolean inside = insideViewport(mouseX, mouseY);
        page.pointer(inside ? mouseX : -1, inside ? mouseY : -1);
        int viewportY = bodyY + page.topInset();
        graphics.enableScissor(bodyX, viewportY, bodyX + bodyWidth, viewportY + viewport);
        contentHeight = page.render(graphics, context, bodyX, viewportY - scroll, bodyWidth, viewport);
        heights.put(view, contentHeight);
        graphics.disableScissor();

        int maxScroll = Math.max(0, contentHeight - viewport);
        scroll = Math.min(scroll, maxScroll);
        scrolls.put(view, scroll);
        if (maxScroll > 0) {
            int trackX = bodyX + bodyWidth + 2;
            int thumbHeight = Math.max(12, viewport * viewport / contentHeight);
            int thumbY = viewportY + (viewport - thumbHeight) * scroll / maxScroll;
            graphics.fill(trackX, viewportY, trackX + 2, viewportY + viewport, InkPanelTheme.BAR_TRACK);
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, InkPanelTheme.GOLD);
        }
    }

    /** The body's visible height: the page rectangle less what the open page docks under it. */
    private int viewportHeight() {
        return Math.max(1, bodyHeight - pages.get(view).topInset() - docks.getOrDefault(view, 0));
    }

    private boolean insideViewport(double mouseX, double mouseY) {
        return mouseX >= bodyX
                && mouseX < bodyX + bodyWidth
                && mouseY >= bodyY + pages.get(view).topInset()
                && mouseY < bodyY + pages.get(view).topInset() + viewportHeight();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        PanelPage page = pages.get(view);
        if (pageShown && page != null && insideViewport(mouseX, mouseY)
                && page.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The screen renders a stable, sharp backdrop before Screen renders its widgets.
    }

    private void drawHeader(GuiGraphics g, PanelContext c, int right) {
        CultivationInkTheme.title(g, font, CultivationInkTheme.tr("title"), panelLeft + 10, panelTop + 8, 78);
        int textX = panelLeft + 96, textWidth = Math.max(1, right - textX - 9);
        String name = minecraft != null && minecraft.player != null ? minecraft.player.getDisplayName().getString() : "";
        String standing = c.profile() == null ? "" : c.stageName(c.profile().realmId(), c.profile().stageId()).getString();
        CultivationInkTheme.pair(g, font, name, standing, textX, panelTop + 6, textWidth, CultivationInkTheme.INK);
        CultivationInkTheme.pair(g, font, c.calendarValue(),
                c.text("screen.myvillage.cultivation.card.lifespan") + " " + c.remainingLifespanValue(),
                textX, panelTop + 19, textWidth, CultivationInkTheme.MUTED);
    }

    private void drawFooter(GuiGraphics g, PanelContext c, int right, int footerTop) {
        String hint = Component.translatable("screen.myvillage.cultivation.close_hint",
                ClientCultivationKeyMappings.OPEN_PROFILE.getTranslatedKeyMessage()).getString();
        CultivationInkTheme.pair(g, font, c.profile() == null ? c.unavailableText() : c.sessionText(),
                hint, panelLeft + 8, footerTop + 3, panelWidth - 16, CultivationInkTheme.MUTED);
    }

    private <T extends AbstractWidget> T addPanelWidget(T widget) {
        if (widget instanceof PanelButton button) button.setInk(true);
        return addRenderableWidget(widget);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int viewport = viewportHeight();
        boolean overBody = mouseX >= bodyX
                && mouseX < bodyX + bodyWidth + 4
                && mouseY >= bodyY + pages.get(view).topInset()
                && mouseY < bodyY + pages.get(view).topInset() + viewport;
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
        // While a text field (the 天下 page's search) has focus, H types an h instead of closing.
        if (getFocused() instanceof EditBox field && field.canConsumeInput()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if(view==View.SKILLS || view==View.TECHNIQUES) {
            int current=scrolls.getOrDefault(view,0),maximum=Math.max(0,contentHeight-viewportHeight());
            int next=switch(keyCode) {
                case org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN -> Math.min(maximum,current+Math.max(1,viewportHeight()-14));
                case org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP -> Math.max(0,current-Math.max(1,viewportHeight()-14));
                case org.lwjgl.glfw.GLFW.GLFW_KEY_HOME -> 0;
                case org.lwjgl.glfw.GLFW.GLFW_KEY_END -> maximum;
                default -> -1;
            };
            if(next>=0){scrolls.put(view,next);return true;}
        }
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
