package com.baroo.truehealing.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.baroo.truehealing.BodyPart;
import com.baroo.truehealing.ClientData;
import com.baroo.truehealing.DressingType;
import com.baroo.truehealing.InjuryData;
import com.baroo.truehealing.TrueHealing;
import com.baroo.truehealing.TrueHealingClientConfig;
import com.baroo.truehealing.Wound;
import com.baroo.truehealing.WoundType;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;

/** Small Project Zomboid style status icons: on the HUD, beside the inventory, and in the medical screen. */
public final class MoodleHud {
    private static final ResourceLocation TEX = new ResourceLocation(TrueHealing.MODID, "textures/gui/moodles.png");

    // icon columns in the second row of the atlas
    private static final int ICON_FOOD = 0, ICON_PANIC = 1, ICON_BLEED = 2, ICON_INJURED = 3, ICON_PAIN = 4, ICON_LEG = 5;

    private static final int BASE_SIZE = 24, BASE_GAP = 3, PAD = 6;
    public static final int PANEL_W = BASE_SIZE + PAD * 2;

    public record Moodle(int bg, int icon, String title, List<String> lines) {}

    public record Placed(Moodle m, int x, int y, int size) {}

    public record Panel(int x, int y, int w, int h, List<Placed> items) {}

    private MoodleHud() {}

    /** Atlas background index: 0..3 = green levels 1..4, 4..7 = red levels 1..4. */
    private static int green(int level) { return level - 1; }

    private static int red(int level) { return 3 + level; }

    // ---------------- what to show ----------------

    public static List<Moodle> current(Minecraft mc) {
        List<Moodle> out = new ArrayList<>();
        Player p = mc.player;
        if (p == null) return out;
        InjuryData d = ClientData.data;

        // ---- nourishment: hunger (red) or saturation (green), like AppleSkin ----
        FoodData fd = p.getFoodData();
        int food = fd.getFoodLevel();
        float sat = fd.getSaturationLevel();
        String foodLine = "Food " + food + "/20, saturation " + String.format("%.1f", sat);
        if (food <= 14) {
            int lv = food <= 2 ? 4 : food <= 6 ? 3 : food <= 10 ? 2 : 1;
            out.add(new Moodle(red(lv), ICON_FOOD,
                    new String[]{"Slightly hungry", "Hungry", "Very hungry", "Starving"}[lv - 1],
                    List.of(foodLine, "Eat something.")));
        } else {
            int lv = sat >= 19.5f ? 4 : sat >= 15f ? 3 : sat >= 10f ? 2 : sat >= 5f ? 1 : 0;
            if (lv > 0) {
                out.add(new Moodle(green(lv), ICON_FOOD,
                        new String[]{"Satisfied", "Well fed", "Very well fed", "Full to bursting"}[lv - 1],
                        List.of(foodLine)));
            }
        }

        // ---- panic: hostile mobs hunting you (sent by the server) ----
        int pan = ClientData.panicLevel;
        if (pan > 0) {
            int lv = Math.min(4, pan);
            out.add(new Moodle(red(lv), ICON_PANIC,
                    new String[]{"Slightly panicked", "Panicked", "Very panicked", "Extremely panicked"}[lv - 1],
                    List.of("Hostile mobs are hunting you.")));
        }

        // ---- bleeding / injured ----
        double rate = 0;
        int bleeding = 0;
        double score = 0;
        int wounds = 0;
        for (BodyPart bp : BodyPart.values()) {
            for (Wound w : d.get(bp)) {
                double r = w.type.bleedPer10s * w.bleedFactor();
                if (r > 0) { rate += r; bleeding++; }
                double base = w.type == WoundType.SCRATCH ? 1 : w.type == WoundType.LACERATION ? 2 : 3;
                boolean treated = w.dressing != DressingType.NONE || w.stitched || w.splinted;
                score += treated ? base * 0.5 : base;
                wounds++;
            }
        }
        if (bleeding > 0) {
            int lv = rate < 1.5 ? 1 : rate < 3 ? 2 : rate < 5 ? 3 : 4;
            out.add(new Moodle(red(lv), ICON_BLEED,
                    new String[]{"Minor bleeding", "Bleeding", "Heavy bleeding", "Severe bleeding"}[lv - 1],
                    List.of(bleeding + (bleeding == 1 ? " wound is" : " wounds are") + " bleeding.", "Apply a bandage or stitches.")));
        }
        if (wounds > 0) {
            int lv = score < 2.5 ? 1 : score < 5 ? 2 : score < 8 ? 3 : 4;
            out.add(new Moodle(red(lv), ICON_INJURED,
                    new String[]{"Slightly injured", "Injured", "Badly injured", "Critically injured"}[lv - 1],
                    List.of(wounds + (wounds == 1 ? " injury." : " injuries."))));
        }

        // ---- pain: follows your health ----
        float frac = p.getMaxHealth() <= 0 ? 1f : p.getHealth() / p.getMaxHealth();
        int pain = frac >= 0.98f ? 0 : frac >= 0.85f ? 1 : frac >= 0.55f ? 2 : frac >= 0.35f ? 3 : 4;
        if (pain > 0) {
            out.add(new Moodle(red(pain), ICON_PAIN,
                    new String[]{"Minor pain", "Mild pain", "Major pain", "Agony"}[pain - 1],
                    List.of("Health: " + Math.round(frac * 100) + "%")));
        }

        // ---- restricted movement: broken legs ----
        double slow = d.fractureSlow();
        if (slow > 0) {
            int lv = slow < 0.15 ? 1 : slow < 0.25 ? 2 : slow < 0.35 ? 3 : 4;
            out.add(new Moodle(red(lv), ICON_LEG, "Restricted movement",
                    List.of("Walking speed -" + Math.round(slow * 100) + "%", "Splint your broken leg.")));
        }
        return out;
    }

    // ---------------- shake when a level changes (up or down) ----------------

    private static final Map<Integer, Integer> LAST_BG = new HashMap<>();
    private static final Map<Integer, Long> SHAKE_START = new HashMap<>();
    private static boolean primed = false;
    private static long primedAt = 0L;
    private static final long SHAKE_MS = 1400L;

    public static void reset() {
        LAST_BG.clear();
        SHAKE_START.clear();
        primed = false;
    }

    /** Called whenever moodles are about to be drawn; starts a shake for new or changed moodles. */
    private static void track(List<Moodle> list) {
        long now = System.currentTimeMillis();
        Map<Integer, Integer> cur = new HashMap<>();
        for (Moodle m : list) cur.put(m.icon(), m.bg());
        if (primed && now - primedAt > 2500L) {
            for (Map.Entry<Integer, Integer> e : cur.entrySet()) {
                Integer old = LAST_BG.get(e.getKey());
                if (old == null || !old.equals(e.getValue())) SHAKE_START.put(e.getKey(), now);
            }
        }
        if (!primed) {
            primed = true;
            primedAt = now;
        }
        LAST_BG.clear();
        LAST_BG.putAll(cur);
    }

    private static int shakeX(int icon, float scale) {
        Long start = SHAKE_START.get(icon);
        if (start == null) return 0;
        long el = System.currentTimeMillis() - start;
        if (el >= SHAKE_MS) {
            SHAKE_START.remove(icon);
            return 0;
        }
        double t = el / (double) SHAKE_MS;
        return (int) Math.round(Math.sin(el / 1000.0 * Math.PI * 2 * 7) * 3.0 * scale * (1.0 - t));
    }

    private static void drawIcons(GuiGraphics g, List<Placed> list) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (Placed pl : list) {
            int x = pl.x() + shakeX(pl.m().icon(), pl.size() / (float) BASE_SIZE);
            g.blit(TEX, x, pl.y(), pl.size(), pl.size(), pl.m().bg() * 32f, 0f, 32, 32, 256, 64);
            g.blit(TEX, x, pl.y(), pl.size(), pl.size(), pl.m().icon() * 32f, 32f, 32, 32, 256, 64);
        }
    }

    // ---------------- HUD (right side of the screen) ----------------

    public static List<Placed> hudLayout(Minecraft mc, int screenW) {
        List<Placed> out = new ArrayList<>();
        float scale = TrueHealingClientConfig.MOODLE_SCALE.get().floatValue();
        int size = Math.round(BASE_SIZE * scale);
        int gap = Math.max(2, Math.round(BASE_GAP * scale));
        int x = screenW - size - TrueHealingClientConfig.MOODLE_OFFSET_X.get();
        int y = TrueHealingClientConfig.MOODLE_OFFSET_Y.get();
        for (Moodle m : current(mc)) {
            out.add(new Placed(m, x, y, size));
            y += size + gap;
        }
        return out;
    }

    public static void render(GuiGraphics g, int screenW, int screenH) {
        if (!TrueHealingClientConfig.MOODLES_ENABLED.get()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        // the inventory and the medical screen draw their own moodle panels
        if (mc.screen instanceof InventoryScreen || mc.screen instanceof MedicalScreen) return;
        List<Placed> list = hudLayout(mc, screenW);
        track(list.stream().map(Placed::m).toList());
        drawIcons(g, list);
    }

    /** Tooltip for the HUD moodles while some other screen (chat, pause, ...) is open. */
    public static List<Component> hudTooltipAt(Minecraft mc, int screenW, double mx, double my) {
        if (!TrueHealingClientConfig.MOODLES_ENABLED.get() || mc.player == null) return null;
        return tipIn(hudLayout(mc, screenW), mx, my);
    }

    // ---------------- panels (blue column behind the icons) ----------------

    public static Panel panel(Minecraft mc, int x, int y) {
        if (!TrueHealingClientConfig.MOODLES_ENABLED.get() || mc.player == null) return null;
        List<Moodle> moodles = current(mc);
        if (moodles.isEmpty()) return null;
        int n = moodles.size();
        int w = PANEL_W;
        int h = n * BASE_SIZE + (n - 1) * BASE_GAP + PAD * 2;
        List<Placed> items = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            items.add(new Placed(moodles.get(i), x + PAD, y + PAD + i * (BASE_SIZE + BASE_GAP), BASE_SIZE));
        }
        return new Panel(x, y, w, h, items);
    }

    /**
     * Beside the inventory, where vanilla shows potion effects. When effects are active the panel moves
     * out past them, so with no effects the moodles take the effects' spot.
     */
    public static Panel inventoryPanel(Minecraft mc, InventoryScreen s) {
        int x = s.getGuiLeft() + s.getXSize() + 2;
        int avail = s.width - x;
        if (mc.player != null && !mc.player.getActiveEffects().isEmpty() && avail >= 32) {
            x += (avail >= 120 ? 120 : 32) + 4;
        }
        return panel(mc, x, s.getGuiTop());
    }

    public static void drawPanel(GuiGraphics g, Panel p) {
        g.fill(p.x(), p.y(), p.x() + p.w(), p.y() + p.h(), 0xCC243A68);
        g.renderOutline(p.x(), p.y(), p.w(), p.h(), 0x80405A8C);
        track(p.items().stream().map(Placed::m).toList());
        drawIcons(g, p.items());
    }

    public static List<Component> tipFor(Panel p, double mx, double my) {
        return p == null ? null : tipIn(p.items(), mx, my);
    }

    private static List<Component> tipIn(List<Placed> list, double mx, double my) {
        for (Placed pl : list) {
            if (mx >= pl.x() && mx < pl.x() + pl.size() && my >= pl.y() && my < pl.y() + pl.size()) {
                List<Component> tip = new ArrayList<>();
                tip.add(Component.literal(pl.m().title()).withStyle(ChatFormatting.BOLD));
                for (String s : pl.m().lines()) tip.add(Component.literal(s).withStyle(ChatFormatting.GRAY));
                return tip;
            }
        }
        return null;
    }
}
