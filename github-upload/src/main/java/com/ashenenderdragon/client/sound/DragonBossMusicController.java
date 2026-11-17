package com.ashenenderdragon.client.sound;

import java.util.UUID;

import javax.annotation.Nullable;

import com.ashenenderdragon.AshenEnderDragon;
import com.ashenenderdragon.boss.DragonBossMode;
import com.ashenenderdragon.boss.DragonBossState;
import com.ashenenderdragon.boss.DragonPhaseTransition;
import com.ashenenderdragon.registry.ModAttachments;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Gives transition music priority over vanilla End/dragon music and the Enderman stare loop. */
public final class DragonBossMusicController {
    private static EncounterTrack activeTrack = EncounterTrack.NONE;
    @Nullable
    private static UUID activeDragon;
    @Nullable
    private static SoundInstance activeSound;
    private static long activeElapsedNanos;
    private static long lastClockNanos;
    private static EncounterTrack completedTrack = EncounterTrack.NONE;
    @Nullable
    private static UUID completedDragon;

    private DragonBossMusicController() {
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        long now = System.nanoTime();
        if (activeTrack != EncounterTrack.NONE) {
            advancePlaybackClock(minecraft, now);
        }

        // A real disconnect ends the encounter. Temporary null level/player states during respawn do not.
        if (minecraft.getConnection() == null) {
            reset(minecraft);
            return;
        }

        TrackSelection selection = findTrack(minecraft);
        if (activeTrack != EncounterTrack.NONE) {
            if (selection.track() != EncounterTrack.NONE
                    && (activeTrack != selection.track() || !selection.dragonUuid().equals(activeDragon))) {
                start(minecraft, selection, now);
            } else if (activeElapsedNanos >= activeTrack.durationNanos) {
                finishActiveTrack(minecraft);
            } else {
                suppressCompetingAudio(minecraft);
            }
            return;
        }

        if (selection.track() != EncounterTrack.NONE && !wasCompleted(selection)) {
            start(minecraft, selection, now);
        }
    }

    public static boolean isSuppressingVanillaMusic() {
        return activeSound != null;
    }

    public static boolean isPhaseTwoTransitionActive() {
        return activeTrack == EncounterTrack.PHASE_TWO;
    }

    private static TrackSelection findTrack(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            return TrackSelection.NONE;
        }
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof EnderDragon dragon)) {
                continue;
            }
            DragonBossState state = dragon.getExistingDataOrNull(ModAttachments.DRAGON_BOSS_STATE.get());
            if (state == null || !state.isInitialized() || dragon.isDeadOrDying()) {
                continue;
            }

            if (state.getBossStage() == 2 && (state.getMode() == DragonBossMode.STAGE_THREE_TRANSITION
                    || state.getMode() == DragonBossMode.GROUNDED && state.getStageTwoGroundedCount() >= 2)) {
                return new TrackSelection(EncounterTrack.PHASE_THREE, dragon.getUUID());
            }
            if (state.getMode() == DragonBossMode.PHASE_TRANSITION
                    && (DragonPhaseTransition.SPIRAL_ASCENT.equals(state.getActionId())
                            || DragonPhaseTransition.ULTIMATE_CHARGE.equals(state.getActionId())
                            || DragonPhaseTransition.ORB_DESCENT.equals(state.getActionId()))) {
                return new TrackSelection(EncounterTrack.PHASE_TWO, dragon.getUUID());
            }
        }
        return TrackSelection.NONE;
    }

    private static boolean wasCompleted(TrackSelection selection) {
        return completedTrack == selection.track() && selection.dragonUuid().equals(completedDragon);
    }

    private static void start(Minecraft minecraft, TrackSelection selection, long now) {
        if (activeTrack != EncounterTrack.NONE) {
            completedTrack = activeTrack;
            completedDragon = activeDragon;
        }
        stopActiveSound(minecraft);
        SoundInstance sound = SimpleSoundInstance.forMusic(switch (selection.track()) {
            case PHASE_TWO -> AshenEnderDragon.PHASE_TWO_TRANSITION_BGM.get();
            case PHASE_THREE -> AshenEnderDragon.PHASE_THREE_TRANSITION_BGM.get();
            case NONE -> throw new IllegalStateException("Cannot play the empty dragon music track");
        });
        activeTrack = selection.track();
        activeDragon = selection.dragonUuid();
        // Keep the encounter clock (also used by snow visuals) in music-free builds.
        String musicFile = selection.track() == EncounterTrack.PHASE_TWO
                ? "phase_two_transition" : "phase_three_transition";
        boolean hasMusic = minecraft.getResourceManager().getResource(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                        AshenEnderDragon.MODID, "sounds/music/" + musicFile + ".ogg")).isPresent();
        activeSound = hasMusic ? sound : null;
        activeElapsedNanos = 0L;
        lastClockNanos = now;
        if (hasMusic) {
            minecraft.getMusicManager().stopPlaying();
            minecraft.getSoundManager().play(sound);
        }
    }

    private static void advancePlaybackClock(Minecraft minecraft, long now) {
        if (lastClockNanos != 0L && !minecraft.isPaused()) {
            activeElapsedNanos += Math.max(0L, now - lastClockNanos);
        }
        lastClockNanos = now;
    }

    private static void suppressCompetingAudio(Minecraft minecraft) {
        if (activeSound == null) {
            return;
        }
        minecraft.getMusicManager().stopPlaying();
        minecraft.getSoundManager().stop(SoundEvents.ENDERMAN_STARE.getLocation(), SoundSource.HOSTILE);
    }

    public static void stopWorldSoundsExceptTransitionMusic(SoundManager soundManager) {
        for (SoundSource source : SoundSource.values()) {
            if (source != SoundSource.MUSIC) {
                soundManager.stop(null, source);
            }
        }
    }

    private static void finishActiveTrack(Minecraft minecraft) {
        completedTrack = activeTrack;
        completedDragon = activeDragon;
        stopActiveSound(minecraft);
    }

    private static void reset(Minecraft minecraft) {
        stopActiveSound(minecraft);
        completedTrack = EncounterTrack.NONE;
        completedDragon = null;
    }

    private static void stopActiveSound(Minecraft minecraft) {
        if (activeSound != null) {
            minecraft.getSoundManager().stop(activeSound);
        }
        activeTrack = EncounterTrack.NONE;
        activeDragon = null;
        activeSound = null;
        activeElapsedNanos = 0L;
        lastClockNanos = 0L;
    }

    private enum EncounterTrack {
        NONE(0L),
        PHASE_TWO(150L * 1_000_000_000L),
        PHASE_THREE(120L * 1_000_000_000L);

        private final long durationNanos;

        EncounterTrack(long durationNanos) {
            this.durationNanos = durationNanos;
        }
    }

    private record TrackSelection(EncounterTrack track, @Nullable UUID dragonUuid) {
        private static final TrackSelection NONE = new TrackSelection(EncounterTrack.NONE, null);
    }
}
