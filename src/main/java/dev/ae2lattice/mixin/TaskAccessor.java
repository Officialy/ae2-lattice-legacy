package dev.ae2lattice.mixin;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value = CraftingCPUCluster.TaskProgress.class, remap = false)
public interface TaskAccessor {
    @Accessor("value") long lattice$getRemaining();
}
