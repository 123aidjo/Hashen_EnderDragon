package com.ashenenderdragon.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SnowflakeParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;

/** A brighter, slower purple snowflake used by the phase-two transition storm. */
public final class EnderSnowflakeParticle extends SnowflakeParticle {
    private EnderSnowflakeParticle(
            ClientLevel level,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed,
            SpriteSet sprites) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
        this.gravity = 0.12F;
        this.quadSize *= 1.35F;
        this.lifetime = 55 + this.random.nextInt(45);

        float shade = this.random.nextFloat();
        this.setColor(0.58F + shade * 0.22F, 0.18F + shade * 0.12F, 0.95F);
    }

    @Override
    public int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
                SimpleParticleType type,
                ClientLevel level,
                double x,
                double y,
                double z,
                double xSpeed,
                double ySpeed,
                double zSpeed) {
            return new EnderSnowflakeParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites);
        }
    }
}
