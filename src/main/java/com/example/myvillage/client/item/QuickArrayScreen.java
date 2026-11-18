package com.example.myvillage.client.item;

import com.example.myvillage.item.QualityPickerArrays;
import com.example.myvillage.network.QualityPickerArrayPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

final class QuickArrayScreen extends Screen {
    private final Screen parent;

    QuickArrayScreen(Screen parent) {
        super(Component.literal("多方块快捷搭建"));
        this.parent = parent;
    }

    @Override protected void init() {
        int x = (width - 220) / 2;
        int y = (height - 172) / 2;
        for (var layout : QualityPickerArrays.Layout.values()) {
            if (layout == QualityPickerArrays.Layout.NONE) continue;
            addRenderableWidget(Button.builder(Component.literal(layout.title()), button -> choose(layout))
                    .bounds(x, y + 42 + (layout.id() - 1) * 21, 220, 19).build());
        }
        addRenderableWidget(Button.builder(Component.literal("关闭快捷搭建"), button -> choose(QualityPickerArrays.Layout.NONE))
                .bounds(x, y + 130, 106, 19).build());
        addRenderableWidget(Button.builder(Component.literal("返回取物器"), button -> onClose())
                .bounds(x + 114, y + 130, 106, 19).build());
    }

    private void choose(QualityPickerArrays.Layout layout) {
        PacketDistributor.sendToServer(new QualityPickerArrayPayload(layout.id(), 0));
        minecraft.setScreen(null);
    }

    static void confirm(Minecraft mc, QualityPickerArrays.Preview preview) {
        var box = QualityPickerArrays.blueprint(preview.layout(), preview.ground(), preview.front()).bounds();
        String size = box.getXSpan() + " × " + box.getYSpan() + " × " + box.getZSpan() + " 格";
        var screen = new ConfirmScreen(confirmed -> {
            PacketDistributor.sendToServer(new QualityPickerArrayPayload(
                    confirmed ? QualityPickerArrays.CONFIRM : QualityPickerArrays.CANCEL, preview.ticket()));
            mc.setScreen(null);
        }, Component.literal("搭建" + preview.layout().title() + "？"),
                Component.literal("将清空 " + size + " 的预览区域，其中有 " + preview.occupied()
                        + " 格现有方块。区域内的容器和内容物也会清除，然后按蓝图搭建。"),
                Component.literal("清空并搭建"), Component.literal("取消预览"));
        mc.setScreen(screen);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int y = (height - 172) / 2;
        graphics.drawCenteredString(font, title, width / 2, y, 0xffecdab4);
        graphics.drawCenteredString(font, "潜行右键地面选区，普通右键确认", width / 2, y + 18, 0xffeee7db);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
