package com.ashenenderdragon.client.animation;

import javax.annotation.Nullable;

import com.ashenenderdragon.boss.DragonBossMode;
import com.ashenenderdragon.boss.DragonBossState;
import com.ashenenderdragon.boss.DragonGroundActions;
import com.ashenenderdragon.boss.DragonPhaseTransition;
import com.ashenenderdragon.boss.DragonStageThreeTransition;

import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A client-only render override. It never changes the real entity or server boss state. */
public final class DragonAnimationDebugSession {
    public static final String[] ACTIONS = {
            DragonBossState.NO_ACTION,
            DragonGroundActions.BREATH_VOLLEY,
            DragonGroundActions.TAIL_SWEEP,
            DragonGroundActions.BODY_SLAM,
            DragonGroundActions.CRAWL_CHARGE,
            DragonGroundActions.BITE,
            DragonGroundActions.FAN_BREATH,
            DragonGroundActions.WIND_PRESSURE,
            DragonPhaseTransition.ULTIMATE_CHARGE,
            DragonStageThreeTransition.CATACLYSM
    };

    private static int targetEntityId = -1;
    @Nullable
    private static EnderDragon previewDragon;
    private static DragonBossState previewState;
    private static String action = DragonBossState.NO_ACTION;
    private static float ticks;
    private static boolean playing;

    private DragonAnimationDebugSession() {
    }

    public static void start(Level level, @Nullable EnderDragon worldDragon) {
        targetEntityId = worldDragon == null ? -1 : worldDragon.getId();
        if (previewDragon == null || previewDragon.level() != level) {
            previewDragon = new EnderDragon(EntityType.ENDER_DRAGON, level);
            previewDragon.setId(Integer.MIN_VALUE + 173);
            previewDragon.setPos(0.0, 0.0, 0.0);
            previewDragon.setYRot(180.0F);
            previewDragon.yBodyRot = 180.0F;
        }
        if (previewState == null) {
            rebuildState();
        }
    }

    public static void stop() {
        targetEntityId = -1;
        previewDragon = null;
        playing = false;
    }

    public static boolean isActive() {
        return previewDragon != null && previewState != null;
    }

    public static boolean isTarget(@Nullable EnderDragon dragon) {
        return dragon != null && isActive() && (dragon == previewDragon || dragon.getId() == targetEntityId);
    }

    public static int targetEntityId() {
        return targetEntityId;
    }

    @Nullable
    public static EnderDragon previewDragon() {
        return previewDragon;
    }

    public static void clearWorldTarget() {
        targetEntityId = -1;
    }

    public static DragonBossState effectiveState(EnderDragon dragon, @Nullable DragonBossState fallback) {
        return isTarget(dragon) ? previewState : fallback;
    }

    public static float renderTicks(EnderDragon dragon, float partialTick, float fallback) {
        return isTarget(dragon) ? Math.min(duration(), ticks + (playing ? partialTick : 0.0F)) : fallback;
    }

    public static String action() {
        return action;
    }

    public static void setAction(String newAction) {
        action = newAction;
        ticks = 0.0F;
        playing = false;
        rebuildState();
    }

    public static float ticks() {
        return ticks;
    }

    public static void setTicks(float newTicks) {
        ticks = Math.max(0.0F, Math.min(duration(), newTicks));
    }

    public static boolean isPlaying() {
        return playing;
    }

    public static void setPlaying(boolean value) {
        playing = value;
    }

    public static void tick() {
        if (!isActive() || !playing) {
            return;
        }
        previewDragon.tickCount++;
        ticks += 1.0F;
        if (ticks > duration()) {
            ticks = 0.0F;
        }
    }

    public static int duration() {
        return switch (action) {
            case DragonGroundActions.BREATH_VOLLEY -> 65;
            case DragonGroundActions.TAIL_SWEEP -> 40;
            case DragonGroundActions.BODY_SLAM -> 104;
            case DragonGroundActions.CRAWL_CHARGE -> 95;
            case DragonGroundActions.BITE -> 42;
            case DragonGroundActions.FAN_BREATH -> 65;
            case DragonGroundActions.WIND_PRESSURE -> 55;
            case DragonPhaseTransition.ULTIMATE_CHARGE -> 160;
            case DragonStageThreeTransition.CATACLYSM -> 600;
            default -> 80;
        };
    }

    public static String actionDisplayName(String actionId) {
        return switch (actionId) {
            case DragonStageThreeTransition.CATACLYSM -> "三阶段转场长啸";
            case DragonPhaseTransition.ULTIMATE_CHARGE -> "二阶段大招悬停蓄力";
            case DragonGroundActions.BREATH_VOLLEY -> "龙息球连射";
            case DragonGroundActions.TAIL_SWEEP -> "扫尾";
            case DragonGroundActions.BODY_SLAM -> "下压";
            case DragonGroundActions.CRAWL_CHARGE -> "龙车";
            case DragonGroundActions.BITE -> "小咬";
            case DragonGroundActions.FAN_BREATH -> "扇形火";
            case DragonGroundActions.WIND_PRESSURE -> "起飞风压";
            default -> "落地静止";
        };
    }

    private static void rebuildState() {
        DragonBossState state = new DragonBossState();
        state.initialize(DragonStageThreeTransition.CATACLYSM.equals(action)
                ? DragonBossMode.STAGE_THREE_TRANSITION
                : DragonPhaseTransition.ULTIMATE_CHARGE.equals(action)
                        ? DragonBossMode.PHASE_TRANSITION
                        : DragonBossMode.GROUNDED, 0L);
        state.beginAction(action, 0L);
        state.setActionDirection(new Vec3(1.0, 0.0, 0.0));
        previewState = state;
    }
}
