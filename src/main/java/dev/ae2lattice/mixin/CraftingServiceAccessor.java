package dev.ae2lattice.mixin;

import java.util.Set;

import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only: the CPU set in the exact order AE2 uses when handing returned items to CPUs. */
@Mixin(value = CraftingService.class, remap = false)
public interface CraftingServiceAccessor {
    @Accessor("craftingCPUClusters")
    Set<CraftingCPUCluster> ae2lattice$getCpus();
}
