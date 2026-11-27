package dev.ae2lattice.mixin;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingMedium;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.inventory.InventoryCrafting;
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
    @WrapOperation(method = "executeCrafting", at = @At(value = "INVOKE",
        target = "Lappeng/api/networking/crafting/ICraftingMedium;pushPattern(Lappeng/api/networking/crafting/ICraftingPatternDetails;Lnet/minecraft/inventory/InventoryCrafting;)Z"))
    private boolean lattice$dispatch(ICraftingMedium medium, ICraftingPatternDetails pattern,
                                      InventoryCrafting inventory, Operation<Boolean> original) {
        boolean accepted = original.call(medium, pattern, inventory);
        if (accepted) {
            try { JobTrack.dispatched((CraftingCPUCluster)(Object)this, pattern, medium); }
            catch (RuntimeException ignored) { /* Do not disrupt successful AE2 crafts. */ }
        }
        return accepted;
    }
    @Inject(method = "cancel", at = @At("HEAD"))
    private void lattice$cancel(CallbackInfo ci) { JobTrack.clear((CraftingCPUCluster)(Object)this); }
    @Inject(method = "completeJob", at = @At("RETURN"))
    private void lattice$complete(CallbackInfo ci) {
        CraftingCPUCluster cpu = (CraftingCPUCluster)(Object)this;
        if (!cpu.isBusy()) JobTrack.clear(cpu);
    }
}
