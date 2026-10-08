package com.baroo.truehealing;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Chance that worn armor deflects an INJURY (the hit itself still lands).
 * Helmet -> head, chestplate -> torso + both arms, leggings + boots -> both legs (they add up, and are
 * tuned so a full pair of legs protects about like a helmet does the head).
 */
public final class ArmorProtection {
    public enum Kind { GENERIC, PROJECTILE, EXPLOSION }

    private static final double K_HEAD = 0.10, K_CHEST = 0.07, K_LEGGINGS = 0.03, K_BOOTS = 0.03;
    private static final double GENERIC_PER_LEVEL = 0.02;   // Protection I..IV: +2% per level
    private static final double SPECIFIC_PER_LEVEL = 0.04;  // Projectile / Blast Protection: double that, one source only

    private ArmorProtection() {}

    private static double cap(double v) {
        return Mth.clamp(v, 0.0, TrueHealingConfig.ARMOR_MAX_DEFLECT.get());
    }

    private static double base(ItemStack s, double k, Kind kind) {
        if (s.isEmpty() || !(s.getItem() instanceof ArmorItem armor)) return 0.0;
        double v = armor.getDefense() * k;
        v += EnchantmentHelper.getItemEnchantmentLevel(Enchantments.ALL_DAMAGE_PROTECTION, s) * GENERIC_PER_LEVEL;
        if (kind == Kind.PROJECTILE) {
            v += EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PROJECTILE_PROTECTION, s) * SPECIFIC_PER_LEVEL;
        } else if (kind == Kind.EXPLOSION) {
            v += EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLAST_PROTECTION, s) * SPECIFIC_PER_LEVEL;
        }
        return v;
    }

    /** One armor piece on its own (what the medical screen shows next to the model). */
    public static double piece(Player p, EquipmentSlot slot, Kind kind) {
        if (!TrueHealingConfig.ARMOR_DEFLECT_ENABLED.get()) return 0.0;
        ItemStack s = p.getItemBySlot(slot);
        return switch (slot) {
            case HEAD -> cap(base(s, K_HEAD, kind));
            case CHEST -> cap(base(s, K_CHEST, kind));
            case LEGS -> cap(base(s, K_LEGGINGS, kind));
            case FEET -> cap(base(s, K_BOOTS, kind));
            default -> 0.0;
        };
    }

    public static double legs(Player p, Kind kind) {
        if (!TrueHealingConfig.ARMOR_DEFLECT_ENABLED.get()) return 0.0;
        return cap(base(p.getItemBySlot(EquipmentSlot.LEGS), K_LEGGINGS, kind)
                + base(p.getItemBySlot(EquipmentSlot.FEET), K_BOOTS, kind));
    }

    public static double forPart(Player p, BodyPart part, Kind kind) {
        return switch (part) {
            case HEAD -> piece(p, EquipmentSlot.HEAD, kind);
            case TORSO, LEFT_ARM, RIGHT_ARM -> piece(p, EquipmentSlot.CHEST, kind);
            case LEFT_LEG, RIGHT_LEG -> legs(p, kind);
        };
    }
}
