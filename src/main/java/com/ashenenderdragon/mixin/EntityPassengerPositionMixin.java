package com.ashenenderdragon.mixin;

import com.ashenenderdragon.boss.DragonBossController;
import com.ashenenderdragon.boss.DragonBossState;
import com.ashenenderdragon.registry.ModAttachments;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Positions the two deliberately-created passenger combinations used by phase two. */
@Mixin(Entity.class)
public abstract class EntityPassengerPositionMixin {
    @Inject(
            method = "positionRider(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$MoveFunction;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void ashenenderdragon$positionCustomPassenger(Entity rider, Entity.MoveFunction moveFunction, CallbackInfo ci) {
        Entity vehicle = (Entity)(Object)this;
        if (vehicle instanceof EnderMan enderman && rider instanceof Creeper) {
            float yaw = -enderman.yBodyRot * Mth.DEG_TO_RAD;
            Vec3 forward = new Vec3(Mth.sin(yaw), 0.0, Mth.cos(yaw));
            Vec3 position = enderman.position().add(forward.scale(0.72)).add(0.0, 0.55, 0.0);
            moveFunction.accept(rider, position.x, position.y, position.z);
            ci.cancel();
            return;
        }

        if (vehicle instanceof EnderDragon dragon && rider instanceof Player) {
            DragonBossState state = dragon.getExistingDataOrNull(ModAttachments.DRAGON_BOSS_STATE.get());
            if (state == null || !DragonBossController.STAGE_TWO_GRAB_CARRY.equals(state.getActionId())) {
                return;
            }
            Vec3 forward = DragonBossController.dragonForwardForPassenger(dragon);
            Vec3 position = dragon.head.position().subtract(forward.scale(0.9)).add(0.0, -1.7, 0.0);
            moveFunction.accept(rider, position.x, position.y, position.z);
            ci.cancel();
        }
    }
}
