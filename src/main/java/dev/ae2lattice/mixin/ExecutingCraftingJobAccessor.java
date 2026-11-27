package dev.ae2lattice.mixin;

import java.util.Map;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ListCraftingInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ExecutingCraftingJob.class, remap = false)
public interface ExecutingCraftingJobAccessor {
    /** Values are ExecutingCraftingJob$TaskProgress (package-private) -> cast to {@link TaskProgressAccessor}. */
    @Accessor("tasks")
    Map<IPatternDetails, Object> ae2lattice$getTasks();

    @Accessor("waitingFor")
    ListCraftingInventory ae2lattice$getWaitingFor();

    @Accessor("finalOutput")
    GenericStack ae2lattice$getFinalOutput();

    @Accessor("remainingAmount")
    long ae2lattice$getRemainingAmount();
}
