package com.baroo.truehealing.client;

import org.lwjgl.glfw.GLFW;

import com.baroo.truehealing.TrueHealing;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TrueHealing.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    public static final KeyMapping OPEN_KEY =
            new KeyMapping("key.truehealing.open", GLFW.GLFW_KEY_H, "key.categories.truehealing");

    private ClientModEvents() {}

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent e) {
        e.register(OPEN_KEY);
    }

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent e) {
        e.registerAboveAll("moodles", (gui, g, partialTick, w, h) -> MoodleHud.render(g, w, h));
    }

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers e) {
        for (String skin : new String[]{"default", "slim"}) {
            PlayerRenderer pr = e.getSkin(skin);
            if (pr != null) pr.addLayer(new WoundLayer(pr, "slim".equals(skin)));
        }
    }
}
