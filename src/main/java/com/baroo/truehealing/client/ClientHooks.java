package com.baroo.truehealing.client;

import net.minecraft.client.Minecraft;

public final class ClientHooks {
    private ClientHooks() {}

    public static void openMedicalScreen() {
        Minecraft.getInstance().setScreen(new MedicalScreen());
    }
}
