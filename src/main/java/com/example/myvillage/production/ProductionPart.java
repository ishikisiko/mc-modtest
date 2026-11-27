package com.example.myvillage.production;

import java.util.Arrays;
import java.util.Locale;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

enum ProductionPart {
    MINE_STATION, MINE_COLUMN, MINE_INTAKE, MINE_HEAD, MINE_OUTLET, MINE_BEAM,
    CONDENSER_INTAKE, BRONZE_CONDENSER, JADE_CRYSTAL_SEAT, JADE_CONDENSER,
    GOLDEN_CRYSTAL_INTAKE, CRYSTAL_CHANNEL, CRYSTAL_PRESS_BASE, GOLDEN_CONDENSER, CRYSTAL_OUTLET,
    IRON_MINE_EYE, XUANTIE_MINE_EYE;

    final String id = name().toLowerCase(Locale.ROOT);

    static ProductionPart byId(String id) {
        return Arrays.stream(values()).filter(part -> part.id.equals(id)).findFirst().orElseThrow();
    }

    boolean eye() { return this == IRON_MINE_EYE || this == XUANTIE_MINE_EYE; }
    boolean outlet() { return this == MINE_OUTLET || this == CRYSTAL_OUTLET; }
    boolean controller() { return this == MINE_STATION || this == BRONZE_CONDENSER || this == JADE_CONDENSER || this == GOLDEN_CONDENSER; }
    boolean directional() { return controller() || outlet() || this == MINE_BEAM || this == MINE_HEAD; }

    VoxelShape northShape() {
        return switch (this) {
            case MINE_COLUMN -> Shapes.box(.08, 0, .1, .92, 1, .9);
            case MINE_BEAM -> Shapes.box(0, 0, .14, 1, .67, .86);
            case MINE_HEAD -> Shapes.box(.16, 0, .15, .84, 1, .85);
            case MINE_OUTLET, CRYSTAL_OUTLET -> Shapes.box(.1, .1, .03, .9, .68, 1);
            case MINE_INTAKE -> Shapes.box(.08, 0, .08, .92, .64, .92);
            case JADE_CRYSTAL_SEAT, GOLDEN_CRYSTAL_INTAKE -> Shapes.box(.045, 0, .045, .955, .91, .955);
            case CRYSTAL_CHANNEL -> Shapes.box(0, 0, 0, 1, .135, 1);
            case IRON_MINE_EYE, XUANTIE_MINE_EYE -> Shapes.block();
            default -> Shapes.box(.045, 0, .045, .955, 1, .955);
        };
    }
}
