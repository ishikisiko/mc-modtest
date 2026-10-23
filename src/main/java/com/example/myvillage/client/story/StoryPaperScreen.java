package com.example.myvillage.client.story;

import com.example.myvillage.client.cultivation.panel.CultivationInkTheme;
import com.example.myvillage.client.cultivation.panel.PanelFonts;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

/** A bounded paper viewport with wheel and keyboard scrolling; the option footer never scrolls away. */
abstract class StoryPaperScreen extends Screen {
    protected StoryLayout.Paper layout;
    private List<FormattedCharSequence> rows = List.of();
    private int scroll;
    private int contentHeight;
    protected StoryPaperScreen(Component title) { super(title); }

    protected final void paper(int options, List<String> paragraphs, boolean preserveScroll) {
        font = PanelFonts.get();
        layout = StoryLayout.paper(width, height, options);
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (String paragraph : paragraphs) {
            wrapped.addAll(font.split(CultivationInkTheme.book(paragraph), layout.body().width() - 5));
            wrapped.add(FormattedCharSequence.EMPTY);
        }
        rows = List.copyOf(wrapped);
        contentHeight = rows.size() * 12;
        scroll = StoryLayout.scroll(preserveScroll ? scroll : 0, contentHeight, layout.body().height());
    }

    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (layout == null) return;
        g.fill(0, 0, width, height, 0x650C1712);
        var p = layout.panel();
        CultivationInkTheme.paper(g, p.x(), p.y(), p.width(), p.height());
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        var body = layout.body();
        g.enableScissor(body.x(), body.y(), body.right(), body.bottom());
        int first = scroll / 12, last = Math.min(rows.size(), (scroll + body.height() + 11) / 12);
        for (int i = first; i < last; i++)
            g.drawString(font, rows.get(i), body.x(), body.y() + i * 12 - scroll, CultivationInkTheme.INK, false);
        g.disableScissor();
        int maximum = Math.max(0, contentHeight - body.height());
        if (maximum > 0) {
            int thumb = Math.max(10, body.height() * body.height() / contentHeight);
            int thumbY = body.y() + scroll * (body.height() - thumb) / maximum;
            g.fill(body.right() - 1, body.y(), body.right(), body.bottom(), CultivationInkTheme.RULE);
            g.fill(body.right() - 2, thumbY, body.right() + 1, thumbY + thumb, CultivationInkTheme.JADE);
        }
        var p = layout.panel();
        CultivationInkTheme.text(g, font, maximum > 0 ? "滚轮 / PgUp PgDn 翻阅 · Esc 收起" : "Esc 收起",
                p.x() + 12, p.bottom() - 13, p.width() - 24, CultivationInkTheme.MUTED);
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (layout.body().contains(x, y)) {
            scroll = StoryLayout.scroll(scroll - (int)Math.round(vertical * 24), contentHeight, layout.body().height());
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        int requested = switch (key) {
            case GLFW.GLFW_KEY_PAGE_UP -> scroll - Math.max(12, layout.body().height() - 12);
            case GLFW.GLFW_KEY_PAGE_DOWN -> scroll + Math.max(12, layout.body().height() - 12);
            case GLFW.GLFW_KEY_HOME -> 0;
            case GLFW.GLFW_KEY_END -> contentHeight;
            default -> -1;
        };
        if (requested >= 0 || key == GLFW.GLFW_KEY_PAGE_UP) {
            scroll = StoryLayout.scroll(requested, contentHeight, layout.body().height());
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public boolean isPauseScreen() { return false; }
}
