package com.ashenenderdragon.item;

import com.ashenenderdragon.AshenEnderDragon;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** Converts exactly one vanilla shield while preserving its damage and all data components. */
public final class SpearWithoutSpearRecipe extends CustomRecipe {
    public SpearWithoutSpearRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return input.ingredientCount() == 1 && input.items().stream().anyMatch(stack -> stack.is(Items.SHIELD));
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        for (ItemStack stack : input.items()) {
            if (stack.is(Items.SHIELD)) {
                return stack.transmuteCopy(AshenEnderDragon.SPEAR_WITHOUT_SPEAR.get(), 1);
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return AshenEnderDragon.SPEAR_WITHOUT_SPEAR_RECIPE.get();
    }
}
