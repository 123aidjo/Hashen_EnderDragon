package com.ashenenderdragon.boss;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.ashenenderdragon.AshenEnderDragon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Phase-one grounded action scheduler. Vanilla sitting attacks are deliberately not used. */
public final class DragonGroundActions {
    public static final String BREATH_VOLLEY = "ground_breath_volley";
    public static final String TAIL_SWEEP = "ground_tail_sweep";
    public static final String BODY_SLAM = "ground_body_slam";
    public static final String CRAWL_CHARGE = "ground_crawl_charge";
    public static final String BITE = "ground_bite";
    public static final String FAN_BREATH = "ground_fan_breath";
    public static final String WIND_PRESSURE = "ground_wind_pressure";

    private static final String[] REGULAR_ACTIONS = {BREATH_VOLLEY, TAIL_SWEEP, BODY_SLAM, CRAWL_CHARGE, BITE, FAN_BREATH};
    private static final int MIN_ACTION_COOLDOWN = 30;
    private static final int MAX_ACTION_COOLDOWN = 50;

    private static final int BREATH_FIRST_SHOT_TICK = 20;
    private static final int BREATH_SECOND_SHOT_TICK = 50;
    private static final int BREATH_DURATION = 65;

    private static final int TAIL_SWEEP_WINDUP = 15;
    private static final int TAIL_SWEEP_DURATION = 35;
    private static final double TAIL_SWEEP_RADIUS = 14.0;
    private static final float TAIL_SWEEP_DAMAGE = 14.0F;

    public static final int BODY_SLAM_IMPACT_TICK = 40;
    private static final int BODY_SLAM_WAVE_TICKS = 52;
    private static final int BODY_SLAM_DURATION = BODY_SLAM_IMPACT_TICK + BODY_SLAM_WAVE_TICKS + 12;
    private static final double BODY_SLAM_RADIUS = 7.0;
    private static final float BODY_SLAM_DAMAGE = 14.0F;
    private static final float BREATH_WAVE_DAMAGE = 8.0F;
    private static final double BREATH_WAVE_VERTICAL_TOLERANCE = 0.9;
    private static final double ENDER_WAVE_TELEPORT_MIN_DISTANCE = 18.0;
    private static final double ENDER_WAVE_TELEPORT_DISTANCE_RANGE = 9.0;
    private static final Map<UUID, List<EnderPressureWave>> ACTIVE_ENDER_WAVES = new HashMap<>();

    public static final int CRAWL_CHARGE_START_TICK = 30;
    private static final int CRAWL_CHARGE_END = 85;
    private static final int CRAWL_CHARGE_DURATION = 95;
    private static final double CRAWL_TELEPORT_DISTANCE = 18.0;
    private static final double CRAWL_SPEED = 0.75;
    private static final float CRAWL_DAMAGE = 16.0F;

    private static final int BITE_TURN_TICKS = 10;
    private static final int BITE_HIT_TICK = 26;
    private static final int BITE_DURATION = 40;
    private static final double BITE_STEP_SPEED = 0.22;
    private static final float BITE_DAMAGE = 12.0F;

    public static final int FAN_BREATH_START_TICK = 25;
    public static final int FAN_BREATH_END_TICK = 55;
    private static final int FAN_BREATH_DURATION = 65;
    private static final int FAN_BREATH_DAMAGE_INTERVAL = 10;
    private static final double FAN_BREATH_RANGE = 20.0;
    private static final double FAN_BREATH_HALF_ANGLE_RADIANS = Math.toRadians(40.0);
    private static final float FAN_BREATH_DAMAGE = 6.0F;

    private static final int TAKEOFF_WINDUP_TICKS = 20;
    private static final double WIND_PRESSURE_RADIUS = 16.0;

    private DragonGroundActions() {
    }

    public static void initialize(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        cleanup(dragon);
        state.setNextActionAt(level.getGameTime() + nextCooldown(dragon));
    }

    public static void tick(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        tickEnderPressureWaves(level, dragon);
        if (state.getModeTicks() >= DragonBossController.groundedDurationTicks(state)
                && !WIND_PRESSURE.equals(state.getActionId())) {
            if (state.getBossStage() == 2 && state.getStageTwoGroundedCount() >= 2) {
                DragonStageThreeTransition.begin(level, dragon, state);
                return;
            }
            beginTakeoff(level, dragon, state);
        }

        tickWindupParticles(level, dragon, state);

        switch (state.getActionId()) {
            case BREATH_VOLLEY -> tickBreathVolley(level, dragon, state);
            case TAIL_SWEEP -> tickTailSweep(level, dragon, state);
            case BODY_SLAM -> tickBodySlam(level, dragon, state);
            case CRAWL_CHARGE -> tickCrawlCharge(level, dragon, state);
            case BITE -> tickBite(level, dragon, state);
            case FAN_BREATH -> tickFanBreath(level, dragon, state);
            case WIND_PRESSURE -> tickTakeoff(level, dragon, state);
            case DragonBossState.NO_ACTION -> tickIdle(level, dragon, state);
            default -> finishAction(level, dragon, state);
        }
    }

    private static void tickWindupParticles(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        int ticks = actionTicks(level, state);
        if ((ticks & 1) != 0) {
            return;
        }

        switch (state.getActionId()) {
            case BREATH_VOLLEY -> {
                if (ticks < BREATH_FIRST_SHOT_TICK
                        || ticks >= 35 && ticks < BREATH_SECOND_SHOT_TICK) {
                    emitHeadWindup(level, dragon, ticks < BREATH_FIRST_SHOT_TICK
                            ? BREATH_FIRST_SHOT_TICK - ticks
                            : BREATH_SECOND_SHOT_TICK - ticks);
                }
            }
            case TAIL_SWEEP -> {
                if (ticks < TAIL_SWEEP_WINDUP) {
                    emitTailWindup(level, dragon, ticks);
                }
            }
            case BODY_SLAM -> {
                if (ticks < BODY_SLAM_IMPACT_TICK) {
                    emitBodyWindup(level, dragon, BODY_SLAM_IMPACT_TICK - ticks);
                }
            }
            case CRAWL_CHARGE -> {
                if (ticks < CRAWL_CHARGE_START_TICK) {
                    emitChargeWindup(level, dragon, CRAWL_CHARGE_START_TICK - ticks);
                }
            }
            case BITE -> {
                if (ticks < BITE_HIT_TICK) {
                    emitHeadWindup(level, dragon, BITE_HIT_TICK - ticks);
                }
            }
            case FAN_BREATH -> {
                if (ticks < FAN_BREATH_START_TICK) {
                    emitHeadWindup(level, dragon, FAN_BREATH_START_TICK - ticks);
                }
            }
            case WIND_PRESSURE -> {
                if (ticks < TAKEOFF_WINDUP_TICKS) {
                    emitWingWindup(level, dragon, TAKEOFF_WINDUP_TICKS - ticks);
                }
            }
            default -> {
            }
        }
    }

    private static void emitHeadWindup(ServerLevel level, EnderDragon dragon, int ticksRemaining) {
        Vec3 head = new Vec3(dragon.head.getX(), dragon.head.getY(0.5), dragon.head.getZ());
        emitEnderCharge(level, head, ticksRemaining <= 6 ? 28 : 16, 1.7, 1.15);
        if (ticksRemaining <= 6) {
            level.sendParticles(ParticleTypes.DRAGON_BREATH, head.x, head.y, head.z, 14, 1.0, 0.7, 1.0, 0.04);
        }
    }

    private static void emitBodyWindup(ServerLevel level, EnderDragon dragon, int ticksRemaining) {
        Vec3 body = dragon.position().add(0.0, 2.2, 0.0);
        emitEnderCharge(level, body, ticksRemaining <= 8 ? 42 : 22, 3.6, 1.8);
        if (ticksRemaining <= 8) {
            level.sendParticles(ParticleTypes.DRAGON_BREATH, body.x, body.y, body.z, 20, 2.5, 1.2, 2.5, 0.035);
        }
    }

    private static void emitChargeWindup(ServerLevel level, EnderDragon dragon, int ticksRemaining) {
        Vec3 forward = DragonBossController.dragonForward(dragon);
        Vec3 body = dragon.position().add(forward.scale(2.5)).add(0.0, 1.8, 0.0);
        emitEnderCharge(level, body, ticksRemaining <= 6 ? 44 : 24, 3.2, 1.6);
        Vec3 head = new Vec3(dragon.head.getX(), dragon.head.getY(0.5), dragon.head.getZ());
        emitEnderCharge(level, head, ticksRemaining <= 6 ? 24 : 12, 1.5, 1.0);
    }

    private static void emitWingWindup(ServerLevel level, EnderDragon dragon, int ticksRemaining) {
        Vec3 forward = DragonBossController.dragonForward(dragon);
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
        Vec3 center = dragon.position().add(0.0, 2.0, 0.0);
        int count = ticksRemaining <= 6 ? 38 : 22;
        emitEnderCharge(level, center.add(right.scale(7.0)), count, 3.5, 1.3);
        emitEnderCharge(level, center.subtract(right.scale(7.0)), count, 3.5, 1.3);
    }

    private static void emitTailWindup(ServerLevel level, EnderDragon dragon, int ticks) {
        Vec3 backward = DragonBossController.dragonForward(dragon).scale(-1.0);
        double progress = Mth.clamp(ticks / (double)TAIL_SWEEP_WINDUP, 0.0, 1.0);
        double leadingDistance = 3.0 + progress * 12.0;
        for (int trail = 0; trail < 4; trail++) {
            double distance = Math.max(2.5, leadingDistance - trail * 2.2);
            Vec3 point = dragon.position().add(backward.scale(distance)).add(0.0, 1.4, 0.0);
            emitEnderCharge(level, point, trail == 0 ? 34 : 18, 1.7, 0.95);
        }
    }

    private static void emitEnderCharge(ServerLevel level, Vec3 position, int count, double horizontalSpread, double verticalSpread) {
        level.sendParticles(
                ParticleTypes.PORTAL,
                position.x, position.y, position.z,
                count,
                horizontalSpread, verticalSpread, horizontalSpread,
                0.16
        );
        level.sendParticles(
                ParticleTypes.REVERSE_PORTAL,
                position.x, position.y, position.z,
                Math.max(6, count / 2),
                horizontalSpread * 0.75, verticalSpread * 0.75, horizontalSpread * 0.75,
                0.08
        );
    }

    private static void tickIdle(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        if (level.getGameTime() < state.getNextActionAt()) {
            return;
        }

        List<Player> players = DragonBossController.getCombatPlayers(level, dragon);
        if (players.isEmpty()) {
            state.setNextActionAt(level.getGameTime() + 20);
            return;
        }

        String action = REGULAR_ACTIONS[dragon.getRandom().nextInt(REGULAR_ACTIONS.length)];
        if (state.getConsecutiveRegularActions() >= 2 && action.equals(state.getLastRegularActionId())) {
            int previousIndex = indexOf(action);
            action = REGULAR_ACTIONS[(previousIndex + 1 + dragon.getRandom().nextInt(REGULAR_ACTIONS.length - 1)) % REGULAR_ACTIONS.length];
        }

        state.recordRegularAction(action);
        switch (action) {
            case BREATH_VOLLEY -> beginBreathVolley(level, dragon, state, DragonBossController.choosePlayer(dragon, players));
            case TAIL_SWEEP -> beginTailSweep(level, dragon, state);
            case BODY_SLAM -> beginBodySlam(level, dragon, state);
            case CRAWL_CHARGE -> beginCrawlCharge(level, dragon, state, DragonBossController.choosePlayer(dragon, players));
            case BITE -> beginBite(level, dragon, state, DragonBossController.choosePlayer(dragon, players));
            case FAN_BREATH -> beginFanBreath(level, dragon, state, DragonBossController.choosePlayer(dragon, players));
            default -> throw new IllegalStateException("Unknown grounded dragon action: " + action);
        }
    }

    private static int indexOf(String action) {
        for (int i = 0; i < REGULAR_ACTIONS.length; i++) {
            if (REGULAR_ACTIONS[i].equals(action)) {
                return i;
            }
        }
        return 0;
    }

    private static void beginBreathVolley(ServerLevel level, EnderDragon dragon, DragonBossState state, Player target) {
        state.beginAction(BREATH_VOLLEY, level.getGameTime());
        state.setDiveTarget(target.getUUID(), target.getId());
        DragonBossController.sync(dragon);
    }

    private static void tickBreathVolley(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        int ticks = actionTicks(level, state);
        Player target = getOrChooseTarget(level, dragon, state);
        if (target != null) {
            facePoint(dragon, target.position(), 8.0F);
        }

        if (ticks == BREATH_FIRST_SHOT_TICK && target != null) {
            startEnderPressureWave(level, dragon, state);
            shootDragonFireball(level, dragon, target);
            Player nextTarget = chooseDifferentTarget(level, dragon, target);
            if (nextTarget != null) {
                state.setDiveTarget(nextTarget.getUUID(), nextTarget.getId());
                DragonBossController.sync(dragon);
            }
        } else if (ticks == BREATH_SECOND_SHOT_TICK && target != null) {
            shootDragonFireball(level, dragon, target);
        }

        if (ticks >= BREATH_DURATION) {
            finishAction(level, dragon, state);
        }
    }

    private static void shootDragonFireball(ServerLevel level, EnderDragon dragon, Player target) {
        Vec3 view = dragon.getViewVector(1.0F);
        double x = dragon.head.getX() - view.x;
        double y = dragon.head.getY(0.5) + 0.5;
        double z = dragon.head.getZ() - view.z;
        Vec3 shot = target.getEyePosition().subtract(x, y, z).normalize();
        if (!dragon.isSilent()) {
            level.levelEvent(null, 1017, dragon.blockPosition(), 0);
        }
        DragonFireball fireball = new DragonFireball(level, dragon, shot);
        fireball.moveTo(x, y, z, 0.0F, 0.0F);
        level.addFreshEntity(fireball);
    }

    @Nullable
    private static Player chooseDifferentTarget(ServerLevel level, EnderDragon dragon, Player previous) {
        List<Player> players = DragonBossController.getCombatPlayers(level, dragon);
        List<Player> alternatives = players.stream().filter(player -> !player.getUUID().equals(previous.getUUID())).toList();
        return alternatives.isEmpty() ? previous : DragonBossController.choosePlayer(dragon, alternatives);
    }

    private static void beginTailSweep(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        state.beginAction(TAIL_SWEEP, level.getGameTime());
        state.setActionDirection(new Vec3(dragon.getRandom().nextBoolean() ? 1.0 : -1.0, 0.0, 0.0));
        state.setDiveTarget(null, -1);
        DragonBossController.sync(dragon);
    }

    private static void tickTailSweep(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        int ticks = actionTicks(level, state);
        if (ticks == TAIL_SWEEP_WINDUP) {
            startEnderPressureWave(level, dragon, state);
            performTailSweep(level, dragon, state);
        }
        if (ticks >= TAIL_SWEEP_DURATION) {
            finishAction(level, dragon, state);
        }
    }

    private static void performTailSweep(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        Vec3 forward = DragonBossController.dragonForward(dragon);
        AABB area = dragon.getBoundingBox().inflate(TAIL_SWEEP_RADIUS, 4.0, TAIL_SWEEP_RADIUS);
        for (LivingEntity target : attackableEntities(level, dragon, area)) {
            Vec3 offset = target.position().subtract(dragon.position()).multiply(1.0, 0.0, 1.0);
            double distance = offset.length();
            if (distance >= 3.0 && distance <= TAIL_SWEEP_RADIUS && offset.normalize().dot(forward) < 0.55) {
                target.hurt(dragon.damageSources().mobAttack(dragon), TAIL_SWEEP_DAMAGE);
                target.knockback(1.6, dragon.getX() - target.getX(), dragon.getZ() - target.getZ());
            }
        }

        double sign = state.getActionDirection() == null ? 1.0 : Math.signum(state.getActionDirection().x);
        Vec3 backward = forward.scale(-1.0);
        for (int angle = -100; angle <= 100; angle += 20) {
            Vec3 direction = backward.yRot((float)Math.toRadians(angle * sign));
            for (double radius : new double[]{5.0, 9.0, 13.0}) {
                Vec3 particle = dragon.position().add(direction.scale(radius)).add(0.0, 1.0, 0.0);
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, particle.x, particle.y, particle.z, 2, 0.5, 0.25, 0.5, 0.0);
            }
        }
        dragon.setYRot(Mth.wrapDegrees(dragon.getYRot() + 180.0F));
        dragon.yRotA = 0.0F;
        level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 4.0F, 0.55F);
    }

    private static void beginBodySlam(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        state.beginAction(BODY_SLAM, level.getGameTime());
        state.setActionPositions(List.of(DragonBossController.findGroundAt(level, dragon.blockPosition())));
        state.setDiveTarget(null, -1);
        DragonBossController.sync(dragon);
        level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 5.0F, 0.66F);
    }

    private static void tickBodySlam(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        int ticks = actionTicks(level, state);
        Vec3 center = state.getActionPositions().isEmpty()
                ? DragonBossController.findGroundAt(level, dragon.blockPosition())
                : state.getActionPositions().getFirst();
        if (ticks == BODY_SLAM_IMPACT_TICK) {
            performBodySlam(level, dragon, center);
            startEnderPressureWave(level, dragon, state, center);
        } else if (ticks > BODY_SLAM_IMPACT_TICK && ticks <= BODY_SLAM_IMPACT_TICK + BODY_SLAM_WAVE_TICKS) {
            if (state.getBossStage() < 2) {
                tickBreathWave(level, dragon, center, ticks - BODY_SLAM_IMPACT_TICK);
            }
        }
        if (ticks >= BODY_SLAM_DURATION) {
            finishAction(level, dragon, state);
        }
    }

    private static void performBodySlam(ServerLevel level, EnderDragon dragon, Vec3 center) {
        AABB area = new AABB(center, center).inflate(BODY_SLAM_RADIUS, 4.0, BODY_SLAM_RADIUS);
        for (LivingEntity target : attackableEntities(level, dragon, area)) {
            if (horizontalDistanceSquared(target.position(), center) <= BODY_SLAM_RADIUS * BODY_SLAM_RADIUS) {
                target.hurt(dragon.damageSources().mobAttack(dragon), BODY_SLAM_DAMAGE);
                target.knockback(1.2, center.x - target.getX(), center.z - target.getZ());
            }
        }
        emitGroundImpact(level, center, 6.0);
    }

    private static void tickBreathWave(ServerLevel level, EnderDragon dragon, Vec3 center, int waveTick) {
        double radius = 1.0 + waveTick * 0.25;
        double previousRadius = Math.max(0.0, radius - 0.25);
        AABB area = new AABB(center, center).inflate(radius + 1.0, BREATH_WAVE_VERTICAL_TOLERANCE, radius + 1.0);
        for (LivingEntity target : attackableEntities(level, dragon, area)) {
            double distance = Math.sqrt(horizontalDistanceSquared(target.position(), center));
            if (distance > previousRadius && distance <= radius
                    && Math.abs(target.getY() - center.y) <= BREATH_WAVE_VERTICAL_TOLERANCE) {
                target.hurt(dragon.damageSources().mobAttack(dragon), BREATH_WAVE_DAMAGE);
                target.knockback(0.7, center.x - target.getX(), center.z - target.getZ());
            }
        }

        if (waveTick % 2 == 0) {
            int samples = 24;
            for (int i = 0; i < samples; i++) {
                double angle = Math.PI * 2.0 * i / samples;
                double x = center.x + Math.cos(angle) * radius;
                double z = center.z + Math.sin(angle) * radius;
                level.sendParticles(AshenEnderDragon.ENDER_SNOWFLAKE.get(), x, center.y + 0.3, z,
                        6, 0.22, 0.14, 0.22, 0.025);
            }
        }
    }

    private static void beginCrawlCharge(ServerLevel level, EnderDragon dragon, DragonBossState state, Player target) {
        Vec3 facing = DragonBossController.horizontalLook(target);
        Vec3 behind = target.position().subtract(facing.scale(CRAWL_TELEPORT_DISTANCE));
        Vec3 ground = DragonBossController.findGroundAt(level, BlockPos.containing(behind));
        Vec3 direction = target.position().subtract(ground).multiply(1.0, 0.0, 1.0).normalize();
        if (direction.lengthSqr() < 1.0E-4) {
            direction = facing;
        }

        state.beginAction(CRAWL_CHARGE, level.getGameTime());
        state.setDiveTarget(target.getUUID(), target.getId());
        state.setActionDirection(direction);
        state.setActionPositions(List.of(ground));
        DragonBossController.sync(dragon);

        emitTeleport(level, dragon.position());
        dragon.teleportTo(ground.x, ground.y, ground.z);
        dragon.setDeltaMovement(Vec3.ZERO);
        faceDirectionImmediately(dragon, direction);
        emitTeleport(level, ground);
        level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 5.0F, 0.65F);
    }

    private static void tickCrawlCharge(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        int ticks = actionTicks(level, state);
        Vec3 direction = state.getActionDirection();
        if (direction == null) {
            direction = DragonBossController.horizontalLook(dragon);
            state.setActionDirection(direction);
        }
        faceDirectionImmediately(dragon, direction);

        if (ticks == CRAWL_CHARGE_START_TICK) {
            startEnderPressureWave(level, dragon, state);
            emitGroundImpact(level, DragonBossController.findGroundAt(level, dragon.blockPosition()), 4.5);
        }
        if (ticks >= CRAWL_CHARGE_START_TICK && ticks < CRAWL_CHARGE_END) {
            moveCrawlingDragon(level, dragon, direction);
            damageCrawlPath(level, dragon, state, direction);
        } else {
            dragon.setDeltaMovement(Vec3.ZERO);
        }
        if (ticks >= CRAWL_CHARGE_DURATION) {
            state.setLandingTarget(DragonBossController.findGroundAt(level, dragon.blockPosition()));
            finishAction(level, dragon, state);
        }
    }

    private static void beginBite(ServerLevel level, EnderDragon dragon, DragonBossState state, Player target) {
        Vec3 direction = horizontalDirection(dragon.position(), target.position(), DragonBossController.dragonForward(dragon));
        state.beginAction(BITE, level.getGameTime());
        state.setDiveTarget(target.getUUID(), target.getId());
        state.setActionDirection(direction);
        DragonBossController.sync(dragon);
    }

    private static void tickBite(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        int ticks = actionTicks(level, state);
        Vec3 direction = state.getActionDirection();
        Player target = DragonBossController.findSelectedPlayer(level, dragon, state);
        if (ticks <= BITE_TURN_TICKS && target != null) {
            facePoint(dragon, target.position(), 14.0F);
            direction = horizontalDirection(dragon.position(), target.position(), DragonBossController.dragonForward(dragon));
            state.setActionDirection(direction);
            if (ticks == BITE_TURN_TICKS) {
                DragonBossController.sync(dragon);
            }
        } else if (direction != null) {
            faceDirectionImmediately(dragon, direction);
        }

        if (direction != null && (ticks >= 10 && ticks < 16 || ticks >= 18 && ticks < 24)) {
            stepGroundedDragon(level, dragon, direction, BITE_STEP_SPEED);
        } else {
            dragon.setDeltaMovement(Vec3.ZERO);
        }

        if (ticks == BITE_HIT_TICK) {
            startEnderPressureWave(level, dragon, state);
            performBite(level, dragon, state, direction == null ? DragonBossController.dragonForward(dragon) : direction);
        }
        if (ticks >= BITE_DURATION) {
            state.setLandingTarget(DragonBossController.findGroundAt(level, dragon.blockPosition()));
            finishAction(level, dragon, state);
        }
    }

    private static void performBite(ServerLevel level, EnderDragon dragon, DragonBossState state, Vec3 direction) {
        Vec3 neckCenter = dragon.position().add(direction.scale(4.2)).add(0.0, 1.0, 0.0);
        AABB biteArea = dragon.head.getBoundingBox()
                .inflate(2.0)
                .minmax(new AABB(neckCenter, neckCenter).inflate(2.4, 2.0, 2.4));
        for (LivingEntity target : attackableEntities(level, dragon, biteArea)) {
            if (state.markActionHit(target.getUUID())) {
                target.hurt(dragon.damageSources().mobAttack(dragon), BITE_DAMAGE);
                target.push(direction.x * 0.8, 0.2, direction.z * 0.8);
                target.hurtMarked = true;
            }
        }
        level.playSound(null, dragon.head.getX(), dragon.head.getY(), dragon.head.getZ(), SoundEvents.ENDER_DRAGON_HURT, SoundSource.HOSTILE, 2.5F, 0.8F);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, dragon.head.getX(), dragon.head.getY(0.5), dragon.head.getZ(), 8, 1.5, 1.0, 1.5, 0.0);
    }

    private static void beginFanBreath(ServerLevel level, EnderDragon dragon, DragonBossState state, Player target) {
        Vec3 direction = horizontalDirection(dragon.position(), target.position(), DragonBossController.dragonForward(dragon));
        state.beginAction(FAN_BREATH, level.getGameTime());
        state.setDiveTarget(target.getUUID(), target.getId());
        state.setActionDirection(direction);
        faceDirectionImmediately(dragon, direction);
        DragonBossController.sync(dragon);
        level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 5.0F, 0.68F);
    }

    private static void tickFanBreath(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        int ticks = actionTicks(level, state);
        Vec3 direction = state.getActionDirection();
        if (direction == null) {
            direction = DragonBossController.dragonForward(dragon);
            state.setActionDirection(direction);
        }
        faceDirectionImmediately(dragon, direction);
        dragon.setDeltaMovement(Vec3.ZERO);

        if (ticks >= FAN_BREATH_START_TICK && ticks < FAN_BREATH_END_TICK) {
            if (ticks == FAN_BREATH_START_TICK) {
                startEnderPressureWave(level, dragon, state);
            }
            if ((ticks - FAN_BREATH_START_TICK) % FAN_BREATH_DAMAGE_INTERVAL == 0) {
                damageFanBreath(level, dragon, direction);
            }
            if ((ticks - FAN_BREATH_START_TICK) % 2 == 0) {
                emitFanBreath(level, dragon, direction);
            }
        }
        if (ticks >= FAN_BREATH_DURATION) {
            finishAction(level, dragon, state);
        }
    }

    private static void damageFanBreath(ServerLevel level, EnderDragon dragon, Vec3 direction) {
        Vec3 origin = dragon.position().add(direction.scale(3.0));
        AABB area = new AABB(origin, origin).inflate(FAN_BREATH_RANGE, 3.0, FAN_BREATH_RANGE);
        double minimumDot = Math.cos(FAN_BREATH_HALF_ANGLE_RADIANS);
        for (LivingEntity target : attackableEntities(level, dragon, area)) {
            Vec3 offset = target.position().subtract(origin).multiply(1.0, 0.0, 1.0);
            double distance = offset.length();
            if (distance <= FAN_BREATH_RANGE && distance > 0.1 && offset.normalize().dot(direction) >= minimumDot
                    && Math.abs(target.getY() - dragon.getY()) <= 3.0) {
                target.hurt(dragon.damageSources().mobAttack(dragon), FAN_BREATH_DAMAGE);
                target.push(direction.x * 0.6, 0.15, direction.z * 0.6);
                target.hurtMarked = true;
            }
        }
    }

    private static void emitFanBreath(ServerLevel level, EnderDragon dragon, Vec3 direction) {
        Vec3 origin = dragon.position().add(direction.scale(3.0));
        for (double distance = 4.0; distance <= FAN_BREATH_RANGE; distance += 3.0) {
            for (int angle = -40; angle <= 40; angle += 10) {
                Vec3 ray = direction.yRot((float)Math.toRadians(angle));
                Vec3 sample = origin.add(ray.scale(distance));
                Vec3 ground = DragonBossController.findGroundAt(level, BlockPos.containing(sample));
                level.sendParticles(ParticleTypes.DRAGON_BREATH, sample.x, ground.y + 0.45, sample.z, 3, 0.35, 0.2, 0.35, 0.025);
            }
        }
    }

    private static void moveCrawlingDragon(ServerLevel level, EnderDragon dragon, Vec3 direction) {
        Vec3 horizontalMovement = direction.scale(CRAWL_SPEED);
        Vec3 next = dragon.position().add(horizontalMovement);
        Vec3 ground = DragonBossController.findGroundAt(level, BlockPos.containing(next));
        double verticalMovement = Mth.clamp(ground.y - dragon.getY(), -0.5, 0.5);
        dragon.move(MoverType.SELF, new Vec3(horizontalMovement.x, verticalMovement, horizontalMovement.z));
        dragon.setDeltaMovement(Vec3.ZERO);
        level.sendParticles(ParticleTypes.CLOUD, dragon.getX(), dragon.getY() + 0.3, dragon.getZ(), 8, 5.0, 0.25, 3.0, 0.06);
    }

    private static void damageCrawlPath(ServerLevel level, EnderDragon dragon, DragonBossState state, Vec3 direction) {
        AABB path = dragon.getBoundingBox().inflate(2.0, 1.0, 2.0);
        for (LivingEntity target : attackableEntities(level, dragon, path)) {
            if (state.markActionHit(target.getUUID())) {
                target.hurt(dragon.damageSources().mobAttack(dragon), CRAWL_DAMAGE);
                target.push(direction.x * 1.4, 0.35, direction.z * 1.4);
                target.hurtMarked = true;
            }
        }
    }

    private static void beginTakeoff(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        state.beginAction(WIND_PRESSURE, level.getGameTime());
        state.setDiveTarget(null, -1);
        state.setNextActionAt(0L);
        DragonBossController.sync(dragon);
        level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 5.0F, 0.72F);
    }

    static void beginDamageThresholdTakeoff(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        if (WIND_PRESSURE.equals(state.getActionId())) {
            return;
        }
        cleanup(dragon);
        dragon.setDeltaMovement(Vec3.ZERO);
        beginTakeoff(level, dragon, state);
    }

    private static void tickTakeoff(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        if (actionTicks(level, state) < TAKEOFF_WINDUP_TICKS) {
            return;
        }

        Vec3 center = dragon.position();
        AABB area = new AABB(center, center).inflate(WIND_PRESSURE_RADIUS, 7.0, WIND_PRESSURE_RADIUS);
        for (LivingEntity target : attackableEntities(level, dragon, area)) {
            if (horizontalDistanceSquared(target.position(), center) > WIND_PRESSURE_RADIUS * WIND_PRESSURE_RADIUS) {
                continue;
            }
            Vec3 away = target.position().subtract(center).multiply(1.0, 0.0, 1.0);
            if (away.lengthSqr() < 1.0E-4) {
                away = new Vec3(1.0, 0.0, 0.0);
            } else {
                away = away.normalize();
            }
            target.push(away.x, 0.7, away.z);
            target.hurtMarked = true;
        }
        level.sendParticles(ParticleTypes.CLOUD, center.x, center.y + 0.5, center.z, 240, 12.0, 0.8, 12.0, 0.45);
        level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, center.x, center.y + 0.5, center.z, 6, 6.0, 0.3, 6.0, 0.0);

        state.setMode(DragonBossMode.TAKEOFF, level.getGameTime());
        state.clearAction(level.getGameTime());
        cleanup(dragon);
        DragonBossController.removeGroundResistance(dragon);
        DragonBossController.sync(dragon);
        dragon.setDeltaMovement(0.0, 0.8, 0.0);
        dragon.getPhaseManager().setPhase(EnderDragonPhase.TAKEOFF);
    }

    private static void finishAction(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        state.clearAction(level.getGameTime());
        state.setDiveTarget(null, -1);
        state.setNextActionAt(level.getGameTime() + nextCooldown(dragon));
        dragon.setDeltaMovement(Vec3.ZERO);
        DragonBossController.sync(dragon);
    }

    private static void startEnderPressureWave(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        startEnderPressureWave(level, dragon, state,
                DragonBossController.findGroundAt(level, dragon.blockPosition()));
    }

    private static void startEnderPressureWave(
            ServerLevel level,
            EnderDragon dragon,
            DragonBossState state,
            Vec3 center) {
        if (state.getBossStage() < 2) {
            return;
        }
        ACTIVE_ENDER_WAVES.computeIfAbsent(dragon.getUUID(), ignored -> new ArrayList<>())
                .add(new EnderPressureWave(center, level.getGameTime()));
        level.sendParticles(AshenEnderDragon.ENDER_SNOWFLAKE.get(), center.x, center.y + 0.45, center.z,
                90, 1.5, 0.3, 1.5, 0.06);
    }

    private static void tickEnderPressureWaves(ServerLevel level, EnderDragon dragon) {
        List<EnderPressureWave> waves = ACTIVE_ENDER_WAVES.get(dragon.getUUID());
        if (waves == null) {
            return;
        }

        Iterator<EnderPressureWave> iterator = waves.iterator();
        while (iterator.hasNext()) {
            EnderPressureWave wave = iterator.next();
            int waveTick = (int)Math.max(0L, level.getGameTime() - wave.startedAt);
            if (waveTick > BODY_SLAM_WAVE_TICKS) {
                iterator.remove();
                continue;
            }
            tickEnderPressureWave(level, dragon, wave, waveTick);
        }
        if (waves.isEmpty()) {
            ACTIVE_ENDER_WAVES.remove(dragon.getUUID());
        }
    }

    private static void tickEnderPressureWave(
            ServerLevel level,
            EnderDragon dragon,
            EnderPressureWave wave,
            int waveTick) {
        double radius = 1.0 + waveTick * 0.25;
        double previousRadius = Math.max(0.0, radius - 0.25);
        AABB area = new AABB(wave.center, wave.center)
                .inflate(radius + 1.0, BREATH_WAVE_VERTICAL_TOLERANCE, radius + 1.0);
        for (Player target : level.getEntitiesOfClass(Player.class, area,
                player -> player.isAlive() && dragon.canAttack(player))) {
            double distance = Math.sqrt(horizontalDistanceSquared(target.position(), wave.center));
            if (distance > previousRadius && distance <= radius
                    && Math.abs(target.getY() - wave.center.y) <= BREATH_WAVE_VERTICAL_TOLERANCE
                    && wave.hitEntities.add(target.getUUID())) {
                target.hurt(dragon.damageSources().mobAttack(dragon), BREATH_WAVE_DAMAGE);
                teleportAwayFromDragon(level, dragon, target);
            }
        }

        if ((waveTick & 1) == 0) {
            int samples = 24;
            for (int i = 0; i < samples; i++) {
                double angle = Math.PI * 2.0 * i / samples;
                double x = wave.center.x + Math.cos(angle) * radius;
                double z = wave.center.z + Math.sin(angle) * radius;
                level.sendParticles(AshenEnderDragon.ENDER_SNOWFLAKE.get(), x, wave.center.y + 0.35, z,
                        6, 0.22, 0.16, 0.22, 0.03);
            }
        }
    }

    private static void teleportAwayFromDragon(ServerLevel level, EnderDragon dragon, LivingEntity target) {
        Vec3 origin = target.position();
        Vec3 away = origin.subtract(dragon.position()).multiply(1.0, 0.0, 1.0);
        if (away.lengthSqr() < 1.0E-4) {
            double angle = dragon.getRandom().nextDouble() * Math.PI * 2.0;
            away = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
        } else {
            away = away.normalize();
        }

        for (int attempt = 0; attempt < 12; attempt++) {
            double angleOffset = Math.toRadians(-45.0 + dragon.getRandom().nextDouble() * 90.0);
            double distance = ENDER_WAVE_TELEPORT_MIN_DISTANCE
                    + dragon.getRandom().nextDouble() * ENDER_WAVE_TELEPORT_DISTANCE_RANGE;
            Vec3 direction = away.yRot((float)angleOffset);
            Vec3 desired = dragon.position().add(direction.scale(distance));
            Vec3 ground = DragonBossController.findGroundAt(level, BlockPos.containing(desired));
            if (target.randomTeleport(ground.x, ground.y, ground.z, true)) {
                level.sendParticles(ParticleTypes.PORTAL, origin.x, origin.y + 1.0, origin.z,
                        36, 0.65, 0.9, 0.65, 0.12);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, ground.x, ground.y + 1.0, ground.z,
                        48, 0.75, 1.0, 0.75, 0.14);
                level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.ENDERMAN_TELEPORT,
                        SoundSource.HOSTILE, 1.8F, 0.72F);
                return;
            }
        }
    }

    public static void cleanup(EnderDragon dragon) {
        ACTIVE_ENDER_WAVES.remove(dragon.getUUID());
    }

    @Nullable
    private static Player getOrChooseTarget(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        Player target = DragonBossController.findSelectedPlayer(level, dragon, state);
        if (target != null) {
            return target;
        }
        target = DragonBossController.chooseCombatPlayer(level, dragon);
        if (target != null) {
            state.setDiveTarget(target.getUUID(), target.getId());
            DragonBossController.sync(dragon);
        }
        return target;
    }

    private static void facePoint(EnderDragon dragon, Vec3 point, float maxTurn) {
        double dx = point.x - dragon.getX();
        double dz = point.z - dragon.getZ();
        float targetYaw = (float)Math.toDegrees(Math.atan2(dx, -dz));
        dragon.setYRot(Mth.approachDegrees(dragon.getYRot(), targetYaw, maxTurn));
        dragon.yRotA = 0.0F;
    }

    private static void faceDirectionImmediately(EnderDragon dragon, Vec3 direction) {
        dragon.setYRot((float)Math.toDegrees(Math.atan2(direction.x, -direction.z)));
        dragon.yRotA = 0.0F;
    }

    private static Vec3 horizontalDirection(Vec3 from, Vec3 to, Vec3 fallback) {
        Vec3 direction = to.subtract(from).multiply(1.0, 0.0, 1.0);
        return direction.lengthSqr() < 1.0E-4 ? fallback : direction.normalize();
    }

    private static void stepGroundedDragon(ServerLevel level, EnderDragon dragon, Vec3 direction, double speed) {
        Vec3 movement = direction.scale(speed);
        Vec3 next = dragon.position().add(movement);
        Vec3 ground = DragonBossController.findGroundAt(level, BlockPos.containing(next));
        double verticalMovement = Mth.clamp(ground.y - dragon.getY(), -0.35, 0.35);
        dragon.move(MoverType.SELF, new Vec3(movement.x, verticalMovement, movement.z));
        dragon.setDeltaMovement(Vec3.ZERO);
    }

    private static List<LivingEntity> attackableEntities(ServerLevel level, EnderDragon dragon, AABB area) {
        return level.getEntitiesOfClass(LivingEntity.class, area, target -> target != dragon && dragon.canAttack(target));
    }

    private static void emitTeleport(ServerLevel level, Vec3 position) {
        level.sendParticles(ParticleTypes.PORTAL, position.x, position.y + 2.0, position.z, 360, 5.5, 3.5, 5.5, 0.4);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, position.x, position.y + 2.0, position.z, 220, 4.5, 3.0, 4.5, 0.2);
        level.sendParticles(ParticleTypes.DRAGON_BREATH, position.x, position.y + 2.0, position.z, 120, 4.0, 2.5, 4.0, 0.1);
        level.playSound(null, position.x, position.y, position.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 3.0F, 0.75F);
    }

    private static void emitGroundImpact(ServerLevel level, Vec3 center, double spread) {
        BlockPos surfaceBlock = BlockPos.containing(center).below();
        BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(surfaceBlock));
        level.sendParticles(debris, center.x, center.y + 0.25, center.z, 120, spread, 0.5, spread, 0.38);
        level.sendParticles(ParticleTypes.CLOUD, center.x, center.y + 0.4, center.z, 70, spread, 0.6, spread, 0.18);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 3.0F, 0.7F);
    }

    private static int actionTicks(ServerLevel level, DragonBossState state) {
        return (int)Math.max(0L, level.getGameTime() - state.getActionStartedAt());
    }

    private static int nextCooldown(EnderDragon dragon) {
        return MIN_ACTION_COOLDOWN + dragon.getRandom().nextInt(MAX_ACTION_COOLDOWN - MIN_ACTION_COOLDOWN + 1);
    }

    private static double horizontalDistanceSquared(Vec3 first, Vec3 second) {
        double x = first.x - second.x;
        double z = first.z - second.z;
        return x * x + z * z;
    }

    private static final class EnderPressureWave {
        private final Vec3 center;
        private final long startedAt;
        private final Set<UUID> hitEntities = new HashSet<>();

        private EnderPressureWave(Vec3 center, long startedAt) {
            this.center = center;
            this.startedAt = startedAt;
        }
    }
}
