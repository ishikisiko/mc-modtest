package com.example.myvillage.forging;

/** Fixed menu roles also reject packets for controls belonging to another station. */
public final class WorkshopStationAccess {
    private WorkshopStationAccess() {}
    public static boolean allows(boolean infusion,int button,int recipeCount) {
        if(infusion)return false;
        return button==WorkshopMenu.QUENCH||button==WorkshopMenu.GRIND||button==WorkshopMenu.REPAIR
                ||(button>WorkshopMenu.PATH_BASE&&button<=WorkshopMenu.PATH_BASE+3);
    }
}
