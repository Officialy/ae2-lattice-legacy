package dev.ae2lattice.mixin;

import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access to a furnace's own progress counters (furnace, blast furnace, smoker, and subclasses). */
@Mixin(AbstractFurnaceBlockEntity.class)
public interface AbstractFurnaceAccessor {
    @Accessor("litTime")
    int ae2lattice$getLitTime();

    @Accessor("cookingProgress")
    int ae2lattice$getCookingProgress();

    @Accessor("cookingTotalTime")
    int ae2lattice$getCookingTotalTime();
}
