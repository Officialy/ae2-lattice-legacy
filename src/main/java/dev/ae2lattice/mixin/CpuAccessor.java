package dev.ae2lattice.mixin;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = CraftingCPUCluster.class, remap = false)
public interface CpuAccessor {
    @Accessor("tasks") Map<ICraftingPatternDetails, CraftingCPUCluster.TaskProgress> lattice$getTasks();
}
