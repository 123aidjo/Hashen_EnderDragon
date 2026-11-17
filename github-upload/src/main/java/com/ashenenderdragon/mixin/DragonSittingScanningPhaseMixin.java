package com.ashenenderdragon.mixin;

import com.ashenenderdragon.boss.DragonBossController;

import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.AbstractDragonSittingPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonSittingScanningPhase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the vanilla sitting movement semantics but removes its hard-coded attack and takeoff scheduler. */
@Mixin(DragonSittingScanningPhase.class)
public abstract class DragonSittingScanningPhaseMixin extends AbstractDragonSittingPhase {
    protected DragonSittingScanningPhaseMixin(EnderDragon dragon) {
        super(dragon);
    }

    @Inject(method = "doServerTick", at = @At("HEAD"), cancellable = true)
    private void ashenenderdragon$suppressVanillaGroundedAi(CallbackInfo callbackInfo) {
        if (DragonBossController.shouldSuppressVanillaGroundedAi(this.dragon)) {
            callbackInfo.cancel();
        }
    }
}
