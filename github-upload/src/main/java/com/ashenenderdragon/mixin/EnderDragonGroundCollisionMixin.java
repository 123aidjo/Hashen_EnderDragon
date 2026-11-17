package com.ashenenderdragon.mixin;

import java.util.List;

import com.ashenenderdragon.boss.DragonBossController;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cancels only the vanilla head/neck contact-damage helper while the custom dragon is grounded. */
@Mixin(EnderDragon.class)
public abstract class EnderDragonGroundCollisionMixin {
    @Inject(method = "hurt(Ljava/util/List;)V", at = @At("HEAD"), cancellable = true)
    private void ashenenderdragon$disableGroundedHeadDamage(List<Entity> entities, CallbackInfo callbackInfo) {
        if (DragonBossController.shouldSuppressVanillaGroundedAi((EnderDragon)(Object)this)) {
            callbackInfo.cancel();
        }
    }
}
