package com.baroo.truehealing;

public enum BodyPart {
    HEAD("Head"), TORSO("Torso"), LEFT_ARM("Left arm"), RIGHT_ARM("Right arm"),
    LEFT_LEG("Left leg"), RIGHT_LEG("Right leg");

    public final String label;

    BodyPart(String label) { this.label = label; }

    public static BodyPart byIndex(int i) {
        BodyPart[] v = values();
        return (i >= 0 && i < v.length) ? v[i] : TORSO;
    }
}
