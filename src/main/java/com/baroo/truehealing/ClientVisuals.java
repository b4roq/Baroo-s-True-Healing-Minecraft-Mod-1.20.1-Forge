package com.baroo.truehealing;

import java.util.HashMap;
import java.util.Map;

/** Compact per-player visual state (for rendering bandages/wounds on any visible player). */
public final class ClientVisuals {
    private static final Map<Integer, byte[]> MAP = new HashMap<>();

    public static void set(int entityId, byte[] data) { MAP.put(entityId, data); }
    public static byte[] get(int entityId) { return MAP.get(entityId); }
    public static void clear() { MAP.clear(); }

    private ClientVisuals() {}
}
