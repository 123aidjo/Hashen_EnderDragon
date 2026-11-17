package com.ashenenderdragon.mixin;

import com.ashenenderdragon.item.ItemCombatEvents;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Scales direct push impulses, including vertical launch, while the custom shield is raised. */
@Mixin(Entity.class)
public abstract class EntityPushMixin {
    @ModifyVariable(method = "push(DDD)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double ashenenderdragon$reducePushX(double value) {
        return this.ashenenderdragon$scalePush(value);
    }

    @ModifyVariable(method = "push(DDD)V", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private double ashenenderdragon$reducePushY(double value) {
        return this.ashenenderdragon$scalePush(value);
    }

    @ModifyVariable(method = "push(DDD)V", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private double ashenenderdragon$reducePushZ(double value) {
        return this.ashenenderdragon$scalePush(value);
    }

    private double ashenenderdragon$scalePush(double value) {
        Entity entity = (Entity)(Object)this;
        return entity instanceof Player player && ItemCombatEvents.shouldReduceImpulse(player)
                ? value * ItemCombatEvents.IMPULSE_REMAINING_FRACTION
                : value;
    }
}
