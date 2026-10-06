package com.example.myvillage.sim.model;

/**
 * A heritage (传承) whose sect was destroyed: it waits in the lost pool until a founder who learned
 * one of its techniques rekindles it. Its manuals turn up in ruins meanwhile.
 *
 * @param heritageId the heritage
 * @param sectId     the sect that held it last
 * @param day        the day it was lost
 */
public record LostHeritage(String heritageId, int sectId, long day) {
}
