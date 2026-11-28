package dev.ae2lattice.mixin;
import appeng.api.networking.IGrid;
import appeng.container.implementations.ContainerCraftingCPU;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=ContainerCraftingCPU.class,remap=false)
public interface ContainerAccessor {
    @Accessor("network") IGrid lattice$getNetwork();
}
