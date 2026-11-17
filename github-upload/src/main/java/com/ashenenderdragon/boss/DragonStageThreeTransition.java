package com.ashenenderdragon.boss;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ashenenderdragon.AshenEnderDragon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Server-authoritative second-to-third-stage battlefield transition. */
public final class DragonStageThreeTransition {
    public static final String CATACLYSM = "phase_three_cataclysm";

    private static final int DURATION_TICKS = 35 * 20;
    private static final int SOUL_BEAM_WARNING_TICKS = 30;
    private static final int SOUL_BEAM_INTERVAL = 70;
    private static final int ENDER_BEAM_INTERVAL = 20;
    private static final int LIGHTNING_INTERVAL = 12;
    private static final float SOUL_BEAM_DAMAGE = 10.0F;
    private static final float ENDER_BEAM_DAMAGE = 6.0F;
    private static final double BEAM_HALF_WIDTH = 0.5;

    private static final Map<UUID, RuntimeState> ACTIVE = new HashMap<>();

    private DragonStageThreeTransition() {
    }

    public static void begin(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        DragonGroundActions.cleanup(dragon);
        state.setMode(DragonBossMode.STAGE_THREE_TRANSITION, level.getGameTime());
        state.beginAction(CATACLYSM, level.getGameTime());
        state.setInvulnerable(true);
        state.setDiveTarget(null, -1);
        state.clearActionPositions();
        ACTIVE.put(dragon.getUUID(), new RuntimeState());
        DragonBossController.sync(dragon);

        dragon.setInvulnerable(true);
        dragon.setNoAi(true);
        dragon.setInvisible(false);
        dragon.setDeltaMovement(Vec3.ZERO);
        dragon.getPhaseManager().setPhase(EnderDragonPhase.SITTING_SCANNING);
        roar(level, dragon, 0.42F);
    }

    public static void tick(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        RuntimeState runtime = ACTIVE.computeIfAbsent(dragon.getUUID(), ignored -> new RuntimeState());
        int ticks = (int)Math.max(0L, level.getGameTime() - state.getActionStartedAt());

        dragon.setInvulnerable(true);
        dragon.setNoAi(true);
        dragon.setDeltaMovement(Vec3.ZERO);
        if (dragon.getPhaseManager().getCurrentPhase().getPhase() != EnderDragonPhase.SITTING_SCANNING) {
            dragon.getPhaseManager().setPhase(EnderDragonPhase.SITTING_SCANNING);
        }

        emitDragonCharge(level, dragon, ticks);
        if (ticks % 80 == 0) {
            roar(level, dragon, 0.42F + dragon.getRandom().nextFloat() * 0.12F);
        }

        boolean beamsChanged = false;
        if (ticks < DURATION_TICKS && (ticks == 10 || ticks > 10 && (ticks - 10) % SOUL_BEAM_INTERVAL == 0)) {
            beamsChanged |= summonSoulBeams(level, dragon, runtime);
        }
        if (ticks < DURATION_TICKS && ticks >= 20 && ticks % ENDER_BEAM_INTERVAL == 0) {
            beamsChanged |= summonEnderBeam(level, dragon, runtime);
        }
        if (ticks < DURATION_TICKS && ticks >= 6 && ticks % LIGHTNING_INTERVAL == 0) {
            summonLightning(level, dragon);
        }

        beamsChanged |= tickSoulBeams(level, dragon, runtime);
        beamsChanged |= tickEnderBeams(level, dragon, runtime);
        if (beamsChanged) {
            syncBeamVisuals(dragon, state, runtime);
        }

        if (ticks >= DURATION_TICKS) {
            finish(level, dragon, state);
        }
    }

    private static boolean summonSoulBeams(ServerLevel level, EnderDragon dragon, RuntimeState runtime) {
        boolean added = false;
        for (Player player : DragonBossController.getCombatPlayers(level, dragon)) {
            Vec3 position = DragonBossController.findGroundAt(level, player.blockPosition());
            runtime.soulBeams.add(new SoulBeam(position, level.getGameTime() + SOUL_BEAM_WARNING_TICKS));
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, position.x, position.y + 0.2, position.z,
                    36, 0.65, 0.2, 0.65, 0.025);
            added = true;
        }
        return added;
    }

    private static boolean summonEnderBeam(ServerLevel level, EnderDragon dragon, RuntimeState runtime) {
        List<Player> players = DragonBossController.getCombatPlayers(level, dragon);
        if (players.isEmpty()) {
            return false;
        }
        Player player = DragonBossController.choosePlayer(dragon, players);
        double angle = dragon.getRandom().nextDouble() * Math.PI * 2.0;
        double radius = 4.0 + dragon.getRandom().nextDouble() * 10.0;
        BlockPos column = BlockPos.containing(
                player.getX() + Math.cos(angle) * radius,
                player.getY(),
                player.getZ() + Math.sin(angle) * radius
        );
        Vec3 position = DragonBossController.findGroundAt(level, column);
        // Ender beams remain until the transition itself ends.
        runtime.enderBeams.add(new EnderBeam(position, Long.MAX_VALUE));
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, position.x, position.y + 1.0, position.z,
                60, 0.8, 1.8, 0.8, 0.12);
        return true;
    }

    private static void summonLightning(ServerLevel level, EnderDragon dragon) {
        List<Player> players = DragonBossController.getCombatPlayers(level, dragon);
        if (players.isEmpty()) {
            return;
        }
        Player target = DragonBossController.choosePlayer(dragon, players);
        double angle = dragon.getRandom().nextDouble() * Math.PI * 2.0;
        double radius = 3.0 + dragon.getRandom().nextDouble() * 14.0;
        Vec3 strike = DragonBossController.findGroundAt(level, BlockPos.containing(
                target.getX() + Math.cos(angle) * radius,
                target.getY(),
                target.getZ() + Math.sin(angle) * radius
        ));

        if (dragon.getRandom().nextFloat() < 0.40F) {
            DragonMinionHelper.spawnHostileEnderman(level, target, strike, true, false);
        }
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(strike.x, strike.y, strike.z);
            level.addFreshEntity(bolt);
        }
    }

    private static boolean tickSoulBeams(ServerLevel level, EnderDragon dragon, RuntimeState runtime) {
        boolean removed = false;
        Iterator<SoulBeam> iterator = runtime.soulBeams.iterator();
        while (iterator.hasNext()) {
            SoulBeam beam = iterator.next();
            if (level.getGameTime() < beam.explodeAt()) {
                if (level.getGameTime() % 2L == 0L) {
                    level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, beam.position().x, beam.position().y + 0.4,
                            beam.position().z, 5, 0.45, 0.35, 0.45, 0.015);
                }
                continue;
            }

            AABB column = beamColumn(level, beam.position());
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, column,
                    entity -> entity.isAlive() && entity != dragon)) {
                if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) {
                    continue;
                }
                entity.hurt(dragon.damageSources().mobAttack(dragon), SOUL_BEAM_DAMAGE);
                entity.addEffect(new MobEffectInstance(AshenEnderDragon.SOUL_EXTRACTION, 5 * 20, 0), dragon);
            }
            Vec3 position = beam.position();
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, position.x, position.y + 1.0, position.z,
                    2, 0.35, 0.5, 0.35, 0.0);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, position.x, position.y + 2.0, position.z,
                    160, 0.8, 3.0, 0.8, 0.16);
            level.playSound(null, position.x, position.y, position.z, SoundEvents.GENERIC_EXPLODE,
                    SoundSource.HOSTILE, 2.5F, 0.75F);
            iterator.remove();
            removed = true;
        }
        return removed;
    }

    private static boolean tickEnderBeams(ServerLevel level, EnderDragon dragon, RuntimeState runtime) {
        boolean removed = false;
        Iterator<EnderBeam> iterator = runtime.enderBeams.iterator();
        while (iterator.hasNext()) {
            EnderBeam beam = iterator.next();
            if (level.getGameTime() >= beam.expiresAt) {
                iterator.remove();
                removed = true;
                continue;
            }

            AABB column = beamColumn(level, beam.position);
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, column,
                    entity -> entity.isAlive() && entity != dragon)) {
                long nextHit = beam.nextHitAt.getOrDefault(entity.getUUID(), 0L);
                if (level.getGameTime() < nextHit) {
                    continue;
                }
                beam.nextHitAt.put(entity.getUUID(), level.getGameTime() + 20L);
                entity.hurt(dragon.damageSources().mobAttack(dragon), ENDER_BEAM_DAMAGE);
                chorusTeleport(level, entity);
            }
            if (level.getGameTime() % 2L == 0L) {
                level.sendParticles(ParticleTypes.PORTAL, beam.position.x, beam.position.y + 1.0, beam.position.z,
                        7, 0.45, 1.2, 0.45, 0.08);
            }
        }
        return removed;
    }

    private static void chorusTeleport(ServerLevel level, LivingEntity entity) {
        Vec3 origin = entity.position();
        for (int attempt = 0; attempt < 16; attempt++) {
            double x = origin.x + (entity.getRandom().nextDouble() - 0.5) * 16.0;
            double z = origin.z + (entity.getRandom().nextDouble() - 0.5) * 16.0;
            Vec3 ground = DragonBossController.findGroundAt(level, BlockPos.containing(x, origin.y, z));
            if (entity.randomTeleport(ground.x, ground.y, ground.z, true)) {
                level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.CHORUS_FRUIT_TELEPORT,
                        SoundSource.HOSTILE, 1.0F, 1.0F);
                entity.playSound(SoundEvents.CHORUS_FRUIT_TELEPORT, 1.0F, 1.0F);
                return;
            }
        }
    }

    private static AABB beamColumn(ServerLevel level, Vec3 position) {
        return new AABB(
                position.x - BEAM_HALF_WIDTH, position.y, position.z - BEAM_HALF_WIDTH,
                position.x + BEAM_HALF_WIDTH, level.getMaxBuildHeight(), position.z + BEAM_HALF_WIDTH
        );
    }

    private static void syncBeamVisuals(EnderDragon dragon, DragonBossState state, RuntimeState runtime) {
        List<Vec3> encoded = new ArrayList<>(runtime.soulBeams.size() + runtime.enderBeams.size());
        for (SoulBeam beam : runtime.soulBeams) {
            encoded.add(beam.position());
        }
        for (EnderBeam beam : runtime.enderBeams) {
            encoded.add(new Vec3(beam.position.x, -Math.abs(beam.position.y), beam.position.z));
        }
        state.setActionPositions(encoded);
        DragonBossController.sync(dragon);
    }

    private static void emitDragonCharge(ServerLevel level, EnderDragon dragon, int ticks) {
        Vec3 head = new Vec3(dragon.head.getX(), dragon.head.getY(0.5), dragon.head.getZ());
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, head.x, head.y, head.z,
                10, 1.8, 1.2, 1.8, 0.025);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, dragon.getX(), dragon.getY() + 2.0, dragon.getZ(),
                24, 5.0, 2.5, 5.0, 0.1);
        if (ticks % 4 == 0) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, dragon.getX(), dragon.getY() + 3.0, dragon.getZ(),
                    18, 6.0, 2.5, 6.0, 0.08);
        }
    }

    private static void roar(ServerLevel level, EnderDragon dragon, float pitch) {
        level.playSound(null, dragon.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL,
                SoundSource.HOSTILE, 10.0F, pitch);
    }

    private static void finish(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        ACTIVE.remove(dragon.getUUID());
        state.setBossStage(3);
        state.setInvulnerable(false);
        state.setMode(DragonBossMode.TAKEOFF, level.getGameTime());
        DragonBossController.removeGroundResistance(dragon);
        state.clearAction(level.getGameTime());
        state.setNextActionAt(level.getGameTime() + 60L);
        dragon.setInvulnerable(false);
        dragon.setNoAi(false);
        dragon.setDeltaMovement(0.0, 0.9, 0.0);
        dragon.getPhaseManager().setPhase(EnderDragonPhase.TAKEOFF);
        DragonBossController.sync(dragon);
        roar(level, dragon, 0.35F);
    }

    public static void cleanup(EnderDragon dragon) {
        ACTIVE.remove(dragon.getUUID());
    }

    private record SoulBeam(Vec3 position, long explodeAt) {
    }

    private static final class EnderBeam {
        private final Vec3 position;
        private final long expiresAt;
        private final Map<UUID, Long> nextHitAt = new HashMap<>();

        private EnderBeam(Vec3 position, long expiresAt) {
            this.position = position;
            this.expiresAt = expiresAt;
        }
    }

    private static final class RuntimeState {
        private final List<SoulBeam> soulBeams = new ArrayList<>();
        private final List<EnderBeam> enderBeams = new ArrayList<>();
    }
}
