package com.example.myvillage.sim.engine;

/** 为跨包的编解码夹具提交显式准备的事件，不扩大生产API的访问范围。 */
public final class ChronicleTestFixture {
    private ChronicleTestFixture() { }
    public static void finishDay(SimContext context) { context.chronicle.endDay(); }
}
