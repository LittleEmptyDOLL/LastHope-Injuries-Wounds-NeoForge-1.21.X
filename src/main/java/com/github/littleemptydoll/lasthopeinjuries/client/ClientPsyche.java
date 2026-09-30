package com.github.littleemptydoll.lasthopeinjuries.client;

public final class ClientPsyche {
    private static int stress;
    private static int panic;

    private ClientPsyche() {}

    public static void receive(int newStress, int newPanic) {
        stress = Math.max(0, Math.min(4, newStress));
        panic = Math.max(0, Math.min(4, newPanic));
    }

    public static int stress() { return stress; }
    public static int panic() { return panic; }

    public static void reset() { receive(0, 0); }
}
