package com.ashenenderdragon.boss;

/**
 * High-level movement modes owned by the custom boss controller.
 *
 * <p>The transition modes deliberately remain separate from vanilla dragon phases. Vanilla phases still
 * provide movement and animation semantics, while this enum decides which phase transitions are currently
 * allowed.</p>
 */
public enum DragonBossMode {
    FLYING,
    DIVING,
    LANDING,
    GROUNDED,
    TAKEOFF,
    PHASE_TRANSITION,
    STAGE_THREE_TRANSITION;

    public static DragonBossMode byId(int id) {
        DragonBossMode[] values = values();
        return id >= 0 && id < values.length ? values[id] : FLYING;
    }
}
