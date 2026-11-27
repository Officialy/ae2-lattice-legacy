package dev.ae2lattice.client.compat;

import org.jetbrains.annotations.Nullable;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.resources.ResourceLocation;

/** Only loaded when EMI is installed (see {@link EmiRecipeView}). */
final class EmiBridge {
    @Nullable
    static ClientTooltipComponent recipeBox(ResourceLocation id) throws Exception {
        EmiRecipe recipe = EmiApi.getRecipeManager().getRecipe(id);
        if (recipe == null) return null;
        // EMI's own hover box. Not part of EMI's public API, so constructed reflectively.
        Object c = Class.forName("dev.emi.emi.screen.tooltip.RecipeTooltipComponent")
                .getConstructor(EmiRecipe.class).newInstance(recipe);
        return c instanceof ClientTooltipComponent ctc ? ctc : null;
    }

    private EmiBridge() {}
}
