package com.ashenenderdragon.mixin.client;

import javax.annotation.Nullable;

import com.ashenenderdragon.boss.DragonBossState;
import com.ashenenderdragon.client.animation.DragonAnimationController;
import com.ashenenderdragon.client.animation.DragonAnimationDebugSession;
import com.ashenenderdragon.registry.ModAttachments;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EnderDragonRenderer;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds action poses after vanilla procedural values are calculated; neck changes run before its segment-position recurrence. */
@Mixin(EnderDragonRenderer.DragonModel.class)
public abstract class EnderDragonModelMixin {
    @Shadow
    @Nullable
    private EnderDragon entity;
    @Shadow
    private float a;
    @Shadow
    @Final
    private ModelPart head;
    @Shadow
    @Final
    private ModelPart neck;
    @Shadow
    @Final
    private ModelPart jaw;
    @Shadow
    @Final
    private ModelPart body;
    @Shadow
    @Final
    private ModelPart leftWing;
    @Shadow
    @Final
    private ModelPart leftWingTip;
    @Shadow
    @Final
    private ModelPart rightWing;
    @Shadow
    @Final
    private ModelPart rightWingTip;
    @Shadow
    @Final
    private ModelPart leftFrontLeg;
    @Shadow
    @Final
    private ModelPart leftFrontLegTip;
    @Shadow
    @Final
    private ModelPart leftFrontFoot;
    @Shadow
    @Final
    private ModelPart leftRearLeg;
    @Shadow
    @Final
    private ModelPart leftRearLegTip;
    @Shadow
    @Final
    private ModelPart leftRearFoot;
    @Shadow
    @Final
    private ModelPart rightFrontLeg;
    @Shadow
    @Final
    private ModelPart rightFrontLegTip;
    @Shadow
    @Final
    private ModelPart rightFrontFoot;
    @Shadow
    @Final
    private ModelPart rightRearLeg;
    @Shadow
    @Final
    private ModelPart rightRearLegTip;
    @Shadow
    @Final
    private ModelPart rightRearFoot;

    @Unique
    private boolean ashenenderdragon$flapFrozen;
    @Unique
    private float ashenenderdragon$savedFlapTime;
    @Unique
    private float ashenenderdragon$savedOldFlapTime;
    @Unique
    private int ashenenderdragon$frontNeckIndex;
    @Unique
    private int ashenenderdragon$tailIndex;

    @Inject(method = "renderToBuffer", at = @At("HEAD"))
    private void ashenenderdragon$pushActionPose(
            PoseStack poseStack,
            VertexConsumer buffer,
            int packedLight,
            int packedOverlay,
            int color,
            CallbackInfo callbackInfo
    ) {
        poseStack.pushPose();
        this.ashenenderdragon$resetParts();
        this.ashenenderdragon$frontNeckIndex = 0;
        this.ashenenderdragon$tailIndex = 0;
        DragonBossState state = this.ashenenderdragon$getState();
        if (state == null) {
            return;
        }

        if (DragonAnimationController.usesGroundPose(state) && this.entity != null) {
            this.ashenenderdragon$flapFrozen = true;
            this.ashenenderdragon$savedFlapTime = this.entity.flapTime;
            this.ashenenderdragon$savedOldFlapTime = this.entity.oFlapTime;
            this.entity.flapTime = 0.0F;
            this.entity.oFlapTime = 0.0F;
            for (double[] history : this.entity.positions) {
                history[0] = this.entity.getYRot();
                history[1] = this.entity.getY();
            }
        }

        DragonAnimationController.applyRoot(poseStack, this.body, state, this.ashenenderdragon$actionTicks(state));
    }

    @Inject(method = "renderToBuffer", at = @At("RETURN"))
    private void ashenenderdragon$popActionPose(
            PoseStack poseStack,
            VertexConsumer buffer,
            int packedLight,
            int packedOverlay,
            int color,
            CallbackInfo callbackInfo
    ) {
        if (this.ashenenderdragon$flapFrozen && this.entity != null) {
            this.entity.flapTime = this.ashenenderdragon$savedFlapTime;
            this.entity.oFlapTime = this.ashenenderdragon$savedOldFlapTime;
            this.ashenenderdragon$flapFrozen = false;
        }
        poseStack.popPose();
    }

    @Inject(
            method = "renderToBuffer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Mth;sin(F)F",
                    ordinal = 0,
                    shift = At.Shift.BEFORE
            )
    )
    private void ashenenderdragon$poseFrontNeck(
            PoseStack poseStack,
            VertexConsumer buffer,
            int packedLight,
            int packedOverlay,
            int color,
            CallbackInfo callbackInfo
    ) {
        DragonBossState state = this.ashenenderdragon$getState();
        int segment = this.ashenenderdragon$frontNeckIndex++;
        if (state == null) {
            return;
        }
        DragonAnimationController.applyNeck(this.neck, state, this.ashenenderdragon$actionTicks(state), segment);
    }

    @Inject(
            method = "renderToBuffer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
                    ordinal = 1,
                    shift = At.Shift.BEFORE
            )
    )
    private void ashenenderdragon$poseHead(
            PoseStack poseStack,
            VertexConsumer buffer,
            int packedLight,
            int packedOverlay,
            int color,
            CallbackInfo callbackInfo
    ) {
        DragonBossState state = this.ashenenderdragon$getState();
        if (state == null) {
            return;
        }
        DragonAnimationController.applyHead(this.head, this.jaw, state, this.ashenenderdragon$actionTicks(state));
    }

    @Inject(
            method = "renderToBuffer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Mth;sin(F)F",
                    ordinal = 3,
                    shift = At.Shift.BEFORE
            )
    )
    private void ashenenderdragon$poseTailChain(
            PoseStack poseStack,
            VertexConsumer buffer,
            int packedLight,
            int packedOverlay,
            int color,
            CallbackInfo callbackInfo
    ) {
        DragonBossState state = this.ashenenderdragon$getState();
        int segment = this.ashenenderdragon$tailIndex++;
        if (state != null) {
            DragonAnimationController.applyTailChain(this.neck, state, this.ashenenderdragon$actionTicks(state), segment);
        }
    }

    @Inject(
            method = "renderToBuffer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
                    ordinal = 3,
                    shift = At.Shift.BEFORE
            )
    )
    private void ashenenderdragon$poseTailSweep(
            PoseStack poseStack,
            VertexConsumer buffer,
            int packedLight,
            int packedOverlay,
            int color,
            CallbackInfo callbackInfo
    ) {
        DragonBossState state = this.ashenenderdragon$getState();
        if (state != null) {
            DragonAnimationController.applyTailSweep(
                    this.neck,
                    state,
                    this.ashenenderdragon$actionTicks(state),
                    Math.max(0, this.ashenenderdragon$tailIndex - 1)
            );
        }
    }

    @Inject(
            method = "renderSide",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
                    ordinal = 0,
                    shift = At.Shift.BEFORE
            )
    )
    private void ashenenderdragon$poseWingsAndLegs(
            PoseStack poseStack,
            VertexConsumer buffer,
            int packedLight,
            int packedOverlay,
            float rotation,
            ModelPart wing,
            ModelPart frontLeg,
            ModelPart frontLegTip,
            ModelPart frontFoot,
            ModelPart rearLeg,
            ModelPart rearLegTip,
            ModelPart rearFoot,
            int color,
            CallbackInfo callbackInfo
    ) {
        DragonBossState state = this.ashenenderdragon$getState();
        if (state == null) {
            return;
        }
        float ticks = this.ashenenderdragon$actionTicks(state);
        boolean left = wing == this.leftWing;
        ModelPart wingTip = left ? this.leftWingTip : this.rightWingTip;
        DragonAnimationController.applySide(
                wing,
                wingTip,
                frontLeg,
                frontLegTip,
                frontFoot,
                rearLeg,
                rearLegTip,
                rearFoot,
                state,
                ticks,
                left
        );
    }

    @Nullable
    private DragonBossState ashenenderdragon$getState() {
        if (this.entity == null) {
            return null;
        }
        DragonBossState attached = this.entity.getExistingDataOrNull(ModAttachments.DRAGON_BOSS_STATE.get());
        return DragonAnimationDebugSession.effectiveState(this.entity, attached);
    }

    private float ashenenderdragon$actionTicks(DragonBossState state) {
        if (this.entity == null) {
            return 0.0F;
        }
        float normalTicks = Math.max(0.0F, (float)(this.entity.level().getGameTime() - state.getActionStartedAt()) + this.a);
        return DragonAnimationDebugSession.renderTicks(this.entity, this.a, normalTicks);
    }

    @Unique
    private void ashenenderdragon$resetParts() {
        this.head.resetPose();
        this.neck.resetPose();
        this.jaw.resetPose();
        this.body.resetPose();
        this.leftWing.resetPose();
        this.leftWingTip.resetPose();
        this.rightWing.resetPose();
        this.rightWingTip.resetPose();
        this.leftFrontLeg.resetPose();
        this.leftFrontLegTip.resetPose();
        this.leftFrontFoot.resetPose();
        this.leftRearLeg.resetPose();
        this.leftRearLegTip.resetPose();
        this.leftRearFoot.resetPose();
        this.rightFrontLeg.resetPose();
        this.rightFrontLegTip.resetPose();
        this.rightFrontFoot.resetPose();
        this.rightRearLeg.resetPose();
        this.rightRearLegTip.resetPose();
        this.rightRearFoot.resetPose();
    }
}
