package com.baroo.truehealing;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

public class Wound {
    /** A dressing on a seeping wound turns dirty after this long (4 minutes). */
    public static final int SEEP_DIRTY_TICKS = 4800;

    public WoundType type;
    public int age;              // ticks since the wound was made
    public int disinfectTicks;   // infection progress is paused while > 0
    public boolean stitched;
    public DressingType dressing = DressingType.NONE;
    public int dressingAge;      // ticks the dressing has been on
    public float infection;      // 0 = clean, 1..100 = infected
    public int healProgress;     // ticks of healing accumulated
    public boolean splinted;     // fractures only

    public Wound(WoundType type) { this.type = type; }

    public boolean isInfected() { return infection > 0f; }

    /** Open wounds that leak blood into a dressing (or onto the floor) until stitched. */
    public boolean seeps() {
        return !stitched && (type == WoundType.LACERATION || type == WoundType.DEEP_WOUND);
    }

    /** Ticks until the current dressing turns dirty. */
    public int dirtyAfter() {
        if (dressing == DressingType.NONE || dressing == DressingType.BANDAID) return Integer.MAX_VALUE;
        return seeps() ? Math.min(dressing.lifeTicks, SEEP_DIRTY_TICKS) : dressing.lifeTicks;
    }

    public boolean isDressingDirty() {
        return dressing != DressingType.NONE && dressing != DressingType.BANDAID && dressingAge >= dirtyAfter();
    }

    /**
     * 1.0 = bleeding fully, 0 = no blood loss. Scratches and fractures never bleed; a dressing stops a
     * laceration completely (it only seeps into the cloth) but only slows a deep wound.
     */
    public double bleedFactor() {
        if (type == WoundType.FRACTURE || type == WoundType.SCRATCH) return 0.0;
        if (stitched) return 0.0;
        if (dressing != DressingType.NONE) {
            if (type != WoundType.DEEP_WOUND) return 0.0;
            return dressing == DressingType.BANDAGE ? 0.25 : 0.4;
        }
        return 1.0;
    }

    public boolean isBleeding() { return bleedFactor() > 0.0; }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putByte("type", (byte) type.ordinal());
        t.putInt("age", age);
        t.putInt("dis", disinfectTicks);
        t.putBoolean("st", stitched);
        t.putByte("dr", (byte) dressing.ordinal());
        t.putInt("dra", dressingAge);
        t.putFloat("inf", infection);
        t.putInt("heal", healProgress);
        t.putBoolean("sp", splinted);
        return t;
    }

    public static Wound load(CompoundTag t) {
        WoundType[] types = WoundType.values();
        DressingType[] dressings = DressingType.values();
        Wound w = new Wound(types[Mth.clamp(t.getByte("type"), 0, types.length - 1)]);
        w.age = t.getInt("age");
        w.disinfectTicks = t.getInt("dis");
        w.stitched = t.getBoolean("st");
        w.dressing = dressings[Mth.clamp(t.getByte("dr"), 0, dressings.length - 1)];
        w.dressingAge = t.getInt("dra");
        w.infection = t.getFloat("inf");
        w.healProgress = t.getInt("heal");
        w.splinted = t.getBoolean("sp");
        return w;
    }
}
