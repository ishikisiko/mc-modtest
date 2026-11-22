package com.example.myvillage.client.story;

import com.example.myvillage.client.cultivation.CultivationProfileScreen;
import com.example.myvillage.client.cultivation.panel.CultivationInkTheme;
import com.example.myvillage.client.cultivation.panel.PanelFonts;
import com.example.myvillage.client.townmap.ClientTownMap;
import com.example.myvillage.client.townmap.TownMapData;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/** The opt-in preview has no connection to inventory, tutorial receipts or NPC interactions. */
public final class OpeningGuidePreview {
    enum Lesson {
        BREATH("基础吐纳", "meditation", TownMapData.Service.APOTHECARY),
        ALCHEMY("第一炉回灵丹", "alchemy", TownMapData.Service.APOTHECARY),
        FORGE("第一把精铁镐", "forging", TownMapData.Service.SMITH);

        final String title;
        final String icon;
        final TownMapData.Service service;

        Lesson(String title, String icon, TownMapData.Service service) {
            this.title = title;
            this.icon = icon;
            this.service = service;
        }
    }

    enum Pill {
        FATE, FORMULA, READ, SELECT, MISSING, REFILL, READY, BREWING, TAKE, COMPLETE
    }

    enum Forge {
        BLANK, HEAT, HAMMER, QUENCH, TEMPER, POLISH, HANDLE, ASSEMBLE, FURNACE, TAKE, COMPLETE
    }

    static final int FURNACE_MILLIS = 1800;
    private static Session active;
    private static ClientTownMap.Target routeTarget;

    private OpeningGuidePreview() { }

    static final class Session {
        private final ClientLevel level;
        private final LocalPlayer player;
        Lesson lesson = Lesson.BREATH;
        Pill pill = Pill.FATE;
        Forge forge = Forge.BLANK;
        int breath;
        int plants;
        int hammerHits;
        boolean automatic;
        boolean aptitudeReady;
        boolean hud;
        boolean route;
        int hudSize;
        long brewEnd;
        long forgeEnd;

        private Session(ClientLevel level, LocalPlayer player) {
            this.level = level;
            this.player = player;
        }

        void confirmAptitude() {
            aptitudeReady = true;
            if (pill == Pill.FATE) pill = Pill.FORMULA;
        }

        boolean settle() {
            long now = Util.getMillis();
            boolean changed = false;
            if (pill == Pill.BREWING && now >= brewEnd) {
                pill = Pill.TAKE;
                changed = true;
            }
            if (forge == Forge.FURNACE && now >= forgeEnd) {
                forge = Forge.TAKE;
                changed = true;
            }
            return changed;
        }

        String objective() {
            return switch (lesson) {
                case BREATH -> switch (breath) {
                    case 0 -> "去药铺找许青禾，问问试种的事。";
                    case 1 -> plants < 3 ? "在城外种下聚灵叶（" + plants + "/3）。" : "回药铺请许青禾教你测灵与吐纳。";
                    case 2 -> "打坐一次，等到首次修炼结算。";
                    default -> "已学会基础吐纳，可以去问炼丹或炼器。";
                };
                case ALCHEMY -> switch (pill) {
                    case FATE -> "到阡镇药庐的测灵碑确认天命。";
                    case FORMULA, READ -> "制作回灵丹方，并手持丹方研读。";
                    case SELECT -> "打开青铜丹炉，选择回灵丹。";
                    case MISSING -> "去药铺买一株凝露花。";
                    case REFILL -> "再选一次回灵丹，把药材补进炉内。";
                    case READY -> "材料齐备，开始炼制。";
                    case BREWING -> "回灵丹正在炼制。";
                    case TAKE -> "从产物槽取走回灵丹。";
                    case COMPLETE -> "已取走第一炉丹药。";
                };
                case FORGE -> switch (forge) {
                    case BLANK -> automatic ? "在普通炼器鼎备料，开始自动制作。" : "到铁作坊，用普通炼器鼎起坯。";
                    case HEAT -> "把粗坯放进加热窑，再放入燃料。";
                    case HAMMER -> "手持锻锤，右键锻器台上的热坯。";
                    case QUENCH -> "给水槽注水，再潜行右键锻器台淬火。";
                    case TEMPER -> "把淬火坯放回窑里回火。";
                    case POLISH -> "把回火坯移回锻器台，准备燧石精磨。";
                    case HANDLE -> "在工作台做好普通木柄。";
                    case ASSEMBLE -> "在组装台放入精磨坯和木柄。";
                    case FURNACE -> "普通炼器鼎正在制作精铁镐。";
                    case TAKE -> "取走做好的精铁镐。";
                    case COMPLETE -> "用新镐挖一簇相连的矿石，试试连锁采矿。";
                };
            };
        }
    }

    public static void open(Screen parent) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !mc.player.isAlive()) return;
        Session displayed = current();
        if (displayed == null) {
            displayed = new Session(mc.level, mc.player);
            active = displayed;
        }
        displayed.settle();
        mc.setScreen(new OpeningGuidePreviewScreen(parent, displayed));
    }

    static boolean reopenTracker() {
        Session current = current();
        if (current == null || !current.hud) return false;
        open(CultivationProfileScreen.journey());
        return true;
    }

    static boolean cycleTracker() {
        Session current = current();
        if (current == null || !current.hud) return false;
        current.hudSize = (current.hudSize + 1) % 3;
        return true;
    }

    static void clear() {
        active = null;
        routeTarget = null;
    }

    private static Session current() {
        Minecraft mc = Minecraft.getInstance();
        if (active != null && (active.level != mc.level || active.player != mc.player)) clear();
        return active;
    }

    static boolean isCurrent(Session session) { return current() == session; }

    static ClientTownMap.Target previewTarget() {
        Session current = current();
        if (current == null || !current.hud || !current.route || current.hudSize == 2) {
            routeTarget = null;
            return null;
        }
        var map = ClientTownMap.view();
        if (map == null) {
            routeTarget = null;
            return null;
        }
        if (routeTarget != null && routeTarget.location().equals(map.location())
                && routeTarget.place().service() == current.lesson.service) return routeTarget;
        routeTarget = map.data().places().stream()
                .filter(place -> place.service() == current.lesson.service).findFirst()
                .map(place -> new ClientTownMap.Target(map.location(), place, Vec3.atBottomCenterOf(map.worldEntry(place))))
                .orElse(null);
        return routeTarget;
    }

    /** Returns the bottom of the cue, or the start of the rectangle if the normal HUD can draw. */
    static int drawTracker(GuiGraphics graphics, StoryLayout.Rect box) {
        Session current = current();
        if (current == null || !current.hud || current.hudSize == 2 || box.height() < 38) return box.y();
        current.settle();
        var font = PanelFonts.get();
        var target = previewTarget();
        String distance = target == null ? "" : target.place().name() + "  "
                + (long) Math.ceil(current.player.position().distanceTo(target.position())) + " 格";
        String next = current.lesson == Lesson.BREATH && current.breath == 0 && !current.route
                ? "药铺伙计正在找人试种聚灵叶。种好后，她会教你基础吐纳。"
                : current.objective();
        int x = box.x();
        int y = box.y();
        int textWidth = Math.max(1, box.width() - 14);
        var lines = font.split(CultivationInkTheme.book(next), textWidth);
        int visible = Math.min(lines.size(), current.hudSize == 0 ? 3 : 1);
        int bottom = y + 37 + visible * 11 + (distance.isEmpty() ? 0 : 11);
        if (bottom > box.bottom()) return y;
        graphics.fill(x, y, box.right(), bottom, 0xDD202D25);
        graphics.fill(x, y, x + 2, bottom, 0xFFDFBE78);
        graphics.drawString(font, CultivationInkTheme.fit(font, "教学预览 · " + current.lesson.title,
                textWidth, true), x + 7, y + 7, 0xFFF4E4B4, true);
        for (int i = 0; i < visible; i++) {
            if (i == visible - 1 && visible < lines.size()) {
                StringBuilder text = new StringBuilder();
                lines.get(i).accept((index, style, codepoint) -> { text.appendCodePoint(codepoint); return true; });
                graphics.drawString(font, CultivationInkTheme.fit(font, text + "…", textWidth, false),
                        x + 7, y + 20 + i * 11, 0xFFF6EFD9, false);
            } else {
                graphics.drawString(font, lines.get(i), x + 7, y + 20 + i * 11, 0xFFF6EFD9, false);
            }
        }
        if (!distance.isEmpty()) graphics.drawString(font, CultivationInkTheme.fit(font, distance, textWidth, false),
                x + 7, bottom - 25, 0xFFFFDA91, false);
        String keys = StoryClient.JOURNAL.getTranslatedKeyMessage().getString() + " 打开预览   "
                + StoryClient.TRACKER.getTranslatedKeyMessage().getString() + " 收起";
        graphics.drawString(font, CultivationInkTheme.fit(font, keys, textWidth, false),
                x + 7, bottom - 13, 0xFFE1D5B3, false);
        return bottom;
    }
}
