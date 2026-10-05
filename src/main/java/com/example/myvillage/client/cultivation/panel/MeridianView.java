package com.example.myvillage.client.cultivation.panel;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.cultivation.meditation.MeditationStatus;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws the meditation page's centrepiece: the seated figure with its meridians, acupoints, and
 * the lower dantian, lit and animated for the session state. Geometry comes from
 * {@link MeridianChart}, the look from {@link MeridianLook}. Only three things on it are
 * quantities, and all three are synced server values: the dantian's fill (cultivation progress
 * over the stage cap), the ring around it (stability over its cap), and the halo behind the head
 * while advancing (elapsed share of the advancement). Everything that moves is decoration.
 */
final class MeridianView {
    static final ResourceLocation FIGURE = ResourceLocation.fromNamespaceAndPath(
            MyVillageMod.MOD_ID, "textures/gui/cultivation/meridian_figure.png");

    /** Head centre in the figure square, for the advancement halo. */
    private static final float HEAD_X = 0.505F;
    private static final float HEAD_Y = 0.150F;
    private static final float HALO_RADIUS = 0.128F;
    private static final float DANTIAN_RADIUS = 0.040F;
    /** The drawn figure's left (cushion), right (knee), and bottom (shadow) in its square. */
    private static final float FIGURE_LEFT = 0.24F;
    private static final float FIGURE_RIGHT = 0.95F;
    private static final float FIGURE_BOTTOM = 0.92F;
    private static final float TAIL = 0.055F;
    private static final int CAPTION_ROW = 10;
    private static final String SEPARATOR = " · ";

    private static final MeridianPath CIRCUIT;
    private static final Map<String, MeridianPath> CHANNEL_PATHS = new LinkedHashMap<>();
    private static final float BARRIER_AT;

    static {
        List<MeridianChart.Channel> loop = new ArrayList<>();
        for (String id : MeridianChart.smallCircuit()) {
            loop.add(MeridianChart.channel(id).orElseThrow());
        }
        CIRCUIT = MeridianPath.of(loop);
        for (MeridianChart.Channel channel : MeridianChart.channels()) {
            CHANNEL_PATHS.put(channel.id(), MeridianPath.of(channel));
        }
        BARRIER_AT = CIRCUIT.fractionOf(MeridianChart.YUZHEN).orElse(0.5F);
    }

    private MeridianView() {
    }

    static void render(GuiGraphics graphics, PanelContext context, MeridianLook look,
                       int x, int y, int width, int height) {
        Font font = context.font();
        double seconds = (Util.getMillis() % 3_600_000L) / 1000.0D;
        graphics.fillGradient(x, y, x + width, y + height, 0xFF121B1D, 0xFF0A0F11);
        graphics.renderOutline(x, y, width, height, PanelTheme.CARD_BORDER);

        List<String> caption = captionLines(context, look);
        // a second caption line joins the first when both fit, so the figure keeps its size
        boolean inline = caption.size() == 2
                && 14 + font.width(caption.get(0)) + font.width(SEPARATOR) + font.width(caption.get(1)) <= width - 8;
        int captionRows = inline ? 1 : caption.size();
        int captionHeight = captionRows * CAPTION_ROW + 4;
        // the figure keeps one size in every state; a second caption line lifts it slightly
        float size = Math.min(figureSize(height, 1), width - 4.0F);
        float[] extent = extent(font, size);
        float boxX = x + (width - (extent[1] - extent[0])) / 2.0F - extent[0];
        float boxY = y + (captionRows > 1 ? 2.0F : 6.0F);
        Box box = new Box(boxX, boxY, size);

        drawAura(graphics, box, look, seconds);
        drawHalo(graphics, context, box, look, seconds);
        int light = Math.round(255 * look.figureLight());
        VectorBrush.texture(graphics, FIGURE, box.x, box.y, box.x + size, box.y + size,
                0xFF000000 | light << 16 | light << 8 | light);
        double[] motes = motePositions(look, seconds);
        drawChannels(graphics, box, look);
        drawFlow(graphics, box, look, motes, seconds);
        drawPoints(graphics, box, look, motes, seconds);
        drawDantian(graphics, context, box, look, seconds);
        drawLabels(graphics, font, box, look, x, x + width);

        // title and caption; a long title gives way to the advancement halo rather than cross it
        String title = context.text("screen.myvillage.cultivation.meridian.title");
        float haloLeft = box.px(HEAD_X - HALO_RADIUS) - 3.0F;
        if (!look.halo() || x + 14 + font.width(title) <= haloLeft) {
            PanelTheme.diamond(graphics, x + 8, y + 7, 2, PanelTheme.GOLD);
            graphics.drawString(font, title, x + 14, y + 3, PanelTheme.GOLD_BRIGHT, false);
        }
        String flow = context.text("screen.myvillage.cultivation.meridian.flow");
        if (!look.halo() && font.width(title) + font.width(flow) + 30 <= width) {
            graphics.drawString(font, flow, x + width - 6 - font.width(flow), y + 3, PanelTheme.FAINT, false);
        }
        int lineY = y + height - captionHeight + 2;
        int stateColor = context.sessionColor();
        if (inline) {
            String state = caption.get(0);
            String rest = SEPARATOR + caption.get(1);
            int lineX = x + (width - font.width(state) - font.width(rest) + 7) / 2;
            PanelTheme.diamond(graphics, lineX - 5, lineY + 3, 2, stateColor);
            graphics.drawString(font, state, lineX + 2, lineY, stateColor, false);
            graphics.drawString(font, rest, lineX + 2 + font.width(state), lineY, PanelTheme.MUTED, false);
            return;
        }
        for (int index = 0; index < caption.size(); index++) {
            String line = PanelTheme.fit(font, caption.get(index), width - 20);
            int lineWidth = font.width(line);
            if (index == 0) {
                int lineX = x + (width - lineWidth + 7) / 2;
                PanelTheme.diamond(graphics, lineX - 5, lineY + 3, 2, stateColor);
                graphics.drawString(font, line, lineX + 2, lineY, stateColor, false);
            } else {
                graphics.drawString(font, line, x + (width - lineWidth) / 2, lineY, PanelTheme.MUTED, false);
            }
            lineY += CAPTION_ROW;
        }
    }

    private static float figureSize(int height, int captionRows) {
        return (height - captionRows * CAPTION_ROW - 4 - 8) / FIGURE_BOTTOM;
    }

    /**
     * Horizontal extent {left, right} of the figure and its labels at {@code size}, in GUI
     * pixels from the figure square's left edge.
     */
    private static float[] extent(Font font, float size) {
        float left = FIGURE_LEFT * size;
        float right = FIGURE_RIGHT * size;
        for (MeridianChart.Acupoint point : MeridianChart.points()) {
            if (point.label().isEmpty()) {
                continue;
            }
            MeridianChart.Label label = point.label().orElseThrow();
            int text = font.width(Component.translatable(point.translationKey()).getString());
            if (label.side() == MeridianChart.LabelSide.LEFT) {
                left = Math.min(left, label.x() * size - text - 1.0F);
            } else {
                right = Math.max(right, label.x() * size + text + 1.0F);
            }
        }
        return new float[] {left, right};
    }

    /** The stage width the figure and its labels need at {@code height}, before any margin. */
    static int preferredWidth(PanelContext context, MeridianLook look, int height) {
        Font font = context.font();
        float[] extent = extent(font, figureSize(height, 1));
        int title = 20 + font.width(context.text("screen.myvillage.cultivation.meridian.title"));
        return (int) Math.ceil(Math.max(extent[1] - extent[0], title));
    }

    private static List<String> captionLines(PanelContext context, MeridianLook look) {
        List<String> lines = new ArrayList<>();
        lines.add(context.sessionText());
        MeditationStatus status = context.meditation();
        if (status != null && status.state().advancing()) {
            // the server repeats advancement status at its feedback interval, so this stays live
            lines.add(context.text(
                    "screen.myvillage.cultivation.advancement_runtime_value",
                    status.advancementTicksRemaining(),
                    status.advancementDurationTicks()));
        } else if (look.phase() == MeridianLook.Phase.DORMANT) {
            lines.add(context.text(context.profile().awakened()
                    ? "message.myvillage.cultivation.session.basic_breathing_required"
                    : "message.myvillage.cultivation.session.not_awakened"));
        }
        return lines;
    }

    private record Box(float x, float y, float size) {
        float px(float u) {
            return x + u * size;
        }

        float py(float v) {
            return y + v * size;
        }
    }

    private static int brighten(int color, float towardWhite) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        r += Math.round((255 - r) * towardWhite);
        g += Math.round((255 - g) * towardWhite);
        b += Math.round((255 - b) * towardWhite);
        return (color & 0xFF000000) | r << 16 | g << 8 | b;
    }

    private static float pulse(double seconds, double period) {
        return (float) (0.5D + 0.5D * Math.sin(2.0D * Math.PI * seconds / period));
    }

    private static void drawAura(GuiGraphics graphics, Box box, MeridianLook look, double seconds) {
        if (look.auraLevel() <= 0.0F) {
            return;
        }
        float breath = look.phase() == MeridianLook.Phase.REST ? 1.0F : 0.85F + 0.15F * pulse(seconds, 4.0D);
        new VectorBrush(graphics).begin()
                .disc(box.px(0.54F), box.py(0.50F), box.size * 0.40F, 0.0F, look.color(),
                        look.auraLevel() * breath, 0.0F)
                .end(true);
    }

    private static void drawHalo(GuiGraphics graphics, PanelContext context, Box box,
                                 MeridianLook look, double seconds) {
        MeditationStatus status = context.meditation();
        if (!look.halo() || status == null || !status.state().advancing()) {
            return;
        }
        double done = 1.0D - PanelReadouts.fraction(
                status.advancementTicksRemaining(), status.advancementDurationTicks());
        float cx = box.px(HEAD_X);
        float cy = box.py(HEAD_Y);
        float radius = box.size * HALO_RADIUS;
        double sweep = 2.0D * Math.PI * done;
        new VectorBrush(graphics).begin()
                .arc(cx, cy, radius, 1.2F, 0.6F, 0.0D, 2.0D * Math.PI, 0xFF3A3A30, 0.9F)
                .end(false);
        VectorBrush glow = new VectorBrush(graphics).begin()
                .disc(cx, cy, radius * 1.15F, 0.0F, look.color(), 0.10F + 0.06F * pulse(seconds, 1.6D), 0.0F);
        if (sweep > 0.0D) {
            glow.arc(cx, cy, radius, 2.0F, 2.6F, 0.0D, sweep, look.color(), 0.55F)
                    .arc(cx, cy, radius, 1.3F, 0.5F, 0.0D, sweep, brighten(look.color(), 0.35F), 1.0F);
        }
        glow.end(true);
    }

    private static void drawChannels(GuiGraphics graphics, Box box, MeridianLook look) {
        VectorBrush core = new VectorBrush(graphics).begin();
        VectorBrush glow = new VectorBrush(graphics);
        boolean anyGlow = false;
        for (MeridianChart.Channel channel : MeridianChart.channels()) {
            float level = look.level(channel.vessel());
            MeridianPath path = CHANNEL_PATHS.get(channel.id());
            float[] xs = new float[path.size()];
            float[] ys = new float[path.size()];
            for (int sample = 0; sample < path.size(); sample++) {
                xs[sample] = box.px(path.x(sample));
                ys[sample] = box.py(path.y(sample));
            }
            core.stroke(xs, ys, 0, xs.length - 1, 0.8F, 0.7F, look.color(), 0.25F + 0.65F * level, null);
            if (level > 0.3F) {
                if (!anyGlow) {
                    glow.begin();
                    anyGlow = true;
                }
                glow.stroke(xs, ys, 0, xs.length - 1, 1.2F, 2.8F, look.color(), 0.32F * level, null);
            }
        }
        core.end(false);
        if (anyGlow) {
            glow.end(true);
        }
    }

    /** Positions of the circuit motes, 0..1 along the small circuit from the perineum. */
    private static double[] motePositions(MeridianLook look, double seconds) {
        double[] positions = new double[look.flowing() ? look.circuitMotes() : 0];
        for (int mote = 0; mote < positions.length; mote++) {
            positions[mote] = look.circuitPosition(seconds, mote, BARRIER_AT);
        }
        return positions;
    }

    private static void drawFlow(GuiGraphics graphics, Box box, MeridianLook look, double[] motes, double seconds) {
        boolean limbFlow = look.limbMotes() > 0;
        if (motes.length == 0 && !look.gathering() && !limbFlow) {
            return;
        }
        VectorBrush brush = new VectorBrush(graphics).begin();
        int bright = brighten(look.color(), 0.55F);
        for (double position : motes) {
            mote(brush, box, CIRCUIT, position, TAIL, true, look.color(), bright, 1.0F);
        }
        if (limbFlow) {
            for (String id : List.of("hand", "foot")) {
                MeridianPath path = CHANNEL_PATHS.get(id);
                for (int mote = 0; mote < look.limbMotes(); mote++) {
                    double lap = seconds / 2.4D + mote / (double) look.limbMotes() + (id.equals("foot") ? 0.37D : 0.0D);
                    // inward: from the palm or sole back toward the trunk
                    double position = 1.0D - (lap - Math.floor(lap));
                    mote(brush, box, path, position, -0.12D, false, look.color(), bright, 0.75F);
                }
            }
        }
        if (look.gathering()) {
            MeridianChart.Acupoint dantian = MeridianChart.point(MeridianChart.DANTIAN).orElseThrow();
            float cx = box.px(dantian.x());
            float cy = box.py(dantian.y());
            for (int ripple = 0; ripple < 2; ripple++) {
                double lap = seconds / 1.8D + ripple / 2.0D;
                double progress = lap - Math.floor(lap);
                float radius = box.size * (DANTIAN_RADIUS + 0.26F * (float) (1.0D - progress));
                brush.arc(cx, cy, radius, 0.7F, 1.4F, 0.0D, 2.0D * Math.PI, look.color(),
                        0.45F * (float) Math.sin(Math.PI * progress));
            }
            int count = 14;
            for (int mote = 0; mote < count; mote++) {
                double lap = seconds / 2.2D + mote / (double) count;
                double progress = lap - Math.floor(lap);
                double angle = mote * 2.0D * Math.PI / count + progress * 2.4D;
                float radius = (float) (box.size * 0.30D * (1.0D - progress)) + 4.0F;
                float px = cx + (float) Math.cos(angle) * radius;
                float py = cy + (float) Math.sin(angle) * radius * 0.8F;
                float alpha = (float) Math.sin(Math.PI * progress);
                brush.disc(px, py, 3.6F, 0.0F, look.color(), 0.50F * alpha, 0.0F)
                        .disc(px, py, 0.9F, 0.5F, bright, alpha, alpha);
            }
        }
        brush.end(true);
    }

    /**
     * One mote of qi with a tail. {@code tail} is the tail's length as a share of the path; a
     * negative tail trails toward the path's end (for motes that run backwards).
     */
    private static void mote(VectorBrush brush, Box box, MeridianPath path, double position, double tail,
                             boolean loop, int color, int bright, float scale) {
        int steps = 10;
        float[] xs = new float[steps + 1];
        float[] ys = new float[steps + 1];
        float[] alphas = new float[steps + 1];
        int used = 0;
        for (int step = 0; step <= steps; step++) {
            double at = position - tail * (1.0D - step / (double) steps);
            if (loop) {
                at = at - Math.floor(at);
            } else if (at < 0.0D || at > 1.0D) {
                continue;
            }
            float[] point = path.pointAt(at);
            xs[used] = box.px(point[0]);
            ys[used] = box.py(point[1]);
            alphas[used] = (float) Math.pow(step / (double) steps, 1.6D);
            used++;
        }
        // a wrapped loop tail jumps from the end of the path to its start: keep the part nearest the head
        int start = 0;
        for (int index = 1; index < used; index++) {
            if (Math.hypot(xs[index] - xs[index - 1], ys[index] - ys[index - 1]) > box.size * 0.08F) {
                start = index;
            }
        }
        if (used - start >= 2) {
            brush.stroke(xs, ys, start, used - 1, 1.1F * scale, 1.2F * scale, color, 0.85F, alphas);
        }
        float[] head = path.pointAt(loop ? position - Math.floor(position) : Math.max(0.0D, Math.min(1.0D, position)));
        float hx = box.px(head[0]);
        float hy = box.py(head[1]);
        brush.disc(hx, hy, 4.2F * scale, 0.0F, color, 0.55F, 0.0F)
                .disc(hx, hy, 1.0F * scale, 0.6F, bright, 1.0F, 1.0F);
    }

    /** How strongly a point on the circuit at {@code at} flares as a mote passes it (0..1). */
    private static float flare(double at, double[] motes) {
        float strongest = 0.0F;
        for (double position : motes) {
            double distance = Math.abs(position - at);
            distance = Math.min(distance, 1.0D - distance);
            strongest = Math.max(strongest, (float) Math.max(0.0D, 1.0D - distance / 0.035D));
        }
        return strongest;
    }

    private static void drawPoints(GuiGraphics graphics, Box box, MeridianLook look, double[] motes, double seconds) {
        VectorBrush nodes = new VectorBrush(graphics).begin();
        VectorBrush glow = new VectorBrush(graphics).begin();
        float lit = look.circuitLevel();
        int ringColor = lit > 0.5F ? brighten(look.color(), 0.25F) : PanelTheme.MUTED;
        for (MeridianChart.Acupoint point : MeridianChart.points()) {
            if (point.tier() == MeridianChart.Tier.DANTIAN) {
                continue;
            }
            float px = box.px(point.x());
            float py = box.py(point.y());
            float flare = CIRCUIT.fractionOf(point.id())
                    .map(at -> flare(at, motes))
                    .orElse(0.0F);
            if (point.tier() == MeridianChart.Tier.MAJOR) {
                float core = 0.8F + 0.4F * lit;
                nodes.disc(px, py, 2.6F, 0.4F, mix(PanelTheme.BAR_TRACK, look.color(), 0.30F * lit), 0.95F, 0.95F)
                        .arc(px, py, 2.1F, 0.8F, 0.4F, 0.0D, 2.0D * Math.PI, ringColor, 0.55F + 0.45F * lit)
                        .disc(px, py, core, 0.4F, ringColor, 0.5F + 0.5F * lit, 0.5F + 0.5F * lit);
            } else {
                nodes.disc(px, py, 1.0F, 0.5F, ringColor, 0.35F + 0.5F * lit, 0.35F + 0.5F * lit);
            }
            if (flare > 0.0F) {
                glow.disc(px, py, 6.0F, 0.0F, look.color(), 0.6F * flare, 0.0F);
            }
        }
        if (look.limbMotes() > 0) {
            // spirit-stone qi enters at the palm and the sole
            for (String id : List.of("laogong", "yongquan")) {
                MeridianChart.Acupoint point = MeridianChart.point(id).orElseThrow();
                float beat = pulse(seconds + (id.equals("yongquan") ? 0.6D : 0.0D), 1.4D);
                float px = box.px(point.x());
                float py = box.py(point.y());
                float arm = 3.0F + 2.5F * beat;
                glow.disc(px, py, 6.0F, 0.0F, look.color(), 0.30F + 0.30F * beat, 0.0F)
                        .line(px - arm, py, px + arm, py, 0.6F, 0.6F, brighten(look.color(), 0.6F), 0.9F)
                        .line(px, py - arm, px, py + arm, 0.6F, 0.6F, brighten(look.color(), 0.6F), 0.9F);
            }
        }
        if (look.barrier()) {
            MeridianChart.Acupoint pass = MeridianChart.point(MeridianChart.YUZHEN).orElseThrow();
            float beat = pulse(seconds, 0.9D);
            float px = box.px(pass.x());
            float py = box.py(pass.y());
            glow.disc(px, py, 7.0F + 2.0F * beat, 0.0F, look.color(), 0.35F + 0.25F * beat, 0.0F)
                    .arc(px, py, 4.0F + 1.2F * beat, 1.0F, 0.8F, 0.0D, 2.0D * Math.PI, look.color(), 0.9F);
        }
        if (look.halo()) {
            MeridianChart.Acupoint crown = MeridianChart.point(MeridianChart.BAIHUI).orElseThrow();
            glow.disc(box.px(crown.x()), box.py(crown.y()), 7.0F, 0.0F, look.color(),
                    0.30F + 0.30F * pulse(seconds, 1.2D), 0.0F);
        }
        nodes.end(false);
        glow.end(true);
    }

    private static void drawDantian(GuiGraphics graphics, PanelContext context, Box box,
                                    MeridianLook look, double seconds) {
        MeridianChart.Acupoint dantian = MeridianChart.point(MeridianChart.DANTIAN).orElseThrow();
        float cx = box.px(dantian.x());
        float cy = box.py(dantian.y());
        float radius = box.size * DANTIAN_RADIUS;
        boolean active = look.phase() != MeridianLook.Phase.DORMANT && look.phase() != MeridianLook.Phase.REST;
        float glowLevel = active ? 0.30F + 0.20F * pulse(seconds, 2.5D) : 0.14F * look.figureLight();
        new VectorBrush(graphics).begin()
                .disc(cx, cy, radius * 2.8F, 0.0F, look.color(), glowLevel, 0.0F)
                .end(true);

        float dim = look.phase() == MeridianLook.Phase.DORMANT ? 0.55F : 1.0F;
        VectorBrush gauge = new VectorBrush(graphics).begin()
                .disc(cx, cy, radius + 0.6F, 0.5F, PanelTheme.BAR_TRACK, 0.95F, 0.95F);
        // cultivation progress over the stage cap fills the dantian from the bottom
        double fraction = context.progressFraction();
        if (fraction > 0.0D) {
            float top = cy + radius - (float) (2.0D * radius * fraction);
            float slice = 0.5F;
            for (float row = top; row < cy + radius; row += slice) {
                float middle = Math.min(cy + radius, row + slice / 2.0F) - cy;
                float half = (float) Math.sqrt(Math.max(0.0F, radius * radius - middle * middle));
                float share = (row - (cy - radius)) / (2.0F * radius);
                int color = mix(PanelTheme.JADE, PanelTheme.JADE_DARK, share);
                gauge.rect(cx - half, row, cx + half, Math.min(cy + radius, row + slice), color, 0.95F * dim);
            }
            if (fraction < 1.0D) {
                float middle = top - cy;
                float half = (float) Math.sqrt(Math.max(0.0F, radius * radius - middle * middle));
                gauge.rect(cx - half, top, cx + half, top + 0.6F, brighten(PanelTheme.JADE, 0.5F), 0.9F * dim);
            }
        }
        gauge.arc(cx, cy, radius + 0.3F, 0.6F, 0.4F, 0.0D, 2.0D * Math.PI, PanelTheme.JADE_DARK, dim);
        // stability over its cap runs clockwise around it from the top
        float ring = radius + 2.6F;
        gauge.arc(cx, cy, ring, 1.3F, 0.4F, 0.0D, 2.0D * Math.PI, 0xFF2C2A22, 0.95F);
        double stability = context.stabilityFraction();
        if (stability > 0.0D) {
            gauge.arc(cx, cy, ring, 1.3F, 0.4F, 0.0D, 2.0D * Math.PI * stability, PanelTheme.GOLD_BRIGHT, dim);
        }
        gauge.end(false);
    }

    private static int mix(int top, int bottom, float share) {
        float t = Math.max(0.0F, Math.min(1.0F, share));
        int r = Math.round(((top >> 16) & 0xFF) + (((bottom >> 16) & 0xFF) - ((top >> 16) & 0xFF)) * t);
        int g = Math.round(((top >> 8) & 0xFF) + (((bottom >> 8) & 0xFF) - ((top >> 8) & 0xFF)) * t);
        int b = Math.round((top & 0xFF) + ((bottom & 0xFF) - (top & 0xFF)) * t);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static void drawLabels(GuiGraphics graphics, Font font, Box box, MeridianLook look, int left, int right) {
        boolean lit = look.circuitLevel() > 0.5F;
        VectorBrush leaders = new VectorBrush(graphics).begin();
        List<Runnable> texts = new ArrayList<>();
        for (MeridianChart.Acupoint point : MeridianChart.points()) {
            if (point.label().isEmpty()) {
                continue;
            }
            MeridianChart.Label label = point.label().orElseThrow();
            String name = Component.translatable(point.translationKey()).getString();
            float px = box.px(point.x());
            float py = box.py(point.y());
            float ax = box.px(label.x());
            float ay = box.py(label.y());
            boolean toLeft = label.side() == MeridianChart.LabelSide.LEFT;
            int available = toLeft ? Math.round(ax) - left - 3 : right - 3 - Math.round(ax);
            String fitted = PanelTheme.fit(font, name, Math.max(1, available));
            int textWidth = font.width(fitted);
            int textX = toLeft ? Math.round(ax) - textWidth : Math.round(ax);
            int textY = Math.round(ay) - 4;
            float endX = toLeft ? ax + 2.0F : ax - 2.0F;
            float dx = endX - px;
            float dy = ay - py;
            float distance = (float) Math.hypot(dx, dy);
            float gap = point.tier() == MeridianChart.Tier.DANTIAN ? box.size * DANTIAN_RADIUS + 4.0F : 3.5F;
            if (distance > gap + 1.0F) {
                leaders.line(px + dx / distance * gap, py + dy / distance * gap, endX, ay,
                        0.5F, 0.5F, PanelTheme.MUTED, 0.55F);
            }
            int color = point.tier() == MeridianChart.Tier.DANTIAN
                    ? PanelTheme.GOLD_BRIGHT
                    : lit ? PanelTheme.TEXT : PanelTheme.MUTED;
            texts.add(() -> graphics.drawString(font, fitted, textX, textY, color, false));
        }
        leaders.end(false);
        texts.forEach(Runnable::run);
    }
}
