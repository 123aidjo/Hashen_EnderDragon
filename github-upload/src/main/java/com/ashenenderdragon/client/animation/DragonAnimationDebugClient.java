package com.ashenenderdragon.client.animation;

import java.util.Comparator;
import java.util.List;

import com.ashenenderdragon.client.screen.DragonAnimationDebugScreen;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

import org.lwjgl.glfw.GLFW;

/** Key binding and lifecycle bridge for the local animation editor. */
public final class DragonAnimationDebugClient {
    public static final KeyMapping OPEN_EDITOR = new KeyMapping(
            "key.ashenenderdragon.animation_editor",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            "key.categories.ashenenderdragon"
    );

    private DragonAnimationDebugClient() {
    }

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_EDITOR);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (DragonAnimationDebugSession.isActive()
                && (minecraft.level == null || DragonAnimationDebugSession.previewDragon() == null
                || DragonAnimationDebugSession.previewDragon().level() != minecraft.level)) {
            DragonAnimationDebugSession.stop();
        } else if (DragonAnimationDebugSession.targetEntityId() >= 0
                && !(minecraft.level.getEntity(DragonAnimationDebugSession.targetEntityId()) instanceof EnderDragon)) {
            DragonAnimationDebugSession.clearWorldTarget();
        }
        DragonAnimationDebugSession.tick();

        while (OPEN_EDITOR.consumeClick()) {
            if (minecraft.screen instanceof DragonAnimationDebugScreen) {
                minecraft.setScreen(null);
                continue;
            }
            if (minecraft.screen != null || minecraft.level == null || minecraft.player == null) {
                continue;
            }

            EnderDragon target = currentOrNearestDragon(minecraft);
            DragonAnimationDebugSession.start(minecraft.level, target);
            minecraft.setScreen(new DragonAnimationDebugScreen());
        }
    }

    private static EnderDragon currentOrNearestDragon(Minecraft minecraft) {
        if (DragonAnimationDebugSession.targetEntityId() >= 0
                && minecraft.level.getEntity(DragonAnimationDebugSession.targetEntityId()) instanceof EnderDragon dragon) {
            return dragon;
        }
        List<EnderDragon> dragons = minecraft.level.getEntitiesOfClass(
                EnderDragon.class,
                minecraft.player.getBoundingBox().inflate(256.0),
                EnderDragon::isAlive
        );
        return dragons.stream().min(Comparator.comparingDouble(minecraft.player::distanceToSqr)).orElse(null);
    }
}
