package com.baroo.truehealing;

import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class TrueHealingRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, TrueHealing.MODID);

    public static final RegistryObject<RecipeSerializer<WaterCraftRecipe>> WASH_RAG = SERIALIZERS.register("water_wash_rag",
            () -> new SimpleCraftingRecipeSerializer<>((id, cat) -> new WaterCraftRecipe(id, cat, WaterCraftRecipe.Kind.WASH_RAG)));
    public static final RegistryObject<RecipeSerializer<WaterCraftRecipe>> WASH_BANDAGE = SERIALIZERS.register("water_wash_bandage",
            () -> new SimpleCraftingRecipeSerializer<>((id, cat) -> new WaterCraftRecipe(id, cat, WaterCraftRecipe.Kind.WASH_BANDAGE)));
    public static final RegistryObject<RecipeSerializer<WaterCraftRecipe>> WIPE = SERIALIZERS.register("water_wipe",
            () -> new SimpleCraftingRecipeSerializer<>((id, cat) -> new WaterCraftRecipe(id, cat, WaterCraftRecipe.Kind.WIPE)));

    private TrueHealingRecipes() {}
}
