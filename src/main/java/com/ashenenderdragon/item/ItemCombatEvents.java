package com.ashenenderdragon.item;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.ashenenderdragon.AshenEnderDragon;

import net.minecraft.core.registries.Registries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;

/** Partial blocking, hunger cost, and knockback resistance for Spear Without Spear. */
public final class ItemCombatEvents {
    public static final float DAMAGE_BLOCKED_FRACTION = 0.60F;
    public static final float PROTECTION_FOUR_BLOCKED_FRACTION = 0.90F;
    public static final float IMPULSE_REMAINING_FRACTION = 0.40F;
    public static final int FOOD_COST = 2;

    private static final Map<UUID, Integer> LAST_SUCCESSFUL_BLOCK_TICK = new ConcurrentHashMap<>();

    private ItemCombatEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ItemCombatEvents::onShieldBlock);
        NeoForge.EVENT_BUS.addListener(ItemCombatEvents::onKnockBack);
    }

    private static void onShieldBlock(LivingShieldBlockEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || !event.getBlocked()
                || !isCustomShield(player.getUseItem())) {
            return;
        }

        if (player.getFoodData().getFoodLevel() <= 0) {
            event.setBlocked(false);
            event.setBlockedDamage(0.0F);
            event.setShieldDamage(0.0F);
            return;
        }

        ItemStack shield = player.getUseItem();
        int protectionLevel = shield.getEnchantmentLevel(player.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.PROTECTION));
        protectionLevel = Mth.clamp(protectionLevel, 0, 4);
        float blockedFraction = Mth.lerp(protectionLevel / 4.0F,
                DAMAGE_BLOCKED_FRACTION, PROTECTION_FOUR_BLOCKED_FRACTION);
        float incomingDamage = event.getOriginalBlockedDamage();
        event.setBlockedDamage(incomingDamage * blockedFraction);

        if (!player.level().isClientSide && incomingDamage > 0.0F) {
            LAST_SUCCESSFUL_BLOCK_TICK.put(player.getUUID(), player.tickCount);
            if (!player.getAbilities().instabuild) {
                player.getFoodData().setFoodLevel(Math.max(0, player.getFoodData().getFoodLevel() - FOOD_COST));
            }
        }
    }

    private static void onKnockBack(LivingKnockBackEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || player.level().isClientSide
                || !shouldReduceImpulse(player)) {
            return;
        }
        event.setStrength(event.getStrength() * IMPULSE_REMAINING_FRACTION);
    }

    public static boolean shouldReduceImpulse(Player player) {
        if (isActivelyUsingCustomShield(player)) {
            return true;
        }
        Integer lastBlockTick = LAST_SUCCESSFUL_BLOCK_TICK.get(player.getUUID());
        return lastBlockTick != null && player.tickCount - lastBlockTick >= 0 && player.tickCount - lastBlockTick <= 1;
    }

    private static boolean isActivelyUsingCustomShield(Player player) {
        return player.isUsingItem()
                && player.getFoodData().getFoodLevel() > 0
                && player.getTicksUsingItem() >= ShieldItem.EFFECTIVE_BLOCK_DELAY
                && isCustomShield(player.getUseItem());
    }

    private static boolean isCustomShield(ItemStack stack) {
        return stack.is(AshenEnderDragon.SPEAR_WITHOUT_SPEAR.get());
    }
}
