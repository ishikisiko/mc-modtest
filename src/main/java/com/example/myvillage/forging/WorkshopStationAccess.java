package com.example.myvillage.forging;

/** Fixed menu roles also reject packets for controls belonging to another station. */
public final class WorkshopStationAccess {
    private WorkshopStationAccess() {}
    public static boolean allows(boolean infusion,int button,int recipeCount) {
        if(infusion)return button==WorkshopMenu.INFUSE||button==WorkshopMenu.COPPER
                ||(button>=WorkshopMenu.TRAIT_BASE&&button<WorkshopMenu.TRAIT_BASE+ForgeMark.TRAITS.size());
        return (button>=WorkshopMenu.START&&button<=WorkshopMenu.ASSEMBLE)||button==WorkshopMenu.REPAIR
                ||(button>=WorkshopMenu.RECIPE_BASE&&button<WorkshopMenu.RECIPE_BASE+recipeCount)
                ||(button>WorkshopMenu.PATH_BASE&&button<=WorkshopMenu.PATH_BASE+3)
                ||(button>=WorkshopMenu.TREATMENT_BASE&&button<=WorkshopMenu.TREATMENT_BASE+2);
    }
}
