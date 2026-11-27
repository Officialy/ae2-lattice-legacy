package dev.ae2lattice.client.compat;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

/**
 * Draws a recipe the way EMI does when you hover it (the box with inputs -> output, duration, EU).
 * This class has no EMI types in it; {@link EmiBridge} (which does) is only loaded when EMI is installed.
 */
public final class EmiRecipeView {
    private static final Map<ResourceLocation, Optional<ClientTooltipComponent>> CACHE = new HashMap<>();
    private static Boolean available;

    /** @return EMI's recipe box for this recipe ID, or null if EMI isn't there / doesn't know the recipe */
    @Nullable
    public static ClientTooltipComponent get(@Nullable ResourceLocation id) {
        if (id == null) return null;
        if (available == null) available = ModList.get().isLoaded("emi");
        if (!available) return null;
        return CACHE.computeIfAbsent(id, k -> {
            try {
                return Optional.ofNullable(EmiBridge.recipeBox(k));
            } catch (Throwable t) {
                return Optional.empty();
            }
        }).orElse(null);
    }

    public static void clear() {
        CACHE.clear();
    }

    private EmiRecipeView() {}
}
