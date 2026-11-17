package com.ashenenderdragon.boss;

import com.ashenenderdragon.AshenEnderDragon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class DragonMinionHelper {
    public static final String CARRYING_CREEPER_TAG = AshenEnderDragon.MODID + ":carrying_creeper";

    private DragonMinionHelper() {
    }

    public static EnderMan spawnHostileEnderman(ServerLevel level, Player target, Vec3 position, boolean withCreeper, boolean lightning) {
        EnderMan enderman = EntityType.ENDERMAN.create(level);
        if (enderman == null) {
            return null;
        }
        enderman.moveTo(position.x, position.y, position.z, level.random.nextFloat() * 360.0F, 0.0F);
        enderman.setTarget(target);
        enderman.setPersistentAngerTarget(target.getUUID());
        enderman.setRemainingPersistentAngerTime(20 * 60);
        level.addFreshEntity(enderman);

        Creeper creeper = null;
        if (withCreeper) {
            creeper = EntityType.CREEPER.create(level);
            if (creeper != null) {
                creeper.moveTo(position.x, position.y + 1.0, position.z, enderman.getYRot(), 0.0F);
                creeper.setTarget(target);
                level.addFreshEntity(creeper);
                creeper.startRiding(enderman, true);
                enderman.getPersistentData().putBoolean(CARRYING_CREEPER_TAG, true);
            }
        }

        if (lightning) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(position.x, position.y, position.z);
                level.addFreshEntity(bolt);
            }
        }
        return enderman;
    }

    public static void tickCarrier(LivingEntity entity) {
        if (entity.level().isClientSide || !(entity instanceof EnderMan enderman)
                || !enderman.getPersistentData().getBoolean(CARRYING_CREEPER_TAG)) {
            return;
        }
        if (enderman.getPassengers().isEmpty()) {
            enderman.getPersistentData().remove(CARRYING_CREEPER_TAG);
            return;
        }
        Entity passenger = enderman.getPassengers().getFirst();
        if (!(passenger instanceof Creeper creeper) || !creeper.isAlive()) {
            enderman.getPersistentData().remove(CARRYING_CREEPER_TAG);
            return;
        }
        LivingEntity target = enderman.getTarget();
        boolean targetInThrowRange = target != null && target.isAlive() && enderman.distanceToSqr(target) <= 6.0 * 6.0;
        if (targetInThrowRange || creeper.getSwelling(1.0F) > 0.2F) {
            Vec3 direction = target == null
                    ? enderman.getLookAngle()
                    : target.getEyePosition().subtract(creeper.position());
            if (direction.lengthSqr() < 1.0E-4) {
                direction = enderman.getLookAngle();
            }
            Vec3 throwVelocity = direction.normalize().scale(0.65).add(0.0, 0.09, 0.0);
            creeper.ignite();
            creeper.stopRiding();
            creeper.setDeltaMovement(throwVelocity);
            creeper.hurtMarked = true;
            enderman.getPersistentData().remove(CARRYING_CREEPER_TAG);
        }
    }
}
