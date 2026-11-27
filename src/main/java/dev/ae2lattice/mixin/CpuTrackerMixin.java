package dev.ae2lattice.mixin;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import dev.ae2lattice.server.JobTrack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CraftingCPUCluster.class, remap = false)
public abstract class CpuTrackerMixin {
    @Inject(method = "addCrafting", at = @At("RETURN"))
    private void lattice$add(ICraftingPatternDetails pattern, long crafts, CallbackInfo ci) {
        JobTrack.remember((CraftingCPUCluster)(Object)this, pattern);
    }
    @Inject(method = {"cancel", "completeJob"}, at = @At("HEAD"))
    private void lattice$clear(CallbackInfo ci) {
        JobTrack.clear((CraftingCPUCluster)(Object)this);
    }
}
