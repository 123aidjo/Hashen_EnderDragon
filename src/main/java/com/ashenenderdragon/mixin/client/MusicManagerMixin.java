package com.ashenenderdragon.mixin.client;

import com.ashenenderdragon.client.sound.DragonBossMusicController;

import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents vanilla End/dragon music from starting while a transition track owns the music channel. */
@Mixin(MusicManager.class)
public abstract class MusicManagerMixin {
    @Shadow
    public abstract void stopPlaying();

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void ashenenderdragon$suppressVanillaMusic(CallbackInfo callbackInfo) {
        if (DragonBossMusicController.isSuppressingVanillaMusic()) {
            this.stopPlaying();
            callbackInfo.cancel();
        }
    }
}
