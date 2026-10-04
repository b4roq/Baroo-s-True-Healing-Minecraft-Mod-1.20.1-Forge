package com.baroo.truehealing;

public enum WoundType {
    //            label            sev  bleed/10s  infect/min  healTicks  needsStitches
    SCRATCH("Scratch",             1,   0.0,       0.03,       6000,      false),   // 5 min, never bleeds
    LACERATION("Laceration",       2,   1.0,       0.10,       18000,     false),   // 15 min (10 min once stitched)
    DEEP_WOUND("Deep wound",       3,   2.0,       0.20,       18000,     true),    // 15 min no matter what; bleeds out unless stitched
    FRACTURE("Fracture",           3,   0.0,       0.0,        18000,     false);

    public final String label;
    public final int severity;
    public final double bleedPer10s;
    public final double infectionPerMin;
    public final int healTicks;
    public final boolean requiresStitches;

    WoundType(String label, int severity, double bleedPer10s, double infectionPerMin,
              int healTicks, boolean requiresStitches) {
        this.label = label;
        this.severity = severity;
        this.bleedPer10s = bleedPer10s;
        this.infectionPerMin = infectionPerMin;
        this.healTicks = healTicks;
        this.requiresStitches = requiresStitches;
    }
}
