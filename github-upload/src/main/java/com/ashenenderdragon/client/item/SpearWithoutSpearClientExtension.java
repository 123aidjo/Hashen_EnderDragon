package com.ashenenderdragon.client.item;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/** Reuses the vanilla shield renderer while retaining this item's own stack components. */
public final class SpearWithoutSpearClientExtension implements IClientItemExtensions {
    private static final SpearWithoutSpearClientExtension INSTANCE = new SpearWithoutSpearClientExtension();

    private BlockEntityWithoutLevelRenderer renderer;

    private SpearWithoutSpearClientExtension() {
    }

    public static IClientItemExtensions instance() {
        return INSTANCE;
    }

    @Override
    public BlockEntityWithoutLevelRenderer getCustomRenderer() {
        if (this.renderer == null) {
            Minecraft minecraft = Minecraft.getInstance();
            this.renderer = new BlockEntityWithoutLevelRenderer(
                    minecraft.getBlockEntityRenderDispatcher(),
                    minecraft.getEntityModels()
            ) {
                @Override
                public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
                                         MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
                    ItemStack shieldView = stack.transmuteCopy(Items.SHIELD);
                    minecraft.getItemRenderer().getBlockEntityRenderer().renderByItem(
                            shieldView, displayContext, poseStack, bufferSource, packedLight, packedOverlay
                    );
                }
            };
        }
        return this.renderer;
    }
}
