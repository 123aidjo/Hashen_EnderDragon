package com.ashenenderdragon.boss;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.INBTSerializable;

/**
 * Persistent, client-synchronized state for one official End dragon.
 *
 * <p>Tick counters and accumulated damage are mutated in place and are therefore not synchronized every tick.
 * Mode/action transitions call {@code Entity#syncData} explicitly. This keeps the state useful to rendering
 * code without producing a packet every game tick.</p>
 */
public final class DragonBossState implements INBTSerializable<CompoundTag> {
    public static final int DATA_VERSION = 7;
    public static final String NO_ACTION = "none";

    public static final StreamCodec<RegistryFriendlyByteBuf, DragonBossState> STREAM_CODEC = StreamCodec.ofMember(
            DragonBossState::write,
            DragonBossState::read
    );

    private boolean initialized;
    private int bossStage = 1;
    private DragonBossMode mode = DragonBossMode.FLYING;
    private int modeTicks;
    private long modeStartedAt;
    private float flightDamageTaken;
    private boolean invulnerable;
    private String actionId = NO_ACTION;
    private long actionStartedAt;
    private long actionStepStartedAt;
    private int actionSequence;
    private long nextActionAt;
    private String lastRegularActionId = NO_ACTION;
    private int consecutiveRegularActions;
    private int stageTwoGroundedCount;
    private float stageTwoFirstGroundDamageTaken;
    @Nullable
    private Vec3 actionDirection;
    private final List<Vec3> actionPositions = new ArrayList<>();
    private final Set<UUID> actionHitEntities = new HashSet<>();
    @Nullable
    private UUID diveTargetUuid;
    private int diveTargetEntityId = -1;
    @Nullable
    private Vec3 landingTarget;

    public boolean isInitialized() {
        return this.initialized;
    }

    public void initialize(DragonBossMode initialMode, long gameTime) {
        this.initialized = true;
        this.bossStage = 1;
        this.setMode(initialMode, gameTime);
    }

    public int getBossStage() {
        return this.bossStage;
    }

    public void setBossStage(int bossStage) {
        this.bossStage = Math.max(1, bossStage);
    }

    public DragonBossMode getMode() {
        return this.mode;
    }

    public int getModeTicks() {
        return this.modeTicks;
    }

    public void tickMode() {
        this.modeTicks++;
    }

    public long getModeStartedAt() {
        return this.modeStartedAt;
    }

    public void setMode(DragonBossMode mode, long gameTime) {
        this.mode = mode;
        this.modeTicks = 0;
        this.modeStartedAt = gameTime;
    }

    public float getFlightDamageTaken() {
        return this.flightDamageTaken;
    }

    public void addFlightDamage(float damage) {
        if (damage > 0.0F) {
            this.flightDamageTaken += damage;
        }
    }

    public void resetFlightDamage() {
        this.flightDamageTaken = 0.0F;
    }

    public boolean isInvulnerable() {
        return this.invulnerable;
    }

    public void setInvulnerable(boolean invulnerable) {
        this.invulnerable = invulnerable;
    }

    public String getActionId() {
        return this.actionId;
    }

    public long getActionStartedAt() {
        return this.actionStartedAt;
    }

    public long getActionStepStartedAt() {
        return this.actionStepStartedAt;
    }

    public void restartActionStep(long gameTime) {
        this.actionStepStartedAt = gameTime;
    }

    public int getActionSequence() {
        return this.actionSequence;
    }

    public void beginAction(String actionId, long gameTime) {
        this.actionId = actionId;
        this.actionStartedAt = gameTime;
        this.actionStepStartedAt = gameTime;
        this.actionSequence++;
        this.actionDirection = null;
        this.actionPositions.clear();
        this.actionHitEntities.clear();
    }

    public void clearAction(long gameTime) {
        this.beginAction(NO_ACTION, gameTime);
    }

    public long getNextActionAt() {
        return this.nextActionAt;
    }

    public void setNextActionAt(long nextActionAt) {
        this.nextActionAt = nextActionAt;
    }

    public String getLastRegularActionId() {
        return this.lastRegularActionId;
    }

    public int getConsecutiveRegularActions() {
        return this.consecutiveRegularActions;
    }

    public void recordRegularAction(String actionId) {
        if (actionId.equals(this.lastRegularActionId)) {
            this.consecutiveRegularActions++;
        } else {
            this.lastRegularActionId = actionId;
            this.consecutiveRegularActions = 1;
        }
    }

    public int getStageTwoGroundedCount() {
        return this.stageTwoGroundedCount;
    }

    public void incrementStageTwoGroundedCount() {
        this.stageTwoGroundedCount++;
    }

    public float getStageTwoFirstGroundDamageTaken() {
        return this.stageTwoFirstGroundDamageTaken;
    }

    public void addStageTwoFirstGroundDamage(float damage) {
        if (damage > 0.0F) {
            this.stageTwoFirstGroundDamageTaken += damage;
        }
    }

    public void resetStageTwoFirstGroundDamage() {
        this.stageTwoFirstGroundDamageTaken = 0.0F;
    }

    @Nullable
    public Vec3 getActionDirection() {
        return this.actionDirection;
    }

    public void setActionDirection(@Nullable Vec3 actionDirection) {
        this.actionDirection = actionDirection;
    }

    public List<Vec3> getActionPositions() {
        return Collections.unmodifiableList(this.actionPositions);
    }

    public void setActionPositions(List<Vec3> positions) {
        this.actionPositions.clear();
        this.actionPositions.addAll(positions);
    }

    public void clearActionPositions() {
        this.actionPositions.clear();
    }

    public boolean markActionHit(UUID entityUuid) {
        return this.actionHitEntities.add(entityUuid);
    }

    @Nullable
    public UUID getDiveTargetUuid() {
        return this.diveTargetUuid;
    }

    public int getDiveTargetEntityId() {
        return this.diveTargetEntityId;
    }

    public void setDiveTarget(@Nullable UUID uuid, int entityId) {
        this.diveTargetUuid = uuid;
        this.diveTargetEntityId = entityId;
    }

    @Nullable
    public Vec3 getLandingTarget() {
        return this.landingTarget;
    }

    public void setLandingTarget(@Nullable Vec3 landingTarget) {
        this.landingTarget = landingTarget;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("DataVersion", DATA_VERSION);
        tag.putBoolean("Initialized", this.initialized);
        tag.putInt("BossStage", this.bossStage);
        tag.putInt("Mode", this.mode.ordinal());
        tag.putInt("ModeTicks", this.modeTicks);
        tag.putLong("ModeStartedAt", this.modeStartedAt);
        tag.putFloat("FlightDamageTaken", this.flightDamageTaken);
        tag.putBoolean("Invulnerable", this.invulnerable);
        tag.putString("ActionId", this.actionId);
        tag.putLong("ActionStartedAt", this.actionStartedAt);
        tag.putLong("ActionStepStartedAt", this.actionStepStartedAt);
        tag.putInt("ActionSequence", this.actionSequence);
        tag.putLong("NextActionAt", this.nextActionAt);
        tag.putString("LastRegularActionId", this.lastRegularActionId);
        tag.putInt("ConsecutiveRegularActions", this.consecutiveRegularActions);
        tag.putInt("StageTwoGroundedCount", this.stageTwoGroundedCount);
        tag.putFloat("StageTwoFirstGroundDamageTaken", this.stageTwoFirstGroundDamageTaken);
        if (this.actionDirection != null) {
            tag.putDouble("ActionDirectionX", this.actionDirection.x);
            tag.putDouble("ActionDirectionY", this.actionDirection.y);
            tag.putDouble("ActionDirectionZ", this.actionDirection.z);
        }
        ListTag actionPositionsTag = new ListTag();
        for (Vec3 position : this.actionPositions) {
            CompoundTag positionTag = new CompoundTag();
            positionTag.putDouble("X", position.x);
            positionTag.putDouble("Y", position.y);
            positionTag.putDouble("Z", position.z);
            actionPositionsTag.add(positionTag);
        }
        tag.put("ActionPositions", actionPositionsTag);
        ListTag actionHitEntitiesTag = new ListTag();
        for (UUID entityUuid : this.actionHitEntities) {
            CompoundTag entityTag = new CompoundTag();
            entityTag.putUUID("Id", entityUuid);
            actionHitEntitiesTag.add(entityTag);
        }
        tag.put("ActionHitEntities", actionHitEntitiesTag);
        if (this.diveTargetUuid != null) {
            tag.putUUID("DiveTarget", this.diveTargetUuid);
        }
        tag.putInt("DiveTargetEntityId", this.diveTargetEntityId);
        if (this.landingTarget != null) {
            tag.putDouble("LandingTargetX", this.landingTarget.x);
            tag.putDouble("LandingTargetY", this.landingTarget.y);
            tag.putDouble("LandingTargetZ", this.landingTarget.z);
        }
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        this.initialized = tag.getBoolean("Initialized");
        this.bossStage = Math.max(1, tag.getInt("BossStage"));
        this.mode = DragonBossMode.byId(tag.getInt("Mode"));
        this.modeTicks = Math.max(0, tag.getInt("ModeTicks"));
        this.modeStartedAt = tag.getLong("ModeStartedAt");
        this.flightDamageTaken = Math.max(0.0F, tag.getFloat("FlightDamageTaken"));
        this.invulnerable = tag.getBoolean("Invulnerable");
        this.actionId = tag.contains("ActionId") ? tag.getString("ActionId") : NO_ACTION;
        this.actionStartedAt = tag.getLong("ActionStartedAt");
        this.actionStepStartedAt = tag.contains("ActionStepStartedAt") ? tag.getLong("ActionStepStartedAt") : this.actionStartedAt;
        this.actionSequence = Math.max(0, tag.getInt("ActionSequence"));
        this.nextActionAt = tag.getLong("NextActionAt");
        this.lastRegularActionId = tag.contains("LastRegularActionId") ? tag.getString("LastRegularActionId") : NO_ACTION;
        this.consecutiveRegularActions = Math.max(0, tag.getInt("ConsecutiveRegularActions"));
        this.stageTwoGroundedCount = Math.max(0, tag.getInt("StageTwoGroundedCount"));
        this.stageTwoFirstGroundDamageTaken = Math.max(0.0F, tag.getFloat("StageTwoFirstGroundDamageTaken"));
        this.actionDirection = tag.contains("ActionDirectionX")
                && tag.contains("ActionDirectionY")
                && tag.contains("ActionDirectionZ")
                ? new Vec3(tag.getDouble("ActionDirectionX"), tag.getDouble("ActionDirectionY"), tag.getDouble("ActionDirectionZ"))
                : null;
        this.actionPositions.clear();
        ListTag actionPositionsTag = tag.getList("ActionPositions", Tag.TAG_COMPOUND);
        for (int i = 0; i < actionPositionsTag.size(); i++) {
            CompoundTag positionTag = actionPositionsTag.getCompound(i);
            this.actionPositions.add(new Vec3(positionTag.getDouble("X"), positionTag.getDouble("Y"), positionTag.getDouble("Z")));
        }
        this.actionHitEntities.clear();
        ListTag actionHitEntitiesTag = tag.getList("ActionHitEntities", Tag.TAG_COMPOUND);
        for (int i = 0; i < actionHitEntitiesTag.size(); i++) {
            CompoundTag entityTag = actionHitEntitiesTag.getCompound(i);
            if (entityTag.hasUUID("Id")) {
                this.actionHitEntities.add(entityTag.getUUID("Id"));
            }
        }
        this.diveTargetUuid = tag.hasUUID("DiveTarget") ? tag.getUUID("DiveTarget") : null;
        this.diveTargetEntityId = tag.contains("DiveTargetEntityId") ? tag.getInt("DiveTargetEntityId") : -1;
        this.landingTarget = tag.contains("LandingTargetX")
                && tag.contains("LandingTargetY")
                && tag.contains("LandingTargetZ")
                ? new Vec3(tag.getDouble("LandingTargetX"), tag.getDouble("LandingTargetY"), tag.getDouble("LandingTargetZ"))
                : null;
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(this.initialized);
        buffer.writeVarInt(this.bossStage);
        buffer.writeEnum(this.mode);
        buffer.writeVarInt(this.modeTicks);
        buffer.writeLong(this.modeStartedAt);
        buffer.writeFloat(this.flightDamageTaken);
        buffer.writeBoolean(this.invulnerable);
        buffer.writeUtf(this.actionId);
        buffer.writeLong(this.actionStartedAt);
        buffer.writeLong(this.actionStepStartedAt);
        buffer.writeVarInt(this.actionSequence);
        buffer.writeLong(this.nextActionAt);
        buffer.writeUtf(this.lastRegularActionId);
        buffer.writeVarInt(this.consecutiveRegularActions);
        buffer.writeVarInt(this.stageTwoGroundedCount);
        buffer.writeFloat(this.stageTwoFirstGroundDamageTaken);
        buffer.writeBoolean(this.actionDirection != null);
        if (this.actionDirection != null) {
            buffer.writeDouble(this.actionDirection.x);
            buffer.writeDouble(this.actionDirection.y);
            buffer.writeDouble(this.actionDirection.z);
        }
        buffer.writeVarInt(this.actionPositions.size());
        for (Vec3 position : this.actionPositions) {
            buffer.writeDouble(position.x);
            buffer.writeDouble(position.y);
            buffer.writeDouble(position.z);
        }
        buffer.writeBoolean(this.diveTargetUuid != null);
        if (this.diveTargetUuid != null) {
            buffer.writeUUID(this.diveTargetUuid);
        }
        buffer.writeVarInt(this.diveTargetEntityId + 1);
        buffer.writeBoolean(this.landingTarget != null);
        if (this.landingTarget != null) {
            buffer.writeDouble(this.landingTarget.x);
            buffer.writeDouble(this.landingTarget.y);
            buffer.writeDouble(this.landingTarget.z);
        }
    }

    private static DragonBossState read(RegistryFriendlyByteBuf buffer) {
        DragonBossState state = new DragonBossState();
        state.initialized = buffer.readBoolean();
        state.bossStage = buffer.readVarInt();
        state.mode = buffer.readEnum(DragonBossMode.class);
        state.modeTicks = buffer.readVarInt();
        state.modeStartedAt = buffer.readLong();
        state.flightDamageTaken = buffer.readFloat();
        state.invulnerable = buffer.readBoolean();
        state.actionId = buffer.readUtf();
        state.actionStartedAt = buffer.readLong();
        state.actionStepStartedAt = buffer.readLong();
        state.actionSequence = buffer.readVarInt();
        state.nextActionAt = buffer.readLong();
        state.lastRegularActionId = buffer.readUtf();
        state.consecutiveRegularActions = buffer.readVarInt();
        state.stageTwoGroundedCount = buffer.readVarInt();
        state.stageTwoFirstGroundDamageTaken = buffer.readFloat();
        state.actionDirection = buffer.readBoolean()
                ? new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble())
                : null;
        int actionPositionCount = buffer.readVarInt();
        for (int i = 0; i < actionPositionCount; i++) {
            state.actionPositions.add(new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));
        }
        state.diveTargetUuid = buffer.readBoolean() ? buffer.readUUID() : null;
        state.diveTargetEntityId = buffer.readVarInt() - 1;
        state.landingTarget = buffer.readBoolean()
                ? new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble())
                : null;
        return state;
    }
}
