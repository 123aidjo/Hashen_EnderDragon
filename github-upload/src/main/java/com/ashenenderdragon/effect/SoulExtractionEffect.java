package com.ashenenderdragon.effect;

import com.ashenenderdragon.AshenEnderDragon;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Reduces maximum health by one health point per second per level, restoring it when removed. */
public final class SoulExtractionEffect extends MobEffect {
    private static final ResourceLocation MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(
            AshenEnderDragon.MODID, "soul_extraction_max_health"
    );
    private static final String LOST_HEALTH_TAG = AshenEnderDragon.MODID + ":soul_extraction_lost_health";
    private static final String PROGRESS_TICKS_TAG = AshenEnderDragon.MODID + ":soul_extraction_progress_ticks";

    public SoulExtractionEffect() {
        super(MobEffectCategory.HARMFUL, 0x54206E);
    }

    public static void tick(LivingEntity entity) {
        AttributeInstance maxHealth = entity.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }

        CompoundTag data = entity.getPersistentData();
        MobEffectInstance instance = entity.getEffect(AshenEnderDragon.SOUL_EXTRACTION);
        if (instance == null) {
            maxHealth.removeModifier(MODIFIER_ID);
            data.remove(LOST_HEALTH_TAG);
            data.remove(PROGRESS_TICKS_TAG);
            return;
        }

        int progressTicks = Math.max(0, data.getInt(PROGRESS_TICKS_TAG)) + 1;
        int elapsedSeconds = progressTicks / 20;
        data.putInt(PROGRESS_TICKS_TAG, progressTicks % 20);
        float lostHealth = Math.max(0.0F, data.getFloat(LOST_HEALTH_TAG));
        if (elapsedSeconds > 0) {
            lostHealth += elapsedSeconds * (instance.getAmplifier() + 1.0F);
            data.putFloat(LOST_HEALTH_TAG, lostHealth);
        }

        if (lostHealth > 0.0F) {
            AttributeModifier currentModifier = maxHealth.getModifier(MODIFIER_ID);
            if (currentModifier == null || Math.abs(currentModifier.amount() + lostHealth) > 1.0E-4) {
                maxHealth.removeModifier(MODIFIER_ID);
                maxHealth.addTransientModifier(new AttributeModifier(
                        MODIFIER_ID, -lostHealth, AttributeModifier.Operation.ADD_VALUE
                ));
            }
            if (!entity.level().isClientSide && entity.getHealth() > entity.getMaxHealth()) {
                entity.setHealth(entity.getMaxHealth());
            }
        }
    }
}
