package com.baroo.truehealing;

public enum DressingType {
    //          label      lifeTicks  risk while clean  risk while dirty
    NONE("None",            0,        1.0,              1.0),
    RAG("Rag",              6000,     0.4,              3.0),   // 5 min, then dirty; still lets some infection in
    BANDAGE("Bandage",      24000,    0.0,              2.0),   // 20 min, seals the wound: no infection while clean
    BANDAID("Bandaid",      0,        0.4,              0.4);   // never gets dirty, acts like a rag, lost when removed

    public final String label;
    public final int lifeTicks;
    public final double cleanRiskMult;
    public final double dirtyRiskMult;

    DressingType(String label, int lifeTicks, double cleanRiskMult, double dirtyRiskMult) {
        this.label = label;
        this.lifeTicks = lifeTicks;
        this.cleanRiskMult = cleanRiskMult;
        this.dirtyRiskMult = dirtyRiskMult;
    }

    public boolean canCover(WoundType type) {
        if (this == NONE || type == WoundType.FRACTURE) return false;
        if (this == BANDAID) return type == WoundType.SCRATCH;
        return true;
    }
}
