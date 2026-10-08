package com.baroo.truehealing.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.joml.Quaternionf;

import com.baroo.truehealing.ArmorProtection;
import com.baroo.truehealing.BodyPart;
import com.baroo.truehealing.ClientData;
import com.baroo.truehealing.DressingType;
import com.baroo.truehealing.TreatAction;
import com.baroo.truehealing.TrueHealing;
import com.baroo.truehealing.TrueHealingNetwork;
import com.baroo.truehealing.Wound;
import com.baroo.truehealing.WoundType;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class MedicalScreen extends Screen {
    private static final TreatAction[] SUPPLY = {
            TreatAction.DISINFECT, TreatAction.RAG, TreatAction.BANDAID,
            TreatAction.STITCH, TreatAction.BANDAGE, TreatAction.SPLINT,
            TreatAction.DIRTY_RAG, TreatAction.DIRTY_BANDAGE};
    private static final String[] SUPPLY_LABEL = {"Alcohol Wipes", "Rag", "Bandaid", "Suture Needle", "Bandage", "Splint",
            "Rag (Dirty)", "Bandage (Dirty)"};

    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final String[] ARMOR_LABEL = {"Head", "Chest", "Legs", "Feet"}; 

    private static final int ENTRY_H = 18;
    private static final int STITCHED_GREEN = 0x2FA84F;
    private static final ResourceLocation ICONS = new ResourceLocation("textures/gui/icons.png");

    private record Status(String text, int color) {}

    private record HpLevel(String name, int color) {}

    private static final HpLevel HEALTHY = new HpLevel("Healthy", 0x55DD55);
    private static final HpLevel MINOR = new HpLevel("Minor injuries", 0xE6E070);
    private static final HpLevel WOUNDED = new HpLevel("Wounded", 0xFF9030);
    private static final HpLevel SEVERE = new HpLevel("Severely wounded", 0xFF4040);
    private static final HpLevel CRITICAL = new HpLevel("Critically injured", 0xCC2020);

    private static final class MenuEntry {
        final String label;
        final TreatAction action;
        final Item icon;
        final List<MenuEntry> children;
        final boolean enabled;

        MenuEntry(String label, TreatAction action, Item icon, List<MenuEntry> children, boolean enabled) {
            this.label = label; this.action = action; this.icon = icon; this.children = children; this.enabled = enabled;
        }
    }

    private BodyPart selected = BodyPart.TORSO;
    private int dragRow = -1;

    // context menu state
    private boolean menuOpen;
    private BodyPart menuPart;
    private int menuX, menuY, menuW;
    private List<MenuEntry> menu = new ArrayList<>();
    private int openSub = -1;

    // layout
    private int leftEnd, centerEnd, rx, rw, cx, feetY, scale, sepY, supplyTop, rowH, rowW;
    private final Map<BodyPart, int[]> listHit = new EnumMap<>(BodyPart.class);

    public MedicalScreen() {
        super(Component.literal("Medical"));
    }

    // ---------------- layout ----------------

    @Override
    protected void init() {
        leftEnd = (int) (width * 0.267);
        centerEnd = (int) (width * 0.656);
        rx = centerEnd + 12;
        rw = width - rx - 24;
        cx = (leftEnd + centerEnd) / 2;
        scale = Mth.clamp((int) (height * 0.315), 70, 220);
        feetY = (int) (height * 0.84);
        sepY = (int) (height * 0.30);
        supplyTop = sepY + 30;
        rowH = Mth.clamp((height - supplyTop - 50) / SUPPLY.length, 20, 28);
        rowW = Math.min(rw, 150);
    }

    @Override
    public void removed() {
        // closing the screen cancels a treatment that is still in progress
        if (ClientData.actionTotalMs > 0) {
            TrueHealingNetwork.CHANNEL.sendToServer(new TrueHealingNetwork.TreatPacket(0, -1));
            ClientData.setAction("", 0);
        }
        super.removed();
    }

    private int[] zone(BodyPart bp) {
        int s = scale;
        int headTop = feetY - (int) (1.875 * s), headBot = feetY - (int) (1.406 * s), torsoBot = feetY - (int) (0.703 * s);
        int hw = (int) (0.25 * s), tw = (int) (0.234 * s), aw = (int) (0.469 * s);
        return switch (bp) {
            case HEAD -> new int[]{cx - hw, headTop, cx + hw, headBot};
            case TORSO -> new int[]{cx - tw, headBot, cx + tw, torsoBot};
            case RIGHT_ARM -> new int[]{cx - aw, headBot, cx - tw, torsoBot};
            case LEFT_ARM -> new int[]{cx + tw, headBot, cx + aw, torsoBot};
            case RIGHT_LEG -> new int[]{cx - tw, torsoBot, cx, feetY};
            case LEFT_LEG -> new int[]{cx, torsoBot, cx + tw, feetY};
        };
    }

    private static boolean inside(int[] r, double mx, double my) {
        return mx >= r[0] && mx < r[2] && my >= r[1] && my < r[3];
    }

    private BodyPart partAt(double mx, double my) {
        for (BodyPart bp : BodyPart.values()) if (inside(zone(bp), mx, my)) return bp;
        return null;
    }

    private BodyPart listPartAt(double mx, double my) {
        for (Map.Entry<BodyPart, int[]> en : listHit.entrySet()) if (inside(en.getValue(), mx, my)) return en.getKey();
        return null;
    }

    private int rowAt(double mx, double my) {
        if (mx < rx || mx >= rx + rowW) return -1;
        int i = (int) ((my - supplyTop) / rowH);
        if (my < supplyTop || i < 0 || i >= SUPPLY.length) return -1;
        return i;
    }

    /** "Remove bandage" style button under the selected part's details (only when it has a dressing). */
    private int[] removeRect() {
        return new int[]{rx, sepY - 34, rx + Math.min(rw, 150), sepY - 12};
    }

    private String removeLabel() {
        boolean aid = false, rag = false, band = false;
        for (Wound w : ClientData.data.get(selected)) {
            switch (w.dressing) {
                case BANDAID -> aid = true;
                case RAG -> rag = true;
                case BANDAGE -> band = true;
                default -> { }
            }
        }
        int kinds = (aid ? 1 : 0) + (rag ? 1 : 0) + (band ? 1 : 0);
        if (kinds != 1) return "Remove dressings";
        return aid ? "Remove bandaid" : rag ? "Remove rag" : "Remove bandage";
    }

    // ---------------- items / rules ----------------

    private static Item itemFor(TreatAction a) {
        return switch (a) {
            case DISINFECT -> TrueHealing.WIPE.get();
            case STITCH -> TrueHealing.SUTURE_KIT.get();
            case RAG -> TrueHealing.RAG.get();
            case BANDAGE -> TrueHealing.BANDAGE.get();
            case BANDAID -> TrueHealing.BANDAID.get();
            case SPLINT -> TrueHealing.SPLINT.get();
            case DIRTY_RAG -> TrueHealing.DIRTY_RAG.get();
            case DIRTY_BANDAGE -> TrueHealing.DIRTY_BANDAGE.get();
            case REMOVE, REMOVE_SPLINT -> null;
        };
    }

    private int count(Item it) {
        if (it == null || minecraft == null || minecraft.player == null) return 0;
        return minecraft.player.getInventory().countItem(it);
    }

    private int countOf(TreatAction a) { return count(itemFor(a)); }

    private boolean canDo(TreatAction a, BodyPart part) {
        List<Wound> ws = ClientData.data.get(part);
        if (ws.isEmpty()) return false;
        if (itemFor(a) != null && countOf(a) <= 0) return false;
        return switch (a) {
            case DISINFECT -> ws.stream().anyMatch(w ->
                    w.type != WoundType.FRACTURE && w.infection > 0f && w.dressing == DressingType.NONE);
            case STITCH -> ws.stream().anyMatch(w -> !w.stitched && w.type != WoundType.SCRATCH
                    && w.type != WoundType.FRACTURE && w.dressing == DressingType.NONE);
            case RAG -> ws.stream().anyMatch(w -> w.dressing == DressingType.NONE && DressingType.RAG.canCover(w.type));
            case BANDAGE -> ws.stream().anyMatch(w -> w.dressing == DressingType.NONE && DressingType.BANDAGE.canCover(w.type));
            case BANDAID -> ws.stream().anyMatch(w -> w.dressing == DressingType.NONE && DressingType.BANDAID.canCover(w.type));
            case DIRTY_RAG -> ws.stream().anyMatch(w -> w.dressing == DressingType.NONE && DressingType.RAG.canCover(w.type));
            case DIRTY_BANDAGE -> ws.stream().anyMatch(w -> w.dressing == DressingType.NONE && DressingType.BANDAGE.canCover(w.type));
            case SPLINT -> ws.stream().anyMatch(w -> w.type == WoundType.FRACTURE && !w.splinted);
            case REMOVE -> ws.stream().anyMatch(w -> w.dressing != DressingType.NONE);
            case REMOVE_SPLINT -> ws.stream().anyMatch(w -> w.type == WoundType.FRACTURE && w.splinted);
        };
    }

    private void send(BodyPart part, TreatAction a) {
        TrueHealingNetwork.CHANNEL.sendToServer(new TrueHealingNetwork.TreatPacket(part.ordinal(), a.ordinal()));
    }

    // ---------------- health level ----------------

    private boolean hasWounds() {
        for (BodyPart bp : BodyPart.values()) if (!ClientData.data.get(bp).isEmpty()) return true;
        return false;
    }

    /** Same thresholds as the pain moodle: 98 / 85 / 55 / 35 percent. */
    private HpLevel levelFor(float frac) {
        if (frac >= 0.98f) return hasWounds() ? MINOR : HEALTHY;
        if (frac >= 0.85f) return MINOR;
        if (frac >= 0.55f) return WOUNDED;
        if (frac >= 0.35f) return SEVERE;
        return CRITICAL;
    }

    private float healthFrac() {
        if (minecraft == null || minecraft.player == null) return 1f;
        return Mth.clamp(minecraft.player.getHealth() / minecraft.player.getMaxHealth(), 0f, 1f);
    }

    // ---------------- wound text ----------------

    private static int typeColor(Wound w) {
        if (w.stitched && w.type != WoundType.FRACTURE) return STITCHED_GREEN;
        return switch (w.type) {
            case SCRATCH -> 0xE6E070;
            case LACERATION -> 0xFFA040;
            case DEEP_WOUND -> 0xFF5555;
            case FRACTURE -> 0xFFC060;
        };
    }

    private static String headerText(Wound w) {
        return (w.stitched && w.type != WoundType.FRACTURE) ? "Stitched" : w.type.label;
    }

    private static List<Status> statuses(Wound w) {
        List<Status> out = new ArrayList<>();
        if (w.type == WoundType.FRACTURE) {
            out.add(w.splinted ? new Status("Splinted", 0x55DD55) : new Status("Broken", 0xFF8844));
            return out;
        }
        if (w.isInfected()) out.add(new Status("Infected " + (int) w.infection + "%", 0x9BE04A));
        if (w.isBleeding()) out.add(new Status(w.dressing != DressingType.NONE ? "Seeping" : "Bleeding", 0xFF5555));
        else if (w.dressing != DressingType.NONE && w.seeps()) out.add(new Status("Seeping", 0xD98B6A));
        if (w.dressing != DressingType.NONE) {
            String n = w.dressing.label.toLowerCase();
            out.add(w.isDressingDirty() ? new Status("Dirty " + n, 0xFFAA33) : new Status("Bandaged (" + n + ")", 0x55DD55));
        }
        if (!w.stitched && w.type.requiresStitches) out.add(new Status("Needs stitches", 0xFFAA33));
        return out;
    }

    private List<Component> describe(Wound w) {
        List<Component> out = new ArrayList<>();
        boolean sut = w.stitched && w.type != WoundType.FRACTURE;
        out.add(Component.literal(headerText(w)).withStyle(sut ? ChatFormatting.DARK_GREEN : ChatFormatting.WHITE));
        if (w.type == WoundType.FRACTURE) {
            if (w.splinted) {
                int secs = Math.max(0, (w.type.healTicks - w.healProgress) / 20);
                out.add(Component.literal(String.format("  Splinted (%d:%02d to heal)", secs / 60, secs % 60))
                        .withStyle(ChatFormatting.GREEN));
            } else {
                out.add(Component.literal("  Broken - needs a splint").withStyle(ChatFormatting.GOLD));
                out.add(Component.literal("  Slows you down").withStyle(ChatFormatting.RED));
            }
            return out;
        }
        if (w.isInfected()) {
            out.add(Component.literal(String.format("  Infected %d%%", (int) w.infection)).withStyle(ChatFormatting.GREEN));
        }
        if (w.isBleeding()) {
            out.add(Component.literal(w.dressing != DressingType.NONE ? "  Seeping" : "  Bleeding").withStyle(ChatFormatting.RED));
        } else if (w.dressing != DressingType.NONE && w.seeps()) {
            out.add(Component.literal("  Seeping into the dressing").withStyle(ChatFormatting.GOLD));
        }
        if (!w.stitched && w.type.requiresStitches) out.add(Component.literal("  Needs stitches").withStyle(ChatFormatting.GOLD));
        if (w.dressing != DressingType.NONE) {
            if (w.dressing == DressingType.BANDAID) {
                out.add(Component.literal("  Bandaid (stays on until removed)").withStyle(ChatFormatting.GREEN));
            } else if (w.isDressingDirty()) {
                out.add(Component.literal("  " + w.dressing.label + " (dirty - replace it)").withStyle(ChatFormatting.GOLD));
            } else {
                int secs = Math.max(0, (w.dirtyAfter() - w.dressingAge) / 20);
                out.add(Component.literal(String.format("  %s (%d:%02d until dirty)", w.dressing.label, secs / 60, secs % 60))
                        .withStyle(ChatFormatting.GREEN));
            }
        }
        return out;
    }

    /** White for healthy limbs, orange when injured, red when it has a deep wound. */
    private int hoverTint(BodyPart bp) {
        List<Wound> ws = ClientData.data.get(bp);
        if (ws.isEmpty()) return 0x40FFFFFF;
        for (Wound w : ws) if (w.type == WoundType.DEEP_WOUND) return 0x55FF3030;
        return 0x55FFA030;
    }

    private int hoverOutline(BodyPart bp) {
        List<Wound> ws = ClientData.data.get(bp);
        if (ws.isEmpty()) return 0xAAFFFFFF;
        for (Wound w : ws) if (w.type == WoundType.DEEP_WOUND) return 0xDDFF5050;
        return 0xDDFFB040;
    }

    // ---------------- context menu ----------------

    private void openMenu(BodyPart part, int mx, int my) {
        selected = part;
        menuPart = part;
        menu = buildMenu(part);
        openSub = -1;

        int textW = 0;
        for (MenuEntry e : menu) textW = Math.max(textW, font.width(e.label) + (e.children != null ? 14 : 0));
        menuW = textW + 16;
        int h = menu.size() * ENTRY_H + 4;
        menuX = Math.min(mx, width - menuW - 4);
        menuY = Math.min(my, height - h - 4);
        menuOpen = true;
    }

    private void closeMenu() {
        menuOpen = false;
        openSub = -1;
    }

    private List<MenuEntry> buildMenu(BodyPart part) {
        List<MenuEntry> out = new ArrayList<>();
        List<MenuEntry> bandage = new ArrayList<>();
        if (canDo(TreatAction.RAG, part)) bandage.add(new MenuEntry("Rag", TreatAction.RAG, TrueHealing.RAG.get(), null, true));
        if (canDo(TreatAction.BANDAGE, part)) bandage.add(new MenuEntry("Bandage", TreatAction.BANDAGE, TrueHealing.BANDAGE.get(), null, true));
        if (canDo(TreatAction.BANDAID, part)) bandage.add(new MenuEntry("Bandaid", TreatAction.BANDAID, TrueHealing.BANDAID.get(), null, true));
        if (canDo(TreatAction.DIRTY_RAG, part)) bandage.add(new MenuEntry("Rag (Dirty)", TreatAction.DIRTY_RAG, TrueHealing.DIRTY_RAG.get(), null, true));
        if (canDo(TreatAction.DIRTY_BANDAGE, part)) bandage.add(new MenuEntry("Bandage (Dirty)", TreatAction.DIRTY_BANDAGE, TrueHealing.DIRTY_BANDAGE.get(), null, true));
        if (!bandage.isEmpty()) out.add(new MenuEntry("Bandage", null, null, bandage, true));
        if (canDo(TreatAction.DISINFECT, part)) out.add(new MenuEntry("Disinfect", TreatAction.DISINFECT, null, null, true));
        if (canDo(TreatAction.STITCH, part)) out.add(new MenuEntry("Stitch", TreatAction.STITCH, null, null, true));
        if (canDo(TreatAction.SPLINT, part)) out.add(new MenuEntry("Splint", TreatAction.SPLINT, null, null, true));
        if (canDo(TreatAction.REMOVE, part)) out.add(new MenuEntry("Remove bandage", TreatAction.REMOVE, null, null, true));
        if (canDo(TreatAction.REMOVE_SPLINT, part)) out.add(new MenuEntry("Remove splint", TreatAction.REMOVE_SPLINT, null, null, true));
        if (out.isEmpty()) out.add(new MenuEntry("No treatment available", null, null, null, false));
        return out;
    }

    private int subWidth(List<MenuEntry> kids) {
        int w = 0;
        for (MenuEntry e : kids) w = Math.max(w, font.width(e.label));
        return w + 8 + 20 + 8;
    }

    private int[] subRect() {
        List<MenuEntry> kids = menu.get(openSub).children;
        int sw = subWidth(kids);
        int sh = kids.size() * ENTRY_H + 4;
        int sx = menuX + menuW;
        if (sx + sw > width - 4) sx = menuX - sw;
        int sy = Math.min(menuY + 2 + openSub * ENTRY_H, height - sh - 4);
        return new int[]{sx, sy, sx + sw, sy + sh};
    }

    private void updateSub(int mx, int my) {
        int h = menu.size() * ENTRY_H + 4;
        boolean inMain = mx >= menuX && mx < menuX + menuW && my >= menuY && my < menuY + h;
        if (inMain) {
            int idx = (my - menuY - 2) / ENTRY_H;
            if (idx >= 0 && idx < menu.size() && menu.get(idx).children != null) openSub = idx;
            else openSub = -1;
        }
    }

    private void drawMenu(GuiGraphics g, int mx, int my) {
        if (!menuOpen) return;
        updateSub(mx, my);
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);

        int h = menu.size() * ENTRY_H + 4;
        g.fill(menuX, menuY, menuX + menuW, menuY + h, 0xF0161B20);
        g.renderOutline(menuX, menuY, menuW, h, 0xFF4A525A);
        for (int i = 0; i < menu.size(); i++) {
            MenuEntry e = menu.get(i);
            int ey = menuY + 2 + i * ENTRY_H;
            boolean hov = e.enabled && mx >= menuX && mx < menuX + menuW && my >= ey && my < ey + ENTRY_H;
            if (hov || i == openSub) g.fill(menuX + 1, ey, menuX + menuW - 1, ey + ENTRY_H, 0x40FFFFFF);
            g.drawString(font, e.label, menuX + 8, ey + (ENTRY_H - 8) / 2, e.enabled ? 0xFFFFFF : 0x808890, false);
            if (e.children != null) g.drawString(font, ">", menuX + menuW - 12, ey + (ENTRY_H - 8) / 2, 0xC0C8D0, false);
        }

        if (openSub >= 0) {
            List<MenuEntry> kids = menu.get(openSub).children;
            int[] r = subRect();
            g.fill(r[0], r[1], r[2], r[3], 0xF0161B20);
            g.renderOutline(r[0], r[1], r[2] - r[0], r[3] - r[1], 0xFF4A525A);
            for (int i = 0; i < kids.size(); i++) {
                MenuEntry e = kids.get(i);
                int ey = r[1] + 2 + i * ENTRY_H;
                boolean hov = mx >= r[0] && mx < r[2] && my >= ey && my < ey + ENTRY_H;
                if (hov) g.fill(r[0] + 1, ey, r[2] - 1, ey + ENTRY_H, 0x40FFFFFF);
                if (e.icon != null) g.renderItem(new ItemStack(e.icon), r[0] + 4, ey + 1);
                g.drawString(font, e.label, r[0] + 26, ey + (ENTRY_H - 8) / 2, 0xFFFFFF, false);
            }
        }
        g.pose().popPose();
    }

    /** Returns true if the click was consumed by the menu. */
    private boolean menuClick(double mx, double my) {
        if (openSub >= 0) {
            int[] r = subRect();
            if (inside(r, mx, my)) {
                int idx = (int) ((my - r[1] - 2) / ENTRY_H);
                List<MenuEntry> kids = menu.get(openSub).children;
                if (idx >= 0 && idx < kids.size() && kids.get(idx).action != null) {
                    send(menuPart, kids.get(idx).action);
                    closeMenu();
                }
                return true;
            }
        }
        int h = menu.size() * ENTRY_H + 4;
        if (mx >= menuX && mx < menuX + menuW && my >= menuY && my < menuY + h) {
            int idx = (int) ((my - menuY - 2) / ENTRY_H);
            if (idx >= 0 && idx < menu.size()) {
                MenuEntry e = menu.get(idx);
                if (e.children != null) {
                    openSub = idx;
                } else if (e.enabled && e.action != null) {
                    send(menuPart, e.action);
                    closeMenu();
                }
            }
            return true;
        }
        closeMenu();
        return false;
    }

    // ---------------- rendering ----------------

    /** Renders the player with the body fixed facing us and only the head following the mouse. */
    private void renderModel(GuiGraphics g, int mx, int my) {
        if (minecraft == null || minecraft.player == null) return;
        LivingEntity e = minecraft.player;
        float f = (float) Math.atan((cx - mx) / 40.0F);
        float f1 = (float) Math.atan(((feetY - scale * 1.65F) - my) / 40.0F);

        float bodyRot = e.yBodyRot, bodyRotO = e.yBodyRotO;
        float yRot = e.getYRot(), yRotO = e.yRotO;
        float xRot = e.getXRot(), xRotO = e.xRotO;
        float headRot = e.yHeadRot, headRotO = e.yHeadRotO;

        e.yBodyRot = 180.0F;
        e.yBodyRotO = 180.0F;
        e.setYRot(180.0F + f * 40.0F);
        e.yRotO = e.getYRot();
        e.setXRot(-f1 * 20.0F);
        e.xRotO = e.getXRot();
        e.yHeadRot = e.getYRot();
        e.yHeadRotO = e.getYRot();

        InventoryScreen.renderEntityInInventory(g, cx, feetY, scale,
                new Quaternionf().rotateZ((float) Math.PI), new Quaternionf(), e);

        e.yBodyRot = bodyRot;
        e.yBodyRotO = bodyRotO;
        e.setYRot(yRot);
        e.yRotO = yRotO;
        e.setXRot(xRot);
        e.xRotO = xRotO;
        e.yHeadRot = headRot;
        e.yHeadRotO = headRotO;
    }

    /** The vanilla hardcore heart (empty container + full heart), scaled up. */
    private void drawHeart(GuiGraphics g, int x, int y) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(1.5f, 1.5f, 1f);
        g.blit(ICONS, 0, 0, 16, 45, 9, 9);
        g.blit(ICONS, 0, 0, 52, 45, 9, 9);
        g.pose().popPose();
    }

    private void drawHealth(GuiGraphics g) {
        int x0 = leftEnd + 12, x1 = centerEnd - 12, y0 = 38, y1 = 48, w = x1 - x0;
        float frac = healthFrac();
        g.fill(x0, y0, x1, y1, 0xA0000000);
        g.renderOutline(x0, y0, w, y1 - y0, 0x60FFFFFF);
        int fillW = (int) ((w - 2) * frac);
        g.fill(x0 + 1, y0 + 1, x0 + 1 + fillW, y1 - 1, 0xFFB03030);
        g.fill(x0 + 1, y0 + 1, x0 + 1 + fillW, y0 + 2, 0x66FFFFFF);

        g.drawString(font, "Health: " + Math.round(frac * 100) + "%", x0, 54, 0xE0E0E0, false);
        float hp = minecraft != null && minecraft.player != null ? minecraft.player.getHealth() : 0f;
        float max = minecraft != null && minecraft.player != null ? minecraft.player.getMaxHealth() : 20f;
        String nums = (int) Math.ceil(hp) + "/" + (int) max;
        g.drawString(font, nums, x1 - 20 - font.width(nums), 54, 0xE0E0E0, false);
        drawHeart(g, x1 - 14, 51);
    }

    private void drawOutlined(GuiGraphics g, String s, int x, int y) {
        g.drawString(font, s, x - 1, y, 0xFF000000, false);
        g.drawString(font, s, x + 1, y, 0xFF000000, false);
        g.drawString(font, s, x, y - 1, 0xFF000000, false);
        g.drawString(font, s, x, y + 1, 0xFF000000, false);
        g.drawString(font, s, x, y, 0xFFFFFFFF, false);
    }

    /** White progress bar with a countdown in the middle, shown while a treatment is running. */
    private void drawAction(GuiGraphics g) {
        if (ClientData.actionTotalMs <= 0) return;
        long elapsed = System.currentTimeMillis() - ClientData.actionStartMs;
        if (elapsed > ClientData.actionTotalMs + 500) return;
        float frac = Mth.clamp((float) elapsed / ClientData.actionTotalMs, 0f, 1f);
        double remaining = Math.max(0.0, (ClientData.actionTotalMs - elapsed) / 1000.0);

        int bw = 90, bh = 16;
        int bx = centerEnd - 12 - bw, by = (int) (height * 0.58);
        g.pose().pushPose();
        g.pose().translate(0, 0, 350);
        g.drawCenteredString(font, ClientData.actionLabel, bx + bw / 2, by - 12, 0xFFFFFF);
        g.fill(bx, by, bx + bw, by + bh, 0xFF000000);
        g.renderOutline(bx, by, bw, bh, 0xFF606870);
        g.fill(bx + 1, by + 1, bx + 1 + (int) ((bw - 2) * frac), by + bh - 1, 0xFFE8E8E8);
        String t = String.format("%.1fs", remaining);
        drawOutlined(g, t, bx + (bw - font.width(t)) / 2, by + (bh - 8) / 2);
        g.pose().popPose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xC80C1014);
        g.drawString(font, "MEDICAL", 24, 14, 0xE8E8E8, false);
        String hint = "H / Esc: close";
        g.drawString(font, hint, width - 24 - font.width(hint), 14, 0x6C747C, false);
        g.fill(24, 28, width - 24, 29, 0x30FFFFFF);
        g.fill(leftEnd, 30, leftEnd + 1, height - 12, 0x30FFFFFF);
        g.fill(centerEnd, 30, centerEnd + 1, height - 12, 0x30FFFFFF);
        g.fill(leftEnd + 1, 76, centerEnd, 77, 0x30FFFFFF);
        g.fill(centerEnd + 1, sepY, width - 24, sepY + 1, 0x30FFFFFF);
        g.drawString(font, "Right-click a limb for treatments", leftEnd + 12, 84, 0x6C747C, false);

        drawHealth(g);
        drawInjuryList(g, mx, my);
        renderModel(g, mx, my);
        List<Component> armorTip = drawArmorColumn(g, mx, my);

        BodyPart hovered = menuOpen ? null : partAt(mx, my);

        // limb highlights, drawn in front of the model
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        for (BodyPart bp : BodyPart.values()) {
            int[] r = zone(bp);
            if (bp == selected) {
                g.fill(r[0], r[1], r[2], r[3], 0x22FFFFFF);
                g.renderOutline(r[0], r[1], r[2] - r[0], r[3] - r[1], 0xCCFFFFFF);
            }
            if (bp == hovered) {
                int tint = hoverTint(bp), outline = hoverOutline(bp);
                if (dragRow >= 0) {
                    tint = canDo(SUPPLY[dragRow], bp) ? 0x5055FF55 : 0x50FF5555;
                    outline = 0xAAFFFFFF;
                }
                g.fill(r[0], r[1], r[2], r[3], tint);
                g.renderOutline(r[0], r[1], r[2] - r[0], r[3] - r[1], outline);
            }
        }
        g.pose().popPose();

        drawRightColumn(g, mx, my);
        drawAction(g);

        MoodleHud.Panel moodles = MoodleHud.panel(minecraft, centerEnd - 14 - MoodleHud.PANEL_W, 88);
        if (moodles != null) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 260);
            MoodleHud.drawPanel(g, moodles);
            g.pose().popPose();
        }

        if (System.currentTimeMillis() < ClientData.messageUntil && !ClientData.message.isEmpty()) {
            g.drawCenteredString(font, ClientData.message, cx, height - 16, 0xFFE6B0);
        }

        if (dragRow >= 0) {
            Item it = itemFor(SUPPLY[dragRow]);
            if (it != null) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 300);
                g.renderItem(new ItemStack(it), mx - 8, my - 8);
                g.pose().popPose();
            }
        } else if (armorTip != null && !menuOpen) {
            g.renderComponentTooltip(font, armorTip, mx, my);
        } else if (moodles != null && !menuOpen && MoodleHud.tipFor(moodles, mx, my) != null) {
            g.renderComponentTooltip(font, MoodleHud.tipFor(moodles, mx, my), mx, my);
        } else if (hovered != null) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal(hovered.label).withStyle(ChatFormatting.BOLD));
            List<Wound> hw = ClientData.data.get(hovered);
            if (hw.isEmpty()) tip.add(Component.literal("Healthy").withStyle(ChatFormatting.GRAY));
            else for (Wound w : hw) tip.addAll(describe(w));
            addProtectionLines(tip, hovered);
            g.renderComponentTooltip(font, tip, mx, my);
        }

        drawMenu(g, mx, my);
    }

    // ---------------- armor ----------------

    private static String pct(double v) { return Math.round(v * 100) + "%"; }

    /** "Injury protection" lines for a body part that is covered by armor (the % is the chance to deflect an injury). */
    private void addProtectionLines(List<Component> tip, BodyPart part) {
        if (minecraft == null || minecraft.player == null) return;
        Player pl = minecraft.player;
        double all = ArmorProtection.forPart(pl, part, ArmorProtection.Kind.GENERIC);
        if (all <= 0) return;
        tip.add(Component.literal("Injury protection: " + pct(all)).withStyle(ChatFormatting.AQUA));
        double proj = ArmorProtection.forPart(pl, part, ArmorProtection.Kind.PROJECTILE);
        double blast = ArmorProtection.forPart(pl, part, ArmorProtection.Kind.EXPLOSION);
        if (Math.round(proj * 100) != Math.round(all * 100)) {
            tip.add(Component.literal("  vs projectiles: " + pct(proj)).withStyle(ChatFormatting.GRAY));
        }
        if (Math.round(blast * 100) != Math.round(all * 100)) {
            tip.add(Component.literal("  vs explosions: " + pct(blast)).withStyle(ChatFormatting.GRAY));
        }
    }

    /** The four worn armor pieces on the left of the player model, each with its chance to deflect an injury. */
    private List<Component> drawArmorColumn(GuiGraphics g, int mx, int my) {
        if (minecraft == null || minecraft.player == null) return null;
        Player pl = minecraft.player;
        int x = leftEnd + 18;
        int y0 = Math.max(100, (int) (height * 0.2));
        List<Component> tip = null;
        for (int i = 0; i < ARMOR_SLOTS.length; i++) {
            EquipmentSlot slot = ARMOR_SLOTS[i];
            ItemStack stack = pl.getItemBySlot(slot);
            double v = ArmorProtection.piece(pl, slot, ArmorProtection.Kind.GENERIC);
            int y = y0 + i * 34;
            if (!stack.isEmpty()) g.renderItem(stack, x, y);
            else g.fill(x + 5, y + 5, x + 11, y + 11, 0x30FFFFFF);
            g.drawString(font, pct(v), x + 22, y, v > 0 ? 0xBFE8FF : 0x6C747C, false);
            g.drawString(font, ARMOR_LABEL[i], x + 22, y + 10, 0x6C747C, false);
            if (mx >= x && mx < x + 72 && my >= y - 2 && my < y + 20) {
                tip = new ArrayList<>();
                tip.add(stack.isEmpty() ? Component.literal(ARMOR_LABEL[i] + ": nothing worn").withStyle(ChatFormatting.GRAY)
                        : stack.getHoverName());
                tip.add(Component.literal("Deflects " + pct(v) + " of injuries").withStyle(ChatFormatting.AQUA));
                switch (slot) {
                    case HEAD -> tip.add(Component.literal("Protects the head.").withStyle(ChatFormatting.GRAY));
                    case CHEST -> tip.add(Component.literal("Protects the torso and both arms.").withStyle(ChatFormatting.GRAY));
                    case LEGS, FEET -> tip.add(Component.literal("Both legs, with the "
                            + (slot == EquipmentSlot.LEGS ? "boots" : "leggings") + ": "
                            + pct(ArmorProtection.legs(pl, ArmorProtection.Kind.GENERIC))).withStyle(ChatFormatting.GRAY));
                    default -> { }
                }
            }
        }
        return tip;
    }

    private void drawInjuryList(GuiGraphics g, int mx, int my) {
        listHit.clear();
        int x = 24, y = 44;
        g.drawString(font, "Overall Body Status", x, y, 0xE8E8E8, false);
        y += 11;
        HpLevel lv = levelFor(healthFrac());
        g.drawString(font, lv.name(), x, y, lv.color(), false);
        y += 11;
        if (ClientData.data.antibioticTicks > 0) {
            int s = ClientData.data.antibioticTicks / 20;
            g.drawString(font, String.format("Antibiotics active (%d:%02d)", s / 60, s % 60), x, y, 0x66CCFF, false);
            y += 11;
        }
        y += 10;

        boolean any = false;
        for (BodyPart bp : BodyPart.values()) {
            List<Wound> ws = ClientData.data.get(bp);
            if (ws.isEmpty()) continue;
            any = true;
            int lines = 0;
            for (Wound w : ws) lines += 1 + statuses(w).size();
            int blockH = 11 + lines * 10;
            int top = y - 2;
            int[] hit = {x - 6, top, leftEnd - 8, top + blockH + 2};
            listHit.put(bp, hit);
            if (bp == selected) g.fill(hit[0], hit[1], hit[2], hit[3], 0x30FFFFFF);
            else if (inside(hit, mx, my)) g.fill(hit[0], hit[1], hit[2], hit[3], 0x18FFFFFF);

            g.drawString(font, bp.label, x, y, 0xFFFFFF, false);
            y += 11;
            for (Wound w : ws) {
                g.drawString(font, "- " + headerText(w), x + 6, y, typeColor(w), false);
                y += 10;
                for (Status st : statuses(w)) {
                    g.drawString(font, "- " + st.text(), x + 16, y, st.color(), false);
                    y += 10;
                }
            }
            y += 8;
            if (y > height - 30) break;
        }
        if (!any) g.drawString(font, "No injuries", x, y, 0x7FD67F, false);
    }

    private void drawRemoveButton(GuiGraphics g, int mx, int my) {
        if (!canDo(TreatAction.REMOVE, selected)) return;
        int[] r = removeRect();
        boolean hover = !menuOpen && inside(r, mx, my);
        g.fill(r[0], r[1], r[2], r[3], hover ? 0xB0384048 : 0x80202830);
        g.renderOutline(r[0], r[1], r[2] - r[0], r[3] - r[1], 0x80A0AAB4);
        g.drawString(font, removeLabel(), r[0] + 8, r[1] + (r[3] - r[1] - 8) / 2, 0xFFFFFF, false);
    }

    private void drawRightColumn(GuiGraphics g, int mx, int my) {
        int y = 44;
        g.drawString(font, "SELECTED PART", rx, y, 0x8A929A, false);
        g.drawString(font, selected.label.toUpperCase(), rx + font.width("SELECTED PART") + 6, y, 0xFFFFFF, false);
        y += 13;
        List<Wound> ws = ClientData.data.get(selected);
        if (ws.isEmpty()) {
            g.drawString(font, "No wounds", rx, y, 0x7FD67F, false);
        } else {
            outer:
            for (Wound w : ws) {
                for (Component line : describe(w)) {
                    if (y > sepY - 40) { g.drawString(font, "...", rx, y, 0x8A929A, false); break outer; }
                    g.drawString(font, line, rx, y, 0xFFFFFF, false);
                    y += 10;
                }
                y += 3;
            }
        }
        drawRemoveButton(g, mx, my);

        g.drawString(font, "MEDICAL SUPPLIES", rx, sepY + 10, 0x8A929A, false);
        g.drawString(font, "(drag onto a body part)", rx + font.width("MEDICAL SUPPLIES") + 6, sepY + 10, 0x6C747C, false);

        for (int i = 0; i < SUPPLY.length; i++) {
            int ry = supplyTop + i * rowH;
            int n = countOf(SUPPLY[i]);
            boolean hover = rowAt(mx, my) == i && !menuOpen;
            g.fill(rx, ry + 1, rx + rowW, ry + rowH - 1, hover && n > 0 ? 0xB0384048 : 0x80202830);
            g.renderOutline(rx, ry + 1, rowW, rowH - 2, n > 0 ? 0x60A0AAB4 : 0x30607080);
            Item it = itemFor(SUPPLY[i]);
            if (it != null) {
                int iy = ry + (rowH - 16) / 2;
                g.renderItem(new ItemStack(it), rx + 5, iy);
                if (n == 0) {
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 200);
                    g.fill(rx + 5, iy, rx + 21, iy + 16, 0x90000000);
                    g.pose().popPose();
                }
            }
            g.drawString(font, SUPPLY_LABEL[i] + " (" + n + ")", rx + 28, ry + (rowH - 8) / 2,
                    n > 0 ? 0xFFFFFF : 0x707880, false);
        }

        if (count(TrueHealing.DIRTY_RAG.get()) + count(TrueHealing.DIRTY_BANDAGE.get()) > 0) {
            int dy = supplyTop + SUPPLY.length * rowH + 6;
            g.drawString(font, "Wash dirty cloth with a water bottle or", rx, dy, 0x6C747C, false);
            g.drawString(font, "bucket in the crafting grid.", rx, dy + 10, 0x6C747C, false);
        }
    }

    // ---------------- input ----------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (menuOpen && menuClick(mx, my)) return true;

        if (button == 0) {
            if (canDo(TreatAction.REMOVE, selected) && inside(removeRect(), mx, my)) {
                send(selected, TreatAction.REMOVE);
                return true;
            }
            int row = rowAt(mx, my);
            if (row >= 0) {
                if (countOf(SUPPLY[row]) > 0) dragRow = row;
                return true;
            }
            BodyPart lp = listPartAt(mx, my);
            if (lp != null) { selected = lp; return true; }
        }

        BodyPart bp = partAt(mx, my);
        if (bp == null) bp = listPartAt(mx, my);
        if (bp != null) {
            selected = bp;
            if (button == 1) openMenu(bp, (int) mx, (int) my);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragRow >= 0 && button == 0) {
            TreatAction a = SUPPLY[dragRow];
            dragRow = -1;
            BodyPart bp = partAt(mx, my);
            if (bp != null) {
                selected = bp;
                send(bp, a); // the server validates, runs the timer, and replies with a message
            }
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256 && menuOpen) { // Esc closes the menu first
            closeMenu();
            return true;
        }
        if (ClientModEvents.OPEN_KEY.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
