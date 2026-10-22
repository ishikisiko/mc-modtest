package com.example.myvillage.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Replaces only the player's hearts. Values remain vanilla server-synchronized health. */
public final class HealthBarLayer {
    private static int reservedHeight;
    private HealthBarLayer() {}

    public static void register(RegisterGuiLayersEvent event) {
        event.replaceLayer(VanillaGuiLayers.PLAYER_HEALTH, HealthBarLayer::render);
        // Keep armor and food on their original shared baseline, then reserve the full-width bar
        // for air, mount hearts, item names and action text drawn by later vanilla layers.
        event.wrapLayer(VanillaGuiLayers.ARMOR_LEVEL, original -> (g, delta) -> {
            original.render(g, delta);
            if (visible()) Minecraft.getInstance().gui.leftHeight = Math.max(Minecraft.getInstance().gui.leftHeight, reservedHeight);
        });
        event.wrapLayer(VanillaGuiLayers.FOOD_LEVEL, original -> (g, delta) -> {
            original.render(g, delta);
            if (visible()) Minecraft.getInstance().gui.rightHeight = Math.max(Minecraft.getInstance().gui.rightHeight, reservedHeight);
        });
    }

    private static boolean visible() {
        var mc = Minecraft.getInstance();
        return mc.level != null && mc.player != null && mc.gameMode != null && mc.gameMode.canHurtPlayer()
                && !mc.options.hideGui && !mc.player.isSpectator() && mc.getCameraEntity() instanceof Player;
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        if (!visible()) return;
        var mc = Minecraft.getInstance();
        var player = (Player)mc.getCameraEntity();
        var box = HealthBarLayout.at(g.guiWidth(), g.guiHeight(), Math.max(mc.gui.leftHeight, mc.gui.rightHeight));
        reservedHeight = box.reservedHeight();
        double health = player.getHealth(), maximum = player.getMaxHealth(), absorption = player.getAbsorptionAmount();
        boolean low = HealthBarLayout.ratio(health, maximum) <= .25;
        int dark = 0xFF7E292B, light = 0xFFC56251;
        String label = "生命";
        if (player.hasEffect(MobEffects.WITHER)) { dark=0xFF3E3946;light=0xFF998499;label="凋零"; }
        else if (player.hasEffect(MobEffects.POISON)) { dark=0xFF426A35;light=0xFF8AA653;label="中毒"; }
        else if (player.isFullyFrozen()) { dark=0xFF3E6886;light=0xFFA4D6E5;label="冰冻"; }
        else if (player.hasEffect(MobEffects.REGENERATION)) label="回复";
        if (player.level().getLevelData().isHardcore()) label="极限·"+label;
        if (low) label="濒危·"+label;
        g.drawString(mc.font,label,box.x(),box.headerY(),low?0xFFFFA895:0xFFE0BB72,true);
        if (absorption > 0) {
            String shield="吸收 +"+HealthBarLayout.number(absorption);
            text(g,shield,box.x()+box.width()/2,box.headerY(),box.width()/2,0xFFE0BB72,false);
        }
        int x=box.x(),y=box.y(),w=box.width();
        g.fill(x-1,y-1,x+w+1,y+14,0xB509110E);
        g.fill(x,y,x+w,y+13,player.hurtTime>0?0xFFF4DBB4:low?0xFFE59078:0xFFA58C5E);
        g.fill(x+1,y+1,x+w-1,y+12,0xEE231B1A);
        int filled=HealthBarLayout.fill(health,maximum,w-4);
        for(int column=0;column<filled;column++) {
            float t=column/(float)(w-4);
            int r=Math.round(((dark>>16)&255)*(1-t)+((light>>16)&255)*t);
            int green=Math.round(((dark>>8)&255)*(1-t)+((light>>8)&255)*t);
            int b=Math.round((dark&255)*(1-t)+(light&255)*t);
            g.fill(x+2+column,y+2,x+3+column,y+11,0xFF000000|r<<16|green<<8|b);
        }
        text(g,HealthBarLayout.balance(health,maximum),x+3,y+3,w-6,0xFFFFF0D3,true);
        // Gold has its own scale (absorption / max health), never changes the red denominator.
        if(absorption>0) {
            g.fill(x,box.absorptionY(),x+w,box.absorptionY()+2,0xDD18251E);
            g.fill(x,box.absorptionY(),x+HealthBarLayout.fill(absorption,maximum,w),box.absorptionY()+2,0xFFD6B669);
        }
    }

    private static void text(GuiGraphics g,String text,int x,int y,int width,int color,boolean center) {
        var font=Minecraft.getInstance().font;
        float scale=Math.min(1,width/(float)Math.max(1,font.width(text)));
        float offset=center?(width-font.width(text)*scale)/2:width-font.width(text)*scale;
        g.pose().pushPose();g.pose().translate(x+offset,y,0);g.pose().scale(scale,scale,1);
        g.drawString(font,text,0,0,color,true);g.pose().popPose();
    }
}
