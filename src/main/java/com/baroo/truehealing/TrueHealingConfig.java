package com.baroo.truehealing;

import net.minecraftforge.common.ForgeConfigSpec;

public final class TrueHealingConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.BooleanValue SHOW_MESSAGES;
    public static final ForgeConfigSpec.DoubleValue WOUND_CHANCE_BASE;
    public static final ForgeConfigSpec.DoubleValue WOUND_CHANCE_PER_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue BLEED_MULT;
    public static final ForgeConfigSpec.DoubleValue INFECTION_CHANCE_MULT;
    public static final ForgeConfigSpec.DoubleValue INFECTION_SPEED;
    public static final ForgeConfigSpec.DoubleValue HEAL_SPEED_MULT;
    public static final ForgeConfigSpec.BooleanValue FRACTURES_ENABLED;
    public static final ForgeConfigSpec.DoubleValue FRACTURE_MIN_FALL;
    public static final ForgeConfigSpec.DoubleValue FRACTURE_MAX_FALL;
    public static final ForgeConfigSpec.DoubleValue FRACTURE_MIN_CHANCE;
    public static final ForgeConfigSpec.DoubleValue FRACTURE_MAX_CHANCE;
    public static final ForgeConfigSpec.DoubleValue FRACTURE_SLOW_ONE;
    public static final ForgeConfigSpec.DoubleValue FRACTURE_SLOW_BOTH;
    public static final ForgeConfigSpec.DoubleValue ANTIBIOTIC_FACTOR;
    public static final ForgeConfigSpec.IntValue ANTIBIOTIC_SECONDS;
    public static final ForgeConfigSpec.DoubleValue ACTION_TIME_MULT;
    public static final ForgeConfigSpec.BooleanValue BLOCK_SLEEP_IN_PAIN;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        ENABLED = b.comment("Master switch.").define("enabled", true);
        SHOW_MESSAGES = b.comment("Show action-bar messages when wounds happen, get infected or heal.")
                .define("showMessages", true);
        WOUND_CHANCE_BASE = b.comment("Base chance that a damaging hit leaves a wound.")
                .defineInRange("woundChanceBase", 0.35, 0.0, 1.0);
        WOUND_CHANCE_PER_DAMAGE = b.comment("Extra wound chance per point of damage taken.")
                .defineInRange("woundChancePerDamage", 0.08, 0.0, 1.0);
        BLEED_MULT = b.comment("Multiplier for bleeding damage. 0 disables bleeding damage.")
                .defineInRange("bleedMultiplier", 1.0, 0.0, 10.0);
        INFECTION_CHANCE_MULT = b.comment("Multiplier for the chance a wound gets infected. 0 disables infection.")
                .defineInRange("infectionChanceMultiplier", 1.0, 0.0, 10.0);
        INFECTION_SPEED = b.comment("Infection progress in percent per second once infected.")
                .defineInRange("infectionSpeedPercentPerSecond", 0.15, 0.0, 10.0);
        HEAL_SPEED_MULT = b.comment("Multiplier for how fast treated wounds heal.")
                .defineInRange("healSpeedMultiplier", 1.0, 0.1, 20.0);
        FRACTURES_ENABLED = b.comment("Falling can break legs.").define("fracturesEnabled", true);
        FRACTURE_MIN_FALL = b.comment("Fall height (blocks) where fractures can start.")
                .defineInRange("fractureMinFall", 7.0, 1.0, 200.0);
        FRACTURE_MAX_FALL = b.comment("Fall height (blocks) where the chance reaches its maximum.")
                .defineInRange("fractureMaxFall", 20.0, 2.0, 200.0);
        FRACTURE_MIN_CHANCE = b.comment("Chance per leg at the minimum fall height.")
                .defineInRange("fractureMinChance", 0.10, 0.0, 1.0);
        FRACTURE_MAX_CHANCE = b.comment("Chance per leg at the maximum fall height.")
                .defineInRange("fractureMaxChance", 0.90, 0.0, 1.0);
        FRACTURE_SLOW_ONE = b.comment("Walking speed lost with one unsplinted broken leg (0.2 = 20%).")
                .defineInRange("fractureSlowOneLeg", 0.20, 0.0, 0.95);
        FRACTURE_SLOW_BOTH = b.comment("Walking speed lost with two unsplinted broken legs.")
                .defineInRange("fractureSlowBothLegs", 0.45, 0.0, 0.95);
        ANTIBIOTIC_FACTOR = b.comment("Infection speed multiplier while antibiotics are active (0.25 = 4x slower).")
                .defineInRange("antibioticSlowFactor", 0.25, 0.0, 1.0);
        ANTIBIOTIC_SECONDS = b.comment("How long one dose of antibiotics lasts, in seconds.")
                .defineInRange("antibioticDurationSeconds", 360, 10, 86400);
        ACTION_TIME_MULT = b.comment("Multiplier for how long treatments take (1.0 = default, 0.5 = twice as fast).")
                .defineInRange("actionTimeMultiplier", 1.0, 0.1, 5.0);
        BLOCK_SLEEP_IN_PAIN = b.comment("You cannot sleep in a bed while in major pain or agony (under 55% health).")
                .define("blockSleepInPain", true);
        SPEC = b.build();
    }

    private TrueHealingConfig() {}
}
