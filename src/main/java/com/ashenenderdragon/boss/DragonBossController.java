package com.ashenenderdragon.boss;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.ashenenderdragon.AshenEnderDragon;
import com.ashenenderdragon.registry.ModAttachments;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authoritative phase-one encounter controller.
 *
 * <p>The controller schedules high-level modes and custom actions while vanilla phases continue to own dragon
 * movement, multipart updates, procedural animation, death, and {@link EndDragonFight} integration.</p>
 */
public final class DragonBossController {
    public static final String LOW_BREATH_PASS = "low_breath_pass";
    public static final String METEOR_VOLLEY = "meteor_volley";
    public static final String LANDING_TRANSITION = "landing_transition";
    public static final String STAGE_TWO_GRAB_DIVE = "stage_two_grab_dive";
    public static final String STAGE_TWO_GRAB_CARRY = "stage_two_grab_carry";
    public static final String STAGE_TWO_SUMMON_MINIONS = "stage_two_summon_minions";

    public static final int MAX_FLIGHT_TICKS = 90 * 20;
    public static final int MAX_GROUNDED_TICKS = 60 * 20;
    public static final int STAGE_TWO_MAX_GROUNDED_TICKS = 90 * 20;
    public static final int STAGE_TWO_SECOND_GROUNDED_TICKS = 85 * 20;
    public static final int MAX_DIVE_TICKS = 15 * 20;
    public static final int MAX_LANDING_TICKS = 10 * 20;
    public static final float FLIGHT_DAMAGE_THRESHOLD = 0.15F;
    private static final float STAGE_TWO_FIRST_GROUND_DAMAGE_THRESHOLD = 0.25F;

    private static final int MIN_ACTION_COOLDOWN = 60;
    private static final int MAX_ACTION_COOLDOWN = 100;
    private static final int LOW_PASS_MAX_APPROACH_TICKS = 8 * 20;
    private static final int LOW_PASS_SEGMENT_TICKS = 4 * 20;
    private static final int LOW_PASS_SEGMENTS = 3;
    private static final int LOW_PASS_DURATION = LOW_PASS_SEGMENT_TICKS * LOW_PASS_SEGMENTS;
    private static final double LOW_PASS_CLEARANCE = 5.0;
    private static final double LOW_PASS_APPROACH_SPEED = 0.9;
    private static final double LOW_PASS_ATTACK_SPEED = 1.2;
    private static final int BREATH_CLOUD_INTERVAL = 3;
    private static final int BREATH_CLOUD_DURATION = 6 * 20;

    private static final int METEOR_WAVES = 3;
    private static final int METEOR_WAVE_INTERVAL = 45;
    private static final int METEOR_WARNING_TICKS = 30;
    private static final int METEOR_ACTION_DURATION = (METEOR_WAVES - 1) * METEOR_WAVE_INTERVAL + METEOR_WARNING_TICKS + 5;
    private static final DustParticleOptions METEOR_WARNING_PARTICLE = new DustParticleOptions(new Vector3f(1.0F, 0.12F, 0.02F), 1.5F);
    private static final Map<UUID, List<MeteorPayload>> STAGE_TWO_METEORS = new HashMap<>();
    private static final Map<UUID, Integer> GRAB_HIT_COUNTS = new HashMap<>();

    private static final int MINION_SUMMON_WINDUP_TICKS = 30;
    private static final int MINION_SUMMON_DURATION_TICKS = 50;
    private static final int GRAB_CARRY_TICKS = 5 * 20;
    private static final int GRAB_BREAK_HITS = 8;
    private static final double GRAB_CLIMB_HEIGHT = 38.0;
    private static final int MAX_GROUND_RESISTANCE_LEVEL = 4;

    private static final int LANDING_VANISH_TICKS = 2 * 20;
    private static final int LANDING_FRONT_APPROACH_TICKS = 8 * 20;
    private static final double LANDING_FRONT_DISTANCE = 20.0;
    private static final double LANDING_FRONT_HEIGHT = 8.0;
    private static final double LANDING_FRONT_REACHED_DISTANCE_SQUARED = 8.0 * 8.0;
    private static final double LANDING_BEHIND_DISTANCE = 12.0;
    private static final double LANDING_SPAWN_HEIGHT = 12.0;
    private static final double LANDING_DIVE_SPEED = 1.4;
    private static final double LANDING_IMPACT_RADIUS = 8.0;
    private static final float LANDING_IMPACT_DAMAGE = 12.0F;

    private static final double TARGET_RANGE = 192.0;
    private static final double TARGET_RANGE_SQUARED = TARGET_RANGE * TARGET_RANGE;
    private static final double LANDING_APPROACH_DISTANCE_SQUARED = 10.0 * 10.0;
    private static final double GROUND_CONTACT_DISTANCE_SQUARED = 1.5 * 1.5;
    private static final double LANDING_FALLBACK_DISTANCE_SQUARED = 3.0 * 3.0;
    private static final Component BOSS_NAME = Component.translatable("entity.ashenenderdragon.ashen_ender_dragon");

    private record MeteorPayload(Vec3 position, UUID targetUuid, boolean enderVariant) {
    }
    private DragonBossController() {
    }

    public static boolean isOfficialDragon(EnderDragon dragon) {
        if (!(dragon.level() instanceof ServerLevel level)) {
            return false;
        }

        EndDragonFight fight = level.getDragonFight();
        return fight != null && dragon.getUUID().equals(fight.getDragonUUID());
    }

    public static void tick(EnderDragon dragon) {
        if (!(dragon.level() instanceof ServerLevel level) || !isOfficialDragon(dragon)) {
            return;
        }
        if (!BOSS_NAME.equals(dragon.getCustomName())) {
            dragon.setCustomName(BOSS_NAME);
            dragon.setCustomNameVisible(false);
        }
        if (dragon.isDeadOrDying() || dragon.getPhaseManager().getCurrentPhase().getPhase() == EnderDragonPhase.DYING) {
            STAGE_TWO_METEORS.remove(dragon.getUUID());
            GRAB_HIT_COUNTS.remove(dragon.getUUID());
            DragonGroundActions.cleanup(dragon);
            DragonStageThreeTransition.cleanup(dragon);
            return;
        }

        DragonBossState state = dragon.getData(ModAttachments.DRAGON_BOSS_STATE);
        if (!state.isInitialized()) {
            state.initialize(inferMode(dragon.getPhaseManager().getCurrentPhase()), level.getGameTime());
            state.setNextActionAt(level.getGameTime() + nextActionCooldown(dragon));
            sync(dragon);
        }

        state.tickMode();
        if (state.getBossStage() >= 2) {
            DragonPhaseTransition.tickStageTwoAura(level, dragon);
        }
        switch (state.getMode()) {
            case FLYING -> tickFlying(level, dragon, state);
            case DIVING -> tickDiving(level, dragon, state);
            case LANDING -> tickLanding(level, dragon, state);
            case GROUNDED -> tickGrounded(level, dragon, state);
            case TAKEOFF -> tickTakeoff(level, dragon, state);
            case PHASE_TRANSITION -> DragonPhaseTransition.tick(level, dragon, state);
            case STAGE_THREE_TRANSITION -> DragonStageThreeTransition.tick(level, dragon, state);
        }
    }

    public static boolean interceptIncomingDamage(EnderDragon dragon, float amount) {
        if (!(dragon.level() instanceof ServerLevel level) || !isOfficialDragon(dragon) || dragon.isDeadOrDying()) {
            return false;
        }
        DragonBossState state = dragon.getData(ModAttachments.DRAGON_BOSS_STATE);
        return state.isInvulnerable() || state.getMode() == DragonBossMode.PHASE_TRANSITION;
    }

    public static boolean interceptFinalDamage(EnderDragon dragon, float amount) {
        if (!(dragon.level() instanceof ServerLevel level) || !isOfficialDragon(dragon) || dragon.isDeadOrDying()) {
            return false;
        }
        DragonBossState state = dragon.getData(ModAttachments.DRAGON_BOSS_STATE);
        if (state.getBossStage() == 1 && state.getMode() != DragonBossMode.PHASE_TRANSITION
                && amount > 0.0F && dragon.getHealth() - amount <= 1.0F) {
            dragon.setHealth(1.0F);
            DragonPhaseTransition.begin(level, dragon, state);
            return true;
        }
        return false;
    }

    public static void recordDamage(EnderDragon dragon, float finalDamage) {
        if (!isOfficialDragon(dragon) || dragon.isDeadOrDying()) {
            return;
        }

        DragonBossState state = dragon.getData(ModAttachments.DRAGON_BOSS_STATE);
        if (state.getBossStage() >= 2 && STAGE_TWO_GRAB_CARRY.equals(state.getActionId()) && finalDamage > 0.0F) {
            GRAB_HIT_COUNTS.merge(dragon.getUUID(), 1, Integer::sum);
        }
        if (state.getBossStage() == 2
                && state.getMode() == DragonBossMode.GROUNDED
                && state.getStageTwoGroundedCount() == 1
                && !DragonGroundActions.WIND_PRESSURE.equals(state.getActionId())) {
            state.addStageTwoFirstGroundDamage(finalDamage);
        }
        if (state.isInitialized() && state.getMode() == DragonBossMode.FLYING) {
            state.addFlightDamage(finalDamage);
        }
    }

    /**
     * Called from the phase-manager mixin. Invalid high-level transitions are redirected without replacing the
     * phase manager or any vanilla phase implementation.
     */
    public static EnderDragonPhase<?> filterPhaseTransition(EnderDragon dragon, EnderDragonPhase<?> requested) {
        if (!(dragon.level() instanceof ServerLevel) || requested == EnderDragonPhase.DYING || !isOfficialDragon(dragon)) {
            return requested;
        }

        DragonBossState state = dragon.getExistingDataOrNull(ModAttachments.DRAGON_BOSS_STATE.get());
        if (state == null || !state.isInitialized() || dragon.isDeadOrDying()) {
            return requested;
        }

        EnderDragonPhase<?> current = dragon.getPhaseManager().getCurrentPhase().getPhase();
        return switch (state.getMode()) {
            case FLYING -> {
                if (LOW_BREATH_PASS.equals(state.getActionId())) {
                    yield requested == EnderDragonPhase.CHARGING_PLAYER ? requested : current;
                }
                if (METEOR_VOLLEY.equals(state.getActionId())) {
                    yield requested == EnderDragonPhase.HOLDING_PATTERN ? requested : current;
                }
                if (STAGE_TWO_SUMMON_MINIONS.equals(state.getActionId())) {
                    yield requested == EnderDragonPhase.HOLDING_PATTERN ? requested : current;
                }
                yield isGroundSequence(requested) ? EnderDragonPhase.HOLDING_PATTERN : requested;
            }
            case DIVING -> requested == EnderDragonPhase.CHARGING_PLAYER || requested == EnderDragonPhase.HOVERING
                    ? requested
                    : current;
            case LANDING -> requested == EnderDragonPhase.CHARGING_PLAYER ? requested : current;
            case GROUNDED -> EnderDragonPhase.SITTING_SCANNING;
            case TAKEOFF -> requested == EnderDragonPhase.TAKEOFF || requested == EnderDragonPhase.HOLDING_PATTERN
                    ? requested
                    : current;
            case PHASE_TRANSITION -> requested == EnderDragonPhase.HOVERING
                    || requested == EnderDragonPhase.CHARGING_PLAYER ? requested : current;
            case STAGE_THREE_TRANSITION -> requested == EnderDragonPhase.SITTING_SCANNING
                    || requested == EnderDragonPhase.HOVERING ? requested : current;
        };
    }

    private static void tickFlying(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        if (STAGE_TWO_GRAB_DIVE.equals(state.getActionId()) || STAGE_TWO_GRAB_CARRY.equals(state.getActionId())) {
            tickDiving(level, dragon, state);
            return;
        }
        if (LOW_BREATH_PASS.equals(state.getActionId())) {
            tickLowBreathPass(level, dragon, state);
            return;
        }
        if (METEOR_VOLLEY.equals(state.getActionId())) {
            tickMeteorVolley(level, dragon, state);
            return;
        }
        if (STAGE_TWO_SUMMON_MINIONS.equals(state.getActionId())) {
            tickSummonMinions(level, dragon, state);
            return;
        }

        boolean flightTimedOut = state.getModeTicks() >= MAX_FLIGHT_TICKS;
        boolean damageThresholdReached = state.getFlightDamageTaken() >= dragon.getMaxHealth() * FLIGHT_DAMAGE_THRESHOLD;
        if (flightTimedOut || damageThresholdReached) {
            Player target = chooseCombatPlayer(level, dragon);
            if (target != null) {
                beginDive(level, dragon, state, target);
                return;
            }
        }

        if (!DragonBossState.NO_ACTION.equals(state.getActionId())) {
            finishFlyingAction(level, dragon, state, false);
            return;
        }

        if (state.getNextActionAt() == 0L) {
            state.setNextActionAt(level.getGameTime() + nextActionCooldown(dragon));
            sync(dragon);
        } else if (level.getGameTime() >= state.getNextActionAt() && canStartRegularFlyingAction(dragon)) {
            startRegularFlyingAction(level, dragon, state);
        }
    }

    private static boolean canStartRegularFlyingAction(EnderDragon dragon) {
        EnderDragonPhase<?> phase = dragon.getPhaseManager().getCurrentPhase().getPhase();
        return phase == EnderDragonPhase.HOLDING_PATTERN || phase == EnderDragonPhase.HOVERING;
    }

    private static void startRegularFlyingAction(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        List<Player> players = getCombatPlayers(level, dragon);
        if (players.isEmpty()) {
            state.setNextActionAt(level.getGameTime() + 20);
            return;
        }

        List<Player> lowPassPlayers = getSafeLowPassPlayers(level, players);
        List<String> choices = new ArrayList<>();
        if (!lowPassPlayers.isEmpty()) {
            choices.add(LOW_BREATH_PASS);
        }
        choices.add(METEOR_VOLLEY);
        if (state.getBossStage() >= 2) {
            choices.add(STAGE_TWO_GRAB_DIVE);
            choices.add(STAGE_TWO_SUMMON_MINIONS);
        }
        String selected = choices.get(dragon.getRandom().nextInt(choices.size()));
        if (choices.size() > 1 && state.getConsecutiveRegularActions() >= 2
                && selected.equals(state.getLastRegularActionId())) {
            choices.remove(selected);
            selected = choices.get(dragon.getRandom().nextInt(choices.size()));
        }

        state.recordRegularAction(selected);
        switch (selected) {
            case LOW_BREATH_PASS -> beginLowBreathPass(level, dragon, state, choosePlayer(dragon, lowPassPlayers));
            case STAGE_TWO_GRAB_DIVE -> beginGrabDive(level, dragon, state, choosePlayer(dragon, players));
            case STAGE_TWO_SUMMON_MINIONS -> beginSummonMinions(level, dragon, state);
            default -> beginMeteorVolley(level, dragon, state);
        }
    }

    private static void beginLowBreathPass(ServerLevel level, EnderDragon dragon, DragonBossState state, Player target) {
        state.beginAction(LOW_BREATH_PASS, level.getGameTime());
        state.setDiveTarget(target.getUUID(), target.getId());
        state.setActionDirection(null);
        sync(dragon);
        updateLowPassApproach(level, dragon, target);
    }

    private static void tickLowBreathPass(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        int actionTicks = actionTicks(level, state);
        int passTicks = (int)Math.max(0L, level.getGameTime() - state.getActionStepStartedAt());
        if (state.getActionDirection() != null && passTicks >= LOW_PASS_DURATION) {
            finishFlyingAction(level, dragon, state, true);
            return;
        }

        Player target = findSelectedPlayer(level, dragon, state);
        if (target == null || !isSafeLowPassTarget(level, target)) {
            List<Player> safePlayers = getSafeLowPassPlayers(level, getCombatPlayers(level, dragon));
            target = safePlayers.isEmpty() ? null : choosePlayer(dragon, safePlayers);
        }
        if (target == null) {
            finishFlyingAction(level, dragon, state, true);
            return;
        }

        if (state.getActionDirection() == null) {
            state.setDiveTarget(target.getUUID(), target.getId());
            updateLowPassApproach(level, dragon, target);
            if (isLowPassApproachReady(level, dragon, target)) {
                aimLowPassAtPlayer(dragon, state, target);
                state.restartActionStep(level.getGameTime());
                sync(dragon);
            } else if (actionTicks >= LOW_PASS_MAX_APPROACH_TICKS) {
                finishFlyingAction(level, dragon, state, true);
                return;
            } else {
                return;
            }
        }

        passTicks = (int)Math.max(0L, level.getGameTime() - state.getActionStepStartedAt());
        if (passTicks > 0 && passTicks % LOW_PASS_SEGMENT_TICKS == 0) {
            state.setDiveTarget(target.getUUID(), target.getId());
            aimLowPassAtPlayer(dragon, state, target);
            sync(dragon);
        }

        if (!updateLowPassFlightTarget(level, dragon, state)) {
            finishFlyingAction(level, dragon, state, true);
            return;
        }
        emitForwardBreath(level, dragon, state, actionTicks);
    }

    private static boolean isLowPassApproachReady(ServerLevel level, EnderDragon dragon, Player target) {
        Vec3 surface = findSafeGroundAt(level, dragon.blockPosition());
        if (surface == null) {
            return false;
        }
        double clearance = dragon.getBoundingBox().minY - surface.y;
        double dx = dragon.getX() - target.getX();
        double dz = dragon.getZ() - target.getZ();
        return clearance > 4.62 && clearance <= 5.4 && dx * dx + dz * dz <= 32.0 * 32.0;
    }

    private static void updateLowPassApproach(ServerLevel level, EnderDragon dragon, Player target) {
        Vec3 surface = findSafeGroundAt(level, target.blockPosition());
        if (surface == null) {
            return;
        }
        Vec3 targetPosition = new Vec3(target.getX(), surface.y + LOW_PASS_CLEARANCE, target.getZ());
        setChargeTarget(dragon, targetPosition);
        Vec3 direction = targetPosition.subtract(dragon.position());
        if (direction.lengthSqr() > 1.0E-4) {
            dragon.setDeltaMovement(direction.normalize().scale(LOW_PASS_APPROACH_SPEED));
        }
    }

    private static void aimLowPassAtPlayer(EnderDragon dragon, DragonBossState state, Player target) {
        Vec3 direction = new Vec3(target.getX() - dragon.getX(), 0.0, target.getZ() - dragon.getZ());
        state.setActionDirection(direction.lengthSqr() < 1.0E-4 ? horizontalLook(target) : direction.normalize());
    }

    private static boolean updateLowPassFlightTarget(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        Vec3 direction = state.getActionDirection();
        if (direction == null || direction.lengthSqr() < 1.0E-4) {
            direction = dragonForward(dragon);
            state.setActionDirection(direction);
        }

        Vec3 heightProbe = dragon.position().add(direction.scale(8.0));
        Vec3 surface = findSafeGroundAt(level, BlockPos.containing(heightProbe));
        if (surface == null) {
            return false;
        }
        double currentClearance = dragon.getBoundingBox().minY - surface.y;
        double verticalCorrection = Mth.clamp((LOW_PASS_CLEARANCE - currentClearance) * 0.22, -0.35, 0.35);
        Vec3 flyTarget = new Vec3(
                dragon.getX() + direction.x * 80.0,
                surface.y + LOW_PASS_CLEARANCE,
                dragon.getZ() + direction.z * 80.0
        );
        setChargeTarget(dragon, flyTarget);
        dragon.setDeltaMovement(direction.x * LOW_PASS_ATTACK_SPEED, verticalCorrection, direction.z * LOW_PASS_ATTACK_SPEED);
        return true;
    }

    private static void emitForwardBreath(ServerLevel level, EnderDragon dragon, DragonBossState state, int actionTicks) {
        Vec3 direction = state.getActionDirection();
        if (direction == null) {
            return;
        }

        Vec3 groundTarget = findSafeGroundAt(level, BlockPos.containing(dragon.position().add(direction.scale(6.0))));
        if (groundTarget == null) {
            return;
        }
        Vec3 mouth = new Vec3(dragon.head.getX(), dragon.head.getY(0.5), dragon.head.getZ());
        Vec3 breathEnd = groundTarget.add(0.0, 0.25, 0.0);
        for (int i = 1; i <= 5; i++) {
            Vec3 sample = mouth.lerp(breathEnd, i / 5.0);
            level.sendParticles(ParticleTypes.DRAGON_BREATH, sample.x, sample.y, sample.z, 8, 0.9, 0.3, 0.9, 0.035);
        }

        if (actionTicks % BREATH_CLOUD_INTERVAL == 0) {
            spawnBreathCloud(level, dragon, breathEnd);
        }
    }

    private static void spawnBreathCloud(ServerLevel level, EnderDragon dragon, Vec3 position) {
        AreaEffectCloud cloud = new AreaEffectCloud(level, position.x, position.y, position.z);
        cloud.setOwner(dragon);
        cloud.setParticle(ParticleTypes.DRAGON_BREATH);
        cloud.setRadius(2.5F);
        cloud.setWaitTime(0);
        cloud.setDuration(BREATH_CLOUD_DURATION);
        cloud.addEffect(new MobEffectInstance(MobEffects.HARM, 1, 1));
        level.addFreshEntity(cloud);
    }

    private static void beginMeteorVolley(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        state.beginAction(METEOR_VOLLEY, level.getGameTime());
        state.setDiveTarget(null, -1);
        prepareMeteorWave(level, dragon, state);
        sync(dragon);
        dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
        level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 5.0F, 0.7F);
    }

    private static void tickMeteorVolley(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        int actionTicks = actionTicks(level, state);
        if (actionTicks >= METEOR_ACTION_DURATION) {
            finishFlyingAction(level, dragon, state, false);
            return;
        }

        if (actionTicks > 0 && actionTicks % METEOR_WAVE_INTERVAL == 0
                && actionTicks / METEOR_WAVE_INTERVAL < METEOR_WAVES) {
            prepareMeteorWave(level, dragon, state);
            sync(dragon);
        }

        int waveTicks = actionTicks % METEOR_WAVE_INTERVAL;
        if (waveTicks < METEOR_WARNING_TICKS) {
            showMeteorWarnings(level, dragon, state, waveTicks);
        } else if (waveTicks == METEOR_WARNING_TICKS) {
            impactMeteors(level, dragon, state);
            state.clearActionPositions();
            sync(dragon);
        }
    }

    private static void prepareMeteorWave(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        List<Player> players = getCombatPlayers(level, dragon);
        List<Vec3> positions = new ArrayList<>();
        List<MeteorPayload> payloads = new ArrayList<>();
        for (Player player : players) {
            Vec3 position = findGroundAt(level, player.blockPosition());
            positions.add(position);
            if (state.getBossStage() >= 2) {
                payloads.add(new MeteorPayload(position, player.getUUID(), dragon.getRandom().nextFloat() < 0.30F));
            }
        }
        state.setActionPositions(positions);
        if (state.getBossStage() >= 2) {
            STAGE_TWO_METEORS.put(dragon.getUUID(), payloads);
        } else {
            STAGE_TWO_METEORS.remove(dragon.getUUID());
        }
    }

    private static void showMeteorWarnings(ServerLevel level, EnderDragon dragon, DragonBossState state, int waveTicks) {
        double descent = 30.0 * (1.0 - waveTicks / (double)METEOR_WARNING_TICKS);
        List<MeteorPayload> payloads = STAGE_TWO_METEORS.get(dragon.getUUID());
        List<Vec3> targets = state.getActionPositions();
        for (int index = 0; index < targets.size(); index++) {
            Vec3 target = targets.get(index);
            if (waveTicks % 2 == 0) {
                level.sendParticles(METEOR_WARNING_PARTICLE, target.x, target.y + 0.08, target.z, 18, 1.35, 0.03, 1.35, 0.0);
            }
            double meteorY = target.y + Math.max(1.0, descent);
            boolean enderVariant = payloads != null && index < payloads.size() && payloads.get(index).enderVariant();
            if (state.getBossStage() >= 2) {
                level.sendParticles(enderVariant ? ParticleTypes.PORTAL : ParticleTypes.SOUL_FIRE_FLAME,
                        target.x, meteorY, target.z, enderVariant ? 14 : 8, 0.5, 0.9, 0.5, enderVariant ? 0.12 : 0.025);
                if (enderVariant) {
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, target.x, meteorY, target.z, 6, 0.35, 0.6, 0.35, 0.08);
                } else {
                    level.sendParticles(ParticleTypes.SOUL, target.x, meteorY + 0.4, target.z, 4, 0.35, 0.6, 0.35, 0.02);
                }
            } else {
                level.sendParticles(ParticleTypes.FLAME, target.x, meteorY, target.z, 6, 0.45, 0.8, 0.45, 0.02);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, target.x, meteorY + 0.5, target.z, 3, 0.35, 0.7, 0.35, 0.01);
            }
        }
    }

    private static void impactMeteors(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        List<MeteorPayload> payloads = STAGE_TWO_METEORS.remove(dragon.getUUID());
        List<Vec3> targets = state.getActionPositions();
        for (int index = 0; index < targets.size(); index++) {
            Vec3 target = targets.get(index);
            List<Player> soulExtractionTargets = List.of();
            if (state.getBossStage() >= 2 && payloads != null && index < payloads.size()
                    && !payloads.get(index).enderVariant()) {
                AABB possibleDamageArea = new AABB(target, target).inflate(6.0);
                soulExtractionTargets = level.getEntitiesOfClass(Player.class, possibleDamageArea,
                        player -> dragon.canAttack(player) && player.position().distanceToSqr(target) <= 36.0);
            }
            level.explode(dragon, target.x, target.y, target.z, 3.0F, Level.ExplosionInteraction.MOB);
            if (state.getBossStage() < 2 || payloads == null || index >= payloads.size()) {
                continue;
            }

            MeteorPayload payload = payloads.get(index);
            Player selectedPlayer = level.getPlayerByUUID(payload.targetUuid());
            if (payload.enderVariant()) {
                if (selectedPlayer != null && selectedPlayer.isAlive()) {
                    boolean stageThreeSummon = state.getBossStage() >= 3;
                    DragonMinionHelper.spawnHostileEnderman(level, selectedPlayer, target,
                            stageThreeSummon || dragon.getRandom().nextBoolean(), stageThreeSummon);
                }
            } else {
                for (Player hitPlayer : soulExtractionTargets) {
                    hitPlayer.addEffect(new MobEffectInstance(AshenEnderDragon.SOUL_EXTRACTION, 5 * 20, 0), dragon);
                }
            }
        }
    }

    private static void finishFlyingAction(ServerLevel level, EnderDragon dragon, DragonBossState state, boolean returnToHolding) {
        STAGE_TWO_METEORS.remove(dragon.getUUID());
        state.clearAction(level.getGameTime());
        state.setDiveTarget(null, -1);
        state.setNextActionAt(level.getGameTime() + nextActionCooldown(dragon));
        sync(dragon);
        if (returnToHolding) {
            dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
        }
    }

    private static void beginSummonMinions(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        state.beginAction(STAGE_TWO_SUMMON_MINIONS, level.getGameTime());
        state.setDiveTarget(null, -1);
        sync(dragon);
        dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
        level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(), SoundEvents.ENDER_DRAGON_GROWL,
                SoundSource.HOSTILE, 5.0F, 0.62F);
    }

    private static void tickSummonMinions(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        int ticks = actionTicks(level, state);
        if (ticks >= MINION_SUMMON_DURATION_TICKS) {
            finishFlyingAction(level, dragon, state, false);
            return;
        }

        if (ticks < MINION_SUMMON_WINDUP_TICKS && ticks % 2 == 0) {
            for (Player player : getCombatPlayers(level, dragon)) {
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(),
                        14, 3.0, 1.2, 3.0, 0.08);
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 0.2, player.getZ(),
                        4, 2.5, 0.2, 2.5, 0.015);
            }
        }
        if (ticks == MINION_SUMMON_WINDUP_TICKS) {
            for (Player player : getCombatPlayers(level, dragon)) {
                double angle = dragon.getRandom().nextDouble() * Mth.TWO_PI;
                double distance = 4.0 + dragon.getRandom().nextDouble() * 2.0;
                BlockPos horizontal = BlockPos.containing(
                        player.getX() + Math.cos(angle) * distance,
                        player.getY(),
                        player.getZ() + Math.sin(angle) * distance
                );
                Vec3 spawn = findGroundAt(level, horizontal);
                boolean stageThreeSummon = state.getBossStage() >= 3;
                boolean carryingCreeper = stageThreeSummon || dragon.getRandom().nextFloat() < 0.10F;
                DragonMinionHelper.spawnHostileEnderman(level, player, spawn, carryingCreeper, carryingCreeper);
                level.sendParticles(ParticleTypes.PORTAL, spawn.x, spawn.y + 1.0, spawn.z,
                        80, 1.3, 1.8, 1.3, 0.16);
            }
        }
    }

    private static void beginGrabDive(ServerLevel level, EnderDragon dragon, DragonBossState state, Player target) {
        beginDive(level, dragon, state, target, STAGE_TWO_GRAB_DIVE);
    }

    private static void beginDive(ServerLevel level, EnderDragon dragon, DragonBossState state, Player target) {
        beginDive(level, dragon, state, target, LANDING_TRANSITION);
    }

    private static void beginDive(ServerLevel level, EnderDragon dragon, DragonBossState state, Player target, String actionId) {
        if (!STAGE_TWO_GRAB_DIVE.equals(actionId)) {
            state.setMode(DragonBossMode.DIVING, level.getGameTime());
        }
        state.beginAction(actionId, level.getGameTime());
        state.setDiveTarget(target.getUUID(), target.getId());
        state.setLandingTarget(findGroundAt(level, target.blockPosition()));
        state.setActionDirection(horizontalLook(target));
        state.clearActionPositions();
        state.setInvulnerable(false);
        sync(dragon);

        dragon.setNoAi(false);
        dragon.setInvulnerable(false);
        dragon.setInvisible(false);
        setChargeTarget(dragon, landingFrontPoint(level, target));
    }

    private static void tickDiving(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        if (STAGE_TWO_GRAB_CARRY.equals(state.getActionId())) {
            tickGrabCarry(level, dragon, state);
            return;
        }

        boolean diveTransition = isDiveTransition(state.getActionId());
        if (diveTransition && isApproachingLandingVanish(state)) {
            tickLandingVanishApproach(level, dragon, state);
            return;
        }

        int actionStepTicks = (int)Math.max(0L, level.getGameTime() - state.getActionStepStartedAt());
        if (diveTransition && state.isInvulnerable() && actionStepTicks < LANDING_VANISH_TICKS) {
            dragon.setNoAi(true);
            dragon.setDeltaMovement(Vec3.ZERO);
            if (dragon.getPhaseManager().getCurrentPhase().getPhase() != EnderDragonPhase.HOVERING) {
                dragon.getPhaseManager().setPhase(EnderDragonPhase.HOVERING);
            }
            if (state.getModeTicks() % 4 == 0 && !state.getActionPositions().isEmpty()) {
                Vec3 vanishedAt = state.getActionPositions().getFirst();
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, vanishedAt.x, vanishedAt.y + 2.0, vanishedAt.z, 30, 4.0, 2.5, 4.0, 0.08);
            }
            return;
        }

        if (diveTransition
                && (state.isInvulnerable() || dragon.isInvisible() || !state.getActionPositions().isEmpty())) {
            reappearForDive(level, dragon, state);
        }

        Vec3 landingTarget = getOrCreateLandingTarget(level, dragon, state);
        setChargeTarget(dragon, landingTarget);
        Vec3 diveDirection = state.getActionDirection();
        if (diveDirection != null && diveDirection.lengthSqr() > 1.0E-4) {
            dragon.setDeltaMovement(diveDirection.normalize().scale(LANDING_DIVE_SPEED));
            faceDragonDirectionImmediately(dragon, diveDirection);
        }
        int activeDiveTicks = (int)Math.max(0L, level.getGameTime() - state.getActionStepStartedAt());
        if (STAGE_TWO_GRAB_DIVE.equals(state.getActionId())) {
            Player target = findSelectedPlayer(level, dragon, state);
            if (target != null && grabDiveTouchesTarget(dragon, target)) {
                beginGrabCarry(level, dragon, state, target);
                return;
            }
            if (dragon.position().distanceToSqr(landingTarget) <= LANDING_APPROACH_DISTANCE_SQUARED
                    || activeDiveTicks >= MAX_DIVE_TICKS) {
                finishGrabAction(level, dragon, state, null, false);
            }
            return;
        }
        if (dragon.position().distanceToSqr(landingTarget) <= LANDING_APPROACH_DISTANCE_SQUARED
                || activeDiveTicks >= MAX_DIVE_TICKS) {
            beginLanding(level, dragon, state, landingTarget);
        }
    }

    private static boolean grabDiveTouchesTarget(EnderDragon dragon, Player target) {
        AABB targetBox = target.getBoundingBox().inflate(0.75);
        return dragon.head.getBoundingBox().inflate(1.75).intersects(targetBox)
                || dragon.getBoundingBox().inflate(0.75).intersects(targetBox);
    }

    private static boolean isDiveTransition(String actionId) {
        return LANDING_TRANSITION.equals(actionId) || STAGE_TWO_GRAB_DIVE.equals(actionId);
    }

    private static void beginGrabCarry(ServerLevel level, EnderDragon dragon, DragonBossState state, Player target) {
        target.stopRiding();
        if (!target.startRiding(dragon, true)) {
            finishGrabAction(level, dragon, state, null, false);
            return;
        }

        Vec3 ground = findGroundAt(level, dragon.blockPosition());
        double targetY = Math.min(level.getMaxBuildHeight() - 24.0,
                Math.max(dragon.getY() + 24.0, ground.y + GRAB_CLIMB_HEIGHT));
        Vec3 climbTarget = new Vec3(dragon.getX(), targetY, dragon.getZ());
        state.beginAction(STAGE_TWO_GRAB_CARRY, level.getGameTime());
        state.setDiveTarget(target.getUUID(), target.getId());
        state.setActionPositions(List.of(climbTarget));
        state.setActionDirection(new Vec3(0.0, 1.0, 0.0));
        GRAB_HIT_COUNTS.put(dragon.getUUID(), 0);
        sync(dragon);
        setChargeTarget(dragon, climbTarget);
        level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(), SoundEvents.ENDER_DRAGON_GROWL,
                SoundSource.HOSTILE, 5.0F, 0.82F);
    }

    private static void tickGrabCarry(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        UUID targetUuid = state.getDiveTargetUuid();
        Player target = targetUuid == null ? null : level.getPlayerByUUID(targetUuid);
        if (target == null || !target.isAlive() || target.distanceToSqr(dragon) > TARGET_RANGE_SQUARED) {
            finishGrabAction(level, dragon, state, null, false);
            return;
        }
        if (!target.isPassengerOfSameVehicle(dragon)) {
            target.startRiding(dragon, true);
        }

        int carryTicks = (int)Math.max(0L, level.getGameTime() - state.getActionStartedAt());
        int receivedHits = GRAB_HIT_COUNTS.getOrDefault(dragon.getUUID(), 0);
        if (receivedHits >= GRAB_BREAK_HITS) {
            finishGrabAction(level, dragon, state, target, false, true);
            return;
        }
        if (carryTicks >= GRAB_CARRY_TICKS) {
            finishGrabAction(level, dragon, state, target, true);
            return;
        }

        Vec3 climbTarget = state.getActionPositions().isEmpty()
                ? dragon.position().add(0.0, GRAB_CLIMB_HEIGHT, 0.0)
                : state.getActionPositions().getFirst();
        setChargeTarget(dragon, climbTarget);
        Vec3 climb = climbTarget.subtract(dragon.position());
        if (climb.lengthSqr() > 16.0) {
            dragon.setDeltaMovement(climb.normalize().scale(1.05));
        } else {
            dragon.setDeltaMovement(Vec3.ZERO);
            dragon.getPhaseManager().setPhase(EnderDragonPhase.HOVERING);
        }

        if (carryTicks % 3 == 0) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), target.getY() + 0.8, target.getZ(),
                    10, 0.7, 1.0, 0.7, 0.06);
        }
    }

    private static void finishGrabAction(ServerLevel level, EnderDragon dragon, DragonBossState state,
            @Nullable Player target, boolean applyEffects) {
        finishGrabAction(level, dragon, state, target, applyEffects, false);
    }

    private static void finishGrabAction(ServerLevel level, EnderDragon dragon, DragonBossState state,
            @Nullable Player target, boolean applyEffects, boolean interruptedByPlayers) {
        GRAB_HIT_COUNTS.remove(dragon.getUUID());
        if (target != null) {
            target.stopRiding();
            if (applyEffects && target.isAlive()) {
                target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 10 * 20, 0), dragon);
                target.addEffect(new MobEffectInstance(AshenEnderDragon.SOUL_EXTRACTION, 10 * 20, 0), dragon);
            }
            if (interruptedByPlayers) {
                teleportInterruptedGrabTarget(level, target, state.getLandingTarget());
            } else {
                Vec3 forward = dragonForward(dragon);
                target.setDeltaMovement(forward.x * 0.35, -0.15, forward.z * 0.35);
            }
            target.hurtMarked = true;
        }

        // Grab is a regular flying action. Do not reset the 90-second flight timer or its damage threshold.
        if (state.getMode() != DragonBossMode.FLYING) {
            // Recovery path for an in-progress grab saved by an older build.
            state.setMode(DragonBossMode.FLYING, level.getGameTime());
        }
        state.clearAction(level.getGameTime());
        state.setDiveTarget(null, -1);
        state.setLandingTarget(null);
        state.setInvulnerable(false);
        state.setNextActionAt(level.getGameTime() + nextActionCooldown(dragon));
        dragon.setNoAi(false);
        dragon.setInvisible(false);
        dragon.setInvulnerable(false);
        dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
        sync(dragon);
    }

    private static void teleportInterruptedGrabTarget(ServerLevel level, Player target, @Nullable Vec3 fallback) {
        Vec3 origin = target.position();
        for (int attempt = 0; attempt < 16; attempt++) {
            double x = origin.x + (target.getRandom().nextDouble() - 0.5) * 24.0;
            double z = origin.z + (target.getRandom().nextDouble() - 0.5) * 24.0;
            Vec3 ground = findSafeGroundAt(level, BlockPos.containing(x, origin.y, z));
            if (ground != null && target.randomTeleport(ground.x, ground.y, ground.z, true)) {
                target.setDeltaMovement(Vec3.ZERO);
                target.fallDistance = 0.0F;
                level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.CHORUS_FRUIT_TELEPORT,
                        SoundSource.PLAYERS, 1.0F, 1.0F);
                target.playSound(SoundEvents.CHORUS_FRUIT_TELEPORT, 1.0F, 1.0F);
                return;
            }
        }
        if (fallback != null) {
            target.teleportTo(fallback.x, fallback.y, fallback.z);
            target.setDeltaMovement(Vec3.ZERO);
            target.fallDistance = 0.0F;
            level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.CHORUS_FRUIT_TELEPORT,
                    SoundSource.PLAYERS, 1.0F, 1.0F);
            target.playSound(SoundEvents.CHORUS_FRUIT_TELEPORT, 1.0F, 1.0F);
        }
    }

    private static boolean isApproachingLandingVanish(DragonBossState state) {
        Vec3 direction = state.getActionDirection();
        return !state.isInvulnerable()
                && state.getActionPositions().isEmpty()
                && (direction == null || Math.abs(direction.y) < 1.0E-3);
    }

    private static void tickLandingVanishApproach(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        Player target = findSelectedPlayer(level, dragon, state);
        if (target == null) {
            target = chooseCombatPlayer(level, dragon);
            if (target != null) {
                state.setDiveTarget(target.getUUID(), target.getId());
            }
        }

        Vec3 front = target == null ? dragon.position() : landingFrontPoint(level, target);
        if (target != null) {
            Vec3 facing = horizontalLook(target);
            state.setLandingTarget(findGroundAt(level, target.blockPosition()));
            state.setActionDirection(facing);
            setChargeTarget(dragon, front);
        }

        int approachTicks = (int)Math.max(0L, level.getGameTime() - state.getActionStartedAt());
        if (dragon.position().distanceToSqr(front) <= LANDING_FRONT_REACHED_DISTANCE_SQUARED
                || approachTicks >= LANDING_FRONT_APPROACH_TICKS) {
            beginLandingVanish(level, dragon, state);
        }
    }

    private static void beginLandingVanish(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        Vec3 vanishedAt = dragon.position();
        state.setActionPositions(List.of(vanishedAt));
        state.setInvulnerable(true);
        state.restartActionStep(level.getGameTime());
        sync(dragon);

        emitTeleportBurst(level, vanishedAt, true);
        dragon.setNoAi(true);
        dragon.setInvulnerable(true);
        dragon.setInvisible(true);
        dragon.setDeltaMovement(Vec3.ZERO);
        dragon.getPhaseManager().setPhase(EnderDragonPhase.HOVERING);
    }

    private static Vec3 landingFrontPoint(ServerLevel level, Player target) {
        Vec3 facing = horizontalLook(target);
        Vec3 horizontal = target.position().add(facing.scale(LANDING_FRONT_DISTANCE));
        Vec3 surface = findGroundAt(level, BlockPos.containing(horizontal));
        double y = Math.max(target.getY() + LANDING_FRONT_HEIGHT, surface.y + LANDING_FRONT_HEIGHT);
        return new Vec3(horizontal.x, y, horizontal.z);
    }

    private static void reappearForDive(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        Player target = findSelectedPlayer(level, dragon, state);
        Vec3 facing = state.getActionDirection();
        if (facing == null || facing.lengthSqr() < 1.0E-4) {
            facing = target != null ? horizontalLook(target) : horizontalLook(dragon);
        }

        Vec3 landingTarget = state.getLandingTarget();
        Vec3 playerPosition = landingTarget == null ? dragon.position() : landingTarget;
        if (target != null) {
            landingTarget = findGroundAt(level, target.blockPosition());
            playerPosition = target.position();
            facing = horizontalLook(target);
            state.setDiveTarget(target.getUUID(), target.getId());
            state.setLandingTarget(landingTarget);
            state.setActionDirection(facing);
        } else if (landingTarget == null) {
            landingTarget = findGroundAt(level, dragon.blockPosition());
            state.setLandingTarget(landingTarget);
        }

        Vec3 behind = playerPosition.subtract(facing.scale(LANDING_BEHIND_DISTANCE));
        Vec3 behindSurface = findGroundAt(level, BlockPos.containing(behind));
        double spawnY = Math.max(playerPosition.y + LANDING_SPAWN_HEIGHT, behindSurface.y + LANDING_SPAWN_HEIGHT);
        Vec3 spawnPosition = new Vec3(behind.x, spawnY, behind.z);
        Vec3 diveDirection = landingTarget.subtract(spawnPosition).normalize();

        dragon.teleportTo(spawnPosition.x, spawnPosition.y, spawnPosition.z);
        dragon.setDeltaMovement(diveDirection.scale(LANDING_DIVE_SPEED));
        dragon.setNoAi(false);
        dragon.setInvisible(false);
        dragon.setInvulnerable(false);
        state.setInvulnerable(false);
        state.setActionDirection(diveDirection);
        state.clearActionPositions();
        state.restartActionStep(level.getGameTime());
        sync(dragon);

        emitTeleportBurst(level, spawnPosition, false);
        faceDragonDirectionImmediately(dragon, diveDirection);
        setChargeTarget(dragon, landingTarget);
    }

    private static void emitTeleportBurst(ServerLevel level, Vec3 position, boolean dissolving) {
        level.sendParticles(ParticleTypes.PORTAL, position.x, position.y + 2.0, position.z, 520, 7.0, 4.5, 7.0, 0.5);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, position.x, position.y + 2.0, position.z, 320, 6.0, 4.0, 6.0, 0.28);
        level.sendParticles(ParticleTypes.DRAGON_BREATH, position.x, position.y + 2.0, position.z, dissolving ? 260 : 180, 5.5, 3.5, 5.5, 0.14);
        level.sendParticles(ParticleTypes.POOF, position.x, position.y + 2.0, position.z, 140, 5.0, 3.0, 5.0, 0.2);
        level.playSound(null, position.x, position.y, position.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 4.0F, dissolving ? 0.65F : 0.85F);
    }

    private static void beginLanding(ServerLevel level, EnderDragon dragon, DragonBossState state, Vec3 landingTarget) {
        state.setMode(DragonBossMode.LANDING, level.getGameTime());
        sync(dragon);
        setChargeTarget(dragon, landingTarget);
    }

    private static void tickLanding(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        Vec3 landingTarget = getOrCreateLandingTarget(level, dragon, state);
        setChargeTarget(dragon, landingTarget);

        double distanceSquared = dragon.position().distanceToSqr(landingTarget);
        Vec3 toTarget = landingTarget.subtract(dragon.position());
        Vec3 diveDirection = state.getActionDirection();
        boolean passedTarget = diveDirection != null && toTarget.dot(diveDirection) <= 0.0;
        boolean closeEnough = distanceSquared <= GROUND_CONTACT_DISTANCE_SQUARED;
        boolean blockedNearTarget = state.getModeTicks() >= MAX_LANDING_TICKS
                && distanceSquared <= LANDING_FALLBACK_DISTANCE_SQUARED;
        if (closeEnough || blockedNearTarget || passedTarget) {
            enterGrounded(level, dragon, state, landingTarget);
        } else if (diveDirection != null && diveDirection.lengthSqr() > 1.0E-4) {
            dragon.setDeltaMovement(diveDirection.normalize().scale(LANDING_DIVE_SPEED));
            faceDragonDirectionImmediately(dragon, diveDirection);
        }
    }

    private static void enterGrounded(ServerLevel level, EnderDragon dragon, DragonBossState state, Vec3 landingTarget) {
        state.setMode(DragonBossMode.GROUNDED, level.getGameTime());
        if (state.getBossStage() == 2) {
            state.incrementStageTwoGroundedCount();
            if (state.getStageTwoGroundedCount() == 1) {
                state.resetStageTwoFirstGroundDamage();
            }
        }
        ensureGroundResistance(dragon, state);
        state.clearAction(level.getGameTime());
        state.setDiveTarget(null, -1);
        state.setInvulnerable(false);
        DragonGroundActions.initialize(level, dragon, state);
        sync(dragon);

        dragon.setInvisible(false);
        dragon.setInvulnerable(false);
        dragon.setNoAi(false);
        dragon.teleportTo(landingTarget.x, landingTarget.y, landingTarget.z);
        dragon.setDeltaMovement(Vec3.ZERO);
        createLandingImpact(level, dragon, landingTarget);
        dragon.getPhaseManager().setPhase(EnderDragonPhase.SITTING_SCANNING);
    }

    private static void createLandingImpact(ServerLevel level, EnderDragon dragon, Vec3 impact) {
        AABB damageArea = new AABB(impact, impact).inflate(LANDING_IMPACT_RADIUS, 4.0, LANDING_IMPACT_RADIUS);
        for (Player player : level.getEntitiesOfClass(Player.class, damageArea, dragon::canAttack)) {
            if (distanceToSqr2D(player.position(), impact) <= LANDING_IMPACT_RADIUS * LANDING_IMPACT_RADIUS) {
                player.hurt(dragon.damageSources().mobAttack(dragon), LANDING_IMPACT_DAMAGE);
                player.knockback(1.8, impact.x - player.getX(), impact.z - player.getZ());
            }
        }

        BlockPos surfaceBlock = BlockPos.containing(impact).below();
        BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(surfaceBlock));
        level.sendParticles(debris, impact.x, impact.y + 0.25, impact.z, 180, 5.5, 0.6, 5.5, 0.45);
        level.sendParticles(ParticleTypes.CLOUD, impact.x, impact.y + 0.5, impact.z, 90, 5.0, 0.7, 5.0, 0.2);
        level.sendParticles(ParticleTypes.EXPLOSION, impact.x, impact.y + 0.5, impact.z, 24, 4.0, 0.8, 4.0, 0.12);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, impact.x, impact.y + 0.5, impact.z, 2, 1.0, 0.2, 1.0, 0.0);
        level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 4.0F, 0.65F);
    }

    private static void tickGrounded(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        ensureGroundResistance(dragon, state);
        if (state.getBossStage() == 2
                && state.getStageTwoGroundedCount() == 1
                && state.getStageTwoFirstGroundDamageTaken()
                        >= dragon.getMaxHealth() * STAGE_TWO_FIRST_GROUND_DAMAGE_THRESHOLD
                && !DragonGroundActions.WIND_PRESSURE.equals(state.getActionId())) {
            DragonGroundActions.beginDamageThresholdTakeoff(level, dragon, state);
        }
        DragonGroundActions.tick(level, dragon, state);
    }

    static void ensureGroundResistance(EnderDragon dragon, DragonBossState state) {
        if (!(dragon.level() instanceof ServerLevel level) || state.getBossStage() < 2) {
            return;
        }

        int resistanceLevel = Math.min(getCombatPlayers(level, dragon).size(), MAX_GROUND_RESISTANCE_LEVEL);
        MobEffectInstance resistance = dragon.getEffect(MobEffects.DAMAGE_RESISTANCE);
        if (resistanceLevel == 0) {
            if (isGroundResistance(resistance)) {
                dragon.removeEffect(MobEffects.DAMAGE_RESISTANCE);
            }
            return;
        }

        int desiredAmplifier = resistanceLevel - 1;
        if (isGroundResistance(resistance)) {
            if (resistance.getAmplifier() == desiredAmplifier) {
                return;
            }
            dragon.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        } else if (resistance != null && resistance.getAmplifier() >= desiredAmplifier) {
            return;
        }

        dragon.addEffect(new MobEffectInstance(
                MobEffects.DAMAGE_RESISTANCE,
                MobEffectInstance.INFINITE_DURATION,
                desiredAmplifier,
                false,
                false
        ));
    }

    static void removeGroundResistance(EnderDragon dragon) {
        MobEffectInstance resistance = dragon.getEffect(MobEffects.DAMAGE_RESISTANCE);
        if (isGroundResistance(resistance)) {
            dragon.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        }
    }

    private static boolean isGroundResistance(@Nullable MobEffectInstance resistance) {
        return resistance != null
                && resistance.getAmplifier() >= 0
                && resistance.getAmplifier() < MAX_GROUND_RESISTANCE_LEVEL
                && resistance.isInfiniteDuration()
                && !resistance.isAmbient()
                && !resistance.isVisible()
                && !resistance.showIcon();
    }

    public static int groundedDurationTicks(DragonBossState state) {
        if (state.getBossStage() != 2) {
            return MAX_GROUNDED_TICKS;
        }
        return state.getStageTwoGroundedCount() >= 2
                ? STAGE_TWO_SECOND_GROUNDED_TICKS
                : STAGE_TWO_MAX_GROUNDED_TICKS;
    }

    private static void tickTakeoff(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        if (dragon.getPhaseManager().getCurrentPhase().getPhase() == EnderDragonPhase.HOLDING_PATTERN) {
            state.setMode(DragonBossMode.FLYING, level.getGameTime());
            state.resetFlightDamage();
            state.setLandingTarget(null);
            state.setNextActionAt(level.getGameTime() + nextActionCooldown(dragon));
            sync(dragon);
        }
    }

    private static int actionTicks(ServerLevel level, DragonBossState state) {
        return (int)Math.max(0L, level.getGameTime() - state.getActionStartedAt());
    }

    private static int nextActionCooldown(EnderDragon dragon) {
        return MIN_ACTION_COOLDOWN + dragon.getRandom().nextInt(MAX_ACTION_COOLDOWN - MIN_ACTION_COOLDOWN + 1);
    }

    static List<Player> getCombatPlayers(ServerLevel level, EnderDragon dragon) {
        List<Player> players = new ArrayList<>();
        for (Player player : level.players()) {
            if (player.isAlive() && dragon.canAttack(player) && player.distanceToSqr(dragon) <= TARGET_RANGE_SQUARED) {
                players.add(player);
            }
        }
        return players;
    }

    private static List<Player> getSafeLowPassPlayers(ServerLevel level, List<Player> players) {
        List<Player> safePlayers = new ArrayList<>();
        for (Player player : players) {
            if (isSafeLowPassTarget(level, player)) {
                safePlayers.add(player);
            }
        }
        return safePlayers;
    }

    private static boolean isSafeLowPassTarget(ServerLevel level, Player player) {
        Vec3 surface = findSafeGroundAt(level, player.blockPosition());
        return surface != null && player.getY() >= surface.y - 3.0;
    }

    @Nullable
    static Player chooseCombatPlayer(ServerLevel level, EnderDragon dragon) {
        List<Player> players = getCombatPlayers(level, dragon);
        return players.isEmpty() ? null : choosePlayer(dragon, players);
    }

    static Player choosePlayer(EnderDragon dragon, List<Player> players) {
        return players.get(dragon.getRandom().nextInt(players.size()));
    }

    @Nullable
    static Player findSelectedPlayer(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        UUID targetUuid = state.getDiveTargetUuid();
        Player target = targetUuid == null ? null : level.getPlayerByUUID(targetUuid);
        return target != null && target.isAlive() && dragon.canAttack(target) && target.distanceToSqr(dragon) <= TARGET_RANGE_SQUARED
                ? target
                : null;
    }

    private static Vec3 getOrCreateLandingTarget(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        Vec3 landingTarget = state.getLandingTarget();
        if (landingTarget != null) {
            return landingTarget;
        }

        landingTarget = findGroundAt(level, dragon.blockPosition());
        state.setLandingTarget(landingTarget);
        sync(dragon);
        return landingTarget;
    }

    static Vec3 findGroundAt(ServerLevel level, BlockPos horizontalPosition) {
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, horizontalPosition);
        return Vec3.atBottomCenterOf(surface);
    }

    @Nullable
    private static Vec3 findSafeGroundAt(ServerLevel level, BlockPos horizontalPosition) {
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, horizontalPosition);
        if (surface.getY() <= level.getMinBuildHeight() || level.getBlockState(surface.below()).isAir()) {
            return null;
        }
        return Vec3.atBottomCenterOf(surface);
    }

    static Vec3 horizontalLook(net.minecraft.world.entity.Entity entity) {
        Vec3 look = entity.getLookAngle().multiply(1.0, 0.0, 1.0);
        if (look.lengthSqr() < 1.0E-4) {
            float yaw = entity.getYRot() * Mth.DEG_TO_RAD;
            return new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
        }
        return look.normalize();
    }

    static Vec3 dragonForward(EnderDragon dragon) {
        float yaw = dragon.getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(Mth.sin(yaw), 0.0, -Mth.cos(yaw)).normalize();
    }

    public static Vec3 dragonForwardForPassenger(EnderDragon dragon) {
        return dragonForward(dragon);
    }

    private static void faceDragonDirectionImmediately(EnderDragon dragon, Vec3 direction) {
        dragon.setYRot((float)Math.toDegrees(Math.atan2(direction.x, -direction.z)));
        dragon.yBodyRot = dragon.getYRot();
        dragon.yRotA = 0.0F;
    }

    private static double distanceToSqr2D(Vec3 first, Vec3 second) {
        double x = first.x - second.x;
        double z = first.z - second.z;
        return x * x + z * z;
    }

    private static void setChargeTarget(EnderDragon dragon, Vec3 target) {
        if (dragon.getPhaseManager().getCurrentPhase().getPhase() != EnderDragonPhase.CHARGING_PLAYER) {
            dragon.getPhaseManager().setPhase(EnderDragonPhase.CHARGING_PLAYER);
        }
        dragon.getPhaseManager().getPhase(EnderDragonPhase.CHARGING_PLAYER).setTarget(target);
    }

    private static DragonBossMode inferMode(DragonPhaseInstance phase) {
        EnderDragonPhase<?> vanillaPhase = phase.getPhase();
        if (vanillaPhase == EnderDragonPhase.LANDING_APPROACH || vanillaPhase == EnderDragonPhase.LANDING) {
            return DragonBossMode.LANDING;
        }
        if (vanillaPhase == EnderDragonPhase.TAKEOFF) {
            return DragonBossMode.TAKEOFF;
        }
        if (phase.isSitting()) {
            return DragonBossMode.GROUNDED;
        }
        return DragonBossMode.FLYING;
    }

    private static boolean isGroundSequence(EnderDragonPhase<?> phase) {
        return phase == EnderDragonPhase.LANDING_APPROACH || phase == EnderDragonPhase.LANDING || isSittingPhase(phase);
    }

    private static boolean isSittingPhase(EnderDragonPhase<?> phase) {
        return phase == EnderDragonPhase.SITTING_SCANNING
                || phase == EnderDragonPhase.SITTING_ATTACKING
                || phase == EnderDragonPhase.SITTING_FLAMING;
    }

    static void sync(EnderDragon dragon) {
        dragon.syncData(ModAttachments.DRAGON_BOSS_STATE);
    }

    public static boolean shouldSuppressVanillaGroundedAi(EnderDragon dragon) {
        if (!(dragon.level() instanceof ServerLevel) || !isOfficialDragon(dragon)) {
            return false;
        }
        DragonBossState state = dragon.getExistingDataOrNull(ModAttachments.DRAGON_BOSS_STATE.get());
        return state != null && state.isInitialized() && state.getMode() == DragonBossMode.GROUNDED;
    }
}
