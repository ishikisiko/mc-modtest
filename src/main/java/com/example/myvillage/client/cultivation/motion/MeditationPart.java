package com.example.myvillage.client.cultivation.motion;

/** Implemented only on the client ModelPart mixin; copyFrom carries the current warp to armor. */
public interface MeditationPart {
    MeditationWarp myvillage$meditationWarp();
    void myvillage$meditationWarp(MeditationWarp value);
}
