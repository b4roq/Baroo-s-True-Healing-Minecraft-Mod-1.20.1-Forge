package com.baroo.truehealing;

public enum TreatAction {
    DISINFECT, RAG, BANDAGE, BANDAID, STITCH, REMOVE, SPLINT, REMOVE_SPLINT, DIRTY_RAG, DIRTY_BANDAGE;

    public static TreatAction byIndex(int i) {
        TreatAction[] v = values();
        return (i >= 0 && i < v.length) ? v[i] : REMOVE;
    }
}
