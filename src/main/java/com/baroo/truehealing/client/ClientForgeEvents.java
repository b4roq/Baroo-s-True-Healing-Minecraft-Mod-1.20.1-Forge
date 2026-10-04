package com.baroo.truehealing.client;

import java.util.List;

import com.baroo.truehealing.ClientData;
import com.baroo.truehealing.ClientVisuals;
import com.baroo.truehealing.InjuryData;
import com.baroo.truehealing.TrueHealing;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TrueHealing.MODID, value = Dist.CLIENT)
public final class ClientForgeEvents {
    private ClientForgeEvents() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        while (ClientModEvents.OPEN_KEY.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.screen == null) mc.setScreen(new MedicalScreen());
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        ClientVisuals.clear();
        ClientData.data = new InjuryData();
        ClientData.panicLevel = 0;
        MoodleHud.reset();
    }

    // ---- inventory: quick-access button + moodle panel ----

    private static int[] invButton(InventoryScreen s) {
        return new int[]{s.getGuiLeft() + 64, s.getGuiTop() + 10, 10, 10};
    }

    private static boolean over(int[] r, double mx, double my) {
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post e) {
        Minecraft mc = Minecraft.getInstance();
        GuiGraphics g = e.getGuiGraphics();
        int mx = e.getMouseX(), my = e.getMouseY();

        if (e.getScreen() instanceof InventoryScreen s) {
            int[] r = invButton(s);
            boolean hover = over(r, mx, my);
            g.fill(r[0], r[1], r[0] + 10, r[1] + 10, hover ? 0xFF3C6E8C : 0xFF2A4A60);
            g.renderOutline(r[0], r[1], 10, 10, hover ? 0xFFFFFFFF : 0xFF8FB4CC);
            g.fill(r[0] + 4, r[1] + 2, r[0] + 6, r[1] + 8, 0xFFFFFFFF);
            g.fill(r[0] + 2, r[1] + 4, r[0] + 8, r[1] + 6, 0xFFFFFFFF);
            if (hover) g.renderTooltip(mc.font, Component.literal("Medical"), mx, my);

            MoodleHud.Panel panel = MoodleHud.inventoryPanel(mc, s);
            if (panel != null) {
                MoodleHud.drawPanel(g, panel);
                List<Component> tip = MoodleHud.tipFor(panel, mx, my);
                if (tip != null) g.renderComponentTooltip(mc.font, tip, mx, my);
            }
        } else if (!(e.getScreen() instanceof MedicalScreen)) {
            // chat, pause menu, ...: the HUD moodles are still visible behind it
            List<Component> tip = MoodleHud.hudTooltipAt(mc, e.getScreen().width, mx, my);
            if (tip != null) g.renderComponentTooltip(mc.font, tip, mx, my);
        }
    }

    @SubscribeEvent
    public static void onScreenClick(ScreenEvent.MouseButtonPressed.Pre e) {
        if (e.getButton() != 0 || !(e.getScreen() instanceof InventoryScreen s)) return;
        if (over(invButton(s), e.getMouseX(), e.getMouseY())) {
            Minecraft.getInstance().setScreen(new MedicalScreen());
            e.setCanceled(true);
        }
    }
}
