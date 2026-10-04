package com.baroo.truehealing;

import net.minecraftforge.common.ForgeConfigSpec;

/** Per-player (client side) settings: config/truehealing-client.toml */
public final class TrueHealingClientConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue MOODLES_ENABLED;
    public static final ForgeConfigSpec.IntValue MOODLE_OFFSET_Y;
    public static final ForgeConfigSpec.IntValue MOODLE_OFFSET_X;
    public static final ForgeConfigSpec.DoubleValue MOODLE_SCALE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        MOODLES_ENABLED = b.comment("Show the moodle icons on the right side of the screen.")
                .define("moodlesEnabled", true);
        MOODLE_OFFSET_Y = b.comment("Distance from the top of the screen in pixels. Raise it to move the moodles down",
                        "(for example below potion effect icons or a minimap).")
                .defineInRange("moodleOffsetY", 60, 0, 4000);
        MOODLE_OFFSET_X = b.comment("Distance from the right edge of the screen in pixels.")
                .defineInRange("moodleOffsetX", 6, 0, 4000);
        MOODLE_SCALE = b.comment("Size multiplier for the moodle icons.")
                .defineInRange("moodleScale", 1.0, 0.5, 3.0);
        SPEC = b.build();
    }

    private TrueHealingClientConfig() {}
}
