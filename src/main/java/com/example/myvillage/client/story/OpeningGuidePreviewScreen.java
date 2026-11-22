package com.example.myvillage.client.story;

import com.example.myvillage.alchemy.AlchemyRules;
import com.example.myvillage.block.ModBlocks;
import com.example.myvillage.client.cultivation.panel.CultivationInkTheme;
import com.example.myvillage.client.cultivation.panel.PanelAssets;
import com.example.myvillage.client.cultivation.panel.PanelButton;
import com.example.myvillage.client.cultivation.panel.PanelFonts;
import com.example.myvillage.client.portrait.PortraitTextures;
import com.example.myvillage.client.townmap.ClientTownMap;
import com.example.myvillage.forging.ForgingRules;
import com.example.myvillage.forging.PreparationBlockEntity;
import com.example.myvillage.forging.Workpiece;
import com.example.myvillage.forging.WorkshopItems;
import com.example.myvillage.forging.WorkshopRules;
import com.example.myvillage.item.ModItems;
import com.example.myvillage.portrait.PortraitAssign;
import com.example.myvillage.portrait.PortraitSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import static com.example.myvillage.client.cultivation.panel.CultivationInkTheme.*;
import static com.example.myvillage.client.story.OpeningGuidePreview.*;

final class OpeningGuidePreviewScreen extends Screen {
    private static final String PICKAXE = "forged_pickaxe";
    private static final int DEMO_HITS = 3;
    private static final int WINDOW_WIDTH = 456;
    private static final int WINDOW_HEIGHT = 282;
    private static final int TEXT_ROW = 12;
    private static final PortraitSpec HERBALIST = PortraitAssign.of(23, true, "qi_refining", "rogue", -1, 29,
            new int[]{2000, 2000, 2000, 2000, 2000}, new int[]{45, 20, 60, 20, 72}, 0, true);
    private static final PortraitSpec SMITH = PortraitAssign.of(91, false, "qi_refining", "rogue", -1, 47,
            new int[]{2000, 2000, 2000, 2000, 2000}, new int[]{50, 25, 50, 20, 70}, 0, true);

    private enum Tab {
        JOURNAL("入门指引"), NPC("店铺交谈"), ALCHEMY("青铜丹炉"), FORGE("铁作坊");
        final String label;
        Tab(String label) { this.label = label; }
    }

    private enum Detail {
        NONE, FORMULA, HERB_SOURCE, HERB_TRADE, FORGE_ROUTE
    }

    private record Action(String label, Runnable run) {}
    private record Card(String title, String text, List<ItemStack> materials, List<Action> actions) {}

    private final Screen parent;
    private final Session session;
    private Tab tab = Tab.JOURNAL;
    private Detail detail = Detail.NONE;
    private Card card;
    private StoryLayout.Rect window;
    private StoryLayout.Rect body;
    private StoryLayout.Rect workshopTarget;
    private int scroll;
    private int scrollMax;

    OpeningGuidePreviewScreen(Screen parent, Session session) {
        super(Component.literal("入门教学预览"));
        this.parent = parent;
        this.session = session;
    }

    @Override
    protected void init() {
        font = PanelFonts.get();
        int w = Math.max(1, Math.min(WINDOW_WIDTH, width - 12));
        int h = Math.max(1, Math.min(WINDOW_HEIGHT, height - 12));
        window = new StoryLayout.Rect((width - w) / 2, (height - h) / 2, w, h);
        int inside = Math.max(1, w - 20);
        int x = window.x() + 10;
        card = selectCard();

        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Tab choice = tabs[i];
            int start = i * inside / tabs.length;
            int end = (i + 1) * inside / tabs.length;
            var button = button(x + start, window.y() + 30, Math.max(1, end - start - 2), 19,
                    choice.label, () -> select(choice), false, PanelButton.Style.NAV);
            button.setSelected(choice == tab);
        }
        button(window.right() - 72, window.y() + 9, 62, 16, "返回行迹", this::onClose, false, PanelButton.Style.ACTION);

        int rows = (card.actions().size() + 1) / 2;
        int actionsY = window.bottom() - 11 - rows * 21;
        int actionWidth = Math.max(1, (inside - 4) / 2);
        for (int i = 0; i < card.actions().size(); i++) {
            Action action = card.actions().get(i);
            button(x + (i % 2) * (actionWidth + 4), actionsY + (i / 2) * 21,
                    card.actions().size() == 1 ? inside : actionWidth, 18,
                    action.label(), action.run(), i == 0, PanelButton.Style.ACTION);
        }
        int bodyTop = window.y() + 54;
        if (tab == Tab.JOURNAL) {
            Lesson[] lessons = Lesson.values();
            for (int i = 0; i < lessons.length; i++) {
                Lesson lesson = lessons[i];
                int start = i * inside / lessons.length;
                int end = (i + 1) * inside / lessons.length;
                var choice = button(x + start, bodyTop, Math.max(1, end - start - 3), 18,
                        lesson.title, () -> lesson(lesson, Tab.JOURNAL), false, PanelButton.Style.ACTION);
                choice.setSelected(lesson == session.lesson);
                if (inside >= 285) choice.setIcon(PanelAssets.navigation(lesson.icon));
            }
            bodyTop += 25;
        }
        body = new StoryLayout.Rect(x, bodyTop, inside, Math.max(1, actionsY - 5 - bodyTop));
    }

    private PanelButton button(int x, int y, int w, int h, String label, Runnable run,
                               boolean primary, PanelButton.Style style) {
        var button = new PanelButton(style, JADE, x, y, w, h, Component.literal(label), ignored -> run.run());
        button.setInk(true);
        button.setPrimary(primary);
        return addRenderableWidget(button);
    }

    private void select(Tab next) {
        tab = next;
        detail = Detail.NONE;
        if (next == Tab.ALCHEMY) session.lesson = Lesson.ALCHEMY;
        if (next == Tab.FORGE) session.lesson = Lesson.FORGE;
        refresh();
    }

    private void show(Detail page) {
        detail = page;
        refresh();
    }

    private void lesson(Lesson lesson, Tab next) {
        session.lesson = lesson;
        select(next);
    }

    private void refresh() {
        scroll = 0;
        scrollMax = 0;
        workshopTarget = null;
        rebuildWidgets();
    }

    private void showWorld(boolean markShop) {
        session.hud = true;
        session.hudSize = 0;
        session.route = markShop;
        minecraft.setScreen(null);
    }

    @Override
    public void tick() {
        if (!OpeningGuidePreview.isCurrent(session)) {
            minecraft.setScreen(null);
            return;
        }
        if (session.settle()) refresh();
    }

    @Override
    public void onClose() {
        session.hud = false;
        session.route = false;
        minecraft.setScreen(parent);
    }

    @Override public boolean isPauseScreen() { return false; }

    private static Action action(String label, Runnable run) { return new Action(label, run); }

    private static Card card(String heading, String text, List<ItemStack> items, Action... actions) {
        return new Card(heading, text, items, List.of(actions));
    }

    private Card selectCard() {
        if (detail != Detail.NONE) return detailCard();
        return switch (tab) {
            case JOURNAL -> journal();
            case NPC -> conversation();
            case ALCHEMY -> alchemy();
            case FORGE -> workshop();
        };
    }

    private Card journal() {
        var map = ClientTownMap.view();
        boolean hasDestination = map != null && map.data().places().stream()
                .anyMatch(place -> place.service() == session.lesson.service);
        String directions = hasDestination
                ? "追踪会使用当前城镇地图上的建筑入口。"
                : "位于省城附近时，指引可以标记药铺或铁作坊。";
        return card(session.lesson.title, session.objective() + "\n" + directions, List.of(),
                action("在世界里显示提示", () -> showWorld(false)),
                action("追踪" + session.lesson.service.label, () -> showWorld(true)),
                action("预览店铺交谈", () -> select(Tab.NPC)),
                action("重新演练本课", this::restart));
    }

    private void restart() {
        switch (session.lesson) {
            case BREATH -> {
                session.breath = 0;
                session.plants = 0;
            }
            case ALCHEMY -> {
                session.pill = session.aptitudeReady ? Pill.FORMULA : Pill.FATE;
                session.brewEnd = 0;
            }
            case FORGE -> {
                session.forge = Forge.BLANK;
                session.forgeEnd = 0;
                session.hammerHits = 0;
                session.automatic = false;
            }
        }
        refresh();
    }

    private Card conversation() {
        if (session.lesson == Lesson.FORGE) {
            return card("石承", "想学打镐，可以先做一把精铁镐。带铁锭到普通炼器鼎起坯，旁边就是加热窑、锻器台和水槽。", List.of(),
                    action("去看公用炼器鼎", () -> select(Tab.FORGE)),
                    action("问药铺伙计", () -> lesson(Lesson.BREATH, Tab.NPC)));
        }
        Action learnAlchemy = action("请教炼丹", () -> select(Tab.ALCHEMY));
        return switch (session.breath) {
            case 0 -> card("许青禾", "我想试试城外的土能不能养好聚灵叶。你愿意种三株，我就给你灵土和种苗。种好后，回来跟我学吐纳。",
                    List.of(stack("yuanxian:spirit_soil", 3), stack("yuanxian:spirit_leaf", 3)),
                    action("领种苗，接受试种", () -> {
                        session.breath = 1;
                        session.lesson = Lesson.BREATH;
                        refresh();
                    }), learnAlchemy, action("问铁匠", () -> lesson(Lesson.FORGE, Tab.NPC)));
            case 1 -> {
                if (session.plants < 3) yield card("许青禾", "先把灵土铺在城外空地，再把聚灵叶种下去。已经种好了 "
                                + session.plants + "/3 格。", List.of(stack("yuanxian:spirit_leaf", 1)),
                        action("示范在下一格播种", () -> {
                            session.plants++;
                            refresh();
                        }), learnAlchemy);
                yield card("许青禾", "三株都种好了。回来得正好，我替你测灵，再教你基础吐纳。", List.of(),
                        action("请教测灵与吐纳", () -> { session.breath = 2; refresh(); }), learnAlchemy);
            }
            case 2 -> card("许青禾", "找块平地打坐一次。等第一次修炼结算，再收功起来。", List.of(),
                    action("示范打坐到结算", () -> { session.breath = 3; refresh(); }), learnAlchemy);
            default -> card("许青禾", "留一株聚灵叶接着种，剩下的可以和凝露花一起炼回灵丹。你要试一炉，我带你看看丹炉。", List.of(),
                    learnAlchemy, action("问铁匠", () -> lesson(Lesson.FORGE, Tab.NPC)));
        };
    }

    private AlchemyRules.Recipe pillRecipe() {
        return AlchemyRules.get().recipe("huiling").orElseThrow();
    }

    private List<ItemStack> pillCosts() {
        var recipe = pillRecipe();
        var costs = new ArrayList<ItemStack>();
        for (int i = 0; i < recipe.ingredients().size(); i++) costs.add(stack(recipe.ingredients().get(i), recipe.count(i)));
        costs.add(new ItemStack(ModItems.LOW_GRADE_SPIRIT_STONE.get(), recipe.fuelCount()));
        return costs;
    }

    private Card alchemy() {
        return switch (session.pill) {
            case FATE -> card("准备开始炼丹", "先到测灵碑确认天命，再准备回灵丹方。预览从确认后的练习开始。", List.of(),
                    action("从确认天命后继续", () -> { session.confirmAptitude(); refresh(); }),
                    action("直接看缺料提示", () -> { session.confirmAptitude(); setPill(Pill.MISSING); }));
            case FORMULA -> card("丹炉还没有已学丹方", "先在工作台制作回灵丹方，再手持丹方右键研读一次。", List.of(),
                    action("查看丹方来源", () -> show(Detail.FORMULA)));
            case READ -> card("手持丹方研读", "练习背包里有一张回灵丹方。拿在手中，对空右键研读。", List.of(stack(pillRecipe().formula(), 1)),
                    action("示范右键研读", () -> setPill(Pill.SELECT)));
            case SELECT -> card("在丹炉选择回灵丹", "选择丹方时，丹炉会从背包补入已有的药材和灵石。", List.of(),
                    action("选择回灵丹", () -> setPill(Pill.MISSING)));
            case MISSING -> card("还缺凝露花 ×" + pillRecipe().count(1), "另外一味药和灵石已经放进炉里。买好凝露花后，再选一次回灵丹补料。", List.of(),
                    action("查看凝露花来源", () -> show(Detail.HERB_SOURCE)));
            case REFILL -> card("药材已买好", "再选一次回灵丹，把凝露花补进炉内。", List.of(stack(pillRecipe().ingredients().get(1), pillRecipe().count(1))),
                    action("再选回灵丹补料", () -> setPill(Pill.READY)));
            case READY -> card("材料已备齐", "一炉会尝试炼制 " + pillRecipe().outputCount() + " 颗，每颗分别判定成败与品质。", List.of(),
                    action("开始炼制", () -> {
                        session.brewEnd = Util.getMillis() + FURNACE_MILLIS;
                        setPill(Pill.BREWING);
                    }));
            case BREWING -> card("炉次进行中", "完成后，从右边的产物槽取丹。", List.of());
            case TAKE -> card("取走回灵丹", "这炉演示炼成一颗下品回灵丹。", List.of(), action("取走回灵丹", () -> setPill(Pill.COMPLETE)));
            case COMPLETE -> card("第一炉丹已经完成", "回灵丹可以补充灵力。下次做同一种丹药，选已学丹方直接补料。", List.of(stack(pillRecipe().output(), 1)),
                    action("回入门指引", () -> select(Tab.JOURNAL)));
        };
    }

    private void setPill(Pill next) {
        session.pill = next;
        detail = Detail.NONE;
        refresh();
    }

    private Card detailCard() {
        return switch (detail) {
            case FORMULA -> card("回灵丹方怎么获得", "在工作台用一张纸、两味药材和一枚下品灵石合成丹方。每次开炉要另备药材与灵石。", formulaCosts(),
                    action("示范合成丹方", () -> setPill(Pill.READ)),
                    action("返回丹炉", () -> show(Detail.NONE)));
            case HERB_SOURCE -> card("凝露花从哪里来", "药铺伙计当值且店内有货时可以购买。买到第一株后，可以种在灵土上，成熟后留一株继续种。",
                    List.of(stack(pillRecipe().ingredients().get(1), 1)),
                    action("找药铺伙计购买", () -> show(Detail.HERB_TRADE)),
                    action("在地图上追踪药铺", () -> showWorld(true)),
                    action("返回丹炉", () -> show(Detail.NONE)));
            case HERB_TRADE -> card("许青禾", "凝露花每株两枚下品灵石。炼回灵丹时，把它和聚灵叶一起放进炉里。",
                    List.of(new ItemStack(ModItems.LOW_GRADE_SPIRIT_STONE.get(), 2)),
                    action("用两枚下品灵石购买", () -> setPill(Pill.REFILL)),
                    action("回看药材来源", () -> show(Detail.HERB_SOURCE)));
            case FORGE_ROUTE -> card("精铁镐的两种做法", "普通炼器鼎可以一次备料，自动做出标准成品。手工路线从起坯开始，在窑、锻器台和淬火水槽之间加工，最后到组装台装柄。", List.of(),
                    action("返回工位", () -> show(Detail.NONE)));
            case NONE -> throw new IllegalStateException("detail page");
        };
    }

    private List<ItemStack> formulaCosts() {
        var recipe = pillRecipe();
        var costs = new ArrayList<ItemStack>();
        costs.add(new ItemStack(Items.PAPER));
        for (String id : recipe.ingredients()) costs.add(stack(id, 1));
        costs.add(new ItemStack(ModItems.LOW_GRADE_SPIRIT_STONE.get()));
        return costs;
    }

    private static List<ItemStack> stacks(Map<String, Integer> items) {
        return items.entrySet().stream().map(entry -> stack(entry.getKey(), entry.getValue())).toList();
    }

    private static ItemStack stack(String id, int count) {
        // The sample chooses coal from the existing fuel tag. The caption includes the allowed alternative.
        if (id.equals("#yuanxian:forging_fuels")) return new ItemStack(Items.COAL, count);
        if (id.equals(AlchemyRules.FUEL)) return new ItemStack(ModItems.LOW_GRADE_SPIRIT_STONE.get(), count);
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)), count);
    }

    private Card workshop() {
        Action routes = action("查看两种做法", () -> show(Detail.FORGE_ROUTE));
        Action next = action(forgeActionLabel(), this::advanceForge);
        if (session.forge == Forge.BLANK) {
            if (session.automatic) {
                List<ItemStack> costs = stacks(PreparationBlockEntity.automaticCosts(new Workpiece(PICKAXE, 0, 0, 0), true));
                return card("普通鼎自动制作",
                        "普通鼎会预付整把镐的材料。燃料可用煤炭或木炭，用水后返还空桶。",
                        costs, next, action("切换手工锻造", () -> setAutomatic(false)), routes);
            }
            var recipe = ForgingRules.get().recipe(PICKAXE).orElseThrow();
            return card("先在普通鼎起坯", "精铁镐入门即可制作。在炼器鼎选择精铁镐，点「只起坯」。",
                    stacks(WorkshopRules.get().blankCosts(recipe)), next, action("切换自动制作", () -> setAutomatic(true)),
                    action("直接看锻台锤打", () -> {
                        session.forge = Forge.HAMMER;
                        refresh();
                    }), routes);
        }
        return switch (session.forge) {
            case HEAT -> card("给粗坯加热", "把粗坯放进窑，再添煤炭或木炭。热好后，移到旁边的锻器台。",
                    stacks(WorkshopRules.get().fuel_cost()), next);
            case HAMMER -> card("在锻器台敲打热坯", "对准台上的热坯右键。预览已敲 " + session.hammerHits + "/" + DEMO_HITS + " 锤。",
                    List.of(stack("yuanxian:forging_hammer", 1)), next);
            case QUENCH -> card("给成形坯淬火", "给同层相邻的水槽注水，接着在锻器台潜行右键淬火。", List.of(new ItemStack(Items.WATER_BUCKET)), next);
            case TEMPER -> card("回到窑前回火", "淬火坯放回窑里，添煤炭或木炭，然后取回器坯。", stacks(WorkshopRules.get().fuel_cost()), next);
            case POLISH -> card("在锻器台精磨", "放上回火坯，用燧石精磨出刃口。", stacks(WorkshopRules.get().grind_cost()), next);
            case HANDLE -> card("做好普通木柄", "用两根木棍在工作台制作普通木柄，做好后与精磨坯一起送去组装台。",
                    List.of(new ItemStack(Items.STICK, 2)), next);
            case ASSEMBLE -> card("在组装台给镐装柄", "放入精磨坯和木柄，查看预览后再装配。", List.of(stack("yuanxian:wood_handle", 1)), next);
            case FURNACE -> card("普通鼎正在制作", "关闭工位界面后，炉次仍会继续。", List.of());
            case TAKE -> card("取走精铁镐", session.automatic ? "这次演示做成一把精铁镐，空桶已返还。" : "这次演示做成一把精铁镐，从组装台取走成品。", List.of(), next);
            case COMPLETE -> card("第一把镐已经做好", "用精铁镐挖一簇相连的矿石，可以试试连锁采矿。", List.of(),
                    action("回入门指引", () -> select(Tab.JOURNAL)),
                    action("重练另一种做法", () -> {
                        session.forge = Forge.BLANK;
                        session.hammerHits = 0;
                        setAutomatic(!session.automatic);
                    }));
            case BLANK -> throw new IllegalStateException("start page");
        };
    }

    private String forgeActionLabel() {
        if (!session.aptitudeReady && (session.forge == Forge.ASSEMBLE || session.automatic && session.forge == Forge.BLANK))
            return "示范确认天命后继续";
        return switch (session.forge) {
            case BLANK -> session.automatic ? "开始自动制作" : "只起坯";
            case HEAT -> "放入粗坯和燃料";
            case HAMMER -> "持锤右键敲打";
            case QUENCH -> "注水并淬火";
            case TEMPER -> "把淬火坯放回窑";
            case POLISH -> "用燧石精磨";
            case HANDLE -> "在工作台制柄";
            case ASSEMBLE -> "装配精铁镐";
            case TAKE -> "取走精铁镐";
            default -> "";
        };
    }

    private void setAutomatic(boolean automatic) {
        session.automatic = automatic;
        refresh();
    }

    private void advanceForge() {
        if (!session.aptitudeReady && (session.forge == Forge.ASSEMBLE || session.automatic && session.forge == Forge.BLANK)) {
            session.confirmAptitude();
            refresh();
            return;
        }
        session.forge = switch (session.forge) {
            case BLANK -> {
                if (!session.automatic) yield Forge.HEAT;
                session.forgeEnd = Util.getMillis() + FURNACE_MILLIS;
                yield Forge.FURNACE;
            }
            case HEAT -> Forge.HAMMER;
            case HAMMER -> ++session.hammerHits >= DEMO_HITS ? Forge.QUENCH : Forge.HAMMER;
            case QUENCH -> Forge.TEMPER;
            case TEMPER -> Forge.POLISH;
            case POLISH -> Forge.HANDLE;
            case HANDLE -> Forge.ASSEMBLE;
            case ASSEMBLE -> Forge.TAKE;
            case TAKE -> Forge.COMPLETE;
            default -> session.forge;
        };
        refresh();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x650C1712);
        CultivationInkTheme.paper(graphics, window.x(), window.y(), window.width(), window.height());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int x = window.x() + 10;
        int titleWidth = Math.max(1, window.width() - 88);
        CultivationInkTheme.title(graphics, font, "入门教学预览", x, window.y() + 11, titleWidth);
        graphics.fill(x, window.y() + 50, window.right() - 10, window.y() + 51, RULE);

        int y = body.y() - scroll;
        workshopTarget = null;
        graphics.enableScissor(body.x(), body.y(), body.right(), body.bottom());
        if (detail == Detail.HERB_TRADE || tab == Tab.NPC) {
            boolean smith = detail != Detail.HERB_TRADE && session.lesson == Lesson.FORGE;
            PortraitTextures.draw(graphics, smith ? SMITH : HERBALIST, body.x() + 1, y, 39);
            CultivationInkTheme.title(graphics, font, card.title(), body.x() + 47, y + 4, Math.max(1, body.width() - 48));
            CultivationInkTheme.text(graphics, font, smith ? "铁作坊铁匠 · 对话演练" : "药铺伙计 · 对话演练",
                    body.x() + 47, y + 24, Math.max(1, body.width() - 48), MUTED);
            y += 44;
        } else {
            CultivationInkTheme.title(graphics, font, card.title(), body.x(), y, body.width());
            y += 20;
        }
        y = paragraphs(graphics, card.text(), y, INK);
        if (tab == Tab.JOURNAL) y = drawJournal(graphics, y + 3);
        if (tab == Tab.ALCHEMY && detail == Detail.NONE) y = drawAlchemy(graphics, y + 4);
        if (tab == Tab.FORGE && detail == Detail.NONE) y = drawWorkshop(graphics, y + 4);
        if (!card.materials().isEmpty()) y = drawMaterials(graphics, y + 4);
        if (tab == Tab.ALCHEMY || tab == Tab.FORGE) {
            y = paragraphs(graphics, "本页使用演练用料。", y + 4, MUTED);
        }
        graphics.disableScissor();

        int written = y - (body.y() - scroll);
        scrollMax = Math.max(0, written - body.height());
        if (scroll > scrollMax) scroll = scrollMax;
        if (scrollMax > 0) {
            int thumb = Math.max(5, body.height() * body.height() / written);
            int position = body.y() + scroll * (body.height() - thumb) / scrollMax;
            graphics.fill(body.right() + 2, body.y(), body.right() + 3, body.bottom(), RULE);
            graphics.fill(body.right() + 1, position, body.right() + 4, position + thumb, JADE);
        }
    }

    private int paragraphs(GuiGraphics graphics, String text, int y, int color) {
        for (var line : font.split(book(text), Math.max(1, body.width() - 2))) {
            graphics.drawString(font, line, body.x(), y, color, false);
            y += TEXT_ROW;
        }
        return y;
    }

    private int drawJournal(GuiGraphics graphics, int y) {
        return switch (session.lesson) {
            case BREATH -> {
                y = checklist(graphics, y, session.breath > 0, "与药铺伙计谈试种");
                y = checklist(graphics, y, session.plants == 3, "在城外三格灵土上种下聚灵叶");
                yield checklist(graphics, y, session.breath == 3, "回药铺学吐纳，再完成一次打坐");
            }
            case ALCHEMY -> {
                y = checklist(graphics, y, session.aptitudeReady, "确认天命");
                y = checklist(graphics, y, session.pill.ordinal() >= Pill.SELECT.ordinal(), "制作并研读回灵丹方");
                yield checklist(graphics, y, session.pill == Pill.COMPLETE, "备料开炉，取走回灵丹");
            }
            case FORGE -> {
                y = checklist(graphics, y, session.forge != Forge.BLANK, "选择做法，在普通鼎备料");
                y = checklist(graphics, y, session.forge.ordinal() >= Forge.TAKE.ordinal(), "完成制作");
                yield checklist(graphics, y, session.forge == Forge.COMPLETE, "取走精铁镐");
            }
        };
    }

    private int checklist(GuiGraphics graphics, int y, boolean done, String text) {
        int x = body.x() + 1;
        graphics.renderOutline(x, y + 2, 7, 7, done ? JADE : RULE);
        if (done) graphics.fill(x + 2, y + 4, x + 5, y + 7, JADE);
        CultivationInkTheme.text(graphics, font, text, x + 12, y, Math.max(1, body.width() - 15), done ? JADE : INK);
        return y + TEXT_ROW;
    }

    private int drawAlchemy(GuiGraphics graphics, int y) {
        int tile = Math.max(12, Math.min(23, (body.width() - 10) / 8));
        int slotsWidth = 8 * tile + 8;
        boolean sideBySide = body.width() >= slotsWidth + 68;
        int slotAreaX = sideBySide ? body.x() + 66 : body.x();
        int slotsY = sideBySide ? y + 6 : y + 50;
        drawItem(graphics, new ItemStack(ModBlocks.ALCHEMY_FURNACE.get()), body.x() + 6, y, 43);
        int step = session.pill.ordinal();
        boolean hasRecipe = step >= Pill.SELECT.ordinal();
        boolean loaded = step >= Pill.MISSING.ordinal() && step <= Pill.BREWING.ordinal();
        boolean ready = step >= Pill.READY.ordinal();
        var recipe = pillRecipe();
        var costs = pillCosts();
        CultivationInkTheme.text(graphics, font, hasRecipe ? "已学丹方  回灵丹" : "已学丹方  空", slotAreaX, slotsY,
                Math.max(1, body.right() - slotAreaX), MUTED);
        int slotsTop = slotsY + 26;
        CultivationInkTheme.text(graphics, font, "药材", slotAreaX, slotsY + 14, 3 * tile, INK);
        for (int i = 0; i < 3; i++) {
            boolean has = loaded && i < recipe.ingredients().size() && (i != 1 || ready);
            drawSlot(graphics, slotAreaX + tile * i, slotsTop, tile,
                    has ? costs.get(i) : ItemStack.EMPTY, i == 1 && session.pill == Pill.MISSING);
        }
        int fuel = slotAreaX + 3 * tile + 4;
        CultivationInkTheme.text(graphics, font, "灵石", fuel, slotsY + 14, tile + 4, INK);
        drawSlot(graphics, fuel, slotsTop, tile, loaded ? costs.getLast() : ItemStack.EMPTY, false);
        int output = fuel + tile + 4;
        CultivationInkTheme.text(graphics, font, "出丹", output, slotsY + 14, Math.max(1, body.right() - output), INK);
        for (int i = 0; i < 4; i++) {
            boolean has = i == 0 && session.pill == Pill.TAKE;
            drawSlot(graphics, output + i * tile, slotsTop, tile,
                    has ? stack(recipe.output(), 1) : ItemStack.EMPTY, has);
        }
        if (session.pill == Pill.BREWING) drawWait(graphics, slotsTop + tile + 5, session.brewEnd);
        return slotsTop + tile + 10;
    }

    private record Station(String label, ItemStack model) {}
    private List<Station> workshopStations() {
        var ding = new Station("普通炼器鼎", new ItemStack(ModBlocks.FORGING_TABLE.get()));
        if (session.automatic) return List.of(ding);
        return List.of(ding, new Station("加热窑", new ItemStack(ModBlocks.HEATING_KILN.get())),
                new Station("锻器台", new ItemStack(ModBlocks.FORGING_ANVIL.get())),
                new Station("淬火水槽", new ItemStack(ModBlocks.QUENCHING_TROUGH.get())),
                new Station("组装台", new ItemStack(ModBlocks.ASSEMBLY_TABLE.get())));
    }

    private int stationIndex() {
        if (session.automatic) return 0;
        return switch (session.forge) {
            case HEAT, TEMPER -> 1;
            case HAMMER, QUENCH, POLISH -> 2;
            case HANDLE, ASSEMBLE, TAKE, COMPLETE -> 4;
            default -> 0;
        };
    }

    private int drawWorkshop(GuiGraphics graphics, int y) {
        var stations = workshopStations();
        int cell = Math.min(82, body.width() / stations.size());
        int size = Math.max(1, Math.min(43, cell - 5));
        int left = body.x() + (body.width() - cell * stations.size()) / 2;
        int selected = stationIndex();
        for (int i = 0; i < stations.size(); i++) {
            var station = stations.get(i);
            int origin = left + cell * i;
            int top = y + 2;
            if (i == selected || session.forge == Forge.QUENCH && i == 3) {
                graphics.fill(origin + 1, top, origin + cell - 1, top + size + 5, 0x58D9BE77);
                graphics.renderOutline(origin + 1, top, cell - 2, size + 5, GOLD);
            }
            drawItem(graphics, station.model(), origin + (cell - size) / 2, top + 3, size);
            CultivationInkTheme.centered(graphics, font, station.label(), origin, top + size + 7, cell, false, INK);
            if (i == selected && (session.forge == Forge.HAMMER || session.forge == Forge.QUENCH)) {
                workshopTarget = new StoryLayout.Rect(origin + 1, top, Math.max(1, cell - 2), size + 5);
            }
        }
        y += size + 22;
        var work = workpiece();
        if (!work.isEmpty()) {
            drawItem(graphics, work, body.x() + 2, y + 1, 19);
            CultivationInkTheme.text(graphics, font, work.getHoverName().getString(), body.x() + 25, y + 5,
                    Math.max(1, body.width() - 27), INK);
            y += 23;
        }
        if (session.forge == Forge.FURNACE) {
            drawWait(graphics, y + 1, session.forgeEnd);
            y += 8;
        }
        return y;
    }

    private ItemStack workpiece() {
        if (session.forge == Forge.TAKE || session.forge == Forge.COMPLETE) {
            return stack(ForgingRules.get().recipe(PICKAXE).orElseThrow().output(), 1);
        }
        int stage = switch (session.forge) {
            case HEAT, HAMMER -> 0;
            case QUENCH -> 1;
            case TEMPER -> 2;
            case POLISH -> 3;
            case HANDLE, ASSEMBLE -> 4;
            default -> -1;
        };
        if (stage == -1) return ItemStack.EMPTY;
        int points = stage == 0 ? 0 : WorkshopRules.get().formingPoints(PICKAXE);
        return WorkshopItems.blank(new Workpiece(PICKAXE, stage, points, 0));
    }

    private int drawMaterials(GuiGraphics graphics, int y) {
        CultivationInkTheme.text(graphics, font, "这一步用到", body.x(), y, body.width(), MUTED);
        y += 15;
        int columns = body.width() >= 275 ? 2 : 1;
        int cell = body.width() / columns;
        for (int i = 0; i < card.materials().size(); i++) {
            var item = card.materials().get(i);
            int x = body.x() + i % columns * cell;
            int row = y + i / columns * 22;
            drawSlot(graphics, x, row, 20, item, false);
            CultivationInkTheme.text(graphics, font, item.getHoverName().getString() + " ×" + item.getCount(),
                    x + 26, row + 5, Math.max(1, cell - 28), INK);
        }
        return y + (card.materials().size() + columns - 1) / columns * 22 + 2;
    }

    private void drawWait(GuiGraphics graphics, int y, long end) {
        double progress = 1 - (end - Util.getMillis()) / (double) FURNACE_MILLIS;
        CultivationInkTheme.bar(graphics, body.x(), y, body.width(), progress, GOLD);
    }

    private void drawSlot(GuiGraphics graphics, int x, int y, int size, ItemStack item, boolean highlight) {
        graphics.fill(x, y, x + size - 1, y + size - 1, 0xFED5CBB2);
        graphics.renderOutline(x, y, size - 1, size - 1, highlight ? GOLD : RULE);
        if (highlight) graphics.renderOutline(x + 1, y + 1, size - 3, size - 3, GOLD);
        if (!item.isEmpty()) {
            int iconSize = Math.min(16, size - 3);
            drawItem(graphics, item, x + (size - iconSize) / 2, y + (size - iconSize) / 2, iconSize);
        }
    }

    private void drawItem(GuiGraphics graphics, ItemStack item, int x, int y, int size) {
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(size / 16.0f, size / 16.0f, 1);
        graphics.renderItem(item, 0, 0);
        pose.popPose();
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && workshopTarget != null
                && body.contains(x, y) && workshopTarget.contains(x, y)) {
            advanceForge();
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (body.contains(x, y)) {
            scroll = Math.clamp(scroll - (int) Math.round(vertical * 24), 0, scrollMax);
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_PAGE_DOWN) {
            int amount = Math.max(TEXT_ROW, body.height() - TEXT_ROW);
            scroll = Math.clamp(scroll + (key == GLFW.GLFW_KEY_PAGE_DOWN ? amount : -amount), 0, scrollMax);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
}
