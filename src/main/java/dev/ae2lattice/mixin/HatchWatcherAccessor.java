package dev.ae2lattice.mixin;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.common.tileentities.machines.IHatchWatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.List;
@Mixin(value=MTEHatch.class,remap=false)
public interface HatchWatcherAccessor {
    @Accessor("watchers") List<IHatchWatcher> lattice$watchers();
}
