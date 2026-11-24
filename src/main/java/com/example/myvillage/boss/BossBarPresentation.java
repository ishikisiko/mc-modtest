package com.example.myvillage.boss;

import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Structured presentation carried by vanilla boss-name packets; no entity/name guessing. */
public record BossBarPresentation(Component title, Component realm, Component status, boolean emphasized) {
    public static final String KEY = "entity.yuanxian.thunder_tiger.bossbar";
    public static final String PHASE_ONE = "hud.yuanxian.boss.phase_one";
    public static final String PHASE_TWO = "hud.yuanxian.boss.phase_two";
    public static final String HORN_INTACT = "hud.yuanxian.boss.horn_intact";
    public static final String HORN_BROKEN = "hud.yuanxian.boss.horn_broken";
    public static final String LOTUS_KEY = "entity.yuanxian.lotus_guard.bossbar";
    public static final String LOTUS_PROTECTED = "hud.yuanxian.boss.lotus_guard.protected";
    public static final String LOTUS_LEFT_EXPOSED = "hud.yuanxian.boss.lotus_guard.left_exposed";
    public static final String LOTUS_RIGHT_EXPOSED = "hud.yuanxian.boss.lotus_guard.right_exposed";

    public static Component name(Component title, Component realm, boolean enraged, boolean broken) {
        return Component.translatable(KEY, title, realm,
                Component.translatable(enraged ? PHASE_TWO : PHASE_ONE),
                Component.translatable(broken ? HORN_BROKEN : HORN_INTACT));
    }

    public static Component lotusName(Component title, Component realm, int openFlank) {
        String status = switch (openFlank) {
            case 1 -> LOTUS_LEFT_EXPOSED;
            case -1 -> LOTUS_RIGHT_EXPOSED;
            default -> LOTUS_PROTECTED;
        };
        return Component.translatable(LOTUS_KEY, title, realm, Component.translatable(status));
    }

    public static Optional<BossBarPresentation> read(Component name) {
        if (!(name.getContents() instanceof TranslatableContents text)
                || !name.getSiblings().isEmpty()) return Optional.empty();
        Object[] args = text.getArgs();
        if (args.length < 3 || !(args[0] instanceof Component title) || !(args[1] instanceof Component realm)
                || !(args[2] instanceof Component status)) return Optional.empty();
        if (KEY.equals(text.getKey()) && args.length == 4 && args[3] instanceof Component horn
                && (key(status, PHASE_ONE) || key(status, PHASE_TWO))
                && (key(horn, HORN_INTACT) || key(horn, HORN_BROKEN))) {
            return Optional.of(new BossBarPresentation(title, realm,
                    Component.empty().append(status).append(" · ").append(horn), key(status, PHASE_TWO)));
        }
        if (LOTUS_KEY.equals(text.getKey()) && args.length == 3
                && (key(status, LOTUS_PROTECTED) || key(status, LOTUS_LEFT_EXPOSED) || key(status, LOTUS_RIGHT_EXPOSED))) {
            return Optional.of(new BossBarPresentation(title, realm, status, !key(status, LOTUS_PROTECTED)));
        }
        return Optional.empty();
    }

    private static boolean key(Component component, String key) {
        return component.getContents() instanceof TranslatableContents text && key.equals(text.getKey());
    }
}
