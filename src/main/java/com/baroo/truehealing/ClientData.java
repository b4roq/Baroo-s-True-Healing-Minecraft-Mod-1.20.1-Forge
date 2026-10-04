package com.baroo.truehealing;

import net.minecraft.nbt.CompoundTag;

/** Latest data received from the server (no client-only imports, safe anywhere). */
public final class ClientData {
    public static InjuryData data = new InjuryData();
    public static String message = "";
    public static long messageUntil = 0L;
    public static int panicLevel = 0;
    public static String actionLabel = "";
    public static long actionStartMs = 0L;
    public static long actionTotalMs = 0L;

    public static void set(CompoundTag tag) { data = InjuryData.load(tag); }

    public static void setMessage(String text) {
        message = text;
        messageUntil = System.currentTimeMillis() + 4000L;
    }

    public static void setAction(String label, int ticks) {
        if (ticks <= 0) {
            actionTotalMs = 0L;
            actionLabel = "";
        } else {
            actionLabel = label;
            actionStartMs = System.currentTimeMillis();
            actionTotalMs = ticks * 50L;
        }
    }

    private ClientData() {}
}
