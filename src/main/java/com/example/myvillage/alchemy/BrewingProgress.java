package com.example.myvillage.alchemy;

/** Pure saved progress machine: output blockage pauses; changed input identity resets. */
public final class BrewingProgress {
    private String recipe = "";
    private int ticks;
    public static boolean outputFits(boolean empty, boolean sameItemAndComponents, int count, int batch, int stackLimit) {
        return batch > 0 && batch <= stackLimit && (empty || (sameItemAndComponents && count <= stackLimit - batch));
    }
    public String recipe() { return recipe; }
    public int ticks() { return ticks; }
    public void reset() { recipe = ""; ticks = 0; }
    public void restore(String recipe, int ticks, int duration) {
        this.recipe = recipe;
        this.ticks = Math.max(0, Math.min(ticks, duration - 1));
    }
    /** True exactly once at completion; caller commits all input/output changes atomically. */
    public boolean advance(String nextRecipe, int duration, boolean inputsChanged, boolean ready) {
        if (inputsChanged || !recipe.equals(nextRecipe)) { ticks = 0; recipe = nextRecipe; }
        if (nextRecipe.isEmpty()) { reset(); return false; }
        if (!ready) return false;
        if (++ticks >= duration) { ticks = 0; return true; }
        return false;
    }
}
