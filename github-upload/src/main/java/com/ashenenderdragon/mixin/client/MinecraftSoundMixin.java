package com.ashenenderdragon.mixin.client;

import com.ashenenderdragon.client.sound.DragonBossMusicController;

import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keeps the global transition track alive when death moves the client between dimensions. */
@Mixin(Minecraft.class)
public abstract class MinecraftSoundMixin {
    @Redirect(
            method = "updateScreenAndTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/sounds/SoundManager;stop()V"
            )
    )
    private void ashenenderdragon$keepTransitionMusicAcrossLevelChange(SoundManager soundManager) {
        if (DragonBossMusicController.isSuppressingVanillaMusic()) {
            DragonBossMusicController.stopWorldSoundsExceptTransitionMusic(soundManager);
        } else {
            soundManager.stop();
        }
    }
}
