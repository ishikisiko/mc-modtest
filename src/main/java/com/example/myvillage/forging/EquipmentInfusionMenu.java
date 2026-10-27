package com.example.myvillage.forging;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;

/** Private equipment infusion inventory, opened only by the forging ding. */
public final class EquipmentInfusionMenu extends WorkshopMenu {
    public EquipmentInfusionMenu(int id,Inventory inventory){this(id,inventory,ContainerLevelAccess.NULL);}
    public EquipmentInfusionMenu(int id,Inventory inventory,ContainerLevelAccess access){
        super(ForgingMenus.EQUIPMENT_INFUSION.get(),id,inventory,access,true);
    }
}
