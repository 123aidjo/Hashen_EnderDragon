package com.ashenenderdragon.boss;

import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.LivingEntity;
import com.ashenenderdragon.effect.SoulExtractionEffect;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public final class DragonBossEvents {
    private DragonBossEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(DragonBossEvents::onEntityTickPre);
        NeoForge.EVENT_BUS.addListener(DragonBossEvents::onLivingDamagePost);
        NeoForge.EVENT_BUS.addListener(DragonBossEvents::onLivingDamagePre);
        NeoForge.EVENT_BUS.addListener(DragonBossEvents::onLivingIncomingDamage);
        NeoForge.EVENT_BUS.addListener(DragonBossEvents::onLivingTickPost);
    }

    private static void onLivingTickPost(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity living) {
            SoulExtractionEffect.tick(living);
            DragonMinionHelper.tickCarrier(living);
        }
    }

    private static void onEntityTickPre(EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof EnderDragon dragon) {
            DragonBossController.tick(dragon);
        }
    }

    private static void onLivingDamagePost(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof EnderDragon dragon) {
            DragonBossController.recordDamage(dragon, event.getNewDamage());
        }
    }

    private static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof EnderDragon dragon
                && DragonBossController.interceptIncomingDamage(dragon, event.getAmount())) {
            event.setCanceled(true);
        }
    }

    private static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof EnderDragon dragon
                && DragonBossController.interceptFinalDamage(dragon, event.getNewDamage())) {
            event.setNewDamage(0.0F);
        }
    }
}
