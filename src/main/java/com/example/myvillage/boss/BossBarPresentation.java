package com.example.myvillage.boss;

import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Structured presentation carried by vanilla boss-name packets; no entity/name guessing. */
public record BossBarPresentation(Component title, Component realm, Component phase, Component horn) {
    public static final String KEY = "entity.myvillage.thunder_tiger.bossbar";
    public static final String PHASE_ONE = "hud.myvillage.boss.phase_one";
    public static final String PHASE_TWO = "hud.myvillage.boss.phase_two";
    public static final String HORN_INTACT = "hud.myvillage.boss.horn_intact";
    public static final String HORN_BROKEN = "hud.myvillage.boss.horn_broken";

    public static Component name(Component title, Component realm, boolean enraged, boolean broken) {
        return Component.translatable(KEY, title, realm,
                Component.translatable(enraged ? PHASE_TWO : PHASE_ONE),
                Component.translatable(broken ? HORN_BROKEN : HORN_INTACT));
    }

    public static Optional<BossBarPresentation> read(Component name) {
        if (!(name.getContents() instanceof TranslatableContents text) || !KEY.equals(text.getKey())
                || !name.getSiblings().isEmpty()) return Optional.empty();
        Object[] args = text.getArgs();
        if (args.length != 4 || !(args[0] instanceof Component title) || !(args[1] instanceof Component realm)
                || !(args[2] instanceof Component phase) || !(args[3] instanceof Component horn)
                || !(key(phase, PHASE_ONE) || key(phase, PHASE_TWO))
                || !(key(horn, HORN_INTACT) || key(horn, HORN_BROKEN))) return Optional.empty();
        return Optional.of(new BossBarPresentation(title, realm, phase, horn));
    }

    public boolean enraged() { return key(phase, PHASE_TWO); }
    public boolean broken() { return key(horn, HORN_BROKEN); }

    private static boolean key(Component component, String key) {
        return component.getContents() instanceof TranslatableContents text && key.equals(text.getKey());
    }
}
