package com.ashenenderdragon.client.animation;

import net.minecraft.util.Mth;

/** Additive pose offset. Positions use model units, rotations use degrees. */
public record DragonPoseTransform(
        float x,
        float y,
        float z,
        float xRot,
        float yRot,
        float zRot
) {
    public static final DragonPoseTransform ZERO = new DragonPoseTransform(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);

    public static DragonPoseTransform lerp(float progress, DragonPoseTransform from, DragonPoseTransform to) {
        return new DragonPoseTransform(
                Mth.lerp(progress, from.x, to.x),
                Mth.lerp(progress, from.y, to.y),
                Mth.lerp(progress, from.z, to.z),
                Mth.lerp(progress, from.xRot, to.xRot),
                Mth.lerp(progress, from.yRot, to.yRot),
                Mth.lerp(progress, from.zRot, to.zRot)
        );
    }

    public float component(int index) {
        return switch (index) {
            case 0 -> this.x;
            case 1 -> this.y;
            case 2 -> this.z;
            case 3 -> this.xRot;
            case 4 -> this.yRot;
            case 5 -> this.zRot;
            default -> throw new IllegalArgumentException("Unknown transform component " + index);
        };
    }

    public DragonPoseTransform withComponent(int index, float value) {
        return switch (index) {
            case 0 -> new DragonPoseTransform(value, this.y, this.z, this.xRot, this.yRot, this.zRot);
            case 1 -> new DragonPoseTransform(this.x, value, this.z, this.xRot, this.yRot, this.zRot);
            case 2 -> new DragonPoseTransform(this.x, this.y, value, this.xRot, this.yRot, this.zRot);
            case 3 -> new DragonPoseTransform(this.x, this.y, this.z, value, this.yRot, this.zRot);
            case 4 -> new DragonPoseTransform(this.x, this.y, this.z, this.xRot, value, this.zRot);
            case 5 -> new DragonPoseTransform(this.x, this.y, this.z, this.xRot, this.yRot, value);
            default -> throw new IllegalArgumentException("Unknown transform component " + index);
        };
    }
}
