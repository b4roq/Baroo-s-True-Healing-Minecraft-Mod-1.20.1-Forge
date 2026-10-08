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
 * Recipes that use up water but hand the empty container back (glass bottle or bucket):
 *  - dirty rag / dirty bandage + water  -> clean rag / bandage
 *  - 2 sugar + paper + water            -> 1 alcohol wipes
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

    private static boolean isWaterBottle(ItemStack s) {
        return s.is(Items.POTION) && PotionUtils.getPotion(s) == Potions.WATER;
    }

    private static boolean isWater(ItemStack s) {
        return isWaterBottle(s) || s.is(Items.WATER_BUCKET);
    }

    @Override
    public boolean matches(CraftingContainer c, Level level) {
        int n = 0, water = 0, main = 0, sugar = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (s.isEmpty()) continue;
            n++;
            if (isWater(s)) water++;
            else if (s.is(input())) main++;
            else if (kind == Kind.WIPE && s.is(Items.SUGAR)) sugar++;
            else return false;
        }
        if (kind == Kind.WIPE) return n == 4 && water == 1 && main == 1 && sugar == 2;
        return n == 2 && water == 1 && main == 1;
    }

    @Override
    public ItemStack assemble(CraftingContainer c, RegistryAccess access) {
        return new ItemStack(output());
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer c) {
        NonNullList<ItemStack> out = NonNullList.withSize(c.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (isWaterBottle(s)) out.set(i, new ItemStack(Items.GLASS_BOTTLE));
            else if (s.is(Items.WATER_BUCKET)) out.set(i, new ItemStack(Items.BUCKET));
        }
        return out;
    }

    @Override
    public boolean canCraftInDimensions(int w, int h) {
        return w * h >= (kind == Kind.WIPE ? 4 : 2);
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
