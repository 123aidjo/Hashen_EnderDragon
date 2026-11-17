package com.ashenenderdragon.mixin;

import com.ashenenderdragon.AshenEnderDragon;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.ShieldDecorationRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Lets the vanilla banner decoration recipe treat the custom item as a shield. */
@Mixin(ShieldDecorationRecipe.class)
public abstract class ShieldDecorationRecipeMixin {
    @Redirect(
            method = {
                    "matches(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/world/level/Level;)Z",
                    "assemble(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z")
    )
    private boolean ashenenderdragon$acceptCustomShield(ItemStack stack, Item expectedItem) {
        return stack.is(expectedItem)
                || expectedItem == Items.SHIELD && stack.is(AshenEnderDragon.SPEAR_WITHOUT_SPEAR.get());
    }
}
