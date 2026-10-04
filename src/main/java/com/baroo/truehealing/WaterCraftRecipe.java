package com.baroo.truehealing;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * "Item + water bottle" recipes that use up the water but hand the empty glass bottle back
 * (like the cake recipe returns the milk bucket).
 */
public class WaterCraftRecipe extends CustomRecipe {
    public enum Kind { WASH_RAG, WASH_BANDAGE, WIPE }

    private final Kind kind;

    public WaterCraftRecipe(ResourceLocation id, CraftingBookCategory category, Kind kind) {
        super(id, category);
        this.kind = kind;
    }

    private Item input() {
        return switch (kind) {
            case WASH_RAG -> TrueHealing.DIRTY_RAG.get();
            case WASH_BANDAGE -> TrueHealing.DIRTY_BANDAGE.get();
            case WIPE -> Items.PAPER;
        };
    }

    private Item output() {
        return switch (kind) {
            case WASH_RAG -> TrueHealing.RAG.get();
            case WASH_BANDAGE -> TrueHealing.BANDAGE.get();
            case WIPE -> TrueHealing.WIPE.get();
        };
    }

    private static boolean isWater(ItemStack s) {
        return s.is(Items.POTION) && PotionUtils.getPotion(s) == Potions.WATER;
    }

    @Override
    public boolean matches(CraftingContainer c, Level level) {
        boolean water = false, main = false;
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (s.isEmpty()) continue;
            n++;
            if (isWater(s)) {
                if (water) return false;
                water = true;
            } else if (s.is(input())) {
                if (main) return false;
                main = true;
            } else {
                return false;
            }
        }
        return n == 2 && water && main;
    }

    @Override
    public ItemStack assemble(CraftingContainer c, RegistryAccess access) {
        return new ItemStack(output());
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer c) {
        NonNullList<ItemStack> out = NonNullList.withSize(c.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < c.getContainerSize(); i++) {
            if (isWater(c.getItem(i))) out.set(i, new ItemStack(Items.GLASS_BOTTLE));
        }
        return out;
    }

    @Override
    public boolean canCraftInDimensions(int w, int h) {
        return w * h >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return switch (kind) {
            case WASH_RAG -> TrueHealingRecipes.WASH_RAG.get();
            case WASH_BANDAGE -> TrueHealingRecipes.WASH_BANDAGE.get();
            case WIPE -> TrueHealingRecipes.WIPE.get();
        };
    }
}
