package com.ashenenderdragon.item;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.EffectCures;

/** An enchanted golden apple that cleanses existing milk-curable effects before applying its food effects. */
public final class OldGoldenAppleItem extends Item {
    public OldGoldenAppleItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
        if (!level.isClientSide) {
            // Cleanse first so the enchanted golden apple's own positive effects are applied afterwards.
            livingEntity.removeEffectsCuredBy(EffectCures.MILK);
        }
        return super.finishUsingItem(stack, level, livingEntity);
    }
}
