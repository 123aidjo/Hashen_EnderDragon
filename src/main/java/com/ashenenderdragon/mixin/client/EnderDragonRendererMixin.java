package com.ashenenderdragon.mixin.client;

import com.ashenenderdragon.boss.DragonBossState;
import com.ashenenderdragon.boss.DragonPhaseTransition;
import com.ashenenderdragon.boss.DragonStageThreeTransition;
import com.ashenenderdragon.registry.ModAttachments;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.entity.EnderDragonRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Renders encounter columns with the actual vanilla beacon-beam shader and texture. */
@Mixin(EnderDragonRenderer.class)
public abstract class EnderDragonRendererMixin {
    private static final int ASHEN_PURPLE = 0xFF8A2BE2;
    private static final int SOUL_CYAN = 0xFF35D8E6;
    private static final int ENDER_PURPLE = 0xFFA020F0;

    @Inject(method = "render", at = @At("TAIL"))
    private void ashenenderdragon$renderAltarBeacon(
            EnderDragon dragon,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            CallbackInfo callbackInfo
    ) {
        DragonBossState state = dragon.getExistingDataOrNull(ModAttachments.DRAGON_BOSS_STATE.get());
        if (state == null || state.getActionPositions().isEmpty()) {
            return;
        }
        String action = state.getActionId();
        double dragonX = Mth.lerp(partialTick, dragon.xo, dragon.getX());
        double dragonY = Mth.lerp(partialTick, dragon.yo, dragon.getY());
        double dragonZ = Mth.lerp(partialTick, dragon.zo, dragon.getZ());
        if (DragonStageThreeTransition.CATACLYSM.equals(action)) {
            for (Vec3 encoded : state.getActionPositions()) {
                boolean enderBeam = encoded.y < 0.0;
                Vec3 position = new Vec3(encoded.x, Math.abs(encoded.y), encoded.z);
                poseStack.pushPose();
                poseStack.translate(position.x - 0.5 - dragonX, position.y - dragonY, position.z - 0.5 - dragonZ);
                BeaconRenderer.renderBeaconBeam(
                        poseStack,
                        buffer,
                        BeaconRenderer.BEAM_LOCATION,
                        partialTick,
                        1.0F,
                        dragon.level().getGameTime(),
                        0,
                        BeaconRenderer.MAX_RENDER_Y,
                        enderBeam ? ENDER_PURPLE : SOUL_CYAN,
                        0.45F,
                        0.5F
                );
                poseStack.popPose();
            }
            return;
        }
        if (!DragonPhaseTransition.SPIRAL_ASCENT.equals(action)
                && !DragonPhaseTransition.ULTIMATE_CHARGE.equals(action)) {
            return;
        }

        Vec3 altar = state.getActionPositions().getFirst();
        poseStack.pushPose();
        poseStack.translate(altar.x - 0.5 - dragonX, altar.y - dragonY, altar.z - 0.5 - dragonZ);
        BeaconRenderer.renderBeaconBeam(
                poseStack,
                buffer,
                BeaconRenderer.BEAM_LOCATION,
                partialTick,
                1.0F,
                dragon.level().getGameTime(),
                0,
                BeaconRenderer.MAX_RENDER_Y,
                ASHEN_PURPLE,
                2.5F,
                3.0F
        );
        poseStack.popPose();
    }
}
