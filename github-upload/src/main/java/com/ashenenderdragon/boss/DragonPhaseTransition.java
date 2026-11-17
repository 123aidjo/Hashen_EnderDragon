package com.ashenenderdragon.boss;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.levelgen.feature.SpikeFeature;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Server-authoritative phase-one finale. It never replaces EndDragonFight or vanilla death/portal logic. */
public final class DragonPhaseTransition {
    public static final String FLY_TO_ALTAR = "phase_fly_to_altar";
    public static final String ALTAR_CHARGE = "phase_altar_charge";
    public static final String SPIRAL_ASCENT = "phase_spiral_ascent";
    public static final String ULTIMATE_CHARGE = "phase_ultimate_charge";
    public static final String ORB_DESCENT = "phase_orb_descent";

    private static final int FLY_TIMEOUT = 20 * 20;
    private static final int ALTAR_CHARGE_TICKS = 25 * 20;
    private static final int SPIRAL_TICKS = 14 * 20;
    private static final int ULTIMATE_TICKS = 120 * 20;
    private static final int RAY_INTERVAL = 55;
    private static final double ALTAR_HOVER_HEIGHT = 7.0;
    private static final double SPIRAL_RADIUS = 15.0;
    private static final double FINAL_HOVER_DISTANCE = 19.0;
    private static final double ORB_FALL_SPEED = 0.22;
    private static final double ISLAND_DAMAGE_RADIUS = 190.0;
    private static final int CRATER_RADIUS = 72;
    private static final DustParticleOptions PURPLE_BEAM = new DustParticleOptions(new Vector3f(0.72F, 0.08F, 1.0F), 3.5F);
    private static final DustParticleOptions DARK_PURPLE = new DustParticleOptions(new Vector3f(0.34F, 0.0F, 0.58F), 4.0F);
    private static final Map<UUID, List<SoulRay>> ACTIVE_RAYS = new HashMap<>();

    private DragonPhaseTransition() {
    }

    public static void begin(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        state.setMode(DragonBossMode.PHASE_TRANSITION, level.getGameTime());
        state.beginAction(FLY_TO_ALTAR, level.getGameTime());
        state.setInvulnerable(true);
        state.setDiveTarget(null, -1);
        state.setLandingTarget(altar(level, dragon));
        state.setActionPositions(List.of(beamBase(level, dragon), finalHover(level, dragon)));
        DragonBossController.sync(dragon);

        dragon.setHealth(1.0F);
        dragon.setInvulnerable(true);
        dragon.setInvisible(false);
        dragon.setNoAi(false);
        dragon.getPhaseManager().setPhase(EnderDragonPhase.CHARGING_PLAYER);
        setChargeTarget(dragon, altar(level, dragon).add(0.0, ALTAR_HOVER_HEIGHT, 0.0));
        syncBossBar(dragon);
        level.playSound(null, dragon.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 8.0F, 0.55F);
    }

    public static void tick(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        if (!ULTIMATE_CHARGE.equals(state.getActionId()) && !ORB_DESCENT.equals(state.getActionId())) {
            dragon.setHealth(1.0F);
        }
        dragon.setInvulnerable(true);
        String action = state.getActionId();
        if (FLY_TO_ALTAR.equals(action)) {
            tickFlyToAltar(level, dragon, state);
        } else if (ALTAR_CHARGE.equals(action)) {
            tickAltarCharge(level, dragon, state);
        } else if (SPIRAL_ASCENT.equals(action)) {
            tickSpiral(level, dragon, state);
        } else if (ULTIMATE_CHARGE.equals(action)) {
            tickUltimateCharge(level, dragon, state);
        } else if (ORB_DESCENT.equals(action)) {
            tickOrb(level, dragon, state);
        } else {
            begin(level, dragon, state);
        }
        syncBossBar(dragon);
    }

    static void tickStageTwoAura(ServerLevel level, EnderDragon dragon) {
        if (level.getGameTime() % 2L == 0L) {
            emitWingAura(level, dragon);
        }
    }

    private static void tickFlyToAltar(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        Vec3 target = altar(level, dragon).add(0.0, ALTAR_HOVER_HEIGHT, 0.0);
        setChargeTarget(dragon, target);
        Vec3 delta = target.subtract(dragon.position());
        if (delta.lengthSqr() > 1.0E-4) {
            dragon.setDeltaMovement(delta.normalize().scale(Mth.clamp(delta.length() * 0.09, 0.45, 1.25)));
            face(dragon, delta);
        }
        emitFlightTrail(level, dragon);
        if (delta.lengthSqr() <= 6.0 * 6.0 || actionTicks(level, state) >= FLY_TIMEOUT) {
            holdAt(dragon, target);
            beginAction(level, dragon, state, ALTAR_CHARGE);
            level.playSound(null, dragon.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 8.0F, 0.45F);
        }
    }

    private static void tickAltarCharge(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        Vec3 center = altar(level, dragon).add(0.0, ALTAR_HOVER_HEIGHT, 0.0);
        holdAt(dragon, center);
        int ticks = actionTicks(level, state);
        for (int i = 0; i < 14; i++) {
            double angle = (ticks * 0.17 + i * Mth.TWO_PI / 14.0);
            double radius = 17.0 - (ticks % 30) * 0.48;
            Vec3 source = center.add(Math.cos(angle) * radius, (i % 7) - 1.0, Math.sin(angle) * radius);
            Vec3 velocity = center.subtract(source).normalize().scale(0.32);
            level.sendParticles(ParticleTypes.PORTAL, source.x, source.y, source.z, 2, velocity.x, velocity.y, velocity.z, 0.1);
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y + 2.0, center.z, 28, 5.0, 3.0, 5.0, 0.08);
        if (ticks >= ALTAR_CHARGE_TICKS) {
            beginAction(level, dragon, state, SPIRAL_ASCENT);
            level.playSound(null, BlockPos.containing(center), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 8.0F, 0.55F);
        }
    }

    private static void tickSpiral(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        dragon.setNoAi(false);
        int ticks = actionTicks(level, state);
        // Give the vanilla charging phase a point well ahead on the curve. It then owns yaw, banking and turning.
        double targetProgress = Mth.clamp((ticks + 40.0) / (SPIRAL_TICKS + 40.0), 0.0, 1.0);
        Vec3 base = altar(level, dragon);
        double top = transitionHeight(level);
        double angle = -Math.PI / 2.0 + targetProgress * Math.PI * 6.0;
        double radius = Mth.lerp(targetProgress, SPIRAL_RADIUS, FINAL_HOVER_DISTANCE);
        double enterRadius = Mth.clamp(targetProgress * 5.0, 0.0, 1.0);
        radius *= enterRadius;
        Vec3 desired = new Vec3(base.x + Math.cos(angle) * radius, Mth.lerp(targetProgress, base.y + ALTAR_HOVER_HEIGHT, top), base.z + Math.sin(angle) * radius);
        setChargeTarget(dragon, desired);
        emitFlightTrail(level, dragon);
        Vec3 hover = finalHover(level, dragon);
        if ((ticks >= SPIRAL_TICKS && dragon.position().distanceToSqr(hover) <= 5.0 * 5.0)
                || ticks >= SPIRAL_TICKS + 100) {
            holdAt(dragon, hover);
            restoreCrystals(level, dragon);
            beginAction(level, dragon, state, ULTIMATE_CHARGE);
            emitTransformationBurst(level, dragon);
        }
    }

    private static void tickUltimateCharge(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        Vec3 hover = finalHover(level, dragon);
        holdAt(dragon, hover);
        face(dragon, altar(level, dragon).subtract(hover));
        int ticks = actionTicks(level, state);
        float targetHealth = 1.0F + (dragon.getMaxHealth() - 1.0F) * Mth.clamp(ticks / (float)ULTIMATE_TICKS, 0.0F, 1.0F);
        dragon.setHealth(targetHealth);
        updateCrystalBeams(level, dragon);
        emitWingAura(level, dragon);
        if (ticks > 0 && ticks % RAY_INTERVAL == 0) {
            launchRays(level, dragon);
        }
        tickRays(level, dragon);
        if (ticks >= ULTIMATE_TICKS) {
            ACTIVE_RAYS.remove(dragon.getUUID());
            Vec3 center = altar(level, dragon);
            Vec3 orbStart = new Vec3(center.x, transitionHeight(level) + 35.0, center.z);
            state.setActionPositions(List.of(center, orbStart));
            beginAction(level, dragon, state, ORB_DESCENT);
            state.setActionPositions(List.of(center, orbStart));
            DragonBossController.sync(dragon);
            clearCrystalBeams(level);
        }
    }

    private static void tickOrb(ServerLevel level, EnderDragon dragon, DragonBossState state) {
        Vec3 center = altar(level, dragon);
        holdAt(dragon, finalHover(level, dragon));
        int ticks = actionTicks(level, state);
        double y = transitionHeight(level) + 35.0 - ticks * ORB_FALL_SPEED;
        Vec3 orb = new Vec3(center.x, y, center.z);
        emitOrb(level, orb, ticks);
        if (y <= center.y + 1.0 || ticks >= 20 * 25) {
            finishExplosion(level, dragon, state, center);
        }
    }

    private static void beginAction(ServerLevel level, EnderDragon dragon, DragonBossState state, String action) {
        state.beginAction(action, level.getGameTime());
        state.setActionPositions(List.of(beamBase(level, dragon), finalHover(level, dragon)));
        DragonBossController.sync(dragon);
    }

    private static void restoreCrystals(ServerLevel level, EnderDragon dragon) {
        for (SpikeFeature.EndSpike spike : SpikeFeature.getSpikesForLevel(level)) {
            AABB area = new AABB(spike.getCenterX() - 5, spike.getHeight() - 2, spike.getCenterZ() - 5,
                    spike.getCenterX() + 6, spike.getHeight() + 9, spike.getCenterZ() + 6);
            List<EndCrystal> crystals = level.getEntitiesOfClass(EndCrystal.class, area);
            if (crystals.isEmpty()) {
                EndCrystal crystal = EntityType.END_CRYSTAL.create(level);
                if (crystal != null) {
                    crystal.moveTo(spike.getCenterX() + 0.5, spike.getHeight() + 1.0, spike.getCenterZ() + 0.5,
                            dragon.getRandom().nextFloat() * 360.0F, 0.0F);
                    crystal.setShowBottom(false);
                    level.addFreshEntity(crystal);
                }
            }
            windPressure(level, new Vec3(spike.getCenterX() + 0.5, spike.getHeight() + 1.0, spike.getCenterZ() + 0.5));
        }
    }

    private static void windPressure(ServerLevel level, Vec3 center) {
        AABB area = new AABB(center, center).inflate(2.5, 5.0, 2.5);
        for (Player player : level.getEntitiesOfClass(Player.class, area, Player::isAlive)) {
            Vec3 away = player.position().subtract(center).multiply(1.0, 0.0, 1.0);
            if (away.lengthSqr() < 0.01) away = new Vec3(1.0, 0.0, 0.0);
            player.push(away.normalize().x * 1.1, 1.15, away.normalize().z * 1.1);
            player.hurtMarked = true;
        }
        level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, center.x, center.y, center.z, 4, 2.5, 0.3, 2.5, 0.0);
        level.sendParticles(ParticleTypes.CLOUD, center.x, center.y, center.z, 120, 4.0, 1.0, 4.0, 0.18);
    }

    private static void updateCrystalBeams(ServerLevel level, EnderDragon dragon) {
        BlockPos target = BlockPos.containing(dragon.position().add(0.0, 2.0, 0.0));
        for (EndCrystal crystal : transitionCrystals(level)) {
            crystal.setBeamTarget(target);
        }
    }

    private static void clearCrystalBeams(ServerLevel level) {
        for (EndCrystal crystal : transitionCrystals(level)) crystal.setBeamTarget(null);
    }

    private static List<EndCrystal> transitionCrystals(ServerLevel level) {
        List<EndCrystal> result = new ArrayList<>();
        for (SpikeFeature.EndSpike spike : SpikeFeature.getSpikesForLevel(level)) {
            AABB area = new AABB(spike.getCenterX() - 5, spike.getHeight() - 2, spike.getCenterZ() - 5,
                    spike.getCenterX() + 6, spike.getHeight() + 9, spike.getCenterZ() + 6);
            result.addAll(level.getEntitiesOfClass(EndCrystal.class, area));
        }
        return result;
    }

    private static void launchRays(ServerLevel level, EnderDragon dragon) {
        List<SoulRay> rays = ACTIVE_RAYS.computeIfAbsent(dragon.getUUID(), ignored -> new ArrayList<>());
        Vec3 origin = new Vec3(dragon.head.getX(), dragon.head.getY(), dragon.head.getZ());
        for (Player player : DragonBossController.getCombatPlayers(level, dragon)) {
            Vec3 velocity = player.getEyePosition().subtract(origin).normalize().scale(1.65);
            rays.add(new SoulRay(origin, velocity, player.getUUID(), 0));
        }
        level.playSound(null, dragon.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 4.0F, 1.7F);
    }

    private static void tickRays(ServerLevel level, EnderDragon dragon) {
        List<SoulRay> rays = ACTIVE_RAYS.get(dragon.getUUID());
        if (rays == null) return;
        ListIterator<SoulRay> iterator = rays.listIterator();
        while (iterator.hasNext()) {
            SoulRay ray = iterator.next();
            Player target = level.getPlayerByUUID(ray.target());
            if (target == null || !target.isAlive() || ray.age() > 110) {
                iterator.remove();
                continue;
            }
            Vec3 desired = target.getEyePosition().subtract(ray.position()).normalize().scale(1.65);
            Vec3 velocity = ray.velocity().scale(0.84).add(desired.scale(0.16)).normalize().scale(1.65);
            Vec3 next = ray.position().add(velocity);
            for (int i = 0; i < 4; i++) {
                Vec3 sample = ray.position().lerp(next, i / 4.0);
                level.sendParticles(ParticleTypes.FIREWORK, sample.x, sample.y, sample.z,
                        7, 0.12, 0.12, 0.12, 0.025);
            }
            if (next.distanceToSqr(target.getEyePosition()) <= 2.2 * 2.2) {
                target.hurt(dragon.damageSources().mobAttack(dragon), 8.0F);
                level.sendParticles(ParticleTypes.FLASH, next.x, next.y, next.z, 1, 0.0, 0.0, 0.0, 0.0);
                iterator.remove();
            } else {
                iterator.set(new SoulRay(next, velocity, ray.target(), ray.age() + 1));
            }
        }
    }

    private static void finishExplosion(ServerLevel level, EnderDragon dragon, DragonBossState state, Vec3 center) {
        emitCataclysm(level, center);

        // Terrain destruction is unconditional. Crystal survival only controls player damage and the next boss mode.
        carveCrater(level, center);

        boolean crystalsRemain = !transitionCrystals(level).isEmpty();
        for (Player player : level.players()) {
            if (!player.isAlive() || player.isCreative() || player.isSpectator()
                    || distance2D(player.position(), center) > ISLAND_DAMAGE_RADIUS) continue;
            if (crystalsRemain) {
                player.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
            } else if (isExposed(level, player, center)) {
                float remainingHealth = player.getHealth() - 18.0F;
                if (remainingHealth <= 0.0F) {
                    player.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
                } else {
                    player.setHealth(remainingHealth);
                }
            }
        }

        clearCrystalBeams(level);
        state.setBossStage(2);
        state.setInvulnerable(false);
        state.resetFlightDamage();
        state.clearAction(level.getGameTime());
        dragon.setHealth(dragon.getMaxHealth());
        dragon.setInvulnerable(false);
        dragon.setNoAi(false);
        dragon.setInvisible(false);
        if (crystalsRemain) {
            state.setMode(DragonBossMode.FLYING, level.getGameTime());
            state.setNextActionAt(level.getGameTime() + 80);
            dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
        } else {
            Vec3 landing = altar(level, dragon);
            state.setMode(DragonBossMode.LANDING, level.getGameTime());
            state.setLandingTarget(landing);
            state.setActionDirection(landing.subtract(dragon.position()).normalize());
            dragon.getPhaseManager().setPhase(EnderDragonPhase.CHARGING_PLAYER);
            setChargeTarget(dragon, landing);
        }
        DragonBossController.sync(dragon);
    }

    private static boolean isExposed(ServerLevel level, Player player, Vec3 center) {
        if (player.getY() > center.y + 30.0 || player.getY() < center.y - 5.0) return false;
        return level.clip(new ClipContext(center.add(0.0, 3.0, 0.0), player.getEyePosition(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
    }

    private static void carveCrater(ServerLevel level, Vec3 center) {
        int cx = Mth.floor(center.x);
        int cz = Mth.floor(center.z);
        for (int x = -CRATER_RADIUS; x <= CRATER_RADIUS; x++) {
            for (int z = -CRATER_RADIUS; z <= CRATER_RADIUS; z++) {
                if (x * x + z * z > CRATER_RADIUS * CRATER_RADIUS) continue;
                BlockPos column = new BlockPos(cx + x, Mth.floor(center.y), cz + z);
                if (!level.hasChunkAt(column)) continue;
                BlockPos surface = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, column).below();
                for (int depth = 0; depth < 2; depth++) {
                    BlockPos pos = surface.below(depth);
                    if (level.getBlockState(pos).is(Blocks.END_STONE)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static void emitCataclysm(ServerLevel level, Vec3 center) {
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y + 3.0, center.z, 24, 35.0, 12.0, 35.0, 0.0);
        level.sendParticles(ParticleTypes.DRAGON_BREATH, center.x, center.y + 12.0, center.z, 2200, 75.0, 28.0, 75.0, 0.18);
        level.sendParticles(PURPLE_BEAM, center.x, center.y + 10.0, center.z, 1600, 80.0, 25.0, 80.0, 0.1);
        level.sendParticles(ParticleTypes.FIREWORK, center.x, center.y + 18.0, center.z, 900, 75.0, 30.0, 75.0, 0.3);
        level.sendParticles(ParticleTypes.FLASH, center.x, center.y + 8.0, center.z, 80, 55.0, 18.0, 55.0, 0.0);

        // Count zero gives each particle an explicit velocity: these are the visible radial blast jets.
        for (int i = 0; i < 240; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0;
            double horizontal = 0.65 + level.random.nextDouble() * 2.9;
            double upward = -0.15 + level.random.nextDouble() * 2.6;
            ParticleOptions particle = switch (i % 4) {
                case 0 -> ParticleTypes.FIREWORK;
                case 1 -> ParticleTypes.DRAGON_BREATH;
                case 2 -> ParticleTypes.SOUL_FIRE_FLAME;
                default -> ParticleTypes.PORTAL;
            };
            level.sendParticles(
                    particle,
                    center.x,
                    center.y + 4.0 + level.random.nextDouble() * 10.0,
                    center.z,
                    0,
                    Math.cos(angle) * horizontal,
                    upward,
                    Math.sin(angle) * horizontal,
                    1.0
            );
        }

        // Several expanding rings make the ground shockwave readable across the whole island.
        for (int ring = 1; ring <= 6; ring++) {
            double radius = ring * 13.0;
            for (int sample = 0; sample < 48; sample++) {
                double angle = sample * Math.PI * 2.0 / 48.0 + ring * 0.13;
                double x = center.x + Math.cos(angle) * radius;
                double z = center.z + Math.sin(angle) * radius;
                level.sendParticles(PURPLE_BEAM, x, center.y + 1.2 + ring * 0.35, z, 18, 2.2, 0.8, 2.2, 0.08);
                if ((sample + ring) % 3 == 0) {
                    level.sendParticles(ParticleTypes.EXPLOSION, x, center.y + 2.0, z, 3, 1.4, 1.0, 1.4, 0.08);
                }
            }
        }
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 20.0F, 0.45F);
    }

    private static void emitOrb(ServerLevel level, Vec3 orb, int ticks) {
        double pulse = 6.0 + Math.sin(ticks * 0.2) * 0.7;
        level.sendParticles(DARK_PURPLE, orb.x, orb.y, orb.z, 120, pulse, pulse, pulse, 0.0);
        level.sendParticles(ParticleTypes.DRAGON_BREATH, orb.x, orb.y, orb.z, 80, pulse * 0.8, pulse * 0.8, pulse * 0.8, 0.02);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, orb.x, orb.y, orb.z, 60, pulse, pulse, pulse, 0.04);
    }

    private static void emitWingAura(ServerLevel level, EnderDragon dragon) {
        Vec3 forward = DragonBossController.dragonForward(dragon);
        Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
        for (double sign : new double[]{-1.0, 1.0}) {
            Vec3 wing = dragon.position().add(side.scale(8.0 * sign)).add(0.0, 2.5, 0.0);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, wing.x, wing.y, wing.z, 14, 2.2, 0.7, 2.2, 0.025);
            level.sendParticles(ParticleTypes.PORTAL, wing.x, wing.y, wing.z, 20, 2.8, 0.9, 2.8, 0.08);
        }
    }

    private static void emitTransformationBurst(ServerLevel level, EnderDragon dragon) {
        Vec3 pos = dragon.position().add(0.0, 2.0, 0.0);
        level.sendParticles(ParticleTypes.PORTAL, pos.x, pos.y, pos.z, 850, 12.0, 6.0, 12.0, 0.35);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y, pos.z, 520, 10.0, 5.0, 10.0, 0.2);
        level.sendParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 480, 12.0, 6.0, 12.0, 0.28);
        level.playSound(null, dragon.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 12.0F, 0.38F);
    }

    private static void emitFlightTrail(ServerLevel level, EnderDragon dragon) {
        level.sendParticles(ParticleTypes.PORTAL, dragon.getX(), dragon.getY() + 1.5, dragon.getZ(), 20, 2.5, 1.2, 2.5, 0.08);
    }

    private static Vec3 altar(ServerLevel level, EnderDragon dragon) {
        return DragonBossController.findGroundAt(level, dragon.getFightOrigin());
    }

    private static Vec3 beamBase(ServerLevel level, EnderDragon dragon) {
        BlockPos column = dragon.getFightOrigin();
        BlockPos cursor = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, column).below();
        BlockPos lowestBedrock = cursor;
        boolean foundBedrock = false;
        for (int i = 0; i < 12 && cursor.getY() > level.getMinBuildHeight(); i++, cursor = cursor.below()) {
            if (level.getBlockState(cursor).is(Blocks.BEDROCK)) {
                lowestBedrock = cursor;
                foundBedrock = true;
            } else if (foundBedrock) {
                break;
            }
        }
        return Vec3.atBottomCenterOf(lowestBedrock);
    }

    private static double transitionHeight(ServerLevel level) {
        int max = 100;
        for (SpikeFeature.EndSpike spike : SpikeFeature.getSpikesForLevel(level)) max = Math.max(max, spike.getHeight());
        return max + 2.0;
    }

    private static Vec3 finalHover(ServerLevel level, EnderDragon dragon) {
        Vec3 center = altar(level, dragon);
        return new Vec3(center.x, transitionHeight(level), center.z - FINAL_HOVER_DISTANCE);
    }

    private static void holdAt(EnderDragon dragon, Vec3 position) {
        dragon.setNoAi(true);
        if (dragon.getPhaseManager().getCurrentPhase().getPhase() != EnderDragonPhase.HOVERING) {
            dragon.getPhaseManager().setPhase(EnderDragonPhase.HOVERING);
        }
        dragon.setPos(position);
        dragon.setDeltaMovement(Vec3.ZERO);
    }

    private static void setChargeTarget(EnderDragon dragon, Vec3 target) {
        if (dragon.getPhaseManager().getCurrentPhase().getPhase() != EnderDragonPhase.CHARGING_PLAYER) {
            dragon.getPhaseManager().setPhase(EnderDragonPhase.CHARGING_PLAYER);
        }
        dragon.getPhaseManager().getPhase(EnderDragonPhase.CHARGING_PLAYER).setTarget(target);
    }

    private static void face(EnderDragon dragon, Vec3 direction) {
        if (direction.x * direction.x + direction.z * direction.z < 1.0E-5) return;
        dragon.setYRot((float)Math.toDegrees(Math.atan2(direction.x, -direction.z)));
        dragon.yBodyRot = dragon.getYRot();
        dragon.yRotA = 0.0F;
    }

    private static int actionTicks(ServerLevel level, DragonBossState state) {
        return (int)Math.max(0L, level.getGameTime() - state.getActionStartedAt());
    }

    private static void syncBossBar(EnderDragon dragon) {
        EndDragonFight fight = dragon.getDragonFight();
        if (fight != null) {
            fight.updateDragon(dragon);
        }
    }

    private static double distance2D(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    private record SoulRay(Vec3 position, Vec3 velocity, UUID target, int age) {
    }
}
