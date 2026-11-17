package com.ashenenderdragon.mixin;

import com.ashenenderdragon.boss.DragonBossController;

import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhaseManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(EnderDragonPhaseManager.class)
public abstract class EnderDragonPhaseManagerMixin {
    @Shadow
    @Final
    private EnderDragon dragon;

    @ModifyVariable(method = "setPhase", at = @At("HEAD"), argsOnly = true)
    private EnderDragonPhase<?> ashenenderdragon$filterPhaseTransition(EnderDragonPhase<?> requested) {
        return DragonBossController.filterPhaseTransition(this.dragon, requested);
    }
}
