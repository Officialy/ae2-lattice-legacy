package dev.ae2lattice.mixin;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingMedium;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.inventory.InventoryCrafting;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import dev.ae2lattice.server.JobTrack;
import dev.ae2lattice.server.DispatchContext;
import appeng.api.storage.data.IAEStack;
import org.spongepowered.asm.mixin.Mixin;
import org.objectweb.asm.Opcodes;
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
        try(DispatchContext context=DispatchContext.begin(pattern)){
            boolean accepted=original.call(medium,pattern,inventory);
            if(accepted){
                try{JobTrack.dispatched((CraftingCPUCluster)(Object)this,pattern,medium,context);}
                catch(RuntimeException ignored){ /* Never change a successful AE2 craft. */ }
            }
            return accepted;
        }
    }
    // AE2 invokes this only for outputs actually credited in MODULATE mode.
    @Inject(method="recordReturnedOutputs",at=@At("HEAD"))
    private void lattice$returned(IAEStack<?> stack,CallbackInfo ci){
        try{JobTrack.returned((CraftingCPUCluster)(Object)this,stack);}
        catch(RuntimeException ignored){}
    }
    @Inject(method = "cancel", at = @At("HEAD"))
    private void lattice$cancel(CallbackInfo ci) {
        try { JobTrack.finish((CraftingCPUCluster)(Object)this,true); }
        catch(RuntimeException ignored) { JobTrack.clear((CraftingCPUCluster)(Object)this); }
    }
    // This write is reached only after AE2's early-return guard, before counters reset.
    @Inject(method = "completeJob", at = @At(value="FIELD",
        target="Lappeng/me/cluster/implementations/CraftingCPUCluster;usedStorage:J",
        opcode=Opcodes.PUTFIELD, shift=At.Shift.BEFORE))
    private void lattice$complete(CallbackInfo ci) {
        try { JobTrack.finish((CraftingCPUCluster)(Object)this,false); }
        catch(RuntimeException ignored) { JobTrack.clear((CraftingCPUCluster)(Object)this); }
    }
}
