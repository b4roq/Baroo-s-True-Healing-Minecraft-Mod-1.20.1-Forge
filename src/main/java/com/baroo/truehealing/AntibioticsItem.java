package com.baroo.truehealing;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A pill: eaten like a golden apple (even when full), slows infections for a while. */
public class AntibioticsItem extends Item {
    public AntibioticsItem() {
        super(new Item.Properties().food(new FoodProperties.Builder()
                .nutrition(0).saturationMod(0.0f).alwaysEat().fast().build()));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer sp) {
            InjuryManager.takeAntibiotics(sp);
        }
        return super.finishUsingItem(stack, level, entity);
    }
}
