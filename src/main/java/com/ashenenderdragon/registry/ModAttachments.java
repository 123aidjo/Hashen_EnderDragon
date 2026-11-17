package com.ashenenderdragon.registry;

import com.ashenenderdragon.AshenEnderDragon;
import com.ashenenderdragon.boss.DragonBossState;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(
            NeoForgeRegistries.Keys.ATTACHMENT_TYPES,
            AshenEnderDragon.MODID
    );

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<DragonBossState>> DRAGON_BOSS_STATE = ATTACHMENT_TYPES.register(
            "dragon_boss_state",
            () -> AttachmentType.serializable(DragonBossState::new)
                    .sync(DragonBossState.STREAM_CODEC)
                    .build()
    );

    private ModAttachments() {
    }
}
