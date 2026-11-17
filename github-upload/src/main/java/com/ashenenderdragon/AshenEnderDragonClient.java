package com.ashenenderdragon;

import com.ashenenderdragon.client.animation.DragonAnimationDebugClient;
import com.ashenenderdragon.client.animation.DragonAnimationOverrideLibrary;
import com.ashenenderdragon.client.item.SpearWithoutSpearClientExtension;
import com.ashenenderdragon.client.particle.DragonTransitionSnowstorm;
import com.ashenenderdragon.client.particle.EnderSnowflakeParticle;
import com.ashenenderdragon.client.sound.DragonBossMusicController;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.common.NeoForge;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = AshenEnderDragon.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = AshenEnderDragon.MODID, value = Dist.CLIENT)
public class AshenEnderDragonClient {
    public AshenEnderDragonClient(IEventBus modEventBus, ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modEventBus.addListener(DragonAnimationDebugClient::registerKeyMappings);
        modEventBus.addListener(AshenEnderDragonClient::registerClientExtensions);
        modEventBus.addListener(AshenEnderDragonClient::registerParticleProviders);
        NeoForge.EVENT_BUS.addListener(DragonAnimationDebugClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(DragonBossMusicController::onClientTick);
        NeoForge.EVENT_BUS.addListener(DragonTransitionSnowstorm::onClientTick);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // Some client setup code
        AshenEnderDragon.LOGGER.info("HELLO FROM CLIENT SETUP");
        AshenEnderDragon.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        event.enqueueWork(() -> {
            DragonAnimationOverrideLibrary.load();
            ItemProperties.register(
                    AshenEnderDragon.SPEAR_WITHOUT_SPEAR.get(),
                    ResourceLocation.withDefaultNamespace("blocking"),
                    (stack, level, livingEntity, seed) -> livingEntity != null
                            && livingEntity.isUsingItem()
                            && livingEntity.getUseItem() == stack ? 1.0F : 0.0F
            );
        });
    }

    private static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(SpearWithoutSpearClientExtension.instance(), AshenEnderDragon.SPEAR_WITHOUT_SPEAR.get());
    }

    private static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(AshenEnderDragon.ENDER_SNOWFLAKE.get(), EnderSnowflakeParticle.Provider::new);
    }
}
