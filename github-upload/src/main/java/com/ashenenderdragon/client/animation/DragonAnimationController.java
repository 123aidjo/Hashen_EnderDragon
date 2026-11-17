package com.ashenenderdragon.client.animation;

import com.ashenenderdragon.boss.DragonBossMode;
import com.ashenenderdragon.boss.DragonBossState;
import com.ashenenderdragon.boss.DragonGroundActions;
import com.ashenenderdragon.boss.DragonPhaseTransition;
import com.ashenenderdragon.boss.DragonStageThreeTransition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Coarse keyframed poses layered over the vanilla dragon model.
 *
 * <p>This deliberately avoids terrain IK. The controller owns readable animation primitives while the
 * renderer mixin remains only an injection bridge into vanilla's procedural model.</p>
 */
public final class DragonAnimationController {
    private static final float GROUND_WING_X = 0.16F;
    private static final float GROUND_WING_Y = 0.18F;
    private static final float GROUND_WING_Z = 0.16F;
    private static final float GROUND_WING_TIP_Z = 1.15F;

    private DragonAnimationController() {
    }

    public static boolean usesGroundPose(DragonBossState state) {
        return state.getMode() == DragonBossMode.GROUNDED
                || state.getMode() == DragonBossMode.STAGE_THREE_TRANSITION
                || DragonPhaseTransition.ULTIMATE_CHARGE.equals(state.getActionId());
    }

    public static void applyRoot(PoseStack poseStack, ModelPart body, DragonBossState state, float ticks) {
        body.xRot = 0.0F;
        body.yRot = 0.0F;
        if (!usesGroundPose(state)) {
            return;
        }

        String action = state.getActionId();
        String poseAction = proceduralAction(action, ticks);
        applyGroundBaseRoot(poseStack, body, action, ticks);
        if (DragonStageThreeTransition.CATACLYSM.equals(poseAction)) {
            applyRearStandRoot(poseStack, body, 0.46F * stageThreeRoar(ticks));
        } else if (DragonPhaseTransition.ULTIMATE_CHARGE.equals(poseAction)) {
            poseStack.translate(0.0F, 0.65F, 0.15F);
            poseStack.mulPose(Axis.XP.rotationDegrees(-52.0F));
            body.xRot -= 0.12F;
        } else if (DragonGroundActions.BODY_SLAM.equals(poseAction)) {
            applyBodySlamReadyRoot(poseStack, body, bodySlamUpright(ticks));
        } else if (DragonGroundActions.CRAWL_CHARGE.equals(poseAction)) {
            float upright = crawlChargeUpright(ticks);
            applyRearStandRoot(poseStack, body, upright);
            if (ticks >= DragonGroundActions.CRAWL_CHARGE_START_TICK) {
                float crawl = smooth(Mth.clamp((ticks - DragonGroundActions.CRAWL_CHARGE_START_TICK) / 5.0F, 0.0F, 1.0F));
                float bob = Math.abs((float)Math.sin((ticks - DragonGroundActions.CRAWL_CHARGE_START_TICK) * 0.8F)) * 0.09F;
                poseStack.translate(0.0F, (0.4F + bob) * crawl, -0.18F * crawl);
                poseStack.mulPose(Axis.XP.rotationDegrees(-7.0F * crawl));
                body.xRot += 0.1F * crawl;
            }
        } else if (DragonGroundActions.BITE.equals(poseAction)) {
            float walking = biteWalkingAmount(ticks);
            float bob = Math.abs((float)Math.sin((ticks - 10.0F) * Mth.TWO_PI / 14.0F)) * 0.08F * walking;
            float lunge = biteLunge(ticks);
            poseStack.translate(0.0F, bob + 0.08F * lunge, -0.35F * lunge);
            body.xRot -= 0.07F * lunge;
        } else if (DragonGroundActions.FAN_BREATH.equals(poseAction)) {
            float roar = fanRoar(ticks);
            float prone = fanProne(ticks);
            poseStack.translate(0.0F, -0.2F * roar + 0.48F * prone, 0.16F * roar - 0.22F * prone);
            poseStack.mulPose(Axis.XP.rotationDegrees(10.0F * roar - 9.0F * prone));
            body.xRot += -0.08F * roar + 0.13F * prone;
        } else if (DragonGroundActions.TAIL_SWEEP.equals(poseAction)) {
            float brace = tailLook(ticks);
            poseStack.translate(0.0F, 0.08F * brace, 0.0F);
            body.zRot += tailSign(state) * 0.06F * brace;
        } else if (DragonGroundActions.WIND_PRESSURE.equals(poseAction)) {
            float crouch = smooth(Mth.clamp(ticks / 20.0F, 0.0F, 1.0F));
            poseStack.translate(0.0F, 0.3F * crouch, 0.0F);
            poseStack.mulPose(Axis.XP.rotationDegrees(-9.0F * crouch));
            body.xRot += 0.08F * crouch;
        }
        applyActionRootOverride(poseStack, action, ticks);
        applyActionPartOverride(body, DragonModelPart.BODY, action, ticks);
    }

    public static void applyNeck(ModelPart neck, DragonBossState state, float ticks, int segment) {
        if (!usesGroundPose(state)) {
            return;
        }

        String action = state.getActionId();
        String poseAction = proceduralAction(action, ticks);
        applyGroundBasePart(neck, DragonModelPart.NECK, action, ticks);
        if (DragonGroundActions.CRAWL_CHARGE.equals(poseAction) && ticks >= DragonGroundActions.CRAWL_CHARGE_START_TICK) {
            neck.x = 0.0F;
            neck.y = 20.0F;
            neck.z = -12.0F - segment * 10.0F;
            neck.xRot = 0.02F * (float)Math.sin(ticks * 0.8F + segment * 0.4F);
            neck.yRot = 0.0F;
            neck.zRot = 0.0F;
            applyActionPartOverride(neck, DragonModelPart.NECK, action, ticks);
            return;
        }

        if (DragonStageThreeTransition.CATACLYSM.equals(poseAction)) {
            neck.xRot -= 0.42F * stageThreeRoar(ticks);
        } else if (DragonPhaseTransition.ULTIMATE_CHARGE.equals(poseAction)) {
            neck.xRot -= 0.18F;
        } else if (DragonGroundActions.TAIL_SWEEP.equals(poseAction)) {
            neck.yRot += tailSign(state) * 0.28F * tailLook(ticks);
        } else if (DragonGroundActions.BITE.equals(poseAction)) {
            neck.xRot -= 0.1F * biteLunge(ticks);
        } else if (DragonGroundActions.FAN_BREATH.equals(poseAction)) {
            neck.xRot += 0.14F * fanProne(ticks) - 0.2F * fanRoar(ticks);
            neck.y += 2.0F * fanProne(ticks);
        } else if (DragonGroundActions.CRAWL_CHARGE.equals(poseAction)) {
            neck.xRot -= 0.22F * roar(ticks, 0.0F, 14.0F);
        } else if (DragonGroundActions.WIND_PRESSURE.equals(poseAction)) {
            neck.xRot -= 0.18F * roar(ticks, 0.0F, 16.0F);
        } else if (DragonGroundActions.BODY_SLAM.equals(poseAction)) {
            neck.xRot -= 0.2F * roar(ticks, 0.0F, 12.0F) + 0.12F * bodySlamUpright(ticks);
        }
        applyActionPartOverride(neck, DragonModelPart.NECK, action, ticks);
    }

    public static void applyHead(ModelPart head, ModelPart jaw, DragonBossState state, float ticks) {
        if (!usesGroundPose(state)) {
            return;
        }

        jaw.xRot = 0.0F;
        String action = state.getActionId();
        String poseAction = proceduralAction(action, ticks);
        applyGroundBasePart(head, DragonModelPart.HEAD, action, ticks);
        applyGroundBasePart(jaw, DragonModelPart.JAW, action, ticks);
        if (DragonStageThreeTransition.CATACLYSM.equals(poseAction)) {
            float roar = stageThreeRoar(ticks);
            head.xRot -= 0.82F * roar;
            jaw.xRot += 0.92F * roar;
        } else if (DragonPhaseTransition.ULTIMATE_CHARGE.equals(poseAction)) {
            head.xRot -= 0.38F;
            jaw.xRot += 0.32F;
        } else if (DragonGroundActions.TAIL_SWEEP.equals(poseAction)) {
            float look = tailLook(ticks);
            head.yRot += tailSign(state) * 1.9F * look;
            head.xRot += 0.18F * look;
        } else if (DragonGroundActions.BREATH_VOLLEY.equals(poseAction)) {
            float breath = Math.max(pulse(ticks, 20.0F, 8.0F), pulse(ticks, 50.0F, 8.0F));
            jaw.xRot += 0.83F * breath;
            head.xRot += 0.12F * breath;
        } else if (DragonGroundActions.BITE.equals(poseAction)) {
            float bite = pulse(ticks, 24.0F, 12.0F);
            float snap = pulse(ticks, 27.0F, 4.0F);
            head.z -= 6.0F * biteLunge(ticks);
            head.xRot -= 0.18F * bite;
            jaw.xRot += 0.9F * bite * (1.0F - snap);
        } else if (DragonGroundActions.FAN_BREATH.equals(poseAction)) {
            float roar = fanRoar(ticks);
            float prone = fanProne(ticks);
            head.y += 5.0F * prone;
            head.xRot += -0.52F * roar + 0.3F * prone;
            jaw.xRot += ticks >= DragonGroundActions.FAN_BREATH_START_TICK
                    && ticks < DragonGroundActions.FAN_BREATH_END_TICK ? 0.78F : 0.72F * roar;
        } else if (DragonGroundActions.CRAWL_CHARGE.equals(poseAction)) {
            if (ticks >= DragonGroundActions.CRAWL_CHARGE_START_TICK) {
                head.x = 0.0F;
                head.y = 20.0F;
                head.z = -62.0F;
                head.xRot = 0.0F;
                head.yRot = 0.0F;
                head.zRot = 0.0F;
                jaw.xRot += 0.16F;
            } else {
                float roar = roar(ticks, 0.0F, 14.0F);
                head.xRot -= 0.55F * roar;
                jaw.xRot += 0.78F * roar;
            }
        } else if (DragonGroundActions.BODY_SLAM.equals(poseAction)) {
            float roar = roar(ticks, 0.0F, 12.0F);
            head.xRot -= 0.5F * roar + 0.24F * bodySlamUpright(ticks);
            jaw.xRot += 0.72F * roar;
        } else if (DragonGroundActions.WIND_PRESSURE.equals(poseAction)) {
            float roar = roar(ticks, 0.0F, 16.0F);
            head.xRot -= 0.5F * roar;
            jaw.xRot += 0.76F * roar;
        }
        applyActionPartOverride(head, DragonModelPart.HEAD, action, ticks);
        applyActionPartOverride(jaw, DragonModelPart.JAW, action, ticks);
    }

    public static void applyTailChain(ModelPart tail, DragonBossState state, float ticks, int segment) {
        if (!usesGroundPose(state)) {
            return;
        }

        String action = state.getActionId();
        String poseAction = proceduralAction(action, ticks);
        if (DragonGroundActions.TAIL_SWEEP.equals(poseAction)) {
            return;
        }
        applyGroundBasePart(tail, DragonModelPart.TAIL, action, ticks);
        applyActionPartOverride(tail, DragonModelPart.TAIL, action, ticks);
    }

    public static void applyTailSweep(ModelPart tail, DragonBossState state, float ticks, int segment) {
        if (!usesGroundPose(state)) {
            return;
        }

        String action = state.getActionId();
        if (!DragonGroundActions.TAIL_SWEEP.equals(proceduralAction(action, ticks))) {
            return;
        }

        float fraction = (segment + 1.0F) / 12.0F;
        float bend = tailSweepBend(ticks, tailSign(state));
        float angle = bend * fraction;
        float length = segment * 9.5F;
        tail.x = (float)Math.sin(angle) * length;
        tail.y = 10.0F + (float)Math.sin(fraction * Math.PI) * 2.0F * Math.abs(bend);
        tail.z = 60.0F + (float)Math.cos(angle) * length;
        tail.yRot = (float)Math.PI + angle + bend / 12.0F;
        tail.zRot += tailSign(state) * 0.08F * fraction * tailLook(ticks);
        applyActionPartOverride(tail, DragonModelPart.TAIL, action, ticks);
    }

    public static void applySide(
            ModelPart wing,
            ModelPart wingTip,
            ModelPart frontLeg,
            ModelPart frontLegTip,
            ModelPart frontFoot,
            ModelPart rearLeg,
            ModelPart rearLegTip,
            ModelPart rearFoot,
            DragonBossState state,
            float ticks,
            boolean left
    ) {
        if (!usesGroundPose(state)) {
            return;
        }

        applyQuadrupedBase(wing, wingTip, frontLeg, frontLegTip, frontFoot, rearLeg, rearLegTip, rearFoot, left);
        String action = state.getActionId();
        String poseAction = proceduralAction(action, ticks);
        applyGroundBaseSide(
                wing, wingTip, frontLeg, frontLegTip, frontFoot, rearLeg, rearLegTip, rearFoot,
                action, ticks, left
        );
        if (DragonStageThreeTransition.CATACLYSM.equals(poseAction)) {
            float roar = stageThreeRoar(ticks);
            applyRearStandSide(wing, wingTip, frontLeg, frontLegTip, frontFoot,
                    rearLeg, rearLegTip, rearFoot, left, 0.46F * roar);
            wing.zRot = Mth.lerp(roar, wing.zRot, left ? -1.05F : 1.05F);
            wingTip.zRot = Mth.lerp(roar, wingTip.zRot, left ? -0.42F : 0.42F);
        } else if (DragonPhaseTransition.ULTIMATE_CHARGE.equals(poseAction)) {
            wing.xRot = -0.2F;
            wing.yRot = 0.0F;
            wing.zRot = left ? -1.22F : 1.22F;
            wingTip.zRot = left ? -0.22F : 0.22F;
            frontLeg.xRot = 0.45F;
            frontLegTip.xRot = -0.25F;
            frontFoot.xRot = 0.35F;
            rearLeg.xRot = 1.18F;
            rearLegTip.xRot = 0.32F;
            rearFoot.xRot = 0.55F;
        } else if (DragonGroundActions.BODY_SLAM.equals(poseAction)) {
            applyRearStandSide(wing, wingTip, frontLeg, frontLegTip, frontFoot, rearLeg, rearLegTip, rearFoot, left, bodySlamUpright(ticks));
        } else if (DragonGroundActions.CRAWL_CHARGE.equals(poseAction)) {
            float upright = crawlChargeUpright(ticks);
            applyRearStandSide(wing, wingTip, frontLeg, frontLegTip, frontFoot, rearLeg, rearLegTip, rearFoot, left, upright);
            if (ticks >= DragonGroundActions.CRAWL_CHARGE_START_TICK) {
                applyWalkCycle(
                        frontLeg, frontLegTip, frontFoot, rearLeg, rearLegTip, rearFoot, left,
                        (ticks - DragonGroundActions.CRAWL_CHARGE_START_TICK) * 0.8F,
                        1.0F
                );
                wing.xRot = 0.06F;
                wing.yRot = left ? -0.08F : 0.08F;
                wing.zRot = left ? -0.08F : 0.08F;
                wingTip.zRot = left ? -1.28F : 1.28F;
            }
        } else if (DragonGroundActions.BITE.equals(poseAction)) {
            float walking = biteWalkingAmount(ticks);
            applyWalkCycle(frontLeg, frontLegTip, frontFoot, rearLeg, rearLegTip, rearFoot, left, (ticks - 10.0F) * Mth.TWO_PI / 14.0F, walking);
        } else if (DragonGroundActions.FAN_BREATH.equals(poseAction)) {
            float roar = fanRoar(ticks);
            float prone = fanProne(ticks);
            wing.zRot = Mth.lerp(roar, wing.zRot, left ? -0.52F : 0.52F);
            wingTip.zRot = Mth.lerp(roar, wingTip.zRot, left ? -0.65F : 0.65F);
            wing.xRot = Mth.lerp(prone, wing.xRot, 0.05F);
            wing.zRot = Mth.lerp(prone, wing.zRot, left ? -0.08F : 0.08F);
            wingTip.zRot = Mth.lerp(prone, wingTip.zRot, left ? -1.35F : 1.35F);
            frontLeg.xRot = Mth.lerp(prone, frontLeg.xRot, 1.55F);
            frontLegTip.xRot = Mth.lerp(prone, frontLegTip.xRot, -0.9F);
            frontFoot.xRot = Mth.lerp(prone, frontFoot.xRot, 0.42F);
            rearLeg.xRot = Mth.lerp(prone, rearLeg.xRot, 1.18F);
            rearLegTip.xRot = Mth.lerp(prone, rearLegTip.xRot, 0.2F);
        } else if (DragonGroundActions.TAIL_SWEEP.equals(poseAction)) {
            float brace = tailLook(ticks);
            wing.zRot = Mth.lerp(brace, wing.zRot, left ? -0.08F : 0.08F);
            frontLeg.xRot += 0.14F * brace;
            rearLeg.xRot -= 0.12F * brace;
        } else if (DragonGroundActions.WIND_PRESSURE.equals(poseAction)) {
            float progress = smooth(Mth.clamp(ticks / 20.0F, 0.0F, 1.0F));
            wing.xRot = Mth.lerp(progress, wing.xRot, -0.15F);
            wing.yRot = Mth.lerp(progress, wing.yRot, 0.0F);
            wing.zRot = Mth.lerp(progress, wing.zRot, left ? -0.95F : 0.95F);
            wingTip.zRot = Mth.lerp(progress, wingTip.zRot, left ? -0.35F : 0.35F);
            frontLeg.xRot += 0.28F * progress;
            rearLeg.xRot += 0.22F * progress;
        }

        applyActionPartOverride(wing, left ? DragonModelPart.LEFT_WING : DragonModelPart.RIGHT_WING, action, ticks);
        applyActionPartOverride(wingTip, left ? DragonModelPart.LEFT_WING_TIP : DragonModelPart.RIGHT_WING_TIP, action, ticks);
        applyActionPartOverride(frontLeg, left ? DragonModelPart.LEFT_FRONT_LEG : DragonModelPart.RIGHT_FRONT_LEG, action, ticks);
        applyActionPartOverride(frontLegTip, left ? DragonModelPart.LEFT_FRONT_LEG_TIP : DragonModelPart.RIGHT_FRONT_LEG_TIP, action, ticks);
        applyActionPartOverride(frontFoot, left ? DragonModelPart.LEFT_FRONT_FOOT : DragonModelPart.RIGHT_FRONT_FOOT, action, ticks);
        applyActionPartOverride(rearLeg, left ? DragonModelPart.LEFT_REAR_LEG : DragonModelPart.RIGHT_REAR_LEG, action, ticks);
        applyActionPartOverride(rearLegTip, left ? DragonModelPart.LEFT_REAR_LEG_TIP : DragonModelPart.RIGHT_REAR_LEG_TIP, action, ticks);
        applyActionPartOverride(rearFoot, left ? DragonModelPart.LEFT_REAR_FOOT : DragonModelPart.RIGHT_REAR_FOOT, action, ticks);
    }

    private static void applyGroundBaseRoot(PoseStack poseStack, ModelPart body, String action, float ticks) {
        float baseTicks = DragonBossState.NO_ACTION.equals(action) ? ticks : 0.0F;
        applyRootOverride(poseStack, DragonBossState.NO_ACTION, baseTicks);
        applyPartOverride(body, DragonModelPart.BODY, DragonBossState.NO_ACTION, baseTicks);
    }

    private static String proceduralAction(String action, float ticks) {
        return DragonAnimationOverrideLibrary.isIdlePoseFrame(action, ticks) ? DragonBossState.NO_ACTION : action;
    }

    private static void applyGroundBasePart(ModelPart part, DragonModelPart modelPart, String action, float ticks) {
        float baseTicks = DragonBossState.NO_ACTION.equals(action) ? ticks : 0.0F;
        applyPartOverride(part, modelPart, DragonBossState.NO_ACTION, baseTicks);
    }

    private static void applyGroundBaseSide(
            ModelPart wing,
            ModelPart wingTip,
            ModelPart frontLeg,
            ModelPart frontLegTip,
            ModelPart frontFoot,
            ModelPart rearLeg,
            ModelPart rearLegTip,
            ModelPart rearFoot,
            String action,
            float ticks,
            boolean left
    ) {
        applyGroundBasePart(wing, left ? DragonModelPart.LEFT_WING : DragonModelPart.RIGHT_WING, action, ticks);
        applyGroundBasePart(wingTip, left ? DragonModelPart.LEFT_WING_TIP : DragonModelPart.RIGHT_WING_TIP, action, ticks);
        applyGroundBasePart(frontLeg, left ? DragonModelPart.LEFT_FRONT_LEG : DragonModelPart.RIGHT_FRONT_LEG, action, ticks);
        applyGroundBasePart(frontLegTip, left ? DragonModelPart.LEFT_FRONT_LEG_TIP : DragonModelPart.RIGHT_FRONT_LEG_TIP, action, ticks);
        applyGroundBasePart(frontFoot, left ? DragonModelPart.LEFT_FRONT_FOOT : DragonModelPart.RIGHT_FRONT_FOOT, action, ticks);
        applyGroundBasePart(rearLeg, left ? DragonModelPart.LEFT_REAR_LEG : DragonModelPart.RIGHT_REAR_LEG, action, ticks);
        applyGroundBasePart(rearLegTip, left ? DragonModelPart.LEFT_REAR_LEG_TIP : DragonModelPart.RIGHT_REAR_LEG_TIP, action, ticks);
        applyGroundBasePart(rearFoot, left ? DragonModelPart.LEFT_REAR_FOOT : DragonModelPart.RIGHT_REAR_FOOT, action, ticks);
    }

    private static void applyActionRootOverride(PoseStack poseStack, String action, float ticks) {
        if (!DragonBossState.NO_ACTION.equals(action)) {
            applyRootOverride(poseStack, action, ticks);
        }
    }

    private static void applyActionPartOverride(ModelPart part, DragonModelPart modelPart, String action, float ticks) {
        if (!DragonBossState.NO_ACTION.equals(action)) {
            applyPartOverride(part, modelPart, action, ticks);
        }
    }

    private static void applyRootOverride(PoseStack poseStack, String action, float ticks) {
        DragonPoseTransform transform = DragonAnimationOverrideLibrary.sample(action, DragonModelPart.ROOT, ticks);
        poseStack.translate(transform.x(), transform.y(), transform.z());
        poseStack.mulPose(Axis.XP.rotationDegrees(transform.xRot()));
        poseStack.mulPose(Axis.YP.rotationDegrees(transform.yRot()));
        poseStack.mulPose(Axis.ZP.rotationDegrees(transform.zRot()));
    }

    private static void applyPartOverride(ModelPart part, DragonModelPart modelPart, String action, float ticks) {
        DragonPoseTransform transform = DragonAnimationOverrideLibrary.sample(action, modelPart, ticks);
        part.x += transform.x();
        part.y += transform.y();
        part.z += transform.z();
        part.xRot += transform.xRot() * Mth.DEG_TO_RAD;
        part.yRot += transform.yRot() * Mth.DEG_TO_RAD;
        part.zRot += transform.zRot() * Mth.DEG_TO_RAD;
    }

    private static void applyQuadrupedBase(
            ModelPart wing,
            ModelPart wingTip,
            ModelPart frontLeg,
            ModelPart frontLegTip,
            ModelPart frontFoot,
            ModelPart rearLeg,
            ModelPart rearLegTip,
            ModelPart rearFoot,
            boolean left
    ) {
        wing.xRot = GROUND_WING_X;
        wing.yRot = left ? -GROUND_WING_Y : GROUND_WING_Y;
        wing.zRot = left ? -GROUND_WING_Z : GROUND_WING_Z;
        wingTip.zRot = left ? -GROUND_WING_TIP_Z : GROUND_WING_TIP_Z;
        frontLeg.xRot = 1.15F;
        frontLegTip.xRot = -0.62F;
        frontFoot.xRot = 0.82F;
        rearLeg.xRot = 0.92F;
        rearLegTip.xRot = 0.58F;
        rearFoot.xRot = 0.72F;
    }

    private static void applyWalkCycle(
            ModelPart frontLeg,
            ModelPart frontLegTip,
            ModelPart frontFoot,
            ModelPart rearLeg,
            ModelPart rearLegTip,
            ModelPart rearFoot,
            boolean left,
            float phase,
            float amount
    ) {
        float stride = (float)Math.sin(phase) * (left ? 1.0F : -1.0F) * amount;
        float frontLift = Math.max(0.0F, stride);
        float rearLift = Math.max(0.0F, -stride);
        frontLeg.xRot += stride * 0.48F;
        frontLegTip.xRot -= frontLift * 0.42F;
        frontFoot.xRot += frontLift * 0.28F;
        rearLeg.xRot -= stride * 0.38F;
        rearLegTip.xRot -= rearLift * 0.3F;
        rearFoot.xRot += rearLift * 0.22F;
    }

    private static void applyRearStandRoot(PoseStack poseStack, ModelPart body, float amount) {
        if (amount <= 0.0F) {
            return;
        }
        poseStack.translate(0.0F, 0.72F * amount, 0.82F * amount);
        poseStack.mulPose(Axis.XP.rotationDegrees(36.0F * amount));
        body.xRot -= 0.2F * amount;
    }

    private static void applyBodySlamReadyRoot(PoseStack poseStack, ModelPart body, float amount) {
        if (amount <= 0.0F) {
            return;
        }
        poseStack.translate(0.0F, 0.82F * amount, 0.55F * amount);
        poseStack.mulPose(Axis.XP.rotationDegrees(-32.0F * amount));
        body.xRot += 0.18F * amount;
    }

    private static void applyRearStandSide(
            ModelPart wing,
            ModelPart wingTip,
            ModelPart frontLeg,
            ModelPart frontLegTip,
            ModelPart frontFoot,
            ModelPart rearLeg,
            ModelPart rearLegTip,
            ModelPart rearFoot,
            boolean left,
            float amount
    ) {
        rearLeg.xRot = Mth.lerp(amount, rearLeg.xRot, 0.12F);
        rearLegTip.xRot = Mth.lerp(amount, rearLegTip.xRot, 0.2F);
        rearFoot.xRot = Mth.lerp(amount, rearFoot.xRot, 0.08F);
        frontLeg.xRot = Mth.lerp(amount, frontLeg.xRot, -0.72F);
        frontLegTip.xRot = Mth.lerp(amount, frontLegTip.xRot, 0.42F);
        frontFoot.xRot = Mth.lerp(amount, frontFoot.xRot, 0.12F);
        wing.zRot = Mth.lerp(amount, wing.zRot, left ? -0.48F : 0.48F);
        wingTip.zRot = Mth.lerp(amount, wingTip.zRot, left ? -0.7F : 0.7F);
    }

    private static float bodySlamUpright(float ticks) {
        if (ticks < 12.0F || ticks >= DragonGroundActions.BODY_SLAM_IMPACT_TICK) {
            return 0.0F;
        }
        if (ticks < 25.0F) {
            return smooth(Mth.clamp((ticks - 12.0F) / 13.0F, 0.0F, 1.0F));
        }
        return 1.0F - smooth(Mth.clamp(
                (ticks - 25.0F) / (DragonGroundActions.BODY_SLAM_IMPACT_TICK - 25.0F),
                0.0F,
                1.0F
        ));
    }

    private static float crawlChargeUpright(float ticks) {
        if (ticks < 6.0F) {
            return smooth(Mth.clamp(ticks / 6.0F, 0.0F, 1.0F));
        }
        if (ticks < 14.0F) {
            return 1.0F;
        }
        return 1.0F - smooth(Mth.clamp(
                (ticks - 14.0F) / (DragonGroundActions.CRAWL_CHARGE_START_TICK - 14.0F),
                0.0F,
                1.0F
        ));
    }

    private static float biteWalkingAmount(float ticks) {
        if (ticks < 10.0F || ticks >= 24.0F) {
            return 0.0F;
        }
        float enter = smooth(Mth.clamp((ticks - 10.0F) / 3.0F, 0.0F, 1.0F));
        float exit = 1.0F - smooth(Mth.clamp((ticks - 21.0F) / 3.0F, 0.0F, 1.0F));
        return Math.min(enter, exit);
    }

    private static float biteLunge(float ticks) {
        float firstStep = pulse(ticks, 13.0F, 6.0F) * 0.3F;
        float secondStep = pulse(ticks, 20.0F, 6.0F) * 0.5F;
        float bite = pulse(ticks, 26.0F, 8.0F);
        return Mth.clamp(firstStep + secondStep + bite, 0.0F, 1.0F);
    }

    private static float fanRoar(float ticks) {
        return roar(ticks, 0.0F, 11.0F);
    }

    private static float stageThreeRoar(float ticks) {
        return smooth(Mth.clamp(ticks / 18.0F, 0.0F, 1.0F));
    }

    private static float fanProne(float ticks) {
        if (ticks < 10.0F) {
            return 0.0F;
        }
        if (ticks < DragonGroundActions.FAN_BREATH_START_TICK) {
            return smooth(Mth.clamp(
                    (ticks - 10.0F) / (DragonGroundActions.FAN_BREATH_START_TICK - 10.0F),
                    0.0F,
                    1.0F
            ));
        }
        if (ticks < DragonGroundActions.FAN_BREATH_END_TICK) {
            return 1.0F;
        }
        return 1.0F - smooth(Mth.clamp((ticks - DragonGroundActions.FAN_BREATH_END_TICK) / 10.0F, 0.0F, 1.0F));
    }

    private static float roar(float ticks, float start, float end) {
        float local = ticks - start;
        float duration = end - start;
        if (local < 0.0F || local >= duration) {
            return 0.0F;
        }
        float enter = smooth(Mth.clamp(local / 4.0F, 0.0F, 1.0F));
        float exit = 1.0F - smooth(Mth.clamp((local - (duration - 4.0F)) / 4.0F, 0.0F, 1.0F));
        return Math.min(enter, exit);
    }

    private static float tailLook(float ticks) {
        return ticks <= 15.0F
                ? smooth(Mth.clamp(ticks / 15.0F, 0.0F, 1.0F))
                : 1.0F - smooth(Mth.clamp((ticks - 15.0F) / 8.0F, 0.0F, 1.0F));
    }

    private static float tailSweepBend(float ticks, float sign) {
        if (ticks <= 15.0F) {
            return sign * 0.95F * smooth(Mth.clamp(ticks / 15.0F, 0.0F, 1.0F));
        }
        if (ticks <= 23.0F) {
            float sweep = smooth(Mth.clamp((ticks - 15.0F) / 8.0F, 0.0F, 1.0F));
            return sign * Mth.lerp(sweep, 0.95F, -1.1F);
        }
        float recover = smooth(Mth.clamp((ticks - 23.0F) / 12.0F, 0.0F, 1.0F));
        return -sign * 1.1F * (1.0F - recover);
    }

    private static float tailSign(DragonBossState state) {
        return state.getActionDirection() == null || state.getActionDirection().x >= 0.0 ? 1.0F : -1.0F;
    }

    private static float pulse(float ticks, float center, float width) {
        return Mth.clamp(1.0F - Math.abs(ticks - center) / (width * 0.5F), 0.0F, 1.0F);
    }

    private static float smooth(float value) {
        return value * value * (3.0F - 2.0F * value);
    }
}
