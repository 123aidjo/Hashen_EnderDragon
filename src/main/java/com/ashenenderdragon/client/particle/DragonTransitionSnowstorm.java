package com.ashenenderdragon.client.particle;

import com.ashenenderdragon.AshenEnderDragon;
import com.ashenenderdragon.client.sound.DragonBossMusicController;

import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Creates a local weather-sized snow field without changing the End dimension's saved weather. */
public final class DragonTransitionSnowstorm {
    private static final double HORIZONTAL_RADIUS = 20.0;
    private static final double FORWARD_BIAS = 8.0;
    private static final double BELOW_VIEW = 8.0;
    private static final double VERTICAL_SPAN = 25.0;

    private DragonTransitionSnowstorm() {
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        if (!DragonBossMusicController.isPhaseTwoTransitionActive()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Player player = minecraft.player;
        if (level == null || player == null || level.dimension() != Level.END || minecraft.isPaused()) {
            return;
        }

        int flakesPerTick = switch (minecraft.options.particles().get()) {
            case ALL -> 18;
            case DECREASED -> 9;
            case MINIMAL -> 3;
        };
        RandomSource random = level.random;
        Vec3 look = player.getLookAngle();
        Vec3 horizontalLook = new Vec3(look.x, 0.0, look.z);
        if (horizontalLook.lengthSqr() > 1.0E-4) {
            horizontalLook = horizontalLook.normalize();
        }
        double stormCenterX = player.getX() + horizontalLook.x * FORWARD_BIAS;
        double stormCenterZ = player.getZ() + horizontalLook.z * FORWARD_BIAS;
        double windAngle = level.getGameTime() * 0.0075;
        double windX = Mth.cos((float) windAngle) * 0.025;
        double windZ = Mth.sin((float) windAngle) * 0.025;

        for (int i = 0; i < flakesPerTick; i++) {
            double x = stormCenterX + random.triangle(0.0, HORIZONTAL_RADIUS);
            double y = player.getEyeY() - BELOW_VIEW + random.nextDouble() * VERTICAL_SPAN;
            double z = stormCenterZ + random.triangle(0.0, HORIZONTAL_RADIUS);
            double flutterX = windX + (random.nextDouble() - 0.5) * 0.018;
            double flutterZ = windZ + (random.nextDouble() - 0.5) * 0.018;
            level.addParticle(AshenEnderDragon.ENDER_SNOWFLAKE.get(), x, y, z, flutterX, -0.035, flutterZ);
        }
    }
}
